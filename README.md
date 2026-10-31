# Commission Quote

Lets lending staff enter loan details and get a commission quote from a third-party vendor API.

```
quote-ui (React, :5173)  ->  lending-quote-service (:8080)  ->  mock-vendor (:8081)
  no secrets                  holds the api-key                  requires api-key,
                              validates, times out,              randomly fails
                              maps errors                        (stand-in for the real vendor)
```

> Work in progress - sections below are filled in as features land.

## Applications

Three independent applications, each with its own build:

| Folder                   | What it is |
|--------------------------|------------|
| `quote-ui/`              | React app staff use (coming next). |
| `lending-quote-service/` | Spring Boot backend-for-frontend. The only thing the browser talks to. Holds the vendor api-key server-side, validates input, applies timeouts and maps vendor errors. |
| `mock-vendor/`           | Spring Boot stand-in for the vendor's Commission Quote API: agreed contract, api-key auth, random failures and latency. |

## Running locally

Prerequisites: JDK 17 (Gradle downloads one if missing).

```bash
cp .env.example .env                                  # git-ignored; local-only shared key

cd mock-vendor && ./gradlew bootRun                   # terminal 1, http://localhost:8081
cd lending-quote-service && ./gradlew bootRun         # terminal 2, http://localhost:8080
```

Try it:

```bash
curl -i -X POST http://localhost:8080/api/commission-quotes \
  -H 'Content-Type: application/json' \
  -d '{"loanAmount": 500000, "loanTermInMonths": 360, "riskBand": "MEDIUM"}'
```

Run it a few times: about 1 in 5 calls fails on purpose (`VENDOR_FAILURE_RATE=0.2`) and you get a 502.

## Tests

```bash
cd mock-vendor && ./gradlew test
cd lending-quote-service && ./gradlew test
```
