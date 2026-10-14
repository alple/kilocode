package ai.kilocode.client.settings.appearance

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.settings.ChatStyleSettingsListener
import ai.kilocode.client.util.edtWait
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JSpinner

class AppearanceConfigurableTest : BasePlatformTestCase() {

    private lateinit var settings: KiloAppearanceSettingsService
    private var styleChanges = 0

    override fun setUp() {
        super.setUp()
        settings = KiloAppearanceSettingsService()
        ApplicationManager.getApplication().messageBus
            .connect(testRootDisposable)
            .subscribe(ChatStyleSettingsListener.TOPIC, ChatStyleSettingsListener { styleChanges++ })
    }

    override fun tearDown() {
        try {
            settings.update(ChatFontSpec())
        } finally {
            super.tearDown()
        }
    }

    fun `test createComponent renders family picker, size spinners and preview`() = edt {
        val cfg = configurable()
        val root = cfg.createComponent() as Container

        assertEquals(1, combos(root).size)
        assertEquals(KiloBundle.message("settings.appearance.family.followIde"), combos(root).single().getItemAt(0))
        assertEquals(4, spinners(root).size)
        assertEquals(2, separators(root).size)
        assertEquals(4, previewLabels(root).size)
        assertFalse(cfg.isModified)
    }

    fun `test isModified tracks size changes`() = edt {
        val cfg = configurable()
        val root = cfg.createComponent() as Container

        spinners(root).first().value = 0.5

        assertTrue(cfg.isModified)
    }

    fun `test reset restores saved values`() = edt {
        settings.update(ChatFontSpec(thinking = 1.5f))
        val cfg = configurable()
        val root = cfg.createComponent() as Container

        spinners(root)[1].value = -2.0
        cfg.reset()

        assertEquals(1.5, spinners(root)[1].value)
        assertFalse(cfg.isModified)
    }

    fun `test apply persists offsets and notifies listeners`() = edt {
        val cfg = configurable()
        val root = cfg.createComponent() as Container

        spinners(root)[0].value = 0.5
        spinners(root)[1].value = -1.0
        spinners(root)[2].value = 2.0
        spinners(root)[3].value = 0.1

        assertEquals(0, styleChanges)
        cfg.apply()

        val spec = settings.spec()
        assertEquals(0.5f, spec.response)
        assertEquals(-1.0f, spec.thinking)
        assertEquals(2.0f, spec.tool)
        assertEquals(0.1f, spec.prompt)
        assertEquals(1, styleChanges)
        assertFalse(cfg.isModified)
    }

    fun `test family picker follows ide by default and applies a chosen family`() = edt {
        settings.update(ChatFontSpec(family = "MyTestFont"))
        val cfg = configurable()
        val root = cfg.createComponent() as Container
        val combo = combos(root).single()
        val familyIndex = (0 until combo.itemCount).first { combo.getItemAt(it) == "MyTestFont" }

        assertEquals("MyTestFont", combo.selectedItem)

        combo.selectedIndex = FOLLOW_IDE_INDEX
        cfg.apply()
        assertNull(settings.spec().family)

        // Re-seed the family, then a fresh page rebuilt from the persisted state keeps it available and selected.
        settings.update(ChatFontSpec(family = "MyTestFont"))
        val rebuilt = configurable()
        val reread = rebuilt.createComponent() as Container
        val rereadCombo = combos(reread).single()
        assertEquals("MyTestFont", rereadCombo.selectedItem)

        rereadCombo.selectedIndex = (0 until rereadCombo.itemCount).first { rereadCombo.getItemAt(it) == "MyTestFont" }
        rebuilt.apply()
        assertEquals("MyTestFont", settings.spec().family)
    }

    fun `test preview fonts follow the draft before apply`() = edt {
        val cfg = configurable()
        val root = cfg.createComponent() as Container
        val labels = previewLabels(root)

        spinners(root)[0].value = 2.0

        val response = labels.first { it.text == KiloBundle.message("settings.appearance.preview.response") }
        val prompt = labels.first { it.text == KiloBundle.message("settings.appearance.preview.prompt") }
        assertTrue(
            "response sample (${response.font.size2D}) should exceed prompt sample (${prompt.font.size2D})",
            response.font.size2D > prompt.font.size2D,
        )
    }

    private fun configurable() = AppearanceConfigurable(settings)

    private fun combos(root: Container): List<ComboBox<*>> = collect(root) { it as? ComboBox<*> }

    private fun spinners(root: Container): List<JSpinner> = collect(root) { it as? JSpinner }

    private fun separators(root: Container): List<TitledSeparator> = collect(root) { it as? TitledSeparator }

    private fun previewLabels(root: Container): List<JBLabel> {
        val samples = setOf(
            KiloBundle.message("settings.appearance.preview.response"),
            KiloBundle.message("settings.appearance.preview.thinking"),
            KiloBundle.message("settings.appearance.preview.tool"),
            KiloBundle.message("settings.appearance.preview.prompt"),
        )
        return collect(root) { (it as? JBLabel)?.takeIf { label -> label.text in samples } }
    }

    private fun <T : JComponent> collect(root: Container, cast: (JComponent) -> T?): List<T> {
        val found = mutableListOf<T>()
        collectInto(root, cast, found)
        return found
    }

    private fun <T : JComponent> collectInto(root: Container, cast: (JComponent) -> T?, found: MutableList<T>) {
        for (comp in root.components) {
            if (comp is JComponent) cast(comp)?.let { found.add(it) }
            if (comp is Container) collectInto(comp, cast, found)
        }
    }

    private fun edt(block: () -> Unit) = edtWait(block)

    private companion object {
        const val FOLLOW_IDE_INDEX = 0
    }
}
