package com.mcc.app

import com.mcc.app.ui.HitTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 캐릭터 뒤 소품 잡기: 소품의 '그려진 곳'만 소품으로 봅니다. */
class DecorTouchTest {
    // 100×100 그림, 가운데 40×40 만 불투명 (나머지 투명) — 200×200 칸에 가운데 맞춤
    private val alpha = { x: Int, y: Int -> if (x in 30..69 && y in 30..69) 255 else 0 }

    @Test
    fun solid_part_of_the_decor_is_found_even_under_the_character() {
        assertTrue(HitTest.solidNear(200, 200, 100, 100, 100f, 100f, 0f, alpha))
    }

    @Test
    fun transparent_corner_of_the_decor_is_not_the_decor() {
        assertFalse(HitTest.solidNear(200, 200, 100, 100, 10f, 10f, 0f, alpha))
        assertFalse(HitTest.solidNear(200, 200, 100, 100, -5f, 100f, 0f, alpha))
    }

    @Test
    fun finger_radius_reaches_the_edge() {
        // 불투명 영역 가장자리(칸 좌표 60)에서 8px 바깥을 눌러도 손가락 여유 10px 이면 잡힘
        assertFalse(HitTest.solidNear(200, 200, 100, 100, 52f, 100f, 0f, alpha))
        assertTrue(HitTest.solidNear(200, 200, 100, 100, 52f, 100f, 10f, alpha))
    }
}
