package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ForYouInsightsFeed(
    width: Double,
    scale: Double,
    safeTop: Double,
    bottomInset: Double,
    section: ForYouInsightsSection,
    reducedMotion: Boolean,
    readerLive: Boolean,
    activeArticle: Int,
    frame: State<ForYouInsightsFrame>,
    assets: ForYouInsightsAssets,
    feedScroll: androidx.compose.foundation.ScrollState,
    onMore: () -> Unit,
    onPressRow: (Int, Boolean) -> Unit,
    onOpenRow: (Int, Double) -> Unit,
) {
    val feedHeight = forYouInsightsFeedY(
        ForYouInsightsTheme.Layout.ROW_TOP +
            ForYouInsightsTheme.Layout.ROW_PITCH * ForYouInsightsContent.articles.size,
        safeTop,
        scale,
    ) + (ForYouInsightsTheme.Layout.TAB_BAR_HEIGHT + ForYouInsightsTheme.Space.SCREEN) * scale +
        bottomInset
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (reducedMotion) {
                    scaleX = 1f
                    scaleY = 1f
                    return@graphicsLayer
                }
                val progress = frame.value.progress
                val amount = ForYouInsightsMotion.smooth(
                    ForYouInsightsMotion.seg(progress, 0.05, 0.8),
                )
                val value = ForYouInsightsMotion.lerp(
                    1.0,
                    ForYouInsightsMotion.Reader.FEED_SCALE,
                    amount,
                ).toFloat()
                scaleX = value
                scaleY = value
            }
            .semantics {
                if (readerLive) {
                    invisibleToUser()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(feedScroll, enabled = !readerLive),
        ) {
            Box(
                Modifier
                    .width(width.dp)
                    .height(feedHeight.dp),
            ) {
                ForYouInsightsSectionLayer(
                    visible = section == ForYouInsightsSection.INSIGHTS,
                    modifier = Modifier.fillMaxSize(),
                ) {
                ForYouInsightsRail(
                    scale = scale,
                    top = forYouInsightsFeedY(ForYouInsightsTheme.Layout.RAIL_TOP, safeTop, scale),
                )
                ForYouInsightsDivider(
                    left = 0.0,
                    top = forYouInsightsFeedY(
                        ForYouInsightsTheme.Layout.RAIL_DIVIDER,
                        safeTop,
                        scale,
                    ),
                    width = width,
                )
                ForYouInsightsHeading(
                    title = "For You",
                    scale = scale,
                    safeTop = safeTop,
                )
                Row(
                    Modifier
                        .offset(
                            x = ((ForYouInsightsTheme.Layout.COLUMN +
                                ForYouInsightsTheme.Layout.LABEL_INSET) * scale).dp,
                            y = forYouInsightsFeedY(
                                ForYouInsightsTheme.Layout.LABEL_TOP,
                                safeTop,
                                scale,
                            ).dp,
                        )
                        .height((ForYouInsightsTheme.Layout.LABEL_HEIGHT * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        (ForYouInsightsTheme.Layout.LABEL_ICON_GAP * scale).dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = ForYouInsightsTheme.Colors.pink,
                        modifier = Modifier.size(
                            (ForYouInsightsTheme.Layout.LABEL_ICON * scale).dp,
                        ),
                    )
                    ForYouInsightsText(
                        text = ForYouInsightsContent.label,
                        style = ForYouInsightsTheme.Type.label,
                        color = ForYouInsightsTheme.Colors.pink,
                        scale = scale,
                    )
                }
                ForYouInsightsMoreButton(
                    scale = scale,
                    left = (ForYouInsightsTheme.Layout.ELLIPSIS_CENTER_X - 22.0) * scale,
                    top = forYouInsightsFeedY(
                        ForYouInsightsTheme.Layout.ELLIPSIS_CENTER_Y,
                        safeTop,
                        scale,
                    ) - 22.0 * scale,
                    enabled = section == ForYouInsightsSection.INSIGHTS && !readerLive,
                    onClick = onMore,
                )
                ForYouInsightsTip(
                    scale = scale.toFloat(),
                    frame = frame,
                    modifier = Modifier
                        .offset(
                            x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                            y = forYouInsightsFeedY(
                                ForYouInsightsTheme.Layout.TIP_TOP,
                                safeTop,
                                scale,
                            ).dp,
                        )
                        .size(
                            width = (ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale).dp,
                            height = (ForYouInsightsTheme.Layout.TIP_LINES *
                                (ForYouInsightsTheme.Type.tip.line ?: 27.0) * scale).dp,
                        ),
                )
                ForYouInsightsDivider(
                    left = ForYouInsightsTheme.Layout.COLUMN * scale,
                    top = forYouInsightsFeedY(
                        ForYouInsightsTheme.Layout.TIP_DIVIDER,
                        safeTop,
                        scale,
                    ),
                    width = ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale,
                )
                ForYouInsightsRows(
                    base = ForYouInsightsTheme.Layout.ROW_TOP,
                    scale = scale,
                    safeTop = safeTop,
                    visible = section == ForYouInsightsSection.INSIGHTS,
                    activeArticle = activeArticle,
                    frame = frame,
                    assets = assets,
                    onPressRow = onPressRow,
                    onOpenRow = onOpenRow,
                )
            }

                ForYouInsightsSectionLayer(
                    visible = section == ForYouInsightsSection.LONGEVITY,
                    modifier = Modifier.fillMaxSize(),
                ) {
                ForYouInsightsHeading(
                    title = "Longevity",
                    scale = scale,
                    safeTop = safeTop,
                )
                ForYouInsightsText(
                    text = "Small habits that support a steadier day",
                    style = ForYouInsightsTheme.Type.sectionSubhead,
                    color = ForYouInsightsTheme.Colors.secondary,
                    scale = scale,
                    modifier = Modifier.offset(
                        x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                        y = forYouInsightsFeedY(
                            ForYouInsightsTheme.Layout.LABEL_TOP,
                            safeTop,
                            scale,
                        ).dp,
                    ),
                )
                ForYouInsightsHabits(scale = scale, safeTop = safeTop)
            }

                ForYouInsightsSectionLayer(
                    visible = section == ForYouInsightsSection.BROWSE,
                    modifier = Modifier.fillMaxSize(),
                ) {
                ForYouInsightsHeading(
                    title = "Browse",
                    scale = scale,
                    safeTop = safeTop,
                )
                ForYouInsightsText(
                    text = "Guides for everyday routines",
                    style = ForYouInsightsTheme.Type.sectionSubhead,
                    color = ForYouInsightsTheme.Colors.secondary,
                    scale = scale,
                    modifier = Modifier.offset(
                        x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                        y = forYouInsightsFeedY(
                            ForYouInsightsTheme.Layout.LABEL_TOP,
                            safeTop,
                            scale,
                        ).dp,
                    ),
                )
                ForYouInsightsRows(
                    base = ForYouInsightsTheme.Layout.BROWSE_ROW_TOP,
                    scale = scale,
                    safeTop = safeTop,
                    visible = section == ForYouInsightsSection.BROWSE,
                    activeArticle = activeArticle,
                    frame = frame,
                    assets = assets,
                    onPressRow = onPressRow,
                    onOpenRow = onOpenRow,
                )
            }
        }
    }
}
}

