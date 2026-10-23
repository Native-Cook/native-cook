package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
fun ForYouInsightsReader(
    articleIndex: Int,
    frame: State<ForYouInsightsFrame>,
    scale: Double,
    top: Double,
    viewportHeight: Double,
    bottomInset: Double,
    live: Boolean,
    reducedMotion: Boolean,
) {
    val article = ForYouInsightsContent.articles[articleIndex]
    val scroll = rememberScrollState()
    Box(
        Modifier
            .offset(y = top.dp)
            .fillMaxWidth()
            .height(viewportHeight.dp)
            .graphicsLayer {
                val current = frame.value
                alpha = if (current.actorIndex == articleIndex) 1f else 0f
                translationY = current.dragY.dp.toPx()
            }
            .semantics {
                if (!live) {
                    invisibleToUser()
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = live)
                .padding(
                    start = (ForYouInsightsTheme.Layout.READER_TEXT_INSET * scale).dp,
                    end = (ForYouInsightsTheme.Layout.READER_TEXT_INSET * scale).dp,
                    bottom = (ForYouInsightsTheme.Layout.READER_BOTTOM * scale + bottomInset).dp,
                ),
        ) {
            ForYouInsightsReaderBlock(
                index = 0,
                frame = frame,
                scale = scale,
                reducedMotion = reducedMotion,
            ) {
                ForYouInsightsText(
                    text = article.title,
                    style = ForYouInsightsTheme.Type.readerTitle,
                    color = ForYouInsightsTheme.Colors.ink,
                    scale = scale,
                    modifier = Modifier.semantics { heading() },
                )
            }
            ForYouInsightsReaderBlock(
                index = 1,
                frame = frame,
                scale = scale,
                reducedMotion = reducedMotion,
            ) {
                Row(
                    Modifier.padding(
                        top = (ForYouInsightsTheme.Layout.READER_META_GAP * scale).dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(
                        (ForYouInsightsTheme.Space.XS * scale).dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = ForYouInsightsTheme.Colors.pink,
                        modifier = Modifier.size((13.0 * scale).dp),
                    )
                    ForYouInsightsText(
                        text = "${article.readTime.uppercase()} · ${ForYouInsightsContent.label}",
                        style = ForYouInsightsTheme.Type.readerMeta,
                        color = ForYouInsightsTheme.Colors.pink,
                        scale = scale,
                    )
                }
            }
            ForYouInsightsReaderBlock(
                index = 2,
                frame = frame,
                scale = scale,
                reducedMotion = reducedMotion,
            ) {
                ForYouInsightsText(
                    text = article.summary,
                    style = ForYouInsightsTheme.Type.readerLead,
                    color = ForYouInsightsTheme.Colors.secondary,
                    scale = scale,
                    modifier = Modifier.padding(top = (ForYouInsightsTheme.Space.LG * scale).dp),
                )
            }
            article.sections.forEachIndexed { sectionIndex, section ->
                ForYouInsightsReaderBlock(
                    index = sectionIndex + 3,
                    frame = frame,
                    scale = scale,
                    reducedMotion = reducedMotion,
                ) {
                    Column(
                        Modifier.padding(
                            top = (ForYouInsightsTheme.Layout.READER_SECTION_GAP * scale).dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            (ForYouInsightsTheme.Layout.READER_PARAGRAPH_GAP * scale).dp,
                        ),
                    ) {
                        ForYouInsightsText(
                            text = section.heading,
                            style = ForYouInsightsTheme.Type.readerHeading,
                            color = ForYouInsightsTheme.Colors.ink,
                            scale = scale,
                        )
                        ForYouInsightsText(
                            text = section.body,
                            style = ForYouInsightsTheme.Type.readerBody,
                            color = ForYouInsightsTheme.Colors.secondary,
                            scale = scale,
                        )
                    }
                }
            }
            Spacer(Modifier.height((ForYouInsightsTheme.Layout.READER_BOTTOM * scale).dp))
        }
    }
}

@Composable
private fun ForYouInsightsReaderBlock(
    index: Int,
    frame: State<ForYouInsightsFrame>,
    scale: Double,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.graphicsLayer {
            val progress = frame.value.progress
            if (reducedMotion) {
                alpha = ForYouInsightsMotion.clamp01(progress).toFloat()
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
                return@graphicsLayer
            }
            val range = if (index == 0) {
                ForYouInsightsMotion.Reader.TITLE_IN
            } else {
                ForYouInsightsMotion.Reader.BODY_IN
            }
            val delay = if (index == 0) {
                0.0
            } else {
                (index - 1) * ForYouInsightsMotion.Reader.BODY_STAGGER
            }
            val amount = ForYouInsightsMotion.seg(
                progress,
                range.first + delay,
                range.second + delay,
            )
            val rise = if (index == 0) {
                ForYouInsightsMotion.Reader.TITLE_RISE
            } else {
                ForYouInsightsMotion.Reader.BODY_RISE
            }
            alpha = ForYouInsightsMotion.smooth(amount).toFloat()
            translationY = ((1.0 - ForYouInsightsMotion.easeOut(amount)) * rise * scale)
                .dp
                .toPx()
            if (index == 0) {
                val titleScale = ForYouInsightsMotion.lerp(
                    ForYouInsightsMotion.Reader.TITLE_SCALE_FROM,
                    1.0,
                    ForYouInsightsMotion.easeOut(amount),
                ).toFloat()
                scaleX = titleScale
                scaleY = titleScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        },
    ) {
        content()
    }
}

@Composable
fun ForYouInsightsHeroDragTarget(
    articleIndex: Int,
    live: Boolean,
    scale: Double,
    controller: ForYouInsightsController,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    Box(
        modifier
            .pointerInput(articleIndex, live, scale) {
                if (!live) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var active = false
                    var translationX = 0f
                    var translationY = 0f
                    while (true) {
                        val change = awaitPointerEvent()
                            .changes
                            .firstOrNull { item -> item.id == down.id }
                            ?: break
                        translationX = change.position.x - down.position.x
                        translationY = change.position.y - down.position.y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!active && hypot(translationX, translationY) >= 4f * density) {
                            active = true
                        }
                        if (active) {
                            controller.dragChanged(
                                translationX = (translationX / density).toDouble(),
                                translationY = (translationY / density).toDouble(),
                                scale = scale,
                            )
                            change.consume()
                            onUpdate()
                        }
                        if (!change.pressed) {
                            break
                        }
                    }
                    if (active) {
                        val velocity = tracker.calculateVelocity().y / density
                        val dismissed = controller.dragEnded(
                            translationY = (translationY / density).toDouble(),
                            velocityY = velocity.toDouble(),
                            scale = scale,
                            time = forYouInsightsReaderNow(),
                        )
                        if (dismissed) {
                            onDismiss()
                        }
                        onUpdate()
                    }
                }
            }
            .semantics {
                if (!live) {
                    invisibleToUser()
                } else {
                    contentDescription = "Drag down to close ${ForYouInsightsContent.articles[articleIndex].title}"
                    customActions = listOf(
                        CustomAccessibilityAction(label = "Close article") {
                            controller.close(forYouInsightsReaderNow())
                            onUpdate()
                            true
                        },
                    )
                }
            },
    )
}

