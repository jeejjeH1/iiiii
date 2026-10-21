// Match extras: live commentary, win probability, highlights and the shareable result card.
import { esc, fa, num, api, icon, crest, isLive, statusShort, dateOf, toast, sheet, empty, errorBox, skeleton } from './core.js';
import { club } from './clubs.js';

/* ---------- live commentary (FotMob, English only) ---------- */
const LTC = {
  G: ['⚽', 'گل!', 'goal'], AS: ['🅰️', 'پاس گل', ''], YC: ['🟨', 'کارت زرد', ''], RC: ['🟥', 'کارت قرمز', 'red'], Y2C: ['🟥', 'کارت زرد دوم', 'red'],
  SI: ['🔁', 'تعویض', ''], highlight: ['✨', 'لحظه حساس', 'hl'], 'post_match summary': ['🏁', 'گزارش پایان بازی', 'sum'],
  'half_time summary': ['⏸️', 'گزارش بین دو نیمه', 'sum'], 'team news': ['📋', 'اخبار ترکیب', 'sum'], PM: ['❌', 'پنالتی از دست رفت', ''], PG: ['⚽', 'گل پنالتی', 'goal'], OG: ['⚽', 'گل به خودی', 'goal'],
};
export async function commentaryTab(d, box) {
  const [h, a] = d.header.teams;
  if (!box.firstChild) box.innerHTML = skeleton(6);
  try {
    const r = await api('ltc', { matchId: d.general.matchId, teams: JSON.stringify([h.name, a.name]) }, { maxAge: 10000 });
    const evs = r?.events || [];
    if (!evs.length) { box.innerHTML = empty('گزارش لحظه‌به‌لحظه برای این بازی منتشر نشده', 'list'); return; }
    box.innerHTML = `<div class="card ltc"><div class="card-h">گزارش لحظه‌به‌لحظه${isLive(d.header.status) ? '<span class="pill live" style="margin-inline-start:6px"><span class="dot"></span>زنده</span>' : ''}<span class="end sub">متن به انگلیسی · FotMob</span></div>
      ${evs.map((e, i) => {
        const [ic, label, cls] = LTC[e.type] || ['', '', ''];
        const t = e.time ? `${fa(String(e.time.main).replace(/[^\d]/g, ''))}'${e.time.added ? fa(e.time.added) : ''}` : '';
        const side = e.teamEvent === 'home' ? h : e.teamEvent === 'away' ? a : null;
        return `<div class="ltc-row ${cls} rise" style="--i:${Math.min(i, 12)}">
          <div class="ltc-t">${t ? `<span class="min-ltr">${t}</span>` : ''}${ic ? `<span class="ltc-ic">${ic}</span>` : ''}</div>
          <div class="grow">${label ? `<b class="ltc-l">${label}${e.players?.length && LTC[e.type] && cls !== 'sum' ? ` · <span class="ltr">${esc(e.players.map((p) => p.name).join(' ← '))}</span>` : ''}${side && cls !== 'sum' && label ? ` <small class="mute">(${esc(side.name)})</small>` : ''}</b>` : ''}
            <p class="ltr">${esc(e.text)}</p></div></div>`;
      }).join('')}</div>`;
  } catch (e) { box.innerHTML = errorBox(e); }
}

/* ---------- win probability (our own estimate from xG and the score) ---------- */
// Poisson model: remaining goals per team = blended scoring rate (xG so far, shrunk toward a league average) × minutes left.
function poisson(l) { const p = [Math.exp(-l)]; for (let k = 1; k <= 10; k++) p.push(p[k - 1] * l / k); return p; }
function probs(gh, ga, lh, la) {
  const ph = poisson(lh), pa = poisson(la);
  let w = 0, dr = 0, lo = 0;
  for (let i = 0; i <= 10; i++) for (let j = 0; j <= 10; j++) {
    const p = ph[i] * pa[j], fh = gh + i, fa2 = ga + j;
    if (fh > fa2) w += p; else if (fh === fa2) dr += p; else lo += p;
  }
  const t = w + dr + lo;
  return [w / t, dr / t, lo / t];
}
export function winProbability(d) {
  const s = d.header.status;
  if (!s.started || s.cancelled) return '';
  const [home] = d.header.teams;
  const shots = d.content?.shotmap?.shots || [];
  const goals = (d.content?.matchFacts?.events?.events || []).filter((e) => e.type === 'Goal');
  const now = s.finished ? 95 : Math.max(1, parseInt(s.liveTime?.short) || 1);
  const end = Math.max(93, now + 2), BASE = 1.35 / 90;
  const series = [];
  for (let t = 0; t <= Math.min(now, end); t++) {
    const xh = shots.filter((x) => x.min <= t && x.teamId === home.id).reduce((a, x) => a + (x.expectedGoals || 0), 0);
    const xa = shots.filter((x) => x.min <= t && x.teamId !== home.id).reduce((a, x) => a + (x.expectedGoals || 0), 0);
    const gh = goals.filter((g) => g.time <= t && g.isHome).length, ga = goals.filter((g) => g.time <= t && !g.isHome).length;
    const w = t / (t + 35), left = Math.max(0, end - t);
    const rh = (1 - w) * BASE + w * (xh / Math.max(t, 1)), ra = (1 - w) * BASE + w * (xa / Math.max(t, 1));
    series.push(probs(gh, ga, rh * left, ra * left));
  }
  if (s.finished) { const [h, a] = d.header.teams.map((x) => x.score); series.push(h > a ? [1, 0, 0] : h === a ? [0, 1, 0] : [0, 0, 1]); }
  const last = series[series.length - 1];
  const W = 300, H = 110, n = series.length - 1 || 1;
  const x = (i) => (i / n) * W;
  const area = (fromTop) => series.map((p, i) => `${x(i).toFixed(1)},${(fromTop(p) * H).toFixed(1)}`).join(' ');
  // stacked: home (bottom), draw (middle), away (top)
  const top1 = area((p) => p[2]), top2 = area((p) => p[2] + p[1]);
  const pct = (v) => `${num(Math.round(v * 100))}٪`;
  return `<div class="card"><div class="card-h">احتمال برد<span class="end sub">برآورد گل‌لایو از روی نتیجه و xG</span></div>
    <div class="wp-now"><div style="color:var(--home)"><b>${pct(last[0])}</b><small>${esc(home.name)}</small></div><div><b>${pct(last[1])}</b><small>مساوی</small></div><div style="color:var(--away)"><b>${pct(last[2])}</b><small>${esc(d.header.teams[1].name)}</small></div></div>
    <div class="card-b"><svg class="wp" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="direction:ltr">
      <polygon points="0,${H} ${top2} ${W},${H}" fill="var(--home)" opacity=".85"/>
      <polygon points="${top1} ${[...top2.split(' ')].reverse().join(' ')}" fill="var(--draw)" opacity=".55"/>
      <polygon points="0,0 ${top1} ${W},0" fill="var(--away)" opacity=".85"/>
      <line x1="${x(Math.min(45, n))}" x2="${x(Math.min(45, n))}" y1="0" y2="${H}" stroke="rgba(255,255,255,.35)" stroke-dasharray="3 3"/>
    </svg><div class="row mute ltr" style="justify-content:space-between;font-size:11px"><span>0'</span><span>${s.finished ? 'FT' : statusShort(s).replace(/<[^>]+>/g, '')}</span></div></div></div>`;
}

