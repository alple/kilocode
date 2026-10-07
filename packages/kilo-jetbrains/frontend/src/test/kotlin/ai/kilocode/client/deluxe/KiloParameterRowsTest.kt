package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Route rows, gating, staleness notes, and the row key/format helpers of the parameter popup. */
class KiloParameterRowsTest : BasePlatformTestCase() {

    private fun endpoint(
        tag: String,
        quantization: String? = "fp8",
        status: Int? = 0,
        uptime30m: Double? = 0.99,
        prompt: Double? = 0.0000025,
        completion: Double? = 0.000010,
    ) = RouteEndpointDto(
        tag = tag,
        providerName = tag.ifBlank { null },
        quantization = quantization,
        status = status,
        uptime = RouteUptimeDto(last30m = uptime30m),
        pricing = RoutePricingDto(prompt = prompt, completion = completion),
    )

    private fun item(id: String, provider: String = "openrouter") =
        ModelPicker.Item(id, id, provider, id)

    fun `test routeAuthorSlug splits author and slug`() {
        assertEquals("z-ai" to "glm-5.3-flash", routeAuthorSlug("z-ai/glm-5.3-flash"))
        assertEquals("parasail" to "r2", routeAuthorSlug("parasail/r2"))
    }

    fun `test routeAuthorSlug rejects ids without an author part`() {
        assertNull(routeAuthorSlug("no-slash"))
        assertNull(routeAuthorSlug("/leading"))
        assertNull(routeAuthorSlug("trailing/"))
    }

    fun `test route section is hidden without a route state`() {
        assertNull(routeSectionRows("parasail", null))
    }

    fun `test route section lists Auto plus the fetched endpoints`() {
        val rows = routeSectionRows("parasail", KiloRouteState.Ready(listOf(endpoint("parasail"), endpoint("nano"))))!!

        assertEquals(3, rows.size)
        assertEquals(null, (rows[0] as KiloParamRow.Route).tag)
        assertEquals("parasail", (rows[1] as KiloParamRow.Route).tag)
        assertEquals("nano", (rows[2] as KiloParamRow.Route).tag)
        assertTrue(rows.all { it.section == KiloBundle.message("param.picker.route.section") })
    }

    fun `test a stale pin renders a recovery note and keeps the Auto row`() {
        val rows = routeSectionRows("parasail", KiloRouteState.Ready(listOf(endpoint("nano"))))!!

        assertTrue("The pin row itself is not fabricated", rows.none { it is KiloParamRow.Route && it.tag == "parasail" })
        val note = rows.last() as KiloParamRow.Note
        assertTrue(note.warn)
        assertEquals(KiloBundle.message("param.picker.stale", "parasail"), note.text)
    }

    fun `test a fresh pin renders no note`() {
        val rows = routeSectionRows("parasail", KiloRouteState.Ready(listOf(endpoint("parasail"))))!!

        assertTrue(rows.none { it is KiloParamRow.Note })
    }

    fun `test loading and failed states render notes`() {
        val loading = routeSectionRows("parasail", KiloRouteState.Loading)!!
        val failed = routeSectionRows("parasail", KiloRouteState.Failed("boom"))!!

        assertFalse((loading.single() as KiloParamRow.Note).warn)
        assertTrue((failed.single() as KiloParamRow.Note).warn)
    }

    fun `test routesApply gates on the option and the author-slug shape`() {
        val gate: (String) -> Boolean = { it == "openrouter" }

        assertFalse(routesApply(gate, null))
        assertFalse(routesApply(gate, item("z-ai/glm-5.3-flash", provider = "openai")))
        assertFalse("A bare id has no CLI path", routesApply(gate, item("claude")))
        assertTrue(routesApply(gate, item("z-ai/glm-5.3-flash")))
    }

