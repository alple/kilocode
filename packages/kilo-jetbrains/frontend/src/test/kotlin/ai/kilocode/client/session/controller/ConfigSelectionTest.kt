package ai.kilocode.client.session.controller

import ai.kilocode.rpc.dto.AgentDto
import ai.kilocode.rpc.dto.AgentConfigDto
import ai.kilocode.rpc.dto.ConfigDto
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ModelSelectionDto
import ai.kilocode.rpc.dto.ModelStateDto
import ai.kilocode.rpc.dto.ProviderDto

class ConfigSelectionTest : SessionControllerTestBase() {

    fun `test selectModel stays in its session`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5"),
                        "opus" to ModelDto(id = "opus", name = "Opus"),
                    ),
                ),
            ),
        )
        val first = controller()
        val second = controller()
        collect(first)
        collect(second)
        flush()

        edt { first.selectModel("kilo", "opus") }
        flush()

        assertTrue(appRpc.selections.isEmpty())
        assertSession(
            """
            [code] [kilo/opus] [app: DISCONNECTED] [workspace: READY]
            """,
            first,
            show = false,
        )
        assertSession(
            """
            [code] [kilo/gpt-5] [app: DISCONNECTED] [workspace: READY]
            """,
            second,
            show = false,
        )
        assertTrue(first.model.modelOverride)

        app.toggleModelFavorite("kilo", "opus")
        flush()

        assertEquals("kilo/opus", first.model.model)
        assertTrue(first.model.modelOverride)
        assertEquals("kilo/gpt-5", second.model.model)
    }

    /**
     * A mode switch must stay client-side. Writing it to the CLI's global config made the CLI dispose
     * every instance it held, cancelling every running turn in every worktree.
     */
    fun `test selectAgent stays local until a session starts`() {
        val m = controller()
        collect(m)
        flush()

        edt { m.selectAgent("plan") }
        flush()

        assertEquals("plan", m.model.agent)
        // The project's remembered mode only updates when a new session's first prompt goes out.
        assertNull(store.agent())
        assertSession(
            """
            [plan] [app: DISCONNECTED] [workspace: PENDING]
            """,
            m,
            show = false,
        )
    }

    fun `test remembered mode seeds a new session ahead of the CLI default`() {
        store.setAgent("plan")
        projectRpc.state.value = workspaceReady(
            agents = listOf(
                AgentDto(name = "code", displayName = "Code", mode = "code"),
                AgentDto(name = "plan", displayName = "Plan", mode = "code"),
            ),
            default = "code",
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("plan", m.model.agent)
    }

    fun `test CLI default wins when the remembered mode no longer exists`() {
        store.setAgent("removed-mode")
        projectRpc.state.value = workspaceReady(default = "code")
        val m = controller()
        collect(m)
        flush()

        assertEquals("code", m.model.agent)
    }

    fun `test selectModel fires WorkspaceReady event`() {
        projectRpc.state.value = workspaceReady()
        val m = controller()
        val events = collect(m)
        flush()
        events.clear()

        edt { m.selectModel("kilo", "gpt-5") }
        flush()

        assertControllerEvents("WorkspaceReady", events)
    }

    fun `test project store pick seeds a new session and shows as override`() {
        store.setModel("code", "openai", "gpt")
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(agent = mapOf("code" to AgentConfigDto(model = "anthropic/claude"))),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("anthropic", "openai"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        // The store pick wins over the config default; the config default stays the computed one.
        assertEquals("anthropic/claude", m.model.defaultModel)
        assertEquals("openai/gpt", m.model.model)
        assertTrue(m.model.modelOverride)
    }

    fun `test background ticks never re-resolve a resolved session`() {
        store.setModel("code", "openai", "gpt")
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(agent = mapOf("code" to AgentConfigDto(model = "anthropic/claude"))),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("anthropic", "openai"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()
        assertEquals("openai/gpt", m.model.model)

        // Background event waves: a config change, a model-state reload, and a workspace re-emit
        // must not move a session that already resolved its selection.
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(
                model = "anthropic/claude",
                agent = mapOf("code" to AgentConfigDto(model = "anthropic/claude")),
            ),
        )
        appRpc.models = ModelStateDto(recent = listOf(ModelSelectionDto("anthropic", "claude")))
        app.refreshModelFavoritesAsync()
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude", variants = listOf("low", "high"))),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("anthropic", "openai"),
            defaults = emptyMap(),
        )
        flush()

        assertEquals("openai/gpt", m.model.model)
        assertEquals("anthropic/claude", m.model.defaultModel)
    }

    fun `test a session the store seeded keeps its pick when another session records a newer one`() {
        store.setModel("code", "kilo", "gpt-5")
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5"),
                        "opus" to ModelDto(id = "opus", name = "Opus"),
                    ),
                ),
            ),
        )
        val seeded = controller()
        collect(seeded)
        flush()
        assertEquals("kilo/gpt-5", seeded.model.model)

        val later = controller()
        collect(later)
        flush()
        edt { later.selectModel("kilo", "opus") }
        edt { later.prompt("go") }
        flush()

        assertEquals("kilo/opus", store.model("code"))
        // The already-opened session keeps what it was seeded with.
        assertEquals("kilo/gpt-5", seeded.model.model)
        // A session opened after the recording starts from the newer pick.
        val next = controller()
        collect(next)
        flush()
        assertEquals("kilo/opus", next.model.model)
    }

    fun `test first prompt send records explicit picks into the project store`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high")),
                        "opus" to ModelDto(id = "opus", name = "Opus", variants = listOf("low", "high")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        edt {
            m.selectAgent("plan")
            m.selectModel("kilo", "opus")
            m.selectVariant("high")
            m.prompt("go")
        }
        flush()

        assertEquals("plan", store.agent())
        assertEquals("kilo/opus", store.model("plan"))
        assertEquals("high", store.variant("plan/kilo/opus"))
    }

    fun `test first prompt send records only what was explicitly picked`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high"))),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        edt { m.selectVariant("high") }
        edt { m.prompt("go") }
        flush()

        assertNull(store.agent())
        assertNull(store.model("code"))
        assertEquals("high", store.variant("code/kilo/gpt-5"))
    }

    fun `test picking without sending never records`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high")),
                        "opus" to ModelDto(id = "opus", name = "Opus"),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        edt {
            m.selectAgent("plan")
            m.selectModel("kilo", "opus")
            m.selectVariant("high")
        }
        flush()

        assertNull(store.agent())
        assertNull(store.model("plan"))
        assertNull(store.variant("plan/kilo/opus"))
    }

    fun `test established session picks never record`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high")),
                        "opus" to ModelDto(id = "opus", name = "Opus"),
                    ),
                ),
            ),
        )
        val (m, _, _) = prompted()
        assertEquals("kilo/gpt-5", m.model.model)
        assertTrue(store.model("code") == null && store.agent() == null)

        edt {
            m.selectAgent("plan")
            m.selectModel("kilo", "opus")
            m.selectVariant("high")
        }
        flush()

        assertNull(store.agent())
        assertNull(store.model("plan"))
        assertNull(store.variant("plan/kilo/opus"))
    }

    fun `test reset keeps the recorded picks`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "high")),
                        "opus" to ModelDto(id = "opus", name = "Opus"),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        edt { m.selectModel("kilo", "opus") }
        edt { m.prompt("go") }
        flush()
        assertEquals("kilo/opus", store.model("code"))

        // Reset is session-local: the project keeps what the started session recorded.
        edt { m.clearModelOverride() }
        flush()
        assertEquals("kilo/opus", store.model("code"))
    }

    fun `test clearModelOverride restores default model`() {
        store.setModel("code", "openai", "gpt")
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(agent = mapOf("code" to AgentConfigDto(model = "anthropic/claude"))),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("kilo-auto/free" to ModelDto(id = "kilo-auto/free", name = "Auto")),
                ),
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("kilo", "anthropic", "openai"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("openai/gpt", m.model.model)
        assertTrue(m.model.modelOverride)

        edt { m.clearModelOverride() }
        flush()

        assertEquals("anthropic/claude", m.model.model)
        assertFalse(m.model.modelOverride)
    }

    fun `test global config supplies computed default`() {
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(model = "openai/gpt"),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("kilo-auto/free" to ModelDto(id = "kilo-auto/free", name = "Auto")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("kilo", "openai"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("openai/gpt", m.model.defaultModel)
        assertEquals("openai/gpt", m.model.model)
        assertFalse(m.model.modelOverride)
    }

    fun `test invalid store pick falls through to config`() {
        store.setModel("code", "openai", "gpt")
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(model = "anthropic/claude"),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("kilo-auto/free" to ModelDto(id = "kilo-auto/free", name = "Auto")),
                ),
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude")),
                ),
            ),
            connected = listOf("kilo", "anthropic"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("anthropic/claude", m.model.defaultModel)
        assertEquals("anthropic/claude", m.model.model)
        assertFalse(m.model.modelOverride)
    }

    fun `test no valid candidates falls back to kilo auto`() {
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(model = "missing/model"),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("kilo-auto/free" to ModelDto(id = "kilo-auto/free", name = "Auto")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("kilo"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("kilo/kilo-auto/free", m.model.defaultModel)
        assertEquals("kilo/kilo-auto/free", m.model.model)
        assertFalse(m.model.modelOverride)
    }

    fun `test reset recomputes variants for computed model`() {
        store.setModel("code", "openai", "gpt")
        store.setVariant("code/anthropic/claude", "high")
        appRpc.state.value = KiloAppStateDto(
            KiloAppStatusDto.READY,
            config = ConfigDto(agent = mapOf("code" to AgentConfigDto(model = "anthropic/claude"))),
        )
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "anthropic",
                    name = "Anthropic",
                    models = mapOf("claude" to ModelDto(id = "claude", name = "Claude", variants = listOf("low", "high"))),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT", variants = listOf("fast"))),
                ),
            ),
            connected = listOf("anthropic", "openai"),
            defaults = emptyMap(),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("openai/gpt", m.model.model)
        assertEquals(listOf("fast"), m.model.variants)

        edt { m.clearModelOverride() }
        flush()

        assertEquals("anthropic/claude", m.model.model)
        assertEquals(listOf("low", "high"), m.model.variants)
        assertEquals("high", m.model.variant)
    }

    fun `test selectAgent uses stored model for selected agent`() {
        store.setModel("plan", "openai", "gpt")
        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY, config = ConfigDto(model = "kilo/gpt-5"))
        projectRpc.state.value = workspaceReady(
            agents = listOf(
                AgentDto(name = "code", displayName = "Code", mode = "code"),
                AgentDto(name = "plan", displayName = "Plan", mode = "code"),
            ),
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf("gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5")),
                ),
                ProviderDto(
                    id = "openai",
                    name = "OpenAI",
                    models = mapOf("gpt" to ModelDto(id = "gpt", name = "GPT")),
                ),
            ),
            connected = listOf("kilo", "openai"),
            defaults = mapOf("code" to "kilo/gpt-5", "plan" to "kilo/gpt-5"),
        )
        val m = controller()
        collect(m)
        flush()

        edt { m.selectAgent("plan") }
        flush()

        assertEquals("openai/gpt", m.model.model)
        assertTrue(m.model.modelOverride)
    }

    fun `test selectVariant stays in its session`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("low", "medium", "high")),
                    ),
                ),
            ),
        )
        val first = controller()
        val second = controller()
        collect(first)
        collect(second)
        flush()

        edt { first.selectVariant("high") }
        flush()

        assertEquals("high", first.model.variant)
        assertEquals("high", second.model.variant)
        assertTrue(appRpc.variants.isEmpty())
    }

    fun `test strongest variant is the never-picked effort default`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("none", "low", "medium", "high", "xhigh", "max")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("max", m.model.variant)
    }

    fun `test stored effort beats the strongest default`() {
        store.setVariant("code/kilo/gpt-5", "low")
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("none", "low", "medium", "high")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("low", m.model.variant)
    }

    fun `test stale stored effort falls through to the strongest default`() {
        store.setVariant("code/kilo/gpt-5", "stale")
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("none", "low", "high")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("high", m.model.variant)
    }

    fun `test only unranked efforts fall back to the last listed entry`() {
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("fast", "turbo")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("turbo", m.model.variant)
    }

    fun `test stored effort that is no longer offered falls through for unranked models too`() {
        store.setVariant("code/kilo/gpt-5", "gone")
        projectRpc.state.value = workspaceReady(
            providers = listOf(
                ProviderDto(
                    id = "kilo",
                    name = "Kilo",
                    models = mapOf(
                        "gpt-5" to ModelDto(id = "gpt-5", name = "GPT-5", variants = listOf("fast", "turbo")),
                    ),
                ),
            ),
        )
        val m = controller()
        collect(m)
        flush()

        assertEquals("turbo", m.model.variant)
    }
}
