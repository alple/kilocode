import { describe, expect, test } from "bun:test"
import { asSchema } from "ai"
import { McpCatalog } from "@/mcp/catalog"

function clientReturning(result: unknown) {
  return {
    callTool: async () => result,
  } as any
}

describe("McpCatalog.convertTool input validation", () => {
  test("validates model tool inputs against the declared schema", async () => {
    const tool = {
      name: "edit",
      description: "Edit an issue",
      inputSchema: {
        type: "object",
        properties: { fields: { type: "object" } },
        required: ["fields"],
      },
    } as any
    const converted = McpCatalog.convertTool(tool, clientReturning({ content: [] }))

    const validate = asSchema(converted.inputSchema).validate
    expect(validate).toBeDefined()

    const good = await validate!({ fields: {} })
    expect(good.success).toBe(true)

    const bad = await validate!({})
    expect(bad.success).toBe(false)
    const text = (bad as { error: Error }).error.message
    expect(text).toContain('missing required property "fields" at $')
    expect(text).toContain('call the "edit" tool again')
  })
})
