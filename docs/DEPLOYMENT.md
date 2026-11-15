# Deployment

TripMind is two deployable parts:

| Part | Lives in | Hosted on | Deploys when |
|---|---|---|---|
| Website (static HTML/CSS/JS) | `frontend/` | Vercel (Netlify also works) | You push to GitHub `main` |
| API (Spring Boot, Java 17) | `backend/` → copied into `TripMindAi/` | Hugging Face Space (Docker) | You push `TripMindAi/` to Hugging Face |

The website calls the API at the URL in `frontend/js/config.js` (`REMOTE_API`). You can override it per page with `window.__TRIPMIND_API_BASE__`.

---

## 1. Backend on Hugging Face Spaces (current)

The Space `Arjunrajawat/TripMindAi` is a separate git repository. The local copy is the `TripMindAi/` folder, a submodule of the main repo. It has its own `Dockerfile` (port 7860, runs as UID 1000) and `README.md` (Space metadata), plus `RootController.java`, which answers `/` with a status message.

**Deploy a backend change**

```bash
cd backend && mvn test && cd ..          # 1. tests must pass
bash scripts/sync-space.sh               # 2. copy backend/src + pom.xml into TripMindAi/
cd TripMindAi
git add -A && git commit -m "describe the change"
git push origin main                     # 3. Hugging Face rebuilds (2–5 min)
cd .. && git add -A && git commit -m "describe the change" && git push origin main   # 4. keep GitHub in step
```

Authentication: username `Arjunrajawat`, password = a Hugging Face **Write** access token.

**Space secrets** (Settings → Variables and secrets): `ADMIN_PASS`, `GEMINI_API_KEY`, `DATABASE_URL`, plus optional `ADMIN_USER`, `GEMINI_MODELS`, `GEMINI_MODEL`, `APP_CORS_ALLOWED_ORIGINS`, `JPA_DDL_AUTO`, `DB_POOL_SIZE`. See `docs/OWNER_GUIDE.md` for what each does.

**Check it**

```bash
curl https://arjunrajawat-tripmindai.hf.space/actuator/health     # {"status":"UP",...}
curl https://arjunrajawat-tripmindai.hf.space/api/origins         # list of cities
```

**Sleep.** Free Spaces pause after 48 hours without traffic. `.github/workflows/keep-alive.yml` pings the health check every 12 hours (and can be run by hand from the Actions tab). An external pinger (UptimeRobot, cron-job.org) on the same URL works too. Use a timeout of at least 120 seconds, because a sleeping Space takes time to start.

## 2. Database: Supabase

1. Create a Supabase project (any region; Mumbai `ap-south-1` is closest for Indian users).
2. Project → **Connect** → choose **Session pooler** (not "Direct connection"). The host must end in `pooler.supabase.com`.
3. Put the URI in the Space secret `DATABASE_URL`, with the password filled in. URL-encode special characters (`@`→`%40`, `#`→`%23`, `/`→`%2F`) or use a letters-and-numbers password.

The app converts `postgres://` / `postgresql://` URLs to JDBC itself (`config/DatabaseUrlEnvironment.java`) and adds `sslmode=require` for remote hosts. Tables are created and updated automatically (`JPA_DDL_AUTO=update`).

Why not "Direct connection": Supabase's direct host is IPv6-only, and Hugging Face Spaces can't reach IPv6. The app then crashes at startup with `java.net.SocketException: Network is unreachable`.

Without `DATABASE_URL` the API uses an in-memory H2 database, which is fine for testing but loses everything on restart.

## 3. Website: Vercel (or Netlify)

- **Vercel:** import the GitHub repo, set the root directory to `frontend`, with no build command. `frontend/vercel.json` holds headers and clean URLs.
- **Netlify:** same idea: base directory `frontend`, no build command, publish directory `frontend`.
- After moving to a new domain, add it to the Space secret `APP_CORS_ALLOWED_ORIGINS` (comma-separated, include `https://`).

Netlify can host the website but **not** the API: Netlify runs static files and short serverless functions, not a long-running Java server.

## 4. Alternatives for the API

### Render (blueprint included)
`render.yaml` creates a Docker web service plus a PostgreSQL database. Render dashboard → New → Blueprint → select the repo. Then set `GEMINI_API_KEY` and `APP_CORS_ALLOWED_ORIGINS`, and read the generated `ADMIN_PASS` under Environment. Notes:
- The free web service sleeps after 15 minutes idle and has 512 MB RAM (the JVM flags in the root `Dockerfile` are tuned for this).
- Render's free PostgreSQL expires after 30 days, so prefer Supabase via `DATABASE_URL`.
- If you switch, change `REMOTE_API` in `frontend/js/config.js` and the URL in `.github/workflows/keep-alive.yml`.

### Docker anywhere (VPS, Railway, Fly.io, Cloud Run)
The root `Dockerfile` builds the API **and** serves the website from the same container:

```bash
docker build -t tripmind .
docker run -p 8080:7860 -e ADMIN_PASS=... -e DATABASE_URL=... -e GEMINI_API_KEY=... tripmind
```

Or locally with PostgreSQL: `ADMIN_PASS=something docker compose up -d --build`, then open http://localhost:8080.

## 5. Environment variables (full list)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `7860` | HTTP port |
| `DATABASE_URL` | none | Platform-style Postgres URL; converted to JDBC automatically |
| `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_DRIVER`, `JPA_DIALECT` | in-memory H2 | Explicit JDBC settings (override `DATABASE_URL`) |
| `JPA_DDL_AUTO` | `update` | Hibernate schema mode |
| `DB_POOL_SIZE` | `5` | Max database connections |
| `ADMIN_USER` / `ADMIN_PASS` | `admin` / `change_this_in_production` | Admin API credentials (HTTP Basic). A warning is logged if the password is the default or short. |
| `GEMINI_API_KEY` | none | Enables AI destination discovery |
| `GEMINI_MODELS` | `gemini-flash-lite-latest,gemini-3.1-flash-lite,gemini-3.5-flash` | Model fallback chain |
| `GEMINI_MODEL` | none | A model to try before the chain |
| `GEMINI_TIMEOUT_SECONDS` | `80` | Cap for the whole AI call |
| `APP_CORS_ALLOWED_ORIGINS` | local dev URLs, `tripmind-ai.vercel.app`, `travelai-frontend.vercel.app`, `tripmind-ai.com` | Websites allowed to call the API |
| `FRONTEND_PATH` | `file:../frontend/` | Where the API serves the website from (Docker sets `/app/frontend/`) |
| `H2_CONSOLE_ENABLED` | `false` | Never enable in production |
| `APP_LOG_LEVEL` | `INFO` | Log level for `com.tripmind` |
