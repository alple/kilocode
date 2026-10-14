import { describe, expect, test } from "bun:test"
import { LayerNode } from "@opencode-ai/core/effect/layer-node"
import { ProviderV2 } from "@opencode-ai/core/provider"
import type { Provider as CoreProvider } from "@opencode-ai/core/models-dev"
import { Effect } from "effect"
import type { Config } from "@/config/config"
import { Env } from "@/env"
import { Plugin } from "@/plugin"
import { Provider } from "@/provider/provider"
import { expandClones } from "../../src/provider/models"
import { DEFAULT_HEADERS } from "../../src/kilocode/const"
import { patchKiloProviderClones } from "../../src/kilocode/provider/provider"
import type { Info as ProviderInfo } from "../../src/provider/provider"
import { testEffect } from "../lib/effect"

const base = {
  id: "openrouter",
  name: "OpenRouter",
  env: ["OPENROUTER_API_KEY"],
  api: "https://openrouter.ai/api/v1",
  npm: "@openrouter/ai-sdk-provider",
  models: {
    "anthropic/claude-sonnet-4-5": { id: "anthropic/claude-sonnet-4-5", name: "Claude Sonnet 4.5" },
    "gpt-5-chat-latest": { id: "gpt-5-chat-latest", name: "GPT-5 Chat" },
    "openai/gpt-5-chat": { id: "openai/gpt-5-chat", name: "GPT-5 Chat" },
  },
} as unknown as CoreProvider

function providersWith(...entries: Record<string, unknown>[]): Record<string, CoreProvider> {
  const providers = { openrouter: structuredClone(base) }
  const cfg = { provider: entries[0] } as Config.Info
  expandClones(cfg, providers)
  return providers
}

describe("expandClones", () => {
  test("expands a clone entry with the full base catalog", () => {
    const providers = providersWith({ "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" } })
    const clone = providers["openrouter-work"]
    expect(clone).toBeDefined()
    expect(clone!.name).toBe("OpenRouter Work")
    expect(clone!.api).toBe(base.api)
    expect(clone!.npm).toBe(base.npm)
    expect("anthropic/claude-sonnet-4-5" in clone!.models).toBe(true)
  })

  test("strips inherited env vars so clones never read the base key", () => {
    const providers = providersWith({ "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" } })
    expect(providers["openrouter-work"]!.env).toEqual([])
    expect(providers.openrouter.env).toEqual(["OPENROUTER_API_KEY"])
  })

  test("prunes invalid alias models from the clone only", () => {
    const providers = providersWith({ "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" } })
    const clone = providers["openrouter-work"]!
    expect("gpt-5-chat-latest" in clone.models).toBe(false)
    expect("openai/gpt-5-chat" in clone.models).toBe(false)
    expect("gpt-5-chat-latest" in providers.openrouter.models).toBe(true)
    expect("openai/gpt-5-chat" in providers.openrouter.models).toBe(true)
  })

  test("never mutates the base entry", () => {
    const providers = providersWith({ "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" } })
    const cloneModel = providers["openrouter-work"]!.models["anthropic/claude-sonnet-4-5"]!
    const baseModel = providers.openrouter.models["anthropic/claude-sonnet-4-5"]!
    expect(cloneModel).not.toBe(baseModel)
    ;(cloneModel as { name: string }).name = "mutated"
    expect(baseModel.name).toBe("Claude Sonnet 4.5")
  })

  test("skips entries without a resolvable base", () => {
    const providers = providersWith({ "ghost-clone": { cloneOf: "nonexistent", name: "Ghost" } })
    expect(providers["ghost-clone"]).toBeUndefined()
  })

  test("skips clones colliding with real catalog entries", () => {
    const providers = providersWith({ openrouter: { cloneOf: "anthropic", name: "Shadow" } })
    expect(providers.openrouter.name).toBe("OpenRouter")
  })

  test("ignores self-references and null entries", () => {
    const providers = providersWith({
      openrouter: { cloneOf: "openrouter", name: "Self" },
      "broken-clone": null,
      "plain-clone": { cloneOf: "openrouter", name: "Plain" },
    })
    expect(providers.openrouter.name).toBe("OpenRouter")
    expect(providers["broken-clone"]).toBeUndefined()
    expect(providers["plain-clone"]).toBeDefined()
  })

  test("supports multiple independent clones of one base", () => {
    const providers = providersWith({
      "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" },
      "openrouter-personal": { cloneOf: "openrouter", name: "OpenRouter Personal" },
    })
    const work = providers["openrouter-work"]!.models["anthropic/claude-sonnet-4-5"]!
    const personal = providers["openrouter-personal"]!.models["anthropic/claude-sonnet-4-5"]!
    expect(work).not.toBe(personal)
    expect(providers["openrouter-work"]!.name).toBe("OpenRouter Work")
    expect(providers["openrouter-personal"]!.name).toBe("OpenRouter Personal")
  })

  test("falls back to the base name when the config entry has none", () => {
    const providers = providersWith({ "openrouter-work": { cloneOf: "openrouter" } })
    expect(providers["openrouter-work"]!.name).toBe("OpenRouter")
  })
})

