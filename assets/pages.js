import { club, patternStyle } from './clubs.js';
import { storyHtml } from './story.js';
import { animateCounts } from './motion.js';
import { useComp } from './themes.js';
import {
  esc, fa, num, dec, tr, api, icon, crest, lgLogo, face, flagImg, tabs, seg, errorBox, empty, skeleton, starBtn, matchRow,
  matchIdFromUrl, formChips, rating, dateOf, timeOf, score, money, age, isDark, teamImgL,
} from './core.js';

const $ = (s, el) => el.querySelector(s);

/* ---------- standings table (used by league, team and match pages) ---------- */
const FILTERS = [['all', 'همه'], ['home', 'خانه'], ['away', 'خارج'], ['xg', 'xG']];
function tableRows(rows, kind, legend, hl) {
  const color = (i) => legend?.find((l) => l.indices?.includes(i))?.color;
  if (kind === 'xg') {
    return `<table class="tbl"><thead><tr><th>#</th><th style="text-align:start">تیم</th><th>xG</th><th>xGA</th><th>xPts</th><th>امتیاز</th></tr></thead><tbody>${rows.map((r, i) => `
      <tr class="${hl.includes(String(r.id)) ? 'hl' : ''}"><td class="pos">${num(r.xPosition ?? r.idx)}</td>
      <td class="team"><a href="#/team/${r.id}">${crest(r.id)}<span>${esc(r.shortName || r.name)}</span></a></td>
      <td>${dec(r.xg, 1)}</td><td>${dec(r.xgConceded, 1)}</td><td class="pts">${dec(r.xPoints, 1)}</td><td class="mute">${num(r.pts)}</td></tr>`).join('')}</tbody></table>`;
  }
  return `<table class="tbl"><thead><tr><th>#</th><th style="text-align:start">تیم</th><th>بازی</th><th class="xs-hide">برد</th><th class="xs-hide">مساوی</th><th class="xs-hide">باخت</th><th>گل</th><th>تفاضل</th><th>امتیاز</th></tr></thead><tbody>${rows.map((r, i) => `
    <tr class="${hl.includes(String(r.id)) ? 'hl' : ''}"><td class="pos">${color(i) && kind === 'all' ? `<i style="background:${color(i)}"></i>` : ''}${num(r.idx ?? i + 1)}</td>
    <td class="team"><a href="#/team/${r.id}">${crest(r.id)}<span>${esc(r.shortName || r.name)}</span>${r.ongoing ? '<span class="dot pulse" style="color:var(--live)"></span>' : ''}</a></td>
    <td>${num(r.played)}</td><td class="xs-hide">${num(r.wins)}</td><td class="xs-hide">${num(r.draws)}</td><td class="xs-hide">${num(r.losses)}</td><td class="ltr">${fa(r.scoresStr)}</td>
    <td class="${r.goalConDiff > 0 ? 'posv' : r.goalConDiff < 0 ? 'neg' : ''}">${r.goalConDiff > 0 ? '+' : ''}${num(r.goalConDiff)}</td><td class="pts">${num(r.pts)}</td></tr>`).join('')}</tbody></table>`;
}
export function tableCard(data, highlight = []) {
  const hl = highlight.map(String);
  const root = Array.isArray(data) ? data[0]?.data : data?.data || data;
  if (!root) return empty('جدولی وجود ندارد', 'list');
  const tables = root.tables?.length ? root.tables : [root];
  const kinds = FILTERS.filter(([k]) => tables.some((t) => t.table?.[k]?.length));
  return `${kinds.length > 1 ? `<div data-tblseg>${seg('tbl', kinds, 'all')}</div>` : ''}` + tables.map((t) => {
    if (!t.table) return '';
    // groups with one of the highlighted teams go first
    return { t, pri: hl.length && t.table.all?.some((r) => hl.includes(String(r.id))) ? 0 : 1 };
  }).filter(Boolean).sort((a, b) => a.pri - b.pri).map(({ t }) => `
    <div class="card" style="margin-top:12px">${tables.length > 1 ? `<div class="card-h">${esc(t.leagueName)}</div>` : ''}
      ${kinds.map(([k]) => `<div class="tbl-wrap" data-tv="${k}" ${k === 'all' ? '' : 'hidden'}>${t.table[k] ? tableRows(t.table[k], k, t.legend, hl) : ''}</div>`).join('')}
      ${t.legend?.length ? `<div class="tbl-legend">${t.legend.map((l) => `<span><i style="background:${l.color}"></i>${esc(l.title)}</span>`).join('')}</div>` : ''}
    </div>`).join('');
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-tblseg] button');
  if (!b) return;
  const scope = b.closest('[data-tblseg]').parentElement;
  b.parentElement.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
  scope.querySelectorAll('[data-tv]').forEach((t) => (t.hidden = t.dataset.tv !== b.dataset.v));
});

