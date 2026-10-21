# Football Live

Plain HTML page + a tiny Node proxy (no dependencies) that reads match data from FotMob.

    node football-live/server.js        # http://localhost:3000

- If FotMob answers 401/403, set `FOTMOB_XMAS` to the current `x-mas` request header (copy it from DevTools on fotmob.com).
- `MOCK=1 node football-live/server.js` serves `mock.json` for UI development only.
- FotMob has no public API; this is unofficial and may break or be restricted by their terms.
