package com.ngocdevv.nativecook.blocks.onboarding.watchfuleyespermission

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and draws under both bars, so it is shown without a stage. Its
 * screen is dark; an app that mounts it sets light bar icons for it.
 */
@Composable
fun WatchfulEyesPermissionDemo() {
    WatchfulEyesPermission(autoPlay = true)
}

@Preview
@Composable
private fun WatchfulEyesPermissionDemoPreview() {
    WatchfulEyesPermissionDemo()
}
