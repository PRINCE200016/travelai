// ===== TripMind AI - Admin Panel JS (SaaS Upgrade) =====

const API_BASE = window.TRIPMIND_CONFIG?.apiBase || '/api';
let adminCredentials = null;

// ===== Admin Login =====
function adminLogin(e) {
  e.preventDefault();
  const username = document.getElementById('loginUser').value.trim();
  const password = document.getElementById('loginPass').value.trim();

  adminCredentials = btoa(`${username}:${password}`);

  // Try to validate against backend
  fetch(`${API_BASE}/admin/stats`, {
    headers: { 'Authorization': `Basic ${adminCredentials}` }
  }).then(res => {
    if (res.ok) {
      sessionStorage.setItem('adminAuth', adminCredentials);
      showAdminPanel();
    } else {
      throw new Error('Invalid credentials');
    }
  }).catch(() => {
    document.getElementById('loginError').textContent = '❌ Backend not reachable or invalid credentials';
    document.getElementById('loginError').style.display = 'block';
    adminCredentials = null;
  });
}

function showAdminPanel() {
  document.getElementById('loginModal').classList.remove('active');
  document.getElementById('adminPanel').style.display = 'flex';
  loadAllData();
}

function adminLogout() {
  sessionStorage.removeItem('adminAuth');
  adminCredentials = null;
  location.reload();
}

// Check if already logged in
document.addEventListener('DOMContentLoaded', () => {
  const saved = sessionStorage.getItem('adminAuth');
  if (saved) {
    adminCredentials = saved;
    showAdminPanel();
  }
  document.getElementById('adminDate').textContent = new Date().toLocaleDateString('en-IN', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
  });
});

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (adminCredentials) headers['Authorization'] = `Basic ${adminCredentials}`;
  return headers;
}

// ===== Tab Navigation =====
function switchTab(tabName) {
  document.querySelectorAll('.admin-tab-content').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.admin-nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById(`tab-${tabName}`).classList.add('active');
  document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

  if (tabName === 'analytics') loadAnalytics();
  if (tabName === 'contacts') loadContacts();
  if (tabName === 'users') loadUsers();
  if (tabName === 'chats') loadChatMessages();
  if (tabName === 'payments') loadPayments();
}

// ===== Load All Data =====
function loadAllData() {
  loadOverview();
  loadRequests();
  loadPackages();
  loadLeads();
  loadContacts();
  loadUsers();
  loadChatMessages();
  loadPayments();
}

// ===== Overview Stats =====
function loadOverview() {
  fetch(`${API_BASE}/admin/analytics`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      document.getElementById('statUsers').textContent = (data.totalSessions || 0).toLocaleString();
      document.getElementById('statRequests').textContent = (data.totalRequests || 0).toLocaleString();
      document.getElementById('statRevenue').textContent = '₹' + (data.estimatedRevenue || 0).toLocaleString();
      document.getElementById('statLeads').textContent = (data.totalLeads || 0).toLocaleString();
    });
}

// ===== Analytics Tab =====
function loadAnalytics() {
  fetch(`${API_BASE}/admin/analytics`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      document.getElementById('analyticsChats').textContent = (data.totalChats || 0).toLocaleString();
      document.getElementById('analyticsConversion').textContent = (data.conversionRate || 0) + '%';
      document.getElementById('analyticsSessions').textContent = (data.totalSessions || 0).toLocaleString();
      document.getElementById('analyticsRevenue').textContent = '₹' + (data.estimatedRevenue || 0).toLocaleString();

      const destContainer = document.getElementById('topDestinations');
      if (data.topDestinations && data.topDestinations.length > 0) {
        destContainer.innerHTML = data.topDestinations.map((d, i) => `
          <div class="analytics-dest-row">
            <span class="rank">#${i + 1}</span>
            <span class="dest-name">${d.destination}</span>
            <span class="dest-count">${d.count} searches</span>
            <div class="dest-bar"><div class="dest-bar-fill" style="width:${Math.min(100, d.count * 10)}%"></div></div>
          </div>
        `).join('');
      } else {
        destContainer.innerHTML = getDemoDestinations();
      }
    });
}

