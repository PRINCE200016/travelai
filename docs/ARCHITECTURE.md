# Architecture

```
 Browser ──► Website (frontend/, static, Vercel)
               │  fetch /api/...
               ▼
            API (backend/, Spring Boot, Hugging Face Space)
               ├─ OriginResolver ── catalogue cities, else Open-Meteo geocoding (any town)
               ├─ AiPlaceService ── Gemini (model chain) → parse → verify on open map data
               │                     (Wikipedia, Open-Meteo/GeoNames, OpenStreetMap)
               │                     → last year's weather (Open-Meteo archive) + photo (Wikipedia)
               ├─ TripPlanner ───── transport, feasibility, costing, scoring, ranking
               ├─ WeatherService ── live 16-day forecast (Open-Meteo)
               └─ PostgreSQL (Supabase) / H2 ── users, sessions, searches, requests, messages
```

## Frontend (`frontend/`)

Plain HTML, CSS and JavaScript with no build step. Every page has the same skeleton: `<header data-site-header>` and `<footer data-site-footer>` placeholders, `<main id="main">`, then `js/config.js` and `js/site.js`.

### Shared files

| File | Role |
|---|---|
| `css/style.css` | Design system: OKLCH colour tokens (kohl ink + vermilion action), Schibsted Grotesk type scale, spacing, buttons, forms, choice tiles, steppers, panels, badges, key-value lists, photo tiles, dialogs, toasts, cookie bar, tables, mobile menu, print and reduced-motion rules |
| `css/pages.css` | Page compositions: home hero and quick-plan bar, planner wizard, results/trip layout, saved trips, auth split |
| `js/config.js` | API base URL (`REMOTE_API`), support email, WhatsApp number, optional analytics ID |
| `js/site.js` | `window.TM`: header/footer and mobile menu, icon set (`TM.icon` / `data-icon`), `TM.api` (JSON fetch, auth header, timeouts, "waking up" notice), toasts, auth storage, saved trips (localStorage), cookie consent and opt-in analytics, origin-city autocomplete, ₹ input formatting, `TM.esc` for HTML escaping |
| `js/query.js` | `window.TMQuery`: converts a trip search to/from URL parameters (so every results page is shareable), labels, trip-type list |

### Pages

| Page | Script | Purpose |
|---|---|---|
| `index.html` | `home.js` | Photo hero with the quick-plan bar, how estimates work, trip types, expert planning, FAQ |
| `plan.html` | `plan.js` | 3-step planner: basics → trip type, "describe your dream trip", famous/hidden, weather, crowds, pace → comfort |
| `result.html` | `result.js` | Calls `POST /api/plan`; renders trips, costs, transport, weather, crowds, reasons, cautions, day plan, booking links, save/share; over-budget and no-match states; saved snapshots (`?saved=`) |
| `destinations.html` | inline | Catalogue browser with trip-type, month and search filters; "Price a trip here" opens the planner with `include=` |
| `saved.html` | `saved.js` | Saved trips (this browser) and, when signed in, account search history |
| `itinerary.html` | `itinerary.js` | Expert planning request (₹199 after confirmation); saved via API, EmailJS notice is best-effort |
| `admin.html` | `admin.js` | Admin console: stats, requests, packages, leads (CSV), messages, users, payments, searches. HTTP Basic, credentials kept in sessionStorage |
| `login.html`, `register.html` | inline | Accounts (token stored in localStorage as `user`) |
| `contact.html`, `about.html`, `pricing.html`, `careers.html`, `blog.html`, `best-places.html`, `budget-travel.html` | inline / none | Content pages |
| `privacy.html`, `terms.html`, `cookie-policy.html`, `ai-disclaimer.html`, `legal.html` | none | Legal; `ai-disclaimer.html` explains how estimates and AI are used |
| `404.html`, `500.html`, `offline.html` | none | Error pages (`<base href="/">` on 404/500) |
| `chat.html`, `plan-trip.html`, `results.html`, `dashboard.html` | none | Redirects from old URLs (keep query strings) |

### Frontend rules
- Escape every server or user value with `TM.esc` before inserting HTML.
- Icons come from `TM.icon(name)`, not emoji.
- Colours only through CSS custom properties; no hard-coded hex.
- The mobile menu is created by `site.js` as a direct child of `<body>`. If it were inside the header, the header's styles would make its fixed positioning relative to the header.

## Backend (`backend/src/main/java/com/tripmind/`)

| Package | Contents |
|---|---|
| `planner/` | `TripPlanner` (engine), `Catalog` (JSON catalogue, origin matching, haversine, nearest airport), `PlanRequest` (input + validation), `PlanResult` (response records), `OriginResolver` |
| `planner/ai/` | `AiPlaceService` (prompt, parsing, verification, enrichment, cache), `GeminiClient` (model chain), `OpenDataLookup` (Open-Meteo, Wikipedia, OpenStreetMap), `LlmClient` and `PlaceLookup` interfaces |
| `controller/` | `PlanController`, `AuthController`, `AdminController`, `ItineraryController`, `ContactController`, `LeadController`, `PackageController`, `Validate` |
| `config/` | Filters (rate limit → logging → bearer auth → admin auth), `WebConfig` (CORS, static files), `ApiExceptionHandler`, `Tokens`, `DatabaseUrlEnvironment` |
| `service/` | `WeatherService` (live forecast) |
| `model/`, `repository/` | JPA entities and repositories |

