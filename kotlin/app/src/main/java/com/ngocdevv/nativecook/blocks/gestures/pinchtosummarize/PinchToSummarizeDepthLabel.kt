package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The pinch-depth chooser and its thinking replacement live below the shrinking page. */
@Composable
internal fun PtsDepthLabel(stage: PtsStage, insetBottom: Float, state: PtsState) {
    val centerY = stage.height - insetBottom - PtsLayout.depthLift
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter)
                .offset(y = (centerY - PtsLayout.depthHeight / 2f).dp)
                .height(PtsLayout.depthHeight.dp)
                .graphicsLayer { alpha = state.depthAlpha.value }
                .clip(CircleShape)
                .background(PtsColor.depthGlass)
                .border(.5.dp, PtsColor.depthBorder, CircleShape)
                .padding(PtsLayout.depthPad.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PtsCopy.levels.forEachIndexed { index, level ->
                Box(
                    Modifier.height((PtsLayout.depthHeight - PtsLayout.depthPad * 2f).dp)
                        .clip(CircleShape)
                        .background(if (index == state.level) PtsColor.depthLens else PtsColor.pageClear)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        level.name,
                        style = PtsType.depth.copy(
                            color = if (index == state.level) PtsColor.depthInkActive else PtsColor.depthInk,
                        ),
                    )
                }
            }
        }
        Text(
            PtsCopy.thinking,
            style = PtsType.thinking,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = (centerY - PtsType.thinking.lineHeight.value / 2f).dp)
                .graphicsLayer {
                    val pulse = ptsClamp01(
                        (state.wait.value - PtsMark.pulseDelay) /
                            PtsMark.pulseMs,
                    )
                    alpha = state.think.value * pulse * (1f - state.lift.value)
                },
        )
    }
}
