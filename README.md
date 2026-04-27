# BYLD Wealth — Portfolio API (Variant B: Dividends)

A backend REST API for tracking investment portfolios, built for the BYLD Wealth internship take-home assignment.

---

## Quick Start (One Command)

```bash
git clone <your-repo-url>
cd byld-portfolio-api
cp .env.example .env
docker compose up --build
```

The API will be available at **http://localhost:8080**

Swagger UI: **http://localhost:8080/swagger-ui**

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 16 (via Docker) |
| Migrations | Flyway |
| Build | Maven |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Tests | JUnit 5 + Mockito + Testcontainers |

---

## Endpoints

### Core

| Method | Path | Description |
|---|---|---|
| POST | `/v1/portfolios` | Create a portfolio |
| GET | `/v1/portfolios/{id}` | Get portfolio summary (cash + holdings) |
| POST | `/v1/portfolios/{id}/transactions/buy` | Record a BUY |
| POST | `/v1/portfolios/{id}/transactions/sell` | Record a SELL (409 if over-sold) |
| GET | `/v1/portfolios/{id}/holdings` | List holdings with weighted avg cost basis |

### Variant B — Dividends

| Method | Path | Description |
|---|---|---|
| POST | `/v1/portfolios/{id}/dividends` | Record a dividend event; credits cash balance |
| GET | `/v1/portfolios/{id}/dividends` | List dividends received, grouped by symbol with totals |

---

## Key Design Decisions

### Money Math
All quantities and prices are `NUMERIC(19,4)` in PostgreSQL and `BigDecimal` in Java — never `double` or `float`. Rounding uses `HALF_UP` at 4 decimal places, consistent with Indian market conventions.

### Weighted-Average Cost Basis
On every BUY:
```
newAvg = (oldQty × oldAvg + buyQty × buyPrice) / (oldQty + buyQty)
```
On SELL: the cost basis per share does **not** change — only quantity decreases. This is the standard FIFO-adjacent average cost method used by most Indian brokers.

### Dividend Logic (Variant B)
When `POST /v1/portfolios/{id}/dividends` is called:
1. Current holding quantity for the symbol is looked up (present state as proxy for record-date state).
2. `totalPayout = sharesAtRecord × perShareAmount`
3. `portfolio.cashBalance += totalPayout` — atomic within the same transaction.
4. The Dividend entity is persisted with all fields for full audit trail.

If the portfolio holds no shares of the symbol on the record date, `payout = 0` but the event is still persisted (audit purposes).

> **Trade-off note**: A production system would snapshot quantity-as-of-recordDate from the transaction history. Here I use the current holding as a pragmatic proxy that passes all functional tests. This is documented as a known limitation.

### Error Handling
All errors return a consistent JSON shape:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient holding for RELIANCE: requested 20 but only 10 available",
  "requestId": "a1b2c3d4-...",
  "timestamp": "2024-04-01T10:30:00Z"
}
```

### Correlation IDs
Every request gets an `X-Request-Id` header injected into the MDC. If the client provides one, it is reused; otherwise a UUID is generated. It appears in every log line and is echoed back in the response header.

---

## Auth

Not required per assignment scope. All endpoints are open. To add auth, add Spring Security with a JWT filter — straightforward to layer on.

---

## Running Tests

```bash
# Unit tests only (no Docker needed)
mvn test -Dtest="PortfolioServiceTest,DividendServiceTest"

# All tests including integration (requires Docker for Testcontainers)
mvn test
```

Test coverage highlights:
- **PortfolioServiceTest**: 7 unit tests covering weighted avg cost basis recalculation, SELL validation, edge cases.
- **DividendServiceTest**: 5 unit tests covering dividend payout math, fractional shares, zero-holding case, accumulation.
- **PortfolioIntegrationTest**: 5 integration tests against a real Postgres container (full flow, 409 sell, 404 unknown, validation, zero-payout dividend).

---

## What I'd Do With 2 More Days

1. **Snapshot quantity at record date**: Query the transaction table to compute held quantity as of `recordDate` rather than using current state. More accurate for historical dividend re-processing.

2. **Pagination**: `GET /v1/portfolios/{id}/holdings` and the dividend list should be paginated for HNI clients with large portfolios (100+ symbols).

3. **Optimistic locking**: Add `@Version` to `Portfolio` and `Holding` to prevent concurrent BUY/SELL race conditions (two requests for the same portfolio hitting the DB simultaneously).

4. **Proper auth**: Add a `Bearer` token filter. Simple API-key-per-user or JWT with a 15-minute expiry. Enough to make the system multi-tenant safe.

5. **Audit log table**: Separate `audit_log` table that records every mutation with `before/after` JSON blobs — critical in regulated financial contexts.

6. **Historical price integration**: Replace the deterministic price stub with a real price lookup (NSE/BSE APIs) gated behind a circuit breaker (Resilience4j).

---

## AI_LOG.md

See [AI_LOG.md](./AI_LOG.md) in the repo root.
