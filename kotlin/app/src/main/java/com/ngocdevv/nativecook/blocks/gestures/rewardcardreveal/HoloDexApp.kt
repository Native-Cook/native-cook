package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.screens.CardDetailScreen
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.screens.CardGalleryScreen
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor

object Routes {
    const val GALLERY = "gallery"
    const val CARD = "card/{id}"
    fun card(id: String) = "card/$id"
}

/** Expo Router stack: `animation: 'fade'`, 240 ms, headers hidden. */
private const val ROUTE_FADE_MILLIS = 240

@Composable
fun HoloDexApp() {
    val navController = rememberNavController()
    val screenBackground = cssColor("#d69aa8")

    Box(modifier = Modifier.fillMaxSize().background(screenBackground)) {
        NavHost(
            navController = navController,
            startDestination = Routes.GALLERY,
            enterTransition = { fadeIn(tween(ROUTE_FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(ROUTE_FADE_MILLIS)) },
            popEnterTransition = { fadeIn(tween(ROUTE_FADE_MILLIS)) },
            popExitTransition = { fadeOut(tween(ROUTE_FADE_MILLIS)) },
        ) {
            composable(Routes.GALLERY) {
                CardGalleryScreen(onOpenCard = { id -> navController.navigate(Routes.card(id)) })
            }
            composable(
                route = Routes.CARD,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                CardDetailScreen(
                    cardId = entry.arguments?.getString("id"),
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.GALLERY) {
                                popUpTo(Routes.GALLERY) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
    }
}
