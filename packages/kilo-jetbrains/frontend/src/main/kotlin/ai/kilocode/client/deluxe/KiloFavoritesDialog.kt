package ai.kilocode.client.deluxe

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.kiloRoot
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.telemetry.Telemetry
import ai.kilocode.client.ui.UiStyle
import ai.kilocode.rpc.dto.ProvidersDto
import ai.kilocode.rpc.dto.RouteEndpointDto
import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnActionButton
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.ActionEvent
import java.util.WeakHashMap
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.TransferHandler
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * The favorites management window (ADR-0003, KILO-9.3): an accordion of label groups with the
 * presets visible under each on the left, and a live editor for the selected preset on the right.
 * Edits buffer in a [KiloFavoritesDraft] and reach the store only through OK or Apply — Cancel
 * discards. One window per project: reopening focuses the existing one and re-selects the
 * invoking project's label.
 */
internal class KiloFavoritesDialog(
    private val project: Project,
    directory: String?,
    private val store: KiloFavoritePresetStore = KiloFavoritePresetStore.getInstance(),
    private val routing: () -> KiloRoutingOptions = { KiloRoutingOptions.getInstance() },
    private val catalog: suspend (dir: String) -> ProvidersDto?,
    private val routes: suspend (dir: String, author: String, slug: String) -> List<RouteEndpointDto>,
) : DialogWrapper(project, false) {

    private sealed interface Node {
        data class Group(val label: String?) : Node

        data class Preset(val id: String) : Node
    }

    private data class Where(val label: String?, val index: Int, val groupPath: TreePath?)

    private class PresetData(val id: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val draft = KiloFavoritesDraft(store)
    private val collapsed = mutableSetOf<String?>()
    private var selection: String? = null
    private var preselect = project.name
    private var muted = false
    private var initial = true

    private val search = SearchTextField(false).apply {
        textEditor.emptyText.text = KiloBundle.message("favorites.window.search")
    }
    private val tree = Tree().apply {
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = Renderer()
    }
    private val editor: KiloFavoritesEditor
    private var body: JComponent? = null

    private val applyAction = object : DialogWrapper.DialogWrapperAction(CommonBundle.getApplyButtonText()) {
        override fun doAction(e: ActionEvent) {
            if (doValidate() == null) applyDraft()
        }
    }

    init {
        title = KiloBundle.message("favorites.window.title")
        isModal = false
        tree.transferHandler = Drag()
        tree.dropMode = DropMode.ON_OR_INSERT
        // Drag setup requires a headful toolkit; tests and headless imports skip it.
        if (!java.awt.GraphicsEnvironment.isHeadless()) tree.dragEnabled = true
        tree.addTreeSelectionListener {
            if (muted) return@addTreeSelectionListener
            selection = selectedId()
            editor.show(selection)
        }
        search.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = rebuild()
            override fun removeUpdate(e: DocumentEvent) = rebuild()
            override fun changedUpdate(e: DocumentEvent) = rebuild()
        })
        editor = KiloFavoritesEditor(
            draft,
            scope,
            routing,
            catalog = { catalog(directory ?: project.kiloRoot() ?: "") },
            routes = { author, slug -> routes(directory ?: project.kiloRoot() ?: "", author, slug) },
            baseline = { draft.preset(selection) },
            onEdited = { rebuild(); syncActions() },
        )
        init()
        rebuild()
    }

    override fun createCenterPanel(): JComponent {
        val left = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(UiStyle.Gap.sm())
            add(search, BorderLayout.NORTH)
            add(pane(), BorderLayout.CENTER)
        }
        val right = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(UiStyle.Gap.md())
            add(editor, BorderLayout.CENTER)
        }
        return JBSplitter(false, SPLIT_RATIO).apply {
            firstComponent = left
            secondComponent = right
            preferredSize = JBDimension(SPLIT_WIDTH, SPLIT_HEIGHT)
        }.also { body = it }
    }

    /**
     * The built center panel. [DialogWrapper.getContentPane][getContentPane] stays null until the
     * window is shown, so callers that need the laid-out content before `show()` use this.
     */
    internal fun content(): JComponent = body ?: error("dialog content not built yet")

    override fun createActions(): Array<javax.swing.Action> = arrayOf(okAction, applyAction, cancelAction)

    override fun getPreferredFocusedComponent(): JComponent = tree

    override fun getDimensionServiceKey(): String = DIMENSION_KEY

    override fun doValidate(): ValidationInfo? {
        val violation = draft.validate() ?: return null
        reveal(violation.id)
        return ValidationInfo(violation.message)
    }

    /** The window's validation entry, shared by OK, Apply and any future menu action. */
    internal fun validateDraft(): ValidationInfo? = doValidate()

    override fun doOKAction() {
        draft.apply()
        rebuild()
        Telemetry.send("Favorite Presets Saved", emptyMap())
        super.doOKAction()
    }

    override fun dispose() {
        opened.remove(project)
        scope.cancel()
        super.dispose()
    }

    /** Commits the draft and keeps the window open for further edits. */
    internal fun applyDraft() {
        draft.apply()
        rebuild()
        Telemetry.send("Favorite Presets Saved", emptyMap())
    }

    @RequiresEdt
    private fun rebuild() {
        muted = true
        val root = DefaultMutableTreeNode("root")
        var group: DefaultMutableTreeNode? = null
        for (row in draft.rows(search.text)) {
            when (row) {
                is KiloFavoritesDraft.Row.Group -> {
                    group = DefaultMutableTreeNode(Node.Group(row.label))
                    root.add(group)
                }
                is KiloFavoritesDraft.Row.Entry -> group?.add(DefaultMutableTreeNode(Node.Preset(row.id)))
            }
        }
        tree.model = DefaultTreeModel(root)
        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as DefaultMutableTreeNode
            val label = (node.userObject as Node.Group).label
            if (label !in collapsed) tree.expandPath(TreePath(node.path))
        }
        restore(root)
        muted = false
        syncActions()
    }

    private fun restore(root: DefaultMutableTreeNode) {
        val wanted = selection ?: if (initial) firstOf(findGroup(root, preselect)) else null
        initial = false
        val node = findPreset(root, wanted)
        if (node == null) {
            tree.clearSelection()
            return
        }
        tree.selectionPath = TreePath(node.path)
        tree.scrollPathToVisible(TreePath(node.path))
    }

    private fun firstOf(group: DefaultMutableTreeNode?): String? =
        ((group?.getChildAt(0) as? DefaultMutableTreeNode)?.userObject as? Node.Preset)?.id

    private fun findGroup(root: DefaultMutableTreeNode, label: String?): DefaultMutableTreeNode? {
        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as DefaultMutableTreeNode
            val group = node.userObject as? Node.Group ?: continue
            if (group.label?.equals(label, ignoreCase = true) == true) return node
        }
        return null
    }

    private fun findPreset(root: DefaultMutableTreeNode, id: String?): DefaultMutableTreeNode? {
        id ?: return null
        val stack = ArrayDeque<DefaultMutableTreeNode>()
        stack += root
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            if ((node.userObject as? Node.Preset)?.id == id) return node
            for (index in 0 until node.childCount) stack += node.getChildAt(index) as DefaultMutableTreeNode
        }
        return null
    }

    private fun selectedId(): String? =
        ((tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Node.Preset)?.id

    private fun selectedGroup(): Node.Group? =
        (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Node.Group

    /** The left pane: the accordion tree with its add/remove/duplicate/alphabetize toolbar. */
    private fun pane(): JComponent = ToolbarDecorator.createDecorator(tree)
        .setAddAction { addPreset() }
        .setRemoveAction { removeSelected() }
        .addExtraAction(Button(KiloBundle.message("favorites.window.duplicate"), AllIcons.Actions.Copy, ::duplicateSelected) { selection != null })
        .addExtraAction(Button(KiloBundle.message("favorites.window.orderAlphabetically"), AllIcons.ObjectBrowser.SortByType, ::alphabetizeSelected) { selectedGroup()?.label != null })
        .createPanel()

    private fun syncActions() {
        val modified = draft.isModified()
        okAction.isEnabled = modified
        applyAction.isEnabled = modified
    }

    /** Expands a hovered group during a drag; returns whether it was collapsed before. */
    @RequiresEdt
    internal fun autoExpand(path: TreePath?): Boolean {
        val node = (path?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Node.Group ?: return false
        if (node.label == null || tree.isExpanded(path)) return false
        tree.expandPath(path)
        collapsed.remove(node.label)
        return true
    }

    /** Selects [id] in the tree and editor, e.g. to navigate to a preset that failed validation. */
    private fun reveal(id: String) {
        selection = id
        rebuild()
        editor.show(id)
    }

    private fun addPreset() {
        reveal(draft.add(""))
    }

    private fun removeSelected() {
        val id = selection ?: return
        draft.remove(id)
        selection = null
        rebuild()
        editor.show(null)
    }

    private fun duplicateSelected() {
        selection = draft.duplicate(selection ?: return) ?: return
        rebuild()
        editor.show(selection)
    }

    private fun alphabetizeSelected() {
        draft.orderAlphabetically(selectedGroup()?.label ?: return)
        rebuild()
    }

    /** Selects the given label group, expanding it, and selects its first preset. */
    @RequiresEdt
    internal fun selectLabel(label: String?) {
        preselect = label ?: return
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        val node = findGroup(root, label) ?: return
        collapsed.remove((node.userObject as Node.Group).label)
        tree.expandPath(TreePath(node.path))
        selection = firstOf(node)
        rebuild()
        editor.show(selection)
    }

    private inner class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is Node.Group -> {
                    append(node.label ?: KiloBundle.message("favorites.window.group.unlabeled"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${draft.groupOf(node.label).size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is Node.Preset -> {
                    val preset = draft.preset(node.id) ?: return
                    append(preset.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("   ${preset.provider}/${preset.modelId}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    preset.routeTag?.let { append("   $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                    preset.variant?.let { append("   $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                }
                else -> {}
            }
        }
    }

    private inner class Drag : TransferHandler() {
        override fun getSourceActions(c: JComponent): Int = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val id = selectedId() ?: return null
            return Transfer(PresetData(id))
        }

        override fun canImport(support: TransferSupport): Boolean {
            val where = locate(support) ?: return false
            autoExpand(where.groupPath)
            return where.label != null
        }

        override fun importData(support: TransferSupport): Boolean {
            val where = locate(support) ?: return false
            val data = support.transferable.getTransferData(FLAVOR) as? PresetData ?: return false
            draft.move(data.id, where.label, where.index)
            selection = data.id
            rebuild()
            return true
        }

        private fun locate(support: TransferSupport): Where? {
            if (!support.isDrop) return null
            val location = support.dropLocation as? javax.swing.JTree.DropLocation ?: return null
            val node = location.path?.lastPathComponent as? DefaultMutableTreeNode ?: return null
            return when (val value = node.userObject) {
                is Node.Group -> {
                    val label = value.label ?: return null
                    Where(label, draft.groupOf(label).size, location.path)
                }
                is Node.Preset -> {
                    val parent = node.parent as? DefaultMutableTreeNode ?: return null
                    val group = parent.userObject as? Node.Group ?: return null
                    val index = if (location.childIndex >= 0) location.childIndex else parent.getIndex(node)
                    Where(group.label, index, TreePath(parent.path))
                }
                else -> null
            }
        }
    }

    private class Transfer(private val data: PresetData) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(FLAVOR)

        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == FLAVOR

        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != FLAVOR) throw UnsupportedFlavorException(flavor)
            return data
        }
    }

    private inner class Button(label: String, icon: Icon, private val run: () -> Unit, private val gate: () -> Boolean) :
        AnActionButton(label, icon) {
        override fun actionPerformed(e: AnActionEvent) = run()

        override fun updateButton(e: AnActionEvent) {
            e.presentation.isEnabled = gate()
        }
    }

    companion object {
        private val opened = WeakHashMap<Project, KiloFavoritesDialog>()

        /** Opens (or focuses) the window for [project], preselecting the project's label group. */
        fun show(project: Project, directory: String?) {
            val existing = opened[project]
            if (existing != null && !existing.isDisposed) {
                existing.window.toFront()
                existing.selectLabel(project.name)
                return
            }
            Telemetry.send("Favorites Window Opened", emptyMap())
            val dialog = KiloFavoritesDialog(
                project,
                directory,
                catalog = { dir -> service<KiloWorkspaceService>().models(dir).providers },
                routes = { dir, author, slug -> service<KiloWorkspaceService>().routes(dir, author, slug) },
            )
            opened[project] = dialog
            dialog.show()
        }

        const val DIMENSION_KEY = "kilo.favorites.window"
        const val SPLIT_WIDTH = 780
        const val SPLIT_HEIGHT = 460
        const val SPLIT_RATIO = 0.4f
        val FLAVOR = DataFlavor(PresetData::class.java, "kilo-favorite-preset")
    }
}
