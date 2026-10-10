package ai.kilocode.client.actions

import ai.kilocode.client.deluxe.KiloFavoritesDialog
import ai.kilocode.client.plugin.KiloBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/** Opens the favorites management window (ADR-0003) for the invoking project. */
class EditFavoritesAction : AnAction(
    KiloBundle.message("action.Kilo.EditFavorites.text"),
    KiloBundle.message("action.Kilo.EditFavorites.description"),
    AllIcons.Nodes.Favorite,
), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        KiloFavoritesDialog.show(project, null)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
