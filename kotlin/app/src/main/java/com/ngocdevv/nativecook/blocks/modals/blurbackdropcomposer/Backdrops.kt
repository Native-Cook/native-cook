package com.ngocdevv.nativecook.blocks.modals.blurbackdropcomposer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

private fun photo(id: String, w: Int, h: Int) =
    "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"

/** The photos behind the composer, one per opening: a neon street at night, a lagoon, peaks above the clouds. */
internal val Backdrops = listOf(
    photo("photo-1540959733332-eab4deabeeaf", 600, 1300),
    photo("photo-1573843981267-be1999ff37cd", 600, 1300),
    photo("photo-1506905925346-21bda4d32df4", 600, 1300),
)

/** Photos of the page behind: the welcome card, the collage and the avatar. */
internal val HeroPhoto = photo("photo-1502602898657-3e91760cbb34", 900, 440)
internal val CollagePhotos = listOf(
    photo("photo-1570077188670-e3a8d69ac5ff", 360, 280),
    photo("photo-1641128324972-af3212f0f6bd", 360, 280),
    photo("photo-1524492412937-b28074a5d7da", 360, 280),
)
internal val AvatarPhoto = photo("photo-1544005313-94ddf0286df2", 120, 120)

// The React Native image blurs 28 photo pixels; the photo is drawn about 0.67 dp a pixel.
private const val BLUR = 19

// Without a blur (below Android 12) the photo is veiled instead, so the cards still read on it.
private val Veil = Color(0xFF20323A).copy(alpha = 0.55f)

/** A photo that fills its box, cropped, with nothing drawn until it has loaded. */
@Composable
internal fun Photo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/**
 * The photo behind the composer, blurred as the material would blur it. All three stay composed
 * and only the current one shows, so a new opening never waits for its photo.
 */
@Composable
internal fun Backdrop(scene: Int, modifier: Modifier = Modifier) {
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.then(if (blurs) Modifier.blur(BLUR.dp, BlurredEdgeTreatment.Rectangle) else Modifier)) {
        Backdrops.forEachIndexed { s, url ->
            Photo(url, Modifier.fillMaxSize().graphicsLayer { alpha = if (s == scene) 1f else 0f })
        }
        if (!blurs) Box(Modifier.fillMaxSize().background(Veil))
    }
}
