@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Canvas
import android.graphics.Path
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberMotion.Companion.seg
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.countLight
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.drawing
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Airborne light sits above copy but behind the pulsing heart lens. */
internal class EmberEffects {
    private val brush = EmberPaint()
    private val paint get() = brush.value
    private val heartClip = Path(EmberPaths.heart).apply { offset(E.heart.x, E.heart.y) }

    fun draw(canvas: Canvas, motion: EmberMotion) {
        if (motion.reduced || motion.quality == EmberQuality.Low) return
        // Scale the inverse heart clip, then undo the transform for effect coordinates.
        canvas.save()
        val pulse = EmberMotion.beat(motion.phase)
        canvas.scale(pulse, pulse, E.heart.x + E.heart.cx, E.heart.y + E.heart.cy)
        canvas.clipOutPath(heartClip)
        canvas.scale(1 / pulse, 1 / pulse, E.heart.x + E.heart.cx, E.heart.y + E.heart.cy)
        // Deterministic phases/directions are the reference particle recipe, not random samples.
        repeat(8) { index ->
            val life = (motion.time * 0.55f + index * 0.137f) % 1
            val x = E.embers.x + index * E.embers.pitch + sin(motion.time * 1.7f + index * 2.1f) * drawing.emberSway
            val y = E.count.top + E.count.h + E.embers.below - life * drawing.emberRise
            brush.reset(
                emberTone.wave, seg(motion.charge, 0.2f, 0.7f) * sin(PI.toFloat() * life) * 0.8f * countLight(x, y),
            )
            canvas.drawCircle(x, y, drawing.emberRadius, paint)
        }
        val head = E.bar.x + max(E.bar.h / 2, motion.progress * E.bar.w) - drawing.waveHeadInset
        val wave = seg(motion.ignition, 0f, 0.58f)
        if (motion.ignition > 0 && wave < 1) {
            brush.reset(emberTone.wave, 0.55f * (1 - wave), stroke = drawing.waveStroke - drawing.waveThinning * wave)
            canvas.drawCircle(head, E.bar.centerY, drawing.waveRadius * (1 - (1 - wave).pow(4)), paint)
        }
        if (motion.ignition > 0) repeat(if (motion.quality == EmberQuality.Medium) 10 else 18) { index ->
            val age = motion.ignition * M.ignite
            val angle = -(20 + index * 53 % 140) * PI.toFloat() / 180
            val speed = drawing.sparkSpeed + (index * 37 % 100) / 100f * drawing.sparkSpeedRange
            val life = 0.7f + (index * 29 % 100) / 100f * 0.4f
            val x = head + cos(angle) * speed * age
            val y = E.bar.centerY + sin(angle) * speed * age + drawing.sparkHalfGravity * age * age
            val radius = drawing.sparkRadius * max(0f, 1 - age / life)
            val cool = seg(age, 0f, life)
            val mask = countLight(x, y)
            brush.reset(emberTone.spark, (1 - cool) * mask)
            canvas.drawCircle(x, y, radius, paint)
            brush.reset(emberTone.sparkCool, cool * mask)
            canvas.drawCircle(x, y, radius, paint)
        }
        canvas.restore()
    }
}
