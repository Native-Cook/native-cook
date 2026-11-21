package com.ngocdevv.nativecook.blocks.gestures.trailparallaxtracker
import kotlin.math.*
internal data class TrailParallaxTrackerBiome(val width:Double,val offsets:DoubleArray,val sky:IntArray,val fills:IntArray,val native:Int)
internal data class TrailParallaxTrackerWaypoint(val at:Double,val name:String,val note:String)
internal object TrailParallaxTrackerWorld {
 val borders=doubleArrayOf(624.0,1408.8,2900.0)
 val starts=doubleArrayOf(0.0,624.0,1408.8,2900.0)
 val ends=doubleArrayOf(624.0,1408.8,2900.0,3600.0)
 val biomes=arrayOf(
TrailParallaxTrackerBiome(1440.0,doubleArrayOf(560.0,620.0,484.0),intArrayOf(0x5E9FE6,0x8FC0F0,0xB5D6F5),intArrayOf(0xC9D8EE,0xA9BEDF,0x8AA3CF,0xF1F5FB),0),
TrailParallaxTrackerBiome(1560.0,doubleArrayOf(490.0,516.0,430.0),intArrayOf(0x5E9FE6,0x8FC0F0,0xB5D6F5),intArrayOf(0xA9BEDF,0xC1A6AB,0x9A8AA5,0x6E6C96,0x4B5980),1),
TrailParallaxTrackerBiome(2800.0,doubleArrayOf(460.0,420.0,0.0),intArrayOf(0xE6EFAE,0xEEF3B0,0xF3F6C2),intArrayOf(0xEEF3B0,0xBFE3A0,0x5FAF8F,0x2F7D6B,0x8E2A22,0x5E1A1C),2),
TrailParallaxTrackerBiome(1400.0,doubleArrayOf(446.0,450.0,-40.0),intArrayOf(0xF5DFB6,0xFBECC5,0xFCF1D8),intArrayOf(0xB9B7B0,0xD1A696,0xE3B79A,0xC98F78,0x3E3A3C,0x262325),3),
)
 val waypoints=arrayOf(
TrailParallaxTrackerWaypoint(0.0,"Ember Canyon","A deep canyon cut by the Ember River. The trail keeps to the rim for nine miles, then drops to the water."),
TrailParallaxTrackerWaypoint(29.7,"Mount Hallam","A granite massif of fins and spires. The trail threads the saddle under its north face, with snow into July."),
TrailParallaxTrackerWaypoint(52.0,"Needle Col","A narrow notch between two spires and the high point of the section. The wind picks up fast after noon."),
TrailParallaxTrackerWaypoint(57.5,"Giants Grove","Sequoias older than any map of the range. The trail runs between their trunks for forty miles of shade."),
TrailParallaxTrackerWaypoint(105.7,"Tarn Meadow","A string of small lakes in a clearing still ringed by the big trees. The best water on the whole stretch."),
TrailParallaxTrackerWaypoint(120.7,"Chimney Rock","A lone stack of dark rock above the dunes. Hikers leave a stone on the cairn at its foot for luck."),
TrailParallaxTrackerWaypoint(131.7,"Sawtooth Saddle","A long, sandy ridge between two ranges, warm until late in the evening and cold right after."),
TrailParallaxTrackerWaypoint(142.7,"Owl Peak","The last summit before the desert. Camp below it and watch the light leave the hills one by one."),
)
 val hours=arrayOf(doubleArrayOf(-0.035,-0.004,-0.024,0.9),doubleArrayOf(0.0,0.0,0.0,1.0),doubleArrayOf(0.012,0.006,0.02,1.05),doubleArrayOf(-0.02,0.016,0.034,0.92),doubleArrayOf(-0.3,-0.004,-0.075,0.45))
 val hourKeys=arrayOf(doubleArrayOf(-20.0,0.0),doubleArrayOf(0.0,0.0),doubleArrayOf(26.0,1.0),doubleArrayOf(58.7,1.5),doubleArrayOf(76.0,2.0),doubleArrayOf(105.0,2.3),doubleArrayOf(120.83333333333333,3.0),doubleArrayOf(148.5,3.0),doubleArrayOf(150.0,4.0))
 data class Split(val a:Int,val b:Int,val x:Double)
 fun split(camera:Double,reduced:Boolean):Split{val rate=if(reduced)1.0 else 1.7;var k=0;for((i,c)in borders.withIndex())if(camera>=c)k=i+1;if(k<borders.size){val x=201-rate*(camera-borders[k]);if(x<662)return Split(k,k+1,x)};if(k>0){val x=201-rate*(camera-borders[k-1]);if(x> -260)return Split(k-1,k,x)};return Split(k,k,9999.0)}
 fun offset(biome:Int,depth:Int,camera:Double,reduced:Boolean):Double{val b=biomes[biome];val flat=(b.width-402-(ends[biome]-starts[biome]))/2;return(camera-starts[biome])*(if(reduced)1.0 else doubleArrayOf(.12,.35,.7)[depth])+(if(reduced)flat else b.offsets[depth])}
}
