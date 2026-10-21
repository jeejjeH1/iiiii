// Same-origin copy of FotMob images so the share-card canvas can draw them (their CDN sends no CORS headers).
// GET /api/img?p=logo/teamlogo/8633.png
const ALLOWED = /^(logo\/teamlogo\/\d{1,9}(_small)?|logo\/leaguelogo\/(dark\/)?\d{1,9}|playerimages\/\d{1,9})\.png$/;

module.exports = async (req, res) => {
  const p = new URL(req.url, 'http://x').searchParams.get('p') || '';
  if (!ALLOWED.test(p)) { res.statusCode = 400; return res.end(); }
  try {
    const r = await fetch(`https://images.fotmob.com/image_resources/${p}`, { signal: AbortSignal.timeout(8000) });
    if (!r.ok) { res.statusCode = r.status; return res.end(); }
    res.statusCode = 200;
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=86400, s-maxage=604800');
    res.end(Buffer.from(await r.arrayBuffer()));
  } catch {
    res.statusCode = 502; res.end();
  }
};
