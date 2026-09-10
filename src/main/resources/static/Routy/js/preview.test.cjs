'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const rules=require('./preview.js');
const valid=()=>({startDate:'2026-10-01',endDate:'2026-10-03',dailyStartTime:'10:00',dailyEndTime:'21:00',mealTravelBufferMinutes:'15',foods:'돼지국밥, 회'});
test('city search accepts Korean partial names and case-insensitive English keys',()=>{
  const cities={busan:{name:'부산'},seoul:{name:'서울'},jeju:{name:'제주'}};
  for(const query of ['부','부산',' BUSAN ','ｂｕｓａｎ'])assert.deepEqual(rules.searchCities(cities,query).map(([key])=>key),['busan']);
  assert.deepEqual(rules.searchCities(cities,'서울').map(([key])=>key),['seoul']);
  for(const query of ['','   ','도쿄','<script>'])assert.deepEqual(rules.searchCities(cities,query),[]);
  assert.equal(Object.keys(cities).length,3);
});
test('valid conditions and inclusive 1 / 14 day boundaries',()=>{
  for(const endDate of ['2026-10-01','2026-10-14'])assert.deepEqual(rules.validateConditions({...valid(),endDate}),[]);
});
test('reject reversed, missing, impossible, and overlong dates',()=>{
  for(const endDate of ['2026-09-30','2026-10-15','','2026-02-30'])assert.ok(rules.validateConditions({...valid(),endDate}).length);
});
test('accept 0 / 60 minute buffer and reject missing/fractional/out of range',()=>{
  for(const buffer of ['0','60'])assert.deepEqual(rules.validateConditions({...valid(),mealTravelBufferMinutes:buffer}),[]);
  for(const buffer of ['','-1','61','1.5'])assert.ok(rules.validateConditions({...valid(),mealTravelBufferMinutes:buffer}).some(([field])=>field==='mealTravelBufferMinutes'));
});
test('reject duplicate foods, empty food, excessive count and length',()=>{
  for(const foods of ['국밥, 국밥','','국밥,','a,b,c,d,e,f','가'.repeat(51)])assert.ok(rules.validateConditions({...valid(),foods}).some(([field])=>field==='foods'));
});
test('reject equal or reversed daily time',()=>{
  for(const end of ['10:00','09:00',''])assert.ok(rules.validateConditions({...valid(),dailyEndTime:end}).some(([field])=>field==='dailyEndTime'));
});
test('escape user-controlled display content',()=>assert.equal(rules.escape('<img onerror="x"> &'), '&lt;img onerror=&quot;x&quot;&gt; &amp;'));
test('distinguish restaurant empty reasons from provider failure',()=>{
  const messages=['NO_MATCHING_CANDIDATES','NO_TIME_FEASIBLE_CANDIDATES','PLACE_PROVIDER_UNAVAILABLE'].map(rules.errorMessage);
  assert.equal(new Set(messages).size,3);
});
