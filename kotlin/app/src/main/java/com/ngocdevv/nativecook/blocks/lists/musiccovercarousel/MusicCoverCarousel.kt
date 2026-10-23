package com.ngocdevv.nativecook.blocks.lists.musiccovercarousel

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.isActive

const val LOOP_MS = 7_000L

private object MusicCoverCarouselTokens {
    const val LoopSeconds = LOOP_MS / 1_000f
    const val Scale = 0.615f
    // Kept for value-for-value parity with the approved motion exports, even though the RN view does not consume them.
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    const val SettleDurationSeconds = 0.4f
    const val SettleDampingRatio = 1f
    const val PressDuration = 0.12f
    const val PressScale = 0.97f
    const val PressDim = 0.6f
    const val SheenAlpha = 0.4f
    const val SheenWidth = 0.46f
    const val SheenSlant = 18f
    const val SheenSpan = 40f
    const val PlayheadFrom = 0.1f
    const val PlayheadPerLoop = 0.6f
    const val PinCenterX = 368f
    const val PinCenterY = 282f
    const val Start = 2f
    const val CardWidth = 215f
    const val CardHeight = 269f
    const val CardCenterX = 360.5f
    const val CardCenterY = 222f
    val Slots = floatArrayOf(0f, 108f, 176f)
    val SlotsRight = floatArrayOf(0f, 113f, 184f)
    val Player = MusicCoverCarouselRect(46f, 409f, 644f, 68f)
    const val PreviousX = 92.4f
    const val PauseX = 151.2f
    const val NextX = 211.1f
    const val IconY = 443f
    const val IconSize = 22f
    val Capsule = MusicCoverCarouselRect(248f, 418f, 240f, 50f)
    const val LyricsX = 531.9f
    const val ListX = 587.2f
    const val VolumeX = 643.1f
    val Tracks = listOf(
        MusicCoverCarouselTrack("braids", "Dusk Signal", "Gianni Rowe", R.drawable.musiccovercarousel_braids, Color(0xFF1E3A3C), listOf(Color(0xFF2E5A5D), Color(0xFF1E3A3C), Color(0xFF142829))),
        MusicCoverCarouselTrack("velvet", "Velvet Hours", "Ritva Lane", R.drawable.musiccovercarousel_velvet, Color(0xFF6E6A66), listOf(Color(0xFF8E8984), Color(0xFF6E6A66), Color(0xFF55514E))),
        MusicCoverCarouselTrack("ember", "Ember Lights", "Thea Vance", R.drawable.musiccovercarousel_ember, Color(0xFFF2A63C), listOf(Color(0xFFE39631), Color(0xFFC47516), Color(0xFFAE5F08))),
        MusicCoverCarouselTrack("satin", "Opera 1 Theme", "Quinn Tyler", R.drawable.musiccovercarousel_satin, Color(0xFF8A8580), listOf(Color(0xFFA39E98), Color(0xFF8A8580), Color(0xFF6C6864))),
        MusicCoverCarouselTrack("drive", "Night Drive (Chennai)", "Arun, Isha & Dev", R.drawable.musiccovercarousel_drive, Color(0xFF4A4A50), listOf(Color(0xFF626269), Color(0xFF4A4A50), Color(0xFF36363B))),
    )
    val Steps = listOf(
        MusicCoverCarouselStep(0.9f, 3f, 0.55f, 0.8f, 2f, MusicCoverCarouselPressKind.Next),
        MusicCoverCarouselStep(2.3f, 4f, 0.62f, 0.7f, 6f),
        MusicCoverCarouselStep(3.75f, 3f, 0.62f, 0.72f, 6f),
        MusicCoverCarouselStep(5.491f, 2f, 0.55f, 0.8f, 3f, MusicCoverCarouselPressKind.Previous),
    )
    val Changes = buildList {
        var previous = round(musicCoverCarouselFlowAt(0f)).toInt()
        for (millisecond in 1 until (LoopSeconds * 1_000).toInt()) {
            val current = round(musicCoverCarouselFlowAt(millisecond / 1_000f)).toInt()
            if (current != previous) add(millisecond / 1_000f)
            previous = current
        }
    }
}

private data class MusicCoverCarouselRect(val x: Float, val y: Float, val width: Float, val height: Float)
private enum class MusicCoverCarouselPressKind { Previous, Next }
private data class MusicCoverCarouselStep(
    val at: Float,
    val to: Float,
    val response: Float,
    val damping: Float,
    val velocity: Float,
    val press: MusicCoverCarouselPressKind? = null,
)
private data class MusicCoverCarouselTrack(
    val id: String,
    val title: String,
    val artist: String,
    val cover: Int,
    val glow: Color,
    val wash: List<Color>,
)
private data class MusicCoverCarouselTransition(
    val from: Float,
    val to: Float,
    val velocity: Float,
    val started: Float,
    val duration: Float,
    val reduced: Boolean,
    val stiffness: Float,
)

