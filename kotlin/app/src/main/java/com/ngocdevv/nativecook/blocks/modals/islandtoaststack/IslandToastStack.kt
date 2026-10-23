package com.ngocdevv.nativecook.blocks.modals.islandtoaststack

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// A dark lock screen: deep ink with a few wide pools of colour, so the frosted cards have something
// to refract. The island stays pure black, like the hardware it sits on.
private val Background = Color(0xFF07080E)
private val WallTop = Color(0xFF07080E)
private val WallBottom = Color(0xFF0C0B13)

/** A pool of light in the wallpaper: centre (share of width, height), radius (share of width), colour, alpha. */
private class Pool(val x: Float, val y: Float, val r: Float, val color: Color, val a: Float)

private val Pools = listOf(
    Pool(0.92f, 0.1f, 1.0f, Color(0xFF3A54C4), 0.55f),
    Pool(0.02f, 0.34f, 0.9f, Color(0xFF7C3A8A), 0.42f),
    Pool(0.82f, 0.62f, 0.85f, Color(0xFF288096), 0.22f),
)
private val Ink = Color.White
// The system's notification material: the wallpaper seen through the card under a grey tint, so a
// card never shows the ones behind it. Cards further back in the closed stack are darker, and the
// ones past the third melt away.
private val Material = Color(128, 128, 138).copy(alpha = 0.4f)
private const val BACK_SHADE = 0.38f // black over the third card back (less over the second)
private val Control = Color(128, 128, 138).copy(alpha = 0.4f) // the header's Show less and clear buttons
private const val ICON = 38f

/** The lock screen's wallpaper: deep ink, a little lighter at the foot, with wide pools of colour. */
private fun DrawScope.drawWallpaper() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(WallTop, WallBottom), startY = 0f, endY = h))
    for (pool in Pools) {
        drawRect(
            Brush.radialGradient(
                0f to pool.color.copy(alpha = pool.a),
                0.45f to pool.color.copy(alpha = pool.a * 0.45f),
                1f to pool.color.copy(alpha = 0f),
                center = Offset(pool.x * w, pool.y * h),
                radius = max(1f, pool.r * w),
            ),
        )
    }
}

private fun smoothstep(t: Float): Float {
    val k = clamp01(t)
    return k * k * (3f - 2f * k)
}
private val Band = Color(235, 235, 245).copy(alpha = 0.62f)
private val Secondary = Color(235, 235, 245).copy(alpha = 0.6f)
private val Orange = Color(0xFFFF9F0A)
private val Blue = Color(0xFF0A84FF)
private val Tabular = "tnum"

