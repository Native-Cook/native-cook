package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ArrowColor = Color(0xFF1A1A1A)
private val LightSurface = Color(0xFFE7ECE2)
private val DotColor = Color(0xFF3A3A3A)
private val ActiveDotColor = Color(0xFFF2F2F0)

/** `arrowGlyph` (line 127): 22 sp on a 26 sp line, centred; dark on the light "applied" surface. */
private fun glyphStyle(glyph: String): TextStyle = TextStyle(
    color = if (glyph == "✓") Color(0xFF121612) else Color(0xFFD9DED5),
    fontSize = 22.sp,
    lineHeight = 26.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = SilverScreenCarouselLineHeightStyle,
)

/** `applyLabel` (line 127): 14 sp semibold on the light pill. */
private val ApplyLabelStyle = TextStyle(
    color = Color(0xFF192019),
    fontSize = 14.sp,
    fontWeight = FontWeight.SemiBold,
)

/**
 * The foot of the page (`footer`, SilverScreenCarousel.tsx:108-119): [marginTop] below the cards, 34 dp
 * from each side.
 *
 * In the reference layout ([referenceLayout]) it is the reference's pager: four step dots on the left
 * (they count onboarding steps, the first of four, not film profiles) and a round arrow on the right
 * that applies the profile in the gate; once applied the arrow becomes a check and a note appears. In
 * the large-text or narrow layout it is a previous arrow, one dot per profile and a next arrow, a pill
 * button under them and a status line that is always shown.
 *
 * [enabled] is false while `previewTime` freezes the screen: nothing answers touch then.
 */
@Composable
internal fun SilverScreenCarouselFooter(
    motion: SilverScreenCarouselMotion,
    referenceLayout: Boolean,
    reduced: Boolean,
    enabled: Boolean,
    marginTop: Float,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = motion.selected
    val applied = motion.applied
    val profile = SilverScreenCarouselProfiles[selectedIndex]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = marginTop.dp)
            .padding(horizontal = 34.dp),
    ) {
        if (referenceLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .height(48.dp)
                        .clearAndSetSemantics { contentDescription = "Step 1 of 4" },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (index in 0 until 4) {
                            val size: Dp = if (index == 0) 6.dp else 5.dp
                            Box(
                                Modifier
                                    .size(size)
                                    .clip(CircleShape)
                                    .background(if (index == 0) ActiveDotColor else DotColor),
                            )
                        }
                    }
                }
                SilverScreenCarouselPressScale(
                    enabled = enabled,
                    reduced = reduced,
                    description = if (applied != null) "Try another film profile" else "Use ${profile.name}",
                    onPress = { if (motion.applied != null) motion.tryAnother() else motion.applyProfile() },
                    surface = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (applied != null) LightSurface else ArrowColor),
                ) {
                    SilverScreenCarouselSwapText(
                        text = if (applied != null) "✓" else "→",
                        styleFor = ::glyphStyle,
                        reduced = reduced,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SilverScreenCarouselArrowButton(
                    glyph = "←",
                    description = "Previous film profile",
                    isDisabled = selectedIndex == 0 || !enabled,
                    dimmed = selectedIndex == 0,
                    onPress = { motion.select(motion.selected - 1) },
                )
                Row {
                    for (index in 0 until 4) {
                        SilverScreenCarouselDotTarget(
                            name = SilverScreenCarouselProfiles[index].name,
                            active = selectedIndex == index,
                            reduced = reduced,
                            enabled = enabled,
                            onPress = { motion.select(index) },
                        )
                    }
                }
                SilverScreenCarouselArrowButton(
                    glyph = "→",
                    description = "Next film profile",
                    isDisabled = selectedIndex == 3 || !enabled,
                    dimmed = selectedIndex == 3,
                    onPress = { motion.select(motion.selected + 1) },
                )
            }
            SilverScreenCarouselPressScale(
                enabled = enabled,
                reduced = reduced,
                description = if (applied != null) "Try another profile" else "Use ${profile.name}",
                onPress = { if (motion.applied != null) motion.tryAnother() else motion.applyProfile() },
                surface = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(LightSurface),
            ) {
                // The pill's padding (20 by 12) sits on the label, inside the touch area of the whole pill.
                SilverScreenCarouselSwapText(
                    text = if (applied != null) "Try another profile" else "Use ${profile.name}",
                    styleFor = { ApplyLabelStyle },
                    reduced = reduced,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (applied != null || !referenceLayout) {
            BasicText(
                text = if (applied != null) "$applied applied to your preview." else "Swipe to find your point of view.",
                style = TextStyle(
                    color = Color(0xFFA3ADA0),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeightStyle = SilverScreenCarouselLineHeightStyle,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * A previous or next arrow of the large-text layout (`arrow`, line 114): a 58 dp round button with a
 * 22 sp glyph that ignores the system font scale (`allowFontScaling={false}`). At the first or last
 * profile it is [dimmed] to 32% and does nothing ([isDisabled]); under a finger it is 60%.
 */
@Composable
private fun SilverScreenCarouselArrowButton(
    glyph: String,
    description: String,
    isDisabled: Boolean,
    dimmed: Boolean,
    onPress: () -> Unit,
) {
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val fixedScale = LocalDensity.current
    val unscaled = remember(fixedScale) { Density(fixedScale.density, 1f) }
    Box(
        modifier = Modifier
            .alpha(if (dimmed) 0.32f else if (pressed && !isDisabled) 0.6f else 1f)
            .size(58.dp)
            .clip(CircleShape)
            .background(ArrowColor)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (isDisabled) disabled()
                onClick(description) {
                    if (isDisabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .silverScreenCarouselPressable(
                enabled = !isDisabled,
                onPressedChange = { pressed = it },
                onPress = { currentPress() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalDensity provides unscaled) {
            BasicText(
                text = glyph,
                style = glyphStyle(glyph),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * One dot of the large-text pager (`dotTarget`, line 114): a 48 dp square that selects its profile, with
 * a dot that grows from 5 to 6 dp and lights up from #3A3A3A to #F2F2F0 in 240 ms on the strong
 * ease-out (instantly under Reduce Motion).
 */
@Composable
private fun SilverScreenCarouselDotTarget(
    name: String,
    active: Boolean,
    reduced: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
) {
    val currentPress by rememberUpdatedState(onPress)
    val duration = if (reduced) 0 else 240
    val dotSize by animateDpAsState(
        targetValue = if (active) 6.dp else 5.dp,
        animationSpec = tween(duration, easing = SilverScreenCarouselSettle),
        label = "silverScreenCarouselDotSize",
    )
    val dotColor by animateColorAsState(
        targetValue = if (active) ActiveDotColor else DotColor,
        animationSpec = tween(duration, easing = SilverScreenCarouselSettle),
        label = "silverScreenCarouselDotColor",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Select $name"
                this.selected = active
                if (!enabled) disabled()
                onClick("Select $name") {
                    if (!enabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .silverScreenCarouselPressable(
                enabled = enabled,
                onPressedChange = { },
                onPress = { currentPress() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}
