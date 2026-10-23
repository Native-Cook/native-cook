// Page light and liquid shell, term-for-term from aura-light.ts and aura-shader.ts.
// A flat float buffer avoids Swift/Metal float3 alignment differences.
enum AuraShader {
  static let source = """
    #include <metal_stdlib>
    using namespace metal;
    struct AuraField {
      static float smooth(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
      }

    float2 origin;
    float cardWidth;
    float pixelRatio;
    float3 pageTone;
    float3 leftCore;
    float3 leftEdge;
    float3 lowCore;
    float3 lowEdge;
    float3 highTone;

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
      float fallWidth = mix(0.64, 1.10, smooth(1.15, 1.65, q.y));
      float fall = auraLamp(q, float2(0.90, 1.55), float2(fallWidth, 0.72));
      float3 pool = 1.0 - (1.0 - leftCore * poolLeft * 0.30)
        * (1.0 - lowCore * poolRight * 0.46) * (1.0 - lowCore * fall * 0.55);
      float ceiling = smooth(-0.10, 0.28, q.y);
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

    float shellRadius;
    float bleed;
    float opaque;
    float shellClear;
    float3 shellTone;
    float3 lightTone;
    float4 heroRect;
    float2 heroRadii;
    float2 lipShape;
    float lipXs[5];
    float3 heroCore;
    float3 heroMid;
    float3 heroLeft;
    float3 heroEdge;
    float4 paneRect;
    float paneRadius;
    float4 paneFill;
    float4 paneLine;
    float4 actionRects[4];
    float pressValues[4];
    float3 pillowTop;
    float3 pillowMid;
    float3 pillowLow;
    float3 pillowShine;
    float3 accentTop;
    float3 accentMid;
    float3 accentLow;
    float3 accentShine;
    float4 shadowSizes;
    float4 surfaceSizes;
    float4 lipPoses[5];
    float4 liquidState;
    float4 dropPose;
    float4 dropShape;
    float4 neckPose;
    float4 sendFx;
    float4 landing;
    float4 liquidOptics;
    float neckJoin;
    float4 liquidBounds;
    float3 dropCast;
    float4 castBounds;
    float2 poolWave;

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
      float waist = neckPose.z * smooth(0.45, 1.0, dropShape.w);
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
          * smooth(heroRect.w * 0.25, heroRect.w * 0.5, rel.y);
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
          float connected = auraUnion(d, neckD, neckPose.w * smooth(0.0, 0.45, dropShape.w));
          d = mix(d, connected, neckJoin);
        }
        holes = max(holes, (dropPose.w - dropRadial) * metric);
      }
      d = max(d, holes);
      return d;
    }

    float4 main(float2 xy) {
      float2 p = xy - origin;
      float2 halfCard = float2(cardWidth * 0.5);
      float shellD = auraSoftBox(p - halfCard, halfCard, shellRadius);
      float shellCoverage = auraCover(shellD);
      float castD = auraSoftBox(p - halfCard - float2(0.0, shadowSizes.x), halfCard, shellRadius);
      float shadowAlpha = exp(-pow(max(castD, 0.0) / shadowSizes.y, 2.0)) * 0.48;
      // Black premultiplied shadow, gone before the canvas boundary.
      float outside = max(shellD, 0.0);
      shadowAlpha *= (0.16 + 0.84 * smooth(0.52, 1.0, p.y / cardWidth))
        * (1.0 - smooth(bleed * 0.65, bleed, outside));
      if (shellCoverage <= 0.0) return float4(0.0, 0.0, 0.0, shadowAlpha);

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
            * (1.0 - smooth(dropCast.y, dropCast.y * 2.0, castEdge));
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
        float lower = smooth(0.32, 0.88, uv.y);
        float rightFall = 1.0 - smooth(0.72, 0.97, uv.x) * smooth(0.30, 0.58, uv.y);
        float across = auraLamp(uv, float2(0.47, 0.35), float2(0.33, 1.0));
        float leftLift = (1.0 - smooth(0.0, 0.70, uv.x)) * (1.0 - lower * 0.76);
        float3 cushion = mix(heroEdge, heroLeft, leftLift);
        cushion = mix(cushion, heroMid, auraLamp(uv, float2(0.48, 0.34), float2(0.46, 0.70)) * 0.54 * mix(1.0, across * 0.45, lower) * rightFall);
        cushion = mix(cushion, heroCore, central * mix(1.0, across, lower) * rightFall);
        float edgeDepth = max(-heroD, 0.0);
        cushion *= 1.0 - 0.10 * exp(-edgeDepth / (heroRect.w * 0.028)) * smooth(0.38, 0.95, uv.y);
        cushion = mix(cushion, lightTone, 0.16 * (1.0 - smooth(0.02, 0.64, uv.y)) * auraRim(heroD, surfaceSizes.z));
        if (liquidState.x > 0.0) {
          cushion *= sendFx.y;
          // The wave stays in the original cushion, never in a hanging drop.
          // Both the bounding box and the surface coverage precede its radius.
          if (poolWave.y > 0.0 && auraInside(p, float4(heroRect.xy, heroRect.xy + heroRect.zw))) {
            float waveRadial = length(p - neckPose.xy);
            float crest = auraBell((waveRadial - poolWave.x) / (3 * liquidState.w));
            float trough = auraBell((waveRadial - poolWave.x + 4 * liquidState.w) / (5 * liquidState.w));
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
            float crown = smooth(-0.15, 0.95, towardLamp);
            float curved = auraBell((band - 0.64) / 0.36);
            float3 dropLight = mix(heroMid, heroEdge, smooth(-0.1, 0.9, -towardLamp));
            dropLight = mix(dropLight, heroCore, crown * 0.78);
            dropLight = mix(dropLight, lightTone, crown * crown * curved * 0.50);
            dropLight *= 1.0 - 0.14 * smooth(0.72, 1.0, band) * (1.0 - crown);
            dropLight *= 1.0 - 0.16 * auraBell(max(band, 0.0) / 0.22);
            // Only the joined surface has a rim. A circular rim through the neck
            // would etch a seam across what must read as one continuous liquid.
            float outerRim = auraRim(heroD, surfaceSizes.z) * smooth(0.64, 1.0, band);
            dropLight = mix(dropLight, lightTone, outerRim * crown * 0.28);
            float3 localLight = cushion;
            if (liquidOptics.z > 0.0 && dropShape.w > 0.0 && neckJoin > 0.0) {
              float flank = (p.x - neckSection.y) / max(neckSection.z, liquidState.w);
              float3 neckLight = mix(heroCore, heroEdge, smooth(-0.9, 0.9, flank));
              neckLight = mix(neckLight, lightTone, auraBell((flank - -0.42) / 0.42) * 0.22);
              float rootFade = smooth(neckPose.y - neckPose.w, neckPose.y + neckPose.w, p.y);
              localLight = mix(localLight, neckLight, rootFade * neckJoin * dropShape.w);
            }
            float bodyBlend = 1.0 - smooth(dropPose.z - neckPose.w * 0.2, dropPose.z + neckPose.w * 0.4, dropRadial);
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
          float trough = auraBell((radial - sendFx.z + 3 * liquidState.w) / (3.5 * liquidState.w));
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
            float3 body = mix(topTone, midTone, smooth(0.0, 0.65, ramp));
            body = mix(body, lowTone, smooth(0.50, 1.0, ramp));
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
      return float4(glass * shellCoverage, alpha);
    }

    float4 page(float2 xy) {
      float3 color = auraPageField(xy - origin) + auraDither(xy);
      return float4(clamp(color, 0.0, 1.0), 1.0);
    }

    };
    struct AuraVertex { float4 position [[position]]; };
    vertex AuraVertex auraVertex(uint id [[vertex_id]]) {
      float2 p = float2((id << 1) & 2, id & 2);
      return { float4(p * 2.0 - 1.0, 0.0, 1.0) };
    }
    AuraField auraFieldFromUniforms(constant float *u) {
      AuraField f;
      f.origin = float2(u[0], u[1]);
      f.cardWidth = u[2];
      f.pixelRatio = u[3];
      f.pageTone = float3(u[4], u[5], u[6]);
      f.leftCore = float3(u[7], u[8], u[9]);
      f.leftEdge = float3(u[10], u[11], u[12]);
      f.lowCore = float3(u[13], u[14], u[15]);
      f.lowEdge = float3(u[16], u[17], u[18]);
      f.highTone = float3(u[19], u[20], u[21]);
      f.shellRadius = u[22];
      f.bleed = u[23];
      f.opaque = u[24];
      f.shellClear = u[25];
      f.shellTone = float3(u[26], u[27], u[28]);
      f.lightTone = float3(u[29], u[30], u[31]);
      f.heroRect = float4(u[32], u[33], u[34], u[35]);
      f.heroRadii = float2(u[36], u[37]);
      f.lipShape = float2(u[38], u[39]);
      f.lipXs[0] = u[40];
      f.lipXs[1] = u[41];
      f.lipXs[2] = u[42];
      f.lipXs[3] = u[43];
      f.lipXs[4] = u[44];
      f.heroCore = float3(u[45], u[46], u[47]);
      f.heroMid = float3(u[48], u[49], u[50]);
      f.heroLeft = float3(u[51], u[52], u[53]);
      f.heroEdge = float3(u[54], u[55], u[56]);
      f.paneRect = float4(u[57], u[58], u[59], u[60]);
      f.paneRadius = u[61];
      f.paneFill = float4(u[62], u[63], u[64], u[65]);
      f.paneLine = float4(u[66], u[67], u[68], u[69]);
      f.actionRects[0] = float4(u[70], u[71], u[72], u[73]);
      f.actionRects[1] = float4(u[74], u[75], u[76], u[77]);
      f.actionRects[2] = float4(u[78], u[79], u[80], u[81]);
      f.actionRects[3] = float4(u[82], u[83], u[84], u[85]);
      f.pressValues[0] = u[86];
      f.pressValues[1] = u[87];
      f.pressValues[2] = u[88];
      f.pressValues[3] = u[89];
      f.pillowTop = float3(u[90], u[91], u[92]);
      f.pillowMid = float3(u[93], u[94], u[95]);
      f.pillowLow = float3(u[96], u[97], u[98]);
      f.pillowShine = float3(u[99], u[100], u[101]);
      f.accentTop = float3(u[102], u[103], u[104]);
      f.accentMid = float3(u[105], u[106], u[107]);
      f.accentLow = float3(u[108], u[109], u[110]);
      f.accentShine = float3(u[111], u[112], u[113]);
      f.shadowSizes = float4(u[114], u[115], u[116], u[117]);
      f.surfaceSizes = float4(u[118], u[119], u[120], u[121]);
      f.lipPoses[0] = float4(u[122], u[123], u[124], u[125]);
      f.lipPoses[1] = float4(u[126], u[127], u[128], u[129]);
      f.lipPoses[2] = float4(u[130], u[131], u[132], u[133]);
      f.lipPoses[3] = float4(u[134], u[135], u[136], u[137]);
      f.lipPoses[4] = float4(u[138], u[139], u[140], u[141]);
      f.liquidState = float4(u[142], u[143], u[144], u[145]);
      f.dropPose = float4(u[146], u[147], u[148], u[149]);
      f.dropShape = float4(u[150], u[151], u[152], u[153]);
      f.neckPose = float4(u[154], u[155], u[156], u[157]);
      f.sendFx = float4(u[158], u[159], u[160], u[161]);
      f.landing = float4(u[162], u[163], u[164], u[165]);
      f.liquidOptics = float4(u[166], u[167], u[168], u[169]);
      f.neckJoin = u[170];
      f.liquidBounds = float4(u[171], u[172], u[173], u[174]);
      f.dropCast = float3(u[175], u[176], u[177]);
      f.castBounds = float4(u[178], u[179], u[180], u[181]);
      f.poolWave = float2(u[182], u[183]);
      return f;
    }
    fragment float4 auraShellFragment(AuraVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
      AuraField f = auraFieldFromUniforms(u);
      return f.main(v.position.xy / f.pixelRatio);
    }
    fragment float4 auraPageFragment(AuraVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
      AuraField f = auraFieldFromUniforms(u);
      return f.page(v.position.xy / f.pixelRatio);
    }
    """
}
