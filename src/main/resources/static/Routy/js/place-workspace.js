'use strict';

(function (root, factory) {
  const api = factory(root.RoutyPlaceSelectionState || (typeof require === 'function' ? require('./place-selection-state.js') : null));
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyPlaceWorkspace = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (stateModule) {
  const {ROLES, BOUNDARY_SLOTS} = stateModule;
  const SEARCH_SIZE = 10;
  const MESSAGES = Object.freeze({
    REQUEST_TOO_LARGE: '요청 크기가 너무 큽니다. 검색 입력을 줄여 다시 검색해 주세요. 현재 선택은 유지됩니다.',
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
    const code = status === 413 ? 'REQUEST_TOO_LARGE' : Object.hasOwn(MESSAGES, body?.code) ? body.code : status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR';
    const seconds = code === 'RATE_LIMIT_EXCEEDED' && Number.isInteger(body?.retryAfterSeconds) && body.retryAfterSeconds > 0
      ? ` ${body.retryAfterSeconds}초 뒤 다시 시도해 주세요.` : '';
    return {code, message: MESSAGES[code] + seconds};
  }

  function createImageLoader(document, window, client) {
    const cache = new Map(), pending = new Set();
    function clear() { pending.forEach(controller => controller.abort()); pending.clear(); cache.clear(); }
    function append(item, place) {
      if (!place?.kakaoPlaceId || !place.providerDisplayName || !place.address) return;
      let lookup = cache.get(place.kakaoPlaceId);
      if (!lookup) {
        const controller = new AbortController(); pending.add(controller);
        lookup = client.protectedRequest('/api/places/image', {method: 'POST',
          headers: {'Content-Type': 'application/json', Accept: 'application/json', 'Idempotency-Key': window.crypto.randomUUID()},
          body: JSON.stringify({placeName: place.providerDisplayName, address: place.address}), signal: controller.signal})
          .then(response => response instanceof window.Response && response.ok ? response.json() : null)
          .then(body => (Array.isArray(body?.thumbnailUrls) ? body.thumbnailUrls : []).flatMap(value => {
            try {
              const url = new URL(value);
              return url.protocol === 'https:' && (url.hostname === 'kakaocdn.net' || url.hostname.endsWith('.kakaocdn.net')) ? [url.href] : [];
            } catch (_) { return []; }
          }).slice(0, 5)).catch(() => []).finally(() => pending.delete(controller));
        cache.set(place.kakaoPlaceId, lookup);
      }
      lookup.then(urls => {
        if (!urls.length || !item.isConnected) return;
        const gallery = document.createElement('div'); gallery.className = 'place-image-gallery';
        const strip = document.createElement('div'); strip.className = 'place-image-strip';
        const count = document.createElement('span'); count.className = 'place-image-count';
        count.textContent = `1 / ${urls.length}`;
        if (urls.length > 1) {
          strip.tabIndex = 0;
          strip.setAttribute('role', 'group');
          strip.setAttribute('aria-label', `${urls.length}장의 장소 사진. 좌우로 넘겨 보세요.`);
          strip.addEventListener('scroll', () => {
            count.textContent = `${Math.min(urls.length, Math.round(strip.scrollLeft / strip.clientWidth) + 1)} / ${urls.length}`;
          });
          let startX = null, startScroll = 0;
          strip.addEventListener('pointerdown', event => {
            if (event.pointerType !== 'mouse' || event.button !== 0) return;
            startX = event.clientX; startScroll = strip.scrollLeft;
            strip.setPointerCapture(event.pointerId);
            event.preventDefault();
          });
          strip.addEventListener('pointermove', event => {
            if (startX !== null) strip.scrollLeft = startScroll + startX - event.clientX;
          });
          const stopDrag = () => { startX = null; };
          strip.addEventListener('pointerup', stopDrag);
          strip.addEventListener('pointercancel', stopDrag);
        }
        for (const url of urls) {
          const img = document.createElement('img'); img.className = 'place-thumbnail'; img.alt = '';
          img.loading = 'eager'; img.decoding = 'async'; img.referrerPolicy = 'no-referrer'; img.draggable = false;
          img.onload = () => { if (item.isConnected) item.classList.add('has-thumbnail'); };
          img.onerror = () => img.remove();
          img.src = url; strip.append(img);
        }
        gallery.append(strip);
        if (urls.length > 1) gallery.append(count);
        item.append(gallery);
      });
    }
    return {append, clear};
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
    let infoWindows = [];
    let viewportKey = '';
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
      clearMarkers();
      const bounds = new maps.LatLngBounds();
      for (const place of places) {
        const position = new maps.LatLng(place.latitude, place.longitude);
        const selected = selectedIds.has(place.kakaoPlaceId);
        const title = `${selected ? '✓ 선택됨 · ' : ''}${place.providerDisplayName}`;
        const marker = new maps.Marker({map, position, title, zIndex: selected ? 3 : 1, opacity: selected ? 1 : 0.8});
        const label = window.document.createElement('div');
        label.className = 'map-place-label';
        label.textContent = title;
        const info = new maps.InfoWindow({content: label, zIndex: selected ? 4 : 5});
        maps.event.addListener(marker, 'mouseover', () => info.open(map, marker));
        maps.event.addListener(marker, 'mouseout', () => { if (!selected) info.close(); });
        maps.event.addListener(marker, 'click', () => onChoose(place.kakaoPlaceId));
        if (selected) info.open(map, marker);
        infoWindows.push(info);
        markers.push(marker);
        bounds.extend(position);
      }
      const nextKey = JSON.stringify(places.map(place => [place.kakaoPlaceId, place.latitude, place.longitude]).sort());
      if (places.length && nextKey !== viewportKey) map.setBounds(bounds);
      else map.relayout();
      viewportKey = nextKey;
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
    function clearMarkers() { infoWindows.forEach(info => info.close()); infoWindows = []; markers.forEach(marker => marker.setMap(null)); markers = []; }
    function clear() { clearMarkers(); viewportKey = ''; }
    function dispose() { generation++; clear(); map = null; ready = false; element.replaceChildren(); element.classList.remove('is-unavailable'); status.textContent = ''; }
    return {ensure, render, center, bounds, clear, dispose};
  }

  function mount(document, window, client, onSummary) {
    const $ = selector => document.querySelector(selector);
    const store = stateModule.createStore();
    const map = createMapView(window, $('#place-map'), $('#place-map-status'));
    const hotelMap = createMapView(window, $('#hotel-map'), $('#hotel-map-status'));
    const images = createImageLoader(document, window, client);
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
      images.clear();
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
      $('#district-controls').hidden = !['서울특별시', '부산광역시', '대구광역시', '인천광역시', '광주', '광주광역시', '대전광역시', '울산광역시'].includes(region?.name);
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
      const focusedId = list.contains(document.activeElement) ? document.activeElement.dataset.placeId : null;
      list.replaceChildren(...places.map(place => {
        const item = document.createElement('li'); item.className = 'place-result';
        const heading = document.createElement('strong'); heading.textContent = place.providerDisplayName;
        const address = document.createElement('span'); address.textContent = place.address;
        const link = document.createElement('a'); link.href = place.placeUrl; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = '카카오 장소 보기';
        const button = document.createElement('button'); button.type = 'button'; button.className = 'search-result-choice';
        button.dataset.placeId = place.kakaoPlaceId;
        const state = document.createElement('span'); state.className = 'search-result-state';
        state.textContent = selectedIds.has(place.kakaoPlaceId) ? '선택됨' : '';
        const copy = document.createElement('span'); copy.append(heading, address); button.append(copy, state);
        button.setAttribute('aria-pressed', String(selectedIds.has(place.kakaoPlaceId)));
        button.addEventListener('click', () => choose(place.kakaoPlaceId));
        item.append(button, link);
        images.append(item, place);
        return item;
      }));
      if (focusedId) [...list.querySelectorAll('button')].find(button => button.dataset.placeId === focusedId)?.focus({preventScroll: true});
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
      const focusedSlot = $('#boundary-slot-controls').contains(document.activeElement) ? document.activeElement.value : null;
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
      slots.className = 'boundary-slot-picker';
      const legend = document.createElement('legend'); legend.textContent = '어느 장소를 선택할까요?'; slots.append(legend);
      for (const slot of [BOUNDARY_SLOTS.START, BOUNDARY_SLOTS.END]) {
        const label = document.createElement('label'), radio = document.createElement('input');
        radio.type = 'radio'; radio.name = 'boundary-slot'; radio.value = slot; radio.checked = slot === boundarySlot;
        radio.addEventListener('change', () => { boundarySlot = slot; renderAll(); });
        label.append(radio, document.createTextNode(slot === BOUNDARY_SLOTS.START ? ' 첫날 시작' : ' 마지막 날 종료')); slots.append(label);
      }
      $('#boundary-slot-controls').replaceChildren(slots);
      if (focusedSlot) slots.querySelector('input:checked').focus({preventScroll: true});
      const boundaryHeading = document.createElement('h3'); boundaryHeading.textContent = '선택한 시작·종료 장소'; boundary.append(boundaryHeading);
      for (const [slot, place] of [[BOUNDARY_SLOTS.START, selected.startBoundary], [BOUNDARY_SLOTS.END, selected.endBoundary]]) {
        const card = document.createElement('div'); card.className = 'selected-place';
        card.dataset.boundarySlot = slot;
        const label = document.createElement('strong'); label.textContent = slot === BOUNDARY_SLOTS.START ? '첫날 시작 장소' : '마지막 날 종료 장소';
        const name = document.createElement('span'); name.textContent = place?.providerDisplayName || '아직 선택하지 않았어요.';
        const help = document.createElement('p'); help.className = 'field-help'; help.textContent = place ? '장소를 바꾸려면 위에서 검색한 결과를 선택해 주세요.' : '위에서 시작 또는 종료를 고른 뒤 검색 결과의 선택 버튼을 눌러 주세요.';
        card.append(label, name, help);
        if (place) { const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'button button-ghost'; remove.textContent = '선택 해제'; remove.addEventListener('click', () => { store.unselectBoundary(generation, slot); renderAll(); }); card.append(remove); }
        boundary.append(card);
      }
      const hotel = $('#hotel-selected'); hotel.replaceChildren();
      const hotelLabel = document.createElement('strong'); hotelLabel.textContent = selected.hotel ? `선택한 숙소: ${selected.hotel.providerDisplayName}` : '선택한 숙소 없음'; hotel.append(hotelLabel);
      if (selected.hotel) { const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '선택 해제'; remove.addEventListener('click', () => { store.unselect(generation, ROLES.HOTEL, selected.hotel.kakaoPlaceId); renderAll(); }); hotel.append(remove); }
    }
    function renderAll() {
      const selected = store.selected();
      const selectedPlaces = role === ROLES.ATTRACTION ? selected.attractions
        : [boundarySlot === BOUNDARY_SLOTS.START ? selected.startBoundary : selected.endBoundary].filter(Boolean);
      const selectedIds = new Set(selectedPlaces.map(place => place.kakaoPlaceId));
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
          const li = document.createElement('li'), button = document.createElement('button'); button.type = 'button'; button.textContent = item.name;
          li.className = 'place-result'; button.className = 'search-result-choice';
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

  return Object.freeze({MESSAGES, errorFor, searchBody, hotelBody, validPage, loadMapSdk, createMapView, createImageLoader, mount});
});
