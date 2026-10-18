# Seismic — brand motion film (16:9)

`seismic_motion.mp4` — 1920×1080, 60fps, 72s, H.264 + AAC. Built for X/Twitter.

Brand: Seismic palette (Mauve `#825A6D`, Purple `#523542`, greys), the five-facet mark traced from the logo,
Suisse-style type (Inter Tight / Source Serif 4 as open stand-ins), purple-tinted architecture with light grain.
Everything is generated from code — no stock footage. Cities, the 3D monolith, UI and soundtrack are procedural.

| File | What it does |
|---|---|
| `timeline.js` | Shared timeline at 120 BPM: key moments, sound cues, camera FX |
| `core.js` | Canvas setup, text animation, the logo geometry, glass UI primitives, 3D camera + clipping |
| `city.js` | Procedural cities (avenue, worm's-eye, aerial grid), photo grade, extruded 3D gem |
| `scenes.js` | Every scene and the scene timeline (`compose`) |
| `post.js` | Seismic slice displacement, glitch, bloom, shake, whip blur, vignette, grain |
| `audio.js` | Synthesizes the score (drone, kick, bass, supersaw pads, arp) and all SFX (quake, shatter, locks, beam…) |
| `render.js` | Headless Playwright render → ffmpeg |
| `build.sh` | Builds everything and muxes the final MP4 |

## Rebuild

```bash
./build.sh            # FPS=30 ./build.sh for a faster draft
node render.js stills 12.5,40,64   # single frames -> build/stills/
```

Preview: open `index.html` after a build and click to play with audio.

## Story (bars @120 BPM, 2s per bar)

1. **Signal** (0–8s): a seismograph trace on recording paper. "Money moves at the speed of the internet." → "The rails underneath it never caught up." → "Something has to shift."
2. **Quake** (8s): the trace shatters into crystal shards, with a shockwave and the screen tearing in horizontal slices
3. **Mark** (10–13s): the shards fly back together into the five facets, one landing per beat
4. **Wordmark** (13–15s): cut to white. Seismic lockup, then "A new standard for financial infrastructure."
5. **Into the city**: the camera flies through the big facet into a purple 3D city, using the site's hero UI
   - *Create Account*: Grupo Financeiro São Vicente (Brazil) onboarding on a street-level avenue
   - *Send Payments*: Park Finance (New York) sends $2.4M, the amount encrypts, a beam fires, Settled. Shot looking straight up between towers
   - *Bundle Services*: Lukas Müller (Germany) switches on six services, which snap into one account. Aerial orbit
6. **Drop** (40s): Create accounts. / Send payments. / Run compliance workflows. / And a whole lot more. / **All in one place.**
7. **Principles**: Clarity above all / System driven / Quiet confidence (exploded mark, construction grid, giant crop)
8. **Finale**: "Powering leading financial services companies." The 3D monolith of the mark rises out of the night city
9. **End card**: lockup, tagline, Get Started / Talk To Sales, seismic.systems
