package ai.kilocode.client.session

import ai.kilocode.client.actions.MessageRollbackAction
import ai.kilocode.client.session.ui.SessionMessageListPanel
import ai.kilocode.client.session.views.TextView
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.MessageErrorDto
import ai.kilocode.rpc.dto.MessageWithPartsDto
import ai.kilocode.rpc.dto.SessionRevertDto
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import java.awt.Component

/**
 * Closes the KILO-5 rewind gaps that only indirect coverage left behind: the pending rollback
 * settle against a marker the CLI widened to the preceding user message, the no-op marker, the
 * failed rollback that must leave no pending state, and the queued prompt publication through the
 * real DataManager chain.
 */
@Suppress("UnstableApiUsage")
class SessionUiRollbackTest : SessionUiTestBase() {

    /** A user question and an assistant answer, tall enough to overflow the viewport. */
    private fun seeded() {
        rpc.history.addAll(listOf(
            MessageWithPartsDto(
                message("u1"),
                listOf(part("u1p", "u1", "text", text(0) + text(1) + text(2) + text(3) + text(4))),
            ),
            MessageWithPartsDto(
                message("a1").copy(role = "assistant"),
                listOf(part("a1p", "a1", "text", text(5))),
            ),
        ))
        ui = newUi(id = "ses_test")
        settle()
        drainScroll()
    }

    fun `test rollback on an assistant message settles when the marker widens to the user message`() {
        realDataManager()
        seeded()

        val ref = resolved(target("a1"))
        assertEquals("a1", ref.id)
        assertFalse(ref.queued)
        val (action, event) = eventAt(target("a1"))
        assertTrue(event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)
        settle()
        assertEquals(listOf(FakeSessionRpcApi.RevertCall("ses_test", "/test", "a1", null)), rpc.reverts)

        val bar = scrollBar()
        setValue(bar, bottom(bar) / 2)
        assertTrue(jumpButton().isVisible)

        // The CLI widens the assistant boundary to the preceding user message, so the marker id
        // differs from the clicked message. Any resulting marker settles the pending rollback.
        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test").copy(revert = SessionRevertDto("u1"))))
        drainScroll()

        assertEquals("u1", controller().model.revert()?.messageID)
        assertBottom(bar)
        assertTrue(ui.scroll.following())
        assertFalse(jumpButton().isVisible)
    }

    fun `test a revert marker with no pending rollback does not scroll`() {
        realDataManager()
        seeded()
        val bar = scrollBar()
        setValue(bar, bottom(bar) / 2)
        val value = bar.value
        assertTrue(jumpButton().isVisible)

        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test").copy(revert = SessionRevertDto("u1"))))
        drainScroll()

        // The marker itself applies through the model, but it must not settle-or-scroll anything.
        assertEquals("u1", controller().model.revert()?.messageID)
        assertEquals(value, bar.value)
        assertFalse(ui.scroll.following())
        assertTrue(jumpButton().isVisible)
    }

    fun `test a failed rollback leaves no pending rollback behind`() {
        realDataManager()
        seeded()
        val (action, event) = eventAt(target("a1"))
        assertTrue(event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)
        settle()
        assertEquals(1, rpc.reverts.size)

        // No marker arrives: the CLI reports an error instead. The error state drops the pending
        // rollback, so a later marker from elsewhere must not scroll on the stale click's behalf.
        emit(ChatEventDto.Error("ses_test", MessageErrorDto(type = "APIError", message = "boom")))

        val bar = scrollBar()
        setValue(bar, bottom(bar) / 2)
        val value = bar.value
        assertTrue(jumpButton().isVisible)

        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test").copy(revert = SessionRevertDto("u1"))))
        drainScroll()

        assertEquals("u1", controller().model.revert()?.messageID)
        assertEquals(value, bar.value)
        assertFalse(ui.scroll.following())
        assertTrue(jumpButton().isVisible)
    }

    fun `test queued prompt publishes queued=true and hides the rollback item`() {
        realDataManager()
        ui = newUi(id = "ses_test")
        settle()

        controller().prompt("first")
        settle()
        emit(ChatEventDto.MessageUpdated("ses_test", message("u1")), flush = false)
        emit(ChatEventDto.PartUpdated("ses_test", part("u1p", "u1", "text", "first\n")), flush = false)
        emit(ChatEventDto.TurnOpen("ses_test"))

        controller().prompt("second")
        settle()
        assertEquals(2, rpc.prompts.size)

        emit(ChatEventDto.MessageUpdated("ses_test", message("u2")), flush = false)
        emit(ChatEventDto.PartUpdated("ses_test", part("u2p", "u2", "text", "second\n")), flush = false)
        emit(ChatEventDto.SessionQueueChanged("ses_test", listOf("u2")))
        drainScroll()

        val ref = resolved(target("u2"))
        assertEquals("u2", ref.id)
        assertTrue("queued prompt must publish queued=true", ref.queued)
        assertFalse("rollback hides for the queued target", eventAt(target("u2")).second.presentation.isEnabledAndVisible)

        emit(ChatEventDto.TurnClose("ses_test", "stop"))

        val settled = resolved(target("u1"))
        assertFalse(settled.queued)
        assertTrue("rollback stays visible for the settled prompt", eventAt(target("u1")).second.presentation.isEnabledAndVisible)
        assertFalse("the queued flag alone keeps hiding the queued target", eventAt(target("u2")).second.presentation.isEnabledAndVisible)
    }

    private fun target(mid: String): TextView {
        val view = find<SessionMessageListPanel>(ui).findMessage(mid) ?: error("missing message $mid")
        return find(view, TextView::class.java) ?: error("missing text part in $mid")
    }

    private fun resolved(target: Component): SessionMessageRef =
        DataManager.getInstance().getDataContext(target).getData(SessionMessageKeys.MESSAGE)
            ?: error("no message ref resolved")

    private fun eventAt(target: Component): Pair<MessageRollbackAction, AnActionEvent> {
        val action = MessageRollbackAction()
        val presentation = Presentation().apply { copyFrom(action.templatePresentation) }
        val event = AnActionEvent.createFromDataContext(
            "",
            presentation,
            DataManager.getInstance().getDataContext(target),
        )
        ActionUtil.updateAction(action, event)
        return action to event
    }

    /**
     * `HeadlessDataManager` never traverses the Swing hierarchy by default, so every `UiDataProvider`
     * on the ancestor chain is ignored and even `COPY_PROVIDER` reads back null. Opt into the real
     * `DataManagerImpl` for the tests that assert context resolution.
     */
    private fun realDataManager() {
        HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
    }
}