@Stable
private class MusicCoverCarouselMotion(
    private val autoPlay: Boolean,
    freezeAt: Float?,
) {
    private val frozenAt = freezeAt
    var flow by mutableFloatStateOf(frozenAt?.let(::musicCoverCarouselFlowAt) ?: MusicCoverCarouselTokens.Start)
    var current by mutableIntStateOf(round(flow).toInt().coerceIn(0, MusicCoverCarouselTokens.Tracks.lastIndex))
    var progress by mutableFloatStateOf(frozenAt?.let(::musicCoverCarouselPlayheadAt) ?: 0.22f)
    var previousPress by mutableFloatStateOf(
        frozenAt?.let { musicCoverCarouselPressAt(it, MusicCoverCarouselPressKind.Previous) } ?: 0f,
    )
    var nextPress by mutableFloatStateOf(
        frozenAt?.let { musicCoverCarouselPressAt(it, MusicCoverCarouselPressKind.Next) } ?: 0f,
    )
    var playing by mutableStateOf(true)
    var pressedCard by mutableIntStateOf(-1)
    var dragging = false
    var dragFrom = MusicCoverCarouselTokens.Start
    var dragStartX = 0f

    private var scripted = autoPlay && frozenAt == null
    private var autoStart = -1f
    private var transition: MusicCoverCarouselTransition? = null
    private var contactActive = false
    private var lastTouch = -1f
    private var resumeAt = -1f
    private var previousPulseAt = -1f
    private var nextPulseAt = -1f
    private var changedAt = -1f

    fun initialize(now: Float) {
        if (autoPlay && autoStart < 0f) autoStart = now
        if (changedAt < 0f) {
            val initial = musicCoverCarouselPlayheadAt(0f)
            val elapsed = (initial - MusicCoverCarouselTokens.PlayheadFrom) /
                MusicCoverCarouselTokens.PlayheadPerLoop * MusicCoverCarouselTokens.LoopSeconds
            changedAt = now - elapsed
        }
    }

    fun advance(now: Float, reduced: Boolean) {
        if (frozenAt != null) return
        if (scripted) {
            val clock = positiveModulo(now - autoStart, MusicCoverCarouselTokens.LoopSeconds)
            flow = musicCoverCarouselFlowAt(clock)
            previousPress = musicCoverCarouselPressAt(clock, MusicCoverCarouselPressKind.Previous)
            nextPress = musicCoverCarouselPressAt(clock, MusicCoverCarouselPressKind.Next)
            progress = musicCoverCarouselPlayheadAt(clock)
        } else if (!dragging) {
            transition?.let { item ->
                if (item.reduced) {
                    val unit = ((now - item.started) / item.duration).coerceIn(0f, 1f)
                    flow = item.from + (item.to - item.from) * easeOut(unit)
                    if (unit >= 1f) transition = null
                } else {
                    flow = reanimatedSpringPosition(item.from, item.to, item.velocity, now - item.started, 0.8f, item.stiffness)
                    if (flow == item.to && now > item.started) transition = null
                }
            }
            previousPress = pulse(now, previousPulseAt)
            nextPress = pulse(now, nextPulseAt)
            progress = min(
                1f,
                MusicCoverCarouselTokens.PlayheadFrom + MusicCoverCarouselTokens.PlayheadPerLoop *
                    max(0f, now - changedAt) / MusicCoverCarouselTokens.LoopSeconds,
            )

            if (autoPlay && !contactActive && lastTouch >= 0f && now - lastTouch >= 3.5f && resumeAt < 0f) {
                transition = MusicCoverCarouselTransition(flow, MusicCoverCarouselTokens.Start, 0f, now, 0.3f, true, 0f)
                playing = true
                lastTouch = -1f
                resumeAt = now + 0.32f
            }
            if (resumeAt >= 0f && now >= resumeAt) {
                flow = MusicCoverCarouselTokens.Start
                transition = null
                scripted = autoPlay
                autoStart = now
                resumeAt = -1f
            }
        }
        val next = round(flow).toInt().coerceIn(0, MusicCoverCarouselTokens.Tracks.lastIndex)
        if (next != current) {
            current = next
            if (!scripted) changedAt = now
        }
    }

    fun takeOver(now: Float) {
        if (scripted) {
            val clock = positiveModulo(now - autoStart, MusicCoverCarouselTokens.LoopSeconds)
            val playhead = musicCoverCarouselPlayheadAt(clock)
            val elapsed = (playhead - MusicCoverCarouselTokens.PlayheadFrom) /
                MusicCoverCarouselTokens.PlayheadPerLoop * MusicCoverCarouselTokens.LoopSeconds
            changedAt = now - elapsed
        }
        scripted = false
        transition = null
        resumeAt = -1f
    }

    fun goTo(proposed: Float, velocity: Float, now: Float, reduced: Boolean, pulse: Int = 0) {
        takeOver(now)
        val target = proposed.coerceIn(0f, MusicCoverCarouselTokens.Tracks.lastIndex.toFloat())
        if (reduced) {
            flow = target
            current = round(target).toInt().coerceIn(0, MusicCoverCarouselTokens.Tracks.lastIndex)
            transition = null
            changedAt = now
            return
        }
        val clippedVelocity = towardVelocity(flow, target, velocity)
        transition = MusicCoverCarouselTransition(
            flow,
            target,
            clippedVelocity,
            now,
            if (reduced) 0.2f else 0.4f,
            reduced,
            reanimatedStiffness(flow - target, clippedVelocity, 0.4f, 0.8f),
        )
        if (pulse < 0) previousPulseAt = now
        if (pulse > 0) nextPulseAt = now
        if (!contactActive) lastTouch = now
    }

    fun touched(now: Float) {
        takeOver(now)
        lastTouch = now
    }

    fun beginContact(now: Float) {
        contactActive = true
        takeOver(now)
        lastTouch = -1f
    }

    fun endContact(now: Float) {
        contactActive = false
        lastTouch = now
    }

}