function getDemoDestinations() {
  const dests = [
    { name: 'Manali, Himachal Pradesh', count: 142 },
    { name: 'Goa', count: 128 },
    { name: 'Munnar, Kerala', count: 87 },
    { name: 'Varanasi, UP', count: 64 },
    { name: 'Jaipur, Rajasthan', count: 51 }
  ];
  return dests.map((d, i) => `
    <div class="analytics-dest-row">
      <span class="rank">#${i + 1}</span>
      <span class="dest-name">${d.name}</span>
      <span class="dest-count">${d.count} searches</span>
      <div class="dest-bar"><div class="dest-bar-fill" style="width:${Math.min(100, d.count / 1.5)}%"></div></div>
    </div>
  `).join('');
}

// ===== Itinerary Requests =====
function loadRequests() {
  fetch(`${API_BASE}/itinerary-requests`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => renderRequests(data));
}

function renderRequests(data) {
  const table = document.getElementById('allRequestsTable');
  const recent = document.getElementById('recentRequestsTable');
  document.getElementById('requestCount').textContent = data.length + ' requests';

  const rows = data.map(r => `
    <tr>
      <td>${r.id || '--'}</td>
      <td>${r.name || 'N/A'}</td>
      <td>${r.email || 'N/A'}</td>
      <td>₹${r.budget ? r.budget.toLocaleString() : '--'}</td>
      <td>${r.days || '--'}</td>
      <td>${r.preferences || 'N/A'}</td>
      <td>
        <span class="status-badge ${r.status}" onclick="toggleStatus(${r.id}, '${r.status}')">
          ● ${capitalize(r.status || 'pending')}
        </span>
      </td>
      <td><button class="btn btn-secondary btn-sm" onclick="deleteRequest(${r.id})">🗑️</button></td>
    </tr>
  `).join('');

  table.innerHTML = rows || '<tr><td colspan="8" style="text-align:center;color:var(--text-muted);">No requests yet</td></tr>';

  // Recent (last 5)
  recent.innerHTML = data.slice(0, 5).map(r => `
    <tr>
      <td>${r.name || 'N/A'}</td>
      <td>₹${r.budget ? r.budget.toLocaleString() : '--'}</td>
      <td>${r.days || '--'}</td>
      <td>${r.preferences || 'N/A'}</td>
      <td><span class="status-badge ${r.status}">● ${capitalize(r.status || 'pending')}</span></td>
      <td>${formatDate(r.createdAt)}</td>
    </tr>
  `).join('');
}

function toggleStatus(id, currentStatus) {
  const newStatus = currentStatus === 'pending' ? 'completed' : 'pending';
  fetch(`${API_BASE}/itinerary-request/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status: newStatus })
  }).then(() => loadRequests())
    .catch(() => loadRequests());
}

function deleteRequest(id) {
  if (!confirm('Delete this request?')) return;
  fetch(`${API_BASE}/itinerary-request/${id}`, { method: 'DELETE', headers: authHeaders() })
    .then(() => loadRequests())
    .catch(() => loadRequests());
}

// ===== Packages =====
function loadPackages() {
  fetch(`${API_BASE}/packages`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => renderPackages(data));
}

function renderPackages(data) {
  const table = document.getElementById('packagesTable');
  table.innerHTML = data.map(p => `
    <tr>
      <td>${p.id || '--'}</td>
      <td>${p.title}</td>
      <td>₹${p.price ? p.price.toLocaleString() : '--'}</td>
      <td>
        <span class="featured-badge ${p.featured ? 'active' : ''}" onclick="toggleFeatured(${p.id}, ${!p.featured})">
          ${p.featured ? '⭐ Featured' : '☆ Regular'}
        </span>
      </td>
      <td>${(p.description || '').substring(0, 50)}${p.description && p.description.length > 50 ? '...' : ''}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="editPackage(${p.id})">✏️</button>
        <button class="btn btn-secondary btn-sm" onclick="deletePackage(${p.id})">🗑️</button>
      </td>
    </tr>
  `).join('');
}

function toggleFeatured(id, featured) {
  fetch(`${API_BASE}/packages/${id}`, { headers: authHeaders() })
    .then(r => r.json())
    .then(pkg => {
      pkg.featured = featured;
      return fetch(`${API_BASE}/packages/${id}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify(pkg)
      });
    })
    .then(() => loadPackages())
    .catch(() => loadPackages());
}

