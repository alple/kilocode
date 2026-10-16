package ai.kilocode.client.session.ui

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.settings.appearance.ChatFontSpec
import ai.kilocode.client.ui.UiStyle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font
import kotlin.math.roundToInt

@Suppress("UnstableApiUsage")
class SessionEditorStyleTest : BasePlatformTestCase() {

    fun `test transcript font uses ui family and editor size`() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val style = SessionEditorStyle.current()
        val font = style.transcriptFont
        val size = UISettingsUtils.getInstance()
            .scaleFontSize(scheme.editorFontSize.toFloat())
            .roundToInt()

        assertEquals(UiStyle.Fonts.regular().name, font.name)
        assertEquals(size, font.size)
        assertEquals(scheme.defaultForeground, style.editorForeground)
        assertEquals(scheme.defaultBackground, style.editorBackground)
        assertEquals(Font.PLAIN, font.style)
    }

    fun `test current scales editor size with ide scale`() {
        val settings = UISettings.getInstance()
        val original = settings.ideScale
        try {
            val base = EditorColorsManager.getInstance().globalScheme.editorFontSize
            settings.ideScale = 1.5f
            settings.fireUISettingsChanged()

            val style = SessionEditorStyle.current()

            assertTrue(
                "transcript should grow with ide scale (base=$base, got=${style.transcriptFont.size})",
                style.transcriptFont.size > base,
            )
            assertEquals(style.editorSize, style.transcriptFont.size)
        } finally {
            settings.ideScale = original
            settings.fireUISettingsChanged()
        }
    }

    fun `test editor font uses editor family and size`() {
        val style = SessionEditorStyle.current()
        val font = style.editorFont

        assertEquals(style.editorFamily, font.name)
        assertEquals(style.editorSize, font.size)
        assertEquals(Font.PLAIN, font.style)
    }

    fun `test bold transcript font uses ui family and editor size`() {
        val style = SessionEditorStyle.current()
        val font = style.boldEditorFont

        assertEquals(UiStyle.Fonts.regular().name, font.name)
        assertEquals(style.editorSize, font.size)
        assertTrue(font.isBold)
    }

    fun `test small transcript font uses ui family with smaller editor-derived size`() {
        val style = SessionEditorStyle.current()
        val font = style.smallEditorFont

        assertEquals(UiStyle.Fonts.small().name, font.name)
        assertTrue(font.size < style.editorSize)
    }

    fun `test custom style keeps editor fields from supplied baseline`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)

        assertEquals("Courier New", style.editorFamily)
        assertEquals(22, style.editorSize)
        assertEquals("Courier New", style.editorFont.name)
        assertEquals(UiStyle.Fonts.regular().name, style.transcriptFont.name)
        assertEquals(22, style.transcriptFont.size)
        assertEquals(UiStyle.Fonts.regular().name, style.boldEditorFont.name)
        assertEquals(22, style.boldEditorFont.size)
        assertTrue(style.boldEditorFont.isBold)
        assertTrue(style.smallEditorFont.size < style.editorSize)
    }

    // --- UI fonts come from UiStyle.Fonts, NOT from the editor ---

    fun `test headerFont equals UiStyle Fonts header`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(UiStyle.Fonts.header(), style.headerFont)
    }

    fun `test secondary text font equals UiStyle Fonts regular`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(UiStyle.Fonts.regular(), SessionUiStyle.Text.Secondary.font(style))
    }

    fun `test regularFont equals UiStyle Fonts regular`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(UiStyle.Fonts.regular(), style.regularFont)
    }

    fun `test boldFont equals UiStyle Fonts bold`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(UiStyle.Fonts.bold(), style.boldFont)
        assertTrue(style.boldFont.isBold)
    }

    fun `test smallFont equals UiStyle Fonts small`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(UiStyle.Fonts.small(), style.smallFont)
    }

    fun `test ui fonts do not use editor font family`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)

        assertFalse("headerFont should not use editor font family", style.headerFont.name == "Courier New")
        assertFalse("secondary text font should not use editor font family", SessionUiStyle.Text.Secondary.font(style).name == "Courier New")
        assertFalse("regularFont should not use editor font family", style.regularFont.name == "Courier New")
        assertFalse("boldFont should not use editor font family", style.boldFont.name == "Courier New")
        assertFalse("smallFont should not use editor font family", style.smallFont.name == "Courier New")
    }

    fun `test transcript editor styling ignores disposed editor`() {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(""), project) as EditorEx

        factory.releaseEditor(editor)

        SessionEditorStyle.current().applyInputToEditor(editor)
    }

    // --- chat font settings: per-group offsets and family override ---

    fun `test thinking font matches transcript text at default offsets`() {
        val base = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(base.transcriptFont.size, base.thinkingFont.size)
        assertEquals(UiStyle.Fonts.regular().name, base.thinkingFont.name)
        assertEquals(UiStyle.Fonts.regular().name, base.promptFont.name)
        assertEquals(base.transcriptFont.size, base.promptFont.size)
    }

    fun `test per-group offsets shift only their group`() {
        val base = SessionEditorStyle.create(family = "Courier New", size = 22)
        val shifted = SessionEditorStyle.create(
            family = "Courier New",
            size = 22,
            chat = ChatFontSpec(response = 1f, thinking = 2f, tool = 3f, prompt = 4f),
        )
        assertEquals(base.editorSize + 1, shifted.transcriptFont.size)
        assertEquals(base.editorSize + 2, shifted.thinkingFont.size)
        assertTrue(shifted.thinkingFont.size > shifted.smallEditorFont.size)
        assertEquals(base.editorSize + 3, shifted.editorFont.size)
        assertEquals(base.editorSize + 3, shifted.codeSize)
        assertEquals(base.editorSize + 4, shifted.promptFont.size)
        assertEquals(base.editorSize, shifted.boldEditorFont.size)
    }

    fun `test chat family override replaces proportional chat text family only`() {
        val shifted = SessionEditorStyle.create(
            family = "Courier New",
            size = 22,
            chat = ChatFontSpec(family = "Dialog"),
        )
        assertEquals("Dialog", shifted.transcriptFont.name)
        assertEquals("Dialog", shifted.thinkingFont.name)
        assertEquals("Dialog", shifted.promptFont.name)
        assertEquals("Courier New", shifted.editorFont.name)
        assertEquals("Courier New", shifted.editorFamily)
        assertEquals(UiStyle.Fonts.regular().name, shifted.regularFont.name)
        assertEquals(UiStyle.Fonts.small().name, shifted.smallFont.name)
    }

    fun `test equal offsets render equal nominal sizes across chat groups`() {
        val shifted = SessionEditorStyle.create(
            family = "Courier New",
            size = 22,
            chat = ChatFontSpec(response = 2f, thinking = 2f, tool = 2f, prompt = 2f),
        )
        assertEquals(shifted.transcriptFont.size, shifted.thinkingFont.size)
        assertEquals(shifted.transcriptFont.size, shifted.promptFont.size)
        assertEquals(shifted.transcriptFont.size, shifted.editorFont.size)
        assertEquals(shifted.transcriptFont.size, shifted.codeSize)
    }

    fun `test default spec preserves derived sizes`() {
        val base = SessionEditorStyle.create(family = "Courier New", size = 22)
        val withDefaults = SessionEditorStyle.create(family = "Courier New", size = 22, chat = ChatFontSpec.DEFAULT)
        assertEquals(22, withDefaults.codeSize)
        assertEquals(base.transcriptFont.size, withDefaults.transcriptFont.size)
        assertEquals(base.editorFont.size, withDefaults.editorFont.size)
        assertEquals(base.thinkingFont.size, withDefaults.thinkingFont.size)
        assertEquals(base.promptFont.size, withDefaults.promptFont.size)
    }

    fun `test tool header fonts match fixed fonts at zero tool offset`() {
        val style = SessionEditorStyle.create(family = "Courier New", size = 22)
        assertEquals(style.boldEditorFont, style.toolTitleFont)
        assertTrue(style.toolTitleFont.isBold)
        assertEquals(style.smallEditorFont, style.toolSubtitleFont)
        assertFalse(style.toolSubtitleFont.isBold)
    }

    fun `test tool header fonts follow the tool offset`() {
        val base = SessionEditorStyle.create(family = "Courier New", size = 22)
        val shifted = SessionEditorStyle.create(family = "Courier New", size = 22, chat = ChatFontSpec(tool = 2f))
        assertEquals(base.editorSize + 2, shifted.toolTitleFont.size)
        assertTrue(shifted.toolTitleFont.isBold)
        assertTrue("subtitle must grow with the tool offset", shifted.toolSubtitleFont.size > base.smallEditorFont.size)
        assertTrue(shifted.toolSubtitleFont.size < shifted.toolTitleFont.size)
    }

    fun `test applyToEditor skips redundant scheme reinit for the same snapshot`() {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("a\nb\nc\n"), project) as EditorEx
        try {
            val style = SessionEditorStyle.current()
            style.applyToEditor(editor)
            // setColorsScheme wraps the scheme in a fresh delegate on every call, so an unchanged
            // colorsScheme identity proves the redundant second apply was skipped (no reinit).
            val applied = editor.colorsScheme
            style.applyToEditor(editor)
            assertSame(applied, editor.colorsScheme)

            // A different snapshot instance must still re-apply and swap the delegate.
            SessionEditorStyle.current().applyToEditor(editor)
            assertNotSame(applied, editor.colorsScheme)
        } finally {
            factory.releaseEditor(editor)
        }
    }
}
