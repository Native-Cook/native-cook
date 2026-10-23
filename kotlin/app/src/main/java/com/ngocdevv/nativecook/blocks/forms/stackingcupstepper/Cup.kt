package com.ngocdevv.nativecook.blocks.forms.stackingcupstepper

import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.exp
import kotlin.math.roundToInt

private const val FLAT = .27f
private const val BASE = 22f
private const val MOUTH = 30f
private const val SLEEVE = 31f
private const val SLEEVE_DROP = 15f
private const val SLEEVE_SLIDE = .35f
private const val SLEEVE_PAD = 1.2f
private const val SHORTEST = 62f
private const val RIM = 35.5f
private const val RIM_HEIGHT = 6f
private const val DOME = 29.5f
private const val DOME_TOP = 27f
private const val DOME_HEIGHT = 7.5f
private const val LID_TIP = .008f
private val PAPER = intArrayOf(0xFFC9BFAE.toInt(), 0xFFF7F2E8.toInt(), 0xFFE6DECF.toInt(), 0xFFAEA391.toInt())
private val KRAFT = intArrayOf(0xFF86542A.toInt(), 0xFFBC844C.toInt(), 0xFFA9713D.toInt(), 0xFF744721.toInt())
private val LID = intArrayOf(0xFFD3CABB.toInt(), 0xFFFCF9F2.toInt(), 0xFFEDE6D9.toInt(), 0xFFB9AE9C.toInt())
private val SHADE_AT = floatArrayOf(0f, .3f, .64f, 1f)
private val LID_TOP = intArrayOf(0xFFFFFDF8.toInt(), 0xFFE9E1D3.toInt())
private const val BEAN = 0xFF3A2315.toInt()
private val INSIDE = intArrayOf(0xFF6B4A30.toInt(), 0xFF2A1C12.toInt())
private val RIM_TOP = intArrayOf(0xFFE4DCCE.toInt(), 0xFFFBF8F1.toInt(), 0xFFD6CDBE.toInt())
private val SUNKEN = intArrayOf(0xFFE2DACB.toInt(), 0xFFF6F1E7.toInt(), 0xFFDDD4C4.toInt())
private const val HOLE = 0xFF74685A.toInt()
private const val GROUND_BLUR = 5f
private const val GROUND_ALPHA = .5f
private const val SHADOW_LENGTH = 22f
private const val SHADOW_BOX_HEIGHT = 34f
private const val SHADOW_LIFT = 6f
private const val MARK_DEPTH = .96f
private const val BEAN_STROKE = 1.3f
private val GROUND = RectF(-BASE - 9f, -4f, -BASE - 9f + BASE * 2f + 22f, 11f)
private val GROUND_SAMPLES = FloatArray(7) { (it - 3).toFloat() }
private val GROUND_WEIGHTS = FloatArray(7) { exp(-GROUND_SAMPLES[it] * GROUND_SAMPLES[it] / 2f) }.let { values ->
    val sum = values.sum()
    FloatArray(values.size) { values[it] / sum }
}

const val CUP_WIDTH = RIM * 2f

fun cupHeight(body: Float) = body + RIM_HEIGHT + DOME_HEIGHT + DOME_TOP * FLAT + BASE * FLAT

/** The changing paths are built once per frame, then shared by every cup in the pile. */
class CupShape(body: Float = SHORTEST, lift: Float = 0f) {
    internal val paper = Path()
    internal val sleeve = Path()
    internal var markY = 0f
        private set
    internal var mouthY = 0f
        private set
    internal var lidY = 0f
        private set
    internal var lidAngle = 0f
        private set
    internal var shadowOpacity = 1f
        private set
    private var oldBody = Float.NaN
    private var oldLift = Float.NaN
    private val arcBounds = RectF()

    init { update(body, lift) }

