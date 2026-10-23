package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min

/** Wide display face for the headlines; the file and its licence sit next to this one. */
private val DisplayFont = FontFamily(Font(R.font.envelopegiftreveal_archivo_expanded_black, FontWeight.Black))

/** Room around the button and the message for their soft shadows, so the layer does not clip them. */
private const val SHADOW_BLEED = 64f

private val buttonShadow = EnvelopeSoftRoundRect(0x593A4A00, 11f)
private val messageShadow = EnvelopeSoftRoundRect(0x14000000, 9f)

/**
 * Envelope Gift Reveal: swipe up on the envelope (or press Open). The flap folds back, the card
 * follows the finger out, flies into place and the page turns from lime to white under confetti.
 */
@Composable
fun EnvelopeGiftReveal(autoPlay: Boolean = false, onShop: (() -> Unit)? = null) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val reduced = rememberReducedMotion()
        // A new size or motion setting starts the screen again, as the React Native key does.
        key(width, height, reduced) {
            EnvelopeGiftRevealScreen(width, height, reduced, autoPlay, onShop)
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
    val topInset = WindowInsets.systemBars.union(WindowInsets.displayCutout).getTop(density) / density.density
    val lay = envelopeScreenLayout(height, topInset)
    val currentLay by rememberUpdatedState(lay)
    var phase by remember { mutableIntStateOf(EnvelopePhase.sealed) }
    val roll = rememberEnvelopeRoll(enabled = !reduced)
    val engine = remember {
        EnvelopeGiftRevealEngine(
            width = width,
            centreY = { currentLay.cardTop + EnvelopeLayout.cardH / 2f },
            envTop = { currentLay.envTop },
            autoPlay = autoPlay,
            reduced = reduced,
            onPhase = { phase = it },
            onHaptic = { performEnvelopeHaptic(view, it) },
        )
    }
    val painter = remember(context) {
        EnvelopeGiftRevealPainter(context, width, height, reduced, { currentLay }, { roll.value })
    }
    val fades = remember { Array(3) { EnvelopeFinalFade(it, reduced) } }

    // The final screen fades in on the phase change, not from the clock.
    val showFinal = phase == EnvelopePhase.revealing || phase == EnvelopePhase.revealed
    val sealed = phase == EnvelopePhase.sealed || phase == EnvelopePhase.dragging
    val revealed = phase == EnvelopePhase.revealed
    LaunchedEffect(showFinal) {
        for (fade in fades) fade.retarget(showFinal, engine.clock)
    }

    // One clock for everything: the engine advances it each frame, capped at 64 ms like the original.
    LaunchedEffect(engine) {
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dtMs = if (last == 0L) 16f else min((nanos - last) / 1_000_000f, 64f)
                last = nanos
                engine.onFrame(dtMs)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(EnvelopeColors.lime)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(engine) { trackEnvelopePan(engine) }
                .drawBehind {
                    // Reading the tick makes this lambda, and only this one, run again every frame.
                    if (engine.tick < 0L) return@drawBehind
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.scale(this.density, this.density)
                        painter.draw(native, engine)
                        native.restore()
                    }
                },
        )
        Box(
            modifier = Modifier
                .offset(x = ((width - EnvelopeLayout.envW) / 2f).dp, y = lay.envTop.dp)
                .size(EnvelopeLayout.envW.dp, EnvelopeLayout.envH.dp)
                .clearAndSetSemantics {
                    if (sealed) {
                        contentDescription = "Gift envelope. Swipe up to open it, or double tap"
                        role = Role.Button
                        onClick("Open the gift") {
                            openFromUi(engine)
                            true
                        }
                    }
                },
        )
        EnvelopeHeadline(engine, lay, width, reduced)
        EnvelopeOpenButton(engine, lay, width, sealed)
        EnvelopeTitle(engine, lay, width, fades[0])
        EnvelopeMessage(engine, lay, width, revealed, fades[1])
        EnvelopeCta(engine, lay, width, revealed, fades[2], onShop)
    }
}

