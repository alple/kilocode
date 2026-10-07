package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.settings.base.SettingsStackedRow
import ai.kilocode.client.settings.base.SettingsToggle
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.HAlign
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.layout.StackAxis
import ai.kilocode.client.ui.layout.VAlign
import ai.kilocode.client.ui.layout.align
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

/**
 * Deluxe-only "enable model routing" option page (KILO-8.4): the on/off gate for every piece of
 * route UI plus the route-capable provider-id list (defaults `openrouter` + `openrouter-private`,
 * user-extendable for custom-URL OpenRouter clones). Purely local state in [KiloRoutingOptions];
 * there is no CLI round-trip to wait on.
 */
class KiloRoutingConfigurable : Configurable {

    private var page: KiloRoutingPage? = null

    override fun getDisplayName(): String = KiloBundle.message("settings.routing.displayName")

    override fun createComponent(): JComponent {
        val page = KiloRoutingPage(KiloRoutingOptions.getInstance())
        this.page = page
        return page
    }

    override fun isModified(): Boolean {
        val page = page ?: return false
        val options = KiloRoutingOptions.getInstance()
        return page.enabled() != options.enabled || page.providers() != options.providers
    }

    override fun apply() {
        val page = page ?: return
        val options = KiloRoutingOptions.getInstance()
        options.update(page.enabled(), page.providers())
        page.sync(options.enabled, options.providers)
    }

    override fun reset() {
        val options = KiloRoutingOptions.getInstance()
        page?.sync(options.enabled, options.providers)
    }

    override fun disposeUIResources() {
        page = null
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings.routing"
    }
}

/** Toggle for the routing gate plus the editable route-capable provider-id list. */
internal class KiloRoutingPage(options: KiloRoutingOptions) : Stack(StackAxis.VERTICAL, UiStyle.Gap.md()) {

    private var enabled = options.enabled
    private var providers = options.providers

    private val toggle = SettingsToggle(options.enabled) { value ->
        enabled = value
        list.isEnabled = value
    }
    private val list = ProviderList(options.providers) { values ->
        providers = values
    }

    init {
        next(
            SettingsStackedRow(
                KiloBundle.message("settings.routing.enable.title"),
                KiloBundle.message("settings.routing.enable.description"),
                toggle,
            )
        )
        next(
            SettingsStackedRow(
                KiloBundle.message("settings.routing.providers.title"),
                KiloBundle.message("settings.routing.providers.description"),
                list,
            )
        )
    }

    fun enabled(): Boolean = enabled

    fun providers(): List<String> = providers

    @RequiresEdt
    fun sync(value: Boolean, providers: List<String>) {
        enabled = value
        this.providers = providers
        toggle.isSelected = value
        list.sync(providers)
        list.isEnabled = value
    }
}

/**
 * Editable route-capable provider-id list: add via an input prompt, edit via double-click, remove
 * via Delete or the toolbar icon. Applied values land in [KiloRoutingOptions] on Apply.
 */
private class ProviderList(initial: List<String>, private val change: (List<String>) -> Unit) :
    Stack(StackAxis.VERTICAL, UiStyle.Gap.sm()) {

    private val model = CollectionListModel<String>(initial)

    private val add = HoverIcon().apply {
        icon = AllIcons.General.Add
        toolTipText = KiloBundle.message("settings.routing.add")
        addActionListener { addProvider() }
    }
    private val remove = HoverIcon().apply {
        icon = AllIcons.General.Remove
        toolTipText = KiloBundle.message("settings.routing.remove")
        addActionListener { removeSelected() }
    }
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        isFocusable = true
        emptyText.text = KiloBundle.message("settings.routing.providers.empty")
        cellRenderer = ProviderRenderer()
        addListSelectionListener { if (!it.valueIsAdjusting) syncActions() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || !UIUtil.isActionClick(e, MouseEvent.MOUSE_CLICKED, true)) return
                val idx = locationToIndex(e.point)
                if (idx < 0 || getCellBounds(idx, idx)?.contains(e.point) != true) return
                edit(idx)
            }
        })
        registerKeyboardAction(
            { removeSelected() },
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0),
            JComponent.WHEN_FOCUSED,
        )
        ScrollingUtil.installActions(this)
    }
    private val scroll = JBScrollPane(list).apply {
        border = null
        viewportBorder = null
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
    private val toolbar = Stack.horizontal().next(add).next(remove)

    init {
        next(toolbar.align(HAlign.RIGHT, VAlign.CENTER))
        gap(UiStyle.Gap.sm())
        next(scroll)
        syncActions()
    }

    fun sync(values: List<String>) {
        if (model.items != values) model.replaceAll(values)
        syncActions()
    }

    override fun setEnabled(value: Boolean) {
        super.setEnabled(value)
        list.isEnabled = value
        scroll.isEnabled = value
        syncActions()
    }

    private fun syncActions() {
        add.isEnabled = isEnabled
        remove.isEnabled = isEnabled && list.selectedIndex >= 0
    }

    private fun addProvider() {
        if (!isEnabled) return
        val value = promptInput().trim()
        if (value.isBlank()) return
        val values = model.items.toMutableList()
        val idx = values.indexOf(value).takeIf { it >= 0 } ?: run {
            values += value
            model.replaceAll(values)
            change(values)
            values.lastIndex
        }
        list.selectedIndex = idx
        ScrollingUtil.ensureIndexIsVisible(list, idx, 0)
        syncActions()
    }

    private fun edit(idx: Int) {
        if (!isEnabled || idx < 0 || idx >= model.size) return
        val value = promptEdit(model.getElementAt(idx)).trim()
        if (value.isBlank()) return
        val values = model.items.toMutableList()
        if (value != values[idx] && value in values) return
        values[idx] = value
        model.replaceAll(values)
        list.selectedIndex = idx
        ScrollingUtil.ensureIndexIsVisible(list, idx, 0)
        change(values)
        syncActions()
    }

    private fun removeSelected() {
        val idx = list.selectedIndex.takeIf { it >= 0 && it < model.size } ?: return
        if (!isEnabled) return
        val values = model.items.toMutableList()
        values.removeAt(idx)
        model.replaceAll(values)
        val next = idx.coerceAtMost(values.lastIndex)
        if (next >= 0) list.selectedIndex = next else list.clearSelection()
        change(values)
        syncActions()
    }

    private fun promptInput(): String = Messages.showInputDialog(
        this,
        KiloBundle.message("settings.routing.input.prompt"),
        KiloBundle.message("settings.routing.input.title"),
        null,
    ).orEmpty()

    private fun promptEdit(value: String): String = Messages.showInputDialog(
        this,
        KiloBundle.message("settings.routing.input.edit"),
        KiloBundle.message("settings.routing.input.title"),
        null,
        value,
        null,
    ).orEmpty()

    private class ProviderRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            selected: Boolean,
            focus: Boolean,
        ): java.awt.Component {
            val comp = super.getListCellRendererComponent(list, value, index, selected, focus) as JComponent
            comp.border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap())
            return comp
        }
    }
}
