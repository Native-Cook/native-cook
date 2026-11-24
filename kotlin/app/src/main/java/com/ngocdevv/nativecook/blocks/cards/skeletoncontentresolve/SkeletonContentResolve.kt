package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import android.provider.Settings
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val PAGE = Color(0xFF15120F)
private val CARD = Color(0xFF211C17)
private val RAISED = Color(0xFF2A231C)
private val INK = Color(0xFFF5EDE1)
private val MUTE = Color(0xFFA3978A)
private val FAINT = Color(0xFF6F665C)
private val EMBER = Color(0xFFF0803C)
private val HAIR = Color(0xFFF5E8D6).copy(alpha = 0.09f)
private val BAR = Color(0xFFF5E8D6).copy(alpha = 0.11f)
private val SHINE = Color(0xFFFFF0DE).copy(alpha = 0.22f)
private val BLOCK = Color(0xFF2E2821)
private val TAB_BG = Color(0xFF191511)
private val WARM_HIGHLIGHT = Color(0xFFFFF0DC)

private val SIDE = 16.dp
private val DOT = 46.dp
private val SPINNER = 28.dp
private const val SPIN_FADE = 0.3f

private fun ts(
    size: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    line: Float? = null,
    spacing: Float = 0f,
    tabular: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = if (line != null) line.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val KICKER = ts(14f, MUTE, FontWeight.Medium, 18f)
private val EYEBROW = ts(13f, EMBER, FontWeight.SemiBold, 16f)
private val META = ts(13f, MUTE, line = 17f, tabular = true)
private val PLAY_TEXT = ts(14f, PAGE, FontWeight.SemiBold)
private val SECTION = ts(20f, INK, FontWeight.Bold, spacing = -0.3f)
private val SECTION_LINK = ts(15f, EMBER, FontWeight.Medium)
private val ROW_TITLE = ts(16f, INK, FontWeight.SemiBold, 21f, -0.2f)
private val DAY = ts(13f, FAINT, FontWeight.Medium, 17f)
private val MINI_TITLE = ts(15f, INK, FontWeight.SemiBold, spacing = -0.2f)
private val MINI_META = ts(12.5f, MUTE, tabular = true)

private data class TabItem(val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabItem(Icons.Filled.Home, "Home"),
    TabItem(Icons.Filled.Search, "Search"),
    TabItem(Icons.Filled.Layers, "Library"),
    TabItem(Icons.Outlined.ArrowCircleDown, "Downloads"),
)

/**
 * A podcast home whose loading state turns into its content instead of being swapped for it.
 * Pull down to refresh: every line becomes a bar with one band of light crossing all of them; then,
 * top to bottom, each bar stretches to the exact width of the text it stood for, the text fades in
 * inside it and the bar dissolves, while the covers open through a soft radial wipe.
 */
@Composable
fun SkeletonContentResolve(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val film = autoPlay && !reduced
    val controller = remember {
        ResolveController(
            film,
            onArm = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            onLand = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
        )
    }
    SideEffect { controller.film = film }
    LaunchedEffect(controller) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { controller.frame(it / 1_000_000_000.0) }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(PAGE)) {
        val screenW = maxWidth
        val screenH = maxHeight
        val compact = screenH < 740.dp
        val contentW = min(screenW.value, 480f).dp
        val w = contentW - SIDE * 2
        val top = statusTop + if (compact) 2.dp else 6.dp
        val headerH = if (compact) 52.dp else 60.dp
        val gap = if (compact) 10.dp else 14.dp
        val tabInset = max(navBottom.value, 6f).dp
        val tabH = 49.dp + tabInset
        val miniH = if (compact) 54.dp else 60.dp
        val miniBottom = tabH + 8.dp
        val listTop = top + headerH + gap
        val listH = screenH - listTop - miniBottom - miniH - if (compact) 6.dp else 10.dp
        val featuredH = if (compact) 148.dp else 160.dp
        val sectionGap = if (compact) 12.dp else 18.dp
        val sectionH = if (compact) 26.dp else 28.dp
        val rowH = floor((listH - 4.dp - featuredH - sectionGap - sectionH - 4.dp).value / 4f).coerceIn(56f, 84f).dp
        val coverSize = min(60f, rowH.value - 18f).dp
        val bigCover = featuredH - 28.dp
        val ghostY = listTop + featuredH * 0.55f

        val group = remember(contentW, listH) {
            SkeletonGroup(
                sinceHide = controller.sinceHide,
                sinceShow = controller.sinceShow,
                span = contentW.value + SLANT * listH.value,
                bar = BAR,
                shine = SHINE,
                block = BLOCK,
            )
        }
        val feed = FEEDS[controller.data.intValue]
        val titleSize = if (compact) 28f else 32f
        val featuredTitle = if (compact) 18f else 19f
        val titleStyle = remember(titleSize) { ts(titleSize, INK, FontWeight.Bold, titleSize + 5f, -0.5f) }
        val featuredStyle = remember(featuredTitle) { ts(featuredTitle, INK, FontWeight.Bold, featuredTitle + 4f, -0.3f) }

        Box(Modifier.width(contentW).fillMaxHeight().align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(top = top).height(headerH).fillMaxWidth().padding(horizontal = SIDE),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Tuesday evening", style = KICKER)
                        Text("Listen now", style = titleStyle)
                    }
                    Avatar()
                }

                Box(
                    Modifier
                        .padding(top = gap)
                        .height(listH)
                        .fillMaxWidth()
                        .clipToBounds()
                        .semantics {
                            customActions = listOf(CustomAccessibilityAction("Refresh") { controller.refresh(); true })
                        }
                        .pointerInput(controller) { pullGesture(controller) },
                ) {
                    Spinner(controller, Modifier.offset(x = contentW / 2 - SPINNER / 2, y = 12.dp))

                    Column(
                        Modifier
                            .graphicsLayer { translationY = controller.offset.floatValue * density }
                            .fillMaxWidth()
                            .onPlaced { group.container = it }
                            .background(PAGE)
                            .padding(start = SIDE, end = SIDE, top = 4.dp),
                    ) {
                        Featured(group, feed, featuredH, bigCover, featuredStyle)
                        Row(
                            Modifier.padding(top = sectionGap).height(sectionH).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Up next", style = SECTION)
                            Text("See all", style = SECTION_LINK)
                        }
                        Column(Modifier.padding(top = 4.dp)) {
                            feed.episodes.forEachIndexed { r, episode ->
                                EpisodeRow(group, episode, r, rowH, coverSize, last = r == feed.episodes.lastIndex)
                            }
                        }
                    }
                }
            }

            MiniPlayer(miniH, w, Modifier.align(Alignment.BottomStart).padding(start = SIDE, bottom = miniBottom))
            TabBar(tabH, tabInset, Modifier.align(Alignment.BottomStart))
        }

        Box(
            Modifier
                .graphicsLayer {
                    alpha = controller.ghost.floatValue * 0.9f
                    translationX = (screenW / 2 - DOT / 2).toPx()
                    translationY = (ghostY + controller.fingerDy.floatValue.dp - DOT / 2).toPx()
                }
                .size(DOT)
                .background(Color.White.copy(alpha = 0.22f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.pullGesture(controller: ResolveController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        controller.take()
        var active = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dx = (change.position.x - down.position.x) / density
            val dy = (change.position.y - down.position.y) / density
            if (!active) {
                if (dx > 24f || dx < -24f) break
                if (dy >= 8f) {
                    active = true
                    controller.dragStart()
                }
            }
            if (active) {
                change.consume()
                controller.drag(dy)
            }
        }
        if (active) controller.release()
    }
}

@Composable
private fun Avatar() {
    Box(
        Modifier
            .padding(bottom = 3.dp)
            .size(36.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFF3A25C), Color(0xFFD9653A), Color(0xFF8A3A3F)),
                        start = Offset(size.width * 0.1f, 0f),
                        end = Offset(size.width * 0.9f, size.height),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("MR", style = ts(13f, Color(0xFFFFF6E8), FontWeight.Bold, spacing = 0.2f))
    }
}

