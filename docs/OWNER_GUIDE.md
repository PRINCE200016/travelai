# TripMind owner's guide

Everything you need to run TripMind day to day: where things live, how to log in, how to update the site, and what to do when something breaks.

> **Short version (Hinglish)**
> - Website (frontend) GitHub ke `main` se Vercel par deploy hoti hai. Backend (API) Hugging Face Space `Arjunrajawat/TripMindAi` par chalta hai.
> - Backend badalna ho: `backend/` mein code badlo → `bash scripts/sync-space.sh` → `TripMindAi/` folder se Hugging Face par push. GitHub par bhi push karo.
> - Secrets (Gemini key, admin password, database URL) Hugging Face Space → Settings → Variables and secrets mein hain. Code mein kabhi nahi.
> - AI chal raha hai ya nahi: koi bhi search karo aur response mein `query.aiStatus` dekho.
> - Supabase ka **Session pooler** URL hi use karna, "Direct connection" wala Hugging Face par nahi chalta.

---

## 1. Where everything lives

| What | Where | Notes |
|---|---|---|
| Code | GitHub: `PRINCE200016/travelai` (`main` branch) | Frontend, backend, docs, deploy config |
| Website (frontend) | Vercel project linked to the GitHub repo | Deploys automatically on every push to `main`. CORS currently allows `https://tripmind-ai.vercel.app` and `https://travelai-frontend.vercel.app` |
| Backend (API) | Hugging Face Space: https://huggingface.co/spaces/Arjunrajawat/TripMindAi | Separate git repo, checked out locally as `TripMindAi/` |
| API base URL | https://arjunrajawat-tripmindai.hf.space/api | Set in `frontend/js/config.js` (`REMOTE_API`) |
| Health check | https://arjunrajawat-tripmindai.hf.space/actuator/health | Returns `{"status":"UP"}` when healthy |
| Database | Supabase (PostgreSQL) | Connected through `DATABASE_URL` on the Space |
| AI | Google AI Studio (Gemini API key) | https://aistudio.google.com |
| Keep-alive | GitHub Actions: "Keep backend awake" | Pings the health check every 12 hours |

## 2. Logins and secrets

**Admin panel:** `<your website>/admin.html`
- Username: `admin` (or whatever `ADMIN_USER` is set to)
- Password: the `ADMIN_PASS` secret on the Hugging Face Space. If it was never set, the password is the default `change_this_in_production`, which anyone could guess. Set it.

**Secrets on the Hugging Face Space** (Settings → Variables and secrets). Changing any of them restarts the Space.

| Name | Required | What it is |
|---|---|---|
| `ADMIN_PASS` | Yes | Admin panel password. Use 12+ characters. |
| `GEMINI_API_KEY` | For AI | Google AI Studio key. Without it the planner uses the built-in 40-place list. |
| `DATABASE_URL` | Yes, to keep data | Supabase **Session pooler** connection string, e.g. `postgresql://postgres.<ref>:<password>@aws-0-ap-south-1.pooler.supabase.com:5432/postgres`. Without it, data lives in memory and disappears on every restart. |
| `ADMIN_USER` | No | Admin username (default `admin`) |
| `GEMINI_MODELS` | No | Comma-separated model fallback chain. Default `gemini-flash-lite-latest,gemini-3.1-flash-lite,gemini-3.5-flash` |
| `GEMINI_MODEL` | No | One model to try first. **Leave unset** unless you have a reason; a slow model here slows every search. |
| `APP_CORS_ALLOWED_ORIGINS` | No | Comma-separated website URLs allowed to call the API. Set it if you move to a new domain (e.g. Netlify or a custom domain). |

Never put secrets in code, commits or chat messages.

## 3. Updating the site

### Website (HTML/CSS/JS in `frontend/`)
1. Edit files in `frontend/`.
2. Check locally (see section 6).
3. Commit and push to GitHub `main`. Vercel redeploys within a minute or two.

### Backend (Java in `backend/`)
1. Edit files in `backend/`, then run the tests: `cd backend && mvn test`.
2. Copy the backend into the Space repo: `bash scripts/sync-space.sh`.
3. Deploy: `cd TripMindAi && git add -A && git commit -m "what changed" && git push origin main`.
   Hugging Face asks for a username and password the first time: username `Arjunrajawat`, password = a **Write** token from https://huggingface.co/settings/tokens (your normal password is rejected).
4. Push the same change to GitHub from the repo root, so both stay in step.
5. Wait 2–5 minutes, then open the health check.

### Destinations and starting cities
The fallback catalogue is plain JSON, no code needed:
- `backend/src/main/resources/catalog/destinations.json` (40 destinations with climate, seasons, access, costs, highlights)
- `backend/src/main/resources/catalog/origins.json` (~70 starting cities; any other town is found on the map automatically)

## 4. Is the AI working?

Run any search on the site, or:

