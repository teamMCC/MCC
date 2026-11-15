package com.mcc.app.ui

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.view.View
import com.mcc.app.R
import com.mcc.app.region.Region
import com.mcc.app.vehicle.DrivingSession
import com.mcc.app.vehicle.SimDrive
import kotlin.math.roundToInt

/**
 * 직접 운전해 보는 시뮬레이터 화면.
 *
 * [주행 시작] → 목표 속도·운전 방식·야간·지역을 바꿔 가며 달림 → [주차하고 기록]
 * 주차하면 onFinish 로 주행 기록을 넘기고, 앱은 실제 주행과 같은 경로로 처리합니다.
 */
class SimPanel(
    context: Context,
    private val onFinish: (DrivingSession) -> Unit,
) : LinearLayout(context) {

    private val sim = SimDrive()
    private var scale = 10
    private var lastRealMs = 0L
    private var running = false

    private val status = TextView(context)
    private val btnStart = button("주행 시작 (기어 D)") { sim.start(); refresh() }
    private val btnPark = button("주차하고 기록 (기어 P)") { park() }
    private val speedButtons = TARGETS.map { kph ->
        kph to button(if (kph == 0) "정지" else "${kph}") { sim.targetKph = kph.toFloat(); refresh() }
    }
    private val btnStyle = button("") {
        sim.style = if (sim.style == SimDrive.Style.GENTLE) SimDrive.Style.HARSH else SimDrive.Style.GENTLE
        refresh()
    }
    private val btnNight = button("") { sim.night = !sim.night; refresh() }
    private val btnRegion = button("") {
        val all = Region.values()
        val i = all.indexOf(sim.region)
        sim.region = all[(i + 1) % all.size]
        refresh()
    }
    private val scaleButtons = SCALES.map { k -> k to button("×$k") { scale = k; refresh() } }

    init {
        orientation = VERTICAL
        background = context.getDrawable(R.drawable.mcc_panel)
        setPadding(dp(16), dp(16), dp(16), dp(16))

        status.textSize = 22f
        status.setTextColor(Color.parseColor("#745B4C"))
        status.setLineSpacing(dp(4).toFloat(), 1f)
        addView(status)

        addRow(label("기어"), btnStart, btnPark)
        addRow(label("목표 속도 km/h"), *speedButtons.map { it.second }.toTypedArray())
        addRow(label("운전·시간·지역"), btnStyle, btnNight, btnRegion)
        addRow(label("배속"), *scaleButtons.map { it.second }.toTypedArray())
        refresh()
    }

    private fun park() {
        val drive = sim.finish() ?: return
        refresh()
        onFinish(drive)
    }

    private fun refresh() {
        val km = "%.2f".format(sim.distanceKm)
        val min = sim.elapsedSec / 60
        val sec = sim.elapsedSec % 60
        status.text = if (sim.isDriving) {
            "주행 중 · ${sim.speedKph.roundToInt()} km/h → 목표 ${sim.targetKph.roundToInt()}\n" +
                "거리 $km km · 시간 ${min}분 ${sec}초 · 급가감속 ${sim.harshCount}회" +
                (if (sim.speedKph > SimDrive.OVER_SPEED_KPH) "  ⚠ 과속" else "")
        } else {
            "정차 · '주행 시작'을 누르고 목표 속도를 고르세요\n" +
                "알을 품고 있다면 30km 를 달린 뒤 '주차하고 기록'을 누르면 부화합니다"
        }
        btnStart.isEnabled = !sim.isDriving
        btnPark.isEnabled = sim.isDriving
        speedButtons.forEach { (kph, b) ->
            b.isEnabled = sim.isDriving
            b.text = (if (kph == 0) "정지" else "$kph") + if (sim.targetKph.roundToInt() == kph && sim.isDriving) " ✓" else ""
        }
        btnStyle.text = "운전: ${sim.style.label}"
        btnNight.text = "야간 " + if (sim.night) "ON" else "OFF"
        btnRegion.text = "지역: ${sim.region?.label ?: "-"} ▸"
        scaleButtons.forEach { (k, b) -> b.text = "×$k" + if (k == scale) " ✓" else "" }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            // 화면이 잠깐 멈췄다 돌아와도 한 번에 너무 많이 흐르지 않게 1초로 자릅니다.
            val dt = (now - lastRealMs).coerceIn(0L, 1_000L)
            lastRealMs = now
            if (sim.isDriving) {
                sim.advance(dt * scale)
                refresh()
            }
            postDelayed(this, TICK_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastRealMs = SystemClock.uptimeMillis()
        removeCallbacks(tick)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    // ── 화면 부품 ──

    /** 작은 디스플레이에서도 속도 버튼들이 눌릴 만큼 넓게 표시됩니다. */
    private fun addRow(vararg views: View) {
        val group = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        views.firstOrNull()?.let { group.addView(it) }
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        views.drop(1).forEach { view ->
            view.layoutParams = LayoutParams(dp(142), dp(64)).apply { marginEnd = dp(8) }
            row.addView(view)
        }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        group.addView(scroller)
        addView(group)
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.parseColor("#765F4F"))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = 20f
        setAllCaps(false)
        backgroundTintList = null
        background = context.getDrawable(R.drawable.mcc_tile)
        setTextColor(Color.parseColor("#745B4C"))
        layoutParams = LayoutParams(dp(142), dp(64))
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()

    companion object {
        private val TARGETS = listOf(0, 30, 60, 100, 130)
        private val SCALES = listOf(1, 10, 30)
        private const val TICK_MS = 100L
    }
}
