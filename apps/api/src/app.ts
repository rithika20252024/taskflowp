import 'dotenv/config';
import express, { NextFunction, Request, Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { Prisma, PrismaClient, Role, TaskPriority, TaskStatus } from './generated/prisma/client.js';
import { PrismaPg } from '@prisma/adapter-pg';
import { z, ZodError } from 'zod';

const databaseUrl = process.env.DATABASE_URL ?? 'postgresql://taskflow:taskflow@localhost:5432/taskflow?schema=public';
export const prisma = new PrismaClient({ adapter: new PrismaPg({ connectionString: databaseUrl }) });
const app = express();
const secret = process.env.JWT_SECRET ?? (process.env.NODE_ENV === 'production' ? '' : 'development-only-change-this-secret-32-chars');
if (secret.length < 32) throw new Error('JWT_SECRET must contain at least 32 characters');
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN?.split(',') ?? 'http://localhost:3000' }));
app.use(express.json({ limit: '1mb' }));
app.use((_req, res, next) => {
  const startedAt = process.hrtime.bigint();
  res.on('finish', () => {
    const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
    console.info(JSON.stringify({ event: 'http_request', method: _req.method, path: _req.path, status: res.statusCode, durationMs: Number(elapsedMs.toFixed(2)) }));
  });
  next();
});

type AuthRequest = Request & { userId?: string };
const wrap = (fn: (req: AuthRequest, res: Response, next: NextFunction) => Promise<unknown>) =>
  (req: Request, res: Response, next: NextFunction) => Promise.resolve(fn(req as AuthRequest, res, next)).catch(next);
const auth = (req: AuthRequest, res: Response, next: NextFunction) => {
  const token = req.header('authorization')?.replace(/^Bearer\s+/i, '');
  if (!token) return res.status(401).json({ error: 'Authentication required' });
  try { req.userId = (jwt.verify(token, secret) as jwt.JwtPayload).sub; next(); }
  catch { return res.status(401).json({ error: 'Invalid or expired token' }); }
};
const idParam = z.string().min(1).max(64);
const routeParam = (value: string | string[]) => Array.isArray(value) ? value[0] ?? '' : value;
const registerSchema = z.object({ email: z.string().email().max(254), name: z.string().trim().min(1).max(80), password: z.string().min(10).max(100) });
const credentialsSchema = z.object({ email: z.string().email(), password: z.string().min(1) });
const taskCreateSchema = z.object({ title: z.string().trim().min(1).max(160), description: z.string().max(4000).optional(), priority: z.nativeEnum(TaskPriority).default('MEDIUM'), dueDate: z.string().datetime().optional() });
const taskPatchSchema = taskCreateSchema.partial().extend({ status: z.nativeEnum(TaskStatus).optional() });

app.get('/health', (_req, res) => res.json({ status: 'ok' }));
app.post('/api/auth/register', wrap(async (req, res) => {
  const data = registerSchema.parse(req.body);
  const passwordHash = await bcrypt.hash(data.password, 12);
  const user = await prisma.user.create({ data: { email: data.email.toLowerCase(), name: data.name, passwordHash }, select: { id: true, email: true, name: true } });
  const token = jwt.sign({}, secret, { subject: user.id, expiresIn: '12h' });
  res.status(201).json({ user, token });
}));
app.post('/api/auth/login', wrap(async (req, res) => {
  const data = credentialsSchema.parse(req.body);
  const user = await prisma.user.findUnique({ where: { email: data.email.toLowerCase() } });
  if (!user || !(await bcrypt.compare(data.password, user.passwordHash))) return res.status(401).json({ error: 'Invalid email or password' });
  const token = jwt.sign({}, secret, { subject: user.id, expiresIn: '12h' });
  res.json({ user: { id: user.id, email: user.email, name: user.name }, token });
}));
app.get('/api/auth/me', auth, wrap(async (req, res) => {
  const user = await prisma.user.findUnique({ where: { id: req.userId }, select: { id: true, email: true, name: true } });
  if (!user) return res.status(401).json({ error: 'Account not found' });
  res.json({ user });
}));

app.post('/api/teams', auth, wrap(async (req, res) => {
  const { name } = z.object({ name: z.string().trim().min(1).max(100) }).parse(req.body);
  const team = await prisma.team.create({ data: { name, memberships: { create: { userId: req.userId!, role: 'OWNER' } } } });
  res.status(201).json({ team });
}));
app.get('/api/teams', auth, wrap(async (req, res) => {
  const teams = await prisma.team.findMany({ where: { memberships: { some: { userId: req.userId } } }, orderBy: { createdAt: 'desc' }, select: { id: true, name: true, createdAt: true, memberships: { where: { userId: req.userId }, select: { role: true } } } });
  res.json({ teams: teams.map(({ memberships, ...team }) => ({ ...team, role: memberships[0]?.role })) });
}));

