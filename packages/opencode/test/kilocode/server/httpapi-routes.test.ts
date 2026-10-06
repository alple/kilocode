import { afterEach, describe, expect, spyOn, test } from "bun:test"
import { ConfigProvider, Layer } from "effect"
import { HttpRouter } from "effect/unstable/http"
import * as Log from "@opencode-ai/core/util/log"
import { RoutesPaths } from "../../../src/kilocode/server/httpapi/groups/routes"
import * as HttpApiServer from "../../../src/server/routes/instance/httpapi/server"
import { resetDatabase } from "../../fixture/db"
import { disposeAllInstances, tmpdir } from "../../fixture/fixture"

void Log.init({ print: false })

const env = { KILO_AUTH_CONTENT: process.env.KILO_AUTH_CONTENT }

const upstreamURL = "https://openrouter.ai/api/v1/models/z-ai/glm-5.3-flash/endpoints"

function app() {
  const handler = HttpRouter.toWebHandler(
    HttpApiServer.routes.pipe(Layer.provide(ConfigProvider.layer(ConfigProvider.fromUnknown({})))),
    { disableLogger: true },
  ).handler

  return {
    request(path: string, init?: RequestInit) {
      return handler(new Request(new URL(path, "http://localhost"), init), HttpApiServer.context)
    },
  }
}

async function send(path = RoutesPaths.list.replace(":author", "z-ai").replace(":slug", "glm-5.3-flash")) {
  await using tmp = await tmpdir({ config: { formatter: false, lsp: false } })
  return app().request(path, {
    headers: { "content-type": "application/json", "x-kilo-directory": tmp.path },
  })
}

function stub(run: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) {
  const fetch: typeof globalThis.fetch = Object.assign(run, { preconnect: globalThis.fetch.preconnect })
  return spyOn(globalThis, "fetch").mockImplementation(fetch)
}

function url(input: RequestInfo | URL) {
  if (typeof input === "string") return input
  if (input instanceof URL) return input.href
  return input.url
}

function endpoints(payload: unknown, status = 200) {
  return Response.json(payload, { status })
}

function restore() {
  for (const [key, value] of Object.entries(env)) {
    if (value === undefined) delete process.env[key]
    else process.env[key] = value
  }
}

afterEach(async () => {
  restore()
  await disposeAllInstances()
  await resetDatabase()
})

