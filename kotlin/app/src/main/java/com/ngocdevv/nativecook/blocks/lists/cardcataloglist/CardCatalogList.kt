package com.ngocdevv.nativecook.blocks.lists.cardcataloglist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListArt.DRAWER_BOX_H
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListArt.DRAWER_BOX_Y
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.CARDS
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.CARD_H
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.CARD_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.CARD_X
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_X
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_Y
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_H
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_X
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_Y
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.LOOP
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.PIVOT_Y
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.PT_PER_CARD
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.STATUS_LABEL
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.TARGET
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.autoPosition
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.cardPose
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.clamp01
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.rubberBand
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Card Catalog List — a port of card-catalog-list (React Native) and CardCatalogList.swift.
 A library's card catalogue drawer seen from above: 24 index cards stand on a brass rod, each
 turning about its bottom edge (rotationX with perspective) by how far it is from the reading line;
 thumbed cards fall forward over the drawer's lip and show their backs, the standing ones fan back
 with the thumb's speed. LOOP = 9 s: rest 0–1.4 s, thumb forward to Gaskell (card 15) by 4.6 s with
 a settle, hold, thumb back 5.8–7.6 s, rest. The panel's title, byline, call number and status and
 the card counter roll with the drawer. Touch: drag down to thumb forward, up to go back (rubber
 band at the ends, release with momentum onto a whole card); tap to tip one card. 2 s after the
 last touch the drawer eases back to the first card. Reduced motion: Gaskell at the front, still.
 */

private val TypeFace = FontFamily.Monospace
private val Paper = Color(0xFFF3EBD8)
private val Ink = Color(0xFF2A2730)
private val Cream = Color(0xFFF3E8D4)
private val Muted = Color(0x9EF3E8D4)
private val Amber = Color(0xFFE4B36A)
private const val IDLE = 2f
private const val RETURN_MS = 850f
private const val ROLL_H = 24f
private const val DIGIT_H = 20f
private val LAST = CARDS.size - 1
private const val PERSPECTIVE = 1600f
private val RULES = listOf(86f, 106f, 126f, 146f, 166f)
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private class CatalogDrawer(val rest: Float) {
    var t = 0f
    var owned by mutableStateOf(false)
    var touching = false
    var returning = false
    var idle = 0f
    var s by mutableFloatStateOf(rest)
    var lean by mutableFloatStateOf(0f)
    val held = Animatable(rest)
}

/**
 * A library's card catalogue drawer: thumb through the index cards to find a book.
 *
 * @param autoPlay thumbs through the drawer to Gaskell and back, every [LOOP] seconds.
 */
