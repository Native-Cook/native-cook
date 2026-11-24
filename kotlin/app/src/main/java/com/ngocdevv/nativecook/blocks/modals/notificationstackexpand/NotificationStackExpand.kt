package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

// Relay, a team inbox: the screen, the rest of the feed and the autoPlay script around the reusable NotificationStack.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

// Layout, dp.
private const val SIDE = 16f
private const val BRAND_H = 28f
private const val TITLE_H = 41f
private const val FILTER_H = 32f
private const val LABEL_H = 22f
/** From the top of the header block to the top of the group. */
private const val ABOVE_GROUP = BRAND_H + 4f + TITLE_H + 12f + FILTER_H + 20f + LABEL_H + 8f
private const val BELOW_GAP = 14f
private const val TAB_H = 50f
private const val GHOST = 44f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

/** The ghost finger's drag left across the first card: slow start, fast at the release. */
private fun swipeX(t: Float, width: Float): Float {
    if (t <= NseT.swipeStart) return 0f
    if (t < NseT.swipeEnd) {
        val u = (t - NseT.swipeStart) / (NseT.swipeEnd - NseT.swipeStart)
        return -SWIPE_DRAG * (0.35f * u + 0.65f * u * u)
    }
    val v = seg(t, NseT.swipeEnd, NseT.flingSpan)
    val travel = width + 24f - SWIPE_DRAG
    return -SWIPE_DRAG - travel * (0.4f * v + 0.6f * v * v)
}

/** The ghost finger's pull down on the stack, dp. */
private fun pullDy(t: Float) = GHOST_PEEK * easeInOutCubic(seg(t, NseT.peekStart, NseT.peekRelease - NseT.peekStart))

private fun peekAt(t: Float): Float {
    if (t < NseT.peekStart) return 0f
    if (t < NseT.peekRelease) return peekFromDrag(pullDy(t))
    return peekFromDrag(GHOST_PEEK) * (1f - springAt(t - NseT.peekRelease, PEEK_SPRING))
}

/** Every card of the stack at loop time t. */
private fun applyPose(m: StackMotion, t: Float, width: Float, total: Int) {
    val gapShift = springAt(t - NseT.gap, GAP_SPRING) - springAt(t - NseT.arrive - 0.04f, PUSH_SPRING)
    val arrive = easeOutCubic(seg(t, NseT.arrive, NseT.arriveSpan))
    for (i in 0 until total) {
        val c = m.cards[i]
        val open = springAt(t - NseT.expand - i * STAGGER, EXPAND_SPRING)
        val close = springAt(t - NseT.collapse - (total - 1 - i) * STAGGER, COLLAPSE_SPRING)
        c.e.put(open - close)
        if (i == 0) {
            c.slot.put(0f)
            c.x.put(if (t < NseT.arrive) swipeX(t, width) else 0f)
            c.alpha.put(if (t < NseT.arrive) 1f - seg(t, NseT.swipeEnd + 0.12f, 0.14f) else arrive)
            c.drop.put(if (t < NseT.arrive) 0f else ARRIVE_DROP * (1f - arrive))
        } else {
            c.slot.put(i - gapShift)
            c.x.put(0f)
            c.alpha.put(1f)
            c.drop.put(0f)
        }
    }
    m.peek.put(peekAt(t))
    m.count.put(total - gapShift)
    m.countN = if (t >= NseT.gap && t < NseT.arrive) total - 1 else total
}

private class GhostPoint(val t: Float, val x: Float, val y: Float)

private fun ghostAt(path: List<GhostPoint>, t: Float, xAxis: Boolean): Float {
    fun v(p: GhostPoint) = if (xAxis) p.x else p.y
    if (t <= path[0].t) return v(path[0])
    for (i in 1 until path.size) {
        if (t <= path[i].t) {
            val u = easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t))
            return mix(u, v(path[i - 1]), v(path[i]))
        }
    }
    return v(path.last())
}

