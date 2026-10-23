package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.MaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Xfermode
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Runtime shaders (AGSL) exist from Android 13; below it each material has a plain stand-in. */
internal val SHADERS_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** A hardware canvas draws textured vertex meshes from Android 10; below it they go through a bitmap. */
private val HARDWARE_MESHES = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

internal val BLEND_PLUS = PorterDuffXfermode(PorterDuff.Mode.ADD)
internal val BLEND_SCREEN = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
internal val BLEND_SRC_ATOP = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

/** A colour from 0–255 channels and a 0–1 alpha, as the source writes `rgba(…)`. */
internal fun rgba(red: Int, green: Int, blue: Int, alpha: Double): Int =
    Color.argb((alpha * 255).roundToInt().coerceIn(0, 255), red, green, blue)

/** [color] with its alpha multiplied by [opacity]. */
internal fun fade(color: Int, opacity: Double): Int =
    (color and 0x00FFFFFF) or ((Color.alpha(color) * clamp(opacity, 0.0, 1.0)).roundToInt() shl 24)

internal fun alpha255(opacity: Double): Int = (clamp(opacity, 0.0, 1.0) * 255).roundToInt()

internal fun linearGradient(
    x0: Double,
    y0: Double,
    x1: Double,
    y1: Double,
    colors: IntArray,
    positions: FloatArray?,
): Shader =
    LinearGradient(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), colors, positions, Shader.TileMode.CLAMP)

internal fun radialGradient(
    x: Double,
    y: Double,
    radius: Double,
    colors: IntArray,
    positions: FloatArray?,
): Shader =
    RadialGradient(x.toFloat(), y.toFloat(), max(radius, 0.001).toFloat(), colors, positions, Shader.TileMode.CLAMP)

/** [bitmap] stretched over [rect], clamped at its edges: the source's `ImageShader` with `fit="fill"`. */
internal fun imageShader(bitmap: Bitmap, rect: Rect): BitmapShader {
    val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val matrix = Matrix()
    matrix.setScale((rect.width / bitmap.width).toFloat(), (rect.height / bitmap.height).toFloat())
    matrix.postTranslate(rect.x.toFloat(), rect.y.toFloat())
    shader.setLocalMatrix(matrix)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // A child of a runtime shader does not read the paint's filter flag.
        shader.filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
    return shader
}

