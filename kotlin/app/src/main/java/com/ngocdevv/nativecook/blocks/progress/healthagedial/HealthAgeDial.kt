package com.ngocdevv.nativecook.blocks.progress.healthagedial

import android.app.Activity
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private const val SF_ASCENT = 0.953f
private const val SF_SPAN = 0.712f
private const val REDUCED_FADE_START = 4.0
private const val REDUCED_FADE_END = 30.0

/** Full-screen port of the accepted React Native Health Age screen. */
@Composable
fun HealthAgeDial(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playback = remember { HealthAgeDialPlayback() }
    val frameState = remember { mutableStateOf(HealthAgeDialRenderFrame()) }
    val runtime = remember(playback, frameState) { HealthAgeDialRuntime(playback, frameState) }
    var configuredReduced by remember { mutableStateOf(reducedMotion) }
    var configuredAutoPlay by remember { mutableStateOf(autoPlay) }

    LaunchedEffect(playback) {
        playback.start(autoPlay = autoPlay && !reducedMotion, reduced = reducedMotion, now = nowSeconds())
        runtime.publish()
    }
    LaunchedEffect(autoPlay, reducedMotion) {
        if (configuredReduced != reducedMotion || configuredAutoPlay != autoPlay) {
            configuredReduced = reducedMotion
            configuredAutoPlay = autoPlay
            playback.setConfiguration(
                autoPlay = autoPlay && !reducedMotion,
                reduced = reducedMotion,
                now = nowSeconds(),
            )
            runtime.publish()
        }
    }
    LaunchedEffect(lifecycleOwner, runtime) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            playback.setActive(active = true, now = nowSeconds())
            runtime.runClock()
        }
    }
    DisposableEffect(lifecycleOwner, playback) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playback.setActive(active = false, now = nowSeconds())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) {
                controller.isAppearanceLightStatusBars = oldStatus
            }
            if (oldNavigation != null) {
                controller.isAppearanceLightNavigationBars = oldNavigation
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HealthAgeDialColors.page),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val topInset = androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(density) / density.density
        val y0 = topInset - HealthAgeDialLayout.pinSafeTop
        val contentHeight = y0 + HealthAgeDialLayout.contentBottom + 112.0
        playback.setMaximumScroll(max(0.0, contentHeight - height))

        val chipWidths = healthAgeDialChipWidths(width)
        val dialCenterX = width / 2.0 + HealthAgeDialLayout.Dial.cx - HealthAgeDialLayout.pinWidth / 2.0
        val dialCenterY = y0 + HealthAgeDialLayout.Dial.cy
        val chipLeft = HealthAgeDialSpace.chipInset + chipWidths.first + HealthAgeDialSpace.chipGap
        val chipRect = RectF(
            chipLeft.toFloat(),
            (y0 + HealthAgeDialLayout.Chip.top).toFloat(),
            (chipLeft + chipWidths.second).toFloat(),
            (y0 + HealthAgeDialLayout.Chip.top + HealthAgeDialLayout.Chip.height).toFloat(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Scroll down") {
                            val actionNow = nowSeconds()
                            playback.beginTouch(actionNow)
                            playback.updateScroll(frameState.value.scroll + height * 0.72, actionNow, height)
                            playback.endScroll(0.0, actionNow, height)
                            runtime.publish()
                            true
                        },
                        CustomAccessibilityAction("Scroll up") {
                            val actionNow = nowSeconds()
                            playback.beginTouch(actionNow)
                            playback.updateScroll(frameState.value.scroll - height * 0.72, actionNow, height)
                            playback.endScroll(0.0, actionNow, height)
                            runtime.publish()
                            true
                        },
                    )
                }
                .healthAgeDialGestures(
                    playback = playback,
                    runtime = runtime,
                    density = density,
                    dialCenter = Offset(dialCenterX.toFloat(), dialCenterY.toFloat()),
                    chipRect = chipRect,
                    screenHeight = height.toFloat(),
                    reducedMotion = reducedMotion,
                    view = view,
                ),
        ) {
            HealthAgeDialScrollableContent(
                frameState = frameState,
                width = width,
                contentHeight = contentHeight,
                y0 = y0,
                density = density,
            )
            HealthAgeDialTopScrim(width = width, y0 = y0)
            HealthAgeDialChips(
                frameState = frameState,
                playback = playback,
                runtime = runtime,
                y0 = y0,
                widths = chipWidths,
                density = density,
            )
            HealthAgeDialHero(
                frameState = frameState,
                width = width,
                y0 = y0,
                reducedMotion = reducedMotion,
                density = density,
            )
            HealthAgeDialTabBar(width = width, height = height, density = density)
        }
    }
}

