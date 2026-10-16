'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const ui = require('./place-workspace.js');

test('standard map pins show safe labels, preserve viewport on selection and discard old windows', async () => {
  const pins = [], windows = [], maps = [];
  const window = {document: {createElement: () => ({})}, kakao: {maps: {
    LatLng: class { constructor(lat, lng) { this.lat = lat; this.lng = lng; } },
    LatLngBounds: class { extend() {} },
    Map: class { constructor() { this.fits = 0; maps.push(this); } relayout() {} setBounds() { this.fits++; } },
    Marker: class { constructor(options) { Object.assign(this, options); pins.push(this); } setMap(map) { this.map = map; } },
    InfoWindow: class { constructor(options) { Object.assign(this, options); windows.push(this); } open(map, marker) { this.map = map; this.marker = marker; } close() { this.map = null; } },
    event: {addListener(marker, event, listener) { marker[event] = listener; }}
  }}};
  const element = {classList: {remove() {}}, replaceChildren() {}};
  const view = ui.createMapView(window, element, {});
  assert.equal(await view.ensure(), true);
  const places = [{kakaoPlaceId: 'a', providerDisplayName: '<img onerror=alert(1)>', latitude: 35, longitude: 128}];
  const chosen = [];
  view.render(places, new Set(), id => chosen.push(id));
  assert.equal(pins[0].image, undefined);
  assert.equal(windows[0].content.textContent, places[0].providerDisplayName);
  pins[0].mouseover(); assert.equal(windows[0].marker, pins[0]);
  pins[0].mouseout(); assert.equal(windows[0].map, null);
  pins[0].click(); assert.deepEqual(chosen, ['a']);
  view.render(places, new Set(['a']), id => chosen.push(id));
  assert.equal(pins[0].map, null);
  assert.equal(maps[0].fits, 1);
  assert.match(pins[1].title, /✓ 선택됨/);
  assert.equal(windows[1].map, maps[0]);
  pins[1].mouseout(); assert.equal(windows[1].map, maps[0]);
  view.render([{...places[0], latitude: 36}], new Set(), () => {});
  assert.equal(maps[0].fits, 2);
  assert.equal(windows[1].map, null);
  view.dispose();
  assert.ok(pins.every(pin => pin.map === null));
  assert.ok(windows.every(info => info.map === null));
});

test('attraction search retains district filter while moving the map and separates explicit whole-region search', () => {
  assert.deepEqual(ui.searchBody('ATTRACTION', 'city', '해변', {districtFilterId: 'district', center: {latitude: 35, longitude: 129}, page: 2}), {
    regionId: 'city', districtFilterId: 'district', placeRole: 'ATTRACTION', query: '해변',
    center: {latitude: 35, longitude: 129}, radiusMeters: 20000, page: 2, size: 10
  });
  assert.deepEqual(ui.searchBody('ATTRACTION', 'city', '해변', {wholeRegion: true}).center, null);
  assert.equal(ui.searchBody('ATTRACTION', 'city', '해변', {wholeRegion: true}).radiusMeters, null);
});

test('boundary search uses the dedicated request contract', () => {
  assert.deepEqual(ui.searchBody('TRAVEL_BOUNDARY', 'city', '역'), {regionId: 'city', query: '역', page: 1, size: 10});
  assert.deepEqual(ui.searchBody('TRAVEL_BOUNDARY', 'city', '역', {center: {latitude: 35, longitude: 129}}), {
    regionId: 'city', query: '역', center: {latitude: 35, longitude: 129}, radiusMeters: 20000, page: 1, size: 10
  });
});

test('hotel modes have exclusive request fields and require map bounds for the map mode', () => {
  const base = {regionId: 'city', attractionSelectionTokens: ['temporary-token'], page: 1, size: 10};
  assert.deepEqual(ui.hotelBody('city', ['temporary-token'], 'GEOMETRIC_MEDIAN'), {...base, mode: 'GEOMETRIC_MEDIAN'});
  assert.deepEqual(ui.hotelBody('city', ['temporary-token'], 'GEOMETRIC_MEDIAN_10'), {...base, mode: 'GEOMETRIC_MEDIAN', radiusMeters: 10000});
  assert.deepEqual(ui.hotelBody('city', ['temporary-token'], 'MEDOID'), {...base, mode: 'MEDOID'});
  const bounds = {minLatitude: 35, minLongitude: 128, maxLatitude: 36, maxLongitude: 129};
  assert.deepEqual(ui.hotelBody('city', ['temporary-token'], 'MAP_BOUNDS', bounds), {...base, mode: 'MAP_BOUNDS', bounds});
  assert.throws(() => ui.hotelBody('city', ['temporary-token'], 'MAP_BOUNDS', null));
});

test('invalid responses and provider text do not enter the displayed error', () => {
  assert.equal(ui.validPage({places: [], page: 1, hasNext: false}), true);
  assert.equal(ui.validPage({places: [], page: '1', hasNext: false}), false);
  assert.equal(ui.errorFor(503, {code: 'PLACE_PROVIDER_UNAVAILABLE', message: 'sensitive provider text'}).message.includes('sensitive'), false);
  assert.match(ui.errorFor(429, {code: 'RATE_LIMIT_EXCEEDED', retryAfterSeconds: 13}).message, /13초/);
});

test('image loader keeps safe photos in one draggable gallery and reuses the lookup', async () => {
  class Response { constructor(body) { this.ok = true; this.body = body; } async json() { return this.body; } }
  const calls = [];
  const document = {createElement: tag => ({tag, children: [], listeners: {}, scrollLeft: 0, clientWidth: 144,
    append(...children) { this.children.push(...children); },
    addEventListener(type, listener) { this.listeners[type] = listener; },
    setAttribute(name, value) { this[name] = value; }, setPointerCapture() {}})};
  const window = {Response, crypto: {randomUUID: () => 'request-id'}};
  const client = {protectedRequest: async (path, options) => {
    calls.push({path, options});
    return new Response({thumbnailUrls: ['https://search2.kakaocdn.net/argon/first',
      'https://example.invalid/photo', 'https://search2.kakaocdn.net/argon/second']});
  }};
  const item = {isConnected: true, classList: {add: value => assert.equal(value, 'has-thumbnail')}, children: [],
    append(child) { this.children.push(child); }};
  const loader = ui.createImageLoader(document, window, client);
  const place = {kakaoPlaceId: '1', providerDisplayName: '테스트 장소', address: '테스트도 테스트시'};
  loader.append(item, place);
  loader.append(item, place);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 1);
  assert.equal(calls[0].path, '/api/places/image');
  assert.deepEqual(JSON.parse(calls[0].options.body), {placeName: place.providerDisplayName, address: place.address});
  const strip = item.children[0].children[0];
  assert.deepEqual(strip.children.map(image => image.src), [
    'https://search2.kakaocdn.net/argon/first', 'https://search2.kakaocdn.net/argon/second']);
  assert.equal(strip.children[0].alt, '');
  assert.equal(strip.tabIndex, 0);
  strip.children[0].onload();
  strip.listeners.pointerdown({pointerType: 'mouse', button: 0, clientX: 100, pointerId: 1, preventDefault() {}});
  strip.listeners.pointermove({clientX: 20});
  assert.equal(strip.scrollLeft, 80);
  strip.listeners.scroll();
  assert.equal(item.children[0].children[1].textContent, '2 / 2');
});
