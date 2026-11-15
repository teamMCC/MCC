package com.mcc.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.mcc.app.game.WindowArea
import kotlin.random.Random

/**
 * 방 창문 안에만 내리는 비.
 *
 * 와이퍼가 움직이면(또는 시연에서 '비'를 켜면) 창밖이 살짝 흐려지고 빗줄기가 내립니다.
 * 창문 위치는 [area] 가 매 프레임 알려 줍니다 (방 그림이 화면에 맞춰 잘리는 위치를 반영).
 * 창문 마스크 그림(room_xxx_window.png)이 있으면 그 흰 부분에만, 없으면 네모·둥근·동그란 창 모양에만 그립니다.
 * 비가 그치면 그리기를 멈춰 CPU 를 쓰지 않습니다. 빗줄기는 1초에 약 30번만 다시 그립니다.
 */
class RainView(context: Context) : View(context) {

    /**
     * 비가 내릴 자리.
     * @param rect      빗줄기를 뿌릴 화면 범위 (마스크가 있으면 마스크 흰 부분을 감싸는 범위)
     * @param shape     마스크가 없을 때 창 모양
     * @param mask      창문 마스크 (흰색 = 비가 보이는 유리). null 이면 shape 사용
     * @param imageRect 마스크(= 방 그림 전체)가 화면에 놓이는 범위
     */
    class Area(val rect: RectF, val shape: WindowArea.Shape, val mask: Bitmap? = null, val imageRect: RectF? = null)

    /** 지금 창문의 화면 위치와 모양. null 이면 그리지 않음 */
    var area: () -> Area? = { null }

    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private val drops = FloatArray(DROPS * 3)   // x(0~1), y(0~1), 속도 배율
    private val rnd = Random(7)
    private var lastMs = 0L
    private val path = Path()

    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 235, 244, 255)
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val cloudPaint = Paint().apply { color = Color.argb(70, 70, 90, 120) }

    private val frame = Runnable { invalidate() }

    var raining: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            lastMs = 0L
            if (value) invalidate() else { removeCallbacks(frame); invalidate() }
        }

    init {
        for (i in 0 until DROPS) {
            drops[i * 3] = rnd.nextFloat()
            drops[i * 3 + 1] = rnd.nextFloat()
            drops[i * 3 + 2] = 0.7f + rnd.nextFloat() * 0.6f
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!raining) return
        val current = area()
        if (current == null || current.rect.width() <= 0f || current.rect.height() <= 0f) {
            // 방 그림이 아직 배치되지 않았거나 창문이 없는 방 → 잠시 뒤 다시 확인
            postDelayed(frame, WAIT_MS)
            return
        }
        val rect = current.rect
        val mask = current.mask
        val imageRect = current.imageRect

        val now = SystemClock.uptimeMillis()
        val dt = if (lastMs == 0L) 0f else ((now - lastMs).coerceAtMost(100L)) / 1000f
        lastMs = now

        val useMask = mask != null && imageRect != null
        val saved = if (useMask) canvas.saveLayer(rect, null) else canvas.save()
        if (!useMask) {
            path.reset()
            when (current.shape) {
                WindowArea.Shape.OVAL -> path.addOval(rect, Path.Direction.CW)
                WindowArea.Shape.ROUND -> {
                    val r = rect.width() * 0.12f
                    path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
                }
                WindowArea.Shape.RECT -> path.addRect(rect, Path.Direction.CW)
            }
            canvas.clipPath(path)
        }
        canvas.drawRect(rect, cloudPaint)

        val len = rect.height() * 0.09f
        val slant = len * 0.25f
        dropPaint.strokeWidth = (rect.width() * 0.004f).coerceAtLeast(1.5f)
        for (i in 0 until DROPS) {
            val k = i * 3
            var y = drops[k + 1] + dt * FALL_PER_SEC * drops[k + 2]
            if (y > 1.1f) {
                y -= 1.2f
                drops[k] = rnd.nextFloat()
            }
            drops[k + 1] = y
            val x = rect.left + drops[k] * (rect.width() + slant)
            val top = rect.top + y * rect.height()
            canvas.drawLine(x, top, x - slant, top + len, dropPaint)
        }
        // 마스크: 흰(불투명) 부분만 남기고 창살·벽·커튼 위의 비는 지웁니다.
        if (useMask) canvas.drawBitmap(mask!!, null, imageRect!!, maskPaint)
        canvas.restoreToCount(saved)
        postDelayed(frame, FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DROPS = 70
        /** 창 높이 대비 1초에 내려오는 거리 */
        private const val FALL_PER_SEC = 1.6f
        private const val FRAME_MS = 33L
        private const val WAIT_MS = 500L
    }
}
