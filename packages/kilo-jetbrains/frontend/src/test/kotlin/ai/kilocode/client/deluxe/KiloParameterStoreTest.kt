package ai.kilocode.client.deluxe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/** Round-trips the project store's persisted state through getState/loadState. */
class KiloParameterStoreTest {

    fun `test getState loadState round-trips picks`() {
        val persisted = KiloParameterStore().apply {
            setAgent("plan")
            setModel("plan", "kilo", "opus")
            setVariant("plan/kilo/opus", "high")
        }
        val restored = KiloParameterStore()
        restored.loadState(persisted.state)

        assertEquals("plan", restored.agent())
        assertEquals("kilo/opus", restored.model("plan"))
        assertEquals("high", restored.variant("plan/kilo/opus"))
    }

    fun `test setModel stores the provider slash model key per agent`() {
        val store = KiloParameterStore()
        store.setModel("code", "kilo", "kilo-auto/free")

        assertEquals("kilo/kilo-auto/free", store.model("code"))
        assertNull(store.model("plan"))
    }

    fun `test blank entries load as absent`() {
        val store = KiloParameterStore()
        store.loadState(
            KiloParameterStore.State(
                model = mapOf("" to "kilo/gpt-5", "code" to ""),
                variant = mapOf("code/kilo/gpt-5" to "", "" to "high"),
                agent = "  ",
            ),
        )

        assertNull(store.agent())
        assertNull(store.model("code"))
        assertNull(store.variant("code/kilo/gpt-5"))
        assertEquals(0, store.state.model.size)
        assertEquals(0, store.state.variant.size)
    }

    fun `test setters ignore blank values and clear removes entries`() {
        val store = KiloParameterStore()
        store.setAgent("   ")
        store.setModel("code", "kilo", " ")
        store.setVariant("code/kilo/gpt-5", "")

        assertNull(store.agent())
        assertNull(store.model("code"))
        assertNull(store.variant("code/kilo/gpt-5"))

        store.setAgent("plan")
        store.setModel("plan", "kilo", "opus")
        store.setVariant("plan/kilo/opus", "high")
        store.clearModel("plan")
        store.clearVariant("plan/kilo/opus")
        store.clearAgent()

        assertNull(store.agent())
        assertNull(store.model("plan"))
        assertNull(store.variant("plan/kilo/opus"))
        // Clearing an absent key is a no-op.
        store.clearModel("plan")
        store.clearVariant("plan/kilo/opus")
    }

    fun `test getState returns a stable snapshot across writes`() {
        val store = KiloParameterStore()
        val before = store.getState()
        store.setAgent("plan")
        store.setModel("plan", "kilo", "opus")

        assertEquals(KiloParameterStore.State(), before)
        assertEquals("plan", store.getState().agent)
        assertEquals("kilo/opus", store.getState().model["plan"])
    }
}
