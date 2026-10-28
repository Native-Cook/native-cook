package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.sqrt

/** Back light each habitat casts on the creature's silhouette (fire, water, grass). */
private val RIM_LIGHT = arrayOf(floatArrayOf(1f, 0.55f, 0.22f), floatArrayOf(0.45f, 0.85f, 1f), floatArrayOf(0.75f, 0.95f, 0.45f))

/** Margin around the art square, so a hop, a squash or a wing beat is never cut by the canvas. */
internal const val CREATURE_PAD = 0.14f
private const val ART = 512

/** Skia's RuntimeEffect output on iOS is converted in the shader; AGSL needs none. A runtime shader is drawn on a hardware canvas in points (dp). */
internal fun supportsAgsl() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * A normal map baked once from the art's alpha (NORMAL_BAKE, on the CPU): a wide blur (sigma 16)
 * gives the body its dome, a near one (sigma 5) keeps limbs and edges crisp; the gradient of the two
 * is the surface's slope. Any cut-out PNG gets a volume this way, with no extra asset.
 */
internal fun bakeNormals(image: Bitmap): Bitmap {
    val width = image.width
    val height = image.height
    val pixels = IntArray(width * height)
    image.getPixels(pixels, 0, width, 0, 0, width, height)
    val alpha = FloatArray(width * height) { (pixels[it] ushr 24) / 255f }
    val wide = blur(alpha, width, height, 16f)
    val near = blur(alpha, width, height, 5f)
    fun at(a: FloatArray, x: Int, y: Int): Float = if (x < 0 || y < 0 || x >= width || y >= height) 0f else a[y * width + x]
    // NORMAL_BAKE's `depth` uniform: the reference passes 7.
    val depth = 7f
    val out = IntArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        if (alpha[y * width + x] < .01f) { out[y * width + x] = argb(.5f, .5f, 1f); continue }
        val hx = (at(wide, x + 2, y) - at(wide, x - 2, y)) * .65f + (at(near, x + 2, y) - at(near, x - 2, y)) * .35f
        val hy = (at(wide, x, y + 2) - at(wide, x, y - 2)) * .65f + (at(near, x, y + 2) - at(near, x, y - 2)) * .35f
        val nx = -hx * depth
        val ny = -hy * depth
        val inv = 1f / sqrt(nx * nx + ny * ny + 1f)
        out[y * width + x] = argb(nx * inv * .5f + .5f, ny * inv * .5f + .5f, inv * .5f + .5f)
    }
    return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
}

private fun argb(r: Float, g: Float, b: Float): Int {
    fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (255 shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
}

/** Separable Gaussian of the alpha with transparent edges (TileMode.Decal). */
private fun blur(source: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
    val radius = (sigma * 3f).toInt()
    val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
    val total = kernel.sum()
    for (i in kernel.indices) kernel[i] /= total
    val pass = FloatArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        var sum = 0f
        for (k in -radius..radius) {
            val xx = x + k
            if (xx in 0 until width) sum += source[y * width + xx] * kernel[k + radius]
        }
        pass[y * width + x] = sum
    }
    val out = FloatArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        var sum = 0f
        for (k in -radius..radius) {
            val yy = y + k
            if (yy in 0 until height) sum += pass[yy * width + x] * kernel[k + radius]
        }
        out[y * width + x] = sum
    }
    return out
}

/** One creature's place in the exchange and its pose this frame (shared with its signature effects). */
internal class CreatureState(val exchange: ExchangePose, val pose: LifePose, val performing: Boolean)

internal fun creaturePose(index: Int, motion: HabitatMotion): CreatureState {
    val reducedMotion = motion.reduced
    val exchange = exchangePose(index, motion.position, reducedMotion)
    val performing = motion.powerIndex == index
    val pose = lifePose(
        LifeInput(
            t = motion.time, reduced = reducedMotion, role = exchange.role, f = exchange.f,
            press = motion.press.value, look = motion.look.value, pet = motion.pet.value, petClock = motion.petClock,
            land = if (!reducedMotion && motion.landingIndex == index) landEnvelope(motion.landing.value) else 0f,
            signature = if (performing) motion.power.value else 0f, fx = if (performing) motion.fx.value else -1f, index = index,
        ),
    )
    return CreatureState(exchange, pose, performing)
}

