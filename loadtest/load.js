#!/usr/bin/env node
/*
 * Minimal load test - no dependencies, just Node 18+ fetch.
 *
 * Usage:
 *   node load.js <url> <totalRequests> <concurrency> [cookieHeader]
 *
 * Example:
 *   set COOKIE=JSESSIONID=abc123
 *   node load.js http://localhost:8080/api/conversations 500 25
 *
 * Reports throughput, latency percentiles and error counts. Run it against
 * endpoints that do real work (not static files) for meaningful numbers.
 */

const url = process.argv[2] || 'http://localhost:8080/api/conversations';
const total = Number(process.argv[3] || 200);
const concurrency = Number(process.argv[4] || 20);
const cookie = process.argv[5] || process.env.COOKIE || '';

const latencies = [];
const statuses = new Map();
let completed = 0;

async function one() {
  const started = process.hrtime.bigint();
  let status = 0;
  try {
    const res = await fetch(url, {
      headers: cookie ? { cookie } : {},
    });
    status = res.status;
    await res.arrayBuffer();
  } catch (e) {
    status = -1;
  }
  const ms = Number(process.hrtime.bigint() - started) / 1e6;
  latencies.push(ms);
  statuses.set(status, (statuses.get(status) || 0) + 1);
  completed++;
}

async function worker(id) {
  while (true) {
    const n = next();
    if (n === null) return;
    await one();
  }
}

let cursor = 0;
function next() {
  if (cursor >= total) return null;
  return cursor++;
}

(async () => {
  const started = Date.now();
  await Promise.all(Array.from({ length: concurrency }, (_, i) => worker(i)));
  const elapsed = (Date.now() - started) / 1000;

  latencies.sort((a, b) => a - b);
  const pct = (p) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * p))];

  const errors = [...statuses.entries()].filter(([s]) => s < 200 || s >= 400);

  console.log('URL           :', url);
  console.log('Requests      :', total, '| concurrency:', concurrency);
  console.log('Duration      :', elapsed.toFixed(2) + 's');
  console.log('Throughput    :', (total / elapsed).toFixed(1) + ' req/s');
  console.log('Latency p50   :', pct(0.50).toFixed(1) + ' ms');
  console.log('Latency p95   :', pct(0.95).toFixed(1) + ' ms');
  console.log('Latency p99   :', pct(0.99).toFixed(1) + ' ms');
  console.log('Latency max   :', latencies[latencies.length - 1].toFixed(1) + ' ms');
  console.log('Status codes  :', [...statuses.entries()].map(([s, c]) => `${s}=${c}`).join(' '));
  if (errors.length) {
    console.log('NON-2xx/3xx   :', errors.map(([s, c]) => `${s}=${c}`).join(' '));
  }
})();
