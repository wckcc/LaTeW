/**
 * Sample Windows working set (MB) of the JVM listening on BENCH_PORT (default 8080)
 * after each scripted scenario. Requires backend up; optional login + JWT for protected routes.
 *
 * Env: BENCH_PORT, BENCH_EMAIL, BENCH_PASSWORD, BENCH_USER_ID, BENCH_PROJECT_ID,
 *      BENCH_AI_TIMEOUT_MS, BENCH_COMPILE_TIMEOUT_MS, BENCH_SETTLE_MS
 */
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { execSync } from "child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const ymlPath = path.join(root, "src/main/resources/application.yml");
const port = Number(process.env.BENCH_PORT || "8080");
const base = process.env.BENCH_BASE || `http://127.0.0.1:${port}`;
const settleMs = Number(process.env.BENCH_SETTLE_MS || "2000");

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

function listenPid(p) {
  try {
    const o = execSync("netstat -ano", { encoding: "utf8", windowsHide: true });
    for (const line of o.split(/\r?\n/)) {
      if (!line.includes("LISTENING")) continue;
      const parts = line.trim().split(/\s+/);
      const local = parts[1];
      if (!local || !local.endsWith(`:${p}`)) continue;
      const pid = Number(parts[parts.length - 1]);
      if (Number.isFinite(pid) && pid > 0) return pid;
    }
  } catch {
    /* ignore */
  }
  return null;
}

function memMbForPid(pid) {
  try {
    const out = execSync(
      `powershell -NoProfile -Command "[math]::Round((Get-Process -Id ${pid}).WorkingSet64 / 1MB)"`,
      { encoding: "utf8", windowsHide: true }
    ).trim();
    const n = Number(out);
    return Number.isFinite(n) ? n : null;
  } catch {
    return null;
  }
}

function memMbForPort(p) {
  const pid = listenPid(p);
  if (pid == null) return null;
  return memMbForPid(pid);
}

async function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function fetchJson(url, opts = {}) {
  const { timeoutMs = 120000, ...rest } = opts;
  const res = await fetch(url, {
    ...rest,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await res.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }
  return { status: res.status, data };
}

async function main() {
  const rows = [];

  const m0 = memMbForPort(port);
  rows.push({
    scenario: "系统启动完成后",
    mb: m0,
    note: m0 == null ? "端口无监听" : "JVM Working Set（任务管理器同类口径）",
  });
  if (m0 == null) {
    console.log(
      JSON.stringify(
        {
          error: `No process listening on port ${port}. Start the backend first, then re-run.`,
          rows,
        },
        null,
        2
      )
    );
    process.exit(2);
  }

  await sleep(settleMs);

  const email = process.env.BENCH_EMAIL;
  const password = process.env.BENCH_PASSWORD;
  const userId = Number(process.env.BENCH_USER_ID || "1");
  const secret = loadJwtSecret();
  const nowSec = Math.floor(Date.now() / 1000);
  let token = signJwtHs256(
    { userId, username: "bench", exp: nowSec + 86400, iat: nowSec },
    secret
  );

  if (email && password) {
    await fetchJson(`${base}/api/users/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
      timeoutMs: 30000,
    });
  }

  await fetchJson(`${base}/api/projects/user/${userId}`, {
    headers: { token },
    timeoutMs: 30000,
  });
  await sleep(settleMs);
  rows.push({
    scenario: "用户登录并浏览项目列表",
    mb: memMbForPort(port),
    note: "POST /login（若配置）+ GET /api/projects/user/{id}",
  });

  let pid = process.env.BENCH_PROJECT_ID
    ? Number(process.env.BENCH_PROJECT_ID)
    : null;
  if (!pid) {
    const lr = await fetchJson(`${base}/api/projects/user/${userId}`, {
      headers: { token },
    });
    const list = lr.data?.data;
    if (Array.isArray(list) && list.length) pid = list[0].id;
  }

  if (pid) {
    await fetchJson(`${base}/api/projects`, {
      method: "PUT",
      headers: { token, "Content-Type": "application/json" },
      body: JSON.stringify({
        id: pid,
        name: "mem-bench",
        content:
          "% mem " +
          Date.now() +
          "\n\\documentclass{article}\\begin{document}x\\end{document}\n",
      }),
      timeoutMs: 60000,
    });
  }
  await sleep(settleMs);
  rows.push({
    scenario: "编辑并保存论文内容",
    mb: memMbForPort(port),
    note: pid ? "PUT /api/projects" : "跳过保存（无 projectId）",
  });

  if (pid) {
    const compileTo = Number(process.env.BENCH_COMPILE_TIMEOUT_MS || 240000);
    try {
      await fetchJson(`${base}/api/projects/${pid}/compile`, {
        method: "POST",
        headers: { token, "Content-Type": "application/json" },
        body: JSON.stringify({ compiler: "pdflatex" }),
        timeoutMs: compileTo,
      });
    } catch (e) {
      rows.push({
        scenario: "_compile_note",
        mb: null,
        note: String(e?.cause?.code || e.message),
      });
    }
  }
  await sleep(settleMs);
  rows.push({
    scenario: "执行 LaTeX 编译",
    mb: memMbForPort(port),
    note: pid ? "POST /compile 返回后采样（含子进程峰值后的回落）" : "无 projectId",
  });

  if (pid) {
    const aiTo = Number(process.env.BENCH_AI_TIMEOUT_MS || 120000);
    try {
      await fetchJson(`${base}/api/ai/process`, {
        method: "POST",
        headers: { token, "Content-Type": "application/json" },
        body: JSON.stringify({
          projectId: pid,
          content: "Undefined control sequence \\foo",
          type: "ERROR_ANALYSIS",
        }),
        timeoutMs: aiTo,
      });
    } catch (e) {
      rows.push({
        scenario: "_ai_note",
        mb: null,
        note: String(e?.cause?.code || e.message),
      });
    }
  }
  await sleep(settleMs);
  rows.push({
    scenario: "调用 AI 错误分析",
    mb: memMbForPort(port),
    note: "POST /api/ai/process 返回后采样",
  });

  const table = rows
    .filter((r) => !String(r.scenario).startsWith("_"))
    .map((r) => ({
      测试场景: r.scenario,
      "内存占用情况/MB": r.mb,
      运行状态: r.mb != null ? "正常" : "异常",
      说明: r.note,
    }));

  console.log(JSON.stringify({ port, base, table }, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
