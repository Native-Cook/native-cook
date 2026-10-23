// Exact RN SkSL translation: point coordinates, constants and all light/material terms preserved.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

internal val horizonSceneAgsl = horizonLightAgsl + """
half4 main(float2 p) {
  float2 local = (p - slab.xy) / slabScale;
  float d = box(local, slab.zw, CARD_RADIUS) * slabScale;
  float cover = coverage(d);
  float grain = hash(floor(p * scale)) - 0.5;

  if (cover <= 0.0) {
    float3 pageColor = field(p, 0.0);
    if (d < 70.0) {
      float gap = box(local - float2(0.0, 10.0 / slabScale), slab.zw, CARD_RADIUS) * slabScale;
      float shadow = exp(-0.5 * max(gap, 0.0) * max(gap, 0.0) / (14.0 * 14.0));
      // The contact shadow grounds the glass, but cannot extinguish the
      // exposed emitter itself. Its halo fades back into the normal shadow.
      float2 emitterUV = (p - float2(planet.x, rest.y + rest.w - RISE)) / 12.0;
      float emitter = min(dawnBreak, 1.0) * exp(-dot(emitterUV, emitterUV));
      // The card shades the dark sky, not the light: dimming the bloom
      // around the limb left its white core standing alone.
      float glowing = smoothstep(0.10, 0.45, max(pageColor.r, max(pageColor.g, pageColor.b)));
      pageColor *= 1.0 - 0.35 * shadow * (1.0 - emitter) * (1.0 - glowing);
    }
    return half4(clamp(pageColor + grain / 255.0, 0.0, 1.0), 1.0);
  }

  float wd = box(local - well.xy, well.zw, WELL_RADIUS) * slabScale;
  float wc = coverage(wd);
  float4 lampLight = lamp(p);
  float beadPress = clamp(closePress, 0.0, 1.0);
  float beadRadius = bead.z * (1.0 - 0.08 * beadPress);
  float2 beadUV = local - bead.xy;
  float beadDistance = length(beadUV);
  float bd = (beadDistance - beadRadius) * slabScale;
  float bc = coverage(bd);
  if (opaque > 0.5) {
    float3 solid = mix(PLAIN, float3(1.0), 0.1200 * edge(d, slabScale));
    solid = mix(solid, PLAIN_WELL, wc);
    solid += lampLight.rgb * (1.0 - wc);
    float3 plainBead = mix(DISC, float3(1.0), 0.18 * edge(bd, slabScale));
    solid = mix(solid, plainBead, bc);
    if (cover < 1.0) solid = mix(field(p, 0.0), solid, cover);
    return half4(clamp(solid, 0.0, 1.0), 1.0);
  }

  float depth = max(-d / slabScale, 0.0);
  float bevel = 1.0 - clamp(depth / 16.0, 0.0, 1.0);
  float2 n = normal(local, slab.zw, CARD_RADIUS);
  float bend = cover >= 1.0 ? 26.0 * (1.0 - sqrt(max(1.0 - bevel * bevel, 0.0))) * slabScale : 0.0;
  float softness = 0.45 * (1.0 - bevel);
  float3 behind = field(p + n * bend, softness);
  if (bend >= 0.125) {
    behind.r = field(p + n * bend * 1.045, softness).r;
    behind.b = field(p + n * bend * 0.955, softness).b;
  }
  // A clear tint preserves the field's structure. Only the brightest energy
  // compresses, keeping labels readable without frosting away the limb.
  float peak = max(behind.r, max(behind.g, behind.b));
  float compress = 1.0 / (1.0 + 0.45 * max(peak - 0.45, 0.0));
  float3 transmitted = behind * float3(0.62, 0.70, 0.88) * compress;
  float3 glass = transmitted + float3(0.020, 0.022, 0.032);
  // Adapt the dynamic range rather than adding frost over bright content.
  // This also protects copy when a drag brings it closer to the sun.
  float faceEnergy = dot(glass * glass, float3(0.2126, 0.7152, 0.0722));
  float legibility = inversesqrt(max(1.0, faceEnergy / 0.19));
  transmitted *= legibility;
  glass *= legibility;

  float3 edgeFlux = float3(0.0);
  if (depth < 20.0) {
    // One additional field sample just beyond the nearest rim. This light
    // changes as the pane moves; there are no painted corner pools.
    float2 outsidePoint = p + n * (max(-d, 0.0) + 3.0);
    edgeFlux = max(field(outsidePoint, 0.0) - PAGE, 0.0);
    float edgePeak = max(edgeFlux.r, max(edgeFlux.g, edgeFlux.b));
    edgeFlux /= 1.0 + 0.65 * min(dawnBreak, 1.0) * max(edgePeak - 0.6, 0.0);
    glass += edgeFlux * (0.58 * exp(-depth / 7.0) + 0.35 * edge(d, 1.0 / scale));
  }

  float facing = dot(n, float2(-0.7071068, -0.7071068));
  float upper = max(facing, 0.0);
  float lower = max(-facing, 0.0);
  upper *= upper; upper *= upper; upper *= upper;
  lower *= lower; lower *= lower; lower *= lower;
  float specular = (0.12 + 0.43 * upper + 0.13 * lower) * (1.0 + flare);
  glass += float3(specular) * (edge(d, 0.8 * slabScale) + 0.055 * exp(-depth / 3.0));
  glass *= 1.0 - 0.035 * bell((depth - 15.0) / 2.0);

  if (wc > 0.0) {
    float wellDepth = max(-wd / slabScale, 0.0);
    float2 wn = normal(local - well.xy, well.zw, WELL_RADIUS);
    float bottomFacing = max(wn.y, 0.0);
    float3 recess = WELL + transmitted * 0.30;
    // The ceiling overhang shades the top, while the rounded lower wall
    // collects light from just beyond its edge. Both belong to the recess.
    recess *= 1.0 - 0.42 * max(-wn.y, 0.0) * (1.0 - smoothstep(0.0, 7.0, wellDepth));
    if (bottomFacing > 0.0 && wellDepth < 18.0) {
      float2 wellOutside = p + wn * (wellDepth + 2.0) * slabScale;
      float3 wellFlux = max(field(wellOutside, 0.35) - PAGE, 0.0);
      float wellPeak = max(wellFlux.r, max(wellFlux.g, wellFlux.b));
      wellFlux /= 1.0 + wellPeak;
      float innerLip = bell((wellDepth - 1.15) / 0.55);
      float innerGlow = bell(wellDepth / 8.0) * (1.0 - smoothstep(14.0, 18.0, wellDepth));
      recess += bottomFacing * (wellFlux * (0.16 * innerGlow + 0.32 * innerLip) + BLOOM * 0.06 * innerLip);
    }
    // A dark outer seam and a lighter return just inside it turn the edge.
    recess *= 1.0 - 0.36 * edge(wd, 0.65 * slabScale);
    recess += float3(0.022, 0.032, 0.055) * bell((wellDepth - 1.1) / 0.4) * (1.0 - 0.5 * bottomFacing);
    glass = mix(glass, recess, wc);
  }

  // Localized dimming under the narrow line retains its deep-blue identity
  // even when the sun is visible through the glass beneath it.
  glass = mix(glass, PANE, lampLight.a * (1.0 - wc));
  glass += lampLight.rgb * (1.0 - wc * 0.35);

  if (sweepStrength > 0.0) {
    float bandCenter = mix(slab.w + 65.0, -slab.w - 65.0, sweep);
    float band = bell((local.y - bandCenter + local.x * 0.2125566) / 35.0);
    float response = (0.16 + 0.38 * bevel * bevel + 0.65 * edge(d, slabScale)) * (1.0 - 0.70 * wc);
    glass += mix(BLOOM, CORE, 0.85) * band * sweepStrength * power * response;
  }
  if (beadDistance < beadRadius + 20.0) {
    float beadShadowGap = max(length(beadUV - float2(0.0, 2.0)) - beadRadius, 0.0);
    float beadShadow = 0.30 * exp(-0.5 * beadShadowGap * beadShadowGap / 36.0);
    float3 beadPane = glass;
    glass *= 1.0 - beadShadow;
    if (bc > 0.0) {
      float beadDepth = max(beadRadius - beadDistance, 0.0);
      float2 beadNormal = beadUV / max(beadDistance, 0.0001);
      float beadFacing = dot(beadNormal, float2(-0.7071068, -0.7071068));
      float beadUpper = max(beadFacing, 0.0);
      float beadLower = max(-beadFacing, 0.0);
      // Sample a slightly smaller patch of the pane underneath this convex
      // lens. Highlights stay on the perimeter, clear of the 12 pt glyph.
      float2 beadSample = slab.xy + (bead.xy + beadUV * 0.90) * slabScale;
      float3 beadField = field(beadSample, 0.45);
      float3 beadBody = beadPane + (beadField - behind) * float3(0.62, 0.70, 0.88) * compress * legibility;
      beadBody += float3(0.05 + 0.025 * beadPress);
      beadBody *= 1.0 - 0.28 * beadLower * bell((beadDepth - 1.8) / 2.4);
      float crescent = bell((beadDepth - 0.9) / mix(1.1, 2.2, beadPress));
      beadBody += float3(0.82, 0.90, 1.0) * mix(0.48, 0.32, beadPress) * beadUpper * beadUpper * crescent;
      beadBody += float3(0.10 + 0.17 * beadUpper) * edge(bd, 0.65 * slabScale);
      if (beadDepth < 7.0 && beadNormal.y > 0.0) {
        float2 beadOutside = slab.xy + (bead.xy + beadNormal * (beadRadius + 3.0)) * slabScale;
        float3 beadFlux = max(field(beadOutside, 0.35) - PAGE, 0.0);
        beadFlux /= 1.0 + max(beadFlux.r, max(beadFlux.g, beadFlux.b));
        beadBody += beadFlux * float3(0.75, 0.88, 1.0) * 0.20 * beadNormal.y * bell(beadDepth / 3.0);
      }
      glass = mix(glass, beadBody, bc);
    }
  }
  float3 color = mix(behind, glass, cover);
  color += grain * (1.2 / 255.0);
  return half4(clamp(color, 0.0, 1.0), 1.0);
}
"""
