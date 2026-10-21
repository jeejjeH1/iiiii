// Offline shell: app files are network-first (always fresh when online) with a cached fallback.
// API responses are never cached here; the edge cache in front of /api handles that.
const CACHE = 'goallive-v7';
const SHELL = ['/', '/assets/app.css', '/assets/app.js', '/assets/core.js', '/assets/match.js', '/assets/pages.js', '/assets/fa.js', '/assets/clubs.js', '/assets/motion.js', '/assets/story.js', '/assets/extras.js', '/assets/themes.js', '/icon.svg', '/icons/icon-192.png', '/manifest.webmanifest'];

self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  e.respondWith(fetch(e.request).then((r) => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return r;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match('/'))));
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
