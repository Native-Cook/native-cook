package com.ngocdevv.nativecook.blocks.lists.pastaplatewheel

import android.app.Activity
import android.graphics.BitmapShader
import android.graphics.Canvas as NativeCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val BG = Color(0xFFECEBE7)
private val INK = Color(0xFF1B1A17)
private val PAPER = Color(0xFFFBFAF7)
private val ACCENT = Color(0xFFC8472B)

/**
 * The pasta menu of Trattoria Lumetta, an invented restaurant. A big black disc sits half off the
 * left edge with eight plates of pasta on its rim; the wheel turns and the plates ride it, each
 * food spinning on its own plate as it rolls, while the plate at the right middle lifts out of
 * the wheel (larger, deeper shadow) as the selection. The selected dish's number, name, line and
 * price roll in below like drums, a little after the wheel (LOOP = 8 s).
 *
 * Drag up or down to turn the wheel (it carries the finger's speed and snaps to a plate); tap a
 * plate to bring it to the front, tap the front plate to pop it. 2 s after the last touch the
 * wheel springs back to the first dish and the loop restarts. Reduced motion: the first dish, still.
 */
@Composable
fun PastaPlateWheel(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsOff(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { PastaPlateWheelTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                clock.doubleValue = base + (now - startNanos) / 1e9
            }
            if (!autoPlay && !touch.touching && touch.release != null && clock.doubleValue > touch.restsAfter) {
                awake.value = false
                break
            }
        }
    }
    val phiAt: (Double) -> Double = { c -> if (reduced) 0.0 else touch.phi(c, autoPlay) }

    var bagCount by remember { mutableIntStateOf(2) }
    val badge = remember { Animatable(1f) }
    LaunchedEffect(bagCount) {
        if (bagCount == 2) return@LaunchedEffect
        badge.animateTo(1.35f, spring(dampingRatio = 0.9f, stiffness = 2500f))
        badge.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 195f))
    }

    val painter = remember { PastaPlateWheelPainter() }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density / k
        val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density / k
        val cy = PastaPlateWheelMotion.centreY(safeTop.toDouble(), height, safeBottom.toDouble())
        val infoTop = PastaPlateWheelMotion.infoTop(height, safeBottom.toDouble())
        val u: (Double) -> Dp = { (it * k).dp }
        val ts: (Double) -> TextUnit = { with(density) { (it * k).dp.toSp() } }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Pasta wheel" }
                .pointerInput(reduced, k, cy) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        touch.begin(clock.doubleValue, autoPlay)
                        awake.value = true
                        var last = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            val d = change.position - down.position
                            if (touch.move(d.x / scale.toDouble(), d.y / scale.toDouble())) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        val vy = tracker.calculateVelocity().y / scale
                        val feedback = touch.end(clock.doubleValue, vy.toDouble(), last.x / scale.toDouble(), last.y / scale.toDouble(), cy)
                        when (feedback) {
                            PastaPlateWheelTouch.Feedback.None -> {}
                            PastaPlateWheelTouch.Feedback.Pop -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            PastaPlateWheelTouch.Feedback.Snap -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                },
        ) {
            val c = clock.doubleValue
            val canvas = drawContext.canvas.nativeCanvas
            painter.drawGrain(canvas, size.width, size.height, k * density.density)
            canvas.save()
            canvas.scale(size.width / 393f, size.width / 393f)
            painter.drawWheel(canvas, phiAt(c), cy, PastaPlateWheelMotion.pop(c - touch.popClock), touch.popIndex)
            canvas.restore()
        }

        // "Select Pasta" pill
        Row(
            Modifier
                .offset(u(-24.0), u(cy - 22))
                .size(u(160.0), u(44.0))
                .background(PAPER, RoundedCornerShape(50))
                .padding(start = u(42.0), end = u(16.0))
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Select Pasta", color = INK, fontSize = ts(15.0), fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(u(4.0), u(9.0))) {
                val w = size.width; val h = size.height
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(w, h / 2); lineTo(0f, h) }
                drawPath(path, INK, style = Stroke(width = 2 * k * density.density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Header
        Box(Modifier.offset(0.dp, u(safeTop + 8.0)).fillMaxWidth().height(u(44.0))) {
            Text(
                "Delicious Pasta",
                color = INK, fontSize = ts(22.0), fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
            )
            Column(
                Modifier.offset(u(24.0), 0.dp).size(u(44.0)).semantics { contentDescription = "Menu" },
                verticalArrangement = Arrangement.spacedBy(u(6.0), Alignment.CenterVertically),
            ) {
                for (w in listOf(22.0, 14.0, 18.0)) {
                    Box(Modifier.size(u(w), u(2.2)).background(INK, RoundedCornerShape(50)))
                }
            }
            Box(
                Modifier
                    .offset(u(393.0 - 20 - 42), u(1.0))
                    .size(u(42.0))
                    .shadow(u(3.0), CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                    .background(PAPER, CircleShape)
                    .semantics { contentDescription = "Order, $bagCount items" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(u(16.0), u(19.0))) {
                    val s = size.width / 16f
                    val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawRoundRect(INK, topLeft = Offset(0f, 5 * s), size = androidx.compose.ui.geometry.Size(16 * s, 14 * s),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * s), style = stroke)
                    val handle = androidx.compose.ui.graphics.Path().apply {
                        moveTo(4.5f * s, 7.5f * s)
                        lineTo(4.5f * s, 4f * s)
                        arcTo(androidx.compose.ui.geometry.Rect(4.5f * s, 0.5f * s, 11.5f * s, 7.5f * s), 180f, 180f, false)
                        lineTo(11.5f * s, 7.5f * s)
                    }
                    drawPath(handle, INK, style = stroke)
                }
            }
            Box(
                Modifier
                    .offset(u(393.0 - 20 + 4 - 18), u(-2.0))
                    .size(u(18.0))
                    .graphicsLayer { scaleX = badge.value; scaleY = badge.value }
                    .background(ACCENT, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text("$bagCount", color = Color.White, fontSize = ts(11.0), fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Trattoria Lumetta · 8 dishes",
            color = Color(0xFF8A867D), fontSize = ts(13.0), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.offset(0.dp, u(safeTop + 49.0)).fillMaxWidth(),
        )

        // Info drums
        val dishes = PastaPlateWheelArt.dishes
        val counters = remember { dishes.indices.map { "%02d / %02d".format(it + 1, dishes.size) } }
        val selected by remember { derivedStateOf { Math.floorMod(PastaPlateWheelMotion.round(phiAt(clock.doubleValue)).toInt(), 8) } }
        Box(
            Modifier.offset(u(24.0), u(infoTop)).size(u(345.0), u(90.0))
                .clearAndSetSemantics { contentDescription = "${dishes[selected].name}, ${dishes[selected].line}, ${dishes[selected].price}" },
        )
        PastaPlateWheelDrum(
            counters, { phiAt(clock.doubleValue) }, 16.0, 320.0, k,
            TextStyle(color = ACCENT, fontSize = ts(12.0), fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            Modifier.offset(u(24.0), u(infoTop)),
        )
        PastaPlateWheelDrum(
            dishes.map { it.name }, { phiAt(clock.doubleValue - 0.04) }, 36.0, 320.0, k,
            TextStyle(color = INK, fontSize = ts(28.0), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
            Modifier.offset(u(24.0), u(infoTop + 20)),
        )
        PastaPlateWheelDrum(
            dishes.map { it.line }, { phiAt(clock.doubleValue - 0.08) }, 20.0, 320.0, k,
            TextStyle(color = Color(0xFF7A766E), fontSize = ts(14.0)),
            Modifier.offset(u(24.0), u(infoTop + 60)),
        )
        PastaPlateWheelDrum(
            dishes.map { it.price }, { phiAt(clock.doubleValue - 0.12) }, 32.0, 140.0, k,
            TextStyle(color = INK, fontSize = ts(26.0), fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            Modifier.offset(u(24.0), u(infoTop + 108)),
        )
        Row(
            Modifier
                .offset(u(393.0 - 20 - 196), u(infoTop + 96))
                .size(u(196.0), u(56.0))
                .background(INK, RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Add ${dishes[selected].name} to order",
                ) {
                    bagCount += 1
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                .padding(start = u(14.0)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u(12.0)),
        ) {
            Box(Modifier.size(u(28.0)).background(ACCENT, CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(u(10.0))) {
                    val w = size.width
                    val stroke = 2 * k * density.density
                    drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, w), stroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(0f, w / 2), Offset(w, w / 2), stroke, StrokeCap.Round)
                }
            }
            Text("Add to order", color = PAPER, fontSize = ts(16.0), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A line of text on a drum: rows sit `row` apart and roll with φ, fading away from the middle. */
@Composable
private fun PastaPlateWheelDrum(
    items: List<String>,
    phi: () -> Double,
    row: Double,
    width: Double,
    k: Float,
    style: TextStyle,
    modifier: Modifier,
) {
    val base by remember { derivedStateOf { floor(phi()).toInt() } }
    val density = LocalDensity.current
    val rowPx = with(density) { (row * k).dp.toPx() }
    Box(modifier.size((width * k).dp, (row * k).dp).clipToBounds().clearAndSetSemantics {}) {
        for (o in -1..2) {
            val j = base + o
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val d = j - phi()
                        translationY = (d * rowPx).toFloat()
                        alpha = max(0.0, 1 - 2.2 * abs(d)).toFloat()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(items[Math.floorMod(j, items.size)], style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun animationsOff(context: android.content.Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Draws the disc and the plates in the 393-wide design space. */
internal class PastaPlateWheelPainter {
    private val m = PastaPlateWheelMotion
    private val art = PastaPlateWheelArt
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grainPaint = Paint().apply { alpha = (0.035 * 255).toInt() }
    private var grainScale = -1f
    private val rect = RectF()

    fun drawGrain(canvas: NativeCanvas, w: Float, h: Float, pxPerUnit: Float) {
        if (grainScale != pxPerUnit) {
            grainScale = pxPerUnit
            grainPaint.shader = BitmapShader(art.atlas.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(Matrix().apply { setScale(pxPerUnit / 2, pxPerUnit / 2) })
            }
        }
        canvas.drawRect(0f, 0f, w, h, grainPaint)
    }

    private fun radialFill(canvas: NativeCanvas, x: Double, y: Double, r: Double, colors: IntArray, stops: FloatArray) {
        shade.color = 0xFF000000.toInt()
        shade.shader = RadialGradient(x.toFloat(), y.toFloat(), r.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), shade)
        shade.shader = null
    }

    fun drawWheel(canvas: NativeCanvas, phi: Double, cy: Double, pop: Double, popIndex: Int) {
        val cx = m.CENTRE_X
        val disc = m.DISC_RADIUS
        fill.color = art.argb(0x151413)
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), disc.toFloat(), fill)
        // The sheen: a radial gradient centred off the disc's centre, clipped to the disc.
        canvas.save()
        val clip = android.graphics.Path().apply { addCircle(cx.toFloat(), cy.toFloat(), disc.toFloat(), android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        radialFill(canvas, cx + 120, cy - 40, 260.0, intArrayOf(art.argb(0x2B2723, 0.95), art.argb(0x2B2723, 0.0)), floatArrayOf(0f, 1f))
        canvas.restore()
        line.strokeWidth = 1.5f
        for ((r, a) in listOf(m.ORBIT - 72 to 0.045, m.ORBIT - 112 to 0.03)) {
            line.color = art.argb(0xFFFFFF, a)
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), line)
        }
        line.strokeWidth = 1f
        line.color = art.argb(0xFFFFFF, 0.07)
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), (disc - 0.5).toFloat(), line)
        fill.color = art.argb(0xFFFFFF, 0.18)
        for (j in 0 until m.COUNT) {
            val a = (j + 0.5 - phi) * PI / 4
            canvas.drawCircle((cx + m.ORBIT * cos(a)).toFloat(), (cy + m.ORBIT * sin(a)).toFloat(), 2.5f, fill)
        }

        val atlas = art.atlas
        for (p in m.plates(phi, cy, popIndex, pop)) {
            if (p.x + p.size / 2 < -4) continue
            val dish = art.dishes[p.index]
            val half = p.size / 2
            val f = p.front
            val blur = p.size * 0.06 + 10 * f
            val outer = half + blur
            val alpha = 0.22 + 0.2 * f
            radialFill(
                canvas, p.x + 4 + 6 * f, p.y + 8 + 12 * f, outer,
                intArrayOf(art.argb(0x2A2118, alpha), art.argb(0x2A2118, alpha), art.argb(0x2A2118, 0.0)),
                floatArrayOf(0f, max(0.0, (half - blur * 0.6) / outer).toFloat(), 1f),
            )
            val contact = half + 2.5
            val ca = 0.32 - 0.15 * f
            radialFill(
                canvas, p.x + 1, p.y + 2, contact,
                intArrayOf(art.argb(0x1A140E, ca), art.argb(0x1A140E, ca), art.argb(0x1A140E, 0.0)),
                floatArrayOf(0f, ((half - 2.5) / contact).toFloat(), 1f),
            )
            val plate = atlas.plates[if (dish.bowl) 1 else 0]
            rect.set((p.x - half).toFloat(), (p.y - half).toFloat(), (p.x + half).toFloat(), (p.y + half).toFloat())
            canvas.drawBitmap(plate, null, rect, bitmapPaint)
            val foodHalf = (p.size * art.foodFraction(dish.bowl) * 1.08 / 2).toFloat()
            canvas.save()
            canvas.translate(p.x.toFloat(), p.y.toFloat())
            canvas.rotate(Math.toDegrees(p.spin).toFloat())
            rect.set(-foodHalf, -foodHalf, foodHalf, foodHalf)
            canvas.drawBitmap(atlas.foods[p.index], null, rect, bitmapPaint)
            canvas.restore()
            rect.set(p.x.toFloat() - foodHalf, p.y.toFloat() - foodHalf, p.x.toFloat() + foodHalf, p.y.toFloat() + foodHalf)
            canvas.drawBitmap(atlas.lights[if (dish.bowl) 1 else 0], null, rect, bitmapPaint)
        }
    }
}
