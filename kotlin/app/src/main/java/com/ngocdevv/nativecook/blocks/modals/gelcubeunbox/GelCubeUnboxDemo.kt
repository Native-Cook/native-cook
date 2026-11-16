package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The RN demo: the reveal playing by itself with its close cross, which goes back. */
@Composable
fun GelCubeUnboxDemo() {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    GelCubeUnbox(autoPlay = true, onClose = { back?.onBackPressed() })
}

@Preview
@Composable
private fun GelCubeUnboxDemoPreview() {
    GelCubeUnboxDemo()
}
