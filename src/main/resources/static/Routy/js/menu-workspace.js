'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyMenuWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const MESSAGES = Object.freeze({
    VALIDATION_FAILED: '입력 내용을 확인해 주세요. 기존 메뉴는 그대로 유지됩니다.',
    AUTHENTICATION_REQUIRED: '로그인 시간이 만료되었습니다. 다시 로그인해 주세요.',
    REQUEST_IN_PROGRESS: '같은 요청을 처리 중입니다. 잠시 뒤 새로 요청해 주세요.',
    REQUEST_ALREADY_COMPLETED: '이미 처리된 요청입니다. 새 분석을 요청해 주세요.',
    RATE_LIMIT_EXCEEDED: '메뉴 분석 한도에 도달했습니다.',
    AI_UNAVAILABLE: '메뉴 분석을 잠시 사용할 수 없습니다. 직접 메뉴를 입력해 주세요.',
    AI_RESPONSE_INVALID: '분석 결과를 확인할 수 없습니다. 직접 메뉴를 입력해 주세요.',
    NETWORK_ERROR: '연결을 확인하고 다시 요청하거나 직접 메뉴를 입력해 주세요.',
    SERVER_ERROR: '메뉴 분석을 완료하지 못했습니다. 직접 메뉴를 입력해 주세요.'
  });

  function errorFor(status, body) {
    const code = Object.hasOwn(MESSAGES, body?.code) ? body.code : status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR';
    const seconds = code === 'RATE_LIMIT_EXCEEDED' && Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0
      ? ` ${body.retryAfterSeconds}초 뒤 다시 시도해 주세요.` : '';
    return MESSAGES[code] + seconds;
  }

  function validMenus(body, attractionIds) {
    if (!Array.isArray(body?.menus) || body.menus.length < 1 || body.menus.length > 5) return null;
    const ids = new Set(attractionIds);
    const names = new Set();
    const menus = [];
    for (const item of body.menus) {
      if (!item || typeof item.name !== 'string' || typeof item.searchQuery !== 'string' || typeof item.reason !== 'string'
          || item.targetClientPlaceId != null && (typeof item.targetClientPlaceId !== 'string' || !ids.has(item.targetClientPlaceId))) return null;
      const name = item.name.trim(), searchQuery = item.searchQuery.trim(), reason = item.reason.trim();
      const key = name.toLowerCase();
      if (!name || Array.from(name).length > 50 || !searchQuery || !reason || names.has(key)) return null;
      names.add(key);
      menus.push({name, searchQuery, reason, targetClientPlaceId: item.targetClientPlaceId || null});
    }
    return menus;
  }

  function mount(document, window, client) {
    const $ = selector => document.querySelector(selector);
    const section = $('#menu-workspace');
    let regionId = null;
    let attractions = [];
    let contextSignature = '';
    let menus = [];
    let confirmed = null;
    let pending = null;
    let requestVersion = 0;

    function status(message, error = false) {
      const target = $('#menu-status');
      target.textContent = message;
      target.dataset.error = String(error);
      if (error) target.focus({preventScroll: true});
    }
    function cancelRequest() { requestVersion++; pending?.abort(); pending = null; $('#menu-submit').disabled = false; }
    function updateConfirmation() {
      $('#menu-confirmed-status').textContent = confirmed
        ? `${confirmed.length}개 메뉴를 확정했습니다. 다음 단계로 이동할 수 있습니다.`
        : menus.length ? `${menus.length}개 메뉴를 검토 중입니다. 수정이 끝나면 메뉴를 확정해 주세요.` : '분석을 요청하거나 메뉴를 직접 추가해 주세요.';
      $('#menu-confirm').disabled = menus.length === 0;
    }
    function dirty() { if (pending) cancelRequest(); confirmed = null; updateConfirmation(); }
    function targetSelect(value, onChange) {
      const select = document.createElement('select');
      const none = document.createElement('option'); none.value = ''; none.textContent = '대상 관광지 없음'; select.append(none);
      for (const attraction of attractions) {
        const option = document.createElement('option'); option.value = attraction.clientPlaceId;
        option.textContent = attraction.displayName; select.append(option);
      }
      select.value = value || '';
      select.addEventListener('change', () => onChange(select.value || null));
      return select;
    }
    function render() {
      const list = $('#menu-draft'); list.replaceChildren();
      menus.forEach((menu, index) => {
        const card = document.createElement('li'); card.className = 'menu-card';
        const heading = document.createElement('h3'); heading.textContent = `메뉴 ${index + 1}`;
        card.append(heading);
        for (const [labelText, key] of [['메뉴 이름', 'name'], ['카카오 검색어', 'searchQuery'], ['선택 이유', 'reason']]) {
          const label = document.createElement('label'); label.textContent = labelText;
          const input = document.createElement('input'); input.type = 'text'; input.value = menu[key];
          if (key === 'name') input.maxLength = 50;
          input.addEventListener('input', () => { menu[key] = input.value; dirty(); });
          label.append(input); card.append(label);
        }
        const target = document.createElement('label'); target.textContent = '기준 관광지';
        target.append(targetSelect(menu.targetClientPlaceId, value => { menu.targetClientPlaceId = value; dirty(); }));
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'button button-ghost';
        remove.textContent = '이 메뉴 삭제';
        remove.addEventListener('click', () => { menus.splice(index, 1); dirty(); render(); });
        card.append(target, remove); list.append(card);
      });
      $('#menu-direct-target').replaceChildren(...Array.from(targetSelect(null, () => {}).options).map(option => option.cloneNode(true)));
      updateConfirmation();
    }
    function reset() {
      cancelRequest(); regionId = null; attractions = []; contextSignature = ''; menus = []; confirmed = null;
      $('#menu-request').value = ''; $('#menu-direct-name').value = ''; $('#menu-direct-query').value = '';
      status(''); render();
    }
    function syncContext(nextRegionId, nextAttractions) {
      const next = nextAttractions.map(item => ({clientPlaceId: item.clientPlaceId, displayName: item.displayName}));
      const signature = JSON.stringify(next);
      if (regionId !== nextRegionId) {
        reset(); regionId = nextRegionId; attractions = next; contextSignature = signature; render(); return;
      }
      if (signature !== contextSignature) {
        cancelRequest(); attractions = next; contextSignature = signature;
        const ids = new Set(next.map(item => item.clientPlaceId));
        menus = menus.map(menu => ({...menu, targetClientPlaceId: ids.has(menu.targetClientPlaceId) ? menu.targetClientPlaceId : null}));
        confirmed = null; render(); status('선택한 관광지가 바뀌어 메뉴의 기준 관광지를 다시 확인해 주세요.');
      }
    }
    function showStep(step, nextRegionId, nextAttractions) {
      section.hidden = step !== 4;
      if (step === 4) syncContext(nextRegionId, nextAttractions);
      else cancelRequest();
    }
    async function analyze(event) {
      event.preventDefault();
      if (pending) return;
      const request = $('#menu-request').value.trim();
      if (!regionId || !request || Array.from(request).length > 200) { status('먹고 싶은 음식이나 분위기를 1~200자로 입력해 주세요.', true); return; }
      const version = ++requestVersion, controller = new AbortController(); pending = controller;
      $('#menu-submit').disabled = true; status('메뉴를 분석하고 있습니다. 기존 메뉴는 유지됩니다.');
      try {
        const response = await client.protectedRequest('/api/ai/menus/analyze', {
          method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()},
          body: JSON.stringify({regionId, request, attractions}), signal: controller.signal
        });
        if (version !== requestVersion || !(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        if (version !== requestVersion) return;
        if (response.status !== 200) { status(response.ok ? MESSAGES.SERVER_ERROR : errorFor(response.status, body), true); return; }
        const next = validMenus(body, attractions.map(item => item.clientPlaceId));
        if (!next) { status(MESSAGES.AI_RESPONSE_INVALID, true); return; }
        menus = next; confirmed = null; render(); status('분석한 메뉴를 검토하고 수정한 뒤 확정해 주세요.');
        $('#menu-draft .menu-card input')?.focus();
      } catch (_) { if (version === requestVersion) status(MESSAGES.NETWORK_ERROR, true); }
      finally { if (pending === controller) { pending = null; $('#menu-submit').disabled = false; } }
    }
    function addDirect(event) {
      event.preventDefault();
      const name = $('#menu-direct-name').value.trim(), searchQuery = $('#menu-direct-query').value.trim();
      if (menus.length >= 5) { status('메뉴는 최대 5개까지 선택할 수 있습니다.', true); return; }
      if (!name || Array.from(name).length > 50 || !searchQuery) { status('메뉴 이름은 1~50자, 검색어는 빈칸 없이 입력해 주세요.', true); return; }
      if (menus.some(menu => menu.name.trim().toLowerCase() === name.toLowerCase())) { status('같은 메뉴 이름은 한 번만 사용할 수 있습니다.', true); return; }
      menus.push({name, searchQuery, reason: '직접 입력', targetClientPlaceId: $('#menu-direct-target').value || null});
      $('#menu-direct-name').value = ''; $('#menu-direct-query').value = '';
      dirty(); render(); status('메뉴를 추가했습니다.');
    }
    function confirm() {
      if (pending) cancelRequest();
      const checked = validMenus({menus}, attractions.map(item => item.clientPlaceId));
      if (!checked) { status('메뉴 1~5개의 이름·검색어·이유와 중복 여부를 확인해 주세요.', true); return; }
      confirmed = checked; updateConfirmation(); status('메뉴를 확정했습니다.');
    }
    function canLeave(nextRegionId, nextAttractions) {
      syncContext(nextRegionId, nextAttractions);
      return confirmed ? null : '메뉴를 1~5개 검토하고 확정해 주세요.';
    }
    function showError(message) { status(message, true); }
    $('#menu-analysis-form').addEventListener('submit', analyze);
    $('#menu-request').addEventListener('input', () => { if (pending) { cancelRequest(); status('요청 내용이 바뀌어 이전 분석을 취소했습니다.'); } });
    $('#menu-direct-form').addEventListener('submit', addDirect);
    $('#menu-confirm').addEventListener('click', confirm);
    render();
    return {showStep, canLeave, showError, confirmedMenus: () => confirmed?.map(item => ({...item})) || [], reset};
  }

  return Object.freeze({MESSAGES, errorFor, validMenus, mount});
});