const hero = (img, title, sub, end = '', c1 = '', pattern = '', lat = false, ink = '') => `<section class="phero" style="${c1 ? `--c1:${c1};` : ''}${ink ? `color:${ink};` : ''}">
  <div class="bg"></div>${pattern ? `<div class="pat" style="${patternStyle(pattern)}"></div>` : ''}
  <div class="phero-in">${img}<div class="grow"><h1 class="${lat ? 'lat' : ''}">${title}</h1><div class="sub">${sub}</div></div><div class="end">${end}</div></div></section>`;

/* ---------- league ---------- */
const lgState = { round: {} };
export async function leagueView(el, [id, tabArg], q) {
  el.innerHTML = skeleton(1) + skeleton(8);
  const season = q.get('season') || '';
  let d;
  try { d = await api('leagues', { id, ccode3: 'IRN', ...(season && { season }) }, { maxAge: 12e4 }); } catch (e) { el.innerHTML = errorBox(e); return; }
  const det = d.details || {};
  useComp(id);
  const has = (k) => (d.tabs || []).includes(k);
  const avail = [
    ['table', 'جدول', !!d.table?.length], ['fixtures', 'بازی‌ها', !!d.fixtures?.allMatches?.length],
    ['stats', 'آمار', !!(d.stats?.players?.length || d.stats?.teams?.length)], ['seasons', 'قهرمانان', !!d.seasons?.length || has('seasons')],
  ].filter((t) => t[2]);
  const tab = avail.some((t) => t[0] === tabArg) ? tabArg : avail[0]?.[0] || 'fixtures';
  const seasons = d.allAvailableSeasons || [];
  const seasonSel = seasons.length > 1 ? `<select id="seasonSel" class="pill" style="border:0;height:30px;font:inherit;font-weight:600">${seasons.map((s) => `<option ${s === det.selectedSeason ? 'selected' : ''}>${fa(s)}</option>`).join('')}</select>` : '';
  let body = '';
  if (tab === 'table') body = tableCard(d.table);
  else if (tab === 'fixtures') body = fixtures(d);
  else if (tab === 'stats') body = leaders(d.stats);
  else if (tab === 'seasons') body = d.seasons?.length ? `<div class="card">${d.seasons.map((s) => `
    <div class="list-row"><span class="badge-num" style="width:auto">${fa(s.seasonName)}</span>${s.winner ? `<a class="row grow" href="#/team/${s.winner.id}">${icon('trophy', 16, 'style="color:var(--gold)"')}${crest(s.winner.id)}<b class="ellipsis">${esc(s.winner.name)}</b></a>` : '<span class="grow"></span>'}
      ${s.loser ? `<a class="end" href="#/team/${s.loser.id}">${crest(s.loser.id, 'sm')}<span class="ellipsis">${esc(s.loser.name)}</span></a>` : ''}</div>`).join('')}</div>` : empty('اطلاعاتی نیست', 'trophy');
  el.innerHTML = hero(lgLogo(id, 'crest lg'), esc(det.name), `${det.country && det.country !== 'INT' ? `<img class="crest sm" src="${flagImg(det.country)}" alt="" onerror="this.remove()">${esc(det.country)} · ` : ''}فصل ${seasonSel || fa(det.selectedSeason || '')}`,
    starBtn('leagues', id, det.name), det.leagueColor || '', 'dots', true) + tabs('#/league/' + id, avail, tab) + body;
  document.title = `${det.name} | گل‌لایو`;
  const sel = $('#seasonSel', el);
  if (sel) sel.onchange = () => { location.hash = `#/league/${id}/${tab}?season=${encodeURIComponent(seasons[sel.selectedIndex])}`; };
  el.addEventListener('click', (e) => {
    const r = e.target.closest('[data-round]');
    if (!r) return;
    lgState.round[id] = r.dataset.round;
    $('#fx', el).outerHTML = fixtures(d);
  });
}
function fixtures(d) {
  const all = d.fixtures.allMatches || [];
  const rounds = [...new Set(all.map((m) => String(m.round ?? m.roundName ?? '')))];
  const id = d.details?.id;
  const first = all[d.fixtures.firstUnplayedMatch?.firstUnplayedMatchIndex] || all.find((m) => !m.status?.finished) || all[all.length - 1];
  const cur = lgState.round[id] && rounds.includes(lgState.round[id]) ? lgState.round[id] : String(first?.round ?? first?.roundName ?? rounds[0]);
  const i = rounds.indexOf(cur);
  const ms = all.filter((m) => String(m.round ?? m.roundName ?? '') === cur);
  const label = (r) => (/^\d+$/.test(r) ? `هفته ${fa(r)}` : esc(r) || 'بازی‌ها');
  const byDay = {};
  for (const m of ms) (byDay[dateOf(m.status.utcTime, { weekday: 'long', day: 'numeric', month: 'long' })] ||= []).push(m);
  return `<div id="fx"><div class="card"><div class="card-h">
      <button class="icon-btn" ${i > 0 ? `data-round="${esc(rounds[i - 1])}"` : 'disabled style="opacity:.3"'}>${icon('back', 18)}</button>
      <span class="grow" style="text-align:center">${label(cur)}</span>
      <button class="icon-btn" ${i < rounds.length - 1 ? `data-round="${esc(rounds[i + 1])}"` : 'disabled style="opacity:.3"'}>${icon('chev', 18)}</button></div>
    ${Object.entries(byDay).map(([day, list]) => `<div class="kv-h">${day}</div>${list.map((m) => matchRow({ id: m.id, home: m.home, away: m.away, status: m.status })).join('')}`).join('')}</div></div>`;
}
function leaders(stats) {
  const block = (title, arr, isTeam) => (arr?.length ? `<div class="section-title">${title}</div><div class="leader-grid">${arr.map((s) => `
    <div class="card"><div class="card-h">${esc(tr(s.header))}</div>${(s.topThree || []).map((p, i) => `
      <a class="list-row" href="#/${isTeam ? 'team/' + p.teamId : 'player/' + p.id}"><span class="badge-num">${num(p.rank || i + 1)}</span>${isTeam ? crest(p.teamId, 'md') : face(p.id)}
        <div class="grow ellipsis"><b class="ltr">${esc(p.name)}</b>${isTeam ? '' : `<div class="meta">${esc(p.teamName || '')}</div>`}</div>
        <b class="ltr">${fmtStat(p.stat || { value: p.value })}</b></a>`).join('')}</div>`).join('')}</div>` : '');
  return block('بازیکنان', stats?.players) + block('تیم‌ها', stats?.teams, true) || empty('آماری نیست', 'list');
}
function fmtStat(s) {
  const v = s.value;
  if (v == null) return '–';
  if (s.format === 'percent') return num(v) + '٪';
  if (s.format === 'fraction' || (typeof v === 'number' && v % 1)) return dec(v, s.fractions ?? 2);
  if (s.name === 'squad_value_team') return money(v);
  return num(v);
}

