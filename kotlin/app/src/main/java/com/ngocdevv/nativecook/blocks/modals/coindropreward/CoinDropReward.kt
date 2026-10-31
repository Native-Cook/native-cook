package com.ngocdevv.nativecook.blocks.modals.coindropreward

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.*

private val cdrOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val cdrInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val cdrPour = CubicBezierEasing(0.55f, 0f, 0.75f, 0.2f)
private val cdrDefault = androidx.compose.animation.core.Easing { u -> if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f).pow(2) / 2f }

/** Each manual property has its own track, so a pan cancels only the properties it owns. */
private class CoinDropRewardTrack(
    val from: Double, val to: Double, val start: Double, val duration: Double,
    val curve: (Double) -> Double,
) {
    fun at(t: Double): Double {
        val u = CoinDropRewardMotion.seg(t, start, start + duration)
        return from + (to - from) * curve(u)
    }
}

private class CoinDropRewardState(val done: () -> Unit) {
    private val m = CoinDropRewardMotion
    var frame by mutableStateOf(CoinDropRewardMotion.Frame())
    var selected by mutableIntStateOf(0)
    var rewardOut by mutableStateOf(false)
    var left0 by mutableDoubleStateOf(0.0)
    var left1 by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(false)
    var stage = 0 // rest, opening, waiting, pulled, pouring
    var clock = 0.0
    var reduced = false
    var auto = false
    var automated = false
    private var autoStart = 0.0
    private var lastT = 0.0
    private var lastTouch = -100.0
    private var waitStart = 0.0
    private var openEnd = Double.POSITIVE_INFINITY
    private var pourEnd = Double.POSITIVE_INFINITY
    private val end = doubleArrayOf(0.0, 0.0)
    private val tracks = mutableMapOf<String, CoinDropRewardTrack>()
    private var dragInitial = 0.0

    private fun value(key: String): Double = when (key) {
        "launch" -> frame.launch; "flip" -> frame.flip; "pull" -> frame.pull
        "melt" -> frame.melt; "fly" -> frame.fly; "trail" -> frame.trail
        "toast" -> frame.toast; "text" -> frame.text; "wobble" -> frame.wobbleU
        "plus" -> frame.plus; "finger" -> frame.finger; "press" -> frame.press
        else -> 0.0
    }
    private fun set(key: String, v: Double) {
        tracks[key] = CoinDropRewardTrack(v, v, clock, 0.001) { it }
    }
    private fun animate(key: String, v: Double, duration: Double, delay: Double = 0.0,
                        easing: androidx.compose.animation.core.Easing = cdrOut) {
        tracks[key] = CoinDropRewardTrack(value(key), v, clock + delay, duration) { easing.transform(it.toFloat()).toDouble() }
    }
    private fun springTo(key: String, v: Double, duration: Double, damping: Double) {
        val initial = value(key)
        val zeta = damping
        val settling = duration * 1.5
        // Reanimated 4's duration spring: mass 4, critical-envelope stiffness solve,
        // then the requested underdamped response. Normalized displacement is -1, velocity 0.
        fun energyDifference(stiffness: Double): Double {
            val omega = sqrt(stiffness / 4) * zeta
            val envelope = exp(-omega * settling)
            val displacement = (-1 - omega * settling) * envelope
            val velocity = (-1 - omega * settling) * envelope * -omega - omega * envelope
            return (0.5 * stiffness * displacement * displacement + 2 * velocity * velocity) / (0.5 * stiffness) - 6e-9
        }
        var low = 2.220446049250313e-16
        var high = 8000.0
        var stiffness = (low + high) / 2
        var iterations = 100
        while (abs(energyDifference(stiffness)) > 6e-12 && iterations-- > 0) {
            if (energyDifference(stiffness) > 0) low = stiffness else high = stiffness
            stiffness = (low + high) / 2
        }
        val omega0 = sqrt(stiffness / 4)
        val omega1 = omega0 * sqrt(1 - zeta * zeta)
        tracks[key] = CoinDropRewardTrack(initial, v, clock, settling) { u ->
            val t = u * settling
            val sine = sin(omega1 * t)
            val cosine = cos(omega1 * t)
            val envelope = exp(-zeta * omega0 * t)
            val displacement = envelope * (-zeta * omega0 / omega1 * sine - cosine)
            val velocity = -zeta * omega0 * displacement + envelope * (-zeta * omega0 * cosine + omega1 * sine)
            val energyFraction = displacement * displacement + 4 * velocity * velocity / stiffness
            if (energyFraction <= 6e-9 || u >= 1) 1.0 else 1 + displacement
        }
    }

