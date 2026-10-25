package com.ngocdevv.nativecook.blocks.forms.punchcardform

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormArt.CARD_PAD
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormArt.HEAD_BOX
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.AUTO_CHAR
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.AUTO_TIME
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CANVAS_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CANVAS_Y
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_W
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.CARD_Y
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.COLS
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.FIELDS
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.HOLE_H
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.HOLE_W
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.LOOP
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.MARGIN_TOP
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.REGISTER_FROM
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.ROWS
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.ROW_PITCH
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.SCRIPT
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.SLOT_Y
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.STATION_X
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoBusy
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoColumn
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoFeed
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoField
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoLabel
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoRegister
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.autoTyped
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.cardLeft
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.colX
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.hash
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.holeMask
import com.ngocdevv.nativecook.blocks.forms.punchcardform.PunchCardFormMotion.rowY
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Punch Card Form — a port of punch-card-form (React Native) and PunchCardForm.swift.
 A member-card form where each typed letter is punched into its own column of an 80-column card:
 the card steps along under a punch head, holes glow with the lamp under the bed, chads tumble
 into the reader, and Submit feeds the card into the reader while a fresh one registers from the
 right. LOOP = 9 s: "ADA" 1.5–2.9 s, a tab to YEAR 3.15–3.5 s, "2026" 3.62–4.22 s, "WEST"
 4.46–5.0 s, Submit 5.2 s (feed 5.25–6.1 s, new card 5.7–7.2 s, lamps chase), "Welcome, member"
 until 7.6 s; rest. Real text fields: focusing one takes over with a blank card; typing punches.
 2 s after the last input the holes fade as the card slides back and the loop restarts.
 Reduced motion: the typed card, still, before it is submitted.
 */

private const val IDLE = 2f
private const val LABEL_H = 22f
private val LABELS = listOf("Submit card", "Reading…", "Welcome, member")
private val PLACEHOLDERS = listOf("Your name", "Year", "Wing")
/** Monospace advance of the field's type (0.6 em on Android's monospace), for the demo's caret. */
private const val ADVANCE = 17f * 0.6f
private const val STILL_T = 5.1f
private val Gold = Color(0xFFE2B05C)
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private val Register = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1.06f)

/** The desk: the loop clock, the user's card and the values that animate it. */
private class PunchDesk(val reduced: Boolean) {
    var t by mutableFloatStateOf(if (reduced) STILL_T else 0f)
    var owned by mutableStateOf(false)
    var clock by mutableFloatStateOf(0f)
    var returning = false
    var focused = false
    var busy = false
    var idle = 0f
    val chars = IntArray(COLS)
    val times = FloatArray(COLS) { -1f }
    var version by mutableIntStateOf(0)
    val col = Animatable(1f)
    val feed = Animatable(0f)
    val register = Animatable(1f)
    val ink = Animatable(1f)
    val label = Animatable(0f)

    fun clearCard() {
        chars.fill(0)
        times.fill(-1f)
        version++
    }
}

/** One frame's worth of the scene, from the loop or from the desk. */
private class PunchScene(
    val chars: IntArray,
    val times: FloatArray,
    val now: Float,
    val col: Float,
    val feed: Float,
    val register: Float,
    val busy: Float,
    val t: Float,
    val ink: Float,
)

private fun PunchDesk.scene(): PunchScene = when {
    owned -> PunchScene(chars, times, clock, col.value, feed.value, register.value, if (busy) 1f else 0f, clock % LOOP, ink.value)
    reduced -> PunchScene(AUTO_CHAR, AUTO_TIME, LOOP, autoColumn(STILL_T), 0f, 1f, 0f, 0f, 1f)
    else -> PunchScene(AUTO_CHAR, AUTO_TIME, t, autoColumn(t), autoFeed(t), autoRegister(t), autoBusy(t), t, 1f)
}

private fun clean(text: String, field: Int): String {
    val up = text.uppercase()
    val kept = up.filter { ch -> if (field == 1) ch in '0'..'9' else (ch in 'A'..'Z' || ch in '0'..'9' || ch == ' ' || ch == '-') }
    return kept.take(FIELDS[field].size)
}

