'use strict';

(function (root, factory) {
  const mapModule = root.RoutyPlaceWorkspace || (typeof require === 'function' ? require('./place-workspace.js') : null);
  const api = factory(mapModule);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyRestaurantWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (mapModule) {
  const slotKey = slot => `${slot.date}|${slot.mealType}`;
  const point = value => Number.isFinite(value?.latitude) && Number.isFinite(value?.longitude)
    && value.latitude >= 33 && value.latitude <= 39 && value.longitude >= 124 && value.longitude <= 132;
  const safeLink = url => {
    try { const parsed = new URL(url); return parsed.protocol === 'https:' && parsed.hostname === 'place.map.kakao.com'; }
    catch (_) { return false; }
  };

  function searchBody(confirmed, slot, menu, referenceId, bounds = null, page = 1) {
    if (!confirmed?.request || !slot || !confirmed.slots?.some(item => slotKey(item) === slotKey(slot))
      || !menu || typeof menu.searchQuery !== 'string' || !menu.searchQuery.trim() || menu.searchQuery.trim().length > 50
      || !Number.isInteger(page) || page < 1 || page > 45) return null;
    const sameDay = confirmed.request.places.filter(place => place.day === slot.date);
    if (referenceId && !sameDay.some(place => place.clientPlaceId === referenceId)) return null;
    if (bounds && (!point({latitude: bounds.minLatitude, longitude: bounds.minLongitude})
      || !point({latitude: bounds.maxLatitude, longitude: bounds.maxLongitude})
      || bounds.minLatitude >= bounds.maxLatitude || bounds.minLongitude >= bounds.maxLongitude)) return null;
    return {estimate: confirmed.request, mealDate: slot.date, mealType: slot.mealType,
      menuQuery: menu.searchQuery.trim(), referenceAttractionClientPlaceId: referenceId || null,
      ...(bounds ? {bounds} : {}), page, size: 15};
  }

  function validResponse(body, request) {
    if (body?.mealDate !== request.mealDate || !/^([01]\d|2[0-3]):[0-5]\d$/.test(body.mealStartTime || '')
      || !/^([01]\d|2[0-3]):[0-5]\d$/.test(body.mealEndTime || '')
      || !point(body.previousPlace) || !point(body.nextPlace)
      || (body.referenceAttraction && !point(body.referenceAttraction))
      || !Array.isArray(body.places) || body.page !== request.page || typeof body.hasNext !== 'boolean'
      || (body.places.length === 0 && body.emptyReason !== 'NO_CANDIDATES')
      || (request.referenceAttractionClientPlaceId && body.referenceAttraction?.clientPlaceId !== request.referenceAttractionClientPlaceId)
      || new Set(body.places.map(place => place.kakaoPlaceId)).size !== body.places.length) return false;
    return body.places.every(place => typeof place.kakaoPlaceId === 'string' && Boolean(place.kakaoPlaceId)
      && typeof place.providerDisplayName === 'string' && Boolean(place.providerDisplayName)
      && typeof place.address === 'string' && typeof place.selectionToken === 'string' && Boolean(place.selectionToken)
      && point(place) && safeLink(place.placeUrl) && Number.isFinite(place.detourKilometers)
      && Number.isInteger(place.estimatedDetourMinutes) && place.estimatedDetourMinutes >= 0);
  }

  function errorText(response, body) {
    switch (body?.code) {
      case 'VALIDATION_FAILED': return '식사 슬롯이나 선택 정보가 유효하지 않습니다. 추정 일정을 다시 계산하고 만료된 장소는 다시 선택해 주세요.';
      case 'REQUEST_IN_PROGRESS': return '같은 요청을 처리 중입니다. 잠시 뒤 새로 검색해 주세요.';
      case 'REQUEST_ALREADY_COMPLETED': return '이미 처리된 요청입니다. 새 검색을 시작해 주세요.';
      case 'RATE_LIMIT_EXCEEDED': return `검색 한도에 도달했습니다.${Number.isInteger(body.retryAfterSeconds) && body.retryAfterSeconds > 0 ? ` ${body.retryAfterSeconds}초 뒤` : ' 잠시 뒤'} 다시 시도해 주세요.`;
      case 'PLACE_PROVIDER_UNAVAILABLE': return '음식점 검색 서비스를 잠시 사용할 수 없습니다. 선택한 음식점은 유지됩니다.';
      default: return response.status === 401 ? '로그인 시간이 만료되었습니다. 다시 로그인해 주세요.' : '음식점 검색을 완료하지 못했습니다. 다시 시도해 주세요.';
    }
  }

  function mount(document, window, client, context) {
    const $ = selector => document.querySelector(selector);
    const section = $('#restaurant-workspace'), slotInput = $('#restaurant-slot'), menuInput = $('#restaurant-menu');
    const referenceInput = $('#restaurant-reference'), status = $('#restaurant-status');
    const layout = $('#restaurant-layout'), intro = $('#restaurant-intro');
    const map = mapModule.createMapView(window, $('#restaurant-map'), $('#restaurant-map-status'));
    const selections = new Map();
    let fingerprint = null, pending = null, version = 0, results = [], references = null, pagination = null;
    function setStatus(message, error = false) { status.textContent = message; status.dataset.error = String(error); if (error) status.focus({preventScroll: true}); else if (document.activeElement === status) status.blur(); }
    function cancel() { version++; pending?.abort(); pending = null; $('#restaurant-submit').disabled = false; }
    function clearResults() { results = []; references = null; pagination = null; map.clear(); render(); }
    function reset() { cancel(); fingerprint = null; selections.clear(); slotInput.replaceChildren(); menuInput.replaceChildren(); referenceInput.replaceChildren(); clearResults(); setStatus(''); }
    function selectedSlot(confirmed) { return confirmed?.slots.find(slot => slotKey(slot) === slotInput.value) || null; }
    function renderReferenceChoices(confirmed, menus) {
      const slot = selectedSlot(confirmed), menu = menus[Number(menuInput.value)];
      const prior = referenceInput.value; referenceInput.replaceChildren();
      const none = document.createElement('option'); none.value = ''; none.textContent = '기준 관광지 없음'; referenceInput.append(none);
      for (const place of confirmed?.request.places.filter(item => item.day === slot?.date) || []) {
        const option = document.createElement('option'); option.value = place.clientPlaceId; option.textContent = place.displayName; referenceInput.append(option);
      }
      referenceInput.value = prior && [...referenceInput.options].some(option => option.value === prior) ? prior
        : [...referenceInput.options].some(option => option.value === menu?.targetClientPlaceId) ? menu.targetClientPlaceId : '';
    }
    function sync() {
      const data = context(), confirmed = data.confirmed, menus = data.menus;
      const nextFingerprint = confirmed ? JSON.stringify({request: confirmed.request, slots: confirmed.slots, menus}) : null;
      if (fingerprint === nextFingerprint) return;
      cancel(); selections.clear(); clearResults(); fingerprint = nextFingerprint;
      slotInput.replaceChildren(); menuInput.replaceChildren();
      for (const slot of confirmed?.slots || []) {
        const option = document.createElement('option'); option.value = slotKey(slot);
        option.textContent = `${slot.date} · ${slot.mealType === 'LUNCH' ? '점심' : '저녁'} ${slot.startTime}–${slot.endTime}`; slotInput.append(option);
      }
      menus.forEach((menu, index) => { const option = document.createElement('option'); option.value = String(index); option.textContent = menu.name; menuInput.append(option); });
      renderReferenceChoices(confirmed, menus);
      setStatus(confirmed?.slots.length ? '' : '이 추정 일정에는 검색할 식사 슬롯이 없습니다. 활동 시간을 조정하면 다시 계산할 수 있습니다.');
    }
    function referenceName(reference, fallback) {
      if (!reference) return fallback;
      const place = context().confirmed?.request.places.find(item => item.clientPlaceId === reference.clientPlaceId);
      return place?.displayName || ({HOTEL: '숙소', START_BOUNDARY: '시작 장소', END_BOUNDARY: '종료 장소'}[reference.kind] || fallback);
    }
    function mapPlaces() {
      if (!references) return [];
      return [[references.previousPlace, '직전 장소'], [references.nextPlace, '직후 장소'], [references.referenceAttraction, '기준 관광지']]
        .filter(([item]) => item).map(([item, name], index) => ({kakaoPlaceId: `reference:${index}`, providerDisplayName: `${name} · ${referenceName(item, name)}`, latitude: item.latitude, longitude: item.longitude}));
    }
    function render() {
      const slot = slotInput.value, chosen = selections.get(slot);
      const searched = Boolean(pagination);
      layout.hidden = !searched;
      $('#restaurant-view-switch').hidden = !searched;
      intro.hidden = searched || !context().confirmed?.slots.length;
      $('#restaurant-context').hidden = !references;
      $('#restaurant-context').replaceChildren();
      if (references) {
        for (const [label, ref] of [['직전 장소', references.previousPlace], ['직후 장소', references.nextPlace], ['기준 관광지', references.referenceAttraction]]) {
          if (!ref) continue;
          const item = document.createElement('span'); item.textContent = `${label}: ${referenceName(ref, label)}`; $('#restaurant-context').append(item);
        }
      }
      const cards = results.map(place => {
        const item = document.createElement('li'); item.className = 'place-result';
        const heading = document.createElement('strong'); heading.textContent = place.providerDisplayName;
        const address = document.createElement('span'); address.textContent = place.address;
        const detour = document.createElement('span'); detour.textContent = `예상 동선 이탈 ${place.estimatedDetourMinutes}분`;
        const link = document.createElement('a'); link.href = place.placeUrl; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = '카카오 장소 보기';
        const button = document.createElement('button'); button.type = 'button'; button.className = 'button button-ghost';
        button.textContent = chosen?.kakaoPlaceId === place.kakaoPlaceId ? '선택됨' : '선택';
        button.setAttribute('aria-pressed', String(chosen?.kakaoPlaceId === place.kakaoPlaceId));
        button.addEventListener('click', () => choose(place.kakaoPlaceId));
        item.append(heading, address, detour, link, button); return item;
      });
      if (searched && !results.length) {
        const item = document.createElement('li'); item.className = 'workspace-empty';
        const title = document.createElement('h3'); title.textContent = '조건에 맞는 음식점이 없어요';
        const help = document.createElement('p'); help.textContent = '메뉴를 바꾸거나 지도 영역을 이동해 다시 검색해 보세요.';
        item.append(title, help); cards.push(item);
      }
      $('#restaurant-results').replaceChildren(...cards);
      $('#restaurant-page').parentElement.hidden = !searched || !results.length;
      $('#restaurant-page').textContent = String(pagination?.page || 1);
      $('#restaurant-prev-page').disabled = !pagination || pagination.page <= 1 || Boolean(pending);
      $('#restaurant-next-page').disabled = !pagination?.hasNext || Boolean(pending);
      const selected = $('#restaurant-selected'); selected.hidden = selections.size === 0; selected.replaceChildren();
      for (const [key, place] of selections) {
        const item = document.createElement('div'); item.className = 'selected-place';
        const label = document.createElement('strong'); label.textContent = `${key.replace('|LUNCH', ' 점심').replace('|DINNER', ' 저녁')} · ${place.providerDisplayName}`;
        const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '선택 해제';
        remove.addEventListener('click', () => { selections.delete(key); render(); }); item.append(label, remove); selected.append(item);
      }
      map.render([...mapPlaces(), ...results, ...(chosen && !results.some(place => place.kakaoPlaceId === chosen.kakaoPlaceId) ? [chosen] : [])], new Set(chosen ? [chosen.kakaoPlaceId] : []), choose);
    }
    function choose(id) {
      const place = results.find(item => item.kakaoPlaceId === id); if (!place) return;
      if (selections.get(slotInput.value)?.kakaoPlaceId === id) selections.delete(slotInput.value);
      else selections.set(slotInput.value, place);
      render(); setStatus('음식점 선택을 현재 작성 흐름에 유지했습니다. 다른 검색 결과도 살펴볼 수 있습니다.');
    }
    async function search(page = 1, bounds = null) {
      const data = context(), confirmed = data.confirmed;
      const request = searchBody(confirmed, selectedSlot(confirmed), data.menus[Number(menuInput.value)], referenceInput.value, bounds, page);
      if (!request) { setStatus('식사 슬롯·메뉴·기준 관광지 또는 지도 영역을 확인해 주세요.', true); return; }
      if (pending) return;
      const expected = fingerprint, currentSlot = slotInput.value, id = ++version, controller = new AbortController(); pending = controller;
      $('#restaurant-submit').disabled = true; render(); setStatus('음식점을 검색하고 있습니다.');
      try {
        const response = await client.protectedRequest('/api/places/restaurants/search', {method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()}, body: JSON.stringify(request), signal: controller.signal});
        if (id !== version || expected !== fingerprint || currentSlot !== slotInput.value || !(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        if (id !== version || expected !== fingerprint || currentSlot !== slotInput.value) return;
        if (!response.ok) {
          if (response.status === 400 && body?.code === 'VALIDATION_FAILED') { selections.clear(); clearResults(); }
          setStatus(errorText(response, body), true); return;
        }
        if (!validResponse(body, request)) { setStatus('음식점 결과를 확인할 수 없습니다. 다시 검색해 주세요.', true); return; }
        results = body.places; references = body; pagination = {page: body.page, hasNext: body.hasNext};
        if (!results.length) {
          layout.dataset.mobileView = 'list';
          section.querySelectorAll('[data-restaurant-view]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.restaurantView === 'list')));
        }
        render();
        map.ensure().then(ok => { if (ok && !section.hidden) render(); });
        setStatus(results.length ? `${results.length}곳을 찾았습니다. 목록이나 지도 마커에서 직접 선택해 주세요.` : '조건에 맞는 음식점 후보가 없습니다. 다른 메뉴나 지도 영역으로 다시 검색해 주세요.');
      } catch (_) { if (id === version) setStatus('연결을 확인하고 다시 검색해 주세요. 선택한 음식점은 유지됩니다.', true); }
      finally { if (pending === controller) { pending = null; $('#restaurant-submit').disabled = false; render(); } }
    }
    function showStep(step) {
      section.hidden = step !== 6;
      if (step !== 6) { cancel(); return; }
      sync();
      $('#restaurant-submit').disabled = !context().confirmed?.slots.length;
      if (pagination) map.ensure().then(ok => { if (ok && !section.hidden) render(); });
    }
    $('#restaurant-search-form').addEventListener('submit', event => { event.preventDefault(); search(); });
    slotInput.addEventListener('change', () => { cancel(); clearResults(); renderReferenceChoices(context().confirmed, context().menus); setStatus(''); });
    menuInput.addEventListener('change', () => { cancel(); clearResults(); renderReferenceChoices(context().confirmed, context().menus); setStatus(''); });
    referenceInput.addEventListener('change', () => { cancel(); clearResults(); setStatus(''); });
    $('#restaurant-prev-page').addEventListener('click', () => { if (pagination?.page > 1) search(pagination.page - 1); });
    $('#restaurant-next-page').addEventListener('click', () => { if (pagination?.hasNext) search(pagination.page + 1); });
    $('#restaurant-search-map').addEventListener('click', () => { const bounds = map.bounds(); if (bounds) search(1, bounds); else setStatus('지도를 먼저 불러와 주세요.', true); });
    section.querySelectorAll('[data-restaurant-view]').forEach(button => button.addEventListener('click', () => {
      const choice = button.dataset.restaurantView; $('#restaurant-layout').dataset.mobileView = choice;
      section.querySelectorAll('[data-restaurant-view]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
      if (choice === 'map') map.ensure().then(ok => { if (ok) render(); });
    }));
    window.addEventListener('pagehide', reset);
    render();
    return {showStep, reset, selected: () => [...selections].map(([key, place]) => ({slot: key, selectionToken: place.selectionToken}))};
  }
  return Object.freeze({searchBody, validResponse, errorText, mount});
});
