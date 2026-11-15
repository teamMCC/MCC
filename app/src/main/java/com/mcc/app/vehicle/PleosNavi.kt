package com.mcc.app.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mcc.app.game.VehicleMoments

/**
 * Pleos 내비게이션 (ai.pleos.playground:NaviHelper) 의 길 안내 상태.
 *
 * MCC 는 길 안내가 '시작됐다 / 도착했다'는 사실만 받습니다.
 *   · 목적지 이름·주소·좌표는 읽지도, 저장하지도 않습니다.
 *   · 경로를 요청하거나 바꾸지 않습니다 (그래서 NAVI_ROUTE_SEARCH 권한은 선언하지 않음).
 * 내비 서비스가 없는 기기에서는 조용히 꺼진 채로 앱이 동작합니다.
 */
class PleosNavi(private val context: Context) {

    private var navi: NaviHelper? = null
    private val main = Handler(Looper.getMainLooper())

    /** 길 안내 시작·도착 순간. 항상 메인 스레드에서 불립니다. */
    var onMoment: (VehicleMoments.Moment) -> Unit = {}

    /** 지금 길 안내 중인지. 아직 모르면 null */
    @Volatile
    var routing: Boolean? = null
        private set

    /** 연결한 뒤 목적지에 도착한 횟수 */
    @Volatile
    var arrivedCount: Int = 0
        private set

    @Volatile
    private var status: String = "연결 전"
    @Volatile
    private var connected = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            routing = routeStateInfo.isRouting
            status = if (routeStateInfo.isRouting) "길 안내 중" else "길 안내 없음"
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            routing = true
            status = "길 안내 시작"
            emit(VehicleMoments.Moment.ROUTE_START)
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            routing = false
            arrivedCount++
            status = "목적지 도착"
            emit(VehicleMoments.Moment.ARRIVED)
        }

        override fun onRouteCancelled() {
            routing = false
            status = "길 안내 취소"
        }

        override fun onRouteEnded() {
            routing = false
            status = "길 안내 끝"
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "내비 오류: $errorCode")
        }
    }

    /** 연결하고 알림을 받기 시작합니다. 실패해도 예외를 밖으로 내보내지 않습니다. */
    fun start() {
        if (navi != null) return
        try {
            val helper = NaviHelper(context.applicationContext)
            navi = helper
            status = "연결 중"
            helper.initialize(
                {
                    connected = true
                    status = "연결됨 · 길 안내 기다리는 중"
                    try {
                        helper.addListener(listener)
                        // 앱을 켰을 때 이미 안내 중인지 한 번 물어봅니다 (답은 onRouteStateInfo 로 옴).
                        helper.getRouteStateInfo()
                    } catch (t: Throwable) {
                        status = "알림 등록 실패: ${describe(t)}"
                        Log.w(TAG, "내비 알림 등록 실패", t)
                    }
                },
                { t ->
                    status = "연결 실패: ${describe(t)}"
                    Log.w(TAG, "내비 연결 실패", t)
                },
            )
        } catch (t: Throwable) {
            // SDK·서비스가 없거나 버전이 맞지 않아도 앱은 계속 동작해야 합니다.
            navi = null
            status = "내비 서비스 없음: ${describe(t)}"
            Log.w(TAG, "내비 사용 불가", t)
        }
    }

    fun stop() {
        val helper = navi ?: return
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }.onFailure { Log.w(TAG, "내비 해제 실패", it) }
        navi = null
        connected = false
        routing = null
        status = "연결 전"
    }

    /** 점검 화면용 한 줄 */
    fun report(): String {
        val mark = when {
            routing != null -> "O"
            connected -> "△"
            else -> "X"
        }
        return "  $mark  내비게이션 길 안내 — 출발·도착 반응 ($status)\n"
    }

    private fun emit(m: VehicleMoments.Moment) {
        main.post { onMoment(m) }
    }

    private fun describe(t: Throwable): String = t.message ?: t.javaClass.simpleName

    companion object {
        private const val TAG = "MCC_Navi"
    }
}
