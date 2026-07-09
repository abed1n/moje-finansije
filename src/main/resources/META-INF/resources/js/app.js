'use strict';

// Stanje i pomocne funkcije
const state = {
    token: localStorage.getItem('pfm_token'),
    user: JSON.parse(localStorage.getItem('pfm_user') || 'null'),
    accounts: [],
    categories: []
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

// Token iz linka za reset lozinke (?reset=...), postavlja se pri inicijalizaciji
let resetToken = null;
// Poruka o ishodu potvrde emaila, prikazuje se nakon inicijalizacije
let verifyNotice = null;
// Google client id sa servera (null ako Google prijava nije podesena)
let googleClientId = null;
let googleReady = false;
// Da li server trazi potvrdu emaila prije prijave (podrazumijevano da)
let verificationRequired = true;

function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

function fmtMoney(value, currency = 'EUR') {
    const n = Number(value || 0);
    return n.toLocaleString('sr-ME', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + currency;
}

function fmtDate(iso) {
    if (!iso) return '-';
    const [y, m, d] = iso.split('-');
    return `${d}.${m}.${y}.`;
}

function fmtMonth(ym) {
    const names = ['jan', 'feb', 'mar', 'apr', 'maj', 'jun', 'jul', 'avg', 'sep', 'okt', 'nov', 'dec'];
    const [y, m] = ym.split('-').map(Number);
    return `${names[m - 1]} ${String(y).slice(2)}.`;
}

const ACCOUNT_TYPES = {
    CHECKING: 'Tekući račun',
    SAVINGS: 'Štednja',
    CASH: 'Gotovina',
    CREDIT_CARD: 'Kreditna kartica'
};

const PERIODS = { MONTHLY: 'Mjesečno', YEARLY: 'Godišnje' };

// SVG ikone (stroke, 24x24)
const ICONS = {
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    edit: '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
    trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
    clip: '<path d="M13.234 20.252 21 12.3a6 6 0 0 0-8.486-8.486L4.75 11.5a4 4 0 0 0 5.656 5.656l7.03-7.03a2 2 0 0 0-2.829-2.828l-7.03 7.03"/>',
    wallet: '<path d="M21 12V7H5a2 2 0 0 1 0-4h14v4"/><path d="M3 5v14a2 2 0 0 0 2 2h16v-5"/><path d="M18 12a2 2 0 0 0 0 4h4v-4Z"/>',
    trendUp: '<path d="M16 7h6v6"/><path d="m22 7-8.5 8.5-5-5L2 17"/>',
    trendDown: '<path d="M16 17h6v-6"/><path d="m22 17-8.5-8.5-5 5L2 7"/>',
    scale: '<path d="M12 3v18"/><path d="M8 21h8"/><path d="m5 7 3 7a3 3 0 0 1-6 0z"/><path d="m19 7 3 7a3 3 0 0 1-6 0z"/><path d="M4 7h16"/>',
    repeat: '<path d="m17 2 4 4-4 4"/><path d="M3 11v-1a4 4 0 0 1 4-4h14"/><path d="m7 22-4-4 4-4"/><path d="M21 13v1a4 4 0 0 1-4 4H3"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
    upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/>',
    moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
    chevLeft: '<path d="m15 18-6-6 6-6"/>',
    chevRight: '<path d="m9 18 6-6-6-6"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    tagIco: '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5" fill="currentColor"/>',
    tools: '<path d="M12 3v3"/><path d="m6.6 6.6 2.1 2.1"/><path d="M3 12h3"/><path d="M12 18v3"/><path d="m17.4 6.6-2.1 2.1"/><path d="M18 12h3"/><circle cx="12" cy="12" r="4"/>',
    user: '<circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 0 0-16 0"/>',
    shield: '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
    transfer: '<path d="m16 3 4 4-4 4"/><path d="M20 7H4"/><path d="m8 21-4-4 4-4"/><path d="M4 17h16"/>',
    target: '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>',
    flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><path d="M4 22v-7"/>'
};

function icon(name, cls = 'ico') {
    return `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name]}</svg>`;
}

// API klijent
async function api(path, options = {}, retried = false) {
    const headers = options.headers || {};
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
    let body = options.body;
    if (body && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
    }
    const res = await fetch(path, { method: options.method || 'GET', headers, body });
    if (res.status === 401 && state.token) {
        // Pristupni token je vjerovatno istekao - probaj ga jednom tiho obnoviti pa ponovi zahtjev
        if (!retried && await refreshAccessToken()) {
            return api(path, { ...options, headers: {} }, true);
        }
        logout();
        throw new Error('Sesija je istekla, prijavite se ponovo');
    }
    if (!res.ok) {
        let message = 'Greška (' + res.status + ')';
        try {
            const data = await res.json();
            if (data.error) message = data.error;
            if (data.details && data.details.length) message += ': ' + data.details.join(', ');
        } catch (ignored) { /* nije JSON */ }
        const error = new Error(message);
        error.status = res.status;
        throw error;
    }
    if (res.status === 204) return null;
    return res.json();
}

// Dobavlja novi pristupni token pomocu refresh kolacica; vraca true ako je uspjelo
async function refreshAccessToken() {
    try {
        const res = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' });
        if (!res.ok) return false;
        const auth = await res.json();
        saveAuth(auth);
        return true;
    } catch (ignored) {
        return false;
    }
}

// Toast + modal
function toast(message, type = 'success') {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 4200);
}

function openModal(title, bodyHtml, wide = false) {
    $('#modal').classList.toggle('wide', !!wide);
    $('#modal-title').textContent = title;
    $('#modal-body').innerHTML = bodyHtml;
    $('#modal-backdrop').classList.remove('hidden');
    return $('#modal-body');
}

function closeModal() {
    $('#modal-backdrop').classList.add('hidden');
    $('#modal-body').innerHTML = '';
}

$('#modal-close').addEventListener('click', closeModal);
$('#modal-backdrop').addEventListener('click', e => {
    if (e.target === $('#modal-backdrop')) closeModal();
});

// Potvrda u stilu aplikacije (umjesto browserskog confirm dijaloga).
// Zivi u vlastitom sloju pa radi i preko otvorenog modala.
function confirmDialog({ title = 'Potvrda', message, confirmText = 'Obriši' }) {
    return new Promise(resolve => {
        const overlay = document.createElement('div');
        overlay.className = 'confirm-backdrop';
        overlay.innerHTML = `
            <div class="confirm-box" role="alertdialog" aria-modal="true" aria-label="${esc(title)}">
                <h3>${esc(title)}</h3>
                <p>${esc(message)}</p>
                <div class="confirm-actions">
                    <button class="btn btn-secondary" data-r="0" type="button">Odustani</button>
                    <button class="btn btn-danger-solid" data-r="1" type="button">${esc(confirmText)}</button>
                </div>
            </div>`;

        const done = result => {
            document.removeEventListener('keydown', onKey);
            overlay.remove();
            resolve(result);
        };
        const onKey = e => { if (e.key === 'Escape') done(false); };

        overlay.addEventListener('click', e => { if (e.target === overlay) done(false); });
        overlay.querySelectorAll('button').forEach(btn =>
            btn.addEventListener('click', () => done(btn.dataset.r === '1')));
        document.addEventListener('keydown', onKey);
        document.body.appendChild(overlay);
        overlay.querySelector('[data-r="0"]').focus();
    });
}

$('#bell-btn').addEventListener('click', openAlertsModal);
$('#theme-btn').addEventListener('click', toggleTheme);
$('#mobile-more').addEventListener('click', openMoreSheet);

// Centar obavjestenja: podsjetnik za izvod, budzeti 80%+, rokovi ciljeva, racuni u minusu
let notifications = [];

const MONTH_NAMES = ['januar', 'februar', 'mart', 'april', 'maj', 'jun',
    'jul', 'avgust', 'septembar', 'oktobar', 'novembar', 'decembar'];

async function refreshAlerts() {
    if (!state.token) return;
    try {
        const [budgets, goals, accounts, importStatus] = await Promise.all([
            api('/api/budgets'), api('/api/goals'), api('/api/accounts'), api('/api/import/status')
        ]);
        notifications = [];

        // 1. Podsjetnik za izvod: nijedan uvoz u tekucem mjesecu
        const now = new Date();
        const last = importStatus.lastImportAt ? new Date(importStatus.lastImportAt) : null;
        const importedThisMonth = last
            && last.getMonth() === now.getMonth() && last.getFullYear() === now.getFullYear();
        if (accounts.length && !importedThisMonth) {
            const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
            notifications.push({
                kind: 'statement', icon: 'upload',
                title: 'Vrijeme je za izvod',
                text: `Uvezite bankovni izvod za ${MONTH_NAMES[prev.getMonth()]} — kategorije se popune same.`,
                action: 'import'
            });
        }

        // 2. Budzeti preko 80% limita
        budgets.filter(b => b.percentUsed >= 80)
            .sort((a, b) => b.percentUsed - a.percentUsed)
            .forEach(b => {
                const over = b.percentUsed >= 100;
                notifications.push({
                    kind: over ? 'over' : 'warn', icon: 'target',
                    title: `${b.name} — ${b.percentUsed}%`,
                    text: over
                        ? `Limit je probijen: ${fmtMoney(b.spent)} od ${fmtMoney(b.limitAmount)}.`
                        : `Blizu limita: ${fmtMoney(b.spent)} od ${fmtMoney(b.limitAmount)}.`,
                    action: 'budgets'
                });
            });

        // 3. Ciljevi kojima istice rok
        goals.filter(g => !g.achieved && g.deadline).forEach(g => {
            const days = Math.ceil((new Date(g.deadline) - now) / 86400000);
            if (days < 0) {
                notifications.push({
                    kind: 'over', icon: 'flag',
                    title: `${g.name} — rok je istekao`,
                    text: `Do cilja je nedostajalo još ${fmtMoney(g.remaining)}.`,
                    action: 'budgets'
                });
            } else if (days <= 14) {
                notifications.push({
                    kind: 'warn', icon: 'flag',
                    title: `${g.name} — još ${days} ${days === 1 ? 'dan' : 'dana'}`,
                    text: `Do cilja nedostaje ${fmtMoney(g.remaining)}.`,
                    action: 'budgets'
                });
            }
        });

        // 4. Racuni u minusu
        accounts.filter(a => Number(a.balance) < 0).forEach(a => {
            notifications.push({
                kind: 'over', icon: 'wallet',
                title: `${a.name} je u minusu`,
                text: `Trenutno stanje: ${fmtMoney(a.balance, a.currency)}.`,
                action: 'accounts'
            });
        });

        const badge = $('#bell-badge');
        badge.textContent = notifications.length;
        badge.classList.toggle('hidden', notifications.length === 0);
    } catch (ignored) { /* obavjestenja nisu kriticna za rad aplikacije */ }
}

function openAlertsModal() {
    const rows = notifications.map((n, i) => `
        <button class="notif-row ${n.kind}" data-i="${i}" type="button">
            <span class="notif-ico">${icon(n.icon)}</span>
            <span class="notif-body">
                <b>${esc(n.title)}</b>
                <span>${esc(n.text)}</span>
            </span>
            ${icon('chevRight', 'ico ico-sm notif-go')}
        </button>`).join('');

    const body = openModal('Obavještenja', notifications.length
        ? rows
        : `<div class="alert-ok"><svg class="ico" viewBox="0 0 24 24">${ICONS.check}</svg>Sve je pod kontrolom — nema novih obavještenja.</div>`);

    $$('.notif-row', body).forEach(row => row.addEventListener('click', () => {
        const n = notifications[Number(row.dataset.i)];
        closeModal();
        if (n.action === 'import') {
            state.openImport = true;
            if (location.hash === '#/transactions') route();
            else location.hash = '#/transactions';
        } else {
            location.hash = '#/' + n.action;
        }
    }));
}

// "Jos" sheet na mobilnoj navigaciji
function openMoreSheet() {
    const isDark = localStorage.getItem(THEME_KEY) === 'dark';
    const body = openModal('Još', `<div class="more-grid">
        <button class="more-item" data-nav="categories" type="button">${icon('tagIco')}Kategorije</button>
        <button class="more-item" data-nav="tools" type="button">${icon('tools')}Alati</button>
        <button class="more-item" data-nav="profile" type="button">${icon('user')}Profil</button>
        ${state.user && state.user.role === 'ADMIN'
            ? `<button class="more-item" data-nav="admin" type="button">${icon('shield')}Administracija</button>` : ''}
        <button class="more-item" id="more-theme" type="button">${icon(isDark ? 'sun' : 'moon')}${isDark ? 'Svijetla tema' : 'Tamna tema'}</button>
        <button class="more-item danger" id="more-logout" type="button">${icon('logout')}Odjava</button>
    </div>`);

    $$('.more-item[data-nav]', body).forEach(btn => btn.addEventListener('click', () => {
        closeModal();
        location.hash = '#/' + btn.dataset.nav;
    }));
    $('#more-theme', body).addEventListener('click', () => { closeModal(); toggleTheme(); });
    $('#more-logout', body).addEventListener('click', () => { closeModal(); logout(); });
}

// Autentifikacija
function saveAuth(auth) {
    state.token = auth.token;
    state.user = auth.user;
    localStorage.setItem('pfm_token', auth.token);
    localStorage.setItem('pfm_user', JSON.stringify(auth.user));
}

function logout() {
    // Opozovi refresh token na serveru (kolacic se salje automatski); ne blokiramo odjavu
    fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).catch(() => {});
    state.token = null;
    state.user = null;
    localStorage.removeItem('pfm_token');
    localStorage.removeItem('pfm_user');
    showAuth();
}

let landingReady = false;

function showAuth() {
    $('#main-layout').classList.add('hidden');
    $('#auth-screen').classList.remove('hidden');
    applyTheme(null); // landing ima svoj fiksni dizajn, tamna tema vazi samo u aplikaciji
    if (!landingReady) {
        landingReady = true;
        initLanding();
    }
    if (googleClientId) {
        setupGoogleSignIn();
        $('#google-auth').classList.remove('hidden'); // login je podrazumijevani mod
    }
    // "Zaboravili ste lozinku?" ima smisla samo ako slanje emaila radi (kad je verifikacija ukljucena)
    $('#forgot-link').classList.toggle('hidden', !verificationRequired);
}

