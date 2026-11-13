package com.mcc.app

import com.mcc.app.game.Texts
import com.mcc.app.ui.EggCrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 알 깨기 연출과 정확한 부화 진행도 */
class EggHatchTest {

    @Test
    fun cracks_grow_with_each_tap_and_the_third_tap_bursts() {
        assertTrue(EggCrack.lines(0).isEmpty())
        val one = EggCrack.lines(1)
        val two = EggCrack.lines(2)
        assertTrue(one.isNotEmpty())
        assertTrue("누를수록 금이 늘어남", two.size > one.size)
        assertEquals("앞 단계 금은 그대로 남음", one.first().toList(), two.first().toList())
        assertFalse(EggCrack.burstsOn(1))
        assertFalse(EggCrack.burstsOn(2))
        assertTrue(EggCrack.burstsOn(EggCrack.TAPS_TO_HATCH))
    }

    @Test
    fun crack_points_stay_inside_the_egg() {
        EggCrack.lines(5).forEach { line ->
            assertEquals("x,y 쌍", 0, line.size % 2)
            line.forEach { v -> assertTrue("0~1 사이: $v", v in 0f..1f) }
        }
    }

    @Test
    fun opaque_bounds_skip_transparent_margins() {
        // 100×100 그림, (20..59, 10..89) 만 그려짐
        val b = EggCrack.opaqueBounds(100, 100, step = 1) { x, y -> if (x in 20..59 && y in 10..89) 255 else 0 }
        assertEquals(listOf(20, 10, 60, 90), b.toList())
        // 전부 투명하면 그림 전체
        assertEquals(listOf(0, 0, 100, 100), EggCrack.opaqueBounds(100, 100) { _, _ -> 0 }.toList())
    }

    @Test
    fun exact_progress_text() {
        assertEquals("부화까지 57.6% · 17.3 / 30 km · 남은 12.7 km", Texts.eggProgress(17.3f, 30f))
        assertEquals("부화까지 0.0% · 0.0 / 30 km · 남은 30.0 km", Texts.eggProgress(0f, 30f))
        // 다 안 찼으면 100% 로 보이지 않고, 남은 거리도 0.0 으로 보이지 않음
        val almost = Texts.eggProgress(29.97f, 30f)
        assertTrue(almost, almost.startsWith("부화까지 99.9%"))
        assertTrue(almost, almost.endsWith("남은 0.1 km"))
        assertTrue(Texts.eggProgress(30f, 30f).startsWith("부화 준비 완료"))
        assertTrue(Texts.eggProgress(42f, 30f).startsWith("부화 준비 완료"))
    }
}
