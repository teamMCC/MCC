package com.mcc.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.sin

/**
 * 레이어 그림을 겹쳐서 캐릭터를 그리고, 살짝 움직이게 합니다.
 *
 * ── 그림 넣는 곳 ─────────────────────────────────────────────
 *   app/src/main/res/drawable-nodpi/
 *
 *   파일명 = 에셋 이름 + .png   (예: hair_03_wet.png)
 *   · 진짜 그림이 없으면 임시 그림(ph_ 로 시작하는 파일)을 대신 씁니다.
 *   · 둘 다 없으면 그 레이어만 건너뜁니다. 하나씩 채워 넣어도 앱은 그대로 동작합니다.
 *
 * ── 움직임 ───────────────────────────────────────────────────
 *   · 프레임 그림: hair_03_wet_f1.png, _f2.png, _f3.png … 처럼 번호를 붙이면
 *     자동으로 왕복 재생합니다 (초당 8장). 움직이는 레이어는 장수를 맞춰 주세요.
 *   · 프레임 그림이 하나도 없으면 코드로 숨쉬기(살짝 늘었다 줄었다)를 줍니다.
 *   · 알은 좌우로 흔들리고, 부화가 가까울수록 크게 흔들립니다.
 *   · poke() 를 부르면 한 번 콩 뛰어오릅니다 (캐릭터를 눌렀을 때).
 *
 * ── 도감 단계 (★·★★) ────────────────────────────────────────
 *   · hair_01_wave_s1.png (★), hair_01_wave_s2.png (★★) 처럼 그림이 있으면 그 그림을 씁니다.
 *   · 없으면 기본 그림의 색을 바꿔서 그립니다 (Recolor). ★★ 는 반짝임(fx_sparkle)도 얹습니다.
 */
class CharacterView(context: Context) : FrameLayout(context) {

    enum class Motion { BREATHE, WOBBLE }

    private class Layer(
        val image: ImageView,
        val frames: List<Int>,
        val recolorTier: Int = 0,
        val includeDark: Boolean = false,
        /** 누름 판정에 넣을지 (반짝이 효과는 캐릭터 몸이 아니므로 뺌) */
        val touchable: Boolean = true,
    ) {
        var shownIndex = -1
        var bitmap: Bitmap? = null
    }

    private val layers = mutableListOf<Layer>()

    private var crackView: CrackView? = null

    /**
     * 알의 금 단계 (0 = 금 없음). 알 그림(첫 레이어) 위에 그려지고, 알과 함께 흔들립니다.
     * setAssets 로 그림을 새로 읽으면 0 으로 돌아갑니다.
     */
    var crackLevel: Int = 0
        set(value) {
            field = value
            val egg = layers.firstOrNull()?.bitmap
            if (value <= 0 || egg == null) {
                crackView?.let { removeView(it) }
                crackView = null
                return
            }
            val view = crackView ?: CrackView(context, egg).also {
                it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                addView(it)
                crackView = it
            }
            view.level = value
        }

    /**
     * 밤 조명. 그림 전체에 곱해지는 색 (null = 원래 밝기).
     * 캐릭터 그림을 새로 읽어도(setAssets) 그대로 유지됩니다.
     */
    var tint: android.graphics.ColorFilter? = null
        set(value) {
            field = value
            layers.forEach { it.image.colorFilter = value }
        }
    private var targetPx = 256
    private var motion = Motion.BREATHE
    private var amplitude = 0f
    private var periodMs = 2400.0
    private var frameCount = 1
    private var startMs = 0L
    private var running = false
    private var pokeStartMs = 0L

    /** 진짜 그림이 들어간 레이어 이름 */
    var real: List<String> = emptyList()
        private set

    /** 임시 그림으로 대신한 레이어 이름 */
    var placeholder: List<String> = emptyList()
        private set

    /** 진짜·임시 모두 없어서 빠진 레이어 이름 */
    var missing: List<String> = emptyList()
        private set

