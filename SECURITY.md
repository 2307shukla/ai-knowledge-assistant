# Security policy

## Reporting a vulnerability

Please report security problems **privately**, not in a public issue:

- Use GitHub's "Report a vulnerability" button on the repository's **Security** tab, or
- open a private security advisory for this repository.

Include what you found, the steps to reproduce it and, if you can, the impact. Do not include other
people's data. You will get an answer within a few days.

## What is covered

The application in this repository and its container image. Out of scope: the free hosting
provider, the AI provider and denial-of-service by heavy traffic against the free demo.

## How the project protects users

- Documents are private to their owner; another user's document id answers 404.
- Uploads are limited and checked by content; only PDFs are accepted.
- Errors show a short message and a request id; details stay in the server log.
- Logs contain ids and counts only: no document text, prompts, keys, e-mail addresses or usernames.
- Dependencies are scanned in CI (Trivy), licences are checked, and secrets are scanned (gitleaks).
- Cross-site state-changing requests are refused; the session cookie is `HttpOnly` and `SameSite=Lax`.

See the README, section "Security notes", for details.