private fun avatar(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=144&h=144&q=80"

private object People {
    const val CALLER = "Theo Marsh"
    const val CALLER_INITIALS = "TM"
    val callerPhoto = avatar("photo-1587397845856-e6cf49176c70")
    const val SENDER = "Rafael Ortiz"
    const val SENDER_INITIALS = "RO"
    const val BODY = "Running 5 min late, grab us the corner table?"
    val senderPhoto = avatar("photo-1600603406200-5b2a104684ac")
    const val ALBUM = "Lisbon, spring"
    const val COUNT = 24
    const val TIMER = "Sourdough proof"
    const val PLACE = "Kitchen"
}

private fun label(kind: Kind) = when (kind) {
    Kind.Call -> "Missed call from ${People.CALLER}"
    Kind.Message -> "${People.SENDER}: ${People.BODY}"
    Kind.Upload -> "Uploading ${People.COUNT} photos to ${People.ALBUM}"
    Kind.Timer -> "${People.TIMER} timer"
}

private fun formatTime(seconds: Double): String {
    val s = max(0, ceil(if (seconds.isFinite()) seconds else 0.0).toInt())
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * Live notifications that grow out of the camera's island. Each toast morphs out of the island's own
 * frame on a spring (width a little ahead of height, corners continuous), its content fades in once
 * the shape has settled, then it drops out of the island as a card, joined to it by a gooey neck
 * that thins and snaps, and lands at the front of a stack under it. The stack opens into a list,
 * cards swipe away, pulling the island down opens a big timer, and clearing draws every card back up
 * into the island, which gulps each one. Android has no island, so at rest the pill is hidden just
 * under the status bar and shows only while something grows out of it.
 *
 * @param autoPlay Plays four toasts, opens and trims the stack, pulls the island open and draws
 * everything back in, then starts over. A touch pauses it for a few seconds.
 */
@Composable
fun IslandToastStack(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val engine = remember { IslandEngine() }
    engine.onLight = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.onMedium = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    LaunchedEffect(engine, autoPlay) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                // The first frame has no previous one; Reanimated counts it as 16 ms.
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, 0.1)
                last = now
                engine.step(dt, autoPlay)
            }
        }
    }
    DisposableEffect(engine) { onDispose { engine.dispose() } }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        engine.layout(maxWidth.value, safeTop, reduced)
        Canvas(Modifier.fillMaxSize()) { drawWallpaper() }

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 26.dp, end = 26.dp, bottom = (safeBottom + 28f).dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.Bottom,
        ) {
            BasicText("Tuesday, 14 October", style = TextStyle(color = Ink.copy(alpha = 0.7f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "9:41",
                style = TextStyle(
                    color = Ink,
                    fontSize = 96.sp,
                    lineHeight = 104.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-3).sp,
                    fontFeatureSettings = Tabular,
                ),
            )
            BasicText(
                "Tap or pull down the top · Tap the stack · Swipe a card away",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(color = Ink.copy(alpha = 0.46f), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }

        Scene(engine, reduced)
    }
}

/** Reused every frame: one path and one rectangle per card, plus the island's. */
private class Paths {
    val cards = Kind.entries.associateWith { Path() }
    val necks = Kind.entries.associateWith { Path() }
    val island = Path()
    val rect = CardRect()
}

/** Everything that moves, laid out and drawn again every frame. */
@Composable
private fun Scene(engine: IslandEngine, reduced: Boolean) {
    engine.frame.longValue
    val density = LocalDensity.current
    val d = density.density
    val paths = remember { Paths() }
    // The wallpaper recorded once for its size: a card's material is this layer seen through its shape.
    val wallpaper = rememberGraphicsLayer()
    val wallpaperSize = remember { IntArray(2) }
    val width = engine.width
    val e = engine.expanded.float
    val stack = engine.stack
    // Draw order: the stack back to front, then anything leaving or arriving on top of it.
    val order = stack.reversed() + Kind.entries.filter { it !in stack }
    val iw = engine.islandW.float
    val ih = max(0f, engine.islandH.float)
    val ix = engine.islandX()

    Box(Modifier.fillMaxSize()) {
        if (engine.expandedOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(engine) { detectTapGestures { engine.toggleStack() } }
                    .semantics { contentDescription = "Close the stack" },
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val pw = size.width.toInt()
            val ph = size.height.toInt()
            if (pw > 0 && ph > 0 && (wallpaperSize[0] != pw || wallpaperSize[1] != ph)) {
                wallpaperSize[0] = pw
                wallpaperSize[1] = ph
                wallpaper.record(this, layoutDirection, IntSize(pw, ph)) { drawWallpaper() }
            }
            scale(d, pivot = Offset.Zero) {
                val r = paths.rect
                // A card's body. Leaving the island it is still the island's black, so the neck
                // between them reads as one shape; as it settles into the stack it clears into the
                // notification material, and rising back into the island it darkens to black again.
                // The material is the wallpaper itself drawn through the card's shape under a grey
                // tint (the wallpaper is only soft pools of colour, so blurring it would change
                // nothing), which keeps the cards behind out of sight.
                for (kind in order) {
                    val m = engine.motion.getValue(kind)
                    if (m.vis <= 0f || m.absorb.float > 0.995f) continue
                    engine.cardRect(kind, r)
                    // 0 the island's black … 1 the material; how far back the card sits (0 front … 1
                    // the third card); how much of it is left past that.
                    val clear = clamp01((m.arrive.float - 0.5f) / 0.4f) * (1f - clamp01(m.absorb.float * 2.5f))
                    val depth = clamp01(r.fade / 0.42f)
                    val present = 1f - smoothstep((r.fade - 0.42f) / 0.58f)
                    if (present <= 0.002f) continue
                    val path = paths.cards.getValue(kind)
                    squircle(path, r.x, r.y, r.w, r.h, r.r)
                    val tx = m.tx.float
                    val angle = tx * 0.035f
                    val pivot = Offset(r.x + r.w / 2f, r.y + r.h / 2f)
                    translate(left = tx) {
                        rotate(angle, pivot = pivot) {
                            // The card turns a little as it is swiped; the wallpaper inside it is
                            // turned back so it stays put.
                            if (wallpaperSize[0] > 0) {
                                clipPath(path) {
                                    rotate(-angle, pivot = pivot) {
                                        translate(left = -tx) {
                                            scale(1f / d, pivot = Offset.Zero) { drawLayer(wallpaper) }
                                        }
                                    }
                                }
                            }
                            drawPath(path, Material, alpha = present)
                            val shade = BACK_SHADE * depth * present
                            if (shade > 0.002f) drawPath(path, Color.Black, alpha = shade)
                            // The island's black lies over the material and thins away as the card
                            // clears (mixing the two colours instead would flash a light grey half way).
                            val dark = (1f - clear) * present
                            if (dark > 0.002f) drawPath(path, Color.Black, alpha = dark)
                        }
                    }
                }
                // Android has no island: the pill (and the neck it pulls) shows only while a toast or
                // the big card grows out of it.
                val shown = smoothstep((ih - ISLAND_H - 4f) / 30f)
                if (shown > 0.002f) {
                    for (kind in Kind.entries) {
                        val neck = paths.necks.getValue(kind)
                        if (engine.bridge(kind, neck, r)) drawPath(neck, Color.Black, alpha = shown)
                    }
                    squircle(paths.island, ix - iw / 2f, engine.islandTop, iw, ih, radiusFor(iw, ih))
                    drawPath(paths.island, Color.Black, alpha = shown)
                }
            }
        }

        // The header over the open stack.
        val headerShift = max(0f, ih - ISLAND_H) + if (reduced) 0f else mix(-10f, 0f, e)
        Row(
            Modifier
                .offset(x = ((width - engine.cardW) / 2f + 6f).dp, y = engine.deckTop.dp)
                .width((engine.cardW - 12f).dp)
                .height(34.dp)
                .graphicsLayer {
                    alpha = clamp01(e * 1.6f - 0.6f)
                    translationY = headerShift * d
                }
                .then(if (engine.expandedOpen) Modifier else Modifier.clearAndSetSemantics {}),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Notifications",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .height(30.dp)
                        .background(Control, RoundedCornerShape(15.dp))
                        .then(
                            if (engine.expandedOpen) {
                                Modifier
                                    .pointerInput(engine) { detectTapGestures { engine.toggleStack() } }
                                    .semantics { role = Role.Button }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Show less", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Control, CircleShape)
                        .then(
                            if (engine.expandedOpen) {
                                Modifier
                                    .pointerInput(engine) { detectTapGestures { engine.clearStack() } }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Clear all"
                                    }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Ink, modifier = Modifier.size(13.dp))
                }
            }
        }

        // The band under the closed island, where a pull (or a tap) on the island starts too; the cards lie over it.
        if (!engine.expandedOpen) {
            Box(
                Modifier
                    .offset(x = ((width - engine.toastW) / 2f).dp, y = (engine.islandTop + ISLAND_H).dp)
                    .size(max(0f, engine.toastW).dp, PULL_REACH.dp)
                    .clearAndSetSemantics {}
                    .pointerInput(engine) { bandPull(engine) },
            )
        }

        for (kind in order) {
            key(kind) { CardContent(engine, kind, paths.rect) }
        }

        // The island: its content at the toast's width, clipped by the island's own outline.
        Box(
            Modifier
                .offset(x = (ix - iw / 2f).dp, y = engine.islandTop.dp)
                .size(max(0f, iw).dp, ih.dp)
                .clip(SquircleShape)
                .clearAndSetSemantics {},
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(x = ((iw - engine.toastW) / 2f).dp)
                    .width(engine.toastW.dp)
                    .height(BIG_H.dp),
            ) {
                for (kind in Kind.entries) {
                    val c = engine.motion.getValue(kind).content.float
                    if (c > 0.001f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(TOAST_H.dp)
                                .graphicsLayer {
                                    alpha = c
                                    val s = if (reduced) 1f else mix(0.96f, 1f, c)
                                    scaleX = s
                                    scaleY = s
                                    translationY = if (reduced) 0f else mix(-6f, 0f, c) * d
                                },
                        ) { ToastBody(engine, kind) }
                    }
                }
                if (engine.bigMounted) {
                    val b = engine.bigOpacity.float
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(BIG_H.dp)
                            .graphicsLayer {
                                alpha = b
                                val s = if (reduced) 1f else mix(0.96f, 1f, b)
                                scaleX = s
                                scaleY = s
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            },
                    ) { BigTimer(engine) }
                }
            }
        }

        // Worked out here, where the frame is read: the semantics block alone reads no state, so it
        // would keep its first label.
        val islandLabel = when {
            engine.bigMounted -> "Timer, open"
            engine.presenting != null -> label(engine.presenting ?: Kind.Call)
            else -> "Island. Tap for the next toast, pull down to open the timer"
        }
        // The island's touch area reaches up to the screen's edge and a little past its sides.
        Box(
            Modifier
                .offset(x = (ix - iw / 2f - 24f).dp)
                .size((iw + 48f).dp, (engine.islandTop + ih + 14f).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = islandLabel
                    onClick { engine.tapIsland(); true }
                }
                .pointerInput(engine) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        var pulling = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                if (pulling) engine.pullCancel()
                                return@awaitEachGesture
                            }
                            tracker.addPointerInputChange(change)
                            val dy = (change.position.y - down.position.y) / density.density
                            if (!change.pressed) {
                                if (pulling) {
                                    engine.pullEnd(dy, tracker.calculateVelocity().y / density.density)
                                } else if (change.uptimeMillis - down.uptimeMillis <= 350) {
                                    engine.tapIsland()
                                }
                                return@awaitEachGesture
                            }
                            if (!pulling && abs(dy) >= 8f && engine.canPull) {
                                pulling = true
                                engine.pullStart()
                            }
                            if (pulling) {
                                change.consume()
                                engine.pullUpdate(dy)
                            }
                        }
                    }
                },
        )
    }
}

