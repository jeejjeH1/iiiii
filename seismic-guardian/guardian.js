// Seismic — "The Guardian". A faceted stone golem takes plain data into its chest,
// seals it, and releases it as encrypted crystals that join a chain.
// Everything is a pure function of time: renderAt(t) draws one frame.
import * as THREE from 'three';
import { ConvexGeometry } from 'three/addons/geometries/ConvexGeometry.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';

const { T, CUBES, CRYSTALS, DURATION, ACTS } = globalThis.TL;

// ------------------------------------------------------------------ palettes
// "original" follows the character drawing; "brand" maps it onto the Seismic kit.
const PALETTES = {
  original: {
    brown: ['#3f302a', '#4a3931', '#55423a', '#372a25', '#5e4a40'],
    cap: ['#a58d7e', '#b8a293', '#8f786a'],
    cool: ['#2f343e', '#373d48', '#2a2e36', '#3e4451'],
    dark: ['#241e22', '#2c2429', '#1d181b'],
    panel: '#1a1719',
    outline: '#120d0f',
    bg: '#2e2e2f',
    fog: '#2e2e2f',
    ground: '#3a3a3a',
    groundAlt: '#333333',
  },
  brand: {
    brown: ['#523542', '#5d3c4b', '#694656', '#472e39', '#825a6d'],
    cap: ['#a98a9a', '#bba2ae', '#957685'],
    cool: ['#2b2a2a', '#343332', '#3d3c3b', '#474645'],
    dark: ['#161616', '#1d1c1c', '#242323'],
    panel: '#161616',
    outline: '#0c0b0b',
    bg: '#1f1e1f',
    fog: '#1f1e1f',
    ground: '#2a2929',
    groundAlt: '#242323',
  },
};
const GLOW = new THREE.Color('#d9a2bd');      // light mauve used for light/energy
const MAUVE = new THREE.Color('#825a6d');
const WHITE = new THREE.Color('#ffffff');

// ------------------------------------------------------------------ helpers
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, k) => a + (b - a) * k;
const smooth = (a, b, x) => { const k = clamp((x - a) / (b - a)); return k * k * (3 - 2 * k); };
const easeOutBack = (k) => { const c = 1.7; return 1 + (c + 1) * Math.pow(k - 1, 3) + c * Math.pow(k - 1, 2); };
const easeInCubic = (k) => k * k * k;
const easeOutCubic = (k) => 1 - Math.pow(1 - k, 3);
function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}
const hash = (n) => { const x = Math.sin(n * 127.1) * 43758.5453; return x - Math.floor(x); };

// ------------------------------------------------------------------ renderer
const W = 1920, H = 1080;
const canvas = document.getElementById('stage-canvas');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(W, H, false);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;

const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2('#2e2e2f', 0.028);
const camera = new THREE.PerspectiveCamera(35, W / H, 0.1, 200);

const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(W, H), 0.9, 0.55, 0.82);
composer.addPass(bloom);
composer.addPass(new OutputPass());

// cel-shading ramp: shadow / mid / lit
const ramp = new THREE.DataTexture(new Uint8Array([105, 105, 105, 255, 175, 175, 175, 255, 255, 255, 255, 255]), 3, 1);
ramp.minFilter = ramp.magFilter = THREE.NearestFilter;
ramp.needsUpdate = true;

// ------------------------------------------------------------------ lights
const hemi = new THREE.HemisphereLight('#d8d2d6', '#3a3236', 0.55);
scene.add(hemi);
const key = new THREE.DirectionalLight('#fff3ea', 1.15);
key.position.set(6, 9, 7);
scene.add(key);
const rim = new THREE.DirectionalLight('#9aa6bf', 0.55);
rim.position.set(-8, 4, -3);
scene.add(rim);
const coreLight = new THREE.PointLight(GLOW, 0, 9, 1.6);
scene.add(coreLight);
const eyeLight = new THREE.PointLight('#ffffff', 0, 3, 2);
scene.add(eyeLight);

// ------------------------------------------------------------------ faceted rocks
// Every body part is a convex hull of a chamfered, jittered box, so it breaks into
// flat facets like the drawing. Each facet gets one flat color picked by "role".
const rocks = [];       // { mesh, edges, hullMesh, faces, opts }
const outlineMats = []; // edge line materials (recolored by the energy sweep)

function chamferedBox(w, h, d, o, r) {
  const pts = [];
  const bevel = o.bevel ?? 0.2;
  const jit = o.jitter ?? 0.05;
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) for (const sz of [-1, 1]) {
    const tx = sy > 0 ? (o.taperTop ?? 1) : (o.taperBottom ?? 1);
    const tz = sy > 0 ? (o.taperTopZ ?? o.taperTop ?? 1) : (o.taperBottomZ ?? o.taperBottom ?? 1);
    const cx = sx * w / 2 * tx, cy = sy * h / 2, cz = sz * d / 2 * tz;
    const b = Math.min(w * tx, h, d * tz) * bevel;
    const j = () => (r() - 0.5) * 2 * jit * Math.min(w, h, d);
    pts.push(new THREE.Vector3(cx - sx * b + j(), cy + j(), cz + j()));
    pts.push(new THREE.Vector3(cx + j(), cy - sy * b + j(), cz + j()));
    pts.push(new THREE.Vector3(cx + j(), cy + j(), cz - sz * b + j()));
  }
  // extra bulges break big faces into more facets
  for (let i = 0; i < (o.bulges ?? 4); i++) {
    const ax = Math.floor(r() * 3), s = r() < 0.5 ? -1 : 1;
    const v = new THREE.Vector3((r() - 0.5) * w * 0.6, (r() - 0.5) * h * 0.6, (r() - 0.5) * d * 0.6);
    const half = [w / 2, h / 2, d / 2][ax] * (1 + (o.bulge ?? 0.06));
    v.setComponent(ax, s * half);
    pts.push(v);
  }
  return pts;
}

