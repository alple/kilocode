package ai.kilocode.client.deluxe

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The Deluxe "enable model routing" option: on by default (KILO-8.5), the OpenRouter pair as the
 * default route-capable provider list, sanitized updates, and the [KiloRoutingOptions.routesEnabled]
 * gate. A stored off survives and hides all route UI.
 */
class KiloRoutingOptionsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            KiloRoutingOptions.getInstance().update(false, KiloRoutingOptions.DEFAULT_PROVIDERS)
        } finally {
            super.tearDown()
        }
    }

    fun `test defaults are on with the OpenRouter pair`() {
        val options = KiloRoutingOptions()

        assertTrue(options.enabled)
        assertEquals(KiloRoutingOptions.DEFAULT_PROVIDERS, options.providers)
        assertTrue(options.routesEnabled("openrouter"))
        assertTrue(options.routesEnabled("openrouter-private"))
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

    fun `test a stored off survives loadState`() {
        val options = KiloRoutingOptions()
        options.loadState(KiloRoutingOptions.State(enabled = false, providers = mutableListOf("openrouter")))

        assertFalse("An explicit opt-out must not be reset by the on default", options.enabled)
        assertFalse(options.routesEnabled("openrouter"))
    }

    fun `test the service instance carries update and routesEnabled`() {
        val options = KiloRoutingOptions.getInstance()

        options.update(enabled = true, providers = listOf("openrouter"))
        assertTrue(options.enabled)
        assertTrue(options.routesEnabled("openrouter"))
        assertEquals(listOf("openrouter"), options.providers)
    }
}
