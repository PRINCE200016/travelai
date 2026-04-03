// ===== TripMind AI - Homepage JS =====
const API_BASE = window.TRIPMIND_CONFIG?.apiBase || '/api';

// Theme (light/dark) toggle
function applyTheme(theme) {
  const t = theme === 'light' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', t);
  localStorage.setItem('tripmind_theme', t);
  const btn = document.getElementById('themeToggle');
  if (btn) btn.textContent = t === 'light' ? '🌙' : '☀️';
}

function initTheme() {
  const saved = localStorage.getItem('tripmind_theme');
  if (saved) return applyTheme(saved);
  const prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
  applyTheme(prefersLight ? 'light' : 'dark');
}

// Auth State Management
document.addEventListener('DOMContentLoaded', () => {
  initTheme();

  const navLinks = document.querySelector('.nav-links');
  if (navLinks) {
    const userJson = localStorage.getItem('user');
    const authDiv = document.createElement('div');
    authDiv.style.display = 'inline-block';
    authDiv.style.marginLeft = '16px';
    
    if (userJson) {
      authDiv.innerHTML = `
        <a href="dashboard.html" class="btn btn-outline" style="padding:8px 16px; margin-right:8px;">Dashboard</a>
        <a href="saved.html" class="btn btn-outline" style="padding:8px 16px; margin-right:8px;">Saved</a>
        <button onclick="logoutUser()" class="btn btn-primary" style="padding:8px 16px;">Logout</button>
      `;
    } else {
      authDiv.innerHTML = `
        <a href="login.html" class="btn btn-outline" style="padding:8px 16px; margin-right:8px;">Log In</a>
        <a href="saved.html" class="btn btn-outline" style="padding:8px 16px; margin-right:8px;">Saved</a>
        <a href="register.html" class="btn btn-primary" style="padding:8px 16px;">Sign Up</a>
      `;
    }
    navLinks.appendChild(authDiv);
  }

  const toggle = document.getElementById('themeToggle');
  if (toggle) {
    toggle.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      applyTheme(current === 'light' ? 'dark' : 'light');
    });
  }
});

// Navbar scroll effect
window.addEventListener('scroll', () => {
  const navbar = document.getElementById('navbar');
  if (window.scrollY > 50) {
    navbar.classList.add('scrolled');
  } else {
    navbar.classList.remove('scrolled');
  }
});

// Mobile nav toggle
function toggleMobileNav() {
  const navLinks = document.getElementById('navLinks');
  if (!navLinks) return;
  navLinks.style.display = navLinks.style.display === 'flex' ? 'none' : 'flex';
  
  if (navLinks.style.display === 'flex') {
    navLinks.style.position = 'fixed';
    navLinks.style.top = '0';
    navLinks.style.left = '0';
    navLinks.style.width = '100%';
    navLinks.style.height = '100vh';
    navLinks.style.background = 'rgba(10, 10, 26, 0.98)';
    navLinks.style.flexDirection = 'column';
    navLinks.style.alignItems = 'center';
    navLinks.style.justifyContent = 'center';
    navLinks.style.gap = '24px';
    navLinks.style.zIndex = '9999';
    navLinks.style.fontSize = '1.2rem';
  }
}

// Smooth scroll for anchor links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', function (e) {
    const target = document.querySelector(this.getAttribute('href'));
    if (target) {
      e.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });
});

// Intersection Observer for fade-in animations
const observerOptions = {
  threshold: 0.1,
  rootMargin: '0px 0px -50px 0px'
};

const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.style.opacity = '1';
      entry.target.style.transform = 'translateY(0)';
    }
  });
}, observerOptions);

document.querySelectorAll('.card, .mood-card, .result-section, .cta-section').forEach(el => {
  el.style.opacity = '0';
  el.style.transform = 'translateY(20px)';
  el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
  observer.observe(el);
});

// Animate hero stats counter
function animateCounter(element, target, suffix = '') {
  let current = 0;
  const increment = target / 60;
  const timer = setInterval(() => {
    current += increment;
    if (current >= target) {
      current = target;
      clearInterval(timer);
    }
    
    if (target >= 1000) {
      element.textContent = Math.floor(current).toLocaleString() + suffix;
    } else {
      element.textContent = Math.floor(current) + suffix;
    }
  }, 16);
}

