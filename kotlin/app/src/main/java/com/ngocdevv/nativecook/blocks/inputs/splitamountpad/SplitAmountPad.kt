// Split Amount Pad — "Split the bill" in an invented payments app, Kindly.
//
// A custom number pad types an amount whose digits are tiles: each press drops one in on a spring,
// the amount re-centres, a thousands comma pops in, and the four shares under the avatars roll to
// their new values. Backspace flicks the last digit away (held, it clears), a key that would pass
// $2,500 shakes the amount instead, and tapping a friend takes them out of the split.
//
// autoPlay: LOOP = 9 s (SCRIPT in SapMotion.kt). A ghost finger types 1 2 8 . 4 0, the shares settle
// on $32.10, Tobi is tapped out ($42.80 each), backspace is held until the amount clears, Tobi comes
// back, rest. Frame 9 s equals frame 0. A real touch takes over at once and keeps what was typed;
// IDLE seconds after the last touch the amount clears and the loop restarts at t = 0.
// Reduced motion: the finished state ($128.40, $32.10 each) with no loop; keys still work, without
// the springs.
//
// Drawing model: SapEngine holds every moving number as plain fields stepped by one frame callback.
// The amount, the avatars, the shares and the keys are canvases whose draw lambdas read those fields,
// so neither a frame nor a key press recomposes anything.
package com.ngocdevv.nativecook.blocks.inputs.splitamountpad

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ colours and copy */

private val PAPER_TOP = Color(0xFFF7F3EB)
private val PAPER_MID = Color(0xFFF2EDE3)
private val PAPER_END = Color(0xFFEAE4D7)
private val INK = Color(0xFF1E1B16)
/** secondary and tertiary text, the same warm grey family */
private val MUTE = Color(0xFF7B7568)
private val FAINT = Color(0xFFA8A294)
/** the one accent */
private val ACCENT = Color(0xFF1F6B4E)
private val ACCENT_INK = Color(0xFFF4FBF6)
private val KEY = Color(0xFFFFFEFB)
private val KEY_PRESSED = rgba(40, 32, 20, 0.07f)

private const val COPY_TITLE = "Split the bill"
private const val COPY_PLACE = "Dinner at Marlow's · Friday"
private const val COPY_LIMIT = "Up to $2,500 a bill"
private const val COPY_BETWEEN = "Split evenly between"
private const val COPY_OUT = "Sits out"
private const val COPY_CTA = "Send requests"

/** who is at the table: name, initials, the avatar's tint and its ink */
private class Person(val name: String, val initials: String, val tint: Color, val ink: Color)

private val PEOPLE = arrayOf(
    Person("You", "AK", Color(0xFFDCE8DD), Color(0xFF2F6B4A)),
    Person("Maren", "MH", Color(0xFFF6DFD2), Color(0xFFA0502C)),
    Person("Tobi", "TO", Color(0xFFD9E4F1), Color(0xFF355C88)),
    Person("Ines", "IV", Color(0xFFF2E6BF), Color(0xFF7A5E14)),
)

private const val GHOST = 44f
private const val KEY_RADIUS = 18f
private const val SHARE_WIDTH = SHARE_DIGIT * 7 + SHARE_MARK * 2
private val GLYPHS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".")

/* ------------------------------------------------------------------ paints, paths and shadows, built once */

private fun face(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, weight, false)
    else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

/** a text paint in dp units (the canvases below are scaled by the density before text is drawn) */
private fun textPaint(size: Float, weight: Int, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f) =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        typeface = face(weight)
        textAlign = align
        fontFeatureSettings = "tnum"
        if (spacing != 0f) letterSpacing = spacing / size
    }

/** the baseline that centres a paint's ascent-to-descent box in a line of this height */
private fun Paint.baselineIn(line: Float): Float {
    val fm = fontMetrics
    return line / 2f - (fm.ascent + fm.descent) / 2f
}

private fun Paint.tint(color: Color, alpha: Float) {
    this.color = color.toArgb()
    this.alpha = (color.alpha * alpha.coerceIn(0f, 1f) * 255f).roundToInt()
}

