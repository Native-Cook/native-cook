package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceMotion as M
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceTheme as Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * The screen in one canvas, in the RN order: the ribbon strip (the particle shader, or the line fallback), then the text block (the greeting and line 2's
 * layers: the question, "I'm listening", the three thread dots and the reply's words, each cross-fading with a rise), the request as it is heard and the
 * talk control with its hint. Everything is in dp: the caller scales the canvas by the density.
 */
internal class ParticleRibbonVoiceRenderer(private val assets: AssetManager) {
    private fun face(name: String) = Typeface.createFromAsset(assets, "particleribbonvoice/particleribbonvoice_$name.ttf")
    private val extraLight = face("InterTight-ExtraLight")
    private val light = face("InterTight-Light")
    private var pass: Any? = null
    private val fallback by lazy { ParticleRibbonVoiceFallback() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val mic: Path = try {
        PathParser.createPathFromPathData("M12 3.2a3 3 0 0 0-3 3v5.6a3 3 0 0 0 6 0V6.2a3 3 0 0 0-3-3Z M5.8 11.2a6.2 6.2 0 0 0 12.4 0 M12 17.4v3.4")
    } catch (error: RuntimeException) {
        Path()
    }
    private val ramp = arrayOf(Theme.rgbOf(Theme.gold), Theme.rgbOf(Theme.green), Theme.rgbOf(Theme.cyan))

    private fun shaderPass(): ParticleRibbonVoiceShaderPass? {
        if (Build.VERSION.SDK_INT < 33) return null
        (pass as? ParticleRibbonVoiceShaderPass)?.let { return it }
        return try {
            ParticleRibbonVoiceShaderPass().also { pass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("ParticleRibbonVoice", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    }

    private fun font(tf: Typeface, size: Double, track: Double, color: Int, alpha: Double) {
        text.typeface = tf
        text.textSize = size.toFloat()
        text.letterSpacing = track.toFloat()
        text.color = color
        text.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).toInt()
    }

    /** the baseline of a line centred in a box of `lineH` (RN's line height span: the extra leading is shared above and below) */
    private fun baseline(top: Double, lineH: Double): Float {
        val fm = text.fontMetrics
        return (top + (lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent).toFloat()
    }

    private fun a(c: Int, alpha: Double) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    /** `show` is the 320 ms fade-in of the text; `insetBottom` the bottom inset in dp. */
    fun draw(c: Canvas, w: Float, h: Float, l: Theme.Layout, e: ParticleRibbonVoiceEngine, show: Double, insetBottom: Double) {
        val s = l.s
        fill.color = Theme.page
        c.drawRect(0f, 0f, w, h, fill)
        // the ribbon strip
        c.save()
        c.translate(0f, l.stripTop.toFloat())
        c.clipRect(0f, 0f, w, l.stripH.toFloat())
        val sp = shaderPass()
        if (sp != null && Build.VERSION.SDK_INT >= 33) sp.draw(c, w, l.stripH.toFloat(), s.toFloat(), e.frame) else fallback.draw(c, w, s.toFloat(), e.frame)
        c.restore()
        if (show <= 0.002) return
        val cap = e.cap
        val fs = Theme.BODY_SIZE * s
        val lh = Theme.BODY_LINE * s
        val cx = (w / 2)
        // the greeting, over the line that follows the state
        font(extraLight, Theme.GREETING_SIZE * s, Theme.GREETING_TRACK, Theme.greeting, show)
        val line1Top = l.textBottom - 2 * lh
        val line2Top = l.textBottom - lh
        c.drawText(Theme.GREETING, cx - text.measureText(Theme.GREETING) / 2, baseline(line1Top, Theme.GREETING_LINE * s), text)
        fun body(str: String, alpha: Double, rise: Double) {
            if (alpha <= 0.002) return
            font(light, fs, Theme.BODY_TRACK, Theme.ink, alpha * show)
            c.drawText(str, cx - text.measureText(str) / 2, baseline(line2Top + (1 - alpha) * rise, lh), text)
        }
        body(Theme.IDLE, cap[0], 6 * s)
        body(Theme.LISTENING, cap[1], 6 * s)
        // the thread's three dots, lit one by one at each reversal
        val dotD = 6.4 * s
        for (i in 0 until 3) {
            val v = cap[2 + i]
            if (v <= 0.002) continue
            fill.color = a(Theme.ink, v * show)
            val dx = cx + ((i - 1) * (dotD + 12.8 * s)).toFloat()
            c.drawCircle(dx, (line2Top + lh / 2 + s).toFloat(), (dotD / 2).toFloat(), fill)
        }
        // the reply, one word at a time: each takes the ribbon's colour beneath it for 240 ms
        font(light, fs, Theme.BODY_TRACK, Theme.ink, 1.0)
        val gap = 0.24 * fs
        val widths = DoubleArray(Theme.REPLY.size) { text.measureText(Theme.REPLY[it]).toDouble() }
        val rowW = widths.sum() + gap * (widths.size - 1)
        var wx = 16 + ((w - 32) - rowW) / 2
        for ((i, word) in Theme.REPLY.withIndex()) {
            e.wordX[i] = (wx + widths[i] / 2) / s
            val v = cap[5 + i]
            if (v > 0.002) {
                val k = M.TINT * cap[5 + M.MAX_WORDS + i]
                val rc = M.rampMix(ramp, e.wordX[i] / Theme.REF_W)
                val ink = Theme.rgbOf(Theme.ink)
                val col = Color.rgb(
                    Math.round(255 * (ink[0] + (rc[0] - ink[0]) * k)).toInt(), Math.round(255 * (ink[1] + (rc[1] - ink[1]) * k)).toInt(),
                    Math.round(255 * (ink[2] + (rc[2] - ink[2]) * k)).toInt(),
                )
                font(light, fs, Theme.BODY_TRACK, col, v * show)
                c.drawText(word, wx.toFloat(), baseline(line2Top, lh), text)
            }
            wx += widths[i] + gap
        }
        // the request as it is heard: a word arrives with each syllable, rising 4 pt into place
        val ask = e.ask
        val askSize = Theme.ASK_SIZE * s
        val askLine = Theme.ASK_LINE * s
        font(light, askSize, Theme.ASK_TRACK, Theme.greeting, 1.0)
        val askGap = 0.26 * askSize
        val askW = DoubleArray(Theme.ASK.size) { text.measureText(Theme.ASK[it]).toDouble() }
        // wrap into centred rows within the margins
        var row = 0
        var i = 0
        val top = l.yc(0.0) + Theme.ASK_Y * s
        while (i < askW.size) {
            var j = i
            var rw = 0.0
            while (j < askW.size && (j == i || rw + askGap + askW[j] <= w - 32)) {
                rw += (if (j == i) 0.0 else askGap) + askW[j]
                j++
            }
            var x = 16 + ((w - 32) - rw) / 2
            for (k in i until j) {
                val v = ask[k]
                if (v > 0.002) {
                    font(light, askSize, Theme.ASK_TRACK, Theme.greeting, v * show)
                    c.drawText(Theme.ASK[k], x.toFloat(), baseline(top + row * askLine + (1 - v) * 4, askLine), text)
                }
                x += askW[k] + askGap
            }
            row++
            i = j
        }
        // the talk control: a hairline ring with a microphone; held it fills with the ink and a soft halo breathes round it
        val press = ask[M.MAX_WORDS]
        val d = Theme.CONTROL_D * s
        val hintSize = Theme.CONTROL_HINT * s
        val hintLine = hintSize * 1.3
        val dockBottom = h - (Math.max(insetBottom, 12.0) + Theme.CONTROL_BOTTOM * s)
        val ringCy = dockBottom - hintLine - Theme.CONTROL_GAP - d / 2
        val ringCx = cx
        c.save()
        val bodyScale = (1 - 0.05 * press).toFloat()
        c.scale(bodyScale, bodyScale, ringCx, ringCy.toFloat())
        stroke.shader = null
        stroke.strokeWidth = 0.7f
        if (press > 0.002) {
            val halo = (1.16 + 0.05 * sin((2 * PI * e.clock) / 1.1) * press).toFloat()
            stroke.color = a(Theme.ink, 0.45 * press * show)
            c.drawCircle(ringCx, ringCy.toFloat(), (d / 2).toFloat() * halo, stroke)
        }
        stroke.color = a(Theme.ink, Theme.CONTROL_RING * show)
        c.drawCircle(ringCx, ringCy.toFloat(), (d / 2).toFloat(), stroke)
        if (press > 0.002) {
            fill.color = a(Theme.ink, press * show)
            c.drawCircle(ringCx, ringCy.toFloat(), (d / 2).toFloat(), fill)
        }
        // the microphone: ink on the ring, the page colour on the filled one
        c.save()
        val gs = (d * 0.4 / 24).toFloat()
        c.translate(ringCx - (d * 0.4 / 2).toFloat(), ringCy.toFloat() - (d * 0.4 / 2).toFloat())
        c.scale(gs, gs)
        stroke.strokeWidth = 1.4f
        stroke.color = a(Theme.ink, (1 - press) * show)
        c.drawPath(mic, stroke)
        stroke.color = a(Theme.page, press * show)
        c.drawPath(mic, stroke)
        c.restore()
        c.restore()
        // the hint under it
        font(light, hintSize, 0.0, Theme.greeting, (1 - press) * show)
        val hintTop = ringCy + d / 2 + Theme.CONTROL_GAP
        c.drawText(Theme.HOLD, cx - text.measureText(Theme.HOLD) / 2, baseline(hintTop, hintLine), text)
        font(light, hintSize, 0.0, Theme.greeting, press * show)
        c.drawText(Theme.RELEASE, cx - text.measureText(Theme.RELEASE) / 2, baseline(hintTop, hintLine), text)
    }
}