/** Compiles an AGSL program, or returns null where it cannot run, so the caller draws its stand-in. */
internal fun runtimeShader(name: String, source: String): RuntimeShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return try {
        RuntimeShader(source)
    } catch (error: IllegalArgumentException) {
        Log.w("BoosterPackOpening", "The $name shader did not compile", error)
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setRect(name: String, rect: Rect) {
    setFloatUniform(name, rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat())
}

/**
 * Blur mask filters by Skia sigma in dp. Android's filter takes a radius, which Skia turns back into
 * `0.57735 * radius + 0.5`, so a sigma under half a dp is drawn at that floor.
 */
internal class BlurCache {
    private val filters = HashMap<Int, MaskFilter>()

    fun sigma(sigma: Double): MaskFilter {
        val key = (sigma * 200).roundToInt()
        return filters.getOrPut(key) {
            if (filters.size > 256) filters.clear()
            BlurMaskFilter(max((key / 200.0 - 0.5) / 0.57735, 0.01).toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
    }
}

/**
 * One reusable paint, configured per shape the way the source configures a Skia paint: a colour
 * or a shader, an opacity, a blur given as a Skia sigma, and a blend mode.
 */
internal class Brush {
    private val paint = Paint()
    private val blurs = BlurCache()

    fun fill(
        color: Int = Color.BLACK,
        opacity: Double = 1.0,
        blur: Double = 0.0,
        shader: Shader? = null,
        blend: Xfermode? = null,
    ): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        if (shader != null) {
            paint.shader = shader
            paint.alpha = alpha255(opacity)
        } else {
            paint.color = fade(color, opacity)
        }
        if (blur > 0) paint.maskFilter = blurs.sigma(blur)
        paint.xfermode = blend
        return paint
    }

    fun stroke(
        width: Double,
        color: Int = Color.BLACK,
        opacity: Double = 1.0,
        blur: Double = 0.0,
        shader: Shader? = null,
        blend: Xfermode? = null,
        cap: Paint.Cap = Paint.Cap.BUTT,
        join: Paint.Join = Paint.Join.MITER,
    ): Paint {
        fill(color, opacity, blur, shader, blend)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = cap
        paint.strokeJoin = join
        return paint
    }
}

/** Reusable float arrays and the drawing of a textured triangle mesh. */
internal class MeshRenderer {
    private var positions = FloatArray(0)
    private var coordinates = FloatArray(0)
    private val scratches = ArrayList<Scratch>()
    private var cursor = 0
    private val target = RectF()

    /** True where the canvas draws meshes itself, without the detour through a bitmap. */
    val hardware: Boolean get() = HARDWARE_MESHES

    /** Call before a frame's first mesh: the bitmaps of the frame before may be drawn into again. */
    fun beginFrame() {
        cursor = 0
    }

    /**
     * Draws [vertices] with [paint]'s shader sampled at [textures]. The paint's alpha and blend
     * mode apply to the mesh as a whole. [density] is the scale of [canvas], used below Android 10
     * to size the bitmap the mesh is rasterised into.
     */
    fun draw(
        canvas: Canvas,
        vertices: List<Point>,
        textures: List<Point>,
        indices: ShortArray,
        paint: Paint,
        density: Float,
    ) {
        val count = min(vertices.size, textures.size)
        if (count < 3) return
        if (positions.size < count * 2) {
            positions = FloatArray(count * 2)
            coordinates = FloatArray(count * 2)
        }
        for (index in 0 until count) {
            val vertex = vertices[index]
            val texture = textures[index]
            positions[index * 2] = vertex.x.toFloat()
            positions[index * 2 + 1] = vertex.y.toFloat()
            coordinates[index * 2] = texture.x.toFloat()
            coordinates[index * 2 + 1] = texture.y.toFloat()
        }
        if (HARDWARE_MESHES) {
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES, count * 2, positions, 0, coordinates, 0, null, 0,
                indices, 0, indices.size, paint,
            )
        } else {
            drawThroughBitmap(canvas, count, indices, paint, density)
        }
    }

    // Android 8 and 9 ignore drawVertices on a hardware canvas. The mesh is rasterised in software
    // into a bitmap the size of its bounds, and that bitmap is composited with the paint's alpha
    // and blend mode, so clips and layers around the call behave the same. The canvas reads a
    // bitmap when the frame is rendered, not when it is recorded, so every mesh of one frame has
    // a bitmap of its own.
    private fun drawThroughBitmap(canvas: Canvas, count: Int, indices: ShortArray, paint: Paint, density: Float) {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (index in 0 until count) {
            left = min(left, positions[index * 2])
            right = max(right, positions[index * 2])
            top = min(top, positions[index * 2 + 1])
            bottom = max(bottom, positions[index * 2 + 1])
        }
        left = floor(left - 1)
        top = floor(top - 1)
        right = ceil(right + 1)
        bottom = ceil(bottom + 1)
        val width = ceil((right - left) * density).toInt()
        val height = ceil((bottom - top) * density).toInt()
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return
        if (cursor == scratches.size) scratches.add(Scratch())
        val scratch = scratches[cursor]
        cursor += 1
        val bitmap = scratch.sized(width, height)
        val software = scratch.canvas
        val alpha = paint.alpha
        val mode = paint.xfermode
        paint.alpha = 255
        paint.xfermode = null
        software.save()
        software.scale(density, density)
        software.translate(-left, -top)
        software.drawVertices(
            Canvas.VertexMode.TRIANGLES, count * 2, positions, 0, coordinates, 0, null, 0,
            indices, 0, indices.size, paint,
        )
        software.restore()
        paint.alpha = alpha
        paint.xfermode = mode
        scratch.blit.alpha = alpha
        scratch.blit.xfermode = mode
        target.set(left, top, left + bitmap.width / density, top + bitmap.height / density)
        canvas.drawBitmap(bitmap, null, target, scratch.blit)
    }

    /** A software bitmap that grows to the largest mesh it has held, cleared before each use. */
    private class Scratch {
        private var bitmap: Bitmap? = null
        var canvas = Canvas()
            private set
        val blit = Paint(Paint.FILTER_BITMAP_FLAG)

        fun sized(width: Int, height: Int): Bitmap {
            val current = bitmap
            if (current != null && current.width >= width && current.height >= height) {
                current.eraseColor(Color.TRANSPARENT)
                return current
            }
            val grown =
                Bitmap.createBitmap(max(width, current?.width ?: 0), max(height, current?.height ?: 0), Bitmap.Config.ARGB_8888)
            bitmap = grown
            canvas = Canvas(grown)
            return grown
        }
    }
}