private fun roundBox(w: Float, h: Float, r: Float) = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }

/** the 1 dp band an `inset 0 <dy>px 0` shadow paints along the top (dy > 0) or bottom (dy < 0) inner edge */
private fun insetBand(w: Float, h: Float, r: Float, dy: Float): Path {
    val shifted = Path().apply { addRoundRect(RoundRect(0f, dy, w, h + dy, CornerRadius(r))) }
    return Path().apply { op(roundBox(w, h, r), shifted, PathOperation.Difference) }
}

private class SapArt(val l: SapLayout) {
    val digit = textPaint(l.amount, 700)
    val dollar = textPaint(l.amount * DOLLAR_SCALE, 600, Paint.Align.LEFT)
    val share = textPaint(SHARE_SIZE, 600)
    val keyLabel = textPaint(26f, 500)
    val initials = textPaint(l.disc * 0.34f, 700, spacing = 0.2f)
    val digitBase = digit.baselineIn(l.line)
    val dollarBase = dollar.baselineIn(l.line) - l.amount * 0.14f
    val shareBase = share.baselineIn(SHARE_LINE)
    val keyLabelBase = keyLabel.baselineIn(l.keyH)
    val initialsBase = initials.baselineIn(l.disc)

    val metrics = Metrics(digit.measureText("0"), digit.measureText("."), digit.measureText(","), dollar.measureText("$"))

    val keyBox = roundBox(l.keyW, l.keyH, KEY_RADIUS)
    val keyTop = insetBand(l.keyW, l.keyH, KEY_RADIUS, 1f)
    val keyBottom = insetBand(l.keyW, l.keyH, KEY_RADIUS, -1f)
    val keyAmbient = SoftRoundRect(rgba(40, 32, 20, 0.10f), sigma = 8f)
    val keyContact = SoftRoundRect(rgba(40, 32, 20, 0.18f), sigma = 0.75f)

    val discBox = roundBox(l.disc, l.disc, l.disc / 2f)
    val discTop = insetBand(l.disc, l.disc, l.disc / 2f, 1f)
    val discNear = SoftRoundRect(rgba(40, 32, 20, 0.14f), sigma = 1f)
    val discFar = SoftRoundRect(rgba(40, 32, 20, 0.08f), sigma = 7f)

    val navTop = insetBand(36f, 36f, 18f, 1f)

    val back = PathParser().parsePathString("M11.2 7.5h11.3a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H11.2a3 3 0 0 1-2.3-1.1L4 15l4.9-6.4a3 3 0 0 1 2.3-1.1Z").toPath()
    val backCross = PathParser().parsePathString("m14 12 5.6 6M19.6 12 14 18").toPath()
    val check = PathParser().parsePathString("M1.8 5.3 4.1 7.5 8.3 2.8").toPath()
    val chevron = PathParser().parsePathString("M11 3.5 5.5 9l5.5 5.5").toPath()
}

/** a CSS box-shadow layer under a box at the origin, outside the box only; dp units */
private fun DrawScope.boxShadow(soft: SoftRoundRect, box: Path, w: Float, h: Float, r: Float, dy: Float, alpha: Float) {
    if (alpha <= 0.003f) return
    clipPath(box, ClipOp.Difference) {
        drawIntoCanvas { soft.draw(it.nativeCanvas, 0f, dy, w, h, r, alpha) }
    }
}

/* ------------------------------------------------------------------ paper grain */

