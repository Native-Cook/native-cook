package com.ngocdevv.nativecook.blocks.charts.metricdropdownlinescrub

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.floor

data class Plot(val left: Float,val top: Float,val width: Float,val height: Float)
private object C { val grid=Color(0xff2c2c30);val line=Color(0xfff4f4f5);val hairline=Color(0xff4a4a50);val halo=Color.White.copy(alpha=0.14f);val ring=Color(0xff17171a);const val lineWidth=2f;const val dot=2.6f;const val picked=3f;const val grown=5f;const val pickedHalo=11f;const val pickedRing=1.5f;const val dimmed=0.35f }
internal fun DrawScope.drawChart(plot: Plot,grid: List<Double>,points: List<Double>,x: Float,y: Float,scrub: Float,hairlineTop: Float,opacity: Float){
    val lines=Path();val dots=Path();for(at in grid){val y=floor(plot.top+plot.height*(1-at)+0.5).toFloat()+0.5f;drawLine(C.grid,Offset(plot.left,y),Offset(plot.left+plot.width,y),1f)}
    for(i in points.indices step 2){val px=points[i].toFloat();val py=points[i+1].toFloat();if(i==0)lines.moveTo(px,py)else lines.lineTo(px,py);dots.addOval(Rect(px-C.dot,py-C.dot,px+C.dot,py+C.dot))}
    drawLine(C.hairline.copy(alpha=scrub*opacity),Offset(x,hairlineTop),Offset(x,plot.top+plot.height),1f);val rest=(1-(1-C.dimmed)*scrub)*opacity;drawPath(lines,C.line,alpha=rest,style=Stroke(C.lineWidth,cap=StrokeCap.Round,join=StrokeJoin.Round));drawPath(dots,C.line,alpha=rest)
    val radius=C.picked+(C.grown-C.picked)*scrub;drawCircle(C.halo,C.pickedHalo*scrub,Offset(x,y),alpha=scrub*opacity);drawCircle(C.ring,radius+C.pickedRing,Offset(x,y),alpha=scrub*opacity);drawCircle(C.line,radius,Offset(x,y),alpha=scrub*opacity)
}