/**
 * A member-card form whose letters are punched into an 80-column card as they are typed.
 *
 * @param autoPlay types a member card, submits it into the reader and loads a fresh one, every [LOOP] seconds.
 */
@Composable
fun PunchCardForm(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val screen = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val desk = remember(reduced) { PunchDesk(reduced) }
    var art by remember { mutableStateOf<PunchCardArtSet?>(null) }
    val appear = remember { Animatable(0f) }
    var values by remember { mutableStateOf(listOf("", "", "")) }
    var caret by remember { mutableIntStateOf(-1) }

    val images = remember(art) {
        art?.let { PunchImages(it.card.asImageBitmap(), it.bed.asImageBitmap(), it.reader.asImageBitmap(), it.head.asImageBitmap(), it.grain.asImageBitmap()) }
    }
    val holes = remember { PunchHoles() }

    LaunchedEffect(Unit) {
        art = withContext(Dispatchers.Default) { PunchCardFormArt.bake(screen.density) }
        appear.animateTo(1f, tween(260))
    }

    fun startReturn() {
        // Back to rest within 0.9 s: the holes fade as the card slides back to column 1.
        desk.returning = true
        scope.launch {
            val fade = launch { desk.ink.animateTo(0f, tween(320, easing = InOutQuad)) }
            desk.col.animateTo(1f, tween(760, easing = InOut))
            fade.join()
            desk.clearCard()
            desk.ink.snapTo(1f)
            desk.returning = false
            desk.t = 0f
            desk.owned = false
        }
    }

    // One frame loop: the loop clock while the demo plays, the desk's clock while a person owns it.
    LaunchedEffect(desk, auto) {
        snapshotFlow { (auto && !desk.owned && art != null) || desk.owned }.collectLatest { run ->
            if (!run) return@collectLatest
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 1f / 20f)
                    last = now
                    desk.clock += dt
                    if (!desk.owned) {
                        if (auto) desk.t = (desk.t + dt) % LOOP
                    } else if (!desk.focused && !desk.busy && !desk.returning) {
                        desk.idle += dt
                        if (desk.idle >= IDLE) startReturn()
                    }
                }
            }
        }
    }

    // The fields follow the demo's keystrokes (a dozen changes a loop, never per frame).
    LaunchedEffect(desk) {
        snapshotFlow { if (!desk.owned) autoTyped(desk.t) * 10 + (autoField(desk.t) + 1) else -1 }.collect { now ->
            if (now < 0) return@collect
            val n = now / 10
            values = listOf(
                SCRIPT[0].take(min(n, 3)),
                SCRIPT[1].take(max(0, min(n - 3, 4))),
                SCRIPT[2].take(max(0, n - 7)),
            )
            caret = now % 10 - 1
        }
    }

    /** A finger or a keyboard takes the desk: the demo stops, and a blank card waits at [startCol]. */
    fun takeOver(startCol: Int) {
        desk.idle = 0f
        if (desk.owned && !desk.returning) return
        desk.returning = false
        desk.clearCard()
        desk.busy = false
        scope.launch { desk.col.snapTo(startCol.toFloat()) }
        scope.launch { desk.ink.snapTo(1f) }
        scope.launch { desk.feed.snapTo(0f) }
        scope.launch { desk.register.snapTo(1f) }
        scope.launch { desk.label.snapTo(0f) }
        desk.owned = true
    }

    fun punchField(field: Int, text: String) {
        val f = FIELDS[field]
        for (i in 0 until f.size) {
            val code = if (i < text.length) text[i].code else 0
            val k = f.first - 1 + i
            if (desk.chars[k] != code) {
                desk.chars[k] = code
                desk.times[k] = if (code == 0) -1f else desk.clock
            }
        }
        desk.version++
        val next = min(f.first + text.length, f.first + f.size - 1)
        scope.launch {
            delay(70)
            desk.col.animateTo(next.toFloat(), spring(dampingRatio = 0.72f, stiffness = 433f))
        }
        desk.idle = 0f
    }

    fun submit() {
        desk.idle = 0f
        desk.busy = true
        scope.launch {
            desk.label.animateTo(1f, tween(250, easing = InOutQuad))
            delay(650)
            desk.label.animateTo(2f, tween(250, easing = InOutQuad))
            delay(1300)
            desk.label.animateTo(0f, tween(300, easing = InOutQuad))
        }
        scope.launch { desk.feed.animateTo(1f, tween(850, easing = InOut)) }
        scope.launch {
            delay(450)
            desk.register.animateTo(0f, tween(1500, easing = Register))
            desk.clearCard()
            desk.col.snapTo(1f)
            desk.feed.snapTo(0f)
            desk.register.snapTo(1f)
            desk.busy = false
            desk.idle = 0f
            values = listOf("", "", "")
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF121613))
            .clipToBounds(),
    ) {
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / screen.density
        val bottom = insets.getBottom(screen) / screen.density
        val roomW = maxWidth.value
        val roomH = maxHeight.value - top - bottom
        val k = min(min(roomW / DESIGN_W, roomH / DESIGN_H), 1.15f)
        val ox = (roomW - DESIGN_W * k) / 2f
        val oy = top + max(0f, (roomH - DESIGN_H * k) / 2f)

        // The room: deep green-black enamel with grain, a lamp's warmth and a vignette.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val base = Brush.verticalGradient(0f to Color(0xFF1A201C), 0.5f to Color(0xFF141915), 1f to Color(0xFF0E120F))
                    val lamp = Brush.radialGradient(
                        0f to Color(0x1AFFCE8C), 1f to Color(0x00FFCE8C), center = Offset(w * 0.3f, h * 0.38f), radius = w * 1.1f,
                    )
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x99020403),
                        center = Offset(w / 2f, h * 0.45f), radius = h * 0.75f,
                    )
                    val grain = images?.let { ShaderBrush(ImageShader(it.grain, TileMode.Repeated, TileMode.Repeated)) }
                    onDrawBehind {
                        drawRect(base)
                        if (grain != null) drawRect(grain, alpha = appear.value)
                        drawRect(lamp)
                        drawRect(vignette)
                    }
                },
        )

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed((DESIGN_W * density).toInt(), (DESIGN_H * density).toInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place((ox * density).toInt(), (oy * density).toInt()) }
                }
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Column(
                Modifier
                    .offset(24.dp, 8.dp)
                    .width((DESIGN_W - 48f).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText("ANALYTICAL SOCIETY", style = TextStyle(color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
                BasicText(
                    "Member card",
                    style = TextStyle(color = Color(0xFFEFE8D6), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
                )
                BasicText(
                    "Each letter is punched into its own column.",
                    style = TextStyle(color = Color(0x99EFE8D6), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }

            Row(
                Modifier
                    .offset(16.dp, 98.dp)
                    .size((DESIGN_W - 32f).dp, 58.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FIELDS.forEachIndexed { i, f ->
                    val active = caret == i
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .weight(if (i == 0) 1.7f else if (i == 1) 1f else 1.3f)
                            .fillMaxHeight()
                            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 14.dp)
                            .background(if (active) Color(0x14E2B05C) else Color(0x0FECE4CE), shape)
                            .border(0.5.dp, if (active) Color(0xB3E2B05C) else Color(0x29ECE4CE), shape)
                            .drawWithContent {
                                drawContent()
                                drawLine(Color(0x0DFFFFFF), Offset(14.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 14.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
                            }
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    ) {
                        BasicText(
                            "${f.label} · ${f.first}–${f.first + f.size - 1}",
                            style = TextStyle(
                                color = Color(0x8CECE4CE), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .height(28.dp)
                                .fillMaxWidth(),
                        ) {
                            val style = TextStyle(color = Color(0xFFF2EBD8), fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = values[i],
                                onValueChange = { text ->
                                    val next = clean(text, i)
                                    if (next == values[i]) return@BasicTextField
                                    if (next.length > values[i].length) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    values = values.toMutableList().also { it[i] = next }
                                    takeOver(f.first)
                                    punchField(i, next)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .semantics { contentDescription = "${f.label}, columns ${f.first} to ${f.first + f.size - 1}" }
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            desk.focused = true
                                            if (!desk.owned || desk.returning) {
                                                values = listOf("", "", "")
                                            }
                                            caret = -1
                                            takeOver(f.first)
                                        } else if (desk.focused && !state.hasFocus) {
                                            desk.focused = false
                                            desk.idle = 0f
                                        }
                                    },
                                singleLine = true,
                                textStyle = style,
                                cursorBrush = SolidColor(Gold),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false,
                                    keyboardType = if (i == 1) KeyboardType.Number else KeyboardType.Ascii,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (values[i].isEmpty()) {
                                            BasicText(PLACEHOLDERS[i], style = style.copy(color = Color(0x52ECE4CE)))
                                        }
                                        inner()
                                    }
                                },
                            )
                            // The demo's caret (a person's typing shows the field's own cursor instead).
                            if (active && !desk.owned) {
                                Box(
                                    Modifier
                                        .offset((values[i].length * ADVANCE).dp, 4.dp)
                                        .size(2.dp, 20.dp)
                                        .graphicsLayer { alpha = if (sin(2f * PI.toFloat() * 9f * desk.t / LOOP) > -0.2f) 1f else 0f }
                                        .background(Gold, RoundedCornerShape(1.dp)),
                                )
                            }
                        }
                    }
                }
            }

            // The hero: bed, cards, holes, head, chads and reader, drawn from the scene every frame.
            Canvas(
                Modifier
                    .offset(0.dp, CANVAS_Y.dp)
                    .size(DESIGN_W.dp, CANVAS_H.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.changes.any { it.pressed && !it.previousPressed }) {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }
                            }
                        }
                    },
            ) {
                desk.version
                val s = desk.scene()
                val im = images
                scale(density, density, pivot = Offset.Zero) {
                    if (im == null) {
                        drawRoundRect(Color(0xFF2C332E), Offset(6f, 6f), Size(DESIGN_W - 12f, SLOT_Y + 4f), androidx.compose.ui.geometry.CornerRadius(18f))
                        drawRect(Color(0xFFE2D0A4), Offset(cardLeft(s.col), CARD_Y), Size(CARD_W, CARD_H))
                        return@scale
                    }
                    drawPunchScene(s, im, holes, appear.value)
                }
            }

            val press = remember { MutableInteractionSource() }
            val down by press.collectIsPressedAsState()
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .offset(16.dp, 632.dp)
                    .size((DESIGN_W - 32f).dp, 52.dp)
                    .scale(if (down) 0.97f else 1f)
                    .cssBoxShadow(Color(0x73000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x40C68D39), 10.dp, 24.dp, 26.dp)
                    .background(Brush.verticalGradient(0f to Color(0xFFF3CF86), 0.55f to Color(0xFFE2AE57), 1f to Color(0xFFC68D39)), shape)
                    .drawWithContent {
                        drawContent()
                        val r = 26.dp.toPx()
                        drawLine(
                            Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color(0x99FFF6DC), 0.88f to Color(0x99FFF6DC), 1f to Color.Transparent),
                            Offset(r * 0.5f, 0.5.dp.toPx()), Offset(size.width - r * 0.5f, 0.5.dp.toPx()), 1.dp.toPx(),
                        )
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Submit card"
                    }
                    .clickable(press, null) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        takeOver(1)
                        submit()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(LABEL_H.dp)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .graphicsLayer {
                                val at = if (desk.owned) desk.label.value else autoLabel(desk.t)
                                translationY = -at * LABEL_H * density
                            },
                    ) {
                        for (l in LABELS) {
                            Box(Modifier.fillMaxWidth().height(LABEL_H.dp), contentAlignment = Alignment.Center) {
                                BasicText(
                                    l,
                                    style = TextStyle(color = Color(0xFF2A1A06), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                )
                            }
                        }
                    }
                }
            }

            BasicText(
                "Cards are read at the front desk · Gallery 3",
                Modifier
                    .offset(24.dp, 700.dp)
                    .width((DESIGN_W - 48f).dp),
                style = TextStyle(color = Color(0x73EFE8D6), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}

private class PunchImages(val card: ImageBitmap, val bed: ImageBitmap, val reader: ImageBitmap, val head: ImageBitmap, val grain: ImageBitmap)

/** The holes of the typed card in card dp, rebuilt only when the set of open holes changes. */
private class PunchHoles {
    val path = Path()
    private var key = -1L
    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC864.toInt()
        maskFilter = BlurMaskFilter((5f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    val glyph = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 10f
        color = 0xFF2A2620.toInt()
    }

    fun update(s: PunchScene) {
        var k = 17L
        var any = false
        for (i in 0 until COLS) {
            val code = s.chars[i]
            if (code == 0) continue
            if (s.now - s.times[i] <= 0.025f) continue
            k = k * 31 + (i * 128 + code)
            any = true
        }
        if (!any) k = 0L
        if (k == key) return
        key = k
        path.reset()
        for (i in 0 until COLS) {
            val code = s.chars[i]
            if (code == 0 || s.now - s.times[i] <= 0.025f) continue
            val cx = colX(i + 1f)
            val mask = holeMask(code)
            for (r in 0 until ROWS) {
                if (mask and (1 shl r) == 0) continue
                val cy = rowY(r)
                path.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        cx - HOLE_W / 2f, cy - HOLE_H / 2f, cx + HOLE_W / 2f, cy + HOLE_H / 2f,
                        androidx.compose.ui.geometry.CornerRadius(1.1f),
                    ),
                )
            }
        }
    }

    val empty get() = key == 0L || key == -1L
}

