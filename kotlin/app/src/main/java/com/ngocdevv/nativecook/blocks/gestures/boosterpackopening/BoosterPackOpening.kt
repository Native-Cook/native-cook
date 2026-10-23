package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val ASSETS = "boosterpackopening"
private val CARD_FILES = listOf("bulbasaur", "squirtle", "snorlax", "wigglytuff-ex", "pikachu-ex")

private const val TEXT_NEXT_LEFT = "Reveal next card to the left"
private const val TEXT_NEXT = "Reveal next card"
private const val TEXT_ALL_REVEALED = "Every card revealed"
private const val TEXT_REPLAY = "Open another pack"

/** A tap is a touch that lifts within this time without moving. */
private const val TAP_MAX_MS = 500L

/** The replay button arrives 420 ms after the last card's turn, rising 25 dp as it fades in. */
private const val FOOTER_FADE_MS = 420.0
private const val FOOTER_RISE = 25f

private val SKY =
    GradientBrush.verticalGradient(
        0.08f to Color(0xFFAAC2E5),
        0.33f to Color(0xFFCADBF8),
        0.69f to Color(0xFFE7FFFF),
        0.93f to Color(0xFFE3F9FD),
    )

/**
 * A sealed foil booster pack that the finger tears open. A swipe across the top of the pack cuts
 * along the finger's own path while the strip above it folds and curls; when the cut reaches the
 * far edge there is a flash, the strip flies off, the mouth opens and the first card rises. Five
 * cards follow one by one: a slow drag tilts the stack, a tap or a flick reveals the next card,
 * and the two rarest turn through their back with a foil sweep. "Open another pack" starts over.
 * Reduce Motion replaces the travel with fades.
 *
 * The block fills the space it is given, edge to edge, and draws its own sky behind the pack.
 *
 * @param autoPlay plays the block by itself in a 15-second loop, silently, until it is touched.
 * @param muted keeps the block silent: no ambience, tearing, opening or card sounds.
 */
