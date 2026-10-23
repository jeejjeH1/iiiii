# Soundtrack for promo v2: a 140 BPM F-minor festival track cut to the video's grid (see TIMING in index.html).
# No samples: every drum, synth and effect is synthesized here.  Usage: python3 sound.py out.wav
import numpy as np, sys, wave

SR = 44100
B = 60 / 140; BAR = 4 * B
T = dict(drop1=2 * BAR, logo=3 * BAR, feat=4 * BAR, build=10 * BAR, goal=12 * BAR, outro=14 * BAR, end=16 * BAR)
TOTAL = 16 * BAR + 1.2
N = int(SR * TOTAL)
rng = np.random.default_rng(11)

drums = np.zeros((N, 2)); synths = np.zeros((N, 2)); fx = np.zeros((N, 2)); bass = np.zeros((N, 2))
kicks = []

def tt(d): return np.arange(int(SR * d)) / SR
def midi(m): return 440 * 2 ** ((m - 69) / 12)
def put(buf, t, sig, pan=0., gain=1.):
    s = int(round(t * SR))
    if s >= len(buf) or s + len(sig) <= 0: return
    if s < 0: sig = sig[-s:]; s = 0
    sig = sig[: len(buf) - s]
    if sig.ndim == 1:
        buf[s:s + len(sig), 0] += sig * gain * np.sqrt((1 - pan) / 2) * 1.414
        buf[s:s + len(sig), 1] += sig * gain * np.sqrt((1 + pan) / 2) * 1.414
    else:
        buf[s:s + len(sig)] += sig * gain

def band(x, lo, hi):
    f = np.fft.rfft(x, axis=0); fr = np.fft.rfftfreq(len(x), 1 / SR)
    m = ((fr >= lo) & (fr <= hi)).astype(float)
    return np.fft.irfft(f * (m[:, None] if x.ndim > 1 else m), len(x), axis=0)

def sweep(x, cutoff, hp=False):
    """Low-pass (or high-pass) whose cutoff follows cutoff(time) — STFT overlap-add with a soft edge."""
    mono = x.ndim == 1
    if mono: x = x[:, None]
    n, hop = 2048, 512; win = np.hanning(n)
    out = np.zeros_like(x); norm = np.zeros(len(x))
    fr = np.fft.rfftfreq(n, 1 / SR)
    for s in range(0, len(x) - n, hop):
        c = cutoff((s + n / 2) / SR)
        m = 1 / (1 + (fr / c) ** 4) if not hp else 1 - 1 / (1 + (fr / c) ** 4)
        seg = np.fft.rfft(x[s:s + n] * win[:, None], axis=0) * m[:, None]
        out[s:s + n] += np.fft.irfft(seg, n, axis=0) * win[:, None]; norm[s:s + n] += win ** 2
    out /= np.maximum(norm, 1e-3)[:, None]
    return out[:, 0] if mono else out

def reverb(x, d=2.2, decay=3.2, mix=.3):
    t = tt(d); ir = np.stack([rng.standard_normal(len(t)), rng.standard_normal(len(t))], 1) * np.exp(-t * decay)[:, None]
    ir = band(ir, 200, 9000); ir /= np.sqrt((ir ** 2).sum(0))
    L = len(x) + len(ir); n = 1 << (L - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, n, axis=0) * np.fft.rfft(ir, n, axis=0), n, axis=0)[: len(x)]
    return x * (1 - mix) + wet * mix * 3

# ---------- instruments ----------
def kick(g=1.):
    t = tt(.45); f = 48 + 170 * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6.5)
    click = band(rng.standard_normal(len(t)), 2000, 9000) * np.exp(-t * 250) * .5
    return np.tanh((body + click) * 1.6) * g

def clap(g=1.):
    t = tt(.35); n = band(rng.standard_normal(len(t)), 1000, 8000)
    e = np.zeros(len(t))
    for k, d in enumerate((0, .011, .022)): s = int(d * SR); e[s:] += np.exp(-(t[: len(t) - s]) * (60 if k < 2 else 16))
    tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * .4
    return (n * e * .55 + tone) * g

def hat(open_=False, g=1.):
    t = tt(.3 if open_ else .05)
    return band(rng.standard_normal(len(t)), 8000, 17000) * np.exp(-t * (11 if open_ else 80)) * .32 * g

