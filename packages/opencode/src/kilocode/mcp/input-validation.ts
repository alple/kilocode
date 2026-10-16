import type { JSONSchema7 } from "@ai-sdk/provider"

// Tolerant structural validation of MCP tool inputs against the tool's declared
// schema. MCP tools run with zero argument validation today (the AI SDK passes
// parsed JSON straight through), so a model that emits valid-but-incomplete
// arguments silently executes and servers accept the partial payload. This
// guard converts those silent losses into loud, retryable failures.
//
// Deliberately permissive: it only rejects clear violations (missing required
// keys, primitive/nested type mismatches, enum misses). Combinators, $refs and
// unknown keywords are skipped rather than guessed at, values are never
// mutated, and the kill switch covers servers with pathological schemas.

const MAX_DEPTH = 32
const MAX_ISSUES = 10
const COMBINATORS = ["anyOf", "oneOf", "allOf", "not", "$ref", "if"]
const TYPES = new Set(["string", "number", "integer", "boolean", "object", "array", "null"])

type Node = Record<string, unknown>

function record(value: unknown): value is Node {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function kind(value: unknown): string {
  if (value === null) return "null"
  if (Array.isArray(value)) return "array"
  if (typeof value === "number") return Number.isInteger(value) ? "integer" : "number"
  return typeof value
}

function fits(actual: string, expected: string): boolean {
  if (actual === expected) return true
  return expected === "number" && actual === "integer"
}

function off(): boolean {
  const flag = process.env.KILO_DISABLE_MCP_INPUT_VALIDATION
  return flag === "1" || flag === "true" || flag === "yes"
}

function walk(value: unknown, schema: unknown, path: string, issues: string[], depth: number): void {
  if (issues.length >= MAX_ISSUES || depth > MAX_DEPTH) return
  if (!record(schema)) return
  if (value === null) return
  if (COMBINATORS.some((key) => key in schema)) return

  const declared =
    typeof schema.type === "string"
      ? [schema.type]
      : Array.isArray(schema.type)
        ? schema.type.filter((item): item is string => typeof item === "string")
        : []
  if (declared.length > 0 && !declared.every((item) => TYPES.has(item))) return

  const actual = kind(value)
  if (declared.length > 0 && !declared.some((item) => fits(actual, item))) {
    issues.push(`${path} must be ${declared.join(" | ")}, got ${actual}`)
    return
  }

  if (Array.isArray(schema.enum) && !schema.enum.some((item) => item === value)) {
    issues.push(`${path} must be one of ${JSON.stringify(schema.enum)}, got ${JSON.stringify(value)}`)
    return
  }

  if (record(value)) {
    const required = Array.isArray(schema.required)
      ? schema.required.filter((item): item is string => typeof item === "string")
      : []
    for (const name of required) {
      if (!(name in value)) issues.push(`missing required property "${name}" at ${path}`)
    }
    const props = record(schema.properties) ? schema.properties : {}
    for (const [key, item] of Object.entries(props)) {
      if (key in value) walk(value[key], item, `${path}.${key}`, issues, depth + 1)
    }
    return
  }

  if (Array.isArray(value) && record(schema.items)) {
    value.forEach((item, index) => walk(item, schema.items, `${path}[${index}]`, issues, depth + 1))
    const min = schema.minItems
    if (typeof min === "number" && value.length < min) {
      issues.push(`${path} must have at least ${min} items, got ${value.length}`)
    }
  }
}

function message(name: string, issues: string[]): string {
  const shown = issues.slice(0, MAX_ISSUES).join("; ")
  const extra = issues.length > MAX_ISSUES ? ` (+${issues.length - MAX_ISSUES} more)` : ""
  return (
    `the tool call arguments do not match the "${name}" tool's declared schema: ${shown}${extra}. ` +
    `Modify the arguments to fix each violation and call the "${name}" tool again with the updated input.`
  )
}

export function validator(name: string, schema: JSONSchema7) {
  return async (value: unknown) => {
    if (off()) return { success: true as const, value }
    const issues: string[] = []
    walk(value, schema, "$", issues, 0)
    if (issues.length === 0) return { success: true as const, value }
    return { success: false as const, error: new Error(message(name, issues)) }
  }
}
