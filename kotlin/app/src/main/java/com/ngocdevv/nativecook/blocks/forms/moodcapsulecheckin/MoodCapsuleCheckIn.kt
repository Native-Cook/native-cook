package com.ngocdevv.nativecook.blocks.forms.moodcapsulecheckin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The five outlines drawn inside the capsule. */
enum class Glyph { Spark, Bloom, Dot, Petal, Clover }
data class Mood(val name: String, val light: String, val base: String, val deep: String, val tint: String, val ink: String, val glyph: Glyph, val emotions: List<String>)
data class CheckIn(val mood: Mood, val emotion: String?, val context: String?, val scope: Int)
private val MOODS = listOf(
    Mood("Very unpleasant", "#8B66F2", "#6A3FD8", "#4A27B4", "#C9B6FF", "#2A1670", Glyph.Spark, listOf("Angry", "Anxious", "Drained")),
    Mood("Unpleasant", "#4C84FF", "#2F63EE", "#1C3FC2", "#ABC6FF", "#0F2A8A", Glyph.Bloom, listOf("Sad", "Tired", "Stressed")),
    Mood("Neutral", "#78D8E4", "#45BCCB", "#2F99AB", "#CAF7FD", "#0E5560", Glyph.Dot, listOf("Calm", "Content", "Indifferent")),
    Mood("Pleasant", "#F3D83A", "#E6C417", "#CDA70E", "#FFF27E", "#6B5600", Glyph.Petal, listOf("Happy", "Grateful", "Relaxed")),
    Mood("Very pleasant", "#FFA26A", "#F58A4B", "#E3672A", "#FFD6B6", "#7A3210", Glyph.Clover, listOf("Joyful", "Excited", "Proud")),
)
private val CONTEXTS = listOf("Work", "Family", "Friends", "Health")
private const val BACKGROUND = "#0E0E10"
private const val MUTE = "#8E8E93"
private const val TILE = "#1C1C1E"
private const val SEGMENT_ON = "#1F1F22"
private const val SEGMENT_INK = "#D6D6DA"
private val GREY_FILL = rgba(255, 255, 255, .1f)
private val GREY_EDGE = rgba(255, 255, 255, .13f)
private val GREY_MARK = rgba(255, 255, 255, .34f)
private val CHIP_FILL = rgba(255, 255, 255, .2f)
private const val DESIGN_WIDTH = 402f
private const val ROW_AT = .52f
private const val REST_WIDTH = 62f
private const val REST_HEIGHT = 103f
private const val REST_GAP = 9.5f
private const val SMALL_WIDTH = 42f
private const val SMALL_HEIGHT = 73f
private const val LARGE_WIDTH = 158f
private const val LARGE_HEIGHT = 306f
private const val OPEN_GAP = 10f
private const val MARK_REST = 27f
private const val MARK_SMALL = 17f
private const val MARK_LARGE = 31f
private const val MARK_Y = -95f
private const val CHIP_WIDTH = 116f
private const val CHIP_HEIGHT = 38f
private const val CHIP_RADIUS = 14f
private val CHIP_Y = floatArrayOf(-52f, -11f)
private const val CHIP_TEXT = 15f
private const val SEND_Y = 85f
private const val SEND_RADIUS = 34.5f
private const val QUESTION_Y = -103f
private const val QUESTION_TEXT = 20f
private const val NAME_Y = -212f
private const val NAME_TEXT = 21f
private const val TEXT_WIDTH = 320f
private const val GLOW_BLUR = 16f
private const val BLOOM_BLUR = 34f
private const val TEXT_BLUR = 8f
private const val CONTENT_BLUR = 12f
private const val SOFT_BLUR = 3f
private const val HAZE_REST = 120f
private const val HAZE_OPEN = 340f
private const val SEGMENTS_WIDTH = 289f
private const val SEGMENTS_HEIGHT = 54f
private const val SEGMENTS_INSET = 7f
private const val SEGMENT = (SEGMENTS_WIDTH - SEGMENTS_INSET * 2f) / 2f
private data class Spring(val mass: Float, val stiffness: Float, val damping: Float)
private val OPEN_SPRING = Spring(1f, 260f, 20f)
private val CLOSE_SPRING = Spring(1f, 260f, 28f)
private val GLIDE_SPRING = Spring(1f, 150f, 20f)
private val THUMB_SPRING = Spring(1f, 300f, 26f)
private val DIP_SPRING = Spring(1f, 420f, 18f)
private const val SWIPE = 120f
private const val RUBBER = .3f
private const val THROW = .15f
private const val PRESS = .06f
private const val PRESS_IN = 120
private const val CHIP_DIP = .9f
private const val SENT_IN = .28
private const val SENT_HOLD = .9
const val LOOP = 9.0
private const val AUTO_MOOD = 3
private const val TAP_AT = .8
private val SWIPES_AT = listOf(2.0 to 2, 3.0 to 3, 4.0 to 4, 5.0 to 3)
private const val CHIP_AT = 6.0
private const val CLOSE_AT = 7.2
private const val AUTO_PRESS = .12
private const val USER_HOLD = 3.0
private const val CLOSE_FOR = .7
private const val NOTHING = -1
private const val CHIP = 10
private const val SEND = 12
private const val INSIDE = 20
private const val DIP_IN = 200
private const val VEIL_IN = 260
private const val OUTLINE_IN = 220
private const val SENT_END = 1
private const val SPRING_FRAME_CAP = .064f
private const val SPRING_ENERGY_THRESHOLD = 6e-9f
private const val TAP_DISTANCE = 12f
private const val TAP_DURATION_MS = 500L
private const val PAN_OFFSET = 10f
private const val PAN_FAIL = 18f
private const val TILE_RIGHT = 27f
private const val TILE_WIDTH = 48f
private const val TILE_HEIGHT = 94f
private const val TILE_RADIUS = 24f
private const val TILE_PADDING = 4f
private const val SETTINGS_SIZE = 22f
private const val HISTORY_SIZE = 23f
private const val TILE_SHRINK = .08f
private const val SEGMENTS_BOTTOM = 24f
private const val SAFE_BOTTOM = 16f
private const val SEGMENT_TEXT = 16f
private const val SEGMENT_LINE = 20f
private const val LETTER_SPACING = -.2f
private const val TEXT_SPILL = 24f
private const val LEGACY_DIM = .22f
private const val PRESS_RETENTION = 20f
private const val PRESS_RETENTION_BOTTOM = 30f
private const val CAPSULE_GLOW_ALPHA = .5f
private const val CAPSULE_BLOOM_ALPHA = .62f
private const val CAPSULE_GREY_STROKE = 1f
private const val CAPSULE_GLASS_STROKE = 1.5f
private const val CAPSULE_OUTLINE_STROKE = 2f
private const val GLYPH_SAMPLES = 120
private val CAPSULE_BLUR_SAMPLES = FloatArray(7) { (it - 3).toFloat() }
private val CAPSULE_BLUR_WEIGHTS = FloatArray(7) { exp(-CAPSULE_BLUR_SAMPLES[it] * CAPSULE_BLUR_SAMPLES[it] / 2f) }.let { values ->
    val sum = values.sum()
    FloatArray(values.size) { values[it] / sum }
}
private const val CAPSULE_ICON_UNITS = 512f
private const val SETTINGS_FONT_CENTER = 191.5f
private const val HISTORY_FONT_CENTER = 192f
// Outlines from the source icon fonts, retained as paths without a font dependency.
private val CAPSULE_SETTINGS_PATH = PathParser().parsePathString("M203 419 206 422H256H306L309 419Q312 417 312.5 415.0Q313 413 318 383L322 352L332 347Q342 342 351 336L361 329L390 341Q415 351 420.5 352.0Q426 353 430 350Q433 347 456 306Q475 273 478.0 266.5Q481 260 478 256Q477 253 452 234L428 215V211Q429 185 428 176L427 169L452 150Q476 132 477 130Q481 126 479 120Q479 117 455 76Q436 44 431.5 38.0Q427 32 423 32Q420 32 391 43L361 55L351 49Q341 42 332 37L322 32L318 1Q314 -24 313.0 -28.5Q312 -33 308 -36Q305 -38 256.0 -38.0Q207 -38 204 -36Q200 -33 199.0 -28.5Q198 -24 194 1L190 32L179 37Q169 43 160 49L151 55L122 43Q97 33 91.5 32.0Q86 31 82 34Q79 37 56 78Q37 111 34.0 117.0Q31 123 33 128Q35 130 59 149L84 169V173Q83 199 84 208L85 215L62 232Q40 250 37 252Q31 258 32 264Q33 267 57 308Q76 340 80.5 346.0Q85 352 89 352Q92 352 121 341L151 329L161 335Q171 342 180 347L190 351L194 382Q198 412 199.0 414.5Q200 417 203 419ZM275 270Q275 270 275 270Q268 271 260 272Q232 273 209.0 256.5Q186 240 179 212Q173 192 179 172Q182 158 190 146Q200 133 215 123Q238 109 265 112Q282 115 297 123Q312 133 322 146Q330 158 333 172Q338 191 334 210Q329 229 317 243Q300 264 275 270Z").toPath()
private val CAPSULE_HISTORY_PATH = PathParser().parsePathString("M341 197 432 353 469 332 357 139 218 219 116 43H469V0H43V384H85V74L203 277Z").toPath()
private val QuadInOut = Easing { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
private val ARROW = PathParser().parsePathString("M0 12L0 -11M-9.5 -1.5L0 -11L9.5 -1.5").toPath()
private val CHECK = PathParser().parsePathString("M-11 1L-3.5 8.5L11.5 -7.5").toPath()

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)
private fun smooth(t: Float, from: Float, to: Float): Float { val u = clamp01((t - from) / (to - from)); return u * u * (3f - 2f * u) }
private data class CapsuleRow(val x: Float, val width: Float, val height: Float, val chosen: Float)
private fun rowAt(focus: Float, open: Float, count: Int): List<CapsuleRow> {
    if (count == 0) return emptyList()
    val opened = clamp01(open); val gap = REST_GAP + (OPEN_GAP - REST_GAP) * opened
    var x = 0f
    val row = List(count) { i ->
        val chosen = smooth(1f - abs(i - focus), 0f, 1f)
        val width = REST_WIDTH + (SMALL_WIDTH + (LARGE_WIDTH - SMALL_WIDTH) * chosen - REST_WIDTH) * open
        val height = REST_HEIGHT + (SMALL_HEIGHT + (LARGE_HEIGHT - SMALL_HEIGHT) * chosen - REST_HEIGHT) * open
        CapsuleRow(x + width / 2f, width, height, chosen).also { x += width + gap }
    }
    val inside = focus.coerceIn(0f, (count - 1).toFloat()); val before = floor(inside).toInt(); val after = min(count - 1, before + 1)
    val focused = row[before].x + (row[after].x - row[before].x) * (inside - before)
    val middle = (x - gap) / 2f
    val anchor = middle + (focused - middle + (focus - inside) * SWIPE) * opened
    return row.map { it.copy(x = it.x - anchor) }
}
private fun hit(x: Float, y: Float, focus: Int, open: Int, count: Int): Int {
    val row = rowAt(focus.toFloat(), open.toFloat(), count)
    if (open == 1 && focus in row.indices) {
        val dx = x - row[focus].x
        if (hypot(dx, y - SEND_Y) < SEND_RADIUS + 6f) return SEND
        if (abs(dx) < CHIP_WIDTH / 2f + 4f) {
            if (y >= CHIP_Y[0] - 4f && y < CHIP_Y[0] + CHIP_HEIGHT + 1f) return CHIP
            if (y >= CHIP_Y[1] - 1f && y < CHIP_Y[1] + CHIP_HEIGHT + 4f) return CHIP + 1
        }
        if (abs(dx) < LARGE_WIDTH / 2f && abs(y) < LARGE_HEIGHT / 2f) return INSIDE
    }
    row.forEachIndexed { i, capsule -> if (abs(x - capsule.x) < capsule.width / 2f + 5f && abs(y) < capsule.height / 2f + 8f) return i }
    return NOTHING
}
private fun color(value: String): Color = MoodCapsuleCheckInCSS.color(value)
private fun blend(from: String, to: String, amount: Double): Color {
    // The source helper reads raw hex slices before it interpolates channels.
    fun channel(hex: String, offset: Int): Double? {
        if (offset >= hex.length) return null
        var part = hex.substring(offset, min(offset + 2, hex.length)).trimStart()
        val sign = if (part.startsWith("-")) -1.0 else 1.0
        if (part.startsWith("+") || part.startsWith("-")) part = part.drop(1)
        if (part.startsWith("0x", ignoreCase = true)) part = part.drop(2)
        return part.takeWhile { it in "0123456789abcdefABCDEF" }.toIntOrNull(16)?.let { it * sign }
    }
    val mixed = intArrayOf(0, 0, 0)
    for ((index, offset) in listOf(1, 3, 5).withIndex()) {
        val a = channel(from, offset) ?: return Color.Transparent
        val b = channel(to, offset) ?: return Color.Transparent
        val value = a + (b - a) * amount
        val low = floor(value)
        mixed[index] = (if (value - low < .5) low else low + 1).toInt()
    }
    return rgba(mixed[0], mixed[1], mixed[2], 1f)
}
private fun gammaBlend(from: Color, to: Color, amount: Float): Color {
    fun mix(a: Float, b: Float) = (a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * amount).coerceAtLeast(0f).pow(1f / 2.2f)
    return rgba((mix(from.red, to.red) * 255f).roundToInt(), (mix(from.green, to.green) * 255f).roundToInt(), (mix(from.blue, to.blue) * 255f).roundToInt(), 1f)
}
private fun spectrum(moods: List<Mood>, at: Float, tint: Boolean): Color {
    if (moods.isEmpty()) return color(if (tint) SEGMENT_INK else SEGMENT_ON)
    val pos = at.coerceIn(0f, (moods.size - 1).toFloat()); val before = floor(pos).toInt(); val after = min(moods.size - 1, before + 1)
    return gammaBlend(color(if (tint) moods[before].tint else moods[before].base), color(if (tint) moods[after].tint else moods[after].base), pos - before)
}
private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