private fun ghostOpacity(t: Float): Float {
    val a = seg(t, NseT.ghostIn, 0.18f) * (1f - seg(t, NseT.tapOpen + 0.15f, 0.2f))
    val b = seg(t, NseT.swipeStart - 0.35f, 0.2f) * (1f - seg(t, NseT.swipeEnd + 0.05f, 0.18f))
    val c = seg(t, NseT.tapLess - 0.35f, 0.2f) * (1f - seg(t, NseT.tapLess + 0.1f, 0.2f))
    val d = seg(t, NseT.peekStart - 0.35f, 0.2f) * (1f - seg(t, NseT.peekRelease + 0.05f, 0.2f))
    return maxOf(a, b, c, d)
}

private fun ghostPress(t: Float): Float {
    var press = 0f
    for (tap in floatArrayOf(NseT.tapOpen, NseT.tapLess)) {
        val d = t - tap
        if (d > -0.1f && d < 0.12f) press = max(press, if (d < 0f) 1f + d / 0.1f else 1f - d / 0.12f)
    }
    val drag = seg(t, NseT.swipeStart - 0.08f, 0.08f) * (1f - seg(t, NseT.swipeEnd, 0.1f))
    val pull = seg(t, NseT.peekStart - 0.08f, 0.08f) * (1f - seg(t, NseT.peekRelease, 0.1f))
    return maxOf(press, drag, pull)
}

/** The rest pose: stacked, whole, still. Reduce Motion: the list shown open instead. */
private fun staticPose(m: StackMotion, total: Int, open: Int) {
    for (i in m.cards.indices) {
        val c = m.cards[i]
        c.e.set(open.toFloat())
        c.slot.set(i.toFloat())
        c.x.set(0f)
        c.alpha.set(if (i < total) 1f else 0f)
        c.drop.set(0f)
    }
    m.peek.set(0f)
    m.count.set(total.toFloat())
    m.countN = total
    m.open = open
    m.swiped = -1
    m.gone = IntArray(MAX_CARDS) { if (it < total) 0 else 1 }
}

/** The driver's own state: clock, mode and the ghost finger. */
private class NseDriver {
    var clock = 0f
    var mode by mutableIntStateOf(MODE_AUTO)
    var seen = 0
    var idle = 0f
    var rootHeld = 0
    val ghostX = mutableFloatStateOf(0f)
    val ghostY = mutableFloatStateOf(0f)
    val ghostO = NseValue(0f)
    val ghostS = mutableFloatStateOf(0f)
}

private class NseGeometry(val rowW: Float, val grabX: Float, val pullY: Float, val path: List<GhostPoint>)