@Composable
private fun ForYouInsightsSectionLayer(
    visible: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .semantics {
                if (!visible) {
                    invisibleToUser()
                }
            },
    ) {
        content()
    }
}

@Composable
private fun ForYouInsightsHeading(
    title: String,
    scale: Double,
    safeTop: Double,
) {
    ForYouInsightsText(
        text = title,
        style = ForYouInsightsTheme.Type.heading,
        color = ForYouInsightsTheme.Colors.ink,
        scale = scale,
        modifier = Modifier
            .offset(
                x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                y = forYouInsightsFeedY(
                    ForYouInsightsTheme.Layout.HEADING_TOP,
                    safeTop,
                    scale,
                ).dp,
            )
            .semantics { heading() },
    )
    ForYouInsightsDivider(
        left = ForYouInsightsTheme.Layout.COLUMN * scale,
        top = forYouInsightsFeedY(
            ForYouInsightsTheme.Layout.HEADING_DIVIDER,
            safeTop,
            scale,
        ),
        width = ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale,
    )
}

@Composable
private fun ForYouInsightsRows(
    base: Double,
    scale: Double,
    safeTop: Double,
    visible: Boolean,
    activeArticle: Int,
    frame: State<ForYouInsightsFrame>,
    assets: ForYouInsightsAssets,
    onPressRow: (Int, Boolean) -> Unit,
    onOpenRow: (Int, Double) -> Unit,
) {
    ForYouInsightsContent.articles.forEachIndexed { index, article ->
        ForYouInsightsArticleRow(
            index = index,
            article = article,
            top = base + ForYouInsightsTheme.Layout.ROW_PITCH * index,
            base = base,
            scale = scale,
            safeTop = safeTop,
            visible = visible,
            activeArticle = activeArticle,
            frame = frame,
            image = assets.flats[index],
            onPress = { down -> onPressRow(index, down) },
            onOpen = { onOpenRow(index, base) },
        )
    }
}

