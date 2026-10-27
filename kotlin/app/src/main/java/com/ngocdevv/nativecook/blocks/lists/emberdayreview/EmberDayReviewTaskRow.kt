package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.EaseOutStrong

/**
 * One action of the Today list (`styles.task` inside `PressScale scale={0.98}`): the round [EmberDayReviewCheck] and
 * the title, at least 50 dp tall with 8 dp of padding. An action typed by the user ([animateIn]) enters with a 320 ms
 * rise from 10 dp below and a fade, on the strong ease-out.
 */
@Composable
internal fun EmberDayReviewTaskRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    animateIn: Boolean,
    reduced: Boolean,
    onToggle: () -> Unit,
) {
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn) {
            enter.animateTo(1f, tween(320, easing = EaseOutStrong))
        }
    }
    EmberDayReviewPressScale(
        checked = checked,
        enabled = enabled,
        reduced = reduced,
        scale = 0.98f,
        onToggle = onToggle,
        modifier = Modifier
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 10f * density
            }
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmberDayReviewCheck(checked, reduced)
            EmberDayReviewText(
                text = title,
                size = 17f,
                color = if (checked) EmberDayReviewPalette.TaskDone else EmberDayReviewPalette.TaskText,
                modifier = Modifier.weight(1f),
                line = 23f,
                decoration = if (checked) TextDecoration.LineThrough else null,
            )
        }
    }
}
