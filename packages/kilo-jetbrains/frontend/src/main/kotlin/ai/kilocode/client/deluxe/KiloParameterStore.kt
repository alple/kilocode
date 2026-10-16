package ai.kilocode.client.deluxe

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Per-project persistent store for the picks a new session starts with: the mode, the model per
 * mode, and the reasoning effort per mode + model.
 *
 * Lives in the IDE project's workspace file and is machine-local (`RoamingType.DISABLED`), so two
 * projects opened side by side stay independent and picks never reach the machine-global
 * model.json the CLI and the TUI share. A write only happens when a new session actually starts
 * (its first prompt send, or the New Worktree dialog's create); picking in a picker without
 * sending, mid-session switches, and the reset button never touch the store. An open session
 * never re-reads the store either — it keeps whatever it was seeded or chose.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "KiloParameterStore",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],
)
class KiloParameterStore : PersistentStateComponent<KiloParameterStore.State> {

    data class State(
        var model: Map<String, String> = emptyMap(),
        var variant: Map<String, String> = emptyMap(),
        var agent: String? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.copy(
            model = clean(state.model),
            variant = clean(state.variant),
            agent = state.agent?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** The stored model for [agent], as `"provider/model"`, or null when never picked. */
    fun model(agent: String): String? = state.model[agent]

    fun setModel(agent: String, provider: String, modelId: String) {
        if (agent.isBlank() || provider.isBlank() || modelId.isBlank()) return
        state = state.copy(model = state.model + (agent to "$provider/$modelId"))
    }

    fun clearModel(agent: String) {
        if (state.model[agent] == null) return
        state = state.copy(model = state.model - agent)
    }

    /** The stored reasoning effort for [key] (`"agent/provider/model"`), or null when never picked. */
    fun variant(key: String): String? = state.variant[key]

    fun setVariant(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        state = state.copy(variant = state.variant + (key to value))
    }

    fun clearVariant(key: String) {
        if (state.variant[key] == null) return
        state = state.copy(variant = state.variant - key)
    }

    /** The remembered mode, or null when never picked in this project. */
    fun agent(): String? = state.agent

    fun setAgent(name: String) {
        state = state.copy(agent = name.trim().takeIf { it.isNotEmpty() } ?: return)
    }

    fun clearAgent() {
        state = state.copy(agent = null)
    }

    private fun clean(map: Map<String, String>): Map<String, String> =
        map.filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
}
