import {
  esc, fa, num, dec, tr, api, icon, crest, lgLogo, face, tabs, seg, sheet, errorBox, empty, skeleton, isLive, statusShort,
  timeOf, dateOf, score, rating, teamImgL, ratingClass, formChips, matchRow, matchIdFromUrl, starBtn, isDark, money,
} from './core.js';
import { tableCard } from './pages.js';
import { club, derbyName, patternStyle } from './clubs.js';
import { storyHtml } from './story.js';
import { commentaryTab, winProbability, highlightsCard, loadHighlights, shareCard } from './extras.js';
import { celebrate, animateCounts } from './motion.js';

const REFRESH = 20000;
const ui = { period: 'All', shotTeam: 'all', shot: null, plTeam: 'all', plSort: 'rating' }; // survives live re-renders

/* ---------- colours ---------- */
function lum(hex) {
  const m = /^#?([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i.exec(hex || '');
  if (!m) return 0.5;
  const [r, g, b] = m.slice(1).map((x) => parseInt(x, 16) / 255);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function teamColors(general) {
  const c = general?.teamColors?.[isDark() ? 'darkMode' : 'lightMode'] || {};
  let h = c.home || '', a = c.away || '';
  const close = (x, y) => x && y && Math.abs(lum(x) - lum(y)) < 0.08 && x.slice(1, 3) === y.slice(1, 3);
  if (!h || !a || close(h, a)) { h = getComputedStyle(document.documentElement).getPropertyValue('--home'); a = getComputedStyle(document.documentElement).getPropertyValue('--away'); }
  const ink = (x) => (lum(x.trim()) > 0.6 ? '#111' : '#fff');
  return `--home:${h};--away:${a};--home-ink:${ink(h)};--away-ink:${ink(a)}`;
}

/* ---------- header ---------- */
function scorers(map, side) {
  const items = Object.values(map || {}).map((evs) => {
    const p = evs[0];
    const mins = evs.map((e) => `<span class="min-ltr">${minuteText(e)}'</span>` + (e.ownGoal ? ' (گل به خودی)' : '') + (e.goalDescriptionKey === 'penalty' || e.suffixKey === 'penalty_short' || /pen/i.test(e.suffix || '') ? ' (پ)' : '')).join('، ');
    return `<li><span class="ltr">${esc(p.lastName || p.nameStr || p.player?.name || '')}</span> ${mins}</li>`;
  });
  return `<ul class="${side}">${items.join('')}</ul>`;
}
const PITCH_LINES = `<svg class="pitchlines" viewBox="0 0 400 240" preserveAspectRatio="none"><rect x="10" y="10" width="380" height="220" rx="4"/>
  <line x1="200" y1="10" x2="200" y2="230"/><circle cx="200" cy="120" r="34"/><rect x="10" y="62" width="56" height="116"/><rect x="334" y="62" width="56" height="116"/>
  <path d="M66 96a26 26 0 0 1 0 48M334 96a26 26 0 0 0 0 48"/></svg>`;
const prevScores = {};
function header(d) {
  const g = d.general, h = d.header, [home, away] = h.teams, s = h.status;
  const started = s.started && !s.cancelled;
  const live = isLive(s);
  const ch = club(home.id), ca = club(away.id);
  const c1 = ch?.c[0] || 'var(--home)', c2 = ca?.c[0] || 'var(--away)';
  const pattern = ch?.pattern || ca?.pattern;
  // flip the digit that changed since the last render
  const key = g.matchId, prev = prevScores[key];
  prevScores[key] = [home.score, away.score];
  const flip = (i, v) => (prev && prev[i] !== v ? 'd flip' : 'd');
  const minute = parseInt(s.liveTime?.short) || 0, full = minute > 90 ? 120 : 90, C = 2 * Math.PI * 22;
  const center = started
    ? `<div class="board-score"><span class="${flip(0, home.score)}">${fa(home.score)}</span><span class="sep">-</span><span class="${flip(1, away.score)}">${fa(away.score)}</span></div>
       ${live ? (minute ? `<div class="ring"><svg viewBox="0 0 52 52"><circle class="tr" cx="26" cy="26" r="22"/><circle class="pr" cx="26" cy="26" r="22" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - Math.min(minute, full) / full)}"/></svg>${statusShort(s)}</div>`
         : `<span class="pill live"><span class="dot"></span>${statusShort(s)}</span>`) : `<span class="pill">${statusShort(s)}</span>`}`
    : `<div class="board-time">${s.cancelled ? '–' : timeOf(s.utcTime)}</div><span class="pill">${s.cancelled ? statusShort(s) : countdown(s.utcTime)}</span>`;
  const ev = h.events || {};
  const hasGoals = Object.keys(ev.homeTeamGoals || {}).length + Object.keys(ev.awayTeamGoals || {}).length;
  const reds = (m) => Object.values(m || {}).map((e) => `<li><i class="card-r" style="display:inline-block;vertical-align:-2px"></i> <span class="ltr">${esc(e[0].lastName || e[0].nameStr || '')}</span> <span class="min-ltr">${fa(e[0].timeStr)}'</span></li>`).join('');
  const extra = [s.aggregatedStr && `مجموع دو بازی: <span class="ltr">${fa(s.aggregatedStr)}</span>`, s.whoLostOnPenalties && h.status.reason?.penalties && `پنالتی: <span class="ltr">${fa(h.status.reason.penalties.join(' - '))}</span>`]
    .filter(Boolean).join(' · ');
  const round = g.leagueRoundName ? ` · ${/^\d+$/.test(g.leagueRoundName) ? 'هفته ' + fa(g.leagueRoundName) : esc(g.leagueRoundName)}` : '';
  const derby = derbyName(home.id, away.id);
  return `<section class="hero-m" style="--c1:${c1};--c2:${c2}">
    <div class="bg"></div>${pattern ? `<div class="pat" style="${patternStyle(pattern)}"></div>` : ''}<div class="sweep"></div>${PITCH_LINES}
    <div class="hero-top"><a class="lg-link" href="#/league/${g.parentLeagueId || g.leagueId}">${lgLogo(g.parentLeagueId || g.leagueId, '')}<span>${esc(g.leagueName)}${round}</span></a>
      <span class="end">${starBtn('matches', g.matchId, home.name + ' - ' + away.name)}<button class="icon-btn" id="shareBtn" title="اشتراک‌گذاری">${icon('share', 18)}</button></span></div>
    ${derby ? `<div class="derby-tag">⚡ ${derby} ⚡</div>` : ''}
    <div class="mh-grid">
      <a class="mh-team" href="#/team/${home.id}"><img src="${teamImgL(home.id)}" alt="" onerror="this.style.visibility='hidden'"><span>${esc(home.name)}</span>${ch ? `<small>${esc(ch.fa)}</small>` : ''}</a>
      <div class="board">${center}<div class="mh-date">${dateOf(s.utcTime, { weekday: 'long', day: 'numeric', month: 'long' })}</div></div>
      <a class="mh-team a" href="#/team/${away.id}"><img src="${teamImgL(away.id)}" alt="" onerror="this.style.visibility='hidden'"><span>${esc(away.name)}</span>${ca ? `<small>${esc(ca.fa)}</small>` : ''}</a>
    </div>
    ${hasGoals || reds(ev.homeTeamRedCards) || reds(ev.awayTeamRedCards) ? `<div class="mh-scorers">
      <div>${scorers(ev.homeTeamGoals, 'h')}<ul class="h">${reds(ev.homeTeamRedCards)}</ul></div>
      <div class="ball">${hasGoals ? icon('ball', 14) : ''}</div>
      <div>${scorers(ev.awayTeamGoals, 'a')}<ul class="a">${reds(ev.awayTeamRedCards)}</ul></div></div>` : ''}
    ${extra ? `<div class="mh-extra">${extra}</div>` : ''}
    ${watchable(s) ? `<button class="watch-btn" id="watchBtn">${icon('live', 18)} تماشای زنده</button>` : ''}
  </section>`;
}

