package com.ngocdevv.nativecook.blocks.inputs.morphotpinput

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BACKGROUND = 0xFF0C0D10.toInt()
private const val ACCENT = 0xFF7CF2C5.toInt()
private const val ERROR = 0xFFFF5A5F.toInt()
private const val CELL_FILL = 0xFF16171C.toInt()
private const val CELL_FILLED = 0xFF1D1E24.toInt()
private const val CELL_ERROR = 0xFF35161A.toInt()
private const val INK = 0xFF0C0D10.toInt()
private const val DIGIT_INK = 0xFFF4F5F7.toInt()
private val ACTIVE_BORDER = withAlpha(ACCENT, 0.34f)

private val Paper = Color(0xFFF4F5F7)
private val Accent = Color(ACCENT)

/** Mixes two ARGB colours the way Reanimated's `interpolateColor` does: in 2.2 gamma, alpha straight. */
private fun mixColor(a: Int, b: Int, t: Float): Int {
    val k = clamp01(t).toDouble()
    fun channel(shift: Int): Int {
        val x = ((a shr shift) and 0xFF) / 255.0
        val y = ((b shr shift) and 0xFF) / 255.0
        val lx = x.pow(2.2)
        val ly = y.pow(2.2)
        return ((lx + (ly - lx) * k).pow(1 / 2.2) * 255).roundToInt()
    }
    val alpha = (((a ushr 24) and 0xFF) + ((((b ushr 24) and 0xFF) - ((a ushr 24) and 0xFF)) * k)).roundToInt()
    return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (((color ushr 24) and 0xFF) * clamp01(alpha)).roundToInt()
    return (a shl 24) or (color and 0x00FFFFFF)
}

// Mask-filter blurs draw on a hardware canvas from Android 9; below that digits roll in sharp and
// the caret has no glow.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/** Paints and scratch objects reused every frame. */
private class OtpPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val clip = Path()
    val digit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DIGIT_SIZE
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
    }
    val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val rect = RectF()
    val checkPath = Path().apply {
        moveTo(-11f, 1f)
        lineTo(-3.5f, 8.5f)
        lineTo(12f, -8f)
    }
    val measure = PathMeasure(checkPath, false)
    val trimmed = Path()
    val blurs = HashMap<Int, BlurMaskFilter>()

    /** A blur of `sigma` dp, cached by hundredths. */
    fun blur(sigma: Float): BlurMaskFilter {
        val key = (sigma * 100).roundToInt()
        return blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 100f), BlurMaskFilter.Blur.NORMAL) }
    }
}

