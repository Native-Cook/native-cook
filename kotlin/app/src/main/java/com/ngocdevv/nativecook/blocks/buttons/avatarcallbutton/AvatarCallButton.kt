package com.ngocdevv.nativecook.blocks.buttons.avatarcallbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The pill has a fixed size so the avatars have known places to travel between.
private const val WIDTH = 164f
private const val HEIGHT = 52f
private const val AVATAR = 38f

// The canvas overflows the button by this much on every side to fit the shadow.
private val BLEED = 26.dp

// Avatar centres, measured from the pill's left edge: where they fade in, where they pause as
// "host + you", and where they end up overlapping.
private val HOST_X = floatArrayOf(26f, 48f, 27f)
private val GUEST_X = floatArrayOf(138f, 116f, 53f)
private const val MESSAGE_X = 80f
private const val MESSAGE_WIDTH = 80f

private const val OPEN_MS = 380
private const val PAIR_HOLD_MS = 420
private const val JOIN_MS = 520
private const val CLOSE_MS = 320

// How long a tap keeps the greeting up, and the rhythm of the autoplay loop.
private const val TAP_HOLD_MS = 1300
private const val AUTOPLAY_PAUSE_MS = 1500L
private const val AUTOPLAY_HOLD_MS = 1700

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.2.sp,
)
private val PlusStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val InitialsStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val YouStyle = TextStyle(color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
private val MessageStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)

private class AvatarCallMotion(private val scope: CoroutineScope) {
    /** 0 → 1: the label makes way for the two avatars. */
    val open = Animatable(0f)

    /** 0 → 1: the avatars slide together and the message appears. */
    val join = Animatable(0f)
    var held = false
    var auto = false
    var tapped = false
    var pressedAt = 0L
    private var greeting = false
    private var job: Job? = null
    private var tapJob: Job? = null

    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.34f), sigma = 9f)
    val pill = Path()

    // A blurred stroke; hardware canvases only honour mask filters from API 28.
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = rgba(255, 255, 255, 0.46f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(3.5f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    /** Starts or ends the greeting so it matches what press, tap and autoplay currently ask for. */
    fun sync() {
        val wanted = held || auto || tapped
        if (wanted == greeting) return
        greeting = wanted
        job?.cancel()
        job = scope.launch {
            if (wanted) {
                open.animateTo(1f, tween(OPEN_MS, easing = EaseOutStrong))
                delay(PAIR_HOLD_MS.toLong())
                join.animateTo(1f, tween(JOIN_MS, easing = EaseInOut))
            } else {
                launch { join.animateTo(0f, tween((CLOSE_MS * 0.7f).toInt(), easing = EaseOutStrong)) }
                open.animateTo(0f, tween(CLOSE_MS, easing = EaseOutStrong))
            }
        }
    }

    /** A tap is too short to watch the greeting, so it keeps it up for a moment. */
    fun tap() {
        // A long hold has already shown it.
        if (SystemClock.uptimeMillis() - pressedAt > OPEN_MS + PAIR_HOLD_MS) return
        tapped = true
        tapJob?.cancel()
        tapJob = scope.launch {
            delay((OPEN_MS + PAIR_HOLD_MS + JOIN_MS + TAP_HOLD_MS).toLong())
            tapped = false
            sync()
        }
        sync()
    }
}

/**
 * Black "BOOK A CALL" pill. While pressed (or by itself with [autoPlay]) the label slides away,
 * two avatars swing in as "host + you", then slide together and a short message wipes in beside
 * them. A tap plays the whole greeting once.
 *
 * @param message Shown next to the two avatars once they have met.
 * @param initials Initials on the host's avatar; the other one always reads "YOU".
 * @param autoPlay Plays the whole greeting by itself with a pause between runs.
 */
@Composable
fun AvatarCallButton(
    label: String = "BOOK A CALL",
    message: String = "Let's talk!",
    initials: String = "JD",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { AvatarCallMotion(scope) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    motion.pressedAt = SystemClock.uptimeMillis()
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
            motion.held = pressed
            motion.sync()
        }
    }

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.auto = true
            motion.sync()
            delay((OPEN_MS + PAIR_HOLD_MS + JOIN_MS + AUTOPLAY_HOLD_MS).toLong())
            motion.auto = false
            motion.sync()
            delay(CLOSE_MS.toLong())
        }
    }
    DisposableEffect(autoPlay) {
        onDispose {
            motion.auto = false
            motion.sync()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                motion.tap()
            }
            .semantics { contentDescription = label }
            .size(WIDTH.dp, HEIGHT.dp),
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val r = h / 2
                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, x + 2, y + 8, w - 4, h - 2, r) }
                    drawRoundRect(Color(0xFF030303), Offset(x, y), Size(w, h), CornerRadius(r))

                    // A soft light hugging the inside of the edge, as if the pill were slightly domed.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        motion.pill.reset()
                        motion.pill.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r)))
                        clipPath(motion.pill) {
                            drawIntoCanvas { it.nativeCanvas.drawRoundRect(x, y, x + w, y + h, r, r, motion.rim) }
                        }
                    }
                }
            }
        }

        // The texts below are decoration; the button's own semantics carry the label.
        Box(Modifier.matchParentSize().clip(CircleShape).clearAndSetSemantics {}) {
            BasicText(
                text = label,
                style = LabelStyle,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val o = motion.open.value
                    alpha = 1f - min(1f, o * 1.8f)
                    translationY = if (reducedMotion) 0f else -22.dp.toPx() * o
                },
            )
            BasicText(
                text = "+",
                style = PlusStyle,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    alpha = if (reducedMotion) 0f else motion.open.value * (1f - min(1f, motion.join.value * 2.5f))
                },
            )
            Avatar(Color(0xFFFF7A59), initials, InitialsStyle, HOST_X, -1f, motion, reducedMotion)
            Avatar(Color.White, "YOU", YouStyle, GUEST_X, 1f, motion, reducedMotion)
            BasicText(
                text = message,
                style = MessageStyle,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = MESSAGE_X.dp)
                    .width(MESSAGE_WIDTH.dp)
                    .graphicsLayer {
                        alpha = if (reducedMotion) motion.open.value else min(1f, wipe(motion.join.value) * 2f)
                    }
                    // The message is wiped in from the left.
                    .drawWithContent {
                        val t = if (reducedMotion) 1f else wipe(motion.join.value)
                        clipRect(right = size.width * t) { this@drawWithContent.drawContent() }
                    },
            )
        }
    }
}

private fun wipe(join: Float) = max(0f, (join - 0.35f) / 0.65f)

/** One avatar disc. [stops] are its three centre positions and [swing] the side it rotates in from. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Avatar(
    color: Color,
    text: String,
    style: TextStyle,
    stops: FloatArray,
    swing: Float,
    motion: AvatarCallMotion,
    reducedMotion: Boolean,
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(AVATAR.dp)
            .graphicsLayer {
                val o = motion.open.value
                val j = motion.join.value
                // Reduced motion keeps the fades but nothing travels: the avatars appear already joined.
                if (reducedMotion) {
                    alpha = o
                    translationX = (stops[2] - AVATAR / 2).dp.toPx()
                } else {
                    val x = stops[0] + (stops[1] - stops[0]) * o + (stops[2] - stops[1]) * j
                    alpha = min(1f, o * 1.5f)
                    translationX = (x - AVATAR / 2).dp.toPx()
                    rotationZ = swing * 40f * (1f - o)
                    scaleX = 0.8f + 0.2f * o
                    scaleY = 0.8f + 0.2f * o
                }
            }
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = style, maxLines = 1)
    }
}
