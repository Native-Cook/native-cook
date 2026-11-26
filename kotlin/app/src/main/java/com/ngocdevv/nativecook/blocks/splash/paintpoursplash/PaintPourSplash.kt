package com.ngocdevv.nativecook.blocks.splash.paintpoursplash

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Four-second recording loop, containing two identical two-second flows. No touch interaction. */
private const val PAINT_POUR_SPLASH_LOOP = 4.0

@Composable
fun PaintPourSplash(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val view = androidx.compose.ui.platform.LocalView.current
    val window = androidx.activity.compose.LocalActivity.current?.window
    androidx.compose.runtime.DisposableEffect(window, view) {
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        // Matches the reference StatusBar style=dark; return the gallery style on exit.
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    AndroidView(
        factory = { PaintPourSplashView(it) },
        update = { it.setAutoPlay(autoPlay) },
        modifier = modifier.fillMaxSize(),
    )
}

private class PaintPourSplashView(context: Context) : View(context), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val bitmap = context.assets.open("paintpoursplash/paintpoursplash_paint.webp").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        })!!
    }
    private val image = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val flow = if (Build.VERSION.SDK_INT >= 33) PaintPourSplashFlow(bitmap) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private val archivo = Typeface.createFromAsset(context.assets, "paintpoursplash/paintpoursplash_ArchivoExpanded-Black.ttf")
    private val inter = Typeface.createFromAsset(context.assets, "paintpoursplash/paintpoursplash_Inter-Medium.ttf")
    // Isolated, unhinted glyph advances match Skia Glyphs; whole-string metrics apply kerning.
    private fun glyphWidths(text: String, font: Typeface, size: Float): FloatArray {
        paint.typeface = font; paint.textSize = size
        val one = FloatArray(1)
        return FloatArray(text.length) { i ->
            paint.getTextWidths(text, i, i + 1, one)
            one[0]
        }
    }
    private val hueWidths = glyphWidths("Hue", archivo, 120f)
    private val firstWidths = glyphWidths("Colour for", inter, 16f)
    private val secondWidths = glyphWidths("every wall.", inter, 16f)
    private var autoPlay = true
    private var reduced = false
    private var scheduled = false
    private var origin = 0L
    private var elapsed = 0.0
    private var time = 0f
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { readReducedMotion() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Hue. Colour for every wall."
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        isClickable = false
        isFocusable = true
        setBackgroundColor(0xffe9e9ec.toInt())
    }
    fun setAutoPlay(value: Boolean) { autoPlay = value; schedule() }
    private fun readReducedMotion() {
        reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        schedule()
        invalidate()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        readReducedMotion()
    }
    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer)
        stop()
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) schedule() else stop()
    }
    private fun stop() {
        if (scheduled && origin != 0L) elapsed += (System.nanoTime() - origin) / 1e9
        origin = 0L
        scheduled = false
        Choreographer.getInstance().removeFrameCallback(this)
    }
    private fun schedule() {
        val running = isAttachedToWindow && windowVisibility == VISIBLE && autoPlay && !reduced && flow != null
        if (!running) { stop(); return }
        if (!scheduled) {
            origin = 0L
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (!scheduled) return
        if (origin == 0L) origin = frameTimeNanos
        time = ((elapsed + (frameTimeNanos - origin) / 1e9) % PAINT_POUR_SPLASH_LOOP).toFloat()
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val scale = h / 1536f
        val fitX = (w - 708 * scale) / 2
        image.setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate(fitX, 0f) })
        flow?.resize(w.toFloat(), h.toFloat())
    }
    override fun onDraw(canvas: Canvas) {
        // On API 26–32 and actual system Reduce Motion, use the untouched image shader.
        paint.shader = if (!reduced && flow != null) flow.at(time) else image
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        canvas.save()
        canvas.scale(density, density)
        val w = width / density
        val fy = height / density / 852f
        fun line(text: String, font: Typeface, size: Float, right: Float, baseline: Float, tracking: Float, color: Int, advances: FloatArray) {
            paint.typeface = font; paint.textSize = size; paint.color = color
            var x = right - advances.sum() - tracking * (text.length - 1)
            for (i in text.indices) {
                canvas.drawText(text, i, i + 1, x, baseline, paint)
                x += advances[i] + tracking
            }
        }
        line("Hue", archivo, 120f, w - 16, 400 * fy, -8f, 0xff16161a.toInt(), hueWidths)
        line("Colour for", inter, 16f, w - 28, 432 * fy, 0f, 0xff55555e.toInt(), firstWidths)
        line("every wall.", inter, 16f, w - 28, 452 * fy, 0f, 0xff55555e.toInt(), secondWidths)
        canvas.restore()
    }
}
