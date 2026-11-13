package com.mcc.app.vehicle

import com.mcc.app.region.Region
import kotlin.math.abs

/**
 * 1회 주행(기어 D → P)의 기록.
 *
 * 주행 중에는 메모리에만 쌓고, 주차 시점에 요약해서 저장합니다.
 * 네트워크는 쓰지 않습니다. 터널·음영 지역에서 끊겨도 영향이 없습니다.
 */
class DrivingSession {

    private var startedAtMs: Long = 0
    private var lastSampleMs: Long = 0
    private var lastSpeedKph: Float = 0f
    private var lastSteeringDeg: Float = 0f

    // 첫 샘플에서는 변화량을 판정하지 않습니다.
    // 이전 값이 0으로 초기화되어 있어, 출발 직후 80km/h 가 들어오면
    // 0 → 80 변화를 급가속으로 오판하게 됩니다.
    private var hasSpeedSample = false
    private var hasSteeringSample = false

    // 시간 누적은 ms(실수)로 모읍니다.
    // 500ms 샘플에서 초 단위 정수로 더하면 매번 0.5 → 0 으로 잘려
    // 정차·우천·야간 시간이 영원히 0이 됩니다.
    private var idleMs: Float = 0f
    private var wiperMs: Float = 0f
    private var nightMs: Float = 0f

    /** 집계값 — 주차 시 이 값들로 운전 성향을 계산합니다. */
    var distanceKm: Float = 0f; private set
    var durationSec: Long = 0; private set
    var harshAccelCount: Int = 0; private set
    var harshBrakeCount: Int = 0; private set
    var sharpSteerCount: Int = 0; private set
    var overSpeedCount: Int = 0; private set     // 과속 '구간' 수 (샘플 수가 아님)
    var maxSpeedKph: Float = 0f; private set

    // 과속은 '몇 번 샘플에서 걸렸나'가 아니라 '몇 초 동안 넘었나'로 봅니다.
    private var overSpeedMs: Float = 0f
    private var wasOverSpeed = false
    val overSpeedSec: Long get() = (overSpeedMs / 1000f).toLong()

    // 지역별 주행거리. 좌표는 받지 않고, 이미 지역으로 바뀐 값만 받습니다.
    private val regionKmInternal = LinkedHashMap<Region, Float>()

    /** 이번 주행에서 지역별로 달린 거리. 지역을 몰랐던 구간은 빠집니다. */
    val regionKm: Map<Region, Float> get() = regionKmInternal

    val idleSec: Long get() = (idleMs / 1000f).toLong()          // 정차 누적
    val wiperActiveSec: Long get() = (wiperMs / 1000f).toLong()  // 우천 주행
    val nightSec: Long get() = (nightMs / 1000f).toLong()        // 야간 주행

    var isActive: Boolean = false; private set

    companion object {
        /** 주행거리계 보정은 이 거리 이상일 때만 (주행거리계 눈금이 보통 1km) */
        private const val MIN_CALIBRATE_KM = 5f
        /** 두 거리의 차이가 이 비율을 넘으면 보정하지 않음 */
        private const val MAX_CALIBRATE_GAP = 0.3f
        /** 급가속·급제동 판정 기준 (m/s²). 실차 검증 후 조정하세요. */
        private const val HARSH_ACCEL_MPS2 = 3.0f
        private const val HARSH_BRAKE_MPS2 = -3.5f
        /** 급조향 판정 기준 (도/초). */
        private const val SHARP_STEER_DPS = 120f
        /** 정차 판정 속도 (km/h). */
        private const val IDLE_SPEED_KPH = 1.0f
        /** 샘플 간격이 이보다 길면 변화량 판정을 건너뜁니다 (앱 복귀·절전 복귀 구간). */
        private const val MAX_SANE_GAP_SEC = 5f
        /** 이 시간 이내의 과속은 보너스 판정에서 관용합니다 (초). */
        private const val CLEAN_TOLERANCE_SEC = 10L
    }

    fun start(nowMs: Long) {
        startedAtMs = nowMs
        lastSampleMs = nowMs
        isActive = true
    }

