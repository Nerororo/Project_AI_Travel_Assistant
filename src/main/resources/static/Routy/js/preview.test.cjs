'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const ui = require('./preview.js');

test('known hashes resolve to page-level views', () => {
  assert.equal(ui.viewForRoute('#/'), 'landing');
  assert.equal(ui.viewForRoute('#/auth'), 'auth');
  assert.equal(ui.viewForRoute('#/workspace'), 'workspace');
  assert.equal(ui.viewForRoute('#/trips'), 'trips');
  assert.equal(ui.viewForRoute('#/trip'), 'detail');
  assert.equal(ui.viewForRoute('#/shared'), 'shared');
});

test('unknown and malformed hashes safely return to landing', () => {
  for (const hash of ['', '#', '#/unknown', '#//workspace', '#/<script>']) {
    assert.equal(ui.normalizeRoute(hash), '/');
    assert.equal(ui.viewForRoute(hash), 'landing');
  }
});

test('trailing slash and query do not create duplicate view identities', () => {
  assert.equal(ui.normalizeRoute('#/workspace/'), '/workspace');
  assert.equal(ui.normalizeRoute('#/workspace?state=loading'), '/workspace');
});

test('primary navigation keeps detail under My Trips and share public', () => {
  assert.equal(ui.navGroupForView('workspace'), 'workspace');
  assert.equal(ui.navGroupForView('trips'), 'trips');
  assert.equal(ui.navGroupForView('detail'), 'trips');
  assert.equal(ui.navGroupForView('shared'), 'landing');
});

test('workspace exposes all eight product stages without API results', () => {
  assert.equal(ui.STEPS.length, 8);
  assert.deepEqual(ui.STEPS.map(step => step[0]), ['지역 선택', '여행 조건', '관광지', '숙소', '메뉴', '추정 일정', '음식점', '검토']);
});

test('step navigation stays inside the workspace boundary', () => {
  assert.equal(ui.clampStep(-10), 0);
  assert.equal(ui.clampStep(99), 7);
  assert.equal(ui.clampStep('3'), 3);
  assert.equal(ui.nextStep(0, -1), 0);
  assert.equal(ui.nextStep(6, 1), 7);
  assert.equal(ui.nextStep(7, 1), 7);
});

test('state preview distinguishes initial loading empty and error', () => {
  const models = ['initial', 'loading', 'empty', 'error'].map(ui.stateModel);
  assert.equal(new Set(models.map(model => model.title)).size, 4);
  assert.equal(ui.stateModel('unknown'), ui.VIEW_STATES.initial);
});
