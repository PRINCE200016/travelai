// Planner wizard: three steps, live summary, hands off to result.html with the query in the URL.
(function () {
  var Q = window.TMQuery;
  var form = document.getElementById('planForm');
  var steps = Array.prototype.slice.call(form.querySelectorAll('.step'));
  var bars = Array.prototype.slice.call(document.querySelectorAll('.progress span'));
  var el = function (id) { return document.getElementById(id); };

  // ---------- State: URL params win, then the last search, then defaults
  var params = new URLSearchParams(window.location.search);
  var state = Q.fromParams();
  var last = TM.storageGet('tm_last_query', null);
  if (!params.get('origin') && last) {
    ['origin', 'days', 'travellers', 'group', 'budget', 'weather', 'crowd', 'pace', 'comfort', 'discovery'].forEach(function (k) {
      if (!params.get(k) && last[k] != null) state[k] = last[k];
    });
    if (!params.get('vibes') && Array.isArray(last.vibes) && !params.get('vibes')) state.vibes = last.vibes.slice(0, 3);
  }
  if (state.start && state.start < todayIso()) state.start = '';

  function todayIso() {
    var d = new Date();
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  }

  // ---------- Build dynamic controls
  el('vibeChoices').innerHTML = Q.VIBES.map(function (v) {
    return '<label class="choice"><input type="checkbox" name="vibes" value="' + v.id + '"><span>' + TM.esc(v.label) + '<small>' + TM.esc(v.hint) + '</small></span></label>';
  }).join('');
  TM.fillOriginList('originList');
  TM.bindMoneyInput(el('budget'));
  el('start').min = todayIso();

  // ---------- Hydrate inputs from state
  el('origin').value = state.origin;
  el('start').value = state.start;
  el('flexible').checked = !state.start && !!params.get('origin');
  el('start').disabled = el('flexible').checked;
  el('budget').value = state.budget ? state.budget.toLocaleString('en-IN') : '';
  setRadio('group', state.group);
  setRadio('weather', state.weather);
  setRadio('crowd', state.crowd);
  setRadio('pace', state.pace);
  setRadio('comfort', state.comfort);
  setRadio('discovery', state.discovery);
  el('notes').value = state.notes || '';
  el('noteIdeas').addEventListener('click', function (e) {
    var b = e.target.closest('button');
    if (!b) return;
    var cur = el('notes').value.trim();
    el('notes').value = (cur ? cur.replace(/[.,;]?$/, ', ') : '') + b.textContent.toLowerCase();
    if (!cur) el('notes').value = b.textContent;
    el('notes').focus();
    render();
  });
  form.querySelectorAll('input[name="vibes"]').forEach(function (cb) { cb.checked = state.vibes.indexOf(cb.value) !== -1; });

  function setRadio(name, value) {
    var r = form.querySelector('input[name="' + name + '"][value="' + value + '"]');
    if (r) r.checked = true;
  }
  function radio(name) { var r = form.querySelector('input[name="' + name + '"]:checked'); return r ? r.value : null; }

  // ---------- Steppers + group sync
  form.querySelectorAll('[data-step-for]').forEach(function (b) {
    b.addEventListener('click', function () {
      var key = b.getAttribute('data-step-for');
      var delta = Number(b.getAttribute('data-delta'));
      if (key === 'days') state.days = Math.max(1, Math.min(21, state.days + delta));
      else {
        state.travellers = Math.max(1, Math.min(20, state.travellers + delta));
        syncGroupFromTravellers();
      }
      render();
    });
  });
  function syncGroupFromTravellers() {
    var g = radio('group');
    if (state.travellers === 1) g = 'solo';
    else if (state.travellers === 2 && (g === 'solo' || !g)) g = 'couple';
    else if (state.travellers > 2 && (g === 'solo' || g === 'couple')) g = 'friends';
    else if (state.travellers !== 2 && g === 'couple') g = 'friends';
    setRadio('group', g);
    state.group = g;
  }
  form.querySelectorAll('input[name="group"]').forEach(function (r) {
    r.addEventListener('change', function () {
      state.group = r.value;
      if (r.value === 'solo') state.travellers = 1;
      else if (r.value === 'couple') state.travellers = 2;
      else if (state.travellers < 2) state.travellers = r.value === 'family' ? 3 : 2;
      render();
    });
  });

  // ---------- Vibes (max 3)
  form.querySelectorAll('input[name="vibes"]').forEach(function (cb) {
    cb.addEventListener('change', function () {
      var checked = form.querySelectorAll('input[name="vibes"]:checked');
      if (checked.length > 3) { cb.checked = false; el('vibeHint').textContent = 'You can pick up to 3. Untick one to choose another.'; return; }
      el('vibeHint').textContent = checked.length ? checked.length + ' of 3 chosen.' : 'Choose up to 3.';
      render();
    });
  });

  el('flexible').addEventListener('change', function () {
    el('start').disabled = el('flexible').checked;
    if (el('flexible').checked) el('start').value = '';
    render();
  });
  form.addEventListener('input', render);
  form.addEventListener('change', render);

  // ---------- Read form into state
  function read() {
    state.origin = el('origin').value.trim();
    state.start = el('flexible').checked ? '' : el('start').value;
    state.budget = TM.parseMoney(el('budget').value);
    state.group = radio('group') || state.group;
    state.weather = radio('weather') || 'any';
    state.crowd = radio('crowd') || 'any';
    state.pace = radio('pace') || 'balanced';
    state.comfort = radio('comfort') || 'auto';
    state.discovery = radio('discovery') || 'mix';
    state.notes = el('notes').value.replace(/[<>]/g, ' ').trim().slice(0, 300);
    state.vibes = Array.prototype.map.call(form.querySelectorAll('input[name="vibes"]:checked'), function (c) { return c.value; });
  }

  // ---------- Render summary, presets, per-day
  function render() {
    read();
    el('daysOut').textContent = state.days;
    el('travOut').textContent = state.travellers;
    form.querySelector('[data-step-for="days"][data-delta="-1"]').disabled = state.days <= 1;
    form.querySelector('[data-step-for="days"][data-delta="1"]').disabled = state.days >= 21;
    form.querySelector('[data-step-for="travellers"][data-delta="-1"]').disabled = state.travellers <= 1;
    form.querySelector('[data-step-for="travellers"][data-delta="1"]').disabled = state.travellers >= 20;

    var personDays = state.travellers * state.days;
    var presets = [1800, 3500, 7000].map(function (pp) { return Math.round(pp * personDays / 1000) * 1000; });
    el('presets').innerHTML = presets.map(function (v, i) {
      return '<button type="button" data-preset="' + v + '" aria-label="' + ['Budget', 'Comfortable', 'Premium'][i] + ' budget ' + TM.inr(v) + '">' + TM.inr(v) + '</button>';
    }).join('');

    el('perDay').innerHTML = state.budget
      ? 'About <strong>' + TM.inr(state.budget / personDays) + '</strong> per person per day, all in.'
      : 'Covers travel, stay, food, local transport and activities.';

    var rows = [
      ['From', state.origin || null],
      ['When', state.start ? Q.formatDate(state.start, { day: 'numeric', month: 'short' }) : (el('flexible').checked ? 'Flexible' : null)],
      ['Length', state.days + (state.days === 1 ? ' day' : ' days')],
      ['Who', (Q.LABELS.group[state.group] || '') + ' · ' + state.travellers + (state.travellers === 1 ? ' person' : ' people')],
      ['Budget', state.budget ? TM.inr(state.budget) : null],
      ['Trip type', state.vibes.length ? state.vibes.map(Q.vibeLabel).join(', ') : 'Open to ideas'],
      ['Places', Q.LABELS.discovery[state.discovery]],
      ['In your words', state.notes ? (state.notes.length > 40 ? state.notes.slice(0, 40) + '…' : state.notes) : null],
      ['Comfort', Q.LABELS.comfort[state.comfort]]
    ];
    el('summary').innerHTML = rows.map(function (r) {
      return '<div><dt>' + r[0] + '</dt><dd' + (r[1] ? '' : ' class="empty-val"') + '>' + TM.esc(r[1] || 'Not set') + '</dd></div>';
    }).join('');
    el('sumBudgetTop').textContent = state.budget ? TM.inr(state.budget) : '';
  }
  el('presets').addEventListener('click', function (e) {
    var b = e.target.closest('[data-preset]');
    if (!b) return;
    el('budget').value = Number(b.getAttribute('data-preset')).toLocaleString('en-IN');
    clearError('budget');
    render();
  });

  // ---------- Validation
  function setError(field, msg) {
    var input = el(field);
    var err = el(field + 'Err');
    input.setAttribute('aria-invalid', 'true');
    err.textContent = msg;
    err.hidden = false;
    input.focus();
  }
  function clearError(field) {
    el(field).removeAttribute('aria-invalid');
    el(field + 'Err').hidden = true;
  }
  el('origin').addEventListener('input', function () { clearError('origin'); });
  el('budget').addEventListener('input', function () { clearError('budget'); });

  function validateStep1() {
    read();
    if (state.origin.length < 2) { setError('origin', 'Tell us which city you’re leaving from.'); return false; }
    if (!state.budget) { setError('budget', 'Enter the most you want to spend on the whole trip.'); return false; }
    var floor = 800 * state.travellers;
    if (state.budget < floor) { setError('budget', 'That’s under ₹800 a person, which won’t cover a trip. Try at least ' + TM.inr(floor) + '.'); return false; }
    if (state.start && state.start < todayIso()) { el('start').focus(); TM.toast('Pick a start date from today onwards.', 'bad'); return false; }
    return true;
  }

  // ---------- Step navigation
  var current = 1;
  function go(n, focus) {
    current = n;
    steps.forEach(function (s) { s.hidden = Number(s.getAttribute('data-step')) !== n; });
    bars.forEach(function (b, i) { b.classList.toggle('done', i < n); });
    if (focus !== false) {
      var h = form.querySelector('.step[data-step="' + n + '"] h1');
      h.setAttribute('tabindex', '-1');
      h.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
  form.addEventListener('click', function (e) {
    if (e.target.closest('[data-next]')) {
      if (current === 1 && !validateStep1()) return;
      go(current + 1);
    } else if (e.target.closest('[data-back]')) {
      go(current - 1);
    }
  });
  form.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.tagName === 'INPUT' && e.target.type !== 'checkbox' && e.target.type !== 'radio' && current < 3) {
      e.preventDefault();
      form.querySelector('.step[data-step="' + current + '"] [data-next]').click();
    }
  });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (!validateStep1()) { go(1); validateStep1(); return; }
    read();
    TM.storageSet('tm_last_query', state);
    TM.track('plan_submit', { days: state.days, travellers: state.travellers, vibes: state.vibes.join(',') });
    window.location.href = 'result.html?' + Q.toParams(state);
  });

  // Came from a destination page? Say which place we'll price.
  if (state.include) {
    var note = document.createElement('div');
    note.className = 'notice';
    note.style.marginTop = '24px';
    var fallbackName = state.include.charAt(0).toUpperCase() + state.include.slice(1);
    note.innerHTML = TM.icon('pin') + '<p>We’ll price <strong id="incName">' + TM.esc(fallbackName) + '</strong> for your dates and budget, and show the best alternatives too. <button type="button" class="link" id="incClear">Remove</button></p>';
    form.querySelector('.step[data-step="1"] .lede').after(note);
    TM.api('/destinations/' + encodeURIComponent(state.include), { auth: false }).then(function (d) {
      document.getElementById('incName').textContent = d.name;
    }, function () {});
    document.getElementById('incClear').addEventListener('click', function () { state.include = ''; note.remove(); });
  }

  // Coming from the home page with the basics filled in? Start at step 2.
  render();
  var basicsDone = params.get('origin') && params.get('budget') && state.budget >= 800 * state.travellers;
  go(basicsDone ? 2 : 1, false);
})();
