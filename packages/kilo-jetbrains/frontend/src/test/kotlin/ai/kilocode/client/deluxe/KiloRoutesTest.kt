package ai.kilocode.client.deluxe

import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the route data helpers the Deluxe surfaces share (ADR-0003): the `author/slug` id shape
 * the CLI route-list path is keyed by, and the "enable model routing" visibility gate.
 */
class KiloRoutesTest {

    private fun item(id: String, provider: String = "openrouter") =
        ModelPicker.Item(id, id, provider, id)

    @Test
    fun `routeAuthorSlug splits author and slug`() {
        assertEquals("z-ai" to "glm-5.3-flash", routeAuthorSlug("z-ai/glm-5.3-flash"))
        assertEquals("parasail" to "r2", routeAuthorSlug("parasail/r2"))
    }

    @Test
    fun `routeAuthorSlug rejects ids without an author part`() {
        assertNull(routeAuthorSlug("no-slash"))
        assertNull(routeAuthorSlug("/leading"))
        assertNull(routeAuthorSlug("trailing/"))
    }

    @Test
    fun `routesApply gates on the option and the author-slug shape`() {
        val gate = { provider: String -> provider == "openrouter" }
        assertFalse(routesApply(gate, null))
        assertFalse(routesApply(gate, item("z-ai/glm-5.3-flash", provider = "openai")))
        assertFalse(routesApply(gate, item("claude")), "A bare id has no CLI path")
        assertTrue(routesApply(gate, item("z-ai/glm-5.3-flash")))
    }

    @Test
    fun `KiloRouteText formats prices uptime and quantization`() {
        assertNull(KiloRouteText.pricing(null))
        assertEquals("in $2.50 · out $10.00", KiloRouteText.pricing(RoutePricingDto(prompt = 0.0000025, completion = 0.00001)))
        assertEquals("in $2.50", KiloRouteText.pricing(RoutePricingDto(prompt = 0.0000025)))
        assertEquals("97%", KiloRouteText.uptime(RouteUptimeDto(last30m = 0.97)))
        assertEquals("90%", KiloRouteText.uptime(RouteUptimeDto(last1d = 0.9)))
        assertNull(KiloRouteText.uptime(RouteUptimeDto()))
        assertEquals("fp8", KiloRouteText.quantization(endpoint(quantization = "fp8")))
        assertNull(KiloRouteText.quantization(null))
        assertTrue(KiloRouteText.degraded(2))
        assertFalse(KiloRouteText.degraded(null))
        assertFalse(KiloRouteText.degraded(0))
    }

    private fun endpoint(quantization: String?): RouteEndpointDto = RouteEndpointDto(quantization = quantization)
}
