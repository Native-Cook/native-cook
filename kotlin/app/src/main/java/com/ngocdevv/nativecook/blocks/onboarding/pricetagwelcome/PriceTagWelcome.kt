package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

const val LOOP = 8.0

@Composable
fun PriceTagWelcome(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = rememberPriceTagWelcomeReducedMotion()
    val assets = rememberPriceTagWelcomeAssets(context)
    val frostRenderer = remember(assets) { PriceTagWelcomeFrostRenderer(assets) }
    val controller = remember {
        PriceTagWelcomeController(nowSeconds(), autoPlay, reducedMotion)
    }

    DisposableEffect(view) {
        val activity = view.context as? Activity
        val bars = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
        bars?.isAppearanceLightStatusBars = true
        bars?.isAppearanceLightNavigationBars = true
        onDispose {
            bars?.isAppearanceLightStatusBars = false
            bars?.isAppearanceLightNavigationBars = false
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.restartAutoPlay(nowSeconds())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, autoPlay, reducedMotion) {
        controller.setAutoPlay(autoPlay, reducedMotion, nowSeconds())
    }

    LaunchedEffect(controller) {
        while (isActive) {
            if (!controller.shouldTick) {
                controller.wake.receive()
            }
            // Frame state is consumed only by draw and layer lambdas.
            withFrameNanos { frameNanos ->
                controller.tick(frameNanos / 1_000_000_000.0)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PriceTagWelcomeTheme.Colors.page)
            .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val layoutScale = maxWidth.value / PriceTagWelcomeTheme.REF_PT.toFloat()
        val unitPx = with(density) { (layoutScale / 3f).dp.toPx() }
        val featureScale = 3f * unitPx
        fun refDp(value: Double): Dp = (value.toFloat() * layoutScale / 3f).dp

        val lightHaptic: () -> Unit = remember(view) {
            {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                Unit
            }
        }
        val freezeHaptic: (Boolean) -> Unit = remember(view) {
            { frozen ->
                val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (frozen) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.GESTURE_END
                    }
                } else {
                    if (frozen) {
                        HapticFeedbackConstants.LONG_PRESS
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                }
                view.performHapticFeedback(feedback)
                Unit
            }
        }
        val headlinePaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.headline, unitPx)
        }
        val subPaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.sub, unitPx)
        }
        val pricePaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.price, unitPx)
        }
        val waitPaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.wait, unitPx)
        }
        val timerPaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.timer, unitPx).apply {
                fontFeatureSettings = "tnum"
            }
        }
        val buttonPaint = remember(unitPx) {
            textPaint(PriceTagWelcomeTheme.Type.button, unitPx)
        }

        PriceTagWelcomeBackdrop(unitPx)
        PriceTagWelcomeCardIcon(
            assets = assets,
            controller = controller,
            reducedMotion = reducedMotion,
            unitPx = unitPx,
            refDp = ::refDp,
        )
        PriceTagWelcomeCopy(
            unitPx = unitPx,
            refDp = ::refDp,
            headlinePaint = headlinePaint,
            subPaint = subPaint,
        )
        PriceTagWelcomeConveyor(
            assets = assets,
            frostRenderer = frostRenderer,
            controller = controller,
            reducedMotion = reducedMotion,
            unitPx = unitPx,
            featureScale = featureScale,
            refDp = ::refDp,
            pricePaint = pricePaint,
            waitPaint = waitPaint,
            timerPaint = timerPaint,
            freezeHaptic = freezeHaptic,
        )
        PriceTagWelcomeButton(
            top = maxHeight - refDp(PriceTagWelcomeTheme.Layout.Button.fromBottom),
            layoutScale = layoutScale,
            unitPx = unitPx,
            refDp = ::refDp,
            paint = buttonPaint,
            onClick = lightHaptic,
        )
    }
}

