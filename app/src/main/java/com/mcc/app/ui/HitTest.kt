package com.mcc.app.ui

/**
 * '그림이 실제로 그려진 곳을 눌렀는지' 계산 (순수 함수, 테스트 대상).
 *
 * 캐릭터·알 그림은 네모난 칸 안에 가운데 맞춤(FIT_CENTER)으로 그려지고 주변은 투명합니다.
 * 칸 전체를 누름으로 치면 배경을 눌러도 캐릭터가 반응하고 뒤에 있는 방 소품을 가리므로,
 * 누른 자리를 그림의 픽셀 좌표로 바꿔 그 픽셀이 투명한지 확인합니다.
 */
object HitTest {

    /**
     * 화면 칸(viewW × viewH) 안의 점 (x, y) → 가운데 맞춤으로 그려진 그림(bmpW × bmpH)의 픽셀 좌표.
     * 그림 밖이면 null.
     */
    fun bitmapPoint(viewW: Int, viewH: Int, bmpW: Int, bmpH: Int, x: Float, y: Float): IntArray? {
        if (viewW <= 0 || viewH <= 0 || bmpW <= 0 || bmpH <= 0) return null
        val scale = minOf(viewW.toFloat() / bmpW, viewH.toFloat() / bmpH)
        val left = (viewW - bmpW * scale) / 2f
        val top = (viewH - bmpH * scale) / 2f
        val px = ((x - left) / scale).toInt()
        val py = ((y - top) / scale).toInt()
        if (x < left || y < top || px >= bmpW || py >= bmpH) return null
        return intArrayOf(px, py)
    }

    /** 누른 자리와 그 둘레(반지름 radius) 8방향 — 손가락은 한 점이 아니므로 조금 넉넉하게 봅니다. */
    fun probes(x: Float, y: Float, radius: Float): List<FloatArray> {
        val d = radius * 0.7071f
        return listOf(
            floatArrayOf(x, y),
            floatArrayOf(x - radius, y), floatArrayOf(x + radius, y),
            floatArrayOf(x, y - radius), floatArrayOf(x, y + radius),
            floatArrayOf(x - d, y - d), floatArrayOf(x + d, y - d),
            floatArrayOf(x - d, y + d), floatArrayOf(x + d, y + d),
        )
    }

    /**
     * (x, y) 둘레 [radius] 안에 그림이 그려진 픽셀이 하나라도 있는지.
     * [alphaAt] 은 그림 픽셀 (px, py) 의 알파값(0~255)을 돌려줍니다.
     */
    fun solidNear(
        viewW: Int, viewH: Int, bmpW: Int, bmpH: Int,
        x: Float, y: Float, radius: Float,
        alphaAt: (Int, Int) -> Int,
    ): Boolean = probes(x, y, radius).any { (qx, qy) ->
        val p = bitmapPoint(viewW, viewH, bmpW, bmpH, qx, qy) ?: return@any false
        alphaAt(p[0], p[1]) >= MIN_ALPHA
    }

    /** 이 알파값이면 '그려진 곳'으로 봄 (0~255) */
    const val MIN_ALPHA = 40
}
