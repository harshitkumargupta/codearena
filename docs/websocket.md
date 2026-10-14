# WebSocket (STOMP) API

The api pushes live updates over STOMP 1.2 on a plain WebSocket. The channel is **server-to-client only**:
clients connect, subscribe and receive; any client `SEND` is rejected.

## Endpoint

```
ws://<api-host>/ws          (wss:// behind TLS)
```

No SockJS fallback. Browsers must connect from the configured frontend origin (`CORS_ALLOWED_ORIGINS`,
default `http://localhost:5173`); other origins fail the handshake.

## Authentication

Send the same JWT access token as the REST API in the STOMP `CONNECT` frame (not in the URL, not as a cookie):

```
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer <accessToken>

^@
```

- Missing, malformed, wrongly signed or expired token: the server replies with an `ERROR` frame
  (`message:Authentication required`) and closes the connection.
- The token is checked **only at CONNECT**. A connected socket keeps working after the token expires
  (2 hours by default). When the REST API starts returning 401, refresh the token, then disconnect and reconnect
  the socket.

With `@stomp/stompjs`:

```js
const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  reconnectDelay: 0, // reconnect yourself after refreshing the token
});
```

## Destinations

| Destination | Who may subscribe | Payload |
|---|---|---|
| `/topic/submissions/{id}` | the submission's owner, or an ADMIN | `SubmissionStatusUpdate` |
| `/topic/contests/{id}/leaderboard` | any authenticated user, if the contest exists (same rule as `GET /api/contests/{id}/leaderboard`) | `LeaderboardResponse` |

Every other destination is denied. A denied `SUBSCRIBE` (or any `SEND`) gets an `ERROR` frame
(`message:Access denied` or `message:Clients may not send messages`) **and the server closes the connection**, so
only subscribe to destinations the user is allowed to see.

## Payloads

All timestamps are ISO-8601 UTC strings.

### `SubmissionStatusUpdate` (`/topic/submissions/{id}`)

Pushed after every committed status change: `RUNNING`, each final verdict, and `INTERNAL_ERROR` from the judge's
dead-letter handling or the stuck-submission sweeper.

```json
{
  "id": 29,
  "userId": 6,
  "problemId": 1,
  "contestId": null,
  "language": "PYTHON",
  "status": "WRONG_ANSWER",
  "failedTestOrdinal": 3,
  "timeMs": 52,
  "memoryKb": 4812,
  "createdAt": "2026-10-13T14:06:27.123456Z",
  "judgedAt": "2026-10-13T14:06:29.456789Z",
  "detailAvailable": false
}
```

This is the `GET /api/submissions/{id}` response **without `sourceCode` and `verdictDetail`** (compiler output
quotes source). When `detailAvailable` is `true` (for example a compilation error), call
`GET /api/submissions/{id}` to show the detail.

`status` is one of `PENDING`, `RUNNING`, `ACCEPTED`, `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`,
`MEMORY_LIMIT_EXCEEDED`, `RUNTIME_ERROR`, `COMPILATION_ERROR`, `INTERNAL_ERROR`. Everything except `PENDING` and
`RUNNING` is final; unsubscribe after a final status.

### `LeaderboardResponse` (`/topic/contests/{id}/leaderboard`)

Same shape as `GET /api/contests/{id}/leaderboard` (top 50). Broadcast after a contest submission is judged or a
user joins, at most about **once per second per contest**: a burst of results produces one board with the latest
state, not one board per result. Each message is a full snapshot; replace the displayed board, do not merge.

```json
{
  "contestId": 1,
  "entries": [
    { "rank": 1, "userId": 6, "username": "alice", "solved": 2, "penaltyMinutes": 21 },
    { "rank": 2, "userId": 7, "username": "bob", "solved": 2, "penaltyMinutes": 22 }
  ]
}
```

Equal scores share a rank (1, 1, 3).

## Required client pattern: subscribe first, then GET

A result can be pushed before your subscription is active (fast judges finish in well under a second), and pushes
are never replayed. Always:

1. Connect and `SUBSCRIBE` to the topic.
2. Then `GET` the current state once (`GET /api/submissions/{id}` or `GET /api/contests/{id}/leaderboard`).
3. Show whichever is newer. For a submission, a final status always wins over `RUNNING`/`PENDING`; for the
   leaderboard, the last message received replaces the board.

For a new submission: `POST /api/submissions` returns the id, subscribe to `/topic/submissions/{id}`, then GET it.
If the GET already shows a final status, unsubscribe.

## Limits

- The broker is in-memory and per api instance: a client only receives pushes produced by the instance it is
  connected to. With more than one api instance, use sticky sessions or a shared broker (see docs/DESIGN.md, open
  questions).
- No heartbeats are configured; rely on reconnect logic.
- After a reconnect, repeat the subscribe-then-GET pattern, because updates sent while disconnected are lost.