def snare(g=1.):
    t = tt(.2); return (band(rng.standard_normal(len(t)), 1500, 9000) * np.exp(-t * 28) * .6 + np.sin(2 * np.pi * 220 * t) * np.exp(-t * 35) * .4) * g

def tom(f0=110, g=1.):
    t = tt(.6); f = f0 * (1 + .6 * np.exp(-t * 18))
    return np.tanh(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 5) * 1.8) * g

def impact(d=3., g=1.):
    t = tt(d); f = 28 + 120 * np.exp(-t * 7)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.5) * 1.3
    crack = band(rng.standard_normal(len(t)), 300, 12000) * np.exp(-t * 10) * .9
    tail = band(rng.standard_normal(len(t)), 60, 2000) * np.exp(-t * 1.8) * .3
    return np.tanh((sub + crack + tail) * 1.3) * g

def riser(d, g=1.):
    t = tt(d); n = rng.standard_normal(len(t))
    r = sweep(n, lambda x: 300 + 9000 * (x / d) ** 2) * (t / d) ** 2
    tone = sum(np.sin(2 * np.pi * np.cumsum(220 * 2 ** (3 * (t / d) ** 2) * k) / SR) for k in (1, 1.5, 2)) / 3
    return (r * .8 + tone * .35 * (t / d) ** 3) * g

def downlifter(d=2., g=1.):
    return riser(d, g)[::-1] * np.linspace(1, 0, int(SR * d)) ** .5

def whoosh(d=.5, g=1.):
    t = tt(d); n = rng.standard_normal(len(t)); env = np.sin(np.pi * t / d) ** 3
    return sweep(n, lambda x: 500 + 7000 * np.sin(np.pi * x / d)) * env * .9 * g

def zap(g=1.):
    t = tt(.35); f = 2400 * np.exp(-t * 14) + 80
    return np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * np.exp(-t * 9) * .25 * g

def clunk(g=1.):
    """A stadium floodlight switching on."""
    t = tt(1.2); thud = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-t * 4) + 40) / SR) * np.exp(-t * 9)
    metal = sum(np.sin(2 * np.pi * f * t) for f in (1830, 2470, 3310)) * np.exp(-t * 14) * .15
    hum = np.sin(2 * np.pi * 100 * t) * np.exp(-t * 2.5) * .12
    return (thud + metal + hum) * g

def crowd(d, g=1.):
    t = tt(d); c = np.stack([band(rng.standard_normal(len(t)), 250, 3200) for _ in range(2)], 1)
    sw = np.clip(t / .4, 0, 1) * np.clip((d - t) / 1.6, 0, 1) * (.85 + .15 * np.sin(2 * np.pi * 2.3 * t))
    return c * sw[:, None] * g

def crackle(g=1.):
    t = tt(.9); x = np.zeros(len(t)); r = rng.random(len(t))
    x[r > .995] = rng.standard_normal((r > .995).sum())
    return band(x, 2000, 12000) * np.exp(-t * 3) * 2.5 * g

def supersaw(notes, d, cutoff=None, att=.01, rel=.3, g=1.):
    t = tt(d); L = np.zeros(len(t)); R = np.zeros(len(t))
    for m in notes:
        f = midi(m)
        for k, det in enumerate((-.012, -.007, -.003, 0, .003, .007, .012)):
            ph = rng.random()
            s = 2 * ((t * f * (1 + det) + ph) % 1) - 1
            (L if k % 2 else R)[:] += s; (R if k % 2 else L)[:] += s * .6
    env = np.minimum(1, t / att) * np.clip((d - t) / rel, 0, 1)
    y = np.stack([L, R], 1) * env[:, None] / (7 * len(notes))
    if cutoff: y = sweep(y, cutoff)
    return y * g

def pluck(m, d=.35, g=1.):
    t = tt(d); f = midi(m)
    s = (2 * ((t * f) % 1) - 1) * .6 + np.sign(np.sin(2 * np.pi * f * 1.005 * t)) * .4
    return sweep(s * np.exp(-t * 11), lambda x: 600 + 7000 * np.exp(-x * 14)) * g

def lead(m, d, g=1.):
    t = tt(d); f = midi(m) * (1 + .004 * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / .15, 0, 1))
    ph = np.cumsum(f) / SR
    s = sum((2 * ((ph * (1 + det)) % 1) - 1) for det in (-.006, 0, .006)) / 3
    env = np.minimum(1, t / .008) * np.exp(-t * 2.2) * np.clip((d - t) / .04, 0, 1)
    return sweep(s * env, lambda x: 1500 + 5000 * np.exp(-x * 5)) * g

