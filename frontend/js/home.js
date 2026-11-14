// Home: quick planner in the hero hands off to plan.html with the basics filled in.
(function () {
  var form = document.getElementById('quickPlan');
  if (!form) return;
  var origin = document.getElementById('qpOrigin');
  var budget = document.getElementById('qpBudget');
  var error = document.getElementById('qpError');

  TM.fillOriginList('originList');
  TM.bindMoneyInput(budget);

  // Remember the last search so returning visitors don't retype.
  var last = TM.storageGet('tm_last_query', null);
  if (last) {
    if (last.origin) origin.value = last.origin;
    if (last.budget) budget.value = Number(last.budget).toLocaleString('en-IN');
    if (last.days) document.getElementById('qpDays').value = String(last.days);
    if (last.travellers) document.getElementById('qpTravellers').value = String(Math.min(6, last.travellers));
  }

  function fail(msg, field) {
    error.textContent = msg;
    error.hidden = false;
    field.setAttribute('aria-invalid', 'true');
    field.focus();
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    error.hidden = true;
    origin.removeAttribute('aria-invalid');
    budget.removeAttribute('aria-invalid');

    var o = origin.value.trim();
    var b = TM.parseMoney(budget.value);
    var people = Number(document.getElementById('qpTravellers').value);
    if (o.length < 2) return fail('Tell us which city you’re leaving from.', origin);
    if (!b) return fail('Enter a total budget for the trip.', budget);
    if (b < 1000 * people) return fail('That’s under ₹1,000 a person, which won’t cover a trip. Try a higher budget.', budget);

    var params = new URLSearchParams({
      origin: o,
      days: document.getElementById('qpDays').value,
      travellers: String(people),
      budget: String(b)
    });
    TM.track('quick_plan_submit', { days: params.get('days') });
    window.location.href = 'plan.html?' + params.toString();
  });
})();
