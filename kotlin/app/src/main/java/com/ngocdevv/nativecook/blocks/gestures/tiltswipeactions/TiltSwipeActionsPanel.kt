package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** TabPanel.tsx TABS, in the order of FLAT.tabs. */
private val TABS = listOf(
    TabKind.Pie to "Overview",
    TabKind.Wallet to "Wallet",
    TabKind.Person to "Spending",
)

private class MenuEntry(val label: String, val icon: FeatherIcon, val tint: Color)

/** The source's menu: three items, each with a tinted round glyph on the right. */
private val MENU = listOf(
    MenuEntry("Income", FeatherIcon.Plus, TiltColor.income),
    MenuEntry("Expense", FeatherIcon.Minus, TiltColor.pink),
    MenuEntry("Transfer", FeatherIcon.ArrowUpRight, TiltColor.transfer),
)

private val GLOW_BLEED = 60.dp
private val MENU_BLEED = 40.dp

/** The source's pale tab panel and its FAB, which opens Income / Expense / Transfer. */
@Composable
internal fun TabPanel(screen: TiltScreen, reduceMotion: Boolean) {
    val tab = screen.tab
    val open = screen.menuOpen
    Box(Modifier.fillMaxSize()) {
        EdgeMask()
        Panel(screen, tab, reduceMotion)
        Scrim(screen, open)
        Menu(screen, open, reduceMotion)
        FabGlow(screen, reduceMotion)
        Fab(screen, open, reduceMotion)
    }
}

@Composable
private fun EdgeMask() {
    Box(
        Modifier
            .at(0f, TiltFlat.Panel.top - TiltDetail.panelEdgeMaskOverlap)
            .size(
                TiltFlat.Panel.left.dp,
                (PLANE_H - TiltFlat.Panel.top + TiltDetail.panelEdgeMaskOverlap).dp,
            )
            .background(TiltColor.deskEdge),
    )
}

/** The panel runs from near the screen's left edge to the right, under the FAB. */
@Composable
private fun Panel(screen: TiltScreen, tab: Int, reduceMotion: Boolean) {
    val lensShadow = remember { TiltShadow(TiltColor.slabShadow, 10f) }
    Box(
        modifier = Modifier
            .at(TiltFlat.Panel.left, TiltFlat.Panel.top)
            .size((PLANE_W - TiltFlat.Panel.left).dp, (PLANE_H - TiltFlat.Panel.top).dp)
            .drawBehind {
                val panelPath = cornerPath(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        topLeft = TiltFlat.Panel.radiusTop.dp.toPx(),
                        topRight = 0f,
                        bottomRight = 0f,
                        bottomLeft = TiltFlat.Panel.radiusBottom.dp.toPx(),
                    )
                drawPath(
                    path = panelPath,
                    brush = Brush.verticalGradient(
                        0f to TiltColor.panelEdge,
                        TiltDetail.panelFadeHeight / TiltDetail.panelFadeBasis to TiltColor.panel,
                        1f to TiltColor.panel,
                        endY = size.height,
                    ),
                )
            },
    ) {
        // A tinted glass lens slides under the active tab (Liquid Glass), on a spring.
        Box(
            modifier = Modifier
                .at(0f, TiltFlat.tabY - TiltFlat.Panel.top - TiltLayout.lens / 2f)
                .size(TiltLayout.lens.dp)
                .graphicsLayer {
                    translationX = (screen.lensX.value - TiltFlat.Panel.left - TiltLayout.lens / 2f).dp.toPx()
                }
                .drawBehind {
                    inDp {
                        val diameter = TiltLayout.lens
                        lensShadow.draw(this, 0f, 4f, diameter, diameter, diameter / 2f, 0.18f)
                        drawCircle(TiltColor.lens, radius = diameter / 2f, center = Offset(diameter / 2f, diameter / 2f))
                        val hairline = 1f / density
                        drawCircle(
                            TiltColor.lensRim,
                            radius = diameter / 2f - hairline / 2f,
                            center = Offset(diameter / 2f, diameter / 2f),
                            style = Stroke(hairline),
                        )
                        drawRoundRect(
                            TiltColor.glassEdge,
                            topLeft = Offset(TiltDetail.lensHighlightInset, TiltDetail.lensHighlightTop),
                            size = Size(
                                diameter - TiltDetail.lensHighlightInset * 2f,
                                TiltDetail.lensHighlightHeight,
                            ),
                            cornerRadius = CornerRadius(1f),
                        )
                    }
                },
        )
        TABS.forEachIndexed { index, (kind, label) ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (pressed && !reduceMotion) TiltDetail.tabPressScale else 1f,
                animationSpec = tween(
                    durationMillis = if (pressed) TiltTiming.pressIn else TiltTiming.pressOut,
                    easing = EASE_OUT,
                ),
                label = "tab press",
            )
            Box(
                modifier = Modifier
                    .at(
                        TiltFlat.tabs[index] - TiltFlat.Panel.left - TiltDetail.tabHitSize / 2f,
                        TiltFlat.tabY - TiltFlat.Panel.top - TiltDetail.tabHitSize / 2f,
                    )
                    .size(TiltDetail.tabHitSize.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .semantics {
                        role = Role.Tab
                        contentDescription = label
                        selected = index == tab
                    }
                    .clickable(interactionSource = source, indication = null) { screen.selectTab(index) },
                contentAlignment = Alignment.Center,
            ) {
                TabGlyph(kind, if (index == tab) TiltColor.panelIconActive else TiltColor.panelIcon)
            }
        }
    }
}

