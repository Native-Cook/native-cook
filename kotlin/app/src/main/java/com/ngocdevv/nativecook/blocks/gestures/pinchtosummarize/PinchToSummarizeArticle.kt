package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest

/** The full article keeps its layout while the outer layer crops and transforms it. */
@Composable
internal fun PinchToSummarizeArticle(stage: PtsStage, insetBottom: Float, state: PtsState) {
    val scroll = rememberScrollState()
    val reading = state.mode == PtsMode.Reading
    LaunchedEffect(state.isScrollLocked) {
        if (state.isScrollLocked) scroll.stopScroll()
    }
    LaunchedEffect(scroll, state) {
        snapshotFlow { state.readScroll.value }.collectLatest { y ->
            scroll.scrollTo(y.toInt().coerceAtLeast(0))
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val frame = ptsPageFrame(
                stage, state.p.value, state.lift.value, state.driftX.value, state.driftY.value,
                ptsBreathing(state.think.value, state.clock.value, PtsTiming.breathe, PtsTiming.breathePeriod.toInt()),
            )
            translationX = frame.x.dp.toPx()
            translationY = frame.y.dp.toPx()
            scaleX = frame.scale
            scaleY = frame.scale
            alpha = state.pageAlpha.value
            transformOrigin = TransformOrigin(0f, 0f)
            // A real layer outline clips drawing AND hit testing outside the visible card.
            shape = object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                    val radius = with(density) { (frame.radius / frame.scale).dp.toPx() }
                    val height = with(density) { frame.clipHeight.dp.toPx() }
                    return Outline.Rounded(RoundRect(0f, 0f, size.width, height, CornerRadius(radius)))
                }
            }
            clip = true
        }.background(PtsColor.page).then(
            if (reading) Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction("Summarize article") {
                    state.requestSummary()
                    true
                })
            } else Modifier.clearAndSetSemantics {},
        ),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = -ptsPageFrame(stage, state.p.value, 0f, 0f, 0f).trim.dp.toPx()
        }.background(PtsColor.page)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll, enabled = !state.isScrollLocked)
                    .padding(top = stage.insetTop.dp, bottom = (PtsLayout.toolbarHeight + insetBottom + 32f).dp),
            ) {
                PtsMasthead()
                PtsArticleHero(stage.width)
                PtsArticleBody(state)
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height((PtsLayout.toolbarHeight + insetBottom + PtsLayout.toolbarFade).dp)
                    .graphicsLayer { alpha = 1f - ptsClamp01(state.p.value / PtsTiming.toolbarFadeSpan) }
                    .background(Brush.verticalGradient(colorStops = arrayOf(0f to PtsColor.pageClear, .45f to PtsColor.page))),
            )
            PtsToolbar(
                state, reading,
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = (PtsLayout.toolbarCenterFromBottom - PtsLayout.toolbarHeight / 2f).dp)
                    .graphicsLayer { alpha = 1f - ptsClamp01(state.p.value / PtsTiming.toolbarFadeSpan) },
            )
        }
        if (!reading) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = null, indication = null,
                onClick = state::requestArticle,
            ))
        }
    }
}

