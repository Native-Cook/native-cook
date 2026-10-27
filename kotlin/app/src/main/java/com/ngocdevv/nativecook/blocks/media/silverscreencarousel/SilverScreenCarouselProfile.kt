package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.ui.graphics.Color

/**
 * One film profile of the carousel (an entry of `PROFILES`, SilverScreenCarousel.tsx:22). [photo] is the
 * bundled file under `assets/silverscreencarousel/`, [colors] the three stops of the colour bar.
 */
internal class SilverScreenCarouselProfile(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val photo: String,
    val colors: List<Color>,
)

/** The four profiles, in carousel order (SilverScreenCarousel.tsx:23-26). */
internal val SilverScreenCarouselProfiles = listOf(
    SilverScreenCarouselProfile(
        id = "ember",
        code = "EM",
        name = "Ember chrome",
        description = "Bold reds. Clear blue skies.",
        photo = "silverscreencarousel_red-geometry.jpg",
        colors = listOf(Color(0xFF3B79B9), Color(0xFFF6BA72), Color(0xFFC74728)),
    ),
    SilverScreenCarouselProfile(
        id = "afternoon",
        code = "AF",
        name = "Afternoon film",
        description = "Warm light. Soft green shadows.",
        photo = "silverscreencarousel_coastal-afternoon.jpg",
        colors = listOf(Color(0xFF38543B), Color(0xFFA6B092), Color(0xFFEFB88A)),
    ),
    SilverScreenCarouselProfile(
        id = "silver",
        code = "SV",
        name = "Silver midnight",
        description = "Deep blacks. Luminous grain.",
        photo = "silverscreencarousel_midnight-stage.jpg",
        colors = listOf(Color(0xFF252728), Color(0xFF7E8383), Color(0xFFE6E7E3)),
    ),
    SilverScreenCarouselProfile(
        id = "salt",
        code = "SA",
        name = "Salt air",
        description = "Cool blues. An honest portrait.",
        photo = "silverscreencarousel_salt-air.jpg",
        colors = listOf(Color(0xFF343F65), Color(0xFF8294A8), Color(0xFFE9E3D6)),
    ),
)
