package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/**
 * The block's artwork and its Ionicons font, loaded once off the main thread. The files are the React Native ones, copied to
 * `assets/foliofeaturelibrary/foliofeaturelibrary_<name>`; the 1254 px orbs are decoded at half size,
 * which is still above the 172 dp they are drawn at on a 3x screen.
 */
internal class FolioFeatureLibraryAssets private constructor(
    val book: ImageBitmap,
    val bookGreen: ImageBitmap,
    val lensNeutral: ImageBitmap,
    val lensGreen: ImageBitmap,
    val globe: ImageBitmap,
    val portrait: ImageBitmap,
    val recipe: ImageBitmap,
    val everything: ImageBitmap,
    val author: ImageBitmap,
    val avatars: List<ImageBitmap>,
    val icons: List<ImageBitmap>,
    val ionicons: Typeface,
) {
    fun art(art: FolioArt): ImageBitmap = when (art) {
        FolioArt.BookGreen -> bookGreen
        FolioArt.Globe -> globe
        FolioArt.Portrait -> portrait
        FolioArt.Recipe -> recipe
    }

    companion object {
        private const val MAX_SIDE = 600

        fun load(context: Context): FolioFeatureLibraryAssets = FolioFeatureLibraryAssets(
            book = decode(context, "glass-book-blue.webp"),
            bookGreen = decode(context, "orb-book-green.webp"),
            lensNeutral = decode(context, "glass-lens-neutral.webp"),
            lensGreen = decode(context, "glass-lens-green.webp"),
            globe = decode(context, "glass-globe.png"),
            portrait = decode(context, "glass-portrait.png"),
            recipe = decode(context, "orb-recipe.webp"),
            everything = decode(context, "cover-everything.webp"),
            author = decode(context, "author.webp"),
            avatars = listOf(
                decode(context, "avatar-1.webp"),
                decode(context, "avatar-2.webp"),
                decode(context, "avatar-3.webp"),
            ),
            icons = listOf(
                decode(context, "icon-1.webp"),
                decode(context, "icon-2.webp"),
                decode(context, "icon-3.webp"),
                decode(context, "icon-4.webp"),
                decode(context, "icon-5.webp"),
                decode(context, "icon-6.webp"),
                decode(context, "icon-7.webp"),
                decode(context, "icon-8.webp"),
            ),
            ionicons = Typeface.createFromAsset(context.assets, "foliofeaturelibrary/foliofeaturelibrary_Ionicons.ttf"),
        )

        private fun decode(context: Context, name: String): ImageBitmap {
            val path = "foliofeaturelibrary/foliofeaturelibrary_$name"
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            return checkNotNull(bitmap) { "Unable to decode Folio asset: $name" }.asImageBitmap()
        }
    }
}
