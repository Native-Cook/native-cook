package com.ngocdevv.nativecook.blocks.media.scattercollagehero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private val Ink = Color(0xFF1D1D1B)
private val Muted = Color(0xFF8D8B85)
private val Paper = Color(0xFFECEBE6)

// The hero's bottom stack, from the bottom edge up: thumbnails, the Start row, the copy.
private const val THUMB_H = 84f
private const val THUMB_GAP = 10f
private const val SIDE = 22f
private const val ROW_H = 48f
private fun stackBottom(bottom: Float) = bottom + 12f
private fun rowBottom(bottom: Float) = stackBottom(bottom) + THUMB_H + 24f
private fun copyBottom(bottom: Float) = rowBottom(bottom) + ROW_H + 22f

// A tap is a touch that never travelled this far; past it the hero pans.
private const val PAN_SLOP = 6f

/**
 * A wellness library as an airy collage: photos of different sizes float on a white page, flying
 * slowly towards the viewer at different depths, the small far ones barely moving and the near
 * ones sliding out past the edges. Tapping one flies it out of the collage into a full-screen hero
 * with a serif title, a Start button and a strip of thumbnails. Swiping the hero sideways pushes in
 * the next one; dragging it down closes it back into its slot.
 *
 * @param sessions The practices in the collage, in the order they are born. Twenty to thirty read best.
 * @param autoPlay Plays the loop by itself: the collage drifts, one photo opens, the hero moves to the next, closes.
 * @param background The page behind the collage.
 * @param accent The one accent: the active chip and the Start button.
 * @param onStart Called when Start is pressed on a hero.
 */
@Composable
fun ScatterCollageHero(
    modifier: Modifier = Modifier,
    sessions: List<CollageSession> = CollageSessions,
    autoPlay: Boolean = false,
    background: Color = Color(0xFFFAFAF7),
    accent: Color = Color(0xFFCDEEC3),
    onStart: ((CollageSession) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val starting by rememberUpdatedState(onStart)
    val motion = remember(sessions) { CollageMotion(sessions) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        motion.top = safeTop
        motion.bottom = safeBottom
        motion.reduced = reducedMotion
        motion.onStart = { starting?.invoke(it) }
        motion.haptic = {
            view.performHapticFeedback(
                when (it) {
                    CollageHaptic.Open -> HapticFeedbackConstants.CONTEXT_CLICK
                    CollageHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    CollageHaptic.Start -> HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
        }
        // The full-size photos of the heroes that just opened, the tapped one first.
        motion.sharpen = { list ->
            for (index in list) {
                if (motion.high[index] != null) continue
                scope.launch {
                    val image = loadCollagePhoto(context, collagePhotoUrl(sessions[index].photo, HERO_PX)) ?: return@launch
                    motion.sharpArrived(index, image, list)
                }
            }
        }
    }

    LaunchedEffect(motion, auto) { motion.restartLoop(auto) }

    // The collage draws its photos as they arrive, each fading in.
    LaunchedEffect(motion, context) {
        sessions.forEachIndexed { i, s ->
            launch {
                val image = loadCollagePhoto(context, collagePhotoUrl(s.photo, TILE_PX)) ?: return@launch
                motion.low[i] = Loaded(image, motion.elapsed)
            }
        }
    }

    LaunchedEffect(motion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.064f)
                last = now
                motion.step(dt)
            }
        }
    }

    val paints = remember { CollagePaints() }
    val hero = motion.hero

    BoxWithConstraints(modifier.fillMaxSize().background(background).clipToBounds()) {
        motion.width = maxWidth.value
        motion.height = maxHeight.value

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Practice library: a collage of photos. Tap a photo to open it." }
                .pointerInput(motion) {
                    detectTapGestures { motion.tap(it.x / density.density, it.y / density.density) }
                },
        ) {
            motion.elapsed
            inDp { drawCollage(it, motion, paints) }
        }

        CollageChrome(motion, background, accent, interactive = hero == null)

        Canvas(Modifier.fillMaxSize()) {
            motion.elapsed
            inDp { drawHero(it, motion, paints) }
        }

        if (hero != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(motion) {
                        val tracker = VelocityTracker()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            tracker.resetTracking()
                            tracker.addPointerInputChange(down)
                            var panning = false
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPointerInputChange(change)
                                    val dx = (change.position.x - down.position.x) / density.density
                                    val dy = (change.position.y - down.position.y) / density.density
                                    if (!change.pressed) {
                                        if (panning) {
                                            released = true
                                            change.consume()
                                            val v = tracker.calculateVelocity()
                                            motion.panEnd(dy, v.x / density.density, v.y / density.density)
                                        }
                                        break
                                    }
                                    if (!panning && sqrt(dx * dx + dy * dy) >= PAN_SLOP) {
                                        if (!motion.panStart()) break
                                        panning = true
                                    }
                                    if (panning) {
                                        change.consume()
                                        motion.panUpdate(dx, dy)
                                    }
                                }
                            } finally {
                                // A finger lost to the system (or a cancelled gesture) still lets go of the hero.
                                if (panning && !released && motion.axis != 0) motion.panEnd(0f, 0f, 0f)
                            }
                        }
                    },
            ) {
                hero.forEachIndexed { k, index ->
                    androidx.compose.runtime.key(index) { CollageCopy(motion, k, sessions[index]) }
                }
                CollageControls(motion, hero, accent)
            }
        }
    }
}