function buildRock(name, w, h, d, o = {}) {
  const r = rng(o.seed ?? (name.length * 977 + w * 131 + h * 71));
  const geo = new ConvexGeometry(chamferedBox(w, h, d, o, r));
  // group triangles into facets by plane, then color each facet
  const pos = geo.attributes.position, nor = geo.attributes.normal;
  const triCount = pos.count / 3;
  const faces = [];
  const facetKey = new Map();
  for (let t = 0; t < triCount; t++) {
    const n = new THREE.Vector3(nor.getX(t * 3), nor.getY(t * 3), nor.getZ(t * 3)).normalize();
    const p = new THREE.Vector3(pos.getX(t * 3), pos.getY(t * 3), pos.getZ(t * 3));
    const k = [Math.round(n.x * 20), Math.round(n.y * 20), Math.round(n.z * 20), Math.round(n.dot(p) * 20)].join(',');
    if (!facetKey.has(k)) facetKey.set(k, { n, pick: r() });
    faces.push(facetKey.get(k));
  }
  geo.setAttribute('color', new THREE.BufferAttribute(new Float32Array(pos.count * 3), 3));
  const mat = new THREE.MeshToonMaterial({ vertexColors: true, gradientMap: ramp });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.name = name;
  // ink lines on every facet edge + a thicker silhouette from an inverted hull
  const lineMat = new THREE.LineBasicMaterial({ color: '#120d0f', toneMapped: false });
  outlineMats.push(lineMat);
  const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geo, 12), lineMat);
  mesh.add(edges);
  const hullMat = new THREE.MeshBasicMaterial({ color: '#120d0f', side: THREE.BackSide });
  const hullMesh = new THREE.Mesh(geo, hullMat);
  hullMesh.scale.setScalar(o.ink ?? 1.035);
  mesh.add(hullMesh);
  const rock = { mesh, edges, hullMesh, lineMat, hullMat, faces, o, glow: 0 };
  rocks.push(rock);
  return mesh;
}

function paintRock(rock, P) {
  const { mesh, faces, o } = rock;
  const col = mesh.geometry.attributes.color;
  const c = new THREE.Color();
  const coolDir = o.cool ? new THREE.Vector3(...o.cool).normalize() : null;
  faces.forEach((f, t) => {
    let set = P[o.role ?? 'brown'];
    if (o.cap && f.n.y > (o.capAt ?? 0.55)) set = P.cap;
    else if (coolDir && f.n.dot(coolDir) > (o.coolAt ?? 0.35)) set = P.cool;
    if (o.role === 'panel') c.set(P.panel);
    else c.set(set[Math.floor(f.pick * set.length) % set.length]);
    for (let v = 0; v < 3; v++) col.setXYZ(t * 3 + v, c.r, c.g, c.b);
  });
  col.needsUpdate = true;
  rock.lineMat.color.set(P.outline);
  rock.hullMat.color.set(P.outline);
  rock.baseOutline = new THREE.Color(P.outline);
}

// place a rock between two points (limb segments)
function segment(name, a, b, thick, o = {}) {
  const dir = new THREE.Vector3().subVectors(b, a);
  const len = dir.length();
  const m = buildRock(name, thick * (o.wScale ?? 1), len + thick * (o.overlap ?? 0.25), thick * (o.dScale ?? 1), o);
  m.position.copy(a).add(b).multiplyScalar(0.5);
  m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
  if (o.twist) m.rotateY(o.twist);
  return m;
}
const V = (x, y, z) => new THREE.Vector3(x, y, z);

// ------------------------------------------------------------------ the golem
// Facing +z. Character's left arm/leg is +x (the viewer's right in the drawing).
const golem = new THREE.Group();
scene.add(golem);

// hips: pelvis, belt ring, waist disc
const hips = new THREE.Group();
hips.position.set(0, 2.62, 0);
golem.add(hips);
{
  const pelvis = buildRock('pelvis', 1.25, 0.72, 0.95, { role: 'dark', bevel: 0.32, bulges: 8, seed: 11, taperBottom: 0.7 });
  pelvis.position.set(0, -0.12, -0.02);
  hips.add(pelvis);
  // belt: an octagonal ring of stone
  const r = rng(5);
  const pts = [];
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2 + Math.PI / 8;
    for (const [y, s] of [[0.42, 0.9], [0.32, 1.0], [0.12, 1.0], [0.02, 0.88]]) {
      const rr = 0.98 * s * (1 + (r() - 0.5) * 0.06);
      pts.push(V(Math.cos(a) * rr * 1.06, y, Math.sin(a) * rr * 0.86));
    }
  }
  const belt = buildRock('belt', 1, 1, 1, { cap: true, capAt: 0.5, seed: 19 });
  belt.geometry.dispose();
  const bg = new ConvexGeometry(pts);
  // rebuild facet table for the custom hull
  const rock = rocks[rocks.length - 1];
  belt.geometry = bg;
  rock.edges.geometry = new THREE.EdgesGeometry(bg, 12);
  rock.hullMesh.geometry = bg;
  const P = bg.attributes.position, N = bg.attributes.normal;
  const rr = rng(23), map = new Map();
  rock.faces = [];
  for (let t = 0; t < P.count / 3; t++) {
    const n = V(N.getX(t * 3), N.getY(t * 3), N.getZ(t * 3)).normalize();
    const k = [Math.round(n.x * 20), Math.round(n.y * 20), Math.round(n.z * 20)].join(',');
    if (!map.has(k)) map.set(k, { n, pick: rr() });
    rock.faces.push(map.get(k));
  }
  bg.setAttribute('color', new THREE.BufferAttribute(new Float32Array(P.count * 3), 3));
  belt.position.set(0, 0.08, 0);
  hips.add(belt);
  const waist = buildRock('waist', 1.45, 0.34, 1.15, { role: 'cool', bevel: 0.35, bulges: 6, seed: 29 });
  waist.position.set(0, 0.62, -0.02);
  hips.add(waist);
}

// torso pivots at the waist
const torso = new THREE.Group();
torso.position.set(0, 3.25, 0);
golem.add(torso);

{
  const chest = buildRock('chest', 2.5, 1.5, 1.38, { taperBottom: 0.6, taperBottomZ: 0.82, bevel: 0.2, bulges: 6, seed: 41, cap: true, capAt: 0.8 });
  chest.position.set(0, 0.82, -0.02);
  torso.add(chest);
  const abs = buildRock('abs', 1.35, 0.5, 1.05, { taperBottom: 0.75, bevel: 0.25, seed: 43 });
  abs.position.set(0, 0.1, 0.02);
  torso.add(abs);
  // side plates and a collar, like the separate stones around the drawing's chest panel
  const plateL = buildRock('plateL', 0.5, 1.1, 1.2, { taperBottom: 0.6, seed: 47, cap: true });
  plateL.position.set(0.98, 0.95, 0.06); plateL.rotation.z = -0.32;
  torso.add(plateL);
  const plateR = buildRock('plateR', 0.5, 1.05, 1.15, { taperBottom: 0.6, seed: 53, cool: [-1, 0, 0.2] });
  plateR.position.set(-0.98, 0.9, 0.04); plateR.rotation.z = 0.32;
  torso.add(plateR);
  const collar = buildRock('collar', 1.5, 0.32, 1.0, { seed: 59, cap: true, capAt: 0.5 });
  collar.position.set(0, 1.62, -0.05);
  torso.add(collar);
  const plateLow = buildRock('plateLow', 1.25, 0.32, 0.4, { taperBottom: 0.7, seed: 61 });
  plateLow.position.set(0, 0.36, 0.55); plateLow.rotation.x = -0.25;
  torso.add(plateLow);
}