// Google Identity Services: ucitava skriptu i iscrtava dugme ako je client id podesen
function setupGoogleSignIn() {
    if (!googleClientId || googleReady) return;
    const render = () => {
        if (!window.google || !google.accounts || !google.accounts.id) return;
        google.accounts.id.initialize({ client_id: googleClientId, callback: onGoogleCredential });
        google.accounts.id.renderButton($('#google-btn'),
            { theme: 'outline', size: 'large', width: 300, text: 'continue_with' });
        googleReady = true;
    };
    if (window.google && window.google.accounts) { render(); return; }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = render;
    document.head.appendChild(script);
}

// Google dugme crta sam Google, pa mu dodajemo poseban loading prekrivac radi doslednosti
function showAuthLoading(on) {
    $('#auth-loading').classList.toggle('hidden', !on);
}

async function onGoogleCredential(response) {
    showAuthLoading(true);
    try {
        const auth = await api('/api/auth/google', {
            method: 'POST', body: { idToken: response.credential }
        });
        saveAuth(auth);
        showApp();
    } catch (err) {
        showAuthError(err.message);
    } finally {
        showAuthLoading(false);
    }
}

// Landing stranica: dugmad, brojaci, scroll-reveal, marquee
function countUp(el) {
    const target = Number(el.dataset.count);
    const suffix = el.dataset.suffix || '';
    const format = value => value.toLocaleString('sr-ME') + suffix;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || target === 0) {
        el.textContent = format(target);
        return;
    }
    const start = performance.now();
    const ease = t => 1 - Math.pow(1 - t, 3);
    const tick = now => {
        const p = Math.min((now - start) / 1400, 1);
        el.textContent = format(Math.round(target * ease(p)));
        if (p < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
}

function initLanding() {
    const goToForm = tab => {
        switchAuthTab(tab);
        $('#auth-panel').scrollIntoView({ behavior: 'smooth', block: 'center' });
    };
    $('#nav-open').addEventListener('click', () => goToForm('register'));
    $('#nav-brand').addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

    // Skrolanje do sekcija (nav, footer i CTA dugmad sa data-target)
    $$('.lp-scroll[data-target]').forEach(btn => btn.addEventListener('click', () =>
        $('#' + btn.dataset.target).scrollIntoView({ behavior: 'smooth', block: 'start' })));

    // "Probaj demo" - popuni demo kredencijale i odmah prijavi
    const startDemo = () => {
        const form = $('#login-form');
        form.elements.email.value = 'demo@pfm.me';
        form.elements.password.value = 'demo123';
        form.requestSubmit();
    };
    $('#cta-demo').addEventListener('click', startDemo);
    $('#cta-demo-bottom').addEventListener('click', startDemo);
    $('#cta-open-bottom').addEventListener('click', () => goToForm('register'));

    // Marquee: dupliraj sadrzaj da petlja bude besavna
    const track = $('#marquee-track');
    track.innerHTML += track.innerHTML;

    // Brojaci u sekcijama krecu tek kad sekcija dodje u kadar
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.classList.add('in');
            $$('b[data-count]', entry.target).forEach(countUp);
            observer.unobserve(entry.target);
        }
    }), { threshold: .16 });
    $$('#auth-screen .reveal').forEach(el => observer.observe(el));

    // Demo video: zvuk na klik, pauza kad izadje iz kadra
    const demoVideo = $('#demo-video');
    if (demoVideo) {
        const soundBtn = $('#demo-sound');
        const iconMuted = '<path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"/><path d="m22 9-6 6"/><path d="m16 9 6 6"/>';
        const iconOn = '<path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"/><path d="M16 9a5 5 0 0 1 0 6"/><path d="M19.364 18.364a9 9 0 0 0 0-12.728"/>';
        soundBtn.addEventListener('click', () => {
            demoVideo.muted = !demoVideo.muted;
            soundBtn.querySelector('svg').innerHTML = demoVideo.muted ? iconMuted : iconOn;
            soundBtn.querySelector('span').textContent = demoVideo.muted ? 'Uključi zvuk' : 'Isključi zvuk';
            if (!demoVideo.muted && demoVideo.paused) demoVideo.play();
        });

        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            demoVideo.removeAttribute('autoplay');
            demoVideo.setAttribute('controls', '');
            demoVideo.pause();
        } else {
            new IntersectionObserver(entries => entries.forEach(entry => {
                if (entry.isIntersecting) { demoVideo.play().catch(() => {}); }
                else { demoVideo.pause(); }
            }), { threshold: .25 }).observe(demoVideo);
        }
    }

    // Zivi konverter valuta: kursna tabela se ucita jednom (Frankfurter/ECB),
    // a konverzija se racuna lokalno pa je rezultat trenutan dok korisnik kuca
    const lcAmount = $('#lc-amount');
    if (lcAmount) {
        const lcFrom = $('#lc-from');
        const lcTo = $('#lc-to');
        const lcResult = $('#lc-result');
        const lcSwap = $('#lc-swap');
        const codes = ['EUR', 'USD', 'GBP', 'CHF', 'JPY', 'CAD', 'AUD', 'SEK', 'NOK', 'PLN', 'CZK', 'DKK'];
        lcFrom.innerHTML = codes.map(c => `<option ${c === 'EUR' ? 'selected' : ''}>${c}</option>`).join('');
        lcTo.innerHTML = codes.map(c => `<option ${c === 'USD' ? 'selected' : ''}>${c}</option>`).join('');

        let rates = null; // kursevi prema EUR

        function convert() {
            if (!rates) return;
            const amount = Number(lcAmount.value);
            if (!amount || amount <= 0) { lcResult.innerHTML = '&nbsp;'; return; }
            const value = amount * rates[lcTo.value] / rates[lcFrom.value];
            lcResult.textContent = value.toLocaleString('sr-ME',
                { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + lcTo.value;
        }

        async function loadRates() {
            try {
                const res = await fetch('https://api.frankfurter.dev/v1/latest?base=EUR');
                if (!res.ok) throw new Error();
                const data = await res.json();
                rates = { EUR: 1, ...data.rates };
                convert();
            } catch (ignored) {
                lcResult.textContent = 'Kursevi nisu dostupni';
            }
        }

        [lcAmount, lcFrom, lcTo].forEach(el => el.addEventListener('input', convert));
        lcSwap.addEventListener('click', () => {
            const from = lcFrom.value;
            lcFrom.value = lcTo.value;
            lcTo.value = from;
            lcSwap.classList.toggle('flip');
            convert();
        });
        loadRates();
    }

    // Lottie animacija novcanika u bento kartici
    if (window.lottie && $('#wallet-anim')) {
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const walletAnim = lottie.loadAnimation({
            container: $('#wallet-anim'),
            renderer: 'svg',
            loop: true,
            autoplay: false,
            path: 'img/wallet.json'
        });
        if (reducedMotion) {
            walletAnim.addEventListener('DOMLoaded', () =>
                walletAnim.goToAndStop(walletAnim.totalFrames - 1, true));
        } else {
            // Pusti animaciju samo dok je vidljiva u kadru
            new IntersectionObserver(entries => entries.forEach(entry =>
                entry.isIntersecting ? walletAnim.play() : walletAnim.pause()
            ), { threshold: .2 }).observe($('#wallet-anim'));
        }
    }

}

function showApp() {
    $('#auth-screen').classList.add('hidden');
    $('#main-layout').classList.remove('hidden');
    applyTheme(localStorage.getItem(THEME_KEY));
    refreshAlerts();
    const user = state.user;
    $('#user-name').textContent = user.name;
    $('#user-email').textContent = user.email;
    $('#user-avatar').textContent = user.name.trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
    $('#nav-admin').classList.toggle('hidden', user.role !== 'ADMIN');
    if (!location.hash || location.hash === '#/') location.hash = '#/dashboard';
    route();
}

function showAuthError(message) {
    $('#auth-success').classList.add('hidden');
    const el = $('#auth-error');
    el.textContent = message;
    el.classList.remove('hidden');
}

function showAuthSuccess(message) {
    $('#auth-error').classList.add('hidden');
    const el = $('#auth-success');
    el.textContent = message;
    el.classList.remove('hidden');
}

$('#tab-login').addEventListener('click', () => switchAuthTab('login'));
$('#tab-register').addEventListener('click', () => switchAuthTab('register'));

// Prikazuje jednu od formi (login/register/forgot/reset) i sakriva ostale.
// Kartice Prijava/Registracija se vide samo za login i register.
function switchAuthTab(mode) {
    const tabsVisible = mode === 'login' || mode === 'register';
    $('#tab-login').classList.toggle('active', mode === 'login');
    $('#tab-register').classList.toggle('active', mode === 'register');
    $('.auth-tabs').classList.toggle('hidden', !tabsVisible);
    $('#login-form').classList.toggle('hidden', mode !== 'login');
    $('#register-form').classList.toggle('hidden', mode !== 'register');
    $('#forgot-form').classList.toggle('hidden', mode !== 'forgot');
    $('#reset-form').classList.toggle('hidden', mode !== 'reset');
    $('#auth-error').classList.add('hidden');
    $('#auth-success').classList.add('hidden');
    $('#auth-resend').classList.add('hidden');
    // Google dugme ima smisla samo uz prijavu i registraciju
    $('#google-auth').classList.toggle('hidden', !(googleClientId && tabsVisible));
}

$('#forgot-link').addEventListener('click', () => switchAuthTab('forgot'));
$('#forgot-back').addEventListener('click', () => switchAuthTab('login'));

$('#forgot-form').addEventListener('submit', async e => {
    e.preventDefault();
    const email = new FormData(e.target).get('email');
    try {
        await api('/api/auth/forgot-password', { method: 'POST', body: { email } });
        switchAuthTab('login');
        showAuthSuccess('Ako nalog postoji, poslali smo link za novu lozinku na email.');
    } catch (err) {
        showAuthError(err.message);
    }
});

$('#reset-form').addEventListener('submit', async e => {
    e.preventDefault();
    const newPassword = new FormData(e.target).get('newPassword');
    try {
        await api('/api/auth/reset-password', { method: 'POST', body: { token: resetToken, newPassword } });
        resetToken = null;
        history.replaceState(null, '', location.pathname); // ukloni token iz URL-a
        switchAuthTab('login');
        showAuthSuccess('Lozinka je promijenjena. Možete se prijaviti.');
    } catch (err) {
        showAuthError(err.message);
    }
});

// Prikazuje "u toku..." na submit dugmetu forme dok traje akcija (feedback pri prijavi)
async function withButtonLoading(formEl, loadingText, fn) {
    const btn = formEl.querySelector('button[type="submit"]');
    const original = btn ? btn.textContent : '';
    if (btn) { btn.disabled = true; btn.textContent = loadingText; }
    try {
        await fn();
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = original; }
    }
}

$('#login-form').addEventListener('submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    const email = form.get('email');
    try {
        await withButtonLoading(e.target, 'Prijavljivanje...', async () => {
            const auth = await api('/api/auth/login', {
                method: 'POST',
                body: { email, password: form.get('password') }
            });
            saveAuth(auth);
            showApp();
        });
    } catch (err) {
        showAuthError(err.message);
        // 403 = nalog nije verifikovan: ponudi ponovno slanje linka
        if (err.status === 403) showResendVerification(email);
    }
});

// Prikazuje dugme za ponovno slanje verifikacionog linka nakon neuspjele prijave
function showResendVerification(email) {
    const box = $('#auth-resend');
    box.classList.remove('hidden');
    const btn = $('#resend-verif-link');
    btn.disabled = false;
    btn.onclick = async () => {
        btn.disabled = true;
        try {
            await api('/api/auth/resend-verification', { method: 'POST', body: { email } });
            box.classList.add('hidden');
            showAuthSuccess('Poslali smo novi link za potvrdu na ' + email + '.');
        } catch (err) {
            showAuthError(err.message);
            btn.disabled = false;
        }
    };
}

$('#register-form').addEventListener('submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    const email = form.get('email');
    const password = form.get('password');
    try {
        await withButtonLoading(e.target, 'Kreiranje naloga...', async () => {
            await api('/api/auth/register', {
                method: 'POST',
                body: { name: form.get('name'), email, password }
            });
            if (verificationRequired) {
                // Nalog kreiran, ali se prvo mora potvrditi email
                e.target.reset();
                switchAuthTab('login');
                showAuthSuccess('Nalog je kreiran. Poslali smo link za potvrdu na ' + email
                    + ' — potvrdite adresu da biste se prijavili.');
            } else {
                // Verifikacija iskljucena: odmah prijavi korisnika
                const auth = await api('/api/auth/login', { method: 'POST', body: { email, password } });
                saveAuth(auth);
                showApp();
            }
        });
    } catch (err) {
        showAuthError(err.message);
    }
});

$('#logout-btn').addEventListener('click', logout);

// Ruter
const routes = {
    dashboard: renderDashboard,
    transactions: renderTransactions,
    accounts: renderAccounts,
    budgets: renderBudgets,
    categories: renderCategories,
    tools: renderTools,
    profile: renderProfile,
    admin: renderAdmin
};