### How a search works (`POST /api/plan`)

1. **Validate.** `PlanRequest.normalise` checks the budget and dates, clamps days (1–21) and travellers (1–20), maps trip-type synonyms, and cleans the free text (max 300 chars). With no date, it assumes three weeks from today and tells the user.
2. **Starting point.** `OriginResolver` matches ~70 known cities, including typos and old names (Dehli, Bombay, Bangalore), else geocodes any Indian town. Unknown → `origin_unknown` with suggestions.
3. **AI discovery** (when `GEMINI_API_KEY` is set):
   - The prompt carries every rule: start, month, days, group, budget per person per day, trip types, weather, crowds, pace, comfort, the famous/hidden mix, the traveller's own words, how far is reachable for the trip length, and "no invented places, no prices".
   - `GeminiClient` tries models in order. Each attempt is capped (30 s for lite models, 60 s for others, 80 s in total), and an overloaded or timed-out model is skipped for 5 minutes.
   - Each of the 8 proposals is sanitised (lengths, tags, months, ranges) and **verified**:
     1. Wikipedia article coordinates within 50 km, else
     2. a gazetteer hit for the place or its base town within 50 km, else
     3. OpenStreetMap (1 request per second), else
     4. a same-name, same-state match within ~150 km, keeping the model's coordinates.
     Anything else is dropped.
   - Verified places get last year's observed temperatures and rainy days for the travel month (a very wet month becomes an "avoid" month) and a Wikipedia photo, else a representative image.
   - The result is cached for 6 hours per similar search. If fewer than 3 places verify, the catalogue is used instead.
4. **Planning** (`TripPlanner`) runs for every candidate:
   - **Transport:** road (state bus / AC sleeper / private cab), train (sleeper / 3AC / 2AC, plus the last mile from the railhead) or flight (economy or flexible, plus the last mile; small regional airports count as one-stop; towns without an airport drive to the nearest hub). It picks the option with the lowest cost plus the value of daylight hours lost. Overnight legs count as about 2 waking hours.
   - **Feasible?** Not closed that month, not shorter than the place needs, the journey leaves at least ~45% of waking time on the ground, and day trips are ≤4 h each way unless overnight.
   - **Cost:** transport + stay (rooms by group type; dorm beds for budget solo/friends) + food + local transport + activities + 5% buffer. Season multipliers: peak +25% on stays, off-season −20%, Christmas–New Year +45%; flights also rise in peak. "Auto" comfort picks the highest level that keeps ~3% headroom.
   - **Score (0–100):**

     | Factor | Weight |
     |---|---|
     | Trip-type match | 36% |
     | Season | 18% |
     | Weather preference vs that month | 13% |
     | Crowds on those dates | 9% |
     | Travel effort | 16% |
     | Budget use | 8% |

     It also adjusts for group type (families avoid nightlife, couples favour relaxed places), the famous/hidden preference, and trips longer than a place deserves. It writes plain-language reasons and month-specific cautions.
5. **Ranking rules:**
   - Only trips **within budget** are recommended. If trip types were chosen, only places matching them are recommended (average tag strength ≥ 0.45).
   - Nothing matches but other trips fit → `ok_other`: an honest message, the matching places with their real cost (`stretch`), and one-click fixes.
   - Nothing fits → `over_budget`: the closest options, plus suggestions for the budget that unlocks them, fewer days, or cheaper comfort.
   - Nothing reachable → `no_match`, with a suggested longer trip.
   - Top 3 avoid repeating a state. "Mix" guarantees a hidden gem in the top 3 when a good one exists. `include` adds a requested place, or explains why it's impossible.
6. **Day plan:** an arrival day (overnight arrival or travel + check-in), middle days filled from the place's highlights at the chosen pace, and a return day. Leh's first day is rest.
7. **Saved:** the search is stored in `plan_searches` for the user's history and admin stats.

### Data (tables created automatically)

| Table | Holds |
|---|---|
| `users` | Name, email (unique, case-insensitive), bcrypt password |
| `auth_sessions` | SHA-256 hash of each session token, user, 30-day expiry |
| `plan_searches` | Query JSON, status, top destination, total, user (if signed in) |
| `itinerary_requests` | Expert planning requests and their status (`pending` → `in_progress` → `sent` → `closed`) |
| `contact_messages` | Contact form messages (`unread` / `read` / `replied`) |
| `leads`, `packages`, `payment_requests` | Older features kept for the admin console |

### Security
- `/api/admin/**` requires HTTP Basic (constant-time compare). There is no `WWW-Authenticate` header, so browsers don't pop a native dialog.
- Public endpoints only create records, and they validate every field (lengths, email/phone formats, statuses).
- Session tokens are 256-bit random values, stored only as SHA-256 hashes. Passwords use bcrypt, with a timing-safe login.
- Rate limits per IP: plan 15/min, auth 10/min, forms 5/min, admin 120/min.
- Errors return `{"error": "..."}` without stack traces. CORS uses an explicit origin list. No request bodies or IPs are logged.
- The AI never sees user identity, and its text is stripped of HTML and length-capped. The frontend escapes everything it renders.
