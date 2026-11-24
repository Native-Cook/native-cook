package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// "Margin": a reading list whose rows can be peeked (long press) and popped into the article (swipe up).
// The peek itself is PeekPreview.kt (reusable); this file is the screen, what is drawn inside the frame, and the
// autoPlay script's clock and ghost finger.

// Palette: a cool paper, one ink, one green.
private val DESK = Color(0xFFEFEEE9)
private val SHEET = Color(0xFFFDFCF9)
private val INK = Color(0xFF1A1B1E)
private val INK_BODY = Color(0xFF2C2D32)
private val INK_SOFT = Color(0xFF7C7D84)
private val LEAF = Color(0xFF0F7B5F)
private val DANGER = Color(0xFFD23A2E)
private val SHADOW = Color(26 / 255f, 24 / 255f, 18 / 255f, 1f)

// Layout, in dp.
private const val SIDE = 16f
private const val MAX_COLUMN = 520f
private const val HEADER = 104f
private const val ROW_GAP = 8f
private const val ROW_MAX = 88f
private const val ROW_MIN = 72f
private const val FOOT = 60f
private const val COVER_INSET = 12f
private const val THUMB_RADIUS = 12f
private const val CARD_SIDE = 14f
private const val CARD_MAX = 400f
private const val CARD_PAD = 18f
private const val PAGE_PAD = 20f
private const val ACTIONS_GAP = 10f
private const val ACTIONS_WIDTH = 250f
private const val ACTION_ROW = 46f

// Title, meta and text are set once at the page's size and scaled down for the preview and the row, in the list
// too, so all three states are the same pixels and wrap the same way.
private const val TITLE_SIZE = 26f
private const val TITLE_LINE = 31f
private const val TITLE_ROW = 16f / TITLE_SIZE
private const val TITLE_CARD = 21f / TITLE_SIZE
private const val META_LINE = 18f
private const val BODY_SIZE = 17f
private const val BODY_LINE = 26f
private const val EXCERPT_LINES = 4
private const val NAV = 38f

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1
private const val MODE_RETURN = 2

private class Metrics(
    val width: Float,
    val height: Float,
    val geometry: PeekGeometry,
    val rowHeight: Float,
    val thumb: Float,
    val coverHeight: Float,
    val heroHeight: Float,
    val textWidth: Float,
    /** x, y in the row, the preview card and the page. */
    val titleX: FloatArray,
    val titleY: FloatArray,
    val metaY: FloatArray,
    val contentY: FloatArray,
    val contentScale: Float,
    /** The title's scale in the preview card (never wider than the card's text column). */
    val titleCard: Float,
    val actions: PeekRect,
    val footer: Float?,
    val navTop: Float,
) {
    val rows get() = geometry.rows
    val card get() = geometry.card
    val full get() = geometry.full
}

