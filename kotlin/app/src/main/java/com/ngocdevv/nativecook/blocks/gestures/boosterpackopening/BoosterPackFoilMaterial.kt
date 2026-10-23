package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// These angles describe the out-of-plane rotation that a projected 2D mesh
// cannot retain. The light stays fixed; the turning, twisting foil reflects it.
// In the reference the reflection reaches the right end at f133, then moves
// toward the middle as that end turns away between f140 and f162.
private val MATERIAL_TIMES =
    doubleArrayOf(0.0, 66.667, 116.667, 150.0, 233.333, 333.333, 433.333, 533.333, 633.333, 733.333, 900.0, 1000.0)
private val MATERIAL_ROLL = doubleArrayOf(-1.32, -1.14, -0.88, -0.66, 0.2, 1.02, 1.75, 2.05, 2.3, 2.48, 2.65, 2.8)
private val MATERIAL_TWIST = doubleArrayOf(0.12, 0.5, 1.08, 1.5, 1.85, 2.12, 2.35, 2.58, 2.8, 3.02, 3.25, 3.4)

private val SURFACE_LOOKUP =
    (0 until MESH_COLUMNS - 1).joinToString("\n") { index ->
        "if (column <= ${index + 1}.0) return mix(surface[$index], surface[${index + 1}], column - $index.0);"
    }

private val FOIL_SHADER =
    """
uniform shader artwork;
uniform float4 packRect;
uniform float4 material;
uniform float pixelScale;
// Per column: signed facing, bending angle, texture height, projected height.
uniform float4 surface[$MESH_COLUMNS];

float4 sampleSurface(float column) {
  $SURFACE_LOOKUP
  return surface[${MESH_COLUMNS - 1}];
}

half4 main(float2 xy) {
  float u = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
  float column = u * ${MESH_COLUMNS - 1}.0;
  float4 local = sampleSurface(column);
  float v = clamp((xy.y - packRect.y) / max(local.z, 0.001), 0.0, 1.0);
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);

  // The source photograph includes a fixed pink light on its left side.
  // Remove that illumination from the purple substrate, keeping the printed
  // logos and pattern, before applying the changing reflection.
  float purpleInk = smoothstep(0.08, 0.28, print.b - print.g);
  float leftSubstrate = 1.0 - smoothstep(0.32, 0.4, u);
  // The unlit violet ink has little green. Its excess green estimates the
  // additive pink illumination more reliably than painting a dark UV stripe.
  float deglare = max(print.g - 0.025, 0.0)
    * purpleInk * leftSubstrate * material.z;
  float3 unlit = max(
    // Blue clips early in the photographed pink hotspot. Removing as much
    // blue as green would turn the recovered violet ink into a maroon patch.
    print - deglare * float3(1.05, 0.95, 0.35),
    print * float3(0.32, 0.08, 0.4)
  );

  // A softbox reflection is evaluated in foil coordinates, including both the
  // span-wise twist and the cross-strip bend. UVs then carry it through every
  // triangle's rotation, compression and inversion with the printed texture.
  float theta = material.x + (u - 0.5) * material.y
    + (v - 0.56) * 0.44 + local.y * 0.6;
  float3 normal = float3(sin(theta), 0.0, cos(theta));
  float facingLight = max(normal.z, 0.0);
  float broadReflection = pow(facingLight, 16.0);
  float sharpReflection = pow(facingLight, 88.0);
  float lowerReflection = 0.58 + 0.42 * smoothstep(0.1, 0.95, v);
  float reflection = (broadReflection * 0.42 + sharpReflection * 0.76)
    * lowerReflection * pow(abs(local.x), 0.65) * material.w;

  // The signed Jacobian of each mesh column decides which side is visible.
  // Adjacent parts of the strip can show different sides during the twist.
  float front = smoothstep(-0.075, 0.075, local.x);
  float diffuse = 0.69 + 0.22 * abs(local.x) + 0.09 * facingLight;
  float3 frontColor = unlit * diffuse;
  float reverseDetail = dot(unlit, float3(0.2126, 0.7152, 0.0722));
  float3 reverseColor = float3(0.32, 0.012, 0.72)
    * (0.72 + 0.28 * abs(local.x))
    + reverseDetail * float3(0.04, 0.005, 0.07);
  // The thin purple laminate keeps its printed marks visible as it twists.
  // In particular the dark panel and silver letters remain legible on the
  // turned tip; replacing the whole reverse with purple erased that detail.
  float reversePrint = (1.0 - purpleInk) * 0.88
    * (1.0-smoothstep(1.90,2.20,material.x));
  reverseColor = mix(reverseColor, unlit * float3(0.72, 0.62, 0.90), reversePrint);
  float3 base = mix(reverseColor, frontColor, front);

  // The broad reflected light is pink/violet; its center tends toward silver,
  // with a restrained cyan interference fringe as the foil becomes edge-on.
  float interference = 0.5 + 0.5 * sin(theta * 5.0 + v * 2.1);
  float3 reflectionColor = mix(
    float3(1.0, 0.52, 0.93),
    float3(0.68, 0.9, 1.0),
    interference * 0.3
  );
  reflectionColor = mix(reflectionColor, float3(1.0, 0.97, 1.0), sharpReflection);
  float backsideReflection = pow(max(-normal.z, 0.0), 28.0) * 0.055;
  reflection = mix(backsideReflection * material.w, reflection, front);
  float3 lit = base + (1.0 - base) * reflectionColor * min(reflection, 0.94);

  float grazing = pow(1.0 - abs(local.x), 3.0);
  lit += grazing * float3(0.025, 0.014, 0.065) * material.w;

  // Thin colored edge, also in UV space. Its width compensates for projected
  // compression, so a turning strip does not keep an oversized glowing rim.
  float rimWidth = clamp(1.0 * pixelScale / max(local.w, pixelScale), 0.007, 0.1);
  float rim = smoothstep(1.0 - rimWidth, 1.0, v);
  float3 rimColor = mix(float3(0.3, 0.43, 0.83), float3(0.49, 1.0, 1.0), front);
  lit = mix(lit, rimColor, rim * (0.35 + 0.45 * front));
  return half4(clamp(lit, 0.0, 1.0) * sampled.a, sampled.a);
}
"""

