'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RoutyItineraryDownload = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const template = `<!doctype html><html lang="ko"><head><meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="referrer" content="no-referrer">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
    <title>Routy 여행 일정</title></head><body><div class="itinerary-page">
    <header class="brand-bar"><strong>Routy<span>.</span></strong><span>Route your Trip</span></header>
    <main><header class="hero">
      <div class="hero-copy"><p class="eyebrow">MY JOURNEY / 나만의 여행</p><h1></h1>
        <p class="hero-description">좋아하는 장소를 따라,<br>우리만의 하루가 이어집니다.</p><p class="period"></p>
        <svg class="journey-art" viewBox="0 0 480 180" aria-hidden="true" focusable="false">
          <path class="journey-dash" d="M-20 145 C80 15 180 200 285 105 S395 35 480 55"/>
          <g class="journey-arrow"><path d="M-12-9 13 0-12 9-6 0Z"/></g>
          <circle cx="285" cy="105" r="7"/><circle cx="410" cy="43" r="4"/>
        </svg>
      </div>
      <div class="hero-ticket" aria-hidden="true"><span class="ticket-stamp">MADE BY YOU ↗</span>
        <div class="ticket-frame"><p class="eyebrow">ROUTY JOURNEY CLUB</p><p class="ticket-title">YOUR<br>OWN WAY<span>↗</span></p>
        <div class="ticket-bottom"><p>좋은 하루는,<br>좋은 순서에서.</p><span class="ticket-flower">✳</span></div></div>
      </div>
    </header>
    <div class="facts" aria-label="여행 요약"></div>
    <div class="schedule-heading"><div><p class="eyebrow">THE ITINERARY</p><h2>하루를 채우는 순간들<span>.</span></h2></div><span>차곡차곡, 나만의 여행</span></div>
    <nav labelria-label="일차별 일정 선택"></nav>
    <div class="layout"><div class="days"></div><aside>
      <section class="hotel card"><p class="eyebrow">REST & RECHARGE</p><h2>머무는 곳<span>↗</span></h2><div></div></section>
      <section class="note card"><p class="eyebrow">A LITTLE TRAVEL NOTE</p><h2>좋은 여행은,<br>좋은 흐름에서.</h2>
      <p>이동시간은 예상 시간입니다.<br>잠깐의 여유도 함께 챙겨 주세요.</p>
      <p>장소 옆 아이콘에서<br>카카오 장소 페이지를 열 수 있어요.</p><span class="note-star" aria-hidden="true">✳</span></section></aside></div>
    </main><footer><strong>Routy.</strong><span>좋아하는 장소가 하루의 좋은 흐름이 되도록.</span></footer></div></body></html>`;

  const styles = `
    .itinerary-page{color-scheme:light;--ink:#142018;--muted:#687068;--line:#e3e3da;--blue:#4058f5;--lime:#d8f23d;background:#f7f6f0;color:var(--ink);font-family:system-ui,-apple-system,"Malgun Gothic",sans-serif;line-height:1.6;container-type:inline-size}
    *{box-sizing:border-box}body{margin:0}
    a{color:inherit;text-decoration:none}a:focus-visible{outline:3px solid var(--blue);outline-offset:5px}
    h1,h2,h3,p{margin:0}h1,h2,h3,.memo,.place-name{overflow-wrap:anywhere}main,.brand-bar,footer{max-width:1160px;margin:auto}
    .brand-bar{display:flex;justify-content:space-between;align-items:center;padding:24px 28px 8px}.brand-bar strong{font-size:30px;letter-spacing:-2px}.brand-bar strong span{color:var(--blue)}.brand-bar>span{font-size:9px;letter-spacing:.17em;font-weight:750;color:var(--muted)}
    main{padding:20px 28px 40px}.eyebrow{font-size:10px;letter-spacing:.18em;font-weight:800;color:var(--muted)}
    .hero{display:grid;grid-template-columns:1.25fr 1fr;border-radius:30px;overflow:hidden;box-shadow:0 12px 35px #14201808}.hero-copy{position:relative;isolation:isolate;padding:38px 38px 82px;background:var(--blue);color:white;min-width:0}.hero-copy .eyebrow{color:#e0e5ff;margin-bottom:22px}
    h1{font-size:clamp(30px,4.6cqi,52px);font-weight:850;letter-spacing:-.055em;line-height:1.18;text-wrap:balance;word-break:keep-all;max-width:15ch}.hero-description{margin-top:20px;font-size:14px;line-height:1.8;color:#e3e7ff}.period{display:inline-block;margin-top:24px;padding:7px 13px;border:1px solid #ffffff45;border-radius:99px;background:#ffffff0d;font-size:11px;font-variant-numeric:tabular-nums}
    .journey-art{position:absolute;inset:auto -10% -12px auto;width:85%;height:150px;z-index:-1;overflow:visible;fill:none;stroke:#fff;stroke-width:1.5;opacity:.5}.journey-dash{stroke-dasharray:7 9;animation:journey-dash 12s linear infinite}.journey-arrow{fill:var(--lime);stroke:none;offset-path:path('M-20 145 C80 15 180 200 285 105 S395 35 480 55');animation:journey-flight 7s ease-in-out infinite;offset-distance:48%;offset-rotate:auto}
    @keyframes journey-dash{to{stroke-dashoffset:-160}}@keyframes journey-flight{0%{offset-distance:5%;opacity:0}10%,90%{opacity:1}100%{offset-distance:94%;opacity:0}}
    .hero-ticket{position:relative;display:grid;place-items:center;padding:42px 28px 28px;background-color:var(--lime);background-image:linear-gradient(#14201807 1px,transparent 1px),linear-gradient(90deg,#14201807 1px,transparent 1px);background-size:30px 30px}.ticket-stamp{position:absolute;right:21px;top:18px;padding:4px 9px;border:1px solid #14201860;border-radius:99px;font-size:8px;font-weight:800;letter-spacing:.08em;rotate:7deg}.ticket-frame{width:100%;padding:24px;border:1.5px solid #142018ba;border-radius:24px;background:#f5ffa65c;rotate:-3deg}.ticket-frame .eyebrow{color:#364222;font-size:8px}.ticket-title{position:relative;font-size:clamp(30px,4.3cqi,50px);font-weight:900;letter-spacing:-.06em;line-height:.98;margin:20px 0 26px}.ticket-title>span{position:absolute;right:0;top:-3px;color:var(--blue);font-size:70px;font-weight:400}.ticket-bottom{display:flex;justify-content:space-between;align-items:end;border-top:1px solid #14201840;padding-top:15px}.ticket-bottom p{font-size:12px;font-weight:650;line-height:1.6;letter-spacing:-.04em}.ticket-flower{font-size:47px;line-height:1;color:var(--blue)}
    .facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 0 42px}.fact{padding:20px 22px;border:1px solid var(--line);border-radius:21px;background:white;min-width:0}.fact>span{display:block;font-size:10px;color:var(--muted);margin-bottom:10px}.fact strong{display:block;font-size:24px;letter-spacing:-.045em;font-weight:850;overflow-wrap:anywhere}.fact small{display:block;font-size:10px;color:var(--muted);margin-top:2px}.fact:last-child{background:var(--ink);border-color:var(--ink);color:white}.fact:last-child>span{color:#bcc8be}.fact:last-child small{color:var(--lime)}
    .schedule-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:18px}.schedule-heading .eyebrow{color:var(--blue);font-size:9px;margin-bottom:7px}.schedule-heading h2{font-size:25px;letter-spacing:-.05em}.schedule-heading h2 span{color:var(--blue)}.schedule-heading>span{font-size:11px;color:var(--muted)}
    nav{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 22px;padding:7px;border:1px solid var(--line);border-radius:19px;background:white}nav input{position:absolute;opacity:0;width:1px;height:1px}nav label:focus-within{outline:3px solid var(--blue);outline-offset:2px}nav label{position:relative;cursor:pointer;padding:12px 18px;border-radius:12px;font-size:12px;font-weight:750;transition:background .2s,color .2s}nav label:has(input:checked){background:var(--ink);color:white}nav label:hover{background:#eef0ff;color:var(--blue)}
    .layout{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:22px;align-items:start}.days{display:grid;gap:26px;min-width:0}.day{display:none;border:1px solid var(--line);background:#fbfaf7;border-radius:28px;overflow:hidden;scroll-margin-top:90px}.day:target{outline:2px solid var(--blue);outline-offset:4px}.day-header{position:relative;isolation:isolate;padding:25px 26px;background:linear-gradient(110deg,#eef1ff,#fff 63%,#f5ffd9);border-bottom:1px solid var(--line)}.day-label{display:inline-block;background:var(--ink);color:white;border-radius:99px;padding:4px 14px;font-size:10px;font-weight:850;letter-spacing:.14em}.day-header h2{margin:13px 0 5px;font-size:23px;letter-spacing:-.045em;padding-right:22px}.day-header p{font-size:12px;color:var(--muted)}.day-watermark{position:absolute;right:20px;top:12px;font-size:80px;letter-spacing:-.09em;line-height:1.2;font-weight:900;color:#14201808;z-index:-1}
    .day-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:16px 22px 5px}.day-summary p{padding:11px 13px;background:white;border:1px solid var(--line);border-radius:14px;font-size:14px;font-weight:800;font-variant-numeric:tabular-nums}.day-summary small{display:block;margin-bottom:4px;font-size:8px;letter-spacing:.12em;color:var(--muted);font-weight:700}.day-summary p:last-child{background:var(--ink);border-color:var(--ink);color:var(--lime)}.day-summary p:last-child small{color:#bac8bc}
    ol{position:relative;isolation:isolate;list-style:none;margin:0;padding:16px 22px 24px;display:grid;gap:14px}ol:before{content:'';position:absolute;left:40px;top:34px;bottom:46px;width:1px;background:linear-gradient(var(--blue),#4058f520);z-index:-1}.entry{display:grid;grid-template-columns:36px minmax(0,1fr);gap:13px;align-items:start;--accent:var(--blue)}.entry.meal{--accent:var(--lime)}.number{display:grid;place-items:center;width:36px;height:36px;margin-top:3px;border-radius:50%;background:var(--accent);color:white;font-weight:850;font-size:12px;box-shadow:0 0 0 5px #fbfaf7}.meal .number{color:var(--ink)}.move .number{width:24px;height:24px;margin:8px auto 0;background:#f7f6f0;color:#7b827c;font-size:16px;box-shadow:0 0 0 5px #fbfaf7}
    .stop{position:relative;border:1px solid var(--line);border-radius:19px;padding:17px 18px 17px 21px;background:white;min-width:0;overflow:hidden;transition:box-shadow .2s,translate .2s}.stop:before{content:'';position:absolute;left:0;inset-block:0;width:4px;background:var(--accent)}.meta{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;align-items:center;font-size:9px;font-weight:700;color:var(--muted)}time{padding:4px 8px;border-radius:99px;background:#eff1ff;color:#304bd8;font-size:10px;white-space:nowrap;font-variant-numeric:tabular-nums}.stop h3{font-size:19px;margin-top:8px;letter-spacing:-.04em;line-height:1.4}.stay{display:inline-block;font-size:10px;color:var(--muted);margin-top:7px}.memo{white-space:pre-wrap;font-size:12px;line-height:1.8;color:var(--muted);margin-top:10px}.stop:has(.place-link)>:not(.meta,.place-link){margin-right:44px}.place-link{position:absolute;right:13px;bottom:13px;display:grid;place-items:center;width:40px;height:40px;border:1px solid #e0e4ff;border-radius:13px;color:var(--blue);background:#f1f3ff;transition:background .2s,color .2s,translate .2s}.place-link svg{width:21px;height:21px}.place-link:hover{background:var(--blue);color:white;translate:0 -2px}.place-link:focus-visible{outline-offset:-4px}.move .stop{padding:7px 0;border:0;background:transparent;overflow:visible}.move .stop:before{display:none}.move .meta{justify-content:start;gap:10px}.move time{background:transparent;color:var(--muted);padding:0}.move .stop h3{font-size:11px;font-weight:600;color:var(--muted);margin-top:3px}
    aside{display:grid;gap:18px}.card{position:relative;isolation:isolate;overflow:hidden;padding:26px;border:1px solid var(--line);border-radius:26px;background:white}.card h2{font-size:23px;letter-spacing:-.045em;line-height:1.35;margin:13px 0 22px}.hotel h2{display:flex;justify-content:space-between;align-items:center}.hotel h2>span{color:var(--blue);font-size:29px}.hotel>div{position:relative;padding:18px 0 0;border-top:1px dashed var(--line);min-height:72px}.hotel>div:has(.place-link){padding-right:45px}.hotel .place-link{right:0;bottom:0}.place-name{font-size:16px;line-height:1.5;letter-spacing:-.035em}.note{background:var(--ink);color:white;min-height:300px}.note .eyebrow{color:var(--lime);font-size:8px}.note h2{font-size:26px}.note p:not(.eyebrow){font-size:11px;line-height:1.9;color:#c5d0c8;margin-top:15px}.note-star{position:absolute;right:-25px;bottom:-54px;font-size:190px;line-height:1;color:#ffffff06;z-index:-1}
    footer{display:flex;justify-content:space-between;gap:18px;flex-wrap:wrap;padding:24px 28px 35px;border-top:1px solid var(--line);font-size:10px;color:var(--muted)}footer strong{font-size:24px;letter-spacing:-1px;color:var(--ink)}
    @container(max-width:760px){.hero{grid-template-columns:1fr}.hero-copy{padding:30px 25px 65px}h1{font-size:36px;max-width:15ch}.hero-ticket{padding:30px 32px 24px}.ticket-frame{padding:20px;rotate:-2deg}.ticket-title{font-size:42px;margin:16px 0 20px}.ticket-bottom{padding-top:12px}.ticket-title>span{font-size:65px}.ticket-flower{font-size:38px}.facts{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:30px}.layout{grid-template-columns:1fr}.schedule-heading>span{display:none}aside{grid-template-columns:1fr 1fr}.note{min-height:260px}.card{padding:22px}.card h2{font-size:22px}}
    @container(max-width:480px){main{padding:14px 12px 28px}.brand-bar{padding:17px 16px 3px}.brand-bar>span{font-size:7px;letter-spacing:.1em}.brand-bar strong{font-size:27px}.hero{border-radius:25px}h1{font-size:32px}.hero-copy{padding:28px 23px 66px}.hero-description{font-size:12px}.hero-copy .eyebrow{font-size:8px;margin-bottom:20px}.period{font-size:10px}.hero-ticket{padding:30px 26px 23px}.ticket-frame{border-radius:20px}.ticket-title{font-size:36px}.fact{padding:16px;border-radius:19px}.fact strong{font-size:21px}.fact>span{font-size:9px}.fact small{font-size:9px}.schedule-heading h2{font-size:22px}nav{gap:4px;padding:6px;border-radius:17px}nav label{padding:10px;font-size:10px}.day-header{padding:21px 20px}.day-header h2{font-size:20px}.day-header p{font-size:10px}.day-watermark{font-size:70px}.day-summary{margin:13px 14px 4px;gap:6px}.day-summary p{padding:10px;font-size:12px}.day-summary small{font-size:7px}ol{padding:14px 13px 20px;gap:12px}ol:before{left:28px}.entry{grid-template-columns:30px minmax(0,1fr);gap:10px}.number{width:30px;height:30px;font-size:10px}.stop{padding:14px 14px 16px 17px;border-radius:17px}.stop:has(.place-link)>:not(.meta,.place-link){margin-right:39px}.stop h3{font-size:17px}.meta{font-size:8px}time{font-size:9px;padding:4px 6px}.memo{font-size:11px}.place-link{width:36px;height:36px;right:10px;bottom:12px;border-radius:11px}.place-link svg{width:19px;height:19px}aside{grid-template-columns:1fr}.note{min-height:235px}.note h2{font-size:28px}.note-star{right:8px;font-size:220px}footer{padding:22px 16px;font-size:9px}}
    @media(hover:hover) and (prefers-reduced-motion:no-preference){.entry:not(.move) .stop:hover{translate:0 -2px;box-shadow:0 9px 22px #14201808}}
    @media(prefers-reduced-motion:reduce){.journey-art *{animation:none!important}*{transition:none!important}}
    @media(forced-colors:active){.hero-copy,.hero-ticket,.note,.fact:last-child,.number{border:1px solid CanvasText}.journey-art{display:none}.place-link{border:1px solid LinkText}.stop:before{background:CanvasText}}
    @media print{.itinerary-page{background:white;font-size:11pt}main{padding:12px 0}.brand-bar,footer{padding:12px 0}.hero{display:block;border:1px solid #ccc;box-shadow:none}.hero-copy{padding:24px;background:white;color:black}.hero-copy .eyebrow,.hero-description{color:#555}h1{font-size:30px}.period{margin-top:12px;border-color:#ccc}.hero-ticket,.journey-art,nav,.schedule-heading{display:none}.facts{margin:16px 0}.fact{padding:12px}.layout{display:block}aside{margin-top:20px;grid-template-columns:1fr 1fr}.day{display:block!important;margin:18px 0;overflow:visible}.day-header{break-after:avoid}.entry,.card{break-inside:avoid}.note{background:white;color:var(--ink);min-height:0}.note p:not(.eyebrow),.note .eyebrow{color:var(--muted)}*{animation:none!important}}
  `;

  function safeLink(value) {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && url.hostname === 'place.map.kakao.com'
        && !url.username && !url.password && !url.port && /^\/[A-Za-z0-9_-]+$/.test(url.pathname)
        && !url.search && !url.hash;
    } catch (_) { return false; }
  }

  function filename(title) {
    const name = Array.from(title.replace(/[<>:"/\\|?*\u0000-\u001f\u007f]/g, '_').trim()).slice(0, 70).join('');
    return `Routy_${name || '여행 일정'}.html`;
  }

  function buildDocument(document, plan) {
    const page = document.implementation.createHTMLDocument('');
    const source = document.createElement('template');
    source.innerHTML = template.slice(template.indexOf('<body>') + 6, template.indexOf('</body>'));
    page.body.replaceChildren(source.content.cloneNode(true));
    const head = new document.defaultView.DOMParser().parseFromString(template, 'text/html').head;
    page.head.replaceChildren(...Array.from(head.childNodes, node => page.importNode(node, true)));
    page.documentElement.lang = 'ko';
    const css = page.createElement('style'); css.textContent = styles; page.head.append(css);
    const $ = selector => page.querySelector(selector);
    const node = (tag, text, className) => {
      const element = page.createElement(tag);
      if (text != null) element.textContent = text;
      if (className) element.className = className;
      return element;
    };
    function place(parent, value) {
      if (value.memo) parent.append(node('p', value.memo, 'memo'));
      if (safeLink(value.placeUrl)) {
        const link = node('a', null, 'place-link');
        link.href = value.placeUrl; link.target = '_blank'; link.rel = 'noopener noreferrer';
        link.setAttribute('aria-label', `카카오 지도에서 ${value.displayName} 장소 보기`);
        link.title = `카카오 지도에서 ${value.displayName} 장소 보기`;
        const icon = page.createElementNS('http://www.w3.org/2000/svg', 'svg');
        icon.setAttribute('viewBox', '0 0 24 24'); icon.setAttribute('fill', 'none');
        icon.setAttribute('stroke', 'currentColor'); icon.setAttribute('stroke-width', '1.7');
        icon.setAttribute('stroke-linecap', 'round'); icon.setAttribute('stroke-linejoin', 'round');
        icon.setAttribute('aria-hidden', 'true'); icon.setAttribute('focusable', 'false');
        const path = page.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', 'M13 4h7v7m0-7-9 9M9 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3');
        icon.append(path); link.append(icon);
        parent.append(link);
      }
    }
    page.title = `${plan.title} · Routy`;
    $('h1').textContent = plan.title;
    $('.period').textContent = `${plan.startDate} — ${plan.endDate}`;
    const travelMode = plan.travelMode === 'CAR' ? '자동차' : '대중교통';
    const visits = new Set(plan.days.flatMap(day => day.items.filter(item => item.type === 'VISIT').map(item => item.planPlaceId))).size;
    for (const [label, value, detail] of [
      ['여행 기간', `${plan.days.length}일`, `${plan.startDate.slice(5).replace('-', '.')} — ${plan.endDate.slice(5).replace('-', '.')}`],
      ['이동 방식', travelMode, '우리의 이동 수단'],
      ['방문 일정', `${visits}곳`, '선택한 관광지'],
      ['나만의 여행', 'YOUR FLOW', '좋은 흐름에서']
    ]) {
      const fact = node('div', null, 'fact');
      fact.append(node('span', label), node('strong', value), node('small', detail)); $('.facts').append(fact);
    }
    for (const [index, day] of plan.days.entries()) {
      const label = node('label'), choice = node('input');
      choice.type = 'radio'; choice.name = 'itinerary-day'; choice.id = `day-choice-${index + 1}`;
      choice.defaultChecked = index === 0;
      choice.setAttribute('aria-controls', `day-${index + 1}`);
      label.append(choice, page.createTextNode(`${day.day}일차 · ${day.date}`)); $('nav').append(label);
      const article = node('section', null, 'day'); article.id = `day-${index + 1}`;
      css.textContent += `\n.itinerary-page:has(#day-choice-${index + 1}:checked) #day-${index + 1}{display:block}`;
      const header = node('header', null, 'day-header');
      header.append(node('span', `DAY ${String(day.day).padStart(2, '0')}`, 'day-label'), node('h2', `${day.day}일차 · ${day.date}`),
        node('p', `활동 시간 ${day.activityStartTime}–${day.activityEndTime}`));
      const watermark = node('span', String(day.day).padStart(2, '0'), 'day-watermark'); watermark.setAttribute('aria-hidden', 'true'); header.append(watermark);
      const summary = node('div', null, 'day-summary');
      for (const [label, value] of [['START', day.activityStartTime], ['FINISH', day.activityEndTime], ['TRAVEL', travelMode]]) {
        const valueNode = node('p'); valueNode.append(node('small', label), page.createTextNode(value)); summary.append(valueNode);
      }
      const list = node('ol'); let stop = 0;
      for (const item of day.items) {
        const move = item.type === 'MOVE', meal = item.type === 'MEAL';
        const row = node('li', null, `entry ${move ? 'move' : meal ? 'meal' : 'visit'}`);
        const number = node('span', move ? '↓' : String(++stop).padStart(2, '0'), 'number'); number.setAttribute('aria-hidden', 'true');
        const card = node('div', null, 'stop'), meta = node('div', null, 'meta');
        meta.append(node('span', move ? '이동' : meal ? '식사' : '방문'), node('time', `${item.startTime}–${item.endTime}`));
        card.append(meta, node('h3', move ? `예상 이동시간 ${item.estimatedMinutes}분` : item.displayName));
        if (!move) {
          if (item.stayMinutes != null) card.append(node('p', `체류 ${item.stayMinutes}분`, 'stay'));
          place(card, item);
        }
        row.append(number, card); list.append(row);
      }
      article.append(header, summary, list); $('.days').append(article);
    }
    if (plan.hotel) {
      const hotel = $('.hotel div'); hotel.append(node('h3', plan.hotel.displayName, 'place-name')); place(hotel, plan.hotel);
    } else $('.hotel').remove();
    return page;
  }

  function renderCompleted(document, plan) {
    const host = document.querySelector('#detail-document');
    const root = host.shadowRoot || host.attachShadow({mode: 'open'});
    const page = buildDocument(document, plan);
    root.replaceChildren(document.importNode(page.querySelector('style'), true),
      document.importNode(page.querySelector('.itinerary-page'), true));
    document.querySelector('#detail-warning').textContent = plan.warnings?.includes('ESTIMATED_TRAVEL_TIMES_USED')
      ? '일부 경로를 확인하지 못해 일정 전체의 예상 이동시간을 거리 기반으로 계산했습니다.' : '';
  }

  function clearCompleted(document) {
    document.querySelector('#detail-document').shadowRoot?.replaceChildren();
    document.querySelector('#detail-warning').textContent = '';
  }

  function mount(document, window) {
    const button = document.querySelector('#download-itinerary'), status = document.querySelector('#download-status');
    let plan = null;
    const urls = new Set();
    function clear() {
      plan = null; button.disabled = true; status.textContent = '';
      for (const url of urls) window.URL.revokeObjectURL(url);
      urls.clear();
    }
    button.addEventListener('click', () => {
      if (!plan) return;
      let url;
      try {
        const page = buildDocument(document, plan);
        const blob = new window.Blob(['<!doctype html>\n', page.documentElement.outerHTML], {type: 'text/html;charset=utf-8'});
        url = window.URL.createObjectURL(blob); urls.add(url);
        const link = document.createElement('a'); link.href = url; link.download = filename(plan.title);
        document.body.append(link); link.click(); link.remove();
        status.textContent = 'HTML 파일 다운로드를 요청했습니다. 저장한 파일은 인터넷 없이도 읽을 수 있어요.';
        window.setTimeout(() => { if (urls.delete(url)) window.URL.revokeObjectURL(url); }, 1000);
      } catch (_) {
        if (url) { urls.delete(url); window.URL.revokeObjectURL(url); }
        status.textContent = '파일을 만들지 못했습니다. 다시 시도해 주세요.';
      }
    });
    window.addEventListener('pagehide', clear);
    return {setPlan(value) { plan = value; button.disabled = !value; status.textContent = ''; }, clear};
  }

  return Object.freeze({buildDocument, filename, safeLink, renderCompleted, clearCompleted, mount});
});
