package ai.kilocode.client.deluxe

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Deluxe-only "enable model routing" option (KILO-8.4): gates every piece of route UI in the
 * plugin. When [enabled] is off (the default) no route UI exists anywhere; the parameter picker
 * then shows model + reasoning effort only.
 *
 * The option carries the list of provider ids that are treated as route-capable. It defaults to
 * the OpenRouter pair and can be extended (custom-URL OpenRouter clones) from the routing settings
 * page. Matching is plain id equality — the provider-state DTO carries no new fields.
 */
@Service(Service.Level.APP)
@State(
    name = "KiloRoutingOptions",
    storages = [Storage("kiloRoutingOptions.xml")],
)
class KiloRoutingOptions : PersistentStateComponent<KiloRoutingOptions.State> {

    data class State(
        var enabled: Boolean = false,
        var providers: MutableList<String> = DEFAULT_PROVIDERS.toMutableList(),
    )

    private var state = State()

    val enabled: Boolean get() = state.enabled

    /** Route-capable provider ids, including the built-in defaults. */
    val providers: List<String> get() = state.providers.distinct()

    /** Whether route UI applies to [provider]: the option is on and the provider id is listed. */
    fun routesEnabled(provider: String): Boolean = enabled && provider.trim() in state.providers

    /**
     * Replace the stored option. [providers] is sanitized: trimmed, non-blank, de-duplicated
     * entries only; an empty list falls back to the defaults so the option can never strand the
     * user with no route-capable providers.
     */
    fun update(enabled: Boolean, providers: List<String>) {
        state.enabled = enabled
        state.providers = sanitize(providers)
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = State(
            enabled = state.enabled,
            providers = sanitize(state.providers),
        )
    }

    companion object {
        val DEFAULT_PROVIDERS = listOf("openrouter", "openrouter-private")

        fun getInstance(): KiloRoutingOptions = service()

        internal fun sanitize(providers: List<String>): MutableList<String> {
            val cleaned = providers.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
            return (if (cleaned.isEmpty()) DEFAULT_PROVIDERS else cleaned).toMutableList()
        }
    }
}
