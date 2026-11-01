package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class TinyWorldPickerArt(context: Context) {
    private val title=Typeface.createFromAsset(context.assets,"tinyworldpicker/tinyworldpicker_TitanOne-Regular.ttf")
    private val black=Typeface.createFromAsset(context.assets,"tinyworldpicker/tinyworldpicker_Nunito-Black.ttf")
    private val bold=Typeface.createFromAsset(context.assets,"tinyworldpicker/tinyworldpicker_Nunito-ExtraBold.ttf")
    private val ion=Typeface.createFromAsset(context.assets,"tinyworldpicker/tinyworldpicker_Ionicons.ttf")
    private val material=Typeface.createFromAsset(context.assets,"tinyworldpicker/tinyworldpicker_MaterialCommunityIcons.ttf")
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaints=mutableMapOf<String,Paint>()
    private val runtime=if(Build.VERSION.SDK_INT>=33)TinyWorldPickerRuntime() else null
    private var hud: Bitmap?=null; private var hudKey=""
    private val stars: List<DoubleArray> = run {
        var seed=7L
        fun rnd(): Double { seed=(seed*16807)%2147483647; return seed.toDouble()/2147483647 }
        List(70) { doubleArrayOf(rnd(),rnd(),0.6+rnd()*1.1,rnd()*6.283,1+floor(rnd()*3)) }
    }
    private fun color(rgb: Int, alpha: Double=1.0)=((alpha*255).roundToInt().coerceIn(0,255) shl 24) or rgb
    private fun paint(rgb: Int, alpha: Double=1.0): Paint { p.reset(); p.isAntiAlias=true; p.color=color(rgb,alpha); return p }
    private fun textPaint(face: Typeface, size: Double, rgb: Int, alpha: Double, spacing: Double): Paint {
        val key="$face|$size|$rgb|$alpha|$spacing"
        return textPaints.getOrPut(key) { Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=face; textSize=size.toFloat(); color=color(rgb,alpha); if((face==black || face==bold) && (size==13.0 || size==14.0))fontFeatureSettings="tnum"} }
    }
    // Canvas has no letterSpacing; draw individual measured glyphs below.
    private fun width(text: String, face: Typeface, size: Double, spacing: Double=0.0): Float = textPaint(face,size,0xFFFFFF,1.0,spacing).measureText(text)+(text.codePointCount(0,text.length)-1).coerceAtLeast(0)*spacing.toFloat()
    private fun text(c: Canvas, text: String, face: Typeface, size: Double, x: Float, y: Float, rgb: Int=0xFFFFFF, alpha: Double=1.0, spacing: Double=0.0, shadow: Boolean=false) {
        val tp=textPaint(face,size,rgb,alpha,spacing)
        val fm=tp.fontMetrics; val baseline=y-(fm.ascent+fm.descent)/2
        var left=x-width(text,face,size,spacing)/2
        if(shadow)tp.setShadowLayer(0.01f,0f,2f,0x40000000)
        if(spacing==0.0)c.drawText(text,left,baseline,tp) else {
            for(glyph in text.codePoints().toArray()) {val s=String(Character.toChars(glyph)); c.drawText(s,left,baseline,tp); left+=tp.measureText(s)+spacing.toFloat()}
        }
        if(shadow)tp.clearShadowLayer()
    }
    private fun icon(c: Canvas, code: Int, size: Double, x: Float, y: Float, rgb: Int=0xCFEFF4, alpha: Double=1.0, mat: Boolean=false) = text(c,String(Character.toChars(code)),if(mat)material else ion,size,x,y,rgb,alpha)
    private fun round(c: Canvas,x: Float,y: Float,w: Float,h: Float,r: Float,rgb: Int,alpha: Double=1.0) {c.drawRoundRect(x,y,x+w,y+h,r,r,paint(rgb,alpha))}
    private fun glass(c: Canvas,x: Float,y: Float,w: Float,h: Float,r: Float,density: Float) {
        round(c,x,y,w,h,r,0x071A21,0.62)
        val border=2/density
        c.drawRoundRect(x+border/2,y+border/2,x+w-border/2,y+h-border/2,r-border/2,r-border/2,paint(0xA0E6F0,0.16).apply {style=Paint.Style.STROKE; strokeWidth=border})
    }
    private fun hud(w: Float,h: Float,top: Float,bottom: Float,d: Float): Bitmap {
        val key="$w|$h|$top|$bottom|$d"
        if(hudKey==key)return hud!!
        hud?.recycle(); val bitmap=Bitmap.createBitmap(ceil(w*d).toInt(),ceil(h*d).toInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap);c.scale(d,d);val cy=round(h*0.47f);val hy=top+6
        c.drawCircle(37f,hy+21,21f,paint(0x0B2129));c.drawCircle(37f,hy+21,19.5f,paint(0xFFD84A).apply {style=Paint.Style.STROKE;strokeWidth=3f})
        text(c,"20",title,16.0,37f,hy+21.5f)
        val ew=(2*2/d+20+14+64+12)+width("17,963",black,14.0)
        glass(c,66f,hy+5,ew,32f,16f,d);val ex=66+2/d+10
        icon(c,62230,14.0,ex+7,hy+21,0xFFD84A)
        round(c,ex+20,hy+17,64f,8f,4f,0xFFFFFF,0.12);round(c,ex+20,hy+17,64*0.72f,8f,4f,0xFFD84A)
        val etw=width("17,963",black,14.0);text(c,"17,963",black,14.0,ex+90+etw/2,hy+21)
        val cw=2*2/d+24+16+6+width("13,401,935",black,14.0);glass(c,w-16-cw,hy,cw,32f,16f,d)
        val cx=w-16-cw+2/d+12;c.drawCircle(cx+8,hy+16,8f,paint(0xFFC531));c.drawCircle(cx+8,hy+16,3.25f,paint(0xE39A12).apply {style=Paint.Style.STROKE;strokeWidth=1.5f})
        text(c,"13,401,935",black,14.0,cx+22+width("13,401,935",black,14.0)/2,hy+16)
        val sw1=width("18,313",black,14.0);val sw2=width(" / 20,000",bold,14.0);val sw=2*2/d+24+19+sw1+sw2
        glass(c,w-16-sw,hy+40,sw,32f,16f,d);val sx=w-16-sw+2/d+12
        icon(c,62869,13.0,sx+6.5f,hy+56,0xFFB84A);text(c,"18,313",black,14.0,sx+19+sw1/2,hy+56)
        text(c," / 20,000",bold,14.0,sx+19+sw1+sw2/2,hy+56,0xCFEFF4,0.55)
        listOf(61916,62264,62531).forEachIndexed {i,code -> val y=cy-176+i*56;glass(c,w-60,y,44f,44f,15f,d);icon(c,code,21.0,w-38,y+22)}
        val by=h-bottom-68
        glass(c,16f,by+2,52f,52f,18f,d);glass(c,w-68,by+2,52f,52f,18f,d)
        icon(c,62361,24.0,42f,by+28);icon(c,62294,24.0,w-42,by+28)
        round(c,80f,by,w-160,56f,28f,0xFFD84A)
        c.save();c.clipPath(Path().apply {addRoundRect(RectF(80f,by,w-80,by+56),28f,28f,Path.Direction.CW)})
        c.drawRect(80f,by+52,w-80,by+56,paint(0xD9A21E));c.restore()
        c.drawCircle(w-21,by+7,10f,paint(0xFF5A4E));c.drawCircle(w-21,by+7,9f,paint(0x0B2129).apply {style=Paint.Style.STROKE;strokeWidth=2f})
        text(c,"2",black,11.0,w-21,by+7)
        hud=bitmap;hudKey=key;return bitmap
    }
    fun draw(c: Canvas,w: Float,h: Float,top: Float,bottom: Float,d: Float,f: TinyWorldPickerMotion.Frame,pressed: Int) {
        val cy=round(h*0.47f);val s=round(w*0.44f)
        val bg=paint(0xFFFFFF);bg.shader=LinearGradient(0f,0f,0f,h,intArrayOf(color(0x163C49),color(0x0E2A34),color(0x081A21)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,bg)
        val a=TinyWorldPickerWorld.all[f.cur].halo;val b=TinyWorldPickerWorld.all[f.nxt].halo;val k=TinyWorldPickerMotion.smooth(0.2,0.8,f.m)
        fun channel(shift: Int): Int {val x=((a shr shift)and 255)/255.0;val y=((b shr shift)and 255)/255.0;return (((x.pow(2.2)*(1-k)+y.pow(2.2)*k).pow(1/2.2))*255).roundToInt()}
        val halo=(channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        val hp=paint(0xFFFFFF);hp.shader=RadialGradient(w/2,cy,w*0.62f,intArrayOf(color(halo,0.32),color(0x12303A,0.0)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,cy-260,w,cy+260,hp)
        for(st in stars) { val op=0.18+0.42*(0.5+0.5*sin(f.t*2*PI*st[4]/9+st[3]));c.drawCircle((st[0]*w).toFloat(),(st[1]*h).toFloat(),st[2].toFloat(),paint(0xB6E4EC,op)) }
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {val sp=paint(0xFFFFFF);sp.shader=runtime.shader(w,cy,s,d,f);c.drawRect(0f,cy-250,w,cy+250,sp)} else fallback(c,w,cy,s,f)
        val bitmap=hud(w,h,top,bottom,d);val by=h-bottom-68
        val controls=listOf(RectF(w-60,cy-176,w-16,cy-132),RectF(w-60,cy-120,w-16,cy-76),RectF(w-60,cy-64,w-16,cy-20),RectF(16f,by+2,68f,by+54),RectF(w-68,by+2,w-16,by+54),RectF(80f,by,w-80,by+56))
        if(pressed in controls.indices) {
            val rect=controls[pressed];val crop=RectF(rect);if(pressed==4)crop.inset(-6f,-6f)
            c.save();c.clipOutRect(crop);c.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint(0xFFFFFF));c.restore()
            c.save();c.scale(0.96f,0.96f,rect.centerX(),rect.centerY());c.clipRect(crop);c.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint(0xFFFFFF));c.restore()
        } else c.drawBitmap(bitmap,null,RectF(0f,0f,w,h),paint(0xFFFFFF))
        val ms=sin(PI*TinyWorldPickerMotion.clamp(f.m));val bob=sin(f.t*2*PI*2/9)*2.5-ms*6
        c.save();c.translate(w/2,cy-156+bob.toFloat());c.scale((1-0.12*ms).toFloat(),(1-0.12*ms).toFloat())
        round(c,-22f,-22f,44f,44f,14f,0xFFFFFF,0.14)
        c.drawRoundRect(-22+1/d,-22+1/d,22-1/d,22-1/d,14-1/d,14-1/d,paint(0xA0E6F0,0.16).apply {style=Paint.Style.STROKE;strokeWidth=2/d})
        TinyWorldPickerWorld.all.forEachIndexed { i,wd -> val v=TinyWorldPickerMotion.vis(i,f);if(v>0)icon(c,wd.glyph,24.0,0f,0f,0xFFFFFF,v,true) };c.restore()
        TinyWorldPickerWorld.all.forEachIndexed {i,wd ->
            val v=TinyWorldPickerMotion.labelVis(i,f);val vis=TinyWorldPickerMotion.vis(i,f)
            if(v>0.0001) {
                c.saveLayerAlpha(null,(v*255).roundToInt());c.save()
                val incoming=i==f.nxt && f.nxt!=f.cur;c.translate(w/2,cy+180+((1-v)*(if(incoming)10 else -8)).toFloat());c.scale((0.96+0.04*v).toFloat(),(0.96+0.04*v).toFloat())
                text(c,wd.name,title,32.0,0f,-21f,shadow=true)
                val tint=if(wd.done)0x6BDC74 else if(i==2)0xFFC14D else 0x7FE6F5
                val tw=width(wd.status,black,12.0,1.4);val pw=tw+27+(if(wd.done)20 else 0)
                round(c,-pw/2,8f,pw,28f,14f,tint,31.0/255)
                c.drawRoundRect(-pw/2+0.75f,8.75f,pw/2-0.75f,35.25f,13.25f,13.25f,paint(tint,136.0/255).apply {style=Paint.Style.STROKE;strokeWidth=1.5f})
                if(wd.done)icon(c,61982,14.0,-pw/2+20.5f,22f,tint)
                text(c,wd.status,black,12.0,if(wd.done)10f else 0f,22f,tint,spacing=1.4);c.restore()
                val total=wd.stats.sumOf {width(it,bold,13.0).toDouble()+19}+36;var x=(w-total).toFloat()/2
                wd.stats.forEachIndexed {j,stat -> val twidth=width(stat,bold,13.0);val y=cy+236+((1-v)*6).toFloat();icon(c,listOf(62942,61985,62264)[j],14.0,x+7,y,0xCFEFF4,0.62);text(c,stat,bold,13.0,x+19+twidth/2,y,0xCFEFF4,0.78);x+=19+twidth+18}
                c.save();if(pressed==5)c.scale(0.96f,0.96f,w/2,by+28)
                text(c,wd.cta,black,18.0,w/2,h-bottom-42+((1-v)*6).toFloat(),0x2A1E05,spacing=0.2);c.restore();c.restore()
            }
            val dw=(7+15*vis).toFloat();round(c,w/2-45+i*30-dw/2,cy+270.5f,dw,7f,3.5f,0xCFEFF4,0.35+0.65*vis)
        }
    }
    private fun fallback(c: Canvas,w: Float,cy: Float,s: Float,f: TinyWorldPickerMotion.Frame) {
        // API 26–32: drawn toy solids retain the four silhouettes and the same morph/squash.
        fun world(i: Int,alpha: Double) {
            c.saveLayerAlpha(null,(alpha*255).roundToInt());c.save();c.translate(w/2,cy);c.scale((1+f.sq*0.5).toFloat(),(1-f.sq).toFloat());c.rotate((f.yaw*180/PI).toFloat()*0.15f)
            val r=s*0.85f
            when(i) {
                0 -> {c.drawCircle(0f,0f,r,paint(0x48C9D9));for(j in 0..6) {val a=j*2*PI/7;c.drawCircle((cos(a)*r*0.82).toFloat(),(sin(a)*r*0.72).toFloat(),r*0.29f,paint(0xFFFFFF))}}
                1 -> {c.drawCircle(0f,0f,r,paint(0x8ED343));c.drawOval(-r*1.12f,-r*0.55f,r*1.12f,r*0.55f,paint(0x343640).apply {style=Paint.Style.STROKE;strokeWidth=r*0.25f});for(j in 0..4)c.drawCircle((cos(j*1.4)*r*0.7).toFloat(),(sin(j*1.4)*r*0.6).toFloat(),r*0.18f,paint(0x33994D))}
                2 -> {for((j,col)in listOf(0xE39445,0x6FC640,0xF4C52E,0x6B3821,0xE39445).withIndex())round(c,-r,-r*0.65f+j*r*0.28f,2*r,r*0.35f,r*0.17f,col);c.drawArc(-r,-r,r,0f,180f,180f,true,paint(0xF6B858))}
                else -> {c.drawOval(-r,-r*0.5f,r,r*0.75f,paint(0xA78757));c.drawOval(-r,-r*0.8f,r,r*0.45f,paint(0xDAB082).apply {style=Paint.Style.STROKE;strokeWidth=r*0.23f});for(j in 0..8)round(c,-r*0.85f+j*r*0.2f,-r*0.25f,r*0.09f,r*0.3f,r*0.045f,0x63553D)}
            };c.restore();c.restore()
        }
        world(f.cur,1-TinyWorldPickerMotion.smooth(0.25,0.75,f.m));if(f.cur!=f.nxt)world(f.nxt,TinyWorldPickerMotion.smooth(0.25,0.75,f.m))
    }
    fun close() {hud?.recycle();hud=null}
}
