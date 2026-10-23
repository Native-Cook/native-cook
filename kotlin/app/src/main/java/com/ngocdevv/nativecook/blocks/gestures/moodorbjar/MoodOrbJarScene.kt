package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val GLASS_WALL = 5f
private const val GLASS_BASE = 14f

private const val BEAD_SHADER = """
uniform float2 center;
uniform float radius;
uniform float3 lightColor;
uniform float3 baseColor;
uniform float3 deepColor;
half4 main(float2 xy) {
  float2 p = (xy - center) / radius;
  float edge = smoothstep(1.0, 0.965, length(p));
  float body = clamp(length(p - float2(-0.34, -0.40)) / 1.48, 0.0, 1.0);
  float3 color = mix(lightColor, baseColor, smoothstep(0.05, 0.76, body));
  color = mix(color, deepColor, smoothstep(0.70, 1.25, body) * 0.72);
  float glow = 1.0 - smoothstep(0.0, 0.58, length(p - float2(0.40, 0.43)));
  color = mix(color, lightColor, glow * 0.22);
  return half4(color, edge);
}
"""

/** One shader per draw prevents mutable uniforms leaking into a recorded display list. */
internal class MoodOrbJarBeadMaterial {
    private var cursor = 0
    private val agsl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MoodOrbJarAgslPool() else null

    fun beginFrame() { cursor = 0 }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun next(mood: MoodOrbJarMood, x: Float, y: Float, radius: Float): Paint {
        return checkNotNull(agsl).next(cursor++, mood, x, y, radius)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class MoodOrbJarAgslPool {
    private val shaders = Array(96) { RuntimeShader(BEAD_SHADER) }
    private val paints = Array(96) { Paint(Paint.ANTI_ALIAS_FLAG) }

    fun next(cursor: Int, mood: MoodOrbJarMood, x: Float, y: Float, radius: Float): Paint {
        val index = cursor.coerceAtMost(shaders.lastIndex)
        val shader = shaders[index]
        shader.setFloatUniform("center", x, y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("lightColor", mood.light.red, mood.light.green, mood.light.blue)
        shader.setFloatUniform("baseColor", mood.base.red, mood.base.green, mood.base.blue)
        shader.setFloatUniform("deepColor", mood.deep.red, mood.deep.green, mood.deep.blue)
        return paints[index].also { it.shader = shader }
    }
}

@Composable
internal fun rememberMoodOrbJarBeadMaterial() = remember { MoodOrbJarBeadMaterial() }

internal class MoodOrbJarSceneCache {
    val inner = Path()
    val outer = Path()
    val aim = Path()
    val cardShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    val floorShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    val caustic = Paint(Paint.ANTI_ALIAS_FLAG)
    var geometryKey = Double.NaN

    fun ensure(g: MoodOrbJarGeometry) {
        val key = g.w + g.mouthY + g.floor
        if (geometryKey == key) return
        geometryKey = key
        buildJarPath(inner, g, 0f, 0f, 8f * g.s.toFloat())
        buildJarPath(outer, g, GLASS_WALL * g.s.toFloat(), GLASS_BASE * g.s.toFloat(), 8f * g.s.toFloat())
        cardShadow.color = MoodOrbJarColors.cardShadow.toArgb()
        cardShadow.maskFilter = BlurMaskFilter(12f * g.s.toFloat(), BlurMaskFilter.Blur.NORMAL)
        floorShadow.color = MoodOrbJarColors.floorShadow.toArgb()
        floorShadow.maskFilter = BlurMaskFilter(10f * g.s.toFloat(), BlurMaskFilter.Blur.NORMAL)
        caustic.color = MoodOrbJarColors.caustic.toArgb()
        caustic.maskFilter = BlurMaskFilter(5f * g.s.toFloat(), BlurMaskFilter.Blur.NORMAL)
    }
}

@Composable
internal fun rememberMoodOrbJarSceneCache() = remember { MoodOrbJarSceneCache() }

private fun buildJarPath(path: Path, g: MoodOrbJarGeometry, d: Float, bottomExtra: Float, neck: Float) {
    val cx = g.cx.toFloat()
    val half = ((g.right - g.left) / 2).toFloat() + d
    val mouth = g.mouthHalf.toFloat() + d
    val top = g.mouthY.toFloat() - neck
    val bottom = g.floor.toFloat() + bottomExtra
    val corner = g.corner.toFloat() + d
    val shoulder = g.shoulderEnd.toFloat()
    val control = ((g.shoulderEnd - g.mouthY) * 0.55).toFloat()
    path.rewind()
    path.moveTo(cx - mouth, top)
    path.lineTo(cx - mouth, g.mouthY.toFloat())
    path.cubicTo(cx - mouth, g.mouthY.toFloat() + control, cx - half, shoulder - control, cx - half, shoulder)
    path.lineTo(cx - half, bottom - corner)
    path.quadraticTo(cx - half, bottom, cx - half + corner, bottom)
    path.lineTo(cx + half - corner, bottom)
    path.quadraticTo(cx + half, bottom, cx + half, bottom - corner)
    path.lineTo(cx + half, shoulder)
    path.cubicTo(cx + half, shoulder - control, cx + mouth, g.mouthY.toFloat() + control, cx + mouth, g.mouthY.toFloat())
    path.lineTo(cx + mouth, top)
    path.close()
}

internal fun DrawScope.drawMoodOrbJarBead(
    material: MoodOrbJarBeadMaterial?,
    moodIndex: Int,
    center: Offset,
    radius: Float,
    alpha: Float = 1f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
) {
    if (alpha <= 0f || radius <= 0f) return
    val mood = MoodOrbJarMoods[moodIndex.coerceIn(MoodOrbJarMoods.indices)]
    withTransform({ scale(scaleX, scaleY, pivot = center) }) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && material != null) {
            drawIntoCanvas { canvas ->
                val paint = material.next(mood, center.x, center.y, radius)
                paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
            }
        } else {
            drawCircle(
                Brush.radialGradient(
                    listOf(mood.light, mood.base, mood.deep),
                    Offset(center.x - radius * .34f, center.y - radius * .4f), radius * 1.48f,
                ), radius, center, alpha,
            )
        }
        drawCircle(mood.deep.copy(alpha = .24f * alpha), radius, center, style = Stroke(1f))
        drawOval(
            Brush.radialGradient(listOf(mood.light.copy(alpha = .34f * alpha), Color.Transparent), Offset(center.x + radius * .38f, center.y + radius * .42f), radius * .58f),
            Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2),
        )
        drawOval(Color.White.copy(alpha = .52f * alpha), Offset(center.x - radius * .5f, center.y - radius * .58f), Size(radius * .34f, radius * .18f))
        drawCircle(Color.White.copy(alpha = .64f * alpha), radius * .055f, Offset(center.x - radius * .32f, center.y - radius * .37f))
    }
}

