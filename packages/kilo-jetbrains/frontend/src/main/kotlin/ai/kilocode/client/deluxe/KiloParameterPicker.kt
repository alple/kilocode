package ai.kilocode.client.deluxe

import ai.kilocode.log.KiloLog
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.client.session.ui.model.ModelText
import ai.kilocode.client.ui.PickerButton
import ai.kilocode.client.ui.picker.PickerListRenderer
import ai.kilocode.client.ui.picker.PickerPopup
import ai.kilocode.rpc.dto.ModelSelectionDto
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.CollectionListModel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.SwingConstants

private const val PARAM_FAVORITE_CLICK_AREA = 32
private const val PARAM_PICKER_MIN_WIDTH = 480

/** Favorite-star click zone on model rows, shared with the upstream model picker's mechanic. */
internal fun isParamFavoriteClick(list: JList<*>, bounds: Rectangle, point: Point): Boolean =
    PickerListRenderer.trailingClickZone(list, bounds, point, PARAM_FAVORITE_CLICK_AREA)

/**
 * Session-header parameter picker (KILO-8.4, ADR-0001): one control for the model, its OpenRouter
 * route and the reasoning effort, with a lock chip for the freeze-after-first-message rule.
 *
 * The popup reuses the generic [PickerPopup] shell with fork-owned rows and a fork-owned renderer.
 * The route section renders only when the "enable model routing" option ([KiloRoutingOptions])
 * lists the selected model's provider; its endpoint list is fetched from the CLI on demand (popup
 * open and after every model pick). A pinned tag missing from the refreshed list renders a
 * recovery note while the pin itself stays active — staleness is never auto-cleared.
 */
internal class KiloParameterPicker : PickerButton() {

    /** A model was picked; the controller refuses while the parameter trio is frozen. */
    var onModel: (ModelPicker.Item) -> Unit = {}

    /** Pin or clear (null = Auto) the OpenRouter route for the selected model. */
    var onRoute: (String?) -> Unit = {}

    /** Pin a reasoning effort for the selected model. */
    var onVariant: (String) -> Unit = {}

    /** Auto effort: clear the pin and the store's remembered variant. */
    var onClearVariant: () -> Unit = {}

    /** Explicit unfreeze of the model/route/effort trio. */
    var onUnlock: () -> Unit = {}

    /** Favorite toggle for model rows, mirroring the upstream model picker. */
    var onFavoriteToggle: (ModelPicker.Item) -> Unit = {}

    /** Favorite ids for model-row ordering and the favorite section, same source as the upstream picker. */
    var favorites: () -> List<ModelSelectionDto> = { emptyList() }

    /** "Enable model routing" gate: provider id → route UI applies. */
    var routesEnabled: (String) -> Boolean = { false }

    /**
     * Route-list lookup for one model. The callee launches the lookup off the EDT and invokes
     * [done] on the EDT with the endpoint list, or [KiloRouteState.Failed] when it could not load.
     */
    var fetchRoutes: (provider: String, modelId: String, done: (KiloRouteState) -> Unit) -> Unit =
        { _, _, done -> done(KiloRouteState.Ready(emptyList())) }

    private var items: List<ModelPicker.Item> = emptyList()
    private var selected: String? = null
    private var variants: List<String> = emptyList()
    private var variant: String? = null
    private var route: String? = null
    private var frozen = false
    private var routeState: KiloRouteState? = null
    private var fetchToken = 0
    private var popup: PickerPopup<KiloParamRow>? = null

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

    internal fun favoriteKeysForTest(): Set<String> = favoriteKeys()

    override fun syncTooltip() {
        toolTipText = if (frozen) {
            tip(KiloBundle.message("param.picker.tooltip"), KiloBundle.message("param.picker.locked.hint"))
        } else {
            tip(KiloBundle.message("param.picker.tooltip"))
        }
    }

    private fun selectedItem(): ModelPicker.Item? = selected?.let { key -> items.firstOrNull { it.key == key } }

    private fun favoriteKeys(): Set<String> = favorites().map { "${it.providerID}/${it.modelID}" }.toSet()

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

    private fun rowsFor(query: String): List<KiloParamRow> {
        val item = selectedItem()
        val routing = item != null && routesApply(routesEnabled, item)
        return kiloParameterRows(
            items = items,
            selected = selected,
            favorites = favorites(),
            variants = variants,
            variant = variant,
            route = route,
            frozen = frozen,
            routes = if (routing) routeState else null,
            query = query,
        )
    }

    private fun showPopup() {
        val data = CollectionListModel(rowsFor(""))
        val shown = PickerPopup(
            anchor = this,
            placement = PickerPopup.Placement.ABOVE,
            rows = ::rowsFor,
            model = data,
            renderer = KiloParameterRenderer(
                model = data,
                activeModel = { selected },
                activeRoute = { route },
                activeVariant = { variant },
                favorites = ::favoriteKeys,
                locked = ::frozen,
            ),
            key = ::kiloParamKey,
            mode = PickerPopup.Mode.Multi,
            onPrimary = ::pick,
            sectionTitle = ::kiloParameterSectionTitle,
            trailingHit = ::isParamFavoriteClick,
            onTrailing = ::toggleFavorite,
            search = true,
            minWidth = PARAM_PICKER_MIN_WIDTH,
        )
        val handle = shown.show()
        restoreFocusOnPick(handle)
        handle.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                this@KiloParameterPicker.popup = null
            }
        })
        this.popup = shown
        refreshRoutes()
    }

    private fun pick(row: KiloParamRow) {
        when (row) {
            is KiloParamRow.Lock -> onUnlock()
            is KiloParamRow.Model -> {
                val item = row.row.item ?: return
                if (frozen) return
                onModel(item)
                // Routes and efforts follow the freshly picked model; the controller fires
                // WorkspaceReady, which re-syncs this picker before the popup refreshes.
                fetchRoutesFor(item)
            }
            is KiloParamRow.Route -> {
                if (frozen) return
                onRoute(row.tag)
            }
            is KiloParamRow.Effort -> {
                if (frozen) return
                val id = row.id
                if (id == null) onClearVariant() else onVariant(id)
            }
            is KiloParamRow.Note -> Unit
        }
    }

    private fun toggleFavorite(row: KiloParamRow) {
        val item = (row as? KiloParamRow.Model)?.row?.item ?: return
        if (frozen) return
        onFavoriteToggle(item)
    }

    private fun refreshRoutes() {
        val item = selectedItem() ?: return
        if (!routesApply(routesEnabled, item)) {
            routeState = null
            return
        }
        fetchRoutesFor(item)
    }

    private fun fetchRoutesFor(item: ModelPicker.Item) {
        val token = ++fetchToken
        routeState = KiloRouteState.Loading
        popup?.repaint()
        fetchRoutes(item.provider, item.id) { next ->
            if (token != fetchToken) return@fetchRoutes
            routeState = next
            if (next is KiloRouteState.Ready) {
                val pin = route
                if (pin != null && next.endpoints.none { it.tag == pin }) {
                    LOG.info("parameter picker: pinned route $pin is missing from the refreshed list; the pin stays active until re-picked")
                }
            }
            popup?.refresh(prefer = preferKey())
            popup?.repaint()
        }
    }

    private fun preferKey(): String = "m:${selected ?: "unset"}"

    private companion object {
        val LOG = KiloLog.create(KiloParameterPicker::class.java)
    }
}
