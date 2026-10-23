# ۵ کانکتر Claude — Instagram Reel (9:16)

`claude_connectors_reel.mp4`: 1080×1920, 30fps, 32.4s, H.264 + AAC, -14 LUFS, with a Persian voiceover. Ready to upload as a Reel.
`claude_connectors_reel_sfx_only.mp4`: voiceover + sound effects, no music, for adding a trending track in Instagram.
`claude_connectors_reel_novoice.mp4`: music + sound effects, no voiceover.

A countdown (#5 → #1) of Claude connectors in Claude's look: cream paper, coral `#D97757`, ink black, serif titles.
Each connector tile flies in, a cable plugs it into Claude with an electric zap, then a short chat shows a real use.

Everything is generated from code:

| File | What it does |
|---|---|
| `timeline.js` | Shared 128 BPM timeline: connector copy, scene windows, sound and camera cues |
| `motion.js` | Canvas renderer: hook, number panels, plug-in cable, chat bubbles, how-to, CTA |
| `audio.js` | Synthesizes a future-house track (kick, clap, offbeat bass, supersaw stabs, arp) and every SFX |
| `voice/` | Voiceover: `lines.json` (phrases, each pinned to a beat), `pronounce.json` (spellings that steer pronunciation), `tts.py` (Piper TTS), `vo/` (rendered phrases) |
| `logos.js` | Brand logo paths + colors from [Simple Icons](https://simpleicons.org) (CC0). Logos are trademarks of their owners |
| `render.js` / `build.sh` | Headless Playwright render → ffmpeg, muxes both MP4s |
| `fonts/` | Vazirmatn (Farsi digits), Source Serif 4, JetBrains Mono, all SIL OFL |

## Rebuild

```bash
./build.sh
```

Edit connector text in `timeline.js` (`CONNECTORS`).

### Voiceover

Rendered locally with [Piper](https://github.com/OHF-Voice/piper1-gpl) using the `fa_IR-ganji-medium` voice (dataset CC0).
It was picked over the other Persian Piper voices because Whisper transcribed it with the lowest error rate.
Each phrase is synthesized on its own, trimmed and placed on its beat: «شماره …» lands on the number slam,
the product name on the logo, and each how-to step on its click. Music ducks under the voice.

Alternate voices (same video, same timing) are in `alt-voices/`, rendered from `voice/vo_amir` and `voice/vo_gyro`:
- `amir` (`fa_IR-amir-medium`, dataset CC0): a different male speaker, a bit less clear than ganji.
- `gyro` (`fa_IR-gyro-medium`): clearer than amir; its dataset license is only listed as "see github.com/gyroing", so check it before commercial use.

Use one for the main build with `VO_DIR=voice/vo_amir ./build.sh`.

```bash
pip install piper-tts   # then download fa_IR-ganji-medium.onnx + .onnx.json from rhasspy/piper-voices
python voice/tts.py fa_IR-ganji-medium.onnx voice/vo 0.8 && ./build.sh
``` Set `HANDLE` in `motion.js` to show your page handle on the last frame.

## Story (bars @128 BPM, 1 bar = 1.875s)

| Bars | Scene |
|---|---|
| 0–1 | Hook: Claude mark slams in, «۵ تا کانکتر / که باید همین الان / به Claude وصل کنی», logo swarm, riser |
| 2–3 | #5 Figma: «طرحت رو مستقیم به کد تبدیل کنه» |
| 4–5 | #4 Notion: «یادداشت‌هات رو مرتب و صفحه‌بندی کنه» |
| 6–7 | #3 Google Drive: «بین همه‌ی فایل‌هات جستجو کنه» |
| 8–9 | #2 Google Calendar: «برنامه‌ات رو بدونه و وقت خالی پیدا کنه» |
| 10–11 | #1 Gmail: «ایمیل‌هات رو بخونه و خلاصه کنه» |
| 12–13 | How to connect: Settings → Connectors → Connect (breakdown) |
| 14–15 | «تو کدومو اول وصل می‌کنی؟» + comment prompt (drop) |
| 16 | «Save کن، که یادت نره» |
