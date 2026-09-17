'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyAuth = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const protectedRoutes = new Set(['/workspace', '/trips', '/trip']);
  const MAX_TIMER_DELAY_MS = 2147483647;
  const messages = Object.freeze({
    VALIDATION_FAILED: '입력 내용을 확인해 주세요.',
    EMAIL_ALREADY_EXISTS: '이미 사용 중인 이메일입니다.',
    AUTHENTICATION_REQUIRED: '인증이 필요합니다. 이메일과 비밀번호를 확인해 주세요.',
    ACCESS_DENIED: '접근 권한이 없습니다.',
    RATE_LIMIT_EXCEEDED: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
    NETWORK_ERROR: '연결을 확인한 뒤 다시 시도해 주세요.',
    SERVER_ERROR: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  });
  function validate(email, password) {
    const errors = {};
    if (!email || email.length > 255 || !/^[^\s@]+@[^\s@]+$/.test(email)) errors.email = '올바른 이메일을 입력해 주세요.';
    const length = Array.from(password).length;
    if (length < 8 || length > 64 || new TextEncoder().encode(password).length > 72) {
      errors.password = '비밀번호는 8~64자, UTF-8 기준 72바이트 이하여야 합니다.';
    } else if (/[\u0000-\u001f\u007f-\u009f]/.test(password)) errors.password = '비밀번호에 제어 문자를 사용할 수 없습니다.';
    return errors;
  }
  function apiError(status, body) {
    const codes = {400: 'VALIDATION_FAILED', 401: 'AUTHENTICATION_REQUIRED', 403: 'ACCESS_DENIED', 409: 'EMAIL_ALREADY_EXISTS', 429: 'RATE_LIMIT_EXCEEDED'};
    const code = codes[status] === body?.code ? body.code : (status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR');
    const fieldErrors = {};
    if (code === 'VALIDATION_FAILED' && Array.isArray(body.fieldErrors)) {
      for (const error of body.fieldErrors) {
        if (error.field === 'email') fieldErrors.email = '이메일 형식과 길이를 확인해 주세요.';
        if (error.field === 'password') fieldErrors.password = '비밀번호 길이와 허용 문자를 확인해 주세요.';
      }
    }
    return {ok: false, code, message: messages[code], fieldErrors};
  }
  function expirationTime(startedAt, expiresInSeconds) {
    if (!Number.isSafeInteger(startedAt) || startedAt < 0
        || !Number.isSafeInteger(expiresInSeconds) || expiresInSeconds <= 0) return null;
    const lifetime = expiresInSeconds * 1000;
    if (!Number.isSafeInteger(lifetime) || lifetime > Number.MAX_SAFE_INTEGER - startedAt) return null;
    return startedAt + lifetime;
  }
  // Credentials and access token remain in this closure; no browser persistence.
  function createClient({fetch: request, now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout, onSessionEnd = () => {}}) {
    let token = null;
    let expiresAt = 0;
    let timer;
    let generation = 0;
    let pending = null;
    function scheduleExpiration(expectedExpiresAt) {
      clearTimer(timer);
      const remaining = expectedExpiresAt - now();
      if (remaining <= 0) { clear('expired'); return; }
      timer = setTimer(() => {
        if (token !== null && expiresAt === expectedExpiresAt) scheduleExpiration(expectedExpiresAt);
      }, Math.min(remaining, MAX_TIMER_DELAY_MS));
    }
    function clear(reason = 'logout') {
      generation++;
      pending?.abort();
      pending = null;
      clearTimer(timer);
      token = null;
      expiresAt = 0;
      onSessionEnd(reason);
    }
    function isAuthenticated() {
      if (token && now() >= expiresAt) clear('expired');
      return token !== null;
    }
    function cancel() {
      generation++;
      pending?.abort();
      pending = null;
    }
    async function submit(mode, email, password) {
      if (pending) return {ok: false, code: 'BUSY'};
      const fieldErrors = validate(email, password);
      if (Object.keys(fieldErrors).length) return {ok: false, code: 'VALIDATION_FAILED', message: messages.VALIDATION_FAILED, fieldErrors};
      const version = ++generation;
      const controller = new AbortController();
      pending = controller;
      const started = now();
      const timeout = setTimer(() => controller.abort(), 15000);
      try {
        const response = await request(mode === 'signup' ? '/api/users' : '/api/auth/login', {
          method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json'},
          body: JSON.stringify({email, password}), signal: controller.signal, credentials: 'omit', cache: 'no-store', redirect: 'error'
        });
        password = '';
        if (version !== generation) return {ok: false, code: 'CANCELLED'};
        if (mode === 'signup' && response.status === 201) return {ok: true};
        const body = await response.json().catch(() => null);
        if (version !== generation) return {ok: false, code: 'CANCELLED'};
        const expiresAtCandidate = expirationTime(started, body?.expiresInSeconds);
        if (mode === 'login' && response.status === 200 && body?.tokenType === 'Bearer'
            && typeof body.accessToken === 'string' && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(body.accessToken)
            && expiresAtCandidate !== null) {
          clearTimer(timer);
          token = body.accessToken;
          expiresAt = expiresAtCandidate;
          scheduleExpiration(expiresAt);
          return {ok: true};
        }
        return apiError(response.status, body);
      } catch (_) {
        return version !== generation ? {ok: false, code: 'CANCELLED'} : {ok: false, code: 'NETWORK_ERROR', message: messages.NETWORK_ERROR, fieldErrors: {}};
      } finally {
        password = '';
        clearTimer(timeout);
        if (pending === controller) pending = null;
      }
    }
    // Future protected adapters share 401 handling; stale responses cannot clear a newer session.
    async function protectedRequest(path, options = {}) {
      if (!/^\/api\/(?!\/)/.test(path) || path.includes('\\') || path.includes('#')) throw new Error('Invalid API path');
      if (!isAuthenticated()) return {ok: false, code: 'AUTHENTICATION_REQUIRED'};
      const version = generation;
      const response = await request(path, {...options, headers: {...options.headers, Authorization: `Bearer ${token}`}, credentials: 'omit', cache: 'no-store', redirect: 'error'});
      if (version !== generation || !isAuthenticated()) return {ok: false, code: 'CANCELLED'};
      if (response.status === 401) clear('expired');
      return response;
    }
    return Object.freeze({submit, cancel, clear, isAuthenticated, protectedRequest});
  }
  return Object.freeze({validate, apiError, expirationTime, createClient, isProtected: route => protectedRoutes.has(route)});
});