/**
 * A touch in the band under the closed island: a pull down opens the island like a pull on it, a tap
 * taps the island, a sideways drag is neither.
 */
private suspend fun PointerInputScope.bandPull(engine: IslandEngine) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.resetTracking()
        tracker.addPointerInputChange(down)
        var pulling = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (pulling) engine.pullCancel()
                return@awaitEachGesture
            }
            tracker.addPointerInputChange(change)
            val dx = (change.position.x - down.position.x) / density
            val dy = (change.position.y - down.position.y) / density
            if (!change.pressed) {
                if (pulling) {
                    engine.pullEnd(dy, tracker.calculateVelocity().y / density)
                } else if (abs(dx) < 10f && abs(dy) < 10f && change.uptimeMillis - down.uptimeMillis <= 350) {
                    // The island sits under the system's own touch area at the top edge, so a tap
                    // just under it counts as a tap on it.
                    engine.tapIsland()
                }
                return@awaitEachGesture
            }
            if (!pulling) {
                if (abs(dx) >= 16f) return@awaitEachGesture
                if (dy >= PULL_START && engine.canPull) {
                    pulling = true
                    engine.pullStart()
                }
            }
            if (pulling) {
                change.consume()
                engine.pullUpdate(dy)
            }
        }
    }
}

/** The island's outline as a clip: the same continuous corners the canvas draws. */
private val SquircleShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // The radius rule is in dp; the clip is in pixels.
        val d = density.density
        val path = Path()
        squircle(path, 0f, 0f, size.width, size.height, radiusFor(size.width / d, size.height / d) * d)
        return Outline.Generic(path)
    }
}