// chest panel: dark doors carrying the emblem halves, a glowing core behind them
const PANEL = { y: 0.9, z: 0.72, w: 1.25, h: 0.78 };
const cavity = new THREE.Mesh(
  new THREE.PlaneGeometry(PANEL.w * 0.96, PANEL.h * 0.95),
  new THREE.MeshBasicMaterial({ color: '#0b090a' }),
);
cavity.position.set(0, PANEL.y, PANEL.z - 0.06);
torso.add(cavity);
const cavityGlow = new THREE.Mesh(
  new THREE.PlaneGeometry(PANEL.w * 0.96, PANEL.h * 0.95),
  new THREE.MeshBasicMaterial({ color: GLOW, transparent: true, opacity: 0, toneMapped: false, blending: THREE.AdditiveBlending, depthWrite: false }),
);
cavityGlow.position.set(0, PANEL.y, PANEL.z - 0.05);
torso.add(cavityGlow);

const core = new THREE.Mesh(
  new THREE.IcosahedronGeometry(0.2, 0),
  new THREE.MeshBasicMaterial({ color: new THREE.Color('#f3cfe0').multiplyScalar(2.2), toneMapped: false }),
);
core.position.set(0, PANEL.y, PANEL.z + 0.02);
torso.add(core);
const coreShell = new THREE.LineSegments(
  new THREE.EdgesGeometry(new THREE.IcosahedronGeometry(0.3, 0)),
  new THREE.LineBasicMaterial({ color: GLOW.clone().multiplyScalar(2), toneMapped: false, transparent: true }),
);
core.add(coreShell);

function emblemTexture(side) {
  // The chest mark: six rounded strokes forming a stylised X of nested brackets,
  // traced from the supplied emblem (350px space, centre x = 175). side = -1 shows
  // the left half, +1 the right half, so each door carries its half of the mark.
  const c = document.createElement('canvas');
  c.width = c.height = 512;
  const g = c.getContext('2d');
  const S = 512 / 230;
  g.scale(S, S);
  g.translate(-60, -58);
  g.strokeStyle = '#ffffff';
  g.lineWidth = 17;
  g.lineCap = 'round';
  g.lineJoin = 'round';
  const path = (f) => { g.beginPath(); f(); g.stroke(); };
  for (const m of [1, -1]) {
    const X = (x) => 175 + m * (x - 175);
    // outer arms: flat, then sweeping in to the crossing
    path(() => { g.moveTo(X(75), 77); g.lineTo(X(122), 77); g.bezierCurveTo(X(150), 77, X(160), 84, X(175), 101); });
    path(() => { g.moveTo(X(75), 268); g.lineTo(X(122), 268); g.bezierCurveTo(X(150), 268, X(160), 261, X(175), 244); });
    // big bracket
    path(() => { g.moveTo(X(95), 116); g.lineTo(X(118), 116); g.bezierCurveTo(X(150), 116, X(167), 142, X(167), 176); g.bezierCurveTo(X(167), 210, X(150), 237, X(118), 237); g.lineTo(X(95), 237); });
    // small inner bracket
    path(() => { g.moveTo(X(106), 142); g.bezierCurveTo(X(127), 142, X(140), 157, X(140), 176); g.bezierCurveTo(X(140), 195, X(127), 210, X(106), 210); });
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  tex.repeat.set(0.5, 1);
  tex.offset.set(side < 0 ? 0 : 0.5, 0);
  return tex;
}

const doors = [];
for (const side of [-1, 1]) {
  const hinge = new THREE.Group();
  hinge.position.set(side * PANEL.w / 2, PANEL.y, PANEL.z);
  torso.add(hinge);
  const shape = new THREE.Shape();
  const hw = PANEL.w / 2, hh = PANEL.h / 2;
  // trapezoid, wider at the top like the drawing's panel
  shape.moveTo(0, -hh); shape.lineTo(-side * hw * 0.98, -hh * 0.94);
  shape.lineTo(-side * hw * 1.0, hh); shape.lineTo(0, hh * 1.04);
  shape.closePath();
  const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.07, bevelEnabled: true, bevelThickness: 0.02, bevelSize: 0.02, bevelSegments: 1 });
  const door = new THREE.Mesh(geo, new THREE.MeshToonMaterial({ color: '#1a1719', gradientMap: ramp }));
  door.position.z = -0.02;
  hinge.add(door);
  const lm = new THREE.LineBasicMaterial({ color: '#120d0f', toneMapped: false });
  door.add(new THREE.LineSegments(new THREE.EdgesGeometry(geo, 20), lm));
  const em = new THREE.Mesh(
    new THREE.PlaneGeometry(0.34, 0.68),
    new THREE.MeshBasicMaterial({ map: emblemTexture(side), transparent: true, toneMapped: false, depthWrite: false }),
  );
  em.position.set(-side * (PANEL.w / 2 - 0.17), 0.0, 0.1);
  hinge.add(em);
  doors.push({ hinge, door, em, side, lm });
}

// head
const head = new THREE.Group();
head.position.set(0.04, 1.86, 0.18);
torso.add(head);
{
  const skull = buildRock('head', 0.66, 0.62, 0.66, { taperTop: 0.78, bevel: 0.26, bulges: 6, seed: 71, cap: true, capAt: 0.7 });
  skull.position.set(0, 0.28, 0);
  head.add(skull);
}
const eyeMat = new THREE.MeshBasicMaterial({ color: '#ffffff', toneMapped: false });
const eyes = [];
const glowTex = (() => {
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const g = c.getContext('2d');
  const gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(255,255,255,0.45)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
})();
for (const s of [-1, 1]) {
  const e = new THREE.Mesh(new THREE.SphereGeometry(0.048, 12, 8), eyeMat);
  e.position.set(s * 0.15, 0.38, 0.31);
  head.add(e);
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, color: '#ffffff', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  sp.scale.setScalar(0.42);
  e.add(sp);
  eyes.push({ e, sp });
}

// shoulders + arms (children of torso so they ride the breathing)
{
  const shL = buildRock('shoulderL', 1.12, 0.98, 1.08, { seed: 83, cap: true, capAt: 0.35, bevel: 0.24, bulges: 6 });
  shL.position.set(1.52, 1.25, -0.05); shL.rotation.set(0.1, 0.25, -0.35);
  torso.add(shL);
  const shR = buildRock('shoulderR', 0.98, 0.88, 1.0, { seed: 89, cool: [-0.4, 0.2, 0.6], coolAt: -0.2, bevel: 0.24, bulges: 6 });
  shR.position.set(-1.46, 1.08, -0.08); shR.rotation.set(0.05, -0.2, 0.3);
  torso.add(shR);
}