function openPackageModal(editData) {
  document.getElementById('packageModal').classList.add('active');
  document.getElementById('packageModalTitle').textContent = editData ? 'Edit Package' : 'Add Package';
  if (editData) {
    document.getElementById('packageEditId').value = editData.id;
    document.getElementById('pkgTitle').value = editData.title;
    document.getElementById('pkgPrice').value = editData.price;
    document.getElementById('pkgImage').value = editData.imageUrl || '';
    document.getElementById('pkgDesc').value = editData.description || '';
    document.getElementById('pkgFeatured').checked = editData.featured || false;
  } else {
    document.getElementById('packageForm').reset();
    document.getElementById('packageEditId').value = '';
  }
}

function closePackageModal() {
  document.getElementById('packageModal').classList.remove('active');
}

function savePackage(e) {
  e.preventDefault();
  const id = document.getElementById('packageEditId').value;
  const pkg = {
    title: document.getElementById('pkgTitle').value,
    price: parseFloat(document.getElementById('pkgPrice').value),
    imageUrl: document.getElementById('pkgImage').value,
    description: document.getElementById('pkgDesc').value,
    featured: document.getElementById('pkgFeatured').checked
  };

  const url = id ? `${API_BASE}/packages/${id}` : `${API_BASE}/packages`;
  const method = id ? 'PUT' : 'POST';

  fetch(url, { method, headers: authHeaders(), body: JSON.stringify(pkg) })
    .then(() => {
      closePackageModal();
      loadPackages();
    })
    .catch(() => {
      closePackageModal();
      loadPackages();
    });
}

function editPackage(id) {
  fetch(`${API_BASE}/packages/${id}`, { headers: authHeaders() })
    .then(r => r.json())
    .then(pkg => openPackageModal(pkg))
    .catch(() => {});
}

function deletePackage(id) {
  if (!confirm('Delete this package?')) return;
  fetch(`${API_BASE}/packages/${id}`, { method: 'DELETE', headers: authHeaders() })
    .then(() => loadPackages())
    .catch(() => loadPackages());
}

// ===== Leads =====
function loadLeads() {
  fetch(`${API_BASE}/leads`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => renderLeads(data));
}

function renderLeads(data) {
  document.getElementById('leadsTable').innerHTML = data.map(l => `
    <tr>
      <td>${l.id || '--'}</td>
      <td>${l.name || 'Anonymous'}</td>
      <td>${l.phone || '--'}</td>
      <td>${l.budget || '--'}</td>
      <td>${l.destinationInterest || '--'}</td>
      <td>${formatDate(l.createdAt)}</td>
    </tr>
  `).join('');
}

function exportCSV() {
  // Try API first
  fetch(`${API_BASE}/leads/export`, { headers: authHeaders() })
    .then(r => r.blob())
    .then(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'tripmind_leads.csv';
      a.click();
    })
    .catch(() => {
      // Fallback: export from local data
      const leads = JSON.parse(localStorage.getItem('tripmind_leads') || '[]');
      let csv = 'Name,Phone,Budget,Destination,Date\n';
      leads.forEach(l => {
        csv += `"${l.name || ''}","${l.phone || ''}","${l.budget || ''}","${l.destinationInterest || ''}","${l.createdAt || ''}"\n`;
      });
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'tripmind_leads.csv';
      a.click();
    });
}

// ===== Contacts =====
function loadContacts() {
  fetch(`${API_BASE}/admin/contact`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => renderContacts(data));
}

function loadUsers() {
  fetch(`${API_BASE}/admin/users`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      document.getElementById('usersTable').innerHTML = data.map(u => `
        <tr>
          <td>${u.id}</td>
          <td>${u.name || '--'}</td>
          <td>${u.email}</td>
          <td>${formatDate(u.createdAt)}</td>
        </tr>
      `).join('');
    });
}

