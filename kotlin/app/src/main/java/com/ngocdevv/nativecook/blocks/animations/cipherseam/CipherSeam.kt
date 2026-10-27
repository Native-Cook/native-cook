package com.ngocdevv.nativecook.blocks.animations.cipherseam

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlinx.coroutines.delay
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds. */
const val CIPHER_SEAM_LOOP = 10.8

private val ScreenColor = Color(0xFF070411)

/**
 * Cipher Seam: a payments landing page for the invented Cipher. A frosted nav capsule (a layers
 * mark, Log in, a white Talk to an Expert pill and a menu button) over a violet panel, where a lane of
 * fine star dust runs behind a light seam. A sample card (chip, contactless, number, holder and
 * expiry) travels through the seam and comes out the other side as a dense token matrix; the card
 * and the token matrix are one plane clipped at the seam. Under the hero: "Flexible Payments
 * Security", copy and a Talk to an Expert button, a row of invented partner logos, a domain pill, and
 * a round chat button over the page. Port of `CipherSeam.tsx`.
 *
 * Loop of [CIPHER_SEAM_LOOP] s, every frame a function of one clock:
 *   0.00-2.75  the card rests off to the left; the previous pass's tokens leave to the right by 2.5
 *   2.75-9.80  the card's left edge follows the measured knots (-188 to 333 of 362)
 *   5.00-8.10  the seam flares into a white band about 12 dp wide, its right side breaking into particles
 *   9.80-10.80 the card has left; the previous pass's tokens return at 10.55-10.8 and the loop closes
 * Dust drifts and the token glyphs change twelve times a second throughout.
 *
 * Without [autoPlay] the screen rests at time 0. A drag across the hero scrubs the pass (2.75 to
 * 10.5 s) and a release springs to the nearer end; Talk to an Expert in the panel replays the pass
 * with a light haptic. A touch holds an `autoPlay` loop and two seconds after the last one it starts
 * again from 0. [previewTime] freezes the clock at that loop time and turns the touch off, to look
 * at one frame. Under Reduce Motion (animations removed) the card sits still across the seam.
 *
 * @param onLearnMore called when the nav's Talk to an Expert pill opens the info sheet
 */
@Composable
fun CipherSeam(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onLearnMore: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val fontScale = density.fontScale

    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsRemoved(context)) }
    val normalizedPreview = remember(previewTime) { CipherSeamTimeline.normalizePreview(previewTime, CIPHER_SEAM_LOOP) }
    val session = remember(normalizedPreview) { CipherSeamSession(normalizedPreview ?: 0.0, normalizedPreview) }

    // `AppState`: the loop waits while the app is in the background, and a finger there is gone.
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsRemoved(context)
            if (!foreground) session.interacting = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Light status bar icons on the dark page (`StatusBar style="light"`).
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    // The first effect of the React Native file: freeze on a preview; otherwise stand still while in
    // the background, behind the sheet, under Reduce Motion, or when nothing plays by itself; else
    // start the loop from 0 unless a hand has taken over.
    DisposableEffect(session, autoPlay, foreground, session.info, session.manual, reduced) {
        val preview = session.previewTime
        if (preview != null) {
            session.clock.cancel()
            session.clock.set(preview)
        } else if (!foreground || session.info || reduced || (!autoPlay && !session.manual)) {
            session.clock.cancel()
        } else if (autoPlay && !session.manual) {
            session.clock.loop()
        }
        onDispose { }
    }
    DisposableEffect(session) {
        onDispose { session.clock.dispose() }
    }
    // Two seconds after the last touch the hand lets go and the loop starts again.
    LaunchedEffect(session, foreground, session.manual, session.interacting, session.info) {
        if (!session.manual || session.interacting || session.info || !foreground) return@LaunchedEffect
        delay(IDLE_BEFORE_LOOP_MS)
        session.manual = false
    }
    LaunchedEffect(session) {
        while (true) {
            withFrameNanos { nanos -> session.clock.advance(nanos) }
        }
    }

    val scroll = rememberScrollState()
    val closeInfo = {
        session.info = false
        session.release()
    }
    val openInfo = {
        session.begin()
        session.info = true
    }
    val replay = {
        session.replay(reduced)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(ScreenColor)) {
        val width = maxWidth.value
        val height = maxHeight.value
        // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeTop = insets.getTop(density) / density.density
        val safeBottom = insets.getBottom(density) / density.density

        val heroWidth = min(width, 560f)
        val heroHeight = if (height < 740f || fontScale > 1.3f) 260f else 370f

        CipherSeamBlurViewport(
            enabled = true,
            overflowing = { scroll.maxValue > 4f * density.density },
            backgroundColor = ScreenColor,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(top = safeTop.dp, bottom = (safeBottom + 90f).dp),
            ) {
                CipherSeamNav(
                    reduced = reduced,
                    onTalk = {
                        openInfo()
                        onLearnMore?.invoke()
                    },
                )
                CipherSeamPanel(
                    session = session,
                    heroWidth = heroWidth,
                    heroHeight = heroHeight,
                    reduced = reduced,
                    draggable = !reduced && normalizedPreview == null,
                    onReplay = replay,
                )
                CipherSeamLogos()
                CipherSeamDomain()
            }
        }

        // The chat button floats over the page, above the bottom inset.
        val shadow = remember { SoftRoundRect(Color(91 / 255f, 51 / 255f, 242 / 255f, 0.8f), sigma = 10f) }
        CipherSeamPressScale(
            label = "Chat with us",
            reduced = reduced,
            onPress = openInfo,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = (safeBottom + 37f).dp),
            surface = Modifier
                .size(44.dp)
                .drawBehind { drawChatShadow(shadow) }
                .clip(CircleShape)
                .background(Color(0xFF5B33F2)),
        ) {
            CipherSeamGlyph(CipherSeamIcon.Chatbox, 22f, Color.White)
        }

        CipherSeamInfoModal(
            visible = session.info,
            maxHeight = height - safeTop - safeBottom - 48f,
            onClose = closeInfo,
        )
    }
}

/**
 * The button's `boxShadow: '0px 8px 20px -6px rgba(91,51,242,0.8)'`: the 44 dp circle pulled in 6 dp
 * on every side (spread -6), moved 8 dp down and blurred by sigma 10 (half the blur radius); only
 * the part outside the circle shows.
 */
private fun DrawScope.drawChatShadow(shadow: SoftRoundRect) {
    val circle = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.minDimension / 2f)))
    }
    clipPath(circle, ClipOp.Difference) {
        scale(density, density, pivot = Offset.Zero) {
            drawIntoCanvas { canvas -> shadow.draw(canvas.nativeCanvas, 6f, 14f, 32f, 32f, 16f) }
        }
    }
}

/** `setManual(false)` after 2000 ms without a touch. */
private const val IDLE_BEFORE_LOOP_MS = 2000L

/** True when the user turned animations off in system settings ("Remove animations"). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
