# TaskFlow Pro

TaskFlow Pro is a team task-management app built with a Next.js interface, an Express REST API, PostgreSQL, Prisma, and Docker Compose. Teams can share tasks, assign workflow states and priorities, and filter and page through work. Authentication uses signed JWTs; team membership roles control access.

## Stack

- **Web:** Next.js, React, TypeScript
- **API:** Node.js, Express, TypeScript, Zod validation, JWT authentication
- **Data:** PostgreSQL and Prisma ORM
- **Delivery:** Docker Compose and GitHub Actions

## Architecture

```mermaid
flowchart LR
    U[Team member's browser]
    W[Next.js web app<br/>React UI]
    A[Express REST API<br/>JWT auth · Zod validation · team roles]
    P[Prisma ORM]
    D[(PostgreSQL<br/>users · teams · memberships · tasks)]
    C[Docker Compose]
    CI[GitHub Actions<br/>build · integration tests · coverage]

    U -->|loads interface| W
    U -->|HTTPS / JSON + bearer token| A
    A --> P
    P --> D
    C -. runs .-> W
    C -. runs .-> A
    C -. runs .-> D
    CI -. verifies .-> W
    CI -. verifies .-> A
```

The browser uses the Next.js app for the interface and calls the Express API for authenticated team and task operations. The API checks JWTs and team membership, validates inputs, then reads and writes PostgreSQL through Prisma. Docker Compose runs the web app, API, database, and migration step; GitHub Actions builds both apps and runs the API integration suite against PostgreSQL.

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
cp apps/api/.env.example .env
# Set JWT_SECRET to a random secret with at least 32 characters.
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

## API overview

All team/task routes require `Authorization: Bearer <token>`. Users can register or log in at `/api/auth/register` and `/api/auth/login`. The user who creates a team becomes its `OWNER`; owners and admins can add registered users to the team. Team membership is checked before task access, and task queries are scoped to the selected team.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Return a signed JWT |
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

- Built a full-stack team task-management app with a Next.js interface, Express REST API, PostgreSQL, and Prisma.
- Implemented JWT authentication, team membership roles, validated task CRUD, and server-side filtering, sorting, and pagination.
- Added API integration tests, a GitHub Actions build-and-test workflow, and API coverage reporting.

Vitest/V8 records API coverage. The API also logs per-request duration in milliseconds. To collect latency percentiles, first start the app, register and log in a user through `/api/auth/register` and `/api/auth/login`, then create a team with `POST /api/teams` using that user's bearer token. Use the returned JWT and team ID for the repeatable sample:

```bash
TEAM_ID='your-team-id' TASKFLOW_TOKEN='your-jwt' npm --workspace @taskflow/api run benchmark
```

The benchmark reports median, p95, and p99 response times in milliseconds for 100 requests at concurrency 10 by default. Use the measured values from your own run when writing resume claims; results depend on the machine and environment.
