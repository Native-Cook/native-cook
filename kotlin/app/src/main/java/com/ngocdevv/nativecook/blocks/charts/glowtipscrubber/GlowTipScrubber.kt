package com.ngocdevv.nativecook.blocks.charts.glowtipscrubber

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.*

private object S {
    val page = Color(0xff1b1b1d); val well = Color(0xff242426); val pills = Color(0xff2a2a2d); val pillOn = Color(0xff3a3a3d); val ink = Color.White; val soft = Color(0xffc7c7cc); val mute = Color(0xff8a8a8e); val faint = Color(0xff6c6c70); val red = Color(0xffff5a5f); val coin = Color(0xfff7931a)
    const val margin = 16f; const val bar = 44f; const val coinSize = 48f; const val gutter = 88f; const val tagH = 23f; const val tagGap = 8f; const val chartPad = 16f; const val chartMin = 220f; const val pillsH = 44f; const val pillInset = 4f; const val tickEvery = 12f; const val heldIn = 0.15; const val heldOut = 0.25; const val drawFor = 1.0; const val slotFor = 0.22
    const val nameSize = 15f; const val nameLine = 20f; const val priceSize = 34f; const val priceLine = 41f; const val priceTracking = -0.4f; const val changeSize = 15f; const val changeLine = 20f; const val dateTop = 2f; const val headerHeight = nameLine + priceLine + changeLine * 2 + dateTop
    fun quad(t: Double): Double { val u = Motion.clamp(t); return if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2 }
}
private data class Scalar(val from: Double, val to: Double = from, val at: Double = -100.0, val duration: Double = 0.0, val curve: Int = 0) {
    fun value(now: Double): Double { val u = if (duration == 0.0) 1.0 else Motion.clamp((now - at) / duration); val eased = if (curve == 1) Motion.ease(u) else if (curve == 2) S.quad(u) else if (curve == 3) u.pow(3) else u; return from + (to - from) * eased }
    fun target(value: Double, now: Double, duration: Double, curve: Int = 2) = Scalar(this.value(now), value, now, duration, curve)
    val end get() = at + duration
}
private data class Hand(val tip: Scalar = Scalar(0.0), val held: Scalar = Scalar(0.0), val lit: Scalar = Scalar(0.0), val reveal: Scalar = Scalar(1.0), val shown: Scalar = Scalar(1.0), val marks: Scalar = Scalar(1.0), val down: Double = 0.0) {
    constructor(f: Film) : this(Scalar(f.tip), Scalar(f.held), Scalar(f.lit), Scalar(f.reveal), Scalar(f.shown), Scalar(f.marks), f.down)
    fun film(now: Double) = Film(tip.value(now), held.value(now), down, lit.value(now), reveal.value(now), shown.value(now), marks.value(now))
}

