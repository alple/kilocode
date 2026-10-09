package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.ui.model.ModelPicker
import ai.kilocode.rpc.dto.ModelAutoRoutingDto
import ai.kilocode.rpc.dto.ModelCostDto
import com.intellij.ui.components.JBLabel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container

/**
 * Covers the Deluxe details sidecar (ADR-0003): model previews keep the upstream metadata, preset
 * previews add the route/effort block, and presets whose model is missing from the catalog still
 * preview their summary. The star reflects the project's labeled-preset visibility.
 */
class KiloModelDetailsPanelTest : BasePlatformTestCase() {

    private fun item(id: String = "auto") = ModelPicker.Item(
        id = id,
        display = "Kilo Auto",
        provider = "kilo",
        providerName = "Kilo",
        cost = ModelCostDto(1.0, 2.0),
        autoRouting = ModelAutoRoutingDto(listOf("openai/gpt", "anthropic/claude")),
    )

    private fun preset(
        name: String = "Fast",
        provider: String = "openrouter",
        modelId: String = "z-ai/glm-5.3-flash",
        routeTag: String? = "parasail",
        variant: String? = "high",
    ) = KiloFavoritePreset(
        id = name.lowercase(),
        name = name,
        labels = listOf("p"),
        provider = provider,
        modelId = modelId,
        routeTag = routeTag,
        variant = variant,
    )

    fun `test a model preview keeps the upstream metadata without a parameters block`() {
        val panel = KiloModelDetailsPanel(starred = { emptySet() }, toggle = {})

        panel.update(item())

        assertTrue(panel.labels().any { it.contains("Auto") })
        // No preset → no route/effort block.
        assertEquals(emptyList<String>(), panel.labels().filter { it == "parasail" || it == "high" })
    }

    fun `test a preset preview adds its route and effort block`() {
        val panel = KiloModelDetailsPanel(starred = { emptySet() }, toggle = {})

        panel.update(item(), preset())

        assertTrue(panel.labels().any { it == "parasail" })
        assertTrue(panel.labels().any { it == "high" })
        assertTrue(panel.labels().any { it.contains("Route") })
        assertTrue(panel.labels().any { it.contains("Reasoning effort") })
    }

    fun `test preset pins fall back to Auto in the preview`() {
        val panel = KiloModelDetailsPanel(starred = { emptySet() }, toggle = {})

        panel.update(item(), preset(routeTag = null, variant = null))

        assertTrue(panel.labels().any { it == KiloBundle.message("param.picker.auto") })
    }

    fun `test a preset whose model is missing from the catalog still previews its summary`() {
        val panel = KiloModelDetailsPanel(starred = { emptySet() }, toggle = {})

        panel.update(null, preset())

        assertTrue(panel.labels().any { it == "Fast" })
        assertTrue(panel.labels().any { it.contains("openrouter / z-ai/glm-5.3-flash") })
        assertTrue(panel.labels().any { it == "parasail" })
        assertTrue(panel.labels().any { it == "high" })
    }

    fun `test the star reflects preset visibility`() {
        val starred = mutableSetOf("kilo/auto")
        val panel = KiloModelDetailsPanel(starred = { starred }, toggle = {})

        panel.update(item())
        assertTrue(panel.labels().any { it.contains("Remove from this project's favorites") })

        starred.clear()
        panel.update(item())
        assertTrue(panel.labels().any { it.contains("Add to this project's favorites") })
    }

    fun `test empty preview resets the parameters block`() {
        val panel = KiloModelDetailsPanel(starred = { emptySet() }, toggle = {})

        panel.update(item(), preset())
        panel.update(null as ModelPicker.Item?)

        assertEquals(
            emptyList<String>(),
            panel.labels().filter { it == "parasail" || it == "high" },
        )
    }

    private fun Component.labels(): List<String> {
        if (!isVisible) return emptyList()
        val own = if (this is JBLabel) {
            listOfNotNull(text.orEmpty().takeIf { it.isNotBlank() }, toolTipText)
        } else {
            emptyList()
        }
        if (this !is Container) return own
        return own + components.flatMap { it.labels() }
    }
}
