package com.ngocdevv.nativecook.blocks.inputs.inksignaturepad

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun photo(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"
// Cream paper with a fine speckle for the pad; white laid paper for the pages behind it.
private val PAD_PAPER = photo("photo-1629968417850-3505f5180761", 900)
private val PAGE_PAPER = photo("photo-1601662528567-526cd06f6582", 600)

internal val BACKGROUND = Color(0xFF15120E)
private val CREAM = Color(0xFFEFE6D5)
private val MUTED = Color(0xFF8C8170)
private val PRINT = Color(0xFF463D31)
private val RULE = Color(0xFF9C8F78)

// Layout, in dp.
private const val SIDE = 18f
private const val PAD_TOP = 96f
private const val PAD_RADIUS = 22f
private const val BUTTON_HEIGHT = 54f
private const val BUTTONS_GAP = 22f
// The signature line sits this share of the pad's height down, the signature's baseline LINE_LIFT above it.
private const val LINE_AT = 0.7f
private const val LINE_LIFT = 5.0
// The signature runs from SIGN_LEFT (clear of the × mark) to SIGN_RIGHT dp from the pad's right edge.
private const val SIGN_LEFT = 50.0
private const val SIGN_RIGHT = 26.0
// Done: the page shrinks to CARD_SCALE; the wax drops from STAMP_FROM×.
private const val CARD_SCALE = 0.56f
private const val STAMP_FROM = 1.75f
// The two pages behind the signed one, as offsets (dp, before scaling) and turns (°).
private val BACK_PAGES = listOf(Triple(-26f, -18f, -5.5f), Triple(24f, -8f, 4f))

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/**
 * A signature pad on cream paper. The finger writes in fountain-pen ink whose width follows its
 * speed, drawn through as smooth curves, glossy while wet and drying to blue-black. Clear sweeps a
 * felt wiper across it, Replay redraws the signature at the speed it was written, and Done shrinks
 * the page onto the rest of the lease and presses a wax seal on it.
 *
 * @param autoPlay signs the name, replays it, seals the page, opens it again and wipes it, over and over.
 * @param onDone called with the ink (x, y, ms, width, stroke per sample) when the page is sealed by hand.
 */
@Composable
fun InkSignaturePad(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    signer: String = "Mara Ellison",
    role: String = "Tenant",
    date: String = "13 Oct 2026",
    monogram: String = "ME",
    onDone: ((DoubleArray) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val done by rememberUpdatedState(onDone)
    val model = remember { PadModel() }
    val painter = remember { InkPainter() }
    // Each frame bumps tick, which everything drawn from the model reads; wake restarts the clock.
    var tick by remember { mutableLongStateOf(0L) }
    var wake by remember { mutableIntStateOf(0) }
    var hasInk by remember { mutableStateOf(false) }
    var sealed by remember { mutableStateOf(false) }
    var paper by remember { mutableStateOf<Bitmap?>(null) }

    model.reduced = reduced
    model.onDone = { done?.invoke(it) }
    model.thud = { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS) }
    model.tap = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

    LaunchedEffect(context) {
        // A software bitmap: it is drawn through the native canvas, which may not take a hardware one.
        val request = ImageRequest.Builder(context).data(PAD_PAPER).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        paper = result.image.toBitmap()
    }

    LaunchedEffect(wake) {
        while (true) {
            withFrameNanos { nanos ->
                model.advance(nanos)
                tick++
                if (hasInk != model.hasInk) hasInk = model.hasInk
                if (sealed != model.sealed) sealed = model.sealed
            }
            if (!model.live) {
                model.rest()
                break
            }
        }
    }
    fun poke() {
        wake++
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND).clipToBounds()) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value
        val top = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density) / density.density
        val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
        // Never negative: the first measure can report a zero size.
        val padW = max(0f, screenW - SIDE * 2)
        val padTop = top + PAD_TOP
        val buttonsTop = screenH - bottom - 14 - BUTTON_HEIGHT
        val padH = max(300f, buttonsTop - BUTTONS_GAP - padTop)
        val lineY = (padH * LINE_AT).roundToInt().toFloat()
        // Where the sealed card sits, and the seal on it, over its lower right corner.
        val cardCentreY = top + (screenH - top - bottom) * 0.45f
        val shiftY = cardCentreY - (padTop + padH / 2)
        val cardRight = screenW / 2 + padW * CARD_SCALE / 2
        val cardBottom = cardCentreY + padH * CARD_SCALE / 2
        val sealX = cardRight - SEAL_SIZE / 2 - 22
        val sealY = cardBottom - SEAL_SIZE / 2 - 24

        LaunchedEffect(autoPlay, reduced, padW, lineY) {
            model.configure(autoPlay && !reduced, padW.toDouble(), lineY.toDouble(), SIGN_LEFT, SIGN_RIGHT, LINE_LIFT)
            poke()
        }
        // The model's clock, read through tick so that whatever reads it is drawn again each frame.
        fun now() = tick.let { model.clock }
        fun seal() = model.seal.value(now()).toFloat()

        Header(
            "STUDIO LEASE · PAGE 4 OF 4", "Sign to accept",
            Modifier
                .offset((SIDE + 4).dp, (top + 12).dp)
                .graphicsLayer { alpha = clamp01(1 - seal() * 2.2f) },
        )
        Header(
            "STUDIO LEASE · COMPLETE", "Lease sealed",
            Modifier
                .offset((SIDE + 4).dp, (top + 12).dp)
                .graphicsLayer { alpha = clamp01((seal() - 0.55f) / 0.45f) },
        )

        for ((x, y, turn) in BACK_PAGES) {
            Box(
                Modifier
                    .offset(SIDE.dp, padTop.dp)
                    .size(padW.dp, padH.dp)
                    .graphicsLayer {
                        val e = seal()
                        val s = 1 - (1 - CARD_SCALE) * e
                        alpha = clamp01(e * 3) * model.fade.value(now()).toFloat()
                        scaleX = s
                        scaleY = s
                        rotationZ = turn * e
                        translationX = x * e * s * density.density
                        translationY = (shiftY * e + y * e * s) * density.density
                        shape = RoundedCornerShape(PAD_RADIUS.dp)
                        clip = true
                    }
                    .background(Color(0xFFF2EEE6)),
            ) {
                AsyncImage(
                    model = remember(context) { ImageRequest.Builder(context).data(PAGE_PAPER).crossfade(true).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color(40, 30, 18).copy(alpha = 0.12f)))
            }
        }

        // The page: it shrinks onto the pages behind it and rises to the middle of the screen.
        Box(
            Modifier
                .offset(SIDE.dp, padTop.dp)
                .size(padW.dp, padH.dp)
                .graphicsLayer {
                    val e = seal()
                    val s = 1 - (1 - CARD_SCALE) * e
                    alpha = model.fade.value(now()).toFloat()
                    scaleX = s
                    scaleY = s
                    translationY = shiftY * e * density.density
                    shape = RoundedCornerShape(PAD_RADIUS.dp)
                    clip = true
                }
                .background(Color(0xFFEFE7D6))
                .semantics { contentDescription = "Signature pad for $signer. Draw your signature above the line" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        model.begin(down.position.x / density.density.toDouble(), down.position.y / density.density.toDouble())
                        poke()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val v = tracker.calculateVelocity()
                            val speed = kotlin.math.hypot(v.x, v.y) / density.density
                            model.move(change.position.x / density.density.toDouble(), change.position.y / density.density.toDouble(), speed.toDouble())
                            change.consume()
                        }
                        model.end()
                        poke()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                now()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    paper?.let { bitmap -> drawCover(native, bitmap, size.width, size.height) }
                    native.save()
                    native.scale(density.density, density.density)
                    painter.paint(native, model.frame(padW, padH))
                    native.restore()
                }
            }
            Column(
                Modifier
                    .offset(26.dp, 26.dp)
                    .width(max(0f, padW - 56).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ACCEPTANCE", color = Color(0xFF8F826B), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(
                    "I have read the lease for Atelier 3B, 14 Rue Vasse, and take the studio from 1 November 2026 for twelve months.",
                    color = PRINT,
                    style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                )
            }
            Text(
                "Sign above the line",
                color = Color(0xFFA39679),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(0.dp, (lineY - 64).dp)
                    .width(padW.dp)
                    .graphicsLayer {
                        alpha = model.hint.value(now()).toFloat()
                    },
            )
            SignLine(signer, role, date, Modifier.offset(26.dp, lineY.dp).width(max(0f, padW - 52).dp))
        }

        // The wax seal and the line under the sealed card.
        WaxSeal(
            monogram,
            Modifier
                .offset(sealX.dp, sealY.dp)
                .graphicsLayer {
                    val s = model.stamp.value(now()).toFloat()
                    val scale = if (reduced) 1f else if (s <= 1) STAMP_FROM - (STAMP_FROM - 1) * s else 1 - (s - 1) * 1.6f
                    scaleX = scale
                    scaleY = scale
                    rotationZ = -16 + 10 * min(1f, s)
                    alpha = clamp01(s * 1.6f)
                },
        )
        Column(
            Modifier
                .offset(0.dp, (cardBottom + 40).dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = clamp01((seal() - 0.55f) / 0.45f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Signed by $signer · $date", color = CREAM, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Text("Tap the page to sign again", color = MUTED, fontSize = 12.sp, letterSpacing = 0.3.sp)
        }
        if (sealed) {
            Box(
                Modifier
                    .offset(((screenW - padW * CARD_SCALE) / 2).dp, (cardCentreY - padH * CARD_SCALE / 2).dp)
                    .size((padW * CARD_SCALE).dp, (padH * CARD_SCALE).dp)
                    .semantics {
                        contentDescription = "Open the page to sign again"
                        this.role = Role.Button
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            model.touched()
                            model.sealDown()
                            poke()
                        }
                    },
            )
        }

        Row(
            Modifier
                .offset(SIDE.dp, buttonsTop.dp)
                .size(padW.dp, BUTTON_HEIGHT.dp)
                .graphicsLayer { alpha = clamp01(1 - seal() * 2.2f) },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val enabled = hasInk && !sealed
            PadButton("Clear", 0, false, !hasInk, enabled, { model.pressClear.value(now()).toFloat() }, Modifier.weight(1f)) {
                model.touched()
                model.startSweep(true)
                poke()
            }
            PadButton("Replay", 1, false, !hasInk, enabled, { model.pressReplay.value(now()).toFloat() }, Modifier.weight(1f)) {
                model.touched()
                model.startReplay()
                poke()
            }
            PadButton("Done", 2, true, !hasInk, enabled, { model.pressDone.value(now()).toFloat() }, Modifier.weight(1f)) {
                model.touched()
                model.sealUp(true)
                poke()
            }
        }
    }
}