@Composable
private fun ForYouInsightsArticleRow(
    index: Int,
    article: ForYouInsightsArticle,
    top: Double,
    base: Double,
    scale: Double,
    safeTop: Double,
    visible: Boolean,
    activeArticle: Int,
    frame: State<ForYouInsightsFrame>,
    image: androidx.compose.ui.graphics.ImageBitmap,
    onPress: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val rowTop = forYouInsightsFeedY(top, safeTop, scale)
    ForYouInsightsText(
        text = article.title,
        style = ForYouInsightsTheme.Type.rowTitle,
        color = ForYouInsightsTheme.Colors.ink,
        scale = scale,
        width = ForYouInsightsTheme.Layout.ROW_TEXT_WIDTH * scale,
        maxLines = 3,
        modifier = Modifier
            .offset(
                x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                y = rowTop.dp,
            )
            .graphicsLayer {
                val current = frame.value
                val progress = if (current.actorIndex == index) {
                    ForYouInsightsMotion.smooth(
                        ForYouInsightsMotion.seg(
                            current.progress,
                            ForYouInsightsMotion.Reader.ROW_TITLE_OUT.first,
                            ForYouInsightsMotion.Reader.ROW_TITLE_OUT.second,
                        ),
                    )
                } else {
                    0.0
                }
                alpha = (1.0 - progress).toFloat()
                translationY = (-ForYouInsightsMotion.Reader.ROW_TITLE_RISE *
                    scale * progress).dp.toPx()
            },
    )
    ForYouInsightsText(
        text = article.summary,
        style = ForYouInsightsTheme.Type.rowSummary,
        color = ForYouInsightsTheme.Colors.tertiary,
        scale = scale,
        width = ForYouInsightsTheme.Layout.ROW_TEXT_WIDTH * scale,
        maxLines = 3,
        modifier = Modifier.offset(
            x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
            y = forYouInsightsFeedY(
                top + ForYouInsightsTheme.Layout.ROW_SUMMARY_OFFSET,
                safeTop,
                scale,
            ).dp,
        ),
    )
    Row(
        Modifier.offset(
            x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
            y = forYouInsightsFeedY(
                top + ForYouInsightsTheme.Layout.ROW_META_OFFSET,
                safeTop,
                scale,
            ).dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(
            (ForYouInsightsTheme.Layout.ROW_META_GAP * scale).dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            tint = ForYouInsightsTheme.Colors.meta,
            modifier = Modifier.size((ForYouInsightsTheme.Layout.ROW_META_ICON * scale).dp),
        )
        ForYouInsightsText(
            text = article.readTime,
            style = ForYouInsightsTheme.Type.rowMeta,
            color = ForYouInsightsTheme.Colors.meta,
            scale = scale,
        )
    }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        modifier = Modifier
            .offset(
                x = (ForYouInsightsTheme.Layout.ROW_IMAGE_X * scale).dp,
                y = rowTop.dp,
            )
            .size((ForYouInsightsTheme.Layout.ROW_IMAGE_SIZE * scale).dp)
            .graphicsLayer {
                val current = frame.value
                val mine = current.actorIndex == index
                alpha = if (mine && current.progress > ForYouInsightsMotion.Reader.HAND_OFF) {
                    0f
                } else {
                    1f
                }
                val press = if (mine) {
                    1.0 - (1.0 - ForYouInsightsMotion.PRESS_SCALE) * current.press *
                        (1.0 - ForYouInsightsMotion.smooth(
                            ForYouInsightsMotion.seg(current.progress, 0.0, 0.5),
                        ))
                } else {
                    1.0
                }
                scaleX = press.toFloat()
                scaleY = press.toFloat()
            }
            .clip(
                RoundedCornerShape(
                    (ForYouInsightsTheme.Radius.IMAGE * scale).dp,
                ),
            ),
    )
    Box(
        Modifier
            .offset(
                x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                y = (rowTop - ForYouInsightsTheme.Space.MD * scale).dp,
            )
            .size(
                width = (ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale).dp,
                height = (ForYouInsightsTheme.Layout.ROW_PITCH * scale).dp,
            )
            .pointerInput(index, visible) {
                if (!visible) {
                    return@pointerInput
                }
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress(true)
                    val up = waitForUpOrCancellation()
                    onPress(false)
                    if (up != null) {
                        onOpen()
                    }
                }
            }
            .semantics {
                if (!visible) {
                    invisibleToUser()
                } else {
                    role = Role.Button
                    contentDescription = "${article.title}. ${article.summary} ${article.readTime}."
                    onClick(action = {
                        onOpen()
                        true
                    })
                }
            },
    )
    ForYouInsightsDivider(
        left = ForYouInsightsTheme.Layout.COLUMN * scale,
        top = forYouInsightsFeedY(
            top + ForYouInsightsTheme.Layout.ROW_DIVIDER_OFFSET,
            safeTop,
            scale,
        ),
        width = ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale,
    )
}

