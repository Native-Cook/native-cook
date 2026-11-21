package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

import kotlin.math.*

internal object LouvreVentThermostatUniforms {
    private val m=LouvreVentThermostatMotion
    fun rgb(h:Int)=doubleArrayOf((h shr 16 and 255)/255.0,(h shr 8 and 255)/255.0,(h and 255)/255.0)
    fun ramp(h:Double,stops:DoubleArray,values:List<DoubleArray>):DoubleArray {
        if(h<=stops[0])return values[0]
        for(i in 1..<stops.size) if(h<=stops[i]) { val k=(h-stops[i-1])/(stops[i]-stops[i-1]); return DoubleArray(values[i].size){ m.mix(values[i-1][it],values[i][it],k) } }
        return values.last()
    }
    fun colors(h:Double,stops:DoubleArray,hex:IntArray)=ramp(h,stops,hex.map(::rgb))
    fun scalar(h:Double,stops:DoubleArray,values:DoubleArray)=ramp(h,stops,values.map { doubleArrayOf(it) })[0]
    fun values(s:LouvreVentThermostatMotion.Scene,reduced:Boolean):Map<String,FloatArray> {
        val h=s.heat; val hx=doubleArrayOf(16.0,20.0,24.0,28.0,32.0); val gx=doubleArrayOf(17.5,24.0,30.0)
        val heat=colors(h,hx,intArrayOf(0x3E5568,0x6A2A1C,0xC2410C,0xEB7F02,0xFFB238))
        val intensity=scalar(h,hx,doubleArrayOf(.35,.5,.72,.92,1.05))
        return mapOf(
            "uP3" to doubleArrayOf(0.0),"uFrame" to doubleArrayOf(78.0,246.0,246.0,484.0),"uVent" to doubleArrayOf(86.0,254.0,230.0,468.0),
            "uLineY" to doubleArrayOf(488.0),"uS0" to doubleArrayOf(s.s0),"uK0" to doubleArrayOf(s.k0),"uBl" to s.blades,
            "uHeat" to heat,"uI" to doubleArrayOf(intensity*(1+.25*abs(tanh(s.velocity/260)))),"uElem" to doubleArrayOf(m.smooth(19.5,27.0,h)),"uCore" to doubleArrayOf(m.smooth(25.0,30.0,h)),
            "uBarOff" to doubleArrayOf(.55*(s.temperature-24)*41.6),"uPh" to doubleArrayOf(2*PI*s.time/9),"uPilot" to doubleArrayOf(.2+.15*(.5+.5*sin(2*PI*s.time/1.5))),
            "uEdgeL" to colors(h,gx,intArrayOf(0x0B1218,0x2A0305,0x420709)),"uEdgeR" to colors(h,gx,intArrayOf(0x0B1218,0x2F0204,0x4A080A)),
            "uGlowH" to doubleArrayOf(scalar(h,doubleArrayOf(16.0,17.5,24.0,30.0,32.0),doubleArrayOf(110.0,120.0,234.0,380.0,420.0))+s.breath),
            "uG0" to colors(h,gx,intArrayOf(0x2E404C,0xEB7F02,0xFFB238)),"uG1" to colors(h,gx,intArrayOf(0x24323D,0x69230F,0xEB7F02)),"uG2" to colors(h,gx,intArrayOf(0x121A20,0x2B0C01,0x69230F)),
            "uGA" to doubleArrayOf(scalar(h,gx,doubleArrayOf(.6,1.0,1.0))),"uHalo" to heat.map { it*.22*intensity }.toDoubleArray(),"uConv" to doubleArrayOf(s.conv-floor(s.conv)),
            "uDot" to colors(h,gx,intArrayOf(0x27404F,0x3A1418,0x7A2A12)),"uDotA" to doubleArrayOf(scalar(h,gx,doubleArrayOf(.6,1.0,1.15))),"uDotAmp" to doubleArrayOf(if(reduced)0.0 else scalar(h,gx,doubleArrayOf(.6,.5,1.0)))
        ).mapValues { (_,v)->v.map(Double::toFloat).toFloatArray() }
    }
    val PAGE_FIELDS=listOf("uP3","uEdgeL","uEdgeR","uGlowH","uG0","uG1","uG2","uGA","uHalo","uFrame","uConv","uDot","uDotA","uDotAmp")
    val VENT_FIELDS=listOf("uP3","uFrame","uVent","uLineY","uS0","uK0","uBl","uHeat","uI","uElem","uCore","uBarOff","uPh","uPilot")
}
