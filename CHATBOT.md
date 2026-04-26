# MargDarshak AI — Chatbot Documentation

A Spring Boot backend that turns free-form Indian astrology questions into structured, grounded answers. One REST endpoint in, typed JSON out. Built to be predictable, cheap, and easy to extend.

---

## 1. What it does

The bot handles three real user jobs and explicitly rejects the fourth. Every user message is routed to exactly one of these:

| Intent | What the user wants | Example prompts |
|---|---|---|
| `MUHURTA` | Best/auspicious time for an activity | "Best time to buy a car", "Shubh muhurat for grih pravesh" |
| `FESTIVAL_GUIDE` | Steps, rituals, puja vidhi, do/don'ts | "How to do Diwali puja at home?", "Karwa Chauth vrat vidhi" |
| `ASTROLOGY_EXPLAINER` | Reassurance + explanation + remedies for life problems | "Why is my career stuck?", "Kya mere kundli mein koi dosh hai?" |
| `UNKNOWN` | Anything else | Returns a polite fallback, no LLM call wasted |

One endpoint: `POST /api/chat` with `{ userId, message }`. Returns `{ intent, result }` where `result` shape is intent-specific.

---

## 2. How it decides — the routing logic

Every message goes through the same pipeline:

```
User message
     │
     ▼
┌──────────────────────┐
│ IntentClassifier     │  ← Claude Haiku 4.5 (fast, cheap)
│ (AiIntentClassifier) │     System prompt with 4 labels + examples
└──────────┬───────────┘     Returns ONE label, nothing else
           │
           ▼
   Intent enum value
           │
           ▼
┌──────────────────────┐
│ IntentHandler map    │  ← Spring DI auto-wires all handlers
│ (by supportedIntent) │     Map<Intent, IntentHandler>
└──────────┬───────────┘
           │
  ┌────────┼────────────────────┬──────────────┐
  ▼        ▼                    ▼              ▼
Muhurta  FestivalGuide  AstrologyExplainer  (UNKNOWN →
Handler  Handler        Handler              default ChatResponse)
  │        │                    │
  ▼        ▼                    ▼
Each handler:
  1. Injects day-of-week astrological context (AstrologyService)
  2. Calls Claude Sonnet 4.5 with a structured system prompt
  3. Parses JSON response into a typed DTO (MuhurtaResult,
     FestivalResult, ExplainerResult)
  4. Returns ChatResponse

           │
           ▼
   ChatService saves: user message + intent label +
   response JSON to chat_history (Postgres, jsonb column)
```

**Key design choice:** classification and generation use **different models**. Classification is a labeling task → Haiku (~5-10x cheaper, ~3x faster). Generation needs reasoning → Sonnet.

---

## 3. The logic per handler

### 3.1 MuhurtaHandler

- Asks `AstrologyService.getContextForDate(today)` for weekday quality and a short planetary note.
- Embeds that context in a system prompt: "The user asks about auspicious timing. Today's context is X. Respond in JSON with `{status, timeWindow, message, reason}`."
- Parses the JSON into `MuhurtaResult`.

### 3.2 FestivalGuideHandler

- No date context needed — festivals are topic-based, not time-based.
- System prompt: "User wants ritual steps. Respond in JSON with `{festivalName, steps[], requiredItems[], warnings[]}`" where each step has `{stepNumber, action, reason}`.
- Parses into `FestivalResult` with nested `Step` objects.

### 3.3 AstrologyExplainerHandler

- Uses today's astrological context (same `AstrologyService` call as Muhurta).
- System prompt is intentionally empathetic: "The user is worried. Respond in JSON with `{explanation, reassurance, remedies[]}`."
- Parses into `ExplainerResult`.

### 3.4 The grounding trick — AstrologyService

This is the single most important piece. It's a **rule-based** service (no LLM) that returns deterministic daily context:

```java
public DayContext getContextForDate(LocalDate date) {
    return new DayContext(
        getDayQuality(date.getDayOfWeek()),        // "auspicious" / "neutral" / "cautious"
        getPlanetaryNote(date.getDayOfWeek())      // "Ruled by Jupiter — good for learning"
    );
}
```

