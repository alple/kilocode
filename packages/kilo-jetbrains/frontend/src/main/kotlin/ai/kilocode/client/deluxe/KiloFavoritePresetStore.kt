package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.util.UUID

/**
 * A favorite as a labeled route preset (ADR-0003): a named combination of provider + model +
 * OpenRouter route ([routeTag] null = Auto) + reasoning effort ([variant] null = Auto), carrying
 * user-assigned [labels]. One preset, N labels — the labels decide which project popups show it.
 */
data class KiloFavoritePreset(
    var id: String = "",
    var name: String = "",
    var labels: List<String> = emptyList(),
    var provider: String = "",
    var modelId: String = "",
    var routeTag: String? = null,
    var variant: String? = null,
)

/** Per-label preset order: the sequence [label]'s project popup shows its presets in. */
data class KiloPresetLabelOrder(
    var label: String = "",
    var ids: List<String> = emptyList(),
)

/**
 * IDE-global favorite-preset store (ADR-0003): an application-level [PersistentStateComponent] in
 * its own config-dir file with default roaming, so presets survive IntelliJ version updates and
 * Settings Sync. Deliberately JetBrains-only — the CLI, `model.json`, and the shared provider-state
 * DTOs stay untouched (ADR-0002's favorites carve-out keeps flat `model.json` favorites for the TUI
 * and the upstream pickers; this store is the Deluxe session surface's own data).
 *
 * Visibility: a preset shows in project P's popup iff one of its labels equals P's name
 * (case-insensitive); unlabeled presets are visible nowhere. Per-label order maps drive each
 * project's popup order — ordering is per label, never global.
 */
@Service(Service.Level.APP)
@State(
    name = "KiloFavoritePresets",
    storages = [Storage("kiloFavoritePresets.xml")],
)
class KiloFavoritePresetStore : PersistentStateComponent<KiloFavoritePresetStore.State> {

