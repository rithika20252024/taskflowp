# TaskFlow Pro — Payment Workflow Demo

TaskFlow Pro is a team task-management app with an end-to-end Stripe Checkout workflow. The demo uses Stripe-hosted test-mode checkout for fixed-price one-time purchases, stores order state in PostgreSQL, and treats verified webhooks as the source of truth for payment completion. It is a portfolio project, not a production billing platform.

## Stack

- **Web:** Next.js, React, TypeScript
- **API:** Node.js, Express, TypeScript, Zod validation, JWT authentication, Stripe Node SDK
- **Payments:** Stripe Checkout Sessions, signed webhooks, idempotent order creation and event processing
- **Data:** PostgreSQL and Prisma ORM; amounts are integer minor units
- **Delivery:** Docker Compose and GitHub Actions

## Architecture

```mermaid
flowchart LR
    U[Team member's browser]
    W[Next.js web app<br/>React UI]
    A[Express REST API<br/>JWT auth · Zod · payment orders]
    P[Prisma ORM]
    D[(PostgreSQL<br/>users · teams · tasks · payment orders · Stripe event IDs)]
    S[Stripe Checkout]
    C[Docker Compose]
    CI[GitHub Actions<br/>build · integration tests · coverage]

    U -->|loads interface| W
    U -->|HTTPS / JSON + bearer token| A
    A --> P
    P --> D
    A -->|Create session with stable idempotency key| S
    S -->|Signed webhook; event deduplicated| A
    S -->|Hosted card entry| U
    C -. runs .-> W
    C -. runs .-> A
    C -. runs .-> D
    CI -. verifies .-> W
    CI -. verifies .-> A
```

The browser uses the Next.js app for the interface and calls the Express API for authenticated team, task, and payment operations. The API fixes the package and amount server-side, creates an order, then asks Stripe to create a hosted Checkout Session with an idempotency key derived from that order. Stripe sends signed webhook events to the raw-body webhook route; the API verifies the signature, records each event ID once, and updates order status. The browser return URL only displays status and never marks an order paid. Card details are entered on Stripe-hosted Checkout and are not collected or stored by this app.

## Project structure

```text
taskflowp/
├── apps/
│   ├── api/
│   │   ├── prisma/          # Data model and PostgreSQL migrations
│   │   ├── scripts/         # API latency benchmark
│   │   ├── src/             # Express app and server entry point
│   │   └── test/            # API integration tests
│   └── web/
│       ├── app/             # Next.js routes and interface styles
│       └── lib/             # API client helpers
├── legacy/                  # Original Spring Boot job-queue project
├── .github/workflows/       # CI build and test workflow
├── Dockerfile.api
├── Dockerfile.web
├── docker-compose.yml
├── package.json             # npm workspaces and shared commands
└── prisma.config.ts
```

## Run locally

Requirements: Node.js 22+, npm, and Docker Compose.

```bash
git clone https://github.com/rithika20252024/taskflowp
cd taskflowp
cp apps/api/.env.example .env
# Set JWT_SECRET and Stripe settings as described below.
docker compose up --build
```

