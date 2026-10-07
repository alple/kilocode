package ai.kilocode.client.deluxe

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Per-project parameter store — the single write target for every Kilo session
 * parameter this plugin persists (ADR-0002).
 *
 * Stored in the project's workspace.xml, so side-by-side IntelliJ projects never
 * cross-populate picks. `model.json` is user-global with no directory dimension,
 * which is why it cannot be the home; the plugin only reads it (per-agent
 * override, legacy effort map, favorites) and no longer writes it for
 * selection/variant/route/mode — the CLI TUI keeps its own writes.
 *
 * Keys:
 * - `model`: agent name → "provider/model" pick the agent's sessions inherit.
 * - `variant`: "provider/model" → reasoning effort picked for that model.
 * - `route`: "provider/model" → OpenRouter routing tag pinned for that model.
 * - `agent`: the mode the next new session opens with.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "KiloParameterStore",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],
)
class KiloProjectParameterStore : PersistentStateComponent<KiloProjectParameterStore.State> {

    data class State(
        var model: Map<String, String> = emptyMap(),
        var variant: Map<String, String> = emptyMap(),
        var route: Map<String, String> = emptyMap(),
        var agent: String? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = State(
            model = state.model.filterValues { it.isNotBlank() },
            variant = state.variant.filterValues { it.isNotBlank() },
            route = state.route.filterValues { it.isNotBlank() },
            agent = state.agent?.takeIf { it.isNotBlank() },
        )
    }

    fun model(agent: String): String? = state.model[agent]

    fun setModel(agent: String, provider: String, modelId: String) {
        state = state.copy(model = state.model + (agent to "$provider/$modelId"))
    }

    fun clearModel(agent: String) {
        state = state.copy(model = state.model - agent)
    }

    fun variant(key: String): String? = state.variant[key]

    fun setVariant(key: String, value: String) {
        state = state.copy(variant = state.variant + (key to value))
    }

    fun route(key: String): String? = state.route[key]

    fun setRoute(key: String, value: String) {
        state = state.copy(route = state.route + (key to value))
    }

    fun clearRoute(key: String) {
        state = state.copy(route = state.route - key)
    }

    fun getAgent(): String? = state.agent

    fun setAgent(value: String) {
        state = state.copy(agent = value)
    }

    companion object {
        fun getInstance(project: Project): KiloProjectParameterStore = project.service()
    }
}