private class CapsuleArt(val mood: Mood) {
    private val baseColor = color(mood.base)
    private val tintColor = color(mood.tint)
    private val glow = com.ngocdevv.nativecook.shared.SoftRoundRect(baseColor, GLOW_BLUR)
    private val bloom = com.ngocdevv.nativecook.shared.SoftRoundRect(baseColor, BLOOM_BLUR)
    private val gradient = android.graphics.LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(argb(color(mood.light)), argb(baseColor), argb(color(mood.deep))),
        floatArrayOf(0f, .5f, 1f), android.graphics.Shader.TileMode.CLAMP,
    )
    private val glass = android.graphics.LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(argb(rgba(255, 255, 255, .5f)), argb(rgba(255, 255, 255, .06f)), argb(rgba(255, 255, 255, .2f))),
        floatArrayOf(0f, .5f, 1f), android.graphics.Shader.TileMode.CLAMP,
    )
    private val shaderMatrix = android.graphics.Matrix()
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
    private val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = glass; style = android.graphics.Paint.Style.STROKE; strokeWidth = CAPSULE_GLASS_STROKE
    }
    private val greyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val greyEdgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = CAPSULE_GREY_STROKE
    }
    private val tintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = argb(tintColor) }
    private val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(tintColor); style = android.graphics.Paint.Style.STROKE; strokeWidth = CAPSULE_OUTLINE_STROKE
    }
    private val fallbackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = argb(baseColor) }
    private var lastSoft = Float.NaN
    private val glyph = makeGlyph(mood.glyph)

    fun draw(scope: DrawScope, row: CapsuleRow, open: Float, outline: Float) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val opened = clamp01(open)
        val lit = 1f - opened * (1f - row.chosen)
        val grey = 1f - lit
        val chosen = opened * row.chosen
        val left = -row.width / 2f
        val top = -row.height / 2f
        val radius = row.width / 2f
        val glowAlpha = CAPSULE_GLOW_ALPHA * lit * (1f - chosen)
        val bloomAlpha = CAPSULE_BLOOM_ALPHA * chosen
        if (Build.VERSION.SDK_INT >= 28) {
            bloom.draw(canvas, left, top, row.width, row.height, radius, bloomAlpha)
            glow.draw(canvas, left, top, row.width, row.height, radius, glowAlpha)
        } else {
            roundRect(canvas, fallbackPaint, left, top, row.width, row.height, radius, BLOOM_BLUR, baseColor.alpha * bloomAlpha)
            roundRect(canvas, fallbackPaint, left, top, row.width, row.height, radius, GLOW_BLUR, baseColor.alpha * glowAlpha)
        }

        greyPaint.alpha = (GREY_FILL.alpha * grey * 255f).roundToInt()
        canvas.drawRoundRect(left, top, -left, -top, radius, radius, greyPaint)
        greyEdgePaint.alpha = (GREY_EDGE.alpha * grey * 255f).roundToInt()
        canvas.drawRoundRect(left, top, -left, -top, radius, radius, greyEdgePaint)

        shaderMatrix.setScale(1f, row.height)
        shaderMatrix.postTranslate(0f, top)
        gradient.setLocalMatrix(shaderMatrix)
        glass.setLocalMatrix(shaderMatrix)
        val soft = SOFT_BLUR * 4f * row.chosen * (1f - row.chosen) * opened
        if (Build.VERSION.SDK_INT >= 28 && soft != lastSoft) {
            val filter = if (soft > 0f) android.graphics.BlurMaskFilter(sigmaToRadius(soft), android.graphics.BlurMaskFilter.Blur.NORMAL) else null
            fillPaint.maskFilter = filter
            glassPaint.maskFilter = filter
            lastSoft = soft
        }
        roundRect(canvas, fillPaint, left, top, row.width, row.height, radius, soft, lit)
        roundRect(canvas, glassPaint, left, top, row.width, row.height, radius, soft, lit)
        outlinePaint.alpha = (tintColor.alpha * outline * chosen * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(left, top, -left, -top, radius, radius, outlinePaint)

        val markSize = MARK_REST + (MARK_SMALL + (MARK_LARGE - MARK_SMALL) * row.chosen - MARK_REST) * opened
        canvas.save()
        canvas.translate(0f, MARK_Y * chosen * row.height / LARGE_HEIGHT)
        canvas.scale(markSize / 2f, markSize / 2f)
        greyPaint.alpha = (GREY_MARK.alpha * grey * 255f).roundToInt()
        canvas.drawPath(glyph, greyPaint)
        tintPaint.alpha = (tintColor.alpha * lit * 255f).roundToInt()
        canvas.drawPath(glyph, tintPaint)
        canvas.restore()
    }

    private fun roundRect(canvas: android.graphics.Canvas, paint: android.graphics.Paint, x: Float, y: Float, width: Float, height: Float, radius: Float, sigma: Float, alpha: Float) {
        if (alpha <= 0f) return
        if (Build.VERSION.SDK_INT >= 28 || sigma <= 0f) {
            paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
            return
        }
        // Translate coverage, keeping the shader fixed, like Skia's mask blur before shading.
        for (vertical in CAPSULE_BLUR_SAMPLES.indices) for (horizontal in CAPSULE_BLUR_SAMPLES.indices) {
            val left = x + CAPSULE_BLUR_SAMPLES[horizontal] * sigma
            val top = y + CAPSULE_BLUR_SAMPLES[vertical] * sigma
            paint.alpha = (alpha * CAPSULE_BLUR_WEIGHTS[horizontal] * CAPSULE_BLUR_WEIGHTS[vertical] * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint)
        }
    }

    private fun argb(value: Color) = android.graphics.Color.argb(
        (value.alpha * 255f).roundToInt(), (value.red * 255f).roundToInt(),
        (value.green * 255f).roundToInt(), (value.blue * 255f).roundToInt(),
    )
    private fun makeGlyph(glyph: Glyph): android.graphics.Path {
        if (glyph == Glyph.Dot) return android.graphics.Path().apply { addCircle(0f, 0f, .8f, android.graphics.Path.Direction.CW) }
        val path = android.graphics.Path()
        val star = glyph == Glyph.Spark
        val count = if (star) 16 else GLYPH_SAMPLES
        val lobes = when (glyph) { Glyph.Bloom -> 8; Glyph.Petal -> 5; Glyph.Clover -> 6; else -> 8 }
        val radius = when (glyph) { Glyph.Bloom -> .86; else -> .8 }
        val wave = when (glyph) { Glyph.Bloom -> .12; Glyph.Petal -> .2; else -> .18 }
        for (index in 0 until count) {
            val angle = index.toDouble() / count * PI * 2.0
            val r = if (star) { if (index % 2 == 0) 1.0 else .66 } else radius + wave * cos(lobes * angle)
            val x = String.format(java.util.Locale.US, "%.3f", r * sin(angle)).toFloat()
            val y = String.format(java.util.Locale.US, "%.3f", -r * cos(angle)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}

private object MoodCapsuleCheckInCSS {
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



private class FrameTime(var now: Long? = null)
private class Motion(initial: Float) : State<Float> {
    private val state = mutableFloatStateOf(initial)
    override val value get() = state.floatValue
    private var target = initial
    private var velocity = 0f
    private var config = Spring(1f, 1f, 1f)
    private var energy0 = 0f
    private var springing = false
    private var springTimestamp = 0L
    private var timing = false
    private var origin = 0L
    private var duration = 0f
    private var from = initial
    private var completion: (() -> Unit)? = null
    fun direct(next: Float) { timing = false; springing = false; completion = null; velocity = 0f; state.floatValue = next; target = next }
    fun spring(next: Float, spring: Spring, startingAt: Long, onFinished: (() -> Unit)? = null) {
        if (value == next) { direct(next); onFinished?.invoke(); return }
        if (springing && target == next && config == spring) { completion = onFinished; advance(startingAt); return }
        if ((next > value && velocity < 0f) || (next < value && velocity > 0f)) velocity = 0f
        if (!springing) springTimestamp = startingAt
        energy0 = .5f * spring.stiffness * (value - next).pow(2)
        target = next; config = spring; springing = true; timing = false; completion = onFinished
        advance(startingAt)
    }
    fun tween(next: Float, ms: Int, startingAt: Long, still: Boolean = false, force: Boolean = false, onFinished: (() -> Unit)? = null) {
        if (still || (value == next && !force)) { direct(next); onFinished?.invoke(); return }
        if (timing && target == next && !force) { duration = ms * 1e6f; completion = onFinished; return }
        from = value; target = next; origin = startingAt; duration = ms * 1e6f; timing = true; springing = false; velocity = 0f; completion = onFinished
    }
    fun advance(now: Long) {
        if (timing) {
            val u = ((now - origin) / duration).coerceIn(0f, 1f)
            state.floatValue = from + (target - from) * QuadInOut.transform(u)
            if (u >= 1f) { timing = false; val finished = completion; completion = null; finished?.invoke() }
        }
        if (!springing) return
        val dt = min(SPRING_FRAME_CAP, ((now - springTimestamp) / 1e9f).coerceAtLeast(0f)); springTimestamp = now
        val omega = sqrt(config.stiffness / config.mass); val decay = config.damping / (2f * config.mass); val e = value - target
        val next: Float
        if (decay < omega) {
            val w = sqrt(omega * omega - decay * decay); val s = sin(w * dt); val c = cos(w * dt); val envelope = exp(-decay * dt)
            next = envelope * (e * c + (velocity + decay * e) / w * s)
            velocity = envelope * (velocity * c - (decay * velocity + omega * omega * e) / w * s)
        } else {
            val envelope = exp(-omega * dt); val b = velocity + omega * e
            next = envelope * (e + b * dt); velocity = envelope * (velocity - omega * b * dt)
        }
        state.floatValue = target + next
        val energy = .5f * config.stiffness * next * next + .5f * config.mass * velocity * velocity
        if (energy0 == 0f || energy / energy0 <= SPRING_ENERGY_THRESHOLD) {
            state.floatValue = target; velocity = 0f; springing = false
            val finished = completion; completion = null; finished?.invoke()
        }
    }
}
private class SentDelay(var at: Long? = null)
private data class ChipText(val text: String, val picked: Boolean)

/** A tap opens a mood; swipes pass its glow and chips to a neighbour. */
@Composable
fun MoodCapsuleCheckIn(
    moods: List<Mood> = MOODS,
    contexts: List<String> = CONTEXTS,
    question: String = "How are you feeling?",
    emotionLabel: String = "Emotions",
    contextLabel: String = "Context",
    scopes: List<String> = listOf("Current mood", "Overall today"),
    autoPlay: Boolean = false,
    onMoodChange: ((Mood) -> Unit)? = null,
    onSubmit: ((CheckIn) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val reduced by rememberUpdatedState(still)
    val currentMoods by rememberUpdatedState(moods)
    val currentContexts by rememberUpdatedState(contexts)
    val moodCallback by rememberUpdatedState(onMoodChange)
    val submitCallback by rememberUpdatedState(onSubmit)
    val settingsCallback by rememberUpdatedState(onSettings)
    val historyCallback by rememberUpdatedState(onHistory)
    val view = LocalView.current
    val density = LocalDensity.current
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var emotion by remember { mutableStateOf<Int?>(null) }
    var context by remember { mutableStateOf<Int?>(null) }
    var scope by remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableIntStateOf(NOTHING) }
    var aim by remember { mutableIntStateOf(min(AUTO_MOOD, max(0, moods.size - 1))) }
    var opened by remember { mutableIntStateOf(0) }
    val open = remember { Motion(0f) }
    val focus = remember { Motion(min(AUTO_MOOD, max(0, moods.size - 1)).toFloat()) }
    val outline = remember { Motion(0f) }
    val thumb = remember { Motion(0f) }
    val sent = remember { Motion(0f) }
    val dips = remember { listOf(Motion(1f), Motion(1f)) }
    val veil = remember { Motion(1f) }
    val downStorage = remember { mutableListOf<Motion>() }
    val down = remember(moods.size) {
        while (downStorage.size < moods.size) downStorage += Motion(0f)
        while (downStorage.size > moods.size) downStorage.removeAt(downStorage.lastIndex)
        downStorage.toList()
    }
    val sendDown = remember { Motion(0f) }
    val sentDelay = remember { SentDelay() }
    val frameTime = remember { FrameTime() }
    fun timestamp() = frameTime.now ?: System.nanoTime()
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun tap() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun move(value: Motion, to: Float, spring: Spring, then: (() -> Unit)? = null) {
        if (reduced) { value.direct(to); veil.direct(.2f); veil.tween(1f, VEIL_IN, timestamp(), still = true); then?.invoke() }
        else value.spring(to, spring, timestamp(), then)
    }
    fun forget() { emotion = null; context = null; sentDelay.at = null; sent.direct(0f) }
    fun choose(index: Int, auto: Boolean) {
        val mood = currentMoods.getOrNull(index) ?: return
        if (!auto) tap()
        focus.direct(index.toFloat()); aim = index; opened = 1; selected = index
        moodCallback?.invoke(mood); move(open, 1f, OPEN_SPRING)
    }
    fun glide(index: Int, auto: Boolean) {
        val mood = currentMoods.getOrNull(index) ?: return
        if (index != selected) { if (!auto) tick(); emotion = null; selected = index; moodCallback?.invoke(mood) }
        aim = index; move(focus, index.toFloat(), GLIDE_SPRING)
    }
    fun close(auto: Boolean, fromSubmission: Boolean = false) {
        if (selected == null && !fromSubmission) return
        if (!auto) tap()
        opened = 0; selected = null; move(open, 0f, CLOSE_SPRING, ::forget)
    }
    fun pick(which: Int, auto: Boolean) {
        val mood = currentMoods.getOrNull(selected ?: return) ?: return
        if (!auto) tap()
        fun next(value: Int?, length: Int): Int? = if (value == null) 0 else if (value + 1 < length) value + 1 else null
        if (which == 0) emotion = next(emotion, mood.emotions.size) else context = next(context, currentContexts.size)
        dips[which].direct(0f)
        if (reduced) dips[which].tween(1f, DIP_IN, timestamp(), still = true)
        else dips[which].spring(1f, DIP_SPRING, timestamp())
    }
    fun submit() {
        val mood = currentMoods.getOrNull(selected ?: return) ?: return
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        submitCallback?.invoke(CheckIn(mood, emotion?.let { mood.emotions.getOrNull(it) }, context?.let { currentContexts.getOrNull(it) }, scope))
        sentDelay.at = null
        sent.tween(1f, (SENT_IN * 1000).roundToInt(), timestamp(), force = true) {
            sentDelay.at = timestamp() + ((SENT_HOLD - SENT_IN) * 1e9).toLong()
        }
    }
    fun touched() { touches++ }
    fun pickScope(index: Int, auto: Boolean = false) {
        touched()
        if (!auto) tick()
        scope = index
        if (reduced) thumb.direct(index.toFloat()) else thumb.spring(index.toFloat(), THUMB_SPRING, timestamp())
        outline.tween(index.toFloat(), OUTLINE_IN, timestamp(), still = reduced)
    }
    fun act(target: Int, auto: Boolean) {
        if (selected == null) { if (target in currentMoods.indices) choose(target, auto) }
        else if (target == SEND) submit()
        else if (target == CHIP || target == CHIP + 1) pick(target - CHIP, auto)
        else if (target == NOTHING) close(auto)
        else if (target < currentMoods.size && target != selected) glide(target, auto)
    }
    val latestAct by rememberUpdatedState(::act)
    val latestChoose by rememberUpdatedState(::choose)
    val latestGlide by rememberUpdatedState(::glide)
    val latestClose by rememberUpdatedState(::close)
    val latestSubmit by rememberUpdatedState(::submit)
    val latestTouched by rememberUpdatedState(::touched)
    val latestPickScope by rememberUpdatedState(::pickScope)
    val currentSelected by rememberUpdatedState(selected)
    val currentScope by rememberUpdatedState(scope)
    val currentAim by rememberUpdatedState(aim)
    val currentOpened by rememberUpdatedState(opened)
    val currentDown by rememberUpdatedState(down)
    LaunchedEffect(pressed, still, moods.size) {
        down.forEachIndexed { index, value -> value.tween(if (pressed == index) 1f else 0f, PRESS_IN, timestamp(), still = reduced) }
        sendDown.tween(if (pressed == SEND) 1f else 0f, PRESS_IN, timestamp(), still = reduced)
    }
    LaunchedEffect(autoPlay, touches, moods.size) {
        if (!autoPlay || moods.isEmpty()) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (true) {
                if (currentScope != 0) latestPickScope(0, true)
                if (currentSelected == null) break
                latestClose(true, false); delay((CLOSE_FOR * 1000).toLong())
            }
        }
        var origin = System.nanoTime()
        while (true) {
            val events = mutableListOf<Pair<Double, () -> Unit>>()
            fun press(at: Double, target: Int) {
                events += at to { pressed = target }
                events += at + AUTO_PRESS to { pressed = NOTHING; latestAct(target, true) }
            }
            press(TAP_AT, min(AUTO_MOOD, moods.size - 1))
            SWIPES_AT.forEach { (at, mood) -> events += at to { latestGlide(min(mood, moods.size - 1), true) } }
            press(CHIP_AT, CHIP)
            events += CLOSE_AT to { latestClose(true, false) }
            for ((at, action) in events.sortedBy { it.first }) {
                delay(max(0L, (origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000)); action()
            }
            delay(max(0L, (origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000)); origin += (LOOP * 1e9).toLong()
        }
    }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now ->
            frameTime.now = now
            try {
                listOf(open, focus, outline, thumb, sent, veil, sendDown).forEach { it.advance(now) }
                dips.forEach { it.advance(now) }; currentDown.forEach { it.advance(now) }
                val delayed = sentDelay.at
                if (delayed != null && now >= delayed) {
                    sentDelay.at = null
                    sent.tween(1f, SENT_END, now, force = true) { latestClose(true, true) }
                }
            } finally { frameTime.now = null }
        }
    }
    val capsuleArt = remember(moods) { moods.map(::CapsuleArt) }
    val hazeColors = remember(moods) { moods.map { mood -> listOf("FF", "55", "00", "70", "38", "16", "0C").map { color(mood.base + it) } } }
    val row = rowAt(focus.value, open.value, moods.size)
    val chips = moods.mapIndexed { index, mood ->
        val own = index == selected
        listOf(
            ChipText(if (own && emotion != null) mood.emotions.getOrNull(emotion!!) ?: "" else emotionLabel, own && emotion != null),
            ChipText(if (context != null) contexts.getOrNull(context!!) ?: "" else contextLabel, context != null),
        )
    }
    val thumbInk = gammaBlend(color(SEGMENT_ON), gammaBlend(color(SEGMENT_ON), spectrum(moods, focus.value, false), .2f), clamp01(open.value))
    val segmentInk = gammaBlend(color(SEGMENT_INK), spectrum(moods, focus.value, true), clamp01(open.value))
    BoxWithConstraints(modifier.fillMaxSize().background(color(BACKGROUND))) {
        val width = maxWidth.value; val height = maxHeight.value
        val scale = min(1f, width / DESIGN_WIDTH).coerceAtLeast(.001f)
        val cx = width / 2f; val cy = height * ROW_AT
        val currentFrame by rememberUpdatedState(floatArrayOf(cx, cy, scale))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                surface(0.dp) { _, _, _, _ ->
                    withTransform({ translate(cx, cy); scale(scale, scale, Offset.Zero) }) {
                        val frameOrigin = Offset(-cx / scale, -cy / scale); val frameSize = Size(width / scale, height / scale)
                        hazeColors.forEachIndexed { index, base ->
                            drawRect(
                                Brush.radialGradient(0f to base[0], .45f to base[1], 1f to base[2], center = Offset(row[index].x, 0f), radius = HAZE_REST),
                                frameOrigin, frameSize, alpha = .2f * (1f - clamp01(open.value)),
                            )
                            drawRect(
                                Brush.radialGradient(0f to base[3], .35f to base[4], .7f to base[5], 1f to base[6], center = Offset.Zero, radius = HAZE_OPEN),
                                frameOrigin, frameSize, alpha = clamp01(open.value) * row[index].chosen,
                            )
                        }
                    }
                }
            }
            moods.forEachIndexed { index, mood ->
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    surface(0.dp) { _, _, _, _ ->
                        withTransform({ translate(cx + row[index].x * scale, cy); val s = scale * (1f - PRESS * down[index].value); scale(s, s, Offset.Zero) }) { capsuleArt[index].draw(this, row[index], open.value, outline.value) }
                    }
                }
                CapsuleContent(
                    mood, row[index], open.value, down[index].value, sendDown.value, sent.value,
                    dips.map { it.value }, chips[index], cx, cy, scale,
                )
            }
            CapsuleLine(question, QUESTION_TEXT, 400, color(MUTE), QUESTION_Y, 1f - smooth(open.value, 0f, .5f), cx, cy, scale)
            moods.forEachIndexed { index, mood ->
                CapsuleLine(mood.name, NAME_TEXT, 400, blend(mood.tint, mood.base, .3), NAME_Y, smooth(row[index].chosen, .5f, 1f) * smooth(open.value, .6f, 1f), cx, cy, scale)
            }
        }
        fun accessibility(action: String): Boolean {
            if (currentMoods.isEmpty()) return false
            val current = currentSelected ?: currentAim
            when (action) {
                "escape" -> latestClose(false, false)
                "activate" -> if (currentSelected == null) latestChoose(current, false) else latestSubmit()
                else -> {
                    val next = (current + if (action == "increment") 1 else -1).coerceIn(0, currentMoods.lastIndex)
                    if (currentSelected == null) latestChoose(next, false) else latestGlide(next, false)
                }
            }
            return true
        }
        val latestAccessibility by rememberUpdatedState(::accessibility)
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = question
            selected?.let { moods.getOrNull(it)?.let { mood -> stateDescription = mood.name } }
            if (moods.isNotEmpty()) progressBarRangeInfo = ProgressBarRangeInfo((selected ?: aim).toFloat(), 0f..moods.lastIndex.toFloat(), max(0, moods.size - 2))
            customActions = listOf(
                CustomAccessibilityAction("Increment") { latestAccessibility("increment") },
                CustomAccessibilityAction("Decrement") { latestAccessibility("decrement") },
                CustomAccessibilityAction("Activate") { latestAccessibility("activate") },
                CustomAccessibilityAction("Escape") { latestAccessibility("escape") },
            )
            onClick { latestAccessibility("activate") }
            setProgress { value ->
                if (currentMoods.isEmpty()) false
                else {
                    val next = floor(value + .5f).toInt().coerceIn(0, currentMoods.lastIndex)
                    if (currentSelected == null) latestChoose(next, false) else latestGlide(next, false)
                    true
                }
            }
        }.pointerInput(Unit) {
            awaitEachGesture {
                val downEvent = awaitFirstDown(requireUnconsumed = false)
                val f = currentFrame
                fun target(point: Offset) = hit((point.x / density.density - f[0]) / f[2], (point.y / density.density - f[1]) / f[2], currentAim, currentOpened, currentMoods.size)
                pressed = target(downEvent.position); latestTouched()
                val origin = downEvent.position
                var activatedAt = origin
                var caught = focus.value
                var tapAlive = true
                var panFailed = false
                var panActive = false
                val velocity = VelocityTracker()
                velocity.addPosition(downEvent.uptimeMillis, downEvent.position)
                try {
                    downEvent.consume()
                    while (true) {
                        val remaining = TAP_DURATION_MS - (android.os.SystemClock.uptimeMillis() - downEvent.uptimeMillis)
                        val event = if (tapAlive && !panActive) withTimeoutOrNull(max(1L, remaining)) { awaitPointerEvent() } else awaitPointerEvent()
                        if (event == null) { tapAlive = false; pressed = NOTHING; continue }
                        val change = event.changes.firstOrNull { it.id == downEvent.id } ?: break
                        if (change.isConsumed || event.changes.any { it.id != downEvent.id && it.pressed }) break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val dx = (change.position.x - origin.x) / density.density
                        val dy = (change.position.y - origin.y) / density.density
                        if (!panActive && abs(dy) > PAN_FAIL) panFailed = true
                        if (!panActive && !panFailed && abs(dx) > PAN_OFFSET && change.pressed) {
                            activatedAt = change.position; caught = focus.value; panActive = true
                            tapAlive = false; pressed = NOTHING; latestTouched()
                        }
                        if (tapAlive && (hypot(dx, dy) > TAP_DISTANCE || change.uptimeMillis - downEvent.uptimeMillis > TAP_DURATION_MS)) { tapAlive = false; pressed = NOTHING }
                        if (panActive && currentOpened != 0 && change.pressed) {
                            val raw = caught - (change.position.x - activatedAt.x) / (density.density * SWIPE * currentFrame[2])
                            val last = currentMoods.lastIndex.toFloat()
                            if (last >= 0f) focus.direct(if (raw < 0f) raw * RUBBER else if (raw > last) last + (raw - last) * RUBBER else raw)
                        }
                        change.consume()
                        if (!change.pressed) {
                            if (panActive && currentOpened != 0 && currentMoods.isNotEmpty()) {
                                val speed = velocity.calculateVelocity().x / density.density
                                val thrown = floor(focus.value - speed * THROW / (SWIPE * currentFrame[2]) + .5f).toInt()
                                val next = min(min(currentMoods.lastIndex, currentAim + 1), max(max(0, currentAim - 1), thrown))
                                latestGlide(next, false)
                            } else if (tapAlive && !panActive) latestAct(target(change.position), false)
                            break
                        }
                    }
                } finally { pressed = NOTHING }
            }
        })
        val tileY = safe.calculateTopPadding().value + TILE_PADDING
        Box(Modifier.offset((width - TILE_RIGHT - TILE_WIDTH).dp, tileY.dp).size(TILE_WIDTH.dp, TILE_HEIGHT.dp).graphicsLayer {
            alpha = 1f - smooth(open.value, 0f, .5f); scaleX = 1f - TILE_SHRINK * clamp01(open.value); scaleY = scaleX
        }.background(color(TILE), RoundedCornerShape(TILE_RADIUS.dp))) {
            CapsuleControl("Settings", selected == null, Modifier.offset(y = TILE_PADDING.dp).size(TILE_WIDTH.dp, ((TILE_HEIGHT - TILE_PADDING * 2f) / 2f).dp), {
                latestTouched(); settingsCallback?.invoke()
            }) { CapsuleSettingsIcon() }
            CapsuleControl("History", selected == null, Modifier.offset(y = (TILE_HEIGHT / 2f).dp).size(TILE_WIDTH.dp, ((TILE_HEIGHT - TILE_PADDING * 2f) / 2f).dp), {
                latestTouched(); historyCallback?.invoke()
            }) { CapsuleHistoryIcon() }
        }
        val segmentY = height - max(safe.calculateBottomPadding().value, SAFE_BOTTOM) - SEGMENTS_BOTTOM - SEGMENTS_HEIGHT
        Box(Modifier.offset(((width - SEGMENTS_WIDTH) / 2f).dp, segmentY.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(SEGMENTS_WIDTH.dp, SEGMENTS_HEIGHT.dp).background(rgba(255, 255, 255, .035f), RoundedCornerShape(SEGMENTS_HEIGHT.dp / 2f)).border((1f / density.density).dp, rgba(255, 255, 255, .08f), RoundedCornerShape(SEGMENTS_HEIGHT.dp / 2f)).selectableGroup()) {
            Box(Modifier.offset((SEGMENTS_INSET + 1f / density.density + thumb.value * SEGMENT).dp, (SEGMENTS_INSET + 1f / density.density).dp).size(SEGMENT.dp, (SEGMENTS_HEIGHT - SEGMENTS_INSET * 2f).dp).background(thumbInk, RoundedCornerShape((SEGMENTS_HEIGHT - SEGMENTS_INSET * 2f).dp / 2f)).clearAndSetSemantics {})
            scopes.forEachIndexed { index, label ->
                val segmentWidth = if (scopes.isEmpty()) SEGMENT else (SEGMENTS_WIDTH - SEGMENTS_INSET * 2f - 2f / density.density) / scopes.size
                CapsuleControl(label, true, Modifier.offset((SEGMENTS_INSET + 1f / density.density + index * segmentWidth).dp, (SEGMENTS_INSET + 1f / density.density).dp).size(segmentWidth.dp, (SEGMENTS_HEIGHT - SEGMENTS_INSET * 2f - 2f / density.density).dp).semantics { role = Role.Tab; this.selected = scope == index }, { latestPickScope(index, false) }, role = Role.Tab) {
                    BasicText(label, Modifier.wrapContentSize(Alignment.Center), style = TextStyle(color = if (scope == index) segmentInk else color(MUTE), fontFamily = FontFamily.SansSerif, fontWeight = if (scope == index) FontWeight.Medium else FontWeight.Normal, fontSize = SEGMENT_TEXT.sp, lineHeight = SEGMENT_LINE.sp, textAlign = TextAlign.Center, platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun fixedStyle(size: Float, weight: Int, ink: Color, fontScale: Float) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = (size / fontScale).sp,
    fontWeight = FontWeight(weight), color = ink, letterSpacing = (LETTER_SPACING / fontScale).sp,
    textAlign = TextAlign.Center, platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The extra bounds keep the blur inside the same clipped paragraph layer as Skia. */
@Composable
private fun CapsuleLine(text: String, size: Float, weight: Int, ink: Color, y: Float, shown: Float, cx: Float, cy: Float, scale: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = fixedStyle(size, weight, ink, density.fontScale)
    val measured = remember(text, style, density) { measurer.measure(AnnotatedString(text), style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (TEXT_WIDTH * density.density).roundToInt())) }
    val height = measured.size.height / density.density
    val sigma = TEXT_BLUR * (1f - shown)
    Box(Modifier.offset((cx - TEXT_WIDTH / 2f).dp, (cy + y * scale - height / 2f - TEXT_SPILL).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(TEXT_WIDTH.dp, (height + TEXT_SPILL * 2f).dp).graphicsLayer {
        scaleX = scale; scaleY = scale
        alpha = shown * if (Build.VERSION.SDK_INT >= 31) 1f else 1f - LEGACY_DIM * sigma / TEXT_BLUR
        compositingStrategy = CompositingStrategy.Offscreen; clip = true
        val px = sigma * density.density
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && px > 0f) BlurEffect(sigmaToRadius(px), sigmaToRadius(px), TileMode.Decal) else null
    }.clearAndSetSemantics {}) {
        BasicText(text, Modifier.offset(y = TEXT_SPILL.dp).size(TEXT_WIDTH.dp, height.dp), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CapsuleContent(mood: Mood, row: CapsuleRow, open: Float, down: Float, sendDown: Float, sent: Float, dips: List<Float>, chips: List<ChipText>, cx: Float, cy: Float, scale: Float) {
    val density = LocalDensity.current
    val chosen = clamp01(open) * row.chosen
    val shown = smooth(chosen, .3f, .9f)
    val sigma = CONTENT_BLUR * (1f - smooth(chosen, .5f, 1f))
    val top = CHIP_Y[0] - 16f
    val height = SEND_Y + SEND_RADIUS - CHIP_Y[0] + 32f
    val scaled = scale * (1f - PRESS * down) * row.height / LARGE_HEIGHT
    val checkMeasure = remember { PathMeasure().apply { setPath(CHECK, false) } }
    val checkPath = remember { Path() }
    Box(Modifier.offset((cx + row.x * scale - LARGE_WIDTH / 2f).dp, (cy + top).dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(LARGE_WIDTH.dp, height.dp).graphicsLayer {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, -top / height)
        scaleX = scaled; scaleY = scaled
        alpha = shown * if (Build.VERSION.SDK_INT >= 31) 1f else 1f - LEGACY_DIM * sigma / CONTENT_BLUR
        compositingStrategy = CompositingStrategy.Offscreen; clip = true
        val px = sigma * density.density
        renderEffect = if (Build.VERSION.SDK_INT >= 31 && px > 0f) BlurEffect(sigmaToRadius(px), sigmaToRadius(px), TileMode.Decal) else null
    }.clearAndSetSemantics {}) {
        chips.forEachIndexed { index, chip ->
            val size = CHIP_DIP + (1f - CHIP_DIP) * dips[index]
            Box(Modifier.offset(((LARGE_WIDTH - CHIP_WIDTH) / 2f).dp, (CHIP_Y[index] - top).dp).size(CHIP_WIDTH.dp, CHIP_HEIGHT.dp).graphicsLayer { scaleX = size; scaleY = size }.background(if (chip.picked) color(mood.tint) else CHIP_FILL, RoundedCornerShape(CHIP_RADIUS.dp)), contentAlignment = Alignment.Center) {
                BasicText(chip.text, Modifier.size((CHIP_WIDTH - 12f).dp, CHIP_HEIGHT.dp).wrapContentSize(Alignment.Center), style = fixedStyle(CHIP_TEXT, 500, if (chip.picked) color(mood.ink) else blend(mood.tint, "#FFFFFF", .4), density.fontScale), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Canvas(Modifier.offset((LARGE_WIDTH / 2f - SEND_RADIUS).dp, (SEND_Y - SEND_RADIUS - top).dp).size((SEND_RADIUS * 2f).dp).graphicsLayer { scaleX = 1f - PRESS * sendDown; scaleY = scaleX }) {
            surface(0.dp) { _, _, _, _ ->
                withTransform({ translate(SEND_RADIUS, SEND_RADIUS) }) {
                    drawCircle(color(mood.tint), SEND_RADIUS, Offset.Zero)
                    drawPath(ARROW, color(mood.ink), alpha = 1f - smooth(sent, 0f, .5f), style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val end = smooth(sent, .4f, 1f)
                    checkPath.reset()
                    if (end > 0f) { checkMeasure.getSegment(0f, checkMeasure.length * end, checkPath, true); drawPath(checkPath, color(mood.ink), style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
        }
    }
}

/** Pressability's retention region permits leaving and returning before a release. */
@Composable
private fun CapsuleControl(label: String, enabled: Boolean, modifier: Modifier, action: () -> Unit, role: Role = Role.Button, content: @Composable () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    val active by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val view = LocalView.current
    val hit = if (enabled) Modifier.semantics(mergeDescendants = true) {
        contentDescription = label; this.role = role
        onClick { if (active) { currentAction(); true } else false }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!active) return@awaitEachGesture
            down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || !active) break
                    val p = change.position
                    val inside = p.x >= -PRESS_RETENTION * density.density && p.x <= size.width + PRESS_RETENTION * density.density && p.y >= -PRESS_RETENTION * density.density && p.y <= size.height + PRESS_RETENTION_BOTTOM * density.density
                    change.consume()
                    if (!change.pressed) {
                        if (inside) { view.playSoundEffect(SoundEffectConstants.CLICK); currentAction() }
                        break
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
        }
    } else Modifier.clearAndSetSemantics {}
    Box(modifier.then(hit), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CapsuleSettingsIcon() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.Center).size(SETTINGS_SIZE.dp)) {
            surface(0.dp) { _, _, _, _ ->
                val unit = SETTINGS_SIZE / CAPSULE_ICON_UNITS
                withTransform({
                    translate(0f, SETTINGS_SIZE / 2f + SETTINGS_FONT_CENTER * unit)
                    scale(unit, -unit, Offset.Zero)
                }) { drawPath(CAPSULE_SETTINGS_PATH, color(MUTE)) }
            }
        }
    }
}

@Composable
private fun CapsuleHistoryIcon() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.align(Alignment.Center).size(HISTORY_SIZE.dp)) {
            surface(0.dp) { _, _, _, _ ->
                val unit = HISTORY_SIZE / CAPSULE_ICON_UNITS
                withTransform({
                    translate(0f, HISTORY_SIZE / 2f + HISTORY_FONT_CENTER * unit)
                    scale(unit, -unit, Offset.Zero)
                }) { drawPath(CAPSULE_HISTORY_PATH, color(MUTE)) }
            }
        }
    }
}

