package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.content.res.AssetManager
import android.graphics.*
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class IslandGlassOrbRelight(assets: AssetManager) {
    private val effect=RuntimeShader(SOURCE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=effect }
    init {
        listOf("uRing" to "igo_ring_matte", "uSpec" to "igo_ring_spec", "uFloor" to "igo_floor_matte").forEach { (uniform,name) ->
            val bitmap=assets.open("islandglassorb/islandglassorb_$name.png").use { BitmapFactory.decodeStream(it) }
            effect.setInputShader(uniform,BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setScale(402f/bitmap.width,874f/bitmap.height) }); filterMode=BitmapShader.FILTER_MODE_LINEAR })
        }
    }
    fun draw(c: Canvas,f: IslandGlassOrbFrame,t: Double,scale: Double) {
        val save=c.save(); c.translate((201*(1-scale)).toFloat(),(276*(1-scale)).toFloat()); c.scale(scale.toFloat(),scale.toFloat())
        effect.setFloatUniform("uBand",f.bandY.toFloat(),f.bandA.toFloat(),f.floorY.toFloat(),f.floorA.toFloat())
        effect.setFloatUniform("uT",t.toFloat()); effect.setFloatUniform("uAmp",f.causticAmp.coerceIn(0.0,1.0).toFloat())
        for(mode in 0..1) {
            if(mode==0 && f.bandA+f.floorA<=.001)continue
            effect.setFloatUniform("uHue",f.bandHue.toFloat(),f.floorHue.toFloat(),mode.toFloat(),(f.pill.coerceIn(0.0,1.0)*(1-f.ban.coerceIn(0.0,1.0))).toFloat())
            paint.blendMode=if(mode==0)BlendMode.COLOR else BlendMode.PLUS
            c.drawRect(0f,230f,402f,750f,paint)
        }
        c.restoreToCount(save)
    }
    companion object {
        private const val SOURCE="""
half3 toOut(half3 c) { return c; }
uniform shader uRing;
uniform shader uSpec;
uniform shader uFloor;
uniform float4 uBand;  // band y, band strength, floor y, floor strength
uniform float4 uHue;   // band hue, floor hue (spectrum positions), mode (0 colour layer, 1 light layer), thinking 0..1
uniform float uT;
uniform float uAmp;
const float TAU = 6.2831853;
float sq(float x) { return x * x; }
half3 spectrum(float x) {
  float u = fract(x) * 5.0;
  float i = floor(u);
  float f = smoothstep(0.0, 1.0, fract(u));
  half3 a = i < 1.0 ? half3(1,.353,.122) : i < 2.0 ? half3(.961,.804,.588) : i < 3.0 ? half3(.392,.957,.976) : i < 4.0 ? half3(.169,.357,1) : half3(1,.176,.541);
  half3 b = i < 1.0 ? half3(.961,.804,.588) : i < 2.0 ? half3(.392,.957,.976) : i < 3.0 ? half3(.169,.357,1) : i < 4.0 ? half3(1,.176,.541) : half3(1,.353,.122);
  return mix(a, b, f);
}
float band(float y, float yc) { return 1.0 - smoothstep(30.0, 70.0, abs(y - yc)); } // 140 pt tall
half4 main(float2 p) {
  float ring = uRing.eval(p).r;
  float spec = uSpec.eval(p).r;
  float flo = uFloor.eval(p).r;
  float bandP = band(p.y, uBand.x) * uBand.y;
  float floorP = band(p.y, uBand.z) * uBand.w;
  half3 hueB = toOut(spectrum(uHue.x));
  if (uHue.z < 0.5) {
    // the light follows the ridges: full on and beside a ridge, a quarter of it on the plain glass between them
    float ridgeNear = 0.0;
    if (bandP > 0.001) {
      float r1 = uSpec.eval(p + float2(5.0, 0.0)).r + uSpec.eval(p - float2(5.0, 0.0)).r + uSpec.eval(p + float2(0.0, 5.0)).r + uSpec.eval(p - float2(0.0, 5.0)).r;
      float r2 = uSpec.eval(p + float2(11.0, 0.0)).r + uSpec.eval(p - float2(11.0, 0.0)).r + uSpec.eval(p + float2(0.0, 11.0)).r + uSpec.eval(p - float2(0.0, 11.0)).r;
      ridgeNear = clamp(max(spec, max(0.35 * r1, 0.28 * r2)) * 1.8, 0.0, 1.0);
    }
    float aRing = 0.30 * ring * (0.25 + 0.75 * ridgeNear) * bandP;
    float aFloor = 0.30 * flo * floorP;
    half3 hueF = toOut(spectrum(uHue.y));
    float a = clamp(aRing + aFloor, 0.0, 0.7);
    return half4(hueB * aRing + hueF * aFloor, a);
  }
  float3 add = float3(hueB) * 0.9 * spec * bandP;
  // the floor's caustic shimmers: two warped sine fields (38 and 61 pt, 15.2 and 12.2 pt/s in opposite directions, whole
  // numbers of cycles per 10 s loop), #DFF5C8 at 0.10 × matte × (1 + 1.5 A); while the assistant thinks it breathes with the
  // spinner (one turn a second) and is brighter, so the pill's second and a half is never still
  float think = uHue.w;
  float2 w1 = p + 6.0 * float2(sin(TAU * (p.y / 53.0) + TAU * 0.3 * uT), cos(TAU * (p.x / 47.0) - TAU * 0.2 * uT));
  float s1 = sin(TAU * (dot(w1, float2(0.94, 0.34)) / 38.0 - 0.4 * uT));
  float s2 = sin(TAU * (dot(w1, float2(-0.30, 0.95)) / 61.0 + 0.2 * uT));
  float field = 0.55 + 0.225 * (s1 + s2);
  float gain = 1.0 + 1.5 * uAmp + think * (4.2 + 1.6 * sin(TAU * uT));
  add += float3(0.875, 0.961, 0.784) * 0.10 * flo * field * gain;
  // the ridges breathe too while it thinks
  add += float3(1.0) * 0.26 * think * spec * (0.5 + 0.5 * sin(TAU * uT - 1.2));
  // one 60 pt glint runs along the ridges at 90 pt/s every 3.33 s, white at 0.35 (0.8 while thinking)
  float g = fract(uT * 0.3);
  float sPos = 360.0 + 300.0 * g;
  float gl = (1.0 - smoothstep(10.0, 30.0, abs(dot(p, float2(0.54, 0.84)) - sPos))) * smoothstep(0.0, 0.08, g) * (1.0 - smoothstep(0.92, 1.0, g));
  add += float3(1.0) * (0.35 + 0.45 * think) * spec * gl;
  add = min(add, float3(1.0));
  return half4(half3(toOut(half3(add))), max(add.r, max(add.g, add.b)));
}
"""
    }
}
