// Shared FotMob fetch used by the proxy (api/fm.js) and the push poller (api/push.js).
async function fotmob(path, params = {}) {
  const r = await fetch(`https://www.fotmob.com/api/${path}?${new URLSearchParams(params)}`, {
    headers: {
      'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36',
      accept: 'application/json',
      referer: 'https://www.fotmob.com/',
      ...(process.env.FOTMOB_XMAS && { 'x-mas': process.env.FOTMOB_XMAS }),
    },
    signal: AbortSignal.timeout(10000),
  });
  if (!r.ok) throw new Error('FotMob responded ' + r.status);
  return r.text();
}
module.exports = { fotmob };
