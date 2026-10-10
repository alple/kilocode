package ai.kilocode.client.deluxe

import ai.kilocode.rpc.dto.ModelCacheCostDto
import ai.kilocode.rpc.dto.ModelCostDto
import ai.kilocode.rpc.dto.ModelDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiloFavoritesDeltaTest {

    private fun cost(input: Double, output: Double, read: Double? = null) = ModelCostDto(
        input = input,
        output = output,
        cache = read?.let { ModelCacheCostDto(read = it, write = 0.0) },
    )

    private fun model(cost: ModelCostDto? = null, inputPrice: Double? = null, outputPrice: Double? = null) = ModelDto(
        id = "m",
        name = "M",
        inputPrice = inputPrice,
        outputPrice = outputPrice,
        cost = cost,
    )

    @Test
    fun blendedWithCacheRead() {
        val prices = kiloModelPrices(model(cost(2.0, 4.0, 0.5)))
        // 0.7 * read + 0.2 * in + 0.1 * out = 0.35 + 0.4 + 0.4
        assertEquals(1.15, prices!!.blended!!, 1e-9)
    }

    @Test
    fun blendedWithoutCacheRead() {
        val prices = kiloModelPrices(model(cost(2.0, 4.0, null)))
        // 0.9 * in + 0.1 * out = 1.8 + 0.4
        assertEquals(2.2, prices!!.blended!!, 1e-9)
    }

    @Test
    fun pricesFallBackToFlatFields() {
        val prices = kiloModelPrices(model(inputPrice = 1.0, outputPrice = 3.0))
        assertEquals(1.0, prices?.input)
        assertEquals(3.0, prices?.output)
        assertNull(prices?.cached)
        assertEquals(1.2, prices!!.blended!!, 1e-9)
    }

    @Test
    fun noPricesMeansNoBaseline() {
        assertNull(kiloModelPrices(model()))
        assertNull(kiloModelPrices(null))
    }

    @Test
    fun deltaSignsTowardCheaper() {
        val base = kiloModelPrices(model(cost(2.0, 4.0, 0.5)))
        val target = kiloModelPrices(model(cost(1.0, 3.0, 0.5)))
        val set = kiloPriceDelta(base, target)
        val head = set.headline
        assertTrue(head != null && head.cheaper)
        assertTrue(set.input!!.cheaper)
        assertTrue(set.output!!.cheaper)
        assertNull(set.cached) // cache read equal on both sides: no difference
    }

    @Test
    fun deltaPricierAndMissingRowsStayNeutral() {
        val base = kiloModelPrices(model(cost(2.0, 4.0, 0.5)))
        val target = kiloModelPrices(model(cost(3.0, 5.0, null)))
        val set = kiloPriceDelta(base, target)
        assertFalse(set.headline!!.cheaper)
        assertFalse(set.input!!.cheaper)
        assertFalse(set.output!!.cheaper)
        assertNull(set.cached) // target has no cache price: neutral, never free
    }

    @Test
    fun deltaWithoutEitherSideIsAllNeutral() {
        val set = kiloPriceDelta(null, kiloModelPrices(model(cost(1.0, 1.0))))
        assertNull(set.headline)
        assertNull(set.input)
        assertNull(set.output)
        assertNull(set.cached)
    }

    @Test
    fun equalPricesCarryNoDelta() {
        val prices = kiloModelPrices(model(cost(2.0, 4.0, 0.5)))
        val set = kiloPriceDelta(prices, prices)
        assertNull(set.headline)
        assertNull(set.input)
        assertNull(set.output)
        assertNull(set.cached)
    }

    @Test
    fun priceText() {
        assertEquals("Free", KiloPriceText.price(0.0))
        assertEquals("$2.50/1M", KiloPriceText.price(2.5))
        assertEquals("$0.0025/1M", KiloPriceText.price(0.0025))
        assertEquals("-$0.53/1M", KiloPriceText.delta(-0.534))
        assertEquals("+$1.20/1M", KiloPriceText.delta(1.2))
    }
}
