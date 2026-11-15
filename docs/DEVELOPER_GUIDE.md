# Developer guide

How to work on TripMind. For how the system fits together, read `ARCHITECTURE.md` first; for deploying, `DEPLOYMENT.md`.

## Setup

- Java 17 and Maven 3.9 (or the Maven wrapper cache in `~/.m2`)
- Node.js, only for running the optional browser test scripts
- Any modern browser

```bash
cd backend
mvn spring-boot:run                 # API + website on http://localhost:7860 (H2 in-memory database)
mvn test                            # all automated tests (~50)
mvn test -Dlive=true                # also checks verification against the real Open-Meteo / Wikipedia APIs
```

Useful local environment variables: `ADMIN_PASS` (admin login), `GEMINI_API_KEY` (AI discovery), `DATABASE_URL` (a real Postgres). The full list is in `DEPLOYMENT.md`.

## Repository layout

```
frontend/            static website (no build step)
  css/style.css      design system
  css/pages.css      page layouts
  js/site.js         shared shell: header, menu, API client, icons, toasts, consent, saved trips
  js/query.js        trip search <-> URL parameters
  js/*.js            one script per interactive page
backend/             Spring Boot API
  src/main/java/com/tripmind/planner/      engine, catalogue, request/response types
  src/main/java/com/tripmind/planner/ai/   AI discovery and open-data verification
  src/main/resources/catalog/              destinations.json, origins.json
  src/test/java/...                        unit and API tests
TripMindAi/          Hugging Face Space repo (a copy of backend/ + Space Dockerfile); submodule
scripts/sync-space.sh  copies backend/ into TripMindAi/
docs/                these documents
Dockerfile           API + website in one container (Docker, Render)
render.yaml          optional Render blueprint
.github/workflows/keep-alive.yml   pings the API every 12 hours
```

## Common changes

**Add or edit a destination in the fallback catalogue.** Edit `backend/src/main/resources/catalog/destinations.json`. Each entry needs:
- `id`, `name`, `state`, `lat`, `lon`, `terrain`
- `tags` (0–1 per trip type)
- `costIndex` (1.0 = typical), `activityPerDay`, `popularity` (1 = offbeat … 3 = famous)
- railhead and airport plus distances (`null` when too far)
- 12 monthly `highC`/`lowC`
- `best`/`avoid`/`peak`/`closed`/`roadClosed` months, `minDays`
- `image` and `imageAlt`, `summary`, ordered `highlights` (these become the day plan), `cautions`

A caution can be limited to certain months with a prefix such as `"[6,7,8,9] text"`. Only use photos you have checked, and describe them specifically in `imageAlt`.

**Add a starting city.** Append to `catalog/origins.json` with `name`, `lat`, `lon`, `airport` and optional `aliases` (old names, common typos). Unlisted towns already work through geocoding; adding a city just makes it faster and typo-tolerant.

**Tune the planner.** Everything is in `planner/TripPlanner.java`:
- `chooseLeg`: transport rates, speeds, overnight rules, flight thresholds
- `cost`: room rates, food, local transport, activities, buffer, comfort selection
- `season` / `crowd`: seasonal multipliers and crowd signals
- `score`: weights, reasons and cautions
- `rank` / `mismatch` / `suggestionsWhenOver`: what gets recommended and what is suggested

Run `mvn test` after any change. `TripPlannerTest` encodes the product rules: never over budget, trip type changes the answer, monsoon hurts beaches, Kedarnath closed in winter, and so on.

**Change the AI prompt or model chain.** The prompt is `AiPlaceService.prompt`. The chain is the `GEMINI_MODELS` setting, with logic in `GeminiClient`. Keep the "never invent places / no prices" rules: verification and costing depend on them. `AiPlaceServiceTest` uses a fake model and a fake map, so it runs offline.

**Change the website.**
- Reuse classes from `style.css`. Put page-only CSS in `pages.css` or a page `<style>` block, using the CSS custom properties.
- Escape anything from the server with `TM.esc`.
- New pages: copy the head and body skeleton from `index.html`, and add the page to `frontend/sitemap.xml` and to the nav in `site.js` if it belongs there.
- Check at 390px wide: no sideways scrolling, and the menu opens.

## Browser test scripts (optional)

The end-to-end and mobile checks used during the rebuild drive Chrome with `puppeteer-core`. They aren't part of the repo. To recreate them, install `puppeteer-core`, point it at your Chrome, and follow `QA_TEST_CHECKLIST.md`.

## Conventions

- Prices and estimates come only from `TripPlanner`. Never let AI text contain money.
- Never change the user's budget or dates silently. Offer a suggestion instead.
- Public endpoints create records only; anything that lists or changes data lives under `/api/admin`.
- No secrets in code or commits. Configuration comes from environment variables.
- Commit as the repo owner. Keep backend changes in step between `backend/` and `TripMindAi/` (use `scripts/sync-space.sh`).
