package com.ngocdevv.nativecook.blocks.pickers.velvetnapstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal class VelvetNapStudioResources private constructor(context: Context) {
    val atlas: Bitmap=decode(context,"atlas.png")
    val shadow: Bitmap=decode(context,"shadow.png")
    val pathBytes: ByteArray=context.assets.open("velvetnapstudio/velvetnapstudio_path.bin").use{it.readBytes()}
    val pathFloats: FloatArray=FloatArray(16384).also {ByteBuffer.wrap(pathBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)}
    // Each grid cell uses four consecutive RGBA pixels, one exact IEEE754 Float32 per pixel.
    val pathBuffer: Bitmap=rawBitmap(256,64,pathBytes)
    val contrastAtlas: Bitmap?=if(Build.VERSION.SDK_INT<33) makeContrast(atlas) else null
    init {require(pathBytes.size==65536);VelvetNapStudioMath.grid}
    private fun decode(context: Context,name: String): Bitmap = context.assets.open("velvetnapstudio/velvetnapstudio_$name").use {
        BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})!!
    }
    private fun makeContrast(source: Bitmap): Bitmap {
        val w=source.width;val h=source.height;val pixels=IntArray(w*h);source.getPixels(pixels,0,w,0,0,w,h);val out=pixels.copyOf()
        fun sample(x: Double,y: Double): DoubleArray {
            val sx=x.coerceIn(0.0,w-1.0);val sy=y.coerceIn(0.0,h-1.0);val ix=floor(sx).toInt();val iy=floor(sy).toInt();val fx=sx-ix;val fy=sy-iy
            val result=DoubleArray(4)
            for(k in 0..3) {val xx=min(w-1,ix+(k%2));val yy=min(h-1,iy+(k/2));val c=pixels[yy*w+xx];val weight=(if(k%2==0)1-fx else fx)*(if(k/2==0)1-fy else fy)
                result[0]+=((c shr 16)and 255)*weight;result[1]+=((c shr 8)and 255)*weight;result[2]+=(c and 255)*weight;result[3]+=(c ushr 24)*weight
            };return result
        }
        for(y in 0 until h) for(x in 0 until w) {
            val i=y*w+x;val c=pixels[i];if(c ushr 24==0) continue
            val px=(x+.5)/2;val py=(y+.5)/2;val theta=.6+.52*sin(.043*px+.019*py)+.35*sin(.028*py-.016*px)
            val dx=-sin(theta)*1.2;val dy=cos(theta)*1.2;val a=sample(x-dx,y-dy);val b=sample(x+dx,y+dy)
            var color=c and -0x1000000
            for(ch in 0..2) {val shift=16-8*ch;val base=((c shr shift)and 255).toDouble();val n0=if(a[3]>.95*255)a[ch] else base;val n1=if(b[3]>.95*255)b[ch] else base
                color=color or ((base+(base-(n0+n1)*.5)*.12).roundToInt().coerceIn(0,255) shl shift)
            };out[i]=color
        }
        return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888)
    }
    companion object {
        @Volatile private var shared: VelvetNapStudioResources?=null
        fun load(context: Context): VelvetNapStudioResources=shared?:synchronized(this){shared?:VelvetNapStudioResources(context.applicationContext).also{shared=it}}
        /** Raw data: unassociated alpha and no color-space interpretation in setInputBuffer. */
        fun rawBitmap(width: Int,height: Int,bytes: ByteArray): Bitmap {
            val pixels=IntArray(width*height) {i->((bytes[i*4+3].toInt()and 255)shl 24) or ((bytes[i*4].toInt()and 255)shl 16) or ((bytes[i*4+1].toInt()and 255)shl 8) or (bytes[i*4+2].toInt()and 255)}
            return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply{setPremultiplied(false);setPixels(pixels,0,width,0,0,width,height)}
        }
    }
}
