# python3 audio.py name  → out/name.wav: an original upbeat loop plus sound effects on the video's moments (out/name.json)
import json, sys, wave
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

SR = 44100
name = sys.argv[1]
seed = sum(map(ord, name))
rng = np.random.default_rng(seed)
ev = json.load(open(f'out/{name}.json'))
D = ev['dur']; N = int(D * SR) + SR
OUT = D - 2.8
L = np.zeros(N); R = np.zeros(N)
t_all = np.arange(N) / SR

def filt(x, kind, f, order=2):
    f = np.atleast_1d(f) / (SR / 2)
    return sosfilt(butter(order, f if len(f) > 1 else f[0], btype=kind, output='sos'), x)

def add(sig, at, gain=1.0, pan=0.0):
    i = int(at * SR)
    if i >= N or i + len(sig) <= 0: return
    j = max(0, -i); sig = sig[j:]; i = max(0, i)
    n = min(len(sig), N - i)
    L[i:i + n] += sig[:n] * gain * (1 - max(0, pan)); R[i:i + n] += sig[:n] * gain * (1 + min(0, pan))

def env(n, a, d, sus=0.0):
    e = np.ones(n); na = max(1, int(a * SR)); e[:na] = np.linspace(0, 1, na)
    rest = n - na
    if rest > 0: e[na:] = sus + (1 - sus) * np.exp(-np.arange(rest) / (d * SR))
    return e

def note(m): return 440 * 2 ** ((m - 69) / 12)

def saw(f, n, detune=0.0):
    t = np.arange(n) / SR
    return 2 * ((t * f * (1 + detune)) % 1) - 1

def reverb(x, secs=1.6, mix=0.3):
    n = int(secs * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (secs * SR / 5))
    ir = filt(ir, 'low', 6000); ir /= np.abs(ir).sum() ** .5 * 6
    return x * (1 - mix) + fftconvolve(x, ir)[:len(x)] * mix

# ---------- the music: 112 bpm, Am F C G ----------
BPM = 112; beat = 60 / BPM; bar = beat * 4
start = 1.25                                    # drums come in as the phone lands
chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]
roots = [45, 41, 48, 43]
music_end = OUT + 0.15
kick_times = []

def kick():
    n = int(.45 * SR); t = np.arange(n) / SR
    f = 45 + 95 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 7) + 0.25 * filt(rng.standard_normal(n), 'high', 3000) * np.exp(-t * 120)

def clap():
    n = int(.3 * SR); t = np.arange(n) / SR
    x = filt(rng.standard_normal(n), 'band', [900, 4500])
    e = np.exp(-t * 18); e[:int(.012 * SR)] *= (np.sin(np.linspace(0, 6 * np.pi, int(.012 * SR))) ** 2)
    return x * e

def hat(open_=False):
    n = int((.25 if open_ else .06) * SR); t = np.arange(n) / SR
    return filt(rng.standard_normal(n), 'high', 7000) * np.exp(-t * (12 if open_ else 70))

pad_bus = np.zeros(N); pluck_bus = np.zeros(N); bass_bus = np.zeros(N)

def addbus(bus, x, at):
    i = int(round(at * SR)); j = max(0, -i); x = x[j:]; i = max(0, i)
    n = max(0, min(len(x), N - i)); bus[i:i + n] += x[:n]

# bars are laid so one starts exactly when the drums come in; the bar before it is the intro
b = 0
tb = start - bar
while tb < music_end:
    ci = b % 4
    # pad: from the very start, filtered and swelling
    n = int(bar * SR) + int(.3 * SR)
    pad = sum(saw(note(m), n, d) for m in chords[ci] for d in (-0.006, 0.0, 0.007)) / 9
    pad = filt(pad, 'low', 1400 if tb >= start else 700) * env(n, .25, .9, .7)
    addbus(pad_bus, pad, tb)
    if tb >= start - 0.01:
        for k in range(4):
            bt = tb + k * beat
            if bt >= music_end: break
            add(kick(), bt, .7); kick_times.append(bt)
            if k in (1, 3): add(clap(), bt, .35, .1)
            add(hat(), bt + beat / 2, .16, -.3)
            add(hat(), bt + beat / 4 * 3, .07, .3)
            if k == 3 and b % 2 == 1: add(hat(True), bt + beat / 2, .12, .2)
        # bass: eighth notes on the root
        for k in range(8):
            bt = tb + k * beat / 2
            if bt >= music_end: break
            n2 = int(beat / 2 * SR)
            f = note(roots[ci] - 12 + (12 if k % 4 == 3 else 0))
            x = filt(saw(f, n2) + 0.5 * np.sin(2 * np.pi * f * np.arange(n2) / SR), 'low', 420) * env(n2, .005, .12, .3)
            addbus(bass_bus, x, bt)
        # pluck arpeggio, sixteenths
        arp = [chords[ci][0] + 12, chords[ci][1] + 12, chords[ci][2] + 12, chords[ci][1] + 24]
        for k in range(16):
            bt = tb + k * beat / 4
            if bt >= music_end: break
            if b < 3 and k % 2: continue
            n2 = int(.35 * SR); f = note(arp[k % 4]); t = np.arange(n2) / SR
            x = (np.sin(2 * np.pi * f * t) + .3 * np.sin(4 * np.pi * f * t)) * np.exp(-t * 14)
            addbus(pluck_bus, x * (.9 if k % 4 == 0 else .6), bt)
    tb += bar; b += 1

