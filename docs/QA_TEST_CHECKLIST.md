# QA Test Checklist

## A) Backend Automated Tests
Run:
```bash
mvn -f backend/pom.xml test
```

Must pass:
- `ConstraintDecisionEngineTest`
- `ChatControllerConstraintIT`

## B) API Smoke Tests (Manual)
1. Valid hard-constraint case:
   - POST `/api/chat` with `Indore, 1000, 1 day, Spiritual`
   - Expect destination `Ujjain, Madhya Pradesh`
   - Expect `costBreakdown.total <= 1000`

2. Restrictive case:
   - POST `/api/chat` with very low budget and infeasible duration
   - Expect fallback text only

## C) Frontend Smoke (Browser)
1. Open `http://localhost:8080`
2. Chat flow:
   - Fill inputs
   - Confirm "Constraint Preview" updates in real time
   - Submit and verify strict output block format
3. Result page:
   - Verify hard-constraint section and cost rows
   - Verify fallback page behavior (no fake destination details)
4. Saved trips:
   - Save result
   - Open `saved.html`
   - Export and remove item
5. Theme toggle:
   - Verify dark/light persistence after refresh

## D) Admin Smoke
1. Open `admin.html`
2. Login with admin credentials
3. Verify overview tabs load without JS errors

## E) Security Checks
- Ensure `.env.example` has placeholders only
- Ensure `docker-compose.yml` does not include production secrets
- Confirm production secrets are supplied via environment variables

