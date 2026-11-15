# Changelog

## 15 November 2026: AI discovery live, mobile fixes, deployment

**AI discovery**
- An AI suggests destinations anywhere in India (famous and hidden gems) from the traveller's details and their own words, instead of picking from a fixed list.
- Every suggestion is verified on open map data (Wikipedia, Open-Meteo/GeoNames, OpenStreetMap), with last year's observed weather and a Wikipedia photo. Unverifiable places are dropped.
- Gemini model fallback chain (`gemini-flash-lite-latest` → `gemini-3.1-flash-lite` → `gemini-3.5-flash`) with per-model time limits and a 5-minute cool-down for overloaded models. Before this, `gemini-2.5-flash` was retired for new users (404) and `gemini-3.8-flash` was overloaded (timeouts / 503).
- `query.aiStatus` in every plan response says whether AI was used and why not.
- Planner asks "Famous places or hidden gems?" and "Describe your dream trip"; results show a hidden-gem badge, "why it suits you" and a local tip.
- Any town can be the starting point (geocoded); flights from towns without an airport go via the nearest hub.

**Mobile**
- Fixed the mobile menu, which opened with zero height on every page and locked scrolling. It is now a full-screen panel with a close button.
- No blur on the sticky header on phones; larger tap targets; smaller images on phones; consent label layout fixed.

**Deployment**
- API on Hugging Face Spaces; database on Supabase via the Session pooler.
- `DATABASE_URL` (postgres://…) is converted to JDBC automatically.
- Keep-alive GitHub Action pings the API every 12 hours.
- Website waits for a sleeping server and shows a "waking up" notice.
- `scripts/sync-space.sh` copies the backend into the Space repo.
- Optional `render.yaml` blueprint.

## 14 November 2026: rebuild

**Planner (replaces the old chat bot and `ConstraintDecisionEngine`)**
- The old engine always returned the cheapest nearby place and ignored the requested trip type; Gemini was configured but never called.
- New `TripPlanner`:
  - picks realistic transport;
  - checks feasibility (time on the ground, closed seasons, minimum days);
  - costs trips door to door with seasonal pricing;
  - scores on trip type, season, weather, crowds, travel effort and budget use;
  - recommends only trips within budget.
- Honest states with one-click fixes: over budget, no matching trip type, unknown city, unreachable.
- 40-place catalogue with monthly climate, seasons, access and costs; 70 starting cities with typo tolerance.

**Website**
- New design system (Schibsted Grotesk, kohl-ink and vermilion palette); every page rebuilt.
- 3-step planner, shareable results, destinations browser, saved trips and account history, expert planning, admin console.
- Removed: fake statistics, visible ad placeholders, auto-popping lead capture, emoji UI, wrong destination photos.

**Security**
- Admin routes protected; public endpoints create-only with validation.
- Lead export, request deletion, package editing and payment status are no longer publicly writable.
- Hashed session tokens, no plaintext-password login path, escaped output (the admin panel previously had stored XSS), bounded rate limiting, consistent JSON errors, H2 console off.

**Housekeeping**
- Docker image serves the website.
- Build output and logs removed from git.
- Documentation rewritten.
