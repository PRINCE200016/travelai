// ===== TripMind AI - Interactive Constraint Adjustment Chat =====

const API_BASE = window.TRIPMIND_CONFIG?.apiBase || '/api';

const chatFlow = [
  { id: 'originCity', message: "Hey! 👋 I'm your AI travel assistant.\n\nWhere are you traveling from? 📍", type: 'text', field: 'originCity', placeholder: 'Enter city (e.g. Delhi)', validate: v => v.trim().length < 2 ? 'Please enter a valid city.' : null },
  { id: 'travelStartDate', message: "Travel start date? 📅 (YYYY-MM-DD)", type: 'text', field: 'travelStartDate', placeholder: 'YYYY-MM-DD', validate: v => !v.match(/^\d{4}-\d{2}-\d{2}$/) ? 'Use YYYY-MM-DD format.' : null },
  { id: 'budget', message: "Total budget in ₹?", type: 'text', field: 'budget', placeholder: 'e.g. 10000', validate: v => {
    const n = parseInt(v.replace(/[^\d]/g, ''), 10);
    if (!n || n <= 0) return 'Please enter a valid positive budget.';
    if (n < 500) return 'Budget should be at least ₹500.';
    return null;
  }},
  { id: 'duration', message: "Trip duration?", type: 'options', options: ['1-2 days', '3-4 days', '5-7 days', '8-15 days'], field: 'duration' },
  { id: 'travelType', message: "Who are you traveling with? 🧳", type: 'options', options: ['🧑 Solo', '💑 Couple', '👨‍👩‍👧‍👦 Family', '👥 Group (Friends)'], field: 'travelType' },
  { id: 'travelersCount', message: "How many travelers?", type: 'text', field: 'travelersCount', placeholder: 'e.g. 2', validate: v => {
    const n = parseInt(v, 10);
    return isNaN(n) || n <= 0 || n > 50 ? 'Please enter travelers between 1 and 50.' : null;
  }},
  { id: 'stressLevel', message: "How stressed are you feeling? 🧠", type: 'options', options: ['😌 Low — I\'m chill', '😰 Medium — Need a break', '🤯 High — I need to escape!'], field: 'stressLevel' },
  { id: 'mood', message: "What vibe are you looking for? ✨", type: 'options', options: ['🧘 Relax & Peace', '🏔️ Adventure', '🙏 Spiritual', '🎉 Party & Nightlife'], field: 'mood' },
  { id: 'weatherPref', message: "Weather preference? 🌦️", type: 'options', options: ['❄️ Cold', '☀️ Warm', '🌤️ Moderate', '🤷 No Preference'], field: 'weatherPref' },
  { id: 'crowdTolerance', message: "Crowd preference? 👥", type: 'options', options: ['🍃 Low — I want peace', '⚖️ Medium — Doesn\'t matter', '🎊 High — I love the energy'], field: 'crowdTolerance' }
];

let currentStep = 0;
let tripState = {};
let currentUser = null;
let adjustmentMode = false;
let isApiBusy = false;
let sessionId = `sess_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;

document.addEventListener('DOMContentLoaded', () => {
  try { currentUser = JSON.parse(localStorage.getItem('user') || 'null'); } catch (_) { currentUser = null; }

  const moodPreset = new URLSearchParams(window.location.search).get('mood');
  if (moodPreset) tripState.mood = moodPreset;

  trackSession();
  setTimeout(() => askCurrentStep(), 350);
  updateConstraintPreview();

  document.getElementById('chatInput').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') sendMessage();
  });
});

function trackSession() {
  fetch(`${API_BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, device: navigator.userAgent.includes('Mobile') ? 'Mobile' : 'Desktop' })
  }).catch(() => {});
}

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (currentUser?.token) headers.Authorization = `Bearer ${currentUser.token}`;
  return headers;
}

function logChatMessage(sender, message) {
  fetch(`${API_BASE}/chat/message`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ userId: currentUser?.id || null, sessionId, sender, message })
  }).catch(() => {});
}

