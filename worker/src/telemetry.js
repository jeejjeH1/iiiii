// Crash reports and anonymous usage counts from the Android app, kept in one SQLite-backed Durable Object.
// POST /t/up    the app's batch: a random device id, app/phone versions, per-day event counts, crashes
// GET  /t/dash?key=…   the dashboard (Persian), behind the DASH_KEY secret
// GET  /t/stats?key=…  the same numbers as JSON
import { DurableObject } from 'cloudflare:workers';

const NAME = /^[a-z0-9_:.\-]{1,48}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;

export class Telemetry extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, first TEXT, last TEXT, app TEXT, sdk INTEGER, model TEXT, lang TEXT);
      CREATE TABLE IF NOT EXISTS daily (day TEXT, device TEXT, PRIMARY KEY (day, device));
      CREATE TABLE IF NOT EXISTS counts (day TEXT PRIMARY KEY, data TEXT);
      CREATE TABLE IF NOT EXISTS crashes (sig TEXT PRIMARY KEY, msg TEXT, stack TEXT, n INTEGER, first TEXT, last TEXT, app TEXT, model TEXT, sdk INTEGER, screen TEXT);
    `);
  }

  /** Takes one batch from a phone. */
  up(b) {
    const id = String(b.d || '').slice(0, 40);
    if (!/^[a-f0-9-]{16,40}$/.test(id)) return 'bad device';
    // the deploy check's batch (all zeros) only proves the way in works
    if (/^[0-]+$/.test(id)) return 'ok';
    const app = String(b.v || '').slice(0, 20), sdk = +b.sdk || 0, model = String(b.m || '').slice(0, 60), lang = String(b.l || '').slice(0, 5);
    const days = Object.keys(b.days || {}).filter(d => DAY.test(d)).sort().slice(-14);
    const today = days[days.length - 1] || new Date().toISOString().slice(0, 10);
    const known = this.sql.exec('SELECT first FROM devices WHERE id = ?', id).toArray()[0];
    const first = known ? (days[0] && days[0] < known.first ? days[0] : known.first) : (days[0] || today);
    this.sql.exec(`INSERT INTO devices (id, first, last, app, sdk, model, lang) VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET first = excluded.first, last = MAX(last, excluded.last), app = excluded.app, sdk = excluded.sdk, model = excluded.model, lang = excluded.lang`,
      id, first, today, app, sdk, model, lang);
    for (const day of days) {
      this.sql.exec('INSERT OR IGNORE INTO daily (day, device) VALUES (?, ?)', day, id);
      // the day's counts live in one JSON row, so a batch costs a write per day however many events it has
      const add = b.days[day] || {};
      const row = this.sql.exec('SELECT data FROM counts WHERE day = ?', day).toArray()[0];
      const data = row ? JSON.parse(row.data) : {};
      let n = 0;
      for (const [k, v] of Object.entries(add)) {
        if (!NAME.test(k) || n++ > 80) continue;
        data[k] = (data[k] || 0) + Math.max(0, Math.min(10000, Math.round(+v || 0)));
      }
      this.sql.exec('INSERT INTO counts (day, data) VALUES (?, ?) ON CONFLICT(day) DO UPDATE SET data = excluded.data', day, JSON.stringify(data));
    }
    for (const c of (b.crashes || []).slice(0, 5)) {
      const stack = String(c.stack || '').slice(0, 6000), msg = String(c.msg || '').slice(0, 400);
      // the same crash is the same exception at the same place in our code
      const where = (stack.match(/app\.goallive\.live[^\n]*/) || [stack.split('\n')[1] || ''])[0];
      const sig = (msg.split(':')[0] + '|' + where).slice(0, 300);
      const at = DAY.test(c.day) ? c.day : today;
      this.sql.exec(`INSERT INTO crashes (sig, msg, stack, n, first, last, app, model, sdk, screen) VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(sig) DO UPDATE SET n = n + 1, last = MAX(last, excluded.last), msg = excluded.msg, stack = excluded.stack, app = excluded.app, model = excluded.model, sdk = excluded.sdk, screen = excluded.screen`,
        sig, msg, stack, at, at, app, model, sdk, String(c.screen || '').slice(0, 40));
    }
    return 'ok';
  }

  stats() {
    const q = (s, ...a) => this.sql.exec(s, ...a).toArray();
    const dau = q('SELECT day, COUNT(*) AS n FROM daily GROUP BY day ORDER BY day DESC LIMIT 30').reverse();
    const fresh = q('SELECT first AS day, COUNT(*) AS n FROM devices GROUP BY first ORDER BY first DESC LIMIT 30').reverse();
    const cohorts = q(`SELECT d.first AS day, COUNT(*) AS n,
        SUM(EXISTS(SELECT 1 FROM daily x WHERE x.device = d.id AND x.day = date(d.first, '+1 day'))) AS d1,
        SUM(EXISTS(SELECT 1 FROM daily x WHERE x.device = d.id AND x.day >= date(d.first, '+7 day'))) AS d7
      FROM devices d GROUP BY d.first ORDER BY d.first DESC LIMIT 21`).reverse();
    const week = q('SELECT day, data FROM counts ORDER BY day DESC LIMIT 7');
    const events = {};
    for (const r of week) for (const [k, v] of Object.entries(JSON.parse(r.data))) events[k] = (events[k] || 0) + v;
    const total = q('SELECT COUNT(*) AS n FROM devices')[0].n;
    const active7 = q("SELECT COUNT(DISTINCT device) AS n FROM daily WHERE day >= date('now', '-6 day')")[0].n;
    const versions = q('SELECT app, COUNT(*) AS n FROM devices WHERE last >= date(\'now\', \'-14 day\') GROUP BY app ORDER BY n DESC LIMIT 8');
    const androids = q('SELECT sdk, COUNT(*) AS n FROM devices WHERE last >= date(\'now\', \'-14 day\') GROUP BY sdk ORDER BY sdk DESC');
    const crashes = q('SELECT sig, msg, stack, n, first, last, app, model, sdk, screen FROM crashes ORDER BY last DESC, n DESC LIMIT 30');
    return { total, active7, dau, fresh, cohorts, events, versions, androids, crashes };
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === '/up') {
      const b = await request.json().catch(() => null);
      return new Response(b ? this.up(b) : 'bad body', { status: b ? 200 : 400 });
    }
    return Response.json(this.stats());
  }
}

/** /t/* on the worker. */
export async function telemetry(request, env, url) {
  const stub = env.TELEMETRY.get(env.TELEMETRY.idFromName('main'));
  if (url.pathname === '/t/up' && request.method === 'POST') {
    const len = +request.headers.get('content-length') || 0;
    if (len > 65536) return new Response('too big', { status: 413 });
    return stub.fetch('https://t/up', { method: 'POST', body: await request.text() });
  }
  const key = env.DASH_KEY;
  if (!key) return new Response('The dashboard needs the DASH_KEY secret (GitHub → Settings → Secrets → DASH_KEY, then re-run the Cloudflare server workflow).', { status: 503 });
  if (url.searchParams.get('key') !== key) return new Response('wrong key', { status: 403 });
  const s = await (await stub.fetch('https://t/stats')).json();
  if (url.pathname === '/t/stats') return Response.json(s);
  return new Response(page(s), { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
}

/* ---------- the dashboard ---------- */

const FA = n => String(n).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]);
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const ANDROID = { 35: '15', 34: '14', 33: '13', 32: '12L', 31: '12', 30: '11', 29: '10', 28: '9', 27: '8.1', 26: '8', 25: '7.1', 24: '7' };
const LABEL = {
  open: 'باز کردن برنامه', 'screen:home': 'صفحه‌ی بازی‌ها', 'screen:match': 'صفحه‌ی بازی', 'screen:league': 'صفحه‌ی لیگ', 'screen:team': 'صفحه‌ی تیم',
  'screen:player': 'صفحه‌ی بازیکن', 'screen:leagues': 'همه‌ی لیگ‌ها', 'screen:following': 'دنبال‌شده‌ها', 'screen:more': 'بیشتر', 'screen:search': 'جستجو',
  'screen:settings': 'تنظیمات', 'screen:rankings': 'رتبه‌بندی', 'screen:assistant': 'دستیار', 'screen:predictions': 'پیش‌بینی', 'screen:fantasy': 'فانتزی',
  'screen:quiz': 'کوئیز', 'screen:calendar': 'تقویم', 'screen:board': 'تخته تاکتیک', 'screen:compare': 'مقایسه', 'screen:coach': 'مربی تو',
  'story:open': 'باز کردن استودیو استوری', 'story:image': 'ساخت عکس استوری', 'story:video': 'ساخت ویدیو استوری', 'story:reel': 'ساخت «بازی در یک دقیقه»',
  'share:instagram': 'ارسال به اینستاگرام', 'share:telegram': 'ارسال به تلگرام', 'share:other': 'ارسال به بقیه', 'share:save': 'ذخیره در گالری',
  'welcome:done': 'خوشامد: تمام کرد', 'welcome:skip': 'خوشامد: رد شد', 'welcome:team': 'خوشامد: تیم انتخاب کرد', 'welcome:notify': 'خوشامد: اعلان را روشن کرد',
  'len:0-1': 'کمتر از ۱ دقیقه', 'len:1-5': '۱ تا ۵ دقیقه', 'len:5-15': '۵ تا ۱۵ دقیقه', 'len:15+': 'بیش از ۱۵ دقیقه',
  'ask:assistant': 'سؤال از دستیار', 'predict:made': 'ثبت پیش‌بینی', 'notify:goal': 'اعلان گل', 'crash': 'کرش',
};
const label = k => LABEL[k] || (k.startsWith('exit:') ? 'خروج از: ' + (LABEL['screen:' + k.slice(5)] || k.slice(5)) : k);

function bars(rows, key = 'n', color = '#C6FF3D') {
  const max = Math.max(1, ...rows.map(r => r[key]));
  return `<div class="bars">${rows.map(r => `<div class="b" title="${esc(r.day)}: ${r[key]}"><i style="height:${Math.round(r[key] / max * 100)}%;background:${color}"></i><span>${FA(r.day.slice(8))}</span></div>`).join('')}</div>`;
}

function page(s) {
  const today = s.dau[s.dau.length - 1]?.n || 0;
  const ev = Object.entries(s.events).sort((a, b) => b[1] - a[1]);
  const screens = ev.filter(([k]) => k.startsWith('screen:')), exits = ev.filter(([k]) => k.startsWith('exit:')), other = ev.filter(([k]) => !k.startsWith('screen:') && !k.startsWith('exit:') && !k.startsWith('len:'));
  const lens = ev.filter(([k]) => k.startsWith('len:'));
  const list = (rows, color) => { const max = Math.max(1, ...rows.map(r => r[1])); return rows.map(([k, v]) => `<div class="row"><span>${esc(label(k))}</span><b>${FA(v)}</b><i style="width:${Math.round(v / max * 100)}%;background:${color}"></i></div>`).join('') || '<p class="mute">هنوز داده‌ای نیست</p>'; };
  const pct = (a, b) => b ? FA(Math.round(a / b * 100)) + '٪' : '–';
  return `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>داشبورد گل‌لایو</title>
