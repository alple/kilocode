package ai.kilocode.client.settings.appearance

import ai.kilocode.client.session.settings.ChatStyleSettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Bounds and step, in points, for chat font size offsets. */
object ChatFontOffset {
    const val MIN = -4.0

    const val MAX = 8.0

    const val STEP = 0.1

    fun clamp(value: Double): Double = ((value.coerceIn(MIN, MAX) * 100).toInt()) / 100.0
}

/**
 * Chat font preferences resolved by [ai.kilocode.client.session.ui.style.SessionEditorStyle]: a font
 * family override for proportional chat text and per-group size offsets in points. A null family
 * means "follow the IDE".
 */
data class ChatFontSpec(
    val family: String? = null,
    val response: Float = 0f,
    val thinking: Float = 0f,
    val tool: Float = 0f,
    val prompt: Float = 0f,
) {
    /** Blank family to null and every offset clamped to the supported range, so drafts compare equal after round-trips. */
    fun normalized(): ChatFontSpec = copy(
        family = family?.takeIf { it.isNotBlank() },
        response = ChatFontOffset.clamp(response.toDouble()).toFloat(),
        thinking = ChatFontOffset.clamp(thinking.toDouble()).toFloat(),
        tool = ChatFontOffset.clamp(tool.toDouble()).toFloat(),
        prompt = ChatFontOffset.clamp(prompt.toDouble()).toFloat(),
    )

    companion object {
        val DEFAULT = ChatFontSpec()
    }
}

/**
 * IDE-local chat appearance preferences: the chat font family and per-group size offsets
 * (chat response, thinking, tool output & code, prompt input) in hundredths of a point.
 *
 * Offsets apply on top of the sizes the session style already derives, so a fresh install renders
 * exactly as before. Changes are broadcast on [ChatStyleSettingsListener] so open sessions restyle
 * without reopening the tool window.
 */
@Service(Service.Level.APP)
@State(
    name = "KiloAppearanceSettings",
    storages = [Storage("kiloAppearanceSettings.xml")],
)
class KiloAppearanceSettingsService : PersistentStateComponent<KiloAppearanceSettingsService.State> {

    data class State(
        var chatFontFamily: String? = null,
        var responseOffset: Int? = null,
        var thinkingOffset: Int? = null,
        var toolOffset: Int? = null,
        var promptOffset: Int? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun spec(): ChatFontSpec = ChatFontSpec(
        family = state.chatFontFamily?.takeIf { it.isNotBlank() },
        response = pt(state.responseOffset),
        thinking = pt(state.thinkingOffset),
        tool = pt(state.toolOffset),
        prompt = pt(state.promptOffset),
    )

    fun update(spec: ChatFontSpec) {
        val norm = spec.normalized()
        state.chatFontFamily = norm.family
        state.responseOffset = hundredths(norm.response)
        state.thinkingOffset = hundredths(norm.thinking)
        state.toolOffset = hundredths(norm.tool)
        state.promptOffset = hundredths(norm.prompt)
    }

    /** Persists nothing extra — the platform saves [State] — and notifies open session surfaces. */
    fun publish() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(ChatStyleSettingsListener.TOPIC)
            .changed()
    }

    companion object {
        fun getInstance(): KiloAppearanceSettingsService = service()

        /** Current spec, or defaults when no application is running (unit tests, early startup). */
        fun current(): ChatFontSpec =
            ApplicationManager.getApplication()?.getService(KiloAppearanceSettingsService::class.java)
                ?.spec() ?: ChatFontSpec.DEFAULT

        private fun pt(hundredths: Int?): Float = (hundredths ?: 0) / 100f

        private fun hundredths(pt: Float): Int = Math.round(pt * 100)
    }
}
