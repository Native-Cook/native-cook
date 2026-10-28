package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eleven original PNGs, decoded once away from frame and gesture work. */
internal class PrismaticGameProfileArtwork(private val images: Map<String, Bitmap>) {
    private val composeImages=images.mapValues { it.value.asImageBitmap() }
    fun bitmap(name: String): Bitmap = images.getValue(name)
    fun image(name: String): ImageBitmap = composeImages.getValue(name)
    companion object {
        private val names=listOf("avatar","landscape","crown","shield","diamond","game","ruby","coin","stats_level","stats_games","stats_time")
        @Composable
        fun rememberArtwork(): PrismaticGameProfileArtwork? {
            val context=LocalContext.current.applicationContext
            val artwork by produceState<PrismaticGameProfileArtwork?>(null,context) {
                value=withContext(Dispatchers.IO) {
                    PrismaticGameProfileArtwork(names.associateWith { name ->
                        context.assets.open("prismaticgameprofile/prismaticgameprofile_$name.png").use { input ->
                            requireNotNull(BitmapFactory.decodeStream(input)) { "Unable to decode Prismatic artwork $name" }
                        }
                    })
                }
            }
            return artwork
        }
    }
}