/* ---------- highlights: Aparat videos played in an app-styled player ---------- */
// Persian names help Aparat search (titles there are Persian); big clubs come from clubs.js.
const FA_NAMES = {
  'Foolad': 'فولاد', 'Gol Gohar': 'گل گهر', 'Malavan': 'ملوان', 'Zob Ahan': 'ذوب آهن', 'Aluminium Arak': 'آلومینیوم اراک', 'Chadormalou': 'چادرملو',
  'Kheybar Khorramabad': 'خیبر خرم‌آباد', 'Havadar': 'هوادار', 'Mes Rafsanjan': 'مس رفسنجان', 'Esteghlal Khuzestan': 'استقلال خوزستان', 'Shams Azar': 'شمس آذر',
  'Nassaji': 'نساجی', 'Nassaji Mazandaran': 'نساجی', 'Paykan': 'پیکان', 'Sepahan': 'سپاهان', 'Tractor': 'تراکتور', 'Persepolis': 'پرسپولیس', 'Esteghlal': 'استقلال',
  'Mes Kerman': 'مس کرمان', 'Shahr Khodro': 'شهرخودرو', 'Sanat Naft': 'صنعت نفت', 'Zob Ahan Esfahan': 'ذوب آهن', 'Fajr Sepasi': 'فجر سپاسی', 'Iran': 'ایران',
  'Newcastle United': 'نیوکاسل', 'Aston Villa': 'استون ویلا', 'West Ham United': 'وستهام', 'Everton': 'اورتون', 'Brighton & Hove Albion': 'برایتون', 'Fulham': 'فولام',
  'Crystal Palace': 'کریستال پالاس', 'Brentford': 'برنتفورد', 'Wolverhampton Wanderers': 'ولورهمپتون', 'Nottingham Forest': 'ناتینگهام فارست', 'AFC Bournemouth': 'بورنموث',
  'Leeds United': 'لیدز', 'Sunderland': 'ساندرلند', 'Burnley': 'برنلی', 'Sevilla': 'سویا', 'Villarreal': 'ویارئال', 'Real Betis': 'رئال بتیس', 'Real Sociedad': 'رئال سوسیداد',
  'Athletic Club': 'اتلتیک بیلبائو', 'Valencia': 'والنسیا', 'Girona': 'جیرونا', 'Celta Vigo': 'سلتاویگو', 'Roma': 'رم', 'Lazio': 'لاتزیو', 'Atalanta': 'آتالانتا',
  'Fiorentina': 'فیورنتینا', 'Bologna': 'بولونیا', 'Torino': 'تورینو', 'Como': 'کومو', 'Bayer Leverkusen': 'بایر لورکوزن', 'RB Leipzig': 'لایپزیگ', 'VfB Stuttgart': 'اشتوتگارت',
  'Eintracht Frankfurt': 'آینتراخت فرانکفورت', 'Borussia Mönchengladbach': 'مونشن‌گلادباخ', 'VfL Wolfsburg': 'ولفسبورگ', 'Marseille': 'مارسی', 'Lyon': 'لیون',
  'Monaco': 'موناکو', 'Lille': 'لیل', 'Benfica': 'بنفیکا', 'FC Porto': 'پورتو', 'Sporting CP': 'اسپورتینگ', 'Ajax': 'آژاکس', 'PSV Eindhoven': 'آیندهوون', 'Feyenoord': 'فاینورد',
  'Galatasaray': 'گالاتاسرای', 'Fenerbahçe': 'فنرباغچه', 'Besiktas': 'بشیکتاش', 'Club Brugge': 'کلوب بروژ', 'Celtic': 'سلتیک', 'Shakhtar Donetsk': 'شاختار',
  'Al-Hilal': 'الهلال', 'Al Hilal': 'الهلال', 'Al-Nassr': 'النصر', 'Al Nassr': 'النصر', 'Al-Ittihad': 'الاتحاد', 'Al Ittihad': 'الاتحاد', 'Al-Ahli': 'الاهلی', 'Al Ahli': 'الاهلی',
  'Slavia Prague': 'اسلاویا پراگ', 'AEK Athens': 'آاک آتن', 'Olympiacos': 'المپیاکوس', 'Panathinaikos': 'پاناتینایکوس', 'Red Star Belgrade': 'ستاره سرخ',
  'Dinamo Zagreb': 'دینامو زاگرب', 'Young Boys': 'یانگ بویز', 'Salzburg': 'سالزبورگ', 'Sturm Graz': 'اشتورم گراتس', 'Copenhagen': 'کپنهاگ', 'Union St. Gilloise': 'اونیون',
  'Bodø/Glimt': 'بودو گلیمت', 'Viking': 'وایکینگ', 'Qarabag FK': 'قره‌باغ', 'Kairat Almaty': 'کایرات', 'Pafos FC': 'پافوس', 'Athletic Bilbao': 'اتلتیک بیلبائو', 'Inter Miami CF': 'اینترمیامی', 'Spain': 'اسپانیا', 'Argentina': 'آرژانتین', 'Brazil': 'برزیل', 'France': 'فرانسه', 'England': 'انگلیس',
  'Germany': 'آلمان', 'Portugal': 'پرتغال', 'Italy': 'ایتالیا', 'Netherlands': 'هلند', 'Belgium': 'بلژیک', 'Croatia': 'کرواسی', 'Japan': 'ژاپن', 'South Korea': 'کره جنوبی',
  'Saudi Arabia': 'عربستان', 'Qatar': 'قطر', 'Iraq': 'عراق', 'Uzbekistan': 'ازبکستان', 'United Arab Emirates': 'امارات', 'Morocco': 'مراکش',
};
const faName = (t) => club(t.id)?.fa || FA_NAMES[t.name] || '';
const dur = (s) => (s ? `${fa(Math.floor(s / 60))}:${fa(String(s % 60).padStart(2, '0'))}` : '');