// character's left arm (+x): elbow out, fist planted on the hip
const armL = new THREE.Group();
armL.position.set(1.62, 0.95, -0.08);
torso.add(armL);
{
  const o = armL.position;
  const sh = V(0, 0, 0), el = V(2.08 - o.x, -0.35 - o.y + 0.5, -0.2 - o.z), wr = V(1.32 - o.x, -0.5 - o.y + 0.1, 0.3 - o.z);
  armL.add(segment('upperArmL', sh, el, 0.72, { seed: 97, cap: true, capAt: 0.6 }));
  const elbow = buildRock('elbowL', 0.62, 0.55, 0.62, { seed: 101 });
  elbow.position.copy(el); armL.add(elbow);
  armL.add(segment('forearmL', el, wr, 0.74, { seed: 103, cap: true, capAt: 0.45, wScale: 1.08 }));
  const fist = buildRock('fistL', 0.66, 0.66, 0.66, { seed: 107, cap: true, capAt: 0.3, bevel: 0.28 });
  fist.position.copy(wr).add(V(-0.12, -0.1, 0.06)); fist.rotation.set(0.2, 0.5, 0.3);
  armL.add(fist);
}
// character's right arm (-x): darker, in the cool shadow, fist resting at the hip
const armR = new THREE.Group();
armR.position.set(-1.56, 0.82, -0.1);
torso.add(armR);
{
  const o = armR.position;
  const sh = V(0, 0, 0), el = V(-1.98 - o.x, -0.28 - o.y + 0.5, -0.32 - o.z), wr = V(-1.48 - o.x, -0.78 - o.y + 0.25, 0.02 - o.z);
  armR.add(segment('upperArmR', sh, el, 0.66, { seed: 109, role: 'cool', cool: [0, 1, 0], coolAt: 2 }));
  const elbow = buildRock('elbowR', 0.56, 0.5, 0.56, { seed: 113, cool: [-1, 0, 0.5], coolAt: 0.1 });
  elbow.position.copy(el); armR.add(elbow);
  armR.add(segment('forearmR', el, wr, 0.66, { seed: 127, cool: [-0.3, 0, 1], coolAt: 0.1 }));
  const fist = buildRock('fistR', 0.6, 0.6, 0.6, { seed: 131, cool: [-1, 0.3, 0.6], coolAt: 0.2, bevel: 0.28 });
  fist.position.copy(wr).add(V(0.05, -0.12, 0.02)); fist.rotation.set(-0.2, -0.4, 0.2);
  armR.add(fist);
}

// legs: wide stance, huge blocky shins and slab feet
function buildLeg(side) {
  const g = new THREE.Group();
  const hip = V(side * 0.52, 2.5, 0);
  g.position.copy(hip);
  golem.add(g);
  const knee = V(side * 1.12 - hip.x, 1.92 - hip.y, 0.08);
  const cool = side < 0 ? [1, 0, 0.3] : null;
  g.add(segment('thigh' + side, V(0, 0, 0), knee, 0.88, { seed: 137 + side * 3, cool, coolAt: 0.3, wScale: 1.05 }));
  const kneeCap = buildRock('knee' + side, 0.98, 0.5, 0.95, { seed: 149 + side, cap: side > 0, capAt: 0.4, cool, coolAt: 0.3 });
  kneeCap.position.copy(knee).add(V(side * 0.04, -0.05, 0.06));
  g.add(kneeCap);
  const shin = buildRock('shin' + side, 1.08, 1.32, 1.04, { taperBottom: 1.06, taperTop: 0.92, seed: 157 + side * 5, cool, coolAt: 0.25, bulges: 6 });
  shin.position.set(side * 1.2 - hip.x, 1.07 - hip.y, 0.06);
  shin.rotation.z = side * 0.05;
  g.add(shin);
  const foot = buildRock('foot' + side, 1.28, 0.48, 1.78, { taperTop: 0.82, taperTopZ: 0.8, seed: 163 + side * 7, cap: side > 0, capAt: 0.55, cool, coolAt: 0.5, bevel: 0.22 });
  foot.position.set(side * 1.3 - hip.x, 0.24 - hip.y, 0.3);
  foot.rotation.y = side * 0.22;
  g.add(foot);
  return { g, foot };
}
const legL = buildLeg(1);
const legR = buildLeg(-1);

// ------------------------------------------------------------------ ground
const groundGeo = new THREE.PlaneGeometry(90, 90, 110, 110);
groundGeo.rotateX(-Math.PI / 2);
const gBase = new Float32Array(groundGeo.attributes.position.count);
{
  const p = groundGeo.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), z = p.getZ(i);
    const jx = (hash(i * 3.1) - 0.5) * 0.5, jz = (hash(i * 7.7) - 0.5) * 0.5;
    p.setX(i, x + jx); p.setZ(i, z + jz);
    gBase[i] = (hash(i * 1.3) - 0.5) * 0.12 - 0.02;
    p.setY(i, gBase[i]);
  }
}
const groundMat = new THREE.MeshToonMaterial({ color: '#3a3a3a', gradientMap: ramp });
const ground = new THREE.Mesh(groundGeo, groundMat);
scene.add(ground);
const groundLines = new THREE.LineSegments(
  new THREE.WireframeGeometry(groundGeo),
  new THREE.LineBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.035, depthWrite: false }),
);
scene.add(groundLines);
groundLines.geometry = new THREE.WireframeGeometry(groundGeo);

// seismic rings for the stomp
const rings = [];
for (let i = 0; i < 4; i++) {
  const m = new THREE.Mesh(
    new THREE.RingGeometry(0.94, 1, 72),
    new THREE.MeshBasicMaterial({ color: GLOW.clone().multiplyScalar(1.6), transparent: true, opacity: 0, toneMapped: false, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide }),
  );
  m.rotation.x = -Math.PI / 2;
  scene.add(m);
  rings.push(m);
}

// ------------------------------------------------------------------ particles
function makePoints(n, size, color, opacity) {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(n * 3), 3));
  const m = new THREE.PointsMaterial({ size, color, transparent: true, opacity, depthWrite: false, map: glowTex, blending: THREE.AdditiveBlending, sizeAttenuation: true });
  const p = new THREE.Points(g, m);
  scene.add(p);
  return p;
}
const motes = makePoints(260, 0.09, '#e7cfdb', 0.5);
const dust = makePoints(220, 0.22, '#a99ca2', 0.0);