// Neutral warm specks at up to 5 % opacity, premultiplied, on a half-dp grid. Nothing here depends on time.
private const val GRAIN_AGSL = """
uniform float density;
half4 main(float2 p) {
  float2 q = floor(p / density * 2.0);
  float n = fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453);
  float a = n * n * 0.05;
  return half4(half3(0.16, 0.13, 0.08) * a, a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun grainBrush(density: Float): Brush = ShaderBrush(RuntimeShader(GRAIN_AGSL).apply { setFloatUniform("density", density) })

/* ------------------------------------------------------------------ the screen */

@Composable
fun SplitAmountPad(modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val screen = LocalDensity.current
    BlockSystemBars(darkIcons = true)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to PAPER_TOP, 0.5f to PAPER_MID, 1f to PAPER_END)),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = remember(width, height) { SapLayout(width, height) }
        val art = remember(l) { SapArt(l) }
        val engine = remember(art) { SapEngine(art.metrics) }
        /** window y of the screen, the avatars' row and the pad, px */
        val tops = remember(engine) { FloatArray(3) { Float.NaN } }
        fun place() {
            if (!tops[0].isNaN() && !tops[1].isNaN()) engine.seatsY = (tops[1] - tops[0]) / screen.density
            if (!tops[0].isNaN() && !tops[2].isNaN()) engine.padY = (tops[2] - tops[0]) / screen.density
        }

        // The engine reads these as plain fields on every frame, so they are always the latest.
        SideEffect {
            engine.layout = l
            engine.auto = autoPlay && !reduced
            engine.reduced = reduced
            engine.onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            engine.onWarn = {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }
        }
        LaunchedEffect(engine, reduced) {
            if (reduced) engine.showFinal()
        }
        LaunchedEffect(engine) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min(0.05f, (now - last) / 1_000_000_000f)
                    last = now
                    engine.tick(dt)
                }
            }
        }

        val grain = remember(screen.density) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) grainBrush(screen.density) else null
        }
        if (grain != null) Box(Modifier.fillMaxSize().drawBehind { drawRect(grain) })

        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { tops[0] = it.positionInWindow().y; place() }
                .padding(top = insets.calculateTopPadding(), bottom = max(insets.calculateBottomPadding().value, 12f).dp + 8.dp),
        ) {
            // the navigation bar
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = SIDE.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .cssBoxShadow(rgba(40, 32, 20, 0.07f), offsetY = 4.dp, blur = 10.dp, radius = 18.dp)
                        .cssBoxShadow(rgba(40, 32, 20, 0.14f), offsetY = 1.dp, blur = 1.5.dp, radius = 18.dp)
                        .background(KEY, CircleShape)
                        .drawBehind {
                            scale(density, density, pivot = Offset.Zero) {
                                drawPath(art.navTop, Color.White, alpha = 0.9f)
                                translate(9f, 9f) {
                                    drawPath(art.chevron, INK, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        },
                )
                Label(COPY_TITLE, 17f, FontWeight.SemiBold, INK, spacing = -0.2f)
                Spacer(Modifier.width(36.dp))
            }

            // the amount
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Label(COPY_PLACE, 14f, FontWeight.Medium, MUTE)
                Spacer(Modifier.height(if (l.compact) 4.dp else 8.dp))
                Amount(engine, art, width)
                Spacer(Modifier.height(if (l.compact) 2.dp else 6.dp))
                Label(
                    COPY_LIMIT, 13f, FontWeight.Medium, INK,
                    Modifier.graphicsLayer {
                        engine.frame
                        val v = engine.limit.value
                        alpha = 0.62f + 0.38f * v
                        scaleX = 1f + 0.04f * v
                        scaleY = scaleX
                    },
                )
            }

            Label(COPY_BETWEEN, 13f, FontWeight.Medium, MUTE, Modifier.padding(start = SIDE.dp, end = SIDE.dp, bottom = 10.dp))
            Row(
                Modifier
                    .padding(horizontal = SIDE.dp)
                    .onGloballyPositioned { tops[1] = it.positionInWindow().y; place() },
            ) {
                for (i in 0 until FRIENDS) Friend(i, engine, art)
            }

            Column(
                Modifier
                    .padding(start = SIDE.dp, end = SIDE.dp, top = l.block.dp)
                    .onGloballyPositioned { tops[2] = it.positionInWindow().y; place() },
                verticalArrangement = Arrangement.spacedBy(l.gap.dp),
            ) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(l.gap.dp)) {
                        for (col in 0 until 3) PadKey(row * 3 + col, engine, art)
                    }
                }
            }

            // the button follows the amount: dim while nothing is typed
            val ctaRadius = l.cta / 2f
            Box(
                Modifier
                    .padding(start = SIDE.dp, end = SIDE.dp, top = l.block.dp)
                    .fillMaxWidth()
                    .height(l.cta.dp)
                    .cssBoxShadow(rgba(18, 60, 44, 0.18f), offsetY = 8.dp, blur = 18.dp, radius = ctaRadius.dp) { engine.frame; 0.42f + 0.58f * (1f - engine.empty.value) }
                    .cssBoxShadow(rgba(18, 60, 44, 0.30f), offsetY = 1.dp, blur = 2.dp, radius = ctaRadius.dp) { engine.frame; 0.42f + 0.58f * (1f - engine.empty.value) }
                    .drawBehind {
                        engine.frame
                        val a = 0.42f + 0.58f * (1f - engine.empty.value)
                        val r = CornerRadius(ctaRadius.dp.toPx())
                        drawRoundRect(ACCENT, cornerRadius = r, alpha = a)
                        // inset 0 1px 0 rgba(255,255,255,0.18)
                        val band = Path().apply {
                            op(
                                Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) },
                                Path().apply { addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), r)) },
                                PathOperation.Difference,
                            )
                        }
                        drawPath(band, Color.White, alpha = 0.18f * a)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    COPY_CTA, 17f, FontWeight.SemiBold, ACCENT_INK,
                    Modifier.graphicsLayer {
                        engine.frame
                        alpha = 0.42f + 0.58f * (1f - engine.empty.value)
                    },
                    spacing = -0.2f,
                )
            }
        }

        // the ghost finger
        Box(
            Modifier
                .size(GHOST.dp)
                .graphicsLayer {
                    engine.frame
                    translationX = (engine.ghostX - GHOST / 2f) * density
                    translationY = (engine.ghostY - GHOST / 2f) * density
                    val s = 1f - 0.14f * engine.autoPress
                    scaleX = s
                    scaleY = s
                    alpha = engine.ghostA.value.coerceIn(0f, 1f)
                }
                .background(rgba(30, 27, 22, 0.14f), CircleShape)
                .border(1.dp, rgba(30, 27, 22, 0.26f), CircleShape),
        )
    }
}

/** static text in dp, so the system's font scale does not move the layout (allowFontScaling false) */
@Composable
private fun Label(text: String, size: Float, weight: FontWeight, color: Color, modifier: Modifier = Modifier, spacing: Float = 0f) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.dp.toSp() },
            fontWeight = weight,
            letterSpacing = with(density) { spacing.dp.toSp() },
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/* ------------------------------------------------------------------ the amount */

@Composable
private fun Amount(engine: SapEngine, art: SapArt, width: Float) {
    val l = art.l
    val m = art.metrics
    Canvas(Modifier.fillMaxWidth().height(l.line.dp)) {
        engine.frame
        val mid = width / 2f
        scale(density, density, pivot = Offset.Zero) {
            translate(left = engine.shake.value) {
                drawIntoCanvas { scope ->
                    val canvas = scope.nativeCanvas
                    // the dollar sign, smaller and raised, at the amount's left edge
                    art.dollar.tint(MUTE, 1f)
                    canvas.drawText("$", mid + engine.dollarX.value, art.dollarBase, art.dollar)
                    // the placeholder zero stands where an empty amount's first digit would
                    val zero = engine.empty.value * 0.24f
                    if (zero > 0.003f) {
                        art.digit.tint(INK, zero)
                        canvas.drawText("0", mid + (m.dollar + DOLLAR_GAP) / 2f, art.digitBase, art.digit)
                    }
                    // the thousands comma pops in where it belongs
                    val comma = engine.commaOn.value
                    if (comma > 0.003f) {
                        canvas.save()
                        canvas.translate(mid + engine.commaX.value, l.line / 2f + 6f * (1f - comma))
                        canvas.scale(0.6f + 0.4f * comma, 0.6f + 0.4f * comma)
                        art.digit.tint(INK, comma)
                        canvas.drawText(",", 0f, art.digitBase - l.line / 2f, art.digit)
                        canvas.restore()
                    }
                    for (tile in engine.tiles) {
                        val e = tile.enter.value
                        val out = tile.leave.value
                        val alpha = min(1f, max(0f, e * 3f)) * (1f - out * out)
                        if (alpha <= 0.003f) continue
                        val s = (DROP_SCALE + (1f - DROP_SCALE) * e) * (1f - FLICK_SHRINK * out)
                        canvas.save()
                        canvas.translate(
                            mid + tile.x.value + FLICK_X * out,
                            l.line / 2f + DROP_RISE * (1f - e) + FLICK_UP * out + FLICK_FALL * out * out,
                        )
                        canvas.rotate(tile.turn * (1f - e) + FLICK_TURN * out)
                        canvas.scale(s, s)
                        art.digit.tint(INK, alpha)
                        canvas.drawText(GLYPHS[tile.glyph], 0f, art.digitBase - l.line / 2f, art.digit)
                        canvas.restore()
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ the people */

@Composable
private fun Friend(index: Int, engine: SapEngine, art: SapArt) {
    val l = art.l
    val person = PEOPLE[index]
    Column(
        Modifier
            .width(l.column.dp)
            .pointerInput(engine, index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    engine.friendDown(index)
                    waitForUpOrCancellation()
                    engine.friendUp(index)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the avatar and its check badge
        Canvas(Modifier.size(l.disc.dp)) {
            engine.frame
            val v = engine.friendOn[index].value
            val p = max(engine.friendPress[index].value, if (engine.autoFriend == index) engine.autoPress else 0f)
            val alpha = 0.4f + 0.6f * clamp01(v)
            val d = l.disc
            scale(density, density, pivot = Offset.Zero) {
                scale((0.9f + 0.1f * v) * (1f - 0.06f * p), pivot = Offset(d / 2f, d / 2f)) {
                    boxShadow(art.discFar, art.discBox, d, d, d / 2f, 6f, alpha)
                    boxShadow(art.discNear, art.discBox, d, d, d / 2f, 1f, alpha)
                    drawCircle(person.tint, d / 2f, Offset(d / 2f, d / 2f), alpha = alpha)
                    drawPath(art.discTop, Color.White, alpha = 0.55f * alpha)
                    drawIntoCanvas {
                        art.initials.tint(person.ink, alpha)
                        it.nativeCanvas.drawText(person.initials, d / 2f, art.initialsBase, art.initials)
                    }
                    if (index > 0) {
                        val badge = alpha * min(1f, max(0f, v * 2f))
                        val centre = Offset(d - 7f, d - 7f)
                        if (badge > 0.003f && v > 0f) {
                            scale(v, pivot = centre) {
                                drawCircle(PAPER_MID, 10f, centre, alpha = badge)
                                drawCircle(ACCENT, 8f, centre, alpha = badge)
                                translate(centre.x - 5f, centre.y - 5f) {
                                    drawPath(art.check, ACCENT_INK, alpha = badge, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Label(person.name, 13f, FontWeight.Medium, INK)
        Box(Modifier.padding(top = 2.dp).height(SHARE_LINE.dp), contentAlignment = Alignment.Center) {
            Share(index, engine, art)
            Label(
                COPY_OUT, 13f, FontWeight.Medium, FAINT,
                Modifier.graphicsLayer {
                    engine.frame
                    alpha = 1f - clamp01(engine.friendOn[index].value)
                },
            )
        }
    }
}

/** "$0,000.00" at most: six digit strips sprung to their digits, the leading ones fading as the group re-centres */
@Composable
private fun Share(friend: Int, engine: SapEngine, art: SapArt) {
    Canvas(Modifier.size(SHARE_WIDTH.dp, SHARE_LINE.dp)) {
        engine.frame
        val roll = engine.rolls[friend]
        val alpha = clamp01(engine.friendOn[friend].value)
        if (alpha <= 0.003f) return@Canvas
        val hidden = roll.hidden.value
        val paint = art.share
        scale(density, density, pivot = Offset.Zero) {
            translate(left = -hidden / 2f) {
                clipRect(0f, 0f, SHARE_WIDTH, SHARE_LINE) {
                    drawIntoCanvas { scope ->
                        val canvas = scope.nativeCanvas
                        fun mark(text: String, x: Float, w: Float, a: Float) {
                            if (a <= 0.003f) return
                            paint.tint(INK, alpha * a)
                            canvas.drawText(text, x + w / 2f, art.shareBase, paint)
                        }
                        fun digit(place: Int, x: Float) {
                            val a = alpha * clamp01(roll.shown[place].value)
                            if (a <= 0.003f) return
                            val d = roll.digit[place].value
                            val first = floorInt(d)
                            canvas.save()
                            canvas.clipRect(x, 0f, x + SHARE_DIGIT, SHARE_LINE)
                            paint.tint(INK, a)
                            for (k in first..first + 1) {
                                if (k < 0 || k > 9) continue
                                canvas.drawText(GLYPHS[k], x + SHARE_DIGIT / 2f, art.shareBase + (k - d) * SHARE_LINE, paint)
                            }
                            canvas.restore()
                        }
                        var x = 0f
                        mark("$", x + hidden, SHARE_DIGIT, 1f)
                        x += SHARE_DIGIT
                        digit(5, x)
                        x += SHARE_DIGIT
                        mark(",", x, SHARE_MARK, clamp01(roll.thousands.value))
                        x += SHARE_MARK
                        digit(4, x)
                        x += SHARE_DIGIT
                        digit(3, x)
                        x += SHARE_DIGIT
                        digit(2, x)
                        x += SHARE_DIGIT
                        mark(".", x, SHARE_MARK, 1f)
                        x += SHARE_MARK
                        digit(1, x)
                        x += SHARE_DIGIT
                        digit(0, x)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ the keys */

@Composable
private fun PadKey(cell: Int, engine: SapEngine, art: SapArt) {
    val l = art.l
    val code = KEY_CODES[cell]
    Canvas(
        Modifier
            .size(l.keyW.dp, l.keyH.dp)
            .pointerInput(engine, cell) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // the digit is committed as the finger lands, not when it lifts
                    engine.keyDown(cell)
                    if (code == KEY_BACK) {
                        var lifted = false
                        withTimeoutOrNull(HOLD_MS) {
                            waitForUpOrCancellation()
                            lifted = true
                        }
                        if (!lifted) {
                            engine.keyHeld()
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                    }
                    engine.keyUp(cell)
                }
            },
    ) {
        engine.frame
        val p = max(engine.keyPress[cell].value, if (engine.autoKey == cell) engine.autoPress else 0f)
        val w = l.keyW
        val h = l.keyH
        val centre = Offset(w / 2f, h / 2f)
        val box = Size(w, h)
        val corner = CornerRadius(KEY_RADIUS)
        scale(density, density, pivot = Offset.Zero) {
            // lifted, the key throws a soft shadow; pressed, only the tight contact shadow is left
            scale(1f - 0.03f * p, pivot = centre) {
                boxShadow(art.keyAmbient, art.keyBox, w, h, KEY_RADIUS, 7f, 1f - 0.8f * p)
                drawRoundRect(KEY, size = box, cornerRadius = corner, alpha = 1f - 0.8f * p)
            }
            translate(top = 2f * p) {
                scale(1f + 0.012f * p, 1f - 0.05f * p, pivot = centre) {
                    boxShadow(art.keyContact, art.keyBox, w, h, KEY_RADIUS, 1f, 1f)
                    drawRoundRect(KEY, size = box, cornerRadius = corner)
                    drawPath(art.keyTop, Color.White, alpha = 0.95f)
                    drawPath(art.keyBottom, rgba(40, 32, 20, 0.05f))
                    if (code == KEY_BACK) {
                        translate(centre.x - 15f, centre.y - 15f) {
                            drawPath(art.back, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
                            drawPath(art.backCross, INK, style = Stroke(1.8f, cap = StrokeCap.Round))
                        }
                    } else {
                        drawIntoCanvas {
                            art.keyLabel.tint(INK, 1f)
                            it.nativeCanvas.drawText(KEY_LABELS[cell], centre.x, art.keyLabelBase, art.keyLabel)
                        }
                    }
                    if (p > 0.003f) drawRoundRect(KEY_PRESSED, size = box, cornerRadius = corner, alpha = p)
                }
            }
        }
    }
}
