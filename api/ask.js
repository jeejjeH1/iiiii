// "دستیار گل‌لایو": answers any football question in Persian, using live FotMob data through tools.
// POST /api/ask  { q: "…", history: [{ role: "user"|"assistant", text: "…" }] }
//   -> { answer: "…", links: [{ type: "team"|"player"|"league"|"match", id, label }] }
// Needs ANTHROPIC_API_KEY in the Vercel project settings; without it the app keeps its built-in answers.
const Anthropic = require('@anthropic-ai/sdk');
const { fotmob } = require('../lib/fotmob');

const MODEL = 'claude-opus-5-5';
const MAX_ROUNDS = 8;

const tools = [
  {
    name: 'search',
    description: 'Find teams, players and leagues by name. Names must be in English/Latin letters (translate Persian names first, e.g. پرسپولیس -> Persepolis, طارمی -> Taremi). Returns ids for the other tools.',
    input_schema: { type: 'object', properties: { term: { type: 'string' } }, required: ['term'], additionalProperties: false },
  },
  {
    name: 'get_team',
    description: "A team's current season: league position and record, next and last match, recent form, season stats ranks, top players, coach and squad.",
    input_schema: { type: 'object', properties: { id: { type: 'integer' } }, required: ['id'], additionalProperties: false },
  },
  {
    name: 'get_player',
    description: "A player's profile: team, position, age, market value, this season's league stats, recent matches with ratings, career clubs.",
    input_schema: { type: 'object', properties: { id: { type: 'integer' } }, required: ['id'], additionalProperties: false },
  },
  {
    name: 'get_league',
    description: 'A league or cup: full standings table and the top scorers / assists. Common ids: 47 Premier League, 87 LaLiga, 55 Serie A, 54 Bundesliga, 53 Ligue 1, 42 Champions League, 73 Europa League, 523 Iran Persian Gulf Pro League.',
    input_schema: { type: 'object', properties: { id: { type: 'integer' } }, required: ['id'], additionalProperties: false },
  },
  {
    name: 'get_matches',
    description: 'All matches on a day (Tehran time), grouped by league, with scores, live minute and match ids. Date format YYYYMMDD.',
    input_schema: { type: 'object', properties: { date: { type: 'string' } }, required: ['date'], additionalProperties: false },
  },
  {
    name: 'get_match',
    description: 'One match in detail: score, status, goals and cards with minutes, main stats, line-ups, head-to-head summary.',
    input_schema: { type: 'object', properties: { id: { type: 'integer' } }, required: ['id'], additionalProperties: false },
  },
];

const get = async (path, params) => JSON.parse(await fotmob(path, params));
const pick = (o, keys) => Object.fromEntries(keys.filter((k) => o?.[k] !== undefined && o?.[k] !== null).map((k) => [k, o[k]]));
const matchBrief = (m) => m && {
  id: m.id, home: m.home?.name, away: m.away?.name, score: m.status?.scoreStr,
  time: m.status?.utcTime, finished: m.status?.finished, started: m.status?.started,
  live: m.status?.liveTime?.short, tournament: m.tournament?.name,
};

