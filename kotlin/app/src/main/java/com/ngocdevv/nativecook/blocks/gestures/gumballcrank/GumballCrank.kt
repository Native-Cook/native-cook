package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/** A full-screen Compose port of the shipped `GumballUpgrade` screen. */
@Composable
fun GumballCrank(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    var reducedMotion by remember { mutableStateOf(gumballCrankReducedMotion(context)) }
    val simulation = remember(autoPlay) { GumballCrankSimulation(reducedMotion, autoPlay) }
    val assets = remember(context) { GumballCrankAssets(context) }
    val renderer = remember(assets) { GumballCrankRenderer(assets) }
    var frameVersion by remember { mutableLongStateOf(0L) }
    var visiblePhase by remember { mutableStateOf(GumballCrankPhase.READY) }
    var coinTarget by remember { mutableIntStateOf(Tokens.Motion.COINS.from.toInt()) }
    var shownCoins by remember { mutableIntStateOf(Tokens.Motion.COINS.from.toInt()) }
    val latestWidth = remember { mutableStateOf(0.0) }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = gumballCrankReducedMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(simulation, reducedMotion) {
        simulation.updateReducedMotion(reducedMotion)
        frameVersion++
    }

    // The renderer reads simulation values in Canvas/graphicsLayer. Only phase and coin changes compose again.
    LaunchedEffect(simulation) {
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previousFrame) / 1_000_000_000.0).coerceAtMost(Tokens.PORT.maxFrameDelta)
            previousFrame = now
            val changed = if (simulation.shouldTick) simulation.step(elapsed * Tokens.Motion.QA_SPEED, latestWidth.value) else false
            simulation.drainHaptics().forEach { haptic -> view.performGumballCrankHaptic(haptic) }
            if (visiblePhase != simulation.phase) visiblePhase = simulation.phase
            val nextCoins = simulation.coins
            if (coinTarget != nextCoins) coinTarget = nextCoins
            if (changed) frameVersion++
        }
    }

    // RN :75-85 rolls the balance by five 70 ms steps, after a hidden instantaneous drop.
    LaunchedEffect(coinTarget, shownCoins, reducedMotion) {
        if (shownCoins == coinTarget) return@LaunchedEffect
        if (reducedMotion || coinTarget < shownCoins) {
            shownCoins = coinTarget
            return@LaunchedEffect
        }
        delay(Tokens.Motion.COINS.countStep.toLong())
        val increment = kotlin.math.ceil(Tokens.Motion.COINS.add / Tokens.Motion.COINS.countSteps).toInt()
        shownCoins = minOf(coinTarget, shownCoins + increment)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Tokens.REPLICA_ART.wallFill)) {
        val stageWidth = maxWidth.value.toDouble()
        SideEffect { latestWidth.value = stageWidth }
        val stageScale = stageWidth / Tokens.STAGE_WIDTH
        val knobCenterX = Tokens.REPLICA_ART.knob.x + Tokens.REPLICA_ART.knob.size / 2.0
        val knobCenterY = Tokens.REPLICA_ART.knob.y + Tokens.REPLICA_ART.knob.size / 2.0
        val knobRadius = Tokens.UI.knobTarget / 2.0
        val knobLeft = ((knobCenterX - knobRadius) * stageScale).dp
        val knobTop = ((knobCenterY - knobRadius) * stageScale).dp
        val knobSize = (Tokens.UI.knobTarget * stageScale).dp
        val couponLeft = ((Tokens.UP.card.x + Tokens.UP.card.w * Tokens.UP.couponText.x) * stageScale).dp
        val couponTop = ((Tokens.UP.card.y + Tokens.UP.card.h * Tokens.UP.couponText.y) * stageScale).dp
        val couponWidth = (Tokens.UP.card.w * Tokens.UP.couponText.w * stageScale).dp

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(simulation, stageWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x / density.density
                        val downY = down.position.y / density.density
                        if (!simulation.down(downX.toDouble(), downY.toDouble(), stageWidth)) return@awaitEachGesture
                        down.consume()
                        var moved = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed) {
                                    simulation.up()
                                    pointer.consume()
                                    break
                                }
                                simulation.move(
                                    (pointer.position.x / density.density).toDouble(),
                                    (pointer.position.y / density.density).toDouble(),
                                    stageWidth,
                                )
                                moved = moved || pointer.position != down.position
                                pointer.consume()
                            }
                        } finally {
                            simulation.up()
                            if (!moved) simulation.command(GumballCrankCommand.TURN)
                            simulation.drainHaptics().forEach { haptic -> view.performGumballCrankHaptic(haptic) }
                            frameVersion++
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frameVersion
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.scale(density.density, density.density)
                    renderer.draw(
                        canvas.nativeCanvas,
                        simulation,
                        size.width / density.density,
                        size.height / density.density,
                        safeTop,
                    )
                    canvas.nativeCanvas.restore()
                }
            }

            // Semantic target mirrors RN :288-294 without taking the circular drag away from the Canvas.
            Box(
                Modifier
                    .offset(x = knobLeft, y = knobTop)
                    .size(knobSize)
                    .semantics {
                        role = Role.Button
                        contentDescription = Tokens.COPY.knob
                        onClick(Tokens.COPY.knobHint) {
                            simulation.command(GumballCrankCommand.TURN)
                            frameVersion++
                            true
                        }
                    },
            )
        }

        GumballCrankCounter(
            accessibleCoins = coinTarget,
            shownCoins = shownCoins,
            safeTop = safeTop,
            frameVersion = { frameVersion },
            simulation = simulation,
            reducedMotion = reducedMotion,
        )

        // Android font ascenders differ from RN; removing platform padding keeps the coupon's 10/44/11 pt stack tight.
        androidx.compose.foundation.layout.Column(
            Modifier
                .offset(x = couponLeft, y = couponTop)
                .width(couponWidth)
                .graphicsLayer {
                    frameVersion
                    alpha = simulation.cardTextOpacity.toFloat().coerceIn(0f, 1f)
                },
        ) {
            Text(
                text = Tokens.COPY.prize.uppercase(),
                color = Tokens.UI.eyebrow,
                fontSize = Tokens.UI.eyebrowSize.sp,
                lineHeight = Tokens.UI.eyebrowLineHeight.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = Tokens.UI.eyebrowTracking.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            Text(
                text = Tokens.COPY.amount,
                color = Color.White,
                fontSize = Tokens.UI.amountSize.sp,
                lineHeight = Tokens.UI.amountLineHeight.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = Tokens.UI.amountTracking.sp,
                style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            Text(
                text = Tokens.COPY.cardMeta,
                color = Color.White.copy(alpha = Tokens.UI.metaAlpha.toFloat()),
                fontSize = Tokens.UI.metaSize.sp,
                lineHeight = Tokens.UI.metaLineHeight.sp,
                style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }

        GumballCrankCollectButton(
            top = ((Tokens.UP.card.y + Tokens.UP.card.h + Tokens.SPACE.lg) * stageScale).dp,
            frameVersion = { frameVersion },
            simulation = simulation,
            active = visiblePhase == GumballCrankPhase.REWARD,
        )

        Canvas(Modifier.fillMaxSize()) {
            frameVersion
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.scale(density.density, density.density)
                renderer.drawFlyingCoins(canvas.nativeCanvas, simulation, stageScale.toFloat(), safeTop)
                canvas.nativeCanvas.restore()
            }
        }
    }
}

