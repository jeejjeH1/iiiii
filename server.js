// Tiny dependency-free server: serves index.html and proxies FotMob (browsers can't call it directly: no CORS).
// Usage: node server.js   (PORT=3000 default)
// FotMob's API expects an `x-mas` signature header that rotates; if requests get 401/403,
// copy the current value from your browser's DevTools (Network tab on fotmob.com) into FOTMOB_XMAS.
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const XMAS = process.env.FOTMOB_XMAS || '';
const MOCK = process.env.MOCK === '1'; // dev-only: serve ./mock.json instead of calling FotMob
const TTL = 15 * 1000;
const cache = new Map();

function fetchFotmob(date, tz, cc) {
  return new Promise((resolve, reject) => {
    const q = new URLSearchParams({ date, timezone: tz, ccode3: cc });
    const req = https.get({
      hostname: 'www.fotmob.com',
      path: '/api/data/matches?' + q,
      headers: {
        'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36',
        accept: 'application/json',
        referer: 'https://www.fotmob.com/',
        ...(XMAS && { 'x-mas': XMAS }),
      },
    }, (res) => {
      let body = '';
      res.on('data', (c) => (body += c));
      res.on('end', () => (res.statusCode === 200 ? resolve(body) : reject(new Error('FotMob responded ' + res.statusCode))));
    });
    req.setTimeout(10000, () => req.destroy(new Error('FotMob timeout')));
    req.on('error', reject);
  });
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/api/matches') {
    const date = (url.searchParams.get('date') || '').replace(/\D/g, '');
    if (!/^\d{8}$/.test(date)) { res.writeHead(400); return res.end('{"error":"date=YYYYMMDD"}'); }
    const tz = url.searchParams.get('tz') || 'UTC';
    const key = date + tz;
    const hit = cache.get(key);
    res.setHeader('content-type', 'application/json');
    if (hit && Date.now() - hit.t < TTL) return res.end(hit.body);
    try {
      const body = MOCK ? fs.readFileSync(path.join(__dirname, 'mock.json'), 'utf8') : await fetchFotmob(date, tz, 'IRN');
      cache.set(key, { t: Date.now(), body });
      res.end(body);
    } catch (e) {
      if (hit) return res.end(hit.body); // serve stale on upstream failure
      res.writeHead(502); res.end(JSON.stringify({ error: e.message }));
    }
    return;
  }
  const file = url.pathname === '/' ? 'index.html' : null;
  if (!file) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
  res.end(fs.readFileSync(path.join(__dirname, file)));
}).listen(PORT, () => console.log('http://localhost:' + PORT + (MOCK ? ' (MOCK)' : '')));