@Composable
fun ForYouInsightsCloseButton(
    title: String,
    frame: State<ForYouInsightsFrame>,
    reducedMotion: Boolean,
    scale: Double,
    left: Double,
    top: Double,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .offset(x = left.dp, y = top.dp)
            .size(((ForYouInsightsTheme.Layout.CLOSE_SIZE + 8.0) * scale).dp)
            .graphicsLayer {
                val current = frame.value
                val amount = ForYouInsightsMotion.seg(
                    current.progress,
                    ForYouInsightsMotion.Reader.CLOSE_IN.first,
                    ForYouInsightsMotion.Reader.CLOSE_IN.second,
                )
                alpha = ForYouInsightsMotion.smooth(amount).toFloat()
                translationY = current.dragY.dp.toPx()
                val buttonScale = if (reducedMotion) {
                    1.0
                } else {
                    ForYouInsightsMotion.lerp(
                        ForYouInsightsMotion.Reader.CLOSE_SCALE_FROM,
                        1.0,
                        ForYouInsightsMotion.easeOut(amount),
                    )
                }.toFloat()
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Close $title",
                onClick = onClose,
            )
            .semantics {
                if (!enabled) {
                    invisibleToUser()
                } else {
                    contentDescription = "Close $title"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((ForYouInsightsTheme.Layout.CLOSE_SIZE * scale).dp)
                .shadow(
                    elevation = (2.0 * scale).dp,
                    shape = CircleShape,
                    ambientColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.12f),
                    spotColor = ForYouInsightsTheme.Colors.shadow.copy(alpha = 0.12f),
                )
                .clip(CircleShape)
                .background(ForYouInsightsTheme.Colors.closeDisc)
                .border(
                    width = (0.33 * scale).dp,
                    color = ForYouInsightsTheme.Colors.tabEdge,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = ForYouInsightsTheme.Colors.ink,
                modifier = Modifier.size((18.0 * scale).dp),
            )
        }
    }
}

private fun forYouInsightsReaderNow(): Double {
    return System.nanoTime() / 1_000_000_000.0
}
