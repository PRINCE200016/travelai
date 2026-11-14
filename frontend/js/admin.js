/* TripMind admin console.
   Talks only to /api/admin/** (HTTP Basic) plus the public GET /api/packages.
   All server data is untrusted: every interpolated value goes through esc(). */
(function () {
  'use strict';

  var TM = window.TM;
  var esc = TM.esc;
  var icon = TM.icon;
  var CRED_KEY = 'tm_admin_basic';

  // Demo mode: sample data, no network. Only on local hosts AND only with ?demo=1.
  var host = window.location.hostname;
  var IS_LOCAL = window.location.protocol === 'file:' || host === 'localhost' || host === '127.0.0.1';
  var DEMO = IS_LOCAL && /(?:^|[?&])demo=1(?:&|$)/.test(window.location.search.slice(1));

  // ------------------------------------------------------------------
  // Credentials (sessionStorage only)
  // ------------------------------------------------------------------
  function getCreds() { try { return sessionStorage.getItem(CRED_KEY); } catch (_) { return null; } }
  function setCreds(v) { try { sessionStorage.setItem(CRED_KEY, v); } catch (_) {} }
  function clearCreds() { try { sessionStorage.removeItem(CRED_KEY); } catch (_) {} }
  function encodeBasic(user, pass) {
    // UTF-8 safe base64 of "user:pass"
    return btoa(unescape(encodeURIComponent(user + ':' + pass)));
  }
  function credUser() {
    var c = getCreds();
    if (!c) return '';
    try { var s = decodeURIComponent(escape(atob(c))); return s.slice(0, s.indexOf(':')); } catch (_) { return ''; }
  }

  // ------------------------------------------------------------------
  // API
  // ------------------------------------------------------------------
  function AdminError(message, status) { this.message = message; this.status = status; }
  AdminError.prototype = Object.create(Error.prototype);

  /** adminApi(path, {method, body, creds, as:'blob'}) → Promise of parsed body. */
  function adminApi(path, opts) {
    opts = opts || {};
    if (DEMO) return demoApi(path, opts);

    var creds = opts.creds || getCreds();
    var headers = { 'Accept': opts.as === 'blob' ? '*/*' : 'application/json' };
    if (creds) headers.Authorization = 'Basic ' + creds;
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

    var ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 20000) : null;

    return fetch(TM.config.apiBase + path, {
      method: opts.method || 'GET',
      headers: headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      // 'omit' keeps the browser from showing its own Basic-auth prompt on 401.
      credentials: 'omit',
      cache: 'no-store',
      signal: ctrl ? ctrl.signal : undefined
    }).then(function (res) {
      if (timer) clearTimeout(timer);
      if (res.status === 401) {
        if (!opts.creds) sessionExpired();
        throw new AdminError(opts.creds ? 'That username or password is incorrect.'
          : 'Your admin session has ended. Sign in again.', 401);
      }
      if (!res.ok) {
        return res.text().then(function (text) {
          var msg = '';
          try { var b = JSON.parse(text); msg = (b && (b.error || b.message)) || ''; } catch (_) {}
          if (res.status === 403 && !msg) msg = 'This account is not allowed to do that.';
          throw new AdminError(String(msg || 'The server returned an error (' + res.status + ').'), res.status);
        });
      }
      if (opts.as === 'blob') {
        return res.blob().then(function (blob) {
          return { blob: blob, filename: filenameFrom(res.headers.get('content-disposition')) };
        });
      }
      if (res.status === 204) return null;
      var ct = res.headers.get('content-type') || '';
      return ct.indexOf('json') !== -1 ? res.json() : res.text();
    }, function (err) {
      if (timer) clearTimeout(timer);
      var aborted = err && err.name === 'AbortError';
      throw new AdminError(aborted ? 'The server took too long to respond. Try again.'
        : 'Couldn’t reach the TripMind server. Check your connection and that the API is running.', 0);
    });
  }
  function filenameFrom(cd) {
    var m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd || '');
    var name = m ? m[1].replace(/[\\/:*?"<>|]+/g, '_') : '';
    return name || 'tripmind-leads-' + new Date().toISOString().slice(0, 10) + '.csv';
  }
  function asList(data) {
    if (Array.isArray(data)) return data;
    if (data && typeof data === 'object') {
      var keys = ['items', 'content', 'data', 'results', 'rows'];
      for (var i = 0; i < keys.length; i++) if (Array.isArray(data[keys[i]])) return data[keys[i]];
    }
    return [];
  }

  // ------------------------------------------------------------------
  // Formatting
  // ------------------------------------------------------------------
  var DT = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true });
  var D = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
  var NIL = '<span class="nil" aria-label="Not provided">—</span>';

  function toDate(v) {
    if (v == null || v === '') return null;
    if (Array.isArray(v)) return new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0);
    if (typeof v === 'number') return new Date(v);
    var s = String(v);
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
    if (m) return new Date(+m[1], +m[2] - 1, +m[3]);
    var d = new Date(s);
    return isNaN(d.getTime()) ? null : d;
  }
  function fmtDateTime(v) {
    var d = toDate(v);
    return d ? esc(DT.format(d).replace(/\b(AM|PM)\b/g, function (x) { return x.toLowerCase(); })) : NIL;
  }
  // Table cell: date on one line, time beneath (same en-IN wording as fmtDateTime).
  function whenCell(v) {
    var d = toDate(v);
    if (!d) return NIL;
    var full = DT.format(d).replace(/(AM|PM)/g, function (x) { return x.toLowerCase(); });
    var i = full.lastIndexOf(', ');
    return i === -1 ? esc(full) : '<span class="nowrap">' + esc(full.slice(0, i)) + '</span><span class="cell-sub">' + esc(full.slice(i + 2)) + '</span>';
  }
  function fmtDate(v) {
    var d = toDate(v);
    return d ? esc(D.format(d)) : (v ? esc(v) : NIL);
  }
  function isNum(v) { return v !== null && v !== '' && v !== undefined && typeof v !== 'boolean' && !isNaN(Number(v)); }
  function money(v) {
    if (isNum(v)) return esc(TM.inr(v));
    return v ? esc(v) : NIL; // free-text budgets like "10-15k"
  }
  function int(v) { return isNum(v) ? esc(Math.round(Number(v)).toLocaleString('en-IN')) : NIL; }
  function text(v) {
    if (Array.isArray(v)) v = v.join(', ');
    if (v && typeof v === 'object') v = JSON.stringify(v);
    return v == null || v === '' ? NIL : esc(v);
  }
  function plain(v) {
    if (Array.isArray(v)) return v.join(', ');
    if (v == null) return '';
    return typeof v === 'object' ? JSON.stringify(v) : String(v);
  }
  function truncate(s, n) { s = plain(s); return s.length > n ? s.slice(0, n - 1).trimEnd() + '…' : s; }
  function pick(obj) {
    for (var i = 1; i < arguments.length; i++) { var v = obj[arguments[i]]; if (v != null && v !== '') return v; }
    return null;
  }
  function safeHttpUrl(u) {
    if (!u) return '';
    try {
      var url = new URL(String(u).trim());
      return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : '';
    } catch (_) { return ''; }
  }
  function cap(s) { s = String(s || ''); return s.charAt(0).toUpperCase() + s.slice(1).replace(/_/g, ' '); }
  function idAttr(id) { return esc(id == null ? '' : id); }
  function search(hay, q) {
    if (!q) return true;
    return hay.map(plain).join(' \u0001 ').toLowerCase().indexOf(q) !== -1;
  }

  // ------------------------------------------------------------------
  // Status vocab
  // ------------------------------------------------------------------
  var REQ_STATUS = [['pending', 'Pending', 'warn'], ['in_progress', 'In progress', 'action'], ['sent', 'Sent', 'ok'], ['closed', 'Closed', '']];
  var MSG_STATUS = [['unread', 'Unread', 'warn'], ['read', 'Read', ''], ['replied', 'Replied', 'ok']];
  function toneOf(list, value) {
    for (var i = 0; i < list.length; i++) if (list[i][0] === value) return list[i][2];
    return '';
  }
  function statusSelect(view, list, row, label) {
    var cur = String(row.status || list[0][0]);
    var opts = list.slice();
    if (!opts.some(function (o) { return o[0] === cur; })) opts.push([cur, cap(cur), '']);
    return '<select class="status-select" data-status="' + view + '" data-id="' + idAttr(row.id) + '" data-tone="' +
      toneOf(list, cur) + '" aria-label="' + esc(label) + '">' +
      opts.map(function (o) {
        return '<option value="' + esc(o[0]) + '"' + (o[0] === cur ? ' selected' : '') + '>' + esc(o[1]) + '</option>';
      }).join('') + '</select>';
  }
  function payTone(s) {
    s = String(s || '').toLowerCase();
    if (/paid|success|complete|captured|settled/.test(s)) return 'badge-ok';
    if (/fail|cancel|refund|declin|error/.test(s)) return 'badge-bad';
    if (/pend|await|created|initiat/.test(s)) return 'badge-warn';
    return '';
  }

  // ------------------------------------------------------------------
  // Views
  // ------------------------------------------------------------------
  var NAV = [
    { key: 'overview', label: 'Overview', icon: 'compass' },
    { key: 'requests', label: 'Itinerary requests', icon: 'calendar', count: 'pendingRequests' },
    { key: 'plans', label: 'Planner searches', icon: 'route' },
    { key: 'packages', label: 'Packages', icon: 'ticket' },
    { key: 'leads', label: 'Leads', icon: 'phone' },
    { key: 'contact', label: 'Contact messages', icon: 'mail', count: 'contactUnread' },
    { key: 'users', label: 'Users', icon: 'users' },
    { key: 'payments', label: 'Payments', icon: 'wallet' }
  ];

  var state = { view: 'overview', stats: null, statsError: null, statsLoading: false, counts: {} };
  var tables = {};

  function defineTable(key, cfg) {
    cfg.key = key;
    cfg.rows = null; cfg.loading = false; cfg.error = null; cfg.loaded = false; cfg.seq = 0;
    tables[key] = cfg;
  }

  function loadTable(key) {
    var t = tables[key];
    var seq = ++t.seq;
    t.loading = true; t.error = null;
    renderTable(key);
    return adminApi(t.endpoint).then(function (data) {
      if (seq !== t.seq) return;
      t.rows = asList(data);
      t.loading = false; t.loaded = true;
      if (t.afterLoad) t.afterLoad(t.rows);
      renderTable(key);
    }, function (err) {
      if (seq !== t.seq) return;
      t.loading = false;
      if (err.status === 401) return; // login screen takes over
      t.error = err.message;
      renderTable(key);
    });
  }

  function filteredRows(t) {
    var q = (document.querySelector('[data-search="' + t.key + '"]') || {}).value || '';
    var sel = document.querySelector('[data-filter="' + t.key + '"]');
    var status = sel ? sel.value : '';
    q = q.trim().toLowerCase();
    return t.rows.filter(function (r) {
      if (status && String(r.status || t.defaultStatus || '') !== status) return false;
      return search(t.haystack(r), q);
    });
  }

  function renderTable(key) {
    var t = tables[key];
    var body = document.querySelector('[data-body="' + key + '"]');
    var count = document.querySelector('[data-count="' + key + '"]');
    if (!body) return;
    body.setAttribute('aria-busy', t.loading ? 'true' : 'false');

    if (t.loading && !t.rows) {
      if (count) count.textContent = '';
      body.innerHTML = skeletonTable(t.columns);
      return;
    }
    if (t.error) {
      if (count) count.textContent = '';
      body.innerHTML = errorBox('Couldn’t load ' + t.noun + '.', t.error, key);
      return;
    }
    var rows = t.rows || [];
    if (t.renderFilters) t.renderFilters(rows);
    if (!rows.length) {
      if (count) count.textContent = '';
      body.innerHTML = '<div class="empty state"><h2>' + esc(t.emptyTitle) + '</h2><p>' + esc(t.emptyText) + '</p>' +
        (t.emptyAction || '') + '</div>';
      return;
    }
    var list = filteredRows(t);
    if (count) count.textContent = list.length === rows.length ? rows.length.toLocaleString('en-IN') + ' total'
      : list.length.toLocaleString('en-IN') + ' of ' + rows.length.toLocaleString('en-IN');
    if (!list.length) {
      body.innerHTML = '<div class="empty state"><h2>No matches</h2><p>Nothing matches the current search or filter.</p>' +
        '<button class="btn btn-sm" type="button" data-action="clear-filters" data-view="' + key + '">Clear filters</button></div>';
      return;
    }
    body.innerHTML = '<div class="table-wrap"><table class="table"><caption class="sr-only">' + esc(t.noun) + '</caption><thead><tr>' +
      t.columns.map(function (c) {
        return '<th scope="col"' + (c.num ? ' class="num"' : '') + '>' + (c.sr ? '<span class="sr-only">' + esc(c.label) + '</span>' : esc(c.label)) + '</th>';
      }).join('') + '</tr></thead><tbody>' + list.map(t.row).join('') + '</tbody></table></div>';
  }

  function skeletonTable(columns) {
    var widths = [70, 90, 55, 80, 45, 65, 50, 75];
    var rows = '';
    for (var r = 0; r < 6; r++) {
      rows += '<tr>' + columns.map(function (c, i) {
        return '<td><span class="skeleton sk-line" style="width:' + (widths[(i + r) % widths.length]) + '%"></span></td>';
      }).join('') + '</tr>';
    }
    return '<div class="table-wrap"><table class="table" aria-hidden="true"><thead><tr>' +
      columns.map(function (c) { return '<th>' + (c.sr ? '' : esc(c.label)) + '</th>'; }).join('') +
      '</tr></thead><tbody>' + rows + '</tbody></table></div><p class="sr-only" role="status">Loading…</p>';
  }

  function errorBox(title, message, retryKey) {
    return '<div class="notice notice-bad error-box" role="alert">' + icon('alert') + '<div><strong>' + esc(title) + '</strong>' +
      '<span>' + esc(message) + '</span><br><button class="btn btn-sm" type="button" data-action="retry" data-view="' + esc(retryKey) + '">' +
      icon('refresh') + 'Try again</button></div></div>';
  }

  function statusFilterOptions(selectId, list, rows, fallback) {
    var sel = document.getElementById(selectId);
    if (!sel) return;
    var cur = sel.value;
    var counts = {};
    var known = !!rows;
    rows = rows || [];
    rows.forEach(function (r) { var s = String(r.status || fallback); counts[s] = (counts[s] || 0) + 1; });
    var opts = list.map(function (o) { return [o[0], o[1]]; });
    Object.keys(counts).forEach(function (s) { if (!opts.some(function (o) { return o[0] === s; })) opts.push([s, cap(s)]); });
    sel.innerHTML = '<option value="">All statuses' + (known ? ' (' + rows.length + ')' : '') + '</option>' + opts.map(function (o) {
      return '<option value="' + esc(o[0]) + '"' + (o[0] === cur ? ' selected' : '') + '>' + esc(o[1]) + (known ? ' (' + (counts[o[0]] || 0) + ')' : '') + '</option>';
    }).join('');
  }

  // ---------- Itinerary requests ----------
  function reqFields(r) {
    return {
      name: pick(r, 'name'), email: pick(r, 'email'), phone: pick(r, 'phone'),
      destination: pick(r, 'destination'),
      start: pick(r, 'startDate', 'travelStartDate'), end: pick(r, 'endDate', 'travelEndDate'),
      days: pick(r, 'days'), travellers: pick(r, 'travellers', 'travelers'),
      budget: pick(r, 'budget'), preferences: pick(r, 'preferences'),
      notes: pick(r, 'notes', 'specialRequest'), status: r.status || 'pending', createdAt: pick(r, 'createdAt')
    };
  }
  defineTable('requests', {
    endpoint: '/admin/itinerary-requests', noun: 'itinerary requests', defaultStatus: 'pending',
    emptyTitle: 'No itinerary requests yet', emptyText: 'Requests from the expert planning form will appear here.',
    columns: [{ label: 'Received' }, { label: 'Traveller' }, { label: 'Destination' }, { label: 'Dates' },
      { label: 'Travellers', num: true }, { label: 'Budget', num: true }, { label: 'Notes' }, { label: 'Status' }, { label: 'Actions', sr: true }],
    haystack: function (r) { var f = reqFields(r); return [f.name, f.email, f.phone, f.destination, f.notes, f.preferences, r.id]; },
    renderFilters: function (rows) { statusFilterOptions('reqStatus', REQ_STATUS, rows, 'pending'); },
    afterLoad: function (rows) {
      setCount('pendingRequests', rows.filter(function (r) { return (r.status || 'pending') === 'pending'; }).length);
    },
    row: function (r) {
      var f = reqFields(r);
      var dates = f.start ? fmtDate(f.start) : NIL;
      var sub = [];
      if (f.end) sub.push('to ' + fmtDate(f.end));
      if (isNum(f.days)) sub.push(int(f.days) + (Number(f.days) === 1 ? ' day' : ' days'));
      var notes = plain(f.notes);
      return '<tr>' +
        '<td class="nowrap">' + whenCell(f.createdAt) + '</td>' +
        '<td class="col-person"><span class="cell-main">' + text(f.name) + '</span>' +
          (f.email ? '<span class="cell-sub">' + esc(f.email) + '</span>' : '') +
          (f.phone ? '<span class="cell-sub nowrap">' + esc(f.phone) + '</span>' : '') + '</td>' +
        '<td class="col-text">' + text(f.destination) + '</td>' +
        '<td class="nowrap">' + dates + (sub.length ? '<span class="cell-sub">' + sub.join(' · ') + '</span>' : '') + '</td>' +
        '<td class="num">' + int(f.travellers) + '</td>' +
        '<td class="num">' + money(f.budget) + '</td>' +
        '<td class="col-text">' + (notes ? '<span class="truncate" title="' + esc(truncate(notes, 300)) + '">' + esc(truncate(notes, 60)) + '</span>' : '<span class="truncate nil">No notes</span>') +
          '<button class="linkish" type="button" data-action="req-view" data-id="' + idAttr(r.id) + '">View<span class="sr-only"> request from ' + esc(plain(f.name) || 'unknown') + '</span></button></td>' +
        '<td>' + statusSelect('requests', REQ_STATUS, { id: r.id, status: f.status }, 'Status for request from ' + (plain(f.name) || 'unknown')) + '</td>' +
        '<td><div class="row-actions"><button class="icon-btn sm danger" type="button" data-action="req-delete" data-id="' + idAttr(r.id) +
          '" aria-label="Delete request from ' + esc(plain(f.name) || 'unknown') + '">' + icon('trash') + '</button></div></td>' +
      '</tr>';
    }
  });

  // ---------- Planner searches ----------
  function parseQuery(q) {
    if (!q) return {};
    if (typeof q === 'object') return q;
    try { var o = JSON.parse(q); return o && typeof o === 'object' ? o : {}; } catch (_) { return {}; }
  }
  function planFields(p) {
    var q = parseQuery(p.query);
    return {
      createdAt: pick(p, 'createdAt'), userId: pick(p, 'userId'),
      origin: pick(q, 'originLabel', 'origin') || pick(p, 'origin'),
      days: pick(q, 'days') != null ? q.days : pick(p, 'days'),
      travellers: pick(q, 'travellers', 'travelers') != null ? pick(q, 'travellers', 'travelers') : pick(p, 'travellers'),
      budget: pick(q, 'budget') != null ? q.budget : pick(p, 'budget'),
      destination: pick(p, 'destination', 'topDestination') || pick(q, 'topDestination'),
      total: pick(p, 'total')
    };
  }
  defineTable('plans', {
    endpoint: '/admin/plans', noun: 'planner searches',
    emptyTitle: 'No planner searches yet', emptyText: 'Searches made in the trip planner, by users and guests, appear here.',
    columns: [{ label: 'Date' }, { label: 'User' }, { label: 'Origin' }, { label: 'Days', num: true }, { label: 'Travellers', num: true },
      { label: 'Budget', num: true }, { label: 'Top destination' }, { label: 'Trip total', num: true }],
    haystack: function (p) { var f = planFields(p); return [f.origin, f.destination, f.userId]; },
    row: function (p) {
      var f = planFields(p);
      return '<tr>' +
        '<td class="nowrap">' + whenCell(f.createdAt) + '</td>' +
        '<td class="nowrap">' + (f.userId != null ? 'User #' + esc(f.userId) : '<span class="muted">Guest</span>') + '</td>' +
        '<td>' + text(f.origin) + '</td>' +
        '<td class="num">' + int(f.days) + '</td>' +
        '<td class="num">' + int(f.travellers) + '</td>' +
        '<td class="num">' + money(f.budget) + '</td>' +
        '<td>' + text(f.destination) + '</td>' +
        '<td class="num">' + (isNum(f.total) ? esc(TM.inr(f.total)) : NIL) + '</td>' +
      '</tr>';
    }
  });

  // ---------- Packages ----------
  defineTable('packages', {
    endpoint: '/admin/packages', noun: 'packages',
    emptyTitle: 'No packages yet', emptyText: 'Create a package to feature it on the public site.',
    emptyAction: '<button class="btn btn-primary btn-sm" type="button" data-action="pkg-new">New package</button>',
    columns: [{ label: 'Image' }, { label: 'Title' }, { label: 'Price', num: true }, { label: 'Featured' }, { label: 'Description' }, { label: 'Actions', sr: true }],
    haystack: function (p) { return [p.title, p.description, p.id]; },
    row: function (p) {
      var img = safeHttpUrl(p.imageUrl);
      var title = plain(p.title) || 'Untitled package';
      return '<tr>' +
        '<td>' + (img ? '<img class="thumb" src="' + esc(img) + '" alt="" loading="lazy" referrerpolicy="no-referrer" width="64" height="44">'
          : '<span class="thumb thumb-empty" title="No image">' + icon('mountain') + '</span>') + '</td>' +
        '<td><span class="cell-main">' + esc(title) + '</span>' + (p.id != null ? '<span class="cell-sub">#' + esc(p.id) + '</span>' : '') + '</td>' +
        '<td class="num">' + money(p.price) + '</td>' +
        '<td>' + (p.featured ? '<span class="badge badge-ok">Featured</span>' : '<span class="badge">Not featured</span>') + '</td>' +
        '<td>' + (p.description ? '<span class="truncate" title="' + esc(truncate(p.description, 300)) + '">' + esc(truncate(p.description, 80)) + '</span>' : NIL) + '</td>' +
        '<td><div class="row-actions">' +
          '<button class="btn btn-sm" type="button" data-action="pkg-edit" data-id="' + idAttr(p.id) + '">Edit<span class="sr-only"> ' + esc(title) + '</span></button>' +
          '<button class="icon-btn sm danger" type="button" data-action="pkg-delete" data-id="' + idAttr(p.id) + '" aria-label="Delete ' + esc(title) + '">' + icon('trash') + '</button>' +
        '</div></td>' +
      '</tr>';
    }
  });

  // ---------- Leads ----------
  defineTable('leads', {
    endpoint: '/admin/leads', noun: 'leads',
    emptyTitle: 'No leads yet', emptyText: 'Leads captured on the site will appear here.',
    columns: [{ label: 'Date' }, { label: 'Name' }, { label: 'Contact' }, { label: 'Destination interest' }, { label: 'Budget', num: true }],
    haystack: function (l) { return [l.name, l.phone, l.email, l.destinationInterest, l.destination, l.budget]; },
    row: function (l) {
      return '<tr>' +
        '<td class="nowrap">' + whenCell(l.createdAt) + '</td>' +
        '<td><span class="cell-main">' + text(l.name) + '</span></td>' +
        '<td>' + (l.phone || l.email ? (l.phone ? '<span class="nowrap">' + esc(l.phone) + '</span>' : '') +
          (l.email ? '<span class="cell-sub">' + esc(l.email) + '</span>' : '') : NIL) + '</td>' +
        '<td>' + text(pick(l, 'destinationInterest', 'destination')) + '</td>' +
        '<td class="num">' + money(l.budget) + '</td>' +
      '</tr>';
    }
  });

  // ---------- Contact messages ----------
  defineTable('contact', {
    endpoint: '/admin/contact', noun: 'contact messages', defaultStatus: 'unread',
    emptyTitle: 'No messages yet', emptyText: 'Messages sent through the contact form will appear here.',
    columns: [{ label: 'Received' }, { label: 'From' }, { label: 'Message' }, { label: 'Status' }, { label: 'Actions', sr: true }],
    haystack: function (c) { return [c.name, c.email, c.phone, c.message]; },
    renderFilters: function (rows) { statusFilterOptions('msgStatus', MSG_STATUS, rows, 'unread'); },
    afterLoad: function (rows) {
      setCount('contactUnread', rows.filter(function (r) { return (r.status || 'unread') === 'unread'; }).length);
    },
    row: function (c) {
      var msg = plain(c.message);
      var long = msg.length > 140 || msg.split('\n').length > 2;
      var mid = 'msg-' + String(c.id).replace(/[^\w-]/g, '_');
      var name = plain(c.name) || 'unknown sender';
      return '<tr>' +
        '<td class="nowrap">' + whenCell(c.createdAt) + '</td>' +
        '<td class="col-person"><span class="cell-main">' + text(c.name) + '</span>' +
          (c.email ? '<span class="cell-sub">' + esc(c.email) + '</span>' : '') +
          (c.phone ? '<span class="cell-sub">' + esc(c.phone) + '</span>' : '') + '</td>' +
        '<td>' + (msg ? '<p class="msg' + (long ? ' clamped' : '') + '" id="' + esc(mid) + '">' + esc(msg) + '</p>' +
          (long ? '<button class="linkish" type="button" data-action="msg-toggle" aria-expanded="false" aria-controls="' + esc(mid) + '">Show more</button>' : '') : NIL) + '</td>' +
        '<td>' + statusSelect('contact', MSG_STATUS, { id: c.id, status: c.status || 'unread' }, 'Status for message from ' + name) + '</td>' +
        '<td><div class="row-actions">' +
          (c.email ? '<a class="btn btn-sm" href="mailto:' + esc(encodeURIComponent(plain(c.email)).replace(/%40/g, '@')) + '?subject=' + esc(encodeURIComponent('Re: your message to TripMind')) + '">Reply</a>' : '') +
          '<button class="icon-btn sm danger" type="button" data-action="msg-delete" data-id="' + idAttr(c.id) + '" aria-label="Delete message from ' + esc(name) + '">' + icon('trash') + '</button>' +
        '</div></td>' +
      '</tr>';
    }
  });

  // ---------- Users ----------
  defineTable('users', {
    endpoint: '/admin/users', noun: 'users',
    emptyTitle: 'No users yet', emptyText: 'Accounts created on the site will appear here.',
    columns: [{ label: 'ID', num: true }, { label: 'Name' }, { label: 'Email' }, { label: 'Joined' }],
    haystack: function (u) { return [u.id, u.name, u.email]; },
    row: function (u) {
      return '<tr>' +
        '<td class="num">' + text(u.id) + '</td>' +
        '<td><span class="cell-main">' + text(u.name) + '</span></td>' +
        '<td>' + text(u.email) + '</td>' +
        '<td class="nowrap">' + whenCell(u.createdAt) + '</td>' +
      '</tr>';
    }
  });

  // ---------- Payments ----------
  defineTable('payments', {
    endpoint: '/admin/payments', noun: 'payments',
    emptyTitle: 'No payments yet', emptyText: 'Payments for expert planning will appear here.',
    columns: [{ label: 'Date' }, { label: 'User ID' }, { label: 'Type' }, { label: 'Status' }, { label: 'Amount', num: true }],
    haystack: function (p) { return [p.userId, pick(p, 'requestType', 'type'), pick(p, 'paymentStatus', 'status'), p.id]; },
    row: function (p) {
      var st = pick(p, 'paymentStatus', 'status');
      return '<tr>' +
        '<td class="nowrap">' + whenCell(p.createdAt) + '</td>' +
        '<td>' + (p.userId != null ? '#' + esc(p.userId) : NIL) + '</td>' +
        '<td>' + text(cap(plain(pick(p, 'requestType', 'type'))) || null) + '</td>' +
        '<td>' + (st ? '<span class="badge ' + payTone(st) + '">' + esc(cap(st)) + '</span>' : NIL) + '</td>' +
        '<td class="num">' + money(p.amount) + '</td>' +
      '</tr>';
    }
  });

  // ---------- Overview ----------
  var STAT_TILES = [
    { key: 'users', label: 'Users', alt: ['totalUsers'], view: 'users' },
    { key: 'plans', label: 'Planner searches', alt: ['totalPlans'], view: 'plans' },
    { key: 'itineraryRequests', label: 'Itinerary requests', alt: ['totalRequests'], view: 'requests' },
    { key: 'pendingRequests', label: 'Pending requests', alt: [], view: 'requests', filter: 'pending' },
    { key: 'contactUnread', label: 'Unread messages', alt: ['unreadMessages'], view: 'contact', filter: 'unread' },
    { key: 'leads', label: 'Leads', alt: ['totalLeads'], view: 'leads' }
  ];
  function statValue(s, tile) {
    var v = s[tile.key];
    for (var i = 0; v == null && i < tile.alt.length; i++) v = s[tile.alt[i]];
    return v;
  }
  function loadStats() {
    state.statsLoading = true; state.statsError = null;
    renderOverview();
    return adminApi('/admin/stats').then(function (s) {
      applyStats(s);
    }, function (err) {
      state.statsLoading = false;
      if (err.status === 401) return;
      state.statsError = err.message;
      renderOverview();
    });
  }
  function applyStats(s) {
    state.stats = s && typeof s === 'object' ? s : {};
    state.statsLoading = false;
    state.statsAt = Date.now();
    setCount('pendingRequests', statValue(state.stats, STAT_TILES[3]));
    setCount('contactUnread', statValue(state.stats, STAT_TILES[4]));
    renderOverview();
  }
  function renderOverview() {
    var tiles = document.getElementById('statTiles');
    var dest = document.getElementById('topDestBody');
    var loading = state.statsLoading && !state.stats;
    if (state.statsError && !state.stats) {
      tiles.innerHTML = '';
      tiles.hidden = true;
      dest.innerHTML = errorBox('Couldn’t load the overview.', state.statsError, 'overview');
      return;
    }
    tiles.hidden = false;
    var s = state.stats || {};
    tiles.innerHTML = STAT_TILES.map(function (t) {
      var v = statValue(s, t);
      var fig = loading ? '<span class="skeleton"></span>' : int(v);
      var attrs = ' data-action="goto" data-view="' + t.view + '"' + (t.filter ? ' data-filter-value="' + t.filter + '"' : '');
      return '<button class="stat" type="button"' + attrs + '><span class="stat-label">' + esc(t.label) + '</span><span class="stat-value">' + fig + '</span></button>';
    }).join('');

    if (loading) { dest.innerHTML = skeletonTable([{ label: '#' }, { label: 'Destination' }, { label: 'Searches' }, { label: 'Share' }]); return; }
    var top = asList(s.topDestinations).filter(function (d) { return d && typeof d === 'object'; });
    if (!top.length) {
      dest.innerHTML = '<div class="empty state"><h2>No destination data yet</h2><p>Top destinations appear once people use the planner.</p></div>';
      return;
    }
    var max = top.reduce(function (m, d) { return Math.max(m, Number(d.count) || 0); }, 0) || 1;
    var sum = top.reduce(function (m, d) { return m + (Number(d.count) || 0); }, 0) || 1;
    dest.innerHTML = '<div class="table-wrap"><table class="table"><caption class="sr-only">Top destinations by planner searches</caption><thead><tr>' +
      '<th scope="col" class="num">#</th><th scope="col">Destination</th><th scope="col" class="num">Searches</th><th scope="col">Share of top ' + top.length + '</th></tr></thead><tbody>' +
      top.slice(0, 15).map(function (d, i) {
        var c = Number(d.count) || 0;
        var pct = Math.round((c / sum) * 100);
        return '<tr><td class="num">' + (i + 1) + '</td><td><span class="cell-main">' + text(pick(d, 'destination', 'name')) + '</span></td>' +
          '<td class="num">' + int(c) + '</td>' +
          '<td><div class="share"><span class="share-bar" aria-hidden="true"><span style="width:' + Math.max(2, Math.round((c / max) * 100)) + '%"></span></span>' +
          '<span class="xs muted num">' + pct + '%</span></div></td></tr>';
      }).join('') + '</tbody></table></div>';
  }

  // ------------------------------------------------------------------
  // Navigation
  // ------------------------------------------------------------------
  function renderNav() {
    var nav = document.getElementById('sideNav');
    nav.innerHTML = NAV.map(function (n) {
      var c = n.count && state.counts[n.count];
      return '<button type="button" data-nav="' + n.key + '"' + (n.key === state.view ? ' aria-current="page"' : '') + '>' +
        icon(n.icon) + '<span>' + esc(n.label) + '</span>' +
        (c ? '<span class="count" aria-label="' + esc(c + ' ' + (n.count === 'pendingRequests' ? 'pending' : 'unread')) + '">' + esc(c) + '</span>' : '') +
        '</button>';
    }).join('');
  }
  function setCount(key, v) {
    state.counts[key] = isNum(v) ? Number(v) : 0;
    renderNav();
  }
  function navLabel(key) {
    for (var i = 0; i < NAV.length; i++) if (NAV[i].key === key) return NAV[i].label;
    return 'Overview';
  }
  function showView(key, opts) {
    opts = opts || {};
    if (!NAV.some(function (n) { return n.key === key; })) key = 'overview';
    state.view = key;
    document.querySelectorAll('[data-view]').forEach(function (s) {
      if (s.tagName === 'SECTION') s.hidden = s.getAttribute('data-view') !== key;
    });
    var title = navLabel(key);
    document.getElementById('viewTitle').textContent = title;
    document.title = title + ' · TripMind admin';
    renderNav();
    if (history.replaceState) {
      try { history.replaceState(null, '', window.location.pathname + window.location.search + '#' + key); } catch (_) {}
    }
    if (key === 'overview') { if (!state.stats || opts.reload || Date.now() - (state.statsAt || 0) > 30000) loadStats(); else renderOverview(); }
    else if (!tables[key].loaded || opts.reload) loadTable(key);
    else renderTable(key);
    if (opts.focus) document.getElementById('viewTitle').focus();
  }
  function refreshCurrent() {
    if (state.view === 'overview') loadStats();
    else loadTable(state.view);
  }

  // ------------------------------------------------------------------
  // Login / session
  // ------------------------------------------------------------------
  function showLogin(message) {
    document.getElementById('appView').hidden = true;
    document.getElementById('loginView').hidden = false;
    document.title = 'Sign in · TripMind admin';
    var err = document.getElementById('loginError');
    err.hidden = !message;
    err.textContent = message || '';
    var pass = document.getElementById('loginPass');
    pass.value = '';
    var user = document.getElementById('loginUser');
    (user.value ? pass : user).focus();
  }
  function resetData() {
    Object.keys(tables).forEach(function (k) { var t = tables[k]; t.rows = null; t.loaded = false; t.error = null; t.seq++; });
    state.stats = null; state.statsError = null; state.statsAt = 0; state.counts = {};
  }
  function showApp(prefetchedStats) {
    document.getElementById('loginView').hidden = true;
    document.getElementById('appView').hidden = false;
    document.getElementById('whoAmI').textContent = DEMO ? 'Demo session' : (credUser() ? 'Signed in as ' + credUser() : '');
    document.getElementById('demoNote').hidden = !DEMO;
    var start = (window.location.hash || '').replace('#', '');
    start = start || 'overview';
    if (prefetchedStats) applyStats(prefetchedStats);
    else if (start !== 'overview') loadStats(); // fills the sidebar counts
    showView(start);
  }
  var expiredShown = false;
  function sessionExpired() {
    clearCreds();
    resetData();
    closeDialogs();
    if (!expiredShown) {
      expiredShown = true;
      showLogin('Your admin session has ended. Sign in again.');
      setTimeout(function () { expiredShown = false; }, 500);
    }
  }
  function signOut() {
    clearCreds();
    resetData();
    closeDialogs();
    document.getElementById('loginUser').value = '';
    showLogin('');
  }

  function bindLogin() {
    var form = document.getElementById('loginForm');
    var btn = document.getElementById('loginSubmit');
    var err = document.getElementById('loginError');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var user = document.getElementById('loginUser');
      var pass = document.getElementById('loginPass');
      user.removeAttribute('aria-invalid'); pass.removeAttribute('aria-invalid');
      if (!user.value.trim() || !pass.value) {
        if (!user.value.trim()) user.setAttribute('aria-invalid', 'true');
        if (!pass.value) pass.setAttribute('aria-invalid', 'true');
        err.textContent = 'Enter both the username and the password.';
        err.hidden = false;
        (user.value.trim() ? pass : user).focus();
        return;
      }
      var creds = encodeBasic(user.value.trim(), pass.value);
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>Checking…';
      err.hidden = true;
      adminApi('/admin/stats', { creds: creds }).then(function (stats) {
        setCreds(creds);
        pass.value = '';
        showApp(stats);
      }, function (e2) {
        if (e2.status === 401) { pass.setAttribute('aria-invalid', 'true'); pass.select(); }
        err.textContent = e2.message;
        err.hidden = false;
      }).then(function () {
        btn.disabled = false;
        btn.textContent = 'Sign in';
      });
    });
  }

  // ------------------------------------------------------------------
  // Dialogs
  // ------------------------------------------------------------------
  var lastFocus = null;
  function openDialog(d) {
    lastFocus = document.activeElement;
    if (typeof d.showModal === 'function') d.showModal(); else d.setAttribute('open', '');
  }
  function closeDialog(d) {
    if (d.open) { if (typeof d.close === 'function') d.close(); else d.removeAttribute('open'); }
  }
  function closeDialogs() { document.querySelectorAll('dialog[open]').forEach(closeDialog); }
  function bindDialogs() {
    document.querySelectorAll('dialog.modal').forEach(function (d) {
      d.addEventListener('click', function (e) {
        if (e.target.closest('[data-close]')) { closeDialog(d); return; }
        if (e.target === d) { // backdrop click
          var r = d.getBoundingClientRect();
          if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) closeDialog(d);
        }
      });
      d.addEventListener('close', function () {
        if (lastFocus && document.contains(lastFocus) && typeof lastFocus.focus === 'function') lastFocus.focus();
      });
    });
  }

  function findRow(key, id) {
    var rows = tables[key].rows || [];
    for (var i = 0; i < rows.length; i++) if (String(rows[i].id) === String(id)) return rows[i];
    return null;
  }

  function openRequest(id) {
    var r = findRow('requests', id);
    if (!r) return;
    var f = reqFields(r);
    var kv = function (label, value) { return '<div><dt>' + esc(label) + '</dt><dd>' + value + '</dd></div>'; };
    var email = plain(f.email);
    var phone = plain(f.phone).replace(/[^\d+]/g, '');
    var html = '<dl class="kv">' +
      kv('Received', fmtDateTime(f.createdAt)) +
      kv('Status', '<span class="badge ' + ({ warn: 'badge-warn', ok: 'badge-ok', action: 'badge-action' }[toneOf(REQ_STATUS, f.status)] || '') + '">' + esc(cap(f.status)) + '</span>') +
      kv('Name', text(f.name)) +
      kv('Email', email ? '<a href="mailto:' + esc(encodeURIComponent(email).replace(/%40/g, '@')) + '">' + esc(email) + '</a>' : NIL) +
      kv('Phone', f.phone ? (phone ? '<a href="tel:' + esc(phone) + '">' + esc(f.phone) + '</a>' : esc(f.phone)) : NIL) +
      kv('Destination', text(f.destination)) +
      kv('Start date', f.start ? fmtDate(f.start) : NIL) +
      (f.end ? kv('End date', fmtDate(f.end)) : '') +
      kv('Days', int(f.days)) +
      kv('Travellers', int(f.travellers)) +
      kv('Budget', money(f.budget)) +
      kv('Preferences', text(f.preferences)) +
      (r.id != null ? kv('Request ID', esc(r.id)) : '') +
      '</dl>' +
      '<div class="notes-block"><strong>Notes</strong>' + (f.notes ? esc(plain(f.notes)) : 'No notes provided.') + '</div>' +
      '<div class="modal-actions">' +
        (email ? '<a class="btn btn-primary" href="mailto:' + esc(encodeURIComponent(email).replace(/%40/g, '@')) + '?subject=' +
          esc(encodeURIComponent('Your TripMind trip plan' + (f.destination ? ' for ' + plain(f.destination) : ''))) + '">Email traveller</a>' : '') +
        '<button class="btn" type="button" data-close>Close</button></div>';
    document.getElementById('requestDialogTitle').textContent = 'Request from ' + (plain(f.name) || 'unknown traveller');
    document.getElementById('requestDialogBody').innerHTML = html;
    openDialog(document.getElementById('requestDialog'));
  }

  // ---------- Package editor ----------
  var editingPkg = null;
  function fieldError(id, msg) {
    var el = document.getElementById(id + 'Err');
    var input = document.getElementById(id);
    el.textContent = msg || '';
    el.hidden = !msg;
    if (msg) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
  }
  function openPackage(pkg) {
    editingPkg = pkg || null;
    var form = document.getElementById('packageForm');
    form.reset();
    ['pkgTitle', 'pkgPrice', 'pkgImage'].forEach(function (id) { fieldError(id, ''); });
    document.getElementById('pkgFormErr').hidden = true;
    document.getElementById('packageDialogTitle').textContent = pkg ? 'Edit package' : 'New package';
    if (pkg) {
      document.getElementById('pkgTitle').value = plain(pkg.title);
      document.getElementById('pkgPrice').value = isNum(pkg.price) ? Number(pkg.price) : '';
      document.getElementById('pkgImage').value = plain(pkg.imageUrl);
      document.getElementById('pkgDesc').value = plain(pkg.description);
      document.getElementById('pkgFeatured').checked = !!pkg.featured;
    }
    openDialog(document.getElementById('packageDialog'));
    document.getElementById('pkgTitle').focus();
  }
  function bindPackageForm() {
    var form = document.getElementById('packageForm');
    var save = document.getElementById('pkgSave');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var title = document.getElementById('pkgTitle').value.trim();
      var priceRaw = document.getElementById('pkgPrice').value.trim();
      var image = document.getElementById('pkgImage').value.trim();
      var price = Number(priceRaw);
      var bad = null;
      fieldError('pkgTitle', title ? '' : 'Enter a title.');
      if (!title) bad = bad || 'pkgTitle';
      var priceErr = priceRaw === '' ? 'Enter a price.' : (!isFinite(price) || price < 0) ? 'Price must be a number of 0 or more.' : '';
      fieldError('pkgPrice', priceErr);
      if (priceErr) bad = bad || 'pkgPrice';
      var imgErr = image && !safeHttpUrl(image) ? 'Use a full http:// or https:// address.' : '';
      fieldError('pkgImage', imgErr);
      if (imgErr) bad = bad || 'pkgImage';
      if (bad) { document.getElementById(bad).focus(); return; }

      var body = {
        title: title, price: price, imageUrl: image ? safeHttpUrl(image) : '',
        description: document.getElementById('pkgDesc').value.trim(),
        featured: document.getElementById('pkgFeatured').checked
      };
      var editing = editingPkg && editingPkg.id != null;
      var path = editing ? '/admin/packages/' + encodeURIComponent(editingPkg.id) : '/admin/packages';
      save.disabled = true;
      save.innerHTML = '<span class="spinner" aria-hidden="true"></span>Saving…';
      var formErr = document.getElementById('pkgFormErr');
      formErr.hidden = true;
      adminApi(path, { method: editing ? 'PUT' : 'POST', body: body }).then(function () {
        closeDialog(document.getElementById('packageDialog'));
        TM.toast(editing ? 'Package updated.' : 'Package created.', 'ok');
        loadTable('packages');
      }, function (err) {
        if (err.status === 401) return;
        formErr.textContent = err.message;
        formErr.hidden = false;
      }).then(function () {
        save.disabled = false;
        save.textContent = 'Save package';
      });
    });
  }

  // ---------- Mutations ----------
  var MUT = {
    requests: { base: '/admin/itinerary-requests/', list: REQ_STATUS, count: 'pendingRequests', countStatus: 'pending', noun: 'Request' },
    contact: { base: '/admin/contact/', list: MSG_STATUS, count: 'contactUnread', countStatus: 'unread', noun: 'Message' }
  };
  function changeStatus(sel) {
    var key = sel.getAttribute('data-status');
    var m = MUT[key];
    var row = findRow(key, sel.getAttribute('data-id'));
    if (!m || !row) return;
    var prev = row.status || m.countStatus;
    var next = sel.value;
    if (prev === next) return;
    // optimistic
    row.status = next;
    sel.setAttribute('data-tone', toneOf(m.list, next));
    sel.disabled = true;
    adjustCount(m, prev, next);
    adminApi(m.base + encodeURIComponent(row.id) + '/status', { method: 'PUT', body: { status: next } }).then(function () {
      sel.disabled = false;
      TM.toast(m.noun + ' marked ' + cap(next).toLowerCase() + '.', 'ok');
      var t = tables[key];
      if (t.renderFilters) t.renderFilters(t.rows);
      var fsel = document.querySelector('[data-filter="' + key + '"]');
      if (fsel && fsel.value && fsel.value !== next) renderTable(key);
    }, function (err) {
      row.status = prev;
      adjustCount(m, next, prev);
      sel.value = prev;
      sel.setAttribute('data-tone', toneOf(m.list, prev));
      sel.disabled = false;
      if (err.status !== 401) TM.toast('Couldn’t update status: ' + err.message, 'bad');
    });
  }
  function adjustCount(m, from, to) {
    var c = state.counts[m.count] || 0;
    if (from === m.countStatus && to !== m.countStatus) c = Math.max(0, c - 1);
    if (to === m.countStatus && from !== m.countStatus) c += 1;
    setCount(m.count, c);
  }
  function deleteRow(key, id, path, confirmText, doneText) {
    var row = findRow(key, id);
    if (!row || !window.confirm(confirmText)) return;
    var t = tables[key];
    adminApi(path, { method: 'DELETE' }).then(function () {
      t.rows = t.rows.filter(function (r) { return r !== row; });
      if (t.afterLoad) t.afterLoad(t.rows);
      renderTable(key);
      TM.toast(doneText, 'ok');
    }, function (err) {
      if (err.status !== 401) TM.toast('Couldn’t delete: ' + err.message, 'bad');
    });
  }

  function exportLeads(btn) {
    btn.disabled = true;
    var label = btn.textContent;
    btn.innerHTML = '<span class="spinner" aria-hidden="true"></span>Exporting…';
    adminApi('/admin/leads/export', { as: 'blob' }).then(function (res) {
      var url = URL.createObjectURL(res.blob);
      var a = document.createElement('a');
      a.href = url; a.download = res.filename; a.rel = 'noopener';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
      TM.toast('Leads exported.', 'ok');
    }, function (err) {
      if (err.status !== 401) TM.toast('Export failed: ' + err.message, 'bad');
    }).then(function () { btn.disabled = false; btn.textContent = label; });
  }

  // ------------------------------------------------------------------
  // Events
  // ------------------------------------------------------------------
  function bindEvents() {
    document.getElementById('sideNav').addEventListener('click', function (e) {
      var b = e.target.closest('[data-nav]');
      if (b) showView(b.getAttribute('data-nav'), { focus: true });
    });
    document.getElementById('signOut').addEventListener('click', signOut);
    document.getElementById('refreshBtn').addEventListener('click', refreshCurrent);

    var main = document.getElementById('adminMain');
    main.addEventListener('click', function (e) {
      var b = e.target.closest('[data-action]');
      if (!b) return;
      var a = b.getAttribute('data-action');
      var id = b.getAttribute('data-id');
      if (a === 'retry') { var v = b.getAttribute('data-view'); v === 'overview' ? loadStats() : loadTable(v); }
      else if (a === 'goto') {
        var view = b.getAttribute('data-view');
        var fv = b.getAttribute('data-filter-value');
        var fsel = document.querySelector('[data-filter="' + view + '"]');
        if (fsel && fv) {
          if (!fsel.querySelector('option[value="' + fv + '"]')) fsel.insertAdjacentHTML('beforeend', '<option value="' + esc(fv) + '"></option>');
          fsel.value = fv;
        }
        showView(view, { focus: true });
      }
      else if (a === 'clear-filters') {
        var k = b.getAttribute('data-view');
        var s = document.querySelector('[data-search="' + k + '"]'); if (s) s.value = '';
        var f = document.querySelector('[data-filter="' + k + '"]'); if (f) f.value = '';
        renderTable(k);
      }
      else if (a === 'req-view') openRequest(id);
      else if (a === 'req-delete') deleteRow('requests', id, '/admin/itinerary-requests/' + encodeURIComponent(id),
        'Delete this itinerary request? This cannot be undone.', 'Request deleted.');
      else if (a === 'pkg-new') openPackage(null);
      else if (a === 'pkg-edit') { var p = findRow('packages', id); if (p) openPackage(p); }
      else if (a === 'pkg-delete') {
        var pk = findRow('packages', id);
        deleteRow('packages', id, '/admin/packages/' + encodeURIComponent(id),
          'Delete the package “' + (pk ? plain(pk.title) : '') + '”? This cannot be undone.', 'Package deleted.');
      }
      else if (a === 'leads-export') exportLeads(b);
      else if (a === 'msg-toggle') {
        var target = document.getElementById(b.getAttribute('aria-controls'));
        var open = b.getAttribute('aria-expanded') !== 'true';
        b.setAttribute('aria-expanded', String(open));
        b.textContent = open ? 'Show less' : 'Show more';
        if (target) target.classList.toggle('clamped', !open);
      }
      else if (a === 'msg-delete') deleteRow('contact', id, '/admin/contact/' + encodeURIComponent(id),
        'Delete this message? This cannot be undone.', 'Message deleted.');
    });
    main.addEventListener('change', function (e) {
      var t = e.target;
      if (t.matches('select[data-status]')) changeStatus(t);
      else if (t.matches('[data-filter]')) renderTable(t.getAttribute('data-filter'));
    });
    var timer = null;
    main.addEventListener('input', function (e) {
      var t = e.target;
      if (!t.matches('[data-search]')) return;
      clearTimeout(timer);
      timer = setTimeout(function () { renderTable(t.getAttribute('data-search')); }, 120);
    });
    window.addEventListener('hashchange', function () {
      var k = (window.location.hash || '').replace('#', '');
      if (k && k !== state.view && !document.getElementById('appView').hidden) showView(k);
    });
  }

  // ------------------------------------------------------------------
  // Demo data (local hosts + ?demo=1 only)
  // ------------------------------------------------------------------
  var demoDB = null;
  function demoData() {
    if (demoDB) return demoDB;
    var now = Date.now();
    var ago = function (h) { return new Date(now - h * 3600e3).toISOString(); };
    demoDB = {
      requests: [
        { id: 41, createdAt: ago(2), name: 'Ananya Iyer', email: 'ananya.iyer@example.com', phone: '+91 98450 12345', destination: 'Coorg', startDate: '2026-12-19', days: 4, travellers: 2, budget: 38000, preferences: ['nature', 'relax'], notes: 'Anniversary trip. Would love a homestay with a view of the coffee estates, and one day of light trekking. Vegetarian food please.', status: 'pending' },
        { id: 40, createdAt: ago(9), name: 'Rohit Malhotra', email: 'rohit.m@example.com', phone: '+91 99100 55555', destination: 'Rishikesh', startDate: '2026-11-28', days: 3, travellers: 4, budget: 30000, preferences: 'adventure', notes: 'Rafting for all four of us.', status: 'in_progress' },
        { id: 39, createdAt: ago(30), name: '<img src=x onerror=alert(1)>', email: 'test@example.com', destination: '<script>alert(1)</script>', travelStartDate: '2027-01-05', travelEndDate: '2027-01-12', days: 8, travelers: 3, budget: 90000, specialRequest: '"><svg onload=alert(1)>', status: 'sent' },
        { id: 38, createdAt: ago(80), name: 'Fatima Sheikh', phone: '+91 90040 11122', destination: 'Udaipur', days: 3, travellers: 2, budget: 25000, status: 'closed' },
        { id: 37, createdAt: ago(120), name: 'Karthik R', email: 'karthik@example.com', destination: 'Gokarna', startDate: '2026-12-02', days: 5, travellers: 5, budget: 42000, notes: '', status: 'pending' }
      ],
      plans: [
        { id: 901, createdAt: ago(1), userId: 12, destination: 'Coorg', query: JSON.stringify({ origin: 'Bengaluru', days: 4, travellers: 2, budget: 30000 }), total: 24200 },
        { id: 900, createdAt: ago(3), userId: null, destination: 'Rishikesh', query: { originLabel: 'Delhi', days: 3, travellers: 4, budget: 28000 }, total: 26150 },
        { id: 899, createdAt: ago(5), query: 'not json', total: null },
        { id: 898, createdAt: ago(26), userId: 7, destination: 'Pondicherry', query: '{"origin":"Chennai","days":2,"travellers":1,"budget":9000}', total: 8420 }
      ],
      packages: [
        { id: 3, title: 'Spiti winter circuit', price: 32999, imageUrl: 'https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?auto=format&fit=crop&w=160&q=60', description: '7 days from Chandigarh, shared stays, permits included.', featured: true },
        { id: 2, title: 'Kerala backwaters weekend', price: 14500, imageUrl: 'javascript:alert(1)', description: 'Houseboat night in Alleppey plus a day in Kumarakom.', featured: false },
        { id: 1, title: 'Old Delhi food walk', price: 1800, imageUrl: '', description: '', featured: false }
      ],
      leads: [
        { id: 5, createdAt: ago(4), name: 'Sneha P', phone: '+91 87654 32109', budget: '15000', destinationInterest: 'Goa' },
        { id: 4, createdAt: ago(50), name: 'Vikram', phone: '+91 98765 43210', budget: '10-15k', destinationInterest: 'Manali' },
        { id: 3, createdAt: ago(70), email: 'lead@example.com' }
      ],
      contact: [
        { id: 21, createdAt: ago(1.5), name: 'Meera Nair', email: 'meera@example.com', phone: '+91 94470 00000', message: 'Hi, I planned a trip to Munnar with the planner and the train estimate looked lower than what I see on IRCTC for the dates I picked. Is the estimate for sleeper class or 3A? Also, does the expert planning fee include hotel booking help, or only the itinerary? Thanks!', status: 'unread' },
        { id: 20, createdAt: ago(20), name: 'Aditya', email: 'aditya@example.com', message: 'Love the site. Please add Northeast destinations.', status: 'replied' },
        { id: 19, createdAt: ago(44), name: 'Unknown', message: '<b>bold?</b> & "quotes"', status: 'read' }
      ],
      users: [
        { id: 12, name: 'Ananya Iyer', email: 'ananya.iyer@example.com', createdAt: ago(200) },
        { id: 7, name: 'Rahul K', email: 'rahul@example.com', createdAt: ago(900) },
        { id: 3, email: 'no-name@example.com', createdAt: [2026, 3, 14, 18, 5, 0] }
      ],
      payments: [
        { id: 8, createdAt: ago(6), userId: 12, requestType: 'expert_itinerary', paymentStatus: 'paid', amount: 199 },
        { id: 7, createdAt: ago(30), userId: 7, requestType: 'expert_itinerary', paymentStatus: 'pending', amount: 199 },
        { id: 6, createdAt: ago(90), userId: null, type: 'expert_itinerary', status: 'failed', amount: 199 }
      ]
    };
    return demoDB;
  }
  function demoApi(path, opts) {
    var db = demoData();
    var method = (opts.method || 'GET').toUpperCase();
    var delay = function (v) { return new Promise(function (res) { setTimeout(function () { res(v); }, 350); }); };
    var m;
    if (path === '/admin/stats') {
      return delay({
        users: db.users.length, plans: 1284, itineraryRequests: db.requests.length,
        pendingRequests: db.requests.filter(function (r) { return r.status === 'pending'; }).length,
        contactUnread: db.contact.filter(function (r) { return r.status === 'unread'; }).length, leads: 312,
        topDestinations: [{ destination: 'Rishikesh', count: 182 }, { destination: 'Coorg', count: 141 }, { destination: 'Goa', count: 133 },
          { destination: 'Udaipur', count: 96 }, { destination: 'Pondicherry', count: 71 }, { destination: '<i>Gokarna</i>', count: 40 }]
      });
    }
    var map = { '/admin/itinerary-requests': 'requests', '/admin/plans': 'plans', '/admin/packages': 'packages', '/admin/leads': 'leads',
      '/admin/contact': 'contact', '/admin/users': 'users', '/admin/payments': 'payments' };
    if (method === 'GET' && map[path]) return delay(JSON.parse(JSON.stringify(db[map[path]])));
    if (path === '/admin/leads/export') {
      return delay({ blob: new Blob(['name,phone\nSneha P,+91 87654 32109\n'], { type: 'text/csv' }), filename: 'tripmind-leads-demo.csv' });
    }
    if ((m = /^\/admin\/(itinerary-requests|contact)\/([^/]+)\/status$/.exec(path)) && method === 'PUT') {
      var list = db[m[1] === 'contact' ? 'contact' : 'requests'];
      if (m[2] === '38') return delay(null).then(function () { throw new AdminError('Demo: simulated server error.', 500); });
      list.forEach(function (r) { if (String(r.id) === decodeURIComponent(m[2])) r.status = opts.body.status; });
      return delay({ ok: true });
    }
    if ((m = /^\/admin\/(itinerary-requests|contact|packages)\/([^/]+)$/.exec(path)) && method === 'DELETE') {
      var k = { 'itinerary-requests': 'requests', contact: 'contact', packages: 'packages' }[m[1]];
      db[k] = db[k].filter(function (r) { return String(r.id) !== decodeURIComponent(m[2]); });
      return delay(null);
    }
    if (path === '/admin/packages' && method === 'POST') {
      var np = Object.assign({ id: Date.now() % 100000 }, opts.body);
      db.packages.unshift(np);
      return delay(np);
    }
    if ((m = /^\/admin\/packages\/([^/]+)$/.exec(path)) && method === 'PUT') {
      db.packages = db.packages.map(function (p) { return String(p.id) === decodeURIComponent(m[1]) ? Object.assign({ id: p.id }, opts.body) : p; });
      return delay({});
    }
    return delay(null).then(function () { throw new AdminError('Demo: no mock for ' + method + ' ' + path, 404); });
  }

  // ------------------------------------------------------------------
  // Boot
  // ------------------------------------------------------------------
  function boot() {
    bindLogin();
    bindDialogs();
    bindPackageForm();
    bindEvents();
    renderNav();
    statusFilterOptions('reqStatus', REQ_STATUS, null, 'pending');
    statusFilterOptions('msgStatus', MSG_STATUS, null, 'unread');
    if (DEMO || getCreds()) showApp(null);
    else showLogin('');
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
