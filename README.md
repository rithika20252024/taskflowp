# TaskFlow Pro

**A shared workspace for teams to organize tasks and track progress.** TaskFlow Pro is a full-stack web application: people create an account, make a team workspace, add tasks, and update each task as work moves forward. It also includes a Stripe Checkout demonstration for fixed-price, one-time purchases.

| | |
|---|---|
| **Live application** | [Open TaskFlow Pro](https://taskflowp-ni3fcwsmf-rithika20252024s-projects.vercel.app/) |
| **Source code** | [github.com/rithika20252024/taskflowp](https://github.com/rithika20252024/taskflowp) |

> The live site opens on a sign-in page. Create an account there to enter the app. The team and task features need a database; checkout additionally needs Stripe credentials and a configured webhook.

## Contents

- [What can I do with TaskFlow Pro?](#what-can-i-do-with-taskflow-pro)
- [A quick tour](#a-quick-tour)
- [How the pieces fit together](#how-the-pieces-fit-together)
- [Technology used](#technology-used)
- [Run the project on your computer](#run-the-project-on-your-computer)
- [Environment settings](#environment-settings)
- [Try the Stripe test checkout](#try-the-stripe-test-checkout)
- [API guide](#api-guide)
- [Deploy to Vercel](#deploy-to-vercel)
- [Build and test](#build-and-test)
- [Project folder guide](#project-folder-guide)
- [Troubleshooting](#troubleshooting)
- [Security notes and current scope](#security-notes-and-current-scope)
- [Glossary](#glossary)
- [License](#license)

## What can I do with TaskFlow Pro?

TaskFlow Pro gives a small team one place to see its work. A user can:

- Create an account or sign in with an email address and password.
- Create a team workspace. The person who creates it becomes its owner.
- Add tasks to a team and see the team’s task list.
- Filter tasks by status: **To do**, **In progress**, or **Done**.
- Change a task’s status as work progresses.
- View a task’s priority and optional description when provided by the API.
- Open a Stripe-hosted checkout for one of the fixed-price examples and view recent order status.

Team access is tied to membership. A user must belong to a team to work with that team’s tasks. Owners and admins can add registered users to their team through the API.

## A quick tour

1. Open the [live app](https://taskflowp-ni3fcwsmf-rithika20252024s-projects.vercel.app/).
2. Choose **Create an account**, enter an email and password, and submit the form. New passwords must be at least 10 characters.
3. In the workspace, choose **New team** and give the workspace a name.
4. Type a task into **Add a task to the list…**, then choose **Add task**.
5. Use the status menu on the task to move it between To do, In progress, and Done. Use the filter near the task heading to show one status at a time.
6. Use the payment buttons only when Stripe has been configured for the deployment. The demo offers **Starter · $9** and **Team · $29** one-time checkout options.

The app remembers the sign-in token in that browser so a refresh keeps the session available. **Sign out** removes it from that browser.

## How the pieces fit together

This project has a web app, an API, and a database:

```mermaid
flowchart LR
    Person[You in a browser] --> Web[Next.js web app]
    Web -->|JSON requests and sign-in token| API[Express API]
    API --> ORM[Prisma database toolkit]
    ORM --> DB[(PostgreSQL database)]
    API -->|Create a hosted checkout| Stripe[Stripe]
    Stripe -->|Signed payment event| API
```

- **The web app** draws the pages and sends requests when you sign in, create a team, or change a task.
- **The API** checks requests, verifies who is signed in, applies team permissions, and performs the requested operation.
- **PostgreSQL** keeps accounts, teams, tasks, payment orders, and processed Stripe event IDs between visits.
- **Prisma** is the layer the API uses to work with PostgreSQL and apply database migrations.
- **Stripe Checkout** hosts the card-entry page. The app does not collect or store card details.

For payments, the API chooses the package price on the server, creates an order, and asks Stripe to open checkout. After checkout, Stripe sends a signed webhook event to the API. The API verifies that signature and updates the order. Returning to the app by itself does not mark an order as paid.

## Technology used

| Part | Tools | In everyday terms |
|---|---|---|
| Web interface | Next.js, React, TypeScript | Builds the pages and interactive controls in your browser. |
| API | Node.js, Express, TypeScript | Receives and handles requests from the web app. |
| Input checks | Zod | Checks that request data has the expected shape. |
| Sign-in | JSON Web Tokens (JWT), bcrypt password hashing | Lets the API recognize a signed-in user without storing a plain-text password. |
| Database | PostgreSQL, Prisma | Stores the app’s data and gives the API a structured way to access it. |
| Payments | Stripe Checkout and signed webhooks | Hosts the example checkout and reports payment outcomes to the API. |
| Local services | Docker Compose | Starts the database, API, and web app together for development. |
| Automated checks | GitHub Actions, Vitest | Builds the app and runs API integration tests in CI. |

## Run the project on your computer

### What you need

- **Git**, to download the source code.
- **Docker Desktop** (or a compatible Docker Engine with Compose), to run the database and app containers.
- An internet connection the first time, so Docker can download the required images.

The Docker route is the easiest way to get started; you do not need to install Node.js separately for this route.

### 1. Download the source

Open Terminal (macOS/Linux) or PowerShell (Windows), then run:

```bash
git clone https://github.com/rithika20252024/taskflowp.git
cd taskflowp
```

### 2. Create the local settings file

Copy the example settings file:

```bash
cp apps/api/.env.example .env
```

On Windows PowerShell, use:

```powershell
Copy-Item apps/api/.env.example .env
```

For a local-only demo, Docker Compose has local development defaults for the database and login secret. You can start without Stripe keys; the team and task parts can still run, while checkout will need Stripe configured. For a safer local secret, replace the `JWT_SECRET` value in `.env` with a long, random string. Never reuse a production secret here.

### 3. Start the app

Make sure Docker Desktop is running, then from the repository folder run:

```bash
docker compose up --build
```

The first start can take a few minutes while images are downloaded and built. Compose starts PostgreSQL, waits for it to be ready, applies the database migration, and then starts the API and web app.

### 4. Open it

- Web app: [http://localhost:3000](http://localhost:3000)
- API health check: [http://localhost:4000/health](http://localhost:4000/health)

The health check should return a small JSON response indicating that the API is running. Create an account in the web app, create a team, and add a task.

To stop the containers, press **Ctrl+C** in the terminal. To start them again later, run `docker compose up` from the project folder. To stop and remove the containers while keeping the database volume, run `docker compose down`.

### Run the web and API directly with Node.js (optional)

Use this approach if you want to work on the code without building the web and API containers. It still uses Docker for PostgreSQL.

Install **Node.js 22 or newer** and npm, then run from the repository root:

```bash
cp apps/api/.env.example .env
npm install
docker compose up -d db
npm run db:deploy --workspace @taskflow/api
npm run db:generate --workspace @taskflow/api
npm run dev
```

On Windows PowerShell, replace the `cp` line with `Copy-Item apps/api/.env.example .env`. The `npm run dev` command starts the API and web development servers together. The web page is at `http://localhost:3000`; the API is at `http://localhost:4000`.

## Environment settings

Environment variables are configuration values supplied to the app when it runs. The local template is [`apps/api/.env.example`](apps/api/.env.example). Keep real values in your local `.env` or your hosting provider’s settings; **do not commit secrets to GitHub**.

| Name | Needed for | What it means |
|---|---|---|
| `DATABASE_URL` | API and database | PostgreSQL connection address. The Docker setup uses its own internal database address. |
| `JWT_SECRET` | Sign-in | Private random string the API uses to sign login tokens. Use at least 32 characters and keep it secret. |
| `PORT` | API | Port the API listens on; local default is `4000`. |
| `CORS_ORIGIN` | Browser/API connection | The web app’s allowed origin, usually `http://localhost:3000` locally. |
| `APP_URL` | Checkout return links | Public address of the web app, usually `http://localhost:3000` locally. |
| `STRIPE_SECRET_KEY` | Checkout | Secret key from Stripe. Use a test key (`sk_test_...`) while learning. |
| `STRIPE_WEBHOOK_SECRET` | Checkout result updates | Signing secret for the Stripe webhook endpoint. For local use, Stripe CLI prints a `whsec_...` value. |
| `NEXT_PUBLIC_API_URL` | Optional web configuration | API base address when the web app and API do not share a domain. Leave unset for the Vercel single-domain routing in this project. |

Never put a Stripe secret key in frontend code or in a variable whose name starts with `NEXT_PUBLIC_`. Variables with that prefix may be included in browser code.

## Try the Stripe test checkout

Payments are optional. To try them, use Stripe **test mode** and never a live key for a learning run.

1. Create or use a Stripe account and copy a test secret key beginning with `sk_test_` into `.env` as `STRIPE_SECRET_KEY`.
2. Install and authenticate the [Stripe CLI](https://docs.stripe.com/stripe-cli). In another terminal, start webhook forwarding:

   ```bash
   stripe listen --forward-to localhost:4000/api/payments/webhook
   ```

3. The CLI prints a webhook signing secret beginning with `whsec_`. Put it in `.env` as `STRIPE_WEBHOOK_SECRET`, set `APP_URL=http://localhost:3000`, and restart the API/container so it reads the new values.
4. Sign in to the local app and choose **Starter · $9** or **Team · $29**. The API sets the prices; the browser cannot choose a different amount.
5. On Stripe’s hosted test checkout page, use test card `4242 4242 4242 4242`, any future expiry date, any three-digit CVC, and any postal code.
6. Return to TaskFlow Pro. The app checks order status until the verified webhook updates it.

These are one-time checkout examples; subscriptions are not implemented. Stripe test mode does not charge real money. Do not use production payment credentials for benchmarks or local experiments.

## API guide

An API is how the web page and other programs ask the server to do work. Most team, task, and payment endpoints require a sign-in token in this HTTP header:

```text
Authorization: Bearer YOUR_TOKEN
```

The register and login endpoints return the token. In the browser, the web app manages it for you.

| Method | Address | What it does |
|---|---|---|
| `GET` | `/health` | Checks whether the API is running. |
| `POST` | `/api/auth/register` | Creates an account. |
| `POST` | `/api/auth/login` | Signs in and returns a token. |
| `GET` | `/api/auth/me` | Returns the current signed-in user. |
| `GET` / `POST` | `/api/teams` | Lists your teams / creates a team. |
| `POST` | `/api/teams/:teamId/members` | Adds a registered user (owner/admin only). |
| `GET` / `POST` | `/api/teams/:teamId/tasks` | Lists / creates tasks in a team. |
| `GET` / `PATCH` / `DELETE` | `/api/teams/:teamId/tasks/:taskId` | Reads / updates / removes a task. |
| `POST` | `/api/payments/checkout` | Creates a server-priced Stripe Checkout Session. Requires an `Idempotency-Key` header as well as the sign-in token. |
| `GET` | `/api/payments` | Lists the signed-in user’s recent payment orders. |
| `GET` | `/api/payments/:orderId` | Gets an order’s status for its owner. |
| `POST` | `/api/payments/webhook` | Receives and verifies Stripe events. Stripe calls this endpoint; it does not use a user token. |

Task lists can be filtered by `status` and `priority`, and paginated with `page` and `limit`. They can be sorted by `createdAt`, `updatedAt`, `dueDate`, `priority`, or `title`, with `asc` or `desc` ordering. For example:

```bash
curl 'http://localhost:4000/api/teams/TEAM_ID/tasks?status=TODO&priority=HIGH&page=1&limit=20&sort=createdAt&order=desc' \
  -H 'Authorization: Bearer YOUR_TOKEN'
```

Replace `TEAM_ID` and `YOUR_TOKEN` with values from your own local account. API validation errors use HTTP 400, missing/invalid login uses 401, insufficient team permissions uses 403, and missing team/task resources use 404.

## Deploy to Vercel

This repository is configured to run the Next.js web app and Express API as Vercel Services on one domain. You also need a managed PostgreSQL database. Vercel Services must be enabled for your Vercel account/project.

1. Import `rithika20252024/taskflowp` into Vercel.
2. Select the **Services** framework preset and keep the project root directory at the repository root. The service definitions and route rewrites are in [`vercel.json`](vercel.json).
3. Create or connect a PostgreSQL database. Use the provider’s pooled connection string if it recommends one for serverless services.
4. In the Vercel project’s environment variable settings, add the following for the environments you use:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | Your managed PostgreSQL connection string. |
   | `JWT_SECRET` | A new, random secret of at least 32 characters. Generate one with `openssl rand -base64 48`. |
   | `APP_URL` | The deployed app’s HTTPS URL. |
   | `CORS_ORIGIN` | The exact deployed web origin, including `https://`. |
   | `STRIPE_SECRET_KEY` | Optional for teams/tasks. Add a Stripe test key to enable test checkout. |
   | `STRIPE_WEBHOOK_SECRET` | Optional for teams/tasks. Required for processing Stripe checkout events. |

5. Deploy. When changing the database schema, apply migrations before the app depends on the new schema:

   ```bash
   DATABASE_URL='your-production-database-url' npm run db:deploy --workspace @taskflow/api
   ```

6. To accept real payments, configure Stripe live credentials and a Stripe webhook at `https://YOUR_DOMAIN/api/payments/webhook`. Subscribe it to `checkout.session.completed`, `checkout.session.expired`, `checkout.session.async_payment_succeeded`, and `checkout.session.async_payment_failed`, then save the endpoint’s signing secret as `STRIPE_WEBHOOK_SECRET`.

Add credentials only through Vercel’s Environment Variables settings. Do not put them in source files, commit them, or paste them into an issue or chat. Live payments require live Stripe credentials and a matching webhook signing secret.

## Build and test

Install dependencies and build both apps:

```bash
npm ci
npm run db:generate --workspace @taskflow/api
npm run build
```

API integration tests use a separate PostgreSQL database, so they do not reuse your normal local database. With Docker running, create and migrate it once:

```bash
docker compose up -d db
docker compose exec db createdb -U taskflow taskflow_test
DATABASE_URL='postgresql://taskflow:taskflow@localhost:5432/taskflow_test?schema=public' npm run db:deploy --workspace @taskflow/api
```

If `createdb` says the database already exists, continue. Run the tests with:

```bash
DATABASE_URL='postgresql://taskflow:taskflow@localhost:5432/taskflow_test?schema=public' \
JWT_SECRET='integration-test-secret-at-least-32-characters-long' \
npm test
```

The test command runs the API’s Vitest integration suite and writes coverage output under `apps/api/coverage/`. GitHub Actions also provisions PostgreSQL, builds the web and API apps, runs the API tests, and collects coverage.

## Project folder guide

```text
taskflowp/
├── apps/
│   ├── api/
│   │   ├── prisma/           Database schema and migrations
│   │   ├── scripts/          Optional API benchmark scripts
│   │   ├── src/               Express API and server entry point
│   │   └── test/              API integration tests
│   └── web/
│       ├── app/               Next.js pages and styles
│       └── lib/               Browser-side API helpers and types
├── legacy/                    Earlier Spring Boot job-queue project
├── .github/workflows/          Automated GitHub Actions checks
├── Dockerfile.api              Instructions for building the API container
├── Dockerfile.web              Instructions for building the web container
├── docker-compose.yml          Local database, API, and web setup
├── prisma.config.ts            Prisma configuration
├── package.json                Shared commands and npm workspace setup
└── vercel.json                 Vercel Services and route configuration
```

The current TaskFlow Pro app lives under `apps/`. The `legacy/` folder contains an earlier Spring Boot job-queue project and is not the web application described in the main sections above.

## Troubleshooting

| Problem | Things to check |
|---|---|
| `localhost:3000` does not open | Make sure Docker is running and `docker compose up --build` finished without an error. |
| Database connection or migration error | Check that the `db` container is healthy. If you edited `.env`, restart the services so they load the new settings. |
| Sign-in says the account is unknown | Create the account first using **Create an account**, then sign in with the same email and password. |
| Team list is empty | Create a team with **New team**. A task belongs to a team, so create/select a team before adding tasks. |
| Checkout says Stripe is not configured | Add a Stripe test secret key and a webhook signing secret to `.env`, then restart the API. Checkout is optional for task management. |
| Checkout returns but stays pending | Make sure `stripe listen` is still running, its printed `whsec_...` value matches `STRIPE_WEBHOOK_SECRET`, and the API was restarted after changing `.env`. |
| Port 3000, 4000, or 5432 is already in use | Stop the other application using that port, or change the port mapping in `docker-compose.yml`. |

## Security notes and current scope

- Passwords are stored as bcrypt hashes, not as readable passwords.
- Team membership and permissions are checked by the API, not just by the browser interface.
- Checkout prices are selected by the API. The browser does not submit an amount.
- Card details are entered on Stripe-hosted Checkout and are not collected or stored by TaskFlow Pro.
- Stripe webhook signatures are verified, and event IDs are recorded to avoid processing the same event more than once.
- Local default secrets are for local development only. Use unique secrets and managed database credentials for a deployed environment.
- The payment feature demonstrates one-time checkout and webhook handling. It does not implement subscriptions, a complete accounting ledger, or payment reconciliation for missed webhooks. Treat it as a project/demo, not as evidence of a production payment operation.

## Glossary

| Word | Simple meaning |
|---|---|
| **API** | The server interface the web page calls to read or change information. |
| **Database** | Organized storage that keeps accounts, teams, tasks, and orders after the page is closed. |
| **Environment variable** | A setting supplied when software runs, often used for URLs or private keys. |
| **JWT / token** | A signed proof the browser sends to the API to show which user is signed in. |
| **Migration** | A versioned instruction for creating or changing database tables. |
| **Webhook** | A server-to-server message, such as Stripe telling the API that a checkout completed. |
| **Idempotency key** | A unique request label that helps prevent a retry from accidentally creating duplicate work or orders. |
| **Docker Compose** | A tool that starts multiple related services, such as the database and app, from one command. |

## License

This project is released under the [MIT License](LICENSE).
