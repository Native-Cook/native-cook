package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.graphics.*
import kotlin.math.*

/** API26–32 counterpart: the same perspective ray/sphere projection on a small cached CPU surface. */
internal class RaceGlobeCalendarFallback(images:List<Bitmap>) {
 private val m=RaceGlobeCalendarMotion
 private val textures=images.mapIndexed { i,b -> val width=if(i==3)512 else 1024;Bitmap.createScaledBitmap(b,width,width/2,true) }
 private val pixels=textures.map { b->IntArray(b.width*b.height).also { b.getPixels(it,0,b.width,0,0,b.width,b.height) } }
 private val bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);private val output=IntArray(192*192);private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private var key=""
 private fun enc(c:Double):Int { val v=c.coerceIn(0.0,1.0);return ((if(v<=.0031308)v*12.92 else 1.055*v.pow(1/2.4)-.055)*255).roundToInt().coerceIn(0,255) }
 private fun lin(c:Double):Double { val v=c.coerceIn(0.0,1.0);return if(v<=.04045)v/12.92 else ((v+.055)/1.055).pow(2.4) }
 private fun sample(i:Int,u:Double,v:Double):DoubleArray {
  val b=textures[i];val x=((u-floor(u))*b.width-.5);val y=(v*b.height-.5).coerceIn(0.0,b.height-1.0);val fx=floor(x).toInt();val fy=floor(y).toInt();val ax=x-floor(x);val ay=y-fy
  fun p(x:Int,y:Int)=pixels[i][y*b.width+((x%b.width)+b.width)%b.width]
  val a=p(fx,fy);val bb=p(fx+1,fy);val c=p(fx,min(fy+1,b.height-1));val d=p(fx+1,min(fy+1,b.height-1))
  return DoubleArray(3) { channel->
   val shift=16-channel*8
   fun value(n:Int):Double { return ((n ushr shift)and 255)/255.0 }
   m.mix(m.mix(value(a),value(bb),ax),m.mix(value(c),value(d),ax),ay) }
 }
 fun draw(c:Canvas,f:RaceGlobeCalendarFrame) {
  val state="${floor(f.lat*8)}|${floor(f.lon*8)}|${f.sun.joinToString { (it*400).roundToInt().toString() }}|${floor(f.t*8)}"
  if(state!=key) { key=state;render(f) }
  val r=300*f.zoom;val rect=RectF((201-r).toFloat(),(372-r).toFloat(),(201+r).toFloat(),(372+r).toFloat())
  c.drawCircle(201f,372f,r.toFloat(),Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xff06152b.toInt();setShadowLayer((r/50).toFloat(),0f,0f,0x996fb4ff.toInt()) });c.drawBitmap(bitmap,null,rect,paint)
 }
 private fun render(f:RaceGlobeCalendarFrame) {
  val rows=m.rows(f.lat,f.lon);val lamp=doubleArrayOf(.9451,.6863,.3608);val moon=doubleArrayOf(.0549,.1020,.1804);val yw=doubleArrayOf(.2126,.7152,.0722)
  fun dot(a:DoubleArray,b:DoubleArray)=m.dot(a,b)
  fun amp(color:DoubleArray)=max(0.0,(dot(color,yw)-dot(moon,yw))/(dot(lamp,yw)-dot(moon,yw)))
  for(y in 0..191)for(x in 0..191) {
   val ox=(x+.5-96)/96;val oy=(y+.5-96)/96;val rho=ox*ox+oy*oy;val index=y*192+x;if(rho>1) { output[index]=0;continue }
   val rd=m.norm(doubleArrayOf(ox/sqrt(15.0),-oy/sqrt(15.0),-1.0));val b=4*rd[2];val t=-b-sqrt(max(1-(16-b*b),0.0));val nv=m.norm(doubleArrayOf(t*rd[0],t*rd[1],4+t*rd[2]));val ev=m.norm(doubleArrayOf(-nv[0],-nv[1],4-nv[2]))
   fun world(v:DoubleArray)=DoubleArray(3) { v[0]*rows[it]+v[1]*rows[it+3]+v[2]*rows[it+6] }
   val nw=world(nv);val ew=world(ev);val u=atan2(nw[0],nw[2])/(2*PI)+.5;val v=.5-asin(nw[1].coerceIn(-1.0,1.0)).coerceIn(-1.4835,1.4835)/PI
   val day=sample(0,u,v);val night=sample(1,u,v);val water=sample(2,u,v)[0];val glow=sample(3,u,v)
   val ndl=dot(nw,f.sun);val lit=m.smoothstep(-.1,.12,ndl);val view=dot(nv,ev).coerceIn(0.0,1.0);val shade=view.pow(1.4)
   val amount=1.65*((ndl+.06)/1.06).coerceIn(0.0,1.0).pow(.62)*m.smoothstep(-.06,.1,ndl);val wa=exp(-((ndl-.04)/.045).pow(2));val wr=exp(-((ndl+.05)/.045).pow(2))
   val a=amp(night);val ag=amp(glow);val on=m.smoothstep(0.0,.06,-ndl-.045);val twinkle=1+.05*sin(2*PI*7*f.t/10.5+PI)
   val h=m.norm(DoubleArray(3) { f.sun[it]+ew[it] });val nh=max(dot(nw,h),0.0);val glint=water*m.smoothstep(0.0,.25,ndl)*(.55*nh.pow(420)+.022*nh.pow(22))
   val fresnel=(1-view).pow(3.2);val litRim=m.smoothstep(-.3,.4,ndl)
   val result=IntArray(3) { i->
    val albedo=lin(day[i]);var dc=albedo*amount;dc=dc*m.mix(1.0,doubleArrayOf(1.2,.9,.7)[i],.55*wa)+lin(doubleArrayOf(1.0,.72,.52)[i])*.024*wa*shade
    val floor=lin(max(night[i]-a*lamp[i],0.0));val lamps=lin(lamp[i])*(a*2.2+ag*.9)*on*twinkle
    val nc=floor*.75+albedo*.075+lin(doubleArrayOf(.02,.027,.051)[i])+lamps
    var col=m.mix(nc,dc,lit)+lin(doubleArrayOf(.8,.58,.74)[i])*.015*wr*shade
    col+=m.mix(doubleArrayOf(1.0,.86,.66)[i],doubleArrayOf(.55,.75,1.0)[i],.35)*glint*(.6+.4*ndl.coerceIn(0.0,1.0))
    col=m.mix(col,lin(doubleArrayOf(.435,.706,1.0)[i])*m.mix(.25,1.0,litRim),fresnel*m.mix(.12,.70,litRim));enc(col)
   }
   output[index]=Color.rgb(result[0],result[1],result[2])
  }
  bitmap.setPixels(output,0,192,0,0,192,192)
 }
}
