// GoalLive server on Cloudflare Workers.
// /api/* runs the same handlers as the Vercel functions in ../api (they are written for Node's req/res, adapted below);
// / lists the endpoints. API only: the app and the website keep their own sources.
import fm from '../../api/fm.js';
import today from '../../api/today.js';
import widget from '../../api/widget.js';
import hl from '../../api/hl.js';
import img from '../../api/img.js';
import { tts } from './tts.js';
import { v1, DOCS, HttpError } from './v1.js';
import { telemetry } from './telemetry.js';
import { league, settleAll } from './league.js';
export { Telemetry } from './telemetry.js';
export { League } from './league.js';

const ROUTES = { fm, today, widget, hl, img };

/** Runs a Node-style (req, res) handler and turns what it wrote into a Response. */
async function runNode(handler, request) {
  const headers = new Headers();
  const chunks = [];
  let status = 200;
  const res = {
    get statusCode() { return status; },
    set statusCode(v) { status = v; },
    setHeader(k, v) { headers.set(k, String(v)); },
    getHeader(k) { return headers.get(k); },
    end(body) { if (body != null) chunks.push(typeof body === 'string' ? new TextEncoder().encode(body) : new Uint8Array(body)); },
  };
  await handler({ url: new URL(request.url).pathname + new URL(request.url).search, method: request.method, headers: Object.fromEntries(request.headers) }, res);
  const size = chunks.reduce((n, c) => n + c.length, 0);
  const body = new Uint8Array(size);
  let at = 0;
  for (const c of chunks) { body.set(c, at); at += c.length; }
  return new Response(body, { status, headers });
}

/** Edge cache: a handler's "s-maxage" becomes how long Cloudflare keeps the answer, so FotMob is asked once per window. */
async function cached(request, ctx, make) {
  const cache = caches.default;
  const hit = await cache.match(request);
  if (hit) return hit;
  const res = await make();
  const cc = res.headers.get('Cache-Control') || '';
  const age = +(cc.match(/s-maxage=(\d+)/) || [])[1] || 0;
  if (res.status === 200 && age > 0) {
    const copy = new Response(res.clone().body, res);
    copy.headers.set('Cache-Control', `public, max-age=${age}`);
    ctx.waitUntil(cache.put(request, copy));
  }
  return res;
}

export default {
  // every ten minutes: the final scores of picked matches, for the league tables
  async scheduled(event, env, ctx) { ctx.waitUntil(settleAll(env)); },
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    // the prediction league: accounts, picks, leagues and their invite links
    if (url.pathname === '/privacy') return new Response(PRIVACY, { headers: { 'content-type': 'text/html; charset=utf-8' } });
    if (url.pathname.startsWith('/p/') || url.pathname.startsWith('/j/')) {
      try { return await league(request, env, url); } catch (e) { return Response.json({ error: String(e && e.message || e) }, { status: 500 }); }
    }
    if (url.pathname.startsWith('/t/')) {
      try { return await telemetry(request, env, url); } catch (e) { return new Response(String(e && e.message || e), { status: 500 }); }
    }
    const m = url.pathname.match(/^\/api\/([a-z]+)$/);
    if (url.pathname === '/v1' || url.pathname.startsWith('/v1/')) return api(request, ctx, url);
    if (!m) return Response.json({
      ...DOCS,
      raw: {
        'GET /api/fm?e=matches|matchDetails|ltc|tltable|leagues|teams|playerData|suggest|allLeagues&…': "FotMob's own answers, untouched",
        'GET /api/today?teams=<id,id>': "today's matches, followed teams first",
        'GET /api/widget?team=<id>': "a team's next/live match for widgets",
        'GET /api/hl?q=<text>': 'highlight videos (Aparat)',
        'GET /api/img?p=<path>': 'team/player images',
        'GET /api/tts?t=<text>&v=farid|dilara|ryan|sonia': 'speech (mp3)',
      },
    }, { status: url.pathname === '/' ? 200 : 404, headers: { 'Access-Control-Allow-Origin': '*' } });
    const name = m[1];
    let res;
    try {
      if (name === 'tts') res = await cached(request, ctx, () => tts(url));
      else if (ROUTES[name]) res = await cached(request, ctx, () => runNode(ROUTES[name], request));
      else res = Response.json({ error: 'not available on this server' }, { status: 404 });
    } catch (e) {
      res = Response.json({ error: String(e && e.message || e) }, { status: 502 });
    }
    res = new Response(res.body, res);
    res.headers.set('Access-Control-Allow-Origin', '*');
    return res;
  },
};

