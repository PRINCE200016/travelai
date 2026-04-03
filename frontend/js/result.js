// ===== TripMind AI - Result Page JS (SaaS Upgrade) =====

const API_BASE = window.TRIPMIND_CONFIG?.apiBase || '/api';
const currentUser = (() => {
  try { return JSON.parse(localStorage.getItem('user') || 'null'); } catch (_) { return null; }
})();

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (currentUser?.token) headers.Authorization = `Bearer ${currentUser.token}`;
  return headers;
}

document.addEventListener('DOMContentLoaded', () => {
  const resultData = sessionStorage.getItem('tripResult');
  
  if (resultData) {
    const data = JSON.parse(resultData);
    renderResult(data);
  } else {
    renderResult(getDemoData());
  }

  // Save lead data
  saveLead();

  // Show lead capture popup after 5 seconds
  setTimeout(() => {
    const dismissed = sessionStorage.getItem('leadPopupDismissed');
    if (!dismissed) {
      document.getElementById('leadPopup').classList.add('active');
    }
  }, 5000);

  // Track page view
  if (typeof gtag !== 'undefined') {
    const data = JSON.parse(sessionStorage.getItem('tripResult') || '{}');
    gtag('event', 'result_page_viewed', { destination: data.destination || 'unknown' });
  }
});

