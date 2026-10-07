package ai.kilocode.client.deluxe

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JToggleButton

/**
 * The routing settings page: the toggle and the provider list are local page state; Apply writes
 * them into [KiloRoutingOptions], Reset restores what the option holds. There is no CLI round-trip.
 */
class KiloRoutingConfigurableTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            KiloRoutingOptions.getInstance().update(false, KiloRoutingOptions.DEFAULT_PROVIDERS)
        } finally {
            super.tearDown()
        }
    }

    fun `test isModified is false for a fresh page`() {
        val cfg = KiloRoutingConfigurable()
        cfg.createComponent()

        assertFalse(cfg.isModified)
    }

    fun `test isModified reflects an option change behind the open page`() {
        val cfg = KiloRoutingConfigurable()
        cfg.createComponent()
        assertFalse(cfg.isModified)

        KiloRoutingOptions.getInstance().update(enabled = true, providers = listOf("openrouter"))

        assertTrue(cfg.isModified)
        cfg.reset()
        assertFalse("Reset restores the stored option", cfg.isModified)
    }

    fun `test toggling the page writes through apply`() {
        val cfg = KiloRoutingConfigurable()
        val page = cfg.createComponent() as JComponent
        val toggle = toggles(page).single()
        toggle.doClick()

        assertTrue("The toggle edits the page state", cfg.isModified)

        cfg.apply()

        assertTrue(KiloRoutingOptions.getInstance().enabled)
        assertFalse("Apply consumes the modification", cfg.isModified)
    }

    fun `test apply sanitizes the provider list`() {
        val options = KiloRoutingOptions.getInstance()
        options.update(enabled = true, providers = listOf("openrouter", " clone "))
        val cfg = KiloRoutingConfigurable()
        val page = cfg.createComponent() as KiloRoutingPage

        cfg.apply()

        assertEquals(listOf("openrouter", "clone"), options.providers)
        assertEquals(listOf("openrouter", "clone"), page.providers())
    }

    fun `test apply disables the gate and the page follows`() {
        val options = KiloRoutingOptions.getInstance()
        options.update(enabled = true, providers = listOf("openrouter"))
        val cfg = KiloRoutingConfigurable()
        val page = cfg.createComponent() as KiloRoutingPage
        toggles(page).single().doClick()

        cfg.apply()

        assertFalse(options.enabled)
        assertFalse(page.enabled())
        assertFalse(cfg.isModified)
    }
}

private fun toggles(root: Container): List<JToggleButton> {
    val out = mutableListOf<JToggleButton>()
    if (root is JToggleButton) out += root
    for (child in root.components) {
        if (child is Container) out += toggles(child)
    }
    return out
}
