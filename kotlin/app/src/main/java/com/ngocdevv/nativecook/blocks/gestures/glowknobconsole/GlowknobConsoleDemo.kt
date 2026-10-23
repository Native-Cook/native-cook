package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.android.filament.Filament
import com.google.android.filament.utils.Utils
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console.Showcase

/**
 * The block on the whole screen: the console on its white glass stage, with dark icons on both
 * system bars. What the original app's activity did around `Showcase` is done here.
 */
@Composable
fun GlowknobConsoleDemo() {
    GlowknobConsoleSystemBars()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // The lifted object's shadow and the floor's reflection are RenderEffect blurs, which
        // Android has from 12 (API 31): the original app's minSdk.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Filament's native libraries, loaded before the console composes.
            val filament = remember {
                Filament.init()
                Utils.init()
                true
            }
            // The UI-thread animation runtime (Reanimated's role), one per window.
            LaunchedEffect(Unit) { AnimationEngine.run() }
            if (filament) Showcase()
        } else {
            val vietnamese = LocalConfiguration.current.locales[0].language == "vi"
            BasicText(
                text = if (vietnamese) "Block này cần Android 12 trở lên." else "This block needs Android 12 or newer.",
                style = TextStyle(color = Color(0xFF737373), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * The page is white, so the original activity asked for dark icons on both system bars. The
 * gallery's activity uses light icons, so they are switched while the block is open and put back
 * when it closes.
 */
@Composable
private fun GlowknobConsoleSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

@Preview
@Composable
private fun GlowknobConsoleDemoPreview() {
    GlowknobConsoleDemo()
}
