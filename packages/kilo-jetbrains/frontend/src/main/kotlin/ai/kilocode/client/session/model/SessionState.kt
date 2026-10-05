package ai.kilocode.client.session.model

/** Single source of truth for what a session is doing right now. */
sealed class SessionState {
    data object Idle : SessionState()

    data object Loading : SessionState()

    data class Busy(val text: String) : SessionState()

    data class Reverting(val text: String, val kind: Kind, val message: String? = null) : SessionState() {
        enum class Kind { ROLLBACK, REDO }
    }

    data class AwaitingQuestion(val question: Question) : SessionState()

    data class AwaitingPermission(val permission: Permission) : SessionState()

    data class Retry(val message: String, val attempt: Int, val next: Long) : SessionState()

    data class Offline(val message: String, val requestId: String) : SessionState()

    data class Error(val message: String, val kind: String? = null) : SessionState()

    data class TurnEnded(val outcome: Outcome, val finish: String? = null) : SessionState()

    data class LoginRequired(val message: String) : SessionState()

    fun isBusy(): Boolean = when (this) {
        is Idle, is Loading, is Error, is TurnEnded, is LoginRequired -> false
        else -> true
    }

    /** A turn paused on a pending question or permission: nothing is streaming while it waits for you. */
    fun isWaiting(): Boolean = this is AwaitingQuestion || this is AwaitingPermission

    /**
     * Whether rewind affordances may act right now. Waiting states count as rewindable — a rollback
     * in them first settles the pending interaction; actively working states still refuse.
     */
    fun isRewindable(): Boolean = !isBusy() || isWaiting()
}
