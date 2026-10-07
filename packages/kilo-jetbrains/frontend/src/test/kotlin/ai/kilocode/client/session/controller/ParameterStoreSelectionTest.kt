package ai.kilocode.client.session.controller

import ai.kilocode.rpc.dto.AgentConfigDto
import ai.kilocode.rpc.dto.AgentDto
import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.MessageErrorDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ModelSelectionDto
import ai.kilocode.rpc.dto.ModelStateDto
import ai.kilocode.rpc.dto.ProviderDto

/**
 * Covers the per-project parameter store wiring (ADR-0002): the store is the top model tier and the
 * effort/route write target, model.json is a read-only legacy tier, never-picked effort is Auto, and
 * background events only complete a new session's resolution — they never re-resolve a resolved one.
 */
class ParameterStoreSelectionTest : SessionControllerTestBase() {

    private fun providers(vararg models: Pair<String, List<String>>): List<ProviderDto> = listOf(
        ProviderDto(
            id = "kilo",
            name = "Kilo",
            models = models.associate { (id, variants) ->
                id to ModelDto(id = id, name = id, variants = variants)
            },
        ),
        ProviderDto(
            id = "openai",
            name = "OpenAI",
            models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
        ),
    )

    private fun ready(vararg models: Pair<String, List<String>>) {
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/${models.first().first}"))
        projectRpc.state.value = workspaceReady(
            providers = providers(*models),
            connected = listOf("kilo", "openai"),
        )
    }

    fun `test a new session inherits the store pick over the model json override`() {
        // model.json still carries an older pick (written by a TUI); the project store wins.
        appRpc.models = ModelStateDto(model = mapOf("code" to ModelSelectionDto("openai", "gpt")))
        store.setModel("code", "kilo", "opus")
        ready("gpt-5" to listOf(), "opus" to listOf())
        val m = controller()
        flush()

        assertEquals("kilo/opus", m.model.model)
        assertTrue(m.model.modelOverride)
    }

    fun `test the model json override stays the read tier when the store is empty`() {
        appRpc.models = ModelStateDto(model = mapOf("code" to ModelSelectionDto("openai", "gpt")))
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        assertEquals("openai/gpt", m.model.model)
        assertTrue(m.model.modelOverride)
    }

    fun `test never-picked effort stays auto`() {
        ready("gpt-5" to listOf("low", "high"))
        val m = controller()
        flush()

        assertEquals(null, m.model.variant)
    }

    fun `test a new session inherits the store effort`() {
        // model.json carries a machine-global effort (TUI pick); the project store pick outranks it.
        appRpc.models = ModelStateDto(variant = mapOf("kilo/gpt-5" to "low"))
        store.setVariant("kilo/gpt-5", "high")
        ready("gpt-5" to listOf("low", "high"))
        val m = controller()
        flush()

        assertEquals("high", m.model.variant)
    }

    fun `test the model json effort stays the read tier when the store is empty`() {
        appRpc.models = ModelStateDto(variant = mapOf("kilo/gpt-5" to "low"))
        ready("gpt-5" to listOf("low", "high"))
        val m = controller()
        flush()

        assertEquals("low", m.model.variant)
    }

    fun `test an in-session pick inherits into the next session only`() {
        ready("gpt-5" to listOf("low", "high"), "opus" to listOf())
        val first = controller()
        flush()
        edt { first.selectModel("kilo", "opus") }
        flush()

        val second = controller()
        flush()

        assertEquals("kilo/opus", second.model.model)
        // The pick reaches the project store, and model.json stays untouched.
        assertEquals("kilo/opus", store.model("code"))
        assertEquals(null, appRpc.models.model["code"])
    }

    fun `test app state changes do not re-resolve a resolved session`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready("gpt-5" to listOf())
        val m = controller("ses_test")
        flush()
        assertEquals("kilo/gpt-5", m.model.model)

