# Launch status

Status as of 15 November 2026. ✅ done · ⚠️ needs action · ⏳ not started

## Product
- ✅ AI destination discovery (famous + hidden gems) with map verification, real weather and photos; live on Hugging Face
- ✅ Planner: transport, feasibility, full door-to-door costing, scoring; budget never exceeded in recommendations
- ✅ Honest states: over budget, no matching trip type, unknown city, unreachable
- ✅ Shareable results URLs, saved trips, account history, expert planning requests, contact form
- ✅ Admin console (requests, messages, users, searches, packages, leads CSV, payments)

## Design and usability
- ✅ New design system and every page rebuilt
- ✅ Mobile: full-screen menu works on all pages, no sideways scrolling, larger tap targets, smaller photos on phones
- ✅ Keyboard focus, alt text, reduced motion, readable contrast

## Security
- ✅ Admin routes need credentials; public endpoints are create-only and validated
- ✅ Hashed session tokens, bcrypt passwords, rate limits, HTML escaping, safe CSV export
- ⚠️ Confirm `ADMIN_PASS` is set to a strong value on the Space

## Infrastructure
- ✅ Backend on Hugging Face Space; database on Supabase (Session pooler)
- ✅ Keep-alive GitHub Action every 12 hours
- ✅ Automated tests: 49 passing
- ⚠️ Make sure no `GEMINI_MODEL` secret points at an overloaded model (leave it unset)
- ⏳ Error alerting (for example UptimeRobot email when the health check fails)
- ⏳ Database backups beyond Supabase's free-tier defaults

## Legal and content (owner decisions)
- ⚠️ Confirm refund and dispute wording in `terms.html`
- ⚠️ Replace the personal support email with a domain address when available
- ⚠️ Confirm the domain (`tripmind-ai.com`) used in canonical links and the sitemap
- ⏳ Analytics ID (optional; loads only after consent)
- ⏳ Payment collection for expert planning (currently a payment link sent manually after confirmation)

## Before announcing
1. Run `QA_TEST_CHECKLIST.md` sections 2–4 on the live site, including a real phone.
2. Do one expert-planning request end to end and reply to it.
3. Check the Space logs for startup errors.
