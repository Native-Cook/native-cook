package com.ngocdevv.nativecook.blocks.cards.postcardstampcancel

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_R
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.Pose
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TARGET_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TARGET_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_PAD
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.autoPose
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.stillPose
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Postcard Stamp Cancel — a port of postcard-stamp-cancel (React Native) and PostcardStampCancel.swift.
 A travel journal's day page: a cream postcard on an indigo linen desk, a wooden duplex handstamp on
 an ultramarine ink pad. The handstamp is carried to the stamp, presses, and the cancellation prints
 unevenly from an AGSL shader reading the rubber face as a mask; the trip counter rolls 12 → 13, the
 card turns to its photo and back as a fresh card. LOOP = 9 s (PostcardStampCancelMotion.autoPose).
 Touch: drag the handstamp (springs up); let go within 64 dp of the stamp and it presses; tap the
 card to turn it. 2 s after the last touch everything goes home within 0.9 s and the loop restarts.
 Reduced motion: the card cancelled, the tool on its pad, the count at 13; nothing plays.
 */

private val Cream = Color(0xFFF3EBDA)
private val Muted = Color(0x9EF3EBDA)
private val Amber = Color(0xFFEDB262)
private val Backdrop = Color(0xFF14213A)
private const val IDLE_MS = 2000L
private const val SNAP = 64f
private const val DIGIT_H = 22f

private val InOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val OutSoft: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val OutQuad: Easing = CubicBezierEasing(0.5f, 1f, 0.89f, 1f)
private val InOutQuad: Easing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

/** The loop clock and the finger-driven values that take over from it while a hand is involved. */
private class PostcardHand(val reduced: Boolean) {
    var t by mutableFloatStateOf(0f)
    var owned by mutableStateOf(false)
    var returning = false
    var touching = false
    val x = Animatable(PAD_X)
    val y = Animatable(PAD_Y)
    val h = Animatable(0f)
    val squash = Animatable(0f)
    val ink = Animatable(0f)
    val fade = Animatable(1f)
    val flip = Animatable(0f)
    val tilt = Animatable(0f)
    val count = Animatable(0f)
    val all get() = listOf(x, y, h, squash, ink, fade, flip, tilt, count)
    var idleJob: Job? = null
    val jobs = mutableListOf<Job>()

    fun rest(): Pose = if (reduced) stillPose() else autoPose(0f)

    /** Read only from draw and layer lambdas. */
    fun pose(): Pose = when {
        owned -> Pose(x.value, y.value, h.value, squash.value, ink.value, fade.value, flip.value, tilt.value, count.value)
        reduced -> stillPose()
        else -> autoPose(t)
    }
}

/** Starts [block] at once on the caller's frame (so a snap lands before the next draw). */
private fun CoroutineScope.now(block: suspend CoroutineScope.() -> Unit) = launch(start = CoroutineStart.UNDISPATCHED, block = block)

/** A finger lands: the loop stops and the hand's values take over from the current frame. */
private fun PostcardHand.takeOver(scope: CoroutineScope) {
    touching = true
    idleJob?.cancel()
    idleJob = null
    if (owned && !returning) return
    val p = pose()
    jobs.forEach { it.cancel() }
    jobs.clear()
    scope.now {
        x.snapTo(p.x); y.snapTo(p.y); h.snapTo(p.h); squash.snapTo(p.squash); ink.snapTo(p.ink)
        fade.snapTo(p.fade); flip.snapTo(p.flip % 360f); tilt.snapTo(p.tilt); count.snapTo(p.count)
    }
    returning = false
    owned = true
}

