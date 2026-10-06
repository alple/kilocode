// kilocode_change - new file

// Transports that forward providerOptions to OpenRouter verbatim (the BYOK
// `@openrouter/ai-sdk-provider` spread and the kilo-gateway openrouter namespace).
const TRANSPORTS = new Set(["@openrouter/ai-sdk-provider", "@kilocode/kilo-gateway"])

/**
 * Per-turn OpenRouter route pin (ADR-0001). Emits the OpenRouter `provider`
 * routing preference that pins exactly one endpoint: a full tag slug matches a
 * single server, and `allow_fallbacks: false` fails the request instead of
 * silently routing elsewhere. Absent route or a non-OpenRouter transport is a
 * no-op (provider-default routing).
 */
export function resolve(input: { route?: string; model: { api: { npm: string } } }) {
  const tag = input.route?.trim()
  if (!tag || !TRANSPORTS.has(input.model.api.npm)) return undefined
  return { provider: { order: [tag], allow_fallbacks: false } }
}