function showAIMessage(text) {
  const messages = document.getElementById('chatMessages');
  const typing = document.createElement('div');
  typing.className = 'typing-indicator';
  typing.innerHTML = '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';
  messages.appendChild(typing);
  messages.scrollTop = messages.scrollHeight;

  setTimeout(() => {
    typing.remove();
    const msg = document.createElement('div');
    msg.className = 'message ai';
    msg.innerHTML = `<div class="message-bubble">${formatText(text)}</div><div class="message-time">${getTime()}</div>`;
    messages.appendChild(msg);
    messages.scrollTop = messages.scrollHeight;
    logChatMessage('ai', text);
  }, 450);
}

function showUserMessage(text) {
  const messages = document.getElementById('chatMessages');
  const msg = document.createElement('div');
  msg.className = 'message user';
  msg.innerHTML = `<div class="message-bubble">${text}</div><div class="message-time">${getTime()}</div>`;
  messages.appendChild(msg);
  messages.scrollTop = messages.scrollHeight;
  logChatMessage('user', text);
}

function showOptions(options, onClick) {
  const c = document.getElementById('chatOptions');
  c.innerHTML = '';
  options.forEach(label => {
    const b = document.createElement('button');
    b.className = 'chat-option-btn';
    b.textContent = label;
    b.onclick = () => onClick(label);
    c.appendChild(b);
  });
}

function clearOptions() {
  document.getElementById('chatOptions').innerHTML = '';
}

function updatePlaceholder(text) {
  document.getElementById('chatInput').placeholder = text || 'Type your answer...';
}

function askCurrentStep() {
  if (currentStep >= chatFlow.length) return;
  const step = chatFlow[currentStep];
  showAIMessage(step.message);
  if (step.type === 'options') {
    showOptions(step.options, (selected) => {
      showUserMessage(selected);
      processStepAnswer(selected);
    });
  } else {
    updatePlaceholder(step.placeholder);
  }
}

function sendMessage() {
  const input = document.getElementById('chatInput');
  const text = input.value.trim();
  if (!text || isApiBusy) return;
  input.value = '';
  showUserMessage(text);

  if (adjustmentMode) {
    handleAdjustmentInput(text);
    return;
  }

  processStepAnswer(text);
}

function processStepAnswer(answer) {
  const step = chatFlow[currentStep];
  if (!step) return;

  if (step.type === 'text' && step.validate) {
    const err = step.validate(answer);
    if (err) {
      showAIMessage(`⚠️ ${err}`);
      return;
    }
  }

  applyStepToState(step.field, answer);
  updateConstraintPreview();

  currentStep += 1;
  if (currentStep < chatFlow.length && chatFlow[currentStep].field === 'mood' && tripState.mood) {
    currentStep += 1; // mood preset
  }

  if (currentStep < chatFlow.length) {
    clearOptions();
    setTimeout(askCurrentStep, 300);
    return;
  }

  processWithAI('Calculating strict feasible options...');
}

function applyStepToState(field, value) {
  if (field === 'budget') {
    tripState.budget = parseInt(String(value).replace(/[^\d]/g, ''), 10) || 0;
    return;
  }
  if (field === 'duration') {
    tripState.duration = parseDuration(value);
    return;
  }
  if (field === 'travelersCount') {
    tripState.travelersCount = parseInt(value, 10) || 1;
    return;
  }
  tripState[field] = value;
}

