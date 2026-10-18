'use strict';
// Post-processing: seismic slice displacement, glitch, bloom, camera shake/punch, whip blur,
// vignette, film grain (the brand's "light noise treatment") and flashes.

function fxState(t) {
  const s = { shake: 0, flash: 0, glitch: 0, punch: 0, whip: 0, quake: 0 };
  for (const e of FX) {
    const d = t - e.t;
    if (d < 0 || d > e.dur) continue;
    const k = 1 - d / e.dur;
    if (e.type === 'shake') s.shake += e.amp * k * k;
    else if (e.type === 'flash') s.flash = Math.max(s.flash, e.a * k * k);
    else if (e.type === 'glitch') s.glitch = Math.max(s.glitch, e.amp * k);
    else if (e.type === 'punch') s.punch += e.amp * k * k * k;
    else if (e.type === 'quake') s.quake = Math.max(s.quake, e.amp * k * k);
    else if (e.type === 'whip') s.whip = Math.max(s.whip, Math.sin(Math.PI * (d / e.dur)) * e.amp);
  }
  return s;
}
const GRAIN = [];
for (let g = 0; g < 4; g++) {
  const gc = mk(256, 256), gx2 = gc.getContext('2d');
  const id = gx2.createImageData(256, 256);
  for (let i = 0; i < 256 * 256; i++) {
    const v = Math.floor(rnd(i, g + 100) * 255);
    id.data[i * 4] = id.data[i * 4 + 1] = id.data[i * 4 + 2] = v;
    id.data[i * 4 + 3] = 255;
  }
  gx2.putImageData(id, 0, 0);
  GRAIN.push(ctx.createPattern(gc, 'repeat'));
}
const VIG = (() => {
  const v = mk(W, H), vx = v.getContext('2d');
  const g = vx.createRadialGradient(W / 2, H / 2, H * 0.4, W / 2, H / 2, H * 1.05);
  g.addColorStop(0, 'rgba(20,6,14,0)'); g.addColorStop(1, 'rgba(20,6,14,0.42)');
  vx.fillStyle = g; vx.fillRect(0, 0, W, H);
  return v;
})();
function rgbSplit(dst, src, off, oy) {
  const ch = [['#ff0000', -off, -oy], ['#00ff00', 0, 0], ['#0000ff', off, oy]];
  dst.save();
  dst.fillStyle = '#000'; dst.fillRect(0, 0, W, H);
  for (const [col, dx, dy] of ch) {
    tx2.globalCompositeOperation = 'source-over';
    tx2.drawImage(src, 0, 0);
    tx2.globalCompositeOperation = 'multiply';
    tx2.fillStyle = col; tx2.fillRect(0, 0, W, H);
    dst.globalCompositeOperation = 'lighter';
    dst.drawImage(tmp2, dx, dy);
  }
  dst.restore();
  tx2.globalCompositeOperation = 'source-over';
}
// how dark/photographic the frame is (drives grain + vignette)
function moodAt(t) {
  if (t < K.white) return 1;
  if (t < T(7, 3)) return 0;
  if (t < K.drop) return 1;
  if (t < K.endcard) return t >= K.powering ? 1 : 0;
  return 0;
}

