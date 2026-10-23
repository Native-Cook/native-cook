package com.ngocdevv.nativecook.blocks.text.bakeryflapboard

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

private const val CREAM = 0xFFF4EAD6
private const val COCOA = 0xFF2B1D12
// A pan starts after DRAG_SLOP dp; a touch lifted within TAP_MS that never got that far is a tap.
private const val DRAG_SLOP = 6f
private const val TAP_MS = 450L

private fun condensedFamily(weight: Int): FontFamily {
    val base = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
    val face = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        android.graphics.Typeface.create(base, weight, false)
    } else {
        android.graphics.Typeface.create("sans-serif-condensed", if (weight >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    return FontFamily(ComposeTypeface(face))
}

/**
 * A split-flap menu board in a small bakery: a walnut case with brass edges under a drifting strip
 * of bakery photos. Two lines tell the time in words, four list what is out of the oven; when a line
 * changes, every flap falls through the alphabet to its new letter with the real two-half flip, a
 * column at a time, and lands with a little bounce. Tapping a line flips it to its next item and
 * grows that line into a cream card with the item's photo and price.
 *
 * @param menu One list of items per menu line; tapping a line shows the next item of its list.
 * @param strip Photos (Unsplash ids) in the strip across the top.
 * @param notice The two lines that stand in for the time now and then.
 * @param name The bakery's name over the strip.
 * @param autoPlay Changes lines on a schedule, opens one card and puts everything back, on a loop.
 * @param onOpen Called with a menu item when its card opens.
 */
@Composable
fun BakeryFlapBoard(
    modifier: Modifier = Modifier,
    menu: List<List<BakeItem>> = MENU,
    strip: List<String> = STRIP,
    notice: List<String> = listOf("SECOND BAKE", "AT HALF NINE"),
    name: String = "ROSIER BAKEHOUSE",
    autoPlay: Boolean = false,
    onOpen: ((BakeItem) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val opening by rememberUpdatedState(onOpen)
    val motion = remember(menu, notice) { BakeryFlapBoardMotion(menu, notice, auto) }
    val paints = remember { BakeryFlapBoardPaints() }
    // Bumped once a frame; whatever reads it is drawn or placed again.
    var frame by remember { mutableLongStateOf(0L) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
        motion.reducedMotion = reducedMotion
        motion.onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        motion.onSoft = {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        motion.onOpen = { opening?.invoke(it) }
    }

    LaunchedEffect(motion, auto) { motion.resetLoop(auto) }

    // The card photos are fetched up front, so a card never opens on an empty frame.
    LaunchedEffect(menu, strip) {
        val loader = SingletonImageLoader.get(context)
        menu.flatten().forEach { loader.enqueue(ImageRequest.Builder(context).data(cardPhoto(it)).build()) }
        strip.forEach { loader.enqueue(ImageRequest.Builder(context).data(unsplash(it, 340)).build()) }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016 else min((now - last) / 1e9, 0.064)
                last = now
                if (dt > 0) motion.step(dt)
                frame = now
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(BACKGROUND))
            .semantics { contentDescription = "Split-flap menu board. Tap a line to flip it to the next item and open its card." }
            .pointerInput(motion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (!change.pressed) {
                            if (panning) {
                                change.consume()
                                motion.panEnd(tracker.calculateVelocity().y / density.density)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                motion.tap(change.position.x / density.density, change.position.y / density.density)
                            }
                            released = true
                            continue
                        }
                        if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                            panning = true
                            motion.panStart()
                        }
                        if (panning) {
                            change.consume()
                            motion.panUpdate(dy)
                        }
                    }
                    // A finger lost to the system still lets go.
                    if (panning && !released) motion.panEnd(0f)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        motion.width = width
        motion.height = height
        val layout = remember(width, height, safeTop, safeBottom, motion.rowCount) {
            BoardLayout(width, height, safeTop, safeBottom, motion.rowCount)
        }
        val labels = listOf("THE TIME", "TODAY'S BAKE")
        val wood = remember(width, height) { if (Build.VERSION.SDK_INT >= 33) null else woodBitmap(width, height, 1.5f) }
        val woodShader = remember { if (Build.VERSION.SDK_INT >= 33) BakeryFlapBoardWood() else null }

        // Everything under the letters that never moves.
        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    if (woodShader != null) {
                        woodShader.draw(nc, width, height)
                    } else if (wood != null) {
                        drawImage(wood.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(width.roundToInt(), height.roundToInt()))
                    }
                    drawCase(nc, paints, layout, labels)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (frame < 0L) return@Canvas
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawFlaps(it.nativeCanvas, paints, motion, layout) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawHinges(it.nativeCanvas, paints, layout) }
            }
        }

        Header(name, safeTop)
        PhotoStrip(strip, safeTop) { if (frame < 0L || motion.reducedMotion) 0f else motion.elapsed.toFloat() }
        BasicText(
            "Tap a line for its card",
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (safeBottom + 18f).dp)
                .graphicsLayer { alpha = 0.75f },
            style = TextStyle(color = Color(BRASS), fontFamily = condensedFamily(600), fontSize = 12.sp, letterSpacing = 1.6.sp, textAlign = TextAlign.Center),
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (frame < 0L) 0f else 0.62f * clamp01(motion.cardOpen) }
                .background(Color.Black),
        )
        Card(motion, layout, CardRect(width, height, safeTop, safeBottom)) { frame }
    }
}

