# گل‌لایو — Football Live

Persian (RTL) live-scores web app with FotMob-level match data. No build step, no dependencies.

- **Matches**: day strip, live / followed filters, sort by league or kick-off time, auto-refresh every 20s, goal flash + sound.
- **Match page**: summary (momentum, event timeline, player of the match, insights, form, venue/referee/weather), full team stats by half,
  interactive xG shotmap, lineups on a pitch with ratings and events, per-player stats, live standings, head-to-head.
- **Leagues / teams / players**: tables (all / home / away / xG), fixtures by round, stat leaders, past champions, squads,
  trophies, player season stats with percentiles, recent matches and career.
- Favourites (teams, leagues, matches) with goal notifications, search, light/dark theme, installable PWA.

## Run locally

    node server.js        # http://localhost:3000

## Deploy

Vercel serves the static files and `api/fm.js` as a serverless function (allowlisted, trimmed, edge-cached proxy to FotMob).
If FotMob ever answers 401/403, set `FOTMOB_XMAS` to the current `x-mas` request header from fotmob.com.

FotMob has no public API; this is unofficial and may break or be restricted by their terms.

## Mobile app

- **Install from the browser (PWA):** open the site, then "Install app" (Android/Chrome) or Share → "Add to Home Screen" (iOS).
- **Android app:** `android/` is a native app (Kotlin + Jetpack Compose) that uses the same `/api` functions as the site:
  matches by day, match page (summary, video, stats, line-up pitch, table, head-to-head), leagues, teams with club themes
  and history, players, search, following, on-device goal / line-up / kick-off alerts and the "my team" home-screen widget.
  GitHub Actions (`.github/workflows/android.yml`) builds it on every change under `android/` and publishes `goallive.apk`
  to the `android-latest` release. Locally: `cd android && ./gradlew assembleRelease` (needs the Android SDK).
  `node android/tools/gen-data.mjs` regenerates the club and translation tables from `assets/`.