@Composable
private fun ForYouInsightsHabits(
    scale: Double,
    safeTop: Double,
) {
    ForYouInsightsContent.habits.forEachIndexed { index, habit ->
        val top = forYouInsightsFeedY(
            ForYouInsightsTheme.Layout.HABIT_TOP +
                ForYouInsightsTheme.Layout.HABIT_PITCH * index,
            safeTop,
            scale,
        )
        Row(
            Modifier
                .offset(
                    x = (ForYouInsightsTheme.Layout.COLUMN * scale).dp,
                    y = top.dp,
                )
                .size(
                    width = (ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale).dp,
                    height = (ForYouInsightsTheme.Layout.HABIT_PITCH * scale).dp,
                )
                .padding(top = (ForYouInsightsTheme.Space.MD * scale).dp),
            horizontalArrangement = Arrangement.spacedBy(
                (ForYouInsightsTheme.Space.MD * scale).dp,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size((ForYouInsightsTheme.Layout.HABIT_ICON * scale).dp)
                    .clip(
                        RoundedCornerShape(
                            (ForYouInsightsTheme.Radius.HABIT_ICON * scale).dp,
                        ),
                    )
                    .background(ForYouInsightsTheme.Colors.white),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (habit.icon) {
                        ForYouInsightsHabitIcon.SUNNY -> Icons.Filled.WbSunny
                        ForYouInsightsHabitIcon.LEAF -> Icons.Filled.Eco
                        ForYouInsightsHabitIcon.WALK -> Icons.Filled.DirectionsRun
                    },
                    contentDescription = null,
                    tint = Color(habit.color),
                    modifier = Modifier.size((22.0 * scale).dp),
                )
            }
            Column(
                Modifier.width(
                    ((ForYouInsightsTheme.Layout.COLUMN_WIDTH -
                        ForYouInsightsTheme.Layout.HABIT_ICON -
                        ForYouInsightsTheme.Space.MD) * scale).dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    (ForYouInsightsTheme.Space.XS * scale).dp,
                ),
            ) {
                ForYouInsightsText(
                    text = habit.title,
                    style = ForYouInsightsTheme.Type.habitTitle,
                    color = ForYouInsightsTheme.Colors.ink,
                    scale = scale,
                )
                ForYouInsightsText(
                    text = habit.body,
                    style = ForYouInsightsTheme.Type.habitBody,
                    color = ForYouInsightsTheme.Colors.secondary,
                    scale = scale,
                    maxLines = 3,
                )
            }
        }
        ForYouInsightsDivider(
            left = ForYouInsightsTheme.Layout.COLUMN * scale,
            top = top + ForYouInsightsTheme.Layout.HABIT_PITCH * scale -
                ForYouInsightsTheme.Layout.DIVIDER,
            width = ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale,
        )
    }
}

