package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
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
        val summary = rememberPtsSummaryLayout(stage.width)
        val state = rememberPtsState(
            stage = stage,
            autoPlay = autoPlay,
            reduceMotion = reduceMotion,
            revealTotal = summary.revealTotal,
            ready = stage.width > 0f && stage.height > 0f,
        )
        val insetBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
        PinchToSummarizeStatusBars(darkStatus = state.mode == PtsMode.Reading)
        Box(Modifier.fillMaxSize().ptsGestures(state)) {
            PtsField(stage = stage, state = state, modifier = Modifier.fillMaxSize())
            PinchToSummarizeArticle(stage = stage, insetBottom = insetBottom, state = state)
            PinchToSummarizeSummary(stage = stage, insetBottom = insetBottom, state = state, reduceMotion = reduceMotion, summary = summary)
            PtsLight(stage = stage, state = state, modifier = Modifier.fillMaxSize())
        }
    }
}