@Composable
private fun PriceTagWelcomeBackdrop(unitPx: Float) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        drawRect(rgb(PriceTagWelcomeTheme.GLOW_BASE))
        val stops = doubleArrayOf(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0)
        for (blob in PriceTagWelcomeTheme.glow) {
            val radius = (3.0 * blob.sigma * unitPx).toFloat()
            val colorStops = stops.map { stop ->
                (stop / 3.0).toFloat() to
                    rgb(blob.peak, kotlin.math.exp(-stop * stop / 2.0))
            }.toTypedArray()
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = colorStops,
                    center = Offset(
                        (blob.cx * unitPx).toFloat(),
                        (blob.cy * unitPx).toFloat(),
                    ),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(
                    (blob.cx * unitPx).toFloat(),
                    (blob.cy * unitPx).toFloat(),
                ),
            )
        }
    }
}

@Composable
private fun PriceTagWelcomeCardIcon(
    assets: PriceTagWelcomeAssets,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    refDp: (Double) -> Dp,
) {
    val icon = PriceTagWelcomeTheme.Layout.Icon
    val box = PriceTagWelcomeTheme.Layout.Icon.Box
    val shape = RoundedCornerShape(refDp(PriceTagWelcomeTheme.Radius.icon))
    Box(
        Modifier
            .offset(refDp(box.x), refDp(box.y))
            .size(refDp(box.w), refDp(box.h))
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.07f),
                spotColor = Color.Black.copy(alpha = 0.07f),
            )
            .background(PriceTagWelcomeTheme.Colors.iconBacking, shape),
    )
    Box(
        Modifier
            .offset(refDp(box.x), refDp(box.y))
            .size(refDp(box.w), refDp(box.h))
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            ),
    )
    Image(
        bitmap = assets.cardIcon,
        contentDescription = null,
        filterQuality = FilterQuality.High,
        modifier = Modifier
            .offset(refDp(icon.x), refDp(icon.y))
            .size(refDp(icon.w), refDp(icon.h)),
    )
    Canvas(
        Modifier
            .offset(refDp(box.x), refDp(box.y))
            .size(refDp(box.w), refDp(box.h))
            .clip(shape),
    ) {
        val frame = controller.frame.value
        val alpha = if (reducedMotion) {
            0f
        } else {
            (PriceTagWelcomeMotion.Glint.alpha * frame.glintAlpha).toFloat()
        }
        if (alpha <= 0f) {
            return@Canvas
        }
        val bandWidth = (box.w * 0.5 * unitPx).toFloat()
        val travel = (box.w * 1.9 * unitPx).toFloat()
        val left = -bandWidth -
            (box.w * 0.45 * unitPx).toFloat() +
            (frame.glint * travel).toFloat()
        val top = (-box.h * 0.3 * unitPx).toFloat()
        val height = (box.h * 1.6 * unitPx).toFloat()
        rotate(20f, pivot = Offset(left + bandWidth / 2f, top + height / 2f)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = alpha),
                    1f to Color.Transparent,
                    startX = left,
                    endX = left + bandWidth,
                ),
                topLeft = Offset(left, top),
                size = Size(bandWidth, height),
            )
        }
    }
}

@Composable
private fun PriceTagWelcomeCopy(
    unitPx: Float,
    refDp: (Double) -> Dp,
    headlinePaint: AndroidPaint,
    subPaint: AndroidPaint,
) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val canvas = drawContext.canvas.nativeCanvas
        PriceTagWelcomeTheme.Copy.headline.forEachIndexed { index, text ->
            drawCenteredText(
                canvas,
                text,
                (PriceTagWelcomeTheme.Layout.headlineBaselines[index] * unitPx).toFloat(),
                headlinePaint,
                size.width,
            )
        }
        PriceTagWelcomeTheme.Copy.sub.forEachIndexed { index, text ->
            drawCenteredText(
                canvas,
                text,
                (PriceTagWelcomeTheme.Layout.subBaselines[index] * unitPx).toFloat(),
                subPaint,
                size.width,
            )
        }
    }
    PriceTagWelcomeTheme.Copy.headline.forEachIndexed { index, text ->
        Box(
            Modifier
                .offset(
                    y = refDp(
                        PriceTagWelcomeTheme.Layout.headlineBaselines[index] - 130.0,
                    ),
                )
                .fillMaxWidth()
                .height(refDp(140.0))
                .semantics {
                    contentDescription = text
                    if (index == 0) {
                        heading()
                    }
                },
        )
    }
    PriceTagWelcomeTheme.Copy.sub.forEachIndexed { index, text ->
        Box(
            Modifier
                .offset(
                    y = refDp(
                        PriceTagWelcomeTheme.Layout.subBaselines[index] - 80.0,
                    ),
                )
                .fillMaxWidth()
                .height(refDp(90.0))
                .semantics { contentDescription = text },
        )
    }
}

