@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.optical
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.space
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.type
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.platform.EmberHaptics
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.platform.rememberEmberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive

internal val EmberRegular = FontFamily(Font(R.font.emberchargewidget_manrope_regular))
internal val EmberSemibold = FontFamily(Font(R.font.emberchargewidget_manrope_semibold))

@Composable
fun EmberCharge(
    autoPlay: Boolean = false,
    goal: Int = E.goal,
    quality: EmberQuality = EmberQuality.Auto,
    onIgnite: () -> Unit = {},
) {
    require(goal > E.restSteps + 1) { "The daily goal must exceed the resting count." }
    val context = LocalContext.current
    val callback by rememberUpdatedState(onIgnite)
    val haptics = remember(context) { EmberHaptics(context) }
    // One remembered owner serves touch, text endpoints and all three draw passes.
    val motion = remember(goal) {
        EmberMotion(
            goal = goal,
            autoPlay = autoPlay,
            ignite = {
                haptics.success()
                callback()
            },
            tick = { haptics.selection() },
        )
    }
    val reduced = rememberEmberReducedMotion()
    SideEffect {
        motion.autoPlay = autoPlay
        motion.requestedQuality = quality
        motion.updateReduced(reduced)
    }
    LaunchedEffect(motion) {
        var previous = 0L
        while (isActive) withFrameNanos { now ->
            motion.frame(
                if (previous == 0L) M.frame.missing else (now - previous) / 1_000_000_000f, firstFrame = previous == 0L,
            )
            previous = now
        }
    }
    val hint = emberString(R.string.emberchargewidget_hint)
    val action = emberString(R.string.emberchargewidget_action)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val scale = min(1f, (maxWidth.value - 2 * E.pageMargin) / E.size).coerceAtLeast(0.1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val landed = if (motion.reached) goal else E.restSteps
            val label = emberString(
                R.string.emberchargewidget_label, String.format(Locale.US, "%,d", landed),
                if (motion.reached) E.goalBpm else E.restBpm,
            )
            val density = LocalDensity.current.density
            Box(
                // Input stays on the untransformed wrapper; convert back to card dp.
                Modifier.size((E.size * scale).dp).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = label
                    onClick(action) {
                        motion.activate()
                        true
                    }
                }.pointerInput(motion, scale, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        motion.begin(down.position.x / density / scale, down.position.y / density / scale)
                        try {
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                pressed = change.pressed
                                if (pressed) motion.follow(
                                    change.position.x / density / scale, change.position.y / density / scale,
                                )
                                change.consume()
                            }
                        } finally {
                            val velocity = tracker.calculateVelocity()
                            motion.end(velocity.x / density / scale, velocity.y / density / scale)
                        }
                    }
                },
            ) {
                Box(
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(E.size.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    EmberCard(motion)
                }
            }
            BasicText(
                hint,
                Modifier.padding(top = (space.corner * scale).dp).padding(horizontal = space.near.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(
                    fontFamily = EmberRegular, fontSize = type.hint.sp, lineHeight = E.line.hint.sp,
                    color = Color(emberTone.sub), textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun EmberCard(motion: EmberMotion) {
    val density = LocalDensity.current
    val renderer = remember(density.density) { EmberRenderer(density.density) }
    Box(
        Modifier.size(E.size.dp).graphicsLayer {
            motion.revision
            alpha = motion.enter
            // Dimensionless pop/compression envelopes and tremble frequencies match ember-charge.tsx.
            val ignition = motion.ignition
            val age = ignition * M.ignite
            val compress =
                if (motion.reduced) 0f else 0.012f * motion.charge * (1 - EmberMotion.seg(ignition, 0f, 0.055f))
            val pop = if (motion.reduced || ignition <= 0) 0f else 0.1f * exp(-9 * age) * sin(12 * age)
            val shake =
                if (motion.reduced) 0f else motion.charge * motion.charge * (1 - EmberMotion.seg(ignition, 0f, 0.02f))
            scaleX = if (motion.reduced) 1f else (1 - compress + pop) * (1 - 0.04f * motion.pressure)
            scaleY = scaleX
            translationX =
                sin(motion.time * 28 * 2 * PI).toFloat() * EmberTheme.drawing.trembleX * shake * density.density
            translationY =
                cos(motion.time * 31 * 2 * PI).toFloat() * EmberTheme.drawing.trembleY * shake * density.density
        }.clearAndSetSemantics {},
    ) {
        EmberCanvas(motion, renderer, 0)
        EmberCanvas(motion, renderer, 1)
        EmberLabel(
            emberString(R.string.emberchargewidget_title), E.title.x, E.title.top, type.title, E.line.title,
            type.titleTracking, true,
        )
        EmberLabel(
            emberString(R.string.emberchargewidget_subtitle), E.title.x, E.title.sub, type.subtitle, E.line.subtitle,
            type.subtitleTracking, false, Color(emberTone.sub),
        )
        Box(
            Modifier.offset(0.dp, E.time.top.dp).width((E.size - E.time.right).dp), contentAlignment = Alignment.TopEnd,
        ) {
            BasicText(
                "4:24 AM",
                style = emberTextStyle(type.small, E.line.time, color = Color(emberTone.sub)).copy(
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        val units = listOf("km", emberString(R.string.emberchargewidget_minutes), "kcal")
        listOf("5.21", "413", "1343").forEachIndexed { i, value ->
            EmberLabel(
                value, E.metrics.x[i] + E.metrics.text, E.metrics.valueTop, type.metric, E.line.value,
                type.metricTracking, true,
            )
            EmberLabel(
                units[i], E.metrics.x[i] + E.metrics.text, E.metrics.unitTop, type.small, E.line.unit, 0f, false,
                Color(emberTone.sub),
            )
        }
        EmberCount(motion)
        EmberCanvas(motion, renderer, 2)
    }
}

@Composable
private fun EmberCanvas(motion: EmberMotion, renderer: EmberRenderer, pass: Int) {
    Canvas(
        Modifier.offset((-E.bleed).dp, (-E.bleed).dp).wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(E.canvasSize.dp),
    ) {
        motion.revision
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(density, density)
        canvas.translate(E.bleed, E.bleed)
        // Clip only the canvas bounds, retaining the full halo/effect bleed.
        canvas.clipRect(-E.bleed, -E.bleed, E.size + E.bleed, E.size + E.bleed)
        when (pass) {
            0 -> renderer.glass(canvas, motion)
            1 -> renderer.surface(canvas, motion)
            else -> renderer.effects(canvas, motion)
        }
        canvas.restore()
    }
}

@Composable
internal fun emberTextStyle(
    size: Float, line: Float? = null, spacing: Float = 0f, bold: Boolean = false, color: Color = Color(emberTone.ink),
): TextStyle {
    // Convert dp to sp explicitly so fontScale cannot overflow the instrument's fixed boxes.
    val density = LocalDensity.current
    return TextStyle(
        fontFamily = if (bold) EmberSemibold else EmberRegular, fontSize = with(density) { size.dp.toSp() },
        lineHeight = if (line == null) TextUnit.Unspecified else with(density) { line.dp.toSp() },
        letterSpacing = with(density) { spacing.dp.toSp() },
        color = color, platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

@Composable
private fun EmberLabel(
    text: String, x: Float, y: Float, size: Float, line: Float, spacing: Float, bold: Boolean,
    color: Color = Color(emberTone.ink),
) {
    // Match the bundled font's iOS glyph baseline inside the unchanged line box.
    val baselineCorrection = when (size) {
        type.title -> optical.titleBaseline
        type.metric -> optical.metricBaseline
        type.subtitle -> optical.subtitleBaseline
        else -> 0f
    }
    BasicText(
        text, Modifier.offset(x.dp, y.dp).graphicsLayer { translationY = baselineCorrection * density },
        style = emberTextStyle(size, line, spacing, bold, color), maxLines = 1, softWrap = false,
    )
}
