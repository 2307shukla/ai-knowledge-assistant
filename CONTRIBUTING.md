# Contributing

1. Branch from `main`; one topic per branch and pull request.
2. Write small commits in the form `type: short summary` (`feat`, `fix`, `test`, `docs`, `ci`,
   `build`, `security`).
3. Add tests for new behaviour. Never skip, disable or weaken an existing test.
4. Run `./mvnw verify` (tests and PMD lint) before pushing. CI also checks licences, secrets and
   known vulnerabilities.
5. Use only MIT, Apache-2.0 or BSD dependencies, and nothing that needs a paid service or a new key.
6. Fill in the pull request template, including the rollback note. Merge only when CI is green.

Security issues: see [SECURITY.md](SECURITY.md).
