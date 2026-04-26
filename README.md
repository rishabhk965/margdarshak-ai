# MargDarshak AI

Astrology Assistant Chatbot for Indian users, built with Spring Boot and a switchable AI backend, integrated with WhatsApp via Meta's Cloud API.

> **Deploying to production?** See [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the step-by-step guide to going live on WhatsApp.

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- PostgreSQL 15+ (or use the bundled `docker-compose.yml`)
- An API key for at least one AI provider (see below)
- (For WhatsApp) A Meta Developer account with a WhatsApp app

## Quick Start (local dev)

```bash
cp .env.example .env       # then edit .env with your real values
docker-compose up --build  # starts Postgres + the app
```

Then test:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":"test-user","message":"Best time to buy a car"}'
```

All configuration lives in **a single `.env` file**. See [`.env.example`](./.env.example) for the full list of variables and where to get each value.

## AI Provider Setup

The app supports multiple LLM backends. Switch between them by setting **one env var**.

### Supported Providers

| Provider | Models used | Free tier | Get a key |
|----------|------------|-----------|-----------|
| **Gemini** (default) | `gemini-2.0-flash`, `gemini-2.0-flash-lite` | 15 RPM, 1500 req/day | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **Claude** | `claude-sonnet-4-5`, `claude-haiku-4-5` | Paid only | [console.anthropic.com](https://console.anthropic.com) |

### Switching Providers

**Use Gemini (free, default):**
```bash
export AI_PROVIDER=gemini
export GEMINI_API_KEY=your-gemini-key
```

**Use Claude:**
```bash
export AI_PROVIDER=claude
export ANTHROPIC_API_KEY=your-anthropic-key
```

Only the active provider's key is required. The other can be left unset.

### Adding a New Provider

1. Create a class implementing `AiService` (see `GeminiAiService` or `ClaudeAiService`)
2. Add a new `case` in `AiConfig.aiService()` factory method
3. Add the API key property to `application.yml`

## Setup (without Docker)

If you'd rather run Postgres directly:

1. Create a PostgreSQL database:
   ```sql
   CREATE DATABASE margdarshak;
   ```

2. Copy and configure env:
   ```bash
   cp .env.example .env
   # edit .env
   set -a && source .env && set +a
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## API

### Chat
```
POST /api/chat
{
  "userId": "user-123",
  "message": "Best time to buy a car"
}
```

### Users
```
POST /api/users
GET  /api/users/{id}
GET  /api/users/{id}/history
```

### WhatsApp Webhook
```
GET  /webhook   - Meta verification handshake
POST /webhook   - Incoming WhatsApp messages
```

### Health Check
```
GET  /actuator/health   - Used by Railway/Fly/Render to detect a healthy instance
```

## Deployment

See [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the full guide covering:

- All environment variables and where to get each value
- Local dev with Docker Compose
- Deploying to Railway, Fly.io, or Render
- Configuring the Meta WhatsApp webhook
- Going from test number to real production phone number
- Token rotation and security
