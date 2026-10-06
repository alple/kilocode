import { describe, expect, test } from "bun:test"
import * as KiloRoute from "../../../src/kilocode/session/route"

const model = (npm: string) => ({ api: { npm } })

describe("KiloRoute.resolve", () => {
  test("absent, empty, and blank routes are no-ops", () => {
    expect(KiloRoute.resolve({ model: model("@openrouter/ai-sdk-provider") })).toBeUndefined()
    expect(KiloRoute.resolve({ route: "", model: model("@openrouter/ai-sdk-provider") })).toBeUndefined()
    expect(KiloRoute.resolve({ route: "   ", model: model("@openrouter/ai-sdk-provider") })).toBeUndefined()
  })

  test("gates the pin to the OpenRouter transports", () => {
    expect(KiloRoute.resolve({ route: "parasail/fp4", model: model("@ai-sdk/anthropic") })).toBeUndefined()
    expect(KiloRoute.resolve({ route: "parasail/fp4", model: model("@ai-sdk/openai-compatible") })).toBeUndefined()
  })

  test("emits exact pin semantics for both transports", () => {
    const pinned = { provider: { order: ["parasail/fp4"], allow_fallbacks: false } }
    expect(KiloRoute.resolve({ route: "parasail/fp4", model: model("@openrouter/ai-sdk-provider") })).toEqual(pinned)
    expect(KiloRoute.resolve({ route: "parasail/fp4", model: model("@kilocode/kilo-gateway") })).toEqual(pinned)
  })

  test("plain provider tags are accepted like variant tags", () => {
    expect(KiloRoute.resolve({ route: "openai", model: model("@openrouter/ai-sdk-provider") })).toEqual({
      provider: { order: ["openai"], allow_fallbacks: false },
    })
  })
})