/** Paper, picker and code-drawn glass jar. Static paths are cached outside the frame loop. */
internal fun DrawScope.drawMoodOrbJarScene(g: MoodOrbJarGeometry, frame: MoodOrbJarFrame, material: MoodOrbJarBeadMaterial, cache: MoodOrbJarSceneCache) {
    frame.readTick()
    cache.ensure(g)
    material.beginFrame()
    val s = g.s.toFloat()
    val width = g.w.toFloat()
    val height = g.h.toFloat()
    scale(density, density, pivot = Offset.Zero) {
        drawRect(Brush.linearGradient(listOf(MoodOrbJarColors.bg, MoodOrbJarColors.bgBottom), Offset.Zero, Offset(width * .3f, height)), size = Size(width, height))

        val cardX = MoodOrbJarLayout.SIDE.toFloat() * s
        val cardY = (g.top + MoodOrbJarLayout.PICKER_TOP * g.s).toFloat()
        val cardW = width - cardX * 2
        val cardH = MoodOrbJarLayout.PICKER_HEIGHT.toFloat() * s
        val cardRadius = MoodOrbJarLayout.CARD_RADIUS.toFloat() * s
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(cardX, cardY + 6f * s, cardX + cardW, cardY + 6f * s + cardH, cardRadius, cardRadius, cache.cardShadow)
        }
        drawRoundRect(MoodOrbJarColors.card, Offset(cardX, cardY), Size(cardW, cardH), CornerRadius(cardRadius))
        drawRoundRect(MoodOrbJarColors.cardEdge, Offset(cardX + .5f, cardY + .5f), Size(cardW - 1f, cardH - 1f), CornerRadius(cardRadius), style = Stroke(1f))
        for (index in MoodOrbJarMoods.indices) {
            val slot = frame.slots[index]
            val orbScale = .9f + .1f * slot
            val x = g.slotX[index].toFloat()
            val y = g.slotY.toFloat()
            drawOval(Color(0x293C2D19), Offset(x - 16f * s, y + 15.8f * s), Size(32f * s, 7.3f * s), alpha = slot)
            drawMoodOrbJarBead(material, index, Offset(x, y), MoodOrbJarLayout.PICKER_ORB.toFloat() * s, slot, orbScale, orbScale)
        }

        val rippleOn = frame.ripple[4]
        val rippleTime = frame.ripple[2] * MoodOrbJarRipple.DURATION
        val knock = if (rippleOn > 0f) {
            MoodOrbJarKnock.AMPLITUDE.toFloat() * s * exp(-MoodOrbJarKnock.DECAY * rippleTime).toFloat() * sin(2 * PI * MoodOrbJarKnock.HERTZ * rippleTime).toFloat()
        } else 0f
        withTransform({ translate(knock, 0f) }) {
            val half = ((g.right - g.left) / 2).toFloat()
            val groundY = g.floor.toFloat() + GLASS_BASE * s
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawOval(g.cx.toFloat() - half * 1.02f, groundY - 9f * s, g.cx.toFloat() - half * 1.02f + half * 2.3f, groundY + 13f * s, cache.floorShadow)
                canvas.nativeCanvas.drawOval(g.cx.toFloat() - half * .35f, groundY - 2f * s, g.cx.toFloat() - half * .35f + half * 1.05f, groundY + 7f * s, cache.caustic)
            }
            drawPath(cache.outer, MoodOrbJarColors.glassBody)
            drawOval(MoodOrbJarColors.glassEdgeSoft, Offset((g.cx - g.mouthHalf).toFloat(), (g.mouthY - 5 * g.s).toFloat()), Size((g.mouthHalf * 2).toFloat(), 10f * s), style = Stroke(1.5f))
            drawPool(g, frame, material, front = false)

            if (rippleOn > 0f) {
                drawCircle(
                    MoodOrbJarMoods[frame.ripple[3].toInt().coerceIn(MoodOrbJarMoods.indices)].light,
                    (MoodOrbJarLayout.ORB + MoodOrbJarRipple.RADIUS * frame.ripple[2]).toFloat() * s,
                    Offset(frame.ripple[0], frame.ripple[1]),
                    alpha = rippleOn * (1 - frame.ripple[2]) * .55f,
                    style = Stroke(2f * s),
                )
            }

            drawPath(cache.outer, MoodOrbJarColors.glassEdgeSoft, style = Stroke(GLASS_WALL * 2f * s))
            drawPath(cache.outer, MoodOrbJarColors.glassEdge, style = Stroke(1.2f))
            drawPath(cache.inner, Color.White.copy(alpha = .5f), style = Stroke(.8f))
            clipPath(cache.outer) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), Color(0x4296ACA6), Color(0x5C6E847E)), g.floor.toFloat(), groundY),
                    Offset(g.cx.toFloat() - half - GLASS_WALL * s, g.floor.toFloat()), Size((half + GLASS_WALL * s) * 2f, GLASS_BASE * s),
                )
            }
            drawLine(Color.White.copy(alpha = .8f), Offset(g.cx.toFloat() - half + g.corner.toFloat(), g.floor.toFloat() + .5f), Offset(g.cx.toFloat() + half - g.corner.toFloat(), g.floor.toFloat() + .5f))

            val sideTop = g.shoulderEnd.toFloat() + 10f * s
            val sideH = g.floor.toFloat() - g.corner.toFloat() - sideTop
            val shineX = frame.tilt * 6f * s
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .78f), Color.White.copy(alpha = .5f), Color.Transparent), sideTop, sideTop + sideH), Offset(g.cx.toFloat() - half + 10f * s + shineX, sideTop), Size(8f * s, sideH), CornerRadius(4f * s))
            drawRoundRect(Color.White.copy(alpha = .55f), Offset(g.cx.toFloat() - half + 22f * s + shineX, sideTop + 30f * s), Size(2f * s, sideH * .6f), CornerRadius(1f * s))
            drawRoundRect(Color.White.copy(alpha = .22f), Offset(g.cx.toFloat() + half - 15f * s + shineX, sideTop + 20f * s), Size(4f * s, sideH * .75f), CornerRadius(2f * s))
            drawArc(Color.White.copy(alpha = .85f), 0f, 180f, false, Offset((g.cx - g.mouthHalf - GLASS_WALL * g.s).toFloat(), (g.mouthY - 6 * g.s).toFloat()), Size(((g.mouthHalf + GLASS_WALL * g.s) * 2).toFloat(), 12f * s), style = Stroke(1.5f))

            if (frame.aimSize > 0) {
                cache.aim.rewind()
                var index = 0
                while (index + 1 < frame.aimSize) {
                    cache.aim.addOval(Rect(Offset(frame.aim[index], frame.aim[index + 1]), (2.2f - index * .04f) * s))
                    index += 2
                }
                drawPath(cache.aim, MoodOrbJarColors.aim)
            }
            drawPool(g, frame, material, front = true)
            drawLid(g, frame.lid)
        }
    }
}

