import app, { prisma } from './app.js';
const port = Number(process.env.PORT ?? 4000);
const server = app.listen(port, () => console.log(`TaskFlow API listening on ${port}`));
async function shutdown() { server.close(); await prisma.$disconnect(); }
process.on('SIGINT', shutdown); process.on('SIGTERM', shutdown);
