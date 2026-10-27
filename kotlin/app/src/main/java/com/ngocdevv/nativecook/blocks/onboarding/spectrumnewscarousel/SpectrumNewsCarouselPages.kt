package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A page's headline, its body and the description of its artwork (`PAGES`). */
internal class SpectrumNewsCarouselPageText(val title: String, val body: String, val art: String)

/** `PAGES`: the copy of the five pages, character for character. */
internal val SpectrumNewsCarouselPages = listOf(
    SpectrumNewsCarouselPageText(
        title = "Spectrum",
        body = "An easier way to keep up\nwith the news",
        art = "Spectrum dot emblem on a warm glowing field.",
    ),
    SpectrumNewsCarouselPageText(
        title = "Stories, not articles",
        body = "We show stories covered by\nmultiple sources, so you\nalways see varied perspectives.",
        art = "Ten fictional publication badges orbit a blue news icon.",
    ),
    SpectrumNewsCarouselPageText(
        title = "News selected for you",
        body = "Spectrum gives you powerful\ncontrol over which stories you see,\nand which you don’t.",
        art = "A person silhouette above red and green topic preferences.",
    ),
    SpectrumNewsCarouselPageText(
        title = "Understand more, faster",
        body = "Get a summary, cut straight to the\ndetails, listen on your drive, or dive\ninto the articles.",
        art = "A blue summary panel above an article, a 16-perspective pill and audio-preview icon.",
    ),
    SpectrumNewsCarouselPageText(
        title = "Ask questions",
        body = "Spectrum finds answers.\nIf you need clarification, want further\ndetails, or sourced analysis…\njust ask.",
        art = "An original waterfront-garden photograph beside a question and a short answer.",
    ),
)

/** Where the bundled photograph lives: `assets/spectrumnewscarousel/spectrumnewscarousel_<name>`. */
private const val PHOTO_ASSET = "spectrumnewscarousel/spectrumnewscarousel_waterfront-garden.webp"

/**
 * The waterfront-garden photograph (`assets/waterfront-garden.webp`, 720 by 451), decoded once at its
 * own size; the artwork's photo card and the briefing both crop it to fill (`contentFit="cover"`).
 */
internal fun loadSpectrumNewsCarouselPhoto(context: Context): ImageBitmap {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val bitmap = context.assets.open(PHOTO_ASSET).use { stream ->
        checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Cannot decode the bundled Spectrum photograph" }
    }
    return bitmap.asImageBitmap()
}
