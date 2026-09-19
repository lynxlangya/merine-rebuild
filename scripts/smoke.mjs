import assert from 'node:assert/strict';

const base = process.env.SMOKE_BASE_URL ?? 'http://127.0.0.1:5173';
const note = `SMOKE-${Date.now()}`;
async function call(path, options) {
  const response = await fetch(`${base}${path}`, {
    ...options,
    signal: AbortSignal.timeout(15000),
  });
  const json = await response.json();
  assert.ok(json.requestId, 'Request must contain a request ID');
  assert.equal(response.headers.get('x-request-id'), json.requestId);
  return { response, json };
}

const before = await call('/api/bootstrap');
assert.equal(before.response.status, 200);
assert.equal(before.json.data.database, 'merine_rebuild');

const created = await call('/api/bootstrap/probes', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ note }),
});
assert.equal(created.response.status, 201);
assert.equal(created.json.data.note, note);
assert.ok(created.json.data.createdAt);

const after = await call('/api/bootstrap');
assert.ok(after.json.data.totalProbes >= before.json.data.totalProbes + 1);
assert.ok(
  after.json.data.recentProbes.some((row) => row.id === created.json.data.id && row.note === note),
);

for (const invalid of ['', ' '.repeat(3), 'x'.repeat(121)]) {
  const result = await call('/api/bootstrap/probes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ note: invalid }),
  });
  assert.equal(result.response.status, 400);
  assert.equal(result.json.code, 'VALIDATION_ERROR');
}
const missing = await call('/api/not-a-route');
assert.equal(missing.response.status, 404);
console.log(
  `PASS: proxy → API → MySQL read/write, validation and 404; synthetic record ${created.json.data.id}`,
);