/* ---------- club stories (big clubs get a themed history section) ---------- */
const trophyCache = {};
export async function loadTrophies(id) {
  if (trophyCache[id]) return trophyCache[id];
  try {
    const t = await api('teams', { id, ccode3: 'IRN' }, { maxAge: 36e5 });
    trophyCache[id] = (t.history?.trophyList || []).filter((x) => +x.won > 0).sort((a, b) => b.won - a.won).slice(0, 8).map((x) => ({ name: x.name, won: +x.won }));
  } catch { trophyCache[id] = []; }
  return trophyCache[id];
}
function storyCard(id, name, full) {
  const c = club(id);
  if (!c) return '';
  if (!full) {
    return `<a class="story" href="#/team/${id}/story" style="--c1:${c.c[0]};--c2:${c.accent || c.c[1]};color:${c.ink === '#111' || c.ink === '#000' || c.ink === '#0b1b3a' ? c.ink : '#fff'}">
      <div class="bg"></div><div class="pat" style="${patternStyle(c.pattern)}"></div>
      <div class="story-teaser">${crest(id, 'md')}<div class="grow"><b style="font-size:15px">داستان ${esc(c.fa)}</b><div style="font-size:12px;opacity:.85">${esc(c.nick)} · از ${fa(c.founded)}</div></div>${icon('chev', 18)}</div></a>`;
  }
  return storyHtml(id, c);
}

// Live coverage stays on the broadcasters' own sites (rights holders); we only link out to them.
const watchable = (s) => isLive(s) || (!s.started && !s.cancelled && new Date(s.utcTime) - Date.now() < 3 * 36e5);
const BROADCASTERS = [
  ['آپارات اسپورت', 'پخش زنده لیگ برتر ایران و رقابت‌های اروپایی', 'https://sport.aparat.com/'],
  ['نماوا', 'پخش زنده با اشتراک نماوا', 'https://www.namava.ir/'],
  ['تلوبیون · شبکه ورزش', 'پخش زنده شبکه ورزش صداوسیما', 'https://telewebion.com/live/varzesh'],
  ['تلوبیون · شبکه ۳', 'پخش زنده شبکه سه', 'https://telewebion.com/live/tv3'],
];
function watchSheet(d) {
  const [h, a] = d.header.teams;
  sheet(`<div class="card-h" style="font-size:16px">تماشای زنده<span class="end"><button class="icon-btn" data-close>${icon('x')}</button></span></div>
    <div class="kv-h">${esc(h.name)} - ${esc(a.name)}</div>
    ${BROADCASTERS.map(([name, sub, url]) => `<a class="list-row" href="${url}" target="_blank" rel="noopener">${icon('live', 22, 'style="color:var(--live)"')}<div class="grow"><b>${name}</b><div class="meta">${sub}</div></div>${icon('chev', 18)}</a>`).join('')}
    <div class="list-row" style="cursor:default">${icon('info')}<div class="grow meta">پخش در سایت رسمی هر سرویس باز می‌شود. هر بازی فقط روی سرویسی پخش می‌شود که حق پخش آن را دارد.</div></div>
    <div style="height:12px"></div>`);
}
function countdown(iso) {
  const ms = new Date(iso) - Date.now();
  if (ms <= 0) return 'به‌زودی';
  const h = Math.floor(ms / 36e5), m = Math.floor((ms % 36e5) / 6e4);
  if (h >= 48) return `${num(Math.floor(h / 24))} روز دیگر`;
  return h ? `${num(h)} ساعت و ${num(m)} دقیقه دیگر` : `${num(m)} دقیقه دیگر`;
}

