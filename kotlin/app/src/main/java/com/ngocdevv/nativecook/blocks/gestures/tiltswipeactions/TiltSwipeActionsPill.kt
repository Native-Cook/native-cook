package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.min

private val PILL_BLEED = 40.dp
private const val PILL_LEFT = TiltLayout.gutter
private const val PILL_RIGHT = TiltLayout.gutter + TiltLayout.fab + TiltDetail.pillRightExtra
private const val PILL_WIDTH = PLANE_W - PILL_LEFT - PILL_RIGHT
private const val PILL_TOP = PLANE_H - PILL_BOTTOM - TiltLayout.pillHeight

/**
 * UndoPill.tsx: "Groceries deleted - Undo", above the tab panel. Hero morph: the pill grows out
 * of the deleted row's Delete tile: it starts at the row's height, tile-sized at the right, and
 * travels down while it widens into the pill.
 */
@Composable
internal fun UndoPill(screen: TiltScreen, reduceMotion: Boolean) {
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.pillShadowRadius) }
    val bleed = PILL_BLEED.value
    val title = screen.pillTitle
    Box(
        modifier = Modifier
            .at(PILL_LEFT, PILL_TOP)
            .size(PILL_WIDTH.dp, TiltLayout.pillHeight.dp)
            .bleed(PILL_BLEED)
            .graphicsLayer {
                if (reduceMotion) {
                    alpha = min(screen.pill.value, 1f)
                } else {
                    val p = screen.pill.value.coerceIn(0f, 1.06f)
                    // transformOrigin 'right': the right edge of the pill itself, not of the larger layer.
                    transformOrigin = TransformOrigin((PILL_WIDTH + bleed) / (PILL_WIDTH + 2f * bleed), 0.5f)
                    alpha = min(p * 3f, 1f)
                    translationY = ((1f - p) * screen.pillFrom.value).dp.toPx()
                    scaleX = 0.22f + 0.78f * p
                    scaleY = 1f + 0.08f * (1f - p)
                }
            }
            .drawBehind {
                surface(PILL_BLEED) { x, y, w, h ->
                    val radius = TiltLayout.pillHeight / 2f
                    shadow.draw(
                        this,
                        x,
                        y + TiltDetail.pillShadowY,
                        w,
                        h,
                        radius,
                        TiltDetail.pillShadowOpacity,
                    )
                    drawRoundRect(TiltColor.plum, Offset(x, y), Size(w, h), CornerRadius(radius))
                }
            }
            .padding(PILL_BLEED)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = TiltSpace.xl.dp, end = TiltSpace.s.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TiltLabel(
                text = "$title deleted",
                style = TiltType.pill,
                color = TiltColor.white,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            val source = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .height(TiltDetail.undoHitHeight.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Undo, restore $title"
                    }
                    .clickable(interactionSource = source, indication = null) { screen.onUndoPress() }
                    .padding(horizontal = TiltSpace.m.dp),
                horizontalArrangement = Arrangement.spacedBy(TiltSpace.s.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeatherGlyph(FeatherIcon.RotateCcw, 16f, TiltColor.pink)
                TiltLabel("Undo", TiltType.pillAction, TiltColor.pink)
            }
        }
    }
}
