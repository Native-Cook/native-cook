package com.ngocdevv.nativecook.blocks.lists.planetexplorercarousel

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LOOP_MS = 10_200f
private const val START_INDEX = 1f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val PlanetRegular = FontFamily(Font(R.font.planetexplorercarousel_poppins_regular, FontWeight.Normal))
private val PlanetSemiBold = FontFamily(Font(R.font.planetexplorercarousel_poppins_semibold, FontWeight.SemiBold))

private data class PlanetItem(
    val name: String,
    val order: String,
    val short: String,
    val long: String,
    val accent: String,
    val image: Int,
    val glow: Color,
)

private val Planets = listOf(
    PlanetItem("Mars", "4", "The fourth planet from the Sun, a dusty, cold, desert world..", "The fourth planet from the Sun is a dusty, cold desert world with a very thin atmosphere and the tallest volcano we know.", "the Sun", R.drawable.planetexplorercarousel_mars, Color(0xFFFF8A4C)),
    PlanetItem("Earth", "3", "The third planet from the Sun and the only astronomical..", "The third planet from the Sun and the only astronomical object known to harbor and support life. 70.8% covered with water.", "the Sun", R.drawable.planetexplorercarousel_earth, Color(0xFF5FC6FF)),
    PlanetItem("Venus", "2", "The second planet from the Sun and our closest planetary neighbour..", "The second planet from the Sun and our closest planetary neighbour, wrapped in thick clouds that trap its heat.", "the Sun", R.drawable.planetexplorercarousel_venus, Color(0xFFCFEAFF)),
)