@Composable
private fun PriceTagWelcomeConveyor(
    assets: PriceTagWelcomeAssets,
    frostRenderer: PriceTagWelcomeFrostRenderer,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    featureScale: Float,
    refDp: (Double) -> Dp,
    pricePaint: AndroidPaint,
    waitPaint: AndroidPaint,
    timerPaint: AndroidPaint,
    freezeHaptic: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .offset(y = refDp(PriceTagWelcomeTheme.Row.top))
            .fillMaxWidth()
            .height(refDp(PriceTagWelcomeTheme.Row.bottom - PriceTagWelcomeTheme.Row.top))
            .pointerInput(controller, unitPx) {
                priceTagWelcomeGesture(controller, unitPx, freezeHaptic)
            },
    ) {
        for (index in PriceTagWelcomeTheme.items.indices) {
            PriceTagWelcomeSticker(
                index = index,
                assets = assets,
                frostRenderer = frostRenderer,
                controller = controller,
                reducedMotion = reducedMotion,
                unitPx = unitPx,
                featureScale = featureScale,
                refDp = refDp,
                timerPaint = timerPaint,
                freezeHaptic = freezeHaptic,
            )
        }
        for (index in PriceTagWelcomeTheme.items.indices) {
            PriceTagWelcomeTag(
                index = index,
                frostRenderer = frostRenderer,
                controller = controller,
                reducedMotion = reducedMotion,
                unitPx = unitPx,
                featureScale = featureScale,
                refDp = refDp,
                pricePaint = pricePaint,
                waitPaint = waitPaint,
            )
        }
    }
}

@Composable
private fun PriceTagWelcomeSticker(
    index: Int,
    assets: PriceTagWelcomeAssets,
    frostRenderer: PriceTagWelcomeFrostRenderer,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    featureScale: Float,
    refDp: (Double) -> Dp,
    timerPaint: AndroidPaint,
    freezeHaptic: (Boolean) -> Unit,
) {
    val item = PriceTagWelcomeTheme.items[index]
    val frozen = controller.frozenMask.value and (1 shl index) != 0
    val description = buildString {
        append(item.name)
        append(", ")
        append(item.price)
        if (frozen) {
            append(", ")
            append(PriceTagWelcomeTheme.Copy.a11yFrozen)
        }
    }
    Box(
        Modifier
            .offset(refDp(item.x), refDp(item.y - PriceTagWelcomeTheme.Row.top))
            .size(refDp(item.w), refDp(item.h))
            .graphicsLayer {
                val frame = controller.frame.value
                translationX = (
                    (
                        PriceTagWelcomeMotion.wrapX(item.x, frame.x) -
                            item.x
                        ) *
                        unitPx
                    ).toFloat()
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = PriceTagWelcomeTheme.Copy.a11yItemHint) {
                    freezeHaptic(controller.tapItem(nowSeconds(), index))
                    true
                }
            },
    ) {
        Image(
            bitmap = requireNotNull(assets.shadows[item.key]),
            contentDescription = null,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val press = controller.frame.value.press[index].toFloat()
                    val scale = (
                        1.0 -
                            (1.0 - PriceTagWelcomeMotion.Press.scale) *
                            press
                        ).toFloat()
                    translationY = (4.0 - 2.0 * press).toFloat() * unitPx
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.14f
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val press = controller.frame.value.press[index].toFloat()
                    val scale = (
                        1.0 -
                            (1.0 - PriceTagWelcomeMotion.Press.scale) *
                            press
                        ).toFloat()
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Image(
                bitmap = requireNotNull(assets.art[item.key]),
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
            if (controller.onScreenMask.value and (1 shl index) != 0) {
                PriceTagWelcomeStickerFrost(
                    index = index,
                    frostRenderer = frostRenderer,
                    controller = controller,
                    reducedMotion = reducedMotion,
                    unitPx = unitPx,
                    featureScale = featureScale,
                    refDp = refDp,
                )
            }
            PriceTagWelcomeCountdown(
                index = index,
                controller = controller,
                reducedMotion = reducedMotion,
                unitPx = unitPx,
                featureScale = featureScale,
                paint = timerPaint,
            )
        }
    }
}