// Skeleton koji priblizno oponasa raspored stranice dok se podaci ucitavaju
function skeletonFor(routeName) {
    const line = (width, height = 12) =>
        `<div class="sk sk-line" style="width:${width};height:${height}px"></div>`;
    const gap = height => `<div style="height:${height}px"></div>`;

    if (routeName === 'dashboard') {
        const statCard = `<div class="card stat-card">${line('55%')}${gap(12)}${line('80%', 24)}${gap(8)}${line('40%', 10)}</div>`;
        return `<div class="stats-grid">${statCard.repeat(4)}</div>
            <div class="dash-grid">
                <div class="card"><div class="sk sk-block" style="min-height:236px"></div></div>
                <div class="card"><div class="sk sk-block" style="min-height:236px"></div></div>
            </div>`;
    }
    if (routeName === 'accounts' || routeName === 'budgets') {
        const card = `<div class="card">${line('35%', 10)}${gap(10)}${line('60%', 18)}${gap(14)}${line('85%', 22)}${gap(12)}${line('50%')}</div>`;
        return `<div class="accounts-grid">${card.repeat(3)}</div>`;
    }
    const row = `<div style="display:flex;align-items:center;gap:16px;padding:13px 0">
        ${line('70px')}${line('26%')}${line('16%')}<span style="flex:1"></span>${line('80px')}</div>`;
    return `<div class="card">${line('30%', 16)}${gap(18)}${row.repeat(6)}</div>`;
}

function route() {
    if (!state.token) return;
    const name = (location.hash.replace('#/', '') || 'dashboard').split('?')[0];
    const render = routes[name] || renderDashboard;
    $$('#nav a, #mobile-nav a').forEach(a => a.classList.toggle('active', a.dataset.route === name));
    $('#view').innerHTML = skeletonFor(name);
    refreshAlerts();
    render().catch(err => {
        $('#view').innerHTML = `<div class="empty-state"><span class="emoji">⚠️</span><p>${esc(err.message)}</p></div>`;
    });
}

window.addEventListener('hashchange', route);

async function loadRefs() {
    [state.accounts, state.categories] = await Promise.all([
        api('/api/accounts'),
        api('/api/categories')
    ]);
}

// Tema (svijetla/tamna) — cuva se u localStorage, vazi samo unutar aplikacije
const THEME_KEY = 'pfm_theme';

function applyTheme(theme) {
    if (theme === 'dark') {
        document.documentElement.dataset.theme = 'dark';
    } else {
        delete document.documentElement.dataset.theme;
    }
    const themeIcon = $('#theme-icon');
    const themeLabel = $('#theme-label');
    if (themeIcon) themeIcon.innerHTML = theme === 'dark' ? ICONS.sun : ICONS.moon;
    if (themeLabel) themeLabel.textContent = theme === 'dark' ? 'Svijetla tema' : 'Tamna tema';
}

function toggleTheme() {
    const next = localStorage.getItem(THEME_KEY) === 'dark' ? 'light' : 'dark';
    localStorage.setItem(THEME_KEY, next);
    applyTheme(next);
    route(); // grafikoni se ponovo iscrtaju u bojama teme
}

// SVG grafikoni
// Boje serija su validirane dataviz validatorom za obje podloge (CVD, kontrast):
// svijetla: #059669 / #e34948 · tamna: #199e70 / #e66767
function chartColors() {
    return document.documentElement.dataset.theme === 'dark'
        ? { income: '#199e70', expense: '#e66767' }
        : { income: '#059669', expense: '#e34948' };
}

function svgDoughnut(items, centerLabel = 'EUR · ovaj mjesec') {
    const total = items.reduce((sum, it) => sum + Number(it.amount), 0);
    if (!total) return '<p class="muted">Nema rashoda u izabranom periodu.</p>';
    let offset = 25; // start na vrhu
    const circles = items.map(it => {
        const pct = Number(it.amount) / total * 100;
        // 1.2 jedinice razmaka izmedju segmenata (2px pravilo)
        const seg = Math.max(pct - 1.2, 0.4);
        const c = `<circle r="15.9155" cx="21" cy="21" fill="none" stroke="${esc(it.color)}"
            stroke-width="4.6" pathLength="100"
            stroke-dasharray="${seg} ${100 - seg}"
            stroke-dashoffset="${offset - 0.6}"></circle>`;
        offset -= pct;
        return c;
    }).join('');
    const legend = items.map(it => `
        <div class="legend-row ${it.id != null ? 'clickable' : ''}"${it.id != null
            ? ` data-id="${it.id}" data-name="${esc(it.name)}" data-color="${esc(it.color)}" title="Detalji kategorije"` : ''}>
            <span class="dot" style="background:${esc(it.color)}"></span>
            <span class="name">${esc(it.name)}</span>
            <span class="legend-value">${fmtMoney(it.amount)}</span>
        </div>`).join('');
    return `<div class="doughnut-wrap">
        <svg viewBox="0 0 42 42" width="168" height="168" style="flex-shrink:0" role="img" aria-label="Rashodi po kategorijama">
            ${circles}
            <text x="21" y="20.2" text-anchor="middle" font-size="5.4" font-weight="800" letter-spacing="-.1" style="fill:var(--text)">${Number(total).toLocaleString('sr-ME', { maximumFractionDigits: 0 })}</text>
            <text x="21" y="25.6" text-anchor="middle" font-size="2.5" font-weight="500" style="fill:var(--text-3)">${esc(centerLabel.toUpperCase())}</text>
        </svg>
        <div class="chart-legend">${legend}</div>
    </div>`;
}

// Gornja ivica bara zaobljena, dno ravno na baznoj liniji
function barPath(x, yTop, w, h, r) {
    r = Math.min(r, h, w / 2);
    const yBase = yTop + h;
    return `M${x},${yBase} L${x},${yTop + r} Q${x},${yTop} ${x + r},${yTop} L${x + w - r},${yTop} Q${x + w},${yTop} ${x + w},${yTop + r} L${x + w},${yBase} Z`;
}

function niceCeil(value) {
    const pow = Math.pow(10, Math.floor(Math.log10(value || 1)));
    const unit = value / pow;
    const nice = unit <= 1 ? 1 : unit <= 2 ? 2 : unit <= 5 ? 5 : 10;
    return nice * pow;
}

function svgBars(flow) {
    const colors = chartColors();
    const rawMax = Math.max(...flow.map(f => Math.max(Number(f.income), Number(f.expense))), 1);
    const max = niceCeil(rawMax);
    const W = 560, H = 236, padL = 46, padR = 10, padT = 10, padB = 26;
    const chartH = H - padT - padB;
    const baseline = H - padB;
    const groupW = (W - padL - padR) / flow.length;
    const barW = Math.min(17, (groupW - 16) / 2);
    const ticks = [0, .25, .5, .75, 1];

    const grid = ticks.map(t => {
        const y = baseline - t * chartH;
        const label = (max * t).toLocaleString('sr-ME', { maximumFractionDigits: 0 });
        return `<line x1="${padL}" y1="${y}" x2="${W - padR}" y2="${y}" style="stroke:${t === 0 ? 'var(--chart-axis)' : 'var(--chart-grid)'}"></line>
            <text x="${padL - 8}" y="${y + 3.5}" text-anchor="end" font-size="10.5" style="fill:var(--text-3);font-variant-numeric:tabular-nums">${label}</text>`;
    }).join('');

    const bars = flow.map((f, i) => {
        const cx = padL + i * groupW + groupW / 2;
        const hInc = Math.max(Number(f.income) / max * chartH, Number(f.income) > 0 ? 2 : 0);
        const hExp = Math.max(Number(f.expense) / max * chartH, Number(f.expense) > 0 ? 2 : 0);
        return `<g class="bar-group" data-i="${i}">
            <rect class="bar-hit" x="${padL + i * groupW}" y="${padT}" width="${groupW}" height="${chartH}" fill="transparent"></rect>
            ${hInc ? `<path class="bar" d="${barPath(cx - barW - 1, baseline - hInc, barW, hInc, 4)}" fill="${colors.income}"></path>` : ''}
            ${hExp ? `<path class="bar" d="${barPath(cx + 1, baseline - hExp, barW, hExp, 4)}" fill="${colors.expense}"></path>` : ''}
            <text x="${cx}" y="${H - 8}" text-anchor="middle" font-size="11" style="fill:var(--text-3)">${esc(fmtMonth(f.month))}</text>
        </g>`;
    }).join('');

    return `<svg class="bar-chart" viewBox="0 0 ${W} ${H}" style="width:100%;display:block" role="img" aria-label="Mjesečni tok novca">
        ${grid}${bars}
    </svg>`;
}

// Tooltip sloj za bar grafikon
function attachBarTips(card, flow) {
    const tip = document.createElement('div');
    tip.className = 'chart-tip';
    card.appendChild(tip);
    card.querySelectorAll('.bar-group').forEach(group => {
        group.addEventListener('mouseenter', () => {
            const colors = chartColors();
            const f = flow[Number(group.dataset.i)];
            tip.innerHTML = `
                <div class="tip-title">${esc(fmtMonth(f.month))}</div>
                <div class="tip-row"><span class="swatch" style="background:${colors.income}"></span>Prihodi <b>+${fmtMoney(f.income)}</b></div>
                <div class="tip-row"><span class="swatch" style="background:${colors.expense}"></span>Rashodi <b>-${fmtMoney(f.expense)}</b></div>`;
            tip.classList.add('show');
        });
        group.addEventListener('mousemove', e => {
            const rect = card.getBoundingClientRect();
            const x = Math.min(e.clientX - rect.left + 14, rect.width - tip.offsetWidth - 8);
            const y = e.clientY - rect.top - tip.offsetHeight - 10;
            tip.style.left = Math.max(x, 8) + 'px';
            tip.style.top = Math.max(y, 4) + 'px';
        });
        group.addEventListener('mouseleave', () => tip.classList.remove('show'));
    });
}

function budgetRow(b) {
    const pct = Math.min(b.percentUsed, 100);
    const cls = b.percentUsed >= 100 ? 'over' : (b.percentUsed >= 80 ? 'warn' : '');
    const cats = b.categories.length
        ? b.categories.map(c => `<span class="chip"><span class="dot" style="background:${esc(c.color || '#94a3b8')}"></span>${esc(c.name)}</span>`).join(' ')
        : '<span class="chip">Svi rashodi</span>';
    return `<div class="budget-row">
        <div class="budget-row-head">
            <strong>${esc(b.name)}</strong>
            <span class="muted">${fmtMoney(b.spent)} / ${fmtMoney(b.limitAmount)} - ${b.percentUsed}%</span>
        </div>
        <div class="budget-bar"><div class="budget-bar-fill ${cls}" style="width:${pct}%"></div></div>
        <div style="margin-top:8px">${cats} <span class="chip">${PERIODS[b.period]}</span></div>
    </div>`;
}

// Dashboard
let dashPeriod = 1; // 1, 3 ili 12 mjeseci — pamti se dok traje sesija

async function renderDashboard() {
    const d = await api('/api/dashboard?months=' + dashPeriod);
    const periodText = dashPeriod === 1 ? 'ovaj mjesec'
        : dashPeriod === 3 ? 'zadnja 3 mjeseca' : 'zadnjih 12 mjeseci';
    const recentRows = d.recentTransactions.map(t => `
        <tr>
            <td>${fmtDate(t.date)}</td>
            <td>${esc(t.description || '-')}</td>
            <td><span class="chip"><span class="dot" style="background:${esc(t.categoryColor || '#94a3b8')}"></span>${esc(t.categoryName || 'Bez kategorije')}</span></td>
            <td class="amount ${t.type === 'INCOME' ? 'income' : 'expense'}">${t.type === 'INCOME' ? '+' : '-'}${fmtMoney(t.amount)}</td>
        </tr>`).join('');

    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Pregled</h1><p>Vaše finansije na jednom mjestu</p></div>
            <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
                <div class="seg" id="dash-period" role="group" aria-label="Period">
                    <button type="button" data-m="1" class="${dashPeriod === 1 ? 'active' : ''}">Ovaj mjesec</button>
                    <button type="button" data-m="3" class="${dashPeriod === 3 ? 'active' : ''}">3 mjeseca</button>
                    <button type="button" data-m="12" class="${dashPeriod === 12 ? 'active' : ''}">Godina</button>
                </div>
                <button class="btn btn-primary" id="dash-add-tx">${icon('plus', 'ico ico-sm')} Nova transakcija</button>
            </div>
        </div>
        <div class="stats-grid">
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Ukupno stanje</span><span class="stat-ico">${icon('wallet')}</span></div>
                <div class="stat-value">${fmtMoney(d.totalBalance)}</div>
                <div class="stat-sub">${d.accountCount} račun(a)${d.hasForeignCurrency ? ' · strane valute preračunate u EUR' : ''}</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Prihodi</span><span class="stat-ico">${icon('trendUp')}</span></div>
                <div class="stat-value" style="color:var(--income)">+${fmtMoney(d.incomeThisMonth)}</div>
                <div class="stat-sub">${periodText}</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Rashodi</span><span class="stat-ico red">${icon('trendDown')}</span></div>
                <div class="stat-value" style="color:var(--expense)">-${fmtMoney(d.expenseThisMonth)}</div>
                <div class="stat-sub">${periodText}</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Neto</span><span class="stat-ico slate">${icon('scale')}</span></div>
                <div class="stat-value" style="color:${Number(d.netThisMonth) >= 0 ? 'var(--income)' : 'var(--expense)'}">${fmtMoney(d.netThisMonth)}</div>
                <div class="stat-sub">prihodi minus rashodi, ${periodText}</div>
            </div>
        </div>
        <div class="dash-grid">
            <div class="card chart-card" id="flow-card">
                <div class="card-head">
                    <h3>Tok novca — zadnjih ${d.monthlyFlow.length} mjeseci</h3>
                    <div class="chart-legend-top">
                        <span class="legend-item"><span class="swatch" style="background:${chartColors().income}"></span>Prihodi</span>
                        <span class="legend-item"><span class="swatch" style="background:${chartColors().expense}"></span>Rashodi</span>
                    </div>
                </div>
                ${svgBars(d.monthlyFlow)}
            </div>
            <div class="card"><h3>Rashodi po kategorijama</h3>${svgDoughnut(d.spendingByCategory, 'EUR · ' + periodText)}</div>
        </div>
        <div class="dash-grid-2">
            <div class="card">
                <h3>Posljednje transakcije</h3>
                ${d.recentTransactions.length
                    ? `<div class="table-wrap"><table><tbody>${recentRows}</tbody></table></div>`
                    : '<p class="muted">Još nema transakcija.</p>'}
            </div>
            <div class="card">
                <h3>Budžeti</h3>
                ${d.budgets.length ? d.budgets.map(budgetRow).join('') : '<p class="muted">Još nema budžeta.</p>'}
            </div>
        </div>`;

    attachBarTips($('#flow-card'), d.monthlyFlow);

    $$('#dash-period button').forEach(btn => btn.addEventListener('click', () => {
        dashPeriod = Number(btn.dataset.m);
        renderDashboard();
    }));

    $$('#view .legend-row.clickable').forEach(row => row.addEventListener('click', () =>
        openCategoryDrill({
            id: Number(row.dataset.id),
            name: row.dataset.name,
            color: row.dataset.color
        })));

    $('#dash-add-tx').addEventListener('click', async () => {
        await loadRefs();
        openTransactionModal(null, () => route());
    });
}

// Drill-down: detalji potrosnje za jednu kategoriju
function svgMiniBars(points, color) {
    const max = niceCeil(Math.max(...points.map(p => p.value), 1));
    const maxValue = Math.max(...points.map(p => p.value));
    const W = 480, H = 170, padL = 8, padR = 8, padT = 24, padB = 24;
    const chartH = H - padT - padB;
    const baseline = H - padB;
    const slot = (W - padL - padR) / points.length;
    const barW = Math.min(36, slot * 0.5);

    const bars = points.map((p, i) => {
        const x = padL + i * slot + (slot - barW) / 2;
        const h = Math.max(p.value / max * chartH, p.value > 0 ? 2 : 0);
        // Selektivna oznaka: vrijednost samo na najvisem stubicu
        const topLabel = p.value > 0 && p.value === maxValue
            ? `<text x="${x + barW / 2}" y="${baseline - h - 7}" text-anchor="middle" font-size="10.5" font-weight="600" style="fill:var(--text-2);font-variant-numeric:tabular-nums">${Math.round(p.value)}</text>`
            : '';
        return `${h ? `<path d="${barPath(x, baseline - h, barW, h, 4)}" fill="${color}"><title>${esc(p.label)}: ${fmtMoney(p.value)}</title></path>` : ''}
            ${topLabel}
            <text x="${padL + i * slot + slot / 2}" y="${H - 7}" text-anchor="middle" font-size="10.5" style="fill:var(--text-3)">${esc(p.label)}</text>`;
    }).join('');

    return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;display:block" role="img" aria-label="Potrošnja po mjesecima">
        <line x1="${padL}" y1="${baseline}" x2="${W - padR}" y2="${baseline}" style="stroke:var(--chart-axis)"></line>
        ${bars}
    </svg>`;
}

