package ai.kilocode.client.settings.appearance

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.settings.base.SettingsRow
import ai.kilocode.client.settings.base.SettingsRows
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.JSpinner.NumberEditor

/** Chat font settings: one family picker, per-group size offsets, and a live preview. */
internal class AppearanceSettingsUi(
    initial: ChatFontSpec = KiloAppearanceSettingsService.getInstance().spec(),
) : JBPanel<AppearanceSettingsUi>(BorderLayout()) {

    private val familyModel = DefaultComboBoxModel<String>()

    private val family = ComboBox(familyModel)

    private val response = sizeSpinner()

    private val thinking = sizeSpinner()

    private val tool = sizeSpinner()

    private val prompt = sizeSpinner()

    private val resets = mutableMapOf<JSpinner, HoverIcon>()

    private val previewResponse = JBLabel(KiloBundle.message("settings.appearance.preview.response"))

    private val previewThinking = JBLabel(KiloBundle.message("settings.appearance.preview.thinking"))

    private val previewTool = JBLabel(KiloBundle.message("settings.appearance.preview.tool"))

    private val previewPrompt = JBLabel(KiloBundle.message("settings.appearance.preview.prompt"))

    private val preview = JBPanel<Nothing>(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(UiStyle.Colors.contentBorder()),
            JBUI.Borders.empty(UiStyle.Gap.pad()),
        )
        add(
            Stack.vertical(UiStyle.Gap.sm())
                .next(previewResponse)
                .next(previewThinking)
                .next(previewTool)
                .next(previewPrompt),
            BorderLayout.CENTER,
        )
    }

    private var saved = initial.normalized()

    init {
        resetForm()
        family.addActionListener { refreshPreview() }
        listOf(response, thinking, tool, prompt).forEach { spinner ->
            spinner.addChangeListener { refreshPreview() }
        }

        val rows = SettingsRows().apply {
            border = JBUI.Borders.empty(UiStyle.Gap.pad(), UiStyle.Gap.lg())
            row(SettingsRow(
                KiloBundle.message("settings.appearance.family.title"),
                KiloBundle.message("settings.appearance.family.description"),
                family,
            ))
            row(TitledSeparator(KiloBundle.message("settings.appearance.size.title")))
            row(SettingsRow(
                KiloBundle.message("settings.appearance.size.response.title"),
                KiloBundle.message("settings.appearance.size.response.description"),
                offsetRow(response, "response"),
            ))
            row(SettingsRow(
                KiloBundle.message("settings.appearance.size.thinking.title"),
                KiloBundle.message("settings.appearance.size.thinking.description"),
                offsetRow(thinking, "thinking"),
            ))
            row(SettingsRow(
                KiloBundle.message("settings.appearance.size.tool.title"),
                KiloBundle.message("settings.appearance.size.tool.description"),
                offsetRow(tool, "tool"),
            ))
            row(SettingsRow(
                KiloBundle.message("settings.appearance.size.prompt.title"),
                KiloBundle.message("settings.appearance.size.prompt.description"),
                offsetRow(prompt, "prompt"),
            ))
            row(TitledSeparator(KiloBundle.message("settings.appearance.preview.title")))
            row(preview)
        }
        add(rows, BorderLayout.CENTER)
        refreshPreview()
    }

    /** Draft spec from the controls, clamped and rounded to the stored precision. */
    @RequiresEdt
    fun value(): ChatFontSpec = ChatFontSpec(
        family = family(),
        response = pt(response),
        thinking = pt(thinking),
        tool = pt(tool),
        prompt = pt(prompt),
    )


    @RequiresEdt
    fun modified(): Boolean = value() != saved

    @RequiresEdt
    fun resetForm() {
        rebuildFamilies(saved.family)
        family.selectedIndex = if (saved.family == null) FOLLOW_IDE_INDEX else familyModel.getIndexOf(saved.family)
        response.value = saved.response.toDouble()
        thinking.value = saved.thinking.toDouble()
        tool.value = saved.tool.toDouble()
        prompt.value = saved.prompt.toDouble()
        refreshPreview()
    }

    /** Freezes the draft as the new baseline after a successful apply. */
    @RequiresEdt
    fun sync() {
        saved = value()
        resetForm()
    }

    private fun family(): String? =
        family.selectedIndex.takeIf { it != FOLLOW_IDE_INDEX }?.let(familyModel::getElementAt)

    private fun pt(spinner: JSpinner): Float = ChatFontOffset.clamp((spinner.value as Number).toDouble()).toFloat()

    private fun refreshPreview() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val style = SessionEditorStyle.create(
            scheme = scheme,
            family = scheme.editorFontName,
            size = SessionEditorStyle.baseSize(scheme),
            chat = value(),
        )
        previewResponse.font = style.transcriptFont
        previewThinking.font = style.thinkingFont.deriveFont(Font.ITALIC)
        previewTool.font = style.editorFont
        previewPrompt.font = style.promptFont
        for ((spinner, reset) in resets) reset.isEnabled = pt(spinner) != 0f
    }

    /** Spinner plus a reset-to-zero icon; the icon is enabled only while the offset is non-zero. */
    private fun offsetRow(spinner: JSpinner, group: String): JComponent {
        val reset = HoverIcon().apply {
            icon = AllIcons.Actions.Rollback
            toolTipText = KiloBundle.message("settings.appearance.size.reset")
            name = "kilo.appearance.reset.$group"
            addActionListener { spinner.value = 0.0 }
        }
        resets[spinner] = reset
        return Stack.horizontal(UiStyle.Gap.sm())
            .next(spinner)
            .next(reset)
    }

    private fun rebuildFamilies(extra: String?) {
        familyModel.removeAllElements()
        familyModel.addElement(KiloBundle.message("settings.appearance.family.followIde"))
        families(extra).forEach(familyModel::addElement)
    }

    companion object {
        private const val FOLLOW_IDE_INDEX = 0

        private fun families(extra: String?): List<String> =
            (GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList() + listOfNotNull(extra))
                .distinct()
                .sorted()

        private fun sizeSpinner(): JSpinner {
            val spinner = JSpinner(
                SpinnerNumberModel(0.0, ChatFontOffset.MIN, ChatFontOffset.MAX, ChatFontOffset.STEP),
            )
            spinner.editor = NumberEditor(spinner, "0.0")
            return spinner
        }
    }
}
