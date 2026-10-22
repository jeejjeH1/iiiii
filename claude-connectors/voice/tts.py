# Synthesizes every voiceover phrase with Piper (local neural TTS), then trims the
# silence, cleans it up and resamples to 48 kHz with ffmpeg. audio.js places each
# phrase on its beat.
# Spellings in pronounce.json only steer the pronunciation; on-screen text is unchanged.
# usage: python tts.py <voice.onnx> <out_dir> [length_scale]   (tested with fa_IR-ganji-medium)
import json, subprocess, sys, wave
from pathlib import Path
from piper import PiperVoice, SynthesisConfig

model, out = sys.argv[1], Path(sys.argv[2])
scale = float(sys.argv[3]) if len(sys.argv) > 3 else 0.8
out.mkdir(parents=True, exist_ok=True)
voice = PiperVoice.load(model)
PRON = json.load(open(Path(__file__).with_name('pronounce.json'), encoding='utf-8'))
TRIM = 'silenceremove=start_periods=1:start_threshold=-42dB'
CHAIN = ','.join([TRIM, 'areverse', TRIM, 'areverse', 'highpass=f=90',
                  'equalizer=f=3200:t=q:w=1.2:g=3', 'acompressor=threshold=-20dB:ratio=3:attack=4:release=90:makeup=2'])
for line in json.load(open(Path(__file__).with_name('lines.json'), encoding='utf-8')):
    for i, seg in enumerate(line['segs']):
        raw = out / f"{line['scene']}_{i}.raw.wav"
        cfg = SynthesisConfig(length_scale=seg.get('scale', scale), noise_scale=0.6, noise_w_scale=0.5)
        with wave.open(str(raw), 'wb') as w:
            say = seg['text']
            for a, b in PRON.items():
                say = say.replace(a, b)
            voice.synthesize_wav(say, w, syn_config=cfg)
        dst = out / f"{line['scene']}_{i}.wav"
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(raw), '-af', CHAIN,
                        '-ar', '48000', '-ac', '1', '-c:a', 'pcm_s16le', str(dst)], check=True)
        with wave.open(str(dst)) as w:
            print(f"{dst.name:10s} {w.getnframes() / w.getframerate():.2f}s  {seg['text']}")
