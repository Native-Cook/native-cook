package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

private val LINING_LOOKUP =
    (0 until MOUTH_LIGHT_SAMPLES - 1).joinToString("\n") { index ->
        "if (column <= ${index + 1}.0) return mix(liningSurface[$index], liningSurface[${index + 1}], column-$index.0);"
    }

private val MOUTH_SHADER =
    """
uniform float center;
uniform float secondCenter;
uniform float strength;
uniform float opening;
uniform float travel;
uniform float rim;
uniform float liningCenter;
uniform float liningLight;
uniform float sideLight;
uniform float liningTilt;
uniform float3 liningTint;
uniform float2 liningSurface[$MOUTH_LIGHT_SAMPLES];

float2 sampleLining(float u) {
  float column = u*${MOUTH_LIGHT_SAMPLES - 1}.0;
  $LINING_LOOKUP
  return liningSurface[${MOUTH_LIGHT_SAMPLES - 1}];
}

float lobe(float x, float width) {
  return exp(-x*x/(width*width));
}
half4 main(float2 uv) {
  float u = clamp(uv.x, 0.0, 1.0);
  float v = clamp(uv.y, 0.0, 1.0);
  // Surface pitch changes the direction reflected toward the viewer. The
  // small bend belongs to the laminate, so its reflections deform with its UVs.
  float bend = 0.016*sin(u*7.5 + travel*2.8)*(1.0-v);
  float pitch = (v - 0.5) * (0.085 + opening * 1.45)
    + travel * 0.04 + bend;
  float q = u + pitch;
  float broad = lobe(q - center, 0.24);
  if (rim < 0.5) {
    // Sample broad reflected lights through the curved sheet's surface angle.
    // The shallow creases stay in material coordinates; they bend the light,
    // rather than translating a painted stripe or scrolling a noise texture.
    float sheetBend = 0.014*sin(u*10.0 + 0.8)
      + 0.006*sin(u*24.0 - v*1.8);
    float2 localSurface = sampleLining(u);
    float reflected = u + (v-0.5)*(0.055 + opening*0.3)
      + sheetBend*(0.35 + 0.65*v) + liningTilt*(v-0.2)
      + localSurface.x*0.13 + localSurface.y*0.45;
    float d = reflected - liningCenter;
    float softLight = lobe(d,d < 0.0 ? 0.205 : 0.19);
    // A cool environment reflected in aluminum, including the dark left
    // flank and violet surroundings at the turned end. No flat gray fill.
    float3 color = mix(float3(0.145,0.155,0.205),float3(0.255,0.230,0.375),
      smoothstep(0.10,0.95,reflected));
    color *= 0.91 + 0.09*v;
    // The first shallow opening still shows the printed back of the wrapper.
    // Opening the wall rotates its silver face into view; the outer purple
    // laminate remains visible only along the lower folded return.
    float printedFacing = (1.0-smoothstep(0.025,0.039,opening))
      * smoothstep(0.36,0.74,u);
    color = mix(color,float3(0.30,0.025,0.65),printedFacing);
    // Thin-film color has a broad falloff and shares the same reflected
    // direction as the white light. Interpolate reflected energy instead
    // of adding saturated RGB lobes, which clipped blue into a flat ribbon.
    float filmAngle = d + (v-0.5)*0.026;
    float filmShift = clamp((1.0-liningTint.g)/0.40,0.0,1.0);
    float3 blueWing = mix(float3(0.26,0.72,0.94),float3(0.38,0.43,1.0),filmShift);
    float3 lightColor = mix(blueWing,liningTint,
      smoothstep(-0.16,-0.012,filmAngle));
    lightColor = mix(lightColor,float3(0.70,0.76,0.83),
      smoothstep(0.005,0.16,filmAngle));
    color = mix(color,lightColor,softLight*liningLight);
    // Different shallow facets catch the same environment at different
    // angles: quiet silver streaks, not equally bright rainbow stripes.
    float streak = lobe(reflected-liningCenter-0.055,0.012)*0.11
      + lobe(reflected-liningCenter-0.235,0.021)*0.12;
    color += streak*float3(0.72,0.82,0.94)*liningLight;
    // A second light catches the turned foil at a different incidence angle.
    // It shares the scene pose, while keeping its own direction and falloff.
    float side = lobe(reflected-secondCenter,0.036)*sideLight;
    color += side*float3(0.53,0.64,0.47);

    // The outer printed laminate turns back under the silver inside wall.
    // The return is wider at the exit, where the corner exposes its purple
    // outer face; it is not a purple pigment gradient across the interior.
    float sideTurn = smoothstep(0.925,0.987,u);
    color *= 1.0 - 0.30*sideTurn;
    float returnStart = 0.77 - 0.36*smoothstep(0.52,0.96,u);
    float printedReturn = smoothstep(returnStart,0.87,v);
    float3 laminate = float3(0.28,0.025,0.57)*(0.82 + 0.18*v)
      + softLight*float3(0.025,0.016,0.045);
    color = mix(color, laminate, printedReturn);

    // The folded end catches a subdued metallic glint only on its silver
    // face. A full-height white stripe would look like a seam on a rigid cap.
    float creaseCenter = 0.980 + (0.5-v)*0.003;
    float crease = lobe(u-creaseCenter,0.0018)*(0.25 + 0.40*strength)
      * (1.0-printedReturn);
    color = mix(color, float3(0.66,0.62,0.82), crease*0.20);
    color *= 1.0 - 0.28*smoothstep(0.989,1.0,u);
    return half4(clamp(color, 0.0, 1.0), 1.0);
  }
  float cyan = lobe(q - center + 0.06, 0.11);
  float pink = lobe(q - center - 0.025, 0.10);
  float white = lobe(q - center - 0.092, 0.046);
  float second = lobe(q - secondCenter, 0.065);
  float3 spectral = float3(0.14,0.74,0.88)*cyan
                  + float3(0.76,0.08,0.64)*pink
                  + float3(0.64,0.66,0.61)*white
                  + float3(0.28,0.22,0.38)*second;
  // A thin bonded cut edge: a white glint, cyan transmission and a soft violet
  // underside. They share one surface rather than four overlapping shapes.
  float3 color = mix(float3(0.84,0.96,0.99), float3(0.99,1.0,1.0), broad);
  color = mix(color, float3(0.06,0.80,1.0), smoothstep(0.35,0.67,v));
  color = mix(color, float3(0.24,0.10,0.64), smoothstep(0.63,0.90,v));
  color += spectral * strength * (0.28 + 0.55*broad);
  color *= 1.0 - 0.16*smoothstep(0.82,1.0,u);
  float alpha = 1.0-smoothstep(0.75,1.0,v);
  return half4(clamp(color,0.0,1.0)*alpha, alpha);
}
"""

