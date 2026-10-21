// Caching strategy (tuned for slow connections):
//  - app code (html/js/css): network first so all modules stay on the same version, cache after 2.5s or offline
//  - fonts & icons: cache first (they never change)
//  - team/player images from FotMob: cache-first, kept for later visits
//  - /api/*: always network (live data), never cached here
const CACHE = 'goallive-v8';
const IMG_CACHE = 'goallive-img-v1';
const SHELL = ['/', '/assets/app.css', '/assets/app.js', '/assets/core.js', '/assets/match.js', '/assets/pages.js', '/assets/fa.js', '/assets/clubs.js',
  '/assets/motion.js', '/assets/story.js', '/assets/extras.js', '/assets/themes.js', '/fonts/vazirmatn-arabic-wght-normal.woff2', '/fonts/vazirmatn-latin-wght-normal.woff2',
  '/fonts/barlow-condensed-latin-700-normal.woff2', '/icon.svg', '/icons/icon-192.png', '/manifest.webmanifest'];

self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== IMG_CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

async function networkFirst(req) {
  const cache = await caches.open(CACHE);
  const fresh = fetch(req).then((r) => { if (r.ok) cache.put(req, r.clone()); return r; });
  const slow = new Promise((ok) => setTimeout(ok, 2500)).then(() => cache.match(req, { ignoreSearch: true }));
  try {
    return (await Promise.race([fresh, slow.then((hit) => hit || fresh)])) || (await fresh);
  } catch {
    return (await cache.match(req, { ignoreSearch: true })) || caches.match('/');
  }
}
async function cacheFirst(req) {
  const hit = await caches.match(req, { ignoreSearch: true });
  if (hit) return hit;
  const r = await fetch(req);
  if (r.ok) (await caches.open(CACHE)).put(req, r.clone());
  return r;
}
async function imageCacheFirst(req) {
  const cache = await caches.open(IMG_CACHE);
  const hit = await cache.match(req);
  if (hit) return hit;
  const r = await fetch(req);
  if (r.ok || r.type === 'opaque') {
    cache.put(req, r.clone());
    cache.keys().then((keys) => { if (keys.length > 200) keys.slice(0, 50).forEach((k) => cache.delete(k)); }); // keep it bounded
  }
  return r;
}

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  if (url.hostname === 'images.fotmob.com') { e.respondWith(imageCacheFirst(e.request).catch(() => fetch(e.request))); return; }
  if (url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  e.respondWith(/^\/(fonts|icons)\//.test(url.pathname) ? cacheFirst(e.request) : networkFirst(e.request));
});

// Tapping a goal / lineup notification opens (or focuses) the match page.
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = e.notification.data?.url || '/';
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    const win = list.find((c) => new URL(c.url).origin === location.origin);
    if (win) return win.focus().then(() => win.navigate(url));
    return self.clients.openWindow(url);
  }));
});

// Server push (goals, lineups, kick-off/full-time for followed teams). If the app is open and focused it
// already shows these in-page, so only notify when nobody is looking.
self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data.json(); } catch { d = { title: 'گل‌لایو', body: e.data?.text() || '' }; }
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    if (list.some((c) => c.focused && c.visibilityState === 'visible') && d.tag !== 'test') return;
    return self.registration.showNotification(d.title || 'گل‌لایو', {
      body: d.body || '', tag: d.tag, renotify: true, icon: '/icons/icon-192.png', badge: '/icons/icon-192.png',
      dir: 'rtl', lang: 'fa', vibrate: [120, 60, 120], data: { url: d.url || '/' },
    });
  }));
});
