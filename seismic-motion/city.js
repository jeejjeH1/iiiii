'use strict';
// Procedural 3D cities (avenue / worm's-eye / aerial grid) and the extruded 3D gem.
// Drawn with a painter's algorithm on canvas 2D, then graded like the brand photography:
// purple tint, haze and light noise.

// ---------------------------------------------------------------- generation
function genAvenue() {
  const B = [];
  let id = 0;
  for (const side of [-1, 1]) {
    for (const row of [0, 1, 2]) {
      let z = 60;
      while (z > -1700) {
        const s = id * 13 + 7;
        const w = 12 + rnd(s, 1) * (row ? 40 : 26);
        const d = 16 + rnd(s, 2) * 24;
        const gap = 1.5 + rnd(s, 3) * (row ? 14 : 5);
        let h = row === 0 ? 22 + rnd(s, 4) * 70 + (rnd(s, 5) < 0.18 ? 70 : 0)
          : row === 1 ? 60 + rnd(s, 4) * 150 : 120 + rnd(s, 4) * 260;
        const off = row === 0 ? 16 : row === 1 ? 50 + rnd(s, 6) * 20 : 110 + rnd(s, 6) * 60;
        const x0 = side < 0 ? -off - d : off;
        B.push({ x0, x1: x0 + d, z0: z - w, z1: z, h, s, lit: row === 2 ? 0.2 : 0.32 });
        z -= w + gap;
        id++;
      }
    }
  }
  return B;
}
function genWorm() {
  const B = [];
  let id = 0;
  for (let gx = -6; gx <= 6; gx++) {
    for (let gz = -6; gz <= 6; gz++) {
      const s = 900 + id++;
      const cx = gx * 34 + (rnd(s, 1) - 0.5) * 8, cz = gz * 34 + (rnd(s, 2) - 0.5) * 8;
      const dist = Math.hypot(cx, cz);
      if (dist < 22) continue;
      if (rnd(s, 9) < 0.12) continue;
      const w = 15 + rnd(s, 3) * 13, d = 15 + rnd(s, 4) * 13;
      const h = 60 + rnd(s, 5) * 170 + (rnd(s, 6) < 0.25 ? 160 : 0) + Math.max(0, 140 - dist);
      B.push({ x0: cx - w / 2, x1: cx + w / 2, z0: cz - d / 2, z1: cz + d / 2, h, s, lit: 0.3 });
    }
  }
  return B;
}
function genGrid() {
  const B = [], ST = [];
  const N = 9, PITCH = 58, BLK = 44;
  let id = 0;
  for (let gx = -N; gx < N; gx++) {
    for (let gz = -N; gz < N; gz++) {
      const bx0 = gx * PITCH + (PITCH - BLK) / 2, bz0 = gz * PITCH + (PITCH - BLK) / 2;
      const bcx = bx0 + BLK / 2, bcz = bz0 + BLK / 2;
      const dist = Math.hypot(bcx, bcz);
      if (dist < 120) continue; // plaza for the monolith
      if (Math.abs(bcx) < 60 && bcz > 0) continue; // clear sightline for the final shot
      for (let lx = 0; lx < 2; lx++) {
        for (let lz = 0; lz < 2; lz++) {
          const s = 5000 + id++;
          if (rnd(s, 1) < 0.1) continue;
          const lw = BLK / 2 - 2;
          const ix = 1 + rnd(s, 2) * 3, iz = 1 + rnd(s, 3) * 3;
          const x0 = bx0 + lx * BLK / 2 + ix, z0 = bz0 + lz * BLK / 2 + iz;
          const w = lw - ix - rnd(s, 4) * 3, d = lw - iz - rnd(s, 5) * 3;
          const core = Math.max(0, 1 - Math.abs(dist - 230) / 260);
          const h = 10 + Math.pow(rnd(s, 6), 2.2) * 90 + core * core * (60 + rnd(s, 7) * 170);
          B.push({ x0, x1: x0 + w, z0, z1: z0 + d, h, s, lit: 0.3 });
        }
      }
    }
  }
  for (let k = -N; k <= N; k++) ST.push(k * PITCH);
  return { B, ST, ext: N * PITCH };
}
const CITY_A = genAvenue();
const CITY_B = genWorm();
const CITY_C = genGrid();

