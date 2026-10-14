package ai.kilocode.client.settings.appearance

import ai.kilocode.client.session.settings.ChatStyleSettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KiloAppearanceSettingsServiceTest : BasePlatformTestCase() {

    fun `test fresh service resolves to the default spec`() {
        val spec = KiloAppearanceSettingsService().spec()

        assertNull(spec.family)
        assertEquals(0f, spec.response)
        assertEquals(0f, spec.thinking)
        assertEquals(0f, spec.tool)
        assertEquals(0f, spec.prompt)
    }

    fun `test update stores spec and spec reads it back`() {
        val settings = KiloAppearanceSettingsService()
        settings.update(ChatFontSpec(family = "Monospaced", response = 1.5f, thinking = -2f, tool = 0.1f, prompt = 8f))

        val spec = settings.spec()

        assertEquals("Monospaced", spec.family)
        assertEquals(1.5f, spec.response)
        assertEquals(-2f, spec.thinking)
        assertEquals(0.1f, spec.tool)
        assertEquals(8f, spec.prompt)
    }

    fun `test update clamps out-of-range offsets`() {
        val settings = KiloAppearanceSettingsService()
        settings.update(ChatFontSpec(response = 99f, thinking = -99f))

        val spec = settings.spec()

        assertEquals(ChatFontOffset.MAX.toFloat(), spec.response)
        assertEquals(ChatFontOffset.MIN.toFloat(), spec.thinking)
    }

    fun `test blank family resolves to null`() {
        val settings = KiloAppearanceSettingsService()
        settings.update(ChatFontSpec(family = "   "))

        assertNull(settings.spec().family)
    }

    fun `test state round-trip through persistence`() {
        val settings = KiloAppearanceSettingsService()
        settings.update(ChatFontSpec(family = "Monospaced", response = 1.5f, thinking = -2f, tool = 0.1f, prompt = 8f))

        val reloaded = KiloAppearanceSettingsService()
        reloaded.loadState(settings.state)

        val spec = reloaded.spec()
        assertEquals("Monospaced", spec.family)
        assertEquals(1.5f, spec.response)
        assertEquals(-2f, spec.thinking)
        assertEquals(0.1f, spec.tool)
        assertEquals(8f, spec.prompt)
    }

    fun `test publish notifies chat style listeners`() {
        val settings = KiloAppearanceSettingsService()
        var fired = 0
        val bus = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        bus.subscribe(ChatStyleSettingsListener.TOPIC, ChatStyleSettingsListener { fired++ })

        settings.publish()

        assertEquals(1, fired)
    }
}
