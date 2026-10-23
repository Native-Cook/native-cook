package com.ngocdevv.nativecook.blocks.playground.neonsignflicker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The colours of the gas, per tone (rose, ice cyan, amber, lime): the bright core of a lit tube,
// its white-hot centre, the colour of its halo, and the coated glass when it is dark.
private val Core = intArrayOf(0xff6fae, 0x45dcff, 0xffc061, 0xd4ff70)
private val Hot = intArrayOf(0xffe6f1, 0xc9f6ff, 0xfff3da, 0xf7ffe2)
// Cyan reads far brighter than rose at the same strength: each tone's halo is scaled to match.
private val HaloGain = floatArrayOf(1f, 0.5f, 0.85f, 0.9f)
private val Halo = intArrayOf(0xff2d7c, 0x14c4ff, 0xff8a1c, 0x8cff26)
private val Unlit = intArrayOf(0x4a3440, 0x2f4650, 0x4d4234, 0x3f4a32)
// The light each word throws on the wall (rose, cyan, amber), linear 0…1.
private val Spill = arrayOf(floatArrayOf(1f, 0.2f, 0.48f), floatArrayOf(0.1f, 0.62f, 1f), floatArrayOf(1f, 0.5f, 0.14f))

private val Backdrop = Color(0xFF0C0908)
private val HintColor = Color(0xFF7D6E66)

// The tube: glass diameter, lit core, white-hot centre (dp).
private const val TUBE = 6f
private const val CORE_W = 4.4f
private const val HOT_W = 1.7f
// The blacked-out glass at a tube's electrode end (dp).
private const val CAP_LENGTH = 3.6
// Halo layers baked around each tube: stroke width and blur sigma (dp), and strength.
private val HaloLayers = arrayOf(floatArrayOf(26f, 15f, 0.3f), floatArrayOf(12f, 5.5f, 0.55f), floatArrayOf(6f, 2f, 0.85f))
private const val HALO_PAD = 34f
private const val HALO_SCALE = 2f
// The light on the wall is baked at this many pixels per dp, this far around the sign (dp).
private const val SPILL_SCALE = 0.2f
private const val SPILL_MARGIN = 300f

// The clear acrylic panel the tubes are mounted on, around the lettering (dp).
private const val PANEL_PAD_X = 30.0
private const val PANEL_PAD_Y = 30.0
private const val PANEL_RADIUS = 14f
// The two chains meet at a hook HANG dp above the panel; they hold it CHAIN_INSET from its corners.
private const val HANG = 96f
private const val CHAIN_INSET = 20f
private const val LINK = 6.4f
// The panel's centre, as a share of the screen's height.
private const val CENTER_AT = 0.45f
// The swing a finger can give before it resists (rad), and how hard it resists past that.
private const val MAX_SWING = 0.5
private const val RUBBER = 0.35
private const val MAX_SPIN = 2.0
// After a touch the loop waits this long (s), then starts over.
private const val USER_HOLD = 4.0
// The tube that is allowed to stutter: the "t" of "late".
private const val FAULTY_WORD = 1
private const val FAULTY_ORDER = 2
// A pan starts after this much travel (dp); a tap is a touch lifted within this long (ms).
private const val PAN_SLOP = 4f
private const val TAP_TIME = 350L

/**
 * The wall, the same shader as the React Native block's: brick albedo lit by a faint cold street
 * light from above and by the sign's own glow. The glow comes from a baked image holding each
 * word's light in one channel (red = "open", green = "late", blue = the glass) in the sign's
 * frame, so the light swings with the sign.
 */
private const val WALL_AGSL = """
uniform shader wall;
uniform shader spill;
uniform float2 pivotW;
uniform float2 pivotL;
uniform float2 rot;
uniform float3 level;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float2 size;

half4 main(float2 p) {
  half3 albedo = wall.eval(p).rgb;
  float2 d = p - pivotW;
  float2 q = pivotL + float2(rot.x * d.x + rot.y * d.y, -rot.y * d.x + rot.x * d.y);
  half3 s = spill.eval(q).rgb;
  float3 light = s.r * level.x * c0 + s.g * level.y * c1 + s.b * level.z * c2;
  float v = p.y / size.y;
  float2 c = (p - 0.5 * size) / size;
  float ambient = (0.07 + 0.11 * (1.0 - v)) * (1.0 - 0.9 * dot(c, c));
  float3 col = float3(albedo) * (ambient * float3(0.78, 0.86, 1.0) + light * 2.4) + light * 0.035;
  col = col / (1.0 + 0.25 * col);
  return half4(half3(col), 1.0);
}
"""

/** The sign's geometry: the tubes as paths, their electrode ends, the mounts and the panel (dp). */
private class NeonScene {
    val pieces: List<NeonPiece>
    val paths: List<List<Path>>
    val caps = Path()
    val mounts = ArrayList<FloatArray>()
    val panel: RectF
    val faulty: Int

