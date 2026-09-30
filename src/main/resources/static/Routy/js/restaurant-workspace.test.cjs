'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {searchBody, validResponse, errorText} = require('./restaurant-workspace.js');

const placeId = '1b86ad0e-85a7-4aa1-9db9-c6c57dc4e141';
const confirmed = {request: {places: [{clientPlaceId: placeId, day: '2026-10-01', order: 1}]}, slots: [{date: '2026-10-01', mealType: 'LUNCH', startTime: '12:00', endTime: '13:00'}]};
const slot = confirmed.slots[0];
const menu = {name: '국밥', searchQuery: '지역 국밥', targetClientPlaceId: placeId};
const reference = {kind: 'ATTRACTION', clientPlaceId: placeId, latitude: 36, longitude: 127};
const response = {mealDate: '2026-10-01', mealStartTime: '12:00', mealEndTime: '13:00', previousPlace: reference,
  nextPlace: {kind: 'END_BOUNDARY', clientPlaceId: null, latitude: 36.01, longitude: 127.01}, referenceAttraction: reference,
  places: [{kakaoPlaceId: '123', placeUrl: 'https://place.map.kakao.com/123', providerDisplayName: '가상 음식점',
    address: '가상 주소', latitude: 36.005, longitude: 127.005, selectionToken: 'temporary', detourKilometers: 0.5, estimatedDetourMinutes: 10}],
  emptyReason: null, page: 1, hasNext: false};

test('search uses confirmed visit order, meal slot and optional reference', () => {
  const body = searchBody(confirmed, slot, menu, placeId);
  assert.equal(body.estimate.places[0].order, 1);
  assert.equal(body.mealType, 'LUNCH');
  assert.equal(body.menuQuery, '지역 국밥');
  assert.equal(body.referenceAttractionClientPlaceId, placeId);
  assert.equal(body.size, 15);
});

test('rejects missing slots, cross-day reference and invalid map bounds', () => {
  assert.equal(searchBody(confirmed, null, menu, null), null);
  assert.equal(searchBody(confirmed, slot, menu, 'unknown'), null);
  assert.equal(searchBody(confirmed, slot, menu, null, {minLatitude: 36, minLongitude: 127, maxLatitude: 35, maxLongitude: 128}), null);
});

test('validates temporary provider result and safe external link', () => {
  const body = searchBody(confirmed, slot, menu, placeId);
  assert.equal(validResponse(response, body), true);
  assert.equal(validResponse({...response, places: [{...response.places[0], placeUrl: 'javascript:alert(1)'}]}, body), false);
  assert.equal(validResponse({...response, mealDate: '2026-10-02'}, body), false);
  assert.equal(validResponse({...response, places: [], emptyReason: null}, body), false);
});

test('provider failure differs from empty candidates without reflecting provider message', () => {
  assert.match(errorText({status: 503}, {code: 'PLACE_PROVIDER_UNAVAILABLE', message: 'raw'}), /잠시/);
  assert.doesNotMatch(errorText({status: 503}, {code: 'PLACE_PROVIDER_UNAVAILABLE', message: 'raw'}), /raw/);
});