// ------------------------------------------------------------------ labels (sprites)
const FONT_MONO = '"Geist Mono", ui-monospace, "SFMono-Regular", Menlo, monospace';
function labelSprite(w = 512, h = 96) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, toneMapped: false }));
  sp.scale.set(w / h * 0.22, 0.22, 1);
  sp.userData = { c, g: c.getContext('2d'), tex, text: null };
  return sp;
}
function drawLabel(sp, text, style) {
  const key = text + '|' + style;
  if (sp.userData.text === key) return;
  sp.userData.text = key;
  const { c, g, tex } = sp.userData;
  g.clearRect(0, 0, c.width, c.height);
  g.font = `500 44px ${FONT_MONO}`;
  const tw = Math.min(c.width - 8, g.measureText(text).width + 44);
  const x0 = (c.width - tw) / 2;
  g.fillStyle = style === 'enc' ? 'rgba(82,53,66,0.88)' : 'rgba(22,22,22,0.78)';
  g.beginPath(); g.roundRect(x0, 14, tw, c.height - 28, 10); g.fill();
  g.strokeStyle = style === 'enc' ? 'rgba(217,162,189,0.9)' : 'rgba(255,255,255,0.5)';
  g.lineWidth = 2; g.stroke();
  g.fillStyle = style === 'enc' ? '#f2d9e5' : '#ffffff';
  g.textBaseline = 'middle'; g.textAlign = 'center';
  g.fillText(text, c.width / 2, c.height / 2 + 2);
  tex.needsUpdate = true;
}

// ------------------------------------------------------------------ data cubes (plaintext in)
const PLAIN = [
  'balance: 4,280 USDC', 'bid: 31.5 ETH', 'to: 0x71C…9A3F', 'salary: 8,400',
  'vote: YES', 'order: BUY 120', 'position: LONG 5x', 'health_id: 22-8841',
  'payroll: 12 rows', 'score: 742', 'swap: 900 → ETH', 'key: owner',
];
const cubes = [];
{
  const r = rng(777);
  for (let i = 0; i < CUBES; i++) {
    const grp = new THREE.Group();
    const box = new THREE.Mesh(
      new THREE.BoxGeometry(0.22, 0.22, 0.22),
      new THREE.MeshBasicMaterial({ color: new THREE.Color('#ffffff').multiplyScalar(1.15), toneMapped: false, transparent: true }),
    );
    const edge = new THREE.LineSegments(new THREE.EdgesGeometry(box.geometry), new THREE.LineBasicMaterial({ color: GLOW.clone().multiplyScalar(1.5), toneMapped: false, transparent: true }));
    box.add(edge);
    grp.add(box);
    const lab = labelSprite();
    lab.position.set(0, 0.32, 0);
    grp.add(lab);
    drawLabel(lab, PLAIN[i], 'plain');
    scene.add(grp);
    const side = i % 2 ? 1 : -1;
    const ang = side * (0.5 + r() * 1.1) + (r() - 0.5) * 0.3;
    const rad = 13 + r() * 6;
    cubes.push({
      grp, box, lab,
      start: V(Math.sin(ang) * rad, 1.5 + r() * 5, Math.cos(ang) * rad * 0.7 - 2),
      orbitR: 2.9 + r() * 1.3, orbitA: ang * 0.8 + (r() - 0.5) * 0.6, orbitY: 3.0 + r() * 2.3,
      spin: V(r(), r(), r()), phase: r() * 6.28,
    });
  }
}

// ------------------------------------------------------------------ encrypted crystals (logo shape)
// Facets of the Seismic mark, traced from the logo (400px space).
const LOGO_FACETS = [
  { pts: [[198, 50], [274, 84], [265, 191], [195, 100]], col: '#6d5660' },
  { pts: [[190, 58], [186, 103], [122, 187], [131, 167]], col: '#5f4650' },
  { pts: [[191, 107], [263, 200], [167, 344], [95, 235]], col: '#593d49' },
  { pts: [[279, 113], [300, 232], [272, 200], [272, 191]], col: '#887780' },
  { pts: [[268, 207], [299, 250], [261, 353], [171, 352]], col: '#745f68' },
];
function crystalGeometry() {
  const pts = [];
  const S = 1 / 160, cx = 198, cy = 200;
  const outline = [[198, 50], [274, 84], [279, 113], [300, 232], [299, 250], [261, 353], [171, 352], [95, 235], [122, 187], [190, 58]];
  for (const [x, y] of outline) for (const z of [-0.12, 0.12]) pts.push(V((x - cx) * S, -(y - cy) * S, z));
  for (const [x, y] of [[191, 107], [265, 191], [268, 207], [200, 220]]) for (const z of [-0.34, 0.34]) pts.push(V((x - cx) * S, -(y - cy) * S, z));
  return new ConvexGeometry(pts);
}
const crystalGeo = crystalGeometry();
{
  // color each facet by the logo facet it faces most, so the gem reads as the mark
  const P = crystalGeo.attributes.position, N = crystalGeo.attributes.normal;
  const cols = [];
  const pal = ['#593d49', '#6d5660', '#745f68', '#887780', '#5f4650', '#825a6d', '#523542'];
  for (let t = 0; t < P.count / 3; t++) {
    const n = V(N.getX(t * 3), N.getY(t * 3), N.getZ(t * 3));
    const i = Math.abs(Math.round(n.x * 3 + n.y * 5 + n.z * 2)) % pal.length;
    const c = new THREE.Color(pal[i]);
    for (let v = 0; v < 3; v++) cols.push(c.r, c.g, c.b);
  }
  crystalGeo.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
}
const crystals = [];
const CHAIN = [];
for (let k = 0; k < CRYSTALS; k++) {
  const x = -7.4 + k * 1.35;
  CHAIN.push(V(x, 7.4 + Math.sin(k * 0.9) * 0.35 - Math.abs(x) * 0.05, -6.5 - Math.abs(x) * 0.18));
}
for (let i = 0; i < CRYSTALS; i++) {
  const grp = new THREE.Group();
  const m = new THREE.Mesh(crystalGeo, new THREE.MeshToonMaterial({ vertexColors: true, gradientMap: ramp, emissive: MAUVE, emissiveIntensity: 0.25 }));
  const lm = new THREE.LineBasicMaterial({ color: GLOW.clone().multiplyScalar(1.4), toneMapped: false, transparent: true });
  m.add(new THREE.LineSegments(new THREE.EdgesGeometry(crystalGeo, 10), lm));
  m.scale.setScalar(0.6);
  grp.add(m);
  const lab = labelSprite();
  lab.position.set(0, 0.85, 0); lab.scale.multiplyScalar(1.5);
  grp.add(lab);
  scene.add(grp);
  crystals.push({ grp, m, lm, lab });
}
// links between chain slots
const linkGeo = new THREE.BufferGeometry();
linkGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array((CRYSTALS - 1) * 6), 3));
linkGeo.setAttribute('color', new THREE.BufferAttribute(new Float32Array((CRYSTALS - 1) * 6), 3));
const links = new THREE.LineSegments(linkGeo, new THREE.LineBasicMaterial({ vertexColors: true, toneMapped: false, transparent: true }));
scene.add(links);

