# GoalLive API

Base: `https://goallive.gelsa.workers.dev` · JSON · CORS open · free (Cloudflare Workers, 100k requests a day, answers cached at the edge).
Add `?lang=fa` to any `/v1` route for Persian team, league and stat names. Times are UTC (ISO 8601). Data from FotMob.

| Route | What |
|---|---|
| `GET /v1` | this list |
| `GET /v1/leagues` | popular leagues, Iran first |
| `GET /v1/matches?date=today` | matches of a day by league; `date` = `today`, `yesterday`, `tomorrow` or `YYYY-MM-DD`; optional `league=47,87`, `tz=Asia/Tehran` |
| `GET /v1/live` | matches being played now |
| `GET /v1/matches/{id}` | one match: score, events, stats, lineups, shots, momentum, venue, referee, player of the match |
| `GET /v1/leagues/{id}/table` | table (one per group in cup group stages) |
| `GET /v1/teams/{id}` | team: form, next and last match, fixtures, squad with injuries |
| `GET /v1/players/{id}` | player: profile, season stats, last 10 matches |
| `GET /v1/search?q=persepolis` | teams, players, leagues, matches |

League ids: Iran 523, Premier League 47, LaLiga 87, Serie A 55, Bundesliga 54, Ligue 1 53, Champions League 42.
Team ids: Persepolis 101610, Esteghlal 101614, Barcelona 8634, Real Madrid 8633.

A match in a list:
```json
{ "id": 5795484, "league": { "id": 47, "name": "Premier League" }, "kickoff": "2026-10-19T19:00:00.000Z",
  "status": "finished", "minute": null,
  "home": { "id": 8586, "name": "Tottenham Hotspur", "logo": "https://…/8586.png", "score": 3 },
  "away": { "id": 10203, "name": "Nottingham Forest", "logo": "https://…/10203.png", "score": 1 } }
```
`status` is `upcoming`, `live` (with `minute`, e.g. `"67'"`), `finished`, `postponed` or `cancelled`.
Match events have `type`: `goal`, `ownGoal`, `shootoutGoal`, `yellowCard`, `secondYellow`, `redCard`, `substitution`, `var`, `period`, `addedTime`.

Errors: `{ "error": "…" }` with 400 (bad input), 404 (unknown route or id) or 502 (FotMob didn't answer).

The older `/api/*` routes (FotMob's raw answers, today, widget, highlights, images, speech) are listed at `/`.
