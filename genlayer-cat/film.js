// "The Dispute" — GenLayer cat, idea 4 (Equivalence Principle / Optimistic Democracy).
// Every frame is a pure function of t, so the renderer can split the film across workers.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createCharacter, LOGO_L, LOGO_DIAMOND, COLORS } from './character.js';
import { ToonPipeline } from './toon.js';
import * as HG from './holo.js';

const TL = window.TL, K = TL.K;
export const W = 1920, H = 1080;
const { pink: PINK, violet: VIOLET, cyan: CYAN } = HG.THEME;

// ---------------------------------------------------------------- math
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lin = (t, a, b) => clamp((t - a) / (b - a));
const sm = (x) => x * x * (3 - 2 * x);
const eio = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const eout = (x) => 1 - Math.pow(1 - x, 3);
const back = (x, s = 1.70158) => 1 + (s + 1) * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2);
const mix = (a, b, t) => a + (b - a) * t;
const win = (t, a, b, fi = 0.25, fo = 0.25) => Math.min(sm(lin(t, a, a + fi)), 1 - sm(lin(t, b - fo, b)));
const V = (x, y, z) => new THREE.Vector3(x, y, z);
const hash = (i) => {
  let h = Math.imul(i ^ 0x9e3779b9, 0x85ebca6b); h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
};
const R = (i, j = 0) => hash(i * 7919 + j * 104729 + 13);
const fxSum = (type, t, key = 'amp') => {
  let v = 0;
  for (const e of TL.FX) if (e.type === type && t >= e.t && t < e.t + e.dur) { const k = 1 - (t - e.t) / e.dur; v += e[key] * k * k; }
  return v;
};

// ---------------------------------------------------------------- renderer
const glCanvas = document.createElement('canvas');
const renderer = new THREE.WebGLRenderer({ canvas: glCanvas, antialias: false, powerPreference: 'high-performance' });
renderer.setPixelRatio(1);
renderer.setSize(W, H, false);
renderer.toneMapping = THREE.NeutralToneMapping;
renderer.toneMappingExposure = 2.0;
renderer.outputColorSpace = THREE.SRGBColorSpace;
export const out = document.createElement('canvas');
out.width = W; out.height = H;
const octx = out.getContext('2d');

const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
scene.environmentIntensity = 0.5;
scene.add(new THREE.HemisphereLight('#ffffff', '#3a1a55', 0.42));
const key = new THREE.DirectionalLight('#fff6ff', 0.62); key.position.set(3, 7, 9); scene.add(key);
const fill = new THREE.DirectionalLight('#c9b8ff', 0.14); fill.position.set(-7, 3, 5); scene.add(fill);
const rimA = new THREE.DirectionalLight(PINK, 0.65); rimA.position.set(-6, 4, -7); scene.add(rimA);
const rimB = new THREE.DirectionalLight(VIOLET, 0.75); rimB.position.set(7, 5, -6); scene.add(rimB);

const camera = new THREE.PerspectiveCamera(34, W / H, 0.1, 300);
const pipe = new ToonPipeline(renderer, scene, camera, { width: W, height: H, lineWidth: 2.1, bloom: { strength: 0.65, radius: 0.5, threshold: 1.3 }, grade: true, samples: Number(new URLSearchParams(location.search).get('msaa') ?? 0) });

