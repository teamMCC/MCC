package com.mcc.app

import com.mcc.app.game.GameRules
import com.mcc.app.game.GameState
import com.mcc.app.region.Region
import com.mcc.app.vehicle.SimDrive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 직접 운전 시뮬레이터: 거리 · 급가감속 · 날씨·야간·지역 · 과속 · 부화까지 */
class SimDriveTest {

    private fun minutes(m: Int) = m * 60_000L

    /** 앱과 같이 짧게 나눠 흘립니다 (×30 배속에서 0.1초마다 3초). */
    private fun SimDrive.run(ms: Long) {
        var left = ms
        while (left > 0) { val step = minOf(3_000L, left); advance(step); left -= step }
    }

    @Test
    fun gentle_60kph_for_30_minutes_is_about_30km_without_harsh_events() {
        val sim = SimDrive().apply { region = Region.BUSAN }
        sim.start(); sim.targetKph = 60f
        sim.run(minutes(30))
        val d = sim.finish()!!
        assertEquals(30.0, d.distanceKm.toDouble(), 0.3)
        assertEquals("부드럽게: 급가속 없음", 0, d.harshAccelCount)
        assertEquals("부드럽게: 급제동 없음", 0, d.harshBrakeCount)
        assertEquals("거리가 전부 부산", d.distanceKm.toDouble(), (d.regionKm[Region.BUSAN] ?: 0f).toDouble(), 0.01)
        assertTrue("과속 없음 → 무과속 주행", d.isCleanDrive())
    }

    @Test
    fun harsh_style_creates_harsh_accel_and_brake() {
        val sim = SimDrive().apply { style = SimDrive.Style.HARSH }
        sim.start()
        repeat(5) {
            sim.targetKph = 80f; sim.run(20_000)
            sim.targetKph = 0f; sim.run(20_000)
        }
        val d = sim.finish()!!
        assertTrue("급가속 잡힘 (${d.harshAccelCount})", d.harshAccelCount > 0)
        assertTrue("급제동 잡힘 (${d.harshBrakeCount})", d.harshBrakeCount > 0)
    }

    @Test
    fun wiper_night_and_speeding_are_recorded() {
        val sim = SimDrive().apply { wiper = true; night = true }
        sim.start(); sim.targetKph = 130f
        sim.run(minutes(10))
        val d = sim.finish()!!
        assertTrue("와이퍼 시간", d.wiperActiveSec >= 590)
        assertTrue("야간 시간", d.nightSec >= 590)
        assertTrue("110km/h 초과 → 과속", d.overSpeedSec > 10)
        assertFalse("과속 주행은 무과속 인정 안 됨", d.isCleanDrive())
    }

    @Test
    fun speed_up_does_not_change_the_result() {
        // ×1 로 0.1초씩 흘린 것과 ×30 으로 3초씩 흘린 것이 같아야 함
        fun run(stepMs: Long): Float {
            val sim = SimDrive(); sim.start(); sim.targetKph = 60f
            var t = 0L
            while (t < minutes(10)) { sim.run(stepMs); t += stepMs }
            return sim.finish()!!.distanceKm
        }
        assertEquals(run(100).toDouble(), run(3000).toDouble(), 0.01)
    }

    @Test
    fun parking_twice_is_safe_and_resets_the_car() {
        val sim = SimDrive()
        assertNull("달리기 전 주차 → 기록 없음", sim.finish())
        sim.start(); sim.targetKph = 50f; sim.run(60_000)
        sim.finish()
        assertFalse(sim.isDriving)
        assertEquals(0f, sim.speedKph)
        assertNull(sim.finish())
    }

    @Test
    fun simulated_drive_hatches_an_egg_through_the_real_rules() {
        val s = GameState(); GameRules.ensureStarterEgg(s)
        val sim = SimDrive().apply { region = Region.JEJU }
        sim.start(); sim.targetKph = 100f
        sim.run(minutes(20))            // 약 33km
        GameRules.applyDrive(s, sim.finish()!!, Random(1), 0L) { "baby" }
        GameRules.tryHatch(s, Random(1), 0L) { "baby" }
        assertEquals("부화", 1, s.creatures.size)
        assertTrue("제주 20km 넘게 품음 → 제주 악세사리", Region.JEJU in s.creatures[0].genome.accessories)
    }
}
