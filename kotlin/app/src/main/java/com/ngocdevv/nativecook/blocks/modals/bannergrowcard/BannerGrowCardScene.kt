package com.ngocdevv.nativecook.blocks.modals.bannergrowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

internal const val SCENE_WIDTH = 252f
internal const val SCENE_HEIGHT = 380f

/** Peaks above a sea of cloud at first light: the calm the practice promises. */
private const val PHOTO = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=600&h=820&q=80"

private val Warmth = Brush.verticalGradient(
    0f to Color(232, 140, 62).copy(alpha = 0.34f),
    0.45f to Color(178, 96, 40).copy(alpha = 0.18f),
    1f to Color(36, 16, 6).copy(alpha = 0.78f),
)

/** The photo of the card, warmed to the app's amber and darkened at the foot for its caption. */
@Composable
internal fun Scene(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context) { ImageRequest.Builder(context).data(PHOTO).build() }
    Box(modifier.size(SCENE_WIDTH.dp, SCENE_HEIGHT.dp).background(Color(0xFFB26B3A))) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Warmth))
    }
}