@Composable
private fun Spinner(controller: ResolveController, modifier: Modifier) {
    Canvas(
        modifier
            .size(SPINNER)
            .graphicsLayer {
                val ss = controller.sinceShow.floatValue
                val spinning = controller.sinceHide.floatValue >= 0f && ss < SPIN_FADE
                val a = if (spinning) {
                    if (ss < 0f) 1f else 1f - seg(ss, 0f, SPIN_FADE)
                } else if (controller.reveal.floatValue > 0f) 1f else 0f
                alpha = a
                scaleX = 0.8f + 0.2f * a
                scaleY = scaleX
            },
    ) {
        val hidden = controller.sinceHide.floatValue
        val spinning = hidden >= 0f && controller.sinceShow.floatValue < SPIN_FADE
        val reveal = controller.reveal.floatValue
        val c = Offset(size.width / 2f, size.height / 2f)
        val tickW = 2.5.dp.toPx()
        val tickH = 7.dp.toPx()
        val out = 8.5.dp.toPx()
        for (i in 0 until TICKS) {
            val a = if (spinning) tickSpinning(i, hidden) else tickArming(i, reveal)
            if (a <= 0f) continue
            rotate(i * 360f / TICKS, c) {
                drawRoundRect(
                    INK,
                    Offset(c.x - tickW / 2f, c.y - tickH / 2f - out),
                    Size(tickW, tickH),
                    CornerRadius(tickW / 2f),
                    alpha = a,
                )
            }
        }
    }
}