private fun measure(width: Float, height: Float, top: Float, bottom: Float, count: Int): Metrics {
    val column = min(width, MAX_COLUMN)
    val ox = (width - column) / 2f
    val listTop = top + 8f + HEADER
    val room = height - max(bottom, 12f) - listTop
    val gaps = ROW_GAP * (count - 1)
    var rowHeight = floor((room - FOOT - gaps) / count)
    var footer: Float? = null
    if (rowHeight >= ROW_MIN) {
        rowHeight = min(rowHeight, ROW_MAX)
        val end = listTop + rowHeight * count + gaps
        footer = end + (height - max(bottom, 12f) - end - 48f) / 2f
    } else {
        rowHeight = min(floor((room - gaps) / count), ROW_MAX)
    }
    val rows = List(count) { i -> PeekRect(ox + SIDE, listTop + i * (rowHeight + ROW_GAP), column - SIDE * 2f, rowHeight) }
    val thumb = rowHeight - 24f
    val textWidth = column - PAGE_PAD * 2f

    val cardWidth = min(column - CARD_SIDE * 2f, CARD_MAX)
    val coverHeight = min(cardWidth * 0.5f, height * 0.21f).roundToInt().toFloat()
    val contentScale = (cardWidth - CARD_PAD * 2f) / textWidth
    val titleScale = min(TITLE_CARD, contentScale)
    val titleCard = coverHeight + 14f
    val metaCard = titleCard + (TITLE_LINE * 2f * titleScale).roundToInt() + 5f
    val contentCard = metaCard + META_LINE + 10f
    val cardHeight = contentCard + (EXCERPT_LINES * BODY_LINE * contentScale).roundToInt() + 16f
    val actionsHeight = ACTION_ROW * PeekAction.entries.size
    val stack = cardHeight + ACTIONS_GAP + actionsHeight
    val cardTop = top + max(8f, (height - top - bottom - stack) / 2f)
    val card = PeekRect((width - cardWidth) / 2f, cardTop, cardWidth, cardHeight)

    val heroHeight = (height * 0.36f).roundToInt().toFloat()
    val titleFull = heroHeight + 20f
    val metaFull = titleFull + TITLE_LINE * 2f + 8f
    val titleRowLines = (TITLE_LINE * 2f * TITLE_ROW).roundToInt().toFloat()
    val titleRow = ((rowHeight - (titleRowLines + 4f + META_LINE)) / 2f).roundToInt().toFloat()
    return Metrics(
        width = width,
        height = height,
        geometry = PeekGeometry(rows, card, PeekRect(0f, 0f, width, height)),
        rowHeight = rowHeight,
        thumb = thumb,
        coverHeight = coverHeight,
        heroHeight = heroHeight,
        textWidth = textWidth,
        titleX = floatArrayOf(COVER_INSET + thumb + 12f, CARD_PAD, ox + PAGE_PAD),
        titleY = floatArrayOf(titleRow, titleCard, titleFull),
        metaY = floatArrayOf(titleRow + titleRowLines + 4f, metaCard, metaFull),
        contentY = floatArrayOf(contentCard, metaFull + META_LINE + 18f),
        contentScale = contentScale,
        titleCard = titleScale,
        actions = PeekRect(card.x, card.y + cardHeight + ACTIONS_GAP, min(ACTIONS_WIDTH, cardWidth), actionsHeight),
        footer = footer,
        navTop = top + 4f,
    )
}

/** The autoPlay clock, and what a touch does to it. Stepped once per frame; nothing here recomposes. */
private class PeekPlayer(private val state: PeekState, count: Int) {
    var clock by mutableFloatStateOf(0f)
    private var mode = MODE_AUTO
    private var idle = 0f
    private var seen = 0

    /** The height of each article's text at the page's size, for how far the page scrolls. */
    val textHeights = FloatArray(count) { 900f }

    fun restart(playing: Boolean) {
        clock = 0f
        mode = if (playing) MODE_RETURN else MODE_MANUAL
        state.scripted = 0
        state.peekClose(0f, true)
    }

    fun frame(dt: Float, playing: Boolean, reduced: Boolean, m: Metrics, pageBottom: Float, rowA: Int, rowB: Int) {
        state.tick(dt)
        val out = state.active
        if (out >= 0) {
            val text = textHeights.getOrElse(out) { 900f }
            state.maxScroll = max(0f, m.contentY[1] + text + pageBottom - m.height)
        }

        // A touch takes over: the script lets go and whatever is out settles where it is nearest to.
        if (state.touches != seen) {
            seen = state.touches
            idle = 0f
            if (state.scripted == 1) {
                state.scripted = 0
                state.peekSettle(reduced)
            }
            mode = MODE_MANUAL
        }
        if (mode == MODE_MANUAL) {
            if (!playing) return
            if (state.held == 0) idle += dt
            if (idle >= RESUME_AFTER) {
                state.peekClose(0f, false)
                mode = MODE_RETURN
            }
            return
        }
        if (mode == MODE_RETURN) {
            // Back at rest: the loop starts again from 0.
            if (state.active < 0 && playing) {
                clock = 0f
                state.scripted = 1
                mode = MODE_AUTO
            }
            return
        }
        if (!playing) return

        var t = clock + dt
        if (t >= LOOP) t -= LOOP
        clock = t
        val row = if (scriptRow(t) == ROW_A) rowA else rowB
        val pressed = scriptPress(t)
        val peeked = scriptPeek(t)
        val popped = scriptPop(t)
        state.press.set(pressed)
        state.peek.set(peeked)
        state.sink.set(0f)
        state.pop.set(popped)
        state.reveal.set(scriptReveal(t))
        state.scroll.set(min(scriptScroll(t), state.maxScroll))
        state.wants = false
        state.active = if (pressed + abs(peeked) + abs(popped) > 0.0005f) row else -1
    }
}