    init {
        val sign = NeonSignTubes.buildSign()
        pieces = sign.pieces
        paths = pieces.map { p -> p.tubes.map(::pathOf) }
        panel = RectF(
            (-sign.width / 2 - PANEL_PAD_X).toFloat(),
            (-sign.height / 2 - PANEL_PAD_Y).toFloat(),
            (sign.width / 2 + PANEL_PAD_X).toFloat(),
            (sign.height / 2 + PANEL_PAD_Y).toFloat(),
        )
        // Where a tube passes through the panel the glass is painted black (the electrode ends),
        // and each piece sits on one clear standoff.
        for (piece in pieces) {
            val t = piece.tubes[0]
            val n = t.size
            val ends = ArrayList<DoubleArray>()
            if (piece.endStart) ends.add(doubleArrayOf(t[0], t[1], t[2] - t[0], t[3] - t[1]))
            if (piece.endEnd) ends.add(doubleArrayOf(t[n - 2], t[n - 1], t[n - 4] - t[n - 2], t[n - 3] - t[n - 1]))
            for (e in ends) {
                val h = hypot(e[2], e[3])
                val len = if (h == 0.0) 1.0 else h
                caps.moveTo(e[0].toFloat(), e[1].toFloat())
                caps.lineTo((e[0] + e[2] / len * CAP_LENGTH).toFloat(), (e[1] + e[3] / len * CAP_LENGTH).toFloat())
            }
            val mid = (t.size / 16) * 8
            mounts.add(floatArrayOf(t[mid].toFloat(), t[mid + 1].toFloat()))
        }
        faulty = pieces.indexOfFirst { it.word == FAULTY_WORD && it.order == FAULTY_ORDER }
    }

    private fun pathOf(seg: DoubleArray): Path {
        val p = Path()
        p.moveTo(seg[0].toFloat(), seg[1].toFloat())
        var i = 0
        while (i < seg.size) {
            p.cubicTo(seg[i + 2].toFloat(), seg[i + 3].toFloat(), seg[i + 4].toFloat(), seg[i + 5].toFloat(), seg[i + 6].toFloat(), seg[i + 7].toFloat())
            i += 8
        }
        return p
    }
}

/** Every image the sign is drawn with, rendered once on a background thread. */
private class NeonBakes(
    val glows: List<Bitmap>,
    val glowRects: List<RectF>,
    val spill: Bitmap,
    val spillX: Float,
    val spillY: Float,
    val wall: Bitmap,
    val wallScale: Float,
    /** The wall under the street light alone, for Android 12 and older (no runtime shader). */
    val calmWall: Bitmap?,
    val shadow: Bitmap,
    val shadowRect: RectF,
    val plateShadow: Bitmap,
)

/** Seeded random in [0, 1): mulberry32, with JavaScript's 32-bit wrapping maths. */
private class NeonRandom(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

private fun rgb(hex: Int, alpha: Float = 1f) = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), (hex shr 16) and 0xff, (hex shr 8) and 0xff, hex and 0xff)

private fun rgbF(r: Float, g: Float, b: Float, a: Float = 1f) = android.graphics.Color.argb(
    (a.coerceIn(0f, 1f) * 255).roundToInt(),
    (r.coerceIn(0f, 1f) * 255).roundToInt(),
    (g.coerceIn(0f, 1f) * 255).roundToInt(),
    (b.coerceIn(0f, 1f) * 255).roundToInt(),
)

/** Skia's blur sigma as the radius Android's blur mask filter takes. */
private fun blurRadius(sigma: Float) = max(0.1f, (sigma - 0.5f) / 0.57735f)

private fun strokePaint(color: Int, width: Float, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    this.color = color
    if (blur > 0f) maskFilter = BlurMaskFilter(blurRadius(blur), BlurMaskFilter.Blur.NORMAL)
}

private fun fillPaint(color: Int, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    if (blur > 0f) maskFilter = BlurMaskFilter(blurRadius(blur), BlurMaskFilter.Blur.NORMAL)
}

private val AddMode = PorterDuffXfermode(PorterDuff.Mode.ADD)

