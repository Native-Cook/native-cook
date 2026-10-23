package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Xfermode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Constant indices keep the lookup a chain of comparisons, as in the source's material.
private val SURFACE_LOOKUP =
    (0 until PREVIEW_MESH_COLUMNS - 1).joinToString("\n") { index ->
        "if (column <= ${index + 1}.0) return mix(foldSurface[$index], foldSurface[${index + 1}], column - $index.0);"
    }
private val CROSS_LOOKUP = SURFACE_LOOKUP.replace("foldSurface", "foldCross")

private val FOLD_SHADER =
    """
uniform shader artwork;
uniform float4 packRect;
// Maximum projected compression, bent span, swipe direction, sheen strength.
uniform float4 fold;
// Texture height, compression, first-row stretch, and upper-edge slope.
uniform float4 foldSurface[$PREVIEW_MESH_COLUMNS];
// Projected distance from the crest at quarter, half and three-quarter rows,
// divided by texture height. Retain the folded cross-section beyond row one.
uniform float3 foldCross[$PREVIEW_MESH_COLUMNS];

float4 sampleFold(float column) {
  $SURFACE_LOOKUP
  return foldSurface[${PREVIEW_MESH_COLUMNS - 1}];
}

float3 sampleCross(float column) {
  $CROSS_LOOKUP
  return foldCross[${PREVIEW_MESH_COLUMNS - 1}];
}

half4 main(float2 xy) {
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);
  float u = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
  float column = acos(clamp(1.0-2.0*u, -1.0, 1.0)) / 3.14159265359 * ${PREVIEW_MESH_COLUMNS - 1}.0;
  float4 local = sampleFold(column);
  float v = clamp((xy.y - packRect.y) / max(local.x, 0.001), 0.0, 1.0);
  float d = fold.z > 0.0 ? u : 1.0-u;
  float bent = smoothstep(0.015, 0.17, fold.x);

  // The 2D projection loses the sheet's depth. Continue the bend's lighting
  // softly into its shoulder, as seen at f42–48, without moving the artwork.
  float shoulder = max(0.48, fold.y + 0.12);
  float across = 1.0-smoothstep(0.0, shoulder, d);
  float upper = 1.0-smoothstep(0.12, 0.92, v);
  float shade = bent * across * upper;
  float compression = max(local.y, fold.x * across * 0.48);
  float facing = clamp(1.0-1.45*compression*pow(max(1.0-v, 0.0), 0.45), 0.0, 1.0);

  // The photographed pink softbox is illumination, not printed ink. Remove
  // it only where the fold turns away; logos and the unbent panel retain color.
  float purpleInk = smoothstep(0.08, 0.28, print.b-print.g);
  float deglare = max(print.g-0.035, 0.0) * purpleInk * shade * 0.95;
  float3 unlit = max(print-deglare*float3(1.05, 0.95, 0.35),
    print*float3(0.32, 0.08, 0.40));
  float diffuse = 1.0-shade*(0.50+0.16*(1.0-facing));
  float3 lit = unlit * diffuse;

  // A broad satin reflection settles toward the foot of the bend as its
  // compression increases. It follows material UVs, so holding or reversing
  // a gesture holds or reverses the lighting too; there is no light timer.
  float sheenV = 0.60+fold.x*0.14-d*0.38;
  float sheenWidth = 0.20-0.065*fold.x;
  float band = exp(-pow((v-sheenV)/sheenWidth, 2.0));
  float crestCenter = 0.13+0.12*smoothstep(0.20, 0.65, fold.x);
  float crest = exp(-pow((d-crestCenter)/0.12, 2.0));
  float settling = 1.0-smoothstep(0.38, 0.73, fold.x)*0.28;
  float exposure = mix(0.13, 0.52, smoothstep(0.25, 0.68, fold.x));
  float reflection = band*crest*bent*settling*exposure*fold.w;
  lit = mix(lit, float3(0.95, 0.84, 0.80), reflection);

  // A restrained grazing reflection traces the rounded crest. It is inside
  // the material and never produces an emissive outline around the corner.
  float edge = (1.0-smoothstep(0.0, 0.022, v))*across*bent;
  lit += float3(0.10, 0.055, 0.17)*edge*fold.w;

  // At f57–66 the turned laminate starts exposing its purple reverse. This
  // is a narrow surface inside the curved silhouette, with a dark contact
  // crease separating it from the print, rather than a glowing stroke.
  float turned = smoothstep(0.56, 0.72, fold.x);
  float tipTaper = smoothstep(0.0, 0.065, d);
  float shoulderTaper = 1.0-smoothstep(fold.y*0.42, fold.y*0.88+0.001, d);
  float reverseExposure = turned*tipTaper*shoulderTaper;
  float reverseWidth = packRect.z*0.007*reverseExposure;
  // Convert material UV distance into distance normal to the projected edge.
  // Compensation keeps the reverse thin even where the folded mesh compresses.
  float3 crossing = sampleCross(column);
  float along = v < 0.125 ? v*max(local.z, 0.0)
    : v < 0.25 ? mix(0.125*max(local.z, 0.0), crossing.x, (v-0.125)*8.0)
    : v < 0.5 ? mix(crossing.x, crossing.y, (v-0.25)*4.0)
    : v < 0.75 ? mix(crossing.y, crossing.z, (v-0.5)*4.0)
    : mix(crossing.z, 1.0-local.y, (v-0.75)*4.0);
  float edgeDistance = max(along, 0.0)*local.x/sqrt(1.0+local.w*local.w);
  float acrossReverse = edgeDistance/max(reverseWidth, packRect.z*0.00001);
  float reverseMask = (1.0-smoothstep(0.78, 1.12, acrossReverse))*reverseExposure;
  float outerRidge = 1.0-smoothstep(0.05, 0.45, acrossReverse);
  float3 reverseColor = mix(float3(0.77, 0.40, 1.0),
    float3(0.97, 0.61, 1.0), outerRidge);
  lit = mix(lit, reverseColor, reverseMask);
  float contactCrease = smoothstep(0.85, 1.12, acrossReverse)
    *(1.0-smoothstep(1.20, 1.75, acrossReverse))*reverseExposure;
  lit *= 1.0-contactCrease*0.28;
  return half4(clamp(lit, 0.0, 1.0)*sampled.a, sampled.a);
}
"""

