// Decode original 4x artwork once; all renderers share the same immutable bitmaps and fonts.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R

internal class HorizonAssets(private val context: Context) {
    private fun bitmap(name: String): Bitmap = context.assets.open("horizonupload/$name.png").use {
        requireNotNull(BitmapFactory.decodeStream(it)) { "Horizon Upload: missing $name" }
    }
    val sheet = bitmap("file-sheet")
    val badge = bitmap("file-badge")
    val badgeDone = bitmap("file-badge-done")
    val status = arrayOf(bitmap("status-idle"), bitmap("status-spinner"), bitmap("status-paused"), bitmap("status-done"), bitmap("status-canceled"))
    val close = bitmap("glyph-close")
    val retry = bitmap("glyph-retry")
    val medium = requireNotNull(ResourcesCompat.getFont(context, R.font.horizon_upload_manrope_medium))
}
