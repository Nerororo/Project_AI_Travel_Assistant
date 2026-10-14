'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const ui = require('./trips-workspace.js');
const review = require('./review-workspace.js');
const plan = {title: '여행', hotel: {planPlaceId: 8, displayName: '숙소', memo: null}, days: [{items: [
  {planPlaceId: 7, displayName: '산책', memo: '준비'}, {planPlaceId: null, type: 'MOVE'}, {planPlaceId: 7, displayName: '산책', memo: '준비'}
]}]};
test('direct routes accept positive safe IDs and only fixed-length share tokens', () => {
  assert.equal(ui.route('#/trip?id=42').id, 42);
  for (const id of ['0', '-1', '1e2', '9007199254740992', 'javascript:']) assert.equal(ui.route(`#/trip?id=${id}`).id, null);
  assert.equal(ui.route(`#/shared?token=${'a'.repeat(43)}`).token, 'a'.repeat(43));
  assert.equal(ui.route('#/shared?token=<script>').token, null);
});
test('editing deduplicates saved places, includes hotel, and sends only changed text', () => {
  assert.deepEqual(ui.editablePlaces(plan).map(place => place.planPlaceId), [8, 7]);
  assert.deepEqual(ui.patch(plan, '여행', [{planPlaceId: 7, displayName: '산책', memo: '준비'}]), {});
  assert.deepEqual(ui.patch(plan, ' 새 여행 ', [{planPlaceId: 8, displayName: '쉼터', memo: ''}, {planPlaceId: 7, displayName: '산책', memo: ''}]),
    {title: '새 여행', placeEdits: [{planPlaceId: 8, displayName: '쉼터'}, {planPlaceId: 7, memo: null}]});
});
test('invalid text and unknown place IDs cannot produce a patch', () => {
  assert.equal(ui.patch(plan, '', []), null);
  assert.equal(ui.patch(plan, 'x'.repeat(101), []), null);
  for (const edit of [{planPlaceId: 99, displayName: '장소', memo: ''}, {planPlaceId: 7, displayName: ' ', memo: ''},
    {planPlaceId: 7, displayName: '장소', memo: 'x'.repeat(1001)}]) assert.equal(ui.patch(plan, '여행', [edit]), null);
});
test('ownership, missing links, rate limits and storage caps provide recovery without server text', () => {
  assert.match(ui.errorText(403, {}), /권한/); assert.match(ui.errorText(404, {}), /만료/);
  assert.match(ui.errorText(429, {retryAfterSeconds: 30}), /30초/);
  assert.doesNotMatch(ui.errorText(500, {message: '<script>'}), /script/);
  assert.match(review.errorText({status: 409}, {code: 'TRAVEL_PLAN_LIMIT_EXCEEDED'}), /내 여행.*삭제/);
});