private fun DrawScope.drawImageAt(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, pivot = Offset.Zero)
    }) {
        drawImage(img, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** Height of a quick punch stroke: down in 30 ms, back up by 90 ms. */
private fun strokeAt(dt: Float) = when {
    dt < 0f || dt > 0.09f -> 0f
    dt < 0.03f -> dt / 0.03f
    else -> 1f - (dt - 0.03f) / 0.06f
}

private val ChadPath = Path()

private fun DrawScope.drawPunchScene(s: PunchScene, im: PunchImages, holes: PunchHoles, appear: Float) {
    drawImageAt(im.bed, 0f, 0f, DESIGN_W, CANVAS_H, appear)
    val cw = CARD_W + CARD_PAD * 2
    val ch = CARD_H + CARD_PAD * 2
    // The next card, registering from the right (under the typed one while that one leaves).
    if (s.register < 1f) {
        val x = cardLeft(1f) + s.register * REGISTER_FROM
        drawImageAt(im.card, x - CARD_PAD, CARD_Y - CARD_PAD, cw, ch, appear)
    }
    if (s.feed < 1f && s.register > 0.02f) {
        val x0 = cardLeft(s.col)
        val y0 = CARD_Y + s.feed * (CARD_H + 24f)
        clipRect(0f, 0f, DESIGN_W, SLOT_Y + 6f) {
            drawImageAt(im.card, x0 - CARD_PAD, y0 - CARD_PAD, cw, ch, appear)
            holes.update(s)
            if (!holes.empty && s.ink > 0f) {
                translate(x0, y0) {
                    // Light from the lamp under the bed comes through the holes.
                    drawPath(
                        holes.path,
                        Brush.verticalGradient(0f to Color(0xFFFFFBEA), 0.5f to Color(0xFFFFEBB8), 1f to Color(0xFFFFD27E), startY = 0f, endY = CARD_H),
                        alpha = s.ink,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        holes.glow.alpha = (0.7f * s.ink * 255f).toInt().coerceIn(0, 255)
                        drawIntoCanvas { it.nativeCanvas.drawPath(holes.path.asAndroidPath(), holes.glow) }
                    }
                    drawPath(holes.path, Color(0xFF7A6238), alpha = 0.9f * s.ink, style = Stroke(1f))
                }
            }
            // The character prints along the top edge as the column is punched.
            drawIntoCanvas { canvas ->
                for (i in 0 until COLS) {
                    val code = s.chars[i]
                    if (code == 0) continue
                    val age = s.now - s.times[i]
                    if (age < 0f) continue
                    val cx = x0 + colX(i + 1f)
                    if (cx < -10f || cx > DESIGN_W + 10f) continue
                    val a = min(1f, age / 0.08f)
                    holes.glyph.alpha = (a * s.ink * 255f).toInt().coerceIn(0, 255)
                    canvas.nativeCanvas.drawText(code.toChar().toString(), cx - 3f, y0 + MARGIN_TOP - 12f + (1f - a) * -2f, holes.glyph)
                }
            }
        }
    }
    // Column pointer and head.
    var strike = 0f
    for (i in 0 until COLS) if (s.chars[i] != 0) strike = max(strike, strokeAt(s.now - s.times[i]))
    drawRect(Color(0x59E0533F), Offset(STATION_X - 0.5f, CARD_Y + MARGIN_TOP - 6f), Size(1f, ROWS * ROW_PITCH + 6f))
    drawImageAt(im.head, STATION_X - HEAD_BOX / 2f, CARD_Y - 2f + strike * 3f - HEAD_BOX / 2f, HEAD_BOX, HEAD_BOX, appear)
    // Chads, tumbling out of the station and into the reader's bin.
    ChadPath.reset()
    var n = 0
    for (i in 0 until COLS) {
        val code = s.chars[i]
        if (code == 0) continue
        val age = s.now - s.times[i]
        if (age < 0.02f || age > 0.9f) continue
        val mask = holeMask(code)
        for (r in 0 until ROWS) {
            if (mask and (1 shl r) == 0) continue
            val a = age - 0.02f
            val id = (i + 1) * 16f + r
            val vx = (hash(id) - 0.5f) * 90f
            val vy = -70f - hash(id + 7f) * 90f
            val spin = (hash(id + 13f) - 0.5f) * 26f
            val px = STATION_X + vx * a
            val py = CARD_Y + rowY(r) + vy * a + 0.5f * 1500f * a * a
            if (py > SLOT_Y + 8f) continue
            val turn = spin * a
            val flat = cos((5f + hash(id + 3f) * 9f) * a)
            val ca = cos(turn)
            val sa = sin(turn)
            val hw = HOLE_W / 2f * max(0.25f, abs(flat))
            val hh = HOLE_H / 2f
            ChadPath.moveTo(px - hw * ca + hh * sa, py - hw * sa - hh * ca)
            ChadPath.lineTo(px + hw * ca + hh * sa, py + hw * sa - hh * ca)
            ChadPath.lineTo(px + hw * ca - hh * sa, py + hw * sa + hh * ca)
            ChadPath.lineTo(px - hw * ca - hh * sa, py - hw * sa + hh * ca)
            ChadPath.close()
            n++
        }
    }
    if (n > 0) {
        translate(1.5f, 3f) { drawPath(ChadPath, Color.Black, alpha = 0.25f) }
        drawPath(ChadPath, Color(0xFFE6D4A8))
        drawPath(ChadPath, Color(0xFFA08A5C), style = Stroke(0.6f))
    }
    // The reader shakes a quarter point while it runs (a 30 Hz whirr: 270 cycles per loop).
    val shake = s.busy * 0.35f * sin(2f * PI.toFloat() * 270f * s.t / 9f)
    drawImageAt(im.reader, 0f, shake, DESIGN_W, CANVAS_H, appear)
    // Lamps: ready (green) at rest; while reading they chase, amber and green.
    for (i in 0 until 4) {
        val x = 36f + i * 30f
        val y = SLOT_Y + 80f
        val chase = 0.5f + 0.5f * sin(2f * PI.toFloat() * 18f * s.t / 9f - i * 1.4f)
        val idleOn = if (i == 0) 1f else 0.08f
        val on = idleOn * (1f - s.busy) + s.busy * (if (i % 2 == 0) chase else 1f - chase)
        val hue = if (i == 0 || i == 2) Color(0xFF7CFF9A) else Color(0xFFFFC25A)
        drawCircle(hue, 4.2f, Offset(x, y), alpha = (0.15f + 0.85f * on) * appear)
        drawCircle(
            Brush.radialGradient(0f to hue.copy(alpha = 0.55f * on), 0.45f to hue.copy(alpha = 0.3f * on), 1f to hue.copy(alpha = 0f), center = Offset(x, y), radius = 19f),
            19f, Offset(x, y), blendMode = BlendMode.Plus, alpha = appear,
        )
    }
}
