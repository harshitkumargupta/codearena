#!/usr/bin/env node
// Throwaway STOMP 1.2 client for checking the api's WebSocket layer by hand. Node 22+ (global WebSocket), no deps.
//   node scripts/ws-check.mjs connect [--token T]              CONNECT and print the reply frame
//   node scripts/ws-check.mjs expired-token                      CONNECT with a correctly signed but expired JWT
//   node scripts/ws-check.mjs watch --token T --dest D [--seconds N] [--name X] [--until-final]
// Env: WS_URL (default ws://localhost:8080/ws), JWT_SECRET (expired-token only).
import { createHmac } from "node:crypto";

const url = process.env.WS_URL ?? "ws://localhost:8080/ws";
const [command, ...rest] = process.argv.slice(2);
const opts = {};
for (let i = 0; i < rest.length; i++) {
  if (rest[i].startsWith("--")) {
    const key = rest[i].slice(2);
    opts[key] = rest[i + 1] && !rest[i + 1].startsWith("--") ? rest[++i] : true;
  }
}
const name = opts.name ?? "client";
const log = (msg) => console.log(`${new Date().toISOString()} [${name}] ${msg}`);

function encode(cmd, headers = {}, body = "") {
  return `${cmd}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n")}\n\n${body}\0`;
}

function decode(raw) {
  const text = raw.replace(/^\n+/, "");
  if (!text) return null;
  const sep = text.indexOf("\n\n");
  const head = text.slice(0, sep).split("\n");
  const headers = Object.fromEntries(head.slice(1).map((l) => [l.slice(0, l.indexOf(":")), l.slice(l.indexOf(":") + 1)]));
  return { command: head[0], headers, body: text.slice(sep + 2) };
}

function open(token) {
  return new Promise((resolve) => {
    const ws = new WebSocket(url, ["v12.stomp"]);
    let buffer = "";
    const listeners = [];
    ws.onopen = () => ws.send(encode("CONNECT", {
      "accept-version": "1.2", host: "localhost", "heart-beat": "0,0",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }));
    ws.onmessage = (event) => {
      buffer += typeof event.data === "string" ? event.data : Buffer.from(event.data).toString();
      let end;
      while ((end = buffer.indexOf("\0")) >= 0) {
        const frame = decode(buffer.slice(0, end));
        buffer = buffer.slice(end + 1);
        if (frame) listeners.forEach((l) => l(frame));
      }
    };
    ws.onclose = (event) => listeners.forEach((l) => l({ command: "CLOSED", headers: { code: String(event.code) }, body: "" }));
    ws.onerror = () => {};
    resolve({ ws, onFrame: (l) => listeners.push(l) });
  });
}

function expiredJwt() {
  const secret = process.env.JWT_SECRET;
  if (!secret) throw new Error("JWT_SECRET is required for expired-token");
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const body = `${b64({ alg: "HS256" })}.${b64({ sub: "1", username: "admin", role: "ADMIN", iat: now - 7300, exp: now - 100 })}`;
  return `${body}.${createHmac("sha256", secret).update(body).digest("base64url")}`;
}

async function connectOnce(token) {
  const { ws, onFrame } = await open(token);
  return new Promise((resolve) => {
    const frames = [];
    onFrame((f) => {
      frames.push(f);
      log(`${f.command}${f.headers.message ? ` message="${f.headers.message}"` : ""}${f.headers.code ? ` code=${f.headers.code}` : ""}`);
      if (f.command === "CONNECTED") ws.close();
      if (f.command === "CLOSED") resolve(frames);
    });
    setTimeout(() => { ws.close(); resolve(frames); }, 5000);
  });
}

async function watch() {
  const { ws, onFrame } = await open(opts.token);
  const seconds = Number(opts.seconds ?? 30);
  let messages = 0;
  const done = (why) => { log(`done (${why}), ${messages} MESSAGE frame(s)`); ws.close(); process.exit(0); };
  onFrame((f) => {
    if (f.command === "CONNECTED") {
      log(`CONNECTED, SUBSCRIBE ${opts.dest}`);
      ws.send(encode("SUBSCRIBE", { id: "sub-0", destination: opts.dest }));
    } else if (f.command === "MESSAGE") {
      messages++;
      log(`MESSAGE #${messages} ${f.headers.destination} ${f.body}`);
      const status = (() => { try { return JSON.parse(f.body).status; } catch { return undefined; } })();
      if (opts["until-final"] && status && !["PENDING", "RUNNING"].includes(status)) done("final status");
    } else if (f.command === "ERROR") {
      log(`ERROR message="${f.headers.message}"`);
    } else if (f.command === "CLOSED") {
      log(`connection closed by server (code ${f.headers.code})`);
      process.exit(0);
    }
  });
  setTimeout(() => done(`${seconds}s elapsed`), seconds * 1000);
}

if (command === "connect") await connectOnce(opts.token === true ? undefined : opts.token);
else if (command === "expired-token") await connectOnce(expiredJwt());
else if (command === "watch") await watch();
else { console.error("usage: connect | expired-token | watch"); process.exit(2); }
