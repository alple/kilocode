package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.ModelSelectionDto
import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The parameter picker button surface: the label composes model, route and effort, the lock icon
 * rides the frozen state, and a selection-less picker stays disabled. Popup internals are covered
 * by the rows and controller tests.
 */
class KiloParameterPickerTest : BasePlatformTestCase() {

    private fun item(id: String, provider: String = "openrouter") =
        ModelPicker.Item(id, id, provider, id)

    fun `test an empty picker stays disabled and blank`() {
        val p = KiloParameterPicker()
        p.sync(emptyList(), selected = null, route = null, variants = emptyList(), variant = null, frozen = false)

        assertFalse(p.isEnabled)
        assertEquals(" ", p.text)
        assertNull(p.icon)
    }

    fun `test the label composes model route and effort`() {
        val p = KiloParameterPicker()
        p.routesEnabled = { true }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = "parasail",
            variants = listOf("low", "high"),
            variant = "high",
            frozen = false,
        )

        assertTrue(p.isEnabled)
        assertTrue(
            "Model, route and effort all show in the label",
            p.text.contains("z-ai/glm-5.3-flash") && p.text.contains("parasail") && p.text.contains("high"),
        )
    }

    fun `test route shows Auto when unpinned and hides when routing does not apply`() {
        val p = KiloParameterPicker()
        p.routesEnabled = { true }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = false,
        )
        assertTrue(p.text.contains(KiloBundle.message("param.picker.auto")))

        p.routesEnabled = { false }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = "parasail",
            variants = emptyList(),
            variant = null,
            frozen = false,
        )
        assertFalse("A disabled option removes the route part from the label", p.text.contains("parasail"))
    }

    fun `test the frozen picker shows the lock icon`() {
        val p = KiloParameterPicker()
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = false,
        )
        assertNull(p.icon)

        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = true,
        )

        assertEquals(AllIcons.Nodes.Locked, p.icon)
        assertTrue(p.frozenForTest())
    }

    fun `test an unknown selection key resolves through the item id`() {
        val p = KiloParameterPicker()
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "z-ai/glm-5.3-flash",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = false,
        )

        assertTrue(p.isEnabled)
        assertEquals("The bare id resolves to the canonical item key", "openrouter/z-ai/glm-5.3-flash", p.selectionKeyForTest())
    }

    fun `test favorite keys derive from the favorites callback`() {
        val p = KiloParameterPicker()
        p.favorites = { listOf(ModelSelectionDto("openrouter", "z-ai/glm-5.3-flash")) }

        assertEquals(setOf("openrouter/z-ai/glm-5.3-flash"), p.favoriteKeysForTest())
    }
}