// ---------------------------------------------------------------- look
const LOOK = {
  dusk: { skyTop: hex('#3b2b3b'), skyMid: hex('#6e5466'), skyHor: hex('#a7889a'), fog: hex('#8b6d7f'), fogD: 520, wall: hex('#3a2a33'), ground: hex('#2a1f26') },
  over: { skyTop: hex('#5e4a5d'), skyMid: hex('#77606f'), skyHor: hex('#9b8291'), fog: hex('#806777'), fogD: 380, wall: hex('#3d2c35'), ground: hex('#2a1f26') },
  night: { skyTop: hex('#140c12'), skyMid: hex('#2a1a24'), skyHor: hex('#4b3040'), fog: hex('#2c1c27'), fogD: 900, wall: hex('#1c131a'), ground: hex('#120c11') },
};

function drawSky(c, cam, L) {
  const hx = cam.f[0], hz = cam.f[2], hl = Math.hypot(hx, hz) || 1;
  const P0 = proj(cam, cam.px + hx / hl * 1e4, cam.py, cam.pz + hz / hl * 1e4);
  const PZ = proj(cam, cam.px + hx / hl * 1e3, cam.py + 1e4, cam.pz + hz / hl * 1e3);
  if (P0 && PZ) {
    const g = c.createLinearGradient(P0[0], P0[1], PZ[0], PZ[1]);
    g.addColorStop(0, rgba(L.skyHor));
    g.addColorStop(0.3, rgba(L.skyMid));
    g.addColorStop(1, rgba(L.skyTop));
    c.fillStyle = g;
  } else c.fillStyle = rgba(L.skyMid);
  c.fillRect(0, 0, W, H);
  return P0;
}

function drawGround(c, cam, L, ext, n = 14) {
  const st = (ext * 2) / n;
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      const x0 = -ext + i * st, z0 = -ext + j * st;
      const d = Math.hypot(x0 + st / 2 - cam.px, cam.py, z0 + st / 2 - cam.pz);
      const k = 1 - Math.exp(-d / L.fogD);
      if (!poly3(c, cam, [[x0, 0, z0], [x0 + st, 0, z0], [x0 + st, 0, z0 + st], [x0, 0, z0 + st]])) continue;
      c.fillStyle = rgba(mix3(L.ground, L.fog, k));
      c.fill();
    }
  }
}

// windows for one face: origin o, width axis (ux,uz) of length fw, height h
function drawWindows(c, cam, L, b, o, ux, uz, fw, fk, faceId, t) {
  if (fk > 0.86) return;
  const O = toCam(cam, o[0], 0, o[2]);
  const Uc = [ux * cam.r[0] + uz * cam.r[2], ux * cam.u[0] + uz * cam.u[2], ux * cam.f[0] + uz * cam.f[2]];
  const Vc = [cam.r[1], cam.u[1], cam.f[1]];
  const FH = 3.2, CW = 2.3;
  const cols = Math.max(1, Math.floor((fw - 1) / CW)), rows = Math.floor((b.h - 3) / FH);
  const x0 = (fw - cols * CW) / 2;
  const al = (1 - fk / 0.86) * 0.95;
  const warm = mix3([255, 196, 140], L.fog, fk * 0.7), cool = mix3([228, 226, 255], L.fog, fk * 0.7);
  const q = (u, v) => [O[0] + Uc[0] * u + Vc[0] * v, O[1] + Uc[1] * u + Vc[1] * v, O[2] + Uc[2] * u + Vc[2] * v];
  // floor bands
  c.strokeStyle = rgba([0, 0, 0], 0.18 * al);
  c.lineWidth = 1;
  c.beginPath();
  for (let r = 1; r < rows; r += 1) {
    const a = q(0, r * FH + 1.2), e = q(fw, r * FH + 1.2);
    if (a[2] < NEAR || e[2] < NEAR) continue;
    c.moveTo(projX(cam, a), projY(cam, a)); c.lineTo(projX(cam, e), projY(cam, e));
  }
  c.stroke();
  const flick = Math.floor(t * 0.7);
  const pane = fk < 0.55;
  const paneCol = rgba(mix3(L.skyHor, L.wall, 0.5), 0.13 * (1 - fk / 0.55));
  for (let r = 0; r < rows; r++) {
    for (let k = 0; k < cols; k++) {
      const h = rnd(b.s * 31 + faceId, r * 131 + k);
      const lit = h <= b.lit && rnd(b.s + faceId, r * 7 + k + flick * 977) >= 0.02;
      if (!lit && !pane) continue;
      const u0 = x0 + k * CW + 0.45, u1 = u0 + CW - 0.9, v0 = r * FH + 1.5, v1 = v0 + FH - 1.2;
      const a = q(u0, v0), bq = q(u1, v0), cq = q(u1, v1), dq = q(u0, v1);
      if (a[2] < NEAR || bq[2] < NEAR || cq[2] < NEAR || dq[2] < NEAR) continue;
      const ax = projX(cam, a), ay = projY(cam, a), cx = projX(cam, cq), cy = projY(cam, cq);
      if ((ax < -50 && cx < -50) || (ax > W + 50 && cx > W + 50) || (ay < -50 && cy < -50) || (ay > H + 50 && cy > H + 50)) continue;
      c.fillStyle = lit ? rgba(h < b.lit * 0.25 ? cool : warm, al * (0.45 + 0.55 * rnd(b.s, r * 53 + k))) : paneCol;
      c.beginPath();
      c.moveTo(ax, ay); c.lineTo(projX(cam, bq), projY(cam, bq)); c.lineTo(cx, cy); c.lineTo(projX(cam, dq), projY(cam, dq));
      c.fill();
    }
  }
}

