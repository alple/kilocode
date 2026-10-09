package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.client.ui.FilledBadgeIcon
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.client.ui.layout.Stack
import ai.kilocode.client.ui.md.MdView
import ai.kilocode.client.ui.md.MdViewFactory
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Deluxe clone of the upstream [ai.kilocode.client.session.ui.model.ModelDetailsPanel] (ADR-0003):
 * the session sidecar previews the hovered row's model, and preset rows add the preset's
 * route/effort block. The star toggles the project's labeled preset for the model, not the flat
 * favorite. Presets whose model is missing from the catalog still preview with their summary.
 */
internal class KiloModelDetailsPanel(
    private val starred: () -> Set<String>,
    private val toggle: (ModelPicker.Item) -> Unit,
) : JPanel(BorderLayout()), Disposable {
    private val empty = JBLabel(KiloBundle.message("model.picker.details.empty")).apply {
        foreground = SessionUiStyle.Text.Secondary.foreground()
    }
    private val title = JBLabel().apply { font = UiStyle.Fonts.bold() }
    private val provider = JBLabel().apply { foreground = SessionUiStyle.Text.Secondary.foreground() }
    private val star = JBLabel().apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        horizontalAlignment = JBLabel.CENTER
        verticalAlignment = JBLabel.CENTER
    }
    private val head = JPanel(BorderLayout()).apply {
        add(Stack.vertical(UiStyle.Gap.xs()).next(title).next(provider), BorderLayout.CENTER)
        add(star, BorderLayout.EAST)
    }
    private val free = badge(ModelText.freeLabel())
    private val byok = badge("BYOK")
    private val data = badge(KiloBundle.message("model.picker.dataCollected"))
    private val latest = badge(KiloBundle.message("model.picker.details.latest"))
    private val badges = Stack.horizontal(UiStyle.Gap.xs()).next(free).next(byok).next(data).next(latest)
    private val params = RowsSection(KiloBundle.message("param.picker.details.parameters"))
    private val props = RowsSection(KiloBundle.message("model.picker.details.properties"))
    private val bench = RowsSection(KiloBundle.message("model.picker.details.terminalBench"))
    private val caps = TagsSection(KiloBundle.message("model.picker.details.capabilities"))
    private val desc = MarkdownSection(KiloBundle.message("model.picker.details.description"))
    private val routeText = JBLabel().apply {
        foreground = UIUtil.getLabelForeground()
        setAllowAutoWrapping(true)
    }
    private val route = Stack.vertical(UiStyle.Gap.xs())
        .next(heading(KiloBundle.message("model.picker.details.autoRouting")))
        .next(routeText)
    private val ids = RowsSection(KiloBundle.message("model.picker.details.ids"))
    private val body = Stack.vertical(UiStyle.Gap.sm()).apply {
        border = JBUI.Borders.empty(UiStyle.Gap.md(), UiStyle.Gap.lg(), UiStyle.Gap.md(), UiStyle.Gap.xl())
        next(empty)
        next(head)
        next(badges)
        next(params.root)
        next(props.root)
        next(bench.root)
        next(caps.root)
        next(desc.root)
        next(route)
        next(ids.root)
    }
    private val scroll = JBScrollPane(body).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        border = JBUI.Borders.empty()
        viewportBorder = JBUI.Borders.empty()
    }
    private var item: ModelPicker.Item? = null
    private var preset: KiloFavoritePreset? = null

    init {
        border = JBUI.Borders.customLineLeft(UIUtil.getBoundsColor())
        add(scroll, BorderLayout.CENTER)
        star.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val shown = item ?: return
                toggle(shown)
                update(shown, preset)
            }
        })
        showEmpty()
    }

    fun update(value: ModelPicker.Item?, preset: KiloFavoritePreset? = null) {
        item = value
        this.preset = preset
        if (value == null && preset == null) {
            showEmpty()
            refresh()
            return
        }
        if (value == null) {
            // A preset whose model is not in the catalog: still show what applying it would mean.
            showPresetOnly(preset ?: return)
            refresh()
            return
        }

        empty.isVisible = false
        head.isVisible = true
        title.sync(ModelText.parts(value).model)
        provider.sync(value.providerName)
        syncStar(value)
        syncBadges(value)
        params.update(paramRows(preset))
        props.update(properties(value))
        bench.update(bench(value))
        caps.update(capabilities(value))
        desc.update(value.options?.description?.takeIf { it.isNotBlank() }?.let(::descriptionText))
        syncRouting(value)
        ids.update(listOf(
            KiloBundle.message("model.picker.details.providerId") to value.provider,
            KiloBundle.message("model.picker.details.modelId") to value.id,
        ))
        refresh()
    }

    private fun showPresetOnly(preset: KiloFavoritePreset) {
        empty.isVisible = false
        head.isVisible = true
        title.sync(preset.name)
        provider.sync("${preset.provider} / ${preset.modelId}")
        star.isVisible = false
        badges.isVisible = false
        params.update(paramRows(preset))
        props.update(emptyList())
        bench.update(emptyList())
        caps.update(emptyList())
        desc.update(null)
        route.isVisible = false
        ids.update(emptyList())
    }

    private fun showEmpty() {
        empty.isVisible = true
        head.isVisible = false
        badges.isVisible = false
        params.update(emptyList())
        props.update(emptyList())
        bench.update(emptyList())
        caps.update(emptyList())
        desc.update(null)
        route.isVisible = false
        ids.update(emptyList())
    }

    private fun paramRows(preset: KiloFavoritePreset?): List<Pair<String, String>> {
        if (preset == null) return emptyList()
        val auto = KiloBundle.message("param.picker.auto")
        return listOf(
            KiloBundle.message("param.picker.route.section") to (preset.routeTag ?: auto),
            KiloBundle.message("param.picker.effort.section") to (preset.variant ?: auto),
        )
    }

    private fun syncStar(value: ModelPicker.Item) {
        star.isVisible = true
        val selected = value.key in starred()
        star.icon = if (selected) AllIcons.Nodes.Favorite else AllIcons.Nodes.NotFavoriteOnHover
        star.toolTipText = if (selected) {
            KiloBundle.message("param.picker.star.remove")
        } else {
            KiloBundle.message("param.picker.star.add")
        }
    }

    private fun syncBadges(value: ModelPicker.Item) {
        free.isVisible = value.free && !value.byok
        byok.isVisible = value.byok
        data.isVisible = ModelText.collectsData(value)
        latest.isVisible = value.latest == true
        badges.isVisible = free.isVisible || byok.isVisible || data.isVisible || latest.isVisible
    }

    private fun properties(item: ModelPicker.Item): List<Pair<String, String>> {
        val ctx = item.limit?.context?.takeIf { it > 0 } ?: item.contextLength?.takeIf { it > 0 }
        return buildList {
            item.releaseDate?.let { add(KiloBundle.message("model.picker.details.released") to date(it)) }
            if (!item.free) {
                item.cost?.takeIf { it.input > 0.0 || it.output > 0.0 }?.let { cost ->
                    add(KiloBundle.message("model.picker.details.input") to price(cost.input))
                    add(KiloBundle.message("model.picker.details.output") to price(cost.output))
                    add(KiloBundle.message("model.picker.details.cached") to cached(cost.input, cost.cache?.read))
                    add(KiloBundle.message("model.picker.details.average") to price(average(cost.input, cost.output, cost.cache?.read)))
                } ?: run {
                    item.inputPrice?.takeIf { it > 0.0 }?.let { add(KiloBundle.message("model.picker.details.input") to price(it)) }
                    item.outputPrice?.takeIf { it > 0.0 }?.let { add(KiloBundle.message("model.picker.details.output") to price(it)) }
                }
            }
            ctx?.let { add(KiloBundle.message("model.picker.details.context") to context(it)) }
        }
    }

    private fun bench(item: ModelPicker.Item): List<Pair<String, String>> {
        val bench = item.terminalBench ?: return emptyList()
        return listOf(
            KiloBundle.message("model.picker.details.completion") to percent(bench.overallScore),
            KiloBundle.message("model.picker.details.costAttempt") to attempt(bench.avgAttemptCostUsd),
        )
    }

    private fun capabilities(item: ModelPicker.Item): List<String> {
        val cap = item.capabilities
        return buildList {
            if (cap?.reasoning == true || item.reasoning) add(KiloBundle.message("model.picker.details.reasoning"))
            val input = cap?.input
            if (input?.text == true) add(KiloBundle.message("model.picker.details.modality.text"))
            if (input?.image == true) add(KiloBundle.message("model.picker.details.modality.image"))
            if (input?.audio == true) add(KiloBundle.message("model.picker.details.modality.audio"))
            if (input?.video == true) add(KiloBundle.message("model.picker.details.modality.video"))
            if (input?.pdf == true) add(KiloBundle.message("model.picker.details.modality.pdf"))
            if (item.attachment) add(KiloBundle.message("model.picker.details.attachments"))
        }
    }

    private fun syncRouting(item: ModelPicker.Item) {
        val models = item.autoRouting?.models?.takeIf { it.isNotEmpty() }
        route.isVisible = models != null
        if (models == null) return
        routeText.sync(XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(models.joinToString("\n"))))
    }

    private fun refresh() {
        body.revalidate()
        body.repaint()
    }

    override fun dispose() {
        desc.dispose()
    }
}

