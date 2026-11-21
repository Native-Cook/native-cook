package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class RaceGlobeCalendarRenderer(private val art:RaceGlobeCalendarArtwork) {
 private val m=RaceGlobeCalendarMotion;private val d=RaceGlobeCalendarData
 private val shader=if(Build.VERSION.SDK_INT>=33)RaceGlobeCalendarShader(art.images) else null
 private val cpu by lazy { RaceGlobeCalendarFallback(art.images) }
 private val backdrop=if(Build.VERSION.SDK_INT>=33)RaceGlobeCalendarBackdrop() else null
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private var height=874.0;private var pixel=1/3.0;private var current=RaceGlobeCalendarFrame()
 private fun ink(color:Int,alpha:Double=1.0):Paint { paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=color;paint.alpha=(255*alpha.coerceIn(0.0,1.0)).roundToInt();return paint }
 private fun circle(c:Canvas,x:Double,y:Double,r:Double,col:Int,a:Double=1.0) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),ink(col,a)) }
 private fun path(points:List<PointF>,closed:Boolean=false)=Path().apply { if(points.isNotEmpty()) { moveTo(points[0].x,points[0].y);for(p in points.drop(1))lineTo(p.x,p.y);if(closed)close() } }
 private fun point(x:Double,y:Double)=PointF(x.toFloat(),y.toFloat())
 private fun text(c:Canvas,s:String,x:Double,y:Double,size:Double,face:String="regular",alpha:Double=1.0) { val p=Path(art.run(s,size,face).first);p.offset(x.toFloat(),y.toFloat());c.drawPath(p,ink(Color.WHITE,alpha)) }
 private fun rounded(c:Canvas,r:RectF,radius:Float) { c.clipPath(Path().apply { addRoundRect(r,radius,radius,Path.Direction.CW) }) }
 private fun glass(c:Canvas,r:RectF,radius:Float,blur:Float,top:Int,at:Double,bottom:Int,ab:Double,alpha:Double=1.0) {
  val save=c.saveLayer(null,Paint().apply { this.alpha=(255*alpha.coerceIn(0.0,1.0)).roundToInt() });val ss=c.save();rounded(c,r,radius)
  if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated && shader!=null)backdrop?.draw(c,shader,current,height,pixel,r,blur)
  val ca=(top and 0xffffff) or ((255*at).roundToInt() shl 24);val cb=(bottom and 0xffffff) or ((255*ab).roundToInt() shl 24)
  c.drawRect(r,ink(Color.WHITE).apply { this.shader=LinearGradient(0f,r.top,0f,r.bottom,ca,cb,Shader.TileMode.CLAMP) });c.restoreToCount(ss)
  c.drawRoundRect(RectF(r.left+.5f,r.top+.5f,r.right-.5f,r.bottom-.5f),radius-.5f,radius-.5f,ink(Color.WHITE).apply { style=Paint.Style.STROKE;strokeWidth=1f;this.shader=LinearGradient(0f,r.top,0f,r.bottom,0x33ffffff,0x0dffffff,Shader.TileMode.CLAMP) });c.restoreToCount(save)
 }
 fun draw(c:Canvas,f:RaceGlobeCalendarFrame,ch:Double,px:Double) {
  current=f;height=ch;pixel=px;if(Build.VERSION.SDK_INT>=33)backdrop?.begin()
  c.drawColor(0xff020308.toInt());for(s in art.stars)circle(c,s[0],s[1],s[2],Color.WHITE,s[3]*(.8+.2*sin(2*PI*4*f.t/10.5+if(s[4]==0.0)0.0 else 2.1)))
  if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated)shader?.draw(c,f,ch,px) else fallback(c,f)
  val rows=m.rows(f.lat,f.lon);val radius=300*f.zoom;val projections=d.W.map { m.project(rows,it,radius) };arcs(c,f,rows,radius)
  val lnd=m.landing(f.land)
  if(lnd[2]>=0 && f.from!=f.to && projections[f.to][3]>.5) { c.drawPath(ring(d.W[f.to],34*(1-(1-lnd[2]).pow(2))/radius,rows,radius),ink(Color.WHITE,.5*(1-lnd[2])).apply { style=Paint.Style.STROKE;strokeWidth=2f });if(lnd[2]>.15)c.drawPath(ring(d.W[f.to],22*(1-(1-(lnd[2]-.15)/.85).pow(2))/radius,rows,radius),ink(0xffb9bcff.toInt(),.35*(1-lnd[2])).apply { style=Paint.Style.STROKE;strokeWidth=1.5f }) }
  val pins=mutableListOf<RaceGlobeCalendarPin>()
  for(i in 0..23) { val q=projections[i];val a=m.smoothstep(.25,.48,q[2]);if(q[3]<.5 || a<=.01)continue
   val num=m.clamp((2.5-abs(i-f.selF))*14)*f.numbered;var sel=0.0;if(i==f.to && f.land>=0)sel=1.0;if(i==f.from && f.from!=f.to)sel=max(sel,1-f.leave)
   if(num<=.001 && sel<=.001) { circle(c,q[0],q[1],3.6,0xff7f83ff.toInt(),.9);circle(c,q[0],q[1],3.0,0xff6e72f2.toInt());continue }
   var lift=0.0;var grow=sel;if(i==f.to && f.from!=f.to) { lift=if(f.land<0)10*m.smooth((f.land+.3)/.3) else -lnd[0];grow=if(f.land<0)0.0 else if(lnd[1]>0)(lnd[1]-.6)/.4 else 0.0 }
   val k=if(i==f.to && f.from!=f.to && f.land>=0)max(num,min(grow,1.0)) else max(num,if(sel>0)1.0 else 0.0);lift*=k;val r=m.mix(3.0,m.mix(15.0,17.0,grow),k)
   pins.add(RaceGlobeCalendarPin(i,q[0],q[1],q[0],q[1]-6*k-r-lift,r,q[2],grow,k,lift,a))
  }
  repeat(4) { for(p in pins.indices)for(q in p+1 until pins.size) { val a=pins[p];val b=pins[q];val dx=b.hx-a.hx;val dy=b.hy-a.hy;val dd=max(hypot(dx,dy),.01);val need=a.r+b.r+4;if(dd>=need)continue;val push=need-dd;val wa=if(a.sel>b.sel)0.0 else if(b.sel>a.sel)1.0 else .5;a.hx-=dx/dd*push*wa;a.hy-=dy/dd*push*wa;b.hx+=dx/dd*push*(1-wa);b.hy+=dy/dd*push*(1-wa) } }
  pins.sortWith(compareBy<RaceGlobeCalendarPin> { it.sel }.thenBy { it.z })
  for(p in pins) {
   c.drawOval(RectF((p.x-6-p.lift*.3).toFloat(),(p.y-2).toFloat(),(p.x+6+p.lift*.3).toFloat(),(p.y+2).toFloat()),ink(Color.BLACK,.35*p.a*m.clamp(1-p.lift/16)*p.num).apply { maskFilter=BlurMaskFilter(2.2f,BlurMaskFilter.Blur.NORMAL) })
   c.drawPath(path(listOf(point(p.hx-p.r*.5,p.hy+p.r*.55),point(p.x,p.y-p.lift),point(p.hx+p.r*.5,p.hy+p.r*.55)),true),ink(0xff4b4fe0.toInt(),p.a));if(p.sel>.01)circle(c,p.hx,p.hy,p.r+2.4,Color.WHITE,p.a*p.sel)
   c.drawCircle(p.hx.toFloat(),p.hy.toFloat(),p.r.toFloat(),ink(Color.WHITE,p.a).apply { this.shader=LinearGradient(p.hx.toFloat(),(p.hy-p.r).toFloat(),p.hx.toFloat(),(p.hy+p.r).toFloat(),0xff5c60f0.toInt(),0xff3e42cf.toInt(),Shader.TileMode.CLAMP) })
   c.drawCircle(p.hx.toFloat(),p.hy.toFloat(),(p.r-.5).toFloat(),ink(0xff7f83ff.toInt(),p.a*.95).apply { style=Paint.Style.STROKE;strokeWidth=1f })
   if(p.r>10.5) { val ss=c.save();c.translate(p.hx.toFloat(),p.hy.toFloat());c.scale((p.r/15).toFloat(),(p.r/15).toFloat());val s=(p.i+1).toString();text(c,s,-art.run(s,13.0,"bold").second/2,4.7,13.0,"bold",p.a);c.restoreToCount(ss) }
  }
  val k=lnd[3]*(1-f.calloutOut);pins.firstOrNull { it.i==f.to }?.let { if(k>.005)callout(c,f,it,k,lnd[4]) }
  val bs=c.save();val scale=1-.1*f.shuffle;c.translate(41f,90f);c.scale(scale.toFloat(),scale.toFloat());c.translate(-41f,-90f);glass(c,RectF(19f,68f,63f,112f),22f,20f,0xff22262e.toInt(),.55,0xff16191f.toInt(),.62)
  c.translate(41f,90f);c.rotate(f.shuffleRot.toFloat());c.scale(20/24f,20/24f);c.translate(-12f,-12f);c.drawPath(art.icons.getValue("SHUFFLE"),ink(Color.WHITE).apply { style=Paint.Style.STROKE;strokeWidth=2.4f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND });c.restoreToCount(bs)
  val first=floor(f.cardPos).toInt()-1;val focus=floor(f.cardPos+.5).toInt()
  for(slot in first..first+3) { val i=if(f.track==0)m.autoTrack[((slot%8)+8)%8] else slot;if(i !in 0..23)continue;val x=24+(slot-f.cardPos)*366;if(x < -358 || x>406)continue;val sheen=if(f.track==0)if(slot==f.sheenSlot)f.sheen else -1.0 else if(slot==focus)f.sheen else -1.0;card(c,f,x,ch-224,i,if(slot==focus)1.0 else 0.0,sheen) }
 }
 private fun callout(c:Canvas,f:RaceGlobeCalendarFrame,p:RaceGlobeCalendarPin,k:Double,circuit:Double) {
  val i=f.to;val w=12+max(art.run(art.titles[i],15.0,"semibold").second,art.run(art.wheres[i],13.0).second)+54;val x=(p.hx-w/2).coerceIn(16.0,386-w);val bottom=p.hy-p.r-2.4-9;val sink=f.calloutOut*6;val s=m.mix(.72,1.0,k);val a=m.clamp(k*2.2)*p.a;val y=bottom-54+sink
  val ss=c.save();c.translate(p.hx.toFloat(),(bottom+sink).toFloat());c.scale(s.toFloat(),s.toFloat());c.translate(-p.hx.toFloat(),-(bottom+sink).toFloat());glass(c,RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+54).toFloat()),12f,16f,0xff14161a.toInt(),.72,0xff0e1014.toInt(),.8,a)
  val px=p.hx.coerceIn(x+16,x+w-16);c.drawPath(path(listOf(point(px-6,y+53.5),point(px,y+60),point(px+6,y+53.5)),true),ink(0xff14161a.toInt(),.8*a));text(c,art.titles[i],x+12,y+23+(1-k)*4,15.0,"semibold",a);text(c,art.wheres[i],x+12,y+42+(1-k)*4,13.0,alpha=.58*a)
  val cx=x+w-42;val cy=y+12;c.drawRoundRect(RectF(cx.toFloat(),cy.toFloat(),(cx+30).toFloat(),(cy+30).toFloat()),8f,8f,ink(0xff2a2d33.toInt(),a));val ps=c.save();c.translate((cx+3).toFloat(),(cy+3).toFloat());c.scale(24/28f,24/28f)
  val progress=if(f.pulse>=0)m.out(f.pulse/.45) else circuit;val measure=PathMeasure(art.circuits[i],false);val trim=Path();measure.getSegment(0f,(measure.length*max(.001,progress)).toFloat(),trim,true);c.drawPath(trim,ink(Color.WHITE,.92*a).apply { style=Paint.Style.STROKE;strokeWidth=2.3f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND });c.restoreToCount(ps);c.restoreToCount(ss)
 }
 private fun card(c:Canvas,f:RaceGlobeCalendarFrame,x:Double,y:Double,i:Int,focus:Double,sheen:Double) {
  val scale=1-.02*f.press*focus;val ss=c.save();c.translate((x+177).toFloat(),(y+64).toFloat());c.scale(scale.toFloat(),scale.toFloat());c.translate((-x-177).toFloat(),(-y-64).toFloat());val rect=RectF(x.toFloat(),y.toFloat(),(x+354).toFloat(),(y+128).toFloat());glass(c,rect,28f,28f,0xff10141a.toInt(),.6,0xff0b0e13.toInt(),.78);rounded(c,rect,28f)
  if(sheen>=0 && sheen<=1 && i>14) { val bx=x+m.mix(-90.0,444.0,sheen);val color=if(d.hours[i]>=19)0x00ffb25a else 0x003e7bff;val peak=((255*.22*sin(PI*sheen)).roundToInt() shl 24) or color;c.drawRect(rect,ink(Color.WHITE).apply { this.shader=LinearGradient((bx-70).toFloat(),y.toFloat(),(bx+70).toFloat(),(y+50).toFloat(),intArrayOf(color,peak,color),null,Shader.TileMode.CLAMP);xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD) }) }
  val cw=43+art.run(art.chips[i],13.0,"semibold").second;c.drawRoundRect(RectF((x+17).toFloat(),(y+17).toFloat(),(x+17+cw).toFloat(),(y+43).toFloat()),13f,13f,ink(Color.WHITE,.1))
  val icon=c.save();c.translate((x+27).toFloat(),(y+22.5).toFloat());c.scale(15/24f,15/24f);c.drawPath(art.icons.getValue(if(i<=14)"TROPHY" else "FLAG"),ink(Color.WHITE,.95).apply { style=Paint.Style.STROKE;strokeWidth=3f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND });c.restoreToCount(icon)
  text(c,art.chips[i],x+48,y+34.6,13.0,"semibold",.95);text(c,art.titles[i],x+17,y+82,24.0,"display_semibold");text(c,art.pre[i],x+17,y+108,14.0,alpha=.62)
  val minutes=(d.hours[i]+f.hour*focus)*60;val st=minutes/5;val si=floor(st+1e-6);val roll=m.smoothstep(.6,1.0,st-si)
  fun hhmm(minutes:Double):String { val value=((floor(minutes+.5).toInt()%1440)+1440)%1440;return "%02d:%02d".format(java.util.Locale.US,value/60,value%60) }
  val cur=hhmm(si*5);val next=hhmm(si*5+5);val digitWidth=(0..9).maxOf { art.run(it.toString(),14.0).second };val colon=art.run(":",14.0).second;var cx=x+17+art.run(art.pre[i],14.0).second;val ds=c.save();c.clipRect(x.toFloat(),(y+93).toFloat(),(x+354).toFloat(),(y+113).toFloat())
  for(p in 0..4) { val width=if(p==2)colon else digitWidth;val a=cur[p].toString();val b=next[p].toString();val wa=art.run(a,14.0).second;if(roll>.001 && a!=b) { text(c,a,cx+(width-wa)/2,y+108-roll*17,14.0,alpha=.62);text(c,b,cx+(width-art.run(b,14.0).second)/2,y+108+(1-roll)*17,14.0,alpha=.62) } else text(c,a,cx+(width-wa)/2,y+108,14.0,alpha=.62);cx+=width };c.restoreToCount(ds);text(c,art.suf[i],cx,y+108,14.0,alpha=.62);c.restoreToCount(ss)
 }
 private fun ring(w:DoubleArray,a:Double,rows:DoubleArray,r:Double):Path { val l=max(hypot(w[2],w[0]),1e-9);val e1=doubleArrayOf(w[2]/l,0.0,-w[0]/l);val e2=doubleArrayOf(w[1]*e1[2],w[2]*e1[0]-w[0]*e1[2],-w[1]*e1[0]);return path((0..48).map { k->val theta=k/48.0*PI*2;val ct=cos(theta)*sin(a);val st=sin(theta)*sin(a);val p=DoubleArray(3) { w[it]*cos(a)+e1[it]*ct+e2[it]*st };val q=m.project(rows,p,r);point(q[0],q[1]) }) }
 private fun arcs(c:Canvas,f:RaceGlobeCalendarFrame,rows:DoubleArray,r:Double) {
  val first=max(0,floor(f.arcPos).toInt()-9);val last=min(22,floor(f.arcPos).toInt());if(first>last)return
  for(j in first..last) { val progress=m.clamp(f.arcPos-j);if(progress<=0)continue;val alpha=m.mix(.35,.92,1-m.clamp((f.arcPos-(j+1))*2.5));val end=progress*40;val kEnd=ceil(end).toInt();val points=mutableListOf<PointF>();val vis=mutableListOf<Double>()
   for(k in 0..kEnd) { var p=art.arcs[j][k];if(k==kEnd && kEnd>end) { val fraction=end-(k-1);p=DoubleArray(3) { m.mix(art.arcs[j][k-1][it],p[it],fraction) } };val q=m.project(rows,p,r);points.add(point(q[0],q[1]));vis.add(q[3]*m.smoothstep(.25,.48,q[2]/sqrt(m.dot(p,p)))) }
   var run=Path();var runA = -1.0;var open=false;var firstRun=true
   fun flush() { if(open) { c.drawPath(run,ink(Color.WHITE,alpha*runA).apply { style=Paint.Style.STROKE;strokeWidth=3f;strokeCap=if(firstRun)Paint.Cap.ROUND else Paint.Cap.BUTT;strokeJoin=Paint.Join.ROUND });firstRun=false } }
   for(k in 0 until kEnd) { val a=min(vis[k],vis[k+1]);val qa=if(a<=.001)0.0 else floor(a*20+.5)/20;if(open && qa!=runA) { flush();run=Path();open=false };if(qa<=0)continue;if(!open) { run.moveTo(points[k].x,points[k].y);open=true;runA=qa };run.lineTo(points[k+1].x,points[k+1].y) };flush()
   if(progress>=.6 && vis[22]>.001 && vis[23]>.001) { val dx=(points[23].x-points[21].x).toDouble();val dy=(points[23].y-points[21].y).toDouble();val l=max(hypot(dx,dy),1e-9);val ux=dx/l;val uy=dy/l;val h=points[22];c.drawPath(path(listOf(point(h.x+ux*5,h.y+uy*5),point(h.x-ux*5-uy*4.5,h.y-uy*5+ux*4.5),point(h.x-ux*2.6,h.y-uy*2.6),point(h.x-ux*5+uy*4.5,h.y-uy*5-ux*4.5)),true),ink(Color.WHITE,alpha*vis[22])) }
   if(f.comet>.5 && progress>0 && progress<1 && vis[kEnd]>.001) { val head=points[kEnd];val tail=Path();tail.moveTo(head.x,head.y);var length=0.0;var ex=head.x.toDouble();var ey=head.y.toDouble()
    for(k in kEnd-1 downTo 0) { if(vis[k]<=.001 || length>=60)break;val dx=points[k].x-ex;val dy=points[k].y-ey;val l=hypot(dx,dy);val ratio=min(1.0,(60-length)/max(l,1e-9));ex+=dx*ratio;ey+=dy*ratio;length+=l*ratio;tail.lineTo(ex.toFloat(),ey.toFloat()) }
    val a=if(progress<.06)progress/.06 else 1.0;if(length>2)c.drawPath(tail,ink(Color.WHITE,a).apply { style=Paint.Style.STROKE;strokeWidth=6f;strokeCap=Paint.Cap.ROUND;this.shader=LinearGradient(head.x,head.y,ex.toFloat(),ey.toFloat(),0xd9ffffff.toInt(),0x00bed7ff,Shader.TileMode.CLAMP);maskFilter=BlurMaskFilter(2.2f,BlurMaskFilter.Blur.NORMAL) })
    c.drawCircle(head.x,head.y,11f,ink(Color.WHITE,a).apply { this.shader=RadialGradient(head.x,head.y,11f,intArrayOf(Color.WHITE,0x8ccde2ff.toInt(),0x0096beff),floatArrayOf(0f,.3f,1f),Shader.TileMode.CLAMP) });circle(c,head.x.toDouble(),head.y.toDouble(),3.4,Color.WHITE,a)
   }
  }
 }
 private fun fallback(c:Canvas,f:RaceGlobeCalendarFrame) { cpu.draw(c,f) }
}