@Composable
private fun ForYouInsightsRail(
    scale: Double,
    top: Double,
) {
    Row(
        Modifier
            .offset(y = top.dp)
            .height((ForYouInsightsTheme.Layout.RAIL_HEIGHT * scale).dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = (ForYouInsightsTheme.Layout.RAIL_INSET * scale).dp)
            .semantics { contentDescription = "Today's context" },
        horizontalArrangement = Arrangement.spacedBy(
            (ForYouInsightsTheme.Layout.RAIL_GAP * scale).dp,
        ),
    ) {
        ForYouInsightsHeartTile(scale)
        ForYouInsightsMetricTile(
            title = "Sleep",
            value = "High",
            kind = ForYouInsightsMetricKind.SLEEP,
            width = ForYouInsightsTheme.Layout.TILE_WIDTHS[1],
            scale = scale,
        )
        ForYouInsightsMetricTile(
            title = "Readiness",
            value = "Ready",
            kind = ForYouInsightsMetricKind.READINESS,
            width = ForYouInsightsTheme.Layout.TILE_WIDTHS[2],
            scale = scale,
        )
        ForYouInsightsMetricTile(
            title = "Fitness",
            value = "5 of 7",
            kind = ForYouInsightsMetricKind.FITNESS,
            width = ForYouInsightsTheme.Layout.TILE_WIDTHS[3],
            scale = scale,
        )
    }
}

private enum class ForYouInsightsMetricKind {
    SLEEP,
    READINESS,
    FITNESS,
}

@Composable
private fun ForYouInsightsHeartTile(scale: Double) {
    val width = ForYouInsightsTheme.Layout.TILE_WIDTHS[0]
    Box(
        Modifier
            .size(
                width = (width * scale).dp,
                height = (ForYouInsightsTheme.Layout.RAIL_HEIGHT * scale).dp,
            )
            .shadow(
                elevation = (6.0 * scale).dp,
                shape = RoundedCornerShape((ForYouInsightsTheme.Radius.TILE * scale).dp),
                ambientColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.06f),
                spotColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.06f),
            )
            .clip(RoundedCornerShape((ForYouInsightsTheme.Radius.TILE * scale).dp))
            .background(ForYouInsightsTheme.Colors.tileHeart)
            .semantics { contentDescription = "Heart health" },
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = ForYouInsightsTheme.Colors.heart,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    y = ((ForYouInsightsTheme.Layout.HEART_TOP -
                        ForYouInsightsTheme.Layout.RAIL_TOP) * scale).dp,
                )
                .size((ForYouInsightsTheme.Layout.HEART_SIZE * scale).dp),
        )
        ForYouInsightsTheme.Layout.HEART_LINES.forEach { line ->
            Box(
                Modifier
                    .offset(
                        x = ((width - line.first) / 2.0 * scale).dp,
                        y = ((line.second - ForYouInsightsTheme.Layout.RAIL_TOP) * scale).dp,
                    )
                    .size(
                        width = (line.first * scale).dp,
                        height = (2.0 * scale).dp,
                    )
                    .clip(CircleShape)
                    .background(ForYouInsightsTheme.Colors.heartLine),
            )
        }
    }
}

