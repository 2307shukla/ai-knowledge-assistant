# AI Knowledge Assistant

A Claude-style AI chat application built with **Spring Boot 3**, **Spring AI** and **Spring Security**, running on **Google Gemini's free tier**.

Sign in, hold multiple conversations, teach it facts about yourself that persist across chats, and ask questions about uploaded documents.

**Live demo:** https://ai-knowledge-assistant-378a.onrender.com

> **Demo account:** `demo` / `demo123`

---

## Features

| Feature | Detail |
|---|---|
| **Authentication** | Spring Security form login, BCrypt password hashing, self-registration |
| **Conversations** | Multiple chat threads per user, auto-titled from the first message |
| **History** | Full message history per conversation, restored on reload |
| **Memory** | User-authored facts injected into every conversation's system prompt |
| **Document Q&A** | Upload a PDF; questions are answered from its contents (RAG) |
| **Per-user isolation** | Every query is scoped to the signed-in user — no cross-account access |
| **Rate limiting** | 20 requests/minute per user (Bucket4j token bucket), protecting the free AI quota |
| **API documentation** | Interactive Swagger UI at `/swagger-ui.html` (springdoc-openapi) |
| **Input sanitisation** | Control characters stripped, hard length caps, all input normalised before the model |

---

## How memory works

Claude-style memory, but **explicit rather than inferred**.

- The user adds facts in the sidebar: *"Prefers short bullet answers"*, *"Works with Pega and Java"*.
- Those facts are stored per user and prepended to the system prompt on every request.
- The user can delete any memory, and it stops being used immediately.

Why not have the model summarise history automatically? Because that costs an extra API call per exchange, can drift over time, and silently captures things the user never agreed to. Explicit memory is cheaper, predictable and honest.

Within a conversation, the last 12 messages are also sent so the thread has continuity.

---

## Architecture

```
Browser ──▶ Spring Security (session cookie)
              │
              ├─ /api/auth/**        register, current user
              ├─ /api/conversations  list, create, delete
              ├─ /api/conversations/{id}/messages  history, send
              ├─ /api/memories       list, add, delete
              └─ /api/rag/**         PDF upload + document Q&A
                        │
                        ▼
                 ChatService
                   ├─ system prompt + memories
                   ├─ last 12 messages
                   └─ new message
                        │
                        ▼
              Gemini (OpenAI-compatible surface)
```

---

## API

**Interactive docs:** https://ai-knowledge-assistant-378a.onrender.com/swagger-ui.html

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations` | Create a conversation |
| `DELETE` | `/api/conversations/{id}` | Delete a conversation |
| `GET` | `/api/conversations/{id}/messages` | Message history |
| `POST` | `/api/conversations/{id}/messages` | Send a message, get a reply |
| `GET` | `/api/memories` | List saved memories |
| `POST` | `/api/memories` | Add a memory |
| `DELETE` | `/api/memories/{id}` | Forget a memory |
| `POST` | `/api/rag/upload` | Upload and index a PDF |
| `GET` | `/api/rag/ask?question=...` | Answer from indexed documents |

Unauthenticated API calls return **401 JSON**; browser navigations redirect to the login page.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security 6 (form login, BCrypt) |
| AI | Spring AI 1.0.0 |
| Model | Google Gemini `gemini-3.6-flash` / `gemini-embedding-001` |
| Storage | H2 in-memory + JPA |
| Build | Maven, multi-stage Docker |
| Hosting | Render (free tier) |

No paid dependency. No credit card anywhere.

---

## Running locally

```bash
export AI_API_KEY=your-gemini-key     # Windows: set AI_API_KEY=your-key
./mvnw spring-boot:run
```

Open http://localhost:8080 and sign in with `demo` / `demo123`.

### Configuration

```properties
spring.ai.openai.base-url=https://generativelanguage.googleapis.com/v1beta/openai
spring.ai.openai.api-key=${AI_API_KEY:unset}
spring.ai.openai.chat.completions-path=/chat/completions
spring.ai.openai.embedding.embeddings-path=/embeddings
spring.ai.openai.chat.options.model=gemini-3.6-flash
spring.ai.openai.embedding.options.model=gemini-embedding-001
```

> Gemini is reached through its OpenAI-compatible surface, so the standard Spring AI OpenAI client is used unchanged — note there is no `/v1` in its paths.

---

## Persistence

| Data | Storage | Survives restart? |
|---|---|---|
| Users, conversations, memories | H2 in-memory | ❌ recreated on boot |
| Document embeddings | JSON file (`data/vector-store.json`) | ✅ reloaded from disk, **not** re-embedded |

Embeddings are the expensive part — each chunk costs an API call. The vector store is written after every index operation and loaded on startup, so a restart reuses existing vectors instead of paying to recompute them.

Verified: on a cold restart the log shows `Loaded vector store from …` followed by `Vector store already populated - skipping sample indexing`, and document questions still answer correctly.

> **Caveat, stated honestly:** Render's free tier has an ephemeral filesystem. This survives a restart of a running instance but **not** a redeploy or a sleep/wake cycle. For true durability, set `VECTOR_STORE_PATH` to a mounted volume, or switch to `PgVectorStore` backed by a free Postgres instance.

---

## Known limitations

- **In-memory database.** Accounts, chats and memories reset when the container restarts. The demo account and sample document are recreated on boot. Persistent storage would need PostgreSQL.
- **CSRF disabled.** The UI is a single page using JSON fetch calls. For production, use `CookieCsrfTokenRepository` and send `X-XSRF-TOKEN`.
- **Free-tier cold start.** Render sleeps after ~15 minutes idle; the first request afterwards can take up to a minute.
- **Public demo account.** `demo` is shared, so do not store anything private in it.

---

## Licence

MIT
