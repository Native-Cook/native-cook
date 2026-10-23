package com.ngocdevv.nativecook.blocks.forms.dialslider

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.forms.dialslider.example.ExampleScreen

/** The example app's root content: its dark `MaterialTheme` around `ExampleScreen()`. */
@Composable
fun DialSliderDemo() {
    DialSliderDemoSystemBars()
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
        ),
    ) {
        ExampleScreen()
    }
}

/**
 * The screen is black, so both system bars get light icons while the block is open. The original
 * activity called `enableEdgeToEdge()` with its default styles, where the icons follow the
 * system's light or dark mode instead (dark icons on this black screen in light mode); they are
 * pinned to the screen here. The navigation bar keeps the system's contrast scrim, as those
 * defaults do, which the gallery's activity turns off, so everything is put back when the block
 * closes.
 */
@Composable
private fun DialSliderDemoSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        val contrastEnforced = Build.VERSION.SDK_INT >= 29 && window.isNavigationBarContrastEnforced
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = contrastEnforced
        }
    }
}

@Preview
@Composable
private fun DialSliderDemoPreview() {
    DialSliderDemo()
}
