'use strict';
// All scenes. Each takes (c, t) and draws a full 1920x1080 frame.

// =================================================================== 0. SIGNAL
const PEN_X = 1480, TRACE_V = 250, TRACE_Y = 650;
function traceAmp(tau) {
  let a = 2.2;
  for (let b = 1; b < 4; b++) {
    const tb = T(b);
    if (tau > tb) a += (14 + b * 16) * Math.exp(-(tau - tb) * 3.2);
    if (tau > T(b, 0.45)) a += (8 + b * 8) * Math.exp(-(tau - T(b, 0.45)) * 4);
  }
  if (tau > T(3)) a += 6 * Math.exp((tau - T(3)) * 1.7);
  if (tau > T(3, 2)) a += Math.pow((tau - T(3, 2)) / 0.75, 2) * 170;
  return a;
}
const traceSig = (tau) => Math.sin(tau * 38.3) * 0.45 + Math.sin(tau * 71.1 + 1) * 0.3 + Math.sin(tau * 131.7 + 2) * 0.18 + Math.sin(tau * 17.9) * 0.25;
function traceY(x, t) {
  const tau = t - (PEN_X - x) / TRACE_V;
  if (tau < 0.15) return null;
  return TRACE_Y + traceAmp(tau) * traceSig(tau);
}
function hud(c, t, al, extra = {}) {
  const o = { fam: MONO, weight: 400, size: 15, color: `rgba(255,255,255,${0.55 * al})`, align: 'left', ls: 1.5 };
  text(c, 'SEISMIC / SIGNAL MONITOR', 64, 58, o);
  const fr = Math.floor(t * 30);
  const tc = [Math.floor(fr / 108000), Math.floor(fr / 1800) % 60, Math.floor(fr / 30) % 60, fr % 30].map((v) => String(v).padStart(2, '0')).join(':');
  text(c, 'REC  ' + tc, W - 64, 58, { ...o, align: 'right' });
  c.fillStyle = `rgba(255,70,80,${al * (Math.floor(t * 2) % 2 ? 0.9 : 0.3)})`;
  c.beginPath(); c.arc(W - 64 - textW(c, 'REC  ' + tc, o) - 16, 58, 5, 0, TAU); c.fill();
  text(c, extra.bl || '40.7128° N   74.0060° W', 64, H - 54, o);
  text(c, extra.br || '© 2026', W - 64, H - 54, { ...o, align: 'right' });
}
function sSignal(c, t) {
  c.fillStyle = '#0d090c';
  c.fillRect(0, 0, W, H);
  const tension = clamp(t / K.quake);
  const g = c.createRadialGradient(W / 2, TRACE_Y, 0, W / 2, TRACE_Y, 1100);
  g.addColorStop(0, `rgba(130,90,109,${0.12 + tension * 0.22})`);
  g.addColorStop(1, 'rgba(130,90,109,0)');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  // recording paper grid
  const al = clamp(t / 0.8);
  c.lineWidth = 1;
  for (let k = -1; k < 26; k++) {
    const x = ((k * 80 - t * TRACE_V) % 2080 + 2080) % 2080 - 80;
    const major = ((k % 5) + 5) % 5 === 0;
    c.strokeStyle = `rgba(255,255,255,${(major ? 0.07 : 0.035) * al})`;
    c.beginPath(); c.moveTo(x, 0); c.lineTo(x, H); c.stroke();
  }
  for (let k = 0; k < 14; k++) {
    const y = TRACE_Y + (k - 7) * 80;
    c.strokeStyle = `rgba(255,255,255,${(k === 7 ? 0.08 : 0.03) * al})`;
    c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke();
  }
  // trace
  c.beginPath();
  let started = false, py = TRACE_Y;
  for (let x = 0; x <= PEN_X; x += 2) {
    const y = traceY(x, t);
    if (y == null) continue;
    if (!started) { c.moveTo(x, y); started = true; } else c.lineTo(x, y);
    py = y;
  }
  c.save();
  c.globalCompositeOperation = 'lighter';
  c.strokeStyle = 'rgba(190,120,160,0.35)'; c.lineWidth = 9; c.lineJoin = 'round'; c.stroke();
  c.restore();
  c.strokeStyle = 'rgba(245,238,242,0.95)'; c.lineWidth = 2.2; c.stroke();
  // pen
  c.strokeStyle = 'rgba(255,255,255,0.12)';
  c.setLineDash([4, 8]);
  c.beginPath(); c.moveTo(PEN_X, 0); c.lineTo(PEN_X, H); c.stroke();
  c.setLineDash([]);
  c.save();
  c.globalCompositeOperation = 'lighter';
  sprite(c, SPR.mauve, PEN_X, py, 60, 0.9);
  sprite(c, SPR.white, PEN_X, py, 14, 1);
  c.restore();
  c.globalAlpha = 1;
  // magnitude readout
  const mag = 0.9 + 8.2 * Math.pow(tension, 3.2) + (Math.sin(t * 40) * 0.05);
  text(c, 'MAG ' + mag.toFixed(1), PEN_X + 26, py - 26, { fam: MONO, size: 18, weight: 500, color: 'rgba(255,255,255,0.8)', align: 'left', ls: 1 });
  hud(c, t, al);
  // copy
  const o = { fam: SERIF, weight: 400, size: 66, color: '#f4eef1', ls: -0.6 };
  maskRise(c, 'Money moves at the speed of the internet.', W / 2, 400, o, t, T(0, 2), { out: T(1, 3.4), stagger: 0.07 });
  maskRise(c, 'The rails underneath it never caught up.', W / 2, 400, o, t, T(2), { out: T(2, 3.4), stagger: 0.07 });
  maskRise(c, 'Something has to shift.', W / 2, 400, { ...o, size: 84 }, t, T(3), { stagger: 0.12, colors: [null, null, null, '#d9a9c3'] });
  // the breath before the hit
  const bk = P(t, T(3, 3.55), 0.2);
  if (bk > 0) { c.fillStyle = `rgba(0,0,0,${bk * 0.92})`; c.fillRect(0, 0, W, H); }
}

