package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxMotion as M
import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxTheme as Theme

/** What the screen draws besides the engine's state (the props of the RN component). */
internal class GelCubeUnboxParams(
    val sealed: GelCubeUnboxCopy,
    val opened: GelCubeUnboxCopy,
    val title: String,
    val subtitle: String,
    val showClose: Boolean,
    val shapeScale: Double,
    val sheetRadius: Double,
    /** false in the large-type flow, where the texts are real views */
    val drawText: Boolean,
)

/**
 * The picture: the material pass (the AGSL shader, or the gradient fallback below API 33) over the whole screen, then the texts of the RN screen drawn
 * on the same canvas (their colour, shadow and cross-fade follow the lid exactly as the RN animated styles do). Everything is in dp: the caller scales
 * the canvas by the density.
 */
internal class GelCubeUnboxRenderer(assets: AssetManager) {
    private val inter = Typeface.createFromAsset(assets, "gelcubeunbox/gelcubeunbox_Inter-Regular.ttf")
    private val uniforms = GelCubeUnboxUniforms()
    private var pass: Any? = null
    private val fallback by lazy { GelCubeUnboxFallback() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var shadowFilter: BlurMaskFilter? = null
    private var shadowRadius = -1f

    private fun shaderPass(): GelCubeUnboxShaderPass? {
        if (Build.VERSION.SDK_INT < 33) return null
        (pass as? GelCubeUnboxShaderPass)?.let { return it }
        return try {
            GelCubeUnboxShaderPass().also { pass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("GelCubeUnbox", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    }

    private fun lerp(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(x: Int, y: Int) = Math.round(x + (y - x) * k).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /** the uniforms of this frame, as RN's derived value computes them */
    private fun fill(l: Theme.Layout, e: GelCubeUnboxEngine, params: GelCubeUnboxParams) {
        val progress = e.progress
        val timeline = e.mode != 1
        val visual = if (e.reduce) 0.0 else progress
        val flags = (if (params.showClose) 1 else 0) + 2
        uniforms.frame[0] = l.ox.toFloat()
        uniforms.frame[1] = l.oy.toFloat()
        uniforms.frame[2] = l.s.toFloat()
        uniforms.frame[3] = flags.toFloat()
        uniforms.shape[0] = params.shapeScale.toFloat()
        uniforms.shape[1] = Theme.GLASS_FILL.toFloat()
        uniforms.shape[2] = Theme.GLASS_STROKE.toFloat()
        uniforms.shape[3] = params.sheetRadius.toFloat()
        uniforms.state[0] = visual.toFloat()
        uniforms.state[1] = (if (e.reduce) 1 + M.clamp01(progress) * 0.25 else if (timeline) M.coreAt(e.t) else e.manualCore.value).toFloat()
        uniforms.state[2] = (if (e.reduce) 0.0 else if (timeline) M.fanAt(e.t) else Math.max(0.0, visual - e.closeLead.value)).toFloat()
        uniforms.state[3] = (if (timeline) M.coreAt(e.t - M.CAUSTIC_LAG) else e.manualCore.value).toFloat()
        uniforms.optics[0] = (if (e.reduce) 0.0 else M.angleAt(progress)).toFloat()
        uniforms.optics[1] = (if (e.reduce) 0.0 else if (timeline) M.flashAt(e.t) else e.flash).toFloat()
        uniforms.optics[2] = 0f // the output stays sRGB on Android
        uniforms.optics[3] = Theme.GRAIN.toFloat()
    }

    fun draw(c: Canvas, w: Float, h: Float, l: Theme.Layout, e: GelCubeUnboxEngine, pal: Theme.Palette, params: GelCubeUnboxParams) {
        fill(l, e, params)
        val sp = shaderPass()
        if (sp != null && Build.VERSION.SDK_INT >= 33) {
            sp.draw(c, w, h, uniforms, pal)
        } else {
            fallback.draw(c, w, h, l.ox, l.oy, l.s, uniforms.frame[3].toInt(), uniforms, pal)
        }
        if (params.drawText) drawTexts(c, l, e, params)
    }

    // ---- texts ----

    private fun font(sizeDesign: Double, s: Double) {
        text.typeface = inter
        text.textSize = (sizeDesign * s).toFloat()
        // the RN style gives every text the display tracking in px: -0.5 x S, i.e. -0.5 / size in em
        text.letterSpacing = (Theme.DISPLAY_TRACK / sizeDesign).toFloat()
        text.maskFilter = null
    }

    /** greedy word wrap of one paragraph into lines no wider than `width` */
    private fun lines(str: String, width: Float): List<String> {
        val out = ArrayList<String>()
        for (para in str.split('\n')) {
            var cur = ""
            for (word in para.split(' ')) {
                val next = if (cur.isEmpty()) word else "$cur $word"
                if (cur.isNotEmpty() && text.measureText(next) > width) {
                    out.add(cur)
                    cur = word
                } else {
                    cur = next
                }
            }
            out.add(cur)
        }
        return out
    }

    /**
     * A centred block of text in a box: each line sits in a line box of `lineH` with the font's extra leading shared evenly above and below
     * (RN's line-height span), no font padding.
     */
    private fun block(c: Canvas, str: String, left: Float, top: Float, width: Float, lineH: Float, color: Int, alpha: Double, dy: Float = 0f) {
        if (alpha <= 0.002) return
        text.color = color
        text.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).toInt()
        val fm = text.fontMetrics
        val inset = (lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent
        for ((i, ln) in lines(str, width).withIndex()) {
            c.drawText(ln, left + (width - text.measureText(ln)) / 2, top + dy + i * lineH + inset, text)
        }
    }

    private fun drawTexts(c: Canvas, l: Theme.Layout, e: GelCubeUnboxEngine, params: GelCubeUnboxParams) {
        val s = l.s
        val reduce = e.reduce
        val p = e.p.value
        val closedA = 1 - (if (reduce) M.clamp01(p) else M.contentAt(e.progress))
        val openedA = if (reduce) M.clamp01(p) else M.contentAt(e.progress)
        val lit = if (reduce) 0.0 else p

        // the headline: a title that warms up as the light reaches it, with a soft shadow
        val box = Theme.TITLE_BOX
        font(Theme.DISPLAY_SIZE, s)
        val headline = M.lightAt(p, M.LIGHT_HEADLINE)
        val titleLeft = l.x(box.x).toFloat()
        val titleTop = l.y(box.y).toFloat()
        val titleW = (box.w * s).toFloat()
        val titleLine = (Theme.DISPLAY_LINE * s).toFloat()
        val shadowR = (Theme.TITLE_SHADOW_RADIUS * s).toFloat()
        if (shadowFilter == null || shadowRadius != shadowR) {
            shadowFilter = BlurMaskFilter(shadowR, BlurMaskFilter.Blur.NORMAL)
            shadowRadius = shadowR
        }
        val shadowColor = Color.argb(255, Theme.TITLE_SHADOW_RGB[0], Theme.TITLE_SHADOW_RGB[1], Theme.TITLE_SHADOW_RGB[2])
        text.maskFilter = shadowFilter
        block(c, params.title, titleLeft, titleTop, titleW, titleLine, shadowColor, Theme.TITLE_SHADOW_ALPHA * headline, (Theme.TITLE_SHADOW_Y * s).toFloat())
        text.maskFilter = null
        block(c, params.title, titleLeft, titleTop, titleW, titleLine, lerp(Theme.titleRest, Theme.ink, headline), 1.0)

        // the subtitle
        font(Theme.BODY_SIZE, s)
        val sub = Theme.SUBTITLE_BOX
        block(
            c, params.subtitle, l.x(sub.x).toFloat(), l.y(sub.y).toFloat(), (sub.w * s).toFloat(), (Theme.BODY_LINE * s).toFloat(),
            lerp(Theme.mute, Theme.ink, M.lightAt(p, 0.16)), 1.0,
        )

        // the action: its two labels cross-fade, the pill scales a little while it is pressed
        val pill = Theme.PILL
        val pcx = l.x(pill.x + pill.w / 2).toFloat()
        val pcy = l.y(pill.y + pill.h / 2).toFloat()
        font(Theme.ACTION_SIZE, s)
        val actionColor = lerp(Theme.ink, Theme.litInk, M.lightAt(lit, 0.28))
        val press = if (!reduce && e.pressed) Theme.PRESS.toFloat() else 1f
        c.save()
        c.scale(press, press, pcx, pcy)
        val fm = text.fontMetrics
        val base = pcy - (fm.descent + fm.ascent) / 2
        text.color = actionColor
        text.alpha = (closedA.coerceIn(0.0, 1.0) * 255).toInt()
        if (closedA > 0.002) c.drawText(params.sealed.action, pcx - text.measureText(params.sealed.action) / 2, base, text)
        text.color = actionColor
        text.alpha = (openedA.coerceIn(0.0, 1.0) * 255).toInt()
        if (openedA > 0.002) c.drawText(params.opened.action, pcx - text.measureText(params.opened.action) / 2, base, text)
        c.restore()

        // the sheet: chip, title and body, each a pair that cross-fades; their ink turns dark as the sheet's glass catches the light
        val chip = Theme.CHIP
        sheetText(
            c, l, params.sealed.chip, params.opened.chip, Theme.CHIP_SIZE, 20.0, l.x(chip.x + 12).toFloat(), l.y(chip.y + 7).toFloat(), ((chip.w - 12) * s).toFloat(),
            lerp(Theme.ink, Theme.litInk, M.lightAt(lit, M.LIGHT_CHIP)), closedA, openedA,
        )
        val st = Theme.SHEET_TITLE
        sheetText(
            c, l, params.sealed.sheetTitle, params.opened.sheetTitle, Theme.DISPLAY_SIZE, 32.0, l.x(st.x).toFloat(), l.y(st.y).toFloat(), (st.w * s).toFloat(),
            lerp(Theme.ink, Theme.litInk, M.lightAt(lit, M.LIGHT_SHEET_TITLE)), closedA, openedA,
        )
        val sb = Theme.SHEET_BODY
        sheetText(
            c, l, params.sealed.sheetBody, params.opened.sheetBody, Theme.BODY_SIZE, 23.0, l.x(sb.x).toFloat(), l.y(sb.y).toFloat(), (sb.w * s).toFloat(),
            lerp(Theme.mute, Theme.litMute, M.lightAt(lit, M.LIGHT_SHEET_BODY)), closedA, openedA,
        )
    }

    private fun sheetText(
        c: Canvas, l: Theme.Layout, closed: String?, opened: String?, size: Double, lineDesign: Double, left: Float, top: Float, width: Float,
        color: Int, closedA: Double, openedA: Double,
    ) {
        font(size, l.s)
        val lineH = (lineDesign * l.s).toFloat()
        if (closed != null) block(c, closed, left, top, width, lineH, color, closedA)
        if (opened != null) block(c, opened, left, top, width, lineH, color, openedA)
    }
}
