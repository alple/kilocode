package ai.kilocode.client.deluxe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Covers the favorite-preset store (ADR-0003): label-based per-project visibility, per-label order,
 * unique names, load-time sanitation, and the star label-toggle. Production persists [KiloFavoritePresetStore.State]
 * to its own roaming config file; these tests exercise the same state contract the platform
 * serializes.
 */
class KiloFavoritePresetStoreTest {

    private fun preset(
        id: String,
        name: String,
        labels: List<String>,
        provider: String = "openrouter",
        modelId: String = "z-ai/glm-5.3-flash",
        routeTag: String? = null,
        variant: String? = null,
    ) = KiloFavoritePreset(id = id, name = name, labels = labels, provider = provider, modelId = modelId, routeTag = routeTag, variant = variant)

    @Test
    fun `state round trips through the platform state shape`() {
        val first = KiloFavoritePresetStore()
        first.create("Fast", listOf("alpha"), "openrouter", "z-ai/glm-5.3-flash", "parasail", "high")
        first.create("Slow", listOf("alpha", "beta"), "openai", "gpt-5", null, "low")

        val restored = KiloFavoritePresetStore()
        restored.loadState(first.getState())

        assertEquals(first.all().map { it.name }, restored.all().map { it.name })
        assertEquals(first.visibleIn("alpha").map { it.name }, restored.visibleIn("alpha").map { it.name })
        assertEquals(first.visibleIn("beta").map { it.name }, restored.visibleIn("beta").map { it.name })
    }

    @Test
    fun `a preset is visible exactly in the projects its labels name`() {
        val s = KiloFavoritePresetStore()
        s.create("Pair", listOf("Alpha", "Beta"), "openrouter", "z-ai/glm-5.3-flash", null, null)
        s.create("Hidden", emptyList(), "openrouter", "z-ai/glm-5.3-flash", null, null)
        s.create("Other", listOf("gamma"), "openai", "gpt-5", null, null)

        assertEquals(listOf("Pair"), s.visibleIn("alpha").map { it.name })
        assertEquals(listOf("Pair"), s.visibleIn("ALPHA").map { it.name }, "Case-insensitive project match")
        assertEquals(listOf("Pair"), s.visibleIn("beta").map { it.name })
        assertEquals(emptyList(), s.visibleIn("gamma").filter { it.name == "Hidden" }, "Unlabeled presets are visible nowhere")
        assertEquals(emptyList(), s.visibleIn("").map { it.name }, "Blank project names see nothing")
        assertEquals(emptyList(), s.visibleIn(null).map { it.name })
    }

    @Test
    fun `per-label order drives the popup order and unordered presets trail`() {
        val s = KiloFavoritePresetStore()
        val a = s.create("A", listOf("p"), "openrouter", "z-ai/glm-5.3-flash", null, null)!!
        val b = s.create("B", listOf("p"), "openrouter", "z-ai/glm-5.3-flash", null, null)!!
        val c = s.create("C", listOf("p"), "openrouter", "z-ai/glm-5.3-flash", null, null)!!

        assertEquals(listOf("A", "B", "C"), s.visibleIn("p").map { it.name }, "Creation order until reordered")
        s.reorder("p", listOf(c.id, a.id))
        assertEquals(listOf("C", "A", "B"), s.visibleIn("p").map { it.name })
        assertEquals(emptyList(), s.visibleIn("q"), "Other labels keep their own order")

        s.reorder("p", listOf(a.id, "unknown-id"))
        assertEquals(listOf("A", "B", "C"), s.visibleIn("p").map { it.name }, "Unknown ids are ignored, unordered presets trail")
    }

    @Test
    fun `names are unique with an auto suffix on conflict`() {
        val s = KiloFavoritePresetStore()
        s.create("GPT", listOf("p"), "openai", "gpt-5", null, null)

        assertEquals("gpt 2", s.uniqueName("gpt"))
        assertEquals("GPT 2", s.uniqueName("GPT 2"))
        assertEquals("Fresh", s.uniqueName("Fresh"))
        assertEquals("Untitled preset", s.uniqueName("   "))
    }

    @Test
    fun `create refuses unusable inputs and sanitizes the rest`() {
        val s = KiloFavoritePresetStore()
        assertNull(s.create("X", listOf("p"), "  ", "gpt-5", null, null), "Blank provider refused")
        assertNull(s.create("X", listOf("p"), "openai", "", null, null), "Blank model refused")

        val made = s.create("  Trimmed  ", listOf(" p ", "", "P", " q "), " openrouter ", " z-ai/glm-5.3-flash ", " parasail ", " high ")!!
        assertEquals("Trimmed", made.name)
        assertEquals(listOf("p", "q"), made.labels)
        assertEquals("openrouter", made.provider)
        assertEquals("z-ai/glm-5.3-flash", made.modelId)
        assertEquals("parasail", made.routeTag)
        assertEquals("high", made.variant)
        assertEquals(emptyList<KiloFavoritePreset>(), s.all().filter { it.routeTag?.isBlank() == true }, "Blank pins become Auto (null)")
    }

