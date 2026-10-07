package ai.kilocode.client.deluxe

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The Deluxe "enable model routing" option: off by default, the OpenRouter pair as the default
 * route-capable provider list, sanitized updates, and the [KiloRoutingOptions.routesEnabled] gate.
 */
class KiloRoutingOptionsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            KiloRoutingOptions.getInstance().update(false, KiloRoutingOptions.DEFAULT_PROVIDERS)
        } finally {
            super.tearDown()
        }
    }

    fun `test defaults are off with the OpenRouter pair`() {
        val options = KiloRoutingOptions()

        assertFalse(options.enabled)
        assertEquals(KiloRoutingOptions.DEFAULT_PROVIDERS, options.providers)
        assertFalse(options.routesEnabled("openrouter"))
    }

    fun `test sanitize trims dedups and drops blanks`() {
        assertEquals(listOf("openrouter", "clone"), KiloRoutingOptions.sanitize(listOf(" openrouter ", "", "clone", "openrouter")))
        assertEquals(listOf("a"), KiloRoutingOptions.sanitize(listOf("a")))
    }

    fun `test an empty provider list falls back to the defaults`() {
        assertEquals(KiloRoutingOptions.DEFAULT_PROVIDERS, KiloRoutingOptions.sanitize(emptyList()))
        assertEquals(KiloRoutingOptions.DEFAULT_PROVIDERS, KiloRoutingOptions.sanitize(listOf("  ", "")))
    }

    fun `test routesEnabled requires the gate and a listed provider`() {
        val options = KiloRoutingOptions()
        options.update(enabled = true, providers = listOf("openrouter", " clone "))

        assertTrue(options.routesEnabled("openrouter"))
        assertTrue("The user-added provider is sanitized into the list", options.routesEnabled("clone"))
        assertFalse("An unlisted provider gets no route UI", options.routesEnabled("anthropic"))
        assertFalse("Case matters, matching is plain id equality", options.routesEnabled("OpenRouter"))

        options.update(enabled = false, providers = listOf("openrouter"))
        assertFalse("The option gates all route UI", options.routesEnabled("openrouter"))
    }

    fun `test loadState sanitizes incoming storage`() {
        val options = KiloRoutingOptions()
        options.loadState(KiloRoutingOptions.State(enabled = true, providers = mutableListOf(" ", "x", "x")))

        assertTrue(options.enabled)
        assertEquals(listOf("x"), options.providers)
    }

    fun `test the service instance carries update and routesEnabled`() {
        val options = KiloRoutingOptions.getInstance()

        options.update(enabled = true, providers = listOf("openrouter"))
        assertTrue(options.enabled)
        assertTrue(options.routesEnabled("openrouter"))
        assertEquals(listOf("openrouter"), options.providers)
    }
}