/* ---------- summary ---------- */
// timeStr is sometimes already "90 + 7"; only add overloadTime when it isn't
const minuteText = (e) => fa(String(e.timeStr).replace(/\s+/g, '')) + (e.overloadTime && !String(e.timeStr).includes('+') ? '+' + fa(e.overloadTime) : '');
function evMinute(e) { return `<span class="min-ltr">${minuteText(e)}'</span>`; }
function eventRow(e) {
  const side = e.isHome ? 'h' : 'a';
  let ico = '', who = '', cls = '';
  switch (e.type) {
    case 'Goal': {
      cls = 'goal';
      ico = icon('ball', 16);
      const pen = e.goalDescriptionKey === 'penalty' || /pen/i.test(e.suffix || '') || e.shotmapEvent?.situation === 'Penalty';
      const sc = e.newScore ? `<span class="score-chip">${score(e.newScore[0], e.newScore[1])}</span> ` : '';
      who = `<b>${sc}<a href="#/player/${e.player?.id}" class="ltr">${esc(e.nameStr || e.player?.name)}</a></b><small>${e.ownGoal ? 'گل به خودی' : pen ? 'پنالتی' : e.assistInput ? `پاس گل: <span class="ltr">${esc(e.assistInput)}</span>` : 'گل'}</small>`;
      break;
    }
    case 'Card': {
      const c = e.card === 'Red' ? 'card-r' : e.card === 'YellowRed' ? 'card-yr' : 'card-y';
      ico = `<i class="${c}"></i>`;
      who = `<b><a href="#/player/${e.player?.id}" class="ltr">${esc(e.nameStr || e.player?.name)}</a></b><small>${e.card === 'Red' ? 'کارت قرمز' : e.card === 'YellowRed' ? 'کارت زرد دوم' : 'کارت زرد'}${e.cardReason?.defaultText ? ' · ' + esc(tr(e.cardReason.defaultText)) : ''}</small>`;
      break;
    }
    case 'Substitution': {
      const [pin, pout] = e.swap || [];
      ico = icon('swap', 15, 'style="color:var(--accent)"');
      who = `<b class="ltr" style="color:var(--accent)">${esc(pin?.name)}</b><small><span class="ltr">${esc(pout?.name)}</span>${e.injuredPlayerOut ? ' · مصدوم' : ''}</small>`;
      break;
    }
    case 'Half': return `<div class="ev-divider"><span>${e.halfStrShort === 'HT' ? 'پایان نیمه اول' : e.halfStrShort === 'FT' ? 'پایان بازی' : esc(tr(e.halfStrShort))} ${score(e.homeScore, e.awayScore)}</span></div>`;
    case 'AddedTime': return `<div class="ev-divider"><span>+${fa(e.minutesAddedInput)} دقیقه وقت اضافه</span></div>`;
    case 'VAR': ico = icon('info', 15); who = `<b>VAR</b><small>${esc(e.VAR?.decision?.value || '')}</small>`; break;
    default:
      if (!e.player?.name && !e.nameStr) return '';
      ico = icon('info', 15); who = `<b class="ltr">${esc(e.nameStr || e.player?.name)}</b><small>${esc(e.type)}</small>`;
  }
  const content = `<div class="who">${who}</div><span class="ico">${ico}</span>`;
  return `<div class="ev ${cls}">
    <div class="side h">${side === 'h' ? content : ''}</div>
    <span class="min">${evMinute(e)}</span>
    <div class="side a">${side === 'a' ? content : ''}</div></div>`;
}

function momentumSvg(data) {
  if (!data?.length) return '';
  const max = Math.ceil(Math.max(...data.map((p) => p.minute), 90));
  const W = 300, H = 100, mid = H / 2, bw = W / (max + 1);
  const bars = data.map((p) => {
    const x = (p.minute / (max + 1)) * W, h = (Math.abs(p.value) / 100) * (mid - 4);
    return p.value >= 0 ? `<rect x="${x.toFixed(1)}" y="${(mid - h).toFixed(1)}" width="${Math.max(bw - 0.6, 0.8).toFixed(1)}" height="${h.toFixed(1)}" fill="var(--home)" rx=".6"/>`
      : `<rect x="${x.toFixed(1)}" y="${mid}" width="${Math.max(bw - 0.6, 0.8).toFixed(1)}" height="${h.toFixed(1)}" fill="var(--away)" rx=".6"/>`;
  }).join('');
  const ht = (45 / (max + 1)) * W;
  return `<svg class="momentum" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="direction:ltr">
    <line x1="0" x2="${W}" y1="${mid}" y2="${mid}" stroke="var(--line)" stroke-width="1"/>
    <line x1="${ht}" x2="${ht}" y1="4" y2="${H - 4}" stroke="var(--line)" stroke-dasharray="2 3"/>${bars}</svg>
    <div class="row mute ltr" style="justify-content:space-between;font-size:11px;padding:0 2px"><span>0'</span><span>45'</span><span>${max}'</span></div>`;
}

function summary(d) {
  const c = d.content, mf = c.matchFacts || {}, [home, away] = d.header.teams, s = d.header.status;
  const out = [highlightsCard(d)];
  const evs = (mf.events?.events || []).slice();
  if (isLive(s)) evs.reverse();
  if (c.momentum?.main?.data?.length) out.push(`<div class="card"><div class="card-h">مومنتوم بازی<span class="end legend" style="margin:0"><span><i style="background:var(--home)"></i>${esc(home.name)}</span><span><i style="background:var(--away)"></i>${esc(away.name)}</span></span></div><div class="card-b">${momentumSvg(c.momentum.main.data)}</div></div>`);
  out.push(winProbability(d));
  if (evs.length) out.push(`<div class="card"><div class="card-h">رویدادها</div><div class="card-b"><div class="tl">${evs.map(eventRow).join('')}</div></div></div>`);
  if (mf.playerOfTheMatch?.id) {
    const p = mf.playerOfTheMatch;
    out.push(`<div class="card"><button class="potm" data-player="${p.id}" style="width:100%;text-align:start">${face(p.id)}<div class="grow"><span class="t">${icon('star', 13, 'fill="currentColor"')} بهترین بازیکن زمین</span><b class="ltr" style="text-align:right">${esc(p.name?.fullName)}</b><span class="mute" style="font-size:12px">${esc(p.teamName)}</span></div>${rating(p.rating?.num || p.rating)}</button></div>`);
  }
  const tp = mf.topPlayers;
  if (tp?.homeTopPlayers?.length) {
    const col = (arr, t) => `<div><div class="card-h" style="padding-bottom:4px">${crest(t.id, 'sm')}${esc(t.name)}</div>${arr.map((p) => `<button class="list-row" data-player="${p.playerId}">${face(p.playerId)}<div class="grow ellipsis"><b class="ltr">${esc(p.name?.lastName || p.name?.fullName)}</b><div class="meta">${esc(p.positionLabel?.label || '')}</div></div>${rating(p.playerRating)}</button>`).join('')}</div>`;
    out.push(`<div class="card"><div class="card-h">بهترین‌های هر تیم</div><div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr))">${col(tp.homeTopPlayers, home)}${col(tp.awayTopPlayers, away)}</div></div>`);
  }
  if (mf.insights?.length) out.push(`<div class="card"><div class="card-h">نکته‌های آماری</div>${mf.insights.map((i) => `<div class="insight"><i style="background:${esc(i.color || 'var(--accent)')}"></i><span class="ltr" style="text-align:left;flex:1">${esc(i.text)}</span></div>`).join('')}</div>`);
  if (mf.teamForm?.length === 2) {
    out.push(`<div class="card"><div class="card-h">فرم ۵ بازی اخیر</div>${[home, away].map((t, i) => `<div class="list-row">${crest(t.id)}<span class="grow">${esc(t.name)}</span>${formChips(mf.teamForm[i])}</div>`).join('')}</div>`);
  }
  out.push(infoCard(d));
  if (c.h2h?.matches?.length && !s.started) out.push(h2h(d, 3));
  return out.join('');
}