// =================================================================== 1. QUAKE -> LOGO
const LOGO_R = 235;
const SHARDS = [];
for (let i = 0; i < 260; i++) {
  const f = i % 5;
  const ox = 40 + (i / 260) * 1840 + (rnd(i, 1) - 0.5) * 20;
  const spike = (rnd(i, 2) - 0.5) * 520 * Math.exp(-Math.pow((ox - 960) / 650, 2));
  // random point inside facet f (triangle fan)
  const fv = FACETS[f];
  const tri = 1 + Math.floor(rnd(i, 3) * (fv.length - 2));
  let a = rnd(i, 4), b = rnd(i, 5);
  if (a + b > 1) { a = 1 - a; b = 1 - b; }
  const tx = fv[0][0] + (fv[tri][0] - fv[0][0]) * a + (fv[tri + 1][0] - fv[0][0]) * b;
  const ty = fv[0][1] + (fv[tri][1] - fv[0][1]) * a + (fv[tri + 1][1] - fv[0][1]) * b;
  SHARDS.push({
    f, ox, oy: TRACE_Y + spike,
    vx: (rnd(i, 6) - 0.5) * 1700, vy: (rnd(i, 7) - 0.5) * 1300 - 200, vz: (rnd(i, 8) - 0.35) * 2600,
    sz: 7 + Math.pow(rnd(i, 9), 2) * 34, rot: rnd(i, 10) * TAU, spin: (rnd(i, 11) - 0.5) * 9, flip: 2 + rnd(i, 12) * 9,
    tx, ty, arr: K.facets[f] - 0.62 + rnd(i, 13) * 0.18,
    shape: [rnd(i, 14), rnd(i, 15), rnd(i, 16)],
  });
}
const FOC = 1100;
function shardPos(s, t) {
  const dt = t - K.quake, k = 1.7;
  const e = (1 - Math.exp(-k * dt)) / k;
  let x = s.ox + s.vx * e, y = s.oy + s.vy * e, z = s.vz * e;
  // slow orbital swirl
  const sw = dt * 0.35;
  const dx = x - W / 2, dz = z;
  x = W / 2 + dx * Math.cos(sw) - dz * Math.sin(sw) * 0.4;
  z = dx * Math.sin(sw) * 0.4 + dz * Math.cos(sw);
  if (t > s.arr) {
    const p = E.inCubic(P(t, s.arr, K.facets[s.f] - s.arr));
    x = lerp(x, W / 2 + s.tx * LOGO_R, p);
    y = lerp(y, H / 2 + s.ty * LOGO_R, p);
    z = lerp(z, 0, p);
  }
  return [x, y, z];
}
function facet3D(c, i, cx, cy, r, z, rx, ry) {
  const f = FACETS[i];
  const cxr = Math.cos(rx), sxr = Math.sin(rx), cyr = Math.cos(ry), syr = Math.sin(ry);
  c.beginPath();
  f.forEach(([x, y], k) => {
    const X0 = (x - FCENT[i][0]) * r, Y0 = (y - FCENT[i][1]) * r;
    const X1 = X0 * cyr, Z1 = -X0 * syr;
    const Y2 = Y0 * cxr - Z1 * sxr, Z2 = Y0 * sxr + Z1 * cxr;
    const s = FOC / (FOC + z + Z2);
    const px = W / 2 + (cx - W / 2 + FCENT[i][0] * r + X1) * s, py = H / 2 + (cy - H / 2 + FCENT[i][1] * r + Y2) * s;
    if (k) c.lineTo(px, py); else c.moveTo(px, py);
  });
  c.closePath();
  return Math.abs(cyr * cxr);
}
function sAssemble(c, t) {
  const dt = t - K.quake;
  c.fillStyle = '#0e0a0d';
  c.fillRect(0, 0, W, H);
  const glowA = 0.25 + 0.35 * P(t, K.facets[0], 1.5);
  const g = c.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, 900);
  g.addColorStop(0, `rgba(150,100,128,${glowA})`);
  g.addColorStop(0.5, `rgba(90,52,74,${glowA * 0.4})`);
  g.addColorStop(1, 'rgba(60,30,50,0)');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  // drifting dust
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 90; i++) {
    const x = (rnd(i, 61) * W + t * (10 + rnd(i, 62) * 30)) % W, y = (rnd(i, 63) * H - t * (6 + rnd(i, 64) * 14) + H * 4) % H;
    sprite(c, SPR.mauve, x, y, 2 + rnd(i, 65) * 5, 0.25 + 0.3 * Math.sin(t * 2 + i));
  }
  c.restore();
  c.globalAlpha = 1;
  // shockwave rings
  for (let k = 0; k < 3; k++) {
    const p = P(dt, k * 0.12, 1.6);
    if (p <= 0 || p >= 1) continue;
    const r = E.outCubic(p) * 2300;
    c.strokeStyle = `rgba(255,${220 - k * 40},${240 - k * 30},${(1 - p) * (0.75 - k * 0.2)})`;
    c.lineWidth = 2 + (1 - p) * 26;
    c.beginPath(); c.ellipse(W / 2, TRACE_Y, r, r * 0.55, 0, 0, TAU); c.stroke();
  }
  // shards (painter sorted by depth)
  const vis = [];
  for (const s of SHARDS) {
    if (t >= K.facets[s.f]) continue;
    const p = shardPos(s, t);
    if (FOC + p[2] < 60) continue;
    vis.push([p[2], s, p]);
  }
  vis.sort((a, b) => b[0] - a[0]);
  for (const [, s, p] of vis) {
    const sc = FOC / (FOC + p[2]);
    const x = W / 2 + (p[0] - W / 2) * sc, y = H / 2 + (p[1] - H / 2) * sc;
    const arrive = P(t, s.arr, K.facets[s.f] - s.arr);
    const sz = s.sz * sc * (1 - arrive * 0.6);
    const a = s.rot + s.spin * dt;
    const fl = Math.cos(s.rot * 3 + s.flip * dt);
    c.save();
    c.translate(x, y);
    c.rotate(a);
    c.scale(1, fl);
    c.beginPath();
    c.moveTo(-sz * (0.4 + s.shape[0] * 0.6), -sz * 0.3);
    c.lineTo(sz * (0.3 + s.shape[1] * 0.7), -sz * (0.2 + s.shape[2] * 0.4));
    c.lineTo(sz * 0.1, sz * (0.5 + s.shape[0] * 0.5));
    c.closePath();
    const lit = Math.pow(Math.abs(fl), 6);
    c.fillStyle = rgba(mix3(FCOL_RGB[s.f], [255, 236, 246], 0.25 + 0.75 * lit * (1 - arrive)));
    c.globalAlpha = clamp(dt * 8);
    c.fill();
    c.restore();
  }
  c.globalAlpha = 1;
  // facets landing
  const push = 1 + 0.035 * E.inOutSine(P(t, K.facets[0], K.white - K.facets[0]));
  for (let i = 0; i < 5; i++) {
    const tf = K.facets[i];
    if (t < tf - 0.32) continue;
    const p = E.outExpo(P(t, tf - 0.32, 0.32));
    const z = lerp(-820, 0, p);
    const sgn = i % 2 ? 1 : -1;
    c.save();
    c.translate(W / 2, H / 2); c.scale(push, push); c.translate(-W / 2, -H / 2);
    const lit = facet3D(c, i, W / 2, H / 2, LOGO_R, z, (1 - p) * 1.1 * sgn, (1 - p) * 1.6 * -sgn);
    c.globalAlpha = clamp(p * 2.5);
    c.fillStyle = rgba(mix3(FCOL_RGB[i], [255, 240, 248], (1 - lit) * 0.6));
    c.fill();
    const fl = Math.exp(-Math.max(0, t - tf) * 6) * (t >= tf ? 1 : 0);
    if (fl > 0.01) { c.fillStyle = `rgba(255,246,250,${fl * 0.85})`; c.fill(); }
    c.restore();
    // ripple from facet
    if (t >= tf) {
      const q = P(t, tf, 0.9);
      if (q < 1) {
        const cx = W / 2 + FCENT[i][0] * LOGO_R * push, cy = H / 2 + FCENT[i][1] * LOGO_R * push;
        c.strokeStyle = `rgba(230,190,212,${(1 - q) * 0.6})`;
        c.lineWidth = 2;
        c.beginPath(); c.arc(cx, cy, 30 + E.outCubic(q) * 420, 0, TAU); c.stroke();
      }
    }
  }
  c.globalAlpha = 1;
  // specular sweep across the assembled mark
  const sw = P(t, T(6), BEAT * 2);
  if (sw > 0 && sw < 1) {
    c.save();
    c.translate(W / 2, H / 2); c.scale(push, push); c.translate(-W / 2, -H / 2);
    c.beginPath();
    for (let i = 0; i < 5; i++) {
      const f = FACETS[i];
      c.moveTo(W / 2 + f[0][0] * LOGO_R, H / 2 + f[0][1] * LOGO_R);
      for (let k = 1; k < f.length; k++) c.lineTo(W / 2 + f[k][0] * LOGO_R, H / 2 + f[k][1] * LOGO_R);
      c.closePath();
    }
    c.clip();
    const x = lerp(W / 2 - 400, W / 2 + 400, E.inOutSine(sw));
    const gr = c.createLinearGradient(x - 140, H / 2 - 300, x + 140, H / 2 + 300);
    gr.addColorStop(0, 'rgba(255,255,255,0)');
    gr.addColorStop(0.5, 'rgba(255,240,248,0.42)');
    gr.addColorStop(1, 'rgba(255,255,255,0)');
    c.fillStyle = gr; c.fillRect(0, 0, W, H);
    c.restore();
  }
  hud(c, t, 0.7 * (1 - P(t, K.facets[0], 0.6)));
}