async function processWithAI(loadingText = 'Recalculating...') {
  clearOptions();
  setApiBusy(true, loadingText);
  const loadingEl = showRecalculateBubble(loadingText);

  const payload = {
    userId: currentUser?.id || null,
    originCity: cleanText(tripState.originCity),
    budget: Number(tripState.budget || 0),
    duration: Number(tripState.duration || 1),
    travelType: cleanText(tripState.travelType),
    travelersCount: Number(tripState.travelersCount || 1),
    mood: cleanText(tripState.mood),
    weatherPref: cleanText(tripState.weatherPref),
    crowdTolerance: cleanText(tripState.crowdTolerance),
    stressLevel: cleanText(tripState.stressLevel),
    psychProfile: getPsychProfile(),
    travelStartDate: tripState.travelStartDate
  };

  try {
    const res = await fetch(`${API_BASE}/chat`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('API error');
    const data = await res.json();
    loadingEl.remove();
    handleAIResult(data);
  } catch (e) {
    loadingEl.remove();
    showAIMessage("I couldn't reach the server. Please try again.");
  } finally {
    setApiBusy(false);
  }
}

function showRecalculateBubble(text) {
  const messages = document.getElementById('chatMessages');
  const msg = document.createElement('div');
  msg.className = 'message ai';
  msg.innerHTML = `<div class="message-bubble"><div class="analysis-loading"><div class="analysis-spinner"></div><div class="analysis-steps"><div class="analysis-step"><span class="check">⟳</span> ${text}</div></div></div></div>`;
  messages.appendChild(msg);
  messages.scrollTop = messages.scrollHeight;
  return msg;
}

function setApiBusy(busy, placeholder = 'Type your answer...') {
  isApiBusy = busy;
  const input = document.getElementById('chatInput');
  input.disabled = busy;
  updatePlaceholder(busy ? placeholder : (adjustmentMode ? 'Type: "increase budget by 2000" or "add 1 day"' : 'Type your answer...'));
}

function handleAIResult(data) {
  const destinationText = String(data?.destination || '');
  const isFallback = Boolean(data?.restrictive) || data?.status === 'fail' || destinationText.toLowerCase().includes('too restrictive');
  const b = data.costBreakdown || data.budgetEstimate || {};

  if (isFallback) {
    adjustmentMode = true;
    
    if (data.reason === 'budget_unrealistic') {
      showAIMessage(`⚠️ ${data.explanation || data.destination}`);
      renderAdjustmentButtons({ minBudget: 0, minDays: 0, alternatives: [], unrealistic: true });
      updateConstraintPreview();
      return;
    }

    const required = data.required || {};
    const minBudget = Number(required.minBudget || data.minimumRequiredBudgetIncrease || 0);
    const minDays = Number(required.minDays || data.minimumRequiredDaysIncrease || 0);

    const expl = data.explanation || destinationText;
    showAIMessage(expl);

    let recMsg = "";
    if (minBudget > 0) {
      recMsg += `Required budget for ${minDays} day(s) is ~₹${minBudget.toLocaleString('en-IN')}. `;
    }
    if (minDays > Number(tripState.duration || 1)) {
      recMsg += `(Requires minimum ${minDays} days for viable travel)`;
    }
    if (recMsg) showAIMessage(recMsg);
    
    renderAdjustmentButtons({ 
      minBudget, 
      minDays, 
      alternatives: data.alternatives || [], 
      requiredDaysOptions: required.alternative,
      unrealistic: false
    });
    
    updateConstraintPreview();
    return;
  }

  adjustmentMode = false;
  const strictBlock =
    `Destination: ${destinationText}\n\n` +
    `Reason:\n${data.justification || ''}\n\n` +
    `Cost Breakdown:\n` +
    `Travel: ₹${Number(b.travel || 0).toLocaleString('en-IN')}\n` +
    `Food: ₹${Number(b.food || 0).toLocaleString('en-IN')}\n` +
    `Activities: ₹${Number(b.activities || 0).toLocaleString('en-IN')}\n` +
    `Stay: ₹${Number(b.stay || 0).toLocaleString('en-IN')}\n` +
    `Total: ₹${Number(b.total || 0).toLocaleString('en-IN')}`;

  showAIMessage(strictBlock);
  clearOptions();
  updatePlaceholder('Type your answer...');

  sessionStorage.setItem('tripResult', JSON.stringify(data));
  sessionStorage.setItem('sessionId', sessionId);

  setTimeout(() => {
    const messages = document.getElementById('chatMessages');
    const linkEl = document.createElement('div');
    linkEl.className = 'message ai';
    linkEl.innerHTML = `<div class="message-bubble" style="text-align:center;"><a href="result.html" class="btn btn-primary btn-sm" style="margin:8px 0;">📊 View Detailed Results</a><br><a href="chat.html" class="btn btn-secondary btn-sm" style="margin:8px 0;">🔄 Try Again</a></div>`;
    messages.appendChild(linkEl);
    messages.scrollTop = messages.scrollHeight;
  }, 800);
}

function renderAdjustmentButtons({ minBudget = 0, minDays = 0, alternatives = [], requiredDaysOptions = null, unrealistic = false }) {
  const actions = [];

  if (unrealistic) {
    actions.push({ label: 'Restart Chat', handler: () => window.location.reload() });
  } else {
    if (minBudget > 0) {
      actions.push({
        label: `✅ Apply Recommended (₹${minBudget.toLocaleString('en-IN')})`,
        handler: () => adjustState({ budgetAbs: minBudget, daysAbs: minDays })
      });
    }
    
    if (requiredDaysOptions && requiredDaysOptions.reduceDaysToFitBudget) {
      actions.push({
        label: `🔽 Reduce Trip to ${requiredDaysOptions.reduceDaysToFitBudget} Day(s)`,
        handler: () => adjustState({ daysAbs: requiredDaysOptions.reduceDaysToFitBudget })
      });
    }

    if (alternatives && alternatives.length > 0) {
      actions.push({
        label: '🗺️ Show Cheaper Destinations',
        handler: () => {
          let msg = "Here are some cheaper alternatives based on your current inputs:\n";
          alternatives.forEach(alt => {
             msg += `• **${alt.destination}** (Approx. ₹${(alt.estimatedCost || 0).toLocaleString('en-IN')})\n`;
          });
          showAIMessage(msg);
          // Re-render without the alternatives button to avoid clutter
          renderAdjustmentButtons({ minBudget, minDays, alternatives: [], requiredDaysOptions, unrealistic });
        }
      });
    }
    
    actions.push({ label: '+ Increase Budget by ₹5000', handler: () => adjustState({ budgetDelta: 5000 }) });
    actions.push({ label: 'Custom Input', handler: () => showAIMessage('Type your update, e.g. "make budget 15000" or "reduce to 2 days".') });
  }

  const c = document.getElementById('chatOptions');
  c.innerHTML = '';
  actions.forEach(a => {
    const b = document.createElement('button');
    b.className = 'chat-option-btn';
    b.textContent = a.label;
    b.onclick = a.handler;
    c.appendChild(b);
  });
}

function adjustState({ budgetDelta = 0, daysDelta = 0, budgetAbs = null, daysAbs = null }) {
  const currentBudget = Number(tripState.budget || 0);
  const currentDays = Number(tripState.duration || 1);

  let nextBudget = budgetAbs != null ? budgetAbs : currentBudget + budgetDelta;
  let nextDays = daysAbs != null ? daysAbs : currentDays + daysDelta;

  nextBudget = Math.max(500, nextBudget);
  nextDays = Math.max(1, nextDays);

  tripState.budget = nextBudget;
  tripState.duration = nextDays;
  updateConstraintPreview();

  showUserMessage(`Updated constraints -> Budget: ₹${nextBudget.toLocaleString('en-IN')}, Duration: ${nextDays} day(s)`);
  processWithAI('Recalculating with updated constraints...');
}

function handleAdjustmentInput(text) {
  const parsed = parseAdjustmentInput(text);
  if (!parsed) {
    showAIMessage("I didn’t understand that. Try 'increase budget by 2000' or use the buttons.");
    renderAdjustmentButtons({ minBudget: 0, minDays: 0 });
    return;
  }
  adjustState(parsed);
}

// Basic NLP parser (regex + keywords)
function parseAdjustmentInput(input) {
  const t = input.toLowerCase().trim();
  const num = (s) => parseInt((s || '').replace(/[^\d]/g, ''), 10);

  let budgetAbs = null;
  let budgetDelta = 0;
  let daysAbs = null;
  let daysDelta = 0;

  // make budget 15000 / increase budget to 10000
  let m = t.match(/(?:make|set|increase)\s+budget(?:\s+to)?\s+(\d{3,7})/);
  if (m) budgetAbs = num(m[1]);

  // increase budget by 2000 / add 2000 budget
  m = t.match(/(?:increase|add)\s+(?:budget\s+)?(?:by\s+)?(\d{3,7})/);
  if (m && budgetAbs == null) budgetDelta = num(m[1]);

  // add 2 days / add 1 more day
  m = t.match(/add\s+(\d+)\s*(?:more\s*)?day/);
  if (m) daysDelta = num(m[1]);

  // ". make 3 days / set duration to 3 / reduce to 2 days"
  m = t.match(/(?:make|set|reduce)\s+(?:duration\s*(?:to)?\s*|to\s+)?(\d+)\s*day/);
  if (m) daysAbs = num(m[1]);

  // "... and 3 days"
  m = t.match(/and\s+(\d+)\s*day/);
  if (m && daysAbs == null && daysDelta === 0) daysAbs = num(m[1]);

  if (budgetAbs == null && budgetDelta === 0 && daysAbs == null && daysDelta === 0) return null;
  if ((budgetAbs != null && budgetAbs <= 0) || budgetDelta < 0 || (daysAbs != null && daysAbs <= 0) || daysDelta < 0) return null;

  return { budgetAbs, budgetDelta, daysAbs, daysDelta };
}

function distanceLimitFromDuration(duration) {
  const d = Number(duration || 1);
  if (d <= 1) return 150;
  if (d <= 3) return 400;
  return Infinity;
}

function updateConstraintPreview() {
  const el = document.getElementById('constraintPreview');
  if (!el) return;
  const budget = Number(tripState.budget || 0);
  const duration = Number(tripState.duration || 0);
  const source = tripState.originCity || '--';
  const type = cleanText(tripState.travelType || '--') || '--';
  const limit = distanceLimitFromDuration(duration);
  const travelCap = budget > 0 ? Math.floor(budget * 0.4) : 0;
  const hasAny = source !== '--' || budget > 0 || duration > 0;
  el.style.display = hasAny ? 'block' : 'none';

  el.innerHTML = `
    <div class="strict-title"><div style="font-weight:800;">Constraint Preview</div><span class="strict-pill">${adjustmentMode ? 'Adjustment Mode' : 'Pre-check'}</span></div>
    <div style="font-size:0.9rem; color:var(--text-secondary); line-height:1.6;">
      Source: <strong>${source}</strong> | Type: <strong>${type}</strong><br>
      Budget: <strong>₹${budget ? budget.toLocaleString('en-IN') : '--'}</strong> | Duration: <strong>${duration || '--'} day(s)</strong><br>
      Distance rule: <strong>${Number.isFinite(limit) ? `${limit} km max` : 'No hard distance limit'}</strong><br>
      Travel-cost hard cap (40%): <strong>₹${travelCap ? travelCap.toLocaleString('en-IN') : '--'}</strong>
    </div>
  `;
}

function getPsychProfile() {
  const stress = (tripState.stressLevel || '').toLowerCase();
  const mood = (tripState.mood || '').toLowerCase();
  if (stress.includes('high') || stress.includes('escape')) {
    if (mood.includes('spirit')) return 'deeply_stressed_spiritual';
    if (mood.includes('adventure')) return 'stressed_seeking_thrill';
    return 'burnout_needs_isolation';
  }
  if (stress.includes('medium') || stress.includes('break')) {
    if (mood.includes('peace') || mood.includes('relax')) return 'moderate_stress_needs_calm';
    return 'moderate_stress_needs_change';
  }
  if (mood.includes('party')) return 'social_energetic';
  if (mood.includes('adventure')) return 'thrill_seeker';
  if (mood.includes('spirit')) return 'spiritual_explorer';
  return 'relaxed_explorer';
}

function parseDuration(text) {
  if (!text) return 3;
  const t = String(text);
  if (t.includes('1-2')) return 2;
  if (t.includes('3-4')) return 4;
  if (t.includes('5-7')) return 6;
  if (t.includes('8-15')) return 10;
  return parseInt(t, 10) || 3;
}

function cleanText(text) {
  if (!text) return '';
  return String(text).replace(/[🧑💑👨‍👩‍👧‍👦👥🧘🏔️🙏🎉❄️☀️🌤️🤷🍃⚖️🎊😌😰🤯]/g, '').trim();
}

function formatText(text) {
  return String(text)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>');
}

function getTime() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
