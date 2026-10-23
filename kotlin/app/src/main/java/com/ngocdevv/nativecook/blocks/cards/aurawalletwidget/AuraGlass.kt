package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.cards.aurawalletwidget.AuraPalette.Tone

// The shell owns its shader and fixed uniform arrays. Its Compose draw phase
// reads the drive; it has no independent animation or continuous redraw loop.
internal class AuraGlass(private val width: Double, private val density: Float) {
    val uniforms = AuraUniforms()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = if (Build.VERSION.SDK_INT >= 33) makeShader() else null

    fun update(drive: AuraDrive) {
        auraMotionUniforms(width, drive.phase, drive.chosen, drive.formed.value,
            drive.leaving, drive.unformed.value, drive.pullX.value, drive.pullY.value,
            drive.clock.value, drive.fromX, drive.fromY, drive.wide.value, drive.nudge.value,
            0.0, drive.press[0].value, drive.press[1].value, drive.press[2].value,
            drive.press[3].value, drive.weight.value, drive.entered.value, uniforms)
    }

    @RequiresApi(33)
    fun draw(canvas: Canvas) {
        val material = checkNotNull(shader)
        for (index in uniforms.names.indices) {
            AuraShaderUniforms.set(material, uniforms.names[index], uniforms.arrays[index])
        }
        paint.shader = material
        canvas.save()
        canvas.scale(density, density)
        canvas.translate(-AuraPalette.bleed.toFloat(), -AuraPalette.bleed.toFloat())
        val side = (width + 2 * AuraPalette.bleed).toFloat()
        canvas.drawRect(0f, 0f, side, side, paint)
        canvas.restore()
    }

    @RequiresApi(33)
    private fun makeShader(): RuntimeShader {
        val material = RuntimeShader(auraShellAgsl)
        auraLightUniforms(material)
        val unit = (width / AuraPalette.design).toFloat()
        material.setFloatUniform("origin", AuraPalette.bleed.toFloat(), AuraPalette.bleed.toFloat())
        material.setFloatUniform("cardWidth", width.toFloat())
        material.setFloatUniform("pixelRatio", density)
        material.setFloatUniform("shellRadius", (AuraPalette.radius * unit).toFloat())
        material.setFloatUniform("bleed", AuraPalette.bleed.toFloat())
        material.setFloatUniform("shellClear", Tone.shellClear.toFloat())
        auraColorUniform(material, "shellTone", Tone.shell)
        auraColorUniform(material, "lightTone", Tone.ink)
        material.setFloatUniform("heroRect", 12 * unit, 12 * unit, 338 * unit, 156 * unit)
        material.setFloatUniform("heroRadii", 32 * unit, 40 * unit)
        material.setFloatUniform("lipShape", 164 * unit, 21 * unit)
        AuraShaderUniforms.set(material, "lipXs", FloatArray(5) { (auraSlotX(it) * unit).toFloat() })
        auraColorUniform(material, "heroCore", Tone.heroCore)
        auraColorUniform(material, "heroMid", Tone.heroMid)
        auraColorUniform(material, "heroLeft", Tone.heroLeft)
        auraColorUniform(material, "heroEdge", Tone.heroEdge)
        material.setFloatUniform("paneRect", 12 * unit, 201 * unit, 338 * unit, 80 * unit)
        material.setFloatUniform("paneRadius", 24 * unit)
        material.setFloatUniform("paneFill", 1f, 1f, 1f, 0.045f)
        material.setFloatUniform("paneLine", 1f, 1f, 1f, 0.16f)
        auraColorUniform(material, "pillowTop", Tone.pillowTop)
        auraColorUniform(material, "pillowMid", Tone.pillowMid)
        auraColorUniform(material, "pillowLow", Tone.pillowLow)
        auraColorUniform(material, "pillowShine", Tone.pillowShine)
        auraColorUniform(material, "accentTop", Tone.accentTop)
        auraColorUniform(material, "accentMid", Tone.accentMid)
        auraColorUniform(material, "accentLow", Tone.accentLow)
        auraColorUniform(material, "accentShine", Tone.accentShine)
        material.setFloatUniform("shadowSizes", 3 * unit, 7 * unit, 3 * unit, 3.5f * unit)
        material.setFloatUniform("surfaceSizes", 1.5f * unit, 2 * unit, 0.65f * unit, unit)
        return material
    }
}

@RequiresApi(33)
internal fun auraLightUniforms(shader: RuntimeShader) {
    auraColorUniform(shader, "pageTone", Tone.page)
    auraColorUniform(shader, "leftCore", Tone.lampLeftCore)
    auraColorUniform(shader, "leftEdge", Tone.lampLeftEdge)
    auraColorUniform(shader, "lowCore", Tone.lampLowCore)
    auraColorUniform(shader, "lowEdge", Tone.lampLowEdge)
    auraColorUniform(shader, "highTone", Tone.lampHigh)
}

@RequiresApi(33)
private fun auraColorUniform(shader: RuntimeShader, name: String, hex: String) {
    val color = Color.parseColor(hex)
    shader.setFloatUniform(name, Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
}