private fun DrawScope.drawPool(g: MoodOrbJarGeometry, frame: MoodOrbJarFrame, material: MoodOrbJarBeadMaterial, front: Boolean) {
    val r = MoodOrbJarLayout.ORB.toFloat() * g.s.toFloat()
    val half = ((g.right - g.left) / 2).toFloat()
    for (i in 0 until POOL) {
        val k = i * ORB_STRIDE
        if ((frame.orbs[k + 10] == 1f) != front) continue
        val alpha = frame.orbs[k + 4]
        if (alpha <= 0f) continue
        val x = frame.orbs[k]
        val y = frame.orbs[k + 1]
        val q = frame.orbs[k + 6]
        val inside = !front && y > g.mouthY + r
        val u = ((x - g.cx.toFloat()) / half).coerceIn(-1f, 1f)
        val lens = if (inside) 1f - .22f * u * u else 1f
        val cx = x + if (inside) .05f * (x - g.cx.toFloat()) else 0f
        if (front && frame.orbs[k + 9] == 1f) {
            for (n in 0 until MoodOrbJarTrail.COPIES) {
                val tau = (n + 1) * MoodOrbJarTrail.GAP.toFloat()
                drawCircle(MoodOrbJarMoods[frame.orbs[k + 5].toInt().coerceIn(MoodOrbJarMoods.indices)].base, r * (1f - tau * 3f), Offset(cx - frame.orbs[k + 7] * tau, y - frame.orbs[k + 8] * tau + .5f * MoodOrbJarThrow.GRAVITY.toFloat() * g.s.toFloat() * tau * tau), alpha = MoodOrbJarTrail.alpha[n])
            }
        }
        drawMoodOrbJarBead(material, frame.orbs[k + 5].toInt(), Offset(cx, y + q * r), r, alpha, lens * frame.orbs[k + 3] * (1f + q), frame.orbs[k + 3] * (1f - q))
    }
}