    /**
     * @param names     아래에서 위로 겹칠 에셋 이름 (Genome.layerOrder())
     * @param sizePx    화면에 그려질 크기. 이 크기에 맞춰 그림을 줄여서 읽습니다 (메모리 절약).
     * @param amplitude BREATHE: 늘어나는 비율 (0.025 = 2.5%) / WOBBLE: 흔들리는 각도 (도)
     * @param tiers     에셋 이름 → 도감 단계 (1 ★, 2 ★★). 없으면 기본 그림
     */
    fun setAssets(
        names: List<String>,
        sizePx: Int,
        motion: Motion = Motion.BREATHE,
        amplitude: Float = 0.025f,
        periodMs: Long = 2400L,
        tiers: Map<String, Int> = emptyMap(),
    ) {
        removeAllViews()
        layers.clear()
        crackView = null
        crackLevel = 0
        targetPx = sizePx
        this.motion = motion
        this.amplitude = amplitude
        this.periodMs = periodMs.toDouble()

        val realNames = mutableListOf<String>()
        val placeholderNames = mutableListOf<String>()
        val missingNames = mutableListOf<String>()

        val withSparkle = if (tiers.values.any { it >= 2 }) names + SPARKLE else names
        withSparkle.forEach { name ->
            val tier = tiers[name] ?: 0
            // 단계 전용 그림이 있으면 그것을, 없으면 기본 그림을 색만 바꿔서 씁니다.
            val tierArt = if (tier > 0) Assets.find(context, "${name}_s$tier")?.takeIf { !it.placeholder } else null
            val source = tierArt ?: Assets.find(context, name)
            if (source == null) {
                missingNames += name
                return@forEach
            }
            if (source.placeholder) placeholderNames += name else realNames += name

            val image = ImageView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(image)
            val layer = Layer(
                image,
                source.frames,
                recolorTier = if (tierArt == null) tier else 0,
                includeDark = name.startsWith("eye_") || name.startsWith("mouth_"),
                touchable = name != SPARKLE,
            )
            layer.image.colorFilter = tint
            layers += layer
            showFrame(layer, 0)
        }

        real = realNames
        placeholder = placeholderNames
        missing = missingNames
        frameCount = layers.maxOfOrNull { it.frames.size } ?: 1
        resetPose()
        restartTick()
    }

    /** 움직일 게 없는 그림(도감 썸네일 등)은 타이머를 아예 돌리지 않습니다. */
    private val isStatic: Boolean
        get() = (amplitude == 0f && frameCount <= 1) && pokeStartMs == 0L

    private fun restartTick() {
        removeCallbacks(tick)
        if (running && windowVisibility == VISIBLE) post(tick)
    }

    /** 실제로 그려진 레이어 수 */
    val layerCount: Int get() = layers.size

    private fun showFrame(layer: Layer, index: Int) {
        val i = index % layer.frames.size
        if (layer.shownIndex == i) return
        val bmp = if (layer.recolorTier > 0) {
            Assets.recolored(context, layer.frames[i], targetPx, layer.recolorTier, layer.includeDark)
        } else {
            Assets.bitmap(context, layer.frames[i], targetPx)
        }
        bmp?.let { layer.image.setImageBitmap(it); layer.bitmap = it }
        layer.shownIndex = i
    }

    /**
     * 이 칸 안의 점 (x, y) 이 캐릭터·알이 실제로 그려진 곳인지.
     * 그림 주변의 투명한 곳은 false → 누름이 뒤에 있는 방 소품으로 넘어갑니다.
     * @param radius 손가락 굵기만큼 넉넉하게 볼 반지름(px)
     */
    fun hits(x: Float, y: Float, radius: Float): Boolean {
        val solid = layers.filter { it.touchable && it.bitmap != null }
        // 그림을 아직 못 읽었으면 가운데 절반만 캐릭터로 봅니다.
        if (solid.isEmpty()) return x > width * 0.25f && x < width * 0.75f && y > height * 0.25f
        return solid.any { layer ->
            val bmp = layer.bitmap ?: return@any false
            HitTest.solidNear(width, height, bmp.width, bmp.height, x, y, radius) { px, py -> bmp.getPixel(px, py) ushr 24 }
        }
    }

    private fun resetPose() {
        scaleX = 1f
        scaleY = 1f
        rotation = 0f
        translationY = 0f
    }

    /** 한 번 콩 뛰어오릅니다. 캐릭터를 눌렀을 때 부릅니다. */
    fun poke() {
        pokeStartMs = SystemClock.uptimeMillis()
        restartTick()
    }

    /**
     * true 면 움직임을 멈춥니다 (숨쉬기·흔들기·프레임 넘김·콩).
     * 주행 중에는 Pleos 가이드(움직이는 이미지 최소화)에 따라 멈춘 그림으로 둡니다.
     */
    var still: Boolean = false
        set(value) {
            field = value
            if (value) {
                scaleX = 1f; scaleY = 1f; rotation = 0f; translationY = 0f
                pokeStartMs = 0L
            }
            restartTick()
        }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            // 주행 중(still)이거나 움직일 게 없으면 타이머를 멈춥니다. 다시 필요할 때 restartTick() 으로 깨웁니다.
            if (still || isStatic) return
            // 주행 중 화면이 가려져 있으면 쉬면서 기다립니다.
            if (!isShown) {
                postDelayed(this, IDLE_TICK_MS)
                return
            }
            val t = SystemClock.uptimeMillis() - startMs

            if (frameCount > 1) {
                val step = FrameSequence.frameAt(t, frameCount, FRAME_MS)
                layers.forEach { showFrame(it, step) }
            }

