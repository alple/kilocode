package ai.kilocode.client.session.controller

import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ProviderDto

/**
 * Covers the frozen model/route/effort trio (ADR-0001): the trio locks as one at the session's
 * first message, edits before that are free, edits after it need an explicit unlock, and the next
 * send re-locks. A pin is never auto-cleared.
 */
class ParameterFreezeTest : SessionControllerTestBase() {

    private fun providers() = listOf(
        ProviderDto(
            id = "kilo",
            name = "Kilo",
            models = mapOf(
                "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high")),
                "opus" to ModelDto(id = "opus", name = "Opus"),
            ),
        ),
        ProviderDto(
            id = "openai",
            name = "OpenAI",
            models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
        ),
    )

    private fun ready() {
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/gpt-5"))
        projectRpc.state.value = workspaceReady(
            providers = providers(),
            connected = listOf("kilo", "openai"),
        )
    }

    fun `test a fresh session starts with the parameter trio unlocked`() {
        ready()
        val m = controller()
        flush()

        assertFalse(m.model.parametersFrozen)
        assertNull(m.model.variant)
        assertNull(m.routeFor(m.model.model))
    }

    fun `test the first message freezes the parameter trio`() {
        ready()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.prompt("hello") }
        flush()

        assertTrue("The trio locks at the first message", m.model.parametersFrozen)
        assertControllerEvents("""
            AccountOverlayChanged hide
            ViewChanged session
            WorkspaceReady
        """, events)
    }

    fun `test a later message does not fire the freeze event again`() {
        ready()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.prompt("first") }
        flush()
        events.clear()
        edt { m.prompt("second") }
        flush()

        assertTrue("Re-locking an already frozen trio stays silent", events.isEmpty())
        assertEquals(2, rpc.prompts.size)
    }

    fun `test a session with history starts frozen`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready()
        val m = controller("ses_test")
        flush()

        assertTrue(m.model.parametersFrozen)
    }

    fun `test empty history loads unfrozen`() {
        ready()
        val m = controller("ses_test")
        flush()

        assertFalse("An explicit empty session has no messages to lock on", m.model.parametersFrozen)
    }

    fun `test selection changes are refused while frozen`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready()
        val m = controller("ses_test")
        flush()

        assertTrue(m.model.parametersFrozen)
        edt { m.selectModel("openai", "gpt") }
        edt { m.selectRoute("deepseek/r1") }
        edt { m.selectVariant("high") }
        edt { m.clearVariant() }
        edt { m.clearModelOverride() }
        flush()

        assertEquals("The model pick stays what the history resolved", "kilo/gpt-5", m.model.model)
        assertNull(m.model.variant)
        assertNull(m.routeFor(m.model.model))
        assertNull(store.route("kilo/gpt-5"))
        assertNull(store.variant("kilo/gpt-5"))
        assertTrue(rpc.prompts.isEmpty())
    }

    fun `test unlock allows edits and the next message re-freezes`() {
        ready()
        val m = controller()
        flush()
        edt { m.prompt("hello") }
        flush()
        assertTrue(m.model.parametersFrozen)

        edt { m.unfreezeParameters() }
        edt { m.selectModel("openai", "gpt") }
        edt { m.selectRoute("deepseek/r1") }
        flush()

        assertFalse("Unlock releases the trio", m.model.parametersFrozen)
        assertEquals("openai/gpt", m.model.model)
        assertEquals("deepseek/r1", m.routeFor("openai/gpt"))

        edt { m.prompt("again") }
        flush()

        assertTrue("The next message re-locks the trio", m.model.parametersFrozen)
        assertEquals("deepseek/r1", rpc.prompts.last().third.route)
    }

    fun `test unlock on an unfrozen session fires nothing`() {
        ready()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.unfreezeParameters() }
        flush()

        assertTrue(events.isEmpty())
    }

    fun `test route and effort picks fire WorkspaceReady so the header re-syncs`() {
        ready()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.selectRoute("deepseek/r1") }
        edt { m.selectVariant("high") }
        flush()

        assertEquals(listOf("WorkspaceReady", "WorkspaceReady"), events.map { it.toString() })
        assertEquals("deepseek/r1", m.routeFor(m.model.model))
        assertEquals("high", m.model.variant)
    }

    fun `test clear resets the frozen state`() {
        rpc.history.add(
            MessageWithPartsDto(
                msg("msg_user", "ses_test", "user").copy(providerID = "kilo", modelID = "gpt-5", agent = "code"),
                emptyList(),
            ),
        )
        ready()
        val m = controller("ses_test")
        flush()
        assertTrue(m.model.parametersFrozen)

        edt { m.model.clear() }
        flush()

        assertFalse("A cleared session restarts with an editable trio", m.model.parametersFrozen)
    }
}