/* ---------- team ---------- */
export async function teamView(el, [id, tabArg]) {
  el.innerHTML = skeleton(1) + skeleton(6);
  let d;
  try { d = await api('teams', { id, ccode3: 'IRN' }, { maxAge: 12e4 }); } catch (e) { el.innerHTML = errorBox(e); return; }
  const det = d.details || {}, ov = d.overview || {};
  const avail = [
    ['overview', 'نمای کلی', true], ['fixtures', 'بازی‌ها', !!d.fixtures?.allFixtures?.fixtures?.length], ['table', 'جدول', !!d.table?.length],
    ['squad', 'بازیکنان', !!d.squad?.squad?.length], ['trophies', 'افتخارات', !!d.history?.trophyList?.length],
  ].filter((t) => t[2]);
  const cl = club(id);
  if (cl) avail.splice(1, 0, ['story', 'تاریخچه', true]);
  const tab = avail.some((t) => t[0] === tabArg) ? tabArg : 'overview';
  const color = ov.teamColors?.[isDark() ? 'darkMode' : 'lightMode'] || ov.teamColors?.color;
  const league = d.table?.[0]?.data;
  let body = '';
  const trophies = (d.history?.trophyList || []).filter((x) => +x.won > 0).sort((a, b) => b.won - a.won).slice(0, 8).map((x) => ({ name: x.name, won: +x.won }));
  if (tab === 'story') body = storyHtml(id, cl, trophies);
  else if (tab === 'overview') body = teamOverview(d);
  else if (tab === 'fixtures') body = teamFixtures(d);
  else if (tab === 'table') body = tableCard(d.table, [id]);
  else if (tab === 'squad') body = squad(d);
  else if (tab === 'trophies') body = `<div class="card">${d.history.trophyList.map((t) => `
    <a class="list-row" href="#/league/${t.leagueId}">${lgLogo(t.leagueId, 'crest md')}<div class="grow"><b>${esc(t.name)}</b><div class="meta ltr" style="text-align:right">${fa((t.seasonsWon || '').split(',').filter(Boolean).slice(0, 6).join('، '))}${(t.seasonsWon || '').split(',').length > 6 ? ' …' : ''}</div></div>
      <span class="end"><b style="color:var(--gold);font-size:16px">${icon('trophy', 16)} ${num(t.won)}</b>${t.runnerup ? `<span>نایب‌قهرمان ${num(t.runnerup)}</span>` : ''}</span></a>`).join('')}</div>`;
  el.innerHTML = hero(`<img class="crest" src="${teamImgL(id)}" alt="" onerror="this.style.visibility='hidden'">`, cl ? `${esc(cl.fa)}` : esc(det.name),
    `${cl ? `<span class="ltr lat" style="font-size:15px">${esc(det.name)}</span> · ${esc(cl.nick)}` : det.country ? `<img class="crest sm" src="${flagImg(det.country)}" alt="" onerror="this.remove()">${esc(det.country)}` : ''}${league ? ` · <a href="#/league/${league.leagueId}">${esc(league.leagueName)}</a>` : ''}`,
    starBtn('teams', id, det.name), cl?.c[0] || color || '', cl?.pattern || '', !cl, cl?.ink) + tabs('#/team/' + id, avail, tab) + body;
  animateCounts(el);
  document.title = `${det.name} | گل‌لایو`;
}
const fxRow = (f) => matchRow({ id: f.id, home: f.home, away: f.away, status: f.status }, { date: true, extra: f.tournament?.name ? `<span class="agg">${esc(f.tournament.name)}</span>` : '' });
function teamOverview(d) {
  const ov = d.overview || {}, out = [];
  const big = (m, title) => {
    if (!m) return '';
    const s = m.status || {}, started = s.started || s.finished;
    return `<div class="card"><div class="card-h">${title}<span class="end sub">${esc(m.tournament?.name || '')}</span></div><a class="mcard" href="#/match/${m.id}">
      <div class="t">${crest(m.home.id, 'md')}<span>${esc(m.home.name)}</span></div>
      <div class="c">${started ? `<b>${s.scoreStr ? score(...s.scoreStr.split(' - ')) : score(m.home.score, m.away.score)}</b>` : `<b>${timeOf(s.utcTime)}</b>`}<small>${dateOf(s.utcTime)}</small></div>
      <div class="t">${crest(m.away.id, 'md')}<span>${esc(m.away.name)}</span></div></a></div>`;
  };
  out.push(big(ov.nextMatch, 'بازی بعدی'), big(ov.lastMatch, 'آخرین بازی'));
  if (ov.teamForm?.length) out.push(`<div class="card"><div class="list-row"><b class="grow">فرم اخیر</b>${formChips(ov.teamForm)}</div></div>`);
  const tp = ov.topPlayers;
  if (tp) {
    const col = (title, x, unit) => (x?.players?.length ? `<div class="card"><div class="card-h">${title}</div>${x.players.slice(0, 3).map((p) => `
      <a class="list-row" href="#/player/${p.id}">${face(p.id)}<b class="grow ellipsis ltr" style="text-align:right">${esc(p.name)}</b><b>${unit === 'r' ? rating(p.value) : num(p.value)}</b></a>`).join('')}</div>` : '');
    out.push(`<div class="leader-grid" style="margin-top:12px">${col('بهترین نمره', tp.byRating, 'r')}${col('گلزنان', tp.byGoals)}${col('پاس گل', tp.byAssists)}</div>`);
  }
  const t = d.table?.[0]?.data;
  if (t?.table?.all || t?.tables) {
    const rows = (t.table?.all || t.tables?.find((x) => x.table?.all?.some((r) => String(r.id) === String(d.details.id)))?.table?.all || []);
    const i = rows.findIndex((r) => String(r.id) === String(d.details.id));
    if (i >= 0) {
      const slice = rows.slice(Math.max(0, i - 2), i + 3);
      out.push(`<div class="card" style="margin-top:12px"><div class="card-h"><a href="#/league/${t.leagueId}">${esc(t.leagueName)}</a><a class="end pill" href="#/team/${d.details.id}/table">جدول کامل</a></div>${tableRows(slice, 'all', null, [String(d.details.id)])}</div>`);
    }
  }
  const v = ov.venue;
  if (v?.widget) out.push(`<div class="card"><div class="card-h">ورزشگاه</div><div class="info"><div>${icon('stadium', 18)}<div><span class="k">نام</span><span class="val ltr">${esc(v.widget.name)}</span></div></div>
    <div>${icon('pin', 18)}<div><span class="k">شهر</span><span class="val">${esc(v.widget.city || '')}</span></div></div>
    ${(v.statPairs || []).map(([k, val]) => `<div>${icon('info', 18)}<div><span class="k">${{ Surface: 'زمین', Capacity: 'ظرفیت', Opened: 'افتتاح' }[k] || esc(k)}</span><span class="val">${typeof val === 'number' ? (k === 'Opened' ? fa(val) : num(val)) : esc(val === 'Grass' ? 'چمن طبیعی' : val)}</span></div></div>`).join('')}</div></div>`);
  return out.join('') || empty('اطلاعاتی نیست');
}
function teamFixtures(d) {
  const all = d.fixtures.allFixtures.fixtures;
  const next = all.findIndex((f) => !f.status?.finished && !f.status?.cancelled);
  const past = next < 0 ? all : all.slice(0, next), upcoming = next < 0 ? [] : all.slice(next);
  return `${upcoming.length ? `<div class="section-title">بازی‌های پیش رو</div><div class="card">${upcoming.map(fxRow).join('')}</div>` : ''}
    ${past.length ? `<div class="section-title">نتایج</div><div class="card">${past.slice().reverse().map(fxRow).join('')}</div>` : ''}`;
}
function squad(d) {
  return d.squad.squad.map((g) => `<div class="section-title">${esc(tr(g.title))}</div><div class="card">${g.members.map((p) => `
    <a class="list-row" href="#/player/${p.id}">${face(p.id)}<div class="grow ellipsis"><b class="ltr" style="display:block;text-align:right">${esc(p.name)}</b>
      <div class="meta">${p.shirtNumber ? '#' + fa(p.shirtNumber) + ' · ' : ''}${esc(p.cname || '')}${p.age ? ' · ' + num(p.age) + ' سال' : ''}${p.injury ? ` · <span class="err">مصدوم</span>` : ''}</div></div>
      <span class="end">${p.goals ? `${num(p.goals)} ${icon('ball', 13)}` : ''}${p.transferValue ? `<span>${money(p.transferValue)}</span>` : ''}${rating(p.rating)}</span></a>`).join('')}</div>`).join('');
}

