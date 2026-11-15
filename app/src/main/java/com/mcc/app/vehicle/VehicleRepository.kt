package com.mcc.app.vehicle

import kotlin.math.roundToInt
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 차량 신호 읽기.
 *
 * Pleos 'SDK API 정책 — 표준 우선':
 *   속도·기어처럼 AAOS 표준에 이미 있는 차량 데이터는 AAOS Car API 로 읽는다.
 *   Pleos Connect SDK 는 표준에 없는 제조사 전용 기능만 제공한다.
 * 그래서 MCC 는 Pleos SDK 없이 AAOS 표준 API 만 씁니다.
 *
 * 읽는 신호는 매니페스트에 선언한 3가지 권한 범위 안의 것뿐입니다.
 *   CAR_SPEED                 → 속도
 *   CAR_POWERTRAIN            → 기어
 *   CAR_EXTERIOR_ENVIRONMENT  → 야간 모드
 * 승인되지 않은 권한의 API 는 호출 자체를 하지 않습니다 (Pleos 심사 반려 사유).
 *
 * 주행 규제는 CarUxRestrictionsManager 리스너로 받습니다 (Pleos 주행 규제 개발 가이드).
 * 모든 읽기를 try/catch 로 감싸서, 신호가 없는 차량에서도 null 만 돌아오고 앱은 멈추지 않습니다.
 */
class VehicleRepository(private val context: Context) {

    companion object {
        /** VehicleGear.GEAR_1 (0x10). 수동 1~9단은 이 값 이상입니다. */
        private const val MANUAL_GEAR_1 = 0x0010
        private const val TAG = "MCC_Vehicle"

        /** 연료량·연료통 크기(mL) → 잔량 %. 값이 이상하면 null */
        fun fuelPercentOf(levelMl: Float, capacityMl: Float): Int? {
            if (capacityMl <= 0f || levelMl < 0f) return null
            return (levelMl / capacityMl * 100f).coerceIn(0f, 100f).roundToInt()
        }

        /** MCC 가 쓰는 신호. '차량 신호 점검'에서 지원 여부를 보여줍니다. */
        val TARGET_PROPERTIES: List<Pair<Int, String>> = listOf(
            VehiclePropertyIds.GEAR_SELECTION     to "기어 — 주행 시작·종료 판정",
            VehiclePropertyIds.PERF_VEHICLE_SPEED to "속도 — 거리·급가감속·정차·과속",
            VehiclePropertyIds.NIGHT_MODE         to "야간 모드 — 빛나는 눈·야간형·방 밤 연출",
            VehiclePropertyIds.FUEL_LEVEL         to "연료 잔량 — 배고픔·든든함 (내연기관차)",
        )

    }

    /** Pleos 전용 SDK 로 읽는 신호 (와이퍼·주행거리계·문·좌석·연료 등) */
    val pleos = PleosVehicle(context)

    /** Pleos 내비게이션의 길 안내 시작·도착 */
    val navi = PleosNavi(context)

    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var uxListener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null

    fun connect(): Boolean = try {
        // Pleos SDK 는 표준 차량 서비스 연결과 따로 시작합니다 (둘 다 안에서 실패를 스스로 처리).
        pleos.start()
        navi.start()
        car = Car.createCar(context)
        pm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        ux = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        val ok = pm != null
        Log.i(TAG, if (ok) "차량 서비스 연결 성공" else "CarPropertyManager 획득 실패")
        ok
    } catch (e: Exception) {
        Log.e(TAG, "연결 실패: ${e.message}", e)
        false
    }

