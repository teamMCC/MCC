package com.mcc.app

import com.mcc.app.game.Greetings
import com.mcc.app.game.VehicleMoments
import com.mcc.app.game.VehicleMoments.Moment
import com.mcc.app.region.Region
import com.mcc.app.vehicle.DrivingSession
import com.mcc.app.vehicle.PleosVehicle
import com.mcc.app.vehicle.VehicleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pleos 차량·내비게이션 SDK 신호를 게임 반응으로 바꾸는 순수 로직 검증 */
class VehicleSignalsTest {

    @Test
    fun first_value_is_only_a_baseline() {
        val m = VehicleMoments()
        // 앱을 켰을 때 이미 열려 있던 문·앉아 있던 동승자에는 반응하지 않음
        assertNull(m.door("ROW_1_LEFT", true))
        assertNull(m.passenger("ROW_1_RIGHT", true))
        assertNull(m.window(true))
        assertNull(m.chargePort(true))
    }

    @Test
    fun reacts_once_when_state_changes() {
        val m = VehicleMoments()
        m.door("ROW_1_LEFT", false)
        assertEquals(Moment.DOOR_OPEN, m.door("ROW_1_LEFT", true))
        assertNull(m.door("ROW_1_LEFT", true))          // 같은 상태 반복
        assertNull(m.door("ROW_1_LEFT", false))         // 닫힘에는 반응 없음
        // 문마다 따로 기억
        m.door("ROW_2_RIGHT", false)
        assertEquals(Moment.DOOR_OPEN, m.door("ROW_2_RIGHT", true))

        m.passenger("ROW_1_RIGHT", false)
        assertEquals(Moment.PASSENGER_IN, m.passenger("ROW_1_RIGHT", true))
        m.window(false)
        assertEquals(Moment.WINDOW_OPEN, m.window(true))
        m.chargePort(false)
        assertEquals(Moment.CHARGING, m.chargePort(true))
    }

    @Test
    fun seat_value_from_the_sdk_is_inverted() {
        // 에뮬레이터 콘솔 '탑승' → SDK false. 비어 있다가 탑승하면 "친구도 탔네!"
        assertEquals(true, VehicleMoments.seatedFrom(false))
        assertEquals(false, VehicleMoments.seatedFrom(true))
        val m = VehicleMoments()
        m.passenger("ROW_1_RIGHT", VehicleMoments.seatedFrom(true))          // 콘솔: 비어 있음
        assertEquals(Moment.PASSENGER_IN, m.passenger("ROW_1_RIGHT", VehicleMoments.seatedFrom(false)))  // 콘솔: 탑승
    }

    @Test
    fun energy_low_and_refill() {
        val m = VehicleMoments()
        assertNull(m.energy(60))
        assertNull(m.energy(40))
        assertEquals(Moment.ENERGY_LOW, m.energy(VehicleMoments.LOW_PERCENT))
        assertNull(m.energy(12))                         // 계속 부족 → 다시 말하지 않음
        assertEquals(Moment.ENERGY_FULL, m.energy(80))  // 주유·충전
        assertNull(m.energy(81))
        // 처음부터 부족하면 바로 알림
        assertEquals(Moment.ENERGY_LOW, VehicleMoments().energy(5))
    }

    @Test
    fun battery_and_fuel_do_not_mix() {
        // 전기차 배터리 12% 와 연료 100% 가 번갈아 들어와도 '충전됨'이 반복되면 안 됩니다.
        val m = VehicleMoments()
        assertNull(m.energy(100, VehicleMoments.SOURCE_FUEL))
        assertNull(m.energy(80, VehicleMoments.SOURCE_BATTERY))
        assertEquals(Moment.ENERGY_LOW, m.energy(12, VehicleMoments.SOURCE_BATTERY))
        repeat(5) {
            assertNull(m.energy(100, VehicleMoments.SOURCE_FUEL))
            assertNull(m.energy(12, VehicleMoments.SOURCE_BATTERY))
        }
        assertEquals(Moment.ENERGY_FULL, m.energy(40, VehicleMoments.SOURCE_BATTERY))
    }

    @Test
    fun every_moment_has_a_line() {
        val rng = Random(3)
        Moment.values().forEach { assertTrue(it.name, Greetings.moment(rng, it).isNotBlank()) }
    }

    @Test
    fun turn_signal_and_light_names() {
        assertTrue(PleosVehicle.isSignalOn("LEFT"))
        assertTrue(PleosVehicle.isSignalOn("right"))
        assertTrue(PleosVehicle.isSignalOn("ON"))
        assertFalse(PleosVehicle.isSignalOn("NONE"))
        assertFalse(PleosVehicle.isSignalOn("off"))
    }

    private fun drive(km: Int, region: Region?): DrivingSession {
        val s = DrivingSession()
        s.start(0L)
        var t = 0L
        // 시속 60km → 1분에 1km
        repeat(km * 60) { t += 1000L; s.sample(t, 60f, 0f, false, false, false, region) }
        return s
    }

    @Test
    fun odometer_calibrates_distance_and_regions() {
        val s = drive(10, Region.SEOUL)
        assertEquals(10f, s.distanceKm, 0.1f)
        assertTrue(s.calibrateDistance(11f))
        assertEquals(11f, s.distanceKm, 0.001f)
        assertEquals(11f, s.regionKm.getValue(Region.SEOUL), 0.15f)
    }

    @Test
    fun odometer_is_ignored_when_short_or_far_off() {
        val short = drive(2, null)
        assertFalse(short.calibrateDistance(2f))        // 짧은 주행
        val s = drive(10, null)
        assertFalse(s.calibrateDistance(20f))           // 너무 다름
        assertFalse(s.calibrateDistance(4f))
        assertFalse(s.calibrateDistance(-1f))
        assertEquals(10f, s.distanceKm, 0.1f)
    }

    @Test
    fun turn_signals_and_arrivals_are_counted() {
        val s = drive(1, null)
        s.addTurnSignals(3); s.addTurnSignals(-2); s.addTurnSignals(0)
        s.addArrivals(1); s.addArrivals(-1)
        assertEquals(3, s.turnSignalCount)
        assertEquals(1, s.arrivalCount)
    }

    @Test
    fun fuel_percent_from_standard_properties() {
        assertEquals(50, VehicleRepository.fuelPercentOf(25_000f, 50_000f))
        assertEquals(15, VehicleRepository.fuelPercentOf(7_400f, 50_000f))
        assertEquals(100, VehicleRepository.fuelPercentOf(60_000f, 50_000f))
        assertNull(VehicleRepository.fuelPercentOf(10f, 0f))      // 전기차 (연료통 없음)
        assertNull(VehicleRepository.fuelPercentOf(-1f, 50_000f))
    }
}
