'use strict';

// Service worker: aplikacija se moze instalirati i otvoriti bez mreze.
// Strategija: mreza prvo (uvijek svjez sadrzaj), kes kao rezerva kad mreze nema.
// API pozivi se nikad ne kesiraju.

const CACHE_NAME = 'pfm-v1';

const APP_SHELL = [
    '/',
    '/css/app.css',
    '/css/landing.css',
    '/js/app.js',
    '/js/vendor/lottie_light.min.js',
    '/img/wallet.json',
    '/img/placanje.jpg',
    '/img/demo-poster.jpg',
    '/img/icon-192.png',
    '/img/icon-512.png',
    '/manifest.json'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(APP_SHELL))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);

    // Kesiraju se samo GET zahtjevi sa naseg domena, i nikad API
    if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
        return;
    }

    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request).then(cached =>
                cached || (request.mode === 'navigate' ? caches.match('/') : Response.error())
            ))
    );
});
