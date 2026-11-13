package com.mcc.app

import com.mcc.app.region.Region
import com.mcc.app.vehicle.DrivingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 주행 집계: 거리 적분 · 지역별 거리 · 과속 관용 */
class DrivingSessionTest {

    private fun run(s: DrivingSession, seconds: Int, kph: Float, region: Region?, over: Boolean = false, from: Long = 0L): Long {
        var t = from
        repeat(seconds * 2) {
            t += 500
            s.sample(t, kph, 0f, false, false, over, region)
        }
        return t
    }

    @Test
    fun one_hour_at_80_is_80km() {
        val s = DrivingSession().apply { start(0) }
        val end = run(s, 3600, 80f, null)
        s.stop(end)
        assertEquals(80.0, s.distanceKm.toDouble(), 0.1)
        assertEquals(3600L, s.durationSec)
    }

    @Test
    fun unknown_region_is_left_out_but_distance_counts() {
        val s = DrivingSession().apply { start(0) }
        run(s, 600, 60f, null)
        assertTrue(s.regionKm.isEmpty())
        assertTrue(s.distanceKm > 9.9f)
    }

    @Test
    fun crossing_regions_splits_distance() {
        val s = DrivingSession().apply { start(0) }
        val t = run(s, 1800, 60f, Region.SEOUL)        // 30분 서울
        run(s, 1200, 60f, Region.GYEONGGI, from = t)   // 20분 경기
        assertEquals(30.0, (s.regionKm[Region.SEOUL] ?: 0f).toDouble(), 0.1)
        assertEquals(20.0, (s.regionKm[Region.GYEONGGI] ?: 0f).toDouble(), 0.1)
        val sum = s.regionKm.values.sum()
        assertEquals("지역 합 = 전체", s.distanceKm.toDouble(), sum.toDouble(), 0.01)
    }

    @Test
    fun brief_speeding_is_forgiven_long_speeding_is_not() {
        val brief = DrivingSession().apply { start(0) }
        var t = run(brief, 600, 60f, null)
        t = run(brief, 5, 120f, null, over = true, from = t)    // 추월 5초
        run(brief, 600, 60f, null, from = t)
        assertTrue("5초 초과는 무과속 인정", brief.isCleanDrive())
        assertEquals(1, brief.overSpeedCount)

        val long = DrivingSession().apply { start(0) }
        run(long, 600, 120f, null, over = true)
        assertFalse("10분 과속은 불인정", long.isCleanDrive())
    }
}