@Composable
private fun GumballCrankCounter(
    accessibleCoins: Int,
    shownCoins: Int,
    safeTop: Float,
    frameVersion: () -> Long,
    simulation: GumballCrankSimulation,
    reducedMotion: Boolean,
) {
    val slideDistancePx = with(LocalDensity.current) { Tokens.UI.counterSlide.dp.roundToPx() }
    val counterEasing = Easing { f ->
        if (f < 0.5f) 2f * f * f else 1f - 2f * (1f - f) * (1f - f)
    }
    Row(
        modifier = Modifier
            .offset(x = Tokens.UP.counter.left.dp, y = (safeTop + Tokens.UP.counter.top).dp)
            .height(Tokens.UI.counterHeight.dp)
            .graphicsLayer {
                frameVersion()
                alpha = simulation.counterOpacity.toFloat().coerceIn(0f, 1f)
                val scale = if (reducedMotion) 1f else 1f + Tokens.UI.counterBump.toFloat() * simulation.bump.toFloat()
                scaleX = scale
                scaleY = scale
            }
            .background(
                Color.White.copy(alpha = Tokens.UI.counterBackgroundAlpha.toFloat()),
                RoundedCornerShape(Tokens.UI.counterRadius.dp),
            )
            .padding(horizontal = Tokens.SPACE.md.dp)
            .semantics { contentDescription = "$accessibleCoins ${Tokens.COPY.coinsWord}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.gumballcrank_coin),
            contentDescription = null,
            modifier = Modifier
                .padding(end = Tokens.SPACE.sm.dp)
                .size(Tokens.UI.counterCoin.dp),
        )
        Box(
            Modifier
                .height(Tokens.UI.counterDigitBoxHeight.dp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                targetState = shownCoins,
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(durationMillis = Tokens.UI.counterEnterMillis, easing = counterEasing)) +
                            slideInVertically(tween(durationMillis = Tokens.UI.counterEnterMillis, easing = counterEasing)) { slideDistancePx }) togetherWith
                            (fadeOut(tween(durationMillis = Tokens.UI.counterExitMillis, easing = counterEasing)) +
                                slideOutVertically(tween(durationMillis = Tokens.UI.counterExitMillis, easing = counterEasing)) { -slideDistancePx })
                    }
                },
                label = "gumball-odometer",
            ) { value ->
                Text(
                    text = String.format(Locale.US, "%,d", value),
                    color = Tokens.UI.ink,
                    fontSize = Tokens.UI.counterDigitSize.sp,
                    lineHeight = Tokens.UI.counterDigitLineHeight.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = Tokens.UI.counterDigitTracking.sp,
                    style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
        }
        Text(
            text = Tokens.COPY.coinsWord,
            modifier = Modifier.padding(start = Tokens.SPACE.xs.dp),
            color = Tokens.UI.ink.copy(alpha = Tokens.UI.counterWordAlpha.toFloat()),
            fontSize = Tokens.UI.counterWordSize.sp,
            lineHeight = Tokens.UI.counterWordLineHeight.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}

@Composable
private fun GumballCrankCollectButton(
    top: androidx.compose.ui.unit.Dp,
    frameVersion: () -> Long,
    simulation: GumballCrankSimulation,
    active: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) Tokens.UI.buttonPressedScale.toFloat() else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) Tokens.UI.buttonPressMillis else Tokens.UI.buttonReleaseMillis,
            easing = CubicBezierEasing(
                Tokens.UI.buttonCurveX1,
                Tokens.UI.buttonCurveY1,
                Tokens.UI.buttonCurveX2,
                Tokens.UI.buttonCurveY2,
            ),
        ),
        label = "gumball-collect-press",
    )
    val actionModifier = if (active) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = Tokens.COPY.collect,
        ) {
            simulation.command(GumballCrankCommand.COLLECT)
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        Modifier
            .fillMaxSize()
            .offset(y = top),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .height(Tokens.UI.buttonHeight.dp)
                .graphicsLayer {
                    frameVersion()
                    alpha = simulation.cardTextOpacity.toFloat().coerceIn(0f, 1f)
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .background(Tokens.UI.button, RoundedCornerShape(Tokens.UI.buttonRadius.dp))
                .then(actionModifier)
                .padding(horizontal = Tokens.SPACE.xl.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.gumballcrank_coin),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = Tokens.SPACE.sm.dp)
                    .size(Tokens.UI.buttonCoin.dp),
            )
            Text(
                text = Tokens.COPY.collect,
                color = Tokens.UI.navy,
                fontSize = Tokens.UI.buttonTextSize.sp,
                lineHeight = Tokens.UI.buttonTextLineHeight.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

private fun gumballCrankReducedMotion(context: android.content.Context): Boolean {
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
