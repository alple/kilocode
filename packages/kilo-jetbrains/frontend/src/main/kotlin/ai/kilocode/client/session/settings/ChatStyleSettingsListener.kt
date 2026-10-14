package ai.kilocode.client.session.settings

import com.intellij.util.messages.Topic

/** Notifies session surfaces that chat appearance settings changed and styles should be re-applied. */
fun interface ChatStyleSettingsListener {
    fun changed()

    companion object {
        @JvmField
        val TOPIC: Topic<ChatStyleSettingsListener> = Topic.create(
            "Kilo chat style settings",
            ChatStyleSettingsListener::class.java,
        )
    }
}
