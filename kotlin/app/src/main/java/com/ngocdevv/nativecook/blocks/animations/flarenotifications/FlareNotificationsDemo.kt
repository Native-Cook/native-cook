package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.AnimationEngine

/**
 * The block on the whole screen: the reference's cool gray page, dark system bar icons, and the
 * notes laid out inside the safe area. What the original app's activity did around `FlareNotes`
 * is done here.
 */
@Composable
fun FlareNotificationsDemo() {
    FlareNotificationsSystemBars()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlareTone.page)
            // useSafeAreaInsets(): top and bottom padding only.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Vertical)),
        contentAlignment = Alignment.Center,
    ) {
        // The flame is an AGSL RuntimeShader, which Android has from 13 (API 33).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The UI-thread animation runtime (Reanimated's role), one per window.
            LaunchedEffect(Unit) { AnimationEngine.run() }
            Box(Modifier.fillMaxSize()) { FlareNotes() }
        } else {
            BasicText(
                text = stringResource(R.string.flarenotifications_unsupported),
                style = TextStyle(color = FlareTone.pageHint, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * The page is light, so the original activity asked for dark icons on both system bars. The
 * gallery's activity uses light icons, so they are switched while the block is open and put back
 * when it closes.
 */
@Composable
private fun FlareNotificationsSystemBars() {
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
private fun FlareNotificationsDemoPreview() {
    FlareNotificationsDemo()
}