async function openCategoryDrill(cat) {
    const body = openModal(cat.name,
        '<div class="loading-state"><span class="spinner"></span>Učitavanje...</div>');

    const start = new Date();
    start.setDate(1);
    start.setMonth(start.getMonth() - 5);
    const fromStr = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-01`;

    let transactions;
    try {
        transactions = await api(`/api/transactions?categoryId=${cat.id}&from=${fromStr}`);
    } catch (err) {
        body.innerHTML = `<p class="muted">${esc(err.message)}</p>`;
        return;
    }

    const now = new Date();
    const months = [];
    for (let i = 5; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const ym = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        months.push({ ym, label: fmtMonth(ym), value: 0 });
    }
    transactions.forEach(t => {
        const bucket = months.find(m => m.ym === t.date.slice(0, 7));
        if (bucket) bucket.value += Number(t.amount);
    });
    const total = months.reduce((sum, m) => sum + m.value, 0);
    const recent = transactions.slice(0, 5);

    body.innerHTML = `
        <div class="drill-head">
            <span class="dot" style="background:${esc(cat.color)}"></span>
            Potrošeno u zadnjih 6 mjeseci: <b>${fmtMoney(total)}</b>
        </div>
        ${svgMiniBars(months.map(m => ({ label: m.label, value: m.value })), cat.color)}
        <div style="margin-top:14px">
            <span style="font-size:12.5px;font-weight:600;color:var(--text-2)">Posljednje transakcije</span>
            ${recent.length ? `<div class="table-wrap"><table><tbody>${recent.map(t => `
                <tr>
                    <td>${fmtDate(t.date)}</td>
                    <td>${esc(t.description || '-')}</td>
                    <td class="muted">${esc(t.accountName)}</td>
                    <td class="amount expense" style="text-align:right">-${fmtMoney(t.amount)}</td>
                </tr>`).join('')}</tbody></table></div>`
            : '<p class="muted" style="font-size:13px;margin-top:6px">Nema transakcija u ovom periodu.</p>'}
        </div>
        <div class="form-actions">
            <button class="btn btn-secondary" id="drill-all" type="button">Prikaži sve u transakcijama</button>
        </div>`;

    $('#drill-all', body).addEventListener('click', () => {
        closeModal();
        state.categoryFilter = cat.id;
        location.hash = '#/transactions';
    });
}

// Transakcije
async function renderTransactions() {
    await loadRefs();
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Transakcije</h1><p>Svi prihodi i rashodi</p></div>
            <div style="display:flex;gap:10px;flex-wrap:wrap">
                <button class="btn btn-secondary" id="tx-import" type="button">${icon('upload', 'ico ico-sm')} Uvezi izvod</button>
                <button class="btn btn-secondary" id="tx-recurring" type="button">${icon('repeat', 'ico ico-sm')} Ponavljajuće</button>
                <button class="btn btn-secondary" id="tx-export" type="button">${icon('download', 'ico ico-sm')} Izvezi CSV</button>
                <button class="btn btn-primary" id="add-tx" type="button">${icon('plus', 'ico ico-sm')} Nova transakcija</button>
            </div>
        </div>
        <div class="card">
            <div class="filters">
                <div class="form-field"><span>Račun</span>
                    <select id="f-account"><option value="">Svi</option>
                        ${state.accounts.map(a => `<option value="${a.id}">${esc(a.name)}</option>`).join('')}
                    </select></div>
                <div class="form-field"><span>Kategorija</span>
                    <select id="f-category"><option value="">Sve</option>
                        ${state.categories.map(c => `<option value="${c.id}">${esc(c.icon || '')} ${esc(c.name)}</option>`).join('')}
                    </select></div>
                <div class="form-field"><span>Tip</span>
                    <select id="f-type"><option value="">Svi</option>
                        <option value="INCOME">Prihod</option><option value="EXPENSE">Rashod</option>
                    </select></div>
                <div class="form-field"><span>Od</span><input type="date" id="f-from"></div>
                <div class="form-field"><span>Do</span><input type="date" id="f-to"></div>
                <div class="form-field grow"><span>Pretraga</span><input type="search" id="f-search" placeholder="Opis transakcije..."></div>
                <button class="btn btn-secondary" id="f-apply">Filtriraj</button>
            </div>
            <div id="tx-table"></div>
        </div>`;

    const PAGE_SIZE = 20;
    let allTransactions = [];
    let sortKey = 'date';
    let sortDir = 'desc';
    let page = 1;

    function sorted() {
        const dir = sortDir === 'asc' ? 1 : -1;
        return [...allTransactions].sort((a, b) => {
            const cmp = sortKey === 'amount'
                ? Number(a.amount) - Number(b.amount)
                : (a.date === b.date ? a.id - b.id : (a.date < b.date ? -1 : 1));
            return cmp * dir;
        });
    }

    function renderTable() {
        if (!allTransactions.length) {
            $('#tx-table').innerHTML = `<div class="empty-state"><span class="emoji">🧾</span><p>Nema transakcija za zadate filtere.</p></div>`;
            return;
        }
        const rows = sorted();
        const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
        page = Math.min(page, pages);
        const start = (page - 1) * PAGE_SIZE;
        const visible = rows.slice(start, start + PAGE_SIZE);
        const arrow = key => sortKey === key
            ? `<span class="sort-arrow">${sortDir === 'asc' ? '▲' : '▼'}</span>` : '';

        $('#tx-table').innerHTML = `<div class="table-wrap"><table>
            <thead><tr>
                <th class="sortable" data-sort="date">Datum${arrow('date')}</th>
                <th>Opis</th><th>Kategorija</th><th>Račun</th>
                <th class="sortable" data-sort="amount" style="text-align:right">Iznos${arrow('amount')}</th>
                <th></th>
            </tr></thead>
            <tbody>${visible.map(t => `
                <tr data-id="${t.id}">
                    <td>${fmtDate(t.date)}</td>
                    <td>${esc(t.description || '-')}
                        ${t.tags.map(tag => `<span class="tag-chip">#${esc(tag)}</span>`).join('')}
                        ${t.attachments.length ? `<span class="chip" title="${t.attachments.length} prilog(a)">${icon('clip', 'ico ico-sm')} ${t.attachments.length}</span>` : ''}</td>
                    <td><span class="chip"><span class="dot" style="background:${esc(t.categoryColor || '#94a3b8')}"></span>${esc(t.categoryName || 'Bez kategorije')}</span></td>
                    <td class="muted">${esc(t.accountName)}</td>
                    <td class="amount ${t.type === 'INCOME' ? 'income' : 'expense'}" style="text-align:right">${t.type === 'INCOME' ? '+' : '-'}${fmtMoney(t.amount)}</td>
                    <td><div class="row-actions">
                        <button class="icon-btn" data-act="edit" title="Uredi">${icon('edit')}</button>
                        <button class="icon-btn danger" data-act="delete" title="Obriši">${icon('trash')}</button>
                    </div></td>
                </tr>`).join('')}</tbody>
        </table></div>
        ${rows.length > PAGE_SIZE ? `<div class="pager">
            <span class="pager-info">Prikazano ${start + 1}–${start + visible.length} od ${rows.length}</span>
            <div class="pager-controls">
                <button class="icon-btn" id="pg-prev" type="button" ${page === 1 ? 'disabled' : ''} aria-label="Prethodna strana">${icon('chevLeft')}</button>
                <span class="pager-page">${page} / ${pages}</span>
                <button class="icon-btn" id="pg-next" type="button" ${page === pages ? 'disabled' : ''} aria-label="Sljedeća strana">${icon('chevRight')}</button>
            </div>
        </div>` : ''}`;

        $$('#tx-table th.sortable').forEach(th => th.addEventListener('click', () => {
            const key = th.dataset.sort;
            if (sortKey === key) {
                sortDir = sortDir === 'asc' ? 'desc' : 'asc';
            } else {
                sortKey = key;
                sortDir = 'desc';
            }
            page = 1;
            renderTable();
        }));
        const prev = $('#pg-prev'), next = $('#pg-next');
        if (prev) prev.addEventListener('click', () => { page--; renderTable(); });
        if (next) next.addEventListener('click', () => { page++; renderTable(); });

        $$('#tx-table [data-act]').forEach(btn => btn.addEventListener('click', async () => {
            const id = Number(btn.closest('tr').dataset.id);
            const transaction = allTransactions.find(t => t.id === id);
            if (btn.dataset.act === 'edit') {
                openTransactionModal(transaction, loadTable);
            } else if (await confirmDialog({ title: 'Brisanje transakcije',
                    message: 'Stanje računa će biti vraćeno kao da transakcija nije ni postojala.' })) {
                try {
                    await api('/api/transactions/' + id, { method: 'DELETE' });
                    toast('Transakcija obrisana');
                    loadTable();
                } catch (err) { toast(err.message, 'error'); }
            }
        }));
    }

    async function loadTable() {
        const params = new URLSearchParams();
        if ($('#f-account').value) params.set('accountId', $('#f-account').value);
        if ($('#f-category').value) params.set('categoryId', $('#f-category').value);
        if ($('#f-type').value) params.set('type', $('#f-type').value);
        if ($('#f-from').value) params.set('from', $('#f-from').value);
        if ($('#f-to').value) params.set('to', $('#f-to').value);
        if ($('#f-search').value) params.set('search', $('#f-search').value);
        allTransactions = await api('/api/transactions?' + params);
        page = 1;
        renderTable();
    }

    // CSV izvoz: postuje aktivne filtere i sortiranje, format prilagodjen Excelu
    function exportCsv() {
        if (!allTransactions.length) {
            toast('Nema transakcija za izvoz', 'error');
            return;
        }
        const field = value => {
            const s = String(value ?? '');
            return /[;"\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
        };
        const header = ['Datum', 'Opis', 'Kategorija', 'Račun', 'Tip', 'Iznos (EUR)', 'Tagovi'];
        const lines = sorted().map(t => [
            fmtDate(t.date),
            t.description || '',
            t.categoryName || '',
            t.accountName,
            t.type === 'INCOME' ? 'Prihod' : 'Rashod',
            (t.type === 'INCOME' ? '' : '-') + String(t.amount).replace('.', ','),
            t.tags.join(', ')
        ].map(field).join(';'));

        // BOM na pocetku da Excel prepozna UTF-8 (nasa slova)
        const csv = '﻿' + [header.join(';'), ...lines].join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'transakcije-' + new Date().toISOString().slice(0, 10) + '.csv';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        toast('CSV fajl je preuzet (' + allTransactions.length + ' transakcija)');
    }

    $('#f-apply').addEventListener('click', loadTable);
    $('#f-search').addEventListener('keydown', e => { if (e.key === 'Enter') loadTable(); });
    $('#add-tx').addEventListener('click', () => openTransactionModal(null, loadTable));
    $('#tx-export').addEventListener('click', exportCsv);
    $('#tx-recurring').addEventListener('click', openRecurringModal);
    $('#tx-import').addEventListener('click', () => openImportModal(loadTable));

    // Filter prenesen iz drill-down pregleda kategorije
    if (state.categoryFilter) {
        $('#f-category').value = String(state.categoryFilter);
        state.categoryFilter = null;
    }
    await loadTable();

    // Dosli smo preko obavjestenja "Vrijeme je za izvod" - odmah otvori uvoz
    if (state.openImport) {
        state.openImport = false;
        openImportModal(loadTable);
    }
}

// Uvoz bankovnog izvoda: upload CSV-a, pregled sa predlozima kategorija, potvrda
function openImportModal(onDone) {
    const body = openModal('Uvoz bankovnog izvoda', `
        <form id="import-form">
            <div class="form-grid">
                <div class="form-field"><span>Račun na koji se uvozi</span>
                    <select name="accountId">
                        ${state.accounts.map(a => `<option value="${a.id}">${esc(a.name)} (${esc(a.currency)})</option>`).join('')}
                    </select></div>
                <div class="form-field"><span>CSV fajl izvoda iz e-bankinga</span>
                    <input type="file" name="file" required></div>
            </div>
            <p class="muted" style="font-size:12.5px;margin-top:12px">
                Fajl mora biti <strong>CSV</strong> (u e-bankingu izaberite izvoz u CSV; Excel fajl
                sačuvajte kao CSV). Podržane su kolone datum, opis i iznos (ili odvojene uplata/isplata).
                Prije uvoza dobijate pregled sa predloženim kategorijama koje možete izmijeniti.</p>
            <div class="form-actions">
                <button type="submit" class="btn btn-primary">Učitaj i pregledaj</button>
            </div>
        </form>`, true);

    $('#import-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const accountId = Number(e.target.elements.accountId.value);
        const fileInput = e.target.elements.file;
        if (!fileInput.files.length) return;

        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('accountId', String(accountId));

        body.innerHTML = '<div class="loading-state"><span class="spinner"></span>Čitam izvod...</div>';
        let preview;
        try {
            preview = await api('/api/import/preview', { method: 'POST', body: formData });
        } catch (err) {
            toast(err.message, 'error');
            closeModal();
            return;
        }
        renderImportPreview(body, accountId, preview, onDone);
    });
}