@Composable
private fun PriceTagWelcomeStickerFrost(
    index: Int,
    frostRenderer: PriceTagWelcomeFrostRenderer,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    featureScale: Float,
    refDp: (Double) -> Dp,
) {
    val item = PriceTagWelcomeTheme.items[index]
    val margin = PriceTagWelcomeTheme.Space.frostMargin
    Canvas(
        Modifier
            .offset(refDp(-margin), refDp(-margin))
            .size(
                refDp(item.w + 2.0 * margin),
                refDp(item.h + 2.0 * margin),
            )
            .clearAndSetSemantics { },
    ) {
        val frame = controller.frame.value
        val look = PriceTagWelcomeMotion.frostLook(
            frame.freezeTime[index],
            frame.meltTime[index],
            reducedMotion,
        )
        val marginPx = (margin * unitPx).toFloat()
        val width = (item.w * unitPx).toFloat()
        val height = (item.h * unitPx).toFloat()
        val originX = ((item.tag.cx - item.x) * unitPx).toFloat() + marginPx
        val originY = ((item.tag.cy - item.y) * unitPx).toFloat() + marginPx
        val span = (
            hypot(
                max(originX, size.width - originX).toDouble(),
                max(originY, size.height - originY).toDouble(),
            ) +
                12.0 * featureScale
            ).toFloat()
        frostRenderer.drawSticker(
            canvas = drawContext.canvas.nativeCanvas,
            index = index,
            look = look,
            clock = frame.clock,
            originX = originX,
            originY = originY,
            span = span,
            featureScale = featureScale,
            boxWidth = width,
            boxHeight = height,
            margin = marginPx,
        )
    }
}

