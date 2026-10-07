package com.mikehildner.blackjack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicStrategyTest {

    private val s17 = Rules(dealerHitsSoft17 = false)
    private val h17 = Rules(dealerHitsSoft17 = true)

    private fun cell(section: BasicStrategy.Section, key: Int, up: Int, rules: Rules = s17) =
        BasicStrategy.chartCell(section, key, up, rules)

    @Test
    fun `hard totals follow the classic chart`() {
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 8, 6))
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.HARD, 9, 3))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 9, 2))
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.HARD, 10, 9))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 10, 10))
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.HARD, 11, 10))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 12, 2))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 12, 3))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 12, 4))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 13, 2))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 13, 7))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 17, 11))
    }

    @Test
    fun `11 versus ace depends on H17`() {
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 11, 11, s17))
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.HARD, 11, 11, h17))
    }

    @Test
    fun `surrender cells appear only when the table allows it`() {
        assertEquals(ChartCode.SURRENDER_HIT, cell(BasicStrategy.Section.HARD, 16, 10))
        assertEquals(ChartCode.SURRENDER_HIT, cell(BasicStrategy.Section.HARD, 16, 9))
        assertEquals(ChartCode.SURRENDER_HIT, cell(BasicStrategy.Section.HARD, 15, 10))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 15, 9))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.HARD, 16, 10, Rules(lateSurrender = false)))
        assertEquals(ChartCode.SURRENDER_STAND, cell(BasicStrategy.Section.HARD, 17, 11, h17))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.HARD, 17, 11, s17))
    }

    @Test
    fun `soft totals`() {
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.SOFT, 13, 5))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.SOFT, 13, 4))
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.SOFT, 17, 3))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.SOFT, 18, 2, s17))
        assertEquals(ChartCode.DOUBLE_STAND, cell(BasicStrategy.Section.SOFT, 18, 2, h17))
        assertEquals(ChartCode.DOUBLE_STAND, cell(BasicStrategy.Section.SOFT, 18, 6))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.SOFT, 18, 8))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.SOFT, 18, 9))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.SOFT, 19, 6, s17))
        assertEquals(ChartCode.DOUBLE_STAND, cell(BasicStrategy.Section.SOFT, 19, 6, h17))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.SOFT, 20, 6, h17))
    }

    @Test
    fun `pairs`() {
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 11, 11), "always split aces")
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 8, 10), "always split eights")
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.PAIRS, 10, 6), "never split tens")
        assertEquals(ChartCode.DOUBLE_HIT, cell(BasicStrategy.Section.PAIRS, 5, 6), "fives are a hard 10")
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.PAIRS, 9, 7))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 9, 8))
        assertEquals(ChartCode.STAND, cell(BasicStrategy.Section.PAIRS, 9, 10))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 7, 7))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.PAIRS, 7, 8))
    }

    @Test
    fun `small pairs depend on double after split`() {
        val das = Rules(doubleAfterSplit = true)
        val noDas = Rules(doubleAfterSplit = false)
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 4, 5, das))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.PAIRS, 4, 5, noDas))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 2, 2, das))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.PAIRS, 2, 2, noDas))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 6, 2, das))
        assertEquals(ChartCode.HIT, cell(BasicStrategy.Section.PAIRS, 6, 2, noDas))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 6, 3, noDas))
    }

    @Test
    fun `eights against an ace surrender under H17 with surrender`() {
        assertEquals(ChartCode.SURRENDER_SPLIT, cell(BasicStrategy.Section.PAIRS, 8, 11, h17))
        assertEquals(ChartCode.SPLIT, cell(BasicStrategy.Section.PAIRS, 8, 11, s17))
    }

    @Test
    fun `recommendation falls back when the chart action is not available`() {
        val threeCardEleven = Hand.of(10, "2h", "4c", "5d")
        val rec = BasicStrategy.recommend(threeCardEleven, Card.of("6s"), s17, setOf(Action.HIT, Action.STAND))
        assertEquals(Action.HIT, rec.action)
        assertEquals(ChartCode.HIT, rec.chart, "with three cards the chart itself says hit")

        val sixteen = Hand.of(10, "10h", "6c")
        val noSurrender = BasicStrategy.recommend(sixteen, Card.of("10s"), s17, setOf(Action.HIT, Action.STAND, Action.DOUBLE))
        assertEquals(Action.HIT, noSurrender.action)
        assertEquals(ChartCode.SURRENDER_HIT, noSurrender.chart)
        assertTrue(noSurrender.reason.contains("not available"))
    }

    @Test
    fun `pair is played as a total when splitting is not possible`() {
        val eights = Hand.of(10, "8h", "8c")
        val rec = BasicStrategy.recommend(eights, Card.of("6s"), s17, setOf(Action.HIT, Action.STAND, Action.DOUBLE))
        assertEquals(Action.STAND, rec.action, "hard 16 vs 6 stands")
    }

    @Test
    fun `every recommendation comes with an explanation`() {
        for (section in BasicStrategy.Section.entries) {
            for (row in BasicStrategy.rows(section)) {
                for (up in BasicStrategy.dealerColumns) {
                    val cell = BasicStrategy.chartCell(section, row, up, h17)
                    assertTrue(cell.code.isNotEmpty())
                }
            }
        }
        val rec = BasicStrategy.recommend(Hand.of(10, "Ah", "7c"), Card.of("9s"), s17, setOf(Action.HIT, Action.STAND, Action.DOUBLE))
        assertEquals(Action.HIT, rec.action)
        assertTrue(rec.reason.length > 40)
    }
}