function infoCard(d) {
  const ib = d.content.matchFacts?.infoBox || {}, w = d.content.weather, s = d.header.status;
  const cell = (ic, k, v) => (v ? `<div>${icon(ic, 18)}<div><span class="k">${k}</span><span class="val">${v}</span></div></div>` : '');
  const st = ib.Stadium;
  return `<div class="card"><div class="card-h">اطلاعات بازی</div><div class="info">
    ${cell('cal', 'زمان', `${dateOf(s.utcTime, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })} · ${timeOf(s.utcTime)}`)}
    ${cell('trophy', 'رقابت', ib.Tournament ? `<a href="#/league/${ib.Tournament.parentLeagueId || ib.Tournament.id}">${esc(ib.Tournament.leagueName)}</a>${ib.Tournament.roundName ? ' · ' + (/^\d+$/.test(ib.Tournament.roundName) ? 'هفته ' + fa(ib.Tournament.roundName) : esc(ib.Tournament.roundName)) : ''}` : '')}
    ${cell('stadium', 'ورزشگاه', st ? `<span class="ltr">${esc(st.name)}</span>${st.city ? '، ' + esc(st.city) : ''}${st.capacity ? ` · ظرفیت ${num(st.capacity)}` : ''}${st.surface ? ` · ${st.surface === 'grass' ? 'چمن طبیعی' : 'چمن مصنوعی'}` : ''}` : '')}
    ${cell('whistle', 'داور', ib.Referee?.text ? `<span class="ltr">${esc(ib.Referee.text)}</span>${ib.Referee.country ? ' · ' + esc(ib.Referee.country) : ''}` : '')}
    ${cell('people', 'تماشاگر', ib.Attendance ? num(ib.Attendance) : '')}
    ${cell('cloud', 'آب‌وهوا', w?.temperature != null ? `${num(w.temperature)}° · ${esc(tr(w.defaultTitle || w.description))}${w.relativeHumidity != null ? ` · رطوبت ${num(w.relativeHumidity)}٪` : ''}` : '')}
    ${ib.legInfo ? cell('info', 'بازی رفت/برگشت', esc(ib.legInfo.defaultText || '')) : ''}
  </div></div>`;
}

/* ---------- stats ---------- */
function statsTab(d) {
  const P = d.content.stats?.Periods || {};
  const keys = Object.keys(P);
  if (!keys.length) return empty('آماری برای این بازی ثبت نشده', 'list');
  if (!P[ui.period]) ui.period = keys[0];
  const [home, away] = d.header.teams;
  const groups = P[ui.period].stats || [];
  const row = (st) => {
    if (st.type === 'title') return `<h4>${esc(tr(st.title))}</h4>`;
    const [hv, av] = st.stats || [];
    if (hv == null && av == null) return '';
    const hr = +(st.rawStats?.[0]?.value ?? parseFloat(hv)) || 0, ar = +(st.rawStats?.[1]?.value ?? parseFloat(av)) || 0;
    if (st.type === 'graph' || st.key === 'BallPossesion') {
      return `<div class="st-row"><div class="st-top"><span></span><span class="t">${esc(tr(st.title))}</span><span></span></div>
        <div class="poss ltr" style="direction:rtl"><div class="h" style="flex-basis:${hr}%;color:var(--home-ink)">${num(hr)}٪</div><div class="a" style="flex-basis:${ar}%;color:var(--away-ink)">${num(ar)}٪</div></div></div>`;
    }
    const tot = hr + ar || 1;
    const hi = st.highlighted;
    return `<div class="st-row"><div class="st-top">
        <span class="v ${hi === 'home' ? 'hi h' : ''}" style="${hi === 'home' ? 'color:var(--home-ink)' : ''}">${fa(hv)}</span>
        <span class="t">${esc(tr(st.title))}</span>
        <span class="v ${hi === 'away' ? 'hi a' : ''}" style="${hi === 'away' ? 'color:var(--away-ink)' : ''}">${fa(av)}</span></div>
      <div class="st-bars"><i class="h"><b style="width:${(hr / tot) * 100}%"></b></i><i class="a"><b style="width:${(ar / tot) * 100}%"></b></i></div></div>`;
  };
  const labels = { All: 'کل بازی', FirstHalf: 'نیمه اول', SecondHalf: 'نیمه دوم' };
  return `${keys.length > 1 ? seg('period', keys.map((k) => [k, labels[k] || k]), ui.period) : ''}
    <div class="legend"><span><i style="background:var(--home)"></i>${esc(home.name)}</span><span><i style="background:var(--away)"></i>${esc(away.name)}</span></div>
    ${groups.map((g) => `<div class="card stat-group" style="margin-top:12px"><div class="card-h">${esc(tr(g.title))}</div><div class="card-b">${(g.stats || []).filter((x) => x.type !== 'title' || true).map(row).join('')}</div></div>`).join('')}
    ${attackZones(d)}`;
}
function attackZones(d) {
  const z = d.content.attackingZones;
  if (!z?.home?.total) return '';
  const [home, away] = d.header.teams;
  const zone = (t, team) => `<div><div class="card-h" style="padding:6px 0">${crest(team.id, 'sm')}${esc(team.name)}</div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:4px;direction:ltr">${['left', 'center', 'right'].map((k) => `<div style="background:var(--card2);border-radius:10px;padding:10px 4px;text-align:center"><b style="font-size:16px">${num(t.total[k])}٪</b><div class="mute" style="font-size:11px">${{ left: 'چپ', center: 'میانه', right: 'راست' }[k]}</div></div>`).join('')}</div></div>`;
  return `<div class="card" style="margin-top:12px"><div class="card-h">مناطق حمله</div><div class="card-b" style="display:grid;gap:10px">${zone(z.home, home)}${zone(z.away, away)}</div></div>`;
}

