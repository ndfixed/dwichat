# dwiChat — Android + backend starter

A real starter project for username-based registration/login and private one-to-one messaging. The server is **not hosted or active by this repository**. You must configure PostgreSQL, environment variables, and a reachable backend before messages can work across devices.

## Included
- Android native UI: Kotlin + Jetpack Compose
- Username/password registration and login (password hashes stored with bcrypt)
- Search users by username
- Create/open one-to-one conversations
- REST API + Socket.IO real-time message delivery
- PostgreSQL persistence for users, conversations, and messages

## Backend setup (Node.js 20+ and PostgreSQL)
1. Create a PostgreSQL database and user, then grant access.
2. In a terminal:
   ```bash
   cd backend
   npm install
   cp .env.example .env
   ```
3. Edit `.env`. Set `DATABASE_URL` and a random `JWT_SECRET` of at least 32 characters.
4. Apply schema:
   ```bash
   psql "$DATABASE_URL" -f schema.sql
   ```
5. Start the API:
   ```bash
   npm run dev
   ```
6. Check `http://localhost:3000/health`.

Do not expose the server publicly over plain HTTP. For production, use HTTPS/WSS, set a specific `CORS_ORIGIN`, enable database backups, add rate limiting, and review logging and account recovery before inviting users.

## Android setup
1. Open the `dwiChat-project` folder in Android Studio (JDK 17; Android SDK 35 recommended).
2. Let Gradle sync, then run the `app` configuration.
3. Default API URL in `app/build.gradle.kts` is `http://10.0.2.2:3000/`, which reaches the host machine from the Android emulator.
4. For a physical phone, change `API_BASE_URL` to your computer/server's LAN IP for local testing, or your HTTPS backend URL for deployment. Phone and computer must be reachable on the same network for LAN testing.
5. Build an APK using **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The manifest permits cleartext traffic for local development. Remove `usesCleartextTraffic="true"` and use HTTPS before production release. The JWT token is held in memory in this starter, so logging out or closing the app requires login again. Add Android Keystore-backed secure token storage before production.

## API overview
- `POST /auth/register` `{ "username": "ndifix", "password": "at-least-8-chars" }`
- `POST /auth/login`
- `GET /users/search?q=...` with `Authorization: Bearer <token>`
- `GET /conversations`
- `POST /conversations` `{ "userId": "uuid" }`
- `GET /conversations/:id/messages`
- Socket.IO auth: `{ auth: { token: "<JWT>" } }`
- Socket events: `conversation:join`, `message:send`, `message:new`

## Current starter limitations
- No end-to-end encryption; TLS only protects data in transit when deployed correctly.
- No push notifications, media uploads, group chats, read receipts, password reset, or abuse/rate limiting yet.
- The UI does not persist login tokens and does not yet show detailed network/server status.
- Socket message sending is persisted by the server, but the Android UI currently relies on `message:new`; reconnect/resync improvements are needed for production reliability.
- The project source is provided; no APK has been built or tested in this environment.
