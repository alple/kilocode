package ai.kilocode.client.testing

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path
import java.util.Date

/**
 * Data-only descriptor for plugin-set logic tests: the guard scans descriptors and only ever reads
 * id, name, and the enabled flag, so the rest of the interface is dead weight here.
 */
class FakePluginDescriptor(
    private val id: String,
    private val name: String = id,
    private val enabled: Boolean = true,
) : IdeaPluginDescriptor {
    val idString: String = id

    override fun getPluginId(): PluginId = PluginId.getId(idString)
    override fun getPluginClassLoader(): ClassLoader = javaClass.classLoader
    override fun getPluginPath(): Path = Path.of("fake")
    override fun getDescription() = ""
    override fun getChangeNotes() = ""
    override fun getName() = name
    override fun getVersion() = ""
    override fun getProductCode() = ""
    override fun getReleaseDate(): Date = Date(0)
    override fun getReleaseVersion() = 0
    override fun isLicenseOptional() = false
    override fun getVendor() = ""
    override fun getResourceBundleBaseName() = ""
    override fun getCategory() = ""
    override fun getVendorEmail() = ""
    override fun getVendorUrl() = ""
    override fun getUrl() = ""
    override fun getSinceBuild() = ""
    override fun getUntilBuild() = ""
    override fun isEnabled() = enabled
    override fun setEnabled(enabled: Boolean) {}
    override fun getDependencies(): List<IdeaPluginDependency> = emptyList()
    override fun getDescriptorPath() = ""
}
