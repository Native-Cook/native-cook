package com.ngocdevv.nativecook.blocks.animations.particlesigninring

import android.view.ContextThemeWrapper
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ngocdevv.nativecook.R

/**
 * The original app's content view, `SignInScreen`, hosted as it is. What its Activity did around
 * the view is done here: the theme, the light system bars, pausing with the lifecycle, and keeping
 * the playhead and the speed across a saved state.
 */
@Composable
fun ParticleSignInRingDemo() {
    val context = LocalContext.current
    val holder = remember { SignInScreenHolder() }
    // onSaveInstanceState: "elapsedMs" and "speed", read from the view when the state is saved.
    val restored = rememberSaveable(
        saver = Saver<DoubleArray, DoubleArray>(
            save = { initial ->
                holder.screen?.let { doubleArrayOf(it.animation.elapsedMs, it.animation.playbackSpeed) } ?: initial
            },
            restore = { it },
        ),
    ) { doubleArrayOf(0.0, 1.0) }
    val screen = remember(context) {
        // The views take their defaults from the original app's light theme, not the gallery's.
        SignInScreen(ContextThemeWrapper(context, R.style.particlesigninring_AppTheme)).also {
            it.animation.elapsedMs = restored[0]
            it.selectSpeed(restored[1])
            holder.screen = it
        }
    }

    ParticleSignInRingSystemBars()

    // onResume / onPause
    LifecycleResumeEffect(screen) {
        screen.animation.resumeAnimation()
        onPauseOrDispose { screen.animation.pauseAnimation() }
    }

    AndroidView(factory = { screen }, modifier = Modifier.fillMaxSize())
}

private class SignInScreenHolder {
    var screen: SignInScreen? = null
}

/**
 * The screen is white, so the original activity asked for dark icons on both system bars. The
 * gallery's activity uses light icons, so they are switched while the block is open and put back
 * when it closes. The rest of the original window setup (drawing under both bars, no navigation
 * bar contrast scrim) is what the gallery's edge-to-edge activity already does.
 */
@Composable
private fun ParticleSignInRingSystemBars() {
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
