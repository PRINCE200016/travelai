/* TripMind — shared site shell.
   Every page loads config.js then site.js. Pages mark placeholders:
     <header data-site-header></header>   (add data-variant="photo" on the home hero)
     <footer data-site-footer></footer>
   and set <body data-page="plan|destinations|expert|saved|...">. */
(function () {
  'use strict';

  var CFG = window.TRIPMIND_CONFIG || { apiBase: '/api' };

  // ---------- Utilities ----------
  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function inr(n) {
    var v = Math.round(Number(n) || 0);
    return '₹' + v.toLocaleString('en-IN');
  }
  function storageGet(key, fallback) {
    try { var v = localStorage.getItem(key); return v == null ? fallback : JSON.parse(v); } catch (_) { return fallback; }
  }
  function storageSet(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch (_) { return false; }
  }

  // ---------- Icons (inline SVG, 24px grid, stroke) ----------
  var ICONS = {
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    back: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
    pin: '<path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    users: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5.2a3.5 3.5 0 0 1 0 6.6M18.5 20a6.5 6.5 0 0 0-3-5.5"/>',
    wallet: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18M16 15h2"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
    cloud: '<path d="M7 18a4.5 4.5 0 1 1 .9-8.9A5.5 5.5 0 0 1 18.5 11 3.5 3.5 0 0 1 18 18z"/>',
    crowd: '<circle cx="7" cy="9" r="2.5"/><circle cx="17" cy="9" r="2.5"/><circle cx="12" cy="7" r="2.5"/><path d="M3 19a4 4 0 0 1 8 0M13 19a4 4 0 0 1 8 0M8 16.5a4 4 0 0 1 8 0"/>',
    train: '<rect x="5" y="3.5" width="14" height="13" rx="3"/><path d="M5 11h14M9 20l-2 1.5M15 20l2 1.5M8.5 14h.01M15.5 14h.01"/>',
    plane: '<path d="M10.5 13.5 3 11l1.5-1.5 8 .5 4-4.5a2 2 0 0 1 3 3L15 12.5l.5 8L14 22l-2.5-7.5L8 17v2.5L6.5 21 5 18 2 16.5 3.5 15H6z"/>',
    bus: '<rect x="4" y="3.5" width="16" height="14" rx="2.5"/><path d="M4 10h16M7.5 20.5v-3M16.5 20.5v-3M8 14h.01M16 14h.01"/>',
    car: '<path d="M5 16.5V12l2-5h10l2 5v4.5"/><path d="M3.5 12h17v4.5h-17z"/><circle cx="7.5" cy="16.5" r="1.5"/><circle cx="16.5" cy="16.5" r="1.5"/>',
    bed: '<path d="M3 18V7M3 13h18v5M21 18v-3a3 3 0 0 0-3-3h-7v1"/><circle cx="7" cy="10" r="2"/>',
    food: '<path d="M7 3v8M5 3v4a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 2-2.5 5-2.5 8h3V21"/>',
    ticket: '<path d="M3 8.5V6.5a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v2a2.5 2.5 0 0 0 0 5v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2a2.5 2.5 0 0 0 0-5z"/><path d="M14 6v12" stroke-dasharray="2 2"/>',
    bookmark: '<path d="M6.5 3.5h11v17L12 16.5l-5.5 4z"/>',
    bookmarkFill: '<path d="M6.5 3.5h11v17L12 16.5l-5.5 4z" fill="currentColor"/>',
    share: '<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="m8.2 10.8 7.6-4.1M8.2 13.2l7.6 4.1"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5M12 7.5h.01"/>',
    alert: '<path d="M12 3.5 2.5 20h19z"/><path d="M12 10v4.5M12 17.5h.01"/>',
    sliders: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3.5 6.5 8.5 6.5 8.5-6.5"/>',
    phone: '<path d="M5 3.5h4l2 5-2.5 1.5a11 11 0 0 0 5.5 5.5l1.5-2.5 5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5.5a2 2 0 0 1 2-2z"/>',
    whatsapp: '<path d="M4 20l1.3-3.9A8 8 0 1 1 8 19z"/><path d="M9 9.5c0 3 2.5 5.5 5.5 5.5l1.2-1.4-2-1-1 .8a4 4 0 0 1-2-2l.8-1-1-2z"/>',
    refresh: '<path d="M20 11a8 8 0 0 0-14.5-4.5L4 8M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5L20 16M20 20v-4h-4"/>',
    trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
    external: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
    compass: '<circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2 5-5 2 2-5z"/>',
    leaf: '<path d="M5 19c0-8 5-13 15-14-1 10-6 15-14 15"/><path d="M5 19c3-3 6-5 9-7"/>',
    mountain: '<path d="m3 19 6.5-11 4 6.5 2.5-3.5L21 19z"/>',
    wave: '<path d="M3 15c2 0 2-2 4.5-2S10 15 12 15s2.5-2 4.5-2 2.5 2 4.5 2M3 19c2 0 2-2 4.5-2S10 19 12 19s2.5-2 4.5-2 2.5 2 4.5 2M12 4l2.5 6h-5z"/>',
    temple: '<path d="M12 3 9 7h6zM8 7h8l1 4H7zM6 11h12v9H6zM10 20v-4h4v4M4 20h16"/>',
    music: '<path d="M9 18V5l11-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
    landmark: '<path d="M3 21h18M5 21V10M19 21V10M9 21V10M15 21V10M2.5 10h19L12 3.5z"/>',
    paw: '<circle cx="7" cy="9" r="1.8"/><circle cx="17" cy="9" r="1.8"/><circle cx="10" cy="5.5" r="1.8"/><circle cx="14" cy="5.5" r="1.8"/><path d="M12 11c-3 0-5.5 4.5-5 7 .4 1.8 2.5 2 5 1 2.5 1 4.6.8 5-1 .5-2.5-2-7-5-7z"/>',
    printer: '<path d="M7 9V3.5h10V9M7 17H4.5V10a1 1 0 0 1 1-1h13a1 1 0 0 1 1 1v7H17"/><rect x="7" y="14" width="10" height="7"/>',
    shield: '<path d="M12 3 4.5 6v6c0 4.5 3.2 7.8 7.5 9 4.3-1.2 7.5-4.5 7.5-9V6z"/><path d="m9 12 2 2 4-4"/>',
    route: '<circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="6" r="2.5"/><path d="M8.5 18H16a3 3 0 0 0 0-6H8a3 3 0 0 1 0-6h7.5"/>'
  };
  function icon(name, cls) {
    var body = ICONS[name] || ICONS.info;
    return '<svg class="icon' + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + body + '</svg>';
  }
  var BRAND_MARK =
    '<svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">' +
    '<rect width="32" height="32" rx="8" fill="currentColor"/>' +
    '<path d="M9 22.5c3.5 0 4-5 7-5s3.5 5 7 5" fill="none" stroke="var(--action)" stroke-width="2.4" stroke-linecap="round"/>' +
    '<circle cx="16" cy="11" r="3.2" fill="var(--mark-dot, var(--bg))"/></svg>';

  // ---------- Auth ----------
  function getUser() { var u = storageGet('user', null); return u && u.token ? u : null; }
  function setUser(u) { storageSet('user', u); }
  function clearUser() { try { localStorage.removeItem('user'); } catch (_) {} }

  // ---------- API ----------
  function ApiError(message, status, body) { this.message = message; this.status = status; this.body = body; }
  ApiError.prototype = Object.create(Error.prototype);

  var wakeNoticeShown = false;

  function api(path, opts) {
    opts = opts || {};
    var headers = { 'Accept': 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    var user = getUser();
    if (user && opts.auth !== false) headers.Authorization = 'Bearer ' + user.token;
    if (opts.headers) Object.keys(opts.headers).forEach(function (k) { headers[k] = opts.headers[k]; });

    var ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, opts.timeout || 60000) : null;
    // Free hosting sleeps when idle; tell people why the first request is slow (once per visit).
    var slow = setTimeout(function () {
      if (wakeNoticeShown) return;
      wakeNoticeShown = true;
      toast('Waking up the server. The first request after a quiet spell can take up to a minute.');
    }, 7000);

    return fetch(CFG.apiBase + path, {
      method: opts.method || (opts.body !== undefined ? 'POST' : 'GET'),
      headers: headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: ctrl ? ctrl.signal : undefined
    }).then(function (res) {
      if (timer) clearTimeout(timer);
      clearTimeout(slow);
      var ct = res.headers.get('content-type') || '';
      var parse = ct.indexOf('application/json') !== -1 ? res.json() : res.text();
      return parse.then(function (body) {
        if (res.status === 401 && user && opts.auth !== false && path.indexOf('/auth/') !== 0) {
          // Expired session: drop it so the UI stops pretending we're signed in.
          clearUser();
        }
        if (!res.ok) {
          var msg = (body && body.error) || (body && body.message) || 'Request failed (' + res.status + ')';
          throw new ApiError(msg, res.status, body);
        }
        return body;
      });
    }, function (err) {
      if (timer) clearTimeout(timer);
      clearTimeout(slow);
      var aborted = err && err.name === 'AbortError';
      throw new ApiError(aborted ? 'The server took too long to respond. Please try again.'
        : 'We couldn’t reach TripMind. Check your connection and try again.', 0, null);
    });
  }

  // ---------- Toasts ----------
  function toast(message, kind) {
    var host = document.querySelector('.toast-host');
    if (!host) { host = document.createElement('div'); host.className = 'toast-host'; host.setAttribute('role', 'status'); host.setAttribute('aria-live', 'polite'); document.body.appendChild(host); }
    var el = document.createElement('div');
    el.className = 'toast' + (kind ? ' toast-' + kind : '');
    el.innerHTML = icon(kind === 'bad' ? 'alert' : kind === 'ok' ? 'check' : 'info') + '<span>' + esc(message) + '</span>';
    host.appendChild(el);
    setTimeout(function () { el.remove(); }, 4200);
  }

  // ---------- Header / footer ----------
  var NAV = [
    { href: 'plan.html', label: 'Plan a trip', key: 'plan' },
    { href: 'destinations.html', label: 'Destinations', key: 'destinations' },
    { href: 'itinerary.html', label: 'Expert planning', key: 'expert' },
    { href: 'pricing.html', label: 'Pricing', key: 'pricing' },
    { href: 'about.html', label: 'About', key: 'about' }
  ];

  function renderHeader() {
    var el = document.querySelector('[data-site-header]');
    if (!el) return;
    var page = document.body.getAttribute('data-page') || '';
    var onPhoto = el.getAttribute('data-variant') === 'photo';
    var user = getUser();
    el.className = 'site-header' + (onPhoto ? ' on-photo' : '');

    var links = NAV.map(function (n) {
      return '<a href="' + n.href + '"' + (n.key === page ? ' aria-current="page"' : '') + '>' + n.label + '</a>';
    }).join('');

    var account = user
      ? '<a class="btn btn-ghost hide-sm" href="saved.html">' + icon('bookmark') + 'My trips</a>' +
        '<button class="btn btn-ghost hide-sm" type="button" data-logout>Sign out</button>'
      : '<a class="btn btn-ghost hide-sm" href="saved.html">' + icon('bookmark') + 'Saved</a>' +
        '<a class="btn btn-ghost hide-sm" href="login.html">Sign in</a>';

    el.innerHTML =
      '<a class="skip-link" href="#main">Skip to content</a>' +
      '<div class="wrap">' +
        '<a class="brand" href="index.html" aria-label="TripMind home">' + BRAND_MARK + '<span>TripMind</span></a>' +
        '<nav class="nav" aria-label="Main">' + links + '</nav>' +
        '<div class="header-actions">' + account +
          '<a class="btn btn-primary hide-sm" href="plan.html">Start planning</a>' +
          '<button class="menu-btn" type="button" aria-expanded="false" aria-controls="mobileNav" aria-label="Open menu">' + icon('menu') + '</button>' +
        '</div>' +
      '</div>' +
      '<div class="mobile-nav" id="mobileNav" data-open="false">' +
        NAV.map(function (n) { return '<a href="' + n.href + '">' + n.label + '</a>'; }).join('') +
        '<a href="saved.html">' + (user ? 'My trips' : 'Saved trips') + '</a>' +
        (user ? '<a href="#" data-logout>Sign out</a>' : '<a href="login.html">Sign in</a>') +
        '<a class="btn btn-primary btn-block btn-lg" href="plan.html">Start planning</a>' +
      '</div>';

    var btn = el.querySelector('.menu-btn');
    var panel = el.querySelector('#mobileNav');
    btn.addEventListener('click', function () {
      var open = panel.getAttribute('data-open') !== 'true';
      panel.setAttribute('data-open', String(open));
      btn.setAttribute('aria-expanded', String(open));
      btn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
      btn.innerHTML = icon(open ? 'close' : 'menu');
      document.body.style.overflow = open ? 'hidden' : '';
      if (onPhoto) el.classList.toggle('on-photo', !open);
    });
    el.querySelectorAll('[data-logout]').forEach(function (b) {
      b.addEventListener('click', function (e) { e.preventDefault(); logout(); });
    });
  }

  function renderFooter() {
    var el = document.querySelector('[data-site-footer]');
    if (!el) return;
    el.className = 'site-footer';
    var year = new Date().getFullYear();
    el.innerHTML =
      '<div class="wrap">' +
        '<div class="footer-top">' +
          '<div><a class="brand" href="index.html">' + BRAND_MARK + '<span>TripMind</span></a>' +
          '<p>Trip plans for India that start from your budget. Every recommendation is costed door to door before we show it to you.</p></div>' +
          '<div class="footer-col"><h3>Plan</h3><ul>' +
            '<li><a href="plan.html">Trip planner</a></li><li><a href="destinations.html">Destinations</a></li>' +
            '<li><a href="itinerary.html">Expert planning</a></li><li><a href="saved.html">Saved trips</a></li></ul></div>' +
          '<div class="footer-col"><h3>Company</h3><ul>' +
            '<li><a href="about.html">About</a></li><li><a href="blog.html">Guides</a></li>' +
            '<li><a href="careers.html">Careers</a></li><li><a href="contact.html">Contact</a></li></ul></div>' +
          '<div class="footer-col"><h3>Legal</h3><ul>' +
            '<li><a href="privacy.html">Privacy</a></li><li><a href="terms.html">Terms</a></li>' +
            '<li><a href="cookie-policy.html">Cookies</a></li><li><a href="ai-disclaimer.html">How estimates work</a></li></ul></div>' +
        '</div>' +
        '<div class="footer-bottom"><span>&copy; ' + year + ' TripMind. Prices are estimates in Indian rupees and can change at booking.</span>' +
        '<span><a href="mailto:' + esc(CFG.supportEmail) + '">' + esc(CFG.supportEmail) + '</a></span></div>' +
      '</div>';
  }

  function logout() {
    var user = getUser();
    var done = function () { clearUser(); window.location.href = 'index.html'; };
    if (!user) return done();
    api('/auth/logout', { method: 'POST' }).then(done, done);
  }

  // ---------- Cookie consent + optional analytics ----------
  function loadAnalytics() {
    if (!CFG.gaId || window.gtag) return;
    var s = document.createElement('script');
    s.async = true;
    s.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(CFG.gaId);
    document.head.appendChild(s);
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    window.gtag('config', CFG.gaId, { anonymize_ip: true });
  }
  function track(name, params) {
    if (typeof window.gtag === 'function') window.gtag('event', name, params || {});
  }
  function cookieConsent() {
    if (document.body.hasAttribute('data-no-cookie-bar')) return;
    var choice = storageGet('tm_consent', null);
    if (choice === 'all') return loadAnalytics();
    if (choice) return;
    var bar = document.createElement('div');
    bar.className = 'cookie-bar no-print';
    bar.setAttribute('role', 'region');
    bar.setAttribute('aria-label', 'Cookie preferences');
    bar.innerHTML = '<strong>Cookies</strong>We use essential storage to keep you signed in and remember saved trips. With your permission we also measure visits to improve the planner. <a class="link" href="cookie-policy.html">Details</a>' +
      '<div class="row"><button class="btn btn-dark btn-sm" data-c="all">Allow analytics</button><button class="btn btn-sm" data-c="essential">Essential only</button></div>';
    document.body.appendChild(bar);
    bar.addEventListener('click', function (e) {
      var b = e.target.closest('[data-c]');
      if (!b) return;
      storageSet('tm_consent', b.getAttribute('data-c'));
      bar.remove();
      if (b.getAttribute('data-c') === 'all') loadAnalytics();
    });
  }

  // ---------- Saved trips (local, works signed-out) ----------
  var SAVED_KEY = 'tm_saved_trips';
  function savedTrips() { var a = storageGet(SAVED_KEY, []); return Array.isArray(a) ? a : []; }
  function saveTrip(trip) {
    var list = savedTrips().filter(function (t) { return t.id !== trip.id; });
    list.unshift(trip);
    return storageSet(SAVED_KEY, list.slice(0, 50));
  }
  function removeTrip(id) { storageSet(SAVED_KEY, savedTrips().filter(function (t) { return t.id !== id; })); }
  function isSaved(id) { return savedTrips().some(function (t) { return t.id === id; }); }

  // ---------- Origin cities (server list, with an offline fallback) ----------
  var FALLBACK_ORIGINS = ['Agra', 'Ahmedabad', 'Amritsar', 'Bengaluru', 'Bhopal', 'Bhubaneswar', 'Chandigarh', 'Chennai', 'Coimbatore', 'Dehradun', 'Delhi', 'Goa', 'Guwahati', 'Gurugram', 'Hyderabad', 'Indore', 'Jaipur', 'Jodhpur', 'Kanpur', 'Kochi', 'Kolkata', 'Lucknow', 'Ludhiana', 'Madurai', 'Mangaluru', 'Mumbai', 'Mysuru', 'Nagpur', 'Nashik', 'Noida', 'Patna', 'Pune', 'Raipur', 'Ranchi', 'Surat', 'Thiruvananthapuram', 'Udaipur', 'Vadodara', 'Varanasi', 'Visakhapatnam'];
  var originsPromise = null;
  function origins() {
    if (!originsPromise) {
      originsPromise = api('/origins', { auth: false, timeout: 8000 })
        .then(function (list) { return Array.isArray(list) && list.length ? list : FALLBACK_ORIGINS; },
              function () { return FALLBACK_ORIGINS; });
    }
    return originsPromise;
  }
  function fillOriginList(datalistId) {
    var dl = document.getElementById(datalistId);
    if (!dl) return;
    origins().then(function (list) {
      dl.innerHTML = list.map(function (c) { return '<option value="' + esc(c) + '"></option>'; }).join('');
    });
  }
  function parseMoney(v) { return parseInt(String(v || '').replace(/[^\d]/g, ''), 10) || 0; }
  function bindMoneyInput(input) {
    input.addEventListener('input', function () {
      var n = parseMoney(input.value);
      var caret = input.value.length - input.selectionStart;
      input.value = n ? n.toLocaleString('en-IN') : '';
      var pos = Math.max(0, input.value.length - caret);
      try { input.setSelectionRange(pos, pos); } catch (_) {}
    });
  }

  window.TM = {
    origins: origins, fillOriginList: fillOriginList, parseMoney: parseMoney, bindMoneyInput: bindMoneyInput,
    brandMark: BRAND_MARK,
    api: api, ApiError: ApiError, esc: esc, inr: inr, icon: icon, toast: toast, track: track,
    getUser: getUser, setUser: setUser, clearUser: clearUser, logout: logout,
    savedTrips: savedTrips, saveTrip: saveTrip, removeTrip: removeTrip, isSaved: isSaved,
    storageGet: storageGet, storageSet: storageSet, config: CFG
  };

  function boot() {
    renderHeader();
    renderFooter();
    cookieConsent();
    document.querySelectorAll('[data-icon]').forEach(function (n) { var c = n.getAttribute('class'); n.outerHTML = icon(n.getAttribute('data-icon'), c); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
