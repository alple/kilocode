package ai.kilocode.client.session.ui.style

import ai.kilocode.client.settings.appearance.ChatFontSpec
import ai.kilocode.client.settings.appearance.KiloAppearanceSettingsService
import ai.kilocode.client.ui.UiStyle
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import javax.swing.ScrollPaneConstants
import kotlin.math.roundToInt

/**
 * Immutable snapshot of editor-derived fonts and colors for transcript components.
 *
 * Session UI uses this instead of reading editor globals in every component so font and color changes can be applied
 * consistently through [SessionEditorStyleTarget].
 *
 * Editor-specific fields ([editorFont], [editorFamily], [editorSize], [codeSize], [editorForeground],
 * [editorBackground]) are derived from the active editor color scheme and are used for code/editor-rendered content;
 * [codeSize] is the editor size with the tool/code offset applied.
 *
 * UI font fields ([transcriptFont], [thinkingFont], [promptFont], [smallEditorFont], [boldEditorFont],
 * [toolTitleFont], [toolSubtitleFont], [headerFont], [regularFont], [boldFont], [smallFont]) come from [UiStyle.Fonts]
 * and follow standard platform typography. Transcript fonts use the editor size so the session body tracks editor
 * zoom without adopting the editor family. Chat font settings apply per-group offsets on top: [transcriptFont]
 * carries the chat-response offset, [thinkingFont] the thinking offset, [promptFont] the prompt offset; a chat
 * family override replaces the family of those three only. [toolTitleFont] and [toolSubtitleFont] size tool-card
 * header chrome (title bold, subtitle small) at the tool group size, so the Tool output & code offset steers the
 * whole card header together with its body (KILO-15.4). [applyToEditor] sizes embedded code editors with
 * [codeSize] and [applyInputToField]/[applyPromptToEditor] style user-input editors with [promptFont].
 */