/** The finger lifts: 2 s later everything goes home within 0.9 s and the loop restarts at 0. */
private fun PostcardHand.letGo(scope: CoroutineScope) {
    touching = false
    idleJob?.cancel()
    idleJob = scope.launch {
        delay(IDLE_MS)
        returning = true
        val rest = rest()
        val glide = tween<Float>(760, easing = InOut)
        val parts = listOf(
            launch { x.animateTo(rest.x, glide) },
            launch { y.animateTo(rest.y, glide) },
            launch {
                h.animateTo(0.7f, tween(300, easing = InOutQuad))
                h.animateTo(0f, tween(460, easing = InOut))
            },
            launch { squash.animateTo(0f, tween(200, easing = InOutQuad)) },
            launch { fade.animateTo(rest.fade, tween(380, easing = InOutQuad)) },
            launch { flip.animateTo(0f, glide) },
            launch { tilt.animateTo(0f, tween(300, easing = InOutQuad)) },
            launch { count.animateTo(rest.count, glide) },
            launch {
                delay(400)
                ink.animateTo(rest.ink, tween(380, easing = InOutQuad))
            },
        )
        parts.joinAll()
        fade.snapTo(1f)
        returning = false
        t = 0f
        owned = false
    }
}

private fun PostcardHand.track(job: Job) {
    jobs.add(job)
}

/** The press, once the tool is let go over the stamp: drop, squash, ink spreads, lift, the count rolls. */
private fun PostcardHand.press(scope: CoroutineScope, thump: () -> Unit) {
    val s = spring<Float>(dampingRatio = 0.607f, stiffness = 220f)
    track(scope.launch { x.animateTo(TARGET_X, s) })
    track(scope.launch { y.animateTo(TARGET_Y, s) })
    track(
        scope.launch {
            h.animateTo(1.08f, tween(90, easing = OutQuad))
            h.animateTo(0f, tween(190, easing = InOut))
            thump()
            delay(380)
            h.animateTo(0.85f, tween(320, easing = OutSoft))
        },
    )
    track(
        scope.launch {
            delay(280)
            squash.animateTo(1f, tween(170, easing = InOutQuad))
            squash.animateTo(0f, tween(280, easing = InOutQuad))
        },
    )
    if (ink.value < 1f) {
        scope.now { fade.snapTo(1f) }
        track(
            scope.launch {
                delay(280)
                ink.animateTo(1f, tween(450, easing = OutSoft))
            },
        )
        track(
            scope.launch {
                delay(900)
                count.animateTo(1f, tween(350, easing = InOut))
            },
        )
    }
    track(
        scope.launch {
            delay(280)
            tilt.animateTo(-1.6f, tween(70, easing = InOutQuad))
            // Reanimated's damping 6 / stiffness 260 / mass 0.6 as a ratio and a per-mass stiffness.
            tilt.animateTo(0f, spring(dampingRatio = 0.24f, stiffness = 433f))
        },
    )
}

/**
 * A travel journal's postcard whose stamp is cancelled by hand with a wooden duplex handstamp.
 *
 * @param autoPlay lifts the postmark off its pad, cancels the stamp and turns the card, every [LOOP] seconds.
 */
