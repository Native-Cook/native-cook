package com.ngocdevv.nativecook.blocks.charts.linetocandlesswitch

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.*

data class Range(val label: String, val prices: List<Double>, val start: Double, val end: Double, val hours: Boolean)
val Ranges = listOf(
    Range("1D", listOf(196.4, 196.57, 196.23, 196.13, 196.02, 196.05, 196.14, 195.89, 196.29, 196.07, 196.06, 196.06, 195.82, 195.95, 195.98, 195.76, 195.83, 195.62, 195.62, 195.69, 195.89, 196.02, 195.87, 195.91, 196.22, 196.07, 196.41, 196.4, 196.41, 196.57, 196.45, 196.78, 196.91, 197.12, 196.8, 197.12, 196.89, 196.83, 196.9, 196.63, 196.88, 196.68, 196.79, 196.56, 196.93, 196.85, 196.86, 196.55, 196.5, 196.83, 196.85, 196.73, 197.18, 197.37, 196.99, 197.33, 197.63, 197.63, 197.66, 197.97, 197.72, 198.05, 197.95, 198.1, 198.31, 198.07, 198.28, 198.43, 198.51, 198.49, 198.04, 198.18, 198.21, 198.32, 198.08, 197.86, 198.01, 197.98, 197.7, 197.96, 197.81, 197.5, 197.98, 197.77, 198.15, 198.22, 198.07, 197.99, 198.06, 198.11, 198.25, 198.61, 198.42, 198.89, 199.0, 198.87), 1744574400000.0, 1744660800000.0, true),
    Range("1W", listOf(181.48, 181.86, 182.24, 183.31, 183.73, 182.9, 182.78, 183.54, 183.51, 183.44, 183.67, 182.93, 183.93, 183.3, 183.54, 183.82, 183.74, 183.63, 183.95, 184.19, 190.53, 190.57, 191.17, 190.59, 190.61, 190.36, 190.22, 190.48, 190.07, 190.28, 190.27, 190.27, 190.07, 190.51, 190.49, 190.09, 190.33, 190.1, 189.96, 190.09, 190.44, 189.72, 189.46, 189.97, 189.81, 189.41, 189.5, 190.3, 190.07, 189.97, 190.36, 190.54, 191.16, 190.45, 191.32, 191.08, 191.04, 191.39, 192.03, 193.82, 194.03, 194.9, 195.6, 195.6, 195.61, 195.65, 195.48, 195.41, 196.03, 196.14, 196.65, 196.52, 198.74, 199.76, 199.85, 199.19, 199.8, 198.35, 198.45, 197.03, 197.47, 197.27, 198.16, 199.62, 199.21, 199.43, 199.17, 199.34, 199.16, 199.48, 198.75, 199.59, 199.08, 199.42, 199.44, 198.87), 1744056000000.0, 1744660800000.0, true),
    Range("1M", listOf(172.15, 171.22, 169.24, 168.65, 168.62, 170.49, 167.73, 167.26, 166.02, 166.86, 166.45, 165.4, 165.28, 164.5, 163.5, 163.17, 162.85, 165.46, 164.99, 166.83, 167.85, 167.27, 166.96, 168.83, 167.24, 169.89, 167.83, 168.83, 168.68, 171.66, 176.87, 180.72, 180.65, 179.21, 181.17, 178.2, 180.53, 179.69, 178.24, 178.59, 179.07, 177.88, 177.09, 176.42, 178.55, 178.44, 176.55, 177.11, 177.99, 176.81, 178.58, 177.55, 178.47, 180.68, 178.7, 182.32, 180.79, 181.52, 183.05, 181.95, 182.26, 183.24, 182.84, 185.02, 184.72, 182.54, 182.26, 181.78, 183.19, 184.76, 185.41, 189.1, 189.77, 190.68, 191.71, 193.86, 193.87, 193.85, 193.38, 193.88, 193.74, 194.39, 191.36, 192.69, 190.92, 191.82, 191.28, 192.24, 192.53, 193.62, 196.21, 197.48, 197.31, 197.09, 196.34, 198.87), 1742068800000.0, 1744660800000.0, true),
    Range("1Y", listOf(120.3, 122.14, 123.93, 124.1, 120.3, 123.41, 123.68, 122.66, 128.93, 128.89, 128.66, 127.17, 130.55, 130.26, 130.01, 132.66, 129.05, 134.09, 135.12, 136.39, 131.79, 134.68, 133.4, 132.04, 131.27, 131.28, 128.36, 127.25, 122.4, 123.62, 125.28, 130.29, 129.17, 130.78, 134.96, 136.43, 140.21, 140.77, 142.49, 144.43, 142.48, 144.44, 146.0, 144.99, 148.98, 142.66, 144.26, 140.95, 142.6, 144.45, 144.04, 141.01, 141.08, 140.49, 141.45, 141.87, 149.04, 149.49, 152.97, 153.96, 152.38, 157.76, 157.17, 162.1, 163.57, 166.32, 165.36, 165.19, 167.42, 162.3, 164.91, 161.36, 163.17, 157.66, 161.98, 156.05, 159.47, 159.68, 164.46, 163.73, 165.81, 168.1, 169.32, 170.13, 175.45, 176.75, 179.27, 181.37, 183.97, 183.68, 188.16, 192.54, 192.07, 194.19, 198.63, 198.87), 1713124800000.0, 1744660800000.0, false),
    Range("ALL", listOf(4.12, 4.05, 5.38, 4.97, 5.18, 3.25, 3.8, 4.7, 4.29, 4.89, 3.72, 5.29, 4.77, 4.72, 3.94, 5.77, 5.27, 4.92, 4.37, 4.7, 4.7, 5.36, 6.95, 4.8, 7.07, 5.93, 6.8, 5.56, 5.28, 5.59, 5.19, 7.33, 7.16, 6.57, 5.54, 6.51, 8.35, 7.64, 6.36, 7.96, 7.66, 7.14, 9.01, 7.52, 8.1, 8.95, 7.83, 7.35, 8.97, 7.73, 10.39, 10.15, 9.12, 9.74, 10.42, 10.79, 12.92, 11.77, 11.96, 11.51, 11.87, 13.98, 14.48, 14.24, 14.41, 14.13, 15.21, 15.13, 17.65, 19.8, 19.18, 22.27, 23.11, 24.11, 23.75, 26.05, 28.0, 32.85, 37.67, 40.4, 45.21, 49.88, 54.98, 59.34, 67.58, 78.12, 86.2, 98.54, 108.83, 108.22, 106.26, 100.82, 121.53, 148.35, 173.97, 198.87), 1429300800000.0, 1744660800000.0, false),
)

