package com.mcc.app.ui

/**
 * 창문 마스크 그림(room_xxx_window.png) 도우미.
 *
 * 마스크 규칙: 방 그림과 같은 비율·크기의 PNG. 비가 보여야 할 유리 부분만 흰색(불투명), 나머지는 투명.
 * 창살·창호지·커튼처럼 밖이 안 보이는 부분은 투명으로 두면 그 위에는 비가 내리지 않습니다.
 */
object WindowMask {

    /** 이 정도 이상 불투명해야 '유리'로 봅니다 (가장자리 흐림은 무시) */
    const val ALPHA_MIN = 128

    /**
     * 마스크에서 유리 부분을 감싸는 범위 (그림 크기에 대한 비율 left, top, right, bottom).
     * 유리가 하나도 없으면 null.
     */
    fun bounds(argb: IntArray, width: Int, height: Int): FloatArray? {
        if (width <= 0 || height <= 0 || argb.size < width * height) return null
        var l = width
        var t = height
        var r = -1
        var b = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((argb[row + x] ushr 24) >= ALPHA_MIN) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        return floatArrayOf(
            l / width.toFloat(), t / height.toFloat(),
            (r + 1) / width.toFloat(), (b + 1) / height.toFloat(),
        )
    }
}