/** Runs one tool and returns compact JSON, plus the page the answer can link to. */
async function run(name, input, links) {
  if (name === 'search') {
    const groups = await get('data/search/suggest', { term: String(input.term).slice(0, 60) });
    const all = groups.flatMap((g) => g.suggestions || []);
    return all.filter((s) => s.type !== 'match').slice(0, 10).map((s) => pick(s, ['type', 'id', 'name', 'teamName', 'leagueName', 'ccode']));
  }
  if (name === 'get_team') {
    const d = await get('data/teams', { id: input.id, ccode3: 'IRN' });
    links.push({ type: 'team', id: input.id, label: d.details?.name });
    const tables = (d.table || []).flatMap((t) => [t.data?.table, ...(t.data?.tables || []).map((x) => x.table)]).filter(Boolean);
    const table = tables.find((t) => (t.all || []).some((r) => r.id === input.id));
    const row = table?.all.find((r) => r.id === input.id);
    const ov = d.overview || {};
    return {
      name: d.details?.name, country: d.details?.country,
      league: d.table?.[0]?.data?.leagueName,
      position: row && pick(row, ['idx', 'played', 'wins', 'draws', 'losses', 'scoresStr', 'pts']),
      leagueSize: table?.all?.length,
      nextMatch: ov.nextMatch && { id: ov.nextMatch.id, home: ov.nextMatch.home?.name, away: ov.nextMatch.away?.name, time: ov.nextMatch.status?.utcTime, tournament: ov.nextMatch.tournament?.name },
      lastMatch: ov.lastMatch && { id: ov.lastMatch.id, home: ov.lastMatch.home?.name, away: ov.lastMatch.away?.name, score: ov.lastMatch.status?.scoreStr, time: ov.lastMatch.status?.utcTime },
      form: (ov.teamForm || []).map((f) => `${f.resultString} ${f.tooltipText?.homeTeam} ${f.score} ${f.tooltipText?.awayTeam}`),
      seasonRanks: (d.stats?.teams || []).map((s) => `${s.header}: ${s.participant?.value} (rank ${s.participant?.rank})`),
      topPlayers: (d.stats?.players || []).slice(0, 8).map((s) => `${s.header}: ${s.participant?.name} ${s.participant?.value}`),
      coach: d.squad?.squad?.find((g) => g.title === 'coach')?.members?.[0]?.name,
      squad: (d.squad?.squad || []).filter((g) => g.title !== 'coach').map((g) => ({ [g.title]: (g.members || []).map((p) => `${p.name} #${p.shirtNumber ?? ''} (${p.id})`) })),
    };
  }
  if (name === 'get_player') {
    const d = await get('data/playerData', { id: input.id });
    links.push({ type: 'player', id: input.id, label: d.name });
    const info = Object.fromEntries((d.playerInformation || []).map((x) => [x.title, x.value?.fallback ?? x.value?.numberValue ?? x.value]));
    return {
      name: d.name, team: d.primaryTeam?.teamName, position: d.positionDescription?.primaryPosition?.label, info,
      injury: d.injuryInformation, season: d.mainLeague && { league: d.mainLeague.leagueName, season: d.mainLeague.season, stats: (d.mainLeague.stats || []).map((s) => `${s.title}: ${s.value}`) },
      recent: (d.recentMatches || []).slice(0, 6).map((m) => pick(m, ['opponentTeamName', 'homeScore', 'awayScore', 'isHomeTeam', 'goals', 'assists', 'minutesPlayed', 'leagueName', 'matchDate'])),
      career: (d.careerHistory?.careerItems?.senior?.teamEntries || []).slice(0, 8).map((t) => `${t.team} ${t.startDate?.slice(0, 4) ?? ''}-${t.endDate?.slice(0, 4) ?? ''} apps ${t.appearances ?? ''} goals ${t.goals ?? ''}`),
    };
  }
  if (name === 'get_league') {
    const d = await get('data/leagues', { id: input.id, ccode3: 'IRN' });
    links.push({ type: 'league', id: input.id, label: d.details?.name });
    const tables = (d.table || []).flatMap((t) => [t.data?.table ? { name: t.data.leagueName, rows: t.data.table.all } : null,
      ...(t.data?.tables || []).map((x) => ({ name: x.leagueName, rows: x.table?.all }))]).filter((t) => t && t.rows);
    return {
      name: d.details?.name, season: d.details?.selectedSeason,
      tables: tables.slice(0, 8).map((t) => ({ name: t.name, rows: t.rows.map((r) => `${r.idx}. ${r.name} (${r.id}) P${r.played} W${r.wins} D${r.draws} L${r.losses} ${r.scoresStr} ${r.pts}pts`) })),
      stats: (d.stats?.players || []).slice(0, 4).map((s) => ({ [s.header]: (s.topThree || []).map((p) => `${p.name} (${p.teamName}) ${p.value}`) })),
    };
  }
  if (name === 'get_matches') {
    const d = await get('data/matches', { date: String(input.date).replace(/\D/g, '').slice(0, 8), timezone: 'Asia/Tehran', ccode3: 'IRN' });
    return (d.leagues || []).slice(0, 60).map((l) => ({
      league: l.name, leagueId: l.primaryId,
      matches: (l.matches || []).map((m) => `${m.id}: ${m.home?.name} ${m.home?.score ?? ''}-${m.away?.score ?? ''} ${m.away?.name} [${m.status?.finished ? 'FT' : m.status?.started ? 'LIVE ' + (m.status?.liveTime?.short || '') : m.status?.utcTime}]`),
    }));
  }
  if (name === 'get_match') {
    const d = await get('data/matchDetails', { matchId: input.id });
    const h = d.header || {};
    links.push({ type: 'match', id: input.id, label: (h.teams || []).map((t) => t.name).join(' - ') });
    const events = (d.content?.matchFacts?.events?.events || []).filter((e) => ['Goal', 'Card', 'Substitution'].includes(e.type))
      .map((e) => `${e.time}' ${e.type}${e.card ? ' ' + e.card : ''} ${e.nameStr || e.player?.name || ''} (${e.isHome ? 'home' : 'away'})${e.newScore ? ' ' + e.newScore.join('-') : ''}`);
    const stats = (d.content?.stats?.Periods?.All?.stats || []).flatMap((g) => g.stats || []).slice(0, 14).map((s) => `${s.title}: ${(s.stats || []).join(' - ')}`);
    const lineup = (side) => side && { formation: side.formation, starters: (side.starters || []).map((p) => p.name), coach: side.coach?.name };
    return {
      teams: (h.teams || []).map((t) => ({ id: t.id, name: t.name, score: t.score })), status: pick(h.status || {}, ['utcTime', 'started', 'finished', 'scoreStr']),
      league: d.general?.leagueName, round: d.general?.leagueRoundName, events, stats,
      lineup: { home: lineup(d.content?.lineup?.homeTeam), away: lineup(d.content?.lineup?.awayTeam) },
      h2h: d.content?.h2h?.summary, playerOfTheMatch: d.content?.matchFacts?.playerOfTheMatch?.name?.fullName,
    };
  }
  throw new Error('unknown tool');
}

