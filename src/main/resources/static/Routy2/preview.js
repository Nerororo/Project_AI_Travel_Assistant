/* Standalone design preview. No provider calls, IDs, authentication, or schedule algorithms.
 * Replace the presentation seam described in INTEGRATION.md when the APIs are ready.
 */
'use strict';
const PreviewRules = Object.freeze({
  searchCities(cities, query) {
    const term = query.trim().normalize('NFKC').toLocaleLowerCase('en');
    if (!term) return [];
    return Object.entries(cities).filter(([key, city]) =>
      `${city.name} ${key}`.normalize('NFKC').toLocaleLowerCase('en').includes(term));
  },
  validateConditions(value) {
    const errors = [];
    const validDate = date => /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0,10) === date;
    if (!validDate(value.startDate) || !validDate(value.endDate)) errors.push(['startDate', '출발일과 돌아오는 날을 입력해 주세요.']);
    else {
      const days = (Date.parse(value.endDate) - Date.parse(value.startDate)) / 86400000 + 1;
      if (days < 1 || days > 14) errors.push(['endDate', '여행 기간은 출발일부터 1~14일이어야 해요.']);
    }
    if (!/^\d{2}:\d{2}$/.test(value.dailyStartTime) || !/^\d{2}:\d{2}$/.test(value.dailyEndTime) || value.dailyStartTime >= value.dailyEndTime) errors.push(['dailyEndTime', '하루 종료 시각은 시작 시각보다 늦어야 해요.']);
    if (value.mealTravelBufferMinutes === '' || !Number.isInteger(Number(value.mealTravelBufferMinutes)) || Number(value.mealTravelBufferMinutes) < 0 || Number(value.mealTravelBufferMinutes) > 60) errors.push(['mealTravelBufferMinutes', '식사 이동 여유는 한쪽 기준 정수 0~60분이에요.']);
    const foods = value.foods.split(',').map(x => x.trim());
    if (foods.length < 1 || foods.length > 5 || foods.some(x => x.length < 1 || x.length > 50) || new Set(foods).size !== foods.length) errors.push(['foods', '음식은 쉼표로 구분해 중복 없이 1~5개, 각각 1~50자로 입력해 주세요.']);
    return errors;
  },
  escape(value) { return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); },
  errorMessage(code) {
    return ({VALIDATION_FAILED:'입력값을 다시 확인해 주세요. 이전 선택은 유지돼요.', PLAN_CAPACITY_EXCEEDED:'선택한 날짜의 시간 안에 모두 담기 어려워요. 장소의 체류 시간이나 날짜 배정을 조정해 주세요.', PLACE_NOT_FOUND:'선택한 장소를 찾을 수 없어요. 목적지나 장소를 다시 선택해 주세요.', TRAVEL_PLAN_NOT_FOUND:'이 일정을 찾을 수 없어요. 내 여행 목록에서 다른 일정을 열어 주세요.', MEAL_SLOT_NOT_FOUND:'이 날짜에는 선택한 식사 시간이 없어요. 다른 식사 슬롯을 선택해 주세요.', FORBIDDEN:'이 일정에 접근할 수 없어요. 본인의 여행 목록을 확인해 주세요.', UNAUTHORIZED:'로그인이 필요해요. 로그인 후 내 여행을 다시 열어 주세요.', NO_MATCHING_CANDIDATES:'검색한 범위에서 조건에 맞는 음식점을 찾지 못했어요. 다른 음식으로 검색해 보세요.', NO_TIME_FEASIBLE_CANDIDATES:'음식점 후보는 있지만 현재 식사 시간에 다녀오기 어려워요. 일정 수정에서 이동 여유나 날짜 배정을 조정해 보세요.', AI_RESPONSE_INVALID:'도시 추천 결과를 확인할 수 없어요. 다시 시도하거나 도시를 직접 선택해 주세요.', AI_UNAVAILABLE:'도시 추천 연결이 잠시 어려워요. 다시 시도하거나 도시를 직접 선택해 주세요.', PLACE_PROVIDER_UNAVAILABLE:'장소 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', ROUTE_PROVIDER_UNAVAILABLE:'이동 정보를 불러오지 못했어요. 기존 일정은 그대로 유지돼요.', SAVE_FAILED:'변경 내용을 저장하지 못했어요. 기존 일정이 유지되며 다시 시도할 수 있어요.'})[code] || '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
  }
});
if (typeof module !== 'undefined') module.exports = PreviewRules;