@Composable
fun CardCatalogList(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val screen = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val rest = if (reduced) TARGET.toFloat() else 0f
    val drawer = remember(reduced) { CatalogDrawer(rest) }
    var art by remember { mutableStateOf<CardCatalogArtSet?>(null) }
    val appear = remember { Animatable(0f) }
    val images = remember(art) {
        art?.let { CatalogImages(it.drawer.asImageBitmap(), it.front.asImageBitmap(), it.grain.asImageBitmap(), it.fine.asImageBitmap()) }
    }

    LaunchedEffect(Unit) {
        art = withContext(Dispatchers.Default) { CardCatalogListArt.bake(screen.density) }
        appear.animateTo(1f, tween(260))
    }

    // One frame loop: the demo's clock, or the finger's value, and the lean that follows the speed.
    LaunchedEffect(drawer, auto) {
        snapshotFlow { (auto && !drawer.owned && art != null) || drawer.owned || drawer.lean > 0.01f }.collectLatest { run ->
            if (!run) return@collectLatest
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 1f / 20f)
                    last = now
                    val before = drawer.s
                    val next: Float
                    if (!drawer.owned) {
                        if (auto) drawer.t = (drawer.t + dt) % LOOP
                        next = if (auto) autoPosition(drawer.t) else drawer.rest
                    } else {
                        next = drawer.held.value
                        if (!drawer.touching && !drawer.returning) {
                            drawer.idle += dt
                            if (drawer.idle >= IDLE) {
                                drawer.returning = true
                                val far = abs(next - drawer.rest)
                                scope.launch {
                                    drawer.held.animateTo(drawer.rest, tween(min(RETURN_MS, 300f + far * 40f).roundToInt(), easing = InOut))
                                    drawer.returning = false
                                    drawer.t = 0f
                                    drawer.owned = false
                                }
                            }
                        }
                    }
                    drawer.s = next
                    // Fast thumbing fans the standing cards back; the lean follows the speed with a short lag.
                    val speed = abs(next - before) / max(dt, 1e-3f)
                    val target = min(speed * 1.4f, 12f)
                    val lean = drawer.lean + (target - drawer.lean) * min(1f, dt * 9f)
                    drawer.lean = if (lean < 0.005f && target == 0f) 0f else lean
                }
            }
        }
    }

    // A light tick each time a new card reaches the front under the finger.
    LaunchedEffect(drawer) {
        var before = Int.MIN_VALUE
        snapshotFlow { drawer.s.roundToInt() }.collect { now ->
            if (before != Int.MIN_VALUE && now != before && drawer.owned) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            before = now
        }
    }

    fun takeOver() {
        drawer.touching = true
        drawer.idle = 0f
        val from = drawer.s
        val fresh = !drawer.owned || drawer.returning
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (fresh) drawer.held.snapTo(from) else drawer.held.stop()
        }
        drawer.returning = false
        drawer.owned = true
    }

    fun letGo() {
        drawer.touching = false
        drawer.idle = 0f
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1C130D))
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

        // The desk: dark wood with its grain, and a vignette.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val base = Brush.verticalGradient(0f to Color(0xFF3A281C), 0.55f to Color(0xFF2B1D14), 1f to Color(0xFF1C130D))
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x8C080402),
                        center = Offset(size.width / 2f, size.height * 0.42f), radius = size.height * 0.78f,
                    )
                    val grain = images?.let { ShaderBrush(ImageShader(it.grain, TileMode.Repeated, TileMode.Repeated)) }
                    val fine = images?.let { ShaderBrush(ImageShader(it.fine, TileMode.Repeated, TileMode.Repeated)) }
                    onDrawBehind {
                        drawRect(base)
                        if (grain != null && fine != null) {
                            drawRect(grain, alpha = 0.8f * appear.value)
                            drawRect(fine, alpha = appear.value)
                        }
                        drawRect(vignette)
                    }
                },
        )

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
            // The drawer's inside, where the cards stand (a flat stand-in until the art is baked).
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) {
                    val a = appear.value
                    if (a < 1f) {
                        drawRoundRect(
                            Color(0xFF1E130C), Offset(DRAWER_X, DRAWER_Y), androidx.compose.ui.geometry.Size(DRAWER_W, PIVOT_Y + 66f - DRAWER_Y),
                            androidx.compose.ui.geometry.CornerRadius(10f), alpha = 1f - a,
                        )
                    }
                    images?.let { drawImageAt(it.drawer, 0f, DRAWER_BOX_Y, DESIGN_W, DRAWER_BOX_H, a) }
                }
            }

            CatalogHeader(drawer)

            // The cards, back to front, so the front card draws over the ones behind it.
            for (i in CARDS.indices.reversed()) CatalogCardView(i, drawer)

            // The drawer area takes the drag (thumb) and the tap (tip one card).
            Box(
                Modifier
                    .offset(DRAWER_X.dp, DRAWER_Y.dp)
                    .size(DRAWER_W.dp, (FRONT_Y - DRAWER_Y + 40f).dp)
                    .semantics { contentDescription = "Catalogue drawer. Drag down to thumb forward, up to go back, or tap to tip one card." }
                    .pointerInput(drawer) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            takeOver()
                            val start = drawer.s
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var dragging = false
                            var moved = 0f
                            var up = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    up = true
                                    break
                                }
                                val dy = (change.position.y - down.position.y) / density
                                moved = max(moved, (change.position - down.position).getDistance() / density)
                                if (!dragging && abs(dy) > 6f) dragging = true
                                if (dragging) {
                                    change.consume()
                                    // Pulling down tips the front card toward you, so the drawer moves on.
                                    val v = rubberBand(start + dy / PT_PER_CARD, LAST.toFloat())
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) { drawer.held.snapTo(v) }
                                }
                            }
                            if (dragging) {
                                val v = tracker.calculateVelocity().y / density / PT_PER_CARD
                                val aim = (drawer.held.value + v * 0.18f).roundToInt().coerceIn(0, LAST)
                                scope.launch { drawer.held.animateTo(aim.toFloat(), spring(dampingRatio = 0.844f, stiffness = 170f), initialVelocity = v) }
                            } else if (up && moved < 8f) {
                                val aim = min(LAST, drawer.held.value.roundToInt() + 1)
                                scope.launch { drawer.held.animateTo(aim.toFloat(), spring(dampingRatio = 0.844f, stiffness = 170f)) }
                            }
                            letGo()
                        }
                    },
            )

            // The drawer's front panel over the cards' far ends, and its label.
            Canvas(
                Modifier
                    .offset(FRONT_X.dp, FRONT_Y.dp)
                    .size((FRONT_W + 12f).dp, (FRONT_H + 24f).dp),
            ) {
                scale(density, density, pivot = Offset.Zero) {
                    val a = appear.value
                    if (a < 1f) {
                        drawRoundRect(
                            Color(0xFF6E4A2E), Offset.Zero, androidx.compose.ui.geometry.Size(FRONT_W, FRONT_H),
                            androidx.compose.ui.geometry.CornerRadius(10f), alpha = 1f - a,
                        )
                    }
                    images?.let { drawImageAt(it.front, 0f, 0f, FRONT_W + 12f, FRONT_H + 24f, a) }
                }
            }
            Column(
                Modifier
                    .offset((FRONT_X + FRONT_W / 2f - 80f).dp, (FRONT_Y + 24f).dp)
                    .size(160.dp, 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    "FICTION · F – G",
                    style = TextStyle(color = Color(0xFF3A2614), fontFamily = FontFamily.Serif, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                )
                BasicText("DRAWER 14", style = TextStyle(color = Color(0xB33A2614), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            }

            CatalogPanel(drawer)

            val press = remember { MutableInteractionSource() }
            val down by press.collectIsPressedAsState()
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .offset(16.dp, 702.dp)
                    .size((DESIGN_W - 32f).dp, 52.dp)
                    .scale(if (down) 0.97f else 1f)
                    .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x47C98A3E), 10.dp, 22.dp, 26.dp)
                    .background(Brush.verticalGradient(0f to Color(0xFFF2C98A), 0.55f to Color(0xFFE0A85C), 1f to Color(0xFFC98A3E)), shape)
                    .drawWithContent {
                        drawContent()
                        val r = 26.dp.toPx()
                        drawLine(
                            Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color(0x99FFF5DC), 0.88f to Color(0x99FFF5DC), 1f to Color.Transparent),
                            Offset(r * 0.5f, 0.5.dp.toPx()), Offset(size.width - r * 0.5f, 0.5.dp.toPx()), 1.dp.toPx(),
                        )
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Place a hold on the card at the front, for example ${CARDS[TARGET].title}"
                    }
                    .clickable(press, null) {},
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Place a hold", style = TextStyle(color = Color(0xFF2A1808), fontSize = 17.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private class CatalogImages(val drawer: ImageBitmap, val front: ImageBitmap, val grain: ImageBitmap, val fine: ImageBitmap)