private class RowsSection(title: String) {
    private val rows = Stack.vertical(UiStyle.Gap.xs())
    private val pool = mutableListOf<DetailRow>()
    val root = Stack.vertical(UiStyle.Gap.xs())
        .next(heading(title))
        .next(rows)

    fun update(values: List<Pair<String, String>>) {
        root.isVisible = values.isNotEmpty()
        values.forEachIndexed { idx, value ->
            val row = row(idx)
            row.update(value.first, value.second)
            row.isVisible = true
        }
        for (idx in values.size until pool.size) {
            pool[idx].isVisible = false
        }
        rows.revalidate()
        rows.repaint()
    }

    private fun row(idx: Int): DetailRow {
        pool.getOrNull(idx)?.let { return it }
        val row = DetailRow()
        pool.add(row)
        rows.next(row)
        return row
    }
}

private class DetailRow : JPanel(BorderLayout()) {
    private val name = JBLabel().apply { foreground = SessionUiStyle.Text.Secondary.foreground() }
    private val value = JBLabel().apply { foreground = SessionUiStyle.Colors.foreground() }

    init {
        add(name, BorderLayout.WEST)
        add(value, BorderLayout.EAST)
    }

    fun update(label: String, text: String) {
        name.isVisible = label.isNotBlank()
        name.sync(label)
        value.sync(text)
    }
}

