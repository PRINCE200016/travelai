// Runtime configuration. Override before this file loads with:
//   <script>window.__TRIPMIND_API_BASE__ = "https://api.example.com/api";</script>
//   <script>window.__TRIPMIND_GA_ID__ = "G-XXXXXXX";</script>   (analytics only loads after cookie consent)
(function bootstrapConfig() {
  // The deployed backend (Hugging Face Space). Change this if the backend moves.
  var REMOTE_API = 'https://arjunrajawat-tripmindai.hf.space/api';

  var loc = window.location;
  var isLocal = loc.hostname === 'localhost' || loc.hostname === '127.0.0.1' || loc.protocol === 'file:';
  var servedByBackend = loc.port === '8080' || loc.port === '7860' || /\.onrender\.com$/.test(loc.hostname)
    || loc.origin === new URL(REMOTE_API).origin;

  var defaultApiBase = servedByBackend ? '/api' : isLocal ? 'http://localhost:7860/api' : REMOTE_API;
  var base = window.__TRIPMIND_API_BASE__ || defaultApiBase;

  window.TRIPMIND_CONFIG = {
    apiBase: base.replace(/\/+$/, ''),
    gaId: window.__TRIPMIND_GA_ID__ || '',
    supportEmail: 'arjunrajawat28@gmail.com',
    whatsapp: '917509245769'
  };
})();
