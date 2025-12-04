const CACHE_NAME = "qr-code-cache-v1.2";
const urlsToCache = [
  "/elevator-qr",
  "/elevator-qr/index.html",
  "/elevator-qr/qr.png",
  "/elevator-qr/manifest.json",
  "/elevator-qr/sw1.1.js",
  "https://cdn.jsdelivr.net/npm/crypto-js@4.1.1/crypto-js.js",
  "https://cdn.jsdelivr.net/npm/qrious@4.0.2/dist/qrious.min.js",
];

// Install event - Cache static assets
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(urlsToCache);
    })
  );
  // Force the waiting service worker to become the active service worker.
  self.skipWaiting();
});

// Activate event - Clean up old caches
self.addEventListener("activate", (event) => {
  const cacheWhitelist = [CACHE_NAME];
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheWhitelist.indexOf(cacheName) === -1) {
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
  // Tell the active service worker to take control of the page immediately.
  event.waitUntil(self.clients.claim());
});

// Fetch event - Network First for HTML, Cache First for assets
self.addEventListener("fetch", (event) => {
  const requestUrl = new URL(event.request.url);

  // Check if the request is for the main HTML file (navigation or explicitly index.html)
  // or if it's a navigation request.
  if (event.request.mode === 'navigate' || requestUrl.pathname.endsWith('index.html') || requestUrl.pathname.endsWith('/elevator-qr/')) {
     event.respondWith(
      fetch(event.request)
        .then((response) => {
          // If network fetch is successful, update the cache
          if (!response || response.status !== 200 || response.type !== 'basic') {
            return response;
          }
          const responseToCache = response.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseToCache);
          });
          return response;
        })
        .catch(() => {
          // If network fails, try cache
          return caches.match(event.request);
        })
    );
  } else {
    // For everything else (assets), use Cache First (Stale-While-Revalidate could also be used,
    // but user said "local cache never expires" which implies strong caching)
    // However, to satisfy "update immediately", we might want Stale-While-Revalidate for JS/CSS too?
    // User said "if no update, rely on local cache".
    // Let's stick to Cache First for assets to save data/speed, as versioned assets usually have new names or query strings.
    // But here we are overwriting files. So Stale-While-Revalidate is safer for assets if filenames don't change.
    // Given the user constraint "update immediately", Network First is safest but slow.
    // Let's use Stale-While-Revalidate for assets?
    // Actually, the simplest interpretation of "update immediately" is ensuring the *App Logic* (HTML/JS) is new.
    // If I use Network First for HTML, the new HTML will request new assets (if I versioned them).
    // I didn't version the JS libs (CDNs are versioned).
    // I didn't version `qr.png`.
    // So if `qr.png` changes, we might have an issue with Cache First.
    // But `sw1.1.js` logic was `Cache First`.
    // I will switch to `Stale-While-Revalidate` for non-HTML requests to ensure updates eventually propagate,
    // OR keep Cache First but rely on SW version bump to clear old cache (which I did in `activate`).
    // Since I bump `CACHE_NAME` to `v1.2`, the `activate` event clears `v1.1`.
    // So the next load *will* re-cache everything.
    // So Cache First is fine provided we bump SW version.

    event.respondWith(
      caches.match(event.request).then((response) => {
        return response || fetch(event.request);
      })
    );
  }
});