@Composable
fun PostcardStampCancel(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val context = LocalContext.current
    val view = LocalView.current
    val screen = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hand = remember(reduced) { PostcardHand(reduced) }
    var art by remember { mutableStateOf<PostcardStampCancelArtSet?>(null) }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val baked = withContext(Dispatchers.Default) { PostcardStampCancelArt.bake(context.assets, screen.density) }
        art = baked
        appear.animateTo(1f, tween(260, easing = OutSoft))
    }

    // One frame loop drives the loop clock while nothing else owns the screen and the art is ready.
    LaunchedEffect(hand, auto) {
        snapshotFlow { auto && !hand.owned && art != null }.collectLatest { run ->
            if (!run) return@collectLatest
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 1f / 20f)
                    last = now
                    hand.t = (hand.t + dt) % LOOP
                }
            }
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(POSTCARD_INK_AGSL) }.getOrNull() else null
    }
    val images = remember(art) {
        art?.let { a ->
            PostcardImages(
                mask = a.mask.asImageBitmap(), tool = a.tool.asImageBitmap(), near = a.shadowNear.asImageBitmap(),
                far = a.shadowFar.asImageBitmap(), contact = a.shadowContact.asImageBitmap(), desk = a.desk.asImageBitmap(),
                front = a.front.asImageBitmap(), back = a.back.asImageBitmap(), cardShadow = a.cardShadow.asImageBitmap(),
                weave = a.weave.asImageBitmap(),
            )
        }
    }
    val inkBrush = remember(art, shader) {
        val a = art
        if (a != null && shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val mask = BitmapShader(a.mask, android.graphics.Shader.TileMode.DECAL, android.graphics.Shader.TileMode.DECAL)
            mask.setLocalMatrix(Matrix().apply { setScale(screen.density / a.scale, screen.density / a.scale) })
            shader.setInputShader("mask", mask)
            shader.setFloatUniform("size", INK_W, INK_H)
            shader.setFloatUniform("tilt", 0.8f, -0.5f)
            shader.setFloatUniform("seed", 3.7f)
            shader.setFloatUniform("scale", screen.density)
            ShaderBrush(shader)
        } else {
            null
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .clipToBounds(),
    ) {
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / screen.density
        val bottom = insets.getBottom(screen) / screen.density
        val roomW = maxWidth.value
        val roomH = maxHeight.value - top - bottom
        val k = min(min(roomW / DESIGN_W, roomH / DESIGN_H), 1.15f)
        val ox = (roomW - DESIGN_W * k) / 2f
        val oy = top + max(0f, (roomH - DESIGN_H * k) / 2f)

        // The desk: indigo linen with a weave, a warm lamp and a vignette, over the whole screen.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val hh = size.height
                    val base = Brush.linearGradient(
                        0f to Color(0xFF1E3150), 0.5f to Color(0xFF18263F), 1f to Color(0xFF101A2C),
                        start = Offset.Zero, end = Offset(w * 0.4f, hh),
                    )
                    val lamp = Brush.radialGradient(
                        0f to Color(0x24FFD6A0), 0.5f to Color(0x0AFFD6A0), 1f to Color(0x00FFD6A0),
                        center = Offset(w * 0.62f, (oy + 200f * k) * density), radius = w,
                    )
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x99030614),
                        center = Offset(w / 2f, hh * 0.45f), radius = hh * 0.75f,
                    )
                    val weave = images?.let { ShaderBrush(ImageShader(it.weave, TileMode.Repeated, TileMode.Repeated)) }
                    onDrawBehind {
                        drawRect(base)
                        if (weave != null) drawRect(weave, alpha = appear.value)
                        drawRect(lamp)
                        drawRect(vignette)
                    }
                },
        )

        // The design box, scaled to fit between the safe insets.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed((DESIGN_W * density).toInt(), (DESIGN_H * density).toInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place((ox * density).toInt(), (oy * density).toInt()) }
                }
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            // Spare card, ink pad and lid (a stand-in pad until the art is baked).
            Canvas(Modifier.fillMaxSize()) {
                val a = appear.value
                scale(density, density, pivot = Offset.Zero) {
                    if (a < 1f) {
                        drawCircle(Color(0xFF8E97A6), PAD_R, Offset(PAD_X, PAD_Y), alpha = 1f - a)
                        drawCircle(Color(0xFF22306E), PAD_R - 7f, Offset(PAD_X, PAD_Y), alpha = 1f - a)
                    }
                    images?.let { drawBitmap(it.desk, 0f, 0f, DESIGN_W, DESIGN_H, a) }
                }
            }

            PostcardHeader(hand)

            // The card's shadow: it narrows with the turn and drifts down while the card lifts.
            Canvas(
                Modifier
                    .offset(CARD_X.dp + 6.dp, CARD_Y.dp + 10.dp)
                    .size((CARD_W - 12f).dp, (CARD_H - 8f).dp)
                    .graphicsLayer {
                        val p = hand.pose()
                        val lifted = sin(PI.toFloat() * min(180f, p.flip % 180.001f) / 180f)
                        alpha = 1f - 0.4f * lifted
                        translationY = 10f * lifted * density
                        scaleX = max(0.2f, abs(cos(p.flip * PI.toFloat() / 180f)))
                    },
            ) {
                val img = images?.cardShadow ?: return@Canvas
                val m = art?.cardShadowMargin ?: 0f
                scale(density, density, pivot = Offset.Zero) {
                    drawBitmap(img, -m, -m, CARD_W - 12f + m * 2, CARD_H - 8f + m * 2, appear.value)
                }
            }

            // The card: its writing side with the ink, or its photo side, turning about its vertical axis.
            Canvas(
                Modifier
                    .offset(CARD_X.dp, CARD_Y.dp)
                    .size(CARD_W.dp, CARD_H.dp)
                    .graphicsLayer {
                        val p = hand.pose()
                        val lifted = sin(PI.toFloat() * min(180f, p.flip % 180.001f) / 180f)
                        translationY = -10f * lifted * density
                        rotationZ = p.tilt
                        rotationY = p.flip
                        scaleX = 1f + 0.04f * lifted
                        scaleY = 1f + 0.04f * lifted
                        // CSS perspective 1200 dp; the layer's camera distance is in 72-per-inch units.
                        cameraDistance = 1200f * density / 72f
                    },
            ) {
                val p = hand.pose()
                val frontUp = cos(p.flip * PI.toFloat() / 180f) >= 0f
                val im = images
                scale(density, density, pivot = Offset.Zero) {
                    if (im == null || appear.value < 1f) {
                        drawRoundRect(Color(0xFFF4ECDA), cornerRadius = CornerRadius(6f), alpha = 1f - appear.value)
                    }
                    if (im == null) return@scale
                    if (frontUp) {
                        drawBitmap(im.front, 0f, 0f, CARD_W, CARD_H, appear.value)
                    } else {
                        // The photo side is seen from behind the layer, so it is mirrored back to read.
                        scale(-1f, 1f, pivot = Offset(CARD_W / 2f, CARD_H / 2f)) {
                            drawBitmap(im.back, 0f, 0f, CARD_W, CARD_H, appear.value)
                        }
                    }
                }
                if (im != null && frontUp && p.fade > 0.001f && p.ink > 0f) {
                    translate(INK_X * density, INK_Y * density) {
                        val brush = inkBrush
                        if (brush != null && shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            shader.setFloatUniform("press", p.ink)
                            shader.setFloatUniform("fade", p.fade)
                            drawRect(brush, Offset.Zero, Size(INK_W * density, INK_H * density))
                        } else {
                            // Below Android 13: the rubber face tinted in ink, fading in with the press.
                            scale(density, density, pivot = Offset.Zero) {
                                drawBitmap(
                                    im.mask, 0f, 0f, INK_W, INK_H, 0.85f * p.ink * p.fade,
                                    ColorFilter.tint(Color(0xFF1E2A66)),
                                )
                            }
                        }
                    }
                }
            }

            BasicText(
                "Drag the postmark onto the stamp · tap the card to turn it",
                Modifier
                    .offset(24.dp, 362.dp)
                    .width((DESIGN_W - 48f).dp),
                style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )

            PostcardInfo()

            // The touch area (RN's top 100, 580 tall): grab the handstamp, or tap the card.
            Box(
                Modifier
                    .offset(0.dp, 100.dp)
                    .size(DESIGN_W.dp, 580.dp)
                    .semantics { contentDescription = "Postcard and postmark. Drag the postmark onto the stamp to cancel it." }
                    .pointerInput(hand) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val sx = down.position.x / density
                            val sy = down.position.y / density + 100f
                            val p = hand.pose()
                            if (abs(sx - p.x) <= TOOL_W / 2f + 14f && abs(sy - p.y) <= TOOL_H / 2f + 14f) {
                                down.consume()
                                hand.takeOver(scope)
                                val gx = p.x - sx
                                val gy = p.y - sy
                                hand.track(scope.launch { hand.h.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 240f)) })
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val nx = change.position.x / density + gx
                                    val ny = change.position.y / density + 100f + gy
                                    scope.now {
                                        hand.x.snapTo(nx)
                                        hand.y.snapTo(ny)
                                    }
                                }
                                if (hypot(hand.x.value - TARGET_X, hand.y.value - TARGET_Y) < SNAP) {
                                    hand.press(scope) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                                } else {
                                    hand.track(scope.launch { hand.h.animateTo(0f, tween(260, easing = InOut)) })
                                }
                                hand.letGo(scope)
                            } else {
                                var moved = 0f
                                var up = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    moved = max(moved, (change.position - down.position).getDistance() / density)
                                    if (!change.pressed) {
                                        up = true
                                        break
                                    }
                                }
                                val onCard = sx in CARD_X..(CARD_X + CARD_W) && sy in CARD_Y..(CARD_Y + CARD_H)
                                if (up && moved < 10f && onCard) {
                                    hand.takeOver(scope)
                                    val target = if (hand.pose().flip < 90f) 180f else 0f
                                    hand.track(scope.launch { hand.flip.animateTo(target, spring(dampingRatio = 0.73f, stiffness = 120f)) })
                                    hand.letGo(scope)
                                }
                            }
                        }
                    },
            )

            // The handstamp over everything: two shadows that spread as it rises, then the block.
            Canvas(Modifier.fillMaxSize()) {
                val p = hand.pose()
                scale(density, density, pivot = Offset.Zero) { drawTool(p, images, art?.shadowMargin ?: 0f, appear.value) }
            }

            PostcardActions()
        }
    }
}