Every handler pastes this into the LLM prompt. The model then **has** to reason on top of a fixed fact instead of hallucinating a completely fresh interpretation each call. Same input date → same context → much more consistent responses across sessions.

---

## 4. Architecture at a glance

```
src/main/java/com/margdarshak/ai/
├── controller/
│   └── ChatController.java          POST /api/chat
├── service/
│   ├── ChatService.java             Orchestrator: user, classify, dispatch, persist
│   ├── AiService.java               Interface: generate() + generateFast()
│   ├── ClaudeAiService.java         Impl: Sonnet for generate, Haiku for generateFast
│   ├── AstrologyService.java        Rule-based daily context (no LLM)
│   ├── IntentClassifier.java        Interface
│   └── AiIntentClassifier.java      Impl: Haiku + 4-label system prompt
├── handler/
│   ├── IntentHandler.java           Interface: supportedIntent(), handle()
│   ├── MuhurtaHandler.java
│   ├── FestivalGuideHandler.java
│   └── AstrologyExplainerHandler.java
├── dto/
│   ├── ChatRequest.java             { userId, message }
│   ├── ChatResponse.java            { intent, result }
│   ├── MuhurtaResult.java
│   ├── FestivalResult.java
│   └── ExplainerResult.java
├── model/
│   ├── Intent.java                  enum: MUHURTA, FESTIVAL_GUIDE, ASTROLOGY_EXPLAINER, UNKNOWN
│   ├── User.java                    JPA: externalId (from client), name, timestamps
│   └── ChatHistory.java             JPA: user, message, intent, response (jsonb), created_at
└── config/
    └── AiConfig.java                Wires AnthropicClient bean
```

Patterns in use:
- **Strategy pattern** — each intent → its own handler, selected at runtime via a `Map<Intent, IntentHandler>` injected by Spring.
- **Open/Closed** — add a new intent by: adding an enum value + a new `IntentHandler` bean. Zero changes to existing files.
- **DTOs as the API contract** — `ChatResponse.result` is polymorphic (`Object`), with concrete shape determined by `intent`. Client dispatches on intent.

---

## 5. Efficiency — where the money and latency go

### Cost model per request

| Step | Model | Tokens | Approx latency |
|---|---|---|---|
| Classification | Claude Haiku 4.5 | ~200 in, ~5 out | ~300-500ms |
| Generation (if not UNKNOWN) | Claude Sonnet 4.5 | ~500 in, ~300-600 out | ~2-4s |
| DB write (user upsert + chat_history insert) | Postgres | — | <20ms |
| **Total** | | | **~2.5-4.5s** |

**UNKNOWN messages cost only the Haiku call.** No Sonnet invocation, no parsing, no JSON. This is the whole point of routing before generation — you don't burn Sonnet tokens on "hi" or "what's the weather."

### Throughput

- **Stateless** at the service layer. Scales horizontally — add replicas behind a load balancer.
- **Single DB write per request.** One `User` upsert (indexed on `external_id`) + one `ChatHistory` insert. Jackson ObjectMapper serializes the response DTO to jsonb directly, no extra round trips.
- **No conversation memory yet.** Each message is independent. Good for cost. Bad if you later want multi-turn context — that's the first extension point.
- **Transactional boundary** on `ChatService.processMessage` ensures either both the classification result and the history row land, or neither does.

---

## 6. Why it's better to use (and where it isn't)

### Where it wins

1. **Typed responses, not free text.** Your frontend doesn't have to parse markdown or guess formatting. `MuhurtaResult.timeWindow` is always a string, `FestivalResult.steps` is always an ordered list. The client can render UI deterministically.
2. **Cheap routing.** Off-topic messages are rejected for ~$0.0001 instead of ~$0.02. At scale this is 100x cost reduction on the long tail.
3. **Grounded on deterministic context.** Two users asking "best time to buy a car today" at 10am IST get responses that agree on the day's quality. No "Monday is auspicious" one call, "Monday is inauspicious" the next.
4. **Easy to extend.** Want a `KUNDALI_MATCHING` intent? Add one enum value, one handler class, one DTO. Nothing else changes. The `ChatService` loop is already a strategy map.
5. **Auditable history.** Every (user, message, intent, response) is in Postgres as jsonb. Great for prompt tuning, cost analysis, abuse detection, and user-facing chat history features.
6. **Fast vs. thoughtful, by design.** Haiku for the boring task, Sonnet for the hard one. Most systems use one model for both and pay too much for classification.