function drawBuilding(c, cam, L, b, t, o) {
  const d = Math.hypot((b.x0 + b.x1) / 2 - cam.px, b.h / 2 - cam.py, (b.z0 + b.z1) / 2 - cam.pz);
  const fk = 1 - Math.exp(-d / L.fogD);
  const faces = [];
  if (cam.px > b.x1) faces.push([[b.x1, b.z1], [b.x1, b.z0], 0.62, 0]);
  if (cam.px < b.x0) faces.push([[b.x0, b.z0], [b.x0, b.z1], 0.48, 1]);
  if (cam.pz > b.z1) faces.push([[b.x0, b.z1], [b.x1, b.z1], 0.82, 2]);
  if (cam.pz < b.z0) faces.push([[b.x1, b.z0], [b.x0, b.z0], 0.7, 3]);
  for (const [p, q, sh, fid] of faces) {
    if (!poly3(c, cam, [[p[0], 0, p[1]], [q[0], 0, q[1]], [q[0], b.h, q[1]], [p[0], b.h, p[1]]])) continue;
    const base = mix3(L.wall.map((v) => v * (0.7 + sh * 0.6)), L.fog, fk);
    const pb = proj(cam, (p[0] + q[0]) / 2, 0, (p[1] + q[1]) / 2), pt = proj(cam, (p[0] + q[0]) / 2, b.h, (p[1] + q[1]) / 2);
    if (pb && pt) {
      const g = c.createLinearGradient(pb[0], pb[1], pt[0], pt[1]);
      g.addColorStop(0, rgba(mix3(base, [0, 0, 0], 0.35 * (1 - fk))));
      g.addColorStop(1, rgba(mix3(base, L.skyHor, 0.22 + 0.2 * (1 - fk))));
      c.fillStyle = g;
    } else c.fillStyle = rgba(base);
    c.fill();
    if (o.windows !== false && d < (o.winDist || 900)) {
      const fw = Math.hypot(q[0] - p[0], q[1] - p[1]);
      drawWindows(c, cam, L, b, [p[0], 0, p[1]], (q[0] - p[0]) / fw, (q[1] - p[1]) / fw, fw, fk, fid, t);
    }
  }
  if (cam.py > b.h && poly3(c, cam, [[b.x0, b.h, b.z0], [b.x1, b.h, b.z0], [b.x1, b.h, b.z1], [b.x0, b.h, b.z1]])) {
    c.fillStyle = rgba(mix3(L.wall.map((v) => v * 1.15), L.fog, fk));
    c.fill();
  }
  // aircraft warning light on tall towers
  if (b.h > 170) {
    const p = proj(cam, (b.x0 + b.x1) / 2, b.h + 1.5, (b.z0 + b.z1) / 2);
    if (p) {
      const blink = Math.pow(0.5 + 0.5 * Math.sin(t * 3.2 + b.s), 6);
      sprite(c, SPR.red, p[0], p[1], Math.max(3, 900 / p[2]) * (0.6 + blink), (0.25 + 0.75 * blink) * (1 - fk * 0.6));
      c.globalAlpha = 1;
    }
  }
}