def bass808(m, d, g=1.):
    t = tt(d); f = midi(m) * (1 + .8 * np.exp(-t * 40))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR)
    env = np.minimum(1, t / .005) * np.clip((d - t) / .05, 0, 1) * np.exp(-t * .9)
    return np.tanh(s * env * 2.4) * .8 * g

# ---------- composition ----------
CHORDS = [(53, 56, 60, 65), (49, 53, 56, 61), (56, 60, 63, 68), (51, 55, 58, 63)]   # Fm Db Ab Eb
ROOTS = [29, 37, 32, 39]
HOOK = [[72, 77, 80, 77, 84, 80, 77, 75], [73, 77, 80, 77, 85, 80, 77, 72], [72, 75, 80, 75, 84, 80, 75, 72], [70, 75, 79, 75, 82, 79, 75, 79]]

def beat_kick(t, g=1.):
    put(drums, t, kick(g)); kicks.append(t)

def groove(start, bars, hats16=False, hook=False, arps=False, chords=True, g_chords=1.):
    for b in range(bars):
        t0 = start + b * BAR; ci = int(round((t0 - T['drop1']) / BAR)) % 4
        for k in range(4):
            beat_kick(t0 + k * B)
            if k % 2: put(drums, t0 + k * B, clap(), gain=.9)
            put(drums, t0 + k * B + B / 2, hat(open_=True, g=.6), pan=.25)
            if hats16:
                for h in (0, .25, .75): put(drums, t0 + k * B + h * B, hat(g=.7), pan=-.3 if h else .1)
        # 808 on the root, pumping on the offbeats
        for k in range(4): put(bass, t0 + k * B + B * .5, bass808(ROOTS[ci], B * .48), gain=.9)
        put(bass, t0, bass808(ROOTS[ci], B * .45), gain=.7)
        if chords: put(synths, t0, supersaw(CHORDS[ci], BAR, cutoff=lambda x: 6000, rel=.1, g=.55 * g_chords))
        if hook:
            for k, m in enumerate(HOOK[ci]): put(synths, t0 + k * B / 2, lead(m, B / 2 * .95, g=.45), pan=.1 * (1 if k % 2 else -1))
        if arps:
            notes = [n + 12 for n in CHORDS[ci]]
            for k in range(16):
                m = notes[k % 4] + (12 if (k // 4) % 2 else 0)
                put(synths, t0 + k * B / 4, pluck(m, g=.32), pan=(-.5 if k % 2 else .5))
                put(synths, t0 + k * B / 4 + 3 * B / 4, pluck(m, g=.12), pan=(.5 if k % 2 else -.5))  # ping-pong echo

# intro: four floodlight banks clunk on, a dark pad, heartbeat kicks, then the build into the drop
for k in range(4): put(fx, k * B / 2, clunk(), pan=(-.6 if k % 2 == 0 else .6), gain=.9)
put(synths, 0, supersaw(CHORDS[0], T['drop1'], cutoff=lambda x: 250 + 2600 * (x / T['drop1']) ** 2, att=.6, g=.7))
for k in range(4): beat_kick(BAR + k * B, g=.45 + k * .12)
for k in range(8): put(drums, BAR + 2 * B + k * B / 4, snare(g=.25 + k * .06), pan=.1)
put(fx, BAR, riser(BAR - .06, g=.9))
for k in range(4): put(fx, BAR + k * B, zap(g=.8), pan=(-.4 if k % 2 else .4))

# drop 1: the ball hits, the title slams, the logo flips in
put(fx, T['drop1'], impact(3.4, g=1.2))
put(fx, T['drop1'] + B, impact(1.2, g=.45))
put(fx, T['drop1'] + B, zap(g=.9))
groove(T['drop1'], 2, hook=True)
put(fx, T['logo'], whoosh(.6, g=.8), pan=-.3)
put(fx, T['logo'] + B * 1.2, impact(1.5, g=.4))
put(fx, T['feat'] - B * .6, whoosh(.6, g=1.), pan=.3)

# features: six bars of groove with 16th hats and arps, a hit and a whoosh on every cut
groove(T['feat'], 6, hats16=True, arps=True, g_chords=.7)
for i in range(6):
    t0 = T['feat'] + i * BAR
    put(fx, t0, impact(1.2, g=.55)); put(fx, t0 - .2, whoosh(.45, g=.8), pan=(.5 if i % 2 else -.5))
    put(fx, t0 + B * .85, zap(g=.7)); put(fx, t0 + B * 3.25, whoosh(.55, g=.6), pan=(-.5 if i % 2 else .5))

# build: drums thin out, snare roll speeds up, everything rises, 3·2·1, half a beat of silence
for k in range(4): beat_kick(T['build'] + k * B, g=.9)
roll = [T['build'] + k * B / 2 for k in range(8)] + [T['build'] + BAR + k * B / 4 for k in range(4)] + [T['build'] + BAR + B + k * B / 8 for k in range(20)]
for k, rt in enumerate(roll):
    if rt < T['goal'] - B * .5: put(drums, rt, snare(g=.3 + .7 * k / len(roll)), pan=.05)
put(fx, T['build'], riser(2 * BAR - B * .5, g=1.1))
put(synths, T['build'], supersaw(CHORDS[3], 2 * BAR - B * .5, cutoff=lambda x: 400 + 7000 * (x / (2 * BAR)) ** 2, att=.3, g=.65))
for k in range(3): put(fx, T['build'] + BAR + B * (k + 1), tom(90 - k * 10, g=1.2)); put(fx, T['build'] + BAR + B * (k + 1), impact(.9, g=.4))

# goal: the biggest hit, the net, the crowd, a stadium horn and fireworks
put(fx, T['goal'], impact(4., g=1.4))
put(fx, T['goal'] + .18, impact(1.6, g=.7))
put(fx, T['goal'] + .1, crowd(4.2, g=.55))
put(synths, T['goal'] + .18, supersaw([65, 68, 72, 77], 1.6, att=.02, rel=.6, g=.9))  # horn
for k in range(6): put(fx, T['goal'] + .18 + k * B * .9, crackle(g=.8), pan=(-.5 + .2 * k))
groove(T['goal'], 2, hats16=True, hook=True)

# outro: the logo lands, the badge flips in, one last hit and a long tail
put(fx, T['outro'], impact(2.2, g=.9))
put(fx, T['outro'] + B * 1.4, impact(1.8, g=.7)); put(fx, T['outro'] + B * .2, whoosh(1., g=.8))
groove(T['outro'], 1, hook=True)
for k in range(4): beat_kick(T['outro'] + BAR + k * B, g=.8)
put(synths, T['outro'] + BAR, supersaw(CHORDS[0], BAR, cutoff=lambda x: 5000, rel=.2, g=.5))
put(fx, T['outro'] + BAR + B, impact(1.6, g=.75)); put(fx, T['outro'] + BAR + B, zap(g=.6))
put(fx, T['end'], impact(4.5, g=1.3))
put(synths, T['end'], supersaw(CHORDS[0], 1.8, att=.005, rel=1.6, g=.8))
put(fx, T['end'] + .05, downlifter(1.6, g=.6))

# ---------- mix ----------
# sidechain: synths and bass duck under each kick
duck = np.ones(N)
for kt in kicks:
    s = int(kt * SR); n = int(.22 * SR); e = s + n
    if s >= N: continue
    curve = 1 - .75 * np.exp(-np.arange(min(n, N - s)) / SR * 22)
    duck[s:s + len(curve)] = np.minimum(duck[s:s + len(curve)], curve)
synths *= duck[:, None]; bass *= duck[:, None]
synths = reverb(synths, 2.0, 3.5, .25)
fx = reverb(fx, 2.6, 2.6, .3)
drums = drums + reverb(drums, 1.0, 7, 1.) * .08
# bass audible on phone speakers: add its upper harmonics
bass = bass * .55 + band(np.tanh(bass * 4), 120, 900) * .35
mix = drums * .8 + bass + synths * 1.05 + fx * .8
mix = band(mix, 30, 18000)
mix += band(mix, 1500, 7000) * .35  # presence
# half a beat of silence right before the goal, so the hit lands harder
s0, s1 = int((T['goal'] - B * .5) * SR), int(T['goal'] * SR)
gate = np.zeros(s1 - s0); f = int(.03 * SR); gate[:f] = np.linspace(1, 0, f)
mix[s0:s1] *= gate[:, None]
mix = np.tanh(mix * 1.15)
mix /= np.max(np.abs(mix)) / .97
with wave.open(sys.argv[1], 'w') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes())
print('ok', round(TOTAL, 2), 's')
