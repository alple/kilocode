---
"kilo-code": minor
---

Remember model, reasoning effort, and routing picks per IntelliJ project. Session picks now persist in the project's workspace.xml instead of user-global state, so side-by-side projects keep separate defaults and never swap a running session's selection underneath it; new sessions inherit the project's picks, never-picked effort starts at Auto, and the first turn of every prompt — including retries — carries the pinned OpenRouter route.