private fun DrawScope.drawImageAt(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, pivot = Offset.Zero)
    }) {
        drawImage(img, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** One index card. Its pose depends only on how far it is from the reading line; it never recomposes. */
@Composable
private fun CatalogCardView(index: Int, drawer: CatalogDrawer) {
    val card = CARDS[index]
    val tabLeft = 14f + card.tab * 106f
    val tabShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val bodyShape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .offset(CARD_X.dp, (PIVOT_Y - CARD_H).dp)
            .size(CARD_W.dp, CARD_H.dp)
            .graphicsLayer {
                val p = cardPose(index - drawer.s, drawer.lean)
                alpha = p.opacity
                translationY = p.ty * density
                scaleX = p.scale
                scaleY = p.scale
                rotationX = p.rot
                transformOrigin = TransformOrigin(0.5f, 1f)
                // CSS perspective 1600 dp; the layer's camera distance is in 72-per-inch units.
                cameraDistance = PERSPECTIVE * density / 72f
            },
    ) {
        Box(
            Modifier
                .offset(tabLeft.dp, 0.dp)
                .size(92.dp, 26.dp)
                .cssBoxShadow(Color(0x40281808), (-1).dp, 2.dp, 8.dp)
                .background(Color(0xFFEFE4CB), tabShape)
                .drawWithContent {
                    drawContent()
                    drawLine(Color(0xB3FFFFFF), Offset(6.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 6.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
                }
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                card.surname.take(2),
                style = TextStyle(color = Color(0xFF6B4A2A), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = TypeFace, letterSpacing = 1.sp),
            )
        }
        Box(
            Modifier
                .offset(0.dp, 22.dp)
                .size(CARD_W.dp, (CARD_H - 22f).dp)
                .cssBoxShadow(Color(0x59140A04), 1.dp, 2.dp, 6.dp)
                .cssBoxShadow(Color(0x470A0502), 10.dp, 24.dp, 6.dp)
                .clip(bodyShape)
                .background(Brush.verticalGradient(0f to Color(0xFFF7F0DF), 0.5f to Paper, 1f to Color(0xFFE8DCC0)))
                .drawWithContent {
                    drawContent()
                    drawLine(Color(0xBFFFFFFF), Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
                },
        ) {
            Box(Modifier.offset(0.dp, 44.dp).fillMaxWidth().height(1.5.dp).background(Color(0xB3C4403A)))
            for (y in RULES) Box(Modifier.offset(0.dp, y.dp).fillMaxWidth().height(0.75.dp).background(Color(0x475078B4)))
            BasicText(
                "${card.callTop}\n${card.callBottom}",
                Modifier.offset(14.dp, 10.dp),
                style = TextStyle(color = Ink, fontFamily = TypeFace, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                "${card.surname}, ${card.given}.",
                Modifier.offset(70.dp, 18.dp).width((CARD_W - 84f).dp),
                style = TextStyle(color = Ink, fontFamily = TypeFace, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "${card.title} / ${card.given} ${card.surname}.",
                Modifier.offset(86.dp, 54.dp).width((CARD_W - 100f).dp),
                style = TextStyle(color = Ink, fontFamily = TypeFace, fontSize = 13.sp, lineHeight = 20.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val imprint = TextStyle(color = Ink.copy(alpha = 0.85f), fontFamily = TypeFace, fontSize = 12.sp, lineHeight = 20.sp)
            BasicText(
                "${card.publisher}, ${card.year}.",
                Modifier.offset(86.dp, 114.dp).width((CARD_W - 100f).dp),
                style = imprint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "${card.pages} p. ; 21 cm.",
                Modifier.offset(86.dp, 134.dp).width((CARD_W - 100f).dp),
                style = imprint, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "ESL ${(4100 + index * 37).toString().padStart(5, '0')}",
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 12.dp)
                    .rotate(-4f),
                style = TextStyle(color = Color(0xC7623E8C), fontFamily = TypeFace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            )
            CatalogRodHole(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
        // The card's back, seen once it has tipped past upright.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = cardPose(index - drawer.s, drawer.lean).back },
        ) {
            Box(Modifier.offset(tabLeft.dp, 0.dp).size(92.dp, 26.dp).background(Color(0xFFE4D7BA), tabShape))
            Box(
                Modifier
                    .offset(0.dp, 22.dp)
                    .size(CARD_W.dp, (CARD_H - 22f).dp)
                    .clip(bodyShape)
                    .background(Color(0xFFE6D9BC)),
            ) {
                for (y in RULES) Box(Modifier.offset(0.dp, y.dp).fillMaxWidth().height(0.75.dp).background(Color(0x1F785A3C)))
                CatalogRodHole(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
        // The shade of the cards further back.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = cardPose(index - drawer.s, drawer.lean).shade },
        ) {
            Box(Modifier.offset(tabLeft.dp, 0.dp).size(92.dp, 26.dp).background(Color(0xFF0C0704), tabShape))
            Box(Modifier.offset(0.dp, 22.dp).size(CARD_W.dp, (CARD_H - 22f).dp).background(Color(0xFF0C0704), bodyShape))
        }
    }
}

/** The hole for the drawer's rod: dark, with an inner shadow and a lit lower lip. */
@Composable
private fun CatalogRodHole(modifier: Modifier) {
    Box(
        modifier
            .size(14.dp)
            .drawWithContent {
                drawCircle(Color(0x99FFFFFF), size.width / 2f, Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()))
                drawCircle(Color(0xFF2B1C12))
                drawCircle(
                    Brush.verticalGradient(0f to Color(0xB3000000), 0.5f to Color(0x00000000)),
                    size.width / 2f,
                )
            }
            .clip(CircleShape),
    )
}

@Composable
private fun CatalogHeader(drawer: CatalogDrawer) {
    Column(
        Modifier
            .offset(24.dp, 8.dp)
            .width((DESIGN_W - 48f).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("ELM STREET LIBRARY", style = TextStyle(color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("Fiction, F–G", style = TextStyle(color = Cream, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .height(32.dp)
                    .background(Color(0x14F3E8D4), shape)
                    .border(0.5.dp, Color(0x38F3E8D4), shape)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "Card of ${CARDS.size}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "CARD",
                    Modifier.padding(end = 6.dp),
                    style = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                )
                CatalogDigitRoll(drawer, 10)
                CatalogDigitRoll(drawer, 1)
                BasicText(
                    "/ ${CARDS.size}",
                    Modifier.padding(start = 4.dp),
                    style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
        }
        BasicText("Drawer 14 · Card catalogue, ground floor", style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

private val Digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

/** One digit of the card number, rolling like a counter wheel. */
@Composable
private fun CatalogDigitRoll(drawer: CatalogDrawer, place: Int) {
    Box(
        Modifier
            .size(9.dp, DIGIT_H.dp)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val v = max(1f, drawer.s + 1f)
                    val ones = v - 10f * floor(v / 10f)
                    val at = if (place == 1) ones else floor(v / 10f) + clamp01(ones - 9f)
                    translationY = -at * DIGIT_H * density
                },
        ) {
            for (d in Digits) {
                Box(Modifier.size(9.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        d,
                        style = TextStyle(color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** A column of labels in a clipped box, rolled to the card at the front (fractional while thumbing). */
@Composable
private fun CatalogRoll(drawer: CatalogDrawer, height: Float, lines: List<String>, style: TextStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(height.dp)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val at = min(max(drawer.s, -0.4f), lines.size - 0.6f)
                    translationY = -at * height * density
                },
        ) {
            for (line in lines) {
                Box(Modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.CenterStart) {
                    BasicText(line, Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val Titles = CARDS.map { it.title }
private val Bylines = CARDS.map { "${it.given} ${it.surname} · ${it.year}" }
private val Calls = CARDS.map { "${it.callTop} ${it.callBottom}" }
private val Statuses = CARDS.map { STATUS_LABEL[it.status] }

@Composable
private fun CatalogPanel(drawer: CatalogDrawer) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .offset(16.dp, 590.dp)
            .size((DESIGN_W - 32f).dp, 96.dp)
            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 20.dp)
            .cssBoxShadow(Color(0x59000000), 12.dp, 30.dp, 20.dp)
            .background(Color(0xD1180F09), shape)
            .border(0.5.dp, Color(0x29F3E8D4), shape)
            .drawWithContent {
                drawContent()
                drawLine(Color(0x14FFECC8), Offset(20.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 20.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CatalogRoll(drawer, ROLL_H, Titles, TextStyle(color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            CatalogRoll(drawer, 20f, Bylines, TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }
        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CatalogRoll(
                drawer, 20f, Calls,
                TextStyle(color = Amber, fontFamily = TypeFace, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                Modifier.width(104.dp),
            )
            CatalogRoll(
                drawer, 18f, Statuses,
                TextStyle(color = Color(0xE6AAD2A0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
                Modifier.width(104.dp),
            )
        }
    }
}
