package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Local poster strip and the canonical QR payload, decoded once per screen instance. */
internal class EventPassShareAssets(context: Context) {
    val portraitStripBitmap: Bitmap = decode(context, "eventpassshare_poster-strip.jpg")
    val qrBitmap: Bitmap = decode(context, "eventpassshare_qr-pass.png")
    val portraitStrip: ImageBitmap = portraitStripBitmap.asImageBitmap()
    val qrCode: ImageBitmap = qrBitmap.asImageBitmap()

    private fun decode(context: Context, name: String): Bitmap =
        context.assets.open("eventpassshare/$name").use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream)) { "Unable to decode Event Pass asset: $name" }
        }
}