@Composable
fun NotificationStackExpand(autoPlay: Boolean = true, group: NoticeGroup = LAUNCH) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val total = min(group.notices.size, MAX_CARDS)
    val m = remember(total) { StackMotion(total) }
    val driver = remember { NseDriver() }
    var filter by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(NSE_BG[1])
            .clipToBounds()
            .pointerInput(m) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed() }) {
                            driver.rootHeld = event.changes.count { it.pressed }
                            m.touches += 1
                        }
                    }
                }
            },
    ) {
        val screenDensity = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val width = maxWidth.value
        val insetTop = insets.getTop(screenDensity) / screenDensity.density
        val insetBottom = insets.getBottom(screenDensity) / screenDensity.density
        val rowW = width - SIDE * 2f
        val headerTop = insetTop + 6f
        val groupTop = headerTop + ABOVE_GROUP
        val belowTop = groupTop + groupHeight(0f, total.toFloat()) + BELOW_GAP
        val tabBottom = max(insetBottom, 8f)

        val geometry = remember(rowW, groupTop) {
            val cx = SIDE + rowW * 0.56f
            val cy = groupTop + CARD_H / 2f
            val c0y = groupTop + HEADER_H + CARD_H / 2f
            val grabX = SIDE + rowW - 56f
            val lessX = SIDE + rowW - CLOSE_W - 8f - SHOW_LESS_W / 2f
            val lessY = groupTop + (HEADER_H - 8f) / 2f
            val pullY = cy - 8f
            val t = NseT
            NseGeometry(
                rowW, grabX, pullY,
                listOf(
                    GhostPoint(t.ghostIn, cx + 28f, cy + 40f),
                    GhostPoint(t.tapOpen - 0.08f, cx, cy),
                    GhostPoint(t.tapOpen + 0.12f, cx, cy),
                    GhostPoint(t.tapOpen + 0.4f, cx + 12f, cy + 30f),
                    GhostPoint(t.swipeStart - 0.35f, grabX + 18f, c0y + 30f),
                    GhostPoint(t.swipeStart - 0.04f, grabX, c0y),
                    GhostPoint(t.swipeStart, grabX, c0y),
                    // While the finger drags, its x is the card's (below); y holds.
                    GhostPoint(t.swipeEnd, grabX - SWIPE_DRAG, c0y),
                    GhostPoint(t.swipeEnd + 0.24f, grabX - SWIPE_DRAG - 70f, c0y - 10f),
                    GhostPoint(t.tapLess - 0.35f, lessX + 20f, lessY + 40f),
                    GhostPoint(t.tapLess - 0.08f, lessX, lessY),
                    GhostPoint(t.tapLess + 0.12f, lessX, lessY),
                    GhostPoint(t.tapLess + 0.4f, lessX + 10f, lessY + 30f),
                    GhostPoint(t.peekStart - 0.35f, cx + 20f, pullY + 34f),
                    GhostPoint(t.peekStart - 0.04f, cx, pullY),
                    GhostPoint(t.peekStart, cx, pullY),
                    GhostPoint(t.peekRelease, cx, pullY + GHOST_PEEK),
                    GhostPoint(t.peekRelease + 0.3f, cx + 10f, pullY + GHOST_PEEK + 14f),
                ),
            )
        }
        // Read every frame through State, so a new layout reaches the running loop.
        val geo by rememberUpdatedState(geometry)

        LaunchedEffect(m, playing, reduced, total) {
            val d = driver
            if (playing) {
                d.clock = 0f
                d.mode = MODE_AUTO
                m.driven = 1
            } else {
                // Reduce Motion: the list stands open and legible. autoPlay off: the resting stack.
                d.mode = MODE_MANUAL
                m.driven = 0
                d.ghostO.set(0f)
                staticPose(m, total, if (reduced) 1 else 0)
            }
            val wake = Channel<Unit>(Channel.CONFLATED)
            m.onAnimationStart { wake.trySend(Unit) }
            d.ghostO.onStart = { wake.trySend(Unit) }
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = min((now - last) / 1_000_000_000f, 0.048f)
                last = now
                if (dt <= 0f) continue
                d.ghostO.step(dt)
                val animating = m.step(dt) || d.ghostO.active

                if (m.touches != d.seen) {
                    d.seen = m.touches
                    d.idle = 0f
                    if (d.mode != MODE_MANUAL) {
                        adoptPose(m)
                        d.ghostO.timing(0f, 0.12f)
                        d.mode = MODE_MANUAL
                    }
                }
                if (!playing) {
                    if (!animating) {
                        wake.receive()
                        last = withFrameNanos { it }
                    }
                    continue
                }
                when (d.mode) {
                    MODE_MANUAL -> {
                        if (m.held > 0 || d.rootHeld > 0) {
                            d.idle = 0f
                        } else {
                            d.idle += dt
                            if (d.idle >= RESUME_AFTER) {
                                restoreStack(m, total)
                                d.mode = MODE_RETURN
                            }
                        }
                    }
                    MODE_RETURN -> if (stackAtRest(m, total)) {
                        d.clock = 0f
                        m.driven = 1
                        d.mode = MODE_AUTO
                    }
                    else -> {
                        var t = d.clock + dt
                        if (t >= LOOP) t -= LOOP
                        d.clock = t
                        val g = geo
                        applyPose(m, t, g.rowW, total)
                        val inSwipe = t > NseT.swipeStart && t < NseT.swipeEnd
                        val inPull = t > NseT.peekStart && t < NseT.peekRelease
                        d.ghostX.floatValue = if (inSwipe) g.grabX + swipeX(t, g.rowW) else ghostAt(g.path, t, true)
                        d.ghostY.floatValue = if (inPull) g.pullY + pullDy(t) else ghostAt(g.path, t, false)
                        d.ghostO.set(ghostOpacity(t))
                        d.ghostS.floatValue = ghostPress(t)
                    }
                }
            }
        }

        // Header block.
        Column(Modifier.offset(SIDE.dp, headerTop.dp).width(rowW.dp)) {
            Row(Modifier.fillMaxWidth().height(BRAND_H.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Text("Relay", Modifier.padding(start = 7.dp), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NSE_INK))
                Text("Fernway", Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 15.sp, color = NSE_MUTE))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).background(
                        Brush.verticalGradient(0f to Color(0xFFC7D3E0), 0.5f to Color(0xFF8EA2B8), 1f to Color(0xFF64788F)),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AL", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
            Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(TITLE_H.dp)) {
                Text(
                    "Inbox",
                    Modifier.alignByBaseline().semantics { heading() },
                    style = TextStyle(fontSize = 34.sp, lineHeight = TITLE_H.sp, fontWeight = FontWeight.Bold, color = NSE_INK, letterSpacing = 0.2.sp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "9 unread",
                    Modifier.alignByBaseline(),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = NSE_MUTE, fontFeatureSettings = "tnum"),
                )
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(FILTER_H.dp), contentAlignment = Alignment.Center) {
                FeedFilter(FILTERS, filter, { filter = it })
            }
            SectionLabel("Today", Modifier.padding(top = 20.dp))
        }

        // The rest of the feed, pushed down while the group opens.
        Column(
            Modifier
                .offset(SIDE.dp, belowTop.dp)
                .width(rowW.dp)
                .graphicsLayer { translationY = belowShift(m, total) * density },
        ) {
            NoticeCard(STANDUP)
            Spacer(Modifier.height(12.dp))
            StillStack(CRIT, rowW)
            SectionLabel("Yesterday", Modifier.padding(top = 22.dp, bottom = 8.dp))
            NoticeCard(YESTERDAY[0])
            Spacer(Modifier.height(8.dp))
            NoticeCard(YESTERDAY[1])
        }

        NotificationStack(group, m, rowW, Modifier.offset(SIDE.dp, groupTop.dp), reducedMotion = reduced)

        TabBar(Modifier.align(Alignment.BottomStart), width, tabBottom)

        Box(
            Modifier
                .size(GHOST.dp)
                .graphicsLayer {
                    alpha = if (driver.mode == MODE_AUTO) driver.ghostO.get() * 0.9f else 0f
                    translationX = (driver.ghostX.floatValue - GHOST / 2f) * density
                    translationY = (driver.ghostY.floatValue - GHOST / 2f) * density
                    val s = 1f - 0.16f * driver.ghostS.floatValue
                    scaleX = s
                    scaleY = s
                }
                .background(Color(110, 118, 130).copy(alpha = 0.32f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier.height(LABEL_H.dp).padding(start = 4.dp),
        style = TextStyle(fontSize = 15.sp, lineHeight = LABEL_H.sp, fontWeight = FontWeight.SemiBold, color = NSE_MUTE),
    )
}

/** The inbox filter: a Material 3 segmented control (RN uses SwiftUI's segmented Picker on iOS). */
@Composable
private fun FeedFilter(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val track = Color(118, 118, 128).copy(alpha = 0.12f)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(FILTER_H.dp)) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = RoundedCornerShape(9.dp)),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.White,
                    activeContentColor = NSE_INK,
                    activeBorderColor = track,
                    inactiveContainerColor = track,
                    inactiveContentColor = NSE_INK,
                    inactiveBorderColor = track,
                ),
                icon = {},
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                label = {
                    Text(
                        label,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Medium,
                            color = NSE_INK,
                        ),
                    )
                },
            )
        }
    }
}

