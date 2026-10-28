package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Parent layout reserves this height only when device size or system text size changes. */
internal fun habitatIdentityTextHeight(scale: Float, fontScale: Float = 1f): Float =
    60 * scale * min(2f, max(1f, fontScale))

/**
 * Choose Your Ball's wordmark morph, reused: when the selection changes, letters the two names
 * share glide to their new place while the rest blur out and back in, so the headline is rewritten
 * rather than replaced (CHARIZARD -> BLASTOISE keeps its A, R and I).
 * Species 15 + name 45 at default text size; windows expand up to 2x without animating layout.
 */
@Composable
internal fun HabitatIdentityText(
    reducedMotion: Boolean,
    scale: Float,
    fontScale: Float,
    top: Float,
    species: List<String>,
    names: List<String>,
    selectedIndex: Int,
    width: Float,
) {
    val count = names.size
    val index = ((selectedIndex % count) + count) % count
    val effectiveFontScale = min(2f, max(1f, fontScale))
    val speciesHeight = 15 * scale * effectiveFontScale
    val nameHeight = 45 * scale * effectiveFontScale
    // The reference's Skia text takes its size in points, not scaled again by the system font size.
    val system = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(system.density, fontScale = 1f)) {
        Column(Modifier.offset(0.dp, top.dp).size(width.dp, habitatIdentityTextHeight(scale, fontScale).dp)) {
            HabitatMorphingText(
                text = species[index], fitTexts = species, fontSize = (10 * scale * effectiveFontScale).dp, tracking = (1 * scale).dp,
                width = width.dp, height = speciesHeight.dp, paddingLeft = 0.dp, color = Color(0xFFB3C1AD), reducedMotion = reducedMotion,
                weight = FontWeight.Normal, center = true,
            )
            HabitatMorphingText(
                text = names[index], fitTexts = names, fontSize = (37 * scale * effectiveFontScale).dp, tracking = (-1.3f * scale).dp,
                width = width.dp, height = nameHeight.dp, paddingLeft = 0.dp, color = Color(0xFFF5F4E9), reducedMotion = reducedMotion,
                weight = FontWeight.SemiBold, center = true,
            )
        }
    }
}