@Composable
private fun Scrim(screen: TiltScreen, open: Boolean) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = min(screen.menuProgress.value, 1f) }
            .background(TiltColor.menuScrim)
            .then(
                if (open) {
                    Modifier
                        .semantics { contentDescription = "Close menu" }
                        .clickable(interactionSource = source, indication = null) { screen.showMenu(false) }
                } else {
                    Modifier
                },
            ),
    )
}

/** One white shape that the FAB sits in: it grows out of the FAB's corner, as in the source. */
@Composable
private fun Menu(screen: TiltScreen, open: Boolean, reduceMotion: Boolean) {
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.menuShadowRadius) }
    val block = remember { MutableInteractionSource() }
    val menu = TiltFlat.Menu
    val width = menu.width
    val bleed = MENU_BLEED.value
    Box(
        modifier = Modifier
            .at(menu.left, menu.top)
            .size(width.dp, menu.height.dp)
            .bleed(MENU_BLEED)
            .graphicsLayer {
                // Right bottom of the menu itself, not of the larger layer around it.
                transformOrigin = TransformOrigin(
                    (width + bleed) / (width + 2f * bleed),
                    (menu.height + bleed) / (menu.height + 2f * bleed),
                )
                alpha = min(screen.menuProgress.value, 1f)
                if (!reduceMotion) {
                    val scale = TiltDetail.menuStartScale +
                        (1f - TiltDetail.menuStartScale) * screen.menuProgress.value
                    scaleX = scale
                    scaleY = scale
                }
            }
            .drawBehind {
                surface(MENU_BLEED) { x, y, w, h ->
                    shadow.draw(
                        this,
                        x,
                        y + TiltDetail.menuShadowY,
                        w,
                        h,
                        menu.radius,
                        TiltDetail.menuShadowOpacity,
                    )
                    drawRoundRect(TiltColor.white, Offset(x, y), Size(w, h), CornerRadius(menu.radius))
                }
            }
            .padding(MENU_BLEED)
            .clickable(interactionSource = block, indication = null, enabled = open) { },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = menu.padTop.dp, start = menu.padLeft.dp, end = menu.padRight.dp),
        ) {
            MENU.forEachIndexed { index, entry ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (pressed && !reduceMotion) TiltDetail.menuPressScale else 1f,
                    animationSpec = tween(
                        durationMillis = if (pressed) TiltTiming.pressIn else TiltTiming.pressOut,
                        easing = EASE_OUT,
                    ),
                    label = "menu press",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(menu.item.dp)
                        .graphicsLayer {
                            val denominator = 1f - 2f * TiltTiming.menuStagger
                            val rowProgress = (
                                (screen.menuProgress.value - index * TiltTiming.menuStagger) / denominator
                                ).coerceIn(0f, 1f)
                            alpha = rowProgress
                            translationY = if (reduceMotion) {
                                0f
                            } else {
                                (1f - rowProgress) * TiltDetail.menuRowRise.dp.toPx()
                            }
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .semantics { role = Role.Button }
                        .clickable(interactionSource = source, indication = null, enabled = open) {
                            screen.showMenu(false)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TiltLabel(entry.label, TiltType.menu, TiltColor.menuInk)
                    Box(
                        modifier = Modifier
                            .size(menu.icon.dp)
                            .clip(CircleShape)
                            // `${tint}1A`: the source tint at ten percent alpha.
                            .background(entry.tint.copy(alpha = TiltDetail.menuTintAlpha)),
                        contentAlignment = Alignment.Center,
                    ) { FeatherGlyph(entry.icon, TiltDetail.menuGlyphSize, entry.tint) }
                }
            }
        }
    }
}

