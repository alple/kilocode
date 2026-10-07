package ai.kilocode.client.deluxe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the per-project parameter store (ADR-0002): the single write target for mode, model,
 * effort, and route picks. Production persists [State] to workspace.xml; these tests exercise the
 * same state contract the platform serializes.
 */
class KiloProjectParameterStoreTest {

    @Test
    fun `state round trips through the platform state shape`() {
        val first = KiloProjectParameterStore()
        first.setAgent("plan")
        first.setModel("plan", "kilo", "opus")
        first.setVariant("kilo/opus", "high")
        first.setRoute("kilo/opus", "deepseek/r1")

        val restored = KiloProjectParameterStore()
        restored.loadState(first.getState())

        assertEquals("plan", restored.getAgent())
        assertEquals("kilo/opus", restored.model("plan"))
        assertEquals("high", restored.variant("kilo/opus"))
        assertEquals("deepseek/r1", restored.route("kilo/opus"))
    }

    @Test
    fun `model picks key per agent`() {
        val s = KiloProjectParameterStore()
        s.setModel("code", "kilo", "gpt-5")
        s.setModel("plan", "openai", "gpt")

        assertEquals("kilo/gpt-5", s.model("code"))
        assertEquals("openai/gpt", s.model("plan"))
        assertNull(s.model("build"))

        s.clearModel("code")
        assertNull(s.model("code"))
        assertEquals("openai/gpt", s.model("plan"))
    }

    @Test
    fun `variant and route key per provider-model pair`() {
        val s = KiloProjectParameterStore()
        s.setVariant("kilo/gpt-5", "high")
        s.setRoute("kilo/gpt-5", "deepseek/r1")

        assertEquals("high", s.variant("kilo/gpt-5"))
        assertNull(s.variant("anthropic/claude"))
        assertEquals("deepseek/r1", s.route("kilo/gpt-5"))
        assertNull(s.route("anthropic/claude"))

        s.clearRoute("kilo/gpt-5")
        assertNull(s.route("kilo/gpt-5"))
        assertEquals("high", s.variant("kilo/gpt-5"))
    }

    @Test
    fun `blank values never enter the state`() {
        val s = KiloProjectParameterStore()
        s.loadState(KiloProjectParameterStore.State(
            model = mapOf("code" to " "),
            variant = mapOf("kilo/gpt-5" to ""),
            route = mapOf("kilo/gpt-5" to "  "),
            agent = "",
        ))

        assertNull(s.model("code"))
        assertNull(s.variant("kilo/gpt-5"))
        assertNull(s.route("kilo/gpt-5"))
        assertNull(s.getAgent())
    }

    @Test
    fun `a fresh store is empty`() {
        val s = KiloProjectParameterStore()

        assertNull(s.getAgent())
        assertNull(s.model("code"))
        assertNull(s.variant("kilo/gpt-5"))
        assertNull(s.route("kilo/gpt-5"))
        assertEquals(KiloProjectParameterStore.State(), s.getState())
    }
}
