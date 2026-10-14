package ai.kilocode.client.settings.appearance

import ai.kilocode.client.plugin.KiloBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent

/** Chat font appearance under Settings -> Tools -> Kilo Code -> Appearance. */
class AppearanceConfigurable(
    private val settings: KiloAppearanceSettingsService = KiloAppearanceSettingsService.getInstance(),
) : SearchableConfigurable, Configurable.NoScroll {
    private var ui: AppearanceSettingsUi? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = KiloBundle.message("settings.appearance.displayName")

    override fun createComponent(): JComponent = AppearanceSettingsUi(settings.spec()).also { ui = it }

    override fun isModified(): Boolean = ui?.modified() == true

    override fun apply() {
        val panel = ui ?: return
        settings.update(panel.value())
        settings.publish()
        panel.sync()
    }

    override fun reset() {
        ui?.resetForm()
    }

    override fun disposeUIResources() {
        ui = null
    }

    companion object {
        const val ID = "ai.kilocode.jetbrains.settings.appearance"
    }
}