/**
 * The attached foil while it folds: the pack's artwork relit from the folded mesh itself, so a
 * held gesture holds the lighting. Below Android 13 the artwork is drawn on the mesh unlit.
 */
internal class FoldMaterial(
    image: Bitmap,
    private val pack: Rect,
) {
    private val artwork = imageShader(image, pack)
    private val shader = runtimeShader("fold", FOLD_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val foldSurface = FloatArray(PREVIEW_MESH_COLUMNS * 4)
    private val foldCross = FloatArray(PREVIEW_MESH_COLUMNS * 3)

    init {
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setInputShader("artwork", artwork)
            shader.setRect("packRect", pack)
            paint.shader = shader
        } else {
            paint.shader = artwork
        }
    }

    /** Derives the material's uniforms from the folded [positions] and their [uv]. */
    fun update(positions: List<Point>, uv: List<Point>, swipeDirection: Int, reducedMotion: Boolean) {
        if (shader == null || !SHADERS_AVAILABLE) return
        if (positions.size < PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS || uv.size < positions.size) return
        var compression = 0.0
        var bentArea = 0.0
        var previousU = 0.0
        var previousBend = 0.0
        for (column in 0 until PREVIEW_MESH_COLUMNS) {
            val start = column * PREVIEW_MESH_ROWS
            val end = start + PREVIEW_MESH_ROWS - 1
            val top = positions[start]
            val bottom = positions[end]
            val uvTop = uv[start]
            val uvBottom = uv[end]
            val textureHeight = max(uvBottom.y - uvTop.y, 0.001)
            val local = if (textureHeight > 0.001) clamp(1 - (bottom.y - top.y) / textureHeight, 0.0, 0.95) else 0.0
            val nextRow = positions[start + 1]
            val nextUV = uv[start + 1]
            val uvRowHeight = nextUV.y - uvTop.y
            val rowStretch = if (uvRowHeight > 0.001) (nextRow.y - top.y) / uvRowHeight else 1.0
            val before = positions[max(0, column - 1) * PREVIEW_MESH_ROWS]
            val after = positions[min(PREVIEW_MESH_COLUMNS - 1, column + 1) * PREVIEW_MESH_ROWS]
            val edgeSlope = if (abs(after.x - before.x) > 0.001) (after.y - before.y) / (after.x - before.x) else 0.0
            val materialU = (uvTop.x - pack.x) / pack.width
            val bend = sqrt(local)
            if (column > 0) bentArea += (bend + previousBend) * 0.5 * abs(materialU - previousU)
            previousU = materialU
            previousBend = bend
            compression = max(compression, local)
            foldSurface[column * 4] = textureHeight.toFloat()
            foldSurface[column * 4 + 1] = local.toFloat()
            foldSurface[column * 4 + 2] = rowStretch.toFloat()
            foldSurface[column * 4 + 3] = edgeSlope.toFloat()
            for (sample in 0 until 3) {
                val point = positions[start + (sample + 1) * 2]
                foldCross[column * 3 + sample] = max((point.y - top.y) / textureHeight, 0.0).toFloat()
            }
        }
        // Integrate the sampled bend using its actual, nonuniform column spacing.
        // A last-bent-column cutoff would make the shadow jump as the fold widens.
        val extent = if (compression > 0) clamp(2 * bentArea / sqrt(compression), 0.0, 1.0) else 0.0
        shader.setFloatUniform(
            "fold",
            compression.toFloat(),
            extent.toFloat(),
            swipeDirection.toFloat(),
            if (reducedMotion) 0.4f else 1f,
        )
        shader.setFloatUniform("foldSurface", foldSurface)
        shader.setFloatUniform("foldCross", foldCross)
    }

    fun draw(
        canvas: Canvas,
        meshes: MeshRenderer,
        positions: List<Point>,
        uv: List<Point>,
        opacity: Double,
        blend: Xfermode?,
        density: Float,
    ) {
        val alpha = alpha255(opacity)
        if (alpha == 0) return
        paint.alpha = alpha
        paint.xfermode = blend
        meshes.draw(canvas, positions, uv, PREVIEW_FOIL_MESH_INDICES, paint, density)
    }
}