// =================================================================== 2. WORDMARK (white)
const LOCK_SIZE = 150, LOCK_Y = 470;
function sWordmark(c, t) {
  c.fillStyle = C.g50;
  c.fillRect(0, 0, W, H);
  // faint construction lines
  c.strokeStyle = 'rgba(0,0,0,0.04)';
  c.lineWidth = 1;
  for (let x = 160; x < W; x += 160) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, H); c.stroke(); }
  const gm = lockupGeom(c, W / 2, LOCK_SIZE);
  const p = E.outExpo(P(t, K.white, 0.8));
  const lx = lerp(W / 2, gm.lx, p), ly = lerp(H / 2, LOCK_Y, p), r = lerp(LOGO_R, gm.r, p);
  // wordmark slides out from behind the mark
  const q = E.outExpo(P(t, K.white + 0.3, 0.9));
  c.save();
  c.beginPath();
  c.rect(lx + r * LOGO_HW + 6, 0, W, H);
  c.clip();
  wordmark(c, gm.wx - (1 - q) * (gm.ww + 60), LOCK_Y + LOCK_SIZE * 0.06, LOCK_SIZE, C.black);
  c.restore();
  logo(c, lx, ly, r);
  // tagline in three beats
  const o = { size: 46, weight: 400, color: C.g500, ls: -0.6 };
  const parts = ['A new standard', 'for financial', 'infrastructure.'];
  const sp = textW(c, ' ', o);
  const ws = parts.map((s) => textW(c, s, o));
  let x = W / 2 - (ws[0] + ws[1] + ws[2] + sp * 2) / 2;
  parts.forEach((s, i) => {
    maskRise(c, s, x, LOCK_Y + 190, { ...o, align: 'left', color: i === 2 ? C.mauve : C.g500 }, t, T(7, i), { stagger: 0.04 });
    x += ws[i] + sp;
  });
  deckFrame(c, 'Introduction', P(t, K.white + 0.4, 0.6));
}
// zoom through the big facet into the first city
function sZoomIn(c, t) {
  const t0 = T(7, 3) - 0.08, t1 = K.ch1 + 0.12;
  const p = E.inCubic(P(t, t0, t1 - t0));
  const s = Math.exp(Math.log(70) * p);
  la.save(); sWordmark(la, T(7, 3) - 0.001); la.restore();
  lb.save(); sCh1(lb, t); lb.restore();
  const gm = lockupGeom(c, W / 2, LOCK_SIZE);
  const ax = gm.lx + FCENT[0][0] * gm.r, ay = LOCK_Y + FCENT[0][1] * gm.r;
  c.save();
  c.translate(W / 2, H / 2); c.scale(s, s); c.translate(-ax, -ay);
  c.drawImage(LA, 0, 0);
  facetPath(c, 0, gm.lx, LOCK_Y, gm.r);
  c.restore();
  c.save();
  c.clip();
  c.drawImage(LB, 0, 0);
  c.restore();
}

// =================================================================== 3. CHAPTERS (city + hero UI)
const CH_TABS = ['Create Account', 'Send Payments', 'Bundle Services'];
const CARD = { x: (W - 960) / 2, y: 92, w: 960, h: 172 };
const PANEL = { x: CARD.x, y: CARD.y + CARD.h + 16, w: 960, h: 520 };
function heroCard(c, t, t0, d) {
  const p = E.outExpo(P(t, t0, 0.7));
  if (p <= 0) return;
  c.save();
  c.translate(0, (1 - p) * -40);
  glass(c, CARD.x, CARD.y, CARD.w, CARD.h, { alpha: p });
  c.globalAlpha = p;
  const x = CARD.x, y = CARD.y;
  logo(c, x + 46, y + 44, 19, { cols: FCOL.map(() => '#5d4a55') });
  text(c, 'Seismic', x + 70, y + 46, { fam: SERIF, weight: 500, size: 31, color: '#4a3f46', align: 'left', ls: -0.3 });
  text(c, d.country, x + CARD.w - 66, y + 46, { size: 22, weight: 450, color: C.g800, align: 'right', ls: 0 });
  const pulse = 0.5 + 0.5 * Math.sin(t * 4);
  c.fillStyle = C.mauve;
  c.beginPath(); c.arc(x + CARD.w - 42, y + 46, 9, 0, TAU); c.fill();
  c.strokeStyle = `rgba(130,90,109,${0.5 * (1 - pulse)})`;
  c.lineWidth = 2;
  c.beginPath(); c.arc(x + CARD.w - 42, y + 46, 9 + pulse * 10, 0, TAU); c.stroke();
  c.fillStyle = 'rgba(0,0,0,0.12)';
  c.fillRect(x + 36, y + 88, CARD.w - 72, 1.5);
  maskRise(c, d.name, x + 40, y + 131, { fam: SERIF, weight: 400, size: 40, color: C.g800, align: 'left', ls: -0.3 }, t, t0 + 0.15, { stagger: 0.05 });
  pill(c, x + CARD.w - 36, y + 131, d.type, { align: 'right', size: 20 });
  c.restore();
}
function panelBox(c, t, t0) {
  const p = E.outExpo(P(t, t0, 0.6));
  if (p <= 0) return 0;
  const h = PANEL.h * p;
  glass(c, PANEL.x, PANEL.y, PANEL.w, h, { alpha: clamp(p * 2) });
  return p;
}
function tabs(c, t, idx, t0) {
  const y = 968, x0 = W / 2 - 555, sw = 370;
  const ap = E.outExpo(P(t, t0 - 0.3, 0.5));
  c.save();
  c.globalAlpha = ap;
  CH_TABS.forEach((s, i) => {
    const active = i === idx;
    text(c, s, x0 + sw * i + sw / 2, y, { size: 31, weight: active ? 500 : 400, color: active ? '#ffffff' : 'rgba(255,255,255,0.58)', ls: -0.3 });
  });
  c.fillStyle = 'rgba(255,255,255,0.3)';
  c.fillRect(x0, y + 36, sw * 3, 1.5);
  const prog = clamp((t - t0) / (BAR * 4));
  c.fillStyle = '#ffffff';
  c.fillRect(x0 + sw * idx, y + 34.5, sw * prog, 4);
  c.restore();
}
function row(c, x, y, w, label, al = 1) {
  c.save();
  c.globalAlpha *= al;
  text(c, label, x, y, { size: 22, weight: 400, color: C.g500, align: 'left', ls: 0 });
  c.fillStyle = 'rgba(0,0,0,0.08)';
  c.fillRect(x, y + 34, w, 1);
  c.restore();
}
function chapterFrame(c, t, idx, t0, d, panelFn) {
  grade(c, 1);
  updateGlass(c.canvas);
  heroCard(c, t, T(idx * 4 + 8, 2) - 0.1, d);
  const pp = panelBox(c, t, T(idx * 4 + 8, 3));
  if (pp > 0.98) {
    c.save();
    rr(c, PANEL.x, PANEL.y, PANEL.w, PANEL.h, 8);
    c.clip();
    panelFn(c, t, PANEL.x + 40, PANEL.y, PANEL.w - 80);
    c.restore();
  }
  tabs(c, t, idx, t0);
  // HUD in the corners, like a camera feed
  const o = { fam: MONO, weight: 400, size: 14, color: 'rgba(255,255,255,0.6)', ls: 1.5 };
  text(c, `0${idx + 1} / 03`, 64, 58, { ...o, align: 'left' });
  text(c, d.coords, 64, H - 54, { ...o, align: 'left' });
  text(c, CH_TABS[idx].toUpperCase(), W - 64, 58, { ...o, align: 'right' });
  text(c, 'seismic.systems', W - 64, H - 54, { ...o, align: 'right' });
}

