package com.mcc.app.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 도감 단계(★·★★) 그림이 따로 없을 때, 기본 그림의 색을 바꿔 단계 그림을 만듭니다.
 *
 *   ★   색상환을 150° 돌린 색 변형 (민트 → 분홍, 노랑 → 보라 …)
 *   ★★  금빛 — 색이 있는 부분을 금색 계열로
 *
 * 진갈색 외곽선은 그대로 둡니다 (선이 바뀌면 다른 캐릭터처럼 보임).
 * 눈·입처럼 선 자체가 그림인 파츠는 includeDark = true 로 선까지 바꿉니다.
 * 흰색·회색(채도 낮음)과 투명 픽셀도 그대로 둡니다.
 *
 * 안드로이드에 의존하지 않는 순수 계산이라 단위 테스트로 확인합니다.
 */
object Recolor {

    const val HUE_SHIFT_STAR1 = 150f
    private const val GOLD_HUE = 44f

    fun apply(argb: IntArray, tier: Int, includeDark: Boolean) {
        if (tier <= 0) return
        val hsv = FloatArray(3)
        for (i in argb.indices) {
            val c = argb[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            rgbToHsv(r, g, b, hsv)
            val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            if (s < 0.18f) continue                       // 흰색·회색·은색
            if (!includeDark && v < 0.45f) continue       // 진갈색 외곽선
            when (tier) {
                1 -> {
                    hsv[0] = (h + HUE_SHIFT_STAR1) % 360f
                    hsv[1] = min(1f, s * 1.08f)
                }
                else -> {
                    hsv[0] = GOLD_HUE + ((h - GOLD_HUE + 540f) % 360f - 180f) * 0.12f  // 원래 색 차이를 살짝만 남김
                    hsv[1] = max(0.55f, min(0.9f, s))
                    hsv[2] = if (v < 0.45f) max(v, 0.40f) else min(1f, v * 1.06f + 0.04f)
                }
            }
            argb[i] = (a shl 24) or hsvToRgb(hsv)
        }
    }

    fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == rf -> 60f * (((gf - bf) / d) % 6f)
            mx == gf -> 60f * (((bf - rf) / d) + 2f)
            else -> 60f * (((rf - gf) / d) + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (mx == 0f) 0f else d / mx
        out[2] = mx
    }

    /** @return 0xRRGGBB */
    fun hsvToRgb(hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
