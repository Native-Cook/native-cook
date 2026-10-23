package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun TiltPosterParallaxInfo(
    engine: TiltPosterParallaxEngine,
    stageWidth: Dp,
    top: Dp,
    typography: TiltPosterParallaxTypography,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .offset(x = Tokens.LAYOUT.gutter.dp, y = top)
            .width(stageWidth - (Tokens.LAYOUT.gutter * 2.0).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Tokens.COPY.title,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.ink,
            fontSize = Tokens.TYPE.titleSize.sp,
            fontFamily = typography.serif,
            lineHeight = Tokens.TYPE.titleLineHeight.sp,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(Tokens.LAYOUT.infoGap.dp))
        Text(
            text = Tokens.COPY.meta,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.inkFaint,
            fontSize = Tokens.TYPE.metaSize.sp,
            fontFamily = typography.regular,
            lineHeight = Tokens.TYPE.metaLineHeight.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height((Tokens.LAYOUT.infoGap + Tokens.LAYOUT.pillsMarginTop).dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.LAYOUT.pillHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.pillGap.dp),
        ) {
            TiltPosterParallaxWatchlistPill(engine, typography, Modifier.weight(1f))
            TiltPosterParallaxPlayPill(engine, typography, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height((Tokens.LAYOUT.pillsMarginBottom + Tokens.LAYOUT.infoGap).dp))
        Text(
            text = Tokens.COPY.body,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.inkSoft,
            fontSize = Tokens.TYPE.bodySize.sp,
            fontFamily = typography.regular,
            lineHeight = Tokens.TYPE.bodyLineHeight.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Clip,
        )
        Spacer(modifier = Modifier.height(Tokens.LAYOUT.infoGap.dp))
        Row(
            modifier = Modifier
                .align(Alignment.Start)
                .clickable { expanded = !expanded }
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) Tokens.COPY.less else Tokens.COPY.more
                },
            horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.moreGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) Tokens.COPY.less else Tokens.COPY.more,
                color = Tokens.COLOR.ink,
                fontSize = Tokens.TYPE.moreSize.sp,
                fontFamily = typography.semibold,
            )
            TiltPosterParallaxChevronIcon(up = expanded)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Tokens.LAYOUT.formatsMarginTop.dp)
                .drawBehind {
                    drawLine(
                        color = Tokens.COLOR.glassBorder,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
                .padding(top = Tokens.LAYOUT.formatsPaddingTop.dp)
                .semantics { contentDescription = "Available in 4K, HDR, Dolby Atmos, with audio description" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tokens.COPY.formats.forEach { format ->
                Box(
                    modifier = Modifier
                        .height(Tokens.LAYOUT.chipHeight.dp)
                        .border(0.5.dp, Tokens.COLOR.inkFaint, RoundedCornerShape(Tokens.LAYOUT.chipRadius.dp))
                        .padding(horizontal = Tokens.LAYOUT.chipPadding.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = format,
                        color = Tokens.COLOR.inkSoft,
                        fontSize = Tokens.TYPE.chipSize.sp,
                        fontFamily = typography.semibold,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = Tokens.TYPE.chipLineHeight.sp,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TiltPosterParallaxWatchlistPill(
    engine: TiltPosterParallaxEngine,
    typography: TiltPosterParallaxTypography,
    modifier: Modifier,
) {
    val view = LocalView.current
    val progress = engine.savedMotion.coerceIn(0f, 1f)
    val background = lerp(Tokens.COLOR.glass, Tokens.COLOR.pillSaved, progress)
    val border = lerp(Tokens.COLOR.glassBorder, Tokens.COLOR.pillSavedBorder, progress)
    val pop = 1f + sin(progress * PI).toFloat() * Tokens.MORPH.pop.toFloat()
    Row(
        modifier = modifier
            .height(Tokens.LAYOUT.pillHeight.dp)
            .graphicsLayer {
                scaleX = engine.watchPress * pop
                scaleY = engine.watchPress * pop
            }
            .background(background, RoundedCornerShape((Tokens.LAYOUT.pillHeight / 2.0).dp))
            .border(0.5.dp, border, RoundedCornerShape((Tokens.LAYOUT.pillHeight / 2.0).dp))
            .padding(horizontal = Tokens.LAYOUT.pillPadding.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (engine.saved) "In your watchlist" else "Add to watchlist"
                selected = engine.saved
                onClick {
                    performTiltPosterParallaxWatchHaptic(view, engine.toggleSaved())
                    true
                }
            }
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.realTouchDown()
                    engine.setWatchPressed(true)
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp() || !change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    } finally {
                        engine.setWatchPressed(false)
                        if (released) performTiltPosterParallaxWatchHaptic(view, engine.toggleSaved())
                        engine.realTouchUp()
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.pillContentGap.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(Tokens.LAYOUT.pillIcon.dp), contentAlignment = Alignment.Center) {
            TiltPosterParallaxPlusIcon(progress)
            TiltPosterParallaxCheckIcon(progress)
        }
        Text(
            text = if (engine.saved) Tokens.COPY.added else Tokens.COPY.watchlist,
            color = if (engine.saved) Tokens.COLOR.goldInk else Tokens.COLOR.ink,
            fontSize = Tokens.TYPE.pillSize.sp,
            fontFamily = typography.semibold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TiltPosterParallaxPlayPill(
    engine: TiltPosterParallaxEngine,
    typography: TiltPosterParallaxTypography,
    modifier: Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(Tokens.LAYOUT.pillHeight.dp)
            .alpha(if (pressed) 0.8f else 1f)
            .background(Tokens.COLOR.play, RoundedCornerShape((Tokens.LAYOUT.pillHeight / 2.0).dp))
            .padding(horizontal = Tokens.LAYOUT.pillPadding.dp)
            .semantics {
                role = Role.Button
                contentDescription = Tokens.COPY.play
            }
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.realTouchDown()
                    pressed = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp() || !change.pressed) break
                            change.consume()
                        }
                    } finally {
                        pressed = false
                        engine.realTouchUp()
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.pillContentGap.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.18f)
                lineTo(size.width * 0.82f, size.height * 0.50f)
                lineTo(size.width * 0.30f, size.height * 0.82f)
                close()
            }
            drawPath(path, Tokens.COLOR.playInk)
        }
        Text(
            text = Tokens.COPY.play,
            color = Tokens.COLOR.playInk,
            fontSize = Tokens.TYPE.pillSize.sp,
            fontFamily = typography.semibold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TiltPosterParallaxPlusIcon(progress: Float) {
    Canvas(
        modifier = Modifier
            .size(Tokens.LAYOUT.pillIcon.dp)
            .graphicsLayer {
                alpha = 1f - progress
                rotationZ = progress * Tokens.MORPH.iconTurn.toFloat()
                scaleX = 1f - progress * (1f - Tokens.MORPH.iconSmall.toFloat())
                scaleY = scaleX
            },
    ) {
        val stroke = 1.8.dp.toPx()
        val inset = size.width * 0.22f
        drawLine(Tokens.COLOR.ink, Offset(size.width / 2f, inset), Offset(size.width / 2f, size.height - inset), stroke, StrokeCap.Round)
        drawLine(Tokens.COLOR.ink, Offset(inset, size.height / 2f), Offset(size.width - inset, size.height / 2f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun TiltPosterParallaxCheckIcon(progress: Float) {
    Canvas(
        modifier = Modifier
            .size(Tokens.LAYOUT.pillIcon.dp)
            .graphicsLayer {
                alpha = progress
                rotationZ = (progress - 1f) * Tokens.MORPH.iconTurn.toFloat()
                scaleX = Tokens.MORPH.iconSmall.toFloat() + progress * (1f - Tokens.MORPH.iconSmall.toFloat())
                scaleY = scaleX
            },
    ) {
        val stroke = 1.8.dp.toPx()
        drawLine(Tokens.COLOR.goldInk, Offset(size.width * 0.2f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.72f), stroke, StrokeCap.Round)
        drawLine(Tokens.COLOR.goldInk, Offset(size.width * 0.42f, size.height * 0.72f), Offset(size.width * 0.82f, size.height * 0.28f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun TiltPosterParallaxChevronIcon(up: Boolean) {
    Canvas(modifier = Modifier.size(Tokens.LAYOUT.chevron.dp)) {
        val top = if (up) size.height * 0.62f else size.height * 0.38f
        val bottom = if (up) size.height * 0.38f else size.height * 0.62f
        val stroke = 1.4.dp.toPx()
        drawLine(Tokens.COLOR.ink, Offset(size.width * 0.22f, top), Offset(size.width * 0.5f, bottom), stroke, StrokeCap.Round)
        drawLine(Tokens.COLOR.ink, Offset(size.width * 0.5f, bottom), Offset(size.width * 0.78f, top), stroke, StrokeCap.Round)
    }
}

private fun performTiltPosterParallaxWatchHaptic(view: android.view.View, haptic: TiltPosterParallaxHaptic) {
    val constant = if (haptic == TiltPosterParallaxHaptic.Save) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
    view.performHapticFeedback(constant)
}
