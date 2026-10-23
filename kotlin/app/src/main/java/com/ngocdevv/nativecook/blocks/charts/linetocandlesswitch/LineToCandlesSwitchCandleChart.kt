package com.ngocdevv.nativecook.blocks.charts.linetocandlesswitch

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.*

val LineGreen = Color(0xff1fa463)
private object C { val up = Color(0xff00c076); val down = Color(0xfff6465d); val cursor = Color(0xff71767b); const val line = 1.6f; const val wick = 1f; const val dot = 4.5f; val dash = floatArrayOf(3f,4f) }
fun candleProgress(a: Frame,b: Frame,x: Double,time: Double): Double {
    if(a.lineAlpha>0.5 && b.lineAlpha<0.5) return Motion.ease((time-Motion.fade-x*Motion.spread)/Motion.grow)
    if(a.lineAlpha<0.5 && b.lineAlpha>0.5) return Motion.ease((time-x*Motion.spread)/Motion.grow)
    return Motion.ease(time/Motion.move)
}
fun lineAlphaAt(a: Frame,b: Frame,time: Double,length: Double): Double {
    if(a.lineAlpha>b.lineAlpha) return a.lineAlpha*(1-Motion.clamp(time/Motion.fade))
    if(a.lineAlpha<b.lineAlpha) return a.lineAlpha+(b.lineAlpha-a.lineAlpha)*Motion.clamp((time-(length-Motion.fade))/Motion.fade)
    return b.lineAlpha
}
fun frameAt(a: Frame,b: Frame,time: Double,length: Double): Frame {
    if(time>=length)return b
    val e=Motion.ease(time/length);val u=List(Motion.points){candleProgress(a,b,a.x[it],time)}
    fun mix(x: List<Double>,y: List<Double>)=List(Motion.points){x[it]+(y[it]-x[it])*u[it]}
    return Frame(mix(a.o,b.o),mix(a.h,b.h),mix(a.l,b.l),mix(a.c,b.c),mix(a.x,b.x),mix(a.w,b.w),List(Motion.points){a.line[it]+(b.line[it]-a.line[it])*e},a.low+(b.low-a.low)*e,a.high+(b.high-a.high)*e,lineAlphaAt(a,b,time,length),max(a.candles,b.candles))
}
internal fun DrawScope.drawChart(a: Frame,b: Frame,time: Double,length: Double,width: Float,height: Float,pad: Float,cursor: Double,held: Double) {
    val f=frameAt(a,b,time,length);val span=(f.high-f.low).let{if(it==0.0)1.0 else it};fun y(value: Double)=(pad+(1-(value-f.low)/span)*(height-2*pad)).toFloat()
    val line=Path();val area=Path();val upBodies=Path();val downBodies=Path();val upWicks=Path();val downWicks=Path()
    for(i in 0 until Motion.points){val x=(i+0.5f)/Motion.points*width;val py=y(f.line[i]);if(i==0){line.moveTo(x,py);area.moveTo(x,height)}else line.lineTo(x,py);area.lineTo(x,py)};area.lineTo((Motion.points-0.5f)/Motion.points*width,height);area.close()
    if(f.candles>0){val floor=if(a.candles>0 && b.candles>0)1f else 0f;for(i in 0 until Motion.points){val top=y(max(f.o[i],f.c[i]));val bottom=max(top+floor,y(min(f.o[i],f.c[i])));val wt=y(f.h[i]);val wb=y(f.l[i]);if(wb-wt<0.3f)continue;val x=(f.x[i]*width).toFloat();val half=(f.w[i]*width/2).toFloat();val wick=Rect(x-C.wick/2,wt,x+C.wick/2,wb);val body=Rect(x-half,top,x+half,bottom);if(f.c[i]>=f.o[i]){upWicks.addRect(wick);if(bottom-top>=0.3f)upBodies.addRect(body)}else{downWicks.addRect(wick);if(bottom-top>=0.3f)downBodies.addRect(body)}}}
    clipRect(0f,0f,width,height){drawPath(area,Brush.linearGradient(0f to rgba(31,164,99,0.45f),0.6f to rgba(31,164,99,0.12f),1f to rgba(31,164,99,0f),start=Offset(0f,pad),end=Offset(0f,height)),alpha=f.lineAlpha.toFloat());drawPath(line,LineGreen,alpha=f.lineAlpha.toFloat(),style=Stroke(C.line,cap=StrokeCap.Round,join=StrokeJoin.Round));drawPath(upWicks,C.up);drawPath(downWicks,C.down);drawPath(upBodies,C.up);drawPath(downBodies,C.down);val x=(cursor*width).toFloat();drawLine(C.cursor.copy(alpha=held.toFloat()),Offset(x,0f),Offset(x,height),1f,pathEffect=PathEffect.dashPathEffect(C.dash));drawCircle(LineGreen.copy(alpha=(held*f.lineAlpha).toFloat()),C.dot,Offset(x,y(Motion.lineAt(f.line,cursor))))}
}
