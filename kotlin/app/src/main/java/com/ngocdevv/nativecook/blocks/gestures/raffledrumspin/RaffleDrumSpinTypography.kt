package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import org.json.JSONObject

/** Immutable outlines and advances cached from the exact bundled OFL faces by reference Skia. */
internal class RaffleDrumSpinTypography(context: Context, private val faces: Map<Typeface, String>) {
    private data class Glyph(val advance: Float, val path: Path)
    private data class LayoutKey(val face: Typeface, val size: Float, val text: String)
    private val layouts = HashMap<LayoutKey, Path>()
    private val glyphs = HashMap<Pair<String, Char>, Glyph>()
    private val data = context.assets.open("raffledrumspin/raffledrumspin_font-outlines.json").bufferedReader().use { JSONObject(it.readText()) }
    private val canonical = data.getDouble("canonicalSize").toFloat()
    private val savedShaderMatrix = Matrix()
    private val shiftedShaderMatrix = Matrix()
    private val glyphMatrix = Matrix()

    private fun glyph(paint: Paint, char: Char): Glyph {
        val name = faces[paint.typeface] ?: error("Unregistered Raffle Drum Spin font")
        return glyphs.getOrPut(name to char) {
            val item = data.getJSONObject("fonts").getJSONObject(name).getJSONObject(char.code.toString())
            val outline = item.getString("path")
            Glyph(item.getDouble("advance").toFloat(), if (outline.isEmpty()) Path() else requireNotNull(PathParser.createPathFromPathData(outline)))
        }
    }

    fun advance(paint: Paint, text: String): Float {
        val scale = paint.textSize / canonical
        var width = 0f
        for (char in text) width += glyph(paint, char).advance * scale
        return width
    }

    fun draw(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        val key = LayoutKey(paint.typeface ?: Typeface.DEFAULT, paint.textSize, text)
        val outline = layouts.getOrPut(key) {
            val result = Path()
            val scale = paint.textSize / canonical
            var offset = 0f
            for (char in text) {
                val glyph = glyph(paint, char)
                glyphMatrix.setScale(scale, scale)
                glyphMatrix.postTranslate(offset, 0f)
                result.addPath(glyph.path, glyphMatrix)
                offset += glyph.advance * scale
            }
            result
        }
        val shader = paint.shader
        if (shader != null) {
            shader.getLocalMatrix(savedShaderMatrix)
            shiftedShaderMatrix.set(savedShaderMatrix)
            shiftedShaderMatrix.postTranslate(-x, -y)
            shader.setLocalMatrix(shiftedShaderMatrix)
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.drawPath(outline, paint)
        canvas.restore()
        if (shader != null) shader.setLocalMatrix(savedShaderMatrix)
    }
}
