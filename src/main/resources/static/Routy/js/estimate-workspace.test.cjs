'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {buildRequest, validResult, errorText} = require('./estimate-workspace.js');

const base = () => ({regionId: 'KR-26', summary: {days: 2, travelMode: 'CAR'},
  selected: {startBoundary: {selectionToken: 'start'}, endBoundary: {selectionToken: 'end'}, hotel: {selectionToken: 'hotel'}},
  places: [{clientPlaceId: '1b86ad0e-85a7-4aa1-9db9-c6c57dc4e141', selectionToken: 'place', displayName: '산책', stayMinutes: 90, day: null, order: null}],
  foods: ['국밥'], startDate: '2026-10-01', times: [{start: '10:00', end: '20:00'}, {start: '09:00', end: '18:00'}]});

test('estimate request contains every date and ephemeral selection token', () => {
  const result = buildRequest(base());
  assert.equal(result.value.endDate, '2026-10-02');
  assert.deepEqual(result.value.days.map(day => day.date), ['2026-10-01', '2026-10-02']);
  assert.equal(result.value.startBoundarySelectionToken, 'start');
  assert.equal(result.value.hotelSelectionToken, 'hotel');
  assert.equal(result.value.places[0].day, null);
});

test('invalid dates, clocks, and missing hotel are rejected before request', () => {
  assert.match(buildRequest({...base(), startDate: '2026-02-30'}).error, /날짜/);
  assert.match(buildRequest({...base(), times: [{start: '20:00', end: '10:00'}, {start: '09:00', end: '18:00'}]}).error, /시각/);
  assert.match(buildRequest({...base(), selected: {...base().selected, hotel: null}}).error, /숙소/);
});

test('manual placement requires consecutive order within each day', () => {
  const input = base();
  input.places[0].day = '2026-10-02'; input.places[0].order = 2;
  assert.match(buildRequest(input).error, /순서/);
  input.places[0].order = 1;
  assert.equal(buildRequest(input).value.places[0].day, '2026-10-02');
});

test('one-day trip omits hotel and response must remain an unverified estimate', () => {
  const input = {...base(), summary: {days: 1, travelMode: 'PUBLIC_TRANSIT'}, times: [{start: '09:00', end: '18:00'}]};
  const request = buildRequest(input).value;
  assert.equal(request.hotelSelectionToken, null);
  const item = {clientPlaceId: input.places[0].clientPlaceId, order: 1, type: 'VISIT', displayName: '산책', startTime: '10:00', endTime: '11:30', estimatedMinutes: null};
  assert.equal(validResult({routeVerified: false, days: [{date: '2026-10-01', items: [item]}]}, request), true);
  assert.equal(validResult({routeVerified: true, days: [{date: '2026-10-01', items: [item]}]}, request), false);
});

test('capacity error gives adjustment guidance without copying provider text', () => {
  const message = errorText({status: 422}, {code: 'PLAN_CAPACITY_EXCEEDED', message: 'raw', details: {date: '2026-10-01', plannedEndTime: '20:30', allowedEndTime: '20:00', exceededMinutes: 30}});
  assert.match(message, /30분 초과/);
  assert.doesNotMatch(message, /raw/);
});
