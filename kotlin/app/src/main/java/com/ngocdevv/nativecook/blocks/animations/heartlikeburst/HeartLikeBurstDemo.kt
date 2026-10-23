package com.ngocdevv.nativecook.blocks.animations.heartlikeburst

import android.graphics.Color
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.R
import kotlin.math.roundToInt

/**
 * The original app's content view, built as its Activity built it: a black root with the 58 dp
 * heart in the centre of the whole screen, under both system bars.
 *
 * The original window setup (edge to edge, light system bar icons, no contrast scrims) is what the
 * gallery's activity already uses, so the block changes nothing on the window. Liking vibrates,
 * which needs the `VIBRATE` permission in the manifest.
 */
@Composable
fun HeartLikeBurstDemo() {
    // onSaveInstanceState: "liked"
    var liked by rememberSaveable { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            // The view takes its defaults from the original app's theme, not the gallery's.
            val themed = ContextThemeWrapper(context, R.style.heartlikeburst_Theme_XHeart)
            val root = FrameLayout(themed).apply { setBackgroundColor(Color.BLACK) }
            val heart = XHeartAnimationView(
                context = themed,
                initialLiked = liked,
                onToggle = { value ->
                    liked = value
                    Log.d("XHeartAnimation", "Liked: $value")
                },
            )
            val size = (HeartAnimationMath.CANVAS_SIZE * themed.resources.displayMetrics.density).roundToInt()
            root.addView(heart, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            root
        },
        modifier = Modifier.fillMaxSize(),
    )
}
