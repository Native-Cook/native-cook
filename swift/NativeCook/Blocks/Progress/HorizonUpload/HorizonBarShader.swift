/// Original material shader, evaluated in points over premultiplied sRGB pixels.
enum HorizonBarShader {
  static let source = """

float uBarWidth;
float uProgress;
float uPower;
float uPulse;
float uPaused;
float uCalm;
float uShine;
float uMotion;
float uDone;
float uScale;
float uThickness;
float uBleed;
float3 uTrackRGB;
float3 uFillRGB;
float3 uBloomRGB;
float3 uDeepRGB;
float horizonTube(float2 bPoint, float bSpan) {
  float bRadius = min(uThickness * 0.5, bSpan * 0.5);
  float2 bInset = abs(bPoint - float2(uBleed + bSpan * 0.5, uBleed + uThickness * 0.5))
    - float2(max(0.0, bSpan * 0.5 - bRadius), uThickness * 0.5 - bRadius);
  return length(max(bInset, 0.0)) + min(max(bInset.x, bInset.y), 0.0) - bRadius;
}
float horizonBell(float bOffset) { return exp(-bOffset * bOffset); }
float4 main(float2 bPoint) {
  float bSpan = uBarWidth * clamp(uProgress, 0.0, 1.0);
  float bY = bPoint.y - uBleed - uThickness * 0.5;
  float bGroove = clamp(0.5 - horizonTube(bPoint, uBarWidth) * uScale, 0.0, 1.0);
  float bFillSdf = horizonTube(bPoint, max(bSpan, 0.001));
  float bVisible = smoothstep(0.0, 1.0, bSpan);
  float bFill = clamp(0.5 - bFillSdf * uScale, 0.0, 1.0) * bVisible;
  float bNear = exp(-pow(max(bFillSdf, 0.0), 2.0) / 50.0);
  float bBloomAlpha = bNear * mix(0.55, 0.35, uCalm) * uPower * (1.0 - uPaused) * bVisible;
  float3 bRGB = mix(uDeepRGB, uBloomRGB, bNear) * bBloomAlpha;
  float bAlpha = bBloomAlpha;
  float bTopShade = 1.0 - 0.45 * smoothstep(0.0, 2.0, -bY);
  float bLip = horizonBell((bY - uThickness * 0.5 + 0.35) / 0.28) * 0.18;
  float3 bGrooveRGB = uTrackRGB * 0.55 * bTopShade + uBloomRGB * bLip;
  bRGB = bRGB * (1.0 - bGroove) + bGrooveRGB * bGroove;
  bAlpha = bAlpha * (1.0 - bGroove) + bGroove;
  float bHot = exp(-bY * bY / 0.65);
  float bBeatPos = uBleed + (1.0 - uPulse) * bSpan;
  float bBeat = horizonBell((bPoint.x - bBeatPos) / 13.0) * 0.35
    * smoothstep(0.0, 0.08, uPulse) * uMotion * (1.0 - uDone) * (1.0 - uPaused);
  float bSweep = 0.0;
  if (uShine > 0.0 && uShine < 1.0) {
    bSweep = horizonBell((bPoint.x - uBleed - uShine * uBarWidth) / 13.0)
      * 0.6 * sin(3.14159265 * uShine) * uMotion;
  }
  float3 bCoreRGB = clamp(mix(uBloomRGB, uFillRGB, bHot) * (1.0 + bBeat + bSweep), 0.0, 1.0);
  float bCoreAlpha = bFill * (1.0 - 0.45 * uPaused);
  bRGB = bRGB * (1.0 - bCoreAlpha) + bCoreRGB * bCoreAlpha;
  bAlpha = bAlpha * (1.0 - bCoreAlpha) + bCoreAlpha;
  float bHeadX = bPoint.x - uBleed - max(2.0, bSpan - 2.0);
  float bCap = exp(-bHeadX * bHeadX / 3.2 - bY * bY / 4.0);
  float bFlare = pow(max(0.0, 1.0 - abs(bHeadX) / 12.0), 2.0) * exp(-bY * bY / 0.8);
  float bHeadAlpha = clamp(bCap * 0.4 + bFlare * (0.2 + uPulse * 0.65), 0.0, 0.95)
    * bVisible * (1.0 - uPaused) * (1.0 - uDone);
  bRGB = bRGB * (1.0 - bHeadAlpha) + uFillRGB * bHeadAlpha;
  bAlpha = bAlpha * (1.0 - bHeadAlpha) + bHeadAlpha;
  return float4(bRGB, bAlpha);
}
"""
}