private class PostcardImages(
    val mask: ImageBitmap,
    val tool: ImageBitmap,
    val near: ImageBitmap,
    val far: ImageBitmap,
    val contact: ImageBitmap,
    val desk: ImageBitmap,
    val front: ImageBitmap,
    val back: ImageBitmap,
    val cardShadow: ImageBitmap,
    val weave: ImageBitmap,
)

/** Draws [img] stretched to the dp rect (x, y, w, h), without rounding the position to whole pixels. */
private fun DrawScope.drawBitmap(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float, filter: ColorFilter? = null) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, pivot = Offset.Zero)
    }) {
        drawImage(img, alpha = alpha.coerceIn(0f, 1f), colorFilter = filter)
    }
}

private fun DrawScope.drawTool(p: Pose, images: PostcardImages?, margin: Float, appear: Float) {
    val lift = p.h
    val scale = (1f + 0.1f * lift) * (1f - 0.025f * p.squash)
    if (images == null || appear < 1f) {
        drawRoundRect(
            Color(0xFF7A4D2D), Offset(p.x - TOOL_W * scale / 2f, p.y - TOOL_H * scale / 2f), Size(TOOL_W * scale, TOOL_H * scale),
            CornerRadius(12f), alpha = 1f - appear,
        )
        if (images == null) return
    }
    // Ambient shadow: further away and softer the higher the tool is (a cross-fade from blur 5 to 19).
    val amb = (0.42f - 0.16f * lift) * appear
    val ax = p.x - TOOL_W * scale / 2f + 6f + 10f * lift
    val ay = p.y - TOOL_H * scale / 2f + 6f + 22f * lift
    val lf = lift.coerceIn(0f, 1f)
    drawBitmap(images.near, ax - margin * scale, ay - margin * scale, (TOOL_W + margin * 2) * scale, (TOOL_H + margin * 2) * scale, amb * (1f - lf))
    drawBitmap(images.far, ax - margin * scale, ay - margin * scale, (TOOL_W + margin * 2) * scale, (TOOL_H + margin * 2) * scale, amb * lf)
    // Contact shadow: tight while it rests, gone once it is up.
    val contact = 0.5f * (1f - min(1f, lift * 3f)) * appear
    drawBitmap(images.contact, p.x - TOOL_W / 2f - margin, p.y - TOOL_H / 2f + 2f - margin, TOOL_W + margin * 2, TOOL_H + margin * 2, contact)
    val w = (TOOL_W + TOOL_PAD * 2) * scale
    val h = (TOOL_H + TOOL_PAD * 2) * scale
    drawBitmap(images.tool, p.x - w / 2f, p.y - h / 2f, w, h, appear)
}

