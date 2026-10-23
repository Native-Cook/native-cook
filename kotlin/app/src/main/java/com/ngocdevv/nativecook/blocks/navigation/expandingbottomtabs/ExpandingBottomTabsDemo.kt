package com.ngocdevv.nativecook.blocks.navigation.expandingbottomtabs

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.navigation.expandingbottomtabs.navigation.AppNavigation
import com.ngocdevv.nativecook.blocks.navigation.expandingbottomtabs.ui.theme.BottomTabExplorationTheme

/** The original app's root content: `BottomTabExplorationTheme { AppNavigation() }`. */
@Composable
fun ExpandingBottomTabsDemo() {
    ExpandingBottomTabsDemoSystemBars()
    BottomTabExplorationTheme {
        AppNavigation()
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
private fun ExpandingBottomTabsDemoSystemBars() {
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
private fun ExpandingBottomTabsDemoPreview() {
    ExpandingBottomTabsDemo()
}
