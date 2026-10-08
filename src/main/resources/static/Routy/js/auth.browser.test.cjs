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
  const files = new Map([['/', 'index.html'], ['/css/style.css', 'css/style.css'], ['/js/auth.js', 'js/auth.js'], ['/js/place-selection-state.js', 'js/place-selection-state.js'], ['/js/place-workspace.js', 'js/place-workspace.js'], ['/js/menu-workspace.js', 'js/menu-workspace.js'], ['/js/estimate-workspace.js', 'js/estimate-workspace.js'], ['/js/restaurant-workspace.js', 'js/restaurant-workspace.js'], ['/js/review-workspace.js', 'js/review-workspace.js'], ['/js/preview.js', 'js/preview.js'], ['/img/mark.svg', 'img/mark.svg']]);
  files.set('/css/journey.css', 'css/journey.css');
  files.set('/fonts/NanumPenScript-Regular.ttf', 'fonts/NanumPenScript-Regular.ttf');
  for (const name of ['auth-coast.png', 'auth-heritage.png', 'workspace-coast.png']) files.set(`/img/${name}`, `img/${name}`);
  const types = {'.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf'};
  const account = {email: `${randomBytes(6).toString('hex')}@example.invalid`, password: randomBytes(12).toString('base64url')};
  const token = Array.from({length: 3}, () => randomBytes(16).toString('base64url')).join('.');
  let responseMode = 'success';
  let loginTtl = 3600;
  let loginCalls = 0;
  let registrationCalls = 0;
  let regionSearchCalls = 0;
  let regionRecommendationCalls = 0;
  let recommendationHeaders;
  const placeRequests = [];
  const menuRequests = [];
  const estimateRequests = [];
  let estimateMode = 'success';
  const restaurantRequests = [];
  let restaurantMode = 'success';
  const createRequests = [];
  let createMode = 'success';
  let menuMode = 'success';
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
      if (req.url.includes('%EB%B6%80%EC%82%B0')) {
        res.end(JSON.stringify({regions: [
          {regionId: 'opaque-metro', name: '부산광역시', provinceName: '', parentRegionId: null, type: 'METROPOLITAN_CITY', selectable: true, placeSearchFilterable: false},
          {regionId: 'opaque-filter', name: '해운대구', provinceName: '부산광역시', parentRegionId: 'opaque-metro', type: 'DISTRICT_FILTER', selectable: false, placeSearchFilterable: true}
        ]}));
        return;
      }
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
    if (req.method === 'POST' && req.url === '/api/ai/menus/analyze') {
      let raw = '';
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw);
      menuRequests.push({body, headers: req.headers});
      const mode = menuMode;
      if (mode === 'delayed') await delay(350);
      res.setHeader('Content-Type', 'application/json');
      res.setHeader('Cache-Control', 'no-store');
      if (mode === 'unavailable') {
        res.writeHead(503);
        res.end(JSON.stringify({code: 'AI_UNAVAILABLE', message: 'untrusted-response'}));
      } else {
        res.writeHead(200);
        res.end(JSON.stringify({menus: [
          {name: '가상 국밥', searchQuery: '국밥', reason: '따뜻한 한 끼', targetClientPlaceId: body.attractions[0]?.clientPlaceId || null},
          {name: '가상 전골', searchQuery: '전골', reason: '함께 먹기 좋은 메뉴', targetClientPlaceId: null}
        ]}));
      }
      return;
    }
    if (req.method === 'POST' && req.url === '/api/travel-plans/estimate') {
      let raw = '';
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw);
      estimateRequests.push(body);
      res.setHeader('Content-Type', 'application/json');
      if (estimateMode === 'capacity') {
        res.writeHead(422);
        res.end(JSON.stringify({code: 'PLAN_CAPACITY_EXCEEDED', message: 'fixed', details: {date: body.startDate, plannedEndTime: '20:30', allowedEndTime: '20:00', exceededMinutes: 30}, adjustments: ['CHANGE_END_TIME']}));
      } else {
        res.writeHead(200);
        res.end(JSON.stringify({routeVerified: false, days: body.days.map((day, dayIndex) => ({date: day.date, items: [
          ...(dayIndex === 0 ? body.places.map((place, index) => ({clientPlaceId: place.clientPlaceId, order: index + 1, type: 'VISIT', displayName: place.displayName, startTime: '10:00', endTime: '11:30', estimatedMinutes: null})) : []),
          ...(dayIndex === 0 ? [{clientPlaceId: null, order: body.places.length + 1, type: 'MOVE', displayName: null, startTime: '11:30', endTime: '12:00', estimatedMinutes: 30}] : []),
          {clientPlaceId: null, order: dayIndex === 0 ? body.places.length + 2 : 1, type: 'MEAL', displayName: null, startTime: '12:00', endTime: '13:00', estimatedMinutes: null},
          {clientPlaceId: null, order: dayIndex === 0 ? body.places.length + 3 : 2, type: 'MEAL', displayName: null, startTime: '18:00', endTime: '19:00', estimatedMinutes: null}
        ]}))}));
      }
      return;
    }
    if (req.method === 'POST' && req.url === '/api/travel-plans') {
      let raw = '';
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw);
      createRequests.push({body, headers: req.headers});
      const mode = createMode;
      if (mode === 'delayedRoute') await delay(350);
      res.setHeader('Content-Type', 'application/json');
      res.setHeader('Cache-Control', 'no-store');
      if (mode === 'route' || mode === 'delayedRoute') {
        res.writeHead(422);
        res.end(JSON.stringify({code: 'ROUTE_NOT_FOUND', message: 'untrusted-provider-message', details: {date: body.startDate, moveOrder: 2, travelMode: body.travelMode}}));
      } else {
        res.writeHead(201, {Location: '/api/travel-plans/42'});
        res.end(JSON.stringify({travelPlanId: 42, title: body.title, region: {regionId: body.regionId, displayName: '부산광역시'}, travelMode: body.travelMode,
          startDate: body.startDate, endDate: body.endDate, warnings: ['ESTIMATED_TRAVEL_TIMES_USED'],
          hotel: body.hotelSelectionToken ? {planPlaceId: 8, displayName: body.hotelDisplayName, memo: null, placeUrl: 'https://place.map.kakao.com/hotel'} : null,
          days: body.days.map((day, index) => ({day: index + 1, date: day.date, activityStartTime: day.activityStartTime, activityEndTime: day.activityEndTime,
            items: index === 0 ? [
              {itemId: 1, order: 1, type: 'VISIT', planPlaceId: 7, displayName: body.places[0].displayName, placeUrl: 'https://place.map.kakao.com/attraction', startTime: '10:00', endTime: '11:30', stayMinutes: 90, estimatedMinutes: null, memo: null},
              {itemId: 2, order: 2, type: 'MOVE', planPlaceId: null, displayName: null, placeUrl: null, startTime: '11:30', endTime: '12:00', stayMinutes: null, estimatedMinutes: 30, memo: null}
            ] : []}))}));
      }
      return;
    }
    if (req.method === 'POST' && req.url === '/api/places/restaurants/search') {
      let raw = '';
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw); restaurantRequests.push({body, headers: req.headers});
      res.setHeader('Content-Type', 'application/json');
      if (restaurantMode === 'unavailable' || restaurantMode === 'invalid') {
        res.writeHead(restaurantMode === 'invalid' ? 400 : 503);
        res.end(JSON.stringify({code: restaurantMode === 'invalid' ? 'VALIDATION_FAILED' : 'PLACE_PROVIDER_UNAVAILABLE', message: 'untrusted-provider-response'})); return;
      }
      const ref = {kind: 'ATTRACTION', clientPlaceId: body.estimate.places[0].clientPlaceId, latitude: 36, longitude: 127};
      const id = body.bounds ? 'restaurant-map' : `restaurant-${body.page}`;
      res.writeHead(200);
      res.end(JSON.stringify({mealDate: body.mealDate, mealStartTime: body.mealType === 'DINNER' ? '18:00' : '12:00', mealEndTime: body.mealType === 'DINNER' ? '19:00' : '13:00', previousPlace: ref,
        nextPlace: {kind: 'END_BOUNDARY', clientPlaceId: null, latitude: 36.01, longitude: 127.01},
        referenceAttraction: body.referenceAttractionClientPlaceId ? ref : null,
        places: restaurantMode === 'empty' ? [] : [{kakaoPlaceId: id, placeUrl: `https://place.map.kakao.com/${id}`,
          providerDisplayName: body.bounds ? '가상 음식점 지도' : `가상 음식점 ${body.page}`, address: '가상 주소', latitude: 36.005, longitude: 127.005,
          selectionToken: `fake-${id}-token`, detourKilometers: 0.5, estimatedDetourMinutes: 10}],
        emptyReason: restaurantMode === 'empty' ? 'NO_CANDIDATES' : null, page: body.page, hasNext: body.page === 1 && restaurantMode !== 'empty'}));
      return;
    }
    if (req.method === 'POST' && ['/api/places/search', '/api/places/travel-boundaries/search', '/api/places/hotels/search'].includes(req.url)) {
      let raw = '';
      for await (const chunk of req) raw += chunk;
      const body = JSON.parse(raw);
      placeRequests.push({path: req.url, body, headers: req.headers});
      const hotel = req.url.includes('/hotels/');
      const boundary = req.url.includes('/travel-boundaries/');
      const id = hotel ? 'hotel-1' : boundary ? 'boundary-1' : 'attraction-1';
      res.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
      res.end(JSON.stringify({places: [{kakaoPlaceId: id, placeUrl: `https://place.map.kakao.com/${id}`,
        providerDisplayName: hotel ? '가상 숙소' : boundary ? '가상 역' : '가상 해변', address: '가상 주소',
        latitude: 37.5, longitude: 127.0, ...(hotel || boundary ? {} : {suggestedStayMinutes: 90}), selectionToken: `fake-${id}-token`}], page: body.page, hasNext: false}));
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
  async function designScreenshot(name) {
    await evaluate('document.fonts.ready.then(() => true)');
    const scroll = await evaluate('scrollY');
    await delay(150);
    await evaluate('window.scrollTo({top:0,behavior:"instant"})');
    await until('scrollY === 0');
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'design fits viewport');
    await screenshot(name);
    await evaluate(`window.scrollTo({top:${scroll},behavior:"instant"})`);
  }
  async function mobileDesignScreenshot(name) {
    const size = await evaluate('({width: innerWidth, height: innerHeight})');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await designScreenshot(name);
    await send('Emulation.setDeviceMetricsOverride', {...size, deviceScaleFactor: 1, mobile: size.width <= 700});
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
  await evaluate('document.fonts.ready.then(() => true)');
  assert.equal(await evaluate('document.fonts.check(\'24px "Routy Handwriting"\')'), true, 'local decoration font loads');
  await until(`Array.from(document.querySelectorAll('.auth-photos img')).every(img => img.complete && img.naturalWidth > 0)`);
  assert.equal(await evaluate(`Array.from(document.querySelectorAll('.auth-photos img')).every(img => Math.abs(img.clientWidth / img.clientHeight - 1.5) < .03)`), true, 'login photos retain landscape aspect ratio');
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
    assert.equal(await evaluate(`document.querySelector('.summary-empty').hidden`), false);
    assert.equal(await evaluate(`document.querySelector('.summary-list').hidden`), true);
    await screenshot('w1-workspace-initial-desktop.png');
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
    assert.equal(await evaluate(`document.querySelector('.summary-list').hidden`), false);
    assert.equal(await evaluate(`document.querySelector('#summary-days').textContent`), '설정 전');
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
  await t.test('W1-02 keeps place selections in memory and uses dedicated place APIs', async () => {
    await navigate('/workspace');
    assert.equal(await evaluate(`document.querySelector('[data-step="2"]').disabled`), true);
    await click('[data-step="2"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="0"]').getAttribute('aria-current')`), 'step');
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="1"]').getAttribute('aria-current')`), 'step');
    await designScreenshot('w1-06-conditions-desktop.png');
    await mobileDesignScreenshot('w1-06-conditions-mobile.png');
    await evaluate(`document.querySelector('#trip-days').value='2';document.querySelector('#trip-days').dispatchEvent(new Event('change'))`);
    await click('#next-step');
    await evaluate(`document.querySelector('#attraction-query').value='해변';document.querySelector('#attraction-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#place-results .place-result').length === 1`);
    await designScreenshot('w1-06-attractions-desktop.png');
    const attractionRequest = placeRequests.at(-1);
    assert.equal(attractionRequest.path, '/api/places/search');
    assert.equal(attractionRequest.body.placeRole, 'ATTRACTION');
    assert.equal(attractionRequest.body.radiusMeters, 20000);
    assert.match(attractionRequest.headers['idempotency-key'], /^[0-9a-f-]{36}$/);
    await click('#place-results .place-result button');
    assert.equal(await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value`), '');
    assert.equal(await evaluate(`document.querySelector('#attraction-selected input[type="text"]').placeholder.includes('가상 해변')`), false);
    await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value='내 바닷가';document.querySelector('#attraction-selected input[type="text"]').dispatchEvent(new Event('input'))`);
    await click('[data-place-role="TRAVEL_BOUNDARY"]');
    await evaluate(`document.querySelector('#boundary-query').value='역';document.querySelector('#boundary-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#place-results .place-result').length === 1`);
    assert.equal(placeRequests.at(-1).path, '/api/places/travel-boundaries/search');
    await click('#place-results .place-result button');
    await click('input[name="boundary-slot"][value="END"]');
    await click('#place-results .place-result button');
    assert.equal(await evaluate(`document.querySelector('#boundary-selected').textContent.includes('시작: 가상 역') && document.querySelector('#boundary-selected').textContent.includes('종료: 가상 역')`), true);
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').disabled`), true);
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="2"]').getAttribute('aria-current')`), 'step');
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="3"]').getAttribute('aria-current')`), 'step');
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="3"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#hotel-status').textContent.includes('숙소를 직접 선택')`), true);
    await evaluate(`document.querySelector('#hotel-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#hotel-results .place-result').length === 1`);
    assert.equal(placeRequests.at(-1).path, '/api/places/hotels/search');
    assert.deepEqual(placeRequests.at(-1).body.attractionSelectionTokens, ['fake-attraction-1-token']);
    await click('#hotel-results .place-result button');
    assert.equal(await evaluate(`document.querySelector('#summary-places').textContent`), '1곳');
    assert.equal(await evaluate(`(()=>{const heading=document.querySelector('#step-title').getBoundingClientRect();return heading.top >= document.querySelector('.site-header').getBoundingClientRect().bottom && heading.top < innerHeight})()`), true);
    assert.equal(await evaluate(`document.querySelector('#hotel-map').classList.contains('is-unavailable')`), true);
    await screenshot('w1-02-hotel-desktop.png');
    await mobileDesignScreenshot('w1-06-hotel-mobile.png');
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    assert.equal(await evaluate(`document.documentElement.outerHTML.includes('fake-attraction-1-token')`), false);
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').getAttribute('aria-current')`), 'step');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await click('[data-step="2"]');
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    await screenshot('w1-02-attractions-mobile.png');
    await click('#attractions-workspace [data-place-view="map"]');
    assert.equal(await evaluate(`document.querySelector('#attractions-workspace .place-layout').dataset.mobileView`), 'map');
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#attractions-workspace .place-list-pane')).display`), 'none');
    assert.equal(await evaluate(`document.querySelector('#attractions-workspace [data-place-view="map"]').getAttribute('aria-pressed')`), 'true');
    await evaluate(`document.querySelector('#place-map').scrollIntoView({block:'center'})`);
    await screenshot('w1-02-map-mobile.png');
    await click('#attractions-workspace [data-place-view="list"]');
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#attractions-workspace .place-list-pane')).display !== 'none'`), true);
    assert.equal(await evaluate(`document.querySelector('#boundary-selected').textContent.includes('시작: 가상 역')`), true);
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').getAttribute('aria-current')`), 'step');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await click('[data-step="1"]');
    await evaluate(`document.querySelector('#trip-days').value='1';document.querySelector('#trip-days').dispatchEvent(new Event('change'))`);
    await click('#next-step');
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('[data-step="3"]').disabled`), true);
    await click('[data-step="3"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#hotel-selected').textContent.includes('선택한 숙소 없음')`), true);
    await click('#previous-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="2"]').getAttribute('aria-current')`), 'step');
  });
  await t.test('W1-02A analyzes, edits, adds and confirms menus without losing drafts on failure', async () => {
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('#menu-workspace').hidden`), false);
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="4"]').getAttribute('aria-current')`), 'step');
    menuMode = 'unavailable';
    await evaluate(`document.querySelector('#menu-request').value='따뜻한 지역 음식';document.querySelector('#menu-analysis-form').requestSubmit()`);
    await until(`document.querySelector('#menu-status').textContent.includes('직접 메뉴를 입력')`);
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 0);
    await evaluate(`document.querySelector('#menu-direct-name').value='직접 고른 메뉴';document.querySelector('#menu-direct-query').value='직접 검색어';document.querySelector('#menu-direct-query').focus()`);
    await key('Enter', 'Enter', 13);
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 1);
    await click('#menu-confirm');
    assert.equal(await evaluate(`document.querySelector('#menu-confirmed-status').textContent.includes('확정했습니다')`), true);
    await evaluate(`document.querySelector('#menu-request').value='따뜻한 지역 음식';document.querySelector('#menu-analysis-form').requestSubmit()`);
    await until(`document.querySelector('#menu-status').textContent.includes('직접 메뉴를 입력')`);
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 1);
    assert.equal(await evaluate(`document.querySelector('#menu-confirmed-status').textContent.includes('확정했습니다')`), true);
    menuMode = 'success';
    await evaluate(`document.querySelector('#menu-analysis-form').requestSubmit()`);
    await until(`document.querySelectorAll('#menu-draft .menu-card').length === 2`);
    assert.equal(menuRequests.at(-1).body.regionId, 'opaque-b');
    assert.equal(menuRequests.at(-1).body.request, '따뜻한 지역 음식');
    assert.equal(menuRequests.at(-1).body.attractions.length, 1);
    assert.match(menuRequests.at(-1).body.attractions[0].clientPlaceId, /^[0-9a-f-]{36}$/);
    assert.equal(menuRequests.at(-1).body.attractions[0].displayName, '내 바닷가');
    assert.equal(JSON.stringify(menuRequests.at(-1).body).includes('fake-attraction-1-token'), false);
    assert.match(menuRequests.at(-1).headers['idempotency-key'], /^[0-9a-f-]{36}$/);
    assert.notEqual(menuRequests.at(-1).headers['idempotency-key'], menuRequests.at(-2).headers['idempotency-key']);
    assert.equal(menuRequests.at(-1).headers.authorization, `Bearer ${token}`);
    await screenshot('w1-02a-menu-desktop.png');
    await evaluate(`document.querySelector('#menu-draft .menu-card input').value='내 국밥';document.querySelector('#menu-draft .menu-card input').dispatchEvent(new Event('input'))`);
    await click('#menu-draft .menu-card:nth-child(2) button');
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 1);
    menuMode = 'delayed';
    await evaluate(`document.querySelector('#menu-analysis-form').requestSubmit()`);
    await until(`document.querySelector('#menu-submit').disabled`);
    await evaluate(`document.querySelector('#menu-direct-name').value='직접 추가한 메뉴';document.querySelector('#menu-direct-query').value='직접 추가 검색어';document.querySelector('#menu-direct-form').requestSubmit()`);
    await delay(450);
    assert.equal(await evaluate(`document.querySelector('#menu-draft').textContent.includes('직접 추가한 메뉴')`), false);
    assert.equal(await evaluate(`Array.from(document.querySelectorAll('#menu-draft .menu-card input')).some(input => input.value === '직접 추가한 메뉴')`), true);
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 2);
    menuMode = 'success';
    await click('#menu-confirm');
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="5"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#next-step').textContent`), '다음: 음식점 선택 →');
      await click('#estimate-submit');
      assert.equal(estimateRequests.length, 0);
      await evaluate(`document.querySelector('#estimate-start-date').value='2026-10-01';document.querySelector('#estimate-start-date').dispatchEvent(new Event('change'))`);
      assert.equal(await evaluate(`document.querySelector('#summary-date').textContent`), '2026-10-01');
      estimateMode = 'capacity';
      await click('#estimate-submit');
      await until(`document.querySelector('#estimate-status').textContent.includes('30분 초과')`);
      assert.equal(await evaluate(`document.querySelector('#estimate-start-date').value`), '2026-10-01');
      assert.equal(await evaluate(`document.querySelector('#estimate-result').textContent`), '');
      estimateMode = 'success';
      await click('#estimate-submit');
      await until(`document.querySelector('#estimate-result section') !== null`);
      assert.equal(await evaluate(`document.activeElement === document.querySelector('#estimate-status')`), false);
      assert.equal(estimateRequests.at(-1).days.length, 1);
      assert.equal(estimateRequests.at(-1).places[0].selectionToken.startsWith('fake-'), true);
      assert.equal(await evaluate(`document.querySelector('#estimate-status').textContent.includes('실제 경로 검증 전')`), true);
      assert.equal(await evaluate(`document.documentElement.outerHTML.includes('fake-attraction-1-token')`), false);
      await click('#estimate-manual');
      assert.equal(await evaluate(`document.querySelector('#estimate-placement').hidden`), false);
      await click('#estimate-submit');
      await until(`document.querySelector('#estimate-result section') !== null`);
      assert.equal(estimateRequests.at(-1).places[0].day, '2026-10-01');
      assert.equal(estimateRequests.at(-1).places[0].order, 1);
      await screenshot('w1-03-estimate-desktop.png');
      await evaluate(`document.querySelector('#estimate-result').scrollIntoView({block:'start',behavior:'instant'})`);
      await delay(150);
      await screenshot('w1-03-result-desktop.png');
      await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
      assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
      assert.equal(await evaluate(`getComputedStyle(document.querySelector('.journey-summary')).display`), 'block');
      await screenshot('w1-03-estimate-mobile.png');
      await evaluate(`document.querySelector('#estimate-result').scrollIntoView({block:'start',behavior:'instant'})`);
      await delay(150);
      assert.equal(await evaluate(`document.querySelector('#estimate-result li').getBoundingClientRect().bottom <= document.querySelector('.canvas-actions').getBoundingClientRect().top`), true);
      await screenshot('w1-03-result-mobile.png');
      await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
      assert.deepEqual(await evaluate(`(async () => {
        const doc = document.implementation.createHTMLDocument('route-error');
        doc.body.innerHTML = '<section id="estimate-workspace"><form id="estimate-form"><input id="estimate-start-date" type="date"><div id="estimate-day-fields"></div><input id="estimate-manual" type="checkbox"><div id="estimate-placement"></div><button id="estimate-submit"></button></form><p id="estimate-status" tabindex="-1"></p><div id="estimate-result"></div></section>';
        doc.querySelector('#estimate-start-date').value = '2026-10-01';
        const context = () => ({regionId: 'KR-26', summary: {days: 1, travelMode: 'CAR'}, selected: {startBoundary: {selectionToken: 'start'}, endBoundary: {selectionToken: 'end'}}, places: [{clientPlaceId: 'place-1', selectionToken: 'place', displayName: '산책', stayMinutes: 90, day: null, order: null}], foods: ['국밥']});
        const body = {routeVerified: false, days: [{date: '2026-10-01', items: [{order: 1, type: 'VISIT', clientPlaceId: 'place-1', displayName: '산책', startTime: '10:00', endTime: '11:30'}, {order: 2, type: 'MOVE', startTime: '11:30', endTime: '12:00', estimatedMinutes: 30}, {order: 3, type: 'MEAL', startTime: '12:00', endTime: '13:00'}]}]};
        let restaurants = [];
        const controller = RoutyEstimateWorkspace.mount(doc, window, {protectedRequest: async () => new Response(JSON.stringify(body), {status: 200})}, context, () => restaurants);
        doc.querySelector('#estimate-form').dispatchEvent(new Event('submit', {cancelable: true}));
        for (let attempt = 0; attempt < 20 && !doc.querySelector('#estimate-result section'); attempt++) await new Promise(resolve => setTimeout(resolve, 10));
        controller.showRouteNotFound({code: 'ROUTE_NOT_FOUND', details: {date: '2026-10-01', moveOrder: 2, travelMode: 'CAR'}, message: 'untrusted'});
        const exact = [...doc.querySelectorAll('.estimate-route-failure')].map(item => item.querySelector('strong').textContent);
        const retained = doc.querySelector('#estimate-start-date').value === '2026-10-01' && controller.hasCurrentResult();
        controller.showRouteNotFound({code: 'ROUTE_NOT_FOUND', details: {date: '2026-10-01', moveOrder: 9, travelMode: 'CAR'}});
        const fallback = doc.querySelector('#estimate-status').textContent.includes('해당 날짜');
        restaurants = [{slot: '2026-10-01|LUNCH', selectionToken: 'restaurant'}];
        controller.showRouteNotFound({code: 'ROUTE_NOT_FOUND', details: {date: '2026-10-01', moveOrder: 2, travelMode: 'CAR'}});
        const restaurantFallback = doc.querySelector('#estimate-status').textContent.includes('해당 날짜');
        restaurants = [];
        controller.showStep(6);
        controller.showStep(5);
        return {exact, retained, fallback, restaurantFallback, staleCleared: doc.querySelector('#estimate-status').textContent === '', remaining: doc.querySelectorAll('.estimate-route-failure').length, reflected: doc.body.textContent.includes('untrusted')};
      })()`), {exact: ['산책', '이동 · 예상 30분', '식사'], retained: true, fallback: true, restaurantFallback: true, staleCleared: true, remaining: 0, reflected: false});
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="6"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#next-step').textContent`), '검토 화면 미리보기 →');
    assert.equal(await evaluate(`document.querySelector('#restaurant-slot').options.length`), 2);
    assert.equal(await evaluate(`document.querySelector('#restaurant-layout').hidden`), true);
    assert.equal(await evaluate(`document.querySelector('#restaurant-intro').hidden`), false);
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#restaurant-results .place-result').length === 1`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-layout').hidden`), false);
    assert.equal(restaurantRequests.at(-1).body.mealType, 'LUNCH');
    assert.equal(restaurantRequests.at(-1).body.estimate.places[0].order, 1);
    assert.equal(restaurantRequests.at(-1).body.estimate.places[0].day, '2026-10-01');
    assert.equal(restaurantRequests.at(-1).body.referenceAttractionClientPlaceId, restaurantRequests.at(-1).body.estimate.places[0].clientPlaceId);
    assert.match(restaurantRequests.at(-1).headers['idempotency-key'], /^[0-9a-f-]{36}$/);
    assert.equal(await evaluate(`document.querySelector('#restaurant-context').textContent.includes('직전 장소') && document.querySelector('#restaurant-context').textContent.includes('직후 장소')`), true);
    await screenshot('w1-03a-candidates-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await evaluate(`document.querySelector('#restaurant-results').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    assert.equal(await evaluate(`document.querySelector('#restaurant-results .place-result').getBoundingClientRect().bottom <= document.querySelector('.canvas-actions').getBoundingClientRect().top`), true);
    await screenshot('w1-03a-candidates-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await click('#restaurant-results .place-result button');
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('가상 음식점 1')`), true);
    await evaluate(`document.querySelector('#restaurant-slot').value='2026-10-01|DINNER';document.querySelector('#restaurant-slot').dispatchEvent(new Event('change'))`);
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-status').textContent.includes('1곳을 찾았습니다')`);
    assert.equal(restaurantRequests.at(-1).body.mealType, 'DINNER');
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('2026-10-01 점심')`), true);
    await evaluate(`document.querySelector('#restaurant-slot').value='2026-10-01|LUNCH';document.querySelector('#restaurant-slot').dispatchEvent(new Event('change'))`);
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#restaurant-results .place-result').length === 1`);
    await evaluate(`window.__restaurantMarkers=[];window.kakao={maps:{
      LatLng:class{constructor(lat,lng){this.lat=lat;this.lng=lng}getLat(){return this.lat}getLng(){return this.lng}},
      LatLngBounds:class{constructor(){this.points=[]}extend(p){this.points.push(p)}getSouthWest(){return new window.kakao.maps.LatLng(Math.min(...this.points.map(p=>p.lat)),Math.min(...this.points.map(p=>p.lng)))}getNorthEast(){return new window.kakao.maps.LatLng(Math.max(...this.points.map(p=>p.lat)),Math.max(...this.points.map(p=>p.lng))) }},
      Map:class{constructor(){this.area=null}relayout(){}setBounds(area){this.area=area}getBounds(){return this.area}},
      Marker:class{constructor(options){this.map=options.map;this.title=options.title;window.__restaurantMarkers.push(this)}setMap(map){this.map=map}},
      MarkerImage:class{},Size:class{},event:{addListener(marker,event,handler){marker.choose=handler}}
    }}`);
    await click('#restaurant-workspace [data-restaurant-view="map"]');
    await until(`window.__restaurantMarkers.some(marker => marker.map && marker.title.includes('가상 음식점'))`);
    assert.equal(await evaluate(`window.__restaurantMarkers.filter(marker => marker.map && marker.title.includes('직전 장소')).length`), 1);
    await evaluate(`window.__restaurantMarkers.find(marker => marker.map && marker.title.includes('가상 음식점')).choose()`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-results .place-result button').getAttribute('aria-pressed')`), 'false');
    await evaluate(`window.__restaurantMarkers.find(marker => marker.map && marker.title.includes('가상 음식점')).choose()`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-results .place-result button').getAttribute('aria-pressed')`), 'true');
    await click('#restaurant-search-map');
    await until(`document.querySelector('#restaurant-results').textContent.includes('가상 음식점 지도')`);
    assert.equal(Boolean(restaurantRequests.at(-1).body.bounds), true);
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('가상 음식점 1')`), true);
    await click('#restaurant-next-page');
    await until(`document.querySelector('#restaurant-page').textContent === '2'`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('가상 음식점 1')`), true);
    restaurantMode = 'unavailable';
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-status').textContent.includes('잠시')`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('가상 음식점 1')`), true);
    restaurantMode = 'empty';
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-status').textContent.includes('후보가 없습니다')`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-results .workspace-empty h3').textContent`), '조건에 맞는 음식점이 없어요');
    assert.equal(await evaluate(`document.querySelector('#restaurant-layout').dataset.mobileView`), 'list');
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent.includes('가상 음식점 1')`), true);
    await screenshot('w1-03a-restaurant-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    await screenshot('w1-03a-restaurant-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    restaurantMode = 'invalid';
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-status').textContent.includes('다시 계산')`);
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent`), '');
    restaurantMode = 'success';
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('#menu-draft .menu-card input').value`), '내 국밥');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    await screenshot('w1-02a-menu-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await click('[data-step="2"]');
    await click('[data-place-role="ATTRACTION"]');
    await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value='새 바닷가';document.querySelector('#attraction-selected input[type="text"]').dispatchEvent(new Event('input'))`);
    await click('[data-step="4"]');
    assert.equal(await evaluate(`document.querySelector('#menu-confirmed-status').textContent.includes('검토 중')`), true);
    assert.equal(await evaluate(`document.querySelector('#menu-draft select').textContent.includes('새 바닷가')`), true);
  });
  await t.test('W1-02 district filter is explicit and a new region discards prior place selections', async () => {
    await click('[data-step="0"]');
    await evaluate(`document.querySelector('#region-query').value='부산';document.querySelector('#region-search-form').requestSubmit()`);
    await until(`document.querySelector('#region-search-results [data-region-id="opaque-metro"]') !== null`);
    await click('#region-search-results [data-region-id="opaque-metro"]');
    assert.equal(await evaluate(`document.querySelector('#summary-places').textContent`), '선택 전');
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 0);
    await click('#next-step'); await click('#next-step');
    await evaluate(`document.querySelector('#district-query').value='해운대';document.querySelector('#district-form').requestSubmit()`);
    await until(`document.querySelector('#district-results button') !== null`);
    await click('#district-results button');
    await evaluate(`document.querySelector('#attraction-query').value='해변';document.querySelector('#attraction-search-form').requestSubmit()`);
    await until(`document.querySelector('#place-status').textContent.includes('1곳을 찾았습니다')`);
    assert.equal(placeRequests.at(-1).body.districtFilterId, 'opaque-filter');
    await click('#district-current button');
    const before = placeRequests.length;
    await evaluate(`document.querySelector('#attraction-search-form').requestSubmit()`);
    for (let i = 0; i < 100 && placeRequests.length === before; i++) await delay(30);
    assert.equal(placeRequests.length, before + 1);
    assert.equal(Object.hasOwn(placeRequests.at(-1).body, 'districtFilterId'), false);
  });
  await t.test('W1-04 reviews each day, handles stale and 422 responses, then shows only the saved plan', async () => {
    await click('#place-results .place-result button');
    await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value='내 해변';document.querySelector('#attraction-selected input[type="text"]').dispatchEvent(new Event('input'))`);
    await click('[data-place-role="TRAVEL_BOUNDARY"]');
    await evaluate(`document.querySelector('#boundary-query').value='역';document.querySelector('#boundary-search-form').requestSubmit()`);
    await until(`document.querySelector('#place-results').textContent.includes('가상 역')`);
    await click('#place-results .place-result button');
    await click('input[name="boundary-slot"][value="END"]');
    await click('#place-results .place-result button');
    await evaluate(`document.querySelector('#trip-days').value='2';document.querySelector('#trip-days').dispatchEvent(new Event('change'))`);
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="3"]').getAttribute('aria-current')`), 'step');
    await evaluate(`document.querySelector('#hotel-search-form').requestSubmit()`);
    await until(`document.querySelector('#hotel-results').textContent.includes('가상 숙소')`);
    await click('#hotel-results .place-result button');
    await click('#next-step');
    await evaluate(`document.querySelector('#menu-direct-name').value='직접 메뉴';document.querySelector('#menu-direct-query').value='메뉴 검색';document.querySelector('#menu-direct-form').requestSubmit()`);
    await click('#menu-confirm');
    await click('#next-step');
    await evaluate(`document.querySelector('#estimate-start-date').value='2026-10-01';document.querySelector('#estimate-start-date').dispatchEvent(new Event('change'));document.querySelector('#estimate-form').requestSubmit()`);
    await until(`document.querySelectorAll('#estimate-result section').length === 2`);
    await click('#next-step');
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-results').textContent.includes('가상 음식점 1')`);
    await click('#restaurant-results .place-result button');
    await evaluate(`document.querySelector('#restaurant-selected input').value='내 점심 식당';document.querySelector('#restaurant-selected input').dispatchEvent(new Event('input'))`);
    await evaluate(`window.__reviewOverlays=[];window.kakao.maps.CustomOverlay=class{constructor(options){this.map=options.map;this.content=options.content;window.__reviewOverlays.push(this)}setMap(map){this.map=map}}`);
    const beforeDaySwitch = {estimate: estimateRequests.length, restaurant: restaurantRequests.length, create: createRequests.length};
    await click('#next-step');
    await until(`document.querySelector('#review-timeline').textContent.includes('내 점심 식당')`);
    assert.equal(await evaluate(`document.querySelectorAll('#review-days button').length`), 2);
    assert.equal(await evaluate(`document.querySelector('#review-timeline').textContent.includes('내 해변')`), true);
    assert.equal(await evaluate(`document.querySelector('#review-timeline .review-visit .review-entry-marker').textContent`), '2');
    assert.equal(await evaluate(`document.querySelector('#review-timeline .review-move .review-entry-marker').textContent`), '↓');
    assert.equal(await evaluate(`window.__reviewOverlays.some(overlay => overlay.map && overlay.content.textContent.includes('3'))`), true);
    await click('#review-days button:nth-child(2)');
    assert.equal(await evaluate(`document.querySelector('#review-timeline').textContent.includes('내 해변')`), false);
    assert.equal(await evaluate(`document.querySelector('#review-days button:nth-child(2)').getAttribute('aria-pressed')`), 'true');
    assert.equal(await evaluate(`document.querySelector('#review-day-title').textContent`), '2일차 일정');
    assert.equal(await evaluate(`document.querySelector('#step-description').textContent`), '날짜별 시간표와 방문 순서를 살펴본 뒤 여행을 완성하세요.');
    assert.equal(await evaluate(`document.querySelector('#review-adjust').closest('.review-finish') !== null`), true);
    assert.equal(await evaluate(`document.querySelector('#review-map').closest('.review-map-card') !== null`), true);
    assert.equal(await evaluate(`Array.from(document.querySelectorAll('#review-workspace .review-heading-icon')).length === 4 && Array.from(document.querySelectorAll('#review-workspace .review-heading-icon')).every(icon => icon.getAttribute('aria-hidden') === 'true')`), true);
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#review-days [aria-pressed="true"]')).animationName`), 'review-choice');
    await click('.motion-toggle');
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#review-days [aria-pressed="true"]')).animationName`), 'none');
    await click('.motion-toggle');
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]});
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#review-days [aria-pressed="true"]')).animationName`), 'none');
    await send('Emulation.setEmulatedMedia', {features: []});
    assert.equal(await evaluate(`document.querySelector('#review-timeline .review-meal .review-entry-marker').textContent`), '식');
    assert.deepEqual(await evaluate(`Array.from(document.querySelectorAll('#review-timeline > li')).map(item => ({kind: item.className, number: item.dataset.stop || null, time: item.querySelector('time')?.textContent || null}))`), [
      {kind: 'review-entry review-boundary', number: '1', time: null},
      {kind: 'review-entry review-meal', number: null, time: '12:00–13:00'},
      {kind: 'review-entry review-meal', number: null, time: '18:00–19:00'},
      {kind: 'review-entry review-boundary', number: '2', time: null}
    ]);
    assert.equal(await evaluate(`window.__reviewOverlays.filter(overlay => overlay.map).length`), 1);
    assert.deepEqual({estimate: estimateRequests.length, restaurant: restaurantRequests.length, create: createRequests.length}, beforeDaySwitch);
    await evaluate(`document.querySelector('.review-picker').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    await screenshot('w1-04-review-overview-desktop.png');
    await designScreenshot('w1-06-review-desktop.png');
    await evaluate(`document.querySelector('.review-schedule').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    await screenshot('w1-04-review-schedule-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    await evaluate(`document.querySelector('.review-picker').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    await screenshot('w1-04-review-overview-mobile.png');
    await evaluate(`document.querySelector('.review-schedule').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    assert.equal(await evaluate(`document.querySelector('.review-schedule').getBoundingClientRect().right <= innerWidth`), true);
    await screenshot('w1-04-review-schedule-mobile.png');
    await evaluate(`document.querySelector('#review-timeline li:last-child').scrollIntoView({block:'center',behavior:'instant'})`);
    assert.equal(await evaluate(`document.querySelector('#review-timeline li:last-child').getBoundingClientRect().bottom <= document.querySelector('.canvas-actions').getBoundingClientRect().top`), true);
    await evaluate(`document.querySelector('.review-finish').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    assert.equal(await evaluate(`document.querySelector('#review-submit').getBoundingClientRect().right <= innerWidth`), true);
    await screenshot('w1-04-review-finish-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    createMode = 'route';
    await evaluate(`document.querySelector('#review-form').requestSubmit()`);
    await until(`document.querySelector('#review-status').textContent.includes('경로를 찾지')`);
    assert.equal(createRequests.at(-1).body.meals[0].displayName, '내 점심 식당');
    assert.match(createRequests.at(-1).headers['idempotency-key'], /^[0-9a-f-]{36}$/);
    assert.equal(createRequests.at(-1).headers.authorization, `Bearer ${token}`);
    assert.equal(await evaluate(`document.querySelector('#review-timeline').textContent.includes('내 해변')`), false);
    await click('#review-adjust');
    assert.equal(await evaluate(`document.querySelector('[data-step="5"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#estimate-status').textContent.includes('해당 날짜')`), true);
    await evaluate(`document.querySelector('#estimate-day-fields [data-time="end"]').value='21:00';document.querySelector('#estimate-day-fields [data-time="end"]').dispatchEvent(new Event('change'))`);
    await click('[data-step="7"]');
    assert.equal(await evaluate(`document.querySelector('[data-step="5"]').getAttribute('aria-current')`), 'step');
    await evaluate(`document.querySelector('#estimate-form').requestSubmit()`);
    await until(`document.querySelectorAll('#estimate-result section').length === 2`);
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('#restaurant-selected').textContent`), '');
    await click('#next-step');
    createMode = 'delayedRoute';
    await evaluate(`document.querySelector('#review-form').requestSubmit()`);
    await until(`document.querySelector('#review-submit').disabled`);
    await evaluate(`document.querySelector('#review-title').value='새 제목'`);
    await until(`document.querySelector('#review-status').textContent.includes('이전 완료 응답')`);
    assert.equal(await evaluate(`document.body.dataset.view`), 'workspace');
    createMode = 'success';
    await evaluate(`document.querySelector('#review-form').requestSubmit()`);
    await until(`document.body.dataset.view === 'detail'`);
    assert.equal(await evaluate(`document.querySelector('#detail-title').textContent`), '새 제목');
    assert.equal(await evaluate(`document.querySelector('#detail-days').textContent.includes('내 해변')`), true);
    assert.equal(await evaluate(`document.querySelector('#detail-days').textContent.includes('예상 이동시간 30분')`), true);
    assert.deepEqual(await evaluate(`(() => {
      const link = document.querySelector('#detail-days .kakao-place-link');
      link.focus();
      return {icon: !!link.querySelector('svg[aria-hidden="true"]'), text: link.textContent,
        name: link.getAttribute('aria-label'), url: link.href, target: link.target,
        rel: link.rel, keyboard: document.activeElement === link};
    })()`), {icon: true, text: '', name: '카카오 지도에서 내 해변 장소 보기',
      url: 'https://place.map.kakao.com/attraction', target: '_blank', rel: 'noopener noreferrer', keyboard: true});
    assert.equal(await evaluate(`document.querySelector('#detail-hotel .kakao-place-link').getAttribute('aria-label')`), '카카오 지도에서 숙소 보기');
    assert.equal(await evaluate(`document.querySelector('#detail-hotel .kakao-place-link').textContent`), '');
    assert.equal(await evaluate(`document.querySelector('#detail-warning').textContent.includes('거리 기반')`), true);
    assert.equal(await evaluate(`document.querySelector('#detail-days .place-map, #detail-hotel .place-map')?.id || 'none'`), 'none');
    assert.equal(await evaluate(`document.querySelector('#place-map').closest('[data-view]').dataset.view`), 'workspace');
    assert.equal(await evaluate(`document.querySelector('#review-map').childElementCount`), 0);
    assert.deepEqual(await evaluate(`(async () => {
      const map = RoutyPlaceWorkspace.createMapView(window, document.createElement('div'), document.createElement('p'));
      await map.ensure(); map.dispose();
      return {center: map.center(), bounds: map.bounds()};
    })()`), {center: null, bounds: null});
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    assert.equal(await evaluate(`document.documentElement.outerHTML.includes('fake-attraction-1-token')`), false);
    await screenshot('w1-04-completed-desktop.png');
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