// ---------------------------------------------------------------- world
const sky = new THREE.Mesh(new THREE.SphereGeometry(150, 48, 24), new THREE.ShaderMaterial({
  side: THREE.BackSide, depthWrite: false,
  uniforms: { uTime: { value: 0 } },
  vertexShader: `varying vec3 vD; void main(){ vD = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: `
    uniform float uTime; varying vec3 vD;
    void main(){
      float h = vD.y;
      vec3 top = vec3(0.004, 0.0015, 0.012), hor = vec3(0.042, 0.007, 0.065), low = vec3(0.01, 0.002, 0.02);
      vec3 c = mix(hor, top, smoothstep(0.0, 0.55, h));
      c = mix(c, low, smoothstep(0.0, -0.2, h));
      float a = atan(vD.x, vD.z);
      c += vec3(0.5, 0.05, 0.55) * 0.05 * exp(-pow((a - 0.9) * 1.6, 2.0)) * exp(-pow((h - 0.12) * 5.0, 2.0));
      c += vec3(0.6, 0.25, 0.65) * 0.035 * exp(-pow((a + 1.1) * 1.4, 2.0)) * exp(-pow((h - 0.25) * 4.0, 2.0));
      c += vec3(0.86, 0.0, 1.0) * 0.035 * exp(-abs(h) * 30.0);
      gl_FragColor = vec4(c, 1.0);
    }`,
}));
scene.add(sky);

const floorU = {
  uTime: { value: 0 }, uBeat: { value: 0 }, uRipT: { value: -1 }, uRipC: { value: new THREE.Vector2() }, uGlow: { value: 1 },
  uShadows: { value: Array.from({ length: 6 }, () => new THREE.Vector3()) },
  cA: { value: new THREE.Color(PINK) }, cB: { value: new THREE.Color(VIOLET) },
};
const floor = new THREE.Mesh(new THREE.PlaneGeometry(160, 160), new THREE.ShaderMaterial({
  uniforms: floorU,
  vertexShader: `varying vec3 vW; void main(){ vec4 w = modelMatrix * vec4(position, 1.0); vW = w.xyz; gl_Position = projectionMatrix * viewMatrix * w; }`,
  fragmentShader: `
    uniform float uTime, uBeat, uRipT, uGlow; uniform vec2 uRipC; uniform vec3 uShadows[6]; uniform vec3 cA, cB;
    varying vec3 vW;
    void main(){
      vec2 p = vW.xz;
      float d = length(p);
      vec2 q = p * 0.5;
      vec2 g = abs(fract(q - 0.5) - 0.5) / fwidth(q);
      float line = 1.0 - min(min(g.x, g.y), 1.0);
      float fade = exp(-d * 0.085);
      vec3 col = vec3(0.005, 0.0012, 0.01);
      vec3 lc = mix(cB, cA, 0.5 + 0.5 * sin(d * 0.35 - uTime * 0.9));
      col += lc * line * fade * (0.16 + 0.3 * uBeat);
      // dots at grid crossings
      vec2 f = abs(fract(q) - 0.0);
      col += lc * 0.0;
      // ripple from impacts
      if (uRipT >= 0.0) {
        float rd = length(p - uRipC);
        float r = uRipT * 10.0;
        col += cA * exp(-pow((rd - r) * 1.6, 2.0)) * exp(-uRipT * 1.4) * 1.4;
        col += cA * line * exp(-pow((rd - r) * 0.5, 2.0)) * exp(-uRipT * 1.2) * 1.2;
      }
      col += cB * exp(-d * d * 0.05) * 0.1 * uGlow;
      for (int i = 0; i < 6; i++) {
        vec3 s = uShadows[i];
        if (s.z > 0.0) { float sd = length(p - s.xy) / s.z; col *= 1.0 - 0.8 * exp(-sd * sd * 2.2); }
      }
      // horizon fade into sky colour
      col = mix(col, vec3(0.03, 0.005, 0.048), smoothstep(14.0, 60.0, d));
      gl_FragColor = vec4(col, 1.0);
    }`,
}));
floor.rotation.x = -Math.PI / 2;
scene.add(floor);

// ---------------------------------------------------------------- particles
function pointsMaterial(additive = true) {
  return new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
    uniforms: { uScale: { value: H * 0.5 }, uOpacity: { value: 1 } },
    vertexShader: `attribute float size; attribute vec3 color; attribute float alpha; varying vec3 vC; varying float vA;
      uniform float uScale;
      void main(){ vC = color; vA = alpha; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_PointSize = size * uScale / -mv.z; gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `varying vec3 vC; varying float vA; uniform float uOpacity;
      void main(){ vec2 d = gl_PointCoord - 0.5; float r = dot(d, d) * 4.0; float a = exp(-r * 3.0) * (1.0 - smoothstep(0.8, 1.0, r)); gl_FragColor = vec4(vC * a * vA * uOpacity, a * vA * uOpacity); }`,
  });
}
function makePoints(n) {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
  g.setAttribute('size', new THREE.Float32BufferAttribute(new Float32Array(n), 1));
  g.setAttribute('alpha', new THREE.Float32BufferAttribute(new Float32Array(n), 1));
  const p = new THREE.Points(g, pointsMaterial());
  p.frustumCulled = false;
  scene.add(p);
  return p;
}
const PAL = [PINK, VIOLET, CYAN, '#ffffff'].map((c) => new THREE.Color(c));
const dust = makePoints(420), streams = makePoints(300), burst = makePoints(160);
function setPt(p, i, x, y, z, c, size, a) {
  const g = p.geometry;
  g.attributes.position.setXYZ(i, x, y, z);
  g.attributes.color.setXYZ(i, c.r, c.g, c.b);
  g.attributes.size.setX(i, size);
  g.attributes.alpha.setX(i, a);
}
function flushPts(p) { for (const k of ['position', 'color', 'size', 'alpha']) p.geometry.attributes[k].needsUpdate = true; }

// confetti
const CONF = 260;
const confetti = new THREE.InstancedMesh(new THREE.PlaneGeometry(0.11, 0.18), new THREE.MeshBasicMaterial({ side: THREE.DoubleSide, toneMapped: true }), CONF);
confetti.frustumCulled = false;
for (let i = 0; i < CONF; i++) confetti.setColorAt(i, new THREE.Color([PINK, VIOLET, CYAN, '#ffffff', '#ffd36b'][i % 5]).multiplyScalar(1.4));
scene.add(confetti);

// light beams (validator spawns, the landing)
const beamGeo = new THREE.CylinderGeometry(1, 1, 1, 48, 1, true); beamGeo.translate(0, 0.5, 0);
function makeBeam(color) {
  const m = new THREE.Mesh(beamGeo, new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
    uniforms: { uC: { value: new THREE.Color(color) }, uA: { value: 0 }, uT: { value: 0 } },
    vertexShader: `varying vec2 vUv; varying vec3 vN; varying vec3 vV; void main(){ vUv = uv; vN = normalize(normalMatrix * normal); vec4 mv = modelViewMatrix * vec4(position, 1.0); vV = normalize(-mv.xyz); gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `uniform vec3 uC; uniform float uA, uT; varying vec2 vUv; varying vec3 vN; varying vec3 vV;
      void main(){ float e = 1.0 - abs(dot(normalize(vN), normalize(vV))); float y = vUv.y;
        float a = (0.06 + 0.94 * pow(e, 2.2)) * pow(1.0 - y, 1.3) * uA * 0.6;
        a *= 0.8 + 0.2 * sin(y * 40.0 - uT * 12.0);
        gl_FragColor = vec4(uC * a * 2.2, a); }`,
  }));
  m.visible = false;
  scene.add(m);
  return m;
}

// lightning between the bet cards
const zap = new HG.Holo(7.0, 1.1, 1400, (g, w, h, st) => {
  if (!st.on) return;
  for (let pass = 0; pass < 2; pass++) {
    g.beginPath();
    let x = 0, y = h / 2;
    g.moveTo(x, y);
    for (let k = 1; k <= 18; k++) {
      x = (k / 18) * w;
      y = h / 2 + (k === 18 ? 0 : (R(st.seed, k + pass * 31) - 0.5) * h * 0.75);
      g.lineTo(x, y);
    }
    g.strokeStyle = pass ? '#ffffff' : PINK;
    g.lineWidth = pass ? 2.5 : 7; g.shadowColor = PINK; g.shadowBlur = 22; g.lineJoin = 'round';
    g.stroke();
  }
}, { scale: 1.6, blending: THREE.AdditiveBlending });
scene.add(zap.mesh);

// ---------------------------------------------------------------- characters
const hero = createCharacter({ idBase: 0 });
hero.visor.material.envMapIntensity = 0.22;
scene.add(hero.root);
const VCOL = [CYAN, PINK, '#b98aff', '#7dffb2', '#ffd36b'];
const VS = 0.6;
const VPOS = [-72, -36, 0, 36, 72].map((a, i) => {
  const r = a * Math.PI / 180;
  const rad = i === 2 ? 6.4 : 5.6;
  return { p: V(Math.sin(r) * rad, [0.7, 1.8, 3.35, 1.8, 0.7][i], -Math.cos(r) * rad + 0.6), yaw: -r * 0.55 };
});
const ANS = [
  { visor: 'YES', card: 'YES — light rain' },
  { visor: '4mm', card: 'Rained · 4 mm' },
  { visor: 'TRUE', card: 'TRUE' },
  { visor: 'YES', card: 'Yes, it rained' },
  { visor: 'RAIN', card: 'Precipitation: yes' },
];
const vals = VCOL.map((c, i) => {
  const v = createCharacter({ led: c, detail: 0.6, idBase: 30 * (i + 1) });
  v.root.scale.setScalar(VS);
  v.visor.material.envMapIntensity = 0.22;
  scene.add(v.root);
  return v;
});
// hover platforms
const platGeo = new THREE.TorusGeometry(0.95, 0.05, 12, 64); platGeo.rotateX(Math.PI / 2);
const discGeo = new THREE.CircleGeometry(0.92, 64); discGeo.rotateX(-Math.PI / 2);
const plats = VPOS.map((v, i) => {
  const g = new THREE.Group();
  g.position.copy(v.p);
  const ring = new THREE.Mesh(platGeo, new THREE.MeshBasicMaterial({ color: new THREE.Color(VCOL[i]).multiplyScalar(2.2), transparent: true }));
  const disc = new THREE.Mesh(discGeo, new THREE.MeshBasicMaterial({ color: new THREE.Color(VCOL[i]).multiplyScalar(0.35), transparent: true, opacity: 0.6, blending: THREE.AdditiveBlending, depthWrite: false }));
  g.add(ring, disc);
  g.userData = { ring, disc };
  scene.add(g);
  return g;
});
const vBeams = VCOL.map((c) => makeBeam(c));
const skyBeam = makeBeam(PINK);

// ---------------------------------------------------------------- holograms
const alice = new HG.Holo(3.4, 1.6, 900, HG.drawBetCard);
const bob = new HG.Holo(3.4, 1.6, 900, HG.drawBetCard);
const potTag = new HG.Holo(1.6, 0.45, 440, HG.drawTag);
const contract = new HG.Holo(4.7, 3.4, 1080, HG.drawContract);
const webs = [0, 1, 2].map(() => new HG.Holo(2.75, 1.79, 600, HG.drawWeb));
const labels = vals.map(() => new HG.Holo(2.4, 0.71, 480, HG.drawLabel));
const answers = vals.map(() => new HG.Holo(2.75, 1.02, 640, HG.drawLabel));
const verdict = new HG.Holo(3.6, 1.4, 900, HG.drawVerdict);
const stamp = new HG.Holo(2.35, 0.8, 700, HG.drawStamp, { scale: 1.6 });
for (const h of [alice, bob, potTag, contract, ...webs, ...labels, ...answers, verdict, stamp]) scene.add(h.mesh);
alice.update({ who: 'ALICE', msg: 'It rained in Paris on Friday.', icon: 'rain', chip: 'BETS 100 ON RAIN' });
bob.update({ who: 'BOB', msg: 'Nope. Not a single drop.', icon: 'sun', chip: 'BETS 100 ON NO RAIN' });
potTag.update({ text: 'POT · 200', size: 34 });
webs.forEach((w, k) => w.update({ kind: k }));
labels.forEach((l, i) => l.update({ col: VCOL[i], title: `VALIDATOR ${i + 1}`, sub: `LLM ${'ABCDE'[i]}` }));
answers.forEach((a, i) => a.update({ col: VCOL[i], big: ANS[i].card, sub: `Validator ${i + 1} · LLM ${'ABCDE'[i]}` }));
verdict.update({});
stamp.update({});

// coin
const coinTex = (() => {
  const c = document.createElement('canvas'); c.width = c.height = 512;
  HG.drawCoinFace(c.getContext('2d'), 512);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
})();
const coinGeo = new THREE.CylinderGeometry(0.55, 0.55, 0.12, 64); coinGeo.rotateX(Math.PI / 2);
const coin = new THREE.Mesh(coinGeo, [
  new THREE.MeshPhysicalMaterial({ color: '#c43be0', metalness: 0.6, roughness: 0.3, clearcoat: 1 }),
  new THREE.MeshStandardMaterial({ map: coinTex, emissive: '#ffffff', emissiveMap: coinTex, emissiveIntensity: 0.25, roughness: 0.35 }),
  new THREE.MeshStandardMaterial({ map: coinTex, emissive: '#ffffff', emissiveMap: coinTex, emissiveIntensity: 0.25, roughness: 0.35 }),
]);
coin.userData.lineId = 200;
scene.add(coin);

// 3D logo for the outro: two halves + diamond, extruded
const logo = (() => {
  const g = new THREE.Group();
  const toShape = (pts, s = 1) => new THREE.Shape(pts.map(([u, v]) => new THREE.Vector2(u * s, -v)));
  const ext = { depth: 0.22, bevelEnabled: true, bevelThickness: 0.04, bevelSize: 0.025, bevelSegments: 3 };
  const mat = new THREE.MeshPhysicalMaterial({ color: '#d228e9', emissive: '#7a10a0', emissiveIntensity: 0.6, roughness: 0.25, metalness: 0.2, clearcoat: 1 });
  const parts = [toShape(LOGO_L, 1), toShape(LOGO_L.map(([u, v]) => [-u, v]).reverse(), 1), toShape(LOGO_DIAMOND, 1)].map((sh, i) => {
    const geo = new THREE.ExtrudeGeometry(sh, ext);
    geo.translate(0, 0.5, -0.11);
    const m = new THREE.Mesh(geo, mat);
    m.userData.lineId = 210 + i;
    g.add(m);
    return m;
  });
  g.scale.setScalar(2.2);
  g.userData.parts = parts;
  scene.add(g);
  return g;
})();

// ---------------------------------------------------------------- face glyphs (crop coords, centre ~ (648, 590))
const glyph = {
  text: (str, size = 190, y = 610) => (g) => {
    g.font = `900 ${size}px Inter`; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText(str, 648, y);
  },
  spinner: (t) => (g) => {
    for (let k = 0; k < 8; k++) {
      const a = k / 8 * Math.PI * 2 + t * 6;
      g.globalAlpha = 0.25 + 0.75 * ((k / 8 + t * 0.95) % 1);
      g.beginPath(); g.arc(648 + Math.cos(a) * 110, 600 + Math.sin(a) * 110, 30, 0, Math.PI * 2); g.fill();
    }
    g.globalAlpha = 1;
  },
  proposal: (g) => {
    HG.cloudIcon(g, 648, 520, 1.9, { col: '#fff', drop: '#fff' });
    g.font = '900 150px Inter'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('YES', 648, 760);
  },
};
const faceKeys = new Map();
function setFace(cat, st) {
  const k = JSON.stringify(st, (n, v) => (typeof v === 'function' ? undefined : (typeof v === 'number' ? Math.round(v * 100) / 100 : v)));
  if (faceKeys.get(cat) === k) return;
  faceKeys.set(cat, k);
  cat.face.draw(st);
}
const blinkAt = (t, seed) => { const p = (t + seed * 1.37) % 3.3; return p < 0.16 ? Math.sin(p / 0.16 * Math.PI) : 0; };

// ---------------------------------------------------------------- character motion
const qx = new THREE.Quaternion(), ex = new THREE.Euler();
const addRot = (j, x, y, z) => j.quaternion.multiply(qx.setFromEuler(ex.set(x, y, z)));
function drive(c, t, o) {
  let P = c.poses.rest;
  if (o.hero) P = c.mixPose(P, c.poses.hero, o.hero);
  if (o.cheer) P = c.mixPose(P, c.poses.cheer, o.cheer);
  if (o.think) P = c.mixPose(P, c.poses.think, o.think);
  c.applyPose(P);
  const J = c.J, s = o.seed || 0;
  const br = Math.sin(t * 2.4 + s);
  J.hips.position.y += br * 0.012 + (o.dip || 0);
  addRot(J.torso, 0.025 * br + (o.lean || 0), (o.twist || 0) + 0.03 * Math.sin(t * 0.9 + s), 0.015 * Math.sin(t * 1.3 + s));
  addRot(J.neck, (o.pitch || 0) + 0.03 * Math.sin(t * 1.1 + s * 2), (o.yaw || 0) + 0.05 * Math.sin(t * 0.7 + s), (o.tilt || 0) + 0.04 * Math.sin(t * 0.83 + s));
  const tw = (o.twitch || 0);
  addRot(J.ears.L, -0.25 * tw, 0, 0.18 * tw); addRot(J.ears.R, -0.25 * tw, 0, -0.18 * tw);
  // arm sway on the free arms
  const sw = 0.04 * Math.sin(t * 2.4 + s + 1);
  addRot(J.arms.L.sh, 0, 0, sw * (1 - (o.cheer || 0)));
  if (!o.hero) addRot(J.arms.R.sh, 0, 0, -sw * (1 - (o.cheer || 0)));
  if (o.pawWave) addRot(J.arms.R.wr, 0, 0, o.pawWave);
  const amp = o.tailAmp ?? 1;
  c.tail((p, u) => {
    const w = u * u * amp;
    p.x += Math.sin(t * 2.1 + u * 3.2 + s) * 0.16 * w;
    p.y += Math.sin(t * 1.6 + u * 2.4 + s) * 0.09 * w + (o.tailUp || 0) * u * u;
    p.z += Math.cos(t * 1.8 + u * 2.0 + s) * 0.08 * w;
    return p;
  });
  const sq = o.squash || 0;
  c.root.position.set(o.x || 0, o.y || 0, o.z || 0);
  c.root.rotation.set(0, o.rotY || 0, 0);
  const sc = o.scale ?? 1;
  c.root.scale.set(sc * (1 + sq * 0.5) * (o.sx ?? 1), sc * (1 - sq) * (o.sy ?? 1), sc * (1 + sq * 0.5) * (o.sx ?? 1));
}
const hop = (t, t0, h = 1.3) => { const u = lin(t, t0, t0 + 0.5); return u > 0 && u < 1 ? h * 4 * u * (1 - u) : 0; };
const landSquash = (t, t0, a = 0.2) => { const u = t - t0; return u >= 0 && u < 0.7 ? a * Math.exp(-u * 7) * Math.cos(u * 20) : 0; };

// ---------------------------------------------------------------- camera
const CAM = [
  [0.0, [0, 2.5, 13.2], [0, 2.4, 0], 32],
  [3.9, [0, 2.5, 11.4], [0, 2.5, 0], 32],
  [4.0, [0.6, 1.3, 10.6], [0, 3.6, 0], 36],
  [5.9, [0.2, 1.15, 9.6], [0, 3.1, 0], 36],
  [6.6, [0, 1.3, 9.6], [0, 1.3, 0], 36],
  [7.95, [-2.4, 1.6, 9.0], [0, 1.4, 0], 34],
  [8.05, [-4.2, 3.2, 11.6], [-1.2, 3.1, 0], 36],
  [13.9, [-3.5, 3.1, 10.3], [-1.1, 3.0, 0], 36],
  [14.0, [0, 3.0, 12.0], [0, 2.4, -1.0], 34],
  [15.6, [0, 4.5, 14.2], [0, 2.75, -2.4], 31],
  [21.9, [1.8, 4.2, 13.6], [0, 2.85, -2.4], 31],
  [22.0, [0, 3.6, 12.2], [0, 4.4, -1.0], 33],
  [27.9, [0, 3.4, 10.8], [0, 4.6, -1.0], 33],
  [28.0, [0, 4.0, 13.6], [0, 4.0, -0.8], 34],
  [33.9, [-1.2, 3.6, 12.6], [0, 3.6, -0.8], 34],
  [34.0, [-1.2, 3.6, 12.6], [0, 3.6, -0.8], 34],
  [35.6, [2.3, 2.4, 10.6], [2.35, 2.25, 0], 32],
  [44.5, [2.6, 2.35, 9.6], [2.5, 2.3, 0], 31],
];
// segments shorter than 0.2s are cuts (no easing across them)
function cameraAt(t) {
  let i = 0;
  while (i < CAM.length - 2 && t >= CAM[i + 1][0]) i++;
  const [t0, p0, l0, f0] = CAM[i], [t1, p1, l1, f1] = CAM[i + 1];
  const u = eio(lin(t, t0, t1));
  const P = p0.map((v, k) => mix(v, p1[k], u)), L = l0.map((v, k) => mix(v, l1[k], u));
  return { p: V(...P), l: V(...L), fov: mix(f0, f1, u) };
}

// ---------------------------------------------------------------- frame
const tmpV = new THREE.Vector3();
let lastT = -1;
export function update(t) {
  // camera + shake
  const cam = cameraAt(t);
  const shake = fxSum('shake', t);
  const hand = (k) => 0.02 * Math.sin(t * 0.7 + k) + 0.012 * Math.sin(t * 1.9 + 2 * k);
  cam.p.x += hand(0) + shake * (R(Math.floor(t * 60), 1) - 0.5) * 2;
  cam.p.y += hand(1) + shake * (R(Math.floor(t * 60), 2) - 0.5) * 2;
  camera.position.copy(cam.p);
  camera.fov = cam.fov; camera.updateProjectionMatrix();
  camera.lookAt(cam.l);

  // floor
  floorU.uTime.value = t;
  const beatPhase = (t % TL.BEAT) / TL.BEAT;
  const musicOn = (t > K.land && t < K.fade) ? 1 : 0.35;
  floorU.uBeat.value = Math.exp(-beatPhase * 5) * musicOn;
  const rips = [K.land, K.merge, K.stamp, K.hop + 0.5, K.hop2 + 0.5, ...K.logo].filter((x) => x <= t);
  const lr = rips.length ? rips[rips.length - 1] : -10;
  floorU.uRipT.value = t - lr < 3 ? t - lr : -1;
  floorU.uGlow.value = 0.6 + 0.4 * lin(t, 0, 1.5);
  sky.material.uniforms.uTime.value = t;

  // ---------------- intro: the bet
  const cardsIn = (t0) => back(lin(t, t0, t0 + 0.55), 1.2);
  const exitU = eio(lin(t, K.who, K.who + 0.9));
  const ret = eio(lin(t, K.stamp + 0.2, K.stamp + 0.8));
  const gone = lin(t, K.beamOut, K.beamOut + 0.6);
  const cardVis = (t0) => (t < K.who + 1.2 ? Math.min(1, cardsIn(t0) * 1.5) * (1 - 0.6 * exitU) * (t < K.who + 0.9 ? 1 : 1 - lin(t, K.who + 0.9, K.who + 1.2)) : ret * (1 - gone));
  [[alice, -1, K.aliceIn], [bob, 1, K.bobIn]].forEach(([c, s, t0]) => {
    const inU = cardsIn(t0);
    const jit = t > 1.8 && t < K.who ? 0.05 * Math.sin(t * 40 + s) * fxSum('glitch', t) * 6 : 0;
    let p = V(s * mix(9, 3.7, inU), 2.45 + jit, 0.5);
    p.lerp(V(s * 5.0, 4.0, -1.2), exitU);
    if (t >= K.stamp) p = V(s * 5.1, 4.3, -0.4).lerp(V(s * 5.1, 4.3, -0.4), 0).add(V(0, 0.05 * Math.sin(t * 1.5 + s), 0));
    c.mesh.position.copy(p);
    c.mesh.quaternion.copy(camera.quaternion);
    c.show(cardVis(t0), t);
  });
  // payout state of the cards
  const paid = t >= K.payout + 0.75;
  alice.update({ who: 'ALICE', msg: 'It rained in Paris on Friday.', icon: 'rain', chip: paid ? '+200  WON ✓' : 'BETS 100 ON RAIN', chipCol: paid ? 'rgba(53,225,244,0.9)' : null, chipText: paid ? '#0a0418' : null });
  bob.update({ who: 'BOB', msg: 'Nope. Not a single drop.', icon: 'sun', chip: paid ? 'FAIR ENOUGH — PAID OUT' : 'BETS 100 ON NO RAIN' });

  // coin + pot
  const coinIntro = back(lin(t, 1.3, 1.75));
  const coinOut = lin(t, K.who, K.who + 0.7);
  let coinVis = coinIntro * (1 - coinOut);
  let cp = V(0, 2.75 + 0.08 * Math.sin(t * 2), 0.6).lerp(V(0, 7.5, -3), eio(coinOut));
  let coinSpin = t * 2.2;
  if (t >= K.stamp + 0.2) { // re-appears on the verdict card, then flies to Alice
    const a = back(lin(t, K.stamp + 0.2, K.stamp + 0.5));
    const fly = eio(lin(t, K.payout, K.payout + 0.75));
    const from = V(-1.0, 7.25, -0.5), to = V(-5.1, 4.3, -0.2);
    cp = from.clone().lerp(to, fly); cp.y += Math.sin(fly * Math.PI) * 1.4;
    coinVis = a * (1 - lin(t, K.payout + 0.7, K.payout + 0.85));
    coinSpin = t * 3 + fly * 12;
  }
  coin.visible = coinVis > 0.01;
  coin.position.copy(cp);
  coin.scale.setScalar(Math.max(0.001, coinVis));
  coin.rotation.set(0.15, coinSpin, 0);
  potTag.mesh.position.set(0, 1.75, 0.6);
  potTag.mesh.quaternion.copy(camera.quaternion);
  potTag.show(win(t, 1.45, K.who + 0.3, 0.3, 0.3), t);

  // lightning
  const zt = K.zaps.filter((z) => z <= t).pop();
  const zon = zt !== undefined && t - zt < 0.16 && t < K.who;
  zap.update({ on: zon, seed: zt === undefined ? 0 : Math.floor(zt * 100) + Math.floor((t - zt) * 30) });
  zap.mesh.position.set(0, 2.55, 0.2);
  zap.mesh.quaternion.copy(camera.quaternion);
  zap.show(zon ? 1 : 0, 0);

  // ---------------- hero cat
  const land = K.land, visible = t >= land - 0.55;
  hero.root.visible = visible;
  if (visible) {
    const o = { seed: 0.3, tailAmp: 1 };
    const fallU = lin(t, land - 0.55, land);
    if (t < land) { o.y = mix(10, 0, fallU * fallU); o.cheer = 0.6; o.sy = 1.08; o.sx = 0.95; o.tailUp = 0.4; }
    o.squash = landSquash(t, land, 0.22) + landSquash(t, K.hop + 0.5, 0.14) + landSquash(t, K.hop2 + 0.5, 0.14);
    o.dip = -0.1 * Math.max(0, landSquash(t, land, 1)) ;
    // paw up for the contract, back down for the validators, up again at the end
    o.hero = back(lin(t, K.pawUp, K.pawUp + 0.55), 1.4) * (1 - eio(lin(t, 14.0, 14.6))) + back(lin(t, K.hero, K.hero + 0.6), 1.3);
    o.cheer = (o.cheer || 0) + win(t, K.hop - 0.25, K.hop2 + 0.9, 0.25, 0.4);
    o.y = (o.y || 0) + hop(t, K.hop, 1.4) + hop(t, K.hop2, 1.1);
    // look direction
    let yaw = 0, pitch = 0, tilt = 0;
    if (t > K.pawUp && t < 14.0) { yaw = -0.22 * sm(lin(t, 8.3, 8.8)); pitch = -0.12 * win(t, 9.5, 12.7, 0.4, 0.4); }
    if (t >= 14.0 && t < 22.0) {
      const target = K.spawn.reduce((acc, ts, i) => (t >= ts ? i : acc), -1);
      const ya = target >= 0 ? VPOS[target].yaw * -1.1 : 0;
      yaw = mix(0, clamp(ya, -0.7, 0.7), sm(lin(t, 14.6, 15.2)));
      pitch = -0.15;
      if (t > K.think) { yaw = 0.55 * Math.sin((t - K.think) * 1.1); pitch = -0.2; }
    }
    if (t >= 22.0 && t < 28.0) { pitch = -0.38 * sm(lin(t, 22.0, 22.6)); yaw = 0.25 * Math.sin((t - 22) * 2.2) * (1 - lin(t, 23.2, 23.5)); }
    if (t >= 28.0 && t < 34.0) { pitch = -0.2 * (1 - lin(t, 29, 30)); tilt = 0.12 * Math.sin(t * 3); }
    if (t >= K.boot && t < K.pawUp) tilt = 0.1 * win(t, 6.8, 7.9, 0.3, 0.3);
    o.yaw = yaw; o.pitch = pitch; o.tilt = tilt;
    o.rotY = -0.28 * win(t, K.pawUp - 0.2, 14.2, 0.5, 0.4) + 0.12 * lin(t, K.hero - 0.5, K.hero + 0.6);
    o.twitch = Math.max(0, Math.sin((t - 6.7) * 9)) * win(t, 6.7, 7.3, 0.05, 0.1) + Math.max(0, Math.sin((t - K.unify) * 10)) * win(t, K.unify, K.unify + 0.6, 0.05, 0.2);
    drive(hero, t, o);
    hero.root.updateMatrixWorld(true);
    // face
    const st = { led: CYAN, blink: blinkAt(t, 0.2) };
    if (t < K.boot) Object.assign(st, { bright: 0, off: 1 });
    else if (t < K.boot + 0.3) Object.assign(st, { bright: 0, off: 1 - lin(t, K.boot, K.boot + 0.3) * 0.6, scan: lin(t, K.boot, K.boot + 0.3) });
    else if (t < K.boot + 0.55) Object.assign(st, { expr: 'angry', bright: R(Math.floor(t * 30), 5) > 0.35 ? 1 : 0.15 });
    else if (t < 8.6) Object.assign(st, { expr: 'angry' });
    else if (t < K.propose) Object.assign(st, { expr: 'focus', lookX: -40 * Math.sin(t * 1.3), scan: ((t - 8.6) * 0.7) % 1 });
    else if (t < 14.0) Object.assign(st, { expr: 'none', mouth: 'none', draw: glyph.proposal, gk: 'prop', blink: 0 });
    else if (t < 22.0) Object.assign(st, { expr: 'neutral', lookX: -60 * Math.sin(yaw), lookY: -20 });
    else if (t < K.merge) Object.assign(st, { expr: 'wide', lookY: -40 });
    else if (t < K.stamp) Object.assign(st, { expr: 'happy', blink: 0 });
    else if (t < K.stamp + 0.5) Object.assign(st, { expr: 'wide', mouthOpen: 0.6 });
    else if (t < 34.0) Object.assign(st, { expr: 'happy', blink: 0, mouthOpen: 0.4 });
    else if (t < K.hero + 0.3) Object.assign(st, { expr: 'neutral' });
    else Object.assign(st, { expr: 'angry' });
    setFace(hero, st);
  }
  skyBeam.visible = t > K.beam - 0.2 && t < land + 0.5;
  skyBeam.position.set(0, 0, 0); skyBeam.scale.set(0.95, 30, 0.95);
  skyBeam.material.uniforms.uA.value = sm(lin(t, K.beam - 0.2, land - 0.2)) * (1 - lin(t, land, land + 0.5)) * 0.9;
  skyBeam.material.uniforms.uT.value = t;

  // ---------------- contract + web
  const panelV = sm(lin(t, K.panel, K.panel + 0.45)) * (1 - sm(lin(t, 13.7, 14.1)));
  contract.mesh.position.set(-3.6, 2.85, 0.4);
  contract.mesh.lookAt(V(-4.2, 3.2, 11.6));
  contract.show(panelV, t);
  const chars = Math.floor(HG.CODE_CHARS * lin(t, K.typeStart, K.typeEnd));
  let status = '', statusCol = CYAN;
  if (t > K.web[0]) status = 'Reading the web…';
  if (t > 11.6) { status = 'Asking the AI…'; statusCol = PINK; }
  if (t > K.propose) { status = 'Proposed: YES — it rained'; statusCol = '#7dffb2'; }
  contract.update({ chars, status, statusCol, cursor: t < K.typeEnd + 0.4 && Math.floor(t * 4) % 2 === 0 });
  const WPOS = [V(-6.6, 5.7, -3.6), V(-2.7, 6.15, -4.4), V(1.3, 5.65, -3.8)];
  webs.forEach((w, k) => {
    const a = back(lin(t, K.web[k], K.web[k] + 0.45));
    const vis = Math.min(1, a) * (1 - sm(lin(t, 13.6, 14.0)));
    w.mesh.position.copy(WPOS[k]).add(V(0, 0.08 * Math.sin(t * 1.4 + k), 0));
    w.mesh.lookAt(V(-4.2, 3.2, 11.6));
    w.show(vis, t + k);
  });
  // data streams: web windows -> paw
  const paw = hero.J.arms.R.hand.getWorldPosition(tmpV).clone();
  const sOn = win(t, K.web[2] + 0.1, K.streamEnd, 0.3, 0.4);
  const pa = streams.geometry.attributes;
  for (let i = 0; i < 300; i++) {
    const k = i % 3, ph = (R(i, 3) + t * (0.55 + 0.35 * R(i, 4))) % 1;
    const a = WPOS[k], b = paw;
    const c = a.clone().lerp(b, 0.5).add(V(0, 1.6 + R(i, 5), 0.6));
    const u = ph, iu = 1 - u;
    const x = iu * iu * a.x + 2 * iu * u * c.x + u * u * b.x + (R(i, 6) - 0.5) * 0.25 * iu;
    const y = iu * iu * a.y + 2 * iu * u * c.y + u * u * b.y + (R(i, 7) - 0.5) * 0.25 * iu;
    const z = iu * iu * a.z + 2 * iu * u * c.z + u * u * b.z;
    setPt(streams, i, x, y, z, PAL[[2, 2, 0, 3][i % 4]], 0.08 + 0.06 * R(i, 8), sOn * Math.sin(u * Math.PI) * 1.6);
  }
  flushPts(streams);
  streams.visible = sOn > 0;

  // ---------------- validators
  vals.forEach((v, i) => {
    const ts = K.spawn[i], tOut = K.beamOut + i * 0.12;
    const on = t >= ts - 0.02 && t < tOut + 0.55;
    v.root.visible = on;
    plats[i].visible = t >= ts - 0.3 && t < tOut + 0.55;
    const pv = sm(lin(t, ts - 0.3, ts + 0.1)) * (1 - lin(t, tOut, tOut + 0.5));
    plats[i].userData.ring.material.opacity = pv; plats[i].userData.disc.material.opacity = pv * 0.6;
    plats[i].position.copy(VPOS[i].p).add(V(0, 0.06 * Math.sin(t * 1.5 + i), 0));
    plats[i].scale.setScalar(0.6 + 0.4 * pv);
    const b = vBeams[i];
    const bA = Math.max(win(t, ts - 0.35, ts + 0.45, 0.15, 0.35), win(t, tOut - 0.05, tOut + 0.6, 0.1, 0.35));
    b.visible = bA > 0.01;
    b.position.copy(VPOS[i].p); b.scale.set(0.62, 18, 0.62);
    b.material.uniforms.uA.value = bA; b.material.uniforms.uT.value = t;
    if (!on) { labels[i].show(0); answers[i].show(0); return; }
    const pop = back(lin(t, ts, ts + 0.4), 2.0);
    const outU = lin(t, tOut, tOut + 0.5);
    const o = { seed: i * 1.7 + 1, x: VPOS[i].p.x, y: plats[i].position.y, z: VPOS[i].p.z, rotY: VPOS[i].yaw, scale: VS * Math.max(0.001, pop), tailAmp: 1.2 };
    o.sx = 1 - outU * 0.9; o.sy = 1 + outU * 2.2;
    o.yaw = 0.25 * Math.sin(t * 0.8 + i);
    o.pitch = t > K.fly && t < K.stamp ? -0.3 : 0;
    o.think = win(t, K.think - 0.1, K.answers[i] + 0.1, 0.3, 0.25) * 0.85;
    o.cheer = win(t, K.hop - 0.2 + i * 0.06, K.hop2 + 0.9, 0.25, 0.4);
    o.y += hop(t, K.hop + i * 0.06, 0.8) + hop(t, K.hop2 + i * 0.06, 0.7);
    o.squash = landSquash(t, ts + 0.12, 0.15);
    o.twitch = Math.max(0, Math.sin((t - K.answers[i]) * 10)) * win(t, K.answers[i], K.answers[i] + 0.5, 0.02, 0.2);
    drive(v, t, o);
    const led = t < K.unify ? VCOL[i] : CYAN;
    v.setLed(led);
    const st = { led, blink: blinkAt(t, i * 0.9 + 0.4) };
    if (t < ts + 0.35) Object.assign(st, { expr: 'neutral', scan: lin(t, ts, ts + 0.35), bright: lin(t, ts + 0.1, ts + 0.35) });
    else if (t < K.think) Object.assign(st, { expr: 'neutral', lookX: -50 * Math.sin(t + i) });
    else if (t < K.answers[i]) Object.assign(st, { expr: 'none', mouth: 'none', blink: 0, draw: glyph.spinner(t), sp: Math.floor(t * 30) });
    else if (t < K.fly) Object.assign(st, { expr: 'none', mouth: 'none', blink: 0, draw: glyph.text(ANS[i].visor, ANS[i].visor.length > 3 ? 160 : 200), ans: i });
    else if (t < K.unify) Object.assign(st, { expr: 'neutral', lookY: -45 });
    else Object.assign(st, { expr: 'happy', blink: 0 });
    setFace(v, st);
    // label (under the platform) and answer card (above the head)
    labels[i].mesh.position.copy(plats[i].position).add(V(0, -0.62, 0.25));
    labels[i].mesh.quaternion.copy(camera.quaternion);
    labels[i].show(win(t, ts + 0.3, Math.min(K.fly, tOut), 0.3, 0.3), t + i);
    // answer cards: pop above heads, fly into an orbit, merge
    const above = plats[i].position.clone().add(V(0, 3.9 * VS + 0.75, 0.2));
    const orbitA = (i / 5) * Math.PI * 2 + (t - K.fly) * 1.6;
    const orbit = V(Math.cos(orbitA) * 2.9, 6.0 + Math.sin(orbitA) * 0.9, -0.6 + Math.sin(orbitA) * 0.6);
    const flyU = eio(lin(t, K.fly + i * 0.08, K.fly + 0.9 + i * 0.08));
    const mergeU = ein2(lin(t, K.merge - 0.6, K.merge));
    const ap = above.clone().lerp(orbit, flyU).lerp(V(0, 6.0, -0.6), mergeU);
    answers[i].mesh.position.copy(ap);
    answers[i].mesh.quaternion.copy(camera.quaternion);
    answers[i].mesh.scale.x = 1 - 0.6 * mergeU;
    answers[i].show(back(lin(t, K.answers[i], K.answers[i] + 0.35)) * (t < K.merge ? 1 : 0), t + i);
  });

  // ---------------- verdict
  const vU = back(lin(t, K.merge, K.merge + 0.5), 1.6);
  verdict.mesh.position.set(0, 6.0 + 0.06 * Math.sin(t * 1.3), -0.6);
  verdict.mesh.quaternion.copy(camera.quaternion);
  verdict.mesh.scale.x = Math.max(0.001, vU);
  verdict.show(Math.min(1, vU) * (1 - gone), t);
  const sU = lin(t, K.stamp - 0.18, K.stamp);
  stamp.mesh.position.set(1.5, 5.28, -0.3);
  stamp.mesh.quaternion.copy(camera.quaternion);
  stamp.mesh.rotateZ(0.2);
  const sScale = t < K.stamp ? mix(3.2, 1, sU * sU) : 1 + 0.08 * Math.exp(-(t - K.stamp) * 10);
  stamp.mesh.scale.set(sScale, sScale, 1);
  stamp.show(t >= K.stamp - 0.18 ? Math.min(1, sU * 2) * (1 - gone) : 0, 0);

  // ---------------- outro logo
  const lp = V(4.45, 3.35, 0.2);
  logo.position.copy(lp);
  logo.rotation.set(0, -0.28 + 0.05 * Math.sin(t * 0.8), 0);
  const parts = logo.userData.parts;
  const offs = [V(-6, 0, 0), V(6, 0, 0), V(0, -5, 2)];
  parts.forEach((m, k) => {
    const u = lin(t, K.logo[k] - 0.35, K.logo[k]);
    m.visible = t > K.logo[k] - 0.35 && t < K.fade + 2;
    m.position.copy(offs[k]).multiplyScalar(Math.pow(1 - u, 2) / 2.2);
    m.rotation.set(0, (1 - u) * 2.5 * (k === 1 ? -1 : 1), (1 - u) * (k === 2 ? 3 : 0));
    const pulse = 1 + 0.12 * Math.exp(-(t - K.logo[k]) * 8) * (t > K.logo[k] ? 1 : 0);
    m.scale.setScalar(pulse);
  });

  // ---------------- particles
  {
    for (let i = 0; i < 420; i++) {
      const x = (R(i, 1) - 0.5) * 34, z = -18 + R(i, 2) * 26;
      const y = ((R(i, 3) * 11 + t * (0.12 + 0.2 * R(i, 4))) % 11);
      setPt(dust, i, x + Math.sin(t * 0.3 + i) * 0.4, y, z, PAL[i % 3], 0.05 + 0.07 * R(i, 5), (0.25 + 0.5 * R(i, 6)) * Math.sin((y / 11) * Math.PI) * (0.7 + 0.3 * Math.sin(t * 2 + i)));
    }
    flushPts(dust);
    // landing / stamp dust bursts
    const bursts = [[K.land, V(0, 0.05, 0), 1], [K.stamp, V(0, 6.0, -0.6), 0.9]];
    let used = 0;
    for (const [bt, c, s] of bursts) {
      const u = t - bt;
      for (let j = 0; j < 80; j++, used++) {
        if (u < 0 || u > 1.4) { setPt(burst, used, 0, -10, 0, PAL[0], 0, 0); continue; }
        const a = R(j, 11 + bt) * Math.PI * 2, sp = (2.5 + 4 * R(j, 12 + bt)) * s;
        const e = 1 - Math.exp(-u * 4);
        const x = c.x + Math.cos(a) * sp * e * 0.9, z = c.z + Math.sin(a) * sp * e * 0.9;
        const y = c.y + (bt === K.land ? (0.2 + 1.6 * R(j, 13)) * e * (1 - u * 0.6) : (R(j, 14) - 0.5) * 3 * e);
        setPt(burst, used, x, y, z, PAL[j % 4], 0.1 + 0.12 * R(j, 15), (1 - u / 1.4) * 1.4);
      }
    }
    flushPts(burst);
  }
  // confetti
  {
    const ct = t - (K.hop + 0.5);
    confetti.visible = ct > 0 && ct < 5;
    const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler();
    for (let i = 0; i < CONF; i++) {
      const a = R(i, 21) * Math.PI * 2, sp = 2 + 5 * R(i, 22), up = 6 + 6 * R(i, 23);
      const u = Math.max(0, ct);
      const drag = (1 - Math.exp(-u * 1.6)) / 1.6;
      const x = Math.cos(a) * sp * drag + Math.sin(u * 3 + i) * 0.25;
      const z = Math.sin(a) * sp * drag * 0.6 - 0.5;
      const y = 3.5 + up * drag - 2.2 * u * u * 0.5 - 0.6 * u;
      e.set(u * (4 + 6 * R(i, 24)), u * (3 + 5 * R(i, 25)), u * 2);
      q.setFromEuler(e);
      const s = y < 0 ? 0 : 1;
      m4.compose(V(x, y, z), q, V(s, s, s));
      confetti.setMatrixAt(i, m4);
    }
    confetti.instanceMatrix.needsUpdate = true;
  }

  // contact shadows (x, z, radius)
  const sh = floorU.uShadows.value;
  sh[0].set(hero.root.position.x, hero.root.position.z, hero.root.visible ? 0.95 / (1 + hero.root.position.y * 0.25) : 0);
  for (let i = 0; i < 5; i++) sh[i + 1].set(0, 0, 0);

  // grade
  pipe.grade.uniforms.time.value = t;
  pipe.grade.uniforms.fade.value = Math.max(1 - lin(t, 0, 0.5), lin(t, K.fade, K.fade + 1.6));
  pipe.grade.uniforms.ca.value = 0.0018 + fxSum('glitch', t) * 0.01;
  lastT = t;
}
function ein2(x) { return x * x; }

// ---------------------------------------------------------------- 2D overlay
function caption(t) {
  for (const [t0, t1, text, tag, style] of TL.CAP) {
    if (t < t0 || t > t1) continue;
    const outU = sm(lin(t, t1 - 0.32, t1));
    octx.save();
    let size = 74;
    octx.font = `900 ${size}px Inter`;
    const full = octx.measureText(text).width;
    if (full > 1500) { size = Math.floor(size * 1500 / full); octx.font = `900 ${size}px Inter`; }
    const words = text.split(' ');
    const space = octx.measureText(' ').width;
    const widths = words.map((w) => octx.measureText(w).width);
    const total = widths.reduce((a, b) => a + b, 0) + space * (words.length - 1);
    let x = W / 2 - total / 2;
    const y = 958;
    octx.textBaseline = 'alphabetic';
    // soft backing gradient for legibility
    const bg = octx.createLinearGradient(0, 760, 0, H);
    bg.addColorStop(0, 'rgba(10,2,18,0)'); bg.addColorStop(1, `rgba(10,2,18,${0.55 * (1 - outU)})`);
    octx.fillStyle = bg; octx.fillRect(0, 760, W, H - 760);
    if (tag) {
      const tu = eout(lin(t, t0, t0 + 0.4)) * (1 - outU);
      octx.globalAlpha = tu;
      octx.font = '800 24px Inter';
      octx.letterSpacing = '7px';
      const tw = octx.measureText(tag).width;
      octx.fillStyle = PINK;
      octx.fillText(tag, W / 2 - tw / 2 + 3.5, y - size - 18);
      octx.fillRect(W / 2 - tw / 2 - 70, y - size - 27, 46 * tu, 3);
      octx.fillRect(W / 2 + tw / 2 + 24 + 46 * (1 - tu), y - size - 27, 46 * tu, 3);
      octx.letterSpacing = '0px';
      octx.font = `900 ${size}px Inter`;
    }
    words.forEach((w, i) => {
      const u = eout(lin(t, t0 + i * 0.055, t0 + i * 0.055 + 0.38));
      const a = u * (1 - outU);
      if (a <= 0.001) { x += widths[i] + space; return; }
      octx.globalAlpha = a;
      const dy = (1 - u) * 34 - outU * 10;
      const blur = (1 - u) * 10 + outU * 6;
      octx.filter = blur > 0.3 ? `blur(${blur.toFixed(1)}px)` : 'none';
      const gr = octx.createLinearGradient(0, y - size, 0, y + 10);
      gr.addColorStop(0, '#ffffff'); gr.addColorStop(1, '#ffc6ff');
      octx.shadowColor = 'rgba(220,0,255,0.65)'; octx.shadowBlur = 28;
      if (style === 'glitch' && (fxSum('glitch', t) > 0.05 || R(Math.floor(t * 24), i) > 0.82)) {
        const o = 6 + 10 * fxSum('glitch', t);
        octx.fillStyle = 'rgba(53,225,244,0.8)'; octx.fillText(w, x - o, y + dy);
        octx.fillStyle = 'rgba(255,60,200,0.8)'; octx.fillText(w, x + o, y + dy);
      }
      octx.fillStyle = gr;
      octx.fillText(w, x, y + dy);
      x += widths[i] + space;
    });
    octx.restore();
  }
}
function outroText(t) {
  const anchor = V(4.45, 1.55, 0.2).project(camera);
  const cx = (anchor.x * 0.5 + 0.5) * W, cy = (-anchor.y * 0.5 + 0.5) * H;
  const fadeAll = 1 - lin(t, K.fade, K.fade + 1.4);
  const line = (t0, text, font, y, col, spacing = '0px') => {
    const u = eout(lin(t, t0, t0 + 0.6));
    if (u <= 0) return;
    octx.save();
    octx.globalAlpha = u * fadeAll;
    octx.font = font; octx.letterSpacing = spacing; octx.textAlign = 'center'; octx.textBaseline = 'middle';
    octx.filter = u < 0.98 ? `blur(${((1 - u) * 12).toFixed(1)}px)` : 'none';
    octx.shadowColor = 'rgba(220,0,255,0.7)'; octx.shadowBlur = 30;
    octx.fillStyle = col;
    octx.fillText(text, cx, cy + y + (1 - u) * 26);
    octx.restore();
  };
  line(K.title, 'GenLayer', '900 128px Inter', 0, '#ffffff', '-2px');
  line(K.sub, 'Intelligent Contracts that read the web & reason with AI', '600 32px Inter', 92, '#f2d9ff');
  line(K.url, 'genlayer.com', '800 34px Inter', 152, PINK, '4px');
}

export function render(t) {
  update(t);
  pipe.render();
  octx.globalAlpha = 1; octx.filter = 'none';
  octx.drawImage(glCanvas, 0, 0);
  // glitch slices
  const g = fxSum('glitch', t);
  if (g > 0.02) {
    const f = Math.floor(t * 60);
    for (let k = 0; k < 7; k++) {
      const y = R(f, k) * H, hh = 8 + R(f, k + 20) * 70, dx = (R(f, k + 40) - 0.5) * 120 * g;
      octx.drawImage(out, 0, y, W, hh, dx, y, W, hh);
      octx.fillStyle = `rgba(${k % 2 ? '255,60,220' : '53,225,244'},${0.12 * g})`;
      octx.fillRect(0, y, W, hh);
    }
  }
  caption(t);
  if (t > K.title - 0.2) outroText(t);
  const fl = fxSum('flash', t, 'a');
  if (fl > 0) { octx.fillStyle = `rgba(255,236,255,${Math.min(0.9, fl)})`; octx.fillRect(0, 0, W, H); }
  const blackFade = Math.max(1 - lin(t, 0, 0.45), lin(t, K.fade + 0.6, K.fade + 1.8));
  if (blackFade > 0) { octx.fillStyle = `rgba(0,0,0,${blackFade})`; octx.fillRect(0, 0, W, H); }
}

export async function ready() {
  await Promise.all(['900 74px Inter', '800 24px Inter', '700 30px Inter', '600 22px Inter', '30px "DejaVu Sans Mono"'].map((f) => document.fonts.load(f)));
  // repaint holograms now that fonts exist
  for (const h of [alice, bob, potTag, contract, ...webs, ...labels, ...answers, verdict, stamp]) h.key = null;
  alice.update({ who: 'ALICE', msg: 'It rained in Paris on Friday.', icon: 'rain', chip: 'BETS 100 ON RAIN' });
  bob.update({ who: 'BOB', msg: 'Nope. Not a single drop.', icon: 'sun', chip: 'BETS 100 ON NO RAIN' });
  potTag.update({ text: 'POT · 200', size: 34 });
  webs.forEach((w, k) => w.update({ kind: k }));
  labels.forEach((l, i) => l.update({ col: VCOL[i], title: `VALIDATOR ${i + 1}`, sub: `LLM ${'ABCDE'[i]}` }));
  answers.forEach((a, i) => a.update({ col: VCOL[i], big: ANS[i].card, sub: `Validator ${i + 1} · LLM ${'ABCDE'[i]}` }));
  verdict.update({}); stamp.update({});
  faceKeys.clear();
  // compile everything once
  for (const tt of [0.6, 6.2, 9, 16, 18, 24, 29, 31, 37]) render(tt);
}
export const DURATION = TL.DURATION;

// timing breakdown for one frame (ms), used by tools/profile.js
export function profile(t) {
  const gl = renderer.getContext();
  const px = new Uint8Array(4);
  const now = () => { gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); return performance.now(); };
  const T0 = now();
  update(t);
  const T1 = now();
  const comp = pipe.composer.render.bind(pipe.composer);
  let idMs = 0;
  pipe.composer.render = () => { idMs = now() - T1; comp(); };
  pipe.render();
  const T2 = now();
  pipe.composer.render = comp;
  const passes = {};
  for (const p of pipe.composer.passes) {
    const r = p.render.bind(p);
    p.render = (...a) => { const s0 = now(); r(...a); passes[p.constructor.name + (p.uniforms && p.uniforms.tId ? ':ink' : '')] = Math.round(now() - s0); };
  }
  const T3 = now();
  pipe.render();
  const T4 = now();
  for (const p of pipe.composer.passes) delete p.render;
  octx.drawImage(glCanvas, 0, 0);
  const T5 = now();
  out.toDataURL('image/jpeg', 0.95);
  const T6 = now();
  return { update: T1 - T0, idPass: idMs, pipeline: T2 - T1, again: T4 - T3, draw2d: T5 - T4, jpeg: T6 - T5, passes };
}
