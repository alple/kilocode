package ai.kilocode.client.plugin

import ai.kilocode.KiloPlugin
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup leg of the conflict guard (KILO-6): if the other Kilo identity is enabled when this one
 * comes up — both were installed while the IDE was closed, or one was re-enabled by hand — disable
 * it and say so. The collision may already have happened during loading; the disable makes every
 * following session clean and the notification explains the restart.
 */
class KiloConflictStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val own = KiloPlugin.ownIdentity ?: return
        val other = KiloPluginConflictGuard
            .conflict(PluginManagerCore.plugins.toList(), own.pluginId.idString) ?: return
        KiloPluginConflictGuard.enforce(own, other)
    }
}
