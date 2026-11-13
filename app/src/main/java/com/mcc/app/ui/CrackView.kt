package com.mcc.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** 알 그림 위에 금을 그립니다 ([EggCrack]). 알과 함께 흔들리도록 CharacterView 안에 얹습니다. */
class CrackView(context: Context, private val egg: Bitmap) : View(context) {

    var level: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** 알 그림의 그려진 영역 (한 번만 계산) */
    private val bounds: IntArray by lazy {
        EggCrack.opaqueBounds(egg.width, egg.height) { x, y -> egg.getPixel(x, y) ushr 24 }
    }

    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#5B3A26")
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFF6E6")
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lines = EggCrack.lines(level)
        if (lines.isEmpty() || width == 0 || height == 0) return
        // ImageView FIT_CENTER 와 같은 배치
        val scale = minOf(width.toFloat() / egg.width, height.toFloat() / egg.height)
        val ox = (width - egg.width * scale) / 2f
        val oy = (height - egg.height * scale) / 2f
        val (l, t, r, b) = bounds.toList()
        val ew = (r - l) * scale
        val eh = (b - t) * scale
        val ex = ox + l * scale
        val ey = oy + t * scale
        dark.strokeWidth = ew * 0.028f
        light.strokeWidth = ew * 0.010f
        lines.forEach { pts ->
            val path = Path()
            var i = 0
            while (i + 1 < pts.size) {
                val x = ex + pts[i] * ew
                val y = ey + pts[i + 1] * eh
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            canvas.drawPath(path, dark)
            canvas.save()
            canvas.translate(-dark.strokeWidth * 0.35f, -dark.strokeWidth * 0.35f)
            canvas.drawPath(path, light)
            canvas.restore()
        }
    }
}
