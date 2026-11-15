package com.mcc.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import com.mcc.app.game.WindowArea
import com.mcc.app.ui.NightLight
import android.view.animation.OvershootInterpolator
import com.mcc.app.ui.EggCrack
import com.mcc.app.ui.HitTest
import com.mcc.app.ui.RainView
import com.mcc.app.ui.WindowMask
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.mcc.app.data.LocalStore
import com.mcc.app.databinding.ActivityMainBinding
import com.mcc.app.game.CollectionRules
import com.mcc.app.game.Creature
import com.mcc.app.game.Egg
import com.mcc.app.game.GameRules
import com.mcc.app.game.GameState
import com.mcc.app.game.AmbienceRules
import com.mcc.app.game.DemoUnlock
import com.mcc.app.game.Greetings
import com.mcc.app.game.RoomCatalog
import com.mcc.app.game.RoomItem
import com.mcc.app.game.RoomRules
import com.mcc.app.game.RoomSlot
import com.mcc.app.game.Samples
import com.mcc.app.game.Texts
import com.mcc.app.trait.GeneCode
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.TraitEngine
import com.mcc.app.ui.Assets
import com.mcc.app.ui.CharacterView
import com.mcc.app.ui.SimPanel
import com.mcc.app.vehicle.DemoDrive
import com.mcc.app.vehicle.DrivingSession
import com.mcc.app.vehicle.LocationTracker
import com.mcc.app.vehicle.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * MCC 메인 화면.
 *
 * 흐름
 *   기어 D  →  DrivingSession.start()
 *   주행 중  →  500ms마다 sample() — 속도·야간·지역  (주행 규제 중 화면 조작 차단)
 *   기어 P  →  GameRules.applyDrive()
 *               · 대표 캐릭터: 지역 20km → 지역 악세사리
 *               · 알: 품는 동안의 운전 성향 누적, 30km 에 부화
 *
 * 게임 규칙은 전부 GameRules / Genetics 에 있고, 이 파일은 화면과 버튼만 다룹니다.
 *
 * 메인 화면
 *   · 방(테마·소품) 위 가운데에 대표 캐릭터. 누르면 콩 뛰며 말풍선 (기어 P 에서만)
 *   · 시동 직후와 주차 직후에 3초 말풍선 (Greetings, 15자 이하)
 *   · 주행 중(주행 규제 또는 기어가 P 가 아님): 메뉴·말풍선·대화상자를 닫고
 *     캐릭터만 '함께 달리는' 모습으로 두며, 아래에 한 줄 안내만 보여 줍니다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var vehicle: VehicleRepository
    private lateinit var location: LocationTracker
    private lateinit var store: LocalStore
    private lateinit var state: GameState
    private lateinit var btnBasicEgg: Button

    private val rng: Random = Random.Default
    private val newId: () -> String = { UUID.randomUUID().toString().take(8) }

    // ── 주행 판정 ──
    private var session: DrivingSession? = null
    private var zeroSpeedTicks = 0
    private var sensorsStarted = false
    private var vehicleConnected = false
    private var drivingLock: Boolean? = null
    private var uxListening = false
    /** 주행 규제 신호 (CarUxRestrictions) */
    private var uxRestricted = false
    /** 기어가 P 가 아님. 캐릭터 상호작용·꾸미기는 P 에서만 합니다. */
    private var gearDriving = false
    /** 잠긴 이유 (시연 빌드에서만 안내 줄 아래에 보여 줍니다) */
    private var lockReason = ""
    private var homeArt: CharacterView? = null
    private var bubble: TextView? = null
    private val hideBubble = Runnable { bubble?.visibility = View.GONE }

    // ── 밤·비 연출 ──
    /** 지금 화면이 밤 연출 중인지 / 창밖에 비가 오는 중인지 */
    private var nightNow = false
    private var rainNow = false
    /**
     * 사용자가 오른쪽 위 버튼으로 고른 상태. null = 차량 신호를 따름.
     * 차량 신호(야간 모드·와이퍼)가 바뀌는 순간 null 로 돌아가, 와이퍼를 켜면 비가 자동으로 켜지고
     * 끄면 자동으로 꺼집니다. 그 사이에는 버튼으로 마음대로 켜고 끌 수 있습니다.
     */
    private var nightChoice: Boolean? = null
    private var rainChoice: Boolean? = null
    /** 마지막으로 읽은 차량 신호 (바뀌는 순간을 알아채는 데 사용) */
    private var lastVehicleNight: Boolean? = null
    private var lastVehicleRain: Boolean? = null
    private var rainView: RainView? = null

    /** 주행 시작 때의 주행거리계·방향지시등 횟수 (Pleos 차량 SDK 가 알려 줄 때만) */
    private var tripStartOdometerKm: Float? = null
    private var tripStartTurnSignals = 0
    private var tripStartArrivals = 0
    /** 주행 중이라 못 띄운 말풍선 (잠금이 풀리면 한 번 보여 줌) */
    private var heldBubble: String? = null
    /** 부화 준비된 알을 지금까지 누른 횟수 (금 단계). 주행이 시작되면 0 으로 */
    private var crackTaps = 0
    /** 알이 터지는 연출 중 (그 사이 누름은 무시) */
    private var hatching = false
    /** 차량 실내등이 켜져 있는지 (방 스탠드 불빛에 반영) */
    private var cabinLightNow = false
    private var homeMenuExpanded = true
    private var menuAnimator: ValueAnimator? = null
    private var menuSwipeTracking = false
    private var menuSwipeConsuming = false
    private var menuSwipeStartX = 0f
    private var menuSwipeStartY = 0f
    private val menuTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    /** 열려 있는 대화상자. 주행 규제가 켜지면 전부 닫습니다 (Pleos 주행 규제 가이드). */
    private val openDialogs = mutableListOf<AlertDialog>()

    /** 각 메뉴에 맞는 데이터만 표시하는 독립 화면. */
    private enum class Section(val title: String, val subtitle: String) {
        COLLECTION("캐릭터 보관함", "함께한 친구들을 관리해요 · 최대 5마리"),
        EGG("나의 알", "주행하며 알을 품고 부화를 기다려요"),
        GENETICS("유전", "부모를 선택해 새로운 알을 만들어요"),
        TEST("주행 테스트", "데모 주행과 차량 연결 상태를 확인해요"),
        ODDS("확률표", "부화할 캐릭터의 예상 형질을 살펴봐요"),
        BOOK("도감", "같은 파츠를 3번 모으면 ★, 6번 모으면 ★★ 이 열려요"),
        ROOM("방 꾸미기", "방 테마와 소품을 골라요 · 주행과 도감으로 더 열려요"),
        SETTINGS("설정", "저장된 데이터를 관리해요"),
    }

    // ── 유전 선택 (저장하지 않음) ──
    private val selectedIds = mutableListOf<String>()
    private var partner: Genome? = null
    private var partnerLabel: String? = null

    // ── 데모 ──
    private var demoIndex = 0
    private var demoBusy = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, granted) -> Log.i(TAG, "${if (granted) "허용" else "거부"}  $perm") }
        // 허용 여부와 상관없이 시작합니다. 속도가 없으면 홈에 '허용하기' 안내가 뜨고, 위치·에너지는 그 기능만 빠집니다.
        startSensors()
        refreshPermissionState()
    }

    /** 위치 수신이 켜졌는지 (나중에 설정에서 위치를 허용하고 돌아오면 그때 켭니다) */
    private var locationOn = false

    // ═══════════════════════════════════════════════
    // 생명주기
    // ═══════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCatcher()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        rainView = RainView(this).also { rain ->
            rain.area = { windowOnScreen() }
            b.homeWeather.addView(rain, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        store = LocalStore(this)
        vehicle = VehicleRepository(this)
        location = LocationTracker(this)
        state = store.load()
        if (GameRules.ensureStarterEgg(state)) store.save(state)

        b.btnDemo.setOnClickListener { runDemoDrive() }
        b.btnDump.setOnClickListener { b.txtSignals.text = vehicle.signalReport() }
        b.btnReset.setOnClickListener { confirmReset() }
        b.btnPartner.setOnClickListener { if (partner != null) clearPartner() else askPartnerCode() }
        b.btnBreed.setOnClickListener { breed() }

        btnBasicEgg = Button(this).apply {
            text = "기본 알 받기"
            textSize = TEXT_BODY
            setAllCaps(false)
            backgroundTintList = null
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.mcc_primary_button)
            setTextColor(Color.WHITE)
            setOnClickListener { takeBasicEgg() }
        }
        // 직접 운전 시뮬레이터: 주차하면 실제 주행과 같은 경로(finishDrive)로 들어갑니다.
        b.simContainer.addView(SimPanel(this) { drive -> finishDrive(drive, demoLabel = "직접 운전 시뮬레이터") })
        // 시연용: 도감·악세서리·방 모두 해금 / 되돌리기
        b.simContainer.addView(btnUnlockAll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(80)).apply { topMargin = dp(14) })
        updateUnlockAllLabel()
        // 오른쪽 위 밤·비 버튼
        b.homeNightToggle.setOnClickListener { toggleNight() }
        b.homeRainToggle.setOnClickListener { toggleRain() }
        b.homePermissionBar.setOnClickListener { onAllowSpeed() }
        refreshAmbienceButtons()

        b.eggSection.addView(
            btnBasicEgg,
            b.eggSection.indexOfChild(b.txtEgg) + 1,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply {
                topMargin = dp(12)
            }
        )

        setupHome()
        applyDemoToolsVisibility()
        updateDemoLabel()
        renderAll()
        requestPermissionsThenStart()
        showLastCrashIfAny()
        // 시동 인사 (기어 P 일 때만 — 주행 중이면 setDrivingLock 이 바로 닫습니다)
        b.homeCharacter.postDelayed({ greet() }, GREETING_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 권한을 켜고 돌아왔을 수 있으므로 다시 확인합니다. (속도는 매번 읽으므로 바로 반영됩니다)
        if (sensorsStarted) refreshPermissionState()
        rainView?.raining = rainNow && drivingLock != true
    }

    override fun onPause() {
        // 화면이 안 보이면 비 애니메이션을 멈춰 CPU 를 아낍니다.
        rainView?.raining = false
        super.onPause()
    }

    override fun onDestroy() {
        vehicle.disconnect()
        location.stop()
        super.onDestroy()
    }

    /**
     * Pleos 보안·개인정보 가이드: 위치 등을 요청할 때는 '왜 필요한지' 먼저 알린다.
     * 빠진 권한이 없으면 묻지 않고 시작합니다. 아니면 목적을 설명한 뒤 시스템 권한 창을 띄웁니다.
     * 필수(속도)는 켤 때마다, 선택(위치·에너지)은 처음 한 번만 묻습니다 ([PermissionGate]).
     */
    private fun requestPermissionsThenStart() {
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val ask = PermissionGate.toAskOnLaunch(::isGranted, prefs.getBoolean(KEY_OPTIONAL_ASKED, false))
        if (ask.isEmpty()) {
            startSensors()
            updatePermissionBar()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.purpose_title)
            .setMessage(R.string.purpose_body)
            .setCancelable(false)
            .setPositiveButton(R.string.purpose_ok) { _, _ -> launchPermissions(ask) }
            .showTracked()
    }

    private fun launchPermissions(perms: List<String>) {
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit()
        prefs.putBoolean(KEY_OPTIONAL_ASKED, true)
        if (PermissionGate.CAR_SPEED in perms) prefs.putBoolean(KEY_SPEED_ASKED, true)
        prefs.apply()
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** 홈의 '허용하기' 안내: 속도 권한이 없고 주행 중이 아닐 때만 보입니다. */
    private fun updatePermissionBar() {
        val show = !PermissionGate.coreReady(::isGranted) && drivingLock != true
        b.homePermissionBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** 홈의 '허용하기' (차량 속도) */
    private fun onAllowSpeed() = onAllow(PermissionGate.ITEMS.first { it.required })

    /** 권한 하나를 켜려고 눌렀을 때: 권한 창을 다시 띄우거나, 더 뜨지 않으면 설정 화면으로 안내합니다. */
    private fun onAllow(item: PermissionGate.Item) {
        if (drivingLock == true) return
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val asked = prefs.getBoolean(if (item.required) KEY_SPEED_ASKED else KEY_OPTIONAL_ASKED, false)
        when (PermissionGate.action(item.permissions, ::isGranted, { shouldShowRequestPermissionRationale(it) }, asked)) {
            PermissionGate.Action.NONE -> refreshPermissionState()
            PermissionGate.Action.ASK -> launchPermissions(item.permissions)
            PermissionGate.Action.OPEN_SETTINGS -> showPermissionSettings()
        }
    }

    /** 설정 탭의 '권한' 목록: 묶음마다 지금 상태와, 누르면 켜기(권한 창) 또는 끄기(시스템 설정) */
    private fun renderPermissionRows() {
        val box = b.permissionContainer
        box.removeAllViews()
        PermissionGate.ITEMS.forEach { item ->
            val on = item.permissions.any(::isGranted)
            val row = TextView(this).apply {
                text = "${item.label} ${if (item.required) "(필수)" else "(선택)"}  ·  ${if (on) "허용됨" else "꺼짐"}\n" +
                    "${item.use}  —  ${if (on) "누르면 앱 설정 열기" else "눌러서 켜기"}"
                textSize = TEXT_BODY
                setTextColor(Color.parseColor(if (on) "#5F4B3E" else "#8A4B2B"))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.mcc_panel)
                setPadding(dp(24), dp(14), dp(24), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener { if (on) openAppSettings() else onAllow(item) }
            }
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) })
        }
    }

    /** 권한 상태가 바뀌었을 수 있을 때: 홈 안내·설정 목록·위치 수신을 맞춥니다. */
    private fun refreshPermissionState() {
        updatePermissionBar()
        renderPermissionRows()
        if (sensorsStarted && !locationOn &&
            (isGranted(PermissionGate.FINE_LOCATION) || isGranted(PermissionGate.COARSE_LOCATION))
        ) locationOn = location.start()
    }

    /** 이 앱의 시스템 설정 화면 (권한 켜기·끄기는 안드로이드 설정에서만 할 수 있습니다) */
    private fun openAppSettings() {
        if (drivingLock == true) return
        try {
            // 새 작업(task)으로 엽니다. 안 그러면 설정 화면이 MCC 위에 쌓여서,
            // 아래 줄의 MCC 아이콘을 눌러도 계속 설정 화면이 나옵니다.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "앱 설정 화면 열기 실패", e)
        }
    }

    /** 두 번 거부해 권한 창이 더 뜨지 않을 때: 사용자가 '허용하기'를 눌렀으므로 설정 화면을 안내합니다. */
    private fun showPermissionSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_settings_title)
            .setMessage(R.string.permission_settings_body)
            .setPositiveButton(R.string.permission_settings_open) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.permission_settings_later, null)
            .showTracked()
    }

    private fun startSensors() {
        if (sensorsStarted) return
        sensorsStarted = true
        locationOn = location.start()
        // 차량 상태가 바뀌는 순간(문·동승자·연료 등)에 캐릭터가 한마디 합니다. 주행 중에는 말풍선이 뜨지 않습니다.
        vehicle.pleos.onMoment = { moment -> showBubble(Greetings.moment(rng, moment)) }
        // 내비게이션: 길 안내 시작·도착. 도착은 보통 주행 중에 오므로 화면 잠금이 풀릴 때 말합니다.
        vehicle.navi.onMoment = { moment ->
            val text = Greetings.moment(rng, moment)
            if (drivingLock == true) heldBubble = text else showBubble(text)
        }
        vehicleConnected = vehicle.connect()
        if (vehicleConnected) {
            // 주행 규제는 폴링이 아니라 CarUxRestrictions 변경 알림으로 받습니다.
            uxListening = vehicle.listenDrivingRestrictions { restricted ->
                runOnUiThread {
                    uxRestricted = restricted
                    refreshLock()
                }
            }
        }
        loop()   // 차량 서비스가 없어도 위치·화면 갱신은 계속합니다
    }

    // ═══════════════════════════════════════════════
    // 주행 감지 (500ms)
    // ═══════════════════════════════════════════════

    private fun loop() = lifecycleScope.launch {
        while (isActive) {
            try {
                tick()
            } catch (e: Exception) {
                // 차량 신호 하나가 예외를 던져도 앱 전체가 죽지 않게 막습니다.
                Log.e(TAG, "tick 오류", e)
                saveCrash(e)
            }
            // 주행 기록 중이거나 화면이 보일 때는 0.5초, 주차 중 앱이 뒤에 있을 때는 2초마다만 확인합니다.
            // (Pleos 성능 가이드: 백그라운드 타이머·폴링 최소화. 주행 시작은 2초 안에 잡힙니다)
            val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            delay(if (session != null || visible) SAMPLE_INTERVAL_MS else BACKGROUND_INTERVAL_MS)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val region = location.currentRegion()
        val regionText = region?.label ?: "확인 안 됨"

        if (!vehicleConnected) {
            b.txtStatus.text = getString(R.string.status_no_vehicle) + "\n지역 $regionText"
            setDrivingLock(false)
            updateAmbience(null, null)
            return
        }

        val gear = vehicle.gearLabel() ?: "읽을 수 없음"
        val speed = vehicle.speedKph()
        val tripOver = isTripOver(speed)

        // 기어가 읽히면 P 해제가 시작 신호, 못 읽으면 속도가 시작 신호입니다.
        // 기어가 D·R 이면 시작. 기어를 못 읽거나 '알 수 없음'·N 이면 속도로 판단합니다.
        val driveGear = vehicle.isInDriveGear()
        val night = vehicle.isNightMode()
        val wiper = vehicle.isWiperOn()
        vehicle.fuelPercent()?.let { vehicle.pleos.onStandardFuel(it) }
        val shouldStart = driveGear == true || (speed ?: 0f) > MOVING_SPEED_KPH
        if (shouldStart && session == null) {
            session = DrivingSession().apply { start(now) }
            tripStartOdometerKm = vehicle.pleos.odometerKm
            tripStartTurnSignals = vehicle.pleos.turnSignalCount
            tripStartArrivals = vehicle.navi.arrivedCount
            zeroSpeedTicks = 0
            Log.i(TAG, "주행 시작")
        }

        session?.let { s ->
            if (s.isActive) {
                s.sample(
                    nowMs = now,
                    speedKph = speed,
                    // 조향각은 제조사 전용 권한이라 읽지 않습니다 (Pleos 심사 기준).
                    steeringDeg = null,
                    // 와이퍼는 Pleos 차량 SDK 로 읽고, 못 읽으면 null
                    wiperOn = wiper,
                    nightMode = night,
                    // 내비 과속 경고 신호를 연결하기 전까지는 국내 고속도로 최고 제한속도를 기준으로 봅니다.
                    // 이게 없으면 실제 주행은 전부 '무과속'으로 잡혀 모범형 확률만 오릅니다.
                    isOverSpeed = speed?.let { it > OVER_SPEED_PROXY_KPH },
                    region = region,
                )
            }
            if (tripOver && s.isActive) {
                s.stop(now)
                session = null
                // 주행거리계를 읽을 수 있으면 그 값으로 거리를 맞추고, 방향지시등 사용 횟수를 남깁니다.
                val odoStart = tripStartOdometerKm
                val odoEnd = vehicle.pleos.odometerKm
                if (odoStart != null && odoEnd != null) s.calibrateDistance(odoEnd - odoStart)
                s.addTurnSignals(vehicle.pleos.turnSignalCount - tripStartTurnSignals)
                s.addArrivals(vehicle.navi.arrivedCount - tripStartArrivals)
                Log.i(TAG, "주행 종료")
                finishDrive(s, demoLabel = null)
            }
        }

        val speedText = speed?.let { "%.0f km/h".format(it) } ?: "—"
        val mode = if (session != null) "주행 기록 중" else "정차"
        b.txtStatus.text = "기어 $gear · 속도 $speedText · $mode\n지역 $regionText"
        // 리스너 등록에 실패한 차량에서만 폴링으로 대신합니다.
        if (!uxListening) uxRestricted = vehicle.isDriving() == true
        // 기어 D·R 이거나 실제로 움직이고 있으면 조작을 막습니다 (상호작용은 P 에서만).
        gearDriving = driveGear == true || (speed ?: 0f) > MOVING_SPEED_KPH
        lockReason = when {
            uxRestricted -> "주행 규제"
            driveGear == true -> "기어 $gear"
            gearDriving -> "속도 $speedText"
            else -> ""
        }
        refreshLock()
        updateAmbience(night, wiper)
    }

    // ═══════════════════════════════════════════════
    // 밤·비 연출 (차량 야간 모드 · 와이퍼)
    // ═══════════════════════════════════════════════

    /**
     * 차량 신호(또는 시연 설정)에 맞춰 방 분위기를 바꿉니다.
     *   · 야간 모드 → 방이 남색으로 어두워지고 화면 전체도 살짝 낮아짐, 스탠드 소품에 불빛
     *   · 와이퍼 작동 → 방 창문 안에 비
     * 바뀌는 순간에만 다시 그리고, 주차 중이면 캐릭터가 한마디 합니다.
     * 화면 밝기 자체는 차량이 조절하므로 앱은 건드리지 않습니다.
     */
    private fun updateAmbience(vehicleNight: Boolean?, vehicleRain: Boolean?) {
        // 차량 신호가 바뀌면 버튼으로 고른 상태는 버리고 차량을 따릅니다 (와이퍼 켬 → 비 켬, 끔 → 비 끔).
        if (vehicleNight != lastVehicleNight) nightChoice = null
        if (vehicleRain != lastVehicleRain) rainChoice = null
        lastVehicleNight = vehicleNight
        lastVehicleRain = vehicleRain
        val night = AmbienceRules.effective(nightChoice, vehicleNight)
        val rain = AmbienceRules.effective(rainChoice, vehicleRain)
        if (night != nightNow) {
            nightNow = night
            applyNight()
            if (night) showBubble(Greetings.nightOn(rng))
        }
        if (rain != rainNow) {
            rainNow = rain
            rainView?.raining = rain && drivingLock != true
            if (rain) showBubble(Greetings.rain(rng))
        }
        refreshAmbienceButtons()
        // 차량 실내등을 켜면 방의 스탠드도 켜집니다 (낮에도).
        val cabinLight = vehicle.isCabinLightOn() == true
        if (cabinLight != cabinLightNow) {
            cabinLightNow = cabinLight
            renderDecor()
        }
    }

    private fun applyNight() {
        val to = if (nightNow) 1f else 0f
        b.homeShade.animate().alpha(to).setDuration(AMBIENCE_FADE_MS).start()
        b.nightDim.animate().alpha(to).setDuration(AMBIENCE_FADE_MS).start()
        // 캐릭터·소품도 방과 함께 천천히 어두워집니다 (켜진 스탠드만 밝게 남음).
        nightAnimator?.cancel()
        nightAnimator = android.animation.ValueAnimator.ofFloat(nightAmount, to).apply {
            duration = AMBIENCE_FADE_MS
            addUpdateListener { setNightAmount(it.animatedValue as Float) }
            start()
        }
        b.homeTitle.setTextColor(Color.parseColor(if (nightNow) "#F6E9D4" else "#775A49"))
        renderDecor()
    }

    /** 밤 조명 정도 (0 = 낮, 1 = 밤) */
    private var nightAmount = 0f
    private var nightAnimator: android.animation.ValueAnimator? = null
    /** 밤에 어두워지는 소품 그림들과 스탠드 불빛을 받는 정도(0~1). 켜진 스탠드 자체는 빠짐 */
    private val decorTintTargets = mutableListOf<Pair<ImageView, Float>>()
    /** 캐릭터 뒤 소품 찾기용: 그려진 순서(뒤 → 앞)대로 소품 그림·옮기기 손잡이 */
    private val decorHitTargets = mutableListOf<Triple<ImageView, Bitmap, DecorDrag>>()
    /** 캐릭터가 스탠드 불빛을 받는 정도 (0 = 안 받음, 1 = 바로 옆) */
    private var characterLight = 0f

    private fun nightFilter(light: Float = 0f): android.graphics.ColorFilter? =
        if (nightAmount <= 0f) null
        else android.graphics.PorterDuffColorFilter(
            NightLight.tint(nightAmount, light), android.graphics.PorterDuff.Mode.MULTIPLY,
        )

    private fun setNightAmount(v: Float) {
        nightAmount = v
        homeArt?.tint = nightFilter(characterLight)
        decorTintTargets.forEach { (img, light) -> img.colorFilter = nightFilter(light) }
    }

    /** 방 테마별 창문 마스크 (그림 이름 → 마스크·유리 범위). 없는 방은 null 로 기억 */
    private val windowMasks = HashMap<String, Pair<Bitmap, FloatArray>?>()

    /**
     * 창문 마스크 그림 읽기: 방 그림 이름 + "_window" (예: room_seoul_window.png).
     * 화면용으로 줄여 읽고, 유리 부분의 범위를 한 번만 계산해 둡니다.
     */
    private fun windowMask(asset: String): Pair<Bitmap, FloatArray>? = windowMasks.getOrPut(asset) {
        val id = Assets.firstResId(this, asset + WINDOW_MASK_SUFFIX)
        if (id == 0) return@getOrPut null
        val bmp = Assets.bitmap(this, id, WINDOW_MASK_PX) ?: return@getOrPut null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        WindowMask.bounds(px, bmp.width, bmp.height)?.let { bmp to it }
    }

    /**
     * 지금 방 그림 속 창문이 화면 어디에 있는지.
     * 방 그림은 화면을 꽉 채우도록 가운데 기준으로 잘려 보이므로(centerCrop) 같은 계산으로 옮깁니다.
     * 마스크 그림이 있으면 그 모양 그대로, 없으면 Room.kt 에 적힌 창문 범위(네모·둥근·동그란)를 씁니다.
     */
    private fun windowOnScreen(): RainView.Area? {
        val theme = RoomRules.theme(state)
        val d = b.homeRoom.drawable ?: return null
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        val vw = b.homeRoom.width.toFloat()
        val vh = b.homeRoom.height.toFloat()
        if (iw <= 0f || ih <= 0f || vw <= 0f || vh <= 0f) return null
        val s = maxOf(vw / iw, vh / ih)
        val dx = (vw - iw * s) / 2f
        val dy = (vh - ih * s) / 2f
        fun onScreen(l: Float, t: Float, r: Float, bottom: Float) =
            RectF(dx + l * iw * s, dy + t * ih * s, dx + r * iw * s, dy + bottom * ih * s)

        windowMask(theme.asset)?.let { (mask, box) ->
            return RainView.Area(
                rect = onScreen(box[0], box[1], box[2], box[3]),
                shape = WindowArea.Shape.RECT,
                mask = mask,
                imageRect = onScreen(0f, 0f, 1f, 1f),
            )
        }
        val win = theme.window ?: return null
        return RainView.Area(onScreen(win.left, win.top, win.right, win.bottom), win.shape)
    }

    /** 오른쪽 위 '밤' 버튼: 지금 보이는 상태의 반대로 */
    private fun toggleNight() {
        if (drivingLock == true) return
        nightChoice = !nightNow
        updateAmbience(lastVehicleNight, lastVehicleRain)
    }

    /** 오른쪽 위 '비' 버튼: 지금 보이는 상태의 반대로 */
    private fun toggleRain() {
        if (drivingLock == true) return
        rainChoice = !rainNow
        updateAmbience(lastVehicleNight, lastVehicleRain)
    }

    private fun refreshAmbienceButtons() {
        fun style(view: TextView, on: Boolean, icon: String, name: String) {
            view.text = "$icon $name ${if (on) "켜짐" else "꺼짐"}"
            view.background = ContextCompat.getDrawable(
                this, if (on) R.drawable.mcc_primary_button else R.drawable.mcc_tile,
            )
            view.setTextColor(if (on) Color.WHITE else Color.parseColor("#6E5342"))
            view.contentDescription = "$name 연출 ${if (on) "켜짐" else "꺼짐"}. 누르면 ${if (on) "끄기" else "켜기"}"
        }
        style(b.homeNightToggle, nightNow, "🌙", "밤")
        style(b.homeRainToggle, rainNow, "☔", "비")
    }

    private fun refreshLock() {
        setDrivingLock(uxRestricted || gearDriving)
        if (drivingLock == true) {
            updateLockNotice()
        } else {
            heldBubble?.let { heldBubble = null; showBubble(it) }
        }
    }

    /** 방의 스탠드가 켜져 있어야 하는지: 밤이거나 차량 실내등이 켜져 있을 때 */
    private fun lampLit(): Boolean = nightNow || cabinLightNow

    /**
     * 주행 종료 판정. 기어 P 가 읽히면 그것이 종료이고, 기어를 못 읽는 기기에서는
     * 속도 0이 60초 이어지면 종료로 봅니다.
     */
    private fun isTripOver(speedKph: Float?): Boolean {
        if (vehicle.isInPark() == true) return true
        if (speedKph == null) return false
        zeroSpeedTicks = if (speedKph <= MOVING_SPEED_KPH) zeroSpeedTicks + 1 else 0
        return zeroSpeedTicks >= STOP_CONFIRM_TICKS
    }

    /**
     * 주행 규제 중에는 본문·버튼·대화상자를 모두 닫고 안내 한 줄만 보여줍니다.
     * (Pleos: DO 선언 앱은 CarUxRestrictions 를 받아 기능을 스스로 제한해야 함.
     *  게임 요소처럼 운전자 시선을 끄는 화면은 주행 중 보여주지 않음)
     */
    private fun setDrivingLock(locked: Boolean) {
        if (drivingLock == locked) return
        drivingLock = locked
        if (locked) {
            openDialogs.toList().forEach { it.dismiss() }
            hideBubble.run()
        }
        b.detailGroup.visibility = View.GONE
        b.legacyPanel.visibility = View.GONE
        b.homeMenuPanel.visibility = if (locked) View.GONE else View.VISIBLE
        updateHomeMenu()
        // 캐릭터는 주행 중에도 '함께 달리는' 모습으로 남지만, 누를 수 없고 움직이지도 않습니다.
        // (Pleos 가이드: 주행 중 애니메이션·움직이는 이미지 최소화) 창밖 비도 멈춥니다.
        b.homeCharacter.visibility = View.VISIBLE
        homeArt?.still = locked
        rainView?.raining = rainNow && !locked
        // 주행 중에는 아래 안내 줄이 발을 가리지 않게 바닥을 그만큼 올립니다.
        b.homeCharacter.setPadding(0, 0, 0, if (locked) dp(DRIVE_NOTICE_SPACE_DP) else 0)
        b.homeBack.visibility = View.GONE
        b.homeTitle.visibility = if (locked) View.GONE else View.VISIBLE
        b.homeAmbienceBar.visibility = if (locked) View.GONE else View.VISIBLE
        updatePermissionBar()
        b.homeProgress.visibility = if (locked) View.GONE else if (state.egg != null) View.VISIBLE else View.GONE
        b.homeProgressLabel.visibility = b.homeProgress.visibility
        // 주행이 시작되면 깨던 알은 처음부터 (주행 중에는 누를 수 없음)
        if (locked) crackTaps = 0
        b.txtDrivingNotice.visibility = if (locked) View.VISIBLE else View.GONE
        updateLockNotice()
    }

    /** 시연 빌드에서는 왜 잠겼는지 한 줄 더 보여 줍니다 (에뮬레이터 기어 확인용). */
    private fun updateLockNotice() {
        val base = getString(R.string.driving_notice)
        b.txtDrivingNotice.text =
            if (BuildConfig.SHOW_DEMO_TOOLS && lockReason.isNotEmpty()) "$base\n($lockReason · 기어를 P 로)" else base
    }

    /** 대화상자를 열면서 목록에 올려 둡니다. 주행 규제 중에는 열지 않습니다. */
    private fun AlertDialog.Builder.showTracked(): AlertDialog {
        val dialog = create()
        if (drivingLock == true) return dialog
        dialog.setOnDismissListener { openDialogs.remove(dialog) }
        openDialogs += dialog
        dialog.show()
        enlargeDialogText(dialog)
        return dialog
    }

    /**
     * 대화상자 기본 글자(본문 16sp·버튼 14sp)는 차량 화면 기준(보조 3mm ≈ 19sp)보다 작아서 키웁니다.
     * 목록형 대화상자(setItems)의 항목도 함께 키웁니다.
     */
    private fun enlargeDialogText(dialog: AlertDialog) {
        dialog.findViewById<TextView>(android.R.id.message)?.textSize = TEXT_BODY
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.textSize = TEXT_TITLE
        listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL).forEach {
            dialog.getButton(it)?.textSize = TEXT_BODY
        }
        dialog.listView?.let { list ->
            list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    (child as? TextView)?.textSize = TEXT_BODY
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
        }
    }

    /**
     * 시연 도구(데모 주행·직접 운전 시뮬레이터·샘플 운전자·신호 점검)는
     * Pleos 심사 기준 '더미 데이터·임시 콘텐츠 금지'에 걸리므로 마켓 제출 빌드에서는 숨깁니다.
     * 켜고 끄는 곳: app/build.gradle.kts 의 SHOW_DEMO_TOOLS
     */
    private fun applyDemoToolsVisibility() {
        val show = if (BuildConfig.SHOW_DEMO_TOOLS) View.VISIBLE else View.GONE
        b.btnDemo.visibility = show
        b.btnDump.visibility = show
        b.txtSimHeader.visibility = show
        b.simContainer.visibility = show
    }

    /** 주행 1회가 끝났을 때 (실제든 데모든 같은 경로). */
    private fun finishDrive(s: DrivingSession, demoLabel: String?) {
        val tiersBefore = CollectionRules.tiers(state.collection)
        val roomBefore = RoomRules.unlocked(state).map { it.id }.toSet()
        val creaturesBefore = state.creatures.size
        val readyBefore = state.egg?.readyToHatch == true
        val events = GameRules.applyDrive(state, s, rng, System.currentTimeMillis(), newId)
        val newParts = CollectionRules.newlyUnlocked(tiersBefore, CollectionRules.tiers(state.collection))
        val newRoom = RoomRules.newlyUnlocked(roomBefore, state)
        store.save(state)
        selectedIds.retainAll { id -> state.find(id) != null }
        renderAll()
        // 말풍선은 가장 반가운 소식 하나만 (15자 이하).
        // 기어를 P 로 바꾼 바로 그 순간엔 아직 주행 잠금이 풀리기 전이라, 잠금이 풀리면 보여 줍니다.
        val afterText = when {
            !readyBefore && state.egg?.readyToHatch == true -> "알을 톡톡 눌러 줘!"
            state.creatures.size > creaturesBefore -> "알이 부화했어!"
            newParts.any { it.second >= 1 } -> "도감 ${"★".repeat(newParts.maxOf { it.second })} 이 열렸어!"
            newRoom.any { it.slot == RoomSlot.THEME } -> "새 방이 열렸어!"
            newRoom.isNotEmpty() -> "새 방 소품이 열렸어!"
            else -> Greetings.afterDrive(rng, s.distanceKm)
        }
        if (drivingLock == true) heldBubble = afterText else showBubble(afterText)
        b.txtSignals.text = buildString {
            if (demoLabel != null) appendLine("데모 주행 · $demoLabel")
            if (events.isNotEmpty()) {
                events.forEach { appendLine(it) }
                appendLine()
            }
            newParts.forEach { (e, t) ->
                appendLine("도감 " + if (t == 0) "등록 — ${e.group} ${e.label}" else "${"★".repeat(t)} 해금 — ${e.group} ${e.label}")
            }
            newRoom.forEach { appendLine("방 꾸미기 해금 — ${it.slot.label} ${it.label}") }
            if (newParts.isNotEmpty() || newRoom.isNotEmpty()) appendLine()
            appendLine("[ 이번 주행 ]")
            append(s.summary())
        }
    }

    // ═══════════════════════════════════════════════
    // 화면 그리기
    // ═══════════════════════════════════════════════

    private fun renderAll() {
        renderHome()
        renderEgg()
        renderSlots()
        renderBreed()
        renderOdds()
        if (b.bookSection.visibility == View.VISIBLE) renderBook()
        if (b.roomSection.visibility == View.VISIBLE) renderRoomSection()
    }

    /** 화면을 끌 때는 캐릭터가 있는 메인으로 되돌아갑니다. */
    private fun returnToHome() {
        if (drivingLock == true) return
        b.legacyPanel.visibility = View.GONE
        b.detailGroup.visibility = View.GONE
        b.homeMenuPanel.visibility = View.VISIBLE
        updateHomeMenu()
        b.homeBack.visibility = View.GONE
    }

    /**
     * 뒤로 가기 (화면 버튼·차량 하드키·CCP Back 모두 같은 이벤트).
     * Pleos CCP 가이드: 첫 화면에서는 앱을 닫지(finish) 말고 moveTaskToBack(true) 로 숨겨야 합니다.
     * 하위 화면에서는 기존처럼 홈으로 돌아갑니다.
     */
    @Deprecated("Handled for compatibility with the vehicle's system Back button")
    override fun onBackPressed() {
        if (b.legacyPanel.visibility == View.VISIBLE) returnToHome()
        else moveTaskToBack(true)
    }

    /** 메인 메뉴는 펼침/접힘 상태를 유지하고, 동작 중에는 캐릭터 화면의 공간을 부드럽게 조절합니다. */
    private fun updateHomeMenu() {
        menuAnimator?.cancel()
        menuAnimator = null
        val controls = b.homeControls
        controls.layoutParams = controls.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        controls.visibility = if (homeMenuExpanded) View.VISIBLE else View.GONE
        b.homeMenuToggle.text = if (homeMenuExpanded) "⌄  아래로 쓸어 숨기기" else "⌃  위로 쓸어 열기"
        b.homeMenuToggle.contentDescription = if (homeMenuExpanded) "메인 메뉴 펼쳐짐. 아래로 쓸어 접기" else "메인 메뉴 접힘. 위로 쓸어 펼치기"
    }

    /** 접을 때는 0 높이까지, 펼칠 때는 내용 크기까지 애니메이션합니다. */
    private fun setHomeMenuExpanded(expanded: Boolean) {
        if (homeMenuExpanded == expanded) return
        homeMenuExpanded = expanded
        menuAnimator?.cancel()
        menuAnimator = null
        val controls = b.homeControls
        val startHeight = if (controls.visibility == View.VISIBLE) controls.height else 0
        b.homeMenuToggle.text = if (expanded) "⌄  아래로 쓸어 숨기기" else "⌃  위로 쓸어 열기"
        b.homeMenuToggle.contentDescription = if (expanded) "메인 메뉴 펼쳐짐. 아래로 쓸어 접기" else "메인 메뉴 접힘. 위로 쓸어 펼치기"

        // 화면이 아직 측정되지 않았으면 애니메이션 없이 안전하게 상태만 반영합니다.
        if (b.homeMenuPanel.width == 0) {
            updateHomeMenu()
            return
        }
        val targetHeight = if (expanded) {
            controls.visibility = View.VISIBLE
            controls.measure(
                View.MeasureSpec.makeMeasureSpec(b.homeMenuPanel.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            controls.measuredHeight
        } else 0
        if (startHeight == targetHeight) {
            updateHomeMenu()
            return
        }
        val anim = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                controls.layoutParams = controls.layoutParams.apply {
                    height = value.animatedValue as Int
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (menuAnimator !== animation) return
                    controls.layoutParams = controls.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    controls.visibility = if (expanded) View.VISIBLE else View.GONE
                    menuAnimator = null
                }
            })
        }
        menuAnimator = anim
        anim.start()
    }

    /** 손잡이 또는 메뉴 타일에서 시작한 세로 스와이프만 인식합니다. 타일의 일반 탭은 그대로 유지합니다. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!::b.isInitialized || drivingLock == true ||
            b.legacyPanel.visibility == View.VISIBLE || b.homeMenuPanel.visibility != View.VISIBLE
        ) {
            menuSwipeTracking = false
            menuSwipeConsuming = false
            return super.dispatchTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2)
                b.homeMenuPanel.getLocationOnScreen(location)
                menuSwipeTracking = ev.rawX >= location[0] &&
                    ev.rawX < location[0] + b.homeMenuPanel.width &&
                    ev.rawY >= location[1] &&
                    ev.rawY < location[1] + b.homeMenuPanel.height
                menuSwipeConsuming = false
                menuSwipeStartX = ev.rawX
                menuSwipeStartY = ev.rawY
            }
            MotionEvent.ACTION_MOVE -> if (menuSwipeTracking) {
                val dx = ev.rawX - menuSwipeStartX
                val dy = ev.rawY - menuSwipeStartY
                val allowedDirection = if (homeMenuExpanded) dy > 0 else dy < 0
                if (!menuSwipeConsuming && allowedDirection &&
                    abs(dy) > menuTouchSlop * 2 && abs(dy) > abs(dx) * 1.25f
                ) {
                    // 타일에 ACTION_CANCEL을 보내 스와이프가 클릭으로 이어지지 않게 합니다.
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    menuSwipeConsuming = true
                }
                if (menuSwipeConsuming) return true
            }
            MotionEvent.ACTION_UP -> {
                if (menuSwipeConsuming) {
                    val dy = ev.rawY - menuSwipeStartY
                    val allowedDirection = if (homeMenuExpanded) dy > 0 else dy < 0
                    if (allowedDirection && abs(dy) >= dp(42)) {
                        setHomeMenuExpanded(!homeMenuExpanded)
                    }
                    menuSwipeTracking = false
                    menuSwipeConsuming = false
                    return true
                }
                menuSwipeTracking = false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                menuSwipeTracking = false
                if (menuSwipeConsuming) {
                    menuSwipeConsuming = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupHome() {
        updateHomeMenu()
        // 접근성·마우스 조작을 위한 클릭 대안도 유지합니다.
        b.homeMenuToggle.setOnClickListener {
            if (drivingLock != true) setHomeMenuExpanded(!homeMenuExpanded)
        }
        b.detailGroup.visibility = View.GONE
        val destinations = listOf(
            b.homeCollection to Section.COLLECTION,
            b.homeEgg to Section.EGG,
            b.homeGenetics to Section.GENETICS,
            b.homeBook to Section.BOOK,
            b.homeRoomDeco to Section.ROOM,
            b.homeTest to Section.TEST,
            b.homeOdds to Section.ODDS,
            b.homeSettings to Section.SETTINGS,
        )
        destinations.forEach { (tile, screen) ->
            tile.setOnClickListener { openSection(screen) }
        }
        b.homeBack.setOnClickListener { returnToHome() }
    }

    private fun openSection(section: Section) {
        if (drivingLock == true) return
        b.collectionSection.visibility = if (section == Section.COLLECTION) View.VISIBLE else View.GONE
        b.eggSection.visibility = if (section == Section.EGG) View.VISIBLE else View.GONE
        b.geneticsSection.visibility = if (section == Section.GENETICS) View.VISIBLE else View.GONE
        b.testSection.visibility = if (section == Section.TEST) View.VISIBLE else View.GONE
        b.oddsSection.visibility = if (section == Section.ODDS) View.VISIBLE else View.GONE
        b.settingsSection.visibility = if (section == Section.SETTINGS) View.VISIBLE else View.GONE
        if (section == Section.SETTINGS) renderPermissionRows()
        b.bookSection.visibility = if (section == Section.BOOK) View.VISIBLE else View.GONE
        b.roomSection.visibility = if (section == Section.ROOM) View.VISIBLE else View.GONE
        if (section == Section.BOOK) renderBook()
        if (section == Section.ROOM) renderRoomSection()
        b.screenTitle.text = section.title
        b.screenSubtitle.text = section.subtitle
        b.detailGroup.visibility = View.VISIBLE
        b.legacyPanel.visibility = View.VISIBLE
        b.homeMenuPanel.visibility = View.GONE
        b.homeBack.visibility = View.VISIBLE
        b.legacyPanel.smoothScrollTo(0, 0)
    }

    private fun renderHome() {
        b.homeCharacter.removeCallbacks(hideBubble)
        b.homeCharacter.removeAllViews()
        renderRoomTheme()
        val active = state.active
        // 30km 를 채운 알은 대표 캐릭터 대신 홈 한가운데에 나와 '깨워 달라'고 흔들립니다.
        val hatchReady = state.egg?.readyToHatch == true
        val names = if (hatchReady) listOf("egg")
            else active?.genome?.layerOrder() ?: if (state.egg != null) listOf("egg") else emptyList()
        val artSize = artSize()
        // 캐릭터는 화면 아래쪽 '바닥'에 세웁니다 (방 그림의 바닥은 아래 30~40% 에 있음).
        // floor = 이 영역 아래 끝에서 발바닥까지의 거리
        val floor = dp(FLOOR_DP)
        val charBottom = floor - (artSize * (1f - FEET_Y)).roundToInt()
        // 방 소품: 방 화면 전체 기준 자유 배치 (캐릭터 뒤 레이어)
        renderDecor(artSize)
        val art = CharacterView(this).apply {
            when {
                hatchReady -> setAssets(names, artSize, CharacterView.Motion.WOBBLE,
                    amplitude = READY_EGG_WOBBLE_DEG, periodMs = READY_EGG_WOBBLE_MS)
                else -> setAssets(
                    names, artSize,
                    if (active == null) CharacterView.Motion.WOBBLE else CharacterView.Motion.BREATHE,
                    tiers = active?.let { tiersFor(it) } ?: emptyMap(),
                )
            }
        }
        homeArt = art
        art.still = drivingLock == true
        art.tint = nightFilter(characterLight)
        // 캐릭터(알)를 누르면 콩 뛰고 말풍선 (기어 P 에서만).
        // 그림이 실제로 그려진 곳만 누름으로 칩니다. 주변 투명한 곳·배경을 누르면 반응하지 않고,
        // 그 누름은 뒤에 있는 방 소품이 받습니다.
        art.contentDescription = if (hatchReady) "부화 준비된 알. 눌러서 깨우기" else "대표 캐릭터. 누르면 인사"
        if (hatchReady) art.crackLevel = crackTaps
        art.setOnClickListener {
            if (drivingLock == true) return@setOnClickListener
            if (hatchReady) {
                onEggTap(art)
                return@setOnClickListener
            }
            art.poke()
            showBubble(Greetings.tap(rng))
        }
        // 캐릭터 뒤에 소품이 겹쳐 있으면: 짧게 누르면 캐릭터, 길게 누르면 그 소품을 꺼내 옮깁니다.
        // (소품은 캐릭터보다 뒤에 그려지지만, 옮기기는 소품이 먼저)
        var behind: DecorDrag? = null
        art.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    behind = null
                    if (drivingLock == true || !art.hits(e.x, e.y, dp(CHARACTER_TOUCH_PAD_DP).toFloat())) {
                        false   // 캐릭터가 아니면 뒤의 소품·배경이 받습니다.
                    } else {
                        behind = decorUnder(e.rawX, e.rawY)
                        behind?.follow(e)
                        true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val decor = behind
                    behind = null
                    val lifted = decor?.lifted == true
                    decor?.follow(e)
                    // 소품을 들어 올리지 않았고, 누른 자리에서 크게 벗어나지 않고 뗐을 때만 캐릭터 누름
                    if (!lifted && e.x >= 0 && e.y >= 0 && e.x < v.width && e.y < v.height) v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    behind?.follow(e)
                    behind = null
                    true
                }
                else -> {
                    behind?.follow(e)
                    true
                }
            }
        }
        b.homeCharacter.addView(art, FrameLayout.LayoutParams(artSize, artSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = charBottom })
        // 말풍선: 캐릭터 머리 위
        bubble = TextView(this).apply {
            textSize = TEXT_BUBBLE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#6E5342"))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.mcc_bubble)
            setPadding(dp(26), dp(12), dp(26), dp(12))
            visibility = View.GONE
        }
        b.homeCharacter.addView(bubble, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = charBottom + (artSize * 0.97f).roundToInt() })
        b.homeTitle.text = when {
            hatchReady -> "알이 깨어나려 해요!"
            active == null -> "알을 품는 중"
            else -> "나의 MCC"
        }
        b.homeProgress.progress = ((state.egg?.progress ?: 0f) * 1000).roundToInt()
        b.homeProgress.visibility = if (state.egg == null || drivingLock == true) View.GONE else View.VISIBLE
        b.homeProgressLabel.text = state.egg?.let { egg ->
            Texts.eggProgress(egg.km) +
                if (hatchReady && state.creatures.size >= GameRules.MAX_SLOTS) "\n자리가 가득 찼어요 · 하나를 놓아줘야 깨어나요" else ""
        } ?: ""
        b.homeProgressLabel.visibility = b.homeProgress.visibility
    }

    /** 메인 캐릭터 크기 (px). 방 소품 크기도 이 값을 따라갑니다. */
    private fun artSize(): Int =
        minOf(dp(ART_MAX_DP), (resources.displayMetrics.heightPixels * ART_HEIGHT_RATIO).roundToInt())

    /**
     * 방에 꺼내 둔 소품들 (최대 4개).
     * 저장된 위치(방 화면 비율)에 소품의 '아래 가운데'를 맞춰 세우고, 아래쪽에 있는 소품이 앞에 그려지게 합니다.
     */
    private fun renderDecor(artSize: Int = artSize(), retry: Boolean = true) {
        val layer = b.homeDecor
        layer.removeAllViews()
        decorTintTargets.clear()
        decorHitTargets.clear()
        val w = layer.width
        val h = layer.height
        if (w == 0 || h == 0) {
            // 화면 배치가 끝나기 전이면 한 번만 다시 시도합니다.
            if (retry) layer.post { renderDecor(artSize, retry = false) }
            return
        }
        val placed = RoomRules.placed(state).sortedBy { it.second.y }
        val lit = lampLit()

        // 켜진 스탠드: 불빛 중심(갓 높이)과 빛이 닿는 반경. 주변 캐릭터·소품이 덜 어두워지고 따뜻해집니다.
        var light: Triple<Float, Float, Float>? = null   // x, y, 반경 (px, 소품 층 기준)
        placed.firstOrNull { it.first.light }?.takeIf { lit }?.let { (item, p) ->
            val ih = artSize * item.height
            light = Triple(p.x * w, p.y * h - ih * LAMP_SHADE_Y, ih * LAMP_LIGHT_RADIUS)
        }
        light?.let { (lx, ly, r) ->
            // 빛 웅덩이는 소품보다 먼저(뒤에) 깔아 벽·바닥이 밝아 보이게 합니다.
            val size = (r * 2).roundToInt()
            val glow = View(this).apply {
                background = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = r
                    colors = intArrayOf(
                        Color.argb(LAMP_GLOW_CORE_ALPHA, 255, 222, 150),
                        Color.argb(LAMP_GLOW_MID_ALPHA, 255, 206, 120),
                        Color.argb(0, 255, 206, 120),
                    )
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            layer.addView(glow, FrameLayout.LayoutParams(size, size).apply {
                leftMargin = (lx - r).roundToInt()
                topMargin = (ly - r).roundToInt()
            })
        }
        fun lightAt(x: Float, y: Float): Float = light?.let { (lx, ly, r) ->
            NightLight.falloff(kotlin.math.hypot(x - lx, y - ly), r)
        } ?: 0f

        placed.forEach { (item, p) ->
            // 불이 켜지는 소품은 '켜진 그림'(이름 + _on, 예: room_item_lamp_on.png)이 있으면 그걸로 바꿉니다.
            val litId = if (lit && item.light) Assets.firstResId(this, item.asset + LIT_SUFFIX) else 0
            val resId = litId.takeIf { it != 0 } ?: Assets.firstResId(this, item.asset)
            val bmp = resId.takeIf { it != 0 }
                ?.let { Assets.bitmap(this, it, dp(DECOR_LOAD_DP)) } ?: return@forEach
            val ih = (artSize * item.height).roundToInt()
            val iw = (ih * bmp.width / bmp.height.toFloat()).roundToInt()
            val img = ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "${item.label}. 길게 눌러 옮기기"
            }
            // 밤에는 소품도 어두워지되, 스탠드 가까이 있을수록 덜 어둡고 따뜻하게. 켜진 스탠드 자체는 원래 밝기.
            if (!(lit && item.light)) {
                val l = lightAt(p.x * w, p.y * h - ih / 2f)
                img.colorFilter = nightFilter(l)
                decorTintTargets += img to l
            }
            layer.addView(img, FrameLayout.LayoutParams(iw, ih).apply {
                leftMargin = (p.x * w - iw / 2f).roundToInt()
                topMargin = (p.y * h - ih).roundToInt()
            })
            val drag = DecorDrag(item.id, img)
            img.setOnTouchListener(drag)
            decorHitTargets += Triple(img, bmp, drag)
        }

        // 캐릭터가 받는 불빛: 캐릭터 그림 가운데와 스탠드 사이 거리로 계산합니다.
        characterLight = homeArt?.let { art ->
            val a = IntArray(2).also { art.getLocationInWindow(it) }
            val d = IntArray(2).also { layer.getLocationInWindow(it) }
            lightAt(a[0] - d[0] + art.width / 2f, a[1] - d[1] + art.height / 2f)
        } ?: 0f
        homeArt?.tint = nightFilter(characterLight)
    }

    /** 화면 좌표 (rawX, rawY) 에 그려진 소품 중 가장 앞의 것. 소품의 투명한 곳은 제외합니다. */
    private fun decorUnder(rawX: Float, rawY: Float): DecorDrag? {
        val pad = dp(DECOR_TOUCH_PAD_DP).toFloat()
        val loc = IntArray(2)
        for ((img, bmp, drag) in decorHitTargets.asReversed()) {
            img.getLocationOnScreen(loc)
            val hit = HitTest.solidNear(img.width, img.height, bmp.width, bmp.height,
                rawX - loc[0], rawY - loc[1], pad) { x, y -> bmp.getPixel(x, y) ushr 24 }
            if (hit) return drag
        }
        return null
    }

    /**
     * 소품 옮기기: 길게 누르면(0.35초) 살짝 커지며 들리고, 끌어서 원하는 곳에 놓으면 그 자리에 고정됩니다.
     * 짧게 누르거나 바로 끌면 아무 일도 없습니다 (실수로 옮겨지지 않게). 주행 중에는 반응하지 않습니다.
     */
    private inner class DecorDrag(private val id: String, private val view: View) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        /** 캐릭터 너머로 전달받은 누름인지 (그때는 누르자마자 커지지 않음 — 캐릭터를 누른 것일 수도 있으므로) */
        private var quiet = false

        /** 길게 눌러 들어 올린 상태인지 */
        val lifted: Boolean get() = dragging

        /** 캐릭터를 누른 손가락을 이 소품도 함께 따라갑니다 (길게 누르면 소품이 들림). */
        fun follow(e: MotionEvent) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) quiet = true
            onTouch(view, e)
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) quiet = false
        }

        private val lift = Runnable {
            dragging = true
            view.parent?.requestDisallowInterceptTouchEvent(true)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.animate().scaleX(DECOR_LIFT_SCALE).scaleY(DECOR_LIFT_SCALE).alpha(0.85f).setDuration(120).start()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (drivingLock == true) {
                view.removeCallbacks(lift)
                if (dragging) cancelDrag()
                return false
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                    // 소품을 잡은 손가락은 메뉴 쓸어 넘기기로 치지 않습니다.
                    menuSwipeTracking = false
                    // 잡혔다는 표시: 누르는 즉시 살짝 커짐 (캐릭터 너머로 누른 때는 들릴 때까지 그대로)
                    if (!quiet) view.animate().scaleX(DECOR_PRESS_SCALE).scaleY(DECOR_PRESS_SCALE).setDuration(80).start()
                    view.postDelayed(lift, DECOR_LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (dragging) {
                        view.translationX = dx
                        view.translationY = dy
                    } else if (abs(dx) > menuTouchSlop * 2 || abs(dy) > menuTouchSlop * 2) {
                        // 들리기 전에 크게 움직이면 취소 (손 떨림 정도는 봐줌)
                        view.removeCallbacks(lift)
                        release()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(lift)
                    if (dragging) drop() else release()
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(lift)
                    if (dragging) cancelDrag() else release()
                    dragging = false
                }
            }
            return true
        }

        private fun drop() {
            val layer = b.homeDecor
            val lp = view.layoutParams as FrameLayout.LayoutParams
            if (layer.width > 0 && layer.height > 0) {
                val centerX = lp.leftMargin + view.translationX + lp.width / 2f
                val bottomY = lp.topMargin + view.translationY + lp.height
                if (RoomRules.move(state, id, centerX / layer.width, bottomY / layer.height)) store.save(state)
            }
            // 놓은 자리로 다시 그립니다 (화면 밖이면 안쪽으로 맞추고, 앞뒤 순서도 다시 정함).
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100)
                .withEndAction { renderDecor() }.start()
        }

        /** 들리기 전에 손을 뗐을 때: 눌린 표시만 되돌림 */
        private fun release() {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }

        private fun cancelDrag() {
            dragging = false
            view.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(150).start()
        }
    }

    /**
     * 부화 준비된 알 누르기: 누를 때마다 콩 + 금이 늘고, [EggCrack.TAPS_TO_HATCH] 번째에 알이 터지며 부화합니다.
     */
    private fun onEggTap(art: CharacterView) {
        if (drivingLock == true || hatching) return
        crackTaps++
        art.poke()
        if (!EggCrack.burstsOn(crackTaps)) {
            art.crackLevel = crackTaps
            showBubble(if (crackTaps == 1) "톡! 금이 갔어" else "조금만 더!")
            return
        }
        hatching = true
        bubble?.visibility = View.GONE
        art.crackLevel = crackTaps
        art.animate().scaleX(HATCH_BURST_SCALE).scaleY(HATCH_BURST_SCALE).alpha(0f)
            .setDuration(HATCH_BURST_MS).withEndAction { hatchNow() }.start()
    }

    /** 알이 터진 뒤: 실제 부화 → 새 캐릭터가 톡 튀어나옴 */
    private fun hatchNow() {
        hatching = false
        crackTaps = 0
        if (isFinishing || isDestroyed) return
        val message = GameRules.tryHatch(state, rng, System.currentTimeMillis(), newId)
        store.save(state)
        renderAll()
        if (state.egg == null) {
            // 새 캐릭터가 바닥에서 톡 튀어나오는 연출 (캐릭터 영역 전체를 발밑 기준으로 키움)
            val box = b.homeCharacter
            box.pivotX = box.width / 2f
            box.pivotY = box.height.toFloat()
            box.scaleX = HATCH_POP_FROM; box.scaleY = HATCH_POP_FROM; box.alpha = 0f
            box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(HATCH_POP_MS)
                .setInterpolator(OvershootInterpolator(2f)).start()
            showBubble("알이 부화했어!")
            b.txtSignals.text = message ?: ""
        } else {
            showBubble("자리가 가득 찼어!")
            message?.let { AlertDialog.Builder(this).setMessage(it).setPositiveButton("확인", null).showTracked() }
        }
    }

    private fun renderEgg() {
        val egg = state.egg
        renderEggArt(egg)
        btnBasicEgg.visibility =
            if (egg == null && state.creatures.size < GameRules.MAX_SLOTS) View.VISIBLE else View.GONE
        if (egg == null) {
            b.txtEgg.text = "품고 있는 알이 없습니다.\n아래 '유전'에서 부모 둘을 골라 알을 만들 수 있어요."
            return
        }
        val goal = TraitEngine.HATCH_DISTANCE_KM
        b.txtEgg.text = buildString {
            if (egg.parents.size == 2) {
                appendLine("${egg.generation}세대 알 · 부모 ${Texts.genomeShort(egg.parents[0])} × ${Texts.genomeShort(egg.parents[1])}")
            } else {
                appendLine("시작 알")
            }
            appendLine(Texts.eggProgress(egg.km, goal))
            if (egg.readyToHatch && state.creatures.size >= GameRules.MAX_SLOTS) {
                appendLine("자리가 가득 찼어요. 하나를 놓아줘야 깨어나요.")
            }
            appendLine(Texts.regionLine(egg.regionKm, emptySet()))
            appendLine("품는 동안의 주행은 알에만 쌓여요.")
            appendLine("형질별 부화 확률은 메인 화면의 '확률표'에서 확인할 수 있어요.")
        }
    }

    /** 알 그림 (egg.png). 부화가 가까울수록 크게 흔들립니다. 그림이 없으면 칸을 숨깁니다. */
    private fun renderEggArt(egg: Egg?) {
        b.eggArt.removeAllViews()
        if (egg == null) {
            b.eggArt.visibility = View.GONE
            return
        }
        val size = dp(180)
        val art = CharacterView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            setAssets(
                names = listOf("egg"),
                sizePx = size,
                motion = CharacterView.Motion.WOBBLE,
                amplitude = 2f + 10f * egg.progress,      // 2° → 12°
                periodMs = 1600L,
            )
        }
        if (art.layerCount == 0) {
            b.eggArt.visibility = View.GONE
            return
        }
        b.eggArt.addView(art)
        b.eggArt.visibility = View.VISIBLE
    }

    /** 그림 담당자용: 이 캐릭터에 필요한 그림 중 아직 임시이거나 없는 파일 */
    private fun artStatus(art: CharacterView, all: List<String>): String = when {
        art.layerCount == 0 ->
            "그림 없음 · ${all.first()}.png"
        art.placeholder.isEmpty() && art.missing.isEmpty() ->
            "그림 ${all.size}장 모두 진짜 그림"
        else -> buildString {
            append("그림  진짜 ${art.real.size} · 임시 ${art.placeholder.size}")
            if (art.missing.isNotEmpty()) append(" · 없음 ${art.missing.size}")
            append("\n아직 필요한 파일  ")
            append((art.placeholder + art.missing).joinToString(", ") { "$it.png" })
        }
    }

    private fun renderSlots() {
        b.txtSlotsHeader.text = "보관 중 ${state.creatures.size} / ${GameRules.MAX_SLOTS}"
        b.slotContainer.removeAllViews()
        for (slot in 0 until GameRules.MAX_SLOTS) {
            b.slotContainer.addView(
                state.creatures.getOrNull(slot)?.let { creatureCard(it) }
                    ?: emptySlotCard(slot + 1)
            )
        }
    }

    private fun emptySlotCard(slotNumber: Int): View = TextView(this).apply {
        text = "%02d  ·  빈 자리\n새 친구가 여기 와요".format(slotNumber)
        textSize = TEXT_BODY
        setTextColor(Color.parseColor("#765F4F"))
        setLineSpacing(dp(5).toFloat(), 1f)
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.mcc_empty_slot)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
    }

    private fun creatureCard(c: Creature): View {
        val isActive = state.active?.id == c.id
        val selected = c.id in selectedIds
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity,
                if (isActive) R.drawable.mcc_active_card else R.drawable.mcc_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val layerNames = c.genome.layerOrder()
        val artSize = dp(136)
        val art = CharacterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply { marginEnd = dp(14) }
            setAssets(layerNames, artSize, tiers = tiersFor(c))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (art.layerCount > 0) top.addView(art)
        top.addView(TextView(this).apply {
            text = Texts.creatureCard(c, state)
            textSize = TEXT_BODY
            setTextColor(Color.parseColor("#6F5748"))
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(top)

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        firstRow.addView(rowButton(if (isActive) "대표 캐릭터 ✓" else "대표로 설정", enabled = !isActive) {
            GameRules.setActive(state, c.id)
            store.save(state)
            renderAll()
        })
        firstRow.addView(rowButton(if (selected) "부모 선택 ✓" else "부모로 선택") { toggleParent(c.id) })
        firstRow.addView(rowButton("파츠 꾸미기 ★") { chooseTierSlot(c) })
        secondRow.addView(rowButton("유전 코드") { showCode(c) })
        secondRow.addView(rowButton("놓아주기") { confirmRelease(c) })
        card.addView(firstRow)
        card.addView(secondRow)
        return card
    }

    private fun rowButton(label: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = TEXT_BODY
            isEnabled = enabled
            setAllCaps(false)
            backgroundTintList = null
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.mcc_tile)
            setTextColor(Color.parseColor("#785C49"))
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { if (drivingLock != true) onClick() }
        }

    private fun renderBreed() {
        val parents = chosenParents()
        val reason = GameRules.breedBlockReason(state, parents.size)
        b.btnBreed.isEnabled = reason == null
        b.btnPartner.text = partnerLabel?.let { "다른 운전자 해제 · $it" } ?: getString(R.string.btn_partner)

        val names = selectedIds.mapNotNull { id -> state.find(id)?.let { Texts.name(it, state) } } +
            listOfNotNull(partnerLabel)
        b.txtBreed.text = buildString {
            appendLine("부모: " + if (names.isEmpty()) "아직 없음" else names.joinToString("  ×  "))
            if (reason != null) {
                append(reason)
            } else {
                append("알을 만들 수 있어요. 예상은 '확률표'에서.")
            }
        }
    }

    /** 확률 정보는 확률표 화면에서만 노출합니다. */
    private fun renderOdds() {
        val egg = state.egg
        val parents = if (egg != null) egg.parents else chosenParents().takeIf { it.size == 2 } ?: emptyList()
        val profile = egg?.incubation ?: TraitEngine.Profile()
        val regions = egg?.regionKm ?: emptyMap()
        b.txtOdds.text = buildString {
            if (egg != null) {
                appendLine("품고 있는 ${egg.generation}세대 알의 예상 결과")
                appendLine("진행도 ${(egg.progress * 100).roundToInt()}%")
            } else if (parents.size == 2) {
                appendLine("선택한 부모 2명의 예상 결과")
                appendLine("아직 알을 품기 전이므로 운전 성향은 반영되지 않았어요.")
            } else {
                appendLine("기본 부화 확률")
                appendLine("보관함에서 부모 두 명을 선택하면 예상 확률을 확인할 수 있어요.")
            }
            appendLine()
            append(Texts.oddsBlock(profile, parents, regions))
            appendLine()
            appendLine("※ 실제 부화 결과는 확률에 따라 달라질 수 있어요.")
            append("▲운전: 주행 성향 보정  ·  ▲유전: 부모 형질 반영")
        }
    }

    private fun updateDemoLabel() {
        val next = DemoDrive.STYLES[demoIndex % DemoDrive.STYLES.size]
        b.btnDemo.text = "데모 주행 · ${next.label} (${next.region.label})"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    // ═══════════════════════════════════════════════
    // 말풍선 · 도감 단계 · 방 꾸미기
    // ═══════════════════════════════════════════════

    /** 시동 인사. 주행 중이면 하지 않습니다. */
    private fun greet() {
        if (drivingLock == true) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = if (vehicleConnected) vehicle.isNightMode() else null
        showBubble(Greetings.greet(rng, hour, night, state.egg))
    }

    /** 캐릭터 머리 위 말풍선 한 줄. 3초 뒤 사라지고, 주행 중에는 띄우지 않습니다. */
    private fun showBubble(text: String) {
        val view = bubble ?: return
        if (drivingLock == true || b.legacyPanel.visibility == View.VISIBLE) return
        view.text = text
        view.visibility = View.VISIBLE
        b.homeCharacter.removeCallbacks(hideBubble)
        b.homeCharacter.postDelayed(hideBubble, BUBBLE_MS)
    }

    /** 이 캐릭터에 입힌 도감 단계: 그림 이름 → 단계 */
    private fun tiersFor(c: Creature): Map<String, Int> = Slot.values().mapNotNull { slot ->
        val t = CollectionRules.equippedTier(state.collection, c, slot)
        val asset = c.genome.traits[slot]?.asset
        if (t > 0 && asset != null) asset to t else null
    }.toMap()

    private fun tierName(t: Int) = when (t) { 0 -> "기본"; 1 -> "★ 색 변형"; else -> "★★ 금빛" }

    /** 보관함 → 파츠 꾸미기: 슬롯 고르기 */
    private fun chooseTierSlot(c: Creature) {
        val slots = Slot.values().filter { c.genome.traits[it] != null }
        val labels = slots.map { slot ->
            val trait = c.genome.traits.getValue(slot)
            val open = CollectionRules.tier(state.collection, trait.asset)
            val now = CollectionRules.equippedTier(state.collection, c, slot)
            "${slot.label} · ${trait.label}   지금 ${tierName(now)}" +
                if (open >= 1) "   (${"★".repeat(open)} 열림)" else "   (★ 까지 ${CollectionRules.TIER_COUNTS[1] - CollectionRules.count(state.collection, trait.asset)}회)"
        }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this)
            .setTitle("${Texts.name(c, state)} 파츠 꾸미기")
            .setItems(labels) { _, which -> chooseTier(c, slots[which]) }
            .setNegativeButton("닫기", null)
            .showTracked()
    }

    /** 단계 고르기. 열린 단계만 보여 줍니다. */
    private fun chooseTier(c: Creature, slot: Slot) {
        val trait = c.genome.traits[slot] ?: return
        val open = CollectionRules.tier(state.collection, trait.asset).coerceAtLeast(0)
        val options = (0..open).toList()
        if (options.size == 1) {
            showMessage("아직 기본 그림만 있어요", "${CollectionRules.TIER_COUNTS[1]}번 모으면 ★, ${CollectionRules.TIER_COUNTS[2]}번 모으면 ★★\n지금 ${CollectionRules.count(state.collection, trait.asset)}번")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("${slot.label} · ${trait.label}")
            .setItems(options.map { tierName(it) }.toTypedArray<CharSequence>()) { _, which ->
                if (drivingLock == true) return@setItems
                if (CollectionRules.equip(state.collection, c, slot, options[which])) {
                    store.save(state)
                    renderAll()
                    showMessage("입혔어요", "${trait.label} → ${tierName(options[which])}")
                }
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("확인", null).showTracked()
    }

    /** 메인 배경 = 고른 방 테마 */
    private fun renderRoomTheme() {
        val theme = RoomRules.theme(state)
        val id = Assets.firstResId(this, theme.asset)
        if (id != 0) {
            val w = resources.displayMetrics.widthPixels.coerceAtLeast(dp(400))
            Assets.bitmap(this, id, w)?.let { b.homeRoom.setImageBitmap(it) }
        }
    }

    /** 도감 화면: 그룹별로 파츠 카드 (등록 전에는 흐린 '?') */
    private fun renderBook() {
        val c = state.collection
        val all = CollectionRules.ENTRIES
        val star1 = all.count { it.tiered && CollectionRules.tier(c, it.asset) >= 1 }
        val star2 = all.count { it.tiered && CollectionRules.tier(c, it.asset) >= 2 }
        b.txtBookSummary.text = "등록 ${CollectionRules.registered(c)} / ${all.size}   ·   ★ $star1 / 20   ·   ★★ $star2 / 20"
        b.bookContainer.removeAllViews()
        all.groupBy { it.group }.forEach { (group, entries) ->
            b.bookContainer.addView(sectionTitle(group))
            entries.chunked(BOOK_COLUMNS).forEach { rowEntries ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowEntries.forEach { row.addView(bookCard(it)) }
                repeat(BOOK_COLUMNS - rowEntries.size) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                }
                b.bookContainer.addView(row)
            }
        }
    }

    private fun bookCard(e: CollectionRules.Entry): View {
        val n = CollectionRules.count(state.collection, e.asset)
        val t = CollectionRules.tier(state.collection, e.asset)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(this@MainActivity,
                if (t >= 1 && e.tiered) R.drawable.mcc_active_card else R.drawable.mcc_card)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
        }
        val size = dp(104)
        val thumb: View = if (e.group == "지역") {
            ImageView(this).apply {
                Assets.firstResId(this@MainActivity, e.asset).takeIf { it != 0 }
                    ?.let { Assets.trimmed(this@MainActivity, it, dp(240)) }?.let { setImageBitmap(it) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
        } else {
            CharacterView(this).apply {
                val tiers = if (e.tiered && t >= 1) mapOf(e.asset to t) else emptyMap()
                setAssets(listOf("body_base", e.asset), size, tiers = tiers, amplitude = 0f)
            }
        }
        thumb.alpha = if (n > 0) 1f else 0.18f
        card.addView(thumb, LinearLayout.LayoutParams(size, size))
        card.addView(TextView(this).apply {
            text = if (n > 0) e.label else "???"
            textSize = TEXT_SMALL
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6E5342"))
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            val goal = CollectionRules.nextGoal(state.collection, e.asset)
            text = when {
                n == 0 -> "미발견"
                !e.tiered -> "${n}회"
                goal == null -> "${n}회 · ★★ 완성"
                else -> "${n}회 · ${"★".repeat(t.coerceAtLeast(0))}${"☆".repeat(2 - t.coerceAtLeast(0))} · 다음까지 ${goal - n}"
            }
            textSize = TEXT_SMALL
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#765F4F"))
        })
        return card
    }

    /**
     * 방 꾸미기 화면.
     *   · 방 테마: 누르면 바로 바뀜
     *   · 소품: 누르면 방에 꺼내고(최대 4개), 다시 누르면 넣음. 위치는 홈 화면에서 길게 눌러 끌어 옮김
     * 잠긴 것은 흐리게 + 여는 조건.
     */
    private fun renderRoomSection() {
        b.roomContainer.removeAllViews()
        b.roomContainer.addView(sectionTitle(RoomSlot.THEME.label))
        val themeNow = RoomRules.theme(state).id
        b.roomContainer.addView(roomRow(RoomCatalog.of(RoomSlot.THEME).map { roomCard(it, it.id == themeNow) }))

        val count = RoomRules.placed(state).size
        b.roomContainer.addView(sectionTitle("${RoomSlot.ITEM.label}  $count / ${RoomRules.MAX_PLACED}"))
        b.roomContainer.addView(TextView(this).apply {
            text = "누르면 꺼내고, 다시 누르면 넣어요.\n홈에서 길게 눌러 끌면 옮겨져요."
            textSize = TEXT_SMALL
            setTextColor(Color.parseColor("#765F4F"))
            setPadding(dp(4), 0, 0, dp(8))
        })
        b.roomContainer.addView(roomRow(RoomCatalog.of(RoomSlot.ITEM).map { roomCard(it, RoomRules.isPlaced(state, it.id)) }))
    }

    private fun roomRow(cards: List<View>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.forEach { row.addView(it) }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    /** 방 테마·소품 카드 하나. chosen = 지금 테마 / 방에 꺼낸 소품 */
    private fun roomCard(item: RoomItem, chosen: Boolean): View {
        val unlocked = RoomRules.isUnlocked(item, state)
        val isTheme = item.slot == RoomSlot.THEME
        val w = if (isTheme) dp(220) else dp(150)
        val h = dp(150)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(this@MainActivity,
                if (chosen) R.drawable.mcc_chip_on else R.drawable.mcc_card)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(w + dp(20), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            isClickable = true
            setOnClickListener {
                if (drivingLock == true) return@setOnClickListener
                if (!unlocked) {
                    showMessage("아직 잠겨 있어요", "${item.label} — ${item.unlock.hint}")
                    return@setOnClickListener
                }
                val changed = when {
                    isTheme -> RoomRules.selectTheme(state, item.id)
                    chosen -> RoomRules.remove(state, item.id)
                    else -> when (RoomRules.place(state, item.id)) {
                        RoomRules.PlaceResult.OK -> true
                        RoomRules.PlaceResult.FULL -> {
                            showMessage(
                                "소품은 ${RoomRules.MAX_PLACED}개까지 꺼낼 수 있어요",
                                "꺼내 둔 소품 하나를 눌러 넣은 뒤 다시 골라 주세요.",
                            )
                            false
                        }
                        else -> false
                    }
                }
                if (changed) {
                    store.save(state)
                    renderHome()
                    renderRoomSection()
                }
            }
        }
        val img = ImageView(this).apply {
            scaleType = if (isTheme) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            Assets.firstResId(this@MainActivity, item.asset).takeIf { it != 0 }
                ?.let { Assets.bitmap(this@MainActivity, it, w) }?.let { setImageBitmap(it) }
            alpha = if (unlocked) 1f else 0.3f
        }
        card.addView(img, LinearLayout.LayoutParams(w, h))
        card.addView(TextView(this).apply {
            text = when {
                !chosen -> item.label
                isTheme -> "✓ ${item.label}"
                else -> "✓ ${item.label} (꺼냄)"
            }
            textSize = TEXT_SMALL
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6E5342"))
            setTypeface(typeface, Typeface.BOLD)
        })
        if (!unlocked) card.addView(TextView(this).apply {
            text = "🔒 ${item.unlock.hint}"
            textSize = TEXT_SMALL
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#765F4F"))
        })
        return card
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = TEXT_TITLE
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#795D4C"))
        setPadding(dp(4), dp(18), dp(4), dp(8))
    }

    // ═══════════════════════════════════════════════
    // 유전
    // ═══════════════════════════════════════════════

    private fun chosenParents(): List<Genome> =
        selectedIds.mapNotNull { state.find(it)?.genome } + listOfNotNull(partner)

    private fun toggleParent(id: String) {
        if (id in selectedIds) {
            selectedIds.remove(id)
        } else {
            selectedIds += id
            // 다른 운전자를 넣었으면 내 캐릭터는 하나만. 아니면 둘까지. 넘치면 먼저 고른 쪽이 빠집니다.
            val maxOwn = if (partner != null) 1 else 2
            while (selectedIds.size > maxOwn) selectedIds.removeAt(0)
        }
        renderSlots()
        renderBreed()
        renderOdds()
    }

    private fun askPartnerCode() {
        val input = EditText(this).apply {
            // 실제로 쓰이는 코드를 예시로 보여 주면 그대로 넣어 쓸 수 있으므로 모양만 보여 줍니다.
            hint = "XXXXX-XXXXX (10자리)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            textSize = TEXT_INPUT
            setSingleLine(true)
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle("다른 운전자의 유전 코드")
            .setMessage(
                "상대 MCC의 '유전 코드' 10자리를 넣어요." +
                    if (BuildConfig.SHOW_DEMO_TOOLS) "\n코드가 없으면 '샘플 운전자'로 시연할 수 있습니다." else ""
            )
            .setView(input)
            .setPositiveButton("확인") { _, _ -> applyPartnerCode(input.text.toString()) }
            .setNegativeButton("취소", null)
            .apply {
                if (BuildConfig.SHOW_DEMO_TOOLS) setNeutralButton("샘플 운전자") { _, _ -> pickSamplePartner() }
            }
            .showTracked()
    }

    private fun applyPartnerCode(raw: String) {
        when (val r = GeneCode.decode(raw)) {
            is GeneCode.Result.Ok -> setPartner(r.genome, "다른 운전자의 ${Texts.genomeShort(r.genome)}")
            is GeneCode.Result.Error -> AlertDialog.Builder(this)
                .setTitle("코드를 읽지 못했습니다")
                .setMessage(r.message)
                .setPositiveButton("다시 입력") { _, _ -> askPartnerCode() }
                .setNegativeButton("닫기", null)
                .showTracked()
        }
    }

    private fun pickSamplePartner() {
        val labels = Samples.PARTNERS.map { p ->
            "${p.owner} — ${Texts.genomeShort(p.genome)}\n${Texts.accessoriesLine(p.genome.accessories)}   ${p.code}"
        }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this)
            .setTitle("샘플 운전자")
            .setItems(labels) { _, which ->
                val p = Samples.PARTNERS[which]
                setPartner(p.genome, "${p.owner}의 ${Texts.genomeShort(p.genome)}")
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    private fun setPartner(genome: Genome, label: String) {
        partner = genome
        partnerLabel = label
        while (selectedIds.size > 1) selectedIds.removeAt(0)
        renderSlots()
        renderBreed()
        renderOdds()
    }

    private fun clearPartner() {
        partner = null
        partnerLabel = null
        renderBreed()
        renderOdds()
    }

    private fun breed() {
        val parents = chosenParents()
        if (GameRules.breedBlockReason(state, parents.size) != null) {
            renderBreed()
            return
        }
        val egg = GameRules.breed(state, parents[0], parents[1])
        selectedIds.clear()
        partner = null
        partnerLabel = null
        store.save(state)
        renderAll()
        openSection(Section.EGG)
        b.txtSignals.text = "★ ${egg.generation}세대 알을 만들었습니다.\n" +
            "${TraitEngine.HATCH_DISTANCE_KM.roundToInt()}km 달리면 부화해요."
    }

    private fun takeBasicEgg() {
        val reason = GameRules.takeBasicEgg(state)
        if (reason != null) {
            b.txtSignals.text = reason
            return
        }
        store.save(state)
        renderAll()
        b.txtSignals.text = "기본 알을 받았습니다. 30km 를 달리면 부화합니다."
    }

    private fun showCode(c: Creature) {
        val code = GeneCode.encode(c.genome)
        val view = TextView(this).apply {
            text = code
            textSize = 34f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("${Texts.name(c, state)}의 유전 코드")
            .setView(view)
            .setMessage(
                "다른 운전자가 이 코드로 유전할 수 있어요.\n" +
                    "악세사리도 10% 확률로 전해져요."
            )
            .setPositiveButton("닫기", null)
            .showTracked()
    }

    private fun confirmRelease(c: Creature) {
        val title = "${Texts.name(c, state)}을(를) 놓아줄까요?"
        val code = GeneCode.encode(c.genome)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                "되돌릴 수 없어요. 코드를 적어 두면 다시 유전에 쓸 수 있어요.\n\n$code"
            )
            .setPositiveButton("놓아주기") { _, _ ->
                GameRules.release(state, c.id)
                selectedIds.remove(c.id)
                store.save(state)
                renderAll()
                b.txtSignals.text = "${c.genome.species.label}을(를) 놓아주었습니다." +
                    if (state.egg?.readyToHatch == true) "\n알이 깨어날 자리가 생겼어요. 홈에서 알을 눌러 주세요." else ""
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    // ═══════════════════════════════════════════════
    // 데모 · 초기화
    // ═══════════════════════════════════════════════

    /** 가상 주행 1회를 실제와 같은 경로(finishDrive)로 통과시킵니다. */
    private fun runDemoDrive() {
        if (demoBusy) return
        val style = DemoDrive.STYLES[demoIndex % DemoDrive.STYLES.size]
        demoIndex++
        demoBusy = true
        b.btnDemo.isEnabled = false
        b.btnDemo.text = "주행 만드는 중…"

        lifecycleScope.launch {
            try {
                val drive = withContext(Dispatchers.Default) { DemoDrive.generate(style) }
                finishDrive(drive, demoLabel = "${style.label} (${style.region.label})")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e   // 화면이 닫혀서 취소된 것. 오류 아님
            } catch (e: Exception) {
                Log.e(TAG, "데모 실패", e)
                saveCrash(e)
                b.txtSignals.text = "데모 중 오류\n\n" + Log.getStackTraceString(e).take(2000)
            } finally {
                demoBusy = false
                b.btnDemo.isEnabled = true
                updateDemoLabel()
            }
        }
    }

    /** 시연용 '모두 해금' 버튼 (테스트 메뉴, SHOW_DEMO_TOOLS 빌드에서만 보임) */
    private val btnUnlockAll by lazy {
        Button(this).apply {
            textSize = TEXT_BODY
            setAllCaps(false)
            setOnClickListener { if (store.hasDemoBackup()) confirmRestoreUnlock() else confirmUnlockAll() }
        }
    }

    private fun updateUnlockAllLabel() {
        btnUnlockAll.text = if (store.hasDemoBackup()) "해금 전으로 되돌리기" else "시연용 모두 해금 (도감·악세서리·방)"
    }

    private fun confirmUnlockAll() {
        AlertDialog.Builder(this)
            .setTitle("시연용 모두 해금")
            .setMessage("도감 ★★·지역 악세서리·방을 모두 열어요.\n지금 기록은 백업했다가 되돌릴 수 있어요.")
            .setPositiveButton("해금") { _, _ ->
                store.backupForDemo(state)
                DemoUnlock.unlockAll(state)
                store.save(state)
                updateUnlockAllLabel()
                renderAll()
                b.txtSignals.text = "모두 해금했어요. '해금 전으로 되돌리기'로 원래대로 돌아가요."
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    private fun confirmRestoreUnlock() {
        AlertDialog.Builder(this)
            .setTitle("해금 전으로 되돌리기")
            .setMessage("해금 전 기록으로 돌아가요.\n해금 뒤의 주행 기록은 사라져요.")
            .setPositiveButton("되돌리기") { _, _ ->
                store.restoreDemoBackup()
                state = store.load()
                GameRules.ensureStarterEgg(state)
                selectedIds.clear()
                updateUnlockAllLabel()
                renderAll()
                b.txtSignals.text = "해금 전 기록으로 되돌렸어요."
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("전체 초기화")
            .setMessage("캐릭터 ${state.creatures.size}마리·알·기록을 지워요.\n되돌릴 수 없어요.")
            .setPositiveButton("초기화") { _, _ ->
                store.reset()
                state = GameState()
                GameRules.ensureStarterEgg(state)
                store.save(state)
                selectedIds.clear()
                partner = null
                partnerLabel = null
                demoIndex = 0
                session = null
                updateDemoLabel()
                updateUnlockAllLabel()
                renderAll()
                b.txtSignals.text = "초기화했습니다. 시작 알을 받았습니다."
            }
            .setNegativeButton("취소", null)
            .showTracked()
    }

    // ═══════════════════════════════════════════════
    // 종료 원인 기록 — 다음 실행 때 화면에 보여줍니다
    // ═══════════════════════════════════════════════

    private fun crashPrefs() = getSharedPreferences("mcc_crash", MODE_PRIVATE)

    private fun saveCrash(e: Throwable) {
        try {
            crashPrefs().edit().putString("last", Log.getStackTraceString(e).take(4000)).commit()
        } catch (ignored: Exception) {
        }
    }

    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            saveCrash(e)
            previous?.uncaughtException(thread, e)
        }
    }

    private fun showLastCrashIfAny() {
        val last = crashPrefs().getString("last", null) ?: return
        b.txtSignals.text = "[ 지난번 종료 원인 — 이 화면을 캡처해 주세요 ]\n\n$last"
        crashPrefs().edit().remove("last").apply()
        // 메뉴 안쪽 글에만 쓰면 못 보고 지나치므로, 시연 빌드에서는 시작하자마자 창으로 띄웁니다.
        if (BuildConfig.SHOW_DEMO_TOOLS) {
            AlertDialog.Builder(this)
                .setTitle("지난번에 앱이 꺼진 이유")
                .setMessage(last.take(1500))
                .setPositiveButton("확인", null)
                .show()
        }
    }

    companion object {
        private const val TAG = "MCC"
        private const val PREFS_UI = "mcc_ui"
        /** 위치·에너지(선택 권한)를 한 번 물어봤는지 — 거부했으면 다시 조르지 않습니다 */
        private const val KEY_OPTIONAL_ASKED = "optional_permissions_asked"
        /** 속도 권한을 물어본 적이 있는지 — 두 번 거부 뒤 설정 안내로 바꾸는 데 씁니다 */
        private const val KEY_SPEED_ASKED = "speed_permission_asked"
        private const val SAMPLE_INTERVAL_MS = 500L
        private const val BACKGROUND_INTERVAL_MS = 2_000L
        /** 이 속도를 넘으면 움직이는 것으로 봅니다 (km/h). */
        private const val MOVING_SPEED_KPH = 2f
        /** 속도 0이 이만큼 연속되면 주행 종료 (500ms × 120 = 60초). */
        private const val STOP_CONFIRM_TICKS = 120
        /** 과속 경고 신호 연결 전 임시 기준 (km/h). */
        private const val OVER_SPEED_PROXY_KPH = 110f
        /** 캐릭터 그림(1024)에서 발바닥 높이 비율 — 방 소품 바닥을 여기에 맞춥니다. */
        private const val FEET_Y = 940f / 1024f
        private const val BUBBLE_MS = 3000L
        /** 캐릭터 영역 아래 끝 → 발바닥 거리 (dp) */
        private const val FLOOR_DP = 36
        /** 방 소품을 들어 올리기까지 누르고 있어야 하는 시간 (ms) */
        private const val DECOR_LONG_PRESS_MS = 250L

        // ── 글자 크기 (sp). Pleos 디자인 가이드: 중요 정보 5mm↑, 보조 3mm↑.
        //    1sp ≈ 0.159mm (1/160 인치) → 20sp ≈ 3.2mm, 32sp ≈ 5.1mm
        /** 가장 작은 글자 (카드 이름·잠금 조건 등). 3mm 이상 */
        private const val TEXT_SMALL = 20f
        /** 본문·버튼 */
        private const val TEXT_BODY = 22f
        /** 소제목 */
        private const val TEXT_TITLE = 26f
        /** 입력칸 */
        private const val TEXT_INPUT = 28f
        /** 캐릭터 말풍선 */
        private const val TEXT_BUBBLE = 28f
        /** 소품을 누르는 순간 커지는 배율 (잡혔다는 표시) */
        private const val DECOR_PRESS_SCALE = 1.05f
        /** 캐릭터 누름 판정에 더해 주는 손가락 여유 (dp) */
        private const val CHARACTER_TOUCH_PAD_DP = 10
        /** 부화 준비된 알: 좌우로 크게(도) 빠르게(ms) 흔들림 */
        private const val READY_EGG_WOBBLE_DEG = 7f
        private const val READY_EGG_WOBBLE_MS = 900L
        /** 마지막 누름에 알이 커지며 사라지는 시간·크기 */
        private const val HATCH_BURST_MS = 320L
        private const val HATCH_BURST_SCALE = 1.35f
        /** 새 캐릭터가 튀어나오는 시간·시작 크기 */
        private const val HATCH_POP_MS = 520L
        private const val HATCH_POP_FROM = 0.3f
        /** 캐릭터 뒤 소품을 찾을 때 손가락 여유 (dp) */
        private const val DECOR_TOUCH_PAD_DP = 6
        /** 들어 올린 소품 크기 배율 */
        private const val DECOR_LIFT_SCALE = 1.12f
        /** 밤·비 연출이 바뀔 때 서서히 바뀌는 시간 (ms) */
        private const val AMBIENCE_FADE_MS = 900L
        /** 스탠드 불빛 크기 (스탠드 높이 대비) */
        /** 스탠드 불빛 중심: 소품 바닥에서 키의 78% 위 (갓 높이) */
        private const val LAMP_SHADE_Y = 0.78f
        /** 불빛이 닿는 반경: 스탠드 키의 2.4배 */
        private const val LAMP_LIGHT_RADIUS = 2.4f
        /** 빛 웅덩이 진하기 (가운데 / 중간, 0~255) */
        private const val LAMP_GLOW_CORE_ALPHA = 150
        private const val LAMP_GLOW_MID_ALPHA = 60
        /** 불 켜진 소품 그림 이름 꼬리 (room_item_lamp → room_item_lamp_on) */
        private const val LIT_SUFFIX = "_on"
        /** 방 소품 그림을 읽을 크기 (dp). 화면에서 가장 큰 소품보다 조금 크게 */
        private const val DECOR_LOAD_DP = 320
        /** 창문 마스크 그림 이름 꼬리 (room_seoul → room_seoul_window) */
        private const val WINDOW_MASK_SUFFIX = "_window"
        /** 창문 마스크를 읽을 가로 크기 (px). 모양만 쓰므로 작게 읽습니다 */
        private const val WINDOW_MASK_PX = 640
        /** 메인 캐릭터 최대 크기 (dp). 크기를 바꾸려면 이 숫자와 아래 비율을 같이 바꾸세요. */
        private const val ART_MAX_DP = 465
        /** 메인 캐릭터 크기 = 화면 높이 × 이 비율 (최대 ART_MAX_DP) */
        private const val ART_HEIGHT_RATIO = 0.51f
        /** 주행 중 안내 줄 높이 + 여백 (dp) */
        private const val DRIVE_NOTICE_SPACE_DP = 96
        private const val GREETING_DELAY_MS = 900L
        private const val BOOK_COLUMNS = 5
    }
}
