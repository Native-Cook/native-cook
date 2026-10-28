package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

/**
 * Habitat Specimen Carousel as the gallery shows it: the autoPlay loop of [LOOP] seconds. For review,
 * `--ef t <seconds>` on the launch intent freezes the screen at that second of the loop, and
 * `--ez reduced true` shows Reduced Motion (both as the reference's `t` and `reduced` query).
 */
@Composable
fun HabitatSpecimenCarouselDemo() {
    val intent = (LocalContext.current as? Activity)?.intent
    val frozen = intent?.takeIf { it.hasExtra("t") }?.getFloatExtra("t", 0f)
    val reduced = intent?.takeIf { it.hasExtra("reduced") }?.getBooleanExtra("reduced", false)
    HabitatSpecimenCarousel(autoPlay = true, previewTime = frozen, previewReducedMotion = reduced)
}

@Preview
@Composable
private fun HabitatSpecimenCarouselDemoPreview() {
    HabitatSpecimenCarouselDemo()
}
