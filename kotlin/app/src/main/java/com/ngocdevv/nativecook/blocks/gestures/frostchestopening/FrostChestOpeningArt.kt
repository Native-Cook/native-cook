package com.ngocdevv.nativecook.blocks.gestures.frostchestopening

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.Frame
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.T
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.easeOut
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.mix
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.rand
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningMotion.seg
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningTheme.C
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningTheme.L
import com.ngocdevv.nativecook.blocks.gestures.frostchestopening.FrostChestOpeningTheme.COPY

/** Native Canvas is Compose's GPU Canvas. Assets, fonts, shader programs and card faces are cached. */
internal class FrostChestOpeningArt(context: Context) {
    private val bitmaps = listOf("chest-closed.webp", "chest-open.webp", "card-front.webp", "card-back.webp", "floor.webp", "backdrop.jpg", "jacket.webp", "xp-boost.webp", "winter-boots.webp", "frozen-shrine.webp", "coin.webp", "crystal.webp").associateWith { name ->
        context.assets.open("frostchestopening/frostchestopening_$name").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }
    private val faces = listOf("Black", "ExtraBold", "Bold", "SemiBold", "Regular").associateWith {
        Typeface.createFromAsset(context.assets, "frostchestopening/frostchestopening_Poppins-$it.ttf")
    }
    private val arrow = requireNotNull(PathParser.createPathFromPathData("M19 12H5 M12 19l-7-7 7-7"))
    private val hand = requireNotNull(PathParser.createPathFromPathData("M10 9.5V4a2 2 0 0 0-4 0v10 M14 10V9a2 2 0 0 0-4 0v1 M18 11v-1a2 2 0 0 0-4 0 M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"))
    private val starPath = requireNotNull(PathParser.createPathFromPathData("M0 -10 Q1.8 -1.8 10 0 Q1.8 1.8 0 10 Q-1.8 1.8 -10 0 Q-1.8 -1.8 0 -10 Z"))
    private val shaders = if (Build.VERSION.SDK_INT >= 33) FrostChestOpeningRuntime() else null
    private var sx = 1.0; private var sy = 1.0
    private var width = 402.0; private var height = 874.0; private var top = 0.0
    private lateinit var c: Canvas
    private var labelScale = -1.0
    private var cardHeightScale = -1.0
    private var backdropNode: RenderNode? = null
    private var panelNode: RenderNode? = null
    private val labels = mutableMapOf<String, Paint>()
    private val blurFaces = mutableMapOf<String, Bitmap>()
    private val cards = mutableMapOf<String, Bitmap>()
    private fun x(v: Number) = v.toDouble() * sx
    private fun y(v: Number) = v.toDouble() * sy
    private fun s(v: Number) = v.toDouble() * sx
    private fun color(v: Int, a: Double = 1.0) = ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or v
    private fun paint(v: Int = 0xffffff, a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = color(v, a) }
    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble()+w.toDouble()).toFloat(), (y.toDouble()+h.toDouble()).toFloat())
    private val blurRadii = java.util.WeakHashMap<MaskFilter, Float>()
    private val blurNodes = mutableListOf<RenderNode>()
    private var blurIndex = 0
    private val softMasks = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 96
    }
    private fun blurFilter(sigma: Float): MaskFilter = BlurMaskFilter(max(0.001f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL).also { blurRadii[it] = sigma }
    private inline fun blurred(sigma: Float, bounds: RectF = rect(0, 0, width, height), block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated && sigma > 0.1f) {
            val index = blurIndex++
            val node = if (index < blurNodes.size) blurNodes[index] else RenderNode("FrostChestOpening blur").also { blurNodes.add(it) }
            val pad = ceil(sigma * 3).toInt()
            val left = floor(bounds.left).toInt() - pad
            val top = floor(bounds.top).toInt() - pad
            val right = ceil(bounds.right).toInt() + pad
            val bottom = ceil(bounds.bottom).toInt() + pad
            node.setPosition(left, top, right, bottom)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.DECAL))
            val previous = c
            c = node.beginRecording()
            c.translate(-left.toFloat(), -top.toFloat())
            try { block() } finally { node.endRecording(); c = previous }
            c.drawRenderNode(node)
        } else block()
    }
    private inline fun blurredPaint(p: Paint, bounds: RectF, shape: String = "", block: (Paint) -> Unit) {
        val sigma = blurRadii[p.maskFilter]
        if (sigma != null && Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated) {
            val sharp = Paint(p).apply { maskFilter = null }
            blurred(sigma, bounds) { block(sharp) }
        } else if (sigma != null && c.isHardwareAccelerated) {
            // Hardware Canvas before API 31 ignores MaskFilter. Rasterize an opaque mask once,
            // then vary only its alpha and placement, retaining the same Gaussian silhouette.
            val pad = ceil(sigma * 3).toInt()
            val bw = ceil(bounds.width()).toInt() + 2*pad
            val bh = ceil(bounds.height()).toInt() + 2*pad
            val key = "$shape:$bw:$bh:$sigma:${p.color and 0xffffff}:${p.style}:${p.strokeWidth}"
            val bitmap = softMasks[key] ?: Bitmap.createBitmap(bw.coerceAtLeast(1), bh.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { b ->
                val previous = c
                c = Canvas(b)
                c.translate(pad-bounds.left, pad-bounds.top)
                try { block(Paint(p).apply { alpha = 255 }) } finally { c = previous }
                softMasks[key] = b
            }
            c.drawBitmap(bitmap, bounds.left-pad, bounds.top-pad, paint(0xffffff, p.alpha/255.0))
        } else block(p)
    }
    private fun rr(x: Number, y: Number, w: Number, h: Number, r: Number, p: Paint) { blurredPaint(p, rect(x, y, w, h), "round:$r") { c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),it) } }
    private fun circle(x: Number, y: Number, r: Number, p: Paint) { blurredPaint(p, rect(x.toDouble()-r.toDouble(), y.toDouble()-r.toDouble(), r.toDouble()*2, r.toDouble()*2), "circle") { c.drawCircle(x.toFloat(), y.toFloat(), max(0f,r.toFloat()), it) } }
    private fun line(x1: Number, y1: Number, x2: Number, y2: Number, p: Paint) { c.drawLine(x1.toFloat(),y1.toFloat(),x2.toFloat(),y2.toFloat(),p) }
    private fun translate(x: Number, y: Number) { c.translate(x.toFloat(),y.toFloat()) }
    private fun scale(x: Number, y: Number = x) { c.scale(x.toFloat(),y.toFloat()) }
    private inline fun saved(block: () -> Unit) { c.save(); try { block() } finally { c.restore() } }
    private inline fun layer(p: Paint? = null, block: () -> Unit) { c.saveLayer(null,p); try { block() } finally { c.restore() } }
    private fun radial(cx: Number, cy: Number, r: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = RadialGradient(cx.toFloat(),cy.toFloat(), max(0.01f,r.toFloat()), colors, stops, Shader.TileMode.CLAMP)
    }
    private fun linear(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = LinearGradient(x1.toFloat(),y1.toFloat(),x2.toFloat(),y2.toFloat(),colors,stops,Shader.TileMode.CLAMP)
    }
    // Android matrix translations are 0..255; RN Skia matrix translations are normalized 0..1.
    private fun matrix(vararg values: Double) = ColorMatrixColorFilter(values.mapIndexed { i,v -> (if (i % 5 == 4) v*255 else v).toFloat() }.toFloatArray())
    private fun grade(e: Double, dim: Double): ColorFilter {
        val lift=(e-1)*0.08
        return matrix(e*(1-0.62*dim),0.0,0.0,0.0,lift,0.0,e*(1-0.5*dim),0.0,0.0,lift,0.0,0.0,e*(1-0.22*dim),0.0,lift,0.0,0.0,0.0,1.0,0.0)
    }
    private fun image(name: String, cx: Double, cy: Double, w: Double, a: Double=1.0, p: Paint=paint()) {
        val b=bitmaps.getValue(name); val h=w*b.height/b.width
        p.alpha=(a.coerceIn(0.0,1.0)*255).roundToInt()
        c.drawBitmap(b,null,rect(cx-w/2,cy-h/2,w,h),p)
    }
    private fun fit(key: String, str: String, face: String, target: Double) {
        val p=paint().apply { typeface=faces.getValue(face);textSize=100f }
        p.textSize=(100*target/p.measureText(str)).toFloat()
        labels[key]=p
    }
    private fun prepareText() {
        if (labelScale==sx && cardHeightScale==sy) return
        labelScale=sx; cardHeightScale=sy; cards.clear(); blurFaces.clear()
        fit("balance",COPY.balance,"Bold",s(L.balanceW));fit("eyebrow",COPY.eyebrow,"Black",s(L.eyebrowW));fit("title",COPY.title,"Black",s(L.titleW))
        fit("drop",COPY.drop,"ExtraBold",s(L.dropW));fit("hint1",COPY.hint1,"Bold",s(L.hintW));fit("hint2",COPY.hint2,"Bold",s(L.hintW2))
        fit("reward1",COPY.reward1,"ExtraBold",s(L.rewardW));fit("reward2",COPY.reward2,"ExtraBold",s(L.rewardW2));fit("rarity",COPY.rarity,"Bold",s(L.rarityTextW))
        fit("others",COPY.others,"SemiBold",s(L.othersW));fit("claim",COPY.claim,"ExtraBold",s(L.claimTextW))
        for(i in 0..2)fit("item$i",COPY.items[i],"Regular",s(L.itemW[i]))
    }
    private fun text(key: String,str: String,cx: Double,base: Double,v: Int,a: Double=1.0,blur: Double=0.0) {
        if(a<=0.003)return
        val p=Paint(labels.getValue(key)).apply { color=color(v,a); if(blur>0)maskFilter=blurFilter(blur.toFloat()) }
        blurredPaint(p, rect(cx-p.measureText(str)/2, base+p.fontMetrics.top, p.measureText(str), p.fontMetrics.bottom-p.fontMetrics.top), "text:$str:${p.textSize}") { c.drawText(str,(cx-p.measureText(str)/2).toFloat(),base.toFloat(),it) }
    }
    private fun glass(x: Double,y: Double,w: Double,h: Double,r: Double,a: Double,fillA: Double=0.62) {
        if(a<=0.003)return
        rr(x,y+6,w,h,r,paint(0x7d9bd6,0.22*a).apply { maskFilter=blurFilter(10f) })
        rr(x,y,w,h,r,linear(x,y,x,y+h,intArrayOf(color(0xffffff,(fillA+0.18)*a),color(0xf2f6fd,fillA*a),color(0xe6eefb,(fillA-0.06)*a)),floatArrayOf(0f,0.55f,1f)))
        rr(x+0.75,y+0.75,w-1.5,h-1.5,r-0.75,paint(0xffffff,0.95*a).apply { style=Paint.Style.STROKE;strokeWidth=1.5f })
    }
    private fun star(x: Double,y: Double,size: Double,a: Double,kind: Int=0) {
        if(a<=0.01||size<=0.3)return
        saved { translate(x,y);scale(size/20);c.drawPath(starPath,paint(if(kind==2)C.starEdgeSoft else C.starEdge,if(kind==1)0.45*a else a));scale(if(kind==1)0.78 else 0.58);c.drawPath(starPath,paint(0xffffff,a)) }
    }
    private fun drawSky(f: Frame) {
        if(Build.VERSION.SDK_INT>=33) {
            c.drawRect(rect(0,0,width,height),paint().apply { shader=shaders!!.sky(width,height,x(L.rayCX),y(L.rayCY),f.sky) })
        } else {
            c.drawRect(rect(0,0,width,height),linear(0,0,0,height,intArrayOf(color(C.skyTop),color(C.skyMid),color(C.skyLow),color(C.floor)),floatArrayOf(0f,0.42f,0.78f,1f)))
            // Pre-33 fallback keeps the same eight light/deep wedges and central wash.
            saved {
                translate(x(L.rayCX),y(L.rayCY));c.rotate((-0.4276*180/PI).toFloat())
                repeat(8) { i -> c.rotate(45f); val path=Path().apply { moveTo(0f,0f);lineTo((-height*1.5).toFloat(),(-height*0.4).toFloat());lineTo((-height*1.5).toFloat(),(height*0.4).toFloat());close() }; c.drawPath(path,paint(0xf4f8ff,0.12+f.sky*0.8)) }
            }
            circle(x(201),y(420),s(300),radial(x(201),y(420),s(300),intArrayOf(color(0xffffff,0.6),color(0xffffff,0.0))))
        }
        val b=bitmaps.getValue("floor.webp");val fh=width*b.height/b.width
        c.drawBitmap(b,null,rect(0,height-fh,width,fh),paint(0xffffff,0.92))
        c.drawRect(rect(0,height-fh,width,fh),linear(0,height-fh,0,height,intArrayOf(color(0xf6f9ff,0.0),color(0xf6f9ff,0.45))))
        if(f.sky>0.01 && Build.VERSION.SDK_INT>=33)c.drawRect(rect(0,y(L.rayCY),width,height-y(L.rayCY)),paint().apply {shader=shaders!!.floor(width,height,x(L.rayCX),y(L.rayCY),f.sky)})
    }
    private fun drawFrost(f: Frame) {
        if(f.frost<=0.003)return
        layer(paint(0xffffff,f.frost)) {
            val breathe=1.03+0.012*sin(f.idle/9.6*PI*4);val dw=width*breathe;val dh=height*breathe
            c.drawBitmap(bitmaps.getValue("backdrop.jpg"),null,rect((width-dw)/2,(height-dh)/2,dw,dh),paint())
            c.drawRect(rect(0,0,width,height),radial(x(L.openCX),y(L.mouthY-40),s(330),intArrayOf(color(0xffffff,0.7),color(0xf0f6ff,0.25),color(0xf0f6ff,0.0)),floatArrayOf(0f,0.55f,1f)))
        }
    }
    private fun drawChestGlow(f: Frame) {
        val a=(0.4+0.6*f.charge)*(1-f.reveal);if(a<=0.01)return
        val cx=x(L.chestCX+3);val cy=y(375);val r=s(220+90*f.charge)
        circle(cx,cy,r,radial(cx,cy,r,intArrayOf(color(0xffffff,0.85*a),color(0xd2f4ff,0.4*a),color(0xd2f4ff,0.0)),floatArrayOf(0f,0.42f,1f)))
    }
    private fun chestRect(f: Frame): RectF {
        val back=1+0.04*(1-easeOut(seg(f.t,T.swapBack,T.restIn[1])))*(if(f.t>=T.swapBack)1 else 0)
        val b=bitmaps.getValue("chest-closed.webp");val w=s(L.chestW)*back;val h=w*b.height/b.width*L.chestSY
        return rect(x(L.chestCX)-w/2,y(L.chestCY)-h/2,w,h)
    }
    private val glareMatrix = matrix(0.3,0.0,0.0,0.0,0.3,0.0,0.3,0.0,0.0,0.66,0.0,0.0,0.3,0.0,0.7,0.0,0.0,0.0,1.0,0.0)
    private fun screen(p: Paint) { p.xfermode=PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private fun drawClosedChest(f: Frame) {
        val b=bitmaps.getValue("chest-closed.webp");val dst=chestRect(f)
        c.drawBitmap(b,null,dst,paint())
        if(f.charge>0.01) {
            c.drawBitmap(b,null,dst,paint(0xffffff,f.charge).apply {colorFilter=matrix(0.95,0.0,0.0,0.0,0.0,0.0,0.4,0.0,0.0,0.6,0.0,0.0,0.2,0.0,0.8,0.0,0.0,0.0,1.0,0.0)})
            val cx=x(L.chestCX);val by=y(385)
            saved {translate(cx,by);scale(1.25,0.8);translate(-cx,-by);circle(cx,by,s(175),radial(cx,by,s(175),intArrayOf(color(0xffffff,0.6*f.charge),color(0xe6fbff,0.32*f.charge),color(0xe6fbff,0.0)),floatArrayOf(0f,0.5f,1f)))}
        }
        if(f.seam>0.01)c.drawBitmap(b,null,dst,paint(0xffffff,f.seam).apply {colorFilter=glareMatrix;screen(this)})
    }
    private fun drawOpenChest(f: Frame) {
        val k=0.94-0.04*f.pale;val w=0.1*f.pale
        image("chest-open.webp",x(L.openCX),y(L.openCY+f.openDY),s(L.openW),p=paint().apply {colorFilter=matrix(k,0.0,0.0,0.0,w,0.0,k,0.0,0.0,w*0.94,0.0,0.0,k,0.0,w*0.9,0.0,0.0,0.0,1.0,0.0)})
        if(f.glare>0.01)image("chest-open.webp",x(L.openCX),y(L.openCY+f.openDY),s(L.openW),0.9*f.glare,paint().apply {colorFilter=glareMatrix;screen(this)})
    }
    private fun drawShaft(f: Frame) {
        val u=f.shaft;if(u<=0||u>=1)return
        layer {
            val env=seg(u,0.0,0.08)*(1-seg(u,0.9,1.0));val half=s(8+40*sin(PI*u))
            saved {translate(x(-15+413*u),y(445));c.rotate(22f)
                layer {
                    val box=rect(-half*1.7,-s(300),half*3.4,s(560))
                    c.drawRect(box,linear(-half*1.7,0,half*1.7,0,intArrayOf(color(0xffffff,0.0),color(0xffffff,env),color(0xffffff,env),color(0xffffff,0.0)),floatArrayOf(0f,0.3f,0.7f,1f)))
                    c.drawRect(box,linear(0,-s(300),0,s(260),intArrayOf(0,Color.BLACK,Color.BLACK,0),floatArrayOf(0f,0.25f,0.8f,1f)).apply {xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)})
                }
            }
            c.drawBitmap(bitmaps.getValue("chest-closed.webp"),null,chestRect(f),paint().apply {xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)})
        }
    }
    private fun drawMouth(f: Frame) {
        val a=f.mouth*(1-f.sink);if(a<=0.01)return
        val cx=x(L.openCX);val cy=y(L.mouthY+f.openDY);val r=s(170)
        saved {translate(cx,cy);scale(1.0,0.5);translate(-cx,-cy);circle(cx,cy,r,radial(cx,cy,r,intArrayOf(color(0xffffff,a),color(0xffffff,0.9*a),color(0xeefcff,0.45*a),color(0xeefcff,0.0)),floatArrayOf(0f,0.35f,0.65f,1f)))}
    }
    private fun drawLight(f: Frame) {
        if(maxOf(f.lCore,f.lRays,f.lStreak,f.lRingA,f.lPillar)<0.004)return
        val p=paint()
        if(Build.VERSION.SDK_INT>=33) {
            p.shader=shaders!!.light(width,height,x(f.lX),y(f.lY),f,s(f.lRingR),y(L.mouthY+f.openDY))
            p.blendMode=android.graphics.BlendMode.PLUS
        } else {
            p.shader=RadialGradient(x(f.lX).toFloat(),y(f.lY).toFloat(),s(360).toFloat(),intArrayOf(color(0xffffff,min(1.0,f.lCore)),color(0x8cdbff,min(0.65,f.lRays*0.35)),color(0x8cdbff,0.0)),floatArrayOf(0f,0.3f,1f),Shader.TileMode.CLAMP)
            p.xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        c.drawRect(rect(-40,-40,width+80,height+80),p)
    }
    private fun drawVignette(f: Frame) {
        if(f.vignette<=0.004)return
        c.drawRect(rect(-40,-40,width+80,height+80),radial(x(f.lX),y(f.lY-60),hypot(width,height)*0.62,intArrayOf(color(0x0a1e46,0.0),color(0x0a1e46,0.0),color(0x0a1e46,f.vignette)),floatArrayOf(0f,0.45f,1f)))
    }
    private fun drawCardFace(front: Boolean,w: Double,h: Double) {
        val r=s(L.cardR)
        rr(-w/2,-h/2,w,h,r,paint(if(front)0xffffff else 0xbfeaff,if(front)0.35 else 0.7).apply {maskFilter=blurFilter(s(if(front)10 else 14).toFloat())})
        c.drawBitmap(bitmaps.getValue(if(front)"card-front.webp" else "card-back.webp"),null,rect(-w/2,-h/2,w,h),paint(0xffffff,if(front)0.82 else 1.0))
        if(!front)return
        fun oy(v: Double)=y(v)-y(L.cardCY)
        image("jacket.webp",s(L.jacketCX-L.cardCX),oy(L.jacketCY),s(L.jacketW))
        star(s(135-L.cardCX),oy(135.0),s(20),1.0)
        text("reward1",COPY.reward1,0.0,oy(L.rewardBase),C.deepBlue)
        text("reward2",COPY.reward2,0.0,oy(L.rewardBase2),C.deepBlue)
        val cw=s(L.rarityW);val ch=s(L.rarityH);val cy=oy(L.rarityCY)
        rr(-cw/2,cy-ch/2+2,cw,ch,ch/2,paint(0x8aa6da,0.25).apply {maskFilter=blurFilter(4f)})
        rr(-cw/2,cy-ch/2,cw,ch,ch/2,paint(0xffffff,0.78))
        val label=labels.getValue("rarity");val tw=label.measureText(COPY.rarity);val icon=s(9);val gap=s(3);val start=-(icon+gap+tw)/2
        image("crystal.webp",start+icon/2,cy,icon)
        c.drawText(COPY.rarity,(start+icon+gap).toFloat(),(cy+label.textSize*0.36).toFloat(),Paint(label).apply {color=color(C.inkSoft)})
    }
    // Faces are rasterized once. A separable Gaussian caches motion-blurred variants; Canvas has
    // no Paint image filter equivalent on API 26, and MaskFilter cannot blur an entire saveLayer.
    private fun cardBitmap(front: Boolean, blur: Double): Bitmap {
        val key=if(front)"front" else "back"
        val base=cards.getOrPut(key) {
            val b=Bitmap.createBitmap(ceil(s(L.cardW)+s(100)).toInt(),ceil(s(L.cardH)+s(100)).toInt(),Bitmap.Config.ARGB_8888)
            val previous=c;c=Canvas(b);translate(b.width/2.0,b.height/2.0);drawCardFace(front,s(L.cardW),s(L.cardH));c=previous;b
        }
        val bucket=(blur/sx*2).roundToInt().coerceIn(0,18)
        if(bucket==0)return base
        return blurFaces.getOrPut("$key-$bucket") { gaussian(base,(bucket*0.5*sx).toFloat()) }
    }
    private fun gaussian(b: Bitmap,sigma: Float): Bitmap {
        val radius=ceil(sigma*3).toInt().coerceAtLeast(1);val weights=FloatArray(radius*2+1){ exp(-((it-radius)*(it-radius))/(2f*sigma*sigma)) };val sum=weights.sum();weights.indices.forEach {weights[it]/=sum}
        val w=b.width;val h=b.height;val src=IntArray(w*h);b.getPixels(src,0,w,0,0,w,h)
        val tmp=FloatArray(w*h*4);val dst=IntArray(w*h)
        // Convolve premultiplied channels so translucent card edges retain the correct colour.
        for(yy in 0 until h)for(xx in 0 until w) {
            val off=(yy*w+xx)*4
            for(k in -radius..radius) {val ix=xx+k;if(ix !in 0 until w)continue;val v=src[yy*w+ix];val a=(v ushr 24).toFloat();val wt=weights[k+radius];tmp[off]+=a*wt;tmp[off+1]+=(v shr 16 and 255)*a/255*wt;tmp[off+2]+=(v shr 8 and 255)*a/255*wt;tmp[off+3]+=(v and 255)*a/255*wt}
        }
        for(yy in 0 until h)for(xx in 0 until w) {
            var a=0f;var r=0f;var g=0f;var bl=0f
            for(k in -radius..radius){val iy=yy+k;if(iy !in 0 until h)continue;val off=(iy*w+xx)*4;val wt=weights[k+radius];a+=tmp[off]*wt;r+=tmp[off+1]*wt;g+=tmp[off+2]*wt;bl+=tmp[off+3]*wt}
            fun channel(v: Float)=if(a<0.001f)0 else (v*255/a).roundToInt().coerceIn(0,255)
            dst[yy*w+xx]=(a.roundToInt().coerceIn(0,255) shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(bl)
        }
        return Bitmap.createBitmap(dst,w,h,Bitmap.Config.ARGB_8888)
    }
    private fun drawCard(f: Frame) {
        if(f.reveal<0.5||f.t<T.card)return
        var cx=x(f.cardX);var cy=y(f.cardY);var sc=f.cardS;var a=f.cardA
        if(f.sink>0){cx=mix(cx,x(L.openCX),f.sink);cy=mix(cy,y(L.mouthY)+s(10),f.sink);sc*=mix(1.0,0.3,f.sink);a*=1-seg(f.sink,0.7,1.0)}
        if(a<=0.01)return
        val w=s(L.cardW);val h=s(L.cardH);val th=f.spin*PI/180;val co=cos(th);val si=sin(th);val d=s(700)
        if(f.cardHalo>0.01) {
            val hw=w*sc*max(0.25,abs(co))*1.18;val hh=h*sc*1.12
            rr(cx-hw/2,cy-hh/2,hw,hh,s(30),paint(0xffffff,0.85*f.cardHalo*a).apply {maskFilter=blurFilter(s(22).toFloat())})
        }
        val hot=f.cardHot
        val p=paint(0xffffff,a).apply {if(hot>0.01)colorFilter=matrix(1-hot,0.0,0.0,0.0,hot,0.0,1-hot*0.97,0.0,0.0,hot*0.99,0.0,0.0,1-hot*0.94,0.0,hot,0.0,0.0,0.0,1.0,0.0)}
        val blur=s(f.cardBlur)+f.sink*4
        val gpuBlur=Build.VERSION.SDK_INT>=31 && c.isHardwareAccelerated
        val bitmap=cardBitmap(co>=0,if(gpuBlur)0.0 else blur)
        saved {
            translate(cx,cy)
            c.concat(Matrix().apply {setValues(floatArrayOf(co.toFloat(),0f,0f,0f,1f,0f,(si/d).toFloat(),0f,1f))})
            scale(sc)
            if(co<0)scale(-1,1)
            blurred(if(gpuBlur)blur.toFloat() else 0f, rect(-bitmap.width/2.0, -bitmap.height/2.0, bitmap.width, bitmap.height)) { c.drawBitmap(bitmap,-bitmap.width/2f,-bitmap.height/2f,p) }
        }
    }
    private fun drawHold(f: Frame) {
        val a=f.pill
        if(a>0.003){val pw=s(L.pillW);val ph=s(L.pillH);val px=x(L.pillX);val py=y(L.pillY);val k=1-0.03*f.press
            saved {translate(px+pw/2,py+ph/2);scale(k);translate(-px-pw/2,-py-ph/2)
                glass(px,py,pw,ph,ph/2,a,0.7);image("crystal.webp",px+s(37),py+ph/2,s(L.pillIcon),a)
                val tw=labels.getValue("drop").measureText(COPY.drop)
                text("drop",COPY.drop,x(L.pillTextX)+tw/2,y(L.dropBase),C.ink,a)
            }
        }
        if(f.hint>0.003){saved {translate(x(201),y(L.handY));scale(s(L.handSize)/24);translate(-12,-12);c.drawPath(hand,paint(C.hint,f.hint).apply {style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND})}
            text("hint1",COPY.hint1,x(201),y(L.hintBase),C.hint,f.hint);text("hint2",COPY.hint2,x(201),y(L.hintBase2),C.hint,f.hint)
        }
        val show=f.orb>0;if(!show&&a<=0.003)return
        val cx=x(if(show)f.orbX else 201.5);val cy=y(if(show)f.orbY else 651.0);val r=s(if(show)f.orbR else 6.5);val oa=if(show)1.0 else a
        if(show){val pr=s(66.8)*1.25;val px=x(201.5);val py=y(643+66.8*0.8);circle(px,py,pr,radial(px,py,pr,intArrayOf(color(0xffffff,0.85*a),color(0xffffff,0.0))))}
        circle(cx,cy,r*1.2,radial(cx,cy,r*1.2,intArrayOf(color(0xffffff,0.9*oa),color(0xffffff,0.9*oa),color(0xffffff,0.0)),floatArrayOf(0f,0.8f,1f)))
        if(Build.VERSION.SDK_INT>=33)c.drawRect(rect(cx-r-2,cy-r-2,r*2+4,r*2+4),paint().apply {shader=shaders!!.orb(cx,cy,r,f.fill,f.t,oa)})
        else {
            circle(cx,cy,r,radial(cx-r*0.3,cy-r*0.4,r*1.8,intArrayOf(color(0xe8f8ff,oa),color(0xdbeeff,oa))))
            saved {c.clipPath(Path().apply {addCircle(cx.toFloat(),cy.toFloat(),r.toFloat(),Path.Direction.CW)})
                val path=Path();path.moveTo((cx-r).toFloat(),(cy+r).toFloat());path.lineTo((cx-r).toFloat(),(cy+r*(1-2*f.fill)).toFloat())
                for(i in 0..60){val q=-1+i/30.0;val wave=(0.06*sin(q*4.2+f.t*6.5)+0.03*sin(q*8.5-f.t*9))*(1-f.fill.pow(3));path.lineTo((cx+q*r).toFloat(),(cy+r*(1-2*f.fill+wave)).toFloat())}
                path.lineTo((cx+r).toFloat(),(cy+r).toFloat());path.close()
                if(f.fill>0.002)c.drawPath(path,radial(cx,cy,r,intArrayOf(color(0x32c8ff,oa),color(0x64ffff,oa))))
            }
        }
    }
    private fun drawStreaks(f: Frame) {
        if(f.orb<=0||f.t<0.72)return
        val k=seg(f.t,0.72,0.85)*(1-seg(f.fly,0.5,1.0));if(k<=0.01)return
        val p=paint(C.streak).apply {style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeWidth=s(2.2).toFloat()}
        repeat(26){i -> val ii=i.toDouble();val ang=rand(ii,21.0)*PI*2;val life=0.42+0.3*rand(ii,22.0);val u=(((f.t-0.72)/life+rand(ii,23.0))%1+1)%1;val rad=s(mix(165.0,62.0,u*u));val len=s(8+11*rand(ii,24.0))
            p.alpha=(sin(u*PI)*k*(0.55+0.45*rand(ii,25.0))*255).roundToInt().coerceIn(0,255)
            line(x(f.orbX)+cos(ang)*rad,y(f.orbY)+sin(ang)*rad,x(f.orbX)+cos(ang)*(rad+len),y(f.orbY)+sin(ang)*(rad+len),p)
        }
    }
    private fun drawHeader(f: Frame) {
        val a=f.header;if(a<=0.003)return
        val bx=x(L.backX);val by=top+s(L.backY);val br=s(L.backD/2)
        glass(bx-br,by-br,br*2,br*2,br,a,0.58)
        saved{translate(bx,by);scale(s(22)/24);translate(-12,-12);c.drawPath(arrow,paint(C.ink,0.85*a).apply {style=Paint.Style.STROKE;strokeWidth=2f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND})}
        val px=x(L.coinX);val py=top+s(L.coinY);val pw=s(L.coinW);val ph=s(L.coinH)
        glass(px,py,pw,ph,ph/2,a,0.5);image("coin.webp",px+s(26),py+ph/2,s(L.coinIcon),a)
        text("balance",COPY.balance,px+s(47)+labels.getValue("balance").measureText(COPY.balance)/2,top+s(L.balanceBase),C.ink,a)
        val pr=s(L.plusD/2);val pcx=px+pw-s(4.5)-pr;val pcy=py+ph/2
        circle(pcx,pcy,pr,linear(pcx,pcy-pr,pcx,pcy+pr,intArrayOf(color(C.claimTop,a),color(C.claimLow,a))))
        circle(pcx,pcy,pr-0.6,paint(0xffffff,0.7*a).apply {style=Paint.Style.STROKE;strokeWidth=1.2f})
        val cross=paint(0xffffff,a).apply {style=Paint.Style.STROKE;strokeWidth=s(2.6).toFloat();strokeCap=Paint.Cap.ROUND};val arm=s(8)
        line(pcx-arm,pcy,pcx+arm,pcy,cross);line(pcx,pcy-arm,pcx,pcy+arm,cross)
    }
    private fun drawTitle(f: Frame) {
        val a=f.header;val lift=s(8)*(1-a)
        text("eyebrow",COPY.eyebrow,x(201),y(L.eyebrowBase)-lift,C.titleGlow,0.75*a,s(7));text("title",COPY.title,x(201),y(L.titleBase)-lift,C.titleGlow,0.75*a,s(7))
        text("eyebrow",COPY.eyebrow,x(201),y(L.eyebrowBase)-lift,C.title,a);text("title",COPY.title,x(201),y(L.titleBase)-lift,C.title,a)
    }
    private fun drawRewards(f: Frame) {
        if(f.panel<=0.003)return
        if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated) {
            val source = backdropNode
            if (source != null) saved {
                val node = panelNode ?: RenderNode("FrostChestOpening frosted panel").also { panelNode = it }
                node.setPosition(0, 0, ceil(width).toInt(), ceil(height).toInt())
                node.setRenderEffect(RenderEffect.createBlurEffect((14*f.panel).toFloat(), (14*f.panel).toFloat(), Shader.TileMode.CLAMP))
                val recording = node.beginRecording()
                recording.drawRenderNode(source)
                node.endRecording()
                c.clipPath(Path().apply { addRoundRect(rect(x(L.panelX),y(L.panelY),s(L.panelW),s(L.panelH)),s(L.panelR).toFloat(),s(L.panelR).toFloat(),Path.Direction.CW) })
                c.drawRenderNode(node)
            }
        }
        glass(x(L.panelX),y(L.panelY),s(L.panelW),s(L.panelH),s(L.panelR),f.panel,0.2)
        text("others",COPY.others,x(201),y(L.othersBase),C.inkSoft,f.others)
        for(i in 0..2){val k=f.tiles[i];if(k<=0.003)continue;val d=s(L.tileD);val tx=x(L.tileXs[i]);val ty=y(L.tileCY)
            blurred((if(k<0.98)3*(1-k) else 0.0).toFloat(), rect(tx-d/2-s(10),ty-d/2-s(10),d+s(20),y(L.itemBase)-ty+d/2+s(20))) {
            saved{translate(tx,ty);scale(mix(0.92,1.0,k));glass(-d/2,-d/2,d,d,s(L.tileR),k,0.6)
                val name=arrayOf("xp-boost.webp","winter-boots.webp","frozen-shrine.webp")[i];val art=bitmaps.getValue(name)
                image(name,0.0,0.0,s(L.tileArt)*min(1.0,art.width.toDouble()/art.height),k)
            }
            text("item$i",COPY.items[i],tx,y(L.itemBase),C.inkSoft,k)
            }
        }
    }
    private fun drawClaim(f: Frame) {
        if(f.claim<=0.003)return
        val x=x(L.claimX);val w=s(L.claimW);val h=s(L.claimH);val yy=y(L.claimY+f.claimY);val press=1-0.03*f.claimDown
        val v=mix(0.35,1.0,f.claimInk);val lift=mix(0.65,0.0,f.claimInk)
        layer(paint(0xffffff,f.claim).apply {colorFilter=matrix(v,0.0,0.0,0.0,lift,0.0,v,0.0,0.0,lift,0.0,0.0,v,0.0,lift,0.0,0.0,0.0,1.0,0.0)}) {
            translate(x+w/2,yy+h/2);scale(press);translate(-x-w/2,-yy-h/2)
            rr(x+10,yy+10,w-20,h-4,h/2,paint(0x1a8cff,0.45).apply {maskFilter=blurFilter(14f)})
            rr(x-2,yy-2,w+4,h+4,h/2+2,paint(0xffffff,0.85))
            rr(x,yy,w,h,h/2,linear(x,yy,x,yy+h,intArrayOf(color(C.claimTop),color(C.claimMid),color(C.claimLow)),floatArrayOf(0f,0.55f,1f)))
            rr(x+6,yy+3,w-12,h*0.5,h*0.25,linear(x,yy,x,yy+h*0.5,intArrayOf(color(0xffffff,0.5),color(0xffffff,0.0))))
            saved{c.clipPath(Path().apply {addRoundRect(rect(x,yy,w,h),(h/2).toFloat(),(h/2).toFloat(),Path.Direction.CW)});c.drawRect(rect(x,yy,w,h),radial(x+w/2,yy+h,w*0.5,intArrayOf(color(0x82dcff,0.55),color(0x82dcff,0.0))))}
            text("claim",COPY.claim,x+w/2,y(L.claimBase)+(yy-y(L.claimY)),C.claimInk)
        }
    }
    private fun drawSnow(f: Frame) {
        repeat(44){i -> val ii=i.toDouble();val phase=f.idle/4.8+rand(ii,31.0);val cyc=floor(phase);val u=phase-cyc;val seed=i*7+cyc%2
            val xx=rand(seed,32.0)*width+s(14)*sin(u*PI*2+i);val yy=rand(seed,33.0)*height*0.96+s(46)*u;val big=rand(ii,34.0)>0.86
            val rad=s(if(big)2.6+2*rand(ii,35.0) else 0.9+1.3*rand(ii,35.0));val p=paint(0xffffff,sin(u*PI)*(if(big)0.55 else 0.85))
            if(big)p.maskFilter=blurFilter((rad*0.6).toFloat())
            circle(xx,yy,rad,p)
        }
    }
    private fun drawFlash(f: Frame) {
        if(f.flash<=0.003)return
        val first=f.t<6;val cx=x(if(first)200.0 else L.openCX);val cy=y(if(first)430.0 else L.mouthY)
        val rad=if(first)s(40+80*seg(f.t,T.flashIn[0],2.68)+560*seg(f.t,2.68,T.flashIn[1]).pow(1.8)) else hypot(width,height)*1.15
        c.drawRect(rect(0,0,width,height),radial(cx,cy,rad,intArrayOf(color(0xffffff,f.flash),color(0xfbfdff,f.flash*0.92),color(0xeef8ff,f.flash*0.55),color(0xeef8ff,0.0)),floatArrayOf(0f,0.35f,0.7f,1f)))
        if(first){val u=seg(f.t,2.61,2.7);if(u>0&&u<1)star(cx,cy,s(90)*sin(PI*u),1.0,1)}
    }
    private fun drawSparkles(f: Frame) {
        if(f.reveal<0.5) {
            val stars=arrayOf(doubleArrayOf(188.0,338.0,34.0,0.84,1.42),doubleArrayOf(268.0,351.0,60.0,1.08,1.78),doubleArrayOf(127.0,291.0,42.0,1.4,1.95),doubleArrayOf(288.0,477.0,54.0,1.94,2.6),doubleArrayOf(306.0,481.0,26.0,2.4,2.66))
            for(st in stars){val u=seg(f.t,st[3],st[4]);if(u>0&&u<1)star(x(st[0]),y(st[1]),s(st[2])*sin(u.pow(0.7)*PI),1.0)}
            val a=f.header*(1-seg(f.t,0.84,0.9));star(x(188),y(338),s(16),a,1);star(x(45),y(505),s(12),a,1);star(x(100),y(525),s(16),a,1)
            return
        }
        if(f.sparkle<0)return
        repeat(64){i -> val ii=i.toDouble();val born=0.02+0.4*rand(ii,61.0).pow(1.3);val life=0.26+0.24*rand(ii,62.0);val age=f.sparkle-born
            if(age>=0&&age<=life){val x0=40+300*rand(ii,63.0);val y0=282+170*rand(ii,64.0).pow(1.25);val vy=25+55*rand(ii,65.0);val vx=(x0-196)*0.22+(rand(ii,66.0)-0.5)*24;val k=age/life
                val pop=easeOut(seg(k,0.0,0.18));val out=1-seg(k,0.6,1.0);val size=s(16+34*rand(ii,67.0).pow(1.4))*mix(0.45,1.0,pop)*mix(0.6,1.0,out)
                star(x(x0+vx*age),y(y0-vy*age),size,pop*out,if(rand(ii,68.0)<0.72)2 else 1)
            }
        }
        val spots=arrayOf(doubleArrayOf(120.0,480.0,26.0,0.05),doubleArrayOf(152.0,517.0,32.0,0.12),doubleArrayOf(245.0,442.0,26.0,0.0),doubleArrayOf(297.0,477.0,22.0,0.2),doubleArrayOf(322.0,520.0,22.0,0.3),doubleArrayOf(230.0,560.0,30.0,0.26))
        for(st in spots){val u=seg(f.sparkle,st[3],st[3]+0.36);if(u>0&&u<1)star(x(st[0]),y(st[1]+f.openDY),s(st[2])*sin(u*PI),1.0,1)}
    }
    private data class FrostChestOpeningVector(val x: Double,val y: Double,val z: Double) {
        operator fun plus(b: FrostChestOpeningVector)=FrostChestOpeningVector(x+b.x,y+b.y,z+b.z)
        operator fun minus(b: FrostChestOpeningVector)=FrostChestOpeningVector(x-b.x,y-b.y,z-b.z)
        operator fun times(s: Double)=FrostChestOpeningVector(x*s,y*s,z*s)
        fun dot(b: FrostChestOpeningVector)=x*b.x+y*b.y+z*b.z
        fun cross(b: FrostChestOpeningVector)=FrostChestOpeningVector(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
        fun normalized(): FrostChestOpeningVector {val len=sqrt(dot(this));return this*(1/max(1e-12,len))}
        fun rotate(axis: FrostChestOpeningVector,angle: Double): FrostChestOpeningVector {
            val c=cos(angle);val s=sin(angle)
            return this*c+axis.cross(this)*s+axis*(axis.dot(this)*(1-c))
        }
        fun lean(angle: Double)=FrostChestOpeningVector(x*cos(angle)-y*sin(angle),x*sin(angle)+y*cos(angle),z)
    }
    private fun smooth(a: Double,b: Double,v: Double): Double {val k=seg(v,a,b);return k*k*(3-2*k)}
    /** The WGSL bipyramid, axis rotation, trajectory and flat-face lighting, projected to Canvas. */
    private fun drawChips(f: Frame) {
        if(f.t<T.sparkle[0]||f.t>=T.motes[1])return
        val tau=f.t-T.sparkle[0];val fade=1-f.flash
        repeat(30){index -> val i=index.toDouble();fun hash(salt: Int)=rand(i,salt.toDouble())
            val born=0.3*hash(12);val life=0.35+0.22*hash(13);val t=max(tau-born,0.0);val k=t/life
            val opacity=(if(tau>=born)1 else 0)*smooth(0.0,0.12,k)*(1-smooth(0.6,1.0,k))*fade
            if(opacity<=0.001)return@repeat
            val startX=(hash(1)-0.5)*250*sx;val startY=(hash(2)-0.6)*90*sx
            val away=FrostChestOpeningVector(startX,startY-75*sx+0.001,0.0).normalized()
            val speed=(40+90*hash(11))*sx;val travel=speed*(1-exp(-2.5*t))/2.5;val grav=-18*sx*t
            val depth=(hash(3)-0.5)*1.4;val persp=1/(1-depth*0.35);val len=(3.4+3.2*hash(5))*sx
            val axis=FrostChestOpeningVector(hash(6)-0.5,hash(7)-0.5,hash(8)-0.5+0.001).normalized()
            val spin=t*(6+10*hash(9))+hash(10)*6.28;val lean=atan2(away.x,-away.y)
            val cx=x(L.openCX)+startX+away.x*travel*persp;val cy=y(395)+startY+(away.y*travel+grav)*persp
            val w=0.5+0.2*hash(4)
            fun corner(tri: Int,j: Int): FrostChestOpeningVector {
                val top=tri<4;val side=tri%4
                if(j==0)return FrostChestOpeningVector(0.0,if(top)1.0 else -0.55,0.0)
                val idx=if(top){if(j==1)side else (side+1)%4}else{if(j==1)(side+1)%4 else side}
                val ang=idx*1.5707963+0.35
                return FrostChestOpeningVector(cos(ang)*w,0.08,sin(ang)*w*0.6)
            }
            val light=FrostChestOpeningVector(-0.45,0.65,0.6).normalized()
            repeat(8){tri ->
                val p0=corner(tri,0);val p1=corner(tri,1);val p2=corner(tri,2)
                val n=(p1-p0).cross(p2-p0).normalized().rotate(axis,spin).lean(lean).normalized()
                val facing=abs(n.z);val fres=(1-facing).pow(2);val incidence=n.dot(light)
                val reflected=n*(2*incidence)-light;val spec=max(reflected.z,0.0).pow(18)
                val diff=0.55+0.45*abs(incidence);val tint=hash(14)
                val ice=FrostChestOpeningVector(mix(0.33,0.50,tint),mix(0.80,0.88,tint),mix(0.96,0.99,tint))*diff
                val rgb=ice*(1-fres*0.7)+FrostChestOpeningVector(0.92,0.98,1.0)*(fres*0.7)+FrostChestOpeningVector(spec,spec,spec)
                val a=((0.7+0.3*fres+spec)*opacity*0.88).coerceIn(0.0,1.0)
                val path=Path()
                listOf(p0,p1,p2).forEachIndexed {j,p -> val q=p.rotate(axis,spin).lean(lean);val xx=(cx+q.x*len*persp).toFloat();val yy=(cy-q.y*len*persp).toFloat();if(j==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)}
                path.close()
                val v=((rgb.x.coerceIn(0.0,1.0)*255).roundToInt() shl 16) or ((rgb.y.coerceIn(0.0,1.0)*255).roundToInt() shl 8) or (rgb.z.coerceIn(0.0,1.0)*255).roundToInt()
                c.drawPath(path,paint(v,a))
            }
        }
    }
    /** 180 camera-facing Gaussian motes, with the WGSL instance indices and hash salts. */
    private fun drawMotes(f: Frame) {
        if(f.t<T.sparkle[0]||f.t>=T.motes[1])return
        val tau=f.t-T.sparkle[0]
        repeat(180){idx -> val i=(idx+130).toDouble();fun hash(salt: Int)=rand(i,salt.toDouble())
            val born=0.03+0.9*hash(21).pow(1.4);val life=0.9+0.8*hash(22);val t=tau-born;val k=(t/life).coerceIn(0.0,1.0);val depth=hash(23)
            if(t<0||k>=1)return@repeat
            val side=if(hash(28)>0.5)1 else -1
            val xx=side*(38+(60+80*depth)*hash(24))+16*sin(t*(1.6+hash(25))+i)
            val yy=-20*hash(26)-(60+140*hash(27))*max(t,0.0)
            val size=mix(5.5,1.3,depth)*sx;val cx=x(L.openCX)+xx*sx;val cy=y(395)+yy*sx
            val a=sin(3.14159*k)*(0.65+0.35*sin(t*9+i*3))*mix(0.55,1.0,depth)*(1-f.flash)
            if (Build.VERSION.SDK_INT >= 33) {
                c.drawRect(rect(cx-size*2.2,cy-size*2.2,size*4.4,size*4.4),paint().apply { shader=shaders!!.mote(cx,cy,size*2.2,a) })
                return@repeat
            }
            // Fragment colour is premultiplied RGB*.8 and A*.6, preserving the WGSL blend.
            val stops=FloatArray(33){it/32f}
            val colors=IntArray(33){j -> val radius=j/32.0;val d=radius*radius;val core=exp(-d*18);val alpha=(exp(-d*4)*a).coerceIn(0.0,1.0)*0.6
                val r=(mix(0.6,1.0,core)*0.8/0.6).coerceIn(0.0,1.0);val g=(mix(0.9,1.0,core)*0.8/0.6).coerceIn(0.0,1.0)
                color(((r*255).roundToInt() shl 16) or ((g*255).roundToInt() shl 8) or 255,alpha)
            }
            saved{c.clipRect(rect(cx-size*2.2,cy-size*2.2,size*4.4,size*4.4));circle(cx,cy,size*2.2*sqrt(2.0),radial(cx,cy,size*2.2,colors,stops))}
        }
    }
    fun drawScene(canvas: Canvas,w: Float,h: Float,safeTop: Float,f: Frame) {
        blurIndex=0;c=canvas;width=w.toDouble();height=h.toDouble();sx=width/402;sy=height/874;top=safeTop.toDouble();prepareText()
        fun content() {
        val exposed=f.exposure>1.002||f.dim>0.002
        val ep=if(exposed)paint().apply {colorFilter=grade(f.exposure,f.dim)}else null
        if(f.reveal<0.5) {
            layer(ep){drawSky(f);drawChestGlow(f)}
            if(f.lBehind>0.5)drawLight(f)
            layer(ep){drawClosedChest(f)}
            drawVignette(f)
            if(f.lBehind<0.5)drawLight(f)
        } else {
            val behind=f.sink>0.55
            layer(ep){if(f.frost<1)drawSky(f.copy(sky=1.0));drawFrost(f);if(behind)drawCard(f);drawOpenChest(f)}
            drawVignette(f);drawLight(f);drawMouth(f);drawChips(f);drawMotes(f)
            if(!behind)drawCard(f)
        }
        drawSparkles(f);drawTitle(f);drawHeader(f);drawShaft(f);drawStreaks(f);drawHold(f)
        }
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val node = backdropNode ?: RenderNode("FrostChestOpening scene").also { backdropNode = it }
            node.setPosition(0, 0, ceil(width).toInt(), ceil(height).toInt())
            node.setClipToBounds(false)
            c = node.beginRecording()
            try { content() } finally { node.endRecording(); c = canvas }
            c.drawRenderNode(node)
        } else content()
        drawRewards(f);drawClaim(f);drawSnow(f);drawFlash(f)
    }
}

