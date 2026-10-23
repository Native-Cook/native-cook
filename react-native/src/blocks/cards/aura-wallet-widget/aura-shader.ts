import { AuraPhase, AURA_LAND_Y, auraActionFrames, auraDropHalo, auraEnterScale, auraFlow, auraLipPose, auraNeckRoot, auraNeckWaist, auraNudgeDip, auraSendMoments, auraSendPose, auraSlotX } from "./aura-flow";
import { AURA_LIGHT, auraLightUniforms, type AuraColorReader } from "./aura-light";
import { AURA_BLEED, AURA_DESIGN, auraDesign as D, auraFrames, type AuraRect, type AuraSkin } from "./aura-palette";

// One surface, from the floating smoked shell to the four thick pillows.
// The cushion is a distance field with five circular bites; the empty part
// really exposes the shell underneath. A chosen bite grows into a drop,
// with light on its exposed meniscus and on the neck joining it to the pool.
// Its two caps retreat only after the waist closes; its hole falls with it.

// Optical lengths in design points and exposure ratios for this material.
// The score owns travel and timing; these only decide how the light falls.
const MATERIAL = {
  // The held pillow recedes into the shell.
  pressInset: 1.5,
  // Its lamp reflection drops with the compressed crown.
  pressShineDrop: 2,
  // The shell floats just above its cast.
  shellShadowDown: 3,
  // The cast falls away before the bleed ends.
  shellShadowReach: 7,
  // The pillows sit above the smoked surface.
  pillowShadowDown: 3,
  // Their cast stays close to the button band.
  pillowShadowReach: 3.5,
  // A sub-point reflection defines the outer surface.
  rim: 0.65,
  // The pane has a finer edge than the pillows.
  paneLine: 1,
  // The exposed band is a rounded meniscus, lit across its thickness.
  bandPeak: 0.64,
  // The meniscus gives its light back to the pool within a fraction of a fillet.
  bodyBlendIn: 0.2,
  // Outside the meniscus the cushion recovers its own light.
  bodyBlendOut: 0.4,
  // The angular falloff is wide, avoiding a hard metallic highlight.
  crownStart: -0.15,
  // The crest peaks only on the arc facing the lamp.
  crownEnd: 0.95,
  // The dark foot begins as the arc turns away.
  footStart: -0.1,
  // The opposite arc carries the deepest body color.
  footEnd: 0.9,
  // Only the outer part of the foot loses extra light.
  footRimStart: 0.72,
  // The neck's lateral ramp shares the drop's upper-left lamp.
  flankStart: -0.9,
  // The right flank recedes into the deeper violet.
  flankEnd: 0.9,
  // A broad reflection rolls across the curved band.
  bandSoftness: 0.36,
  // The upper-left arc faces the cushion lamp.
  crownExposure: 0.78,
  // Only the centre of that reflection approaches white.
  specularExposure: 0.50,
  // The outer upper rim catches a hairline of the lamp.
  rimExposure: 0.28,
  // The lower-right rim turns away from the light.
  footAbsorption: 0.14,
  // The face sits within a shallow occluded seat.
  innerAbsorption: 0.16,
  // The seat shadow dies within the inner part of the band.
  innerReach: 0.22,
  // A shared lamp direction keeps the neck and its drop in the same light.
  lampX: -0.6,
  // The lamp is above as well as to the left.
  lampY: -0.8,
  // The left flank carries the neck reflection.
  neckPeak: -0.42,
  // The reflection wraps around a rounded cross section.
  neckSoftness: 0.42,
  // The thin neck reflects less white than the drop.
  neckExposure: 0.22,
  // A cast on either glass surface gives the hanging drop some clearance.
  dropShadowDown: 3,
  // The soft cast extends beyond the hanging body.
  dropShadowReach: 5,
  // Even at full contact the cast stays below a quarter black.
  dropShadowAlpha: 0.24,
  // Approaching the pane narrows the cast and its offset.
  contactTightness: 0.45,
  // The departing light is quiet enough to travel behind the balance.
  waveWidth: 3,
  // A quiet dark wake follows the outgoing light.
  waveTroughOffset: 4,
  // The wake is softer than the crest.
  waveTroughWidth: 5,
  // The crest never competes with the balance type.
  waveExposure: 0.10,
  // The wake barely darkens the cushion.
  waveAbsorption: 0.025,
  // The landing spreads light under the pane after the body is taken in.
  rippleWidth: 2.2,
  // The pane ripple leaves a narrow inner wake.
  rippleTroughOffset: 3,
  // The inner wake diffuses across the glass.
  rippleTroughWidth: 3.5,
  // Landing is brighter than the departing cushion wave.
  rippleExposure: 0.24,
  // The pane wave displaces a little transmitted light.
  rippleAbsorption: 0.08,
  // The absorbed footprint begins as a shallow oval.
  absorptionTall: 0.55,
  // The disappearing body leaves a soft violet pool.
  absorptionExposure: 0.30,
  // The facing pillows' soft cheeks retain their existing material response.
  cheekFalloff: 0.56,
};

