'use strict';
// Isolated HTTP fake + real Chromium. No provider APIs, real users, or persistent credentials.
// CHROME_PATH may point to a local Chromium executable. No packages are downloaded.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const {randomBytes} = require('node:crypto');
const {setTimeout: delay} = require('node:timers/promises');

test('authentication and region browser flows, keyboard, lifecycle and mobile layout', {timeout: 90000}, async t => {
  const root = path.resolve(__dirname, '..');
  const files = new Map([['/', 'index.html'], ['/css/style.css', 'css/style.css'], ['/js/auth.js', 'js/auth.js'], ['/js/preview.js', 'js/preview.js'], ['/img/mark.svg', 'img/mark.svg']]);
  const types = {'.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml'};
  const account = {email: `${randomBytes(6).toString('hex')}@example.invalid`, password: randomBytes(12).toString('base64url')};
  const token = Array.from({length: 3}, () => randomBytes(16).toString('base64url')).join('.');
  let responseMode = 'success';
  let loginTtl = 3600;
  let loginCalls = 0;
  let registrationCalls = 0;
  let regionSearchCalls = 0;
  let regionRecommendationCalls = 0;
  let recommendationHeaders;
  const server = http.createServer(async (req, res) => {
    if (req.method === 'POST' && ['/api/users', '/api/auth/login'].includes(req.url)) {
      for await (const _ of req) { /* Discard request data immediately. */ }
      const signup = req.url === '/api/users';
      if (signup) registrationCalls++; else loginCalls++;
      res.setHeader('Content-Type', 'application/json');
      res.setHeader('Cache-Control', 'no-store');
      const mode = responseMode;
      if (mode === 'delayed') await delay(350);
      const status = mode === 'duplicate' ? 409 : mode === 'validation' ? 400 : mode === 'unauthorized' ? 401 : signup ? 201 : 200;
      res.writeHead(status);
      if (status === 201) res.end();
      else if (status === 200) res.end(JSON.stringify({accessToken: token, tokenType: 'Bearer', expiresInSeconds: loginTtl}));
      else res.end(JSON.stringify({code: status === 409 ? 'EMAIL_ALREADY_EXISTS' : status === 400 ? 'VALIDATION_FAILED' : 'AUTHENTICATION_REQUIRED', message: 'untrusted-response', fieldErrors: status === 400 ? [{field: 'email', reason: 'INVALID_FORMAT'}] : [], details: null, adjustments: [], retryAfterSeconds: null}));
      return;
    }
    if (req.method === 'GET' && req.url.startsWith('/api/regions?')) {
      regionSearchCalls++;
      res.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
      res.end(JSON.stringify({regions: [
        {regionId: 'opaque-city', name: '강릉시', shortName: '강릉', provinceName: '강원특별자치도', parentRegionId: 'opaque-province', type: 'CITY', selectable: true, placeSearchFilterable: false},
        {regionId: 'opaque-filter', name: '해운대구', shortName: '해운대', provinceName: '부산광역시', parentRegionId: 'opaque-metro', type: 'DISTRICT_FILTER', selectable: false, placeSearchFilterable: true}
      ]}));
      return;
    }
    if (req.method === 'POST' && req.url === '/api/ai/regions/recommend') {
      recommendationHeaders = req.headers;
      for await (const _ of req) { /* Discard natural-language request immediately. */ }
      regionRecommendationCalls++;
      res.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
      res.end(JSON.stringify({regions: [
        {regionId: 'opaque-a', name: '강릉시', provinceName: '강원특별자치도', reason: '바다와 산을 함께 만날 수 있어요.'},
        {regionId: 'opaque-b', name: '속초시', provinceName: '강원특별자치도', reason: '조용한 바닷가를 둘러볼 수 있어요.'},
        {regionId: 'opaque-c', name: '여수시', provinceName: '전라남도', reason: '섬과 해안 풍경이 이어져요.'}
      ]}));
      return;
    }
    const file = files.get(req.url.split('?')[0]);
    if (!file) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, {'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-store'});
    res.end(await fs.readFile(path.join(root, file)));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'routy-auth-test-'));
  const chrome = spawn(process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', ['--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run', '--disable-background-networking', '--disable-extensions', `--user-data-dir=${profile}`, '--remote-debugging-port=0', 'about:blank'], {stdio: 'ignore', windowsHide: true});
  let launchError;
  chrome.on('error', error => { launchError = error; });
  t.after(() => chrome.kill());
  let port;
  for (let i = 0; i < 100; i++) {
    if (launchError) throw new Error('Could not start local Chromium');
    try { port = (await fs.readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; } catch (_) { await delay(100); }
  }
  assert.ok(port, 'Chromium debugging endpoint started');
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  t.after(() => socket.close());
  const pending = new Map();
  let id = 0;
  let runtimeErrors = 0;
  let sensitiveConsole = false;
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Runtime.exceptionThrown') runtimeErrors++;
    if (message.method === 'Runtime.consoleAPICalled') {
      const output = JSON.stringify(message.params);
      sensitiveConsole ||= output.includes(token) || output.includes(account.password);
    }
    if (pending.has(message.id)) { const done = pending.get(message.id); pending.delete(message.id); done(message); }
  };
  async function send(method, params = {}) {
    const requestId = ++id;
    const result = new Promise(resolve => pending.set(requestId, resolve));
    socket.send(JSON.stringify({id: requestId, method, params}));
    const response = await result;
    assert.equal(Boolean(response.error), false, `${method} succeeds`);
    return response.result;
  }
  async function evaluate(expression) {
    const result = await send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    assert.equal(Boolean(result.exceptionDetails), false, 'browser expression succeeds');
    return result.result.value;
  }
  async function until(expression) {
    for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await delay(30); }
    assert.fail('Browser condition did not become true');
  }
  const click = selector => evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
  async function screenshot(name) {
    const directory = path.resolve(root, '../../../../../build/qa');
    await fs.mkdir(directory, {recursive: true});
    const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false});
    await fs.writeFile(path.join(directory, name), Buffer.from(shot.data, 'base64'));
  }
  async function fill() {
    await evaluate(`document.querySelector('#auth-email').value=${JSON.stringify(account.email)};document.querySelector('#auth-password').value=${JSON.stringify(account.password)}`);
  }
  async function navigate(route) {
    await evaluate(`location.hash=${JSON.stringify('#' + route)}`);
    await delay(60);
  }
  async function key(key, code, windowsVirtualKeyCode, modifiers = 0) {
    await send('Input.dispatchKeyEvent', {type: 'keyDown', key, code, windowsVirtualKeyCode, modifiers});
    if (key === 'Enter') await send('Input.dispatchKeyEvent', {type: 'char', text: '\r', key, code, windowsVirtualKeyCode});
    await send('Input.dispatchKeyEvent', {type: 'keyUp', key, code, windowsVirtualKeyCode, modifiers});
  }
  await send('Runtime.enable');
  await send('Page.enable');
  await send('Page.addScriptToEvaluateOnNewDocument', {source: 'const nativeTimer=window.setTimeout;window.setTimeout=(fn,ms,...args)=>nativeTimer(fn,window.__shortExpiry&&ms>3500000?700:ms,...args);'});
  await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
  await send('Page.navigate', {url: `${origin}/#/workspace`});
  await until(`document.body?.dataset.view === 'auth'`);
  await screenshot('w1-01a-auth-desktop.png');

  await t.test('anonymous deep links are guarded and shared remains public', async () => {
    for (const route of ['/workspace', '/trips', '/trip']) {
      await navigate(route);
      assert.equal(await evaluate(`document.body.dataset.view`), 'auth');
      assert.equal(await evaluate(`document.querySelector('[data-view="workspace"]').hidden`), true);
    }
    await navigate('/shared');
    assert.equal(await evaluate('document.body.dataset.view'), 'shared');
    await navigate('/trips');
  });
  await t.test('native keyboard submission and client validation', async () => {
    await evaluate(`document.querySelector('#auth-email').focus()`);
    await key('Tab', 'Tab', 9);
    assert.equal(await evaluate('document.activeElement.id'), 'auth-password');
    await key('Tab', 'Tab', 9, 8);
    assert.equal(await evaluate('document.activeElement.id'), 'auth-email');
    await key('Enter', 'Enter', 13);
    await until(`document.querySelector('#auth-email').getAttribute('aria-invalid') === 'true'`);
    assert.equal(loginCalls, 0);
  });
  await t.test('server validation and duplicate email are recoverable without response reflection', async () => {
    await click('[data-auth-tab="signup"]');
    for (const mode of ['validation', 'duplicate']) {
      responseMode = mode;
      await fill();
      await click('#auth-submit');
      await until(`document.querySelector('#auth-form').getAttribute('aria-busy') === 'false'`);
      assert.equal(await evaluate(`document.querySelector('#auth-status').dataset.error`), 'true');
      assert.equal(await evaluate(`document.querySelector('#auth-email').value === ${JSON.stringify(account.email)}`), true);
      assert.equal(await evaluate(`document.querySelector('#auth-password').value`), '');
      assert.equal(await evaluate(`document.body.textContent.includes('untrusted-response')`), false);
    }
  });
  await t.test('registration 201 switches to login without authenticating', async () => {
    responseMode = 'success';
    await fill();
    await click('#auth-submit');
    await until(`document.querySelector('#auth-status').textContent.includes('회원가입이 완료')`);
    assert.equal(await evaluate(`document.querySelector('[data-auth-tab="login"]').getAttribute('aria-pressed')`), 'true');
    assert.equal(await evaluate('document.body.dataset.view'), 'auth');
    assert.equal(registrationCalls, 3);
  });
  await t.test('failed login remains anonymous, success returns to requested protected view', async () => {
    responseMode = 'unauthorized';
    await fill();
    await click('#auth-submit');
    await until(`document.querySelector('#auth-status').dataset.error === 'true'`);
    assert.equal(await evaluate('document.body.dataset.view'), 'auth');
    responseMode = 'success';
    await fill();
    await evaluate(`document.querySelector('#auth-password').focus()`);
    await key('Enter', 'Enter', 13);
    await until(`document.body.dataset.view === 'trips'`);
    assert.equal(await evaluate(`document.querySelector('#logout-button').hidden`), false);
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    assert.equal(await evaluate(`indexedDB.databases().then(items=>items.length)`), 0);
    assert.equal(await evaluate(`document.documentElement.outerHTML.includes(${JSON.stringify(token)})`), false);
  });
  await t.test('positive server-configured TTL is accepted without a fixed one-hour check', async () => {
    await click('#logout-button');
    await navigate('/workspace');
    loginTtl = 900;
    responseMode = 'success';
    await fill();
    await click('#auth-submit');
    await until(`document.body.dataset.view === 'workspace'`);
    assert.equal(await evaluate(`document.querySelector('#logout-button').hidden`), false);
    loginTtl = 3600;
  });
  await t.test('direct search and exactly three AI candidates support an explicit region choice', async () => {
    await navigate('/workspace');
    await evaluate(`document.querySelector('#region-query').value='강릉';document.querySelector('#region-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#region-search-results .region-result').length === 2`);
    assert.equal(regionSearchCalls, 1);
    assert.equal(await evaluate(`document.querySelectorAll('#region-search-results [data-region-id]').length`), 1);
    assert.equal(await evaluate(`document.querySelector('#region-search-results').textContent.includes('장소 검색 필터')`), true);
    await click('#region-search-results [data-region-id]');
    assert.equal(await evaluate(`document.querySelector('#summary-region').textContent`), '강릉시');
    await click('[data-region-method="ai"]');
    await evaluate(`document.querySelector('#region-request').value='바다가 있고 조용한 여행';document.querySelector('#region-request').dispatchEvent(new Event('input'));document.querySelector('#region-ai-form').requestSubmit()`);
    await until(`document.querySelectorAll('#region-ai-results .region-result').length === 3`);
    assert.equal(regionRecommendationCalls, 1);
    assert.equal(typeof recommendationHeaders['idempotency-key'], 'string');
    assert.match(recommendationHeaders['idempotency-key'], /^[0-9a-f-]{36}$/);
    assert.equal(recommendationHeaders.authorization, `Bearer ${token}`);
    await click('#region-ai-results [data-region-id="opaque-b"]');
    assert.equal(await evaluate(`document.querySelector('#summary-region').textContent`), '속초시');
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    await screenshot('w1-01b-region-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    await evaluate(`document.querySelector('#region-ai-submit').scrollIntoView({block:'center'})`);
    assert.equal(await evaluate(`(()=>{const r=document.querySelector('#region-ai-submit').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth})()`), true);
    await screenshot('w1-01b-region-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
  });
  await t.test('logout discards current step and prevents back navigation from opening protected content', async () => {
    await navigate('/workspace');
    await click('[data-step="4"]');
    await click('#logout-button');
    await until(`document.body.dataset.view === 'landing'`);
    await navigate('/workspace');
    assert.equal(await evaluate('document.body.dataset.view'), 'auth');
    assert.equal(await evaluate(`document.querySelector('[data-step="0"]').getAttribute('aria-current')`), 'step');
  });
  await t.test('duplicate clicks and mode change invalidate a slow login', async () => {
    responseMode = 'delayed';
    await fill();
    const before = loginCalls;
    await evaluate(`document.querySelector('#auth-submit').click();document.querySelector('#auth-submit').click()`);
    await until(`document.querySelector('#auth-submit').disabled`);
    await delay(60);
    await click('[data-auth-tab="signup"]');
    await delay(450);
    assert.equal(loginCalls, before + 1);
    assert.equal(await evaluate('document.body.dataset.view'), 'auth');
    assert.equal(await evaluate(`document.querySelector('#logout-button').hidden`), true);
  });
  await t.test('mobile auth and error layout fit at 390px and reduced motion keeps inputs usable', async () => {
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]});
    await click('#auth-submit');
    await delay(50);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#login-link')).display !== 'none'`), true);
    await evaluate(`document.querySelector('#auth-submit').scrollIntoView({block:'center'})`);
    assert.equal(await evaluate(`(()=>{const r=document.querySelector('#auth-submit').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<innerHeight-75})()`), true);
    await evaluate(`document.querySelector('#auth-email').value=''`);
    await screenshot('w1-01a-auth-mobile.png');
  });
  await t.test('expiry timer discards workspace and refresh never restores login', async () => {
    await click('[data-auth-tab="login"]');
    responseMode = 'success';
    loginTtl = 1;
    await fill();
    await click('#auth-submit');
    await until(`document.body.dataset.view === 'workspace'`);
    await until(`document.body.dataset.view === 'auth'`);
    assert.equal(await evaluate(`document.querySelector('#auth-status').textContent.includes('만료')`), true);
    loginTtl = 3600;
    await fill();
    await click('#auth-submit');
    await until(`document.body.dataset.view === 'workspace'`);
    await send('Page.reload');
    await until(`document.body?.dataset.view === 'auth'`);
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
  });
  assert.equal(runtimeErrors, 0, 'no uncaught browser exceptions');
  assert.equal(sensitiveConsole, false, 'console does not expose credentials or token');
});