@Composable
private fun BoxScope.HealthAgeDialScrollableContent(
    frameState: MutableState<HealthAgeDialRenderFrame>,
    width: Double,
    contentHeight: Double,
    y0: Double,
    density: Density,
) {
    val fontScale = density.fontScale
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(contentHeight.dp)
            .graphicsLayer {
                translationY = -frameState.value.scroll.toFloat() * density.density
            },
    ) {
        val halo = HealthAgeDialLayout.halo
        Canvas(
            modifier = Modifier
                .offset(
                    x = (width / 2f + halo.cx - HealthAgeDialLayout.pinWidth / 2f - halo.radius).dp,
                    y = (y0 + halo.cy - halo.radius).dp,
                )
                .size((halo.radius * 2f).dp)
                .graphicsLayer {
                    alpha = (1.0 - HealthAgeDialMotion.seg(
                        frameState.value.scroll,
                        0.0,
                        HealthAgeDialPlayback.landingOffset * 0.7,
                    )).toFloat()
                },
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(red = 185, green = 219, blue = 250, alpha = 199),
                        0.52f to Color(red = 196, green = 224, blue = 250, alpha = 140),
                        1f to Color(red = 214, green = 230, blue = 248, alpha = 0),
                    ),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
            )
        }

        HealthAgeDialInfoGlyph(
            size = HealthAgeDialLayout.info.diameter.toDouble(),
            modifier = Modifier
                .offset(
                    x = (width - (HealthAgeDialLayout.pinWidth - HealthAgeDialLayout.info.cx) - HealthAgeDialLayout.info.diameter / 2f).dp,
                    y = (y0 + HealthAgeDialLayout.info.cy - HealthAgeDialLayout.info.diameter / 2f).dp,
                )
                .semantics { contentDescription = "About Health Age" },
        )

        val textWidth = width - HealthAgeDialSpace.textInset * 2.0
        HealthAgeDialText(
            text = HealthAgeDialCopy.title,
            style = HealthAgeDialType.title,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialBaseline.title, HealthAgeDialType.title),
            width = textWidth + 8.0,
            height = 82.0,
            lines = 2,
            fontScale = fontScale,
            header = true,
        )
        HealthAgeDialText(
            text = HealthAgeDialCopy.body,
            style = HealthAgeDialType.body,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialBaseline.body, HealthAgeDialType.body),
            width = textWidth + 8.0,
            height = 116.0,
            lines = 4,
            fontScale = fontScale,
        )
        HealthAgeDialText(
            text = HealthAgeDialCopy.healthAgeLabel,
            style = HealthAgeDialType.statLabel,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialBaseline.statLabel, HealthAgeDialType.statLabel),
            width = 150.0,
            height = 24.0,
            fontScale = fontScale,
        )
        HealthAgeDialDynamicHealthValue(
            frameState = frameState,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialBaseline.statValue, HealthAgeDialType.statValue),
            fontScale = fontScale,
        )
        val secondColumn = width / 2.0 + HealthAgeDialSpace.statColumn - HealthAgeDialLayout.pinWidth / 2.0
        HealthAgeDialText(
            text = HealthAgeDialCopy.actualAgeLabel,
            style = HealthAgeDialType.statLabel,
            left = secondColumn,
            top = y0 + topFor(HealthAgeDialBaseline.statLabel, HealthAgeDialType.statLabel),
            width = 150.0,
            height = 24.0,
            fontScale = fontScale,
        )
        HealthAgeDialText(
            text = HealthAgeDialCopy.actualAgeValue,
            style = HealthAgeDialType.statValue,
            left = secondColumn,
            top = y0 + topFor(HealthAgeDialBaseline.statValue, HealthAgeDialType.statValue),
            width = 150.0,
            height = 28.0,
            fontScale = fontScale,
        )

        HealthAgeDialText(
            text = HealthAgeDialCopy.history,
            style = HealthAgeDialType.heading,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialLayout.historyHeading, HealthAgeDialType.heading),
            width = 260.0,
            height = 30.0,
            fontScale = fontScale,
            header = true,
        )
        HealthAgeDialHistoryChart(
            frame = { frameState.value },
            modifier = Modifier
                .offset(HealthAgeDialLayout.chart.left.dp, (y0 + HealthAgeDialLayout.chart.top).dp)
                .semantics {
                    contentDescription = "Health age history, November to March: younger than your actual age, trending younger"
                },
        )
        months.forEachIndexed { index, month ->
            HealthAgeDialText(
                text = month,
                style = HealthAgeDialType.axis,
                left = HealthAgeDialLayout.chart.left +
                    (HealthAgeDialLayout.chart.right - HealthAgeDialLayout.chart.left) * index / HealthAgeDialLayout.chart.columns +
                    HealthAgeDialLayout.chart.labelOffset,
                top = y0 + topFor(HealthAgeDialLayout.chart.labelBaseline, HealthAgeDialType.axis),
                width = 40.0,
                height = 22.0,
                fontScale = fontScale,
            )
        }
        HealthAgeDialText(
            text = HealthAgeDialCopy.older,
            style = HealthAgeDialType.guide,
            left = HealthAgeDialLayout.chart.guideX,
            top = y0 + topFor(HealthAgeDialLayout.chart.olderBaseline, HealthAgeDialType.guide),
            width = 55.0,
            height = 20.0,
            fontScale = fontScale,
            layer = { alpha = (frameState.value.guides * frameState.value.revealOpacity).toFloat() },
        )
        HealthAgeDialText(
            text = HealthAgeDialCopy.younger,
            style = HealthAgeDialType.guide,
            left = HealthAgeDialLayout.chart.guideX,
            top = y0 + topFor(HealthAgeDialLayout.chart.youngerBaseline, HealthAgeDialType.guide),
            width = 64.0,
            height = 20.0,
            fontScale = fontScale,
            layer = { alpha = (frameState.value.guides * frameState.value.revealOpacity).toFloat() },
        )
        HealthAgeDialText(
            text = HealthAgeDialCopy.raising,
            style = HealthAgeDialType.heading,
            left = HealthAgeDialSpace.textInset,
            top = y0 + topFor(HealthAgeDialLayout.raisingHeading, HealthAgeDialType.heading),
            width = 300.0,
            height = 30.0,
            fontScale = fontScale,
            header = true,
        )
        HealthAgeDialFactorCard(0, frameState, width - 2.0 * HealthAgeDialSpace.cardInset, y0, fontScale)
        HealthAgeDialFactorCard(1, frameState, width - 2.0 * HealthAgeDialSpace.cardInset, y0, fontScale)
    }
}