/** A group that stays stacked: its back cards peek under the front one. */
@Composable
private fun StillStack(group: NoticeGroup, width: Float) {
    val shown = group.notices.take(3)
    Box(
        Modifier
            .width(width.dp)
            .height(groupHeight(0f, shown.size.toFloat()).dp)
            .semantics { contentDescription = "${group.title}, ${shown.size} notifications" },
    ) {
        for (i in shown.indices.reversed()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CARD_H.dp)
                    .graphicsLayer {
                        translationY = collapsedY(i.toFloat()) * density
                        scaleX = 1f - DEPTH_SCALE * i
                        scaleY = 1f - DEPTH_SCALE * i
                    }
                    .nseCardSurface(),
            ) {
                if (i == 0) {
                    NoticeFace(shown[i])
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFC9CFD7).copy(alpha = 0.32f * i), RoundedCornerShape(RADIUS.dp)))
                }
            }
        }
    }
}

private val MARK_LINES = svg("M10.5 9 H17.5 M10.5 12 H15")

@Composable
private fun BrandMark() {
    Canvas(Modifier.size(22.dp)) {
        inViewBox(22f) {
            drawRoundRect(Color(0xFF8FC2B6), Offset(1f, 5f), Size(14f, 14f), CornerRadius(4.5f))
            drawRoundRect(NSE_ACCENT, Offset(7f, 2f), Size(14f, 14f), CornerRadius(4.5f))
            strokePath(MARK_LINES, Color(0xFFE9F3F0), 1.6f)
        }
    }
}