/** The ghost finger at loop time t: x, y, opacity, pressed 0..1. */
private fun ghostAt(t: Float, ax: Float, ay: Float, bx: Float, by: Float, sx: Float, sy: Float, dx: Float, dy: Float): FloatArray {
    if (t < T.releaseA + 0.45f) {
        val shown = seg(t, T.appearA, T.appearA + GHOST_FADE) * (1f - seg(t, T.releaseA, T.releaseA + GHOST_FADE))
        return floatArrayOf(ax, ay - GHOST_SWIPE_UP * ghostPull(seg(t, T.swipeA, T.releaseA)), shown, seg(t, T.downA - 0.08f, T.downA))
    }
    if (t < T.scrollEnd + 0.4f) {
        val shown = seg(t, T.scrollStart - 0.3f, T.scrollStart - 0.12f) *
            (1f - seg(t, T.scrollEnd + 0.05f, T.scrollEnd + 0.05f + GHOST_FADE))
        return floatArrayOf(sx, sy - GHOST_SCROLL * easeInOutCubic(seg(t, T.scrollStart, T.scrollEnd)), shown, seg(t, T.scrollStart - 0.1f, T.scrollStart))
    }
    if (t < T.releaseD + 0.45f) {
        val shown = seg(t, T.pullStart - 0.3f, T.pullStart - 0.12f) * (1f - seg(t, T.releaseD, T.releaseD + GHOST_FADE))
        return floatArrayOf(dx, dy + GHOST_DISMISS * ghostPull(seg(t, T.pullStart, T.releaseD)), shown, seg(t, T.pullStart - 0.1f, T.pullStart))
    }
    val shown = seg(t, T.appearB, T.appearB + GHOST_FADE) * (1f - seg(t, T.releaseB, T.releaseB + GHOST_FADE))
    return floatArrayOf(bx, by + GHOST_SINK * ghostPull(seg(t, T.pullB, T.releaseB)), shown, seg(t, T.downB - 0.08f, T.downB))
}

/** Dp sizes, so the one layout the three states share is not resized by the system's font scale. */
private fun Density.textStyle(
    size: Float,
    line: Float,
    weight: FontWeight,
    color: Color,
    tracking: Float = 0f,
    tabular: Boolean = false,
) = TextStyle(
    fontSize = size.dp.toSp(),
    lineHeight = line.dp.toSp(),
    fontWeight = weight,
    color = color,
    letterSpacing = tracking.dp.toSp(),
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private class PeekStyles(density: Density) {
    val brand = density.textStyle(15f, 20f, FontWeight.Bold, LEAF, -0.2f)
    val edit = density.textStyle(16f, 20f, FontWeight.Medium, LEAF)
    val heading = density.textStyle(33f, 38f, FontWeight.ExtraBold, INK, -0.8f)
    val count = density.textStyle(14f, 18f, FontWeight.Medium, INK_SOFT, tabular = true)
    val title = density.textStyle(TITLE_SIZE, TITLE_LINE, FontWeight.ExtraBold, INK, -0.6f)
    val meta = density.textStyle(13f, META_LINE, FontWeight.Medium, INK_SOFT, tabular = true)
    val topic = density.textStyle(12f, META_LINE, FontWeight.SemiBold, LEAF)
    val body = density.textStyle(BODY_SIZE, BODY_LINE, FontWeight.Normal, INK_BODY, -0.2f)
    val add = density.textStyle(16f, 20f, FontWeight.SemiBold, SHEET, -0.2f)
    val action = density.textStyle(17f, 22f, FontWeight.Normal, INK, -0.3f)
}

/** Measures at a fixed width and takes no room, so a layer can place it anywhere over its parent. */
private fun Modifier.floating(widthDp: Float): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixedWidth(widthDp.dp.roundToPx()))
    layout(0, 0) { placeable.placeRelative(0, 0) }
}

