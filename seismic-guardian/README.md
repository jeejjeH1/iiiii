# Seismic — The Guardian (3D spot, 16:9)

`seismic_guardian.mp4`: 1920×1080, 60fps, 30s, H.264 + AAC. This version keeps the character's original colors.
`seismic_guardian_brand.mp4`: the same spot with the character recolored to the Seismic brand palette.

Everything is generated from code in real-time 3D with Three.js. There are no stock assets, and the artist signature on the foot is not reproduced.

| File | What it does |
|---|---|
| `timeline.js` | Shared timeline: acts, event times, counts |
| `guardian.js` | The scene. Faceted golem built from convex rock hulls with ink outlines and cel shading, the chest doors with the `)(` emblem, data cubes, logo-shaped encrypted crystals, chain, stomp shockwave, camera path |
| `index.html` | Player page (play/scrub, original or brand colors, sound) and render target |
| `audio.js` | Synthesizes the soundtrack and SFX on the same timeline |
| `render.js` | Renders frames headless with Playwright and pipes them to ffmpeg |
| `build.sh` | Runs it all and muxes both MP4s |
| `assets/` | Reference: character drawing, logo, brand colors |

## Story

| Time | Act |
|---|---|
| 0:00 | Dormant: fog and a silhouette. The eyes ignite, the emblem lights up and the guardian straightens. |
| 0:04.6 | Plain data arrives: readable cubes (balance, bid, vote, payroll…) float in around it. |
| 0:10 | The guardian opens: the chest doors swing apart and the core pulls every cube inside. |
| 0:15 | Sealed: the doors slam and light runs up every facet edge. |
| 0:16.8 | Encrypted state: crystals shaped like the Seismic mark rise from its back and link into a chain. Their labels stay scrambled. |
| 0:22.2 | Standing guard: one stomp sends a seismic ring across the ground and the chain answers. |
| 0:25.4 | End card: low hero angle, logo crystal, "Seismic" wordmark. |

## Rebuild

```bash
./build.sh                         # FPS=30 ./build.sh for a faster draft
node render.js stills 4.5,12,23.3  # single frames -> build/stills/
node render.js stills 12 brand     # brand palette
```

Preview: serve the folder (`npx serve .`) and open `index.html`.
