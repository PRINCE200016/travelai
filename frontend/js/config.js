// Central runtime config for frontend API calls.
// Override this in hosting by setting: window.__TRIPMIND_API_BASE__ before app scripts load.
(function bootstrapConfig() {
  const defaultApiBase = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1"
    ? "http://localhost:8080/api"
    : "https://arjunrajawat-tripmindai.hf.space/api"; // Deployed Hugging Face Backend URL

  const configuredBase = window.__TRIPMIND_API_BASE__ || defaultApiBase;
  window.TRIPMIND_CONFIG = {
    apiBase: configuredBase.replace(/\/+$/, "")
  };
})();
