package ai.kilocode.client.deluxe

import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Route data helpers shared by the Deluxe surfaces (ADR-0003): the popup only *shows* preset route
 * summaries and performs no fetching — the route list itself belongs to the favorites management
 * window, which reuses these helpers with the CLI route-list RPC.
 */
internal fun routeAuthorSlug(modelId: String): Pair<String, String>? {
    val cut = modelId.indexOf('/')
    if (cut <= 0 || cut == modelId.lastIndex) return null
    return modelId.take(cut) to modelId.substring(cut + 1)
}

/** Route-list fetch outcome for one model. */
internal sealed interface KiloRouteState {
    /** A request is in flight; the caller shows a loading note. */
    data object Loading : KiloRouteState {
        override fun toString(): String = "Loading"
    }

    /** The CLI answered; unknown models (404) surface as an empty list. */
    data class Ready(val endpoints: List<RouteEndpointDto>) : KiloRouteState

    /** The lookup failed — the pin stays active, only the fetched list is missing. */
    data class Failed(val reason: String? = null) : KiloRouteState
}

/** Whether route editing applies to [item]: option on, provider listed, `author/slug` shape. */
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
