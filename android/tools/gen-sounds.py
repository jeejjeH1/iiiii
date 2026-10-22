# Notification sounds, synthesized (no samples, no licences): run from the repo root
#   python3 android/tools/gen-sounds.py   -> android/app/src/main/res/raw/*.ogg
import numpy as np, wave, subprocess, os

SR = 44100
rng = np.random.default_rng(7)
t_ = lambda d: np.arange(int(SR * d)) / SR

def env(n, a=0.01, r=0.2):
    e = np.ones(n); A = int(SR * a); R = int(SR * r)
    if A: e[:A] = np.linspace(0, 1, A)
    if R: e[-R:] *= np.linspace(1, 0, R) ** 2
    return e

def band(x, lo, hi):
    """Band-pass by zeroing FFT bins (good enough for noise colour)."""
    f = np.fft.rfft(x); fr = np.fft.rfftfreq(len(x), 1 / SR)
    f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, len(x))

def saw(freq, t):
    return 2 * ((t * freq) % 1) - 1

def mix(*parts):
    n = max(int(SR * start) + len(p) for start, p in parts)
    out = np.zeros(n)
    for start, p in parts:
        s = int(SR * start); out[s:s + len(p)] += p[: n - s]
    return out

def chime(freq, d=0.6, bright=0.4):
    t = t_(d)
    return (np.sin(2 * np.pi * freq * t) + bright * np.sin(2 * np.pi * freq * 2.01 * t) + 0.15 * np.sin(2 * np.pi * freq * 3.98 * t)) * np.exp(-t * 6)

def whistle(d):
    """Referee whistle: a ~3 kHz tone rattled by the pea inside (fast AM/FM) plus breath noise."""
    t = t_(d)
    rattle = 0.55 + 0.45 * np.sin(2 * np.pi * 34 * t + 2 * np.sin(2 * np.pi * 5 * t))
    tone = np.sin(2 * np.pi * (2950 * t + 40 / (2 * np.pi * 34) * np.sin(2 * np.pi * 34 * t)))
    breath = band(rng.standard_normal(len(t)), 2400, 4200) * 0.25
    return (tone * rattle + breath) * env(len(t), 0.015, 0.05)

def goal():
    d = 3.2; t = t_(d)
    # crowd roar swelling up then easing off
    crowd = band(rng.standard_normal(len(t)), 250, 2600)
    crowd *= np.clip(t / 0.6, 0, 1) ** 1.5 * np.clip((d - t) / 1.2, 0, 1)
    crowd *= 0.9 + 0.1 * np.sin(2 * np.pi * 3.1 * t)
    # stadium horn: Bb major chord on sawtooth voices with a little vibrato
    th = t_(1.5); vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * th)
    horn = sum(saw(f * vib, th) for f in (233.1, 293.7, 349.2, 466.2)) / 4
    horn = band(horn, 120, 3000) * env(len(th), 0.04, 0.35)
    # bright rising arpeggio right at the start
    arp = mix(*[(k * 0.07, chime(f, 0.5)) for k, f in enumerate((784, 988, 1175, 1568))])
    return mix((0, crowd * 0.55), (0.18, horn * 0.75), (0, arp * 0.35))

def var():
    """Goal taken back: two falling 'womp' tones."""
    parts = []
    for k, (f0, f1) in enumerate(((392, 370), (330, 262))):
        d = 0.45 if k == 0 else 0.8; t = t_(d)
        f = np.linspace(f0, f1, len(t)); ph = 2 * np.pi * np.cumsum(f) / SR
        tone = band(saw(1, ph / (2 * np.pi)), 80, 2500) * (1 + 0.3 * np.sin(2 * np.pi * 6 * t))
        parts.append((k * 0.5, tone * env(len(t), 0.02, 0.25)))
    return mix(*parts) * 0.8

def kickoff():
    return mix((0, whistle(0.85)))

def fulltime():
    return mix((0, whistle(0.32)), (0.45, whistle(0.32)), (0.9, whistle(1.1)))

def lineup():
    """Tactics-board chime: three soft rising notes."""
    return mix(*[(k * 0.13, chime(f, 0.7, 0.25)) for k, f in enumerate((659.3, 830.6, 987.8))]) * 0.8

out = 'android/app/src/main/res/raw'
os.makedirs(out, exist_ok=True)
for name, fn in (('goal', goal), ('var_cancel', var), ('kickoff', kickoff), ('fulltime', fulltime), ('lineup', lineup)):
    x = fn(); x = x / np.max(np.abs(x)) * 0.89
    wav = f'/tmp/{name}.wav'
    with wave.open(wav, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', f'{out}/notif_{name}.ogg'], check=True)
    print(name, round(len(x) / SR, 2), 's')