function drawBuildings(c, cam, L, list, t, o = {}) {
  const arr = [];
  for (const b of list) {
    const cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
    const q = toCam(cam, cx, b.h / 2, cz);
    const rad = Math.max(b.h, b.x1 - b.x0) * 0.8;
    if (q[2] < -rad) continue;
    if (q[2] > NEAR && Math.abs(q[0]) - rad > q[2] * W / cam.F * 0.6) continue;
    const d = (cx - cam.px) ** 2 + (b.h * 0.3 - cam.py) ** 2 + (cz - cam.pz) ** 2;
    arr.push([d, b]);
  }
  arr.sort((a, b) => b[0] - a[0]);
  const split = o.splitAt ?? -1;
  for (const [d, b] of arr) {
    if (o.mid && d < split && !o._midDone) { o.mid(); o._midDone = true; }
    drawBuilding(c, cam, L, b, t, o);
  }
  if (o.mid && !o._midDone) o.mid();
}

// purple photo grade applied to a finished city layer
function grade(c, amt = 1, L) {
  c.save();
  c.globalCompositeOperation = 'color';
  c.globalAlpha = 0.32 * amt;
  c.fillStyle = C.mauve;
  c.fillRect(0, 0, W, H);
  c.globalCompositeOperation = 'soft-light';
  c.globalAlpha = 0.35 * amt;
  c.fillStyle = '#6a3f58';
  c.fillRect(0, 0, W, H);
  c.globalCompositeOperation = 'source-over';
  const g = c.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, 'rgba(30,14,24,0.25)');
  g.addColorStop(0.5, 'rgba(30,14,24,0)');
  g.addColorStop(1, 'rgba(20,8,16,0.45)');
  c.globalAlpha = amt;
  c.fillStyle = g;
  c.fillRect(0, 0, W, H);
  c.restore();
}

// ---------------------------------------------------------------- city views
// A: the avenue (camera at street level, dollying down the road)
function viewAvenue(c, t, u) {
  const L = LOOK.dusk;
  const z = 40 - u * 120;
  const cam = camera(Math.sin(u * 2.2) * 1.5, 6.5 + u * 2, z, 0.04 * Math.sin(u * 1.7) - 0.02, 0.17 + u * 0.03, 0.012 * Math.sin(u * 2.5), 64);
  const hor = drawSky(c, cam, L);
  // sun haze at the vanishing point
  if (hor) {
    const g = c.createRadialGradient(hor[0], hor[1], 0, hor[0], hor[1], 700);
    g.addColorStop(0, 'rgba(255,214,220,0.55)');
    g.addColorStop(1, 'rgba(255,214,220,0)');
    c.fillStyle = g; c.fillRect(0, 0, W, H);
  }
  // road + sidewalks
  const segs = 40;
  for (let i = 0; i < segs; i++) {
    const z1 = 80 - i * 45, z0 = z1 - 45;
    const d = Math.max(1, cam.pz - (z0 + z1) / 2);
    const k = 1 - Math.exp(-d / L.fogD);
    if (poly3(c, cam, [[-16, 0, z1], [16, 0, z1], [16, 0, z0], [-16, 0, z0]])) { c.fillStyle = rgba(mix3([34, 26, 31], L.fog, k)); c.fill(); }
    if (poly3(c, cam, [[-70, 0.01, z1], [-16, 0.01, z1], [-16, 0.01, z0], [-70, 0.01, z0]])) { c.fillStyle = rgba(mix3([52, 40, 47], L.fog, k)); c.fill(); }
    if (poly3(c, cam, [[16, 0.01, z1], [70, 0.01, z1], [70, 0.01, z0], [16, 0.01, z0]])) { c.fillStyle = rgba(mix3([52, 40, 47], L.fog, k)); c.fill(); }
  }
  // lane dashes + crosswalk
  for (let i = 0; i < 120; i++) {
    const zz = 60 - i * 12;
    const d = cam.pz - zz;
    if (d < 1) continue;
    const k = 1 - Math.exp(-d / L.fogD);
    for (const lx of [-5.3, 0, 5.3]) {
      if (!poly3(c, cam, [[lx - 0.12, 0.02, zz], [lx + 0.12, 0.02, zz], [lx + 0.12, 0.02, zz - 5], [lx - 0.12, 0.02, zz - 5]])) continue;
      c.fillStyle = rgba(mix3([200, 190, 196], L.fog, k), 0.55);
      c.fill();
    }
  }
  for (let k = 0; k < 14; k++) {
    const xx = -14 + k * 2.1;
    if (poly3(c, cam, [[xx, 0.03, -20], [xx + 1.1, 0.03, -20], [xx + 1.1, 0.03, -27], [xx, 0.03, -27]])) { c.fillStyle = 'rgba(210,200,206,0.5)'; c.fill(); }
  }
  // traffic
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 70; i++) {
    const lane = i % 4, dir = lane < 2 ? 1 : -1;
    const lx = [-10.5, -6, 6, 10.5][lane];
    const sp = 14 + rnd(i, 3) * 10;
    let zz = -1500 + ((rnd(i, 1) * 1600 + dir * t * sp * 3) % 1600 + 1600) % 1600;
    const d = cam.pz - zz;
    if (d < 2) continue;
    const k = 1 - Math.exp(-d / L.fogD);
    for (const side of [-0.8, 0.8]) {
      const p = proj(cam, lx + side, 0.8, zz);
      if (!p) continue;
      sprite(c, dir > 0 ? SPR.red : SPR.white, p[0], p[1], Math.max(1.6, 70 / p[2] * 4), (1 - k) * 0.9);
    }
  }
  c.restore();
  c.globalAlpha = 1;
  drawBuildings(c, cam, L, CITY_A, t, { winDist: 700 });
  // street lamps
  c.save();
  c.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 40; i++) {
    const zz = 40 - i * 36;
    for (const sx of [-17, 17]) {
      const p = proj(cam, sx, 9, zz);
      if (!p) continue;
      const k = 1 - Math.exp(-p[2] / L.fogD);
      sprite(c, SPR.warm, p[0], p[1], Math.max(2, 1400 / p[2]), 0.7 * (1 - k));
    }
  }
  c.restore();
  c.globalAlpha = 1;
  return cam;
}

