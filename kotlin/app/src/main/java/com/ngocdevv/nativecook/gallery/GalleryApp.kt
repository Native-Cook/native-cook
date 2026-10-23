package com.ngocdevv.nativecook.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The gallery, or the block that is open. A block has the whole screen with nothing of the gallery
 * over it, so a recording shows only the block; the system's back gesture or button leaves it.
 */
@Composable
fun GalleryApp(initialSlug: String? = null) {
    var openSlug by rememberSaveable { mutableStateOf(initialSlug) }
    val open = blockRegistry.firstOrNull { it.slug == openSlug }

    MaterialTheme(colorScheme = darkColorScheme()) {
        AnimatedContent(
            targetState = open,
            contentKey = { it?.slug },
            transitionSpec = {
                // The block slides over the gallery, and off it again on the way back.
                if (targetState != null) {
                    (slideInHorizontally { it } togetherWith fadeOut()).apply { targetContentZIndex = 1f }
                } else {
                    (fadeIn() togetherWith slideOutHorizontally { it }).apply { targetContentZIndex = -1f }
                }
            },
            modifier = Modifier.fillMaxSize().background(GalleryColors.Background),
            label = "screen",
        ) { block ->
            if (block == null) {
                GalleryScreen(onOpen = { openSlug = it.slug })
            } else {
                BackHandler { openSlug = null }
                Box(modifier = Modifier.fillMaxSize().background(GalleryColors.Background)) {
                    block.demo()
                }
            }
        }
    }
}
