package com.ngocdevv.nativecook.blocks.cards.friendradarmap

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class FriendRadarMapArt(context: Context) {
    private val mono = Typeface.createFromAsset(context.assets,"friendradarmap/friendradarmap_JetBrainsMono-Medium.ttf")
    private val bold = Typeface.createFromAsset(context.assets,"friendradarmap/friendradarmap_JetBrainsMono-Bold.ttf")
    private val pixel = Typeface.createFromAsset(context.assets,"friendradarmap/friendradarmap_PixelifySans-700.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = pixel; textSize = 64f }
    private val glow = BlurMaskFilter(28f,BlurMaskFilter.Blur.NORMAL)
    private val halo = BlurMaskFilter(14f,BlurMaskFilter.Blur.NORMAL)
    private val map: Bitmap = makeMap()
    private val faces = (0..2).map { makeFace(it) }
    private val shapes = FriendRadarMapMotion.friends.map { f ->
        Path().apply {
            moveTo((-75-150*f.skew*0.2).toFloat(),-52f)
            lineTo((75-150*f.skew*0.2).toFloat(),(-52-104*f.skew*0.25).toFloat())
            lineTo((150*0.38+150*f.skew*0.15).toFloat(),52f)
            lineTo((-150*0.38+150*f.skew*0.15).toFloat(),(52+104*f.skew*0.2).toFloat()); close()
        }
    }
    private fun reset(color: Int, width: Float = 0f): Paint {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        paint.strokeWidth = width; paint.style = if (width>0) Paint.Style.STROKE else Paint.Style.FILL
        return paint
    }
    private fun line(c: Canvas, p: Path, width: Float, color: Int) {
        val pnt = reset(color,width); pnt.strokeCap = Paint.Cap.ROUND; pnt.strokeJoin = Paint.Join.ROUND; c.drawPath(p,pnt)
    }
    private fun makeMap(): Bitmap {
        val bitmap = Bitmap.createBitmap(2200,3000,Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap); c.scale(2f,2f); c.drawColor(0xff101012.toInt())
        c.save(); c.translate(550f,750f); c.rotate(18f)
        for (i in -9..9) {
            val d = i.toDouble(); val x = d*92+(FriendRadarMapMotion.random(d)-0.5)*30
            val p = Path().apply { moveTo(x.toFloat(),-1100f); cubicTo((x+40*(FriendRadarMapMotion.random(d+3)-0.5)).toFloat(),-300f,(x-40*(FriendRadarMapMotion.random(d+7)-0.5)).toFloat(),300f,(x+20).toFloat(),1100f) }
            line(c,p,if (i%4==0) 8f else 3f,if (i%4==0) 0xff38383f.toInt() else 0xff26262b.toInt())
        }
        for (j in -12..12) {
            val d = j.toDouble(); val y = d*86+(FriendRadarMapMotion.random(d+40)-0.5)*26
            val p = Path().apply { moveTo(-1000f,y.toFloat()); cubicTo(-300f,(y+30*(FriendRadarMapMotion.random(d+50)-0.5)).toFloat(),300f,(y-30*(FriendRadarMapMotion.random(d+60)-0.5)).toFloat(),1000f,(y+10).toFloat()) }
            line(c,p,if (j%5==0) 8f else 3f,if (j%5==0) 0xff38383f.toInt() else 0xff26262b.toInt())
        }
        c.restore()
        line(c,Path().apply { moveTo(-50f,300f); cubicTo(400f,520f,700f,760f,1150f,1300f) },10f,0xff3d3d45.toInt())
        line(c,Path().apply { moveTo(1150f,200f); cubicTo(800f,600f,500f,900f,-50f,1250f) },10f,0xff3d3d45.toInt())
        c.drawCircle(560f,780f,360f,reset(0xff2f2f36.toInt(),6f))
        val names = listOf("Berson St","Kiln Ave","Pier Rd","Mill Ln","Ring Rd","Dock St")
        val positions = listOf(floatArrayOf(380f,610f,18f),floatArrayOf(700f,820f,-42f),floatArrayOf(250f,1120f,18f),floatArrayOf(820f,420f,18f),floatArrayOf(220f,700f,-70f),floatArrayOf(600f,1240f,18f))
        for ((i,n) in names.withIndex()) { val p = positions[i]; c.save(); c.translate(p[0],p[1]); c.rotate(p[2]); val t = reset(0xff5a5a62.toInt()); t.typeface = mono; t.textSize = 11f; c.drawText(n,0f,0f,t); c.restore() }
        return bitmap
    }
    private fun makeFace(index: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(414,360,Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap); c.scale(3f,3f)
        val bayer = intArrayOf(0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5)
        fun sm(a: Double,b: Double,x: Double): Double { val k = FriendRadarMapMotion.clamp((x-a)/(b-a)); return k*k*(3-2*k) }
        fun ell(u: Double,v: Double,cx: Double,cy: Double,rx: Double,ry: Double) = ((u-cx)/rx).pow(2)+((v-cy)/ry).pow(2)
        for (y in 0 until 40) for (x in 0 until 46) {
            val u = (x+0.5)/46-0.5; val v = (y+0.5)/40
            var l = 0.12+0.22*(1-v)
            if (ell(u,v,0.0,1.08,0.47,0.34)<1) l = 0.26+0.28*sm(0.3,-0.3,u)
            if (abs(u)<0.075 && v>0.6 && v<0.84) l = 0.5-0.25*sm(-0.02,0.07,u)
            val face = ell(u,v,0.0,0.42,0.19,0.25)
            if (face<1) {
                l = 0.92-0.55*sm(-0.12,0.2,u)-0.12*sm(0.45,0.65,v)
                if (ell(u,v,0.025,0.5,0.025,0.04)<1) l-=0.25
                if (abs(v-0.575)<0.012 && abs(u)<0.055) l=0.22
                if (index==0) {
                    if (ell(u,v,-0.075,0.41,0.07,0.04)<1 || ell(u,v,0.075,0.41,0.07,0.04)<1 || (abs(v-0.4)<0.01 && abs(u)<0.03)) l=0.03
                    if (ell(u,v,-0.095,0.4,0.015,0.012)<1) l=0.95
                } else {
                    if (ell(u,v,-0.072,0.41,0.026,0.018)<1 || ell(u,v,0.072,0.41,0.026,0.018)<1) l=0.06
                    if (abs(v-0.36)<0.01 && abs(abs(u)-0.075)<0.04) l=0.15
                }
            }
            val crown = ell(u,v,0.0,0.36,0.22,0.3)<1 && v<0.3-0.06*cos(u*9)
            if (index==0 && (crown || (abs(u)>0.15 && abs(u)<0.25 && v>0.2 && v<0.6 && face>0.75))) l=0.07+0.25*sm(0.18,0.12,abs(u+0.06))*(if (v<0.3) 1 else 0)
            if (index==1 && ell(u,v,0.0,0.36,0.21,0.28)<1 && v<0.24) l=0.1
            if (index==2 && (ell(u,v,0.0,0.1,0.085,0.07)<1 || (ell(u,v,0.0,0.36,0.21,0.28)<1 && v<0.26))) l=0.09+0.2*sm(0.0,-0.1,u)
            val q = l*2+bayer[(y%4)*4+x%4]/16.0-0.5
            val p = reset(if (q<0.5) 0xff0a1c08.toInt() else if (q<1.5) 0xff4f9a2e.toInt() else 0xffd9ff9e.toInt()); p.isAntiAlias = false
            c.drawRect(x*3f,y*3f,x*3f+3.35f,y*3f+3.35f,p)
        }
        return bitmap
    }
    private fun text(c: Canvas,s: String,size: Float,x: Float,y: Float,dim: Boolean = false,track: Float = 0.6f,align: Paint.Align = Paint.Align.CENTER) {
        val p = reset(if (dim) 0x99ffffff.toInt() else Color.WHITE); p.typeface = if (dim) mono else bold; p.textSize = size; p.letterSpacingCompat(track,size); p.textAlign = align
        // Canvas text has no letter spacing: position glyphs with measured advances.
        val widths = FloatArray(s.length); p.getTextWidths(s,widths)
        val total = widths.sum()+s.length*track
        var dx = x-(if (align==Paint.Align.CENTER) total/2 else if (align==Paint.Align.RIGHT) total else 0f)
        p.textAlign = Paint.Align.LEFT
        for (i in s.indices) { c.drawText(s,i,i+1,dx,y,p); dx += widths[i]+track }
    }
    private fun Paint.letterSpacingCompat(track: Float,size: Float) { /* Explicit glyph advances below. */ }
    fun draw(c: Canvas,w: Float,h: Float,top: Float,bottom: Float,f: FriendRadarMapMotion.Frame,reduced: Boolean) {
        c.drawColor(0xff0b0b0d.toInt())
        val a = FriendRadarMapMotion.friends[f.prev]; val b = FriendRadarMapMotion.friends[f.cur]
        val k = if (reduced) 1.0 else FriendRadarMapMotion.ease(f.since/0.9)
        val cx = FriendRadarMapMotion.mix(a.x,b.x,k); val cy = FriendRadarMapMotion.mix(a.y,b.y,k)
        val zoom = if (reduced) 1.0 else 1-0.22*sin(PI*FriendRadarMapMotion.clamp(f.since/0.9))
        val fx = w/2; val fy = top+h*0.3f
        c.save(); c.translate(fx,fy); c.scale(zoom.toFloat(),zoom.toFloat()); c.translate(-cx.toFloat(),-cy.toFloat())
        c.drawBitmap(map,null,RectF(0f,0f,1100f,1500f),reset(Color.WHITE).apply { isFilterBitmap=true }); c.restore()
        fun px(i: Int) = (fx+(FriendRadarMapMotion.friends[i].x-cx)*zoom).toFloat()
        fun py(i: Int) = (fy+(FriendRadarMapMotion.friends[i].y-cy)*zoom).toFloat()
        for (i in 0..2) {
            if (i==f.cur && f.since>0.35 || i==f.prev && f.prev!=f.cur && f.since<0.3 && !reduced) continue
            val x = px(i); val y = py(i)
            c.drawCircle(x,y,4f,reset(Color.WHITE)); c.drawLine(x,y,x+10,y-22,reset(Color.WHITE,1.5f))
            c.drawRect(x+0.5f,y-47.5f,x+31.5f,y-20.5f,reset(Color.WHITE)); c.drawBitmap(faces[i],null,RectF(x+2,y-46,x+30,y-22),reset(Color.WHITE).apply { isFilterBitmap=true })
        }
        val out = if (reduced || f.prev==f.cur) 0.0 else 1-FriendRadarMapMotion.clamp(f.since/0.3).pow(2)
        val into = if (reduced) 1.0 else FriendRadarMapMotion.back((f.since-0.35)/0.55)
        for (pass in 0..1) {
            val who = if (pass==0) f.prev else f.cur; val g = if (pass==0) out else into
            if (g<=0.01 || pass==0 && f.prev==f.cur) continue
            c.save(); c.translate(px(who),py(who)-(40*g).toFloat()); val sc = (0.2+0.8*g).toFloat(); c.scale(sc,sc)
            val p = reset(0xff8dff5a.toInt()); p.alpha = (255*0.55*(0.75+0.25*sin(2*PI*3*f.time/9))).toInt(); p.maskFilter=glow; c.drawPath(shapes[who],p)
            c.save(); c.clipPath(shapes[who]); c.drawBitmap(faces[who],null,RectF(-85f,-66f,85f,66f),reset(Color.WHITE).apply { isFilterBitmap=true }); c.restore()
            c.drawPath(shapes[who],reset(0xff0a0a0a.toInt(),3f).apply { strokeJoin=Paint.Join.ROUND }); c.restore()
        }
        val (name,decoded) = f.decoded(reduced)
        val tw = namePaint.measureText(name); val fit = min(1f,(w-40)/tw)
        c.save(); c.translate(w/2-tw*fit/2,h-bottom-228); c.scale(fit,fit)
        namePaint.style=Paint.Style.FILL; namePaint.color=Color.WHITE; namePaint.alpha=140; namePaint.maskFilter=halo; c.drawText(decoded,0f,0f,namePaint)
        namePaint.maskFilter=null; namePaint.alpha=255; namePaint.color=0xff050505.toInt(); namePaint.style=Paint.Style.STROKE; namePaint.strokeWidth=8f; namePaint.strokeJoin=Paint.Join.MITER; c.drawText(decoded,0f,0f,namePaint)
        namePaint.style=Paint.Style.FILL; namePaint.color=Color.WHITE; c.drawText(decoded,0f,0f,namePaint); c.restore()
        if (!reduced && f.fill>=1 && f.burst>=0 && f.burst<1.4) {
            for (i in 0 until 40) {
                val t=f.burst; val angle=-PI/2+(FriendRadarMapMotion.hash(i*3.1)-0.3)*1.9; val v=260+FriendRadarMapMotion.hash(i*7.7)*420
                val x=(round((52+cos(angle)*v*t)/3)*3).toFloat(); val y=(round((h-bottom-54+sin(angle)*v*t+700*t*t)/3)*3).toFloat(); val sz=4f+i%3*2
                val p=reset(if (i%3==0) 0xff8dff5a.toInt() else if (i%3==1) 0xffd63ce0.toInt() else Color.WHITE); p.alpha=(255*(1-FriendRadarMapMotion.clamp((t-0.8)/0.6))).toInt(); c.drawRect(x,y,x+sz,y+sz,p)
            }
        }
        val fade=reset(Color.WHITE); fade.shader=LinearGradient(0f,h-330,0f,h,intArrayOf(0x000b0b0d,0xd90b0b0d.toInt(),0xff0b0b0d.toInt()),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP); c.drawRect(0f,h-330,w,h,fade)
        val opacity=if (reduced) 1.0 else if (f.since<0.3 && f.prev!=f.cur) 1-FriendRadarMapMotion.clamp(f.since/0.25) else FriendRadarMapMotion.clamp((f.since-0.5)/0.4)
        val fr=FriendRadarMapMotion.friends[f.labelIndex]
        c.saveLayerAlpha(null,(opacity*255).toInt()); val ly=(h-bottom-120-36+(1-opacity)*6).toFloat()
        text(c,fr.street,14f,w/2,ly+14f); text(c,fr.sooner,11f,w/2,ly+34f,true); c.restore()
        val by=h-bottom-86; val button=RectF(20f,by,w-20,by+64)
        c.drawRoundRect(button,32f,32f,reset(0xff1b1b1f.toInt()))
        c.save(); c.clipPath(Path().apply { addRoundRect(button,32f,32f,Path.Direction.CW) }); c.drawRect(20f,by,20+(w-40)*f.fill.toFloat(),by+64,reset(0xffc932d4.toInt())); c.restore()
        c.drawRoundRect(button,32f,32f,reset(0x14ffffff,1f))
        c.drawCircle(53f,by+32,26f,reset(0xffd63ce0.toInt())); c.drawCircle(53f,by+32,25f,reset(0xff0b0b0d.toInt(),2f))
        val p=reset(0xff0b0b0d.toInt()); p.typeface=bold; p.textSize=28f; p.textAlign=Paint.Align.CENTER; c.drawText(if(f.fill>=1) "✓" else "›",53f,by+41,p)
        text(c,if(f.fill>=1) "RIDE ON" else "HOLD TO ORDER",13f,w-45,by+27,false,0.5f,Paint.Align.RIGHT)
        text(c,if(f.fill>=1) "THE WAY" else "A RIDE FOR $5",13f,w-45,by+44,false,0.5f,Paint.Align.RIGHT)
    }
}