private enum class TabIcon { Inbox, Channels, Search, You }

private val INBOX = svg("M4 13 L6.5 5.2 A2 2 0 0 1 8.4 3.8 H17.6 A2 2 0 0 1 19.5 5.2 L22 13 V18 A2 2 0 0 1 20 20 H6 A2 2 0 0 1 4 18 Z M4 13 H9 L10.5 15.5 H15.5 L17 13 H22")
private val CHANNELS = svg("M10 3.5 L8.4 20.5 M17.6 3.5 L16 20.5 M5 8.5 H21.5 M4.5 15.5 H21")
private val SEARCH_TAIL = svg("M16.3 15.3 L21 20")
private val YOU_BODY = svg("M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5")

@Composable
private fun TabGlyph(kind: TabIcon, color: Color) {
    Canvas(Modifier.size(26.dp, 24.dp)) {
        inViewBox(26f) {
            val w = 1.8f
            when (kind) {
                TabIcon.Inbox -> strokePath(INBOX, color, w)
                TabIcon.Channels -> strokePath(CHANNELS, color, w)
                TabIcon.Search -> {
                    drawCircle(color, 6.5f, Offset(11.5f, 10.5f), style = Stroke(w))
                    strokePath(SEARCH_TAIL, color, w)
                }
                TabIcon.You -> {
                    drawCircle(color, 4.2f, Offset(13f, 8.5f), style = Stroke(w))
                    strokePath(YOU_BODY, color, w)
                }
            }
        }
    }
}

@Composable
private fun TabBar(modifier: Modifier, width: Float, tabBottom: Float) {
    val tabs = listOf(TabIcon.Inbox to "Inbox", TabIcon.Channels to "Channels", TabIcon.Search to "Search", TabIcon.You to "You")
    val tabW = width / tabs.size
    // RN draws a frosted BlurView here; Compose cannot blur what lies behind, so the bar is a near-opaque tint.
    Box(
        modifier
            .fillMaxWidth()
            .height((TAB_H + tabBottom).dp)
            .background(Color(0xFFF4F5F7).copy(alpha = 0.94f)),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x291B1F24)))
        Row(Modifier.fillMaxWidth().height(TAB_H.dp)) {
            tabs.forEachIndexed { i, (kind, label) ->
                val on = i == 0
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = if (on) "$label, selected" else label },
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(top = 6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TabGlyph(kind, if (on) NSE_ACCENT else Color(0xFF8A9099))
                        Text(
                            label,
                            Modifier.padding(top = 2.dp),
                            style = TextStyle(
                                fontSize = 10.5.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) NSE_ACCENT else Color(0xFF8A9099),
                            ),
                        )
                    }
                    if (on) {
                        Box(
                            Modifier
                                .offset((tabW / 2f + 6f).dp, 3.dp)
                                .height(17.dp)
                                .background(NSE_DANGER, RoundedCornerShape(8.5.dp))
                                .padding(horizontal = 4.dp)
                                .width(9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("9", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun NotificationStackExpandPreview() {
    NotificationStackExpand(autoPlay = false)
}