/** CREATURE_LIFE's uniforms for one frame (lifeUniforms in HabitatRig.ts). */
@RequiresApi(33)
internal fun applyLifeUniforms(shader: RuntimeShader, life: LifeFrame) {
    val frame = life.body
    val index = frame.index
    val size = frame.size
    val pose = frame.pose
    val exchange = frame.exchange
    val rig = RIGS[index]
    val p = placement(frame)
    val k = p.k
    fun joint(name: String, bend: String, j: Int) {
        val data = rig.joints[j]
        shader.setFloatUniform(name, data.pivot[0] * k, data.pivot[1] * k, data.center[0] * k, data.center[1] * k)
        shader.setFloatUniform(bend, data.radius * k, pose.angles[j])
    }
    joint("j0", "k0", 0); joint("j1", "k1", 1); joint("j2", "k2", 2); joint("j3", "k3", 3)
    shader.setFloatUniform("box", size, p.fy)
    shader.setFloatUniform("pd", 0.9f)
    shader.setFloatUniform("invX", p.c / pose.sx, p.s / pose.sx, p.fx - (p.c * p.ox + p.s * p.oy) / pose.sx)
    shader.setFloatUniform("invY", -p.s / pose.sy, p.c / pose.sy, p.fy - (-p.s * p.ox + p.c * p.oy) / pose.sy)
    shader.setFloatUniform("headShift", pose.headShift * frame.scale)
    shader.setFloatUniform("chest", rig.chest[0] * k, rig.chest[1] * k, rig.chest[2] * k, pose.breath)
    shader.setFloatUniform("hips", rig.hips * k, pose.shift * k, pose.hang, 10 * k)
    fun eye(name: String, e: Int) {
        val v = rig.eyes[e]
        shader.setFloatUniform(name, v[0] * k, v[1] * k, v[2] * k, v[3] * k)
    }
    eye("eyeA", 0); eye("eyeB", 1)
    shader.setFloatUniform("lid", pose.lid, pose.squint)
    shader.setFloatUniform("origin", p.originX, p.originY)
    shader.setFloatUniform("zoom", p.z)
    shader.setFloatUniform("screen", frame.width, frame.screenHeight)
    shader.setFloatUniform("front", exchange.f)
    shader.setFloatUniform("phase", life.phase)
    shader.setFloatUniform("role", if (exchange.role == 1) 1f else 0f)
    shader.setFloatUniform("element", life.element.toFloat())
    shader.setFloatUniform("tint", life.tint[0], life.tint[1], life.tint[2])
    shader.setFloatUniform("strength", 0.6f)
    // Pushed back into the old world's shade as the new one covers it.
    shader.setFloatUniform("shade", if (exchange.role == 0) 0.3f * exchange.f else 0f)
    shader.setFloatUniform("light", life.light[0], life.light[1], life.light[2])
    shader.setFloatUniform("glowAt", rig.glow.at[0] * k, rig.glow.at[1] * k, rig.glow.radius * k, life.glow)
    shader.setFloatUniform("glowColor", rig.glow.color[0], rig.glow.color[1], rig.glow.color[2])
    shader.setFloatUniform("face", rig.face.at[0] * k, rig.face.at[1] * k, rig.face.radius * k, pose.yaw * rig.face.shift * k)
    val flame = rig.flame
    if (flame != null) shader.setFloatUniform("flame", flame.base[0] * k, flame.base[1] * k, flame.height * k, 1f)
    else shader.setFloatUniform("flame", 0f, 0f, 1f, 0f)
    if (flame != null) shader.setFloatUniform("flameArea", flame.at[0] * k, flame.at[1] * k, flame.radius[0] * k, flame.radius[1] * k)
    else shader.setFloatUniform("flameArea", 0f, 0f, 1f, 1f)
    shader.setFloatUniform("flameState", pose.flameGrow, life.seconds, pose.flameStretch, if (life.reduced) 0f else 1f)
    shader.setFloatUniform("hidden", if (exchange.role < 0) 1f else 0f)
}