private fun metaLine(article: PeekArticle) = "${article.source} · ${article.minutes} min read"

/**
 * A reading list: long-press a row and it lifts into a preview card with quick actions; swipe up and the card becomes
 * the page. Plays a 10.5 s loop until a finger takes over and resumes 2 s after the last touch.
 */
@Composable
fun PeekPopPreview(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    articles: List<PeekArticle> = PEEK_ARTICLES,
    onOpen: ((PeekArticle) -> Unit)? = null,
    onAction: ((PeekAction, PeekArticle) -> Unit)? = null,
) {
    val reducedMotion = rememberLiveReducedMotion()
    val playing = autoPlay && !reducedMotion
    val state = remember { PeekState() }
    val player = remember(articles.size) { PeekPlayer(state, articles.size) }
    val view = LocalView.current
    val impact = remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); Unit } }

    // Over the open page's cover the status bar turns light; it flips once, as the page takes the screen.
    val onPage by remember { derivedStateOf { state.pop.value > 0.6f } }
    BlockSystemBars(darkIcons = !onPage)

    BoxWithConstraints(modifier.fillMaxSize().background(DESK).clipToBounds()) {
        val density = LocalDensity.current
        val bars = WindowInsets.systemBars
        val top = bars.getTop(density) / density.density
        val bottom = bars.getBottom(density) / density.density
        val width = maxWidth.value
        val height = maxHeight.value
        val m = remember(width, height, top, bottom, articles.size) { measure(width, height, top, bottom, articles.size) }
        val styles = remember(density) { PeekStyles(density) }
        val painters = remember(articles, m.width, m.heroHeight) {
            articles.map { CoverPainter(it.cover, m.width, m.heroHeight) }
        }
        val rowA = min(ROW_A, articles.lastIndex)
        val rowB = min(ROW_B, articles.lastIndex)
        val pageBottom = max(bottom, 12f) + 28f

        // Everything the long-lived frame loop and touch handler read goes through state, never a captured val.
        val metrics by rememberUpdatedState(m)
        val reduced by rememberUpdatedState(reducedMotion)
        val currentArticles by rememberUpdatedState(articles)
        val opened by rememberUpdatedState(onOpen)
        val acted by rememberUpdatedState(onAction)
        val pageEnd by rememberUpdatedState(pageBottom)
        val firstRow by rememberUpdatedState(rowA)
        val secondRow by rememberUpdatedState(rowB)

        LaunchedEffect(playing, player) {
            player.restart(playing)
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0L) 0.016f else min((now - last) / 1_000_000_000f, 0.048f)
                    last = now
                    if (dt > 0f) player.frame(dt, playing, reduced, metrics, pageEnd, firstRow, secondRow)
                }
            }
        }

        var pressedAction by remember { mutableIntStateOf(-1) }
        val closeFromTouch = remember(state) {
            {
                state.peekTouch(reduced)
                state.peekClose(0f, reduced)
            }
        }
        val runAction = remember(state, impact) {
            { index: Int ->
                impact()
                currentArticles.getOrNull(state.active)?.let { article -> acted?.invoke(PeekAction.entries[index], article) }
                closeFromTouch()
            }
        }
        val runBack = remember(state, impact) {
            {
                impact()
                closeFromTouch()
            }
        }
        val targets = remember(state, impact) {
            PeekTouchTargets(
                geometry = { metrics.geometry },
                reduced = { reduced },
                back = { PeekRect(metrics.titleX[2] - 6f, metrics.navTop, NAV, NAV) },
                actions = { metrics.actions },
                actionCount = PeekAction.entries.size,
                onPressedAction = { pressedAction = it },
                onPeek = impact,
                onPop = {
                    impact()
                    currentArticles.getOrNull(state.active)?.let { article -> opened?.invoke(article) }
                },
                onClose = impact,
                onBack = runBack,
                onAction = runAction,
            )
        }
        val actionItems = remember {
            listOf(
                PeekActionItem(PeekAction.Save.label, Icons.Outlined.ArrowCircleDown, PeekAction.Save.destructive),
                PeekActionItem(PeekAction.Share.label, Icons.Outlined.IosShare, PeekAction.Share.destructive),
                PeekActionItem(PeekAction.Archive.label, Icons.Outlined.Archive, PeekAction.Archive.destructive),
            )
        }
        val minutes = articles.sumOf { it.minutes }

        Box(Modifier.fillMaxSize().peekGestures(state, targets)) {
            PeekScene(state) {
                ListHeader(m, top, styles, "${articles.size} saved · $minutes min of reading")
                articles.forEachIndexed { i, article ->
                    ArticleRow(article, i, m, state, painters[i], styles) {
                        state.peekTouch(reduced)
                        state.active = i
                        state.peekPop(0f, reduced)
                        impact()
                        opened?.invoke(article)
                    }
                }
                m.footer?.let { y -> AddPill(y, m.width, styles) }
            }

            PeekBackdrop(state)
            PeekActions(
                state = state,
                rect = m.actions,
                items = actionItems,
                pressed = pressedAction,
                style = styles.action,
                ink = INK,
                danger = DANGER,
                backgroundColor = SHEET,
                onAction = runAction,
            )
            PeekFrame(state, m.geometry, SHEET) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = -state.scroll.value * this.density },
                ) {
                    articles.forEachIndexed { i, article ->
                        MorphFace(article, i, m, state, painters[i], styles) { h -> player.textHeights[i] = h }
                    }
                }
                CardFade(state, m)
                PageNav(state, m, runBack)
            }
            Ghost(state, player, m)
        }
    }
}

