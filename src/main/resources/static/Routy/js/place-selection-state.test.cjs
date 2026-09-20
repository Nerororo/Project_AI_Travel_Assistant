'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const state = require('./place-selection-state.js');

function place(id = '1', overrides = {}) {
  return {
    kakaoPlaceId: id,
    placeUrl: `https://place.map.kakao.com/${id}`,
    providerDisplayName: `provider-${id}`,
    address: `temporary-address-${id}`,
    latitude: 36.1,
    longitude: 127.1,
    suggestedStayMinutes: 90,
    selectionToken: `temporary-token-${id}`,
    ...overrides
  };
}

test('keeps defensive transient copies only in a store instance', () => {
  const store = state.createStore();
  const context = store.setContext(7, 'KR-CITY');
  const source = place();
  assert.equal(store.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [source]), true);
  source.latitude = 35;
  source.selectionToken = 'changed';
  assert.equal(store.select(context.generation, state.ROLES.ATTRACTION, '1'), true);
  assert.deepEqual(store.selected().attractions.map(item => [item.latitude, item.selectionToken]),
    [[36.1, 'temporary-token-1']]);
  assert.equal(Object.isFrozen(store.selected().attractions[0]), true);
});

test('supports multiple attractions and exactly one directly selected hotel', () => {
  const store = state.createStore();
  const context = store.setContext(7, 'KR-CITY');
  store.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [place('1'), place('2')]);
  store.select(context.generation, state.ROLES.ATTRACTION, '1');
  store.select(context.generation, state.ROLES.ATTRACTION, '2');
  store.acceptSearchResults(context.generation, state.ROLES.HOTEL, [
    place('3', {suggestedStayMinutes: null}), place('4', {suggestedStayMinutes: null})
  ]);
  store.select(context.generation, state.ROLES.HOTEL, '3');
  store.select(context.generation, state.ROLES.HOTEL, '4');
  assert.deepEqual(store.selected().attractions.map(item => item.kakaoPlaceId), ['1', '2']);
  assert.equal(store.selected().hotel.kakaoPlaceId, '4');
});

test('user and region changes erase candidates tokens coordinates and previous generation access', () => {
  for (const next of [[8, 'KR-CITY'], [7, 'KR-OTHER']]) {
    const store = state.createStore();
    const old = store.setContext(7, 'KR-CITY');
    store.acceptSearchResults(old.generation, state.ROLES.ATTRACTION, [place()]);
    store.select(old.generation, state.ROLES.ATTRACTION, '1');
    const changed = store.setContext(...next);
    assert.deepEqual(store.selected(), {attractions: [], hotel: null});
    assert.equal(store.select(old.generation, state.ROLES.ATTRACTION, '1'), false);
    assert.equal(store.acceptSearchResults(old.generation, state.ROLES.ATTRACTION, [place()]), false);
    assert.ok(changed.generation > old.generation);
  }
});

test('completion cancellation and authentication end make old selections unusable', () => {
  for (const action of ['complete', 'cancel', 'authenticationEnded']) {
    const store = state.createStore();
    const context = store.setContext(7, 'KR-CITY');
    store.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [place()]);
    store.select(context.generation, state.ROLES.ATTRACTION, '1');
    store[action]();
    assert.deepEqual(store.selected(), {attractions: [], hotel: null});
    assert.equal(store.context().authenticatedUserId, null);
    assert.equal(store.select(context.generation, state.ROLES.ATTRACTION, '1'), false);
  }
});

test('pagehide clears memory and detached lifecycle no longer mutates the store', () => {
  const listeners = new Map();
  const windowLike = {
    addEventListener(name, listener) { listeners.set(name, listener); },
    removeEventListener(name, listener) { if (listeners.get(name) === listener) listeners.delete(name); }
  };
  const store = state.createStore();
  const context = store.setContext(7, 'KR-CITY');
  store.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [place()]);
  store.select(context.generation, state.ROLES.ATTRACTION, '1');
  const detach = store.attachPageLifecycle(windowLike);
  listeners.get('pagehide')();
  assert.deepEqual(store.selected(), {attractions: [], hotel: null});
  detach();
  assert.equal(listeners.has('pagehide'), false);
});

test('a reload creates a fresh empty store and browser persistence is never touched', () => {
  const descriptors = {};
  for (const name of ['localStorage', 'sessionStorage', 'indexedDB']) {
    descriptors[name] = Object.getOwnPropertyDescriptor(globalThis, name);
    Object.defineProperty(globalThis, name, {configurable: true, get() { throw new Error(`${name} accessed`); }});
  }
  try {
    const first = state.createStore();
    const context = first.setContext(7, 'KR-CITY');
    first.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [place()]);
    first.select(context.generation, state.ROLES.ATTRACTION, '1');
    const reloaded = state.createStore();
    assert.deepEqual(reloaded.selected(), {attractions: [], hotel: null});
  } finally {
    for (const [name, descriptor] of Object.entries(descriptors)) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else delete globalThis[name];
    }
  }
});

test('rejects malformed provider fields and duplicate result identifiers', () => {
  const store = state.createStore();
  const context = store.setContext(7, 'KR-CITY');
  for (const invalid of [
    place('1', {selectionToken: ''}), place('1', {latitude: 10}),
    place('1', {placeUrl: 'https://example.invalid/1'}), place('1', {suggestedStayMinutes: 95})
  ]) {
    assert.throws(() => store.acceptSearchResults(context.generation, state.ROLES.ATTRACTION, [invalid]));
  }
  assert.throws(() => store.acceptSearchResults(
    context.generation, state.ROLES.ATTRACTION, [place('1'), place('1')]));
});
