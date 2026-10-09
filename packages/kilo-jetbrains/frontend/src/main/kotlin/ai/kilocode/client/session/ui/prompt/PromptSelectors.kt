package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.deluxe.KiloParameterPicker
import ai.kilocode.client.session.ui.ReasoningPicker
import ai.kilocode.client.session.ui.mode.ModePicker
import ai.kilocode.client.session.ui.model.ModelPicker

/**
 * Context resolved via [PromptDataKeys.SELECTORS] for the prompt bar's Ctrl+1/2/3/0 shortcuts
 * (cycle mode / model / reasoning effort, reset the model override). Implemented by [PromptPanel].
 */
interface PromptSelectors {
    val mode: ModePicker
    val model: ModelPicker
    val reasoning: ReasoningPicker

    /**
     * Deluxe session-header parameter control when the host panel carries one (null elsewhere).
     * When present, Ctrl+2 cycles the project's visible favorite presets instead of the hidden
     * upstream [model] picker (ADR-0003).
     */
    val parameters: KiloParameterPicker? get() = null

    /** True when a per-agent model override is active (the prompt bar shows the reset control). */
    val resettable: Boolean

    /** Clears the model override, same as clicking the prompt bar's reset control. */
    fun resetModel()
}