@Composable
fun PlanetExplorerCarousel(autoPlay: Boolean = false, freezeAt: Float? = null) {
    val reducedMotion = rememberReducedMotion()
    val motion = remember { PlanetExplorerCarouselMotion() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var clockStopJob by remember { mutableStateOf<Job?>(null) }
    var clockActive by remember { mutableStateOf(false) }

    fun runClockFor(milliseconds: Long, keepForDetail: Boolean = false) {
        clockStopJob?.cancel()
        if (freezeAt != null) { clockActive = false; return }
        clockActive = true
        if (autoPlay && !reducedMotion) return
        clockStopJob = scope.launch {
            delay(milliseconds)
            clockActive = freezeAt == null && !reducedMotion && keepForDetail && motion.detailVisible()
        }
    }
    fun interrupt() {
        resumeJob?.cancel()
        motion.interrupt(reducedMotion)
        if (!reducedMotion) {
            clockStopJob?.cancel()
            clockActive = true
        }
    }
    fun scheduleResume() {
        if (!autoPlay || freezeAt != null) return
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(3_500)
            motion.returnToRest(reducedMotion)
            runClockFor(if (reducedMotion) 210 else 650)
        }
    }
    fun finishGesture() {
        if (!reducedMotion) runClockFor(700)
        scheduleResume()
    }

    LaunchedEffect(autoPlay, reducedMotion, freezeAt) {
        motion.synchronize(autoPlay, reducedMotion, freezeAt)
        clockStopJob?.cancel()
        clockActive = freezeAt == null && ((autoPlay && !reducedMotion) || (!reducedMotion && motion.detailVisible()))
    }
    LaunchedEffect(clockActive, autoPlay, reducedMotion, freezeAt) {
        if (!clockActive || freezeAt != null) return@LaunchedEffect
        while (isActive && clockActive) {
            withFrameNanos { nanos ->
                motion.advance(nanos / 1_000_000.0, autoPlay, reducedMotion)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val s = maxWidth / 402f
        val density = LocalDensity.current
        val spacingPx = with(density) { (234 * s).toPx() }
        val frame = motion.frame

        PlanetList(
            frame = frame,
            scale = s,
            reducedMotion = reducedMotion,
            interactive = freezeAt == null,
            spacingPx = spacingPx,
            onInterrupt = ::interrupt,
            onResume = ::finishGesture,
            onHaptic = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onOpen = {
                interrupt()
                motion.openDetail(reducedMotion)
                if (reducedMotion) runClockFor(210) else {
                    clockStopJob?.cancel()
                    clockActive = true
                }
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scheduleResume()
            },
        )
        PlanetDetail(
            frame = frame,
            scale = s,
            reducedMotion = reducedMotion,
            interactive = freezeAt == null,
            onBack = {
                interrupt()
                motion.closeDetail(reducedMotion)
                runClockFor(if (reducedMotion) 210 else 650)
                scheduleResume()
            },
        )
    }
}

@Composable
private fun PlanetList(
    frame: PlanetFrame,
    scale: Dp,
    reducedMotion: Boolean,
    interactive: Boolean,
    spacingPx: Float,
    onInterrupt: () -> Unit,
    onResume: () -> Unit,
    onHaptic: () -> Unit,
    onOpen: () -> Unit,
) {
    val s = scale
    val listProgress = window(frame.detail, 0f, .78f)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = 1 - listProgress
                translationY = if (reducedMotion) 0f else 12 * listProgress * s.toPx()
            },
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.12f to Color(0xFF1F6DB3),
                    0.35f to Color(0xFF185CA3),
                    0.8f to Color(0xFF272D79),
                    1f to Color(0xFF2D1E6B),
                ),
            ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val sc = size.width / 402f
            val center = androidx.compose.ui.geometry.Offset(201 * sc, 560 * sc)
            rotate(if (reducedMotion) 0f else (frame.orbit - 1) * -9, center) {
                drawCircle(Color.White.copy(alpha = .22f), 300 * sc, center, style = Stroke(sc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * sc, 6 * sc))))
            }
            val starShift = if (reducedMotion) 0f else -(frame.position - 1) * 22
            Stars.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = .75f), 1.4f * sc, androidx.compose.ui.geometry.Offset((x + starShift) * sc, y * sc)) }
            val dotShift = if (reducedMotion) 0f else -(frame.position - 1) * 40
            Dots.forEach { dot -> drawCircle(dot.color, dot.radius * sc, androidx.compose.ui.geometry.Offset((dot.x + dotShift) * sc, dot.y * sc)) }
        }

        Column(Modifier.offset(22 * s, 42 * s), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6 * s)) {
            Box(Modifier.size(30 * s, 2.6f * s).clip(CircleShape).background(Color.White))
            Box(Modifier.size(22 * s, 2.6f * s).clip(CircleShape).background(Color.White))
            Box(Modifier.size(30 * s, 2.6f * s).clip(CircleShape).background(Color.White))
        }
        Image(painterResource(R.drawable.planetexplorercarousel_avatar_1), "Your profile", Modifier.offset(336 * s, 34 * s).size(40 * s).clip(CircleShape), contentScale = ContentScale.Crop)
        Text("Space", Modifier.offset(97 * s, 99 * s), fontFamily = PlanetSemiBold, fontSize = (60 * s.value).sp, lineHeight = (64 * s.value).sp, letterSpacing = (-.5 * s.value).sp, color = Color.White)
        Text("Explorer", Modifier.offset(103 * s, 153 * s).graphicsLayer { scaleX = 1.067f }, fontFamily = PlanetSemiBold, fontSize = (50 * s.value).sp, lineHeight = (64 * s.value).sp, letterSpacing = (-.5 * s.value).sp, color = Color.White)

        val carouselInput = if (interactive && frame.detail < .5f) Modifier.pointerInput(spacingPx, reducedMotion) {
                var origin = 1f
                var total = 0f
                var tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { offset ->
                        onInterrupt()
                        origin = frame.position
                        total = 0f
                        tracker = VelocityTracker()
                        tracker.addPosition(0, offset)
                    },
                    onDragCancel = onResume,
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x / spacingPx
                        val target = (motionPosition(frame) - velocity * .2f).roundToInt().coerceIn(0, 2).toFloat()
                        if (target != origin.roundToInt().toFloat()) onHaptic()
                        frame.controller.snap(target, -velocity, reducedMotion)
                        onResume()
                    },
                ) { change, drag ->
                    change.consume()
                    total += drag.x
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val raw = origin - total / spacingPx
                    frame.controller.drag(if (raw < 0) raw * .3f else if (raw > 2) 2 + (raw - 2) * .3f else raw, reducedMotion)
                }
            } else Modifier
        Box(
            Modifier.offset(y = 288 * s).fillMaxWidth().height(396 * s).then(carouselInput),
        ) {
            Planets.forEachIndexed { index, planet -> PlanetCard(index, planet, frame, s) }
        }

        ExploreButton(frame, s, interactive && frame.detail < .5f, onOpen)

        Row(Modifier.offset(y = 770.25f * s).fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            FriendResources.forEachIndexed { index, resource ->
                Image(painterResource(resource), null, Modifier.offset(x = (-11 * index) * s).size(37.5f * s).clip(CircleShape).zIndex((4 - index).toFloat()), contentScale = ContentScale.Crop)
            }
        }
        Text("Joey, Linn and 8 friends are online", Modifier.offset(y = 815.5f * s).fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = PlanetRegular, fontSize = (12 * s.value).sp, color = Color(0xFFA9B0E0))
    }
}

