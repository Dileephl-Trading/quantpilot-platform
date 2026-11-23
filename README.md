# Market Signal Scanner

A local, two-service application for evaluating intraday candle data and maintaining a disciplined paper-trading journal. It does **not** connect to a broker, place orders, or promise profitable trades.

## Repository layout

```text
market-signal-scanner/
├── backend/       Spring Boot API, risk engine, PostgreSQL persistence and tests
├── frontend/      React + Vite trading workspace UI
├── .github/       Continuous-integration workflow
└── README.md
```

The first strategy is deliberately conservative: it emits a `WATCH_LONG` or `WATCH_SHORT` only when all of the following agree:

- price breaks the 30-minute opening range;
- price is on the correct side of intraday VWAP;
- current volume is at least 1.5× the preceding five-candle average.

Otherwise it returns `NO_TRADE`. That is a valid and often preferred outcome.

## Safety boundaries

- This is research software, not investment advice.
- Any signal is a hypothesis, not a prediction or order instruction.
- Use paper trading first. Define position size and a hard stop before taking any real trade.
- Broker credentials are used only for the optional read-only Kite market-data connection; automatic trading, averaging-down logic, and “recovery” sizing are not included.

## Production baseline

The runtime is PostgreSQL only. H2 and Hibernate schema auto-update are not used. Flyway owns every schema change, and the backend starts only when its schema validates successfully.

The local production-like stack is intentionally bound to `127.0.0.1`; it is not exposed to your LAN or the public internet. It contains:

- PostgreSQL 17 with a named durable Docker volume
- Spring Boot API with Hikari connection pooling, Flyway migrations and health checks
- An immutable audit-event table for profile and trade lifecycle changes
- Pessimistic profile locks and versioned trade rows to prevent simultaneous risk decisions from consuming the same limits
- React built into Nginx, with `/api` reverse-proxied to the internal backend service

Create your local, uncommitted secret file once:

```bash
cp .env.example .env
# edit .env and replace POSTGRES_PASSWORD with a long unique password
```

Start the complete local stack:

```bash
docker compose --env-file .env up --build -d
```

Open `http://localhost:8080`. Check service health with:

```bash
docker compose --env-file .env ps
docker compose --env-file .env logs -f
```

Stop it with:

```bash
docker compose --env-file .env down
```

`docker compose down` preserves the PostgreSQL volume. Do not run `docker compose down -v` unless you deliberately want to erase local trade history.

## Run services during development

Requires Java 21 and Maven.

```bash
cd backend
mvn spring-boot:run
```

For development outside Docker, first start PostgreSQL through Docker Compose and export the matching `DATABASE_PASSWORD` from `.env`. The backend stores all data in PostgreSQL; it does not create a local H2 file.

## Separate frontend service

The UI is a separate Vite + React service in [`frontend/`](frontend/). It is intentionally independent from the Spring Boot process, but proxies `/api` calls to the local backend during development.

Start the backend in one terminal:

```bash
cd backend
mvn spring-boot:run
```

Then start the frontend in another:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The interface exposes the local paper-trading profile, order-risk preview and, when Kite has been configured, the read-only market-data workspace. The Phase 4 order adapter is present behind explicit gates, but live execution remains disabled by default.

## Configurable paper-trading foundation

The dashboard starts with the agreed conservative configuration profile:

| Setting | Default |
| --- | --- |
| Mode | `PAPER` |
| Allocated capital | ₹50,000 |
| Maximum risk per trade | ₹1,000 |
| Maximum daily loss | ₹2,000 |
| Maximum trades per day | 2 |
| Maximum open positions | 1 |
| Segment/product | NSE Cash / MIS |

Use **Order risk preview** to validate a proposed order before it can reach a future broker adapter. It calculates the maximum permitted quantity as the lower of the stop-loss risk limit and the allocated-capital limit. BUY stops must be below entry and SELL stops must be above entry. The journal additionally enforces the daily loss limit, maximum trades, maximum open positions, cooldown, kill switch and one active intent per symbol. This release cannot send an order to a broker, including when a profile is saved as `DRY_RUN`.

