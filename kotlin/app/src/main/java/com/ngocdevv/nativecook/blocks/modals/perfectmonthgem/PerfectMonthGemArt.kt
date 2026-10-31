package com.ngocdevv.nativecook.blocks.modals.perfectmonthgem

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

internal class PerfectMonthGemArt(context: Context) {
    private val m = PerfectMonthGemMotion
    private val fonts = listOf("InterDisplay-Bold", "Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold").associateWith {
        Typeface.createFromAsset(context.assets, "perfectmonthgem/perfectmonthgem_$it.ttf")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeJoin = Paint.Join.ROUND }
    private val day = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts["Inter-SemiBold"]; textSize = 9f }
    private val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts["Inter-Bold"]; textSize = 12f }
    private val days = (1..31).map { it.toString() }
    private val dayWidths = days.map { day.measureText(it) }
    private val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
    private val rect = RectF()
    private val rays = (0..11).map {
        val a = it/12.0*2*PI
        Path().apply { moveTo(0f,0f); lineTo((cos(a-.11)*230).toFloat(),(sin(a-.11)*230).toFloat()); lineTo((cos(a+.11)*230).toFloat(),(sin(a+.11)*230).toFloat()); close() }
    }
    private val rayGradient = RadialGradient(0f,0f,230f,intArrayOf(Color.argb(115,248,140,226),Color.argb(0,248,140,226)),null,Shader.TileMode.CLAMP)
    private val flash = glow(40.0, 40.0, 16.0)
    private val glint = glow(3.68,1.84,2.5)
    private val shadows = (0..20).map { index ->
        val k = index/20.0
        val width = m.mix(186.0,128.0,k); val height = m.mix(150.0,128.0,k)
        val bitmap = Bitmap.createBitmap(280,244,Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(31,106,63,181); maskFilter = BlurMaskFilter(14f,BlurMaskFilter.Blur.NORMAL) }
        Canvas(bitmap).drawRoundRect((140-width/2).toFloat(),(122-height/2+8).toFloat(),(140+width/2).toFloat(),(122+height/2+8).toFloat(),m.mix(22.0,64.0,k).toFloat(),m.mix(22.0,64.0,k).toFloat(),p)
        bitmap
    }
    private var staticPage: Bitmap? = null
    private var pageKey = ""
    private val path = Path()
    private val labels = mutableMapOf<String, StaticLayout>()
    private fun glow(rx: Double, ry: Double, blur: Double): Bitmap {
        val pad = ceil(blur*4).toInt()
        val bitmap = Bitmap.createBitmap(ceil(rx*2).toInt()+pad*2,ceil(ry*2).toInt()+pad*2,Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; maskFilter = BlurMaskFilter(blur.toFloat(),BlurMaskFilter.Blur.NORMAL) }
        Canvas(bitmap).drawOval(pad.toFloat(),pad.toFloat(),(pad+rx*2).toFloat(),(pad+ry*2).toFloat(),p)
        return bitmap
    }
    private fun color(hex: Int, alpha: Double = 1.0) = Color.argb((alpha.coerceIn(0.0,1.0)*255).roundToInt(),(hex shr 16) and 255,(hex shr 8) and 255,hex and 255)
    private fun rgb(r: Double,g: Double,b: Double,a: Double = 1.0) = Color.argb((a.coerceIn(0.0,1.0)*255).roundToInt(),r.roundToInt(),g.roundToInt(),b.roundToInt())
    private fun round(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,p: Paint = fill) {
        rect.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); c.drawRoundRect(rect,r.toFloat(),r.toFloat(),p)
    }
    private data class V(val x: Double,val y: Double,val z: Double)
    private data class Facet(val vertices: List<V>,val kind: Int,val order: Int)
    private data class Face(val points: List<V>,val depth: Double,val shade: Double,val alpha: Double,val scale: Double,val x: Double,val y: Double,val kind: Int)
    private val facets: List<Facet> = run {
        val table = (0..7).map { val a=it/8.0*2*PI+PI/8; V(cos(a)*.52,sin(a)*.52,.36) }
        val girdle = (0..7).map { val a=it/8.0*2*PI+PI/8; V(cos(a),sin(a),0.0) }
        buildList { add(Facet(table,0,0)); for (i in 0..7) { val j=(i+1)%8; add(Facet(listOf(table[i],girdle[i],girdle[j],table[j]),1,1+i)); add(Facet(listOf(girdle[i],V(0.0,0.0,-.95),girdle[j]),2,9+i)) } }
    }
    private fun rotate(v: V,yaw: Double,pitch: Double): V {
        val x=cos(yaw)*v.x+sin(yaw)*v.z; val z=-sin(yaw)*v.x+cos(yaw)*v.z
        return V(x,cos(pitch)*v.y-sin(pitch)*z,sin(pitch)*v.y+cos(pitch)*z)
    }
    private fun gem(c: Canvas,cx: Double,cy: Double,yaw: Double,forge: Double) {
        if (forge <= .001) return
        val drawn = mutableListOf<Face>()
        for (facet in facets) {
            val pv=facet.vertices.map { rotate(it,yaw,-.18) }
            val a=V(pv[1].x-pv[0].x,pv[1].y-pv[0].y,pv[1].z-pv[0].z)
            val b=V(pv[2].x-pv[0].x,pv[2].y-pv[0].y,pv[2].z-pv[0].z)
            var nx=a.y*b.z-a.z*b.y; var ny=a.z*b.x-a.x*b.z; var nz=a.x*b.y-a.y*b.x
            val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.000001); nx/=length; ny/=length; nz/=length
            val mx=pv.sumOf { it.x }/pv.size; val my=pv.sumOf { it.y }/pv.size; val mz=pv.sumOf { it.z }/pv.size
            if (nx*mx+ny*my+nz*mz < 0) { nx=-nx; ny=-ny; nz=-nz }
            if (nz <= .02) continue
            val dif=max(0.0,-.45*nx-.6*ny+.66*nz)
            val band=exp(-((nx+yaw*.9-.25)*3.2).pow(2))
            val shade=m.clamp(.18+.55*dif+.5*band*(if (facet.kind==2) .6 else 1.0))
            val amount=(forge-facet.order*.03)/.3
            drawn.add(Face(pv,mz,shade,m.clamp(amount),m.back(amount),mx,my,facet.kind))
        }
        drawn.sortBy { it.depth }
        for (d in drawn) {
            if (d.alpha <= .001) continue
            path.reset()
            d.points.forEachIndexed { i,v ->
                val x=(cx+(d.x+(v.x-d.x)*d.scale)*46).toFloat(); val y=(cy+(d.y+(v.y-d.y)*d.scale)*46).toFloat()
                if (i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close()
            val k=if (d.kind==0) .25+.75*d.shade else d.shade
            fill.shader=null; fill.color=rgb(m.mix(58.0,214.0,k),m.mix(23.0,196.0,k*k),m.mix(160.0,255.0,k),d.alpha)
            c.drawPath(path,fill)
            edge.color=color(if (d.shade>.7) 0xf3eeff else 0x3a178f,.55*d.alpha); c.drawPath(path,edge)
        }
        fill.color=Color.WHITE; fill.alpha=(.75*m.clamp((forge-.6)/.4)*255).roundToInt()
        c.drawBitmap(glint,(cx-46*(.28-yaw*.4)-10).toFloat(),(cy-46*.3-10).toFloat(),fill)
        fill.alpha=255
    }
    private fun tiles(c: Canvas,cx: Double,cy: Double,gather: Double) {
        for (d in 1..31) {
            val idx=d-1+3; val x0=-66.0+(idx%7)*22; val y0=-44.0+(idx/7)*22
            val k=m.ease((gather-(d-1)*.012)/.64)
            if (k>=.999) continue
            val r=hypot(x0,y0)*(1-k); val a=atan2(y0,x0)+k*PI*1.65; val s=m.mix(1.0,.25,k)
            c.save(); c.translate((cx+cos(a)*r).toFloat(),(cy+sin(a)*r).toFloat()); c.rotate((k*220).toFloat()); c.scale(s.toFloat(),s.toFloat())
            val base=if (d==17) doubleArrayOf(255.0,180.0,196.0) else doubleArrayOf(201.0,184.0,255.0)
            fill.shader=null; fill.color=rgb(m.mix(base[0],124.0,k),m.mix(base[1],77.0,k),m.mix(base[2],255.0,k),1-m.smooth((k-.8)/.2))
            round(c,-9.0,-9.0,18.0,18.0,5.0)
            if (k<.15) { day.color=color(if (d==17) 0x8a1a3a else 0x3b2380,1-k/.15); c.drawText(days[d-1],-dayWidths[d-1]/2,3.2f,day) }
            c.restore()
        }
    }
    private fun text(c: Canvas,value: String,font: String,size: Float,ink: Int,x: Double,y: Double,width: Double,lineHeight: Float? = null,center: Boolean = false,underline: Boolean = false,tracking: Float = 0f): Int {
        val key="$value/$font/$size/$ink/$width/$lineHeight/$center/$underline/$tracking"
        val layout=labels.getOrPut(key) {
            val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=fonts[font]; textSize=size; color=color(ink); isUnderlineText=underline }
            val builder=StaticLayout.Builder.obtain(value,0,value.length,p,width.toInt()).setIncludePad(false)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            if (lineHeight != null) builder.setLineSpacing(lineHeight-(p.fontMetrics.descent-p.fontMetrics.ascent),1f)
            builder.build()
        }
        c.save(); c.translate(x.toFloat(),y.toFloat()); layout.draw(c); c.restore()
        return layout.height
    }
    private fun page(w: Double,h: Double,bottom: Double): Bitmap {
        val key="$w/$h/$bottom"
        if (pageKey==key) return staticPage!!
        staticPage?.recycle(); pageKey=key
        return Bitmap.createBitmap(ceil(w*2).toInt(),ceil(h*2).toInt(),Bitmap.Config.ARGB_8888).also { bitmap ->
            staticPage=bitmap
            val c=Canvas(bitmap); c.scale(2f,2f)
            fill.shader=null; fill.color=color(0xe7e7ea); round(c,18.0,h*.645,w-36,70.0,16.0)
            c.save(); path.reset(); path.addRoundRect(18f,(h*.645).toFloat(),(w-18).toFloat(),(h*.645+70).toFloat(),16f,16f,Path.Direction.CW); c.clipPath(path)
            fill.color=color(0xf6f6f8); c.drawCircle(31f,(h*.645+35).toFloat(),35f,fill)
            val glyph=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=28f; color=color(0x141218); typeface=Typeface.DEFAULT }
            c.drawText("✦",(31+9-glyph.measureText("✦")/2).toFloat(),(h*.645+35-(glyph.ascent()+glyph.descent())/2).toFloat(),glyph)
            c.restore()
            val font=TextPaint().apply { typeface=fonts["Inter-SemiBold"]; textSize=14f }
            val titleCopy="Are you loving your experience with us so far?"
            val title=StaticLayout.Builder.obtain(titleCopy,0,titleCopy.length,font,(w-106).toInt()).setIncludePad(false).setLineSpacing(19-(font.fontMetrics.descent-font.fontMetrics.ascent),1f).build()
            val ty=h*.645+(70-title.height-4-15.0)/2
            text(c,"Are you loving your experience with us so far?","Inter-SemiBold",14f,0x141218,74.0,ty,w-106,19f)
            text(c,"Give us a rating","Inter-Medium",12.5f,0x4a4652,74.0,ty+title.height+4,w-106,underline=true)
            val by=h-bottom-18-108
            fill.color=color(0x1b1a1f); round(c,18.0,by,w-36,54.0,27.0)
            text(c,"View Badge","Inter-SemiBold",16f,0xffffff,18.0,by+(54-19.36)/2,w-36,center=true)
            text(c,"Go to Home","Inter-SemiBold",15f,0x1b1a1f,18.0,by+60+(48-18.15)/2,w-36,center=true)
        }
    }
    fun draw(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,p0: Double,turn: Double,reduced: Boolean) {
        c.drawColor(color(0xf2f2f4))
        val cx=w/2; val cy=top+196; val p=if (reduced) { if (p0>.5) 1.0 else 0.0 } else p0
        val k=m.smooth((p-.3)/.55)
        val rayA=m.smooth((p-.7)/.3)
        if (rayA>.01) {
            c.save(); c.translate(cx.toFloat(),cy.toFloat()); c.rotate((turn*180/PI/4).toFloat())
            fill.color=Color.WHITE; fill.shader=rayGradient; fill.alpha=(255*(rayA*m.mix(.6,1.0,m.back((p-.7)/.3))).coerceIn(0.0,1.0)).roundToInt()
            rays.forEach { c.drawPath(it,fill) }; c.restore(); fill.shader=null; fill.alpha=255
        }
        fill.color=Color.WHITE; c.drawBitmap(shadows[(k*20).roundToInt().coerceIn(0,20)],(cx-140).toFloat(),(cy-122).toFloat(),fill)
        val cw=m.mix(186.0,128.0,k); val ch=m.mix(150.0,128.0,k); val cr=m.mix(22.0,64.0,k)
        fill.shader=LinearGradient(cx.toFloat(),(cy-ch/2).toFloat(),cx.toFloat(),(cy+ch/2).toFloat(),rgb(255.0,m.mix(255.0,143.0,k),m.mix(255.0,230.0,k)),rgb(m.mix(255.0,233.0,k),m.mix(255.0,76.0,k),m.mix(255.0,214.0,k)),Shader.TileMode.CLAMP)
        round(c,cx-cw/2,cy-ch/2,cw,ch,cr); fill.shader=null
        if (k<.6) { day.color=color(0x9a93a8,1-k/.6); for (i in 0..6) c.drawText(weekdays[i],(cx-69+i*22).toFloat(),(cy-60).toFloat(),day) }
        tiles(c,cx,cy+6*(1-k),m.clamp(p/.6))
        val flashA=exp(-((p-.62)/.07).pow(2))
        if (flashA>.02 && !reduced) { fill.color=Color.WHITE; fill.alpha=(.85*flashA*255).roundToInt(); c.drawBitmap(flash,(cx-flash.width/2).toFloat(),(cy-flash.height/2).toFloat(),fill); fill.alpha=255 }
        gem(c,cx,cy,.42*sin(turn)*m.smooth((p-.9)/.1),if (reduced) { if (p>.5) 1.0 else 0.0 } else m.clamp((p-.3)/.62))
        val tk=m.back((p-.82)/.18)
        if (tk>.01) { c.save(); c.translate((cx+50).toFloat(),(cy-46).toFloat()); c.scale(tk.toFloat(),tk.toFloat()); fill.color=color(0xffc93c); round(c,-17.0,-11.0,34.0,22.0,11.0); tag.color=color(0x3a2a00); c.drawText("×3",-10f,4.5f,tag); c.restore() }
        val cal=1-m.smooth(p0/.35); val badge=m.smooth((p0-.78)/.22); val rest=m.smooth((p0-.85)/.15)
        if (cal>.001) { c.saveLayerAlpha(null,(cal*255).roundToInt()); text(c,"October · 30 of 31 days on budget","Inter-Medium",13f,0x5b5866,0.0,cy+84+(1-cal)*-6,w,center=true); c.restore() }
        if (badge>.001) { c.saveLayerAlpha(null,(badge*255).roundToInt()); text(c,"Perfect Month","Inter-Medium",13f,0x2b2735,0.0,cy+84+(1-badge)*8,w,center=true); c.restore() }
        if (rest>.001) { c.saveLayerAlpha(null,(rest*255).roundToInt()); val y=cy+150+(1-rest)*10
            text(c,"New\nBadge unlocked!","InterDisplay-Bold",27f,0x141218,28.0,y,w-56,32f,true,tracking=-.4f)
            text(c,"You stayed within your budget. Keep up the great work!","Inter-Regular",15f,0x6d6a76,28.0,y+76,w-56,21f,true)
            c.restore()
        }
        fill.color=Color.WHITE; val bitmap=page(w,h,bottom); rect.set(0f,0f,w.toFloat(),h.toFloat()); c.drawBitmap(bitmap,null,rect,fill)
    }
}
