package ai.kilocode.client.deluxe

import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelPickerRow
import com.intellij.icons.AllIcons
import com.intellij.ui.CollectionListModel
import com.intellij.ui.NewUI
import com.intellij.ui.components.JBList
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.ComponentOrientation
import java.awt.Point
import java.awt.Rectangle

/**
 * Covers the Deluxe session popup renderer (ADR-0003): model rows keep the upstream look with the
 * star repurposed as the labeled-preset toggle, preset rows stay star-free, and the star click zone
 * rides the trailing edge in both orientations.
 */
@Suppress("UnstableApiUsage")
class KiloModelPopupRendererTest : BasePlatformTestCase() {

    private fun item(id: String, provider: String = "kilo", free: Boolean = false, training: Boolean = false) =
        ModelPicker.Item(id, id, provider, id, free = free, mayTrainOnYourPrompts = training)

    private fun modelRow(item: ModelPicker.Item, section: String? = "Kilo") =
        KiloModelRow.Model(ModelPickerRow(item, section, favorite = false))

    private fun presetRow(
        name: String = "Fast",
        provider: String = "openrouter",
        modelId: String = "z-ai/glm-5.3-flash",
        routeTag: String? = null,
        variant: String? = null,
    ) = KiloModelRow.Preset(
        KiloFavoritePreset(
            id = name.lowercase(),
            name = name,
            labels = listOf("p"),
            provider = provider,
            modelId = modelId,
            routeTag = routeTag,
            variant = variant,
        ),
    )

    private fun renderer(
        rows: List<KiloModelRow>,
        active: ModelPicker.Item? = null,
        route: String? = null,
        variant: String? = null,
        starred: Set<String> = emptySet(),
        locked: Boolean = false,
    ): KiloModelPopupRenderer {
        val model = CollectionListModel(rows)
        return KiloModelPopupRenderer(model, { active }, { route }, { variant }, { starred }, { locked })
    }

    fun `test model rows show the star as preset visibility`() {
        val row = modelRow(item("auto"))
        val r = renderer(listOf(row), starred = emptySet())
        val list = JBList(CollectionListModel(listOf(row)))

        r.getListCellRendererComponent(list, row, 0, false, false)
        assertSame(EmptyIcon.ICON_16, r.starIcon())

        val starred = renderer(listOf(row), starred = setOf("kilo/auto"))
        starred.getListCellRendererComponent(list, row, 0, false, false)
        assertSame(AllIcons.Nodes.Favorite, starred.starIcon())

        val hovered = renderer(listOf(row))
        hovered.getListCellRendererComponent(list, row, 0, true, false)
        assertSame(AllIcons.Nodes.NotFavoriteOnHover, hovered.starIcon())
    }

    fun `test model rows keep the free badge and data warning`() {
        val free = modelRow(item("auto", free = true))
        val r = renderer(listOf(free))
        val list = JBList(CollectionListModel(listOf(free)))
        r.getListCellRendererComponent(list, free, 0, false, false)
        assertTrue(r.badgeVisible())
        assertFalse(r.warningVisible())

        val paid = modelRow(item("paid", training = true))
        val w = renderer(listOf(paid))
        val paidList = JBList(CollectionListModel(listOf(paid)))
        w.getListCellRendererComponent(paidList, paid, 0, false, false)
        assertFalse(w.badgeVisible())
        assertTrue(w.warningVisible())
        assertEquals("Data may be used for training", w.warningTooltip())
    }

    fun `test preset rows stay star-free and keep badges off`() {
        val row = presetRow("Fast", routeTag = "parasail", variant = "high")
        val r = renderer(listOf(row))
        val list = JBList(CollectionListModel(listOf(row)))

        r.getListCellRendererComponent(list, row, 0, true, false)

        assertSame("Presets toggle their own definition, not per-model stars", EmptyIcon.ICON_16, r.starIcon())
        assertFalse(r.badgeVisible())
        assertFalse(r.byokVisible())
        assertFalse(r.warningVisible())
    }

    fun `test empty rows stay muted without affordances`() {
        val row = KiloModelRow.Empty("no favorites")
        val r = renderer(listOf(row), starred = setOf("kilo/auto"))
        val list = JBList(CollectionListModel(listOf(row)))

        r.getListCellRendererComponent(list, row, 0, true, false)

        assertSame(EmptyIcon.ICON_16, r.starIcon())
        assertFalse(r.badgeVisible())
        assertFalse(r.byokVisible())
        assertFalse(r.warningVisible())
    }

    fun `test star click zone uses the trailing edge in both orientations`() {
        val list = JBList(listOf<KiloModelRow>())
        val bounds = Rectangle(10, 0, 100, 20)
        val inset = if (!NewUI.isEnabled()) {
            0
        } else {
            val inner = JBUI.CurrentTheme.Popup.Selection.innerInsets()
            val edge = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
            edge + (if (list.componentOrientation.isLeftToRight) inner.right else inner.left)
        }

        assertTrue(KiloModelPopupRenderer.isStarClick(list, bounds, Point(100 - inset, 10)))
        assertFalse(KiloModelPopupRenderer.isStarClick(list, bounds, Point(20, 10)))

        list.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        val rtl = if (!NewUI.isEnabled()) {
            0
        } else {
            val inner = JBUI.CurrentTheme.Popup.Selection.innerInsets()
            val edge = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
            edge + inner.left
        }
        assertTrue(KiloModelPopupRenderer.isStarClick(list, bounds, Point(20 + rtl, 10)))
        assertFalse(KiloModelPopupRenderer.isStarClick(list, bounds, Point(100, 10)))
    }
}
