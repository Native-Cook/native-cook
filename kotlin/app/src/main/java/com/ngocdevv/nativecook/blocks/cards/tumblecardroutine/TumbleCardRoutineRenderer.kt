package com.ngocdevv.nativecook.blocks.cards.tumblecardroutine

import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.Xml
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import org.xmlpull.v1.XmlPullParser
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

internal class TumbleCardRoutineRenderer(private val assets: TumbleCardRoutineAssets) {
    private data class IconPath(val path: Path, val color: Int, val strokeWidth: Float)
    private data class Pose(
        val role: Int,
        val x: Double,
        val y: Double,
        val scale: Double,
        val rotation: Double,
        val alpha: Double,
        val blur: Double = 0.0,
        val streak: Double = 0.0,
        val rx:Double=0.0, val y0:Double=0.0, val reveal:Double=.8,
    )
    private val motion = TumbleCardRoutineMotion
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val clip = Path()
    private val source = Rect()
    private val destination = RectF()
    private val chips = listOf("Plan a high-protein day", "Easy ways to drink more", "Recover after a long ride")
    private val icons = listOf("fork_knife", "droplet", "scale", "bolt", "flame", "chart", "plus", "microphone")
        .associateWith(::readIcon)
    private val cards = TumbleCardRoutineCards.mapIndexed { index, card ->
        Picture().apply {
            val canvas = beginRecording(ceil(card.w).toInt(), ceil(card.h).toInt())
            drawFace(canvas, index, card)
            endRecording()
        }
    }
    private val blurredCards = if (Build.VERSION.SDK_INT >= 31) BlurCache(cards) else null

    fun draw(canvas: Canvas, state: TumbleCardRoutineState, top: Float, bottom: Float, height: Float, reduced: Boolean) {
        canvas.drawColor(0xFF0F0F11.toInt())
        val time = state.clock
        val (scriptPosition, scriptPeek) = motion.pos(time)
        val pos = if (state.active) state.position else scriptPosition
        val peek = if (reduced) 0.0 else if (state.active) state.peek else scriptPeek
        val drop = if(reduced) 0.0 else if(state.active) state.drop else motion.drop(time)
        val anticipation = if (state.active || reduced) 0.0 else motion.anticipation(time)
        val (bumpIndex, bump) = if (state.active || reduced) -1 to 0.0 else motion.bump(time)
        var delta = if (state.active) 0.0 else pos - motion.pos(time - 1 / 240.0).first
        if (delta > 2.0) delta -= 4.0
        if (delta < -2.0) delta += 4.0
        val rate = if (state.active) state.rate else delta * 240
        val floor = floor(pos + 0.000001).toInt()
        val phase = if (reduced) 0.0 else max(0.0, pos - floor)

        clip.rewind()
        clip.addRoundRect(0f, 0f, 402f, 719f + bottom, 32f, 32f, Path.Direction.CW)
        val panel = canvas.save()
        canvas.clipPath(clip)
        canvas.drawColor(0xFFFBFBFB.toInt())
        text(canvas, "Nov 12, 2026", 201f, 97 + top, 13f, 0xFF8E8E93.toInt())
        text(canvas, "Today's routine", 201f, 133 + top, 22f, 0xFF111113.toInt(), bold = true)
        text(canvas, "Friday", 370f, 133 + top, 22f, 0xFFC0C2C2.toInt(), bold = true)
        for (role in 2 downTo 0) {
            val index = TumbleCardRoutineIndex(floor + role)
            val card = TumbleCardRoutineCards[index]
            val pose = pose(card, role, phase, peek, drop, if (index == bumpIndex) bump else 0.0, anticipation, rate, state, top, bottom)
            if (pose != null) {
                val arrival=max(phase,.22*peek)
                val rv=if(state.active) { if(role==0) state.reveal else if(role==1) .8*motion.seg(arrival,.35,1.0) else 0.0 } else motion.reveal(time,index)
                drawCard(canvas,index,card,pose.copy(reveal=rv))
            }
            if (role == 1) {
                paint.shader = LinearGradient(
                    0f, 600 + bottom, 0f, 719 + bottom,
                    0x00FBFBFB, Color.argb(((0.9 - 0.55 * peek * (1-motion.seg(max(phase,.22*peek),.22,.5))) * 255).toInt(), 251, 251, 251), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 600 + bottom, 402f, 719 + bottom, paint)
                paint.shader = null
            }
        }
        drawActions(canvas, bottom, state.nod)
        canvas.restoreToCount(panel)
        drawField(canvas, bottom, height)
    }