private fun DrawScope.drawLid(g: MoodOrbJarGeometry, amount: Float) {
    val s = g.s.toFloat()
    val width = MoodOrbJarLayout.LID_WIDTH.toFloat() * s
    val height = MoodOrbJarLayout.LID_HEIGHT.toFloat() * s
    val x = g.lidX.toFloat()
    val y = g.lidY.toFloat()
    val hinge = Offset(x + width / 2f, y + height)
    withTransform({
        translate(0f, -MoodOrbJarLid.LIFT.toFloat() * s * amount)
        rotate((-MoodOrbJarLid.TILT * amount * 180.0 / PI).toFloat(), hinge)
    }) {
        drawRoundRect(Color(0x2E281E14), Offset(x + 2f * s, y + height - 2f * s), Size(width - 4f * s, 6f * s), CornerRadius(3f * s))
        drawRoundRect(Brush.horizontalGradient(MoodOrbJarColors.lid, x, x + width), Offset(x, y), Size(width, height), CornerRadius(7f * s))
        repeat(((width / (4f * s)).toInt() - 3).coerceAtLeast(0)) { index ->
            val ridgeX = x + (8f + index * 4f) * s
            drawLine(MoodOrbJarColors.lidRidge, Offset(ridgeX, y + 8f * s), Offset(ridgeX, y + height - 5f * s), .75f)
        }
        drawLine(Color.White.copy(alpha = .7f), Offset(x + 6f * s, y + 1.5f), Offset(x + width - 6f * s, y + 1.5f), 1f, StrokeCap.Round)
        drawRoundRect(Color(0x401E1810), Offset(x, y), Size(width, height), CornerRadius(7f * s), style = Stroke(.75f))
    }
}

internal fun DrawScope.drawMoodOrbJarGhost(g: MoodOrbJarGeometry, frame: MoodOrbJarFrame) {
    frame.readTick()
    if (frame.ghost[4] <= 0f) return
    scale(density, density, pivot = Offset.Zero) {
        drawMoodOrbJarBead(null, frame.ghost[3].toInt(), Offset(frame.ghost[0], frame.ghost[1]), MoodOrbJarLayout.ORB.toFloat() * g.s.toFloat(), frame.ghost[4], frame.ghost[2], frame.ghost[2])
    }
}
