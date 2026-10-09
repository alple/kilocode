package ai.kilocode.client.session.controller

import ai.kilocode.client.deluxe.KiloFavoritePreset
import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ProviderDto

/**
 * Covers the preset pick (ADR-0003): a favorite preset lands model + OpenRouter route + reasoning
 * effort as one selection through the project parameter store, null pins mean Auto (the pins clear),
 * unknown models are refused, and the frozen trio refuses preset picks like every parameter pick.
 */
class PresetSelectionTest : SessionControllerTestBase() {

    private fun providers(models: List<Pair<String, List<String>>>): List<ProviderDto> = listOf(
        ProviderDto(
            id = "kilo",
            name = "Kilo",
            models = models.associate { (id, variants) ->
                id to ModelDto(id = id, name = id, variants = variants)
            },
        ),
    )

    private fun ready(vararg models: Pair<String, List<String>>) {
        val list = models.toList().ifEmpty { listOf("gpt-5" to listOf("low", "high")) }
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/${list.first().first}"))
        projectRpc.state.value = workspaceReady(
            providers = providers(list),
            connected = listOf("kilo"),
        )
    }

    private fun preset(
        provider: String = "kilo",
        modelId: String = "opus",
        routeTag: String? = "deepseek/r1",
        variant: String? = "high",
    ) = KiloFavoritePreset(
        id = "fast",
        name = "Fast",
        labels = listOf("project"),
        provider = provider,
        modelId = modelId,
        routeTag = routeTag,
        variant = variant,
    )

    fun `test a preset pick lands model route and effort as one`() {
        ready()
        val m = controller()
        flush()

        edt { m.selectPreset(preset(modelId = "gpt-5")) }
        flush()

        assertEquals("kilo/gpt-5", m.model.model)
        assertFalse("The preset model equals the default, so it is not an override", m.model.modelOverride)
        assertEquals("high", m.model.variant)
        assertEquals("deepseek/r1", m.routeFor("kilo/gpt-5"))
        assertEquals("The trio persists for the next session", "kilo/gpt-5", store.model("code"))
        assertEquals("deepseek/r1", store.route("kilo/gpt-5"))
        assertEquals("high", store.variant("kilo/gpt-5"))
    }

    fun `test the preset trio rides the next prompt`() {
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        edt { m.selectPreset(preset(modelId = "gpt-5")) }
        flush()
        edt { m.prompt("go") }
        flush()

        assertEquals("deepseek/r1", rpc.prompts.single().third.route)
    }

    fun `test a null-pin preset clears the pins like Auto`() {
        store.setRoute("kilo/gpt-5", "deepseek/r1")
        store.setVariant("kilo/gpt-5", "low")
        ready("gpt-5" to listOf("low", "high"))
        val m = controller()
        flush()
        assertEquals("deepseek/r1", m.routeFor("kilo/gpt-5"))
        assertEquals("low", m.model.variant)

        edt { m.selectPreset(preset(modelId = "gpt-5", routeTag = null, variant = null)) }
        flush()

        assertEquals("high-variant pin gone, back to Auto", null, m.model.variant)
        assertEquals(null, m.routeFor("kilo/gpt-5"))
        assertEquals(null, store.route("kilo/gpt-5"))
        assertEquals(null, store.variant("kilo/gpt-5"))
    }

    fun `test a preset whose model is unknown is refused without store writes`() {
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        edt { m.selectPreset(preset(modelId = "missing", routeTag = "parasail")) }
        flush()

        assertEquals("The selection stays what the workspace resolved", "kilo/gpt-5", m.model.model)
        assertNull(store.model("code"))
        assertTrue(rpc.prompts.isEmpty())
    }

    fun `test a preset pick is refused while the trio is frozen`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready("gpt-5" to listOf("low", "high"))
        val m = controller("ses_test")
        flush()

        assertTrue(m.model.parametersFrozen)
        edt { m.selectPreset(preset()) }
        flush()

        assertEquals("kilo/gpt-5", m.model.model)
        assertNull(m.routeFor("kilo/gpt-5"))
        assertNull(store.route("kilo/gpt-5"))
        assertTrue(rpc.prompts.isEmpty())
    }
}
