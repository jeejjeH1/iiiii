// Vercel serverless function: proxies FotMob (no CORS for browsers) with CDN caching.
module.exports = async (req, res) => {
  const date = String(req.query.date || '').replace(/\D/g, '');
  if (!/^\d{8}$/.test(date)) return res.status(400).json({ error: 'date=YYYYMMDD' });
  const q = new URLSearchParams({ date, timezone: String(req.query.tz || 'UTC'), ccode3: 'IRN' });
  try {
    const r = await fetch('https://www.fotmob.com/api/data/matches?' + q, {
      headers: {
        'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36',
        accept: 'application/json',
        referer: 'https://www.fotmob.com/',
        ...(process.env.FOTMOB_XMAS && { 'x-mas': process.env.FOTMOB_XMAS }),
      },
      signal: AbortSignal.timeout(10000),
    });
    if (!r.ok) throw new Error('FotMob responded ' + r.status);
    // shared edge cache: ~15s fresh, then serve stale while revalidating
    res.setHeader('Cache-Control', 's-maxage=15, stale-while-revalidate=60');
    res.setHeader('Content-Type', 'application/json');
    res.status(200).send(await r.text());
  } catch (e) {
    res.status(502).json({ error: e.message });
  }
};
