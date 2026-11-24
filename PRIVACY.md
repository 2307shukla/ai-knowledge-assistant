# Privacy

This is what the AI Knowledge Assistant stores, where, and for how long.

| What | Where | Who can see it | Kept until |
|---|---|---|---|
| Account (username, hashed password or sign-in id, display name) | Database | You; the admin analytics page shows counts only | You delete the account data, or the in-memory database restarts |
| Chats and saved memories | Database | You only | You delete them |
| Your PDFs | **Not stored.** Text is extracted, then the file is discarded | n/a | n/a |
| Text passages and their embeddings | Database or vector store, tagged with your account | You only (the public sample is visible to everyone) | You delete the document; rows and vectors go together |
| Your own AI key (optional) | Database, encrypted | Used only to answer your requests; never shown again | You remove it |
| Logs | Hosting provider's log stream | Operator | Provider's retention; ids and counts only, no document text, prompts, keys, e-mail addresses or usernames |

## What leaves the service

Your questions and the document passages needed to answer them are sent to the AI provider (Google
Gemini free tier by default, or your own key). Do not upload anything you may not share with that provider.

## Your controls

- Delete any document in the app: its text and vectors are removed (a 6-second undo window applies first).
- Document answers cite the file and page, and you can open the cited page next to the chat.
- Another user's document id always answers "not found", so existence is never revealed.

Questions or a security report: see [SECURITY.md](SECURITY.md).
