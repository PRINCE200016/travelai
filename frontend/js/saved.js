// Saved trips (localStorage) + account search history (server, when signed in).
(function () {
  var Q = window.TMQuery;
  var esc = TM.esc, inr = TM.inr, icon = TM.icon;
  var list = document.getElementById('savedList');
  var user = TM.getUser();

  if (user) {
    document.getElementById('savedTitle').textContent = 'My trips';
    loadHistory();
  } else {
    document.getElementById('signInPrompt').hidden = false;
  }
  renderSaved();

  function renderSaved() {
    var items = TM.savedTrips();
    if (!items.length) {
      list.innerHTML = '<div class="empty" style="grid-column:1/-1"><h2 class="h-3">No saved trips yet</h2><p>When a plan looks right, press <strong>Save</strong> on the results page and it will appear here.</p><a class="btn btn-dark" href="plan.html">Plan a trip</a></div>';
      return;
    }
    list.innerHTML = items.map(function (s) {
      var t = s.trip, q = s.query;
      var when = q.start ? Q.formatDate(q.start, { day: 'numeric', month: 'short' }) : (t.weather ? t.weather.month : 'Flexible');
      return '<article class="saved-card">' +
        '<img src="' + esc(String(t.image || '').replace(/w=\d+/, 'w=800')) + '" alt="' + esc(t.imageAlt) + '" loading="lazy">' +
        '<div class="body">' +
          '<div class="row between"><h3>' + esc(t.name) + '</h3><span class="badge">' + inr(t.cost.total) + '</span></div>' +
          '<p class="muted small">From ' + esc(s.originLabel || q.origin) + ' · ' + q.days + (q.days === 1 ? ' day' : ' days') + ' · ' + q.travellers + (q.travellers === 1 ? ' person' : ' people') + ' · ' + esc(when) + '</p>' +
          '<p class="xs muted">Saved ' + new Date(s.savedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) + '</p>' +
          '<div class="actions">' +
            '<a class="btn btn-sm btn-dark" href="result.html?saved=' + encodeURIComponent(s.id) + '">Open</a>' +
            '<a class="btn btn-sm" href="result.html?' + esc(Q.toParams(q)) + '&focus=' + esc(t.id) + '">' + icon('refresh') + 'Today’s prices</a>' +
            '<button class="btn btn-sm btn-ghost" type="button" data-remove="' + esc(s.id) + '" aria-label="Remove ' + esc(t.name) + '">' + icon('trash') + '</button>' +
          '</div>' +
        '</div></article>';
    }).join('');
  }

  list.addEventListener('click', function (e) {
    var b = e.target.closest('[data-remove]');
    if (!b) return;
    var id = b.getAttribute('data-remove');
    var removed = TM.savedTrips().filter(function (s) { return s.id === id; })[0];
    TM.removeTrip(id);
    renderSaved();
    TM.toast('Removed ' + (removed ? removed.trip.name : 'trip'));
  });

  function loadHistory() {
    var section = document.getElementById('historySection');
    var box = document.getElementById('history');
    section.hidden = false;
    box.innerHTML = '<div class="skeleton" style="height:56px"></div><div class="skeleton" style="height:56px;margin-top:8px"></div>';
    TM.api('/me/plans').then(function (rows) {
      if (!rows.length) { box.innerHTML = '<p class="muted">Your searches will appear here.</p>'; return; }
      box.innerHTML = rows.map(function (r) {
        var q = r.query || {};
        var query = { origin: q.originLabel || q.origin || '', start: q.dateAssumed ? '' : (q.startDate || ''), days: q.days || 3, travellers: q.travellers || 2,
          group: q.group || 'couple', budget: q.budget || 0, vibes: q.vibes || [], weather: q.weather || 'any', crowd: q.crowd || 'any', pace: q.pace || 'balanced', comfort: q.comfort || 'auto' };
        var label = r.status === 'ok' ? 'Top pick: ' + esc(r.topDestination || '') + (r.total ? ' · ' + inr(r.total) : '')
          : r.status === 'over_budget' ? 'Nothing fit the budget' : 'No trips found';
        return '<a href="result.html?' + esc(Q.toParams(query)) + '"><strong>' + esc(query.origin) + ' · ' + query.days + ' days · ' + inr(query.budget) + '</strong>' +
          '<span class="xs muted">' + new Date(r.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) + '</span>' +
          '<span class="small muted">' + label + '</span></a>';
      }).join('');
    }, function (err) {
      if (err.status === 401) { section.hidden = true; document.getElementById('signInPrompt').hidden = false; return; }
      box.innerHTML = '<p class="muted">' + esc(err.message) + '</p>';
    });
  }
})();