// Start counter animation when visible
const statsObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      const numbers = entry.target.querySelectorAll('.number');
      numbers.forEach(num => {
        const text = num.textContent;
        if (text.includes('K+')) {
          animateCounter(num, 10, 'K+');
        } else if (text.includes('%')) {
          animateCounter(num, 98, '%');
        } else if (text.includes('+')) {
          animateCounter(num, 500, '+');
        }
      });
      statsObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.5 });

const heroStats = document.querySelector('.hero-stats');
if (heroStats) {
  statsObserver.observe(heroStats);
}

// Load Trending Packages
document.addEventListener('DOMContentLoaded', () => {
  const packagesGrid = document.getElementById('packagesGrid');
  if (!packagesGrid) return;

  fetch(`${API_BASE}/packages`)
    .then(r => r.json())
    .then(packages => {
      const featured = packages.filter(p => p.featured).slice(0, 6);
      const displayData = featured.length > 0 ? featured : packages.slice(0, 6);

      if (displayData.length === 0) throw new Error('No packages');

      packagesGrid.innerHTML = displayData.map(p => `
        <div class="card package-card">
          <div class="card-img" style="background: url('${p.imageUrl || 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?w=600'}') center/cover;">
            <div class="card-badge">⭐ Trending</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">${p.title}</h3>
            <p class="card-text">${(p.description || '').substring(0, 100)}...</p>
            <div class="card-price">₹${p.price ? p.price.toLocaleString() : '--'}</div>
          </div>
          <div class="card-footer">
            <button class="btn btn-outline btn-sm" onclick="location.href='itinerary.html'">View Details</button>
            <button class="btn btn-primary btn-sm" onclick="location.href='chat.html'">AI Match</button>
          </div>
        </div>
      `).join('');
    })
    .catch(() => {
      // Demo Fallback
      packagesGrid.innerHTML = `
        <div class="card package-card">
          <div class="card-img" style="background: url('https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?w=600') center/cover;">
            <div class="card-badge">⭐ Top Rated</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">Manali Adventure Escapade</h3>
            <p class="card-text">5 Days of snow sports, paragliding, and camping under the Himalayan stars.</p>
            <div class="card-price">₹12,999</div>
          </div>
          <div class="card-footer">
            <button class="btn btn-outline btn-sm" onclick="location.href='itinerary.html'">View Details</button>
            <button class="btn btn-primary btn-sm" onclick="location.href='chat.html'">AI Match</button>
          </div>
        </div>
        
        <div class="card package-card">
          <div class="card-img" style="background: url('https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?w=600') center/cover;">
            <div class="card-badge">🔥 Popular</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">Goa Beach Retreat & Nightlife</h3>
            <p class="card-text">3 Days of pure relaxation, sunset cruises, and vibrant beach parties.</p>
            <div class="card-price">₹9,499</div>
          </div>
          <div class="card-footer">
            <button class="btn btn-outline btn-sm" onclick="location.href='itinerary.html'">View Details</button>
            <button class="btn btn-primary btn-sm" onclick="location.href='chat.html'">AI Match</button>
          </div>
        </div>

        <div class="card package-card">
          <div class="card-img" style="background: url('https://images.unsplash.com/photo-1561361513-2d000a50f0dc?w=600') center/cover;">
            <div class="card-badge">☮️ Peaceful</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">Varanasi Spiritual Journey</h3>
            <p class="card-text">2 Days immersed in the ancient energy of the Ganges. Perfect for stress relief.</p>
            <div class="card-price">₹4,999</div>
          </div>
          <div class="card-footer">
            <button class="btn btn-outline btn-sm" onclick="location.href='itinerary.html'">View Details</button>
            <button class="btn btn-primary btn-sm" onclick="location.href='chat.html'">AI Match</button>
          </div>
        </div>
      `;
    });
});

function startChatWithMood(mood) {
  sessionStorage.setItem('preselectedMood', mood);
  window.location.href = 'chat.html';
}

console.log('🚀 TripMind AI loaded successfully!');

async function logoutUser() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      headers: user?.token ? { Authorization: `Bearer ${user.token}` } : {}
    });
  } catch (_) {}
  localStorage.removeItem('user');
  window.location.reload();
}
