package com.ngocdevv.nativecook.blocks.forms.lacedbootstudio

import android.graphics.*
import kotlin.math.atan2

internal class LacedBootStudioPainter(private val r:LacedBootStudioResources){
 private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val plus=PorterDuffXfermode(PorterDuff.Mode.ADD)
 private val agletGradient=LinearGradient(-4f,-1.4f,-2f,1.4f,intArrayOf(0xFFE4CCA0.toInt(),0xFFAD9065.toInt(),0xFF806447.toInt()),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
 private val heart=Path().apply{moveTo(351f,546f);cubicTo(348f,543f,342f,539f,342f,534.5f);cubicTo(342f,529f,348f,528.5f,351f,532.5f);cubicTo(354f,528.5f,360f,529f,360f,534.5f);cubicTo(360f,539f,354f,543f,351f,546f);close()}
 private fun image(c:Canvas,b:Bitmap,rect:RectF,p:Paint?=null){c.drawBitmap(b,null,rect,p?:imagePaint)}
 private fun blend(c:Canvas,images:List<Bitmap>,weights:DoubleArray,rect:RectF){c.saveLayer(rect,null);val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{xfermode=plus};for(i in images.indices)if(weights[i]>0){p.alpha=(weights[i]*255).toInt().coerceIn(0,255);image(c,images[i],rect,p)};c.restore()}
 private fun path(q:LacedBootStudioCurve)=Path().apply{moveTo(q.a.x.toFloat(),q.a.y.toFloat());cubicTo(q.c1.x.toFloat(),q.c1.y.toFloat(),q.c2.x.toFloat(),q.c2.y.toFloat(),q.b.x.toFloat(),q.b.y.toFloat())}
 private fun stroke(c:Canvas,path:Path,hex:Int,width:Float,alpha:Double=1.0){c.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=LacedBootStudioResources.color(hex);this.alpha=(alpha*255).toInt().coerceIn(0,255);style=Paint.Style.STROKE;strokeWidth=width;strokeCap=Paint.Cap.ROUND})}
 private fun lace(c:Canvas,s:LacedBootStudioScene){for(i in 0..10){if(i>0&&s.trims[i]<=0)continue;val q=if(i==0)LacedBootStudioGeometry.tail(s)else LacedBootStudioGeometry.curve(i,s.slacks[i]);val table=LacedBootStudioGeometry.arcs[i];val trim=if(i==0)1.0 else s.trims[i];val start=if(i==0).035 else table.parameter(minOf(trim,1.6/table.length));val end=if(i==0)1.0 else table.parameter(maxOf(0.0,trim-1.6/table.length));val p=Paint().apply{alpha=(s.spanAlpha[i]*255).toInt().coerceIn(0,255)};c.saveLayer(RectF(-12f,-12f,129f,332f),p)
  for(pt in if(trim>=.97)listOf(q.a,q.b)else listOf(q.a)){c.save();c.clipPath(Path().apply{addOval(RectF((pt.x-2.4).toFloat(),(pt.y-2.4).toFloat(),(pt.x+2.4).toFloat(),(pt.y+2.4).toFloat()),Path.Direction.CW)});stroke(c,path(q),0x443126,2.8f);c.restore()}
  if(end>start){val front=path(q.segment(start,end));c.save();c.translate(0f,1f);stroke(c,front,0,4.8f,.17);c.restore();if(i>0){c.save();c.translate(0f,.8f);stroke(c,front,0,3.5f,.14);c.restore()};stroke(c,front,0x564135,2.8f);c.save();c.translate(-.35f,-.45f);stroke(c,front,0xA78763,.65f);c.restore()};c.restore()
 }}
 private fun aglet(c:Canvas,s:LacedBootStudioScene){c.save();c.translate(s.tip.x.toFloat(),s.tip.y.toFloat());c.rotate((atan2(s.tangent.y,s.tangent.x)*180/Math.PI).toFloat());val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=agletGradient;alpha=(s.tipAlpha*255).toInt().coerceIn(0,255)};val rect=RectF(-7f,-1.4f,0f,1.4f);c.drawRoundRect(rect,.6f,.6f,p);p.shader=null;p.color=LacedBootStudioResources.color(0x715539);p.alpha=(s.tipAlpha*255).toInt().coerceIn(0,255);p.style=Paint.Style.STROKE;p.strokeWidth=.3f;c.drawRoundRect(rect,.6f,.6f,p);c.restore()}
 fun draw(c:Canvas,s:LacedBootStudioScene,leather:Int,sole:Int,favorite:Boolean){
  imagePaint.alpha=255;imagePaint.xfermode=null;imagePaint.shader=null;image(c,r.paper,RectF(0f,0f,393f,852f))
  for(left in listOf(true,false)){c.save();c.translate(if(left)123.5f else 272.5f,308f);c.rotate(if(left)-4f else 4f);if(left)c.scale(-1f,1f);c.translate(-58.5f,-160f);image(c,r.shadow,RectF(-46f,-40f,163f,382f));blend(c,r.soles,s.soles,RectF(-4f,-4f,121f,328f));blend(c,if(left)r.left else r.bodies,s.materials,RectF(0f,0f,117f,320f));if(!left){lace(c,s);blend(c,r.eyelets,s.materials,RectF(0f,0f,117f,190f));aglet(c,s)};c.restore()}
  val p=Paint(Paint.ANTI_ALIAS_FLAG);for(i in 0..2){p.color=LacedBootStudioResources.color(if(i==0)0x50473E else 0xC8C0B6);c.drawCircle(187.5f+i*9,493f,1.8f,p)}
  c.drawPicture(r.staticCopy);c.drawPicture(r.rowCopy[s.rows]);p.color=LacedBootStudioResources.color(0x302C27);p.style=if(favorite)Paint.Style.FILL else Paint.Style.STROKE;p.strokeWidth=1.4f;c.drawPath(heart,p)
  val colors=intArrayOf(0xA56D48,0x493F36,0x6F4F3A);for(i in 0..2){p.style=Paint.Style.FILL;p.color=LacedBootStudioResources.color(colors[i]);c.drawCircle(38f+i*40,640f,12f,p);if(leather==i){p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=LacedBootStudioResources.color(0x302C27);c.drawCircle(38f+i*40,640f,16f,p)}}
  for(i in 0..1){val x=242f+i*58;p.style=Paint.Style.FILL;p.color=LacedBootStudioResources.color(if(i==0)0x45352D else 0x98734F);c.drawRoundRect(RectF(x-24,630f,x+24,650f),10f,10f,p);if(sole==i){p.style=Paint.Style.STROKE;p.strokeWidth=.8f;p.color=LacedBootStudioResources.color(0x302C27);c.drawRoundRect(RectF(x-26,628f,x+26,652f),12f,12f,p)}}
  fun copy(picture:Picture,alpha:Double){val paint=Paint().apply{this.alpha=(alpha*255).toInt().coerceIn(0,255)};c.saveLayer(RectF(24f,754f,369f,808f),paint);c.drawPicture(picture);c.restore()};copy(r.saveCopy,1-s.savedMix);copy(r.savedCopy,s.savedMix)
 }
}