@Composable
private fun PriceTagWelcomeCountdown(
    index: Int,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    featureScale: Float,
    paint: AndroidPaint,
) {
    val item = PriceTagWelcomeTheme.items[index]
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val frame = controller.frame.value
        val look = PriceTagWelcomeMotion.frostLook(
            frame.freezeTime[index],
            frame.meltTime[index],
            reducedMotion,
        )
        val reveal = if (reducedMotion) 1.0 else look.timer
        val alpha = if (reducedMotion) look.timer else 1.0 - look.out
        if (reveal <= 0.0 || alpha <= 0.0) {
            return@Canvas
        }
        val position = if (item.key == "laptop") {
            PriceTagWelcomeTheme.Layout.Timer.laptop
        } else {
            PriceTagWelcomeTheme.Layout.Timer.other
        }
        val lineHeight = round(paint.textSize * 1.18f)
        val digitWidth = paint.measureText("0")
        val colonWidth = paint.measureText(":")
        val tracking = (
            PriceTagWelcomeTheme.Type.timer.letterSpacing *
                featureScale
            ).toFloat()
        val groupWidth = digitWidth * 6f + colonWidth * 2f + tracking * 7f
        val groupLeft = (item.w * unitPx).toFloat() * position.fx.toFloat() -
            groupWidth / 2f
        val groupTop = (item.h * unitPx).toFloat() * position.fy.toFloat() -
            lineHeight / 2f +
            (look.out * PriceTagWelcomeTheme.Space.etchDrop * unitPx).toFloat()
        val baseline = groupTop + (lineHeight - paint.ascent() - paint.descent()) / 2f
        val native = drawContext.canvas.nativeCanvas
        val checkpoint = native.save()
        native.clipRect(
            groupLeft,
            groupTop,
            groupLeft + groupWidth * reveal.toFloat(),
            groupTop + lineHeight,
        )
        val countdown = PriceTagWelcomeMotion.countdownAt(
            max(frame.freezeTime[index], 0.0),
            reducedMotion,
        )
        var x = groupLeft
        for (column in 0 until 6) {
            if (column == 2 || column == 4) {
                drawTimerGlyph(
                    native,
                    ":",
                    x,
                    baseline,
                    paint,
                    alpha,
                    featureScale,
                )
                x += colonWidth + tracking
            }
            val from = PriceTagWelcomeMotion.digitOf(countdown.previous, column)
            val to = PriceTagWelcomeMotion.digitOf(countdown.left, column)
            val line = PriceTagWelcomeMotion.stripLine(from, to, countdown.roll)
            val digitCheckpoint = native.save()
            native.clipRect(x, groupTop, x + digitWidth, groupTop + lineHeight)
            for (number in 0..10) {
                drawTimerGlyph(
                    native,
                    (number % 10).toString(),
                    x,
                    baseline + number * lineHeight - (line * lineHeight).toFloat(),
                    paint,
                    alpha,
                    featureScale,
                )
            }
            native.restoreToCount(digitCheckpoint)
            x += digitWidth
            if (column < 5) {
                x += tracking
            }
        }
        native.restoreToCount(checkpoint)
    }
}

private fun drawTimerGlyph(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    baseline: Float,
    paint: AndroidPaint,
    alpha: Double,
    featureScale: Float,
) {
    val oldColor = paint.color
    val oldAlpha = paint.alpha
    paint.color = PriceTagWelcomeTheme.Colors.scrapeEdge.toArgb()
    paint.alpha = (255.0 * alpha).toInt().coerceIn(0, 255)
    canvas.drawText(text, x, baseline + featureScale, paint)
    paint.color = PriceTagWelcomeTheme.Colors.scrape.toArgb()
    canvas.drawText(text, x, baseline, paint)
    paint.color = oldColor
    paint.alpha = oldAlpha
}