private suspend fun loadCollagePhoto(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        // A software bitmap can carry mipmaps, which keep the small, far tiles from shimmering.
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap().also { it.setHasMipMap(true) }
}

/** Runs [block] on the native canvas in dp units, like the Skia canvas of the React Native version. */
private inline fun DrawScope.inDp(crossinline block: (android.graphics.Canvas) -> Unit) {
    scale(density, density, pivot = Offset.Zero) {
        drawIntoCanvas { block(it.nativeCanvas) }
    }
}

private class CollagePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val veil = Paint(Paint.ANTI_ALIAS_FLAG)
    val box = RectF()
    val dest = RectF()
    val src = Rect()
    val clip = android.graphics.Path()
    val hero = FloatArray(5)
    val order = ArrayList<Int>()
    val veilColors = intArrayOf(
        android.graphics.Color.argb(107, 12, 12, 10),
        android.graphics.Color.argb(0, 12, 12, 10),
        android.graphics.Color.argb(31, 12, 12, 10),
        android.graphics.Color.argb(219, 12, 12, 10),
    )
    val veilStops = floatArrayOf(0f, 0.2f, 0.42f, 1f)
}

private fun drawCollage(canvas: android.graphics.Canvas, m: CollageMotion, p: CollagePaints) {
    if (m.width == 0f) return
    val o = clamp01(m.open.value)
    // The hero's own tile is drawn by the hero; the rest fade out under it, and back in a little
    // while it is dragged down.
    val hidden = if (m.heroOn && m.ids.isNotEmpty()) m.ids[m.front] else -1
    val peek = clamp01(m.dragY.value / (m.height * PEEK_SPAN))
    val others = 1f - o * (1f - PEEK * peek)
    val geos = Array(m.count) { m.geo(it) }
    // Far tiles first, so the near ones pass over them.
    p.order.clear()
    for (i in 0 until m.count) p.order.add(i)
    p.order.sortBy { geos[it].u }
    for (i in p.order) {
        if (i == hidden) continue
        val g = geos[i]
        val alpha = g.alpha * m.weight(i) * others
        if (alpha < 0.004f) continue
        if (g.x > m.width || g.y > m.height || g.x + g.w < 0f || g.y + g.h < 0f) continue
        drawPhoto(canvas, p, m, i, false, g.x, g.y, g.w, g.h, g.r, 0f, alpha, 0f)
    }
}

// In flight (opening, closing or dragged down) the hero is one photo between its slot and the
// screen; open, it is a row of screens paged sideways.
private fun drawHero(canvas: android.graphics.Canvas, m: CollageMotion, p: CollagePaints) {
    if (m.width == 0f || !m.heroOn || m.ids.isEmpty()) return
    val list = m.ids
    val page = m.page.value
    val o = m.open.value
    val dx = m.dragX.value
    val dy = m.dragY.value
    val shade = reveal(o, 0) * (1f - clamp01(dy / 160f))
    val flying = clamp01(o) < 0.999f || m.closing || abs(dx) > 0.5f || abs(dy) > 0.5f
    if (flying) {
        val index = list[m.front]
        if (m.reduced) {
            drawPhoto(canvas, p, m, index, true, 0f, 0f, m.width, m.height, 0f, 0f, clamp01(o), shade)
            return
        }
        m.heroRect(m.geo(index), p.hero)
        val r = p.hero
        drawPhoto(canvas, p, m, index, true, r[0], r[1], r[2], r[3], r[4], 0f, 1f, shade)
        return
    }
    val k0 = floor(page).toInt()
    for (k in k0..k0 + 1) {
        if (k < 0 || k >= list.size) continue
        val d = k - page
        if (abs(d) >= 1f) continue
        if (m.reduced) {
            drawPhoto(canvas, p, m, list[k], true, 0f, 0f, m.width, m.height, 0f, 0f, 1f - abs(d), shade)
        } else {
            drawPhoto(canvas, p, m, list[k], true, d * m.width, 0f, m.width, m.height, 0f, -d * m.width * PARALLAX, 1f, shade)
        }
    }
}