/* ---------- shotmap ---------- */
const PITCH_H = `<rect x=".5" y=".5" width="104" height="67" rx="1" class="ln"/><line x1="52.5" y1=".5" x2="52.5" y2="67.5" class="ln"/><circle cx="52.5" cy="34" r="9.15" class="ln"/>
  <rect x=".5" y="13.85" width="16.5" height="40.3" class="ln"/><rect x=".5" y="24.85" width="5.5" height="18.3" class="ln"/><rect x="88" y="13.85" width="16.5" height="40.3" class="ln"/><rect x="99" y="24.85" width="5.5" height="18.3" class="ln"/>
  <circle cx="11" cy="34" r=".4" fill="var(--pitch-line)"/><circle cx="94" cy="34" r=".4" fill="var(--pitch-line)"/><circle cx="52.5" cy="34" r=".5" fill="var(--pitch-line)"/>
  <path d="M17 26.7a9.15 9.15 0 0 1 0 14.6M88 26.7a9.15 9.15 0 0 0 0 14.6" class="ln"/>`;
function shotsTab(d) {
  const shots = d.content.shotmap?.shots || [];
  if (!shots.length) return empty('نقشه شوت برای این بازی در دسترس نیست', 'ball');
  const [home, away] = d.header.teams;
  const isH = (s) => s.teamId === home.id;
  const vis = shots.filter((s) => ui.shotTeam === 'all' || (ui.shotTeam === 'h' ? isH(s) : !isH(s)));
  const sel = shots.find((s) => s.id === ui.shot);
  const dots = vis.map((s) => {
    const h = isH(s), x = h ? 105 - s.x : s.x, y = h ? 68 - s.y : s.y;
    const r = 0.9 + Math.sqrt(s.expectedGoals || 0.02) * 3.2;
    const col = h ? 'var(--home)' : 'var(--away)';
    const goal = s.eventType === 'Goal';
    const fill = goal ? col : s.isOnTarget ? col : 'transparent';
    const op = goal ? 1 : s.isOnTarget ? 0.45 : 1;
    return `<g class="shot" data-shot="${s.id}"><circle cx="${x.toFixed(2)}" cy="${y.toFixed(2)}" r="${r.toFixed(2)}" fill="${fill}" fill-opacity="${op}" stroke="${goal ? '#fff' : col}" stroke-width="${goal ? 0.5 : 0.45}"/>
      ${goal ? `<circle cx="${x.toFixed(2)}" cy="${y.toFixed(2)}" r="${(r * 0.38).toFixed(2)}" fill="#fff"/>` : ''}
      ${sel?.id === s.id ? `<circle cx="${x.toFixed(2)}" cy="${y.toFixed(2)}" r="${(r + 1.1).toFixed(2)}" fill="none" stroke="#fff" stroke-width=".5" stroke-dasharray="1 .7"/>` : ''}</g>`;
  }).join('');
  const sum = (arr) => ({ xg: arr.reduce((a, s) => a + (s.expectedGoals || 0), 0), n: arr.length, on: arr.filter((s) => s.isOnTarget).length, g: arr.filter((s) => s.eventType === 'Goal' && !s.isOwnGoal).length });
  const hs = sum(shots.filter(isH)), as = sum(shots.filter((s) => !isH(s)));
  const result = (s) => (s.eventType === 'Goal' ? 'گل' : s.isBlocked ? 'بلاک شد' : s.isOnTarget ? 'مهار شد' : s.eventType === 'Post' ? 'تیرک' : 'به بیرون');
  return `${seg('shotTeam', [['all', 'هر دو تیم'], ['h', esc(home.name)], ['a', esc(away.name)]], ui.shotTeam)}
    <div class="card" style="margin-top:12px;padding:12px">
      <div class="row ltr" style="justify-content:space-between;font-size:12px;font-weight:700;margin-bottom:8px"><span style="color:var(--home)">← ${esc(home.name)}</span><span style="color:var(--away)">${esc(away.name)} →</span></div>
      <svg class="pitch" viewBox="0 0 105 68" style="direction:ltr">${PITCH_H}${dots}</svg>
      <div class="legend"><span><i style="background:var(--mute);border-radius:50%"></i>گل</span><span><i style="background:var(--mute);opacity:.45;border-radius:50%"></i>در چارچوب</span><span><i style="border:1.5px solid var(--mute);border-radius:50%"></i>خارج / بلاک</span><span>اندازه = xG</span></div>
      ${sel ? `<div class="shot-info">
        <div class="who">${face(sel.playerId)}<div class="grow"><span class="ltr">${esc(sel.playerName)}</span><div class="mute" style="font-size:12px;font-weight:500">${fa(sel.min)}${sel.minAdded ? '+' + fa(sel.minAdded) : ''}' · ${result(sel)}</div></div><button class="icon-btn" data-player="${sel.playerId}">${icon('info', 18)}</button></div>
        <div><span class="k">xG</span><span class="val">${dec(sel.expectedGoals)}</span></div>
        <div><span class="k">xGOT</span><span class="val">${sel.expectedGoalsOnTarget != null && sel.isOnTarget ? dec(sel.expectedGoalsOnTarget) : '–'}</span></div>
        <div><span class="k">موقعیت</span><span class="val">${esc(tr(sel.situation))}</span></div>
        <div><span class="k">نوع ضربه</span><span class="val">${esc(tr(sel.shotType))}</span></div></div>` : '<div class="mute" style="text-align:center;font-size:12px;margin-top:8px">روی هر شوت بزنید تا جزئیات آن را ببینید</div>'}
    </div>
    <div class="xg-sum">
      <div><b style="color:var(--home)">${dec(hs.xg)}</b><small>xG ${esc(home.name)}</small></div>
      <div><b>${num(hs.n)} / ${num(as.n)}</b><small>شوت (در چارچوب ${num(hs.on)} / ${num(as.on)})</small></div>
      <div><b style="color:var(--away)">${dec(as.xg)}</b><small>xG ${esc(away.name)}</small></div></div>
    <div class="card" style="margin-top:12px"><div class="card-h">همه شوت‌ها</div>${shots.slice().sort((a, b) => a.min - b.min).map((s) => `
      <button class="list-row" data-shot="${s.id}"><span class="badge-num">${fa(s.min)}'</span>${crest(s.teamId, 'sm')}<div class="grow ellipsis"><b class="ltr">${esc(s.lastName || s.playerName)}</b><div class="meta">${result(s)} · ${esc(tr(s.situation))} · ${esc(tr(s.shotType))}</div></div><span class="end ltr">xG ${dec(s.expectedGoals)}</span></button>`).join('')}</div>`;
}

