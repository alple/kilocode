package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ProviderDto
import ai.kilocode.rpc.dto.ProvidersDto
import ai.kilocode.rpc.dto.RouteEndpointDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ItemEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The management window's right editor (ADR-0003): name, label chips with autocomplete of existing
 * labels, provider/model combos gated to routing-enabled providers, the route list from the CLI
 * `/kilo/routes` proxy with price/uptime sort and filter plus the cheapest-endpoint highlight, the
 * effort combo from the model's variants, and the details block with the RPG-style price delta
 * against the preset selected in the window list. Route and effort pins that vanish from refreshed
 * data are flagged and never auto-cleared. All edits go straight into the draft; the store is
 * written on the dialog's OK/Apply.
 */
internal class KiloFavoritesEditor(
    private val draft: KiloFavoritesDraft,
    private val scope: CoroutineScope,
    private val routing: () -> KiloRoutingOptions,
    private val catalog: suspend () -> ProvidersDto?,
    private val routes: suspend (author: String, slug: String) -> List<RouteEndpointDto>,
    private val baseline: () -> KiloFavoritePreset?,
    private val onEdited: () -> Unit,
) : JPanel(BorderLayout()) {

    /** Combo item: [value] is what gets stored, [label] what the user sees. */
    private data class Option(val value: String, val label: String) {
        override fun toString() = label
    }

    private sealed interface RouteRow {
        data object Auto : RouteRow

        data class Endpoint(val endpoint: RouteEndpointDto, val cheapest: Boolean) : RouteRow
    }

    private class Line(val label: String, val text: String, val delta: KiloPriceDelta? = null)

    private class DetailRow : JPanel(BorderLayout()) {
        private val title = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
        private val value = JBLabel()

        fun update(line: Line) {
            title.text = line.label
            value.text = line.text
            value.foreground = when {
                line.delta == null -> UIUtil.getLabelForeground()
                line.delta.cheaper -> UiStyle.Colors.addedForeground()
                else -> UiStyle.Colors.removedForeground()
            }
        }
    }

    private val empty = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(UiStyle.Gap.pad())
        setAllowAutoWrapping(true)
    }
    private val name = JBTextField()
    private val chips = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.xs(), UiStyle.Gap.xs())).apply { isOpaque = false }
    private val labelBox = JComboBox<String>().apply { isEditable = true }
    private val providerBox = JComboBox<Option>()
    private val modelBox = JComboBox<Option>()
    private val effortBox = JComboBox<Option>()
    private val sortBox = JComboBox<Option>()
    private val priceBox = JComboBox<Option>()
    private val uptimeBox = JComboBox<Option>()
    private val routeState = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
    private val staleRoute = warn()
    private val staleEffort = warn()
    private val routeList = JBList<RouteRow>().apply {
        cellRenderer = RouteRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val routeSection = Stack.vertical(UiStyle.Gap.sm())
    private val detailsSection = Stack.vertical(UiStyle.Gap.xs())
    private val deltaHeadline = JBLabel()
    private val rows = Stack.vertical(UiStyle.Gap.xs())
    private val rowPool = mutableListOf<DetailRow>()
    private val form = Stack.vertical(UiStyle.Gap.md())

    private var providers: ProvidersDto? = null
    private var cataloging = false
    private var catalogFailed = false
    private val routeStates = mutableMapOf<String, KiloRouteState>()
    private var endpoints: List<RouteEndpointDto> = emptyList()
    private var fetch: Job? = null
    private var subject: String? = null
    private var muted = false
    private var loaded = false

    init {
        layout = BorderLayout()
        empty.text = KiloBundle.message("favorites.window.empty.selection")
        form.next(field(KiloBundle.message("favorites.window.name"), name))
        form.next(field(KiloBundle.message("favorites.window.labels"), labelsRow()))
        form.next(field(KiloBundle.message("favorites.window.provider"), providerBox))
        form.next(field(KiloBundle.message("favorites.window.model"), modelBox))
        form.next(field(KiloBundle.message("param.picker.route.section"), routePanel()))
        form.next(field(KiloBundle.message("param.picker.effort.section"), Stack.horizontal(UiStyle.Gap.sm()).next(effortBox).next(staleEffort)))
        form.next(section(KiloBundle.message("favorites.window.details"), detailsSection))
        detailsSection.next(deltaHeadline)
        detailsSection.next(rows)
        sortBox.addItemListener { if (it.stateChange == ItemEvent.SELECTED) rerenderRoutes(endpoints) }
        priceBox.addItemListener { if (it.stateChange == ItemEvent.SELECTED) rerenderRoutes(endpoints) }
        uptimeBox.addItemListener { if (it.stateChange == ItemEvent.SELECTED) rerenderRoutes(endpoints) }
        providerBox.addItemListener { if (!muted && it.stateChange == ItemEvent.SELECTED) { commit(); refresh() } }
        modelBox.addItemListener { if (!muted && it.stateChange == ItemEvent.SELECTED) { commit(); refresh() } }
        effortBox.addItemListener { if (!muted && it.stateChange == ItemEvent.SELECTED) { commit(); refresh() } }
        routeList.addListSelectionListener { if (!muted && !it.valueIsAdjusting) { commit(); refresh() } }
        name.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = commit()
            override fun removeUpdate(e: DocumentEvent) = commit()
            override fun changedUpdate(e: DocumentEvent) = commit()
        })
        val scroll = JBScrollPane(form).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        add(Stack.vertical().next(empty).next(scroll), BorderLayout.CENTER)
    }

    /** Makes [id] the editing subject, fetching the catalog once and routes per model. */
    @RequiresEdt
    fun show(id: String?) {
        if (id == subject && loaded) return
        subject = id
        loaded = true
        refresh()
    }

    /** Hover preview from the window list: updates only the details block, never the form. */
    @RequiresEdt
    fun preview(id: String?) {
        val value = draft.preset(id ?: subject) ?: return
        details(value)
    }

    /** Re-reads the subject from the draft after an external change (e.g. a tree-side edit). */
    @RequiresEdt
    fun sync() = refresh()

    @RequiresEdt
    private fun refresh() {
        val value = draft.preset(subject) ?: run {
            empty.text = KiloBundle.message("favorites.window.empty.selection")
            empty.isVisible = true
            form.isVisible = false
            return
        }
        ensureCatalog()
        val gate = gate()
        empty.isVisible = gate != null
        if (gate != null) {
            empty.text = gate
            form.isVisible = false
            return
        }
        empty.isVisible = false
        form.isVisible = true
        muted = true
        name.text = value.name
        chips(value)
        labelBox.model = DefaultComboBoxModel(draft.labels().toTypedArray())
        labelBox.editor.item = ""
        providerBox.model = DefaultComboBoxModel(providerOptions(value).toTypedArray())
        pick(providerBox, value.provider)
        modelBox.model = DefaultComboBoxModel(modelOptions(value).toTypedArray())
        pick(modelBox, value.modelId)
        effort(value)
        syncRoutes(value)
        details(value)
        muted = false
    }

    /**
     * The gate message that replaces the form, or null when the form renders: no selection, the
     * "enable model routing" option off, the catalog still loading or failed, or no
     * routing-enabled provider in the catalog (ADR-0003).
     */
    private fun gate(): String? = when {
        subject == null -> KiloBundle.message("favorites.window.empty.selection")
        !routing().enabled -> KiloBundle.message("favorites.window.empty.routingOff")
        providers == null && cataloging -> KiloBundle.message("favorites.window.empty.loading")
        providers == null -> KiloBundle.message("favorites.window.empty.catalog")
        routed().isEmpty() -> KiloBundle.message("favorites.window.empty.noProvider")
        else -> null
    }

    private fun ensureCatalog() {
        if (providers != null || cataloging || catalogFailed) return
        cataloging = true
        scope.launch {
            val answer = runCatching { catalog() }.getOrNull()
            providers = answer
            catalogFailed = answer == null
            cataloging = false
            ui { refresh() }
        }
    }

    /** Catalog providers the routing option admits, in catalog order. */
    private fun routed(): List<ProviderDto> {
        val options = routing()
        if (!options.enabled) return emptyList()
        return providers?.providers?.filter { options.routesEnabled(it.id) } ?: emptyList()
    }

    private fun labelsRow(): JComponent {
        val add = HoverIcon().apply {
            icon = AllIcons.General.Add
            toolTipText = KiloBundle.message("favorites.window.label.add")
            accessibleContext.accessibleName = toolTipText
            addActionListener { addLabel() }
        }
        return Stack.horizontal(UiStyle.Gap.sm()).next(chips).next(labelBox).next(add)
    }

    private fun routePanel(): JComponent {
        sortBox.model = DefaultComboBoxModel(
            arrayOf(
                Option(KiloRouteSort.LISTED.name, KiloBundle.message("favorites.window.route.sort.listed")),
                Option(KiloRouteSort.PRICE.name, KiloBundle.message("favorites.window.route.sort.price")),
                Option(KiloRouteSort.UPTIME.name, KiloBundle.message("favorites.window.route.sort.uptime")),
            ),
        )
        priceBox.model = DefaultComboBoxModel(
            arrayOf(
                Option("", KiloBundle.message("favorites.window.route.price.any")),
                Option("1", KiloBundle.message("favorites.window.route.price.low")),
                Option("5", KiloBundle.message("favorites.window.route.price.mid")),
                Option("20", KiloBundle.message("favorites.window.route.price.high")),
            ),
        )
        uptimeBox.model = DefaultComboBoxModel(
            arrayOf(
                Option("", KiloBundle.message("favorites.window.route.uptime.any")),
                Option("90", KiloBundle.message("favorites.window.route.uptime.90")),
                Option("99", KiloBundle.message("favorites.window.route.uptime.99")),
            ),
        )
        routeSection.next(Stack.horizontal(UiStyle.Gap.sm()).next(sortBox).next(priceBox).next(uptimeBox))
        routeSection.next(staleRoute)
        routeSection.next(routeState)
        routeSection.next(
            JBScrollPane(routeList).apply {
                border = JBUI.Borders.empty()
                preferredSize = JBDimension(0, ROUTE_ROWS)
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            },
        )
        return routeSection
    }

    private fun addLabel() {
        val value = draft.preset(subject) ?: return
        val label = (labelBox.editor.item as? String)?.trim() ?: return
        if (label.isEmpty()) return
        draft.setLabels(value.id, value.labels + label)
        onEdited()
        refresh()
    }

    private fun chip(label: String): JPanel {
        val chip = JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.xs(), 0)).apply { isOpaque = false }
        chip.add(JBLabel(label))
        chip.add(
            HoverIcon().apply {
                icon = AllIcons.General.Close
                toolTipText = KiloBundle.message("favorites.window.label.remove", label)
                accessibleContext.accessibleName = toolTipText
                addActionListener {
                    val value = draft.preset(subject ?: return@addActionListener) ?: return@addActionListener
                    draft.setLabels(value.id, value.labels - label)
                    onEdited()
                    refresh()
                }
            },
        )
        return chip
    }

    private fun chips(value: KiloFavoritePreset) {
        chips.removeAll()
        for (label in value.labels) chips.add(chip(label))
        chips.revalidate()
        chips.repaint()
    }

    private fun providerOptions(value: KiloFavoritePreset): List<Option> {
        val options = routed().map { Option(it.id, it.name) }
        val kept = if (options.any { it.value == value.provider }) {
            options
        } else {
            options + Option(value.provider, value.provider)
        }
        return kept.ifEmpty { listOf(Option(value.provider, value.provider)) }
    }

    private fun modelOptions(value: KiloFavoritePreset): List<Option> {
        val provider = (providerBox.selectedItem as? Option)?.value ?: value.provider
        val models = providers?.providers?.firstOrNull { it.id == provider }?.models
        val options = models?.map { (id, model) -> Option(id, model.name) } ?: emptyList()
        val kept = if (options.any { it.value == value.modelId }) options else options + Option(value.modelId, value.modelId)
        return kept.ifEmpty { listOf(Option(value.modelId, value.modelId)) }
    }

    private fun model(): ModelDto? {
        val provider = (providerBox.selectedItem as? Option)?.value ?: return null
        val model = (modelBox.selectedItem as? Option)?.value ?: return null
        return providers?.providers?.firstOrNull { it.id == provider }?.models?.get(model)
    }

    /** Writes the editor fields back into the draft; the draft is the single source of truth. */
    private fun commit() {
        if (muted) return
        val value = draft.preset(subject) ?: return
        val provider = (providerBox.selectedItem as? Option)?.value ?: value.provider
        val model = (modelBox.selectedItem as? Option)?.value ?: value.modelId
        val variant = (effortBox.selectedItem as? Option)?.value?.takeIf { it.isNotEmpty() }
        val route = when {
            !routeSection.isVisible -> value.routeTag
            routeList.isSelectionEmpty -> null
            else -> (routeList.selectedValue as? RouteRow.Endpoint)?.endpoint?.tag
        }
        draft.update(value.id, name.text, provider, model, route, variant)
        onEdited()
    }

    private fun effort(value: KiloFavoritePreset) {
        val variants = model()?.variants ?: emptyList()
        val options = buildList {
            add(Option("", KiloBundle.message("param.picker.auto")))
            variants.forEach { add(Option(it, it)) }
            value.variant?.takeIf { it !in variants }?.let { add(Option(it, it)) }
        }
        effortBox.model = DefaultComboBoxModel(options.toTypedArray())
        pick(effortBox, value.variant ?: "")
        val pinned = value.variant
        if (pinned != null && pinned !in variants) {
            staleEffort.isVisible = true
            staleEffort.text = KiloBundle.message("favorites.window.stale.effort", pinned)
        } else {
            staleEffort.isVisible = false
        }
    }

    private fun syncRoutes(value: KiloFavoritePreset) {
        val pair = routeAuthorSlug(model()?.id ?: value.modelId)
        val applies = routing().routesEnabled((providerBox.selectedItem as? Option)?.value ?: value.provider) && pair != null
        routeSection.isVisible = applies
        staleRoute.isVisible = false
        routeState.isVisible = false
        if (!applies) return
        val (author, slug) = pair
        val key = "$author/$slug"
        val state = routeStates[key] ?: run {
            fetch?.cancel()
            fetch = scope.launch {
                val answer = try {
                    KiloRouteState.Ready(routes(author, slug))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KiloRouteState.Failed(e.message)
                }
                routeStates[key] = answer
                ui { refresh() }
            }
            KiloRouteState.Loading.also { routeStates[key] = it }
        }
        val ready = (state as? KiloRouteState.Ready)?.endpoints
        val pinned = value.routeTag
        if (pinned != null && ready != null && pinned !in ready.mapNotNull { it.tag }) {
            staleRoute.isVisible = true
            staleRoute.text = KiloBundle.message("param.picker.stale", pinned)
        }
        when (state) {
            is KiloRouteState.Loading -> {
                routeState.text = KiloBundle.message("param.picker.routes.loading")
                routeState.isVisible = true
                swap(emptyList())
            }
            is KiloRouteState.Failed -> {
                routeState.text = KiloBundle.message("param.picker.routes.unavailable")
                routeState.isVisible = true
                swap(emptyList())
            }
            is KiloRouteState.Ready -> {
                endpoints = state.endpoints
                rerenderRoutes(endpoints)
                selectRoute(value.routeTag)
            }
        }
    }

    private fun rerenderRoutes(endpoints: List<RouteEndpointDto>) {
        val listed = KiloRouteList.filter(endpoints, filter())
        val cheapest = KiloRouteList.cheapest(listed)
        val sorted = KiloRouteList.sort(listed, (sortBox.selectedItem as Option).value.let(KiloRouteSort::valueOf))
        swap(listOf(RouteRow.Auto) + sorted.map { RouteRow.Endpoint(it, it === cheapest) })
    }

    /** Replaces the route rows without the cleared selection being read as an Auto pick. */
    private fun swap(rows: List<RouteRow>) {
        muted = true
        routeList.setListData(rows.toTypedArray())
        muted = false
    }

    private fun filter(): KiloRouteFilter = KiloRouteFilter(
        maxInput = (priceBox.selectedItem as Option).value.toDoubleOrNull(),
        minUptime = (uptimeBox.selectedItem as Option).value.toDoubleOrNull()?.div(100.0),
    )

    private fun selectRoute(routeTag: String?) {
        for (index in 0 until routeList.model.size) {
            val hit = when (val row = routeList.model.getElementAt(index)) {
                is RouteRow.Auto -> routeTag == null
                is RouteRow.Endpoint -> row.endpoint.tag == routeTag
            }
            if (hit) {
                muted = true
                routeList.selectedIndex = index
                muted = false
                return
            }
        }
        muted = true
        routeList.clearSelection()
        muted = false
    }

    /** The details block: prices, context, release, terminal-bench, capabilities, and the delta. */
    private fun details(value: KiloFavoritePreset) {
        val base = baseline()?.takeIf { it.id != value.id }?.let(::modelOf)
        val target = modelOf(value)
        val set = kiloPriceDelta(kiloModelPrices(base), kiloModelPrices(target))
        val head = set.headline
        deltaHeadline.isVisible = head != null
        if (head != null) {
            deltaHeadline.text = KiloBundle.message(
                "favorites.window.delta.headline",
                baseline()?.name ?: "",
                KiloPriceText.delta(head.diff),
            )
            deltaHeadline.foreground = if (head.cheaper) UiStyle.Colors.addedForeground() else UiStyle.Colors.removedForeground()
        }
        val prices = kiloModelPrices(target)
        val lines = buildList {
            prices?.let { value ->
                value.input?.let { add(Line(KiloBundle.message("model.picker.details.input"), KiloPriceText.price(it), set.input)) }
                value.output?.let { add(Line(KiloBundle.message("model.picker.details.output"), KiloPriceText.price(it), set.output)) }
                value.cached?.let { add(Line(KiloBundle.message("model.picker.details.cached"), KiloPriceText.price(it), set.cached)) }
                value.blended?.let { add(Line(KiloBundle.message("model.picker.details.average"), KiloPriceText.price(it))) }
            }
            target?.let { dto ->
                dto.contextLength?.takeIf { it > 0 }?.let { add(Line(KiloBundle.message("model.picker.details.context"), StringUtil.formatFileSize(it))) }
                dto.releaseDate?.let { add(Line(KiloBundle.message("model.picker.details.released"), date(it))) }
                dto.terminalBench?.let { bench ->
                    add(Line(KiloBundle.message("model.picker.details.completion"), percent(bench.overallScore)))
                    add(Line(KiloBundle.message("model.picker.details.costAttempt"), "$${bench.avgAttemptCostUsd.format(2)}"))
                }
                capabilities(dto).forEach { add(Line(it, "")) }
            }
        }
        while (rowPool.size < lines.size) {
            val row = DetailRow()
            rowPool += row
            rows.next(row)
        }
        for (index in rowPool.indices) rowPool[index].isVisible = index < lines.size
        lines.forEachIndexed { index, line -> rowPool[index].update(line) }
        detailsSection.isVisible = lines.isNotEmpty() || head != null
        deltaHeadline.isVisible = head != null
        detailsSection.revalidate()
        detailsSection.repaint()
    }

    private fun modelOf(value: KiloFavoritePreset): ModelDto? =
        providers?.providers?.firstOrNull { it.id == value.provider }?.models?.get(value.modelId)

    private fun capabilities(dto: ModelDto): List<String> = buildList {
        if (dto.capabilities?.reasoning == true || dto.reasoning) add(KiloBundle.message("model.picker.details.reasoning"))
        dto.capabilities?.input?.let { input ->
            if (input.text) add(KiloBundle.message("model.picker.details.modality.text"))
            if (input.image) add(KiloBundle.message("model.picker.details.modality.image"))
            if (input.audio) add(KiloBundle.message("model.picker.details.modality.audio"))
            if (input.video) add(KiloBundle.message("model.picker.details.modality.video"))
            if (input.pdf) add(KiloBundle.message("model.picker.details.modality.pdf"))
        }
        if (dto.attachment) add(KiloBundle.message("model.picker.details.attachments"))
    }

    private fun date(value: String): String = runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
    }.getOrDefault(value)

    private fun Double.format(digits: Int) = String.format(Locale.US, "%.${digits}f", this)

    private fun percent(value: Double) = "${value.format(1)}%"

    private fun warn() = JBLabel().apply {
        foreground = UiStyle.Colors.warningLabelForeground()
        isVisible = false
        setAllowAutoWrapping(true)
    }

    private fun field(label: String, component: JComponent): JComponent =
        Stack.vertical(UiStyle.Gap.xs())
            .next(JBLabel(label).apply { font = UiStyle.Fonts.small() })
            .next(component)

    private fun section(label: String, content: JComponent): JComponent =
        Stack.vertical(UiStyle.Gap.xs())
            .next(JBLabel(label).apply { font = UiStyle.Fonts.small() })
            .next(content)

    private fun pick(box: JComboBox<Option>, value: String) {
        for (index in 0 until box.itemCount) {
            if (box.getItemAt(index).value == value) {
                box.selectedIndex = index
                return
            }
        }
        box.selectedIndex = -1
    }

    private fun ui(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(block, ModalityState.any())
    }

    private class RouteRenderer : ColoredListCellRenderer<RouteRow>() {
        override fun customizeCellRenderer(
            list: JList<out RouteRow>,
            value: RouteRow,
            index: Int,
            selected: Boolean,
            focused: Boolean,
        ) {
            when (value) {
                is RouteRow.Auto -> append(KiloBundle.message("param.picker.auto"))
                is RouteRow.Endpoint -> {
                    val endpoint = value.endpoint
                    val style = if (value.cheapest) {
                        SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UiStyle.Colors.addedForeground())
                    } else {
                        SimpleTextAttributes.REGULAR_ATTRIBUTES
                    }
                    append(endpoint.tag ?: KiloBundle.message("param.picker.auto"), style)
                    endpoint.providerName?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                    KiloRouteText.quantization(endpoint)?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                    KiloRouteText.uptime(endpoint.uptime)?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                    KiloRouteText.pricing(endpoint.pricing)?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                    if (KiloRouteText.degraded(endpoint.status)) {
                        append("  ${KiloBundle.message("param.picker.route.degraded")}", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    }
                }
            }
        }
    }

    private companion object {
        /** Viewport height the route list reserves, about six endpoint rows. */
        const val ROUTE_ROWS = 132
    }
}