private class TagsSection(title: String) {
    private val tags = TagPanel()
    private val pool = mutableListOf<JBLabel>()
    val root = Stack.vertical(UiStyle.Gap.xs())
        .next(heading(title))
        .next(tags)

    fun update(values: List<String>) {
        root.isVisible = values.isNotEmpty()
        values.forEachIndexed { idx, value ->
            val tag = tag(idx)
            val icon = tag.icon as? FilledBadgeIcon
            if (icon?.text != value) {
                tag.icon = FilledBadgeIcon(value, tagStyle(idx))
            }
            if (tag.toolTipText != value) tag.toolTipText = value
            tag.isVisible = true
        }
        for (idx in values.size until pool.size) {
            pool[idx].isVisible = false
        }
        tags.revalidate()
        tags.repaint()
    }

    private fun tag(idx: Int): JBLabel {
        pool.getOrNull(idx)?.let { return it }
        val tag = JBLabel()
        pool.add(tag)
        tags.add(tag)
        return tag
    }
}

private class MarkdownSection(title: String) : Disposable {
    val root = Stack.vertical(UiStyle.Gap.xs()).next(heading(title))
    private var view: MdView? = null

    fun update(text: String?) {
        root.isVisible = text != null
        if (text == null) return
        val md = view ?: MdViewFactory.create(SessionEditorStyle.current()).apply {
            opaque = false
            addLinkListener { BrowserUtil.browse(it.href) }
        }.also {
            view = it
            root.next(it.component)
        }
        md.set(text)
    }

    override fun dispose() {
        view?.let(Disposer::dispose)
        view = null
    }
}

private class TagPanel : JPanel(FlowLayout(FlowLayout.LEFT, UiStyle.Gap.sm(), UiStyle.Gap.xs())) {
    init {
        isOpaque = false
    }
}

private fun heading(value: String) = JBLabel(value).apply { font = UiStyle.Fonts.bold() }

private fun badge(value: String) = JBLabel(value).apply {
    border = JBUI.Borders.empty(UiStyle.Gap.xs(), UiStyle.Gap.sm(), UiStyle.Gap.xs(), UiStyle.Gap.sm())
    foreground = UIUtil.getLabelForeground()
}

private fun JBLabel.sync(value: String) {
    if (text == value) return
    text = value
}

private fun context(value: Long): String {
    if (value >= 1_000_000) return "${format(value.toDouble() / 1_000_000.0)}M"
    if (value >= 1_000) return "${format(value.toDouble() / 1_000.0)}K"
    return value.toString()
}

private fun price(value: Double): String {
    if (value == 0.0) return KiloBundle.message("model.picker.free")
    val digits = if (value < 0.01) 4 else 2
    return "$${value.format(digits)}/1M"
}

private fun cached(input: Double, read: Double?): String {
    if (read != null) return price(read)
    if (input == 0.0) return price(0.0)
    return KiloBundle.message("model.picker.details.notSupported")
}

private fun average(input: Double, output: Double, read: Double?): Double {
    if (read != null) return read * 0.7 + input * 0.2 + output * 0.1
    return input * 0.9 + output * 0.1
}

private fun percent(value: Double) = "${(value * 100.0).format(1)}%"

private fun attempt(value: Double) = "$${value.format(2)}"

private fun date(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM yyyy"))
}.getOrDefault(value)

private fun descriptionText(value: String): String = value
    .replace(Regex("(?<![\\[<(])\\b(https?://[^\\s<>()`\"']+)([),.;!?])?")) { match ->
        val url = match.groupValues[1]
        val tail = match.groupValues.getOrNull(2).orEmpty()
        "[$url]($url)$tail"
    }

private class TagStyle(private val bg: Color) : UiStyle.Badge.Style {
    override fun bg() = bg

    override fun fg() = SessionUiStyle.Colors.foreground()
}

private fun tagStyle(index: Int): UiStyle.Badge.Style {
    if (index % 4 == 0) return UiStyle.Badge.Secondary
    val bg = when (index % 4) {
        1 -> UiStyle.Colors.blend(UiStyle.Colors.contentBackground(), SessionUiStyle.Colors.foreground(), 0.12f)
        2 -> UiStyle.Colors.blend(UiStyle.Colors.contentBackground(), JBUI.CurrentTheme.Link.Foreground.ENABLED, 0.18f)
        else -> UiStyle.Colors.blend(UiStyle.Colors.contentBackground(), UiStyle.Badge.Primary.bg(), 0.18f)
    }
    return TagStyle(bg)
}

private fun format(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 1
}.format(value)

private fun Double.format(digits: Int) = String.format(Locale.US, "%.${digits}f", this)