/* ---------- lineup ---------- */
const PITCH_V = `<svg class="lines" viewBox="0 0 68 112" preserveAspectRatio="none"><g fill="none" stroke="var(--pitch-line)" stroke-width=".35">
  <rect x="1" y="1" width="66" height="110" rx="1"/><line x1="1" y1="56" x2="67" y2="56"/><circle cx="34" cy="56" r="9.15"/>
  <rect x="13.85" y="1" width="40.3" height="16.5"/><rect x="24.85" y="1" width="18.3" height="5.5"/><rect x="13.85" y="94.5" width="40.3" height="16.5"/><rect x="24.85" y="105.5" width="18.3" height="5.5"/>
  <path d="M26.7 17.5a9.15 9.15 0 0 0 14.6 0M26.7 94.5a9.15 9.15 0 0 1 14.6 0"/></g></svg>`;
function plEvents(p) {
  const ev = p.performance?.events || [], out = [];
  const goals = ev.filter((e) => e.type === 'goal').length, og = ev.filter((e) => e.type === 'ownGoal').length, as = ev.filter((e) => e.type === 'assist').length;
  if (goals) out.push(`<span title="گل">${goals > 1 ? fa(goals) : '⚽'}</span>`);
  if (og) out.push('<span title="گل به خودی" style="color:var(--live)">⚽</span>');
  if (as) out.push('<span title="پاس گل" style="font-size:8px;font-weight:800">A</span>');
  if (ev.some((e) => e.type === 'redCard')) out.push('<span><i class="card-r"></i></span>');
  else if (ev.some((e) => e.type === 'yellowCard')) out.push('<span><i class="card-y"></i></span>');
  if (p.performance?.substitutionEvents?.some((s) => s.type === 'subOut')) out.push(`<span class="sub" title="تعویض">${icon('swap', 10)}</span>`);
  return out.length ? `<div class="badges">${out.join('')}</div>` : '';
}
function lineupTab(d) {
  const L = d.content.lineup;
  if (!L?.homeTeam?.starters?.length) return empty('ترکیب تیم‌ها هنوز اعلام نشده', 'shirt');
  const H = L.homeTeam, A = L.awayTeam;
  const chip = (p, home) => {
    const v = p.verticalLayout;
    if (!v) return '';
    const top = home ? 3 + v.y * 46 : 97 - v.y * 46, left = home ? 6 + v.x * 88 : 94 - v.x * 88;
    return `<div class="pl ${home ? 'home' : 'away'}" style="top:${top}%;left:${left}%;animation-delay:${(home ? v.y : 1 - v.y) * 500 + 300}ms" data-player="${p.id}">
      <div class="face">${face(p.id, '')}${rating(p.performance?.rating)}${plEvents(p)}</div>
      <div class="nm"><small>${esc(p.shirtNumber || '')}</small>${esc(p.lastName || p.name)}</div></div>`;
  };
  const hasLayout = H.starters.some((p) => p.verticalLayout);
  const pitch = hasLayout ? `<div class="lineup-stage"><div class="lineup-pitch">${PITCH_V}<span class="goal-net t"></span><span class="goal-net b"></span>
    <div class="form-h" style="top:8px"><span>${esc(H.formation || '')}</span><span>${esc(H.name)}${H.rating ? ' ' + rating(H.rating) : ''}</span></div>
    ${H.starters.map((p) => chip(p, true)).join('')}${A.starters.map((p) => chip(p, false)).join('')}
    <div class="form-h" style="bottom:8px"><span>${esc(A.formation || '')}</span><span>${esc(A.name)}${A.rating ? ' ' + rating(A.rating) : ''}</span></div></div></div>` : '';
  const subIn = (p) => p.performance?.substitutionEvents?.find((s) => s.type === 'subIn');
  const listRow = (p) => `<button class="list-row" data-player="${p.id}">${face(p.id)}<div class="grow ellipsis"><b class="ltr">${esc(p.name)}</b><div class="meta">${p.shirtNumber ? '#' + fa(p.shirtNumber) + ' · ' : ''}${esc(p.countryName || '')}${subIn(p) ? ` · ${icon('swap', 11)} دقیقه ${fa(subIn(p).time)}` : ''}</div></div>${rating(p.performance?.rating)}</button>`;
  const team = (T) => `<div class="card"><div class="card-h">${crest(T.id)}${esc(T.name)}<span class="sub">${esc(T.formation || '')}</span>
      <span class="end mute" style="font-size:12px;font-weight:500">${T.averageStarterAge ? `میانگین سن ${num(+T.averageStarterAge.toFixed?.(1) || T.averageStarterAge)}` : ''}${T.totalStarterMarketValue ? ` · ارزش ${money(T.totalStarterMarketValue)}` : ''}</span></div>
    ${T.coach ? `<a class="list-row" href="#/player/${T.coach.id}">${face(T.coach.id)}<div class="grow"><b class="ltr">${esc(T.coach.name)}</b><div class="meta">سرمربی</div></div></a>` : ''}
    ${hasLayout ? '' : `<div class="kv-h">ترکیب اصلی</div>${T.starters.map(listRow).join('')}`}
    ${T.subs?.length ? `<div class="kv-h">ذخیره‌ها</div>${T.subs.map(listRow).join('')}` : ''}
    ${T.unavailable?.length ? `<div class="kv-h">غایبان</div>${T.unavailable.map((p) => `<a class="list-row" href="#/player/${p.id}">${face(p.id)}<div class="grow ellipsis"><b class="ltr">${esc(p.name)}</b><div class="meta">${p.unavailability?.type === 'injury' ? 'مصدوم' : p.unavailability?.type === 'suspension' ? 'محروم' : 'غایب'}${p.unavailability?.expectedReturn ? ' · بازگشت: ' + esc(p.unavailability.expectedReturn === 'Doubtful' ? 'نامشخص' : p.unavailability.expectedReturn) : ''}</div></div>${icon('bandage', 18, 'style="color:var(--live)"')}</a>`).join('')}` : ''}</div>`;
  return `${L.lineupType && L.lineupType !== 'standard' ? '<div class="pill accent" style="margin-bottom:10px">ترکیب احتمالی</div>' : ''}${pitch}<div style="margin-top:12px;display:grid;gap:12px">${team(H)}${team(A)}</div>`;
}