/**
 * The silver inside wall of the open pack and the thin bonded rim along its cut, both lit from
 * one [MouthLighting]. Their texture coordinates are material UVs in 0..1. Below Android 13 the
 * wall is a plain cool gradient along its length and the rim a white-to-violet gradient across it.
 */
internal class MouthMaterial(
    private val rim: Boolean,
) {
    private val shader = runtimeShader("mouth", MOUTH_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tint = FloatArray(3)
    private val surface = FloatArray(MOUTH_LIGHT_SAMPLES * 2)

    init {
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setFloatUniform("rim", if (rim) 1f else 0f)
            paint.shader = shader
        } else if (rim) {
            paint.shader =
                linearGradient(
                    0.0, 0.0, 0.0, 1.0,
                    intArrayOf(
                        Color.rgb(233, 250, 253),
                        Color.rgb(15, 204, 255),
                        rgba(61, 26, 163, 0.75),
                        rgba(61, 26, 163, 0.0),
                    ),
                    floatArrayOf(0f, 0.6f, 0.8f, 1f),
                )
        } else {
            paint.shader =
                linearGradient(
                    0.0, 0.0, 1.0, 0.0,
                    intArrayOf(Color.rgb(37, 40, 52), Color.rgb(65, 59, 96)),
                    null,
                )
        }
    }

    fun draw(
        canvas: Canvas,
        meshes: MeshRenderer,
        vertices: List<Point>,
        textures: List<Point>,
        indices: ShortArray,
        lighting: MouthLighting,
        opacity: Double,
        density: Float,
    ) {
        val alpha = alpha255(opacity)
        if (alpha == 0) return
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setFloatUniform("center", lighting.center.toFloat())
            shader.setFloatUniform("secondCenter", lighting.secondCenter.toFloat())
            shader.setFloatUniform("strength", lighting.strength.toFloat())
            shader.setFloatUniform("opening", lighting.opening.toFloat())
            shader.setFloatUniform("travel", lighting.travel.toFloat())
            shader.setFloatUniform("liningCenter", lighting.liningCenter.toFloat())
            shader.setFloatUniform("liningLight", lighting.liningLight.toFloat())
            shader.setFloatUniform("sideLight", lighting.sideLight.toFloat())
            shader.setFloatUniform("liningTilt", lighting.liningTilt.toFloat())
            for (index in 0 until 3) tint[index] = lighting.liningTint[index].toFloat()
            shader.setFloatUniform("liningTint", tint)
            for (index in surface.indices) surface[index] = lighting.liningSurface[index].toFloat()
            shader.setFloatUniform("liningSurface", surface)
        }
        paint.alpha = alpha
        meshes.draw(canvas, vertices, textures, indices, paint, density)
    }
}