    fun disconnect() {
        pleos.stop()
        navi.stop()
        try {
            if (uxListener != null) ux?.unregisterListener()
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "해제 중 오류: ${e.message}")
        }
        uxListener = null; pm = null; ux = null; car = null
    }

    // ────────────────────────────────────────────────
    // 주행 규제 (CarUxRestrictions)
    // ────────────────────────────────────────────────

    /**
     * 주행 규제가 바뀔 때마다 [onChange] 를 부릅니다. 등록 직후 현재 상태로 한 번 부릅니다.
     * @return 등록 성공 여부. 실패하면 호출부는 [isDriving] 폴링으로 대신합니다.
     */
    fun listenDrivingRestrictions(onChange: (restricted: Boolean) -> Unit): Boolean {
        val manager = ux ?: return false
        return try {
            val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
                onChange(isRestricted(r))
            }
            manager.registerListener(listener)
            uxListener = listener
            onChange(isRestricted(manager.currentCarUxRestrictions))
            true
        } catch (e: Exception) {
            Log.w(TAG, "주행 규제 리스너 등록 실패: ${e.message}")
            false
        }
    }

    /** 주행 규제 중이면 true. 이때 화면 조작을 막아야 합니다. */
    fun isDriving(): Boolean? = try {
        ux?.currentCarUxRestrictions?.let { isRestricted(it) }
    } catch (e: Exception) {
        Log.w(TAG, "주행 상태 확인 실패: ${e.message}")
        null
    }

    private fun isRestricted(r: CarUxRestrictions?): Boolean =
        r?.isRequiresDistractionOptimization == true

    // ────────────────────────────────────────────────
    // 신호 지원 여부 — '차량 신호 점검' (시연 도구)
    // ────────────────────────────────────────────────

    /** 이 차량이 해당 신호를 제공하는지. 차종마다 다르므로 차종 이름이 아니라 이걸로 판단합니다. */
    fun isSupported(propId: Int): Boolean = try {
        pm?.getCarPropertyConfig(propId) != null
    } catch (e: Exception) {
        false
    }

    fun signalReport(): String {
        if (pm == null) return "차량 서비스에 연결되지 않았습니다."
        return buildString {
            appendLine("[ MCC 가 쓰는 차량 신호 ]")
            TARGET_PROPERTIES.forEach { (id, desc) ->
                val supported = isSupported(id)
                val readable = supported && when (id) {
                    VehiclePropertyIds.GEAR_SELECTION -> gearRaw() != null
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedKph() != null
                    VehiclePropertyIds.NIGHT_MODE -> isNightMode() != null
                    VehiclePropertyIds.FUEL_LEVEL -> fuelPercent() != null
                    else -> false
                }
                val mark = when {
                    readable -> "O"
                    supported -> "△ (지원하지만 읽기 실패 — 권한 확인)"
                    else -> "X (이 차량 미지원)"
                }
                appendLine("  $mark  $desc")
            }
            appendLine("  ${if (isDriving() != null) "O" else "X"}  주행 규제 상태 — 주행 중 화면 잠금")
            appendLine()
            appendLine("[ Pleos 차량 SDK 로 읽는 신호 ]")
            append(pleos.report())
            appendLine()
            appendLine("[ Pleos 내비게이션 SDK ]")
            append(navi.report())
        }
    }

    // ────────────────────────────────────────────────
    // 개별 신호 — 막히면 null
    // ────────────────────────────────────────────────

    fun gearRaw(): Int? = readInt(VehiclePropertyIds.GEAR_SELECTION)

    fun gearLabel(): String? = gearRaw()?.let {
        when (it) {
            VehicleGear.GEAR_PARK    -> "P (주차)"
            VehicleGear.GEAR_REVERSE -> "R (후진)"
            VehicleGear.GEAR_NEUTRAL -> "N (중립)"
            VehicleGear.GEAR_DRIVE   -> "D (주행)"
            VehicleGear.GEAR_UNKNOWN -> "알 수 없음"
            else -> "기타 ($it)"
        }
    }

    /** 기어를 읽을 수 없으면 null. 호출부는 속도 기준 폴백으로 넘어가야 합니다. */
    fun isInPark(): Boolean? = gearRaw()?.let { it == VehicleGear.GEAR_PARK }

    /**
     * 실제로 달릴 수 있는 기어(D·R·수동 1~9단)인지. 기어를 못 읽으면 null.
     * 알 수 없음(0)·중립(N)·주차(P)는 달리는 기어로 보지 않습니다.
     * (에뮬레이터는 기어 값이 '알 수 없음'으로 시작하는 경우가 있어, P 가 아니라는 이유만으로
     *  주행으로 판단하면 화면이 계속 잠깁니다.)
     */
    fun isInDriveGear(): Boolean? = gearRaw()?.let {
        it == VehicleGear.GEAR_DRIVE || it == VehicleGear.GEAR_REVERSE || it >= MANUAL_GEAR_1
    }

    /** m/s 로 들어오므로 km/h 로 환산합니다. */
    fun speedKph(): Float? = readFloat(VehiclePropertyIds.PERF_VEHICLE_SPEED)?.times(3.6f)

    fun isNightMode(): Boolean? = readBool(VehiclePropertyIds.NIGHT_MODE)

    /** 앞유리 와이퍼가 움직이는 중인지 (Pleos 차량 SDK). 미지원·아직 못 읽음이면 null. */
    fun isWiperOn(): Boolean? = pleos.wiperOn

    /** 차량 실내등이 켜져 있는지. 못 읽으면 null */
    fun isCabinLightOn(): Boolean? = pleos.cabinLightOn

    /**
     * 연료 잔량(%). 표준 FUEL_LEVEL(mL) ÷ INFO_FUEL_CAPACITY(mL).
     * 전기차이거나 못 읽으면 null.
     */
    fun fuelPercent(): Int? {
        val level = readFloat(VehiclePropertyIds.FUEL_LEVEL) ?: return null
        val capacity = readFloat(VehiclePropertyIds.INFO_FUEL_CAPACITY) ?: return null
        return fuelPercentOf(level, capacity)
    }

    private fun readInt(propId: Int): Int? = try {
        pm?.getIntProperty(propId, 0)
    } catch (e: Exception) {
        null
    }

    private fun readFloat(propId: Int): Float? = try {
        pm?.getFloatProperty(propId, 0)
    } catch (e: Exception) {
        null
    }

    private fun readBool(propId: Int): Boolean? = try {
        pm?.getBooleanProperty(propId, 0)
    } catch (e: Exception) {
        null
    }
}
