// Local dev server: serves the static app and runs the same API handler Vercel uses.
// Usage: node server.js   (PORT=3000 default)
const http = require('http');
const fs = require('fs');
const path = require('path');
const fm = require('./api/fm');
const push = require('./api/push');
const img = require('./api/img');
const hl = require('./api/hl');
const widget = require('./api/widget');
const tts = require('./api/tts');
const today = require('./api/today');
const ask = require('./api/ask');

const PORT = process.env.PORT || 3000;
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.png': 'image/png' };

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/api/fm') return fm(req, res);
  if (url.pathname === '/api/push') return push(req, res);
  if (url.pathname === '/api/img') return img(req, res);
  if (url.pathname === '/api/hl') return hl(req, res);
  if (url.pathname === '/api/widget') return widget(req, res);
  if (url.pathname === '/api/tts') return tts(req, res);
  if (url.pathname === '/api/today') return today(req, res);
  if (url.pathname === '/api/ask') return ask(req, res);
  const rel = path.normalize(url.pathname === '/' ? '/index.html' : decodeURIComponent(url.pathname)).replace(/^([/\\])+/, '');
  const file = path.join(__dirname, rel);
  if (!file.startsWith(__dirname + path.sep) || rel.startsWith('api') || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); return res.end();
  }
  res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(PORT, () => console.log('http://localhost:' + PORT));
