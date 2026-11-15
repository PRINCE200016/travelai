# TripMind API

Base URL: `https://arjunrajawat-tripmindai.hf.space/api` (set in `frontend/js/config.js`). Locally: `http://localhost:7860/api`.

All bodies are JSON. Every error is `{ "error": "message for people" }` with a 4xx/5xx status. No stack traces are ever returned.

- **User auth:** `Authorization: Bearer <token>` (from login/register). Optional on most routes; it links searches to the account.
- **Admin auth:** HTTP Basic (`ADMIN_USER` / `ADMIN_PASS`) on every `/api/admin/**` route.
- **Rate limits (per IP, per minute):** `POST /plan` 15, `/auth/*` 10, contact / itinerary / leads 5, admin 120. Over the limit → `429` with `Retry-After: 60`.

---

## Planner

### `POST /api/plan`

```json
{
  "origin": "Pune",                     // required: any city, town or village in India
  "startDate": "2026-12-19",            // optional ISO date (today .. 18 months). Missing = 3 weeks from today
  "days": 3,                            // 1..21, total days away including travel
  "travellers": 2,                      // 1..20
  "group": "couple",                    // solo | couple | family | friends
  "budget": 20000,                      // required, total INR for everyone, door to door
  "vibes": ["nature", "relax"],         // up to 3 of: relax adventure spiritual nightlife heritage nature beach hills food wildlife
  "weather": "any",                     // cold | mild | warm | any
  "crowd": "quiet",                     // quiet | any | lively
  "pace": "balanced",                   // easy | balanced | packed
  "comfort": "auto",                    // auto | budget | standard | premium
  "discovery": "mix",                   // mix | hidden | famous
  "notes": "quiet place with waterfalls", // optional, max 300 chars, passed to the AI
  "include": "goa"                      // optional destination id to price even if it isn't a top match
}
```

**Response**

```json
{
  "status": "ok",
  "message": "3 trips fit ₹20,000 for 2 travellers.",
  "query": {
    "origin": "Pune", "originLabel": "Pune", "originKnown": true,
    "startDate": "2026-12-05", "dateAssumed": true, "month": "December",
    "days": 3, "travellers": 2, "group": "couple", "budget": 20000,
    "vibes": ["nature"], "weather": "any", "crowd": "quiet", "pace": "balanced", "comfort": "auto",
    "engine": "ai",                     // ai | catalog (fallback)
    "aiAvailable": true,
    "aiStatus": "ok via gemini-flash-lite-latest (8 of 8 verified, 13781 ms)"
  },
  "recommendations": [ Trip ],          // up to 3, all within budget, best first
  "stretch": [ Trip ],                  // better matches that cost more, or closest options when nothing fits
  "suggestions": [ { "label": "Raise the budget to ₹24,500 for Amboli", "patch": { "budget": 24500 } } ],
  "originSuggestions": ["Pune", "Puri"],  // only for origin_unknown
  "requested": Trip,                    // only with include, when that place is reachable
  "requestedNote": "Kedarnath is closed to visitors in January."   // only with include, when it isn't
}
```

| `status` | Meaning |
|---|---|
| `ok` | Trips fit the budget and match the requested trip types |
| `ok_other` | Trips fit the budget, but none offer the requested trip types; `stretch` shows the matching places and their real cost |
| `over_budget` | Nothing fits; `stretch` = closest options, `suggestions` = budget / days / comfort changes that would work |
| `no_match` | Nothing reachable and enjoyable in the days given; suggests a longer trip |
| `origin_unknown` | Starting point not found; see `originSuggestions` |

**`Trip`**

