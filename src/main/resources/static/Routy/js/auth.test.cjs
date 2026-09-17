'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {randomBytes} = require('node:crypto');
const auth = require('./auth.js');
const credentials = () => ({email: `${randomBytes(6).toString('hex')}@example.invalid`, password: randomBytes(12).toString('base64url')});
const reply = (status, body) => ({status, json: async () => body});
const loginBody = (expiresInSeconds = 3600) => ({accessToken: Array.from({length: 3}, () => randomBytes(16).toString('base64url')).join('.'), tokenType: 'Bearer', expiresInSeconds});
function harness(fetch) {
  let clock = 1000;
  let id = 0;
  const timers = new Map();
  const ended = [];
  const client = auth.createClient({fetch, now: () => clock, setTimer: (fn, ms) => { timers.set(++id, {fn, at: clock + ms}); return id; }, clearTimer: id => timers.delete(id), onSessionEnd: reason => ended.push(reason)});
  return {client, ended, advance(ms) { clock += ms; for (const [id, timer] of [...timers]) if (timer.at <= clock) { timers.delete(id); timer.fn(); } }};
}
test('password validation uses code points, UTF-8 bytes and ISO controls without trimming', () => {
  const {email} = credentials();
  assert.deepEqual(auth.validate(email, ' '.repeat(8)), {});
  assert.deepEqual(auth.validate(email, '가'.repeat(24)), {});
  assert.ok(auth.validate(email, '가'.repeat(25)).password);
  assert.deepEqual(auth.validate(email, '🌏'.repeat(18)), {});
  assert.ok(auth.validate(email, '🌏'.repeat(19)).password);
  assert.ok(auth.validate(email, 'a'.repeat(65)).password);
  assert.ok(auth.validate(email, 'a'.repeat(7)).password);
  assert.ok(auth.validate(email, 'a'.repeat(8) + '\u0085').password);
});
test('invalid input never sends a request', async () => {
  const {client} = harness(() => { throw new Error('must not call'); });
  assert.equal((await client.submit('login', '', '')).code, 'VALIDATION_FAILED');
});
test('registration sends exact credentials and accepts only 201 without requiring JSON', async () => {
  const data = credentials();
  data.password = ` ${data.password} `;
  const {client} = harness(async (path, options) => {
    assert.equal(path, '/api/users');
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.redirect, 'error');
    const sent = JSON.parse(options.body);
    assert.equal(sent.email === data.email && sent.password === data.password, true);
    assert.equal(options.headers.Authorization, undefined);
    return {status: 201, json() { throw new Error('empty body'); }};
  });
  assert.equal((await client.submit('signup', data.email, data.password)).ok, true);
  assert.equal(client.isAuthenticated(), false);
});
test('unexpected success status and malformed login never authenticate', async () => {
  const data = credentials();
  for (const response of [reply(200, null), reply(201, loginBody()), reply(200, {...loginBody(), tokenType: 'Other'}), reply(200, loginBody(0)), reply(200, loginBody(-1)), reply(200, loginBody(1.5)), reply(200, loginBody('3600')), reply(200, loginBody(Number.MAX_SAFE_INTEGER))]) {
    const {client} = harness(async () => response);
    assert.equal((await client.submit('login', data.email, data.password)).ok, false);
    assert.equal(client.isAuthenticated(), false);
  }
});
test('server TTL must be a positive safe integer with a representable absolute expiry', () => {
  assert.equal(auth.expirationTime(1000, 1), 2000);
  for (const ttl of [0, -1, 1.5, '1', NaN, Infinity, Number.MAX_SAFE_INTEGER]) {
    assert.equal(auth.expirationTime(1000, ttl), null);
  }
  assert.equal(auth.expirationTime(-1, 60), null);
});
test('login accepts server-configured TTL and expires from request start consistently', async () => {
  const data = credentials();
  const h = harness(async () => reply(200, loginBody(90)));
  assert.equal((await h.client.submit('login', data.email, data.password)).ok, true);
  h.advance(89999);
  assert.equal(h.client.isAuthenticated(), true);
  h.advance(1);
  assert.equal(h.client.isAuthenticated(), false);
  assert.deepEqual(h.ended, ['expired']);
});
test('TTL longer than the browser timer limit does not expire early', async () => {
  const data = credentials();
  const thirtyDays = 30 * 24 * 60 * 60;
  const h = harness(async () => reply(200, loginBody(thirtyDays)));
  assert.equal((await h.client.submit('login', data.email, data.password)).ok, true);
  h.advance(2147483647);
  assert.equal(h.client.isAuthenticated(), true);
  h.advance(thirtyDays * 1000 - 2147483647);
  assert.equal(h.client.isAuthenticated(), false);
  assert.deepEqual(h.ended, ['expired']);
});
test('successful login holds token privately, adds Bearer only to protected same-origin API', async () => {
  const data = credentials();
  const body = loginBody();
  const h = harness(async (path, options) => {
    if (path === '/api/auth/login') return reply(200, body);
    assert.equal(options.headers.Authorization === `Bearer ${body.accessToken}`, true);
    return reply(200, {});
  });
  assert.deepEqual(await h.client.submit('login', data.email, data.password), {ok: true});
  assert.equal(h.client.isAuthenticated(), true);
  assert.equal(JSON.stringify(h.client).includes(body.accessToken), false);
  await h.client.protectedRequest('/api/travel-plans');
  await assert.rejects(h.client.protectedRequest('https://example.invalid/api/'));
  await assert.rejects(h.client.protectedRequest('//example.invalid/api/'));
  h.advance(3600000);
  assert.equal(h.client.isAuthenticated(), false);
  assert.deepEqual(h.ended, ['expired']);
});
test('server error text and unknown field paths never enter UI error model', () => {
  const secret = randomBytes(12).toString('hex');
  for (const [status, code] of [[400, 'VALIDATION_FAILED'], [401, 'AUTHENTICATION_REQUIRED'], [409, 'EMAIL_ALREADY_EXISTS'], [429, 'RATE_LIMIT_EXCEEDED'], [500, 'INTERNAL_SERVER_ERROR']]) {
    const result = auth.apiError(status, {code, message: secret, fieldErrors: [{field: secret, reason: secret}, {field: 'email', reason: secret}]});
    assert.equal(JSON.stringify(result).includes(secret), false);
    assert.equal(result.ok, false);
  }
});
test('network failures return safe recoverable messages', async () => {
  const data = credentials();
  const {client} = harness(async () => { throw new Error(data.password); });
  const result = await client.submit('login', data.email, data.password);
  assert.equal(result.code, 'NETWORK_ERROR');
  assert.equal(JSON.stringify(result).includes(data.password), false);
});
test('duplicate submission sends only once', async () => {
  const data = credentials();
  let resolve;
  let calls = 0;
  const {client} = harness(() => { calls++; return new Promise(r => { resolve = r; }); });
  const first = client.submit('login', data.email, data.password);
  assert.equal((await client.submit('login', data.email, data.password)).code, 'BUSY');
  resolve(reply(200, loginBody()));
  assert.equal((await first).ok, true);
  assert.equal(calls, 1);
});
test('logout and cancelled form prevent delayed login from recreating a session', async () => {
  for (const action of ['clear', 'cancel']) {
    const data = credentials();
    let resolve;
    const {client} = harness(() => new Promise(r => { resolve = r; }));
    const pending = client.submit('login', data.email, data.password);
    client[action]();
    resolve(reply(200, loginBody()));
    assert.equal((await pending).code, 'CANCELLED');
    assert.equal(client.isAuthenticated(), false);
  }
});
test('cancellation during JSON parsing cannot authenticate', async () => {
  const data = credentials();
  let resolve;
  const {client} = harness(async () => ({status: 200, json: () => new Promise(r => { resolve = r; })}));
  const pending = client.submit('login', data.email, data.password);
  await new Promise(r => setImmediate(r));
  client.clear();
  resolve(loginBody());
  assert.equal((await pending).code, 'CANCELLED');
  assert.equal(client.isAuthenticated(), false);
});
test('protected 401 ends session while 403 preserves it', async () => {
  for (const status of [401, 403]) {
    const data = credentials();
    const {client, ended} = harness(async path => path === '/api/auth/login' ? reply(200, loginBody()) : reply(status, {}));
    await client.submit('login', data.email, data.password);
    await client.protectedRequest('/api/travel-plans');
    assert.equal(client.isAuthenticated(), status === 403);
    assert.deepEqual(ended, status === 401 ? ['expired'] : []);
  }
});
test('old protected response cannot invalidate a new login', async () => {
  const data = credentials();
  let resolve;
  const {client} = harness(async path => path === '/api/auth/login' ? reply(200, loginBody()) : new Promise(r => { resolve = r; }));
  await client.submit('login', data.email, data.password);
  const oldRequest = client.protectedRequest('/api/travel-plans');
  client.clear();
  await client.submit('login', data.email, data.password);
  resolve(reply(401, {}));
  assert.equal((await oldRequest).code, 'CANCELLED');
  assert.equal(client.isAuthenticated(), true);
});
test('protected routes exclude landing auth and shared', () => {
  for (const route of ['/workspace', '/trips', '/trip']) assert.equal(auth.isProtected(route), true);
  for (const route of ['/', '/auth', '/shared']) assert.equal(auth.isProtected(route), false);
});
