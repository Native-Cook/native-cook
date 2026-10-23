package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.graphics.Bitmap
import kotlin.math.*

/** Only the serial cache worker owns this original-tuple permutation scratch. */
internal class LumaRunStudioSorter(private val source:Bitmap) {
    private val pixels=IntArray(288*330).also {source.getPixels(it,0,288,0,0,288,330)}
    private val y=DoubleArray(pixels.size)
    private val mapping=IntArray(pixels.size)
    private val run=IntArray(330);private val order=IntArray(330);private val rank=IntArray(330);private val temp=IntArray(330)
    init {val lut=DoubleArray(256){val c=it/255.0;if(c<=.04045)c/12.92 else ((c+.055)/1.055).pow(2.4)};for(i in pixels.indices){val p=pixels[i];y[i]=.2126*lut[(p ushr 16)and 255]+.7152*lut[(p ushr 8)and 255]+.0722*lut[p and 255]}}
    private fun stable(count:Int,compare:(Int,Int)->Int) {
        var width=1
        while(width<count){var start=0;while(start<count){val middle=min(count,start+width);val end=min(count,start+2*width);var a=start;var b=middle;var d=start
            while(a<middle||b<end){temp[d++]=if(b>=end||(a<middle&&compare(order[a],order[b])<=0))order[a++] else order[b++]}
            for(i in start until end)order[i]=temp[i];start=end};width*=2}
    }
    fun render(p:LumaRunStudioParams,cancelled:()->Boolean):Bitmap? {
        if(p.q==0.0)return if(cancelled())null else source
        for(i in mapping.indices)mapping[i]=i
        var count=0
        fun flush(){if(count>=6){for(i in 0 until count)order[i]=i
            stable(count){a,b->val ia=run[a];val ib=run[b];val c=y[ia].compareTo(y[ib]);if(c==0)ia.compareTo(ib)else c}
            if(p.q!=1.0){for(i in 0 until count)rank[order[i]]=i;for(i in 0 until count)order[i]=i
                stable(count){a,b->val c=((1-p.q)*a+p.q*rank[a]).compareTo((1-p.q)*b+p.q*rank[b]);if(c==0)run[a].compareTo(run[b])else c}}
            for(i in 0 until count)mapping[run[i]]=run[order[i]]};count=0}
        fun visit(x:Int,yy:Int){val i=yy*288+x;val sx=29+(x+.5)*335/288;val sy=136+(yy+.5)*384/330
            if(sx>=59+p.roiX&&sx<=334+p.roiX&&sy>=178+p.roiY&&sy<=478+p.roiY&&y[i]>=p.low&&y[i]<=p.high)run[count++]=i else flush()}
        val angle=p.angle.coerceIn(-90.0,90.0);val theta=angle*PI/180
        if(abs(angle)<=45){val m=when(angle){45.0->1.0;-45.0->-1.0;else->tan(theta)};val shifts=IntArray(288){floor(m*it+.5).toInt()};val last=shifts.last()
            for(line in -max(0,last)..329-min(0,last)){for(x in 0..287){val yy=line+shifts[x];if(yy in 0..329)visit(x,yy)};flush();if(cancelled())return null}}
        else {val n=if(abs(angle)==90.0)0.0 else 1/tan(theta);val shifts=IntArray(330){floor(n*it+.5).toInt()};val last=shifts.last()
            for(line in -max(0,last)..287-min(0,last)){val rows=if(angle>0)0..329 else 329 downTo 0;for(yy in rows){val x=line+shifts[yy];if(x in 0..287)visit(x,yy)};flush();if(cancelled())return null}}
        if(cancelled())return null
        val output=IntArray(pixels.size){pixels[mapping[it]]}
        return Bitmap.createBitmap(output,288,330,Bitmap.Config.ARGB_8888)
    }
}