export function highlightsCard(d) {
  const s = d.header.status;
  if (!s.finished && !isLive(s)) return '';
  return `<div class="card hl-card" id="hlBox"><div class="card-h">${icon('live', 18)} خلاصه و گل‌ها</div><div class="hl-loading"><div class="sk" style="aspect-ratio:16/9;border-radius:18px"></div></div></div>`;
}
const hlCache = {};
export async function loadHighlights(d, root) {
  const box = root.querySelector('#hlBox');
  if (!box) return;
  const [h, a] = d.header.teams, id = d.general.matchId;
  const fh = faName(h), fa2 = faName(a);
  const q = fh || fa2 ? `خلاصه بازی ${fh || h.name} ${fa2 || a.name}` : `${h.name} ${a.name} highlights`;
  const must = [fh || h.name, fa2 || a.name].join('|');
  if (!hlCache[id]) {
    const after = Date.parse(d.header.status.utcTime) || 0;
    try { hlCache[id] = (await (await fetch(`/api/hl?q=${encodeURIComponent(q)}&must=${encodeURIComponent(must)}&after=${after}`)).json()).videos || []; } catch { hlCache[id] = []; }
  }
  const vids = hlCache[id];
  const fm = d.content?.matchFacts?.highlights;
  const yt = fm?.url && /youtu/.test(fm.url) ? fm.url.match(/(?:v=|youtu\.be\/|embed\/)([\w-]{11})/)?.[1] : null;
  if (yt) vids.push({ uid: 'yt-' + yt, title: `${fm.source || 'YouTube'} · ویدیوی رسمی`, poster: fm.image || `https://i.ytimg.com/vi/${yt}/hqdefault.jpg`, embed: `https://www.youtube-nocookie.com/embed/${yt}?autoplay=1`, channel: 'YouTube (ممکن است فیلترشکن لازم باشد)' });
  const target = root.querySelector('#hlBox');
  if (!target) return;
  if (!vids.length) {
    const qs = encodeURIComponent(q);
    target.innerHTML = `<div class="card-h">${icon('live', 18)} خلاصه و گل‌ها</div>
      <div class="empty" style="padding:18px 16px 8px">ویدیوی خلاصه هنوز پیدا نشد؛ معمولاً یکی دو ساعت بعد از بازی منتشر می‌شود.</div>
      <a class="list-row" href="https://www.aparat.com/search/${qs}" target="_blank" rel="noopener"><span class="hl-logo" style="background:#ed145b">آ</span><div class="grow"><b>جستجو در آپارات</b></div>${icon('chev', 18)}</a>`;
    return;
  }
  const [first, ...rest] = vids;
  const tile = (v, big, i) => `<button class="hl-tile ${big ? 'big' : ''}" data-hl="${i}">
      <span class="hl-poster" style="background-image:url('${esc(v.poster || '')}')"></span><span class="hl-tint"></span>
      <span class="hl-play">${icon('play', big ? 26 : 16)}</span>${v.duration ? `<span class="hl-dur">${dur(v.duration)}</span>` : ''}
      <span class="hl-meta"><b>${esc(v.title)}</b><small>${esc(v.channel || 'آپارات')}</small></span></button>`;
  target.innerHTML = `<div class="card-h">${icon('live', 18)} خلاصه و گل‌ها<span class="end sub">${num(vids.length)} ویدیو</span></div>
    <div style="padding:0 16px">${tile(first, true, 0)}</div>
    ${rest.length ? `<div class="hl-row">${rest.map((v, i) => tile(v, false, i + 1)).join('')}</div>` : '<div style="height:14px"></div>'}`;
  target.onclick = (e) => { const b = e.target.closest('[data-hl]'); if (b) openPlayer(d, vids, +b.dataset.hl); };
}

