package ai.kilocode.client.deluxe

import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KiloFavoritesRouteListTest {

    private fun endpoint(tag: String?, input: Double? = null, uptime: Double? = null) = RouteEndpointDto(
        tag = tag,
        providerName = "p-$tag",
        quantization = "fp8",
        status = 0,
        uptime = uptime?.let { RouteUptimeDto(last30m = it) },
        pricing = input?.let { RoutePricingDto(prompt = it, completion = 2.0) },
    )

    private val rows = listOf(endpoint("a", 2.0, 0.98), endpoint("b", 0.5, 0.90), endpoint("c", null, 0.99))

    @Test
    fun sortKeepsListedOrder() {
        assertEquals(rows, KiloRouteList.sort(rows, KiloRouteSort.LISTED))
    }

    @Test
    fun sortByPricePutsUnpricedLast() {
        assertEquals(listOf("b", "a", "c"), KiloRouteList.sort(rows, KiloRouteSort.PRICE).map { it.tag })
    }

    @Test
    fun sortByUptimePutsUnknownLast() {
        assertEquals(listOf("c", "a", "b"), KiloRouteList.sort(rows, KiloRouteSort.UPTIME).map { it.tag })
    }

    @Test
    fun filterDropsOverPriceAndUnderUptime() {
        assertEquals(listOf("a", "b", "c"), KiloRouteList.filter(rows, KiloRouteFilter()).map { it.tag })
        assertEquals(listOf("b"), KiloRouteList.filter(rows, KiloRouteFilter(maxInput = 1.0)).map { it.tag })
        assertEquals(listOf("a", "c"), KiloRouteList.filter(rows, KiloRouteFilter(minUptime = 0.95)).map { it.tag })
        assertEquals(listOf("a"), KiloRouteList.filter(rows, KiloRouteFilter(maxInput = 3.0, minUptime = 0.95)).map { it.tag })
    }

    @Test
    fun cheapestPicksLowestInputPrice() {
        assertEquals("b", KiloRouteList.cheapest(rows)?.tag)
        assertNull(KiloRouteList.cheapest(listOf(endpoint("c"))))
        assertNull(KiloRouteList.cheapest(emptyList()))
    }

    @Test
    fun uptimePrefersThirtyMinuteWindow() {
        val both = RouteEndpointDto(tag = "x", uptime = RouteUptimeDto(last30m = 0.9, last1d = 1.0))
        assertEquals(0.9, KiloRouteList.uptime(both))
        assertEquals(1.0, KiloRouteList.uptime(RouteEndpointDto(tag = "y", uptime = RouteUptimeDto(last1d = 1.0))))
    }
}