@Composable
fun MusicCoverCarousel(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    freezeAt: Long? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val frozenSeconds = freezeAt?.toFloat()?.div(1_000f)
    val motion = remember(autoPlay, frozenSeconds, reducedMotion) {
        MusicCoverCarouselMotion(autoPlay && !reducedMotion, frozenSeconds)
    }
    val density = LocalDensity.current.density
    val view = LocalView.current

    LaunchedEffect(motion, reducedMotion, freezeAt) {
        if (freezeAt != null || reducedMotion) return@LaunchedEffect
        var first = true
        while (isActive) {
            withFrameNanos { nanos ->
                val now = nanos / 1e9f
                if (first) {
                    motion.initialize(now)
                    first = false
                }
                motion.advance(now, reducedMotion)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2A1607))
            .pointerInput(freezeAt) {
                if (freezeAt != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val px: (Float) -> Float = { (it - MusicCoverCarouselTokens.PinCenterX) * MusicCoverCarouselTokens.Scale + width / 2f }
        val py: (Float) -> Float = { (it - MusicCoverCarouselTokens.PinCenterY) * MusicCoverCarouselTokens.Scale + height / 2f }
        val cardY = py(MusicCoverCarouselTokens.CardCenterY)
        val iconY = py(MusicCoverCarouselTokens.IconY)

        MusicCoverCarouselTokens.Tracks.forEachIndexed { index, track ->
            MusicCoverCarouselBackdrop(track, index, motion)
        }
        MusicCoverCarouselRoom(width, height, px, py)
        MusicCoverCarouselTokens.Tracks.forEachIndexed { index, track ->
            MusicCoverCarouselCard(track, index, motion, px(MusicCoverCarouselTokens.CardCenterX), cardY, reducedMotion, density)
        }

        if (freezeAt == null) {
            MusicCoverCarouselFlowGesture(
                motion = motion,
                centerX = px(MusicCoverCarouselTokens.CardCenterX),
                cardY = cardY,
                reducedMotion = reducedMotion,
                density = density,
                haptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            )
        }

        MusicCoverCarouselPlayerBar(px, py)
        MusicCoverCarouselIconButton(MusicCoverCarouselGlyph.Rewind, "Previous", px(MusicCoverCarouselTokens.PreviousX), iconY, motion.previousPress) {
            val now = System.nanoTime() / 1e9f
            motion.goTo(round(motion.flow) - 1f, 0f, now, reducedMotion, -1)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        MusicCoverCarouselPlayButton(motion, px(MusicCoverCarouselTokens.PauseX), iconY) {
            motion.touched(System.nanoTime() / 1e9f)
            motion.playing = !motion.playing
        }
        MusicCoverCarouselIconButton(MusicCoverCarouselGlyph.Forward, "Next", px(MusicCoverCarouselTokens.NextX), iconY, motion.nextPress) {
            val now = System.nanoTime() / 1e9f
            motion.goTo(round(motion.flow) + 1f, 0f, now, reducedMotion, 1)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        MusicCoverCarouselNowPlaying(motion, px, py)
        MusicCoverCarouselIconButton(MusicCoverCarouselGlyph.Lyrics, "Lyrics", px(MusicCoverCarouselTokens.LyricsX), iconY) { motion.touched(System.nanoTime() / 1e9f) }
        MusicCoverCarouselIconButton(MusicCoverCarouselGlyph.List, "Queue", px(MusicCoverCarouselTokens.ListX), iconY) { motion.touched(System.nanoTime() / 1e9f) }
        MusicCoverCarouselIconButton(MusicCoverCarouselGlyph.Volume, "Volume", px(MusicCoverCarouselTokens.VolumeX), iconY) { motion.touched(System.nanoTime() / 1e9f) }
    }
}

@Composable
private fun MusicCoverCarouselBackdrop(track: MusicCoverCarouselTrack, index: Int, motion: MusicCoverCarouselMotion) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - min(1f, abs(index - motion.flow)) }
            .background(Brush.horizontalGradient(track.wash)),
    ) {
        Image(
            painter = painterResource(track.cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp)
                .graphicsLayer {
                    alpha = 0.12f
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
        )
    }
}

@Composable
private fun MusicCoverCarouselRoom(
    width: Float,
    height: Float,
    px: (Float) -> Float,
    py: (Float) -> Float,
) {
    Canvas(Modifier.size(width.dp, height.dp)) {
        musicCoverCarouselRadial(px(301f), py(160f), 185f * 0.615f, 140f * 0.615f, Color.Black, listOf(0f to 1f, 0.5f to 0.75f, 1f to 0f))
        musicCoverCarouselRadial(px(375f), py(385f), 180f * 0.615f, 45f * 0.615f, Color.Black, listOf(0f to 0.95f, 0.5f to 0.665f, 1f to 0f))
        musicCoverCarouselRadial(px(250f), py(410f), 190f * 0.615f, 95f * 0.615f, Color.Black, listOf(0f to 0.92f, 0.55f to 0.736f, 1f to 0f))
    }
}

private fun DrawScope.musicCoverCarouselRadial(
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    color: Color,
    stops: List<Pair<Float, Float>>,
) {
    val shaderStops = stops.map { it.first to color.copy(alpha = it.second) }.toTypedArray()
    val radius = radiusX.dp.toPx()
    val yScale = radiusY / radiusX
    val center = Offset(centerX.dp.toPx(), centerY.dp.toPx() / yScale)
    withTransform({ scale(1f, yScale, pivot = Offset.Zero) }) {
        drawCircle(
            brush = Brush.radialGradient(*shaderStops, center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun MusicCoverCarouselCard(
    track: MusicCoverCarouselTrack,
    index: Int,
    motion: MusicCoverCarouselMotion,
    centerX: Float,
    centerY: Float,
    reducedMotion: Boolean,
    density: Float,
) {
    val s = MusicCoverCarouselTokens.Scale
    val view = LocalView.current
    val width = MusicCoverCarouselTokens.CardWidth * s
    val height = MusicCoverCarouselTokens.CardHeight * s
    val order by remember(index) {
        derivedStateOf { round(100f - min(3f, abs(index - motion.flow)) * 10f) }
    }
    val press by animateFloatAsState(
        targetValue = if (motion.pressedCard == index) 1f else 0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "card press",
    )
    val shape = RoundedCornerShape((17f * s).dp)

    Box(
        Modifier
            .zIndex(order)
            .offset(x = (centerX - width / 2f).dp, y = (centerY - height / 2f).dp)
            .size(width.dp, height.dp)
            .graphicsLayer {
                val placement = musicCoverCarouselPlacement(index, motion.flow, reducedMotion)
                translationX = placement.x.dp.toPx()
                scaleX = placement.scale * (1f - 0.03f * press)
                scaleY = placement.scale * (1f - 0.03f * press)
                rotationY = -placement.turn
                cameraDistance = 700f * density
                alpha = placement.alpha
                clip = true
                this.shape = shape
            }
            .background(Color(0x6B786E68))
            .border((2f / density).dp, Color.White.copy(alpha = 0.32f), shape)
            .semantics {
                contentDescription = "${track.title} by ${track.artist}"
                role = Role.Button
                onClick {
                    val now = System.nanoTime() / 1e9f
                    motion.goTo(index.toFloat(), 0f, now, reducedMotion)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    true
                }
            },
    ) {
        val inset = 5f * s
        Image(
            painter = painterResource(track.cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset(inset.dp, inset.dp)
                .size((width - inset * 2f).dp, (205f * s).dp)
                .clip(RoundedCornerShape((12f * s).dp)),
        )
        Text(
            text = track.title,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = 4.dp, y = (228f * s - 8f).dp)
                .width((width - 8f).dp)
                .height(16.dp)
                .graphicsLayer { alpha = 1f - 0.25f * ramp(abs(index - motion.flow), 0f, 1f) },
            style = TextStyle(
                fontSize = (17.5f * s).sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
        Text(
            text = track.artist,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = 4.dp, y = (252f * s - 7f).dp)
                .width((width - 8f).dp)
                .height(14.dp)
                .graphicsLayer { alpha = 1f - 0.25f * ramp(abs(index - motion.flow), 0f, 1f) },
            style = TextStyle(
                fontSize = (16.5f * s).sp,
                lineHeight = 14.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (reducedMotion) return@drawBehind
                    val distance = index - motion.flow
                    val placement = musicCoverCarouselPlacement(index, motion.flow, reduced = false)
                    val between = abs(distance - round(distance))
                    val opacity = sin(PI.toFloat() * between)
                    val bandWidth = size.width * MusicCoverCarouselTokens.SheenWidth
                    val left = (0.5f - placement.turn / MusicCoverCarouselTokens.SheenSpan) *
                        size.width - bandWidth / 2f
                    withTransform({ rotate(MusicCoverCarouselTokens.SheenSlant) }) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = MusicCoverCarouselTokens.SheenAlpha * opacity),
                                1f to Color.Transparent,
                                startX = left,
                                endX = left + bandWidth,
                            ),
                            topLeft = Offset(left, -size.height * 0.2f),
                            size = Size(bandWidth, size.height * 1.4f),
                        )
                    }
                },
        )
    }
}

private data class MusicCoverCarouselPlacement(
    val x: Float,
    val scale: Float,
    val turn: Float,
    val alpha: Float,
)

private fun musicCoverCarouselPlacement(index: Int, flow: Float, reduced: Boolean): MusicCoverCarouselPlacement {
    val distance = index - flow
    val amount = min(3f, abs(distance))
    val side = if (distance < 0f) -1f else 1f
    val slots = if (side > 0f) MusicCoverCarouselTokens.SlotsRight else MusicCoverCarouselTokens.Slots
    val x = side * interpolate(amount, floatArrayOf(0f, 1f, 2f, 3f), floatArrayOf(0f, slots[1], slots[2], slots[2] + 40f)) * MusicCoverCarouselTokens.Scale
    return MusicCoverCarouselPlacement(
        x = if (reduced) round(distance) * 108f * MusicCoverCarouselTokens.Scale else x,
        scale = if (reduced) 1f else interpolate(amount, floatArrayOf(0f, 1f, 2f, 3f), floatArrayOf(1f, 0.86f, 0.78f, 0.7f)),
        turn = if (reduced) 0f else side * interpolate(amount, floatArrayOf(0f, 1f, 2f, 3f), floatArrayOf(0f, 12f, 20f, 34f)),
        alpha = if (reduced) 1f - ramp(amount, 0f, 3f) * 0.6f else 1f - ramp(amount, 2.2f, 3f),
    )
}

private fun musicCoverCarouselHitCard(
    x: Float,
    y: Float,
    flow: Float,
    centerX: Float,
    reducedMotion: Boolean,
): Int? {
    val cardWidth = MusicCoverCarouselTokens.CardWidth * MusicCoverCarouselTokens.Scale
    val cardHeight = MusicCoverCarouselTokens.CardHeight * MusicCoverCarouselTokens.Scale
    return MusicCoverCarouselTokens.Tracks.indices
        .sortedBy { abs(it - flow) }
        .firstOrNull { index ->
            val placement = musicCoverCarouselPlacement(index, flow, reducedMotion)
            val halfWidth = cardWidth * placement.scale * abs(cos(placement.turn * PI.toFloat() / 180f)) / 2f
            val halfHeight = cardHeight * placement.scale / 2f
            abs(x - (centerX + placement.x)) <= halfWidth && abs(y - 140f) <= halfHeight
        }
}

@Composable
private fun MusicCoverCarouselFlowGesture(
    motion: MusicCoverCarouselMotion,
    centerX: Float,
    cardY: Float,
    reducedMotion: Boolean,
    density: Float,
    haptic: () -> Unit,
) {
    Box(
        Modifier
            .offset(y = (cardY - 140f).dp)
            .fillMaxWidth()
            .height(280.dp)
            .pointerInput(motion, reducedMotion) {
                awaitEachGesture {
                    val tracker = VelocityTracker()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x / density
                    val startY = down.position.y / density
                    tracker.addPosition(down.uptimeMillis, down.position)
                    motion.beginContact(down.uptimeMillis / 1000f)
                    motion.dragFrom = motion.flow
                    motion.dragStartX = startX
                    motion.pressedCard = musicCoverCarouselHitCard(
                        x = startX,
                        y = down.position.y / density,
                        flow = motion.flow,
                        centerX = centerX,
                        reducedMotion = reducedMotion,
                    ) ?: -1
                    var panning = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val x = change.position.x / density
                            val dx = x - startX
                            val dy = change.position.y / density - startY
                            if (abs(dy) > 16f) motion.pressedCard = -1
                            if (!change.pressed) {
                                val now = change.uptimeMillis / 1000f
                                val tappedCard = motion.pressedCard
                                motion.pressedCard = -1
                                if (panning) {
                                    val velocity = tracker.calculateVelocity().x / density
                                    val v = -velocity / (108f * MusicCoverCarouselTokens.Scale)
                                    val flick = if (abs(velocity) / 1000f > 0.11f) sign(v) else 0f
                                    val target = if (flick != 0f) round(motion.dragFrom) + flick else round(motion.flow + v * 0.15f)
                                    motion.dragging = false
                                    motion.goTo(target, v, now, reducedMotion)
                                    haptic()
                                } else if (tappedCard >= 0) {
                                    motion.goTo(tappedCard.toFloat(), 0f, now, reducedMotion)
                                    haptic()
                                }
                                change.consume()
                                break
                            }
                            if (!panning && abs(dx) >= 8f) {
                                panning = true
                                motion.dragging = true
                                motion.pressedCard = -1
                            }
                            if (panning) {
                                change.consume()
                                val raw = motion.dragFrom - dx / (108f * MusicCoverCarouselTokens.Scale)
                                val last = MusicCoverCarouselTokens.Tracks.lastIndex.toFloat()
                                val over = if (raw < 0f) raw else if (raw > last) raw - last else 0f
                                motion.flow = raw - over + over * 0.3f
                            }
                        }
                    } finally {
                        motion.pressedCard = -1
                        if (motion.dragging) {
                            motion.dragging = false
                            motion.goTo(round(motion.flow), 0f, System.nanoTime() / 1e9f, reducedMotion)
                        }
                        motion.endContact(System.nanoTime() / 1e9f)
                    }
                }
            },
    )
}

@Composable
private fun MusicCoverCarouselPlayerBar(px: (Float) -> Float, py: (Float) -> Float) {
    val token = MusicCoverCarouselTokens.Player
    val density = LocalDensity.current.density
    val width = token.width * MusicCoverCarouselTokens.Scale
    val height = token.height * MusicCoverCarouselTokens.Scale
    val shape = RoundedCornerShape((height / 2f).dp)
    Box(
        Modifier
            .offset(x = px(token.x).dp, y = py(token.y).dp)
            .size(width.dp, height.dp)
            .clip(shape)
            .background(Color(0x33965C1E))
            .border((2f / density).dp, Color.White.copy(alpha = 0.38f), shape),
    )
}

@Composable
private fun MusicCoverCarouselNowPlaying(
    motion: MusicCoverCarouselMotion,
    px: (Float) -> Float,
    py: (Float) -> Float,
) {
    val s = MusicCoverCarouselTokens.Scale
    val token = MusicCoverCarouselTokens.Capsule
    val width = token.width * s
    val height = token.height * s
    val track = MusicCoverCarouselTokens.Tracks[motion.current]

    Box(
        Modifier
            .offset(x = px(token.x).dp, y = py(token.y).dp)
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape((12f * s).dp))
            .background(Color(0xC7100E0D)),
    ) {
        AnimatedContent(
            targetState = track,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
            label = "track swap",
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(shown.cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = ((254f - token.x) * s).dp, y = ((424f - token.y) * s).dp)
                        .size((38f * s).dp)
                        .clip(RoundedCornerShape((7f * s).dp)),
                )
                Text(
                    text = shown.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = ((300f - token.x) * s).dp, y = ((436f - token.y) * s - 6f).dp)
                        .width(80.dp)
                        .height(11.dp),
                    style = TextStyle(
                        fontSize = (12.5f * s).sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
                Text(
                    text = shown.artist,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = ((300f - token.x) * s).dp, y = ((452f - token.y) * s - 5f).dp)
                        .width(80.dp)
                        .height(10.dp),
                    style = TextStyle(
                        fontSize = (10.5f * s).sp,
                        lineHeight = 10.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
        }
        Canvas(
            Modifier
                .offset(x = ((254f - token.x) * s).dp, y = ((464.2f - token.y) * s).dp)
                .size((106f * s).dp, 1.5.dp),
        ) {
            drawRoundRect(Color.White.copy(alpha = 0.84f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            drawRoundRect(
                color = Color.White,
                size = Size(size.width * motion.progress, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
            )
        }
        MusicCoverCarouselGlyphView(
            glyph = MusicCoverCarouselGlyph.Wave,
            modifier = Modifier.offset(
                x = ((442.8f - token.x) * s - 5f).dp,
                y = ((height - 5f) / 2f).dp,
            ),
        )
        MusicCoverCarouselGlyphView(
            glyph = MusicCoverCarouselGlyph.More,
            modifier = Modifier.offset(
                x = ((470f - token.x) * s - 6f).dp,
                y = ((height - 12f) / 2f).dp,
            ),
        )
    }
}

@Composable
private fun MusicCoverCarouselIconButton(
    glyph: MusicCoverCarouselGlyph,
    label: String,
    centerX: Float,
    centerY: Float,
    scriptedPress: Float = 0f,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val localPress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "icon press",
    )
    Box(
        Modifier
            .offset(x = (centerX - 22f).dp, y = (centerY - 22f).dp)
            .size(44.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MusicCoverCarouselGlyphView(
            glyph = glyph,
            modifier = Modifier.graphicsLayer {
                val amount = max(localPress, scriptedPress)
                alpha = 1f - 0.4f * amount
                scaleX = 1f - 0.03f * amount
                scaleY = 1f - 0.03f * amount
            },
        )
    }
}

@Composable
private fun MusicCoverCarouselPlayButton(
    motion: MusicCoverCarouselMotion,
    centerX: Float,
    centerY: Float,
    onClick: () -> Unit,
) {
    val mix by animateFloatAsState(
        targetValue = if (motion.playing) 1f else 0f,
        animationSpec = tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "play pause",
    )
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val localPress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "play press",
    )
    Box(
        Modifier
            .offset(x = (centerX - 22f).dp, y = (centerY - 22f).dp)
            .size(44.dp)
            .semantics {
                contentDescription = if (motion.playing) "Pause" else "Play"
                role = Role.Button
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .graphicsLayer {
                alpha = 1f - 0.4f * localPress
                scaleX = 1f - 0.03f * localPress
                scaleY = 1f - 0.03f * localPress
            },
        contentAlignment = Alignment.Center,
    ) {
        MusicCoverCarouselGlyphView(
            MusicCoverCarouselGlyph.Pause,
            Modifier.graphicsLayer {
                alpha = mix
                scaleX = 0.7f + 0.3f * mix
                scaleY = 0.7f + 0.3f * mix
            },
        )
        MusicCoverCarouselGlyphView(
            MusicCoverCarouselGlyph.Play,
            Modifier.graphicsLayer {
                alpha = 1f - mix
                scaleX = 1f - 0.3f * mix
                scaleY = 1f - 0.3f * mix
            },
        )
    }
}

private enum class MusicCoverCarouselGlyph(val width: Float, val height: Float) {
    Rewind(15.3f, 8.7f),
    Pause(8.7f, 11.7f),
    Play(8.7f, 11.7f),
    Forward(15.6f, 8.7f),
    Lyrics(13f, 12f),
    List(13f, 9.3f),
    Volume(13f, 9f),
    Wave(10f, 5f),
    More(12f, 12f),
}

@Composable
private fun MusicCoverCarouselGlyphView(
    glyph: MusicCoverCarouselGlyph,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier.size(glyph.width.dp, glyph.height.dp)) {
        scale(scaleX = density, scaleY = density, pivot = Offset.Zero) {
            when (glyph) {
            MusicCoverCarouselGlyph.Rewind -> {
                musicCoverCarouselTriangle(color, Offset(7.4f, 0.5f), Offset(7.4f, 8.2f), Offset(0.5f, 4.35f))
                musicCoverCarouselTriangle(color, Offset(14.8f, 0.5f), Offset(14.8f, 8.2f), Offset(7.9f, 4.35f))
            }
            MusicCoverCarouselGlyph.Forward -> {
                musicCoverCarouselTriangle(color, Offset(0.5f, 0.5f), Offset(0.5f, 8.2f), Offset(7.5f, 4.35f))
                musicCoverCarouselTriangle(color, Offset(8.1f, 0.5f), Offset(8.1f, 8.2f), Offset(15.1f, 4.35f))
            }
            MusicCoverCarouselGlyph.Pause -> {
                drawRoundRect(color, topLeft = Offset.Zero, size = Size(3.2f, 11.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.9f))
                drawRoundRect(color, topLeft = Offset(5.5f, 0f), size = Size(3.2f, 11.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.9f))
            }
            MusicCoverCarouselGlyph.Play -> musicCoverCarouselTriangle(color, Offset(1f, 1.6f), Offset(7.7f, 5.85f), Offset(1f, 11f))
            MusicCoverCarouselGlyph.More -> listOf(2.5f, 6f, 9.5f).forEach { x -> drawCircle(color, 0.95f, Offset(x, 6f)) }
            MusicCoverCarouselGlyph.Wave -> {
                drawRoundRect(color, Offset(0f, 0.8f), Size(2.2f, 3.4f), androidx.compose.ui.geometry.CornerRadius(1.1f))
                drawRoundRect(color, Offset(2.9f, 0f), Size(4.2f, 5f), androidx.compose.ui.geometry.CornerRadius(1.6f))
                drawRoundRect(color, Offset(7.8f, 0.8f), Size(2.2f, 3.4f), androidx.compose.ui.geometry.CornerRadius(1.1f))
            }
            MusicCoverCarouselGlyph.List -> listOf(0.7f, 4.65f, 8.6f).forEach { y ->
                drawCircle(color, 0.75f, Offset(0.75f, y))
                drawLine(color, Offset(3.3f, y), Offset(12.3f, y), 1.4f, StrokeCap.Round)
            }
            MusicCoverCarouselGlyph.Volume -> musicCoverCarouselVolume(color)
                MusicCoverCarouselGlyph.Lyrics -> musicCoverCarouselLyrics(color)
            }
        }
    }
}

private fun DrawScope.musicCoverCarouselTriangle(color: Color, a: Offset, b: Offset, c: Offset) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = 1f, join = StrokeJoin.Round))
}

