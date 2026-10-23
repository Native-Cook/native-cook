package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// The page and shell share these stationary lamps, in widget coordinates.
internal val auraLightAgsl = """
uniform float2 origin;
uniform float cardWidth;
uniform float pixelRatio;
uniform float3 pageTone;
uniform float3 leftCore;
uniform float3 leftEdge;
uniform float3 lowCore;
uniform float3 lowEdge;
uniform float3 highTone;

float auraLamp(float2 p, float2 centre, float2 reach) {
  float2 q = (p - centre) / reach;
  return exp(-dot(q, q));
}

float3 auraField(float2 p, float softness) {
  float2 q = p / cardWidth;
  // The left lamp leans outwards as it falls, leaving the upper right dark.
  float2 leftPoint = float2(q.x + 0.24 * (q.y - 0.64), q.y);
  float leftWide = auraLamp(leftPoint, float2(-0.04, 0.64), float2(0.43, 0.64) + softness);
  float leftHot = auraLamp(leftPoint, float2(-0.04, 0.68), float2(0.36, 0.43) + softness);
  float lowWide = auraLamp(q, float2(0.95, 1.31), float2(0.83, 0.66) + softness);
  float lowHot = auraLamp(q, float2(0.91, 1.26), float2(0.48, 0.45) + softness);
  float highGlow = auraLamp(q, float2(-0.22, -0.02), float2(0.46, 0.43) + softness);
  float3 leftLight = mix(leftEdge, leftCore, leftHot) * leftWide;
  float3 lowLight = mix(lowEdge, lowCore, lowHot) * lowWide;
  return 1.0 - (1.0 - pageTone) * (1.0 - leftLight)
    * (1.0 - lowLight) * (1.0 - highTone * highGlow * 0.46);
}

// The lamps' centres, reaches and exposure, in widget widths: material
// settings of this shader, like the ones in aura-shader.ts.
// The exposed page receives the full lamps. The smoked face above keeps
// its quieter transmission, so lifting the page cannot wash out the pane.
float3 auraPageField(float2 p) {
  float2 q = p / cardWidth;
  float leftWide = auraLamp(q, float2(-0.06, 0.55), float2(0.44, q.y < 0.55 ? 0.90 : 0.68)) * 1.08;
  float leftHot = auraLamp(q, float2(-0.06, 0.85), float2(0.38, 0.34));
  float3 leftLight = mix(leftEdge, leftCore, leftHot) * leftWide;
  float poolLeft = auraLamp(q, float2(0.27, 1.10), float2(0.44, 0.26));
  float poolRight = auraLamp(q, float2(0.91, 1.12), float2(0.53, 0.28));
  // The floor spreads out below the contact pool on a tall screen.
  float fallWidth = mix(0.64, 1.10, smoothstep(1.15, 1.65, q.y));
  float fall = auraLamp(q, float2(0.90, 1.55), float2(fallWidth, 0.72));
  float3 pool = 1.0 - (1.0 - leftCore * poolLeft * 0.30)
    * (1.0 - lowCore * poolRight * 0.46) * (1.0 - lowCore * fall * 0.55);
  float ceiling = smoothstep(-0.10, 0.28, q.y);
  // The bare page is darker than the skin's page stop: the lamps lift it.
  float3 dark = pageTone * 0.62;
  return 1.0 - (1.0 - dark) * (1.0 - leftLight * ceiling) * (1.0 - pool * ceiling);
}

// One stationary value per physical pixel, within half an 8-bit level.
// Dark ramps stay smooth without putting visible grain on the lamps.
float auraDither(float2 p) {
  float2 q = floor(p * pixelRatio);
  return (fract(52.9829189 * fract(dot(q, float2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
}
"""

internal val auraPageAgsl = auraLightAgsl + """
half4 main(float2 xy) {
  float3 color = auraPageField(xy - origin) + auraDither(xy);
  return half4(clamp(color, 0.0, 1.0), 1.0);
}
"""
