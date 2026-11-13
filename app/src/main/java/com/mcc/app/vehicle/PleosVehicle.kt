package com.mcc.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mcc.app.game.VehicleMoments

/**
 * Pleos 전용 차량 SDK (ai.pleos.playground:Vehicle) 로 읽는 신호.
 *
 * 무엇을 어디서 읽나:
 *   · 기어·속도·야간 모드·주행 규제 → 안드로이드 차량 표준 API (VehicleRepository).
 *     Pleos 가이드가 표준을 우선하라고 하고, Pleos SDK 의 기어는 공식 샘플 목록에서도 빠져 있습니다.
 *   · 그 밖에 일반 앱이 표준 API 로는 읽기 어려운 신호 → 여기 (아래 Signal 목록).
 *
 * 전부 '읽기'만 하고 차량을 조작하지 않습니다. 권한은 모두 normal 등급이라 설치 시 자동 허용됩니다.
 * 값은 '바뀔 때 알림'으로만 받습니다 (폴링 없음 → 주행 중 CPU 부담 없음).
 * 신호 하나가 이 차량에서 지원되지 않아도 나머지는 그대로 동작하고,
 * Pleos 차량 서비스가 없는 기기에서는 전부 조용히 꺼진 채로 앱이 동작합니다.
 */
class PleosVehicle(private val context: Context) {

    /** 연결하는 신호. label = 점검 화면에 보이는 이름과 쓰임새 */
    enum class Signal(val label: String) {
        WIPER("와이퍼 — 우천주행 기록·창밖 비 연출"),
        ODOMETER("주행거리계 — 주행 거리 보정"),
        DOOR("문 열림 — 캐릭터 반응"),
        SEAT("동승석 착석 — 캐릭터 반응"),
        EV_BATTERY("배터리 잔량 — 배고픔·든든함"),
        WINDOW("운전석 창문 — 캐릭터 반응"),
        TURN_SIGNAL("방향지시등 — 주행 기록"),
        CABIN_LIGHT("실내등 — 방 스탠드 켜기"),
    }

    private var vehicle: Vehicle? = null
    private val undo = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val moments = VehicleMoments()
    // 알림은 SDK 의 다른 스레드에서 올 수 있어 동시 접근에 안전한 자료구조를 씁니다.
    private val status = java.util.concurrent.ConcurrentHashMap<Signal, String>()
    private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<Signal>()

    /** 캐릭터가 반응할 순간. 항상 메인 스레드에서 불립니다. */
    var onMoment: (VehicleMoments.Moment) -> Unit = {}

    /** 앞유리 와이퍼가 움직이는 중인지. 아직 못 읽었거나 지원하지 않으면 null */
    @Volatile
    var wiperOn: Boolean? = null
        private set

    /** 차량 누적 주행거리 (km). 못 읽으면 null */
    @Volatile
    var odometerKm: Float? = null
        private set

    /** 차량 실내등이 켜져 있는지 (켜지면 방의 스탠드도 켜집니다). 못 읽으면 null */
    @Volatile
    var cabinLightOn: Boolean? = null
        private set

    /** 연결한 뒤 방향지시등을 켠 횟수 (꺼짐 → 켜짐 전환 수) */
    @Volatile
    var turnSignalCount: Int = 0
        private set
    private var turnSignalOn = false

    /** Pleos 차량 서비스가 이 기기에 있는지 */
    fun isAvailable(): Boolean = try {
        context.packageManager.queryIntentServices(Intent(SERVICE_ACTION), 0).isNotEmpty()
    } catch (t: Throwable) {
        false
    }

    /** 연결하고 알림을 받기 시작합니다. 실패해도 예외를 밖으로 내보내지 않습니다. */
    fun start() {
        if (vehicle != null) return
        if (!isAvailable()) {
            Log.i(TAG, "Pleos 차량 서비스 없음")
            return
        }
        val v = try {
            Vehicle(context.applicationContext).also { it.initialize() }
        } catch (t: Throwable) {
            // SDK·서비스 버전이 맞지 않아도 앱은 계속 동작해야 합니다.
            Log.w(TAG, "Pleos 차량 SDK 연결 실패", t)
            Signal.values().forEach { status[it] = "연결 실패: ${describe(t)}" }
            return
        }
        vehicle = v
        // 신호마다 따로 연결합니다: 하나가 실패해도 나머지는 계속.
        connect(Signal.WIPER) { wiper(v) }
        connect(Signal.ODOMETER) { odometer(v) }
        connect(Signal.DOOR) { doors(v) }
        connect(Signal.SEAT) { seats(v) }
        connect(Signal.EV_BATTERY) { evBattery(v) }
        connect(Signal.WINDOW) { window(v) }
        connect(Signal.TURN_SIGNAL) { turnSignal(v) }
        connect(Signal.CABIN_LIGHT) { cabinLight(v) }
    }

