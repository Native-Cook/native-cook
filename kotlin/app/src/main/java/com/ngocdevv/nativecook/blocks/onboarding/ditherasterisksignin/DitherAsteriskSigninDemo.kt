package com.ngocdevv.nativecook.blocks.onboarding.ditherasterisksignin

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Footnote sign-in screen on the whole screen, slicing its pixel asterisk by itself. */
@Composable
fun DitherAsteriskSigninDemo() {
    DitherAsteriskSignin(autoPlay = true)
}

@Preview
@Composable
private fun DitherAsteriskSigninDemoPreview() {
    DitherAsteriskSigninDemo()
}
