package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import android.content.res.AssetManager
import android.graphics.*
import androidx.core.graphics.PathParser

internal class HourScrubSkyArtwork(assets:AssetManager) {
 val low=assets.open("hourscrubsky/hourscrubsky_hs_cloud_low_rg.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
 val high=assets.open("hourscrubsky/hourscrubsky_hs_cloud_high_rg.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
 private val fonts=mapOf(300 to "Light",400 to "Regular",500 to "Medium",600 to "SemiBold").mapValues { Typeface.createFromAsset(assets,"hourscrubsky/hourscrubsky_Figtree-${it.value}.ttf") }
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
 private val cache=mutableMapOf<String,Pair<Path,Double>>()
 val icons=mapOf("NAV" to "M20.3 3.2 C20.9 3.0 21.2 3.5 20.9 4.0 L13.9 20.6 C13.6 21.4 12.5 21.3 12.4 20.5 L11.2 13.0 L3.6 11.7 C2.8 11.6 2.7 10.5 3.5 10.2 Z","MAP" to "M3.0 6.6 C3.0 6.2 3.2 5.9 3.6 5.8 L7.8 4.0 L7.8 18.1 L3.8 19.8 C3.4 20.0 3.0 19.7 3.0 19.2 Z M9.3 4.0 L14.7 6.1 L14.7 20.0 L9.3 18.0 Z M16.2 6.0 L20.2 4.3 C20.6 4.1 21.0 4.4 21.0 4.9 V17.4 C21.0 17.8 20.8 18.1 20.4 18.2 L16.2 20.0 Z","TROPHY" to "M6.8 3.2 H17.2 V9.0 C17.2 12.0 14.9 14.3 12.0 14.3 C9.1 14.3 6.8 12.0 6.8 9.0 Z M10.9 14.0 H13.1 V17.0 H10.9 Z M8.2 17.0 H15.8 C16.5 17.0 17.0 17.5 17.0 18.2 V20.8 H7.0 V18.2 C7.0 17.5 7.5 17.0 8.2 17.0 Z","TROPHY_HANDLES" to "M7.0 5.2 H4.2 V7.0 C4.2 9.0 5.5 10.6 7.4 10.9 M17.0 5.2 H19.8 V7.0 C19.8 9.0 18.5 10.6 16.6 10.9","MORE" to "M12 2.5 A9.5 9.5 0 1 1 11.99 2.5 Z M7.6 10.6 A1.45 1.45 0 1 0 7.61 10.6 Z M12 10.6 A1.45 1.45 0 1 0 12.01 10.6 Z M16.4 10.6 A1.45 1.45 0 1 0 16.41 10.6 Z","LEAF" to "M5.2 18.8 C5.0 11.6 9.6 5.6 18.9 5.1 C19.4 14.2 13.8 18.9 6.6 18.9 M5.2 18.8 L11.6 12.4","SLASH" to "M3.8 3.8 L20.2 20.2","SUN_DISC" to "M9.0 5.6 A3.4 3.4 0 1 1 8.99 5.6 Z","SUN_RAYS" to "M9.0 1.8 V3.0 M3.9 3.9 L4.8 4.8 M1.8 9.0 H3.0 M14.1 3.9 L13.2 4.8 M3.9 14.1 L4.8 13.2","CLOUD" to "M8.6 20.0 H17.6 C19.6 20.0 21.2 18.4 21.2 16.4 C21.2 14.5 19.8 13.0 17.9 12.8 C17.3 10.8 15.5 9.4 13.4 9.4 C10.9 9.4 8.9 11.3 8.7 13.7 C7.0 13.9 5.7 15.3 5.7 17.0 C5.7 18.7 7.0 20.0 8.6 20.0 Z","ARROW" to "M-8 0 H8 M2.2 -6 L8 0 L2.2 6","PLAY" to "M-3.4 -5.6 C-3.4 -6.3 -2.7 -6.7 -2.1 -6.3 L6.2 -0.9 C6.8 -0.5 6.8 0.4 6.2 0.8 L-2.1 6.2 C-2.7 6.6 -3.4 6.2 -3.4 5.5 Z","PAUSE" to "M-5.2 -5.6 C-5.2 -6.1 -4.8 -6.5 -4.3 -6.5 H-2.3 C-1.8 -6.5 -1.4 -6.1 -1.4 -5.6 V5.6 C-1.4 6.1 -1.8 6.5 -2.3 6.5 H-4.3 C-4.8 6.5 -5.2 6.1 -5.2 5.6 Z M1.4 -5.6 C1.4 -6.1 1.8 -6.5 2.3 -6.5 H4.3 C4.8 -6.5 5.2 -6.1 5.2 -5.6 V5.6 C5.2 6.1 4.8 6.5 4.3 6.5 H2.3 C1.8 6.5 1.4 6.1 1.4 5.6 Z","RESET" to "M-4.6 -3.6 A6 6 0 1 1 -6.0 1.2 M-4.6 -3.6 L-5.0 -7.4 M-4.6 -3.6 L-0.9 -3.4").mapValues { requireNotNull(PathParser.createPathFromPathData(it.value)).apply { if(it.key=="MORE")fillType=Path.FillType.EVEN_ODD } }
 val words=listOf("Dry","until","evening,","a","calm,","clear","window","ahead")
 val lineOf=intArrayOf(0,0,0,1,1,1,2,2)
 val conditions=listOf("Mostly Cloudy","Partly Cloudy","Cloudy")
 val wordX=DoubleArray(8)
 init { val space=run("a a",34.0,500,-.02).second-2*run("a",34.0,500,-.02).second;var x=22.0;var line=0;for(i in words.indices) { if(lineOf[i]!=line) { line=lineOf[i];x=22.0 };wordX[i]=x;x+=run(words[i],34.0,500,-.02).second+space } }
 fun run(text:String,size:Double,weight:Int=500,track:Double=0.0,tnum:Boolean=false):Pair<Path,Double> {
  val key="$text|$size|$weight|$track|$tnum";cache[key]?.let { return it }
  paint.typeface=fonts[weight];paint.textSize=size.toFloat();paint.fontFeatureSettings=if(tnum)"tnum" else "kern"
  val result=Path();var x=0.0
  for(char in text) { val str=char.toString();val p=Path();paint.getTextPath(str,0,1,x.toFloat(),0f,p);result.addPath(p);x+=paint.measureText(str)+track*size }
  val value=result to x;cache[key]=value;return value
 }
}