    private fun pose(
        card: TumbleCardRoutineCard, role: Int, phase: Double, peek: Double, drop: Double, bump: Double,
        anticipation: Double, rate: Double,
        state: TumbleCardRoutineState, top: Float, bottom: Float,
    ): Pose? {
        val arrival = max(phase, 0.22 * peek)
        val departure = max(0.0, (phase - 0.22 * peek) / (1 - 0.22 * peek))
        val slot = 520 + bottom + 0.86 * card.h / 2
        val rest = (card.y + top).toDouble()
        val clk=if(state.active) state.liveClock else state.clock
        val idle=1-motion.seg(departure,0.0,.08)
        val turn = if (card.tilt < 0) -1 else 1
        return when (role) {
            0 -> {
                val softness = peek * (1 - motion.seg(departure, 0.0, 0.25))
                val departureRate = if (phase > 0.22 * peek) rate / (1 - 0.22 * peek) else 0.0
                val speed = hypot(18 * departureRate, 832 * max(departure, 0.0001).pow(0.6) * departureRate)
                Pose(
                    role, card.x + turn * 18 * departure, rest - 520 * departure.pow(1.6) + 6 * anticipation - 5 * bump + 2*sin(2*PI*clk/4.5+1.1)*idle,
                    (1 - 0.1 * departure) * (1 - 0.03 * softness) * (1 - 0.03 * state.press),
                    card.tilt + turn * (38 * departure + 1.5 * anticipation + card.bump * bump) + state.nod + .4*sin(2*PI*clk/4.5)*idle,
                    1 - motion.seg(departure, 0.7, 1.0), 3 * softness, (0.043 * (speed - 240)).coerceIn(0.0, 64.0), rx=55*departure.pow(.9),
                )
            }
            1 -> {
                if (drop * (1 - arrival) >= 181) return null
                val settle = arrival.pow(0.56)
                Pose(
                    role, card.x.toDouble(),
                    motion.mix(slot + drop * (1 - arrival) - 6 * anticipation, rest, arrival.pow(1.15)) - 5 * bump,
                    motion.mix(0.86, 1.0, settle),
                    motion.mix(card.tilt * 0.6, card.tilt.toDouble(), settle) + turn * card.bump * bump,
                    1.0, 11 * (1 - motion.seg(arrival, 0.2, 0.85)), rx=-18*(1-settle), y0=card.h*.7*min(1.0,arrival/.22),
                )
            }
            else -> {
                val rise = if (state.active) 182 * (1 - motion.seg(phase, 0.4, 1.0)) else 182.0
                if (rise >= 181) null else Pose(role, card.x.toDouble(), slot + rise, 0.86, card.tilt * 0.6, 1.0, 11.0, rx=-18.0)
            }
        }
    }

    private fun drawCard(canvas: Canvas, index: Int, card: TumbleCardRoutineCard, pose: Pose) {
        if (pose.alpha <= 0) return
        val restore = canvas.save()
        canvas.translate(pose.x.toFloat(), pose.y.toFloat())
        canvas.rotate(pose.rotation.toFloat())
        val camera=Camera(); val matrix=Matrix()
        camera.setLocation(0f,0f,-900f/72f); camera.rotateX((-pose.rx).toFloat()); camera.getMatrix(matrix); canvas.concat(matrix)
        canvas.scale((pose.scale*1.1).toFloat(), (pose.scale*1.1).toFloat())
        canvas.translate(-card.w / 2, -card.h / 2)
        val layer = if (pose.alpha < 0.999) {
            canvas.saveLayerAlpha(-45f, -30f, card.w + 45, card.h + 76, (255 * pose.alpha).roundToInt())
        } else null
        paint.color = Color.argb(35, Color.red(card.shadow), Color.green(card.shadow), Color.blue(card.shadow))
        paint.setShadowLayer(16f, 0f, 12f, paint.color)
        canvas.drawRoundRect(2f, 6f, card.w - 2, card.h, card.r, card.r, paint)
        paint.clearShadowLayer()
        paint.alpha = 255
        val picture=if(pose.reveal>0 && pose.reveal<.8) Picture().apply {
            val recording=beginRecording(ceil(card.w).toInt(),ceil(card.h).toInt()); drawFace(recording,index,card,pose.reveal); endRecording()
        } else cards[index]
        if(Build.VERSION.SDK_INT>=31 && canvas.isHardwareAccelerated && (pose.blur>.3 || pose.streak>=1)) {
            blurredCards?.draw(canvas,index,picture,pose.blur,pose.y0,pose.role==0,pose.streak)
        } else canvas.drawPicture(picture)
        if (layer != null) canvas.restoreToCount(layer)
        canvas.restoreToCount(restore)
    }