describe("HttpApi OpenRouter routes proxy", () => {
  test("normalizes rows, dedupes by tag (first wins), and drops unusable rows", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    const calls: Array<{ input: RequestInfo | URL; init?: RequestInit }> = []
    const mock = stub(async (input, init) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      calls.push({ input, init })
      return endpoints({
        data: {
          id: "z-ai/glm-5.3-flash",
          name: "Z.ai: GLM 5.3 Flash",
          endpoints: [
            {
              tag: "baseten/fp8",
              provider_name: "BaseTen",
              name: "BaseTen | z-ai/glm-5.3-flash-20260826",
              quantization: "fp8",
              status: 0,
              uptime_last_5m: null,
              uptime_last_30m: 99.7,
              uptime_last_1d: null,
              pricing: { prompt: "0.00000015", completion: "0.0000005", input_cache_read: "0.00000003", discount: 0 },
              context_length: 1048576,
              max_completion_tokens: 943718,
              supported_parameters: ["reasoning", "tools"],
            },
            {
              tag: "baseten/fp8",
              provider_name: "BaseTen",
              quantization: "fp8",
              status: -2,
              uptime_last_30m: 90.4,
              pricing: { prompt: "0.00000099", completion: "0.000001" },
              context_length: 2048,
            },
            {
              tag: "parasail/fp4",
              provider_name: "Parasail",
              name: "Parasail | z-ai/glm-5.3-flash-20260826",
              quantization: "fp4",
              status: 0,
              uptime_last_30m: 99.9,
              pricing: { prompt: "0.00000015", completion: "0.0000005" },
              context_length: 1048576,
              max_completion_tokens: 943718,
            },
            { provider_name: "NoTag" },
            "not-an-object",
          ],
        },
      })
    })

    try {
      const response = await send()
      expect(response.status).toBe(200)
      expect(await response.json()).toEqual([
        {
          tag: "baseten/fp8",
          providerName: "BaseTen",
          name: "BaseTen | z-ai/glm-5.3-flash-20260826",
          quantization: "fp8",
          status: 0,
          uptime: { last5m: null, last30m: 99.7, last1d: null },
          pricing: { prompt: 0.00000015, completion: 0.0000005, inputCacheRead: 0.00000003, discount: 0 },
          contextLength: 1048576,
          maxCompletionTokens: 943718,
          supportedParameters: ["reasoning", "tools"],
        },
        {
          tag: "parasail/fp4",
          providerName: "Parasail",
          name: "Parasail | z-ai/glm-5.3-flash-20260826",
          quantization: "fp4",
          status: 0,
          uptime: { last5m: null, last30m: 99.9, last1d: null },
          pricing: { prompt: 0.00000015, completion: 0.0000005, inputCacheRead: null, discount: null },
          contextLength: 1048576,
          maxCompletionTokens: 943718,
          supportedParameters: null,
        },
      ])
      expect(calls).toHaveLength(1)
      const call = calls[0]
      if (!call) throw new Error("missing endpoints request")
      expect(url(call.input)).toBe(upstreamURL)
    } finally {
      mock.mockRestore()
    }
  })

  test("attaches the stored BYOK OpenRouter key as Bearer", async () => {
    process.env.KILO_AUTH_CONTENT = JSON.stringify({ openrouter: { type: "api", key: "sk-or-test" } })
    const headers: Array<Record<string, string>> = []
    const mock = stub(async (input, init) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      headers.push(new Headers(init?.headers).toJSON())
      return endpoints({ data: { endpoints: [{ tag: "openai" }] } })
    })

    try {
      const response = await send()
      expect(response.status).toBe(200)
      expect(headers[0]?.authorization).toBe("Bearer sk-or-test")
    } finally {
      mock.mockRestore()
    }
  })

  test("calls the endpoints API publicly without a stored key", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    const headers: Array<Record<string, string>> = []
    const mock = stub(async (input, init) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      headers.push(new Headers(init?.headers).toJSON())
      return endpoints({ data: { endpoints: [{ tag: "openai" }] } })
    })

    try {
      const response = await send()
      expect(response.status).toBe(200)
      expect(headers[0]?.authorization).toBeUndefined()
      expect(await response.json()).toEqual([
        {
          tag: "openai",
          providerName: null,
          name: null,
          quantization: null,
          status: null,
          uptime: { last5m: null, last30m: null, last1d: null },
          pricing: { prompt: null, completion: null, inputCacheRead: null, discount: null },
          contextLength: null,
          maxCompletionTokens: null,
          supportedParameters: null,
        },
      ])
    } finally {
      mock.mockRestore()
    }
  })

  test("passes unknown models through as 404", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    const mock = stub(async (input) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      return endpoints({ error: { message: "Not Found", code: 404 } }, 404)
    })

    try {
      const response = await send(RoutesPaths.list.replace(":author", "z-ai").replace(":slug", "nonexistent"))
      expect(response.status).toBe(404)
    } finally {
      mock.mockRestore()
    }
  })

  test("maps other upstream failures to a 502 RouteListError", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    const mock = stub(async (input) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      return endpoints({ error: { message: "boom" } }, 503)
    })

    try {
      const response = await send()
      expect(response.status).toBe(502)
      expect(await response.json()).toMatchObject({
        _tag: "RouteListError",
        message: 'OpenRouter endpoints request failed: 503 {"error":{"message":"boom"}}',
        status: 503,
      })
    } finally {
      mock.mockRestore()
    }
  })

  test("maps upstream network failures to a 502 RouteListError", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    const mock = stub(async (input) => {
      if (!url(input).includes("openrouter.ai")) return Response.json([])
      throw new Error("connection refused")
    })

    try {
      const response = await send()
      expect(response.status).toBe(502)
      expect(await response.json()).toMatchObject({
        _tag: "RouteListError",
        message: "OpenRouter endpoints request failed: connection refused",
      })
    } finally {
      mock.mockRestore()
    }
  })

  test("rejects malformed path segments", async () => {
    process.env.KILO_AUTH_CONTENT = "{}"
    // URL normalization would resolve literal ".." segments, so exercise the
    // validator through percent-encoded separators and stray characters instead.
    for (const slug of ["bad%20slug", "x%2Fy", "%22quote%22"]) {
      const response = await send(RoutesPaths.list.replace(":author", "z-ai").replace(":slug", slug))
      expect(response.status, slug).toBe(400)
    }
  })
})
