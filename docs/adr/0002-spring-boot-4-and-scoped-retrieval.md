# ADR 0002: Spring Boot 4, scoped retrieval and a relevance floor

Status: accepted

## Context

Three Spring Framework 6.2 vulnerabilities could only be fixed by moving to Framework 7, which needs
Spring Boot 4 and Spring AI 2. At the same time users asked to chat with several documents at once and
to stop the assistant from guessing when the documents do not contain the answer.

## Decisions

1. **Spring Boot 4.0.x, Spring AI 2.0.x, Jackson 3.** The Trivy ignore list is now empty. Spring AI 2's
   OpenAI client uses the official OpenAI Java SDK, so per-user keys (BYOK) build the model from
   `OpenAiChatOptions` instead of the removed `OpenAiApi`.
2. **Scope is a filter, not a trust boundary.** A question may be limited to a set of document ids. The
   owner filter (`owner IN (me, public)`) is always applied as well, each id is checked by
   `DocumentService.requireReadable`, and a foreign id fails the whole request with 404.
3. **Relevance floor with no model call.** Hits scoring below `app.rag.min-score` are dropped; with none
   left the answer is a fixed "not found" message. This costs no quota and removes the main source of
   made-up answers. Trade-off: the right value depends on the embedding model, so it is configurable.
4. **No new storage.** Summaries are cached in memory instead of a new column, so existing databases need
   no migration. Cross-site protection uses `Origin` and `Sec-Fetch-Site` checks rather than CSRF tokens,
   so the front end needed no change; the session cookie is `SameSite=Lax` as before.

## Consequences

- Real Gemini calls through the new client were not exercised in CI (tests use offline fakes): smoke-test
  chat, streaming and embeddings after the first deploy.
- The in-memory summary cache is lost on restart and costs one model call per document afterwards.
