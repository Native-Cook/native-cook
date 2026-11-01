package com.ngocdevv.nativecook.blocks.progress.setupdomeloader

import android.content.res.AssetManager
import android.graphics.*
import kotlin.math.*

internal data class SetupDomeLoaderGeo(val w: Double,val screenH: Double) {
    val cx=w/2
    val by=floor(screenH*0.47+0.5)
    val r=floor(w*0.25+0.5)
    val ry=floor(w*0.25*0.3+0.5)
    val h=floor(w*0.25*1.75+0.5)
}

internal class SetupDomeLoaderArt(assets: AssetManager) {
    private val m=SetupDomeLoaderMotion
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular=Typeface.createFromAsset(assets,"setupdomeloader/setupdomeloader_Inter-Regular.ttf")
    private val semi=Typeface.createFromAsset(assets,"setupdomeloader/setupdomeloader_Inter-SemiBold.ttf")
    private val bold=Typeface.createFromAsset(assets,"setupdomeloader/setupdomeloader_Inter-Bold.ttf")
    private val icons=Typeface.createFromAsset(assets,"setupdomeloader/setupdomeloader_Ionicons.ttf")
    private var keyGlow: Bitmap? = null
    private var keyRy = -1.0
    private val beadGlows = Array(41) { i ->
        glowBitmap(8.0) { canvas, glow -> canvas.drawCircle(50f,50f,(8+i*0.15).toFloat(),glow) }
    }
    private fun glowBitmap(sigma: Double,alpha: Int=128,draw: (Canvas,Paint)->Unit): Bitmap {
        val bitmap=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.scale(3f,3f)
        val glow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0xff3BE07A.toInt(); this.alpha=alpha
            maskFilter=BlurMaskFilter(sigma.toFloat(),BlurMaskFilter.Blur.NORMAL)
        }
        draw(canvas,glow)
        return bitmap
    }
    private fun keyGlow(g: SetupDomeLoaderGeo): Bitmap {
        if(keyGlow==null || keyRy!=g.ry) {
            keyRy=g.ry
            keyGlow=glowBitmap(10.0,140) { canvas,glow ->
                val key=Path().apply {
                    addOval(39f,43f,61f,55f,Path.Direction.CW)
                    moveTo(45f,51f); lineTo(55f,51f)
                    lineTo(59f,(50+g.ry*1.42*0.62).toFloat()); lineTo(41f,(50+g.ry*1.42*0.62).toFloat()); close()
                }
                canvas.drawPath(key,glow)
            }
        }
        return keyGlow!!
    }
    private fun Canvas.glow(bitmap: Bitmap,x: Double,y: Double,alpha: Double=1.0) {
        drawBitmap(bitmap,null,RectF((x-50).toFloat(),(y-50).toFloat(),(x+50).toFloat(),(y+50).toFloat()),p(0xffffff,alpha))
    }
    private fun p(color: Int,a: Double=1.0,stroke: Double=0.0): Paint {
        paint.reset(); paint.isAntiAlias=true; paint.color=color or (0xff shl 24)
        paint.alpha=(255*a.coerceIn(0.0,1.0)).roundToInt(); paint.style=if(stroke>0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth=stroke.toFloat(); paint.strokeCap=Paint.Cap.ROUND
        return paint
    }
    private fun Canvas.path(path: Path,c: Int,a: Double=1.0,s: Double=0.0) = drawPath(path,p(c,a,s))
    private fun Canvas.rect(x: Double,y: Double,w: Double,h: Double,c: Int,a: Double=1.0) = drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p(c,a))
    private fun Canvas.oval(x: Double,y: Double,w: Double,h: Double,c: Int,a: Double=1.0,s: Double=0.0) = drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p(c,a,s))
    private fun Canvas.circle(x: Double,y: Double,r: Double,c: Int,a: Double=1.0,s: Double=0.0) = drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p(c,a,s))
    private fun Canvas.line(x: Double,y: Double,xx: Double,yy: Double,c: Int,a: Double=1.0,s: Double=1.0) = drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),p(c,a,s))
    private fun Canvas.rr(x: Double,y: Double,w: Double,h: Double,r: Double,c: Int,a: Double=1.0,s: Double=0.0) = drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),p(c,a,s))
    private fun Path.move(x: Double,y: Double) = moveTo(x.toFloat(),y.toFloat())
    private fun Path.line(x: Double,y: Double) = lineTo(x.toFloat(),y.toFloat())
    private fun panel(g: SetupDomeLoaderGeo,a: Double,b: Double): Path {
        var x0=g.cx+g.r*cos(a); var x1=x0
        for(i in 0..18) { val x=g.cx+g.r*cos(m.mix(a,b,i/18.0)); x0=min(x0,x); x1=max(x1,x) }
        val mid=(x0+x1)/2; val half=max(0.5,(x1-x0)/2); val arch=half*0.95
        return Path().apply {
            for(i in 0..18) {
                val th=m.mix(a,b,i/18.0); val x=g.cx+g.r*cos(th); val y=g.by+g.ry*sin(th)
                if(i==0) move(x,y) else line(x,y)
            }
            for(i in 18 downTo 0) {
                val x=g.cx+g.r*cos(m.mix(a,b,i/18.0)); val k=m.clamp(1-((x-mid)/half).pow(2))
                line(x,g.by-g.h+arch-arch*sqrt(k))
            }
            close()
        }
    }
    private fun platform(cv: Canvas,g: SetupDomeLoaderGeo,lit: Double) = with(cv) {
        val w=g.r*1.42; val h=g.ry*1.42
        oval(g.cx-w,g.by-h+16,w*2,h*2,0x4B4A9E); rect(g.cx-w,g.by,w*2,16.0,0x4B4A9E)
        val gradient=p(0xffffff)
        gradient.shader=LinearGradient(0f,(g.by-h).toFloat(),0f,(g.by+h).toFloat(),intArrayOf(0xffA9A7F5.toInt(),0xff7C7AD9.toInt()),null,Shader.TileMode.CLAMP)
        drawOval((g.cx-w).toFloat(),(g.by-h).toFloat(),(g.cx+w).toFloat(),(g.by+h).toFloat(),gradient)
        oval(g.cx-w*0.78,g.by-h*0.78,w*1.56,h*1.56,0xC9C7FF,0.35,1.2)
        oval(g.cx-w*0.52,g.by-h*0.52,w*1.04,h*1.04,0xC9C7FF,0.22,1.0)
        val key=Path().apply {
            addOval((g.cx-11).toFloat(),(g.by-7).toFloat(),(g.cx+11).toFloat(),(g.by+5).toFloat(),Path.Direction.CW)
            move(g.cx-5,g.by+1); line(g.cx+5,g.by+1); line(g.cx+9,g.by+h*0.62); line(g.cx-9,g.by+h*0.62); close()
        }
        path(key,0x0B0B14)
        if(lit>0) { path(key,0x3BE07A,lit); glow(keyGlow(g),g.cx,g.by,lit) } else { keyGlow(g) }
    }
    private fun item(cv: Canvas,kind: Int,u: Double,sx: Double,tilt: Double,alpha: Double) = with(cv) {
        save(); rotate(tilt.toFloat()); scale(max(0.08,abs(sx)).toFloat(),1f)
        val back=sx<0
        when(kind) {
            0 -> {
                rr(-u*0.9,-u*0.6,u*1.8,u*1.2,u*0.18,if(back) 0x4A2A1A else 0x6B3D24,alpha)
                rr(-u*0.9,-u*0.6,u*1.8,u*0.62,u*0.18,if(back) 0x5A3320 else 0x80492B,alpha)
                line(-u*0.75,0.0,u*0.75,0.0,0xC08A5C,0.55*alpha,1.2)
                if(!back) circle(u*0.55,u*0.02,u*0.11,0xE8C27A,alpha)
            }
            1 -> {
                rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,if(back) 0x6E5320 else 0x3A2A14,alpha)
                rr(-u*0.62,-u*0.85,u*1.24,u*1.7,u*0.14,0xE3B34E,alpha,2.0)
                if(!back) {
                    val cell=u*0.17
                    for(i in 0..4) for(j in 0..4) if((i*7+j*3+i*j)%3!=0 || (i<2 && j<2) || (i>2 && j<2) || (i<2 && j>2))
                        rect(-u*0.43+i*cell,-u*0.43+j*cell,cell*0.82,cell*0.82,0xF2C55E,alpha)
                }
            }
            2 -> {
                val f=0.12*sin(tilt*3)
                val note=Path().apply { move(-u*0.9,-u*0.45+f*u); line(u*0.9,-u*0.45-f*u); line(u*0.9,u*0.45-f*u); line(-u*0.9,u*0.45+f*u); close() }
                path(note,if(back) 0x1E9E58 else 0x2FCB72,alpha); circle(0.0,0.0,u*0.22,0x1B8A4C,alpha,1.5)
            }
            else -> { circle(0.0,0.0,u*0.48,0xC9581F,alpha); circle(0.0,0.0,u*0.44,if(back) 0xE07030 else 0xF28A3A,alpha); circle(0.0,0.0,u*0.28,0xFFB070,0.7*alpha,1.6) }
        }
        restore()
    }
    private fun glass(cv: Canvas,g: SetupDomeLoaderGeo,a: Double,b: Double,c: Double,d: Double) = with(cv) {
        val l=panel(g,a,b); val r=panel(g,c,d)
        path(l,0x9C9BFF,0.24); path(r,0x9C9BFF,0.24); path(l,0xC3C2FF,0.85,2.2); path(r,0xC3C2FF,0.85,2.2)
        for(s in 0..2) {
            val xl=g.cx+g.r*cos(m.mix(a,b,0.18+s*0.12)); val xr=g.cx+g.r*cos(m.mix(c,d,0.7+s*0.1))
            line(xl,g.by-g.h*0.82,xl,g.by-g.h*0.18,0xE2E1FF,0.28,1.4); line(xr,g.by-g.h*0.82,xr,g.by-g.h*0.18,0xE2E1FF,0.28,1.4)
        }
    }
    fun scene(cv: Canvas,g: SetupDomeLoaderGeo,o: SetupDomeLoaderDriver,reduced: Boolean) = with(cv) {
        val ph=m.phase(o.t)
        rect(0.0,0.0,g.w,g.screenH,0x0D0D12)
        if(ph.chk>0.01) {
            val glow=p(0xffffff,m.clamp(ph.chk)); glow.shader=RadialGradient(g.cx.toFloat(),(g.by-g.h*0.62).toFloat(),(g.r*2.4).toFloat(),intArrayOf(0x523BE07A,0x003BE07A),null,Shader.TileMode.CLAMP)
            drawRect((g.cx-g.r*2.6).toFloat(),(g.by-g.h*0.62-g.r*2.6).toFloat(),(g.cx+g.r*2.6).toFloat(),(g.by-g.h*0.62+g.r*2.6).toFloat(),glow)
        }
        val open=1-ph.c
        if(open>0.01) for(i in 0..5) rect(g.cx-g.r*0.82*open,g.by-g.h+g.h*0.12+i*g.h*0.13,g.r*1.64*open,g.h*0.08,0x2B1830,0.85*open)
        platform(this,g,ph.k)
        if(ph.burst>0) { val r=m.mix(0.3,1.7,m.easeOut(ph.burst)); oval(g.cx-g.r*r,g.by-g.ry*r,g.r*2*r,g.ry*2*r,0x3BE07A,0.8*(1-ph.burst),3.0) }
        fun items(front: Boolean) {
            m.items.forEachIndexed { i,it ->
                val r=1-ph.c; val phi=it.a+(if(reduced) 0.0 else m.ORBIT_W*o.t+o.off)+ph.c*2.4; val z=sin(phi)
                if((z>=0)==front) {
                    val x=g.cx+g.r*0.58*r*cos(phi); val bob=if(reduced) 0.0 else sin(o.clock*(6*PI/m.LOOP)+it.a*2)*4
                    val y=g.by-g.h*m.mix(it.h,0.5,ph.c)+g.ry*0.62*r*z-bob-o.hop[i]
                    val sc=(0.86+0.16*z)*it.s*(1-0.75*ph.c)
                    save(); translate(x.toFloat(),y.toFloat()); scale(sc.toFloat(),sc.toFloat())
                    item(this,it.kind,g.r*0.5,cos(o.clock*it.spin*(2*PI/m.LOOP)*2+it.a),sin(o.clock*(2*PI/m.LOOP)+it.a)*0.25,1-ph.c*ph.c)
                    restore()
                }
            }
        }
        items(false)
        if(ph.c<0.995) glass(this,g,m.mix(PI*0.61,PI*0.5,ph.c),m.mix(PI*1.39,PI*0.5,ph.c),m.mix(-PI*0.39,PI*0.5,ph.c),m.mix(PI*0.39,PI*0.5,ph.c))
        items(true)
        if(ph.c>0.6 && ph.b<0.999) {
            val top=m.mix(g.by-g.h-g.r*0.1,g.by-2,ph.b)
            line(g.cx,top,g.cx,g.by-2,0xC3C2FF,0.9*m.clamp((ph.c-0.6)/0.4),2.4)
            if(ph.b>0) { circle(g.cx,top,4+6*ph.b,0x3BE07A); glow(beadGlows[(6*ph.b/0.15).roundToInt().coerceIn(0,40)],g.cx,top) }
        }
        if(ph.chk>0.005) {
            val cy=m.mix(g.by-4,g.by-g.h*0.62,min(1.0,ph.chk)); val r=m.mix(8.0,g.r*0.42,ph.chk)
            circle(g.cx,cy,r,0x2DD06B)
            if(ph.tick>0) {
                val tick=Path().apply { move(g.cx-r*0.38,cy+r*0.02); line(g.cx-r*0.1,cy+r*0.3); line(g.cx+r*0.42,cy-r*0.28) }
                val measure=PathMeasure(tick,false); val part=Path(); measure.getSegment(0f,(measure.length*ph.tick).toFloat(),part,true)
                val stroke=p(0x0E2416,1.0,max(2.0,r*0.16)); stroke.strokeJoin=Paint.Join.ROUND; drawPath(part,stroke)
            }
        }
    }
    private fun Canvas.text(s: String,x: Double,y: Double,size: Double,c: Int,face: Typeface=regular,center: Boolean=true,lineH: Double?=null) {
        val tp=p(c); tp.typeface=face; tp.textSize=size.toFloat(); tp.textAlign=if(center) Paint.Align.CENTER else Paint.Align.LEFT
        val fm=tp.fontMetrics; val h=lineH ?: (fm.descent-fm.ascent).toDouble()
        drawText(s,x.toFloat(),(y+(h-fm.descent-fm.ascent)/2).toFloat(),tp)
    }
    fun ui(cv: Canvas,g: SetupDomeLoaderGeo,top: Double,bottom: Double,t: Double,pressed: Boolean) = with(cv) {
        text(61735.toChar().toString(),34.0,top+15,22.0,0xE9E8F5,icons,true,22.0)
        text(62334.toChar().toString(),g.w-34,top+14.5,23.0,0xE9E8F5,icons,true,23.0)
        val brand=p(0xF4F3FA); brand.typeface=bold; brand.textSize=19f
        val bw=brand.measureText("kinto"); val bx=(g.w-bw-9)/2
        text("kinto",bx,top+13.5,19.0,0xF4F3FA,bold,false,25.0); circle(bx+bw+5.5,top+21,3.5,0xF6B73C)
        val ph=m.phase(t); val old=1-m.clamp(ph.swap*2); val new=m.clamp(ph.swap*2-1)
        val y=g.by+g.ry*1.42+52; val sy=y+78; val x=(g.w-260)/2
        saveLayerAlpha(null,(old*255).roundToInt()); translate(0f,((1-old)*-8).toFloat())
        text("We're setting up",g.cx,y,22.0,0xF4F3FA,semi,true,29.0); text("your account",g.cx,y+29,22.0,0xF4F3FA,semi,true,29.0)
        val labels=listOf("Verifying your details","Creating your card","Securing your wallet")
        for(i in 0..2) {
            val end=doubleArrayOf(2.3,4.6,7.0)[i]; val start=doubleArrayOf(0.0,2.3,4.6)[i]; val fade=m.ramp(t,8.5,8.9); val done=t>=end
            val alpha=if(t>=start && t<end) 1.0 else if(done) 0.9+((if(i==0) 1.0 else 0.4)-0.9)*fade else 0.4
            saveLayerAlpha(null,(alpha*255).roundToInt())
            circle(x+9,sy+i*28+14,3.25,0x8E8DB0,1.0,1.5)
            if(done && fade<1) { saveLayerAlpha(null,((1-fade)*255).roundToInt()); text(61982.toChar().toString(),x+9,sy+i*28+5,18.0,0x3BE07A,icons,true,18.0); restore() }
            text(labels[i],x+28,sy+i*28+4,15.0,0xD9D8EA,regular,false,20.0)
            restore()
        }
        rr(x,sy+98,260.0,4.0,2.0,0xffffff,0.08); rr(x,sy+98,260*ph.prog,4.0,2*ph.prog,0x8B89F0)
        text("This can take up to 2 minutes",g.cx,sy+112,13.0,0x6F6E86,regular,true,17.0); restore()
        saveLayerAlpha(null,(new*255).roundToInt()); translate(0f,((1-new)*10).toFloat())
        text("You're all set",g.cx,y,22.0,0xF4F3FA,semi,true,29.0); text("Your card and wallet are ready to use.",g.cx,y+39,15.0,0x9E9DB5,regular,true,21.0); restore()
        saveLayerAlpha(null,(new*255).roundToInt()); translate(0f,((1-new)*16).toFloat())
        val centerY=(g.screenH-bottom-43).toFloat()
        val pressScale=if(pressed) 0.96f else 1f
        scale(pressScale,pressScale,g.cx.toFloat(),centerY)
        rr(20.0,g.screenH-bottom-70,g.w-40,54.0,27.0,0xF4F3FA)
        text("Continue",g.cx,g.screenH-bottom-54,17.0,0x0D0D12,semi,true,22.0); restore()
    }
}