@Composable
private fun ListHeader(m: Metrics, top: Float, styles: PeekStyles, count: String) {
    val first = m.rows[0]
    Column(
        Modifier
            .offset(first.x.dp, (top + 8f).dp)
            .width(first.width.dp)
            .height(HEADER.dp)
            .padding(horizontal = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(PeekCopy.BRAND, style = styles.brand, modifier = Modifier.alignByBaseline())
            Text(PeekCopy.EDIT, style = styles.edit, modifier = Modifier.alignByBaseline())
        }
        Text(
            PeekCopy.TITLE,
            style = styles.heading,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        Text(count, style = styles.count, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A row at rest. It is drawn exactly as the frame is at peek 0, so the hand-over is invisible. */
@Composable
private fun ArticleRow(
    article: PeekArticle,
    index: Int,
    m: Metrics,
    state: PeekState,
    painter: CoverPainter,
    styles: PeekStyles,
    onOpenRow: () -> Unit,
) {
    val rect = m.rows[index]
    Box(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp)
            // While this item is out, the frame is drawing it.
            .graphicsLayer { alpha = if (state.active == index) 0f else 1f }
            .cssBoxShadow(SHADOW.copy(alpha = 0.05f), 4.dp, 10.dp, RADIUS_ROW.dp)
            .cssBoxShadow(SHADOW.copy(alpha = 0.08f), 1.dp, 2.dp, RADIUS_ROW.dp)
            .background(SHEET, RoundedCornerShape(RADIUS_ROW.dp))
            .drawBehind {
                with(painter) { drawInBox(COVER_INSET, (m.rowHeight - m.thumb) / 2f, m.thumb, m.thumb, THUMB_RADIUS) }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${article.title}, ${article.source}, ${article.minutes} minutes"
                onClick(label = "Open the article") {
                    onOpenRow()
                    true
                }
            },
    ) {
        Text(
            article.title,
            style = styles.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .floating(m.textWidth)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = m.titleX[0] * density
                    translationY = m.titleY[0] * density
                    scaleX = TITLE_ROW
                    scaleY = TITLE_ROW
                },
        )
        Text(
            metaLine(article),
            style = styles.meta,
            maxLines = 1,
            modifier = Modifier
                .floating(m.textWidth)
                .graphicsLayer {
                    translationX = m.titleX[0] * density
                    translationY = m.metaY[0] * density
                },
        )
        Text(
            article.topic,
            style = styles.topic,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp)
                .offset(y = m.metaY[0].dp),
        )
    }
}

/** The same article inside the frame: every piece travels row, preview, page on the shared values. */
@Composable
private fun MorphFace(
    article: PeekArticle,
    index: Int,
    m: Metrics,
    state: PeekState,
    painter: CoverPainter,
    styles: PeekStyles,
    onTextHeight: (Float) -> Unit,
) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = if (state.active == index) 1f else 0f }
            .drawBehind {
                if (state.active != index) return@drawBehind
                val pk = state.pk
                val pp = state.pop.value
                // The drawing always covers its box, centred: the cover grows as a shared element.
                val w = max(1f, mix3(m.thumb, m.card.width, m.full.width, pk, pp))
                val h = max(1f, mix3(m.thumb, m.coverHeight, m.heroHeight, pk, pp))
                val radius = max(0f, mix3(THUMB_RADIUS, 0f, 0f, clampTo(pk, 0f, 1f), clampTo(pp, 0f, 1f)))
                val x = mix3(COVER_INSET, 0f, 0f, pk, pp)
                val y = mix3((m.rowHeight - m.thumb) / 2f, 0f, 0f, pk, pp)
                with(painter) { drawInBox(x, y, w, h, radius) }
            },
    ) {
        Text(
            article.title,
            style = styles.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .floating(m.textWidth)
                .graphicsLayer {
                    val pk = state.pk
                    val pp = state.pop.value
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = mix3(m.titleX[0], m.titleX[1], m.titleX[2], pk, pp) * density
                    translationY = mix3(m.titleY[0], m.titleY[1], m.titleY[2], pk, pp) * density
                    val s = mix3(TITLE_ROW, m.titleCard, 1f, pk, pp)
                    scaleX = s
                    scaleY = s
                },
        )
        Text(
            metaLine(article),
            style = styles.meta,
            maxLines = 1,
            modifier = Modifier
                .floating(m.textWidth)
                .graphicsLayer {
                    val pk = state.pk
                    val pp = state.pop.value
                    translationX = mix3(m.titleX[0], m.titleX[1], m.titleX[2], pk, pp) * density
                    translationY = mix3(m.metaY[0], m.metaY[1], m.metaY[2], pk, pp) * density
                },
        )
        // Pinned 14 from the frame's right edge, on the row's meta line; it fades as the row lifts.
        Text(
            article.topic,
            style = styles.topic,
            maxLines = 1,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) { placeable.placeRelative(0, 0) }
                }
                .graphicsLayer {
                    alpha = 1f - seg(state.pk, 0f, 0.3f)
                    val frameWidth = state.frameRect(m.geometry).width
                    translationX = (frameWidth - 14f) * density - size.width
                    translationY = m.metaY[0] * density
                },
        )
        // The first paragraph comes out of a blur as the preview lands; on the page it is simply there.
        Column(
            Modifier
                .floating(m.textWidth)
                .onSizeChanged { onTextHeight(it.height / density) }
                .graphicsLayer {
                    val pk = state.pk
                    val pp = state.pop.value
                    val lands = easeOutCubic(seg(state.reveal.value, EXCERPT_AT, EXCERPT_AT + REVEAL_SPAN)) * seg(pk, 0.45f, 0.8f)
                    val u = max(lands, seg(pp, 0f, 0.3f))
                    alpha = u
                    val blur = (1f - u) * EXCERPT_BLUR * density
                    renderEffect = if (u > 0f && blur > 0.25f) BlurEffect(blur, blur, TileMode.Decal) else null
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = mix3(m.titleX[1], m.titleX[1], m.titleX[2], pk, pp) * density
                    translationY = mix3(m.contentY[0] - 10f, m.contentY[0], m.contentY[1], pk, pp) * density
                    val s = mix3(m.contentScale, m.contentScale, 1f, pk, pp)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Text(article.excerpt, style = styles.body)
            article.body.forEach { paragraph ->
                Spacer(Modifier.height(16.dp))
                Text(paragraph, style = styles.body)
            }
        }
    }
}