@Composable
private fun BoxScope.HealthAgeDialDynamicHealthValue(
    frameState: MutableState<HealthAgeDialRenderFrame>,
    left: Double,
    top: Double,
    fontScale: Float,
) {
    val value = kotlin.math.round((AGES.actual + frameState.value.years) * 10.0) / 10.0
    HealthAgeDialText(
        text = "%.1f years".format(java.util.Locale.US, value),
        style = HealthAgeDialType.statValue,
        left = left,
        top = top,
        width = 150.0,
        height = 28.0,
        fontScale = fontScale,
        accessibility = "${HealthAgeDialCopy.healthAgeLabel} ${HealthAgeDialCopy.healthAgeValue}",
    )
}

@Composable
private fun BoxScope.HealthAgeDialFactorCard(
    index: Int,
    frameState: MutableState<HealthAgeDialRenderFrame>,
    width: Double,
    y0: Double,
    fontScale: Float,
) {
    val factor = HealthAgeDialCopy.factors[index]
    val card = HealthAgeDialLayout.cards[index]
    val titleColor = if (index == 0) HealthAgeDialColors.sleep else HealthAgeDialColors.hrv
    val tint = if (index == 0) HealthAgeDialColors.sleepTint else HealthAgeDialColors.hrvTint
    val glyphColor = if (index == 0) HealthAgeDialColors.sleepGlyph else HealthAgeDialColors.hrvGlyph
    Box(
        modifier = Modifier
            .offset(HealthAgeDialSpace.cardInset.dp, (y0 + card.top).dp)
            .size(width.dp, card.height.dp)
            .shadow(4.dp, RoundedCornerShape(HealthAgeDialRadius.card.dp), ambientColor = Color(0x0A4D4A88), spotColor = Color(0x0A4D4A88))
            .background(HealthAgeDialColors.card, RoundedCornerShape(HealthAgeDialRadius.card.dp))
            .clearAndSetSemantics {
                contentDescription = "${factor.title}, plus ${factor.years} years. ${factor.body}"
            },
    ) {
        Box(
            modifier = Modifier
                .offset(
                    HealthAgeDialSpace.cardIconLeading.dp,
                    (HealthAgeDialLayout.cardIcon.cy - HealthAgeDialLayout.cardIcon.diameter / 2f).dp,
                )
                .size(HealthAgeDialLayout.cardIcon.diameter.dp)
                .background(tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val elapsed = if (index == 0) frameState.value.badge1 else frameState.value.badge2
                    val badge = HealthAgeDialMotion.pop(elapsed)
                    alpha = badge.opacity.toFloat()
                    scaleX = badge.scale.toFloat()
                    scaleY = badge.scale.toFloat()
                },
            ) {
                if (index == 0) {
                    BedGlyph(size = HealthAgeDialLayout.cardIcon.glyph.toDouble(), color = glyphColor)
                } else {
                    PulseGlyph(size = HealthAgeDialLayout.cardIcon.glyph.toDouble(), color = glyphColor)
                }
            }
        }
        HealthAgeDialText(
            text = factor.title,
            style = HealthAgeDialType.cardTitle.copy(color = titleColor),
            left = HealthAgeDialSpace.cardTextLeading,
            top = topFor(HealthAgeDialLayout.cardTitleBaseline, HealthAgeDialType.cardTitle),
            width = width - HealthAgeDialSpace.cardTextLeading - 12.0,
            height = 22.0,
            fontScale = fontScale,
        )
        HealthAgeDialFactorValue(
            index = index,
            frameState = frameState,
            years = factor.years,
            width = width,
            fontScale = fontScale,
        )
        HealthAgeDialText(
            text = factor.body,
            style = HealthAgeDialType.cardBody,
            left = HealthAgeDialSpace.cardTextLeading,
            top = topFor(HealthAgeDialLayout.cardBodyBaseline, HealthAgeDialType.cardBody),
            width = width - HealthAgeDialSpace.cardTextLeading - 12.0,
            height = 80.0,
            lines = 4,
            fontScale = fontScale,
        )
    }
}

