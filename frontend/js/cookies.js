/**
 * TripMind AI - Cookie Management System
 * Handles the consent banner and backend cookie initialization.
 */

(function initCookieManager() {
  document.addEventListener("DOMContentLoaded", () => {
    const hasConsented = localStorage.getItem("tm_cookie_consent");
    
    if (!hasConsented) {
      showCookieBanner();
    } else if (hasConsented === "accepted") {
      initializeBackendCookies();
    }
  });

  function showCookieBanner() {
    const banner = document.createElement("div");
    banner.id = "cookieConsentBanner";
    banner.className = "cookie-banner";
    banner.innerHTML = `
      <div class="container cookie-content">
        <div class="cookie-text">
          <h4>🍪 Freshly Baked Cookies</h4>
          <p>We use cookies to personalize your travel planning experience and analyze our traffic. By clicking "Accept All", you consent to our use of cookies.</p>
        </div>
        <div class="cookie-actions">
          <button id="cookieDecline" class="btn btn-outline btn-sm">Essential Only</button>
          <button id="cookieAccept" class="btn btn-primary btn-sm">Accept All</button>
        </div>
      </div>
    `;
    document.body.appendChild(banner);

    // Initial animation
    setTimeout(() => banner.classList.add("show"), 100);

    document.getElementById("cookieAccept").addEventListener("click", () => {
      localStorage.setItem("tm_cookie_consent", "accepted");
      hideBanner();
      initializeBackendCookies();
    });

    document.getElementById("cookieDecline").addEventListener("click", () => {
      localStorage.setItem("tm_cookie_consent", "declined");
      hideBanner();
    });
  }

  function hideBanner() {
    const banner = document.getElementById("cookieConsentBanner");
    if (banner) {
      banner.classList.remove("show");
      setTimeout(() => banner.remove(), 400);
    }
  }

  async function initializeBackendCookies() {
    try {
      const apiUrl = window.TRIPMIND_CONFIG ? window.TRIPMIND_CONFIG.apiBase : "/api";
      const response = await fetch(`${apiUrl}/cookies/init`, {
        method: "GET",
        credentials: "include" // Crucial for receiving cookies from cross-origin backend
      });
      if (response.ok) {
        console.log("Backend cookies initialized successfully.");
      }
    } catch (err) {
      console.warn("Failed to initialize backend cookies:", err);
    }
  }
})();