@Composable
private fun Featured(group: SkeletonGroup, feed: Feed, height: Dp, bigCover: Dp, titleStyle: TextStyle) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .dropShadow(shape, Shadow(radius = 28.dp, color = Color.Black, offset = DpOffset(0.dp, 14.dp), alpha = 0.3f))
            .dropShadow(shape, Shadow(radius = 2.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.4f))
            .background(CARD, shape)
            .innerShadow(shape, Shadow(radius = 0.dp, color = WARM_HIGHLIGHT, offset = DpOffset(0.dp, 1.dp), alpha = 0.07f))
            .padding(14.dp),
    ) {
        SkeletonCover(group, 0f, bigCover, 16.dp, feed.cover)
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Skeleton(group, 0.5f, Placeholder.EYEBROW, 10f) { OneLine(feed.eyebrow, EYEBROW) }
                Skeleton(group, 1f, Placeholder.TITLE_A, 15f) { OneLine(feed.titleA, titleStyle) }
                Skeleton(group, 1.5f, Placeholder.TITLE_B, 15f) { OneLine(feed.titleB, titleStyle) }
                Skeleton(group, 2.2f, Placeholder.META, 10f) { OneLine("${feed.show} · ${feed.minutes} min", META) }
            }
            Skeleton(group, 3f, Placeholder.PLAY, 30f, radius = 15f) {
                Row(
                    Modifier.height(30.dp).background(INK, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = PAGE, modifier = Modifier.size(14.dp))
                    Text("Play", style = PLAY_TEXT)
                }
            }
        }
    }
}

@Composable
private fun OneLine(text: String, style: TextStyle) {
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun EpisodeRow(group: SkeletonGroup, episode: Episode, index: Int, height: Dp, coverSize: Dp, last: Boolean) {
    val order = 4f + index * 2f
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                if (!last) {
                    val rule = 1f
                    val x = (coverSize + 12.dp).toPx()
                    drawRect(HAIR, Offset(x, size.height - rule), Size(size.width - x, rule))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCover(group, order, coverSize, 12.dp, episode.cover)
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Skeleton(group, order + 0.5f, Placeholder.ROW_TITLE[index], 12f) { OneLine(episode.title, ROW_TITLE) }
            Skeleton(group, order + 1f, Placeholder.ROW_META[index], 9f) {
                OneLine("${episode.show} · ${episode.minutes} min", META)
            }
        }
        Skeleton(group, order + 0.5f, Placeholder.DAY, 9f, alignEnd = true) { Text(episode.day, style = DAY) }
    }
}

@Composable
private fun MiniPlayer(height: Dp, width: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val art = height - 16.dp
    Box(
        modifier
            .width(width)
            .height(height)
            .dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 16.dp), alpha = 0.38f))
            .dropShadow(shape, Shadow(radius = 4.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.4f))
            .clip(shape)
            .background(RAISED)
            .innerShadow(shape, Shadow(radius = 0.dp, color = WARM_HIGHLIGHT, offset = DpOffset(0.dp, 1.dp), alpha = 0.08f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(NOW_PLAYING, art, 10.dp)
            Column(
                Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                OneLine("Night Swim Radio", MINI_TITLE)
                OneLine("Low Tide Hours · 12:08 left", MINI_META)
            }
            Icon(Icons.Filled.Pause, "Pause", tint = INK, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0xFFF5E8D6).copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(0.38f).height(2.dp).background(EMBER))
        }
    }
}

@Composable
private fun CoverImage(spec: CoverSpec, size: Dp, radius: Dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(radius))) {
        drawCover(spec, this.size.width)
    }
}

@Composable
private fun TabBar(height: Dp, inset: Dp, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(TAB_BG)
            .drawBehind { drawRect(HAIR, Offset.Zero, Size(size.width, 1f)) }
            .padding(top = 7.dp, bottom = inset),
    ) {
        TABS.forEachIndexed { i, tab ->
            val tint = if (i == 0) EMBER else FAINT
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Text(tab.label, style = ts(10f, tint, FontWeight.Medium))
            }
        }
    }
}