// a hero crystal (the logo) that lands in the end card
const hero = new THREE.Mesh(crystalGeo, new THREE.MeshToonMaterial({ vertexColors: true, gradientMap: ramp, emissive: MAUVE, emissiveIntensity: 0.2 }));
const heroLines = new THREE.LineBasicMaterial({ color: GLOW.clone().multiplyScalar(1.2), toneMapped: false, transparent: true });
hero.add(new THREE.LineSegments(new THREE.EdgesGeometry(crystalGeo, 10), heroLines));
scene.add(hero);

// ------------------------------------------------------------------ camera path
// [time, position, target, fov]
const CAM = [
  [0.0, [0.3, 1.25, 14.5], [0, 3.3, 0], 33],
  [3.0, [0.2, 1.0, 11.0], [0, 3.7, 0], 33],
  [4.6, [0.9, 1.25, 9.6], [0, 4.0, 0], 34],
  [7.6, [7.2, 2.6, 10.4], [0, 3.8, 0], 40],
  [10.4, [2.6, 3.5, 6.6], [0, 4.0, 0.4], 38],
  [13.2, [-2.2, 3.8, 5.4], [0, 4.1, 0.6], 36],
  [15.4, [0.0, 4.05, 4.5], [0, 4.15, 0.7], 34],
  [17.6, [-5.4, 1.0, 12.6], [0, 5.2, -3], 44],
  [21.4, [4.6, 1.3, 13.5], [0, 5.0, -3], 44],
  [23.2, [2.8, 0.75, 10.6], [0, 2.4, 0], 40],
  [25.4, [0.9, 0.45, 14.2], [-2.6, 3.3, 0], 36],
  [30.0, [0.6, 0.4, 13.0], [-2.7, 3.45, 0], 36],
];
function camAt(t) {
  let i = 0;
  while (i < CAM.length - 2 && t > CAM[i + 1][0]) i++;
  const k0 = CAM[Math.max(0, i - 1)], k1 = CAM[i], k2 = CAM[i + 1], k3 = CAM[Math.min(CAM.length - 1, i + 2)];
  const u = smooth(k1[0], k2[0], t);
  const cr = (a, b, c, d) => {
    // Catmull-Rom with eased parameter for velocity continuity-ish
    const u2 = u * u, u3 = u2 * u;
    return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
  };
  const v = (j) => cr(k0[j], k1[j], k2[j], k3[j]);
  const pos = V(0, 0, 0), tgt = V(0, 0, 0);
  for (let a = 0; a < 3; a++) {
    pos.setComponent(a, cr(k0[1][a], k1[1][a], k2[1][a], k3[1][a]));
    tgt.setComponent(a, cr(k0[2][a], k1[2][a], k2[2][a], k3[2][a]));
  }
  return { pos, tgt, fov: v(3) };
}

// ------------------------------------------------------------------ palette switching
let paletteName = 'original';
function applyPalette(name) {
  paletteName = name;
  const P = PALETTES[name];
  rocks.forEach((r) => paintRock(r, P));
  scene.background = new THREE.Color(P.bg);
  scene.fog.color.set(P.fog);
  groundMat.color.set(P.ground);
  doors.forEach((d) => { d.door.material.color.set(P.panel); d.lm.color.set(P.outline); });
}
applyPalette('original');

// ------------------------------------------------------------------ frame
const tmp = V(0, 0, 0);
const chestWorld = () => core.getWorldPosition(new THREE.Vector3());
const SCRAMBLE = '▓▒░█0123456789abcdef';

