// Usage:
//   node render.js stills 1.2,5.0,8.3        -> build/stills/*.jpg
//   node render.js video [fps] [workers]     -> build/video_noaudio.mp4
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const BUILD = path.join(ROOT, 'build');
fs.mkdirSync(BUILD, { recursive: true });
const URL = 'file://' + path.join(ROOT, 'index.html') + '?render';

async function openPage(browser) {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('CONSOLE', m.text()); });
  await page.goto(URL);
  await page.waitForFunction(() => window.motionReady === true);
  // warm up fonts
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => renderOnly(0.1));
  return page;
}

async function stills(times) {
  const browser = await chromium.launch({ args: ['--disable-gpu'] });
  const page = await openPage(browser);
  const dir = path.join(BUILD, 'stills');
  fs.mkdirSync(dir, { recursive: true });
  for (const t of times) {
    const url = await page.evaluate((tt) => renderFrame(tt, 0.9), t);
    const f = path.join(dir, `t_${t.toFixed(2).padStart(6, '0')}.jpg`);
    fs.writeFileSync(f, Buffer.from(url.split(',')[1], 'base64'));
    console.log(f);
  }
  await browser.close();
}

async function worker(browser, idx, frames, fps, out) {
  const page = await openPage(browser);
  const ff = spawn('ffmpeg', [
    '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', String(fps), out,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  for (let k = 0; k < frames.length; k++) {
    const t = frames[k] / fps;
    const url = await page.evaluate((tt) => renderFrame(tt, 0.95), t);
    const b = Buffer.from(url.split(',')[1], 'base64');
    if (!ff.stdin.write(b)) await new Promise((r) => ff.stdin.once('drain', r));
    if (k % 120 === 0) console.log(`[w${idx}] ${k}/${frames.length} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  await page.close();
}

async function video(fps, nw) {
  const browser = await chromium.launch({ args: ['--disable-gpu'] });
  const probe = await openPage(browser);
  const dur = await probe.evaluate(() => DURATION);
  await probe.close();
  const total = Math.round(dur * fps);
  const per = Math.ceil(total / nw);
  const parts = [];
  const jobs = [];
  for (let w = 0; w < nw; w++) {
    const fr = [];
    for (let f = w * per; f < Math.min(total, (w + 1) * per); f++) fr.push(f);
    const out = path.join(BUILD, `part_${w}.mp4`);
    parts.push(out);
    jobs.push(worker(browser, w, fr, fps, out));
  }
  await Promise.all(jobs);
  await browser.close();
  const list = path.join(BUILD, 'parts.txt');
  fs.writeFileSync(list, parts.map((p) => `file '${p}'`).join('\n'));
  await new Promise((res, rej) => {
    const p = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', path.join(BUILD, 'video_noaudio.mp4')], { stdio: 'inherit' });
    p.on('close', (c) => (c ? rej(new Error('concat failed')) : res()));
  });
  parts.forEach((p) => fs.unlinkSync(p));
  console.log('video done', total, 'frames');
}

(async () => {
  const [mode, a, b] = process.argv.slice(2);
  if (mode === 'stills') await stills(a.split(',').map(Number));
  else await video(Number(a || 30), Number(b || 4));
})().catch((e) => { console.error(e); process.exit(1); });
