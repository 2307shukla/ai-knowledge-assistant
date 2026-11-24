# ADR 0001: Private documents and a licence-clean stack

- Status: accepted
- Date: 2026-11-25

## Context

Uploaded documents went into one shared vector store with no owner, so any signed-in user's
question could retrieve passages from anyone's upload. The project also has to stay free (Render
free tier, 512 MB, no paid services) and use only MIT, Apache-2.0 or BSD dependencies, with the
Jakarta APIs (EPL-2.0) as the one approved exception. Hibernate (LGPL), H2 (MPL/EPL),
OpenPDF (LGPL/MPL) and Logback (EPL/LGPL) did not meet that rule.

## Decision

1. **Owner on every vector, filter on every search.** Each chunk's metadata carries `owner` and
   `documentId`. `RagService.search` requires an owner and applies `owner IN (caller, public)`;
   results are checked again in code. Legacy vectors have no owner, so the filter quarantines
   them without a migration.
2. **A document registry in the database** (`knowledge_document`, `document_chunk`) so a user can
   list, delete and re-index, and so re-indexing does not need the original file. Every lookup is
   by id *and* owner; a foreign id returns 404, the same as a missing one.
3. **Spring Data JDBC instead of JPA.** Same table and column names as Hibernate created, so live
   data is kept; `schema.sql` creates only what is missing.
4. **HSQLDB, Apache PDFBox and Log4j2** replace H2, OpenPDF and Logback.
5. **PDF only, checked by content**, with size, page and per-user limits before extraction, and
   page-by-page extraction to bound memory.
6. **Zero-configuration start**: built-in database, optional AI key (users can bring their own),
   optional Google login, `docker compose up`.

## Consequences

- Existing uploads stop appearing in answers until re-uploaded; they can be deleted with the SQL
  in the README.
- Without PostgreSQL the database is in memory; the document list is rebuilt from the vector file
  at start-up.
- Re-index re-embeds stored chunk text (no original file is kept), which keeps storage small.
- CI enforces the licence rule (banned-dependency check and SBOM licence gate).

## Rollback

Revert the merge commit and redeploy. The new tables are unused by the old version, and the old
version can read the same users, conversations and memories tables. Vectors written by the new
version carry extra metadata that the old version ignores.