/* ---------- players ---------- */
const statVal = (p, title) => {
  for (const g of p.stats || []) { const s = g.stats?.[title]; if (s) return s.stat; }
  return null;
};
function playersTab(d) {
  const all = Object.values(d.content.playerStats || {}).filter((p) => p.stats?.length);
  if (!all.length) return empty('آمار بازیکنان در دسترس نیست', 'people');
  const [home] = d.header.teams;
  const list = all.filter((p) => ui.plTeam === 'all' || (ui.plTeam === 'h') === (p.teamId === home.id));
  const sorters = {
    rating: (p) => statVal(p, 'FotMob rating')?.value || 0,
    ga: (p) => (statVal(p, 'Goals')?.value || 0) * 10 + (statVal(p, 'Assists')?.value || 0),
    xg: (p) => statVal(p, 'Expected goals (xG)')?.value || 0,
    min: (p) => statVal(p, 'Minutes played')?.value || 0,
  };
  list.sort((a, b) => sorters[ui.plSort](b) - sorters[ui.plSort](a));
  const [h, a] = d.header.teams;
  return `${seg('plTeam', [['all', 'همه'], ['h', esc(h.name)], ['a', esc(a.name)]], ui.plTeam)}
    <div style="margin-top:8px">${seg('plSort', [['rating', 'نمره'], ['ga', 'گل و پاس گل'], ['xg', 'xG'], ['min', 'دقایق']], ui.plSort)}</div>
    <div class="card" style="margin-top:12px">${list.map((p) => {
      const g = statVal(p, 'Goals')?.value, as = statVal(p, 'Assists')?.value, mins = statVal(p, 'Minutes played')?.value, xg = statVal(p, 'Expected goals (xG)')?.value;
      const bits = [mins != null && `${num(mins)}′`, g && `${num(g)} گل`, as && `${num(as)} پاس گل`, xg && `xG ${dec(xg)}`].filter(Boolean).join(' · ');
      return `<button class="list-row" data-player="${p.id}">${face(p.id)}<div class="grow ellipsis"><b class="ltr">${esc(p.name)}</b><div class="meta">${crest(p.teamId, 'sm').replace('class="crest sm"', 'class="crest sm" style="display:inline;vertical-align:-4px"')} ${bits}</div></div>${p.isPotm ? icon('star', 14, 'fill="var(--gold)" stroke="var(--gold)"') : ''}${rating(statVal(p, 'FotMob rating')?.value)}</button>`;
    }).join('')}</div>`;
}
function playerSheet(d, id) {
  const p = d.content.playerStats?.[id];
  const lp = [...(d.content.lineup?.homeTeam?.starters || []), ...(d.content.lineup?.homeTeam?.subs || []), ...(d.content.lineup?.awayTeam?.starters || []), ...(d.content.lineup?.awayTeam?.subs || [])].find((x) => String(x.id) === String(id));
  const name = p?.name || lp?.name || '';
  const fmt = (s) => {
    if (s.value == null) return '–';
    if (s.type === 'fractionWithPercentage') return `${num(s.value)}/${num(s.total)} (${num(Math.round((s.value / (s.total || 1)) * 100))}٪)`;
    if (s.type === 'double') return dec(s.value);
    if (s.type === 'boolean') return s.value ? 'بله' : 'خیر';
    return num(s.value);
  };
  const groups = (p?.stats || []).map((g) => `<div class="kv-h">${esc(tr(g.title))}</div>${Object.entries(g.stats).filter(([t, s]) => s.key && t !== 'Shotmap').map(([t, s]) => `<div class="kv"><span>${esc(tr(t))}</span><span class="ltr">${fmt(s.stat)}</span></div>`).join('')}`).join('');
  sheet(`<div class="hero" style="padding-bottom:8px">${face(id)}<div class="grow"><h1 class="ltr" style="font-size:18px;text-align:right">${esc(name)}</h1>
      <div class="sub">${p?.teamName ? esc(p.teamName) : ''}${p?.shirtNumber ? ' · #' + fa(p.shirtNumber) : ''}${p?.isPotm ? ' · بهترین بازیکن زمین' : ''}</div></div>
      ${rating(statVal(p || {}, 'FotMob rating')?.value || lp?.performance?.rating)}</div>
    <div style="padding:0 16px 10px"><a class="pill accent" href="#/player/${id}">پروفایل کامل بازیکن ${icon('chev', 14)}</a></div>
    ${groups || '<div class="empty">آماری برای این بازیکن ثبت نشده</div>'}<div style="height:16px"></div>`);
}

/* ---------- h2h & table ---------- */
function h2h(d, limit) {
  const x = d.content.h2h;
  if (!x?.matches?.length) return empty('رودررویی ثبت نشده', 'swap');
  const [home, away] = d.header.teams, [hw, dr, aw] = x.summary || [0, 0, 0], tot = hw + dr + aw || 1;
  const ms = x.matches.slice(0, limit || 50);
  return `<div class="card"><div class="card-h">رودررو${limit ? `<a class="end pill" href="#/match/${d.general.matchId}/h2h">همه</a>` : ''}</div>
    <div class="h2h-sum"><div><b style="color:var(--home)">${num(hw)}</b><small>برد ${esc(home.name)}</small></div><div><b>${num(dr)}</b><small>تساوی</small></div><div><b style="color:var(--away)">${num(aw)}</b><small>برد ${esc(away.name)}</small></div></div>
    <div class="h2h-bar"><i style="flex:${hw};background:var(--home)"></i><i style="flex:${dr};background:var(--draw)"></i><i style="flex:${aw};background:var(--away)"></i></div>
    ${ms.map((m) => matchRow({ id: matchIdFromUrl(m.matchUrl), home: { id: m.home.id, name: m.home.name }, away: { id: m.away.id, name: m.away.name }, status: m.status },
      { showFav: false, date: true, extra: `<span class="agg">${esc(m.league?.name || '')} · ${dateOf(m.time?.utcTime || m.status.utcTime, { year: 'numeric', month: 'short', day: 'numeric' })}</span>` })).join('')}
  </div>`;
}
async function tableTab(d, box) {
  const t = d.content.table;
  if (!t?.leagueId) { box.innerHTML = empty('جدولی برای این رقابت وجود ندارد', 'list'); return; }
  box.innerHTML = skeleton(8);
  try {
    const data = await api('tltable', { leagueId: t.leagueId }, { maxAge: 6e4 });
    box.innerHTML = tableCard(data, t.teams || d.header.teams.map((x) => x.id));
  } catch (e) { box.innerHTML = errorBox(e); }
}

