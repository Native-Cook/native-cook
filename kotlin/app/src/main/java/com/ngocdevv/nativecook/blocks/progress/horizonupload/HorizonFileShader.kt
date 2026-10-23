// Exact RN SkSL translation: point coordinates, constants and all light/material terms preserved.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

internal val horizonFileAgsl = """
uniform shader fAsset;
uniform float fPower;
uniform float fPulse;
uniform float fOpaque;
uniform float fGlint;
uniform float fSheet;

float3 fLit(float3 fColor) {
  float fK = sqrt(clamp((fPower - 0.06) / 0.94, 0.0, 1.0));
  float fGrey = dot(fColor, float3(0.2126, 0.7152, 0.0722));
  return mix(float3(fGrey * 0.87, fGrey * 0.94, fGrey * 1.12), fColor, 0.50 + 0.50 * fK)
    * (0.60 + 0.40 * fK);
}
half4 main(float2 fXY) {
  half4 fPixel = fAsset.eval(fXY);
  float fAlpha = fPixel.a;
  if (fAlpha < 0.001) return half4(0.0);
  float3 fColor = fPixel.rgb / fAlpha;
  // Clear glass still reflects the sweep. Its silhouette has no baked shadow,
  // so low-alpha sheet pixels are material, unlike the badge contact shadow.
  float fBody = fSheet > 0.5 ? smoothstep(0.015, 0.20, fAlpha) : smoothstep(0.3, 0.7, fAlpha);
  if (fSheet > 0.5) {
    float2 fPoint = fXY - float2(8.0);
    float fBand = fPoint.y + 0.42 * (fPoint.x - 45.0) - mix(151.0, -40.0, fGlint);
    float fGate = smoothstep(0.0, 0.10, fGlint) * (1.0 - smoothstep(0.90, 1.0, fGlint));
    float fShine = fGate * (0.23 * exp(-fBand * fBand / 80.0) + 0.25 * exp(-fBand * fBand / 5.0));
    float fRim = min(min(fPoint.x, 90.0 - fPoint.x), min(fPoint.y, 112.0 - fPoint.y));
    fColor *= 1.0 + 0.06 * fPulse * exp(-max(fRim, 0.0) / 5.0) * fBody;
    fColor += float3(0.72, 0.87, 1.0) * fShine * fBody;
  }
  // Flatten the transparent material onto the pane when transparency is
  // reduced, keeping the image's anti-aliased silhouette and soft shadow.
  if (fOpaque > 0.5) {
    fColor = mix(fColor, fColor * fAlpha + float3(13.0, 20.0, 38.0) / 255.0 * (1.0 - fAlpha), fBody);
    fAlpha = mix(fAlpha, 1.0, fBody);
  }
  return half4(clamp(fLit(fColor), 0.0, 1.0) * fAlpha, fAlpha);
}
"""
