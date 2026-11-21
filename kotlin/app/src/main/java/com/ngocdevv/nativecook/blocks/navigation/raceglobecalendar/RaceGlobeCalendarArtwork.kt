package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.content.res.AssetManager
import android.graphics.*
import androidx.core.graphics.PathParser
import org.json.JSONArray
import kotlin.math.*

internal class RaceGlobeCalendarArtwork(assets:AssetManager) {
 val images=listOf("earth_day.jpg","earth_night_2048.jpg","earth_mask_2048.png","earth_night_512.jpg").map { name->assets.open("raceglobecalendar/raceglobecalendar_$name").use { requireNotNull(BitmapFactory.decodeStream(it)) } }
 private val rows=assets.open("raceglobecalendar/raceglobecalendar_rounds.json").bufferedReader().use { reader->val array=JSONArray(reader.readText());List(array.length()) { array.getJSONObject(it) } }
 val titles=rows.map { it.getString("title") };val wheres=rows.map { it.getString("place")+", "+it.getString("country") };val chips=rows.map { it.getString("chip") }
 val pre=rows.map { it.getString("dateLine").substringBefore(it.getString("localStart")) };val suf=rows.map { it.getString("dateLine").substringAfter(it.getString("localStart")) }
 val circuits=rows.map { requireNotNull(PathParser.createPathFromPathData(it.getString("circuitPath"))) }
 val icons=mapOf("SHUFFLE" to "M3 6h2c5 0 9 12 14 12h2 M17 14l4 4-4 4 M3 18h2c2 0 4-3 6-6c2-3 5-6 8-6h2 M17 2l4 4-4 4","TROPHY" to "M7 3h10v7c0 7-10 7-10 0z M7 5H3v3c0 4 4 5 5 4 M17 5h4v3c0 4-4 5-5 4 M12 15v5 M8 21h8","FLAG" to "M5 22V3 M5 4c5-4 9 4 15 0v10c-6 4-10-4-15 0").mapValues { requireNotNull(PathParser.createPathFromPathData(it.value)) }
 private val fonts=listOf("display_semibold","regular","semibold","bold").associateWith { Typeface.createFromAsset(assets,"raceglobecalendar/raceglobecalendar_inter_$it.ttf") }
 private val cache=mutableMapOf<String,Pair<Path,Double>>();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
 val stars:List<DoubleArray>
 val arcs:List<List<DoubleArray>>
 init {
  var seed=7.0;fun rnd():Double { seed=(seed*16807)%2147483647;return seed/2147483647 }
  stars=List(140) { val x=rnd()*402;val y=rnd()*874;val r=.5+rnd()*.7;val b=min(3.0,floor(rnd()*4));val g=if(rnd()<.5)0.0 else 1.0;doubleArrayOf(x,y,r,.15+.15*b,g) }
  val m=RaceGlobeCalendarMotion;val w=RaceGlobeCalendarData.W
  arcs=List(23) { j->val a=w[j];val b=w[j+1];val om=acos(m.dot(a,b).coerceIn(-1.0,1.0));val lift=.02+.04*min(om/(120*PI/180),1.0);List(41) { k->val s=k/40.0;val ka=sin((1-s)*om)/sin(om);val kb=sin(s*om)/sin(om);val h=1+lift*sin(PI*s);DoubleArray(3) { (a[it]*ka+b[it]*kb)*h } } }
 }
 fun run(text:String,size:Double,face:String="regular"):Pair<Path,Double> {
  val key="$text|$size|$face";cache[key]?.let { return it };paint.typeface=fonts[face];paint.textSize=size.toFloat();var x=0.0;val path=Path()
  for(char in text) { val s=char.toString();val p=Path();paint.getTextPath(s,0,1,x.toFloat(),0f,p);path.addPath(p);x+=paint.measureText(s) };val result=path to x;cache[key]=result;return result
 }
}