@Composable
private fun PriceTagWelcomeTag(
    index: Int,
    frostRenderer: PriceTagWelcomeFrostRenderer,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    unitPx: Float,
    featureScale: Float,
    refDp: (Double) -> Dp,
    pricePaint: AndroidPaint,
    waitPaint: AndroidPaint,
) {
    val item = PriceTagWelcomeTheme.items[index]
    val tag = item.tag
    val shape = RoundedCornerShape(refDp(tag.h / 2.0))
    Box(
        Modifier
            .offset(
                refDp(tag.cx - tag.w / 2.0),
                refDp(tag.cy - PriceTagWelcomeTheme.Row.top - tag.h / 2.0),
            )
            .size(refDp(tag.w), refDp(tag.h))
            .graphicsLayer {
                val frame = controller.frame.value
                translationX = (
                    (
                        PriceTagWelcomeMotion.wrapX(item.x, frame.x) -
                            item.x
                        ) *
                        unitPx
                    ).toFloat()
                rotationZ = tag.tilt.toFloat()
                transformOrigin = TransformOrigin.Center
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = controller.frame.value.thetaDegrees[index].toFloat()
                    transformOrigin = TransformOrigin(0.3f, 0f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset(y = 4.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.07f),
                        spotColor = Color.Black.copy(alpha = 0.07f),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .offset(y = 1.5.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.10f),
                    ),
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(PriceTagWelcomeTheme.Colors.tag)
                    .clearAndSetSemantics { },
            ) {
                val frame = controller.frame.value
                val look = PriceTagWelcomeMotion.frostLook(
                    frame.freezeTime[index],
                    frame.meltTime[index],
                    reducedMotion,
                )
                val native = drawContext.canvas.nativeCanvas
                val baseline = size.height / 2f -
                    (pricePaint.ascent() + pricePaint.descent()) / 2f
                val oldPriceAlpha = pricePaint.alpha
                pricePaint.alpha = (255.0 * (1.0 - look.lost)).toInt().coerceIn(0, 255)
                drawCenteredText(native, item.price, baseline, pricePaint, size.width)
                pricePaint.alpha = oldPriceAlpha
            }
            // The frozen price: the same figures with a real blur, under the frost (above the crisp price).
            if (controller.onScreenMask.value and (1 shl index) != 0) {
                PriceTagWelcomePriceBlur(
                    index = index,
                    controller = controller,
                    reducedMotion = reducedMotion,
                    featureScale = featureScale,
                    paint = pricePaint,
                    text = item.price,
                )
            }
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val frame = controller.frame.value
                val look = PriceTagWelcomeMotion.frostLook(
                    frame.freezeTime[index],
                    frame.meltTime[index],
                    reducedMotion,
                )
                val native = drawContext.canvas.nativeCanvas
                val span = priceTagWelcomeFrostSpan(item, unitPx, featureScale)
                if (controller.onScreenMask.value and (1 shl index) != 0) frostRenderer.drawTag(
                    canvas = native,
                    index = index,
                    look = look,
                    clock = frame.clock,
                    span = span,
                    featureScale = featureScale,
                    width = size.width,
                    height = size.height,
                )
                drawWaitLabel(
                    canvas = native,
                    look = look,
                    reducedMotion = reducedMotion,
                    width = size.width,
                    height = size.height,
                    tagWidthRef = tag.w,
                    unitPx = unitPx,
                    featureScale = featureScale,
                    paint = waitPaint,
                )
            }
        }
    }
}

/**
 * The frozen price: the same figures with a real Gaussian blur (0 ... Etch.priceBlur, decal edges so
 * it stays inside the tag), fading to the ghost. Below API 31 there is no render effect, so the
 * ghost fade alone stays. Its values live in the layer lambda: nothing recomposes per frame.
 */
@Composable
private fun PriceTagWelcomePriceBlur(
    index: Int,
    controller: PriceTagWelcomeController,
    reducedMotion: Boolean,
    featureScale: Float,
    paint: AndroidPaint,
    text: String,
) {
    val copy = remember(paint) { AndroidPaint(paint).apply { alpha = 255 } }
    Canvas(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .graphicsLayer {
                val frame = controller.frame.value
                val look = PriceTagWelcomeMotion.frostLook(
                    frame.freezeTime[index],
                    frame.meltTime[index],
                    reducedMotion,
                )
                alpha = if (look.lost > 0.0) look.priceAlpha.toFloat() else 0f
                val radius = (look.lost * PriceTagWelcomeMotion.Etch.priceBlur * featureScale).toFloat()
                renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.01f) {
                    BlurEffect(radius, radius, TileMode.Decal)
                } else {
                    null
                }
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val baseline = size.height / 2f - (copy.ascent() + copy.descent()) / 2f
        drawCenteredText(drawContext.canvas.nativeCanvas, text, baseline, copy, size.width)
    }
}

private fun priceTagWelcomeFrostSpan(
    item: PriceTagWelcomeTheme.Item,
    unitPx: Float,
    featureScale: Float,
): Float {
    val margin = (PriceTagWelcomeTheme.Space.frostMargin * unitPx).toFloat()
    val width = (item.w * unitPx).toFloat() + 2f * margin
    val height = (item.h * unitPx).toFloat() + 2f * margin
    val originX = ((item.tag.cx - item.x) * unitPx).toFloat() + margin
    val originY = ((item.tag.cy - item.y) * unitPx).toFloat() + margin
    return (
        hypot(
            max(originX, width - originX).toDouble(),
            max(originY, height - originY).toDouble(),
        ) +
            12.0 * featureScale
        ).toFloat()
}

