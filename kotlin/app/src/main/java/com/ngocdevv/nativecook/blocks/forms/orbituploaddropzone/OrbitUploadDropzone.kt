package com.ngocdevv.nativecook.blocks.forms.orbituploaddropzone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The title and supporting lines for one state of the upload card. */
data class Copy(val title: String, val text: String)
private val IDLE = Copy("Import file", "Tap to choose a file to upload it to your project. It only takes a few seconds")
private val HOVER = Copy("Release file", "Release your files here to upload")
private val IMPORTING = Copy("Importing data", "Please wait a few seconds while we’re importing your data to the project")
private val DONE = Copy("Import complete", "Your import is complete. You can now start sharing your file.")
private const val BACKDROP = "#F1F3F5"
private const val CARD = "#17161F"
private const val RING = "#2A2932"
private const val INK = "#FFFFFF"
private const val MUTE = "#A0A0A8"
private val DISC_BLACK = Color.Black
private const val BUTTON_DIM = .22f
private const val MARGIN = 24f
private const val CARD_MAX = 360f
private const val CARD_HEIGHT = 340f
private const val CARD_RADIUS = 8f
private const val CARD_LIFT = 12f
private const val ICON_Y = 102f
private const val RING_IDLE = 36f
private const val DISC_IDLE = 22f
private const val GLOW = 32f
private const val GLOW_BLUR = 12f
private const val DISC_IMPORT = 31f
private const val RING_IMPORT = 47.5f
private const val BAND = 12f
private const val DISC_DONE = 33f
private const val ARROW_IMPORT = 1.7f
private const val COPY_TOP = 176f
private const val COPY_WIDTH = 280f
private const val TITLE = 18f
private const val TITLE_LINE = 24f
private const val TEXT = 14f
private const val TEXT_LINE = 20f
private const val COPY_GAP = 6f
private const val HOVER_DROP = 18f
private const val BUTTON = 44f
private const val BUTTON_INSET = 16f
private const val BUTTON_RADIUS = 5f
private const val LABEL = 15f
private const val LABEL_LINE = 20f
private const val BLUR = 8f
private const val BUTTON_BLUR = 9f
private const val BUTTON_SMEAR = .34f
private const val SHEET_WIDTH = 60f
private const val SHEET_HEIGHT = 77f
private const val SHEET_FOLD = 16f
private const val TAG = 24f
private const val TAG_GAP = 4f
private const val TAG_TEXT = 11f
private const val TAG_MAX = 112f
private const val FILE_BOX = 168f
private const val FILE_TOP = 28f
private const val FILE_IN = 58f
private const val FILE_DOWN = .57f
private const val FILE_OUT = 70f
private const val FILE_LOW = 90f
private const val FILE_TILT = 16f
private const val FILE_BOB = 2.5f
const val LOOP = 9.2
private const val PRESS_AT = .8
private const val PRESS_FOR = .15
private const val DONE_HOLD = 1.25
private const val USER_HOLD = 3.0
private const val DURATION = 3.0
private const val HOVER_AT = .1
private const val RELEASE_AT = 1.25
private const val DROP_FOR = .3
private const val IMPORT_AT = 1.5
private const val RING_AT = IMPORT_AT + .2
private const val SWAP_OUT = .28
private const val SWAP_LAG = .04
private const val SWAP_IN = .22
private const val IN_BLUR = .5
private const val PULL_STEP = .045
private const val PULL_TRAVEL = .28
private const val DONE_AFTER = .45
private const val DONE_FOR = .4
private const val LEAVE = .55
private const val BURST = 5.5
private const val STREAK = .03
private const val STREAK_MAX = 9f
private const val BREATH = 2.0
private const val SWIRL = .7
private val DOTS = arrayOf(
    floatArrayOf(226f, 86f, 6f, .14f, 0f, 3f), floatArrayOf(304f, 80f, 10f, .12f, .05f, 6f),
    floatArrayOf(273f, 57f, 5f, .26f, .02f, 1f), floatArrayOf(224f, 59f, 3f, .24f, .08f, 9f),
    floatArrayOf(196f, 88f, 9f, .11f, .03f, 0f), floatArrayOf(189f, 65f, 3f, .2f, .1f, 11f),
    floatArrayOf(162f, 68f, 6f, .18f, .01f, 5f), floatArrayOf(140f, 61f, 3f, .22f, .06f, 2f),
    floatArrayOf(42f, 66f, 3f, .2f, .09f, 8f), floatArrayOf(19f, 82f, 9f, .12f, .04f, 4f),
    floatArrayOf(351f, 57f, 5f, .26f, 0f, 10f), floatArrayOf(9f, 52f, 3f, .3f, .07f, 7f),
    floatArrayOf(322f, 63f, 3.5f, .2f, .05f, 12f), floatArrayOf(343f, 80f, 2.5f, .13f, .11f, 13f),
)
private val PULL_FOR = (DOTS.size - 1) * PULL_STEP + PULL_TRAVEL
private const val STATES = 5
private const val BUTTON_IN_MS = 100
private const val BUTTON_OUT_MS = 150
private const val ZONE_IN_MS = 100
private const val ZONE_OUT_MS = 200
private const val RESUME_POLL_MS = 100L
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val MIN_PRESS_DURATION_MS = 130L
private const val SHADOW_SPILL = 180f
private const val LEGACY_DIM = .22f
private const val SHEET_SHADOW_Y = 8f
private const val SHEET_SHADOW_SIGMA = 10f
private const val SHEET_SHADOW_ALPHA = .28f
private const val TAG_SHADOW_Y = 3f
private const val TAG_SHADOW_SIGMA = 6f
private const val TAG_SHADOW_ALPHA = .22f
private const val TAG_RADIUS = 5f
private const val TAG_PADDING = 16f
private const val MARK_STROKE = 2.2f
private const val MARK_LIFT = 4f
private const val FILE_SHADOW_RED = 10
private const val FILE_SHADOW_GREEN = 10
private const val FILE_SHADOW_BLUE = 20
private val SHEET_COLORS = intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.rgb(233, 234, 238))
private val SHEET_FOLD_COLOR = android.graphics.Color.rgb(211, 213, 220)
private val MARK_COLOR = android.graphics.Color.rgb(205, 207, 216)
private const val ICON_CENTER_X = 110f
private const val IDLE_PRESS_SCALE = .06f
private const val LANDING_SWELL = .1f
private const val ARROW_STROKE = 4f
private const val PAGE_STROKE = 3f
private const val HOVER_CENTER_ALPHA = .62f
private val ARROW = PathParser().parsePathString("M0 8.8L0 -7.4M-6.8 -1.2L0 -8L6.8 -1.2").toPath()
private val CHECK = PathParser().parsePathString("M-11.5 1L-4 8.5L12 -8").toPath()
private val PAGE = PathParser().parsePathString("M-3.5 -16H8L14 -10V6.5Q14 10 10.5 10H-3.5Q-7 10 -7 6.5V-12.5Q-7 -16 -3.5 -16Z").toPath()
private val PAGE_BEHIND = PathParser().parsePathString("M-12.6 -9.5V12Q-12.6 15.8 -8.8 15.8H7.5").toPath()
private val QuadInOut = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val Linear = Easing { it }