function renderImportPreview(body, accountId, preview, onDone) {
    const otherAccounts = state.accounts.filter(a => a.id !== accountId);

    // Pored kategorija, red se moze oznaciti kao prebacivanje na/sa drugog racuna
    const categoryOptions = (row) => {
        const categories = '<option value="">Bez kategorije</option>' + state.categories
            .filter(c => c.type === row.type)
            .map(c => `<option value="${c.id}" ${c.id === row.suggestedCategoryId ? 'selected' : ''}>${esc(c.name)}</option>`)
            .join('');
        if (!otherAccounts.length) {
            return categories;
        }
        const label = row.type === 'EXPENSE' ? 'Prebačeno na' : 'Primljeno sa';
        const preselect = row.possibleTransfer && !row.suggestedCategoryId && otherAccounts.length === 1;
        const transfers = `<optgroup label="Prebacivanje (nije trošak ni prihod)">${otherAccounts.map(a =>
            `<option value="t:${a.id}" ${preselect ? 'selected' : ''}>${label}: ${esc(a.name)}</option>`).join('')}</optgroup>`;
        return categories + transfers;
    };

    const duplicates = preview.rows.filter(r => r.duplicate).length;

    body.innerHTML = `
        <p style="font-size:13px;margin-bottom:4px">
            Pronađeno <b>${preview.rows.length}</b> transakcija${duplicates
                ? `, od toga <b>${duplicates}</b> već postoji (isključene su iz uvoza)` : ''}.
            Provjerite kategorije i potvrdite.</p>
        ${preview.skipped.length
            ? `<p class="muted" style="font-size:12px;margin-bottom:4px">Preskočeno: ${esc(preview.skipped.join('; '))}</p>` : ''}
        <div class="table-wrap" style="max-height:340px;overflow-y:auto">
        <table class="import-table">
            <thead><tr><th></th><th>Datum</th><th>Opis</th><th style="text-align:right">Iznos</th><th>Kategorija</th></tr></thead>
            <tbody>${preview.rows.map((r, i) => `
                <tr class="${r.duplicate ? 'dup' : ''}" data-i="${i}">
                    <td><input type="checkbox" class="imp-inc" ${r.duplicate ? '' : 'checked'}></td>
                    <td style="white-space:nowrap">${fmtDate(r.date)}</td>
                    <td>${esc(r.description || '-')}${r.duplicate ? ' <span class="chip dup-chip">duplikat</span>' : ''}${r.possibleTransfer && !r.duplicate ? ' <span class="chip tr-chip">liči na prebacivanje</span>' : ''}</td>
                    <td class="amount ${r.type === 'INCOME' ? 'income' : 'expense'}" style="text-align:right;white-space:nowrap">${r.type === 'INCOME' ? '+' : '-'}${fmtMoney(r.amount)}</td>
                    <td><select class="imp-cat">${categoryOptions(r)}</select></td>
                </tr>`).join('')}</tbody>
        </table>
        </div>
        <label style="display:flex;align-items:center;gap:9px;margin-top:14px;font-size:13px;cursor:pointer">
            <input type="checkbox" id="imp-learn" class="switch" checked>
            Zapamti moje izbore kategorija za buduće uvoze
        </label>
        <div class="form-actions">
            <button class="btn btn-secondary" id="imp-cancel" type="button">Odustani</button>
            <button class="btn btn-primary" id="imp-confirm" type="button">Uvezi transakcije</button>
        </div>`;

    $('#imp-cancel', body).addEventListener('click', closeModal);
    $('#imp-confirm', body).addEventListener('click', async () => {
        const rows = [];
        $$('.import-table tbody tr', body).forEach(tr => {
            if (!tr.querySelector('.imp-inc').checked) return;
            const r = preview.rows[Number(tr.dataset.i)];
            const selected = tr.querySelector('.imp-cat').value;
            const isTransfer = selected.startsWith('t:');
            rows.push({
                date: r.date,
                description: r.description,
                amount: r.amount,
                type: r.type,
                categoryId: !isTransfer && selected ? Number(selected) : null,
                transferAccountId: isTransfer ? Number(selected.slice(2)) : null
            });
        });
        if (!rows.length) {
            toast('Nijedan red nije označen za uvoz', 'error');
            return;
        }
        const button = $('#imp-confirm', body);
        button.disabled = true;
        try {
            const result = await api('/api/import/confirm', {
                method: 'POST',
                body: { accountId, learnRules: $('#imp-learn', body).checked, rows }
            });
            toast(`Uvezeno ${result.created} transakcija`
                + (result.rulesLearned ? `, naučeno ${result.rulesLearned} novih pravila` : ''));
            closeModal();
            onDone();
        } catch (err) {
            toast(err.message, 'error');
            button.disabled = false;
        }
    });
}

// Ponavljajuca pravila: lista, pauziranje i brisanje
function openRecurringModal() {
    const body = openModal('Ponavljajuće transakcije',
        '<div class="loading-state"><span class="spinner"></span>Učitavanje...</div>');

    async function draw() {
        let rules;
        try {
            rules = await api('/api/recurring');
        } catch (err) {
            body.innerHTML = `<p class="muted">${esc(err.message)}</p>`;
            return;
        }
        body.innerHTML = (rules.length ? rules.map(r => `
            <div class="rec-row ${r.active ? '' : 'paused'}" data-id="${r.id}">
                <span class="rec-day"><b>${r.dayOfMonth}.</b><span>u mj.</span></span>
                <span class="rec-info">
                    <b>${esc(r.description || 'Bez opisa')}</b>
                    <span>${esc(r.accountName)}${r.categoryName ? ' · ' + esc(r.categoryName) : ''}</span>
                </span>
                <span class="rec-amount amount ${r.type === 'INCOME' ? 'income' : 'expense'}">${r.type === 'INCOME' ? '+' : '-'}${fmtMoney(r.amount)}</span>
                <input type="checkbox" class="switch" data-act="toggle" ${r.active ? 'checked' : ''}
                       title="${r.active ? 'Pauziraj pravilo' : 'Aktiviraj pravilo'}" aria-label="Aktivno">
                <button class="icon-btn danger" data-act="delete" type="button" title="Obriši pravilo">${icon('trash')}</button>
            </div>`).join('')
            : '<p class="muted" style="text-align:center;padding:20px 0 8px">Još nema ponavljajućih pravila.<br>Označite "Ponavljaj svakog mjeseca" pri unosu nove transakcije.</p>')
            + '<p class="muted" style="font-size:12px;margin-top:16px">Aktivna pravila automatski upisuju transakciju izabranog dana svakog mjeseca.</p>';

        $$('[data-act]', body).forEach(el => {
            const eventName = el.dataset.act === 'toggle' ? 'change' : 'click';
            el.addEventListener(eventName, async () => {
                const id = Number(el.closest('.rec-row').dataset.id);
                try {
                    if (el.dataset.act === 'toggle') {
                        await api(`/api/recurring/${id}/toggle`, { method: 'PUT' });
                    } else if (await confirmDialog({ title: 'Brisanje pravila',
                            message: 'Pravilo prestaje da važi. Već kreirane transakcije ostaju.' })) {
                        await api('/api/recurring/' + id, { method: 'DELETE' });
                        toast('Pravilo obrisano');
                    }
                } catch (err) {
                    toast(err.message, 'error');
                }
                draw();
            });
        });
    }

    draw();
}

