'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyReviewWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const clock = /^([01]\d|2[0-3]):[0-5]\d$/;
  const dayKey = slot => `${slot.date}|${slot.mealType}`;
  const mealType = item => item.startTime >= '17:30' && item.startTime < '20:30' ? 'DINNER'
    : item.startTime >= '11:30' && item.startTime < '14:00' ? 'LUNCH' : null;
  const safeLink = value => {
    try { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'place.map.kakao.com'; }
    catch (_) { return false; }
  };
  const point = place => Number.isFinite(place?.latitude) && Number.isFinite(place?.longitude);

  function createRequest(confirmed, restaurants, title) {
    if (!confirmed?.request || !confirmed.result || !Array.isArray(confirmed.slots) || !Array.isArray(restaurants)
      || typeof title !== 'string' || !title.trim() || Array.from(title.trim()).length > 100
      || confirmed.request.places.some(place => !place.day || !Number.isInteger(place.order))) return null;
    const slots = new Set(confirmed.slots.map(dayKey));
    if (new Set(restaurants.map(item => item.slot)).size !== restaurants.length
      || restaurants.some(item => !slots.has(item.slot) || !item.selectionToken || !item.displayName
        || Array.from(item.displayName).length > 50 || (item.memo && Array.from(item.memo).length > 1000))) return null;
    const chosen = new Map(restaurants.map(item => [item.slot, item]));
    return {...confirmed.request, title: title.trim(),
      ...(confirmed.request.hotelSelectionToken ? {hotelDisplayName: '숙소'} : {}),
      meals: confirmed.slots.map(slot => {
        const place = chosen.get(dayKey(slot));
        return {date: slot.date, mealType: slot.mealType,
          restaurantSelectionToken: place?.selectionToken || null,
          displayName: place?.displayName || (slot.mealType === 'LUNCH' ? '점심 식사' : '저녁 식사'),
          memo: place?.memo || null};
      })};
  }

  function validPlan(body) {
    return Number.isSafeInteger(body?.travelPlanId) && body.travelPlanId > 0 && typeof body.title === 'string'
      && typeof body.region?.displayName === 'string' && ['CAR', 'PUBLIC_TRANSIT'].includes(body.travelMode)
      && typeof body.startDate === 'string' && typeof body.endDate === 'string'
      && Array.isArray(body.days) && body.days.length >= 1 && body.days.length <= 7
      && (body.hotel == null || (typeof body.hotel.displayName === 'string' && safeLink(body.hotel.placeUrl)))
      && (body.warnings === undefined || Array.isArray(body.warnings))
      && body.days.every((day, index) => day.day === index + 1 && typeof day.date === 'string'
        && Array.isArray(day.items) && day.items.every((item, order) => item.order === order + 1
          && ['VISIT', 'MOVE', 'MEAL'].includes(item.type) && clock.test(item.startTime) && clock.test(item.endTime)
          && (item.type === 'MOVE' ? Number.isInteger(item.estimatedMinutes) && item.estimatedMinutes > 0
            : typeof item.displayName === 'string' && (item.placeUrl == null || safeLink(item.placeUrl)))));
  }

  function errorText(response, body) {
    if (body?.code === 'PLAN_CAPACITY_EXCEEDED') {
      const details = body.details;
      return /^\d{4}-\d{2}-\d{2}$/.test(details?.date || '') && Number.isInteger(details.exceededMinutes)
        ? `${details.date} 일정이 ${details.exceededMinutes}분 초과합니다. 활동 시간·체류 시간·장소·식사를 직접 조정한 뒤 다시 검토해 주세요.`
        : '일정이 허용 시간을 초과합니다. 직접 조정한 뒤 다시 검토해 주세요.';
    }
    if (body?.code === 'ROUTE_NOT_FOUND') return '이동 경로를 찾지 못했습니다. 작성 내용을 유지했습니다. 표시된 날짜나 구간을 확인하고 조정한 뒤 전체 경로를 다시 검증해 주세요.';
    if (body?.code === 'VALIDATION_FAILED') return '입력이나 장소 선택이 유효하지 않습니다. 만료된 장소는 다시 선택하고 추정 일정을 다시 계산해 주세요.';
    if (body?.code === 'REQUEST_IN_PROGRESS' || body?.code === 'REQUEST_ALREADY_COMPLETED') return '같은 완료 요청을 다시 처리할 수 없습니다. 내 여행에서 저장 여부를 확인한 뒤 새 요청을 시작해 주세요.';
    if (response.status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
    if (response.status === 429) return `호출 한도에 도달했습니다.${Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0 ? ` ${body.retryAfterSeconds}초 뒤` : ' 잠시 뒤'} 다시 시도해 주세요.`;
    if (body?.code === 'ROUTE_PROVIDER_UNAVAILABLE') return '경로 서비스를 잠시 사용할 수 없습니다. 작성 내용은 유지됩니다.';
    return '완료 요청을 처리하지 못했습니다. 저장 여부를 확인한 뒤 다시 시도해 주세요.';
  }

  function stopsForDay(confirmed, selected, restaurants, index) {
    const day = confirmed.result.days[index], last = confirmed.result.days.length - 1;
    const attractions = new Map(selected.attractions.map(place => [place.selectionToken, place]));
    const chosen = new Map(restaurants.map(place => [place.slot, place]));
    const stops = [];
    const add = (place, label, itemOrder = null) => { if (point(place)) stops.push({place, label, number: stops.length + 1, itemOrder}); };
    add(index === 0 ? selected.startBoundary : selected.hotel, index === 0 ? '시작 장소' : '숙소 출발');
    for (const item of day.items) {
      if (item.type === 'VISIT') {
        const place = attractions.get(confirmed.request.places.find(entry => entry.clientPlaceId === item.clientPlaceId)?.selectionToken);
        if (place) add(place, confirmed.request.places.find(entry => entry.clientPlaceId === item.clientPlaceId)?.displayName || '관광지', item.order);
      } else if (item.type === 'MEAL') {
        const type = mealType(item), place = type && chosen.get(`${day.date}|${type}`);
        if (place) add(place, place.displayName || '선택한 음식점', item.order);
      }
    }
    add(index === last ? selected.endBoundary : selected.hotel, index === last ? '종료 장소' : '숙소 도착');
    return stops;
  }

  function mount(document, window, client, context, onComplete, onAdjust, onRouteFailure) {
    const $ = selector => document.querySelector(selector);
    const section = $('#review-workspace'), status = $('#review-status'), mapElement = $('#review-map');
    let dayIndex = 0, map = null, markers = [], pending = null, version = 0, mapVersion = 0, shown = false;
    function setStatus(message, error = false) { status.textContent = message; status.dataset.error = String(error); if (error) status.focus({preventScroll: true}); }
    function clearMap() { markers.forEach(marker => marker.setMap(null)); markers = []; }
    function cancel() { version++; pending?.abort(); pending = null; $('#review-submit').disabled = false; }
    function reset() { cancel(); mapVersion++; clearMap(); map = null; mapElement.replaceChildren(); dayIndex = 0; shown = false; $('#review-title').value = ''; $('#review-days').replaceChildren(); $('#review-timeline').replaceChildren(); setStatus(''); }
    async function ensureMap() {
      if (map) { map.relayout(); return; }
      const expected = mapVersion;
      try {
        const maps = await window.RoutyPlaceWorkspace.loadMapSdk(window);
        if (!shown || map || expected !== mapVersion) return;
        mapElement.replaceChildren(); mapElement.classList.remove('is-unavailable');
        map = new maps.Map(mapElement, {center: new maps.LatLng(36.5, 127.8), level: 12});
        $('#review-map-status').textContent = '';
        renderMap();
      } catch (_) {
        if (!shown || expected !== mapVersion) return;
        mapElement.classList.add('is-unavailable');
        mapElement.textContent = '지도를 불러오지 못했습니다. 시간순 일정표에서 방문 순서를 확인해 주세요.';
        $('#review-map-status').textContent = '지도 없이도 날짜별 일정표를 확인하고 완료할 수 있습니다.';
      }
    }
    function renderMap() {
      clearMap();
      if (!map) return;
      const data = context(), confirmed = data.confirmed;
      if (!confirmed) return;
      const maps = window.kakao.maps, bounds = new maps.LatLngBounds();
      const grouped = new Map();
      for (const stop of stopsForDay(confirmed, data.selected, data.restaurants, dayIndex)) {
        const key = `${stop.place.latitude}|${stop.place.longitude}`;
        if (!grouped.has(key)) grouped.set(key, []);
        grouped.get(key).push(stop);
      }
      for (const group of grouped.values()) {
        const {latitude, longitude} = group[0].place, position = new maps.LatLng(latitude, longitude);
        const numbers = group.map(item => item.number).join('·');
        const node = document.createElement('span'); node.className = 'review-marker'; node.textContent = numbers;
        node.setAttribute('aria-label', `${numbers}번 방문`);
        const marker = new maps.CustomOverlay({map, position, content: node, yAnchor: 1});
        node.addEventListener('click', () => $('#review-timeline').querySelector(`[data-stop="${group[0].number}"]`)?.scrollIntoView({block: 'center'}));
        markers.push(marker); bounds.extend(position);
      }
      if (grouped.size) map.setBounds(bounds); else map.relayout();
    }
    function renderDay() {
      const data = context(), confirmed = data.confirmed;
      if (!confirmed) return;
      const days = confirmed.result.days;
      if (dayIndex >= days.length) dayIndex = 0;
      $('#review-days').replaceChildren(...days.map((day, index) => {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'button button-ghost';
        button.textContent = `${index + 1}일차 · ${day.date}${index === dayIndex ? ' · 선택됨' : ''}`;
        button.setAttribute('aria-pressed', String(index === dayIndex));
        button.addEventListener('click', () => { dayIndex = index; renderDay(); }); return button;
      }));
      const day = days[dayIndex], stops = stopsForDay(confirmed, data.selected, data.restaurants, dayIndex);
      $('#review-day-title').textContent = `${dayIndex + 1}일차 일정`;
      const byOrder = new Map(stops.filter(stop => stop.itemOrder !== null).map(stop => [stop.itemOrder, stop]));
      const entries = [];
      function row(label, interval, stop, type, boundary) {
        const entry = document.createElement('li'); entry.className = `review-entry review-${type.toLowerCase()}`;
        if (stop) entry.dataset.stop = String(stop.number);
        const marker = document.createElement('span'); marker.className = 'review-entry-marker'; marker.setAttribute('aria-hidden', 'true');
        marker.textContent = stop ? String(stop.number) : type === 'MOVE' ? '↓' : type === 'MEAL' ? '식' : '•';
        const content = document.createElement('div'); content.className = 'review-entry-content';
        const meta = document.createElement('div'); meta.className = 'review-entry-meta';
        const kind = document.createElement('span'); kind.textContent = boundary || (type === 'VISIT' ? '관광지' : type === 'MEAL' ? '식사' : '이동');
        meta.append(kind);
        if (interval) { const time = document.createElement('time'); time.textContent = interval; meta.append(time); }
        const name = document.createElement('strong'); name.textContent = label;
        content.append(meta, name); entry.append(marker, content); entries.push(entry);
      }
      if (stops.length) row(stops[0].label, null, stops[0], 'BOUNDARY', '출발');
      for (const item of day.items) {
        const stop = byOrder.get(item.order);
        const label = item.type === 'VISIT' ? item.displayName : item.type === 'MOVE'
          ? `예상 이동시간 ${item.estimatedMinutes}분` : (stop?.label || (mealType(item) === 'DINNER' ? '저녁 식사' : '점심 식사'));
        row(label, `${item.startTime}–${item.endTime}`, stop, item.type);
      }
      if (stops.length > 1) row(stops.at(-1).label, null, stops.at(-1), 'BOUNDARY', '도착');
      $('#review-timeline').replaceChildren(...entries);
      renderMap();
    }
    function showStep(step) {
      shown = step === 7; section.hidden = !shown;
      if (!shown) { cancel(); mapVersion++; clearMap(); return; }
      const data = context();
      if (!data.confirmed) { setStatus('현재 입력으로 추정 일정을 다시 계산해 주세요.', true); return; }
      if (!$('#review-title').value) $('#review-title').value = `${data.regionName || '여행'} 일정`;
      renderDay(); ensureMap();
    }
    async function submit(event) {
      event.preventDefault(); if (pending) return;
      const data = context(), request = createRequest(data.confirmed, data.restaurants, $('#review-title').value);
      if (!request) { setStatus('일정 제목과 선택한 음식점의 저장 이름을 확인하고, 변경한 일정은 다시 계산해 주세요.', true); return; }
      const fingerprint = JSON.stringify({request, restaurants: data.restaurants});
      const id = ++version, controller = new AbortController(); pending = controller;
      $('#review-submit').disabled = true; setStatus('전체 이동 경로와 저장 가능 여부를 확인하고 있습니다.');
      try {
        const response = await client.protectedRequest('/api/travel-plans', {method: 'POST',
          headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()},
          body: JSON.stringify(request), signal: controller.signal});
        if (id !== version || !(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        const now = context(), current = createRequest(now.confirmed, now.restaurants, $('#review-title').value);
        if (id !== version || !shown) return;
        if (!current || JSON.stringify({request: current, restaurants: now.restaurants}) !== fingerprint) {
          setStatus('작성 내용이 바뀌어 이전 완료 응답을 적용하지 않았습니다. 내 여행에서 저장 여부를 확인해 주세요.');
          return;
        }
        if (!response.ok) {
          if (response.status === 422 && body?.code === 'ROUTE_NOT_FOUND') onRouteFailure(body);
          setStatus(errorText(response, body), true); return;
        }
        if (response.status !== 201 || !validPlan(body)) { setStatus('저장 응답을 확인할 수 없습니다. 내 여행에서 저장 여부를 확인해 주세요.', true); return; }
        onComplete(body);
      } catch (_) { if (id === version) setStatus('연결이 끊어졌습니다. 내 여행에서 저장 여부를 확인한 뒤 다시 시도해 주세요.', true); }
      finally { if (pending === controller) { pending = null; $('#review-submit').disabled = false; } }
    }
    $('#review-form').addEventListener('submit', submit);
    $('#review-adjust').addEventListener('click', () => { cancel(); onAdjust(); });
    window.addEventListener('pagehide', reset);
    return {showStep, reset};
  }

  function createPlaceLink(document, url, label) {
    const link = document.createElement('a');
    link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer';
    link.className = 'kakao-place-link'; link.setAttribute('aria-label', label); link.title = label;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round'); svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true'); svg.setAttribute('focusable', 'false');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M13 4h7v7m0-7-9 9M19 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h5');
    svg.append(path); link.append(svg);
    return link;
  }

  function renderCompleted(document, body) {
    const $ = selector => document.querySelector(selector);
    $('#detail-title').textContent = body.title;
    $('#detail-summary').textContent = `${body.region.displayName} · ${body.startDate}–${body.endDate} · ${body.travelMode === 'CAR' ? '자동차' : '대중교통'}`;
    $('#detail-warning').textContent = body.warnings?.includes('ESTIMATED_TRAVEL_TIMES_USED')
      ? '일부 경로를 확인하지 못해 일정 전체의 예상 이동시간을 거리 기반으로 계산했습니다.' : '';
    const days = body.days.map(day => {
      const article = document.createElement('article'); article.className = 'itinerary-document';
      const header = document.createElement('header'), number = document.createElement('span'), date = document.createElement('p');
      number.textContent = `DAY ${String(day.day).padStart(2, '0')}`; date.textContent = `${day.date} · ${day.activityStartTime}–${day.activityEndTime}`;
      header.append(number, date);
      const list = document.createElement('ol');
      for (const item of day.items) {
        const row = document.createElement('li'), time = document.createElement('time'), copy = document.createElement('span'), name = document.createElement('strong'), detail = document.createElement('small');
        copy.className = 'completed-item-copy';
        time.textContent = `${item.startTime}–${item.endTime}`;
        name.textContent = item.type === 'MOVE' ? `예상 이동시간 ${item.estimatedMinutes}분` : item.displayName;
        detail.textContent = item.type === 'VISIT' ? `체류 ${item.stayMinutes}분${item.memo ? ` · ${item.memo}` : ''}` : (item.memo || '');
        copy.append(name, detail);
        if (item.placeUrl && safeLink(item.placeUrl)) {
          copy.append(createPlaceLink(document, item.placeUrl, `카카오 지도에서 ${item.displayName} 장소 보기`));
        }
        row.append(time, copy); list.append(row);
      }
      article.append(header, list); return article;
    });
    $('#detail-days').replaceChildren(...days);
    const hotel = $('#detail-hotel'); hotel.replaceChildren(); hotel.hidden = !body.hotel;
    if (body.hotel) {
      const name = document.createElement('strong'); name.textContent = `숙소 · ${body.hotel.displayName}`;
      const memo = document.createElement('p'); memo.textContent = body.hotel.memo || '';
      const link = createPlaceLink(document, body.hotel.placeUrl, `카카오 지도에서 ${body.hotel.displayName} 보기`);
      hotel.append(name, memo, link);
    }
  }

  function clearCompleted(document) {
    document.querySelector('#detail-title').textContent = '저장된 일정이 놓일 자리';
    document.querySelector('#detail-summary').textContent = '내 여행 목록 연결은 다음 화면 작업에서 진행합니다.';
    document.querySelector('#detail-warning').textContent = '';
    document.querySelector('#detail-days').replaceChildren();
    document.querySelector('#detail-hotel').replaceChildren();
    document.querySelector('#detail-hotel').hidden = true;
  }

  return Object.freeze({createRequest, validPlan, errorText, stopsForDay, mount, renderCompleted, clearCompleted});
});
