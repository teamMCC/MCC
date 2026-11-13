package com.mcc.app.vehicle

import com.mcc.app.region.Region

/**
 * 직접 운전해 보는 시뮬레이터의 '차'.
 *
 * 사용자가 목표 속도·운전 방식·와이퍼·야간·지역을 고르면, 0.5초 단위로 가속/감속하며
 * 실제 차량 신호와 같은 형태로 DrivingSession 에 넣습니다. 주차하면 실제 주행과 똑같이
 * 알·캐릭터에 반영됩니다.
 *
 * 배속을 올려도 계산은 항상 0.5초(가상 시간) 단위라서, 급가속·급제동 판정이 실제와 같습니다.
 */
class SimDrive {

    enum class Style(val label: String, val accelMps2: Float, val brakeMps2: Float) {
        GENTLE("부드럽게", 1.5f, 2.0f),     // 급가속(3.0)·급제동(3.5) 기준 아래
        HARSH("급하게", 4.5f, 6.0f),        // 기준을 넘어 급가속·급제동으로 잡힘
    }

    var targetKph = 0f
    var style = Style.GENTLE
    var wiper = false
    var night = false
    var region: Region? = Region.SEOUL

    var speedKph = 0f
        private set

    private var session: DrivingSession? = null
    private var simNowMs = 0L
    private var pendingMs = 0L

    val isDriving: Boolean get() = session != null
    val distanceKm: Float get() = session?.distanceKm ?: 0f
    val elapsedSec: Long get() = simNowMs / 1000
    val harshCount: Int get() = session?.let { it.harshAccelCount + it.harshBrakeCount } ?: 0

    /** 기어 D. 이미 달리는 중이면 아무것도 안 합니다. */
    fun start() {
        if (session != null) return
        session = DrivingSession().apply { start(0L) }
        simNowMs = 0L
        pendingMs = 0L
        speedKph = 0f
    }

    /** 가상 시간을 simMs 만큼 흘립니다 (배속이 곱해진 값). */
    fun advance(simMs: Long) {
        val s = session ?: return
        pendingMs = (pendingMs + simMs).coerceAtMost(MAX_PENDING_MS)
        while (pendingMs >= STEP_MS) {
            pendingMs -= STEP_MS
            step(s)
        }
    }

    private fun step(s: DrivingSession) {
        val dtSec = STEP_MS / 1000f
        val rate = if (targetKph > speedKph) style.accelMps2 else -style.brakeMps2
        val next = speedKph + rate * dtSec * 3.6f
        speedKph = if (targetKph > speedKph) minOf(targetKph, next) else maxOf(targetKph, next)
        if (speedKph < 0f) speedKph = 0f
        simNowMs += STEP_MS
        s.sample(
            nowMs = simNowMs,
            speedKph = speedKph,
            steeringDeg = null,
            wiperOn = wiper,
            nightMode = night,
            isOverSpeed = speedKph > OVER_SPEED_KPH,
            region = region,
        )
    }

    /** 기어 P. 이번 주행 기록을 돌려주고 차를 세웁니다. 달리는 중이 아니면 null. */
    fun finish(): DrivingSession? {
        val s = session ?: return null
        s.stop(simNowMs)
        session = null
        speedKph = 0f
        targetKph = 0f
        return s
    }

    companion object {
        const val STEP_MS = 500L
        /** 실제 앱과 같은 임시 과속 기준 (km/h). */
        const val OVER_SPEED_KPH = 110f
        /** 앱이 잠깐 멈췄다 돌아와도 한 번에 너무 많이 계산하지 않게 막습니다. */
        private const val MAX_PENDING_MS = 60_000L
    }
}