function sCh1(c, t) {
  const u = (t - T(7, 3)) / (K.ch2 - T(7, 3) + 0.3);
  viewAvenue(c, t, u);
  chapterFrame(c, t, 0, K.ch1, { country: 'Brazil', name: 'Grupo Financeiro São Vicente', type: 'Business', coords: '23.5614° S   46.6559° W' }, (c, t, x, y, w) => {
    text(c, 'New account', x, y + 44, { size: 22, weight: 500, color: C.g800, align: 'left', ls: 0 });
    text(c, 'ACC · 2026 · BR', x + w, y + 44, { fam: MONO, size: 16, color: C.g500, align: 'right', ls: 1 });
    c.fillStyle = 'rgba(0,0,0,0.1)'; c.fillRect(x, y + 78, w, 1);
    const F = [['Legal entity', 'Grupo Financeiro São Vicente', T(9), 0.75], ['Jurisdiction', 'Brazil', T(9, 2), 0.35], ['Account type', 'Business', T(10), 0.4]];
    F.forEach(([lab, val, ts, dur], i) => {
      const yy = y + 128 + i * 72;
      row(c, x, yy, w, lab, E.outCubic(P(t, T(8, 3) + 0.2 + i * 0.08, 0.4)));
      typeOn(c, val, x + 260, yy, { size: 30, weight: 500, color: C.black, ls: -0.4 }, t, ts, dur);
    });
    const CK = [['Identity verified', T(10, 2)], ['Compliance screening', T(10, 3)], ['Account ready', T(11)]];
    CK.forEach(([lab, ts], i) => {
      const xx = x + i * 300, yy = y + 368;
      const p = P(t, ts, 0.45);
      c.save();
      c.globalAlpha = clamp(P(t, ts - 0.2, 0.2) * 0.65 + p);
      checkMark(c, xx + 16, yy, 15, p);
      if (p <= 0) { c.strokeStyle = 'rgba(0,0,0,0.2)'; c.lineWidth = 2; c.beginPath(); c.arc(xx + 16, yy, 15, 0, TAU); c.stroke(); }
      text(c, lab, xx + 44, yy + 1, { size: 22, weight: 450, color: C.g800, align: 'left', ls: 0 });
      c.restore();
    });
    // status bar
    const bp = E.inOut(P(t, T(10, 2), T(11, 2) - T(10, 2)));
    const yy = y + 448;
    c.fillStyle = 'rgba(0,0,0,0.07)'; rr(c, x, yy, w, 44, 6); c.fill();
    c.fillStyle = C.mauve; rr(c, x, yy, Math.max(12, w * bp), 44, 6); c.fill();
    const done = t >= T(11, 2);
    text(c, done ? 'Account active' : bp > 0 ? 'Verifying…' : 'Awaiting details', x + 22, yy + 23, { size: 20, weight: 500, color: bp > 0.12 ? '#fff' : C.g500, align: 'left', ls: 0 });
    if (done) {
      const q = P(t, T(11, 2), 0.8);
      c.strokeStyle = `rgba(255,255,255,${1 - q})`;
      c.lineWidth = 3;
      rr(c, x - q * 14, yy - q * 14, w + q * 28, 44 + q * 28, 6 + q * 8); c.stroke();
      text(c, 'LIVE', x + w - 22, yy + 23, { fam: MONO, size: 16, weight: 500, color: '#fff', align: 'right', ls: 2 });
    }
  });
}

function money(v) { return '$' + v.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ','); }
function beam(c, t, t0) {
  const p = P(t, t0, 1.3);
  if (p <= 0 || p >= 1) return;
  const Z = [W + 200, -260];
  const sx = PANEL.x + 140, sy = PANEL.y + 410;
  const head = E.outExpo(clamp(p * 1.6));
  const hx = lerp(sx, Z[0], head), hy = lerp(sy, Z[1], head);
  const fade = 1 - E.inCubic(P(p, 0.45, 0.55));
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (const [lw, a] of [[46, 0.12], [16, 0.35], [4, 0.95]]) {
    const g = c.createLinearGradient(sx, sy, hx, hy);
    g.addColorStop(0, `rgba(214,150,190,${a * 0.2 * fade})`);
    g.addColorStop(1, `rgba(255,236,246,${a * fade})`);
    c.strokeStyle = g; c.lineWidth = lw; c.lineCap = 'round';
    c.beginPath(); c.moveTo(sx, sy); c.lineTo(hx, hy); c.stroke();
  }
  sprite(c, SPR.white, hx, hy, 90, fade);
  for (let i = 0; i < 40; i++) {
    const u = rnd(i, 71) * head;
    const px = lerp(sx, hx, u) + (rnd(i, 72) - 0.5) * 80 * p, py = lerp(sy, hy, u) + (rnd(i, 73) - 0.5) * 40;
    sprite(c, SPR.mauve, px, py, 3 + rnd(i, 74) * 8, fade * 0.8);
  }
  c.restore();
  c.globalAlpha = 1;
}
function sCh2(c, t) {
  const u = (t - (K.ch2 - 0.3)) / (BAR * 4 + 0.6);
  viewWorm(c, t, u);
  chapterFrame(c, t, 1, K.ch2, { country: 'New York', name: 'Park Finance', type: 'Business', coords: '40.7128° N   74.0060° W' }, (c, t, x, y, w) => {
    text(c, 'Send payment', x, y + 44, { size: 22, weight: 500, color: C.g800, align: 'left', ls: 0 });
    if (t >= T(14)) scramble(c, 'TX · 7f3a 91c2 · e77b', x + w, y + 44, { fam: MONO, size: 16, color: C.g500, align: 'right', ls: 1 }, t, T(14), 0.5);
    c.fillStyle = 'rgba(0,0,0,0.1)'; c.fillRect(x, y + 78, w, 1);
    row(c, x, y + 128, w, 'From', E.outCubic(P(t, T(12, 3) + 0.2, 0.4)));
    maskRise(c, 'Park Finance — New York', x + 160, y + 128, { size: 28, weight: 500, color: C.black, align: 'left', ls: -0.3 }, t, T(12, 3) + 0.25);
    row(c, x, y + 198, w, 'To', E.outCubic(P(t, T(12, 3) + 0.3, 0.4)));
    maskRise(c, 'Grupo Financeiro São Vicente — Brazil', x + 160, y + 198, { size: 28, weight: 500, color: C.black, align: 'left', ls: -0.3 }, t, T(12, 3) + 0.35);
    // amount
    const ay = y + 318;
    const v = 2400000 * E.outExpo(P(t, T(13), 1.0));
    const amt = money(v);
    const ao = { size: 96, weight: 500, color: C.black, align: 'left', ls: -3 };
    if (t < T(13, 3)) text(c, amt, x - 4, ay, ao);
    else {
      const enc = '0x8f3a91c2e77b04d9';
      const q = P(t, T(13, 3), 0.75);
      if (q < 1) scramble(c, amt, x - 4, ay, ao, t, T(13, 3) - 0.6, 1.2, { scol: C.mauve });
      else text(c, enc, x, ay, { fam: MONO, size: 64, weight: 500, color: C.mauve, align: 'left', ls: 0 });
    }
    pill(c, x + w, ay, 'USD', { align: 'right', size: 20 });
    // shielded badge
    const sp = E.outBack(P(t, T(14), 0.45));
    if (sp > 0) {
      c.save();
      c.translate(x, y + 410);
      c.globalAlpha = clamp(sp);
      rr(c, 0, -22, 196, 44, 22); c.fillStyle = C.purple; c.fill();
      icon(c, 'lock', 30, 0, 22, '#fff');
      text(c, 'Encrypted', 52, 1, { size: 20, weight: 500, color: '#fff', align: 'left', ls: 0 });
      c.restore();
    }
    // settled
    const st = P(t, T(15), 0.45);
    const yy = y + 470;
    if (st > 0) {
      checkMark(c, x + w - 180, y + 410, 15, st);
      text(c, 'Settled', x + w - 150, y + 411, { size: 22, weight: 500, color: C.g800, align: 'left', ls: 0 });
      c.globalAlpha = E.outCubic(st);
      text(c, 'Only sender and receiver can see this payment.', x, yy, { size: 20, weight: 400, color: C.g500, align: 'left', ls: 0 });
      c.globalAlpha = 1;
    }
  });
  beam(c, t, T(14, 1));
}