function renderResult(data) {
  // Strict fallback message from backend
  const isFail = data?.status === 'fail';
  const fallbackText = isFail || (data?.destination || '').toLowerCase().includes('too restrictive')
    ? data.destination
    : null;

  // If fallback: show only strict message and hide most UI sections
  if (fallbackText) {
    document.getElementById('resultDestination').textContent = 'Trip Requires Adjustment';
    document.getElementById('resultDescription').textContent = data.explanation || fallbackText;
    document.getElementById('resultJustification').textContent = data.explanation || fallbackText;

    const strictSection = document.getElementById('strictSection');
    if (strictSection) {
      strictSection.style.display = 'block';
      document.getElementById('strictDestination').textContent = 'Destination: (none)';
      document.getElementById('strictReason').textContent = fallbackText;
      document.getElementById('strictTravel').textContent = '₹0';
      document.getElementById('strictFood').textContent = '₹0';
      document.getElementById('strictActivities').textContent = '₹0';
      document.getElementById('strictStay').textContent = '₹0';
      document.getElementById('strictTotal').textContent = '₹0';
    }

    // Hide irrelevant sections
    ['weatherTemp','weatherDesc','weatherForecast','activitiesList','crowdBadge','crowdReason','budgetTravel','budgetStay','budgetFood','budgetTotal']
      .forEach(id => { const el = document.getElementById(id); if (el) el.closest?.('.result-section')?.style && (el.closest('.result-section').style.display = 'none'); });

    // CTA buttons still available, but itinerary CTA isn't meaningful
    const itBtn = document.getElementById('btnItinerary');
    if (itBtn) {
      itBtn.textContent = 'Adjust inputs in Chat';
      itBtn.href = 'chat.html';
    }
    return;
  }

  // Destination
  document.getElementById('resultDestination').textContent = data.destination || 'Unknown';
  if (data.description && data.description.includes('⚠️')) {
    document.getElementById('resultDescription').innerHTML = `<span style="color:#d97706; font-weight:bold;">${data.description}</span>`;
  } else {
    document.getElementById('resultDescription').textContent = data.description || 'A fantastic travel destination recommended by our AI.';
  }
  
  // Image
  if (data.imageUrl) {
    document.getElementById('resultImage').src = data.imageUrl;
  }

  // AI Justification
  document.getElementById('resultJustification').textContent = data.justification || 'Based on your preferences, this is the best match for your trip.';

  // Strict Output block (matches required format)
  const strictSection = document.getElementById('strictSection');
  const b = data.costBreakdown || data.budgetEstimate || null;
  if (strictSection && b) {
    strictSection.style.display = 'block';
    document.getElementById('strictDestination').textContent = `Destination: ${data.destination || 'Unknown'}`;
    document.getElementById('strictReason').textContent = `Reason: ${data.justification || 'Selected because it satisfies hard constraints.'}`;
    document.getElementById('strictTravel').textContent = '₹' + formatNum(b.travel || 0);
    document.getElementById('strictFood').textContent = '₹' + formatNum(b.food || 0);
    document.getElementById('strictActivities').textContent = '₹' + formatNum(b.activities || 0);
    document.getElementById('strictStay').textContent = '₹' + formatNum(b.stay || 0);
    document.getElementById('strictTotal').textContent = '₹' + formatNum(b.total || 0);
  }

  // Weather
  if (data.weatherData) {
    document.getElementById('weatherTemp').textContent = data.weatherData.temp + '°C';
    document.getElementById('weatherDesc').textContent = data.weatherData.desc || data.weather;
    
    const forecastEl = document.getElementById('weatherForecast');
    forecastEl.innerHTML = '';
    if (data.weatherData.forecast) {
      data.weatherData.forecast.forEach(day => {
        forecastEl.innerHTML += `
          <div class="forecast-day">
            <div class="day">${day.day}</div>
            <div class="icon">${day.icon}</div>
            <div class="temp">${day.temp}°C</div>
          </div>
        `;
      });
    }
  } else {
    document.getElementById('weatherTemp').textContent = data.weather || '--°C';
    document.getElementById('weatherDesc').textContent = 'Weather data from AI';
  }

  // Crowd Level
  const crowdLevel = (data.crowd || 'medium').toLowerCase();
  document.getElementById('crowdBadge').innerHTML = `<span class="crowd-badge ${crowdLevel}">● ${capitalize(crowdLevel)}</span>`;
  document.getElementById('crowdReason').textContent = data.crowdReason || 'Based on current season and day of week.';

  // Budget
  if (data.budgetEstimate) {
    document.getElementById('budgetTravel').textContent = '₹' + formatNum(data.budgetEstimate.travel);
    document.getElementById('budgetStay').textContent = '₹' + formatNum(data.budgetEstimate.stay);
    document.getElementById('budgetFood').textContent = '₹' + formatNum(data.budgetEstimate.food);
    document.getElementById('budgetTotal').textContent = '₹' + (data.budgetEstimate.total || 
      formatNum(data.budgetEstimate.travel + data.budgetEstimate.stay + data.budgetEstimate.food));
  }

  // Activities
  const activitiesEl = document.getElementById('activitiesList');
  activitiesEl.innerHTML = '';
  (data.activities || []).forEach(act => {
    activitiesEl.innerHTML += `<div class="activity-tag">${act}</div>`;
  });

  // Update affiliate links
  const dest = data.destination ? data.destination.split(',')[0].trim() : 'India';
  const encDest = encodeURIComponent(dest);
  
  let originStr = '';
  if (data.budgetEstimate && data.budgetEstimate.originCity) {
      originStr = encodeURIComponent(data.budgetEstimate.originCity);
  } else {
      originStr = 'delhi'; // fallback
  }

  document.getElementById('btnHotel').href = `https://www.booking.com/searchresults.html?ss=${encDest}`;
  document.getElementById('btnFlights').href = `https://www.skyscanner.co.in/transport/flights/${originStr}/${encDest}/`;

  // Track CTA clicks
  document.getElementById('btnHotel').addEventListener('click', () => {
    trackEvent('cta_hotel_clicked', data.destination);
  });
  document.getElementById('btnFlights').addEventListener('click', () => {
    trackEvent('cta_flights_clicked', data.destination);
  });
  document.getElementById('btnItinerary').addEventListener('click', () => {
    trackEvent('cta_itinerary_clicked', data.destination);
    // Track itinerary purchase intent
    fetch(`${API_BASE}/payment`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        userId: currentUser?.id || null,
        requestType: 'itinerary',
        paymentStatus: 'pending',
        amount: 199
      })
    }).catch(() => {});
  });

  // Animate sections in
  document.querySelectorAll('.result-section').forEach((el, i) => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(20px)';
    el.style.transition = `opacity 0.5s ease ${i * 0.1}s, transform 0.5s ease ${i * 0.1}s`;
    setTimeout(() => {
      el.style.opacity = '1';
      el.style.transform = 'translateY(0)';
    }, 100);
  });
}

// Lead capture
function submitLead(e) {
  e.preventDefault();
  const lead = {
    name: document.getElementById('leadName').value.trim(),
    phone: document.getElementById('leadPhone').value.trim(),
    budget: '',
    destinationInterest: ''
  };

  const resultData = sessionStorage.getItem('tripResult');
  if (resultData) {
    const data = JSON.parse(resultData);
    lead.budget = data.budgetEstimate?.total || data.budget || '';
    lead.destinationInterest = data.destination || '';
  }

  // Save via API
  fetch(`${API_BASE}/leads`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(lead)
  }).catch(() => {
    showToast('Failed to save your details. Please retry.', 'error');
  });

  closeLeadPopup();
  showToast('🎉 Thanks! We\'ll send you exclusive deals!', 'success');
  trackEvent('lead_captured', lead.destinationInterest);
}

function closeLeadPopup() {
  document.getElementById('leadPopup').classList.remove('active');
  sessionStorage.setItem('leadPopupDismissed', 'true');
}