private fun drawWaitLabel(
    canvas: android.graphics.Canvas,
    look: PriceTagWelcomeMotion.FrostLook,
    reducedMotion: Boolean,
    width: Float,
    height: Float,
    tagWidthRef: Double,
    unitPx: Float,
    featureScale: Float,
    paint: AndroidPaint,
) {
    val reveal = if (reducedMotion) 1.0 else look.wait
    val alpha = if (reducedMotion) look.wait else 1.0 - look.out
    if (reveal <= 0.0 || alpha <= 0.0) {
        return
    }
    val oldSize = paint.textSize
    val fitted = min(
        PriceTagWelcomeTheme.Type.wait.size * featureScale,
        (
            tagWidthRef * unitPx -
                2.0 * PriceTagWelcomeTheme.Space.tagPad * unitPx
            ) /
            PriceTagWelcomeTheme.WAIT_EM,
    ).toFloat()
    paint.textSize = fitted
    val text = PriceTagWelcomeTheme.Copy.wait
    val textWidth = paint.measureText(text)
    val left = (width - textWidth) / 2f
    val drop = (look.out * PriceTagWelcomeTheme.Space.etchDrop * unitPx).toFloat()
    val baseline = height / 2f -
        (paint.ascent() + paint.descent()) / 2f +
        drop
    val checkpoint = canvas.save()
    canvas.clipRect(left, 0f, left + textWidth * reveal.toFloat(), height)
    val oldColor = paint.color
    val oldAlpha = paint.alpha
    paint.alpha = (255.0 * alpha).toInt().coerceIn(0, 255)
    paint.color = PriceTagWelcomeTheme.Colors.etchLight.toArgb()
    canvas.drawText(text, left, baseline + 0.8f * featureScale, paint)
    paint.color = PriceTagWelcomeTheme.Colors.etch.toArgb()
    canvas.drawText(text, left, baseline, paint)
    paint.color = oldColor
    paint.alpha = oldAlpha
    paint.textSize = oldSize
    canvas.restoreToCount(checkpoint)
}

@Composable
private fun PriceTagWelcomeButton(
    top: Dp,
    layoutScale: Float,
    unitPx: Float,
    refDp: (Double) -> Dp,
    paint: AndroidPaint,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val easing = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) {
                PriceTagWelcomeMotion.Press.scale.toFloat()
            } else {
                1f
            },
            animationSpec = tween(
                durationMillis = if (pressed) 100 else 200,
                easing = easing,
            ),
        )
    }
    val shape = RoundedCornerShape(
        refDp(PriceTagWelcomeTheme.Layout.Button.h / 2.0),
    )
    Box(
        Modifier
            .offset(refDp(PriceTagWelcomeTheme.Layout.Button.x), top)
            .size(
                refDp(PriceTagWelcomeTheme.Layout.Button.w),
                refDp(PriceTagWelcomeTheme.Layout.Button.h),
            )
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .shadow(
                elevation = (8f * layoutScale).dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(PriceTagWelcomeTheme.Colors.button, shape)
            .border(
                width = (1f * layoutScale).dp,
                color = PriceTagWelcomeTheme.Colors.buttonRim,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = PriceTagWelcomeTheme.Copy.button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = PriceTagWelcomeTheme.Copy.button
            },
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawCenteredText(
                drawContext.canvas.nativeCanvas,
                PriceTagWelcomeTheme.Copy.button,
                (PriceTagWelcomeTheme.Layout.Button.baseline * unitPx).toFloat(),
                paint,
                size.width,
            )
        }
    }
}

