// GenLayer cat mascot, modeled from the 3-view reference sheet (theme-reference.png).
// Units: 1 unit = 200 px of the original 4000px sheet. y up, feet on y=0, character faces +z,
// its right side is -x (viewer's left in the front view). All parts are authored in rest-pose
// world coordinates and parented through joints, so poses are just joint rotations.
import * as THREE from 'three';

export const COLORS = {
  white: '#f4f2fd', gasket: '#e6e7ec',
  guardHi: '#a7aabd', guardLo: '#8f8e98',
  suitHi: '#a49cb3', suitLo: '#8e86a0',
  dark: '#41415e', palm: '#4d5e8d', ring: '#272f42', cyan: '#35e1f4',
  magHi: '#e02af4', magLo: '#9a30c4', discHi: '#c52fe2', discLo: '#9433c0',
  earInnerHi: '#565e76', earInnerLo: '#6c7390', tail: '#1f1a16',
  led: '#02f7f8', ledGap: '#1e8b97',
};

// ------------------------------------------------------------------ geometry helpers
const v3 = (x, y, z) => new THREE.Vector3(x, y, z);
const sgnpow = (x, e) => Math.sign(x) * Math.pow(Math.abs(x), e);
const smax = (a, b, k) => (a + b + Math.sqrt((a - b) * (a - b) + k * k)) / 2;