function render(t) {
  bx.setTransform(1, 0, 0, 1, 0, 0);
  bx.globalAlpha = 1;
  bx.globalCompositeOperation = 'source-over';
  bx.filter = 'none';
  bx.save(); compose(bx, t); bx.restore();
  const f = fxState(t);
  let src = buf;
  // seismic slice displacement
  if (f.quake > 0.02) {
    tx.drawImage(src, 0, 0);
    const fr = Math.floor(t * 60);
    let y = 0, i = 0;
    while (y < H) {
      const h = 6 + rnd(fr, i * 3) * 46;
      const dx = Math.sin(y * 0.013 + t * 47) * f.quake * 26 + (rnd(fr, i * 3 + 1) - 0.5) * f.quake * 40;
      tx.drawImage(src, 0, y, W, h, dx, y + (rnd(fr, i * 3 + 2) - 0.5) * f.quake * 6, W, h);
      y += h; i++;
    }
    src = tmp;
  }
  if (f.glitch > 0.03) {
    const seed = Math.floor(t * 30);
    // rgbSplit writes into tmp, so move a quake-displaced frame out of the way first
    if (src === tmp) { la.drawImage(tmp, 0, 0); src = LA; }
    rgbSplit(tx, src, 4 + f.glitch * 22 * rnd(seed, 1), (rnd(seed, 2) - 0.5) * 6 * f.glitch);
    const nb = Math.floor(3 + f.glitch * 9);
    for (let i = 0; i < nb; i++) {
      const y = rnd(seed, 10 + i) * H, h = 8 + rnd(seed, 30 + i) * 80 * f.glitch;
      const dx = (rnd(seed, 50 + i) - 0.5) * 200 * f.glitch;
      tx.drawImage(src, 0, y, W, h, dx, y, W, h);
    }
    src = tmp;
  }
  // bloom source
  sx.filter = 'blur(6px)';
  sx.clearRect(0, 0, 480, 270);
  sx.drawImage(src, 0, 0, 480, 270);
  sx.filter = 'none';
  sx2.filter = 'blur(12px)';
  sx2.clearRect(0, 0, 240, 135);
  sx2.drawImage(src, 0, 0, 240, 135);
  sx2.filter = 'none';
  // composite with camera
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  const shx = (Math.sin(t * 93.1) * 0.6 + Math.sin(t * 57.3 + 1) * 0.4) * f.shake;
  const shy = (Math.sin(t * 71.7 + 2) * 0.6 + Math.sin(t * 113.9) * 0.4) * f.shake;
  const z = 1 + f.punch + 0.012 + f.quake * 0.01;
  ctx.save();
  ctx.translate(W / 2 + shx, H / 2 + shy);
  ctx.scale(z, z);
  ctx.rotate(f.shake * 0.0005 * Math.sin(t * 40));
  ctx.translate(-W / 2, -H / 2);
  if (f.whip > 0.02) {
    const n = 7;
    for (let k = 0; k < n; k++) {
      ctx.globalAlpha = k === 0 ? 1 : 0.3;
      ctx.drawImage(src, (k - (n - 1) / 2) * f.whip * 80, 0);
    }
    ctx.globalAlpha = 1;
  } else ctx.drawImage(src, 0, 0);
  const mood = moodAt(t);
  ctx.globalCompositeOperation = 'screen';
  ctx.globalAlpha = 0.1 + 0.08 * mood;
  ctx.drawImage(sm, 0, 0, W, H);
  ctx.globalAlpha = 0.08 + 0.1 * mood;
  ctx.drawImage(sm2, 0, 0, W, H);
  ctx.restore();
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 0.35 + 0.65 * mood;
  ctx.drawImage(VIG, 0, 0);
  ctx.globalAlpha = 1;
  if (f.glitch > 0.05) {
    ctx.globalAlpha = f.glitch * 0.2;
    ctx.fillStyle = '#000';
    for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 2);
    ctx.globalAlpha = 1;
  }
  // film grain
  ctx.save();
  ctx.globalCompositeOperation = 'overlay';
  ctx.globalAlpha = 0.05 + 0.07 * mood;
  const fr = Math.floor(t * 30), gi = fr % 4;
  ctx.translate(Math.floor(rnd(gi, fr) * 256), Math.floor(rnd(gi + 9, fr) * 256));
  ctx.fillStyle = GRAIN[gi];
  ctx.fillRect(-256, -256, W + 512, H + 512);
  ctx.restore();
  if (f.flash > 0.01) {
    ctx.fillStyle = `rgba(255,250,252,${f.flash})`;
    ctx.fillRect(0, 0, W, H);
  }
}

window.renderFrame = (t, q = 0.93) => { render(t); return canvas.toDataURL('image/jpeg', q); };
window.renderOnly = (t) => { render(t); return true; };
window.DURATION = DURATION;
