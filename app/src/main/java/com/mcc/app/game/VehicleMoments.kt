package com.mcc.app.game

/**
 * 차량 상태가 바뀌는 '순간'을 골라냅니다 (문이 열림, 동승자가 탐, 연료가 바닥남 …).
 *
 * 차량 SDK 는 값이 바뀔 때마다 알려 주는데, 캐릭터가 반응할 건 그중 일부입니다.
 *   · 처음 받은 값은 기준으로만 삼고 반응하지 않습니다 (앱을 켰을 때 이미 타 있던 동승자 등).
 *     단, 연료·배터리는 처음부터 부족하면 바로 알려 줍니다.
 *   · 같은 상태가 반복되면 다시 반응하지 않습니다.
 * 화면·차량 API 와 분리된 순수 로직이라 단위 테스트로 검증합니다.
 */
class VehicleMoments {

    enum class Moment {
        DOOR_OPEN, PASSENGER_IN,
        ENERGY_LOW, ENERGY_FULL, CHARGING,
        WINDOW_OPEN,
        /** 내비게이션: 길 안내 시작 · 목적지 도착 */
        ROUTE_START, ARRIVED,
    }

    private val doors = HashMap<String, Boolean>()
    private val seats = HashMap<String, Boolean>()
    /** 잔량은 출처(전기차 배터리·연료)마다 따로 기억합니다. 섞이면 두 값 사이를 오가며 '충전됨'이 반복됩니다. */
    private val energy = HashMap<String, Int>()
    private var chargePort: Boolean? = null
    private var window: Boolean? = null

    /** 문 하나의 열림 상태. 닫혀 있다가 열리면 DOOR_OPEN */
    fun door(area: String, open: Boolean): Moment? {
        val before = doors.put(area, open)
        return if (before == false && open) Moment.DOOR_OPEN else null
    }

    /** 동승석(조수석·뒷좌석) 착석. 비어 있다가 앉으면 PASSENGER_IN */
    fun passenger(seat: String, seated: Boolean): Moment? {
        val before = seats.put(seat, seated)
        return if (before == false && seated) Moment.PASSENGER_IN else null
    }

    /**
     * 연료 또는 배터리 잔량(%).
     *   LOW_PERCENT 이하로 내려오면(또는 처음부터 그 이하면) ENERGY_LOW,
     *   한 번에 REFILL_STEP 이상 늘면 ENERGY_FULL (주유·충전).
     */
    fun energy(percent: Int, source: String = SOURCE_BATTERY): Moment? {
        val before = energy.put(source, percent)
        return when {
            before != null && percent - before >= REFILL_STEP -> Moment.ENERGY_FULL
            percent <= LOW_PERCENT && (before == null || before > LOW_PERCENT) -> Moment.ENERGY_LOW
            else -> null
        }
    }

    /** 충전구. 닫혀 있다가 열리면 CHARGING */
    fun chargePort(open: Boolean): Moment? {
        val before = chargePort
        chargePort = open
        return if (before == false && open) Moment.CHARGING else null
    }

    /** 운전석 창문. 닫혀 있다가 열리면 WINDOW_OPEN */
    fun window(open: Boolean): Moment? {
        val before = window
        window = open
        return if (before == false && open) Moment.WINDOW_OPEN else null
    }

    companion object {
        /**
         * Pleos SDK 의 착석 값 → 실제로 앉았는지.
         * Pleos Connect v2.1.0 에뮬레이터에서 확인한 결과(2026-11-13), 콘솔에서 '탑승'으로 두면 false,
         * '비어 있음'으로 두면 true 가 옵니다. 그래서 뒤집어서 씁니다.
         * 실차에서 반대로 나오면 이 한 줄만 고치면 됩니다.
         */
        fun seatedFrom(sdkValue: Boolean): Boolean = !sdkValue

        /** 이 이하면 '배고픔' */
        const val SOURCE_BATTERY = "battery"
        const val SOURCE_FUEL = "fuel"
        const val LOW_PERCENT = 15
        /** 한 번에 이만큼 늘면 주유·충전으로 봄 */
        const val REFILL_STEP = 10
    }
}
