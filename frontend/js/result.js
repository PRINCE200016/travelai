// Results: calls POST /api/plan with the query from the URL and renders every outcome honestly.
(function () {
  var Q = window.TMQuery;
  var esc = TM.esc, inr = TM.inr, icon = TM.icon;
  var root = document.getElementById('results');
  var params = new URLSearchParams(window.location.search);
  var query = Q.fromParams();
  var savedId = params.get('saved');
  var lastResponse = null;

  document.getElementById('editSearch').href = 'plan.html?' + Q.toParams(query);
  renderQueryBar(query, null);

  if (savedId) return showSaved(savedId);
  if (!query.origin || !query.budget) {
    document.getElementById('resultsTitle').textContent = 'Start a new search';
    root.setAttribute('aria-busy', 'false');
    root.innerHTML = '<div class="no-results"><div class="empty"><h2 class="h-2">We need a few details first</h2><p>Tell us where you’re leaving from and your budget, and we’ll find trips that fit.</p><a class="btn btn-primary btn-lg" href="plan.html">Plan a trip</a></div></div>';
    return;
  }
  run();

  // ---------------------------------------------------------------- fetch
  function run() {
    var messages = ['Asking our AI for places across India\u2026', 'Checking each place on the map\u2026', 'Looking up last year\u2019s weather for your month\u2026', 'Costing routes from ' + query.origin + '\u2026', 'Ranking trips by what you asked for\u2026'];
    var i = 0;
    var t = setInterval(function () { i = Math.min(i + 1, messages.length - 1); var el = document.getElementById('loadingText'); if (el) el.textContent = messages[i]; }, 3500);
    document.getElementById('loadingText').textContent = messages[0];

    TM.api('/plan', { body: Q.toApi(query), timeout: 150000 }).then(function (res) {
      clearInterval(t);
      lastResponse = res;
      root.setAttribute('aria-busy', 'false');
      render(res);
      TM.track('plan_result', { status: res.status, top: res.recommendations && res.recommendations[0] ? res.recommendations[0].id : '' });
    }, function (err) {
      clearInterval(t);
      root.setAttribute('aria-busy', 'false');
      document.getElementById('resultsTitle').textContent = 'We couldn’t load your trips';
      root.innerHTML = '<div class="no-results"><div class="notice notice-bad" role="alert">' + icon('alert') + '<p>' + esc(err.message) + '</p></div>' +
        '<div class="row"><button class="btn btn-dark btn-lg" type="button" id="retry">' + icon('refresh') + 'Try again</button><a class="btn btn-lg" href="plan.html?' + esc(Q.toParams(query)) + '">Edit search</a></div></div>';
      document.getElementById('retry').addEventListener('click', function () { location.reload(); });
    });
  }

  // ---------------------------------------------------------------- header
  function renderQueryBar(q, echo) {
    var bits = [];
    bits.push(['pin', '<strong>' + esc(echo && echo.originLabel ? echo.originLabel : q.origin) + '</strong>']);
    bits.push(['calendar', q.start ? esc(Q.formatDate(q.start)) + ' · ' + q.days + (q.days === 1 ? ' day' : ' days')
      : q.days + (q.days === 1 ? ' day' : ' days') + (echo && echo.month ? ', priced for ' + esc(echo.month) : ', flexible dates')]);
    bits.push(['users', q.travellers + (q.travellers === 1 ? ' traveller' : ' travellers') + ' · ' + esc(Q.LABELS.group[q.group] || '')]);
    bits.push(['wallet', '<strong>' + inr(q.budget) + '</strong> total']);
    if (q.vibes.length) bits.push(['compass', esc(q.vibes.map(Q.vibeLabel).join(', '))]);
    if (q.discovery && q.discovery !== 'mix') bits.push(['route', esc(Q.LABELS.discovery[q.discovery])]);
    if (q.notes) bits.push(['info', '\u201c' + esc(q.notes.length > 60 ? q.notes.slice(0, 60) + '\u2026' : q.notes) + '\u201d']);
    document.getElementById('queryBar').innerHTML = bits.map(function (b) { return '<span>' + icon(b[0]) + b[1] + '</span>'; }).join('');
  }

  // ---------------------------------------------------------------- render
  function render(res) {
    renderQueryBar(query, res.query);
    var title = document.getElementById('resultsTitle');
    var from = res.query && res.query.originLabel ? res.query.originLabel : query.origin;
    var html = '';

    if (res.status === 'origin_unknown') {
      title.textContent = 'Which city are you leaving from?';
      html = '<div class="no-results"><p class="lede">' + esc(res.message) + '</p><div class="suggest-list">' +
        (res.originSuggestions || []).map(function (c) { return '<a class="btn btn-lg" href="result.html?' + esc(Q.toParams(Object.assign({}, query, { origin: c }))) + '">' + esc(c) + '</a>'; }).join('') +
        '</div><p class="muted small">Not listed? Choose the nearest big city with a railway station or airport. <a class="link" href="plan.html?' + esc(Q.toParams(query)) + '">Edit search</a></p></div>';
      root.innerHTML = html;
      return;
    }

    html += requestedBlock(res);
    if (res.status === 'ok_other') {
      title.textContent = 'No ' + query.vibes.map(Q.vibeLabel).join(' or ').toLowerCase() + ' trips fit yet';
      html += '<div class="no-results"><p class="lede">' + esc(res.message) + '</p>' + suggestionsBlock(res.suggestions) + '</div>';
      html += stretchBlock(res.stretch, 'Closest matches for what you asked', 'What these would actually cost, so you can decide.');
      html += '<h2 class="h-2 other-picks-title">Trips that fit your budget</h2>';
      res.recommendations.forEach(function (t, i) { html += tripBlock(t, i + 1, res.query); });
    } else if (res.status === 'ok') {
      title.textContent = res.recommendations.length === 1 ? 'One trip fits from ' + from : res.recommendations.length + ' trips that fit from ' + from;
      html += engineNote(res.query) + datesNote(res.query);
      res.recommendations.forEach(function (t, i) {
        if (i === 1) html += '<h2 class="h-2 other-picks-title">Other good fits</h2>';
        html += tripBlock(t, i, res.query);
      });
      if (res.stretch && res.stretch.length) html += stretchBlock(res.stretch, 'If you can stretch your budget', 'These match what you asked for more closely but cost a little more.');
    } else if (res.status === 'over_budget') {
      title.textContent = 'Nothing fits ' + inr(query.budget) + ' yet';
      html += '<div class="no-results"><p class="lede">' + esc(res.message) + '</p>' + suggestionsBlock(res.suggestions) + '</div>';
      html += stretchBlock(res.stretch, 'Closest matches', 'What these would actually cost, so you can decide.');
    } else {
      title.textContent = 'No trips in reach';
      html += '<div class="no-results"><p class="lede">' + esc(res.message) + '</p>' + suggestionsBlock(res.suggestions) +
        '<a class="btn btn-lg" href="plan.html?' + esc(Q.toParams(query)) + '">Edit search</a></div>';
    }
    root.innerHTML = html;
    bindActions(res);
    loadForecast(res);
    if (params.get('focus')) {
      var target = document.getElementById('trip-' + params.get('focus'));
      if (target) target.scrollIntoView({ block: 'start' });
    }
  }

  // The destination the visitor asked about from the Destinations page.
  function requestedBlock(res) {
    if (!query.include) return '';
    if (res.requestedNote) {
      return '<div class="notice notice-warn" style="margin-top:24px">' + icon('info') + '<p>' + esc(res.requestedNote) + ' Here are trips that do work.</p></div>';
    }
    var t = res.requested;
    if (!t) return '';
    var inRecs = (res.recommendations || []).some(function (r) { return r.id === t.id; });
    if (inRecs) {
      return '<div class="notice" style="margin-top:24px">' + icon('check') + '<p><strong>' + esc(t.name) + '</strong> fits your budget and is one of your matches below.</p></div>';
    }
    var lead = t.budgetFit.status === 'over'
      ? 'You asked about ' + t.name + '. It comes to about ' + inr(t.cost.total) + ', which is ' + inr(t.budgetFit.extraNeeded) + ' over your budget.'
      : 'You asked about ' + t.name + '. It fits your budget, though it isn’t one of our closest matches for what you described.';
    return '<section aria-label="' + esc(t.name) + '"><div class="notice" style="margin-top:24px">' + icon('pin') + '<p>' + esc(lead) + '</p></div>' +
      tripBlock(t, 1, res.query) + '<h2 class="h-2 other-picks-title">' + (res.status === 'ok' ? 'Trips that match what you asked for' : 'Other options') + '</h2></section>';
  }

  function engineNote(echo) {
    if (!echo || echo.engine !== 'catalog') return '';
    return '<div class="notice" style="margin-top:24px">' + icon('info') + '<p>' + (echo.aiAvailable
      ? 'Our AI couldn\u2019t find enough verified places for this search just now, so these come from our hand-picked destinations.'
      : 'AI discovery is switched off on this server, so these come from our hand-picked destinations.') + '</p></div>';
  }

  function datesNote(echo) {
    if (!echo || !echo.dateAssumed) return '';
    return '<div class="notice" style="margin-top:24px">' + icon('calendar') + '<p>No dates given, so prices, weather and crowds are for travel in <strong>' + esc(echo.month) +
      '</strong>. <a class="link" href="plan.html?' + esc(Q.toParams(query)) + '">Add dates</a> for a sharper estimate.</p></div>';
  }

  function suggestionsBlock(list) {
    if (!list || !list.length) return '';
    return '<div class="stack"><h2 class="h-3">Quick ways to make it work</h2><div class="suggest-list">' + list.map(function (s) {
      var next = Object.assign({}, query, s.patch || {});
      return '<a class="btn btn-dark" href="result.html?' + esc(Q.toParams(next)) + '">' + esc(s.label) + '</a>';
    }).join('') + '</div><p class="muted small">We never change your budget or dates without you choosing to.</p></div>';
  }

  function stretchBlock(list, heading, sub) {
    if (!list || !list.length) return '';
    return '<section class="section-tight" aria-label="' + esc(heading) + '"><div class="section-head"><h2 class="h-2">' + esc(heading) + '</h2><p class="muted">' + esc(sub) + '</p></div>' +
      '<div class="saved-list">' + list.map(function (t) {
        var extra = t.budgetFit && t.budgetFit.extraNeeded ? t.budgetFit.extraNeeded : 0;
        var target = Math.ceil(t.cost.total / 500) * 500;
        return '<article class="saved-card"><img src="' + esc(imgSize(t.image, 800)) + '" alt="' + esc(t.imageAlt) + '" loading="lazy">' +
          '<div class="body"><div class="row between"><h3>' + esc(t.name) + '</h3><span class="badge">' + t.matchScore + ' match</span></div>' +
          '<p class="muted small">' + esc(t.transport.label) + ' · ' + esc(t.weather.label) + ' in ' + esc(t.weather.month) + '</p>' +
          '<dl class="kv"><div><dt>Total</dt><dd>' + inr(t.cost.total) + '</dd></div>' + (extra ? '<div><dt>Over your budget by</dt><dd>' + inr(extra) + '</dd></div>' : '') + '</dl>' +
          '<div class="actions"><a class="btn btn-sm btn-dark" href="result.html?' + esc(Q.toParams(Object.assign({}, query, { budget: Math.max(query.budget, target) }))) + '&focus=' + esc(t.id) + '">Plan with ' + inr(Math.max(query.budget, target)) + '</a></div></div></article>';
      }).join('') + '</div></section>';
  }

  function fallbackFor(t) {
    var id = t.tags.indexOf('beach') !== -1 ? '1507525428034-b723cf961d3e' : t.tags.indexOf('hills') !== -1 ? '1544634076-a90160ddf44c'
      : t.tags.indexOf('spiritual') !== -1 ? '1571536802807-30451e3955d8' : t.tags.indexOf('heritage') !== -1 ? '1615836245337-f5b9b2303f10'
      : '1469474968028-56623f02e42e';
    return 'https://images.unsplash.com/photo-' + id + '?auto=format&fit=crop&w=1400&q=70';
  }
  document.addEventListener('error', function (e) {
    var img = e.target;
    if (img && img.tagName === 'IMG' && img.dataset.fallback && img.src !== img.dataset.fallback) {
      img.src = img.dataset.fallback;
      img.alt = 'Representative photo';
    }
  }, true);

  function imgSize(url, w) { return String(url || '').replace(/w=\d+/, 'w=' + w); }

  function modeIcon(mode, label) {
    if (mode === 'flight') return 'plane';
    if (mode === 'train') return 'train';
    return /cab/i.test(label) ? 'car' : 'bus';
  }

  function hours(h) {
    if (h < 1) return 'under an hour';
    var r = Math.round(h * 2) / 2;
    return r + (r === 1 ? ' hour' : ' hours');
  }

  function tripBlock(t, index, echo) {
    var c = t.cost, fit = t.budgetFit;
    var pct = Math.min(100, Math.round(c.total / fit.budget * 100));
    var fitBadge = fit.status === 'fits' ? '<span class="badge badge-ok"><span class="dot"></span>Fits budget</span>'
      : fit.status === 'tight' ? '<span class="badge badge-warn"><span class="dot"></span>Just fits</span>'
      : '<span class="badge badge-bad"><span class="dot"></span>Over budget</span>';
    var crowdBadge = { low: 'badge-ok', moderate: 'badge-warn', high: 'badge-bad' }[t.crowd.level] || '';
    var saveId = tripKey(t);
    var saved = TM.isSaved(saveId);
    var days = t.dayPlan || [];
    var shown = days.slice(0, 3);
    var expertUrl = 'itinerary.html?' + new URLSearchParams({ destination: t.name + ', ' + t.state, days: query.days, travellers: query.travellers, budget: query.budget, start: query.start || '' }).toString();

    var costRows = [
      [t.transport.label + ', return', c.transport],
      [c.nights ? c.stayLabel + ', ' + c.nights + (c.nights === 1 ? ' night' : ' nights') + (c.rooms > 1 ? ', ' + c.rooms + (/dorm/i.test(c.stayLabel) ? ' beds' : ' rooms') : '') : 'No overnight stay needed', c.stay],
      ['Food', c.food],
      ['Local transport', c.local],
      ['Activities & entry fees', c.activities],
      ['Buffer for tips and extras', c.buffer]
    ];

    return '<article class="trip" id="trip-' + esc(t.id) + '" aria-labelledby="t-' + esc(t.id) + '">' +
      '<div>' +
        '<div class="trip-photo"><img src="' + esc(imgSize(t.image, window.innerWidth < 700 ? 800 : 1400)) + '" alt="' + esc(t.imageAlt) + '" data-fallback="' + esc(fallbackFor(t)) + '"' + (index ? ' loading="lazy"' : ' fetchpriority="high"') + '>' +
          '<div class="rank">' + (index === 0 ? '<span class="rank-pill">Best match</span>' : '') +
          (t.kind === 'offbeat' ? '<span class="rank-pill gem">Hidden gem</span>' : '') + '</div></div>' +
        '<div class="trip-title"><div><h2 id="t-' + esc(t.id) + '">' + esc(t.name) + '</h2><p class="state">' + esc(t.state) + ' · ' + t.distanceKm.toLocaleString('en-IN') + ' km from ' + esc(echo.originLabel) + '</p></div>' +
          '<div class="score"><div class="score-ring" style="--p:' + t.matchScore + '"><span>' + t.matchScore + '</span></div><small>match</small></div></div>' +
        '<p class="trip-summary">' + esc(t.summary) + '</p>' +
        (t.narrative ? '<p class="trip-summary"><strong>Why it suits you:</strong> ' + esc(t.narrative) + '</p>' : '') +
        (t.localTip ? '<div class="local-tip">' + icon('compass') + '<p><strong>Local tip</strong>' + esc(t.localTip) + '</p></div>' : '') +
        '<div class="facts">' +
          fact(modeIcon(t.transport.mode, t.transport.label), 'Getting there', esc(t.transport.label), t.transport.overnight ? 'Overnight, about ' + hours(t.transport.oneWayHours) : 'About ' + hours(t.transport.oneWayHours) + ' each way') +
          fact('sun', 'Weather in ' + esc(t.weather.month), esc(t.weather.label), t.weather.lowC + '–' + t.weather.highC + '°C ' + (t.source === 'ai' ? 'last year' : 'typical'), 'wx-' + t.id) +
          fact('crowd', 'Crowds', '<span class="badge ' + crowdBadge + '">' + esc(cap(t.crowd.level)) + '</span>', esc(cap(t.crowd.reasons[0] || ''))) +
          fact('calendar', 'Best months', esc((t.bestMonths || []).join(', ')), '') +
        '</div>' +
        '<div class="why"><h3>Why it’s on your list</h3><ul class="reasons">' +
          t.matchReasons.map(function (r) { return '<li>' + icon('check') + '<span>' + esc(r) + '</span></li>'; }).join('') +
          (t.cautions || []).map(function (r) { return '<li class="caution">' + icon('alert') + '<span>' + esc(r) + '</span></li>'; }).join('') +
        '</ul></div>' +
        '<div class="dayplan"><h3>How the days could go</h3><div class="days" data-days="' + esc(t.id) + '">' +
          shown.map(dayHtml).join('') + '</div>' +
          (days.length > 3 ? '<button class="btn btn-ghost btn-sm dayplan-more" type="button" data-more="' + esc(t.id) + '">Show all ' + days.length + ' days</button>' : '') +
          '<p class="xs muted">An outline to get you started. <a class="link" href="' + esc(expertUrl) + '">Get a detailed plan from an expert</a>.</p>' +
          (t.source === 'ai' ? '<p class="xs muted provenance">' + icon('shield') + 'Suggested by AI, confirmed on the map, weather from last year\u2019s records, costs calculated by TripMind.</p>' : '') +
        '</div>' +
      '</div>' +
      '<aside class="trip-side" aria-label="Cost of ' + esc(t.name) + '">' +
        '<div class="cost-card">' +
          '<div class="cost-head"><span class="small muted">Estimated total</span>' + fitBadge + '</div>' +
          '<div class="cost-total">' + inr(c.total) + '</div>' +
          '<p class="small muted">' + inr(c.perPerson) + ' per person · ' + esc(Q.LABELS.comfort[c.comfort] || c.comfort) + ' comfort</p>' +
          '<div class="budget-meter ' + (fit.status === 'fits' ? '' : fit.status) + '" role="img" aria-label="Uses ' + pct + '% of your budget"><span style="width:' + pct + '%"></span></div>' +
          '<p class="xs muted" style="margin-top:6px">' + (fit.extraNeeded ? inr(fit.extraNeeded) + ' over your ' + inr(fit.budget) : inr(fit.remaining) + ' left of ' + inr(fit.budget)) + '</p>' +
          '<dl class="kv">' + costRows.map(function (r) { return '<div><dt>' + esc(r[0]) + '</dt><dd>' + inr(r[1]) + '</dd></div>'; }).join('') +
            '<div class="kv-total"><dt>Total</dt><dd>' + inr(c.total) + '</dd></div></dl>' +
          '<p class="xs muted">' + esc(c.seasonLabel) + '. ' + esc(t.transport.note || '') + '</p>' +
        '</div>' +
        '<div class="side-actions">' +
          '<div class="row"><button class="btn" type="button" data-save="' + esc(t.id) + '" aria-pressed="' + saved + '">' + icon(saved ? 'bookmarkFill' : 'bookmark') + '<span>' + (saved ? 'Saved' : 'Save') + '</span></button>' +
          '<button class="btn" type="button" data-share="' + esc(t.id) + '">' + icon('share') + 'Share</button></div>' +
          (t.bookingLinks && t.bookingLinks.stay ? '<a class="btn btn-block" href="' + esc(t.bookingLinks.stay) + '" target="_blank" rel="noopener sponsored">' + icon('bed') + 'Find stays on Booking.com' + icon('external') + '</a>' : '') +
          (t.bookingLinks && t.bookingLinks.transport ? '<a class="btn btn-block" href="' + esc(t.bookingLinks.transport) + '" target="_blank" rel="noopener">' + icon(modeIcon(t.transport.mode, t.transport.label)) + transportLinkLabel(t) + icon('external') + '</a>' : '') +
          '<a class="btn btn-primary btn-block" href="' + esc(expertUrl) + '">Have an expert plan this · ₹199</a>' +
        '</div>' +
      '</aside>' +
    '</article>';
  }

  function transportLinkLabel(t) {
    if (t.transport.mode === 'flight') return 'Compare flights';
    if (t.transport.mode === 'train') return 'Check trains on IRCTC';
    return /cab/i.test(t.transport.label) ? 'See the route' : 'Find buses';
  }

  function fact(ic, k, v, n, id) {
    return '<div class="fact"' + (id ? ' id="' + esc(id) + '"' : '') + '><span class="fact-k">' + icon(ic) + k + '</span><span class="fact-v">' + v + '</span>' + (n ? '<span class="fact-n">' + n + '</span>' : '') + '</div>';
  }
  function dayHtml(d) {
    return '<div class="day"><h4><span>Day ' + d.day + '</span>' + esc(d.title) + '</h4><ul>' + d.items.map(function (i) { return '<li>' + esc(i) + '</li>'; }).join('') + '</ul></div>';
  }
  function cap(s) { s = String(s || ''); return s.charAt(0).toUpperCase() + s.slice(1); }

  // ---------------------------------------------------------------- actions
  function tripKey(t) { return t.id + '|' + Q.toParams(query); }
  function findTrip(res, id) {
    var all = (res.recommendations || []).concat(res.stretch || []);
    return all.filter(function (t) { return t.id === id; })[0];
  }

  function bindActions(res) {
    root.addEventListener('click', function (e) {
      var more = e.target.closest('[data-more]');
      if (more) {
        var t = findTrip(res, more.getAttribute('data-more'));
        root.querySelector('[data-days="' + t.id + '"]').innerHTML = t.dayPlan.map(dayHtml).join('');
        more.remove();
        return;
      }
      var save = e.target.closest('[data-save]');
      if (save) {
        var trip = findTrip(res, save.getAttribute('data-save'));
        var key = tripKey(trip);
        var nowSaved;
        if (TM.isSaved(key)) { TM.removeTrip(key); nowSaved = false; TM.toast('Removed from saved trips'); }
        else {
          var ok = TM.saveTrip({ id: key, savedAt: new Date().toISOString(), query: query, originLabel: res.query.originLabel, trip: trip });
          nowSaved = ok;
          TM.toast(ok ? 'Saved. Find it under Saved trips.' : 'Couldn’t save: your browser storage is full or blocked.', ok ? 'ok' : 'bad');
          if (ok) TM.track('trip_saved', { id: trip.id });
        }
        save.setAttribute('aria-pressed', String(nowSaved));
        save.innerHTML = icon(nowSaved ? 'bookmarkFill' : 'bookmark') + '<span>' + (nowSaved ? 'Saved' : 'Save') + '</span>';
        return;
      }
      var share = e.target.closest('[data-share]');
      if (share) openShare(findTrip(res, share.getAttribute('data-share')), res);
    });
  }

  function openShare(t, res) {
    var url = location.origin + location.pathname.replace(/[^/]*$/, '') + 'result.html?' + Q.toParams(query) + '&focus=' + encodeURIComponent(t.id);
    var text = t.name + ' from ' + res.query.originLabel + ': ' + query.days + ' days for ' + query.travellers + ', about ' + inr(t.cost.total) + ' all in.';
    if (navigator.share && /Mobi|Android/i.test(navigator.userAgent)) {
      navigator.share({ title: 'TripMind: ' + t.name, text: text, url: url }).catch(function () {});
      return;
    }
    var dlg = document.getElementById('shareDialog');
    document.getElementById('shareUrl').value = url;
    document.getElementById('shareWhatsapp').href = 'https://wa.me/?text=' + encodeURIComponent(text + ' ' + url);
    if (typeof dlg.showModal === 'function') dlg.showModal(); else window.prompt('Copy this link', url);
    TM.track('share_open', { id: t.id });
  }
  var dlg = document.getElementById('shareDialog');
  dlg.addEventListener('click', function (e) { if (e.target === dlg || e.target.closest('[data-close]')) dlg.close(); });
  document.getElementById('copyLink').addEventListener('click', function () {
    var input = document.getElementById('shareUrl');
    var done = function () { TM.toast('Link copied', 'ok'); };
    if (navigator.clipboard) navigator.clipboard.writeText(input.value).then(done, function () { input.select(); document.execCommand('copy'); done(); });
    else { input.select(); document.execCommand('copy'); done(); }
  });

  // Live forecast when the trip starts within a few days (server returns [] otherwise).
  function loadForecast(res) {
    if (!query.start || !res.recommendations || !res.recommendations.length) return;
    var daysAway = (new Date(query.start + 'T00:00:00') - new Date()) / 86400000;
    if (daysAway > 14.5) return;
    var t = res.recommendations[0];
    TM.api('/forecast?lat=' + t.lat + '&lon=' + t.lon + '&date=' + query.start + '&days=' + Math.min(5, query.days)).then(function (r) {
      if (!r || !r.days || !r.days.length) return;
      var box = document.getElementById('wx-' + t.id);
      if (!box) return;
      var d0 = r.days[0];
      box.querySelector('.fact-k').innerHTML = icon('sun') + 'Forecast';
      box.querySelector('.fact-v').textContent = d0.desc || 'Forecast';
      box.querySelector('.fact-n').textContent = r.days.map(function (d) { return Q.formatDate(d.date, { weekday: 'short' }) + ' ' + d.minC + '–' + d.maxC + '°'; }).join(' · ');
    }, function () {});
  }

  // ---------------------------------------------------------------- saved snapshot
  function showSaved(id) {
    var item = TM.savedTrips().filter(function (s) { return s.id === id; })[0];
    root.setAttribute('aria-busy', 'false');
    if (!item) {
      document.getElementById('resultsTitle').textContent = 'Saved trip not found';
      root.innerHTML = '<div class="no-results"><div class="empty"><h2 class="h-3">This trip isn’t saved on this device</h2><p>Saved trips live in the browser where you saved them.</p><a class="btn btn-dark" href="saved.html">See saved trips</a></div></div>';
      return;
    }
    query = item.query;
    document.getElementById('editSearch').href = 'plan.html?' + Q.toParams(query);
    renderQueryBar(query, { originLabel: item.originLabel });
    document.getElementById('resultsTitle').textContent = item.trip.name + ', saved ' + new Date(item.savedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
    var res = { status: 'ok', query: { originLabel: item.originLabel }, recommendations: [item.trip], stretch: [] };
    root.innerHTML = '<div class="notice" style="margin-top:24px">' + icon('info') + '<p>Prices are as they were when you saved this. <a class="link" href="result.html?' + esc(Q.toParams(query)) + '&focus=' + esc(item.trip.id) + '">Recalculate with today’s prices</a></p></div>' + tripBlock(item.trip, 0, res.query);
    bindActions(res);
  }
})();
