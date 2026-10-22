import dotenv from 'dotenv';
import { resolve } from 'node:path';
import { defineConfig } from 'prisma/config';

// Prisma commands run from either the repository root or apps/api.
dotenv.config({ path: [
  resolve(process.cwd(), '.env.local'),
  resolve(process.cwd(), '../../.env.local'),
  resolve(process.cwd(), '.env'),
  resolve(process.cwd(), '../../.env'),
] });

export default defineConfig({
  schema: 'apps/api/prisma/schema.prisma',
  migrations: { path: 'apps/api/prisma/migrations' },
  datasource: {
    url: process.env.DATABASE_URL ?? 'postgresql://taskflow:taskflow@localhost:5432/taskflow?schema=public'
  }
});