// Save auto lead (without popup)
function saveLead() {
  const resultData = sessionStorage.getItem('tripResult');
  if (!resultData) return;
  
  const data = JSON.parse(resultData);
  const lead = {
    budget: data.budgetEstimate?.total || data.budget || 'unknown',
    destinationInterest: data.destination
  };

  fetch(`${API_BASE}/leads`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(lead)
  }).catch(() => {});
}

// Save Trip
function saveTrip() {
  const resultData = sessionStorage.getItem('tripResult');
  if (!resultData) {
    showToast('No trip data to save!', 'error');
    return;
  }

  const saved = JSON.parse(localStorage.getItem('tripmind_saved') || '[]');
  const data = JSON.parse(resultData);
  data.savedAt = new Date().toISOString();
  saved.push(data);
  localStorage.setItem('tripmind_saved', JSON.stringify(saved));
  
  showToast('✅ Trip saved successfully!', 'success');
  trackEvent('trip_saved', data.destination);
}

// Share functionality
function openShareModal() {
  document.getElementById('shareModal').classList.add('active');
  trackEvent('share_modal_opened');
}

function closeShareModal() {
  document.getElementById('shareModal').classList.remove('active');
}

function shareWhatsApp() {
  const data = JSON.parse(sessionStorage.getItem('tripResult') || '{}');
  const text = `🎯 TripMind AI recommended *${data.destination || 'an amazing destination'}* for my trip!\n\n🌦️ Weather: ${data.weather || 'Great'}\n👥 Crowd: ${data.crowd || 'Manageable'}\n💰 Budget: ₹${data.budgetEstimate?.total || '--'}\n\nTry it yourself: ${window.location.origin}/chat.html`;
  window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank');
  trackEvent('shared_whatsapp', data.destination);
  closeShareModal();
}

function shareTwitter() {
  const data = JSON.parse(sessionStorage.getItem('tripResult') || '{}');
  const text = `🎯 TripMind AI suggests ${data.destination || 'a great destination'} for my trip! 🌦️${data.weather || ''} 💰₹${data.budgetEstimate?.total || '--'} Try it: ${window.location.origin}/chat.html`;
  window.open(`https://twitter.com/intent/tweet?text=${encodeURIComponent(text)}`, '_blank');
  trackEvent('shared_twitter', data.destination);
  closeShareModal();
}

function copyShareLink() {
  const data = JSON.parse(sessionStorage.getItem('tripResult') || '{}');
  const text = `Check out ${data.destination || 'this destination'} recommended by TripMind AI! ${window.location.origin}/chat.html`;
  navigator.clipboard.writeText(text).then(() => {
    showToast('📋 Link copied to clipboard!', 'success');
    trackEvent('shared_copy_link', data.destination);
  }).catch(() => {
    showToast('Failed to copy', 'error');
  });
  closeShareModal();
}

// Analytics tracking
function trackEvent(eventName, label) {
  if (typeof gtag !== 'undefined') {
    gtag('event', eventName, { event_label: label });
  }
}

// Toast notification
function showToast(message, type) {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 3000);
}

function getDemoData() {
  return {
    destination: 'Manali, Himachal Pradesh',
    description: 'Snow-capped mountains, thrilling adventure sports, and stunning Himalayan landscapes await.',
    imageUrl: 'https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?w=1200&h=600&fit=crop',
    weather: 'Cold — 8°C',
    weatherData: { temp: 8, desc: 'Cold & Clear', forecast: [
      { day: 'Mon', temp: 8, icon: '❄️' },
      { day: 'Tue', temp: 6, icon: '🌨️' },
      { day: 'Wed', temp: 10, icon: '☀️' },
      { day: 'Thu', temp: 7, icon: '❄️' },
      { day: 'Fri', temp: 9, icon: '🌤️' }
    ]},
    crowd: 'Medium',
    crowdReason: 'Popular season but manageable crowd on weekdays',
    budgetEstimate: { travel: 4000, stay: 5000, food: 2500, total: '11,500' },
    activities: ['🪂 Paragliding', '🏔️ Rohtang Pass', '🏂 Snow Sports', '🌊 River Rafting', '🛕 Hadimba Temple'],
    justification: 'We recommend Manali because it\'s the ultimate adventure destination. The cold weather at 8°C is perfect for snow activities, crowd is moderate, and the range of adventure sports matches your adventurous spirit.'
  };
}

function capitalize(str) { return str.charAt(0).toUpperCase() + str.slice(1); }
function formatNum(n) { return Number(n).toLocaleString('en-IN'); }
