package ai.kilocode.client.plugin

import ai.kilocode.KiloPlugin
import ai.kilocode.client.testing.FakePluginDescriptor
import ai.kilocode.client.testing.PluginDescriptor
import ai.kilocode.client.testing.attribute
import ai.kilocode.client.testing.elements
import com.intellij.ide.plugins.DynamicPluginVetoer
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * KILO-6 guard wiring: the vetoer and the startup activity must be declared by the frontend module
 * descriptor, instantiable, and inert when they cannot prove which Kilo identity is running — the
 * fail-safe direction, because a guessed identity could disable the wrong plugin.
 */
class KiloConflictWiringTest : BasePlatformTestCase() {

    fun `test descriptor declares the vetoer and the startup activity`() {
        val vetoer = PluginDescriptor.frontend().elements("ide.dynamicPluginVetoer")
            .mapNotNull { it.attribute("implementation") }
        val startup = PluginDescriptor.frontend().elements("postStartupActivity")
            .mapNotNull { it.attribute("implementation") }

        assertEquals(listOf("ai.kilocode.client.plugin.KiloConflictVetoer"), vetoer)
        assertEquals(listOf("ai.kilocode.client.plugin.KiloConflictStartupActivity"), startup)
    }

    fun `test vetoer is a real DynamicPluginVetoer and unloads stay allowed`() {
        val vetoer = Class.forName("ai.kilocode.client.plugin.KiloConflictVetoer")
            .getDeclaredConstructor().newInstance() as DynamicPluginVetoer

        assertNull(vetoer.vetoPluginUnload(FakePluginDescriptor("ai.kilocode.jetbrains")))
    }

    fun `test vetoer is inert without a resolvable own identity`() {
        // The test classpath does not run inside a Kilo plugin classloader, so the own identity is
        // not a Kilo id and nothing may be vetoed — the fail-safe the production check relies on.
        assertNull(KiloPlugin.ownIdentity)
        val vetoer = KiloConflictVetoer()

        assertFalse(vetoer.vetoPluginLoad(FakePluginDescriptor(KiloPlugin.LUX_ID)))
        assertFalse(vetoer.vetoPluginLoad(FakePluginDescriptor(KiloPlugin.ID)))
    }

    fun `test startup activity is a real ProjectActivity`() {
        val activity = Class.forName("ai.kilocode.client.plugin.KiloConflictStartupActivity")
            .getDeclaredConstructor().newInstance()

        assertTrue(activity is ProjectActivity)
    }

    fun `test conflict notification strings exist in the base bundle`() {
        val keys = listOf(
            "plugin.conflict.notification.title",
            "plugin.conflict.notification.body",
            "plugin.conflict.notification.restart",
        )

        // optional() consults containsKey; message() would return "!key!" and pass vacuously.
        val missing = keys.filter { KiloBundle.optional(it).isNullOrBlank() }

        assertEquals("conflict guard strings missing from the base bundle", emptyList<String>(), missing)
    }
}
