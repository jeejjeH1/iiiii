# GenLayer cat — "The Dispute" (3D motion piece)

`genlayer_dispute.mp4`: 1920×1080, 30 fps, ~44.5 s, H.264 + AAC. Theme colours `#ff87ff` / `#dc00ff`.

The mascot is modelled in Three.js from the 3-view sheet (`theme-reference.png`). `model_sheet.png`
puts the sheet next to the 3D model rendered at the same framing. The whole video is code: no stock
footage, models or audio.

| File | What it does |
|---|---|
| `character.js` | The cat: rigged model (head, LED visor, ears, earpieces, body, arms, paw beans, legs, tail), LED face renderer, poses (`rest`, `hero`, `cheer`, `think`) |
| `toon.js` | Render pipeline: colour pass → ink lines (id/normal/depth edges, like the sheet's line-art) → bloom → tone map + grade |
| `holo.js` | Hologram panels: bet cards, contract code, web pages, validator cards, verdict, stamp |
| `timeline.js` | One shared timeline (120 BPM): story beats, captions, sound cues, camera FX |
| `film.js` | The scene and the story; every frame is a pure function of `t` |
| `audio.js` | Synthesizes the soundtrack and every SFX from the timeline, so hits land on the frame |
| `render.js` | Headless Chromium renderer → ffmpeg |
| `build.sh` | Runs it all and muxes the final MP4 |
| `turnaround.html`, `tools/` | Model check: renders front/side/back with the sheet's framing (`node tools/shoot.js`), side-by-side compare (`tools/compare.py`), frame profiler |

## Rebuild

```bash
./build.sh                       # ~50 min on 4 CPU cores (software WebGL); resumes if interrupted
node render.js stills 7.2,18.8   # single frames -> build/stills/
```

Preview in a browser: serve the folder (`node tools/serve.js . 8080`), open `index.html`, click to play.

## Story (idea 4: dispute resolution)

1. **The bet** (0–4 s): Alice says it rained in Paris on Friday, Bob says it didn't. 200 in the pot, sparks fly.
2. **Who decides?** (4–8 s): a beam from the sky; the cat lands, its visor boots up.
3. **Intelligent Contract** (8–14 s): paw up, the contract code types itself, web pages stream data into the paw, the visor shows the proposal: YES.
4. **Validators** (14–22 s): five validator cats beam in, each with a different LLM (LED colour). They think, then each answers in its own words.
5. **Equivalence Principle** (22–28 s): the five answers orbit and merge into one verdict; every visor turns cyan and smiles.
6. **Verdict** (28–34 s): SETTLED stamp, the pot flies to Alice, confetti, everyone hops.
7. **Outro** (34–44.5 s): validators beam out, the GenLayer logo slams in piece by piece, `genlayer.com`.

The contract code on screen is illustrative pseudo-code, not the GenLayer SDK API.

## Truth Runner (browser game)

`game.html` + `game.js` + `game-audio.js`: an endless runner on the same 3D cat. Collect trusted
evidence (cyan shards), dodge fake data (red) and obstacles, and at each gate pick the answer the
evidence backs. Right answers reach consensus and raise the multiplier; wrong ones cost a life.
Keyboard (← → ↑ ↓, space, P) or swipes. Music and SFX are synthesized live with WebAudio.

Play locally: `node tools/serve.js . 8080`, then open `http://127.0.0.1:8080/game.html`.
When published as a claude.ai Artifact it keeps a shared top-10 board (`db` + `user` capabilities);
anywhere else it keeps your best score on the device.
`node tools/gametest.js` drives it headless with a test autopilot and saves screenshots.

### Trailer

`truth_runner_trailer.mp4` (1920×1080, 30 fps, ~32 s): a motion-graphics intro to the game for the
GenLayer community, captured from the real game with an overlay for titles, captions and wipes.
Rebuild: `node tools/trailer-audio.js` (soundtrack → `build/trailer_audio.wav`), then
`node tools/trailer.js` (frames → `build/trailer_noaudio.mp4`, ~40 min), then mux with ffmpeg.
