package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/** A real reader page that compresses into an AI summary under a two-finger pinch. */
@Composable
fun PinchToSummarize(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reduceMotion = rememberReducedMotion()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(PtsColor.page),
    ) {
        val stage = PtsStage(
            width = maxWidth.value,
            height = maxHeight.value,
            insetTop = WindowInsets.safeDrawing.getTop(density) / density.density,
        )
        val state = rememberPtsState(
            stage = stage,
            autoPlay = autoPlay,
            reduceMotion = reduceMotion,
            revealTotal = 0f,
            ready = stage.width > 0f && stage.height > 0f,
        )
        val summary = rememberPtsSummaryLayout(stage.width, state.level)
        SideEffect { state.updateRevealTotal(summary.revealTotal) }
        val insetBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
        PinchToSummarizeStatusBars(darkStatus = state.mode == PtsMode.Reading)
        Box(Modifier.fillMaxSize().ptsGestures(state)) {
            PtsField(stage = stage, state = state, modifier = Modifier.fillMaxSize())
            PtsDepthLabel(stage = stage, insetBottom = insetBottom, state = state)
            PinchToSummarizeArticle(stage = stage, insetBottom = insetBottom, state = state)
            PtsChips(stage = stage, state = state, summary = summary)
            PinchToSummarizeSummary(stage = stage, insetBottom = insetBottom, state = state, reduceMotion = reduceMotion, summary = summary)
            PtsLight(stage = stage, state = state, modifier = Modifier.fillMaxSize())
        }
    }
}