@Composable
private fun Header(name: String, top: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (CASE + 4f).dp, end = (CASE + 4f).dp, top = (top + 6f).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            name,
            Modifier.alignByBaseline(),
            style = TextStyle(color = Color(BRASS), fontFamily = condensedFamily(700), fontSize = 14.sp, letterSpacing = 3.4.sp),
        )
        BasicText(
            "OPEN 7 — 4",
            Modifier.alignByBaseline().graphicsLayer { alpha = 0.7f },
            style = TextStyle(color = Color(BRASS), fontFamily = condensedFamily(600), fontSize = 11.sp, letterSpacing = 2.sp),
        )
    }
}

/** The strip drifts one set of photos to the left per loop. */
@Composable
private fun PhotoStrip(strip: List<String>, top: Float, elapsed: () -> Float) {
    val setWidth = strip.size * (TILE_W + TILE_GAP)
    Box(
        Modifier
            .offset(y = (top + 32f).dp)
            .fillMaxWidth()
            .height(TILE_H.dp)
            .clipToBounds(),
    ) {
        Row(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset {
                    val drift = -((elapsed().toDouble() % LOOP) / LOOP * setWidth).toFloat()
                    IntOffset((drift * density).roundToInt(), 0)
                }
                .padding(start = CASE.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
        ) {
            val shape = RoundedCornerShape(10.dp)
            repeat(strip.size * 3) { k ->
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context).data(unsplash(strip[k % strip.size], 340)).crossfade(300).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(TILE_W.dp, TILE_H.dp)
                        .clip(shape)
                        .background(Color(0xFF2C1C12))
                        .border(0.5.dp, Color(230, 190, 120).copy(alpha = 0.35f), shape),
                )
            }
        }
    }
}

/** The item card, grown out of its line. */
@Composable
private fun Card(motion: BakeryFlapBoardMotion, layout: BoardLayout, to: CardRect, frame: () -> Long) {
    val item = motion.cardItem
    Box(
        Modifier
            .layout { measurable, constraints ->
                frame()
                val row = motion.cardRow
                val t = if (motion.reducedMotion) 1f else max(0f, motion.cardOpen)
                val fromX = layout.left - 4f
                val fromY = if (row >= 0) layout.rows[row] - 4f else to.y
                val fromW = layout.w - PAD * 2f + 8f
                val fromH = layout.cellH + 8f
                val w = if (row < 0) to.w else mix(fromW, to.w, t)
                val h = if (row < 0) to.h else mix(fromH, to.h, t)
                val x = if (row < 0) to.x else mix(fromX, to.x, t)
                val y = if (row < 0) to.y else mix(fromY, to.y, t)
                val placeable = measurable.measure(Constraints.fixed(max(0, (w * density).roundToInt()), max(0, (h * density).roundToInt())))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((x * density).roundToInt(), (y * density).roundToInt())
                }
            }
            .graphicsLayer {
                frame()
                val row = motion.cardRow
                val o = max(0f, motion.cardOpen)
                val t = if (motion.reducedMotion) 1f else o
                // The card lifts out of its line as a cream strip and grows; it fades in over the first
                // CARD_FADE of the way so it never sits over the line as a flat block.
                alpha = if (row < 0) 0f else if (motion.reducedMotion) clamp01(o) else clamp01(o / CARD_FADE)
                shape = RoundedCornerShape(mix(5f, 24f, clamp01(t)).dp)
                clip = true
            }
            .background(Color(CREAM)),
    ) {
        // The content stays centred in the growing card, so the card reveals it rather than squeezing it.
        Column(
            Modifier
                .requiredSize(to.w.dp, to.h.dp)
                .graphicsLayer {
                    frame()
                    val shown = clamp01((max(0f, motion.cardOpen) - 0.45f) / 0.4f)
                    alpha = shown
                    translationY = if (motion.reducedMotion) 0f else (1f - shown) * 14f * density
                },
        ) {
            val cocoa = Color(COCOA)
            val photoH = to.w * CARD_PHOTO
            val framed = framePhoto(item, to.w, photoH)
            // Framed on its subject: the photo is sized and placed by hand inside a clipped frame.
            Box(Modifier.size(to.w.dp, photoH.dp).clipToBounds()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(cardPhoto(item)).crossfade(250).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset(framed.left.dp, framed.top.dp)
                        .size(framed.width.dp, framed.height.dp),
                )
            }
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(
                        item.name,
                        Modifier.alignByBaseline().weight(1f, fill = false),
                        style = TextStyle(color = cocoa, fontFamily = FontFamily.Serif, fontSize = 30.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        item.price,
                        Modifier.alignByBaseline(),
                        style = TextStyle(color = Color(0xFFA8722B), fontFamily = condensedFamily(700), fontSize = 28.sp, fontFeatureSettings = "tnum"),
                    )
                }
                BasicText(
                    item.note,
                    style = TextStyle(color = cocoa.copy(alpha = 0.78f), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp),
                    maxLines = 2,
                )
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(cocoa.copy(alpha = 0.25f)),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val meta = TextStyle(color = cocoa.copy(alpha = 0.6f), fontFamily = condensedFamily(600), fontSize = 11.sp, letterSpacing = 1.4.sp)
                    BasicText(if (item.baked == "ALL DAY") "POURED ALL DAY" else "OUT OF THE OVEN ${item.baked}", style = meta)
                    BasicText("DRAG DOWN TO CLOSE", style = meta)
                }
            }
        }
    }
}

/** The walnut drawn by a runtime shader (Android 13+). */
@RequiresApi(33)
private class BakeryFlapBoardWood {
    private val shader = RuntimeShader(WOOD)
    private val paint = Paint()

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float) {
        shader.setFloatUniform("size", width, height)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