@Composable
private fun PostcardHeader(hand: PostcardHand) {
    Column(
        Modifier
            .offset(24.dp, 8.dp)
            .width((DESIGN_W - 48f).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText("POSTMARKS", style = TextStyle(color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                "Lisbon, day 4",
                style = TextStyle(color = Cream, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
            )
            Row(
                Modifier
                    .height(32.dp)
                    .background(Color(0x14F3EBDA), RoundedCornerShape(16.dp))
                    .border(0.5.dp, Color(0x38F3EBDA), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "Postcards sent this trip" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("SENT", Modifier.padding(end = 6.dp), style = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
                PostcardDigit("1")
                // The rolling digit: a strip 0–4 inside a one-digit window, moved in the layer only.
                Box(
                    Modifier
                        .height(DIGIT_H.dp)
                        .clipToBounds(),
                ) {
                    Column(
                        Modifier
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .graphicsLayer { translationY = -(2f + hand.pose().count) * DIGIT_H * density },
                    ) {
                        for (d in listOf("0", "1", "2", "3", "4")) PostcardDigit(d)
                    }
                }
            }
        }
        BasicText("Tram 28 · Alfama · 24 October", style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun PostcardDigit(d: String) {
    Box(Modifier.height(DIGIT_H.dp), contentAlignment = Alignment.Center) {
        BasicText(
            d,
            style = TextStyle(color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", lineHeight = DIGIT_H.sp),
        )
    }
}

@Composable
private fun PostcardInfo() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .offset(214.dp, 456.dp)
            .width(163.dp)
            .cssBoxShadow(Color(0x59000000), 1.dp, 2.dp, 18.dp)
            .cssBoxShadow(Color(0x4D000000), 12.dp, 28.dp, 18.dp)
            .background(Color(0x12F3EBDA), shape)
            .border(0.5.dp, Color(0x2EF3EBDA), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val line = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
        BasicText("TODAY’S POSTMARK", style = TextStyle(color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
        BasicText(
            "Lisboa · 24 Oct",
            Modifier.padding(top = 2.dp),
            style = TextStyle(color = Cream, fontFamily = FontFamily.Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        BasicText("Ultramarine Nº 4 · 13:40", style = line)
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0x33F3EBDA)),
        )
        BasicText("3 of 5 cards for this trip", style = line)
    }
}

@Composable
private fun PostcardActions() {
    Row(
        Modifier
            .offset(16.dp, 694.dp)
            .width((DESIGN_W - 32f).dp)
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val secondary = remember { MutableInteractionSource() }
        val secondaryDown by secondary.collectIsPressedAsState()
        val shape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .scale(if (secondaryDown) 0.97f else 1f)
                .background(Color(0x0FF3EBDA), shape)
                .border(1.dp, Color(0x4DF3EBDA), shape)
                .clickable(secondary, null) {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Write another", style = TextStyle(color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
        val primary = remember { MutableInteractionSource() }
        val primaryDown by primary.collectIsPressedAsState()
        Box(
            Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .scale(if (primaryDown) 0.97f else 1f)
                .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
                .cssBoxShadow(Color(0x4DD38E3C), 10.dp, 22.dp, 26.dp)
                .background(
                    Brush.verticalGradient(0f to Color(0xFFF6CB86), 0.55f to Amber, 1f to Color(0xFFD38E3C)),
                    shape,
                )
                .drawWithContent {
                    drawContent()
                    drawArcHighlight(26.dp.toPx())
                }
                .clickable(primary, null) {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Send card", style = TextStyle(color = Color(0xFF2A1806), fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** A 1 dp light line along the top inside edge of a pill, fading out down its curved ends. */
private fun DrawScope.drawArcHighlight(r: Float) {
    val y = 1.dp.toPx() / 2f
    drawLine(
        Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color(0x99FFF6DE), 0.88f to Color(0x99FFF6DE), 1f to Color.Transparent),
        Offset(r * 0.5f, y), Offset(size.width - r * 0.5f, y), strokeWidth = 1.dp.toPx(),
    )
}
