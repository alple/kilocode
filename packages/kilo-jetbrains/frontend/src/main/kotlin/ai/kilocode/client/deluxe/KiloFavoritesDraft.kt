package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle

/**
 * The management window's edit buffer (ADR-0003): copies of the store's presets plus the pending
 * label-order edits, written through to [KiloFavoritePresetStore] only on apply — OK and Apply
 * commit, Cancel discards. All list semantics (groups, drag drop resolution, per-label ordering,
 * filtering) live here so they are testable without Swing.
 */
internal class KiloFavoritesDraft(private val store: KiloFavoritePresetStore) {

    /** One row of the left pane: a label group header or a preset under it. */
    sealed interface Row {
        data class Group(val label: String?) : Row

        data class Entry(val id: String) : Row
    }

    private val presets = store.all().map(KiloFavoritePreset::copy).toMutableList()
    private val orders = store.getState().order.map { KiloPresetLabelOrder(it.label, it.ids.toList()) }.toMutableList()
    private var applied = snapshot()

    fun preset(id: String?): KiloFavoritePreset? = id?.let { wanted -> presets.firstOrNull { it.id == wanted } }

    /** All label spellings carried by any preset — the chip editor's autocomplete source. */
    fun labels(): List<String> = (presets.flatMapTo(linkedSetOf()) { it.labels } + orders.mapTo(linkedSetOf()) { it.label }).toList()

    fun orderOf(label: String): List<String> = entry(label)?.ids ?: emptyList()

    /**
     * Left-pane rows for [query]: labeled groups in order-entry order, then unlabeled last; empty
     * groups collapse away. The filter matches a preset's name, model, provider or labels, and a
     * label match keeps that group's whole membership.
     */
    fun rows(query: String): List<Row> {
        val q = query.trim().lowercase()
        val out = mutableListOf<Row>()
        for (label in groupLabels()) {
            val members = groupOf(label)
            if (members.isEmpty()) continue
            val keep = q.isEmpty() || label.lowercase().contains(q)
            val shown = if (keep) members else members.filter { matches(q, it) }
            if (shown.isNotEmpty()) {
                out += Row.Group(label)
                shown.forEach { out += Row.Entry(it) }
            }
        }
        val loose = groupOf(null)
        if (loose.isNotEmpty()) {
            val shown = if (q.isEmpty()) loose else loose.filter { matches(q, it) }
            if (shown.isNotEmpty()) {
                out += Row.Group(null)
                shown.forEach { out += Row.Entry(it) }
            }
        }
        return out
    }

    fun add(name: String): String {
        val id = newId()
        presets += KiloFavoritePreset(id = id, name = uniqueName(name, null), labels = emptyList())
        return id
    }

    /** The toolbar's only independent fork: "name copy", same trio, no labels (ADR-0003). */
    fun duplicate(id: String): String? {
        val from = preset(id) ?: return null
        val copy = KiloFavoritePreset(
            id = newId(),
            name = uniqueName(KiloBundle.message("favorites.window.duplicate.copy", from.name), null),
            labels = emptyList(),
            provider = from.provider,
            modelId = from.modelId,
            routeTag = from.routeTag,
            variant = from.variant,
        )
        presets += copy
        return copy.id
    }

    fun remove(id: String) {
        presets.removeAll { it.id == id }
        for (index in orders.indices) orders[index] = orders[index].copy(ids = orders[index].ids - id)
    }

