package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

/**
 * Quiet Signal Onboarding on the whole screen, playing by itself. Starting the activity with the
 * intent extra `previewTime` (`adb shell am start ... --ef previewTime 12.5`) freezes the loop at
 * that time instead, to look at one frame, like the launch argument of the Swift demo.
 */
@Composable
fun QuietSignalOnboardingDemo() {
    val context = LocalContext.current
    val previewTime = remember(context) { previewTimeFromIntent(context) }
    QuietSignalOnboarding(autoPlay = true, previewTime = previewTime)
}

private fun previewTimeFromIntent(context: Context): Double? {
    val extras = (context as? Activity)?.intent?.extras ?: return null
    return when (val value = extras.get("previewTime")) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

@Preview
@Composable
private fun QuietSignalOnboardingDemoPreview() {
    QuietSignalOnboardingDemo()
}
