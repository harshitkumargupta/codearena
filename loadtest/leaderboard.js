import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { setTimeout } from 'k6/timers';
import { WebSocket } from 'k6/websockets';
import { SOLUTIONS } from './lib/solutions.js';
import { awaitVerdict, BASE_URL, login, problemIds, submit, summaryOutputs } from './lib/api.js';

// CLIENTS STOMP sessions subscribe to the contest leaderboard while SUBMITTERS submit contest solutions.
// Each received board is printed as "LBRECV <client> <epoch ms>" (no payload); analyze.py matches the receive times
// with judged_at from the database and the api's broadcast log lines.
const CLIENTS = Number(__ENV.CLIENTS || 50);
const SUBMITTERS = Number(__ENV.SUBMITTERS || 4);
const DURATION_S = Number(__ENV.DURATION_S || 180);
const THINK_S = Number(__ENV.THINK_S || 2);
const RUN = JSON.parse(open(__ENV.USERS_FILE));
const WS_URL = `${BASE_URL.replace(/^http/, 'ws')}/ws`;

const boards = new Counter('lb_messages');
const connected = new Counter('lb_connected');
const stompErrors = new Counter('lb_errors');
const timeToVerdict = new Trend('time_to_verdict', true);
const submitted = new Counter('submissions_ok');
const rejected = new Counter('submit_rejected');
const verdicts = new Counter('verdicts');
const mismatches = new Counter('verdict_mismatch');

export const options = {
  scenarios: {
    watchers: {
      executor: 'per-vu-iterations', vus: CLIENTS, iterations: 1, exec: 'watch', maxDuration: `${DURATION_S + 120}s`,
    },
    submitters: {
      executor: 'constant-vus', vus: SUBMITTERS, duration: `${DURATION_S}s`, exec: 'submitContest',
      startTime: '15s', gracefulStop: '5m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: { lb_messages: ['count>=0'], 'http_req_duration{name:submit}': ['max>=0'] },
  setupTimeout: '5m',
};

function frame(command, headers, body = '') {
  return `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`;
}

export function setup() {
  const data = {
    tokens: RUN.users.map((u) => login(u.username, u.password)),
    problems: problemIds(),
    contestId: RUN.contestId,
  };
  console.log(`SCENARIO_START ${Date.now()}`);
  return data;
}

export function watch(data) {
  const client = exec.vu.idInTest;
  const token = data.tokens[(client - 1) % data.tokens.length];
  const ws = new WebSocket(WS_URL);
  ws.onopen = () => ws.send(frame('CONNECT', { 'accept-version': '1.2', host: 'localhost',
    Authorization: `Bearer ${token}`, 'heart-beat': '0,0' }));
  ws.onmessage = (event) => {
    const text = String(event.data).replace(/^\n+/, '');
    if (text.startsWith('CONNECTED')) {
      connected.add(1);
      ws.send(frame('SUBSCRIBE', { id: 'lb', destination: `/topic/contests/${data.contestId}/leaderboard` }));
    } else if (text.startsWith('MESSAGE')) {
      boards.add(1);
      console.log(`LBRECV ${client} ${Date.now()}`);
    } else if (text.startsWith('ERROR')) {
      stompErrors.add(1);
    }
  };
  ws.onerror = () => stompErrors.add(1);
  setTimeout(() => ws.close(), (DURATION_S + 45) * 1000);
}

export function submitContest(data) {
  const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];
  const solution = SOLUTIONS[Math.floor(Math.random() * SOLUTIONS.length)];
  const started = Date.now();
  const res = submit(token, data.problems[solution.slug], solution, data.contestId, {});
  if (!check(res, { 'submit 201': (r) => r.status === 201 })) {
    rejected.add(1, { status: String(res.status) });
    sleep(THINK_S);
    return;
  }
  submitted.add(1);
  const result = awaitVerdict(token, res.json('id'), started, {});
  if (result.status !== 'TIMEOUT') {
    timeToVerdict.add(result.elapsedMs);
    verdicts.add(1, { verdict: result.status });
    if (result.status !== solution.expected) {
      mismatches.add(1, { solution: solution.key, verdict: result.status });
    }
  }
  sleep(THINK_S);
}

export function handleSummary(data) {
  return summaryOutputs(data, __ENV.SUMMARY_PATH || 'summary.json');
}
