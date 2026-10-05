package ai.kilocode.client.actions

import ai.kilocode.client.session.SessionActionsKeys
import ai.kilocode.client.session.SessionMessageKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Rewinds the session to the transcript message the menu was triggered from: everything after it is
 * hidden and its code edits are restored from snapshots, until the user sends the next prompt.
 *
 * This is the per-message rollback affordance that works on any message kind — user, assistant, or
 * tool-result — including tool cards and code blocks that have no hover toolbar. Hidden where it
 * does not apply: outside a message, on queued prompts, in read-only hosts, and while a prompt is
 * running (rewind is idle-only; stop the turn explicitly first).
 */
class MessageRollbackAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val actions = e.getData(SessionActionsKeys.ACTIONS)
        val message = e.getData(SessionMessageKeys.MESSAGE)
        e.presentation.isEnabledAndVisible = actions != null &&
            !actions.readonly &&
            actions.rewindable &&
            message != null &&
            !message.queued
    }

    override fun actionPerformed(e: AnActionEvent) {
        val message = e.getData(SessionMessageKeys.MESSAGE) ?: return
        e.getData(SessionActionsKeys.ACTIONS)?.rollback(message.id)
    }
}
