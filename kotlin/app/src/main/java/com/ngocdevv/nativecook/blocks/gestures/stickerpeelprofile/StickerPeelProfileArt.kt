package com.ngocdevv.nativecook.blocks.gestures.stickerpeelprofile

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class StickerPeelProfileArt(context:Context) {
    private val fonts=listOf("Medium","SemiBold","Bold").associateWith { Typeface.createFromAsset(context.assets,"stickerpeelprofile/stickerpeelprofile_Inter-$it.ttf") }
    private val outfit=Typeface.createFromAsset(context.assets,"stickerpeelprofile/stickerpeelprofile_Outfit-Medium.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val silhouettes=(0..3).map(::silhouette)
    private val faces=(0..3).map { k -> Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888).also { b -> Canvas(b).apply { scale(3f,3f); translate(80f,80f); face(this,k) } } }
    private val shadows=(0..3).map { k -> Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888).also { b -> Canvas(b).apply { scale(3f,3f); translate(80f,80f); reset(0x000000,.28); paint.maskFilter=BlurMaskFilter(5f,BlurMaskFilter.Blur.NORMAL); drawPath(silhouettes[k],paint); paint.maskFilter=null } } }
    private var page:Bitmap?=null; private var key=""
    private fun col(hex:Int,a:Double=1.0)=Color.argb((a*255).roundToInt(),(hex shr 16)and 255,(hex shr 8)and 255,hex and 255)
    private fun reset(hex:Int,a:Double=1.0) { paint.reset(); paint.isAntiAlias=true; paint.color=col(hex,a) }
    private fun grad(colors:IntArray,x0:Float,y0:Float,x1:Float,y1:Float,alphas:DoubleArray?=null) {
        reset(0xffffff); paint.shader=LinearGradient(x0,y0,x1,y1,colors.mapIndexed { i,v -> col(v,alphas?.get(i)?:1.0) }.toIntArray(),null,Shader.TileMode.CLAMP)
    }
    private fun silhouette(k:Int):Path=Path().apply {
        when(k) {
            0 -> {
                addRoundRect(-48f,-36f,48f,46f,30f,30f,Path.Direction.CW)
                moveTo(-40f,-18f); lineTo(-30f,-46f); lineTo(-12f,-30f); close()
                moveTo(40f,-18f); lineTo(30f,-46f); lineTo(12f,-30f); close()
            }
            1 -> { moveTo(0f,-10f); cubicTo(-9f,-24f,-28f,-20f,-26f,-2f); cubicTo(-24f,10f,-10f,18f,0f,24f); cubicTo(10f,18f,24f,10f,26f,-2f); cubicTo(28f,-20f,9f,-24f,0f,-10f); close() }
            2 -> addRoundRect(-42f,-18f,42f,18f,18f,18f,Path.Direction.CW)
            else -> { moveTo(2f,-28f); cubicTo(10f,-16f,24f,-10f,22f,8f); cubicTo(20f,22f,10f,28f,0f,28f); cubicTo(-12f,28f,-22f,20f,-22f,6f); cubicTo(-22f,-6f,-13f,-10f,-11f,-20f); cubicTo(-6f,-12f,-4f,-8f,-2f,-5f); cubicTo(2f,-13f,1f,-20f,2f,-28f); close() }
        }
    }
    private fun face(c:Canvas,k:Int) {
        val sil=silhouettes[k]
        reset(0xffffff); paint.style=Paint.Style.STROKE; paint.strokeWidth=8f; paint.strokeJoin=Paint.Join.ROUND; c.drawPath(sil,paint)
        when(k) {
            0 -> grad(intArrayOf(0xf8cf6a,0xefb24a,0xd9892a),0f,-46f,0f,46f)
            1 -> grad(intArrayOf(0xff6f84,0xf2304d,0xc4122f),-20f,-20f,20f,24f)
            2 -> grad(intArrayOf(0xff7ac8,0xf2489e,0xc93cd8),-42f,0f,42f,0f)
            else -> grad(intArrayOf(0xffe066,0xff9a2e,0xf2501f),0f,-28f,0f,28f)
        }
        c.drawPath(sil,paint)
        when(k) {
            0 -> {
                reset(0xb8661c,.35); c.drawOval(-40f,-34f,40f,-8f,paint)
                reset(0x3a2412); c.drawOval(-22f,-2f,-13f,10f,paint); c.drawOval(13f,-2f,22f,10f,paint)
                paint.style=Paint.Style.STROKE; paint.strokeWidth=2.4f; paint.strokeCap=Paint.Cap.ROUND
                c.drawPath(Path().apply { moveTo(-7f,14f); quadTo(-3.5f,19f,0f,14f); quadTo(3.5f,19f,7f,14f) },paint)
                repeat(3) { i -> c.drawLine(-46f,4f+i*5,-36f,6f+i*4,paint); c.drawLine(46f,4f+i*5,36f,6f+i*4,paint) }
                reset(0xff8a8a,.55); c.drawOval(-34f,12f,-20f,20f,paint); c.drawOval(20f,12f,34f,20f,paint)
            }
            2 -> {
                reset(0xffffff); paint.typeface=fonts["Bold"]; paint.textSize=24f; val tw=paint.measureText("slay")
                c.save(); c.skew(-.2f,0f); c.drawText("slay",-tw/2+2,8f,paint); c.restore()
            }
            3 -> {
                val p=Path().apply { moveTo(1f,-4f); cubicTo(6f,4f,12f,8f,11f,16f); cubicTo(10f,23f,5f,26f,0f,26f); cubicTo(-6f,26f,-11f,22f,-11f,15f); cubicTo(-11f,9f,-6f,6f,-5f,1f); cubicTo(-2f,5f,-1f,7f,0f,8f); cubicTo(2f,4f,1f,0f,1f,-4f); close() }
                grad(intArrayOf(0xfff2a8,0xffc33a),0f,-4f,0f,26f); c.drawPath(p,paint)
            }
        }
        c.save(); c.clipPath(sil); grad(intArrayOf(0xffffff,0xffffff),-30f,-30f,10f,10f,doubleArrayOf(.45,0.0)); c.drawRect(-60f,-60f,60f,60f,paint); c.restore()
    }
    private fun halfPlane(px:Double,py:Double,nx:Double,ny:Double):Path {
        val tx=-ny; val ty=nx; val b=400
        return Path().apply { moveTo((px+tx*b).toFloat(),(py+ty*b).toFloat()); lineTo((px+tx*b+nx*b).toFloat(),(py+ty*b+ny*b).toFloat()); lineTo((px-tx*b+nx*b).toFloat(),(py-ty*b+ny*b).toFloat()); lineTo((px-tx*b).toFloat(),(py-ty*b).toFloat()); close() }
    }
    fun draw(c:Canvas,ss:List<StickerPeelProfileMotion.Sticker>,order:List<Int>) {
        val box=RectF(-80f,-80f,80f,80f)
        for(i in order) {
            val s=ss[i]; c.save(); c.translate(s.x.toFloat(),s.y.toFloat()); c.rotate((s.rot+(s.vx*.015).coerceIn(-14.0,14.0)).toFloat())
            val sc=(1+.1*s.lift)*s.k; c.scale((sc*(1+s.sq)).toFloat(),(sc*(1-s.sq)).toFloat())
            if(s.lift>.01) { val h=StickerPeelProfileMotion.half[i]; reset(0,.4*s.lift); paint.maskFilter=BlurMaskFilter((6+10*s.lift).toFloat(),BlurMaskFilter.Blur.NORMAL); c.drawRoundRect((-h.first+6).toFloat(),(-h.second+14*s.lift).toFloat(),(h.first-6).toFloat(),(h.second+14*s.lift).toFloat(),16f,16f,paint); paint.maskFilter=null }
            reset(0xffffff); paint.isFilterBitmap=true
            if(s.f<.002) { c.drawBitmap(faces[i],null,box,paint); c.restore(); continue }
            val h=StickerPeelProfileMotion.half[i]; val cx=if(s.corner==0 || s.corner==3)-h.first-4 else h.first+4; val cy=if(s.corner==0 || s.corner==1)-h.second-4 else h.second+4
            val dl=hypot(cx,cy); val nx=cx/dl; val ny=cy/dl; val d=s.f*dl; val px=cx-nx*d; val py=cy-ny*d
            c.save(); c.clipPath(halfPlane(px,py,-nx,-ny)); c.drawBitmap(faces[i],null,box,paint); c.restore()
            val z=2*(px*nx+py*ny); val m=Matrix().apply { setValues(floatArrayOf((1-2*nx*nx).toFloat(),(-2*nx*ny).toFloat(),(z*nx).toFloat(),(-2*nx*ny).toFloat(),(1-2*ny*ny).toFloat(),(z*ny).toFloat(),0f,0f,1f)) }
            val clip=halfPlane(px,py,nx,ny)
            c.save(); c.translate(2f,4f); c.concat(m); c.clipPath(clip); c.drawBitmap(shadows[i],null,box,paint); c.restore()
            c.save(); c.concat(m); c.clipPath(clip)
            grad(intArrayOf(0xd9d6dd,0xf6f4f8,0xffffff),px.toFloat(),py.toFloat(),(px+nx*d).toFloat(),(py+ny*d).toFloat())
            paint.style=Paint.Style.STROKE; paint.strokeWidth=8f; paint.strokeJoin=Paint.Join.ROUND; c.drawPath(silhouettes[i],paint); paint.style=Paint.Style.FILL; c.drawPath(silhouettes[i],paint); c.restore(); c.restore()
        }
    }
    private fun height(size:Float,weight:String="Medium"):Float { reset(0xffffff); paint.typeface=fonts[weight]; paint.textSize=size; return ceil(paint.fontMetrics.descent-paint.fontMetrics.ascent) }
    private fun text(c:Canvas,s:String,size:Float,weight:String,x:Float,y:Float,hex:Int=0xffffff,a:Double=1.0,center:Boolean=false):Float {
        reset(hex,a); paint.typeface=fonts[weight]; paint.textSize=size; val w=paint.measureText(s)
        c.drawText(s,if(center)x-w/2 else x,y-paint.fontMetrics.ascent,paint); return w
    }
    private fun round(c:Canvas,x:Float,y:Float,w:Float,h:Float,r:Float,hex:Int) { reset(hex); c.drawRoundRect(x,y,x+w,y+h,r,r,paint) }
    private fun gradientBox(c:Canvas,x:Float,y:Float,w:Float,h:Float,r:Float,colors:IntArray,diag:Boolean=false) { grad(colors,x,y,if(diag)x+w else x,y+h); c.drawRoundRect(x,y,x+w,y+h,r,r,paint) }
    fun drawPage(c:Canvas,w:Float,h:Float,top:Float,bottom:Float,density:Float) {
        val newKey="$w,$h,$top,$bottom,$density"
        if(newKey!=key) {
            key=newKey; page?.recycle()
            page=Bitmap.createBitmap((w*density).roundToInt(),(h*density).roundToInt(),Bitmap.Config.ARGB_8888)
            val p=Canvas(page!!); p.scale(density,density); page(p,w,h,top,bottom)
        }
        reset(0xffffff); c.drawBitmap(page!!,null,RectF(0f,0f,w,h),paint)
    }
    fun drawClose(c:Canvas,w:Float,h:Float,b:Float,pressed:Boolean) {
        c.save(); c.translate(w/2,h-b-42); if(pressed)c.scale(.96f,.96f)
        round(c,-28f,-28f,56f,56f,18f,0xffffff); text(c,"✕",20f,"Bold",0f,-height(20f,"Bold")/2,0x151517,center=true); c.restore()
    }
    private fun page(c:Canvas,w:Float,h:Float,t:Float,b:Float) {
        c.drawColor(col(0x151517))
        for((x,g) in listOf(w-112 to "◌",w-60 to "⋮")) { round(c,x,t+10,42f,42f,14f,0x2a2a2e); text(c,g,18f,"Bold",x+21,t+10+(42-height(18f,"Bold"))/2,center=true) }
        text(c,"Rio Vega",24f,"Bold",w/2,t+214,center=true)
        val hy=t+214+height(24f,"Bold")+2; text(c,"@riovega",14f,"Medium",w/2,hy,a=.55,center=true)
        val fy=hy+height(14f)+18; reset(0xffffff); paint.typeface=fonts["SemiBold"]; paint.textSize=15f; val fw=64+paint.measureText("1 friend")
        round(c,w/2-fw/2,fy,fw,44f,14f,0x242427); gradientBox(c,w/2-fw/2+14,fy+9,26f,26f,8f,intArrayOf(0xffb36a,0xff5a8a)); text(c,"1 friend",15f,"SemiBold",w/2-fw/2+50,fy+(44-height(15f,"SemiBold"))/2)
        val by=t+318; gradientBox(c,18f,by,w-36,70f,16f,intArrayOf(0x3a1170,0x7a2fc9,0xb04cff),true)
        val ty=by+(70-height(18f,"Bold")-2-height(13f))/2; text(c,"Music Compatibility",18f,"Bold",34f,ty); text(c,"Discover your score!",13f,"Medium",34f,ty+height(18f,"Bold")+2,a=.75)
        c.save(); c.translate(w-70,by+38); c.rotate(6f); round(c,-26f,-30f,52f,60f,10f,0x5a1fa8); text(c,"?",34f,"Bold",0f,-height(34f,"Bold")/2,0x1a0838,center=true); c.restore()
        val ly=by+92; text(c,"Recently played",15f,"SemiBold",18f,ly,a=.6); reset(0xffffff); paint.typeface=fonts["SemiBold"]; paint.textSize=15f; val mw=paint.measureText("See more"); text(c,"See more",15f,"SemiBold",w-18-mw,ly)
        val songs=listOf(Triple("Glass Garden","Mira Sol",intArrayOf(0x2ad4c4,0x3a5bff)),Triple("Night Bus Home","Ode Kim",intArrayOf(0xffcf6a,0xff5a8a)),Triple("Fizz - Remix","Tavi, Rook",intArrayOf(0xff7ac8,0x7a3cff)))
        songs.forEachIndexed { i,s ->
            val y=ly+height(15f,"SemiBold")+14+i*66
            gradientBox(c,18f,y,52f,52f,6f,s.third,true); reset(0xffffff,.35); c.drawCircle(44f,y+26,13f,paint)
            val ry=y+(52-height(16f)-2-height(14f))/2; text(c,s.first,16f,"Medium",84f,ry); text(c,s.second,14f,"Medium",84f,ry+height(16f)+2,a=.55)
            reset(0xffffff); paint.typeface=fonts["SemiBold"]; paint.textSize=12f; val aw=paint.measureText("4 hours ago")+24
            round(c,w-18-aw,y+11,aw,30f,15f,0x242427); text(c,"4 hours ago",12f,"SemiBold",w-18-aw+12,y+11+(30-height(12f,"SemiBold"))/2,a=.7)
        }
    }
}