    fun stop() {
        val v = vehicle ?: return
        undo.forEach { runCatching(it).onFailure { t -> Log.w(TAG, "알림 해제 실패", t) } }
        undo.clear()
        runCatching { v.release() }.onFailure { Log.w(TAG, "Pleos 차량 SDK 해제 실패", it) }
        vehicle = null
        wiperOn = null
        odometerKm = null
        cabinLightOn = null
        status.clear()
        seen.clear()
    }

    /** 점검 화면용: 신호별 O(값 받음) / △(연결했지만 값 없음·실패) / X(서비스 없음) */
    fun report(): String = buildString {
        val available = isAvailable()
        Signal.values().forEach { s ->
            val mark = when {
                s in seen -> "O"
                available -> "△"
                else -> "X"
            }
            val detail = status[s] ?: if (available) "값 기다리는 중" else "Pleos 차량 서비스 없음"
            appendLine("  $mark  ${s.label} ($detail)")
        }
    }

    // ────────────────────────────────────────────────
    // 신호별 연결
    // ────────────────────────────────────────────────

    private fun wiper(v: Vehicle) {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                wiperOn = state?.let { isWiping(it.name) }
                got(Signal.WIPER, state?.name)
            }
            override fun onFailed(e: Exception) = failed(Signal.WIPER, e)
        }
        v.getWiper().getCurrentWiperState(area, { listener.onWiperStateUpdated(it) }, { failed(Signal.WIPER, it) })
        v.getWiper().registerWiperState(area, listener)
        undo += { v.getWiper().unregisterWiperState(area, listener) }
    }

    private fun odometer(v: Vehicle) {
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                if (value != null) odometerKm = value
                got(Signal.ODOMETER, value?.let { "%.0f km".format(it) })
            }
            override fun onFailed(e: Exception) = failed(Signal.ODOMETER, e)
        }
        v.getOdometer().getCurrentDistanceDriven({ listener.onDistanceDrivenUpdated(it) }, { failed(Signal.ODOMETER, it) })
        v.getOdometer().registerDistanceDriven(listener)
        undo += { v.getOdometer().unregisterDistanceDriven(listener) }
    }

    private fun doors(v: Vehicle) {
        listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT).forEach { door ->
            val area = DoorArea(door)
            val listener = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    got(Signal.DOOR, pos?.let { "${door.name} ${if (it > 0) "열림" else "닫힘"}" })
                    if (pos != null) moment { moments.door(door.name, pos > 0) }
                }
                override fun onFailed(e: Exception) = failed(Signal.DOOR, e)
            }
            v.getDoor().registerDoorPosition(area, listener)
            undo += { v.getDoor().unregisterDoorPosition(area, listener) }
        }
    }

    private fun seats(v: Vehicle) {
        // 운전석(ROW_1_LEFT)은 빼고 동승석만 봅니다.
        listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { seat ->
            val area = SeatArea(seat)
            val listener = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                    val seated = occupancy?.let(VehicleMoments::seatedFrom)
                    got(Signal.SEAT, seated?.let { "${seat.name} ${if (it) "착석" else "비어 있음"}" })
                    if (seated != null) moment { moments.passenger(seat.name, seated) }
                }
                override fun onFailed(e: Exception) = failed(Signal.SEAT, e)
            }
            v.getSeat().registerSeatOccupancy(area, listener)
            undo += { v.getSeat().unregisterSeatOccupancy(area, listener) }
        }
    }

    /**
     * 연료 잔량(%)은 표준 API(FUEL_LEVEL)로 읽어 여기로 넘겨 받습니다.
     * Pleos SDK 정책 '표준 우선': 연료처럼 AAOS 표준에 있는 값은 표준으로 읽어야 합니다.
     * 전기차 배터리 잔량은 Pleos FAQ 안내대로 Vehicle SDK(getEvBatteryStateOfCharge 계열)로 읽습니다.
     */
    fun onStandardFuel(percent: Int) {
        moment { moments.energy(percent, VehicleMoments.SOURCE_FUEL) }
    }

    private fun evBattery(v: Vehicle) {
        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                got(Signal.EV_BATTERY, value?.let { "$it%" })
                if (value != null) moment { moments.energy(value, VehicleMoments.SOURCE_BATTERY) }
            }
            override fun onFailed(e: Exception) = failed(Signal.EV_BATTERY, e)
        }
        v.getEvBattery().registerEvBatteryStateOfCharge(listener)
        undo += { v.getEvBattery().unregisterEvBatteryStateOfCharge(listener) }
    }

    private fun window(v: Vehicle) {
        val area = WindowArea(WindowValue.ROW_1_LEFT)
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) {
                got(Signal.WINDOW, position?.let { if (it > 0) "열림 ($it)" else "닫힘" })
                if (position != null) moment { moments.window(position > 0) }
            }
            override fun onFailed(e: Exception) = failed(Signal.WINDOW, e)
        }
        v.getWindow().registerWindowPosition(area, listener)
        undo += { v.getWindow().unregisterWindowPosition(area, listener) }
    }

    private fun turnSignal(v: Vehicle) {
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                got(Signal.TURN_SIGNAL, state?.name)
                val on = state != null && isSignalOn(state.name)
                if (on && !turnSignalOn) turnSignalCount++
                turnSignalOn = on
            }
            override fun onFailed(e: Exception) = failed(Signal.TURN_SIGNAL, e)
        }
        v.getTurnSignal().registerTurnSignalState(listener)
        undo += { v.getTurnSignal().unregisterTurnSignalState(listener) }
    }

    private fun cabinLight(v: Vehicle) {
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(value: LightState?) {
                cabinLightOn = value?.let { isSignalOn(it.name) }
                got(Signal.CABIN_LIGHT, value?.name)
            }
            override fun onFailed(e: Exception) = failed(Signal.CABIN_LIGHT, e)
        }
        v.getLight().registerCabinLightState(listener)
        undo += { v.getLight().unregisterCabinLightState(listener) }
    }

    // ────────────────────────────────────────────────

    private inline fun connect(signal: Signal, block: () -> Unit) {
        try {
            block()
            status.putIfAbsent(signal, "연결됨 · 값 기다리는 중")
        } catch (t: Throwable) {
            status[signal] = "연결 실패: ${describe(t)}"
            Log.w(TAG, "${signal.name} 연결 실패", t)
        }
    }

    private fun got(signal: Signal, value: String?) {
        if (value != null) seen += signal
        status[signal] = value ?: "값 없음"
    }

    private fun failed(signal: Signal, e: Exception) {
        status[signal] = "읽기 실패: ${describe(e)}"
        Log.w(TAG, "${signal.name} 읽기 실패", e)
    }

    /** 순간을 계산해 메인 스레드로 넘깁니다 (SDK 알림은 다른 스레드에서 올 수 있음). */
    private fun moment(compute: () -> VehicleMoments.Moment?) {
        main.post { compute()?.let(onMoment) }
    }

    private fun describe(t: Throwable): String = t.message ?: t.javaClass.simpleName

    companion object {
        private const val TAG = "MCC_Pleos"

        /** Pleos 차량 서비스의 바인딩 액션 */
        private const val SERVICE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"

        /** '꺼짐·모름'으로 볼 상태 이름. 이 밖의 상태(ON·LOW·HIGH·LEFT·RIGHT 등)는 켜진 것으로 봅니다. */
        private val OFF_NAMES = setOf("OFF", "OTHER", "SERVICE", "UNKNOWN", "INVALID", "NONE")

        /** 와이퍼 상태 이름 → 움직이는 중인지 (순수 함수, 테스트 대상) */
        fun isWiping(stateName: String): Boolean = stateName.uppercase() !in OFF_NAMES

        /** 방향지시등 상태 이름 → 켜졌는지 (순수 함수, 테스트 대상) */
        fun isSignalOn(stateName: String): Boolean = stateName.uppercase() !in OFF_NAMES
    }
}
