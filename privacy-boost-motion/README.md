# Startale × Privacy Boost — cinematic motion post (16:9)

`privacy_boost_motion.mp4` is 1920×1080 at 60fps, about 64s long, H.264 + AAC. It is made for X/Twitter.

Everything is generated from code: the logos are redrawn as vectors, and the soundtrack and SFX are synthesized.

| File | What it does |
|---|---|
| `timeline.js` | One shared timeline at 128 BPM: scene timings, sound cues, camera FX (shake, flash, glitch, flare, whip) |
| `engine.js` | Shared drawing engine: text animators (slam / rise / scramble / type), pastel iridescent background, bubbles, the Startale 8-square mark, icons |
| `scenes.js` | Every scene, the vector Privacy Boost mark and wordmark, the cinematic letterbox |
| `post.js` | Compositing: bloom, RGB-split glitch, camera shake/punch/whip, anamorphic flares, light leaks, grain, letterbox |
| `audio.js` | Synthesizes the score (kick, bass, supersaw pads, arp, risers, braams) and every SFX (heartbeat, zaps, scanner beeps, stamps, glass shatter, bubble) |
| `render.js` | Renders frames headless with Playwright and pipes them to ffmpeg |
| `build.sh` | Runs everything and muxes the final MP4 |
| `ref/` | The theme image and logos the piece is based on |

## Rebuild

```bash
./build.sh            # FPS=30 ./build.sh for a faster draft
```

Preview in a browser: open `index.html` after a build, then click to play with audio.
Single frames: `node render.js stills 12.5,40,60` writes them to `build/stills/`.

## Story (bars @128 BPM)

1. **Cold open** (letterboxed): the darkness lifts, a bubble drifts in, then **PRIVACY** scrambles in: "…in the next phase of **WEB3 ADOPTION**"
2. **Drop.** An iridescent blockchain streams by: TRANSPARENCY / VERIFIABILITY / OPEN ACCESS
3. "But there is another side…" glitches, and the world goes dark
4. **Exposure:** a wallet fires transactions, leaving glowing footprints and public cards. A scanner stamps them TRACED. **EVERY TRANSACTION LEAVES A PUBLIC FOOTPRINT**, then a heartbeat freeze
5. Warp speed: "This is where **PRIVACY INFRASTRUCTURE** becomes important." The scene implodes
6. **Reveal:** glass shatters, the Startale mark assembles, the Privacy Boost mark spins in, Startale App × Privacy Boost
7. **Shield:** an iridescent privacy bubble forms around the user. Transactions leave as private transfers, and a tracker fails with "NO PUBLIC TRACE"
8. The bigger picture: the camera pulls back from one bubble to a whole network of users
9. More than fast transactions ✓ and easy apps ✓: **CONTROL** over how financial information is exposed
10. **Balance:** a scale weighs TRANSPARENCY (trust) against PRIVACY (protection), wobbles, and settles on **BALANCE**
11. **Barrier:** the Privacy Boost orb smashes through the wall labelled "barrier to mainstream adoption"
12. **Outro:** "deserves more attention." Startale App × Privacy Boost lockup, then fade
