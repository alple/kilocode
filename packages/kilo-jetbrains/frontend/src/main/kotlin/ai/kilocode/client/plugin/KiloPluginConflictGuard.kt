package ai.kilocode.client.plugin

import ai.kilocode.KiloPlugin
import ai.kilocode.client.KiloNotifications
import ai.kilocode.log.KiloLog
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.ProjectManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps exactly one Kilo plugin identity enabled (KILO-6). Stable (`ai.kilocode.jetbrains`) and lux
 * (`ai.kilocode.jetbrains.lux`) register the same action ids and ship same-FQN classes, so two
 * enabled identities collide on "ID already taken" at action registration and throw
 * ClassCastException across RPC. The guard disables the identity that is not running, persists the
 * choice in disabled_plugins.txt, and tells the user how to get it back.
 */
object KiloPluginConflictGuard {
    private val log = KiloLog.create(KiloPluginConflictGuard::class.java)

    /** Kilo identities this session saw unload for an in-place update; their reload is not a conflict. */
    private val updating = ConcurrentHashMap.newKeySet<String>()

    /** Identities this session already warned about, so one conflict yields one notification. */
    private val notified = ConcurrentHashMap.newKeySet<String>()

    /** Other Kilo identity that is enabled, or null when the state is supported. */
    fun conflict(plugins: List<PluginDescriptor>, own: String): PluginDescriptor? =
        plugins.firstOrNull { it.isEnabled && it.pluginId.idString != own && it.pluginId.idString in KiloPlugin.ids }

    /**
     * Whether [descriptor] is the other Kilo identity loading while this one runs — the case that
     * must not get a second classloader. Own-id loads (this plugin's own upgrade) and in-place
     * upgrades of the other identity ([replacing] tracks the unloads seen) are replacements, not
     * conflicts.
     */
    fun blocks(descriptor: PluginDescriptor, own: String, replacing: Set<String>): Boolean {
        val id = descriptor.pluginId.idString
        return id != own && id in KiloPlugin.ids && id !in replacing
    }

    fun markUpdating(descriptor: PluginDescriptor) {
        updating.add(descriptor.pluginId.idString)
    }

    fun replacingIds(): Set<String> = updating

    /** Disables [other] persistently and notifies once; returns whether the disable took. */
    fun enforce(own: PluginDescriptor, other: PluginDescriptor): Boolean {
        val disabled = PluginManagerCore.disablePlugin(other.pluginId)
        log.info(
            "Conflicting Kilo plugins: ${own.pluginId.idString} is running, auto-disabled " +
                "${other.pluginId.idString} (persisted=$disabled)",
        )
        if (notified.add(other.pluginId.idString)) notify(own, other)
        return disabled
    }

    private fun notify(own: PluginDescriptor, other: PluginDescriptor) {
        ApplicationManager.getApplication().invokeLater {
            val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }
            val restart = KiloBundle.message("plugin.conflict.notification.restart") to {
                ApplicationManager.getApplication().restart()
            }
            KiloNotifications.warning(
                project,
                KiloBundle.message("plugin.conflict.notification.title"),
                KiloBundle.message("plugin.conflict.notification.body", own.name, other.name),
                listOf(restart),
            )
        }
    }
}