    data class State(
        var presets: List<KiloFavoritePreset> = emptyList(),
        var order: List<KiloPresetLabelOrder> = emptyList(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        val (presets, order) = sanitize(state.presets, state.order)
        this.state = State(presets, order)
    }

    fun all(): List<KiloFavoritePreset> = state.presets

    fun get(id: String): KiloFavoritePreset? = state.presets.firstOrNull { it.id == id }

    /** Distinct label spellings as stored (visibility still matches case-insensitively). */
    fun labels(): List<String> = state.presets.flatMapTo(linkedSetOf()) { it.labels }.toList()

    /**
     * Presets visible in project [projectName], ordered by that label's order map (unordered
     * presets trail in creation order). Case-insensitive label match; blank names see nothing.
     */
    fun visibleIn(projectName: String?): List<KiloFavoritePreset> {
        val target = projectName?.trim().takeUnless { it.isNullOrEmpty() } ?: return emptyList()
        val matching = state.presets.filter { preset -> preset.labels.any { it.equals(target, ignoreCase = true) } }
        val ids = state.order.firstOrNull { it.label.equals(target, ignoreCase = true) }?.ids ?: return matching
        val byId = matching.associateBy { it.id }
        return ids.mapNotNull(byId::get) + matching.filter { preset -> ids.none { it == preset.id } }
    }

    /** Presets of one model visible in [projectName] — the star state for a catalog row. */
    fun visibleForModel(projectName: String?, provider: String, modelId: String): List<KiloFavoritePreset> =
        visibleIn(projectName).filter { it.provider == provider && it.modelId == modelId }

    /**
     * Adds a sanitized preset with a unique name (auto-suffix on conflict) and appends it to every
     * new label's order map. Returns the created preset, or null when [provider]/[modelId] are blank.
     */
    fun create(
        name: String,
        labels: List<String>,
        provider: String,
        modelId: String,
        routeTag: String?,
        variant: String?,
    ): KiloFavoritePreset? {
        val owner = provider.trim()
        val model = modelId.trim()
        if (owner.isEmpty() || model.isEmpty()) return null
        val preset = KiloFavoritePreset(
            id = UUID.randomUUID().toString(),
            name = uniqueName(name),
            labels = cleanLabels(labels),
            provider = owner,
            modelId = model,
            routeTag = routeTag?.trim()?.takeIf { it.isNotEmpty() },
            variant = variant?.trim()?.takeIf { it.isNotEmpty() },
        )
        state = State(state.presets + preset, withOrderFor(state.order, preset.labels, preset.id))
        return preset
    }

    fun remove(id: String) {
        state = State(
            presets = state.presets.filterNot { it.id == id },
            order = state.order.map { it.copy(ids = it.ids.filterNot { pid -> pid == id }) },
        )
    }

    /**
     * Replaces a preset's labels: the preset leaves the order entries of labels it no longer
     * carries and joins each new label's order at the end.
     */
    fun setLabels(id: String, labels: List<String>) {
        val preset = get(id) ?: return
        val clean = cleanLabels(labels)
        if (clean == preset.labels) return
        val kept = state.order.mapNotNull { entry ->
            if (clean.none { it.equals(entry.label, ignoreCase = true) }) {
                return@mapNotNull entry.copy(ids = entry.ids.filterNot { it == id }).takeIf { it.ids.isNotEmpty() }
            }
            entry
        }
        state = State(
            presets = state.presets.map { if (it.id == id) it.copy(labels = clean) else it },
            order = withOrderFor(kept, clean, id),
        )
    }

    /** Drops one label from a preset (case-insensitive match — the visibility rule). */
    fun removeLabel(id: String, label: String) {
        val preset = get(id) ?: return
        setLabels(id, preset.labels.filterNot { it.equals(label, ignoreCase = true) })
    }

    /** Stores [ids] as [label]'s popup order; unknown ids are ignored, missing presets trail. */
    fun reorder(label: String, ids: List<String>) {
        val key = label.trim().takeIf { it.isNotEmpty() } ?: return
        val known = ids.filter { id -> state.presets.any { it.id == id } }.distinct()
        val next = state.order.map { entry ->
            if (entry.label.equals(key, ignoreCase = true)) KiloPresetLabelOrder(entry.label, known) else entry
        }
        state = State(state.presets, next)
    }

    /**
     * The star on a catalog model row (ADR-0003): star = create a preset named [name] after the
     * model with the currently-in-effect route/effort, labeled with the project's name; unstar =
     * remove the project label from the model's visible presets. The presets survive (they stay
     * editable/deletable in the management window) and remain visible in every other labeled
     * project.
     */
    fun toggleModelStar(
        projectName: String,
        provider: String,
        modelId: String,
        name: String,
        routeTag: String?,
        variant: String?,
    ) {
        val project = projectName.trim().takeIf { it.isNotEmpty() } ?: return
        val visible = visibleForModel(project, provider, modelId)
        if (visible.isNotEmpty()) {
            visible.forEach { preset -> removeLabel(preset.id, project) }
            return
        }
        val fallback = "${provider.trim()}/${modelId.trim()}"
        create(name.trim().ifEmpty { fallback }, listOf(project), provider, modelId, routeTag, variant)
    }

    /** [desired] made unique against the store, auto-suffixing " 2", " 3"… on conflict. */
    fun uniqueName(desired: String): String {
        val base = desired.trim()
        val taken = { candidate: String -> state.presets.any { it.name.equals(candidate, ignoreCase = true) } }
        if (base.isNotEmpty() && !taken(base)) return base
        var stem = base.ifEmpty { KiloBundle.message("param.picker.preset.untitled") }
        if (taken(stem)) {
            var index = 2
            while (taken("$stem $index")) index++
            stem = "$stem $index"
        }
        return stem
    }

    /** [ids] appended to [labels]' order entries (existing casing kept), new entries at the end. */
    private fun withOrderFor(order: List<KiloPresetLabelOrder>, labels: List<String>, id: String): List<KiloPresetLabelOrder> {
        val out = order.toMutableList()
        for (label in labels) {
            val at = out.indexOfFirst { it.label.equals(label, ignoreCase = true) }
            if (at >= 0) out[at] = KiloPresetLabelOrder(out[at].label, out[at].ids + id)
            else out += KiloPresetLabelOrder(label, listOf(id))
        }
        return out
    }

    internal companion object {
        fun getInstance(): KiloFavoritePresetStore = service()

        /** Trimmed, non-blank, de-duplicated labels (case-insensitive dedupe keeps the first casing). */
        internal fun cleanLabels(labels: List<String>): List<String> {
            val out = mutableListOf<String>()
            for (raw in labels) {
                val label = raw.trim()
                if (label.isEmpty()) continue
                if (out.any { it.equals(label, ignoreCase = true) }) continue
                out += label
            }
            return out
        }

        /**
         * Load-time hygiene: drops entries with blank id/name/provider/model or duplicate ids,
         * repairs missing ids, suffixes conflicting names, trims labels and route/effort pins, and
         * prunes order entries to existing presets and labels.
         */
        internal fun sanitize(
            presets: List<KiloFavoritePreset>,
            order: List<KiloPresetLabelOrder>,
        ): Pair<List<KiloFavoritePreset>, List<KiloPresetLabelOrder>> {
            val seen = mutableSetOf<String>()
            val named = mutableListOf<KiloFavoritePreset>()
            for (raw in presets) {
                val provider = raw.provider.trim()
                val model = raw.modelId.trim()
                if (provider.isEmpty() || model.isEmpty() || raw.name.isBlank()) continue
                val id = raw.id.trim().ifEmpty { UUID.randomUUID().toString() }
                if (!seen.add(id)) continue
                var name = raw.name.trim()
                if (named.any { it.name.equals(name, ignoreCase = true) }) {
                    var index = 2
                    while (named.any { it.name.equals("${raw.name.trim()} $index", ignoreCase = true) }) index++
                    name = "${raw.name.trim()} $index"
                }
                named += raw.copy(
                    id = id,
                    name = name,
                    labels = cleanLabels(raw.labels),
                    provider = provider,
                    modelId = model,
                    routeTag = raw.routeTag?.trim()?.takeIf { it.isNotEmpty() },
                    variant = raw.variant?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            val byId = named.associateByTo(mutableMapOf()) { it.id }
            val knownLabels = named.flatMapTo(mutableSetOf()) { it.labels }
            val cleanOrder = order.mapNotNull { entry ->
                val label = entry.label.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val ids = entry.ids.filter { byId.containsKey(it) }.distinct()
                val known = knownLabels.any { it.equals(label, ignoreCase = true) }
                if (!known && ids.isEmpty()) return@mapNotNull null
                KiloPresetLabelOrder(label, ids)
            }
            return named to cleanOrder
        }
    }
}
