package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The four marked sentences detach from the held article and hand over to their summary rows. */
@Composable
internal fun PtsChips(stage: PtsStage, state: PtsState, summary: PtsSummaryLayout) {
    if (state.mode != PtsMode.Summary || state.reveal.value >= PtsFlight.landMs + 80f) return
    val level = PtsCopy.levels[state.level]
    val sourceY = listOf(790f, 912f, 1_040f, 1_160f)
    val startFrame = ptsPageFrame(stage, PtsAutoplay.holdProgress, 0f, 0f, 0f)
    val summaryTop = ptsCardTop(1f) + PtsLayout.cardHeight + PtsLayout.summaryGap
    Box(Modifier.fillMaxSize()) {
        PtsCopy.body.take(PtsMark.count).forEachIndexed { index, paragraph ->
            val group = level.chips[index]
            val delay = (PtsMark.count - 1 - index) * PtsFlight.stagger
            val raw = ptsClamp01((state.reveal.value - delay) / PtsFlight.ms)
            val u = ptsSheetEase(raw)
            val source = (paragraph.leadIn.orEmpty() + paragraph.key.orEmpty()).trim()
            val targetSource = if (group <= 0) 0 else level.points.getOrNull(group - 1)?.let { PtsCopy.items[it].source } ?: 0
            val target = summary.rows.firstOrNull {
                if (group <= 0) it.kind == PtsSummaryLayout.Kind.Lead else it.kind == PtsSummaryLayout.Kind.Body && it.source == targetSource
            }
            val startX = startFrame.x + 20f * startFrame.scale
            val startY = startFrame.y + (sourceY[index] - PtsAutoplay.readTarget - startFrame.trim) * startFrame.scale
            val endX = target?.x ?: PtsLayout.textInset
            val endY = summaryTop + (target?.y ?: 0f)
            val dy = endY - startY
            val bow = (PtsFlight.bow + abs(dy) * PtsFlight.bowPerDy) * sin(PI.toFloat() * u) * if (index % 2 == 0) 1f else -1f
            val owns = group >= 0 && level.chips.indexOf(group) == index
            val alpha = when {
                group < 0 -> 1f - u
                !owns -> 1f - ptsClamp01((u - .45f) / .35f)
                else -> 1f - ptsClamp01((u - .72f) / .28f)
            }
            val width = ptsMix(stage.width - 40f, target?.width ?: stage.width - 32f, u)
            Box(
                Modifier.offset(x = startX.dp, y = startY.dp)
                    .graphicsLayer {
                        translationX = (ptsMix(0f, endX - startX, u) + bow).dp.toPx()
                        translationY = ptsMix(0f, dy, u).dp.toPx()
                        this.alpha = alpha * state.summaryAlpha.value
                    }
                    .width(width.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PtsColor.chipPill.copy(alpha = ptsClamp01((u - .2f) / .3f)))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    source,
                    style = PtsType.itemBody.copy(
                        color = if (u < .35f) PtsColor.ink else PtsColor.summaryInk,
                    ),
                    maxLines = 3,
                )
            }
        }
    }
}
