package com.ngocdevv.nativecook.blocks.gestures.trailparallaxtracker
import kotlin.math.*
internal data class TrailParallaxTrackerPalette(val source:FloatArray,val destination:FloatArray,val sky:Array<DoubleArray>,val count:Int)
internal object TrailParallaxTrackerLight {
 private fun lin(c:Double)=if(c<=.04045)c/12.92 else ((c+.055)/1.055).pow(2.4)
 private fun enc(c:Double):Double{val v=c.coerceIn(0.0,1.0);return if(v<=.0031308)v*12.92 else 1.055*v.pow(1/2.4)-.055}
 private fun rgb(h:Int)=doubleArrayOf(((h shr 16)and 255)/255.0,((h shr 8)and 255)/255.0,(h and 255)/255.0)
 private fun labLinear(c:DoubleArray):DoubleArray{val r=c[0];val g=c[1];val b=c[2];val l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);val m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);val s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);return doubleArrayOf(.2104542553*l+.793617785*m-.0040720468*s,1.9779984951*l-2.428592205*m+.4505937099*s,.0259040371*l+.7827717662*m-.808675766*s)}
 private fun lab(c:Int)=labLinear(rgb(c).map(::lin).toDoubleArray())
 private fun labRGB(c:DoubleArray):DoubleArray{val l0=c[0];val a=c[1];val b=c[2];val l=(l0+.3963377774*a+.2158037573*b).pow(3);val m=(l0-.1055613458*a-.0638541728*b).pow(3);val s=(l0-.0894841775*a-1.291485548*b).pow(3);return doubleArrayOf(4.0767416621*l-3.3077115913*m+.2309699292*s,-1.2684380046*l+2.6097574011*m-.3413193965*s,-.0041960863*l-.7034186147*m+1.707614701*s)}
 fun hour(distance:Double):Double{val k=TrailParallaxTrackerWorld.hourKeys;if(distance<=k[0][0])return k[0][1];for(i in 1 until k.size)if(distance<=k[i][0])return k[i-1][1]+(k[i][1]-k[i-1][1])*(distance-k[i-1][0])/(k[i][0]-k[i-1][0]);return k.last()[1]}
 private fun light(row:Double):DoubleArray{val i=min(floor(row).toInt(),3);val k=(row-i).coerceIn(0.0,1.0);val h=TrailParallaxTrackerWorld.hours;return DoubleArray(4){h[i][it]+(h[i+1][it]-h[i][it])*k}}
 private fun relight(value:DoubleArray,l:DoubleArray,n:DoubleArray):DoubleArray{val cs=l[3]/n[3];return labRGB(doubleArrayOf(value[0]+l[0]-n[0],value[1]*cs+l[1]-n[1],value[2]*cs+l[2]-n[2]))}
 private val fills=TrailParallaxTrackerWorld.biomes.map{b->b.fills.map(::lab)}
 private val skies=TrailParallaxTrackerWorld.biomes.map{b->b.sky.map(::lab)}
 private val blues=intArrayOf(0x1E2850,0x27335E,0x3A4675).map(::lab)
 fun palette(biome:Int,row:Double):TrailParallaxTrackerPalette{
  val b=TrailParallaxTrackerWorld.biomes[biome];val l=light(row);val n=TrailParallaxTrackerWorld.hours[b.native];val source=ArrayList<Float>();val destination=ArrayList<Float>();val sky=Array(3){DoubleArray(3)}
  for(i in 0..5)if(i<b.fills.size){source+=rgb(b.fills[i]).map{it.toFloat()};destination+=relight(fills[biome][i],l,n).map{enc(it).toFloat()}}else{source+=listOf(9f,9f,9f);destination+=listOf(0f,0f,0f)}
  val blue=(row-3).coerceIn(0.0,1.0);val l3=light(min(row,3.0))
  for(i in 0..2){var c=relight(skies[biome][i],if(blue>0)l3 else l,n);if(blue>0){val a=labLinear(c);val t=blues[i];val glow=sin(PI*blue);c=labRGB(doubleArrayOf(a[0]+(t[0]-a[0])*blue,a[1]+(t[1]-a[1])*blue+doubleArrayOf(.035,.05,.07)[i]*glow,a[2]+(t[2]-a[2])*blue+doubleArrayOf(-.02,0.0,.035)[i]*glow))};sky[i]=c.map(::enc).toDoubleArray()}
  return TrailParallaxTrackerPalette(source.toFloatArray(),destination.toFloatArray(),sky,b.fills.size)
 }
}
