package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.BottomTab
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.CircleTime
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.Footer
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.Header
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.Queued
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.TimeRange
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.TimeRangeItemHeight
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components.TimeRangeWidth
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.FadeInUp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.TimeTargetTheme

/** Root screen. Mirrors `App.tsx`: Header → Queued → (CircleTime + TimeRange) → Footer → BottomTab. */
@Composable
fun TimeTargetScreen() {
    // Both drag surfaces move the same list, keeping the ruler, picker and arrows in sync.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 4)
    val itemHeightPx = with(LocalDensity.current) { TimeRangeItemHeight.toPx() }
    val dialSnapProvider = remember(listState) {
        val base = SnapLayoutInfoProvider(listState, SnapPosition.Start)
        object : SnapLayoutInfoProvider by base {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
            override fun calculateSnapOffset(velocity: Float): Float = base.calculateSnapOffset(0f)
        }
    }
    val dialFlingBehavior = rememberSnapFlingBehavior(dialSnapProvider)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
    ) {
        Header()
        Queued()

        // `container` style: flexDirection row, alignItems center, flex 1
        FadeInUp(
            durationMillis = 600,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircleTime(
                    selectedDuration = {
                        (1f + listState.firstVisibleItemIndex +
                            listState.firstVisibleItemScrollOffset / itemHeightPx).coerceIn(1f, 10f)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = TimeRangeWidth)
                        .scrollable(
                            state = listState,
                            orientation = Orientation.Vertical,
                            reverseDirection = true,
                            flingBehavior = dialFlingBehavior,
                        )
                        .semantics { contentDescription = "Time target dial" },
                )
                TimeRange(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        Footer()
        BottomTab()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, showSystemUi = true)
@Composable
private fun TimeTargetScreenPreview() {
    TimeTargetTheme {
        TimeTargetScreen()
    }
}