if (typeof document !== 'undefined') (() => {
  const $ = selector => document.querySelector(selector);
  const esc = PreviewRules.escape;
  const cities = {busan:{name:'부산',reason:'바다 산책과 도시의 풍경을 함께 만나기 좋은 도시',places:['해운대 해수욕장','동백섬','감천문화마을','부산시민공원']},seoul:{name:'서울',reason:'궁궐의 느린 시간과 도심의 활기가 이어지는 도시',places:['경복궁','서울숲','북촌한옥마을','남산공원']},jeju:{name:'제주',reason:'오름과 바다를 따라 나만의 속도로 둘러보는 도시',places:['성산일출봉','한라수목원','함덕해수욕장','제주돌문화공원']}};
  const labels = ['목적지','도시 추천','여행 조건','방문 장소','호텔','일정','날짜 수정','음식점','내 여행'];
  const defaultConditions = () => ({startDate:'2026-10-01',endDate:'2026-10-03',dailyStartTime:'10:00',dailyEndTime:'21:00',mealTravelBufferMinutes:'15',foods:'돼지국밥, 회'});
  // UI-only example choices; these keys must never be used as Google Place IDs.
  const state = {step:0, mode:'city', city:'busan',conditions:defaultConditions(),selected:[0,1,2,3],required:[0,2],stays:[90,120,90,120],hotel:null,plan:false,day:0,assignments:[0,0,1,2],loggedIn:false,authMode:'login',busy:false,scenario:'success',restaurantScenario:'success',mapFailed:false};
  state.cityQuery = '';
  const planner = $('#planner'); const utility = $('#utility'); let requestVersion = 0; let toastTimer;
  const notify = message => { $('#toast').textContent=message; $('#toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').hidden=true,4200); };
  function feedback(message, success=false) {const box=$('#feedback');box.textContent=message;box.className=`feedback${success?' success':''}`;box.hidden=false;}
  function focusTitle() {$('#planner-title').focus({preventScroll:true});}
  function openPlanner(step=0) {state.step=step; render(); if(!planner.open) planner.showModal(); focusTitle();}
  function closePlanner() {requestVersion++;state.busy=false;planner.close();}
  function transition(step) {state.step=step;state.scenario='success';render();focusTitle();}
  function setCity(key) {if(!cities[key])return;if(state.city!==key){state.city=key;state.selected=[0,1,2,3];state.required=[0,2];state.hotel=null;} $('#quick-destination').value=key;}
  function title(heading,description) {return `<h3 class="pane-title">${heading}</h3><p class="pane-description">${description}</p>`;}
  function field(name,label,type='text',note='',extra='') {return `<label class="field ${name==='foods'?'full':''}" for="${name}">${label}<input id="${name}" name="${name}" type="${type}" value="${esc(state.conditions[name])}" ${extra} aria-describedby="${name}-hint field-error" required><small id="${name}-hint">${note}</small></label>`;}
  function scenarios(options) {return `<label class="preview-controls">미리보기 응답 <select id="scenario">${[['success','성공'],...options].map(([key,label])=>`<option value="${key}" ${state.scenario===key?'selected':''}>${label}</option>`).join('')}</select><span>실제 요청 없이 화면 상태만 재현합니다.</span></label>`;}
  function cityCards() {return `<div class="choice-grid">${Object.entries(cities).map(([key,city],i)=>`<button class="choice ${state.city===key?'selected':''}" data-select-city="${key}" aria-pressed="${state.city===key}"><span class="choice-symbol" aria-hidden="true">${['≈','⌂','△'][i]}</span><span class="tag">대한민국 · 예시 후보</span><strong>${city.name}</strong><small>${city.reason}</small></button>`).join('')}</div>`;}
  function citySearch() {
    return `<section class="city-search" aria-labelledby="city-search-title"><h4 id="city-search-title">${state.step===1?'원하는 도시가 없나요? 직접 검색해 보세요.':'도시 이름으로 직접 찾아보세요.'}</h4><label class="field" for="city-query">도시 검색<input id="city-query" type="search" placeholder="예: 부산, 서울, 제주 또는 Busan" value="${esc(state.cityQuery)}" autocomplete="off" aria-describedby="city-search-hint" aria-controls="city-search-results"><small id="city-search-hint">현재는 부산·서울·제주 예시만 검색합니다. 실제 도시 검색은 Google 연결 후 제공됩니다.</small></label><p id="city-search-status" class="city-search-status" role="status" aria-live="polite"></p><div id="city-search-results" class="city-search-results" role="group" aria-label="도시 검색 결과"></div></section>`;
  }
  function renderCityResults() {
    if (!$('#city-search-results')) return;
    const matches=PreviewRules.searchCities(cities,state.cityQuery);
    $('#city-search-status').textContent=!state.cityQuery.trim()?'도시 이름을 입력한 뒤 검색 결과에서 선택해 주세요.':matches.length?`${matches.length}개의 예시 도시를 찾았어요. Tab으로 결과로 이동하고 Enter로 선택할 수 있어요.`:'예시 목록에서 찾지 못했어요. 실제 도시의 존재 여부와는 관계없습니다. 부산·서울·제주로 체험해 보세요.';
    $('#city-search-results').innerHTML=matches.map(([key,city])=>`<button type="button" class="city-search-result" data-search-city="${key}" aria-pressed="${state.city===key}"><span><strong>${esc(city.name)}</strong><small>대한민국 · 검색 예시</small></span><span>${state.city===key?'선택됨 ✓':'이 도시 선택'}</span></button>`).join('');
  }
  function render() {
    $('#feedback').hidden=true;$('#planner-title').textContent=labels[state.step];$('#step-caption').textContent='A JOURNEY, MADE YOURS';
    $('#stepper').innerHTML=labels.map((label,i)=>`<span class="step-pill" ${state.step===i?'aria-current="step"':''}><span>${String(i+1).padStart(2,'0')}</span>${label}${i===1&&state.mode==='city'?' · 생략':''}</span>`).join('');
    $('#step-count').textContent=`${state.step+1} / 9`;
    $('#back-button').disabled=state.step===0;$('#next-button').hidden=false;$('#next-button').disabled=false;
    $('#next-button').textContent=({5:'날짜 배정 살펴보기 →',6:'음식점 살펴보기 →',7:'내 여행 살펴보기 →',8:'미리보기 마치기',4:'고정 일정 예시 보기 →'})[state.step]||'다음 단계 →';
    const city=cities[state.city];
    let html='';
    switch(state.step) {
      case 0: html=title('어디에서 시작할까요?','도시를 바로 선택하거나, 국가를 먼저 골라 도시 후보를 만나보세요.')+`<div class="search-tabs"><button class="search-tab ${state.mode==='city'?'selected':''}" data-planner-mode="city" aria-pressed="${state.mode==='city'}">도시 직접 선택</button><button class="search-tab ${state.mode==='country'?'selected':''}" data-planner-mode="country" aria-pressed="${state.mode==='country'}">국가로 추천받기</button></div><br>${state.mode==='city'?cityCards():`<label class="field">여행할 국가<select aria-label="여행할 국가"><option>대한민국</option></select><small>이 미리보기의 국가 예시는 대한민국입니다.</small></label>`}`+scenarios([['PLACE_NOT_FOUND','장소 없음'],['PLACE_PROVIDER_UNAVAILABLE','장소 연결 실패']]);break;
      case 1: html=title('세 가지 도시, 서로 다른 설렘','국가를 선택했을 때 나타나는 도시 추천 화면입니다. 아래 추천 이유는 디자인용 예시입니다.')+cityCards()+citySearch()+scenarios([['AI_UNAVAILABLE','AI 연결 실패'],['AI_RESPONSE_INVALID','AI 결과 오류']]);break;
      case 2: html=title(`${city.name}에서의 시간을 정해요`,'출발일과 하루의 시작, 식사를 위한 여유까지. 이전 단계로 돌아가도 입력이 유지됩니다.')+`<div class="form-grid">${field('startDate','출발일','date','여행은 1~14일')}${field('endDate','돌아오는 날','date','출발일부터 포함하여 계산')}${field('dailyStartTime','하루 시작','time','목적지 현지 시각')}${field('dailyEndTime','하루 종료','time','기본 10:00~21:00')}${field('mealTravelBufferMinutes','식사 이동 여유 · 한쪽','number','식사 60분과 별도인 한쪽 이동 여유','min="0" max="60" step="1"')}${field('foods','먹고 싶은 음식','text','쉼표로 구분 · 중복 없이 1~5개')}</div><p id="field-error" role="alert"></p>`;break;
      case 3: html=title('여행에 담을 장소를 골라요','꼭 가고 싶은 장소는 ‘필수’로 표시하고, 머무를 시간을 조정해 보세요.')+`<label class="field">장소 찾기<input id="place-filter" type="search" placeholder="예시 장소 이름으로 검색" aria-describedby="place-search-note"><small id="place-search-note">현재는 아래 예시 목록만 검색합니다. 실제 Google 자동완성은 연결 예정입니다.</small></label><br><div class="place-list">${city.places.map((name,i)=>`<article class="place-row" data-place-name="${name}"><label class="place-select"><input type="checkbox" data-place="${i}" ${state.selected.includes(i)?'checked':''}>${name}</label><small>${city.name} · 관광 장소 예시</small><div class="place-controls"><label><input type="checkbox" data-required="${i}" ${state.required.includes(i)?'checked':''} ${!state.selected.includes(i)?'disabled':''}> 필수 장소</label><label>체류 <input type="number" min="1" max="1440" step="1" data-stay="${i}" aria-label="${name} 체류 시간" value="${state.stays[i]}"> 분</label></div></article>`).join('')}</div><p id="place-empty" class="info-box" hidden>예시 목록에 없는 장소예요. 다른 이름으로 검색해 주세요.</p>`+scenarios([['PLACE_NOT_FOUND','장소 없음'],['PLACE_PROVIDER_UNAVAILABLE','검색 실패']]);break;
      case 4: html=title('하루의 시작과 끝, 머무를 곳','선택한 관광지를 기준으로 호텔 후보가 표시될 자리입니다. 아래 호텔명·거리·순위는 모두 가상 예시입니다.')+`<div class="info-box">${esc(city.name)} · 선택 장소 ${state.selected.length}개 · 필수 ${state.required.length}개<br>${state.selected.map(i=>esc(city.places[i])).join(' · ')}</div><div class="choice-grid">${['코스트 하우스','시티 가든 스테이','온유 호텔'].map((name,i)=>`<button class="choice ${state.hotel===i?'selected':''}" data-hotel="${i}" aria-pressed="${state.hotel===i}"><span class="choice-symbol" aria-hidden="true">▥</span><span class="tag">${i+1}순위 · 거리 예시</span><strong>${name}</strong><small>선택 관광지까지 거리 합<br>${[18.4,23.1,26.8][i]} km · 가상 숙소</small></button>`).join('')}</div><div class="info-box">다음 화면은 부산 10월 1~3일의 고정 시간표입니다. 입력을 바탕으로 계산한 결과가 아니며, 입력 내용은 이후 실제 연결을 위해 별도로 유지합니다.</div>`+scenarios([['PLAN_CAPACITY_EXCEEDED','시간 용량 초과'],['ROUTE_PROVIDER_UNAVAILABLE','이동 정보 실패'],['SAVE_FAILED','저장 실패']]);break;
      case 5: html=renderPlan();break;
      case 6: html=title('하루의 순서를 다시 생각해요','날짜 배정 입력 예시입니다. 실제 재계산은 서버 연결 후 지원하며, 여기서는 실패 시 기존 고정 일정을 보존하는 동작을 확인할 수 있어요.')+`<div id="assignment-list">${cities.busan.places.map((name,i)=>`<label class="assignment-row">${name}<select data-assignment="${i}" aria-label="${name} 날짜 배정">${[0,1,2].map(day=>`<option value="${day}" ${state.assignments[i]===day?'selected':''}>10월 ${day+1}일 · Day ${day+1}</option>`).join('')}</select></label>`).join('')}</div><div class="info-box">기존 예시 일정: Day 1 해운대·동백섬 / Day 2 감천문화마을 / Day 3 부산시민공원</div>`+scenarios([['PLAN_CAPACITY_EXCEEDED','시간 용량 초과'],['ROUTE_PROVIDER_UNAVAILABLE','재계산 실패'],['SAVE_FAILED','저장 실패']])+`<button class="button outline" data-action="recalculate">재계산 응답 미리보기</button>`;break;
      case 7: html=title('이번 식사는, 어떤 맛으로?','고정 일정에 있는 식사 슬롯을 선택해 검색 결과 모양을 살펴보세요. 검색은 시간표를 변경하지 않습니다.')+`<div class="form-grid"><label class="field">일정 날짜<select id="meal-date"><option>2026-10-01</option><option>2026-10-02</option><option>2026-10-03</option></select></label><label class="field">식사 시간<select id="meal-type"><option value="LUNCH">점심 · 12:00~13:00</option><option value="DINNER">저녁 · 18:00~19:00</option></select></label><label class="field full">먹고 싶은 음식<input id="meal-food" value="돼지국밥" maxlength="50" required></label></div><label class="preview-controls">검색 결과 예시<select id="restaurant-scenario"><option value="success">후보 있음</option><option value="NO_MATCHING_CANDIDATES">조건 일치 후보 없음</option><option value="NO_TIME_FEASIBLE_CANDIDATES">시간 충족 후보 없음</option><option value="PLACE_PROVIDER_UNAVAILABLE">장소 제공자 장애</option><option value="ROUTE_PROVIDER_UNAVAILABLE">이동 제공자 장애</option><option value="MEAL_SLOT_NOT_FOUND">식사 슬롯 없음</option><option value="TRAVEL_PLAN_NOT_FOUND">계획 없음</option></select></label><button class="button" data-action="restaurants">음식점 예시 검색</button><div id="restaurant-results" aria-live="polite"></div>`;break;
      case 8: html=renderAccount();break;
    }
    $('#planner-body').innerHTML=html;
    renderCityResults();
    $('#stepper [aria-current]').scrollIntoView({block:'nearest',inline:'nearest'});
  }
  function renderPlan() {
    const stops=[ [['10:00','코스트 하우스에서 출발','숙소 · H'],['10:05','해운대 해수욕장','1 · 90분'],['12:00','점심 시간','13:00까지 · 60분'],['13:25','동백섬','2 · 120분'],['18:00','저녁 시간','19:00까지 · 60분'],['19:15','코스트 하우스로 돌아오기','숙소 · H']], [['10:00','코스트 하우스에서 출발','숙소 · H'],['10:30','감천문화마을','1 · 90분'],['12:00','점심 시간','13:00까지 · 60분'],['18:00','저녁 시간','19:00까지 · 60분'],['19:15','코스트 하우스로 돌아오기','숙소 · H']], [['10:00','코스트 하우스에서 출발','숙소 · H'],['10:15','부산시민공원','1 · 120분'],['12:30','점심 시간','13:30까지 · 60분'],['18:00','저녁 시간','19:00까지 · 60분'],['19:15','코스트 하우스로 돌아오기','숙소 · H']] ];
    return `<div class="result-heading"><div><span class="section-kicker">A LITTLE ESCAPE</span><h3>바다를 따라, 부산 3일</h3></div><span class="preview-badge">고정 시간표 · 계산·저장 아님</span></div><p class="pane-description">2026.10.01 — 10.03 · 가상 숙소 코스트 하우스</p><div class="mini-summary"><span>현지 시각 10:00~21:00</span><span>식사 이동 여유 한쪽 15분</span><span>관광 장소 총 4개</span></div><div class="result-day-tabs" role="group" aria-label="일정 날짜">${[0,1,2].map(i=>`<button data-day="${i}" aria-pressed="${state.day===i}">Day ${i+1} · 10.${String(i+1).padStart(2,'0')}</button>`).join('')}</div><div class="result-layout"><div class="result-timeline">${stops[state.day].map(([time,name,note])=>`<div class="teaser-stop ${name.includes('시간')?'lunch':''}"><i></i><span>${time}</span><div><strong>${name}</strong><small>${note}</small></div></div>`).join('')}</div><div class="result-map" aria-label="호텔과 관광 장소 번호를 보여주는 개념 지도"><span class="map-island"></span><span class="map-road"></span><span class="map-road two"></span><button class="map-pin pin-h" data-marker="코스트 하우스 · 가상 숙소" aria-label="호텔 정보">H</button><button class="map-pin pin-a" data-marker="${['해운대 해수욕장','감천문화마을','부산시민공원'][state.day]}" aria-label="첫 번째 관광 장소 정보">1</button>${state.day===0?'<button class="map-pin pin-b" data-marker="동백섬" aria-label="두 번째 관광 장소 정보">2</button>':''}<div class="map-label">개념 지도 · 실제 위치·도로 경로 아님<br><span id="marker-label">번호를 눌러 장소를 확인하세요.</span></div>${state.mapFailed?'<div class="map-unavailable"><strong>지도를 불러오지 못했어요</strong><p>시간표는 계속 확인할 수 있습니다.</p><br><button class="button outline" data-action="map-retry">지도 다시 보기</button></div>':''}</div></div><div class="meal-buttons"><button class="button outline" data-action="meal">식사 슬롯에서 음식점 찾기</button><button class="text-button" data-action="map-failure">지도 실패 상태 보기</button></div><div class="info-box">영업시간과 실시간 교통은 반영하지 않습니다. 빈 시간은 자유 시간입니다.<br>실제 화면에서는 서버가 반환한 전체 날짜·방문 시각·식사 슬롯과 좌표를 표시합니다.</div>`;
  }
  function renderAccount() {
    if(!state.loggedIn) return title('여행을 다시 만나는 방법','실제 회원정보를 입력하지 마세요. 로그인·가입 계약은 U1에서 확정되며 여기서는 화면 상태만 전환합니다.')+`<div class="auth-tabs"><button data-auth-mode="login" aria-pressed="${state.authMode==='login'}">로그인</button><button data-auth-mode="signup" aria-pressed="${state.authMode==='signup'}">회원가입</button></div><div class="form-grid"><label class="field full">이메일 입력 영역<input placeholder="실제 연결 후 입력" disabled></label><label class="field full">비밀번호 입력 영역<input type="password" placeholder="실제 연결 후 입력" disabled></label></div><div class="info-box">회원가입·로그인 완료 상태를 체험합니다. 계정 생성, 비밀번호 처리, 토큰 저장은 하지 않습니다.</div><button class="button" data-action="demo-login">${state.authMode==='signup'?'가입 완료':'로그인 완료'} 상태 보기</button>`;
    return title('내 여행, 다음에도 이어서','현재 탭에서만 볼 수 있는 예시 목록입니다. 새로고침하면 초기화되며 실제 저장 목록이 아닙니다.')+`<div class="result-heading"><span class="preview-badge">로그인 상태 예시</span><button class="text-button" data-action="logout">로그아웃 상태 보기</button></div>${state.plan?'<article class="saved-row"><div><strong>바다를 따라, 부산 3일</strong><small>2026.10.01 — 10.03 · 예시 일정</small></div><button class="button small" data-action="reopen">일정 다시 열기</button><button class="text-button" data-action="delete">예시 목록에서 지우기</button></article>':'<div class="empty-state"><span>▤</span><h3>아직 담은 여행이 없어요</h3><p>일정 예시를 열면 이 목록에 표시돼요.</p><button class="button" data-action="sample">일정 예시 열기</button></div>'}<div class="preview-controls"><button class="text-button" data-action="forbidden">소유권 오류 보기</button><button class="text-button" data-action="missing">계획 없음 보기</button></div>`;
  }
  async function simulate(button, action, code='success') {
    if(state.busy)return;state.busy=true; const version=++requestVersion; const old=button.innerHTML;button.disabled=true;button.innerHTML='<span class="busy-indicator" aria-hidden="true"></span> 불러오는 중';$('#planner-body').setAttribute('aria-busy','true');
    $('#back-button').disabled=true;$('#next-button').disabled=true;
    // Pause represents only a visible loading state, never a network operation.
    await new Promise(resolve=>setTimeout(resolve,650));
    if(version!==requestVersion)return;
    state.busy=false;button.disabled=false;button.innerHTML=old;$('#back-button').disabled=state.step===0;$('#next-button').disabled=false;$('#planner-body').removeAttribute('aria-busy');
    if(code!=='success'){feedback(`상태 예시 · ${PreviewRules.errorMessage(code)}`);return;}
    action();
  }
  function next() {
    if(state.busy)return;
    if(state.step===8){closePlanner();return;}
    if(state.step===2){
      const errors=PreviewRules.validateConditions(state.conditions);
      $('#planner-body').querySelectorAll('[aria-invalid]').forEach(x=>x.removeAttribute('aria-invalid'));
      if(errors.length){const [name,message]=errors[0];$(`#${name}`).setAttribute('aria-invalid','true');$('#field-error').textContent=message;$(`#${name}`).focus();return;}
    }
    if(state.step===3){
      if(!state.selected.length){feedback('방문할 장소를 한 개 이상 골라 주세요.');return;}
      const bad=state.selected.find(i=>!Number.isInteger(Number(state.stays[i]))||Number(state.stays[i])<=0);
      if(bad!==undefined){feedback('체류 시간은 양의 정수 분으로 입력해 주세요.');$(`[data-stay="${bad}"]`).focus();return;}
    }
    if(state.step===4&&state.hotel===null){feedback('하루의 출발·도착 기준이 될 호텔을 골라 주세요.');return;}
    const target=state.step===0&&state.mode==='city'?2:state.step+1;
    simulate($('#next-button'),()=>{if(state.step===4)state.plan=true;transition(target);},state.scenario);
  }
  $('#next-button').addEventListener('click',next);
  $('#back-button').addEventListener('click',()=>{if(!state.busy)transition(state.step===2&&state.mode==='city'?0:Math.max(0,state.step-1));});
  planner.addEventListener('cancel',()=>{requestVersion++;state.busy=false;});
  planner.addEventListener('close',()=>$('#planner-body').removeAttribute('aria-busy'));
  $('#quick-search').addEventListener('submit',event=>{event.preventDefault();state.conditions.startDate=$('#quick-start').value;state.conditions.endDate=$('#quick-end').value;const value=$('#quick-destination').value;if(value==='kr')state.mode='country';else {setCity(value);state.mode='city';}openPlanner(0);});
  function setQuickMode(mode){state.mode=mode;document.querySelectorAll('[data-mode]').forEach(button=>{const active=button.dataset.mode===mode;button.classList.toggle('selected',active);button.setAttribute('aria-pressed',active);});$('#quick-destination').value=mode==='country'?'kr':state.city;}
  document.addEventListener('click',event=>{
    const button=event.target.closest('button');if(!button)return;
    if(button.dataset.mode){setQuickMode(button.dataset.mode);return;}
    if(button.dataset.city){setCity(button.dataset.city);setQuickMode('city');openPlanner(2);return;}
    if(state.busy && button.dataset.action!=='close')return;
    if(button.dataset.searchCity){
      const key=button.dataset.searchCity;
      setCity(key);
      if(state.mode==='country') $('#quick-destination').value='kr';
      render();
      feedback(`${cities[key].name}을 선택했어요. ‘다음 단계’를 눌러 여행 조건을 입력해 주세요.`,true);
      $(`[data-search-city="${key}"]`).focus();
      return;
    }
    if(button.dataset.plannerMode){state.mode=button.dataset.plannerMode;setQuickMode(state.mode);render();return;}
    if(button.dataset.selectCity){setCity(button.dataset.selectCity);render();return;}
    if(button.dataset.hotel!==undefined){state.hotel=Number(button.dataset.hotel);render();return;}
    if(button.dataset.day!==undefined){state.day=Number(button.dataset.day);render();return;}
    if(button.dataset.marker){$('#marker-label').textContent=button.dataset.marker;return;}
    if(button.dataset.authMode){state.authMode=button.dataset.authMode;render();return;}
    const action=button.dataset.action;
    switch(action){
      case 'start':openPlanner(0);break;
      case 'recommend':setQuickMode('country');openPlanner(0);break;
      case 'sample':state.plan=true;state.day=0;openPlanner(5);break;
      case 'saved':case 'auth':openPlanner(8);break;
      case 'close':closePlanner();break;
      case 'close-utility':utility.close();break;
      case 'guide':openUtility('작은 선택이 모여, 하나의 여행',`<p>국가나 도시를 선택한 뒤, 여행 조건과 장소를 차례로 정해 보세요.</p><ol>${labels.map(label=>`<li>${label}</li>`).join('')}</ol><p>도시를 직접 정하면 도시 추천 단계는 건너뜁니다. 원할 때 창을 닫아도 현재 탭의 입력은 유지됩니다.</p>`);break;
      case 'states':openUtility('화면 상태 살펴보기','<p>여행 만들기를 열고 각 단계의 ‘미리보기 응답’을 선택하세요. 로딩 이후 입력 오류, 장소 없음, 제공자 장애를 재현할 수 있습니다.</p><p>일정에서는 지도 실패, 날짜 수정에서는 재계산·저장 실패, 음식점에서는 두 가지 빈 결과를 확인할 수 있어요. 내 여행에서는 소유권 오류와 계획 없음 상태를 살펴볼 수 있습니다.</p><p>예시 데이터는 서버 응답이 아닙니다. 외부 API 및 실제 로그인·저장은 연결 전입니다.</p>');break;
      case 'map-failure':state.mapFailed=true;render();break;
      case 'map-retry':state.mapFailed=false;render();break;
      case 'meal':transition(7);break;
      case 'recalculate':simulate(button,()=>feedback('입력한 날짜 배정은 유지했습니다. 이 미리보기에서는 고정 시간표를 바꾸지 않습니다. 실제 재계산 결과 교체는 서버 연결 후 지원합니다.',true),state.scenario);break;
      case 'restaurants':searchRestaurants(button);break;
      case 'adjust':transition(6);break;
      case 'retry-food':$('#meal-food').focus();break;
      case 'demo-login':simulate(button,()=>{state.loggedIn=true;render();});break;
      case 'logout':state.loggedIn=false;render();break;
      case 'reopen':transition(5);break;
      case 'delete':openUtility('예시 목록에서 지울까요?','<p>현재 탭의 예시 목록만 비웁니다. 실제 저장된 일정은 없습니다.</p><br><button class="button danger" data-action="confirm-delete">예시 항목 지우기</button>');break;
      case 'confirm-delete':state.plan=false;utility.close();render();break;
      case 'forbidden':feedback(PreviewRules.errorMessage('FORBIDDEN'));break;
      case 'missing':feedback(PreviewRules.errorMessage('TRAVEL_PLAN_NOT_FOUND'));break;
    }
  });
  function openUtility(heading,body){$('#utility-title').textContent=heading;$('#utility-body').innerHTML=`<div class="utility-copy">${body}</div>`;utility.showModal();$('#utility-title').focus();}
  function searchRestaurants(button){
    const food=$('#meal-food').value.trim();if(!food||food.length>50){feedback('음식은 1~50자로 입력해 주세요.');$('#meal-food').focus();return;}
    $('#feedback').hidden=true;$('#restaurant-results').textContent='';const code=$('#restaurant-scenario').value;
    simulate(button,()=>{
      const target=$('#restaurant-results');
      if(code.startsWith('NO_')){const time=code==='NO_TIME_FEASIBLE_CANDIDATES';target.innerHTML=`<div class="empty-state"><span aria-hidden="true">⌕</span><h3>${time?'지금 시간에 맞는 후보가 없어요':'검색 조건에 맞는 후보가 없어요'}</h3><p>${PreviewRules.errorMessage(code)}</p><button class="button outline" data-action="${time?'adjust':'retry-food'}">${time?'일정 조건 살펴보기':'다른 음식으로 찾기'}</button></div>`;}
      else if(code!=='success')feedback(`상태 예시 · ${PreviewRules.errorMessage(code)}`);
      else target.innerHTML=`<div class="info-box">‘${esc(food)}’ 검색 결과의 표시 예시입니다. 실제 음식 일치·이동 가능 여부는 검증되지 않았습니다.</div>${['바람 식탁','골목의 온기'].map((name,i)=>`<article class="restaurant-row"><div><strong>${i+1}. ${name}</strong><small>가상 음식점 · 인접 후보 표시 예시</small></div><div><strong>+${[12,18][i]}분</strong><small>동선 이탈 예시 · ${[.8,1.2][i]}km</small></div></article>`).join('')}`;
    });
  }
  document.addEventListener('input',event=>{
    const input=event.target;
    if(input.id==='city-query'){state.cityQuery=input.value;renderCityResults();}
    if(input.name && Object.hasOwn(state.conditions,input.name))state.conditions[input.name]=input.value;
    if(input.dataset.stay!==undefined)state.stays[Number(input.dataset.stay)]=input.value;
    if(input.id==='place-filter'){let count=0;document.querySelectorAll('[data-place-name]').forEach(row=>{row.hidden=!row.dataset.placeName.includes(input.value.trim());if(!row.hidden)count++;});$('#place-empty').hidden=count!==0;}
  });
  document.addEventListener('change',event=>{
    const input=event.target;
    if(input.id==='quick-destination'){if(input.value!=='kr')setCity(input.value);setQuickMode(input.value==='kr'?'country':'city');}
    if(input.id==='scenario')state.scenario=input.value;
    if(input.dataset.place!==undefined){const i=Number(input.dataset.place);state.selected=input.checked?[...state.selected,i].sort():state.selected.filter(x=>x!==i);if(!input.checked)state.required=state.required.filter(x=>x!==i);state.hotel=null;render();}
    if(input.dataset.required!==undefined){const i=Number(input.dataset.required);state.required=input.checked?[...new Set([...state.required,i])]:state.required.filter(x=>x!==i);}
    if(input.dataset.assignment!==undefined)state.assignments[Number(input.dataset.assignment)]=Number(input.value);
  });
  const reduced=matchMedia('(prefers-reduced-motion: reduce)');
  $('#motion-toggle').addEventListener('click',()=>{const off=document.body.classList.toggle('motion-off');$('#motion-toggle').setAttribute('aria-pressed',String(off));$('#motion-toggle').textContent=off?'모션 켜기':'모션 끄기';$('#motion-toggle').setAttribute('aria-label',off?'장식 애니메이션 켜기':'장식 애니메이션 끄기');});
  $('.hero').addEventListener('pointermove',event=>{if(reduced.matches||document.body.classList.contains('motion-off')||!matchMedia('(hover:hover)').matches)return;const rect=$('.hero').getBoundingClientRect();$('#hero-art').style.setProperty('--ry',`${((event.clientX-rect.left)/rect.width-.5)*9}deg`);$('#hero-art').style.setProperty('--rx',`${-((event.clientY-rect.top)/rect.height-.5)*6}deg`);});
  $('.hero').addEventListener('pointerleave',()=>{$('#hero-art').style.setProperty('--rx','0deg');$('#hero-art').style.setProperty('--ry','0deg');});
  // Start with the country selector visible; keep the original direct-city tab available.
  setQuickMode('country');
})();
