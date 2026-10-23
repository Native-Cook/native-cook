package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.sin

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
    val footerBottom = max(PtsLayout.footerBottom, insetBottom - PtsLayout.footerBottom)
    val cardWidth = ptsCardWidth(stage)
    val live = state.mode == PtsMode.Summary
    Box(Modifier.fillMaxSize()) {
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
                .clipToBounds().clearAndSetSemantics {
                    if (live) contentDescription = "Summary. ${state.summaryText}"
                },
        ) {
            summary.rows.forEachIndexed { index, row ->
                PtsSummaryText(row, index, summary.revealTotal, state, reduceMotion)
            }
        }
        PtsFooter(stage, footerBottom, state, reduceMotion, summary.revealTotal, live)
    }
}

private fun ptsLineProgress(state: PtsState, index: Int, total: Float, reduced: Boolean): Float =
    if (reduced) ptsClamp01(state.reveal.value / max(1f, total))
    else ptsClamp01((state.reveal.value - index * PtsTiming.lineStagger) / PtsTiming.lineMs)

/** Reanimated interpolateColor's default sRGB gamma correction (gamma 2.2). */
private fun ptsInterpolateColor(from: Color, to: Color, fraction: Float): Color {
    val t = ptsClamp01(fraction)
    fun channel(a: Float, b: Float): Float = ptsMix(a.pow(2.2f), b.pow(2.2f), t).pow(1f / 2.2f)
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue), ptsMix(from.alpha, to.alpha, t))
}

@Composable
private fun PtsSummaryText(row: PtsSummaryLayout.Row, index: Int, total: Float, state: PtsState, reduced: Boolean) {
    val final = if (row.kind == PtsSummaryLayout.Kind.Body) PtsColor.summaryInkSoft else PtsColor.summaryInk
    Canvas(Modifier.offset(x = row.x.dp, y = row.y.dp).width((row.width + 8f).dp).height(row.layout.layoutInput.style.lineHeight.value.dp)) {
        val u = ptsLineProgress(state, index, total, reduced)
        val rise = if (reduced) 0f else (1f - u) * PtsTiming.lineRise.dp.toPx()
        if (!reduced) {
            val ghostAlpha = PtsTiming.ghostAlpha * sin(Math.PI.toFloat() * u) * state.summaryAlpha.value
            val shift = (1f - u) * PtsTiming.ghostOffset.dp.toPx()
            drawText(row.layout, color = PtsColor.summaryArrive, topLeft = Offset(-shift, rise), alpha = ghostAlpha)
            drawText(row.layout, color = PtsColor.summaryArrive, topLeft = Offset(shift, rise), alpha = ghostAlpha)
        }
        drawText(
            row.layout,
            color = ptsInterpolateColor(PtsColor.summaryArrive, final, (u - .35f) / .65f),
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
}

@Composable
private fun PtsFooter(stage: PtsStage, footerBottom: Float, state: PtsState, reduced: Boolean, revealTotal: Float, live: Boolean) {
    // Draw the changing rect itself: scaling a parent changes its pivot and corner shape.
    Canvas(Modifier.fillMaxSize()) {
        val t = ptsClamp01(state.p.value)
        val e = t * t * (3f - 2f * t)
        val width = ptsMix(PtsLayout.toolbarPill.width, PtsLayout.creditPill.width, e)
        val height = ptsMix(PtsLayout.toolbarPill.height, PtsLayout.creditPill.height, e)
        val left = ptsMix(stage.width / 2f - PtsLayout.toolbarPill.width / 2f, PtsLayout.textInset - PtsLayout.creditPad, e)
        val bottom = ptsMix(PtsLayout.toolbarCenterFromBottom - PtsLayout.toolbarPill.height / 2f, footerBottom + (PtsLayout.footerHeight - PtsLayout.creditPill.height) / 2f, e)
        val opacity = when {
            QA_HOLD != null -> 0f
            reduced -> ptsClamp01((t - .9f) / .1f)
            else -> ptsClamp01(t / .25f)
        }
        drawRoundRect(
            color = ptsInterpolateColor(PtsColor.toolbar, PtsColor.closeFill, e),
            topLeft = Offset(left.dp.toPx(), (stage.height - bottom - height).dp.toPx()),
            size = Size(width.dp.toPx(), height.dp.toPx()),
            cornerRadius = CornerRadius(height.dp.toPx() / 2f), alpha = opacity,
        )
    }
    val creditTop = stage.height - footerBottom - PtsLayout.footerHeight + (PtsLayout.footerHeight - 26f) / 2f
    Box(Modifier.offset(x = PtsLayout.textInset.dp, y = creditTop.dp).graphicsLayer {
        alpha = if (QA_HOLD != null) {
            state.summaryAlpha.value * ptsClamp01((state.reveal.value - revealTotal + PtsTiming.lineMs) / PtsTiming.lineMs)
        } else ptsClamp01((state.p.value - .92f) / .08f)
    }.clearAndSetSemantics {
        if (live) contentDescription = PtsCopy.credit.joinToString(". ")
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(PtsColor.creditIcon)), contentAlignment = Alignment.Center) {
                PtsIonicon("sparkles", PtsColor.closeInk, Modifier.size(12.dp))
            }
            Column {
                Text(PtsCopy.credit[0], style = PtsType.credit)
                Text(PtsCopy.credit[1], style = PtsType.credit)
            }
        }
        Canvas(
            Modifier.offset(x = (-PtsLayout.creditPad).dp, y = (-(PtsLayout.creditPill.height - 26f) / 2f).dp)
                .size(PtsLayout.creditPill.width.dp, PtsLayout.creditPill.height.dp)
                .clip(RoundedCornerShape(PtsLayout.creditPill.height.dp / 2f)),
        ) {
            val at = state.bloomAt.value
            val u = if (at < 0f) 0f else ptsClamp01((state.clock.value - at - revealTotal - PtsTiming.sweepDelay) / PtsTiming.sweepMs)
            if (u > 0f && u < 1f) {
                val e = if (u < .5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
                val x = -PtsLayout.sweepWidth + e * (PtsLayout.creditPill.width + PtsLayout.sweepWidth)
                translate(left = x.dp.toPx()) {
                    drawRect(Brush.horizontalGradient(PtsColor.sweep, endX = PtsLayout.sweepWidth.dp.toPx()), size = Size(PtsLayout.sweepWidth.dp.toPx(), size.height))
                }
            }
        }
    }
    // Native 48 dp minimum covers the source's 32 + 6 + 6 dp close target.
    Box(
        Modifier.offset(x = (stage.width - 20f - PtsLayout.closeSize - 6f).dp, y = (stage.height - footerBottom - PtsLayout.footerHeight + (PtsLayout.footerHeight - PtsLayout.closeSize) / 2f - 6f).dp)
            .size((PtsLayout.closeSize + 12f).dp).graphicsLayer { alpha = ptsClamp01((state.p.value - .9f) / .1f) }
            .then(if (live) Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = "Close summary", onClick = state::requestArticle)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Close summary. Shows the full article. You can also pinch out."
                    onClick(label = "Close summary") {
                        state.requestArticle()
                        true
                    }
                } else Modifier.clearAndSetSemantics {}),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(PtsLayout.closeSize.dp).clip(CircleShape).background(PtsColor.closeFill), contentAlignment = Alignment.Center) {
            PtsIonicon("close", PtsColor.closeInk, Modifier.size(16.dp))
        }
    }
}