    fun update(body: Float, lift: Float) {
        if (body == oldBody && lift == oldLift) return
        if (body != oldBody) {
            paper.rewind()
            paper.moveTo(-MOUTH, -body)
            paper.lineTo(-BASE, 0f)
            arc(paper, BASE, 0f, true)
            paper.lineTo(MOUTH, -body)
            paper.close()
            val top = -body + SLEEVE_DROP + SLEEVE_SLIDE * (body - SHORTEST)
            val bottom = top + SLEEVE
            fun radius(y: Float) = BASE + (MOUTH - BASE) * -y / body
            val wide = radius(top) + SLEEVE_PAD
            val narrow = radius(bottom) + SLEEVE_PAD
            sleeve.rewind()
            sleeve.moveTo(-wide, top)
            arc(sleeve, wide, top, true)
            sleeve.lineTo(narrow, bottom)
            arc(sleeve, narrow, bottom, false)
            sleeve.close()
            val middle = top + SLEEVE / 2f
            markY = middle + (radius(middle) + SLEEVE_PAD) * FLAT * MARK_DEPTH
            mouthY = -body
        }
        lidY = -body - lift
        lidAngle = lift * LID_TIP
        shadowOpacity = 1f / (1f + lift / SHADOW_LIFT)
        oldBody = body
        oldLift = lift
    }

    private fun arc(path: Path, radius: Float, y: Float, fromLeft: Boolean) {
        arcBounds.set(-radius, y - radius * FLAT, radius, y + radius * FLAT)
        path.arcTo(arcBounds, if (fromLeft) 180f else 0f, if (fromLeft) -180f else 180f, false)
    }
}

@Composable
fun useCupShape(body: State<Float>, lift: State<Float>): CupShape {
    val shape = remember { CupShape() }
    shape.update(body.value, lift.value)
    return shape
}

/** The origin is the middle of the cup's base, as in the source canvas group. */
@Composable
fun Cup(shape: CupShape, modifier: Modifier = Modifier) {
    val art = remember { CupArt() }
    Canvas(modifier) { surface(0.dp) { _, _, _, _ -> art.draw(this, shape) } }
}