@Composable
private fun PlanetCard(index: Int, planet: PlanetItem, frame: PlanetFrame, s: Dp) {
    val p = frame.position
    val orbit = frame.orbit
    val active = amount(index, p)
    val orbitAmount = amount(index, orbit)
    val travel = ((p - orbit) * 8).coerceIn(-1f, 1f)
    val relative = index - p
    val ahead = if (relative == 0f) 0f else (if (relative > 0) 1 else -1) * travel
    val bell = 4 * active * (1 - active)
    val outgoing = max(0f, -ahead) * bell
    val incoming = max(0f, ahead) * bell
    val cardScale = 1 - .12f * outgoing + .03f * incoming
    val cover = 1 - ((outgoing - .15f) / .25f).coerceIn(0f, 1f)
    val side = if (index - orbit > 0) -1 else 1
    val lag = (p - orbit) * 234
    val k = if (ahead > 0) -.8f * ahead else 1 + .6f * max(0f, -ahead)
    val planetSize = 120 + 80 * orbitAmount
    val planetX = 117 + side * 46 * (1 - orbitAmount) + lag * k
    val planetY = lerp(434f, 388f, orbitAmount) - 395
    val swell = 1 + min(.16f, abs(p - orbit) * 3 * .16f)
    val bgTop = lerp(25f, 0f, active)
    val bgHeight = lerp(235f, 289f, active)
    val hidden = frame.detail > 0 && frame.selected == index

    Box(
        Modifier.offset(x = (201 + (index - p) * 234 - 117) * s, y = 107 * s)
            .size(234 * s, 289 * s).scale(cardScale)
            .zIndex((active * 10 + ahead * 6).roundToInt().toFloat()),
    ) {
        Box(Modifier.offset(y = bgTop * s).size(234 * s, bgHeight * s).clip(RoundedCornerShape(30 * s)).background(lerp(Color(0xFFB9B8F4), Color.White, active)))
        Box(Modifier.offset(y = bgTop * s).size(234 * s, bgHeight * s).clip(RoundedCornerShape(30 * s)).background(Color(0xFF272D79).copy(alpha = .28f * outgoing)))
        Text(planet.order, Modifier.offset(x = 26 * s, y = 87 * s).width(190 * s).graphicsLayer { alpha = active * cover }, textAlign = TextAlign.End, fontFamily = PlanetSemiBold, fontSize = (150 * s.value).sp, lineHeight = (165 * s.value).sp, color = Color(0xFFE0E0E7))
        Text(planet.name, Modifier.offset(x = 26 * s, y = (100 + 20 * (1 - active)) * s).width(186 * s).graphicsLayer { alpha = cover }, fontFamily = PlanetSemiBold, fontSize = (34 * s.value).sp, lineHeight = (44.2f * s.value).sp, color = lerp(Color(0xFF5D66B8), Color(0xFF11406E), active), maxLines = 1)
        Text(planet.short, Modifier.offset(x = 25 * s, y = (152 + 20 * (1 - active)) * s).width(187 * s).graphicsLayer { alpha = cover }, fontFamily = PlanetRegular, fontSize = (15 * s.value).sp, lineHeight = (24 * s.value).sp, color = lerp(Color(0xFF8D90CF), Color(0xFF747684), active), maxLines = 3)
        Box(Modifier.offset(x = (planetX - 125 * planetSize / 200) * s, y = (planetY - 125 * planetSize / 200) * s).size(250 * planetSize / 200 * s).scale(swell).clip(CircleShape).background(Brush.radialGradient(listOf(planet.glow.copy(alpha = .25f), planet.glow.copy(alpha = 0f)))).graphicsLayer { alpha = if (hidden) 0f else 1f })
        Image(painterResource(planet.image), null, Modifier.offset(x = (planetX - planetSize / 2) * s, y = (planetY - planetSize / 2) * s).size(planetSize * s).rotate(24 * (index - orbit)).graphicsLayer { alpha = if (hidden) 0f else 1f })
    }
}

