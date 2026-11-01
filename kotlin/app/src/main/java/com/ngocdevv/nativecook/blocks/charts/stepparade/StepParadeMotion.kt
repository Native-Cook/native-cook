package com.ngocdevv.nativecook.blocks.charts.stepparade

import kotlin.math.*

internal data class StepParadeFriend(val name: String, val total: Double, val coat: Int, val hat: Int, val skin: Int, val peaks: List<Triple<Double,Double,Double>>)

internal object StepParadeMotion {
    const val LOOP = 9.0
    const val NOW = 21.5
    val friends = listOf(
        StepParadeFriend("Leo", 11240.0, 0x3D6BFF, 0xFF5A4E, 0xF2C3A0, listOf(Triple(7.0,1.0,0.6),Triple(18.0,0.6,1.2))),
        StepParadeFriend("You", 9560.0, 0xFF7A45, 0xFFD23F, 0xE8B48E, listOf(Triple(8.5,0.5,0.8),Triple(12.5,0.7,0.7),Triple(19.0,0.8,1.0))),
        StepParadeFriend("Ana", 8310.0, 0xFFC83D, 0x3D6BFF, 0xC98E66, listOf(Triple(10.0,0.8,1.5),Triple(16.0,0.6,1.0))),
        StepParadeFriend("Kofi", 7120.0, 0x22B573, 0x1F2A44, 0x8D5A3B, listOf(Triple(13.0,1.0,2.5))),
        StepParadeFriend("Juno", 5480.0, 0xA764FF, 0xFF8FC8, 0xF5D1B5, listOf(Triple(17.5,1.0,1.0),Triple(20.5,0.6,0.7))),
        StepParadeFriend("Sam", 3940.0, 0x2A3142, 0xFF9F2E, 0xE3A77F, listOf(Triple(9.0,0.4,2.0),Triple(15.0,0.6,2.0))),
    )
    val curves = friends.map { f ->
        val rate = (0..64).map { i ->
            val h = 6.0+16.0*i/64.0
            f.peaks.fold(0.04) { r,p -> r+p.second*exp(-(h-p.first).pow(2)/(2*p.third*p.third)) }
        }
        val cum = DoubleArray(65)
        for (i in 1..64) cum[i] = cum[i-1]+(rate[i-1]+rate[i])/2
        cum.map { it/cum[64]*f.total }
    }
    fun clamp(x: Double) = x.coerceIn(0.0,1.0)
    fun mix(a: Double,b: Double,p: Double) = a+(b-a)*p
    fun smoother(v: Double): Double { val x = clamp(v); return x*x*x*(x*(x*6-15)+10) }
    fun hour(time: Double): Double {
        val t = time%LOOP
        return if (t<6.5) 6+15.5*smoother(t/6.5) else if(t<7.8) NOW else NOW-15.5*smoother((t-7.8)/1.2)
    }
    fun steps(i: Int,h: Double): Double {
        val u = clamp((h-6)/16)*64; val k = floor(u).toInt()
        return if(k>=64) curves[i][64] else mix(curves[i][k],curves[i][k+1],u-k)
    }
    data class Place(val x: Double,val y: Double,val s: Double)
    fun place(w: Double,top: Double,i: Int,steps: Double) = Place(40+i*7+steps/12000*(w-116),top+268+126+i*252*0.086,0.86+i*0.09)
    fun number(v: Double): String = String.format(java.util.Locale.US,"%,d",floor(v+0.5).toInt())
    fun time(h: Double): String {
        val m = floor(h*4).toInt()*15; val hh=m/60
        return "${if(hh%12==0)12 else hh%12}:${(m%60).toString().padStart(2,'0')} ${if(hh>=12)"PM" else "AM"}"
    }
    fun rank(h: Double): String {
        val mine=steps(1,h); var rank=1; var lead=0.0; var leader=1
        for(i in 0..5) { val v=steps(i,h); if(i!=1 && v>mine)rank++; if(v>lead) { lead=v; leader=i } }
        val ord=when(rank) { 1->"1st";2->"2nd";3->"3rd";else->"${rank}th" }
        return if(leader==1) "$ord of 6 friends · leading" else "$ord of 6 friends · ${number(lead-mine)} behind ${friends[leader].name}"
    }
}
