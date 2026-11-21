package com.ngocdevv.nativecook.blocks.pickers.wavefieldpad

import kotlin.math.*

/** Design-space values shared by shader, Canvas fallback and source-level checks. */
internal object WaveFieldPadRenderModel {
    data class Values(val puckX:Double,val puckY:Double,val dentX:Double,val dentY:Double,val modeN:Double,val modeM:Double,val light:Double,val ambX:Double,val ambY:Double,val px:Double,val adv:Double)
    fun smooth(a:Double,b:Double,x:Double):Double { val k=((x-a)/(b-a)).coerceIn(0.0,1.0);return k*k*(3-2*k) }
    fun values(f:WaveFieldPadMotion.Frame,pixel:Double,reduced:Boolean):Values {
        val n=1+3*f.puck.u.coerceIn(0.0,1.0)
        return Values(44+266*f.puck.u,44+266*(1-f.puck.v),44+266*f.dent.u,44+266*(1-f.dent.v),n,n+1+2*f.puck.v.coerceIn(0.0,1.0),.85+.15*((f.halo-.38)/.17).coerceIn(0.0,1.0),-20+442*smooth(.42,.8,f.dent.u),356+44+266*(1-f.dent.v),pixel,if(reduced)0.0 else 26.0)
    }
    fun waveY(f:WaveFieldPadMotion.Frame,k:Int,x:Double):Double {
        val u=f.dent.u.coerceIn(0.0,1.0);val v=f.dent.v.coerceIn(0.0,1.0)
        val amp=(if(v>=.5)14+78.6*(v-.5) else 14+23*(v-.5))*(1-.1*abs(k))
        val spread0=if(u>=.5)6+26.7*(u-.5) else 6+14.3*(u-.5)
        val spread=(spread0+(3-spread0)*f.settle)*(1-f.lock)
        val taper=smooth(.105,.205,abs(x-.5));val standing=cos(2*PI*f.beat);val cycles=2.6+.35*f.hz
        val travel=sin(PI*x).coerceAtLeast(0.0).pow(1.3)*taper*(.78+.22*standing)*sin(2*PI*(cycles*x+k*spread/90-.5*f.beat))
        val stand=taper*standing*sin(PI*round(1+3*f.puck.u.coerceIn(0.0,1.0))*x)
        return 180+amp*(travel+(stand-travel)*f.lock)
    }
    fun hzRoll(hz:Double):Double {val h=hz.coerceIn(1.0,4.0);val b=floor(h);return min(b+smooth(.3,.7,h-b),4.0)}
}
