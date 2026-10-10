package ai.kilocode.client.deluxe

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.ModelDto
import java.util.Locale
import kotlin.math.abs

/**
 * Per-1M-token prices of one catalog model, as far as the catalog reports them (ADR-0003 details
 * block). A missing price stays null — deltas treat it as "unknown", never as zero.
 */
internal data class KiloModelPrices(
    val input: Double?,
    val output: Double?,
    val cached: Double?,
) {
    /**
     * Blended average the session popup's details pane already uses: with a cache-read price
     * `0.7·read + 0.2·input + 0.1·output`, else `0.9·input + 0.1·output`.
     */
    val blended: Double?
        get() {
            val inPrice = input ?: return null
            val outPrice = output ?: return null
            val read = cached
            return if (read != null) read * 0.7 + inPrice * 0.2 + outPrice * 0.1 else inPrice * 0.9 + outPrice * 0.1
        }
}

internal fun kiloModelPrices(dto: ModelDto?): KiloModelPrices? {
    dto ?: return null
    val cost = dto.cost
    val input = cost?.input ?: dto.inputPrice
    val output = cost?.output ?: dto.outputPrice
    if (input == null && output == null) return null
    return KiloModelPrices(input, output, cost?.cache?.read)
}

/** One price-row delta: target minus baseline, signed toward "cheaper" when negative. */
internal data class KiloPriceDelta(val diff: Double, val cheaper: Boolean)

internal data class KiloPriceDeltaSet(
    /** Blended-average headline delta. */
    val headline: KiloPriceDelta?,
    val input: KiloPriceDelta?,
    val output: KiloPriceDelta?,
    val cached: KiloPriceDelta?,
)

/**
 * RPG-style price comparison (ADR-0003): the composed preset's model against the preset currently
 * selected in the window list. A row is null when either side lacks that price — neutral, never
 * read as cheaper or pricier.
 */
internal fun kiloPriceDelta(base: KiloModelPrices?, target: KiloModelPrices?): KiloPriceDeltaSet =
    KiloPriceDeltaSet(
        headline = delta(base?.blended, target?.blended),
        input = delta(base?.input, target?.input),
        output = delta(base?.output, target?.output),
        cached = delta(base?.cached, target?.cached),
    )

private fun delta(base: Double?, target: Double?): KiloPriceDelta? {
    base ?: return null
    target ?: return null
    val diff = target - base
    if (diff == 0.0) return null // equal prices carry no arrow
    return KiloPriceDelta(diff, diff < 0.0)
}

/** Money text helpers shared by the management window's route rows and details block. */
internal object KiloPriceText {
    /** Per-1M token price, e.g. `$2.50/1M`; free models carry the shared free label. */
    fun price(value: Double): String =
        if (value == 0.0) KiloBundle.message("model.picker.free") else "$${fmt(value)}/1M"

    /** Signed per-1M delta, e.g. `-$0.53/1M` or `+$1.20/1M`; tiny values keep four decimals. */
    fun delta(value: Double): String {
        val sign = if (value < 0.0) "-" else "+"
        return "$sign$${fmt(abs(value))}/1M"
    }

    private fun fmt(value: Double): String {
        val digits = if (value < 0.01) 4 else 2
        return String.format(Locale.US, "%.${digits}f", value)
    }
}
