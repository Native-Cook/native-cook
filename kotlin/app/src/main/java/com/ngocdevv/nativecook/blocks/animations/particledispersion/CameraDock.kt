package com.ngocdevv.nativecook.blocks.animations.particledispersion

import com.ngocdevv.nativecook.R

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/** Fixed dock geometry, color arcs, and dot texture from the original screen. */
internal class CameraDock(context: Context) : ViewGroup(context) {
  var onPrimary: (() -> Unit)? = null
  var onRetake: (() -> Unit)? = null
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val instruction = TextView(context).apply {
    id = R.id.particledispersion_effect_status
    textSize = 17f
    setTextColor(0xFF898789.toInt())
    gravity = Gravity.CENTER
    maxLines = 2
    accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
  }
  private val primary = DockButton(context, true).apply { setOnClickListener { onPrimary?.invoke() } }
  private val left = DockButton(context, false).apply {
    isEnabled = false
    contentDescription = "Crop unavailable in this demo"
  }
  private val right = DockButton(context, false).apply {
    id = R.id.particledispersion_retake_button
    setOnClickListener { onRetake?.invoke() }
  }

  init {
    setWillNotDraw(false)
    background = GradientDrawable().apply {
      setColor(0xF7F6F4F7.toInt())
      cornerRadii = floatArrayOf(dp(52f), dp(52f), dp(52f), dp(52f), 0f, 0f, 0f, 0f)
    }
    clipToOutline = true
    elevation = dp(10f)
    addView(instruction); addView(primary); addView(left); addView(right)
  }

  fun update(text: String, result: Boolean, enabled: Boolean, error: Boolean, reduced: Boolean) {
    val changedMode = primary.result != result
    instruction.text = text
    primary.result = result
    primary.id = if (result) R.id.particledispersion_replay_button else R.id.particledispersion_capture_button
    primary.contentDescription = if (result) "Replay effect" else if (error) "Try again" else "Take photo"
    primary.isEnabled = enabled
    primary.alpha = if (enabled) 1f else 0.65f
    primary.reduced = reduced
    left.reduced = reduced
    right.reduced = reduced
    left.icon = if (result) "crop" else "chevron"
    right.icon = if (result) "close" else "photo"
    right.isEnabled = result
    right.contentDescription = if (result) "Discard photo and retake" else "Photo library unavailable in this demo"
    primary.invalidate(); left.invalidate(); right.invalidate()
    if (changedMode) {
      for (control in listOf(primary, left, right)) {
        val targetAlpha = control.alpha
        control.alpha = 0f
        if (!reduced) { control.scaleX = 0.92f; control.scaleY = 0.92f }
        control.animate().alpha(targetAlpha).scaleX(1f).scaleY(1f).setDuration(180).start()
      }
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
    instruction.measure(MeasureSpec.makeMeasureSpec(width - dp(56f).roundToInt(), MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(dp(44f).roundToInt(), MeasureSpec.EXACTLY))
    for (view in listOf(primary, left, right)) {
      val size = dp(if (view === primary) 106f else 54f).roundToInt()
      view.measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val width = r - l
    fun place(view: View, x: Float, y: Float) {
      view.layout(x.roundToInt(), y.roundToInt(), x.roundToInt() + view.measuredWidth, y.roundToInt() + view.measuredHeight)
    }
    place(instruction, dp(28f), dp(25f))
    place(primary, (width - dp(106f)) / 2, dp(82f))
    val row = min(width.toFloat(), dp(370f))
    place(left, (width - row) / 2 + dp(26f), dp(108f))
    place(right, (width + row) / 2 - dp(80f), dp(108f))
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    paint.color = 0x19695E69
    for (row in 0 until 11) for (column in 0 until 20) {
      canvas.drawCircle((column + 0.5f) * width / 20, (row + 0.5f) * height / 11, dp(1f), paint)
    }
    paint.color = 0x337F787E
    paint.strokeWidth = dp(3f); paint.strokeCap = Paint.Cap.ROUND
    canvas.drawLine(width / 2f - dp(10f), dp(20f), width / 2f, dp(16f), paint)
    canvas.drawLine(width / 2f, dp(16f), width / 2f + dp(10f), dp(20f), paint)
  }

  private fun dp(value: Float) = value * resources.displayMetrics.density
}

private class DockButton @JvmOverloads constructor(context: Context, private val primary: Boolean = false) : View(context) {
  var result = false
  var reduced = false
  var icon = "chevron"
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val arcBounds = RectF(8f, 8f, 98f, 98f)
  private val photoBounds = RectF(13f, 16f, 41f, 38f)
  private val checkPath = Path().apply { moveTo(37f, 53f); lineTo(49f, 64f); lineTo(70f, 42f) }
  private val photoPath = Path().apply { moveTo(14f, 35f); lineTo(23f, 26f); lineTo(30f, 33f); lineTo(35f, 29f); lineTo(40f, 35f) }
  private val colors = intArrayOf(0xF0F885, 0x9FD8BC, 0xB5ADE4, 0xBBDCFC, 0xFBC8EC)
  private val starts = floatArrayOf(-112f, -50f, 20f, 112f, 176f)
  private val sweeps = floatArrayOf(50f, 58f, 78f, 52f, 54f)
  init { isClickable = true; isFocusable = true }
  override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
  override fun drawableStateChanged() {
    super.drawableStateChanged()
    if (!reduced) animate().scaleX(if (isPressed) 0.92f else 1f)
      .scaleY(if (isPressed) 0.92f else 1f).setDuration(100).start()
  }
  override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
    super.onInitializeAccessibilityNodeInfo(info)
    info.className = accessibilityClassName
  }
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val factor = width / (if (primary) 106f else 54f)
    canvas.save(); canvas.scale(factor, factor)
    val center = if (primary) 53f else 27f
    paint.style = Paint.Style.FILL
    paint.color = if (primary) 0xFFFFFDFF.toInt() else 0xFFDEDCDE.toInt()
    canvas.drawCircle(center, center, center - 0.5f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
    paint.strokeWidth = if (primary) 5f else 2.8f
    if (primary && !result) {
      for (i in colors.indices) {
        paint.color = colors[i] or 0xFF000000.toInt()
        canvas.drawArc(arcBounds, starts[i], sweeps[i], false, paint)
      }
    } else if (primary) {
      paint.color = 0xFFB8B1EB.toInt()
      canvas.drawPath(checkPath, paint)
    } else {
      paint.color = Color.WHITE
      when (icon) {
        "close" -> { canvas.drawLine(21f, 21f, 33f, 33f, paint); canvas.drawLine(21f, 33f, 33f, 21f, paint) }
        "photo" -> {
          canvas.drawRoundRect(photoBounds, 3f, 3f, paint)
          canvas.drawCircle(33f, 23f, 2f, paint)
          canvas.drawPath(photoPath, paint)
        }
        "crop" -> {
          canvas.drawLine(20f, 13f, 20f, 34f, paint); canvas.drawLine(20f, 34f, 41f, 34f, paint)
          canvas.drawLine(13f, 20f, 34f, 20f, paint); canvas.drawLine(34f, 20f, 34f, 41f, paint)
        }
        else -> { canvas.drawLine(20f, 24f, 27f, 30f, paint); canvas.drawLine(27f, 30f, 34f, 24f, paint) }
      }
    }
    canvas.restore()
  }
}
