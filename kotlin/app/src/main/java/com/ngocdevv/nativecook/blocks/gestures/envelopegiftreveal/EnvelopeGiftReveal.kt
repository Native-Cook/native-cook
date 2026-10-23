package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min

@Composable
fun EnvelopeGiftReveal(autoPlay: Boolean = false, onShop: (() -> Unit)? = null) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val reduced = rememberReducedMotion()
        key(width, height, reduced) {
            EnvelopeGiftRevealScreen(
                width = width,
                height = height,
                reduced = reduced,
                autoPlay = autoPlay,
                onShop = onShop,
            )
        }
    }
}

@Composable
private fun EnvelopeGiftRevealScreen(
    width: Float,
    height: Float,
    reduced: Boolean,
    autoPlay: Boolean,
    onShop: (() -> Unit)?,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val topInset = insets.getTop(density) / density.density
    val bottomInset = insets.getBottom(density) / density.density
    val screen = envelopeGiftRevealScreenLayout(height, topInset, bottomInset)
    val currentScreen by rememberUpdatedState(screen)
    val serif = remember(context) {
        FontFamily(Font(EnvelopeGiftRevealAssets.serif, context.assets))
    }
    val ui = remember(context) {
        FontFamily(Font(EnvelopeGiftRevealAssets.ui, context.assets))
    }
    val uiBold = remember(context) {
        FontFamily(Font(EnvelopeGiftRevealAssets.uiBold, context.assets))
    }
    var phase by remember { mutableIntStateOf(EnvelopeGiftRevealPhase.sealed) }
    val engine = remember {
        EnvelopeGiftRevealEngine(
            width = width,
            envTop = { currentScreen.envTop },
            autoPlay = autoPlay && !reduced,
            reduced = reduced,
            onPhase = { phase = it },
            onHaptic = { performEnvelopeGiftRevealHaptic(view, it) },
        )
    }
    val painter = remember(context) {
        EnvelopeGiftRevealPainter(
            context = context,
            width = width,
            height = height,
            reduced = reduced,
            layout = { currentScreen },
        )
    }
    val fades = remember { Array(3) { EnvelopeGiftRevealFinalFade(it, reduced) } }
    val showFinal = phase == EnvelopeGiftRevealPhase.revealing ||
        phase == EnvelopeGiftRevealPhase.revealed
    val sealed = phase == EnvelopeGiftRevealPhase.sealed ||
        phase == EnvelopeGiftRevealPhase.dragging
    val revealed = phase == EnvelopeGiftRevealPhase.revealed
    LaunchedEffect(showFinal) {
        fades.forEach { fade -> fade.retarget(showFinal, engine.clock) }
    }
    LaunchedEffect(engine) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val delta = if (lastFrame == 0L) {
                    16f
                } else {
                    min((frameNanos - lastFrame) / 1_000_000f, 64f)
                }
                lastFrame = frameNanos
                engine.onFrame(delta)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EnvelopeGiftRevealColors.table),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(engine) { trackEnvelopeGiftRevealPan(engine) }
                .drawBehind {
                    if (engine.tick < 0L) return@drawBehind
                    drawIntoCanvas { composeCanvas ->
                        val native = composeCanvas.nativeCanvas
                        native.save()
                        native.scale(this.density, this.density)
                        painter.draw(native, engine)
                        native.restore()
                    }
                },
        )
        Box(
            modifier = Modifier
                .offset(
                    x = ((width - EnvelopeGiftRevealLayout.envW) / 2f).dp,
                    y = screen.envTop.dp,
                )
                .size(EnvelopeGiftRevealLayout.envW.dp, EnvelopeGiftRevealLayout.envH.dp)
                .clearAndSetSemantics {
                    if (sealed) {
                        contentDescription = "Gift envelope. Swipe up to open it, or double tap"
                        role = Role.Button
                        onClick("Open the letter") {
                            openEnvelopeGiftRevealFromUi(engine)
                            true
                        }
                    }
                },
        )
        EnvelopeGiftRevealKicker(engine, screen, ui)
        EnvelopeGiftRevealHeadline(engine, screen, width, reduced, serif)
        EnvelopeGiftRevealOpenButton(engine, screen, sealed, ui)
        EnvelopeGiftRevealTitle(engine, screen, width, fades[0], serif)
        EnvelopeGiftRevealMessage(engine, screen, width, revealed, fades[1], ui)
        EnvelopeGiftRevealCta(engine, screen, width, revealed, fades[2], uiBold, onShop)
    }
}

@Composable
private fun EnvelopeGiftRevealKicker(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    family: FontFamily,
) {
    Text(
        text = EnvelopeGiftRevealGift.kicker,
        color = EnvelopeGiftRevealColors.mute,
        fontFamily = family,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp,
        modifier = Modifier
            .offset(
                x = EnvelopeGiftRevealLayout.side.dp,
                y = (screen.headlineTop - 24f).dp,
            )
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                val frame = engine.frame
                alpha = if (frame.reset > 0f) {
                    frame.resetIn
                } else {
                    1f - envelopeGiftRevealClamp01(frame.p / 0.08f)
                }
            },
    )
}

