---
name: Wait for go-ahead before starting implementation
description: Don't jump into large implementation tasks without the user explicitly asking to start
type: feedback
---

Don't start implementing code as soon as context is loaded. When a session summary or memory indicates "next step is X", that is context for what comes next — not a directive to begin immediately.

**Why:** User was not ready to start the UI implementation session and was interrupted mid-session when I began writing files unilaterally.

**How to apply:** After loading context / reading memory / reviewing files, present a brief plan or summary of what you understand to be next, and wait for the user to say "go ahead" or similar before writing any files. This applies especially to large multi-file implementation tasks.