// The score gives the neck's root and waist; how it closes and how far its
// root leans with a sideways pull are this shader's own geometry.
const ATTACHMENT = {
  separation: 0.45,
  rootLean: 0.2,
};

export const AURA_SHELL_SHADER = `${AURA_LIGHT}
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
  float waist = neckPose.z * smoothstep(${ATTACHMENT.separation}, 1.0, dropShape.w);
  float shoulder = (2.0 * t - 1.0);
  float neckWidth = waist + (liquidOptics.x - waist) * shoulder * shoulder;
  float slope = 4.0 * (liquidOptics.x - waist) * shoulder / span;
  float bodyD = (abs(p.x - cx) - neckWidth) / sqrt(1.0 + slope * slope);
  bodyD = max(bodyD, max(root.y - p.y, p.y - endPoint.y));
  if (dropShape.w < ${ATTACHMENT.separation}) {
    float retreat = 1.0 - dropShape.w / ${ATTACHMENT.separation};
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
      float connected = auraUnion(d, neckD, neckPose.w * smoothstep(0.0, ${ATTACHMENT.separation}, dropShape.w));
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
    cushion = mix(cushion, heroMid, auraLamp(uv, float2(0.48, 0.34), float2(0.46, 0.70)) * 0.54 * mix(1.0, across * 0.45, lower) * rightFall);
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
        float crest = auraBell((waveRadial - poolWave.x) / (${MATERIAL.waveWidth} * liquidState.w));
        float trough = auraBell((waveRadial - poolWave.x + ${MATERIAL.waveTroughOffset} * liquidState.w) / (${MATERIAL.waveTroughWidth} * liquidState.w));
        cushion *= 1.0 - trough * poolWave.y * ${MATERIAL.waveAbsorption};
        cushion = mix(cushion, heroCore, crest * poolWave.y * ${MATERIAL.waveExposure});
      }
      if (nearLiquid) {
        // Light crosses the visible annulus, not the hidden centre of the
        // face. Its outer crescent is broad; the hole has a soft inner occlusion.
        float2 dq = (p - dropPose.xy) / dropShape.xy;
        float2 radialN = dq / max(dropRadial, liquidState.w * 0.01);
        float towardLamp = dot(radialN, float2(${MATERIAL.lampX}, ${MATERIAL.lampY}));
        float band = (dropRadial - dropPose.w) / max(dropPose.z - dropPose.w, liquidState.w * 0.01);
        float crown = smoothstep(${MATERIAL.crownStart}, ${MATERIAL.crownEnd}, towardLamp);
        float curved = auraBell((band - ${MATERIAL.bandPeak}) / ${MATERIAL.bandSoftness});
        float3 dropLight = mix(heroMid, heroEdge, smoothstep(${MATERIAL.footStart}, ${MATERIAL.footEnd}, -towardLamp));
        dropLight = mix(dropLight, heroCore, crown * ${MATERIAL.crownExposure});
        dropLight = mix(dropLight, lightTone, crown * crown * curved * ${MATERIAL.specularExposure});
        dropLight *= 1.0 - ${MATERIAL.footAbsorption} * smoothstep(${MATERIAL.footRimStart}, 1.0, band) * (1.0 - crown);
        dropLight *= 1.0 - ${MATERIAL.innerAbsorption} * auraBell(max(band, 0.0) / ${MATERIAL.innerReach});
        // Only the joined surface has a rim. A circular rim through the neck
        // would etch a seam across what must read as one continuous liquid.
        float outerRim = auraRim(heroD, surfaceSizes.z) * smoothstep(${MATERIAL.bandPeak}, 1.0, band);
        dropLight = mix(dropLight, lightTone, outerRim * crown * ${MATERIAL.rimExposure});
        float3 localLight = cushion;
        if (liquidOptics.z > 0.0 && dropShape.w > 0.0 && neckJoin > 0.0) {
          float flank = (p.x - neckSection.y) / max(neckSection.z, liquidState.w);
          float3 neckLight = mix(heroCore, heroEdge, smoothstep(${MATERIAL.flankStart}, ${MATERIAL.flankEnd}, flank));
          neckLight = mix(neckLight, lightTone, auraBell((flank - ${MATERIAL.neckPeak}) / ${MATERIAL.neckSoftness}) * ${MATERIAL.neckExposure});
          float rootFade = smoothstep(neckPose.y - neckPose.w, neckPose.y + neckPose.w, p.y);
          localLight = mix(localLight, neckLight, rootFade * neckJoin * dropShape.w);
        }
        float bodyBlend = 1.0 - smoothstep(dropPose.z - neckPose.w * ${MATERIAL.bodyBlendIn}, dropPose.z + neckPose.w * ${MATERIAL.bodyBlendOut}, dropRadial);
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
      float crest = auraBell((radial - sendFx.z) / (${MATERIAL.rippleWidth} * liquidState.w));
      float trough = auraBell((radial - sendFx.z + ${MATERIAL.rippleTroughOffset} * liquidState.w) / (${MATERIAL.rippleTroughWidth} * liquidState.w));
      float3 rippleTone = mix(heroCore, lightTone, 1.0 - sendFx.w);
      paneColor *= 1.0 - trough * sendFx.w * ${MATERIAL.rippleAbsorption};
      paneColor = mix(paneColor, rippleTone, crest * sendFx.w * ${MATERIAL.rippleExposure});
      // Absorbed light spreads under the pane as the meniscus sinks into it.
      float spread = (1.0 - dropShape.z) * dropPose.z;
      float pool = auraLamp(p, landing.xy, float2(dropPose.z + spread, dropPose.z * ${MATERIAL.absorptionTall} + spread));
      paneColor = mix(paneColor, heroMid, pool * (1.0 - dropShape.z) * sendFx.w * ${MATERIAL.absorptionExposure});
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
        float cheekBlend = landing.w * auraBell(rel.y / (halfPillow.y * ${MATERIAL.cheekFalloff}));
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
`;

