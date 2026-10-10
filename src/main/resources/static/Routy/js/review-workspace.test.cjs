'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const review = require('./review-workspace.js');

const place = (token, latitude = 37.5) => ({selectionToken: token, latitude, longitude: 127});
const confirmed = () => ({request: {regionId: 'KR-26', travelMode: 'CAR', startDate: '2026-10-01', endDate: '2026-10-02',
  hotelSelectionToken: 'hotel', places: [{clientPlaceId: 'a', selectionToken: 'attraction', displayName: '내 해변', day: '2026-10-01', order: 1}]},
  slots: [{date: '2026-10-01', mealType: 'LUNCH'}, {date: '2026-10-02', mealType: 'DINNER'}],
  result: {days: [{date: '2026-10-01', items: [
    {order: 1, type: 'VISIT', clientPlaceId: 'a', displayName: '내 해변', startTime: '10:00', endTime: '11:30'},
    {order: 2, type: 'MEAL', startTime: '12:00', endTime: '13:00'}
  ]}, {date: '2026-10-02', items: [{order: 1, type: 'MEAL', startTime: '18:00', endTime: '19:00'}]}]}});

test('create request uses confirmed placement and user restaurant name, with unselected meal default', () => {
  const chosen = [{slot: '2026-10-01|LUNCH', selectionToken: 'restaurant', displayName: '내 식당', memo: '메모'}];
  const body = review.createRequest(confirmed(), chosen, '  내 여행  ');
  assert.equal(body.title, '내 여행');
  assert.equal(body.places[0].order, 1);
  assert.equal(body.hotelDisplayName, '숙소');
  assert.deepEqual(body.meals, [
    {date: '2026-10-01', mealType: 'LUNCH', restaurantSelectionToken: 'restaurant', displayName: '내 식당', memo: '메모'},
    {date: '2026-10-02', mealType: 'DINNER', restaurantSelectionToken: null, displayName: '저녁 식사', memo: null}
  ]);
  assert.equal('latitude' in body, false);
  assert.equal(review.createRequest(confirmed(), [{...chosen[0], displayName: ''}], '내 여행'), null);
  assert.equal(review.createRequest(confirmed(), [chosen[0], chosen[0]], '내 여행'), null);
  assert.equal(review.createRequest(confirmed(), [{...chosen[0], slot: '2026-10-03|LUNCH'}], '내 여행'), null);
});

test('review stops follow the selected day and omit unselected restaurants', () => {
  const selected = {startBoundary: place('start'), endBoundary: place('end'), hotel: place('hotel'), attractions: [place('attraction')]};
  const restaurants = [{slot: '2026-10-01|LUNCH', ...place('restaurant', 36.5), displayName: '내 식당'}];
  assert.deepEqual(review.stopsForDay(confirmed(), selected, restaurants, 0).map(stop => stop.label), ['시작 장소', '내 해변', '내 식당', '숙소 도착']);
  assert.deepEqual(review.stopsForDay(confirmed(), selected, restaurants, 1).map(stop => stop.label), ['숙소 출발', '종료 장소']);
});

test('saved response shape and safe links are checked before display', () => {
  const item = {order: 1, type: 'VISIT', displayName: '사용자 이름', placeUrl: 'https://place.map.kakao.com/1', startTime: '10:00', endTime: '11:00'};
  const plan = {travelPlanId: 1, title: '여행', region: {displayName: '부산광역시'}, travelMode: 'CAR', startDate: '2026-10-01', endDate: '2026-10-01', warnings: [], hotel: null,
    days: [{day: 1, date: '2026-10-01', items: [item]}]};
  assert.equal(review.validPlan(plan), true);
  assert.equal(review.validPlan({...plan, days: [{...plan.days[0], items: [{...item, placeUrl: 'javascript:alert(1)'}]}]}), false);
  assert.equal(review.validPlan({...plan, days: [{...plan.days[0], items: [{...item, type: 'STAY'}]}]}), false);
  assert.doesNotMatch(review.errorText({status: 422}, {code: 'ROUTE_NOT_FOUND', message: 'provider secret'}), /secret/);
});
