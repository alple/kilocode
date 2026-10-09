package ai.kilocode.client.deluxe

import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelPickerRow
import ai.kilocode.client.session.ui.model.ModelSearch
import ai.kilocode.client.session.ui.model.modelCycle
import ai.kilocode.client.session.ui.model.modelPickerRows
import ai.kilocode.rpc.dto.ModelSelectionDto

/**
 * One row of the Deluxe session popup (ADR-0003): the unlock action while the parameter trio is
 * frozen, a visible favorite preset (applies model + route + effort as one), a muted placeholder
 * when the project has no presets, or a catalog model row. Preset rows carry no section — the
 * pinned Favorites bar above the list is their header; catalog rows keep the upstream sections.
 */
internal sealed interface KiloModelRow {
    val section: String?

    /** Unlock action shown first while the model/route/effort trio is frozen. */
    data object Lock : KiloModelRow {
        override val section: String? = null
        override fun toString(): String = "Lock"
    }

    data class Preset(val preset: KiloFavoritePreset) : KiloModelRow {
        override val section: String? = null
        override fun toString(): String = "Preset ${preset.name}"
    }

    data class Empty(val text: String) : KiloModelRow {
        override val section: String? = null
        override fun toString(): String = "Empty $text"
    }

    data class Model(val row: ModelPickerRow) : KiloModelRow {
        override val section: String? get() = row.section
        override fun toString(): String = "Model ${row.key}"
    }
}

/** Stable per-row key so popup refresh keeps the right row scrolled/selected across rebuilds. */
internal fun kiloModelKey(row: KiloModelRow): String = when (row) {
    is KiloModelRow.Lock -> "lock"
    is KiloModelRow.Preset -> "p:${row.preset.id}"
    is KiloModelRow.Empty -> "e:${row.text}"
    is KiloModelRow.Model -> "m:${row.row.key ?: "unset"}"
}

/** Header for the section [row] starts: rendered above a section's first row only. */
internal fun kiloModelSectionTitle(rows: List<KiloModelRow>, index: Int): String? {
    val row = rows.getOrNull(index) ?: return null
    val section = row.section ?: return null
    val prev = rows.getOrNull(index - 1)
    return if (prev?.section != section) section else null
}

/**
 * Popup rows for the current state: the unlock row while frozen, the Favorites presets (one row per
 * visible preset, in the project label's order) plus the muted empty-state row when the project has
 * none, then the full catalog. The search narrows presets by name/model and the catalog by the
 * upstream model search; the empty-state row always reflects a project without presets, search or
 * not, so the pinned Favorites bar never dangles without content below it.
 */
internal fun kiloModelRows(
    presets: List<KiloFavoritePreset>,
    emptyText: String,
    items: List<ModelPicker.Item>,
    frozen: Boolean,
    query: String,
): List<KiloModelRow> {
    val out = mutableListOf<KiloModelRow>()
    if (frozen) out += KiloModelRow.Lock
    val q = query.trim()
    val matching = if (q.isEmpty()) {
        presets
    } else {
        presets.filter { preset ->
            ModelSearch.matches(q, preset.name) ||
                ModelSearch.matches(q, preset.modelId) ||
                ModelSearch.matches(q, preset.provider) ||
                ModelSearch.matches(q, "${preset.provider}/${preset.modelId}")
        }
    }
    out += matching.map { KiloModelRow.Preset(it) }
    if (presets.isEmpty()) out += KiloModelRow.Empty(emptyText)
    // The flat-favorites and empty rows stay upstream-only: this popup's Favorites section is the
    // preset list above, and the catalog shows every model.
    modelPickerRows(items, favorites = emptyList(), query = q, allowEmpty = false).forEach {
        out += KiloModelRow.Model(it)
    }
    return out
}

/**
 * Ctrl+2 cycle pool (ADR-0003): the project's visible presets in label order; when the project has
 * none, the flat `model.json` favorites in their order (the upstream fallback pool).
 */
internal fun kiloModelCyclePool(
    presets: List<KiloFavoritePreset>,
    items: List<ModelPicker.Item>,
    favorites: List<ModelSelectionDto>,
): List<KiloModelRow> {
    if (presets.isNotEmpty()) return presets.map { KiloModelRow.Preset(it) }
    return modelCycle(items, favorites).map { KiloModelRow.Model(ModelPickerRow(it, null, favorite = false)) }
}
