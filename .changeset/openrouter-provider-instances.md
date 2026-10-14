---
"@kilocode/cli": minor
"@kilocode/kilo-jetbrains": minor
---

Support multiple OpenRouter instances (e.g. work and personal). In JetBrains Settings → Providers, "Add OpenRouter Instance" creates a second OpenRouter provider with its own name and API key that shares the full OpenRouter model catalog. On the CLI side this is config-driven: a provider entry with `cloneOf: "openrouter"` expands into a full catalog copy that authenticates with its own key from the auth store and never reads `OPENROUTER_API_KEY`.