/* ---------- view ---------- */
export async function matchView(el, [id, tabArg]) {
  el.innerHTML = skeleton(1) + '<div style="height:12px"></div>' + skeleton(5);
  let d, timer, alive = true;
  const load = async (force) => {
    d = await api('matchDetails', { matchId: id }, { maxAge: force ? 0 : 15000 });
    return d;
  };
  try { await load(); } catch (e) { el.innerHTML = errorBox(e); return; }
  if (String(d.general?.matchId) !== String(id)) ui.shot = null;

  const c = d.content || {};
  const avail = [
    ['summary', 'خلاصه', true],
    ['stats', 'آمار', !!Object.keys(c.stats?.Periods || {}).length],
    ['shots', 'شوت‌ها', !!c.shotmap?.shots?.length],
    ['lineup', 'ترکیب', !!c.lineup?.homeTeam?.starters?.length],
    ['players', 'بازیکنان', Object.values(c.playerStats || {}).some((p) => p.stats?.length)],
    ['table', 'جدول', !!c.table?.leagueId],
    ['h2h', 'رودررو', !!c.h2h?.matches?.length],
  ].filter((t) => t[2]);
  if (d.header.status.started || c.liveticker?.langs) avail.splice(1, 0, ['live', 'گزارش زنده', true]);
  if (d.header.status.started) avail.splice(1, 0, ['video', '🎬 ویدیو', true]);
  const clubs = d.header.teams.filter((t) => club(t.id));
  if (clubs.length) avail.splice(1, 0, ['story', 'داستان باشگاه', true]);
  const tab = avail.some((t) => t[0] === tabArg) ? tabArg : 'summary';

  const render = () => {
    if (!alive) return;
    const [home, away] = d.header.teams;
    el.style.cssText = teamColors(d.general);
    let body = '';
    if (tab === 'summary') body = summary(d);
    else if (tab === 'stats') body = statsTab(d);
    else if (tab === 'shots') body = shotsTab(d);
    else if (tab === 'lineup') body = lineupTab(d);
    else if (tab === 'players') body = playersTab(d);
    else if (tab === 'h2h') body = h2h(d);
    else if (tab === 'live') body = el.querySelector('#tabBody')?.innerHTML || ''; // keep the feed while it refreshes
    else if (tab === 'video') body = highlightsCard(d);
    else if (tab === 'story') body = clubs.map((t) => storyHtml(t.id, club(t.id), trophyCache[t.id])).join('');
    if (tab === 'summary' && clubs.length) body = `<div style="display:grid;gap:10px;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));margin-bottom:12px">${clubs.map((t) => storyCard(t.id, t.name)).join('')}</div>` + body;
    el.innerHTML = `${header(d)}
      ${tabs('#/match/' + id, avail, tab)}<div id="tabBody">${body}</div>`;
    if (tab === 'table') tableTab(d, el.querySelector('#tabBody'));
    if (tab === 'live') commentaryTab(d, el.querySelector('#tabBody'));
    if (tab === 'summary' || tab === 'video') loadHighlights(d, el);
    animateCounts(el);
    // first paint animates; live refreshes after that stay still
    if (!el.classList.contains('settled')) setTimeout(() => el.classList.add('settled'), 2600);
    document.title = `${home.name} ${d.header.status.started ? d.header.status.scoreStr || '' : 'vs'} ${away.name} | گل‌لایو`;
  };
  render();
  if (tab === 'story') {
    Promise.all(clubs.map((t) => loadTrophies(t.id))).then(() => { el.classList.remove('settled'); render(); });
  }

  el.addEventListener('click', (e) => {
    const sg = e.target.closest('[data-seg] button');
    if (sg) { ui[sg.parentElement.dataset.seg] = sg.dataset.v; render(); return; }
    const sh = e.target.closest('[data-shot]');
    if (sh) { ui.shot = +sh.dataset.shot; render(); if (sh.tagName === 'BUTTON') el.querySelector('.pitch')?.scrollIntoView({ behavior: 'smooth', block: 'center' }); return; }
    const pl = e.target.closest('[data-player]');
    if (pl) { e.preventDefault(); playerSheet(d, pl.dataset.player); return; }
    if (e.target.closest('#watchBtn')) { watchSheet(d); return; }
    if (e.target.closest('#shareBtn')) shareCard(d);
  });

  // live matches (and ones about to start) refresh in place
  const needsPoll = () => {
    const s = d.header.status;
    return isLive(s) || (!s.started && !s.cancelled && new Date(s.utcTime) - Date.now() < 30 * 60e3 && new Date(s.utcTime) - Date.now() > -3 * 36e5);
  };
  const tick = async () => {
    if (!alive) return;
    if (!document.hidden && needsPoll()) {
      try {
        const before = d.header.teams.map((t) => t.score);
        await load(true);
        const now = d.header.teams.map((t) => t.score);
        if (now[0] + now[1] > before[0] + before[1]) {
          const i = now[0] > before[0] ? 0 : 1, t = d.header.teams[i], o = d.header.teams[1 - i];
          celebrate({ sub: `${d.header.teams[0].name} ${fa(now[0])} - ${fa(now[1])} ${d.header.teams[1].name}`,
            c1: club(t.id)?.c[0] || getComputedStyle(el).getPropertyValue(i ? '--away' : '--home'), c2: club(t.id)?.c[1] || club(o.id)?.c[0] || '#fff' });
        }
        if (tab !== 'table') render();
      } catch {}
    }
    timer = setTimeout(tick, REFRESH);
  };
  timer = setTimeout(tick, REFRESH);
  return () => { alive = false; clearTimeout(timer); document.title = 'گل‌لایو | نتایج زنده فوتبال'; };
}
