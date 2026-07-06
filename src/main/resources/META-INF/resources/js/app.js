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
    scale: '<path d="M12 3v18"/><path d="M8 21h8"/><path d="m5 7 3 7a3 3 0 0 1-6 0z"/><path d="m19 7 3 7a3 3 0 0 1-6 0z"/><path d="M4 7h16"/>'
};

function icon(name, cls = 'ico') {
    return `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name]}</svg>`;
}

// API klijent
async function api(path, options = {}) {
    const headers = options.headers || {};
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
    let body = options.body;
    if (body && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
    }
    const res = await fetch(path, { method: options.method || 'GET', headers, body });
    if (res.status === 401 && state.token) {
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
        throw new Error(message);
    }
    if (res.status === 204) return null;
    return res.json();
}

// Toast + modal
function toast(message, type = 'success') {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 4200);
}

function openModal(title, bodyHtml) {
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

// Autentifikacija
function saveAuth(auth) {
    state.token = auth.token;
    state.user = auth.user;
    localStorage.setItem('pfm_token', auth.token);
    localStorage.setItem('pfm_user', JSON.stringify(auth.user));
}

function logout() {
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
    if (!landingReady) {
        landingReady = true;
        initLanding();
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
    $('#cta-demo').addEventListener('click', () => {
        const form = $('#login-form');
        form.elements.email.value = 'demo@pfm.me';
        form.elements.password.value = 'demo123';
        form.requestSubmit();
    });

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

    // Interaktivni budzet demo: slider mijenja limit, traka i boja reaguju
    const budgetRange = $('#bd-range');
    if (budgetRange) {
        const spent = 317;
        const fill = $('#bd-fill');
        const label = $('#bd-label');
        budgetRange.addEventListener('input', () => {
            const limit = Number(budgetRange.value);
            const usage = spent / limit;
            fill.style.transition = 'width .2s ease';
            fill.style.setProperty('--w', Math.min(usage * 100, 100).toFixed(1) + '%');
            fill.classList.toggle('warn', usage >= .8 && usage < 1);
            fill.classList.toggle('over', usage >= 1);
            label.innerHTML = `<b>${spent}</b> / ${limit} EUR`;
        });
    }

    // Tooltip sa iznosima na landing grafikonu
    const lpChart = $('#lp-chart');
    if (lpChart) {
        const tip = document.createElement('div');
        tip.className = 'chart-tip';
        lpChart.appendChild(tip);
        $$('.mock-group', lpChart).forEach(group => {
            group.addEventListener('mouseenter', () => {
                tip.innerHTML = `
                    <div class="tip-title">${group.dataset.m}</div>
                    <div class="tip-row"><span class="swatch" style="background:#059669"></span>Prihodi <b>+${group.dataset.i} EUR</b></div>
                    <div class="tip-row"><span class="swatch" style="background:#e34948"></span>Rashodi <b>-${group.dataset.e} EUR</b></div>`;
                tip.classList.add('show');
            });
            group.addEventListener('mousemove', e => {
                const rect = lpChart.getBoundingClientRect();
                const x = Math.min(e.clientX - rect.left + 14, rect.width - tip.offsetWidth - 8);
                const y = e.clientY - rect.top - tip.offsetHeight - 10;
                tip.style.left = Math.max(x, 8) + 'px';
                tip.style.top = Math.max(y, 4) + 'px';
            });
            group.addEventListener('mouseleave', () => tip.classList.remove('show'));
        });
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

    // Blagi 3D tilt na karticama kolaza (samo mis, ne dodirni ekrani)
    const canTilt = window.matchMedia('(hover: hover) and (pointer: fine)').matches
        && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (canTilt) {
        $$('#auth-screen .app-mock').forEach(card => {
            card.style.transition = 'transform .18s ease-out';
            card.addEventListener('mousemove', e => {
                const rect = card.getBoundingClientRect();
                const x = (e.clientX - rect.left) / rect.width - .5;
                const y = (e.clientY - rect.top) / rect.height - .5;
                card.style.transform = `perspective(900px) rotateY(${(x * 4).toFixed(2)}deg) rotateX(${(-y * 4).toFixed(2)}deg)`;
            });
            card.addEventListener('mouseleave', () => { card.style.transform = ''; });
        });
    }
}

function showApp() {
    $('#auth-screen').classList.add('hidden');
    $('#main-layout').classList.remove('hidden');
    const user = state.user;
    $('#user-name').textContent = user.name;
    $('#user-email').textContent = user.email;
    $('#user-avatar').textContent = user.name.trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
    $('#nav-admin').classList.toggle('hidden', user.role !== 'ADMIN');
    if (!location.hash || location.hash === '#/') location.hash = '#/dashboard';
    route();
}

function showAuthError(message) {
    const el = $('#auth-error');
    el.textContent = message;
    el.classList.remove('hidden');
}

$('#tab-login').addEventListener('click', () => switchAuthTab('login'));
$('#tab-register').addEventListener('click', () => switchAuthTab('register'));

function switchAuthTab(tab) {
    $('#tab-login').classList.toggle('active', tab === 'login');
    $('#tab-register').classList.toggle('active', tab === 'register');
    $('#login-form').classList.toggle('hidden', tab !== 'login');
    $('#register-form').classList.toggle('hidden', tab !== 'register');
    $('#auth-error').classList.add('hidden');
}

$('#login-form').addEventListener('submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
        const auth = await api('/api/auth/login', {
            method: 'POST',
            body: { email: form.get('email'), password: form.get('password') }
        });
        saveAuth(auth);
        showApp();
    } catch (err) {
        showAuthError(err.message);
    }
});

$('#register-form').addEventListener('submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
        const auth = await api('/api/auth/register', {
            method: 'POST',
            body: { name: form.get('name'), email: form.get('email'), password: form.get('password') }
        });
        saveAuth(auth);
        toast('Dobrodošli, ' + auth.user.name + '!');
        showApp();
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

function route() {
    if (!state.token) return;
    const name = (location.hash.replace('#/', '') || 'dashboard').split('?')[0];
    const render = routes[name] || renderDashboard;
    $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.route === name));
    $('#view').innerHTML = '<div class="loading-state"><span class="spinner"></span>Učitavanje...</div>';
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

// SVG grafikoni
// Boje serija su validirane dataviz validatorom (CVD, kontrast):
// prihodi #059669, rashodi #e34948
const CHART = { income: '#059669', expense: '#e34948' };

function svgDoughnut(items) {
    const total = items.reduce((sum, it) => sum + Number(it.amount), 0);
    if (!total) return '<p class="muted">Nema rashoda u ovom mjesecu.</p>';
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
        <div class="legend-row">
            <span class="dot" style="background:${esc(it.color)}"></span>
            <span class="name">${esc(it.name)}</span>
            <span class="legend-value">${fmtMoney(it.amount)}</span>
        </div>`).join('');
    return `<div class="doughnut-wrap">
        <svg viewBox="0 0 42 42" width="168" height="168" style="flex-shrink:0" role="img" aria-label="Rashodi po kategorijama">
            ${circles}
            <text x="21" y="20.2" text-anchor="middle" font-size="5.4" font-weight="800" letter-spacing="-.1" fill="#0c111d">${Number(total).toLocaleString('sr-ME', { maximumFractionDigits: 0 })}</text>
            <text x="21" y="25.6" text-anchor="middle" font-size="2.5" font-weight="500" fill="#8b94a3">EUR OVAJ MJESEC</text>
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
        return `<line x1="${padL}" y1="${y}" x2="${W - padR}" y2="${y}" stroke="${t === 0 ? '#c9cfd9' : '#eceff4'}"></line>
            <text x="${padL - 8}" y="${y + 3.5}" text-anchor="end" font-size="10.5" fill="#8b94a3" style="font-variant-numeric:tabular-nums">${label}</text>`;
    }).join('');

    const bars = flow.map((f, i) => {
        const cx = padL + i * groupW + groupW / 2;
        const hInc = Math.max(Number(f.income) / max * chartH, Number(f.income) > 0 ? 2 : 0);
        const hExp = Math.max(Number(f.expense) / max * chartH, Number(f.expense) > 0 ? 2 : 0);
        return `<g class="bar-group" data-i="${i}">
            <rect class="bar-hit" x="${padL + i * groupW}" y="${padT}" width="${groupW}" height="${chartH}" fill="transparent"></rect>
            ${hInc ? `<path class="bar" d="${barPath(cx - barW - 1, baseline - hInc, barW, hInc, 4)}" fill="${CHART.income}"></path>` : ''}
            ${hExp ? `<path class="bar" d="${barPath(cx + 1, baseline - hExp, barW, hExp, 4)}" fill="${CHART.expense}"></path>` : ''}
            <text x="${cx}" y="${H - 8}" text-anchor="middle" font-size="11" fill="#8b94a3">${esc(fmtMonth(f.month))}</text>
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
            const f = flow[Number(group.dataset.i)];
            tip.innerHTML = `
                <div class="tip-title">${esc(fmtMonth(f.month))}</div>
                <div class="tip-row"><span class="swatch" style="background:${CHART.income}"></span>Prihodi <b>+${fmtMoney(f.income)}</b></div>
                <div class="tip-row"><span class="swatch" style="background:${CHART.expense}"></span>Rashodi <b>-${fmtMoney(f.expense)}</b></div>`;
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
async function renderDashboard() {
    const d = await api('/api/dashboard');
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
            <button class="btn btn-primary" id="dash-add-tx">${icon('plus', 'ico ico-sm')} Nova transakcija</button>
        </div>
        <div class="stats-grid">
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Ukupno stanje</span><span class="stat-ico">${icon('wallet')}</span></div>
                <div class="stat-value">${fmtMoney(d.totalBalance)}</div>
                <div class="stat-sub">${d.accountCount} račun(a)</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Prihodi ovaj mjesec</span><span class="stat-ico">${icon('trendUp')}</span></div>
                <div class="stat-value" style="color:var(--income)">+${fmtMoney(d.incomeThisMonth)}</div>
                <div class="stat-sub">svi računi zajedno</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Rashodi ovaj mjesec</span><span class="stat-ico red">${icon('trendDown')}</span></div>
                <div class="stat-value" style="color:var(--expense)">-${fmtMoney(d.expenseThisMonth)}</div>
                <div class="stat-sub">svi računi zajedno</div>
            </div>
            <div class="card stat-card">
                <div class="stat-top"><span class="stat-label">Neto ovaj mjesec</span><span class="stat-ico slate">${icon('scale')}</span></div>
                <div class="stat-value" style="color:${Number(d.netThisMonth) >= 0 ? 'var(--income)' : 'var(--expense)'}">${fmtMoney(d.netThisMonth)}</div>
                <div class="stat-sub">prihodi minus rashodi</div>
            </div>
        </div>
        <div class="dash-grid">
            <div class="card chart-card" id="flow-card">
                <div class="card-head">
                    <h3>Tok novca — zadnjih 6 mjeseci</h3>
                    <div class="chart-legend-top">
                        <span class="legend-item"><span class="swatch" style="background:${CHART.income}"></span>Prihodi</span>
                        <span class="legend-item"><span class="swatch" style="background:${CHART.expense}"></span>Rashodi</span>
                    </div>
                </div>
                ${svgBars(d.monthlyFlow)}
            </div>
            <div class="card"><h3>Rashodi po kategorijama</h3>${svgDoughnut(d.spendingByCategory)}</div>
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

    $('#dash-add-tx').addEventListener('click', async () => {
        await loadRefs();
        openTransactionModal(null, () => route());
    });
}

// Transakcije
async function renderTransactions() {
    await loadRefs();
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Transakcije</h1><p>Svi prihodi i rashodi</p></div>
            <button class="btn btn-primary" id="add-tx">${icon('plus', 'ico ico-sm')} Nova transakcija</button>
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

    async function loadTable() {
        const params = new URLSearchParams();
        if ($('#f-account').value) params.set('accountId', $('#f-account').value);
        if ($('#f-category').value) params.set('categoryId', $('#f-category').value);
        if ($('#f-type').value) params.set('type', $('#f-type').value);
        if ($('#f-from').value) params.set('from', $('#f-from').value);
        if ($('#f-to').value) params.set('to', $('#f-to').value);
        if ($('#f-search').value) params.set('search', $('#f-search').value);
        const transactions = await api('/api/transactions?' + params);
        if (!transactions.length) {
            $('#tx-table').innerHTML = `<div class="empty-state"><span class="emoji">🧾</span><p>Nema transakcija za zadate filtere.</p></div>`;
            return;
        }
        $('#tx-table').innerHTML = `<div class="table-wrap"><table>
            <thead><tr><th>Datum</th><th>Opis</th><th>Kategorija</th><th>Račun</th><th style="text-align:right">Iznos</th><th></th></tr></thead>
            <tbody>${transactions.map(t => `
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
        </table></div>`;

        $$('#tx-table [data-act]').forEach(btn => btn.addEventListener('click', async () => {
            const id = Number(btn.closest('tr').dataset.id);
            const transaction = transactions.find(t => t.id === id);
            if (btn.dataset.act === 'edit') {
                openTransactionModal(transaction, loadTable);
            } else if (confirm('Obrisati ovu transakciju? Stanje računa će biti vraćeno.')) {
                try {
                    await api('/api/transactions/' + id, { method: 'DELETE' });
                    toast('Transakcija obrisana');
                    loadTable();
                } catch (err) { toast(err.message, 'error'); }
            }
        }));
    }

    $('#f-apply').addEventListener('click', loadTable);
    $('#f-search').addEventListener('keydown', e => { if (e.key === 'Enter') loadTable(); });
    $('#add-tx').addEventListener('click', () => openTransactionModal(null, loadTable));
    await loadTable();
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
                await api('/api/transactions', { method: 'POST', body: payload });
                toast('Transakcija dodana');
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
            } else if (confirm('Obrisati prilog?')) {
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
            <button class="btn btn-primary" id="add-account">${icon('plus', 'ico ico-sm')} Novi račun</button>
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
                    <button class="btn btn-secondary btn-sm" data-act="edit">Uredi</button>
                    <button class="btn btn-danger btn-sm" data-act="delete">Obriši</button>
                </div>
            </div>`).join('')}</div>`
        : `<div class="card"><div class="empty-state"><span class="emoji">🏦</span><p>Još nemate nijedan račun. Kreirajte prvi da počnete pratiti finansije.</p>
             <button class="btn btn-primary" id="add-account-empty">+ Novi račun</button></div></div>`}`;

    const openForm = (account) => openAccountModal(account, renderAccounts);
    if ($('#add-account-empty')) $('#add-account-empty').addEventListener('click', () => openForm(null));
    if ($('#add-account')) $('#add-account').addEventListener('click', () => openForm(null));

    $$('.account-card [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.account-card').dataset.id);
        const account = accounts.find(a => a.id === id);
        if (btn.dataset.act === 'edit') {
            openForm(account);
        } else if (confirm(`Obrisati račun "${account.name}"? Sve njegove transakcije će biti obrisane.`)) {
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

// Budzeti
async function renderBudgets() {
    const [budgets] = await Promise.all([api('/api/budgets'), loadRefs()]);
    $('#view').innerHTML = `
        <div class="page-header">
            <div><h1>Budžeti</h1><p>Postavite limite potrošnje i pratite koliko ste ih iskoristili</p></div>
            <button class="btn btn-primary" id="add-budget">${icon('plus', 'ico ico-sm')} Novi budžet</button>
        </div>
        ${budgets.length ? `<div class="accounts-grid">${budgets.map(b => {
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
            <button class="btn btn-primary" id="add-budget-empty">+ Novi budžet</button></div></div>`}`;

    const openForm = (budget) => openBudgetModal(budget, renderBudgets);
    if ($('#add-budget')) $('#add-budget').addEventListener('click', () => openForm(null));
    if ($('#add-budget-empty')) $('#add-budget-empty').addEventListener('click', () => openForm(null));

    $$('.card[data-id] [data-act]').forEach(btn => btn.addEventListener('click', async () => {
        const id = Number(btn.closest('.card').dataset.id);
        const budget = budgets.find(b => b.id === id);
        if (btn.dataset.act === 'edit') {
            openForm(budget);
        } else if (confirm(`Obrisati budžet "${budget.name}"?`)) {
            try {
                await api('/api/budgets/' + id, { method: 'DELETE' });
                toast('Budžet obrisan');
                renderBudgets();
            } catch (err) { toast(err.message, 'error'); }
        }
    }));
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
        } else if (confirm(`Obrisati kategoriju "${category.name}"?`)) {
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
                        <dt>Nalog kreiran</dt><dd>${new Date(me.createdAt).toLocaleDateString('sr-ME')}</dd>
                    </dl>
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

// Inicijalizacija
(async function init() {
    if (!state.token) {
        showAuth();
        return;
    }
    try {
        const me = await api('/api/auth/me');
        state.user = me;
        localStorage.setItem('pfm_user', JSON.stringify(me));
        showApp();
    } catch (err) {
        logout();
    }
})();
