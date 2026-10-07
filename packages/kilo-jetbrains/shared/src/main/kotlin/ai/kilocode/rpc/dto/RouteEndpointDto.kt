package ai.kilocode.rpc.dto

import kotlinx.serialization.Serializable

/**
 * One routing endpoint for a model, as returned by the CLI route-list endpoint
 * (`GET /kilo/routes/{author}/{slug}` → OpenRouter endpoints data).
 *
 * Populated by the backend's [ai.kilocode.backend.cli.KiloCliDataParser.parseRoutes]; never
 * deserialized through a generated serializer (shared-DTO classloader rule).
 */
@Serializable
data class RouteEndpointDto(
    /** OpenRouter routing tag, e.g. `parasail`. Null when the row has no tag. */
    val tag: String? = null,
    val providerName: String? = null,
    val name: String? = null,
    val quantization: String? = null,
    /** 0 = healthy; anything else is an outage flag surfaced by the picker. */
    val status: Int? = null,
    val contextLength: Long? = null,
    val maxCompletionTokens: Long? = null,
    val uptime: RouteUptimeDto? = null,
    val pricing: RoutePricingDto? = null,
)

@Serializable
data class RouteUptimeDto(
    val last5m: Double? = null,
    val last30m: Double? = null,
    val last1d: Double? = null,
)

@Serializable
data class RoutePricingDto(
    val prompt: Double? = null,
    val completion: Double? = null,
    val inputCacheRead: Double? = null,
    val inputCacheWrite: Double? = null,
    val discount: Double? = null,
)
