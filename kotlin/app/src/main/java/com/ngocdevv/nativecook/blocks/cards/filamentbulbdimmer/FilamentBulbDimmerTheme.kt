package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import android.graphics.RectF
import kotlin.math.*

internal object FilamentBulbDimmerTheme {
    val names=arrayOf("Violet","Ice","Warm","Amber","Rose")
    val dots=intArrayOf(0xAF3AFA,0x54F7FC,0xF8BC02,0xFF963F,0xFF6B8B)
    val xs=doubleArrayOf(62.0,125.0,201.0,278.0,340.0)
    val lights=arrayOf(doubleArrayOf(.46,.2,1.0),doubleArrayOf(.34,.86,1.0),doubleArrayOf(1.0,.43,.11),doubleArrayOf(1.0,.3,.06),doubleArrayOf(1.0,.24,.34))
    val accents=arrayOf(intArrayOf(0x7A52FF,0x9F54FF,0xD884FF,0x9B62F4,0xB884FF,0xAF3AFA),intArrayOf(0x2E9BFF,0x37C6F2,0x86F2FF,0x33A9E0,0x5CD6F5,0x54F7FC),intArrayOf(0xFF8344,0xFE923A,0xFFB71C,0xEE7E5E,0xFFA13A,0xF8BC02),intArrayOf(0xFF6A2C,0xFF7F35,0xFFA445,0xEF6B44,0xFF8A40,0xFF963F),intArrayOf(0xFF4F7C,0xFF6C90,0xFFA0B8,0xEE5F82,0xFF7C9C,0xFF6B8B))
    private val lab=accents.map { row->row.map { h->
        fun dec(c:Int):Double { val v=c/255.0;return if(v<=.04045)v/12.92 else ((v+.055)/1.055).pow(2.4) }
        val r=dec(h shr 16 and 255);val g=dec(h shr 8 and 255);val b=dec(h and 255)
        val l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);val m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);val s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b)
        doubleArrayOf(.2104542553*l+.793617785*m-.0040720468*s,1.9779984951*l-2.428592205*m+.4505937099*s,.0259040371*l+.7827717662*m-.808675766*s)
    } }
    fun accentLin(sw:Double,kind:Int=1):DoubleArray {
        val v=sw.coerceIn(0.0,3.9999);val i=v.toInt();val k=v-i;val c=DoubleArray(3){lab[i][kind][it]+(lab[i+1][kind][it]-lab[i][kind][it])*k}
        val l=(c[0]+.3963377774*c[1]+.2158037573*c[2]).pow(3);val m=(c[0]-.1055613458*c[1]-.0638541728*c[2]).pow(3);val s=(c[0]-.0894841775*c[1]-1.291485548*c[2]).pow(3)
        return doubleArrayOf(4.0767416621*l-3.3077115913*m+.2309699292*s,-1.2684380046*l+2.6097574011*m-.3413193965*s,-.0041960863*l-.7034186147*m+1.707614701*s).map{max(0.0,it)}.toDoubleArray()
    }
    fun accent(sw:Double,kind:Int):Int { val c=accentLin(sw,kind).map { val v=it.coerceIn(0.0,1.0);(255*(if(v<=.0031308)v*12.92 else 1.055*v.pow(1/2.4)-.055)).roundToInt() }; return (255 shl 24) or(c[0] shl 16) or(c[1] shl 8) or c[2] }
    fun light(heat:Double,sw:Double):DoubleArray {
        val t=(900+1800*max(0.0,heat).pow(.6))/100;val g=((99.4708025861*ln(t)-161.1195681661)/255).coerceIn(0.0,1.0);val b=if(t<=19)0.0 else ((138.5177312231*ln(t-10)-305.0447927307)/255).coerceIn(0.0,1.0)
        val bb=doubleArrayOf(1.0,g.pow(2.2),b.pow(2.2));val v=sw.coerceIn(0.0,3.9999);val i=v.toInt();val k=v-i;val sm=((heat-.3)/.5).coerceIn(0.0,1.0).let { it*it*(3-2*it) }
        val c=DoubleArray(3){bb[it]+(lights[i][it]+(lights[i+1][it]-lights[i][it])*k-bb[it])*sm};val mx=max(1e-4,c.max());return c.map{it/mx}.toDoubleArray()
    }
    fun dotX(sx:Double,sw:Double):Double { val v=sw.coerceIn(0.0,3.9999);val i=v.toInt();return (xs[i]+(xs[i+1]-xs[i])*(v-i))*sx }
    data class Layout(val w:Double,val h:Double,val top:Double,val bottom:Double) {
        val sx=w/402;val k=(h-max(bottom,16.0)-14-top)/788
        fun y(p:Number)=top+(p.toDouble()-43)*k
        val card=rect(29*sx,y(121),344*sx,135*k)
        val chipL=rect(50*sx,y(313),108*sx,50*k);val chipR=rect(244*sx,y(313),108*sx,50*k)
        val tab=rect(-26*sx,y(322),92*sx,250*k)
        val r=(y(541)-y(364))/3;val anchorX=w/2;val anchorY=card.bottom.toDouble();val restY=y(364)-.5*r;val cord=restY-anchorY
        val cx=202*sx;val cy=y(724);val rOut=130*sx;val rIn=82*sx;val leg=22*k;val dotsY=y(831)
        val toggle=rect(54*sx,y(196),82*sx,38*k)
        fun arcPoint(level:Double):Pair<Double,Double> {
            val rm=(rOut+rIn)/2;val half=PI*rm;val s=level.coerceIn(0.0,1.0).pow(2.1)*(2*leg+half)
            if(s<leg)return cx-rm to cy+leg-s
            if(s>leg+half)return cx+rm to cy+s-leg-half
            val a=PI-(s-leg)/half*PI;return cx+rm*cos(a) to cy-rm*sin(a)
        }
        fun levelAt(x:Double,y:Double):Double {
            val rm=(rOut+rIn)/2;val half=PI*rm
            val s=if(y>cy) { val down=min(leg,y-cy);if(x<cx)leg-down else leg+half+down } else leg+(PI-atan2(cy-y,x-cx).coerceIn(0.0,PI))/PI*half
            return (s/(2*leg+half)).coerceIn(0.0,1.0).pow(1/2.1)
        }
        fun pose(f:FilamentBulbDimmerMotion.Frame):DoubleArray { val len=max(8.0,cord+f.s);val hx=anchorX+len*sin(f.th);val hy=anchorY+len*cos(f.th);return doubleArrayOf(hx,hy,hx+2.55*r*sin(f.th),hy+2.55*r*cos(f.th),sqrt(3*len*max(0.0,cord-len)/8)) }
        fun finger(f:FilamentBulbDimmerMotion.Frame):Pair<Double,Double> {
            if(f.fingerAt==0 || f.fingerAt==4) {val p=pose(f);val a=if(f.fingerAt==0).42 else 0.0;val d=if(f.fingerAt==0)2.95 else 3.55;return p[0]+(cos(f.th)*a+sin(f.th)*d)*r to p[1]+(-sin(f.th)*a+cos(f.th)*d)*r}
            if(f.fingerAt==1)return arcPoint(f.level)
            if(f.fingerAt==2)return dotX(sx,f.fingerDot) to dotsY
            return toggle.centerX().toDouble() to toggle.centerY().toDouble()
        }
    }
    fun rect(x:Double,y:Double,w:Double,h:Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
}
