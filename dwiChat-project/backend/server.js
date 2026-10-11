import 'dotenv/config';
import http from 'node:http';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import pg from 'pg';
import { Server } from 'socket.io';

const { Pool } = pg;
const required = ['DATABASE_URL', 'JWT_SECRET'];
for (const key of required) if (!process.env[key]) throw new Error(`Missing required environment variable: ${key}`);
if (process.env.JWT_SECRET.length < 32) throw new Error('JWT_SECRET must be at least 32 characters');
const pool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: process.env.PGSSL === 'true' ? { rejectUnauthorized: true } : undefined });
const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: process.env.CORS_ORIGIN || '*', methods: ['GET', 'POST'] } });
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '16kb' }));
app.get('/health', async (_req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, service: 'dwiChat-api' }); }
  catch { res.status(503).json({ ok: false, error: 'database_unavailable' }); }
});
const issueToken = (user) => jwt.sign({ sub: user.id, username: user.username }, process.env.JWT_SECRET, { expiresIn: '7d', issuer: 'dwichat' });
function getToken(req) { return (req.headers.authorization || '').replace(/^Bearer\s+/i, ''); }
function auth(req, res, next) {
  try { const claims = jwt.verify(getToken(req), process.env.JWT_SECRET, { issuer: 'dwichat' }); req.user = { id: claims.sub, username: claims.username }; next(); }
  catch { res.status(401).json({ error: 'unauthorized' }); }
}
function validUsername(value) { return typeof value === 'string' && /^[a-zA-Z0-9_]{3,24}$/.test(value); }
function validPassword(value) { return typeof value === 'string' && value.length >= 8 && value.length <= 72; }
app.post('/auth/register', async (req, res) => {
  const { username, password } = req.body || {};
  if (!validUsername(username) || !validPassword(password)) return res.status(400).json({ error: 'username_must_be_3_to_24_letters_digits_or_underscores_and_password_8_to_72_chars' });
  try {
    const hash = await bcrypt.hash(password, 12);
    const { rows } = await pool.query('INSERT INTO users(username,password_hash) VALUES($1,$2) RETURNING id,username', [username, hash]);
    const user = rows[0]; res.status(201).json({ token: issueToken(user), user });
  } catch (e) { if (e.code === '23505') return res.status(409).json({ error: 'username_taken' }); console.error(e); res.status(500).json({ error: 'server_error' }); }
});
app.post('/auth/login', async (req, res) => {
  const { username, password } = req.body || {};
  if (typeof username !== 'string' || typeof password !== 'string') return res.status(400).json({ error: 'invalid_credentials' });
  try {
    const { rows } = await pool.query('SELECT id,username,password_hash FROM users WHERE username=$1', [username]);
    if (!rows[0] || !(await bcrypt.compare(password, rows[0].password_hash))) return res.status(401).json({ error: 'invalid_credentials' });
    const user = { id: rows[0].id, username: rows[0].username }; res.json({ token: issueToken(user), user });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server_error' }); }
});
app.get('/users/search', auth, async (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 1 || q.length > 24) return res.json({ users: [] });
  try {
    const { rows } = await pool.query('SELECT id,username FROM users WHERE username ILIKE $1 AND id <> $2 ORDER BY username LIMIT 20', [`%${q.replace(/[\\%_]/g, '\\$&')}%`, req.user.id]);
    res.json({ users: rows });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server_error' }); }
});
async function isMember(conversationId, userId) {
  const { rows } = await pool.query('SELECT id FROM conversations WHERE id=$1 AND (user_a=$2 OR user_b=$2)', [conversationId, userId]);
  return rows.length > 0;
}
app.get('/conversations', auth, async (req, res) => {
  try {
    const { rows } = await pool.query(`SELECT c.id, u.id AS other_id, u.username AS other_username,
      (SELECT body FROM messages m WHERE m.conversation_id=c.id ORDER BY m.created_at DESC LIMIT 1) AS last_message,
      COALESCE((SELECT created_at FROM messages m WHERE m.conversation_id=c.id ORDER BY m.created_at DESC LIMIT 1), c.created_at) AS updated_at
      FROM conversations c JOIN users u ON u.id = CASE WHEN c.user_a=$1 THEN c.user_b ELSE c.user_a END
      WHERE c.user_a=$1 OR c.user_b=$1 ORDER BY updated_at DESC`, [req.user.id]);
    res.json({ conversations: rows.map(r => ({ id: r.id, otherUser: { id: r.other_id, username: r.other_username }, lastMessage: r.last_message, updatedAt: r.updated_at })) });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server_error' }); }
});
app.post('/conversations', auth, async (req, res) => {
  const target = req.body?.userId;
  if (typeof target !== 'string' || target === req.user.id) return res.status(400).json({ error: 'invalid_user' });
  try {
    const { rows: targetRows } = await pool.query('SELECT id,username FROM users WHERE id=$1', [target]);
    if (!targetRows[0]) return res.status(404).json({ error: 'user_not_found' });
    const [a,b] = [req.user.id, target].sort();
    const { rows } = await pool.query(`INSERT INTO conversations(user_a,user_b) VALUES($1,$2)
      ON CONFLICT(user_a,user_b) DO UPDATE SET user_a=EXCLUDED.user_a RETURNING id,created_at`, [a,b]);
    res.status(200).json({ id: rows[0].id, otherUser: targetRows[0], lastMessage: null, updatedAt: rows[0].created_at });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server_error' }); }
});
app.get('/conversations/:id/messages', auth, async (req, res) => {
  try {
    if (!(await isMember(req.params.id, req.user.id))) return res.status(403).json({ error: 'forbidden' });
    const { rows } = await pool.query(`SELECT m.id,m.conversation_id,m.sender_id,u.username AS sender_username,m.body,m.created_at
      FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=$1 ORDER BY m.created_at ASC LIMIT 500`, [req.params.id]);
    res.json({ messages: rows.map(mapMessage) });
  } catch (e) { console.error(e); res.status(500).json({ error: 'server_error' }); }
});
function mapMessage(m) { return { id: m.id, conversationId: m.conversation_id, senderId: m.sender_id, senderUsername: m.sender_username, body: m.body, createdAt: m.created_at }; }

io.use((socket, next) => {
  try { const token = socket.handshake.auth?.token; const claims = jwt.verify(token, process.env.JWT_SECRET, { issuer: 'dwichat' }); socket.user = { id: claims.sub, username: claims.username }; next(); }
  catch { next(new Error('unauthorized')); }
});
io.on('connection', socket => {
  socket.on('conversation:join', async (payload = {}) => {
    const id = String(payload.conversationId || '');
    try { if (id && await isMember(id, socket.user.id)) socket.join(`conversation:${id}`); }
    catch (e) { console.error(e); }
  });
  socket.on('message:send', async (payload = {}, ack) => {
    const conversationId = String(payload.conversationId || '');
    const body = typeof payload.body === 'string' ? payload.body.trim() : '';
    try {
      if (!conversationId || body.length < 1 || body.length > 4000) return typeof ack === 'function' && ack({ error: 'invalid_message' });
      if (!(await isMember(conversationId, socket.user.id))) return typeof ack === 'function' && ack({ error: 'forbidden' });
      const { rows } = await pool.query(`INSERT INTO messages(conversation_id,sender_id,body) VALUES($1,$2,$3)
        RETURNING id,conversation_id,sender_id,body,created_at`, [conversationId, socket.user.id, body]);
      const message = mapMessage({ ...rows[0], sender_username: socket.user.username });
      io.to(`conversation:${conversationId}`).emit('message:new', message);
      if (typeof ack === 'function') ack({ ok: true, message });
    } catch (e) { console.error(e); if (typeof ack === 'function') ack({ error: 'server_error' }); }
  });
});
const port = Number(process.env.PORT || 3000);
server.listen(port, () => console.log(`dwiChat API listening on port ${port}`));