@Composable
private fun PtsMasthead() {
    val cutout = remember { PathParser().parsePathString("M17 4.6a8.5 8.5 0 0 1 0 12.8").toPath() }
    Row(
        Modifier.fillMaxWidth().height(PtsLayout.mastheadHeight.dp)
            .padding(horizontal = PtsLayout.gutter.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 34.dp, height = 22.dp).drawBehind {
                val stroke = 3.4.dp.toPx()
                drawCircle(PtsColor.ink, 8.5.dp.toPx(), Offset(11.dp.toPx(), 11.dp.toPx()), style = Stroke(stroke))
                drawCircle(PtsColor.ink, 8.5.dp.toPx(), Offset(23.dp.toPx(), 11.dp.toPx()), style = Stroke(stroke))
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(cutout, PtsColor.page, style = Stroke(1.6f))
                }
            })
            Text(PtsCopy.wordmark, style = PtsType.wordmark, modifier = Modifier.semantics { heading() })
        }
        Box(Modifier.size(28.dp).clip(CircleShape).background(PtsColor.toolbar), contentAlignment = Alignment.Center) {
            PtsIonicon("person", PtsColor.inkSoft, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PtsArticleHero(stageWidth: Float) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PtsLayout.panelInset.dp)
            .clip(RoundedCornerShape(PtsLayout.panelRadius.dp)).background(PtsColor.panel)
            .padding(top = PtsLayout.panelPadTop.dp, bottom = PtsLayout.panelPadBottom.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(PtsLayout.pillHeight.dp).clip(RoundedCornerShape(8.dp)).background(PtsColor.pill).padding(horizontal = PtsLayout.pillPadX.dp), contentAlignment = Alignment.Center) { Text(PtsCopy.kicker, style = PtsType.pill) }
        Text(PtsCopy.headline, style = PtsType.headline, modifier = Modifier.padding(top = PtsLayout.pillGap.dp, start = PtsLayout.panelPadX.dp, end = PtsLayout.panelPadX.dp).semantics { heading() })
        Text(PtsCopy.dek, style = PtsType.dek, modifier = Modifier.padding(top = PtsLayout.dekGap.dp, start = PtsLayout.panelPadX.dp, end = PtsLayout.panelPadX.dp))
        Image(painterResource(R.drawable.pinchtosummarize_hero), "A woman in the Halo One visor waves at the camera", Modifier.fillMaxWidth().padding(top = PtsLayout.photoGap.dp).height(((stageWidth - PtsLayout.panelInset * 2f) * PtsLayout.photoAspect).dp), contentScale = ContentScale.Crop)
        Box(Modifier.padding(top = PtsLayout.dotGap.dp, bottom = PtsLayout.bylineGap.dp).size(4.dp).clip(CircleShape).background(PtsColor.panelInkSoft))
        Text(PtsCopy.byline, style = PtsType.byline)
        Text(PtsCopy.date, style = PtsType.byline, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PtsArticleBody(state: PtsState) {
    Column(Modifier.fillMaxWidth().padding(start = PtsLayout.panelPadX.dp, end = PtsLayout.panelPadX.dp, top = PtsLayout.bodyGap.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(PtsCopy.section, style = PtsType.section)
            Spacer(Modifier.weight(1f).height((1f / LocalDensity.current.density).dp).background(PtsColor.rule))
        }
        Spacer(Modifier.height(16.dp))
        var markerIndex = 0
        PtsCopy.body.forEach { paragraph ->
            val lock = markerIndex
            val isMarked = paragraph.leadIn != null || paragraph.key != null
            val markerProgress = ptsClamp01(state.mark.value - lock) * state.markAlpha.value
            val muted = ptsMix(1f, .38f, state.dim.value / PtsMark.dimMax)
            val article = buildAnnotatedString {
                paragraph.leadIn?.let { lead ->
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .6.sp,
                            background = PtsColor.marker.copy(alpha = markerProgress),
                        ),
                    ) { append(lead) }
                }
                paragraph.key?.let { key ->
                    withStyle(SpanStyle(background = PtsColor.marker.copy(alpha = markerProgress))) {
                        append(key)
                    }
                }
                withStyle(SpanStyle(color = PtsColor.ink.copy(alpha = muted))) {
                    append(paragraph.rest)
                }
            }
            Text(article, style = PtsType.body, modifier = Modifier.padding(bottom = 16.dp))
            if (isMarked) markerIndex += 1
        }
    }
}

@Composable
private fun PtsToolbar(state: PtsState, reading: Boolean, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().height(PtsLayout.toolbarHeight.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(PtsLayout.toolbarRound.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).graphicsLayer {
                rotationZ = 8f
                translationX = cos(Math.toRadians(8.0)).toFloat() * 3.dp.toPx()
                translationY = sin(Math.toRadians(8.0)).toFloat() * 3.dp.toPx()
            }.clip(RoundedCornerShape(6.dp)).background(PtsColor.toolbarInk.copy(alpha = .25f)))
            Box(Modifier.size(22.dp).graphicsLayer {
                rotationZ = -8f
            }.clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(PtsColor.stackIcon)))
        }
        // Match the source's hitSlop=8 independently of the visible pill.
        // Compose additionally enforces its native 48 dp minimum touch height.
        Box(
            Modifier.size((PtsLayout.toolbarPill.width + 16f).dp, (PtsLayout.toolbarPill.height + 16f).dp)
                .then(if (reading) Modifier.clickable(
                    interactionSource = interaction, indication = null,
                    role = Role.Button, onClickLabel = "Summarize article", onClick = state::requestSummary,
                ).semantics { contentDescription = "Summarize article. Or pinch the page with two fingers." }
                else Modifier.clearAndSetSemantics {}),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(PtsLayout.toolbarPill.width.dp, PtsLayout.toolbarPill.height.dp)
                    .graphicsLayer {
                        scaleX = if (pressed.value) PtsTiming.pressScale else 1f
                        scaleY = scaleX
                    }.clip(CircleShape).background(PtsColor.toolbar),
                contentAlignment = Alignment.Center,
            ) { PtsIonicon("add", PtsColor.toolbarInk, Modifier.size(20.dp)) }
        }
        Box(Modifier.size(PtsLayout.toolbarRound.dp).clip(CircleShape).background(PtsColor.toolbar), contentAlignment = Alignment.Center) {
            PtsIonicon("chevron-up", PtsColor.toolbarInk, Modifier.size(16.dp))
        }
    }
}
