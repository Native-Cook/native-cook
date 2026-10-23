package com.ngocdevv.nativecook.blocks.charts.metricdropdownlinescrub

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

internal class Roller {
    companion object { const val roll=0.26;val digits=listOf("0","1","2","3","4","5","6","7","8","9");val ease=CubicBezierEasing(0f,0f,0.58f,1f) }
    private data class Digit(var goal: Int,var from: Double,var to: Double,var at: Double=-100.0,var duration: Double=0.0){fun value(now: Double): Double{val u=if(duration==0.0)1.0 else((now-at)/duration).coerceIn(0.0,1.0);return from+(to-from)*ease.transform(u.toFloat())}}
    private val columns=mutableMapOf<Int,Digit>();var end=-100.0;private set
    fun draw(scope: DrawScope,text: String,now: Double,still: Boolean,x: Float,y: Float): Float=with(scope){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=26f;fontFeatureSettings="tnum";typeface=if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.DEFAULT,600,false)else Typeface.DEFAULT_BOLD;color=Color.White.toArgb()};val height=30f;val tracking=-0.5f;val baseline=(height-paint.fontMetrics.ascent-paint.fontMetrics.descent)/2;var left=x;val keys=mutableSetOf<Int>()
        for((i,char)in text.withIndex()){val key=text.length-i;val label=char.toString();val width=paint.measureText(label)+tracking;val goal=char.digitToIntOrNull();if(goal!=null){keys.add(key);val d=columns.getOrPut(key){Digit(goal,goal.toDouble(),goal.toDouble())};if(d.goal!=goal){d.from=d.value(now);d.goal=goal;d.to=goal.toDouble();d.at=now;d.duration=if(still)0.0 else roll;end=now+d.duration};val pos=d.value(now);clipRect(left,y,left+width,y+height){for(row in 0..9)drawIntoCanvas{it.nativeCanvas.drawText(digits[row],left,y+baseline+(row-pos).toFloat()*height,paint)}}}else drawIntoCanvas{it.nativeCanvas.drawText(label,left,y+baseline,paint)};left+=width};columns.keys.retainAll(keys);left-x
    }
}