<style>
body{margin:0;background:#0B0F14;color:#E8EDF2;font:15px/1.6 Tahoma,sans-serif;padding:16px}h1{font-size:22px;margin:4px 0 14px}h2{font-size:16px;margin:0 0 10px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:14px}.card{background:#141A22;border:1px solid #232C38;border-radius:18px;padding:16px}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin-bottom:14px}.kpi{background:#141A22;border:1px solid #232C38;border-radius:16px;padding:12px}.kpi b{display:block;font-size:26px;color:#C6FF3D}.kpi span{color:#8A96A6;font-size:13px}
.bars{display:flex;align-items:flex-end;gap:3px;height:140px}.b{flex:1;display:flex;flex-direction:column;align-items:center;height:100%;justify-content:flex-end}.b i{display:block;width:100%;border-radius:4px 4px 0 0;min-height:2px}.b span{font-size:10px;color:#8A96A6}
.row{position:relative;display:flex;justify-content:space-between;padding:6px 8px;margin:3px 0;border-radius:8px;overflow:hidden}.row i{position:absolute;inset-block:0;right:0;opacity:.18;border-radius:8px}.row span,.row b{position:relative}
table{width:100%;border-collapse:collapse;font-size:13px}td,th{padding:6px;border-bottom:1px solid #232C38;text-align:right}.mute{color:#8A96A6}
details{margin:6px 0;background:#0F141B;border-radius:12px;padding:8px 10px}summary{cursor:pointer}pre{white-space:pre-wrap;direction:ltr;text-align:left;font-size:11px;color:#B9C3D0;max-height:300px;overflow:auto}
.red{color:#FF6B7A}
</style></head><body>
<h1>📊 داشبورد گل‌لایو</h1>
<div class="kpis">
<div class="kpi"><b>${FA(today)}</b><span>کاربر فعال امروز</span></div>
<div class="kpi"><b>${FA(s.active7)}</b><span>کاربر فعال ۷ روز اخیر</span></div>
<div class="kpi"><b>${FA(s.total)}</b><span>همه‌ی نصب‌ها</span></div>
<div class="kpi"><b class="red">${FA(s.crashes.filter(c => c.last >= (s.dau[Math.max(0, s.dau.length - 7)]?.day || '')).reduce((a, c) => a + c.n, 0))}</b><span>کرش‌ها (اخیر)</span></div>
</div>
<div class="grid">
<div class="card"><h2>کاربر فعال روزانه</h2>${bars(s.dau)}</div>
<div class="card"><h2>کاربر جدید روزانه</h2>${bars(s.fresh, 'n', '#3A86FF')}</div>
<div class="card"><h2>برگشت کاربرهای جدید</h2><table><tr><th>روز نصب</th><th>نصب</th><th>فردا برگشت</th><th>بعد از یک هفته</th></tr>
${s.cohorts.map(c => `<tr><td>${FA(c.day)}</td><td>${FA(c.n)}</td><td>${pct(c.d1, c.n)}</td><td>${pct(c.d7, c.n)}</td></tr>`).join('')}</table></div>
<div class="card"><h2>پربازدیدترین بخش‌ها (۷ روز)</h2>${list(screens, '#C6FF3D')}</div>
<div class="card"><h2>کاربرها کجا برنامه را بستند (۷ روز)</h2>${list(exits, '#FF6B7A')}</div>
<div class="card"><h2>کارها (۷ روز)</h2>${list(other, '#F5C542')}</div>
<div class="card"><h2>مدت هر بار استفاده (۷ روز)</h2>${list(lens, '#7B2CBF')}</div>
<div class="card"><h2>نسخه‌ی برنامه و اندروید</h2><table><tr><th>نسخه</th><th>گوشی</th></tr>${s.versions.map(v => `<tr><td>${esc(v.app)}</td><td>${FA(v.n)}</td></tr>`).join('')}</table>
<table style="margin-top:10px"><tr><th>اندروید</th><th>گوشی</th></tr>${s.androids.map(v => `<tr><td>${esc(ANDROID[v.sdk] || v.sdk)}</td><td>${FA(v.n)}</td></tr>`).join('')}</table></div>
</div>
<div class="card" style="margin-top:14px"><h2 class="red">🐞 کرش‌ها</h2>
${s.crashes.map(c => `<details><summary><b class="red">${FA(c.n)}×</b> ${esc(c.msg.slice(0, 140))} <span class="mute">· آخرین: ${FA(c.last)} · ${esc(c.app)} · ${esc(c.model)} · اندروید ${esc(ANDROID[c.sdk] || c.sdk)}${c.screen ? ' · ' + esc(c.screen) : ''}</span></summary><pre>${esc(c.stack)}</pre></details>`).join('') || '<p class="mute">کرشی گزارش نشده 🎉</p>'}
</div>
<p class="mute">همه‌ی داده‌ها ناشناس است: فقط یک شناسه‌ی تصادفی برای هر نصب، بدون نام، شماره یا مکان.</p>
</body></html>`;
}
