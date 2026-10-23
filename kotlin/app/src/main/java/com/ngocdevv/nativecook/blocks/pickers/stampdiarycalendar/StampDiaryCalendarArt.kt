package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The nine stamp illustrations (420 x 420 JPEG), decoded once, plus a smaller copy for the cell
 * stamps (the shader samples bilinearly, so a smaller source keeps 44 dp stamps from shimmering).
 */
internal class StampDiaryCalendarArtwork(val full: List<Bitmap>, val small: List<Bitmap>) {
    companion object {
        const val SIZE = 420
        const val SMALL_SIZE = 192
    }
}

@Composable
internal fun rememberStampDiaryCalendarArtwork(): StampDiaryCalendarArtwork {
    val context = LocalContext.current
    return remember(context) { loadStampDiaryCalendarArtwork(context) }
}

private fun loadStampDiaryCalendarArtwork(context: Context): StampDiaryCalendarArtwork {
    val full = ArrayList<Bitmap>()
    val small = ArrayList<Bitmap>()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    for (number in 1..9) {
        val filename = "stampdiarycalendar/StampDiaryCalendar-stamp-${number.toString().padStart(2, '0')}.jpg"
        val source = context.assets.open(filename).use(BitmapFactory::decodeStream)
            ?: error("Missing Stamp Diary calendar artwork: $filename")
        for ((side, list) in listOf(StampDiaryCalendarArtwork.SIZE to full, StampDiaryCalendarArtwork.SMALL_SIZE to small)) {
            val scaled = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            Canvas(scaled).drawBitmap(source, null, Rect(0, 0, side, side), paint)
            list.add(scaled)
        }
        source.recycle()
    }
    return StampDiaryCalendarArtwork(full, small)
}