@Composable
private fun BoxScope.HealthAgeDialFactorValue(
    index: Int,
    frameState: MutableState<HealthAgeDialRenderFrame>,
    years: Double,
    width: Double,
    fontScale: Float,
) {
    val count = if (index == 0) frameState.value.count1 else frameState.value.count2
    HealthAgeDialText(
        text = "+%.1f years".format(java.util.Locale.US, years * count),
        style = HealthAgeDialType.cardValue,
        left = HealthAgeDialSpace.cardTextLeading,
        top = topFor(HealthAgeDialLayout.cardValueBaseline, HealthAgeDialType.cardValue),
        width = width - HealthAgeDialSpace.cardTextLeading - 12.0,
        height = 38.0,
        fontScale = fontScale,
    )
}

@Composable
private fun BoxScope.HealthAgeDialTopScrim(width: Double, y0: Double) {
    val height = y0 + HealthAgeDialLayout.Chip.top + HealthAgeDialLayout.Chip.height + 10.0
    Canvas(modifier = Modifier.size(width.dp, max(0.0, height).dp)) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to HealthAgeDialColors.page,
                    0.32f to HealthAgeDialColors.page.copy(alpha = 0.92f),
                    0.62f to HealthAgeDialColors.page.copy(alpha = 0.62f),
                    1f to HealthAgeDialColors.page.copy(alpha = 0f),
                ),
            ),
        )
    }
}