    private fun drawFace(canvas: Canvas, index: Int, card: TumbleCardRoutineCard, rv:Double=.8) {
        clip.rewind()
        clip.addRoundRect(0f, 0f, card.w, card.h, card.r, card.r, Path.Direction.CW)
        canvas.clipPath(clip)
        val ink = if (index == 0) 0xFF3A1606.toInt() else Color.WHITE
        when (index) {
            0 -> {
                canvas.drawColor(0xFFF56419.toInt())
                roundRect(canvas, 8f, 36f, 242f, 278f, 8f, 0xFFFBFBFB.toInt())
            }
            2 -> {
                paint.shader = LinearGradient(0f, 0f, 0f, card.h, 0xFF16191D.toInt(), 0xFF111316.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, card.w, card.h, paint)
                paint.shader = null
            }
            else -> {
                assets.photos[index]?.let {
                    val settle=if(rv>=.8) 0.0 else motion.spring(rv,1.0,0.0,0.0,22.1,157.9).coerceIn(0.0,1.0)
                    photo(canvas,it,0f,0f,card.w,card.h,1+.12*settle,8*settle)
                }
                paint.shader = LinearGradient(
                    0f, 0f, 0f, card.h,
                    intArrayOf(0x4D000000, Color.TRANSPARENT, 0x57000000), null, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, card.w, card.h, paint)
                paint.shader = null
            }
        }
        val headY = if (index == 0) 18f else 22f
        icon(canvas, listOf("fork_knife", "droplet", "scale", "bolt")[index], 11f, headY - 9, 18f)
        text(canvas, card.name, 33f, headY, if (index == 0) 14f else 15f, ink, bold = true, center = false)
        if (index == 2) {
            roundRect(canvas, 123f, 12f, 210f, 32f, 10f, 0xFF6FCC82.toInt())
            text(canvas, "− 1.8 (2.7%)", 166.5f, 22f, 11f, 0xFF0D2B14.toInt(), bold = true)
        } else {
            val count = if (index == 3) 4 else 5
            val active = if (index == 0) 3 else if (index == 1) 2 else 4
            repeat(count) { dot ->
                paint.color = ink
                paint.alpha = if (dot < active) 255 else if(index==0) 77 else 89
                val pop=if(dot<active && rv>0&&rv<.8) sin(PI*motion.seg(rv,.12+.04*dot,.32+.04*dot)) else 0.0
                canvas.drawCircle(card.w - (if(index==0)14 else 16) - 3 - (count - 1 - dot) * 9, headY, (3*(1+.4*pop)).toFloat(), paint)
            }
        }
        val center = card.w / 2
        when (index) {
            0 -> {
                text(canvas, "left 960", center, 61f, 12f, 0xFF8E8E93.toInt())
                number(canvas,"1640","kcal",center,150f,0xFF211D12.toInt(),rv,0.0)
                text(canvas, "63% of daily goal", center, 235f, 12f, 0xFF55555B.toInt())
                text(canvas, "2600 kcal", center, 251f, 12f, 0xFF211D12.toInt(), bold = true)
            }
            1 -> {
                number(canvas,"450","ml",center,151f,ink,rv,0.0)
                text(canvas, "water drunk of 2000 ml", center, 244f, 12f, ink)
                text(canvas, "your daily goal", center, 260f, 12f, ink, bold = true)
            }
            2 -> {
                number(canvas,"64","kg",center,98f,ink,rv,0.0)
                repeat(4) {
                    val x = 22f + 46 * it
                    val k=if(rv<=0||rv>=.8) 1.0 else if(it<3) 1-.7*sin(PI*motion.out(motion.seg(rv,.08+.05*it,.48+.05*it))) else if(rv<.3) 1-.85*sin(PI/2*motion.seg(rv,.08,.3)) else motion.spring(rv-.3,.15,1.0,0.0,19.0,195.0)
                    val bh=(56*max(0.0,k)).toFloat()
                    roundRect(canvas, x, 196f-bh, x + 40, 196f, min(9f,bh/2), if (it == 3) 0xFF3FD05A.toInt() else 0xFF2A2D31.toInt())
                }
                text(canvas, "Your goal", center, 225f, 12f, 0xFF7C8088.toInt())
                text(canvas, "Lose weight", center, 241f, 12f, ink, bold = true)
            }
            else -> {
                icon(canvas, "flame", 106f, 88f, 16f)
                text(canvas, "You burned", 126f, 97f, 12f, ink, center = false)
                number(canvas,"310","kcal",center,144f,ink,rv,0.0)
                number(canvas,"5.2","km",center,194f,ink,rv,.08)
            }
        }
        paint.alpha = 255
    }

