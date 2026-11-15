package com.mcc.app.vehicle

import com.mcc.app.region.Region
import kotlin.random.Random

/**
 * 데모 주행 생성기.
 *
 * 왜 필요한가
 *   알이 부화하려면 실제 30km, 악세사리는 한 지역 20km 주행이 필요합니다.
 *   에뮬레이터로는 30분 넘게 걸리고, 심사위원은 그 시간을 기다리지 않습니다.
 *
 * 무엇을 하는가
 *   가속·제동·신호대기·조향을 500ms 단위로 재현한 가상 주행 1회를 만들어
 *   **실제 경로(DrivingSession → 알 → 부화)에 그대로 통과시킵니다.**
 *   지름길 코드가 아니라 차량 신호만 가상인 같은 경로입니다.
 */
object DemoDrive {

    data class Style(
        val label: String,
        val region: Region,         // 이 데모 주행을 한 지역
        val minutes: Int,
        val cruiseKph: Float,
        val accelG: Float,          // 가속 강도 (g)
        val brakeG: Float,          // 제동 강도 (g)
        val stopsPerMin: Float,     // 분당 신호·정체 진입 횟수
        val steerSigma: Float,      // 조향 변동 (도/샘플)
        val wiper: Boolean,
        val night: Boolean,
        val overSpeedRate: Float,   // 과속 구간 발생 확률/샘플
    )

    /**
     * 누를 때마다 다음 스타일로 넘어갑니다. 주석은 1회 거리 (500회 실측 최소~최대).
     *   2번째(장거리)에서 시작 알이 부화하며 강원 악세사리를 가지고 태어나고,
     *   3번째(야간·부산)는 항상 20km 를 넘겨 부산 악세사리를 얻도록 맞춰 두었습니다.
     */
    val STYLES = listOf(
        Style("출퇴근 도심", Region.SEOUL,    15, 30f, 0.12f, 0.15f, 1.8f, 6f,  false, false, 0.0010f), //   2~6km
        Style("장거리 고속", Region.GANGWON,  70, 95f, 0.10f, 0.12f, 0.1f, 2f,  false, false, 0.0020f), // 91~109km
        Style("야간 주행",   Region.BUSAN,    42, 55f, 0.13f, 0.16f, 0.6f, 5f,  false, true,  0.0010f), // 22~33km
        Style("여유 여행",   Region.JEJU,     25, 45f, 0.09f, 0.11f, 1.0f, 5f,  false, false, 0.0003f), //  7~15km
        Style("급한 운전",   Region.GYEONGGI, 20, 70f, 0.38f, 0.45f, 1.2f, 14f, false, false, 0.0120f), // 10~18km
        Style("느긋한 운전", Region.JEOLLA,   25, 50f, 0.07f, 0.09f, 0.8f, 3f,  false, false, 0.0000f), //  9~17km
        Style("비 오는 날",  Region.CHUNGCHEONG, 30, 45f, 0.10f, 0.13f, 1.0f, 5f, true, false, 0.0005f), // 와이퍼 켜고 주행
    )

    private const val STEP_MS = 500L

    fun generate(style: Style, rng: Random = Random.Default): DrivingSession {
        val s = DrivingSession()
        var t = 0L
        s.start(t)

        var speed = 0f
        var steer = 0f
        var target = style.cruiseKph
        var idleLeft = 0f
        var overUntil = -1

        val steps = style.minutes * 60 * 2
        for (i in 0 until steps) {
            t += STEP_MS

            if (idleLeft > 0f) {
                idleLeft -= 0.5f
                speed = 0f
            } else {
                if (rng.nextFloat() < style.stopsPerMin / 120f) {
                    target = 0f                                  // 신호·정체 진입
                } else if (speed < 1f && target == 0f) {
                    idleLeft = 8f + rng.nextFloat() * 37f        // 8~45초 정차
                    target = style.cruiseKph
                }
                // g → km/h per 0.5s
                val delta = (if (target > speed) style.accelG else -style.brakeG) * 9.81f * 0.5f * 3.6f
                speed = if (target > speed) minOf(target, speed + delta) else maxOf(target, speed + delta)
                if (speed < 0f) speed = 0f
            }

            steer += (rng.nextFloat() - 0.5f) * 2f * style.steerSigma

            if (overUntil < i && rng.nextFloat() < style.overSpeedRate) {
                overUntil = i + 10 + rng.nextInt(70)             // 5~40초 과속 구간
            }

            s.sample(
                nowMs = t,
                speedKph = speed,
                steeringDeg = steer,
                wiperOn = style.wiper,
                nightMode = style.night,
                isOverSpeed = i <= overUntil,
                region = style.region,
            )
        }

        s.stop(t)
        return s
    }
}