/* ---------- player ---------- */
export async function playerView(el, [id]) {
  el.innerHTML = skeleton(1) + skeleton(6);
  let p;
  try { p = await api('playerData', { id }, { maxAge: 3e5 }); } catch (e) { el.innerHTML = errorBox(e); return; }
  const team = p.primaryTeam, pos = p.positionDescription?.primaryPosition?.label;
  const info = (p.playerInformation || []).map((x) => {
    let v = x.value?.fallback;
    if (x.title === 'Market value') v = money(x.value.numberValue);
    else if (x.title === 'Height') v = `${num(x.value.numberValue)} سانتی‌متر`;
    else if (x.title === 'Age') v = `${num(x.value.numberValue)} سال`;
    else if (x.value?.dateValue) v = dateOf(x.value.dateValue, { year: 'numeric', month: 'long', day: 'numeric' });
    else if (typeof v === 'number') v = num(v);
    else v = esc(tr(v));
    return `<div><div><span class="k">${esc(tr(x.title))}</span><span class="val">${v}</span></div></div>`;
  }).join('');
  const ml = p.mainLeague;
  const kpis = ml?.stats?.length ? `<div class="kpis">${ml.stats.slice(0, 8).map((s) => `<div><b>${typeof s.value === 'number' && s.value % 1 ? dec(s.value) : num(s.value)}</b><small>${esc(tr(s.title))}</small></div>`).join('')}</div>` : '';
  const fs = p.firstSeasonStats?.statsSection?.items || [];
  const pctColor = (r) => (r >= 80 ? 'var(--win)' : r >= 50 ? 'var(--gold)' : r >= 25 ? '#f08a24' : 'var(--loss)');
  const deep = fs.map((g) => `<div class="card" style="margin-top:12px"><div class="card-h">${esc(tr(g.title))}<span class="end sub">رتبه صدک بین هم‌پست‌ها</span></div>${g.items.map((s) => `
    <div class="pct"><span>${esc(tr(s.title))}</span><span><span class="val ltr">${fa(s.statValue)}</span>${s.per90 != null && s.statFormat !== 'percent' ? `<span class="p90">${dec(s.per90)} در ۹۰</span>` : ''}</span>
      <div class="bar"><b style="width:${Math.max(3, s.percentileRank || 0)}%;background:${pctColor(s.percentileRank || 0)}"></b></div></div>`).join('')}</div>`).join('');
  const recent = Array.isArray(p.recentMatches) ? p.recentMatches.filter((m) => m.playedInMatch !== false).slice(0, 12) : [];
  const recentHtml = recent.length ? `<div class="section-title">بازی‌های اخیر</div><div class="card">${recent.map((m) => `
    <a class="list-row" href="#/match/${m.id}">${crest(m.opponentTeamId, 'md')}<div class="grow ellipsis"><b>${m.isHomeTeam ? '' : '@ '}${esc(m.opponentTeamName)}</b>
      <div class="meta">${esc(m.leagueName || '')} · ${dateOf(m.matchDate.utcTime, { day: 'numeric', month: 'short' })} · ${score(m.homeScore, m.awayScore)}</div></div>
      <span class="end">${m.goals ? `${num(m.goals)} ${icon('ball', 13)}` : ''}${m.assists ? `${num(m.assists)} A` : ''}${m.minutesPlayed ? `<span>${num(m.minutesPlayed)}′</span>` : ''}${m.playerOfTheMatch ? icon('star', 13, 'fill="var(--gold)" stroke="var(--gold)"') : ''}${rating(+m.ratingProps?.rating)}</span></a>`).join('')}</div>` : '';
  const career = p.careerHistory?.careerItems || {};
  const careerLabels = { senior: 'باشگاهی', 'national team': 'تیم ملی', youth: 'پایه' };
  const careerHtml = Object.entries(career).filter(([, v]) => v.teamEntries?.length).map(([k, v]) => `<div class="section-title">سوابق ${careerLabels[k] || esc(k)}</div><div class="card">${v.teamEntries.map((t) => `
    <a class="list-row" href="#/team/${t.teamId}">${crest(t.teamId, 'md')}<div class="grow ellipsis"><b>${esc(t.team)}</b><div class="meta">${fa(new Date(t.startDate).getFullYear())} – ${t.endDate ? fa(new Date(t.endDate).getFullYear()) : 'اکنون'}${t.transferType === 'on loan' ? ' · قرضی' : ''}</div></div>
      <span class="end">${t.appearances ? `${num(t.appearances)} بازی` : ''}${t.goals ? ` · ${num(t.goals)} گل` : ''}</span></a>`).join('')}</div>`).join('');
  const trophies = (p.trophies?.playerTrophies || []).flatMap((t) => t.tournaments.filter((x) => x.seasonsWon?.length).map((x) => ({ ...x, team: t.teamName })));
  const trophyHtml = trophies.length ? `<div class="section-title">افتخارات</div><div class="card">${trophies.map((x) => `
    <div class="list-row">${icon('trophy', 18, 'style="color:var(--gold)"')}<div class="grow"><b>${esc(x.leagueName)}</b><div class="meta">${esc(x.team)} · <span class="ltr">${fa(x.seasonsWon.join('، '))}</span></div></div><b>${num(x.seasonsWon.length)}×</b></div>`).join('')}</div>` : '';
  const traits = p.traits?.items?.length ? `<div class="card" style="margin-top:12px"><div class="card-h">${esc(p.traits.title || 'ویژگی‌ها')}</div>${p.traits.items.map((t) => `
    <div class="pct"><span>${esc(tr(t.title))}</span><span class="val">${num(Math.round(t.value * 100))}٪</span><div class="bar"><b style="width:${t.value * 100}%;background:var(--accent)"></b></div></div>`).join('')}</div>` : '';
  const injury = p.injuryInformation ? `<div class="card" style="margin-top:12px"><div class="list-row">${icon('bandage', 20, 'style="color:var(--live)"')}<div class="grow"><b>مصدوم</b><div class="meta">${esc(p.injuryInformation.name || '')}${p.injuryInformation.expectedReturn?.expectedReturnFallback ? ' · بازگشت: ' + esc(p.injuryInformation.expectedReturn.expectedReturnFallback) : ''}</div></div></div></div>` : '';
  const pc = club(team?.teamId);
  el.innerHTML = hero(face(id, 'avatar lg'), esc(p.name), `${team ? `<a class="row" style="gap:6px" href="#/team/${team.teamId}">${crest(team.teamId, 'sm')}${esc(team.teamName)}</a>${team.onLoan ? ' (قرضی)' : ''} · ` : ''}${esc(tr(pos || ''))}${p.isCaptain ? ' · کاپیتان' : ''}`,
    '', pc?.c[0] || team?.teamColors?.color || '', pc?.pattern || 'dots', true, pc?.ink) + `<div class="card" style="margin-top:14px">
    <div class="info info3">${info}</div>${ml ? `<div class="card-h" style="border-top:1px solid var(--line)">${lgLogo(ml.leagueId)}${esc(ml.leagueName)}<span class="sub">${fa(ml.season)}</span></div>${kpis}` : ''}</div>
    ${injury}${traits}${deep}${recentHtml}${careerHtml}${trophyHtml}`;
  document.title = `${p.name} | گل‌لایو`;
}
