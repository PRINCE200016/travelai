// ===== TripMind AI - Itinerary Form JS (EmailJS + WhatsApp) =====

const API_BASE = window.TRIPMIND_CONFIG?.apiBase || '/api';
const ADMIN_WHATSAPP = '917509245769';

// EmailJS Configuration — UPDATE THESE IF NEEDED
const EMAILJS_SERVICE_ID = 'service_ure4hqw';
const EMAILJS_TEMPLATE_ID = 'template_ngvq9hn';
const EMAILJS_PUBLIC_KEY = '0oMOWCSvskq3UgmdP';

const getStoredUser = () => {
  try { return JSON.parse(localStorage.getItem('user') || 'null'); } catch (_) { return null; }
};

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  const user = getStoredUser();
  if (user?.token) headers.Authorization = `Bearer ${user.token}`;
  return headers;
}

async function submitItinerary(e) {
  e.preventDefault();
  console.log('📋 Itinerary form submit triggered');

  const btn = document.getElementById('submitBtn');
  btn.disabled = true;
  btn.textContent = '⏳ Sending...';

  const data = {
    name: document.getElementById('formName').value.trim(),
    email: document.getElementById('formEmail').value.trim(),
    phone: document.getElementById('formPhone')?.value.trim() || '',
    destination: document.getElementById('formDestination')?.value.trim() || '',
    budget: parseInt(document.getElementById('formBudget').value),
    days: parseInt(document.getElementById('formDays').value),
    travelers: parseInt(document.getElementById('formTravelers')?.value || '1'),
    travelStartDate: document.getElementById('formTravelDate')?.value || '',
    preferences: document.getElementById('formPreferences').value,
    specialRequest: document.getElementById('formSpecialRequest').value.trim()
  };

  // ===== Validation =====
  if (!data.name || !data.email || !data.budget || !data.days) {
    showToast('Please fill all required fields', 'error');
    btn.disabled = false;
    btn.textContent = '📋 Submit Request — ₹99';
    return;
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
    showToast('Please enter a valid email address', 'error');
    btn.disabled = false;
    btn.textContent = '📋 Submit Request — ₹99';
    return;
  }
  if (data.travelStartDate && new Date(data.travelStartDate) < new Date(new Date().setHours(0, 0, 0, 0))) {
    showToast('Travel date cannot be in the past', 'error');
    btn.disabled = false;
    btn.textContent = '📋 Submit Request — ₹99';
    return;
  }

  // ===== EmailJS — Primary Send (MUST succeed) =====
  const emailParams = {
    name: data.name,
    email: data.email,
    phone: data.phone || 'Not provided',
    destination: data.destination || 'Not specified',
    travel_date: data.travelStartDate || 'Not specified',
    budget: `₹${data.budget?.toLocaleString() || '0'}`,
    travelers: data.travelers || 1,
    message: `Destination: ${data.destination || 'Not specified'}\nDays: ${data.days}\nPreferences: ${data.preferences || 'None'}\nSpecial Request: ${data.specialRequest || 'None'}`,
    reply_to: data.email
  };

  try {
    console.log('📧 Sending EmailJS with params:', emailParams);
    console.log('📧 Service ID:', EMAILJS_SERVICE_ID);
    console.log('📧 Template ID:', EMAILJS_TEMPLATE_ID);
    console.log('📧 Public Key:', EMAILJS_PUBLIC_KEY);

    const emailResult = await emailjs.send(
      EMAILJS_SERVICE_ID,
      EMAILJS_TEMPLATE_ID,
      emailParams,
      EMAILJS_PUBLIC_KEY
    );

    console.log('✅ EmailJS SUCCESS:', emailResult.status, emailResult.text);
  } catch (emailErr) {
    console.error('❌ EmailJS FAILED — Full error object:', JSON.stringify(emailErr));
    console.error('❌ Error text:', emailErr?.text);
    console.error('❌ Error message:', emailErr?.message);
    console.error('❌ Error status:', emailErr?.status);
    showToast('Failed to send email: ' + (emailErr?.text || emailErr?.message || JSON.stringify(emailErr)), 'error');
    btn.disabled = false;
    btn.textContent = '📋 Submit Request — ₹99';
    return; // Stop here — email is the critical path
  }

  // ===== Backend API — Silent best-effort (only runs if backend is available) =====
  try {
    const user = getStoredUser();
    const paymentRes = await fetch(`${API_BASE}/payment`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        userId: user?.id || null,
        requestType: 'itinerary',
        paymentStatus: 'pending',
        amount: 99
      })
    });
    if (paymentRes.ok) {
      await fetch(`${API_BASE}/itinerary-request`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(data)
      });
    }
  } catch (_) {
    // Backend not available — silently ignored
  }

  // ===== WhatsApp Notification =====
  triggerWhatsApp(data);

  // ===== Show Success =====
  showSuccess();
  btn.disabled = false;
  btn.textContent = '📋 Submit Request — ₹99';
}

function triggerWhatsApp(data) {
  const message = `New Travel Query 🚀

Name: ${data.name}
Destination: ${data.destination || 'Not specified'}
Travel Date: ${data.travelStartDate || 'Not specified'}
Budget: ₹${data.budget?.toLocaleString() || '--'}
Travelers: ${data.travelers || 1}
Email: ${data.email}
Phone: ${data.phone || 'Not provided'}`;

  const encoded = encodeURIComponent(message);
  const whatsappUrl = `https://wa.me/${ADMIN_WHATSAPP}?text=${encoded}`;
  window.open(whatsappUrl, '_blank');
}

function showSuccess() {
  document.getElementById('itineraryForm').style.display = 'none';
  const successEl = document.getElementById('formSuccess');
  successEl.style.display = 'block';
  showToast('✅ Request submitted successfully!', 'success');
}

function showToast(message, type) {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  document.body.appendChild(toast);

  setTimeout(() => toast.remove(), 3000);
}
