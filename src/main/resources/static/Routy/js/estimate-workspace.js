'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyEstimateWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const iso = /^\d{4}-\d{2}-\d{2}$/;
  const clock = /^([01]\d|2[0-3]):[0-5]\d$/;
  const dateAt = (start, offset) => {
    const timestamp = Date.parse(`${start}T00:00:00Z`);
    return Number.isFinite(timestamp) ? new Date(timestamp + offset * 86400000).toISOString().slice(0, 10) : null;
  };

  function buildRequest(input) {
    const {regionId, summary, selected, places, foods, startDate, times} = input;
    const count = summary?.days;
    if (!iso.test(startDate || '') || dateAt(startDate, 0) !== startDate || !Number.isInteger(count) || count < 1 || count > 7) return {error: '여행 시작 날짜와 1~7일 기간을 확인해 주세요.'};
    if (!Array.isArray(times) || times.length !== count || times.some(({start, end}) => !clock.test(start) || !clock.test(end) || start >= end)) return {error: '각 날짜의 활동 시작·종료 시각을 확인해 주세요. 시작은 종료보다 빨라야 합니다.'};
    if (!regionId || !['CAR', 'PUBLIC_TRANSIT'].includes(summary.travelMode) || !selected?.startBoundary?.selectionToken || !selected?.endBoundary?.selectionToken || (count > 1 && !selected?.hotel?.selectionToken)) return {error: '지역과 시작·종료 장소, 필요한 숙소를 다시 확인해 주세요.'};
    if (!Array.isArray(places) || !places.length || places.length > count * 5 || places.some(place => !place.selectionToken || !place.clientPlaceId || !place.displayName || !Number.isInteger(place.stayMinutes) || place.stayMinutes < 30 || place.stayMinutes > 480 || place.stayMinutes % 10)) return {error: '관광지 이름과 체류 시간을 다시 확인해 주세요.'};
    if (places.some(place => place.day !== null || place.order !== null)) {
      const grouped = new Map();
      for (const place of places) {
        if (!iso.test(place.day || '') || !Number.isInteger(place.order) || place.order < 1 || place.order > 5 || place.day < startDate || place.day > dateAt(startDate, count - 1)) return {error: '관광지 날짜와 방문 순서를 다시 확인해 주세요.'};
        const orders = grouped.get(place.day) || []; orders.push(place.order); grouped.set(place.day, orders);
      }
      if ([...grouped.values()].some(orders => orders.length > 5 || orders.sort((a, b) => a - b).some((order, index) => order !== index + 1))) return {error: '각 날짜의 방문 순서를 1부터 빠짐없이 지정해 주세요. 하루 관광지는 최대 5곳입니다.'};
    }
    if (!Array.isArray(foods) || foods.length < 1 || foods.length > 5 || foods.some(name => !name || name.length > 50) || new Set(foods).size !== foods.length) return {error: '확정한 메뉴 1~5개를 다시 확인해 주세요.'};
    return {value: {regionId, travelMode: summary.travelMode, startDate, endDate: dateAt(startDate, count - 1),
      startBoundarySelectionToken: selected.startBoundary.selectionToken, endBoundarySelectionToken: selected.endBoundary.selectionToken,
      days: times.map(({start, end}, index) => ({date: dateAt(startDate, index), activityStartTime: start, activityEndTime: end})),
      places, hotelSelectionToken: count === 1 ? null : selected.hotel.selectionToken, mealTravelBufferMinutes: 15, foods}};
  }

  function validResult(body, request) {
    if (body?.routeVerified !== false || !Array.isArray(body.days) || body.days.length !== request.days.length) return false;
    const ids = new Set(request.places.map(place => place.clientPlaceId));
    return body.days.every((day, index) => day?.date === request.days[index].date && Array.isArray(day.items) && day.items.every(item =>
      Number.isInteger(item.order) && item.order > 0 && ['VISIT', 'MOVE', 'MEAL'].includes(item.type) && clock.test(item.startTime) && clock.test(item.endTime) &&
      (item.type !== 'VISIT' || (ids.has(item.clientPlaceId) && typeof item.displayName === 'string'))));
  }

  function errorText(response, body) {
    if (body?.code === 'PLAN_CAPACITY_EXCEEDED') {
      const details = body.details;
      return iso.test(details?.date || '') && clock.test(details?.plannedEndTime) && clock.test(details?.allowedEndTime) && Number.isInteger(details?.exceededMinutes)
        ? `${details.date} 일정이 ${details.exceededMinutes}분 초과합니다. 종료 시각이나 관광지 체류 시간·날짜 배치를 직접 조정해 다시 계산해 주세요.`
        : '일정이 허용 시간을 초과합니다. 활동 시간이나 관광지 선택을 직접 조정해 주세요.';
    }
    if (body?.code === 'VALIDATION_FAILED') return '입력이나 장소 선택 정보가 유효하지 않습니다. 날짜·시간을 확인하고 만료된 장소는 다시 선택해 주세요.';
    if (response.status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
    if (response.status === 429) return `호출 한도에 도달했습니다.${Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0 ? ` ${body.retryAfterSeconds}초 뒤` : ' 잠시 뒤'} 다시 시도해 주세요.`;
    return '추정 일정을 계산하지 못했습니다. 입력을 유지한 채 다시 시도해 주세요.';
  }

  function mount(document, window, client, context) {
    const $ = selector => document.querySelector(selector);
    const section = $('#estimate-workspace'), dateInput = $('#estimate-start-date'), dayFields = $('#estimate-day-fields');
    const manual = $('#estimate-manual'), placement = $('#estimate-placement');
    const result = $('#estimate-result'), status = $('#estimate-status'), submit = $('#estimate-submit');
    let pending = null, version = 0, lastFingerprint = null, lastResult = null;
    function setStatus(message, error = false) { status.textContent = message; status.dataset.error = String(error); if (error) status.focus({preventScroll: true}); else if (document.activeElement === status) status.blur(); }
    function cancel() { version++; pending?.abort(); pending = null; submit.disabled = false; }
    function clearResult() { result.replaceChildren(); lastFingerprint = null; lastResult = null; }
    function reset() { cancel(); clearResult(); dateInput.value = ''; manual.checked = false; placement.replaceChildren(); placement.hidden = true; dayFields.replaceChildren(); setStatus(''); }
    function renderDays() {
      const count = context().summary?.days || 1;
      const old = [...dayFields.querySelectorAll('.estimate-day')].map(row => ({start: row.querySelector('[data-time="start"]').value, end: row.querySelector('[data-time="end"]').value}));
      dayFields.replaceChildren();
      for (let index = 0; index < count; index++) {
        const row = document.createElement('div'); row.className = 'estimate-day';
        const title = document.createElement('strong'); title.textContent = dateInput.value && iso.test(dateInput.value) ? dateAt(dateInput.value, index) : `${index + 1}일차`;
        row.append(title);
        for (const [kind, label, fallback] of [['start', '시작', '09:00'], ['end', '종료', '20:00']]) {
          const field = document.createElement('label'); field.textContent = `활동 ${label} 시각`;
          const input = document.createElement('input'); input.type = 'time'; input.dataset.time = kind; input.value = old[index]?.[kind] || fallback;
          field.append(input); row.append(field);
        }
        dayFields.append(row);
      }
    }
    function currentRequest() {
      const data = context();
      if (manual.checked) data.places = data.places.map(place => {
        const row = [...placement.querySelectorAll('[data-place-id]')].find(item => item.dataset.placeId === place.clientPlaceId);
        return {...place, day: row?.querySelector('select')?.value || null, order: Number(row?.querySelector('input')?.value)};
      });
      return buildRequest({...data, startDate: dateInput.value, times: [...dayFields.querySelectorAll('.estimate-day')].map(row => ({start: row.querySelector('[data-time="start"]').value, end: row.querySelector('[data-time="end"]').value}))});
    }
    function renderPlacement() {
      const old = new Map([...placement.querySelectorAll('[data-place-id]')].map(row => [row.dataset.placeId, {day: row.querySelector('select').value, order: row.querySelector('input').value}]));
      placement.replaceChildren(); placement.hidden = !manual.checked;
      if (!manual.checked || !dateAt(dateInput.value, 0)) return;
      const count = context().summary.days;
      context().places.forEach((place, index) => {
        const row = document.createElement('div'); row.className = 'estimate-day'; row.dataset.placeId = place.clientPlaceId;
        const title = document.createElement('strong'); title.textContent = place.displayName;
        const dateLabel = document.createElement('label'); dateLabel.textContent = '날짜';
        const select = document.createElement('select');
        for (let day = 0; day < count; day++) { const option = document.createElement('option'); option.value = dateAt(dateInput.value, day); option.textContent = option.value; select.append(option); }
        const priorDay = old.get(place.clientPlaceId)?.day;
        select.value = [...select.options].some(option => option.value === priorDay) ? priorDay : dateAt(dateInput.value, Math.min(Math.floor(index / 5), count - 1)); dateLabel.append(select);
        const orderLabel = document.createElement('label'); orderLabel.textContent = '방문 순서';
        const order = document.createElement('input'); order.type = 'number'; order.min = '1'; order.max = '5'; order.step = '1'; order.value = old.get(place.clientPlaceId)?.order || String(index % 5 + 1); orderLabel.append(order);
        row.append(title, dateLabel, orderLabel); placement.append(row);
      });
    }
    function showStep(step) {
      section.hidden = step !== 5;
      if (step !== 5) { cancel(); return; }
      if (dayFields.children.length !== context().summary.days) renderDays();
      if (manual.checked) renderPlacement();
      const request = currentRequest();
      if (lastFingerprint && JSON.stringify(request.value) !== lastFingerprint) clearResult();
    }
    function renderResult(body) {
      result.replaceChildren();
      for (const day of body.days) {
        const group = document.createElement('section'), title = document.createElement('h3'), list = document.createElement('ol');
        group.className = 'estimate-result-day';
        list.className = 'estimate-timeline';
        title.textContent = day.date; group.append(title);
        for (const item of day.items) {
          const entry = document.createElement('li'), time = document.createElement('time'), label = document.createElement('strong');
          entry.className = `estimate-${item.type.toLowerCase()}`;
          time.textContent = `${item.startTime}–${item.endTime}`;
          label.textContent = item.type === 'VISIT' ? item.displayName : item.type === 'MOVE' ? `이동 · 예상 ${item.estimatedMinutes}분` : '식사';
          entry.append(time, label); list.append(entry);
        }
        group.append(list); result.append(group);
      }
    }
    async function estimate(event) {
      event.preventDefault(); if (pending) return;
      const built = currentRequest();
      if (built.error) { setStatus(built.error, true); return; }
      const fingerprint = JSON.stringify(built.value), id = ++version, controller = new AbortController(); pending = controller;
      submit.disabled = true; setStatus('추정 일정을 계산하고 있습니다.');
      try {
        const response = await client.protectedRequest('/api/travel-plans/estimate', {method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json'}, body: fingerprint, signal: controller.signal});
        if (id !== version || !(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        if (id !== version || currentRequest().error || JSON.stringify(currentRequest().value) !== fingerprint) return;
        if (!response.ok) { clearResult(); setStatus(errorText(response, body), true); return; }
        if (!validResult(body, built.value)) { clearResult(); setStatus('일정 응답을 확인할 수 없습니다. 다시 계산해 주세요.', true); return; }
        renderResult(body); lastFingerprint = fingerprint; lastResult = body; setStatus('추정 일정입니다. 실제 경로 검증 전이며 아직 저장되지 않았어요.');
      } catch (_) { if (id === version) setStatus('연결을 확인하고 다시 계산해 주세요. 입력은 유지됩니다.', true); }
      finally { if (pending === controller) { pending = null; submit.disabled = false; } }
    }
    $('#estimate-form').addEventListener('submit', estimate);
    dateInput.addEventListener('change', () => { renderDays(); cancel(); clearResult(); });
    dateInput.addEventListener('change', renderPlacement);
    manual.addEventListener('change', () => { renderPlacement(); cancel(); clearResult(); });
    placement.addEventListener('change', () => { cancel(); clearResult(); });
    dayFields.addEventListener('change', () => { cancel(); clearResult(); });
    window.addEventListener('pagehide', reset);
    renderDays();
    function confirmedEstimate() {
      const built = currentRequest();
      if (!lastResult || built.error || JSON.stringify(built.value) !== lastFingerprint) return null;
      const positions = new Map();
      const slots = [];
      for (const day of lastResult.days) {
        let visitOrder = 0;
        for (const item of day.items) {
          if (item.type === 'VISIT') {
            if (positions.has(item.clientPlaceId)) return null;
            positions.set(item.clientPlaceId, {day: day.date, order: ++visitOrder});
          } else if (item.type === 'MEAL') {
            const mealType = item.startTime >= '17:30' && item.startTime < '20:30' ? 'DINNER'
              : item.startTime >= '11:30' && item.startTime < '14:00' ? 'LUNCH' : null;
            if (mealType) slots.push({date: day.date, mealType, startTime: item.startTime, endTime: item.endTime});
          }
        }
      }
      if (positions.size !== built.value.places.length) return null;
      return {request: {...built.value, places: built.value.places.map(place => ({...place, ...positions.get(place.clientPlaceId)}))}, slots};
    }
    return {showStep, reset, hasCurrentResult: () => Boolean(lastFingerprint && JSON.stringify(currentRequest().value) === lastFingerprint), confirmedEstimate};
  }
  return Object.freeze({buildRequest, validResult, errorText, mount});
});
