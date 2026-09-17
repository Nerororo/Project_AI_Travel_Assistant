'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root && root.document) api.mount(root.document, root);
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const ROUTES = Object.freeze({
    '/': 'landing',
    '/auth': 'auth',
    '/workspace': 'workspace',
    '/trips': 'trips',
    '/trip': 'detail',
    '/shared': 'shared'
  });

  const STEPS = Object.freeze([
    ['지역 선택', '어디로 떠나고 싶나요?', '직접 검색하거나 세 곳을 추천받는 화면이 이 영역에 연결됩니다.'],
    ['여행 조건', '여행의 시간과 이동을 정해요', '1~7일의 기간과 자동차 또는 대중교통 하나를 선택합니다.'],
    ['관광지', '가고 싶은 장면을 골라요', '검색 목록과 지도, 마커를 한 화면에서 연결할 자리입니다.'],
    ['숙소', '하루의 시작과 끝을 정해요', '순위 없이 지도 탐색 결과에서 사용자가 숙소를 직접 선택합니다.'],
    ['메뉴', '여행 사이의 맛을 생각해요', 'AI가 구조화한 메뉴를 검토하고 직접 수정하는 영역입니다.'],
    ['추정 일정', '하루 안에 들어오는지 살펴봐요', '실제 경로 검증 전 추정 일정과 조정 지점을 표시합니다.'],
    ['음식점', '식사 시간의 장소를 골라요', '식사 슬롯 앞뒤 장소와 후보를 목록·지도에 함께 표시합니다.'],
    ['검토', '이제 실제 경로를 확인할 차례예요', '완료 생성의 영향과 경고를 확인하고 중복 제출을 막습니다.']
  ]);

  const VIEW_STATES = Object.freeze({
    initial: Object.freeze({symbol: '⌁', title: '기능이 연결될 작업 영역', description: 'W1 후속 작업에서 실제 입력과 서버 응답을 이 구조에 연결합니다.'}),
    loading: Object.freeze({symbol: '', title: '불러오는 중', description: '요청 중에는 현재 입력과 선택을 유지하고 중복 제출을 막습니다.'}),
    empty: Object.freeze({symbol: '○', title: '표시할 결과가 없어요', description: '빈 결과의 이유와 다음 행동을 같은 화면에서 안내합니다.'}),
    error: Object.freeze({symbol: '!', title: '지금은 이어갈 수 없어요', description: '제공자 장애, 한도와 입력 오류를 구분하고 안전한 복구 행동을 제공합니다.'})
  });

  function normalizeRoute(hash) {
    const raw = String(hash || '').replace(/^#/, '').split('?')[0].replace(/\/+$/, '') || '/';
    return Object.hasOwn(ROUTES, raw) ? raw : '/';
  }

  function viewForRoute(hash) {
    return ROUTES[normalizeRoute(hash)];
  }

  function navGroupForView(view) {
    if (view === 'workspace') return 'workspace';
    if (view === 'trips' || view === 'detail') return 'trips';
    return 'landing';
  }

  function clampStep(step) {
    const value = Number.isFinite(Number(step)) ? Math.trunc(Number(step)) : 0;
    return Math.min(STEPS.length - 1, Math.max(0, value));
  }

  function nextStep(step, direction) {
    return clampStep(clampStep(step) + (direction < 0 ? -1 : 1));
  }

  function stateModel(name) {
    return VIEW_STATES[name] || VIEW_STATES.initial;
  }

  const REGION_MESSAGES = Object.freeze({
    VALIDATION_FAILED: '입력 내용을 확인해 주세요.',
    AUTHENTICATION_REQUIRED: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
    REQUEST_IN_PROGRESS: '같은 추천 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.',
    REQUEST_ALREADY_COMPLETED: '이 추천 요청은 이미 완료되었습니다. 새로 추천받아 주세요.',
    RATE_LIMIT_EXCEEDED: '추천 한도에 도달했습니다. 안내된 시간 뒤 다시 시도해 주세요.',
    AI_UNAVAILABLE: 'AI 추천을 잠시 사용할 수 없습니다. 직접 검색을 이용해 주세요.',
    AI_RESPONSE_INVALID: '추천 결과를 확인할 수 없습니다. 직접 검색을 이용해 주세요.',
    NETWORK_ERROR: '연결을 확인한 뒤 다시 시도해 주세요.',
    SERVER_ERROR: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  });

  function validRegion(region, recommendation = false) {
    if (!region || typeof region.regionId !== 'string' || !region.regionId || typeof region.name !== 'string' || !region.name) return false;
    if (recommendation) return typeof region.provinceName === 'string' && typeof region.reason === 'string' && Boolean(region.reason.trim());
    return typeof region.selectable === 'boolean' && typeof region.placeSearchFilterable === 'boolean';
  }

  function parseRegionSearch(body) {
    return Array.isArray(body?.regions) && body.regions.every(region => validRegion(region)) ? body.regions : null;
  }

  function parseRegionRecommendations(body) {
    if (!Array.isArray(body?.regions) || body.regions.length !== 3 || !body.regions.every(region => validRegion(region, true))) return null;
    return new Set(body.regions.map(region => region.regionId)).size === 3 ? body.regions : null;
  }

  function regionError(status, body) {
    const allowed = new Set(['VALIDATION_FAILED', 'AUTHENTICATION_REQUIRED', 'REQUEST_IN_PROGRESS', 'REQUEST_ALREADY_COMPLETED', 'RATE_LIMIT_EXCEEDED', 'AI_UNAVAILABLE', 'AI_RESPONSE_INVALID']);
    const code = allowed.has(body?.code) ? body.code : (status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR');
    let message = REGION_MESSAGES[code];
    if (code === 'RATE_LIMIT_EXCEEDED' && Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0) message += ` 약 ${body.retryAfterSeconds}초 후 다시 시도할 수 있어요.`;
    return {code, message};
  }

  function mount(document, window) {
    const pages = Array.from(document.querySelectorAll('[data-view]'));
    const content = document.querySelector('#content');
    const stepList = document.querySelector('#step-list');
    const stepTitle = document.querySelector('#step-title');
    const stepDescription = document.querySelector('#step-description');
    const stepCount = document.querySelector('#step-count');
    const stateStage = document.querySelector('#state-stage');
    const regionWorkspace = document.querySelector('#region-workspace');
    const futureStepPreview = document.querySelector('#future-step-preview');
    const previousStep = document.querySelector('#previous-step');
    const nextStepButton = document.querySelector('#next-step');
    const cancelDialog = document.querySelector('#cancel-dialog');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const hoverFine = window.matchMedia('(hover: hover) and (pointer: fine)');
    let currentStep = 0;
    let currentState = 'initial';
    let tiltFrame = 0;
    const auth = window.RoutyAuth;
    const form = document.querySelector('#auth-form');
    const emailInput = document.querySelector('#auth-email');
    const passwordInput = document.querySelector('#auth-password');
    const submitButton = document.querySelector('#auth-submit');
    const authStatus = document.querySelector('#auth-status');
    let authMode = 'login';
    let returnRoute = '/workspace';
    let formVersion = 0;
    let selectedRegion = null;
    let regionVersion = 0;
    let regionPending = null;
    const client = auth.createClient({fetch: window.fetch.bind(window), onSessionEnd(reason) {
      resetJourney();
      cancelForm();
      renderAccount();
      if (reason === 'expired') {
        const route = normalizeRoute(window.location.hash);
        if (auth.isProtected(route)) returnRoute = route;
        selectAuthMode('login');
        setAuthStatus('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
        window.location.hash = '#/auth';
      }
    }});

    function setAuthStatus(message, error = false) {
      authStatus.textContent = message;
      authStatus.dataset.error = String(error);
    }
    function setBusy(busy) {
      form.setAttribute('aria-busy', String(busy));
      submitButton.disabled = busy;
      emailInput.readOnly = busy;
      passwordInput.readOnly = busy;
      submitButton.textContent = busy ? '처리 중…' : authMode === 'login' ? '로그인' : '회원가입';
    }
    function clearErrors() {
      for (const field of ['email', 'password']) {
        document.querySelector(`#auth-${field}`).removeAttribute('aria-invalid');
        const error = document.querySelector(`#${field}-error`);
        error.textContent = '';
        error.hidden = true;
      }
    }
    function cancelForm() {
      formVersion++;
      client.cancel();
      passwordInput.value = '';
      setBusy(false);
    }
    function selectAuthMode(mode) {
      cancelForm();
      authMode = mode === 'signup' ? 'signup' : 'login';
      clearErrors();
      setAuthStatus('');
      setBusy(false);
      passwordInput.autocomplete = authMode === 'signup' ? 'new-password' : 'current-password';
      document.querySelector('#auth-title').textContent = authMode === 'signup' ? '첫 여행을 시작해요' : '여행을 이어볼까요?';
      document.querySelector('#auth-description').textContent = authMode === 'signup' ? '이메일로 계정을 만들고 여행을 준비하세요.' : '로그인하고 나만의 여행을 시작하세요.';
      document.querySelectorAll('[data-auth-tab]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.authTab === authMode)));
    }
    function renderAccount() {
      const signedIn = client.isAuthenticated();
      document.querySelector('#login-link').hidden = signedIn;
      document.querySelector('#logout-button').hidden = !signedIn;
    }
    function resetJourney() {
      cancelRegionRequest();
      selectedRegion = null;
      currentStep = 0;
      currentState = 'initial';
      document.querySelectorAll('[data-state]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.state === 'initial')));
      if (cancelDialog.open) cancelDialog.close();
      renderStep(false);
      renderState();
      renderRegionSummary();
    }

    function cancelRegionRequest() {
      regionVersion++;
      regionPending?.abort();
      regionPending = null;
      setRegionBusy(false);
    }
    function setRegionBusy(busy) {
      document.querySelector('#region-search-submit').disabled = busy;
      document.querySelector('#region-ai-submit').disabled = busy;
      document.querySelector('#region-search-form').setAttribute('aria-busy', String(busy));
      document.querySelector('#region-ai-form').setAttribute('aria-busy', String(busy));
    }
    function setRegionStatus(kind, message, error = false) {
      const status = document.querySelector(`#region-${kind}-status`);
      status.textContent = message;
      status.dataset.error = String(error);
      if (error) status.focus({preventScroll: true});
    }
    function renderRegionSummary() {
      document.querySelector('#summary-region').textContent = selectedRegion?.name || '선택 전';
      document.querySelector('.summary-empty').hidden = Boolean(selectedRegion);
    }
    function selectRegion(region, source) {
      selectedRegion = Object.freeze({regionId: region.regionId, name: region.name, provinceName: region.provinceName || ''});
      document.querySelectorAll('[data-region-id]').forEach(button => {
        const selected = button.dataset.regionId === selectedRegion.regionId;
        button.textContent = selected ? '선택됨' : '이 지역 선택';
        button.setAttribute('aria-pressed', String(selected));
        button.closest('.region-result')?.classList.toggle('is-selected', selected);
      });
      renderRegionSummary();
      setRegionStatus(source, `${selectedRegion.name}을(를) 여행 지역으로 선택했습니다.`);
    }
    function renderRegions(kind, regions) {
      const list = document.querySelector(`#region-${kind}-results`);
      list.replaceChildren(...regions.map(region => {
        const item = document.createElement('li');
        item.className = 'region-result';
        const copy = document.createElement('div');
        const title = document.createElement('h3');
        title.textContent = region.name;
        const detail = document.createElement('p');
        detail.textContent = kind === 'ai' ? `${region.provinceName || '광역 지역'} · ${region.reason}` : (region.provinceName || '광역 지역');
        copy.append(title, detail);
        if (kind === 'search' && !region.selectable) {
          const badge = document.createElement('span');
          badge.className = 'region-filter-badge';
          badge.textContent = region.placeSearchFilterable ? '장소 검색 필터' : '최종 선택 불가';
          copy.append(badge);
        }
        item.append(copy);
        if (kind === 'ai' || region.selectable) {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'button button-ghost';
          button.dataset.regionId = region.regionId;
          button.setAttribute('aria-pressed', String(selectedRegion?.regionId === region.regionId));
          button.textContent = selectedRegion?.regionId === region.regionId ? '선택됨' : '이 지역 선택';
          button.addEventListener('click', () => selectRegion(region, kind));
          item.append(button);
        }
        return item;
      }));
    }
    async function requestRegions(kind, path, options) {
      cancelRegionRequest();
      const version = regionVersion;
      const controller = new AbortController();
      regionPending = controller;
      setRegionBusy(true);
      setRegionStatus(kind, kind === 'ai' ? '세 곳을 찾고 있습니다.' : '지역을 찾고 있습니다.');
      try {
        const response = await client.protectedRequest(path, {...options, signal: controller.signal});
        if (version !== regionVersion || response?.code === 'CANCELLED') return;
        if (!(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        if (version !== regionVersion) return;
        if (!response.ok) {
          const error = regionError(response.status, body);
          if (error.code !== 'AUTHENTICATION_REQUIRED') setRegionStatus(kind, error.message, true);
          return;
        }
        const regions = kind === 'ai' ? parseRegionRecommendations(body) : parseRegionSearch(body);
        if (!regions) {
          setRegionStatus(kind, REGION_MESSAGES[kind === 'ai' ? 'AI_RESPONSE_INVALID' : 'SERVER_ERROR'], true);
          return;
        }
        renderRegions(kind, regions);
        setRegionStatus(kind, regions.length ? `${regions.length}개의 지역을 찾았습니다.` : '검색 결과가 없습니다. 다른 지역 이름으로 검색해 보세요.');
      } catch (_) {
        if (version === regionVersion) setRegionStatus(kind, REGION_MESSAGES.NETWORK_ERROR, true);
      } finally {
        if (regionPending === controller) { regionPending = null; setRegionBusy(false); }
      }
    }
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (submitButton.disabled) return;
      clearErrors();
      setAuthStatus('처리 중입니다.');
      setBusy(true);
      const version = ++formVersion;
      const mode = authMode;
      const result = await client.submit(mode, emailInput.value, passwordInput.value);
      if (version !== formVersion) return;
      passwordInput.value = '';
      setBusy(false);
      if (result.ok && mode === 'signup') {
        selectAuthMode('login');
        setAuthStatus('회원가입이 완료되었습니다. 비밀번호를 다시 입력해 로그인해 주세요.');
        passwordInput.focus();
      } else if (result.ok) {
        resetJourney();
        emailInput.value = '';
        setAuthStatus('');
        renderAccount();
        window.location.hash = `#${returnRoute}`;
      } else if (result.code !== 'CANCELLED' && result.code !== 'BUSY') {
        setAuthStatus(result.message, true);
        let first;
        for (const [field, message] of Object.entries(result.fieldErrors || {})) {
          const input = document.querySelector(`#auth-${field}`);
          const error = document.querySelector(`#${field}-error`);
          input.setAttribute('aria-invalid', 'true');
          error.textContent = message;
          error.hidden = false;
          first ||= input;
        }
        (first || authStatus).focus();
      }
    });
    document.querySelector('#logout-button').addEventListener('click', () => {
      client.clear('logout');
      emailInput.value = '';
      returnRoute = '/workspace';
      window.location.hash = '#/';
    });
    window.addEventListener('pagehide', () => { client.clear('pagehide'); emailInput.value = ''; });
    window.addEventListener('pageshow', () => showRoute(false));
    document.addEventListener('visibilitychange', () => { if (!document.hidden) { client.isAuthenticated(); showRoute(false); } });

    function renderState() {
      const model = stateModel(currentState);
      if (currentState === 'loading') {
        stateStage.innerHTML = '<div class="stage-content"><div class="stage-lines" aria-hidden="true"><i></i><i></i><i></i></div><h3>불러오는 중</h3><p>현재 선택은 그대로 유지됩니다.</p></div>';
        return;
      }
      const errorClass = currentState === 'error' ? ' error' : '';
      stateStage.innerHTML = `<div class="stage-content${errorClass}"><span class="stage-symbol" aria-hidden="true">${model.symbol}</span><h3>${model.title}</h3><p>${model.description}</p></div>`;
    }

    function renderStep(focusHeading) {
      currentStep = clampStep(currentStep);
      stepList.replaceChildren(...STEPS.map(([label], index) => {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.step = String(index);
        if (index === currentStep) button.setAttribute('aria-current', 'step');
        const number = document.createElement('span');
        number.textContent = String(index + 1).padStart(2, '0');
        button.append(number, document.createTextNode(label));
        item.append(button);
        return item;
      }));
      const [, title, description] = STEPS[currentStep];
      stepTitle.textContent = title;
      stepDescription.textContent = description;
      stepCount.textContent = `STEP ${String(currentStep + 1).padStart(2, '0')} / ${String(STEPS.length).padStart(2, '0')}`;
      previousStep.disabled = currentStep === 0;
      nextStepButton.textContent = currentStep === STEPS.length - 1 ? '첫 구조로 돌아가기 ↺' : '다음 구조 보기 →';
      regionWorkspace.hidden = currentStep !== 0;
      futureStepPreview.hidden = currentStep === 0;
      document.querySelector('#step-badge').textContent = currentStep === 0 ? 'W1-01B · CONNECTED' : 'W1 · STRUCTURE';
      if (focusHeading) stepTitle.focus({preventScroll: true});
    }

    function updateNavigation(view) {
      const group = navGroupForView(view);
      document.querySelectorAll('[data-nav]').forEach(link => {
        if (link.dataset.nav === group) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
      });
    }

    function showRoute(shouldFocus) {
      let route = normalizeRoute(window.location.hash);
      if (auth.isProtected(route) && !client.isAuthenticated()) {
        returnRoute = route;
        window.history.replaceState(null, '', '#/auth');
        route = '/auth';
        if (!authStatus.textContent) setAuthStatus('로그인 후 계속할 수 있어요.');
      }
      if (route === '/auth' && client.isAuthenticated()) {
        route = '/workspace';
        window.history.replaceState(null, '', `#${route}`);
      }
      const view = viewForRoute(route);
      if (view !== 'auth') cancelForm();
      renderAccount();
      const update = () => {
        pages.forEach(page => { page.hidden = page.dataset.view !== view; });
        updateNavigation(view);
        document.body.dataset.view = view;
        document.title = view === 'landing' ? 'Routy — 여행의 흐름을 만들다' : `${document.querySelector(`[data-view="${view}"] h1`)?.textContent || 'Routy'} — Routy`;
      };
      // Commit the guard synchronously so a delayed transition cannot reveal a protected view.
      update();
      window.scrollTo({top: 0, behavior: 'instant'});
      if (shouldFocus) content.focus({preventScroll: true});
    }

    document.addEventListener('click', event => {
      const regionMethod = event.target.closest('[data-region-method]');
      if (regionMethod) {
        const method = regionMethod.dataset.regionMethod === 'ai' ? 'ai' : 'search';
        document.querySelectorAll('[data-region-method]').forEach(button => button.setAttribute('aria-selected', String(button.dataset.regionMethod === method)));
        document.querySelector('#region-search-panel').hidden = method !== 'search';
        document.querySelector('#region-ai-panel').hidden = method !== 'ai';
        document.querySelector(`#region-${method === 'ai' ? 'request' : 'query'}`).focus();
        return;
      }
      const stepButton = event.target.closest('[data-step]');
      if (stepButton) {
        cancelRegionRequest();
        currentStep = clampStep(stepButton.dataset.step);
        renderStep(true);
        return;
      }
      const stateButton = event.target.closest('[data-state]');
      if (stateButton) {
        currentState = Object.hasOwn(VIEW_STATES, stateButton.dataset.state) ? stateButton.dataset.state : 'initial';
        document.querySelectorAll('[data-state]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.state === currentState)));
        renderState();
        return;
      }
      const authTab = event.target.closest('[data-auth-tab]');
      if (authTab) {
        selectAuthMode(authTab.dataset.authTab);
        return;
      }
      if (event.target.closest('[data-action="cancel"]')) cancelDialog.showModal();
    });

    previousStep.addEventListener('click', () => { cancelRegionRequest(); currentStep = nextStep(currentStep, -1); renderStep(true); });
    nextStepButton.addEventListener('click', () => {
      cancelRegionRequest();
      currentStep = currentStep === STEPS.length - 1 ? 0 : nextStep(currentStep, 1);
      renderStep(true);
    });

    document.querySelector('#region-search-form').addEventListener('submit', event => {
      event.preventDefault();
      const query = document.querySelector('#region-query').value.trim();
      if (!query) { setRegionStatus('search', '검색할 지역 이름을 입력해 주세요.', true); return; }
      requestRegions('search', `/api/regions?query=${encodeURIComponent(query)}`, {headers: {Accept: 'application/json'}});
    });
    document.querySelector('#region-ai-form').addEventListener('submit', event => {
      event.preventDefault();
      const request = document.querySelector('#region-request').value.trim();
      if (!request || Array.from(request).length > 500) { setRegionStatus('ai', '원하는 여행을 1~500자로 입력해 주세요.', true); return; }
      requestRegions('ai', '/api/ai/regions/recommend', {method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()}, body: JSON.stringify({request})});
    });
    document.querySelector('#region-request').addEventListener('input', event => { document.querySelector('#region-request-count').textContent = String(Array.from(event.target.value).length); });

    cancelDialog.addEventListener('close', () => {
      if (cancelDialog.returnValue === 'leave') window.location.hash = '#/';
    });

    const motionToggle = document.querySelector('.motion-toggle');
    motionToggle.addEventListener('click', () => {
      const off = document.body.classList.toggle('motion-off');
      motionToggle.setAttribute('aria-pressed', String(off));
      document.querySelector('.motion-label').textContent = off ? '모션 켜기' : '모션 줄이기';
    });

    const routeArt = document.querySelector('[data-tilt]');
    routeArt.addEventListener('pointermove', event => {
      if (!hoverFine.matches || reducedMotion.matches || document.body.classList.contains('motion-off')) return;
      if (tiltFrame) window.cancelAnimationFrame(tiltFrame);
      const rect = routeArt.getBoundingClientRect();
      tiltFrame = window.requestAnimationFrame(() => {
        tiltFrame = 0;
        routeArt.style.setProperty('--ry', `${(((event.clientX - rect.left) / rect.width) - .5) * 24}deg`);
        routeArt.style.setProperty('--rx', `${-(((event.clientY - rect.top) / rect.height) - .5) * 18}deg`);
      });
    });
    routeArt.addEventListener('pointerleave', () => {
      if (tiltFrame) window.cancelAnimationFrame(tiltFrame);
      tiltFrame = 0;
      routeArt.style.setProperty('--rx', '0deg');
      routeArt.style.setProperty('--ry', '0deg');
    });

    window.addEventListener('hashchange', () => showRoute(true));
    renderStep(false);
    renderState();
    showRoute(false);
  }

  return Object.freeze({ROUTES, STEPS, VIEW_STATES, REGION_MESSAGES, normalizeRoute, viewForRoute, navGroupForView, clampStep, nextStep, stateModel, validRegion, parseRegionSearch, parseRegionRecommendations, regionError, mount});
});