/** A card's words and pictures, laid over its shape, and the gestures that move it. */
@Composable
private fun CardContent(engine: IslandEngine, kind: Kind, scratch: CardRect) {
    // Read the frame so this recomposes with the scene: its arguments never change.
    engine.frame.longValue
    val m = engine.motion.getValue(kind)
    if (m.vis <= 0f) return
    val density = LocalDensity.current
    val d = density.density
    val r = engine.cardRect(kind, scratch)
    val x = r.x
    val y = r.y
    val w = r.w
    val h = r.h
    val tx = m.tx.float
    val e = engine.expanded.float
    // A card behind keeps its words hidden until the one in front has slid clear of them, about
    // two thirds of the way open: the cards are solid, so they would show through it.
    val front = mix(1f - clamp01(m.slot.float), 1f, smoothstep((e - 0.62f) / 0.36f))
    val landed = clamp01((m.arrive.float - 0.72f) / 0.28f)
    val leaving = 1f - clamp01(m.absorb.float * 3f)
    val swiped = 1f - min(0.8f, abs(tx) / max(1f, engine.width) * 0.9f)
    val opacity = front * landed * leaving * swiped
    val cardW = engine.cardW
    val interactive = kind in engine.stack && (engine.expandedOpen || engine.stack.firstOrNull() == kind) && opacity > 0.05f

    Box(
        Modifier
            .width(cardW.dp)
            .height(CARD_H.dp)
            // Its place and size first, so the touch area follows the card ...
            .graphicsLayer {
                translationX = (x + w / 2f - cardW / 2f) * d
                translationY = (y + h / 2f - CARD_H / 2f) * d
                val s = w / max(1f, cardW)
                scaleX = s
                scaleY = s
                alpha = opacity
            }
            .then(
                if (!interactive) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = "${label(kind)}. Tap to open or close the stack, swipe sideways to dismiss"
                            onClick { engine.toggleStack(); true }
                        }
                        .pointerInput(kind) {
                            val tracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                tracker.resetTracking()
                                tracker.addPointerInputChange(down)
                                var panning = false
                                var pulling = false
                                var failed = false
                                var dx = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        if (panning) engine.cardPanEnd(kind, dx, 0f)
                                        if (pulling) engine.pullCancel()
                                        return@awaitEachGesture
                                    }
                                    tracker.addPointerInputChange(change)
                                    dx = (change.position.x - down.position.x) / density.density
                                    val dy = (change.position.y - down.position.y) / density.density
                                    if (!change.pressed) {
                                        if (panning) {
                                            engine.cardPanEnd(kind, dx, tracker.calculateVelocity().x / density.density)
                                        } else if (pulling) {
                                            engine.pullEnd(dy, tracker.calculateVelocity().y / density.density)
                                        } else if (!failed && change.uptimeMillis - down.uptimeMillis <= 300) {
                                            engine.toggleStack()
                                        }
                                        return@awaitEachGesture
                                    }
                                    if (!panning && !pulling && !failed) {
                                        if (dy >= 12f && !engine.expandedOpen && engine.stack.firstOrNull() == kind && engine.canPull) {
                                            // Down on the closed stack's front card: the island's pull takes the touch over.
                                            pulling = true
                                            engine.pullStart()
                                        } else if (abs(dy) >= 12f) {
                                            failed = true
                                        } else if (abs(dx) >= 14f) {
                                            panning = true
                                            engine.cardPanStart()
                                        }
                                    }
                                    if (panning) {
                                        change.consume()
                                        engine.cardPanUpdate(kind, dx)
                                    }
                                    if (pulling) {
                                        change.consume()
                                        engine.pullUpdate(dy)
                                    }
                                }
                            }
                        }
                },
            )
            // ... and the sideways throw after it, so a drag never moves the space it is measured in.
            .graphicsLayer {
                translationX = tx * d
                rotationZ = tx * 0.035f
            },
    ) {
        DeckBody(engine, kind)
    }
}

