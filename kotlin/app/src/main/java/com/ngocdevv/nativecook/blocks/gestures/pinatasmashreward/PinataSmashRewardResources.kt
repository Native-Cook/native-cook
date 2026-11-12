package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardGeo
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardPoint
import org.json.JSONObject

/** Original RGBA artwork, JSON registration and bundled Onest, decoded once off the main thread. */
internal class PinataSmashRewardResources private constructor(context: Context) {
    data class PinataSmashRewardBulb(val x: Double, val y: Double, val r: Double, val color: Int)
    data class PinataSmashRewardSprite(val rect: Rect, val cx: Double, val cy: Double, val scale: Double)
    data class PinataSmashRewardView(val s: Double, val dy: Double, val top: Double, val w: Double, val h: Double)
    val k = 1 / (0.835 * (1440.0 / 402))
    val ox = 35 * k
    val oy = 170 + 56 + 53 * k
    private val assets = context.assets
    private val root = "pinatasmashreward/pinatasmashreward_"
    private val glyphRuns = mutableMapOf<Pair<Paint, String>, DoubleArray>()
    private fun json(name: String) = assets.open(root + name).bufferedReader().use { JSONObject(it.readText()) }
    private fun point(o: JSONObject) = PinataSmashRewardPoint(o.getDouble("x"), o.getDouble("y"))
    private val layout = json("layout.json")
    private val figure = layout.getJSONObject("figure_box")
    val attach = point(layout.getJSONObject("ribbon_attach_point"))
    val flank = point(layout.getJSONObject("flank_center"))
    val tailRoot = point(layout.getJSONObject("tail_root"))
    val cx = ox + (figure.getDouble("x") + figure.getDouble("w") / 2) * k
    val cy = oy + (figure.getDouble("y") + figure.getDouble("h") * 0.55) * k
    val rx = figure.getDouble("w") / 2 * k
    val ry = figure.getDouble("h") / 2 * k
    val bodyGrid = PinataSmashRewardMesh.PinataSmashRewardGrid(figure.getDouble("x") - 14, figure.getDouble("y") - 14, figure.getDouble("x") + figure.getDouble("w") + 14, figure.getDouble("y") + figure.getDouble("h") + 14)
    val tailGrid: PinataSmashRewardMesh.PinataSmashRewardGrid
    val bulbs: List<PinataSmashRewardBulb>
    val sprites: List<PinataSmashRewardSprite>
    val body = image("pinata-body.png")
    val burst = image("pinata-body-burst.png")
    val crack1 = image("pinata-crack-1.png")
    val crack2 = image("pinata-crack-2.png")
    val tail = image("pinata-tail.png")
    val atlas = image("loot-atlas.png")
    val bodyShader = shader(body)
    val burstShader = shader(burst)
    val crack1Shader = shader(crack1)
    val crack2Shader = shader(crack2)
    val tailShader = shader(tail)
    private fun textPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply { fontFeatureSettings = "'kern' 0, 'liga' 0" }
    val label = textPaint().apply { typeface = Typeface.createFromAsset(assets, root + "Onest-Medium.ttf"); textSize = 15.5f }
    val title = textPaint().apply { typeface = Typeface.createFromAsset(assets, root + "Onest-SemiBold.ttf"); textSize = 20f }
    val chipFont = textPaint().apply { typeface = title.typeface; textSize = 15f }
    private fun advances(font: Paint, text: String) = glyphRuns.getOrPut(font to text) { DoubleArray(text.length) { font.measureText(text, it, it + 1).toDouble() } }
    private fun textWidth(font: Paint, text: String) = advances(font, text).sum()
    val labelTextW = textWidth(label, "Keep tapping")
    val doneTextW = textWidth(label, "Come back tomorrow")
    val chipW = textWidth(chipFont, "000")
    val chipWidth = 8 + 22 + 7 + chipW + 14
    val chipX0 = 402 - 24 - chipWidth
    init {
        val box = layout.getJSONObject("tail_box")
        tailGrid = PinataSmashRewardMesh.PinataSmashRewardGrid(kotlin.math.max(0.0, box.getDouble("x") - 40), kotlin.math.max(0.0, box.getDouble("y") - 40), kotlin.math.min(1080.0, box.getDouble("x") + box.getDouble("w") + 40), kotlin.math.min(1140.0, box.getDouble("y") + box.getDouble("h") + 40))
        val bulbData = layout.getJSONArray("bulbs")
        bulbs = List(bulbData.length()) { i -> val b = bulbData.getJSONObject(i); val p = b.getJSONObject("center"); PinataSmashRewardBulb(p.getDouble("x"), p.getDouble("y"), b.getDouble("radius"), Color.parseColor(b.getString("color"))) }
        val atlasData = json("loot-atlas.json")
        sprites = PinataSmashRewardMotion.ATLAS_NAMES.mapIndexed { i, name ->
            val r = atlasData.getJSONObject(name); val x = r.getInt("x"); val y = r.getInt("y"); val w = r.getInt("w"); val h = r.getInt("h")
            PinataSmashRewardSprite(Rect(x, y, x + w, y + h), r.getDouble("cx"), r.getDouble("cy"), PinataSmashRewardMotion.KIND_W[i] / w)
        }
    }
    private fun image(name: String): Bitmap = assets.open(root + name).use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888; inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) })) { "Piñata asset $name could not be decoded" }.apply { setHasMipMap(true) }
    }
    private fun shader(image: Bitmap): BitmapShader {
        val tile = if (Build.VERSION.SDK_INT >= 31) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
        return BitmapShader(image, tile, tile).apply { if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR }
    }
    fun geo(view: PinataSmashRewardView) = PinataSmashRewardGeo(
        chipX0 + 8 + 11, 58 + 18 + view.top - view.dy,
        bulbs.map { PinataSmashRewardPoint(ox + it.x * k, oy + it.y * k) },
        PinataSmashRewardPoint(ox + flank.x * k, oy + flank.y * k),
        PinataSmashRewardPoint(cx, cy), PinataSmashRewardPoint(ox + attach.x * k, oy + attach.y * k),
    )
    /** RN SkFont draws unshaped glyph IDs; use raw per-glyph advances instead of Minikin pairs. */
    fun drawText(canvas: Canvas, text: String, x: Double, y: Double, font: Paint) {
        val widths = advances(font, text); var at = x
        for (i in text.indices) { canvas.drawText(text, i, i + 1, at.toFloat(), y.toFloat(), font); at += widths[i] }
    }
    companion object {
        fun load(context: Context) = PinataSmashRewardResources(context.applicationContext)
    }
}
