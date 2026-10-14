'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const download = require('./itinerary-download.js');

test('download accepts only standalone Kakao place links', () => {
  assert.equal(download.safeLink('https://place.map.kakao.com/123'), true);
  for (const value of ['javascript:alert(1)', 'https://place.map.kakao.com.evil.test/123',
    'https://secret@place.map.kakao.com/123', 'https://place.map.kakao.com/123?token=secret',
    'https://place.map.kakao.com/123#secret', 'https://place.map.kakao.com:444/123', null]) {
    assert.equal(download.safeLink(value), false);
  }
});

test('download filename retains Korean and cannot introduce directories or reserved filename characters', () => {
  assert.equal(download.filename('바다 여행'), 'Routy_바다 여행.html');
  assert.doesNotMatch(download.filename('../CON:<x>\\path|?*\u0000'), /[<>:"/\\|?*\u0000]/);
  assert.equal(download.filename(' '), 'Routy_여행 일정.html');
  assert.ok(Array.from(download.filename('여'.repeat(200))).length < 90);
});