private suspend fun PointerInputScope.priceTagWelcomeGesture(
    controller: PriceTagWelcomeController,
    unitPx: Float,
    freezeHaptic: (Boolean) -> Unit,
) {
    val velocityTracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        velocityTracker.resetTracking()
        velocityTracker.addPointerInputChange(down)
        val downHit = controller.hitItem(
            (start.x / unitPx).toDouble(),
            (start.y / unitPx).toDouble() + PriceTagWelcomeTheme.Row.top,
        )
        controller.touchDown(nowSeconds(), downHit)
        var dragging = false
        var panFailed = false
        var completed = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPointerInputChange(change)
                val dx = change.position.x - start.x
                val dy = change.position.y - start.y
                if (!change.pressed) {
                    if (dragging) {
                        controller.dragEnd(
                            nowSeconds(),
                            (velocityTracker.calculateVelocity().x / unitPx).toDouble(),
                        )
                        dragging = false
                    } else if (
                        hypot(dx, dy) <=
                        PriceTagWelcomeMotion.TAP_SLOP * unitPx
                    ) {
                        val hit = controller.hitItem(
                            (change.position.x / unitPx).toDouble(),
                            (change.position.y / unitPx).toDouble() +
                                PriceTagWelcomeTheme.Row.top,
                        )
                        if (hit >= 0) {
                            freezeHaptic(controller.tapItem(nowSeconds(), hit))
                        } else {
                            controller.touchCancel(nowSeconds())
                        }
                    } else {
                        controller.touchCancel(nowSeconds())
                    }
                    completed = true
                    break
                }
                if (!dragging && !panFailed) {
                    val failY = PriceTagWelcomeMotion.PAN_FAIL_Y * unitPx
                    val startX = PriceTagWelcomeMotion.PAN_SLOP * unitPx
                    if (abs(dy) >= failY) {
                        panFailed = true
                    } else if (abs(dx) >= startX) {
                        dragging = true
                        controller.dragBegin(nowSeconds())
                    }
                }
                if (dragging) {
                    change.consume()
                    controller.dragMove(nowSeconds(), (dx / unitPx).toDouble())
                }
            }
        } finally {
            if (!completed && dragging) {
                controller.dragEnd(nowSeconds(), 0.0)
            } else if (!completed) {
                controller.touchCancel(nowSeconds())
            }
        }
    }
}

/**
 * Reduce Motion (animator scale 0) or a screen reader (TalkBack's touch exploration), both followed
 * live: the loop stops and the freeze cross-fades instead of growing.
 */
@Composable
private fun rememberPriceTagWelcomeReducedMotion(): Boolean {
    val context = LocalContext.current
    fun readScale(): Boolean {
        return Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var scaleOff by remember(context) { mutableStateOf(readScale()) }
    var screenReader by remember(manager) {
        mutableStateOf(manager?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(context, manager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scaleOff = readScale()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(
                Settings.Global.ANIMATOR_DURATION_SCALE,
            ),
            false,
            observer,
        )
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            screenReader = enabled
        }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            manager?.removeTouchExplorationStateChangeListener(listener)
        }
    }
    return scaleOff || screenReader
}

private fun textPaint(
    spec: PriceTagWelcomeTheme.TypeSpec,
    unitPx: Float,
): AndroidPaint {
    return AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = spec.color.toArgb()
        textSize = (spec.size * 3.0 * unitPx).toFloat()
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(
                Typeface.create("sans-serif", Typeface.NORMAL),
                spec.weight,
                false,
            )
        } else if (spec.weight >= 500) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
}

private fun drawCenteredText(
    canvas: android.graphics.Canvas,
    text: String,
    baseline: Float,
    paint: AndroidPaint,
    width: Float,
) {
    canvas.drawText(text, (width - paint.measureText(text)) / 2f, baseline, paint)
}

private fun rgb(
    color: Triple<Double, Double, Double>,
    alpha: Double = 1.0,
): Color {
    return Color(
        red = (color.first / 255.0).toFloat(),
        green = (color.second / 255.0).toFloat(),
        blue = (color.third / 255.0).toFloat(),
        alpha = alpha.toFloat(),
    )
}

private fun nowSeconds(): Double {
    return System.nanoTime() / 1_000_000_000.0
}