```bash
curl -s -X POST https://arjunrajawat-tripmindai.hf.space/api/plan \
  -H 'Content-Type: application/json' \
  -d '{"origin":"Pune","days":3,"travellers":2,"budget":20000,"vibes":["nature"]}'
```

Look at `query.engine` and `query.aiStatus` in the response:

| `aiStatus` | Meaning | What to do |
|---|---|---|
| `ok via <model> (7 of 8 verified, 11000 ms)` | AI found places and most were confirmed on the map | Nothing |
| `ok (cached, …)` | Same search in the last 6 hours, answered from cache | Nothing |
| `off` | No `GEMINI_API_KEY` on the Space | Add the key |
| `model_failed: … http_429 …` | Daily quota used up | Wait, or use a paid key |
| `model_failed: … timeout …` / `http_503` | Google's models are overloaded | Usually temporary; the client already falls back across models |
| `model_failed: … http_404 …` | A model in the chain was retired | Update `GEMINI_MODELS` (list available models in AI Studio) |
| `unreadable_reply` | The model returned something that wasn't the expected JSON | Retry; if constant, try another model in `GEMINI_MODELS` |
| `too_few_verified: …` | Fewer than 3 suggestions could be confirmed on the map | Usually a one-off; the catalogue is used instead |

When AI isn't used, the results page tells visitors that the trips come from hand-picked destinations.

## 5. When something breaks

| Symptom | Likely cause | Fix |
|---|---|---|
| Space shows "Runtime error" with `Network is unreachable` | `DATABASE_URL` uses Supabase's **direct** host (`db.xxxx.supabase.co`), which is IPv6-only; Spaces have no IPv6 | Use the **Session pooler** string (host ends in `pooler.supabase.com`). Or delete `DATABASE_URL` to run on in-memory data meanwhile. |
| Space "Runtime error" with `password authentication failed` | Wrong database password, or special characters not encoded | Reset the Supabase DB password to letters and numbers, or URL-encode `@`→`%40`, `#`→`%23`, `/`→`%2F` |
| Users, requests and searches vanish after a restart | No `DATABASE_URL`, so data is in memory | Add the Supabase Session pooler URL |
| First search after a quiet spell is slow | The free Space was asleep and is starting (up to a minute or two) | Normal. Keep-alive prevents the 48-hour sleep. The site shows a "waking up" message. |
| Space paused ("Sleeping") | No traffic for 48 hours (keep-alive stopped) | Open the Space page to wake it. GitHub disables scheduled Actions after 60 days without repo activity: GitHub → Actions → "Keep backend awake" → Enable / Run workflow. |
| Website loads but every search fails | Backend down, or the site is on a domain not allowed by CORS | Check the health URL; add the domain to `APP_CORS_ALLOWED_ORIGINS` |
| `git push` to Hugging Face says password authentication is not supported | Git used your account password | Use a **Write** access token as the password. If Windows keeps sending an old one: Control Panel → Credential Manager → Windows Credentials → remove `git:https://huggingface.co` |
| Admin panel says the username or password is incorrect | `ADMIN_PASS` differs from what you typed | Check or reset the secret on the Space (it restarts) |
| Expert-planning emails don't arrive | EmailJS notification failed (it is best-effort) | Requests are still saved; check Admin → Itinerary requests |

## 6. Running it on your computer

Needs Java 17 and Maven.

```bash
cd backend
mvn spring-boot:run        # http://localhost:7860 serves the API and the website from ../frontend
```

Optional, to try AI locally: set `GEMINI_API_KEY` in your environment first. Admin locally: `ADMIN_PASS=something mvn spring-boot:run`.

## 7. Free-tier limits to know

| Service | Free limit that matters |
|---|---|
| Hugging Face Space (CPU basic) | Sleeps after 48 h without traffic; restarts lose in-memory data |
| Supabase | 500 MB database; pauses projects after about a week without database activity. The keep-alive health check queries the database, so it also keeps Supabase active. |
| Gemini API | Per-minute and per-day request quotas on the free tier; repeated searches are cached for 6 hours |
| Open-Meteo / Wikipedia / OpenStreetMap | Free and keyless; OpenStreetMap is limited to 1 request per second, which the app respects |
| GitHub Actions | Scheduled workflows pause after 60 days without commits |

## 8. Open decisions

These were written or chosen during the rebuild and need your confirmation:
- Refund and dispute terms in `frontend/terms.html` (full refund before work starts, corrections within 7 days, disputes in Indore courts).
- Support email: currently `arjunrajawat28@gmail.com` (`frontend/js/config.js`). Replace with a domain address when you have one.
- Domain: canonical links and the sitemap use `https://tripmind-ai.com`.
- Google Analytics: set `window.__TRIPMIND_GA_ID__` in the HTML before `config.js` if you want analytics. It loads only after visitors accept analytics cookies.
- Expert planning price: ₹199, payable after confirmation. No payment is taken on the site.
