// Runtime configuration. Override before this file loads with:
//   <script>window.__TRIPMIND_API_BASE__ = "https://api.example.com/api";</script>
//   <script>window.__TRIPMIND_GA_ID__ = "G-XXXXXXX";</script>   (analytics only loads after cookie consent)
(function bootstrapConfig() {
  var host = window.location.hostname;
  var isLocal = host === 'localhost' || host === '127.0.0.1' || window.location.protocol === 'file:';
  var sameOrigin = window.location.port === '8080' || window.location.port === '7860';
  var defaultApiBase = sameOrigin ? '/api'
    : isLocal ? 'http://localhost:8080/api'
    : 'https://arjunrajawat-tripmindai.hf.space/api';

  var base = window.__TRIPMIND_API_BASE__ || defaultApiBase;
  window.TRIPMIND_CONFIG = {
    apiBase: base.replace(/\/+$/, ''),
    gaId: window.__TRIPMIND_GA_ID__ || '',
    supportEmail: 'arjunrajawat28@gmail.com',
    whatsapp: '917509245769'
  };
})();
