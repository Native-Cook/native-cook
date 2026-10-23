package com.ngocdevv.nativecook.blocks.forms.lacedbootstudio

import android.content.Context
import android.graphics.*

internal class LacedBootStudioResources private constructor(private val context:Context){
 private fun bitmap(name:String):Bitmap=context.assets.open("lacedbootstudio/lacedbootstudio_$name.png").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888;inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)})!!}
 val paper=bitmap("paper");val bodies=listOf(bitmap("body-tan"),bitmap("body-charcoal"),bitmap("body-chestnut"));val left=listOf(bitmap("left-tan"),bitmap("left-charcoal"),bitmap("left-chestnut"));val eyelets=listOf(bitmap("eyelets-tan"),bitmap("eyelets-charcoal"),bitmap("eyelets-chestnut"));val soles=listOf(bitmap("sole-dark"),bitmap("sole-natural"));val shadow=bitmap("shadow")
 val decodedBytes=(listOf(paper,shadow)+bodies+left+eyelets+soles).sumOf{it.allocationByteCount}
 val staticCopy:Picture=Picture().apply{val c=beginRecording(393,852);drawCopy(c);endRecording()}
 val rowCopy=(0..6).map{r->Picture().apply{val c=beginRecording(393,852);text(c,"$r / 6 ROWS",24f,706f,345f,16f,11f,0x302C27,Typeface.create("monospace",Typeface.NORMAL));endRecording()}}
 val saveCopy=Picture().apply{val c=beginRecording(393,852);text(c,"Save this pair",44f,754f,260f,54f,15f,0xF2EDE8,Typeface.create("sans-serif-medium",Typeface.NORMAL));endRecording()}
 val savedCopy=Picture().apply{val c=beginRecording(393,852);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFFF2EDE8.toInt();style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND};val check=Path().apply{moveTo(45f,781f);lineTo(49f,785f);lineTo(56f,777f)};c.drawPath(check,p);text(c,"Pair saved",64f,754f,240f,54f,15f,0xF2EDE8,Typeface.create("sans-serif-medium",Typeface.NORMAL));endRecording()}
 companion object{
  fun load(context:Context)=LacedBootStudioResources(context)
  fun color(hex:Int)=(0xFF000000.toInt() or hex)
  fun text(c:Canvas,value:String,x:Float,y:Float,w:Float,h:Float,size:Float,hex:Int,typeface:Typeface=Typeface.DEFAULT,center:Boolean=false,tracking:Float=0f){
   val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=color(hex);textSize=size;this.typeface=typeface};val fm=p.fontMetrics;val baseline=y+h/2-(fm.ascent+fm.descent)/2
   c.save();c.clipRect(x,y,x+w,y+h)
   if(tracking==0f){c.drawText(value,if(center)x+(w-p.measureText(value))/2 else x,baseline,p)}else{val widths=FloatArray(value.length);p.getTextWidths(value,widths);val total=widths.sum()+tracking*(value.length-1);var xx=if(center)x+(w-total)/2 else x;for(i in value.indices){c.drawText(value,i,i+1,xx,baseline,p);xx+=widths[i]+tracking}}
   c.restore()
  }
  private fun drawCopy(c:Canvas){
   val medium=Typeface.create("sans-serif-medium",Typeface.NORMAL)
   text(c,"ATELIER ROWE",79f,68f,235f,18f,11f,0x302C27,medium,true,2f)
   text(c,"Alder Lace Boot",24f,523f,296f,30f,25f,0x302C27,Typeface.create("serif",Typeface.BOLD))
   text(c,"Hand-finished leather",24f,560f,345f,18f,12f,0x79766F)
   text(c,"LEATHER",24f,601f,150f,16f,9f,0x302C27,medium,false,1.5f);text(c,"SOLE",218f,601f,151f,16f,9f,0x302C27,medium,false,1.5f)
   text(c,"Pull the lace through each eyelet.",24f,678f,345f,16f,11f,0x79766F)
   val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=color(0x302C27);style=Paint.Style.STROKE;strokeWidth=1.5f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
   c.drawPath(Path().apply{moveTo(43f,71f);lineTo(37f,77f);lineTo(43f,83f)},p)
   p.strokeWidth=1.3f;c.drawRoundRect(RectF(346f,73f,360f,87f),1f,1f,p);c.drawPath(Path().apply{moveTo(349f,73f);lineTo(349f,70f);cubicTo(349f,64f,357f,64f,357f,70f);lineTo(357f,73f)},p)
   p.style=Paint.Style.FILL;p.color=color(0x608674);c.drawRoundRect(RectF(24f,754f,369f,808f),6f,6f,p);text(c,"$245",309f,754f,40f,54f,13f,0xF2EDE8)
  }
 }
}