## Evaluate candles

`POST /api/v1/signals/evaluate`

```json
{
  "symbol": "AXISBANK",
  "candles": [
    {"timestamp":"2026-11-18T09:15:00+05:30","open":1368.0,"high":1369.0,"low":1367.5,"close":1368.8,"volume":12000}
  ]
}
```

At least 12 five-minute candles are required: six for the opening range, five for the volume baseline, and the current candle. Prices use ordinary decimal values; timestamps are ISO-8601.

The response includes the detected bias, VWAP, opening-range bounds, a suggested invalidation reference, and reasons. It intentionally avoids a target price: targets should be tested and risk-managed per instrument rather than guessed by the scanner.

## Run without a market-data subscription

You can test the scanner completely offline with a local CSV containing:

```text
timestamp,open,high,low,close,volume
2026-11-18T09:15:00+05:30,1368,1369,1367,1368.8,12000
```

`CsvCandleImporter` reads these candles locally. `FiveMinuteCandleAggregator` converts ordered live ticks into *completed* five-minute candles, which prevents a partially formed candle from triggering a signal.

## Zerodha Kite Connect readiness

The project contains a provider-neutral market-data boundary plus a Zerodha Kite implementation. Market data and broker execution are separate capabilities. Before obtaining Kite credentials, the **Market data** dashboard page reports `NOT_CONFIGURED` and makes no outbound request; broker routing remains locked by default.

When the Kite Connect app is active, add the supplied values only to the local `.env` file:

```bash
KITE_MARKET_DATA_ENABLED=false
KITE_API_KEY=your_api_key
KITE_API_SECRET=your_api_secret
```

Set this exact **Redirect URL** in the Kite Developer Console when you create or edit the app:

```text
http://127.0.0.1:8080/api/v1/integrations/zerodha/callback
```

The application implements the login URL, server-side request-token exchange, instrument-master sync, symbol search, and historical-candle contract. Open the **Market data** dashboard page, select **Connect Kite**, authenticate with Zerodha, and return to the local callback page. The access token exists only in application memory for the current run; it is never returned by an API, displayed in the UI, stored in PostgreSQL, or placed in frontend code. Zerodha access tokens expire daily, so repeat the login each trading day and after an application restart. Broker order routing remains disabled unless all Phase 4 gates are deliberately enabled.

### Read-only market-data verification

After confirming that the session is connected, set the following in your local `.env` and restart the stack:

```bash
KITE_MARKET_DATA_ENABLED=true
```

Restarting clears the in-memory session, so use **Connect Kite** again. Then use the dashboard in this order:

1. **Sync NSE cash symbols** downloads the provider's instrument master and stores only `NSE` equity (`EQ`) instruments in PostgreSQL. This keeps the first live-data scope focused on the cash stocks the trading profile supports.
2. Search a stored symbol, select it, choose a date, and use **Fetch and evaluate**. The backend filters out the in-progress candle, then evaluates completed five-minute historical candles with the existing signal engine.