```json
{
  "id": "ai-amboli-mah", "name": "Amboli", "state": "Maharashtra",
  "lat": 15.96, "lon": 73.99,
  "image": "https://...", "imageAlt": "...", "summary": "...",
  "kind": "offbeat",                    // famous | offbeat (hidden gem) | popular
  "source": "ai",                       // ai | catalog
  "narrative": "Why it suits you (AI-written)",
  "localTip": "Visit the lesser-known Nangartas waterfall early in the morning...",
  "tags": ["nature", "hills"], "matchScore": 99,
  "matchReasons": ["Strong for nature.", "A hidden gem: far fewer visitors than the usual circuit."],
  "cautions": ["Expect crowds and book stays early."],
  "distanceKm": 250,
  "transport": { "mode": "road", "label": "Overnight state bus", "oneWayHours": 11.6, "overnight": true, "note": "..." },
  "cost": { "transport": 1400, "stay": 2600, "food": 3000, "local": 1300, "activities": 600, "buffer": 450,
            "total": 9300, "perPerson": 4650, "comfort": "budget", "nights": 2, "rooms": 1,
            "stayLabel": "Budget hotel or guesthouse", "seasonLabel": "Regular season rates" },
  "budgetFit": { "budget": 20000, "remaining": 10700, "extraNeeded": 0, "status": "fits" },   // fits | tight | over
  "weather": { "month": "December", "lowC": 17, "highC": 29, "label": "Warm", "source": "climate" },
  "crowd": { "level": "low", "reasons": ["weekdays only, so quieter than weekends"] },
  "bestMonths": ["Oct", "Nov", "Dec", "Jan"],
  "highlights": ["..."],
  "dayPlan": [ { "day": 1, "title": "Arrive and explore", "items": ["..."] } ],
  "bookingLinks": { "stay": "https://www.booking.com/...", "transport": "https://...", "map": "https://www.google.com/maps/..." }
}
```

### `GET /api/origins`
Known starting cities, for autocomplete. Any other town also works in `/plan`.

### `GET /api/destinations`
The built-in catalogue (40 places): `id, name, state, image, imageAlt, summary, tags, bestMonths, terrain, dailyCostFrom`.

### `GET /api/destinations/{id}`
One catalogue place, plus `highlights, cautions, highC, lowC, railhead, airport`. `404` if unknown.

### `GET /api/forecast?lat=&lon=&date=YYYY-MM-DD&days=3`
Live daily forecast (Open-Meteo) when the trip starts within ~15 days, else `{"days": []}`. Returns `{ "days": [{ "date", "minC", "maxC", "rainChance", "desc" }] }`.

### `GET /api/destinations/{id}/forecast?date=&days=`
The same, for a catalogue place.

---

## Accounts

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/auth/register` | `{ name, email, password }` (password 8–72 chars) | `{ id, name, email, createdAt, token }`; `409` if the email exists |
| POST | `/api/auth/login` | `{ email, password }` | same; `401` on wrong details |
| POST | `/api/auth/logout` | none (Bearer) | `{ status: "signed_out" }` |
| GET | `/api/auth/me` | none (Bearer) | `{ id, name, email, createdAt }`; `401` if not signed in |
| GET | `/api/me/plans` | none (Bearer) | The 30 most recent searches: `[{ id, createdAt, status, topDestination, topDestinationId, total, query }]` |

Sessions last 30 days. Tokens are stored only as SHA-256 hashes.

## Public forms

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/itinerary-request` | `{ name, email, phone?, destination, startDate?, days?, travellers?, budget?, preferences?, notes? }` | `{ id, status: "pending" }` |
| POST | `/api/contact` | `{ name, email, phone?, message }` | `{ status: "received" }` |
| POST | `/api/leads` | `{ name, phone, budget?, destinationInterest? }` | `{ status: "received" }` |
| GET | `/api/packages`, `/api/packages/{id}` | none | Packages (read-only) |

Expert planning costs ₹199, payable only after an expert confirms. No payment is taken through the API.

## Admin (`/api/admin/**`, HTTP Basic)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/admin/stats` | `{ users, plans, itineraryRequests, pendingRequests, contactUnread, leads, topDestinations: [{destination, count}] }` |
| GET | `/api/admin/itinerary-requests` | Newest first |
| PUT | `/api/admin/itinerary-requests/{id}/status` | `{ status }`: `pending` · `in_progress` · `sent` · `closed` |
| DELETE | `/api/admin/itinerary-requests/{id}` | `204` |
| GET | `/api/admin/contact` | Messages |
| PUT | `/api/admin/contact/{id}/status` | `{ status }`: `unread` · `read` · `replied` |
| DELETE | `/api/admin/contact/{id}` | `204` |
| GET | `/api/admin/users` | Passwords are never serialised |
| GET | `/api/admin/plans` | The 500 most recent searches |
| GET | `/api/admin/leads` | Leads |
| GET | `/api/admin/leads/export` | CSV (UTF-8 with BOM, formula-injection safe) |
| GET | `/api/admin/payments` | Payment records |
| GET / POST | `/api/admin/packages` | List all / create `{ title, price, imageUrl, description, featured }` |
| PUT / DELETE | `/api/admin/packages/{id}` | Update / delete |

## Health

`GET /actuator/health` → `{"status":"UP"}`. It also checks the database connection. `GET /` on the Hugging Face Space returns a short JSON status.
