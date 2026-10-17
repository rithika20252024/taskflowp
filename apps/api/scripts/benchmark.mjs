const baseUrl = process.env.API_URL ?? 'http://localhost:4000';
const teamId = process.env.TEAM_ID;
const token = process.env.TASKFLOW_TOKEN;
const requests = Number(process.env.REQUESTS ?? 100);
const concurrency = Number(process.env.CONCURRENCY ?? 10);
if (!teamId || !token) throw new Error('Set TEAM_ID and TASKFLOW_TOKEN to a team and valid JWT');
if (!Number.isInteger(requests) || requests < 1 || !Number.isInteger(concurrency) || concurrency < 1) throw new Error('REQUESTS and CONCURRENCY must be positive integers');
const samples = [];
const endpoint = `${baseUrl}/api/teams/${encodeURIComponent(teamId)}/tasks?page=1&limit=20`;
for (let sent = 0; sent < requests; sent += concurrency) {
  const batch = Math.min(concurrency, requests - sent);
  const results = await Promise.all(Array.from({ length: batch }, async () => {
    const started = performance.now();
    const response = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
    const elapsed = performance.now() - started;
    if (!response.ok) throw new Error(`Request failed with HTTP ${response.status}`);
    await response.arrayBuffer();
    return elapsed;
  }));
  samples.push(...results);
}
samples.sort((a, b) => a - b);
const percentile = (p) => Number(samples[Math.min(samples.length - 1, Math.ceil(p * samples.length) - 1)].toFixed(2));
console.log(JSON.stringify({ endpoint, requests, concurrency, unit: 'ms', min: Number(samples[0].toFixed(2)), median: percentile(0.5), p95: percentile(0.95), p99: percentile(0.99), max: Number(samples.at(-1).toFixed(2)) }, null, 2));
