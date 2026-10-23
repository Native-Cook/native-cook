package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.pow

/** Native measured rows animate in draw/layer phases, never by recomposing the screen. */
@Composable
internal fun PinchToSummarizeSummary(
    stage: PtsStage,
    insetBottom: Float,
    state: PtsState,
    reduceMotion: Boolean,
    summary: PtsSummaryLayout,
) {
    val top = ptsCardTop(1f) + PtsLayout.cardHeight + PtsLayout.summaryGap
    val footerBottom = max(PtsLayout.footerMin, insetBottom - PtsLayout.footerLift)
    val cardWidth = ptsCardWidth(stage)
    val live = state.mode == PtsMode.Summary
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var sharing by remember { mutableStateOf(false) }
    val shareLift by animateFloatAsState(
        targetValue = if (sharing) 1f else 0f,
        animationSpec = if (sharing) spring(dampingRatio = .8f, stiffness = 520f) else tween(PtsTiming.dropMs.toInt()),
        label = "summary share lift",
    )
    LaunchedEffect(live) {
        if (!live) sharing = false
    }
    Box(Modifier.fillMaxSize()) {
        if (sharing) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = "Put the summary back",
                    onClick = { sharing = false },
                ),
            )
        }
        Row(
            Modifier.offset(x = ((stage.width - cardWidth) / 2f).dp)
                .width(cardWidth.dp).height(16.dp)
                .graphicsLayer {
                    alpha = ptsClamp01((state.p.value - .9f) / .1f)
                    translationY = (ptsCardTop(state.lift.value) - PtsLayout.titleRowGap).dp.toPx()
                }.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(PtsColor.summaryInk), contentAlignment = Alignment.Center) {
                Text("M", style = PtsType.cardTitle.copy(fontSize = 9.sp, fontWeight = FontWeight(900), color = PtsColor.ink))
            }
            Text(PtsCopy.pageTitle, style = PtsType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.offset(y = top.dp).fillMaxWidth()
                .height((stage.height - top - footerBottom - PtsLayout.footerHeight).coerceAtLeast(0f).dp)
                .graphicsLayer {
                    val scale = 1f + (PtsTiming.shareScale - 1f) * shareLift
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(live) {
                    detectTapGestures(
                        onLongPress = {
                            if (live) {
                                sharing = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    )
                }
                .clipToBounds().clearAndSetSemantics {
                    if (live) contentDescription = "Summary. ${state.summaryText}"
                },
        ) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = (PtsLayout.textInset - PtsLayout.sharePad).dp)
                    .graphicsLayer { alpha = shareLift }
                    .clip(RoundedCornerShape(PtsLayout.shareRadius.dp))
                    .background(PtsColor.shareGlass)
                    .border(.5.dp, PtsColor.shareBorder, RoundedCornerShape(PtsLayout.shareRadius.dp)),
            )
            summary.rows.forEachIndexed { index, row ->
                PtsSummaryText(row, index, summary.revealTotal, state, reduceMotion)
            }
            summary.entityY?.let { y ->
                Row(
                    Modifier.offset(x = PtsLayout.textInset.dp, y = y.dp)
                        .graphicsLayer {
                            alpha = ptsClamp01(state.reveal.value / summary.revealTotal) * state.summaryAlpha.value
                        },
                    horizontalArrangement = Arrangement.spacedBy(PtsLayout.entityGapX.dp),
                ) {
                    summary.entities.forEach { entity ->
                        Box(
                            Modifier.height(PtsLayout.entityHeight.dp).clip(CircleShape)
                                .background(PtsColor.entityFill)
                                .border(.5.dp, PtsColor.entityBorder, CircleShape)
                                .padding(horizontal = PtsLayout.entityPadX.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(entity.label, style = PtsType.entity) }
                    }
                }
            }
        }
        PtsFooter(stage, footerBottom, state, live, shareLift)
        PtsShareActions(
            stage = stage,
            footerBottom = footerBottom,
            visible = sharing,
            alpha = shareLift,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Halo One summary", state.summaryText))
                sharing = false
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.summaryText)
                }
                context.startActivity(Intent.createChooser(intent, PtsCopy.share[1]))
                sharing = false
            },
        )
    }
}

