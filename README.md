# TripMind AI

Tripmind AI is an intelligent travel planner startup that curates personalized itineraries, predicts crowd levels, analyzes optimal travel budgets, and plans your perfect getaway based on your psychological profile and budget constraints. 

This repository contains the full MVP including a modern Vanilla JS frontend and a robust Spring Boot backend powered by AI integrations and PostgreSQL.

## 🚀 Key Features
- **AI Matchmaking**: Gemini API integration processes psychological profiles and travel preferences to recommend optimal destinations.
- **Dynamic Budgets**: In-house budget algorithm calculates exact travel and hotel costs based on distances and tier levels.
- **Weather & Crowd Prediction**: Live weather forecasting and seasonal crowd analysis mapped to the destination.
- **Admin Dashboard**: Full CRM allowing you to manage leads, view chat history, and monitor conversion analytics.
- **Authentication**: Custom Bearer Token system with fully encrypted BCrypt passwords.

## 🏗 Stack Architecture
- **Frontend**: Lightweight, high-performance Vanilla HTML/CSS/JS avoiding complex build tools.
- **Backend**: Java 17, Spring Boot 3.2, Spring Data JPA.
- **Database**: PostgreSQL (Designed to natively sync with Supabase for production).
- **Infrastucture**: Dockerized environment for immediate, 1-click execution.

## 🐳 Running Locally (Docker - Recommended)

The absolute fastest way to run this startup is using the composed Docker environment which spins up the Java app, the static frontend, and a local PostgreSQL database imitating Supabase.

```bash
docker-compose up -d --build
```
- **App/Frontend**: `http://localhost:8080`
- **Database**: `localhost:5432` (User: `postgres`, Pass: `postgres`)

## 💻 Running Locally (Manual Development)

To work on elements manually:

### 1) PostgreSQL Setup
Ensure you have a local PostgreSQL DB called `travel_ai` or set your `DB_URL` variables in `backend/src/main/resources/application.properties`.

### 2) Start the Backend
```bash
cd backend
mvn clean spring-boot:run
```
*Backend API and frontend static files run at `http://localhost:8080`.*

## 🌍 Production Deployment (Supabase & Render/Fly.io)

This repository is structured for zero-config deployments. 

1. **Database**: Spin up a Supabase project. Pass your Supabase provided PostgreSQL string into `DB_URL`. 
2. **Backend**: Provide the `Dockerfile` to Render, Fly.io or Railway. They will auto-build the Maven backend and serve the application automatically.
3. **Frontend**: If you want to detach the frontend and host it globally on Vercel/Netlify, simply set the API base url at the top of your `index.html` using:
   ```html
   <script>window.__TRIPMIND_API_BASE__ = "https://your-backend-url.com/api";</script>
   ```

### Critical Environment Variables (Production)
| Variable | Description |
|---|---|
| `DB_URL` | Supabase Postgres URL (`jdbc:postgresql://...`) |
| `DB_USER` | Supabase Postgres Username |
| `DB_PASSWORD` | Supabase Postgres Password |
| `GEMINI_API_KEY` | Your Google Gemini API Key |
| `WEATHER_API_KEY` | OpenWeatherMap API Key |
| `ADMIN_USER` | Admin dashboard username |
| `ADMIN_PASS` | Admin dashboard custom password |

## 📚 Documentation
- Developer guide: `docs/DEVELOPER_GUIDE.md`
- QA checklist: `docs/QA_TEST_CHECKLIST.md`
- Funding/business brief: `docs/BUSINESS_BRIEF_FOR_FUNDING.md`

## ✅ Hard-Constraint Recommendation Engine
The recommendation flow now enforces hard feasibility constraints before returning any destination:
- Distance caps by duration (1 day: 150km, 2-3 days: 400km)
- Low-budget strict mode (`<= ₹2000`) with local/same-day constraints
- Travel-cost cap and full cost simulation before output
- Fallback-only response when no destination is feasible

## 🩺 Health & Monitoring
- Health endpoint: `GET /actuator/health`
- Info endpoint: `GET /actuator/info`
- Metrics endpoint: `GET /actuator/metrics`
