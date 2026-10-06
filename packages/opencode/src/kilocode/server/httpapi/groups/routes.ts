// kilocode_change - new file
import { Schema } from "effect"
import { HttpApi, HttpApiEndpoint, HttpApiError, HttpApiGroup, OpenApi } from "effect/unstable/httpapi"
import { Authorization } from "@/server/routes/instance/httpapi/middleware/authorization"
import { InstanceContextMiddleware } from "@/server/routes/instance/httpapi/middleware/instance-context"
import {
  WorkspaceRoutingMiddleware,
  WorkspaceRoutingQuery,
} from "@/server/routes/instance/httpapi/middleware/workspace-routing"
import { described } from "@/server/routes/instance/httpapi/groups/metadata"

const root = "/kilo/routes"

export const RouteEndpoint = Schema.Struct({
  /** OpenRouter routing slug including variant suffix (e.g. "parasail/fp4"); the pin value for provider.order. */
  tag: Schema.String,
  providerName: Schema.optional(Schema.String),
  name: Schema.optional(Schema.String),
  quantization: Schema.optional(Schema.String),
  /** Upstream endpoint status; 0 = healthy, negative values correlate with degraded availability. */
  status: Schema.optional(Schema.Number),
  uptime: Schema.Struct({
    last5m: Schema.optional(Schema.Number),
    last30m: Schema.optional(Schema.Number),
    last1d: Schema.optional(Schema.Number),
  }),
  pricing: Schema.Struct({
    prompt: Schema.optional(Schema.Number),
    completion: Schema.optional(Schema.Number),
    inputCacheRead: Schema.optional(Schema.Number),
    discount: Schema.optional(Schema.Number),
  }),
  contextLength: Schema.optional(Schema.Number),
  maxCompletionTokens: Schema.optional(Schema.Number),
  supportedParameters: Schema.optional(Schema.Array(Schema.String)),
})

/** Upstream OpenRouter request failed for a reason other than an unknown model. */
export class RouteListError extends Schema.TaggedErrorClass<RouteListError>()(
  "RouteListError",
  {
    message: Schema.String,
    status: Schema.optional(Schema.Number),
  },
  { httpApiStatus: 502 },
) {}

export const RoutesPaths = {
  list: `${root}/:author/:slug`,
} as const

export const RoutesApi = HttpApi.make("routes")
  .add(
    HttpApiGroup.make("routes")
      .add(
        HttpApiEndpoint.get("list", RoutesPaths.list, {
          params: { author: Schema.String, slug: Schema.String },
          query: WorkspaceRoutingQuery,
          success: described(Schema.Array(RouteEndpoint), "OpenRouter routing endpoints for one model"),
          error: [HttpApiError.BadRequest, HttpApiError.NotFound, RouteListError],
        }).annotateMerge(
          OpenApi.annotations({
            identifier: "routes.list",
            summary: "List OpenRouter routing endpoints",
            description: "Proxy the OpenRouter per-model endpoints API, normalized and deduplicated by routing tag.",
          }),
        ),
      )
      .annotateMerge(
        OpenApi.annotations({
          title: "routes",
          description: "Kilo route-list routes.",
        }),
      )
      .middleware(InstanceContextMiddleware)
      .middleware(WorkspaceRoutingMiddleware)
      .middleware(Authorization),
  )
  .annotateMerge(
    OpenApi.annotations({
      title: "kilo HttpApi",
      version: "0.0.1",
      description: "Kilo HttpApi surface.",
    }),
  )