        // A config patch (or any other app-state change) must not swap the selection underneath.
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(agent = mapOf("code" to AgentConfigDto(model = "openai/gpt"))),
        )
        flush()

        assertEquals("kilo/gpt-5", m.model.model)
    }

    fun `test model state changes do not re-resolve a resolved session`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready("gpt-5" to listOf())
        val m = controller("ses_test")
        flush()
        assertEquals("kilo/gpt-5", m.model.model)

        appRpc.models = ModelStateDto(model = mapOf("code" to ModelSelectionDto("openai", "gpt")))
        app.refreshModelFavoritesAsync()
        flush()

        assertEquals("kilo/gpt-5", m.model.model)
    }

    fun `test workspace changes do not re-resolve a resolved session`() {
        ready("gpt-5" to listOf())
        val m = controller()
        flush()
        assertEquals("kilo/gpt-5", m.model.model)
        assertEquals("code", m.model.agent)

        // A workspace refresh that seeds a different default agent must not clobber the open session.
        projectRpc.state.value = workspaceReady(
            agents = listOf(AgentDto("code", "Code", mode = "code"), AgentDto("plan", "Plan", mode = "plan")),
            default = "plan",
            providers = providers("gpt-5" to listOf()),
            connected = listOf("kilo", "openai"),
        )
        flush()

        assertEquals("code", m.model.agent)
        assertEquals("kilo/gpt-5", m.model.model)
    }

    fun `test background events still complete an unresolved session from the store`() {
        val m = controller()
        flush()
        assertEquals(null, m.model.model)

        store.setModel("code", "kilo", "opus")
        ready("gpt-5" to listOf(), "opus" to listOf())
        flush()

        assertEquals("kilo/opus", m.model.model)
        assertTrue(m.model.modelOverride)
    }

    fun `test bootstrap degrades to the first picker item when the app is gone`() {
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "openai/gpt"))
        projectRpc.state.value = workspaceReady(
            providers = providers("gpt-5" to listOf(), "opus" to listOf()),
            connected = listOf("kilo", "openai"),
        )
        val m = controller()
        flush()
        assertEquals("openai/gpt", m.model.model)

        // With the app gone there is no config tier to resolve: the pre-READY degrade picks the
        // first picker item instead (the old dead `defaults` branch returned exactly this).
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.DISCONNECTED)
        flush()
        edt { m.clearModelOverride() }
        flush()
        assertEquals("kilo/gpt-5", m.model.model)
        assertFalse(m.model.modelOverride)
    }

    fun `test the route pin rides the next prompt`() {
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        edt { m.selectRoute("deepseek/r1") }
        flush()
        edt { m.prompt("go") }
        flush()

        assertEquals("deepseek/r1", rpc.prompts.single().third.route)
        assertEquals("deepseek/r1", store.route("kilo/gpt-5"))
    }

    fun `test picking auto clears the route pin`() {
        store.setRoute("kilo/gpt-5", "deepseek/r1")
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        edt { m.selectRoute(null) }
        flush()
        edt { m.prompt("go") }
        flush()

        assertEquals(null, rpc.prompts.single().third.route)
        assertEquals(null, store.route("kilo/gpt-5"))
    }

    fun `test a new session sends the inherited route without a pin`() {
        store.setRoute("kilo/gpt-5", "deepseek/r1")
        ready("gpt-5" to listOf())
        val m = controller()
        flush()

        edt { m.prompt("go") }
        flush()

        assertEquals("deepseek/r1", rpc.prompts.single().third.route)
    }

    fun `test retry rides the route pin for the failed turn's model`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_fail", "ses_test", "assistant").copy(
                    parentID = "msg_user",
                    error = MessageErrorDto(type = "APIError", message = "provider overloaded"),
                ),
                emptyList(),
            ),
        )
        ready("gpt-5" to listOf("low", "high"))
        val m = controller("ses_test")
        flush()

        edt { m.selectRoute("deepseek/r1") }
        flush()
        edt { m.retry() }
        flush()

        assertEquals("deepseek/r1", rpc.prompts.single().third.route)
    }

    fun `test retry rides the store route when there is no pin`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_fail", "ses_test", "assistant").copy(
                    parentID = "msg_user",
                    error = MessageErrorDto(type = "APIError", message = "provider overloaded"),
                ),
                emptyList(),
            ),
        )
        store.setRoute("kilo/gpt-5", "deepseek/r1")
        ready("gpt-5" to listOf("low", "high"))
        val m = controller("ses_test")
        flush()

        edt { m.retry() }
        flush()

        assertEquals("deepseek/r1", rpc.prompts.single().third.route)
    }
}
