// Renders index.html frame by frame at 30 fps into frames/*.jpg (WebGL through SwiftShader when there is no GPU).
// Usage: node render.mjs [from] [to] [everyNthFrame]
import { createRequire } from 'module';
import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
const here = path.dirname(fileURLToPath(import.meta.url));
const { chromium } = createRequire(import.meta.url)(process.env.PW || 'playwright');
const [from = 0, to = 99, every = 1] = process.argv.slice(2).map(Number);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.ttf': 'font/ttf', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const f = path.join(here, decodeURIComponent(req.url.split('?')[0]));
  if (!f.startsWith(here) || !fs.existsSync(f)) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', types[path.extname(f)] || 'application/octet-stream'); fs.createReadStream(f).pipe(res);
}).listen(0);
const port = server.address().port;
const out = path.join(here, 'frames'); fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined,
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
page.on('console', m => { if (m.type() === 'error') console.log('page:', m.text()); });
page.on('pageerror', e => console.log('page error:', e.message));
await page.goto(`http://localhost:${port}/index.html`);
await page.waitForFunction(() => window.ready);
await page.evaluate(() => window.ready);
const total = await page.evaluate(() => window.TIMING.total);
const FPS = 30, t0 = Date.now();
for (let f = Math.round(from * FPS); f < Math.round(Math.min(to, total) * FPS); f += every) {
  const data = await page.evaluate(t => { window.render(t); return document.getElementById('c').toDataURL('image/jpeg', 0.93); }, f / FPS);
  fs.writeFileSync(path.join(out, `f${String(f).padStart(4, '0')}.jpg`), Buffer.from(data.split(',')[1], 'base64'));
}
console.log('frames done in', ((Date.now() - t0) / 1000).toFixed(1), 's; total', total.toFixed(2));
await browser.close(); server.close();