// ── Content ──────────────────────────────────────────────────────────────────────────────────────

private val BandStyle = TextStyle(color = Band, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)
private val TitleStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
private val SubtleStyle = TextStyle(color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

@Composable
private fun Line(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, modifier = modifier, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun BandRow(lead: @Composable () -> Unit, trail: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ISLAND_H.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        lead()
        trail()
    }
}

@Composable
private fun BandLabel(text: String, icon: ImageVector? = null, tint: Color = Band) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Line(text, BandStyle)
    }
}

private fun uploadShare(engine: IslandEngine): Float {
    val start = engine.uploadStart ?: return 0f
    return ((engine.now - start) / UPLOAD_TIME).toFloat().coerceIn(0f, 1f)
}

private fun timerLeft(engine: IslandEngine): Double {
    val start = engine.timerStart ?: return TIMER_SECONDS
    return TIMER_SECONDS - max(0.0, engine.now - start)
}

@Composable
private fun Percent(engine: IslandEngine) {
    engine.frame.longValue
    val p = uploadShare(engine)
    Line(
        if (p >= 1f) "Done" else "${(p * 100).toInt()}%",
        TextStyle(color = Blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular),
    )
}

@Composable
private fun Digits(engine: IslandEngine, size: Float) {
    engine.frame.longValue
    Line(
        formatTime(timerLeft(engine)),
        TextStyle(
            color = Orange,
            fontSize = size.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (size > 30f) (-0.5).sp else 0.sp,
            fontFeatureSettings = Tabular,
        ),
    )
}

