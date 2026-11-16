# AI Knowledge Assistant

![CI](https://github.com/2307shukla/ai-knowledge-assistant/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F)
![License](https://img.shields.io/badge/license-MIT-blue)

A production-shaped AI chat application built with **Java 21**, **Spring Boot 3**, **Spring AI** and **Spring Security** — streamed responses, per-user history, persistent memory, document Q&A and admin analytics. Runs entirely on free-tier infrastructure.

**Live demo:** https://ai-knowledge-assistant-378a.onrender.com

| Account | Username | Password | Role |
|---|---|---|---|
| Demo user | `demo` | `demo123` | USER |
| Administrator | `admin` | `admin123` | ADMIN |

> Free tier sleeps after ~15 minutes. The first request can take up to a minute to wake.

---

## Why this project exists

Most "AI chatbot" portfolio projects are a text box wired to an API key. That demonstrates an API call, not engineering.

This one is built around the questions that actually matter when an LLM sits inside a real application:

- **How do you stream a response** without blocking a request thread or corrupting a JPA transaction?
- **How do you keep users apart** so one person's chats and memories never leak into another's prompt?
- **How do you stop a public endpoint** from being drained of paid quota by a script?
- **How do you know the model didn't hallucinate** when it was supposed to answer from your document?
- **What happens when the free tier goes down** or the disk resets?

Every answer is visible in the code, and the trade-offs are written down rather than hidden.

---

## Architecture

```mermaid
flowchart LR
    B[Browser] -->|HTTPS + session cookie| S[Spring Security]
    S --> C[REST Controllers]

    C --> CS[ChatService]
    CS --> MEM[(Memory)]
    CS --> HIST[(Conversation history)]
    CS --> AI[Spring AI<br/>ChatClient]
    AI -->|OpenAI-compatible| G[Google Gemini<br/>free tier]

    C --> EXP[PDF / CSV export]
    C --> ADM[Admin analytics]
    C --> RAG[RAG service]
    RAG --> VS[(Vector store<br/>JSON on disk)]
    RAG --> AI

    C --> DB[(H2 in-memory<br/>users · chats · memories)]
```

**Request path for a streamed message:**

1. `POST /api/conversations/{id}/messages/stream`
2. Rate limiter checks the caller's token bucket
3. User message persisted in its own transaction
4. Prompt assembled: system prompt + memories + last 12 messages
5. `ChatModel.stream()` piped through `SseEmitter`
6. Assistant reply persisted when the stream completes

---

## Features

| Feature | Implementation |
|---|---|
| **Streaming responses** | Server-Sent Events via `SseEmitter` + `ChatModel.stream()` |
| **Authentication** | Spring Security form login, BCrypt, session cookies |
| **Google sign-in** | OAuth2 (optional) — enabled by env vars, accounts keyed on verified email |
| **Role-based access** | `ROLE_ADMIN` enforced server-side for admin pages and APIs |
| **Conversation history** | JPA entities, per-user scoping on every query |
| **Memory** | User-authored facts injected into the system prompt, individually deletable |
| **Document Q&A (RAG)** | PDF → chunks → embeddings → similarity search |
| **Hallucination guard** | Token-overlap score between answer and retrieved context |
| **Vector persistence** | Store saved to disk, reloaded on boot without re-embedding |
| **Structured JSON output** | `ChatClient.entity()` with a typed record |
| **Function calling** | `@Tool` methods bound to the signed-in user per request |
| **Guest trial** | 5 messages without an account, then a sign-up prompt |
| **Rate limiting** | Bucket4j token bucket, 20 req/min per user or IP |
| **BYOK** | Users can bring their own API key — AES-GCM encrypted, write-only, masked |
| **Input sanitisation** | Control characters stripped, hard length caps |
| **Global error handling** | `@RestControllerAdvice` returning readable JSON |
| **Markdown + code highlighting** | `marked` + DOMPurify + highlight.js |
| **Voice** | Web Speech API for input and text-to-speech output |
| **Export** | Conversation to CSV or PDF (OpenPDF) |
| **Health check** | `/actuator/health` |
| **API documentation** | Swagger UI at `/swagger-ui.html` |
| **CI** | GitHub Actions: build, test, Docker build, container smoke test |

---

## Quick start

```bash
# 1. Get a free Gemini key - no credit card
#    https://aistudio.google.com/apikey

export AI_API_KEY=your-key          # Windows: set AI_API_KEY=your-key

# 2. Run
./mvnw spring-boot:run
```

Open http://localhost:8080 and sign in with `demo` / `demo123`.

### Or with Docker

```bash
docker build -t ai-knowledge-assistant .
docker run -p 8080:8080 -e AI_API_KEY=your-key ai-knowledge-assistant
```

---

## Configuration

Everything except the key is committed:

```properties
spring.ai.openai.base-url=https://generativelanguage.googleapis.com/v1beta/openai
spring.ai.openai.api-key=${AI_API_KEY:unset}
spring.ai.openai.chat.completions-path=/chat/completions
spring.ai.openai.embedding.embeddings-path=/embeddings
spring.ai.openai.chat.options.model=gemini-3.6-flash
spring.ai.openai.embedding.options.model=gemini-embedding-001
app.vector-store.path=${VECTOR_STORE_PATH:./data/vector-store.json}
```

> Gemini is reached through its **OpenAI-compatible** surface, so the standard Spring AI OpenAI client is used unchanged — note there is **no `/v1`** in its paths.

**Provider-agnostic by design.** Any OpenAI-compatible endpoint works, including a local Ollama:

```bash
java -jar app.jar \
  --spring.ai.openai.base-url=http://localhost:11434/v1 \
  --spring.ai.openai.api-key=ollama \
  --spring.ai.openai.chat.options.model=llama3.2 \
  --spring.ai.openai.embedding.options.model=nomic-embed-text
```

---

## Sign-in options

| Method | Notes |
|---|---|
| Username + password | Seeded `demo` / `admin`, plus self-registration |
| **Continue with Google** | Appears automatically when OAuth credentials are configured |

Enable Google sign-in with two environment variables:

```
GOOGLE_CLIENT_ID     = <client id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET = <client secret>
```

Authorised redirect URI in Google Cloud Console:

```
https://<your-host>/login/oauth2/code/google
```

Design notes worth knowing:

- The registration is built **in code**, not mapped from properties, so a missing or malformed configuration hides the button instead of breaking startup.
- The principal name is the **verified email**, which is also `User.username` — so signing in with Google on an existing password account keeps its conversations and memories.
- `server.forward-headers-strategy=framework` is required behind Render's TLS-terminating proxy, otherwise the generated `redirect_uri` is `http://` and Google returns `redirect_uri_mismatch`.

---

## API

Interactive docs: **https://ai-knowledge-assistant-378a.onrender.com/swagger-ui.html**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations` | Create a conversation |
| `DELETE` | `/api/conversations/{id}` | Delete a conversation |
| `GET` | `/api/conversations/{id}/messages` | Message history |
| `POST` | `/api/conversations/{id}/messages` | Send (non-streaming) |
| `POST` | `/api/conversations/{id}/messages/stream` | Send, streamed via SSE |
| `GET` | `/api/conversations/{id}/export.csv` | Export conversation as CSV |
| `GET` | `/api/conversations/{id}/export.pdf` | Export conversation as PDF |
| `GET` / `POST` / `DELETE` | `/api/memories` | Manage memory |
| `GET` / `POST` / `DELETE` | `/api/keys` | BYOK: manage your own provider key |
| `POST` | `/api/rag/upload` | Upload and index a PDF |
| `GET` | `/api/rag/ask` | Answer from documents, with grounding score |
| `POST` | `/api/insights` | Structured JSON answer |
| `POST` | `/api/tools/ask` | Function calling |
| `POST` | `/api/guest/chat` | Anonymous trial (5 messages) |
| `GET` | `/api/admin/stats` | Aggregate analytics (ADMIN only) |
| `GET` | `/actuator/health` | Health check |

Unauthenticated API calls return **401 JSON**; browser navigations redirect to the login page.

### Hallucination guard

```json
{ "answer": "...", "grounded": true, "overlap": 0.89 }
```

A token-overlap heuristic flags answers whose vocabulary does not appear in the retrieved context. It catches the common failure — the model answering from its weights instead of the document — and **does not** catch a fluent answer that reuses the document's words while distorting meaning. It is a cheap signal, not a proof.

---

## Admin analytics

Sign in as `admin` and open `/admin.html`.

Shows aggregate counts only — users, conversations, messages, memories, average messages per conversation, and a per-user table. **No message content is exposed**, and access is enforced by Spring Security, not by hiding a link.

---

## Bring your own key (BYOK)

Users can store their own provider key. Their requests are then made with that key, so usage is billed to them and never touches the shared quota.

| Endpoint | Purpose |
|---|---|
| `GET /api/keys` | `{configured, hint, encryptionPersistent}` — never the key |
| `POST /api/keys` | Store a key (write-only) |
| `DELETE /api/keys` | Remove it |

Security decisions, and why:

- **Encrypted at rest** with AES-GCM. GCM authenticates the ciphertext, so a tampered value fails to decrypt instead of producing garbage.
- **Write-only API.** There is no endpoint that returns the key — not even to its owner.
- **Masked hint only.** The UI shows `****1234`.
- **Never logged**, including in error paths.
- **Per-request model.** A user with a key gets their own `OpenAiChatModel`; everyone else transparently falls back to the shared key.

Set the encryption key in production:

```bash
openssl rand -base64 32        # copy into APP_ENCRYPTION_KEY on Render
```

If `APP_ENCRYPTION_KEY` is unset, a per-process key is generated and stored keys are lost on restart — the application logs a warning rather than failing silently.

**Scope limitation:** BYOK covers chat only. Document embeddings still use the server key, because the vector store is shared across users and mixing providers there would produce incomparable vectors.

---

## Performance

Load-tested with 300 requests at concurrency 20 and 500 at concurrency 50:

| Concurrency | Throughput | p50 | p95 | Errors |
|---|---|---|---|---|
| 20 | 144.7 req/s | 117.7 ms | 266.1 ms | **0** |
| 50 | 127.4 req/s | 306.4 ms | 1,115.8 ms | **0** |

Throughput plateaus around 130 req/s while p95 rises ~4× — the saturation point of a single instance. Full method and raw numbers: [`loadtest/RESULTS.md`](loadtest/RESULTS.md).

---

## Persistence

| Data | Default (H2) | With `DATABASE_URL` → Postgres |
|---|---|---|
| Users, conversations, memories | H2 in-memory — ❌ recreated on boot | ✅ persistent |
| Document embeddings | JSON file (`data/vector-store.json`) — ✅ reloaded, not re-embedded | ✅ `PgVectorStore` (`vector_store` table) |

Embeddings are the expensive part — each chunk costs an API call. In file mode the store is written after every index operation and reloaded on startup.

> **Caveat:** Render's free tier has an ephemeral filesystem. The JSON file survives a restart of a running instance but **not** a redeploy or sleep/wake.

### Switching to Postgres (free)

Any free-tier Postgres with the `pgvector` extension works, for example Neon, Supabase, or Render's free Postgres (which expires after 30 days). Set:

```
DATABASE_URL=jdbc:postgresql://<host>:5432/<db>?sslmode=require
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<password>
```

A `postgresql` URL is detected at startup and embeddings switch to `PgVectorStore` automatically. The app runs `CREATE EXTENSION IF NOT EXISTS vector` and creates the table on first boot; if your database user can't create extensions, enable `vector` once from the provider's dashboard.

---

## Project structure

```
src/main/java/com/abhijeet/ai_knowledge_assistant/
├── config/        ChatClient, OpenAPI, demo data
├── controller/    chat, auth, memory, RAG, admin, export, tools
├── domain/        User, Conversation, ChatMessage, Memory
├── repository/    Spring Data JPA interfaces
├── security/      SecurityConfig, UserDetailsService, rate limiter
├── service/       ChatService, ChatPersistence, RagService
├── tools/         @Tool methods for function calling
└── util/          InputSanitizer, GroundingCheck
```

---

## Screenshots & demo

Add your own captures — the README renders them once the files exist:

```markdown
![Landing page](docs/landing.png)
![Chat with streaming](docs/chat.png)
![Admin analytics](docs/admin.png)
![Demo](docs/demo.gif)
```

To capture: open the live URL, press **Win + G** (Windows Game Bar) to record a short clip, and save screenshots with **Win + Shift + S** into a `docs/` folder.

---

## Known limitations

- **H2 is in-memory by default.** Accounts and chats reset when the container restarts unless `DATABASE_URL` points at Postgres (see [Persistence](#persistence)).
- **CSRF tokens are disabled** for the JSON single-page UI. The session cookie is `SameSite=Lax`, so browsers don't send it on cross-site POST/PUT/DELETE requests, which blocks classic CSRF. Production would add `CookieCsrfTokenRepository` with an `X-XSRF-TOKEN` header as well.
- **Rate limiting is per-instance.** Buckets live in memory; multiple instances would need Redis.
- **Prompt injection is not solved.** The system prompt is server-controlled and the model has no tools that act on untrusted input, but no filter reliably prevents injection.
- **Structured output is model-dependent.** Small models fail the schema; the endpoint returns 502 rather than nulls when that happens.
- **Demo accounts are public** by design, so do not store anything private in them.

---

## Licence

MIT
