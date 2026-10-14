'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyTripsWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function route(hash) {
    const [path, query = ''] = String(hash).replace(/^#/, '').split('?');
    const params = new URLSearchParams(query);
    const id = params.get('id');
    return {path, id: /^[1-9]\d*$/.test(id || '') && Number.isSafeInteger(Number(id)) ? Number(id) : null,
      token: /^[A-Za-z0-9_-]{43}$/.test(params.get('token') || '') ? params.get('token') : null};
  }
  function editablePlaces(plan) {
    const places = new Map();
    for (const place of [plan.hotel, ...plan.days.flatMap(day => day.items)]) {
      if (Number.isSafeInteger(place?.planPlaceId) && place.planPlaceId > 0) places.set(place.planPlaceId, place);
    }
    return [...places.values()];
  }
  function patch(plan, title, edits) {
    if (!title.trim() || title.trim().length > 100) return null;
    const places = new Map(editablePlaces(plan).map(place => [place.planPlaceId, place]));
    const changed = [];
    for (const edit of edits) {
      const original = places.get(edit.planPlaceId);
      if (!original || !edit.displayName.trim() || edit.displayName.trim().length > 50 || edit.memo.length > 1000) return null;
      const value = {planPlaceId: edit.planPlaceId};
      if (edit.displayName.trim() !== original.displayName) value.displayName = edit.displayName.trim();
      if (edit.memo !== (original.memo || '')) value.memo = edit.memo || null;
      if (Object.keys(value).length > 1) changed.push(value);
    }
    return {...(title.trim() !== plan.title ? {title: title.trim()} : {}), ...(changed.length ? {placeEdits: changed} : {})};
  }
  function errorText(status, body) {
    if (status === 413) return '요청 크기가 너무 큽니다. 제목·이름·메모의 입력 길이를 줄인 뒤 다시 저장해 주세요. 편집 내용은 유지됩니다.';
    if (status === 403) return '이 일정에 접근할 권한이 없습니다. 내 여행에서 자신의 일정을 선택해 주세요.';
    if (status === 404) return '일정을 찾을 수 없습니다. 공유 링크는 만료되거나 재발급·삭제되었을 수 있습니다.';
    if (status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
    if (status === 429) return `호출 한도에 도달했습니다. ${Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0 ? `${body.retryAfterSeconds}초 뒤` : '잠시 뒤'} 다시 시도해 주세요.`;
    if (status === 400) return '제목·장소 이름·메모의 입력 길이를 확인해 주세요.';
    return '요청을 처리하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.';
  }
  function mount(document, window, client, download) {
    const $ = selector => document.querySelector(selector);
    const node = (tag, text) => { const value = document.createElement(tag); if (text != null) value.textContent = text; return value; };
    let version = 0, pending = null, plan = null, page = 0, hasNext = false;
    let active = '', created = null;
    const status = message => { $(active === '/shared' ? '#shared-status' : active === '/trips' ? '#trips-status' : '#trip-status').textContent = message; };
    function clear() {
      version++; pending?.abort(); pending = null; plan = null; created = null;
      page = 0; hasNext = false;
      $('#trip-editor').replaceChildren(); $('#trip-editor').hidden = true;
      $('#trip-editor').onsubmit = null;
      $('#trip-actions').hidden = true; $('#share-result').replaceChildren(); $('#trip-delete-confirm').hidden = true;
      $('#trips-list').replaceChildren(); $('#shared-document').shadowRoot?.replaceChildren();
      $('#trips-empty').hidden = true; $('#trips-page').textContent = '';
      for (const selector of ['#trip-status', '#trips-status', '#shared-status']) $(selector).textContent = '';
      window.RoutyItineraryDownload.clearCompleted(document); download.clear();
      busy(false);
    }
    function busy(value) {
      for (const button of document.querySelectorAll('[data-trip-action], #trip-editor button')) button.disabled = value;
      for (const field of document.querySelectorAll('#trip-editor input, #trip-editor textarea')) field.readOnly = value;
      $('#trips-previous').disabled = value || page === 0;
      $('#trips-next').disabled = value || !hasNext;
    }
    async function request(path, options, apply) {
      if (pending) return;
      const id = ++version, controller = new AbortController(); pending = controller; busy(true); status('불러오는 중입니다.');
      try {
        const response = active === '/shared'
          ? await window.fetch(path, {...options, signal: controller.signal, credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer'})
          : await client.protectedRequest(path, {...options, signal: controller.signal});
        if (id !== version || !(response instanceof window.Response)) return;
        const body = response.status === 204 ? null : await response.json().catch(() => null);
        if (id !== version) return;
        if (!response.ok) { status(errorText(response.status, body)); return; }
        apply(body, response.status); status('');
      } catch (_) { if (id === version) status('요청을 처리하지 못했습니다. 연결을 확인한 뒤 다시 시도해 주세요. 변경 요청이었다면 새로 조회하여 저장 상태를 확인해 주세요.'); }
      finally { if (id === version) { pending = null; busy(false); } }
    }
    function renderPlan(body) {
      if (!window.RoutyReviewWorkspace.validPlan(body)) throw new Error('Invalid plan');
      plan = body;
      window.RoutyItineraryDownload.renderCompleted(document, body); download.setPlan(body);
      $('#trip-actions').hidden = false; $('#trip-editor').hidden = true; $('#trip-editor').replaceChildren();
      $('#trip-editor').onsubmit = null;
    }
    function loadList(target) {
      request(`/api/travel-plans?page=${target}&size=20`, {}, body => {
        if (!Array.isArray(body?.items) || body.page !== target || body.size !== 20 || typeof body.hasNext !== 'boolean'
          || !body.items.every(item => Number.isSafeInteger(item.travelPlanId) && item.travelPlanId > 0 && typeof item.title === 'string')) throw new Error('Invalid list');
        page = target; hasNext = body.hasNext;
        $('#trips-page').textContent = `${page + 1}페이지`;
        $('#trips-list').replaceChildren(...body.items.map((item, index) => {
          const row = node('li'); row.className = 'region-result trip-card';
          const number = node('span', String(page * 20 + index + 1).padStart(2, '0')); number.className = 'trip-card-number'; number.setAttribute('aria-hidden', 'true');
          const region = node('span', item.region.displayName); region.className = 'trip-card-region';
          const copy = node('div'); copy.append(region, node('h2', item.title), node('p', `${item.startDate} – ${item.endDate}`));
          const link = node('a', '일정 보기'); link.href = `#/trip?id=${item.travelPlanId}`; link.className = 'button button-ghost';
          link.setAttribute('aria-label', `${item.title} 일정 보기`);
          row.append(number, copy, link); return row;
        }));
        $('#trips-empty').hidden = body.items.length !== 0;
      });
    }
    function show(hash) {
      const saved = created; clear(); const current = route(hash); active = current.path.replace(/\/+$/, '');
      if (active === '/trips') loadList(0);
      if (active === '/trip') {
        if (!current.id) { status('내 여행에서 확인할 일정을 선택해 주세요.'); return; }
        if (saved?.travelPlanId === current.id) { renderPlan(saved); status(''); busy(false); return; }
        request(`/api/travel-plans/${current.id}`, {}, renderPlan);
      }
      if (active === '/shared') {
        if (!current.token) { status('공유 링크가 올바르지 않습니다. 새 링크를 확인해 주세요.'); return; }
        request(`/api/shared/travel-plans/${current.token}`, {}, body => {
          if (!window.RoutyReviewWorkspace.validPlan(body)) throw new Error('Invalid plan');
          const page = window.RoutyItineraryDownload.buildDocument(document, body);
          const host = $('#shared-document'), root = host.shadowRoot || host.attachShadow({mode: 'open'});
          root.replaceChildren(document.importNode(page.querySelector('style'), true), document.importNode(page.querySelector('.itinerary-page'), true));
        });
      }
    }
    function edit() {
      if (!plan || pending) return;
      const form = $('#trip-editor'); form.replaceChildren(); form.hidden = false;
      function input(parent, label, value, max, multiline = false) {
        const wrapper = node('label', label), field = node(multiline ? 'textarea' : 'input');
        field.value = value || ''; field.maxLength = max; if (!multiline) field.required = true;
        wrapper.append(field); parent.append(wrapper); return field;
      }
      const title = input(form, '여행 제목', plan.title, 100), fields = [];
      for (const place of editablePlaces(plan)) {
        const group = node('fieldset'); group.className = 'selected-place'; group.append(node('legend', place.displayName));
        fields.push({planPlaceId: place.planPlaceId, name: input(group, '장소 이름', place.displayName, 50), memo: input(group, '메모', place.memo, 1000, true)}); form.append(group);
      }
      const save = node('button', '변경 저장'); save.type = 'submit'; save.className = 'button button-primary';
      const cancel = node('button', '편집 취소'); cancel.type = 'button'; cancel.className = 'button button-ghost';
      cancel.addEventListener('click', () => { if (!pending) { form.hidden = true; $('#trip-edit').focus(); } });
      form.append(save, cancel); title.focus();
      form.onsubmit = event => {
        event.preventDefault(); const body = patch(plan, title.value, fields.map(field => ({planPlaceId: field.planPlaceId, displayName: field.name.value, memo: field.memo.value})));
        if (!body) { status('제목·이름·메모 입력을 확인해 주세요.'); return; }
        if (!Object.keys(body).length) { status('변경한 내용이 없습니다.'); return; }
        request(`/api/travel-plans/${plan.travelPlanId}`, {method: 'PATCH', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)}, renderPlan);
      };
    }
    $('#trips-previous').addEventListener('click', () => { if (page > 0) loadList(page - 1); });
    $('#trips-next').addEventListener('click', () => { if (hasNext) loadList(page + 1); });
    $('#trips-reload').addEventListener('click', () => loadList(page));
    $('#trip-reload').addEventListener('click', () => show(window.location.hash));
    $('#shared-reload').addEventListener('click', () => { if (!pending) show(window.location.hash); });
    $('#trip-edit').addEventListener('click', edit);
    $('#trip-delete').addEventListener('click', () => { $('#trip-delete-confirm').hidden = false; $('#trip-delete-cancel').focus(); });
    $('#trip-delete-cancel').addEventListener('click', () => { $('#trip-delete-confirm').hidden = true; $('#trip-delete').focus(); });
    $('#trip-delete-yes').addEventListener('click', () => {
      if (plan) request(`/api/travel-plans/${plan.travelPlanId}`, {method: 'DELETE'}, (_, status) => {
        if (status !== 204) throw new Error('Invalid deletion');
        clear(); window.location.hash = '#/trips';
      });
    });
    $('#trip-share').addEventListener('click', () => {
      if (plan) request(`/api/travel-plans/${plan.travelPlanId}/shares`, {method: 'POST'}, (body, code) => {
        if (code !== 201 || !/^[A-Za-z0-9_-]{43}$/.test(body?.shareToken || '') || !Number.isFinite(Date.parse(body.expiresAt))) throw new Error('Invalid share');
        const link = node('a', '공유 일정 열기');
        link.href = `${window.location.origin}${window.location.pathname}#/shared?token=${body.shareToken}`;
        link.rel = 'noreferrer noopener'; link.referrerPolicy = 'no-referrer';
        const field = node('input'); field.readOnly = true; field.value = link.href; field.setAttribute('aria-label', '복사할 공유 링크');
        $('#share-result').replaceChildren(node('p', `이 링크는 ${body.expiresAt}까지 유효합니다. 재발급하면 이전 링크는 만료됩니다.`), field, link);
      });
    });
    window.addEventListener('pagehide', clear);
    return {show, clear, created(body) { created = body; }};
  }
  return Object.freeze({route, editablePlaces, patch, errorText, mount});
});