/** The preview's last line fades into the card; drawn along the frame's bottom edge. */
@Composable
private fun CardFade(state: PeekState, m: Metrics) {
    val brushColors = remember { listOf(SHEET.copy(alpha = 0f), SHEET.copy(alpha = 0.92f), SHEET) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val opacity = seg(state.pk, 0.4f, 0.8f) * (1f - seg(state.pop.value, 0.05f, 0.45f))
                if (opacity <= 0f) return@drawBehind
                val r = state.frameRect(m.geometry)
                val h = 56f * density
                val top = r.height * density - h
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to brushColors[0],
                        0.6f to brushColors[1],
                        1f to brushColors[2],
                        startY = top,
                        endY = top + h,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(r.width * density, h),
                    alpha = opacity,
                )
            },
    )
}

/** The page's two round buttons over a scrim; they settle in late in the pop. Touch comes through peekGestures. */
@Composable
private fun PageNav(state: PeekState, m: Metrics, onBack: () -> Unit) {
    val inset = m.titleX[2] - 6f
    Box(
        Modifier
            .fillMaxWidth()
            .height((m.navTop + NAV + 28f).dp)
            .then(if (state.active < 0) Modifier.clearAndSetSemantics { } else Modifier)
            .graphicsLayer {
                val u = easeOutCubic(seg(state.pop.value, NAV_AT_FROM, NAV_AT_TO))
                alpha = u
                translationY = -NAV_DROP * (1f - u) * density
            }
            .background(Brush.verticalGradient(listOf(Color(10 / 255f, 10 / 255f, 12 / 255f, 0.36f), Color(10 / 255f, 10 / 255f, 12 / 255f, 0f)))),
    ) {
        NavButton(
            Modifier
                .offset(inset.dp, m.navTop.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Back to ${PeekCopy.BACK}"
                    onClick {
                        if (state.pop.value <= 0.8f) return@onClick false
                        onBack()
                        true
                    }
                },
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = INK, modifier = Modifier.size(24.dp))
        }
        NavButton(
            Modifier
                .offset(y = m.navTop.dp)
                .graphicsLayer {
                    translationX = (state.frameRect(m.geometry).width - inset - NAV) * density
                },
        ) {
            Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = INK, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun NavButton(modifier: Modifier, icon: @Composable () -> Unit) {
    Box(
        modifier
            .size(NAV.dp)
            .cssBoxShadow(Color(10 / 255f, 10 / 255f, 12 / 255f, 0.14f), 6.dp, 14.dp, (NAV / 2f).dp)
            .cssBoxShadow(Color(10 / 255f, 10 / 255f, 12 / 255f, 0.16f), 1.dp, 2.dp, (NAV / 2f).dp)
            .background(Color(253 / 255f, 252 / 255f, 249 / 255f, 0.90f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun AddPill(y: Float, width: Float, styles: PeekStyles) {
    Row(
        Modifier
            .offset(((width - 164f) / 2f).dp, y.dp)
            .size(164.dp, 48.dp)
            .cssBoxShadow(SHADOW.copy(alpha = 0.18f), 10.dp, 22.dp, 24.dp)
            .cssBoxShadow(SHADOW.copy(alpha = 0.20f), 1.dp, 2.dp, 24.dp)
            .background(INK, RoundedCornerShape(24.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = SHEET, modifier = Modifier.size(18.dp))
        Text(PeekCopy.ADD, style = styles.add, maxLines = 1)
    }
}

@Composable
private fun Ghost(state: PeekState, player: PeekPlayer, m: Metrics) {
    val rowA = m.rows[min(ROW_A, m.rows.lastIndex)]
    val rowB = m.rows[min(ROW_B, m.rows.lastIndex)]
    Box(
        Modifier
            .size(GHOST_SIZE.dp)
            .graphicsLayer {
                val g = ghostAt(
                    player.clock,
                    rowA.x + rowA.width * 0.55f,
                    rowA.y + rowA.height / 2f,
                    rowB.x + rowB.width * 0.7f,
                    rowB.y + rowB.height / 2f,
                    m.width * 0.62f,
                    m.height * 0.66f,
                    m.width * 0.5f,
                    m.height * 0.3f,
                )
                alpha = if (state.scripted == 1) g[2] else 0f
                translationX = (g[0] - GHOST_SIZE / 2f) * density
                translationY = (g[1] - GHOST_SIZE / 2f) * density
                val s = 1f - GHOST_PRESS * g[3]
                scaleX = s
                scaleY = s
            }
            .background(Color(26 / 255f, 27 / 255f, 30 / 255f, 0.20f), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    )
}