    private fun values() {
        fun v(k: String) = tracks[k]?.at(clock) ?: value(k)
        frame = frame.copy(launch = v("launch"), flip = v("flip"), pull = v("pull"), melt = v("melt"),
            fly = v("fly"), trail = v("trail"), toast = v("toast"), text = v("text"),
            wobbleU = v("wobble"), plus = v("plus"), finger = v("finger"), press = v("press"))
    }
    fun configure(play: Boolean, reduce: Boolean) {
        if (automated && (!play || reduce)) takeOver()
        reduced = reduce; auto = play && !reduce; automated = auto; autoStart = clock
        if (automated) { stage = 0; tracks.clear(); end.fill(0.0) }
        if (reduce) {
            set("finger", 0.0)
            if (rewardOut && stage != 4) {
                set("launch", 1.0); set("flip", 1.0)
                set("pull", 0.0); set("melt", 0.0)
                stage = 2; waitStart = clock
            }
            values()
        }
        awake = auto || stage != 0 || left0 > 0 || left1 > 0
    }
    fun tick(dt: Double) {
        clock += dt.coerceAtMost(0.05)
        if (automated) {
            lastT = ((clock - autoStart) % m.LOOP + m.LOOP) % m.LOOP
            frame = m.frameAt(lastT); selected = 0
            rewardOut = lastT >= m.LAUNCH && lastT < m.ABSORB
            left0 = frame.cooldown; left1 = 0.0
            return
        }
        values()
        if (stage == 1 && clock >= openEnd) { stage = 2; waitStart = clock }
        if (stage == 4 && clock >= pourEnd) absorb()
        left0 = max(0.0, end[0] - clock); left1 = max(0.0, end[1] - clock)
        if (stage == 2 && clock - waitStart > 3) collect()
        val busy = stage != 0 || left0 > 0 || left1 > 0 || frame.wobbleU < 1 || frame.plus < 1
        if (busy) lastTouch = clock
        if (!busy && clock - lastTouch > 2) {
            if (auto) { automated = true; autoStart = clock; tracks.clear() } else awake = false
        }
    }
    fun takeOver() {
        awake = true; lastTouch = clock
        if (!automated) return
        automated = false; tracks.clear()
        val f = m.frameAt(lastT)
        frame = f
        if (f.cooldown > 0) end[0] = clock + f.cooldown
        animate("finger", 0.0, 0.15, easing = cdrDefault)
        animate("press", 0.0, 0.15, easing = cdrDefault)
        if (lastT >= m.LAUNCH && lastT < m.ABSORB) {
            selected = 0; rewardOut = true
            animate("launch", 1.0, 0.2, easing = cdrDefault)
            animate("flip", 1.0, 0.25, easing = cdrDefault)
            if (lastT >= m.RELEASE) { stage = 3; collect() } else settle()
        } else { stage = 0; rewardOut = false }
    }
    fun canOpen(i: Int) = stage == 0 && (if (i == 0) left0 else left1) <= 0
    fun open(i: Int) {
        if (!canOpen(i)) return
        selected = i; stage = 1; rewardOut = true
        listOf("pull", "melt", "fly", "trail", "flip", "launch").forEach { set(it, 0.0) }
        values()
        if (reduced) {
            set("launch", 1.0); set("flip", 1.0)
            animate("toast", 1.0, 0.2, easing = cdrDefault); animate("text", 1.0, 0.2, easing = cdrDefault)
            stage = 2; waitStart = clock; values(); return
        }
        springTo("launch", 1.0, 0.6, 0.62)
        animate("toast", 1.0, 0.35)
        animate("text", 1.0, 0.3, 0.3)
        animate("flip", 1.0, 0.8, 0.65, cdrInOut)
        openEnd = clock + 1.45
    }
    fun collect() {
        if (!rewardOut || stage == 4) return
        stage = 4
        val rest = max(0.25, 1 - frame.fly)
        animate("melt", 1.0, 0.3)
        animate("trail", 1.0, m.FLY_S * 0.9 * rest)
        animate("toast", 0.0, 0.35); animate("text", 0.0, 0.25)
        val duration = (if (reduced) 0.3 else m.FLY_S) * rest
        animate("fly", 1.0, duration, easing = cdrPour)
        pourEnd = clock + duration; awake = true
    }
    private fun absorb() {
        stage = 0; rewardOut = false; end[selected] = clock + m.COOLDOWN
        set("wobble", 0.0); set("plus", 0.0)
        listOf("launch", "flip", "pull", "melt", "fly", "trail").forEach { set(it, 0.0) }
        values()
        if (reduced) set("wobble", 1.0) else animate("wobble", 1.0, 0.7, easing = androidx.compose.animation.core.LinearEasing)
        animate("plus", 1.0, 0.9, easing = androidx.compose.animation.core.LinearEasing)
        done()
    }
    private fun settle() {
        stage = 2; waitStart = clock
        springTo("pull", 0.0, 0.5, 0.6); springTo("melt", 0.0, 0.5, 0.8)
    }
    fun startDrag(): Boolean {
        if (stage != 1 && stage != 2) return false
        stage = 3; animate("flip", 1.0, 0.2, easing = cdrDefault)
        dragInitial = frame.pull; set("pull", frame.pull)
        return true
    }
    fun drag(projected: Double) {
        if (stage != 3) return
        lastTouch = clock
        var v = dragInitial + projected
        if (v < 0) v = -0.04 * (1 - exp(v / 0.04))
        if (v > 0.45) v = 0.45 + 0.1 * (1 - exp(-(v - 0.45) / 0.1))
        set("pull", v); set("melt", m.meltOf(v)); values()
    }
    fun release(speed: Double) {
        if (stage != 3) return
        lastTouch = clock
        if (frame.pull > 0.16 || speed > 0.9) collect() else settle()
    }
}

