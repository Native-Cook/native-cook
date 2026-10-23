package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import android.content.res.AssetManager
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Retained Android paints; every animated value is read only in the Compose draw phase. */
internal class ZeroRenderer(private val assets: AssetManager) {
    private val card = ZeroCardAsset.decode()
    private val spark = ZeroSparkAsset.decode()
    private val bokeh = ZeroBokehAsset.decode()
    private val mark = ZeroMarkAsset.decode()
    private val arrow = ZeroArrowAsset.decode()
    private val env = ZeroEnvAsset.decode()
    private val softDust = ZeroSoftSprite(bokeh)
    private val softSpark = ZeroSoftSprite(spark,true)
    private val buttonGlow = ZeroButtonGlow()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val destination = RectF()
    private val clip = Path()
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu(env, card) else null
    private val fallbackZero by lazy { ZeroFallbackNumeralAsset.decode() }
    private val fallbackBackground by lazy { ZeroFallbackBackdropAsset.decode(assets) }
    private val far = arrayOf(floatArrayOf(62f,164f,6f),floatArrayOf(310f,112f,10f),floatArrayOf(358f,390f,8f),floatArrayOf(40f,452f,14f),floatArrayOf(272f,518f,8f))
    private val near = arrayOf(floatArrayOf(46f,344f,14f),floatArrayOf(350f,494f,22f),floatArrayOf(288f,216f,10f))