@Composable
private fun EnvelopeGiftRevealHeadline(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    width: Float,
    reduced: Boolean,
    family: FontFamily,
) {
    Box(
        modifier = Modifier
            .offset(x = EnvelopeGiftRevealLayout.side.dp, y = screen.headlineTop.dp)
            .width((width - EnvelopeGiftRevealLayout.side * 2f).dp)
            .clearAndSetSemantics {
                heading()
                contentDescription = EnvelopeGiftRevealGift.headline.replace('\n', ' ')
            },
    ) {
        Column {
            EnvelopeGiftRevealGift.headline.split('\n').forEachIndexed { row, line ->
                Row(modifier = Modifier.height(EnvelopeGiftRevealLayout.headlineLine.dp)) {
                    line.forEachIndexed { index, character ->
                        EnvelopeGiftRevealLetter(
                            character = character.toString(),
                            order = row * 8 + index,
                            engine = engine,
                            reduced = reduced,
                            family = family,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvelopeGiftRevealLetter(
    character: String,
    order: Int,
    engine: EnvelopeGiftRevealEngine,
    reduced: Boolean,
    family: FontFamily,
) {
    Text(
        text = character,
        softWrap = false,
        style = TextStyle(
            color = EnvelopeGiftRevealColors.ink,
            fontFamily = family,
            fontSize = EnvelopeGiftRevealLayout.headline.sp,
            lineHeight = EnvelopeGiftRevealLayout.headlineLine.sp,
            letterSpacing = (-0.6f).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        modifier = Modifier.graphicsLayer {
            if (engine.tick < 0L) return@graphicsLayer
            val frame = engine.frame
            if (frame.reset > 0f) {
                val start = EnvelopeGiftRevealReset.inFrom * EnvelopeGiftRevealReset.fadeMs +
                    order * EnvelopeGiftRevealReset.letterStagger
                val progress = envelopeGiftRevealClamp01(
                    (frame.reset * EnvelopeGiftRevealReset.fadeMs - start) /
                        EnvelopeGiftRevealReset.letterMs,
                )
                val eased = envelopeGiftRevealEaseOut(progress)
                alpha = eased
                translationY = EnvelopeGiftRevealHeadline.drop * (1f - eased) * density
                return@graphicsLayer
            }
            val progress = if (reduced) {
                envelopeGiftRevealClamp01(frame.p / 0.08f)
            } else {
                envelopeGiftRevealClamp01(
                    (frame.p - order * EnvelopeGiftRevealHeadline.stagger) /
                        EnvelopeGiftRevealHeadline.span,
                )
            }
            val eased = progress * progress * (3f - 2f * progress)
            alpha = 1f - eased
            translationY = EnvelopeGiftRevealHeadline.drop * eased * density
        },
    )
}

@Composable
private fun EnvelopeGiftRevealOpenButton(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    sealed: Boolean,
    family: FontFamily,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberEnvelopeGiftRevealPressScale(pressed)
    EnvelopeGiftRevealPressTakeover(interaction, engine)
    val shape = RoundedCornerShape(EnvelopeGiftRevealLayout.hintH.dp / 2)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .offset(y = (screen.buttonY - EnvelopeGiftRevealLayout.hintH / 2f).dp)
                .height(EnvelopeGiftRevealLayout.hintH.dp)
                .graphicsLayer {
                    if (engine.tick < 0L) return@graphicsLayer
                    val frame = engine.frame
                    val leaving = if (frame.reset > 0f) {
                        1f - frame.reset
                    } else {
                        envelopeGiftRevealClamp01(frame.p / 0.06f)
                    }
                    alpha = 1f - leaving
                    val scale = (1f - 0.08f * leaving) * pressScale.value
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(EnvelopeGiftRevealColors.ink.copy(alpha = 0.04f))
                .border(1.dp, EnvelopeGiftRevealColors.faint, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = sealed,
                    role = Role.Button,
                    onClickLabel = "Open the letter",
                ) { openEnvelopeGiftRevealFromUi(engine) }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawLine(
                        color = EnvelopeGiftRevealColors.ink,
                        start = Offset(2f, size.height * 0.7f),
                        end = Offset(size.width / 2f, size.height * 0.3f),
                        strokeWidth = 1.75.dp.toPx(),
                    )
                    drawLine(
                        color = EnvelopeGiftRevealColors.ink,
                        start = Offset(size.width / 2f, size.height * 0.3f),
                        end = Offset(size.width - 2f, size.height * 0.7f),
                        strokeWidth = 1.75.dp.toPx(),
                    )
                }
                Text(
                    text = EnvelopeGiftRevealGift.hint,
                    color = EnvelopeGiftRevealColors.ink,
                    fontFamily = family,
                    fontSize = 15.sp,
                    letterSpacing = (-0.1f).sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun EnvelopeGiftRevealTitle(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    width: Float,
    fade: EnvelopeGiftRevealFinalFade,
    family: FontFamily,
) {
    Text(
        text = EnvelopeGiftRevealGift.title,
        color = EnvelopeGiftRevealColors.ink,
        fontFamily = family,
        fontSize = EnvelopeGiftRevealLayout.title.sp,
        lineHeight = EnvelopeGiftRevealLayout.titleLine.sp,
        letterSpacing = (-0.4f).sp,
        modifier = Modifier
            .offset(x = EnvelopeGiftRevealLayout.side.dp, y = screen.titleTop.dp)
            .width((width - EnvelopeGiftRevealLayout.side * 2f).dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
            }
            .semantics { heading() },
    )
}

@Composable
private fun EnvelopeGiftRevealMessage(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    width: Float,
    revealed: Boolean,
    fade: EnvelopeGiftRevealFinalFade,
    family: FontFamily,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset(x = EnvelopeGiftRevealLayout.side.dp, y = screen.messageTop.dp)
            .width((width - EnvelopeGiftRevealLayout.side * 2f).dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
            }
            .clearAndSetSemantics {
                if (revealed) {
                    contentDescription = "Gift card, ${EnvelopeGiftRevealGift.amount} at ${EnvelopeGiftRevealGift.shop}. ${EnvelopeGiftRevealGift.message}"
                }
            },
    ) {
        Text(
            text = EnvelopeGiftRevealGift.caption,
            color = EnvelopeGiftRevealColors.ink,
            fontFamily = family,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = EnvelopeGiftRevealGift.captionSub,
            color = EnvelopeGiftRevealColors.mute,
            fontFamily = family,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EnvelopeGiftRevealCta(
    engine: EnvelopeGiftRevealEngine,
    screen: EnvelopeGiftRevealScreenLayout,
    width: Float,
    revealed: Boolean,
    fade: EnvelopeGiftRevealFinalFade,
    family: FontFamily,
    onShop: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberEnvelopeGiftRevealPressScale(pressed)
    EnvelopeGiftRevealPressTakeover(interaction, engine)
    val shape = RoundedCornerShape(EnvelopeGiftRevealLayout.buttonH.dp / 2)
    Box(
        modifier = Modifier
            .offset(x = EnvelopeGiftRevealLayout.side.dp, y = screen.ctaTop.dp)
            .size(
                width = (width - EnvelopeGiftRevealLayout.side * 2f).dp,
                height = EnvelopeGiftRevealLayout.buttonH.dp,
            )
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .clip(shape)
            .background(EnvelopeGiftRevealColors.cta)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = revealed,
                role = Role.Button,
                onClickLabel = EnvelopeGiftRevealGift.cta,
            ) {
                if (onShop != null) {
                    onShop()
                } else {
                    engine.takeOver()
                    engine.reset()
                    engine.letGo()
                }
            }
            .semantics { contentDescription = EnvelopeGiftRevealGift.cta },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = EnvelopeGiftRevealGift.cta,
            color = EnvelopeGiftRevealColors.ctaInk,
            fontFamily = family,
            fontSize = 16.sp,
            letterSpacing = (-0.2f).sp,
        )
    }
}

@Composable
private fun EnvelopeGiftRevealPressTakeover(
    interaction: MutableInteractionSource,
    engine: EnvelopeGiftRevealEngine,
) {
    LaunchedEffect(interaction, engine) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> engine.takeOver()
                is PressInteraction.Release -> engine.letGo()
                is PressInteraction.Cancel -> engine.letGo()
            }
        }
    }
}

@Composable
private fun rememberEnvelopeGiftRevealPressScale(pressed: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) EnvelopeGiftRevealPolish.pressScale else 1f,
            animationSpec = tween(
                durationMillis = if (pressed) {
                    EnvelopeGiftRevealPolish.pressInMs
                } else {
                    EnvelopeGiftRevealPolish.pressOutMs
                },
                easing = EnvelopeGiftRevealPolish.easeOut,
            ),
        )
    }
    return scale.asState()
}

private fun openEnvelopeGiftRevealFromUi(engine: EnvelopeGiftRevealEngine) {
    engine.takeOver()
    engine.open()
    engine.letGo()
}

private suspend fun PointerInputScope.trackEnvelopeGiftRevealPan(engine: EnvelopeGiftRevealEngine) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        engine.takeOver()
        engine.down(down.position.x / density, down.position.y / density, scripted = false)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed || event.changes.count { it.pressed } > 1) break
                engine.move(change.position.x / density, change.position.y / density)
                change.consume()
            }
        } finally {
            engine.up(tracker.calculateVelocity().y / density)
            engine.letGo()
        }
    }
}

private fun performEnvelopeGiftRevealHaptic(view: View, kind: EnvelopeGiftRevealHaptic) {
    val constant = when (kind) {
        EnvelopeGiftRevealHaptic.Crack -> HapticFeedbackConstants.CLOCK_TICK
        EnvelopeGiftRevealHaptic.Commit -> HapticFeedbackConstants.CONTEXT_CLICK
        EnvelopeGiftRevealHaptic.Success ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        EnvelopeGiftRevealHaptic.Flap ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                HapticFeedbackConstants.TEXT_HANDLE_MOVE
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        EnvelopeGiftRevealHaptic.Light -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    view.performHapticFeedback(constant)
}