/** The shaders of one creature: its image and normal map as children of CREATURE_LIFE. */
@RequiresApi(33)
private class CreatureShader(val image: Bitmap, val normals: Bitmap?) {
    val runtime = RuntimeShader(CREATURE_LIFE_AGSL)
    private val imageShader = BitmapShader(image, Shader.TileMode.DECAL, Shader.TileMode.DECAL).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR }
    private val normalShader = BitmapShader(normals ?: image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR }
    private val matrix = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The art fills a `size` square at the canvas origin (fit contain), as the reference's ImageShaders do. */
    fun place(size: Float) {
        matrix.setScale(size / image.width, size / image.height)
        imageShader.setLocalMatrix(matrix)
        matrix.setScale(size / (normals ?: image).width, size / (normals ?: image).height)
        normalShader.setLocalMatrix(matrix)
        runtime.setInputShader("image", imageShader)
        runtime.setInputShader("normals", normalShader)
        paint.shader = runtime
    }
}

/**
 * One creature, alive from one flat PNG. The exchange moves the whole canvas (a graphics layer);
 * the body's own pose is drawn inside it by CREATURE_LIFE.
 */
@Composable
internal fun HabitatCreature(
    index: Int,
    image: Bitmap,
    normals: Bitmap?,
    motion: HabitatMotion,
    size: Float,
    left: Float,
    top: Float,
    width: Float,
    heroTop: Float,
    screenHeight: Float,
    scale: Float,
) {
    val density = LocalDensity.current.density
    val pad = Math.round(size * CREATURE_PAD).toFloat()
    val view = size + pad * 2
    val drift = EXCHANGE_DRIFT * width
    val shader = remember(image, normals) { if (supportsAgsl()) CreatureShader(image, normals) else null }
    Box(
        Modifier
            .offset((left - pad).dp, (top - pad).dp)
            .size(view.dp)
            .graphicsLayer {
                motion.observe()
                val exchange = exchangePose(index, motion.position, motion.reduced)
                // Below API 33 the art is drawn still, cross-faded by the same exchange.
                alpha = if (exchange.role < 0) 0f else if (shader == null) creatureOpacity(index, motion.position, motion.reduced) else 1f
                translationX = exchange.drift * drift * density
                scaleX = exchange.scale
                scaleY = exchange.scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                clip = false
            }
            .drawBehind {
                motion.observe()
                if (shader == null || Build.VERSION.SDK_INT < 33) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(
                            image, null, android.graphics.RectF(pad * density, pad * density, (pad + size) * density, (pad + size) * density), null,
                        )
                    }
                    return@drawBehind
                }
                val state = creaturePose(index, motion)
                val life = LifeFrame(
                    body = BodyFrame(
                        index = index, size = size, pad = pad, left = left, top = top, heroTop = heroTop, width = width,
                        screenHeight = screenHeight, scale = scale, exchange = state.exchange, driftPx = drift, pose = state.pose,
                    ),
                    phase = motion.frontPhase,
                    element = (floorToInt(wrapPosition(motion.position)) + 1) % 3,
                    light = if (normals != null) motion.light else floatArrayOf(0f, 0f, 0f),
                    tint = RIM_LIGHT[index],
                    glow = if (state.performing) signatureEnvelope(motion.power.value) else 0f,
                    seconds = motion.time * LOOP,
                    reduced = motion.reduced,
                )
                applyLifeUniforms(shader.runtime, life)
                shader.place(size)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    native.drawRect(0f, 0f, view, view, shader.paint)
                    native.restore()
                }
            },
    )
}

internal fun floorToInt(v: Float): Int = kotlin.math.floor(v).toInt()