// B: worm's-eye, looking up between towers, slowly rotating
function viewWorm(c, t, u) {
  const L = LOOK.over;
  const cam = camera(Math.sin(u * 1.3) * 3, 2 + u * 22, Math.cos(u * 1.1) * 3, 0.4 + u * 0.85, 1.05 + u * 0.12, 0, 82);
  drawSky(c, cam, L);
  const Z = proj(cam, cam.px, cam.py + 1e4, cam.pz);
  if (Z) {
    const g = c.createRadialGradient(Z[0], Z[1], 0, Z[0], Z[1], 900);
    g.addColorStop(0, 'rgba(235,215,228,0.5)');
    g.addColorStop(1, 'rgba(235,215,228,0)');
    c.fillStyle = g; c.fillRect(0, 0, W, H);
  }
  drawBuildings(c, cam, L, CITY_B, t, { winDist: 600 });
  return cam;
}

// C: aerial orbit over the grid (also hosts the monolith)
function viewAerial(c, t, cam, L, o = {}) {
  drawSky(c, cam, L);
  drawGround(c, cam, L, CITY_C.ext + 400, 12);
  // glowing street grid + traffic
  c.save();
  c.globalCompositeOperation = 'lighter';
  const ext = CITY_C.ext;
  for (const s of CITY_C.ST) {
    for (const axis of [0, 1]) {
      const pts = [];
      for (let k = -ext; k <= ext; k += ext / 6) pts.push(axis ? [s, 0.1, k] : [k, 0.1, s]);
      c.beginPath();
      let started = false;
      for (const p of pts) {
        const q = proj(cam, p[0], p[1], p[2]);
        if (!q) { started = false; continue; }
        if (!started) { c.moveTo(q[0], q[1]); started = true; } else c.lineTo(q[0], q[1]);
      }
      c.strokeStyle = o.streetCol || 'rgba(255,170,120,0.28)';
      c.lineWidth = 2;
      c.stroke();
    }
  }
  for (let i = 0; i < 360; i++) {
    const s = CITY_C.ST[Math.floor(rnd(i, 1) * CITY_C.ST.length)];
    const axis = i % 2, dir = rnd(i, 2) < 0.5 ? -1 : 1;
    const along = -ext + ((rnd(i, 3) * 2 * ext + dir * t * (25 + rnd(i, 4) * 30)) % (2 * ext) + 2 * ext) % (2 * ext);
    const off = dir * 1.6;
    const p = axis ? proj(cam, s + off, 0.6, along) : proj(cam, along, 0.6, s + off);
    if (!p) continue;
    const k = 1 - Math.exp(-p[2] / L.fogD);
    sprite(c, dir > 0 ? SPR.warm : SPR.white, p[0], p[1], Math.max(1.5, 1500 / p[2]), 0.85 * (1 - k));
  }
  c.restore();
  c.globalAlpha = 1;
  drawBuildings(c, cam, L, CITY_C.B, t, { winDist: 650, mid: o.mid, splitAt: o.splitAt });
}

