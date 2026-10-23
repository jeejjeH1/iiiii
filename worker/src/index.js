// GoalLive server on Cloudflare Workers.
// /api/* runs the same handlers as the Vercel functions in ../api (they are written for Node's req/res, adapted below);
// everything else is the website from ./public (copied there at deploy time).
import fm from '../../api/fm.js';
import today from '../../api/today.js';
import widget from '../../api/widget.js';
import hl from '../../api/hl.js';
import img from '../../api/img.js';
import { tts } from './tts.js';

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
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const m = url.pathname.match(/^\/api\/([a-z]+)$/);
    if (!m) return env.ASSETS.fetch(request);
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