function openTransactionModal(transaction, onSaved) {
    const isEdit = !!transaction;
    if (!state.accounts.length) {
        toast('Prvo kreirajte račun', 'error');
        location.hash = '#/accounts';
        return;
    }
    const body = openModal(isEdit ? 'Uredi transakciju' : 'Nova transakcija', `
        <form id="tx-form">
            <div class="form-grid">
                <div class="form-field"><span>Tip</span>
                    <select name="type" id="tx-type">
                        <option value="EXPENSE">Rashod</option>
                        <option value="INCOME">Prihod</option>
                    </select></div>
                <div class="form-field"><span>Iznos</span>
                    <input type="number" name="amount" step="0.01" min="0.01" required
                           value="${isEdit ? transaction.amount : ''}" placeholder="0.00"></div>
                <div class="form-field"><span>Datum</span>
                    <input type="date" name="date" required value="${isEdit ? transaction.date : new Date().toISOString().slice(0, 10)}"></div>
                <div class="form-field"><span>Račun</span>
                    <select name="accountId" required>
                        ${state.accounts.map(a => `<option value="${a.id}" ${isEdit && transaction.accountId === a.id ? 'selected' : ''}>${esc(a.name)}</option>`).join('')}
                    </select></div>
                <div class="form-field full"><span>Kategorija</span>
                    <select name="categoryId" id="tx-category"></select></div>
                <div class="form-field full"><span>Opis</span>
                    <input type="text" name="description" value="${isEdit ? esc(transaction.description || '') : ''}" placeholder="npr. Kupovina namirnica"></div>
                <div class="form-field full"><span>Tagovi (odvojeni zarezom)</span>
                    <input type="text" name="tags" value="${isEdit ? esc(transaction.tags.join(', ')) : ''}" placeholder="npr. porodica, vikend"></div>
                ${isEdit ? '' : `<div class="form-field full"><span>Prilog — račun ili faktura (opciono)</span>
                    <input type="file" name="attachment"></div>
                <label class="form-field full" style="flex-direction:row;align-items:center;gap:10px;cursor:pointer">
                    <input type="checkbox" name="recurring" class="switch">
                    <span style="font-size:13px;font-weight:500;color:var(--text)">Ponavljaj svakog mjeseca na ovaj dan</span>
                </label>`}
            </div>
            <div id="tx-attachments"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="tx-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">${isEdit ? 'Sačuvaj izmjene' : 'Dodaj transakciju'}</button>
            </div>
        </form>`);

    const typeSelect = $('#tx-type', body);
    const catSelect = $('#tx-category', body);
    if (isEdit) typeSelect.value = transaction.type;

    function refreshCategories() {
        const type = typeSelect.value;
        const options = state.categories.filter(c => c.type === type);
        catSelect.innerHTML = '<option value="">Bez kategorije</option>' +
            options.map(c => `<option value="${c.id}">${esc(c.icon || '')} ${esc(c.name)}</option>`).join('');
        if (isEdit && transaction.categoryId) catSelect.value = String(transaction.categoryId);
    }
    refreshCategories();
    typeSelect.addEventListener('change', refreshCategories);

    if (isEdit) renderAttachmentSection(body, transaction);

    $('#tx-cancel', body).addEventListener('click', closeModal);
    $('#tx-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        const payload = {
            amount: Number(form.get('amount')),
            date: form.get('date'),
            type: form.get('type'),
            description: form.get('description') || null,
            accountId: Number(form.get('accountId')),
            categoryId: form.get('categoryId') ? Number(form.get('categoryId')) : null,
            tags: String(form.get('tags') || '').split(',').map(t => t.trim()).filter(Boolean)
        };
        try {
            if (isEdit) {
                await api('/api/transactions/' + transaction.id, { method: 'PUT', body: payload });
                toast('Transakcija ažurirana');
            } else {
                // Odlozeni upload: prvo sacuvaj transakciju, pa prilozi fajl na dobijeni ID
                const created = await api('/api/transactions', { method: 'POST', body: payload });
                const fileInput = e.target.elements.attachment;
                if (fileInput && fileInput.files.length) {
                    const fileData = new FormData();
                    fileData.append('file', fileInput.files[0]);
                    try {
                        await api(`/api/transactions/${created.id}/attachments`, { method: 'POST', body: fileData });
                        toast('Transakcija i prilog dodani');
                    } catch (uploadErr) {
                        toast('Transakcija je dodana, ali prilog nije sačuvan: ' + uploadErr.message, 'error');
                    }
                } else {
                    toast('Transakcija dodana');
                }
                // Ako je oznaceno ponavljanje, kreiraj i mjesecno pravilo
                if (e.target.elements.recurring && e.target.elements.recurring.checked) {
                    try {
                        await api('/api/recurring', {
                            method: 'POST',
                            body: {
                                amount: payload.amount,
                                type: payload.type,
                                description: payload.description,
                                dayOfMonth: Number(payload.date.slice(8, 10)),
                                accountId: payload.accountId,
                                categoryId: payload.categoryId
                            }
                        });
                        toast('Pravilo kreirano — ponavlja se ' + Number(payload.date.slice(8, 10)) + '. u mjesecu');
                    } catch (ruleErr) {
                        toast('Ponavljajuće pravilo nije kreirano: ' + ruleErr.message, 'error');
                    }
                }
            }
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

function renderAttachmentSection(body, transaction) {
    const container = $('#tx-attachments', body);

    function draw(attachments) {
        container.innerHTML = `
            <div style="margin-top:16px">
                <span style="font-size:12.5px;font-weight:600;color:var(--text-muted)">Prilozi (računi, fakture...)</span>
                <div id="att-list">${attachments.map(a => `
                    <div class="attachment-row" data-id="${a.id}">
                        <a href="#" data-act="download">${icon('clip', 'ico ico-sm')} ${esc(a.filename)}</a>
                        <span class="attachment-size">${(a.size / 1024).toFixed(1)} KB</span>
                        <button type="button" class="icon-btn danger" data-act="remove" style="margin-left:auto" title="Obriši prilog">${icon('trash')}</button>
                    </div>`).join('') || '<p class="muted" style="font-size:13px;margin-top:6px">Nema priloga.</p>'}
                </div>
                <div style="display:flex;gap:8px;margin-top:10px;align-items:center">
                    <input type="file" id="att-file" style="flex:1">
                    <button type="button" class="btn btn-secondary btn-sm" id="att-upload">Dodaj prilog</button>
                </div>
            </div>`;

        $$('#att-list [data-act]', container).forEach(el => el.addEventListener('click', async e => {
            e.preventDefault();
            const id = Number(el.closest('.attachment-row').dataset.id);
            const attachment = attachments.find(a => a.id === id);
            if (el.dataset.act === 'download') {
                try {
                    const res = await fetch('/api/transactions/attachments/' + id, {
                        headers: { Authorization: 'Bearer ' + state.token }
                    });
                    if (!res.ok) throw new Error('Preuzimanje nije uspjelo');
                    const blob = await res.blob();
                    const url = URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = url;
                    link.download = attachment.filename;
                    link.click();
                    URL.revokeObjectURL(url);
                } catch (err) { toast(err.message, 'error'); }
            } else if (await confirmDialog({ title: 'Brisanje priloga',
                    message: 'Fajl "' + attachment.filename + '" će biti trajno obrisan.' })) {
                try {
                    await api('/api/transactions/attachments/' + id, { method: 'DELETE' });
                    draw(attachments.filter(a => a.id !== id));
                    toast('Prilog obrisan');
                } catch (err) { toast(err.message, 'error'); }
            }
        }));

        $('#att-upload', container).addEventListener('click', async () => {
            const input = $('#att-file', container);
            if (!input.files.length) { toast('Odaberite fajl', 'error'); return; }
            const form = new FormData();
            form.append('file', input.files[0]);
            try {
                const attachment = await api(`/api/transactions/${transaction.id}/attachments`, { method: 'POST', body: form });
                draw([...attachments, attachment]);
                toast('Prilog dodan');
            } catch (err) { toast(err.message, 'error'); }
        });
    }

    draw(transaction.attachments);
}

// Racuni
async function renderAccounts() {
    const accounts = await api('/api/accounts');
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Računi</h1><p>Bankovni računi, kartice i gotovina</p></div>
            <div style="display:flex;gap:10px;flex-wrap:wrap">
                <button class="btn btn-secondary" id="transfer-btn" type="button">${icon('transfer', 'ico ico-sm')} Prebaci novac</button>
                <button class="btn btn-primary" id="add-account" type="button">${icon('plus', 'ico ico-sm')} Novi račun</button>
            </div>
        </div>
        ${accounts.length ? `<div class="accounts-grid">${accounts.map(a => `
            <div class="card account-card" data-id="${a.id}">
                <div class="account-type">${ACCOUNT_TYPES[a.type] || a.type}</div>
                <div class="account-name">${esc(a.name)}</div>
                <div class="account-balance" style="color:${Number(a.balance) < 0 ? 'var(--expense)' : 'var(--text)'}">${fmtMoney(a.balance, a.currency)}</div>
                <div class="account-meta">
                    ${a.details ? esc(a.details.bankName || '') + (a.details.accountNumber ? ' - ' + esc(a.details.accountNumber) : '') : '&nbsp;'}
                </div>
                <div class="account-actions">
                    <button class="btn btn-secondary btn-sm" data-act="reconcile">Uskladi</button>
                    <button class="btn btn-secondary btn-sm" data-act="edit">Uredi</button>
                    <button class="btn btn-danger btn-sm" data-act="delete">Obriši</button>
                </div>
            </div>`).join('')}</div>`
        : `<div class="card"><div class="empty-state"><span class="emoji">🏦</span><p>Još nemate nijedan račun. Kreirajte prvi da počnete pratiti finansije.</p>
             <button class="btn btn-primary" id="add-account-empty">+ Novi račun</button></div></div>`}`;

    const openForm = (account) => openAccountModal(account, renderAccounts);
    if ($('#add-account-empty')) $('#add-account-empty').addEventListener('click', () => openForm(null));
    if ($('#add-account')) $('#add-account').addEventListener('click', () => openForm(null));
    $('#transfer-btn').addEventListener('click', () => {
        if (accounts.length < 2) {
            toast('Za prebacivanje trebaju bar dva računa', 'error');
            return;
        }
        openTransferModal(accounts, renderAccounts);
    });

    $$('.account-card [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.account-card').dataset.id);
        const account = accounts.find(a => a.id === id);
        if (btn.dataset.act === 'reconcile') {
            openReconcileModal(account, renderAccounts);
        } else if (btn.dataset.act === 'edit') {
            openForm(account);
        } else if (await confirmDialog({ title: 'Brisanje računa',
                message: `Račun "${account.name}" i sve njegove transakcije će biti trajno obrisani.` })) {
            try {
                await api('/api/accounts/' + id, { method: 'DELETE' });
                toast('Račun obrisan');
                renderAccounts();
            } catch (err) { toast(err.message, 'error'); }
        }
    }));
}

function openAccountModal(account, onSaved) {
    const isEdit = !!account;
    const body = openModal(isEdit ? 'Uredi račun' : 'Novi račun', `
        <form id="account-form">
            <div class="form-grid">
                <div class="form-field full"><span>Naziv</span>
                    <input type="text" name="name" required value="${isEdit ? esc(account.name) : ''}" placeholder="npr. Tekući račun"></div>
                <div class="form-field"><span>Tip</span>
                    <select name="type">${Object.entries(ACCOUNT_TYPES).map(([k, v]) =>
                        `<option value="${k}" ${isEdit && account.type === k ? 'selected' : ''}>${v}</option>`).join('')}</select></div>
                <div class="form-field"><span>Valuta</span>
                    <input type="text" name="currency" maxlength="3" required value="${isEdit ? esc(account.currency) : 'EUR'}" style="text-transform:uppercase"></div>
                ${isEdit ? '' : `<div class="form-field full"><span>Početno stanje</span>
                    <input type="number" name="initialBalance" step="0.01" value="0.00"></div>`}
                <div class="form-field"><span>Banka (opciono)</span>
                    <input type="text" name="bankName" value="${isEdit && account.details ? esc(account.details.bankName || '') : ''}"></div>
                <div class="form-field"><span>Broj računa (opciono)</span>
                    <input type="text" name="accountNumber" value="${isEdit && account.details ? esc(account.details.accountNumber || '') : ''}"></div>
                <div class="form-field full"><span>Datum otvaranja (opciono)</span>
                    <input type="date" name="openedDate" value="${isEdit && account.details ? (account.details.openedDate || '') : ''}"></div>
            </div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="account-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">${isEdit ? 'Sačuvaj' : 'Kreiraj račun'}</button>
            </div>
        </form>`);

    $('#account-cancel', body).addEventListener('click', closeModal);
    $('#account-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        const hasDetails = form.get('bankName') || form.get('accountNumber') || form.get('openedDate');
        const payload = {
            name: form.get('name'),
            type: form.get('type'),
            currency: String(form.get('currency')).toUpperCase(),
            initialBalance: isEdit ? null : Number(form.get('initialBalance') || 0),
            details: hasDetails ? {
                bankName: form.get('bankName') || null,
                accountNumber: form.get('accountNumber') || null,
                openedDate: form.get('openedDate') || null
            } : null
        };
        try {
            if (isEdit) {
                await api('/api/accounts/' + account.id, { method: 'PUT', body: payload });
                toast('Račun ažuriran');
            } else {
                await api('/api/accounts', { method: 'POST', body: payload });
                toast('Račun kreiran');
            }
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

// Uskladjivanje stanja racuna sa stvarnim stanjem (izvod banke ili prebrojani kes)
function openReconcileModal(account, onSaved) {
    const body = openModal('Usklađivanje: ' + account.name, `
        <p style="font-size:13.5px">Stanje u aplikaciji: <b style="font-variant-numeric:tabular-nums">${fmtMoney(account.balance, account.currency)}</b></p>
        <form id="rec-form" style="margin-top:16px">
            <div class="form-field"><span>Stvarno stanje — sa izvoda banke ili prebrojano</span>
                <input type="number" name="actual" step="0.01" required placeholder="0.00"></div>
            <p id="rec-diff" style="font-size:13px;margin-top:12px;min-height:20px;color:var(--text-2)">&nbsp;</p>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="rec-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">Uskladi stanje</button>
            </div>
        </form>`);

    const input = $('#rec-form', body).elements.actual;
    const diffLine = $('#rec-diff', body);
    input.addEventListener('input', () => {
        if (input.value === '') { diffLine.innerHTML = '&nbsp;'; return; }
        const diff = Number(input.value) - Number(account.balance);
        if (Math.abs(diff) < 0.005) {
            diffLine.textContent = 'Stanja se poklapaju — nema šta da se knjiži.';
        } else {
            diffLine.innerHTML = `Biće uknjižena transakcija usklađivanja: `
                + `<b class="${diff > 0 ? 'amount income' : 'amount expense'}">${diff > 0 ? '+' : '-'}${fmtMoney(Math.abs(diff), account.currency)}</b>`;
        }
    });

    $('#rec-cancel', body).addEventListener('click', closeModal);
    $('#rec-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        try {
            const result = await api(`/api/accounts/${account.id}/reconcile`, {
                method: 'POST',
                body: { actualBalance: Number(input.value) }
            });
            toast(result.adjusted
                ? `Stanje usklađeno — razlika od ${fmtMoney(Math.abs(result.difference), account.currency)} je uknjižena`
                : 'Stanja su se već poklapala');
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

// Prebacivanje novca izmedju racuna
function openTransferModal(accounts, onChange) {
    const options = selected => accounts.map(a =>
        `<option value="${a.id}" ${a.id === selected ? 'selected' : ''}>${esc(a.name)} (${esc(a.currency)})</option>`).join('');

    const body = openModal('Prebaci novac', `
        <form id="transfer-form">
            <div class="form-grid">
                <div class="form-field"><span>Sa računa</span>
                    <select name="fromAccountId">${options(accounts[0].id)}</select></div>
                <div class="form-field"><span>Na račun</span>
                    <select name="toAccountId">${options(accounts[1].id)}</select></div>
                <div class="form-field"><span>Iznos</span>
                    <input type="number" name="amount" step="0.01" min="0.01" required placeholder="0.00"></div>
                <div class="form-field"><span>Datum</span>
                    <input type="date" name="date" required value="${new Date().toISOString().slice(0, 10)}"></div>
                <div class="form-field full"><span>Opis (opciono)</span>
                    <input type="text" name="description" placeholder="npr. Mjesečna štednja"></div>
            </div>
            <div class="form-actions">
                <button type="submit" class="btn btn-primary">Prebaci</button>
            </div>
        </form>
        <div style="margin-top:20px">
            <span style="font-size:12.5px;font-weight:600;color:var(--text-2)">Posljednja prebacivanja</span>
            <div id="transfer-history"></div>
        </div>`);

    async function drawHistory() {
        const container = $('#transfer-history', body);
        let transfers;
        try {
            transfers = await api('/api/transfers');
        } catch (err) {
            container.innerHTML = `<p class="muted" style="font-size:13px;margin-top:8px">${esc(err.message)}</p>`;
            return;
        }
        container.innerHTML = transfers.length ? transfers.map(t => `
            <div class="tr-row" data-id="${t.id}">
                <span class="tr-date">${fmtDate(t.date)}</span>
                <span class="tr-route"><b>${esc(t.fromAccountName)}</b> → <b>${esc(t.toAccountName)}</b>
                    ${t.description ? `<span>${esc(t.description)}</span>` : ''}</span>
                <span class="amount">${fmtMoney(t.amount)}</span>
                <button class="icon-btn danger" type="button" title="Poništi prebacivanje">${icon('trash')}</button>
            </div>`).join('')
            : '<p class="muted" style="font-size:13px;margin-top:8px">Još nema prebacivanja.</p>';

        $$('.tr-row button', container).forEach(btn => btn.addEventListener('click', async () => {
            const ok = await confirmDialog({ title: 'Poništavanje prebacivanja',
                message: 'Novac se vraća na izvorni račun.', confirmText: 'Poništi' });
            if (!ok) return;
            const id = Number(btn.closest('.tr-row').dataset.id);
            try {
                await api('/api/transfers/' + id, { method: 'DELETE' });
                toast('Prebacivanje poništeno');
                drawHistory();
                onChange();
            } catch (err) { toast(err.message, 'error'); }
        }));
    }

    $('#transfer-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        try {
            await api('/api/transfers', {
                method: 'POST',
                body: {
                    amount: Number(form.get('amount')),
                    date: form.get('date'),
                    description: form.get('description') || null,
                    fromAccountId: Number(form.get('fromAccountId')),
                    toAccountId: Number(form.get('toAccountId'))
                }
            });
            toast('Novac je prebačen');
            e.target.elements.amount.value = '';
            e.target.elements.description.value = '';
            drawHistory();
            onChange();
        } catch (err) { toast(err.message, 'error'); }
    });

    drawHistory();
}

// Budzeti
async function renderBudgets() {
    const [budgets, goals] = await Promise.all([api('/api/budgets'), api('/api/goals'), loadRefs()]);

    const goalCard = g => {
        const pct = Math.min(g.percent, 100);
        let deadlineInfo = 'bez roka';
        if (g.deadline) {
            const days = Math.ceil((new Date(g.deadline) - Date.now()) / 86400000);
            deadlineInfo = days >= 0
                ? `do ${fmtDate(g.deadline)} — još ${days} ${days === 1 ? 'dan' : 'dana'}`
                : `rok je istekao ${fmtDate(g.deadline)}`;
        }
        return `<div class="card goal-card ${g.achieved ? 'done' : ''}" data-id="${g.id}">
            <div style="display:flex;justify-content:space-between;align-items:baseline;gap:10px">
                <strong style="font-size:16px">${esc(g.name)}</strong>
                ${g.achieved
                    ? `<span class="chip goal-done-chip">${icon('check', 'ico ico-sm')} Dostignut</span>`
                    : `<span class="chip">${g.percent}%</span>`}
            </div>
            <div class="stat-value" style="font-size:22px;margin-top:10px">${fmtMoney(g.savedAmount)} <span class="muted" style="font-size:14px;font-weight:600">/ ${fmtMoney(g.targetAmount)}</span></div>
            <div class="budget-bar"><div class="budget-bar-fill goal-fill" style="width:${pct}%"></div></div>
            <div class="stat-sub" style="margin-top:6px">${g.achieved
                ? 'Cilj je ispunjen — bravo!'
                : `nedostaje još ${fmtMoney(g.remaining)} · ${deadlineInfo}`}</div>
            ${g.accountId ? `<div style="margin-top:10px"><span class="chip"><span class="dot" style="background:var(--brand)"></span>Prati stanje: ${esc(g.accountName)}</span></div>` : ''}
            <div class="account-actions">
                ${g.achieved || g.accountId ? '' : `<button class="btn btn-secondary btn-sm" data-act="deposit">Uplati</button>`}
                <button class="btn btn-danger btn-sm" data-act="delete">Obriši</button>
            </div>
        </div>`;
    };

    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Budžeti</h1><p>Postavite limite potrošnje i pratite koliko ste ih iskoristili</p></div>
            <button class="btn btn-primary" id="add-budget">${icon('plus', 'ico ico-sm')} Novi budžet</button>
        </div>
        <div id="budget-section">
        ${budgets.length ? `<div class="accounts-grid" id="budget-grid">${budgets.map(b => {
            const pct = Math.min(b.percentUsed, 100);
            const cls = b.percentUsed >= 100 ? 'over' : (b.percentUsed >= 80 ? 'warn' : '');
            return `<div class="card" data-id="${b.id}">
                <div style="display:flex;justify-content:space-between;align-items:baseline">
                    <strong style="font-size:16px">${esc(b.name)}</strong>
                    <span class="chip">${PERIODS[b.period]}</span>
                </div>
                <div class="stat-value" style="font-size:22px;margin-top:10px">${fmtMoney(b.spent)} <span class="muted" style="font-size:14px;font-weight:600">/ ${fmtMoney(b.limitAmount)}</span></div>
                <div class="budget-bar"><div class="budget-bar-fill ${cls}" style="width:${pct}%"></div></div>
                <div class="stat-sub" style="margin-top:6px">${b.percentUsed}% iskorišteno - preostalo ${fmtMoney(b.remaining)}</div>
                <div style="margin-top:10px">${b.categories.length
                    ? b.categories.map(c => `<span class="chip" style="margin:2px 2px 0 0"><span class="dot" style="background:${esc(c.color || '#94a3b8')}"></span>${esc(c.name)}</span>`).join('')
                    : '<span class="chip">Svi rashodi</span>'}</div>
                <div class="account-actions">
                    <button class="btn btn-secondary btn-sm" data-act="edit">Uredi</button>
                    <button class="btn btn-danger btn-sm" data-act="delete">Obriši</button>
                </div>
            </div>`;
        }).join('')}</div>`
        : `<div class="card"><div class="empty-state"><span class="emoji">🎯</span><p>Još nemate budžete. Postavite limit potrošnje za kategorije koje želite kontrolisati.</p>
            <button class="btn btn-primary" id="add-budget-empty">+ Novi budžet</button></div></div>`}
        </div>

        <div class="page-header" style="margin-top:36px">
            <div><h1 style="font-size:19px">Ciljevi štednje</h1><p>Koliko želite skupiti i dokle ste stigli</p></div>
            <button class="btn btn-secondary" id="add-goal" type="button">${icon('plus', 'ico ico-sm')} Novi cilj</button>
        </div>
        ${goals.length
            ? `<div class="accounts-grid" id="goal-grid">${goals.map(goalCard).join('')}</div>`
            : `<div class="card"><div class="empty-state"><span class="emoji">🏝️</span><p>Još nemate ciljeve. Postavite šta želite skupiti — ljetovanje, laptop, rezerva za crne dane.</p>
                <button class="btn btn-primary" id="add-goal-empty" type="button">+ Novi cilj</button></div></div>`}`;

    const openForm = (budget) => openBudgetModal(budget, renderBudgets);
    if ($('#add-budget')) $('#add-budget').addEventListener('click', () => openForm(null));
    if ($('#add-budget-empty')) $('#add-budget-empty').addEventListener('click', () => openForm(null));

    $$('#budget-grid .card[data-id] [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.card').dataset.id);
        const budget = budgets.find(b => b.id === id);
        if (btn.dataset.act === 'edit') {
            openForm(budget);
        } else if (await confirmDialog({ title: 'Brisanje budžeta',
                message: `Budžet "${budget.name}" će biti obrisan. Transakcije ostaju netaknute.` })) {
            try {
                await api('/api/budgets/' + id, { method: 'DELETE' });
                toast('Budžet obrisan');
                renderBudgets();
            } catch (err) { toast(err.message, 'error'); }
        }
    }));

    if ($('#add-goal')) $('#add-goal').addEventListener('click', () => openGoalModal(renderBudgets));
    if ($('#add-goal-empty')) $('#add-goal-empty').addEventListener('click', () => openGoalModal(renderBudgets));

    $$('#goal-grid [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.card').dataset.id);
        const goal = goals.find(g => g.id === id);
        if (btn.dataset.act === 'deposit') {
            openDepositModal(goal, renderBudgets);
        } else if (await confirmDialog({ title: 'Brisanje cilja',
                message: `Cilj "${goal.name}" i zabilježeni napredak će biti obrisani.` })) {
            try {
                await api('/api/goals/' + id, { method: 'DELETE' });
                toast('Cilj obrisan');
                renderBudgets();
            } catch (err) { toast(err.message, 'error'); }
        }
    }));
}