// Grid surface from fn(u, v) -> Vector3, u in [0,1] around (wrapped), v in [0,1] bottom->top.
// Normals come from finite differences of fn, so any analytic shape gets smooth shading.
function paramSurface(fn, nu, nv, { wrapU = true, colorFn = null, uvFn = null, flip = false } = {}) {
  const cols = wrapU ? nu : nu + 1;
  const pos = [], nor = [], col = [], uvs = [], idx = [];
  const e = 1e-3;
  for (let j = 0; j <= nv; j++) {
    const v = j / nv;
    for (let i = 0; i < cols; i++) {
      const u = i / nu;
      const p = fn(u, v);
      const vv = Math.min(Math.max(v, 2e-3), 1 - 2e-3);
      const du = fn(u + e, vv).sub(fn(u - e, vv));
      const dv = fn(u, Math.min(vv + e, 1)).sub(fn(u, Math.max(vv - e, 0)));
      const n = new THREE.Vector3().crossVectors(du, dv).normalize();
      if (flip) n.negate();
      pos.push(p.x, p.y, p.z); nor.push(n.x, n.y, n.z);
      if (colorFn) { const c = colorFn(p, n, u, v); col.push(c.r, c.g, c.b); }
      if (uvFn) { const t = uvFn(p, n, u, v); uvs.push(t[0], t[1]); }
    }
  }
  for (let j = 0; j < nv; j++) {
    for (let i = 0; i < nu; i++) {
      const i1 = wrapU ? (i + 1) % cols : i + 1;
      const a = j * cols + i, b = j * cols + i1, c = (j + 1) * cols + i1, d = (j + 1) * cols + i;
      if (flip) idx.push(a, c, b, a, d, c); else idx.push(a, b, c, a, c, d);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
  if (colorFn) g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  if (uvFn) g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  g.setIndex(idx);
  return g;
}

// ellipsoid / superellipsoid point. lat in [-pi/2, pi/2], lon around y (0 = +z front)
function ellipsoidFn(c, r, { ryDown = r.y, eDown = 1, eUp = 1 } = {}) {
  return (u, v) => {
    const lon = u * Math.PI * 2, lat = (v - 0.5) * Math.PI;
    const up = lat >= 0;
    const e = up ? eUp : eDown;
    const ch = sgnpow(Math.cos(lat), e), sh = sgnpow(Math.sin(lat), e);
    return v3(c.x + r.x * ch * Math.sin(lon), c.y + (up ? r.y : ryDown) * sh, c.z + r.z * ch * Math.cos(lon));
  };
}

// Clip a geometry against a plane, keeping the side where n.p >= d. Splits triangles cleanly.
function clipGeometry(geo, n, d) {
  const g = geo.index ? geo.toNonIndexed() : geo;
  const names = Object.keys(g.attributes);
  const A = names.map((k) => g.attributes[k]);
  const out = names.map(() => []);
  const P = g.attributes.position;
  const dist = (i) => n.x * P.getX(i) + n.y * P.getY(i) + n.z * P.getZ(i) - d;
  const vert = (i) => A.map((a) => { const r = []; for (let k = 0; k < a.itemSize; k++) r.push(a.array[i * a.itemSize + k]); return r; });
  const lerp = (a, b, t) => a.map((arr, m) => arr.map((x, k) => x + (b[m][k] - x) * t));
  for (let t = 0; t < P.count; t += 3) {
    const ds = [dist(t), dist(t + 1), dist(t + 2)];
    const vs = [vert(t), vert(t + 1), vert(t + 2)];
    const poly = [];
    for (let k = 0; k < 3; k++) {
      const k2 = (k + 1) % 3;
      if (ds[k] >= 0) poly.push(vs[k]);
      if ((ds[k] >= 0) !== (ds[k2] >= 0)) poly.push(lerp(vs[k], vs[k2], ds[k] / (ds[k] - ds[k2])));
    }
    for (let k = 1; k + 1 < poly.length; k++) {
      for (const q of [poly[0], poly[k], poly[k + 1]]) q.forEach((arr, m) => out[m].push(...arr));
    }
  }
  const r = new THREE.BufferGeometry();
  names.forEach((k, m) => r.setAttribute(k, new THREE.Float32BufferAttribute(out[m], A[m].itemSize)));
  return r;
}

// Loft a strip between two 3D curves a(t), b(t): s in [0,1] across, t in [0,1] along.
function loft(a, b, nt, ns, { bulge = null, colorFn = null } = {}) {
  return paramSurface((s, t) => {
    const p = a(t).lerp(b(t), s);
    if (bulge) p.add(bulge(s, t));
    return p;
  }, ns, nt, { wrapU: false, colorFn });
}

const gradY = (hi, lo, y0, y1) => {
  const H = new THREE.Color(hi), L = new THREE.Color(lo);
  return (p) => L.clone().lerp(H, THREE.MathUtils.clamp((p.y - y0) / (y1 - y0), 0, 1));
};

// ------------------------------------------------------------------ materials
function mats(env) {
  const std = (o) => new THREE.MeshPhysicalMaterial(Object.assign({ roughness: 0.42, metalness: 0, envMapIntensity: 0.3 }, o));
  return {
    white: std({ color: COLORS.white, roughness: 0.32, clearcoat: 0.5, clearcoatRoughness: 0.25 }),
    gasket: std({ color: COLORS.gasket, roughness: 0.3, side: THREE.DoubleSide }),
    guard: std({ vertexColors: true, roughness: 0.38, metalness: 0.15, clearcoat: 0.3, side: THREE.DoubleSide }),
    suit: std({ vertexColors: true, roughness: 0.5 }),
    dark: std({ color: COLORS.dark, roughness: 0.5, specularIntensity: 0.6 }),
    palm: std({ color: COLORS.palm, roughness: 0.5 }),
    ring: std({ color: COLORS.ring, roughness: 0.35, metalness: 0.2 }),
    cyan: new THREE.MeshStandardMaterial({ color: COLORS.cyan, emissive: COLORS.cyan, emissiveIntensity: 1.7, roughness: 0.2 }),
    mag: std({ vertexColors: true, roughness: 0.3, clearcoat: 0.6, clearcoatRoughness: 0.2, side: THREE.DoubleSide }),
    earInner: std({ vertexColors: true, roughness: 0.55, side: THREE.DoubleSide }),
    disc: std({ vertexColors: true, roughness: 0.28, clearcoat: 0.8, clearcoatRoughness: 0.15 }),
    tail: std({ color: COLORS.tail, roughness: 0.6, specularIntensity: 0.4 }),
    seam: new THREE.MeshBasicMaterial({ color: '#16161f' }),
  };
}

// ------------------------------------------------------------------ face display
// Canvas covers the visor's front projection: x in [-1,1], y in [1.65, 3.275] (units).
// Shapes are authored in reference-crop pixels (crop px = 400/unit) and mapped by FACE_T.
export const FACE_W = 1024, FACE_H = 832;
const FACE_T = [1.28, 0, 0, 1.28, -248 * 1.28, -240 * 1.28];

const LOGO_L = [[-0.049, 0], [-0.049, 0.343], [-0.219, 0.733], [-0.066, 0.818], [-0.506, 1]];
const LOGO_DIAMOND = [[0, 0.483], [0.094, 0.689], [0, 0.739], [-0.094, 0.689]];
export function logoPath(ctx, cx, top, w, h) {
  const P = ([u, v], s = 1) => [cx + u * w * s, top + v * h];
  ctx.beginPath();
  for (const s of [1, -1]) {
    LOGO_L.forEach((q, i) => { const [x, y] = P(q, s); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
    ctx.closePath();
  }
  LOGO_DIAMOND.forEach((q, i) => { const [x, y] = P(q); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
  ctx.closePath();
}

function eyeAngry(ctx, m) { // m = +1 left eye, -1 mirrored right eye (about x=648)
  const X = (x) => (m > 0 ? x : 1296 - x);
  ctx.moveTo(X(365), 497);
  ctx.lineTo(X(557), 617);
  ctx.bezierCurveTo(X(525), 665, X(495), 688, X(452), 688);
  ctx.bezierCurveTo(X(392), 688, X(362), 640, X(365), 497);
  ctx.closePath();
}
function mouthCat(ctx, open = 0) {
  ctx.moveTo(512, 731);
  ctx.bezierCurveTo(560, 752, 622, 748, 650, 692);
  ctx.bezierCurveTo(678, 748, 740, 752, 788, 731);
  ctx.bezierCurveTo(770, 778 + open * 40, 690, 790 + open * 40, 650, 753 + open * 30);
  ctx.bezierCurveTo(610, 790 + open * 40, 530, 778 + open * 40, 512, 731);
  ctx.closePath();
}

// state: { expr, blink, lookX, lookY, mouthOpen, icon, iconT, scan, logo }
function faceShapes(ctx, st) {
  const blink = st.blink || 0, lx = st.lookX || 0, ly = st.lookY || 0;
  const expr = st.expr || 'angry';
  const eye = (m) => {
    const cx = m > 0 ? 461 : 835, cy = 592;
    ctx.save();
    ctx.translate(cx + lx, cy + ly);
    ctx.scale(1, Math.max(0.06, 1 - blink));
    ctx.translate(-cx, -cy);
    ctx.beginPath();
    if (expr === 'angry') eyeAngry(ctx, m);
    else if (expr === 'happy') { // ^ ^ arcs
      ctx.lineWidth = 34; ctx.lineCap = 'round';
      ctx.moveTo(cx - 85, cy + 40); ctx.quadraticCurveTo(cx, cy - 95, cx + 85, cy + 40);
      ctx.stroke(); ctx.restore(); return;
    } else if (expr === 'wide' || expr === 'neutral') {
      const r = expr === 'wide' ? 88 : 72;
      ctx.ellipse(cx, cy, r, r * (expr === 'wide' ? 1.05 : 0.95), 0, 0, Math.PI * 2);
    } else if (expr === 'focus') { // half-lidded scanning eyes
      ctx.moveTo(cx - 95, cy - 20); ctx.lineTo(cx + 95, cy - 20);
      ctx.bezierCurveTo(cx + 90, cy + 70, cx - 90, cy + 70, cx - 95, cy - 20);
    } else if (expr === 'sad') {
      const X = (x) => (m > 0 ? x : 1296 - x);
      ctx.moveTo(X(365), 560); ctx.lineTo(X(557), 520);
      ctx.bezierCurveTo(X(560), 640, X(500), 690, X(452), 688);
      ctx.bezierCurveTo(X(392), 688, X(362), 640, X(365), 560);
    }
    ctx.fill();
    ctx.restore();
  };
  ctx.lineJoin = 'round';
  eye(1); eye(-1);
  if (st.mouth !== 'none') {
    ctx.beginPath();
    if (st.mouth === 'flat') { ctx.rect(560, 735, 180, 26); }
    else mouthCat(ctx, st.mouthOpen || 0);
    ctx.fill();
  }
  if (st.logo !== false) { logoPath(ctx, 642.5, 300, 195, 180); ctx.fill('nonzero'); }
}

export function createFace() {
  const mk = () => { const c = document.createElement('canvas'); c.width = FACE_W; c.height = FACE_H; return c; };
  const canvas = mk(), glow = mk(), mask = mk(), grid = mk(), tmp = mk();
  { // LED matrix pattern (cell pitch ~14 crop px, like the sheet)
    const g = grid.getContext('2d');
    const pitch = 17.6, cell = 12.2;
    g.fillStyle = '#fff';
    for (let y = -pitch; y < FACE_H + pitch; y += pitch) for (let x = -pitch; x < FACE_W + pitch; x += pitch) g.fillRect(x, y, cell, cell);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  const glowTex = new THREE.CanvasTexture(glow);
  glowTex.colorSpace = THREE.SRGBColorSpace;
  const ctx = canvas.getContext('2d'), gctx = glow.getContext('2d'), mctx = mask.getContext('2d'), tctx = tmp.getContext('2d');
  const navy = new THREE.Color('#1b2038');

  // (grid or solid) ∩ mask, coloured. Reuses one scratch canvas: draw it before building the next.
  function layer(color, cells) {
    tctx.globalCompositeOperation = 'source-over';
    tctx.clearRect(0, 0, FACE_W, FACE_H);
    if (cells) tctx.drawImage(grid, 0, 0); else { tctx.fillStyle = '#fff'; tctx.fillRect(0, 0, FACE_W, FACE_H); }
    tctx.globalCompositeOperation = 'source-in';
    tctx.fillStyle = color; tctx.fillRect(0, 0, FACE_W, FACE_H);
    tctx.globalCompositeOperation = 'destination-in';
    tctx.drawImage(mask, 0, 0);
    tctx.globalCompositeOperation = 'source-over';
    return tmp;
  }

  // st: { expr, blink, lookX, lookY, mouth, mouthOpen, logo, led, bright, off, scan, draw(ctx, st) }
  function draw(st = {}) {
    const led = st.led || COLORS.led, b = st.bright ?? 1;
    const bg = ctx.createLinearGradient(0, 0, 0, FACE_H);
    bg.addColorStop(0, '#1f5e74'); bg.addColorStop(0.17, '#23697f'); bg.addColorStop(0.42, '#1c2d45');
    bg.addColorStop(0.56, '#1b233a'); bg.addColorStop(1, '#1b2038');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = 1;
    ctx.fillStyle = bg; ctx.fillRect(0, 0, FACE_W, FACE_H);
    if (st.off) { ctx.fillStyle = `rgba(8,10,22,${0.75 * st.off})`; ctx.fillRect(0, 0, FACE_W, FACE_H); }
    gctx.setTransform(1, 0, 0, 1, 0, 0);
    gctx.globalAlpha = 1;
    gctx.fillStyle = '#000'; gctx.fillRect(0, 0, FACE_W, FACE_H);
    if (b > 0.001) {
      mctx.setTransform(1, 0, 0, 1, 0, 0);
      mctx.clearRect(0, 0, FACE_W, FACE_H);
      mctx.setTransform(...FACE_T);
      mctx.fillStyle = '#fff'; mctx.strokeStyle = '#fff';
      if (st.expr !== 'none') faceShapes(mctx, st);
      if (st.draw) { mctx.save(); st.draw(mctx, st); mctx.restore(); } // extra glyphs/text in crop coords
      mctx.setTransform(1, 0, 0, 1, 0, 0);
      const gap = '#' + navy.clone().lerp(new THREE.Color(led), 0.55).getHexString();
      ctx.globalAlpha = 0.9 * b;
      ctx.drawImage(layer(gap, false), 0, 0);
      const cells = layer(led, true);
      ctx.globalAlpha = b;
      ctx.drawImage(cells, 0, 0);
      gctx.globalAlpha = b;
      gctx.drawImage(cells, 0, 0);
      ctx.globalAlpha = 1; gctx.globalAlpha = 1;
    }
    if (st.scan !== undefined && st.scan !== null) { // scanline sweep
      const y = st.scan * FACE_H;
      const c = new THREE.Color(led), rgb = `${Math.round(c.r * 255)},${Math.round(c.g * 255)},${Math.round(c.b * 255)}`;
      for (const [g2, a] of [[ctx, 0.35], [gctx, 0.5]]) {
        const sg = g2.createLinearGradient(0, y - 50, 0, y + 50);
        sg.addColorStop(0, `rgba(${rgb},0)`); sg.addColorStop(0.5, `rgba(${rgb},${a})`); sg.addColorStop(1, `rgba(${rgb},0)`);
        g2.fillStyle = sg; g2.fillRect(0, y - 50, FACE_W, 100);
      }
    }
    tex.needsUpdate = true; glowTex.needsUpdate = true;
  }
  return { canvas, tex, glowTex, draw };
}

// ------------------------------------------------------------------ rig helpers
function joint(parentRest, at, name) {
  const j = new THREE.Group(); j.name = name;
  j.position.copy(at);
  const rest = new THREE.Group();
  rest.position.copy(at).negate();
  j.add(rest);
  parentRest.add(j);
  return { j, rest, at: at.clone() };
}

// ------------------------------------------------------------------ build
// Geometry is cached per detail level, so extra cats (validators) only cost materials + a face.
const GEO = new Map();

export function createCharacter({ led = COLORS.led, detail = 1 } = {}) {
  const M = mats();
  M.cyan = M.cyan.clone(); M.cyan.color.set(led); M.cyan.emissive.set(led);
  const seg = (n) => Math.max(8, Math.round(n * detail));
  const geo = (key, fn) => { const k = key + '@' + detail; if (!GEO.has(k)) GEO.set(k, fn()); return GEO.get(k); };
  let nextId = 1;
  const ids = {};
  const mesh = (g, mat, parent, idName, opts = {}) => {
    const m = new THREE.Mesh(g, mat);
    if (idName === null) m.userData.lineId = 0;
    else { if (!(idName in ids)) ids[idName] = nextId++; m.userData.lineId = ids[idName]; }
    if (opts.noCrease) m.userData.noCrease = true;
    parent.add(m);
    return m;
  };

  const root = new THREE.Group(); root.name = 'cat';
  const rootRest = new THREE.Group(); root.add(rootRest);
  const hips = joint(rootRest, v3(0, 0.62, 0), 'hips');
  const torsoJ = joint(hips.rest, v3(0, 0.62, 0.02), 'torso');
  const neck = joint(torsoJ.rest, v3(0, 1.5, 0), 'neck');
  const H = neck.rest;

  // ---------------- head
  const crC = v3(0, 2.49, -0.04), crR = v3(0.98, 0.9, 0.87);
  mesh(geo('cranium', () => paramSurface(ellipsoidFn(crC, crR), seg(96), seg(64))), M.white, H, 'cranium');

  const gC = v3(0, 2.33, 0.08), gR = v3(1.045, 0.95, 0.8);
  const guardFn = ellipsoidFn(gC, gR, { ryDown: 0.74, eDown: 0.62 });
  // guard is cut by a plane tilted back so the cranium shows behind the earpieces (side view)
  mesh(geo('guard', () => clipGeometry(paramSurface(guardFn, seg(96), seg(64), { colorFn: gradY(COLORS.guardHi, COLORS.guardLo, 1.7, 2.9) }), v3(0, 0.653, 0.757), 1.152)), M.guard, H, 'guard');
  // z of the guard / cranium surface at (x, y), front side
  const guardZ = (x, y) => {
    const dy = y - gC.y;
    const ry = dy >= 0 ? gR.y : 0.74, e = dy >= 0 ? 1 : 0.62;
    const s = Math.min(Math.abs(dy) / ry, 1);
    const sinLat = Math.pow(s, 1 / e);
    const cosLat = Math.sqrt(Math.max(0, 1 - sinLat * sinLat));
    const h = Math.pow(cosLat, e); // horizontal radius factor
    const k = 1 - Math.pow(x / (gR.x * Math.max(h, 1e-4)), 2);
    return k <= 0 ? -1 : gC.z + gR.z * h * Math.sqrt(k);
  };
  const craniumZ = (x, y) => {
    const k = 1 - (x / crR.x) ** 2 - ((y - crC.y) / crR.y) ** 2;
    return k <= 0 ? -1 : crC.z + crR.z * Math.sqrt(k);
  };

  // visor: dome over the front-projection ellipse (cx 0, cy 2.4625, ax 1.0, ay 0.8125)
  const VC = 2.4625, VAX = 1.0, VAY = 0.8125;
  const zRim0 = (th) => { const s = Math.sin(th); return 0.08 + (s > 0 ? 0.37 : 0.64) * Math.pow(Math.abs(s), 1.5); };
  const visorZ = (r, th) => {
    const x = r * Math.cos(th) * VAX, y = VC + r * Math.sin(th) * VAY;
    let z = zRim0(th) * r * r + 0.95 * (1 - r * r);
    z += 0.05 * Math.exp(-(((y - 2.05) / 0.28) ** 2) - (x / 0.4) ** 2);
    z = smax(z, guardZ(x, y) + 0.02, 0.05);
    z = smax(z, craniumZ(x, y) + 0.02, 0.05);
    return z;
  };
  const visorPt = (r, th) => v3(r * Math.cos(th) * VAX, VC + r * Math.sin(th) * VAY, visorZ(r, th));
  const face = createFace();
  face.draw({ expr: 'angry', led });
  const visorMat = new THREE.MeshPhysicalMaterial({
    map: face.tex, emissive: '#ffffff', emissiveMap: face.glowTex, emissiveIntensity: 1.9,
    roughness: 0.12, metalness: 0.0, clearcoat: 1, clearcoatRoughness: 0.05, envMapIntensity: 0.35,
  });
  const visorGeo = geo('visor', () => paramSurface((u, v) => visorPt(v, u * Math.PI * 2), seg(128), seg(48), {
    flip: true,
    uvFn: (p) => [(p.x + 1) / 2, (p.y - (VC - VAY)) / (2 * VAY)],
  }));
  const visor = mesh(visorGeo, visorMat, H, 'visor');

  // gasket skirt: from the visor rim back/outward into the shell
  const rimOut = (th) => v3(Math.cos(th), Math.sin(th) * 0.9, 0).normalize();
  mesh(geo('gasket', () => paramSurface((u, v) => {
    const th = u * Math.PI * 2;
    const p = visorPt(1, th);
    return p.add(rimOut(th).multiplyScalar(0.05 * v)).add(v3(0, 0, -0.3 * v));
  }, seg(128), 4, { flip: true })), M.gasket, H, 'gasket', { noCrease: true });

  // panel seams on the guard (projected radially onto the surface)
  const seam = (key, a, b) => {
    const g = geo('seam' + key, () => {
      const pts = [];
      for (let i = 0; i <= 16; i++) {
        const p = a.clone().lerp(b, i / 16);
        const dir = p.clone().sub(gC);
        let lo = 0.3, hi = 2;
        for (let k = 0; k < 30; k++) {
          const m = (lo + hi) / 2;
          const q = gC.clone().addScaledVector(dir, m);
          const dy = q.y - gC.y, ry = dy >= 0 ? gR.y : 0.74, e = dy >= 0 ? 1 : 0.62;
          const f = (Math.hypot((q.x - gC.x) / gR.x, (q.z - gC.z) / gR.z) ** (2 / e)) + (Math.abs(dy) / ry) ** (2 / e);
          if (f > 1) hi = m; else lo = m;
        }
        pts.push(gC.clone().addScaledVector(dir, lo * 1.004));
      }
      return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 24, 0.008, 6);
    });
    mesh(g, M.seam, H, null);
  };
  for (const s of [1, -1]) {
    seam('a' + s, v3(0.62 * s, 1.84, 0.62), v3(0.55 * s, 1.56, 0.42));
    seam('b' + s, v3(0.93 * s, 2.06, 0.25), v3(0.85 * s, 2.0, -0.3));
    seam('c' + s, v3(0.95 * s, 2.25, 0.2), v3(0.98 * s, 2.24, 0.0));
  }

  // earpieces (lathe around x)
  const lathe = (prof, segs) => {
    const g = new THREE.LatheGeometry(prof.map(([r, h]) => new THREE.Vector2(r, h)), segs);
    g.rotateZ(-Math.PI / 2); // lathe axis y -> +x
    return g;
  };
  const ringProf = [[0.001, -0.2], [0.3, -0.2], [0.365, -0.15], [0.392, -0.075], [0.39, -0.0], [0.365, 0.05], [0.3, 0.075], [0.001, 0.08]];
  const discProf = [[0.001, 0.0], [0.265, 0.0], [0.265, 0.07], [0.245, 0.14], [0.19, 0.19], [0.1, 0.215], [0.001, 0.22]];
  const earpieces = [];
  for (const s of [1, -1]) {
    const g = new THREE.Group();
    g.position.set(1.02 * s, 2.58, -0.24);
    if (s < 0) g.rotation.y = Math.PI;
    H.add(g);
    mesh(geo('earRing', () => { const r = lathe(ringProf, seg(64)); paintGrad(r, COLORS.guardHi, COLORS.guardHi, -0.4, 0.4); return r; }), M.guard, g, 'earRing');
    mesh(geo('earDisc', () => { const d = lathe(discProf, seg(64)); paintGrad(d, COLORS.discHi, COLORS.discLo, 0.26, -0.26); return d; }), M.disc, g, 'earDisc');
    earpieces.push(g);
  }

  // ears: 4-sided pyramid lofted to the tip, faces = opening / outer-back / back-inner / front-inner
  const ears = {};
  for (const s of [1, -1]) {
    const X = (p) => v3(p.x * (s < 0 ? 1 : -1), p.y, p.z);
    const D = X(v3(-1.065, 3.76, 0.45));
    const P = X(v3(-0.8, 3.05, 0.42)), Q = X(v3(-1.12, 2.92, 0.12)), R = X(v3(-1.0, 2.92, -0.4)), I = X(v3(-0.42, 3.28, -0.08));
    const RC = X(v3(-1.1, 3.62, -0.44));
    const ctr = v3(0, 2.5, 0);
    const sink = (b) => b.clone().lerp(ctr, 0.28);
    // edge curve from base (t=0, sunk into head) to tip (t=1)
    const edge = (b, ctrl) => (t) => {
      const b0 = sink(b);
      if (t < 0.2) return b0.lerp(b, t / 0.2);
      const u = (t - 0.2) / 0.8;
      if (!ctrl) return b.clone().lerp(D, u);
      const a = b.clone().multiplyScalar((1 - u) * (1 - u));
      return a.addScaledVector(ctrl, 2 * u * (1 - u)).addScaledVector(D, u * u);
    };
    const eP = edge(P), eQ = edge(Q), eR = edge(R, RC), eI = edge(I);
    const col = gradY(COLORS.magHi, COLORS.magLo, 2.95, 3.7);
    const faces = [[eP, eQ], [eQ, eR], [eR, eI], [eI, eP]];
    const base = P.clone().add(Q).add(R).add(I).multiplyScalar(0.25);
    const earJ = joint(H, base, 'ear' + s);
    faces.forEach(([a, b], k) => {
      mesh(geo('ear' + s + k, () => loft(s < 0 ? b : a, s < 0 ? a : b, seg(24), 10, { colorFn: col })), M.mag, earJ.rest, 'ear');
    });
    // inner ear (grey) inset on the opening face
    const n = new THREE.Vector3().crossVectors(Q.clone().sub(P), D.clone().sub(P)).normalize();
    if (n.z < 0) n.negate();
    const Dp = D.clone().lerp(P.clone().lerp(Q, 0.5), 0.09);
    const Pp = P.clone().lerp(Q, 0.12).lerp(D, 0.04);
    const Qp = Q.clone().lerp(P, 0.3).lerp(D, 0.05);
    const off = n.clone().multiplyScalar(0.012);
    const Ps = sink(P).lerp(Q, 0.06), Qs = sink(Q).lerp(P, 0.18);
    mesh(geo('earInner' + s, () => loft(
      (t) => (t < 0.25 ? Ps.clone().lerp(Pp, t / 0.25) : Pp.clone().lerp(Dp, (t - 0.25) / 0.75)).add(off),
      (t) => (t < 0.25 ? Qs.clone().lerp(Qp, t / 0.25) : Qp.clone().lerp(Dp, (t - 0.25) / 0.75)).add(off),
      12, 2, { colorFn: gradY(COLORS.earInnerHi, COLORS.earInnerLo, 3.6, 3.0) })), M.earInner, earJ.rest, 'earInner');
    ears[s > 0 ? 'L' : 'R'] = earJ.j;
  }

  // ---------------- torso
  const T = torsoJ.rest;
  const tC = v3(0, 1.02, 0.03), tR = v3(0.5, 0.56, 0.37);
  const torsoFn = ellipsoidFn(tC, tR, { eUp: 0.92, eDown: 0.8 });
  const torsoMat = M.white.clone();
  torsoMat.map = LOGO_TEX || (LOGO_TEX = makeLogoTexture());
  mesh(geo('torso', () => paramSurface(torsoFn, seg(96), seg(64), {
    uvFn: (p, n) => (n.z > 0.05 ? [(p.x + 0.42) / 0.84, (p.y - 0.78) / 0.84] : [0.002, 0.002]),
  })), torsoMat, T, 'torso');
  // shoulder yoke: torso surface scaled up, kept above a plane tilted down toward the back
  const yokeFn = ellipsoidFn(tC, tR.clone().multiplyScalar(1.02), { eUp: 0.92, eDown: 0.8 });
  mesh(geo('yoke', () => clipGeometry(paramSurface(yokeFn, seg(96), seg(64), { colorFn: gradY(COLORS.suitHi, COLORS.suitLo, 1.0, 1.55) }), v3(0, 0.951, -0.309), 1.265)), M.suit, T, 'yoke');
  // back light
  {
    const g = new THREE.Group();
    g.position.set(0, 1.385, -0.355); g.rotation.x = Math.PI + 0.32; T.add(g);
    lightPuck(g, 0.115, 0.085, 0.03);
  }
  function lightPuck(g, rOuter, rCore, h) {
    const key = [rOuter, rCore, h].join(',');
    mesh(geo('ring' + key, () => { const r = new THREE.CylinderGeometry(rOuter, rOuter * 1.04, h, seg(40)); r.rotateX(Math.PI / 2); r.translate(0, 0, h / 2); return r; }), M.ring, g, 'ring');
    mesh(geo('core' + key, () => { const c = new THREE.CylinderGeometry(rCore, rCore, h * 0.6, seg(40)); c.rotateX(Math.PI / 2); c.translate(0, 0, h + h * 0.1); return c; }), M.cyan, g, 'core');
  }

  // ---------------- arms
  const arms = {};
  for (const s of [1, -1]) {
    const side = s > 0 ? 'L' : 'R';
    const sh = joint(T, v3(0.4 * s, 1.4, -0.04), 'shoulder' + side);
    mesh(geo('upper' + side, () => {
      const u = new THREE.CapsuleGeometry(0.165, 0.26, 10, seg(32)); u.translate(0.4 * s, 1.4 - 0.16, -0.04);
      paintGrad(u, COLORS.suitHi, COLORS.suitLo, 1.5, 1.0); return u;
    }), M.suit, sh.rest, 'arm' + side);
    const el = joint(sh.rest, v3(0.4 * s, 1.04, -0.04), 'elbow' + side);
    mesh(geo('fore' + side, () => { const f = new THREE.CapsuleGeometry(0.175, 0.16, 10, seg(32)); f.translate(0.4 * s, 0.95, -0.04); return f; }), M.dark, el.rest, 'fore' + side);
    // elbow light, outer side
    const lg = new THREE.Group(); lg.position.set(0.535 * s, 1.0, 0.04); lg.rotation.y = (Math.PI / 2 - 0.55) * s; el.rest.add(lg);
    lightPuck(lg, 0.13, 0.075, 0.035);
    // hand: palm faces local +z of the wrist joint; default palm turned toward the body
    const wr = joint(el.rest, v3(0.4 * s, 0.84, -0.04), 'wrist' + side);
    const hand = new THREE.Group(); hand.position.copy(wr.at); wr.rest.add(hand);
    buildHand(hand, side);
    arms[side] = { sh: sh.j, el: el.j, wr: wr.j, hand };
  }
  function buildHand(g, side) {
    const m = new THREE.Group(); m.position.set(0, -0.16, 0); g.add(m);
    mesh(geo('handBody', () => { const b = new THREE.SphereGeometry(1, seg(48), seg(32)); b.scale(0.21, 0.22, 0.15); return b; }), M.dark, m, 'hand' + side);
    for (const k of [-1, 0, 1]) { // finger bumps along the bottom edge
      mesh(geo('finger' + k, () => { const f = new THREE.SphereGeometry(0.075, seg(24), seg(16)); f.translate(k * 0.105, -0.175 + Math.abs(k) * 0.035, 0.0); return f; }), M.dark, m, 'hand' + side);
    }
    mesh(geo('thumb', () => { const t = new THREE.SphereGeometry(0.07, seg(20), seg(14)); t.scale(1, 1.3, 0.9); t.translate(0.17, 0.02, 0.05); return t; }), M.dark, m, 'hand' + side);
    // palm pad (lighter cap) + paw beans
    mesh(geo('palm', () => {
      const p = new THREE.SphereGeometry(1, seg(40), seg(24), 0, Math.PI * 2, 0, 0.9); p.rotateX(Math.PI / 2);
      p.scale(0.17, 0.18, 0.06); p.translate(0, -0.01, 0.1); return p;
    }), M.palm, m, 'palm' + side);
    const surfZ = (x, y) => 0.1 + 0.06 * Math.sqrt(Math.max(0, 1 - (x / 0.17) ** 2 - ((y + 0.01) / 0.18) ** 2));
    const bean = (x, y, ro, rc) => {
      const b = new THREE.Group(); b.position.set(x, y, surfZ(x, y) - 0.012); m.add(b);
      lightPuck(b, ro, rc, 0.02);
    };
    bean(0.0, 0.05, 0.085, 0.048);
    for (const a of [-0.8, 0, 0.8]) bean(Math.sin(a) * 0.115, 0.05 - 0.09 - Math.cos(a) * 0.06, 0.05, 0.03);
  }

  // ---------------- legs
  const legs = {};
  const legProf = [[0.001, 0], [0.16, 0], [0.215, 0.008], [0.248, 0.035], [0.258, 0.075], [0.245, 0.125], [0.22, 0.18], [0.203, 0.26], [0.198, 0.62], [0.001, 0.66]];
  for (const s of [1, -1]) {
    const side = s > 0 ? 'L' : 'R';
    const lj = joint(hips.rest, v3(0.315 * s, 0.6, 0), 'leg' + side);
    mesh(geo('leg' + side, () => {
      const g = new THREE.LatheGeometry(legProf.map(([r, h]) => new THREE.Vector2(r, h)), seg(48), 0, Math.PI * 2);
      const p = g.attributes.position;
      for (let i = 0; i < p.count; i++) {
        const y = p.getY(i);
        p.setZ(i, p.getZ(i) * 1.1 + 0.07 * (1 - THREE.MathUtils.smoothstep(y, 0, 0.22)));
      }
      g.computeVertexNormals();
      g.translate(0.315 * s, 0, 0);
      return g;
    }), M.white, lj.rest, 'leg' + side);
    // toe lines on the outer front of each foot
    for (const off of [0.04, 0.15]) {
      mesh(geo('toe' + side + off, () => {
        const pts = [];
        for (let i = 0; i <= 8; i++) {
          const y = 0.015 + 0.13 * (i / 8);
          let r = 0.2;
          for (let k = 0; k + 1 < legProf.length; k++) {
            const [r0, h0] = legProf[k], [r1, h1] = legProf[k + 1];
            if (y >= h0 && y <= h1) { r = r0 + (r1 - r0) * (y - h0) / (h1 - h0); break; }
          }
          const a = Math.asin(Math.min(0.95, off / r)) * s;
          pts.push(v3(0.315 * s + Math.sin(a) * r * 1.01, y, Math.cos(a) * r * 1.1 * 1.01 + 0.07 * (1 - THREE.MathUtils.smoothstep(y, 0, 0.22))));
        }
        return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 12, 0.007, 6);
      }), M.seam, lj.rest, null);
    }
    legs[side] = lj.j;
  }

  // ---------------- tail (rebuilt every frame from control points, positions only)
  const TAIL_REST = [
    v3(0, 0.82, -0.25), v3(0.12, 0.58, -0.48), v3(0.5, 0.36, -0.72), v3(0.98, 0.5, -0.95),
    v3(1.24, 0.9, -1.08), v3(1.24, 1.3, -1.1), v3(1.04, 1.45, -0.98), v3(0.86, 1.37, -0.88),
  ];
  const TS = seg(96), TR = 10;
  const tailGeo = new THREE.TubeGeometry(new THREE.CatmullRomCurve3(TAIL_REST), TS, 1, TR);
  const tailMesh = mesh(tailGeo, M.tail, T, 'tail');
  const tipCap = mesh(geo('tip', () => new THREE.SphereGeometry(0.052, 20, 14)), M.tail, T, 'tail');
  const frames = { t: new THREE.Vector3(), n: new THREE.Vector3(), b: new THREE.Vector3() };
  function tailUpdate(fn) {
    const pts = TAIL_REST.map((p, i) => (fn ? fn(p.clone(), i / (TAIL_REST.length - 1)) : p.clone()));
    const curve = new THREE.CatmullRomCurve3(pts, false, 'centripetal');
    const fr = curve.computeFrenetFrames(TS, false);
    const pos = tailGeo.attributes.position;
    const c = new THREE.Vector3();
    for (let i = 0; i <= TS; i++) {
      const t = i / TS;
      curve.getPointAt(t, c);
      let r = 0.042 + 0.012 * t;
      if (t > 0.97) r *= Math.sqrt(Math.max(0, 1 - ((t - 0.97) / 0.03) ** 2)) * 0.6 + 0.4;
      const N = fr.normals[i], B = fr.binormals[i];
      for (let j = 0; j <= TR; j++) {
        const v = (j / TR) * Math.PI * 2, sn = Math.sin(v), cs = -Math.cos(v);
        pos.setXYZ(i * (TR + 1) + j, c.x + r * (cs * N.x + sn * B.x), c.y + r * (cs * N.y + sn * B.y), c.z + r * (cs * N.z + sn * B.z));
      }
    }
    pos.needsUpdate = true;
    tailGeo.computeVertexNormals();
    tailGeo.computeBoundingSphere();
    tipCap.position.copy(curve.getPointAt(1));
  }
  tailUpdate();

  // ---------------- poses
  const J = { root, hips: hips.j, torso: torsoJ.j, neck: neck.j, arms, legs, ears };
  const jointList = [J.hips, J.torso, J.neck, ears.L, ears.R, legs.L, legs.R];
  for (const side of ['L', 'R']) jointList.push(arms[side].sh, arms[side].el, arms[side].wr);
  const hands = [arms.L.hand, arms.R.hand];
  const snapshot = () => ({ q: jointList.map((j) => j.quaternion.clone()), hip: J.hips.position.clone(), hs: hands.map((h) => h.scale.x) });
  const applyPose = (P) => {
    jointList.forEach((j, i) => j.quaternion.copy(P.q[i]));
    J.hips.position.copy(P.hip);
    hands.forEach((h, i) => h.scale.setScalar(P.hs[i]));
  };
  const mixPose = (A, B, w) => ({
    q: A.q.map((q, i) => q.clone().slerp(B.q[i], w)),
    hip: A.hip.clone().lerp(B.hip, w),
    hs: A.hs.map((h, i) => h + (B.hs[i] - h) * w),
  });
  const resetJoints = () => {
    for (const side of ['L', 'R']) {
      const s = side === 'L' ? 1 : -1, A = arms[side];
      A.sh.rotation.set(0, 0, 0.42 * s);
      A.el.rotation.set(0, 0, 0.08 * s);
      A.wr.rotation.set(0, -(Math.PI / 2) * s, 0);
      A.hand.scale.setScalar(1);
    }
    for (const j of [J.hips, J.torso, J.neck, legs.L, legs.R, ears.L, ears.R]) j.rotation.set(0, 0, 0);
    J.hips.position.set(0, 0.62, 0);
  };
  const poses = {};
  resetJoints(); poses.rest = snapshot();
  { // reference hero pose: right paw up, palm to camera, paw enlarged like the sheet
    const R = arms.R;
    const q1 = aim(R.sh, null, v3(-0.539, -0.539, 0.647));
    const q2 = aim(R.el, q1, v3(-0.567, 0.423, 0.707));
    const f = v3(-0.85, -0.5, 0).normalize();
    const y = f.clone().negate(), z = v3(0, 0, 1), x = new THREE.Vector3().crossVectors(y, z);
    const qh = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z));
    R.wr.quaternion.copy(q2.clone().invert().multiply(qh));
    R.hand.scale.setScalar(2.0);
    poses.hero = snapshot();
  }
  resetJoints();
  { // both paws up, cheering
    for (const side of ['L', 'R']) {
      const s = side === 'L' ? 1 : -1, A = arms[side];
      A.sh.rotation.set(0, 0, 2.5 * s);
      A.el.rotation.set(0, 0, 0.25 * s);
      A.wr.rotation.set(0, -(Math.PI / 2) * s, 0);
    }
    poses.cheer = snapshot();
  }
  resetJoints();
  { // thinking: right paw at the chin
    const R = arms.R;
    const q1 = aim(R.sh, null, v3(-0.35, -0.75, 0.56));
    const q2 = aim(R.el, q1, v3(0.45, 0.75, 0.48));
    const qh = new THREE.Quaternion().setFromEuler(new THREE.Euler(-0.3, 0.4, 2.6));
    R.wr.quaternion.copy(q2.clone().invert().multiply(qh));
    poses.think = snapshot();
  }
  resetJoints();
  applyPose(poses.rest);

  return {
    root, J, face, visor, ids, poses, materials: M,
    tail: tailUpdate,
    snapshot, applyPose, mixPose,
    poseRest() { applyPose(poses.rest); },
    poseHero() { applyPose(poses.hero); },
    setLed(c) { M.cyan.color.set(c); M.cyan.emissive.set(c); },
  };
}
let LOGO_TEX = null;

// point a bone (rest direction -y) along dir, given its parent's accumulated rotation; returns its own
function aim(j, parentQ, dir) {
  const q = new THREE.Quaternion().setFromUnitVectors(v3(0, -1, 0), dir.clone().normalize());
  j.quaternion.copy(parentQ ? parentQ.clone().invert().multiply(q) : q);
  return q;
}

function paintGrad(geo, hi, lo, y0, y1) {
  const p = geo.attributes.position, c = [];
  const H = new THREE.Color(hi), L = new THREE.Color(lo);
  for (let i = 0; i < p.count; i++) {
    const t = THREE.MathUtils.clamp((p.getY(i) - y1) / (y0 - y1), 0, 1);
    const k = L.clone().lerp(H, t); c.push(k.r, k.g, k.b);
  }
  geo.setAttribute('color', new THREE.Float32BufferAttribute(c, 3));
}

function makeLogoTexture() {
  // canvas maps torso front projection: x in [-0.42, 0.42], y in [0.78, 1.62]
  const S = 1024, c = document.createElement('canvas');
  c.width = c.height = S;
  const g = c.getContext('2d');
  g.fillStyle = COLORS.white; g.fillRect(0, 0, S, S);
  const k = S / 0.84;
  const X = (x) => (x + 0.42) * k, Y = (y) => (1.62 - y) * k;
  // logo: width 0.6625, height 0.619, top at y 1.469 (sheet measurements)
  const top = Y(1.469), h = 0.619 * k, w = 0.6625 * k;
  const grad = g.createLinearGradient(0, top, 0, top + h);
  grad.addColorStop(0, '#dd25f1'); grad.addColorStop(0.45, '#d327ec'); grad.addColorStop(1, '#9f31c8');
  g.fillStyle = grad;
  logoPath(g, X(0), top, w, h);
  g.fill('nonzero');
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  return t;
}