The verification endpoints are read-only:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/market-data/instruments/sync` | Download and persist NSE cash-equity symbols |
| `GET /api/v1/market-data/instruments?query=SBIN` | Search locally persisted symbols |
| `GET /api/v1/market-data/instruments?sector=BANKING` | Filter synced symbols by local sector classification |
| `GET /api/v1/market-data/groups` | List predefined Bank Nifty and sector groups |
| `GET /api/v1/market-data/historical-candles` | Fetch up to seven days of provider historical candles |

### Live watchlist alerts

After historical verification works, you may add selected symbols to the small local live watchlist and select **Start monitor**. The monitor uses Kite's read-only WebSocket feed, builds only *completed* five-minute candles, and then evaluates the same opening-range/VWAP/volume rules. A `WATCH_LONG` or `WATCH_SHORT` is an alert recorded locally; it is never an order and it does not guarantee a move.

Keep the watchlist small (maximum 50 symbols). The feed reports connection, stale, and reconnecting states and will attempt reconnection while the current session remains valid. After an application restart, complete the daily Kite login again and explicitly start the monitor.

The live-alert endpoints are read-only:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/live-market/watchlist` | Add a synced NSE symbol to the local watchlist |
| `GET /api/v1/live-market/watchlist` | View enabled watchlist entries |
| `DELETE /api/v1/live-market/watchlist/{instrumentToken}` | Disable a watchlist symbol |
| `POST /api/v1/live-market/watchlist/groups/{group}` | Add a predefined group such as `bank-nifty`, `it`, or `pharma` |
| `POST /api/v1/live-market/start` | Start the read-only WebSocket monitor |
| `POST /api/v1/live-market/stop` | Stop the monitor without affecting any broker account |
| `GET /api/v1/live-market/status` | Inspect the connection state and last received tick |
| `GET /api/v1/live-market/signals?symbol=SBIN&type=WATCH_LONG&limit=50` | Filter locally recorded completed-candle alerts |
| `GET /api/v1/live-market/candles` | View locally recorded completed candles and their evaluation, including `NO_TRADE` |

Scanner strategy settings are available through `GET` and `PUT /api/v1/scanner/config`. Runtime changes affect the current application run; environment variables remain the startup defaults.

The frontend uses a relative `/api` base by default and accepts `VITE_API_BASE_URL` at build time. This keeps the same UI usable in the local browser now and in a future privately installed mobile wrapper without changing the backend contracts. Mobile access to a desktop-hosted backend will require an explicitly secured LAN/API configuration; the current backend remains bound to localhost for safety.

## Paper-trade safety gate

`PaperTradeRiskGuard` blocks a proposed paper trade when it exceeds the user-supplied risk-to-stop, capital-allocation, daily-loss, or max-trades rule. The persisted paper-trade service applies the same controls transactionally and also enforces maximum open positions, cooldowns, duplicate-symbol prevention and the kill switch. It does not create or route broker orders.

## Phase 4 — controlled Kite order execution

The execution boundary supports regular Kite orders, status reconciliation, partial-fill states, cancellation, modification, idempotency, and durable execution events. Every live submission requires:

1. Trading profile mode `LIVE`.
2. Kill switch disabled.
3. `KITE_ORDER_ROUTING_ENABLED=true`.
4. A valid daily Kite session.
5. A positive expected entry price and stop price so the risk engine can validate quantity before the broker call.

The execution request is stored before later reconciliation. Status polling updates the same row and appends an `execution_order_event` record for each observed state. A local deployment cannot receive Kite postbacks without a public HTTPS callback, so this release uses scheduled polling for tracked `PENDING`, `OPEN`, and `PARTIALLY_FILLED` orders.