// Novi cilj stednje
function openGoalModal(onSaved) {
    const body = openModal('Novi cilj štednje', `
        <form id="goal-form">
            <div class="form-grid">
                <div class="form-field full"><span>Naziv</span>
                    <input type="text" name="name" required placeholder="npr. Ljetovanje"></div>
                <div class="form-field"><span>Ciljni iznos (EUR)</span>
                    <input type="number" name="targetAmount" step="0.01" min="0.01" required placeholder="0.00"></div>
                <div class="form-field"><span>Rok (opciono)</span>
                    <input type="date" name="deadline"></div>
                <div class="form-field full"><span>Prati stanje računa (opciono)</span>
                    <select name="accountId">
                        <option value="">Ne — vodim uplate ručno</option>
                        ${state.accounts.map(a => `<option value="${a.id}">${esc(a.name)} — trenutno ${fmtMoney(a.balance, a.currency)}</option>`).join('')}
                    </select></div>
            </div>
            <p class="muted" style="font-size:12.5px;margin-top:10px">Ako cilj prati račun (npr. Štednju),
                napredak se ažurira sam: prebacite novac na taj račun i cilj raste, bez ručnih uplata.</p>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="goal-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">Kreiraj cilj</button>
            </div>
        </form>`);

    $('#goal-cancel', body).addEventListener('click', closeModal);
    $('#goal-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        try {
            await api('/api/goals', {
                method: 'POST',
                body: {
                    name: form.get('name'),
                    targetAmount: Number(form.get('targetAmount')),
                    deadline: form.get('deadline') || null,
                    accountId: form.get('accountId') ? Number(form.get('accountId')) : null
                }
            });
            toast('Cilj kreiran');
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

// Uplata na cilj
function openDepositModal(goal, onSaved) {
    const body = openModal('Uplata: ' + goal.name, `
        <form id="deposit-form">
            <div class="form-field"><span>Iznos uplate (EUR)</span>
                <input type="number" name="amount" step="0.01" min="0.01" required placeholder="0.00"></div>
            <div class="quick-amounts">
                ${[10, 20, 50, 100].map(v =>
                    `<button type="button" class="chip qa" data-v="${v}">+${v} EUR</button>`).join('')}
            </div>
            <p class="muted" style="font-size:12.5px;margin-top:12px">Do cilja nedostaje još ${fmtMoney(goal.remaining)}.</p>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="deposit-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">Uplati</button>
            </div>
        </form>`);

    $$('.qa', body).forEach(chipBtn => chipBtn.addEventListener('click', () => {
        const input = $('#deposit-form', body).elements.amount;
        input.value = ((Number(input.value) || 0) + Number(chipBtn.dataset.v)).toFixed(2);
    }));

    $('#deposit-cancel', body).addEventListener('click', closeModal);
    $('#deposit-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const amount = Number(new FormData(e.target).get('amount'));
        try {
            const updated = await api(`/api/goals/${goal.id}/deposit`, { method: 'POST', body: { amount } });
            toast(updated.achieved ? `Čestitamo — cilj "${updated.name}" je dostignut!` : 'Uplata zabilježena');
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

function openBudgetModal(budget, onSaved) {
    const isEdit = !!budget;
    const expenseCategories = state.categories.filter(c => c.type === 'EXPENSE');
    const selected = isEdit ? budget.categories.map(c => c.id) : [];
    const body = openModal(isEdit ? 'Uredi budžet' : 'Novi budžet', `
        <form id="budget-form">
            <div class="form-grid">
                <div class="form-field full"><span>Naziv</span>
                    <input type="text" name="name" required value="${isEdit ? esc(budget.name) : ''}" placeholder="npr. Hrana mjesečno"></div>
                <div class="form-field"><span>Limit</span>
                    <input type="number" name="limitAmount" step="0.01" min="0.01" required value="${isEdit ? budget.limitAmount : ''}"></div>
                <div class="form-field"><span>Period</span>
                    <select name="period">
                        <option value="MONTHLY" ${isEdit && budget.period === 'MONTHLY' ? 'selected' : ''}>Mjesečno</option>
                        <option value="YEARLY" ${isEdit && budget.period === 'YEARLY' ? 'selected' : ''}>Godišnje</option>
                    </select></div>
                <div class="form-field full"><span>Kategorije (prazno = svi rashodi)</span>
                    <div style="display:flex;flex-wrap:wrap;gap:8px;padding:4px 0">
                        ${expenseCategories.map(c => `
                            <label style="display:inline-flex;align-items:center;gap:6px;font-size:13px;border:1px solid var(--border);border-radius:999px;padding:5px 12px;cursor:pointer">
                                <input type="checkbox" name="categoryIds" value="${c.id}" style="width:auto" ${selected.includes(c.id) ? 'checked' : ''}>
                                ${esc(c.icon || '')} ${esc(c.name)}
                            </label>`).join('')}
                    </div></div>
            </div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="budget-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">${isEdit ? 'Sačuvaj' : 'Kreiraj budžet'}</button>
            </div>
        </form>`);

    $('#budget-cancel', body).addEventListener('click', closeModal);
    $('#budget-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        const payload = {
            name: form.get('name'),
            limitAmount: Number(form.get('limitAmount')),
            period: form.get('period'),
            categoryIds: form.getAll('categoryIds').map(Number)
        };
        try {
            if (isEdit) {
                await api('/api/budgets/' + budget.id, { method: 'PUT', body: payload });
                toast('Budžet ažuriran');
            } else {
                await api('/api/budgets', { method: 'POST', body: payload });
                toast('Budžet kreiran');
            }
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

// Kategorije
async function renderCategories() {
    const categories = await api('/api/categories');
    const column = (type, title) => `
        <div class="card">
            <h3>${title}</h3>
            ${categories.filter(c => c.type === type).map(c => `
                <div class="cat-row" data-id="${c.id}">
                    <span class="cat-ico" style="background:${esc(c.color || '#94a3b8')}22">${esc(c.icon || '📁')}</span>
                    <span class="cat-name">${esc(c.name)}</span>
                    <span class="dot" style="background:${esc(c.color || '#94a3b8')}"></span>
                    <button class="icon-btn" data-act="edit" title="Uredi">${icon('edit')}</button>
                    <button class="icon-btn danger" data-act="delete" title="Obriši">${icon('trash')}</button>
                </div>`).join('') || '<p class="muted">Nema kategorija.</p>'}
        </div>`;

    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Kategorije</h1><p>Organizujte transakcije po kategorijama</p></div>
            <button class="btn btn-primary" id="add-category">${icon('plus', 'ico ico-sm')} Nova kategorija</button>
        </div>
        <div class="cat-columns">
            ${column('EXPENSE', '💸 Rashodi')}
            ${column('INCOME', '💵 Prihodi')}
        </div>`;

    $('#add-category').addEventListener('click', () => openCategoryModal(null, renderCategories));
    $$('.cat-row [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.cat-row').dataset.id);
        const category = categories.find(c => c.id === id);
        if (btn.dataset.act === 'edit') {
            openCategoryModal(category, renderCategories);
        } else if (await confirmDialog({ title: 'Brisanje kategorije',
                message: `Kategorija "${category.name}" će biti obrisana sa transakcija koje je koriste.` })) {
            try {
                await api('/api/categories/' + id, { method: 'DELETE' });
                toast('Kategorija obrisana');
                renderCategories();
            } catch (err) { toast(err.message, 'error'); }
        }
    }));
}

function openCategoryModal(category, onSaved) {
    const isEdit = !!category;
    const body = openModal(isEdit ? 'Uredi kategoriju' : 'Nova kategorija', `
        <form id="category-form">
            <div class="form-grid">
                <div class="form-field full"><span>Naziv</span>
                    <input type="text" name="name" required value="${isEdit ? esc(category.name) : ''}"></div>
                <div class="form-field"><span>Tip</span>
                    <select name="type">
                        <option value="EXPENSE" ${isEdit && category.type === 'EXPENSE' ? 'selected' : ''}>Rashod</option>
                        <option value="INCOME" ${isEdit && category.type === 'INCOME' ? 'selected' : ''}>Prihod</option>
                    </select></div>
                <div class="form-field"><span>Boja</span>
                    <input type="color" name="color" value="${isEdit && category.color ? category.color : '#059669'}"></div>
                <div class="form-field full"><span>Ikona (emoji)</span>
                    <input type="text" name="icon" maxlength="4" value="${isEdit ? esc(category.icon || '') : ''}" placeholder="npr. 🍔"></div>
            </div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="category-cancel">Odustani</button>
                <button type="submit" class="btn btn-primary">${isEdit ? 'Sačuvaj' : 'Kreiraj'}</button>
            </div>
        </form>`);

    $('#category-cancel', body).addEventListener('click', closeModal);
    $('#category-form', body).addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        const payload = {
            name: form.get('name'),
            type: form.get('type'),
            color: form.get('color'),
            icon: form.get('icon') || null
        };
        try {
            if (isEdit) {
                await api('/api/categories/' + category.id, { method: 'PUT', body: payload });
                toast('Kategorija ažurirana');
            } else {
                await api('/api/categories', { method: 'POST', body: payload });
                toast('Kategorija kreirana');
            }
            closeModal();
            onSaved();
        } catch (err) { toast(err.message, 'error'); }
    });
}

// Alati (vanjski API-ji)
async function renderTools() {
    // Frankfurter (ECB) podrzava samo glavne svjetske valute
    const currencies = ['EUR', 'USD', 'GBP', 'CHF', 'JPY', 'CAD', 'AUD', 'SEK', 'NOK', 'PLN', 'CZK', 'DKK'];
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Alati</h1><p>Konverzija valuta, lokacija i tačno vrijeme - na osnovu vanjskih servisa</p></div>
        </div>
        <div class="tools-grid">
            <div class="card">
                <h3>💱 Konverter valuta</h3>
                <div class="form-grid">
                    <div class="form-field"><span>Iz</span>
                        <select id="cur-from">${currencies.map(c => `<option ${c === 'EUR' ? 'selected' : ''}>${c}</option>`).join('')}</select></div>
                    <div class="form-field"><span>U</span>
                        <select id="cur-to">${currencies.map(c => `<option ${c === 'USD' ? 'selected' : ''}>${c}</option>`).join('')}</select></div>
                    <div class="form-field full"><span>Iznos</span>
                        <input type="number" id="cur-value" step="0.01" value="100"></div>
                </div>
                <div class="form-actions" style="justify-content:flex-start">
                    <button class="btn btn-primary" id="cur-convert">Konvertuj</button>
                </div>
                <div id="cur-result"></div>
            </div>
            <div class="card">
                <h3>📍 Moja lokacija</h3>
                <p class="muted" style="font-size:13.5px">Detekcija lokacije na osnovu javne IP adrese servera.</p>
                <div class="form-actions" style="justify-content:flex-start">
                    <button class="btn btn-primary" id="loc-btn">Detektuj lokaciju</button>
                </div>
                <div id="loc-result"></div>
            </div>
            <div class="card">
                <h3>🕐 Vremenska zona</h3>
                <p class="muted" style="font-size:13.5px">Tačno vrijeme i vremenska zona na osnovu IP adrese.</p>
                <div class="form-actions" style="justify-content:flex-start">
                    <button class="btn btn-primary" id="tz-btn">Provjeri vrijeme</button>
                </div>
                <div id="tz-result"></div>
            </div>
        </div>
        <div class="card" style="margin-top:16px">
            <h3>Istorija konverzija</h3>
            <div id="cur-history"><p class="muted">Učitavanje...</p></div>
        </div>`;

    async function loadHistory() {
        try {
            const history = await api('/api/tools/currency/history');
            $('#cur-history').innerHTML = history.length ? `<div class="table-wrap"><table>
                <thead><tr><th>Iz</th><th>U</th><th>Kurs</th><th>Iznos</th><th>Rezultat</th><th>Datum</th></tr></thead>
                <tbody>${history.map(h => `<tr>
                    <td>${esc(h.from)}</td><td>${esc(h.to)}</td>
                    <td>${Number(h.rate).toFixed(4)}</td>
                    <td>${Number(h.value).toFixed(2)}</td>
                    <td class="amount">${Number(h.convertedValue).toFixed(2)}</td>
                    <td class="muted">${esc(h.date || '-')}</td></tr>`).join('')}</tbody>
            </table></div>` : '<p class="muted">Još nema konverzija.</p>';
        } catch (err) {
            $('#cur-history').innerHTML = `<p class="muted">${esc(err.message)}</p>`;
        }
    }

    $('#cur-convert').addEventListener('click', async () => {
        const btn = $('#cur-convert');
        btn.disabled = true;
        try {
            const params = new URLSearchParams({
                from: $('#cur-from').value, to: $('#cur-to').value, value: $('#cur-value').value || '0'
            });
            const result = await api('/api/tools/currency?' + params);
            $('#cur-result').innerHTML = `<div class="tool-result">
                <div class="big">${Number(result.convertedValue).toFixed(2)} ${esc(result.to)}</div>
                <div class="muted">${Number(result.value).toFixed(2)} ${esc(result.from)} - kurs ${Number(result.rate).toFixed(4)}</div>
            </div>`;
            loadHistory();
        } catch (err) { toast(err.message, 'error'); } finally { btn.disabled = false; }
    });

    $('#loc-btn').addEventListener('click', async () => {
        const btn = $('#loc-btn');
        btn.disabled = true;
        try {
            const loc = await api('/api/tools/location');
            $('#loc-result').innerHTML = `<div class="tool-result"><dl class="kv">
                <dt>Grad</dt><dd>${esc(loc.city || '-')}</dd>
                <dt>Država</dt><dd>${esc(loc.country_name || '-')} ${esc(loc.country_code || '')}</dd>
                <dt>IP adresa</dt><dd>${esc(loc.ip || '-')}</dd>
                <dt>Koordinate</dt><dd>${loc.latitude}, ${loc.longitude}</dd>
                <dt>Valuta</dt><dd>${esc(loc.currency || '-')}</dd>
            </dl></div>`;
        } catch (err) { toast(err.message, 'error'); } finally { btn.disabled = false; }
    });

    $('#tz-btn').addEventListener('click', async () => {
        const btn = $('#tz-btn');
        btn.disabled = true;
        try {
            const tz = await api('/api/tools/timezone');
            $('#tz-result').innerHTML = `<div class="tool-result">
                <div class="big">${esc(tz.time || '-')}</div>
                <dl class="kv" style="margin-top:8px">
                    <dt>Datum</dt><dd>${esc(tz.date || '-')}</dd>
                    <dt>Zona</dt><dd>${esc(tz.timeZone || '-')}</dd>
                    <dt>Dan</dt><dd>${esc(tz.dayOfWeek || '-')}</dd>
                </dl>
            </div>`;
        } catch (err) { toast(err.message, 'error'); } finally { btn.disabled = false; }
    });

    loadHistory();
}

// Profil
async function renderProfile() {
    const me = await api('/api/auth/me');
    const profile = me.profile || {};
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Profil</h1><p>Lični podaci i sigurnost naloga</p></div>
        </div>
        <div class="dash-grid-2">
            <div class="card">
                <h3>Lični podaci</h3>
                <form id="profile-form">
                    <div class="form-grid">
                        <div class="form-field full"><span>Ime i prezime</span>
                            <input type="text" name="name" required value="${esc(me.name)}"></div>
                        <div class="form-field full"><span>Email</span>
                            <input type="email" value="${esc(me.email)}" disabled></div>
                        <div class="form-field full"><span>Adresa</span>
                            <input type="text" name="address" value="${esc(profile.address || '')}"></div>
                        <div class="form-field"><span>Telefon</span>
                            <input type="text" name="phone" value="${esc(profile.phone || '')}"></div>
                        <div class="form-field"><span>Datum rođenja</span>
                            <input type="date" name="dateOfBirth" value="${profile.dateOfBirth || ''}"></div>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Sačuvaj</button>
                    </div>
                </form>
            </div>
            <div class="card">
                <h3>Promjena lozinke</h3>
                <form id="password-form">
                    <div class="form-grid">
                        <div class="form-field full"><span>Trenutna lozinka</span>
                            <input type="password" name="currentPassword" required autocomplete="current-password"></div>
                        <div class="form-field full"><span>Nova lozinka</span>
                            <input type="password" name="newPassword" required minlength="6" autocomplete="new-password"></div>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Promijeni lozinku</button>
                    </div>
                </form>
                <div class="tool-result" style="margin-top:20px">
                    <dl class="kv">
                        <dt>Uloga</dt><dd>${me.role === 'ADMIN' ? 'Administrator' : 'Korisnik'}</dd>
                        <dt>Email adresa</dt>
                        <dd>${me.emailVerified
                            ? '<span class="verif-badge ok">Potvrđena</span>'
                            : '<span class="verif-badge pending">Nije potvrđena</span>'}</dd>
                        <dt>Nalog kreiran</dt><dd>${new Date(me.createdAt).toLocaleDateString('sr-ME')}</dd>
                    </dl>
                    ${me.emailVerified ? '' : `
                    <div class="verif-cta">
                        <p>Potvrdite email adresu da osigurate pristup nalogu i mogućnost resetovanja lozinke.</p>
                        <button type="button" class="btn btn-secondary btn-sm" id="resend-verif">Pošalji link ponovo</button>
                    </div>`}
                </div>
            </div>
        </div>`;

    $('#profile-form').addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        try {
            const updated = await api('/api/auth/profile', {
                method: 'PUT',
                body: {
                    name: form.get('name'),
                    address: form.get('address') || null,
                    phone: form.get('phone') || null,
                    dateOfBirth: form.get('dateOfBirth') || null
                }
            });
            state.user = updated;
            localStorage.setItem('pfm_user', JSON.stringify(updated));
            $('#user-name').textContent = updated.name;
            toast('Profil sačuvan');
        } catch (err) { toast(err.message, 'error'); }
    });

    $('#password-form').addEventListener('submit', async e => {
        e.preventDefault();
        const form = new FormData(e.target);
        try {
            await api('/api/auth/change-password', {
                method: 'POST',
                body: { currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') }
            });
            e.target.reset();
            toast('Lozinka promijenjena');
        } catch (err) { toast(err.message, 'error'); }
    });

    const resend = $('#resend-verif');
    if (resend) resend.addEventListener('click', async () => {
        resend.disabled = true;
        try {
            await api('/api/auth/resend-verification', { method: 'POST', body: { email: me.email } });
            toast('Link za potvrdu je poslat na email');
        } catch (err) {
            toast(err.message, 'error');
            resend.disabled = false;
        }
    });
}

// Administracija
async function renderAdmin() {
    const users = await api('/api/admin/users');
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Administracija</h1><p>Registrovani korisnici sistema</p></div>
        </div>
        <div class="card">
            <div class="table-wrap"><table>
                <thead><tr><th>ID</th><th>Ime</th><th>Email</th><th>Uloga</th><th>Registrovan</th></tr></thead>
                <tbody>${users.map(u => `<tr>
                    <td class="muted">#${u.id}</td>
                    <td><strong>${esc(u.name)}</strong></td>
                    <td>${esc(u.email)}</td>
                    <td><span class="chip">${u.role === 'ADMIN' ? '⛨ Administrator' : 'Korisnik'}</span></td>
                    <td class="muted">${new Date(u.createdAt).toLocaleDateString('sr-ME')}</td>
                </tr>`).join('')}</tbody>
            </table></div>
        </div>`;
}

// PWA: service worker omogucava instalaciju i rad bez mreze
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch(() => {
            // aplikacija normalno radi i bez service workera
        });
    });
}

// Inicijalizacija
(async function init() {
    // Da li je Google prijava dostupna (client id sa servera)
    try {
        const cfg = await api('/api/auth/config');
        googleClientId = cfg.googleClientId;
        verificationRequired = cfg.emailVerificationRequired !== false;
    } catch (ignored) { /* Google prijava nije obavezna */ }

    const params = new URLSearchParams(location.search);

    // Link iz emaila za reset lozinke: prikazi formu za novu lozinku
    if (params.get('reset')) {
        resetToken = params.get('reset');
        showAuth();
        switchAuthTab('reset');
        return;
    }

    // Link iz emaila za potvrdu adrese: potvrdi token pa nastavi normalno
    if (params.get('verify')) {
        const token = params.get('verify');
        history.replaceState(null, '', location.pathname);
        try {
            await api('/api/auth/verify-email', { method: 'POST', body: { token } });
            if (state.user) state.user.emailVerified = true;
            verifyNotice = { ok: true, message: 'Email adresa je potvrđena.' };
        } catch (err) {
            verifyNotice = { ok: false, message: err.message };
        }
    }

    // Ako nema pristupnog tokena, probaj obnoviti sesiju preko refresh kolacica -
    // tako korisnik ostaje prijavljen (i poslije zatvaranja browsera) dok se sam ne odjavi.
    if (!state.token) {
        const refreshed = await refreshAccessToken();
        if (!refreshed) {
            showAuth();
            if (verifyNotice) {
                verifyNotice.ok ? showAuthSuccess(verifyNotice.message) : showAuthError(verifyNotice.message);
                verifyNotice = null;
            }
            return;
        }
    }
    try {
        const me = await api('/api/auth/me');
        state.user = me;
        localStorage.setItem('pfm_user', JSON.stringify(me));
        showApp();
        if (verifyNotice) {
            toast(verifyNotice.message);
            verifyNotice = null;
        }
    } catch (err) {
        logout();
    }
})();