/** Draws [bitmap] over the whole of a [width] × [height] px area, cropped about its centre. */
private fun drawCover(canvas: android.graphics.Canvas, bitmap: Bitmap, width: Float, height: Float) {
    if (bitmap.width <= 0 || bitmap.height <= 0 || width <= 0f || height <= 0f) return
    val scale = max(width / bitmap.width, height / bitmap.height)
    val w = width / scale
    val h = height / scale
    val left = ((bitmap.width - w) / 2).roundToInt()
    val top = ((bitmap.height - h) / 2).roundToInt()
    val src = Rect(left, top, left + w.roundToInt(), top + h.roundToInt())
    val dst = android.graphics.RectF(0f, 0f, width, height)
    canvas.drawBitmap(bitmap, src, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
}

@Composable
private fun Header(eyebrow: String, title: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
        Text(title, color = CREAM, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 33.sp, letterSpacing = (-0.3).sp)
    }
}

@Composable
private fun SignLine(signer: String, role: String, date: String, modifier: Modifier) {
    val density = LocalDensity.current
    Box(modifier) {
        Text("×", color = RULE, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, modifier = Modifier.offset(0.dp, (-30).dp))
        Column {
            Box(Modifier.fillMaxWidth().height((2 / density.density).dp).background(RULE))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
                val caption = TextStyle(color = Color(0xFF857862), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
                Text("$signer · $role".uppercase(), style = caption)
                Spacer(Modifier.weight(1f))
                Text(date.uppercase(), style = caption)
            }
        }
    }
}

/** A pill button that sinks while held; autoPlay "presses" it through [press]. */
@Composable
private fun PadButton(
    label: String,
    icon: Int,
    primary: Boolean,
    disabled: Boolean,
    enabled: Boolean,
    press: () -> Float,
    modifier: Modifier,
    onPress: () -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val tint = if (primary) Color(0xFF1B1610) else CREAM
    val shape = RoundedCornerShape(50)
    val action by rememberUpdatedState(onPress)
    Row(
        modifier
            .height(BUTTON_HEIGHT.dp)
            .graphicsLayer {
                val k = 1 - 0.05f * max(press(), if (held) 1f else 0f)
                scaleX = k
                scaleY = k
                alpha = if (disabled) 0.38f else 1f
            }
            .clip(shape)
            .then(
                if (primary) Modifier.background(CREAM, shape)
                else Modifier
                    .background(Color(239, 230, 213).copy(alpha = 0.07f), shape)
                    .border(0.75.dp, Color(239, 230, 213).copy(alpha = 0.16f), shape),
            )
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                onClick { if (enabled) action(); enabled }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    onTap = { action() },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadIcon(icon, tint)
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
