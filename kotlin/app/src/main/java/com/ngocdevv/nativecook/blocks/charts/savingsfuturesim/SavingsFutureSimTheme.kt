package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import kotlin.math.floor
import kotlin.math.round

internal object SavingsFutureSimTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0/736.0
    fun rateFrac(rate: Double) = 0.1+0.125*(rate-4)
    fun rateText(rate: Double): String {
        val r = round(rate*100)/100
        val whole = floor(r)
        val decimal = round((r-whole)*100).toInt()
        return "${whole.toInt()},${if (decimal < 10) "0" else ""}$decimal"
    }
}
