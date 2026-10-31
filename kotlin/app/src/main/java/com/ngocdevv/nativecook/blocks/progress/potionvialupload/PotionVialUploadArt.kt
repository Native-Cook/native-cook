package com.ngocdevv.nativecook.blocks.progress.potionvialupload

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class PotionVialUploadArt(context:Context) {
    private val m=PotionVialUploadMotion
    private val p=Paint().apply { strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private val type=Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText=true }
    private val fonts=mapOf("Regular" to face(context,"Regular"),"Medium" to face(context,"Medium"),"Bold" to face(context,"Bold"))
    private val file=context.assets.open("potionvialupload/potionvialupload_pvu-file.png").use { BitmapFactory.decodeStream(it) }
    private var layers=emptyList<Bitmap>(); private var canvases=emptyList<Canvas>(); private var inputs=emptyList<BitmapShader>()
    private var runtime: PotionVialUploadRuntime?=null
    private var lw=0; private var lh=0
    private val page="#0a080d"; private val ink="#f2f1ec"; private val mute="#8b8a93"; private val done="#9be27a"
    private val outer=outline(0.0); private val inner=outline(2.6)
    private val bands=listOf(Triple(doubleArrayOf(311.0,320.0,15.0),false,2.2),Triple(doubleArrayOf(320.0,327.0,19.0),true,3.4),Triple(doubleArrayOf(327.0,337.0,15.0),false,2.2))
    private val gradients= listOf(intArrayOf(Color.parseColor("#ffd277"),Color.parseColor("#e9a944"),Color.parseColor("#e9a944"),Color.parseColor("#a35424")),intArrayOf(Color.parseColor("#d4d0dc"),Color.parseColor("#918d9b"),Color.parseColor("#918d9b"),Color.parseColor("#55525e")))
    private val cubeFaces=arrayOf(
        doubleArrayOf(0.0,0.0,1.0,-1.0,-1.0,1.0,1.0,-1.0,1.0,1.0,1.0,1.0,-1.0,1.0,1.0),
        doubleArrayOf(0.0,0.0,-1.0,1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,1.0,-1.0,1.0,1.0,-1.0),
        doubleArrayOf(1.0,0.0,0.0,1.0,-1.0,1.0,1.0,-1.0,-1.0,1.0,1.0,-1.0,1.0,1.0,1.0),
        doubleArrayOf(-1.0,0.0,0.0,-1.0,-1.0,-1.0,-1.0,-1.0,1.0,-1.0,1.0,1.0,-1.0,1.0,-1.0),
        doubleArrayOf(0.0,-1.0,0.0,-1.0,-1.0,-1.0,1.0,-1.0,-1.0,1.0,-1.0,1.0,-1.0,-1.0,1.0),
        doubleArrayOf(0.0,1.0,0.0,-1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,-1.0,-1.0,1.0,-1.0))
    private fun face(context:Context,weight:String)=Typeface.createFromAsset(context.assets,"potionvialupload/potionvialupload_PixelifySans-$weight.ttf")
    private fun paint(col:String,a:Double=1.0,stroke:Double=0.0): Paint {
        p.reset(); p.color=Color.parseColor(col); p.alpha=(a.coerceIn(0.0,1.0)*255).roundToInt(); p.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth=stroke.toFloat(); p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND; return p
    }
    private fun rect(c:Canvas,x:Double,y:Double,w:Double,h:Double,col:String,a:Double=1.0)=c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint(col,a))
    private fun oval(c:Canvas,x:Double,y:Double,w:Double,h:Double,col:String,a:Double=1.0,stroke:Double=0.0)=c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint(col,a,stroke))
    private fun circle(c:Canvas,x:Double,y:Double,r:Double,col:String,a:Double=1.0,stroke:Double=0.0)=c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint(col,a,stroke))
    private fun path(pts:List<Pair<Double,Double>>,close:Boolean=false)=Path().apply { if(pts.isNotEmpty())moveTo(pts[0].first.toFloat(),pts[0].second.toFloat()); pts.drop(1).forEach{ lineTo(it.first.toFloat(),it.second.toFloat()) }; if(close)close() }
    private fun line(c:Canvas,x:Double,y:Double,x1:Double,y1:Double,col:String,w:Double,a:Double=1.0)=c.drawLine(x.toFloat(),y.toFloat(),x1.toFloat(),y1.toFloat(),paint(col,a,w))
    private fun outline(inset:Double):Path {
        val h=23-inset; val nh=11-inset; val x=22+inset
        return Path().apply { moveTo(282f,-h.toFloat()); lineTo((x+h).toFloat(),-h.toFloat()); arcTo(RectF(x.toFloat(),-h.toFloat(),(x+h*2).toFloat(),h.toFloat()),-90f,-180f,false); lineTo(282f,h.toFloat()); cubicTo(295f,h.toFloat(),293f,nh.toFloat(),302f,nh.toFloat()); lineTo(if(inset>0)313f else 311f,nh.toFloat()); lineTo(if(inset>0)313f else 311f,-nh.toFloat()); lineTo(302f,-nh.toFloat()); cubicTo(293f,-nh.toFloat(),295f,-h.toFloat(),282f,-h.toFloat()); close() }
    }
    private fun local(c:Canvas,g:PotionVialUploadGeo) { c.translate(g.ox.toFloat(),g.cy.toFloat()); c.scale(g.s.toFloat(),g.s.toFloat()) }
    private fun stars(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame) {
        repeat(46) { j -> val i=j.toDouble(); val x=m.hash(i*3.1)*g.w; val y=g.top+m.hash(i*7.7+1)*(g.h-g.top-g.bottom); val r=if(m.hash(i*1.3)>0.8)1.1 else 0.7; rect(c,x,y,r,r,"#f3ecd2",0.22) }
        repeat(14) { j ->
            val i=j.toDouble(); val a=m.hash(i*5.3+2)*PI*2; val rr=120+m.hash(i*2.9)*130
            val x=g.ox+(190+cos(a)*rr*1.15)*g.s; val y=g.cy+sin(a)*rr*0.62*g.s; val tw=max(0.0,sin(2*PI*3*f.t/12+m.hash(i)*6.28)).pow(6); val al=tw*(0.25+0.75*f.glow)
            if(al>=0.03) { val arm=if(m.hash(i*9.1)>0.55)4.5 else 2.6; val col=if(m.hash(i*4.4)>0.45)"#f3ecd2" else "#5fe34f"; rect(c,x-arm,y-0.7,arm*2,1.4,col,al); rect(c,x-0.7,y-arm,1.4,arm*2,col,al) }
        }
    }
    // Android converts radius to sigma as 0.57735 * radius + 0.5.
    // Match the reference mask filters' standard deviations, including the gem halo.
    private fun front(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame) {
        c.save(); local(c,g)
        if(f.glow>=0.01)for((sigma,alpha) in listOf(9.0 to 0.95,24.0 to 0.32)) {
            val paint=paint("#4fe02f",alpha*f.glow); paint.maskFilter=BlurMaskFilter(((sigma-0.5)/0.57735).toFloat(),BlurMaskFilter.Blur.NORMAL); c.drawPath(outer,paint)
        }
        c.drawPath(inner,paint("#c4bed2",0.05))
        val paint=paint("#4fe02f",0.3+0.25*f.glow); paint.maskFilter=BlurMaskFilter(((11.0-0.5)/0.57735).toFloat(),BlurMaskFilter.Blur.NORMAL); c.drawCircle(f.gemX.toFloat(),f.gemY.toFloat(),26.4f,paint); c.restore()
    }
    private fun details(c:Canvas,f:PotionVialUploadFrame) {
        c.save(); c.clipPath(inner)
        if(f.cloud>=0 && f.cloudA>0.01) {
            val k=1-(1-f.cloud).pow(2); val hx=m.mix(305.0,30.6,k); val len=m.mix(24.0,230.0,min(1.0,f.cloud*1.4))
            repeat(40) { j -> val i=j.toDouble(); val u=i/39; val x=hx+u*len
                if(x<=313) { val wob=sin(u*9+f.t*14+i)*3.5*u; val r=m.mix(8.0,5.0,u)+sin(i*1.7)*1.4; val al=(if(u<0.15)0.95 else 0.75*(1-u*0.75))*f.cloudA; val col=if(j<2)"#f0ffe0" else if(j<7)"#a8f870" else if(u<0.5)"#6fd23c" else "#3f8f2c"; circle(c,x,wob+(m.hash(i)-0.5)*5,r,col,al) }
            }
        }
        if(f.liquidOn)repeat(9) { j -> val i=j.toDouble(); val raw=f.t*(0.35+m.hash(i)*0.3)+m.hash(i*2.2); val life=raw-floor(raw); val x=34.6+m.hash(i*4.7)*(f.front-42.6)+life*10; val y=15.4-life*30.8*0.85
            if(x<=f.front-6)circle(c,x,y,1.3+m.hash(i*8.8)*1.2,"#eaffd0",0.6,1.1)
        }; c.restore()
    }
    private fun glass(c:Canvas,f:PotionVialUploadFrame) {
        c.drawPath(outer,paint("#a29aaa",1.0,2.2)); c.drawPath(inner,paint("#525060",0.9,1.3))
        for((x,b) in listOf(48.0 to 120.0,134.0 to 196.0,210.0 to 228.0,242.0 to 252.0,264.0 to 270.0)) { val a=max(x,if(f.liquidOn)f.front+8 else 0.0); if(b>a)line(c,a,-17.4,b,-17.4,"#e9e4f0",1.4,0.5) }; line(c,284.0,-16.5,292.0,-14.0,"#e9e4f0",1.3,0.4)
    }
    private fun chain(c:Canvas,f:PotionVialUploadFrame) {
        m.links(315.5,14.0,f.gemX+sin(f.gemRot)*16,f.gemY-cos(f.gemRot)*16,64.0).forEachIndexed { i,l ->
            c.save(); c.translate(l.x.toFloat(),l.y.toFloat()); c.rotate((l.a*180/PI).toFloat())
            if(i%2==0) { oval(c,-3.0,-2.1,6.0,4.2,"#5a2c0e",1.0,2.6); oval(c,-3.0,-2.1,6.0,4.2,"#e9a944",1.0,1.5); rect(c,-1.2,-2.6,1.6,1.0,"#ffd277",0.9) }
            else { line(c,-2.6,0.0,2.6,0.0,"#5a2c0e",2.8); line(c,-2.4,0.0,2.4,0.0,"#e9a944",1.5) }; c.restore()
        }
    }
    private fun stopper(c:Canvas) {
        for((b,steel,radius) in bands) {
            val paint=paint("#ffffff"); paint.isAntiAlias=true; paint.shader=LinearGradient(0f,-b[2].toFloat(),0f,b[2].toFloat(),gradients[if(steel)1 else 0],floatArrayOf(0f,0.28f,0.62f,1f),Shader.TileMode.CLAMP)
            c.drawRoundRect(b[0].toFloat(),-b[2].toFloat(),b[1].toFloat(),b[2].toFloat(),radius.toFloat(),radius.toFloat(),paint)
            c.drawRoundRect(b[0].toFloat(),-b[2].toFloat(),b[1].toFloat(),b[2].toFloat(),radius.toFloat(),radius.toFloat(),paint("#1a1014",0.55,1.1))
        }
    }
    private fun gem(c:Canvas,f:PotionVialUploadFrame) {
        val cy=cos(0.68); val sy=sin(0.68); val cp=cos(-0.52); val sp=sin(-0.52); val cr=cos(f.gemVis); val sr=sin(f.gemVis)
        fun project(px:Double,py:Double,pz:Double):DoubleArray { val x=px*cy+pz*sy; val z=-px*sy+pz*cy; val y=py*cp-z*sp; return doubleArrayOf(x*cr-y*sr,x*sr+y*cr,py*sp+z*cp) }
        c.save(); c.translate(f.gemX.toFloat(),f.gemY.toFloat()); var right=doubleArrayOf(-999.0,0.0)
        for(face in cubeFaces) {
            val n=project(face[0],face[1],face[2]); if(n[2]<=0.02)continue
            val pts=(0..3).map { k -> project(face[3+k*3]*16.5,face[4+k*3]*16.5,face[5+k*3]*16.5) }; pts.forEach { if(it[0]>right[0])right=it }
            val lit=m.clamp(0.35+0.75*(n[0]*(-0.45)+n[1]*(-0.75)+n[2]*0.48)); val pth=path(pts.map { it[0] to it[1] },true)
            c.drawPath(pth,paint(if(lit>0.78)"#63ea63" else if(lit>0.5)"#33b24b" else "#1b7a3a"))
            val mx=pts.sumOf{it[0]}/4; val my=pts.sumOf{it[1]}/4
            c.drawPath(path(pts.map{m.mix(mx,it[0],0.52)+1.2 to m.mix(my,it[1],0.52)+1.4},true),paint(if(lit>0.78)"#33b24b" else "#12592a",0.75))
            if(lit>0.7)line(c,pts[3][0],pts[3][1],pts[2][0],pts[2][1],"#8ff58a",1.4,0.9)
            c.drawPath(pth,paint("#0b3a1a",0.55,1.0))
        }
        val arm=3+2.5*(0.55+0.45*sin(2*PI*4*f.t/12)); rect(c,right[0]-arm,right[1]-0.7,arm*2,1.4,"#ffffff",0.95); rect(c,right[0]-0.7,right[1]-arm,1.4,arm*2,"#ffffff",0.95); c.restore()
    }
    private fun spider(c:Canvas,f:PotionVialUploadFrame) {
        c.save(); c.translate(f.spX.toFloat(),(f.spY-f.spHop*5).toFloat()); val sc=(1.42+f.spHop*0.12).toFloat(); c.scale(sc,sc); c.rotate((f.spHead*180/PI).toFloat())
        val rest=listOf(13.0 to 15.0,5.0 to 19.0,-5.0 to 19.0,-14.0 to 15.0)
        for(side in listOf(-1.0,1.0))for(j in 0..3) {
            val grp=(j+(if(side>0)1 else 0))%2; val ph=f.spPhase*PI*2+grp*PI; val sw=sin(ph)*5.5*f.spWalk
            val idle=sin(2*PI*4*f.spIdle/12+j*1.7+side)*0.9*(1-f.spWalk); val hx=3-j*2.2; val hy=side*3.5; val fx=rest[j].first+sw+idle; val fy=side*(rest[j].second+cos(ph)*1.2*f.spWalk)
            val kx=(hx+fx)/2+(if(j<2)3 else -3); val ky=(hy+fy)/2+side*6.5; val pth=path(listOf(hx to hy,kx to ky,fx to fy))
            c.drawPath(pth,paint("#2a2832",1.0,2.6)); c.drawPath(pth,paint("#5d5a68",1.0,1.8))
        }
        oval(c,-19.0,-8.5,17.0,17.0,"#16141c"); oval(c,-16.0,-6.5,8.0,5.0,"#4a4656",0.7)
        c.drawPath(path(listOf(-14.5 to -3.0,-6.5 to -3.0,-10.5 to 0.0,-6.5 to 3.0,-14.5 to 3.0,-10.5 to 0.0),true),paint("#e8302f"))
        circle(c,1.5,0.0,6.2,"#16141c"); circle(c,0.5,-2.0,2.0,"#4a4656",0.6); rect(c,5.0,-3.0,2.0,2.0,"#9cf26a"); rect(c,5.0,1.0,2.0,2.0,"#9cf26a"); c.restore()
    }
    private fun burst(c:Canvas,f:PotionVialUploadFrame) {
        if(f.burst<0)return
        repeat(90) { j ->
            val i=j.toDouble(); val a=f.burst-m.hash(i*2.9)*0.45; val life=0.6+m.hash(i*5.5)*0.7
            if(a>0 && a<=life) {
                val ang=-PI*0.36+(m.hash(i*1.7)-0.5)*1.5; val v=90+m.hash(i*3.9)*170; val drag=(1-exp(-a*3.2))/3.2
                val x=343+cos(ang)*v*drag+sin(a*6+i)*3; val y=-4+sin(ang)*v*drag-14*a; val al=1-m.smooth(m.seg(a,life*0.55,life))
                val col=if(m.hash(i*2.2)>0.82)"#ffffff" else if(m.hash(i)>0.45)"#7cf05a" else "#5fe34f"; val sz=if(m.hash(i*7.1)>0.7)2.6 else 1.5; rect(c,x,y,sz,sz,col,al)
            }
        }
    }
    private fun card(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame) {
        c.drawRoundRect(76f,g.cardY.toFloat(),326f,(g.cardY+68).toFloat(),14f,14f,paint("#141219")); c.drawRoundRect(76f,g.cardY.toFloat(),326f,(g.cardY+68).toFloat(),14f,14f,paint(if(f.cardDoneA>0)"#4fae36" else "#2a2733",1.0,1.3))
        val sx=92.0; val sy=g.cardY+17
        if(f.slotEmpty>0.01) {
            repeat(4) { val a=it*8.0; line(c,sx+a,sy,sx+a+4,sy,"#5a5962",1.5,f.slotEmpty); line(c,sx+a,sy+34,sx+a+4,sy+34,"#5a5962",1.5,f.slotEmpty) }
            repeat(5) { val a=it*8.0; line(c,sx,sy+a,sx,sy+a+4,"#5a5962",1.5,f.slotEmpty); line(c,sx+28,sy+a,sx+28,sy+a+4,"#5a5962",1.5,f.slotEmpty) }
        }
        if(f.cardDoneA>0.01) {
            val bx=117.0; val by=g.cardY+46; circle(c,bx,by,6.5*m.back(f.cardDoneA),"#3fbf4a",f.cardDoneA)
            c.drawPath(path(listOf(bx-3 to by,bx-0.8 to by+2.3,bx+3.2 to by-2.2)),paint("#0d2a10",f.cardDoneA,1.6))
        }
    }
    private fun solid(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame) {
        c.save(); local(c,g); c.save(); c.translate(f.shake.toFloat(),(-abs(f.shake)*0.4).toFloat()); details(c,f); glass(c,f); chain(c,f); stopper(c); c.restore(); gem(c,f); spider(c,f); burst(c,f); card(c,g,f); c.restore()
    }
    private fun text(c:Canvas,s:String,size:Double,x:Double,y:Double,col:String,a:Double=1.0,center:Boolean=false,weight:String="Regular") {
        if(a<=0.01)return; type.typeface=fonts[weight]; type.textSize=size.toFloat(); type.color=Color.parseColor(col); type.alpha=(a.coerceIn(0.0,1.0)*255).roundToInt()
        val dx=if(center)type.measureText(s)/2 else 0f; c.drawText(s,x.toFloat()-dx,y.toFloat(),type)
    }
    private fun width(s:String,size:Double,weight:String):Double { type.typeface=fonts[weight]; type.textSize=size.toFloat(); return type.measureText(s).toDouble() }
    private fun type(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame) {
        c.save(); c.translate(g.ox.toFloat(),0f); c.scale(g.s.toFloat(),g.s.toFloat()); val top=g.top/g.s
        c.drawPath(path(listOf(26.0 to top+17,20.0 to top+23,26.0 to top+29)),paint(mute,1.0,2.0))
        text(c,"Grimoire",15.0,34.0,top+28,ink,0.92,weight="Medium"); text(c,"Slot 3 of 5",12.0,382-width("Slot 3 of 5",12.0,"Regular"),top+27,mute)
        text(c,"Add a spell pack",26.0,20.0,top+82,ink,weight="Bold"); text(c,"Zip files up to 200 MB. Every pack is checked for curses.",12.0,20.0,top+106,mute)
        val cy=g.cy/g.s; val ty=cy+82
        text(c,"Drop a file into the potion",13.0,201.0,ty,mute,f.restA,true); text(c,"Release to drop it in",13.0,201.0,ty,"#c6f7a8",f.readyA,true)
        if(f.counterA>0.01) {
            val pct=floor(f.p*100+0.0001).toInt().toString(); val wd=width(pct,38.0,"Bold"); val wp=width("%",17.0,"Bold"); val x=201-(wd+wp+2)/2; val base=cy+52+38*0.72
            text(c,pct,38.0,x,base,ink,f.counterA,weight="Bold"); text(c,"%",17.0,x+wd+2,base-38*0.42,mute,f.counterA,weight="Bold")
            text(c,m.mbText(f.p),13.0,201.0,base+24,mute,f.counterA*(1-m.smooth(f.doneA*3)),true)
            text(c,"Upload complete",13.0,201.0,base+24,done,f.counterA*m.smooth((f.doneA-0.35)/0.65),true)
        }
        val cardTop=g.cy/g.s+g.cardY
        text(c,"spells.zip",15.0,134.0,cardTop+30,ink,0.95,weight="Medium")
        val lines=listOf("48.2 MB · drag it into the potion","Brewing into your grimoire…","Added to your grimoire"); val cols=listOf(mute,mute,done); val st=f.cardState
        val since=if(st==2)f.t-8.4 else if(st==1)f.t-1.75 else 1.0; val inK=m.smooth(m.seg(since,0.1,0.2))
        if(since<0.1 && st>0)text(c,lines[st-1],12.0,134.0,cardTop+49,cols[st-1],1-m.smooth(since/0.1))
        text(c,lines[st],12.0,134.0,cardTop+49,cols[st],if(st==0)1.0 else inK)
        fun icon(x:Double,y:Double,sc:Double,rot:Double,a:Double) {
            if(a<=0.01)return; c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate((rot*180/PI).toFloat()); c.scale(sc.toFloat(),sc.toFloat()); val p=paint("#ffffff",a); p.isFilterBitmap=false; c.drawBitmap(file,null,RectF(-14f,-17f,14f,17f),p); c.restore()
        }
        icon(106.0,cardTop+34,1.0,0.0,1-f.slotEmpty)
        if(f.ghostAlpha>0.01) { val gy=g.cy/g.s+f.ghostY; val la=f.ghostAlpha*m.clamp((f.ghostScale-0.7)/0.3); icon(f.ghostX,gy,f.ghostScale,f.ghostRot,f.ghostAlpha); text(c,"spells.zip",12.0,f.ghostX,gy+31,ink,la,true); text(c,"48.2 MB",12.0,f.ghostX,gy+45,mute,la,true) }
        c.restore(); if(f.fade>0.003)rect(c,0.0,0.0,g.w,g.h,page,f.fade)
    }
    fun draw(c:Canvas,g:PotionVialUploadGeo,f:PotionVialUploadFrame,density:Double) {
        val px=4/density; val w=max(1,ceil(g.w/px).toInt()); val h=max(1,ceil(g.h/px).toInt())
        if(lw!=w || lh!=h) {
            layers.forEach{it.recycle()}; lw=w; lh=h; layers=List(4){Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)}; canvases=layers.map{Canvas(it)}; inputs=layers.map{BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)}
            if(Build.VERSION.SDK_INT>=33)runtime=PotionVialUploadRuntime(inputs)
        }
        fun render(n:Int,draw:(Canvas)->Unit) { layers[n].eraseColor(Color.TRANSPARENT); val a=canvases[n]; a.save(); a.scale((1/px).toFloat(),(1/px).toFloat()); draw(a); a.restore() }
        render(0){it.drawColor(Color.parseColor(page)); stars(it,g,f)}; render(1){front(it,g,f)}; render(2){solid(it,g,f)}; render(3){local(it,g); it.drawPath(inner,paint("#ffffff"))}
        if(Build.VERSION.SDK_INT>=33 && c.isHardwareAccelerated && runtime!=null) {
            val p=paint("#ffffff"); p.shader=runtime!!.shader(g,f,px); c.drawRect(0f,0f,g.w.toFloat(),g.h.toFloat(),p)
        } else {
            val dst=RectF(0f,0f,(w*px).toFloat(),(h*px).toFloat()); c.drawBitmap(layers[0],null,dst,paint("#ffffff")); c.drawBitmap(layers[1],null,dst,paint("#ffffff"))
            if(f.liquidOn) { c.save(); local(c,g); c.clipPath(inner); rect(c,24.6,-20.4,max(0.0,f.front-24.6),40.8,"#82ed3d"); for(i in 0..9)line(c,30.0+i*34,-20.0,50.0+i*34,20.0,"#f5ffeb",1.5,0.8); c.restore() }
            c.drawBitmap(layers[2],null,dst,paint("#ffffff"))
        }
        type(c,g,f)
    }
    fun close() { layers.forEach{it.recycle()}; file.recycle() }
}

@androidx.annotation.RequiresApi(33)
internal class PotionVialUploadRuntime(inputs:List<BitmapShader>) {
    private val effect=RuntimeShader(PotionVialUploadShaders.source)
    init { listOf("back","frontArt","solid","maskArt").forEachIndexed { i,name -> effect.setInputShader(name,inputs[i]) } }
    fun shader(g:PotionVialUploadGeo,f:PotionVialUploadFrame,px:Double):Shader {
        effect.setFloatUniform("geo",g.w.toFloat(),g.h.toFloat(),g.s.toFloat(),g.ox.toFloat())
        effect.setFloatUniform("line",g.cy.toFloat(),0f,f.t.toFloat(),f.p.toFloat())
        effect.setFloatUniform("fields",f.front.toFloat(),f.bulge.toFloat(),f.glow.toFloat(),f.mist.toFloat())
        effect.setFloatUniform("fx",if(f.liquidOn)1f else 0f,f.shake.toFloat(),px.toFloat(),0f); return effect
    }
}
