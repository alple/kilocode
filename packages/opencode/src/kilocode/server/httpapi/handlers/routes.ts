// kilocode_change - new file
import { Effect } from "effect"
import { HttpServerRequest } from "effect/unstable/http"
import { HttpApiBuilder, HttpApiError } from "effect/unstable/httpapi"
import { Auth } from "@/auth"
import { InstanceHttpApi } from "@/server/routes/instance/httpapi/api"
import { RouteListError } from "../groups/routes"

const ENDPOINTS_BASE = "https://openrouter.ai/api/v1/models"

// OpenRouter path segments are lowercase ids with dated/tag suffixes; reject anything
// else so this proxy can only reach the OpenRouter endpoints path shape.
const SEGMENT = /^[A-Za-z0-9._-]+$/

type Row = Record<string, unknown>

const str = (value: unknown) => (typeof value === "string" ? value : undefined)

const num = (value: unknown) => (typeof value === "number" && Number.isFinite(value) ? value : undefined)

// Upstream denominated prices are strings ("0.00000015"); discount arrives numeric.
const price = (value: unknown) => {
  if (typeof value === "number") return Number.isFinite(value) ? value : undefined
  if (typeof value !== "string") return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

const parameters = (value: unknown) => {
  if (!Array.isArray(value)) return undefined
  return value.filter((item): item is string => typeof item === "string")
}

function normalize(row: unknown) {
  if (!row || typeof row !== "object" || Array.isArray(row)) return undefined
  const item = row as Row
  const tag = str(item.tag)
  if (!tag) return undefined
  const pricing = item.pricing
  const p = pricing && typeof pricing === "object" && !Array.isArray(pricing) ? (pricing as Row) : {}
  return {
    tag,
    providerName: str(item.provider_name),
    name: str(item.name),
    quantization: str(item.quantization),
    status: num(item.status),
    uptime: {
      last5m: num(item.uptime_last_5m),
      last30m: num(item.uptime_last_30m),
      last1d: num(item.uptime_last_1d),
    },
    pricing: {
      prompt: price(p.prompt),
      completion: price(p.completion),
      inputCacheRead: price(p.input_cache_read),
      discount: price(p.discount),
    },
    contextLength: num(item.context_length),
    maxCompletionTokens: num(item.max_completion_tokens),
    supportedParameters: parameters(item.supported_parameters),
  }
}

// One endpoint row per tag, first wins: the same tag can repeat upstream
// (e.g. two baseten/fp8 rows) and OpenRouter routes by tag, not row.
function dedupe(rows: unknown) {
  if (!Array.isArray(rows)) return undefined
  const seen = new Set<string>()
  const list = []
  for (const row of rows) {
    const item = normalize(row)
    if (!item || seen.has(item.tag)) continue
    seen.add(item.tag)
    list.push(item)
  }
  return list
}

const reason = (err: unknown) => (err instanceof Error ? err.message : String(err))

export const routesHandlers = HttpApiBuilder.group(InstanceHttpApi, "routes", (handlers) =>
  Effect.gen(function* () {
    const auth = yield* Auth.Service

    const list = Effect.fn("RoutesHttpApi.list")(function* (ctx: { params: { author: string; slug: string } }) {
      const author = ctx.params.author
      const slug = ctx.params.slug
      if (!SEGMENT.test(author) || !SEGMENT.test(slug)) return yield* Effect.fail(new HttpApiError.BadRequest({}))

      // BYOK posture: attach the stored OpenRouter key when one exists, else call the
      // public endpoints API. Auth gates only the latency/throughput percentiles upstream.
      const info = yield* auth.get("openrouter").pipe(Effect.catch(() => Effect.succeed(undefined)))
      const headers: Record<string, string> = { Accept: "application/json" }
      if (info?.type === "api") headers.Authorization = `Bearer ${info.key}`

      const request = yield* HttpServerRequest.HttpServerRequest
      const signal = request.source instanceof Request ? request.source.signal : undefined

      const response = yield* Effect.tryPromise({
        try: () =>
          fetch(`${ENDPOINTS_BASE}/${encodeURIComponent(author)}/${encodeURIComponent(slug)}/endpoints`, {
            headers,
            signal,
          }),
        catch: (err) => new RouteListError({ message: `OpenRouter endpoints request failed: ${reason(err)}` }),
      })

      if (!response.ok) {
        // Unknown models are a plain passthrough; every other upstream status is a proxy failure.
        if (response.status === 404) return yield* Effect.fail(new HttpApiError.NotFound({}))
        const text = yield* Effect.promise(() => response.text().catch(() => "<unreadable>"))
        return yield* Effect.fail(
          new RouteListError({
            message: `OpenRouter endpoints request failed: ${response.status} ${text}`,
            status: response.status,
          }),
        )
      }

      const body = yield* Effect.promise(() => response.json().catch(() => undefined))
      const rows = dedupe((body as { data?: { endpoints?: unknown } } | undefined)?.data?.endpoints)
      if (!rows) return yield* Effect.fail(new RouteListError({ message: "OpenRouter endpoints response had no list" }))
      return rows
    })

    return handlers.handle("list", list)
  }),
)