/**
 * A photo in a rounded box, cropped to cover it: paper until the small copy arrives, then the
 * small copy, then the full-size one fading over it. `shift` slides the photo inside its box (the
 * paging parallax); `shade` darkens the top and the bottom for the hero's copy.
 */
private fun drawPhoto(
    canvas: android.graphics.Canvas,
    p: CollagePaints,
    m: CollageMotion,
    index: Int,
    sharp: Boolean,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    r: Float,
    shift: Float,
    alpha: Float,
    shade: Float,
) {
    val now = m.elapsed
    val low = m.low[index]
    val high = if (sharp) m.high[index] else null
    val lowIn = if (low != null) clamp01((now - low.at) / PHOTO_FADE) else 0f
    val highIn = if (high != null) clamp01((now - high.at) / SHARP_FADE) else 0f
    p.box.set(x, y, x + w, y + h)
    canvas.save()
    p.clip.reset()
    p.clip.addRoundRect(p.box, r, r, android.graphics.Path.Direction.CW)
    canvas.clipPath(p.clip)
    if (lowIn < 1f && highIn < 1f) {
        p.fill.color = Paper.toArgb()
        p.fill.alpha = collageRound(clamp01(alpha) * 255)
        canvas.drawRect(p.box, p.fill)
    }
    p.dest.set(x + shift, y, x + shift + w, y + h)
    if (low != null && lowIn > 0f && highIn < 1f) {
        p.art.alpha = collageRound(clamp01(alpha * lowIn) * 255)
        cover(low.image, w, h, p.src)
        canvas.drawBitmap(low.image, p.src, p.dest, p.art)
    }
    if (high != null && highIn > 0f) {
        p.art.alpha = collageRound(clamp01(alpha * highIn) * 255)
        cover(high.image, w, h, p.src)
        canvas.drawBitmap(high.image, p.src, p.dest, p.art)
    }
    if (shade > 0.004f) {
        p.veil.shader = LinearGradient(0f, y, 0f, y + h, p.veilColors, p.veilStops, Shader.TileMode.CLAMP)
        p.veil.alpha = collageRound(clamp01(alpha * shade) * 255)
        canvas.drawRect(p.box, p.veil)
    }
    canvas.restore()
}

/** The part of a photo that covers a w × h box, centred. */
private fun cover(image: Bitmap, w: Float, h: Float, out: Rect) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    // A box (or photo) of no size would make the crop NaN, and roundToInt throws on it.
    if (w <= 0f || h <= 0f || iw <= 0f || ih <= 0f) {
        out.set(0, 0, image.width, image.height)
        return
    }
    val scale = max(w / iw, h / ih)
    val sw = w / scale
    val sh = h / scale
    val left = (iw - sw) / 2f
    val top = (ih - sh) / 2f
    out.set(left.roundToInt(), top.roundToInt(), (left + sw).roundToInt(), (top + sh).roundToInt())
}

// MARK: - Chrome

