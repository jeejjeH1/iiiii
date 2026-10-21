// Highlight videos from Aparat (public search API; videos play through Aparat's official embed player).
// GET /api/hl?q=<search text>&must=<name1>|<name2>   ->  { videos: [{ uid, title, poster, duration, channel, embed }] }
const UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36';

async function getJson(url) {
  const r = await fetch(url, { headers: { 'user-agent': UA, accept: 'application/json' }, signal: AbortSignal.timeout(8000) });
  if (!r.ok) throw new Error('Aparat ' + r.status);
  return r.json();
}
// Aparat has two API generations; accept either shape.
async function search(q) {
  const enc = encodeURIComponent(q);
  try {
    const d = await getJson(`https://www.aparat.com/etc/api/videoBySearch/text/${enc}/perpage/20`);
    const list = d.videobysearch || [];
    if (list.length) return list.map((v) => ({ uid: v.uid, title: v.title, poster: v.big_poster || v.small_poster, duration: +v.duration || 0, channel: v.sender_name || v.username, official: !!+v.official }));
  } catch {}
  const d = await getJson(`https://api.aparat.com/fa/v1/video/video/search/text/${enc}`);
  return (d.data || []).map((x) => x.attributes || x).filter((v) => v.uid).map((v) => ({
    uid: v.uid, title: v.title, poster: v.big_poster || v.small_poster || v.preview_src, duration: +v.duration || 0, channel: v.sender_name || v.username, official: !!+v.official,
  }));
}

const norm = (s) => String(s || '').toLowerCase().replace(/[‌\s]+/g, ' ').replace(/ي/g, 'ی').replace(/ك/g, 'ک');

module.exports = async (req, res) => {
  const u = new URL(req.url, 'http://x');
  const q = (u.searchParams.get('q') || '').slice(0, 120);
  const must = (u.searchParams.get('must') || '').split('|').map(norm).filter(Boolean).slice(0, 4);
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (!q) { res.statusCode = 400; return res.end('{"error":"q"}'); }
  try {
    const all = await search(q);
    // keep clips that mention one of the teams, prefer ones that look like highlights and come from official channels
    const score = (v) => {
      const t = norm(v.title);
      const hits = must.filter((m) => m.split(' ').some((w) => w.length > 2 && t.includes(w))).length;
      return hits * 10 + (/خلاصه|گل|highlight/.test(t) ? 5 : 0) + (v.official ? 3 : 0) + (v.duration > 60 && v.duration < 1500 ? 2 : 0);
    };
    const videos = all.filter((v) => !must.length || score(v) >= 10).sort((a, b) => score(b) - score(a)).slice(0, 8)
      .map((v) => ({ ...v, embed: `https://www.aparat.com/video/video/embed/videohash/${v.uid}/vt/frame` }));
    res.setHeader('Cache-Control', 'public, s-maxage=1800, stale-while-revalidate=7200');
    res.end(JSON.stringify({ videos }));
  } catch (e) {
    res.statusCode = 502;
    res.end(JSON.stringify({ error: e.message, videos: [] }));
  }
};