    private fun number(c:Canvas,value:String,unit:String,cx:Float,cy:Float,color:Int,rv:Double,delay:Double) {
        paint.color=color; paint.typeface=assets.semibold; paint.textSize=40f; paint.textAlign=Paint.Align.LEFT
        val cell=(0..9).maxOf { paint.measureText(it.toString()) }-1.2f
        val widths=value.map { if(it=='.') paint.measureText(".")-1.2f else cell }
        val unitWidths=FloatArray(unit.length); paint.getTextWidths(unit,unitWidths)
        val unitWidth=unitWidths.sum()-1.2f*(unit.length-1)
        var x=cx-(widths.sum()+8+unitWidth)/2; val base=cy+.727f*40/2
        for((index,ch) in value.withIndex()) {
            val e=motion.out(motion.seg(rv-delay,.045*index,.045*index+.42)); val q=e*10; val k=floor(q).toInt(); val f=q-k
            val restore=c.save(); c.clipRect(x-6,base-.727f*40-.16f*40,x+widths[index]+6,base+.16f*40)
            fun digit(label:String,y:Float) { c.drawText(label,x+(widths[index]-paint.measureText(label))/2,y,paint) }
            if(ch=='.') digit(".",base) else {
                val d=ch.digitToInt(); digit(((d+k)%10).toString(),base-(f*40).toFloat())
                if(e>0&&e<1) digit(((d+k+1)%10).toString(),base+((1-f)*40).toFloat())
            }
            c.restoreToCount(restore); x+=widths[index]
        }
        x+=8
        for(i in unit.indices) { c.drawText(unit,i,i+1,x,base,paint); x+=unitWidths[i]-1.2f }
    }

    private fun drawActions(canvas: Canvas, bottom: Float, nod: Double) {
        val y = 667 + bottom
        paint.color = 0xFFF1F1F1.toInt()
        canvas.drawCircle(41f, y, 20f, paint)
        icon(canvas, "chart", 31f, y - 10, 20f)
        paint.color = 0xFF0F0F11.toInt()
        canvas.drawCircle(201f, y, 32f - (nod * 1.25).toFloat(), paint)
        icon(canvas, "plus", 192f, y - 9, 18f)
        val restore = canvas.save()
        clip.rewind()
        clip.addCircle(364f, y, 19f, Path.Direction.CW)
        canvas.clipPath(clip)
        photo(canvas, assets.avatar, 345f, y - 19, 383f, y + 19)
        canvas.restoreToCount(restore)
    }