private val SHADOW_SAMPLES = FloatArray(7) { (it - 3).toFloat() }
private val SHADOW_WEIGHTS = FloatArray(7) { exp(-SHADOW_SAMPLES[it] * SHADOW_SAMPLES[it] / 2f) }.let { values ->
    val sum = values.sum()
    FloatArray(values.size) { values[it] / sum }
}
private const val CARD_SHADOW_FAR_ALPHA = .34f
private const val CARD_SHADOW_NEAR_ALPHA = .2f
private const val CARD_SHADOW_FAR_Y = 44f
private const val CARD_SHADOW_NEAR_Y = 14f
private const val CARD_SHADOW_FAR_SIGMA = 45f
private const val CARD_SHADOW_NEAR_SIGMA = 15f
private const val HOVER_GLOW_ALPHA = .34f

private class CardShadow {
    private val far = SoftRoundRect(rgba(23, 22, 31, CARD_SHADOW_FAR_ALPHA), CARD_SHADOW_FAR_SIGMA)
    private val near = SoftRoundRect(rgba(23, 22, 31, CARD_SHADOW_NEAR_ALPHA), CARD_SHADOW_NEAR_SIGMA)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(23, 22, 31)
    }
    fun draw(scope: DrawScope, x: Float, y: Float, width: Float) {
        scope.drawIntoCanvas { target ->
            val canvas = target.nativeCanvas
            if (Build.VERSION.SDK_INT >= 28) {
                far.draw(canvas, x, y + CARD_SHADOW_FAR_Y, width, CARD_HEIGHT, CARD_RADIUS)
                near.draw(canvas, x, y + CARD_SHADOW_NEAR_Y, width, CARD_HEIGHT, CARD_RADIUS)
            } else {
                fallback(canvas, x, y + CARD_SHADOW_FAR_Y, width, CARD_SHADOW_FAR_SIGMA, CARD_SHADOW_FAR_ALPHA)
                fallback(canvas, x, y + CARD_SHADOW_NEAR_Y, width, CARD_SHADOW_NEAR_SIGMA, CARD_SHADOW_NEAR_ALPHA)
            }
        }
    }
    private fun fallback(canvas: android.graphics.Canvas, x: Float, y: Float, width: Float, sigma: Float, alpha: Float) {
        for (vertical in SHADOW_SAMPLES.indices) for (horizontal in SHADOW_SAMPLES.indices) {
            val left = x + SHADOW_SAMPLES[horizontal] * sigma
            val top = y + SHADOW_SAMPLES[vertical] * sigma
            paint.alpha = (alpha * SHADOW_WEIGHTS[horizontal] * SHADOW_WEIGHTS[vertical] * 255f).roundToInt()
            canvas.drawRoundRect(left, top, left + width, top + CARD_HEIGHT, CARD_RADIUS, CARD_RADIUS, paint)
        }
    }
}