@Composable
private fun ForYouInsightsMetricTile(
    title: String,
    value: String,
    kind: ForYouInsightsMetricKind,
    width: Double,
    scale: Double,
) {
    Box(
        Modifier
            .size(
                width = (width * scale).dp,
                height = (ForYouInsightsTheme.Layout.RAIL_HEIGHT * scale).dp,
            )
            .shadow(
                elevation = (6.0 * scale).dp,
                shape = RoundedCornerShape((ForYouInsightsTheme.Radius.TILE * scale).dp),
                ambientColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.06f),
                spotColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.06f),
            )
            .clip(RoundedCornerShape((ForYouInsightsTheme.Radius.TILE * scale).dp))
            .background(ForYouInsightsTheme.Colors.tile)
            .border(
                width = (0.67 * scale).dp,
                color = ForYouInsightsTheme.Colors.tileBorder,
                shape = RoundedCornerShape((ForYouInsightsTheme.Radius.TILE * scale).dp),
            )
            .semantics { contentDescription = "$title: $value" },
    ) {
        ForYouInsightsMetricRing(
            kind = kind,
            scale = scale,
            modifier = Modifier
                .offset(
                    x = (ForYouInsightsTheme.Layout.RAIL_RING_PAD * scale).dp,
                    y = ((ForYouInsightsTheme.Layout.RAIL_HEIGHT -
                        ForYouInsightsTheme.Layout.RAIL_RING) / 2.0 * scale).dp,
                )
                .size((ForYouInsightsTheme.Layout.RAIL_RING * scale).dp),
        )
        val textLeft = (ForYouInsightsTheme.Layout.RAIL_RING_PAD +
            ForYouInsightsTheme.Layout.RAIL_RING +
            ForYouInsightsTheme.Layout.RAIL_TEXT_GAP) * scale
        ForYouInsightsText(
            text = title,
            style = ForYouInsightsTheme.Type.railTitle,
            color = ForYouInsightsTheme.Colors.railTitle,
            scale = scale,
            maxLines = 1,
            modifier = Modifier.offset(
                x = textLeft.dp,
                y = ((ForYouInsightsTheme.Layout.RAIL_TITLE_TOP -
                    ForYouInsightsTheme.Layout.RAIL_TOP) * scale).dp,
            ),
        )
        ForYouInsightsText(
            text = value,
            style = ForYouInsightsTheme.Type.railValue,
            color = ForYouInsightsTheme.Colors.ink,
            scale = scale,
            maxLines = 1,
            modifier = Modifier.offset(
                x = textLeft.dp,
                y = ((ForYouInsightsTheme.Layout.RAIL_VALUE_TOP -
                    ForYouInsightsTheme.Layout.RAIL_TOP) * scale).dp,
            ),
        )
    }
}

