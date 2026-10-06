package ai.kilocode.client.plugin

import ai.kilocode.KiloPlugin
import com.intellij.ide.plugins.DynamicPluginVetoer
import com.intellij.ide.plugins.IdeaPluginDescriptor

/**
 * Refuses to let the other Kilo identity load while this one runs: a vetoed load never gets a
 * classloader, so the action-id and same-FQN class collisions (KILO-6) die before they start. The
 * guard still persists the disable and notifies, because a vetoed load stays enabled on disk.
 *
 * The platform marks [DynamicPluginVetoer.vetoPluginLoad] internal; the vetoer extension point
 * (`com.intellij.ide.dynamicPluginVetoer`) is its only supported hook for cancelling a dynamic load.
 */
class KiloConflictVetoer : DynamicPluginVetoer {
    override fun vetoPluginLoad(descriptor: IdeaPluginDescriptor): Boolean {
        val own = KiloPlugin.ownIdentity ?: return false
        val other = KiloPluginConflictGuard.blocks(
            descriptor,
            own.pluginId.idString,
            KiloPluginConflictGuard.replacingIds(),
        )
        return if (other) {
            KiloPluginConflictGuard.enforce(own, descriptor)
            true
        } else {
            false
        }
    }

    override fun vetoPluginUnload(descriptor: IdeaPluginDescriptor): String? = null
}