data class Frame(val o: List<Double>, val h: List<Double>, val l: List<Double>, val c: List<Double>, val x: List<Double>, val w: List<Double>, val line: List<Double>, val low: Double, val high: Double, val lineAlpha: Double, val candles: Double)
data class ChartView(val label: String, val group: Int)
data class Step(val at: Double, val range: Int, val view: Int, val menu: Int)
const val LOOP = 10.0
internal object Motion {
    const val points = 96; const val hour = 3600000.0; const val day = 24 * hour; const val now = 1744660800000.0; const val nowPrice = 198.87; const val body = 0.62
    val views = listOf(ChartView("Line",0), ChartView("30m",1), ChartView("1h",2), ChartView("4h",4))
    const val pressAt = 1.7; const val sweepTo = 3.2; const val backTo = 3.8; const val liftAt = 4.0; const val pressX = 0.12; const val turnX = 0.94; const val restX = 0.55; const val heldFor = 0.2; const val userHold = 3.0; const val fade = 0.2; const val spread = 0.3; const val grow = 0.4; const val move = 0.5
    val steps = listOf(
        Step(0.0,1,0,-1),
        Step(0.8,2,0,-1),
        Step(4.3,2,0,4),
        Step(4.6,2,0,1),
        Step(4.75,2,1,-1),
        Step(6.0,2,1,4),
        Step(6.3,2,1,3),
        Step(6.45,2,3,-1),
        Step(7.3,2,3,4),
        Step(7.6,2,3,0),
        Step(7.75,2,0,-1),
        Step(8.9,1,0,-1)
    )
    fun clamp(t: Double) = t.coerceIn(0.0,1.0)
    fun ease(t: Double): Double { val u = clamp(t); return if (u < 0.5) 4 * u.pow(3) else 1 - (-2 * u + 2).pow(3) / 2 }
    fun scrubAt(t: Double): List<Double> { var x = pressX; if(t >= pressAt) x = pressX + (turnX - pressX) * ease((t - pressAt)/(sweepTo - pressAt)); if(t >= sweepTo) x = turnX + (restX - turnX) * ease((t - sweepTo)/(backTo - sweepTo)); return listOf(x,clamp((t-pressAt)/heldFor)-clamp((t-liftAt)/heldFor),if(t>=pressAt && t<liftAt) 1.0 else 0.0) }
    fun duration(a: Frame,b: Frame): Double { if(a.candles==0.0 && b.candles==0.0) return move; return if(a.lineAlpha!=b.lineAlpha) fade+spread+grow else move }
    fun lineAt(line: List<Double>,x: Double): Double { val at=clamp((x*points-0.5)/(points-1))*(points-1); val i=min(points-2,floor(at).toInt()); return line[i]+(line[i+1]-line[i])*(at-i) }
    fun frames(range: Range,seed: Int): List<Frame> {
        val p=if(range.prices.size==points) range.prices else List(points){range.prices[floor(it.toDouble()/(points-1)*(range.prices.size-1)+0.5).toInt()]}; var a=seed
        fun random(): Double { a+=0x6d2b79f5; var t=(a xor (a ushr 15))*(a or 1); t=t xor (t+(t xor (t ushr 7))*(t or 61)); return (t xor (t ushr 14)).toUInt().toDouble()/4294967296.0 }
        val reach=(1 until points).sumOf { abs(p[it]-p[it-1]) }/(points-1)*0.9; val o=mutableListOf<Double>(); val h=mutableListOf<Double>(); val l=mutableListOf<Double>()
        for(i in 0 until points){val open=if(i==0) p[i]-reach*0.5 else p[i-1];o.add(open);h.add(max(open,p[i])+random()*reach);l.add(min(open,p[i])-random()*reach)}
        val low=l.min(); val high=h.max();val slot=1.0/points
        return views.map { view ->
            val fo=mutableListOf<Double>();val fh=mutableListOf<Double>();val fl=mutableListOf<Double>();val fc=mutableListOf<Double>();val fx=mutableListOf<Double>();val fw=mutableListOf<Double>()
            for(i in 0 until points){if(view.group==0){fo.add(p[i]);fc.add(p[i]);fh.add(p[i]);fl.add(p[i]);fx.add((i+0.5)*slot);fw.add(slot*body)}else{val first=i/view.group*view.group;val last=min(points-1,first+view.group-1);fo.add(o[first]);fc.add(p[last]);fh.add(h.subList(first,last+1).max());fl.add(l.subList(first,last+1).min());fx.add(((first+last)/2.0+0.5)*slot);fw.add(slot*view.group*body)}}
            Frame(fo,fh,fl,fc,fx,fw,p,low,high,if(view.group==0)1.0 else 0.0,if(view.group==0)0.0 else 1.0)
        }
    }
    fun fixed(value: Double): String {
        val bits = abs(value).toBits().toULong(); val exponent = ((bits shr 52) and 0x7ffu).toInt() - 1023 - 52
        val scaled = ((bits and 0x000fffffffffffffu) or (1uL shl 52)) * 100u
        val cents = if (exponent >= 0) scaled shl exponent else { val shift = -exponent; if (shift >= 64) 0uL else (scaled shr shift) + if ((scaled and ((1uL shl shift) - 1u)) >= (1uL shl (shift - 1))) 1u else 0u }
        return "${cents / 100u}.${String.format(Locale.US, "%02d", (cents % 100u).toInt())}"
    }
    fun dateLabel(ms: Double, hours: Boolean): String {
        val quarter = 60000.0; val date = Instant.ofEpochMilli((floor(ms / quarter + 0.5) * quarter).toLong()).atZone(ZoneOffset.UTC)
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"); val day = "${names[date.monthValue - 1]} ${date.dayOfMonth}"
        if (!hours) return "$day, ${date.year}"
        return "$day at ${if (date.hour % 12 == 0) 12 else date.hour % 12}:${String.format(Locale.US, "%02d", date.minute)} ${if (date.hour < 12) "AM" else "PM"}"
    }
}
