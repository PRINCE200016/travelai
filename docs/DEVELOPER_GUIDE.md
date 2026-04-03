# TripMind AI Developer Guide

## 1) Product Overview
TripMind AI is a full-stack travel recommendation platform with:
- Hard-constraint travel decision engine (distance + budget + cost simulation)
- Web chat UX for collecting trip preferences
- Result page with strict cost breakdown
- Lead capture, itinerary requests, payment-intent tracking, and admin dashboard

## 2) Tech Stack
- Frontend: Vanilla HTML/CSS/JS (`frontend/`)
- Backend: Spring Boot 3.2, Java 17+, Spring Data JPA (`backend/`)
- Database: PostgreSQL (local Docker or managed provider)
- Build: Maven

## 3) Repo Structure
- `backend/src/main/java/com/tripmind/...`: API, services, entities, filters
- `backend/src/test/...`: strict unit and integration tests
- `frontend/*.html`: pages
- `frontend/js/*.js`: page logic
- `frontend/css/style.css`: design system + themes
- `docker-compose.yml`: local app + postgres
- `docs/`: internal documentation

## 4) Run Locally

### Option A: Docker (recommended)
```bash
docker-compose up -d --build
```

Services:
- App: `http://localhost:8080`
- Postgres: `localhost:5432` (`travel_ai` / `postgres` / `postgres`)

### Option B: Manual
1. Start PostgreSQL and create DB `travel_ai`
2. Configure env vars (optional overrides):
   - `DB_URL` (default: `jdbc:postgresql://localhost:5432/travel_ai`)
   - `DB_USER` (default: `postgres`)
   - `DB_PASSWORD` (default: `postgres`)
3. Start backend:
```bash
cd backend
mvn clean spring-boot:run
```
4. Open `http://localhost:8080`

## 5) Environment Variables
- `GEMINI_API_KEY`: Gemini key (optional; `demo` fallback exists)
- `WEATHER_API_KEY`: OpenWeather key (optional; fallback exists)
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_DRIVER`
- `JPA_DDL_AUTO` (`update` for dev, prefer controlled migration strategy for prod)
- `APP_CORS_ALLOWED_ORIGINS`
- `ADMIN_USER`, `ADMIN_PASS`
- `EMAIL_USER`, `EMAIL_PASS`

## 6) Hard-Constraint Decision Engine
Core service: `ConstraintDecisionEngine`

Pipeline:
1. Read input
2. Candidate generation by distance only
   - 1 day -> max 150 km
   - 2-3 days -> max 400 km
   - >3 days -> no distance cap
3. Budget hard filters
   - If budget <= 2000:
     - no flights
     - same-day low-cost constraints
     - travel cost must not exceed 40% of budget
4. Cost simulation:
   - `travel + stay + food + activities`
   - reject if total > budget
5. Fallback if no valid destination:
   - `"Your budget and duration are too restrictive for this trip. Please increase budget or duration."`
6. Priority case:
   - `Indore + 1000 + 1 day + Spiritual` evaluates `Ujjain`, then `Omkareshwar` first

## 7) Testing
Run all backend tests:
```bash
mvn -f backend/pom.xml test
```

Added strict tests:
- `ConstraintDecisionEngineTest`
- `ChatControllerConstraintIT`

Test profile:
- `backend/src/test/resources/application.properties` uses in-memory H2

## 8) Key API Endpoints
- Auth:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
- Chat:
  - `POST /api/chat`
  - `GET /api/chat/history/{userId}`
  - `POST /api/chat/message`
- Result/supporting:
  - `GET /api/weather`
  - `GET /api/crowd`
- Commercial:
  - `POST /api/payment`
  - `POST /api/itinerary-request`
  - `POST /api/leads`
  - `POST /api/contact`
- Admin:
  - `/api/admin/*` endpoints for analytics, users, contact, chat logs, payments

## 9) Security Notes
- Do not commit real API keys or DB credentials
- Rotate any previously exposed keys immediately
- Use environment-specific secrets management in production
- Enable HTTPS and reverse proxy headers in deployment

## 10) Deployment Notes
- Build: Dockerfile compiles backend and serves frontend
- Set production env vars for DB + API keys + admin credentials
- For managed Postgres, update `DB_URL` with SSL settings as needed
- Recommend adding DB migration tool (Flyway/Liquibase) before scale