@Composable
private fun BoxScope.HealthAgeDialChips(
    frameState: MutableState<HealthAgeDialRenderFrame>,
    playback: HealthAgeDialPlayback,
    runtime: HealthAgeDialRuntime,
    y0: Double,
    widths: Pair<Double, Double>,
    density: Density,
) {
    HealthAgeDialChip(
        left = HealthAgeDialSpace.chipInset,
        top = y0 + HealthAgeDialLayout.Chip.top,
        width = widths.first,
        selected = false,
        title = HealthAgeDialCopy.overviewTitle,
        value = HealthAgeDialCopy.overviewValue,
        density = density,
        icon = {
            HealthAgeDialPetalMark(
                size = HealthAgeDialLayout.Chip.icon.toDouble(),
                fills = OVERVIEW_FILLS,
            )
        },
    )
    val left = HealthAgeDialSpace.chipInset + widths.first + HealthAgeDialSpace.chipGap
    HealthAgeDialChip(
        left = left,
        top = y0 + HealthAgeDialLayout.Chip.top,
        width = widths.second,
        selected = true,
        title = HealthAgeDialCopy.chipTitle,
        value = HealthAgeDialCopy.chipValue,
        density = density,
        layer = {
            val scale = 1.0 - (1.0 - HealthAgeDialMotion.Press.scale) * frameState.value.chipPress
            scaleX = scale.toFloat()
            scaleY = scale.toFloat()
        },
        action = {
            playback.glideToDial(nowSeconds())
            runtime.publish()
        },
        icon = {
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1.0 + HealthAgeDialMotion.Land.amplitude * frameState.value.bump
                    scaleX = scale.toFloat()
                    scaleY = scale.toFloat()
                },
            ) {
                HealthAgeDialMiniDial(size = HealthAgeDialLayout.Chip.icon + 2.0)
            }
        },
    )
}

