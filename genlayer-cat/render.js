// Usage:
//   node render.js stills 6.2,9,18        -> build/stills/*.jpg
//   node render.js video [fps] [workers]  -> build/video_noaudio.mp4
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { serve } = require('./tools/serve.js');

const ROOT = __dirname;
const BUILD = path.join(ROOT, 'build');
fs.mkdirSync(BUILD, { recursive: true });
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'];

async function openPage(browser, url) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('CONSOLE', m.text()); });
  await page.goto(url + '/index.html?render' + (process.env.Q || ''), { waitUntil: 'domcontentloaded', timeout: 600000 });
  await page.waitForFunction(() => window.motionReady === true, null, { timeout: 600000 });
  return page;
}

async function stills(url, times) {
  const browser = await chromium.launch({ args: ARGS });
  const page = await openPage(browser, url);
  const dir = path.join(BUILD, 'stills');
  fs.mkdirSync(dir, { recursive: true });
  for (const t of times) {
    const t0 = Date.now();
    const data = await page.evaluate((tt) => window.renderFrame(tt, 0.92), t);
    const f = path.join(dir, `t_${t.toFixed(2).padStart(6, '0')}.jpg`);
    fs.writeFileSync(f, Buffer.from(data.split(',')[1], 'base64'));
    console.log(f, ((Date.now() - t0) / 1000).toFixed(1) + 's');
  }
  await browser.close();
}

// Frames are rendered in short segments (build/seg/seg_<first>_<end>.mp4). A segment file only
// appears once it is complete, so an interrupted render resumes where it stopped.
const SEG = 60;
const SEGDIR = path.join(BUILD, 'seg');
const segName = (a, b) => path.join(SEGDIR, `seg_${String(a).padStart(5, '0')}_${String(b).padStart(5, '0')}.mp4`);

async function encodeSegment(page, a, b, fps, idx) {
  const out = segName(a, b), tmp = out + '.part.mp4';
  const ff = spawn('ffmpeg', [
    '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', String(fps), tmp,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let f = a; f < b; f++) {
    const data = await page.evaluate((tt) => window.renderFrame(tt, 0.95), f / fps);
    const buf = Buffer.from(data.split(',')[1], 'base64');
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  const code = await new Promise((r) => ff.on('close', r));
  if (code) throw new Error('ffmpeg failed on ' + out);
  fs.renameSync(tmp, out);
  console.log(`[w${idx}] ${path.basename(out)}`);
}

// existing segments (any boundaries) covering frame f
function covered(f) {
  return fs.readdirSync(SEGDIR).some((n) => {
    const m = /^seg_(\d+)_(\d+)\.mp4$/.exec(n);
    return m && f >= Number(m[1]) && f < Number(m[2]);
  });
}

async function video(url, fps, nw) {
  fs.mkdirSync(SEGDIR, { recursive: true });
  const total = Math.round(44.5 * fps);
  const queue = [];
  for (let a = 0; a < total; a += SEG) {
    const b = Math.min(total, a + SEG);
    let s = a;
    while (s < b && covered(s)) s++;
    let e = s;
    while (e < b && !covered(e)) e++;
    if (s < e) queue.push([s, e]);
  }
  console.log(`${queue.length} segments to render`);
  const t0 = Date.now();
  let done = 0;
  await Promise.all(Array.from({ length: Math.min(nw, queue.length) }, async (_, idx) => {
    const browser = await chromium.launch({ args: ARGS });
    const page = await openPage(browser, url);
    while (queue.length) {
      const [a, b] = queue.shift();
      await encodeSegment(page, a, b, fps, idx);
      done++;
      const el = (Date.now() - t0) / 1000;
      console.log(`  ${done} done, ${queue.length} left, ${(el / 60).toFixed(1)} min elapsed`);
    }
    await browser.close();
  }));
  // concat in frame order, checking the segments tile [0, total)
  const segs = fs.readdirSync(SEGDIR).map((n) => /^seg_(\d+)_(\d+)\.mp4$/.exec(n)).filter(Boolean)
    .map((m) => ({ a: Number(m[1]), b: Number(m[2]), f: path.join(SEGDIR, m[0]) })).sort((x, y) => x.a - y.a);
  let pos = 0;
  for (const s of segs) { if (s.a !== pos) throw new Error(`gap/overlap at frame ${pos} (next segment starts ${s.a})`); pos = s.b; }
  if (pos !== total) throw new Error(`segments end at ${pos}, expected ${total}`);
  const list = path.join(BUILD, 'parts.txt');
  fs.writeFileSync(list, segs.map((s) => `file '${s.f}'`).join('\n'));
  await new Promise((res, rej) => {
    const p = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', path.join(BUILD, 'video_noaudio.mp4')], { stdio: 'inherit' });
    p.on('close', (c) => (c ? rej(new Error('concat failed')) : res()));
  });
  console.log('video done', total, 'frames');
}

(async () => {
  const [mode, a, b] = process.argv.slice(2);
  const { url, close } = await serve(ROOT);
  if (mode === 'stills') await stills(url, a.split(',').map(Number));
  else await video(url, Number(a || 30), Number(b || 4));
  close();
})().catch((e) => { console.error(e); process.exit(1); });
