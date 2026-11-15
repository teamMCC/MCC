package com.mcc.app

import com.mcc.app.PermissionGate.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 권한: 속도만 필수(없으면 앱 안에서 '허용하기'), 위치·에너지는 처음 한 번만 묻는 선택 */
class PermissionGateTest {
    private val all = (PermissionGate.REQUIRED + PermissionGate.OPTIONAL).flatten()

    @Test
    fun first_launch_asks_everything_once() {
        assertEquals(all.toSet(), PermissionGate.toAskOnLaunch({ false }, optionalAskedBefore = false).toSet())
    }

    @Test
    fun later_launches_ask_only_the_missing_speed() {
        assertEquals(listOf(PermissionGate.CAR_SPEED), PermissionGate.toAskOnLaunch({ false }, optionalAskedBefore = true))
        assertTrue(PermissionGate.toAskOnLaunch({ it == PermissionGate.CAR_SPEED }, optionalAskedBefore = true).isEmpty())
    }

    @Test
    fun denying_location_and_energy_keeps_the_core_working() {
        assertTrue(PermissionGate.coreReady { it == PermissionGate.CAR_SPEED })
        assertFalse(PermissionGate.coreReady { it != PermissionGate.CAR_SPEED })
    }

    @Test
    fun approximate_location_is_enough() {
        val granted = all.toSet() - PermissionGate.FINE_LOCATION
        assertTrue(PermissionGate.toAskOnLaunch({ it in granted }, optionalAskedBefore = false).isEmpty())
    }

    @Test
    fun allow_button_asks_again_until_android_stops_showing_the_window() {
        val none = { _: String -> false }
        assertEquals(Action.ASK, PermissionGate.speedAction(none, { false }, askedBefore = false))   // 처음
        assertEquals(Action.ASK, PermissionGate.speedAction(none, { true }, askedBefore = true))     // 한 번 거부
        assertEquals(Action.OPEN_SETTINGS, PermissionGate.speedAction(none, { false }, askedBefore = true)) // 두 번 거부
        assertEquals(Action.NONE, PermissionGate.speedAction({ true }, { false }, askedBefore = true))
    }

    @Test
    fun settings_rows_cover_every_asked_permission_once() {
        val listed = PermissionGate.ITEMS.flatMap { it.permissions }
        assertEquals(all.toSet(), listed.toSet())
        assertEquals(listed.size, listed.toSet().size)
        assertEquals(1, PermissionGate.ITEMS.count { it.required })
    }

    @Test
    fun turning_on_an_optional_one_from_settings() {
        val loc = PermissionGate.ITEMS[1].permissions
        assertEquals(Action.ASK, PermissionGate.action(loc, { false }, { true }, askedBefore = true))
        assertEquals(Action.OPEN_SETTINGS, PermissionGate.action(loc, { false }, { false }, askedBefore = true))
        assertEquals(Action.NONE, PermissionGate.action(loc, { it == PermissionGate.COARSE_LOCATION }, { false }, askedBefore = true))
    }
}
