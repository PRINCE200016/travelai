// Trip query <-> URL params. Every results page is a URL, so it can be shared, refreshed and saved.
(function () {
  var VIBES = [
    { id: 'hills', label: 'Hills', hint: 'Cool air and mountain views' },
    { id: 'beach', label: 'Beaches', hint: 'Sea, sand and slow evenings' },
    { id: 'relax', label: 'Slow down', hint: 'Rest, cafés, nothing rushed' },
    { id: 'adventure', label: 'Adventure', hint: 'Treks, rafting, paragliding' },
    { id: 'spiritual', label: 'Temples & ghats', hint: 'Pilgrimage and quiet ritual' },
    { id: 'heritage', label: 'History', hint: 'Forts, palaces, old cities' },
    { id: 'nature', label: 'Nature', hint: 'Forests, lakes, waterfalls' },
    { id: 'food', label: 'Food', hint: 'Eat your way through a city' },
    { id: 'nightlife', label: 'Nightlife', hint: 'Bars, music, late nights' },
    { id: 'wildlife', label: 'Wildlife', hint: 'Safaris and birding' }
  ];
  var LABELS = {
    group: { solo: 'Solo', couple: 'Couple', family: 'Family', friends: 'Friends' },
    weather: { cold: 'Cold', mild: 'Mild', warm: 'Warm', any: 'Any weather' },
    crowd: { quiet: 'Quiet', any: 'Crowds are fine', lively: 'Lively' },
    pace: { easy: 'Easy pace', balanced: 'Balanced', packed: 'Packed' },
    comfort: { auto: 'Best fit for budget', budget: 'Budget', standard: 'Comfortable', premium: 'Premium' },
    discovery: { mix: 'Famous + hidden gems', hidden: 'Mostly hidden gems', famous: 'Mostly well-known' }
  };
  var DEFAULTS = { days: 3, travellers: 2, weather: 'any', crowd: 'any', pace: 'balanced', comfort: 'auto', discovery: 'mix' };

  function int(v, lo, hi, fallback) {
    var n = parseInt(v, 10);
    if (isNaN(n)) return fallback;
    return Math.max(lo, Math.min(hi, n));
  }
  function pick(v, allowed, fallback) { return allowed.indexOf(v) !== -1 ? v : fallback; }

  function fromParams(search) {
    var p = new URLSearchParams(search || window.location.search);
    var vibes = (p.get('vibes') || '').split(',').map(function (s) { return s.trim(); })
      .filter(function (v) { return VIBES.some(function (x) { return x.id === v; }); }).slice(0, 3);
    var start = p.get('start') || p.get('startDate') || '';
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) start = '';
    var travellers = int(p.get('travellers'), 1, 20, DEFAULTS.travellers);
    var group = pick(p.get('group'), ['solo', 'couple', 'family', 'friends'], travellers === 1 ? 'solo' : travellers === 2 ? 'couple' : 'friends');
    return {
      origin: (p.get('origin') || '').slice(0, 80),
      start: start,
      days: int(p.get('days'), 1, 21, DEFAULTS.days),
      travellers: travellers,
      group: group,
      budget: int(p.get('budget'), 0, 5000000, 0),
      vibes: vibes,
      weather: pick(p.get('weather'), ['cold', 'mild', 'warm', 'any'], DEFAULTS.weather),
      crowd: pick(p.get('crowd'), ['quiet', 'any', 'lively'], DEFAULTS.crowd),
      pace: pick(p.get('pace'), ['easy', 'balanced', 'packed'], DEFAULTS.pace),
      comfort: pick(p.get('comfort'), ['auto', 'budget', 'standard', 'premium'], DEFAULTS.comfort),
      discovery: pick(p.get('discovery'), ['mix', 'hidden', 'famous'], DEFAULTS.discovery),
      notes: (p.get('notes') || '').replace(/[<>]/g, ' ').slice(0, 300),
      include: (p.get('include') || '').toLowerCase().replace(/[^a-z]/g, '').slice(0, 40)
    };
  }

  function toParams(q) {
    var p = new URLSearchParams();
    p.set('origin', q.origin);
    if (q.start) p.set('start', q.start);
    p.set('days', String(q.days));
    p.set('travellers', String(q.travellers));
    p.set('group', q.group);
    p.set('budget', String(q.budget));
    if (q.vibes && q.vibes.length) p.set('vibes', q.vibes.join(','));
    if (q.include) p.set('include', q.include);
    if (q.notes) p.set('notes', q.notes);
    ['weather', 'crowd', 'pace', 'comfort', 'discovery'].forEach(function (k) { if (q[k] && q[k] !== DEFAULTS[k]) p.set(k, q[k]); });
    return p.toString();
  }

  function toApi(q) {
    return {
      origin: q.origin, startDate: q.start || null, days: q.days, travellers: q.travellers, group: q.group,
      budget: q.budget, vibes: q.vibes, weather: q.weather, crowd: q.crowd, pace: q.pace, comfort: q.comfort,
      discovery: q.discovery || 'mix', notes: q.notes || null,
      include: q.include || null
    };
  }

  function formatDate(iso, opts) {
    if (!iso) return '';
    var d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('en-IN', opts || { day: 'numeric', month: 'short', year: 'numeric' });
  }

  function vibeLabel(id) { var v = VIBES.filter(function (x) { return x.id === id; })[0]; return v ? v.label : id; }

  window.TMQuery = {
    VIBES: VIBES, LABELS: LABELS, DEFAULTS: DEFAULTS,
    fromParams: fromParams, toParams: toParams, toApi: toApi, formatDate: formatDate, vibeLabel: vibeLabel
  };
})();