@Composable
private fun Round(icon: ImageVector, color: Color) {
    Box(Modifier.size(46.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A toast as the island shows it: a top band either side of the camera, and a row under it. */
@Composable
private fun ToastBody(engine: IslandEngine, kind: Kind) {
    engine.frame.longValue
    Column(Modifier.fillMaxSize()) {
        when (kind) {
            Kind.Call -> BandRow({ BandLabel("Incoming call") }, { BandLabel("mobile") })
            Kind.Message -> BandRow({ BandLabel("Message", Icons.AutoMirrored.Filled.Chat, Color(0xFF30D158)) }, { BandLabel("now") })
            Kind.Upload -> BandRow(
                { BandLabel("Uploading") },
                { BandLabel("${(uploadShare(engine) * People.COUNT).toInt()} of ${People.COUNT}") },
            )
            Kind.Timer -> BandRow({ BandLabel("Timer", Icons.Filled.Timer, Orange) }, { BandLabel(People.PLACE) })
        }
        Row(
            Modifier.fillMaxWidth().height((TOAST_H - ISLAND_H - 10f).dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (kind) {
                Kind.Call -> {
                    Avatar(People.callerPhoto, People.CALLER_INITIALS, 46f)
                    Line(People.CALLER, TitleStyle, Modifier.weight(1f))
                    Round(Icons.Filled.CallEnd, Color(0xFFFF3B30))
                    Round(Icons.Filled.Call, Color(0xFF30D158))
                }
                Kind.Message -> {
                    Avatar(People.senderPhoto, People.SENDER_INITIALS, 46f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Line(People.SENDER, TitleStyle)
                        Line(People.BODY, TextStyle(color = Color(235, 235, 245).copy(alpha = 0.82f), fontSize = 14.sp, fontWeight = FontWeight.Normal))
                    }
                }
                Kind.Upload -> {
                    UploadRing(engine.upload.float, 40f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Line(People.ALBUM, TitleStyle)
                        Line("${People.COUNT} photos · Shared album", SubtleStyle)
                    }
                    Percent(engine)
                }
                Kind.Timer -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Line(People.TIMER, TitleStyle)
                        Line("Then shape and bake", SubtleStyle)
                    }
                    Digits(engine, 36f)
                }
            }
        }
    }
}

/** A shape with continuous corners of a fixed radius (dp), as the system rounds its icons. */
private class ContinuousShape(private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        squircle(path, 0f, 0f, size.width, size.height, radius * density.density)
        return Outline.Generic(path)
    }
}

private val AppIconShape = ContinuousShape(ICON * 0.225f)
private val BadgeShape = ContinuousShape(4f)

/** An app's icon as the system draws it: a white symbol on a squircle of the app's colour. */
@Composable
private fun AppIcon(color: Color, icon: ImageVector, tint: Color = Color.White) {
    Box(Modifier.size(ICON.dp).background(color, AppIconShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/** A person's picture with the app's icon tucked into its corner, as the system shows a call or a message. */
@Composable
private fun ContactIcon(photo: String, initials: String, color: Color, icon: ImageVector) {
    Box(Modifier.size(ICON.dp)) {
        Avatar(photo, initials, ICON)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(17.dp)
                .background(color, BadgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

private val NoteTitleStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
private val NoteTimeStyle = TextStyle(color = Secondary, fontSize = 13.sp)
private val NoteBodyStyle = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
private val NoteDigits = SpanStyle(color = Orange, fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular)

/** A notification's words: the title with the time beside it, and a line or two under it. */
@Composable
private fun Note(icon: @Composable () -> Unit, title: String, body: AnnotatedString, lines: Int = 1) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Line(title, NoteTitleStyle, Modifier.weight(1f).alignByBaseline())
                BasicText("now", modifier = Modifier.alignByBaseline(), style = NoteTimeStyle)
            }
            BasicText(body, style = NoteBodyStyle, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A toast as it rests in the stack: one of the system's notifications. */
@Composable
private fun DeckBody(engine: IslandEngine, kind: Kind) {
    engine.frame.longValue
    val green = Color(0xFF34C759)
    when (kind) {
        Kind.Call -> Note(
            icon = { ContactIcon(People.callerPhoto, People.CALLER_INITIALS, green, Icons.Filled.Call) },
            title = People.CALLER,
            body = AnnotatedString("Missed call · mobile"),
        )
        Kind.Message -> Note(
            icon = { ContactIcon(People.senderPhoto, People.SENDER_INITIALS, green, Icons.AutoMirrored.Filled.Chat) },
            title = People.SENDER,
            body = AnnotatedString(People.BODY),
            lines = 2,
        )
        Kind.Upload -> {
            val p = uploadShare(engine)
            Note(
                icon = { AppIcon(Blue, Icons.Filled.CloudUpload) },
                title = People.ALBUM,
                body = AnnotatedString(
                    if (p >= 1f) "${People.COUNT} photos uploaded" else "Uploading ${(p * People.COUNT).toInt()} of ${People.COUNT} photos",
                ),
            )
        }
        Kind.Timer -> Note(
            icon = { AppIcon(Color(0xFF1C1C1E), Icons.Filled.Timer, Orange) },
            title = People.TIMER,
            body = buildAnnotatedString {
                withStyle(NoteDigits) { append(formatTime(timerLeft(engine))) }
                append(" left · ${People.PLACE}")
            },
        )
    }
}

/** The island pulled open: the kitchen timer as a big live card. */
@Composable
private fun BigTimer(engine: IslandEngine) {
    engine.frame.longValue
    val left = timerLeft(engine)
    val share = (left / TIMER_SECONDS).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize()) {
        // The band beside the camera only has room for a short word either side of it.
        BandRow({ BandLabel("Timer", Icons.Filled.Timer, Orange) }, { BandLabel(People.PLACE) })
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 20.dp)) {
            Line(
                formatTime(left),
                TextStyle(
                    color = Orange,
                    fontSize = 72.sp,
                    lineHeight = 84.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.5).sp,
                    fontFeatureSettings = Tabular,
                ),
            )
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Orange.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(Orange, RoundedCornerShape(2.5.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Pause", Color.White.copy(alpha = 0.14f), Color.White, Modifier.weight(1f))
                BigButton("+5 min", Orange, Color(0xFF1A1000), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BigButton(text: String, background: Color, ink: Color, modifier: Modifier) {
    Box(modifier.height(44.dp).background(background, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun Avatar(address: String, initials: String, size: Float) {
    val context = LocalContext.current
    val request = remember(address) { ImageRequest.Builder(context).data(address).crossfade(150).build() }
    // A photo that fails to load leaves the initials on their grey disc.
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
        BasicText(initials, style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** The upload's progress, a ring that closes clockwise from the top. */
@Composable
private fun UploadRing(progress: Float, size: Float) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawCircle(Color.White.copy(alpha = 0.16f), radius = (this.size.width - stroke) / 2f, style = Stroke(stroke))
            val p = clamp01(progress)
            if (p > 0f) drawArc(Blue, -90f, 359.9f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Blue, modifier = Modifier.size((size * 0.42f).dp))
    }
}
