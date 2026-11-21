package com.ngocdevv.nativecook.blocks.lists.arrowrankcards
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class ArrowRankCardsRenderer(private val art: ArrowRankCardsArtwork) {
 private val m=ArrowRankCardsMotion
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val prisms=if(Build.VERSION.SDK_INT>=33)ArrowRankCardsPrisms(art) else null
 private fun ink(color: Int,alpha: Double=1.0): Paint { paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=color;paint.alpha=(255*alpha.coerceIn(0.0,1.0)).roundToInt();return paint }
 private fun rect(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,col: Int) { c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),ink(col)) }
 private fun clip(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double) { c.clipPath(Path().apply { addRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),Path.Direction.CW) }) }
 fun draw(c: Canvas,f: ArrowRankCardsFrame,h: Double,deck: Double,lower: Double,saved: BooleanArray,bookmarkScale: Double=1.0) {
  c.drawRect(0f,0f,402f,h.toFloat(),ink(0xffebebea.toInt()).apply { shader=LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(0xfff1f1f0.toInt(),0xffebebea.toInt(),0xffebebea.toInt()),floatArrayOf(0f,.3f,1f),Shader.TileMode.CLAMP) })
  art.text(c,"Popular",18.0,70+34*1490.0/2048,34.0,track=-.714);art.text(c,"Album",18.0,104+34*1490.0/2048,34.0,0xffa8a8a8.toInt(),track=-.714);icon(c,3,362.0,84.0)
  val ds=c.save();c.translate(0f,178f);c.scale(1f,deck.toFloat());val peek=sin(PI*f.drum)*8
  rect(c,81.0,28+peek,233.0,40.0,20.0,0xfff0f0ef.toInt());rect(c,41.0,36+peek,312.0,40.0,22.0,0xfff4f4f3.toInt())
  c.drawRoundRect(RectF(8f,44f,394f,578f),26f,26f,ink(0xfff9f9f8.toInt()).apply { setShadowLayer(12f,0f,3f,0x1a3a3a38) })
  for(line in 0..1) {
   val ss=c.save();c.clipRect(26f,(55+line*27).toFloat(),316f,(55+line*27+if(line==0)27 else 29).toFloat())
   val e=m.smooth(m.seg(f.drum,.08+.16*line,.68+.16*line))
   for(i in listOf(f.index,f.next)) { val alpha=if(f.flat>0)if(i==f.index)1-f.drum else f.drum else 1.0;val offset=if(f.flat>0)0.0 else if(i==f.index)-29*e else 29*(1-e)
    art.text(c,art.albums[i][line],26.0,55+line*27+(if(line==1)-1 else 0)+24*1490.0/2048+offset,24.0,if(line==0)ArrowRankCardsArtwork.INK else 0xffc8c8c8.toInt(),track=-.408,alpha=alpha)
   };c.restoreToCount(ss)
  }
  val ps=c.save();c.translate(15f,196f);clip(c,0.0,0.0,375.0,374.0,18.0)
  if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated)prisms?.draw(c,f) else fallback(c,f)
  val shadow=.18*m.clamp(8*(f.q.maxOf { it*(1-it) }));if(shadow>0) { val ss=c.saveLayer(null,Paint().apply { alpha=(shadow*255).roundToInt() });c.translate(0f,2f);sign(c,f);c.restoreToCount(ss) }
  sign(c,f);c.restoreToCount(ps)
  rect(c,321.0,57.0,64.0,64.0,32.0,0xfffafaf9.toInt());c.drawCircle(353f,89f,31.5f,ink(0xffe7e7e6.toInt()).apply { style=Paint.Style.STROKE;strokeWidth=1f });icon(c,0,341.0,77.0,saved[if(f.drum<.5)f.index else f.next],bookmarkScale);c.restoreToCount(ds)
  rect(c,11.0,lower,64.0,64.0,32.0,0xfff3f3f2.toInt());icon(c,1,31.0,lower+20)
  rect(c,330.0,lower,64.0,64.0,32.0,0xfff3f3f2.toInt());icon(c,2,350.0,lower+20)
  val ss=c.save();clip(c,94.0,lower,217.0,64.0,32.0);rect(c,94.0,lower,217.0,64.0,32.0,0xfff3f3f2.toInt());val shift=((f.ticks%12)+12)%12
  for(i in 0..20)for(y in listOf(lower+6,lower+52))rect(c,82+i*12+shift,y,2.0,6.0,0.0,0xffcdcdcc.toInt())
  art.text(c,"Today",202.5-art.width("Today",13.0)/2,lower+32+13*1490.0/4096,13.0);c.restoreToCount(ss);rect(c,200.0,lower-1,2.0,66.0,0.0,0xffef3b2d.toInt())
 }
 private fun sign(c: Canvas,f: ArrowRankCardsFrame) {
  fun arrow(pose: ArrowRankCardsFrame,alpha: Double) { c.drawPath(ArrowRankCardsArtwork.arrow(pose),ink(ArrowRankCardsArtwork.INK,alpha).apply { style=Paint.Style.STROKE;strokeWidth=22f;strokeCap=Paint.Cap.BUTT;strokeJoin=Paint.Join.MITER }) }
  arrow(if(f.flat>0)f.copy(head=m.heading(f.index),tail=m.heading(f.index)) else f,if(f.flat>0)1-f.drum else 1.0)
  if(f.flat>0)arrow(f.copy(head=m.heading(f.next),tail=m.heading(f.next)),f.drum)
  val ss=c.save();c.clipRect(278f,252f,375f,374f)
  for(i in listOf(f.index,f.next)) { val offset=if(f.flat>0)0.0 else (if(i==f.index)f.drum else f.drum-1)*110*(if(f.reverse>0)-1 else 1);val x=if(f.axis==0.0)offset else 0.0;val y=if(f.axis==1.0)offset else 0.0;val alpha=if(f.flat>0)if(i==f.index)1-f.drum else f.drum else 1.0;val text=art.ranks[i];val left=364-art.width(text,130.0,true)
   if(f.blur>0 && f.flat==0.0)for(j in -3..3) { val blur=j*f.blur/4;art.text(c,text,left+x+if(f.axis==0.0)blur else 0.0,358+y+if(f.axis==1.0)blur else 0.0,130.0,heavy=true,alpha=alpha*exp(-j*j/2.0)/2.505949878974977) }
   else art.text(c,text,left+x,358+y,130.0,heavy=true,alpha=alpha)
  };c.restoreToCount(ss)
 }
 private fun fallback(c: Canvas,f: ArrowRankCardsFrame) {
  if(f.flat>0) { c.drawBitmap(art.faces[f.index],null,RectF(0f,0f,375f,374f),ink(Color.WHITE,1-f.drum));c.drawBitmap(art.faces[f.next],null,RectF(0f,0f,375f,374f),ink(Color.WHITE,f.drum));return }
  val span=if(f.axis>0)374.0 else 375.0;val pitch=span/9
  for(physical in 0..8) {
   val slot=if(f.reverse>0)8-physical else physical;val turn=f.q[slot];val p=(turn+f.twitch*sin(slot*1.7+.5)/120).coerceIn(0.0,1.2)
   val old=max(0.0,cos(p*2.0943951));val new=max(0.0,cos(abs(1-p)*2.0943951));val seam=pitch*new/max(old+new,.001);val left=physical*pitch
   for(arriving in listOf(true,false)) { val width=if(arriving)seam else pitch-seam;if(width<.001)continue;val local=if(arriving)0.0 else seam;val start=left+if(f.reverse>0)pitch-local-width else local
    val src=if(f.axis>0)Rect(0,(left*3).roundToInt(),1125,((left+pitch)*3).roundToInt()) else Rect((left*3).roundToInt(),0,((left+pitch)*3).roundToInt(),1122)
    val dst=if(f.axis>0)RectF(0f,start.toFloat(),375f,(start+width).toFloat()) else RectF(start.toFloat(),0f,(start+width).toFloat(),374f)
    val shade=if(arriving).70+.30*(1-abs(1-p)) else 1-.30*p
    c.drawBitmap(art.faces[if(arriving)f.next else f.index],src,dst,ink(Color.WHITE).apply { colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(shade.toFloat(),0f,0f,0f,0f,0f,shade.toFloat(),0f,0f,0f,0f,0f,shade.toFloat(),0f,0f,0f,0f,0f,1f,0f))) })
   }
  }
 }
 private fun icon(c: Canvas,kind: Int,x: Double,y: Double,saved: Boolean=false,scale: Double=1.0) {
  val data=listOf("M5 4H19V20L12 15.5L5 20Z","M4 6H20V21H4Z M8 3V8 M16 3V8 M4 10H20","M3 7H9L11 9H21V20H3Z","M2 3L9 10L16 3")[kind]
  val path=requireNotNull(PathParser.createPathFromPathData(data));val ss=c.save();c.translate((x+12).toFloat(),(y+12).toFloat());c.scale(scale.toFloat(),scale.toFloat());c.translate(-12f,-12f)
  if(saved)c.drawPath(path,ink(ArrowRankCardsArtwork.INK))
  c.drawPath(path,ink(ArrowRankCardsArtwork.INK).apply { style=Paint.Style.STROKE;strokeWidth=2f;strokeCap=Paint.Cap.SQUARE;strokeJoin=Paint.Join.MITER });if(kind==1)c.drawCircle(8f,15f,1.25f,ink(ArrowRankCardsArtwork.INK));c.restoreToCount(ss)
 }
}