private fun ptsLineProgress(state: PtsState, index: Int, total: Float, reduced: Boolean): Float =
    if (reduced) ptsClamp01(state.reveal.value / max(1f, total))
    else ptsClamp01((state.reveal.value - PtsFlight.landMs - index * PtsTiming.lineStagger) / PtsTiming.lineMs)

@Composable
private fun PtsSummaryText(row: PtsSummaryLayout.Row, index: Int, total: Float, state: PtsState, reduced: Boolean) {
    val final = if (row.kind == PtsSummaryLayout.Kind.Body) PtsColor.summaryInkSoft else PtsColor.summaryInk
    Canvas(
        Modifier.offset(x = row.x.dp, y = row.y.dp)
            .width((row.width + 8f).dp).height(row.layout.layoutInput.style.lineHeight.value.dp)
            .then(
                if (state.mode == PtsMode.Summary) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { state.requestSource(row.source) },
                ) else Modifier,
            ),
    ) {
        val u = ptsLineProgress(state, index, total, reduced)
        val rise = if (reduced) 0f else (1f - u) * PtsTiming.lineRise.dp.toPx()
        drawText(
            row.layout,
            color = final,
            topLeft = Offset(0f, rise), alpha = u * state.summaryAlpha.value,
        )
    }
    if (row.icon != null) {
        Box(
            Modifier.offset(x = PtsLayout.textInset.dp, y = (row.y + (PtsType.itemTitle.lineHeight.value - PtsLayout.iconDisc) / 2f).dp)
                .size(PtsLayout.iconDisc.dp).graphicsLayer {
                    val u = ptsLineProgress(state, index, total, reduced)
                    val c = 1.9f
                    val back = 1f + (c + 1f) * (u - 1f).pow(3) + c * (u - 1f).pow(2)
                    alpha = u * state.summaryAlpha.value
                    scaleX = if (reduced) 1f else PtsTiming.discFrom + (1f - PtsTiming.discFrom) * back
                    scaleY = scaleX
                }.clip(CircleShape).background(PtsColor.iconDisc),
            contentAlignment = Alignment.Center,
        ) { PtsIonicon(row.icon, PtsColor.iconGlyph, Modifier.size(13.dp)) }
    }
    if (row.kind == PtsSummaryLayout.Kind.Title && row.text == PtsCopy.items.first().title) {
        Canvas(
            Modifier.offset(x = (row.x + 137f).dp, y = (row.y + 9f).dp)
                .width((PtsLayout.meterSegW * 5f + PtsLayout.meterGap * 4f).dp)
                .height(PtsLayout.meterSegH.dp),
        ) {
            val u = ptsLineProgress(state, index, total, reduced) * state.summaryAlpha.value
            repeat(5) { segment ->
                val left = segment * (PtsLayout.meterSegW + PtsLayout.meterGap).dp.toPx()
                drawRoundRect(
                    color = PtsColor.meterTrack,
                    topLeft = Offset(left, 0f),
                    size = Size(PtsLayout.meterSegW.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                    alpha = u,
                )
                if (segment < 4) {
                    drawRoundRect(
                        color = PtsColor.meterFill,
                        topLeft = Offset(left, 0f),
                        size = Size(PtsLayout.meterSegW.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                        alpha = u,
                    )
                }
            }
        }
    }
}

@Composable
private fun PtsFooter(stage: PtsStage, footerBottom: Float, state: PtsState, live: Boolean, shareLift: Float) {
    val footerAlpha = ptsClamp01((state.p.value - .9f) / .1f) * state.summaryAlpha.value * (1f - shareLift)
    val footerTop = stage.height - footerBottom - PtsLayout.footerHeight
    Canvas(Modifier.fillMaxSize()) {
        val frame = ptsPageFrame(stage, state.p.value, state.lift.value, state.driftX.value, state.driftY.value)
        val barWidth = ptsCardWidth(stage) - PtsLayout.progressInset * 2f
        val barLeft = frame.x + PtsLayout.progressInset
        val barTop = frame.y + frame.height - PtsLayout.progressBottom - PtsLayout.progressHeight
        drawRoundRect(
            color = PtsColor.progressTrack,
            topLeft = Offset(barLeft.dp.toPx(), barTop.dp.toPx()),
            size = Size(barWidth.dp.toPx(), PtsLayout.progressHeight.dp.toPx()),
            cornerRadius = CornerRadius(PtsLayout.progressHeight.dp.toPx() / 2f),
            alpha = footerAlpha,
        )
        drawRoundRect(
            color = PtsColor.progressFill,
            topLeft = Offset(barLeft.dp.toPx(), barTop.dp.toPx()),
            size = Size((barWidth * .5f).dp.toPx(), PtsLayout.progressHeight.dp.toPx()),
            cornerRadius = CornerRadius(PtsLayout.progressHeight.dp.toPx() / 2f),
            alpha = footerAlpha,
        )
    }
    Row(
        Modifier.offset(x = PtsLayout.textInset.dp, y = footerTop.dp)
            .width((stage.width - PtsLayout.textInset * 2f).dp).height(PtsLayout.footerHeight.dp)
            .graphicsLayer { alpha = footerAlpha }
            .clearAndSetSemantics {
                if (live) contentDescription = "${PtsCopy.credit}. ${PtsCopy.creditRead}"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(PtsLayout.creditPill.height.dp)
                .clip(CircleShape).background(PtsColor.closeFill)
                .padding(start = PtsLayout.creditPadLeft.dp, end = PtsLayout.creditPadRight.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(PtsColor.creditIcon)), contentAlignment = Alignment.Center) {
                PtsIonicon("sparkles", PtsColor.closeInk, Modifier.size(12.dp))
            }
            Column {
                Text(PtsCopy.credit, style = PtsType.creditTitle)
                Text(PtsCopy.creditRead, style = PtsType.creditSub)
            }
        }
        Box(
            Modifier.size(PtsLayout.closeSize.dp)
            .then(if (live) Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = "Close summary", onClick = state::requestArticle)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Close summary. Shows the full article. You can also pinch out."
                    onClick(label = "Close summary") {
                        state.requestArticle()
                        true
                    }
                } else Modifier.clearAndSetSemantics {})
                .clip(CircleShape).background(PtsColor.closeFill),
            contentAlignment = Alignment.Center,
        ) {
            PtsIonicon("close", PtsColor.closeInk, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PtsShareActions(
    stage: PtsStage,
    footerBottom: Float,
    visible: Boolean,
    alpha: Float,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.offset(
            x = PtsLayout.textInset.dp,
            y = (stage.height - footerBottom - PtsLayout.actionHeight).dp,
        ).width((stage.width - PtsLayout.textInset * 2f).dp)
            .height(PtsLayout.actionHeight.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = (1f - alpha) * PtsTiming.shareRise.dp.toPx()
            },
        horizontalArrangement = Arrangement.spacedBy(PtsLayout.actionGap.dp),
    ) {
        PtsShareAction(PtsCopy.share[0], "copy-outline", visible, onCopy, Modifier.weight(1f))
        PtsShareAction(PtsCopy.share[1], "share-outline", visible, onShare, Modifier.weight(1f))
    }
}

@Composable
private fun PtsShareAction(
    label: String,
    icon: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.height(PtsLayout.actionHeight.dp)
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                else Modifier,
            )
            .clip(CircleShape).background(PtsColor.actionFill),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PtsIonicon(icon, PtsColor.summaryInk, Modifier.size(17.dp))
        Text(label, style = PtsType.action)
    }
}