# side-chain: everything but the drums ducks under each kick
duck = np.ones(N)
for kt in kick_times:
    i = int(kt * SR); n = int(.28 * SR)
    seg_ = 1 - .55 * np.exp(-np.arange(n) / (.07 * SR))
    duck[i:i + n] = np.minimum(duck[i:i + n], seg_[:max(0, min(n, N - i))])
# the music fades under the outro and the pad rings out
fade = np.clip((music_end + 2.4 - t_all) / 2.4, 0, 1)
pad_bus = reverb(pad_bus, 2.2, .35) * duck * np.where(t_all > music_end, fade, 1)
pluck_bus = reverb(pluck_bus, 1.2, .35) * duck
bass_bus = bass_bus * duck
# a stereo delay on the plucks
dl = int(beat * .75 * SR)
pl_l = pluck_bus.copy(); pl_r = pluck_bus.copy(); pl_r[dl:] += pluck_bus[:-dl] * .45; pl_l[2 * dl:] += pluck_bus[:-2 * dl] * .25
L += pad_bus * .36 + bass_bus * .42 + pl_l * .16
R += pad_bus * .36 + bass_bus * .42 + pl_r * .16

# ---------- effects ----------
def whoosh(dur=.55, lo=400, hi=5000, up=True):
    # noise through a few fixed bands, crossfaded from low to high over time (smooth, no clicks)
    n = int(dur * SR); x = rng.standard_normal(n); k = np.linspace(0, 1, n)
    if not up: k = 1 - k
    fs = np.geomspace(lo, hi, 6); out = np.zeros(n)
    for j, f in enumerate(fs):
        w = np.exp(-((k * 5 - j) ** 2) / 1.2)
        out += filt(x, 'band', [f * .7, min(f * 1.4, 20000)], 2) * w
    return out * np.sin(np.linspace(0, np.pi, n)) ** 2

def tap():
    n = int(.08 * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 90) + .4 * filt(rng.standard_normal(n), 'high', 4000) * np.exp(-t * 200)

def impact():
    n = int(1.4 * SR); t = np.arange(n) / SR
    f = 38 + 70 * np.exp(-t * 9)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    return x + .35 * filt(rng.standard_normal(n), 'low', 900) * np.exp(-t * 6)

def ding():
    n = int(1.2 * SR); t = np.arange(n) / SR
    a = np.sin(2 * np.pi * note(88) * t) * np.exp(-t * 4)
    b2 = np.sin(2 * np.pi * note(95) * t) * np.exp(-np.clip(t - .13, 0, None) * 4) * (t > .13)
    return reverb((a + b2 * .9) * .6, 1.0, .3)

def buzz():
    n = int(.32 * SR); t = np.arange(n) / SR
    return filt(np.sign(np.sin(2 * np.pi * 165 * t)), 'low', 600) * (np.sin(np.pi * t / .32)) * .5

def riser(dur=1.3):
    n = int(dur * SR); t = np.arange(n) / SR
    x = whoosh(dur, 300, 9000) * 1.2 + .25 * np.sin(2 * np.pi * (200 + 900 * (t / dur) ** 2) * t)
    return x * (t / dur) ** 1.5

def shimmer():
    out = np.zeros(int(2.5 * SR))
    for k, m in enumerate([81, 84, 88, 93, 96]):
        n = int(2.0 * SR); t = np.arange(n) / SR
        s = np.sin(2 * np.pi * note(m) * t) * np.exp(-t * 2.5)
        i = int(k * .07 * SR); out[i:i + n] += s
    return reverb(out * .35, 2.0, .45)

add(whoosh(1.0, 200, 3000), 0.1, .5)
add(impact(), 1.15, .75)
for s in ev['screens']: add(whoosh(.5, 600, 6000), s - .1, .35, .2)
for l in ev['lenses']: add(whoosh(.45, 1500, 7000), l - .05, .3, -.2)
for tp in ev['taps']: add(tap(), tp, .5)
for d in ev['dings']:
    add(buzz(), d, .5); add(ding(), d + .02, .45)
if ev.get('fan'):
    for k in range(6): add(whoosh(.35, 1200, 8000), ev['fan'] + k * .12, .22, (k - 2.5) / 3)
add(riser(1.3), OUT - 1.25, .4)
add(impact(), OUT + .05, .85)
add(shimmer(), OUT + .15, .7)

# ---------- master ----------
mix = np.stack([L, R], 1)[:int(D * SR)]
mix = filt(mix.T, 'high', 28).T
mix = np.tanh(mix * 1.15) / np.tanh(1.15)
mix /= np.abs(mix).max() / 0.89
fo = int(.4 * SR); mix[-fo:] *= np.linspace(1, 0, fo)[:, None]
with wave.open(f'out/{name}.wav', 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes())
print('wav', name, D)