/** Isolated behind the API gate: pre-33 devices never load or construct RuntimeShader. */
@RequiresApi(33)
private class FrostChestOpeningRuntime {
    private val sky=RuntimeShader(FrostChestOpeningShaders.RAYS_SKSL)
    private val floor=RuntimeShader(FrostChestOpeningShaders.RAYLIGHT_SKSL)
    private val orb=RuntimeShader(FrostChestOpeningShaders.ORB_SKSL)
    private val mote=RuntimeShader(FrostChestOpeningShaders.MOTE_AGSL)
    private val light=RuntimeShader(FrostChestOpeningShaders.LIGHT_SKSL)
    private fun RuntimeShader.u(name: String,vararg values: Double) {setFloatUniform(name,values.map(Double::toFloat).toFloatArray())}
    fun sky(w: Double,h: Double,x: Double,y: Double,bright: Double): Shader = sky.apply {u("size",w,h);u("center",x,y);u("rot",0.4276);u("bright",bright)}
    fun floor(w: Double,h: Double,x: Double,y: Double,bright: Double): Shader = floor.apply {u("size",w,h);u("center",x,y);u("rot",0.4276);u("bright",bright)}
    fun orb(x: Double,y: Double,r: Double,fill: Double,time: Double,alpha: Double): Shader = orb.apply {u("center",x,y);u("radius",r);u("fill",fill);u("time",time);u("glow",0.0);u("alpha",alpha)}
    fun mote(x: Double,y: Double,radius: Double,opacity: Double): Shader = mote.apply {u("center",x,y);u("radius",radius);u("opacity",opacity)}
    fun light(w: Double,h: Double,x: Double,y: Double,f: Frame,ringR: Double,mouthY: Double): Shader = light.apply {
        u("size",w,h);u("center",x,y);u("time",f.t);u("core",f.lCore);u("rays",f.lRays);u("reach",f.lReach);u("streak",f.lStreak);u("ringR",ringR);u("ringA",f.lRingA);u("pillar",f.lPillar);u("mouthY",mouthY);u("below",f.lBelow)
    }
}