    private fun drawField(canvas: Canvas, bottom: Float, height: Float) {
        if (height <= 719 + bottom) return
        roundRect(canvas, 19f, 736 + bottom, 383f, 788 + bottom, 14f, 0xFF262628.toInt())
        text(canvas, "Ask me…", 35f, 762 + bottom, 15f, 0xFF9A9AA0.toInt(), center = false)
        icon(canvas, "microphone", 347f, 752 + bottom, 20f)
        var left = 19f
        for (label in chips) {
            paint.typeface = assets.regular
            paint.textSize = 13f
            val width = paint.measureText(label) + 24
            roundRect(canvas, left, 800 + bottom, left + width, 834 + bottom, 10f, 0xFF262628.toInt())
            text(canvas, label, left + 12, 817 + bottom, 13f, 0xFFB0B0B5.toInt(), center = false)
            left += width + 8
        }
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        paint.color = color
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    private fun text(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = true) {
        paint.color = color
        paint.typeface = if (bold) assets.semibold else assets.regular
        paint.textSize = size
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun photo(c: Canvas, image: Bitmap, l: Float, t: Float, r: Float, b: Float, zoom:Double=1.0,parallax:Double=0.0) {
        val scale = max((r - l) / image.width, (b - t) / image.height)*zoom.toFloat()
        val dx = ((image.width - (r - l) / scale) / 2).roundToInt()
        val sourceHeight=(b-t)/scale
        val dy = (((image.height-sourceHeight)/2-parallax/scale).coerceIn(0.0,(image.height-sourceHeight).toDouble())).roundToInt()
        source.set(dx,dy,(dx+(r-l)/scale).roundToInt().coerceAtMost(image.width),(dy+sourceHeight).roundToInt().coerceAtMost(image.height))
        destination.set(l, t, r, b)
        paint.alpha = 255
        c.drawBitmap(image, source, destination, paint)
    }

    private fun readIcon(name: String): List<IconPath> = assets.openIcon(name).use { stream ->
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "path") continue
                val data = parser.getAttributeValue(null, "d") ?: continue
                val stroke = parser.getAttributeValue(null, "stroke")
                val fill = parser.getAttributeValue(null, "fill") ?: "#FFFFFF"
                val path = PathParser().parsePathString(data).toPath().asAndroidPath()
                if (parser.getAttributeValue(null, "fill-rule") == "evenodd") path.fillType = Path.FillType.EVEN_ODD
                add(IconPath(path, Color.parseColor(stroke ?: fill), parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 0f))
            }
        }
    }

    private fun icon(c: Canvas, name: String, x: Float, y: Float, size: Float) {
        val restore = c.save()
        c.translate(x, y)
        val scale = size / if (name == "plus") 18f else 20f
        c.scale(scale, scale)
        for (layer in icons.getValue(name)) {
            paint.color = layer.color
            paint.style = if (layer.strokeWidth > 0) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = layer.strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            c.drawPath(layer.path, paint)
        }
        paint.style = Paint.Style.FILL
        c.restoreToCount(restore)
    }

    @RequiresApi(31)
    private class BlurCache(pictures:List<Picture>) {
        private val nodes=pictures.mapIndexed { i,p -> RenderNode("Tumble card $i").apply { setPosition(0,0,p.width,p.height); setClipToBounds(false) } }
        fun draw(canvas:Canvas,index:Int,picture:Picture,depth:Double,y0:Double,uniform:Boolean,streak:Double) {
            val node=nodes[index]
            node.beginRecording().drawPicture(picture); node.endRecording()
            var effect:RenderEffect?=null
            if(depth>.3) {
                val blurred=RenderEffect.createBlurEffect(depth.toFloat(),depth.toFloat(),Shader.TileMode.DECAL)
                effect=if(uniform) blurred else {
                    val mask=RenderEffect.createShaderEffect(LinearGradient(0f,y0.toFloat(),0f,(y0+120).toFloat(),Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP))
                    val soft=RenderEffect.createBlendModeEffect(mask,blurred,BlendMode.SRC_IN)
                    val sharp=RenderEffect.createBlendModeEffect(mask,RenderEffect.createOffsetEffect(0f,0f),BlendMode.SRC_OUT)
                    RenderEffect.createBlendModeEffect(sharp,soft,BlendMode.PLUS)
                }
            }
            if(streak>=1) {
                // The throw is overwhelmingly vertical; keep its Gaussian narrow across the card.
                val motion=RenderEffect.createBlurEffect(.0001f,(.35*streak).toFloat(),Shader.TileMode.DECAL)
                effect=effect?.let { RenderEffect.createChainEffect(motion,it) } ?: motion
            }
            node.setRenderEffect(effect); canvas.drawRenderNode(node)
        }
    }
}
