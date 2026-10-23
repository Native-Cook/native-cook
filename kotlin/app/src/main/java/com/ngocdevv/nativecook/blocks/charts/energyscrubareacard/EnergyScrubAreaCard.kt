package com.ngocdevv.nativecook.blocks.charts.energyscrubareacard

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.*

private object S {
    val data = listOf(12.6, 11.6, 7.4, 6.2, 10.8, 11.4, 10.2, 15.6, 17.2, 14.4, 14.0, 16.0, 18.0); const val ecoScale = 0.55; const val ecoFloor = 2.0
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val page = Color(0xfff6f6f6); val card = Color(0xfffcfcfc); val ink = Color(0xff111111); val body = Color(0xff4b5563); val mute = Color(0xff6b7280); val axis = Color(0xff9ca3af); val rule = Color(0xffe5e7eb); val orange = Color(0xffee480e); val orangeSoft = Color(0xfffdede6); val pill = Color(0xfff1f3f7)
    const val side = 16f; const val pad = 24f; const val radius = 40f; const val chartTop = 12f; const val plot = 96f; const val baseGap = 10f; const val tickGap = 9f; const val tick = 5f; const val labelGap = 6f; const val line = 2.5f; const val dot = 5f; const val halo = 11f; val dash = floatArrayOf(4f, 4f)
    const val bubbleW = 88f; const val bubbleH = 34f; const val bubbleGap = 12f; const val bubbleLift = 8f; const val sideFlip = 0.22; const val toggleW = 74f; const val toggleH = 40f; const val knob = 32f; const val knobInset = (toggleH - knob) / 2; const val knobTravel = toggleW - knob - knobInset * 2; const val row = 56f
    const val knobDamping = 14.0; const val knobStiffness = 220.0; const val knobMass = 0.8; const val dotDamping = 11.0; const val dotStiffness = 320.0; const val dotMass = 4.0; const val springEnergy = 6e-9
    const val header = 81f; const val headerGap = 24f; const val dayLine = 16f; const val ecoTop = 24f; const val ecoHeight = 49f; const val actionTop = 16f; const val shadowY = 24f; const val shadowBlur = 48f
    const val base = chartTop + plot + baseGap; const val chartHeight = base + tickGap + tick; const val chartBox = chartHeight + labelGap + dayLine; const val chartY = pad + header + headerGap; const val ecoY = chartY + chartBox + ecoTop; const val actionY = ecoY + ecoHeight + actionTop; const val cardHeight = actionY + row + pad
    fun out(t: Double) = 1 - (1 - Motion.clamp(t)).pow(3)
    fun quad(t: Double): Double { val u = Motion.clamp(t); return if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2 }
}
private data class Spring(val damping: Double, val stiffness: Double, val mass: Double, val velocity: Double)
private data class Scalar(val from: Double, val to: Double = from, val at: Double = -100.0, val duration: Double = 0.0, val curve: Int = 0, val spring: Spring? = null) {
    fun sample(now: Double): Pair<Double, Double> {
        val t = max(0.0, now - at); val s = spring
        if (s != null) {
            val z = s.damping / (2 * s.mass); val w = sqrt(s.stiffness / s.mass - z * z); val a = from - to; val b = (s.velocity + z * a) / w
            val delta = exp(-z * t) * (a * cos(w * t) + b * sin(w * t)); val v = exp(-z * t) * (s.velocity * cos(w * t) + (-z * b - w * a) * sin(w * t)); val initial = s.mass * s.velocity * s.velocity + s.stiffness * a * a
            return if (initial == 0.0 || (s.mass * v * v + s.stiffness * delta * delta) / initial < S.springEnergy) Pair(to, 0.0) else (to + delta) to v
        }
        val u = if (duration == 0.0) 1.0 else Motion.clamp(t / duration); val eased = if (curve == 1) Motion.easeInOut(u) else if (curve == 2) S.quad(u) else S.out(u)
        return (from + (to - from) * eased) to 0.0
    }
    fun end(): Double {
        if (spring == null) return at + duration
        var lo = 0.0; var hi = 1.0
        while (sample(at + hi).second != 0.0) hi *= 2
        repeat(48) { val mid = (lo + hi) / 2; if (sample(at + mid).second == 0.0) hi = mid else lo = mid }
        return at + hi
    }
    fun target(value: Double, now: Double, duration: Double, curve: Int = 1) = Scalar(sample(now).first, value, now, duration, curve)
    fun targetSpring(value: Double, now: Double, damping: Double, stiffness: Double, mass: Double): Scalar { val current = sample(now); return Scalar(current.first, value, now, spring = Spring(damping, stiffness, mass, current.second)) }
}
private data class Hand(val cursor: Scalar = Scalar(Motion.dayX(0)), val held: Scalar = Scalar(0.0), val eco: Scalar = Scalar(0.0), val knob: Scalar = Scalar(0.0), val down: Double = 0.0) {
    constructor(film: Film) : this(Scalar(film.cursor), Scalar(film.held), Scalar(film.eco), Scalar(film.knob), film.down)
    fun film(now: Double) = Film(cursor.sample(now).first, held.sample(now).first, down, eco.sample(now).first, knob.sample(now).first)
}
private class Drawing { val headline = RollingNumber(); val cost = RollingNumber(); val bubble = RollingNumber(); var side = Scalar(0.0); var lastSide: Int? = null }

