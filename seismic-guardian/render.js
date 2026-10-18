// Usage:
//   node render.js stills 1.2,5.0,8.3 [palette]      -> build/stills/*.jpg
//   node render.js video [fps] [workers] [palette]   -> build/video_<palette>.mp4
// The page is served from a tiny local server; CDN requests (three.js, Google Fonts)
// are answered from node_modules/ and vendor/ so rendering works offline.
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const BUILD = path.join(ROOT, 'build');
fs.mkdirSync(BUILD, { recursive: true });
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.m4a': 'audio/mp4', '.png': 'image/png' };

function serve() {
  return new Promise((res) => {
    const srv = http.createServer((req, rsp) => {
      const p = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]));
      if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { rsp.writeHead(404); return rsp.end(); }
      rsp.writeHead(200, { 'content-type': TYPES[path.extname(p)] || 'application/octet-stream' });
      fs.createReadStream(p).pipe(rsp);
    });
    srv.listen(0, '127.0.0.1', () => res(srv));
  });
}

async function openPage(browser, port, palette) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.error('CONSOLE', m.text()); });
  await page.route('https://cdn.jsdelivr.net/npm/three@0.160.0/**', (r) => {
    const rel = r.request().url().split('three@0.160.0/')[1];
    r.fulfill({ path: path.join(ROOT, 'node_modules/three', rel), contentType: 'text/javascript' });
  });
  await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ path: path.join(ROOT, 'vendor/fonts/fonts.css'), contentType: 'text/css' }));
  await page.route('https://fonts.gstatic.com/**', (r) => {
    const rel = new URL(r.request().url()).pathname;
    r.fulfill({ path: path.join(ROOT, 'vendor/fonts', rel), contentType: 'font/woff2', headers: { 'access-control-allow-origin': '*' } });
  });
  await page.goto(`http://127.0.0.1:${port}/index.html?render`);
  await page.waitForFunction(() => window.guardianReady === true, null, { timeout: 120000 });
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate((p) => window.setPalette(p), palette);
  await page.evaluate(() => window.renderAt(0.1));
  return page;
}

async function grab(page, t) {
  await page.evaluate((tt) => window.renderAt(tt), t);
  return page.screenshot({ type: 'jpeg', quality: 95, clip: { x: 0, y: 0, width: 1920, height: 1080 } });
}

const launch = () => chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });

async function stills(times, palette) {
  const srv = await serve();
  const browser = await launch();
  const page = await openPage(browser, srv.address().port, palette);
  const dir = path.join(BUILD, 'stills');
  fs.mkdirSync(dir, { recursive: true });
  for (const t of times) {
    const f = path.join(dir, `${palette}_t${t.toFixed(2).padStart(5, '0')}.jpg`);
    fs.writeFileSync(f, await grab(page, t));
    console.log(f);
  }
  await browser.close();
  srv.close();
}

async function worker(browser, port, palette, idx, frames, fps, out) {
  const page = await openPage(browser, port, palette);
  const ff = spawn('ffmpeg', [
    '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', String(fps), out,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  for (let k = 0; k < frames.length; k++) {
    const b = await grab(page, frames[k] / fps);
    if (!ff.stdin.write(b)) await new Promise((r) => ff.stdin.once('drain', r));
    if (k % 60 === 0) console.log(`[w${idx}] ${k}/${frames.length} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  await page.close();
}

async function video(fps, nw, palette) {
  const srv = await serve();
  const port = srv.address().port;
  const browser = await launch();
  const probe = await openPage(browser, port, palette);
  const dur = await probe.evaluate(() => window.DURATION);
  await probe.close();
  const total = Math.round(dur * fps);
  const per = Math.ceil(total / nw);
  const parts = [], jobs = [];
  for (let w = 0; w < nw; w++) {
    const fr = [];
    for (let f = w * per; f < Math.min(total, (w + 1) * per); f++) fr.push(f);
    const out = path.join(BUILD, `part_${palette}_${w}.mp4`);
    parts.push(out);
    jobs.push(worker(browser, port, palette, w, fr, fps, out));
  }
  await Promise.all(jobs);
  await browser.close();
  srv.close();
  const list = path.join(BUILD, `parts_${palette}.txt`);
  fs.writeFileSync(list, parts.map((p) => `file '${p}'`).join('\n'));
  await new Promise((res, rej) => {
    const p = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', path.join(BUILD, `video_${palette}.mp4`)], { stdio: 'inherit' });
    p.on('close', (c) => (c ? rej(new Error('concat failed')) : res()));
  });
  parts.forEach((p) => fs.unlinkSync(p));
  fs.unlinkSync(list);
  console.log('video done', palette, total, 'frames');
}

(async () => {
  const [mode, a, b, c] = process.argv.slice(2);
  if (mode === 'stills') await stills(a.split(',').map(Number), b || 'original');
  else await video(Number(a || 30), Number(b || 4), c || 'original');
})().catch((e) => { console.error(e); process.exit(1); });
