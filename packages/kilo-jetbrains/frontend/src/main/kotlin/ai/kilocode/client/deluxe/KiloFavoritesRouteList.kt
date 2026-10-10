package ai.kilocode.client.deluxe

import ai.kilocode.rpc.dto.RouteEndpointDto

/** How the management window's route list orders its endpoints (ADR-0003). */
internal enum class KiloRouteSort { LISTED, PRICE, UPTIME }

/**
 * Route-list filter (ADR-0003): drop endpoints above [maxInput] per-1M input or below
 * [minUptime] availability. Null limits pass everything.
 */
internal data class KiloRouteFilter(val maxInput: Double? = null, val minUptime: Double? = null)

internal object KiloRouteList {
    /** Uptime preference: the 30-minute window, falling back to the daily one. Ratio 0..1. */
    fun uptime(endpoint: RouteEndpointDto): Double? = endpoint.uptime?.last30m ?: endpoint.uptime?.last1d

    /** Per-token input price as reported by the endpoint; null when unpriced. */
    fun input(endpoint: RouteEndpointDto): Double? = endpoint.pricing?.prompt

    /** [rows] ordered per [sort]; equal keys keep their incoming order. */
    fun sort(rows: List<RouteEndpointDto>, sort: KiloRouteSort): List<RouteEndpointDto> = when (sort) {
        KiloRouteSort.LISTED -> rows
        KiloRouteSort.PRICE -> rows.sortedWith(compareBy { input(it) ?: Double.MAX_VALUE })
        KiloRouteSort.UPTIME -> rows.sortedWith(compareByDescending { uptime(it) ?: -1.0 })
    }

    fun filter(rows: List<RouteEndpointDto>, filter: KiloRouteFilter): List<RouteEndpointDto> = rows.filter { row ->
        val price = input(row)
        if (filter.maxInput != null && (price == null || price > filter.maxInput)) return@filter false
        val up = uptime(row)
        if (filter.minUptime != null && (up == null || up < filter.minUptime)) return@filter false
        true
    }

    /** The cheapest endpoint among [rows] by input price; null when no row carries prices. */
    fun cheapest(rows: List<RouteEndpointDto>): RouteEndpointDto? {
        val priced = rows.mapNotNull { row -> input(row)?.let { it to row } }
        return priced.minByOrNull { it.first }?.second
    }
}
