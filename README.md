# Commission Quote

Lending staff enter loan details and get a commission quote from a third-party vendor's Commission Quote API.
The vendor API isn't built yet, so this repo includes a mock of it that follows the agreed contract.

```
 browser ──► quote-ui ──────────► lending-quote-service ──────────► mock-vendor
            React                  Spring Boot (BFF)                 Spring Boot
            :3000                  :8080                             :8081

            holds no secrets       checks the staff JWT (stg/prd)    checks the api-key header
            (anything in the       adds the vendor api-key           randomly fails and slows down
            browser is public)     validates, times out,             (stand-in for the real vendor)
                                   maps errors, audits
```

The browser never talks to the vendor. The vendor's `api-key` exists only in `lending-quote-service`, which is
the reason the service exists: it is a backend-for-frontend (BFF) that keeps the secret server-side and turns
an unreliable third party into clear, safe responses for the UI.

## Scope and priorities

About 4 hours, spent in priority order. P0 is the brief, P1 is what a production integration needs, P2 is next
(details in [Known limitations and next steps](#known-limitations-and-next-steps)).

| Priority | Item | Status |
|---|---|---|
| **P0 – brief** | Form (amount, term, risk band), Generate Quote, quote display | ✅ |
| | Loading and error states | ✅ |
| | Mock vendor API: `api-key` required, fails randomly | ✅ |
| | `api-key` never exposed to the browser (BFF) | ✅ |
| | Validation of invalid numbers and limits, in the UI and enforced by the service | ✅ |
| | Vendor timeouts and errors handled | ✅ |
| | Core tests and edge cases | ✅ |
| | README with run instructions and AI usage | ✅ |
| **P1 – production** | Circuit breaker on the vendor | ✅ |
| | Staff auth on the service: JWT + role | ✅ |
| | Correlation id and audit log | ✅ |
| | dev / stg / prd config; missing config stops startup | ✅ |
| | Docker Compose, CI per app, e2e tests, 95% coverage gate | ✅ |
| **P2 – next** | UI login (BFF-managed session cookie) | ✖️ |
| | Idempotency key (no duplicate quotes) | ✖️ |
| | Persist quotes, `GET /api/commission-quotes/{id}` | ✖️ |
| | Metrics and alerts | ✖️ |
| | Contract testing (OpenAPI / Pact) | ✖️ |
| | CD: image scanning, push to a registry, deploy to stg/prd | ✖️ |
| | Browser e2e (Playwright), mutation testing | ✖️ |
| | Feature flag for the real vendor, load tests (k6) | ✖️ |

## Setup

| Tool | Version | Needed for | Check |
|---|---|---|---|
| Docker Desktop | Compose 2.20+ | the quick start and the e2e tests | `docker compose version` |
| JDK | 17+ | running or testing the Spring apps without Docker (Gradle downloads a JDK 17 if missing) | `java -version` |
| Node.js | 22 | running or testing the UI without Docker | `node -v` |

```bash
git clone https://github.com/xiaomogu1/commission-quote.git
cd commission-quote
```

Nothing else is needed: Gradle comes with each Java project (`./gradlew`), and the only key is a local-only one for
the mock vendor (`.env.example`).

## Quick start

From the repo root (`commission-quote/`):

```bash
docker compose down         # in case an earlier run is still holding the ports
docker compose up --build
```

Open **http://localhost:3000**. Try `500000` / `360` months / Medium → `0.60%`, `$3,000.00`.

The mock vendor fails about 1 call in 5 on purpose. Other behaviours can be switched on at run time:

```bash
VENDOR_FAILURE_RATE=1 docker compose up                                  # vendor always fails (circuit opens after 10)
VENDOR_MIN_LATENCY_MS=5000 VENDOR_MAX_LATENCY_MS=5000 docker compose up  # vendor slower than our 3s timeout -> 504
```

Stop with `docker compose down`.

## Running each application without Docker

```bash
cp .env.example .env      # git-ignored; the local-only key shared by the service and the mock vendor

cd mock-vendor            && ./gradlew bootRun     # :8081
cd lending-quote-service  && ./gradlew bootRun     # :8080
cd quote-ui               && npm install && npm run dev   # :5173, proxies /api to :8080
```

Both Spring apps read `.env` from the repo root and refuse to start without the key.

`lending-quote-service` can also run on its own with a WireMock stub instead of the mock vendor:

```bash
cd lending-quote-service && docker compose -f docker/docker-compose.yml up --build
```

The stub has fixed triggers for trying error states: `loanAmount 4040.40` → vendor 500, `5040.40` → vendor timeout.

## Tests

| Project | Command | What runs |
|---|---|---|
| `lending-quote-service` | `./gradlew clean build` | unit → component → black-box tests, then coverage gate (stops at the first level that fails) |
| `mock-vendor` | `./gradlew clean build` | unit + component tests |
| `quote-ui` | `npm test`, `npm run lint`, `npm run build` | component tests (Vitest + Testing Library), lint, type check |
| `e2e-tests` | `./gradlew clean test` | the whole system from `docker-compose.yml` (needs Docker, ports 3000/8080/8081 free) |

### lending-quote-service test levels

| Level | Folder | Run alone | What it covers |
|---|---|---|---|
| Unit | `src/test/unit` | `./gradlew unitTest` | mappers, service, filters, interceptors, role mapping, status classification; no Spring |
| Component | `src/test/component` | `./gradlew componentTest` | `@SpringBootTest`: API validation and error mapping, vendor client against WireMock (every status, timeouts, bad bodies), circuit breaker, JWT roles, each environment profile starting for real |
| Black-box | `src/test/blackbox` | `./gradlew blackBoxTest` | the app on a real port called over HTTP like the browser, vendor replaced by WireMock; responses read as raw JSON |

Coverage (JaCoCo, all three levels combined) must be at least 95% for instructions, lines, branches, methods,
classes and complexity, or the build fails. Generated code (Lombok, MapStruct, the `main` class) is excluded.
Report: `lending-quote-service/build/reports/jacoco/test/html/index.html`.

## How it handles the key cases

**Invalid input.** The UI uses the browser's own validation (`required`, `min`, `max`, `step`) for instant feedback.
The service validates again and is the authority: anything outside the limits gets a `400` with per-field messages,
and the vendor is never called. Integer fields reject `12.5` instead of silently truncating it.

| Field | Rule |
|---|---|
| `loanAmount` | 1,000 – 5,000,000, at most 2 decimal places |
| `loanTermInMonths` | whole number, 1 – 360 |
| `riskBand` | `LOW`, `MEDIUM` or `HIGH` |

**Vendor failures.** Every outcome is classified once, at the integration edge, and the UI acts on `retryable`:

| Vendor | Service returns | UI shows |
|---|---|---|
| 2xx with a valid quote | `200` | the quote |
| no answer within 3s | `504`, retryable | "took too long", Try again |
| 5xx, 429, connection error | `502`, retryable | "couldn't get a quote right now", Try again |
| circuit open (vendor keeps failing) | `502`, retryable, instantly | same, without waiting for a timeout |
| 401/403, other 4xx | `502`, not retryable | "contact support" + reference |
| 2xx with a missing/negative/unreadable body | `502`, not retryable | "contact support" + reference |

Errors are RFC 9457 `problem+json` with a `correlationId`. Vendor details are logged, never returned.

**Security.**
- The vendor `api-key` is server-side only: never sent to the browser, never logged (excluded from `toString`),
  and the app refuses to start without it. Locally it comes from `.env` (git-ignored); in stg/prd the platform injects
  it from AWS Secrets Manager. The app only reads the `VENDOR_API_KEY` env var and never calls AWS: ECS fetches the
  secret with the task's execution role (allowed to read only that environment's secrets) and passes it to the
  container. Example task definition fragment (not in this repo; it belongs with the infrastructure code):

  ```json
  "secrets": [
    { "name": "VENDOR_API_KEY",
      "valueFrom": "arn:aws:secretsmanager:ap-southeast-2:<account>:secret:prd/lending-quote-service/vendor-api-key" }
  ]
  ```

  Rotating the key = update the secret and redeploy.
- Staff authentication (`app.security.enabled`, on in stg/prd): the service is an OAuth2 resource server; `/api/**`
  needs a JWT with the `LENDING_STAFF` realm role from Keycloak. No token → `401`, wrong role → `403`.
  Off in dev because the UI has no login flow yet (see Known limitations).
- How staff authentication works in stg/prd:

  ```
  staff            quote-ui              Keycloak (SSO)             lending-quote-service
    │ open the page    │                        │                             │
    │─────────────────►│ not signed in: redirect│                             │
    │                  │───────────────────────►│                             │
    │ sign in (company account, MFA)            │                             │
    │──────────────────────────────────────────►│                             │
    │                  │◄─── ① access token ────│  short-lived JWT with the   │
    │                  │     (+ refresh token)  │  staff member's realm roles │
    │ Generate Quote   │                        │                             │
    │─────────────────►│── ② Authorization: Bearer <access token> ──────────►│
    │                  │                        │◄── ③ signing keys (cached) ─│ checks signature, issuer,
    │                  │◄──────────────────── 200 / 401 / 403 ────────────────│ expiry, LENDING_STAFF role
  ```

  Step ③ is implemented and tested (`SecuredQuoteApiComponentTest`). Steps ① and ② (the UI login) are not built
  yet, see Known limitations. The service only validates tokens; it never issues them.
- Every request gets a correlation id (reused from `X-Correlation-Id` if it is safe, so it can't be used for log
  injection), sent to the vendor and returned to the caller.
- An audit log records every quote generated or failed, with the staff member, and without the loan amount.
- Containers run as non-root users.

## API

`POST /api/commission-quotes`

```json
{ "loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM" }
```

`200`
```json
{ "quoteId": "Q-7f3c…", "commissionRate": 0.006, "totalCommission": 3000.00 }
```

`400` / `5xx`
```json
{ "title": "Quote service timed out", "status": 504, "detail": "…Please try again.",
  "retryable": true, "correlationId": "3f1e…", "errors": { "loanAmount": "must be at least $1,000" } }
```

The vendor contract (`POST /v1/commission-quotes` with an `api-key` header) has the same fields.

## Key decisions

| Decision | Why |
|---|---|
| A BFF between the UI and the vendor | The api-key must not reach the browser. It also isolates the UI from the vendor's contract and failures. |
| Mock vendor as its own application | The service treats it exactly like the real vendor (HTTP + api-key), so switching is a `VENDOR_BASE_URL` change. |
| No automatic retry | Creating a quote is a POST and the contract has no idempotency key; a retry after a timeout could create a duplicate. The user retries explicitly. Tests assert the vendor is called only once. |
| Circuit breaker counts only timeouts and 5xx/429 | A 4xx means our key or contract is wrong; failing fast wouldn't help and would hide the cause. The threshold (50% of 10) sits above the mock's normal 20% failure rate. |
| Error handling at the edges, not in the client | RestClient status handlers and an interceptor translate HTTP/network failures; method validation rejects unusable bodies; one exception handler maps everything to HTTP. `VendorClient` itself only says what to call. |
| Separate vendor, domain and API models with MapStruct | The real vendor's data won't match ours one to one. Mapping lives in one place per boundary, and `unmappedTargetPolicy = ERROR` fails the build if a field is forgotten. |
| `VendorClient` is a class, not an interface | There is one implementation; the mock and the real vendor differ only by URL. An interface can be extracted when a second vendor appears. |
| Missing configuration fails at startup | Unresolved placeholders don't fail in Spring (the literal text becomes the value), so required values default to empty and are validated. Profile tests prove each environment refuses to start without them. |
| Spring MVC + RestClient, not WebFlux | One downstream call per request and low concurrency: reactive would add complexity without benefit. |
| Simple UI | One component with browser validation. The assessment's focus is the integration; UI polish was a nice-to-have. |

## Assumptions

The brief defines field names only. Everything else is an assumption to confirm with the vendor:

- Path `POST /v1/commission-quotes`; missing/invalid key → `401`; errors are `problem+json`.
- `commissionRate` is a fraction (`0.006` = 0.60%), not a percentage. A 100× unit mismatch is a classic integration bug, so this is called out explicitly.
- Money is a JSON number; the service uses `BigDecimal` end to end and rounds half-up to cents. The UI only displays amounts.
- Validation limits (above) and the mock's rates: LOW 0.70%, MEDIUM 0.60%, HIGH 0.50%.

## Configuration

| Profile | Used for | Vendor | Staff auth |
|---|---|---|---|
| `dev` (default) | laptops, docker compose | mock vendor, `localhost:8081` | off |
| `stg` | AWS | `VENDOR_BASE_URL`, key from Secrets Manager | on, `OIDC_ISSUER_URI` |
| `prd` | AWS | same as stg; root logging at WARN, audit always INFO | on |

stg and prd have no defaults: a missing `VENDOR_BASE_URL`, `VENDOR_API_KEY` or `OIDC_ISSUER_URI` stops the app at startup.

## Project structure

```
commission-quote/
├── docker-compose.yml          the whole system
├── quote-ui/                   React + TypeScript + Vite
├── lending-quote-service/
│   ├── docker/                 Dockerfile, standalone compose with a WireMock vendor stub
│   ├── gradle/coverage.gradle  JaCoCo report and gates
│   └── src/main/java/com/bendigo/lendingquote/
│       ├── quote/api           controller, request/response, exception handler, mapper
│       ├── quote/service       QuoteService (audit)
│       ├── quote/client        VendorClient, RestClient config, interceptors, vendor wire format, mapper
│       ├── quote/domain        LoanDetails, CommissionQuote, RiskBand
│       ├── quote/exception     VendorTimeout / Unavailable / Rejected
│       ├── security            JWT resource server, Keycloak role mapping
│       └── common              correlation id
├── mock-vendor/                the vendor stand-in
├── e2e-tests/                  end-to-end tests against docker-compose.yml
└── .github/workflows/          one workflow per application, plus e2e
```

## CI (no CD yet)

GitHub Actions, one workflow per application (triggered by changes under its folder) plus e2e:

- `lending-quote-service`: unit → component → black-box → coverage gate → jar → Docker image
- `mock-vendor`: tests → jar → Docker image
- `quote-ui`: install → lint → tests → build
- `e2e`: builds and starts the whole system and runs `e2e-tests`

This is CI only. Docker images are built to prove the Dockerfiles work, but they are not pushed anywhere or
deployed: there is no target environment for this exercise. CD is not done; the plan is under Delivery below.

## Known limitations and next steps

- **Duplicate quotes after a timeout.** If the vendor timed out but had created a quote, a manual retry creates another.
  The real fix is an idempotency key in the vendor contract; on our side, an `Idempotency-Key` on our API (stored in
  Redis so it works across instances) would stop double submits.
- **No login in the UI.** The UI sends no token, so it only works where `app.security.enabled` is off (dev); with
  auth on, every call gets a `401`. Next: let the BFF run the Keycloak login and give the browser only an `HttpOnly`,
  `Secure`, `SameSite` session cookie, so the token never reaches the browser, the same principle as the api-key
  (this brings back CSRF protection, since the API would then use a cookie). Add Keycloak to docker compose so
  staff auth can run end to end locally.
- **Contract testing.** An OpenAPI file for the vendor contract, and bi-directional contract testing (our pact vs the vendor's OpenAPI) once the real API exists.
- **Browser e2e.** Playwright tests of the UI flow; today e2e drives the HTTP API (including through the UI's nginx).
- **Test quality.** Mutation testing (PIT) to check the tests would catch real bugs, not just execute code.
- **Persistence.** If quotes need to be looked up later: Postgres + Spring Data JPA (`quote/repository`), Flyway migrations.
- **Delivery (CD, not done).** On merge to main: build the image once, scan it (Trivy/Snyk), sign it and push it
  to ECR tagged with the git sha; deploy automatically to stg and run the e2e tests as a smoke test; manual approval;
  deploy the same image to prd (only the environment's config and secrets differ); roll back by redeploying the
  previous tag.
- **Operations.** JSON logs, audit events shipped to a dedicated store, vendor latency/error metrics and alerts, circuit breaker state on health.
- **Switching to the real vendor.** A feature flag for a gradual rollout, with shadow traffic to compare results before the cut-over.
- **Performance.** k6 load tests against stg once the real vendor's SLA and rate limits are known.

## AI usage

I used Claude (Anthropic) throughout, as a pair programmer: it generated most of the code, tests, configuration
and this README, and I directed the design and reviewed every change.

- **My decisions:** the overall architecture (UI / BFF / separate mock vendor / standalone e2e), Java 17, naming and
  package structure, MapStruct for the vendor mapping, Lombok, the three test levels and their layout, coverage gates
  across all counters, testing each environment's configuration, a Resilience4j circuit breaker, JWT with role checks,
  Docker layout, and one CI workflow per application.
- **Where I pushed back on the AI:** removing over-engineering it introduced (a secret-provider abstraction, a
  single-implementation client interface, an over-detailed error enum, a configurable mock rate table), simplifying
  the UI, and cutting unnecessary branching in the vendor client.
- **What running it caught** (each fixed and now covered by a test): missing environment variables not stopping startup
  (Spring keeps unresolved placeholders as literal values), Jackson silently truncating `12.5` to `12`, a flaky
  timeout test, a random number API missing from the slim JRE image, nginx unable to read its config as a non-root
  user, and e2e tests being skipped (Testcontainers vs Docker 29) while the build still reported success.

Time spent: about 4 hours of hands-on work, so I scoped it by priority. I prioritised the integration itself: keeping the key secret, handling every way the vendor can
fail, and testing those paths. The P2 items in "Scope and priorities" are what I didn't get to and would
do next. The core feature is in `quote/` and `quote-ui/src/App.tsx`.
