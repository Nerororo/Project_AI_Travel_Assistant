'use strict';
// Optional local-only preview. Run: node preview-server.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const files = new Map([['/', 'index.html'],['/index.html','index.html'],['/style.css','style.css'],['/preview.js','preview.js'],['/mark.svg','mark.svg']]);
const types = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.svg':'image/svg+xml'};
http.createServer((req,res)=>{
  const file=files.get(new URL(req.url,'http://localhost').pathname);
  if(!file || !['GET','HEAD'].includes(req.method)){res.writeHead(404);res.end();return;}
  res.writeHead(200,{'Content-Type':types[path.extname(file)],'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
  if(req.method==='HEAD'){res.end();return;}
  fs.createReadStream(path.join(__dirname,file)).pipe(res);
}).listen(4173,'127.0.0.1',()=>console.log('Routy2 preview: http://127.0.0.1:4173'));