@Composable
private fun CollageChrome(m: CollageMotion, background: Color, accent: Color, interactive: Boolean) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                m.elapsed
                alpha = 1f - clamp01(m.open.value)
            },
    ) {
        // A soft white fade under the wordmark and over the chips.
        Box(
            Modifier
                .fillMaxWidth()
                .height((m.top + 86f).dp)
                .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)))),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = SIDE.dp, end = SIDE.dp, top = (m.top + 8f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    "Stillhour",
                    style = TextStyle(color = Ink, fontSize = 28.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, letterSpacing = (-0.3).sp),
                )
                BasicText(
                    "${m.count} practices for slow days",
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
                )
            }
            CollageAvatar("AK", accent)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((m.bottom + 120f).dp)
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = (m.bottom + 14f).dp)
                .drawBehind {
                    m.elapsed
                    drawRoundRect(
                        accent,
                        topLeft = Offset(m.pillX.value * density, 0f),
                        size = Size(max(0f, m.pillW.value) * density, 36.dp.toPx()),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollageCategories.forEachIndexed { index, label ->
                val active = m.chip == index
                Box(
                    Modifier
                        .height(36.dp)
                        .onPlaced { m.chipPlaced(index, it.positionInParent().x / density, it.size.width / density) }
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) Color.Transparent else Ink.copy(alpha = 0.05f))
                        .clickable(enabled = interactive, interactionSource = null, indication = null) { m.chooseChip(index) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = TextStyle(color = if (active) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun CollageAvatar(initials: String, tone: Color) {
    Box(Modifier.size(40.dp).background(tone, CircleShape), contentAlignment = Alignment.Center) {
        BasicText(initials, style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp))
    }
}

// MARK: - Hero copy

/** A hero's words: guide, title, line and the time and date beside Start, sliding with its page. */
@Composable
private fun CollageCopy(m: CollageMotion, index: Int, session: CollageSession) {
    fun Modifier.line(order: Int) = graphicsLayer {
        m.elapsed
        val d = index - m.page.value
        val shown = reveal(m.open.value, order)
        val nearby = clamp01(1f - abs(d) * 1.6f)
        val drag = 1f - clamp01(m.dragY.value / 140f)
        alpha = shown * nearby * drag
        translationX = if (m.reduced) 0f else (d * m.width * COPY_PARALLAX).dp.toPx()
        translationY = if (m.reduced) 0f else ((1f - shown) * REVEAL_RISE).dp.toPx()
    }
    val white = Color.White
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = SIDE.dp, end = SIDE.dp, bottom = copyBottom(m.bottom).dp),
        ) {
            Row(
                Modifier.padding(bottom = 12.dp).line(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(white.copy(alpha = 0.22f), CircleShape)
                        .border(0.5.dp, white.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        session.guide.split(" ").mapNotNull { it.firstOrNull() }.joinToString(""),
                        style = TextStyle(color = white, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    )
                }
                BasicText(session.guide, style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
            val title = TextStyle(color = white, fontSize = 56.sp, lineHeight = 58.sp, fontFamily = FontFamily.Serif, letterSpacing = (-0.5).sp)
            BasicText(session.title, modifier = Modifier.line(1), style = title.copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
            BasicText(session.titleItalic, modifier = Modifier.line(2), style = title.copy(fontStyle = FontStyle.Italic), maxLines = 1, softWrap = false)
            BasicText(
                CollageBlurbs[session.category].orEmpty(),
                modifier = Modifier.padding(top = 12.dp).widthIn(max = 320.dp).line(3),
                style = TextStyle(color = white.copy(alpha = 0.74f), fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = SIDE.dp, end = SIDE.dp, bottom = rowBottom(m.bottom).dp)
                .fillMaxWidth()
                .height(ROW_H.dp)
                .line(4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val label = TextStyle(color = white.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            val value = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollageIcon(20f, 1.7f, white, CollageIcons.Clock)
                Column {
                    BasicText("Time", style = label)
                    BasicText("${session.minutes} min", style = value)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    BasicText("Added", style = label.copy(textAlign = TextAlign.End))
                    BasicText(session.date, style = value.copy(textAlign = TextAlign.End))
                }
                CollageIcon(20f, 1.7f, white, CollageIcons.Calendar)
            }
        }
    }
}

// MARK: - Hero controls

/** What stays put while the heroes page: close, the avatar, Start and the thumbnail strip. */
@Composable
private fun CollageControls(m: CollageMotion, list: List<Int>, accent: Color) {
    fun Modifier.fade(order: Int) = graphicsLayer {
        m.elapsed
        val shown = reveal(m.open.value, order)
        alpha = shown * (1f - clamp01(m.dragY.value / 140f))
        translationY = ((1f - shown) * REVEAL_RISE).dp.toPx()
    }
    val inner = m.width - SIDE * 2f
    val slot = (inner - THUMB_GAP * 3f) / 4f
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (SIDE - 4f).dp, end = SIDE.dp, top = (m.top + 6f).dp)
                .fade(0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollagePress(onClick = m::close, description = "Close") {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CollageIcon(18f, 2.4f, Color.White, CollageIcons.Chevron)
                }
            }
            Spacer(Modifier.weight(1f))
            CollageAvatar("AK", accent)
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rowBottom(m.bottom).dp)
                .height(ROW_H.dp)
                .fade(4),
            contentAlignment = Alignment.Center,
        ) {
            CollagePress(onClick = m::start, description = "Start") {
                Row(
                    Modifier
                        .height(ROW_H.dp)
                        .background(accent, RoundedCornerShape(ROW_H / 2f))
                        .padding(start = 6.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(36.dp).background(Ink, CircleShape).padding(start = 2.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(12.dp)) {
                            val k = size.width / 24f
                            val play = Path().apply {
                                moveTo(8f * k, 5.2f * k)
                                lineTo(8f * k, 18.8f * k)
                                lineTo(19.6f * k, 12f * k)
                                close()
                            }
                            drawPath(play, accent)
                            drawPath(play, accent, style = Stroke(width = 2f * k, join = StrokeJoin.Round))
                        }
                    }
                    BasicText("Start", style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = SIDE.dp, bottom = stackBottom(m.bottom).dp)
                .width(inner.dp)
                .height((THUMB_H + 8f).dp)
                .clipToBounds()
                .fade(5),
        ) {
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        m.elapsed
                        // The active thumbnail stays in the second slot until the strip runs out.
                        val shift = min(max(0f, (list.size - 4).toFloat()), max(0f, m.page.value - 1f))
                        translationX = (-shift * (slot + THUMB_GAP)).dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(THUMB_GAP.dp),
            ) {
                list.forEachIndexed { k, index ->
                    androidx.compose.runtime.key(index) { CollageThumb(m, k, index, slot) }
                }
            }
        }
    }
}

/** One thumbnail in the strip: the hero in front is lifted, ringed and fully lit. */
@Composable
private fun CollageThumb(m: CollageMotion, k: Int, index: Int, slot: Float) {
    val session = m.sessions[index]
    Canvas(
        Modifier
            .requiredWidth(slot.dp)
            .height(THUMB_H.dp)
            .semantics { contentDescription = "${session.title} ${session.titleItalic}" }
            .graphicsLayer {
                m.elapsed
                val near = clamp01(1f - abs(k - m.page.value))
                alpha = 0.55f + 0.45f * near
                translationY = (-5f * near).dp.toPx()
            }
            .clickable(interactionSource = null, indication = null) { m.pick(k) },
    ) {
        m.elapsed
        val near = clamp01(1f - abs(k - m.page.value))
        val radius = CornerRadius(14.dp.toPx())
        val box = size
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, box.width, box.height, radius)) }
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(clip)
            drawRect(Color.White.copy(alpha = 0.12f))
            val image = m.low[index]?.image
            if (image != null) {
                val src = Rect()
                cover(image, this.size.width, this.size.height, src)
                canvas.nativeCanvas.drawBitmap(image, src, RectF(0f, 0f, this.size.width, this.size.height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            canvas.restore()
        }
        if (near > 0f) {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = near),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                cornerRadius = CornerRadius(radius.x - stroke / 2f),
                style = Stroke(stroke),
            )
        }
    }
}

