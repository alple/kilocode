import { describe, expect, test } from "bun:test"
import type { Model } from "../../../src/provider/models"
import { kiloModels } from "../../../src/provider/models"

function model(id: string, extra: Partial<Model> = {}): Model {
  return {
    id,
    name: id,
    release_date: "2026-01-01",
    attachment: false,
    reasoning: false,
    temperature: false,
    tool_call: true,
    limit: { context: 200_000, output: 8_192 },
    ...extra,
  } as Model
}

describe("kiloModels catalog assembly", () => {
  const withRanking = { "kilo-auto/small": model("kilo-auto/small", { recommendedIndex: 0 }) }
  const mixed = {
    "kilo-auto/small": model("kilo-auto/small", { recommendedIndex: 0 }),
    "z-ai/glm-5.2": model("z-ai/glm-5.2", { recommendedIndex: 1 }),
    "openai/gpt": model("openai/gpt"),
  }

  test("logged-out sessions lose the recommendation ranking", () => {
    const out = kiloModels({ creds: undefined, valid: true, org: undefined, fetched: mixed, fallback: undefined })

    expect(out["kilo-auto/small"].recommendedIndex).toBeUndefined()
    expect(out["z-ai/glm-5.2"].recommendedIndex).toBeUndefined()
    // Other fields survive the strip
    expect(out["z-ai/glm-5.2"].name).toBe("z-ai/glm-5.2")
    expect(out["z-ai/glm-5.2"].limit).toEqual({ context: 200_000, output: 8_192 })
  })

  test("logged-in sessions keep the ranking", () => {
    const out = kiloModels({ creds: "sk-token", valid: true, org: undefined, fetched: mixed, fallback: undefined })

    expect(out["kilo-auto/small"].recommendedIndex).toBe(0)
    expect(out["z-ai/glm-5.2"].recommendedIndex).toBe(1)
    expect(out["openai/gpt"].recommendedIndex).toBeUndefined()
  })

  test("models without a ranking pass through untouched when logged out", () => {
    const plain = { "openai/gpt": model("openai/gpt") }
    const out = kiloModels({ creds: undefined, valid: true, org: undefined, fetched: plain, fallback: undefined })

    expect(out["openai/gpt"]).toBe(plain["openai/gpt"])
  })

  test("falls back to the bundled catalog when the fetch is empty and no org is set", () => {
    const out = kiloModels({
      creds: undefined,
      valid: true,
      org: undefined,
      fetched: {},
      fallback: withRanking,
    })

    expect(out["kilo-auto/small"].recommendedIndex).toBeUndefined()
  })

  test("keeps the bundled catalog ranking when logged in via fallback", () => {
    const out = kiloModels({ creds: "sk-token", valid: true, org: undefined, fetched: {}, fallback: withRanking })

    expect(out["kilo-auto/small"].recommendedIndex).toBe(0)
  })

  test("an org scope pins the fetched record even when empty", () => {
    const out = kiloModels({ creds: "sk-token", valid: true, org: "org-1", fetched: {}, fallback: withRanking })

    expect(out).toEqual({})
  })

  test("an invalid token/org combination yields no kilo models", () => {
    const out = kiloModels({ creds: "sk-token", valid: false, org: undefined, fetched: {}, fallback: withRanking })

    expect(out).toEqual({})
  })

  test("handles an empty catalog", () => {
    const out = kiloModels({ creds: undefined, valid: true, org: undefined, fetched: {}, fallback: undefined })

    expect(out).toEqual({})
  })
})