/**
 * A six-digit code input drawn as soft rounded cells. The focused cell's outline collapses into a
 * glowing, breathing caret; typed digits rise softly into their cells. A wrong code
 * shakes the row on a damped spring and flushes it red before the digits drop out; the right one
 * slides the cells together into a single pill that closes into a circle, draws a check and
 * throws a ring of sparks. A hidden text field takes real keyboard and one-time-code input.
 *
 * @param code The code that unlocks it.
 * @param destination Where the code was sent, shown under the title.
 * @param autoPlay Types a wrong code, shakes, types the right one, merges into a check, then starts over.
 * @param onVerified Called once the right code is in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorphOtpInput(
    modifier: Modifier = Modifier,
    code: String = "481930",
    destination: String = "nora@larkpost.com",
    autoPlay: Boolean = false,
    onVerified: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val engine = remember(code) { OtpEngine(code) }
    engine.reduced = reduced
    engine.onVerified = onVerified
    engine.onTap = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.onError = {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
    engine.onSuccess = {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val paints = remember { OtpPaints() }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(engine, autoPlay) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                // The first frame has no previous one; Reanimated counts it as 16 ms.
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, 0.064)
                last = now
                engine.step(dt, autoPlay)
            }
        }
    }

    val imeVisible = WindowInsets.isImeVisible
    SideEffect { engine.imeVisible = imeVisible }

    // The hidden field takes the keyboard when the row is tapped and gives it back on success.
    val request = engine.focusRequest.intValue
    LaunchedEffect(request) {
        if (request > 0) {
            focus.requestFocus()
            keyboard?.show()
        } else if (request < 0) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(BACKGROUND))) {
        val width = maxWidth.value
        val cellW = maxOf(24f, min(CELL_MAX_W, (width - 56f - CELL_GAP * (LENGTH - 1)) / LENGTH))
        val rowW = cellW * LENGTH + CELL_GAP * (LENGTH - 1)
        val rowTop = safeTop + 262f

        engine.frame.longValue

        // The heading.
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (safeTop + 48f).dp)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .padding(bottom = 6.dp)
                    .size(56.dp)
                    .background(Accent.copy(alpha = 0.1f), CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Drafts, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val title = TextStyle(color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, textAlign = TextAlign.Center)
                BasicText(
                    "Check your inbox",
                    modifier = Modifier.graphicsLayer { alpha = 1f - engine.shown(engine.successShown) }.then(if (engine.phase == Phase.Success) Modifier.clearAndSetSemantics {} else Modifier),
                    style = title,
                )
                BasicText(
                    "You're in",
                    modifier = Modifier.graphicsLayer { alpha = engine.shown(engine.successShown) }.then(if (engine.phase != Phase.Success) Modifier.clearAndSetSemantics {} else Modifier),
                    style = title,
                )
            }
            BasicText(
                buildAnnotatedString {
                    append("Enter the 6-digit code we sent to\n")
                    withStyle(SpanStyle(color = Paper, fontWeight = FontWeight.SemiBold)) { append(destination) }
                },
                style = TextStyle(color = Paper.copy(alpha = 0.55f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center),
            )
        }

        // The row.
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            // Read here so the row is drawn again every frame.
            engine.frame.longValue
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawRow(it.nativeCanvas, engine, paints, cellW, width / 2f + engine.shake, rowTop + CELL_H / 2f, density.density, reduced) }
            }
        }

        // A field the size of a pixel that takes real typing, pastes and one-time codes from messages.
        BasicTextField(
            value = TextFieldValue(engine.value, TextRange(engine.value.length)),
            onValueChange = { engine.type(it.text.take(LENGTH)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .offset((-10).dp, (-10).dp)
                .graphicsLayer { alpha = 0f }
                .focusRequester(focus)
                .onFocusChanged { engine.fieldFocused = it.isFocused }
                .semantics { contentType = ContentType.SmsOtpCode },
        )

        // Worked out here, in a scope that runs every frame: the semantics block alone reads no state,
        // so it would keep its first label.
        val rowLabel = if (engine.phase == Phase.Success) {
            "Code verified"
        } else {
            "Verification code, ${engine.value.length} of $LENGTH digits entered. Opens the keyboard"
        }
        Box(
            Modifier
                .offset(x = ((width - rowW) / 2f - 8f).dp, y = (rowTop - 12f).dp)
                .width((rowW + 16f).dp)
                .height((CELL_H + 24f).dp)
                .pointerInput(engine) { detectTapGestures { engine.tapRow() } }
                .semantics {
                    role = Role.Button
                    contentDescription = rowLabel
                },
        )

        // The status line under the row, clear of the burst (BURST_MAX from the row's centre).
        Box(
            Modifier
                .offset(y = (rowTop + CELL_H / 2f + BURST_MAX + 10f).dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val status = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            val left = engine.resendLeft()
            Box(
                Modifier
                    .graphicsLayer { alpha = engine.shown(engine.idleShown) }
                    .then(if (engine.phase != Phase.Idle) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                if (left > 0) {
                    BasicText(
                        buildAnnotatedString {
                            append("Resend code in ")
                            withStyle(SpanStyle(color = Paper, fontFeatureSettings = "tnum")) { append("0:${left.toString().padStart(2, '0')}") }
                        },
                        style = status.copy(color = Paper.copy(alpha = 0.55f)),
                    )
                } else {
                    BasicText(
                        "Resend code",
                        modifier = Modifier.then(
                            if (engine.phase == Phase.Idle) {
                                Modifier
                                    .pointerInput(engine) { detectTapGestures { engine.resend() } }
                                    .semantics { role = Role.Button }
                            } else {
                                Modifier
                            },
                        ),
                        style = status.copy(color = Accent),
                    )
                }
            }
            BasicText(
                "That code didn't match. Try again.",
                modifier = Modifier
                    .graphicsLayer { alpha = engine.shown(engine.errorShown) }
                    .then(if (engine.phase != Phase.Error) Modifier.clearAndSetSemantics {} else Modifier),
                style = status.copy(color = Color(ERROR)),
            )
            BasicText(
                "Verified. Welcome back, Nora.",
                modifier = Modifier
                    .graphicsLayer { alpha = engine.shown(engine.successShown) }
                    .then(if (engine.phase != Phase.Success) Modifier.clearAndSetSemantics {} else Modifier),
                style = status.copy(color = Accent),
            )
        }

        Box(Modifier.fillMaxSize().padding(bottom = (safeBottom + 24f).dp), contentAlignment = Alignment.BottomCenter) {
            BasicText("Codes expire after 10 minutes", style = TextStyle(color = Paper.copy(alpha = 0.32f), fontSize = 13.sp))
        }
    }
}

// ── Drawing (dp units, about the row's centre) ───────────────────────────────────────────────────

private fun drawRow(
    canvas: android.graphics.Canvas,
    engine: OtpEngine,
    p: OtpPaints,
    cellW: Float,
    cx: Float,
    cy: Float,
    density: Float,
    reduced: Boolean,
) {
    canvas.save()
    canvas.translate(cx, cy)
    val m = engine.merge.float
    val boxM = if (reduced) 0f else m
    // Under reduced motion the cells stay put and fade; the circle fades in on its own.
    val visible = if (reduced) 1f - clamp01(m) else 1f
    val error = engine.errorAmt.float
    val breath = (0.55 + 0.45 * cos(2 * PI * engine.now / CARET_BREATH)).toFloat()

    engine.cells.forEachIndexed { i, cell ->
        val b = engine.box(i, boxM, cellW)
        val r = min(b[2], min(b[1] / 2f, CELL_H / 2f))
        p.rect.set(b[0] - b[1] / 2f, -CELL_H / 2f, b[0] + b[1] / 2f, CELL_H / 2f)
        val base = mixColor(CELL_FILL, CELL_FILLED, cell.fill.float)
        val hurt = mixColor(base, CELL_ERROR, error)
        val fill = if (reduced) hurt else mixColor(hurt, ACCENT, clamp01(m - 1f))
        p.fill.color = withAlpha(fill, visible)
        canvas.drawRoundRect(p.rect, r, r, p.fill)

        // The hairline border, half a stroke inside the cell; mint while the cell has focus.
        val rest = if (cell.fill.float > 0.5f) withAlpha(0xFFFFFFFF.toInt(), 0.20f) else withAlpha(0xFFFFFFFF.toInt(), 0.09f)
        val edge = mixColor(mixColor(rest, ACTIVE_BORDER, cell.lit.float), ERROR, error)
        p.stroke.strokeWidth = BORDER
        p.stroke.color = withAlpha(edge, visible * (1f - clamp01(m * 3f)))
        p.rect.inset(BORDER / 2f, BORDER / 2f)
        val ir = maxOf(0f, min(r - BORDER / 2f, min(p.rect.width() / 2f, p.rect.height() / 2f)))
        canvas.drawRoundRect(p.rect, ir, ir, p.stroke)

        // The mint outline collapsing into the caret: the box narrows to a sliver and shortens while
        // its stroke thickens a little; once it lets go it shrinks and fades where it stands.
        val f = cell.focus.float
        val fc = clamp01(f)
        val caretW = maxOf(0.01f, (cellW - BORDER) + (0.01f - (cellW - BORDER)) * f)
        val caretH = ((CELL_H - BORDER) + (CARET_H - (CELL_H - BORDER)) * f) * (1f + (0.55f - 1f) * cell.leave.float)
        val caretR = maxOf(0f, min((CELL_RADIUS - BORDER / 2f) + (CARET_W / 2f - (CELL_RADIUS - BORDER / 2f)) * f, min(caretW / 2f, caretH / 2f)))
        val stroke = 1f + (CARET_W - 1f) * f
        // Breathing only starts once the caret has formed, and nothing shows while the row is merged.
        val settled = clamp01((fc - 0.75f) / 0.25f)
        // The collapsing outline starts faint and only reaches full strength as it becomes the caret.
        val caretOpacity = cell.lit.float * (0.3f + 0.7f * fc) * (1f - cell.leave.float) * (1f + (breath - 1f) * settled) * (1f - clamp01(m * 2f))
        if (caretOpacity > 0.002f) {
            // The glow belongs to the formed caret only: a soft bar behind it, never a blurred outline.
            val glowOpacity = caretOpacity * fc * fc * fc * 0.7f
            if (CanBlur && glowOpacity > 0.002f) {
                p.rect.set(b[0] - CARET_W / 2f, -caretH / 2f, b[0] + CARET_W / 2f, caretH / 2f)
                p.glow.maskFilter = p.blur(CARET_GLOW)
                p.glow.color = withAlpha(ACCENT, visible * glowOpacity)
                canvas.drawRoundRect(p.rect, CARET_W / 2f, CARET_W / 2f, p.glow)
            }
            p.rect.set(b[0] - caretW / 2f, -caretH / 2f, b[0] + caretW / 2f, caretH / 2f)
            p.stroke.strokeWidth = stroke
            p.stroke.color = withAlpha(ACCENT, visible * caretOpacity)
            canvas.drawRoundRect(p.rect, caretR, caretR, p.stroke)
        }
    }

    // Digits are drawn after every box so merged boxes never cover them, each clipped to its own
    // cell: typed, it rises softly into place; cleared, it sinks and fades.
    val ink = mixColor(DIGIT_INK, ERROR, error)
    engine.cells.forEachIndexed { i, cell ->
        val k = cell.roll.float
        val shown = if (k < 1f) clamp01(k * 1.4f) else clamp01((2f - k) * 1.5f)
        val opacity = shown * (1f - clamp01((m - 1f) * 3f)) * visible
        if (opacity <= 0.002f) return@forEachIndexed
        val b = engine.box(i, boxM, cellW)
        val y = if (reduced) 0f else if (k < 1f) (1f - k) * ROLL_FROM else (k - 1f) * ROLL_OUT
        val s = if (reduced) 1f else 1f + (0.6f - 1f) * clamp01(m - 1f)
        // The blur is set in device pixels, so it reads the same at any scale.
        val blur = if (reduced) 0f else min(ROLL_BLUR, abs(1f - k) * ROLL_BLUR * 1.5f) / density
        canvas.save()
        p.rect.set(b[0] - b[1] / 2f + 1f, -CELL_H / 2f + 1f, b[0] + b[1] / 2f - 1f, CELL_H / 2f - 1f)
        val cr = maxOf(0f, min(b[2] - 1f, min(p.rect.width() / 2f, p.rect.height() / 2f)))
        p.clip.rewind()
        p.clip.addRoundRect(p.rect, cr, cr, Path.Direction.CW)
        canvas.clipPath(p.clip)
        canvas.translate(b[0], y)
        canvas.scale(s, s)
        p.digit.color = withAlpha(ink, opacity)
        p.digit.maskFilter = if (CanBlur && blur > 0.05f) p.blur(blur) else null
        canvas.drawText(cell.glyph, -p.digit.measureText(cell.glyph) / 2f, DIGIT_SIZE * 0.36f, p.digit)
        canvas.restore()
    }

    drawSuccess(canvas, engine, p, reduced)
    canvas.restore()
}

/** The check drawn into the merged circle, the rings it throws and a scatter of spark dashes. */
private fun drawSuccess(canvas: android.graphics.Canvas, engine: OtpEngine, p: OtpPaints, reduced: Boolean) {
    val radius = CELL_H / 2f
    val m = engine.merge.float
    val check = engine.check.float
    val burst = engine.burst.float
    canvas.save()
    if (!reduced) {
        val pop = 1f + sin(PI.toFloat() * check) * 0.07f
        canvas.scale(pop, pop)
    } else {
        // Under reduced motion the cells only fade, so the circle fades in here instead.
        p.fill.color = withAlpha(ACCENT, clamp01(m - 1f))
        canvas.drawCircle(0f, 0f, radius, p.fill)
    }
    if (check > 0f) {
        p.trimmed.rewind()
        p.measure.getSegment(0f, p.measure.length * min(1f, check), p.trimmed, true)
        p.check.color = INK
        canvas.drawPath(p.trimmed, p.check)
    }
    canvas.restore()
    if (burst <= 0f || burst >= 1f) return
    // The trailing ring starts RING_LAG later and stops short of the first.
    if (burst > RING_LAG) {
        val trail = clamp01((burst - RING_LAG) / (1f - RING_LAG))
        p.stroke.strokeWidth = 1.25f
        p.stroke.color = withAlpha(ACCENT, 0.45f * fadeAfter(trail, 0.4f))
        canvas.drawCircle(0f, 0f, radius + 1f + (radius * (RING_REACH - 0.32f) - radius - 1f) * EaseOut.transform(trail), p.stroke)
    }
    // The leading ring: crisp all the way (never under 1.25 dp), at full strength until 55% of the
    // burst, then gone by its end.
    val eased = EaseOut.transform(burst)
    p.stroke.strokeWidth = 3f + (1.25f - 3f) * burst
    p.stroke.color = withAlpha(ACCENT, fadeAfter(burst, 0.55f))
    canvas.drawCircle(0f, 0f, radius + 1f + (radius * RING_REACH - radius - 1f) * eased, p.stroke)
    // Short dashes shooting straight out, shortening as they go.
    p.dash.color = withAlpha(ACCENT, fadeAfter(burst, 0.45f))
    for (i in 0 until SPARKS) {
        val angle = i.toFloat() / SPARKS * PI.toFloat() * 2f + PI.toFloat() / SPARKS
        val long = i % 2 == 0
        val reach = if (long) BURST_MAX - 3f else BURST_MAX - 12f
        val d = radius + 6f + (reach - radius - 6f) * eased
        val from = if (long) 6f else 4.5f
        val half = from + (1f - from) * eased
        p.dash.strokeWidth = if (long) 2.4f else 2f
        canvas.drawLine(cos(angle) * (d - half), sin(angle) * (d - half), cos(angle) * (d + half), sin(angle) * (d + half), p.dash)
    }
}
