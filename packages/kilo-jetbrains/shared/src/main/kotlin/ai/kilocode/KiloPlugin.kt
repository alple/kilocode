package ai.kilocode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId

object KiloPlugin {
    const val ID = "ai.kilocode.jetbrains"
    const val LUX_ID = "$ID.lux"

    /** Both Kilo plugin identities; exactly one may be enabled at a time (KILO-2, KILO-6). */
    val ids = setOf(ID, LUX_ID)

    val id: PluginId = PluginId.getId(ID)

    /**
     * This build's actual descriptor, resolved from the classloader that loaded this class rather
     * than from [ID]: lux builds carry a different plugin id, so the constant alone cannot resolve
     * them (and would resolve the other identity when both are installed).
     */
    val own: PluginDescriptor? by lazy {
        var loader: ClassLoader? = KiloPlugin::class.java.classLoader
        while (loader != null) {
            val match = PluginManagerCore.plugins.firstOrNull { it.pluginClassLoader == loader }
            if (match != null) return@lazy match
            loader = loader.parent
        }
        null
    }

    val ownId: PluginId get() = own?.pluginId ?: id

    /**
     * [own] restricted to actual Kilo identities: null when this class runs outside a Kilo plugin
     * classloader (tests, tooling), or anywhere the classloader scan cannot be trusted to name us.
     * Conflict-guard actions must fail safe on this null rather than disable a guessed plugin.
     */
    val ownIdentity: PluginDescriptor? get() = own?.takeIf { it.pluginId.idString in ids }

    fun descriptor(): PluginDescriptor? = own ?: PluginManagerCore.getPlugin(id)

    fun version() = descriptor()?.version

    fun isRc() = version()?.contains("-rc.", ignoreCase = true) == true
}