    /**
     * 주행 중 주기적으로 호출합니다 (권장 500ms~1s).
     * 읽지 못한 신호는 null로 넘기면 해당 집계만 건너뜁니다.
     *
     * @param region 지금 달리는 지역. 모르면 null — 지역 거리에만 안 잡히고 나머지는 정상 집계.
     */
    fun sample(
        nowMs: Long,
        speedKph: Float?,
        steeringDeg: Float?,
        wiperOn: Boolean?,
        nightMode: Boolean?,
        isOverSpeed: Boolean?,
        region: Region? = null,
    ) {
        if (!isActive) return

        val dtSec = ((nowMs - lastSampleMs).coerceAtLeast(0)).toFloat() / 1000f
        if (dtSec <= 0f) return
        val sane = dtSec <= MAX_SANE_GAP_SEC

        speedKph?.let { v ->
            // 거리 = 평균 속도 × 시간
            val stepKm = ((v + lastSpeedKph) / 2f) * (dtSec / 3600f)
            distanceKm += stepKm
            if (region != null && stepKm > 0f) {
                regionKmInternal[region] = (regionKmInternal[region] ?: 0f) + stepKm
            }
            if (v > maxSpeedKph) maxSpeedKph = v

            // 가속도 = 속도 변화 / 시간  (km/h → m/s 환산 후)
            if (hasSpeedSample && sane) {
                val accel = ((v - lastSpeedKph) / 3.6f) / dtSec
                if (accel >= HARSH_ACCEL_MPS2) harshAccelCount++
                if (accel <= HARSH_BRAKE_MPS2) harshBrakeCount++
            }

            if (v < IDLE_SPEED_KPH) idleMs += dtSec * 1000f
            lastSpeedKph = v
            hasSpeedSample = true
        }

        steeringDeg?.let { deg ->
            if (hasSteeringSample && sane) {
                val rate = abs(deg - lastSteeringDeg) / dtSec
                if (rate >= SHARP_STEER_DPS) sharpSteerCount++
            }
            lastSteeringDeg = deg
            hasSteeringSample = true
        }

        if (wiperOn == true) wiperMs += dtSec * 1000f
        if (nightMode == true) nightMs += dtSec * 1000f

        if (isOverSpeed == true) {
            if (!wasOverSpeed) overSpeedCount++      // 구간 진입(상승 엣지)만 카운트
            overSpeedMs += dtSec * 1000f
        }
        wasOverSpeed = isOverSpeed == true

        lastSampleMs = nowMs
    }

    /** 이번 주행에서 방향지시등을 켠 횟수 (Pleos 차량 SDK 가 알려 줄 때만) */
    var turnSignalCount: Int = 0; private set

    fun addTurnSignals(count: Int) {
        if (count > 0) turnSignalCount += count
    }

    /** 이번 주행에서 내비게이션 목적지에 도착한 횟수 (Pleos 내비게이션 SDK 가 알려 줄 때만) */
    var arrivalCount: Int = 0; private set

    fun addArrivals(count: Int) {
        if (count > 0) arrivalCount += count
    }

    /**
     * 차량 주행거리계로 잰 실제 거리에 맞춥니다.
     * 속도를 0.5초마다 더해 구한 거리에는 오차가 쌓이므로, 주행거리계 값이 믿을 만하면 그 값으로 바꾸고
     * 지역별 거리도 같은 비율로 맞춥니다. 주행거리계는 보통 1km 단위라 짧은 주행에는 쓰지 않고,
     * 두 값이 크게 다르면(주행거리계 오류·중간에 앱을 켠 경우) 무시합니다.
     * @return 맞췄으면 true
     */
    fun calibrateDistance(odometerKm: Float): Boolean {
        if (distanceKm < MIN_CALIBRATE_KM || odometerKm < MIN_CALIBRATE_KM) return false
        val ratio = odometerKm / distanceKm
        if (ratio < 1f - MAX_CALIBRATE_GAP || ratio > 1f + MAX_CALIBRATE_GAP) return false
        distanceKm = odometerKm
        regionKmInternal.keys.toList().forEach { region ->
            regionKmInternal[region] = (regionKmInternal[region] ?: 0f) * ratio
        }
        return true
    }

    fun stop(nowMs: Long) {
        if (!isActive) return
        durationSec = (nowMs - startedAtMs) / 1000
        isActive = false
    }

    /** 사람이 읽을 수 있는 요약. 주차 후 화면과 로그에 씁니다. */
    fun summary(): String = buildString {
        appendLine("주행거리      %.2f km".format(distanceKm))
        appendLine("주행시간      ${durationSec / 60}분 ${durationSec % 60}초")
        appendLine("최고속도      %.1f km/h".format(maxSpeedKph))
        appendLine("급가속        ${harshAccelCount}회")
        appendLine("급제동        ${harshBrakeCount}회")
        appendLine("급조향        ${sharpSteerCount}회")
        appendLine("정차시간      ${idleSec / 60}분 ${idleSec % 60}초")
        appendLine("우천주행      ${wiperActiveSec / 60}분")
        if (turnSignalCount > 0) appendLine("방향지시등    ${turnSignalCount}회")
        if (arrivalCount > 0) appendLine("목적지 도착  ${arrivalCount}회")
        appendLine("야간주행      ${nightSec / 60}분")
        appendLine("과속          ${overSpeedCount}구간 / ${overSpeedSec}초")
        if (regionKmInternal.isNotEmpty()) {
            append("지역          ")
            appendLine(regionKmInternal.entries.joinToString(" · ") { (r, km) -> "${r.label} %.1fkm".format(km) })
        }
    }

    /**
     * 성장 보너스 대상 주행인지.
     * 추월 중 잠깐 넘긴 것(10초 이내)은 눈감아 줍니다.
     */
    fun isCleanDrive(): Boolean = overSpeedSec <= CLEAN_TOLERANCE_SEC && distanceKm >= 1f
}