@Composable
private fun BoxScope.HealthAgeDialChip(
    left: Double,
    top: Double,
    width: Double,
    selected: Boolean,
    title: String,
    value: String,
    density: Density,
    layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {},
    action: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(HealthAgeDialRadius.chip.dp)
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = "$title, $value"
        this.selected = selected
        if (action != null) {
            role = Role.Button
            onClick(label = "Scrolls back to the dial") {
                action()
                true
            }
        }
    }
    Box(
        modifier = Modifier
            .offset(left.dp, top.dp)
            .size(width.dp, HealthAgeDialLayout.Chip.height.dp)
            .graphicsLayer(block = layer)
            .then(
                if (selected) {
                    Modifier.shadow(3.dp, shape, ambientColor = Color(0x0F4A4880), spotColor = Color(0x0F4A4880))
                } else {
                    Modifier
                },
            )
            .background(
                if (selected) HealthAgeDialColors.chipSelected else HealthAgeDialColors.chipFill,
                shape,
            )
            .then(
                if (selected) Modifier else Modifier.border(1.dp, HealthAgeDialColors.chipBorder, shape)
            )
            .then(semantics),
    ) {
        Box(
            modifier = Modifier
            .offset(x = (HealthAgeDialSpace.chipPadLeading - 1.0).dp, y = 0.dp)
                .size((HealthAgeDialLayout.Chip.icon + 2.0).dp, HealthAgeDialLayout.Chip.height.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        val textLeft = HealthAgeDialSpace.chipPadLeading + HealthAgeDialLayout.Chip.icon + HealthAgeDialSpace.chipIconGap
        HealthAgeDialText(
            text = title,
            style = HealthAgeDialType.chipTitle,
            left = textLeft,
            top = topFor(HealthAgeDialBaseline.chipTitle, HealthAgeDialType.chipTitle) - HealthAgeDialLayout.Chip.top,
            width = width - textLeft - 4.0,
            height = 22.0,
            fontScale = density.fontScale,
        )
        HealthAgeDialText(
            text = value,
            style = HealthAgeDialType.chipValue,
            left = textLeft,
            top = topFor(HealthAgeDialBaseline.chipValue, HealthAgeDialType.chipValue) - HealthAgeDialLayout.Chip.top,
            width = width - textLeft - 4.0,
            height = 22.0,
            fontScale = density.fontScale,
        )
    }
}

@Composable
private fun BoxScope.HealthAgeDialHero(
    frameState: MutableState<HealthAgeDialRenderFrame>,
    width: Double,
    y0: Double,
    reducedMotion: Boolean,
    density: Density,
) {
    val dial = HealthAgeDialLayout.Dial
    val chip = HealthAgeDialLayout.Chip
    val center = width / 2.0
    val dialX = center + dial.cx - HealthAgeDialLayout.pinWidth / 2.0
    val dialY = y0 + dial.cy
    val widths = healthAgeDialChipWidths(width)
    val chipIconX = HealthAgeDialSpace.chipInset + widths.first + HealthAgeDialSpace.chipGap +
        HealthAgeDialSpace.chipPadLeading + chip.icon / 2.0
    val chipY = y0 + chip.top + chip.height / 2.0
    val ringRatio = chip.ring / (2.0 * HEALTH_AGE_DIAL_OUTER)
    val dialLeft = dialX - HEALTH_AGE_DIAL_SIZE / 2.0
    val dialTop = dialY - HEALTH_AGE_DIAL_SIZE / 2.0

    HealthAgeDialDialLayer(
        frame = { frameState.value },
        numberOpacity = {
            val progress = if (reducedMotion) 0.0 else HealthAgeDialMotion.clamp01(
                frameState.value.scroll / HealthAgeDialPlayback.landingOffset,
            )
            (1.0 - HealthAgeDialMotion.seg(progress, 0.0, HealthAgeDialMotion.Flight.numberFadeEnd)).toFloat()
        },
        modifier = Modifier
            .offset(dialLeft.dp, dialTop.dp)
            .size(HEALTH_AGE_DIAL_SIZE.dp)
            .graphicsLayer {
                val frame = frameState.value
                if (reducedMotion) {
                    translationY = -frame.scroll.toFloat() * density.density
                    alpha = (1.0 - HealthAgeDialMotion.seg(frame.scroll, REDUCED_FADE_START, REDUCED_FADE_END)).toFloat()
                    scaleX = 1f
                    scaleY = 1f
                } else {
                    val progress = HealthAgeDialMotion.clamp01(frame.scroll / HealthAgeDialPlayback.landingOffset)
                    val flight = HealthAgeDialMotion.smoothstep(progress)
                    val x = HealthAgeDialMotion.lerp(dialX.toDouble(), chipIconX.toDouble(), flight)
                    val y = HealthAgeDialMotion.lerp((dialY - frame.scroll).toDouble(), chipY.toDouble(), flight)
                    translationX = (x - dialX).toFloat() * density.density
                    translationY = (y - dialY).toFloat() * density.density
                    val scale = HealthAgeDialMotion.lerp(1.0, ringRatio.toDouble(), flight).toFloat()
                    scaleX = scale
                    scaleY = scale
                    alpha = (1.0 - HealthAgeDialMotion.seg(progress, HealthAgeDialMotion.Flight.crossfadeStart, 1.0)).toFloat()
                }
            }
            .clearAndSetSemantics {
                role = Role.Image
                contentDescription = "Health age 26.1 years, 3.9 years younger than your actual age of 30"
            },
    )
}

@Composable
private fun BoxScope.HealthAgeDialTabBar(
    width: Double,
    height: Double,
    density: Density,
) {
    val tab = HealthAgeDialLayout.Tab
    val inset = (tab.width - 3.0 * tab.item) / 2.0
    val left = (width - tab.width) / 2.0
    val top = height - HealthAgeDialSpace.tabBottom - tab.height
    Box(
        modifier = Modifier
            .offset(left.dp, top.dp)
            .size(tab.width.dp, tab.height.dp)
            .shadow(5.dp, RoundedCornerShape(HealthAgeDialRadius.tab.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .background(Color(0xFFF9F9FB).copy(alpha = 0.94f), RoundedCornerShape(HealthAgeDialRadius.tab.dp))
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(HealthAgeDialRadius.tab.dp))
            .semantics { contentDescription = "Tab bar" },
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = (inset + tab.item + (tab.item - tab.pillWidth) / 2.0).dp,
                    y = ((tab.height - tab.pillHeight) / 2.0).dp,
                )
                .size(tab.pillWidth.dp, tab.pillHeight.dp)
                .background(HealthAgeDialColors.tabSelected, RoundedCornerShape(HealthAgeDialRadius.tabPill.dp)),
        )
        HealthAgeDialTabItem(0, inset, density)
        HealthAgeDialTabItem(1, inset, density)
        HealthAgeDialTabItem(2, inset, density)
    }
}

