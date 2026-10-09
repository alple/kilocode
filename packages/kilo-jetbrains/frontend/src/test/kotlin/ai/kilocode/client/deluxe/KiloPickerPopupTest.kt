package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.util.edtWait
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

/**
 * Covers the pinned Favorites bar of the Deluxe session popup (ADR-0003): the bar always shows the
 * section caption with the edit-favorites button on its right, stacked under the search header, and
 * the button fires the host's management-window entry point.
 */
class KiloPickerPopupTest : BasePlatformTestCase() {

    fun `test the favorites bar carries the caption and the edit button on its right`() = edt {
        val picker = KiloParameterPicker()
        var edited = false
        picker.onEditFavorites = { edited = true }

        val bar = favoritesBar(picker)
        val icons = components(bar).filterIsInstance<HoverIcon>()
        val separator = components(bar).filterIsInstance<com.intellij.ui.GroupHeaderSeparator>().single()

        assertEquals(1, icons.size)
        assertEquals(KiloBundle.message("model.picker.favorites"), separator.caption)
        assertTrue("The edit icon sits on the bar's right (EAST)", bar.getComponent(1) is HoverIcon)
        assertFalse("The edit button starts inert", edited)

        icons.single().doClick()

        assertTrue("The edit button fires the host's management-window entry point", edited)
    }

    fun `test the shell stacks the search header and the favorites bar in the head`() = edt {
        val picker = KiloParameterPicker()
        val bar = favoritesBar(picker)
        val head = header(picker, bar)

        assertEquals("Head holds exactly the search row and the pinned bar", 2, head.componentCount)
        assertTrue("The search header comes first", components(head.getComponent(0)).any { it is SearchTextField })
        assertTrue("The pinned Favorites bar is stacked below the search header", head.getComponent(1) === bar)
    }

    private fun favoritesBar(picker: KiloParameterPicker): JPanel {
        val method = KiloParameterPicker::class.java.getDeclaredMethod("favoritesBar")
        method.isAccessible = true
        return method.invoke(picker) as JPanel
    }

    private fun header(picker: KiloParameterPicker, favorites: JPanel): Container {
        val popup = KiloPickerPopup<String>(
            anchor = picker,
            placement = KiloPickerPopup.Placement.ABOVE,
            rows = { emptyList() },
            model = CollectionListModel(listOf("row")),
            renderer = EmptyRenderer(CollectionListModel(listOf("row"))),
            mode = KiloPickerPopup.Mode.Single,
            onPrimary = {},
            search = true,
            favorites = favorites,
        )
        val method = KiloPickerPopup::class.java.getDeclaredMethod("header")
        method.isAccessible = true
        return method.invoke(popup) as Container
    }

    private fun components(component: Component): List<Component> {
        val out = mutableListOf<Component>()
        fun visit(c: Component) {
            out += c
            if (c is Container) c.components.forEach { visit(it) }
        }
        visit(component)
        return out
    }

    private fun <T> edt(block: () -> T): T = edtWait(block)

    private class EmptyRenderer(model: CollectionListModel<String>) : KiloPickerListRenderer<String>(
        model = model,
        checked = { false },
        sectionTitle = { _, _ -> null },
        content = JBLabel(),
    ) {
        override fun update(value: String, index: Int, selected: Boolean, focused: Boolean, foreground: java.awt.Color, weak: java.awt.Color) {
            (content as JBLabel).text = value
        }
    }
}
