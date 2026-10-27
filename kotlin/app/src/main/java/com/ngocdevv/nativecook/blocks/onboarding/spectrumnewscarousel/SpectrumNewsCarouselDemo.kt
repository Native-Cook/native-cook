package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

/**
 * The Spectrum tour on the whole screen, playing by itself (`index.tsx` of the React Native block).
 * Starting the activity with the intent extra `previewTime`
 * (`adb shell am start ... --ef previewTime 7.5`) freezes the loop at that time instead, to look at
 * one frame, like the launch argument of the Swift demo.
 */
@Composable
fun SpectrumNewsCarouselDemo() {
    val context = LocalContext.current
    val previewTime = remember(context) { spectrumNewsCarouselPreviewTime(context) }
    SpectrumNewsCarousel(autoPlay = true, previewTime = previewTime)
}

private fun spectrumNewsCarouselPreviewTime(context: android.content.Context): Double? {
    val extras = (context as? Activity)?.intent?.extras ?: return null
    return when (val value = extras.get("previewTime")) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

@Preview
@Composable
private fun SpectrumNewsCarouselDemoPreview() {
    SpectrumNewsCarouselDemo()
}
