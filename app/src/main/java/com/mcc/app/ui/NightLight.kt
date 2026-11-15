package com.mcc.app.ui

/**
 * 밤 조명 색 (순수 함수, 테스트 대상).
 *
 * 캐릭터·방 소품 그림에 곱하는(MULTIPLY) 색입니다. 0 이면 흰색(원래 밝기), 1 이면 푸른 밤빛.
 * 밤빛은 밝기 약 70% 에 파랑이 조금 남는 색이라, 방의 남색 그늘과 어울리면서 캐릭터는 알아볼 수 있게 남습니다.
 *
 * 켜진 스탠드 가까이 있는 것은 [light](0~1)만큼 덜 어두워지고 따뜻한 빛을 받습니다.
 */
object NightLight {
    /** 완전한 밤일 때 곱하는 색 (R, G, B) */
    const val NIGHT_R = 0xA8
    const val NIGHT_G = 0xAE
    const val NIGHT_B = 0xD2

    /** 스탠드 불빛 색 (R, G, B). 바로 옆이면 이 색에 가까워짐 */
    const val WARM_R = 0xFF
    const val WARM_G = 0xEA
    const val WARM_B = 0xC4

    /** 스탠드 바로 옆에서 어두움을 얼마나 걷어 내는지 (0~1) */
    const val LIGHT_LIFT = 0.85f

    /**
     * amount(밤 정도 0~1)와 light(스탠드 불빛 0~1)에 맞춘 불투명 색 (ARGB).
     * 먼저 흰색 → 밤빛으로 어두워지고, 불빛을 받는 만큼 따뜻한 색으로 되돌아옵니다.
     */
    fun tint(amount: Float, light: Float = 0f): Int {
        val a = amount.coerceIn(0f, 1f)
        val l = light.coerceIn(0f, 1f)
        val dark = a * (1f - LIGHT_LIFT * l)
        val warm = a * l
        fun ch(night: Int, warmC: Int): Int {
            val base = 255 + (night - 255) * dark
            return (base + (warmC - base) * warm).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(NIGHT_R, WARM_R) shl 16) or (ch(NIGHT_G, WARM_G) shl 8) or ch(NIGHT_B, WARM_B)
    }

    /** 불빛 세기: 중심에서 1, 반경 끝에서 0으로 부드럽게 줄어듦 */
    fun falloff(distance: Float, radius: Float): Float {
        if (radius <= 0f) return 0f
        val t = (1f - distance / radius).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
