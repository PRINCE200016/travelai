# TripMind

An AI travel planner for India that starts from your budget. Tell TripMind where you're leaving from (any town), when, who's going, what you can spend and, in your own words, what you're after. The AI searches all of India, famous places and hidden gems, every suggestion is verified on open map data with real weather, and only trips whose full door-to-door cost fits are shown.

- **Frontend**: plain HTML/CSS/JS in `frontend/`, with no build step. Shared shell in `js/site.js`, design system in `css/style.css`.
- **Backend**: Java 17 / Spring Boot 3.2 in `backend/`. PostgreSQL in production, H2 in-memory by default.
- **API contract**: `docs/API.md`.

## How the planner works

`backend/src/main/java/com/tripmind/planner/TripPlanner.java` runs these steps for every destination in `resources/catalog/destinations.json`:

1. **Transport.** It picks how to get there: road, train or flight. The choice depends on distance, terrain, railhead and airport access, the comfort level, and how much the traveller's time is worth. Overnight buses and trains count as costing about two waking hours, not a day.
2. **Feasibility.** A destination is dropped if the journey would eat too much of the trip, if it's closed that month (for example, Kedarnath in winter), or if the trip is shorter than the place needs (for example, Leh needs acclimatisation days).
3. **Costing.** It costs transport, stay (rooms by group type), food, local transport, activities and a 5% buffer. Prices are adjusted for the season and for Christmas–New Year. With "auto" comfort, it picks the highest comfort level that still leaves headroom in the budget.
4. **Scoring.** Each place is scored on trip type, season, weather preference against monthly climate, crowds on those dates, travel effort and how well it uses the budget.

Only trips that fit the budget are recommended. If nothing fits, the response explains why and offers one-click re-plans: the budget that would unlock the closest match, fewer days, or cheaper comfort. Budgets and dates are never changed silently.

**AI discovery** (`planner/ai/`). With `GEMINI_API_KEY` set, Gemini proposes ~12 real places (a mix of famous and offbeat, or as the traveller chose) that respect the trip length, season and budget, each with a "why it suits you" note and a local tip. `AiPlaceService` then verifies every place: Wikipedia coordinates, the Open-Meteo/GeoNames gazetteer, and OpenStreetMap as a rate-limited last resort. Unverifiable places are dropped. Each verified place gets last year's observed weather for the travel month and a Wikipedia photo. The planner then costs and filters them with the same rules as catalogue places, so **the AI never sets prices or breaks the budget**. Results are cached for 6 hours. Without a key, or if the AI fails, the built-in 40-place catalogue is used and the results page says so.

Edit `catalog/destinations.json` and `catalog/origins.json` to add places or starting cities. No code changes are needed.

## Run locally

```bash
cd backend
mvn spring-boot:run          # http://localhost:7860 serves the API and ../frontend
mvn test                     # planner and API tests
```

Or with Docker (PostgreSQL included):

```bash
ADMIN_PASS='a-long-random-password' docker compose up -d --build   # http://localhost:8080
```

## Configuration

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_DRIVER`, `JPA_DIALECT` | Database (PostgreSQL/Supabase in production; H2 if unset) |
| `ADMIN_USER`, `ADMIN_PASS` | Admin console (`/admin.html`) credentials. **Set a strong password.** |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins allowed to call the API |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | AI destination discovery (famous + hidden gems). Without a key the 40-place catalogue is used. Default model `gemini-2.5-flash` |
| `GEMINI_TIMEOUT_SECONDS` | Optional, default 40 |
| `FRONTEND_PATH` | Where static files are served from (default `file:../frontend/`) |

When the frontend is hosted separately (e.g. Vercel), set the API base before `config.js` loads:

```html
<script>window.__TRIPMIND_API_BASE__ = "https://your-backend/api";</script>
<script>window.__TRIPMIND_GA_ID__ = "G-XXXXXXX";</script> <!-- optional; loads only after cookie consent -->
```

## Security notes

- All `/api/admin/**` routes need HTTP Basic credentials. Public endpoints only create records, and they validate their input.
- Session tokens are random, 30-day, and stored only as SHA-256 hashes. Passwords use bcrypt.
- Rate limits: planner 30/min, auth 10/min, forms 5/min per IP.
- Every server value is HTML-escaped before rendering in the frontend and admin console.

## Health

`GET /actuator/health`
