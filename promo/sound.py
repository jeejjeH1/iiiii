# Soundtrack for the promo: a 128 BPM beat, hits, risers and whooshes cut to the video's timeline,
# crowd roar for the goal, and the narration (promo/voice/*.mp3) on top with the music ducked under it.
# Usage: python3 promo/sound.py <voice-dir> <out.wav>
import numpy as np, subprocess, sys, wave

SR = 44100
DUR = 22.5
GOAL, END = 13.1, 16.2
FEATURES = [4.3, 6.05, 7.8, 9.55, 11.3]
VOICE = [('v1', 2.6), ('v2', 4.6), ('goal', 13.15), ('v3', 14.3), ('v4', 16.9), ('v5', 17.9)]
rng = np.random.default_rng(3)
N = int(SR * DUR)
music = np.zeros((N, 2))

def at(buf, t, sig, pan=0.0, gain=1.0):
    s = int(t * SR)
    if s >= len(buf): return
    sig = sig[: len(buf) - s]
    if sig.ndim == 1:
        l, r = np.sqrt((1 - pan) / 2), np.sqrt((1 + pan) / 2)
        buf[s:s + len(sig), 0] += sig * l * gain; buf[s:s + len(sig), 1] += sig * r * gain
    else:
        buf[s:s + len(sig)] += sig * gain

def tt(d): return np.arange(int(SR * d)) / SR

def band(x, lo, hi):
    f = np.fft.rfft(x); fr = np.fft.rfftfreq(len(x), 1 / SR); f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, len(x))

def kick(g=1.0):
    t = tt(0.4); f = 45 + 110 * np.exp(-t * 28)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7) * g

def clap():
    t = tt(0.25); n = band(rng.standard_normal(len(t)), 900, 6000)
    e = np.exp(-t * 18) * (1 + .6 * (np.sin(2 * np.pi * 90 * t) > .9))
    return n * e * .6

def hat(open_=False):
    t = tt(0.25 if open_ else 0.06); return band(rng.standard_normal(len(t)), 7000, 16000) * np.exp(-t * (14 if open_ else 60)) * .35

def saw(f, t): return 2 * ((t * f) % 1) - 1

def boom(d=2.2):
    """Cinematic hit: falling sub, noise crack and a long tail."""
    t = tt(d); f = 30 + 90 * np.exp(-t * 6)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.6)
    crack = band(rng.standard_normal(len(t)), 200, 9000) * np.exp(-t * 9) * .7
    tail = band(rng.standard_normal(len(t)), 80, 1500) * np.exp(-t * 2.2) * .25
    return sub * 1.2 + crack + tail

def riser(d):
    t = tt(d); n = rng.standard_normal(len(t)); out = np.zeros(len(t))
    for k in range(8):  # sweep the band upwards in steps
        a, b = int(len(t) * k / 8), int(len(t) * (k + 1) / 8)
        lo = 300 * 2 ** (k * .6); out[a:b] = band(n, lo, lo * 4)[a:b]
    tone = np.sin(2 * np.pi * np.cumsum(200 + 900 * (t / d) ** 2) / SR) * .25
    return (out + tone) * (t / d) ** 2

def whoosh(d=0.45):
    t = tt(d); n = rng.standard_normal(len(t)); e = np.sin(np.pi * t / d) ** 2
    lo = band(n, 400, 2500); hi = band(n, 2500, 8000)
    mixp = t / d
    return (lo * (1 - mixp) + hi * mixp) * e * .7

def crowd(d):
    t = tt(d); c = band(rng.standard_normal(len(t)), 250, 3000)
    swell = np.clip(t / .5, 0, 1) * np.clip((d - t) / 1.5, 0, 1)
    return c * swell * (0.85 + .15 * np.sin(2 * np.pi * 2.7 * t))

beat = 60 / 128
# chord roots: A minor, F, C, G (one bar each)
ROOTS = [110.0, 87.31, 130.81, 98.0]
CHORDS = [(220.0, 261.63, 329.63), (174.61, 220.0, 261.63), (261.63, 329.63, 392.0), (196.0, 246.94, 293.66)]

def groove(start, stop, full=True):
    t0 = start
    k = 0
    while t0 < stop - 1e-6:
        bar = int(k // 4) % 4
        at(music, t0, kick())
        if k % 2 == 1: at(music, t0, clap(), pan=.0)
        for h in (0, .5): at(music, t0 + h * beat, hat(open_=(h == .5 and k % 4 == 3)), pan=.3 if h else -.3)
        # rolling bass on 8ths, ducked right after the kick
        for h in (.5,):
            tb = tt(beat * .45); f = ROOTS[bar]
            b = (saw(f, tb) * .5 + np.sin(2 * np.pi * f / 2 * tb)) * np.exp(-tb * 4)
            at(music, t0 + h * beat, band(b, 30, 900) * .55)
        if full and k % 4 == 0:  # chord stab at each bar
            tc = tt(beat * 3.5)
            ch = sum(saw(f * (1 + d), tc) for f in CHORDS[bar] for d in (-.004, .004)) / 6
            ch = band(ch, 150, 5000) * np.exp(-tc * 1.2) * .35
            at(music, t0, ch, pan=-.2); at(music, t0 + .01, ch, pan=.2)
        t0 += beat; k += 1

# intro: light flicker hits, a riser into the logo landing
at(music, 0.3, boom(1.2) * .5)
at(music, 0.55, boom(1.0) * .35)
at(music, 0.8, riser(0.8) * .8)
at(music, 1.6, boom(2.6) * 1.1)
# the drop with the title, then the feature montage
at(music, 2.2, boom(1.5) * .6)
groove(2.2, GOAL - .7)
for ft in FEATURES: at(music, ft - .12, whoosh(), pan=(-.5 if FEATURES.index(ft) % 2 else .5))
# into the goal
at(music, GOAL - .9, riser(.9))
at(music, GOAL, boom(3.0) * 1.3)
at(music, GOAL, crowd(3.4) * .55)
# end card: big hit, lighter groove, final hit
at(music, END, boom(2.4))
groove(END + .5, DUR - 2.2, full=True)
at(music, DUR - 2.2, boom(2.2) * .9)

# narration
voice = np.zeros((N, 2))
duck = np.zeros(N)
for name, t0 in VOICE:
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', f'{sys.argv[1]}/{name}.mp3', '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    v = np.frombuffer(raw, np.int16).astype(float) / 32768
    v = v / (np.max(np.abs(v)) + 1e-9) * .9
    s = int(t0 * SR); e = min(N, s + len(v))
    voice[s:e, 0] += v[: e - s]; voice[s:e, 1] += v[: e - s]
    duck[max(0, s - 2000):e] = 1
# smooth the ducking envelope (~80 ms)
k = int(SR * .08); duck = np.convolve(duck, np.ones(k) / k, mode='same')
music *= (1 - .55 * duck)[:, None]

mix = music * .55 + voice * 1.0
# fade out the very end, soft-clip and normalise
fade = np.clip((DUR - np.arange(N) / SR) / .8, 0, 1); mix *= fade[:, None]
mix = np.tanh(mix * 1.2); mix /= np.max(np.abs(mix)) / .95
with wave.open(sys.argv[2], 'w') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((mix * 32767).astype(np.int16).tobytes())
print('ok', DUR)
