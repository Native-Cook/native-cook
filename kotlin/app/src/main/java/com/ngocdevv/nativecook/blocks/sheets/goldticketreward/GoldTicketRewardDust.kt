package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.graphics.Bitmap
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.B
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.Box
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.T

internal data class GoldTicketRewardDust(val x0: Double,
    val y0: Double,
    val c1x: Double,
    val c1y: Double,
    val c2x: Double,
    val c2y: Double,
    val tx: Double,
    val ty: Double,
    val rel: Double,
    val arr: Double,
    val back0: Double,
    val size: Double,
    val warm: Double) {
    companion object {

        private fun hash(n: Double): Double {
            val s = sin(n * 12.9898) * 43758.5453
            return s - floor(s)
        }

        fun make(cars: Bitmap, box: Box): List<GoldTicketRewardDust> {
            val w = cars.width
            val h = cars.height
            val px = IntArray(w * h)
            cars.getPixels(px, 0, w, 0, 0, w, h)
            val spots = mutableListOf < Pair<Double, Double>  >()
            for (y in 4 until h - 4 step 9) for (x in 4 until w - 4 step 9) if ((px[y * w + x] ushr 24) > 200) spots.add(x.toDouble() / w to y.toDouble() / h)
            if (spots.isEmpty()) repeat(400) {
                i -> spots.add(hash(i + 0.5) to 0.3 + 0.6 * hash(i + 7.7))
            }
            return List(150) {
                i ->
                val s = spots[floor(hash(i + 1.23) * spots.size).toInt()%spots.size]
                val x0 = box.x + s.first * box.w
                val y0 = box.y + s.second * box.h
                val front = ((1 - s.second) * 0.62 + 0.19 + 0.08) / 1.16
                val rel = T.carsOut[0] + front.coerceIn(0.0, 1.0) * (T.carsOut[1] - T.carsOut[0]) + hash(i + 3.1) * 0.05
                val u = hash(i + 4.4)
                val v = hash(i + 5.5)
                val tx = B.ticket.x + 14 + u * (B.ticket.w - 28)
                val ty = B.ticket.y + 10 + v * (B.ticket.h - 20)
                val reach = (u + (v - 0.5) * 0.35 + 0.4) / 1.8
                val arr = max(rel + 0.34, T.gold[0] + reach * (T.gold[1] - T.gold[0]) + 0.02)
                GoldTicketRewardDust(x0,
                    y0,
                    x0 + (hash(i + 6.6) - 0.5) * 60,
                    y0 - 34 - hash(i + 7.7) * 56,
                    tx + (hash(i + 8.8) - 0.5) * 90,
                    ty - 80 - hash(i + 9.9) * 70,
                    tx,
                    ty,
                    rel,
                    arr,
                    T.goldOut[0] + (1 - u) * 0.3 + hash(i + 2.2) * 0.06,
                    1.1 + hash(i + 11.1) * 1.4,
                    hash(i + 12.2))
            }
        }
    }
}
