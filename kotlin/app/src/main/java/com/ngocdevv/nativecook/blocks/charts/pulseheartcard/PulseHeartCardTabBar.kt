package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Colors
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Layout
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlinx.coroutines.launch

private val Titles = listOf("Insights", "Longevity", "Browse")

/** The glass lens: a tinted capsule with a sheen along its top half and a white rim. */
@Composable
private fun PulseHeartCardGlassLens(radius: Float, modifier: Modifier) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .clip(shape)
            .background(PulseHeartCardTheme.Lens.fallback)
            .background(
                Brush.verticalGradient(
                    0f to PulseHeartCardTheme.Lens.sheen,
                    PulseHeartCardTheme.Lens.SHEEN_TO to PulseHeartCardTheme.Lens.sheen.copy(alpha = 0f),
                ),
            )
            .border(PulseHeartCardTheme.Lens.RIM_WIDTH.dp, PulseHeartCardTheme.Lens.rim, shape),
    )
}

/**
 * The pin's floating tab bar: a translucent glass capsule (Android has no backdrop blur in Compose, so the
 * glass is the translucent fill, a top highlight and a white rim) and a selection lens that springs to the
 * tapped tab, stretching as it travels. Tabs are peers, so nothing else moves.
 */
@Composable
internal fun PulseHeartCardTabBar(scale: Float, still: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableIntStateOf(0) }
    val lensX = remember { Animatable(0f) }
    val stretch = remember { Animatable(1f) }
    val padding = PulseHeartCardTheme.Space.TAB_PADDING * scale
    val item = Layout.TAB_ITEM * scale
    val itemHeight = Layout.TAB_ITEM_HEIGHT * scale
    val barShape = RoundedCornerShape((Layout.TAB_RADIUS * scale).dp)

    fun select(index: Int) {
        if (index == active) return
        active = index
        scope.launch {
            if (still) lensX.snapTo(index.toFloat()) else lensX.animateTo(index.toFloat(), PulseHeartCardTheme.TabMotion.lens)
        }
        if (!still) {
            scope.launch {
                stretch.animateTo(PulseHeartCardTheme.TabMotion.STRETCH, tween(PulseHeartCardTheme.TabMotion.STRETCH_MS, easing = EaseOutStrong))
                stretch.animateTo(1f, PulseHeartCardTheme.TabMotion.settle)
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier
            .size((Layout.TAB_WIDTH * scale).dp, (Layout.TAB_HEIGHT * scale).dp)
            .shadow(6.dp * scale, barShape, ambientColor = Colors.shadow, spotColor = Colors.shadow)
            .clip(barShape)
            .background(Colors.tabBar)
            .background(Brush.verticalGradient(listOf(Colors.tabGlassTint, Colors.tabGlassTint.copy(alpha = 0f))))
            .border(1.dp, Colors.tabBorder, barShape),
    ) {
        // The lens sits in the padding box (inside the 1 dp border).
        PulseHeartCardGlassLens(
            radius = itemHeight / 2,
            modifier = Modifier
                .offset(x = (1 + padding).dp, y = (1 + padding).dp)
                .size(item.dp, itemHeight.dp)
                .graphicsLayer {
                    translationX = lensX.value * item * density.density
                    scaleX = stretch.value
                },
        )
        Titles.forEachIndexed { index, title ->
            val selected = index == active
            val tint = if (selected) Colors.activeBlue else Colors.ink
            Box(
                Modifier
                    .offset(x = (1 + padding + index * item).dp, y = padding.dp)
                    .size(item.dp, itemHeight.dp)
                    .semantics { this.selected = selected }
                    .pulseHeartCardPress(Role.Tab, title) { select(index) },
            ) {
                Box(Modifier.offset(x = ((item - Layout.TAB_ICON * scale) / 2).dp, y = ((Layout.TAB_ICON_CENTRE - Layout.TAB_ICON / 2) * scale).dp)) {
                    when (index) {
                        0 -> PulseHeartCardHeartIcon(Layout.TAB_ICON * scale, tint)
                        1 -> PulseHeartCardApertureIcon(Layout.TAB_ICON * scale, tint)
                        else -> PulseHeartCardBrowseIcon(Layout.TAB_ICON * scale, tint)
                    }
                }
                PulseHeartCardLineBox(
                    text = title,
                    size = Layout.TAB_LABEL_SIZE * scale,
                    line = Layout.TAB_LABEL_LINE * scale,
                    weight = FontWeight.SemiBold,
                    color = tint,
                    width = item.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = (Layout.TAB_LABEL_TOP * scale).dp),
                )
            }
        }
    }
}
