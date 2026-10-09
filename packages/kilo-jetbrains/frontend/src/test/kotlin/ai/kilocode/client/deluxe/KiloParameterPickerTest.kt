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

    fun `test starred keys derive from the visible presets`() {
        val p = KiloParameterPicker()
        p.presets = {
            listOf(
                KiloFavoritePreset(id = "a", name = "A", labels = listOf("p"), provider = "openrouter", modelId = "z-ai/glm-5.3-flash"),
                KiloFavoritePreset(id = "b", name = "B", labels = listOf("p"), provider = "openai", modelId = "gpt-5"),
            )
        }

        assertEquals(setOf("openrouter/z-ai/glm-5.3-flash", "openai/gpt-5"), p.starredKeysForTest())
    }

    fun `test cycle prefers visible presets in label order`() {
        val p = KiloParameterPicker()
        val fast = KiloFavoritePreset(
            id = "fast", name = "Fast", labels = listOf("p"),
            provider = "openrouter", modelId = "z-ai/glm-5.3-flash", routeTag = "parasail", variant = "high",
        )
        val plain = KiloFavoritePreset(
            id = "plain", name = "Plain", labels = listOf("p"),
            provider = "openai", modelId = "gpt-5",
        )
        p.presets = { listOf(fast, plain) }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash"), item("gpt-5")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = "parasail",
            variants = listOf("high"),
            variant = "high",
            frozen = false,
        )
        var picked: KiloFavoritePreset? = null
        p.onPreset = { picked = it }

        p.cycle()

        assertEquals("The next preset in the pool, not the active one", "Plain", picked?.name)
        assertEquals(true, p.canCycle())
    }

    fun `test cycle applies a preset when the session drifted from the trio`() {
        val p = KiloParameterPicker()
        val fast = KiloFavoritePreset(
            id = "fast", name = "Fast", labels = listOf("p"),
            provider = "openrouter", modelId = "z-ai/glm-5.3-flash", routeTag = "parasail",
        )
        p.presets = { listOf(fast) }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = false,
        )
        var picked: KiloFavoritePreset? = null
        p.onPreset = { picked = it }

        p.cycle()

        assertEquals("A preset matching nothing still cycles once", "Fast", picked?.name)
    }

    fun `test cycle falls back to flat favorites when the project has no presets`() {
        val p = KiloParameterPicker()
        p.favorites = { listOf(ModelSelectionDto("openai", "gpt-5"), ModelSelectionDto("anthropic", "claude")) }
        p.sync(
            items = listOf(item("gpt-5"), item("claude", provider = "anthropic")),
            selected = "openai/gpt-5",
            route = null,
            variants = emptyList(),
            variant = null,
            frozen = false,
        )
        var picked: ModelPicker.Item? = null
        p.onModel = { picked = it }

        p.cycle()

        assertEquals("anthropic/claude", picked?.key)
    }

    fun `test a single preset matching the session makes cycle idle`() {
        val p = KiloParameterPicker()
        val fast = KiloFavoritePreset(
            id = "fast", name = "Fast", labels = listOf("p"),
            provider = "openrouter", modelId = "z-ai/glm-5.3-flash", routeTag = "parasail", variant = "high",
        )
        p.presets = { listOf(fast) }
        p.sync(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            route = "parasail",
            variants = listOf("high"),
            variant = "high",
            frozen = false,
        )

        assertFalse(p.canCycle())
    }
}
