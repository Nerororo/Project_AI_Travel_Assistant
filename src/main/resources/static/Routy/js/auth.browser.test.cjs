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
const {pathToFileURL} = require('node:url');
const {setTimeout: delay} = require('node:timers/promises');

test('authentication and region browser flows, keyboard, lifecycle and mobile layout', {timeout: 90000}, async t => {
  const root = path.resolve(__dirname, '..');
  const files = new Map([['/', 'index.html'], ['/css/style.css', 'css/style.css'], ['/js/auth.js', 'js/auth.js'], ['/js/place-selection-state.js', 'js/place-selection-state.js'], ['/js/place-workspace.js', 'js/place-workspace.js'], ['/js/menu-workspace.js', 'js/menu-workspace.js'], ['/js/estimate-workspace.js', 'js/estimate-workspace.js'], ['/js/restaurant-workspace.js', 'js/restaurant-workspace.js'], ['/js/review-workspace.js', 'js/review-workspace.js'], ['/js/preview.js', 'js/preview.js'], ['/img/mark.svg', 'img/mark.svg']]);
  files.set('/css/journey.css', 'css/journey.css');
  files.set('/js/itinerary-download.js', 'js/itinerary-download.js');
  files.set('/js/trips-workspace.js', 'js/trips-workspace.js');
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
  let savedPlan = null, tripMode = 'success', shareSequence = 0, currentShare = null;
  const tripRequests = [];
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
      const status = mode === 'limited' ? 429 : mode === 'oversized' ? 413 : mode === 'duplicate' ? 409 : mode === 'validation' ? 400 : mode === 'unauthorized' ? 401 : signup ? 201 : 200;
      if (status === 429) res.setHeader('Retry-After', '43');
      res.writeHead(status);
      if (status === 201) res.end();
      else if (status === 200) res.end(JSON.stringify({accessToken: token, tokenType: 'Bearer', expiresInSeconds: loginTtl}));
      else res.end(JSON.stringify({code: status === 429 ? 'RATE_LIMIT_EXCEEDED' : status === 413 ? 'REQUEST_TOO_LARGE' : status === 409 ? 'EMAIL_ALREADY_EXISTS' : status === 400 ? 'VALIDATION_FAILED' : 'AUTHENTICATION_REQUIRED', message: 'untrusted-response', fieldErrors: status === 400 ? [{field: 'email', reason: 'INVALID_FORMAT'}] : [], details: null, adjustments: [], retryAfterSeconds: status === 429 ? 43 : null}));
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
      if (estimateMode === 'oversized' || estimateMode === 'limited') {
        res.writeHead(estimateMode === 'oversized' ? 413 : 429);
        res.end(JSON.stringify({code: estimateMode === 'oversized' ? 'REQUEST_TOO_LARGE' : 'RATE_LIMIT_EXCEEDED', retryAfterSeconds: 43, message: 'untrusted-response'}));
      } else if (estimateMode === 'split') {
        const place = body.places[0];
        res.writeHead(200);
        res.end(JSON.stringify({routeVerified: false, days: body.days.map((day, index) => ({date: day.date,
          items: index ? [] : [
            {order: 1, type: 'MOVE', startTime: '11:00', endTime: '11:00', estimatedMinutes: 0},
            {order: 2, type: 'VISIT', clientPlaceId: place.clientPlaceId, displayName: place.displayName, startTime: '11:00', endTime: '11:45'},
            {order: 3, type: 'MEAL', startTime: '12:00', endTime: '13:00'},
            {order: 4, type: 'VISIT', clientPlaceId: place.clientPlaceId, displayName: place.displayName, startTime: '13:15', endTime: '14:30'},
            {order: 5, type: 'MOVE', startTime: '14:30', endTime: '14:30', estimatedMinutes: 0}
          ]}))}));
      } else if (estimateMode === 'capacity') {
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
    if (req.url.startsWith('/api/travel-plans?') || /^\/api\/travel-plans\/\d+(\/shares)?$/.test(req.url) || req.url.startsWith('/api/shared/travel-plans/')) {
      let raw = ''; for await (const chunk of req) raw += chunk;
      const body = raw ? JSON.parse(raw) : null;
      tripRequests.push({path: req.url, method: req.method, headers: req.headers, body});
      res.setHeader('Content-Type', 'application/json'); res.setHeader('Cache-Control', 'no-store');
      const fail = code => { res.writeHead(code); res.end(JSON.stringify({code: code === 429 ? 'RATE_LIMIT_EXCEEDED' : 'TRAVEL_PLAN_NOT_FOUND', retryAfterSeconds: 12})); };
      const mode = tripMode;
      if (mode === 'delayed') await delay(350);
      if (mode === 'forbidden') { fail(403); return; }
      if (mode === 'limited') { fail(429); return; }
      if (mode === 'unavailable') { fail(503); return; }
      if (req.url.startsWith('/api/shared/')) {
        if (!savedPlan || req.url.split('/').at(-1) !== currentShare || mode === 'expired') { fail(404); return; }
        const {warnings, ...detail} = savedPlan; res.end(JSON.stringify(detail)); return;
      }
      if (req.url.startsWith('/api/travel-plans?')) {
        const page = Number(new URL(req.url, 'http://localhost').searchParams.get('page'));
        res.end(JSON.stringify({items: savedPlan ? Array.from({length: page === 0 ? 20 : 1}, (_, i) => ({...savedPlan, travelPlanId: 42 + page * 20 + i, title: `여행 ${page * 20 + i + 1}`})) : [], page, size: 20, hasNext: !!savedPlan && page === 0})); return;
      }
      if (!savedPlan) { fail(404); return; }
      if (req.method === 'DELETE') { savedPlan = null; currentShare = null; res.writeHead(204); res.end(); return; }
      if (req.url.endsWith('/shares')) {
        currentShare = String(++shareSequence).padStart(43, 'a');
        res.writeHead(201); res.end(JSON.stringify({shareToken: currentShare, expiresAt: '2026-11-14T00:00:00Z'})); return;
      }
      if (req.method === 'PATCH') {
        if (body.title) savedPlan.title = body.title;
        for (const edit of body.placeEdits || []) for (const place of [savedPlan.hotel, ...savedPlan.days.flatMap(day => day.items)]) {
          if (place?.planPlaceId === edit.planPlaceId) Object.assign(place, edit);
        }
      }
      const {warnings, ...detail} = savedPlan; res.end(JSON.stringify(detail)); return;
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
      if (['oversized', 'limited', 'storage', 'processing', 'completed'].includes(mode)) {
        const code = {oversized: 'REQUEST_TOO_LARGE', limited: 'RATE_LIMIT_EXCEEDED', storage: 'TRAVEL_PLAN_LIMIT_EXCEEDED', processing: 'REQUEST_IN_PROGRESS', completed: 'REQUEST_ALREADY_COMPLETED'}[mode];
        res.writeHead(mode === 'oversized' ? 413 : mode === 'limited' ? 429 : 409);
        res.end(JSON.stringify({code, retryAfterSeconds: 43, message: 'untrusted-response'}));
      } else if (mode === 'route' || mode === 'delayedRoute') {
        res.writeHead(422);
        res.end(JSON.stringify({code: 'ROUTE_NOT_FOUND', message: 'untrusted-provider-message', details: {date: body.startDate, moveOrder: 2, travelMode: body.travelMode}}));
      } else {
        res.writeHead(201, {Location: '/api/travel-plans/42'});
        res.end(JSON.stringify(savedPlan = {travelPlanId: 42, title: body.title, region: {regionId: body.regionId, displayName: '부산광역시'}, travelMode: body.travelMode,
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
    if (req.method === 'POST' && req.url === '/api/places/image') {
      for await (const _ of req) { /* Discard temporary search terms. */ }
      res.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
      res.end(JSON.stringify({thumbnailUrls: []}));
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
  const networkRequests = [];
  let downloadedPath;
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Network.requestWillBeSent') networkRequests.push({url: message.params.request.url, documentURL: message.params.documentURL});
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
  async function screenshot(name, fullPage = false) {
    const directory = path.resolve(root, '../../../../../build/qa');
    await fs.mkdir(directory, {recursive: true});
    const size = fullPage ? (await send('Page.getLayoutMetrics')).cssContentSize : null;
    const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: fullPage,
      ...(size ? {clip: {...size, scale: 1}} : {})});
    await fs.writeFile(path.join(directory, name), Buffer.from(shot.data, 'base64'));
  }
  async function designScreenshot(name, fullPage = false) {
    await evaluate('document.fonts.ready.then(() => true)');
    const scroll = await evaluate('scrollY');
    await delay(150);
    await evaluate('window.scrollTo({top:0,behavior:"instant"})');
    await until('scrollY === 0');
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'design fits viewport');
    await screenshot(name, fullPage);
    await evaluate(`window.scrollTo({top:${scroll},behavior:"instant"})`);
  }
  async function searchScrollScreenshot(selector, name) {
    if (!await evaluate(`!!document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox')`)) {
      const previousScroll = await evaluate('scrollY');
      await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});window.scrollProbeNodes=[...list.children];list.replaceChildren(...Array.from({length:20},(_,i)=>window.scrollProbeNodes[i%window.scrollProbeNodes.length].cloneNode(true)))})()`);
      for (const width of [1440, 390]) {
        await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
        await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});list.scrollTop=0;list.scrollIntoView({block:'center',behavior:'instant'});list.focus({preventScroll:true})})()`);
        const layout = await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),pane=list.closest('.place-layout'),map=pane.querySelector('.place-map'),r=list.getBoundingClientRect(),m=map.getBoundingClientRect();return {position:getComputedStyle(list).position,columns:getComputedStyle(list).gridTemplateColumns.split(' ').length,scrolls:list.scrollHeight>list.clientHeight,fits:document.documentElement.scrollWidth<=innerWidth,mapRight:m.left>=r.right,mapHeight:m.height}})()`);
        assert.equal(layout.position, 'static', 'place results stay in the page body');
        assert.equal(layout.columns, width === 1440 ? 2 : 1, `${name} card columns at ${width}px`);
        assert.equal(layout.scrolls && layout.fits, true, `${name} results scroll without page overflow`);
        if (width === 1440) assert.ok(layout.mapRight && layout.mapHeight >= 512, `${name} has a large map on the right`);
        if (width === 1440) {
          await screenshot('selection-summary-' + name + '-desktop.png');
          const summary = await evaluate(`(()=>{const node=document.querySelector('.journey-summary'),r=node.getBoundingClientRect(),header=document.querySelector('.site-header').getBoundingClientRect();return {position:getComputedStyle(node).position,top:r.top,bottom:r.bottom,height:r.height,headerBottom:header.bottom,fields:['#summary-region','#summary-places'].every(selector=>{const b=document.querySelector(selector).getBoundingClientRect();return b.top>=r.top&&b.bottom<=r.bottom})}})()`);
          assert.ok(summary.position==='sticky'&&summary.top>=summary.headerBottom&&summary.bottom<1000&&summary.height<=100&&summary.fields, `${name} keeps current selection visible: ${JSON.stringify(summary)}`);
        }
        await screenshot(`search-scroll-${name}-${width === 1440 ? 'desktop' : 'mobile'}.png`);
        await key('ArrowDown', 'ArrowDown', 40);
        await until(`document.querySelector(${JSON.stringify(selector)}).scrollTop>0`);
      }
      await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});list.replaceChildren(...window.scrollProbeNodes);list.scrollTop=0;delete window.scrollProbeNodes})()`);
      await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
      await evaluate(`window.scrollTo({top:${previousScroll},behavior:'instant'})`);
      return;
    }
    const previousScroll = await evaluate('scrollY');
    assert.equal(await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),choice=list.querySelector('.search-result-choice');return !!choice&&choice.tagName==='BUTTON'&&choice.textContent.trim().length>0&&!list.querySelector('.button')})()`), true, `${name} uses selectable result rows`);
    // Expand fake result cards for layout only; restore the original nodes and listeners.
    await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});window.scrollProbeNodes=[...list.children];list.replaceChildren(...Array.from({length:10},(_,i)=>window.scrollProbeNodes[i%window.scrollProbeNodes.length].cloneNode(true)))})()`);
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
      await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),box=list.closest('.search-combobox');document.activeElement.blur();box.querySelector('[aria-controls]').focus({preventScroll:true});list.scrollTop=0;box.scrollIntoView({block:'start',behavior:'instant'});list.focus({preventScroll:true})})()`);
      await delay(150);
      assert.equal(await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),style=getComputedStyle(list);return list.clientHeight<=Math.ceil(parseFloat(style.maxHeight)+parseFloat(style.paddingTop)+parseFloat(style.paddingBottom))&&list.scrollHeight>list.clientHeight&&list.getBoundingClientRect().right<=innerWidth})()`), true, `${name} bounds at ${width}px`);
      assert.equal(await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),row=list.querySelector('li'),choice=row.querySelector('.search-result-choice');return Math.abs(choice.getBoundingClientRect().width-row.clientWidth)<2&&getComputedStyle(list).rowGap==='0px'})()`), true, `${name} has connected, full-width rows at ${width}px`);
      const overlay = await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),box=list.closest('.search-combobox'),bounds=box.getBoundingClientRect(),result=list.getBoundingClientRect(),probe=document.createElement('span');box.after(probe);const before=probe.offsetTop;box.classList.remove('is-open');const after=probe.offsetTop;box.classList.add('is-open');probe.remove();return {position:getComputedStyle(list).position,gap:result.top-bounds.bottom,width:result.width-bounds.width,movement:after-before}})()`);
      assert.ok(overlay.position === 'absolute' && Math.abs(overlay.gap) <= 1 && Math.abs(overlay.width) <= 1 && Math.abs(overlay.movement) <= 1, `${name} joins the search bar without moving content at ${width}px: ${JSON.stringify(overlay)}`);
      assert.equal(await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)}),r=list.getBoundingClientRect(),y=Math.min(r.bottom-15,innerHeight-100);return list.contains(document.elementFromPoint(r.left+20,y))})()`), true, `${name} results stay above the underlying screen at ${width}px`);
      await evaluate(`document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox').querySelector('[aria-controls]').focus({preventScroll:true})`);
      await screenshot(`search-scroll-${name}-${width === 1440 ? 'desktop' : 'mobile'}.png`);
      await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus({preventScroll:true})`);
      await key('ArrowDown', 'ArrowDown', 40);
      await until(`document.querySelector(${JSON.stringify(selector)}).scrollTop>0`);
      assert.equal(await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});list.scrollTop=list.scrollHeight;return list.scrollTop>0})()`), true);
      await key('Escape', 'Escape', 27);
      assert.equal(await evaluate(`!document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox').classList.contains('is-open')`), true, 'Escape closes the dropdown');
      await evaluate(`document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox').click()`);
      assert.equal(await evaluate(`document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox').classList.contains('is-open')`), true, 'search control reopens results');
      await click('#step-title');
      assert.equal(await evaluate(`!document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox').classList.contains('is-open')`), true, 'outside click closes the dropdown');
    }
    await evaluate(`(()=>{const list=document.querySelector(${JSON.stringify(selector)});list.replaceChildren(...window.scrollProbeNodes);list.scrollTop=0;delete window.scrollProbeNodes})()`);
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await evaluate(`window.scrollTo({top:${previousScroll},behavior:'instant'})`);
    await delay(150);
  }
  async function mobileDesignScreenshot(name, fullPage = false) {
    const size = await evaluate('({width: innerWidth, height: innerHeight})');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await designScreenshot(name, fullPage);
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
  async function chooseWithKeyboard(selector) {
    const dropdown = await evaluate(`!!document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox')`);
    await evaluate(`(()=>{const choice=document.querySelector(${JSON.stringify(selector)});choice.closest('.search-combobox')?.click();choice.focus()})()`);
    await key('Enter', 'Enter', 13);
    if (dropdown) await until(`(()=>{const box=document.querySelector(${JSON.stringify(selector)}).closest('.search-combobox');return !box.classList.contains('is-open')&&document.activeElement===box.querySelector('[aria-controls]')})()`);
    else assert.equal(await evaluate(`document.activeElement === document.querySelector(${JSON.stringify(selector)})`), true, 'card selection keeps keyboard focus');
  }
  await send('Runtime.enable');
  await send('Page.enable');
  await send('Network.enable');
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
  await t.test('W1-06 login rejection shows wait or input recovery without automatic retry', async () => {
    for (const mode of ['limited', 'oversized']) {
      responseMode = mode;
      await fill();
      const before = loginCalls;
      await click('#auth-submit');
      await until(`document.querySelector('#auth-form').getAttribute('aria-busy') === 'false'`);
      assert.equal(loginCalls, before + 1);
      assert.equal(await evaluate(`document.querySelector('#auth-status').textContent.includes(${JSON.stringify(mode === 'limited' ? '43초' : '입력 길이')})`), true);
      assert.equal(await evaluate(`document.querySelector('#auth-password').value`), '');
      assert.equal(await evaluate(`document.querySelector('#auth-email').value === ${JSON.stringify(account.email)}`), true);
      assert.equal(await evaluate(`document.body.dataset.view`), 'auth');
      await designScreenshot(`w1-06-auth-${mode}-desktop.png`, true);
      await mobileDesignScreenshot(`w1-06-auth-${mode}-mobile.png`, true);
      await delay(100);
      assert.equal(loginCalls, before + 1);
    }
    responseMode = 'success';
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
  await t.test('workspace proportions keep every step label on one line across viewport sizes', async () => {
    const activeStep = await evaluate(`document.querySelector('.workspace-shell').dataset.activeStep`);
    for (const width of [320, 390, 768, 930, 1024, 1120, 1280, 1440, 1920]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width <= 390});
      for (const step of ['0', '2', '5', '6', '7']) {
        await evaluate(`document.querySelector('.workspace-shell').dataset.activeStep='${step}'`);
        const layout = await evaluate(`(()=>{const rail=document.querySelector('.step-rail'),canvas=document.querySelector('.work-canvas');return {fits:document.documentElement.scrollWidth<=innerWidth,canvas:canvas.clientWidth,labels:[...rail.querySelectorAll('button')].every(button=>{const node=[...button.childNodes].find(node=>node.nodeType===Node.TEXT_NODE&&node.textContent.trim());const range=document.createRange();range.selectNodeContents(node);const rects=[...range.getClientRects()];return rects.length===1&&rects[0].right<=button.getBoundingClientRect().right})}})()`);
        assert.ok(layout.fits && layout.labels, `step ${step} at ${width}px: ${JSON.stringify(layout)}`);
        if (width >= 930) assert.ok(layout.canvas >= 600, `working area stays readable at ${width}px`);
      }
      await evaluate(`document.querySelector('.workspace-shell').dataset.activeStep=${JSON.stringify(activeStep)}`);
      if ([390, 1024, 1280, 1440].includes(width)) await screenshot(`responsive-workspace-${width}.png`);
    }
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
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
    await searchScrollScreenshot('#region-search-results', 'regions');
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
    await searchScrollScreenshot('#region-ai-results', 'recommendations');
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
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
      const sizes = await evaluate(`(() => {
        const list = document.querySelector('#place-results'), plain = list.querySelector('.place-result');
        const pictured = plain.cloneNode(true), gallery = document.createElement('div'); pictured.id = 'image-layout-probe';
        const strip = document.createElement('div'), image = document.createElement('img');
        gallery.className = 'place-image-gallery'; strip.className = 'place-image-strip'; image.className = 'place-thumbnail';
        image.src = '/img/workspace-coast.png'; image.alt = ''; strip.tabIndex = 0;
        const second = image.cloneNode(); second.src = '/img/auth-heritage.png';
        strip.append(image, second); gallery.append(strip); pictured.append(gallery); pictured.classList.add('has-thumbnail'); list.prepend(pictured);
        const other = pictured.cloneNode(true); other.id = 'image-layout-probe-two';
        other.querySelector('strong').textContent = '가상 한옥'; other.querySelector('img').src = '/img/auth-heritage.png'; list.insertBefore(other, pictured.nextSibling);
        const result = {plainButton: plain.querySelector('button').getBoundingClientRect().width, plainWidth: plain.clientWidth,
          picturedButton: pictured.querySelector('button').getBoundingClientRect().width, picturedWidth: pictured.clientWidth,
          imageWidth: image.getBoundingClientRect().width, imageHeight: image.getBoundingClientRect().height,
          imageBottom: image.getBoundingClientRect().bottom, textTop: pictured.querySelector('button').getBoundingClientRect().top};
        return result;
      })()`);
      assert.ok(Math.abs(sizes.plainButton - sizes.plainWidth) < 1, 'plain result fills the row');
      assert.ok(Math.abs(sizes.picturedButton - sizes.picturedWidth) < 1, 'place text fills the card');
      assert.ok(Math.abs(sizes.imageWidth - sizes.picturedWidth) < 1 && sizes.imageWidth >= 240, 'photo fills the card width');
      assert.equal(sizes.imageHeight, width === 1440 ? 180 : 215, 'photo matches the reference height');
      assert.ok(sizes.imageBottom <= sizes.textTop + 1, 'photo sits above the place information');
      await evaluate(`Promise.all([...document.querySelectorAll('#image-layout-probe img, #image-layout-probe-two img')].map(image=>image.decode()))`);
      await evaluate(`document.querySelector('#attractions-workspace').scrollIntoView({block:'start',behavior:'instant'})`);
      await screenshot(`place-map-layout-${width === 1440 ? 'desktop' : 'mobile'}.png`);
      await evaluate(`document.querySelector('#image-layout-probe .place-image-strip').focus({preventScroll:true})`);
      await key('ArrowRight', 'ArrowRight', 39);
      await until(`document.querySelector('#image-layout-probe .place-image-strip').scrollLeft>0`);
      await evaluate(`document.querySelector('#image-layout-probe').remove();document.querySelector('#image-layout-probe-two').remove()`);
    }
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await designScreenshot('w1-06-attractions-desktop.png');
    await searchScrollScreenshot('#place-results', 'attractions');
    const attractionRequest = placeRequests.at(-1);
    assert.equal(attractionRequest.path, '/api/places/search');
    assert.equal(attractionRequest.body.placeRole, 'ATTRACTION');
    assert.equal(attractionRequest.body.radiusMeters, 20000);
    assert.match(attractionRequest.headers['idempotency-key'], /^[0-9a-f-]{36}$/);
    await chooseWithKeyboard('#place-results .place-result button');
    assert.equal(await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value`), '');
    assert.equal(await evaluate(`document.querySelector('#attraction-selected input[type="text"]').placeholder.includes('가상 해변')`), false);
    await evaluate(`document.querySelector('#attraction-selected input[type="text"]').value='내 바닷가';document.querySelector('#attraction-selected input[type="text"]').dispatchEvent(new Event('input'))`);
    await click('[data-place-role="TRAVEL_BOUNDARY"]');
    assert.equal(await evaluate(`document.querySelector('#boundary-search-form .boundary-slot-picker') !== null`), true);
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
      await evaluate(`document.querySelector('#boundary-search-form').scrollIntoView({block:'center',behavior:'instant'})`);
      await delay(150);
      assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
      await screenshot(`w1-02-boundary-input-${width === 1440 ? 'desktop' : 'mobile'}.png`);
    }
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await evaluate(`document.querySelector('#boundary-query').value='역';document.querySelector('#boundary-search-form').requestSubmit()`);
    await until(`document.querySelectorAll('#place-results .place-result').length === 1`);
    assert.equal(placeRequests.at(-1).path, '/api/places/travel-boundaries/search');
    await searchScrollScreenshot('#place-results', 'boundaries');
    await chooseWithKeyboard('#place-results .place-result button');
    await evaluate(`document.querySelector('input[name="boundary-slot"][value="START"]').focus()`);
    await key('ArrowRight', 'ArrowRight', 39);
    assert.equal(await evaluate(`document.activeElement.name === 'boundary-slot' && document.activeElement.value === 'END'`), true, 'role switch keeps radio focus');
    assert.equal(await evaluate(`document.querySelector('#place-results button').getAttribute('aria-pressed')`), 'false', 'a start-only selection is not selected as the end');
    assert.equal(await evaluate(`document.querySelector('#boundary-selected [data-boundary-slot="END"] > span').textContent.includes('아직 선택')`), true);
    await chooseWithKeyboard('#place-results .place-result button');
    assert.equal(await evaluate(`['START','END'].every(slot=>document.querySelector('#boundary-selected [data-boundary-slot="'+slot+'"] > span').textContent === '가상 역')`), true);
    await click('#boundary-selected [data-boundary-slot="START"] button');
    assert.equal(await evaluate(`document.querySelector('#boundary-selected [data-boundary-slot="START"] > span').textContent.includes('아직 선택') && document.querySelector('#boundary-selected [data-boundary-slot="END"] > span').textContent === '가상 역'`), true);
    await click('input[name="boundary-slot"][value="START"]');
    await click('#place-results .place-result button');
    await click('input[name="boundary-slot"][value="END"]');
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
      await evaluate(`document.querySelector('#boundary-selected').scrollIntoView({block:'center',behavior:'instant'})`);
      await delay(150);
      assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
      await screenshot(`w1-02-boundary-selected-${width === 1440 ? 'desktop' : 'mobile'}.png`);
    }
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
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
    await searchScrollScreenshot('#hotel-results', 'hotels');
    assert.deepEqual(placeRequests.at(-1).body.attractionSelectionTokens, ['fake-attraction-1-token']);
    await chooseWithKeyboard('#hotel-results .place-result button');
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
    assert.equal(await evaluate(`document.querySelector('#boundary-selected [data-boundary-slot="START"] > span').textContent`), '가상 역');
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
      for (const mode of ['oversized', 'limited']) {
        estimateMode = mode;
        await click('#estimate-submit');
        await until(`document.querySelector('#estimate-status').textContent.includes(${JSON.stringify(mode === 'oversized' ? '요청 크기' : '43초')})`);
        assert.equal(await evaluate(`document.querySelector('#estimate-start-date').value`), '2026-10-01');
        assert.equal(await evaluate(`document.querySelector('#estimate-result').textContent`), '');
        await designScreenshot(`w1-06-estimate-${mode}-desktop.png`, true);
        await mobileDesignScreenshot(`w1-06-estimate-${mode}-mobile.png`, true);
      }
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
    await searchScrollScreenshot('#restaurant-results', 'restaurants');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await evaluate(`document.querySelector('#restaurant-results').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('#restaurant-results')).position === 'static'`), true);
    await screenshot('w1-03a-candidates-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await chooseWithKeyboard('#restaurant-results .place-result button');
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
      InfoWindow:class{constructor(options){this.content=options.content}open(map,marker){this.map=map;this.marker=marker}close(){this.map=null}},
      event:{addListener(marker,event,handler){marker[event]=handler;if(event==='click')marker.choose=handler}}
    }}`);
    await click('#restaurant-workspace [data-restaurant-view="map"]');
    await until(`window.__restaurantMarkers.some(marker => marker.map && marker.title.includes('가상 음식점'))`);
    assert.equal(await evaluate(`window.__restaurantMarkers.filter(marker => marker.map && marker.title.includes('직전 장소')).length`), 1);
    assert.equal(await evaluate(`window.__restaurantMarkers.filter(marker => marker.map).every(marker => typeof marker.mouseover === 'function' && typeof marker.mouseout === 'function')`), true);
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
    assert.equal(await evaluate(`(()=>{const empty=document.querySelector('#restaurant-results .workspace-empty'),list=empty.parentElement;return empty.getBoundingClientRect().width>=list.clientWidth-10})()`), true, 'empty result guidance spans the card area');
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
    assert.equal(await evaluate(`document.querySelector('#district-controls').hidden`), true);
    await click('[data-step="2"]');
    await designScreenshot('w1-02-district-hidden-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await evaluate(`document.querySelector('#attraction-search-form').scrollIntoView({block:'center',behavior:'instant'})`);
    await delay(150);
    await screenshot('w1-02-district-hidden-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await click('[data-step="0"]');
    await evaluate(`document.querySelector('#region-query').value='부산';document.querySelector('#region-search-form').requestSubmit()`);
    await until(`document.querySelector('#region-search-results [data-region-id="opaque-metro"]') !== null`);
    await click('#region-search-results [data-region-id="opaque-metro"]');
    assert.equal(await evaluate(`document.querySelector('#summary-places').textContent`), '선택 전');
    assert.equal(await evaluate(`document.querySelectorAll('#menu-draft .menu-card').length`), 0);
    await click('#next-step'); await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('#district-controls').hidden`), false);
    await evaluate(`document.querySelector('#district-query').value='해운대';document.querySelector('#district-form').requestSubmit()`);
    await until(`document.querySelector('#district-results button') !== null`);
    await searchScrollScreenshot('#district-results', 'districts');
    assert.equal(await evaluate(`parseFloat(getComputedStyle(document.querySelector('#district-results')).borderBottomWidth) > 0`), true);
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: 1000, deviceScaleFactor: 1, mobile: width === 390});
      await evaluate(`document.querySelector('#district-results').scrollIntoView({block:'center',behavior:'instant'})`);
      await delay(150);
      assert.equal(await evaluate(`(()=>{const r=document.querySelector('#district-results li').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth})()`), true);
      await screenshot(`w1-02-district-${width === 1440 ? 'desktop' : 'mobile'}.png`);
    }
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
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
    await evaluate(`document.querySelector('#attraction-selected input[type="number"]').value='120';document.querySelector('#attraction-selected input[type="number"]').dispatchEvent(new Event('change'))`);
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
    estimateMode = 'split';
    await evaluate(`document.querySelector('#estimate-start-date').value='2026-10-01';document.querySelector('#estimate-start-date').dispatchEvent(new Event('change'));document.querySelector('#estimate-form').requestSubmit()`);
    await until(`document.querySelectorAll('#estimate-result .estimate-visit').length === 2`);
    assert.equal(await evaluate(`document.querySelectorAll('#estimate-result .estimate-move').length`), 0);
    assert.equal(estimateRequests.at(-1).places[0].stayMinutes, 120);
    await designScreenshot('meal-split-estimate-desktop.png', true);
    await mobileDesignScreenshot('meal-split-estimate-mobile.png', true);
    await click('#next-step');
    assert.equal(await evaluate(`document.querySelector('[data-step="6"]').getAttribute('aria-current')`), 'step');
    await click('#next-step');
    await until(`document.querySelectorAll('#review-timeline .review-visit').length === 2`);
    assert.equal(await evaluate(`document.querySelectorAll('#review-timeline .review-move').length`), 0);
    await designScreenshot('meal-split-review-desktop.png', true);
    await mobileDesignScreenshot('meal-split-review-mobile.png', true);
    await click('#review-adjust');
    estimateMode = 'success';
    await evaluate(`document.querySelector('#estimate-start-date').value='2026-10-01';document.querySelector('#estimate-start-date').dispatchEvent(new Event('change'));document.querySelector('#estimate-form').requestSubmit()`);
    await until(`document.querySelectorAll('#estimate-result section').length === 2`);
    await click('#next-step');
    await evaluate(`document.querySelector('#restaurant-search-form').requestSubmit()`);
    await until(`document.querySelector('#restaurant-results').textContent.includes('가상 음식점 1')`);
    await click('#restaurant-results .place-result button');
    await evaluate(`document.querySelector('#restaurant-selected input').value='내 점심 식당';document.querySelector('#restaurant-selected input').dispatchEvent(new Event('input'))`);
    await evaluate(`window.__reviewMarkerStart=window.__restaurantMarkers.length;window.__reviewOverlays=[];window.kakao.maps.CustomOverlay=class{constructor(options){this.map=options.map;this.content=options.content;window.__reviewOverlays.push(this)}setMap(map){this.map=map}}`);
    const beforeDaySwitch = {estimate: estimateRequests.length, restaurant: restaurantRequests.length, create: createRequests.length};
    await click('#next-step');
    await until(`document.querySelector('#review-timeline').textContent.includes('내 점심 식당')`);
    assert.equal(await evaluate(`document.querySelectorAll('#review-days button').length`), 2);
    assert.equal(await evaluate(`document.querySelector('#review-timeline').textContent.includes('내 해변')`), true);
    assert.equal(await evaluate(`document.querySelector('#review-timeline .review-visit .review-entry-marker').textContent`), '2');
    assert.equal(await evaluate(`document.querySelector('#review-timeline .review-move .review-entry-marker').textContent`), '↓');
    assert.equal(await evaluate(`window.__reviewOverlays.some(overlay => overlay.map && overlay.content.textContent.includes('3'))`), true);
    assert.equal(await evaluate(`window.__reviewOverlays.filter(overlay => overlay.map).every(overlay => overlay.content.tagName === 'BUTTON')`), true);
    const firstDayMarkers = await evaluate(`window.__restaurantMarkers.slice(window.__reviewMarkerStart).filter(marker => marker.map && marker.title.includes('내 해변')).length`);
    assert.ok(firstDayMarkers > 0);
    await click('#review-days button:nth-child(2)');
    assert.equal(await evaluate(`window.__restaurantMarkers.slice(window.__reviewMarkerStart).filter(marker => marker.map && marker.title.includes('내 해변')).length`), 0);
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
    assert.equal(await evaluate(`document.querySelector('#review-title').value`), '', 'title requires user input');
    const beforeEmptyTitle = createRequests.length;
    await evaluate(`document.querySelector('#review-form').requestSubmit()`);
    assert.equal(createRequests.length, beforeEmptyTitle, 'empty title does not create a plan');
    await evaluate(`document.querySelector('#review-title').value='직접 지은 여행 제목'`);
    for (const [mode, message] of [['oversized', '요청 크기'], ['limited', '43초'], ['storage', '내 여행'], ['processing', '저장 여부'], ['completed', '저장 여부']]) {
      createMode = mode;
      await evaluate(`document.querySelector('#review-form').requestSubmit()`);
      await until(`document.querySelector('#review-status').textContent.includes(${JSON.stringify(message)})`);
      assert.equal(await evaluate(`document.querySelector('#review-title').value`), '직접 지은 여행 제목');
      assert.equal(await evaluate(`document.body.dataset.view`), 'workspace');
      assert.equal(savedPlan, null);
      assert.equal(await evaluate(`document.body.textContent.includes('untrusted-response')`), false);
    }
    await designScreenshot('w1-06-create-rejected-desktop.png', true);
    await mobileDesignScreenshot('w1-06-create-rejected-mobile.png', true);
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
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('h1').textContent`), '새 제목');
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('.motion-toggle')).display`), 'none');
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.textContent.includes('내 해변')`), true);
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.textContent.includes('예상 이동시간 30분')`), true);
    assert.deepEqual(await evaluate(`(() => {
      const root = document.querySelector('#detail-document').shadowRoot;
      const link = root.querySelector('.days .place-link');
      link.focus();
      return {text: link.textContent, icon: !!link.querySelector('svg[aria-hidden="true"]'),
        name: link.getAttribute('aria-label'), url: link.href, target: link.target,
        rel: link.rel, keyboard: root.activeElement === link};
    })()`), {text: '', icon: true, name: '카카오 지도에서 내 해변 장소 보기',
      url: 'https://place.map.kakao.com/attraction', target: '_blank', rel: 'noopener noreferrer', keyboard: true});
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('.hotel .place-link').getAttribute('aria-label')`), '카카오 지도에서 숙소 장소 보기');
    assert.equal(await evaluate(`document.querySelector('#detail-warning').textContent.includes('거리 기반')`), true);
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('.place-map')?.id || 'none'`), 'none');
    const visibleScreenDays = () => evaluate(`[...document.querySelector('#detail-document').shadowRoot.querySelectorAll('.day')].filter(day => getComputedStyle(day).display !== 'none').map(day => day.id)`);
    assert.deepEqual(await visibleScreenDays(), ['day-1']);
    await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('#day-choice-2').click()`);
    assert.deepEqual(await visibleScreenDays(), ['day-2'], 'screen shows only the selected day');
    assert.equal(await evaluate(`location.hash`), '#/trip?id=42', 'day selection preserves the app route');
    await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('#day-choice-2').focus()`);
    await key('ArrowLeft', 'ArrowLeft', 37);
    assert.deepEqual(await visibleScreenDays(), ['day-1'], 'native keyboard day selection works');
    assert.equal(await evaluate(`document.querySelector('#place-map').closest('[data-view]').dataset.view`), 'workspace');
    assert.equal(await evaluate(`document.querySelector('#review-map').childElementCount`), 0);
    assert.deepEqual(await evaluate(`(async () => {
      const map = RoutyPlaceWorkspace.createMapView(window, document.createElement('div'), document.createElement('p'));
      await map.ensure(); map.dispose();
      return {center: map.center(), bounds: map.bounds()};
    })()`), {center: null, bounds: null});
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    assert.equal(await evaluate(`document.documentElement.outerHTML.includes('fake-attraction-1-token')`), false);
    await evaluate(`window.scrollTo({top: 0, behavior: 'instant'})`);
    await screenshot('w1-04-completed-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, 'completed screen fits mobile viewport');
    await evaluate(`window.scrollTo({top: 0, behavior: 'instant'})`);
    await screenshot('w1-04a-completed-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    const directory = path.resolve(root, '../../../../../build/qa');
    const downloads = await fs.mkdtemp(path.join(directory, 'w1-04a-download-'));
    await send('Browser.setDownloadBehavior', {behavior: 'allow', downloadPath: downloads});
    await evaluate(`document.querySelector('#download-itinerary').focus()`);
    const requestsBefore = networkRequests.length;
    await key('Enter', 'Enter', 13);
    let saved;
    for (let attempt = 0; attempt < 100; attempt++) {
      saved = (await fs.readdir(downloads)).find(name => name.endsWith('.html'));
      if (saved) break;
      await delay(30);
    }
    assert.equal(saved, 'Routy_새 제목.html');
    const html = await fs.readFile(path.join(downloads, saved), 'utf8');
    assert.equal(await evaluate(`new DOMParser().parseFromString(${JSON.stringify(html)}, 'text/html').querySelector('.itinerary-page').outerHTML === document.querySelector('#detail-document').shadowRoot.querySelector('.itinerary-page').outerHTML`), true, 'screen and download share the same template markup');
    assert.match(html, /<meta charset="UTF-8">/);
    assert.match(html, /내 해변/);
    assert.match(html, /예상 이동시간 30분/);
    assert.doesNotMatch(html, /ESTIMATED_TRAVEL_TIMES_USED|fake-attraction-1-token|travelPlanId|planPlaceId/);
    assert.equal(networkRequests.length, requestsBefore, 'download triggers no network request');
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    downloadedPath = path.join(directory, 'w1-04a-downloaded.html');
    await fs.writeFile(downloadedPath, html);
  });
  await t.test('W1-05 pages, owner text edits, share rotation, anonymous reload and deletion', async () => {
    const providerCallsBefore = {estimate: estimateRequests.length, restaurant: restaurantRequests.length, place: placeRequests.length};
    await navigate('/trips');
    await until(`document.querySelectorAll('#trips-list li').length === 20`);
    assert.equal(await evaluate(`document.querySelectorAll('#trips-list .trip-card').length`), 20);
    assert.equal(await evaluate(`parseFloat(getComputedStyle(document.querySelector('#trips-list li')).borderTopWidth) > 0`), true);
    await screenshot('w1-06-trips-cards-desktop.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await evaluate(`window.scrollTo({top:0,behavior:'instant'})`);
    await delay(150);
    await screenshot('w1-06-trips-overview-mobile.png');
    await evaluate(`document.querySelector('#trips-list').scrollIntoView({block:'start',behavior:'instant'})`);
    await delay(150);
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    await screenshot('w1-06-trips-cards-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    assert.equal(await evaluate(`document.querySelector('#trips-previous').disabled`), true);
    await click('#trips-next'); await until(`document.querySelectorAll('#trips-list li').length === 1`);
    assert.equal(await evaluate(`document.querySelector('#trips-list h2').textContent`), '여행 21');
    await evaluate(`window.scrollTo({top:200,behavior:'instant'})`);
    const listRequestsBeforeReturn = tripRequests.length, listScrollBeforeReturn = await evaluate('scrollY');
    await evaluate(`document.dispatchEvent(new Event('visibilitychange'))`); await delay(150);
    assert.equal(await evaluate(`document.querySelector('#trips-page').textContent`), '2페이지');
    assert.equal(await evaluate('scrollY'), listScrollBeforeReturn);
    assert.equal(tripRequests.length, listRequestsBeforeReturn, 'tab return does not reload the list');
    assert.equal(await evaluate(`document.querySelector('#trips-next').disabled`), true);
    await click('#trips-previous'); await until(`document.querySelectorAll('#trips-list li').length === 20`);
    assert.equal(tripRequests.at(-1).headers.authorization, `Bearer ${token}`);
    await click('#trips-list a'); await until(`document.querySelector('#trip-actions').hidden === false`);
    await evaluate(`document.querySelector('#trip-edit').focus()`); await key('Enter', 'Enter', 13);
    assert.equal(await evaluate(`document.activeElement === document.querySelector('#trip-editor > label input')`), true);
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, 'owner editor fits mobile');
    await screenshot('w1-05-editor-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    const hostile = '<img src=x onerror=alert(1)>';
    await evaluate(`document.querySelector('#trip-editor > label input').value=${JSON.stringify(hostile)}; document.querySelector('#trip-editor fieldset input').value='내 쉼터'; document.querySelector('#trip-editor fieldset textarea').value='준비 메모'`);
    const editRequestsBeforeReturn = tripRequests.length;
    await evaluate(`document.dispatchEvent(new Event('visibilitychange'))`); await delay(150);
    assert.equal(await evaluate(`document.querySelector('#trip-editor').hidden`), false);
    assert.deepEqual(await evaluate(`({title:document.querySelector('#trip-editor > label input').value,name:document.querySelector('#trip-editor fieldset input').value,memo:document.querySelector('#trip-editor fieldset textarea').value})`), {title: hostile, name: '내 쉼터', memo: '준비 메모'});
    assert.equal(tripRequests.length, editRequestsBeforeReturn, 'tab return does not reload or discard edits');
    tripMode = 'unavailable';
    await evaluate(`document.querySelector('#trip-editor').requestSubmit()`);
    await until(`document.querySelector('#trip-status').textContent.includes('처리하지 못')`);
    assert.equal(await evaluate(`document.querySelector('#trip-editor > label input').value`), hostile, 'failed edit retains input');
    tripMode = 'success'; await evaluate(`document.querySelector('#trip-editor').requestSubmit()`);
    await until(`document.querySelector('#trip-editor').hidden`);
    const payload = tripRequests.filter(request => request.method === 'PATCH').at(-1).body;
    assert.deepEqual(payload, {title: hostile, placeEdits: [{planPlaceId: 8, displayName: '내 쉼터', memo: '준비 메모'}]});
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelector('h1').textContent`), hostile);
    assert.equal(await evaluate(`document.querySelector('#detail-document').shadowRoot.querySelectorAll('img,script').length`), 0);
    await click('#trip-share'); await until(`!!document.querySelector('#share-result a')`);
    const oldLink = await evaluate(`document.querySelector('#share-result a').hash`);
    tripMode = 'limited'; await click('#trip-share'); await until(`document.querySelector('#trip-status').textContent.includes('12초')`);
    assert.equal(await evaluate(`document.querySelector('#share-result a').hash`), oldLink, 'rejected rotation preserves link');
    tripMode = 'success'; await click('#trip-share'); await until(`document.querySelector('#share-result a').hash !== ${JSON.stringify(oldLink)}`);
    const sharedHash = await evaluate(`document.querySelector('#share-result a').hash`);
    await navigate('/trip?id=99'); tripMode = 'forbidden'; await click('#trip-reload');
    await until(`document.querySelector('#trip-status').textContent.includes('권한')`);
    assert.equal(await evaluate(`document.querySelector('#trip-actions').hidden`), true);
    tripMode = 'success'; await navigate('/trip?id=42'); await until(`document.querySelector('#trip-actions').hidden === false`);
    tripMode = 'delayed'; await navigate('/trips'); await navigate('/shared?token=bad'); await delay(400);
    assert.equal(await evaluate(`document.querySelectorAll('#trips-list li').length`), 0, 'stale list cannot render after navigation');
    tripMode = 'success'; await click('#logout-button');
    await navigate(sharedHash.slice(1)); await until(`!!document.querySelector('#shared-document').shadowRoot?.querySelector('h1')`);
    assert.equal(tripRequests.at(-1).headers.authorization, undefined);
    assert.equal(tripRequests.at(-1).headers.referer, undefined);
    assert.equal(await evaluate(`document.querySelector('[data-view="detail"]').hidden`), true);
    assert.equal(await evaluate(`document.querySelector('#shared-document').shadowRoot.querySelectorAll('button,form,.place-map').length`), 0);
    await send('Page.reload'); await until(`!!document.querySelector('#shared-document')?.shadowRoot?.querySelector('h1')`);
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true);
    await screenshot('w1-05-shared-mobile.png');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await navigate(oldLink.slice(1)); await until(`document.querySelector('#shared-status').textContent.includes('만료')`);
    assert.equal(await evaluate(`document.querySelector('#shared-document').shadowRoot?.childElementCount || 0`), 0);
    tripMode = 'expired'; await navigate(sharedHash.slice(1)); await until(`document.querySelector('#shared-status').textContent.includes('만료')`);
    tripMode = 'limited'; await click('#shared-reload'); await until(`document.querySelector('#shared-status').textContent.includes('12초')`);
    tripMode = 'success';
    await navigate('/trip?id=42'); await until(`document.body.dataset.view === 'auth'`);
    await fill(); await click('#auth-submit'); await until(`document.querySelector('#trip-actions').hidden === false`);
    assert.equal(await evaluate(`location.hash`), '#/trip?id=42', 'login preserves direct owner route');
    await click('#trip-delete'); await click('#trip-delete-cancel'); assert.ok(savedPlan);
    await click('#trip-delete'); await click('#trip-delete-yes');
    await until(`document.body.dataset.view === 'trips' && !document.querySelector('#trips-empty').hidden`);
    await navigate(sharedHash.slice(1)); await until(`document.querySelector('#shared-status').textContent.includes('만료')`);
    assert.equal(await evaluate(`localStorage.length + sessionStorage.length`), 0);
    assert.deepEqual({estimate: estimateRequests.length, restaurant: restaurantRequests.length, place: placeRequests.length}, providerCallsBefore, 'saved views do not recalculate or search providers');
  });
  await t.test('logout discards current step and prevents back navigation from opening protected content', async () => {
    await navigate('/workspace');
    await click('[data-step="4"]');
    await click('#logout-button');
    await until(`document.body.dataset.view === 'landing'`);
    await navigate('/workspace');
    assert.equal(await evaluate('document.body.dataset.view'), 'auth');
    assert.equal(await evaluate(`document.querySelector('[data-step="0"]').getAttribute('aria-current')`), 'step');
    assert.equal(await evaluate(`document.querySelector('#download-itinerary').disabled`), true);
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
    await send('Emulation.setEmulatedMedia', {features: [{name: 'forced-colors', value: 'active'}, {name: 'prefers-reduced-motion', value: 'reduce'}]});
    await evaluate(`document.querySelector('#auth-email').focus()`);
    await key('Tab', 'Tab', 9);
    assert.equal(await evaluate('document.activeElement.id'), 'auth-password');
    assert.equal(await evaluate(`getComputedStyle(document.activeElement).outlineStyle !== 'none'`), true);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    await screenshot('w1-06-forced-colors-mobile.png', true);
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]});
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
  await t.test('fixed HTML template escapes text, excludes private fields and opens offline at desktop and mobile sizes', async () => {
    const fixture = {
      title: '바다와 골목 사이, 우리의 이틀', startDate: '2026-10-01', endDate: '2026-10-02', travelMode: 'CAR',
      userId: 'PRIVATE-MARKER', selectionToken: 'PRIVATE-MARKER', latitude: 'PRIVATE-MARKER',
      warnings: ['PRIVATE-MARKER'], providerDisplayName: 'PRIVATE-MARKER', address: 'PRIVATE-MARKER',
      hotel: {displayName: '하루를 쉬어갈 숙소', memo: '체크인 후 잠깐 쉬어가기\n다음 날 짐 챙기기', placeUrl: 'https://place.map.kakao.com/123', phone: 'PRIVATE-MARKER'},
      days: [1, 2].map(day => ({day, date: `2026-10-0${day}`, activityStartTime: '10:00', activityEndTime: '19:00', items: [
        {type: 'VISIT', startTime: '10:00', endTime: '11:30', displayName: day === 1 ? '아침 바다를 걷는 시간' : '천천히 둘러보는 골목', stayMinutes: 90,
          memo: '서두르지 않고 풍경을 즐기기.', placeUrl: 'https://place.map.kakao.com/456', coordinate: 'PRIVATE-MARKER'},
        {type: 'MOVE', startTime: '11:30', endTime: '12:00', estimatedMinutes: 30, memo: 'PRIVATE-MARKER'},
        {type: 'MEAL', startTime: '12:00', endTime: '13:00', displayName: day === 1 ? '함께 고른 점심 식당' : '점심 식사',
          memo: day === 1 ? '함께 고른 한 끼' : null, placeUrl: day === 1 ? 'https://place.map.kakao.com/789' : null},
        {type: 'VISIT', startTime: '13:00', endTime: '14:00', displayName: '오후의 작은 쉼표', stayMinutes: 60, memo: null, placeUrl: 'https://place.map.kakao.com/987'}
      ]}))
    };
    const attack = '</title><script>window.__downloadXss=1</script><img src=x onerror="window.__downloadXss=1"> & 한글';
    const result = await evaluate(`(() => {
      const plan = ${JSON.stringify(fixture)};
      const page = RoutyItineraryDownload.buildDocument(document, plan);
      const html = '<!doctype html>\\n' + page.documentElement.outerHTML;
      const hostile = RoutyItineraryDownload.buildDocument(document, {...plan, title: ${JSON.stringify(attack)},
        hotel: {...plan.hotel, memo: ${JSON.stringify(attack)}},
        days: plan.days.map(day => ({...day, items: day.items.map((item, index) => index === 3 ? {...item, placeUrl: 'javascript:alert(1)'} : item)}))});
      const seven = RoutyItineraryDownload.buildDocument(document, {...plan, hotel: null, travelMode: 'PUBLIC_TRANSIT',
        days: Array.from({length:7}, (_,i) => ({...plan.days[0], day:i+1, date:'2026-10-0'+(i+1)}))});
      const one = RoutyItineraryDownload.buildDocument(document, {...plan, hotel: null, endDate: plan.startDate, days: [plan.days[0]]});
      return {html, hostile: '<!doctype html>\\n' + hostile.documentElement.outerHTML,
        days: page.querySelectorAll('.day').length, meals: page.querySelectorAll('.meal').length,
        links: [...page.querySelectorAll('.place-link')].map(a => ({url:a.href,rel:a.rel})),
        hostileText: hostile.querySelector('h1').textContent,
        unsafe: hostile.querySelectorAll('script,img,iframe,[onerror],link').length,
        hostileLinks: hostile.querySelectorAll('.place-link').length,
        seven: seven.querySelectorAll('.day').length, noHotel: !seven.querySelector('.hotel'),
        transit: seven.querySelector('.facts').textContent.includes('대중교통'),
        one: one.querySelectorAll('.day').length, oneHotel: !!one.querySelector('.hotel')};
    })()`);
    assert.equal(result.days, 2); assert.equal(result.meals, 2);
    assert.equal(result.hostileText, attack); assert.equal(result.unsafe, 0);
    assert.equal(result.seven, 7); assert.equal(result.noHotel, true); assert.equal(result.transit, true);
    assert.equal(result.one, 1); assert.equal(result.oneHotel, false);
    assert.equal(result.links.length, 6);
    assert.equal(result.hostileLinks, 4, 'unsafe URLs are omitted from the separate hostile fixture');
    assert.ok(result.links.every(link => link.url.startsWith('https://place.map.kakao.com/') && link.rel === 'noopener noreferrer'));
    assert.doesNotMatch(result.html, /PRIVATE-MARKER|javascript:|<script|<iframe|<img|<link|latitude|selectionToken/);
    const directory = path.resolve(root, '../../../../../build/qa');
    const preview = path.join(directory, 'w1-04a-template.html'), hostilePath = path.join(directory, 'w1-04a-escaped.html');
    await fs.writeFile(preview, result.html); await fs.writeFile(hostilePath, result.hostile);
    await send('Network.emulateNetworkConditions', {offline: true, latency: 0, downloadThroughput: -1, uploadThroughput: -1});
    const before = networkRequests.length;
    await send('Page.navigate', {url: pathToFileURL(downloadedPath).href});
    await until(`document.querySelector('h1')?.textContent === '새 제목'`);
    assert.equal(await evaluate(`document.querySelector('.days').textContent.includes('내 해변')`), true);
    await send('Page.navigate', {url: pathToFileURL(hostilePath).href});
    await until(`document.querySelector('h1')?.textContent === ${JSON.stringify(attack)}`);
    assert.equal(await evaluate(`typeof window.__downloadXss`), 'undefined');
    await send('Page.navigate', {url: pathToFileURL(preview).href});
    await until(`document.querySelector('h1')?.textContent === ${JSON.stringify(fixture.title)}`);
    assert.equal(await evaluate(`document.querySelectorAll('.motion-control,input:not([type="radio"]),button').length`), 0, 'document has no manual motion toggle');
    assert.equal(await evaluate(`[...document.querySelectorAll('.place-link')].every(link => !link.textContent && link.querySelector('svg') && link.getAttribute('aria-label'))`), true, 'place links are named icons');
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'no-preference'}]});
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('.journey-arrow')).animationName`), 'journey-flight');
    const arrowPosition = await evaluate(`getComputedStyle(document.querySelector('.journey-arrow')).offsetDistance`);
    await delay(180);
    assert.notEqual(await evaluate(`getComputedStyle(document.querySelector('.journey-arrow')).offsetDistance`), arrowPosition, 'hero arrow moves offline');
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]});
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('.journey-arrow')).animationName`), 'none');
    await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'no-preference'}]});
    for (const width of [1440, 390]) {
      await send('Emulation.setDeviceMetricsOverride', {width, height: width === 1440 ? 1100 : 844, deviceScaleFactor: 1, mobile: width === 390});
      assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, 'download fits viewport');
      for (const day of [1, 2]) {
        await evaluate(`document.querySelector('#day-choice-${day}').click()`);
        const cards = await evaluate(`[...document.querySelectorAll('#day-${day} .entry:not(.move) .stop')].map(card => ({
          timeRight: card.querySelector('time').getBoundingClientRect().right, link: !!card.querySelector('.place-link'),
          linkRight: card.querySelector('.place-link')?.getBoundingClientRect().right
        }))`);
        assert.ok(Math.max(...cards.map(card => card.timeRight)) - Math.min(...cards.map(card => card.timeRight)) < 1,
          'time pills align with and without place links');
        assert.deepEqual(cards.map(card => card.link), day === 1 ? [true, true, true] : [true, false, true],
          'every selected place has a link; an unselected meal has none');
        assert.ok(cards.filter(card => card.link).every(card => card.linkRight > card.timeRight && card.linkRight - card.timeRight < 8),
          'icon stays at the card edge instead of inheriting the text gutter');
      }
      await evaluate(`document.querySelector('#day-choice-1').click()`);
      await screenshot(`w1-04a-template-${width === 1440 ? 'desktop' : 'mobile'}.png`);
      await screenshot(`w1-04a-template-${width === 1440 ? 'desktop' : 'mobile'}-full.png`, true);
    }
    const visibleFileDays = () => evaluate(`[...document.querySelectorAll('.day')].filter(day => getComputedStyle(day).display !== 'none').map(day => day.id)`);
    assert.deepEqual(await visibleFileDays(), ['day-1']);
    await evaluate(`document.querySelector('#day-choice-1').focus()`);
    await key('ArrowRight', 'ArrowRight', 39);
    assert.deepEqual(await visibleFileDays(), ['day-2'], 'offline keyboard selection shows only day two');
    await evaluate(`document.querySelector('#day-choice-1').click()`);
    assert.deepEqual(await visibleFileDays(), ['day-1'], 'offline pointer selection restores day one');
    await send('Emulation.setEmulatedMedia', {media: 'print'});
    assert.equal(await evaluate(`[...document.querySelectorAll('.day')].every(day => getComputedStyle(day).display !== 'none')`), true);
    assert.deepEqual(networkRequests.slice(before).filter(request => request.documentURL.startsWith('file:') && /^https?:/.test(request.url)), [], 'offline document has no external resource requests');
  });
  assert.equal(runtimeErrors, 0, 'no uncaught browser exceptions');
  assert.equal(sensitiveConsole, false, 'console does not expose credentials or token');
});
