# Deployment Guide — MargDarshak AI on WhatsApp

This guide takes you from a working local app to a live WhatsApp bot in production.

---

## Table of Contents

1. [Environment variables — single source of truth](#1-environment-variables--single-source-of-truth)
2. [Run locally with Docker Compose](#2-run-locally-with-docker-compose)
3. [Get a public HTTPS URL](#3-get-a-public-https-url)
4. [Deploy to a hosting platform](#4-deploy-to-a-hosting-platform)
5. [Configure Meta WhatsApp webhook](#5-configure-meta-whatsapp-webhook)
6. [Test the live bot](#6-test-the-live-bot)
7. [Go to production (real phone number)](#7-go-to-production-real-phone-number)
8. [Token rotation & security](#8-token-rotation--security)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Environment variables — single source of truth

All config lives in **one file**: `.env` (gitignored, never committed).

| Variable | Required | Where to get it | Example |
|---|---|---|---|
| `PORT` | no | — | `8080` |
| `SPRING_DATASOURCE_URL` | yes | DB host | `jdbc:postgresql://localhost:5432/margdarshak` |
| `DB_USERNAME` | yes | DB user | `postgres` |
| `DB_PASSWORD` | yes | DB pass | `postgres` |
| `AI_PROVIDER` | yes | `groq`, `gemini`, or `claude` | `groq` |
| `GROQ_API_KEY` | if AI_PROVIDER=groq | [console.groq.com/keys](https://console.groq.com/keys) | `gsk_...` |
| `GROQ_MODEL` | no | Override main chat model | `llama-3.3-70b-versatile` |
| `GROQ_FAST_MODEL` | no | Override intent classifier model | `llama-3.1-8b-instant` |
| `GEMINI_API_KEY` | if AI_PROVIDER=gemini | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | `AIza...` |
| `ANTHROPIC_API_KEY` | if AI_PROVIDER=claude | [console.anthropic.com](https://console.anthropic.com) | `sk-ant-...` |
| `WHATSAPP_PHONE_NUMBER_ID` | yes | Meta App > WhatsApp > API Setup | `1123282547526392` |
| `WHATSAPP_ACCESS_TOKEN` | yes | Meta App > WhatsApp > API Setup | `EAARZC3...` |
| `WHATSAPP_VERIFY_TOKEN` | yes | **You invent this** (any random string) | `margdarshak-verify-2026` |
| `WHATSAPP_GRAPH_API_VERSION` | no | Match the version in your Meta curl | `v25.0` |

**Workflow:**

1. Copy the template — `cp .env.example .env`
2. Edit `.env` with your real values
3. For local dev: `docker-compose up` automatically reads `.env`
4. For production: paste each variable into your hosting platform's env config UI (do **not** upload `.env`)

---

## 2. Run locally with Docker Compose

The fastest way to verify everything works:

```bash
docker-compose up --build
```

This boots:
- **postgres** on `localhost:5432` (data persists in a Docker volume)
- **app** on `localhost:8080`

Test the chat API directly (no WhatsApp needed yet):

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":"test-user","message":"Best time to buy a car"}'
```

Stop everything: `docker-compose down`
Reset database: `docker-compose down -v`

---

## 3. Get a public HTTPS URL

Meta requires the webhook to be **HTTPS** with a valid certificate. Two paths:

### Path A — Test via ngrok (fastest, for development)

Useful to test the WhatsApp webhook against your **localhost** before deploying.

```bash
# Install ngrok: https://ngrok.com/download
ngrok http 8080
```

ngrok prints a URL like `https://abc123.ngrok-free.app`. Use this as your webhook URL in Meta. Note: free ngrok URLs change every restart.

### Path B — Deploy to a cloud platform (for production)

See the next section.

---

## 4. Deploy to a hosting platform

The repo includes a `Dockerfile`, so any container-friendly platform works. Here are three free-tier options ranked by ease.

### Option 1 — Railway.app (recommended)

**Pros:** Generous free tier, free Postgres included, no cold starts, auto-deploys from GitHub on push.

1. Push the repo to GitHub
2. Go to [railway.app](https://railway.app) > sign in with GitHub
3. **New Project** > **Deploy from GitHub repo** > pick your repo
4. Railway auto-detects the `Dockerfile` and starts building
5. **Add a Postgres plugin:** Project > **+ New** > **Database** > **PostgreSQL**
6. Click on your **app service** > **Variables** tab > paste each var from your `.env`. Use Railway's reference variables for the DB:

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_USERNAME=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   AI_PROVIDER=groq
   GROQ_API_KEY=<your key>
   WHATSAPP_PHONE_NUMBER_ID=1123282547526392
   WHATSAPP_ACCESS_TOKEN=<your permanent token>
   WHATSAPP_VERIFY_TOKEN=margdarshak-verify-2026
   WHATSAPP_GRAPH_API_VERSION=v25.0
   ```

7. **Settings** tab > **Networking** > **Generate Domain**. You'll get something like `https://margdarshak-ai-production.up.railway.app`.
8. Your webhook URL is `https://<your-railway-domain>/webhook`

### Option 2 — Fly.io

**Pros:** Free tier, fast cold-start, multi-region.

```bash
# Install flyctl: https://fly.io/docs/hands-on/install-flyctl/
flyctl auth signup     # or: flyctl auth login
flyctl launch          # detects Dockerfile, asks a few questions

# Add Postgres
flyctl postgres create --name margdarshak-db
flyctl postgres attach margdarshak-db

# Set the rest of your secrets
flyctl secrets set \
  AI_PROVIDER=groq \
  GROQ_API_KEY=<your key> \
  WHATSAPP_PHONE_NUMBER_ID=1123282547526392 \
  WHATSAPP_ACCESS_TOKEN=<your permanent token> \
  WHATSAPP_VERIFY_TOKEN=margdarshak-verify-2026 \
  WHATSAPP_GRAPH_API_VERSION=v25.0

flyctl deploy
flyctl status          # gives you https://<app>.fly.dev
```

When `flyctl postgres attach` runs, it auto-injects `DATABASE_URL`. Spring Boot doesn't auto-read this. Either:

- Set `SPRING_DATASOURCE_URL` manually using the values from `flyctl postgres connect`, OR
- Configure Spring to read `DATABASE_URL` (small code change — ask if you want this).

### Option 3 — Render.com

**Pros:** Simple UI.
**Cons:** Free tier sleeps after 15 min of inactivity (causes 30+ second cold-start delays — bad for webhooks). Use the paid Starter plan ($7/mo) if going live on Render.

1. [render.com](https://render.com) > **New** > **Web Service** > connect GitHub repo
2. Pick **Docker** runtime
3. Add **PostgreSQL** as a separate service
4. Set env vars from `.env`
5. Use the auto-generated `https://<app>.onrender.com` URL

---

## 5. Configure Meta WhatsApp webhook

Once your app is live at a public HTTPS URL:

1. Go to [developers.facebook.com](https://developers.facebook.com) > Your App > **WhatsApp** > **Configuration**
2. Under **Webhook**, click **Edit**
3. Fill in:
   - **Callback URL:** `https://<your-public-url>/webhook`
   - **Verify token:** the **exact same string** as `WHATSAPP_VERIFY_TOKEN` in your env (e.g. `margdarshak-verify-2026`)
4. Click **Verify and Save**

   Meta sends a `GET /webhook?hub.mode=subscribe&hub.verify_token=...&hub.challenge=...` request. Your `WhatsAppController` echoes the challenge back. If env vars are right, Meta marks the webhook as verified.

5. Under **Webhook fields**, click **Manage** > **Subscribe** to **`messages`**

   This is the only field you need to receive incoming messages. Optionally also subscribe to `message_status` if you want delivery/read receipts later.

---

## 6. Test the live bot

1. In Meta App > WhatsApp > **API Setup**, scroll to "Send and receive messages"
2. Add your personal WhatsApp number to the recipient list (free tier allows up to 5 test numbers)
3. From your personal WhatsApp, send a message to the **test business number** that Meta provided
4. Within ~2 seconds you should receive an AI-generated reply
5. Check your platform logs to see the webhook being processed:
   - Railway: Project > app service > **Deployments** > **View Logs**
   - Fly: `flyctl logs`
   - Render: Service > **Logs** tab

If nothing happens, see [Troubleshooting](#9-troubleshooting).

---

## 7. Go to production (real phone number)

Currently you're using Meta's free test phone number. Real users can only message it if they're on your test recipient list. To open it up to anyone:

### Step 7a — Add your own phone number

1. Use a phone number that is **not currently registered on WhatsApp** (personal or business). If it is, you must first delete the WhatsApp account on that number from settings.
2. Meta App > WhatsApp > **API Setup** > **Add phone number**
3. Verify via SMS or voice call code
4. Update `WHATSAPP_PHONE_NUMBER_ID` in your hosting platform's env vars to the new ID

### Step 7b — Submit for App Review

This is required before non-test users can message your bot.

1. App Dashboard > **App Review** > **Permissions and Features**
2. Request **Advanced Access** for:
   - `whatsapp_business_messaging`
   - `whatsapp_business_management`
3. You'll need:
   - A **Privacy Policy URL** (host a simple page on your domain or GitHub Pages)
   - A **demo video** (60-90 seconds) showing the bot replying to messages
   - A **business use case description**
4. Submit. Review usually takes 1-3 business days.

### Step 7c — Switch app to Live mode

Once approved, in App Dashboard toggle the switch from **Development** to **Live**. Done — anyone in the world can now message your business number.

---

## 8. Token rotation & security

### Critical: replace the temporary token

The `WHATSAPP_ACCESS_TOKEN` you got from API Setup expires in **24 hours**. The bot will silently stop replying when it expires. Replace it before deploying:

1. [business.facebook.com](https://business.facebook.com) > **Business Settings** > **System Users** > **Add**
   - Name: `margdarshak-prod`
   - Role: **Admin**
2. Click the new system user > **Add Assets** > **Apps** > select your WhatsApp app > tick **Full control**
3. **Generate New Token**
   - App: your WhatsApp app
   - Token Expiration: **Never**
   - Permissions: `whatsapp_business_messaging`, `whatsapp_business_management`
4. Copy the token, update `WHATSAPP_ACCESS_TOKEN` everywhere (your `.env` locally + your hosting platform's env vars)

### Rotating leaked tokens

If a token is ever exposed (e.g. committed by mistake):

1. **Revoke immediately** from Business Settings > System Users > the user > **Tokens** > revoke
2. Generate a new one and redeploy

### Security hardening (future work — not blocking go-live)

- **Webhook signature validation** — verify the `X-Hub-Signature-256` header against your Meta App Secret on every webhook POST. Without this, anyone who knows your URL can POST fake messages.
- **Rate limiting** on `/webhook` (Spring `@nestjs/throttler` equivalent: `bucket4j`)
- **Database connection pooling tuning** for production load (HikariCP defaults are usually fine)

Ask if you want any of these implemented.

---

## 9. Troubleshooting

### Webhook verification fails ("Could not validate URL")

- The `WHATSAPP_VERIFY_TOKEN` in your env **must exactly match** the verify token you typed into Meta's webhook config (case-sensitive, no leading/trailing spaces)
- Your URL must be reachable. Test: `curl https://<your-url>/webhook?hub.mode=subscribe&hub.verify_token=<token>&hub.challenge=test123` — should return `test123`
- Your URL must be HTTPS with a valid cert (no self-signed)

### Bot receives messages but doesn't reply

- Check `WHATSAPP_ACCESS_TOKEN` — most common cause is expiration (24h temp token)
- Check logs: look for `403` or `401` from `graph.facebook.com`
- Make sure `WHATSAPP_PHONE_NUMBER_ID` is correct (not the WhatsApp Business Account ID — those are different)
- Make sure `WHATSAPP_GRAPH_API_VERSION` matches a supported version (currently `v25.0`)

### App fails to start with "no AI provider key"

- `AiConfig` validates that the active provider's key is set. Check `AI_PROVIDER` and the matching key (`GROQ_API_KEY`, `GEMINI_API_KEY`, or `ANTHROPIC_API_KEY`) are both present.

### Database connection fails on Railway/Fly

- Railway: use the **reference variable syntax** `${{Postgres.PGHOST}}` etc., not raw values (raw values change when DB restarts)
- Fly: after `flyctl postgres attach`, manually set `SPRING_DATASOURCE_URL` since Spring doesn't auto-parse the `DATABASE_URL` Fly provides

### "Recipient phone number not in allowed list" error

- You're still in Development mode and the recipient isn't in your test list. Either add them in API Setup, or complete App Review and switch to Live.

### Messages go through but template-only (no free-form replies)

- WhatsApp has a **24-hour customer service window**: free-form replies are only allowed within 24h of the user's last message. Outside that window you must use approved message templates.
- This bot replies to incoming messages, so this is only an issue if you build proactive notifications later.

---

## Quick reference card

```bash
# Local dev
cp .env.example .env       # then edit .env with real values
docker-compose up --build

# Deploy (Railway flow)
git push                   # auto-deploys

# Deploy (Fly flow)
flyctl deploy

# Tail prod logs
flyctl logs                # Fly
# Railway: web UI > Deployments > View Logs

# Rotate WhatsApp token
# 1. Generate new token in Business Settings > System Users
# 2. Update WHATSAPP_ACCESS_TOKEN in platform env vars
# 3. Restart the app (Railway/Fly auto-restart on env change)
```
