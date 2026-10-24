# RedStone AVS — cinematic motion graphics post (16:9)

`redstone_avs.mp4`: 1920×1080, 60fps, ~57s, H.264 + AAC, ready for X/Twitter.

It uses the RedStone brand palette (RS Red Primary `#AE0822`, Maroon Dark `#290004`, Red Highlight `#E41939`,
Muted Raspberry `#D1707F`, Blush Orchid `#EBB3B9`, Shell `#FFE3E3`, Peach Light `#FFCFC5`) and the hex logo,
rebuilt in 3D as a ruby "red stone" (a hexagonal frustum: outer hex, inner table, six facets).

Everything is generated from code. There are no stock assets:

| File | What it does |
|---|---|
| `timeline.js` | One shared timeline at 120 BPM: scene timings, sound cues, camera FX (shake, flash, chromatic aberration, whip) |
| `motion.js` | Canvas renderer: 3D gem/coin, volumetric god rays (radial blur), anamorphic lens flares with hex ghosts, light shafts, embers, hex bokeh, bloom, grain, letterbox |
| `audio.js` | Synthesizes the score in D minor (braams, impacts, risers, drums, supersaw strings, arp) and every SFX |
| `render.js` | Renders frames headless with Playwright and pipes them to ffmpeg |
| `build.sh` | Runs it all and muxes the final MP4 |

## Rebuild

```bash
./build.sh            # FPS=30 ./build.sh for a faster draft
```

Single frames: `node render.js stills 5,12.5,40` writes them to `build/stills/`.

## Story (bars @120 BPM, 1 bar = 2s)

1. Cold open: the hex gem draws itself edge by edge, ignites with god rays, and lands in the RedStone lockup
2. "One of the most interesting parts of its ecosystem" → **RedStone AVS** (Actively Validated Service)
3. "Why does it matter?"
4. Live price feed → manipulated spike → bad liquidations, value-at-risk counter, sirens
5. "RedStone AVS takes a different approach."
6. 3D pipeline: 01 collect from oracle nodes → 02 check and aggregate (outlier rejected, median) → 03 AVS operators validate (quorum, consensus) → 04 verified data delivered onchain
7. The goal: EFFICIENT → SECURE
8. $RED: spinning hex coin, the economic layer that incentivizes network security
9. Takeaway: RELIABLE · SCALABLE · SECURE
10. The real metrics: adoption, security, value secured
11. Outro lockup: RedStone · AVS · $RED
