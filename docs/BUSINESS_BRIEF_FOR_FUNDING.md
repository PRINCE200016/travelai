# TripMind: business brief

## In one line
An AI travel planner for India that starts from your budget: it finds famous places and hidden gems anywhere in the country, checks every suggestion is real, and only shows trips whose full door-to-door cost fits.

## The problem
- Travel apps and chatbots suggest places without checking what the trip will actually cost from where you live, or whether it's reachable in the days you have.
- General AI assistants confidently invent details, prices and sometimes places.
- Indian travellers plan around a fixed budget and short holidays (long weekends, 3–5 day breaks), so a beautiful but unaffordable or unreachable suggestion is useless.
- Lesser-known destinations are hard to discover and harder to trust.

## What TripMind does
1. **Understands the traveller:** where from (any town), when, how long, who's going, total budget, trip style, weather, crowds, pace, and a free-text "describe your dream trip".
2. **Discovers widely:** an AI proposes places across India, mixing well-known destinations and offbeat ones.
3. **Verifies:** every suggestion is checked against open map data (Wikipedia, GeoNames, OpenStreetMap). Places that can't be confirmed are dropped. Weather comes from real records for that month.
4. **Costs everything:** transport (bus, train or flight, chosen for the distance and the value of time), stay for the group, food, local transport, activities and a buffer, adjusted for season.
5. **Recommends honestly:** only trips within budget, ranked by fit. When nothing fits, it says so and offers concrete fixes (exact budget needed, fewer days, cheaper comfort). It never changes the traveller's numbers silently.
6. **Helps them go:** a day-by-day outline, booking links, saved and shareable plans, and an optional ₹199 expert-written itinerary.

## Why it's different
- **AI for discovery, rules for money:** the model widens the search; prices and feasibility come from TripMind's own transparent cost model.
- **Verification layer:** suggestions are confirmed on open map data before anyone sees them.
- **Hidden gems with confidence:** each comes with a real photo, real weather and a local tip.
- **Budget-first output:** every result shows the full cost breakdown against the traveller's budget.

## Revenue
- Expert-planned itineraries (₹199 per trip, live)
- Affiliate commissions on stays, trains, buses and flights (booking links are already in place; affiliate IDs to be added)
- Premium planning (multi-city trips, price alerts, offline plans): planned
- Partnerships with homestays and local operators at hidden-gem destinations: planned
- B2B planning API for agencies: planned

## Current status (November 2026)
- Live product: website plus API, with AI discovery working against real data
- Built on free tiers (Hugging Face, Supabase, Vercel, Gemini), so running costs are near zero at current scale
- 49 automated tests covering the product rules
- Admin console for handling expert requests and enquiries
- **Not yet measured:** user numbers, conversion and revenue. The first launch will establish these baselines.

## Metrics to track from launch
- Searches per visitor; share of searches answered by AI vs the fallback catalogue
- Share of searches with at least one fitting trip
- Saves, shares and expert-planning requests per 100 searches
- Affiliate click-through and bookings
- Returning users and repeat searches

## Use of funds (template)
- **Product:** affiliate integration, payments, multi-city planning, more verified data on local costs
- **Growth:** destination content for search, partnerships with creators and local operators
- **Infrastructure:** paid hosting without sleep, monitoring, backups, higher AI quotas
- **Team:** one full-stack engineer and one growth and partnerships lead