@Composable
fun BoosterPackOpening(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    muted: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val reducedMotion = rememberBoosterPackReducedMotion()
    val images by produceState<PackImages?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadImages(context) }
    }
    val silent = muted || autoPlay
    val audio = remember(silent) { if (silent) null else PackAudio(context.applicationContext, PACK_CARD_RARITIES) }
    DisposableEffect(audio) { onDispose { audio?.dispose() } }
    val feedback = remember(view) { ViewFeedback(view) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds().background(SKY)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val loaded = images
        val scene =
            remember(loaded, width, height, audio, feedback) {
                if (loaded == null || width <= 0 || height <= 0) null else PackScene(width, height, loaded, audio, feedback)
            }
        val script = remember(scene, autoPlay) { if (scene != null && autoPlay) PackAutoPlay(scene) else null }
        if (scene == null) return@BoxWithConstraints
        scene.reducedMotion = reducedMotion

        // The scene is not Compose state: one counter, bumped once per frame, redraws what reads it.
        var frame by remember(scene) { mutableLongStateOf(0L) }
        var frameMs by remember(scene) { mutableStateOf(0.0) }
        var labelIndex by remember(scene) { mutableIntStateOf(0) }
        var interactive by remember(scene) { mutableStateOf(false) }
        var lastSince by remember(scene) { mutableStateOf(Double.NaN) }

        // Sounds and the idle loops follow the app: they rest while it is in the background.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, scene, audio, script) {
            fun apply(resumed: Boolean) {
                scene.focused = resumed
                audio?.setEnabled(resumed)
                if (resumed) script?.restart()
            }
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) apply(true)
                    if (event == Lifecycle.Event.ON_PAUSE) apply(false)
                }
            val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            scene.focused = resumed
            audio?.setEnabled(resumed)
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                audio?.setEnabled(false)
            }
        }

        LaunchedEffect(scene, script) {
            while (true) {
                withFrameNanos { nanos ->
                    val now = nanos / 1_000_000.0
                    scene.frame(now) { script?.frame(now) }
                    frameMs = now
                    frame += 1
                    val index = scene.cards.index
                    if (index != labelIndex) {
                        labelIndex = index
                        lastSince = if (index == CARD_COUNT - 1) now else Double.NaN
                    }
                    val accepts = scene.interactive
                    if (accepts != interactive) interactive = accepts
                }
            }
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(scene, script) {
                        val slop = 1.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val start = down.position
                            var last = start
                            var activated = false
                            var success = false
                            var upTime = down.uptimeMillis
                            script?.touchDown()
                            scene.panBegin(start.x / density.toDouble(), start.y / density.toDouble())
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // A second finger takes the gesture away, as in the source.
                                    if (event.changes.count { it.pressed } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    last = change.position
                                    upTime = change.uptimeMillis
                                    if (!activated && (last - start).getDistance() >= slop) activated = true
                                    if (!change.pressed) {
                                        success = true
                                        break
                                    }
                                    if (activated) {
                                        scene.panUpdate(
                                            last.x / density.toDouble(),
                                            last.y / density.toDouble(),
                                            (last.x - start.x) / density.toDouble(),
                                            (last.y - start.y) / density.toDouble(),
                                        )
                                    }
                                    change.consume()
                                }
                            } catch (cancellation: CancellationException) {
                                scene.cancelTear()
                                script?.touchUp(scene.clock.now)
                                throw cancellation
                            }
                            val x = last.x / density.toDouble()
                            val y = last.y / density.toDouble()
                            if (activated) {
                                scene.panEnd(
                                    x,
                                    y,
                                    (last.x - start.x) / density.toDouble(),
                                    (last.y - start.y) / density.toDouble(),
                                    tracker.calculateVelocity().x / density.toDouble(),
                                    success,
                                )
                            } else if (success && upTime - down.uptimeMillis <= TAP_MAX_MS) {
                                scene.tap(x, y)
                            } else {
                                scene.cancelTear()
                            }
                            script?.touchUp(scene.clock.now)
                        }
                    },
        ) {
            // Reading the counter redraws the scene on every frame.
            if (frame < 0) return@Canvas
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(density, density)
                scene.draw(native, density)
                native.restore()
            }
        }

        // The stack's controls. They are real buttons, shown once the stack accepts input.
        val last = labelIndex == CARD_COUNT - 1
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (frame < 0) 0f else scene.cards.opacity.toFloat() }
                    .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            if (!last) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(y = (height / 2 - 24).dp)
                            .padding(horizontal = 3.dp)
                            .graphicsLayer {
                                val hidden = frame < 0 || scene.cards.dragging || scene.cards.busy
                                alpha = if (hidden) 0f else 0.65f
                            },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ArrowButton("‹", TEXT_NEXT_LEFT, interactive) { scene.cards.advance(-1) }
                    ArrowButton("›", TEXT_NEXT, interactive) { scene.cards.advance(1) }
                }
            } else {
                // The ending waits for the last card's reveal instead of appearing under it.
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(y = (height * 0.835).dp)
                            .graphicsLayer {
                                val delay = if (reducedMotion) 0.0 else RARE_INTERACTION_MS
                                val since = if (frame < 0 || lastSince.isNaN()) 0.0 else frameMs - lastSince - delay
                                val t = if (reducedMotion) 1.0 else clamp(since / FOOTER_FADE_MS, 0.0, 1.0)
                                val eased = if (t < 0.5) 2 * t * t else 1 - (2 - 2 * t) * (2 - 2 * t) / 2
                                alpha = eased.toFloat()
                                translationY = (1 - eased).toFloat() * FOOTER_RISE * density
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BasicText(
                        text = TEXT_ALL_REVEALED,
                        style = TextStyle(color = Color(0xFF7D8798), fontSize = 12.sp, letterSpacing = 0.2.sp),
                    )
                    ReplayButton(interactive) { scene.reset() }
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(width = 44.dp, height = 48.dp)
                .then(
                    if (enabled) {
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onClick,
                            ).semantics { contentDescription = label }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = glyph,
            style = TextStyle(color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Light),
        )
    }
}

@Composable
private fun ReplayButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier =
            Modifier
                .graphicsLayer { alpha = if (pressed) 0.65f else 1f }
                .defaultMinSize(minHeight = 46.dp)
                .background(Color(0xAEFFFFFF), shape)
                .border(1.dp, Color(0xD9FFFFFF), shape)
                .then(
                    if (enabled) {
                        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = TEXT_REPLAY,
            style = TextStyle(color = Color(0xFF46576E), fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

private fun loadImages(context: Context): PackImages {
    fun decode(name: String) =
        context.assets.open("$ASSETS/$name.webp").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "$name.webp could not be decoded" }
        }
    return PackImages(
        pack = decode("booster-pack"),
        cards = CARD_FILES.map { decode("booster-card-$it") },
        back = decode("booster-card-back"),
    )
}

/** Haptics through the view: a click at the cut, a tick per card, a confirmation for a rare face. */
private class ViewFeedback(
    private val view: View,
) : PackFeedback {
    override fun cutHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun cardHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun rareFaceHaptic() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

/**
 * Reduce Motion as Android exposes it: "Remove animations" sets the transition animation scale
 * to zero. The value is observed, so switching it while the block is open takes effect at once.
 */
@Composable
private fun rememberBoosterPackReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver

    fun read() =
        Settings.Global.getString(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE)?.replace(',', '.')?.toFloatOrNull() == 0f
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = read()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