@Composable
private fun BoxScope.HealthAgeDialTabItem(
    index: Int,
    inset: Double,
    density: Density,
) {
    val selected = index == 1
    val tab = HealthAgeDialLayout.Tab
    Box(
        modifier = Modifier
            .offset(x = (inset + index * tab.item).dp, y = 0.dp)
            .size(tab.item.dp, tab.height.dp)
            .semantics {
                contentDescription = HealthAgeDialCopy.tabs[index]
                this.selected = selected
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        val color = if (selected) HealthAgeDialColors.blue else HealthAgeDialColors.tabText
        when (index) {
            0 -> HealthAgeDialHeartGlyph(tab.heart, HealthAgeDialColors.tabText, Modifier.offset(y = (-6).dp))
            1 -> HealthAgeDialPetalMark(
                size = tab.petal,
                fills = List(8) { HealthAgeDialColors.tabBlue },
                tabShape = true,
                modifier = Modifier.offset(y = (-6).dp),
            )
            else -> HealthAgeDialGridGlyph(tab.grid, HealthAgeDialColors.tabText, Modifier.offset(y = (-6).dp))
        }
        Text(
            text = HealthAgeDialCopy.tabs[index],
            color = color,
            fontSize = (HealthAgeDialType.tab.size / density.fontScale).sp,
            fontWeight = HealthAgeDialType.tab.weight,
            maxLines = 1,
            modifier = Modifier.offset(y = 14.dp),
        )
    }
}

@Composable
private fun BoxScope.HealthAgeDialText(
    text: String,
    style: HealthAgeDialTextStyle,
    left: Double,
    top: Double,
    width: Double,
    height: Double,
    fontScale: Float,
    lines: Int = 1,
    accessibility: String? = null,
    header: Boolean = false,
    layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {},
) {
    Text(
        text = text,
        modifier = Modifier
            .offset(left.dp, top.dp)
            .size(width.dp, height.dp)
            .graphicsLayer(block = layer)
            .semantics {
                if (accessibility != null) {
                    contentDescription = accessibility
                }
                if (header) {
                    heading()
                }
            },
        style = TextStyle(
            color = style.color,
            fontSize = (style.size / fontScale).sp,
            fontWeight = style.weight,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (style.tracking / fontScale).sp,
            lineHeight = ((style.line ?: style.size * 1.2) / fontScale).sp,
        ),
        maxLines = lines,
        overflow = TextOverflow.Clip,
    )
}

private fun topFor(baseline: Double, style: HealthAgeDialTextStyle): Double {
    return if (style.line != null) {
        baseline - (style.line + SF_SPAN * style.size) / 2.0
    } else {
        baseline - SF_ASCENT * style.size
    }
}

private fun healthAgeDialChipWidths(width: Double): Pair<Double, Double> {
    val available = width - HealthAgeDialSpace.chipInset - HealthAgeDialSpace.chipInsetTrailing - HealthAgeDialSpace.chipGap
    val left = available * HealthAgeDialLayout.Chip.leftShare
    return left to available - left
}

private fun Modifier.healthAgeDialGestures(
    playback: HealthAgeDialPlayback,
    runtime: HealthAgeDialRuntime,
    density: Density,
    dialCenter: Offset,
    chipRect: RectF,
    screenHeight: Float,
    reducedMotion: Boolean,
    view: android.view.View,
): Modifier {
    return pointerInput(playback, density, dialCenter, chipRect, screenHeight, reducedMotion) {
        var landingHaptic: Runnable? = null
        awaitEachGesture {
            landingHaptic?.let(view::removeCallbacks)
            landingHaptic = null
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.position / density.density
            val now = nowSeconds()
            val current = playback.frame(now)
            val distance = hypot(start.x - dialCenter.x, start.y - dialCenter.y)
            val onDial = current.scroll < 4.0 &&
                abs(distance - HealthAgeDialLayout.Dial.radius) < HealthAgeDialLayout.Dial.stroke / 2f + 14f
            val onChip = chipRect.contains(start.x, start.y) && current.scroll > 1.0
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val origin = start
            val originScroll = current.scroll
            val target = when {
                onChip -> HealthAgeDialGestureTarget.CHIP
                onDial -> HealthAgeDialGestureTarget.DIAL
                else -> HealthAgeDialGestureTarget.SCROLL
            }
            var chipPressed = onChip
            when (target) {
                HealthAgeDialGestureTarget.CHIP -> playback.chipPress(true, now)
                HealthAgeDialGestureTarget.DIAL -> {
                    playback.beginDial(now)
                    val angle = atan2(start.x - dialCenter.x, -(start.y - dialCenter.y)) * 180f / PI.toFloat()
                    if (playback.updateDial(angle.toDouble(), now)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                HealthAgeDialGestureTarget.SCROLL -> playback.beginScroll(now)
            }
            runtime.publish()

            var released = false
            while (!released) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    val up = change?.position ?: event.changes.firstOrNull()?.position ?: down.position
                    tracker.addPosition(event.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis, up)
                    val velocity = tracker.calculateVelocity()
                    val endNow = nowSeconds()
                    when (target) {
                        HealthAgeDialGestureTarget.DIAL -> {
                            val yearsVelocity = HealthAgeDialPlayback.tangentialYearsVelocity(
                                (velocity.x / density.density).toDouble(),
                                (velocity.y / density.density).toDouble(),
                                playback.frame(endNow).years,
                            )
                            playback.endDial(yearsVelocity, endNow)
                        }
                        HealthAgeDialGestureTarget.SCROLL -> {
                            val delaySeconds = playback.endScroll(
                                velocity = (-velocity.y / density.density).toDouble(),
                                now = endNow,
                                screenHeight = screenHeight.toDouble(),
                            )
                            if (delaySeconds != null && !reducedMotion) {
                                val haptic = Runnable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                }
                                landingHaptic = haptic
                                view.postDelayed(haptic, (delaySeconds * 1_000.0).toLong())
                            }
                        }
                        HealthAgeDialGestureTarget.CHIP -> {
                            playback.chipPress(false, endNow)
                            if (chipPressed) {
                                playback.glideToDial(endNow)
                            }
                        }
                    }
                    runtime.publish()
                    released = true
                } else {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val point = change.position / density.density
                    val moveNow = nowSeconds()
                    when (target) {
                        HealthAgeDialGestureTarget.DIAL -> {
                            change.consume()
                            val angle = atan2(point.x - dialCenter.x, -(point.y - dialCenter.y)) * 180f / PI.toFloat()
                            if (playback.updateDial(angle.toDouble(), moveNow)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        HealthAgeDialGestureTarget.SCROLL -> {
                            val next = originScroll - (point.y - origin.y)
                            if (abs(point.y - origin.y) > 2f) {
                                change.consume()
                            }
                            if (playback.updateScroll(next, moveNow, screenHeight.toDouble()) && !reducedMotion) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                        HealthAgeDialGestureTarget.CHIP -> {
                            val inside = RectF(chipRect).apply { inset(-12f, -12f) }.contains(point.x, point.y)
                            if (inside != chipPressed) {
                                chipPressed = inside
                                playback.chipPress(inside, moveNow)
                            }
                        }
                    }
                    runtime.publish()
                }
            }
        }
    }
}

private enum class HealthAgeDialGestureTarget {
    SCROLL,
    DIAL,
    CHIP,
}

private class HealthAgeDialRuntime(
    private val playback: HealthAgeDialPlayback,
    private val frameState: MutableState<HealthAgeDialRenderFrame>,
) {
    private val events = Channel<Unit>(Channel.CONFLATED)

    suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val now = nowSeconds()
            val end = playback.tickEnd(now)
            if (end == null || end > now) {
                withFrameNanos { time ->
                    frameState.value = playback.frame(time / 1_000_000_000.0)
                }
            } else {
                events.receive()
            }
        }
    }

    fun publish() {
        frameState.value = playback.frame(nowSeconds())
        events.trySend(Unit)
    }
}

private fun nowSeconds(): Double {
    return System.nanoTime() / 1_000_000_000.0
}
