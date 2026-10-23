package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.LabelFont
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.touchableOpacity
import kotlinx.coroutines.launch

private const val SCALE_FACTOR = 2.5f
internal val TimeRangeItemHeight = (36 * SCALE_FACTOR).dp // 90
private val ArrowWrapperHeight = (24 * SCALE_FACTOR).dp  // 60
private val ArrowIconSize = (10 * SCALE_FACTOR).dp       // 25
private val LabelFontSize = (28 * SCALE_FACTOR).sp       // 70
internal val TimeRangeWidth = (100 * SCALE_FACTOR - 66).dp // 184
private val ContainerHeight = TimeRangeItemHeight + ArrowWrapperHeight * 2 // 210
private val DurationOptions = (1..10).toList()

/**
 * `src/components/time-range/index.tsx`
 *
 * A vertical, one-item-per-page picker (1 hr … 10 hr). Its hoisted list state also drives
 * the circular ruler and lets either surface adjust the same continuous duration.
 */
@Composable
fun TimeRange(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val itemHeightPx = with(LocalDensity.current) { TimeRangeItemHeight.toPx() }

    // snapToOffsets + disableIntervalMomentum: snap one item at a time, no decay approach.
    val snapProvider = remember(listState) {
        val base = SnapLayoutInfoProvider(listState, SnapPosition.Start)
        object : SnapLayoutInfoProvider by base {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
        }
    }
    val flingBehavior = rememberSnapFlingBehavior(snapProvider)

    fun currentIndex(): Int {
        val extra = if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        return (listState.firstVisibleItemIndex + extra).coerceIn(0, DurationOptions.lastIndex)
    }

    fun step(delta: Int) {
        val target = (currentIndex() + delta).coerceIn(0, DurationOptions.lastIndex)
        scope.launch { listState.animateScrollToItem(target) }
    }

    Column(
        modifier = modifier
            .width(TimeRangeWidth)
            .height(ContainerHeight),
    ) {
        ArrowButton(icon = Icons.Filled.KeyboardArrowUp, label = "Decrease time target", onClick = { step(-1) })

        Box(
            modifier = Modifier
                .width(TimeRangeWidth)
                .height(TimeRangeItemHeight),
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(DurationOptions) { hours ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TimeRangeItemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$hours hr",
                            color = AppColors.Label,
                            fontSize = LabelFontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = LabelFont,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
            )
        }

        ArrowButton(icon = Icons.Filled.KeyboardArrowDown, label = "Increase time target", onClick = { step(1) })
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ArrowWrapperHeight)
            .touchableOpacity(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(ArrowIconSize + 10.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TimeRangePreview() {
    TimeRange(listState = rememberLazyListState(initialFirstVisibleItemIndex = 4))
}