function openPlayer(d, vids, i) {
  const [h, a] = d.header.teams, s = d.header.status, ev = d.header.events || {};
  const c1 = club(h.id)?.c[0] || 'var(--home)', c2 = club(a.id)?.c[0] || 'var(--away)';
  const goals = [...Object.values(ev.homeTeamGoals || {}).flat().map((g) => ({ ...g, team: h })), ...Object.values(ev.awayTeamGoals || {}).flat().map((g) => ({ ...g, team: a }))]
    .sort((x, y) => x.time - y.time);
  const bg = sheet(`<div class="player" style="--c1:${c1};--c2:${c2}">
    <div class="player-h"><button class="icon-btn" data-close>${icon('x')}</button>
      <div class="player-score"><span>${esc(h.name)}</span><b>${s.started ? `<span class="scr"><span>${fa(h.score)}</span><span class="sep">-</span><span>${fa(a.score)}</span></span>` : 'vs'}</b><span>${esc(a.name)}</span></div></div>
    <div class="player-frame"><iframe allow="autoplay; fullscreen; picture-in-picture" allowfullscreen loading="lazy"></iframe></div>
    <div class="player-title"></div>
    ${goals.length ? `<div class="kv-h">گل‌های بازی</div><div class="player-goals">${goals.map((g) => `<div class="pg">${crest(g.team.id, 'sm')}<span class="min-ltr">${fa(g.timeStr)}'</span><b class="ltr">${esc(g.nameStr || g.player?.name || '')}</b>${g.ownGoal ? '<small>گل به خودی</small>' : ''}</div>`).join('')}</div>` : ''}
    ${vids.length > 1 ? `<div class="kv-h">ویدیوهای دیگر</div><div class="hl-row in-player">${vids.map((v, k) => `<button class="hl-tile" data-v="${k}"><span class="hl-poster" style="background-image:url('${esc(v.poster || '')}')"></span><span class="hl-tint"></span><span class="hl-play">${icon('play', 16)}</span><span class="hl-meta"><b>${esc(v.title)}</b></span></button>`).join('')}</div>` : ''}
    <div class="player-foot">پخش از ${'آپارات'} · حق نشر ویدیو متعلق به منتشرکننده است</div></div>`);
  const play = (k) => {
    const v = vids[k];
    bg.querySelector('iframe').src = v.embed;
    bg.querySelector('.player-title').innerHTML = `<b>${esc(v.title)}</b><small>${esc(v.channel || '')}</small>`;
    bg.querySelectorAll('[data-v]').forEach((t) => t.classList.toggle('on', +t.dataset.v === k));
  };
  bg.addEventListener('click', (e) => { const t = e.target.closest('[data-v]'); if (t) play(+t.dataset.v); });
  play(i);
}

