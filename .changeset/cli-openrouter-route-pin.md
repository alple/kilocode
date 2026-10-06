---
"kilo-code": minor
---

Support listing OpenRouter routing endpoints per model (`GET /kilo/routes/{author}/{slug}`) and pinning a route for a single turn via the prompt `route` option: the pin resolves to an exact OpenRouter endpoint (`provider.order` with fallbacks disabled), applies to both OpenRouter transports, and leaving it out keeps provider-default routing.