data class SessionEditorStyle(
    val editorScheme: EditorColorsScheme,
    val editorFamily: String,
    val editorSize: Int,
    val editorForeground: Color,
    val editorBackground: Color,
    val editorFont: Font,
    val transcriptFont: Font,
    val thinkingFont: Font,
    val promptFont: Font,
    val smallEditorFont: Font,
    val boldEditorFont: Font,
    val toolTitleFont: Font,
    val toolSubtitleFont: Font,
    val headerFont: Font,
    val regularFont: Font,
    val boldFont: Font,
    val smallFont: Font,
    val codeSize: Int,
) {
    /** Apply this snapshot to embedded IntelliJ editor components used by session UI. */
    fun applyToEditor(editor: EditorEx) {
        try {
            if (editor.isDisposed) return
            // setColorsScheme always runs a full reinitSettings (gutter annotation sizing walks every
            // document line), so skip it when this exact style snapshot was already applied to this
            // editor. Snapshots are shared per session and recreated only on a theme change, so an
            // identity check is enough and avoids repeated O(lines) reinit on redundant applyStyle.
            if (editor.getUserData(APPLIED) === this) return
            editor.setColorsScheme(editorScheme)
            editor.setFontSize(codeSize)
            editor.putUserData(APPLIED, this)
        } catch (err: RuntimeException) {
            if (err.javaClass.name != "com.intellij.openapi.util.TraceableDisposable\$DisposalException") throw err
        }
    }

    /** Apply user-input typography to an embedded editor (prompt, question answers). */
    fun applyInputToEditor(editor: EditorEx) {
        try {
            if (editor.isDisposed) return
            applyToEditor(editor)
            if (editor.isDisposed) return
            editor.colorsScheme.setEditorFontName(promptFont.fontName)
            editor.colorsScheme.setEditorFontSize(promptFont.size)
        } catch (err: RuntimeException) {
            if (err.javaClass.name != "com.intellij.openapi.util.TraceableDisposable\$DisposalException") throw err
        }
    }

    /** Apply user-input typography to an editor text field and its embedded editor when available. */
    fun applyInputToField(field: EditorTextField) {
        field.font = promptFont
        field.getEditor(false)?.let(::applyInputToEditor)
    }

    /** Apply the visible prompt-input text styling to embedded session editor components. */
    fun applyPromptToEditor(editor: EditorEx, background: Color = editorBackground) {
        if (editor.isDisposed) return
        applyInputToEditor(editor)
        if (editor.isDisposed) return
        editor.setBorder(JBUI.Borders.empty())
        editor.scrollPane.border = JBUI.Borders.empty()
        editor.scrollPane.viewportBorder = JBUI.Borders.empty(
            0,
            JBUI.scale(SessionUiStyle.View.Prompt.EDITOR_HORIZONTAL_INSET),
            0,
            JBUI.scale(SessionUiStyle.View.Prompt.EDITOR_HORIZONTAL_INSET),
        )
        editor.backgroundColor = background
        editor.component.background = background
        editor.contentComponent.background = background
        editor.scrollPane.background = background
        editor.scrollPane.viewport.background = background
        editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        editor.scrollPane.revalidate()
        editor.scrollPane.repaint()
    }

    companion object {
        /** Marks the last style snapshot applied to an editor so [applyToEditor] can skip redundant reinit. */
        private val APPLIED = Key.create<SessionEditorStyle>("kilo.session.editor.style")

        /** Editor font size with the IDE zoom applied; the base every chat font derivation starts from. */
        fun baseSize(scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme): Int =
            UISettingsUtils.getInstance()
                .scaleFontSize(scheme.editorFontSize.toFloat())
                .roundToInt()
                .coerceAtLeast(1)

        /** Builds a style snapshot from the current global editor color scheme and chat appearance settings. */
        fun current(): SessionEditorStyle {
            val scheme = EditorColorsManager.getInstance().globalScheme
            return create(scheme, scheme.editorFontName, baseSize(scheme), KiloAppearanceSettingsService.current())
        }

        internal fun create(
            scheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme,
            family: String = scheme.editorFontName,
            size: Int = scheme.editorFontSize,
            chat: ChatFontSpec = ChatFontSpec.DEFAULT,
        ): SessionEditorStyle {
            val small = scaledEditorSize(size, JBFont.small())
            val toolSize = (size + chat.tool).roundToInt().coerceAtLeast(1)
            return SessionEditorStyle(
                editorScheme = scheme,
                editorFamily = family,
                editorSize = size,
                editorForeground = scheme.defaultForeground,
                editorBackground = scheme.defaultBackground,
                // Tool output & code: the editor family at the base size shifted by the tool offset. A plain
                // Font on purpose — embedded editors re-read scheme fonts, and labels/areas keep the exact
                // fractional size through getSize2D.
                editorFont = Font(family, Font.PLAIN, size).deriveFont(Font.PLAIN, (size + chat.tool).coerceAtLeast(1f)),
                transcriptFont = chatTextFont(UiStyle.Fonts.regular(), size + chat.response, chat.family),
                // Reasoning body text renders at the chat base like prose; the label-ratio shrink made
                // reasoning bullet lists read visibly smaller than the response text (KILO-15.1).
                thinkingFont = chatTextFont(UiStyle.Fonts.regular(), size + chat.thinking, chat.family),
                promptFont = chatTextFont(UiStyle.Fonts.regular(), size + chat.prompt, chat.family),
                smallEditorFont = uiFont(UiStyle.Fonts.small(), Font.PLAIN, small),
                boldEditorFont = uiFont(UiStyle.Fonts.regular(), Font.BOLD, size),
                // Tool-card header chrome follows the tool group (KILO-15.4): a bold title and the small
                // subtitle at the tool base size, so the Tool output & code offset steers the whole header
                // together with the body. At offset 0 both match the fixed [boldEditorFont]/[smallEditorFont].
                toolTitleFont = uiFont(UiStyle.Fonts.regular(), Font.BOLD, toolSize),
                toolSubtitleFont = uiFont(UiStyle.Fonts.small(), Font.PLAIN, scaledEditorSize(toolSize, JBFont.small())),
                headerFont = UiStyle.Fonts.header(),
                regularFont = UiStyle.Fonts.regular(),
                boldFont = UiStyle.Fonts.bold(),
                smallFont = UiStyle.Fonts.small(),
                codeSize = toolSize,
            )
        }

        private fun scaledEditorSize(size: Int, font: Font): Int {
            val base = com.intellij.util.ui.JBUI.Fonts.label().size.coerceAtLeast(1)
            val ratio = font.size.toFloat() / base
            return (size * ratio).roundToInt().coerceAtLeast(1)
        }

        private fun uiFont(font: Font, style: Int, size: Int): Font = font.deriveFont(style, size.toFloat())

        /**
         * Proportional chat text at [size] points. Keeps the platform JBFont (which rescales with the IDE
         * font) unless a chat family override pins the family — a pinned family trades that automatic
         * rescale for the explicit choice until the next style refresh.
         */
        private fun chatTextFont(base: Font, size: Float, chatFamily: String?): Font =
            chatFamily?.let { Font(it, Font.PLAIN, 1).deriveFont(Font.PLAIN, size.coerceAtLeast(1f)) }
                ?: base.deriveFont(Font.PLAIN, size.coerceAtLeast(1f))
    }
}

/** Session component contract for applying a refreshed [SessionEditorStyle] without rebuilding Swing nodes. */
interface SessionEditorStyleTarget {
    fun applyStyle(style: SessionEditorStyle)
}
