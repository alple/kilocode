package ai.kilocode.client.session.controller

import ai.kilocode.client.session.model.SessionState
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.PermissionReplyDto
import ai.kilocode.rpc.dto.PermissionRequestDto
import ai.kilocode.rpc.dto.QuestionInfoDto
import ai.kilocode.rpc.dto.QuestionRequestDto
import ai.kilocode.rpc.dto.SessionRevertDto

/**
 * Rewind affordances act on a turn paused for the user: a pending question or permission is settled
 * first (rejected or denied, then the resumed turn aborted) so the CLI's busy guard lets the rewind
 * through. An actively streaming turn still refuses outright.
 */
class SessionRewindWaitingTest : SessionControllerTestBase() {

    private fun question(id: String) = QuestionRequestDto(
        id = id,
        sessionID = "ses_test",
        questions = listOf(QuestionInfoDto(question = "Proceed?", header = "Confirm")),
    )

    private fun permission(id: String) = PermissionRequestDto(
        id = id,
        sessionID = "ses_test",
        permission = "edit",
        patterns = listOf("*.kt"),
    )

    private fun seedRevert() {
        emit(ChatEventDto.MessageUpdated("ses_test", msg("u1", "ses_test", "user")), flush = false)
        emit(ChatEventDto.MessageUpdated("ses_test", msg("a1", "ses_test", "assistant")), flush = false)
        emit(ChatEventDto.MessageUpdated("ses_test", msg("u2", "ses_test", "user")), flush = false)
        emit(ChatEventDto.MessageUpdated("ses_test", msg("a2", "ses_test", "assistant")))
        emit(ChatEventDto.SessionUpdated("ses_test", session("ses_test").copy(revert = SessionRevertDto("u1"))))
    }

    fun `test revert while a question pends rejects it and aborts before rewinding`() {
        val (m, _, _) = prompted()
        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        assertTrue(m.model.state is SessionState.AwaitingQuestion)

        edt { m.revert("msg1") }
        flush()

        assertEquals(listOf("q1" to "/test"), rpc.questionRejects)
        assertEquals(listOf("ses_test" to "/test"), rpc.aborts)
        assertEquals(listOf(FakeSessionRpcApi.RevertCall("ses_test", "/test", "msg1", null)), rpc.reverts)
    }

    fun `test revert while a permission pends denies it and aborts before rewinding`() {
        val (m, _, _) = prompted()
        emit(ChatEventDto.PermissionAsked("ses_test", permission("p1")))
        assertTrue(m.model.state is SessionState.AwaitingPermission)

        edt { m.revert("msg1") }
        flush()

        assertEquals(
            listOf(Triple("p1", "/test", PermissionReplyDto(reply = "reject"))),
            rpc.permissionReplies,
        )
        assertEquals(listOf("ses_test" to "/test"), rpc.aborts)
        assertEquals(listOf(FakeSessionRpcApi.RevertCall("ses_test", "/test", "msg1", null)), rpc.reverts)
    }

    fun `test redo while a question pends settles it before rewinding`() {
        val (m, _, _) = prompted()
        seedRevert()
        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        assertTrue(m.model.state is SessionState.AwaitingQuestion)

        edt { m.redo() }
        flush()

        assertEquals(listOf("q1" to "/test"), rpc.questionRejects)
        assertEquals(listOf("ses_test" to "/test"), rpc.aborts)
        assertEquals(listOf(FakeSessionRpcApi.RevertCall("ses_test", "/test", "u2", null)), rpc.reverts)
    }

    fun `test unrevert while a question pends settles it before running`() {
        val (m, _, _) = prompted()
        seedRevert()
        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        assertTrue(m.model.state is SessionState.AwaitingQuestion)

        edt { m.unrevert() }
        flush()

        assertEquals(listOf("q1" to "/test"), rpc.questionRejects)
        assertEquals(listOf("ses_test" to "/test"), rpc.aborts)
        assertEquals(listOf("ses_test" to "/test"), rpc.unreverts)
        assertTrue(rpc.reverts.isEmpty())
    }

    fun `test rollback in a waiting state leaves no session error behind`() {
        val (m, _, _) = prompted()
        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))

        edt { m.revert("msg1") }
        flush()

        // The settle abort is a requested one: stopRequested suppresses its aborted error, so the
        // state must not surface an Error that would show Retry for a cancellation we asked for.
        assertFalse(m.model.state is SessionState.Error)
    }
}
