#!/bin/sh
# One load-test run: setup users, sample resources, run k6, wait for the queue to drain, screenshot Grafana,
# analyze (correctness checks), print the rows matched by the run prefix, and clean up only if every check passed.
#
# Required env: ENV_FILE LABEL TITLE KIND (submissions|saturation|leaderboard|limits) WORKERS LIMITS (raised|real)
#               SCRIPT (submissions.js|leaderboard.js) USERS
# Optional env: CONTEST=1, and the k6 settings of the script (VUS, WARMUP, STEADY, RAMPDOWN, THINK_S, MODE, RATES,
#               STEP, CLIENTS, SUBMITTERS, DURATION_S).
set -eu
cd "$(dirname "$0")/.."
: "${ENV_FILE:?}" "${LABEL:?}" "${TITLE:?}" "${KIND:?}" "${WORKERS:?}" "${LIMITS:?}" "${SCRIPT:?}" "${USERS:?}"
now() { python3 -c 'import time; print(f"{time.time():.3f}")'; }

RUN_ID=$(date -u +%m%d%H%M)
DIR=loadtest/.runs/$RUN_ID
mkdir -p "$DIR" loadtest/results
COMPOSE="docker compose --env-file $ENV_FILE -f docker-compose.yml"
[ "$LIMITS" = raised ] && COMPOSE="$COMPOSE -f docker-compose.loadtest.yml"

echo "== $LABEL run $RUN_ID: scaling to $WORKERS worker(s)"
$COMPOSE up -d --no-recreate --scale worker="$WORKERS" worker >/dev/null 2>&1
until [ "$($COMPOSE ps worker --format '{{.Health}}' | grep -c '^healthy$')" = "$WORKERS" ] \
      && [ "$($COMPOSE ps worker --format '{{.Name}}' | wc -l | tr -d ' ')" = "$WORKERS" ]; do sleep 3; done

python3 loadtest/setup.py --run-id "$RUN_ID" --users "$USERS" --env-file "$ENV_FILE" ${CONTEST:+--contest}
python3 loadtest/sampler.py --out "$DIR/samples.csv" &
SAMPLER=$!
sleep 6

START=$(now)
k6 run -q --console-output "$DIR/console.log" \
  -e USERS_FILE="$PWD/$DIR/users.json" -e SUMMARY_PATH="$DIR/k6-summary.json" \
  ${VUS:+-e VUS=$VUS} ${WARMUP:+-e WARMUP=$WARMUP} ${STEADY:+-e STEADY=$STEADY} ${RAMPDOWN:+-e RAMPDOWN=$RAMPDOWN} \
  ${THINK_S:+-e THINK_S=$THINK_S} ${MODE:+-e MODE=$MODE} ${RATES:+-e RATES=$RATES} ${STEP:+-e STEP=$STEP} \
  ${POLL_INTERVAL_S:+-e POLL_INTERVAL_S=$POLL_INTERVAL_S} ${PRE_VUS:+-e PRE_VUS=$PRE_VUS} ${MAX_VUS:+-e MAX_VUS=$MAX_VUS} \
  ${CLIENTS:+-e CLIENTS=$CLIENTS} ${SUBMITTERS:+-e SUBMITTERS=$SUBMITTERS} ${DURATION_S:+-e DURATION_S=$DURATION_S} \
  "loadtest/$SCRIPT" | tee "$DIR/k6-stdout.txt" || true
END=$(now)

echo "== waiting for this run's submissions to be final"
python3 - "$RUN_ID" "$ENV_FILE" <<'PY'
import sys, time
sys.path.insert(0, "loadtest")
from common import like_prefix, psql
run_id, env = sys.argv[1], sys.argv[2]
deadline = time.time() + 900
while time.time() < deadline:
    n = int(psql(env, "select count(*) from submissions s join users u on u.id = s.user_id where u.username like "
                      f"'{like_prefix(run_id)}' escape '\\' and s.status in ('PENDING', 'RUNNING')")[0][0])
    if n == 0:
        break
    time.sleep(5)
print(f"open submissions left: {n}")
PY
echo "== waiting for judge.jobs and judge.results to be empty (duplicate jobs from the sweeper drain after the rows are final)"
for _ in $(seq 1 180); do
  LEFT=$($COMPOSE exec -T rabbitmq rabbitmqctl -q list_queues name messages \
         | awk '$1 == "judge.jobs" || $1 == "judge.results" { n += $2 } END { print n + 0 }')
  [ "$LEFT" = 0 ] && break
  sleep 5
done
echo "messages left in judge.jobs + judge.results: $LEFT"
sleep 20
kill "$SAMPLER" 2>/dev/null || true
wait "$SAMPLER" 2>/dev/null || true

SCENARIO_START=$(sed -n 's/.*SCENARIO_START \([0-9]*\).*/\1/p' "$DIR/console.log" | head -1)
SCENARIO_START=$(python3 -c "print(${SCENARIO_START:-0} / 1000 or $START)")
STEADY_ARGS=""
if [ "$KIND" = submissions ] || [ "$KIND" = limits ]; then
  STEADY_ARGS=$(python3 - "$SCENARIO_START" "${WARMUP:-1m}" "${STEADY:-3m}" <<'PY'
import sys
s = lambda d: float(d[:-1]) * 60 if d.endswith("m") else float(d.rstrip("s"))
start, warm, steady = float(sys.argv[1]), s(sys.argv[2]), s(sys.argv[3])
print(f"--steady-start {start + warm:.3f} --steady-end {start + warm + steady:.3f}")
PY
)
fi
STEP_ARGS=""
if [ "$KIND" = saturation ]; then
  STEP_ARGS=$(python3 - "$SCENARIO_START" "$RATES" "${STEP:-2m}" <<'PY'
import sys
s = lambda d: float(d[:-1]) * 60 if d.endswith("m") else float(d.rstrip("s"))
start, rates, step = float(sys.argv[1]), sys.argv[2].split(","), s(sys.argv[3]) + 5
print("--steps " + ",".join(f"{r}:{start + i * step + 5:.3f}:{start + (i + 1) * step:.3f}" for i, r in enumerate(rates)))
PY
)
fi

SHOT=loadtest/results/$LABEL.jpg
GF_PASS=$(sed -n 's/^GRAFANA_ADMIN_PASSWORD=//p' "$ENV_FILE") node loadtest/grafana-shot.cjs \
  "$(python3 -c "print(int(($START - 60) * 1000))")" "$(python3 -c "import time; print(int(time.time() * 1000))")" "$SHOT" \
  || SHOT=""

set +e
python3 loadtest/analyze.py --run-id "$RUN_ID" --label "$LABEL" --title "$TITLE" --kind "$KIND" --workers "$WORKERS" \
  --limits "$LIMITS" --settings "${SETTINGS:-}" --start "$SCENARIO_START" --end "$END" --env-file "$ENV_FILE" \
  --summary "$DIR/k6-summary.json" --samples "$DIR/samples.csv" --console "$DIR/console.log" \
  ${SHOT:+--screenshot "$SHOT"} $STEADY_ARGS $STEP_ARGS
CHECKS=$?
set -e

python3 loadtest/cleanup.py --run-id "$RUN_ID" --env-file "$ENV_FILE"
if [ "$CHECKS" = 0 ]; then
  python3 loadtest/cleanup.py --run-id "$RUN_ID" --env-file "$ENV_FILE" --yes
else
  echo "== correctness checks failed: run $RUN_ID data kept for inspection (clean up later with cleanup.py --yes)"
fi
echo "$RUN_ID" > "$DIR/../last-run-id"
exit "$CHECKS"
