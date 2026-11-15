# QA checklist

Run this before every release.

## 1. Automated tests

```bash
cd backend && mvn test
```

All should pass (49 at the time of writing):

| Suite | Covers |
|---|---|
| `TripPlannerTest` | Never over budget across 10 city/budget combinations; cost lines add up; trip type changes the answer; not just the cheapest place; Indore ₹1,000 day trip → Ujjain/Omkareshwar; Kedarnath never in winter; Leh needs 5+ days and starts with rest; monsoon lowers beach scores; day trips ≤4 h; islands by air; tiny budgets get honest suggestions that really work when applied; unknown origins ask instead of guessing; typos and old city names; never your own city; day plan covers every day; year-end prices; seasonal cautions only in season; crowd levels vary; mismatched trip types are reported, not passed off |
| `AiPlaceServiceTest` | Verification keeps real places and drops invented or misplaced ones; sanitising; caching; the prompt carries every rule and strips HTML; AI places are costed by the same rules; AI off → no calls; garbage replies are harmless; `aiStatus` explains failures |
| `ApiTest` | `/plan` happy path and validation; catalogue endpoints; admin requires credentials; formerly public data is no longer exposed; full account lifecycle; form validation; CSV safety |
| `DatabaseUrlEnvironmentTest` | Render and Supabase style `DATABASE_URL` conversion |

Optional live check of the open-data verification (needs internet): `mvn test -Dlive=true`.

## 2. API smoke test (live)

```bash
U=https://arjunrajawat-tripmindai.hf.space
curl -s $U/actuator/health                                  # {"status":"UP",...}
curl -s -o /dev/null -w "%{http_code}\n" $U/api/admin/stats # 401
curl -s -X POST $U/api/plan -H 'Content-Type: application/json' \
  -d '{"origin":"Pune","days":3,"travellers":2,"budget":20000,"vibes":["nature"],"discovery":"hidden"}'
```

In the `/plan` response, check:
- `status` is `ok` or `ok_other`
- `query.engine` is `ai`, and `query.aiStatus` starts with `ok via`
- every recommendation's `cost.total` is ≤ 20000

## 3. Website: desktop

1. **Home:** the hero photo loads. The quick plan with an empty city or budget shows an inline error; filled in, it opens the planner at step 2.
2. **Planner:** steppers and group sync (Just me → 1 traveller). Budget presets and the ₹-per-person-per-day hint. Choose up to 3 trip types (a 4th is refused). Dream-trip idea chips. Famous/hidden choice. The summary card updates.
3. **Results:**
   - The loading steps show. Trips show photo, hidden-gem badge, score, why it suits you, local tip, facts, reasons and cautions, a day plan ("Show all days"), the cost card and budget bar.
   - Save toggles. Share shows a link that reopens the same search. Booking links open in a new tab.
   - Try `budget=3000` (over-budget state with working suggestions), `origin=Atlantis` (asks which city), and a beach search from Jaipur on ₹30,000 for 3 days (honest "no beach trips fit").
4. **Destinations:** filters and search work. "Price a trip here" opens the planner with the place named, and the result prices it.
5. **Saved:** the saved trip is listed. "Open" works offline and "Today's prices" re-runs the search. Remove works.
6. **Accounts:** register (password under 8 characters is refused), log in, the header shows "My trips", the search shows in history, sign out.
7. **Expert planning:** prefilled from results, consent required, success screen, and the request appears in Admin.
8. **Contact:** validation and success. The message appears in Admin.
9. **Admin:** a wrong password is refused. Stats, requests (status change and delete), packages (create and edit), leads CSV export, messages, users, searches.

## 4. Website: phone (390 px wide, or a real phone)

- [ ] Menu button opens a full-screen menu on **every** page; X, Escape and tapping a link close it; the page scrolls normally afterwards
- [ ] No sideways scrolling on any page
- [ ] Buttons and inputs are easy to tap (about 40 px or more)
- [ ] Planner steps, results cards, the cost card and forms are readable without zooming
- [ ] The first search after a long pause shows the "Waking up the server" notice instead of an error

## 5. Accessibility and polish

- [ ] Keyboard only: skip link, menu, planner choices, dialogs (Escape closes), focus is visible
- [ ] Images have descriptive alt text; icons are hidden from screen readers
- [ ] Reduced motion (OS setting): no zoom or slide animations
- [ ] Text contrast is readable in sunlight (body text is dark ink, not light grey)
- [ ] No emoji, gradient text or placeholder content anywhere

## 6. After deploying

- [ ] Health check is UP
- [ ] `aiStatus` shows `ok via …`
- [ ] GitHub → Actions → "Keep backend awake" last run is green
- [ ] Space logs have no `ERROR` at startup