async function teamRole(userId: string, teamId: string) {
  const id = idParam.parse(teamId);
  const member = await prisma.membership.findUnique({ where: { userId_teamId: { userId, teamId: id } }, select: { role: true } });
  return member?.role;
}
async function requireTeam(req: AuthRequest, res: Response, role?: Role) {
  const membershipRole = await teamRole(req.userId!, routeParam(req.params.teamId));
  if (!membershipRole) { res.status(404).json({ error: 'Team not found' }); return false; }
  if (role && membershipRole === 'MEMBER') { res.status(403).json({ error: 'Team admin permission required' }); return false; }
  return true;
}
app.post('/api/teams/:teamId/members', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res, 'ADMIN'))) return;
  const { email, role } = z.object({ email: z.string().email(), role: z.enum(['ADMIN', 'MEMBER']).default('MEMBER') }).parse(req.body);
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase() } });
  if (!user) { res.status(404).json({ error: 'User not found; ask them to create an account first' }); return; }
  const membership = await prisma.membership.create({ data: { userId: user.id, teamId: routeParam(req.params.teamId), role } });
  res.status(201).json({ membership: { userId: membership.userId, teamId: membership.teamId, role: membership.role } });
}));

app.get('/api/teams/:teamId/tasks', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res))) return;
  const query = z.object({ page: z.coerce.number().int().min(1).default(1), limit: z.coerce.number().int().min(1).max(100).default(20), status: z.nativeEnum(TaskStatus).optional(), priority: z.nativeEnum(TaskPriority).optional(), sort: z.enum(['createdAt', 'updatedAt', 'dueDate', 'priority', 'title']).default('createdAt'), order: z.enum(['asc', 'desc']).default('desc') }).parse(req.query);
  const where = { teamId: routeParam(req.params.teamId), ...(query.status ? { status: query.status } : {}), ...(query.priority ? { priority: query.priority } : {}) };
  const [tasks, total] = await prisma.$transaction([
    prisma.task.findMany({ where, orderBy: { [query.sort]: query.order }, skip: (query.page - 1) * query.limit, take: query.limit }),
    prisma.task.count({ where })
  ]);
  res.json({ data: tasks, pagination: { page: query.page, limit: query.limit, total, totalPages: Math.ceil(total / query.limit) } });
}));
app.post('/api/teams/:teamId/tasks', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res))) return;
  const input = taskCreateSchema.parse(req.body);
  const task = await prisma.task.create({ data: { ...input, dueDate: input.dueDate ? new Date(input.dueDate) : undefined, teamId: routeParam(req.params.teamId), createdById: req.userId! } });
  res.status(201).json({ task });
}));
app.get('/api/teams/:teamId/tasks/:taskId', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res))) return;
  const task = await prisma.task.findFirst({ where: { id: idParam.parse(routeParam(req.params.taskId)), teamId: routeParam(req.params.teamId) } });
  if (!task) { res.status(404).json({ error: 'Task not found' }); return; }
  res.json({ task });
}));
app.patch('/api/teams/:teamId/tasks/:taskId', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res))) return;
  const input = taskPatchSchema.parse(req.body);
  const taskId = idParam.parse(routeParam(req.params.taskId));
  const exists = await prisma.task.findFirst({ where: { id: taskId, teamId: routeParam(req.params.teamId) }, select: { id: true } });
  if (!exists) { res.status(404).json({ error: 'Task not found' }); return; }
  const task = await prisma.task.update({ where: { id: taskId }, data: { ...input, dueDate: input.dueDate ? new Date(input.dueDate) : undefined } });
  res.json({ task });
}));
app.delete('/api/teams/:teamId/tasks/:taskId', auth, wrap(async (req, res) => {
  if (!(await requireTeam(req, res, 'ADMIN'))) return;
  const taskId = idParam.parse(routeParam(req.params.taskId));
  const deleted = await prisma.task.deleteMany({ where: { id: taskId, teamId: routeParam(req.params.teamId) } });
  if (!deleted.count) { res.status(404).json({ error: 'Task not found' }); return; }
  res.status(204).end();
}));

app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (err instanceof ZodError) return res.status(400).json({ error: 'Validation failed', details: err.issues.map(i => ({ path: i.path.join('.'), message: i.message })) });
  if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2002') return res.status(409).json({ error: 'A record with that value already exists' });
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
});
export default app;
