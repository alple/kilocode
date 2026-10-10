package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiloFavoritesDraftTest {

    private fun store(vararg presets: KiloFavoritePreset, order: List<KiloPresetLabelOrder> = emptyList()) =
        KiloFavoritePresetStore().apply { loadState(KiloFavoritePresetStore.State(presets.toList(), order)) }

    private fun preset(
        id: String,
        name: String,
        labels: List<String>,
        provider: String = "openrouter",
        modelId: String = "acme/m1",
        routeTag: String? = null,
        variant: String? = null,
    ) = KiloFavoritePreset(id = id, name = name, labels = labels, provider = provider, modelId = modelId, routeTag = routeTag, variant = variant)

    @Test
    fun rowsGroupByLabelWithUnlabeledLast() {
        val draft = KiloFavoritesDraft(
            store(
                preset("a", "Alpha", listOf("Work")),
                preset("b", "Beta", listOf("Work", "Play")),
                preset("c", "Gamma", emptyList()),
                order = listOf(KiloPresetLabelOrder("Play", listOf("b")), KiloPresetLabelOrder("Work", listOf("a", "b"))),
            ),
        )
        assertEquals(
            listOf(
                KiloFavoritesDraft.Row.Group("Play"),
                KiloFavoritesDraft.Row.Entry("b"),
                KiloFavoritesDraft.Row.Group("Work"),
                KiloFavoritesDraft.Row.Entry("a"),
                KiloFavoritesDraft.Row.Entry("b"),
                KiloFavoritesDraft.Row.Group(null),
                KiloFavoritesDraft.Row.Entry("c"),
            ),
            draft.rows(""),
        )
    }

    @Test
    fun rowsFilterMatchesPresetOrWholeGroupOnLabel() {
        val draft = KiloFavoritesDraft(
            store(
                preset("a", "Ship it", listOf("Work")),
                preset("b", "Docs", listOf("Work")),
                order = listOf(KiloPresetLabelOrder("Work", listOf("a", "b"))),
            ),
        )
        assertEquals(listOf(KiloFavoritesDraft.Row.Group("Work"), KiloFavoritesDraft.Row.Entry("a")), draft.rows("ship"))
        assertEquals(
            listOf(
                KiloFavoritesDraft.Row.Group("Work"),
                KiloFavoritesDraft.Row.Entry("a"),
                KiloFavoritesDraft.Row.Entry("b"),
            ),
            draft.rows("work"),
        )
        assertEquals(emptyList(), draft.rows("nothing"))
    }

    @Test
    fun moveIntoAnotherGroupAddsLabelAtDropPosition() {
        val draft = KiloFavoritesDraft(
            store(
                preset("a", "Alpha", listOf("Work")),
                preset("b", "Beta", emptyList()),
                order = listOf(KiloPresetLabelOrder("Work", listOf("a"))),
            ),
        )
        draft.move("b", "Work", 0)
        assertEquals(listOf("b", "a"), draft.groupOf("Work"))
        assertEquals(listOf("Work"), draft.preset("b")?.labels)
        // The other label group still holds the preset untouched.
        assertFalse(draft.groupOf(null).contains("b"))
    }

    @Test
    fun moveWithinGroupReordersThatLabelOnly() {
        val draft = KiloFavoritesDraft(
            store(
                preset("a", "Alpha", listOf("Work", "Play")),
                preset("b", "Beta", listOf("Work", "Play")),
                order = listOf(KiloPresetLabelOrder("Work", listOf("a", "b")), KiloPresetLabelOrder("Play", listOf("a", "b"))),
            ),
        )
        draft.move("a", "Work", 1)
        assertEquals(listOf("b", "a"), draft.groupOf("Work"))
        assertEquals(listOf("a", "b"), draft.groupOf("Play"))
    }

    @Test
    fun moveOntoUnlabeledGroupIsRefused() {
        val draft = KiloFavoritesDraft(store(preset("a", "Alpha", listOf("Work"))))
        draft.move("a", null, 0)
        assertEquals(listOf("a"), draft.groupOf("Work"))
        assertTrue(draft.groupOf(null).isEmpty())
    }

    @Test
    fun orderAlphabeticallySortsOneLabel() {
        val draft = KiloFavoritesDraft(
            store(
                preset("a", "Zebra", listOf("Work")),
                preset("b", "apple", listOf("Work")),
                order = listOf(KiloPresetLabelOrder("Work", listOf("a", "b"))),
            ),
        )
        draft.orderAlphabetically("Work")
        assertEquals(listOf("b", "a"), draft.groupOf("Work"))
    }

    @Test
    fun duplicateForksNameTrioButNotLabels() {
        val draft = KiloFavoritesDraft(store(preset("a", "Ship", listOf("Work"), provider = "p", modelId = "m")))
        val copy = draft.duplicate("a")!!
        val value = draft.preset(copy)!!
        assertEquals("Ship copy", value.name)
        assertEquals(emptyList(), value.labels)
        assertEquals("p", value.provider)
        assertEquals("m", value.modelId)
        assertEquals("Ship copy 2", draft.preset(draft.duplicate("a")!!)!!.name)
    }

    @Test
    fun validateFlagsBlankTrio() {
        val draft = KiloFavoritesDraft(store(preset("a", "Alpha", emptyList())))
        assertNull(draft.validate())
        val id = draft.add("")
        assertEquals(KiloBundle.message("favorites.window.error.model"), draft.validate()?.message)
        assertEquals(id, draft.validate()?.id)
    }

    @Test
    fun applyWritesThroughAndIsModifiedTracks() {
        val raw = store(preset("a", "Alpha", listOf("Work"), routeTag = "parasail", variant = "high"))
        val draft = KiloFavoritesDraft(raw)
        assertFalse(draft.isModified())

        draft.update("a", "Renamed", "openrouter", "acme/m2", "parasail", "low")
        assertTrue(draft.isModified())
        assertEquals("Alpha", raw.get("a")!!.name) // cancel semantics: no write until apply

        draft.apply()
        assertFalse(draft.isModified())
        assertEquals("Renamed", raw.get("a")!!.name)
        assertEquals("acme/m2", raw.get("a")!!.modelId)
        assertEquals("low", raw.get("a")!!.variant)
        assertEquals(listOf("Work"), raw.get("a")!!.labels)
    }

    @Test
    fun applyCreatesRemovesAndOrders() {
        val raw = store(preset("a", "Alpha", listOf("Work")), order = listOf(KiloPresetLabelOrder("Work", listOf("a"))))
        val draft = KiloFavoritesDraft(raw)
        val added = draft.add("Fresh")
        draft.update(added, "Fresh", "openrouter", "acme/m1", null, null)
        draft.setLabels(added, listOf("Work"))
        draft.move(added, "Work", 0)
        draft.remove("a")

        draft.apply()
        assertNull(raw.get("a"))
        val made = raw.all().single()
        assertEquals("Fresh", made.name)
        assertEquals(listOf("Work"), made.labels)
        assertEquals(listOf(made.id), raw.visibleIn("Work").map { it.id })
        assertFalse(draft.isModified())
    }

    @Test
    fun applySuffixesNameConflicts() {
        val raw = store(preset("a", "Alpha", emptyList()))
        val draft = KiloFavoritesDraft(raw)
        draft.update("a", "alpha", "openrouter", "acme/m1", null, null)
        draft.apply()
        // Renaming to the preset's own name (any casing) never suffixes; it only re-cases.
        assertEquals("alpha", raw.get("a")!!.name)
    }

    @Test
    fun setLabelsKeepsOrdersCoherent() {
        val raw = store(
            preset("a", "Alpha", listOf("Work")),
            preset("b", "Beta", listOf("Work", "Play")),
            order = listOf(KiloPresetLabelOrder("Work", listOf("a", "b")), KiloPresetLabelOrder("Play", listOf("b"))),
        )
        val draft = KiloFavoritesDraft(raw)
        draft.setLabels("b", listOf("Play"))
        assertEquals(listOf("a"), draft.groupOf("Work"))
        assertEquals(listOf("b"), draft.groupOf("Play"))
    }
}