    fun update(id: String, name: String, provider: String, modelId: String, routeTag: String?, variant: String?) {
        val at = presets.indexOfFirst { it.id == id }
        if (at < 0) return
        presets[at] = presets[at].copy(
            name = uniqueName(name, id),
            provider = provider.trim(),
            modelId = modelId.trim(),
            routeTag = routeTag?.trim()?.takeIf { it.isNotEmpty() },
            variant = variant?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Replaces a preset's labels. Labels it drops disappear from that label's order; labels it
     * gains join their group's order at the end (a following [move] fixes the position).
     */
    fun setLabels(id: String, labels: List<String>) {
        val at = presets.indexOfFirst { it.id == id }
        if (at < 0) return
        val clean = KiloFavoritePresetStore.cleanLabels(labels)
        val old = presets[at].labels
        presets[at] = presets[at].copy(labels = clean)
        val dropped = old.filter { label -> clean.none { it.equals(label, ignoreCase = true) } }
        for (index in orders.indices) {
            val entry = orders[index]
            val keeps = clean.any { it.equals(entry.label, ignoreCase = true) }
            val ids = when {
                keeps -> entry.ids
                dropped.any { it.equals(entry.label, ignoreCase = true) } -> entry.ids - id
                else -> entry.ids
            }
            orders[index] = entry.copy(ids = ids)
        }
        val added = clean.filter { label -> old.none { it.equals(label, ignoreCase = true) } }
        for (label in added) {
            val existing = entry(label) ?: KiloPresetLabelOrder(label, emptyList()).also { orders += it }
            if (existing.ids.none { it == id }) {
                val at2 = orders.indexOfFirst { it === existing || it.label == existing.label }
                orders[at2] = existing.copy(ids = existing.ids + id)
            }
        }
    }

    /**
     * The drag drop (ADR-0003): dropping preset [id] into group [label] at [index]. Dropping
     * within the preset's own group reorders that label's sequence; dropping into another group
     * adds that label at the drop position. Drops on the unlabeled group are refused — labels are
     * removed through the editor's chips, never by drag.
     */
    fun move(id: String, label: String?, index: Int) {
        label ?: return
        val target = label.trim().takeIf { it.isNotEmpty() } ?: return
        val members = groupOf(target).toMutableList()
        if (members.none { it == id }) {
            val spelling = entry(target)?.label ?: target
            setLabels(id, (preset(id)?.labels ?: emptyList()) + spelling)
        } else {
            members.remove(id)
        }
        members.add(index.coerceIn(0, members.size), id)
        putOrder(entry(target)?.label ?: target, members)
    }

    /** "Order alphabetically" for the viewed label; the unlabeled group has no order to store. */
    fun orderAlphabetically(label: String?) {
        label ?: return
        val members = groupOf(label)
        if (members.isEmpty()) return
        val sorted = members.mapNotNull(::preset).sortedBy { it.name.lowercase() }.map { it.id }
        putOrder(entry(label)?.label ?: label, sorted)
    }

    /** A preset the store would refuse, with the bundle message to show. */
    data class Violation(val id: String, val message: String)

    /** First validation failure across the draft, or null when everything is appliable. */
    fun validate(): Violation? {
        presets.firstOrNull { it.name.isBlank() }?.let { return Violation(it.id, KiloBundle.message("favorites.window.error.name")) }
        presets.firstOrNull { it.provider.isBlank() || it.modelId.isBlank() }?.let {
            return Violation(it.id, KiloBundle.message("favorites.window.error.model"))
        }
        return null
    }

    /** Writes the whole draft through to the store and re-reads it (created ids change). */
    fun apply() {
        val known = store.all().mapTo(mutableSetOf()) { it.id }
        for (id in applied.ids) if (presets.none { it.id == id }) store.remove(id)
        for (value in presets) {
            when {
                value.id in known -> store.edit(value.id, value.name, value.provider, value.modelId, value.routeTag, value.variant)
                value.provider.isNotBlank() && value.modelId.isNotBlank() ->
                    store.create(value.name, value.labels, value.provider, value.modelId, value.routeTag, value.variant)
            }
        }
        for (value in presets) store.setLabels(value.id, value.labels)
        for (entry in orders) if (entry.ids.isNotEmpty()) store.reorder(entry.label, entry.ids)
        reload()
    }

    fun isModified(): Boolean = snapshot() != applied

    /** Group ids in display order: the label's order sequence, then unordered members trailing. */
    internal fun groupOf(label: String?): List<String> {
        val members = when (label) {
            null -> presets.filter { it.labels.isEmpty() }.map { it.id }
            else -> {
                val target = label.lowercase()
                presets.filter { preset -> preset.labels.any { it.lowercase() == target } }.map { it.id }
            }
        }
        if (label == null) return members
        val ordered = entry(label)?.ids ?: return members
        return ordered.filter(members::contains) + members.filter { it !in ordered }
    }

    private fun matches(q: String, id: String): Boolean {
        val value = preset(id) ?: return false
        return listOf(value.name, value.modelId, value.provider, value.labels.joinToString(" "))
            .any { it.lowercase().contains(q) }
    }

    /** Group labels in display order: order entries first, then preset-only labels, creation order. */
    private fun groupLabels(): List<String> {
        val out = mutableListOf<String>()
        for (entry in orders) {
            if (out.none { it.equals(entry.label, ignoreCase = true) }) out += entry.label
        }
        for (value in presets) {
            for (label in value.labels) {
                if (out.none { it.equals(label, ignoreCase = true) }) out += label
            }
        }
        return out
    }

    private fun entry(label: String): KiloPresetLabelOrder? = orders.firstOrNull { it.label.equals(label, ignoreCase = true) }

    private fun putOrder(label: String, ids: List<String>) {
        val at = orders.indexOfFirst { it.label.equals(label, ignoreCase = true) }
        if (at >= 0) orders[at] = orders[at].copy(ids = ids) else orders += KiloPresetLabelOrder(label, ids)
    }

    private fun reload() {
        presets.clear()
        presets += store.all().map(KiloFavoritePreset::copy)
        orders.clear()
        orders += store.getState().order.map { KiloPresetLabelOrder(it.label, it.ids.toList()) }
        applied = snapshot()
    }

    private fun snapshot() = Snapshot(presets.toList(), orders.toList())

    private data class Snapshot(val presets: List<KiloFavoritePreset>, val orders: List<KiloPresetLabelOrder>) {
        val ids: List<String> get() = presets.map { it.id }
    }

    private fun newId(): String = "draft-${java.util.UUID.randomUUID()}"

    /** [desired] made unique against the draft's other presets, suffixing " 2", " 3"… on conflict. */
    private fun uniqueName(desired: String, skip: String?): String {
        val taken = { candidate: String ->
            presets.any { it.id != skip && it.name.equals(candidate, ignoreCase = true) }
        }
        var stem = desired.trim().ifEmpty { KiloBundle.message("param.picker.preset.untitled") }
        if (taken(stem)) {
            var index = 2
            while (taken("$stem $index")) index++
            stem = "$stem $index"
        }
        return stem
    }

}