@Composable
fun GlowTipScrubber(
    symbol: String = "EMB", name: String = "Ember coin", currency: String = "£", mark: String = "E",
    disclaimer: String = "Prices on this screen are made up for illustration. Crypto prices move fast and you could lose all the money you put in.",
    footnote: String = "This is the midpoint between buy and sell rates. The rate you get depends on whether you buy or sell.",
    ranges: List<Range> = Ranges, initialRange: Int = 1, autoPlay: Boolean = false, onScrub: ((Double?, Double?) -> Unit)? = null, onRangeChange: ((Range, Int) -> Unit)? = null, onClose: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion(); val still by rememberUpdatedState(reduced); val items by rememberUpdatedState(ranges); val first by rememberUpdatedState(initialRange)
    val scrubCallback by rememberUpdatedState(onScrub); val rangeCallback by rememberUpdatedState(onRangeChange); val closeCallback by rememberUpdatedState(onClose)
    val birth = remember { System.nanoTime() }; fun clock() = (System.nanoTime() - birth) / 1e9
    val time = remember { mutableDoubleStateOf(0.0) }; var hand by remember { mutableStateOf(Hand()) }; var slot by remember { mutableStateOf(Scalar(initialRange.toDouble())) }; var index by remember { mutableIntStateOf(initialRange) }
    var automatic by remember { mutableStateOf(false) }; var origin by remember { mutableDoubleStateOf(0.0) }; var autoMoving by remember { mutableStateOf(false) }; var busyUntil by remember { mutableDoubleStateOf(-100.0) }; var touches by remember { mutableIntStateOf(0) }; var lastTouch by remember { mutableDoubleStateOf(-100.0) }; var lastTick by remember { mutableFloatStateOf(0f) }
    val reels = remember { RollingPrice() }; val view = LocalView.current; val density = LocalDensity.current
    fun film(now: Double) = if (automatic) Motion.filmAt(max(0.0, now - origin) % LOOP, still) else hand.film(now)
    fun touch(now: Double) { lastTouch = now; touches++ }
    fun snapshot(now: Double) { if (automatic) { hand = Hand(film(now)); automatic = false; autoMoving = false } }
    fun active(now: Double) { time.doubleValue = now; busyUntil = maxOf(hand.tip.end, hand.held.end, hand.lit.end, hand.reveal.end, hand.marks.end, slot.end, now + if (still) 0.0 else RollingPrice.roll) }
    fun scrub(x: Float, width: Float) {
        val now = clock(); val begin = automatic || hand.down == 0.0; snapshot(now); hand = hand.copy(tip = Scalar(Motion.clamp((x / width).toDouble())))
        if (begin) { hand = hand.copy(reveal = Scalar(1.0), shown = Scalar(1.0), down = 1.0, held = hand.held.target(1.0, now, if (still) 0.0 else S.heldIn), lit = hand.lit.target(1.0, now, if (still) 0.0 else S.heldIn), marks = hand.marks.target(0.0, now, if (still) 0.0 else S.heldIn)); lastTick = x; touch(now) }
        else { if (abs(x - lastTick) >= S.tickEvery) { lastTick = x; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }; val r = items[index]; scrubCallback?.invoke(Motion.priceAt(r.prices, hand.tip.value(now)), r.start + hand.tip.value(now) * (r.end - r.start)) }
        active(now)
    }
    fun lift() { val now = clock(); hand = hand.copy(down = 0.0, held = hand.held.target(0.0, now, if (still) 0.0 else S.heldOut), lit = hand.lit.target(0.0, now, if (still) 0.0 else S.heldOut), marks = hand.marks.target(1.0, now, if (still) 0.0 else S.heldOut * 2)); scrubCallback?.invoke(null, null); touch(now); active(now) }
    fun show(next: Int, draw: Boolean, now: Double) {
        index = next; slot = slot.target(next.toDouble(), now, if (still) 0.0 else S.slotFor, 1); hand = hand.copy(shown = Scalar(1.0))
        if (still || !draw) { hand = hand.copy(reveal = Scalar(1.0)); return }
        hand = hand.copy(tip = Scalar(0.0).target(1.0, now, S.drawFor, 0), reveal = Scalar(0.0).target(1.0, now, S.drawFor, 0), lit = Scalar(1.0).target(0.0, now, S.drawFor + 0.25, 3), marks = Scalar(0.0).target(1.0, now, S.drawFor + 0.3, 3))
    }
    fun pick(next: Int) { val now = clock(); snapshot(now); hand = hand.copy(down = 0.0, held = hand.held.target(0.0, now, if (still) 0.0 else S.heldOut)); show(next, true, now); view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); rangeCallback?.invoke(items[next], next); touch(now); active(now) }
    DisposableEffect(view) { val window = (view.context as? Activity)?.window; val controller = window?.let { WindowCompat.getInsetsController(it, view) }; val before = controller?.isAppearanceLightStatusBars; controller?.isAppearanceLightStatusBars = false; onDispose { if (before != null) controller.isAppearanceLightStatusBars = before } }
    LaunchedEffect(busyUntil, autoMoving) { while (autoMoving || clock() < busyUntil) time.doubleValue = max(0.0, (withFrameNanos { it } - birth) / 1e9) }
    LaunchedEffect(autoPlay, touches, reduced) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) { until(birth + ((lastTouch + Motion.userHold) * 1e9).toLong()); if (hand.down != 0.0) return@LaunchedEffect }
        val now = clock(); if (index != first) { show(first, false, now); active(now) }; origin = now; automatic = true; time.doubleValue = now
        val events = if (still) listOf(Motion.pressAt to false, Motion.jumpTo to false) else listOf(Motion.pressAt to true, Motion.backTo + (Motion.jumpTo - Motion.backTo) / (Motion.overshoot + 1) + RollingPrice.roll to false, Motion.jumpTo to true, Motion.jumpTo + Motion.lift to false, Motion.fadeAt to true, Motion.drawAt + Motion.drawFor + Motion.marksIn to false)
        var start = birth + (now * 1e9).toLong()
        while (true) { for ((at, moving) in events) { until(start + (at * 1e9).toLong()); autoMoving = moving; time.doubleValue = clock() }; start += (LOOP * 1e9).toLong(); until(start); origin = (start - birth) / 1e9; time.doubleValue = clock() }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(modifier.fillMaxSize().background(S.page).padding(insets).padding(bottom = max(0f, 16 - insets.calculateBottomPadding().value).dp)) {
        Row(Modifier.fillMaxWidth().height(S.bar.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(46.dp).offset(x = (-10).dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { closeCallback?.invoke() }.semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(26.dp), tint = S.ink) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Box(Modifier.size(24.dp)) { Icon(Icons.Outlined.NotificationsNone, null, tint = S.ink); BasicText("+", Modifier.align(Alignment.TopEnd), style = TextStyle(color = S.ink, fontSize = 11.sp)) }; Icon(Icons.Default.Star, null, tint = S.ink) }
        }
        Label(disclaimer, 13, 18, S.mute, Modifier.padding(top = 12.dp, start = S.margin.dp, end = S.margin.dp).fillMaxWidth().background(S.well, RoundedCornerShape(12.dp)).padding(12.dp))
        Canvas(Modifier.padding(top = 16.dp, start = S.margin.dp, end = S.margin.dp).fillMaxWidth().height(S.headerHeight.dp).semantics { contentDescription = "$name price"; liveRegion = LiveRegionMode.Polite }) { surface(0.dp) { _, _, w, _ -> header(reels, ranges[index], film(time.doubleValue), w, time.doubleValue, reduced, symbol, name, currency, mark) } }
        Spacer(Modifier.height(20.dp))
        BoxWithConstraints(Modifier.weight(1f).heightIn(min = S.chartMin.dp).fillMaxWidth().padding(start = S.margin.dp).semantics { contentDescription = "$name price chart, ${ranges[index].span}" }.pointerInput(Unit) {
            awaitEachGesture { val down = awaitFirstDown(requireUnconsumed = false); val width = size.width / density.density - S.gutter; scrub(down.position.x / density.density, width); do { val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break; if (!change.pressed) break; if (change.position != change.previousPosition) scrub(change.position.x / density.density, width); change.consume() } while (true); lift() }
        }) {
            val graphW = maxWidth.value - S.gutter
            Box(Modifier.width(graphW.dp).fillMaxHeight().clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, h -> drawChart(ranges[index], w, h, S.chartPad, film(time.doubleValue), 0) } }
                for (pass in 1..4) {
                    if (pass == 1 || pass == 3) Box(Modifier.fillMaxSize().graphicsLayer { val f = film(time.doubleValue); clip = true; shape = GenericShape { size, _ -> addRect(Rect(0f, 0f, (f.reveal * size.width).toFloat(), size.height)) } }) {
                        Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(sigmaToRadius(chartBlur(pass)).dp, BlurredEdgeTreatment.Unbounded) else Modifier.graphicsLayer { alpha = 0.15f })) { surface(0.dp) { _, _, w, h -> drawChart(ranges[index], w, h, S.chartPad, film(time.doubleValue), pass) } }
                    } else Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, h -> drawChart(ranges[index], w, h, S.chartPad, film(time.doubleValue), pass) } }
                }
            }
            Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, h ->
                val f = film(time.doubleValue); val r = ranges[index]; val low = r.prices.min(); val high = r.prices.max(); val span = (high - low).let { if (it == 0.0) 1.0 else it }; val alpha = (0.4 + 0.6 * f.marks).toFloat()
                text(Motion.grouped(high), w - S.margin, S.chartPad - 8, 13f, 16f, S.mute.copy(alpha = alpha), trailing = true); text(Motion.grouped(low), w - S.margin, h - S.chartPad - 8, 13f, 16f, S.mute.copy(alpha = alpha), trailing = true)
                val rule = (S.chartPad + (1 - (r.prices[0] - low) / span) * (h - S.chartPad * 2)).toFloat(); val top = if (rule + S.tagGap + S.tagH <= h) rule + S.tagGap else rule - S.tagGap - S.tagH; val label = Motion.grouped(r.prices[0]); val tw = measure(label, 13f) + 16
                drawRoundRect(S.well.copy(alpha = f.marks.toFloat()), Offset(4f, top), Size(tw, S.tagH), CornerRadius(6f)); text(label, 12f, top + 3, 13f, 17f, S.soft.copy(alpha = f.marks.toFloat()))
            } }
        }
        Row(Modifier.padding(top = 16.dp, start = S.margin.dp, end = S.margin.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.weight(1f).height(S.pillsH.dp).background(S.pills, RoundedCornerShape(22.dp))) {
                val segment = (maxWidth.value - S.pillInset * 2) / ranges.size
                Box(Modifier.offset(S.pillInset.dp, S.pillInset.dp).size(segment.dp, (S.pillsH - S.pillInset * 2).dp).graphicsLayer { translationX = (slot.value(time.doubleValue) * segment * density.density).toFloat() }.background(S.pillOn, RoundedCornerShape(18.dp)))
                Row(Modifier.fillMaxSize().padding(horizontal = S.pillInset.dp)) { ranges.forEachIndexed { i, r -> Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { pick(i) }.semantics { selected = i == index }, contentAlignment = Alignment.Center) { Label(r.label, 15, 20, if (i == index) S.ink else S.mute, weight = FontWeight.Medium) } } }
            }
            Canvas(Modifier.size(S.pillsH.dp).background(S.pills, CircleShape)) { surface(0.dp) { _, _, _, _ -> for (i in 0..2) { val x = 15f + i * 7; drawLine(S.soft, Offset(x, 12f + i * 2), Offset(x, 31f - i * 2), 1f); drawLine(S.soft, Offset(x, 17f + i), Offset(x, 26f - i), 3f) } } }
        }
        Label(footnote, 12, 16, S.faint, Modifier.padding(top = 16.dp, start = S.margin.dp, end = S.margin.dp).fillMaxWidth())
    }
}
@Composable
private fun Label(value: String, size: Int, height: Int, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal) { BasicText(value, modifier, style = TextStyle(color = color, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, platformStyle = PlatformTextStyle(includeFontPadding = false))) }
private suspend fun until(target: Long) { while (true) { val left = target - System.nanoTime(); if (left <= 0) return; delay((left + 999_999L) / 1_000_000L) } }
private fun paint(size: Float, weight: Int, tabular: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; if (tabular) fontFeatureSettings = "tnum"; typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
private fun measure(value: String, size: Float, weight: Int = 400) = paint(size, weight).measureText(value)
private fun DrawScope.text(value: String, x: Float, top: Float, size: Float, height: Float, color: Color, weight: Int = 400, trailing: Boolean = false, tabular: Boolean = true) {
    val p = paint(size, weight, tabular).apply { this.color = color.toArgb() }; val baseline = top + (height - p.fontMetrics.ascent - p.fontMetrics.descent) / 2
    drawIntoCanvas { it.nativeCanvas.drawText(value, x - if (trailing) p.measureText(value) else 0f, baseline, p) }
}
private fun DrawScope.header(reels: RollingPrice, r: Range, f: Film, width: Float, now: Double, still: Boolean, symbol: String, name: String, currency: String, mark: String) {
    val price = if (f.down != 0.0) Motion.priceAt(r.prices, f.tip) else r.prices.last(); val change = price - r.prices[0]
    text("$symbol · $name", 0f, 0f, S.nameSize, S.nameLine, S.soft, tabular = false)
    val digits = floor(r.prices.max()).toLong().toString().length; val zeros = "0".repeat(digits); val whole = zeros.mapIndexed { i, char -> (if (i > 0 && (digits - i) % 3 == 0) "," else "") + char }.joinToString("")
    reels.draw(this, price, "$currency$whole.00", S.priceSize, S.priceLine, 600, S.priceTracking, S.ink, still, now, y = S.nameLine)
    val label = (if (change < 0) "−" else "+") + currency + Motion.grouped(change); val percent = (if (change < 0) "▼ " else "▲ ") + Motion.fixed(abs(change / r.prices[0] * 100)) + "%"
    text(label, 0f, 61f, 15f, 20f, S.mute); text(percent, measure(label, 15f) + 6, 61f, 15f, 20f, if (change < 0) S.red else Green, 500); text(if (f.down != 0.0) Motion.dateLabel(r.start + Motion.clamp(f.tip) * (r.end - r.start), r.hours) else r.span, 0f, 83f, 15f, 20f, S.mute)
    val center = Offset(width - S.coinSize / 2, 4 + S.coinSize / 2); drawCircle(S.coin, S.coinSize / 2, center)
    rotate(-12f, center) { val p = paint(26f, 800).apply { color = S.ink.toArgb() }; drawIntoCanvas { it.nativeCanvas.drawText(mark, center.x - p.measureText(mark) / 2, center.y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p) } }
}