@Composable
private fun ExploreButton(frame: PlanetFrame, s: Dp, enabled: Boolean, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) .97f else 1f, tween(if (pressed) 120 else 150, easing = EaseOut), label = "explore press")
    Box(
        Modifier.offset(x = (201 - 43) * s, y = (689 - 43) * s).size(86 * s).scale(press * frame.arrowScale)
            .clip(CircleShape).background(Color.White)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(72 * s).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFFC21A), Color(0xFFF7A600)))), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24 * s)) {
                val k = size.width / 24
                val path = Path().apply { moveTo(4 * k, 12 * k); lineTo(19 * k, 12 * k); moveTo(13 * k, 5.5f * k); lineTo(19.5f * k, 12 * k); lineTo(13 * k, 18.5f * k) }
                drawPath(path, Color.White, style = Stroke(2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun PlanetDetail(frame: PlanetFrame, scale: Dp, reducedMotion: Boolean, interactive: Boolean, onBack: () -> Unit) {
    val s = scale
    val planet = Planets[frame.selected]
    val backdrop = window(frame.detail, .08f, .82f)
    val back = stagger(frame.detail, .3f, .6f)
    val numeral = stagger(frame.detail, .36f, .7f)
    val name = stagger(frame.detail, .44f, .78f)
    val desc = stagger(frame.detail, .52f, .86f)
    val video = stagger(frame.detail, .6f, .94f)
    val rocket = stagger(frame.detail, .55f, 1f)
    val hero = if (reducedMotion) 1f else EaseInOut.transform(window(frame.detail, 0f, .62f))
    val heroOpacity = if (reducedMotion) window(frame.detail, .3f, .7f) else if (frame.detail > 0) 1f else 0f
    val heroSize = lerp(200f, 366f, hero)
    val heroFloat = if (reducedMotion) 0f else sin(frame.clockMs / 4_000f * Math.PI.toFloat() * 2) * 3 * window(frame.detail, .9f, 1f)
    val rocketBob = if (reducedMotion) 0f else sin(frame.clockMs / 4_000f * Math.PI.toFloat() * 2 + 1) * 5 * window(frame.detail, .95f, 1f)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = backdrop }.background(Brush.horizontalGradient(0f to Color(0xFFA29FB3), .1f to Color(0xFFC1BECE), .2f to Color(0xFFE3E2E8), .3f to Color.White, 1f to Color.White)))
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = backdrop }) {
            val sc = size.width / 402
            drawCircle(Color(0x2411406E), 250 * sc, androidx.compose.ui.geometry.Offset(267 * sc, 327 * sc), style = Stroke(sc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * sc, 6 * sc))))
        }
        Box(Modifier.offset(y = 745 * s).width(145 * s).fillMaxHeight().staggerLayer(video, reducedMotion, s).background(Color(0xFF00A86B)))
        Text(planet.order, Modifier.offset(36 * s, 222 * s).staggerLayer(numeral, reducedMotion, s), fontFamily = PlanetSemiBold, fontSize = (210 * s.value).sp, lineHeight = (231 * s.value).sp, color = Color(0xFFE0E0E7))
        Canvas(Modifier.fillMaxSize().staggerLayer(desc, reducedMotion, s)) {
            val sc = size.width / 402
            drawCircle(Color(0xFF2FB66A), 9 * sc, androidx.compose.ui.geometry.Offset(40 * sc, 145 * sc)); drawCircle(Color(0xFFFFC21A), 13 * sc, androidx.compose.ui.geometry.Offset(330 * sc, 436 * sc)); drawCircle(Color(0xFF2A6FE0), 9 * sc, androidx.compose.ui.geometry.Offset(220 * sc, 589 * sc))
        }
        Text(planet.name, Modifier.offset(40 * s, 414 * s).staggerLayer(name, reducedMotion, s), fontFamily = PlanetSemiBold, fontSize = (48 * s.value).sp, lineHeight = (62.4f * s.value).sp, color = Color(0xFF11406E))
        val description = buildAnnotatedString {
            val parts = planet.long.split(planet.accent, limit = 2); append(parts[0]); withStyle(SpanStyle(fontFamily = PlanetSemiBold, color = Color(0xFF22A866))) { append(planet.accent) }; if (parts.size > 1) append(parts[1])
        }
        Text(description, Modifier.offset(40 * s, 479 * s).width(326 * s).staggerLayer(desc, reducedMotion, s), fontFamily = PlanetRegular, fontSize = (14.5f * s.value).sp, lineHeight = (26 * s.value).sp, color = Color(0xFF747684))
        Box(Modifier.offset(35 * s, 631 * s).size(332 * s, 204 * s).clip(RoundedCornerShape(30 * s)).staggerLayer(video, reducedMotion, s), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.planetexplorercarousel_earth_video), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.size(66 * s).clip(CircleShape).background(Color(0x8C282828)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20 * s)) { val k = size.width / 20; val path = Path().apply { moveTo(6.5f * k, 4.5f * k); lineTo(15.5f * k, 10 * k); lineTo(6.5f * k, 15.5f * k); close() }; drawPath(path, Color.White) }
            }
        }
        Box(Modifier.offset(x = (lerp(201f, 257f, hero) - heroSize / 2) * s, y = (lerp(388f, 147f, hero) + heroFloat - heroSize / 2) * s).size(heroSize * s).graphicsLayer { alpha = heroOpacity }) {
            Box(Modifier.offset(x = (-25 * heroSize / 200) * s, y = (-25 * heroSize / 200) * s).size(250 * heroSize / 200 * s).clip(CircleShape).background(Brush.radialGradient(listOf(planet.glow.copy(alpha = .25f), planet.glow.copy(alpha = 0f)))))
            Image(painterResource(planet.image), null, Modifier.fillMaxSize())
        }
        Image(painterResource(R.drawable.planetexplorercarousel_rocket), null, Modifier.offset(x = (174 - 70 * (1 - rocket)) * s, y = (252 + 90 * (1 - rocket) + rocketBob) * s).size(138 * s).rotate(-18 * (1 - rocket)).graphicsLayer { alpha = rocket })
        BackButton(back, frame.backScale, interactive && frame.detail > .5f, reducedMotion, s, onBack)
    }
}

