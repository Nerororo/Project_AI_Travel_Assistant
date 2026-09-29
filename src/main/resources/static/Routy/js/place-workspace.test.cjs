'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const ui = require('./place-workspace.js');

test('attraction search retains district filter while moving the map and separates explicit whole-region search', () => {
  assert.deepEqual(ui.searchBody('ATTRACTION', 'city', '해변', {districtFilterId: 'district', center: {latitude: 35, longitude: 129}, page: 2}), {
    regionId: 'city', districtFilterId: 'district', placeRole: 'ATTRACTION', query: '해변',
    center: {latitude: 35, longitude: 129}, radiusMeters: 20000, page: 2, size: 15
  });
  assert.deepEqual(ui.searchBody('ATTRACTION', 'city', '해변', {wholeRegion: true}).center, null);
  assert.equal(ui.searchBody('ATTRACTION', 'city', '해변', {wholeRegion: true}).radiusMeters, null);
});

test('boundary search uses the dedicated request contract', () => {
  assert.deepEqual(ui.searchBody('TRAVEL_BOUNDARY', 'city', '역'), {regionId: 'city', query: '역', page: 1, size: 15});
  assert.deepEqual(ui.searchBody('TRAVEL_BOUNDARY', 'city', '역', {center: {latitude: 35, longitude: 129}}), {
    regionId: 'city', query: '역', center: {latitude: 35, longitude: 129}, radiusMeters: 20000, page: 1, size: 15
  });
});

test('hotel modes have exclusive request fields and require map bounds for the map mode', () => {
  const base = {regionId: 'city', attractionSelectionTokens: ['temporary-token'], page: 1, size: 15};
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