    fun draw(canvas: Canvas, l: ZeroLayout, frame: ZeroFrame, density: Float, reduced: Boolean, visible: Float) {
        val f = if (gpu == null) ZeroTimeline.rest(0.0, true).also {
            it.buttonPress = frame.buttonPress
            it.sweep = frame.sweep
        } else frame
        canvas.save()
        canvas.scale(density, density)
        if (gpu != null) gpu.backdrop(canvas, paint, l, f, visible) else fallbackBackdrop(canvas, l)
        canvas.saveLayerAlpha(0f, 0f, l.width, l.height, (255 * visible).toInt())
        dust(canvas, l, f, false, reduced)
        drawCard(canvas, l, f, reduced)
        if (gpu != null) gpu.numeral(canvas, paint, l, f, density) else fallbackNumeral(canvas, l)
        sparkles(canvas, l, f, reduced)
        dust(canvas, l, f, true, reduced)
        copy(canvas, l, f, reduced)
        canvas.restore()
        canvas.restore()
    }
    private fun image(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float, opacity: Double = 1.0) {
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = (opacity.coerceIn(0.0, 1.0) * 255).toInt()
        destination.set(x, y, x + w, y + h)
        canvas.drawBitmap(bitmap, null, destination, paint)
        paint.alpha = 255
    }
    private fun drawCard(canvas: Canvas, l: ZeroLayout, f: ZeroFrame, reduced: Boolean) {
        val parallax = if (reduced) 0f else sin(f.yaw + 0.33).toFloat()
        val w = 324 * l.k
        val h = 216 * l.k
        canvas.save()
        canvas.translate(l.offsetX + (8 - 40 * (1 - f.card).toFloat() - 6 * parallax) * l.k,
            (296 + 64 * (1 - f.card).toFloat() + 6 * f.cardExit.toFloat() + 2 * parallax) * l.k)
        canvas.rotate((-10 * (1 - f.card) - 1.5 * parallax).toFloat(), w / 2, h / 2)
        val scale = (0.92 + 0.08 * f.card).toFloat()
        canvas.scale(scale, scale, w / 2, h / 2)
        if (gpu != null) gpu.card(canvas, paint, w, h, f, parallax) else image(canvas, card, 0f, 0f, w, h)
        canvas.restore()
    }
    private fun dust(canvas: Canvas, l: ZeroLayout, f: ZeroFrame, front: Boolean, reduced: Boolean) {
        val points = if (front) near else far
        for ((i, p) in points.withIndex()) {
            val yawShift = if (reduced) 0f else sin(f.yaw + 0.33).toFloat() * if (front) -12 else -4
            val dx = yawShift + if (reduced) 0f else f.drift.toFloat() * (i + 2)
            val dy = if (reduced) 0f else f.drift.toFloat() * if (front) 6 else 3
            softDust.draw(canvas, l.offsetX + (p[0] - p[2] / 2 + dx) * l.k,
                (p[1] - p[2] / 2 + dy) * l.k, p[2] * l.k, (if (front) 2f else .3f) * l.k,
                if (front) f.nearDustOpacity[i] else f.farDustOpacity[i])
        }
    }
    private fun sparkles(canvas: Canvas, l: ZeroLayout, f: ZeroFrame, reduced: Boolean) {
        val positions = arrayOf(floatArrayOf(254f,494f,56f),floatArrayOf(56f,232f,28f),floatArrayOf(360f,172f,18f))
        for ((i, p) in positions.withIndex()) {
            val parallax = if (reduced) 0f else sin(f.yaw + 0.33).toFloat() * (if (i == 0) 4 else -3) * l.k
            val x = l.offsetX + p[0] * l.k + parallax
            val y = p[1] * l.k + if (reduced) 0f else f.drift.toFloat() * (i + 1) * l.k
            val size = p[2] * l.k
            canvas.save()
            canvas.translate(x, y)
            if (!reduced) canvas.rotate((f.sparkleRotation[i] * 180 / PI).toFloat())
            val scale = (f.sparkles[i] * f.twinkle[i]).toFloat()
            canvas.scale(scale, scale)
            image(canvas, spark, -size / 2, -size / 2, size, size, f.sparkleOpacity[i])
            canvas.restore()
        }
        if (f.glint > 0) {
            val p = ZeroTimeline.project(-0.42, 0.86, 0.26, f.yaw, f.pitch, f.scale,
                l.centreX.toDouble(), l.centreY.toDouble(), l.unit.toDouble())
            val s = (0.7 + 0.5 * f.glint).toFloat() * l.k
            canvas.save()
            canvas.translate(p.first.toFloat(),p.second.toFloat())
            canvas.scale(s/l.k,s/l.k)
            softSpark.draw(canvas,-24*l.k,-24*l.k,48*l.k,3*l.k,f.glint)
            paint.xfermode = additive
            image(canvas,spark,-16*l.k,-16*l.k,32*l.k,32*l.k,f.glint)
            paint.xfermode = null
            canvas.restore()
        }
    }
    private fun line(canvas: Canvas, value: String, size: Float, y: Float, height: Float, width: Float,
                     color: Int, weight: Int, opacity: Double, spacing: Float = 0f) {
        text.shader = null
        text.color = color
        text.alpha = (255 * opacity.coerceIn(0.0, 1.0)).toInt()
        text.textSize = size
        text.typeface = Typeface.create(if (weight == 600) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        val base = y + (height - text.fontMetrics.ascent - text.fontMetrics.descent) / 2
        val measured = text.measureText(value) + spacing * (value.length - 1)
        var x = (width - measured) / 2
        if (spacing == 0f) {
            canvas.drawText(value, x, base, text)
        } else {
            for (index in value.indices) {
                canvas.drawText(value, index, index + 1, x, base, text)
                x += text.measureText(value, index, index + 1) + spacing
            }
        }
    }
    private fun copy(canvas: Canvas, l: ZeroLayout, f: ZeroFrame, reduced: Boolean) {
        fun y(index: Int, base: Float) = base + if (reduced) 0f else f.copyY[index].toFloat()
        val pillY = y(0, l.pillTop)
        text.textSize = 13 * l.typeScale
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val pillW = 24 + 14 + 6 + text.measureText("nought")
        val pillX = (l.width - pillW) / 2
        paint.shader = null
        paint.color = Color.argb((15 * f.copy[0]).toInt(),255,255,255)
        canvas.drawRoundRect(pillX,pillY,pillX+pillW,pillY+28,14f,14f,paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb((56 * f.copy[0]).toInt(),185,200,255)
        canvas.drawRoundRect(pillX+.5f,pillY+.5f,pillX+pillW-.5f,pillY+27.5f,14f,14f,paint)
        paint.style = Paint.Style.FILL
        image(canvas,mark,pillX+12,pillY+7,14f,14f,f.copy[0])
        text.color = Color.rgb(225,230,255)
        text.alpha = (255*f.copy[0]).toInt()
        canvas.drawText("nought",pillX+32,pillY+(28-text.fontMetrics.ascent-text.fontMetrics.descent)/2,text)
        line(canvas,"0% deposit fees.",34*l.typeScale,y(1,l.headlineTop),l.headlineLine,l.width,Color.rgb(185,200,255),600,f.copy[1],-0.6f)
        line(canvas,"Every time.",34*l.typeScale,y(2,l.headlineTop+l.headlineLine),l.headlineLine,l.width,Color.rgb(185,200,255),600,f.copy[2],-0.6f)
        line(canvas,"Zero to get in. Everything to gain.",17*l.typeScale,y(3,l.bodyTop),24*l.typeScale,l.width,Color.rgb(219,219,219),400,f.copy[3])
        button(canvas,l,f,y(4,l.buttonY))
    }
    private fun button(canvas: Canvas, l: ZeroLayout, f: ZeroFrame, top: Float) {
        val w = l.buttonWidth
        val h = l.buttonHeight
        canvas.save()
        canvas.translate(24f,top)
        val scale = (1 - 0.03*f.buttonPress).toFloat()
        canvas.scale(scale,scale,w/2,h/2)
        canvas.saveLayerAlpha(-72f,-72f,w+72,h+72,(255*f.copy[4]).toInt())
        buttonGlow.draw(canvas,w,h,f.buttonPress)
        clip.reset()
        clip.addRoundRect(0f,0f,w,h,16f,16f,Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.shader = LinearGradient(0f,0f,0f,h,Color.rgb(220,228,255),Color.rgb(169,186,255),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        if (f.sweep > -0.999 && f.sweep < 0.999) {
            canvas.save()
            canvas.translate((((f.sweep+1)/2)*(w+160)-100).toFloat(),-32f)
            canvas.rotate(-22f,28f,64f)
            paint.shader = LinearGradient(0f,0f,56f,0f,intArrayOf(Color.TRANSPARENT,Color.argb(106,255,255,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,56f,128f,paint)
            canvas.restore()
        }
        paint.shader = LinearGradient(0f,0f,0f,h,
            Color.argb(179,255,255,255),Color.argb(64,220,228,255),Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(.5f,.5f,w-.5f,h-.5f,16f,16f,paint)
        paint.style = Paint.Style.FILL
        line(canvas,"Start now",17*l.typeScale,0f,h,w,Color.rgb(10,16,56),600,1.0)
        val chip = h-16
        val x = w-8-chip+f.arrow.toFloat()
        val y = 8-f.arrow.toFloat()
        paint.shader = LinearGradient(0f,y,0f,y+chip,Color.rgb(142,162,255),Color.rgb(110,132,245),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(x,y,x+chip,y+chip,12f,12f,paint)
        image(canvas,arrow,x+(chip-20)/2,y+(chip-20)/2,20f,20f)
        paint.shader = LinearGradient(0f,y,0f,y+chip,
            Color.argb(115,255,255,255),Color.argb(33,255,255,255),Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(x+.5f,y+.5f,x+chip-.5f,y+chip-.5f,12f,12f,paint)
        paint.style = Paint.Style.FILL
        paint.shader = null
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
    private fun fallbackBackdrop(canvas: Canvas, l: ZeroLayout) {
        image(canvas,fallbackBackground,0f,0f,l.width,l.height)
    }
    private fun fallbackNumeral(canvas: Canvas, l: ZeroLayout) {
        image(canvas,fallbackZero,l.offsetX,0f,402*l.k,536*l.k)
    }

    @android.annotation.TargetApi(33)
    private class Gpu(env: Bitmap, card: Bitmap) {
        private val zero = RuntimeShader(ZERO_NUMERAL_SHADER)
        private val backdrop = RuntimeShader(ZERO_BACKDROP_SHADER)
        private val cardLight = RuntimeShader(ZERO_CARD_SHADER)
        init {
            zero.setInputShader("uEnv",BitmapShader(env,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
            cardLight.setInputShader("cardImage",BitmapShader(card,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
        }
        fun backdrop(canvas: Canvas, paint: Paint, l: ZeroLayout, f: ZeroFrame, visible: Float) {
            backdrop.setFloatUniform("resolution",l.width,l.height)
            backdrop.setFloatUniform("drift",f.drift.toFloat()*visible)
            backdrop.setFloatUniform("flash",f.flash.toFloat()*visible)
            backdrop.setFloatUniform("dim",1-visible*(1-f.dim.toFloat()))
            paint.shader = backdrop
            paint.alpha = 255
            canvas.drawRect(0f,0f,l.width,l.height,paint)
        }
        fun card(canvas: Canvas, paint: Paint, w: Float, h: Float, f: ZeroFrame, yaw: Float) {
            cardLight.setFloatUniform("dimensions",w,h)
            cardLight.setFloatUniform("sweep",f.sweep.toFloat())
            cardLight.setFloatUniform("yaw",yaw)
            cardLight.setFloatUniform("strength",(f.build*(1-f.drain)).toFloat())
            cardLight.setFloatUniform("fuse",f.fuse.toFloat())
            paint.shader = cardLight
            paint.alpha = (255*f.cardOpacity).toInt()
            canvas.drawRect(0f,0f,w,h,paint)
            paint.alpha = 255
        }
        fun numeral(canvas: Canvas, paint: Paint, l: ZeroLayout, f: ZeroFrame, density: Float) {
            zero.setFloatUniform("uCentre",l.centreX,l.centreY)
            zero.setFloatUniform("uUnit",l.unit)
            zero.setFloatUniform("uPixelRatio",density)
            zero.setFloatUniform("uCamera",7f)
            zero.setFloatUniform("uTurn",cos(f.yaw).toFloat(),sin(f.yaw).toFloat(),cos(f.pitch).toFloat(),sin(f.pitch).toFloat())
            zero.setFloatUniform("uDock",.76f,-.1f,.3f)
            zero.setFloatUniform("uScale",f.scale.toFloat())
            zero.setFloatUniform("uBuild",f.build.toFloat())
            zero.setFloatUniform("uDrain",f.drain.toFloat())
            zero.setFloatUniform("uFuse",f.fuse.toFloat())
            zero.setFloatUniform("uRipple",f.ripple.toFloat())
            zero.setFloatUniform("uRippleOrigin",f.rippleX.toFloat(),f.rippleY.toFloat())
            zero.setFloatUniform("uSweep",f.sweep.toFloat())
            zero.setFloatUniform("uPercent",f.percent.toFloat())
            zero.setFloatUniform("uFlash",f.flash.toFloat())
            paint.shader = zero
            canvas.drawRect(0f,0f,l.width,l.height,paint)
        }
    }
}