/** One piece's halo at full brightness, in its own small image. */
private fun bakeGlow(piece: NeonPiece, paths: List<Path>): Pair<Bitmap, RectF> {
    val bx = piece.box[0].toFloat()
    val by = piece.box[1].toFloat()
    val w = piece.box[2].toFloat() + 2 * HALO_PAD
    val h = piece.box[3].toFloat() + 2 * HALO_PAD
    val bitmap = Bitmap.createBitmap(ceil(w * HALO_SCALE).toInt(), ceil(h * HALO_SCALE).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(HALO_SCALE, HALO_SCALE)
    c.translate(HALO_PAD - bx, HALO_PAD - by)
    for (k in HaloLayers.indices) {
        val (width, blur, alpha) = HaloLayers[k]
        val color = if (k == HaloLayers.size - 1) Core[piece.tone] else Halo[piece.tone]
        val p = strokePaint(rgb(color, alpha * HaloGain[piece.tone]), width, blur)
        p.xfermode = AddMode
        for (path in paths) c.drawPath(path, p)
    }
    return bitmap to RectF(bx - HALO_PAD, by - HALO_PAD, bx - HALO_PAD + w, by - HALO_PAD + h)
}

/** The light the sign throws on the wall, one word per channel, in the sign's frame. */
private fun bakeSpill(scene: NeonScene, box: RectF): Bitmap {
    val x0 = box.left - SPILL_MARGIN
    val y0 = box.top - SPILL_MARGIN
    val w = box.width() + 2 * SPILL_MARGIN
    val h = box.height() + 2 * SPILL_MARGIN
    val bitmap = Bitmap.createBitmap(ceil(w * SPILL_SCALE).toInt(), ceil(h * SPILL_SCALE).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.drawColor(android.graphics.Color.BLACK)
    c.scale(SPILL_SCALE, SPILL_SCALE)
    c.translate(-x0, -y0)
    val channel = intArrayOf(0xff0000, 0x00ff00, 0x0000ff)
    for (word in 0 until 3) {
        val near = strokePaint(rgb(channel[word], 0.55f), 18f, 20f).apply { xfermode = AddMode }
        val far = strokePaint(rgb(channel[word], 0.5f), 34f, 52f).apply { xfermode = AddMode }
        var wx0 = Float.POSITIVE_INFINITY
        var wy0 = Float.POSITIVE_INFINITY
        var wx1 = Float.NEGATIVE_INFINITY
        var wy1 = Float.NEGATIVE_INFINITY
        scene.pieces.forEachIndexed { i, piece ->
            if (piece.word != word) return@forEachIndexed
            for (path in scene.paths[i]) {
                c.drawPath(path, far)
                c.drawPath(path, near)
            }
            wx0 = min(wx0, piece.box[0].toFloat())
            wy0 = min(wy0, piece.box[1].toFloat())
            wx1 = max(wx1, (piece.box[0] + piece.box[2]).toFloat())
            wy1 = max(wy1, (piece.box[1] + piece.box[3]).toFloat())
        }
        // A broad wash that reaches across the wall.
        val cx = (wx0 + wx1) / 2
        val cy = (wy0 + wy1) / 2
        val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = AddMode
            shader = RadialGradient(cx, cy, 260f, rgb(channel[word]), android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
            alpha = (0.3f * 255).roundToInt()
        }
        c.drawCircle(cx, cy, 260f, wash)
    }
    return bitmap
}

/** A rounded rectangle's soft black shadow, padded by three blur sigmas, at 2 px per dp. */
private fun bakeSoftRect(w: Float, h: Float, radius: Float, alpha: Float, blur: Float): Bitmap {
    val pad = 3 * blur
    val bitmap = Bitmap.createBitmap(ceil((w + 2 * pad) * 2).toInt(), ceil((h + 2 * pad) * 2).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(2f, 2f)
    c.drawRoundRect(RectF(pad, pad, pad + w, pad + h), radius, radius, fillPaint(rgb(0x000000, alpha), blur))
    return bitmap
}

/** The brick wall's own colour, without light: running bond, each brick a little different. */
private fun bakeWall(width: Float, height: Float, scale: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(ceil(width * scale).toInt(), ceil(height * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bitmap)
    c.scale(scale, scale)
    val rand = NeonRandom(7121)
    fun color(v: DoubleArray, alpha: Float = 1f) = android.graphics.Color.argb((alpha * 255).roundToInt(), v[0].roundToInt(), v[1].roundToInt(), v[2].roundToInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawColor(rgb(0x7a7166))
    // Grit in the mortar.
    for (i in 0 until 1600) {
        val light = rand.next() > 0.5
        val x = rand.next() * width
        val y = rand.next() * height
        val r = 0.3 + rand.next() * 0.6
        paint.color = rgb(if (light) 0xa49a8c else 0x4a443d, 0.5f)
        c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), paint)
    }
    val row = 25.0
    val mortar = 3.4
    val brick = 62.0
    val tones = arrayOf(
        doubleArrayOf(150.0, 70.0, 50.0), doubleArrayOf(134.0, 60.0, 44.0), doubleArrayOf(162.0, 84.0, 60.0),
        doubleArrayOf(120.0, 56.0, 44.0), doubleArrayOf(100.0, 48.0, 40.0), doubleArrayOf(172.0, 98.0, 72.0),
        doubleArrayOf(142.0, 76.0, 58.0),
    )
    val brickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val burn = fillPaint(rgb(0x2a1410, 0.22f), 4f)
    val chip = fillPaint(rgb(0x6d655b))
    val shade = strokePaint(rgb(0x000000, 0.32f), 1f)
    val catch = strokePaint(rgb(0xffd9c4, 0.12f), 0.8f)
    var r = 0
    while (r * row < height + row) {
        val y = r * row + mortar / 2
        val h = row - mortar
        val shift = (r % 2) * (brick / 2) + (rand.next() - 0.5) * 6
        var x = -brick + shift
        while (x < width + brick) {
            val w = brick - mortar + (rand.next() - 0.5) * 2
            val tone = tones[floor(rand.next() * tones.size).toInt()]
            val k = 0.85 + rand.next() * 0.25
            val base = DoubleArray(3) { tone[it] * k }
            val left = (x + mortar / 2).toFloat()
            brickPaint.shader = LinearGradient(
                0f, y.toFloat(), 0f, (y + h).toFloat(),
                intArrayOf(color(DoubleArray(3) { base[it] * 1.1 }), color(base), color(DoubleArray(3) { base[it] * 0.78 })),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(RectF(left, y.toFloat(), left + w.toFloat(), (y + h).toFloat()), 2.4f, 2.4f, brickPaint)
            // Pits and flecks, and now and then a darker burn or a chipped corner.
            for (i in 0 until 16) {
                val dark = rand.next() > 0.35
                val fx = x + mortar / 2 + rand.next() * w
                val fy = y + rand.next() * h
                val fr = 0.35 + rand.next() * 1.1
                paint.color = if (dark) color(DoubleArray(3) { base[it] * 0.6 }, 0.45f) else color(DoubleArray(3) { min(255.0, base[it] * 1.35) }, 0.45f)
                c.drawCircle(fx.toFloat(), fy.toFloat(), fr.toFloat(), paint)
            }
            if (rand.next() > 0.72) {
                val bx = x + mortar / 2 + rand.next() * w
                val by = y + rand.next() * h
                val br = 4 + rand.next() * 7
                c.drawCircle(bx.toFloat(), by.toFloat(), br.toFloat(), burn)
            }
            if (rand.next() > 0.85) {
                val cx = if (rand.next() > 0.5) x + mortar / 2 else x + mortar / 2 + w
                val cy = y + (if (rand.next() > 0.5) 0.0 else h)
                val cr = 2.5 + rand.next() * 2
                c.drawCircle(cx.toFloat(), cy.toFloat(), cr.toFloat(), chip)
            }
            // The lower edge sits in shadow, the upper one catches the light from above.
            c.drawLine(left + 2, (y + h - 0.4).toFloat(), left + w.toFloat() - 2, (y + h - 0.4).toFloat(), shade)
            c.drawLine(left + 2, (y + 0.5).toFloat(), left + w.toFloat() - 2, (y + 0.5).toFloat(), catch)
            x += brick
        }
        r += 1
    }
    // Soot and damp, a few broad stains.
    val stain = fillPaint(rgb(0x120a08, 0.16f), 36f)
    for (i in 0 until 9) {
        val sx = rand.next() * width
        val sy = rand.next() * height
        val sr = 40 + rand.next() * 90
        c.drawCircle(sx.toFloat(), sy.toFloat(), sr.toFloat(), stain)
    }
    return bitmap
}

/** The wall as the shader lights it with the sign off: for Android 12 and older. */
private fun bakeCalmWall(wall: Bitmap, scale: Float): Bitmap {
    val w = wall.width
    val h = wall.height
    val pixels = IntArray(w * h)
    wall.getPixels(pixels, 0, w, 0, 0, w, h)
    val sw = w / scale
    val sh = h / scale
    for (j in 0 until h) {
        val py = (j + 0.5f) / scale
        val v = py / sh
        val cy = (py - 0.5f * sh) / sh
        for (i in 0 until w) {
            val px = (i + 0.5f) / scale
            val cx = (px - 0.5f * sw) / sw
            val ambient = (0.07f + 0.11f * (1 - v)) * (1 - 0.9f * (cx * cx + cy * cy))
            val p = pixels[j * w + i]
            var r = ((p shr 16) and 0xff) / 255f * ambient * 0.78f
            var g = ((p shr 8) and 0xff) / 255f * ambient * 0.86f
            var b = (p and 0xff) / 255f * ambient
            r /= 1 + 0.25f * r
            g /= 1 + 0.25f * g
            b /= 1 + 0.25f * b
            pixels[j * w + i] = rgbF(r, g, b)
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun bake(scene: NeonScene, width: Float, height: Float, wallScale: Float): NeonBakes {
    val glows = scene.pieces.mapIndexed { i, p -> bakeGlow(p, scene.paths[i]) }
    val box = RectF(scene.panel).apply { inset(PANEL_PAD_X.toFloat(), PANEL_PAD_Y.toFloat()) }
    val spill = bakeSpill(scene, box)
    val wall = bakeWall(width, height, wallScale)
    val calm = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) bakeCalmWall(wall, wallScale) else null
    val panel = scene.panel
    return NeonBakes(
        glows = glows.map { it.first },
        glowRects = glows.map { it.second },
        spill = spill,
        spillX = box.left - SPILL_MARGIN,
        spillY = box.top - SPILL_MARGIN,
        wall = wall,
        wallScale = wallScale,
        calmWall = calm,
        shadow = bakeSoftRect(panel.width(), panel.height(), PANEL_RADIUS, 0.32f, 12f),
        shadowRect = RectF(panel.left - 36f, panel.top - 36f, panel.right + 36f, panel.bottom + 36f),
        plateShadow = bakeSoftRect(36f, 12f, 3f, 0.5f, 3f),
    )
}

/** Everything the frame loop, the touch and the picture share, kept off Compose state. */
private class NeonStage(val scene: NeonScene) {
    var sim: NeonSim? = null
    var acc = 0.0
    var looping = false
    // The screen (dp), the hook on it, and where the hook sits in the sign's own frame.
    var width = 0f
    var height = 0f
    var pivotX = 0f
    var pivotY = 0f
    val pivotLX = 0f
    val pivotLY = scene.panel.top - HANG
    val anchors = arrayOf(
        floatArrayOf(scene.panel.left + CHAIN_INSET, scene.panel.top + 11),
        floatArrayOf(scene.panel.right - CHAIN_INSET, scene.panel.top + 11),
    )
    var grabTheta = 0.0
    var grabPhi = 0.0
    var bakes: NeonBakes? by mutableStateOf(null)
    // Bumped on every frame that stepped the simulation: the picture reads it, so it redraws.
    var frame by mutableIntStateOf(0)

    /** The angle of (x, y) around the hook, measured from straight down, clockwise. */
    fun angleAround(x: Float, y: Float) = atan2(-(x - pivotX).toDouble(), (y - pivotY).toDouble())

    fun release(spin: Double) {
        val st = sim ?: return
        if (!st.dragging) return
        st.dragging = false
        st.omega = spin.coerceIn(-MAX_SPIN, MAX_SPIN)
        st.hold = USER_HOLD
    }
}

/** Paints and shaders reused from frame to frame. */
private class NeonPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val linkFace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    val image = Paint(Paint.FILTER_BITMAP_FLAG)
    val halo = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = AddMode }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = AddMode }
    val rect = RectF()
    val matrix = Matrix()
    // Null below Android 13, or if the shader fails to compile: the wall then falls back.
    val wall: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(WALL_AGSL) }.getOrNull() else null
    var shadersFor: NeonBakes? = null
    var plate: LinearGradient? = null
    var plateFor = Float.NaN
}

/**
 * A hand-bent neon sign, "open late" in rose and ice-blue script beside an amber cocktail glass,
 * hanging on two chains in front of a dark brick wall. The tubes buzz on letter by letter and warm
 * up, one letter never quite works, and the wall takes the colour of whatever is lit. Tap a word
 * to switch it; drag the sign to swing it on its chains.
 *
 * @param autoPlay Powers the sign on, browns it out, swings it and powers it down on a 12 s loop.
 */
@Composable
fun NeonSignFlicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val scene = remember { NeonScene() }
    val stage = remember(scene) { NeonStage(scene) }
    val paints = remember { NeonPaints() }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(stage, auto) {
        stage.looping = auto
        stage.sim?.let {
            it.clock = 0.0
            it.hold = 0.0
        }
    }

    LaunchedEffect(stage, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                var st = stage.sim
                if (st == null) {
                    st = NeonSim(
                        scene.pieces.map { it.word }.toIntArray(),
                        scene.pieces.map { it.order }.toIntArray(),
                        scene.faulty,
                        stage.looping,
                        reducedMotion,
                    )
                    stage.sim = st
                }
                st.looping = stage.looping
                st.reduced = reducedMotion
                val elapsed = if (last < 0) 0.016 else (now - last) / 1e9
                last = now
                val dt = min(max(0.0, elapsed), NEON_MAX_TICKS * NEON_TICK)
                var pending = stage.acc + dt
                var ticks = 0
                while (pending >= NEON_TICK && ticks < NEON_MAX_TICKS) {
                    st.step()
                    pending -= NEON_TICK
                    ticks += 1
                }
                stage.acc = min(pending, NEON_TICK)
                if (ticks > 0) stage.frame += 1
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .semantics { contentDescription = "Neon sign: open, late, cocktail glass. Tap a word to switch it, drag to swing the sign." }
            .pointerInput(stage, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val started = down.uptimeMillis
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    // A drag that was cut off lets go before a new one starts.
                    stage.release(0.0)
                    try {
                        var travel = androidx.compose.ui.geometry.Offset.Zero
                        var panning = false
                        var lastX = down.position.x / density.density
                        var lastY = down.position.y / density.density
                        var lifted = false
                        var upTime = started
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            lastX = change.position.x / density.density
                            lastY = change.position.y / density.density
                            travel += change.positionChange()
                            if (!panning && travel.getDistance() / density.density >= PAN_SLOP) {
                                panning = true
                                val st = stage.sim
                                if (st != null && !reducedMotion && hypot(lastX - stage.pivotX, lastY - stage.pivotY) >= 40f) {
                                    st.hold = USER_HOLD
                                    st.dragging = true
                                    st.omega = 0.0
                                    stage.grabTheta = st.theta
                                    stage.grabPhi = stage.angleAround(lastX, lastY)
                                }
                            }
                            if (panning) {
                                change.consume()
                                val st = stage.sim
                                if (st != null && st.dragging) {
                                    var d = stage.angleAround(lastX, lastY) - stage.grabPhi
                                    if (d > Math.PI) d -= 2 * Math.PI
                                    if (d < -Math.PI) d += 2 * Math.PI
                                    val raw = stage.grabTheta + d
                                    val over = kotlin.math.abs(raw) - MAX_SWING
                                    st.theta = if (over > 0) kotlin.math.sign(raw) * (MAX_SWING + over * RUBBER) else raw
                                }
                            }
                            if (!change.pressed) {
                                lifted = true
                                upTime = change.uptimeMillis
                                break
                            }
                        }
                        if (panning) {
                            if (lifted) {
                                val v = tracker.calculateVelocity()
                                val vx = v.x / density.density
                                val vy = v.y / density.density
                                val rx = lastX - stage.pivotX
                                val ry = lastY - stage.pivotY
                                stage.release(((rx * vy - ry * vx) / max(rx * rx + ry * ry, 900f)).toDouble())
                            } else {
                                stage.release(0.0)
                            }
                        } else if (lifted && upTime - started <= TAP_TIME) {
                            val st = stage.sim ?: return@awaitEachGesture
                            // The finger in the sign's frame.
                            val dx = (lastX - stage.pivotX).toDouble()
                            val dy = (lastY - stage.pivotY).toDouble()
                            val c = cos(st.theta)
                            val s = sin(st.theta)
                            val lx = stage.pivotLX + c * dx + s * dy
                            val ly = stage.pivotLY - s * dx + c * dy
                            var hit = -1
                            var best = Double.POSITIVE_INFINITY
                            for (piece in scene.pieces) {
                                val b = piece.box
                                val ox = max(max(b[0] - lx, 0.0), lx - (b[0] + b[2]))
                                val oy = max(max(b[1] - ly, 0.0), ly - (b[1] + b[3]))
                                val off = hypot(ox, oy)
                                if (off < 14 && off < best) {
                                    best = off
                                    hit = piece.word
                                }
                            }
                            if (hit >= 0) {
                                st.hold = USER_HOLD
                                st.setWord(hit, if (st.on[hit] == 1) 0 else 1)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                    } finally {
                        // A gesture cut off (the input restarted or the block left) lets go of the sign.
                        stage.release(0.0)
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        stage.width = width
        stage.height = height
        stage.pivotX = width / 2
        stage.pivotY = max(safeTop + 34f, height * CENTER_AT - scene.panel.height() / 2 - HANG)
        val wallScale = min(3f, density.density)

        LaunchedEffect(stage, width, height, wallScale) {
            // No bitmap of 0 × 0 (before layout) or of an unbounded size (an infinite constraint).
            if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return@LaunchedEffect
            stage.bakes = withContext(Dispatchers.Default) { bake(scene, width, height, wallScale) }
        }

        Canvas(Modifier.fillMaxSize()) {
            stage.frame
            val bakes = stage.bakes
            val st = stage.sim
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                if (bakes != null && st != null) drawNeon(c, stage, st, bakes, paints)
                c.restore()
            }
        }

        BasicText(
            text = "Tap a word to switch it · Drag to swing",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 22f).dp),
            style = TextStyle(
                color = HintColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Moves the canvas into the sign's frame: everything that hangs turns `deg` about the hook. */
private fun enterSign(c: android.graphics.Canvas, stage: NeonStage, deg: Float) {
    c.translate(stage.pivotX, stage.pivotY)
    c.rotate(deg)
    c.translate(-stage.pivotLX, -stage.pivotLY)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun drawShaderWall(c: android.graphics.Canvas, stage: NeonStage, b: NeonBakes, p: NeonPaints, levels: DoubleArray, theta: Double) {
    val shader = p.wall as RuntimeShader
    if (p.shadersFor !== b) {
        p.shadersFor = b
        val wall = BitmapShader(b.wall, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        wall.setLocalMatrix(Matrix().apply { setScale(1f / b.wallScale, 1f / b.wallScale) })
        val spill = BitmapShader(b.spill, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        spill.setLocalMatrix(Matrix().apply {
            setScale(1f / SPILL_SCALE, 1f / SPILL_SCALE)
            postTranslate(b.spillX, b.spillY)
        })
        shader.setInputShader("wall", wall)
        shader.setInputShader("spill", spill)
    }
    shader.setFloatUniform("pivotW", stage.pivotX, stage.pivotY)
    shader.setFloatUniform("pivotL", stage.pivotLX, stage.pivotLY)
    shader.setFloatUniform("rot", cos(theta).toFloat(), sin(theta).toFloat())
    shader.setFloatUniform("level", levels[0].toFloat(), levels[1].toFloat(), levels[2].toFloat())
    shader.setFloatUniform("c0", Spill[0][0], Spill[0][1], Spill[0][2])
    shader.setFloatUniform("c1", Spill[1][0], Spill[1][1], Spill[1][2])
    shader.setFloatUniform("c2", Spill[2][0], Spill[2][1], Spill[2][2])
    shader.setFloatUniform("size", stage.width, stage.height)
    p.fill.reset()
    p.fill.shader = shader
    c.drawRect(0f, 0f, stage.width, stage.height, p.fill)
    p.fill.shader = null
}

/**
 * Below Android 13 there is no runtime shader: the wall is baked under the street light alone and
 * each lit word throws a soft radial glow of its colour on it, centred on the word as the sign swings.
 */
private fun drawFallbackWall(c: android.graphics.Canvas, stage: NeonStage, b: NeonBakes, p: NeonPaints, levels: DoubleArray, deg: Float) {
    val calm = b.calmWall ?: return
    p.rect.set(0f, 0f, calm.width / b.wallScale, calm.height / b.wallScale)
    c.drawBitmap(calm, null, p.rect, p.image)
    c.save()
    enterSign(c, stage, deg)
    for (w in 0 until 3) {
        val l = levels[w].toFloat()
        if (l < 0.01f) continue
        var x0 = Float.POSITIVE_INFINITY
        var y0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        var y1 = Float.NEGATIVE_INFINITY
        stage.scene.pieces.forEach { piece ->
            if (piece.word != w) return@forEach
            x0 = min(x0, piece.box[0].toFloat())
            y0 = min(y0, piece.box[1].toFloat())
            x1 = max(x1, (piece.box[0] + piece.box[2]).toFloat())
            y1 = max(y1, (piece.box[1] + piece.box[3]).toFloat())
        }
        val s = Spill[w]
        val k = 0.32f * l
        p.glow.shader = RadialGradient(
            (x0 + x1) / 2, (y0 + y1) / 2, 240f,
            intArrayOf(rgbF(s[0] * k, s[1] * k, s[2] * k), rgbF(s[0] * k * 0.35f, s[1] * k * 0.35f, s[2] * k * 0.35f), android.graphics.Color.BLACK),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, 240f, p.glow)
    }
    p.glow.shader = null
    c.restore()
}

private fun drawNeon(c: android.graphics.Canvas, stage: NeonStage, st: NeonSim, b: NeonBakes, p: NeonPaints) {
    val scene = stage.scene
    val panel = scene.panel
    val levels = DoubleArray(3)
    st.wordLevels(levels)
    val theta = st.theta
    val deg = Math.toDegrees(theta).toFloat()

    // The wall, lit by the street and by the sign.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && p.wall != null) {
        drawShaderWall(c, stage, b, p, levels, theta)
    } else if (b.calmWall != null) {
        drawFallbackWall(c, stage, b, p, levels, deg)
    } else {
        c.drawColor(rgb(0x0c0908))
    }

    // The light the sign itself gives off, as a colour: tints its chains and the panel's edge.
    var lr = 0f
    var lg = 0f
    var lb = 0f
    for (w in 0 until 3) {
        lr += Spill[w][0] * levels[w].toFloat()
        lg += Spill[w][1] * levels[w].toFloat()
        lb += Spill[w][2] * levels[w].toFloat()
    }
    val glowAmount = min(1f, ((levels[0] + levels[1] + levels[2]) / 1.6).toFloat())
    val peak = max(max(lr, lg), max(lb, 0.001f))
    val tr = lr / peak
    val tg = lg / peak
    val tb = lb / peak

    // Its soft shadow from the street light above.
    c.save()
    c.translate(0f, 14f)
    enterSign(c, stage, deg)
    c.drawBitmap(b.shadow, null, b.shadowRect, p.image)
    c.restore()

    c.save()
    enterSign(c, stage, deg)

    // Chains: links alternately seen face-on and edge-on.
    val steel = rgbF(mix(0.42f, tr, 0.55f * glowAmount), mix(0.43f, tg, 0.55f * glowAmount), mix(0.46f, tb, 0.55f * glowAmount))
    p.linkFace.color = steel
    for (a in stage.anchors) {
        val dx = a[0] - stage.pivotLX
        val dy = a[1] - stage.pivotLY
        val len = hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val count = floor(len / LINK).toInt()
        for (k in 0 until count) {
            val cx = stage.pivotLX + ux * (k + 0.5f) * LINK
            val cy = stage.pivotLY + uy * (k + 0.5f) * LINK
            if (k % 2 == 0) {
                c.save()
                c.translate(cx, cy)
                c.rotate(Math.toDegrees(atan2(uy, ux).toDouble()).toFloat())
                p.rect.set(-4.6f, -2.6f, 4.6f, 2.6f)
                c.drawOval(p.rect, p.linkFace)
                p.stroke.color = rgb(0xffffff, 0.35f)
                p.stroke.strokeWidth = 0.6f
                p.rect.set(-4.0f, -2.1f, 4.0f, 2.1f)
                c.drawArc(p.rect, 200f, 90f, false, p.stroke)
                c.restore()
            } else {
                p.stroke.color = steel
                p.stroke.strokeWidth = 2.1f
                c.drawLine(cx - ux * 3.6f, cy - uy * 3.6f, cx + ux * 3.6f, cy + uy * 3.6f, p.stroke)
            }
        }
    }

    // The clear acrylic panel: barely there, but its edges carry the neon's light.
    p.fill.reset()
    p.fill.isAntiAlias = true
    p.fill.color = rgb(0xffffff, 0.035f)
    c.drawRoundRect(panel, PANEL_RADIUS, PANEL_RADIUS, p.fill)
    p.stroke.color = rgb(0xffffff, 0.08f)
    p.stroke.strokeWidth = 0.7f
    p.rect.set(panel.left + 3, panel.top + 3, panel.right - 3, panel.bottom - 3)
    c.drawRoundRect(p.rect, PANEL_RADIUS - 3, PANEL_RADIUS - 3, p.stroke)
    p.stroke.color = rgbF(mix(0.8f, tr, glowAmount), mix(0.82f, tg, glowAmount), mix(0.86f, tb, glowAmount), 0.16f + 0.5f * glowAmount)
    p.stroke.strokeWidth = 1.4f
    c.drawRoundRect(panel, PANEL_RADIUS, PANEL_RADIUS, p.stroke)
    // The holes the chains go through, with steel grommets.
    for (a in stage.anchors) {
        p.fill.color = rgb(0x0a0a0c, 0.9f)
        c.drawCircle(a[0], a[1], 4.2f, p.fill)
        p.stroke.color = rgb(0x9a9ca2, 0.9f)
        p.stroke.strokeWidth = 1.3f
        c.drawCircle(a[0], a[1], 4.2f, p.stroke)
        p.stroke.color = rgb(0xb9bbc0)
        p.stroke.strokeWidth = 2f
        c.drawLine(a[0], a[1] - 4.2f, a[0], a[1] - 1, p.stroke)
    }

    // Standoffs under the tubes.
    for (m in scene.mounts) {
        p.fill.color = rgb(0xc9d2da, 0.16f)
        c.drawCircle(m[0], m[1], 4.4f, p.fill)
        p.stroke.color = rgb(0xffffff, 0.25f)
        p.stroke.strokeWidth = 0.6f
        c.drawCircle(m[0], m[1], 4.4f, p.stroke)
    }

    // The tubes: coated glass, the lit gas inside it, its white-hot centre and a glint on the glass.
    for (i in scene.pieces.indices) {
        val piece = scene.pieces[i]
        val l = st.level[i].toFloat()
        val unlit = Unlit[piece.tone]
        val lit = Core[piece.tone]
        val core = rgbF(
            mix(((unlit shr 16) and 0xff) / 255f, ((lit shr 16) and 0xff) / 255f, l),
            mix(((unlit shr 8) and 0xff) / 255f, ((lit shr 8) and 0xff) / 255f, l),
            mix((unlit and 0xff) / 255f, (lit and 0xff) / 255f, l),
        )
        for (path in scene.paths[i]) {
            p.stroke.color = rgb(unlit)
            p.stroke.strokeWidth = TUBE
            c.drawPath(path, p.stroke)
            p.stroke.color = core
            p.stroke.strokeWidth = CORE_W
            c.drawPath(path, p.stroke)
            if (l > 0.02f) {
                p.stroke.color = rgb(Hot[piece.tone], min(1f, l * l * 1.1f))
                p.stroke.strokeWidth = HOT_W
                c.drawPath(path, p.stroke)
            }
        }
        c.save()
        c.translate(-0.9f, -1.2f)
        p.stroke.color = rgb(0xffffff, 0.34f * (1 - 0.75f * l))
        p.stroke.strokeWidth = 0.9f
        for (path in scene.paths[i]) c.drawPath(path, p.stroke)
        c.restore()
    }
    p.stroke.color = rgb(0x141216)
    p.stroke.strokeWidth = TUBE + 0.4f
    c.drawPath(scene.caps, p.stroke)

    // The halos, added on top as light.
    for (i in scene.pieces.indices) {
        val l = st.level[i]
        if (l < 0.01) continue
        p.halo.alpha = (min(1.0, l.pow(1.4)) * 255).roundToInt()
        c.drawBitmap(b.glows[i], null, b.glowRects[i], p.halo)
    }
    c.restore()

    // The hook: a small iron plate screwed to the wall, and the ring the chains hang from.
    val px = stage.pivotX
    val py = stage.pivotY
    p.rect.set(px - 18 - 9, py - 10 - 9, px + 18 + 9, py + 2 + 9)
    c.drawBitmap(b.plateShadow, null, p.rect, p.image)
    if (p.plateFor != py) {
        p.plateFor = py
        p.plate = LinearGradient(
            0f, py - 13, 0f, py - 1,
            intArrayOf(rgb(0x4a4b50), rgb(0x26272b), rgb(0x151518)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    p.fill.reset()
    p.fill.isAntiAlias = true
    p.fill.shader = p.plate
    p.rect.set(px - 18, py - 13, px + 18, py - 1)
    c.drawRoundRect(p.rect, 3f, 3f, p.fill)
    p.fill.shader = null
    for (sx in floatArrayOf(-11f, 11f)) {
        p.fill.color = rgb(0x6b6d72)
        c.drawCircle(px + sx, py - 7, 2.2f, p.fill)
        p.stroke.color = rgb(0x1a1a1d)
        p.stroke.strokeWidth = 0.8f
        c.drawLine(px + sx - 1.5f, py - 7, px + sx + 1.5f, py - 7, p.stroke)
    }
    p.stroke.color = rgb(0x8c8f95)
    p.stroke.strokeWidth = 2f
    c.drawCircle(px, py - 1, 4.6f, p.stroke)
    p.stroke.color = rgb(0xffffff, 0.5f)
    p.stroke.strokeWidth = 0.7f
    p.rect.set(px - 4.6f, py - 5.6f, px + 4.6f, py + 3.6f)
    c.drawArc(p.rect, 200f, 80f, false, p.stroke)
}
