package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

private const val BODY_SHADER =
    """
uniform shader artwork;
uniform float4 textureRect;
uniform float strength;

half4 main(float2 xy) {
  half4 sampled = artwork.eval(xy);
  float3 print = float3(sampled.rgb) / max(float(sampled.a), 0.001);
  float2 uv = (xy-textureRect.xy)/max(textureRect.zw,float2(0.001));

  // The intact source photograph contains a pink softbox crescent below its
  // upper-left seal. The opening reference loses that reflection as the front
  // turns away. Subtract only its additive energy in the same texture region.
  float flank = smoothstep(0.006,0.026,uv.x)
    * (1.0-smoothstep(0.105,0.235,uv.x));
  float upperPanel = smoothstep(0.158,0.175,uv.y)
    * (1.0-smoothstep(0.222,0.278,uv.y));
  // Registered reference crops measure excess RGB near [123,143,74]. Green
  // above the dark violet ink floor estimates that light, preserving stars,
  // printed detail and the rest of the character artwork outside the crescent.
  float deglare = max(print.g-0.10,0.0) * flank * upperPanel
    * clamp(strength,0.0,1.0);
  float3 color = max(print-deglare*float3(0.89,1.0,0.52),float3(0.0));
  return half4(color*sampled.a,sampled.a);
}
"""

/**
 * Texture-space light correction, so body deformation carries it with the ink. Below Android 13
 * the body is the artwork alone.
 */
internal class BodyMaterial(
    private val image: Bitmap,
) {
    private val shader = runtimeShader("body", BODY_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(
        canvas: Canvas,
        meshes: MeshRenderer,
        mesh: Mesh,
        textureRect: Rect,
        strength: Double,
        opacity: Double,
        density: Float,
    ) {
        val alpha = alpha255(opacity)
        if (alpha == 0) return
        val artwork = imageShader(image, textureRect)
        if (shader != null && SHADERS_AVAILABLE) {
            shader.setInputShader("artwork", artwork)
            shader.setRect("textureRect", textureRect)
            shader.setFloatUniform("strength", strength.toFloat())
            paint.shader = shader
        } else {
            paint.shader = artwork
        }
        paint.alpha = alpha
        meshes.draw(canvas, mesh.vertices, mesh.textures, BODY_MESH_INDICES, paint, density)
    }
}
