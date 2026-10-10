'use strict';

(function (root, factory) {
  const api = factory(root.RoutyPlaceSelectionState || (typeof require === 'function' ? require('./place-selection-state.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyPlaceWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (stateModule) {
  const {ROLES, BOUNDARY_SLOTS} = stateModule;
  const SEARCH_SIZE = 15;
  const MESSAGES = Object.freeze({
    VALIDATION_FAILED: '선택 정보가 만료되었거나 요청을 확인할 수 없습니다. 장소를 다시 검색해 선택해 주세요.',
    AUTHENTICATION_REQUIRED: '로그인 시간이 만료되었습니다. 다시 로그인해 주세요.',
    REQUEST_IN_PROGRESS: '같은 요청이 처리 중입니다. 잠시 뒤 새로 검색해 주세요.',
    REQUEST_ALREADY_COMPLETED: '이미 처리된 요청입니다. 새 검색을 시작해 주세요.',
    RATE_LIMIT_EXCEEDED: '장소 검색 한도에 도달했습니다. 안내된 시간 뒤 다시 시도해 주세요.',
    PLACE_PROVIDER_UNAVAILABLE: '장소 검색을 잠시 사용할 수 없습니다. 다시 시도해 주세요.',
    NETWORK_ERROR: '연결을 확인하고 다시 검색해 주세요.',
    SERVER_ERROR: '검색을 완료하지 못했습니다. 다시 시도해 주세요.'
  });

  function errorFor(status, body) {
    const code = Object.hasOwn(MESSAGES, body?.code) ? body.code : status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR';
    const seconds = code === 'RATE_LIMIT_EXCEEDED' && Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0
      ? ` ${body.retryAfterSeconds}초 뒤 다시 시도해 주세요.` : '';
    return {code, message: MESSAGES[code] + seconds};
  }

  function searchBody(role, regionId, query, options = {}) {
    if (role === ROLES.ATTRACTION) {
      return {regionId, ...(options.districtFilterId ? {districtFilterId: options.districtFilterId} : {}),
        placeRole: role, query, center: options.center || null,
        radiusMeters: options.wholeRegion ? null : 20000, page: options.page || 1, size: SEARCH_SIZE};
    }
    if (role === ROLES.TRAVEL_BOUNDARY) {
      return {regionId, query, ...(options.center ? {center: options.center, radiusMeters: 20000} : {}),
        page: options.page || 1, size: SEARCH_SIZE};
    }
    throw new TypeError('Unsupported search role');
  }

  function hotelBody(regionId, tokens, mode, bounds, page = 1) {
    const body = {regionId, attractionSelectionTokens: tokens, mode: mode === 'GEOMETRIC_MEDIAN_10' ? 'GEOMETRIC_MEDIAN' : mode,
      page, size: SEARCH_SIZE};
    if (mode === 'GEOMETRIC_MEDIAN_10') body.radiusMeters = 10000;
    if (mode === 'MAP_BOUNDS') {
      if (!bounds) throw new TypeError('Map bounds required');
      body.bounds = bounds;
    }
    return body;
  }

  function validPage(body) {
    return Array.isArray(body?.places) && Number.isInteger(body.page) && body.page >= 1
      && typeof body.hasNext === 'boolean';
  }

  function loadMapSdk(window) {
    if (window.kakao?.maps) return Promise.resolve(window.kakao.maps);
    if (!window.__routyMapSdkPromise) {
      window.__routyMapSdkPromise = window.fetch('js/map-config.json', {cache: 'no-store'})
        .then(response => response.ok ? response.json() : null)
        .then(config => {
          if (!config || typeof config.javascriptKey !== 'string' || !/^[a-zA-Z0-9]+$/.test(config.javascriptKey)) throw new Error('Map key unavailable');
          return new Promise((resolve, reject) => {
            const script = window.document.createElement('script');
            script.src = `https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${encodeURIComponent(config.javascriptKey)}`;
            script.onload = () => window.kakao?.maps?.load(() => resolve(window.kakao.maps));
            script.onerror = () => reject(new Error('Map SDK unavailable'));
            window.document.head.append(script);
          });
        }).catch(error => { window.__routyMapSdkPromise = null; throw error; });
    }
    return window.__routyMapSdkPromise;
  }

  function createMapView(window, element, status) {
    let map = null;
    let markers = [];
    let ready = false;
    let generation = 0;
    async function ensure() {
      if (ready) { map.relayout(); return true; }
      const expected = generation;
      try {
        const maps = await loadMapSdk(window);
        if (expected !== generation) return false;
        element.classList.remove('is-unavailable');
        element.replaceChildren();
        map = new maps.Map(element, {center: new maps.LatLng(36.5, 127.8), level: 12});
        ready = true;
        status.textContent = '';
        return true;
      } catch (_) {
        if (expected !== generation) return false;
        element.classList.add('is-unavailable');
        element.textContent = '지도를 표시할 수 없어요. 목록에서 장소를 선택해 주세요.';
        status.textContent = '지도를 불러오지 못했습니다. 목록에서 장소를 선택할 수 있습니다. 브라우저 지도 키와 등록 도메인을 확인해 주세요.';
        return false;
      }
    }
    function render(places, selectedIds, onChoose) {
      if (!ready) return;
      const maps = window.kakao.maps;
      markers.forEach(marker => marker.setMap(null));
      markers = [];
      const bounds = new maps.LatLngBounds();
      const icon = selected => new maps.MarkerImage(`data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="42" viewBox="0 0 32 42"><path d="M16 40S2 24 2 16a14 14 0 1 1 28 0c0 8-14 24-14 24Z" fill="${selected ? '#1748df' : '#303b4a'}" stroke="white" stroke-width="2"/><circle cx="16" cy="16" r="5" fill="white"/></svg>`)}`, new maps.Size(32, 42));
      for (const place of places) {
        const position = new maps.LatLng(place.latitude, place.longitude);
        const selected = selectedIds.has(place.kakaoPlaceId);
        const marker = new maps.Marker({map, position, image: icon(selected), title: `${selected ? '선택됨 · ' : ''}${place.providerDisplayName}`});
        maps.event.addListener(marker, 'click', () => onChoose(place.kakaoPlaceId));
        markers.push(marker);
        bounds.extend(position);
      }
      if (places.length) map.setBounds(bounds);
      else map.relayout();
    }
    function center() {
      if (!ready) return null;
      const point = map.getCenter();
      return {latitude: point.getLat(), longitude: point.getLng()};
    }
    function bounds() {
      if (!ready) return null;
      const area = map.getBounds(), sw = area.getSouthWest(), ne = area.getNorthEast();
      return {minLatitude: sw.getLat(), minLongitude: sw.getLng(), maxLatitude: ne.getLat(), maxLongitude: ne.getLng()};
    }
    function clear() { markers.forEach(marker => marker.setMap(null)); markers = []; }
    function dispose() { generation++; clear(); map = null; ready = false; element.replaceChildren(); element.classList.remove('is-unavailable'); status.textContent = ''; }
    return {ensure, render, center, bounds, clear, dispose};
  }

  function mount(document, window, client, onSummary) {
    const $ = selector => document.querySelector(selector);
    const store = stateModule.createStore();
    const map = createMapView(window, $('#place-map'), $('#place-map-status'));
    const hotelMap = createMapView(window, $('#hotel-map'), $('#hotel-map-status'));
    let region = null;
    let generation = 0;
    let role = ROLES.ATTRACTION;
    let boundarySlot = BOUNDARY_SLOTS.START;
    let district = null;
    let draft = new Map();
    let pages = {[ROLES.ATTRACTION]: null, [ROLES.TRAVEL_BOUNDARY]: null, [ROLES.HOTEL]: null};
    let results = {[ROLES.ATTRACTION]: [], [ROLES.TRAVEL_BOUNDARY]: [], [ROLES.HOTEL]: []};
    let requestVersion = 0;
    let pending = null;
    let pendingFingerprint = null;
    let districtVersion = 0;
    let districtPending = null;
    let activeStep = 0;

    function status(selector, message, error = false) {
      const target = $(selector);
      target.textContent = message;
      target.dataset.error = String(error);
      if (error) target.focus?.({preventScroll: true});
    }
    function cancelRequest() { requestVersion++; pending?.abort(); pending = null; pendingFingerprint = null; }
    function cancelDistrict() { districtVersion++; districtPending?.abort(); districtPending = null; }
    function clearSearch() {
      results = {[ROLES.ATTRACTION]: [], [ROLES.TRAVEL_BOUNDARY]: [], [ROLES.HOTEL]: []};
      pages = {[ROLES.ATTRACTION]: null, [ROLES.TRAVEL_BOUNDARY]: null, [ROLES.HOTEL]: null};
      map.clear(); hotelMap.clear();
      renderAll();
    }
    function reset() {
      cancelRequest(); cancelDistrict(); store.cancel(); region = null; generation = 0; district = null;
      map.dispose(); hotelMap.dispose();
      draft = new Map(); role = ROLES.ATTRACTION; boundarySlot = BOUNDARY_SLOTS.START;
      $('#district-results').replaceChildren();
      clearSearch(); renderDistrict(); status('#district-status', ''); status('#place-status', ''); status('#hotel-status', '');
      $('#trip-days').value = '1';
      document.querySelector('input[name="travel-mode"][value="CAR"]').checked = true;
      onSummary?.();
    }
    function setRegion(nextRegion) {
      if (region?.regionId === nextRegion.regionId) return;
      cancelRequest(); cancelDistrict();
      map.dispose(); hotelMap.dispose();
      region = nextRegion; generation = store.setContext(client.sessionContextId(), nextRegion.regionId).generation;
      district = null; draft = new Map(); role = ROLES.ATTRACTION; boundarySlot = BOUNDARY_SLOTS.START;
      document.querySelectorAll('[data-place-role]').forEach(tab => tab.setAttribute('aria-pressed', String(tab.dataset.placeRole === role)));
      $('#attraction-controls').hidden = false; $('#boundary-search-form').hidden = true;
      $('#attraction-selected').hidden = false; $('#boundary-selected').hidden = true;
      $('#district-results').replaceChildren();
      status('#district-status', '');
      clearSearch(); renderDistrict();
      status('#place-status', '지역이 바뀌어 이전 장소 선택을 지웠습니다. 다시 검색해 주세요.');
      onSummary?.();
    }
    function renderDistrict() {
      const target = $('#district-current');
      target.replaceChildren();
      if (!district) return;
      const label = document.createElement('span'); label.textContent = `구·군 필터: ${district.name}`;
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '필터 해제';
      remove.addEventListener('click', () => { district = null; renderDistrict(); status('#district-status', '구·군 필터를 해제했습니다.'); });
      target.append(label, remove);
    }
    function renderCards(listSelector, places, selectedIds, choose) {
      const list = $(listSelector);
      list.replaceChildren(...places.map(place => {
        const item = document.createElement('li'); item.className = 'place-result';
        const heading = document.createElement('strong'); heading.textContent = place.providerDisplayName;
        const address = document.createElement('span'); address.textContent = place.address;
        const link = document.createElement('a'); link.href = place.placeUrl; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = '카카오 장소 보기';
        const button = document.createElement('button'); button.type = 'button'; button.className = 'button button-ghost';
        button.textContent = selectedIds.has(place.kakaoPlaceId) ? '선택됨' : '선택';
        button.setAttribute('aria-pressed', String(selectedIds.has(place.kakaoPlaceId)));
        button.addEventListener('click', () => choose(place.kakaoPlaceId));
        item.append(heading, address, link, button);
        return item;
      }));
    }
    function selectPlace(id) {
      if (role === ROLES.ATTRACTION) {
        const selected = store.selected().attractions;
        if (selected.some(place => place.kakaoPlaceId === id)) { store.unselect(generation, role, id); draft.delete(id); }
        else if (selected.length >= days() * 5) { status('#place-status', `관광지는 ${days()}일 여행에서 최대 ${days() * 5}곳까지 선택할 수 있습니다.`, true); return; }
        else if (store.select(generation, role, id)) draft.set(id, {clientPlaceId: window.crypto.randomUUID(), name: '', stayMinutes: results[role].find(place => place.kakaoPlaceId === id).suggestedStayMinutes});
      } else {
        store.selectBoundary(generation, boundarySlot, id);
      }
      renderAll(); onSummary?.();
    }
    function selectHotel(id) {
      if (store.selected().hotel?.kakaoPlaceId === id) store.unselect(generation, ROLES.HOTEL, id);
      else store.select(generation, ROLES.HOTEL, id);
      renderAll(); onSummary?.();
    }
    function renderSelected() {
      const selected = store.selected();
      const attractions = $('#attraction-selected'); attractions.replaceChildren();
      const heading = document.createElement('h3'); heading.textContent = `선택한 관광지 ${selected.attractions.length}곳`;
      attractions.append(heading);
      for (const place of selected.attractions) {
        const values = draft.get(place.kakaoPlaceId) || {name: '', stayMinutes: place.suggestedStayMinutes};
        const card = document.createElement('div'); card.className = 'selected-place';
        const provider = document.createElement('strong'); provider.textContent = place.providerDisplayName;
        const nameLabel = document.createElement('label'); nameLabel.textContent = '사용자 장소 이름 (1~50자)';
        const name = document.createElement('input'); name.type = 'text'; name.maxLength = 50; name.value = values.name; name.placeholder = '직접 이름을 입력해 주세요';
        name.addEventListener('input', () => { values.name = name.value; draft.set(place.kakaoPlaceId, values); });
        nameLabel.append(name);
        const stayLabel = document.createElement('label'); stayLabel.textContent = '체류 시간 (분)';
        const stay = document.createElement('input'); stay.type = 'number'; stay.min = '30'; stay.max = '480'; stay.step = '10'; stay.value = String(values.stayMinutes);
        stay.addEventListener('change', () => { values.stayMinutes = Number(stay.value); draft.set(place.kakaoPlaceId, values); });
        stayLabel.append(stay);
        const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '선택 해제';
        remove.addEventListener('click', () => { store.unselect(generation, ROLES.ATTRACTION, place.kakaoPlaceId); draft.delete(place.kakaoPlaceId); renderAll(); onSummary?.(); });
        card.append(provider, nameLabel, stayLabel, remove); attractions.append(card);
      }
      const boundary = $('#boundary-selected'); boundary.replaceChildren();
      const slots = document.createElement('fieldset');
      const legend = document.createElement('legend'); legend.textContent = '검색 결과를 어느 경계로 선택할까요?'; slots.append(legend);
      for (const slot of [BOUNDARY_SLOTS.START, BOUNDARY_SLOTS.END]) {
        const label = document.createElement('label'), radio = document.createElement('input');
        radio.type = 'radio'; radio.name = 'boundary-slot'; radio.value = slot; radio.checked = slot === boundarySlot;
        radio.addEventListener('change', () => { boundarySlot = slot; });
        label.append(radio, document.createTextNode(slot === BOUNDARY_SLOTS.START ? ' 첫날 시작' : ' 마지막 날 종료')); slots.append(label);
      }
      boundary.append(slots);
      for (const [slot, place] of [[BOUNDARY_SLOTS.START, selected.startBoundary], [BOUNDARY_SLOTS.END, selected.endBoundary]]) {
        const card = document.createElement('div'); card.className = 'selected-place';
        const label = document.createElement('strong'); label.textContent = `${slot === BOUNDARY_SLOTS.START ? '시작' : '종료'}: ${place?.providerDisplayName || '선택 전'}`;
        card.append(label);
        if (place) { const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '선택 해제'; remove.addEventListener('click', () => { store.unselectBoundary(generation, slot); renderAll(); }); card.append(remove); }
        boundary.append(card);
      }
      const hotel = $('#hotel-selected'); hotel.replaceChildren();
      const hotelLabel = document.createElement('strong'); hotelLabel.textContent = selected.hotel ? `선택한 숙소: ${selected.hotel.providerDisplayName}` : '선택한 숙소 없음'; hotel.append(hotelLabel);
      if (selected.hotel) { const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '선택 해제'; remove.addEventListener('click', () => { store.unselect(generation, ROLES.HOTEL, selected.hotel.kakaoPlaceId); renderAll(); }); hotel.append(remove); }
    }
    function renderAll() {
      const selected = store.selected();
      const selectedIds = new Set(selected.attractions.map(place => place.kakaoPlaceId));
      if (role === ROLES.TRAVEL_BOUNDARY) for (const place of [selected.startBoundary, selected.endBoundary]) if (place) selectedIds.add(place.kakaoPlaceId);
      renderCards('#place-results', results[role], selectedIds, selectPlace);
      renderCards('#hotel-results', results[ROLES.HOTEL], new Set(selected.hotel ? [selected.hotel.kakaoPlaceId] : []), selectHotel);
      const additional = role === ROLES.ATTRACTION ? selected.attractions : [selected.startBoundary, selected.endBoundary].filter(Boolean);
      map.render([...new Map([...results[role], ...additional].map(place => [place.kakaoPlaceId, place])).values()], selectedIds, selectPlace);
      hotelMap.render([...new Map([...results[ROLES.HOTEL], ...selected.attractions, ...(selected.hotel ? [selected.hotel] : [])].map(place => [place.kakaoPlaceId, place])).values()], new Set(selected.hotel ? [selected.hotel.kakaoPlaceId] : []), selectHotel);
      renderSelected();
      for (const [key, prefix] of [[role, 'place'], [ROLES.HOTEL, 'hotel']]) {
        $(`#${prefix}-page`).textContent = String(pages[key]?.page || 1);
        $(`#${prefix}-prev-page`).disabled = !pages[key] || pages[key].page <= 1;
        $(`#${prefix}-next-page`).disabled = !pages[key]?.hasNext;
      }
    }
    async function send(roleToSearch, page = 1, center = null) {
      if (!region) { status(roleToSearch === ROLES.HOTEL ? '#hotel-status' : '#place-status', '먼저 지역을 선택해 주세요.', true); return; }
      const target = roleToSearch === ROLES.HOTEL ? '#hotel-status' : '#place-status';
      const query = $(roleToSearch === ROLES.ATTRACTION ? '#attraction-query' : '#boundary-query')?.value.trim();
      if (roleToSearch !== ROLES.HOTEL && !query) { status(target, '검색어를 입력해 주세요.', true); return; }
      const selected = store.selected();
      let body, path;
      if (roleToSearch === ROLES.HOTEL) {
        if (!selected.attractions.length) { status(target, '숙소를 찾으려면 관광지를 먼저 선택해 주세요.', true); return; }
        const mode = $('#hotel-mode').value;
        try { body = hotelBody(region.regionId, selected.attractions.map(place => place.selectionToken), mode, hotelMap.bounds(), page); }
        catch (_) { status(target, '현재 지도 영역을 사용할 수 없습니다. 지도를 불러오거나 다른 탐색 기준을 선택해 주세요.', true); return; }
        path = '/api/places/hotels/search';
      } else {
        body = searchBody(roleToSearch, region.regionId, query, {districtFilterId: district?.regionId,
          wholeRegion: roleToSearch === ROLES.ATTRACTION && $('#attraction-whole-region').checked && !center,
          center, page});
        path = roleToSearch === ROLES.ATTRACTION ? '/api/places/search' : '/api/places/travel-boundaries/search';
      }
      const fingerprint = `${path}:${JSON.stringify(body)}`;
      if (pending && pendingFingerprint === fingerprint) return;
      cancelRequest();
      const version = requestVersion, expectedGeneration = generation, controller = new AbortController(); pending = controller;
      pendingFingerprint = fingerprint;
      status(target, '검색 중입니다.');
      try {
        const response = await client.protectedRequest(path, {method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()}, body: JSON.stringify(body), signal: controller.signal});
        if (version !== requestVersion || expectedGeneration !== generation || roleToSearch !== (activeStep === 3 ? ROLES.HOTEL : role)) return;
        if (!(response instanceof window.Response)) return;
        const payload = await response.json().catch(() => null);
        if (version !== requestVersion || expectedGeneration !== generation) return;
        if (!response.ok) {
          const error = errorFor(response.status, payload);
          if (error.code === 'VALIDATION_FAILED' && roleToSearch === ROLES.HOTEL) {
            store.clear();
            generation = store.setContext(client.sessionContextId(), region.regionId).generation;
            draft.clear(); clearSearch();
          }
          status(target, error.message, true); onSummary?.(); return;
        }
        let accepted = false;
        try { accepted = validPage(payload) && payload.page === page && store.acceptSearchResults(generation, roleToSearch, payload.places); }
        catch (_) { accepted = false; }
        if (!accepted) {
          status(target, '검색 결과를 확인할 수 없습니다. 다시 검색해 주세요.', true); return;
        }
        results[roleToSearch] = payload.places;
        pages[roleToSearch] = {page: payload.page, hasNext: payload.hasNext};
        renderAll();
        status(target, payload.places.length ? `${payload.places.length}곳을 찾았습니다. 목록이나 지도 마커에서 선택해 주세요.` : '결과가 없습니다. 검색어나 지도 위치를 바꿔 다시 검색해 주세요.');
      } catch (_) { if (version === requestVersion) status(target, MESSAGES.NETWORK_ERROR, true); }
      finally { if (pending === controller) { pending = null; pendingFingerprint = null; } }
    }
    async function findDistrict(event) {
      event.preventDefault();
      const query = $('#district-query').value.trim();
      if (!query) { status('#district-status', '구·군 이름을 입력해 주세요.', true); return; }
      cancelDistrict(); const version = districtVersion, controller = new AbortController(); districtPending = controller;
      status('#district-status', '구·군을 찾는 중입니다.');
      try {
        const response = await client.protectedRequest(`/api/regions?query=${encodeURIComponent(query)}`, {headers: {Accept: 'application/json'}, signal: controller.signal});
        if (version !== districtVersion || !(response instanceof window.Response)) return;
        const body = await response.json().catch(() => null);
        if (version !== districtVersion) return;
        if (!response.ok || !Array.isArray(body?.regions)) { status('#district-status', errorFor(response.status, body).message, true); return; }
        const matches = body.regions.filter(item => item.placeSearchFilterable === true && item.parentRegionId === region?.regionId);
        $('#district-results').replaceChildren(...matches.map(item => {
          const li = document.createElement('li'), button = document.createElement('button'); button.type = 'button'; button.textContent = `${item.name} 필터 선택`;
          button.addEventListener('click', () => { district = {regionId: item.regionId, name: item.name}; renderDistrict(); status('#district-status', `${item.name} 필터를 적용했습니다.`); }); li.append(button); return li;
        }));
        status('#district-status', matches.length ? `${matches.length}개의 구·군을 찾았습니다.` : '선택 지역에 속한 구·군 필터가 없습니다.');
      } catch (_) { if (version === districtVersion) status('#district-status', MESSAGES.NETWORK_ERROR, true); }
      finally { if (districtPending === controller) districtPending = null; }
    }
    function showStep(step) {
      activeStep = step;
      $('#conditions-workspace').hidden = step !== 1;
      $('#attractions-workspace').hidden = step !== 2;
      $('#hotel-workspace').hidden = step !== 3;
      if (step === 2) map.ensure().then(ok => { if (ok && activeStep === 2) renderAll(); });
      if (step === 3) hotelMap.ensure().then(ok => { if (ok && activeStep === 3) renderAll(); });
    }
    function days() { return Number($('#trip-days').value); }
    function canLeave(step) {
      const selected = store.selected();
      if (step === 2) {
        if (!selected.attractions.length) return '관광지를 한 곳 이상 선택해 주세요.';
        if (selected.attractions.length > days() * 5) return `관광지는 ${days()}일 여행에서 최대 ${days() * 5}곳입니다. 직접 선택을 조정해 주세요.`;
        for (const place of selected.attractions) {
          const values = draft.get(place.kakaoPlaceId);
          if (!values || !values.name.trim() || Array.from(values.name.trim()).length > 50) return '선택한 관광지의 사용자 장소 이름을 1~50자로 입력해 주세요.';
          if (!Number.isInteger(values.stayMinutes) || values.stayMinutes < 30 || values.stayMinutes > 480 || values.stayMinutes % 10 !== 0) return '체류 시간을 30~480분에서 10분 단위로 입력해 주세요.';
        }
        if (!selected.startBoundary || !selected.endBoundary) return '첫날 시작과 마지막 날 종료 장소를 각각 선택해 주세요.';
      }
      if (step === 3 && days() > 1 && !selected.hotel) return '숙소를 직접 선택해 주세요.';
      return null;
    }
    function nextStep(step) { return step === 2 && days() === 1 ? 4 : step + 1; }
    function summary() { return {days: days(), travelMode: document.querySelector('input[name="travel-mode"]:checked')?.value || 'CAR', attractionCount: store.selected().attractions.length}; }
    function attractionContexts() {
      return store.selected().attractions.map(place => {
        const values = draft.get(place.kakaoPlaceId);
        return {clientPlaceId: values.clientPlaceId, displayName: values.name.trim()};
      });
    }
    function estimatePlaces() {
      return store.selected().attractions.map(place => {
        const values = draft.get(place.kakaoPlaceId);
        return {clientPlaceId: values.clientPlaceId, selectionToken: place.selectionToken,
          displayName: values.name.trim(), stayMinutes: values.stayMinutes, day: null, order: null};
      });
    }

    $('#trip-days').addEventListener('change', () => { if (days() === 1) store.unselect(generation, ROLES.HOTEL, store.selected().hotel?.kakaoPlaceId); renderAll(); onSummary?.(); });
    document.querySelectorAll('input[name="travel-mode"]').forEach(input => input.addEventListener('change', () => onSummary?.()));
    document.querySelectorAll('[data-place-role]').forEach(button => button.addEventListener('click', () => {
      cancelRequest(); role = button.dataset.placeRole;
      document.querySelectorAll('[data-place-role]').forEach(tab => tab.setAttribute('aria-pressed', String(tab === button)));
      $('#attraction-controls').hidden = role !== ROLES.ATTRACTION;
      $('#boundary-search-form').hidden = role !== ROLES.TRAVEL_BOUNDARY;
      $('#attraction-selected').hidden = role !== ROLES.ATTRACTION;
      $('#boundary-selected').hidden = role !== ROLES.TRAVEL_BOUNDARY;
      status('#place-status', ''); renderAll();
    }));
    for (const [workspaceSelector, mapView] of [['#attractions-workspace', map], ['#hotel-workspace', hotelMap]]) {
      const workspace = $(workspaceSelector);
      workspace.querySelectorAll('[data-place-view]').forEach(button => button.addEventListener('click', () => {
        const view = button.dataset.placeView;
        workspace.querySelector('.place-layout').dataset.mobileView = view;
        workspace.querySelectorAll('[data-place-view]').forEach(option => option.setAttribute('aria-pressed', String(option === button)));
        if (view === 'map') mapView.ensure().then(ok => { if (ok) renderAll(); });
      }));
    }
    $('#district-form').addEventListener('submit', findDistrict);
    $('#attraction-search-form').addEventListener('submit', event => { event.preventDefault(); send(ROLES.ATTRACTION); });
    $('#boundary-search-form').addEventListener('submit', event => { event.preventDefault(); send(ROLES.TRAVEL_BOUNDARY); });
    $('#hotel-search-form').addEventListener('submit', event => { event.preventDefault(); send(ROLES.HOTEL); });
    $('#place-search-map').addEventListener('click', () => { const center = map.center(); if (center) send(role, 1, center); else status('#place-status', '지도를 먼저 불러와 주세요.', true); });
    for (const [prefix, roleForPage] of [['place', null], ['hotel', ROLES.HOTEL]]) {
      $(`#${prefix}-prev-page`).addEventListener('click', () => { const key = roleForPage || role; if (pages[key]?.page > 1) send(key, pages[key].page - 1); });
      $(`#${prefix}-next-page`).addEventListener('click', () => { const key = roleForPage || role; if (pages[key]?.hasNext) send(key, pages[key].page + 1); });
    }
    window.addEventListener('pagehide', () => reset());
    renderAll();
    return {setRegion, showStep, canLeave, nextStep, summary, attractionContexts, estimatePlaces, reset, cancelRequest, selected: () => store.selected()};
  }

  return Object.freeze({MESSAGES, errorFor, searchBody, hotelBody, validPage, loadMapSdk, createMapView, mount});
});
