package com.ngocdevv.nativecook.blocks.cards.popupquestcard

import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.popupquestcard.PopUpQuestCardMotion.Layout
import com.ngocdevv.nativecook.blocks.cards.popupquestcard.PopUpQuestCardMotion.Pose
import com.ngocdevv.nativecook.blocks.cards.popupquestcard.PopUpQuestCardMotion.mix
import com.ngocdevv.nativecook.blocks.cards.popupquestcard.PopUpQuestCardMotion.smooth
import com.ngocdevv.nativecook.blocks.cards.popupquestcard.PopUpQuestCardMotion.clamp

internal class PopUpQuestCardArt(private val r: PopUpQuestCardResources) {
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun panel(cv: Canvas,x: Double,y: Double,w: Double,h: Double,rad: Double,opacity: Double,blur: Double,dy: Double) {
        val p=r.paint("#FFFFFF"); p.setShadowLayer(blur.toFloat(),0f,dy.toFloat(),Color.argb((255*opacity).toInt(),58,46,31))
        cv.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),rad.toFloat(),rad.toFloat(),p)
    }
    private fun grid(l: Layout,hidden: Int): Bitmap = r.cached("grid-$l-$hidden",ceil(l.w).toInt(),ceil(l.h).toInt()) { cv ->
        val lh=r.line(14.0)+2+r.line(26.0,true)
        r.text(cv,"Hi, Mia & Theo",20.0,l.top+8,14.0,color="#8C8A93")
        r.text(cv,"Where to next?",20.0,l.top+10+r.line(14.0),26.0,true)
        r.round(cv,l.w-64,l.top+8+(lh-44)/2,44.0,44.0,22.0,"#F6E3C8")
        r.icon(cv,"happy-outline",l.w-53,l.top+8+(lh-22)/2,22.0,"#5A4A3A")
        panel(cv,16.0,l.top+74,l.w-32,50.0,25.0,0.06,12.0,4.0)
        r.icon(cv,"search",34.0,l.top+90,18.0,"#8C8A93")
        r.text(cv,"Search next adventure…",62.0,l.top+74+(50-r.line(15.0))/2,15.0,color="#A9A6B2")
        r.text(cv,"Recommended for you!",20.0,l.top+140,20.0,true)
        for(i in 0..3) {
            val q=r.quests[i]; val c=l.card(i); val b=l.illustration(i)
            panel(cv,c.x,c.y,c.w,c.h,26.0,0.07,14.0,6.0)
            if(hidden!=i) {
                cv.save(); val path=Path(); path.addRoundRect(RectF(b.x.toFloat(),b.y.toFloat(),(b.x+b.w).toFloat(),(b.y+b.h).toFloat()),18f,18f,Path.Direction.CW); cv.clipPath(path)
                cv.drawColor(Color.parseColor(q.bg))
                val sc=max(b.w/1000,b.h/700)
                cv.translate((b.x+b.w/2-500*sc).toFloat(),(b.y+b.h-700*sc).toFloat()); cv.scale(sc.toFloat(),sc.toFloat())
                for(a in q.art)cv.drawBitmap(a,-100f,-100f,bitmapPaint)
                cv.restore()
                r.round(cv,b.x+b.w-36,b.y+8,28.0,28.0,14.0,"#FFFFFF",0.85)
                r.icon(cv,"heart-outline",b.x+b.w-29.5,b.y+14.5,15.0,"#3A3442")
            }
            cv.save(); cv.clipRect(c.x.toFloat(),(b.y+b.h).toFloat(),(c.x+c.w-8).toFloat(),(c.y+c.h).toFloat())
            r.text(cv,q.title,c.x+12,b.y+b.h+10,15.0,true)
            r.text(cv,q.price,c.x+12,b.y+b.h+12+r.line(15.0,true),12.0,color="#8C8A93"); cv.restore()
        }
        val y=l.h-l.bottom-68; val width=l.w-48
        panel(cv,24.0,y,width,60.0,30.0,0.08,16.0,6.0)
        val gap=(width-240)/5; var x=24+gap/2
        listOf("compass-outline","heart-outline","sparkles","document-text-outline","notifications-outline").forEachIndexed { i,name ->
            val w=if(i==2)64.0 else 44.0
            if(i==2)r.round(cv,x,y+8,w,44.0,22.0,"#2A2633")
            r.icon(cv,name,x+(w-21)/2,y+19.5,21.0,if(i==2)"#FFFFFF" else "#6B6876"); x+=w+gap
        }
    }
    private val catsY get()=29+r.line(28.0,true)+16
    private val subY get()=catsY+58+r.line(12.0)+20
    private val actsY get()=subY+r.line(18.0,true)+6
    private fun sheet(l: Layout,i: Int): Bitmap = r.cached("sheet-$l-$i",ceil(l.w).toInt(),ceil(l.h-l.sheetTop).toInt()) { cv ->
        val q=r.quests[i]; val h=l.h-l.sheetTop
        val path=Path(); path.addRoundRect(RectF(0f,0f,l.w.toFloat(),h.toFloat()),floatArrayOf(32f,32f,32f,32f,0f,0f,0f,0f),Path.Direction.CW); cv.drawPath(path,r.paint("#FFFFFF"))
        r.round(cv,(l.w-40)/2,10.0,40.0,5.0,3.0,"#E6E1EA")
        r.text(cv,q.title+"!",20.0,29.0,28.0,true)
        r.text(cv,"Activities:",20.0,subY,18.0,true)
        val y=h-l.bottom-68
        r.round(cv,20.0,y,l.w-108,56.0,28.0,"#2A2633")
        r.text(cv,"Book now · "+q.price.removePrefix("From "),20.0,y+(56-r.line(17.0,true))/2,17.0,true,"#FFFFFF",l.w-108,true)
        r.round(cv,l.w-76,y,56.0,56.0,28.0,"#FDECEA")
        r.icon(cv,"calendar-outline",l.w-59,y+17,22.0,"#E2574C")
    }
    private fun item(l: Layout,i: Int,k: Int): Bitmap = r.cached("item-$l-$i-$k",ceil(l.w).toInt(),ceil(l.h-l.sheetTop).toInt()) { cv ->
        val q=r.quests[i]
        if(k<4) {
            val x=20+k*(l.w-112)/3; val y=catsY
            r.round(cv,x+10,y,52.0,52.0,18.0,q.bg); r.icon(cv,q.cats[k].first,x+25,y+15,22.0,q.tint)
            r.text(cv,q.cats[k].second,x,y+58,12.0,color="#6B6876",width=72.0,center=true)
        } else {
            val a=q.acts[k-4]; val y=actsY+(k-4)*72+8
            r.round(cv,20.0,y,l.w-40,64.0,20.0,"#FBF8F3"); r.round(cv,32.0,y+11,42.0,42.0,14.0,q.bg)
            r.icon(cv,a.first,43.0,y+22,20.0,q.tint)
            val ty=y+(64-r.line(15.0,true)-2-r.line(12.0))/2
            r.text(cv,a.second,86.0,ty,15.0,true); r.text(cv,a.third,86.0,ty+r.line(15.0,true)+2,12.0,color="#8C8A93")
            r.icon(cv,"chevron-forward",l.w-50,y+23,18.0,"#B4B0BE")
        }
    }
    private fun matrix(l: Layout,p: Pose,i: Int,reduced: Boolean,layer: Int,hinge: Double): Matrix {
        val f=l.frame(p,i,reduced); val e=if(reduced)1.0 else p.o
        val sc=mix(max(f.w/1000,f.h/700),min(f.w/1000,f.h/700),e)
        val phi=if(reduced)0.0 else 0.82*smooth(0.15,1.0,p.o); val yaw=if(reduced)0.0 else p.yaw
        val cp=cos(phi); val sp=sin(phi); val cy=cos(yaw); val sy=sin(yaw); val s=(700-hinge)*sc
        val yv=if(layer==1)sc*cp else sc; val yc=if(layer==1)-800*sc*cp else (-100-hinge)*sc-s*cp
        val zv=if(layer==1)sc*sp else 0.0; val zc=if(layer==1)-800*sc*sp else -s*sp
        val xa=cy*sc; val xb=sy*zv; val xc=-600*sc*cy+sy*zc
        val qa=sy*sc/760; val qb=-cy*zv/760; val qc=1-(600*sc*sy+cy*zc)/760
        val cx=f.x+f.w/2; val bot=f.y+f.h
        return Matrix().apply { setValues(doubleArrayOf(xa+cx*qa,xb+cx*qb,xc+cx*qc,bot*qa,yv+bot*qb,yc+bot*qc,qa,qb,qc).map { it.toFloat() }.toFloatArray()) }
    }
    fun draw(cv: Canvas,l: Layout,p: Pose,active: Int,reduced: Boolean) {
        cv.drawColor(Color.parseColor("#FBF8F3"))
        val k=smooth(0.0,0.55,p.o); cv.save(); cv.scale((1-0.04*k).toFloat(),(1-0.04*k).toFloat(),(l.w/2).toFloat(),(l.h/2).toFloat())
        bitmapPaint.alpha=((1-k)*255).toInt().coerceIn(0,255); cv.drawBitmap(grid(l,if(p.o>0.001)active else -1),0f,0f,bitmapPaint); bitmapPaint.alpha=255; cv.restore()
        // Recorded Skia pictures remain visible at zero group opacity in the reference.
        val opacity=if(reduced)clamp(p.o*2) else 1.0
        if(opacity>0) {
            cv.saveLayerAlpha(null,(opacity*255).toInt()); val b=l.clip(p,active,reduced); val e=if(reduced)1.0 else p.o
            val clip=Path(); clip.addRoundRect(RectF(b.x.toFloat(),b.y.toFloat(),(b.x+b.w).toFloat(),(b.y+b.h).toFloat()),(18*(1-e)).toFloat().coerceAtLeast(0f),(18*(1-e)).toFloat().coerceAtLeast(0f),Path.Direction.CW); cv.clipPath(clip); cv.drawColor(Color.parseColor(r.quests[active].bg))
            val q=r.quests[active]
            for(j in 0..4) {
                cv.save(); cv.concat(matrix(l,p,active,reduced,j,q.hinges[j])); cv.drawBitmap(q.art[j],0f,0f,bitmapPaint)
                if(j==1 && !reduced) {
                    val paint=r.paint("#1D2A3A"); paint.alpha=(0.14*smooth(0.4,1.0,p.o)*255).toInt()
                    q.hinges.drop(2).filter { it<700 }.forEach { cv.drawOval(RectF(250f,(it+74).toFloat(),950f,(it+108).toFloat()),paint) }
                }
                cv.restore()
            }
            cv.restore()
        }
        val y=l.sheetTop+(1-smooth(0.25,1.0,p.o))*(l.h-l.sheetTop+40)
        cv.drawBitmap(sheet(l,active),0f,y.toFloat(),bitmapPaint)
        for(j in 0..5) { val v=smooth(0.45+j*0.05,0.8+j*0.05,p.o); bitmapPaint.alpha=(v*255).toInt(); cv.drawBitmap(item(l,active,j),0f,(y+(1-v)*12).toFloat(),bitmapPaint) }
        bitmapPaint.alpha=255
        cv.saveLayerAlpha(null,(smooth(0.6,1.0,p.o)*255).toInt())
        r.round(cv,16.0,l.top+6,44.0,44.0,22.0,"#FFFFFF",0.92); r.icon(cv,"chevron-back",27.0,l.top+17,22.0,"#2A2633"); cv.restore()
    }
}
