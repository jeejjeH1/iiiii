# ۲۴ ساعت با هوش مصنوعی — Instagram Reel (9:16)

`ai24h_reel.mp4`: 1080×1920, 30fps, 26.5s, H.264 + AAC, -14 LUFS. Ready to upload as a Reel.
`ai24h_reel_sfx_only.mp4`: same video with sound effects only. Use it if you want to add a trending track inside Instagram.

Everything is generated from code (no stock assets):

| File | What it does |
|---|---|
| `timeline.js` | Shared 120 BPM timeline: scene windows, clock keyframes, sound and post-FX cues |
| `motion.js` | Canvas renderer: neon clock with rolling digits, 24h progress bar, all scenes, glitch/shake/flash |
| `audio.js` | Synthesizes the synthwave loop (kick, snare, hats, bass, pad, arp) and every SFX |
| `render.js` | Renders frames headless with Playwright and pipes them to ffmpeg |
| `build.sh` | Runs it all and muxes both MP4s |
| `fonts/` | Vazirmatn (Farsi digits) + JetBrains Mono, both SIL OFL |

## Rebuild

```bash
./build.sh
```

Preview: open `index.html` after a build and click to play with audio.
Single frames: `node render.js stills 4,12.5,23` writes them to `build/stills/`.
To show your page handle on the last frame, set `HANDLE` at the top of `motion.js`.

## Story

| Time | Clock | Scene | Caption / tool |
|---|---|---|---|
| 0–3s | 07:59 | Hook: «اگه یه روز کامل / فقط با AI کار کنی…» | — |
| 3–6.5s | 08:00 | 16 emails pile up, get absorbed, 3-line summary pops out | ۵۰ تا ایمیل ← ۳ خط خلاصه · Claude / ChatGPT |
| 6.5–10s | 10:00 | Prompt → slide builds itself → deck of 12 | ارائه در ۲ دقیقه · Gamma |
| 10–13.5s | 13:00 | Line sketch → AI scan reveals neon sunset | از اسکچ تا تصویر · Midjourney |
| 13.5–17s | 16:00 | Mic waveform → subtitle → talking video | صدات ← ویدیو · ElevenLabs / Runway |
| 17–20.5s | 20:00 | Prompt → code types itself → todo app on a phone | اپ بدون کدنویسی · Claude Code / Cursor |
| 20.5–24s | 23:59 → 00:00 | Neural brain with orbiting tool icons, midnight impact | تو کدومشو استفاده می‌کنی؟ |
| 24–26.5s | 00:00 | Bookmark pop | Save کن، که یادت نره |
