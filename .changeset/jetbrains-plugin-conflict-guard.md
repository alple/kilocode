---
"kilo-code": minor
---

Keep exactly one Kilo plugin identity enabled in JetBrains IDEs. When the stable and lux builds are installed together, the one that is not running is auto-disabled and a warning notification explains the conflict with a Restart IDE action; dynamic loads of the other identity are vetoed before they can collide. Previously the co-installation broke every Kilo action with "ID already taken" errors and ClassCastExceptions across RPC, with no visible cause.
