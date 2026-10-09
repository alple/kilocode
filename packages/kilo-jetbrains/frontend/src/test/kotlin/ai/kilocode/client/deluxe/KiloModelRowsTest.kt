package ai.kilocode.client.deluxe

import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.ModelSelectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the Deluxe session popup rows (ADR-0003): the Favorites presets above the catalog, the
 * muted empty state, the unlock row while frozen, and the Ctrl+2 cycle pool.
 */
class KiloModelRowsTest {

    private fun item(
        id: String,
        provider: String = "openai",
        recommendedIndex: Double? = null,
    ) = ModelPicker.Item(id, id, provider, id, recommendedIndex = recommendedIndex)

    private fun preset(
        name: String,
        provider: String = "openrouter",
        modelId: String = "z-ai/glm-5.3-flash",
        routeTag: String? = null,
        variant: String? = null,
    ) = KiloFavoritePreset(
        id = name,
        name = name,
        labels = listOf("p"),
        provider = provider,
        modelId = modelId,
        routeTag = routeTag,
        variant = variant,
    )

    private val items = listOf(
        item("gpt-5"),
        item("claude", provider = "anthropic"),
        item("auto", provider = "kilo", recommendedIndex = 0.0),
    )

    @Test
    fun `favorites presets come first and the catalog keeps its sections`() {
        val rows = kiloModelRows(
            presets = listOf(preset("Fast", routeTag = "parasail", variant = "high"), preset("Cheap")),
            emptyText = "no favorites",
            items = items,
            frozen = false,
            query = "",
        )

        assertEquals(listOf("Fast", "Cheap"), rows.take(2).map { (it as KiloModelRow.Preset).preset.name })
        assertTrue(rows.drop(2).all { it is KiloModelRow.Model })
        assertEquals("Recommended", kiloModelSectionTitle(rows, 2))
        assertEquals(null, rows[0].section, "Preset rows carry no section header — the pinned bar is their header")
    }

    @Test
    fun `the muted empty state shows exactly when the project has no presets`() {
        val empty = kiloModelRows(presets = emptyList(), emptyText = "no favorites", items = items, frozen = false, query = "")
        assertEquals(listOf(KiloModelRow.Empty("no favorites")), empty.filterIsInstance<KiloModelRow.Empty>())

        val withPresets = kiloModelRows(presets = listOf(preset("Fast")), emptyText = "no favorites", items = items, frozen = false, query = "")
        assertTrue(withPresets.none { it is KiloModelRow.Empty })
    }

    @Test
    fun `the unlock row leads while frozen`() {
        val rows = kiloModelRows(presets = listOf(preset("Fast")), emptyText = "e", items = items, frozen = true, query = "")
        assertEquals(KiloModelRow.Lock, rows.first())
        assertEquals(
            listOf("Fast"),
            rows.drop(1).filterIsInstance<KiloModelRow.Preset>().map { it.preset.name },
        )
    }

    @Test
    fun `search narrows presets by name provider and model and hides the empty state`() {
        val rows = kiloModelRows(
            presets = listOf(preset("Fast"), preset("Cheap", provider = "openai", modelId = "gpt-5")),
            emptyText = "no favorites",
            items = items,
            frozen = false,
            query = "gpt",
        )

        assertEquals(listOf("Cheap"), rows.filterIsInstance<KiloModelRow.Preset>().map { it.preset.name })
        assertEquals(listOf("openai/gpt-5"), rows.filterIsInstance<KiloModelRow.Model>().map { it.row.key })
    }

    @Test
    fun `keys stay stable per row`() {
        val rows = kiloModelRows(presets = listOf(preset("Fast")), emptyText = "e", items = items, frozen = true, query = "")

        assertEquals(listOf("lock", "p:Fast"), rows.take(2).map(::kiloModelKey))
        assertTrue(rows.filterIsInstance<KiloModelRow.Model>().any { kiloModelKey(it) == "m:kilo/auto" })
        assertEquals(
            "Recommended",
            (rows.first { kiloModelKey(it) == "m:kilo/auto" } as KiloModelRow.Model).row.section,
            "Model rows keep their upstream section",
        )
    }

    @Test
    fun `cycle pool prefers visible presets and falls back to flat favorites`() {
        val pool = kiloModelCyclePool(listOf(preset("Fast"), preset("Cheap")), items, emptyList())
        assertEquals(2, pool.size)
        assertTrue(pool.all { it is KiloModelRow.Preset })

        val fallback = kiloModelCyclePool(
            emptyList(),
            items,
            listOf(ModelSelectionDto("anthropic", "claude")),
        )
        assertEquals(listOf("m:anthropic/claude"), fallback.map { kiloModelKey(it) })
        val fallbackRow = (fallback.single() as KiloModelRow.Model).row
        assertEquals(null, fallbackRow.section, "Fallback rows render without a section")
        assertEquals(false, fallbackRow.favorite, "Rows carry no favorite flag — the pinned bar does the labeling")
    }

    @Test
    fun `cycle pool without presets or favorites rides the recommended models`() {
        val pool = kiloModelCyclePool(emptyList(), items, emptyList())
        assertEquals("m:kilo/auto", kiloModelKey(pool.single()))
        assertEquals(
            0,
            kiloModelCyclePool(
                emptyList(),
                listOf(ModelPicker.Item("auto-small", "Auto Small", "kilo", "Kilo")),
                emptyList(),
            ).size,
            "Small models stay out of the cycle pool",
        )
    }

    @Test
    fun `model rows wrap the upstream row builder output`() {
        val rows = kiloModelRows(presets = emptyList(), emptyText = "e", items = items, frozen = false, query = "")
        val model = rows.filterIsInstance<KiloModelRow.Model>().map { it.row }
        assertEquals(
            listOf("kilo/auto", "openai/gpt-5", "anthropic/claude"),
            model.map { it.key },
            "Rows mirror modelPickerRows with the flat favorites section removed",
        )
        assertTrue(model.all { it.section != null })
        assertTrue(rows.none { (it as? KiloModelRow.Model)?.row?.isEmpty == true })
    }
}