function cloneProviders(clones: Record<string, ProviderInfo>): Record<string, ProviderInfo> {
  return { openrouter: { id: "openrouter" } as ProviderInfo, ...clones }
}

describe("patchKiloProviderClones", () => {
  test("applies Kilo branding headers to clone providers", () => {
    const providers = cloneProviders({
      "openrouter-work": { id: "openrouter-work", options: {} } as unknown as ProviderInfo,
    })
    const cfg = { provider: { "openrouter-work": { cloneOf: "openrouter" } } } as Config.Info
    patchKiloProviderClones(providers, cfg)
    expect(providers["openrouter-work"]!.options.headers).toMatchObject(DEFAULT_HEADERS)
    expect(providers.openrouter.options).toBeUndefined()
  })

  test("lets user-configured headers win per key", () => {
    const providers = cloneProviders({
      "openrouter-work": {
        id: "openrouter-work",
        options: { headers: { "X-Title": "Mine" } },
      } as unknown as ProviderInfo,
    })
    const cfg = { provider: { "openrouter-work": { cloneOf: "openrouter" } } } as Config.Info
    patchKiloProviderClones(providers, cfg)
    const headers = providers["openrouter-work"]!.options.headers as Record<string, string>
    expect(headers["X-Title"]).toBe("Mine")
    expect(headers["HTTP-Referer"]).toBe(DEFAULT_HEADERS["HTTP-Referer"])
  })

  test("ignores unknown clone ids", () => {
    const providers = cloneProviders({})
    const cfg = { provider: { "ghost-clone": { cloneOf: "openrouter" } } } as Config.Info
    expect(() => patchKiloProviderClones(providers, cfg)).not.toThrow()
  })
})

const it = testEffect(LayerNode.compile(LayerNode.group([Provider.node, Env.node, Plugin.node])))

it.instance(
  "exposes clones through Provider.list with catalog models and branding headers",
  () =>
    Effect.gen(function* () {
      const providers = yield* Provider.use.list()
      const clone = providers[ProviderV2.ID.make("openrouter-work")]
      expect(clone).toBeDefined()
      expect(clone!.name).toBe("OpenRouter Work")
      expect(Object.keys(clone!.models).length).toBeGreaterThan(0)
      expect(clone!.env).toEqual([])
      expect(clone!.source).toBe("config")
      const headers = clone!.options.headers as Record<string, string>
      expect(headers["HTTP-Referer"]).toBe(DEFAULT_HEADERS["HTTP-Referer"])
      expect("openai/gpt-5-chat" in clone!.models).toBe(false)
    }),
  { config: { provider: { "openrouter-work": { cloneOf: "openrouter", name: "OpenRouter Work" } } } },
)