/** Daily shopping missions; the same blue coin can be pulled into a liquid reward. */
@Composable
fun CoinDropReward(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberLiveReducedMotion()
    val font = remember(context) { CoinDropRewardTheme.fonts(context) }
    val s = remember { CoinDropRewardState { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY) } }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoPlay, reduced) { s.configure(autoPlay, reduced) }
    LaunchedEffect(active, s.awake) {
        if (active && s.awake) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now; s.tick(dt)
            }
        }
    }
    BlockSystemBars(darkIcons = true)
    BoxWithConstraints(Modifier.fillMaxSize().background(CoinDropRewardTheme.page)
        .pointerInput(s) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                s.takeOver()
                do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
            }
        }) {
        val l = CoinDropRewardTheme.Layout(maxWidth.value, maxHeight.value,
            WindowInsets.safeDrawing.getTop(density) / density.density,
            WindowInsets.safeDrawing.getBottom(density) / density.density)
        val c = CoinDropRewardTheme
        Column(Modifier.offset(l.side.dp, l.titleTop.dp).width((l.w - 40).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CdrText("Today's visit missions", font, 22, 30, FontWeight.SemiBold, c.ink, Modifier.semantics { heading() })
            CdrText("Browse a product each day to earn points.", font, 14, 20, FontWeight.Normal, c.mute, maxLines = 2)
        }
        Row(Modifier.offset(l.walletLeft.dp, l.walletTop.dp).size(124.dp, 40.dp)
            .graphicsLayer { val w = CoinDropRewardMotion.wobbleAt(s.frame.wobbleU).toFloat(); scaleX = 1 + 0.1f * w; scaleY = 1 - 0.08f * w }
            .shadow(7.dp, CircleShape, ambientColor = Color(0x1A28285A), spotColor = Color(0x1A28285A))
            .background(c.tile, CircleShape).padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Points wallet" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CdrWallet(Modifier.size(20.dp))
            CdrText("Points", font, 14, 18, FontWeight.SemiBold, c.ink)
        }
        CdrText("+5 P", font, 14, 18, FontWeight.SemiBold, c.blue,
            Modifier.offset((l.walletLeft - 44).dp, (l.walletTop + 10).dp).graphicsLayer {
                alpha = sin(PI * s.frame.plus).toFloat().coerceIn(0f, 1f)
                translationX = (-10 * s.frame.plus).toFloat() * density.density
            }.semantics { liveRegion = LiveRegionMode.Polite })
        CdrText("2 missions", font, 14, 18, FontWeight.Medium, c.accent, Modifier.offset(20.dp, l.sectionTop.dp))
        for (i in 0..1) CdrMission(i, l, s, font) {
            s.takeOver()
            if (s.canOpen(i)) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); s.open(i) }
        }
        CdrText("Bonus mission", font, 14, 18, FontWeight.Medium, c.accent, Modifier.offset(20.dp, l.bonusLabelTop.dp))
        Row(Modifier.offset(20.dp, l.bonusTop.dp).size((l.w - 40).dp, 72.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Mystery product, Opens at 1 PM"; disabled() },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(c.stamp, CircleShape), contentAlignment = Alignment.Center) {
                CdrText("···", font, 16, 22, FontWeight.SemiBold, c.mute, letterSpacing = 1f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CdrText("Opens at 1 PM", font, 13, 18, FontWeight.Medium, c.blue)
                CdrText("Mystery product", font, 16, 22, FontWeight.SemiBold, c.ink)
            }
        }
        CdrWeek(l, font)
        Box(Modifier.offset((l.tileCx - 118).dp, (l.tileCy - 118).dp).size(236.dp)
            .graphicsLayer { alpha = s.frame.toast.toFloat(); scaleX = (0.86 + 0.14 * s.frame.toast).toFloat(); scaleY = scaleX }
            .shadow(25.dp, RoundedCornerShape(36.dp), ambientColor = Color(0x29323C8C), spotColor = Color(0x29323C8C))
            .background(c.tile, RoundedCornerShape(36.dp)).padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
            CdrText("You got 5 points", font, 16, 22, FontWeight.SemiBold, c.blue,
                Modifier.graphicsLayer { alpha = s.frame.text.toFloat(); translationY = (6 * (1 - s.frame.text)).toFloat() * density.density })
        }
        if (s.rewardOut) Box(Modifier.offset((l.tileCx - 118).dp, (l.tileCy - 118).dp).size(236.dp)
            .semantics { role = Role.Button; contentDescription = "Collect 5 points"; onClick("Collect 5 points") { s.takeOver(); s.collect(); true } }
            .pointerInput(s, l) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    s.takeOver()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragged = false
                    var accepted = false
                    var cancelled = false
                    var last = down.position
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) { cancelled = true; break }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        last = change.position
                        val delta = (last - down.position) / density.density
                        if (!dragged && delta.getDistance() >= 4f) { dragged = true; accepted = s.startDrag() }
                        if (dragged && accepted) {
                            change.consume()
                            s.drag(((delta.x * l.direction.x + delta.y * l.direction.y) / l.span).toDouble())
                        }
                    } while (change.pressed)
                    if (dragged && accepted) {
                        val velocity = tracker.calculateVelocity()
                        val speed = (velocity.x * l.direction.x + velocity.y * l.direction.y) / density.density / l.span
                        s.release(speed.toDouble())
                    } else if (!cancelled && (s.stage == 1 || s.stage == 2)) s.collect()
                }
            })
        if (active) CdrRewardCanvas(l, s)
        Box(Modifier.size(44.dp).graphicsLayer {
            val f = s.frame
            val p = if (f.onCoin == 1.0) l.toastCoin + l.vector * f.fingerPull.toFloat() else Offset(l.w * 0.62f, l.rowCoin[0].y)
            translationX = (p.x - 22) * density.density; translationY = (p.y - 22) * density.density
            alpha = (0.85 * f.finger).toFloat(); scaleX = (1 - 0.12 * f.press).toFloat(); scaleY = scaleX
        }.background(Color(0x2E1E2850), CircleShape).border(1.5.dp, Color(0x591E2850), CircleShape))
    }
}

