import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import app, { prisma } from '../src/app.js';

const server = app;
async function register(email: string) {
  const response = await request(server).post('/api/auth/register').send({ email, name: 'Test User', password: 'secure-passphrase-123' });
  expect(response.status).toBe(201);
  return response.body.token as string;
}
describe('team task API', () => {
  beforeAll(async () => { await prisma.$connect(); });
  beforeEach(async () => { await prisma.task.deleteMany(); await prisma.membership.deleteMany(); await prisma.team.deleteMany(); await prisma.user.deleteMany(); });
  afterAll(async () => { await prisma.$disconnect(); });

  it('requires authentication and validates registration input', async () => {
    expect((await request(server).get('/api/teams')).status).toBe(401);
    expect((await request(server).post('/api/auth/register').send({ email: 'bad', name: '', password: 'short' })).status).toBe(400);
  });

  it('creates a team and enforces team-scoped roles for task CRUD', async () => {
    const ownerToken = await register('owner@example.com');
    const memberToken = await register('member@example.com');
    const outsiderToken = await register('outsider@example.com');
    const teamResponse = await request(server).post('/api/teams').set('Authorization', `Bearer ${ownerToken}`).send({ name: 'Product' });
    expect(teamResponse.status).toBe(201);
    const teamId = teamResponse.body.team.id as string;
    expect((await request(server).post(`/api/teams/${teamId}/members`).set('Authorization', `Bearer ${ownerToken}`).send({ email: 'member@example.com' })).status).toBe(201);
    const created = await request(server).post(`/api/teams/${teamId}/tasks`).set('Authorization', `Bearer ${memberToken}`).send({ title: 'Ship API', priority: 'HIGH' });
    expect(created.status).toBe(201);
    const taskId = created.body.task.id as string;
    expect((await request(server).get(`/api/teams/${teamId}/tasks`).set('Authorization', `Bearer ${outsiderToken}`)).status).toBe(404);
    const page = await request(server).get(`/api/teams/${teamId}/tasks?priority=HIGH&page=1&limit=1`).set('Authorization', `Bearer ${memberToken}`);
    expect(page.body.pagination).toMatchObject({ page: 1, limit: 1, total: 1, totalPages: 1 });
    expect(page.body.data[0].title).toBe('Ship API');
    expect((await request(server).delete(`/api/teams/${teamId}/tasks/${taskId}`).set('Authorization', `Bearer ${memberToken}`)).status).toBe(403);
    expect((await request(server).patch(`/api/teams/${teamId}/tasks/${taskId}`).set('Authorization', `Bearer ${memberToken}`).send({ status: 'DONE' })).body.task.status).toBe('DONE');
    expect((await request(server).delete(`/api/teams/${teamId}/tasks/${taskId}`).set('Authorization', `Bearer ${ownerToken}`)).status).toBe(204);
    expect((await request(server).get(`/api/teams/${teamId}/tasks`).set('Authorization', `Bearer ${memberToken}`)).body.pagination.total).toBe(0);
  });
});
