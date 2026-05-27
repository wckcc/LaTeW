/**
 * Local API latency probe (loopback). Uses jwt.secret from application.yml
 * to mint a token for a given userId (must exist and own projects for PUT test).
 */
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const ymlPath = path.join(root, "src/main/resources/application.yml");

function loadJwtSecret() {
  const raw = fs.readFileSync(ymlPath, "utf8");
  const m = raw.match(/^\s*secret:\s*(.+)\s*$/m);
  if (!m) throw new Error("jwt.secret not found in application.yml");
  return m[1].trim();
}

function b64url(buf) {
  return Buffer.from(buf)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function signJwtHs256(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const encHeader = b64url(JSON.stringify(header));
  const encPayload = b64url(JSON.stringify(payload));
  const data = `${encHeader}.${encPayload}`;
  const sig = crypto.createHmac("sha256", secret).update(data).digest();
  return `${data}.${b64url(sig)}`;
}

async function timeOnce(url, opts = {}) {
  const { timeoutMs = 20000, ...rest } = opts;
  const t0 = performance.now();
  const res = await fetch(url, {
    ...rest,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const buf = await res.arrayBuffer();
  const ms = performance.now() - t0;
  return { status: res.status, ms, bytes: buf.byteLength };
}

async function medianOfRuns(fn, runs) {
  const times = [];
  for (let i = 0; i < runs; i++) {
    times.push((await fn()).ms);
  }
  times.sort((a, b) => a - b);
  return times[Math.floor(times.length / 2)];
}

const base = process.env.BENCH_BASE || "http://127.0.0.1:8080";
const userId = Number(process.env.BENCH_USER_ID || "1");
const projectId = process.env.BENCH_PROJECT_ID
  ? Number(process.env.BENCH_PROJECT_ID)
  : null;
const secret = loadJwtSecret();
const nowSec = Math.floor(Date.now() / 1000);
const token = signJwtHs256(
  {
    userId,
    username: "bench",
    exp: nowSec + 86400,
    iat: nowSec,
  },
  secret
);

const headers = { token, "Content-Type": "application/json" };
const runs = 7;

async function main() {
  const rows = [];

  // P1 登录（不走 JWT，测真实登录链路；需环境变量 BENCH_EMAIL / BENCH_PASSWORD）
  const email = process.env.BENCH_EMAIL;
  const password = process.env.BENCH_PASSWORD;
  if (email && password) {
    const loginMs = await medianOfRuns(
      () =>
        timeOnce(`${base}/api/users/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        }),
      runs
    );
    rows.push(["P1", "用户登录", Math.round(loginMs), "POST /api/users/login"]);
  } else {
    rows.push([
      "P1",
      "用户登录",
      null,
      "未设置 BENCH_EMAIL/BENCH_PASSWORD，跳过（可设置后重跑）",
    ]);
  }

  const p2 = await medianOfRuns(
    () =>
      timeOnce(`${base}/api/projects/user/${userId}`, { headers: { token } }),
    runs
  );
  rows.push(["P2", "查询项目列表", Math.round(p2), `GET .../user/${userId}`]);

  let pid = projectId;
  if (!pid) {
    const lr = await fetch(`${base}/api/projects/user/${userId}`, {
      headers: { token },
    });
    const j = await lr.json();
    const list = j?.data;
    if (Array.isArray(list) && list.length) pid = list[0].id;
  }

  if (pid) {
    const body = JSON.stringify({
      id: pid,
      name: "bench-touch",
      content: "% bench " + Date.now() + "\n\\documentclass{article}\\begin{document}x\\end{document}\n",
    });
    const p3 = await medianOfRuns(
      () =>
        timeOnce(`${base}/api/projects`, {
          method: "PUT",
          headers,
          body,
        }),
      runs
    );
    rows.push(["P3", "保存论文项目内容", Math.round(p3), "PUT /api/projects"]);
  } else {
    rows.push([
      "P3",
      "保存论文项目内容",
      null,
      "无可用 projectId（设置 BENCH_PROJECT_ID 或确保该用户有项目）",
    ]);
  }

  const p4 = await medianOfRuns(
    () => timeOnce(`${base}/api/templates`, { headers: { token } }),
    runs
  );
  rows.push(["P4", "查询模板列表", Math.round(p4), "GET /api/templates"]);

  if (pid) {
    const p5 = await medianOfRuns(
      () => timeOnce(`${base}/api/projects/${pid}`, { headers: { token } }),
      runs
    );
    rows.push([
      "P5",
      "获取项目详情（含最近元数据，可作“读侧”参考）",
      Math.round(p5),
      `GET /api/projects/${pid}`,
    ]);
  } else {
    rows.push(["P5", "(读侧)", null, "无 projectId"]);
  }

  let p6Note = "POST /api/ai/process (依赖外部模型)";
  let p6Ms = null;
  try {
    const r = await timeOnce(`${base}/api/ai/process`, {
      method: "POST",
      headers,
      timeoutMs: Number(process.env.BENCH_AI_TIMEOUT_MS || 120000),
      body: JSON.stringify({
        projectId: pid || 0,
        content: "Undefined control sequence \\foo",
        type: "ERROR_ANALYSIS",
      }),
    });
    p6Ms = Math.round(r.ms);
    p6Note += ` status=${r.status}`;
  } catch (e) {
    p6Note += ` 失败: ${e?.cause?.code || e.name || e.message}`;
  }
  rows.push(["P6", "AI 错误分析请求", p6Ms, p6Note]);

  console.log(JSON.stringify({ base, userId, projectIdUsed: pid, rows }, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
