package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Offline separable Gaussian, in point-sized snapshots, with the same clamp boundary as Skia. */
internal object IslandGlassOrbBlur {
    fun apply(source: Bitmap,sigma: Double): Bitmap {
        if(sigma<=0)return source.copy(Bitmap.Config.ARGB_8888,false)
        val w=source.width; val h=source.height; val input=IntArray(w*h); source.getPixels(input,0,w,0,0,w,h)
        val radius=ceil(sigma*3).toInt(); val kernel=DoubleArray(radius*2+1) { exp(-((it-radius).toDouble().pow(2))/(2*sigma*sigma)) }
        val sum=kernel.sum(); for(i in kernel.indices)kernel[i]/=sum
        val tmp=IntArray(input.size); val output=IntArray(input.size)
        for(y in 0 until h)for(x in 0 until w) {
            var a=0.0; var r=0.0; var g=0.0; var b=0.0
            for(i in kernel.indices) { val p=input[y*w+(x+i-radius).coerceIn(0,w-1)]; val k=kernel[i]; a+=Color.alpha(p)*k; r+=Color.red(p)*k; g+=Color.green(p)*k; b+=Color.blue(p)*k }
            tmp[y*w+x]=Color.argb(a.roundToInt(),r.roundToInt(),g.roundToInt(),b.roundToInt())
        }
        for(y in 0 until h)for(x in 0 until w) {
            var a=0.0; var r=0.0; var g=0.0; var b=0.0
            for(i in kernel.indices) { val p=tmp[(y+i-radius).coerceIn(0,h-1)*w+x]; val k=kernel[i]; a+=Color.alpha(p)*k; r+=Color.red(p)*k; g+=Color.green(p)*k; b+=Color.blue(p)*k }
            output[y*w+x]=Color.argb(a.roundToInt(),r.roundToInt(),g.roundToInt(),b.roundToInt())
        }
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
    }
}