@Composable
private fun ForYouInsightsMetricRing(
    kind: ForYouInsightsMetricKind,
    scale: Double,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 40f
            when (kind) {
                ForYouInsightsMetricKind.SLEEP -> {
                    fun arc(from: Float, to: Float, color: Color) {
                        drawArc(
                            color = color,
                            startAngle = -90f + 360f * from,
                            sweepAngle = 360f * (to - from),
                            useCenter = false,
                            topLeft = Offset(3.5f * unit, 3.5f * unit),
                            size = androidx.compose.ui.geometry.Size(33f * unit, 33f * unit),
                            style = Stroke(width = 6.5f * unit),
                        )
                    }
                    arc(0.015f, 0.2f, ForYouInsightsTheme.Colors.sleepOrange)
                    arc(0.215f, 0.72f, ForYouInsightsTheme.Colors.sleepBlue)
                    arc(0.735f, 1f, ForYouInsightsTheme.Colors.sleepTeal)
                }
                ForYouInsightsMetricKind.READINESS -> {
                    val stroke = 4f * unit
                    val rect = Offset(4f * unit, 4f * unit)
                    val arcSize = androidx.compose.ui.geometry.Size(32f * unit, 32f * unit)
                    drawArc(
                        color = ForYouInsightsTheme.Colors.readyTrack,
                        startAngle = -205f,
                        sweepAngle = 230f,
                        useCenter = false,
                        topLeft = rect,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ForYouInsightsTheme.Colors.readyLight,
                                ForYouInsightsTheme.Colors.readyStrong,
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        ),
                        startAngle = -205f,
                        sweepAngle = 138f,
                        useCenter = false,
                        topLeft = rect,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val endRadians = Math.toRadians(-67.0)
                    drawCircle(
                        color = ForYouInsightsTheme.Colors.readyStrong,
                        radius = 3.4f * unit,
                        center = Offset(
                            x = (20f + 16f * cos(endRadians).toFloat()) * unit,
                            y = (20f + 16f * sin(endRadians).toFloat()) * unit,
                        ),
                    )
                }
                ForYouInsightsMetricKind.FITNESS -> {
                    listOf(
                        Triple(16.5f, 0.86f, ForYouInsightsTheme.Colors.fitMove),
                        Triple(11.5f, 0.72f, ForYouInsightsTheme.Colors.fitExercise),
                        Triple(6.5f, 0.58f, ForYouInsightsTheme.Colors.fitStand),
                    ).forEach { ring ->
                        val radius = ring.first * unit
                        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                        val ringSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                        drawArc(
                            color = ring.third.copy(alpha = 0.18f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = ringSize,
                            style = Stroke(width = 4.2f * unit),
                        )
                        drawArc(
                            color = ring.third,
                            startAngle = -90f,
                            sweepAngle = 360f * ring.second,
                            useCenter = false,
                            topLeft = topLeft,
                            size = ringSize,
                            style = Stroke(width = 4.2f * unit, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        if (kind != ForYouInsightsMetricKind.FITNESS) {
            ForYouInsightsText(
                text = if (kind == ForYouInsightsMetricKind.SLEEP) "84" else "7",
                style = ForYouInsightsTheme.Type.railNumber,
                color = if (kind == ForYouInsightsMetricKind.READINESS) {
                    ForYouInsightsTheme.Colors.readyStrong
                } else {
                    ForYouInsightsTheme.Colors.ink
                },
                scale = scale * (40.0 / ForYouInsightsTheme.Layout.RAIL_RING),
            )
        }
    }
}

@Composable
fun ForYouInsightsNavigation(
    section: ForYouInsightsSection,
    frame: State<ForYouInsightsFrame>,
    reducedMotion: Boolean,
    scale: Double,
    width: Double,
    top: Double,
    enabled: Boolean,
    onSelect: (ForYouInsightsSection) -> Unit,
) {
    val barWidth = ForYouInsightsTheme.Layout.TAB_BAR_WIDTH * scale
    val barHeight = ForYouInsightsTheme.Layout.TAB_BAR_HEIGHT * scale
    Box(
        Modifier
            .offset(
                x = ((width - barWidth) / 2.0).dp,
                y = top.dp,
            )
            .size(barWidth.dp, barHeight.dp)
            .graphicsLayer {
                val progress = frame.value.progress
                alpha = if (reducedMotion) {
                    (1.0 - ForYouInsightsMotion.clamp01(progress)).toFloat()
                } else if (progress > ForYouInsightsMotion.Reader.TAB_HIDDEN) {
                    0f
                } else {
                    1f
                }
                translationY = if (reducedMotion) {
                    0f
                } else {
                    (ForYouInsightsMotion.Reader.TAB_DROP * scale *
                        ForYouInsightsMotion.easeOut(
                            ForYouInsightsMotion.seg(
                                progress,
                                ForYouInsightsMotion.Reader.TAB_OUT.first,
                                ForYouInsightsMotion.Reader.TAB_OUT.second,
                            ),
                        )).dp.toPx()
                }
            }
            .shadow(
                elevation = (8.0 * scale).dp,
                shape = RoundedCornerShape(
                    (ForYouInsightsTheme.Radius.TAB_BAR * scale).dp,
                ),
                ambientColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.12f),
                spotColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.12f),
            )
            .clip(
                RoundedCornerShape(
                    (ForYouInsightsTheme.Radius.TAB_BAR * scale).dp,
                ),
            )
            .background(ForYouInsightsTheme.Colors.tabFallback),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(
                    width = (ForYouInsightsTheme.Layout.TAB_PILL_WIDTH * scale).dp,
                    height = (ForYouInsightsTheme.Layout.TAB_PILL_HEIGHT * scale).dp,
                )
                .graphicsLayer {
                    val position = frame.value.lens
                    val travel = ForYouInsightsMotion.clamp01(
                        abs(position - position.roundToInt()) * 2.0,
                    )
                    translationX = ((position - 1.0) *
                        ForYouInsightsTheme.Layout.TAB_PITCH * scale).dp.toPx()
                    scaleX = (1.0 + 0.16 * travel).toFloat()
                    scaleY = (1.0 - 0.06 * travel).toFloat()
                }
                .clip(
                    RoundedCornerShape(
                        (ForYouInsightsTheme.Radius.TAB_PILL * scale).dp,
                    ),
                )
                .background(ForYouInsightsTheme.Colors.pillFill)
                .border(
                    width = (0.67 * scale).dp,
                    color = ForYouInsightsTheme.Colors.pillEdge,
                    shape = RoundedCornerShape(
                        (ForYouInsightsTheme.Radius.TAB_PILL * scale).dp,
                    ),
                ),
        )
        ForYouInsightsSection.entries.forEachIndexed { index, item ->
            val selected = item == section
            val label = when (item) {
                ForYouInsightsSection.INSIGHTS -> "Insights"
                ForYouInsightsSection.LONGEVITY -> "Longevity"
                ForYouInsightsSection.BROWSE -> "Browse"
            }
            val icon = when (item) {
                ForYouInsightsSection.INSIGHTS -> Icons.Filled.Favorite
                ForYouInsightsSection.LONGEVITY -> Icons.Filled.WbSunny
                ForYouInsightsSection.BROWSE -> Icons.Filled.GridView
            }
            val tint = if (selected) {
                ForYouInsightsTheme.Colors.blue
            } else {
                ForYouInsightsTheme.Colors.ink
            }
            Box(
                Modifier
                    .offset(
                        x = (barWidth / 2.0 +
                            (index - 1) * ForYouInsightsTheme.Layout.TAB_PITCH * scale -
                            ForYouInsightsTheme.Layout.TAB_PILL_WIDTH * scale / 2.0).dp,
                    )
                    .size(
                        width = (ForYouInsightsTheme.Layout.TAB_PILL_WIDTH * scale).dp,
                        height = barHeight.dp,
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClickLabel = label,
                        onClick = { onSelect(item) },
                    )
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(
                            y = ((ForYouInsightsTheme.Layout.TAB_ICON_CENTER -
                                ForYouInsightsTheme.Layout.TAB_ICON / 2.0) * scale).dp,
                        )
                        .size((ForYouInsightsTheme.Layout.TAB_ICON * scale).dp),
                )
                ForYouInsightsText(
                    text = label,
                    style = ForYouInsightsTheme.Type.tab,
                    color = tint,
                    scale = scale,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(
                            y = ((ForYouInsightsTheme.Layout.TAB_LABEL_CENTER -
                                ForYouInsightsTheme.Type.tab.size * 0.62) * scale).dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ForYouInsightsMoreButton(
    scale: Double,
    left: Double,
    top: Double,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .offset(x = left.dp, y = top.dp)
            .size((44.0 * scale).dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Write this insight again",
                onClick = onClick,
            )
            .semantics { contentDescription = "Write this insight again" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((ForYouInsightsTheme.Layout.ELLIPSIS_SIZE * scale).dp)
                .clip(
                    RoundedCornerShape(
                        (ForYouInsightsTheme.Radius.ELLIPSIS * scale).dp,
                    ),
                )
                .background(ForYouInsightsTheme.Colors.ellipsisFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = ForYouInsightsTheme.Colors.ink,
                modifier = Modifier.size(
                    (ForYouInsightsTheme.Layout.ELLIPSIS_GLYPH * scale).dp,
                ),
            )
        }
    }
}

@Composable
private fun ForYouInsightsDivider(
    left: Double,
    top: Double,
    width: Double,
) {
    Box(
        Modifier
            .offset(x = left.dp, y = top.dp)
            .size(width.dp, ForYouInsightsTheme.Layout.DIVIDER.dp)
            .background(ForYouInsightsTheme.Colors.separator),
    )
}

@Composable
fun ForYouInsightsText(
    text: String,
    style: ForYouInsightsTypeStyle,
    color: Color,
    scale: Double,
    modifier: Modifier = Modifier,
    width: Double? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = if (width == null) modifier else modifier.width(width.dp),
        color = color,
        fontSize = (style.size * scale).toFloat().sp,
        lineHeight = style.line?.let { line -> (line * scale).toFloat().sp }
            ?: TextUnit.Unspecified,
        fontWeight = style.weight,
        letterSpacing = (style.tracking * scale).toFloat().sp,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
    )
}

private fun forYouInsightsFeedY(reference: Double, safeTop: Double, scale: Double): Double {
    return safeTop + (reference - ForYouInsightsTheme.REF_SAFE_TOP) * scale
}
