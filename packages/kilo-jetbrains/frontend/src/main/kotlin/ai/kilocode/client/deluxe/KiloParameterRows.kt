package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelPickerRow
import ai.kilocode.client.session.ui.model.modelPickerRows
import ai.kilocode.rpc.dto.ModelSelectionDto
import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * OpenRouter `author/slug` pair for a route-capable model id (`z-ai/glm-5.3-flash`), or null when
 * the id has no author part. The CLI route-list endpoint is keyed by exactly these path segments.
 */
internal fun routeAuthorSlug(modelId: String): Pair<String, String>? {
    val cut = modelId.indexOf('/')
    if (cut <= 0 || cut == modelId.lastIndex) return null
    return modelId.take(cut) to modelId.substring(cut + 1)
}

/** Route-list fetch outcome for the selected model. */
internal sealed interface KiloRouteState {
    /** A request is in flight; the route section shows a loading note. */
    data object Loading : KiloRouteState {
        override fun toString(): String = "Loading"
    }

    /** The CLI answered; unknown models (404) surface as an empty list. */
    data class Ready(val endpoints: List<RouteEndpointDto>) : KiloRouteState

    /** The lookup failed — the pin stays active, only the fetched list is missing. */
    data class Failed(val reason: String? = null) : KiloRouteState
}

/**
 * One row of the session parameter popup: the unlock action, a model pick, a route pick (Auto =
 * no pin), a reasoning-effort pick (Auto = provider default), or a non-pickable notice row. Every
 * row carries its section label; a header renders above a section's first visible row only.
 */
internal sealed interface KiloParamRow {
    val section: String?

    /** Unlock action shown first while the model/route/effort trio is frozen. */
    data object Lock : KiloParamRow {
        override val section: String? = null
        override fun toString(): String = "Lock"
    }

    data class Model(val row: ModelPickerRow) : KiloParamRow {
        override val section: String? get() = row.section
    }

    /** [tag] null = Auto (no pin); the pin value is the OpenRouter routing tag. */
    data class Route(val tag: String?, val endpoint: RouteEndpointDto?, override val section: String?) : KiloParamRow

    /** [id] null = Auto (provider default effort). */
    data class Effort(val id: String?, override val section: String?) : KiloParamRow

    /** Non-pickable informational row: loading, unavailable, or stale-pin recovery notice. */
    data class Note(val text: String, val warn: Boolean, override val section: String?) : KiloParamRow
}

/** Stable per-row key so popup refresh keeps the right row scrolled/selected across rebuilds. */
internal fun kiloParamKey(row: KiloParamRow): String = when (row) {
    is KiloParamRow.Lock -> "lock"
    is KiloParamRow.Model -> "m:${row.row.key ?: "unset"}"
    is KiloParamRow.Route -> "r:${row.tag ?: "auto"}"
    is KiloParamRow.Effort -> "v:${row.id ?: "auto"}"
    is KiloParamRow.Note -> "n:${row.text}"
}

/** Header for the section [row] starts: rendered above a section's first row only. */
internal fun kiloParameterSectionTitle(rows: List<KiloParamRow>, index: Int): String? {
    val row = rows.getOrNull(index) ?: return null
    val section = row.section ?: return null
    val prev = rows.getOrNull(index - 1)
    return if (prev?.section != section) section else null
}

/**
 * Popup rows for the current parameter state: the unlock action while frozen, model rows
 * (shared with the upstream picker's row builder, so favorites and ordering match), the route
 * section when routing applies, and the effort section when the model carries variants. Search
 * narrows to the model section only — routes and efforts are short and live off the selection.
 */
internal fun kiloParameterRows(
    items: List<ModelPicker.Item>,
    selected: String?,
    favorites: List<ModelSelectionDto>,
    variants: List<String>,
    variant: String?,
    route: String?,
    frozen: Boolean,
    routes: KiloRouteState?,
    query: String,
): List<KiloParamRow> {
    val out = mutableListOf<KiloParamRow>()
    if (frozen) out += KiloParamRow.Lock
    modelPickerRows(items, favorites, query).forEach { out += KiloParamRow.Model(it) }
    if (query.isNotBlank()) return out
    routeSectionRows(route, routes)?.let { out += it }
    if (variants.isNotEmpty()) out += effortSectionRows(variants)
    return out
}

/**
 * Route section rows, or null when routing does not apply to the selected model (option off,
 * provider not route-capable, or no `author/slug`). Auto stays first; the fetched endpoints follow,
 * and a pinned tag missing from the refreshed list renders a recovery note — the pin is never
 * auto-cleared (stays active until the user explicitly re-picks).
 */
internal fun routeSectionRows(route: String?, state: KiloRouteState?): List<KiloParamRow>? {
    val routeSection = KiloBundle.message("param.picker.route.section")
    return when (state) {
        null -> null
        KiloRouteState.Loading -> listOf(
            KiloParamRow.Note(KiloBundle.message("param.picker.routes.loading"), warn = false, section = routeSection),
        )
        is KiloRouteState.Failed -> listOf(
            KiloParamRow.Note(KiloBundle.message("param.picker.routes.unavailable"), warn = true, section = routeSection),
        )
        is KiloRouteState.Ready -> buildList {
            add(KiloParamRow.Route(null, null, routeSection))
            for (endpoint in state.endpoints) {
                val tag = endpoint.tag ?: continue
                add(KiloParamRow.Route(tag, endpoint, routeSection))
            }
            if (route != null && state.endpoints.none { it.tag == route }) {
                add(KiloParamRow.Note(
                    KiloBundle.message("param.picker.stale", route),
                    warn = true,
                    section = routeSection,
                ))
            }
        }
    }
}

private fun effortSectionRows(variants: List<String>): List<KiloParamRow> {
    val section = KiloBundle.message("param.picker.effort.section")
    return buildList {
        add(KiloParamRow.Effort(null, section))
        variants.forEach { add(KiloParamRow.Effort(it, section)) }
    }
}

/**
 * Popup row visibility in one check: the route section renders only when the selected model's
 * provider is enabled in the "enable model routing" option and its id yields an `author/slug`
 * pair for the CLI route-list path.
 */
internal fun routesApply(routingEnabled: (String) -> Boolean, item: ModelPicker.Item?): Boolean {
    item ?: return false
    if (!routingEnabled(item.provider)) return false
    return routeAuthorSlug(item.id) != null
}

/** Row text helpers for route rows; all inputs are nullable and tolerate sparse endpoint rows. */
internal object KiloRouteText {
    /** Per-Mtok pricing summary, e.g. `in $2.50 · out $10.00`; null when no prices ride the endpoint. */
    fun pricing(pricing: RoutePricingDto?): String? {
        val input = pricing?.prompt
        val output = pricing?.completion
        if (input == null && output == null) return null
        val parts = buildList {
            input?.let { add("in ${money(it)}") }
            output?.let { add("out ${money(it)}") }
        }
        return parts.joinToString(" · ")
    }

    /** Uptime preference: 30 minutes, falling back to the daily window. Ratio 0..1. */
    fun uptime(uptime: RouteUptimeDto?): String? {
        val ratio = uptime?.last30m ?: uptime?.last1d ?: return null
        return "${(ratio * 100).roundToInt()}%"
    }

    fun quantization(endpoint: RouteEndpointDto?): String? = endpoint?.quantization?.takeIf { it.isNotBlank() }

    fun degraded(status: Int?): Boolean = status != null && status != 0

    private fun money(perToken: Double): String = String.format(Locale.ROOT, "$%.2f", perToken * 1e6)
}
