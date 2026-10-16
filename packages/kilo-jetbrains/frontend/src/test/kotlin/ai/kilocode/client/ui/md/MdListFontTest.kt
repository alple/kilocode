package ai.kilocode.client.ui.md

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBHtmlPane
import java.awt.Dimension
import java.awt.Font
import javax.swing.text.View
import javax.swing.text.html.HTMLDocument

/**
 * Regression tests for markdown list sizing (KILO-15.4).
 *
 * Swing's HTML reader wraps the text inside `<li>` in an implied paragraph (`p-implied`). When no
 * stylesheet rule matches that tag, both the item text and the list marker — whose font
 * `StyleSheet.ListPainter` resolves from the item view's attributes — fall back to Swing's
 * hardcoded 12pt base font and render visibly smaller than the surrounding prose.
 */
@Suppress("UnstableApiUsage")
class MdListFontTest : BasePlatformTestCase() {

    fun `test list lines render at prose size`() {
        build(size = 15) { md ->
            md.set("Prose line alpha bravo.\n\n- bullet one plain\n- bullet two plain\n\n1. number one plain\n")
            withPane(md) { pane, doc ->
                val text = doc.getText(0, doc.length)
                val prose = lineHeight(pane, text, "Prose line alpha bravo.")
                val bullet = lineHeight(pane, text, "bullet one plain")
                val number = lineHeight(pane, text, "number one plain")
                assertEquals("bullet line must render at prose size", prose, bullet)
                assertEquals("numbered line must render at prose size", prose, number)
            }
        }
    }

    fun `test list item resolves the chat font`() {
        val style = SessionEditorStyle.create(size = 15)
        build(style = style) { md ->
            md.set("Prose line alpha bravo.\n\n- bullet one plain\n\n1. number one plain\n")
            withPane(md) { pane, doc ->
                val opts = MdCommon.defaults(style)
                for (list in listOf("ul", "ol")) {
                    val implied = impliedView(pane, doc, list)
                    val font = doc.getFont(implied.attributes)
                    assertEquals("implied paragraph must resolve the chat size", opts.font.size, font.size)
                    assertEquals("implied paragraph must resolve the chat family", opts.font.name, font.name)
                }
            }
        }
    }

    fun `test italic reasoning block renders list text italic at the thinking size`() {
        val style = SessionEditorStyle.create(size = 15)
        build(style = style) { md ->
            md.font = style.thinkingFont.deriveFont(Font.ITALIC)
            md.set("- bullet one plain\n")
            withPane(md) { pane, doc ->
                val font = doc.getFont(impliedView(pane, doc, "ul").attributes)
                val opts = MdCommon.defaults(style)
                assertEquals(opts.font.size, font.size)
                assertTrue("reasoning list text must stay italic", font.isItalic)
            }
        }
    }

    private fun build(size: Int = -1, style: SessionEditorStyle? = null, block: (MdView) -> Unit) {
        val md = MdViewFactory.hybrid(style ?: SessionEditorStyle.create(size = size))
        try {
            block(md)
        } finally {
            Disposer.dispose(md)
        }
    }

    private fun withPane(md: MdView, block: (JBHtmlPane, HTMLDocument) -> Unit) {
        val pane = panes(md).single()
        pane.setSize(Dimension(500, 2000))
        block(pane, pane.document as HTMLDocument)
    }

    private fun panes(md: MdView): List<JBHtmlPane> {
        val out = mutableListOf<JBHtmlPane>()
        walk(md.component) { if (it is JBHtmlPane) out.add(it) }
        return out
    }

    private fun walk(c: java.awt.Component, visit: (java.awt.Component) -> Unit) {
        visit(c)
        if (c is java.awt.Container) {
            val kids: Array<java.awt.Component> = c.components
            for (child in kids) walk(child, visit)
        }
    }

    private fun lineHeight(pane: JBHtmlPane, text: String, needle: String): Float {
        val at = text.indexOf(needle)
        val rect = pane.modelToView2D(at)?.bounds ?: error("no model position for '$needle'")
        return rect.height.toFloat()
    }

    /** Implied-paragraph view of the first `<li>` inside the `<[list]>` element. */
    private fun impliedView(pane: JBHtmlPane, doc: HTMLDocument, list: String): View {
        val stack = ArrayDeque<View>()
        stack.add(pane.ui.getRootView(pane))
        while (stack.isNotEmpty()) {
            val view = stack.removeFirst()
            val element = view.element
            if (element.name == "p-implied" && element.parentElement?.name == "li" && element.parentElement.parentElement?.name == list) {
                return view
            }
            for (i in 0 until view.viewCount) stack.add(view.getView(i))
        }
        error("no implied paragraph view under <$list>")
    }
}