Open [http://localhost:3000](http://localhost:3000). The API health endpoint is [http://localhost:4000/health](http://localhost:4000/health). Compose starts PostgreSQL, applies the Prisma migration, then starts the API and web app.

For local development with Node.js on your host, first make sure Docker Desktop is running. From the repository root, install the pinned dependencies, start PostgreSQL, and prepare the database:

```bash
cp apps/api/.env.example .env
npm install
docker compose up -d db
npm run db:deploy --workspace @taskflow/api
npm run db:generate --workspace @taskflow/api
npm run dev
```

The API reads `DATABASE_URL` and `JWT_SECRET` from the root `.env` file. Copy `apps/api/.env.example` to `.env` before starting the app and replace `JWT_SECRET` with a random secret of at least 32 characters. Run `npm install` before using Prisma or starting the app; this ensures the project's pinned Prisma CLI and `concurrently` are available. Avoid invoking Prisma through `npx` before installing dependencies, since npm may fetch a different version.

### Try Stripe Checkout locally

1. Create or use a Stripe account in **test mode** and put its `sk_test_...` secret key in `.env` as `STRIPE_SECRET_KEY`. Never place a Stripe secret key in browser code or a `NEXT_PUBLIC_*` variable.
2. Install and authenticate the [Stripe CLI](https://docs.stripe.com/stripe-cli), then run `stripe listen --forward-to localhost:4000/api/payments/webhook`. Copy its printed `whsec_...` signing secret into `.env` as `STRIPE_WEBHOOK_SECRET`, then restart the API. Set `APP_URL=http://localhost:3000` for local use.
3. Sign in to TaskFlow Pro and use the **Starter · $9** or **Team · $29** checkout buttons. Prices are fixed by the API; the browser cannot submit a different amount.
4. Complete Checkout with Stripe's test card `4242 4242 4242 4242`, any future expiry, any three-digit CVC, and any postal code. Return to the app and wait for the signed webhook to confirm the order.

The webhook verifies Stripe's signature against the raw request body. Payment events are deduplicated by Stripe event ID, while order creation uses user-scoped and Stripe API idempotency keys. Duplicate or delayed delivery is expected; production systems also need reconciliation for missed webhooks and a more complete ledger. This project does not implement subscriptions or claim production payment volume.

## API overview

All team/task routes require `Authorization: Bearer <token>`. Users can register or log in at `/api/auth/register` and `/api/auth/login`. The user who creates a team becomes its `OWNER`; owners and admins can add registered users to the team. Team membership is checked before task access, and task queries are scoped to the selected team.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Return a signed JWT |
| POST | `/api/payments/checkout` | Create a server-priced Checkout Session; requires bearer token and `Idempotency-Key` |
| GET | `/api/payments` | List the authenticated user's recent orders |
| GET | `/api/payments/:orderId` | Read an order status for its owner |
| POST | `/api/payments/webhook` | Verify and process Stripe-signed Checkout events; no user JWT |
| GET / POST | `/api/teams` | List memberships / create a team |
| POST | `/api/teams/:teamId/members` | Add a team member (owner/admin) |
| GET / POST | `/api/teams/:teamId/tasks` | Filter and page through tasks / create a task |
| GET / PATCH / DELETE | `/api/teams/:teamId/tasks/:taskId` | Read, update, or remove a task |

Task listing supports `page` (1-based), `limit` (1–100), `status`, `priority`, `sort` (`createdAt`, `updatedAt`, `dueDate`, `priority`, or `title`), and `order` (`asc` or `desc`). For example:

```bash
curl 'http://localhost:4000/api/teams/TEAM_ID/tasks?status=TODO&priority=HIGH&page=1&limit=20&sort=createdAt&order=desc' -H 'Authorization: Bearer YOUR_TOKEN'
```

The API validates request bodies and query parameters and returns structured 400 validation errors, 401 authentication errors, 403 role errors, and 404 team/task errors. Passwords are stored as bcrypt hashes. Production deployments must supply a unique, random `JWT_SECRET` and a managed PostgreSQL database.

## Checks

```bash
npm install
npx prisma generate --config=prisma.config.ts
npm run build
```

API integration tests use a separate PostgreSQL database and clean their tables between cases. With Docker Desktop running, create and migrate that database once:

```bash
docker compose up -d db
docker compose exec db createdb -U taskflow taskflow_test
DATABASE_URL='postgresql://taskflow:taskflow@localhost:5432/taskflow_test?schema=public' npm run db:deploy --workspace @taskflow/api
```

If `createdb` reports that `taskflow_test` already exists, continue. Run the tests with:

```bash
DATABASE_URL='postgresql://taskflow:taskflow@localhost:5432/taskflow_test?schema=public' JWT_SECRET='integration-test-secret-at-least-32-characters-long' npm test
```

Vitest prints the V8 coverage summary in the terminal and writes detailed reports under `apps/api/coverage/`.

The CI workflow provisions PostgreSQL, applies migrations, builds both apps, runs the integration suite, and collects an API coverage report.

## Resume-ready summary

Use claims that match the implementation and the results you measure:

- Integrated Stripe Checkout into a full-stack TypeScript app, persisting server-priced orders and reconciling status from signature-verified, deduplicated webhook events.
- Made checkout retries safe with user-scoped idempotency keys and stable Stripe request keys; kept card entry on Stripe-hosted Checkout.
- Built team/task APIs with JWT auth, role checks, PostgreSQL, Prisma, and validated filtering and pagination.

Do not claim production scale, payment volume, or a latency improvement until you have measured it. The API logs per-request duration in milliseconds without logging credentials or payment data. The existing benchmark samples the authenticated task-list route, not Stripe's external Checkout API; its p50/p95/p99 describe your local API and database only. Stripe latency varies with network and service conditions. Record separate Checkout call timings only after a repeatable test-mode sample, and report sample size, concurrency, and environment with the result.

To collect task API latency percentiles, start the app, register and log in a user through `/api/auth/register` and `/api/auth/login`, then create a team with `POST /api/teams` using that user's bearer token. Use the returned JWT and team ID:

```bash
TEAM_ID='your-team-id' TASKFLOW_TOKEN='your-jwt' npm --workspace @taskflow/api run benchmark
```

The benchmark reports median, p95, and p99 response times in milliseconds for 100 requests at concurrency 10 by default. Use the measured values from your own run when writing resume claims; results depend on the machine and environment.

The payment benchmark creates real Checkout Sessions in Stripe **test mode** (it makes no charges), so it is opt-in and bounded to 100 requests / concurrency 10. Use a separate test-mode deployment, a fresh API token, and its session URL only for timing; do not point it at a live-key environment:

```bash
STRIPE_TEST_MODE_ACK=true TASKFLOW_TOKEN='your-jwt' REQUESTS=20 CONCURRENCY=4 npm --workspace @taskflow/api run benchmark:payments
```

It reports p50, p95, and p99 for the full authenticated checkout endpoint, including the external Stripe API request and database work. The samples also create pending test orders and unpaid test Checkout Sessions. Report your actual run parameters and results; do not describe this as production traffic or claim an improvement without a measured before/after change.