/** Static geometry and colors; press and accessibility join on the UI thread. */
export function auraShellUniforms(width: number, skin: AuraSkin, pixelRatio: number, color: AuraColorReader) {
  const f = auraFrames(width);
  const rgb = (value: string) => Array.from(color(value)).slice(0, 3);
  const rect = (r: AuraRect) => [r.x, r.y, r.w, r.h];
  return {
    ...auraLightUniforms(skin, color),
    origin: [AURA_BLEED, AURA_BLEED],
    cardWidth: f.size,
    pixelRatio,
    shellRadius: f.radius,
    bleed: AURA_BLEED,
    shellClear: skin.shellClear,
    shellTone: rgb(skin.shell),
    lightTone: rgb(skin.ink),
    heroRect: rect(f.hero),
    heroRadii: [f.hero.radiusTop, f.hero.radiusBottom],
    lipShape: [f.lip.cy, f.lip.avatar / 2 + f.lip.ring],
    lipXs: f.lip.xs,
    heroCore: rgb(skin.heroCore),
    heroMid: rgb(skin.heroMid),
    heroLeft: rgb(skin.heroLeft),
    heroEdge: rgb(skin.heroEdge),
    paneRect: rect(f.pane),
    paneRadius: f.pane.radius,
    paneFill: Array.from(color(skin.paneFill)),
    paneLine: Array.from(color(skin.paneLine)),
    actionRects: f.actions.flatMap(rect),
    pillowTop: rgb(skin.pillowTop),
    pillowMid: rgb(skin.pillowMid),
    pillowLow: rgb(skin.pillowLow),
    pillowShine: rgb(skin.pillowShine),
    accentTop: rgb(skin.accentTop),
    accentMid: rgb(skin.accentMid),
    accentLow: rgb(skin.accentLow),
    accentShine: rgb(skin.accentShine),
    shadowSizes: [MATERIAL.shellShadowDown, MATERIAL.shellShadowReach, MATERIAL.pillowShadowDown, MATERIAL.pillowShadowReach].map((v) => v * f.u),
    surfaceSizes: [MATERIAL.pressInset, MATERIAL.pressShineDrop, MATERIAL.rim, MATERIAL.paneLine].map((v) => v * f.u),
  };
}

/**
 * The score translated once for both Graphite and the offline proof. Inputs
 * are plain numbers: no JSI objects or callbacks cross into this worklet.
 * Arrays always have five friends and four actions, including empty slots.
 */
