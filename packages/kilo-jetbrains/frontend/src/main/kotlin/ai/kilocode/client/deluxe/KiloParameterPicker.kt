package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.client.ui.PickerButton
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ModelSelectionDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val PARAM_PICKER_MIN_WIDTH = 480
private const val PARAM_PICKER_EXPANDED_KEY = "kilo.parameter.picker.expanded"

/**
 * Session-header parameter picker (KILO-9.2, ADR-0003): one control that keeps the trio label +
 * lock chip on the button and opens the fork-owned session popup — a Favorites section of the
 * project's visible presets above the full catalog. Picking a preset applies model + route + effort
 * as one; picking a plain model inherits the store's route/effort. The popup itself carries no
 * route/effort sections and performs no route fetching (ADR-0003 retired the KILO-8.4 sections and
 * their diagnosed defects); route editing lives in the favorites management window.
 *
 * The star on catalog rows toggles the project's labeled preset for that model (the store call is
 * wired by the host); Ctrl+2 cycles the visible presets, falling back to the flat `model.json`
 * favorites. The controller refuses preset and model picks while the trio is frozen.
 */
class KiloParameterPicker : PickerButton() {

    /** A model was picked; the controller refuses while the parameter trio is frozen. */
    var onModel: (ModelPicker.Item) -> Unit = {}

    /** A preset was picked; applies its model + route + effort trio as one. */
    var onPreset: (KiloFavoritePreset) -> Unit = {}

    /** Explicit unfreeze of the model/route/effort trio. */
    var onUnlock: () -> Unit = {}

    /** Star label-toggle for model rows: the host maps it onto the preset store. */
    var onStarToggle: (ModelPicker.Item) -> Unit = {}

    /** Opens the favorites management window (the pinned Favorites bar's edit button). */
    var onEditFavorites: () -> Unit = {}

    /** Flat `model.json` favorites, same source as the upstream pickers — Ctrl+2 fallback pool. */
    var favorites: () -> List<ModelSelectionDto> = { emptyList() }

    /** Presets visible to the host project, in the project label's order. */
    var presets: () -> List<KiloFavoritePreset> = { emptyList() }

    /** "Enable model routing" gate, for the button label's route part only. */
    var routesEnabled: (String) -> Boolean = { false }

    private var items: List<ModelPicker.Item> = emptyList()
    private var selected: String? = null
    private var variants: List<String> = emptyList()
    private var variant: String? = null
    private var route: String? = null
    private var frozen = false
    private var popup: KiloPickerPopup<KiloModelRow>? = null

