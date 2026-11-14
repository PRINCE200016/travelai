# TripMind API (v2)

Base URL: `/api` (see `frontend/js/config.js`). All bodies are JSON. Errors return `{ "error": "human readable message" }` with a 4xx/5xx status.

Auth for users: `Authorization: Bearer <token>` (token from login/register).
Auth for admin: HTTP Basic (`ADMIN_USER` / `ADMIN_PASS`) on every `/api/admin/**` route.

---

## Planner

### `POST /api/plan`
Ranks every destination in the catalogue for this traveller and returns the best fits that are fully costed.

Request
```json
{
  "origin": "Pune",                 // required: any city, town or village in India (known cities matched instantly, others via the map)
  "startDate": "2026-12-19",        // optional ISO date; drives season pricing, weather & crowds
  "days": 4,                        // 1..21 (total days including travel)
  "travellers": 2,                  // 1..20
  "group": "couple",                // solo | couple | family | friends
  "budget": 30000,                  // total INR for the whole group, door to door
  "vibes": ["hills", "relax"],      // 0..3 of: relax adventure spiritual nightlife heritage nature beach hills food wildlife
  "weather": "any",                 // cold | mild | warm | any
  "crowd": "any",                   // quiet | any | lively
  "pace": "balanced",               // easy | balanced | packed
  "comfort": "auto",                // auto | budget | standard | premium
  "discovery": "mix",              // mix | hidden | famous
  "notes": "quiet village by a river", // optional, max 300 chars: the traveller's own words, passed to the AI
  "include": "goa"                  // optional destination id to price even if it isn't a top match
}
```

Response
```json
{
  "status": "ok",                   // ok | ok_other | over_budget | no_match | origin_unknown
  "message": "3 trips fit ₹30,000 for 2 travellers.",
  "query": { "...normalised request...", "originLabel": "Pune", "originKnown": true, "month": "December" },
  "recommendations": [ Trip ],      // status=ok: up to 3, all with cost.total <= budget, best match first
  "stretch": [ Trip ],              // status=over_budget: closest matches you could afford with a bit more
  "requested": Trip,                // when include was sent and the place is reachable
  "requestedNote": "…",              // when include was sent but the place isn't possible (closed, too far…)
  "originSuggestions": ["Pune"],     // status=origin_unknown: closest known cities
  "suggestions": [                  // concrete one-click re-plans, never applied automatically
    { "label": "Raise budget to ₹34,000", "patch": { "budget": 34000 } },
    { "label": "Plan 3 days instead of 4", "patch": { "days": 3 } }
  ]
}
```

`Trip`
```json
{
  "id": "rishikesh", "name": "Rishikesh", "state": "Uttarakhand",
  "image": "https://images.unsplash.com/...", "imageAlt": "…",
  "summary": "…", "tags": ["spiritual","adventure","nature"],
  "matchScore": 86, "matchReasons": ["…"], "cautions": ["…"],
  "distanceKm": 240,
  "transport": { "mode": "road|train|flight", "label": "Overnight train", "oneWayHours": 6.5, "note": "…" },
  "cost": { "transport": 4200, "stay": 9600, "food": 5600, "local": 1800, "activities": 3000,
            "total": 24200, "perPerson": 12100, "comfort": "standard", "nights": 3, "rooms": 1,
            "seasonLabel": "Peak season (+25%)" },
  "budgetFit": { "budget": 30000, "remaining": 5800, "extraNeeded": 0, "status": "fits|tight|over" },
  "weather": { "month": "December", "lowC": 8, "highC": 21, "label": "Cool, clear days", "source": "climate" },
  "crowd": { "level": "low|moderate|high", "reasons": ["…"] },
  "bestMonths": ["Oct","Nov","Feb","Mar"],
  "highlights": ["…"],
  "dayPlan": [ { "day": 1, "title": "Arrive and settle in", "items": ["…"] } ],
  "bookingLinks": { "stay": "https://www.booking.com/...", "transport": "https://..." }
}
```

Statuses: `ok`: trips fit and match the requested trip types. `ok_other`: trips fit the budget but none offer the requested trip types; `stretch` holds matching places with their real cost. `over_budget`: nothing fits; `stretch` shows closest options. `no_match`: nothing reachable in the days given. `origin_unknown`: the starting city couldn't be placed.

How places are found: with `GEMINI_API_KEY` set, the AI proposes ~12 places anywhere in India (famous and hidden). Each is verified on open map data (Wikipedia, Open-Meteo/GeoNames, then OpenStreetMap as a last resort), given last year's observed weather for the travel month, then costed and filtered by the same rules as catalogue places. `query.engine` is `ai` or `catalog` (fallback). AI trips carry `kind` (famous | offbeat | popular), `localTip`, `narrative` (why it suits you), `source`, `lat`, `lon`.

### `GET /api/forecast?lat=&lon=&date=YYYY-MM-DD&days=3` — live daily forecast (Open-Meteo, no key) when the trip starts within ~15 days.
### `GET /api/destinations/{id}/forecast?date=YYYY-MM-DD&days=3` — live daily forecast when the trip starts within ~5 days (empty otherwise).
### `GET /api/destinations` — catalogue summary list (`id, name, state, image, imageAlt, summary, tags, bestMonths, dailyCostFrom`).
### `GET /api/destinations/{id}` — one catalogue entry with highlights and climate by month.
### `GET /api/origins` — `["Ahmedabad", "Bengaluru", ...]` for autocomplete.

---

## Accounts
- `POST /api/auth/register` `{ name, email, password }` → `{ id, name, email, token }` (password ≥ 8 chars)
- `POST /api/auth/login` `{ email, password }` → same shape
- `POST /api/auth/logout` (Bearer)
- `GET /api/auth/me` (Bearer) → `{ id, name, email, createdAt }`
- `GET /api/me/plans` (Bearer) → recent planner searches `[{ id, createdAt, query, topDestination, total }]`

## Requests from visitors
- `POST /api/itinerary-request` `{ name, email, phone, destination, startDate, days, travellers, budget, preferences, notes }` → `{ id, status: "pending" }`
  Expert planning costs ₹199, payable only after an expert confirms the request (no card is taken on the site).
- `POST /api/contact` `{ name, email, phone?, message }` → `{ status: "received" }`
- `GET /api/packages` — public list of featured packages.

## Admin (`/api/admin/**`, HTTP Basic)
- `GET /api/admin/stats` → `{ users, plans, itineraryRequests, pendingRequests, contactUnread, leads, topDestinations: [{destination,count}] }`
- `GET /api/admin/itinerary-requests`, `PUT /api/admin/itinerary-requests/{id}/status` `{status}`, `DELETE /api/admin/itinerary-requests/{id}`
  status ∈ `pending | in_progress | sent | closed`
- `GET /api/admin/contact`, `PUT /api/admin/contact/{id}/status` `{status}`, `DELETE /api/admin/contact/{id}` (status ∈ `unread | read | replied`)
- `GET /api/admin/users`
- `GET /api/admin/plans` — recent planner searches (all users and guests)
- `GET /api/admin/leads`, `GET /api/admin/leads/export` (CSV)
- `GET /api/admin/payments`
- `POST /api/admin/packages`, `PUT /api/admin/packages/{id}`, `DELETE /api/admin/packages/{id}`
