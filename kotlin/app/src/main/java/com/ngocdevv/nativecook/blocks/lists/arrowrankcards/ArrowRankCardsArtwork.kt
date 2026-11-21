package com.ngocdevv.nativecook.blocks.lists.arrowrankcards
import android.content.res.AssetManager
import android.graphics.*
import kotlin.math.*

internal class ArrowRankCardsArtwork(assets: AssetManager) {
 val bold=Typeface.createFromAsset(assets,"arrowrankcards/arrowrankcards_InterTight-Bold.ttf")
 val extra=Typeface.createFromAsset(assets,"arrowrankcards/arrowrankcards_InterTight-ExtraBold.ttf")
 val albums=listOf(listOf("Paper","Lanterns"),listOf("Glass","Harbour"),listOf("Voltage","Garden"))
 val ranks=listOf("4","5","2")
 private val titles=listOf(listOf("Slow","Meridian"),listOf("Asked The","Weather"),listOf("Motel","Carousel"))
 private val artists=listOf("Odessa Vale","June Arlo","Nico Brava")
 private val panels=intArrayOf(0xff17b15a.toInt(),0xffc9c4be.toInt(),0xfff54a35.toInt())
 private val muted=intArrayOf(0xff0e7c3d.toInt(),0xff736d68.toInt(),0xff9b291d.toInt())
 private val chips=listOf(listOf("A" to 0xffddf53f.toInt(),"K" to 0xffddb9f6.toInt()),listOf("A" to 0xffddf53f.toInt(),"E" to 0xffc9f6c1.toInt(),"G" to 0xff3fe8b2.toInt()),listOf("H" to 0xffff9f9f.toInt(),"G" to 0xff3fe8b2.toInt()))
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
 fun width(text: String,size: Double,heavy: Boolean=false): Double { paint.typeface=if(heavy)extra else bold;paint.textSize=size.toFloat();return paint.measureText(text).toDouble() }
 fun text(c: Canvas,text: String,x: Double,y: Double,size: Double,color: Int=INK,heavy: Boolean=false,track: Double=0.0,alpha: Double=1.0) {
  paint.typeface=if(heavy)extra else bold;paint.textSize=size.toFloat();paint.color=color;paint.alpha=(255*alpha.coerceIn(0.0,1.0)).roundToInt()
  if(track==0.0)c.drawText(text,x.toFloat(),y.toFloat(),paint)
  else { var cursor=x;for(char in text) { val s=char.toString();c.drawText(s,cursor.toFloat(),y.toFloat(),paint);cursor+=paint.measureText(s)+track } }
 }
 val faces=List(3) { i -> Bitmap.createBitmap(1125,1122,Bitmap.Config.ARGB_8888).also { bitmap ->
  val c=Canvas(bitmap);c.scale(3f,3f);c.drawColor(panels[i])
  text(c,titles[i][0],11.0,16+34*1490.0/2048,34.0);text(c,titles[i][1],11.0,53+34*1490.0/2048,34.0);text(c,artists[i],11.0,94+15*1490.0/2048,15.0,muted[i])
  chips[i].forEachIndexed { j,chip -> val x=42+j*71.0;paint.color=chip.second;paint.style=Paint.Style.FILL;c.drawCircle(x.toFloat(),153f,31f,paint);paint.color=0x59ffffff;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawCircle(x.toFloat(),153f,30f,paint);paint.style=Paint.Style.FILL;text(c,chip.first,x-width(chip.first,30.0)/2,153+30*1490.0/4096,30.0) }
 } }
 companion object {
  const val INK=0xff0b0b0b.toInt()
  fun arrow(f: ArrowRankCardsFrame): Path {
   val head=(f.head+f.shake)*PI/180;val lag=(f.head-f.tail).coerceIn(-75.0,75.0)*PI/180;val tail=head-lag
   val hx=cos(head);val hy=sin(head);val tx=cos(tail);val ty=sin(tail);val s=if(lag<0)-1.0 else 1.0;val r=28.0
   val back=22*max(0.0,-hx)+32*max(0.0,hy)-14*max(0.0,-hy);val px=73-back*hx;val py=313-back*hy
   val arc=r*abs(lag);val straight=max(0.0,97.443651+f.stretch-arc);val lt=straight*.5;val lh=straight-lt
   val ax=px-57*tx+f.beckon*tx;val ay=py-57*ty+f.beckon*ty;val e1x=ax+lt*tx;val e1y=ay+lt*ty
   val ox=e1x-s*r*ty;val oy=e1y+s*r*tx;val e2x=ox+s*r*hy;val e2y=oy-s*r*hx;val bx=e2x+lh*hx;val by=e2y+lh*hy;val k=4.0/3*tan(abs(lag)/4)*r
   return Path().apply { moveTo(ax.toFloat(),ay.toFloat());lineTo(e1x.toFloat(),e1y.toFloat());if(arc>.01)cubicTo((e1x+k*tx).toFloat(),(e1y+k*ty).toFloat(),(e2x-k*hx).toFloat(),(e2y-k*hy).toFloat(),e2x.toFloat(),e2y.toFloat());lineTo(bx.toFloat(),by.toFloat());moveTo((bx-37.221826*hx+37.221826*hy).toFloat(),(by-37.221826*hy-37.221826*hx).toFloat());lineTo(bx.toFloat(),by.toFloat());lineTo((bx-37.221826*hx-37.221826*hy).toFloat(),(by-37.221826*hy+37.221826*hx).toFloat()) }
  }
 }
}
