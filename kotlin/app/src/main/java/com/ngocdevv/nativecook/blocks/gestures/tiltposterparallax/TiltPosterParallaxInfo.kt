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
import androidx.compose.ui.text.font.FontFamily
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
    cardHeight: Dp,
    top: Dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .offset(x = Tokens.LAYOUT.gutter.dp, y = top)
            .width(stageWidth - (Tokens.LAYOUT.gutter * 2.0).dp)
            .graphicsLayer {
                translationY = (engine.lift - 1f) * cardHeight.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Tokens.COPY.title,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.ink,
            fontSize = Tokens.TYPE.titleSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            lineHeight = Tokens.TYPE.titleLineHeight.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(Tokens.LAYOUT.infoGap.dp))
        Text(
            text = Tokens.COPY.date,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.inkFaint,
            fontSize = Tokens.TYPE.dateSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            lineHeight = Tokens.TYPE.dateLineHeight.sp,
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
            WatchlistPill(engine = engine, modifier = Modifier.weight(1f))
            WatchedPill(engine = engine, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height((Tokens.LAYOUT.pillsMarginBottom + Tokens.LAYOUT.infoGap).dp))
        Text(
            text = Tokens.COPY.body,
            modifier = Modifier.fillMaxWidth(),
            color = Tokens.COLOR.ink,
            fontSize = Tokens.TYPE.bodySize.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            lineHeight = Tokens.TYPE.bodyLineHeight.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Clip,
        )
        Spacer(modifier = Modifier.height(Tokens.LAYOUT.infoGap.dp))
        Row(
            modifier = Modifier
                .align(Alignment.Start)
                .clickable {
                    expanded = !expanded
                }
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) Tokens.COPY.less else Tokens.COPY.more
                },
            horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.moreGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) Tokens.COPY.less else Tokens.COPY.more,
                color = Tokens.COLOR.inkFaint,
                fontSize = Tokens.TYPE.moreSize.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
            )
            ChevronIcon(up = expanded)
        }
    }
}

@Composable
private fun WatchlistPill(engine: TiltPosterParallaxEngine, modifier: Modifier) {
    val view = LocalView.current
    val progress = engine.savedMotion.coerceIn(0f, 1f)
    val background = lerp(Tokens.COLOR.pill, Tokens.COLOR.pillSaved, progress)
    val border = lerp(Tokens.COLOR.glassBorder, Tokens.COLOR.pillSavedBorder, progress)
    val pop = 1f + sin(progress * PI).toFloat() * Tokens.MORPH.pop.toFloat()
    val label = if (engine.saved) Tokens.COPY.added else Tokens.COPY.watchlist
    val description = if (engine.saved) "In your watchlist" else "Add to watchlist"
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
                contentDescription = description
                selected = engine.saved
                onClick {
                    performWatchHaptic(view, engine.toggleSaved())
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
                        if (released) {
                            performWatchHaptic(view, engine.toggleSaved())
                        }
                        engine.realTouchUp()
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.pillContentGap.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(Tokens.LAYOUT.pillIcon.dp), contentAlignment = Alignment.Center) {
            PlusIcon(progress = progress)
            CheckIcon(progress = progress)
        }
        Text(
            text = label,
            color = if (engine.saved) Tokens.COLOR.goldInk else Tokens.COLOR.ink,
            fontSize = Tokens.TYPE.pillSize.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
        )
    }
}

@Composable
private fun WatchedPill(engine: TiltPosterParallaxEngine, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(Tokens.LAYOUT.pillHeight.dp)
            .alpha(if (pressed) 0.7f else 1f)
            .background(Tokens.COLOR.pillGhost, RoundedCornerShape((Tokens.LAYOUT.pillHeight / 2.0).dp))
            .padding(horizontal = Tokens.LAYOUT.pillPadding.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Mark as watched"
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
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                break
                            }
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
        EyeIcon()
        Text(
            text = Tokens.COPY.watched,
            color = Tokens.COLOR.inkGhost,
            fontSize = Tokens.TYPE.pillSize.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlusIcon(progress: Float) {
    Canvas(
        modifier = Modifier
            .size(Tokens.LAYOUT.pillIcon.dp)
            .graphicsLayer {
                alpha = 1f - progress
                rotationZ = progress * Tokens.MORPH.iconTurn.toFloat()
                val scale = 1f - progress * (1f - Tokens.MORPH.iconSmall.toFloat())
                scaleX = scale
                scaleY = scale
            },
    ) {
        val stroke = 1.8.dp.toPx()
        val inset = size.width * 0.22f
        drawLine(
            color = Tokens.COLOR.ink,
            start = Offset(size.width / 2f, inset),
            end = Offset(size.width / 2f, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Tokens.COLOR.ink,
            start = Offset(inset, size.height / 2f),
            end = Offset(size.width - inset, size.height / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CheckIcon(progress: Float) {
    Canvas(
        modifier = Modifier
            .size(Tokens.LAYOUT.pillIcon.dp)
            .graphicsLayer {
                alpha = progress
                rotationZ = (progress - 1f) * Tokens.MORPH.iconTurn.toFloat()
                val scale = Tokens.MORPH.iconSmall.toFloat() + progress * (1f - Tokens.MORPH.iconSmall.toFloat())
                scaleX = scale
                scaleY = scale
            },
    ) {
        drawLine(
            color = Tokens.COLOR.goldInk,
            start = Offset(size.width * 0.2f, size.height * 0.52f),
            end = Offset(size.width * 0.42f, size.height * 0.72f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Tokens.COLOR.goldInk,
            start = Offset(size.width * 0.42f, size.height * 0.72f),
            end = Offset(size.width * 0.82f, size.height * 0.28f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun EyeIcon() {
    Canvas(modifier = Modifier.size(Tokens.LAYOUT.pillIcon.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.12f, size.width * 0.92f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.88f, size.width * 0.08f, size.height * 0.5f)
            close()
        }
        drawPath(
            path = path,
            color = Tokens.COLOR.inkGhost,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3.dp.toPx()),
        )
        drawCircle(color = Tokens.COLOR.inkGhost, radius = size.width * 0.13f, center = center)
    }
}

@Composable
private fun ChevronIcon(up: Boolean) {
    Canvas(modifier = Modifier.size(Tokens.LAYOUT.chevron.dp)) {
        val top = if (up) size.height * 0.62f else size.height * 0.38f
        val bottom = if (up) size.height * 0.38f else size.height * 0.62f
        drawLine(
            color = Tokens.COLOR.inkFaint,
            start = Offset(size.width * 0.22f, top),
            end = Offset(size.width * 0.5f, bottom),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Tokens.COLOR.inkFaint,
            start = Offset(size.width * 0.5f, bottom),
            end = Offset(size.width * 0.78f, top),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun performWatchHaptic(view: android.view.View, haptic: TiltPosterParallaxHaptic) {
    val constant = if (haptic == TiltPosterParallaxHaptic.Save) {
        HapticFeedbackConstants.LONG_PRESS
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(constant)
}