@Composable
private fun CdrText(text: String, font: FontFamily, size: Int, line: Int, weight: FontWeight, color: Color,
                    modifier: Modifier = Modifier, maxLines: Int = 1, letterSpacing: Float = 0f) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = line.sp, fontFamily = font,
        fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        letterSpacing = letterSpacing.sp,
        style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun CdrMission(i: Int, l: CoinDropRewardTheme.Layout, s: CoinDropRewardState, font: FontFamily, press: () -> Unit) {
    val c = CoinDropRewardTheme
    val secs by remember(i) { derivedStateOf { ceil(if (i == 0) s.left0 else s.left1).toInt() } }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(Modifier.offset(20.dp, (l.rowsTop + i * 72).dp).size((l.w - 40).dp, 72.dp)
        .clip(RoundedCornerShape(16.dp)).background(if (pressed) Color(0xFFF2F1FA) else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, onClick = press)
        .semantics(mergeDescendants = true) {
            role = Role.Button; contentDescription = "${c.missions[i]}, Visit the shop · 5 P"
            if (secs > 0) { disabled(); stateDescription = "Again in $secs seconds" }
            else stateDescription = "Collects the reward"
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CdrCoin(Modifier.size(44.dp).graphicsLayer {
            val left = if (i == 0) s.left0 else s.left1
            alpha = if (s.rewardOut && s.selected == i) 0f else ((0.35 + 0.65 * (1 - CoinDropRewardMotion.smooth(0.0, 0.25, left))) *
                (1 - CoinDropRewardMotion.smooth(3.7, 4.0, left))).toFloat()
        })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CdrText(c.missions[i], font, 16, 22, FontWeight.SemiBold, c.ink)
            CdrText("Visit the shop · 5 P", font, 13, 18, FontWeight.Normal, c.mute)
        }
        if (secs > 0) Box(Modifier.height(28.dp).background(c.blueSoft, CircleShape).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text("Again in 0:0$secs", color = c.blue, fontFamily = font, fontWeight = FontWeight.Medium,
                fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum", platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
        } else Canvas(Modifier.size(8.dp, 13.dp)) {
            val path = Path().apply { moveTo(1.5f * density, 1.5f * density); lineTo(6.5f * density, 6.5f * density); lineTo(1.5f * density, 11.5f * density) }
            drawPath(path, Color(0xFFB5B6C2), style = Stroke(1.8f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun CdrWeek(l: CoinDropRewardTheme.Layout, font: FontFamily) {
    val c = CoinDropRewardTheme
    Row(Modifier.offset(20.dp, l.daysTop.dp).width((l.w - 40).dp)
        .drawWithContent {
            drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1f)
            drawContent()
        }.padding(top = 12.dp).semantics(mergeDescendants = true) { contentDescription = "This week: 4 days collected" },
        horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CdrText(c.days[i], font, 12, 15, FontWeight.Medium, if (i == 5) c.accent else c.mute)
            Box(Modifier.size(30.dp).background(if (i == 5) c.page else c.stamp, CircleShape)
                .then(if (i == 5) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                if (i in c.stamped) CdrCoin(Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun CdrCoin(modifier: Modifier) {
    Canvas(modifier) {
        val k = size.width / 44f
        drawCircle(Brush.linearGradient(listOf(Color(0xFF6FA4FF), Color(0xFF1E55F0)), Offset(size.width * 0.2f, 0f), Offset(size.width * 0.8f, size.height)), 21 * k)
        drawCircle(Color.White.copy(alpha = 0.35f), 16 * k, style = Stroke(1.5f * k))
        val p = Path().apply { moveTo(17.5f * k, 31 * k); lineTo(17.5f * k, 13 * k); lineTo(23.5f * k, 13 * k)
            val arc = 5.5f * 0.55228475f
            cubicTo((23.5f + arc) * k, 13 * k, 29 * k, (18.5f - arc) * k, 29 * k, 18.5f * k)
            cubicTo(29 * k, (18.5f + arc) * k, (23.5f + arc) * k, 24 * k, 23.5f * k, 24 * k)
            lineTo(17.5f * k, 24 * k) }
        drawPath(p, Color.White, style = Stroke(3.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun CdrWallet(modifier: Modifier) {
    Canvas(modifier) {
        val k = size.width / 20
        val p = Path().apply {
            moveTo(3 * k, 6.5f * k); cubicTo(3 * k, 5.119f * k, 4.119f * k, 4 * k, 5.5f * k, 4 * k)
            lineTo(14.5f * k, 4 * k); cubicTo(15.328f * k, 4 * k, 16 * k, 4.672f * k, 16 * k, 5.5f * k)
            lineTo(16 * k, 7 * k); lineTo(16.5f * k, 7 * k)
            cubicTo(17.328f * k, 7 * k, 18 * k, 7.672f * k, 18 * k, 8.5f * k)
            lineTo(18 * k, 14.5f * k); cubicTo(18 * k, 15.605f * k, 17.105f * k, 16.5f * k, 16 * k, 16.5f * k)
            lineTo(5.5f * k, 16.5f * k); cubicTo(4.119f * k, 16.5f * k, 3 * k, 15.381f * k, 3 * k, 14 * k); close()
        }
        drawPath(p, CoinDropRewardTheme.blue); drawCircle(Color.White, 1.4f * k, Offset(14.5f * k, 11.5f * k))
    }
}

@RequiresApi(33)
private class CoinDropRewardShaderPaint {
    private val shader = RuntimeShader(COIN_DROP_REWARD_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@CoinDropRewardShaderPaint.shader }
    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, density: Float,
             obj: FloatArray, shape: FloatArray, live: FloatArray) {
        shader.setFloatUniform("uObj", obj); shader.setFloatUniform("uShape", shape)
        shader.setFloatUniform("uLive", live); shader.setFloatUniform("uDensity", density)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

@Composable
private fun CdrRewardCanvas(l: CoinDropRewardTheme.Layout, s: CoinDropRewardState) {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) CoinDropRewardShaderPaint() else null }
    Canvas(Modifier.fillMaxSize()) {
        val f = s.frame
        if (!s.rewardOut) return@Canvas
        val row = l.rowCoin[s.selected]
        val launch = f.launch.coerceIn(0.0, 1.0).toFloat()
        val base = row + (l.toastCoin - row) * launch - Offset(0f, (30 * sin(PI * launch)).toFloat())
        val pulled = base + l.vector * f.pull.toFloat()
        val p = pulled + (l.walletIcon - pulled) * f.fly.toFloat() - Offset(0f, (46 * sin(PI * f.fly)).toFloat())
        val radius = ((22 + 44 * f.launch) * (1 - 0.82 * f.fly)).toFloat()
        val opacity = (1 - CoinDropRewardMotion.smooth(0.88, 1.0, f.fly)).toFloat()
        val a = atan2(l.direction.y.toDouble(), -l.direction.x.toDouble())
        val stretch = (0.6 * (f.pull / 0.32).coerceIn(0.0, 1.0) * (1 - f.fly) + 0.7 * sin(PI * f.fly)).toFloat()
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            drawIntoCanvas { canvas -> shader.draw(canvas.nativeCanvas, size.width, size.height, density,
                floatArrayOf(p.x, p.y, radius, opacity),
                floatArrayOf(f.melt.toFloat(), (2 * PI * f.flip).toFloat(), (PI / 2 - a).toFloat(), stretch),
                floatArrayOf((f.trail * (1 - CoinDropRewardMotion.smooth(0.85, 1.0, f.fly))).toFloat(), density, 0f, 0f)) }
        } else {
            // API 26–32: the same interactive shape and trajectory, without ray-marched material.
            val center = p * density
            val r = radius * density
            if (f.melt < 0.5) {
                drawOval(CoinDropRewardTheme.blue.copy(alpha = opacity), center - Offset(r, r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
                drawCircle(Color.White.copy(alpha = opacity * 0.35f), r * 0.76f, center, style = Stroke(1.5f * density))
                val path = Path().apply {
                    moveTo(center.x - r * 0.2f, center.y + r * 0.42f); lineTo(center.x - r * 0.2f, center.y - r * 0.4f)
                    lineTo(center.x + r * 0.03f, center.y - r * 0.4f)
                    cubicTo(center.x + r * 0.48f, center.y - r * 0.4f, center.x + r * 0.48f, center.y + r * 0.06f, center.x + r * 0.03f, center.y + r * 0.06f)
                    lineTo(center.x - r * 0.2f, center.y + r * 0.06f)
                }
                drawPath(path, Color.White.copy(alpha = opacity), style = Stroke(r * 0.17f, cap = StrokeCap.Round))
            } else {
                val tailDir = -l.direction
                val tip = center + tailDir * r * (0.62f + 0.55f * stretch)
                val normal = Offset(-tailDir.y, tailDir.x)
                val path = Path().apply {
                    moveTo((center + normal * r * 0.6f).x, (center + normal * r * 0.6f).y)
                    quadraticTo(tip.x, tip.y, tip.x, tip.y)
                    lineTo((center - normal * r * 0.6f).x, (center - normal * r * 0.6f).y); close()
                }
                drawPath(path, CoinDropRewardTheme.blue.copy(alpha = opacity)); drawCircle(CoinDropRewardTheme.blue.copy(alpha = opacity), r * 0.66f, center)
                val trailing = (f.trail * (1 - CoinDropRewardMotion.smooth(0.85, 1.0, f.fly))).toFloat()
                if (trailing > 0) drawCircle(CoinDropRewardTheme.blue.copy(alpha = opacity), r * 0.22f * (1 - 0.4f * trailing), center + tailDir * r * (0.9f + 1.1f * trailing))
            }
        }
    }
}
