package com.ngocdevv.nativecook.blocks.media.orbitphotoring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// The switcher: a dark capsule of three segments, a light pill sliding under the chosen one.
private const val PICKER_PAD = 3f
// Reanimated's spring of 380 ms (perceptual) at a damping ratio of 0.86 works out to this
// stiffness for a unit mass.
private val PICKER_SPRING = spring<Float>(dampingRatio = 0.86f, stiffness = 629.4f)

private val SegmentText = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    letterSpacing = 0.2.sp,
)

/** A segment's place in the row, in dp. */
private class OrbitSegmentFrame(val x: Float, val width: Float)

/** Ring · Tumble · Gyro, at the top of the screen. */
@Composable
internal fun OrbitStylePicker(
    selected: Int,
    onPick: (Int) -> Unit,
    ink: Color,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val frames = remember { mutableStateMapOf<Int, OrbitSegmentFrame>() }
    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    val frame = frames[selected]
    LaunchedEffect(frame?.x, frame?.width, selected, reduced) {
        val f = frame ?: return@LaunchedEffect
        if (pillW.value == 0f || reduced) {
            pillX.snapTo(f.x)
            pillW.snapTo(f.width)
        } else {
            // Both springs run together; a new pick interrupts them and keeps their speed.
            coroutineScope {
                launch { pillX.animateTo(f.x, PICKER_SPRING) }
                launch { pillW.animateTo(f.width, PICKER_SPRING) }
            }
        }
    }
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .background(Color.White.copy(alpha = 0.07f), shape)
            .border((1f / density.density).dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(PICKER_PAD.dp),
    ) {
        // The pill, drawn at the segment's place without taking part in the row's size.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val w = pillW.value.dp.roundToPx().coerceAtLeast(0)
                    val h = 30.dp.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(0, 0) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    translationX = pillX.value * density.density
                    alpha = if (pillW.value > 0f) 1f else 0f
                }
                .background(ink, shape),
        )
        Row(Modifier.selectableGroup()) {
            OrbitRingStyle.entries.forEachIndexed { index, style ->
                val chosen = index == selected
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier
                        .onPlaced {
                            val x = it.positionInParent().x / density.density
                            val w = it.size.width / density.density
                            val old = frames[index]
                            if (old == null || old.x != x || old.width != w) frames[index] = OrbitSegmentFrame(x, w)
                        }
                        .selectable(selected = chosen, interactionSource = source, indication = null, role = Role.Tab) { onPick(index) }
                        .semantics { contentDescription = "${style.label} style" }
                        .alpha(if (pressed) 0.7f else 1f)
                        .height(30.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        style.label,
                        style = SegmentText,
                        color = if (chosen) Color(0xFF0B0B0C) else ink,
                        maxLines = 1,
                        modifier = Modifier.alpha(if (chosen) 1f else 0.62f),
                    )
                }
            }
        }
    }
}
