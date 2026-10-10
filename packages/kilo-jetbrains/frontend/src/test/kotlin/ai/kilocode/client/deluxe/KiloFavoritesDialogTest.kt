package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.ModelCostDto
import ai.kilocode.rpc.dto.ModelDto
import ai.kilocode.rpc.dto.ProviderDto
import ai.kilocode.rpc.dto.ProvidersDto
import ai.kilocode.rpc.dto.RouteEndpointDto
import ai.kilocode.rpc.dto.RoutePricingDto
import ai.kilocode.rpc.dto.RouteUptimeDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@Suppress("UnstableApiUsage")
class KiloFavoritesDialogTest : BasePlatformTestCase() {

    private fun model() = ModelDto(
        id = "acme/m1",
        name = "Acme One",
        contextLength = 200_000,
        cost = ModelCostDto(input = 2.0, output = 4.0, cache = null),
        variants = listOf("high"),
    )

    private fun catalog(): ProvidersDto = ProvidersDto(
        listOf(ProviderDto(id = "openrouter", name = "OpenRouter", models = mapOf("acme/m1" to model()))),
        emptyList(),
        emptyMap(),
    )

    private fun routes(): List<RouteEndpointDto> = listOf(
        RouteEndpointDto(tag = "parasail", providerName = "Parasail", pricing = RoutePricingDto(prompt = 1.0), uptime = RouteUptimeDto(last30m = 0.99)),
        RouteEndpointDto(tag = "inference.net", providerName = "Inference", pricing = RoutePricingDto(prompt = 2.0), uptime = RouteUptimeDto(last30m = 0.95)),
    )

    private fun routing(enabled: Boolean = true): KiloRoutingOptions =
        KiloRoutingOptions().apply { update(enabled, listOf("openrouter")) }

    private fun seeded(): KiloFavoritePresetStore {
        val store = KiloFavoritePresetStore()
        store.create("Ship", listOf(project.name), "openrouter", "acme/m1", "parasail", "high")
        return store
    }

    private fun dialog(store: KiloFavoritePresetStore = seeded(), options: KiloRoutingOptions = routing()) =
        KiloFavoritesDialog(project, null, store, { options }, { _ -> catalog() }, { _, _, _ -> routes() })

    /** Drains the dialog's coroutine-driven catalog/route fetches into the EDT. */
    private fun settle() {
        UIUtil.dispatchAllInvocationEvents()
        UIUtil.dispatchAllInvocationEvents()
    }

    /** Dispatches until [condition] holds; a watchdog for background→EDT coroutine hops. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(10)
        }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(condition())
    }

    private fun <T : javax.swing.JComponent> find(root: java.awt.Container, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        for (child in root.components) {
            if (type.isInstance(child)) return type.cast(child)
            if (child is java.awt.Container) find(child, type)?.let { return it }
        }
        return null
    }

    private fun tree(dialog: KiloFavoritesDialog): Tree = find(dialog.content(), Tree::class.java)!!

    private fun label(root: java.awt.Container, contains: String): JBLabel? {
        for (child in root.components) {
            if (child is JBLabel && child.text.contains(contains)) return child
            if (child is java.awt.Container) label(child, contains)?.let { return it }
        }
        return null
    }

    private fun nameField(dialog: KiloFavoritesDialog): JBTextField {
        val splitter = find(dialog.content(), JBSplitter::class.java)!!
        return find(splitter.secondComponent as java.awt.Container, JBTextField::class.java) ?: error("editor name field not found")
    }

    private fun firstGroupPath(dialog: KiloFavoritesDialog): TreePath {
        val root = tree(dialog).model.root as DefaultMutableTreeNode
        return TreePath((root.getChildAt(0) as DefaultMutableTreeNode).path)
    }

    fun `test empty selection shows the hint`() {
        val dialog = dialog()
        assertNotNull(label(dialog.content(), KiloBundle.message("favorites.window.empty.selection")))
    }

    fun `test routing off replaces the editor with the gate`() {
        val dialog = dialog(options = routing(enabled = false))
        dialog.selectLabel(project.name)
        assertNotNull(label(dialog.content(), KiloBundle.message("favorites.window.empty.routingOff")))
    }

    fun `test unavailable catalog replaces the editor with the gate`() {
        val dialog = KiloFavoritesDialog(project, null, seeded(), { routing() }, { _ -> null }, { _, _, _ -> routes() })
        dialog.selectLabel(project.name)
        await({ label(dialog.content(), KiloBundle.message("favorites.window.empty.catalog")) != null })
    }

    fun `test select label shows the first preset in the editor`() {
        val dialog = dialog()
        dialog.selectLabel(project.name)
        await({ nameField(dialog).text == "Ship" })
    }

    fun `test auto expand expands a collapsed group`() {
        val dialog = dialog()
        dialog.selectLabel(project.name)
        val path = firstGroupPath(dialog)
        tree(dialog).collapsePath(path)
        assertFalse(tree(dialog).isExpanded(path))
        assertTrue(dialog.autoExpand(path))
        assertTrue(tree(dialog).isExpanded(path))
        assertFalse(dialog.autoExpand(path))
        assertFalse(dialog.autoExpand(null))
    }

    fun `test validation passes on a healthy draft`() {
        val dialog = dialog()
        dialog.selectLabel(project.name)
        assertNull(dialog.validateDraft())
    }

    fun `test apply writes through to the store`() {
        val store = seeded()
        val dialog = dialog(store)
        dialog.selectLabel(project.name)
        await({ nameField(dialog).text == "Ship" })
        nameField(dialog).text = "Renamed"
        dialog.applyDraft()
        assertEquals("Renamed", store.all().single().name)
        // The stored trio is untouched by a pure rename.
        assertEquals("openrouter", store.all().single().provider)
        assertEquals("parasail", store.all().single().routeTag)
    }

    fun `test edits without apply leave the store untouched`() {
        val store = seeded()
        val dialog = dialog(store)
        dialog.selectLabel(project.name)
        await({ nameField(dialog).text == "Ship" })
        nameField(dialog).text = "Drift"
        assertEquals("Ship", store.all().single().name)
    }
}
