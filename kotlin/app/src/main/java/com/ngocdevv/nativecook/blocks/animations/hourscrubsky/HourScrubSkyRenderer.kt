package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class HourScrubSkyRenderer(private val art:HourScrubSkyArtwork) {
 private val m=HourScrubSkyMotion
 private val sky=if(Build.VERSION.SDK_INT>=33)HourScrubSkyShader(art.low,art.high) else null
 private val backdrop=if(Build.VERSION.SDK_INT>=33)HourScrubSkyBackdrop() else null
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val white=doubleArrayOf(1.0,1.0,1.0);private val dark=rgb(0x0b0d12);private val light=rgb(0xf7f8fa);private val pill=rgb(0x16181d);private val red=rgb(0xe5484d);private val glassN=rgb(0x1a1d26)
 private fun rgb(c:Int)=HourScrubSkyColors.rgb(c)
 private fun mix(a:DoubleArray,b:DoubleArray,k:Double)=HourScrubSkyColors.mix(a,b,k)
 private fun color(c:DoubleArray,alpha:Double=1.0)=Color.argb((255*alpha.coerceIn(0.0,1.0)).roundToInt(),(255*c[0]).roundToInt().coerceIn(0,255),(255*c[1]).roundToInt().coerceIn(0,255),(255*c[2]).roundToInt().coerceIn(0,255))
 private fun ink(c:DoubleArray,alpha:Double=1.0):Paint { paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=color(c,alpha);return paint }
 private fun rect(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double,col:DoubleArray,alpha:Double=1.0) { c.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),ink(col,alpha)) }
 private fun clip(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double=0.0) { if(r==0.0)c.clipRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()) else c.clipPath(Path().apply { addRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),Path.Direction.CW) }) }
 private fun text(c:Canvas,s:String,x:Double,cap:Double,size:Double,weight:Int=500,track:Double=0.0,tnum:Boolean=false,col:DoubleArray=dark,alpha:Double=1.0,yInk:Double?=null) {
  val p=Path(art.run(s,size,weight,track,tnum).first);p.offset(x.toFloat(),(cap+.7*size).toFloat());val brush=ink(col,alpha)
  if(yInk!=null)brush.shader=LinearGradient(0f,(yInk-12).toFloat(),0f,(yInk+12).toFloat(),color(col),color(dark),Shader.TileMode.CLAMP)
  c.drawPath(p,brush)
 }
 private fun icon(c:Canvas,name:String,x:Double,y:Double,col:DoubleArray,stroke:Double=0.0,scale:Double=1.0,rotation:Double=0.0) {
  val p=art.icons[name]?:return;val save=c.save();c.translate(x.toFloat(),y.toFloat());c.rotate(rotation.toFloat());c.scale(scale.toFloat(),scale.toFloat());val brush=ink(col);if(stroke>0) { brush.style=Paint.Style.STROKE;brush.strokeWidth=stroke.toFloat();brush.strokeCap=Paint.Cap.ROUND;brush.strokeJoin=Paint.Join.ROUND };c.drawPath(p,brush);c.restoreToCount(save)
 }
 private fun rim(c:Canvas,r:RectF,radius:Float,sky:HourScrubSkyColors,k:Double) {
  val glow=m.clamp((sky.h-5.5)/.3)*(1-m.clamp((sky.h-6.4)/.3));val base=color(white,m.mix(.6,.25,k));val p=ink(white).apply { style=Paint.Style.STROKE;strokeWidth=1f }
  if(glow>.01) { val sun=m.sun(sky.h);val dx=sun.first-r.centerX();val dy=sun.second-r.centerY();val len=max(hypot(dx,dy),1.0);p.strokeWidth=1.5f;p.shader=LinearGradient((r.centerX()+dx/len*60).toFloat(),(r.centerY()+dy/len*60).toFloat(),(r.centerX()-dx/len*60).toFloat(),(r.centerY()-dy/len*60).toFloat(),color(sky.sun,.8*glow+.6*(1-glow)),base,Shader.TileMode.CLAMP) } else p.color=base
  c.drawRoundRect(r,radius,radius,p)
 }
 fun draw(c:Canvas,e:HourScrubSkyEngine,ch:Double,margin:Double) {
  val f=e.frame();val sc=HourScrubSkyColors(f.h)
  if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated)sky?.draw(c,f,sc,ch,e.reduced,e.hold,e.fingerX,e.fingerY) else fallback(c,f,sc,ch,margin)
  fun ic(i:Int)=mix(dark,light,e.ink[i])
  val yInk=if(e.reduced)if(e.ink[6]>.5)ch+200 else -200.0 else sc.yInk(ch)
  val save=c.save();c.translate(57f,84f);c.scale(e.press[0].toFloat(),e.press[0].toFloat());c.translate(-57f,-84f)
  val chipColor=mix(sc.sky(84.0,ch),white,m.mix(.45,.22,e.ink[1]));rect(c,15.0,63.0,84.0,42.0,21.0,chipColor,.85);c.drawRoundRect(RectF(15f,63f,99f,105f),21f,21f,ink(white,m.mix(.45,.14,e.ink[1])).apply { style=Paint.Style.STROKE;strokeWidth=1f })
  icon(c,"LEAF",28.0,75.0,ic(1),2.7,.75);icon(c,"SLASH",28.0,75.0,chipColor,5.5,.75);icon(c,"SLASH",28.0,75.0,ic(1),2.7,.75);icon(c,"SUN_DISC",59.0,74.0,ic(1),scale=.75);icon(c,"SUN_RAYS",59.0,74.0,ic(1),2.4,.75);icon(c,"CLOUD",59.0,74.0,chipColor,3.4,.75);icon(c,"CLOUD",59.0,74.0,ic(1),scale=.75);c.restoreToCount(save)
  val wordDY=DoubleArray(8);val lineDY=DoubleArray(3);val clips=doubleArrayOf(1.0,1.0,1.0);var cond=1.0
  if(f.rs>0) { cond=-m.inOut(m.clamp(f.rs/.22));for(j in 0..7)wordDY[j]=(1-m.out(m.clamp((f.rs-.06*j)/.24)))*40 }
  else if(f.cs>0) { val a=m.inOut(m.clamp(f.cs/.22));for(j in 0..2)wordDY[j]=-46*a;cond=1-a;for(i in 1..2) { val k=m.inOut(m.clamp((f.cs-.04*i)/.22));lineDY[i]=-14*k;clips[i]=1-k } }
  for(line in 0..2) { val ss=c.save();clip(c,0.0,150+46*line-12+lineDY[line],402.0,max(0.0,46*clips[line]))
   for(j in 0..7)if(art.lineOf[j]==line)text(c,art.words[j],art.wordX[j],150+46*line+lineDY[line]+wordDY[j],34.0,500,-.02,col=light,yInk=yInk)
   if(line==0 && cond<1) { val rolls=m.conditionRoll(f.h);for(i in 0..2)if(rolls[i]<=2) { val dy=(cond+rolls[i])*46;if(abs(dy)<46)text(c,art.conditions[i],22.0,150+dy,34.0,500,-.02,col=light,yInk=yInk) } };c.restoreToCount(ss)
  }
  fun roll(fahrenheit:Boolean):Double { val q=f.h*2;val j=floor(q);val k=m.clamp((q-j-.3)/.4);fun read(n:Double):Double { val t=m.temp(n/2);return floor((if(fahrenheit)t*1.8+32 else t)+.5) };val a=read(j);return a+(read(j+1)-a)*k*k*(3-2*k) }
  val position=m.mix(roll(false),roll(true),e.unit);val tens=floor(position/10)+m.clamp((position%10)-9);val ones=position%10;val pitch=.58*106;val x0=22-.05*106;val win=.7*106+20
  for((i,p)in listOf(tens,ones).withIndex()) { val x=x0+pitch*i;val base=floor(p).toInt();val ss=c.save();clip(c,x-6,539.0,pitch+10,win);for(d in base-1..base+1) { val dy=(d-p)*win;if(abs(dy)<win)text(c,(((d%10)+10)%10).toString(),x,549+dy,106.0,300,-.04,true,rgb(0xf3ecc0),yInk=yInk) };c.restoreToCount(ss) }
  c.drawCircle((x0+pitch*2+.02*106+11).toFloat(),560f,9f,ink(rgb(0xf3ecc0)).apply { style=Paint.Style.STROKE;strokeWidth=4f;shader=LinearGradient(0f,(yInk-12).toFloat(),0f,(yInk+12).toFloat(),color(rgb(0xf3ecc0)),color(dark),Shader.TileMode.CLAMP) })
  val asave=c.save();c.translate(346f,605f);c.scale(e.press[1].toFloat(),e.press[1].toFloat());c.translate(-346f,-605f)
  val arrow=RectF(313f,583f,379f,627f);c.drawRoundRect(arrow,22f,22f,ink(mix(white,glassN,e.ink[2]),m.mix(.9,.82,e.ink[2])).apply { setShadowLayer(4f,0f,2f,color(doubleArrayOf(.04,.06,.12),m.mix(.1,.3,e.ink[2]))) });rim(c,arrow,22f,sc,e.ink[2]);icon(c,"ARROW",346.0,605.0,ic(2),2.0);c.restoreToCount(asave)
  icon(c,"NAV",22.0,645.0,ic(3),scale=.55);val rolls=m.conditionRoll(f.h);val ci=(0..2).minBy { abs(rolls[it]) };text(c,"Port Ansel  ·  "+art.conditions[ci],41.0,646.0,15.0,col=ic(3));text(c,"H 30° · L 22° · 56% Humidity · UV 6",22.0,670.5,13.0,400,tnum=true,col=ic(4),alpha=.8)
  val px=80+289*f.hp/24;val tickPitch=289.0/48;val show=m.clamp(f.pill*4);val base=mix(dark,white,e.ink[6])
  for(i in 0..48) { val x=80+tickPitch*i;val d=abs(x-px)/(4*tickPitch);val w=if(d<1)(.5+.5*cos(PI*d))*show else 0.0;val h=m.mix(20.0,28.0,w);rect(c,x-1,756-h,2.0,h,1.0,mix(base,red,w),m.mix(m.mix(.32,.4,e.ink[6]),1.0,w)) }
  if(f.pill>.001) { val k=f.pill;val index=floor(f.hp+.5).toInt().coerceIn(0,23);val label=m.pillLabel(index);val width=max(60.0,art.run(label,13.0,600,tnum=true).second+26);val x=m.mix(px-1,(px-width/2).coerceIn(10.0,392-width),k);val y=m.mix(728.0,690.0,min(k,1.0));val w=m.mix(2.0,width,k);val h=m.mix(28.0,30.0,min(k,1.0));val r=minOf(m.mix(1.0,15.0,m.clamp(k)),h/2,w/2);val kn=e.ink[5]
   rect(c,x,y,max(w,.5),max(h,.5),r,mix(red,mix(pill,white,kn),f.pillDark));val ss=c.save();clip(c,x,y,max(w,.5),max(h,.5),r);text(c,label,x+w/2-art.run(label,13.0,600,tnum=true).second/2,700.4,13.0,600,tnum=true,col=mix(white,dark,kn));c.restoreToCount(ss)
  }
  val control=e.ink[6];c.drawCircle(39f,745f,(16*f.disc).toFloat(),ink(mix(pill,white,control)));val glyph=mix(white,pill,control)
  if(f.gPlay>.01)icon(c,"PLAY",39.0,745.0,glyph,scale=f.gPlay*f.disc);if(f.gPause>.01)icon(c,"PAUSE",39.0,745.0,glyph,scale=f.gPause*f.disc);if(f.gReset>.01)icon(c,"RESET",39.0,745.0,glyph,2.0,f.gReset*f.disc,f.resetRot)
  val ts=c.save();val scale=e.press[2];c.translate(201f,825f);c.scale(scale.toFloat(),scale.toFloat());c.translate(-201f,-825f);val tk=e.ink[7]
  val ss=c.save();clip(c,12.0,792.0,378.0,66.0,33.0);if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated && sky!=null)backdrop?.draw(c,sky,f,sc,ch,e);c.restoreToCount(ss)
  rect(c,12.0,792.0,378.0,66.0,33.0,mix(white,glassN,tk),m.mix(.72,.7,tk));rim(c,RectF(12f,792f,390f,858f),33f,sc,tk);rect(c,16.0,798.0,86.0,54.0,27.0,mix(rgb(0xe4e8ee),rgb(0x2c3140),tk),m.mix(1.0,.9,tk))
  for(i in 0..3) { val x=doubleArrayOf(59.0,153.0,247.0,341.0)[i];val col=if(i==0)mix(rgb(0x1f6feb),rgb(0x5b9bff),tk) else ic(7);icon(c,listOf("NAV","MAP","TROPHY","MORE")[i],x-12,803.0,col);if(i==2)icon(c,"TROPHY_HANDLES",x-12,803.0,col,2.0);val label=listOf("Nearby","Cities","Rankings","More")[i];text(c,label,x-art.run(label,13.0).second/2,836.5,13.0,col=col) };c.restoreToCount(ts)
 }
 private fun fallback(c:Canvas,f:HourScrubSkyFrame,sc:HourScrubSkyColors,ch:Double,margin:Double) {
  val stops=DoubleArray(4) { i->doubleArrayOf(0.0,.35,.62,.8)[i]*ch };val colors=IntArray(4) { color(sc.sky(stops[it],ch)) }
  c.drawRect((-margin).toFloat(),0f,(402+margin).toFloat(),ch.toFloat(),ink(white).apply { shader=LinearGradient(0f,0f,0f,ch.toFloat(),colors,floatArrayOf(0f,.35f,.62f,.8f),Shader.TileMode.CLAMP) })
  val sun=m.sun(f.h);if(sun.second<630) { c.drawCircle(sun.first.toFloat(),sun.second.toFloat(),36f,ink(sc.sun).apply { setShadowLayer(30f,0f,0f,color(sc.sun,.5)) }) }
  val night=1-m.smooth(sc.front-sc.width,sc.front+sc.width,200.0)
  for((i,bitmap)in listOf(art.low,art.high).withIndex()) { val scale=if(i==0)bitmap.height/ch else bitmap.height/600.0;val width=bitmap.width/scale;val off=f.h/24*width+(if(i==0)10 else 6)*sin(2*PI*f.sway/12+if(i==0)0.0 else 1.3)
   val shader=BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);shader.setLocalMatrix(Matrix().apply { setScale((1/scale).toFloat(),(1/scale).toFloat());postTranslate((-off).toFloat(),0f) })
   val tint=mix(mix(sc.cloud[1],sc.cloud[0],.65),rgb(0x3c4357),night);val p=ink(tint);p.shader=shader;p.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f,0f,0f,0f,(tint[0]*255).toFloat(),0f,0f,0f,0f,(tint[1]*255).toFloat(),0f,0f,0f,0f,(tint[2]*255).toFloat(),if(i==0).8f else .6f,0f,0f,0f,0f)))
   c.drawRect((-margin).toFloat(),0f,(402+margin).toFloat(),if(i==0)ch.toFloat() else 560f,p)
  }
 }
}
