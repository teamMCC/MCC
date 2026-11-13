package com.mcc.app.trait

import com.mcc.app.vehicle.DrivingSession

/**
 * 주행 기록 집계.
 *
 * 여러 번의 주행을 하나의 프로필로 모읍니다. 이 프로필이 '운전 성향'이고,
 * 알을 품는 동안 쌓인 프로필이 부화 확률을 바꿉니다. (확률 계산은 Genetics)
 *
 * 설계 원칙
 *   · 거리는 '얼마나 컸나'가 아니라 '어떤 성격인가'를 정합니다. 1회 평균을 씁니다.
 *   · 과속은 외형이 아니라 성장 속도에 반영합니다. 캐릭터에 상처를 입히지 않습니다.
 *   · 읽지 못한 신호는 해당 지표만 0으로 남고, 나머지는 그대로 계산됩니다.
 */
object TraitEngine {

    /** 알이 부화하는 데 필요한 실제 주행 거리. */
    const val HATCH_DISTANCE_KM = 30f

    /** 누적 통계. 여러 주행에 걸쳐 쌓입니다. */
    data class Profile(
        var totalKm: Float = 0f,
        var driveCount: Int = 0,
        var cleanDrives: Int = 0,
        var harshAccel: Int = 0,
        var harshBrake: Int = 0,
        var sharpSteer: Int = 0,
        var idleSec: Long = 0,
        var wiperSec: Long = 0,
        var nightSec: Long = 0,
        var totalSec: Long = 0,
        var cleanDriveStreak: Int = 0,
        var growthPoints: Float = 0f,
    ) {
        /** 1회 평균 이동거리 — 장거리형 / 도심형을 가릅니다. */
        val avgKmPerDrive: Float get() = if (driveCount == 0) 0f else totalKm / driveCount
        /** 1회 평균 주행시간(초) — 장시간 연속 주행 판정. */
        val avgSecPerDrive: Float get() = if (driveCount == 0) 0f else totalSec.toFloat() / driveCount
        /** 평균 속도 — 도심 위주(낮음) / 고속 위주(높음). */
        val avgSpeedKph: Float get() = if (totalSec == 0L) 0f else totalKm / (totalSec / 3600f)

        val nightRatio: Float get() = if (totalSec == 0L) 0f else nightSec.toFloat() / totalSec
        val wiperRatio: Float get() = if (totalSec == 0L) 0f else wiperSec.toFloat() / totalSec
        val idleRatio: Float get() = if (totalSec == 0L) 0f else idleSec.toFloat() / totalSec

        val harshPerKm: Float get() = if (totalKm < 1f) 0f else (harshAccel + harshBrake) / totalKm
        val accelPerKm: Float get() = if (totalKm < 1f) 0f else harshAccel / totalKm
        val brakePerKm: Float get() = if (totalKm < 1f) 0f else harshBrake / totalKm
        val steerPerKm: Float get() = if (totalKm < 1f) 0f else sharpSteer / totalKm

        /** 무과속 주행 비율 — 운전 태도. 한 번의 과속으로 0이 되지 않도록 '비율'로 봅니다. */
        val cleanRatio: Float get() = if (driveCount == 0) 0f else cleanDrives.toFloat() / driveCount
    }

    /** 주행 1회를 프로필에 누적합니다. 같은 프로필 객체를 고쳐서 돌려줍니다. */
    fun accumulate(profile: Profile, s: DrivingSession): Profile {
        profile.totalKm += s.distanceKm
        profile.driveCount += 1
        profile.harshAccel += s.harshAccelCount
        profile.harshBrake += s.harshBrakeCount
        profile.sharpSteer += s.sharpSteerCount
        profile.idleSec += s.idleSec
        profile.wiperSec += s.wiperActiveSec
        profile.nightSec += s.nightSec
        profile.totalSec += s.durationSec

        // 성장 속도 — 과속하면 느려지고, 과속 없으면 빨라집니다.
        val base = s.distanceKm
        profile.growthPoints += when {
            s.isCleanDrive() -> {
                profile.cleanDrives += 1
                profile.cleanDriveStreak += 1
                base * 1.3f                       // 보너스
            }
            s.overSpeedCount > 0 -> {
                profile.cleanDriveStreak = 0
                base * 0.5f                       // 둔화 (감점이 아니라 느려짐)
            }
            else -> base
        }
        return profile
    }

    /** 부화 진행도 0~1. */
    fun hatchProgress(kmSinceEgg: Float): Float =
        (kmSinceEgg / HATCH_DISTANCE_KM).coerceIn(0f, 1f)
}