    fun `test rows gate the route section behind the option`() {
        val gate: (String) -> Boolean = { it == "openrouter" }
        val off = kiloParameterRows(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            favorites = emptyList(),
            variants = listOf("low", "high"),
            variant = null,
            route = "parasail",
            frozen = false,
            routes = null,
            query = "",
        )
        assertTrue("No route rows when the option keeps routes out", off.none { it is KiloParamRow.Route })
        assertTrue("Effort rows still render", off.any { it is KiloParamRow.Effort })

        val on = kiloParameterRows(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            favorites = emptyList(),
            variants = listOf("low", "high"),
            variant = null,
            route = "parasail",
            frozen = false,
            routes = KiloRouteState.Ready(listOf(endpoint("parasail"))),
            query = "",
        )
        assertEquals(listOf<String?>(null, "parasail"), on.filterIsInstance<KiloParamRow.Route>().map { it.tag })
    }

    fun `test search narrows to the model section only`() {
        val gate: (String) -> Boolean = { it == "openrouter" }
        val rows = kiloParameterRows(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            favorites = emptyList(),
            variants = listOf("low", "high"),
            variant = null,
            route = "parasail",
            frozen = false,
            routes = KiloRouteState.Ready(listOf(endpoint("parasail"))),
            query = "glm",
        )
        assertTrue(rows.all { it is KiloParamRow.Model })
    }

    fun `test the lock row appears only while frozen`() {
        val gate: (String) -> Boolean = { false }
        val unfrozen = kiloParameterRows(
            items = listOf(item("claude")),
            selected = "anthropic/claude",
            favorites = emptyList(),
            variants = emptyList(),
            variant = null,
            route = null,
            frozen = false,
            routes = null,
            query = "",
        )
        assertTrue(unfrozen.none { it is KiloParamRow.Lock })

        val frozen = kiloParameterRows(
            items = listOf(item("claude")),
            selected = "anthropic/claude",
            favorites = emptyList(),
            variants = emptyList(),
            variant = null,
            route = null,
            frozen = true,
            routes = null,
            query = "",
        )
        assertEquals(KiloParamRow.Lock, frozen.first())
    }

    fun `test row keys are stable per row kind`() {
        val rows = kiloParameterRows(
            items = listOf(item("z-ai/glm-5.3-flash")),
            selected = "openrouter/z-ai/glm-5.3-flash",
            favorites = emptyList(),
            variants = listOf("high"),
            variant = "high",
            route = "parasail",
            frozen = true,
            routes = KiloRouteState.Ready(listOf(endpoint("parasail"))),
            query = "",
        )
        val keyed = rows.associate { kiloParamKey(it) to it }
        assertEquals(setOf("lock", "m:openrouter/z-ai/glm-5.3-flash", "r:auto", "r:parasail", "v:auto", "v:high"), keyed.keys)
    }

    fun `test pricing formats per Mtok and survives missing halves`() {
        assertEquals("in \$2.50 · out \$10.00", KiloRouteText.pricing(RoutePricingDto(prompt = 0.0000025, completion = 0.00001)))
        assertEquals("out \$10.00", KiloRouteText.pricing(RoutePricingDto(prompt = null, completion = 0.00001)))
        assertEquals(null, KiloRouteText.pricing(null))
    }

    fun `test uptime prefers the 30m window and falls back to the daily one`() {
        assertEquals("99%", KiloRouteText.uptime(RouteUptimeDto(last30m = 0.99, last1d = 0.5)))
        assertEquals("50%", KiloRouteText.uptime(RouteUptimeDto(last1d = 0.5)))
        assertEquals(null, KiloRouteText.uptime(null))
    }

    fun `test quantization and degraded status`() {
        assertEquals("fp8", KiloRouteText.quantization(endpoint("parasail")))
        assertEquals(null, KiloRouteText.quantization(null))
        assertFalse(KiloRouteText.degraded(0))
        assertFalse(KiloRouteText.degraded(null))
        assertTrue(KiloRouteText.degraded(1))
    }
}

