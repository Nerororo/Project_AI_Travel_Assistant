'use strict';
// Optional local-only preview. Run: node js/preview-server.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const files = new Map([
  ['/', 'index.html'], ['/index.html', 'index.html'], ['/css/style.css', 'css/style.css'],
  ['/css/journey.css', 'css/journey.css'],
  ['/fonts/NanumPenScript-Regular.ttf', 'fonts/NanumPenScript-Regular.ttf'],
  ['/js/preview.js', 'js/preview.js'], ['/js/auth.js', 'js/auth.js'],
  ['/js/place-selection-state.js', 'js/place-selection-state.js'],
  ['/js/place-workspace.js', 'js/place-workspace.js'],
  ['/js/menu-workspace.js', 'js/menu-workspace.js'],
  ['/js/estimate-workspace.js', 'js/estimate-workspace.js'],
  ['/js/restaurant-workspace.js', 'js/restaurant-workspace.js'],
  ['/js/review-workspace.js', 'js/review-workspace.js'],
  ['/js/map-config.json', 'js/map-config.json'],
  ['/img/mark.svg', 'img/mark.svg'], ['/img/hero-busan.png', 'img/hero-busan.png'],
  ['/img/hero-seoul.png', 'img/hero-seoul.png'],
  ['/img/auth-coast.png', 'img/auth-coast.png'],
  ['/img/auth-heritage.png', 'img/auth-heritage.png'],
  ['/img/workspace-coast.png', 'img/workspace-coast.png']
]);
const types = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8',
  '.js':'text/javascript; charset=utf-8','.json':'application/json; charset=utf-8',
  '.svg':'image/svg+xml','.png':'image/png','.ttf':'font/ttf'};
http.createServer((req,res)=>{
  const file=files.get(new URL(req.url,'http://localhost').pathname);
  if(!file || !['GET','HEAD'].includes(req.method)){res.writeHead(404);res.end();return;}
  if (!fs.existsSync(path.join(root, file))) {res.writeHead(404);res.end();return;}
  res.writeHead(200,{'Content-Type':types[path.extname(file)],'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
  if(req.method==='HEAD'){res.end();return;}
  fs.createReadStream(path.join(root,file)).pipe(res);
}).listen(4173,'127.0.0.1',()=>console.log('Routy2 preview: http://127.0.0.1:4173'));