/**
 * The torn strip in flight: the pack's artwork with a reflection that follows the strip's turn
 * and twist, and its purple reverse where a column faces away. Below Android 13 the artwork is
 * drawn on the mesh unlit.
 */
internal class FlyingFoilMaterial(
    image: Bitmap,
    private val pack: Rect,
    private val width: Double,
    private val height: Double,
) {
    private val artwork = imageShader(image, pack)
    private val shader = runtimeShader("foil", FOIL_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val surface = FloatArray(MESH_COLUMNS * 4)

    init {
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setInputShader("artwork", artwork)
            shader.setRect("packRect", pack)
            shader.setFloatUniform("pixelScale", min(width / 393, height / 852).toFloat())
            paint.shader = shader
        } else {
            paint.shader = artwork
        }
    }

    fun update(positions: List<Point>, uv: List<Point>, time: Double, reducedMotion: Boolean) {
        if (shader == null || !SHADERS_AVAILABLE) return
        val last = min(MESH_COLUMNS, positions.size / 2) - 1
        val complete = last >= 0 && uv.size >= positions.size
        val globalX: Double
        val globalY: Double
        if (complete) {
            val firstTop = positions[0]
            val firstBottom = positions[1]
            val lastTop = positions[last * 2]
            val lastBottom = positions[last * 2 + 1]
            globalX = (lastTop.x + lastBottom.x - firstTop.x - firstBottom.x) * 0.5
            globalY = (lastTop.y + lastBottom.y - firstTop.y - firstBottom.y) * 0.5
        } else {
            globalX = pack.width
            globalY = 0.0
        }

        for (column in 0 until MESH_COLUMNS) {
            if (!complete || column > last) {
                surface[column * 4] = 1f
                surface[column * 4 + 1] = 0f
                surface[column * 4 + 2] = max(pack.height * 0.18, 1.0).toFloat()
                surface[column * 4 + 3] = 1f
                continue
            }
            val top = positions[column * 2]
            val bottom = positions[column * 2 + 1]
            val uvTop = uv[column * 2]
            val uvBottom = uv[column * 2 + 1]
            val before = max(0, column - 1)
            val after = min(last, column + 1)
            val beforeTop = positions[before * 2]
            val beforeBottom = positions[before * 2 + 1]
            val afterTop = positions[after * 2]
            val afterBottom = positions[after * 2 + 1]
            val tangentX = (afterTop.x + afterBottom.x - beforeTop.x - beforeBottom.x) * 0.5
            val tangentY = (afterTop.y + afterBottom.y - beforeTop.y - beforeBottom.y) * 0.5
            val tangentLength = max(hypot(tangentX, tangentY), 0.001)
            val edgeX = bottom.x - top.x
            val edgeY = bottom.y - top.y
            val textureHeight = max(uvBottom.y - uvTop.y, 0.001)
            val textureSpan = max(uv[after * 2].x - uv[before * 2].x, 0.001)
            val stretch = max(tangentLength / textureSpan, 0.001)
            val signedHeight = (tangentX * edgeY - tangentY * edgeX) / tangentLength
            val facing = clamp(signedHeight / (textureHeight * stretch), -1.0, 1.0)
            val bend =
                atan2(
                    globalX * tangentY - globalY * tangentX,
                    globalX * tangentX + globalY * tangentY,
                )
            surface[column * 4] = facing.toFloat()
            surface[column * 4 + 1] = bend.toFloat()
            surface[column * 4 + 2] = textureHeight.toFloat()
            surface[column * 4 + 3] = hypot(edgeX, edgeY).toFloat()
        }

        val t = if (reducedMotion) 0.0 else clamp(time, 0.0, 1000.0)
        shader.setFloatUniform(
            "material",
            interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_ROLL).toFloat(),
            interpolateKeyframes(t, MATERIAL_TIMES, MATERIAL_TWIST).toFloat(),
            clamp((t - 66.667) / 75, 0.0, 1.0).toFloat(),
            if (reducedMotion) 0.16f else clamp((t - 50) / 58.333, 0.0, 1.0).toFloat(),
        )
        shader.setFloatUniform("surface", surface)
    }

    fun draw(
        canvas: Canvas,
        meshes: MeshRenderer,
        positions: List<Point>,
        uv: List<Point>,
        opacity: Double,
        density: Float,
    ) {
        val alpha = alpha255(opacity)
        if (alpha == 0) return
        paint.alpha = alpha
        meshes.draw(canvas, positions, uv, FOIL_MESH_INDICES, paint, density)
    }
}
