package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.sqrt

/** The block's clock; draw, layout and graphicsLayer lambdas read `frame` from it so they redraw every frame. */
internal val LocalFolioClock = staticCompositionLocalOf<FolioFeatureLibraryClock> {
    error("FolioFeatureLibrary provides its clock")
}

/** Haptics for the block's three moments, on the view (a light tick for impact and selection, a confirm for success). */
@Composable
internal fun rememberFolioHaptic(): (FolioHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind: FolioHaptic -> playFolioHaptic(view, kind) } }
}

private fun playFolioHaptic(view: View, kind: FolioHaptic) {
    when (kind) {
        FolioHaptic.Light, FolioHaptic.Selection -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        FolioHaptic.Success -> {
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(confirm)
        }
    }
}

/**
 * React Native's `pointerEvents` of a whole layer. A layer that is not [interactive] reports a size of
 * zero, so Compose's hit test (which skips children outside their parent's bounds) never reaches it,
 * while its content still lays out and draws at full size. [hidden] also hides it from accessibility
 * (`importantForAccessibility="no-hide-descendants"`).
 */
@Composable
internal fun FolioHitGate(
    interactive: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Layout(
        modifier = if (hidden) modifier.clearAndSetSemantics { } else modifier,
        content = { Box(content = content) },
    ) { measurables, constraints ->
        // A layer that fills the screen is measured at the full size; any other keeps the constraints it was given.
        val childConstraints = if (fill) Constraints.fixed(constraints.maxWidth, constraints.maxHeight) else constraints
        val placeable = measurables.first().measure(childConstraints)
        val width = if (interactive) placeable.width else 0
        val height = if (interactive) placeable.height else 0
        layout(width, height) { placeable.place(0, 0) }
    }
}

/**
 * `PressScale`: a surface that settles to [scale] (0.96, or 0.98 for large ones) in 150 ms on the strong
 * ease-out and returns on the same curve; under Reduce Motion it dims to 0.72 instead. A disabled one never
 * moves. The press shows even when a parent scroll takes the touch away.
 */
@Composable
internal fun FolioPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: Float = 0.96f,
    reduced: Boolean = false,
    label: String? = null,
    pressedAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    val down = pressed && enabled
    val scaleValue by animateFloatAsState(
        targetValue = if (down && !reduced) scale else 1f,
        animationSpec = tween(durationMillis = 150, easing = EaseOutStrong),
        label = "folioPressScale",
    )
    val alphaValue by animateFloatAsState(
        targetValue = if (down && reduced) 0.72f else if (down) pressedAlpha else 1f,
        animationSpec = tween(durationMillis = 150, easing = EaseOutStrong),
        label = "folioPressAlpha",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleValue
                scaleY = scaleValue
                alpha = alphaValue
            }
            .then(modifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (label != null) contentDescription = label
                if (!enabled) disabled()
                this.onClick {
                    latestClick()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { latestClick() },
                )
            },
        content = content,
    )
}

/** `ZoomIn.springify().damping().stiffness()`: the content grows from nothing on a spring (mass 1). */
@Composable
internal fun FolioZoomIn(
    enabled: Boolean,
    damping: Float,
    stiffness: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val grow = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            grow.animateTo(1f, spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness))
        } else {
            grow.snapTo(1f)
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = grow.value
            scaleY = grow.value
        },
    ) { content() }
}

/** The icon of a [FolioButton]; the heart pops in when it appears. */
internal enum class FolioButtonIcon { Heart, HeartOutline, Add, Shuffle, ArrowDown }

/**
 * The `Button` of the block: green by default, [dark] for the library's Remix and Get, [secondary] for
 * Save to Favorites, [pill] for the 252 dp Generate New. A [busy] or disabled button is pale and does not move.
 */
@Composable
internal fun FolioButton(
    label: String,
    onClick: () -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
    dark: Boolean = false,
    secondary: Boolean = false,
    pill: Boolean = false,
    icon: FolioButtonIcon? = null,
    busy: Boolean = false,
) {
    val inactive = disabled || busy
    val ink = if (secondary) Color(0xFF262626) else FolioWhite
    val radius = if (dark) 24f else 26f
    val shape = RoundedCornerShape(radius.dp)
    val minHeight = if (dark || secondary) 48.dp else 52.dp
    val background = when {
        inactive -> FolioGreenBusy
        dark -> FolioDark
        secondary -> FolioWhite
        else -> FolioGreen
    }
    var surface: Modifier = modifier
    if (pill) surface = surface.width(252.dp)
    if (!inactive && !dark) {
        surface = surface.folioShadow(if (secondary) FolioShadowButtonSecondary else FolioShadowButton) { radius }
    }
    surface = surface.clip(shape).background(background, shape)
    if (secondary && !inactive) surface = surface.border(Dp.Hairline, Color(0xFFDADADA), shape)
    if (!inactive && !dark && !secondary) {
        surface = surface.folioInset(Color(1f, 1f, 1f, 0.16f), offsetY = 1f, spread = 0f, radius = radius)
    }
    FolioPressable(
        onClick = onClick,
        modifier = surface.heightIn(min = minHeight).padding(horizontal = if (pill) 18.dp else 22.dp),
        enabled = !inactive,
        reduced = reduced,
        label = label,
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) FolioButtonGlyph(icon, ink, reduced)
            FolioText(
                text = label,
                size = 16f,
                color = if (inactive) Color(0xD9FFFFFF) else ink,
                weight = FontWeight.Medium,
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FolioButtonGlyph(icon: FolioButtonIcon, ink: Color, reduced: Boolean) {
    when (icon) {
        FolioButtonIcon.Heart -> FolioZoomIn(enabled = !reduced, damping = 11f, stiffness = 300f) {
            FolioIconView(FolioIcon.Heart, 17f, Color(0xFFD9534F))
        }
        FolioButtonIcon.HeartOutline -> FolioIconView(FolioIcon.HeartOutline, 17f, ink)
        FolioButtonIcon.Add -> FolioIconView(FolioIcon.Add, 18f, ink)
        FolioButtonIcon.Shuffle -> FolioIconView(FolioIcon.Shuffle, 17f, ink)
        FolioButtonIcon.ArrowDown -> FolioIconView(FolioIcon.ArrowDown, 17f, ink)
    }
}

/** `Working`: three soft dots, overlapping by 2 dp, each a little paler. */
@Composable
internal fun FolioWorking() {
    Box(Modifier.size(width = 21.dp, height = 9.dp)) {
        for (index in 0..2) {
            Box(
                Modifier
                    .offset(x = (index * 7 - 2).dp)
                    .size(9.dp)
                    .graphicsLayer { alpha = 1f - index * 0.22f }
                    .background(Color(0xFF4A4A4A), CircleShape),
            )
        }
    }
}