// ---------------------------------------------------------------- 3D gem (extruded logo)
function gemFaces(g) {
  const ca = Math.cos(g.ang), sa = Math.sin(g.ang);
  const W3 = (x, y, z) => [g.x + x * ca + z * sa, g.y + g.S + y, g.z - x * sa + z * ca];
  const faces = [];
  FACETS.forEach((f, i) => {
    const ex = (g.explode || 0) * g.S;
    const ox = FCENT[i][0] * ex, oy = -FCENT[i][1] * ex;
    const fr = f.map(([x, y]) => W3(x * g.S + ox, -y * g.S + oy, g.th));
    const bk = f.map(([x, y]) => W3(x * g.S + ox, -y * g.S + oy, -g.th));
    faces.push({ pts: fr, n: [sa, 0, ca], i, kind: 0 });
    faces.push({ pts: bk.slice().reverse(), n: [-sa, 0, -ca], i, kind: 1 });
    // polygon winding: the traced facets are clockwise on screen (y down) -> outward normal
    for (let k = 0; k < f.length; k++) {
      const a = f[k], b = f[(k + 1) % f.length];
      // 2D outward normal (y down), then flip y for the y-up local frame
      let nx = b[1] - a[1], ny = -(b[0] - a[0]);
      if (nx * (FCENT[i][0] - a[0]) + ny * (FCENT[i][1] - a[1]) > 0) { nx = -nx; ny = -ny; }
      const nl = Math.hypot(nx, ny);
      nx /= nl; ny = -ny / nl;
      faces.push({ pts: [fr[k], fr[(k + 1) % f.length], bk[(k + 1) % f.length], bk[k]], n: [nx * ca, ny, -nx * sa], i, kind: 2 });
    }
  });
  return faces;
}
const LIGHT = (() => { const l = [-0.45, 0.55, 0.7], n = Math.hypot(...l); return l.map((v) => v / n); })();
function drawGem(c, cam, g) {
  const faces = gemFaces(g);
  const vis = [];
  for (const f of faces) {
    let cx = 0, cy = 0, cz = 0;
    f.pts.forEach((p) => { cx += p[0]; cy += p[1]; cz += p[2]; });
    cx /= f.pts.length; cy /= f.pts.length; cz /= f.pts.length;
    const vx = cam.px - cx, vy = cam.py - cy, vz = cam.pz - cz;
    if (vx * f.n[0] + vy * f.n[1] + vz * f.n[2] <= 0) continue;
    const q = toCam(cam, cx, cy, cz);
    vis.push([q[2], f]);
  }
  vis.sort((a, b) => b[0] - a[0]);
  for (const [, f] of vis) {
    if (!poly3(c, cam, f.pts)) continue;
    const dif = Math.max(0, f.n[0] * LIGHT[0] + f.n[1] * LIGHT[1] + f.n[2] * LIGHT[2]);
    const base = FCOL_RGB[f.i];
    let col = base.map((v) => v * (0.62 + 0.5 * dif));
    if (f.kind === 2) col = mix3(col, [236, 200, 222], 0.35);
    col = mix3(col, [255, 235, 245], (g.glow || 0) * 0.1);
    c.fillStyle = rgba(col);
    c.fill();
    if (f.kind === 0) {
      c.save();
      c.clip();
      const p0 = proj(cam, f.pts[0][0], f.pts[0][1], f.pts[0][2]);
      if (p0 && g.sheen != null) {
        // moving specular band
        const sx0 = p0[0] + (g.sheen - 0.5) * 1600;
        const gr = c.createLinearGradient(sx0 - 260, 0, sx0 + 260, 300);
        gr.addColorStop(0, 'rgba(255,255,255,0)');
        gr.addColorStop(0.5, 'rgba(255,236,246,0.32)');
        gr.addColorStop(1, 'rgba(255,255,255,0)');
        c.fillStyle = gr;
        c.fillRect(0, 0, W, H);
      }
      c.restore();
      c.strokeStyle = `rgba(255,225,240,${0.18 + (g.glow || 0) * 0.5})`;
      c.lineWidth = 1.5;
      c.stroke();
    }
  }
}
