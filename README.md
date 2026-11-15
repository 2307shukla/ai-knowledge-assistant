# AI Knowledge Assistant

A retrieval-augmented Q&A service built with **Spring Boot 3** and **Spring AI**, running entirely on free-tier infrastructure with **Google Gemini**.

Ask questions directly, or upload a PDF and ask questions grounded in its contents.

**Live demo:** https://ai-knowledge-assistant-378a.onrender.com

---

## Features

- **Direct chat** — questions answered by `gemini-3.6-flash`
- **Document Q&A (RAG)** — PDFs are chunked, embedded, and searched by similarity before the model answers
- **Zero-setup demo** — a sample document is indexed automatically at startup, so `/api/rag/ask` works the moment the app boots
- **Readable errors** — every failure returns structured JSON with the real upstream cause, never a blank 500 page
- **Provider-agnostic** — any OpenAI-compatible endpoint works; switching providers is a config change, not a code change

---

## Architecture

```
                    ┌──────────────────────────────────────────┐
  Browser ─────────▶│  Spring Boot 3 (REST)                    │
                    │                                          │
                    │  /api/chat/ask ──────────────┐           │
                    │                              ▼           │
                    │                     Gemini chat model    │
                    │                              ▲           │
                    │  /api/rag/upload ──▶ SimpleVectorStore   │
                    │                          │               │
                    │  /api/rag/ask ───────────┘               │
                    │        (embed → similarity search)       │
                    └──────────────────────────────────────────┘
                                     │
                                     ▼
                    Google Gemini API (free tier)
                    chat: gemini-3.6-flash
                    embeddings: gemini-embedding-001
```

Spring AI talks to Gemini through its **OpenAI-compatible surface**, so the standard OpenAI client is used with a different base URL and paths.

---

## API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/chat/ask?question=...` | Answer a question directly |
| `POST` | `/api/rag/upload` | Upload and index a PDF (multipart form field `file`) |
| `GET` | `/api/rag/ask?question=...` | Answer using the indexed documents |

### Examples

```bash
# Direct chat
curl "https://ai-knowledge-assistant-378a.onrender.com/api/chat/ask?question=What+is+Pega+Platform"

# Upload a document
curl -X POST "https://ai-knowledge-assistant-378a.onrender.com/api/rag/upload" \
     -F "file=@/path/to/document.pdf"

# Ask about the indexed document
curl "https://ai-knowledge-assistant-378a.onrender.com/api/rag/ask?question=Summarise+the+key+points"
```

Errors come back as JSON rather than an HTML error page:

```json
{ "error": "NonTransientAiException", "detail": "HTTP 404 - model not found ..." }
```

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| AI | Spring AI 1.0.0 |
| Model | Google Gemini (chat + embeddings) |
| Vector store | `SimpleVectorStore` (in-memory) |
| Build | Maven (multi-stage Docker build with cached dependencies) |
| Hosting | Render (Docker) |

---

## Running locally

```bash
# 1. Get a free Gemini API key: https://aistudio.google.com/apikey
export AI_API_KEY=your-key-here        # Windows: set AI_API_KEY=your-key-here

# 2. Run
./mvnw spring-boot:run
```

Open http://localhost:8080

### Configuration

Everything except the key is committed in `src/main/resources/application.properties`:

```properties
spring.ai.openai.base-url=https://generativelanguage.googleapis.com/v1beta/openai
spring.ai.openai.api-key=${AI_API_KEY:unset}
spring.ai.openai.chat.completions-path=/chat/completions
spring.ai.openai.embedding.embeddings-path=/embeddings
spring.ai.openai.chat.options.model=gemini-3.6-flash
spring.ai.openai.embedding.options.model=gemini-embedding-001
```

> Model names are hardcoded deliberately. A **blank** environment variable overrides a default in Spring and silently falls back to the OpenAI default model, which produces a confusing `model not found` error.

---

## Deployment

The `Dockerfile` builds in two stages:

1. `maven:3.9-eclipse-temurin-21` — resolves dependencies in a cached layer (`pom.xml` only), then compiles source
2. `eclipse-temurin:21-jre-alpine` — ships just the fat jar

Changing source code only invalidates the final layer, so rebuilds stay fast.

On Render: connect the repo, set `AI_API_KEY`, and deploys run automatically on push.

---

## Known limitations

- **The vector store is in-memory.** Uploaded documents are lost when the container restarts or sleeps. The bundled sample document is re-indexed on every boot, so the demo always works. Persistent storage would need a database with `pgvector`.
- **Free-tier cold starts.** The service sleeps after ~15 minutes of inactivity; the first request afterwards can take up to a minute.
- **No authentication.** The API is public, so it is not suitable for private or sensitive documents, and usage counts against the free quota.

---

## Replacing the sample document

Drop any **text-based** PDF (not a scanned image) at `src/main/resources/sample.pdf` and redeploy. It is indexed at startup automatically.

---

## License

MIT
