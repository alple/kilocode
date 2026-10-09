package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelPickerRow
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.FilledBadgeIcon
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val STAR_CLICK_AREA_WIDTH = 32

/** Row check state: the active model key, or a preset matching the session's full trio. */
private fun checkedRow(row: KiloModelRow, active: ModelPicker.Item?, route: String?, variant: String?): Boolean {
    return when (row) {
        is KiloModelRow.Model -> row.row.key != null && row.row.key == active?.key
        is KiloModelRow.Preset -> active != null &&
            row.preset.provider == active.provider &&
            row.preset.modelId == active.id &&
            row.preset.routeTag == route &&
            row.preset.variant == variant
        is KiloModelRow.Lock, is KiloModelRow.Empty -> false
    }
}

/**
 * Renderer for the Deluxe session popup (ADR-0003): preset rows show the preset name plus its
 * `provider · model · route · effort` summary, model rows keep the upstream look (provider/model
 * text, free/BYOK badges, data-collection notice) with the star repurposed as the labeled-preset
 * toggle, and the empty state stays muted. While the parameter trio is frozen the pickable rows
 * render weak — only the unlock row keeps full strength.
 */
internal class KiloModelPopupRenderer private constructor(
    model: CollectionListModel<KiloModelRow>,
    private val active: () -> ModelPicker.Item?,
    private val activeRoute: () -> String?,
    private val activeVariant: () -> String?,
    private val starred: () -> Set<String>,
    private val locked: () -> Boolean,
    private val parts: Parts,
) : KiloPickerListRenderer<KiloModelRow>(
    model = model,
    checked = { row -> checkedRow(row, active(), activeRoute(), activeVariant()) },
    sectionTitle = ::kiloModelSectionTitle,
    content = parts.head,
    trailing = parts.star,
) {
    constructor(
        model: CollectionListModel<KiloModelRow>,
        active: () -> ModelPicker.Item?,
        activeRoute: () -> String?,
        activeVariant: () -> String?,
        starred: () -> Set<String>,
        locked: () -> Boolean,
    ) : this(model, active, activeRoute, activeVariant, starred, locked, Parts.create())

    companion object {
        val DATA_COLLECTED: Icon = IconLoader.getIcon("/icons/book-open-check.svg", KiloModelPopupRenderer::class.java)
        val checked: Icon = KiloPickerListRenderer.checkedIcon
        val empty: Icon = KiloPickerListRenderer.emptyIcon

        fun isStarClick(list: JList<*>, bounds: Rectangle, point: Point): Boolean {
            return KiloPickerListRenderer.trailingClickZone(list, bounds, point, STAR_CLICK_AREA_WIDTH)
        }
    }

    override fun getListCellRendererComponent(
        list: JList<out KiloModelRow>,
        value: KiloModelRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ): JPanel {
        return super.getListCellRendererComponent(list, value, index, selected, focused) as JPanel
    }

    override fun update(
        value: KiloModelRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
        foreground: java.awt.Color,
        weak: java.awt.Color,
    ) {
        val secondary = if (selected) weak else SessionUiStyle.Text.Secondary.foreground()
        // While frozen the pickable rows read as disabled; the unlock row keeps full strength.
        val main = if (locked() && value !is KiloModelRow.Lock) weak else foreground
        parts.title.clear()
        parts.title.icon = null
        parts.title.toolTipText = null
        parts.badgeLabel.isVisible = false
        parts.byokLabel.isVisible = false
        parts.warn.isVisible = false
        parts.star.icon = EmptyIcon.ICON_16
        when (value) {
            is KiloModelRow.Lock -> {
                parts.title.icon = AllIcons.Nodes.Locked
                parts.title.append(
                    KiloBundle.message("param.picker.unlock"),
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, foreground),
                )
                parts.title.toolTipText = KiloBundle.message("param.picker.locked.description")
            }
            is KiloModelRow.Preset -> renderPreset(value.preset, main, secondary)
            is KiloModelRow.Model -> renderModel(value.row, main, secondary, selected)
            is KiloModelRow.Empty -> parts.title.append(value.text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
        }
    }

    private fun renderPreset(preset: KiloFavoritePreset, main: java.awt.Color, secondary: java.awt.Color) {
        parts.title.append(preset.name, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, main))
        val auto = KiloBundle.message("param.picker.auto")
        val summary = listOfNotNull(
            preset.provider,
            preset.modelId,
            preset.routeTag ?: auto,
            preset.variant ?: auto,
        ).joinToString(" · ")
        parts.title.append("  $summary", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
    }

    private fun renderModel(row: ModelPickerRow, main: java.awt.Color, secondary: java.awt.Color, selected: Boolean) {
        val item = row.item
        if (item == null) {
            parts.title.append(row.emptyText, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, main))
            return
        }
        val name = ModelText.parts(item)
        name.provider?.let {
            parts.title.append(it, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
            parts.title.append(" ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
        }
        parts.title.append(name.model, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, main))

        parts.warn.isVisible = ModelText.collectsData(item)
        parts.badgeLabel.isVisible = item.free && !item.byok
        parts.byokLabel.isVisible = item.byok
        // The star toggles this project's labeled preset for the model, not the flat favorite.
        parts.star.icon = when {
            item.key in starred() -> AllIcons.Nodes.Favorite
            selected -> AllIcons.Nodes.NotFavoriteOnHover
            else -> EmptyIcon.ICON_16
        }
    }

    internal fun starIcon(): Icon? = parts.star.icon

    internal fun badgeVisible(): Boolean = parts.badgeLabel.isVisible

    internal fun byokVisible(): Boolean = parts.byokLabel.isVisible

    internal fun warningVisible(): Boolean = parts.warn.isVisible

    internal fun warningTooltip(): String? = parts.warn.toolTipText

    private class BadgeLabel(icon: Icon) : JBLabel(icon)

    private data class Parts(
        val title: SimpleColoredComponent,
        val badge: FilledBadgeIcon,
        val badgeLabel: BadgeLabel,
        val byokLabel: BadgeLabel,
        val warn: JBLabel,
        val star: JBLabel,
        val head: JPanel,
    ) {
        companion object {
            fun create(): Parts {
                val title = SimpleColoredComponent()
                val badge = FilledBadgeIcon(ModelText.freeLabel(), UiStyle.Badge.Highlight)
                val badgeLabel = BadgeLabel(badge).apply {
                    border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
                }
                val byok = FilledBadgeIcon("BYOK", UiStyle.Badge.Highlight)
                val byokLabel = BadgeLabel(byok).apply {
                    border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
                }
                val warn = JBLabel(DATA_COLLECTED).apply {
                    toolTipText = ModelText.dataCollected()
                    border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
                }
                val star = JBLabel().apply {
                    horizontalAlignment = SwingConstants.CENTER
                    verticalAlignment = SwingConstants.CENTER
                }
                val head = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    add(title)
                    add(warn)
                    add(badgeLabel)
                    add(byokLabel)
                }
                UiStyle.Components.transparent(title, head, warn, badgeLabel, byokLabel, star)
                return Parts(title, badge, badgeLabel, byokLabel, warn, star, head)
            }
        }
    }
}
