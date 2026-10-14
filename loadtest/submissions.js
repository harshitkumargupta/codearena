import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SOLUTIONS } from './lib/solutions.js';
import { awaitVerdict, login, problemIds, submit, summaryOutputs } from './lib/api.js';

// MODE=closed: ramping VUs (warm-up, steady, ramp-down). MODE=open: arrival-rate steps to find saturation.
const MODE = __ENV.MODE || 'closed';
const VUS = Number(__ENV.VUS || 6);
const WARMUP = __ENV.WARMUP || '1m';
const STEADY = __ENV.STEADY || '3m';
const RAMPDOWN = __ENV.RAMPDOWN || '30s';
const RATES = (__ENV.RATES || '30,60,90').split(',').map(Number);
const STEP = __ENV.STEP || '2m';
const THINK_S = Number(__ENV.THINK_S || 1);
const USERS = JSON.parse(open(__ENV.USERS_FILE));

const timeToVerdict = new Trend('time_to_verdict', true);
const verdicts = new Counter('verdicts');
const mismatches = new Counter('verdict_mismatch');
const timeouts = new Counter('verdict_timeout');
const submitted = new Counter('submissions_ok');
const rejected = new Counter('submit_rejected');
const submitSuccess = new Rate('submit_success');

function seconds(d) {
  const n = parseFloat(d);
  return d.endsWith('m') ? n * 60 : n;
}

function phase(started) {
  const t = (Date.now() - started) / 1000;
  if (MODE === 'open') {
    return `rate_${RATES[Math.min(RATES.length - 1, Math.floor(t / (seconds(STEP) + 5)))]}`;
  }
  if (t < seconds(WARMUP)) return 'warmup';
  if (t < seconds(WARMUP) + seconds(STEADY)) return 'steady';
  return 'rampdown';
}

const phases = MODE === 'open' ? RATES.map((r) => `rate_${r}`) : ['warmup', 'steady', 'rampdown'];
const thresholds = { submit_success: ['rate>=0'] };
for (const p of phases) {
  thresholds[`time_to_verdict{phase:${p}}`] = ['max>=0'];
  thresholds[`http_req_duration{name:submit,phase:${p}}`] = ['max>=0'];
  thresholds[`submissions_ok{phase:${p}}`] = ['count>=0'];
}
thresholds['http_req_duration{name:submit}'] = ['max>=0'];
thresholds['submit_rejected{status:429}'] = ['count>=0'];

const scenario = MODE === 'open'
  ? {
    executor: 'ramping-arrival-rate', startRate: RATES[0], timeUnit: '1m',
    preAllocatedVUs: Number(__ENV.PRE_VUS || 60), maxVUs: Number(__ENV.MAX_VUS || 400),
    stages: RATES.flatMap((r) => [{ target: r, duration: '5s' }, { target: r, duration: STEP }]),
  }
  : {
    executor: 'ramping-vus', startVUs: 0,
    stages: [{ target: VUS, duration: '30s' }, { target: VUS, duration: `${seconds(WARMUP) - 30}s` },
      { target: VUS, duration: STEADY }, { target: 0, duration: RAMPDOWN }],
    gracefulRampDown: '10m',
  };

export const options = {
  scenarios: { submissions: scenario },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds,
  setupTimeout: '5m',
  teardownTimeout: '1m',
};

export function setup() {
  const data = { tokens: USERS.users.map((u) => login(u.username, u.password)), problems: problemIds() };
  data.started = Date.now();
  console.log(`SCENARIO_START ${data.started}`);
  return data;
}

export default function (data) {
  const p = phase(data.started);
  const tags = { phase: p };
  const token = data.tokens[(exec.scenario.iterationInTest) % data.tokens.length];
  const solution = SOLUTIONS[Math.floor(Math.random() * SOLUTIONS.length)];
  const started = Date.now();
  const res = submit(token, data.problems[solution.slug], solution, null, tags);
  const ok = check(res, { 'submit 201': (r) => r.status === 201 });
  submitSuccess.add(ok, tags);
  if (!ok) {
    rejected.add(1, Object.assign({ status: String(res.status) }, tags));
    return;
  }
  submitted.add(1, tags);
  const result = awaitVerdict(token, res.json('id'), started, tags);
  if (result.status === 'TIMEOUT') {
    timeouts.add(1, tags);
    return;
  }
  timeToVerdict.add(result.elapsedMs, tags);
  verdicts.add(1, Object.assign({ verdict: result.status, solution: solution.key }, tags));
  if (result.status !== solution.expected) {
    mismatches.add(1, { solution: solution.key, verdict: result.status });
  }
  if (MODE === 'closed' && THINK_S > 0) {
    sleep(THINK_S);
  }
}

export function handleSummary(data) {
  return summaryOutputs(data, __ENV.SUMMARY_PATH || 'summary.json');
}
