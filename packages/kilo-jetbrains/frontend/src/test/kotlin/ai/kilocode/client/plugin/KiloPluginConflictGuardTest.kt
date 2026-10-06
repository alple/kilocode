package ai.kilocode.client.plugin

import ai.kilocode.KiloPlugin
import ai.kilocode.client.testing.FakePluginDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val stable = KiloPlugin.ID
private val lux = KiloPlugin.LUX_ID
private val other = "com.example.other"

private fun descriptor(id: String, name: String = id, enabled: Boolean = true) = FakePluginDescriptor(id, name, enabled)

/**
 * The decision core of the KILO-6 guard: which enabled Kilo identity is the conflict, and which
 * dynamic loads must not get a second classloader. The enforce wiring is covered by the platform
 * wiring test; the vetoer and the startup activity both funnel into these two functions.
 */
class KiloPluginConflictGuardTest {

    @Test
    fun `conflict points at the other identity when lux starts`() {
        val plugins = listOf(descriptor(stable, "Kilo Code"), descriptor(lux, "Kilo Code (lux)"))

        assertEquals(lux, KiloPluginConflictGuard.conflict(plugins, stable)!!.pluginId.idString)
    }

    @Test
    fun `conflict points at the other identity when stable starts`() {
        val plugins = listOf(descriptor(stable, "Kilo Code"), descriptor(lux, "Kilo Code (lux)"))

        assertEquals(stable, KiloPluginConflictGuard.conflict(plugins, lux)!!.pluginId.idString)
    }

    @Test
    fun `no conflict when exactly one identity is enabled`() {
        val plugins = listOf(descriptor(stable, "Kilo Code", enabled = true), descriptor(lux, enabled = false))

        assertNull(KiloPluginConflictGuard.conflict(plugins, stable))
    }

    @Test
    fun `no conflict among unrelated plugins`() {
        val plugins = listOf(descriptor(stable), descriptor(other), descriptor("com.example.other2"))

        assertNull(KiloPluginConflictGuard.conflict(plugins, stable))
    }

    @Test
    fun `blocks the other identity whichever side loads`() {
        assertTrue(KiloPluginConflictGuard.blocks(descriptor(lux), stable, emptySet()))
        assertTrue(KiloPluginConflictGuard.blocks(descriptor(stable), lux, emptySet()))
    }

    @Test
    fun `never blocks own id so in-place upgrades survive`() {
        assertFalse(KiloPluginConflictGuard.blocks(descriptor(stable), stable, emptySet()))
        assertFalse(KiloPluginConflictGuard.blocks(descriptor(lux), lux, emptySet()))
    }

    @Test
    fun `leaves the other identity alone when it replaces itself`() {
        // The other identity was unloaded for an update (beforePluginUnload with isUpdate) and its
        // same-id version is loading back; that replacement must not be vetoed (KILO-6 ac 3).
        assertFalse(KiloPluginConflictGuard.blocks(descriptor(lux), stable, setOf(lux)))
        assertFalse(KiloPluginConflictGuard.blocks(descriptor(stable), lux, setOf(stable)))
    }

    @Test
    fun `ignores unrelated plugin loads`() {
        assertFalse(KiloPluginConflictGuard.blocks(descriptor(other), stable, emptySet()))
    }

    @Test
    fun `identity ids are exactly stable and lux`() {
        assertEquals(setOf(stable, lux), KiloPlugin.ids)
    }
}