const SERVICES = [['accounts', 'Accounts'], ['payments', 'Payments'], ['cards', 'Cards'], ['fx', 'FX'], ['compliance', 'Compliance'], ['treasury', 'Treasury']];
function sCh3(c, t) {
  const u = (t - (K.ch3 - 0.3)) / (BAR * 4 + 0.3);
  const ang = 0.9 + u * 0.9;
  const R = 300 - u * 60, Hc = 230 - u * 50;
  const cam = lookAt(Math.sin(ang) * R, Hc, Math.cos(ang) * R, 0, 20, 0, 0, 60);
  viewAerial(c, t, cam, LOOK.dusk);
  // energy build into the drop
  const bl = P(t, T(19), BAR);
  chapterFrame(c, t, 2, K.ch3, { country: 'Germany', name: 'Lukas Müller', type: 'Personal', coords: '52.5200° N   13.4050° E' }, (c, t, x, y, w) => {
    text(c, 'Services', x, y + 44, { size: 22, weight: 500, color: C.g800, align: 'left', ls: 0 });
    text(c, 'ONE ACCOUNT · DE', x + w, y + 44, { fam: MONO, size: 16, color: C.g500, align: 'right', ls: 1 });
    c.fillStyle = 'rgba(0,0,0,0.1)'; c.fillRect(x, y + 78, w, 1);
    const snap = E.inOutExpo(P(t, T(18, 2) - 0.25, 0.5));
    const gap = lerp(18, 0, snap);
    const tw = (w - gap * 2) / 3, th = 150;
    SERVICES.forEach(([ic, lab], i) => {
      const ts = T(17, i * 0.5);
      const p = E.outBack(P(t, ts, 0.45));
      if (p <= 0) return;
      const cx = i % 3, cy = Math.floor(i / 3);
      const tx0 = x + cx * (tw + gap), ty0 = y + 108 + cy * (th + gap);
      c.save();
      c.translate(tx0 + tw / 2, ty0 + th / 2);
      c.scale(p, p);
      c.translate(-tw / 2, -th / 2);
      rr(c, 0, 0, tw, th, lerp(8, 0, snap));
      c.fillStyle = 'rgba(255,255,255,0.6)';
      c.fill();
      c.strokeStyle = `rgba(0,0,0,${0.08 * (1 - snap)})`; c.lineWidth = 1; c.stroke();
      icon(c, ic, 44, 48, 34, C.mauve);
      text(c, lab, 28, 108, { size: 28, weight: 500, color: C.black, align: 'left', ls: -0.3 });
      // toggle
      const on = E.outCubic(P(t, ts + 0.15, 0.25));
      rr(c, tw - 78, 30, 50, 28, 14); c.fillStyle = rgba(mix3([200, 196, 199], hex(C.mauve), on)); c.fill();
      c.beginPath(); c.arc(tw - 64 + on * 22, 44, 11, 0, TAU); c.fillStyle = '#fff'; c.fill();
      c.restore();
    });
    if (snap > 0) {
      c.save();
      c.strokeStyle = C.mauve;
      c.lineWidth = 2.5;
      c.globalAlpha = snap;
      rr(c, x - 6, y + 102, w + 12, th * 2 + 12, 4); c.stroke();
      c.restore();
      maskRise(c, 'Every service. One account.', x, y + 470, { size: 26, weight: 500, color: C.g800, align: 'left', ls: -0.3 }, t, T(18, 2));
    }
  });
  if (bl > 0) {
    c.fillStyle = `rgba(255,250,252,${E.inExpo(bl) * 0.9})`;
    c.fillRect(0, 0, W, H);
  }
}

function sWipe(c, t, t0, fa, fb) {
  la.save(); fa(la, t); la.restore();
  lb.save(); fb(lb, t); lb.restore();
  c.drawImage(LA, 0, 0);
  const p = E.inOutExpo(P(t, t0 - 0.3, 0.6));
  const S = 4200;
  const cx = lerp(W / 2 + 4800, W / 2 - FCENT[0][0] * S, p), cy = H / 2 - FCENT[0][1] * S + (1 - p) * 300;
  c.save();
  facetPath(c, 0, cx, cy, S);
  c.save();
  c.clip();
  c.drawImage(LB, 0, 0);
  c.restore();
  c.strokeStyle = 'rgba(255,240,248,0.9)';
  c.lineWidth = 4;
  c.stroke();
  c.globalCompositeOperation = 'lighter';
  c.strokeStyle = 'rgba(214,150,190,0.35)';
  c.lineWidth = 28;
  c.stroke();
  c.restore();
}