export function auraMotionUniforms(
  width: number, phase: number, chosen: number, formed: number,
  leaving: number, unformed: number, pullX: number, pullY: number,
  clock: number, fromX: number, fromY: number, wide: number, nudge: number,
  opaque: number, settings: number, swap: number, receive: number, send: number,
  weight: number, entered: number,
) {
  "worklet";
  const u = width / AURA_DESIGN;
  const sending = phase === AuraPhase.sending && chosen >= 0;
  const pulled = chosen >= 0 && (pullX !== 0 || pullY !== 0);
  const moving = sending || pulled;
  const pose = sending ? auraSendPose(clock, fromY) : null;
  const weightedHalo = chosen >= 0 ? auraDropHalo(weight) : auraFlow.drop.halo;
  const lipPoses: number[] = [];
  for (let i = 0; i < 5; i++) {
    const lip = auraLipPose(i, chosen, formed, leaving, unformed);
    const absent = i === chosen && moving;
    const bud = sending && i === chosen ? pose!.bud : 0;
    const arrival = entered >= 0 ? auraEnterScale(i, entered) : lip.scale;
    const faceScale = absent ? bud : arrival;
    lipPoses.push(
      (auraSlotX(i) + (absent ? 0 : lip.dx)) * u,
      (D.lip.cy + (absent ? 0 : lip.dy) + auraNudgeDip(i, nudge)) * u,
      (faceScale > 0 ? D.lip.avatar / 2 * faceScale + D.lip.ring : 0) * u,
      (absent ? 0 : i === chosen ? lip.halo / auraFlow.drop.halo * weightedHalo : lip.halo) * u,
    );
  }
  const chosenPose = auraLipPose(chosen, chosen, formed, leaving, unformed);
  const dropX = sending ? fromX : auraSlotX(chosen) + chosenPose.dx + pullX;
  const dropY = sending ? pose!.y : D.lip.cy + chosenPose.dy + pullY + auraNudgeDip(chosen, nudge);
  const rootY = D.hero.y + D.hero.h;
  const rootX = auraSlotX(chosen) + (dropX - auraSlotX(chosen)) * ATTACHMENT.rootLean;
  const radius = D.lip.avatar / 2 + D.lip.ring + chosenPose.halo / auraFlow.drop.halo * weightedHalo;
  const dropWide = sending ? pose!.wide : 1;
  const dropTall = sending ? pose!.tall : 1;
  const contact = sending ? Math.min(Math.max((clock - auraSendMoments(fromY).impact + auraFlow.send.squash) / auraFlow.send.squash, 0), 1) : 0;
  const castDown = MATERIAL.dropShadowDown * (1 - contact * MATERIAL.contactTightness);
  const castReach = MATERIAL.dropShadowReach * (1 - contact * MATERIAL.contactTightness);
  // The neck starts inside the formed wrap. Grow its fillet only as it
  // emerges: a full blend on a near-zero span would put fins on the lip.
  const depth = sending ? fromY - D.lip.cy - auraFlow.drop.sag : pullY;
  const joined = Math.min(Math.max(depth / auraFlow.neck.blend, 0), 1);
  const rootWidth = auraNeckRoot(depth);
  const actions = auraActionFrames(wide);
  const actionRects: number[] = [];
  for (let i = 0; i < 4; i++) {
    actionRects.push(actions.xs[i] * u, D.actions.y * u, actions.widths[i] * u, D.actions.h * u);
  }
  return {
    opaque,
    neckJoin: joined * joined * (3 - 2 * joined),
    pressValues: [settings, swap, receive, send],
    actionRects,
    lipPoses,
    liquidState: [chosen >= 0 || leaving >= 0 || nudge >= 0 || entered >= 0 ? 1 : 0, chosen >= 0 ? chosen : -1, sending ? 1 : 0, u],
    dropPose: [dropX * u, dropY * u, radius * u, (D.lip.avatar / 2 + D.lip.ring) * u],
    dropShape: [dropWide, dropTall, sending ? pose!.presence : 1, sending ? pose!.neck : 1],
    neckPose: [rootX * u, rootY * u, auraNeckWaist(depth) * u, auraFlow.neck.blend * u],
    sendFx: [sending ? pose!.lipKick * u : 0, sending ? pose!.cushion : 1, sending ? pose!.rippleRadius * u : 0, sending ? pose!.rippleStrength : 0],
    liquidOptics: [rootWidth * u, auraFlow.drop.halo * u, moving ? 1 : 0, Math.min(chosenPose.halo / auraFlow.drop.halo, 1)],
    liquidBounds: [
      (Math.min(rootX - rootWidth, dropX - radius * dropWide) - auraFlow.neck.blend * 2) * u,
      (Math.min(rootY, dropY - radius * dropTall) - auraFlow.neck.blend * 2) * u,
      (Math.max(rootX + rootWidth, dropX + radius * dropWide) + auraFlow.neck.blend * 2) * u,
      (dropY + radius * dropTall + auraFlow.neck.blend * 2) * u,
    ],
    dropCast: [castDown * u, castReach * u, MATERIAL.dropShadowAlpha * Math.min(chosenPose.halo / auraFlow.drop.halo, 1) * (sending ? 1 - pose!.taken : 1)],
    castBounds: [(dropX - (radius + castReach * 2) * dropWide) * u, (dropY + castDown - (radius + castReach * 2) * dropTall) * u, (dropX + (radius + castReach * 2) * dropWide) * u, (dropY + castDown + (radius + castReach * 2) * dropTall) * u],
    poolWave: [sending ? pose!.waveRadius * u : 0, sending ? pose!.waveStrength : 0],
    landing: [fromX * u, AURA_LAND_Y * u, sending ? pose!.rim : 1, actions.kiss * u],
  };
}