    @Test
    fun `sanitation on load drops broken entries and repairs the rest`() {
        val s = KiloFavoritePresetStore()
        s.loadState(
            KiloFavoritePresetStore.State(
                presets = listOf(
                    preset("p1", "Kept", listOf("alpha", " ", "Alpha")),
                    preset("p1", "Duplicate id dropped", listOf("alpha")),
                    preset("", "Repaired id", listOf("alpha")),
                    preset("p3", "   ", listOf("alpha")),
                    preset("p4", "No provider", listOf("alpha"), provider = " "),
                    preset("p5", "No model", listOf("alpha"), modelId = ""),
                    preset("p6", "Kept", listOf("alpha")),
                    preset("p7", "  padded  ", emptyList(), routeTag = "  ", variant = ""),
                ),
                order = listOf(
                    KiloPresetLabelOrder("alpha", listOf("p1", "ghost-id", "p7")),
                    KiloPresetLabelOrder("ghost-label", listOf("p1")),
                    KiloPresetLabelOrder("   ", emptyList()),
                ),
            ),
        )

        val all = s.all()
        assertEquals(listOf("Kept", "Repaired id", "Kept 2", "padded"), all.map { it.name })
        assertNotEquals("A missing id is repaired", "", all[1].id)
        assertEquals(listOf("alpha"), all[0].labels, "Labels are trimmed and case-deduped")
        assertEquals("padded", all[3].name, "Padded name is trimmed")
        assertNull(all[3].routeTag)
        assertNull(all[3].variant)
        assertEquals(
            listOf("Kept", "Repaired id", "Kept 2"),
            s.visibleIn("alpha").map { it.name },
            "Order prunes ghost ids; unlabeled presets stay invisible; unordered presets trail",
        )
        assertEquals(4, s.all().size, "The unlabeled preset survives in the store for the management window")
        assertEquals(2, s.getState().order.size, "Ghost labels with real ids survive as order-only entries")
    }

    @Test
    fun `remove and setLabels keep the order maps consistent`() {
        val s = KiloFavoritePresetStore()
        val a = s.create("A", listOf("p"), "openrouter", "z-ai/glm-5.3-flash", null, null)!!
        val b = s.create("B", listOf("p"), "openai", "gpt-5", null, null)!!

        s.remove(a.id)
        assertEquals(listOf("B"), s.visibleIn("p").map { it.name })

        s.setLabels(b.id, listOf("q"))
        assertEquals(emptyList(), s.visibleIn("p"), "Removing the last project label hides the preset")
        assertEquals(listOf("B"), s.visibleIn("q").map { it.name })
        assertEquals("q", s.labels().single())

        s.setLabels(b.id, listOf("p", "q"))
        assertEquals(listOf("B"), s.visibleIn("p").map { it.name })
    }

    @Test
    fun `test removeLabel matches the project name case-insensitively`() {
        val s = KiloFavoritePresetStore()
        val a = s.create("A", listOf("MyProject", "Other"), "openrouter", "z-ai/glm-5.3-flash", null, null)!!
        s.removeLabel(a.id, "myproject")
        assertEquals(listOf("Other"), s.get(a.id)!!.labels, "Case-insensitive match removes the project label")
        assertEquals("Other", s.labels().single())
    }

    @Test
    fun `starring a model creates a project-labeled preset with the current trio`() {
        val s = KiloFavoritePresetStore()
        s.toggleModelStar("Alpha", "openrouter", "z-ai/glm-5.3-flash", "GLM 5.3 Flash", "parasail", "high")

        val visible = s.visibleIn("alpha")
        assertEquals(1, visible.size)
        val made = visible.single()
        assertEquals("GLM 5.3 Flash", made.name, "Named after the model")
        assertEquals(listOf("Alpha"), made.labels)
        assertEquals("parasail", made.routeTag)
        assertEquals("high", made.variant)

        s.toggleModelStar("alpha", "openrouter", "z-ai/glm-5.3-flash", "GLM 5.3 Flash", "parasail", "high")
        assertEquals(emptyList<KiloFavoritePreset>(), s.visibleIn("alpha"), "The label-toggle unstars when a visible preset exists")
        assertEquals(1, s.all().size, "The preset survives unlabeled for the management window")
    }

    @Test
    fun `unstarring removes the project label from the model's visible presets`() {
        val s = KiloFavoritePresetStore()
        s.toggleModelStar("Alpha", "openrouter", "z-ai/glm-5.3-flash", "GLM", null, null)
        s.create("Custom", listOf("alpha", "beta"), "openrouter", "z-ai/glm-5.3-flash", "parasail", null)

        s.toggleModelStar("alpha", "openrouter", "z-ai/glm-5.3-flash", "GLM", null, null)
        assertEquals(emptyList(), s.visibleIn("alpha"), "Both visible presets lose the project label")
        val kept = s.all().filter { it.name == "Custom" }.single()
        assertEquals(listOf("beta"), kept.labels, "Other labels survive the unstar")
        assertEquals("parasail", kept.routeTag, "The preset itself survives for the management window")
    }

    @Test
    fun `starring without a name falls back to the provider-model id`() {
        val s = KiloFavoritePresetStore()
        s.toggleModelStar("p", "openrouter", "z-ai/glm-5.3-flash", "  ", null, null)
        assertEquals("openrouter/z-ai/glm-5.3-flash", s.visibleIn("p").single().name)
    }

    @Test
    fun `repeated star cycles do not collide on names`() {
        val s = KiloFavoritePresetStore()
        s.toggleModelStar("p", "openai", "gpt-5", "GPT", null, null)
        s.toggleModelStar("p", "openai", "gpt-5", "GPT", "parasail", null)
        s.toggleModelStar("p", "openai", "gpt-5", "GPT", "parasail", "high")

        assertEquals(1, s.visibleIn("p").size)

        s.toggleModelStar("p", "openai", "gpt-5", "GPT", null, null)
        s.toggleModelStar("p", "openai", "gpt-5", "GPT", null, null)
        assertEquals(1, s.visibleIn("p").size, "Re-creating after unstar suffixes instead of clashing")
        assertEquals("GPT 3", s.visibleIn("p").single().name)
        assertEquals(3, s.all().size, "Each star cycle leaves its preset for the management window")
    }
}
