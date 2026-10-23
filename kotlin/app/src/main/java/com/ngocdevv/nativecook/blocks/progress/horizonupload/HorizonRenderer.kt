// Draw-phase-only consumer: scene first, translated card content second, fixed footer last.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

internal class HorizonRenderer(assets: HorizonAssets, private val g: HorizonGeometry, strings: HorizonStrings) {
    private val scene = if (Build.VERSION.SDK_INT >= 33) HorizonScene(g) else null
    private val plain = if (Build.VERSION.SDK_INT < 33) HorizonPlainScene(g) else null
    private val file = HorizonFile(assets)
    private val panel = HorizonPanel(assets, g, strings)
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // The approved filename uses the platform face, not the bundled label font.
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 18 * g.nameScale; color = 0xFFF4F7FF.toInt(); letterSpacing = -.4f / 18
    }
    private val nameBaseline = 68 + (24 * g.nameScale - namePaint.descent() - namePaint.ascent()) / 2
    private val name = TextUtils.ellipsize(strings.name, namePaint, g.cardWidth - 24 - g.copyLeft, TextUtils.TruncateAt.MIDDLE).toString()
    private val sizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF; textSize = 12 * g.nameScale; color = 0xFF8E97AE.toInt(); fontFeatureSettings = "tnum"
    }
    private val size = strings.size
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * g.nameScale; color = 0x80D6E0FF.toInt() }
    private val hint = StaticLayout.Builder.obtain(strings.hint, 0, strings.hint.length, hintPaint, (g.width - 48).toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0f, 18f / 13).build()
    fun draw(canvas: Canvas, state: HorizonState) {
        canvas.save(); canvas.scale(g.density, g.density)
        if (Build.VERSION.SDK_INT >= 33 && scene != null) scene.draw(canvas, state) else plain?.draw(canvas, state)
        canvas.save(); canvas.translate(g.x + state.shiftX.value, g.y + state.shiftY.value - 46 * state.lift)
        canvas.scale(state.scale, state.scale, g.cardWidth / 2, 120f)
        file.draw(canvas, state, g)
        canvas.drawText(name, g.copyLeft, nameBaseline, namePaint)
        val sizeTop = 68 + 24 * g.nameScale + 2
        canvas.drawText(size, g.copyLeft, sizeTop + (16 * g.nameScale - sizePaint.descent() - sizePaint.ascent()) / 2, sizePaint)
        panel.draw(canvas, state)
        canvas.restore()
        canvas.save(); canvas.translate(24f, g.height - g.bottom - 28 - hint.height)
        hint.draw(canvas); canvas.restore(); canvas.restore()
    }
}