    init {
        isEnabled = false
        text = " "
        syncTooltip()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isEnabled || items.isEmpty()) return
                showPopup()
            }
        })
    }

    /**
     * Push the current controller state: model items, the selected key, the route pin, effort
     * variants, the selected effort, and the freeze flag. Called from SessionUi on WorkspaceReady.
     */
    fun sync(
        items: List<ModelPicker.Item>,
        selected: String?,
        route: String?,
        variants: List<String>,
        variant: String?,
        frozen: Boolean,
    ) {
        this.items = items
        this.selected = selected?.let { key -> items.firstOrNull { it.key == key || it.id == key }?.key }
        this.route = route
        this.variants = variants
        this.variant = variant
        this.frozen = frozen
        refresh()
    }

    internal fun selectionKeyForTest(): String? = selected

    internal fun frozenForTest(): Boolean = frozen

    /** Model keys carrying a visible preset — the star state of the catalog rows. */
    internal fun starredKeysForTest(): Set<String> = starredKeys()

    override fun syncTooltip() {
        toolTipText = if (frozen) {
            tip(KiloBundle.message("param.picker.tooltip"), KiloBundle.message("param.picker.locked.hint"))
        } else {
            tip(KiloBundle.message("param.picker.tooltip"))
        }
    }

    fun canCycle(): Boolean = nextCycleTarget() != null

    /** Ctrl+2: apply the next visible preset, or the next flat favorite when the project has none. */
    fun cycle() {
        nextCycleTarget()?.let(::apply)
    }

    private fun selectedItem(): ModelPicker.Item? = selected?.let { key -> items.firstOrNull { it.key == key } }

    private fun starredKeys(): Set<String> = presets().map { "${it.provider}/${it.modelId}" }.toSet()

    private fun refresh() {
        val item = selectedItem()
        if (item == null) {
            isEnabled = false
            text = " "
            icon = null
            syncTooltip()
            cursor = Cursor.getDefaultCursor()
            return
        }
        val label = buildString {
            append(ModelText.buttonLabel(item))
            if (routesApply(routesEnabled, item)) {
                append(" · ")
                append(route ?: KiloBundle.message("param.picker.auto"))
            }
            if (variants.isNotEmpty()) {
                append(" · ")
                append(variant ?: KiloBundle.message("param.picker.auto"))
            }
        }
        text = "$label ▾"
        icon = if (frozen) AllIcons.Nodes.Locked else null
        horizontalTextPosition = SwingConstants.LEFT
        iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
        isEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        syncTooltip()
    }

    private fun rowsFor(query: String): List<KiloModelRow> = kiloModelRows(
        presets = presets(),
        emptyText = KiloBundle.message("param.picker.favorites.empty"),
        items = items,
        frozen = frozen,
        query = query,
    )

    private fun showPopup() {
        val data = CollectionListModel(rowsFor(""))
        val details = KiloModelDetailsPanel(starred = ::starredKeys, toggle = ::toggleStar)
        val shown = KiloPickerPopup(
            anchor = this,
            placement = KiloPickerPopup.Placement.ABOVE,
            rows = ::rowsFor,
            model = data,
            renderer = KiloModelPopupRenderer(
                model = data,
                active = ::selectedItem,
                activeRoute = { route },
                activeVariant = { variant },
                starred = ::starredKeys,
                locked = ::frozen,
            ),
            key = ::kiloModelKey,
            mode = KiloPickerPopup.Mode.Single,
            onPrimary = ::pick,
            sectionTitle = ::kiloModelSectionTitle,
            trailingHit = KiloModelPopupRenderer::isStarClick,
            onTrailing = ::toggleStarRow,
            search = true,
            favorites = favoritesBar(),
            details = details,
            onPreview = { row -> previewDetails(details, row) },
            expandStateKey = PARAM_PICKER_EXPANDED_KEY,
            minWidth = PARAM_PICKER_MIN_WIDTH,
        )
        val handle = shown.show()
        restoreFocusOnPick(handle)
        handle.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                this@KiloParameterPicker.popup = null
            }
        })
        popup = shown
    }

    /** Pinned Favorites bar: section caption on the left, the management-window entry on the right. */
    private fun favoritesBar(): JPanel {
        val edit = HoverIcon().apply {
            icon = AllIcons.Actions.Edit
            toolTipText = KiloBundle.message("param.picker.favorites.edit")
            accessibleContext.accessibleName = toolTipText
            addActionListener { onEditFavorites() }
        }
        val ins = PopupUtil.getListInsets(false, false)
        val pad = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
        val sep = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
        sep.caption = KiloBundle.message("model.picker.favorites")
        sep.setHideLine(false)
        return JPanel(BorderLayout()).apply {
            background = popupBackground
            border = JBUI.Borders.empty(UiStyle.Gap.sm(), ins.left, UiStyle.Gap.sm(), pad)
            add(sep, BorderLayout.CENTER)
            add(edit, BorderLayout.EAST)
        }
    }

    private fun previewDetails(details: KiloModelDetailsPanel, row: KiloModelRow?) {
        when (row) {
            is KiloModelRow.Preset -> details.update(
                items.firstOrNull { it.provider == row.preset.provider && it.id == row.preset.modelId },
                row.preset,
            )
            is KiloModelRow.Model -> details.update(row.row.item ?: selectedItem())
            else -> details.update(selectedItem())
        }
    }

    private fun pick(row: KiloModelRow) {
        when (row) {
            is KiloModelRow.Lock -> onUnlock()
            is KiloModelRow.Preset -> if (!frozen) onPreset(row.preset)
            is KiloModelRow.Model -> {
                val item = row.row.item ?: return
                if (frozen) return
                onModel(item)
            }
            is KiloModelRow.Empty -> Unit
        }
    }

    private fun toggleStarRow(row: KiloModelRow) {
        (row as? KiloModelRow.Model)?.row?.item?.let(::toggleStar)
    }

    private fun toggleStar(item: ModelPicker.Item) {
        onStarToggle(item)
    }

    private fun currentIndex(pool: List<KiloModelRow>, active: ModelPicker.Item?): Int = pool.indexOfFirst { row ->
        when (row) {
            is KiloModelRow.Preset -> active != null &&
                row.preset.provider == active.provider &&
                row.preset.modelId == active.id &&
                row.preset.routeTag == route &&
                row.preset.variant == variant
            is KiloModelRow.Model -> active != null && row.row.key == active.key
            is KiloModelRow.Lock, is KiloModelRow.Empty -> false
        }
    }

    private fun nextCycleTarget(): KiloModelRow? {
        val pool = kiloModelCyclePool(presets(), items, favorites())
        if (pool.isEmpty()) return null
        val index = currentIndex(pool, selectedItem())
        val next = pool[(index + 1).mod(pool.size)]
        if (index >= 0 && pool[index] == next) return null
        return next
    }

    private fun apply(row: KiloModelRow) {
        when (row) {
            is KiloModelRow.Preset -> onPreset(row.preset)
            is KiloModelRow.Model -> row.row.item?.let(onModel)
            is KiloModelRow.Lock, is KiloModelRow.Empty -> Unit
        }
    }
}
