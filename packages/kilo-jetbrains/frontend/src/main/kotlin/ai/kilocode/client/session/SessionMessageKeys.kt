package ai.kilocode.client.session

import com.intellij.openapi.actionSystem.DataKey

/**
 * The transcript message a menu was triggered from, published by [ai.kilocode.client.session.views.MessageView]
 * as a `UiDataProvider`. Because `DataManager` resolves data along the ancestor chain with the deepest
 * provider winning per key, this is present for right-clicks anywhere inside a message (text, tool
 * cards, code blocks) and absent for clicks outside any message.
 */
internal object SessionMessageKeys {
    val MESSAGE: DataKey<SessionMessageRef> = DataKey.create("kilo.session.message")
}

internal class SessionMessageRef(
    /** The message id a rollback rewinds to. */
    val id: String,
    /** Queued prompts have not been answered yet; rewinding to one would strand the pending prompt. */
    val queued: Boolean,
)