// =================================================================== 4. ALL IN ONE PLACE
const PHRASES = [
  [T(20), T(20, 2), 'Create accounts.', '01 / ACCOUNTS'],
  [T(20, 2), T(21), 'Send payments.', '02 / PAYMENTS'],
  [T(21), T(21, 2), 'Run compliance workflows.', '03 / COMPLIANCE'],
  [T(21, 2), T(22), 'And a whole lot more.', '04 / SERVICES'],
];
function gemOutline(c, x, y, r, rot, col, lw = 1) {
  c.save();
  c.translate(x, y); c.rotate(rot);
  c.strokeStyle = col; c.lineWidth = lw;
  for (let i = 0; i < 5; i++) { facetPath(c, i, 0, 0, r); c.stroke(); }
  c.restore();
}
function sKinetic(c, t) {
  const purple = t >= T(23);
  c.fillStyle = purple ? C.purple : C.g50;
  c.fillRect(0, 0, W, H);
  const ink = purple ? '#ffffff' : C.black;
  // background: slowly turning gem outline + grid
  c.strokeStyle = purple ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)';
  c.lineWidth = 1;
  for (let x = 160; x < W; x += 160) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, H); c.stroke(); }
  const gz = 1 + (t - K.drop) * 0.06;
  gemOutline(c, W / 2, H / 2, 620 * gz, (t - K.drop) * 0.05, purple ? 'rgba(255,255,255,0.12)' : 'rgba(130,90,109,0.12)', 1.5);
  // phrases
  for (const [t0, t1, s, lab] of PHRASES) {
    if (t < t0 - 0.01 || t > t1 + 0.2) continue;
    const words = s.split(' ');
    const cols = words.map((w, i) => (i === words.length - 1 ? C.mauve : null));
    const sz = s.length > 20 ? 128 : 160;
    const z = 1 + 0.04 * E.outExpo(P(t, t0, 1));
    c.save();
    c.translate(W / 2, H / 2); c.scale(z, z); c.translate(-W / 2, -H / 2);
    maskRise(c, s, W / 2, H / 2 + 10, { size: sz, weight: 500, color: ink, ls: -sz * 0.04 }, t, t0, { stagger: 0.045, dur: 0.45, out: t1 - 0.1, outDur: 0.14, colors: cols });
    c.restore();
    maskRise(c, lab, W / 2, H / 2 - 150, { fam: MONO, size: 18, weight: 500, color: C.g500, ls: 3 }, t, t0 + 0.05, { out: t1 - 0.1, outDur: 0.14 });
  }
  // All in one place.
  if (t >= T(22) - 0.01) {
    const parts = [['All', T(22)], ['in', T(22, 1)], ['one place.', T(22, 2)]];
    const o = { size: 200, weight: 500, ls: -8 };
    const sp = textW(c, ' ', o);
    const ws = parts.map(([s]) => textW(c, s, o));
    const tot = ws.reduce((a, b) => a + b, 0) + sp * 2;
    const z = purple ? 1 + 0.05 * E.outCubic(P(t, T(23), 2)) : 1 + 0.03 * E.outExpo(P(t, T(22, 2), 1));
    const yy = purple ? lerp(H / 2, H / 2 - 40, E.outExpo(P(t, T(23), 0.8))) : H / 2 + 10;
    c.save();
    c.translate(W / 2, H / 2); c.scale(z, z); c.translate(-W / 2, -H / 2);
    let x = W / 2 - tot / 2;
    parts.forEach(([s, ts], i) => {
      maskRise(c, s, x, yy, { ...o, align: 'left', color: i === 2 ? (purple ? '#d9b3c6' : C.mauve) : ink }, t, ts, { stagger: 0.05, dur: 0.4 });
      x += ws[i] + sp;
    });
    c.restore();
    if (purple) {
      const q = E.outExpo(P(t, T(23, 0.5), 0.8));
      c.save();
      c.globalAlpha = q;
      lockup(c, W / 2, H - 200 + (1 - q) * 30, 46, { mono: '#ffffff', textColor: '#ffffff' });
      c.restore();
    }
  }
  // progress ticks
  c.save();
  for (let i = 0; i < 5; i++) {
    const t0 = i < 4 ? PHRASES[i][0] : T(22);
    const on = t >= t0;
    c.fillStyle = on ? (purple ? '#fff' : C.mauve) : purple ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.12)';
    c.fillRect(W / 2 - 150 + i * 62, H - 120, 52, 3);
  }
  c.restore();
  deckFrame(c, 'Platform', 1, purple);
}

// =================================================================== 5. PRINCIPLES
const PRINC = [
  ['Clarity above all', ['Information is structured, readable,', 'and easy to navigate.']],
  ['System driven', ['Layouts, visuals, and interactions', 'feel engineered and consistent.']],
  ['Quiet confidence', ['Restrained and precise. No hype,', 'no excess. Just infrastructure.']],
];
const EXPLODE_LAB = ['ACCOUNTS', 'IDENTITY', 'PAYMENTS', 'COMPLIANCE', 'TREASURY'];
function principleVisual(c, i, t, t0) {
  const lt = t - t0;
  if (i === 0) {
    const cx = 1360, cy = 540, r = 240;
    const e = 0.42 * E.outExpo(P(lt, 0.15, 1.0));
    for (let k = 0; k < 5; k++) {
      const ox = FCENT[k][0] * e * r, oy = FCENT[k][1] * e * r;
      facetPath(c, k, cx + ox, cy + oy, r);
      c.fillStyle = FCOL[k];
      c.fill();
      // leader line + label
      const lp = E.outCubic(P(lt, 0.45 + k * 0.08, 0.5));
      if (lp <= 0) continue;
      const ax = cx + FCENT[k][0] * r * (1 + e), ay = cy + FCENT[k][1] * r * (1 + e);
      const side = FCENT[k][0] < 0 ? -1 : 1;
      const lx = cx + side * 330, ly = cy + FCENT[k][1] * r * 1.5;
      c.strokeStyle = C.g400; c.lineWidth = 1;
      c.beginPath(); c.moveTo(ax, ay);
      const mx = lerp(ax, lx, lp), my = lerp(ay, ly, lp);
      c.lineTo(mx, my); c.stroke();
      c.fillStyle = C.mauve; c.beginPath(); c.arc(ax, ay, 4, 0, TAU); c.fill();
      c.globalAlpha = lp;
      text(c, EXPLODE_LAB[k], lx + side * 14, ly, { fam: MONO, size: 15, weight: 500, color: C.g500, align: side < 0 ? 'right' : 'left', ls: 2 });
      c.globalAlpha = 1;
    }
  } else if (i === 1) {
    const cx = 1430, cy = 540, r = 290;
    c.strokeStyle = C.g200; c.lineWidth = 1;
    for (let k = -6; k <= 6; k++) {
      const p = E.outCubic(P(lt, 0.05 + Math.abs(k) * 0.03, 0.5));
      c.beginPath(); c.moveTo(cx + k * 60, cy - 420 * p); c.lineTo(cx + k * 60, cy + 420 * p); c.stroke();
      c.beginPath(); c.moveTo(cx - 420 * p, cy + k * 60); c.lineTo(cx + 420 * p, cy + k * 60); c.stroke();
    }
    // construction circles and extended edges
    c.strokeStyle = 'rgba(130,90,109,0.4)';
    const cp = E.outCubic(P(lt, 0.2, 0.9));
    c.beginPath(); c.arc(cx, cy, r * 1.0, -Math.PI / 2, -Math.PI / 2 + TAU * cp); c.stroke();
    c.beginPath(); c.arc(cx, cy, r * 0.62, Math.PI / 2, Math.PI / 2 + TAU * cp); c.stroke();
    for (let k = 0; k < 5; k++) {
      const f = FACETS[k];
      for (let j = 0; j < f.length; j++) {
        const a = f[j], b = f[(j + 1) % f.length];
        const dx = b[0] - a[0], dy = b[1] - a[1];
        const ep = E.outCubic(P(lt, 0.35 + (k * 4 + j) * 0.02, 0.5));
        c.beginPath();
        c.moveTo(cx + (a[0] - dx * 1.2 * ep) * r, cy + (a[1] - dy * 1.2 * ep) * r);
        c.lineTo(cx + (b[0] + dx * 1.2 * ep) * r, cy + (b[1] + dy * 1.2 * ep) * r);
        c.stroke();
      }
    }
    for (let k = 0; k < 5; k++) {
      const p = E.outCubic(P(lt, 0.8 + k * 0.1, 0.4));
      c.globalAlpha = p;
      facetPath(c, k, cx, cy, r);
      c.fillStyle = FCOL[k]; c.fill();
      c.globalAlpha = 1;
      c.fillStyle = C.mauve;
      if (P(lt, 0.4, 0.3) > 0) FACETS[k].forEach(([x, y]) => c.fillRect(cx + x * r - 3.5, cy + y * r - 3.5, 7, 7));
    }
  } else {
    const r = 700 * (1 + lt * 0.02);
    const cx = 1640 - lt * 12, cy = 600;
    c.save();
    c.translate(cx, cy); c.rotate(-0.05 + lt * 0.012);
    for (let k = 0; k < 5; k++) {
      const p = E.outExpo(P(lt, 0.05 + k * 0.06, 0.9));
      const ox = FCENT[k][0] * (1 - p) * r * 0.4, oy = FCENT[k][1] * (1 - p) * r * 0.4;
      c.globalAlpha = p;
      facetPath(c, k, ox, oy, r);
      c.fillStyle = ['#825A6D', '#8d6879', '#9a7786', '#b39aa6', '#a78a98'][k];
      c.fill();
    }
    c.restore();
    c.globalAlpha = 1;
  }
}
function sPrinciples(c, t) {
  c.fillStyle = '#ffffff';
  c.fillRect(0, 0, W, H);
  for (let i = 0; i < 3; i++) {
    const s0 = K.principles + i * BAR, s1 = s0 + BAR;
    if (t < s0 - 0.25 || t > s1 + 0.05) continue;
    const pin = E.outExpo(P(t, s0 - 0.12, 0.7));
    const pout = i < 2 ? E.inExpo(P(t, s1 - 0.22, 0.22)) : 0;
    const dx = (1 - pin) * W * 0.5 - pout * W * 0.6;
    c.save();
    c.translate(dx, 0);
    c.globalAlpha = clamp(pin * 1.5) * (1 - pout);
    principleVisual(c, i, t, s0);
    c.globalAlpha = clamp(pin * 1.5) * (1 - pout);
    text(c, `0${i + 1} — 03`, 140, 330, { fam: MONO, size: 18, weight: 500, color: C.mauve, align: 'left', ls: 2 });
    c.restore();
    c.save();
    c.translate(dx * 0.6, 0);
    maskRise(c, PRINC[i][0], 136, 440, { size: 112, weight: 500, color: C.black, align: 'left', ls: -4.5 }, t, s0, { stagger: 0.06, out: i < 2 ? s1 - 0.2 : null, outDur: 0.18 });
    PRINC[i][1].forEach((l, k) => maskRise(c, l, 140, 560 + k * 46, { size: 32, weight: 400, color: C.g500, align: 'left', ls: -0.3 }, t, s0 + 0.18 + k * 0.06, { stagger: 0.02, out: i < 2 ? s1 - 0.2 : null, outDur: 0.18 }));
    c.restore();
  }
  deckFrame(c, 'Design principles', 1);
}

