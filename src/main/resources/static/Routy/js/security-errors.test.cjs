'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const auth = require('./auth.js');

test('authentication throttling shows only a positive safe server wait', () => {
  assert.match(auth.apiError(429, {code: 'RATE_LIMIT_EXCEEDED', retryAfterSeconds: 43}).message, /43초/);
  for (const value of [0, -1, 1.5, '43', Infinity, Number.MAX_SAFE_INTEGER + 1]) {
    assert.doesNotMatch(auth.apiError(429, {code: 'RATE_LIMIT_EXCEEDED', retryAfterSeconds: value}).message, /초 뒤/);
  }
});

test('oversized requests offer safe recovery across authentication and journey screens', () => {
  const body = {code: 'REQUEST_TOO_LARGE', message: '<script>private-response</script>'};
  const messages = [
    auth.apiError(413, body).message,
    require('./preview.js').regionError(413, body).message,
    require('./place-workspace.js').errorFor(413, body).message,
    require('./menu-workspace.js').errorFor(413, body),
    require('./estimate-workspace.js').errorText({status: 413}, body),
    require('./restaurant-workspace.js').errorText({status: 413}, body),
    require('./review-workspace.js').errorText({status: 413}, body),
    require('./trips-workspace.js').errorText(413, body)
  ];
  for (const message of messages) {
    assert.match(message, /크기.*줄/);
    assert.doesNotMatch(message, /private-response|script/);
  }
});