const system = (today) => `تو «دستیار گل‌لایو» هستی، دستیار فوتبال یک اپ فارسی نتایج زنده.
- همیشه به فارسی ساده و روان جواب بده؛ کوتاه و مستقیم (معمولاً ۱ تا ۵ جمله). نام تیم‌ها و بازیکن‌ها را به فارسی بنویس.
- برای هر چیزی که به وضعیت فعلی مربوط است (نتیجه، جدول، برنامه، آمار، ترکیب، مصدومیت) حتماً از ابزارها داده بگیر و فقط بر اساس همان جواب بده؛ از حافظه عدد نساز.
- اگر داده‌ای پیدا نشد، صادقانه بگو که در دسترس نیست.
- برای جستجو نام‌های فارسی را به انگلیسی ترجمه کن.
- زمان‌ها را به وقت تهران و تاریخ‌ها را شمسی بگو.
- سؤال‌های عمومی فوتبالی (قوانین، تاریخ، مقایسه، پیش‌بینی) را هم جواب بده؛ پیش‌بینی را با استناد به داده‌ها و با احتیاط بگو.
- از Markdown، جدول و علامت‌های ستاره استفاده نکن؛ فقط متن ساده با خط جدید.
امروز (به وقت تهران): ${today}`;

module.exports = async (req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'POST') { res.statusCode = 405; return res.end('{}'); }
  if (!process.env.ANTHROPIC_API_KEY) { res.statusCode = 503; return res.end(JSON.stringify({ error: 'not_configured' })); }
  let body = req.body;
  if (!body || typeof body === 'string') {
    const raw = typeof body === 'string' ? body : await new Promise((ok) => { let s = ''; req.on('data', (c) => { s += c; }); req.on('end', () => ok(s)); });
    try { body = JSON.parse(raw || '{}'); } catch { body = {}; }
  }
  const q = String(body.q || '').trim().slice(0, 500);
  if (!q) { res.statusCode = 400; return res.end(JSON.stringify({ error: 'empty' })); }
  const history = (Array.isArray(body.history) ? body.history : []).slice(-8)
    .filter((m) => m && (m.role === 'user' || m.role === 'assistant') && m.text)
    .map((m) => ({ role: m.role, content: String(m.text).slice(0, 1500) }));
  // the API wants the conversation to start with the user
  while (history.length && history[0].role !== 'user') history.shift();

  const today = new Intl.DateTimeFormat('fa-IR-u-ca-persian', { timeZone: 'Asia/Tehran', dateStyle: 'full' }).format(new Date())
    + ' / ' + new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tehran' }).format(new Date());
  const client = new Anthropic();
  const messages = [...history, { role: 'user', content: q }];
  const links = [];
  try {
    for (let round = 0; round < MAX_ROUNDS; round++) {
      const r = await client.beta.messages.create({
        model: MODEL, max_tokens: 16000,
        // chat: quick answers; on a policy decline, the API retries on its recommended fallback model
        output_config: { effort: 'low' },
        betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default',
        system: system(today), tools, messages,
      });
      if (r.stop_reason === 'refusal') return res.end(JSON.stringify({ answer: 'متأسفم، نمی‌توانم به این سؤال جواب بدهم.', links: [] }));
      if (r.stop_reason === 'tool_use') {
        messages.push({ role: 'assistant', content: r.content });
        const calls = r.content.filter((b) => b.type === 'tool_use');
        const results = await Promise.all(calls.map(async (c) => {
          try {
            return { type: 'tool_result', tool_use_id: c.id, content: JSON.stringify(await run(c.name, c.input || {}, links)).slice(0, 30000) };
          } catch (e) {
            return { type: 'tool_result', tool_use_id: c.id, content: 'error: ' + e.message, is_error: true };
          }
        }));
        messages.push({ role: 'user', content: results });
        continue;
      }
      if (r.stop_reason === 'pause_turn') { messages.push({ role: 'assistant', content: r.content }); continue; }
      const answer = r.content.filter((b) => b.type === 'text').map((b) => b.text).join('\n').trim();
      const seen = new Set();
      const uniq = links.filter((l) => l.id && l.label && !seen.has(l.type + l.id) && seen.add(l.type + l.id)).slice(0, 4);
      return res.end(JSON.stringify({ answer: answer || 'جوابی پیدا نشد.', links: uniq }));
    }
    res.end(JSON.stringify({ answer: 'این سؤال خیلی طولانی شد؛ لطفاً ساده‌ترش کنید.', links: [] }));
  } catch (e) {
    if (e instanceof Anthropic.RateLimitError) { res.statusCode = 429; return res.end(JSON.stringify({ error: 'busy' })); }
    if (e instanceof Anthropic.AuthenticationError) { res.statusCode = 503; return res.end(JSON.stringify({ error: 'not_configured' })); }
    res.statusCode = 502;
    res.end(JSON.stringify({ error: e instanceof Anthropic.APIError ? `api ${e.status}` : String(e.message || e) }));
  }
};