// =================================================================== 6. POWERING + FINALE (aerial city, 3D monolith)
function aerialCam(t) {
  const u = (t - K.powering) / (K.endcard - K.powering); // 0..1 over 4 bars
  const ang = 0.5 + u * 1.6;
  const k = E.inOutSine(P(t, K.finale, K.endcard - K.finale - 0.6));
  const R = lerp(620, 440, k), Hc = lerp(470, 300, k), ty = lerp(0, 110, k);
  let cam = { px: Math.sin(ang) * R, py: Hc, pz: Math.cos(ang) * R, tx: 0, ty, tz: 0 };
  // final swing to face the monolith head-on
  const f = E.inOutExpo(P(t, T(30), K.endcard - T(30)));
  if (f > 0) {
    cam = { px: lerp(cam.px, 0, f), py: lerp(cam.py, 130, f), pz: lerp(cam.pz, 760, f), tx: 0, ty: lerp(ty, 110, f), tz: 0 };
  }
  return lookAt(cam.px, cam.py, cam.pz, cam.tx, cam.ty, cam.tz, 0, lerp(56, 40, f));
}
function letterbox(c, a) {
  const h = 92 * a;
  c.fillStyle = '#000';
  c.fillRect(0, 0, W, h);
  c.fillRect(0, H - h, W, h);
}
function sPowering(c, t) {
  const cam = aerialCam(t);
  viewAerial(c, t, cam, LOOK.night, { streetCol: 'rgba(255,160,110,0.32)' });
  grade(c, 0.9);
  c.fillStyle = 'rgba(10,4,8,0.35)'; c.fillRect(0, 0, W, H);
  const o = { fam: SERIF, weight: 400, size: 82, color: '#f6eef2', ls: -1 };
  maskRise(c, 'Powering leading', W / 2, 470, o, t, K.powering + 0.1, { stagger: 0.08, out: T(27, 3.55), outDur: 0.18 });
  maskRise(c, 'financial services companies.', W / 2, 568, o, t, K.powering + 0.35, { stagger: 0.08, out: T(27, 3.55), outDur: 0.18 });
  letterbox(c, E.outExpo(P(t, K.powering, 0.8)));
  hud(c, t, 0.8, { bl: 'SEISMIC NETWORK', br: 'LIVE' });
}
function sFinale(c, t) {
  const cam = aerialCam(t);
  const rise = E.outCubic(P(t, K.finale, 1.5));
  const S = 100;
  const gem = {
    x: 0, z: 0, S, th: 12,
    y: lerp(-2.3 * S, 0, rise) + (1 - rise) * Math.sin(t * 60) * 3,
    ang: (1 - E.inOutSine(P(t, K.finale, K.endcard - K.finale))) * 3.2,
    glow: 0.4 + 0.6 * Math.exp(-(t - K.finale) * 1.5),
    sheen: P(t, T(30, 2), 1.2),
  };
  const gd = Math.hypot(cam.px, cam.py - 100, cam.pz);
  viewAerial(c, t, cam, LOOK.night, {
    streetCol: 'rgba(255,160,110,0.32)',
    splitAt: gd * gd,
    mid: () => {
      // ground shockwave + beam behind the gem
      c.save();
      c.globalCompositeOperation = 'lighter';
      for (let k = 0; k < 2; k++) {
        const p = P(t, K.finale + k * 0.18, 2.2);
        if (p > 0 && p < 1) {
          const R = E.outCubic(p) * 900;
          c.beginPath();
          let st = false;
          for (let a = 0; a <= 72; a++) {
            const q = proj(cam, Math.cos(a / 72 * TAU) * R, 0.5, Math.sin(a / 72 * TAU) * R);
            if (!q) { st = false; continue; }
            if (!st) { c.moveTo(q[0], q[1]); st = true; } else c.lineTo(q[0], q[1]);
          }
          c.strokeStyle = `rgba(230,170,205,${(1 - p) * 0.8})`;
          c.lineWidth = 3 + (1 - p) * 10;
          c.stroke();
        }
      }
      const b0 = proj(cam, 0, gem.y + 2 * S, 0), b1 = proj(cam, 0, 3000, 0);
      if (b0 && b1 && rise > 0.6) {
        const ba = clamp((rise - 0.6) * 2.5) * (0.5 + 0.2 * Math.sin(t * 3));
        const g = c.createLinearGradient(b0[0], b0[1], b1[0], b1[1]);
        g.addColorStop(0, `rgba(240,200,225,${ba})`);
        g.addColorStop(1, 'rgba(240,200,225,0)');
        c.strokeStyle = g; c.lineWidth = 40; c.lineCap = 'round';
        c.beginPath(); c.moveTo(b0[0], b0[1]); c.lineTo(b1[0], b1[1]); c.stroke();
        c.lineWidth = 6; c.stroke();
      }
      const gp = proj(cam, 0, gem.y + S, 0);
      if (gp) sprite(c, SPR.mauve, gp[0], gp[1], 260000 / gp[2], 0.55 * gem.glow + 0.25);
      c.restore();
      c.globalAlpha = 1;
      drawGem(c, cam, gem);
      // debris
      c.save();
      c.globalCompositeOperation = 'lighter';
      for (let i = 0; i < 120; i++) {
        const a = rnd(i, 81) * TAU, rr0 = 40 + rnd(i, 82) * 260;
        const life = (t - K.finale) * (0.3 + rnd(i, 83) * 0.5);
        const yy = life * (40 + rnd(i, 84) * 140) - 20;
        const q = proj(cam, Math.cos(a + life * 0.2) * rr0, yy, Math.sin(a + life * 0.2) * rr0);
        if (!q) continue;
        sprite(c, SPR.mauve, q[0], q[1], Math.max(1.5, 2400 / q[2]), clamp(1 - life * 0.5) * 0.8);
      }
      c.restore();
      c.globalAlpha = 1;
    },
  });
  grade(c, 0.7);
  letterbox(c, 1 - E.inOutExpo(P(t, T(30, 2), 1.4)));
  // whiteout into the end card
  const wp = P(t, K.endcard - 0.35, 0.35);
  if (wp > 0) { c.fillStyle = `rgba(252,252,252,${E.inCubic(wp)})`; c.fillRect(0, 0, W, H); }
  hud(c, t, 0.8 * (1 - P(t, T(30), 0.5)), { bl: 'SEISMIC NETWORK', br: 'LIVE' });
}