@Composable
fun EnergyScrubAreaCard(
    data: List<Double> = S.data, ecoData: List<Double>? = null, range: List<Double> = listOf(0.0, 20.0), current: Double = 1.2, ecoCurrent: Double = 0.7, rate: Double = 0.225,
    days: List<String> = S.days, title: String = "Energy", subtitle: String = "This week", mode: String = "Comfort", place: String = "Home", unit: String = "kW", ecoLabel: String = "Eco mode", ecoHint: String = "Maximize savings", actionLabel: String = "Spot savings",
    autoPlay: Boolean = false, onScrub: ((Double?) -> Unit)? = null, onEcoChange: ((Boolean) -> Unit)? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion(); val still by rememberUpdatedState(reduced)
    val normal by rememberUpdatedState(data); val eco by rememberUpdatedState(if (ecoData?.size == data.size) ecoData else data.map { it * S.ecoScale + S.ecoFloor })
    val scrubbedCallback by rememberUpdatedState(onScrub); val ecoCallback by rememberUpdatedState(onEcoChange); val actionCallback by rememberUpdatedState(onAction)
    val birth = remember { System.nanoTime() }; fun clock() = (System.nanoTime() - birth) / 1e9
    val time = remember { mutableDoubleStateOf(0.0) }; var hand by remember { mutableStateOf(Hand()) }; var automatic by remember { mutableStateOf(false) }; var autoOrigin by remember { mutableDoubleStateOf(0.0) }
    var autoMoving by remember { mutableStateOf(false) }; var busyUntil by remember { mutableDoubleStateOf(if (reduced) 0.0 else RollingNumber.reveal) }; var touches by remember { mutableIntStateOf(0) }; var lastTouch by remember { mutableDoubleStateOf(-100.0) }; var lastDay by remember { mutableIntStateOf(-1) }; var ecoOn by remember { mutableStateOf(false) }
    val drawing = remember { Drawing() }; val view = LocalView.current; val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    fun film(now: Double) = if (automatic) Motion.filmAt(max(0.0, now - autoOrigin) % LOOP, still) else hand.film(now)
    fun active(now: Double, duration: Double) { time.doubleValue = now; busyUntil = if (still) now + duration else maxOf(now + duration, hand.held.end(), hand.knob.end(), hand.eco.end() + RollingNumber.reveal, hand.cursor.end() + RollingNumber.reveal) }
    fun touch(now: Double) { lastTouch = now; touches++ }
    fun snapshot(now: Double) { if (automatic) { hand = Hand(film(now)); automatic = false; autoMoving = false } }
    fun takeover() { val now = clock(); val wasAuto = automatic; snapshot(now); if (wasAuto) hand = hand.copy(down = 0.0, held = hand.held.target(0.0, now, if (still) 0.0 else Motion.dotOut, 2)); touch(now); active(now, if (still) 0.0 else Motion.dotOut) }
    fun scrub(x: Double) {
        val now = clock(); val begins = automatic || hand.down == 0.0; snapshot(now); hand = hand.copy(cursor = Scalar(Motion.clamp(x)))
        val day = floor(Motion.clamp(x) * 7).toInt()
        if (begins) { hand = hand.copy(down = 1.0, held = if (still) Scalar(1.0) else hand.held.targetSpring(1.0, now, S.dotDamping, S.dotStiffness, S.dotMass)); touch(now); lastDay = day }
        else if (day != lastDay) { lastDay = day; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        active(now, if (still) 0.0 else RollingNumber.reveal); scrubbedCallback?.invoke(Motion.valueAt(Motion.mix(normal, eco, hand.eco.sample(now).first), hand.cursor.sample(now).first))
    }
    fun lift() { val now = clock(); hand = hand.copy(down = 0.0, held = hand.held.target(0.0, now, if (still) 0.0 else Motion.dotOut, 2)); touch(now); active(now, if (still) 0.0 else Motion.dotOut); scrubbedCallback?.invoke(null) }
    fun toggle() { takeover(); val now = clock(); val next = hand.knob.sample(now).first < 0.5; ecoOn = next; val goal = if (next) 1.0 else 0.0; hand = hand.copy(eco = hand.eco.target(goal, now, if (still) 0.0 else Motion.morphFor), knob = if (still) Scalar(goal) else hand.knob.targetSpring(goal, now, S.knobDamping, S.knobStiffness, S.knobMass)); view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); active(now, if (still) 0.0 else 1.3); ecoCallback?.invoke(next) }
    DisposableEffect(view) { val window = (view.context as? Activity)?.window; val controller = window?.let { WindowCompat.getInsetsController(it, view) }; val before = controller?.isAppearanceLightStatusBars; controller?.isAppearanceLightStatusBars = true; onDispose { if (before != null) controller.isAppearanceLightStatusBars = before } }
    LaunchedEffect(busyUntil, autoMoving) { while (autoMoving || clock() < busyUntil) { time.doubleValue = max(0.0, (withFrameNanos { it } - birth) / 1e9) } }
    LaunchedEffect(autoPlay, touches, reduced) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            until(birth + ((lastTouch + Motion.userHold) * 1e9).toLong()); if (hand.down != 0.0) return@LaunchedEffect
            val now = clock(); val duration = if (still) 0.0 else Motion.returnFor
            hand = hand.copy(held = hand.held.target(0.0, now, duration), eco = hand.eco.target(0.0, now, duration), knob = hand.knob.target(0.0, now, duration), cursor = hand.cursor.target(Motion.dayX(0), now, duration)); ecoOn = false; active(now, if (still) 0.0 else duration + RollingNumber.reveal)
            until(birth + ((lastTouch + Motion.userHold + Motion.returnFor) * 1e9).toLong())
        }
        var start = System.nanoTime(); autoOrigin = (start - birth) / 1e9; automatic = true; time.doubleValue = clock()
        val events = if (still) listOf(Motion.pressAt to false, Motion.liftAt to false, Motion.ecoOnAt to false, Motion.ecoOffAt to false) else listOf(Motion.pressAt to true, Motion.glideAt + Motion.glideFor + Motion.backFor + RollingNumber.reveal to false, Motion.liftAt to true, Motion.liftAt + Motion.dotOut to false, Motion.ecoOnAt to true, Motion.ecoOnAt + Motion.morphFor + RollingNumber.reveal to false, Motion.ecoOffAt to true, Motion.ecoOffAt + Motion.morphFor + RollingNumber.reveal to false)
        while (true) { for ((at, moving) in events) { until(start + (at * 1e9).toLong()); autoMoving = moving; time.doubleValue = clock() }; start += (LOOP * 1e9).toLong(); until(start); autoOrigin = (start - birth) / 1e9; time.doubleValue = clock() }
    }
    Column(modifier.fillMaxSize().background(S.page).padding(WindowInsets.safeDrawing.asPaddingValues()).padding(top = 12.dp)) {
        BasicText(title, Modifier.padding(horizontal = 24.dp), style = TextStyle(color = S.ink, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
        BasicText(subtitle, Modifier.padding(horizontal = 24.dp), style = TextStyle(color = S.mute, fontSize = 15.sp, lineHeight = 20.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            val width = maxWidth.value - 2 * S.side
            Box(Modifier.size(width.dp, S.cardHeight.dp)) {
                Canvas(Modifier.fillMaxSize().offset(y = S.shadowY.dp).bleed(S.shadowBlur.dp).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(sigmaToRadius(S.shadowBlur / 2).dp, BlurredEdgeTreatment.Unbounded) else Modifier.graphicsLayer { alpha = 0.35f })) { surface(S.shadowBlur.dp) { x, y, w, h -> drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(S.radius)) } }
                Box(Modifier.fillMaxSize().background(S.card, RoundedCornerShape(S.radius.dp)))
                Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, _ -> drawCard(drawing, film(time.doubleValue), w, time.doubleValue, reduced, data, eco, range, current, ecoCurrent, rate, days, mode, place, unit, ecoLabel, ecoHint, actionLabel, pressed) } }
                Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(sigmaToRadius(3f).dp, BlurredEdgeTreatment.Unbounded) else Modifier.graphicsLayer { alpha = 0.35f })) { surface(0.dp) { _, _, w, _ -> val f = film(time.doubleValue); drawCircle(Color.Black.copy(alpha = 0.14f), S.knob / 2, Offset(w - S.pad - S.toggleW + S.knobInset + S.knobTravel * f.knob.toFloat() + S.knob / 2, S.ecoY + (S.ecoHeight - S.toggleH) / 2 + S.knobInset + S.knob / 2 + 2)) } }
                Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, _ -> val f = film(time.doubleValue); val center = Offset(w - S.pad - S.toggleW + S.knobInset + S.knobTravel * f.knob.toFloat() + S.knob / 2, S.ecoY + (S.ecoHeight - S.toggleH) / 2 + S.knobInset + S.knob / 2); drawCircle(Color.White, S.knob / 2, center); house(center, Motion.clamp(f.knob).toFloat()) } }
                Box(Modifier.offset(S.pad.dp, S.chartY.dp).size((width - S.pad * 2).dp, S.chartBox.dp).semantics { contentDescription = "$place energy use this week" }.pointerInput(width) {
                    awaitEachGesture { val down = awaitFirstDown(requireUnconsumed = false); scrub((down.position.x / density / (width - S.pad * 2)).toDouble()); do { val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break; if (!change.pressed) break; scrub((change.position.x / density / (width - S.pad * 2)).toDouble()); change.consume() } while (true); lift() }
                })
                Box(Modifier.offset((width - S.pad - S.toggleW - 8).dp, (S.ecoY + (S.ecoHeight - S.toggleH) / 2 - 8).dp).size((S.toggleW + 16).dp, (S.toggleH + 16).dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { toggle() }.semantics { contentDescription = ecoLabel; toggleableState = if (ecoOn) ToggleableState.On else ToggleableState.Off })
                Box(Modifier.offset(S.pad.dp, S.actionY.dp).size((width - S.pad * 2).dp, S.row.dp).clickable(interaction, indication = null, role = Role.Button) { takeover(); actionCallback?.invoke() }.semantics { contentDescription = actionLabel })
            }
        }
    }
}
private suspend fun until(target: Long) { while (true) { val left = target - System.nanoTime(); if (left <= 0) return; delay((left + 999_999L) / 1_000_000L) } }
private fun DrawScope.text(value: String, x: Float, top: Float, size: Float, height: Float, color: Color, weight: Int = 400, tracking: Float = 0f): Float {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color.toArgb(); typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    val baseline = top + (height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f
    drawIntoCanvas { canvas -> if (tracking == 0f) canvas.nativeCanvas.drawText(value, x, baseline, paint) else { var at = x; value.forEach { char -> val glyph = char.toString(); canvas.nativeCanvas.drawText(glyph, at, baseline, paint); at += paint.measureText(glyph) + tracking } } }
    return paint.measureText(value) + value.length * tracking
}
private fun measure(value: String, size: Float, weight: Int = 400, tracking: Float = 0f): Float = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }.measureText(value) + value.length * tracking
private fun DrawScope.bolt(center: Offset, size: Float, color: Color) { val p = Path().apply { moveTo(center.x + size * 0.05f, center.y - size / 2); lineTo(center.x - size * 0.35f, center.y + size * 0.08f); lineTo(center.x - size * 0.03f, center.y + size * 0.08f); lineTo(center.x - size * 0.12f, center.y + size / 2); lineTo(center.x + size * 0.35f, center.y - size * 0.1f); lineTo(center.x + size * 0.03f, center.y - size * 0.1f); close() }; drawPath(p, color) }
private fun DrawScope.house(center: Offset, on: Float) { val p = Path().apply { moveTo(center.x - 8, center.y - 1); lineTo(center.x, center.y - 8); lineTo(center.x + 8, center.y - 1); lineTo(center.x + 6, center.y - 1); lineTo(center.x + 6, center.y + 7); lineTo(center.x - 6, center.y + 7); lineTo(center.x - 6, center.y - 1); close() }; drawPath(p, S.ink.copy(alpha = 1 - on), style = Stroke(1.5f, join = StrokeJoin.Round)); drawPath(p, S.orange.copy(alpha = on)); bolt(center + Offset(0f, 1f), 8f, Color((17 + 238 * on) / 255, (17 + 238 * on) / 255, (17 + 238 * on) / 255)) }
private fun DrawScope.drawCard(d: Drawing, f: Film, width: Float, now: Double, still: Boolean, data: List<Double>, eco: List<Double>, range: List<Double>, current: Double, ecoCurrent: Double, rate: Double, days: List<String>, mode: String, place: String, unit: String, ecoLabel: String, ecoHint: String, actionLabel: String, pressed: Boolean) {
    val w = width - 2 * S.pad; val values = Motion.mix(data, eco, f.eco); val frame = Frame(w, S.chartTop, S.chartTop + S.plot, range[0], range[1]); val scrubbed = Motion.valueAt(values, f.cursor); val headline = if (f.down != 0.0) scrubbed else current + (ecoCurrent - current) * f.eco; val shown = Motion.clamp(f.held).toFloat()
    drawCircle(S.orangeSoft, 16f, Offset(S.pad + 16, S.pad + 16)); bolt(Offset(S.pad + 16, S.pad + 16), 17f, S.orange); text(mode, S.pad + 42, S.pad + 5, 17f, 22f, S.body, 500); text(place, S.pad, S.pad + 40, 34f, 41f, S.ink, 600, -0.6f)
    val unitW = measure(unit, 17f); val numberW = d.headline.update(headline, 1, 2, 34f, 41f, 600, S.ink, -0.6f, still, now); d.headline.draw(this, width - S.pad - unitW - 3 - numberW, S.pad - 6, 1, now); text(unit, width - S.pad - unitW, S.pad - 6 + 41 - 5 - 22, 17f, 22f, S.mute)
    val costW = d.cost.update(headline * rate, 2, 2, 13f, 18f, 500, S.body, still = still, now = now); val prefix = measure("~$", 13f, 500); val suffix = measure(" / hr", 13f, 500); val pillW = 16 + prefix + suffix + costW; val pillX = width - S.pad - pillW; val pillY = S.pad - 6 + 41 + 2
    drawRoundRect(S.pill, Offset(pillX, pillY), Size(pillW, 24f), androidx.compose.ui.geometry.CornerRadius(12f)); text("~$", pillX + 8, pillY + 3, 13f, 18f, S.body, 500); d.cost.draw(this, pillX + 8 + prefix, pillY + 3, 2, now); text(" / hr", pillX + 8 + prefix + costW, pillY + 3, 13f, 18f, S.body, 500)
    val x = (f.cursor * w).toFloat(); val y = Motion.yOf(scrubbed, frame)
    translate(S.pad, S.chartY) { clipRect(0f, 0f, w, S.chartHeight) {
        val fill = Brush.linearGradient(0f to rgba(240, 96, 40, 0.2f), 0.55f to rgba(240, 96, 40, 0.07f), 1f to rgba(240, 96, 40, 0f), start = Offset(0f, S.chartTop), end = Offset(0f, S.base))
        drawPath(Motion.curvePath(values, frame, S.base), fill); drawLine(S.rule, Offset(0f, S.base), Offset(w, S.base), 1f)
        days.forEachIndexed { i, _ -> val at = (Motion.dayX(i) * w).toFloat(); drawLine(S.axis, Offset(at, S.base + S.tickGap), Offset(at, S.base + S.tickGap + S.tick), 1f) }
        drawLine(S.axis.copy(alpha = shown), Offset(x, S.chartTop), Offset(x, S.base - 2), 1f, pathEffect = PathEffect.dashPathEffect(S.dash))
        drawPath(Motion.curvePath(values, frame), S.orange, style = Stroke(S.line, cap = StrokeCap.Round, join = StrokeJoin.Round)); drawCircle(S.orange.copy(alpha = 0.16f), S.halo * max(0.0, f.held).toFloat(), Offset(x, y)); drawCircle(S.orange, S.dot * max(0.0, f.held).toFloat(), Offset(x, y))
    } }
    days.forEachIndexed { i, day -> text(day, S.pad + (Motion.dayX(i) * w).toFloat() - measure(day, 13f, 500, 0.3f) / 2, S.chartY + S.chartHeight + S.labelGap, 13f, 16f, S.mute, 500, 0.3f) }
    val side = if (x + S.bubbleGap + S.bubbleW > w) 1 else 0; if (d.lastSide == null) d.side = Scalar(side.toDouble()) else if (side != d.lastSide) d.side = d.side.target(side.toDouble(), now, if (still) 0.0 else S.sideFlip); d.lastSide = side
    val left = x + S.bubbleGap + (-S.bubbleW - S.bubbleGap * 2) * d.side.sample(now).first.toFloat(); var top = y
    for (i in 0..4) top = min(top, Motion.yOf(Motion.valueAt(values, ((left + S.bubbleW * i / 4) / w).toDouble()), frame))
    val bx = S.pad + left; val by = S.chartY + top - S.bubbleLift - S.bubbleH; val bubbleW = d.bubble.update(scrubbed, 1, 2, 17f, 22f, 600, S.ink, still = still, now = now); val bu = " $unit"; val buW = measure(bu, 15f)
    scale(0.92f + 0.08f * shown, 0.92f + 0.08f * shown, Offset(bx + S.bubbleW / 2, by + S.bubbleH / 2)) {
        drawRoundRect(Color.White.copy(alpha = shown), Offset(bx, by), Size(S.bubbleW, S.bubbleH), androidx.compose.ui.geometry.CornerRadius(12f)); drawRoundRect(S.rule.copy(alpha = shown), Offset(bx + 0.5f, by + 0.5f), Size(S.bubbleW - 1, S.bubbleH - 1), androidx.compose.ui.geometry.CornerRadius(11.5f), style = Stroke(1f))
        val tx = bx + (S.bubbleW - bubbleW - buW) / 2
        if (shown > 0) { drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(bx, by, bx + S.bubbleW, by + S.bubbleH), androidx.compose.ui.graphics.Paint().apply { alpha = shown }) }; d.bubble.draw(this, tx, by + 6, 1, now); text(bu, tx + bubbleW, by + 7, 15f, 20f, S.mute); drawIntoCanvas { it.restore() } }
    }
    text(ecoLabel, S.pad, S.ecoY, 20f, 25f, S.ink, 600); text(ecoHint, S.pad, S.ecoY + 29, 15f, 20f, S.mute)
    val k = Motion.clamp(f.knob).toFloat(); val track = Color((237 + (238 - 237) * k) / 255, (238 + (72 - 238) * k) / 255, (242 + (14 - 242) * k) / 255)
    drawRoundRect(track, Offset(width - S.pad - S.toggleW, S.ecoY + (S.ecoHeight - S.toggleH) / 2), Size(S.toggleW, S.toggleH), androidx.compose.ui.geometry.CornerRadius(S.toggleH / 2))
    val alpha = if (pressed) 0.6f else 1f; val action = Offset(S.pad, S.actionY); drawRoundRect(Color.White.copy(alpha = alpha), action, Size(w, S.row), androidx.compose.ui.geometry.CornerRadius(S.row / 2)); drawRoundRect(S.rule.copy(alpha = alpha), action + Offset(0.5f, 0.5f), Size(w - 1, S.row - 1), androidx.compose.ui.geometry.CornerRadius(S.row / 2), style = Stroke(1f)); drawCircle(S.rule.copy(alpha = alpha), 19.5f, Offset(S.pad + 27, S.actionY + 28), style = Stroke(1f))
    drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(S.pad, S.actionY, width - S.pad, S.actionY + S.row), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }) }; drawSpotIcon(S.pad + 16, S.actionY + 17); text(actionLabel, S.pad + 59, S.actionY + 16.5f, 18f, 23f, S.ink); val chevron = Path().apply { moveTo(width - S.pad - 31, S.actionY + 23); lineTo(width - S.pad - 26, S.actionY + 28); lineTo(width - S.pad - 31, S.actionY + 33) }; drawPath(chevron, S.body, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)); drawIntoCanvas { it.restore() }
}
