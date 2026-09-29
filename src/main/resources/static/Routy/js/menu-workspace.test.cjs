'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const {validMenus, errorFor} = require('./menu-workspace.js');

const suggestion = (name, targetClientPlaceId = null) => ({name, searchQuery: name, reason: '여행 맥락', targetClientPlaceId});

test('accepts one to five distinct menu suggestions linked only to selected attractions', () => {
  const menus = validMenus({menus: [suggestion('국밥', 'browser-place-1'), suggestion('전골')]}, ['browser-place-1']);
  assert.deepEqual(menus, [suggestion('국밥', 'browser-place-1'), suggestion('전골')]);
  assert.equal(validMenus({menus: []}, []), null);
  assert.equal(validMenus({menus: Array.from({length: 6}, (_, index) => suggestion(`메뉴 ${index}`))}, []), null);
});

test('rejects duplicate, blank and stale target suggestions without reflecting provider content', () => {
  assert.equal(validMenus({menus: [suggestion('국밥'), suggestion(' 국밥 ')]}, []), null);
  assert.equal(validMenus({menus: [suggestion('국밥', 'removed-place')]}, ['selected-place']), null);
  assert.equal(validMenus({menus: [{...suggestion('국밥'), searchQuery: ' '}]}, []), null);
  assert.equal(validMenus({menus: [{...suggestion('국밥'), reason: ''}]}, []), null);
  assert.equal(validMenus({menus: [suggestion('가'.repeat(51))]}, []), null);
});

test('maps API errors to safe guidance and bounded retry time', () => {
  assert.match(errorFor(503, {code: 'AI_UNAVAILABLE', message: 'provider-secret'}), /직접 메뉴를 입력/);
  assert.equal(errorFor(503, {code: 'AI_UNAVAILABLE'}).includes('provider-secret'), false);
  assert.match(errorFor(429, {code: 'RATE_LIMIT_EXCEEDED', retryAfterSeconds: 27}), /27초/);
  assert.equal(errorFor(500, {code: 'UNKNOWN', message: 'raw-provider-response'}).includes('raw-provider-response'), false);
});