internal class CupArt {
    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun shaded(colors: IntArray, x0: Float, y0: Float, x1: Float, y1: Float, stops: FloatArray? = null) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP) }
    private val paperPaint = shaded(PAPER, -MOUTH, 0f, MOUTH, 0f, SHADE_AT)
    private val sleevePaint = shaded(KRAFT, -MOUTH, 0f, MOUTH, 0f, SHADE_AT)
    private val rimPaint = shaded(LID, -RIM, 0f, RIM, 0f, SHADE_AT)
    private val rimTopPaint = shaded(RIM_TOP, -RIM, 0f, RIM, 0f)
    private val domePaint = shaded(LID, -DOME, 0f, DOME, 0f, SHADE_AT)
    private val topPaint = shaded(LID_TOP, -DOME_TOP, -RIM_HEIGHT - DOME_HEIGHT - 6f, DOME_TOP, -RIM_HEIGHT - DOME_HEIGHT + 6f)
    private val sunkenPaint = shaded(SUNKEN, -DOME_TOP, 0f, DOME_TOP, 0f)
    private val mouthPaint = paint(PAPER[1])
    private val insidePaint = shaded(INSIDE, 0f, -MOUTH * FLAT, 0f, MOUTH * FLAT)
    private val holePaint = paint(HOLE)
    private val beanPaint = paint(BEAN)
    private val beanLinePaint = paint(KRAFT[2]).apply { style = Paint.Style.STROKE; strokeWidth = BEAN_STROKE; strokeCap = Paint.Cap.ROUND }
    private val groundPaint = paint(NativeColor.BLACK).apply {
        if (Build.VERSION.SDK_INT >= 28) maskFilter = BlurMaskFilter(sigmaToRadius(GROUND_BLUR), BlurMaskFilter.Blur.NORMAL)
    }
    private val shadowGradient = LinearGradient(
        0f, 0f, 0f, SHADOW_LENGTH, intArrayOf(NativeColor.argb(107, 40, 28, 14), NativeColor.argb(0, 40, 28, 14)), null, Shader.TileMode.CLAMP,
    )
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shadowGradient }
    private val shadowMatrix = Matrix()
    private val paints = listOf(paperPaint, sleevePaint, rimPaint, rimTopPaint, domePaint, topPaint, sunkenPaint, mouthPaint, insidePaint, holePaint, beanPaint, beanLinePaint)
    private val rimWall = Path().apply {
        moveTo(-RIM, -RIM_HEIGHT); lineTo(-RIM, 1f)
        arcTo(oval(1f, RIM), 180f, -180f, false)
        lineTo(RIM, -RIM_HEIGHT)
        arcTo(oval(-RIM_HEIGHT, RIM), 0f, 180f, false)
        close()
    }
    private val domeWall = Path().apply {
        moveTo(-DOME, -RIM_HEIGHT)
        arcTo(oval(-RIM_HEIGHT, DOME), 180f, -180f, false)
        lineTo(DOME_TOP, -RIM_HEIGHT - DOME_HEIGHT)
        arcTo(oval(-RIM_HEIGHT - DOME_HEIGHT, DOME_TOP), 0f, 180f, false)
        close()
    }
    private val beanLine = Path().apply { moveTo(.6f, -8.4f); cubicTo(4f, -3.4f, -3.8f, 2.6f, -.4f, 8.4f) }
    private val mouthOval = oval(0f, MOUTH)
    private val insideOval = oval(.6f, MOUTH - 2.4f)
    private val rimOval = oval(-RIM_HEIGHT, RIM)
    private val domeOval = oval(-RIM_HEIGHT - DOME_HEIGHT, DOME_TOP)
    private val sunkenOval = oval(-RIM_HEIGHT - DOME_HEIGHT + .7f, DOME_TOP - 5.5f)
    private val holeTop = -RIM_HEIGHT - DOME_HEIGHT - DOME_TOP * FLAT * .62f - 1.5f

    fun draw(scope: DrawScope, shape: CupShape, alpha: Float = 1f) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val opacity = alpha.coerceIn(0f, 1f)
        val fullAlpha = (opacity * 255f).roundToInt()
        paints.forEach { it.alpha = fullAlpha }
        if (Build.VERSION.SDK_INT >= 28) {
            groundPaint.alpha = (GROUND_ALPHA * opacity * 255f).roundToInt()
            canvas.drawOval(GROUND, groundPaint)
        } else {
            for (y in GROUND_SAMPLES.indices) for (x in GROUND_SAMPLES.indices) {
                groundPaint.alpha = (GROUND_ALPHA * opacity * GROUND_WEIGHTS[x] * GROUND_WEIGHTS[y] * 255f).roundToInt()
                val dx = GROUND_SAMPLES[x] * GROUND_BLUR
                val dy = GROUND_SAMPLES[y] * GROUND_BLUR
                canvas.drawOval(GROUND.left + dx, GROUND.top + dy, GROUND.right + dx, GROUND.bottom + dy, groundPaint)
            }
        }
        canvas.drawPath(shape.paper, paperPaint)
        canvas.save()
        canvas.clipPath(shape.paper)
        shadowMatrix.setTranslate(0f, shape.mouthY)
        shadowGradient.setLocalMatrix(shadowMatrix)
        shadowPaint.alpha = (shape.shadowOpacity * opacity * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRect(-MOUTH, shape.mouthY, MOUTH, shape.mouthY + SHADOW_BOX_HEIGHT, shadowPaint)
        canvas.restore()
        canvas.drawPath(shape.sleeve, sleevePaint)
        drawBean(canvas, -5.5f, shape.markY, -.42f)
        drawBean(canvas, 6.5f, shape.markY + .5f, .36f)

        canvas.save()
        canvas.translate(0f, shape.mouthY)
        canvas.drawOval(mouthOval, mouthPaint)
        canvas.drawOval(insideOval, insidePaint)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, shape.lidY)
        canvas.rotate(shape.lidAngle * 180f / Math.PI.toFloat())
        canvas.drawPath(rimWall, rimPaint)
        canvas.drawOval(rimOval, rimTopPaint)
        canvas.drawPath(domeWall, domePaint)
        canvas.drawOval(domeOval, topPaint)
        canvas.drawOval(sunkenOval, sunkenPaint)
        canvas.drawOval(-4.2f, holeTop, 4.2f, holeTop + 3f, holePaint)
        canvas.restore()
    }

    private fun drawBean(canvas: NativeCanvas, x: Float, y: Float, radians: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(radians * 180f / Math.PI.toFloat())
        canvas.drawOval(-6.4f, -9f, 6.4f, 9f, beanPaint)
        canvas.drawPath(beanLine, beanLinePaint)
        canvas.restore()
    }

    private fun oval(y: Float, radius: Float) = RectF(-radius, y - radius * FLAT, radius, y + radius * FLAT)
}