/** Letters leave one after another, sliding down as the flap starts to lift (wide type, staggered). */
@Composable
private fun EnvelopeHeadline(engine: EnvelopeGiftRevealEngine, lay: EnvelopeScreenLayout, width: Float, reduced: Boolean) {
    Box(
        modifier = Modifier
            .offset(x = EnvelopeLayout.finalSide.dp, y = lay.headlineTop.dp)
            .width((width - EnvelopeLayout.finalSide - 8f).dp)
            .clearAndSetSemantics {
                heading()
                contentDescription = EnvelopeGift.headline.replace('\n', ' ')
            },
    ) {
        Column {
            EnvelopeGift.headline.split('\n').forEachIndexed { row, line ->
                Row {
                    line.forEachIndexed { index, ch ->
                        EnvelopeLetter(ch.toString(), row * 8 + index, engine, reduced)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvelopeLetter(ch: String, order: Int, engine: EnvelopeGiftRevealEngine, reduced: Boolean) {
    Text(
        text = ch,
        style = displayStyle(EnvelopeLayout.headline, EnvelopeLayout.headlineLine, -1f),
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            if (engine.tick < 0L) return@graphicsLayer
            val f = engine.frame
            if (f.reset > 0f) {
                alpha = f.reset
                translationY = 0f
                return@graphicsLayer
            }
            val u = if (reduced) {
                envelopeClamp01(f.p / 0.08f)
            } else {
                envelopeClamp01((f.p - order * EnvelopeHeadline.stagger) / EnvelopeHeadline.span)
            }
            val e = u * u * (3f - 2f * u)
            alpha = 1f - e
            translationY = EnvelopeHeadline.drop * e * density
        },
    )
}

/** The round black Open button; it shrinks and fades as the flap starts to lift. */
@Composable
private fun EnvelopeOpenButton(engine: EnvelopeGiftRevealEngine, lay: EnvelopeScreenLayout, width: Float, sealed: Boolean) {
    val d = EnvelopeLayout.buttonD
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberEnvelopePressScale(pressed)
    EnvelopePressTakeover(interaction, engine)
    Box(
        modifier = Modifier
            .offset(x = ((width - d) / 2f - SHADOW_BLEED).dp, y = (lay.buttonY - d / 2f - SHADOW_BLEED).dp)
            .size((d + SHADOW_BLEED * 2f).dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                val f = engine.frame
                val leaving = if (f.reset > 0f) 1f - f.reset else envelopeClamp01(f.p / 0.06f)
                alpha = 1f - leaving
                val s = (1f - 0.08f * leaving) * pressScale.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(this.density, this.density)
                    val origin = SHADOW_BLEED
                    buttonShadow.draw(native, origin, origin + 14f, d, d, d / 2f)
                    native.restore()
                }
                drawCircle(
                    color = EnvelopeColors.ink,
                    radius = d / 2f * this.density,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(d.dp)
                .clip(CircleShape)
                .then(
                    if (sealed) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClickLabel = "Open the gift",
                            role = Role.Button,
                        ) { openFromUi(engine) }
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = "Open the gift" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "OPEN",
                color = EnvelopeColors.white,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
            )
        }
    }
}

@Composable
private fun EnvelopeTitle(engine: EnvelopeGiftRevealEngine, lay: EnvelopeScreenLayout, width: Float, fade: EnvelopeFinalFade) {
    Text(
        text = EnvelopeGift.title,
        style = displayStyle(EnvelopeLayout.title, EnvelopeLayout.titleLine, -0.5f),
        modifier = Modifier
            .offset(x = EnvelopeLayout.finalSide.dp, y = lay.titleTop.dp)
            .width((width - EnvelopeLayout.finalSide - 8f).dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
            }
            .semantics { heading() },
    )
}

/** The white message card; its soft shadow is drawn inside a padded box so the fade layer keeps it. */
@Composable
private fun EnvelopeMessage(
    engine: EnvelopeGiftRevealEngine,
    lay: EnvelopeScreenLayout,
    width: Float,
    revealed: Boolean,
    fade: EnvelopeFinalFade,
) {
    val w = width - EnvelopeLayout.finalSide * 2f
    val h = EnvelopeLayout.messageH
    Box(
        modifier = Modifier
            .offset(x = (EnvelopeLayout.finalSide - SHADOW_BLEED).dp, y = (lay.messageTop - SHADOW_BLEED).dp)
            .size((w + SHADOW_BLEED * 2f).dp, (h + SHADOW_BLEED * 2f).dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
            }
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(this.density, this.density)
                    messageShadow.draw(native, SHADOW_BLEED, SHADOW_BLEED + 8f, w, h, 4f)
                    native.restore()
                }
                drawRoundRect(
                    color = EnvelopeColors.message,
                    topLeft = Offset(SHADOW_BLEED * this.density, SHADOW_BLEED * this.density),
                    size = Size(w * this.density, h * this.density),
                    cornerRadius = CornerRadius(4f * this.density),
                )
            }
            .clearAndSetSemantics {
                if (revealed) contentDescription = "Gift card, ${EnvelopeGift.amount}. ${EnvelopeGift.message}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = EnvelopeGift.message,
            color = EnvelopeColors.messageInk,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = (SHADOW_BLEED + 20f).dp),
        )
    }
}

@Composable
private fun EnvelopeCta(
    engine: EnvelopeGiftRevealEngine,
    lay: EnvelopeScreenLayout,
    width: Float,
    revealed: Boolean,
    fade: EnvelopeFinalFade,
    onShop: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberEnvelopePressScale(pressed)
    EnvelopePressTakeover(interaction, engine)
    Box(
        modifier = Modifier
            .offset(x = EnvelopeLayout.finalSide.dp, y = lay.ctaTop.dp)
            .size((width - EnvelopeLayout.finalSide * 2f).dp, EnvelopeLayout.buttonH.dp)
            .graphicsLayer {
                if (engine.tick < 0L) return@graphicsLayer
                alpha = fade.opacity(engine.clock)
                translationY = fade.rise(engine.clock) * density
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .background(EnvelopeColors.ink)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = revealed,
                onClickLabel = EnvelopeGift.cta,
                role = Role.Button,
            ) {
                if (onShop != null) {
                    onShop()
                } else {
                    engine.takeOver()
                    engine.reset()
                    engine.letGo()
                }
            }
            .semantics { contentDescription = EnvelopeGift.cta },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = EnvelopeGift.cta.uppercase(),
            color = EnvelopeColors.white,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
}

/** Presses own the script immediately, like Pressable's down event rather than its click callback. */
@Composable
private fun EnvelopePressTakeover(interaction: MutableInteractionSource, engine: EnvelopeGiftRevealEngine) {
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

/** Retargeting preserves the scale reached so far, matching the interruptible Reanimated transition. */
@Composable
private fun rememberEnvelopePressScale(pressed: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        val target = if (pressed) EnvelopePolish.pressScale else 1f
        val duration = if (pressed) EnvelopePolish.pressInMs else EnvelopePolish.pressOutMs
        scale.animateTo(target, tween(durationMillis = duration, easing = EnvelopePolish.easeOut))
    }
    return scale.asState()
}

private fun displayStyle(size: Float, line: Float, spacing: Float) = TextStyle(
    color = EnvelopeColors.ink,
    fontFamily = DisplayFont,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
    // The line box is centred on the glyphs, as a smaller line height is on iOS.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** The button, and the accessible alternative: open without dragging. */
private fun openFromUi(engine: EnvelopeGiftRevealEngine) {
    engine.takeOver()
    engine.open()
    engine.letGo()
}

/** Gesture.Pan().minDistance(0).maxPointers(1): the first finger on the scene drives the envelope. */
private suspend fun PointerInputScope.trackEnvelopePan(engine: EnvelopeGiftRevealEngine) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        engine.takeOver()
        engine.down(down.position.x / density, down.position.y / density, false)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) break
                // Gesture.Pan().maxPointers(1) finalizes when another finger joins.
                if (event.changes.count { it.pressed } > 1) break
                engine.move(change.position.x / density, change.position.y / density)
                change.consume()
            }
        } finally {
            engine.up(tracker.calculateVelocity().y / density)
            engine.letGo()
        }
    }
}

/** iOS impact styles mapped to the closest Android constants (the same mapping as the other blocks). */
private fun performEnvelopeHaptic(view: View, kind: EnvelopeHaptic) {
    val constant = when (kind) {
        EnvelopeHaptic.Crack -> HapticFeedbackConstants.CLOCK_TICK
        EnvelopeHaptic.Commit -> HapticFeedbackConstants.CONTEXT_CLICK
        EnvelopeHaptic.Success ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        EnvelopeHaptic.Flap ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.KEYBOARD_TAP
        EnvelopeHaptic.Light -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    view.performHapticFeedback(constant)
}