/* ---------- shareable result card (1080×1350 PNG) ---------- */
const imgPath = (url) => url.replace('https://images.fotmob.com/image_resources/', '');
function loadImg(src) {
  return new Promise((ok) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => ok(null); i.src = src; });
}
export async function shareCard(d) {
  const [h, a] = d.header.teams, s = d.header.status, g = d.general;
  const hero = document.querySelector('.hero-m'), css = (v) => hero && getComputedStyle(hero).getPropertyValue(v).trim();
  const c1 = club(h.id)?.c[0] || css('--home') || '#1d3a7a';
  const c2 = club(a.id)?.c[0] || css('--away') || '#7a1d1d';
  toast('در حال ساخت کارت…');
  await Promise.all(['900 160px Vazirmatn', '800 60px Vazirmatn', '800 64px "Barlow Condensed"'].map((f) => document.fonts.load(f).catch(() => {})));
  const [ih, ia] = await Promise.all([h.id, a.id].map((id) => loadImg(`/api/img?p=logo/teamlogo/${id}.png`)));
  const W = 1080, H = 1350, cv = document.createElement('canvas');
  cv.width = W; cv.height = H;
  const x = cv.getContext('2d');
  // split background
  // home sits on the right (RTL), so its colour fills the right side
  x.fillStyle = c2; x.beginPath(); x.moveTo(0, 0); x.lineTo(W * 0.56, 0); x.lineTo(W * 0.44, H); x.lineTo(0, H); x.fill();
  x.fillStyle = c1; x.beginPath(); x.moveTo(W * 0.56, 0); x.lineTo(W, 0); x.lineTo(W, H); x.lineTo(W * 0.44, H); x.fill();
  const shade = x.createRadialGradient(W / 2, H * 0.45, 100, W / 2, H * 0.5, H * 0.8);
  shade.addColorStop(0, 'rgba(0,0,0,.25)'); shade.addColorStop(1, 'rgba(0,0,0,.82)');
  x.fillStyle = shade; x.fillRect(0, 0, W, H);
  // pitch lines
  x.strokeStyle = 'rgba(255,255,255,.14)'; x.lineWidth = 4;
  x.strokeRect(60, 60, W - 120, H - 120); x.beginPath(); x.moveTo(60, H / 2); x.lineTo(W - 60, H / 2); x.stroke();
  x.beginPath(); x.arc(W / 2, H / 2, 150, 0, 7); x.stroke();
  x.textAlign = 'center'; x.fillStyle = '#fff'; x.direction = 'rtl';
  x.font = '800 40px Vazirmatn'; x.globalAlpha = 0.9;
  x.fillText(`${g.leagueName}${g.leagueRoundName && /^\d+$/.test(g.leagueRoundName) ? ' · هفته ' + fa(g.leagueRoundName) : ''}`, W / 2, 150);
  x.globalAlpha = 1;
  // crests + names (home on the right, like the app)
  const crestAt = (im, cx) => { if (im) { x.shadowColor = 'rgba(0,0,0,.5)'; x.shadowBlur = 30; x.drawImage(im, cx - 130, 300, 260, 260); x.shadowBlur = 0; } };
  crestAt(ih, W * 0.74); crestAt(ia, W * 0.26);
  x.direction = 'ltr'; x.font = '800 60px "Barlow Condensed", Vazirmatn';
  const fit = (t, max) => { let f = 60; while (x.measureText(t).width > max && f > 34) { f -= 2; x.font = `800 ${f}px "Barlow Condensed", Vazirmatn`; } return t; };
  x.fillText(fit(h.name, 420), W * 0.74, 640); x.font = '800 60px "Barlow Condensed", Vazirmatn'; x.fillText(fit(a.name, 420), W * 0.26, 640);
  // score
  x.direction = 'rtl'; x.font = '900 230px Vazirmatn'; x.shadowColor = 'rgba(0,0,0,.6)'; x.shadowBlur = 40;
  const started = s.started && !s.cancelled;
  x.fillText(started ? `${fa(h.score)} - ${fa(a.score)}` : new Date(s.utcTime).toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' }), W / 2, 920);
  x.shadowBlur = 0; x.font = '800 46px Vazirmatn';
  x.fillText(s.finished ? 'پایان بازی' : isLive(s) ? `زنده · ${statusShort(s).replace(/<[^>]+>/g, '')}` : dateOf(s.utcTime, { weekday: 'long', day: 'numeric', month: 'long' }), W / 2, 1010);
  // scorers
  const ev = d.header.events || {}, list = (m) => Object.values(m || {}).map((e) => `${e[0].lastName || e[0].nameStr} ${e.map((g2) => fa(g2.timeStr) + "'").join(' ')}`);
  x.font = '600 34px "Barlow Condensed", Vazirmatn'; x.globalAlpha = 0.9; x.direction = 'ltr';
  list(ev.homeTeamGoals).slice(0, 4).forEach((t, i) => x.fillText('⚽ ' + t, W * 0.74, 1090 + i * 46));
  list(ev.awayTeamGoals).slice(0, 4).forEach((t, i) => x.fillText('⚽ ' + t, W * 0.26, 1090 + i * 46));
  x.globalAlpha = 1;
  // brand strip
  x.fillStyle = '#c6ff3d'; x.fillRect(0, H - 90, W, 90);
  x.fillStyle = '#0b1200'; x.direction = 'rtl'; x.font = '900 44px Vazirmatn'; x.fillText('گل‌لایو', W / 2 + 160, H - 30);
  x.font = '700 32px "Barlow Condensed", Vazirmatn'; x.direction = 'ltr'; x.fillText(location.host, W / 2 - 150, H - 32);

  const blob = await new Promise((ok) => cv.toBlob(ok, 'image/png'));
  const file = new File([blob], `goallive-${g.matchId}.png`, { type: 'image/png' });
  const url = URL.createObjectURL(blob);
  const bg = sheet(`<div class="card-h" style="font-size:16px">کارت بازی<span class="end"><button class="icon-btn" data-close>${icon('x')}</button></span></div>
    <div style="padding:0 16px"><img src="${url}" alt="" style="width:100%;border-radius:18px;box-shadow:var(--shadow)"></div>
    <div style="display:flex;gap:8px;padding:14px 16px 20px">
      ${navigator.canShare?.({ files: [file] }) ? `<button class="watch-btn" data-share style="margin:0;flex:1;animation:none">${icon('share', 18)} اشتراک‌گذاری</button>` : ''}
      <a class="watch-btn" href="${url}" download="${file.name}" style="margin:0;flex:1;animation:none;background:var(--accent);color:var(--accent-ink);box-shadow:none">ذخیره تصویر</a></div>`);
  bg.querySelector('[data-share]')?.addEventListener('click', () => navigator.share({ files: [file], title: `${h.name} - ${a.name}`, text: location.href }).catch(() => {}));
}
