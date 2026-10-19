import { randomUUID } from 'node:crypto';

const baseUrl = process.env.API_URL ?? 'http://localhost:4000';
const token = process.env.TASKFLOW_TOKEN;
const requests = Number(process.env.REQUESTS ?? 20);
const concurrency = Number(process.env.CONCURRENCY ?? 4);
const packageName = process.env.PAYMENT_PACKAGE ?? 'starter';
if (!token) throw new Error('Set TASKFLOW_TOKEN to a valid JWT');
if (process.env.STRIPE_TEST_MODE_ACK !== 'true') throw new Error('Set STRIPE_TEST_MODE_ACK=true only when the API uses Stripe test-mode credentials');
if (!['starter', 'team'].includes(packageName)) throw new Error('PAYMENT_PACKAGE must be starter or team');
if (!Number.isInteger(requests) || requests < 1 || requests > 100 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 10) throw new Error('REQUESTS must be 1-100 and CONCURRENCY must be 1-10');

const samples = [];
const endpoint = `${baseUrl}/api/payments/checkout`;
for (let sent = 0; sent < requests; sent += concurrency) {
  const batch = Math.min(concurrency, requests - sent);
  const results = await Promise.all(Array.from({ length: batch }, async () => {
    const started = performance.now();
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': `benchmark-${randomUUID()}` },
      body: JSON.stringify({ package: packageName })
    });
    const elapsed = performance.now() - started;
    const body = await response.json().catch(() => ({}));
    if (!response.ok || !body.checkoutUrl) throw new Error(`Checkout request failed with HTTP ${response.status}`);
    return elapsed;
  }));
  samples.push(...results);
}
samples.sort((a, b) => a - b);
const percentile = (p) => Number(samples[Math.min(samples.length - 1, Math.ceil(p * samples.length) - 1)].toFixed(2));
console.log(JSON.stringify({ endpoint, requests, concurrency, package: packageName, unit: 'ms', min: Number(samples[0].toFixed(2)), median: percentile(0.5), p95: percentile(0.95), p99: percentile(0.99), max: Number(samples.at(-1).toFixed(2)), note: 'Includes the Stripe test-mode Checkout Session API call; creates unpaid test sessions and no charges.' }, null, 2));
