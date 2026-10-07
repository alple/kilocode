package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPickerRenderer
import ai.kilocode.client.session.ui.model.ModelPickerRow
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.FilledBadgeIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.picker.PickerListRenderer
import ai.kilocode.rpc.dto.RouteEndpointDto
import com.intellij.icons.AllIcons
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Row check state: active model key, route pin (null = Auto), and reasoning effort (null = Auto). */
private fun checkedRow(row: KiloParamRow, model: String?, route: String?, variant: String?): Boolean = when (row) {
    is KiloParamRow.Model -> row.row.key != null && row.row.key == model
    is KiloParamRow.Route -> row.tag == route
    is KiloParamRow.Effort -> row.id == variant
    is KiloParamRow.Lock, is KiloParamRow.Note -> false
}

/**
 * Renderer for the session parameter popup: model rows reuse the upstream model picker's row look
 * (provider/model text, free/BYOK badges, favorite stars), route rows render tag, quantization,
 * status, uptime and pricing, effort rows and notices stay plain. While the parameter trio is
 * frozen the pickable rows render weak — only the unlock row keeps full strength.
 */
internal class KiloParameterRenderer private constructor(
    model: CollectionListModel<KiloParamRow>,
    activeModel: () -> String?,
    activeRoute: () -> String?,
    activeVariant: () -> String?,
    private val favorites: () -> Set<String>,
    private val locked: () -> Boolean,
    private val parts: Parts,
) : PickerListRenderer<KiloParamRow>(
    model = model,
    checked = { row -> checkedRow(row, activeModel(), activeRoute(), activeVariant()) },
    sectionTitle = ::kiloParameterSectionTitle,
    content = parts.head,
    trailing = parts.star,
) {
    constructor(
        model: CollectionListModel<KiloParamRow>,
        activeModel: () -> String?,
        activeRoute: () -> String?,
        activeVariant: () -> String?,
        favorites: () -> Set<String>,
        locked: () -> Boolean,
    ) : this(model, activeModel, activeRoute, activeVariant, favorites, locked, Parts.create())

    override fun getListCellRendererComponent(
        list: JList<out KiloParamRow>,
        value: KiloParamRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ): JPanel {
        return super.getListCellRendererComponent(list, value, index, selected, focused) as JPanel
    }

    override fun update(
        value: KiloParamRow,
        index: Int,
        selected: Boolean,
        focused: Boolean,
        foreground: java.awt.Color,
        weak: java.awt.Color,
    ) {
        val secondary = if (selected) weak else SessionUiStyle.Text.Secondary.foreground()
        // While frozen the pickable rows read as disabled; the unlock row keeps full strength.
        val main = if (locked() && value !is KiloParamRow.Lock) weak else foreground
        parts.title.clear()
        parts.title.icon = null
        parts.title.toolTipText = null
        parts.warn.isVisible = false
        parts.badgeLabel.isVisible = false
        parts.byokLabel.isVisible = false
        parts.provider.isVisible = false
        parts.star.icon = PickerListRenderer.emptyIcon
        when (value) {
            is KiloParamRow.Lock -> {
                parts.title.icon = AllIcons.Nodes.Locked
                parts.title.append(
                    KiloBundle.message("param.picker.unlock"),
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, foreground),
                )
                parts.title.toolTipText = KiloBundle.message("param.picker.locked.description")
            }
            is KiloParamRow.Model -> renderModel(value.row, main, secondary, selected)
            is KiloParamRow.Route -> {
                val name = value.endpoint?.providerName?.takeIf { it.isNotBlank() }
                if (name != null) {
                    parts.title.append(name, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, main))
                    value.tag?.let {
                        parts.title.append("  $it", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
                    }
                } else {
                    parts.title.append(
                        value.tag ?: KiloBundle.message("param.picker.auto"),
                        SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, main),
                    )
                }
                routeDetails(value.endpoint)?.let { details ->
                    parts.title.append("  $details", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
                }
            }
            is KiloParamRow.Effort -> parts.title.append(
                value.id ?: KiloBundle.message("param.picker.auto"),
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, main),
            )
            is KiloParamRow.Note -> parts.title.append(
                value.text,
                if (value.warn) SimpleTextAttributes.ERROR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES,
            )
        }
    }

    private fun routeDetails(endpoint: RouteEndpointDto?): String? {
        if (endpoint == null) return null
        val parts = buildList {
            KiloRouteText.quantization(endpoint)?.let { add(it) }
            KiloRouteText.uptime(endpoint.uptime)?.let { add(it) }
            if (KiloRouteText.degraded(endpoint.status)) add(KiloBundle.message("param.picker.route.degraded"))
            KiloRouteText.pricing(endpoint.pricing)?.let { add(it) }
        }
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    private fun renderModel(row: ModelPickerRow, foreground: java.awt.Color, secondary: java.awt.Color, selected: Boolean) {
        val item = row.item
        if (item == null) {
            parts.title.append(row.emptyText, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))
            return
        }
        val name = ModelText.parts(item)
        name.provider?.let {
            parts.title.append(it, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
            parts.title.append(" ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, secondary))
        }
        parts.title.append(name.model, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, foreground))

        parts.warn.isVisible = ModelText.collectsData(item)
        parts.badgeLabel.isVisible = item.free && !item.byok
        parts.byokLabel.isVisible = item.byok
        parts.provider.isVisible = row.favorite
        parts.provider.text = item.providerName
        parts.provider.foreground = secondary
        parts.provider.border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())

        parts.star.icon = when {
            item.key in favorites() -> AllIcons.Nodes.Favorite
            selected -> AllIcons.Nodes.NotFavoriteOnHover
            else -> PickerListRenderer.emptyIcon
        }
    }

    private companion object {
        val DATA_COLLECTED: Icon = ModelPickerRenderer.DATA_COLLECTED
    }

    private class Parts {
        val title = SimpleColoredComponent()
        val badge = FilledBadgeIcon(ModelText.freeLabel(), UiStyle.Badge.Highlight)
        val badgeLabel = Badge(badge).apply {
            border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
        }
        val byokLabel = Badge(FilledBadgeIcon("BYOK", UiStyle.Badge.Highlight)).apply {
            border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
        }
        val warn = JBLabel(DATA_COLLECTED).apply {
            toolTipText = ModelText.dataCollected()
            border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
        }
        val provider = JBLabel()
        val star = JBLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }
        val head = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(title)
            add(warn)
            add(badgeLabel)
            add(byokLabel)
            add(provider)
        }

        init {
            UiStyle.Components.transparent(title, badgeLabel, byokLabel, warn, provider, star, head)
        }

        companion object {
            fun create(): Parts = Parts()
        }
    }

    private class Badge(icon: Icon) : JBLabel(icon)
}
