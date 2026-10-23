package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// Smoked shell, cushion, neck, drop, landing, waves and pillows.
// Term-for-term AGSL port of aura-shader.ts; material constants are expanded.
internal val auraShellAgsl = auraLightAgsl + """
uniform float shellRadius;
uniform float bleed;
uniform float opaque;
uniform float shellClear;
uniform float3 shellTone;
uniform float3 lightTone;
uniform float4 heroRect;
uniform float2 heroRadii;
uniform float2 lipShape;
uniform float lipXs[5];
uniform float3 heroCore;
uniform float3 heroMid;
uniform float3 heroLeft;
uniform float3 heroEdge;
uniform float4 paneRect;
uniform float paneRadius;
uniform float4 paneFill;
uniform float4 paneLine;
uniform float4 actionRects[4];
uniform float pressValues[4];
uniform float3 pillowTop;
uniform float3 pillowMid;
uniform float3 pillowLow;
uniform float3 pillowShine;
uniform float3 accentTop;
uniform float3 accentMid;
uniform float3 accentLow;
uniform float3 accentShine;
uniform float4 shadowSizes;
uniform float4 surfaceSizes;
uniform float4 lipPoses[5];
uniform float4 liquidState;
uniform float4 dropPose;
uniform float4 dropShape;
uniform float4 neckPose;
uniform float4 sendFx;
uniform float4 landing;
uniform float4 liquidOptics;
uniform float neckJoin;
uniform float4 liquidBounds;
uniform float3 dropCast;
uniform float4 castBounds;
uniform float2 poolWave;

bool auraInside(float2 p, float4 bounds) {
  return p.x >= bounds.x && p.y >= bounds.y && p.x <= bounds.z && p.y <= bounds.w;
}

// Squaring explicitly matters: pow of a negative base is undefined in SkSL,
// even with an even exponent, and cuts a Gaussian in half on some backends.
float auraBell(float v) {
  return exp(-v * v);
}

float auraUnion(float a, float b, float softness) {
  if (softness <= 0.0) return min(a, b);
  float h = max(softness - abs(a - b), 0.0) / softness;
  return min(a, b) - h * h * softness * 0.25;
}

// The root opens with depth; the curved shoulders meet a finite waist.
// After closure, round tips withdraw into their pools. Returning the cross
// section with its distance lets the light use the very same neck profile.
float3 auraNeck(float2 p) {
  float2 root = neckPose.xy;
  float2 endPoint = dropPose.xy;
  float span = max(endPoint.y - root.y, liquidState.w * 0.01);
  float t = clamp((p.y - root.y) / span, 0.0, 1.0);
  float cx = mix(root.x, endPoint.x, t);
  float waist = neckPose.z * smoothstep(0.45, 1.0, dropShape.w);
  float shoulder = (2.0 * t - 1.0);
  float neckWidth = waist + (liquidOptics.x - waist) * shoulder * shoulder;
  float slope = 4.0 * (liquidOptics.x - waist) * shoulder / span;
  float bodyD = (abs(p.x - cx) - neckWidth) / sqrt(1.0 + slope * slope);
  bodyD = max(bodyD, max(root.y - p.y, p.y - endPoint.y));
  if (dropShape.w < 0.45) {
    float retreat = 1.0 - dropShape.w / 0.45;
    float midY = (root.y + endPoint.y) * 0.5;
    float cap = liquidOptics.x * retreat;
    float gap = span * 0.5 * retreat;
    float topY = midY - gap - cap;
    float lowY = midY + gap + cap;
    float topX = mix(root.x, endPoint.x, clamp((topY - root.y) / span, 0.0, 1.0));
    float lowX = mix(root.x, endPoint.x, clamp((lowY - root.y) / span, 0.0, 1.0));
    float topD = length(float2(p.x - topX, max(p.y - topY, 0.0))) - cap;
    float lowD = length(float2(p.x - lowX, min(p.y - lowY, 0.0))) - cap;
    bodyD = max(bodyD, min(topD, lowD));
  }
  return float3(bodyD, cx, neckWidth);
}

float auraBox(float2 p, float2 halfSize, float r) {
  float2 q = abs(p) - halfSize + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The corner reaches 1.5 of the measured radius along each edge.
// A cubic corner meets the straight edge with zero curvature. Its longer
// shoulder keeps the measured radius's diagonal cut while easing in early.
// Dividing by the gradient restores point-sized hairlines at every angle.
float auraSoftBox(float2 p, float2 halfSize, float r) {
  float corner = r * 1.5;
  float2 q = abs(p) - halfSize + corner;
  float2 v = max(q, 0.0);
  float cubic = max(pow(v.x * v.x * v.x + v.y * v.y * v.y, 1.0 / 3.0), 0.0001);
  float slope = length(v * v) / (cubic * cubic);
  return (cubic - corner) / max(slope, 0.75) + min(max(q.x, q.y), 0.0);
}

float2 auraSoftNormal(float2 p, float2 halfSize, float r) {
  float2 q = max(abs(p) - halfSize + r * 1.5, 0.0);
  float2 polarity = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 || q.y > 0.0) return polarity * normalize(q * q);
  return abs(p.x) > abs(p.y) ? float2(polarity.x, 0.0) : float2(0.0, polarity.y);
}

float auraCover(float d) {
  return clamp(0.5 - d * pixelRatio, 0.0, 1.0);
}

float auraRim(float d, float w) {
  return (auraCover(d) - auraCover(d + w)) / max(auraCover(d), 0.0001);
}

float2 auraNormal(float2 p, float2 halfSize, float r) {
  float2 q = abs(p) - halfSize + r;
  float2 polarity = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 && q.y > 0.0) return polarity * normalize(q);
  return q.x > q.y ? float2(polarity.x, 0.0) : float2(0.0, polarity.y);
}

float auraCushion(float2 p, bool nearLiquid, float dropRadial, float neckD) {
  float2 rel = p - heroRect.xy - heroRect.zw * 0.5;
  float r = rel.y < 0.0 ? heroRadii.x : heroRadii.y;
  if (liquidState.z > 0.0 && sendFx.x != 0.0) {
    rel.y -= sendFx.x * auraBell((p.x - neckPose.x) / (neckPose.w * 2.0))
      * smoothstep(heroRect.w * 0.25, heroRect.w * 0.5, rel.y);
  }
  float d = auraSoftBox(rel, heroRect.zw * 0.5, r);
  // Keep the resting arithmetic intact, including its antialiasing.
  if (liquidState.x == 0.0) {
    for (int i = 0; i < 5; ++i) {
      float hole = length(p - float2(lipXs[i], lipShape.x)) - lipShape.y;
      d = max(d, -hole);
    }
    return d;
  }
  float holes = -cardWidth;
  for (int i = 0; i < 5; ++i) {
    float4 friendPose = lipPoses[i];
    float reach = max(friendPose.z, lipShape.y + friendPose.w) + neckPose.w;
    float2 offset = p - friendPose.xy;
    if ((friendPose.z > 0.0 || friendPose.w > 0.0) && abs(offset.x) <= reach && abs(offset.y) <= reach) {
      float radial = length(offset);
      if (friendPose.w > 0.0) {
        float wrapD = radial - lipShape.y - friendPose.w;
        d = auraUnion(d, wrapD, neckPose.w * min(friendPose.w / liquidOptics.y, 1.0));
      }
      if (friendPose.z > 0.0) holes = max(holes, friendPose.z - radial);
    }
  }
  if (nearLiquid && liquidOptics.z > 0.0) {
    float metric = min(dropShape.x, dropShape.y);
    float wrapJoin = min((dropPose.z - dropPose.w) / liquidOptics.y, 1.0);
    d = auraUnion(d, (dropRadial - dropPose.z) * metric, neckPose.w * dropShape.w * wrapJoin);
    if (dropShape.w > 0.0 && neckJoin > 0.0) {
      float connected = auraUnion(d, neckD, neckPose.w * smoothstep(0.0, 0.45, dropShape.w));
      d = mix(d, connected, neckJoin);
    }
    holes = max(holes, (dropPose.w - dropRadial) * metric);
  }
  d = max(d, holes);
  return d;
}

half4 main(float2 xy) {
  float2 p = xy - origin;
  float2 halfCard = float2(cardWidth * 0.5);
  float shellD = auraSoftBox(p - halfCard, halfCard, shellRadius);
  float shellCoverage = auraCover(shellD);
  float castD = auraSoftBox(p - halfCard - float2(0.0, shadowSizes.x), halfCard, shellRadius);
  float shadowAlpha = exp(-pow(max(castD, 0.0) / shadowSizes.y, 2.0)) * 0.48;
  // Black premultiplied shadow, gone before the canvas boundary.
  float outside = max(shellD, 0.0);
  shadowAlpha *= (0.16 + 0.84 * smoothstep(0.52, 1.0, p.y / cardWidth))
    * (1.0 - smoothstep(bleed * 0.65, bleed, outside));
  if (shellCoverage <= 0.0) return half4(0.0, 0.0, 0.0, shadowAlpha);

  float2 shellN = auraSoftNormal(p - halfCard, halfCard, shellRadius);
  float depth = max(-shellD, 0.0);
  float3 glass = shellTone;
  if (opaque < 0.5) {
    float2 bent = p - shellN * shellRadius * 0.11 * exp(-depth / (shellRadius * 0.16));
    glass = shellTone * (1.0 - shellClear * 0.25) + auraField(bent, 0.025) * shellClear;
  }
  float shellCatch = pow(max(dot(shellN, float2(-0.60, -0.80)), 0.0), 2.0);
  glass = mix(glass, lightTone, (0.035 + 0.12 * shellCatch) * auraRim(shellD, surfaceSizes.z)
    + 0.022 * shellCatch * exp(-depth / (shellRadius * 0.12)));
  glass *= 1.0 - 0.13 * max(dot(shellN, float2(0.60, 0.80)), 0.0) * auraRim(shellD, surfaceSizes.z * 2.0);

  // Reject the whole moving region before measuring an ellipse or neck.
  // Idle pixels retain the original five bites and cushion arithmetic.
  bool nearLiquid = false;
  float dropRadial = cardWidth;
  float3 neckSection = float3(cardWidth, 0.0, 1.0);
  float contactAlpha = 0.0;
  if (liquidState.y >= 0.0 && dropShape.z > 0.0) {
    nearLiquid = auraInside(p, liquidBounds);
    if (nearLiquid) {
      dropRadial = length((p - dropPose.xy) / dropShape.xy);
      if (liquidOptics.z > 0.0 && dropShape.w > 0.0 && neckJoin > 0.0) neckSection = auraNeck(p);
    }
    if (auraInside(p, castBounds)) {
      float2 castPoint = (p - dropPose.xy - float2(0.0, dropCast.x)) / dropShape.xy;
      float castEdge = max(length(castPoint) - dropPose.z, 0.0);
      contactAlpha = auraBell(castEdge / dropCast.y) * dropCast.z
        * (1.0 - smoothstep(dropCast.y, dropCast.y * 2.0, castEdge));
    }
  }
  float heroD = auraCushion(p, nearLiquid, dropRadial, neckSection.x);
  float heroCoverage = auraCover(heroD);
  float3 underCushion = glass;
  float3 liquidColor = glass;
  glass *= 1.0 - contactAlpha;
  if (heroCoverage > 0.0) {
    float2 uv = (p - heroRect.xy) / heroRect.zw;
    float central = auraLamp(uv, float2(0.46, 0.02), float2(0.34, 0.48));
    // The crown keeps its light; below it the corners turn away from the lamp.
    float lower = smoothstep(0.32, 0.88, uv.y);
    float rightFall = 1.0 - smoothstep(0.72, 0.97, uv.x) * smoothstep(0.30, 0.58, uv.y);
    float across = auraLamp(uv, float2(0.47, 0.35), float2(0.33, 1.0));
    float leftLift = (1.0 - smoothstep(0.0, 0.70, uv.x)) * (1.0 - lower * 0.76);
    float3 cushion = mix(heroEdge, heroLeft, leftLift);
    cushion = mix(
      cushion,
      heroMid,
      auraLamp(uv, float2(0.48, 0.34), float2(0.46, 0.70)) * 0.54 * mix(1.0, across * 0.45, lower) * rightFall
    );
    cushion = mix(cushion, heroCore, central * mix(1.0, across, lower) * rightFall);
    float edgeDepth = max(-heroD, 0.0);
    cushion *= 1.0 - 0.10 * exp(-edgeDepth / (heroRect.w * 0.028)) * smoothstep(0.38, 0.95, uv.y);
    cushion = mix(cushion, lightTone, 0.16 * (1.0 - smoothstep(0.02, 0.64, uv.y)) * auraRim(heroD, surfaceSizes.z));
    if (liquidState.x > 0.0) {
      cushion *= sendFx.y;
      // The wave stays in the original cushion, never in a hanging drop.
      // Both the bounding box and the surface coverage precede its radius.
      if (poolWave.y > 0.0 && auraInside(p, float4(heroRect.xy, heroRect.xy + heroRect.zw))) {
        float waveRadial = length(p - neckPose.xy);
        float crest = auraBell((waveRadial - poolWave.x) / (3.0 * liquidState.w));
        float trough = auraBell((waveRadial - poolWave.x + 4.0 * liquidState.w) / (5.0 * liquidState.w));
        cushion *= 1.0 - trough * poolWave.y * 0.025;
        cushion = mix(cushion, heroCore, crest * poolWave.y * 0.10);
      }
      if (nearLiquid) {
        // Light crosses the visible annulus, not the hidden centre of the
        // face. Its outer crescent is broad; the hole has a soft inner occlusion.
        float2 dq = (p - dropPose.xy) / dropShape.xy;
        float2 radialN = dq / max(dropRadial, liquidState.w * 0.01);
        float towardLamp = dot(radialN, float2(-0.6, -0.8));
        float band = (dropRadial - dropPose.w) / max(dropPose.z - dropPose.w, liquidState.w * 0.01);
        float crown = smoothstep(-0.15, 0.95, towardLamp);
        float curved = auraBell((band - 0.64) / 0.36);
        float3 dropLight = mix(heroMid, heroEdge, smoothstep(-0.1, 0.9, -towardLamp));
        dropLight = mix(dropLight, heroCore, crown * 0.78);
        dropLight = mix(dropLight, lightTone, crown * crown * curved * 0.50);
        dropLight *= 1.0 - 0.14 * smoothstep(0.72, 1.0, band) * (1.0 - crown);
        dropLight *= 1.0 - 0.16 * auraBell(max(band, 0.0) / 0.22);
        // Only the joined surface has a rim. A circular rim through the neck
        // would etch a seam across what must read as one continuous liquid.
        float outerRim = auraRim(heroD, surfaceSizes.z) * smoothstep(0.64, 1.0, band);
        dropLight = mix(dropLight, lightTone, outerRim * crown * 0.28);
        float3 localLight = cushion;
        if (liquidOptics.z > 0.0 && dropShape.w > 0.0 && neckJoin > 0.0) {
          float flank = (p.x - neckSection.y) / max(neckSection.z, liquidState.w);
          float3 neckLight = mix(heroCore, heroEdge, smoothstep(-0.9, 0.9, flank));
          neckLight = mix(neckLight, lightTone, auraBell((flank - -0.42) / 0.42) * 0.22);
          float rootFade = smoothstep(neckPose.y - neckPose.w, neckPose.y + neckPose.w, p.y);
          localLight = mix(localLight, neckLight, rootFade * neckJoin * dropShape.w);
        }
        float bodyBlend = 1.0 - smoothstep(dropPose.z - neckPose.w * 0.2, dropPose.z + neckPose.w * 0.4, dropRadial);
        localLight = mix(localLight, dropLight, bodyBlend);
        cushion = mix(cushion, localLight, liquidOptics.w);
      }
    }
    liquidColor = cushion;
    glass = mix(glass, cushion, heroCoverage);
  }

  float2 paneRel = p - paneRect.xy - paneRect.zw * 0.5;
  float paneD = auraBox(paneRel, paneRect.zw * 0.5, paneRadius);
  float paneCoverage = auraCover(paneD);
  if (paneCoverage > 0.0) {
    float paneY = clamp((p.y - paneRect.y) / paneRect.w, 0.0, 1.0);
    float3 paneBase = liquidState.y >= 0.0 ? underCushion : glass;
    float3 paneColor = mix(paneBase, paneFill.rgb, paneFill.a);
    paneColor = mix(paneColor, paneLine.rgb, paneLine.a * landing.z * (1.0 - paneY * 0.667) * auraRim(paneD, surfaceSizes.w));
    if (sendFx.w > 0.0) {
      float radial = length(p - landing.xy);
      float crest = auraBell((radial - sendFx.z) / (2.2 * liquidState.w));
      float trough = auraBell((radial - sendFx.z + 3.0 * liquidState.w) / (3.5 * liquidState.w));
      float3 rippleTone = mix(heroCore, lightTone, 1.0 - sendFx.w);
      paneColor *= 1.0 - trough * sendFx.w * 0.08;
      paneColor = mix(paneColor, rippleTone, crest * sendFx.w * 0.24);
      // Absorbed light spreads under the pane as the meniscus sinks into it.
      float spread = (1.0 - dropShape.z) * dropPose.z;
      float pool = auraLamp(p, landing.xy, float2(dropPose.z + spread, dropPose.z * 0.55 + spread));
      paneColor = mix(paneColor, heroMid, pool * (1.0 - dropShape.z) * sendFx.w * 0.30);
    }
    paneColor *= 1.0 - contactAlpha;
    glass = mix(glass, paneColor, paneCoverage);
    if (liquidOptics.z > 0.0 && dropShape.z > 0.0) {
      glass = mix(glass, liquidColor, heroCoverage * dropShape.z);
    }
  }

  // Above the button band even the widest accent glow is invisible.
  if (p.y > actionRects[0].y - shadowSizes.w * 8.0) {
    for (int i = 0; i < 4; ++i) {
      float held = clamp(pressValues[i], 0.0, 1.0);
      float4 frame = actionRects[i];
      float2 halfPillow = frame.zw * 0.5 - surfaceSizes.x * held;
      float2 rel = p - frame.xy - frame.zw * 0.5;
      float pillowD = auraBox(rel, halfPillow, halfPillow.y);
      if (landing.w > 0.0 && i >= 2) {
        // Facing cheeks reach across half the gap. Each keeps its own glass;
        // a smooth union makes the meeting point a bulge, never a stuck-on dot.
        float facing = i == 2 ? 1.0 : -1.0;
        float cheekD = length(rel - float2(facing * (halfPillow.x - halfPillow.y), 0.0))
          - halfPillow.y;
        float cheekBlend = landing.w * auraBell(rel.y / (halfPillow.y * 0.56));
        pillowD = auraUnion(pillowD, cheekD, cheekBlend);
      }
      float pillowCoverage = auraCover(pillowD);
      float reach = shadowSizes.w * (1.0 - 0.5 * held);
      float pillowCast = auraBox(rel - float2(0.0, shadowSizes.z * (1.0 - 0.5 * held)), halfPillow, halfPillow.y);
      if (i == 3) {
        float glow = exp(-pow(max(pillowCast, 0.0) / (reach * 2.4), 2.0));
        glass = mix(glass, accentMid, glow * 0.18 * (1.0 - 0.5 * held));
      }
      glass *= 1.0 - 0.24 * exp(-pow(max(pillowCast, 0.0) / reach, 2.0));
      if (pillowCoverage > 0.0) {
        float2 uv = (rel + halfPillow) / (halfPillow * 2.0);
        float ramp = i == 3 ? clamp(uv.y * 0.90 + uv.x * 0.24, 0.0, 1.0) : uv.y;
        float3 topTone = i == 3 ? accentTop : pillowTop;
        float3 midTone = i == 3 ? accentMid : pillowMid;
        // The accent's foot is darker and more saturated than its lowest
        // palette stop: absorption here supplies the difference.
        float3 lowTone = i == 3 ? accentLow * float3(0.76, 0.46, 0.96) : pillowLow;
        // A cooler reflection on the accent and a brighter peak on the
        // neutral pillows, as the reference's have.
        float3 shineTone = i == 3 ? accentShine * float3(0.97, 0.91, 1.05) : mix(pillowShine, lightTone, 0.14);
        float3 body = mix(topTone, midTone, smoothstep(0.0, 0.65, ramp));
        body = mix(body, lowTone, smoothstep(0.50, 1.0, ramp));
        // Long pillows catch a broad softbox on their upper left. The round
        // pair shares a wider dome, without a concentrated ball-like glint.
        float2 shineAt = float2(i < 2 ? 0.40 : 0.25, (i < 2 ? 0.17 : 0.14) + surfaceSizes.y * held / (halfPillow.y * 2.0));
        float2 shineReach = i < 2 ? float2(0.42, 0.31) : float2(0.36, 0.28);
        float specular = auraLamp(uv, shineAt, shineReach);
        body = mix(body, shineTone, specular * (i == 3 ? 0.64 : (i < 2 ? 0.76 : 0.87)) * (1.0 - 0.40 * held));
        float2 pillowN = auraNormal(rel, halfPillow, halfPillow.y);
        float catches = max(dot(pillowN, float2(-0.40, -0.9165)), 0.0);
        body = mix(body, shineTone, 0.24 * catches * auraRim(pillowD, surfaceSizes.z) * (1.0 - 0.4 * held));
        body *= 1.0 - 0.16 * max(pillowN.y, 0.0) * auraRim(pillowD, surfaceSizes.z * 1.8);
        body *= 1.0 - 0.08 * held;
        glass = mix(glass, body, pillowCoverage);
      }
    }
  }
  glass = clamp(glass + auraDither(xy), 0.0, 1.0);
  float alpha = shellCoverage + shadowAlpha * (1.0 - shellCoverage);
  return half4(glass * shellCoverage, alpha);
}
"""
