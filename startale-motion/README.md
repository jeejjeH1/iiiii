# Startale — motion graphics post (16:9)

`startale_motion.mp4` — 1920×1080, 60fps, ~79s, H.264 + AAC. Built for X/Twitter.

Everything is generated from code (no stock assets):

| File | What it does |
|---|---|
| `timeline.js` | One shared timeline at 128 BPM: scene timings, cursor path/clicks, sound cues, camera FX |
| `motion.js` | Canvas renderer: every scene, the 8-square logo, cursor, pastel mesh + bubbles, glitch/shake/flash post-FX |
| `audio.js` | Synthesizes the soundtrack (kick, bass, supersaw pads, arp, risers) and every SFX (clicks, whooshes, impacts, typing, errors) |
| `render.js` | Renders frames headless with Playwright and pipes them to ffmpeg |
| `build.sh` | Runs it all and muxes the final MP4 |

Because the timeline is shared, each click, slam and whoosh lands on the same frame as its sound.

## Rebuild

```bash
./build.sh            # FPS=30 ./build.sh for a faster draft
```

Preview in a browser: open `index.html` after a build, then click to play with audio.
Single frames: `node render.js stills 12.5,40,70` writes them to `build/stills/`.

## Story (bars @128 BPM)

1. Logo assembles square by square, one landing per beat
2. "Blockchain evolved FAST. So why is crypto adoption still facing major challenges?"
3. COMPLEXITY, behind warning tape
4. Cursor tries to "Send $10": wallet → seed phrase → network → gas → signature → a pile of error popups → "…just to complete a SIMPLE OPERATION."
5. Veterans "feels normal" vs. newcomers hitting a brick wall
6. Click → @StartaleGroup reveal
7. Startale App phone demo (smart wallet, one tap)
8. Smart wallets / simplified access / connected ecosystem
9. Security, real utility, accessibility, trust
10. Key insight: tangle → straight line; internet protocols vs. Web3 "in the background"
11. EASIER — not just more ~~POWERFUL~~
12. Poll: wallet complexity / gas fees / security / UX → reply prompt
13. Outro: logo + @StartaleGroup