/** Ambient: a pink glow under the FAB breathes on the loop clock. */
@Composable
private fun FabGlow(screen: TiltScreen, reduceMotion: Boolean) {
    val shadow = remember { TiltShadow(TiltColor.pink, TiltDetail.fabGlowShadowRadius) }
    val size = TiltFlat.Fab.size + TiltDetail.fabGlowExtra
    Box(
        modifier = Modifier
            .at(
                TiltFlat.Fab.left - TiltDetail.fabGlowInsetX,
                TiltFlat.Fab.top - TiltDetail.fabGlowInsetTop,
            )
            .size(size.dp)
            .bleed(GLOW_BLEED)
            .graphicsLayer {
                val breath = if (reduceMotion) {
                    0.5f
                } else {
                    (sin(2f * PI.toFloat() * screen.demo.ambient / TiltTiming.breath) + 1f) / 2f
                }
                alpha = TiltDetail.fabGlowOpacityBase + TiltDetail.fabGlowOpacityRange * breath
                val scale = TiltDetail.fabGlowScaleBase + TiltDetail.fabGlowScaleRange * breath
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                surface(GLOW_BLEED) { x, y, w, h ->
                    val radius = TiltFlat.Fab.radius + TiltDetail.fabGlowRadiusExtra
                    shadow.draw(this, x, y + TiltDetail.fabGlowShadowY, w, h, radius, 1f)
                    drawRoundRect(TiltColor.pinkGlow, Offset(x, y), Size(w, h), CornerRadius(radius))
                }
            },
    )
}

@Composable
private fun Fab(screen: TiltScreen, open: Boolean, reduceMotion: Boolean) {
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.fabShadowRadius) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) TiltTiming.pressScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) TiltTiming.pressIn else TiltTiming.pressOut,
            easing = EASE_OUT,
        ),
        label = "fab press",
    )
    Box(
        modifier = Modifier
            .at(TiltFlat.Fab.left, TiltFlat.Fab.top)
            .size(TiltFlat.Fab.size.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .drawBehind {
                inDp {
                    val s = TiltFlat.Fab.size
                    shadow.draw(
                        this,
                        0f,
                        TiltDetail.fabShadowY,
                        s,
                        s,
                        TiltFlat.Fab.radius,
                        TiltDetail.fabShadowOpacity,
                    )
                    drawRoundRect(TiltColor.white, Offset.Zero, Size(s, s), CornerRadius(TiltFlat.Fab.radius))
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = if (open) "Close menu" else "Add transaction"
                stateDescription = if (open) "Expanded" else "Collapsed"
            }
            .clickable(interactionSource = source, indication = null) { screen.showMenu(!open) },
        contentAlignment = Alignment.Center,
    ) {
        // The source's FAB shows a pink +; open, it turns plum.
        Box(Modifier.graphicsLayer { alpha = 1f - min(screen.menuProgress.value, 1f) }) {
            FeatherGlyph(FeatherIcon.Plus, TiltDetail.fabGlyphSize, TiltColor.pink)
        }
        Box(Modifier.graphicsLayer { alpha = min(screen.menuProgress.value, 1f) }) {
            FeatherGlyph(FeatherIcon.Plus, TiltDetail.fabGlyphSize, TiltColor.menuPlus)
        }
    }
}
