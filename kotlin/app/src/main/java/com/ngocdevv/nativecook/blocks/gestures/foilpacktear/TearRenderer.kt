package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*

/** Same retained draw order as the Skia scene; only the backend is Android Canvas / AGSL. */
@TargetApi(33)
internal class TearRenderer(assets: TearAssets) {
    private val frame = TearMaterial(FRAME_AGSL).apply { image("uLabel", assets.tabLabel, decal = true) }
    private val foil = TearMaterial(FOIL_AGSL).apply {
        image("uMaps", assets.packMaps, raw = true)
        image("uPrint", assets.packPrint)
    }
    private val curl = TearMaterial(CURL_AGSL)
    private val lip = TearMaterial(LIP_AGSL).apply { image("uMaps", assets.packMaps, raw = true) }
    private val card = TearMaterial(CARD_AGSL).apply {
        image("uFace", assets.cardFace)
        image("uBack", assets.cardBack)
        image("uMaps", assets.cardMaps, raw = true)
    }
    private val light = TearMaterial(LIGHT_AGSL, additive = true)
    private val mouth = TearMaterial(MOUTH_AGSL)
    private val rays = TearMaterial(RAYS_AGSL, additive = true)
    private val shadow = TearMaterial(SHADOW_AGSL)
    private val spark = TearMaterial(SPARK_AGSL, additive = true).apply { image("uSpark", assets.spark, decal = true) }
    private val geometry = TearGeometry()
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(206, 227, 212)
        style = Paint.Style.STROKE
        strokeWidth = 0.65f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 210, 122)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        maskFilter = android.graphics.BlurMaskFilter(7.8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }

    fun draw(canvas: Canvas, p: TearPose, clock: Double, width: Float, height: Float, reduced: Boolean) {
        geometry.update(p)
        uniforms(p, clock)
        val scale = min(width / 402f, height / 874f)
        val ox = (width - 402 * scale) / 2
        val oy = (height - 874 * scale) / 2
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        canvas.concat(geometry.stage)
        canvas.drawRect(-ox / scale - 32, -oy / scale - 32, width / scale + 32, height / scale + 32, frame.paint)
        canvas.drawRect(-24f, 108f, 426f, 648f, rays.paint)
        if (!reduced) repeat(48) { particle(canvas, p, clock, it, true) }
        if (p.packOpacity > 0) {
            canvas.save()
            canvas.concat(geometry.pack)
            val layer = alphaLayer(canvas, p.packOpacity)
            canvas.save()
            canvas.translate(115f, 203f)
            shadow.uniform("uExtent", 104.0, 178.0)
            shadow.uniform("uStrength", 0.38)
            canvas.drawRect(-136f, -210f, 136f, 210f, shadow.paint)
            canvas.restore()
            canvas.drawPath(geometry.opening, mouth.paint)
            edgePaint.alpha = (clamp(p.mouth * 0.85) * 255).toInt()
            canvas.drawPath(geometry.backLip, edgePaint)
            if (layer) canvas.restore()
            canvas.restore()
        }
        if (p.cardFlip > 0.02) packFront(canvas, p)
        if (p.cardOpacity > 0) {
            canvas.save()
            canvas.translate(p.cardX.toFloat(), p.cardY.toFloat())
            canvas.scale(p.cardScale.toFloat(), p.cardScale.toFloat())
            val layer = alphaLayer(canvas, p.cardOpacity)
            canvas.save()
            canvas.translate(0f, 8f)
            shadow.uniform("uExtent", 90 * abs(cos(p.cardFlip + p.cardTiltY)), 126.0)
            shadow.uniform("uStrength", 0.32)
            canvas.drawRect(-120f, -156f, 120f, 156f, shadow.paint)
            canvas.restore()
            canvas.drawRect(-140f, -164f, 140f, 164f, card.paint)
            if (layer) canvas.restore()
            canvas.restore()
        }
        if (p.cardFlip <= 0.02) packFront(canvas, p)
        if (p.packOpacity > 0 && p.leak > 0) {
            canvas.save()
            canvas.concat(geometry.pack)
            val layer = alphaLayer(canvas, p.packOpacity)
            canvas.save()
            canvas.clipOutPath(geometry.opening)
            canvas.drawRect(-190f, -280f, 406f, 240f, light.paint)
            canvas.restore()
            light.paint.alpha = 31
            canvas.drawPath(geometry.opening, light.paint)
            light.paint.alpha = 255
            glowPaint.alpha = (clamp(p.leak * 0.6) * 255).toInt()
            canvas.drawPath(geometry.lip, glowPaint)
            lip.paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
            stroke(canvas, geometry.lip, 1.4f, p.leak * 0.6, 1.0, 0.965, 0.847, 1.0)
            lip.paint.xfermode = null
            if (layer) canvas.restore()
            canvas.restore()
        }
        if (p.stripFree >= 0.5 && p.stripOpacity > 0) {
            canvas.save()
            canvas.concat(geometry.freeCurl)
            curl.paint.alpha = (clamp(p.stripOpacity) * 255).toInt()
            canvas.drawRect(-34f, -12f, 26f, 68f, curl.paint)
            canvas.restore()
        }
        if (!reduced) repeat(18) { particle(canvas, p, clock, it + 100, false) }
        canvas.restore()
    }

    private fun packFront(canvas: Canvas, p: TearPose) {
        if (p.packOpacity <= 0) return
        canvas.save()
        canvas.concat(geometry.pack)
        val layer = alphaLayer(canvas, p.packOpacity)
        canvas.save()
        canvas.clipPath(geometry.cut)
        canvas.drawRect(0f, 0f, 216f, 376f, foil.paint)
        stroke(canvas, geometry.lip, 6f, 1.0, 0.72, 1.0, 0.89, 0.14)
        canvas.restore()
        if (p.tearFront > 0.1) stroke(canvas, geometry.lip, 0.75f, 0.9, 0.89, 0.91, 0.81, 1.0)
        if (p.tearFront > 0.1 && p.stripFree < 0.5) {
            canvas.save()
            canvas.concat(geometry.curl)
            curl.paint.alpha = (clamp(p.stripOpacity) * 255).toInt()
            canvas.drawRect(-34f, -12f, 26f, 68f, curl.paint)
            canvas.restore()
        }
        if (layer) canvas.restore()
        canvas.restore()
    }

    private fun stroke(canvas: Canvas, path: Path, width: Float, alpha: Double, vararg colour: Double) {
        lip.uniform("uColour", *colour)
        lip.paint.style = Paint.Style.STROKE
        lip.paint.strokeWidth = width
        lip.paint.alpha = (clamp(alpha) * 255).toInt()
        canvas.drawPath(path, lip.paint)
    }

    private fun uniforms(p: TearPose, clock: Double) {
        val time = ((round(clock * 1e6) % 13e6) + 13e6) % 13e6 / 1e6
        frame.uniform("uHeat", p.frameHeat)
        frame.uniform("uRing", p.frameRing)
        frame.uniform("uTab", p.tabGlow)
        frame.uniform("uClock", time)
        rays.uniform("uHeat", p.frameHeat)
        rays.uniform("uClock", time)
        rays.uniform("uAge", p.sparkleAge)
        foil.uniform("uLight", p.lightX, p.lightY)
        foil.uniform("uClock", time)
        foil.uniform("uCalm", p.calm)
        foil.uniform("uFront", p.tearFront)
        foil.uniform("uLeak", p.leak)
        foil.uniform("uMouth", p.mouth)
        foil.uniform("uEdgeY", tearYAt(p.tearLine, p.tearFront))
        curl.uniform("uRadius", 7 + 9 * sqrt(clamp(p.tearFront / 216)))
        curl.uniform("uHeight", tearYAt(p.tearLine, p.tearFront))
        curl.uniform("uFree", p.stripFree)
        curl.uniform("uRoll", p.tearFront / 7)
        curl.uniform("uLight", p.lightX + sin(p.stripRot) * p.stripFree, p.lightY)
        curl.uniform("uLeak", p.leak)
        val cy = cos(p.cardFlip + p.cardTiltY)
        val sy = sin(p.cardFlip + p.cardTiltY)
        val cx = cos(p.cardTiltX)
        val sx = sin(p.cardTiltX)
        card.uniform("uRot", cy, sy * sx, -sy * cx, 0.0, cx, sx, sy, -cy * sx, cy * cx)
        card.uniform("uLight", p.lightX, p.lightY)
        card.uniform("uTilt", p.cardTiltX, p.cardTiltY)
        card.uniform("uSheen", p.sheen)
        card.uniform("uLeak", p.leak)
        card.uniform("uScale", p.cardScale)
        light.uniform("uFront", p.tearFront)
        light.uniform("uLeak", p.leak * p.packOpacity)
        light.uniform("uBurst", p.burst * p.packOpacity)
        light.uniform("uClock", time)
        light.uniform("uEdgeY", tearYAt(p.tearLine, p.tearFront * 0.5))
        mouth.uniform("uLeak", p.leak)
        mouth.uniform("uMouth", p.mouth)
        mouth.uniform("uEdgeY", tearYAt(p.tearLine, p.tearFront * 0.5))
    }

    private fun seedValue(seed: Int): Double {
        val value = sin(seed * 127.1 + 311.7) * 43758.5453
        return value - floor(value)
    }

    private fun particle(canvas: Canvas, p: TearPose, clock: Double, seed: Int, burst: Boolean) {
        if (burst && p.sparkleAge < 0 || !burst && p.dust <= 0) return
        val a = seedValue(seed + 1)
        val b = seedValue(seed + 23)
        val c = seedValue(seed + 87)
        val x: Double
        val y: Double
        val size: Double
        val rotation: Double
        val alpha: Double
        if (burst) {
            val age = max(0.0, p.sparkleAge)
            val travel = (1 - exp(-age * 5.2)) * (175 + b * 135)
            val theta = a * PI * 2
            x = p.cardX + cos(theta) * travel
            y = p.cardY + sin(theta) * travel + age * age * 42
            size = (8 + c * 14) * (0.88 + 0.12 * sin(age * 13 + a * 20))
            rotation = a * PI + age * (b - 0.5) * 1.2
            alpha = max(0.0, 1 - age / 1.6) * (0.65 + 0.55 * sin(age * 16 + b * 30).pow(2))
        } else {
            val cycle = (clock % 13) / 13
            val phase = (cycle * (1 + floor(b * 2)) + a) % 1
            val revealed = p.frameHeat
            val pulse = cycle * PI * 8 + b * 50
            val side = if (b < 0.5) -1 else 1
            val peekX = 93 + p.packX + 108 + side * (98 + c * 15 + phase * 21)
            x = peekX * (1 - revealed) + (p.cardX + side * (132 + c * 24)) * revealed + sin(phase * PI * 2 + c * 20) * 4
            y = (176 + p.packY + 34 - 6 - phase * 122) * (1 - revealed) + (p.cardY + 252 * 0.7 - phase * 370) * revealed
            size = 3 + c * 3 + 0.5 + 0.5 * sin(pulse + 1.7)
            rotation = a * PI + sin(cycle * PI * 2 + b * 12) * 0.7
            alpha = p.dust * sin(phase * PI) * (0.40 + 0.55 * sin(pulse).pow(2))
        }
        spark.uniform("uSize", size)
        spark.paint.alpha = (clamp(alpha) * 255).toInt()
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.rotate((rotation * 180 / PI).toFloat())
        val half = (size / 2).toFloat()
        canvas.drawRect(-half, -half, half, half, spark.paint)
        canvas.restore()
    }

    private fun alphaLayer(canvas: Canvas, alpha: Double): Boolean {
        if (alpha >= 0.999) return false
        canvas.saveLayerAlpha(null, (clamp(alpha) * 255).toInt())
        return true
    }
}
