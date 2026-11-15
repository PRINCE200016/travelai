# TripMind

An AI travel planner for India that starts from your budget.

Tell TripMind where you're leaving from (any town), when, how many days, who's going, what you can spend and, in your own words, what you're after. An AI searches all of India, from well-known destinations to hidden valleys and villages. Every suggestion is checked on open map data and given real weather for your month. Then TripMind costs each trip door to door (transport, stay, food, local travel, activities) and shows only the ones that fit your budget.

| | |
|---|---|
| Website | Vercel, deployed from `frontend/` on every push to `main` |
| API | https://arjunrajawat-tripmindai.hf.space/api (Hugging Face Space `Arjunrajawat/TripMindAi`) |
| Health check | https://arjunrajawat-tripmindai.hf.space/actuator/health |
| Admin | `<website>/admin.html` (user `admin`, password = `ADMIN_PASS` secret) |

## Documentation

| Document | Read it when |
|---|---|
| [docs/OWNER_GUIDE.md](docs/OWNER_GUIDE.md) | You need a link, a login, a secret, or something is broken |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Deploying the API, database or website, or moving hosts |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | You want to understand how the site and the planner work |
| [docs/API.md](docs/API.md) | Calling the API |
| [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) | Changing code, destinations or the AI prompt |
| [docs/ALGORITHMS_DSA.md](docs/ALGORITHMS_DSA.md) | The maths: distances, transport choice, costing, scoring, verification |
| [docs/QA_TEST_CHECKLIST.md](docs/QA_TEST_CHECKLIST.md) | Before a release |
| [docs/PRELAUNCH_STATUS.md](docs/PRELAUNCH_STATUS.md) | What's done and what's left |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | What changed and when |
| [docs/BUSINESS_BRIEF_FOR_FUNDING.md](docs/BUSINESS_BRIEF_FOR_FUNDING.md) | Explaining TripMind to partners or investors |

## Quick start

```bash
cd backend
mvn spring-boot:run          # http://localhost:7860 (API + website, in-memory database)
mvn test                     # automated tests
```

Optional: set `GEMINI_API_KEY` for AI discovery, `ADMIN_PASS` for the admin console, and `DATABASE_URL` for PostgreSQL. With Docker and PostgreSQL: `ADMIN_PASS=something docker compose up -d --build`, then open http://localhost:8080.

## Deploying a backend change

```bash
cd backend && mvn test && cd ..
bash scripts/sync-space.sh                         # copy backend into TripMindAi/
cd TripMindAi && git add -A && git commit -m "..." && git push origin main   # Hugging Face redeploys
```

Push the same change to GitHub too. Details are in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Stack

- **Website:** plain HTML, CSS and JavaScript (no build step), in `frontend/`
- **API:** Java 17, Spring Boot 3.2, Spring Data JPA, in `backend/` (deployed copy in the `TripMindAi/` submodule)
- **Data:** PostgreSQL on Supabase in production; H2 in memory locally
- **AI and open data:**
  - Google Gemini for discovery, with a model fallback chain
  - Open-Meteo for geocoding, last year's weather and live forecasts
  - Wikipedia for coordinates and photos
  - OpenStreetMap as a last-resort gazetteer

## How recommendations stay trustworthy

- **The AI suggests; it never decides prices.** Costs, feasibility and ranking come from TripMind's own rules.
- **Suggestions must exist on the map,** or they are dropped.
- **Recommendations never exceed the budget.** When nothing fits, the site says so and shows exactly what would make a trip work.
- **If the AI is unavailable,** a hand-picked 40-destination catalogue keeps the planner working, and the page says so.