function update(t) {
  // ---------- golem body
  const breathe = Math.sin(t * 1.25) * 0.5 + 0.5;
  const rise = smooth(T.rise[0], T.rise[1], t);
  // stomp: character's left leg lifts and slams
  const lift = smooth(T.lift[0], T.lift[1], t) * (1 - smooth(T.lift[1] + 0.02, T.stomp, t));
  const impact = t >= T.stomp ? Math.exp(-(t - T.stomp) * 7) : 0;
  torso.rotation.x = lerp(0.2, 0, rise) + breathe * 0.012 - lift * 0.05 + impact * 0.05;
  torso.rotation.z = lerp(-0.05, 0, rise) - lift * 0.07;
  torso.position.y = 3.25 + lerp(-0.18, 0, rise) + breathe * 0.025 - impact * 0.08;
  torso.scale.set(1 + breathe * 0.008, 1 + breathe * 0.012, 1 + breathe * 0.008);
  hips.position.y = 2.62 + lerp(-0.1, 0, rise) - impact * 0.06;
  hips.rotation.z = -lift * 0.06;
  legL.g.rotation.x = -lift * 0.42;
  legL.g.rotation.z = lift * 0.18;
  legL.g.position.y = 2.5 + lift * 0.12;
  // head: down while dormant, follows the action afterwards
  let lookY = 0, lookX = lerp(0.42, 0, smooth(T.rise[0] + 0.4, T.rise[1], t));
  lookY += Math.sin((t - T.cubesIn) * 0.9) * 0.35 * smooth(T.cubesIn, T.cubesIn + 1, t) * (1 - smooth(9.5, 10.5, t));
  lookX += 0.18 * smooth(10.2, 11, t) * (1 - smooth(14.6, 15.2, t));          // looks down at its chest
  lookX -= 0.32 * smooth(17, 18.5, t) * (1 - smooth(21.4, 22.4, t));          // looks up at the chain
  lookY += 0.25 * smooth(17, 18.5, t) * Math.sin((t - 17) * 0.5) * (1 - smooth(21.4, 22.4, t));
  head.rotation.set(lookX, lookY, 0);
  armL.rotation.z = breathe * 0.02;
  armR.rotation.z = -breathe * 0.02;

  // ---------- eyes, emblem
  const eyeOn = smooth(T.eyes, T.eyes + 0.12, t);
  const flick = t < T.eyes + 0.5 ? (Math.sin(t * 90) > 0.2 ? 1 : 0.35) : 1;
  const eyeI = eyeOn * flick * (1 + impact * 0.8);
  eyes.forEach(({ e, sp }) => { e.material.color.setScalar(0.25 + 2.4 * eyeI); sp.material.opacity = eyeI * 0.85; sp.scale.setScalar(0.32 + eyeI * 0.12 + Math.exp(-(t - T.eyes) * 4) * (t > T.eyes ? 0.6 : 0)); });
  head.getWorldPosition(tmp); eyeLight.position.copy(tmp).add(V(0, 0.4, 0.6)); eyeLight.intensity = eyeI * 0.35;

  const embOn = smooth(T.emblem, T.emblem + 0.6, t);
  const sealFlash = t > T.close[1] ? Math.exp(-(t - T.close[1]) * 2.2) : 0;
  const embI = (0.12 + 0.9 * embOn) * (1 + 0.7 * sealFlash) * (0.92 + 0.08 * Math.sin(t * 2.4));
  const open = smooth(T.open[0], T.open[1], t) * (1 - smooth(T.close[0], T.close[1], t));
  const openOver = t < T.close[0] ? easeOutBack(clamp((t - T.open[0]) / (T.open[1] - T.open[0]))) : open;
  doors.forEach((d) => {
    const a = clamp(openOver, 0, 1.08) * 1.85;
    d.hinge.rotation.y = d.side < 0 ? -a : a;
    d.em.material.color.setScalar(embI * 0.95);
    d.em.material.opacity = 0.35 + 0.65 * embOn;
  });

  // ---------- core
  let pulse = 0;
  for (let i = 0; i < CUBES; i++) {
    const ta = T.absorb + i * T.absorbGap + T.absorbDur;
    if (t > ta) pulse += Math.exp(-(t - ta) * 6);
  }
  const coreOn = smooth(T.open[0], T.open[0] + 0.6, t) * (1 - smooth(T.close[0], T.close[1] + 0.05, t));
  core.scale.setScalar(Math.max(0.001, coreOn * (1 + pulse * 0.35)));
  core.rotation.set(t * 0.7, t * 1.1, 0);
  coreShell.rotation.set(-t * 0.9, t * 0.4, 0);
  cavityGlow.material.opacity = coreOn * (0.35 + pulse * 0.35);
  coreLight.position.copy(chestWorld()).add(V(0, 0, 0.5));
  coreLight.intensity = coreOn * (3 + pulse * 5) + sealFlash * 4;

  // ---------- energy sweep up the facet edges after sealing
  const sweepY = lerp(-0.5, 7.5, smooth(T.sweep[0], T.sweep[1], t));
  const sweepOn = t > T.sweep[0] && t < T.sweep[1] + 0.6 ? 1 - smooth(T.sweep[1], T.sweep[1] + 0.6, t) : 0;
  const stompGlow = impact;
  const glowC = GLOW.clone().multiplyScalar(2.2);
  rocks.forEach((r) => {
    r.mesh.getWorldPosition(tmp);
    const band = Math.exp(-Math.pow((tmp.y - sweepY) / 0.6, 2)) * sweepOn;
    const g = Math.max(band, stompGlow * Math.exp(-tmp.y * 0.45));
    r.lineMat.color.copy(r.baseOutline).lerp(glowC, clamp(g));
  });

  // ---------- data cubes
  const center = chestWorld();
  cubes.forEach((c, i) => {
    const t0 = T.cubesIn + i * T.cubeGap;
    const ta = T.absorb + i * T.absorbGap;
    const appear = smooth(t0, t0 + 0.5, t);
    if (t < t0 || t > ta + T.absorbDur) { c.grp.visible = false; return; }
    c.grp.visible = true;
    const a = c.orbitA + (t - t0) * 0.16 * (i % 2 ? 1 : -1);
    const slot = V(Math.sin(a) * c.orbitR, c.orbitY + Math.sin(t * 0.8 + c.phase) * 0.12, Math.cos(a) * c.orbitR * 0.8 + 1.0);
    const arrive = easeOutCubic(clamp((t - t0) / 3.4));
    const p = c.start.clone().lerp(slot, arrive);
    p.y += Math.sin(arrive * Math.PI) * 0.8;
    const k = clamp((t - ta) / T.absorbDur);
    if (k > 0) {
      const e = easeInCubic(k);
      const sp = (1 - e) * 0.9;
      const ang = e * 5.5 + i;
      const target = center.clone().add(V(Math.sin(ang) * sp, Math.cos(ang) * sp * 0.6, 0.15 * (1 - e)));
      p.lerp(target, e);
    }
    c.grp.position.copy(p);
    c.box.rotation.set(t * c.spin.x * 1.5, t * (0.6 + c.spin.y), t * c.spin.z);
    const s = appear * (1 - smooth(0.7, 1, k));
    c.box.scale.setScalar(Math.max(0.001, s * (1 + Math.max(0, Math.sin((t - t0) * 9)) * 0.2 * (1 - arrive))));
    c.lab.material.opacity = appear * (1 - smooth(0.0, 0.35, k));
  });

  // ---------- encrypted crystals rise into the chain
  const backPos = torso.localToWorld(V(0, 1.55, -0.55));
  let lit = 0;
  crystals.forEach((c, i) => {
    const te = T.emit + i * T.emitGap;
    const k = clamp((t - te) / T.emitDur);
    if (t < te) { c.grp.visible = false; return; }
    c.grp.visible = true;
    lit++;
    const e = easeOutCubic(k);
    const slot = CHAIN[i];
    const ctrl = V(slot.x * 0.35, 9.8, -2.5);
    // quadratic bezier from the golem's back to its chain slot
    const p = backPos.clone().multiplyScalar((1 - e) * (1 - e)).add(ctrl.clone().multiplyScalar(2 * e * (1 - e))).add(slot.clone().multiplyScalar(e * e));
    const wave = t > T.stomp ? Math.exp(-Math.pow((t - T.stomp - 0.35 - i * 0.05) * 6, 2)) : 0;
    p.y += Math.sin(t * 1.3 + i) * 0.06 * e + wave * 0.35;
    c.grp.position.copy(p);
    c.m.rotation.set(Math.sin(t + i) * 0.15, t * 0.7 + i, 0);
    c.m.scale.setScalar(0.6 * (0.2 + 0.8 * easeOutBack(clamp(k * 2.5))) * (1 + wave * 0.3));
    c.m.material.emissiveIntensity = 0.25 + Math.exp(-Math.max(0, t - te - T.emitDur) * 3) * 0.6 * (k >= 1 ? 1 : 0) + wave;
    c.lm.color.copy(GLOW).multiplyScalar(1.2 + wave * 2);
    // label: scrambled ciphertext that never resolves
    const n = 9;
    let s = 'enc:';
    for (let j = 0; j < n; j++) s += SCRAMBLE[Math.floor(hash(i * 31 + j * 7 + Math.floor(t * 10) * 13) * SCRAMBLE.length)];
    drawLabel(c.lab, s, 'enc');
    c.lab.material.opacity = smooth(0.35, 0.8, k) * (1 - smooth(T.endCard - 0.6, T.endCard + 0.4, t));
  });
  // chain links light up as neighbours arrive
  const lp = linkGeo.attributes.position, lc = linkGeo.attributes.color;
  for (let i = 0; i < CRYSTALS - 1; i++) {
    const tA = T.emit + (i + 1) * T.emitGap + T.emitDur;
    const on = smooth(tA - 0.1, tA + 0.3, t);
    const a = crystals[i].grp.position, b = crystals[i + 1].grp.position;
    const bb = a.clone().lerp(b, on);
    lp.setXYZ(i * 2, a.x, a.y, a.z); lp.setXYZ(i * 2 + 1, bb.x, bb.y, bb.z);
    const flash = t > tA ? Math.exp(-(t - tA) * 3) : 0;
    const wave = t > T.stomp ? Math.exp(-Math.pow((t - T.stomp - 0.35 - i * 0.05) * 6, 2)) : 0;
    const c = GLOW.clone().multiplyScalar((0.6 + flash * 2 + wave * 2) * on);
    lc.setXYZ(i * 2, c.r, c.g, c.b); lc.setXYZ(i * 2 + 1, c.r, c.g, c.b);
  }
  lp.needsUpdate = lc.needsUpdate = true;
  links.visible = t > T.emit;

  // ---------- hero crystal in the end card
  const he = smooth(T.endCard + 0.2, T.endCard + 1.6, t);
  hero.visible = he > 0;
  hero.position.set(-6.1, 4.9 + Math.sin(t * 1.1) * 0.06 + (1 - he) * -0.6, 1.5);
  hero.rotation.set(0.05, Math.sin(t * 0.6) * 0.35 - 0.2, 0);
  hero.scale.setScalar(Math.max(0.001, 0.85 * easeOutBack(he)));

  // ---------- ground waves + rings
  const p = groundGeo.attributes.position;
  const ft = legL.foot.getWorldPosition(new THREE.Vector3());
  const dt = t - T.stomp;
  for (let i = 0; i < p.count; i++) {
    let y = gBase[i];
    if (dt > 0 && dt < 4) {
      const dx = p.getX(i) - ft.x, dz = p.getZ(i) - ft.z;
      const d = Math.sqrt(dx * dx + dz * dz);
      const front = dt * 11;
      y += Math.exp(-Math.pow((d - front) / 1.1, 2)) * 0.32 * Math.exp(-dt * 0.9) * Math.exp(-d * 0.02);
    }
    p.setY(i, y);
  }
  p.needsUpdate = true;
  groundLines.geometry.dispose();
  groundLines.geometry = new THREE.WireframeGeometry(groundGeo);
  groundLines.material.opacity = 0.035 + (dt > 0 ? Math.exp(-dt * 1.2) * 0.08 : 0);
  rings.forEach((m, i) => {
    const d = dt - i * 0.22;
    if (d <= 0 || d > 3) { m.visible = false; return; }
    m.visible = true;
    m.position.set(ft.x, 0.03 + i * 0.002, ft.z);
    const rr = 0.5 + d * (10 - i * 1.5);
    m.scale.setScalar(rr);
    m.material.opacity = Math.exp(-d * 1.3) * (1 - i * 0.18);
  });

  // ---------- particles
  {
    const a = motes.geometry.attributes.position;
    for (let i = 0; i < a.count; i++) {
      const r0 = hash(i + 1) * 16 + 1.5, ang = hash(i + 7) * 6.283 + t * 0.03 * (hash(i + 3) - 0.5);
      const y = (hash(i + 11) * 9 + t * (0.08 + hash(i + 5) * 0.1)) % 9;
      a.setXYZ(i, Math.cos(ang) * r0, y + 0.1, Math.sin(ang) * r0 - 2);
    }
    a.needsUpdate = true;
    motes.material.opacity = 0.32 + 0.2 * smooth(T.eyes, T.eyes + 2, t);
  }
  {
    const a = dust.geometry.attributes.position;
    for (let i = 0; i < a.count; i++) {
      const ang = hash(i * 1.7) * 6.283, sp = 1.5 + hash(i * 2.3) * 4;
      const d = Math.max(0, dt);
      const rr = sp * (1 - Math.exp(-d * 2));
      const y = Math.max(0.05, hash(i * 4.1) * 2.2 * (1 - Math.exp(-d * 3)) - d * d * 0.25);
      a.setXYZ(i, ft.x + Math.cos(ang) * rr, y, ft.z + Math.sin(ang) * rr);
    }
    a.needsUpdate = true;
    dust.material.opacity = dt > 0 ? 0.55 * Math.exp(-dt * 0.9) : 0;
  }

  // ---------- camera (with shake on impacts)
  const cam = camAt(t);
  const shake = impact * 0.18 + sealFlash * 0.05 * (t < T.close[1] + 0.4 ? 1 : 0);
  cam.pos.x += Math.sin(t * 61) * shake; cam.pos.y += Math.sin(t * 47 + 1) * shake;
  camera.position.copy(cam.pos);
  camera.fov = cam.fov;
  camera.updateProjectionMatrix();
  camera.lookAt(cam.tgt);

  // ---------- grade
  renderer.toneMappingExposure = lerp(0.55, 1.05, smooth(0, 3.4, t)) * (1 - 0.18 * smooth(T.endCard - 0.4, T.endCard + 1.2, t));
  bloom.strength = 0.85 + sealFlash * 0.3 + impact * 0.5;
  key.intensity = lerp(0.55, 1.15, smooth(2.4, 4.6, t));
  scene.fog.density = lerp(0.04, 0.026, smooth(0, 5, t));

  updateOverlay(t);
}

// ------------------------------------------------------------------ overlay (captions + end card)
const capEl = document.getElementById('caption');
const endEl = document.getElementById('endcard');
function updateOverlay(t) {
  let act = ACTS[0];
  for (const a of ACTS) if (t >= a.at) act = a;
  const next = ACTS[ACTS.indexOf(act) + 1];
  const into = t - act.at, left = (next ? next.at : DURATION) - t;
  const op = act.label ? smooth(0.15, 0.7, into) * smooth(0.0, 0.5, left) : 0;
  if (capEl) {
    if (capEl.dataset.text !== act.label) { capEl.textContent = act.label; capEl.dataset.text = act.label; }
    capEl.style.opacity = op.toFixed(3);
  }
  if (endEl) {
    const e = smooth(T.endCard + 0.6, T.endCard + 1.8, t);
    endEl.style.opacity = e.toFixed(3);
    endEl.style.transform = `translateY(${((1 - e) * 1.2).toFixed(3)}cqw)`;
  }
}

export function renderAt(t) {
  update(Math.max(0, Math.min(DURATION, t)));
  composer.render();
}
export function setPalette(name) { applyPalette(name); }
export function getPalette() { return paletteName; }
export { DURATION };