/** A pressable that dims and shrinks a little while held, like the RN Pressable. */
@Composable
private fun CollagePress(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .graphicsLayer {
                alpha = if (pressed) 0.7f else 1f
                scaleX = if (pressed) 0.96f else 1f
                scaleY = scaleX
            }
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) { content() }
}

private enum class CollageIcons { Clock, Calendar, Chevron }

/** The few line icons, drawn on a 24-unit grid like the SVGs of the React Native version. */
@Composable
private fun CollageIcon(size: Float, width: Float, color: Color, icon: CollageIcons) {
    Canvas(Modifier.size(size.dp)) {
        val k = this.size.width / 24f
        val path = Path()
        when (icon) {
            CollageIcons.Clock -> {
                path.addOval(androidx.compose.ui.geometry.Rect(3f * k, 3f * k, 21f * k, 21f * k))
                path.moveTo(12f * k, 7.5f * k)
                path.lineTo(12f * k, 12f * k)
                path.lineTo(15f * k, 14f * k)
            }
            CollageIcons.Calendar -> {
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(3.5f * k, 6.5f * k, 20.5f * k, 20.5f * k, CornerRadius(1.5f * k)))
                path.moveTo(3.5f * k, 10.5f * k)
                path.lineTo(20.5f * k, 10.5f * k)
                path.moveTo(8f * k, 4f * k)
                path.lineTo(8f * k, 8f * k)
                path.moveTo(16f * k, 4f * k)
                path.lineTo(16f * k, 8f * k)
            }
            CollageIcons.Chevron -> {
                path.moveTo(5f * k, 9f * k)
                path.lineTo(12f * k, 16f * k)
                path.lineTo(19f * k, 9f * k)
            }
        }
        drawPath(path, color, style = Stroke(width = width * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
