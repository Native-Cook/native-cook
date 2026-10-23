package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ngocdevv.nativecook.R

/** Decodes the RN source layers once so gesture frames only transform already decoded pixels. */
internal class GumballCrankAssets(context: Context) {
    val room = bitmap(context, R.drawable.gumballcrank_room)
    val globeFill = bitmap(context, R.drawable.gumballcrank_globe_fill)
    val globeGlass = bitmap(context, R.drawable.gumballcrank_globe_glass)
    val knob = bitmap(context, R.drawable.gumballcrank_knob)
    val pot = bitmap(context, R.drawable.gumballcrank_pot)
    val coupon = bitmap(context, R.drawable.gumballcrank_coupon)
    val coin = bitmap(context, R.drawable.gumballcrank_coin)
    val slicePeach = bitmap(context, R.drawable.gumballcrank_slice_peach)
    val slicePassion = bitmap(context, R.drawable.gumballcrank_slice_passion)
    val peel = bitmap(context, R.drawable.gumballcrank_peel)

    private fun bitmap(context: Context, resource: Int): Bitmap {
        return requireNotNull(
            BitmapFactory.decodeResource(
                context.resources,
                resource,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ),
        )
    }
}
