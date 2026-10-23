package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/** Draws a turning sheet and the cover's foil: with AGSL from API 33, with canvas clips below. */
internal class PageCurlRenderer(
    snapshots: List<Bitmap>,
    density: Float,
    widthDp: Float,
    heightDp: Float,
    foil: FoilRect,
) {
    // The snapshots live on the GPU: they are read every frame while a sheet turns.
    private val pages = snapshots.map { it.copy(Bitmap.Config.HARDWARE, false) ?: it }
    private val gpu = if (Build.VERSION.SDK_INT >= 33) makeGpu(pages, density, widthDp, heightDp, foil) else null
    private val fallback = if (gpu == null) PageCurlFallback(pages, density, widthDp, heightDp) else null

    /** Draws sheet [slot] in screen points scaled by the density. */
    fun drawCurl(canvas: Canvas, slot: Int, u: CurlUniforms) {
        if (gpu != null) {
            gpu.drawCurl(canvas, slot, u)
        } else {
            fallback?.drawCurl(canvas, u)
        }
    }

    /** The foil needs AGSL: below API 33 the cover simply stays as printed. */
    fun drawFoil(canvas: Canvas, u: FoilUniforms) {
        gpu?.drawFoil(canvas, u)
    }

    private companion object {
        const val TAG = "PageCurlJournal"

        /** A shader that fails to compile is logged, not silent, and the flat fold takes its place. */
        @RequiresApi(33)
        fun makeGpu(pages: List<Bitmap>, density: Float, widthDp: Float, heightDp: Float, foil: FoilRect): PageCurlGpu? {
            try {
                return PageCurlGpu(pages, density, widthDp, heightDp, foil)
            } catch (error: RuntimeException) {
                Log.e(TAG, "The AGSL curl did not compile; drawing the flat fold", error)
                return null
            }
        }
    }
}
