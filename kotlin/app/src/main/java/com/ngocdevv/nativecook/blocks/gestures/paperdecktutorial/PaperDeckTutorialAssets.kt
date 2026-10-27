package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Typeface

/**
 * The bundled files of the block, `assets/paperdecktutorial/paperdecktutorial_<name>`: the four flat
 * covers (720 by 1080) and the fonts. The covers are decoded once at their own size in sRGB, with
 * mipmaps so they stay smooth while the deck shrinks them.
 */
internal class PaperDeckTutorialAssets(context: Context) {
    /** `PaperDeckSerif-Semibold`: Source Serif 4 Semibold, the card type. */
    val serif: Typeface = font(context, "SourceSerif4-Semibold.ttf")

    /** `PaperDeckInter-Bold`: the header's title. */
    val interBold: Typeface = font(context, "Inter-Bold.ttf")

    /** `PaperDeckInter-Regular`: the header's count. */
    val interRegular: Typeface = font(context, "Inter-Regular.ttf")

    /** The Ionicons font, whose `settings-sharp` and `lock-closed` glyphs are the gear and the lock. */
    val icons: Typeface = font(context, "Ionicons.ttf")

    /** `COVER`: the puzzled character on violet. */
    val thinking: Bitmap = image(context, "cover-thinking.webp")

    /** `CHOICE`: the burger-or-apple card. */
    val choice: Bitmap = image(context, "choice-food.webp")

    /** `SUNNY`. */
    val sunny: Bitmap = image(context, "cover-sunny.webp")

    /** `NIGHT`. */
    val night: Bitmap = image(context, "cover-night.webp")

    private fun font(context: Context, name: String): Typeface =
        Typeface.createFromAsset(context.assets, "paperdecktutorial/paperdecktutorial_$name")

    private fun image(context: Context, name: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        }
        val bitmap = context.assets.open("paperdecktutorial/paperdecktutorial_$name").use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Cannot decode the bundled cover $name" }
        }
        bitmap.setHasMipMap(true)
        bitmap.prepareToDraw()
        return bitmap
    }
}
