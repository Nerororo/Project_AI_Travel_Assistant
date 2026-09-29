'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyPlaceSelectionState = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const ROLES = Object.freeze({ATTRACTION: 'ATTRACTION', HOTEL: 'HOTEL', TRAVEL_BOUNDARY: 'TRAVEL_BOUNDARY'});
  const BOUNDARY_SLOTS = Object.freeze({START: 'START', END: 'END'});
  const roleValues = new Set(Object.values(ROLES));

  function text(value, name) {
    if (typeof value !== 'string' || !value.trim()) throw new TypeError(`${name} is required`);
    return value;
  }

  function candidate(value, role) {
    if (!value || typeof value !== 'object' || !roleValues.has(role)) throw new TypeError('Invalid place candidate');
    const latitude = value.latitude;
    const longitude = value.longitude;
    if (!Number.isFinite(latitude) || latitude < 33 || latitude > 39
        || !Number.isFinite(longitude) || longitude < 124 || longitude > 132) {
      throw new TypeError('Invalid place coordinates');
    }
    const placeUrl = text(value.placeUrl, 'placeUrl');
    let parsedUrl;
    try { parsedUrl = new URL(placeUrl); } catch (_) { throw new TypeError('Invalid placeUrl'); }
    if (parsedUrl.protocol !== 'https:' || parsedUrl.hostname !== 'place.map.kakao.com') {
      throw new TypeError('Invalid placeUrl');
    }
    const suggestedStayMinutes = role === ROLES.ATTRACTION ? value.suggestedStayMinutes : value.suggestedStayMinutes ?? null;
    if (role !== ROLES.ATTRACTION && suggestedStayMinutes !== null) throw new TypeError('Non-attraction stay suggestion must be null');
    if (role === ROLES.ATTRACTION
        && (!Number.isInteger(suggestedStayMinutes) || suggestedStayMinutes < 30
          || suggestedStayMinutes > 480 || suggestedStayMinutes % 10 !== 0)) {
      throw new TypeError('Invalid attraction stay suggestion');
    }
    return Object.freeze({
      kakaoPlaceId: text(value.kakaoPlaceId, 'kakaoPlaceId'),
      placeUrl,
      providerDisplayName: text(value.providerDisplayName, 'providerDisplayName'),
      address: text(value.address, 'address'),
      latitude,
      longitude,
      suggestedStayMinutes,
      selectionToken: text(value.selectionToken, 'selectionToken')
    });
  }

  function createStore() {
    let authenticatedSessionId = null;
    let regionId = null;
    let generation = 0;
    let candidates = new Map();
    let attractions = new Map();
    let hotel = null;
    let startBoundary = null;
    let endBoundary = null;

    function erase() {
      candidates = new Map();
      attractions = new Map();
      hotel = null;
      startBoundary = null;
      endBoundary = null;
    }

    function context() {
      return Object.freeze({authenticatedSessionId, regionId, generation});
    }

    function setContext(nextSessionId, nextRegionId) {
      if (!Number.isSafeInteger(nextSessionId) || nextSessionId <= 0) throw new TypeError('authenticatedSessionId must be positive');
      text(nextRegionId, 'regionId');
      if (authenticatedSessionId !== nextSessionId || regionId !== nextRegionId) {
        erase();
        authenticatedSessionId = nextSessionId;
        regionId = nextRegionId;
        generation++;
      }
      return context();
    }

    function current(expectedGeneration) {
      return authenticatedSessionId !== null && regionId !== null && expectedGeneration === generation;
    }

    function acceptSearchResults(expectedGeneration, role, places) {
      if (!current(expectedGeneration)) return false;
      if (!roleValues.has(role) || !Array.isArray(places)) throw new TypeError('Invalid search result');
      const next = new Map();
      for (const place of places) {
        const copy = candidate(place, role);
        if (next.has(copy.kakaoPlaceId)) throw new TypeError('Duplicate kakaoPlaceId');
        next.set(copy.kakaoPlaceId, copy);
      }
      candidates.set(role, next);
      return true;
    }

    function select(expectedGeneration, role, kakaoPlaceId) {
      if (!current(expectedGeneration)) return false;
      const selected = candidates.get(role)?.get(kakaoPlaceId);
      if (!selected) return false;
      if (role === ROLES.ATTRACTION) attractions.set(kakaoPlaceId, selected);
      else if (role === ROLES.HOTEL) hotel = selected;
      else return false;
      return true;
    }

    function unselect(expectedGeneration, role, kakaoPlaceId) {
      if (!current(expectedGeneration)) return false;
      if (role === ROLES.ATTRACTION) return attractions.delete(kakaoPlaceId);
      if (role === ROLES.HOTEL && hotel?.kakaoPlaceId === kakaoPlaceId) { hotel = null; return true; }
      return false;
    }

    function selectBoundary(expectedGeneration, slot, kakaoPlaceId) {
      if (!current(expectedGeneration)) return false;
      const selected = candidates.get(ROLES.TRAVEL_BOUNDARY)?.get(kakaoPlaceId);
      if (!selected) return false;
      if (slot === BOUNDARY_SLOTS.START) startBoundary = selected;
      else if (slot === BOUNDARY_SLOTS.END) endBoundary = selected;
      else return false;
      return true;
    }

    function unselectBoundary(expectedGeneration, slot) {
      if (!current(expectedGeneration)) return false;
      if (slot === BOUNDARY_SLOTS.START && startBoundary) { startBoundary = null; return true; }
      if (slot === BOUNDARY_SLOTS.END && endBoundary) { endBoundary = null; return true; }
      return false;
    }

    function selected() {
      return Object.freeze({
        attractions: Object.freeze([...attractions.values()]),
        hotel,
        startBoundary,
        endBoundary
      });
    }

    function clear() {
      erase();
      authenticatedSessionId = null;
      regionId = null;
      generation++;
    }

    function attachPageLifecycle(windowLike) {
      if (!windowLike || typeof windowLike.addEventListener !== 'function'
          || typeof windowLike.removeEventListener !== 'function') throw new TypeError('Invalid window');
      const discard = () => clear();
      windowLike.addEventListener('pagehide', discard);
      return () => windowLike.removeEventListener('pagehide', discard);
    }

    return Object.freeze({context, setContext, acceptSearchResults, select, unselect, selectBoundary, unselectBoundary,
      selected, clear,
      complete: clear, cancel: clear, authenticationEnded: clear, attachPageLifecycle});
  }

  return Object.freeze({ROLES, BOUNDARY_SLOTS, createStore});
});
