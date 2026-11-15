package com.mcc.app.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.mcc.app.region.Region
import com.mcc.app.region.Regions

/**
 * GPS 위치 → 지역.
 *
 * 표준 Android LocationManager 를 씁니다. 인터넷이 필요 없습니다.
 * 위치는 메모리에만 두고 저장하지 않습니다. 앱이 쓰는 건 판정된 '지역 이름'뿐입니다.
 *
 * 에뮬레이터 기본 위치는 미국(Mountain View)이라 지역이 '확인 안 됨'으로 나옵니다.
 * 에뮬레이터 ⋮ → Location 에서 서울·부산 등으로 옮겨 테스트하세요.
 */
class LocationTracker(private val context: Context) {

    private var manager: LocationManager? = null

    /** 가장 최근 위치. 권한이 없거나 GPS 가 없으면 null. */
    var lastLocation: Location? = null
        private set

    // 모든 메서드를 직접 구현합니다. 람다로 줄이면 Android 10 이하에서
    // 시스템이 onStatusChanged 를 부를 때 앱이 죽는 알려진 문제가 있습니다.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    /** 위치 수신 시작. 권한이 없으면 false 를 돌려주고 조용히 넘어갑니다. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val m = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        manager = m
        return try {
            lastLocation = m.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            m.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                // 지역(시·도) 판정에만 쓰므로 자주 받을 필요가 없습니다. 10초 또는 100m 마다.
                // (Pleos 성능 가이드: 불필요한 센서 수집·폴링 최소화)
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper(),
            )
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "위치 권한 없음 — 지역 판정 없이 진행")
            false
        } catch (e: Exception) {
            Log.w(TAG, "위치 수신 실패: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            manager?.removeUpdates(listener)
        } catch (ignored: Exception) {
        }
    }

    /** 지금 있는 지역. 모르면 null. */
    fun currentRegion(): Region? {
        val loc = lastLocation ?: return null
        return Regions.classify(loc.latitude, loc.longitude)
    }

    companion object {
        private const val LOCATION_INTERVAL_MS = 10_000L
        private const val LOCATION_MIN_DISTANCE_M = 100f
        private const val TAG = "MCC_Location"
    }
}
