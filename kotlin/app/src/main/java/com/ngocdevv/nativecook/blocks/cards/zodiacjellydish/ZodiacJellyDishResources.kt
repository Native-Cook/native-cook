package com.ngocdevv.nativecook.blocks.cards.zodiacjellydish

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import androidx.annotation.RequiresApi

internal class ZodiacJellyDishResources(context: Context, private val density: Float) {
    private val root = "zodiacjellydish/zodiacjellydish_"
    val dish = context.assets.open(root+"dish.webp").use { BitmapFactory.decodeStream(it) }
    val black: Typeface = Typeface.createFromAsset(context.assets,root+"Nunito-Black.ttf")
    val extraBold: Typeface = Typeface.createFromAsset(context.assets,root+"Nunito-ExtraBold.ttf")
    val semiBold: Typeface = Typeface.createFromAsset(context.assets,root+"Nunito-SemiBold.ttf")
    val label = Bitmap.createBitmap(kotlin.math.ceil(287*density).toInt(),kotlin.math.ceil(419*density).toInt(),Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); canvas.scale(this@ZodiacJellyDishResources.density,this@ZodiacJellyDishResources.density); canvas.translate(143.5f,209.5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun put(s: String, type: Typeface, size: Float, cx: Float, base: Float, color: Int, glow: Int) {
            paint.typeface = type; paint.textSize = size
            val x = cx-paint.measureText(s)/2
            paint.color = color; paint.setShadowLayer(5f,0f,0f,(glow and 0x00ffffff) or (191 shl 24))
            canvas.drawText(s,x,base,paint); paint.clearShadowLayer()
        }
        put("CHEESE",black,30f,27f,-148f,Color.WHITE,0xffff5fb4.toInt())
        put("PANCAKES",black,30f,27f,-99f,Color.WHITE,0xffff5fb4.toInt())
        put("VIRGO",black,29f,8f,113f,0xffffd23a.toInt(),0xffff9a1f.toInt())
        put("1.09–7.09",extraBold,21f,15f,146f,0xffffd23a.toInt(),0xffff9a1f.toInt())
    }
    private val shadow = Bitmap.createBitmap(kotlin.math.ceil(467*density).toInt(),kotlin.math.ceil(599*density).toInt(),Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); canvas.scale(this@ZodiacJellyDishResources.density,this@ZodiacJellyDishResources.density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8c000000.toInt(); maskFilter = BlurMaskFilter(26f,BlurMaskFilter.Blur.NORMAL) }
        canvas.drawRoundRect(100f,118f,367f,527f,64f,64f,paint)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val runtime = if (Build.VERSION.SDK_INT >= 33) ZodiacJellyDishRuntime(dish,label,density) else null
    fun draw(canvas: Canvas, w: Float, h: Float, top: Float, d: ZodiacJellyDishMotion.Driver) {
        canvas.save(); canvas.scale(density,density)
        canvas.drawColor(0xff07080d.toInt())
        for (i in 0 until 70) {
            val s1 = kotlin.math.sin(i*12.9898+78.233)*43758.5453
            val s2 = kotlin.math.sin(i*7.31+3.1)*24634.6345
            val x = ((s1-kotlin.math.floor(s1))*w).toFloat(); val y = ((s2-kotlin.math.floor(s2))*h).toFloat()
            val tw = 0.35+0.65*kotlin.math.max(0.0,kotlin.math.sin(2*Math.PI*(1+i%3)*d.t/8+i))
            paint.shader = null; paint.color = Color.WHITE; paint.alpha = ((0.08+0.3*tw*(i%5/5.0))*255).toInt()
            val side = if (i%7 == 0) 1.4f else 0.9f
            canvas.drawRect(x,y,x+side,y+side,paint)
        }
        paint.color = 0xff15161c.toInt(); paint.alpha = 255
        canvas.drawRoundRect(0f,top+2,w,h+40,34f,34f,paint)
        val cx = w/2-2; val cy = top+293
        rect.set(cx-233.5f,cy-299.5f,cx+233.5f,cy+299.5f)
        paint.color = Color.WHITE; canvas.drawBitmap(shadow,null,rect,paint)
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            paint.shader = runtime.shader(cx,cy,d)
            canvas.drawRect(cx-233.5f,cy-299.5f,cx+233.5f,cy+299.5f,paint)
            paint.shader = null
        } else {
            // API 26–32: keep the same card, photo, labels, pull and squash without AGSL.
            val j = d.jelly
            canvas.save(); canvas.translate(cx+j.px.toFloat()*0.7f,cy+j.py.toFloat()*0.7f)
            canvas.scale((1+j.sq*0.55).toFloat(),(1-j.sq).toFloat())
            paint.shader = LinearGradient(-143.5f,-209.5f,143.5f,209.5f,intArrayOf(0xffffa3cc.toInt(),0xffa4d5f4.toInt(),0xffb6e6b7.toInt(),0xffffc096.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(-143.5f,-209.5f,143.5f,209.5f,64f,64f,paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f; paint.color = 0xffe9eaf4.toInt()
            canvas.drawRoundRect(-137.5f,-203.5f,137.5f,203.5f,58f,58f,paint); paint.style = Paint.Style.FILL
            canvas.save(); canvas.clipPath(Path().apply { addRoundRect(-46f,-90f,78f,55f,14f,14f,Path.Direction.CW) })
            // Same cover fit and 0.74 crop as the shader.
            val ia = dish.width.toFloat()/dish.height; val wa = 124f/145f
            val sx = if (ia>wa) wa/ia else 1f; val sy = if (ia>wa) 1f else ia/wa
            val dw = 124/(sx*0.74f); val dh = 145/(sy*0.74f)
            rect.set(16-dw*0.5f,-17.5f-dh*0.47f,16+dw*0.5f,-17.5f+dh*0.53f)
            paint.color = Color.WHITE; canvas.drawBitmap(dish,null,rect,paint); canvas.restore()
            rect.set(-143.5f,-209.5f,143.5f,209.5f); canvas.drawBitmap(label,null,rect,paint)
            canvas.restore()
        }
        canvas.restore()
    }
}

@RequiresApi(33)
internal class ZodiacJellyDishRuntime(dish: Bitmap, label: Bitmap, private val density: Float) {
    private val effect = RuntimeShader(ZodiacJellyDishShaders.SLAB)
    private val photo = BitmapShader(dish,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
    private val print = BitmapShader(label,Shader.TileMode.DECAL,Shader.TileMode.DECAL).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
    init {
        effect.setInputShader("dish",photo); effect.setInputShader("label",print)
        effect.setFloatUniform("dishSize",dish.width.toFloat(),dish.height.toFloat())
        effect.setFloatUniform("hs",143.5f,209.5f); effect.setFloatUniform("corner",64f); effect.setFloatUniform("bevel",30f)
        effect.setFloatUniform("win",-46f,-90f,124f,145f); effect.setFloatUniform("winR",14f)
        effect.setFloatUniform("loop",8f); effect.setFloatUniform("labelScale",density)
        effect.setFloatUniform("labelOrigin",-143.5f,-209.5f)
    }
    fun shader(cx: Float, cy: Float, d: ZodiacJellyDishMotion.Driver): Shader {
        val j = d.jelly
        effect.setFloatUniform("center",cx,cy); effect.setFloatUniform("t",d.t.toFloat())
        effect.setFloatUniform("grab",j.gx.toFloat(),j.gy.toFloat()); effect.setFloatUniform("pull",j.px.toFloat(),j.py.toFloat())
        effect.setFloatUniform("squash",j.sq.toFloat()); effect.setFloatUniform("swirl",d.swirl.toFloat())
        effect.setFloatUniform("light",d.lx.toFloat(),d.ly.toFloat()); effect.setFloatUniform("pulse",d.pulse.toFloat())
        return effect
    }
}

internal class ZodiacJellyDishAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val sounds: Map<String,Int>
    private val streams = mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if (status==0) ready.add(id) }
        sounds = listOf("squish","add","tap").associateWith { cue ->
            context.assets.openFd("zodiacjellydish/zodiacjellydish_zjd-$cue.wav").use { pool.load(it,1) }
        }
    }
    fun play(cue: String) {
        val id = sounds[cue] ?: return
        if (id !in ready || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[cue]?.let { pool.stop(it) }; val v = if (cue == "tap") 0.5f else 0.85f
        streams[cue] = pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
