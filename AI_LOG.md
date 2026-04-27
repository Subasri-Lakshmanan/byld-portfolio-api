# AI_LOG.md

## Tools Used
- Claude (claude.ai) — primary tool for scaffolding, reviewing logic, and writing boilerplate
- GitHub Copilot — inline suggestions while writing tests

---

## Significant Prompts

### 1. Weighted-average cost basis formula
**Prompt**: "What is the correct formula to recalculate weighted-average cost basis when a new BUY comes in, given existing quantity and average?"

**What AI produced**: The formula `newAvg = (oldQty * oldAvg + buyQty * buyPrice) / (oldQty + buyQty)` along with an explanation of why SELL should not change the average.

**Kept**: The formula — it's mathematically correct and what Indian brokers use.  
**Rejected**: AI initially suggested updating the average on SELL too ("realised cost tracking"). I rejected this because the standard convention (and what the spec implies) is that cost basis is a per-share average that only changes on BUY.

---

### 2. Flyway migration schema
**Prompt**: "Give me a Flyway V1 migration for a portfolio API with portfolios, holdings, transactions, dividends tables. Money fields should be NUMERIC(19,4)."

**What AI produced**: A complete SQL migration with reasonable column types.

**Kept**: Column types, constraint names, indexes on foreign keys.  
**Rejected**: AI used `SERIAL` for primary keys — I replaced with `UUID DEFAULT gen_random_uuid()` to match the spec (UUIDs) and avoid leaking record counts.

---

### 3. GlobalExceptionHandler
**Prompt**: "Write a Spring @RestControllerAdvice that handles PortfolioNotFoundException (404), InsufficientHoldingException (409), and validation errors (400) with a consistent ErrorResponse JSON shape."

**What AI produced**: A working handler with `@ExceptionHandler` methods.

**Kept**: The overall structure and MDC-based requestId injection.  
**Rejected**: AI added a catch-all that logged the full stack trace to the response body. That's a security issue — internal stack traces should never leak to clients. I replaced it with a generic "unexpected error" message.

---

### 4. Testcontainers integration test
**Prompt**: "Write a Spring Boot integration test using Testcontainers PostgreSQL that tests the full BUY → SELL → dividend flow end-to-end via MockMvc."

**What AI produced**: A reasonable integration test skeleton.

**Kept**: The `@DynamicPropertySource` wiring pattern, test structure.  
**Rejected**: AI checked `cashBalance` before any transactions and expected a non-zero value (copy-paste error from an earlier example in the same session). I caught this during review — the initial cash balance should be 0, not whatever the AI had hardcoded.

---

### 5. Dividend service — zero-holding edge case
**Prompt**: "What should happen when a dividend is recorded but the portfolio holds 0 shares of that symbol?"

**AI response**: "Throw a 404 — the holding doesn't exist."

**What I did instead**: I decided to record the event with `payout = 0` anyway. Financial systems need an audit trail. A dividend being declared on a stock you don't hold is a normal market event — it shouldn't be an error. The AI suggestion would have made it harder to reconstruct historical income statements.

---

## A Bug AI Introduced

During the Testcontainers test, the AI wrote:
```java
.andExpect(jsonPath("$.cashBalance").value(29000.0)); // after first BUY
```

The cash balance is only credited when a **dividend** is recorded — buying shares does not change the cash balance in this model (we don't track capital debited, only dividend credits). The AI confused the `totalValue` of the transaction with the portfolio cash balance. I caught it by re-reading the spec and the `DividendService` logic: cash only moves on `recordDividend()`, not on `buy()`.

---

## A Design Choice Made Against AI Suggestion

**AI suggestion**: Use `spring.jpa.hibernate.ddl-auto=update` for simplicity in development.

**What I did**: Used `validate` + Flyway for all environments. The spec explicitly said no `ddl-auto=update` in production config, and for good reason — it silently drops columns on schema drift and has caused real data loss incidents. Flyway gives explicit, auditable, reversible migrations. It's more setup upfront but the only acceptable choice for financial data.

---

## Time Split

| Activity | Approximate % |
|---|---|
| Design & planning (entity model, API shape, dividend logic) | 20% |
| Writing code (services, controllers, entities, DTOs) | 30% |
| Prompting AI + reviewing/editing AI output | 20% |
| Writing tests (unit + integration) | 20% |
| Docker setup, README, AI_LOG | 10% |