### Where it's weak (honest list)

1. **No multi-turn memory.** Follow-ups like "what about tomorrow instead?" have no idea what "tomorrow" refers to. Each request is an island.
2. **Rule-based astrology is thin.** `AstrologyService` returns day-of-week quality. Real astrology cares about nakshatra, tithi, yoga, karana, planetary transits. Grounding could go much deeper — right now the LLM is doing most of the "astrological reasoning," which is not what you want for a production astrology product.
3. **No schema validation on LLM output.** If Claude returns malformed JSON, `ObjectMapper.readValue` throws. There's no retry, no fallback, no Pydantic-style coercion. One flaky response = one 500 to the user.
4. **No rate limiting, no auth.** `/api/chat` accepts any `userId` string. Anyone with the URL can burn your Anthropic credits.
5. **No caching.** Two users asking the exact same question at 10am on the same day hit Sonnet twice. A simple Redis cache keyed on `(intent, normalized_message, date)` would be a clear win.
6. **UNKNOWN intent is a dead end.** The fallback is a static "I can only help with muhurta, festivals, or explanations" message. Could be a routing hint ("did you mean to ask about an auspicious time?") but isn't.
7. **System prompts live in handler classes.** Tuning them means a code change + redeploy. Moving them to config (DB or YAML) would make prompt engineering much faster.

### When NOT to use this architecture

- If you need **streaming responses** to the client. Current flow is request/response. Streaming would need SSE or WebSocket at the controller layer.
- If you need **multi-turn conversations** with context. Add a conversation state store first.
- If **latency under 1 second** is a hard requirement. Sonnet alone takes 2-4s. You'd need a smaller/local model or heavy caching.

---

## 7. Extending it — the one-pager

To add a new intent (say, `KUNDALI_MATCHING`):

1. Add `KUNDALI_MATCHING` to `Intent.java`.
2. Update the system prompt in `AiIntentClassifier.java` to include the new label with 2-3 examples.
3. Create `KundaliMatchingResult.java` DTO.
4. Create `KundaliMatchingHandler implements IntentHandler`, annotate `@Service`, implement `supportedIntent()` to return `Intent.KUNDALI_MATCHING`, and `handle()` to prompt Sonnet with a structured JSON system prompt.
5. Done. Spring auto-registers it in the `Map<Intent, IntentHandler>` that `ChatService` uses.

No changes to `ChatService`, `ChatController`, or existing handlers. That's the payoff of the strategy pattern.

---

## 8. Quick reference

**Request:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","message":"Best time to buy a car this week?"}'
```

**Response:**
```json
{
  "intent": "MUHURTA",
  "result": {
    "status": "auspicious",
    "timeWindow": "Thursday 10:30 AM - 12:00 PM",
    "message": "Thursday afternoon is favorable for vehicle purchases.",
    "reason": "Jupiter rules Thursday; its influence supports long-term investments."
  }
}
```

**Database schema (simplified):**
```
users(id, external_id UNIQUE, name, created_at, updated_at)
chat_history(id, user_id FK, message TEXT, intent VARCHAR(50), response JSONB, created_at)
```

**Environment:**
- `ANTHROPIC_API_KEY` — required
- Postgres connection via `application.yml`
- Flyway manages migrations

---

## 9. TL;DR

Three intents, one endpoint, two models, typed JSON out, Postgres-backed history. Router in front of Sonnet saves money on every off-topic message. Day-of-week grounding keeps answers consistent. Adding intents is a 4-file change with zero edits to existing code. Biggest gaps: no multi-turn memory, thin grounding beyond weekday, no auth or rate limiting. Good bones, ship the auth layer and a caching layer before you put it in front of real users.