// =================================================================== 7. END CARD
function cursorShape(c, x, y, s = 1) {
  c.save();
  c.translate(x, y); c.scale(s, s);
  c.beginPath();
  c.moveTo(0, 0); c.lineTo(0, 34); c.lineTo(8, 26); c.lineTo(14, 39); c.lineTo(20, 36); c.lineTo(14, 24); c.lineTo(25, 24); c.closePath();
  c.fillStyle = C.black; c.fill();
  c.strokeStyle = '#fff'; c.lineWidth = 2.2; c.stroke();
  c.restore();
}
function sEnd(c, t) {
  c.fillStyle = C.g50;
  c.fillRect(0, 0, W, H);
  c.strokeStyle = 'rgba(0,0,0,0.035)';
  c.lineWidth = 1;
  for (let x = 160; x < W; x += 160) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, H); c.stroke(); }
  const lt = t - K.endcard;
  const size = 132, cy = 420;
  const gm = lockupGeom(c, W / 2, size);
  // facets snap in
  for (let i = 0; i < 5; i++) {
    const p = E.outExpo(P(lt, i * 0.05, 0.55));
    const off = (1 - p) * 0.9;
    c.save();
    c.globalAlpha = clamp(p * 3);
    facetPath(c, i, gm.lx + FCENT[i][0] * off * gm.r, cy + FCENT[i][1] * off * gm.r, gm.r);
    c.fillStyle = FCOL[i];
    c.fill();
    c.restore();
  }
  const q = E.outExpo(P(lt, 0.15, 1.0));
  c.save();
  c.beginPath(); c.rect(gm.lx + gm.r * LOGO_HW + 6, 0, W, H); c.clip();
  wordmark(c, gm.wx - (1 - q) * (gm.ww + 60), cy + size * 0.06, size, C.black);
  c.restore();
  maskRise(c, 'A new standard for financial infrastructure.', W / 2, cy + 170, { size: 46, weight: 400, color: C.g500, ls: -0.6 }, t, T(31, 2), { stagger: 0.05 });
  // buttons
  const bw = 330, bh = 86, by = 700;
  const hover = P(t, T(33, 1.6), 0.15);
  const press = Math.exp(-Math.max(0, t - T(33, 2)) * 10) * (t >= T(33, 2) ? 1 : 0);
  [[W / 2 - bw - 14, 'Get Started', false, T(32)], [W / 2 + 14, 'Talk To Sales', true, T(32, 1)]].forEach(([x, lab, fill, ts], i) => {
    const p = E.outBack(P(t, ts, 0.5));
    if (p <= 0) return;
    c.save();
    c.translate(x + bw / 2, by + bh / 2);
    const sc = p * (i === 0 ? 1 - press * 0.05 : 1);
    c.scale(sc, sc);
    c.globalAlpha = clamp(p);
    const inv = i === 0 && hover > 0;
    c.fillStyle = fill ? C.black : inv ? rgba(mix3([255, 255, 255], hex(C.mauve), hover)) : '#fff';
    c.fillRect(-bw / 2, -bh / 2, bw, bh);
    if (!fill) { c.strokeStyle = inv ? C.mauve : C.black; c.lineWidth = 1.5; c.strokeRect(-bw / 2 + 0.75, -bh / 2 + 0.75, bw - 1.5, bh - 1.5); }
    text(c, lab, 0, 1, { size: 32, weight: 400, color: fill || hover > 0.5 && i === 0 ? '#fff' : C.black, ls: -0.3 });
    c.restore();
  });
  // url
  const uo = { fam: MONO, size: 22, weight: 400, color: C.g500, ls: 2 };
  const uw = textW(c, 'seismic.systems', uo);
  typeOn(c, 'seismic.systems', W / 2 - uw / 2, 880, uo, t, T(32, 3), 0.6);
  // cursor
  if (t >= T(32, 3)) {
    const m = E.inOut(P(t, T(32, 3), T(33, 1.8) - T(32, 3)));
    const cx = lerp(1460, W / 2 - bw / 2 - 14 + 40, m), cy2 = lerp(1010, by + bh / 2 + 6, m);
    cursorShape(c, cx, cy2, 1 - press * 0.1);
    if (press > 0.02) {
      c.strokeStyle = `rgba(130,90,109,${press})`; c.lineWidth = 2;
      c.beginPath(); c.arc(cx, cy2, 10 + (1 - press) * 40, 0, TAU); c.stroke();
    }
  }
  deckFrame(c, 'seismic.systems', P(lt, 0.6, 0.6));
}

// =================================================================== timeline of scenes
function compose(c, t) {
  if (t < K.quake) sSignal(c, t);
  else if (t < K.white) sAssemble(c, t);
  else if (t < T(7, 3) - 0.08) sWordmark(c, t);
  else if (t < K.ch1 + 0.12) sZoomIn(c, t);
  else if (t < K.ch2 - 0.3) sCh1(c, t);
  else if (t < K.ch2 + 0.3) sWipe(c, t, K.ch2, sCh1, sCh2);
  else if (t < K.ch3 - 0.3) sCh2(c, t);
  else if (t < K.ch3 + 0.3) sWipe(c, t, K.ch3, sCh2, sCh3);
  else if (t < K.drop) sCh3(c, t);
  else if (t < K.principles) sKinetic(c, t);
  else if (t < K.powering) sPrinciples(c, t);
  else if (t < K.finale) sPowering(c, t);
  else if (t < K.endcard) sFinale(c, t);
  else sEnd(c, t);
}
