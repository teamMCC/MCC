package com.mcc.app

/**
 * 권한 관문 (순수 로직, 테스트 대상).
 *
 *   · 필수: 차량 속도 — 주행 거리를 재야 알이 자라므로 MCC 의 핵심입니다.
 *     없으면 앱을 닫지 않고, 홈에 "허용하기" 안내를 띄웁니다. 켤 때마다 다시 묻습니다.
 *   · 선택: 위치(지역 악세사리)·차량 에너지(연료 말풍선) — 처음 한 번만 묻고, 거부하면 그 기능만 빠집니다.
 *   · 한 번 허용하면 다시 묻지 않습니다 (안드로이드가 허용 상태를 기억).
 *   · 안드로이드는 두 번 거부하면 권한 창을 더 띄우지 않으므로, 그 뒤 '허용하기'는 설정 화면으로 안내합니다.
 * (기어·야간 모드·Pleos 신호 12개는 설치할 때 자동 허용이라 묻지 않습니다.)
 */
object PermissionGate {

    const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    const val CAR_ENERGY = "android.car.permission.CAR_ENERGY"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    /** 필수 묶음 */
    val REQUIRED: List<List<String>> = listOf(listOf(CAR_SPEED))

    /** 선택 묶음. 묶음마다 하나라도 허용되면 충족 (위치는 정밀·대략 중 하나면 됨) */
    val OPTIONAL: List<List<String>> = listOf(
        // Android 12+ 는 정밀 위치를 대략 위치와 '같은 요청'에 넣지 않으면 요청 자체를 무시합니다.
        listOf(FINE_LOCATION, COARSE_LOCATION),
        listOf(CAR_ENERGY),
    )

    /** 핵심 기능(주행 기록)을 쓸 수 있는지 */
    fun coreReady(isGranted: (String) -> Boolean): Boolean =
        REQUIRED.all { group -> group.any(isGranted) }

    /** 앱을 켤 때 물을 권한: 빠진 필수 + (아직 한 번도 안 물었으면) 빠진 선택 */
    fun toAskOnLaunch(isGranted: (String) -> Boolean, optionalAskedBefore: Boolean): List<String> {
        val groups = REQUIRED + if (optionalAskedBefore) emptyList() else OPTIONAL
        return groups.filter { group -> group.none(isGranted) }.flatten()
    }

    /** 설정 화면에 보여 줄 권한 묶음 */
    data class Item(val label: String, val use: String, val required: Boolean, val permissions: List<String>)

    val ITEMS: List<Item> = listOf(
        Item("차량 속도", "주행 거리 · 알 성장", true, REQUIRED[0]),
        Item("위치", "지역 악세사리 (좌표 저장 안 함)", false, OPTIONAL[0]),
        Item("차량 에너지", "연료 말풍선", false, OPTIONAL[1]),
    )

    enum class Action { NONE, ASK, OPEN_SETTINGS }

    /**
     * 권한 묶음 하나를 켜려고 눌렀을 때.
     * @param canAskAgain 안드로이드가 권한 창을 다시 띄워 주는지 (shouldShowRequestPermissionRationale)
     * @param askedBefore 이 권한을 물어본 적이 있는지 — 처음에도 canAskAgain 은 false 라서 구분이 필요합니다.
     */
    fun action(group: List<String>, isGranted: (String) -> Boolean, canAskAgain: (String) -> Boolean, askedBefore: Boolean): Action =
        when {
            group.any(isGranted) -> Action.NONE
            !askedBefore || group.any(canAskAgain) -> Action.ASK
            else -> Action.OPEN_SETTINGS
        }

    /** 홈의 '허용하기' (속도) */
    fun speedAction(isGranted: (String) -> Boolean, canAskAgain: (String) -> Boolean, askedBefore: Boolean): Action =
        action(REQUIRED[0], isGranted, canAskAgain, askedBefore)
}
