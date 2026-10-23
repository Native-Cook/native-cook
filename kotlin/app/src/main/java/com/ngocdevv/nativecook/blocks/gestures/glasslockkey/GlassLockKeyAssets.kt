package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ngocdevv.nativecook.R

/** The seven full-stage RGBA layers used by the shipped RN upgrade. */
internal class GlassLockKeyAssets(context: Context) {
    val body = decode(context, R.drawable.glasslockkey_padlock_body)
    val shackleClosed = decode(context, R.drawable.glasslockkey_shackle_closed)
    val shackleOpen = decode(context, R.drawable.glasslockkey_shackle_open)
    val keyIn = decode(context, R.drawable.glasslockkey_key_in)
    val keyInA = decode(context, R.drawable.glasslockkey_key_in_a)
    val keyFront = decode(context, R.drawable.glasslockkey_key_front)
    val shadow = decode(context, R.drawable.glasslockkey_shadow)

    private fun decode(context: Context, resource: Int): Bitmap {
        return requireNotNull(
            BitmapFactory.decodeResource(
                context.resources,
                resource,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ),
        ) { "Glass Lock Key layer $resource did not decode" }
    }
}
