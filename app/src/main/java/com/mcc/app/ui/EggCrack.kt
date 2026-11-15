package com.mcc.app.ui

/**
 * 알 깨기 연출의 금 모양 (순수 로직, 테스트 대상).
 *
 * 30km 를 채운 알은 저절로 깨지지 않고, 운전자가 [TAPS_TO_HATCH] 번 눌러 깨웁니다.
 * 누를 때마다 금이 한 단계씩 늘어나고, 마지막 누름에 알이 터지며 부화합니다.
 *
 * 금은 알 그림의 '그려진 부분'(투명한 여백 제외) 안에서 0~1 로 잡은 좌표입니다.
 * 한 줄 = [x0, y0, x1, y1, …] 꺾은선.
 */
object EggCrack {

    /** 이만큼 누르면 깨짐 (금 2단계 → 3번째에 터짐) */
    const val TAPS_TO_HATCH = 3

    private val STAGES: List<List<FloatArray>> = listOf(
        // 1번째 누름: 가운데 작은 금
        listOf(floatArrayOf(0.40f, 0.55f, 0.46f, 0.49f, 0.51f, 0.57f, 0.57f, 0.50f, 0.61f, 0.55f)),
        // 2번째 누름: 양옆으로 길어지고 아래로 갈라짐
        listOf(
            floatArrayOf(0.22f, 0.53f, 0.30f, 0.47f, 0.36f, 0.54f, 0.40f, 0.55f),
            floatArrayOf(0.61f, 0.55f, 0.68f, 0.48f, 0.74f, 0.55f, 0.80f, 0.50f),
            floatArrayOf(0.51f, 0.57f, 0.49f, 0.64f, 0.53f, 0.70f),
            floatArrayOf(0.57f, 0.50f, 0.60f, 0.42f),
        ),
    )

    /** 지금까지 생긴 금 전부 (단계가 쌓임). 0 이면 금 없음 */
    fun lines(level: Int): List<FloatArray> = STAGES.take(level.coerceIn(0, STAGES.size)).flatten()

    /** 다음 누름에 터지는지 */
    fun burstsOn(tapNumber: Int): Boolean = tapNumber >= TAPS_TO_HATCH

    /**
     * 그림에서 실제로 그려진 영역 [left, top, right, bottom] (픽셀).
     * [step] 픽셀마다 건너뛰며 봅니다. 다 투명하면 그림 전체.
     */
    fun opaqueBounds(width: Int, height: Int, step: Int = 4, alphaAt: (Int, Int) -> Int): IntArray {
        var l = width; var t = height; var r = -1; var b = -1
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (alphaAt(x, y) >= HitTest.MIN_ALPHA) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
                x += step
            }
            y += step
        }
        return if (r < 0) intArrayOf(0, 0, width, height)
        else intArrayOf(l, t, (r + step).coerceAtMost(width), (b + step).coerceAtMost(height))
    }
}
