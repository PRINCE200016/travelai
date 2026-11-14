// Expert planning request. The backend is the record of truth; the team email via EmailJS is best-effort.
(function () {
  var EMAILJS = { service: 'service_ure4hqw', template: 'template_ngvq9hn', publicKey: '0oMOWCSvskq3UgmdP' };
  var PREFS = [['relax', 'Relaxing'], ['adventure', 'Adventure'], ['spiritual', 'Spiritual'], ['heritage', 'History & culture'], ['nature', 'Nature'], ['food', 'Food'], ['nightlife', 'Nightlife'], ['family', 'Kid-friendly']];

  var form = document.getElementById('expertForm');
  var $ = function (id) { return document.getElementById(id); };
  var btn = $('submitBtn');

  $('prefs').innerHTML = PREFS.map(function (p) {
    return '<label class="choice"><input type="checkbox" name="prefs" value="' + p[0] + '"><span>' + TM.esc(p[1]) + '</span></label>';
  }).join('');
  TM.bindMoneyInput($('budget'));
  var today = new Date(); today = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  $('start').min = today;

  // Prefill from a results page (?destination=&days=&travellers=&budget=&start=) and from the account.
  var p = new URLSearchParams(location.search);
  if (p.get('destination')) $('destination').value = p.get('destination').slice(0, 160);
  if (p.get('days')) $('days').value = Math.max(1, Math.min(60, parseInt(p.get('days'), 10) || 4));
  if (p.get('travellers')) $('travellers').value = Math.max(1, Math.min(50, parseInt(p.get('travellers'), 10) || 2));
  if (p.get('budget')) $('budget').value = (parseInt(p.get('budget'), 10) || 0).toLocaleString('en-IN') || '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(p.get('start') || '') && p.get('start') >= today) $('start').value = p.get('start');
  var user = TM.getUser();
  if (user) { $('name').value = user.name || ''; $('email').value = user.email || ''; }

  function fail(msg, field) {
    $('formError').innerHTML = TM.icon('alert') + '<p>' + TM.esc(msg) + '</p>';
    $('formError').hidden = false;
    if (field) { field.setAttribute('aria-invalid', 'true'); field.focus(); }
  }
  form.addEventListener('input', function (e) { e.target.removeAttribute('aria-invalid'); });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    $('formError').hidden = true;
    var data = {
      name: $('name').value.trim(),
      email: $('email').value.trim(),
      phone: $('phone').value.trim(),
      destination: $('destination').value.trim(),
      startDate: $('start').value || null,
      days: parseInt($('days').value, 10) || null,
      travellers: parseInt($('travellers').value, 10) || null,
      budget: TM.parseMoney($('budget').value) || null,
      preferences: Array.prototype.map.call(form.querySelectorAll('input[name="prefs"]:checked'), function (c) { return c.value; }),
      notes: $('notes').value.trim()
    };
    if (!data.name) return fail('Please enter your name.', $('name'));
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(data.email)) return fail('Please enter a valid email address so we can send your plan.', $('email'));
    if (data.phone && !/^[+0-9 ()-]{7,20}$/.test(data.phone)) return fail('That phone number doesn’t look right.', $('phone'));
    if (!data.destination) return fail('Tell us where you’d like to go, even roughly.', $('destination'));
    if (!$('consent').checked) return fail('Please tick the box so we can contact you about this request.', $('consent'));

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span>Sending…';
    TM.api('/itinerary-request', { body: data }).then(function (res) {
      notifyTeam(data, res && res.id);
      showDone(data);
      TM.track('expert_request', { destination: data.destination });
    }, function (err) {
      btn.disabled = false;
      btn.textContent = 'Send request';
      fail(err.status === 429 ? 'You’ve sent a few requests already. Please wait a minute and try again.' : err.message);
    });
  });

  function notifyTeam(d, id) {
    try {
      if (!window.emailjs) return;
      window.emailjs.send(EMAILJS.service, EMAILJS.template, {
        name: d.name, email: d.email, reply_to: d.email, phone: d.phone || 'Not given',
        destination: d.destination, travel_date: d.startDate || 'Flexible',
        budget: d.budget ? '₹' + d.budget.toLocaleString('en-IN') : 'Not given', travelers: d.travellers || '',
        message: 'Request #' + (id || '?') + '\nDays: ' + (d.days || '?') + '\nPreferences: ' + (d.preferences.join(', ') || 'None') + '\nNotes: ' + (d.notes || 'None')
      }, { publicKey: EMAILJS.publicKey }).catch(function () { /* saved on the server regardless */ });
    } catch (_) {}
  }

  function showDone(d) {
    form.hidden = true;
    var done = $('done');
    done.hidden = false;
    $('doneText').textContent = 'Thanks, ' + d.name.split(' ')[0] + '. We’ll email ' + d.email + ' within a few hours to confirm the details and share a payment link. Your plan follows within 24 hours of payment.';
    var msg = 'Hi TripMind, I just requested an expert plan for ' + d.destination + (d.startDate ? ' from ' + d.startDate : '') + '. My email is ' + d.email + '.';
    $('waLink').href = 'https://wa.me/' + TM.config.whatsapp + '?text=' + encodeURIComponent(msg);
    done.focus();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
})();