private fun DrawScope.musicCoverCarouselVolume(color: Color) {
    val speaker = Path().apply {
        moveTo(0f, 2.9f)
        lineTo(2.4f, 2.9f)
        lineTo(5.6f, 0.2f)
        lineTo(5.6f, 8.8f)
        lineTo(2.4f, 6.1f)
        lineTo(0f, 6.1f)
        close()
    }
    drawPath(speaker, color)
    val waves = Path().apply {
        moveTo(7.6f, 2.6f)
        cubicTo(10.2f, 3.4f, 10.2f, 5.6f, 7.6f, 6.4f)
        moveTo(9.9f, 0.9f)
        cubicTo(14f, 2.1f, 14f, 6.9f, 9.9f, 8.1f)
    }
    drawPath(waves, color, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
}

private fun DrawScope.musicCoverCarouselLyrics(color: Color) {
    val bubble = Path().apply {
        moveTo(2.8f, 0.75f)
        lineTo(10.2f, 0.75f)
        quadraticTo(12.25f, 0.75f, 12.25f, 2.8f)
        lineTo(12.25f, 7.4f)
        quadraticTo(12.25f, 9.45f, 10.2f, 9.45f)
        lineTo(6.1f, 9.45f)
        lineTo(3.3f, 11.3f)
        lineTo(3.3f, 9.45f)
        lineTo(2.8f, 9.45f)
        quadraticTo(0.75f, 9.45f, 0.75f, 7.4f)
        lineTo(0.75f, 2.8f)
        quadraticTo(0.75f, 0.75f, 2.8f, 0.75f)
    }
    drawPath(bubble, color, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
    listOf(4.6f, 8.2f).forEach { x ->
        drawCircle(color, 1.3f, Offset(x, 4.4f))
        val tail = Path().apply {
            moveTo(x + 1f, 4.4f)
            quadraticTo(x + 1f, 6.4f, x - 0.8f, 7.4f)
        }
        drawPath(tail, color, style = Stroke(width = 1.1f, cap = StrokeCap.Round))
    }
}

private fun musicCoverCarouselSpringRemain(
    time: Float,
    response: Float,
    damping: Float,
    velocity: Float,
): Float {
    if (time <= 0f) return 1f
    if (time > response * 5f) return 0f
    val frequency = 2f * PI.toFloat() / response
    if (damping >= 1f) {
        return exp(-frequency * time) * (1f + (frequency - velocity) * time)
    }
    val damped = frequency * sqrt(1f - damping * damping)
    return exp(-damping * frequency * time) * (
        cos(damped * time) + ((damping * frequency - velocity) / damped) * sin(damped * time)
        )
}

private fun musicCoverCarouselFlowAt(time: Float): Float {
    var flow = MusicCoverCarouselTokens.Start
    var from = MusicCoverCarouselTokens.Start
    MusicCoverCarouselTokens.Steps.forEach { step ->
        if (time > step.at) {
            val normalizedVelocity = step.velocity / abs(step.to - from)
            flow += (step.to - from) * (
                1f - musicCoverCarouselSpringRemain(
                    time = time - step.at,
                    response = step.response,
                    damping = step.damping,
                    velocity = normalizedVelocity,
                )
                )
        }
        from = step.to
    }
    return flow
}

private fun musicCoverCarouselPressAt(time: Float, kind: MusicCoverCarouselPressKind): Float {
    MusicCoverCarouselTokens.Steps.forEach { step ->
        if (step.press != kind) return@forEach
        val unit = (time - step.at + MusicCoverCarouselTokens.PressDuration) /
            MusicCoverCarouselTokens.PressDuration
        if (unit > 0f && unit < 2f) return if (unit < 1f) unit else 2f - unit
    }
    return 0f
}

private fun musicCoverCarouselPlayheadAt(time: Float): Float {
    val finalChange = MusicCoverCarouselTokens.Changes.lastOrNull()
        ?: return MusicCoverCarouselTokens.PlayheadFrom
    var last = finalChange - MusicCoverCarouselTokens.LoopSeconds
    MusicCoverCarouselTokens.Changes.forEach { change -> if (change <= time) last = change }
    return min(
        1f,
        MusicCoverCarouselTokens.PlayheadFrom + MusicCoverCarouselTokens.PlayheadPerLoop *
            ((time - last) / MusicCoverCarouselTokens.LoopSeconds),
    )
}

private fun positiveModulo(value: Float, modulus: Float): Float {
    val result = value % modulus
    return if (result < 0f) result + modulus else result
}

private fun ramp(value: Float, start: Float, end: Float): Float {
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun interpolate(value: Float, input: FloatArray, output: FloatArray): Float {
    for (index in 0 until input.lastIndex) {
        if (value <= input[index + 1]) {
            val amount = (value - input[index]) / (input[index + 1] - input[index])
            return output[index] + (output[index + 1] - output[index]) * amount
        }
    }
    return output.last()
}

private fun pulse(clock: Float, at: Float): Float {
    if (at < 0f) return 0f
    val elapsed = positiveModulo(clock - at, MusicCoverCarouselTokens.LoopSeconds)
    if (elapsed >= 0.24f) return 0f
    return if (elapsed < 0.12f) easeOut(elapsed / 0.12f) else 1f - easeOut((elapsed - 0.12f) / 0.12f)
}

private const val ReanimatedMass = 4f
private const val ReanimatedEnergyThreshold = 6e-9f

private fun towardVelocity(from: Float, to: Float, velocity: Float): Float {
    return if ((to > from && velocity < 0f) || (to < from && velocity > 0f)) 0f else velocity
}

private fun reanimatedStiffness(
    displacement: Float,
    velocity: Float,
    duration: Float,
    dampingRatio: Float,
): Float {
    if (displacement == 0f && velocity == 0f) return 0f
    val settlingDuration = duration * 1.5f
    fun difference(stiffness: Float): Float {
        val decay = sqrt(stiffness / ReanimatedMass) * dampingRatio
        val envelope = exp(-decay * settlingDuration)
        val term = velocity + displacement * decay
        val position = (displacement + term * settlingDuration) * envelope
        val speed = position * -decay + term * envelope
        val initial = reanimatedEnergy(displacement, velocity, stiffness)
        val settled = reanimatedEnergy(position, speed, stiffness)
        return settled / initial - ReanimatedEnergyThreshold
    }

    var low = 1e-12f
    var high = 8_000f
    val direction = if (difference(high) >= difference(low)) 1f else -1f
    var current = (low + high) / 2f
    repeat(100) {
        if (abs(difference(current)) <= ReanimatedEnergyThreshold * 1e-3f) return current
        if (difference(current) * direction < 0f) low = current else high = current
        current = (low + high) / 2f
    }
    return current
}

private fun reanimatedSpringPosition(
    from: Float,
    to: Float,
    velocity: Float,
    elapsed: Float,
    dampingRatio: Float,
    stiffness: Float,
): Float {
    if (elapsed <= 0f) return from
    if (stiffness <= 0f) return to
    val clippedVelocity = towardVelocity(from, to, velocity)
    val displacement = from - to
    val omega0 = sqrt(stiffness / ReanimatedMass)
    val omega1 = omega0 * sqrt(max(0.0001f, 1f - dampingRatio * dampingRatio))
    val envelope = exp(-dampingRatio * omega0 * elapsed)
    val sine = sin(omega1 * elapsed)
    val cosine = cos(omega1 * elapsed)
    val fragment = envelope * (
        sine * ((clippedVelocity + dampingRatio * omega0 * displacement) / omega1) + displacement * cosine
    )
    val value = to + fragment
    val speed = -dampingRatio * omega0 * fragment + envelope * (
        cosine * (clippedVelocity + dampingRatio * omega0 * displacement) - omega1 * displacement * sine
    )
    val initial = reanimatedEnergy(displacement, clippedVelocity, stiffness)
    val current = reanimatedEnergy(to - value, speed, stiffness)
    return if (initial == 0f || current / initial <= ReanimatedEnergyThreshold) to else value
}

private fun reanimatedEnergy(displacement: Float, velocity: Float, stiffness: Float): Float {
    return 0.5f * stiffness * displacement * displacement + 0.5f * ReanimatedMass * velocity * velocity
}

private fun easeOut(value: Float): Float {
    return musicCoverCarouselBezier(value, 0.23f, 1f, 0.32f, 1f)
}

private fun musicCoverCarouselBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var low = 0f
    var high = 1f
    repeat(12) {
        val t = (low + high) / 2f
        if (musicCoverCarouselCubic(t, 0f, x1, x2, 1f) < x) low = t else high = t
    }
    return musicCoverCarouselCubic((low + high) / 2f, 0f, y1, y2, 1f)
}

private fun musicCoverCarouselCubic(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val u = 1f - t
    return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
}
