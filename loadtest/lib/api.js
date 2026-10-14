import http from 'k6/http';
import { sleep } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const FINAL = ['ACCEPTED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'RUNTIME_ERROR',
  'COMPILATION_ERROR', 'INTERNAL_ERROR'];
const POLL_INTERVAL_S = Number(__ENV.POLL_INTERVAL_S || 0.25);
const VERDICT_TIMEOUT_MS = Number(__ENV.VERDICT_TIMEOUT_MS || 600000);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

function auth(token, extra) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS), ...extra };
}

export function login(username, password) {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ username, password }),
    { headers: JSON_HEADERS, tags: { name: 'login' } });
  if (res.status !== 200) {
    throw new Error(`login failed for ${username}: HTTP ${res.status}`);
  }
  return res.json('accessToken');
}

export function problemIds() {
  const res = http.get(`${BASE_URL}/api/problems?size=50`, { tags: { name: 'problems' } });
  const ids = {};
  for (const p of res.json('content')) {
    ids[p.slug] = p.id;
  }
  return ids;
}

export function submit(token, problemId, solution, contestId, tags) {
  return http.post(`${BASE_URL}/api/submissions`, JSON.stringify({
    problemId, language: solution.language, sourceCode: solution.source, contestId: contestId || null,
  }), auth(token, { tags: Object.assign({ name: 'submit' }, tags) }));
}

/** Polls until the submission is final; returns { status, elapsedMs } or { status: 'TIMEOUT' }. */
export function awaitVerdict(token, id, startedMs, tags) {
  while (Date.now() - startedMs < VERDICT_TIMEOUT_MS) {
    const res = http.get(`${BASE_URL}/api/submissions/${id}`,
      auth(token, { tags: Object.assign({ name: 'poll' }, tags) }));
    if (res.status === 200) {
      const status = res.json('status');
      if (FINAL.includes(status)) {
        return { status, elapsedMs: Date.now() - startedMs };
      }
    }
    sleep(POLL_INTERVAL_S);
  }
  return { status: 'TIMEOUT', elapsedMs: Date.now() - startedMs };
}

export function summaryOutputs(data, path) {
  const m = data.metrics;
  const fmt = (name, stat) => (m[name] && m[name].values[stat] !== undefined ? m[name].values[stat].toFixed(0) : 'n/a');
  const lines = [
    `submissions: ${m.submissions_ok ? m.submissions_ok.values.count : 0} accepted by api, `
      + `${m.submit_rejected ? m.submit_rejected.values.count : 0} rejected`,
    `submit p50/p95 ms: ${fmt('http_req_duration{name:submit}', 'p(50)')} / ${fmt('http_req_duration{name:submit}', 'p(95)')}`,
    `time_to_verdict p50/p95 ms: ${fmt('time_to_verdict', 'p(50)')} / ${fmt('time_to_verdict', 'p(95)')}`,
  ];
  // setup_data holds the users' access tokens; it must never be written to a file.
  const { setup_data: omitted, ...rest } = data;
  return { [path]: JSON.stringify(rest, null, 2), stdout: `${lines.join('\n')}\n` };
}