Execution endpoints:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/execution/orders` | Submit a risk-checked order with `Idempotency-Key` |
| `GET /api/v1/execution/orders` | View locally tracked execution requests |
| `GET /api/v1/execution/orders/{orderId}` | Poll and persist one broker order status |
| `GET /api/v1/execution/orders/{orderId}/events` | View the persisted lifecycle and fill events |
| `POST /api/v1/execution/orders/reconcile` | Reconcile tracked open/partial orders |
| `DELETE /api/v1/execution/orders/{orderId}` | Request cancellation and persist the result |
| `PUT /api/v1/execution/orders/{orderId}` | Request modification and persist the result |
| `POST /api/v1/execution/orders/cancel-open` | Cancel tracked open/partial orders |

The order request must include `stopPrice` in addition to the broker fields. Kite `OPEN` orders with a non-zero fill are represented as `PARTIALLY_FILLED`, preserving the actual filled quantity and average price.

Example request shape (use only after the gates are deliberately enabled):

```json
{
  "symbol": "SBIN",
  "side": "BUY",
  "quantity": 1,
  "orderType": "LIMIT",
  "price": 1130,
  "triggerPrice": null,
  "product": "MIS",
  "exchange": "NSE",
  "validity": "DAY",
  "stopPrice": 1128
}
```

### Automated signal-to-order flow

The live monitor can hand completed `WATCH_LONG`/`WATCH_SHORT` signals to the same execution gate, but this is disabled by default:

```bash
AUTOMATED_EXECUTION_ENABLED=false
```

When deliberately enabled, automation still requires LIVE profile mode, `KITE_ORDER_ROUTING_ENABLED=true`, a current Kite session, an open NSE session, a fresh completed candle, a disabled kill switch, valid entry/invalidation prices, available risk/capital, cooldown availability, no active order for the symbol, and an available position slot. The default automated quantity cap is one share. Every rejection is written to the audit trail, and no automatic order is sent when any gate fails.

If a broker call may have succeeded but the local persistence step fails, automation enters an uncertain-state circuit breaker and stops issuing further automatic orders until the application is restarted and the broker order list is manually reconciled.

Inspect the current gates with `GET /api/v1/automation/status`. This release does not enable automatic routing by default and does not provide a remote toggle for it; changing the local environment and restarting is intentional.

## Phase 5 — kill switch and safety layer

The safety layer persists the automation uncertain-state circuit breaker, rejects idempotency-key reuse with a different payload, cancels tracked live open orders when the kill switch is enabled, and exposes `POST /api/v1/automation/recover` for reconciliation-based recovery. Audit events are available through `GET /api/v1/audit/events` and the **Audit log** screen. Recovery remains manual and requires broker status review; the system does not silently resume automation after an uncertain broker result.

## Phase 1 — paper-trade journal

The journal is a local audit trail, not a simulated broker fill. It records the lifecycle separately:

1. `INTENDED`: a risk-approved plan with the intended entry, stop and quantity.
2. `OPEN`: the user records the actual observed fill price and quantity.
3. `CLOSED`: the user records the actual exit, charges and exit reason; the service calculates gross and net P&L.
4. `CANCELLED`: an intended trade did not fill.

The service uses the saved profile to block a new intent when its risk exceeds the per-trade limit, when active risk plus realised loss would exceed the daily limit, when the trade-count limit is reached, or when the kill switch is on. A fill is checked again against the actual fill price because it can differ from the intended price.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/paper-trades` | Today’s journal entries |
| `GET /api/v1/paper-trades/summary` | Today’s P&L and remaining risk capacity |
| `GET /api/v1/paper-trades/report?tradeDate=YYYY-MM-DD` | Daily gross/net P&L, charges, win rate and slippage |
| `GET /api/v1/paper-trades/export?tradeDate=YYYY-MM-DD` | CSV export of the journal and realised results |
| `POST /api/v1/paper-trades` | Record a new intended trade |
| `POST /api/v1/paper-trades/{id}/fill` | Record the actual entry fill |
| `POST /api/v1/paper-trades/{id}/close` | Record actual exit, charges and P&L |
| `POST /api/v1/paper-trades/{id}/cancel` | Cancel an unfilled intent |
| `GET /api/v1/paper-trades/{id}/replay` | Read-only lifecycle replay for one trade |

These endpoints cannot send broker orders. They are the journal and control layer that future broker adapters will use.

## Security and deployment boundary

- `.env` is intentionally ignored by Git. Never commit a database password, broker API secret, access token or session token.
- The current build is a single-user, local system. Do not expose it through a public port-forward, cloud VM or home-router rule before user authentication, authorization, TLS, encrypted secrets and an operational backup/restore process are added.
- Live order placement remains disabled by default. The execution adapter requires the explicit profile, kill-switch, session, routing-flag and risk gates; it never bypasses the risk engine.

## Roadmap

1. Add a licensed market-data adapter and WebSocket candle aggregation.
2. Persist alerts and paper-trade outcomes for backtesting.
3. Add sector/index confirmation and a daily risk-limit dashboard.
4. Evaluate performance on out-of-sample data before considering any broker integration.

