package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sqrt


/** One dock tab: its destination and the glyph. */
internal val PaddockDockTabs = listOf(
    PaddockWidgetBentoDestination.Home,
    PaddockWidgetBentoDestination.Calendar,
    PaddockWidgetBentoDestination.Results,
    PaddockWidgetBentoDestination.Circuits,
    PaddockWidgetBentoDestination.Customize,
)

/**
 * The pin's floating dock (LiquidDock.tsx): the feed blurs behind the capsule (RenderEffect, API 31+), and a lighter
 * lens sits on the active tab. Tapping a tab springs the lens across, stretching it on the way like a drop of glass.
 */
@Composable
internal fun PaddockDock(
    scale: Float,
    selectedTab: PaddockWidgetBentoDestination,
    feedLayer: GraphicsLayer,
    viewportHeight: Float,
    quiet: Boolean,
    modifier: Modifier,
    onSelect: (PaddockWidgetBentoDestination) -> Unit,
) {
    val density = LocalDensity.current.density
    val px = scale * density
    val index = PaddockDockTabs.indexOf(selectedTab).coerceAtLeast(0)
    val pitch = (PaddockTheme.dockWidth - 2 * PaddockTheme.dockPad) / PaddockDockTabs.size
    val lensX = remember { Animatable(index.toFloat()) }
    val stretch = remember { Animatable(1f) }
    LaunchedEffect(index, quiet) {
        if (quiet) {
            lensX.animateTo(index.toFloat(), tween(160, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
            return@LaunchedEffect
        }
        val response = 0.42f
        val spec = spring<Float>(dampingRatio = 0.72f, stiffness = ((2 * PI / response) * (2 * PI / response)).toFloat())
        kotlinx.coroutines.coroutineScope {
            launch { lensX.animateTo(index.toFloat(), spec) }
            launch {
                stretch.animateTo(1.16f, tween(110, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
                val settle = 0.32f
                stretch.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = ((2 * PI / settle) * (2 * PI / settle)).toFloat()))
            }
        }
    }
    val shape = RoundedCornerShape((PaddockTheme.dockHeight / 2 * scale).dp)
    Box(
        modifier.size((PaddockTheme.dockWidth * scale).dp, (PaddockTheme.dockHeight * scale).dp)
            .clip(shape)
            .border(0.5.dp, PaddockTheme.dockStroke, shape),
    ) {
        PaddockWidgetBentoDockMaterial(feedLayer, scale, viewportHeight, Modifier.matchParentSize())
        Box(
            Modifier.offset(((PaddockTheme.dockPad + (pitch - PaddockTheme.dockLensWidth) / 2) * scale).dp, (PaddockTheme.dockLensInset * scale).dp)
                .requiredSize((PaddockTheme.dockLensWidth * scale).dp, (PaddockTheme.dockLensHeight * scale).dp)
                .graphicsLayer {
                    translationX = lensX.value * pitch * px
                    scaleX = stretch.value
                    scaleY = 1f / sqrt(stretch.value)
                }
                .clip(RoundedCornerShape((PaddockTheme.radiusLens * scale).dp))
                .background(PaddockTheme.dockLens)
                .border(0.5.dp, PaddockTheme.dockLensStroke, RoundedCornerShape((PaddockTheme.radiusLens * scale).dp)),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = (PaddockTheme.dockPad * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            PaddockDockTabs.forEach { tab ->
                val active = selectedTab == tab
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier.size((pitch * scale).dp, (PaddockTheme.dockHeight * scale).dp)
                        .graphicsLayer {
                            val press = if (pressed && !quiet) 0.92f else 1f
                            scaleX = press
                            scaleY = press
                        }
                        .semantics { selected = active }
                        .clickable(interaction, indication = null, role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.dockIcon(), tab.name,
                        Modifier.size((PaddockTheme.dockIconSize * scale).dp),
                        tint = if (active) PaddockTheme.dockIconActive else PaddockTheme.dockIcon,
                    )
                }
            }
        }
    }
}
