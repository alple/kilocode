import { describe, expect, test } from "bun:test"
import type { JSONSchema7 } from "@ai-sdk/provider"
import { validator } from "@/kilocode/mcp/input-validation"

const editSchema: JSONSchema7 = {
  type: "object",
  properties: {
    cloudId: { type: "string" },
    issueIdOrKey: { type: "string" },
    fields: {
      type: "object",
      properties: {
        parent: { type: "string" },
        description: { type: "string" },
      },
      required: ["parent"],
    },
  },
  required: ["cloudId", "issueIdOrKey"],
}

const complete = {
  cloudId: "abc",
  issueIdOrKey: "T-1",
  fields: { parent: "T-0", description: "body" },
}

describe("validator", () => {
  test("passes a complete nested payload through unchanged", async () => {
    const validate = validator("edit", editSchema)
    const result = await validate(complete)
    expect(result).toEqual({ success: true, value: complete })
    expect((result as { value: unknown }).value).toBe(complete)
  })

  test("rejects a missing required root key and instructs a retry", async () => {
    const validate = validator("edit", editSchema)
    const result = await validate({ issueIdOrKey: "T-1", fields: { parent: "T-0" } })
    expect(result.success).toBe(false)
    const text = (result as { error: Error }).error.message
    expect(text).toContain(`"edit" tool's declared schema`)
    expect(text).toContain('missing required property "cloudId" at $')
    expect(text).toContain("Modify the arguments")
    expect(text).toContain('call the "edit" tool again')
  })

  test("rejects a missing required key inside a nested object", async () => {
    const validate = validator("edit", editSchema)
    const result = await validate({ cloudId: "abc", issueIdOrKey: "T-1", fields: { description: "body" } })
    expect(result.success).toBe(false)
    expect((result as { error: Error }).error.message).toContain('missing required property "parent" at $.fields')
  })

  test("rejects a nested type mismatch with the dotted path", async () => {
    const validate = validator("edit", editSchema)
    const result = await validate({ ...complete, fields: { parent: 7 } })
    expect(result.success).toBe(false)
    expect((result as { error: Error }).error.message).toContain("$.fields.parent must be string, got integer")
  })

  test("rejects a root type mismatch", async () => {
    const validate = validator("edit", editSchema)
    const result = await validate("nope")
    expect(result.success).toBe(false)
    expect((result as { error: Error }).error.message).toContain("$ must be object, got string")
  })

  test("enforces required names that lack a property definition", async () => {
    const schema: JSONSchema7 = { type: "object", required: ["only"] }
    const result = await validator("t", schema)({})
    expect(result.success).toBe(false)
    expect((result as { error: Error }).error.message).toContain('missing required property "only" at $')
  })

  test("accepts integer for a number-typed property", async () => {
    const schema: JSONSchema7 = { type: "object", properties: { count: { type: "number" } } }
    const result = await validator("t", schema)({ count: 3 })
    expect(result.success).toBe(true)
  })

  test("skips combinators instead of guessing", async () => {
    const schema: JSONSchema7 = {
      type: "object",
      properties: { value: { anyOf: [{ type: "string" }, { type: "number" }] } },
    }
    const result = await validator("t", schema)({ value: { arbitrary: true } })
    expect(result.success).toBe(true)
  })

  test("skips unknown type keywords", async () => {
    const schema = { type: "object", properties: { value: { type: "widget" } } } as unknown as JSONSchema7
    const result = await validator("t", schema)({ value: 1 })
    expect(result.success).toBe(true)
  })

  test("treats null as unpresentable-but-tolerated", async () => {
    const schema: JSONSchema7 = { type: "object", properties: { name: { type: "string" } }, required: ["name"] }
    const result = await validator("t", schema)({ name: null })
    expect(result.success).toBe(true)
  })

  test("rejects an enum miss", async () => {
    const schema: JSONSchema7 = {
      type: "object",
      properties: { size: { type: "string", enum: ["small", "large"] } },
    }
    const result = await validator("t", schema)({ size: "huge" })
    expect(result.success).toBe(false)
    expect((result as { error: Error }).error.message).toContain("$.size must be one of")
  })

  test("walks array items and enforces minItems", async () => {
    const schema: JSONSchema7 = {
      type: "object",
      properties: {
        deps: { type: "array", items: { type: "string" }, minItems: 2 },
      },
    }
    const missing = await validator("t", schema)({ deps: ["a"] })
    expect(missing.success).toBe(false)
    expect((missing as { error: Error }).error.message).toContain("$.deps must have at least 2 items")

    const typed = await validator("t", schema)({ deps: ["a", 2] })
    expect(typed.success).toBe(false)
    expect((typed as { error: Error }).error.message).toContain("$.deps[1] must be string, got integer")
  })

  test("kill switch disables validation", async () => {
    const previous = process.env.KILO_DISABLE_MCP_INPUT_VALIDATION
    process.env.KILO_DISABLE_MCP_INPUT_VALIDATION = "1"
    try {
      const result = await validator("edit", editSchema)({ cloudId: 1 })
      expect(result).toEqual({ success: true, value: { cloudId: 1 } })
    } finally {
      if (previous === undefined) delete process.env.KILO_DISABLE_MCP_INPUT_VALIDATION
      else process.env.KILO_DISABLE_MCP_INPUT_VALIDATION = previous
    }
  })
})
