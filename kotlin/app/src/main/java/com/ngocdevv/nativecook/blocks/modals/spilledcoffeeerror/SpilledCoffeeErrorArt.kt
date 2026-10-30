package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.Pose
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.clamp01
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.easeOut
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.mix
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.smooth
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.LOOP
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorTheme.C

/** Native Canvas equivalent of sce-draw.ts. All artwork uses the pin's 658-pixel coordinates. */
internal class SpilledCoffeeErrorArt(context: Context) {
    data class P2(val x: Double, val y: Double)
    private data class V3(val x: Double, val y: Double, val z: Double)
    private data class Frame(val axis: V3, val u: V3, val v: V3, val base: V3)
    private data class Cup(val mouth: P2, val low: P2, val frame: Frame)
    private val ce = cos(SpilledCoffeeErrorTheme.ELEV)
    private val se = sin(SpilledCoffeeErrorTheme.ELEV)
    private val fall = V3(-cos(SpilledCoffeeErrorTheme.YAW), 0.0, sin(SpilledCoffeeErrorTheme.YAW))
    private val side = V3(-fall.z, 0.0, fall.x)
    private val view = V3(0.0, se, ce)
    private val tau = Math.PI * 2
    private val semi = Typeface.createFromAsset(context.assets, "spilledcoffeeerror/spilledcoffeeerror_Manrope_600SemiBold.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "spilledcoffeeerror/spilledcoffeeerror_Manrope_500Medium.ttf")
    // Software rasterization makes the same Gaussian mask blur work on API 26 and hardware canvases.
    private val puddleBitmap = Bitmap.createBitmap(658, 700, Bitmap.Config.ARGB_8888)
    private val puddleCanvas = Canvas(puddleBitmap)
    private val puddleThreshold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,22f,-9f*255f))
    }

    private val grain = SpilledCoffeeErrorShaders(context.resources.displayMetrics.density)
    private fun p(color: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
    }

    private fun stroke(color: Int, width: Double, alpha: Double = 1.0) = p(color, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun blur(color: Int, alpha: Double, sigma: Double) = p(color, alpha).apply {
        maskFilter = BlurMaskFilter(((sigma - 0.5) / 0.5773502691896257).toFloat(), BlurMaskFilter.Blur.NORMAL)
    }

    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat())
    private fun box(c: Canvas, x: Double, y: Double, w: Double, h: Double, paint: Paint) {
        if (paint.maskFilter == null || !c.isHardwareAccelerated) c.drawRect(rect(x,y,w,h),paint)
        else masked(c, rect(x,y,w,h), paint, "box:$x:$y:$w:$h") {
            it.drawRect(rect(x,y,w,h),paint)
        }
    }

    private fun oval(c: Canvas, x: Double, y: Double, w: Double, h: Double, paint: Paint) {
        if (paint.maskFilter == null || !c.isHardwareAccelerated) c.drawOval(rect(x,y,w,h),paint)
        else masked(c, rect(x,y,w,h), paint, "oval:$x:$y:$w:$h") {
            it.drawOval(rect(x,y,w,h),paint)
        }
    }

    private fun round(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, paint: Paint) {
        if (paint.maskFilter == null || !c.isHardwareAccelerated) c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),paint)
        else masked(c, rect(x,y,w,h), paint, "round:$x:$y:$w:$h:$r") {
            it.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),paint)
        }
    }

    private val masks = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 64
    }

    private fun masked(c: Canvas, bounds: RectF, paint: Paint, shapeKey: String, draw: (Canvas) -> Unit) {
        val left = floor(bounds.left.toDouble()).toInt() - 64
        val top = floor(bounds.top.toDouble()).toInt() - 64
        val width = ceil(bounds.width().toDouble()).toInt() + 130
        val height = ceil(bounds.height().toDouble()).toInt() + 130
        val key = "$shapeKey:${paint.color}:${paint.alpha}"
        val bitmap = masks.getOrPut(key) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                bitmap ->
                val software = Canvas(bitmap)
                software.translate(-left.toFloat(), -top.toFloat())
                draw(software)
            }
        }

        c.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)
    }

    private fun shadowPath(c: Canvas, path: Path, paint: Paint, key: String) {
        if (!c.isHardwareAccelerated) {
            c.drawPath(path, paint)
            return
        }

        val bounds = RectF()
        path.computeBounds(bounds, true)
        masked(c, bounds, paint, key) {
            it.drawPath(path, paint)
        }
    }

    private fun circle(c: Canvas, x: Double, y: Double, r: Double, paint: Paint) = c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint)
    private fun gradient(x0: Double, y0: Double, x1: Double, y1: Double, colors: IntArray, stops: FloatArray? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x0.toFloat(),y0.toFloat(),x1.toFloat(),y1.toFloat(),colors,stops,Shader.TileMode.CLAMP)
    }

    private fun poly(points: List<P2>) = Path().apply {
        points.forEachIndexed {
            i, v -> if (i == 0) moveTo(v.x.toFloat(),v.y.toFloat()) else lineTo(v.x.toFloat(),v.y.toFloat())
        }

        close()
    }

    private fun proj(v: V3) = P2(503 + v.x, 545 - v.y * ce + v.z * se)
    private fun dot(a: V3, b: V3) = a.x*b.x+a.y*b.y+a.z*b.z
    private fun frame(theta: Double): Frame {
        val t = theta * Math.PI / 180
        val ct = cos(t)
        val st = sin(t)
        return Frame(V3(st*fall.x,ct,st*fall.z),side,V3(ct*fall.x,-st,ct*fall.z), V3(55*(1-ct)*fall.x,55*st,55*(1-ct)*fall.z))
    }

    private fun ringPoint(fr: Frame, h: Double, r: Double, angle: Double): V3 {
        val ca = cos(angle)
        val sa = sin(angle)
        return V3(fr.base.x+fr.axis.x*h+r*(fr.u.x*ca+fr.v.x*sa),fr.base.y+fr.axis.y*h+r*(fr.u.y*ca+fr.v.y*sa),fr.base.z+fr.axis.z*h+r*(fr.u.z*ca+fr.v.z*sa))
    }

    private fun center(fr: Frame, h: Double) = proj(V3(fr.base.x+fr.axis.x*h,fr.base.y+fr.axis.y*h,fr.base.z+fr.axis.z*h))
    private fun ring(fr: Frame, h: Double, r: Double) = List(40) {
        proj(ringPoint(fr,h,r,it/40.0*tau))
    }

    private fun hull(points: List<P2>): List<P2> {
        val sorted = points.sortedWith(compareBy<P2> {
            it.x
        }
        .thenBy {
            it.y
        }
        )
        fun cross(o: P2,a: P2,b: P2) = (a.x-o.x)*(b.y-o.y)-(a.y-o.y)*(b.x-o.x)
        fun half(input: List<P2>): MutableList<P2> {
            val result = mutableListOf<P2>()
            for (point in input) {
                while (result.size>=2 && cross(result[result.size-2],result.last(),point)<=0) result.removeAt(result.lastIndex)
                result.add(point)
            }

            result.removeAt(result.lastIndex)
            return result
        }

        return half(sorted)+half(sorted.reversed())
    }

    private fun band(fr: Frame,h0: Double,h1: Double): List<P2>? {
        val r0 = mix(55.0,75.0,h0/215)
        val r1 = mix(55.0,75.0,h1/215)
        val vis = List(48) {
            i -> val a = i/48.0*tau
            dot(V3(fr.u.x*cos(a)+fr.v.x*sin(a),fr.u.y*cos(a)+fr.v.y*sin(a),fr.u.z*cos(a)+fr.v.z*sin(a)),view)>-0.02
        }

        var start=0
        for (i in 0 until 48) if (!vis[i] && vis[(i+1)%48]) {
            start=(i+1)%48
            break
        }

        val front=mutableListOf<P2>()
        val back=mutableListOf<P2>()
        for (k in 0..48) {
            val i=(start+k)%48
            if (!vis[i] && k>0) break
            val a=i/48.0*tau
            front.add(proj(ringPoint(fr,h0,r0,a)))
            back.add(proj(ringPoint(fr,h1,r1,a)))
        }

        return if (front.size<2) null else front+back.reversed()
    }

    private fun room(c: Canvas,amb: Double,top: Double,bottom: Double) {
        box(c,-10.0,top,678.0,382-top,gradient(0.0,top,0.0,382.0,intArrayOf(C.wallTop,C.wallLow)))
        box(c,-10.0,382.0,678.0,bottom-382,gradient(0.0,382.0,0.0,542.0,intArrayOf(C.floorFar,C.floor)))
        box(c,-10.0,380.0,678.0,10.0,blur(0xFFD5DAE1.toInt(),0.6,6.0))
        val glow = p(-1).apply {
            shader = RadialGradient(560f,60f,380f,intArrayOf(0xE6FFFFFF.toInt(),0x00FFFFFF),null,Shader.TileMode.CLAMP)
        }

        box(c,150.0,top,658.0,382-top,glow)
        fun book(x: Double,y: Double,w: Double,h: Double,d: Double,cx: Double) {
            box(c,x-6,y+h-4,w+30,18.0,blur(C.shadow,0.5,9.0))
            c.drawPath(poly(listOf(P2(x,y),P2(x+d,y-d*0.55),P2(x+w+d,y-d*0.55),P2(x+w,y))),p(C.bookTop))
            round(c,x,y,w,h,6.0,p(C.book))
            box(c,x,y+h-10,w,10.0,p(C.bookSide,0.7))
            circle(c,cx,y+h*0.42,h*0.17,p(-1))
            circle(c,cx,y+h*0.42,h*0.17,stroke(0xFFC6CDD6.toInt(),2.0))
            round(c,cx+h*0.3,y+h*0.36,w-(cx-x)-h*0.4,h*0.12,3.0,p(-1))
            round(c,cx+h*0.3,y+h*0.36,w-(cx-x)-h*0.4,h*0.12,3.0,stroke(0xFFC6CDD6.toInt(),1.5))
        }

        book(446.0,386.0,240.0,112.0,26.0,474.0)
        book(516.0,312.0,170.0,86.0,22.0,544.0)
        oval(c,-40.0,420.0,170.0,34.0,blur(C.shadow,0.6,12.0))
        val pot=Path().apply {
            moveTo(-34f,196f)
            lineTo(112f,196f)
            lineTo(98f,438f)
            quadTo(40f,450f,-20f,438f)
            close()
        }

        c.drawPath(pot,gradient(-34.0,0.0,112.0,0.0,intArrayOf(-1,C.pot,C.potShade),floatArrayOf(0f,0.55f,1f)))
        oval(c,-34.0,184.0,146.0,26.0,p(0xFFE3E7EC.toInt()))
        oval(c,-28.0,188.0,134.0,18.0,p(0xFFC9CED6.toInt()))
        val sway=tau*amb/LOOP
        fun leaf(bx: Double,by: Double,len: Double,wid: Double,angle: Double,ca: Int,cb: Int,k: Double) {
            c.save()
            c.translate(bx.toFloat(),by.toFloat())
            c.rotate((angle+2.2*sin(sway+k)).toFloat())
            val leaf=Path().apply {
                moveTo(0f,0f)
                cubicTo(-wid.toFloat(),(-len*0.3).toFloat(),(-wid*0.6).toFloat(),(-len*0.85).toFloat(),0f,-len.toFloat())
                cubicTo((wid*0.7).toFloat(),(-len*0.8).toFloat(),wid.toFloat(),(-len*0.3).toFloat(),0f,0f)
                close()
            }

            c.drawPath(leaf,gradient(-wid,0.0,wid,0.0,intArrayOf(ca,cb)))
            c.drawLine(0f,0f,0f,(-len*0.92).toFloat(),stroke(-1,1.4,0.25))
            c.restore()
        }

        leaf(20.0,196.0,280.0,66.0,-14.0,C.leafC,C.leafA,0.0)
        leaf(46.0,196.0,250.0,58.0,12.0,C.leafA,C.leafB,1.3)
        leaf(8.0,196.0,200.0,50.0,-38.0,C.leafA,C.leafB,2.1)
        leaf(74.0,196.0,170.0,44.0,34.0,C.leafB,C.leafA,3.4)
        leaf(-10.0,196.0,236.0,54.0,-2.0,C.leafB,C.leafC,4.6)
    }

    private fun paper(c: Canvas) {
        val paper=poly(listOf(P2(52.0,576.0),P2(322.0,552.0),P2(432.0,590.0),P2(156.0,626.0)))
        c.save()
        c.translate(4f,7f)
        shadowPath(c,paper,blur(C.shadow,0.55,7.0),"paper")
        c.restore()
        c.drawPath(paper,p(C.paper))
    }

    private val blobs=arrayOf(doubleArrayOf(282.0,566.0,24.0),doubleArrayOf(258.0,574.0,30.0),doubleArrayOf(228.0,581.0,34.0),doubleArrayOf(197.0,587.0,30.0),doubleArrayOf(170.0,590.0,24.0),doubleArrayOf(212.0,595.0,20.0),doubleArrayOf(244.0,590.0,18.0),doubleArrayOf(148.0,592.0,16.0),doubleArrayOf(298.0,576.0,14.0))
    private fun puddle(c: Canvas,pose: Pose,mouth: P2) {
        if (pose.spill<=0.002) return
        puddleBitmap.eraseColor(Color.TRANSPARENT)
        val bp=blur(C.coffee,1.0,7.0)
        val wob=tau*pose.amb/LOOP
        for ((i,b) in blobs.withIndex()) {
            val k=smooth(pose.spill*blobs.size*1.25-i)
            if(k<=0.01) continue
            val x=mix(mouth.x,b[0],easeOut(k))+1.5*sin(wob*2+i)
            val y=mix(mouth.y+30,b[1],easeOut(k))
            val r=b[2]*k
            oval(puddleCanvas,x-r*1.35,y-r*0.42,r*2.7,r*0.84,bp)
        }

        c.drawBitmap(puddleBitmap,0f,0f,puddleThreshold)
        for(i in 0 until 4) {
            val k=smooth(pose.spill*blobs.size*1.25-i)
            if(k<=0.2)continue
            val b=blobs[i]
            oval(c,b[0]-b[2]*0.6,b[1]-b[2]*0.22,b[2]*0.9,b[2]*0.16,p(C.coffeeLight,0.55*k))
        }

        fun hit(t: Double,x: Double,y: Double) {
            if(t<0||t>1.1)return
            for(k in 0 until 2) {
                val q=clamp01((t-k*0.18)/0.9)
                if(q<=0)continue
                oval(c,x-30*q,y-9*q,60*q,18*q,stroke(C.coffeeLight,2*(1-q),0.8*(1-q)))
            }
        }

        hit(pose.splash,236.0,584.0)
        // droplets thrown up where the coffee lands, falling back on their arcs
        if (pose.splash >= 0 && pose.splash < 0.75) {
            val t = pose.splash
            for (i in 0 until 12) {
                val h1 = sin((i + 1) * 12.9898) * 43758.5453
                val r1 = h1 - floor(h1)
                val angle = -PI / 2 + (r1 - 0.5) * 2.2
                val v = 160 + 140 * ((i * 0.37) % 1)
                val x = 236 + cos(angle) * v * t * 1.3
                val y = 580 + sin(angle) * v * t * 0.9 + 520 * t * t
                if (y > 600) continue
                val alpha = 1 - clamp01((t - 0.45) / 0.3)
                oval(c, x - 3, y - 2.2, 6.0, 4.4, p(C.coffee, alpha))
            }
        }
        hit(pose.drip-0.35,262.0,576.0)
    }

    private fun cup(c: Canvas,pose: Pose): Cup {
        val fr=frame(pose.theta)
        val baseRing=ring(fr,0.0,55.0)
        val mouthRing=ring(fr,215.0,75.0)
        val mouth=center(fr,215.0)
        val base=proj(fr.base)
        shadowPath(c,poly(hull((baseRing+mouthRing).map {
            P2(it.x+14,551+(it.y-545)*0.08)
        }
        )),blur(C.shadow,0.55,10.0),"cup:${pose.theta}")
        val body=poly(hull(baseRing+mouthRing))
        val ax=mouth.x-base.x
        val ay=mouth.y-base.y
        val al=max(1.0,hypot(ax,ay))
        val nx=-ay/al
        val ny=ax/al
        val mx=(mouth.x+base.x)/2
        val my=(mouth.y+base.y)/2
        val stops=floatArrayOf(0f,0.4f,0.6f,1f)
        c.drawPath(body,gradient(mx+nx*70,my+ny*70,mx-nx*70,my-ny*70,intArrayOf(C.cupShade,C.cup,-1,C.cupShade),stops))
        if(dot(fr.axis,view)<0)c.drawPath(poly(baseRing),p(0xFFE9ECEF.toInt()))
        val sleeve=band(fr,0.4*215,0.74*215)
        if(sleeve!=null) {
            c.save()
            c.clipPath(body)
            c.drawPath(poly(sleeve),gradient(mx+nx*70,my+ny*70,mx-nx*70,my-ny*70,intArrayOf(C.sleeveShade,C.sleeve,0xFFD6E6C0.toInt(),C.sleeveShade),stops))
            c.restore()
        }

        var best=0.0
        var bestDot=-2.0
        for(i in 0 until 24) {
            val a=i/24.0*tau
            val dd=dot(V3(fr.u.x*cos(a)+fr.v.x*sin(a),fr.u.y*cos(a)+fr.v.y*sin(a),fr.u.z*cos(a)+fr.v.z*sin(a)),view)
            if(dd>bestDot){
                bestDot=dd
                best=a
            }
        }

        val hb=(0.4+0.74)*0.5*215
        val rb=mix(55.0,75.0,hb/215)
        val bc=proj(ringPoint(fr,hb,rb,best))
        c.save()
        c.translate(bc.x.toFloat(),bc.y.toFloat())
        c.rotate((atan2(ay/al,ax/al)*180/Math.PI).toFloat())
        oval(c,-14.0,-10.0,28.0,20.0,p(C.bean,0.95))
        c.drawPath(Path().apply {
            moveTo(-10f,2f)
            cubicTo(-3f,-6f,3f,6f,10f,-2f)
        }
        ,stroke(C.sleeve,2.2))
        c.restore()
        val low=mouthRing.maxBy {
            it.y
        }

        if(dot(fr.axis,view)>0) {
            val mp=poly(mouthRing)
            c.drawPath(mp,p(C.cupInside))
            val left=1-pose.spill
            if(left>0.02&&pose.theta>30){
                c.save()
                c.clipPath(mp)
                oval(c,low.x-75*0.9,low.y-75*0.5*left-10,75*1.8,75*1.1*left+14,p(C.coffee))
                c.restore()
            }

            c.drawPath(mp,stroke(-1,4.5))
        }

        return Cup(mouth,low,fr)
    }

    private fun lid(c: Canvas,pose: Pose,cup: Cup) {
        val k=pose.lid
        val fr=cup.frame
        val on=center(fr,219.0)
        val e=easeOut(k)
        val arc=sin(Math.PI*k)*110
        val hop=if(pose.lidHop>=0)26*abs(sin(pose.lidHop*9))*exp(-pose.lidHop*5) else 0.0
        val x=mix(on.x,502.0,e)
        val y=mix(on.y,620.0,e)-arc-hop
        val tumble=sin(Math.PI*k)*1.4
        val nx=mix(fr.axis.x,0.0,e)+sin(tumble)*0.4
        val ny=mix(fr.axis.y,1.0,e)
        val nz=mix(fr.axis.z,0.0,e)+cos(tumble)*0.1*sin(Math.PI*k)
        val nl=sqrt(nx*nx+ny*ny+nz*nz).takeIf{
            it!=0.0
        }
        ?:1.0
        val n=V3(nx/nl,ny/nl,nz/nl)
        val ref=if(abs(n.y)<0.9)V3(0.0,1.0,0.0)else V3(1.0,0.0,0.0)
        val u0=V3(n.y*ref.z-n.z*ref.y,n.z*ref.x-n.x*ref.z,n.x*ref.y-n.y*ref.x)
        val ul=sqrt(dot(u0,u0)).takeIf{
            it!=0.0
        }
        ?:1.0
        val u=V3(u0.x/ul,u0.y/ul,u0.z/ul)
        val v=V3(n.y*u.z-n.z*u.y,n.z*u.x-n.x*u.z,n.x*u.y-n.y*u.x)
        val r=mix(80.0,62.0,e)
        val pts=mutableListOf<P2>()
        val top=mutableListOf<P2>()
        for(i in 0 until 40){
            val a=i/40.0*tau
            val wx=r*(u.x*cos(a)+v.x*sin(a))
            val wy=r*(u.y*cos(a)+v.y*sin(a))
            val wz=r*(u.z*cos(a)+v.z*sin(a))
            pts.add(P2(x+wx,y-wy*ce+wz*se))
            top.add(P2(x+wx*0.8-n.x*6,y-(wy*0.8+n.y*6)*ce+(wz*0.8+n.z*6)*se))
        }

        if(k>0.98)oval(c,502-62+8.0,614.0,124.0,22.0,blur(C.shadow,0.5,7.0))
        c.drawPath(poly(pts),p(C.lidShade))
        c.save()
        c.translate(0f,-5f)
        c.drawPath(poly(pts),p(C.lid))
        c.restore()
        c.drawPath(poly(top),stroke(0xFFE6E9F0.toInt(),2.0))
    }

    private fun steam(c: Canvas,pose: Pose,top: P2) {
        if(pose.steam<=0.01)return
        for(k in 0 until 3) {
            val path=Path()
            val x0=top.x-22+k*22
            val ph=pose.amb*4+k*1.7
            for(i in 0..16){
                val u=i/16.0
                val y=top.y-16-u*90
                val x=x0+8*sin(ph+u*6)*u
                if(i==0)path.moveTo(x.toFloat(),y.toFloat())else path.lineTo(x.toFloat(),y.toFloat())
            }

            c.drawPath(path,stroke(C.steam,4.0,0.55*pose.steam*(0.6+0.4*sin(ph))))
        }
    }

    private fun text(c: Canvas,value: String,x: Double,cy: Double,size: Double,typeface: Typeface,color: Int,alpha: Double=1.0,track: Double=0.0) {
        val paint=p(color,alpha).apply{
            this.typeface=typeface
            textSize=size.toFloat()
        }

        if(track==0.0){
            c.drawText(value,(x-paint.measureText(value)*0.5).toFloat(),(cy+size*0.36).toFloat(),paint)
            return
        }

        val widths=FloatArray(value.length)
        paint.getTextWidths(value,widths)
        var px=x-(widths.sum()+track*(value.length-1))/2
        value.forEachIndexed{
            i,ch->c.drawText(ch.toString(),px.toFloat(),(cy+size*0.36).toFloat(),paint)
            px+=widths[i]+track
        }
    }

    private fun labels(c: Canvas,pose: Pose) {
        val k=pose.text
        fun fade(a: Double,dy: Double,value: String,y: Double,size: Double,font: Typeface,color: Int){
            if(a>=0.01)text(c,value,329.0,y+dy,size,font,color,a)
        }

        fade(1-k,-10*k,"Error!",728.0,34.0,semi,C.title)
        fade(k,10*(1-k),"Trying again…",728.0,34.0,semi,C.title)
        val body=arrayOf("Something went wrong,","Please try again later")
        val retry=arrayOf("Brewing a fresh connection,","just a moment")
        for(i in 0..1){
            val y=if(i==0)798.0 else 840.0
            fade(1-k,-10*k,body[i],y,25.0,medium,C.body)
            fade(k,10*(1-k),retry[i],y,25.0,medium,C.body)
        }

        val scale=(1-0.04*pose.press).toFloat()
        c.save()
        c.translate(329f,963f)
        c.scale(scale,scale)
        round(c,-97.0,-14.0,194.0,76.0,38.0,blur(0xFFB8BEE0.toInt(),0.6,16.0))
        round(c,-107.0,-38.0,214.0,76.0,38.0,p(C.button))
        if(pose.dots<0.99)text(c,"RETRY",0.0,0.0,25.0,semi,C.buttonText,1-pose.dots,2.5)
        if(pose.dots>0.01)for(i in 0 until 3){
            val b=max(0.0,sin(pose.amb*7-i*0.7))
            circle(c,(i-1)*18.0,-6*b,5.5,p(C.buttonText,pose.dots*(0.5+0.5*b)))
        }

        c.restore()
    }

    fun drawScene(c: Canvas,width: Float,height: Float,pose: Pose) {
        val scale=width/402.0
        val s=SpilledCoffeeErrorTheme.S
        c.save()
        c.scale(scale.toFloat(),scale.toFloat())
        c.translate(0f,64f)
        c.scale(s.toFloat(),s.toFloat())
        val top=-64/s-10
        val bottom=(height/scale-64)/s+10
        room(c,pose.amb,top,bottom)
        paper(c)
        val fr=frame(pose.theta)
        puddle(c,pose,center(fr,215.0))
        val cup=cup(c,pose)
        if(pose.pour>0.01)c.drawPath(Path().apply{
            moveTo(cup.low.x.toFloat(),(cup.low.y-6).toFloat())
            quadTo((cup.low.x-30).toFloat(),(cup.low.y+30).toFloat(),236f,584f)
        }
        ,stroke(C.coffee,12*pose.pour,0.95))
        if(pose.drip>=0&&pose.drip<0.35){
            val q=pose.drip/0.35
            oval(c,cup.low.x-4,mix(cup.low.y,576.0,q*q)-5,8.0,10.0,p(C.coffee))
        }

        lid(c,pose,cup)
        steam(c,pose,center(fr,225.0))
        labels(c,pose)
        if(pose.rewind>0.01)for(i in 0 until 6){
            val y=(pose.amb*900+i*260)%(bottom-top)+top
            box(c,-10.0,y,678.0,(3+(i%2)*2).toDouble(),p(0xFF8FA2C8.toInt(),0.09*pose.rewind))
        }

        c.restore()
        grain.draw(c,width,height)
    }
}