@Composable
private fun BackButton(visible: Float, scriptedScale: Float, enabled: Boolean, reduced: Boolean, s: Dp, onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) .97f else 1f, tween(if (pressed) 120 else 150, easing = EaseOut), label = "back press")
    Box(Modifier.offset(26 * s, 46 * s).size(44 * s).staggerLayer(visible, reduced, s).scale(press * scriptedScale).clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26 * s)) {
            val k = size.width / 26; val path = Path().apply { moveTo(22 * k, 13 * k); lineTo(4.5f * k, 13 * k); moveTo(11 * k, 6 * k); lineTo(4 * k, 13 * k); lineTo(11 * k, 20 * k) }
            drawPath(path, Color(0xFF11406E), style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun Modifier.staggerLayer(progress: Float, reduced: Boolean, s: Dp) = graphicsLayer {
    alpha = progress
    translationY = if (reduced) 0f else 12 * (1 - progress) * s.toPx()
}

private class PlanetFrame(val controller: PlanetExplorerCarouselMotion) {
    var position by mutableFloatStateOf(START_INDEX)
    var orbit by mutableFloatStateOf(START_INDEX)
    var detail by mutableFloatStateOf(0f)
    var selected by mutableIntStateOf(1)
    var arrowScale by mutableFloatStateOf(1f)
    var backScale by mutableFloatStateOf(1f)
    var clockMs by mutableFloatStateOf(0f)
}

private class PlanetExplorerCarouselMotion {
    val frame = PlanetFrame(this)
    private var mode = 0 // 0 initial, 1 autoplay, 2 manual, 3 returning, 4 frozen
    private var now = 0.0
    private var loopStart = 0.0
    private var returnStart = 0.0
    private val position = Driven(START_INDEX)
    private val detail = Driven(0f)
    private var orbit = START_INDEX
    private var orbitVelocity = 0f
    private var orbitTime = 0.0
    private var pausedForReducedMotion = false

    fun synchronize(autoPlay: Boolean, reduced: Boolean, freezeAt: Float?) {
        now = monotonicMilliseconds()
        if (freezeAt != null) {
            mode = 4
            val clock = positiveMod(freezeAt, LOOP_MS)
            val frozen = sample(clock)
            frame.position = frozen.position
            frame.orbit = frozenOrbit(freezeAt, reduced)
            frame.detail = frozen.detail
            frame.selected = 1
            frame.arrowScale = if (frozen.arrow) .97f else 1f
            frame.backScale = if (frozen.back) .97f else 1f
            frame.clockMs = clock
            return
        }
        if (mode == 4) mode = 0
        advance(now, autoPlay, reduced)
    }

    fun advance(nowMs: Double, autoPlay: Boolean, reduced: Boolean) {
        now = nowMs
        if (mode == 0) { mode = if (autoPlay && !reduced) 1 else 2; loopStart = now; orbitTime = now }
        if (reduced && mode == 1) {
            mode = 2; pausedForReducedMotion = true
            position.set(START_INDEX, now); detail.set(0f, now); orbit = START_INDEX; orbitVelocity = 0f
        } else if (!reduced && autoPlay && pausedForReducedMotion) {
            pausedForReducedMotion = false; mode = 1; loopStart = now; orbit = START_INDEX; orbitVelocity = 0f; orbitTime = now
        }
        when (mode) {
            1 -> {
                val clock = positiveMod((now - loopStart).toFloat(), LOOP_MS); val sample = sample(clock)
                frame.position = sample.position; frame.detail = sample.detail; frame.selected = 1; frame.arrowScale = if (sample.arrow) .97f else 1f; frame.backScale = if (sample.back) .97f else 1f; frame.clockMs = clock
                orbit = autoplayOrbit(clock); frame.orbit = orbit
            }
            3 -> {
                val p = position.value(now); val d = detail.value(now); stepOrbit(p, now, reduced); publish(p, d)
                if (now - returnStart >= 500.0) { mode = if (autoPlay && !reduced) 1 else 2; loopStart = now; orbit = START_INDEX; orbitVelocity = 0f; orbitTime = now }
            }
            else -> { val p = position.value(now); val d = detail.value(now); stepOrbit(p, now, reduced); publish(p, d) }
        }
    }
    fun interrupt(reduced: Boolean) { now = monotonicMilliseconds(); mode = 2; position.set(frame.position, now); detail.set(frame.detail, now); orbit = frame.orbit; orbitTime = now; if (reduced) orbitVelocity = 0f }
    fun drag(value: Float, reduced: Boolean) { now = monotonicMilliseconds(); position.set(value, now); frame.position = value; if (reduced) { orbit = value; frame.orbit = value; orbitVelocity = 0f; orbitTime = now } }
    fun snap(target: Float, velocity: Float, reduced: Boolean) { now = monotonicMilliseconds(); if (reduced) { position.set(target, now); orbit = target; frame.position = target; frame.orbit = target } else position.springTo(target, 400.0, .8f, velocity, now) }
    fun openDetail(reduced: Boolean) { now = monotonicMilliseconds(); frame.selected = frame.position.roundToInt().coerceIn(0, 2); if (reduced) position.set(frame.selected.toFloat(), now) else position.springTo(frame.selected.toFloat(), 400.0, .8f, 0f, now); detail.timeTo(1f, if (reduced) 200.0 else 900.0 * (1 - frame.detail), Driven.Curve.Quad, now) }
    fun closeDetail(reduced: Boolean) { now = monotonicMilliseconds(); detail.timeTo(0f, if (reduced) 200.0 else 600.0 * frame.detail, Driven.Curve.Quad, now) }
    fun returnToRest(reduced: Boolean) { now = monotonicMilliseconds(); mode = 3; returnStart = now; position.timeTo(START_INDEX, if (reduced) 0.0 else 500.0, Driven.Curve.EaseOut, now); detail.timeTo(0f, if (reduced) 200.0 else 600.0 * frame.detail, Driven.Curve.Quad, now) }
    fun detailVisible(): Boolean { now = monotonicMilliseconds(); val value = detail.value(now); frame.detail = value; return value > .001f }
    private fun publish(p: Float, d: Float) { frame.position = p; frame.orbit = orbit; frame.detail = d; frame.arrowScale = 1f; frame.backScale = 1f; frame.clockMs = (now % 4_000.0).toFloat() }
    private fun stepOrbit(target: Float, time: Double, reduced: Boolean) {
        if (reduced) { orbit = target; orbitVelocity = 0f; orbitTime = time; return }
        if (orbitTime <= 0 || time < orbitTime || time - orbitTime > 1_000.0) { orbit = target; orbitVelocity = 0f; orbitTime = time; return }
        while (orbitTime < time) { val dtMs = min(8.0, time - orbitTime); val dt = (dtMs / 1_000).toFloat(); val acceleration = 220 * (target - orbit) - 22 * orbitVelocity; orbitVelocity += acceleration * dt; orbit += orbitVelocity * dt; orbitTime += dtMs }
    }
    private data class Sample(val position: Float, val detail: Float, val arrow: Boolean, val back: Boolean)
    private fun sample(t: Float): Sample {
        val p = 1 + EaseInOut.transform(window(t, 1_200f, 1_800f)) - EaseInOut.transform(window(t, 2_600f, 3_200f))
        val d = if (t >= 3_720 && t < 8_420) window(t, 3_720f, 4_620f) else if (t >= 8_420) 1 - window(t, 8_420f, 9_020f) else 0f
        return Sample(p, d, t >= 3_600 && t < 3_740, t >= 8_300 && t < 8_440)
    }
    private fun autoplayOrbit(time: Float): Float { var x = 1f; var v = 0f; var elapsed = 0f; while (elapsed < time) { val dtMs = min(8f, time - elapsed); val dt = dtMs / 1_000; val target = sample(elapsed + dtMs).position; val a = 220 * (target - x) - 22 * v; v += a * dt; x += v * dt; elapsed += dtMs }; return x }
    private fun frozenOrbit(milliseconds: Float, reduced: Boolean): Float {
        var x = START_INDEX; var velocity = 0f; var last = -1f; var sampleTime = 0f
        val outerStep = 1_000f / 120f
        while (sampleTime <= milliseconds) {
            val target = sample(sampleTime).position
            if (reduced) { x = target; velocity = 0f; last = sampleTime }
            else if (last < 0 || sampleTime < last || sampleTime - last > 1_000f) { x = target; velocity = 0f; last = sampleTime }
            else while (last < sampleTime) { val stepMs = min(8f, sampleTime - last); val dt = stepMs / 1_000f; val acceleration = 220 * (target - x) - 22 * velocity; velocity += acceleration * dt; x += velocity * dt; last += stepMs }
            sampleTime += outerStep
        }
        return x
    }
}

private class Driven(initial: Float) {
    enum class Curve { Quad, EaseOut }
    private var mode = 0; private var stored = initial; private var from = initial; private var target = initial; private var start = 0.0; private var duration = 0.0; private var curve = Curve.Quad
    private var initialVelocity = 0f; private var dampingRatio = .8f; private var stiffness = 0f
    fun set(value: Float, now: Double) { stored = value; from = value; target = value; start = now; mode = 0 }
    fun timeTo(value: Float, durationMs: Double, curve: Curve, now: Double) { val current = value(now); if (durationMs <= 0) { set(value, now); return }; from = current; stored = current; target = value; start = now; duration = durationMs; this.curve = curve; mode = 1 }
    fun springTo(value: Float, durationMs: Double, dampingRatio: Float, velocity: Float, now: Double) { val current = value(now); from = current; stored = current; target = value; start = now; duration = durationMs; initialVelocity = velocity; this.dampingRatio = dampingRatio; stiffness = stiffnessFor(current - value, velocity, durationMs, dampingRatio); mode = if (stiffness == 0f) 0 else 2 }
    fun value(now: Double): Float = when (mode) {
        0 -> stored
        1 -> { val p = ((now - start) / max(duration, .0001)).coerceIn(0.0, 1.0).toFloat(); val e = if (curve == Curve.Quad) quad(p) else EaseOut.transform(p); stored = lerp(from, target, e); if (p >= 1) mode = 0; stored }
        else -> { val t = (max(0.0, now - start) / 1_000).toFloat(); val omega0 = sqrt(stiffness); val omega1 = omega0 * sqrt(max(0f, 1 - dampingRatio * dampingRatio)); val x0 = from - target; val envelope = exp(-dampingRatio * omega0 * t); val displacement = if (omega1 > 0) envelope * (x0 * cos(omega1 * t) + (initialVelocity + dampingRatio * omega0 * x0) / omega1 * sin(omega1 * t)) else envelope * (x0 + (initialVelocity + omega0 * x0) * t); stored = target + displacement; if (now - start >= duration * 1.5) set(target, now); stored }
    }
    private fun stiffnessFor(x0: Float, v0: Float, durationMs: Double, zeta: Float): Float {
        if (x0 == 0f && v0 == 0f) return 0f; val settling = durationMs * 1.5 / 1_000.0
        fun diff(k: Double): Double { val omega = sqrt(k) * zeta; val a = x0 + (v0 + x0 * omega) * settling; val e = exp(-omega * settling); val x = a * e; val v = a * e * -omega + (v0 + x0 * omega) * e; return (0.5 * k * x * x + 0.5 * v * v) / (0.5 * k * x0 * x0 + 0.5 * v0 * v0) - 6e-9 }
        var lo = Math.ulp(1.0); var hi = 8e3; val direction = if (diff(hi) >= diff(lo)) 1 else -1; var current = (lo + hi) / 2
        repeat(100) { if (abs(diff(current)) <= 6e-12) return current.toFloat(); if (diff(current) * direction < 0) lo = current else hi = current; current = (lo + hi) / 2 }; return current.toFloat()
    }
}

private data class DecorDot(val x: Float, val y: Float, val radius: Float, val color: Color)
private val Stars = listOf(48f to 152f, 201f to 245f, 345f to 242f, 358f to 272f, 120f to 370f, 48f to 280f, 372f to 330f, 96f to 708f, 112f to 690f, 300f to 722f, 366f to 740f, 70f to 790f, 330f to 770f, 185f to 860f, 26f to 640f, 380f to 610f, 250f to 300f, 140f to 250f)
private val Dots = listOf(DecorDot(285f, 86f, 11f, Color(0xFF2FD07A)), DecorDot(307f, 123f, 9f, Color(0xFFFFB12B)), DecorDot(85f, 214f, 9f, Color(0xFF2F9BEA)), DecorDot(0f, 270f, 9f, Color(0xFFFFC21A)), DecorDot(335f, 872f, 12f, Color(0xFF2F9BEA)))
private val FriendResources = listOf(R.drawable.planetexplorercarousel_avatar_2, R.drawable.planetexplorercarousel_avatar_3, R.drawable.planetexplorercarousel_avatar_4, R.drawable.planetexplorercarousel_avatar_5)
private fun motionPosition(frame: PlanetFrame) = frame.position
private fun amount(index: Int, position: Float) = (1 - abs(index - position)).coerceIn(0f, 1f)
private fun window(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)
private fun stagger(value: Float, start: Float, end: Float) = EaseOut.transform(window(value, start, end))
private fun lerp(from: Float, to: Float, amount: Float) = from + (to - from) * amount
private fun quad(value: Float) = if (value < .5f) 2 * value * value else 1 - 2 * (1 - value) * (1 - value)
private fun positiveMod(value: Float, modulus: Float) = ((value % modulus) + modulus) % modulus
private fun monotonicMilliseconds() = System.nanoTime() / 1_000_000.0
