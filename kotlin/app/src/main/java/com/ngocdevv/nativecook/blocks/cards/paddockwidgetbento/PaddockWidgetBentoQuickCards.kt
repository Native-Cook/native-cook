package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex


@Composable
internal fun PaddockWidgetBentoQuickCards(
    scale: Float,
    fonts: PaddockFonts,
    runtime: PaddockWidgetBentoRuntime,
    onTap: (PaddockWidgetBentoCardID) -> Unit,
    onMove: (PaddockWidgetBentoCardID, Int) -> Unit,
) {
    val density = LocalDensity.current.density
    val px = scale * density
    val book = rememberPaddockTextBook(scale, fonts)
    val grid = rememberVectorPainter(Icons.Filled.GridView)
    val controller = rememberVectorPainter(Icons.Filled.SportsEsports)
    val q = PaddockTheme.quick
    PaddockWidgetBentoCardID.entries.forEach { id ->
        val selected by remember(runtime, id) { derivedStateOf { runtime.frame.value.selectedID == id } }
        val fill = listOf(PaddockTheme.violet, PaddockTheme.orange, PaddockTheme.yellow)[id.ordinal]
        val mid = q.height / 2
        Box(
            Modifier.offset((q.left * scale).dp, (PaddockWidgetBentoMotion.slotY[0] * scale).dp)
                .requiredSize((q.width * scale).dp, (q.height * scale).dp)
                .zIndex(if (selected) 5f else 1f)
                .graphicsLayer {
                    val f = runtime.frame.value
                    val isSelected = f.selectedID == id
                    val pose = PaddockWidgetBentoMotion.pitTile(id.ordinal, runtime.pit.value)
                    val lean = when {
                        isSelected -> f.rotationDegrees
                        runtime.reducedMotion -> 0.0
                        else -> PaddockWidgetBentoMotion.jiggle(id.ordinal, if (f.loopTime >= 0.0) f.loopTime else runtime.clock.value, f.handlesOpacity)
                    }
                    val lift = if (isSelected) f.selectedScale else 1.0
                    translationX = (pose.x * px).toFloat()
                    translationY = (((f.cardY[id] ?: PaddockWidgetBentoMotion.slotY[0]) - PaddockWidgetBentoMotion.slotY[0] + pose.y) * px).toFloat()
                    alpha = (f.cardOpacity[id] ?: 1.0).toFloat()
                    scaleX = (lift * pose.sx).toFloat()
                    scaleY = (lift * pose.sy).toFloat()
                    rotationZ = (pose.rotate + lean).toFloat()
                }
                .dropShadow(CircleShape) {
                    val f = runtime.frame.value
                    val isSelected = f.selectedID == id
                    radius = (if (isSelected) f.shadowRadius.toFloat() else PaddockWidgetBentoMotion.restShadow.toFloat()) * px
                    offset = Offset(0f, 4f * px)
                    color = Color.Black.copy(alpha = if (isSelected) 0.28f else 0f)
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = id.cardName()
                    onClick {
                        onTap(id)
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction("Move up") { onMove(id, -1); true },
                        CustomAccessibilityAction("Move down") { onMove(id, 1); true },
                        CustomAccessibilityAction("Done") { runtime.done(); true },
                    )
                },
        ) {
            Box(
                Modifier.requiredSize((q.width * scale).dp, (q.height * scale).dp)
                    .background(fill, RoundedCornerShape((PaddockTheme.radiusQuick * scale).dp))
                    .drawBehind {
                        with(book) {
                            when (id) {
                                PaddockWidgetBentoCardID.Laps -> {
                                    val left = (PaddockTheme.badgeCenterX - PaddockTheme.badgeWidth / 2 + PaddockTheme.badgeStroke / 2) * px
                                    val top = (mid - PaddockTheme.badgeHeight / 2 + PaddockTheme.badgeStroke / 2) * px
                                    drawRoundRect(
                                        Color.White, Offset(left, top),
                                        Size((PaddockTheme.badgeWidth - PaddockTheme.badgeStroke) * px, (PaddockTheme.badgeHeight - PaddockTheme.badgeStroke) * px),
                                        CornerRadius(PaddockTheme.badgeHeight / 2 * px), Stroke(PaddockTheme.badgeStroke * px),
                                    )
                                    val tn = layout("TN", PaddockTheme.badgeSize, PaddockFace.Black, -0.4f)
                                    drawText(
                                        tn, color = Color.White,
                                        topLeft = Offset(PaddockTheme.badgeCenterX * px - tn.size.width / 2f, mid * px - tn.size.height / 2f),
                                    )
                                    line("every lap", PaddockTheme.violetX, PaddockTheme.violetBaseline[0], PaddockTheme.violetSize, PaddockFace.ExtraBold, Color.White, PaddockTheme.violetTracking)
                                    line("counts", PaddockTheme.violetX, PaddockTheme.violetBaseline[1], PaddockTheme.violetSize, PaddockFace.ExtraBold, Color.White, PaddockTheme.violetTracking)
                                }
                                PaddockWidgetBentoCardID.Reflex -> {
                                    line("Race day", PaddockTheme.tileInset, PaddockTheme.eyebrow, PaddockTheme.eyebrowSize, PaddockFace.SemiBold, Color.White, PaddockTheme.eyebrowTracking)
                                    line("Reflex test", PaddockTheme.tileInset, PaddockTheme.title, PaddockTheme.tileTitleSize, PaddockFace.Bold, Color.White, PaddockTheme.tileTitleTracking)
                                }
                                PaddockWidgetBentoCardID.HomeCards -> {
                                    paddockIcon(grid, PaddockTheme.tileMarkCenter, mid, PaddockTheme.cardIcon, Color.Black, px)
                                    line("custom", PaddockTheme.tileTextAfterMark, PaddockTheme.eyebrow, PaddockTheme.eyebrowSize, PaddockFace.SemiBold, Color.Black, PaddockTheme.eyebrowTracking)
                                    line("home cards", PaddockTheme.tileTextAfterMark, PaddockTheme.title, PaddockTheme.tileTitleSize, PaddockFace.Bold, Color.Black, PaddockTheme.tileTitleTracking)
                                }
                            }
                        }
                    },
            )
            if (id == PaddockWidgetBentoCardID.Reflex) {
                Box(
                    Modifier.offset(((q.width - PaddockTheme.chipTrailing - PaddockTheme.chipWidth) * scale).dp, ((mid - PaddockTheme.chipHeight / 2) * scale).dp)
                        .requiredSize((PaddockTheme.chipWidth * scale).dp, (PaddockTheme.chipHeight * scale).dp)
                        .graphicsLayer { alpha = (1.0 - runtime.frame.value.handlesOpacity).toFloat() }
                        .background(Color.White, RoundedCornerShape((PaddockTheme.radiusChip * scale).dp)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(controller, null, Modifier.requiredSize((PaddockTheme.chipIcon * scale).dp), tint = PaddockTheme.orange)
                }
            }
            Icon(
                Icons.Filled.Menu, null,
                Modifier.offset(((q.width - PaddockTheme.handleTrailing - PaddockTheme.handleSize) * scale).dp, ((mid - PaddockTheme.handleSize / 2) * scale).dp)
                    .requiredSize((PaddockTheme.handleSize * scale).dp)
                    .graphicsLayer {
                        val opacity = runtime.frame.value.handlesOpacity
                        alpha = if (opacity > 0.001) opacity.toFloat() else 0f
                    },
                tint = if (id == PaddockWidgetBentoCardID.HomeCards) PaddockTheme.ink55Black else PaddockTheme.ink75White,
            )
        }
    }
}