function loadChatMessages() {
  fetch(`${API_BASE}/admin/chat-messages`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      document.getElementById('chatMessagesTable').innerHTML = data.map(m => `
        <tr>
          <td>${m.id}</td>
          <td>${m.userId || '--'}</td>
          <td>${m.sessionId || '--'}</td>
          <td>${capitalize(m.sender || 'user')}</td>
          <td style="max-width:280px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${(m.message || '').replace(/"/g, '&quot;')}">${m.message || '--'}</td>
          <td>${formatDate(m.createdAt)}</td>
        </tr>
      `).join('');
    });
}

function loadPayments() {
  fetch(`${API_BASE}/admin/payments`, { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      document.getElementById('paymentsTable').innerHTML = data.map(p => `
        <tr>
          <td>${p.id}</td>
          <td>${p.userId || '--'}</td>
          <td>${p.requestType || '--'}</td>
          <td>₹${Number(p.amount || 0).toLocaleString()}</td>
          <td>${capitalize(p.paymentStatus || 'pending')}</td>
          <td>${formatDate(p.createdAt)}</td>
        </tr>
      `).join('');
    });
}

function renderContacts(data) {
  document.getElementById('contactsTable').innerHTML = data.map(c => `
    <tr>
      <td>${c.id}</td>
      <td>${c.name}</td>
      <td>${c.email}</td>
      <td>${c.phone || '--'}</td>
      <td style="max-width:200px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${c.message}">${c.message}</td>
      <td>
        <span class="status-badge ${c.status === 'read' ? 'completed' : 'pending'}" onclick="toggleContactStatus(${c.id}, '${c.status}')" style="cursor:pointer">
          ● ${capitalize(c.status)}
        </span>
      </td>
      <td>${formatDate(c.createdAt)}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="deleteContact(${c.id})">🗑️</button>
      </td>
    </tr>
  `).join('');
}

function toggleContactStatus(id, currentStatus) {
  const newStatus = currentStatus === 'read' ? 'unread' : 'read';
  fetch(`${API_BASE}/admin/contact/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status: newStatus })
  }).then(() => loadContacts());
}

function deleteContact(id) {
  if (!confirm('Delete this message?')) return;
  fetch(`${API_BASE}/admin/contact/${id}`, { method: 'DELETE', headers: authHeaders() })
    .then(() => loadContacts());
}

// ===== Helpers =====
function capitalize(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : ''; }
function formatDate(d) { return d ? new Date(d).toLocaleDateString('en-IN') : '--'; }

// Sample data fallbacks
function getSampleRequests() {
  return [
    { id: 1, name: 'Arjun S.', email: 'arjun@test.com', budget: 15000, days: 5, preferences: 'Adventure', status: 'pending', createdAt: new Date().toISOString() },
    { id: 2, name: 'Priya M.', email: 'priya@test.com', budget: 8000, days: 3, preferences: 'Spiritual', status: 'completed', createdAt: new Date().toISOString() },
    { id: 3, name: 'Rahul K.', email: 'rahul@test.com', budget: 20000, days: 7, preferences: 'Party', status: 'pending', createdAt: new Date().toISOString() }
  ];
}

function getSamplePackages() {
  return [
    { id: 1, title: 'Manali Mountain Escape', price: 8999, description: '4N/5D adventure package', featured: true },
    { id: 2, title: 'Goa Beach Paradise', price: 12999, description: '3N/4D beach party package', featured: false },
    { id: 3, title: 'Varanasi Spiritual Journey', price: 5999, description: '2N/3D spiritual tour', featured: false }
  ];
}

function getSampleLeads() {
  return [
    { id: 1, name: 'Vikram', phone: '+91 98765 43210', budget: '10000', destinationInterest: 'Manali', createdAt: new Date().toISOString() },
    { id: 2, name: 'Sneha', phone: '+91 87654 32109', budget: '15000', destinationInterest: 'Goa', createdAt: new Date().toISOString() },
    { id: 3, name: 'Anonymous', phone: '--', budget: '8000', destinationInterest: 'Varanasi', createdAt: new Date().toISOString() }
  ];
}