            pivotX = width / 2f
            pivotY = height * FEET_Y
            val phase = sin(2.0 * PI * t / periodMs).toFloat()
            when (motion) {
                Motion.BREATHE -> if (frameCount <= 1) {
                    // 숨쉬기: 위아래로 늘면 옆으로 살짝 줄어듭니다 (말랑한 느낌)
                    scaleY = 1f + amplitude * phase
                    scaleX = 1f - amplitude * 0.4f * phase
                }
                Motion.WOBBLE -> rotation = amplitude * phase
            }
            // 콩: 0.45초 동안 반원 모양으로 뛰었다 내려옵니다.
            val sincePoke = SystemClock.uptimeMillis() - pokeStartMs
            translationY = if (pokeStartMs > 0 && sincePoke < POKE_MS) {
                -height * POKE_HEIGHT * sin(PI * sincePoke / POKE_MS).toFloat()
            } else {
                pokeStartMs = 0L
                0f
            }
            postDelayed(this, TICK_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        startMs = SystemClock.uptimeMillis()
        removeCallbacks(tick)
        post(tick)
    }

    /** 앱이 뒤로 가거나 다른 화면에 가려지면(창이 안 보이면) 타이머를 멈춥니다. */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        restartTick()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 그림 1장이 보이는 시간. 125ms = 초당 8장. */
        const val FRAME_MS = 125L
        private const val TICK_MS = 33L           // 약 30fps
        private const val IDLE_TICK_MS = 500L
        /** 발바닥 기준선 (1024 캔버스에서 y=940). 숨쉬기·흔들기의 중심. */
        private const val FEET_Y = 940f / 1024f
        private const val POKE_MS = 450L
        private const val POKE_HEIGHT = 0.08f
        /** ★★ 캐릭터 위에 얹는 반짝임 그림 */
        const val SPARKLE = "fx_sparkle"
    }
}

/**
 * res/drawable-nodpi 의 그림을 이름으로 찾고, 화면 크기에 맞춰 줄여서 읽습니다.
 *
 * 1024×1024 그림을 그대로 읽으면 한 장에 4MB 입니다. 캐릭터 3마리 × 레이어 8장 ×
 * 프레임 4장이면 400MB 가 넘어 앱이 죽습니다. 화면 크기에 맞춰 줄여 읽고 재사용합니다.
 */
object Assets {

    /** 임시 그림 이름 앞에 붙는 글자. 진짜 그림과 이름이 겹치지 않게 합니다. */
    const val PLACEHOLDER_PREFIX = "ph_"
    private const val MAX_FRAMES = 12

    class Source(val frames: List<Int>, val placeholder: Boolean)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()

    private fun resId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    /** 진짜 프레임(_f1…) → 진짜 한 장 → 임시 그림(ph_) 순서로 찾습니다. 없으면 null. */
    fun find(context: Context, name: String): Source? {
        val frames = mutableListOf<Int>()
        for (i in 1..MAX_FRAMES) {
            val id = resId(context, "${name}_f$i")
            if (id == 0) break
            frames += id
        }
        if (frames.isNotEmpty()) return Source(frames, placeholder = false)

        val single = resId(context, name)
        if (single != 0) return Source(listOf(single), placeholder = false)

        val ph = resId(context, PLACEHOLDER_PREFIX + name)
        if (ph != 0) return Source(listOf(ph), placeholder = true)

        return null
    }

    /** 도감 단계 그림이 따로 없을 때: 기본 그림을 읽어 색을 바꾼 사본 (캐시) */
    fun recolored(context: Context, resId: Int, targetPx: Int, tier: Int, includeDark: Boolean): Bitmap? {
        val key = "$resId@$targetPx#t$tier"
        cache.get(key)?.let { return it }
        val base = bitmap(context, resId, targetPx) ?: return null
        return try {
            val copy = base.copy(Bitmap.Config.ARGB_8888, true) ?: return base
            val w = copy.width
            val h = copy.height
            val px = IntArray(w * h)
            copy.getPixels(px, 0, w, 0, 0, w, h)
            Recolor.apply(px, tier, includeDark)
            copy.setPixels(px, 0, w, 0, 0, w, h)
            cache.put(key, copy)
            copy
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            base
        }
    }

    /** 투명한 테두리를 잘라낸 사본 (도감·방 꾸미기 썸네일용, 캐시) */
    fun trimmed(context: Context, resId: Int, targetPx: Int): Bitmap? {
        val key = "$resId@$targetPx#trim"
        cache.get(key)?.let { return it }
        val base = bitmap(context, resId, targetPx) ?: return null
        val w = base.width
        val h = base.height
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 16) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return base
        val out = Bitmap.createBitmap(base, minX, minY, maxX - minX + 1, maxY - minY + 1)
        cache.put(key, out)
        return out
    }

    /** 이름으로 첫 그림 id (진짜 → 임시). 없으면 0 */
    fun firstResId(context: Context, name: String): Int = find(context, name)?.frames?.firstOrNull() ?: 0

    /** 화면 크기(targetPx) 이상이 되는 범위에서 최대한 줄여 읽습니다. */
    fun bitmap(context: Context, resId: Int, targetPx: Int): Bitmap? {
        val key = "$resId@$targetPx"
        cache.get(key)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            var sample = 1
            while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp: Bitmap? = BitmapFactory.decodeResource(context.resources, resId, options)
            if (bmp != null) cache.put(key, bmp)
            bmp
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            null
        }
    }
}
