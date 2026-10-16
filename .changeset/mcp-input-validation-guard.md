---
"kilo-code": patch
---

Validate MCP tool inputs against each tool's declared schema before execution. Incomplete or malformed tool-call arguments now fail with a precise error naming the violated properties, so the model can rewrite the input and retry instead of silently executing with missing keys. Disable with `KILO_DISABLE_MCP_INPUT_VALIDATION=true`.