/** /v1: JSON with CORS, cached at the edge for as long as each answer stays fresh. */
async function api(request, ctx, url) {
  if (request.method === 'OPTIONS') return new Response(null, { headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET' } });
  const res = await cached(request, ctx, async () => {
    try {
      const { body, ttl } = await v1(url);
      return Response.json(body, { headers: { 'Cache-Control': `public, s-maxage=${ttl}` } });
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 502;
      return Response.json({ error: String(e?.message || e) }, { status });
    }
  });
  const out = new Response(res.body, res);
  out.headers.set('Access-Control-Allow-Origin', '*');
  return out;
}

/** The privacy policy (Persian, then English): what the optional account keeps and how to delete it. */
const PRIVACY = `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>حریم خصوصی گل‌لایو</title>
<style>body{margin:0 auto;max-width:720px;padding:20px 16px;background:#0B0F14;color:#E8EDF2;font:16px/1.9 Tahoma,sans-serif}h1{font-size:22px}h2{font-size:17px;margin-top:28px}.en{direction:ltr;text-align:left;border-top:1px solid #1F2733;margin-top:36px;padding-top:12px}</style></head><body>
<h1>حریم خصوصی گل‌لایو</h1>
<p>گل‌لایو بدون ورود هم کامل کار می‌کند. ورود اختیاری است و فقط برای لیگ پیش‌بینی و نگه داشتن اطلاعات اپ روی گوشی‌های دیگر به کار می‌رود.</p>
<h2>چه چیزی نگه می‌داریم</h2>
<p>اگر وارد شوی: نامی که در جدول نشان داده می‌شود، شماره‌ی موبایل (اگر با شماره وارد شوی) یا شناسه و ایمیل حساب گوگل (اگر با گوگل وارد شوی)، پیش‌بینی‌ها، لیگ‌هایی که عضو آن‌ها هستی و تنظیمات اپ مثل تیم‌های دنبال‌شده. همچنین آمار ناشناس استفاده از اپ، بدون نام یا شماره.</p>
<h2>چه کاری با آن می‌کنیم</h2>
<p>فقط برای ورود، جدول لیگ‌ها و برگرداندن اطلاعاتت روی گوشی دیگر. شماره برای فرستادن کد ورود به سرویس پیامک داده می‌شود. اطلاعاتت را نمی‌فروشیم و برای تبلیغ به کسی نمی‌دهیم.</p>
<h2>پاک کردن</h2>
<p>در اپ، بخش لیگ پیش‌بینی، «حذف حساب» را بزن تا حساب و همه‌ی اطلاعاتش از سرور پاک شود.</p>
<div class="en"><h1>GoalLive privacy policy</h1>
<p>GoalLive works fully without an account. Logging in is optional and is used only for the prediction league and to bring your app data to another phone.</p>
<h2>What we keep</h2><p>If you log in: the name shown in league tables, your mobile number (number login) or your Google account id and email (Google login), your predictions, the leagues you joined, and app settings such as followed teams. Also anonymous usage statistics with no name or number.</p>
<h2>How it is used</h2><p>Only for logging in, league tables and restoring your data on another phone. Your number is passed to the SMS provider to send the login code. We do not sell your data or share it for advertising.</p>
<h2>Deleting</h2><p>In the app, open the prediction league and tap "Delete account" to remove the account and all its data from the server.</p></div>
</body></html>`;