private class HoverGlow(ink: Color) {
    private val alpha = ink.alpha * HOVER_GLOW_ALPHA
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(
            (ink.red * 255f).roundToInt(), (ink.green * 255f).roundToInt(), (ink.blue * 255f).roundToInt(),
        )
        if (Build.VERSION.SDK_INT >= 28) {
            maskFilter = android.graphics.BlurMaskFilter(sigmaToRadius(GLOW_BLUR), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    fun draw(scope: DrawScope, cx: Float, cy: Float) {
        scope.drawIntoCanvas { target ->
            val canvas = target.nativeCanvas
            if (Build.VERSION.SDK_INT >= 28) {
                paint.alpha = (alpha * 255f).roundToInt()
                canvas.drawCircle(cx, cy, GLOW, paint)
            } else {
                for (vertical in SHADOW_SAMPLES.indices) for (horizontal in SHADOW_SAMPLES.indices) {
                    paint.alpha = (alpha * SHADOW_WEIGHTS[horizontal] * SHADOW_WEIGHTS[vertical] * 255f).roundToInt()
                    canvas.drawCircle(
                        cx + SHADOW_SAMPLES[horizontal] * GLOW_BLUR,
                        cy + SHADOW_SAMPLES[vertical] * GLOW_BLUR, GLOW, paint,
                    )
                }
            }
        }
    }
}



@Composable
private fun OrbitIcons(
    bounds: Bounds,
    levels: State<FloatArray>,
    elapsed: Clock,
    leave: Clock,
    duration: Double,
    zoneDown: Clock,
    still: Boolean,
    cardInk: Color,
    ringInk: Color,
    textInk: Color,
    hoverGlow: HoverGlow,
) {
    val checkMeasure = remember { PathMeasure().apply { setPath(CHECK, false) } }
    val checkPart = remember { Path() }
    val hoverBrush = remember {
        Brush.radialGradient(
            colors = listOf(rgba(255, 255, 255, HOVER_CENTER_ALPHA), rgba(255, 255, 255, HOVER_GLOW_ALPHA)),
            center = Offset(ICON_CENTER_X, ICON_Y), radius = GLOW,
        )
    }
    val center = Offset(ICON_CENTER_X, ICON_Y)
    OrbitLayer(bounds, levels, 0) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                val scale = 1f - IDLE_PRESS_SCALE * zoneDown.value.toFloat()
                withTransform({ scale(scale, scale, center) }) {
                    drawCircle(ringInk, RING_IDLE, center)
                    drawCircle(textInk, DISC_IDLE, center)
                    withTransform({ translate(center.x, center.y) }) {
                        drawPath(ARROW, cardInk, style = Stroke(ARROW_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
    OrbitLayer(bounds, levels, 1) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                val landing = RELEASE_AT + DROP_FOR * .7
                val scale = if (still) 1f else (
                    1.0 + LANDING_SWELL * smooth(elapsed.value, landing - .12, landing)
                        * (1.0 - smooth(elapsed.value, landing, landing + .2))
                    ).toFloat()
                withTransform({ scale(scale, scale, center) }) {
                    hoverGlow.draw(this, center.x, center.y)
                    drawCircle(hoverBrush, GLOW, center)
                    withTransform({ translate(center.x, center.y) }) {
                        drawPath(PAGE_BEHIND, textInk, style = Stroke(PAGE_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(PAGE, textInk)
                    }
                }
            }
        }
    }
    OrbitLayer(bounds, levels, 4) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, _, _ ->
                val time = elapsed.value
                val band = bandAt(time, duration) * (1.0 - leave.value)
                val turned = smooth(time, doneAt(duration), doneAt(duration) + DONE_FOR)
                val ringTime = time - RING_AT
                val ringRadius = (DISC_IMPORT + (RING_IMPORT - DISC_IMPORT)
                    * (if (still) smooth(ringTime, 0.0, .2) else spring(ringTime, 9.0, 11.0))).toFloat()
                val ringFlash = (.1 * smooth(time, RING_AT, RING_AT + .08)
                    * (1.0 - smooth(time, RING_AT + .1, RING_AT + .6))).toFloat()
                val ringOpacity = (1.0 - smooth(band, BAND * .7, BAND.toDouble())).toFloat()
                val settleTime = time - doneAt(duration) - DONE_FOR * .25
                val settle = if (still) smooth(settleTime, 0.0, DONE_FOR * .75) else spring(settleTime, 8.0, 9.0)
                val whiteRadius = (DISC_IMPORT + band - (DISC_IMPORT + BAND - DISC_DONE) * settle).toFloat()
                val whiteOpacity = smooth(band, 0.0, .6).toFloat()
                val gray = (255.0 * smooth(turned, 0.0, .75)).roundToInt()
                val arrowOpacity = (1.0 - smooth(turned, .15, .6)).toFloat()
                val checkEnd = smooth(turned, .45, 1.0).toFloat()
                drawCircle(ringInk, ringRadius, center, alpha = ringOpacity)
                drawCircle(textInk, ringRadius, center, alpha = ringFlash)
                drawCircle(textInk, whiteRadius, center, alpha = whiteOpacity)
                drawCircle(rgba(gray, gray, gray, 1f), DISC_IMPORT, center)
                withTransform({ translate(center.x, center.y) }) {
                    withTransform({ scale(ARROW_IMPORT, ARROW_IMPORT, Offset.Zero) }) {
                        drawPath(ARROW, textInk, alpha = arrowOpacity,
                            style = Stroke(ARROW_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    checkPart.reset()
                    checkMeasure.getSegment(0f, checkMeasure.length * checkEnd, checkPart, true)
                    drawPath(checkPart, DISC_BLACK, style = Stroke(ARROW_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

private class FileArt {
    private val sheet = PathParser().parsePathString(
        "M5 0H55Q60 0 60 5V61L44 77H5Q0 77 0 72V5Q0 0 5 0Z",
    ).toPath().asAndroidPath()
    private val fold = PathParser().parsePathString("M44 77V65Q44 61 48 61H60Z").toPath().asAndroidPath()
    private val mark = PathParser().parsePathString("M-9 5L-3.5 -3.5L0 1.5L3.5 -5L9 5").toPath().asAndroidPath()
    private val paperPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, SHEET_WIDTH, SHEET_HEIGHT,
            SHEET_COLORS,
            null, android.graphics.Shader.TileMode.CLAMP,
        )
    }
    private val foldPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = SHEET_FOLD_COLOR
    }
    private val markPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = MARK_COLOR
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = MARK_STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val tagPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    private val sheetShadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((SHEET_SHADOW_ALPHA * 255f).roundToInt(), FILE_SHADOW_RED, FILE_SHADOW_GREEN, FILE_SHADOW_BLUE)
        if (Build.VERSION.SDK_INT >= 28) {
            maskFilter = android.graphics.BlurMaskFilter(sigmaToRadius(SHEET_SHADOW_SIGMA), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val tagShadow = SoftRoundRect(rgba(FILE_SHADOW_RED, FILE_SHADOW_GREEN, FILE_SHADOW_BLUE, TAG_SHADOW_ALPHA), TAG_SHADOW_SIGMA)
    private val fallbackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(FILE_SHADOW_RED, FILE_SHADOW_GREEN, FILE_SHADOW_BLUE)
    }

    fun draw(scope: DrawScope, tagWidth: Float) {
        scope.drawIntoCanvas { target ->
            val canvas = target.nativeCanvas
            val sheetX = (FILE_BOX - SHEET_WIDTH) / 2f
            val tagX = (FILE_BOX - tagWidth) / 2f
            val tagY = FILE_TOP + SHEET_HEIGHT + TAG_GAP
            canvas.save()
            canvas.translate(sheetX, FILE_TOP + SHEET_SHADOW_Y)
            if (Build.VERSION.SDK_INT >= 28) {
                canvas.drawPath(sheet, sheetShadowPaint)
            } else {
                for (y in SHADOW_SAMPLES.indices) for (x in SHADOW_SAMPLES.indices) {
                    canvas.save()
                    canvas.translate(SHADOW_SAMPLES[x] * SHEET_SHADOW_SIGMA, SHADOW_SAMPLES[y] * SHEET_SHADOW_SIGMA)
                    fallbackPaint.alpha = (SHEET_SHADOW_ALPHA * SHADOW_WEIGHTS[x] * SHADOW_WEIGHTS[y] * 255f).roundToInt()
                    canvas.drawPath(sheet, fallbackPaint)
                    canvas.restore()
                }
            }
            canvas.restore()
            canvas.save()
            canvas.translate(sheetX, FILE_TOP)
            canvas.drawPath(sheet, paperPaint)
            canvas.drawPath(fold, foldPaint)
            canvas.translate(SHEET_WIDTH / 2f, SHEET_HEIGHT / 2f - MARK_LIFT)
            canvas.drawPath(mark, markPaint)
            canvas.restore()
            if (Build.VERSION.SDK_INT >= 28) {
                tagShadow.draw(canvas, tagX, tagY + TAG_SHADOW_Y, tagWidth, TAG, TAG_RADIUS)
            } else {
                for (y in SHADOW_SAMPLES.indices) for (x in SHADOW_SAMPLES.indices) {
                    val left = tagX + SHADOW_SAMPLES[x] * TAG_SHADOW_SIGMA
                    val top = tagY + TAG_SHADOW_Y + SHADOW_SAMPLES[y] * TAG_SHADOW_SIGMA
                    fallbackPaint.alpha = (TAG_SHADOW_ALPHA * SHADOW_WEIGHTS[x] * SHADOW_WEIGHTS[y] * 255f).roundToInt()
                    canvas.drawRoundRect(left, top, left + tagWidth, top + TAG, TAG_RADIUS, TAG_RADIUS, fallbackPaint)
                }
            }
            canvas.drawRoundRect(tagX, tagY, tagX + tagWidth, tagY + TAG, TAG_RADIUS, TAG_RADIUS, tagPaint)
        }
    }
}


private fun smooth(t: Double, from: Double, to: Double): Double { val u = ((t - from) / (to - from)).coerceIn(0.0, 1.0); return u * u * (3.0 - 2.0 * u) }
private fun spring(t: Double, damping: Double, frequency: Double) = if (t <= 0.0) 0.0 else 1.0 - exp(-damping * t) * cos(frequency * t)
private fun doneAt(duration: Double) = IMPORT_AT + duration + DONE_AFTER
private fun endAt(duration: Double) = doneAt(duration) + DONE_FOR + .2
private fun stageAt(e: Double, duration: Double) = if (e < 0.0) 0 else if (e < IMPORT_AT) 1 else if (e < doneAt(duration)) 2 else 3
private fun levelsAt(e: Double, leave: Double, duration: Double): FloatArray {
    val done = doneAt(duration); val out = smooth(leave, 0.0, .6); val back = smooth(leave, .3, 1.0)
    val hoverIn = smooth(e, HOVER_AT + SWAP_LAG, HOVER_AT + SWAP_LAG + SWAP_IN)
    val importIn = smooth(e, IMPORT_AT + SWAP_LAG, IMPORT_AT + SWAP_LAG + SWAP_IN)
    val doneIn = smooth(e, done + SWAP_LAG, done + SWAP_LAG + SWAP_IN)
    val idleOut = smooth(e, HOVER_AT, HOVER_AT + SWAP_OUT); val hoverOut = smooth(e, IMPORT_AT, IMPORT_AT + SWAP_OUT); val importOut = smooth(e, done, done + SWAP_OUT)
    return doubleArrayOf(1.0 - idleOut * (1.0 - back), hoverIn * (1.0 - hoverOut) * (1.0 - out), importIn * (1.0 - importOut) * (1.0 - out), doneIn * (1.0 - out), importIn * (1.0 - out), if (leave > 0.0) IN_BLUR * idleOut * (1.0 - back) else idleOut, max(IN_BLUR * (1.0 - hoverIn), max(hoverOut, out)), max(IN_BLUR * (1.0 - importIn), max(importOut, out)), max(IN_BLUR * (1.0 - doneIn), out), max(IN_BLUR * (1.0 - importIn), out)).map { it.toFloat() }.toFloatArray()
}
private fun bandAt(e: Double, duration: Double): Float {
    val pull = IMPORT_AT + duration - PULL_FOR
    var landed = 0.0
    for (dot in DOTS) { val lands = pull + dot[5] * PULL_STEP + PULL_TRAVEL; landed += smooth(e, lands - .08, lands + .1) }
    return (BAND * landed / DOTS.size).toFloat()
}
private fun dotAt(index: Int, e: Double, leave: Double, duration: Double, still: Boolean): FloatArray {
    val dot = DOTS[index]; val degrees = dot[0]; val orbit = dot[1]; val size = dot[2]; val speed = dot[3]; val delay = dot[4]; val order = dot[5]
    val t = e - RING_AT - delay
    if (t <= 0.0) return FloatArray(6)
    val thrown = if (still) 1.0 else 1.0 - exp(-BURST * t)
    val floating = DISC_IMPORT + (orbit - DISC_IMPORT) * thrown + if (still) 0.0 else BREATH * sin(t * 1.3 + index)
    val u = ((e - (IMPORT_AT + duration - PULL_FOR) - order * PULL_STEP) / PULL_TRAVEL).coerceIn(0.0, 1.0)
    val fall = if (still) 0.0 else u * u * u; val cancel = if (still) 0.0 else leave * leave
    val radius = (floating + (DISC_IMPORT + 2f - floating) * fall) * (1.0 - cancel) + (DISC_IMPORT + 2f) * cancel
    val angle = degrees * PI / 180.0 + if (still) 0.0 else speed * t + SWIRL * fall
    val velocity = if (still) 0.0 else (orbit - DISC_IMPORT) * BURST * (1.0 - thrown) + (floating - DISC_IMPORT) * 3.0 * u * u * (if (u < 1.0) 1.0 else 0.0) / PULL_TRAVEL
    val long = size + min(STREAK_MAX.toDouble(), velocity * STREAK); val wide = size * max(.7, sqrt(size / long))
    val alpha = smooth(t, 0.0, if (still) .3 else .06) * (1.0 - smooth(u, if (still) 0.0 else .8, 1.0)) * (1.0 - smooth(leave, 0.0, .7))
    return floatArrayOf((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat(), angle.toFloat(), (long / 2.0).toFloat(), (wide / 2.0).toFloat(), alpha.toFloat())
}
private fun fileAt(e: Double, leave: Double, still: Boolean, screenWidth: Float, left: Float, width: Float): FloatArray {
    if (still) return floatArrayOf(0f, 0f, 0f, 1f, (smooth(e, 0.0, .25) * (1.0 - smooth(e, RELEASE_AT, RELEASE_AT + DROP_FOR)) * (1.0 - leave)).toFloat(), 0f)
    val fromX = screenWidth + FILE_OUT - (left + width - FILE_IN); val toIconX = width / 2f - (width - FILE_IN); val toIconY = ICON_Y - CARD_HEIGHT * FILE_DOWN
    val away = 1.0 - spring(e, 5.5, 6.5) * (1.0 - leave * leave); val held = smooth(e, .5, .9)
    val u = ((e - RELEASE_AT) / DROP_FOR).coerceIn(0.0, 1.0); val drop = u * u * (3.0 - 2.0 * u)
    return floatArrayOf((fromX * away + toIconX * drop).toFloat(), (FILE_LOW * away + FILE_BOB * held * sin((e - .5) * 3.2) * (1.0 - drop) + toIconY * drop).toFloat(), (FILE_TILT * away + 1.2 * held * sin(e * 2.6) * (1.0 - drop)).toFloat(), (1.0 - .8 * drop).toFloat(), if (e < 0.0) 0f else (1.0 - smooth(u, .6, 1.0)).toFloat(), (BLUR * drop).toFloat())
}
private fun color(value: String): Color = OrbitUploadDropzoneCSS.color(value)
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
private fun fixedStyle(size: Float, line: Float, weight: Int, color: Color, spacing: Float, fontScale: Float) = TextStyle(fontFamily = FontFamily.SansSerif, color = color, fontSize = (size / fontScale).sp, lineHeight = (line / fontScale).sp, letterSpacing = (spacing / fontScale).sp, fontWeight = FontWeight(weight), textAlign = TextAlign.Center, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false))
private class Clock(initial: Double) : State<Double> {
    private val state = mutableStateOf(initial)
    override val value get() = state.value
    private var target = initial
    private var from = initial
    private var origin = 0L
    private var duration = 0.0
    private var curve: Easing = QuadInOut
    private var running = false
    private var completion: (() -> Unit)? = null
    fun cancel() { running = false; completion = null }
    fun direct(next: Double) { cancel(); state.value = next; target = next }
    fun time(next: Double, ms: Double, easing: Easing = QuadInOut, still: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (still || value == next || ms <= 0.0) { direct(next); onFinished?.invoke(); return }
        if (running && target == next) { duration = ms * 1e6; curve = easing; completion = onFinished; return }
        target = next; from = value; origin = System.nanoTime(); duration = ms * 1e6; curve = easing; running = true; completion = onFinished
    }
    fun advance(now: Long) {
        if (!running) return
        val u = ((now - origin) / duration).coerceIn(0.0, 1.0)
        state.value = from + (target - from) * (if (curve === Linear) u else curve.transform(u.toFloat()).toDouble())
        if (u >= 1.0) { running = false; val finished = completion; completion = null; finished?.invoke() }
    }
}

private data class AutoEvent(val at: Long, val order: Long, val act: () -> Unit)
private data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float)

/** A chosen file flies into a card whose orbiting particles close its import ring. */
@Composable
fun OrbitUploadDropzone(
    fileName: String = "brand_guide.pdf",
    duration: Double = DURATION,
    autoPlay: Boolean = false,
    idle: Copy = IDLE,
    hover: Copy = HOVER,
    importing: Copy = IMPORTING,
    done: Copy = DONE,
    chooseLabel: String = "Choose file",
    cancelLabel: String = "Cancel",
    backdrop: String = BACKDROP,
    cardColor: String = CARD,
    ringColor: String = RING,
    ink: String = INK,
    mute: String = MUTE,
    onPick: ((String) -> Unit)? = null,
    onImport: ((String) -> Unit)? = null,
    onComplete: ((String) -> Unit)? = null,
    onCancel: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val currentDuration by rememberUpdatedState(duration)
    val currentName by rememberUpdatedState(fileName)
    val picked by rememberUpdatedState(onPick)
    val imported by rememberUpdatedState(onImport)
    val completed by rememberUpdatedState(onComplete)
    val cancelled by rememberUpdatedState(onCancel)
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    val controlScope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val textInk = remember(ink) { color(ink) }
    val textMute = remember(mute) { color(mute) }
    val cardInk = remember(cardColor) { color(cardColor) }
    val ringInk = remember(ringColor) { color(ringColor) }
    val titleStyle = fixedStyle(TITLE, TITLE_LINE, 500, textInk, -.3f, displayDensity.fontScale)
    val bodyStyle = fixedStyle(TEXT, TEXT_LINE, 400, textMute, -.1f, displayDensity.fontScale)
    val labelStyle = fixedStyle(LABEL, LABEL_LINE, 500, cardInk, -.2f, displayDensity.fontScale)
    val tagStyle = fixedStyle(TAG_TEXT, TAG, 500, color(CARD), 0f, displayDensity.fontScale)
    val tagMeasure = remember(fileName, displayDensity) { measurer.measure(AnnotatedString(fileName), tagStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (TAG_MAX * displayDensity.density).roundToInt())) }
    val tagLongest = (0 until tagMeasure.lineCount).maxOfOrNull { tagMeasure.getLineRight(it) - tagMeasure.getLineLeft(it) } ?: 0f
    val tagWidth = ceil(tagLongest / displayDensity.density) + TAG_PADDING
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    var stage by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var autoPressed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var silent by remember { mutableStateOf(false) }
    val elapsed = remember { Clock(-1.0) }
    val leave = remember { Clock(0.0) }
    val pendingReturn = remember { ReturnState() }
    val buttonDown = remember { Clock(0.0) }
    val zoneDown = remember { Clock(0.0) }
    val fileArt = remember { FileArt() }
    val cardShadow = remember { CardShadow() }
    val hoverGlow = remember(textInk) { HoverGlow(textInk) }
    fun feedback(kind: Int) { view.performHapticFeedback(when (kind) { 2 -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY; 1 -> HapticFeedbackConstants.LONG_PRESS; else -> HapticFeedbackConstants.VIRTUAL_KEY }) }
    fun rest() { elapsed.direct(-1.0); leave.direct(0.0); stage = 0; leaving = false }
    fun start(auto: Boolean) {
        if (stage != 0 || leaving) return
        val end = endAt(currentDuration)
        silent = auto
        if (!auto) feedback(0)
        picked?.invoke(currentName)
        elapsed.direct(0.0); elapsed.time(end, end * 1000.0, Linear)
    }
    fun back(auto: Boolean) {
        if (stage == 0 || leaving) return
        leaving = true; elapsed.cancel()
        if (stage < 3) cancelled?.invoke(currentName)
        if (!auto) feedback(0)
        leave.time(1.0, LEAVE * 1000.0, Linear) { pendingReturn.ready = true }
    }
    fun touched() { autoPressed = false; touches++ }
    val latestStart by rememberUpdatedState(::start)
    val latestBack by rememberUpdatedState(::back)
    LaunchedEffect(autoPressed) { buttonDown.time(if (autoPressed) 1.0 else 0.0, BUTTON_IN_MS.toDouble(), still = reduced) }
    LaunchedEffect(Unit) {
        var old: Int? = null
        while (true) withFrameNanos { now ->
            listOf(elapsed, leave, buttonDown, zoneDown).forEach { it.advance(now) }
            val next = stageAt(elapsed.value, currentDuration)
            if (old != null && next != old) {
                stage = next
                if (next == 2) { if (!silent) feedback(1); imported?.invoke(currentName) }
                if (next == 3) { if (!silent) feedback(2); completed?.invoke(currentName) }
            }
            old = next
            if (pendingReturn.ready) { pendingReturn.ready = false; rest() }
        }
    }
    val period = LOOP + duration - DURATION
    LaunchedEffect(autoPlay, touches, duration, period) {
        if (!autoPlay || !duration.isFinite() || period <= 0.0) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (true) {
                if (stage == 3) latestBack(true)
                if (stage == 0 && !leaving) break
                delay(RESUME_POLL_MS)
            }
        }
        val queue = mutableListOf<AutoEvent>()
        var serial = 0L
        fun after(origin: Long, seconds: Double, act: () -> Unit) { queue += AutoEvent(max(System.nanoTime(), origin + (seconds * 1e9).toLong()), serial++, act) }
        fun cycle(origin: Long) {
            val pickedAt = PRESS_AT + PRESS_FOR
            after(origin, PRESS_AT) { autoPressed = true }
            after(origin, pickedAt) { autoPressed = false; latestStart(true) }
            after(origin, pickedAt + doneAt(duration) + DONE_FOR + DONE_HOLD) { latestBack(true) }
            after(origin, period) { cycle(origin + (period * 1e9).toLong()) }
        }
        cycle(System.nanoTime())
        while (queue.isNotEmpty()) {
            val event = queue.minWith(compareBy<AutoEvent> { it.at }.thenBy { it.order })
            val waiting = event.at - System.nanoTime()
            if (waiting > 0L) delay((waiting + 999_999L) / 1_000_000L)
            queue.remove(event); event.act(); yield()
        }
    }
    val levels = remember(duration) { androidx.compose.runtime.derivedStateOf { levelsAt(elapsed.value, leave.value, duration) } }
    val copies = listOf(idle, hover, importing, done)
    val shown = copies[stage]
    BoxWithConstraints(modifier.fillMaxSize().background(color(backdrop))) {
        val screenWidth = maxWidth.value; val width = min(CARD_MAX, screenWidth - MARGIN * 2f).coerceAtLeast(1f)
        val left = (screenWidth - width) / 2f
        val top = max(safe.calculateTopPadding().value + MARGIN, (maxHeight.value - CARD_HEIGHT) / 2f - CARD_LIFT)
        val buttonTop = CARD_HEIGHT - BUTTON_INSET - BUTTON
        val iconBounds = Bounds(width / 2f - 110f, 0f, 220f, ICON_Y + 80f)
        val copyBounds = Bounds(0f, COPY_TOP - 16f, width, buttonTop - COPY_TOP + 16f)
        val buttonBounds = Bounds(0f, buttonTop - 28f, width, BUTTON + BUTTON_INSET + 28f)
        Box(Modifier.offset(x = left.dp, y = top.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(width.dp, CARD_HEIGHT.dp)) {
            Canvas(Modifier.fillMaxSize().bleed(SHADOW_SPILL.dp).clearAndSetSemantics {}) { surface(SHADOW_SPILL.dp) { x, y, _, _ -> cardShadow.draw(this, x, y, width) } }
            Box(Modifier.fillMaxSize().background(cardInk, RoundedCornerShape(CARD_RADIUS.dp)).clip(RoundedCornerShape(CARD_RADIUS.dp))) {
                OrbitIcons(iconBounds, levels, elapsed, leave, duration, zoneDown, still, cardInk, ringInk, textInk, hoverGlow)
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    surface(0.dp) { _, _, _, _ ->
                        for (index in DOTS.indices) {
                            val dot = dotAt(index, elapsed.value, leave.value, duration, still)
                            withTransform({ translate(width / 2f + dot[0], ICON_Y + dot[1]); rotate(dot[2] * 180f / PI.toFloat(), Offset.Zero); scale(dot[3], dot[4], Offset.Zero) }) { drawCircle(textInk, 1f, Offset.Zero, alpha = dot[5]) }
                        }
                    }
                }
                for ((index, copy) in copies.withIndex()) OrbitLayer(copyBounds, levels, index) {
                    val copyX = (width - COPY_WIDTH) / 2f
                    val copyY = COPY_TOP + if (index == 1) HOVER_DROP else 0f
                    BasicText(copy.title, Modifier.offset(x = copyX.dp, y = (copyY - copyBounds.y).dp).wrapContentSize(Alignment.TopStart, unbounded = true).width(COPY_WIDTH.dp).clearAndSetSemantics {}, style = titleStyle)
                    BasicText(copy.text, Modifier.offset(x = copyX.dp, y = (copyY - copyBounds.y + TITLE_LINE + COPY_GAP).dp).wrapContentSize(Alignment.TopStart, unbounded = true).width(COPY_WIDTH.dp).clearAndSetSemantics {}, style = bodyStyle)
                }
                OrbitButton(buttonBounds, width, elapsed, leave, duration, buttonDown, chooseLabel, cancelLabel, labelStyle)
            }
            Control("${shown.title}. ${shown.text}", Modifier.fillMaxSize(), controlScope,
                { zoneDown.time(1.0, ZONE_IN_MS.toDouble(), still = reduced) }, { zoneDown.time(0.0, ZONE_OUT_MS.toDouble(), still = reduced) }) {
                touched(); if (stage == 0) start(false) else if (stage == 3) back(false)
            }
            if (stage < 3) Control(if (stage == 0) chooseLabel else cancelLabel, Modifier.offset(x = BUTTON_INSET.dp, y = buttonTop.dp).size(max(1f, width - BUTTON_INSET * 2f).dp, BUTTON.dp), controlScope,
                { buttonDown.time(1.0, BUTTON_IN_MS.toDouble(), still = reduced) }, { buttonDown.time(0.0, BUTTON_OUT_MS.toDouble(), still = reduced) }) {
                touched(); if (stage == 0) start(false) else back(false)
            }
        }
        Box(Modifier.offset(x = (left + width - FILE_IN - FILE_BOX / 2f).dp, y = (top + CARD_HEIGHT * FILE_DOWN - FILE_TOP - SHEET_HEIGHT / 2f).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(FILE_BOX.dp).graphicsLayer {
            val file = fileAt(elapsed.value, leave.value, still, screenWidth, left, width)
            translationX = file[0] * density; translationY = file[1] * density; rotationZ = file[2]; scaleX = file[3]; scaleY = file[3]; alpha = file[4] * (if (Build.VERSION.SDK_INT < 31) 1f - LEGACY_DIM * file[5] / BUTTON_BLUR else 1f)
            val sigma = file[5] * density
            renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0f) BlurEffect(sigmaToRadius(sigma), sigmaToRadius(sigma), TileMode.Decal) else null
            compositingStrategy = CompositingStrategy.Offscreen
        }.clipToBounds().clearAndSetSemantics {}) {
            Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, _, _ -> fileArt.draw(this, tagWidth) } }
            BasicText(fileName, Modifier.offset(x = ((FILE_BOX - TAG_MAX) / 2f).dp, y = (FILE_TOP + SHEET_HEIGHT + TAG_GAP).dp).width(TAG_MAX.dp).height(TAG.dp), style = tagStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BlurGroup(bounds: Bounds, opacity: () -> Float, blur: () -> Float, content: @Composable () -> Unit) {
    Box(Modifier.offset(x = bounds.x.dp, y = bounds.y.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(bounds.width.dp, bounds.height.dp).clipToBounds().clearAndSetSemantics {}) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val sigmaDp = blur(); val sigma = sigmaDp * density
            alpha = opacity() * (if (Build.VERSION.SDK_INT < 31) 1f - LEGACY_DIM * (sigmaDp / BUTTON_BLUR).coerceIn(0f, 1f) else 1f)
            renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0f) BlurEffect(sigmaToRadius(sigma), sigmaToRadius(sigma), TileMode.Decal) else null
            compositingStrategy = CompositingStrategy.Offscreen
        }) { content() }
    }
}

@Composable
private fun OrbitLayer(bounds: Bounds, levels: State<FloatArray>, index: Int, content: @Composable () -> Unit) {
    BlurGroup(bounds, { levels.value[index] }, { BLUR * levels.value[index + STATES] }, content)
}

private fun smearAt(e: Double, leave: Double) = smooth(e, HOVER_AT, HOVER_AT + SWAP_OUT) * (1.0 - smooth(e, IMPORT_AT + SWAP_LAG, IMPORT_AT + SWAP_LAG + SWAP_IN)) * (1.0 - smooth(leave, 0.0, .6))
@Composable
private fun OrbitButton(bounds: Bounds, width: Float, elapsed: Clock, leave: Clock, duration: Double, down: Clock, choose: String, cancel: String, style: TextStyle) {
    BlurGroup(bounds, {
        val gone = smooth(elapsed.value, doneAt(duration), doneAt(duration) + SWAP_OUT) * (1.0 - smooth(leave.value, .3, 1.0))
        ((1.0 - (1f - BUTTON_SMEAR) * smearAt(elapsed.value, leave.value)) * (1.0 - gone)).toFloat()
    }, { (BUTTON_BLUR * smearAt(elapsed.value, leave.value)).toFloat() }) {
        val buttonY = CARD_HEIGHT - BUTTON_INSET - BUTTON - bounds.y
        val labelY = buttonY + (BUTTON - LABEL_LINE) / 2f
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                val smear = smearAt(elapsed.value, leave.value).toFloat()
                val v = (255.0 * (1.0 - BUTTON_DIM * down.value)).roundToInt()
                drawRoundRect(rgba(v, v, v, 1f), Offset(BUTTON_INSET * (1f - smear), buttonY), Size(width - 2f * BUTTON_INSET * (1f - smear), BUTTON), CornerRadius(BUTTON_RADIUS))
            }
        }
        BasicText(choose, Modifier.offset(x = BUTTON_INSET.dp, y = labelY.dp).width(max(1f, width - BUTTON_INSET * 2f).dp).height(LABEL_LINE.dp).graphicsLayer {
            val shown = 1.0 - smooth(elapsed.value, HOVER_AT, HOVER_AT + .15)
            alpha = (shown + (1.0 - shown) * smooth(leave.value, .4, 1.0)).toFloat()
        }, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(cancel, Modifier.offset(x = BUTTON_INSET.dp, y = labelY.dp).width(max(1f, width - BUTTON_INSET * 2f).dp).height(LABEL_LINE.dp).graphicsLayer {
            alpha = (smooth(elapsed.value, HOVER_AT + .1, HOVER_AT + .3) * (1.0 - smooth(leave.value, 0.0, .5))).toFloat()
        }, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private class PressState { var latestOut: Job? = null; var alive = true }
private class ReturnState { var ready = false }

/** Pressability keeps the fixed hit frame and permits leaving and re-entering its press rectangle. */
@Composable
private fun Control(label: String, modifier: Modifier, scope: CoroutineScope, pressIn: () -> Unit, pressOut: () -> Unit, act: () -> Unit) {
    val view = LocalView.current
    val state = remember { PressState() }
    val currentIn by rememberUpdatedState(pressIn)
    val currentOut by rememberUpdatedState(pressOut)
    val currentAct by rememberUpdatedState(act)
    DisposableEffect(state) { state.alive = true; onDispose { state.alive = false; state.latestOut?.cancel() } }
    Box(modifier.semantics { role = Role.Button; contentDescription = label; onClick { currentAct(); true } }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.latestOut?.cancel(); state.latestOut = null
            var active = true
            var activatedAt: Long? = null
            fun activate() { activatedAt = System.nanoTime(); currentIn() }
            fun deactivate() {
                val remaining = max(0L, MIN_PRESS_DURATION_MS * 1_000_000L - (System.nanoTime() - (activatedAt ?: 0L)))
                activatedAt = null
                if (remaining == 0L) currentOut()
                else state.latestOut = scope.launch { delay((remaining + 999_999L) / 1_000_000L); currentOut() }
            }
            activate(); down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent(); val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (finger.isConsumed) break
                    if (!finger.pressed) {
                        if (active) { deactivate(); active = false; view.playSoundEffect(SoundEffectConstants.CLICK); currentAct() }
                        finger.consume(); break
                    }
                    val inside = finger.position.x > -PRESS_RETENTION * density && finger.position.x < size.width + PRESS_RETENTION * density && finger.position.y > -PRESS_RETENTION * density && finger.position.y < size.height + PRESS_RETENTION_BOTTOM * density
                    if (inside != active) { active = inside; if (inside) activate() else deactivate() }
                    finger.consume()
                }
                if (active) { active = false; deactivate() }
            } catch (cancelled: CancellationException) {
                if (active) scope.launch { yield(); if (state.alive) deactivate() }
                throw cancelled
            }
        }
    })
}

private object OrbitUploadDropzoneCSS {
    private val names: Map<String, Long> = mapOf(
        "transparent" to 0x00000000L,
        "aliceblue" to 0xf0f8ffffL,
        "antiquewhite" to 0xfaebd7ffL,
        "aqua" to 0x00ffffffL,
        "aquamarine" to 0x7fffd4ffL,
        "azure" to 0xf0ffffffL,
        "beige" to 0xf5f5dcffL,
        "bisque" to 0xffe4c4ffL,
        "black" to 0x000000ffL,
        "blanchedalmond" to 0xffebcdffL,
        "blue" to 0x0000ffffL,
        "blueviolet" to 0x8a2be2ffL,
        "brown" to 0xa52a2affL,
        "burlywood" to 0xdeb887ffL,
        "burntsienna" to 0xea7e5dffL,
        "cadetblue" to 0x5f9ea0ffL,
        "chartreuse" to 0x7fff00ffL,
        "chocolate" to 0xd2691effL,
        "coral" to 0xff7f50ffL,
        "cornflowerblue" to 0x6495edffL,
        "cornsilk" to 0xfff8dcffL,
        "crimson" to 0xdc143cffL,
        "cyan" to 0x00ffffffL,
        "darkblue" to 0x00008bffL,
        "darkcyan" to 0x008b8bffL,
        "darkgoldenrod" to 0xb8860bffL,
        "darkgray" to 0xa9a9a9ffL,
        "darkgreen" to 0x006400ffL,
        "darkgrey" to 0xa9a9a9ffL,
        "darkkhaki" to 0xbdb76bffL,
        "darkmagenta" to 0x8b008bffL,
        "darkolivegreen" to 0x556b2fffL,
        "darkorange" to 0xff8c00ffL,
        "darkorchid" to 0x9932ccffL,
        "darkred" to 0x8b0000ffL,
        "darksalmon" to 0xe9967affL,
        "darkseagreen" to 0x8fbc8fffL,
        "darkslateblue" to 0x483d8bffL,
        "darkslategray" to 0x2f4f4fffL,
        "darkslategrey" to 0x2f4f4fffL,
        "darkturquoise" to 0x00ced1ffL,
        "darkviolet" to 0x9400d3ffL,
        "deeppink" to 0xff1493ffL,
        "deepskyblue" to 0x00bfffffL,
        "dimgray" to 0x696969ffL,
        "dimgrey" to 0x696969ffL,
        "dodgerblue" to 0x1e90ffffL,
        "firebrick" to 0xb22222ffL,
        "floralwhite" to 0xfffaf0ffL,
        "forestgreen" to 0x228b22ffL,
        "fuchsia" to 0xff00ffffL,
        "gainsboro" to 0xdcdcdcffL,
        "ghostwhite" to 0xf8f8ffffL,
        "gold" to 0xffd700ffL,
        "goldenrod" to 0xdaa520ffL,
        "gray" to 0x808080ffL,
        "green" to 0x008000ffL,
        "greenyellow" to 0xadff2fffL,
        "grey" to 0x808080ffL,
        "honeydew" to 0xf0fff0ffL,
        "hotpink" to 0xff69b4ffL,
        "indianred" to 0xcd5c5cffL,
        "indigo" to 0x4b0082ffL,
        "ivory" to 0xfffff0ffL,
        "khaki" to 0xf0e68cffL,
        "lavender" to 0xe6e6faffL,
        "lavenderblush" to 0xfff0f5ffL,
        "lawngreen" to 0x7cfc00ffL,
        "lemonchiffon" to 0xfffacdffL,
        "lightblue" to 0xadd8e6ffL,
        "lightcoral" to 0xf08080ffL,
        "lightcyan" to 0xe0ffffffL,
        "lightgoldenrodyellow" to 0xfafad2ffL,
        "lightgray" to 0xd3d3d3ffL,
        "lightgreen" to 0x90ee90ffL,
        "lightgrey" to 0xd3d3d3ffL,
        "lightpink" to 0xffb6c1ffL,
        "lightsalmon" to 0xffa07affL,
        "lightseagreen" to 0x20b2aaffL,
        "lightskyblue" to 0x87cefaffL,
        "lightslategray" to 0x778899ffL,
        "lightslategrey" to 0x778899ffL,
        "lightsteelblue" to 0xb0c4deffL,
        "lightyellow" to 0xffffe0ffL,
        "lime" to 0x00ff00ffL,
        "limegreen" to 0x32cd32ffL,
        "linen" to 0xfaf0e6ffL,
        "magenta" to 0xff00ffffL,
        "maroon" to 0x800000ffL,
        "mediumaquamarine" to 0x66cdaaffL,
        "mediumblue" to 0x0000cdffL,
        "mediumorchid" to 0xba55d3ffL,
        "mediumpurple" to 0x9370dbffL,
        "mediumseagreen" to 0x3cb371ffL,
        "mediumslateblue" to 0x7b68eeffL,
        "mediumspringgreen" to 0x00fa9affL,
        "mediumturquoise" to 0x48d1ccffL,
        "mediumvioletred" to 0xc71585ffL,
        "midnightblue" to 0x191970ffL,
        "mintcream" to 0xf5fffaffL,
        "mistyrose" to 0xffe4e1ffL,
        "moccasin" to 0xffe4b5ffL,
        "navajowhite" to 0xffdeadffL,
        "navy" to 0x000080ffL,
        "oldlace" to 0xfdf5e6ffL,
        "olive" to 0x808000ffL,
        "olivedrab" to 0x6b8e23ffL,
        "orange" to 0xffa500ffL,
        "orangered" to 0xff4500ffL,
        "orchid" to 0xda70d6ffL,
        "palegoldenrod" to 0xeee8aaffL,
        "palegreen" to 0x98fb98ffL,
        "paleturquoise" to 0xafeeeeffL,
        "palevioletred" to 0xdb7093ffL,
        "papayawhip" to 0xffefd5ffL,
        "peachpuff" to 0xffdab9ffL,
        "peru" to 0xcd853fffL,
        "pink" to 0xffc0cbffL,
        "plum" to 0xdda0ddffL,
        "powderblue" to 0xb0e0e6ffL,
        "purple" to 0x800080ffL,
        "rebeccapurple" to 0x663399ffL,
        "red" to 0xff0000ffL,
        "rosybrown" to 0xbc8f8fffL,
        "royalblue" to 0x4169e1ffL,
        "saddlebrown" to 0x8b4513ffL,
        "salmon" to 0xfa8072ffL,
        "sandybrown" to 0xf4a460ffL,
        "seagreen" to 0x2e8b57ffL,
        "seashell" to 0xfff5eeffL,
        "sienna" to 0xa0522dffL,
        "silver" to 0xc0c0c0ffL,
        "skyblue" to 0x87ceebffL,
        "slateblue" to 0x6a5acdffL,
        "slategray" to 0x708090ffL,
        "slategrey" to 0x708090ffL,
        "snow" to 0xfffafaffL,
        "springgreen" to 0x00ff7fffL,
        "steelblue" to 0x4682b4ffL,
        "tan" to 0xd2b48cffL,
        "teal" to 0x008080ffL,
        "thistle" to 0xd8bfd8ffL,
        "tomato" to 0xff6347ffL,
        "turquoise" to 0x40e0d0ffL,
        "violet" to 0xee82eeffL,
        "wheat" to 0xf5deb3ffL,
        "white" to 0xffffffffL,
        "whitesmoke" to 0xf5f5f5ffL,
        "yellow" to 0xffff00ffL,
        "yellowgreen" to 0x9acd32ffL
    )
    private val hex = Regex("""^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val formats = listOf(
        Triple("rgb", false, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("rgb", true, Regex("""rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", false, Regex("""hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hsl", true, Regex("""hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)""")),
        Triple("hwb", false, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)""")),
        Triple("hwb", true, Regex("""hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"""))
    )
    private fun packed(value: Long) = rgba((value shr 24).toInt() and 255, (value shr 16).toInt() and 255, (value shr 8).toInt() and 255, (value and 255) / 255f)
    private fun byte(value: Double): Int {
        if (!value.isFinite()) return 0
        val low = kotlin.math.floor(value)
        return (if (value - low < .5) low else low + 1).toInt()
    }
    private fun hue(p: Double, q: Double, input: Double): Double {
        var t = input
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < .5 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
    fun color(source: String): Color {
        names[source]?.let { return packed(it) }
        if (hex.matches(source)) {
            var digits = source.drop(1)
            if (digits.length == 3 || digits.length == 4) digits = digits.map { "$it$it" }.joinToString("")
            if (digits.length == 6) digits += "ff"
            digits.toLongOrNull(16)?.let { return packed(it) }
        }
        for ((kind, alpha, pattern) in formats) {
            val match = pattern.find(source) ?: continue
            val values = match.groupValues.drop(1).map { it.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
            val opacity = if (alpha) byte(values[3].coerceIn(0.0, 1.0) * 255) / 255f else 1f
            if (kind == "rgb") return rgba(values[0].coerceIn(0.0, 255.0).toInt(), values[1].coerceIn(0.0, 255.0).toInt(), values[2].coerceIn(0.0, 255.0).toInt(), opacity)
            val h = ((values[0] % 360 + 360) % 360) / 360
            val second = (values[1] / 100).coerceIn(0.0, 1.0)
            val third = (values[2] / 100).coerceIn(0.0, 1.0)
            val channels = if (kind == "hwb") {
                if (second + third >= 1) List(3) { second / (second + third) }
                else listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(0.0, 1.0, it) * (1 - second - third) + second }
            } else {
                val q = if (third < .5) third * (1 + second) else third + second - third * second
                val p = 2 * third - q
                listOf(h + 1.0 / 3, h, h - 1.0 / 3).map { hue(p, q, it) }
            }
            return rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return Color.Transparent
    }
}

