#include <metal_stdlib>
using namespace metal;

// Float4 lanes make the CPU/GPU ABI explicit; the atlas is raw, non-sRGB data.
struct GlassBladeDrawUniforms {
    float4 uResolution;
    float4 uEye;
    float4 uSize;
    float4 uCentre[11];
    float4 uAxisX[11];
    float4 uAxisY[11];
    float4 uNormal[11];
    float4 uBounds[11];
    float4 uCard[11];
    float4 uColour[11];
    float4 uHero;
    float4 uBar;
    float4 uLime;
    float4 uMotion;
    float4 metrics;
};

struct GlassBladeDrawVertex {
    float4 position [[position]];
};

vertex GlassBladeDrawVertex glassBladeDrawVertex(uint id [[vertex_id]]) {
    float2 corners[3] = {float2(-1, -1), float2(3, -1), float2(-1, 3)};
    return {float4(corners[id], 0, 1)};
}

// The original SkSL, with only syntax and texture-coordinate translation.
struct GlassBladeDrawShader {
    constant GlassBladeDrawUniforms &u;
    texture2d<float> uAtlas;

    float mod(float x, float y) { return x - y * floor(x / y); }
    float2 mod(float2 x, float2 y) { return x - y * floor(x / y); }
    float4 sampleAtlas(float2 p) {
        constexpr sampler atlasSampler(coord::pixel, address::clamp_to_edge, filter::nearest);
        return uAtlas.sample(atlasSampler, p);
    }
    float sq(float v) { return v * v; }
    float roundedFace(float2 p, float2 halfSize, float radius) {
      float2 q = abs(p) - halfSize + radius;
      return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }
    float grit(float2 p) {
      return sampleAtlas(float2(1408.0, 0.0) + mod(p * 2.6, float2(128.0, 192.0))).r - 0.5;
    }
    float glyph(float tile, float2 uv) {
      if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
      return sampleAtlas(float2(tile * 128.0, 0.0) + clamp(uv * float2(128.0, 192.0),
        float2(1.0), float2(127.0, 191.0))).r;
    }
    float reel(float number, float roll, float2 uv) {
      // The cell clips a vertical strip: adjacent digits never cross-fade on the same pixels.
      if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
      float2 at = uv + float2(0.0, roll);
      return at.y < 1.0 ? glyph(mod(number, 10.0), at)
                       : glyph(mod(number + 1.0, 10.0), at - float2(0.0, 1.0));
    }
    float numeral(float3 p, float value, float fill) {
      float shown = fill > 0.012 ? value * min(fill, 1.0) : value;
      float whole = floor(shown);
      float roll = smoothstep(0.7, 1.0, fract(shown));
      float cellH = u.uSize.x * 0.60;
      float cellW = cellH * (128.0 / 192.0);
      float2 uv = (p.xy - float2(-u.uSize.x + u.uSize.x * 0.10, u.uSize.y - cellH - u.uSize.x * 0.10))
        / float2(cellW, cellH);
      float result = 0.0;
      if (shown >= 10.0 || (whole > 8.5 && roll > 0.0)) {
        float tensRoll = mod(whole, 10.0) > 8.5 ? roll : 0.0;
        result = reel(floor(whole / 10.0), tensRoll, uv);
        uv.x -= 0.65;
      }
      return max(result, reel(mod(whole, 10.0), roll, uv));
    }
    float3 haze(float2 xy) {
      float2 at = (xy - float2(u.uResolution.xy.x * 0.5, u.uBar.y - 145.0 * u.metrics.z))
        / float2(220.0 * u.metrics.z, 270.0 * u.metrics.z);
      return u.uHero.rgb * u.uHero.a * exp(-dot(at, at) * 1.5)
        * (0.050 + 0.045 * u.uMotion.x) * smoothstep(220.0 * u.metrics.z, 430.0 * u.metrics.z, xy.y);
    }
    void castBlade(float2 xy, float3 centre, float3 axisX, float3 axisY, float3 normal,
      float4 boundsArg, float4 card, float3 cardColour,
      thread float3 &accumulated, thread float3 &transmit, thread float2 &bend, thread float &frostBlur) {
        if (max(transmit.r, max(transmit.g, transmit.b)) < 0.025) return;
        float4 bounds = boundsArg;
        float2 sampleP = xy + bend;
        if (sampleP.x < bounds.x || sampleP.y < bounds.y || sampleP.x > bounds.z || sampleP.y > bounds.w
          || card.z < 0.001) return;
        float3 ray = normalize(float3((sampleP - u.uEye.xyz.xy) / u.uEye.xyz.z, -1.0));
        float3 delta = u.uEye.xyz - centre;
        float3 ro = float3(dot(delta, axisX), dot(delta, axisY), dot(delta, normal));
        float3 rd = float3(dot(ray, axisX), dot(ray, axisY), dot(ray, normal));
        // Two parallel plane hits and the closest point along their connecting chord.
        // The rounded silhouette is a swept SDF; no per-pixel sphere-marching or numerical normals.
        float zDenom = rd.z < 0.0 ? -max(abs(rd.z), 0.04) : max(rd.z, 0.04);
        float3 frontP = ro + rd * ((u.uSize.z - ro.z) / zDenom);
        float3 backP = ro + rd * ((-u.uSize.z - ro.z) / zDenom);
        float2 chord = backP.xy - frontP.xy;
        float along = clamp(-dot(frontP.xy, chord) / max(dot(chord, chord), 0.0001), 0.0, 1.0);
        float2 middleP = frontP.xy + chord * along;
        float frontD = roundedFace(frontP.xy, u.uSize.xy, u.uSize.w);
        float backD = roundedFace(backP.xy, u.uSize.xy, u.uSize.w);
        float silhouette = min(frontD, backD);
        if (silhouette > 0.0) {
          silhouette = min(silhouette, roundedFace(middleP, u.uSize.xy, u.uSize.w));
          // A chord that only clips a rounded corner is nearest the corner's own centre, not the blade's:
          // without this sample the wall shows a hairline gap at each corner.
          float2 cornerC = float2(u.uSize.x - u.uSize.w, (frontP.y + backP.y < 0.0 ? -1.0 : 1.0) * (u.uSize.y - u.uSize.w));
          float invChord = 1.0 / max(dot(chord, chord), 0.0001);
          float alongR = clamp(dot(cornerC - frontP.xy, chord) * invChord, 0.0, 1.0);
          float alongL = clamp(dot(float2(-cornerC.x, cornerC.y) - frontP.xy, chord) * invChord, 0.0, 1.0);
          silhouette = min(silhouette, min(roundedFace(frontP.xy + chord * alongR, u.uSize.xy, u.uSize.w),
            roundedFace(frontP.xy + chord * alongL, u.uSize.xy, u.uSize.w)));
        }
        float aa = (u.metrics.y * 0.65 + frostBlur) / max(0.22, abs(rd.z));
        float coverage = (1.0 - smoothstep(-aa, aa, silhouette)) * card.z;
        // A local air halo uses the existing face distance: no extra ray or texture tap.
        if (card.y > 0.01) {
          float haloWidth = (20.0 + 12.0 * u.uMotion.x) * u.metrics.z;
          float haloEdge = max(silhouette, 0.0) * max(0.22, abs(rd.z));
          float haloY = (u.uSize.y - frontP.y) / (2.0 * u.uSize.y);
          float halo = exp(-sq(haloEdge / haloWidth))
            * (1.0 - smoothstep(0.15, 0.68, haloY)) * smoothstep(-0.10, 0.04, haloY)
            * (1.0 - coverage) * card.y * card.z * (0.10 + 0.09 * u.uMotion.x);
          accumulated += transmit * cardColour * halo;
        }
        if (coverage < 0.001) return;
        float face = 1.0 - smoothstep(-0.8 * u.metrics.z - aa, aa, frontD);
        float3 p = frontP;
        // The face's antialiased edge reaches aa past its outline: switch to the wall's hit only beyond it,
        // or that last sliver of face is shaded from the far plane and shows as a dark hairline.
        if (frontD > aa) p.xy = frontD < backD ? frontP.xy : backP.xy;
        float2 corner = max(abs(p.xy) - u.uSize.xy + u.uSize.w, float2(0.0));
        float2 edgeNormal = length(corner) > 0.001 ? normalize(corner) * sign(p.xy)
          : (abs(p.x) / u.uSize.x > abs(p.y) / u.uSize.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y)));
        float bevel = 1.0 - smoothstep(0.0, 1.1 * u.metrics.z, -frontD);
        float3 n = normalize(float3(edgeNormal * (1.0 - face + bevel * 0.45), face));
        float3 nw = axisX * n.x + axisY * n.y + normal * n.z;
        float fromBottom = clamp((u.uSize.y - p.y) / (u.uSize.y * 2.0), 0.0, 1.0);
        float fill = card.y;
        float lit = min(fill, 1.0);
        float nearHero = exp(-sq(card.w / 1.65));
        float3 hue = cardColour;
        float fine = grit(p.xy);
        float cloud = sin(p.x / (34.0 * u.metrics.z) + sin(p.y / (65.0 * u.metrics.z)))
          * sin(p.y / (46.0 * u.metrics.z)) * 0.026;
        float height = card.x * 0.01 * lit;
        float pour = (1.0 - smoothstep(height - 0.30, height + 0.40, fromBottom + cloud)) * lit;
        float ember = (0.34 + 0.30 * nearHero) * (1.0 - smoothstep(0.10, 0.62, fromBottom));
        float fresnel = pow(clamp(1.0 - abs(dot(nw, -ray)), 0.0, 1.0), 4.0);
        float diagonal = exp(-sq((p.x / u.uSize.x * 0.35 + p.y / u.uSize.y + 0.04) / 0.57));
        float topTone = hue.g > 0.5 ? 0.39 : 0.14;
        float room = 0.073 + topTone * lit * (0.70 + 0.30 * fromBottom);
        float roomFacing = exp(-sq((abs(card.w) - 1.0) / 0.82));
        room += diagonal * (0.025 + 0.42 * roomFacing * (1.0 - lit));
        float3 body = float3(room * 0.98, room, room * 0.97);
        // Sampled reference bases; the contract hue is the transported light, not a flat face paint.
        float3 base = hue.g < 0.5 ? float3(0.79, 0.27, 0.012)
          : (hue.b > 0.5 ? float3(0.012, 0.855, 0.812) : float3(0.842, 0.854, 0.090));
        body = mix(body, base, pour);
        float3 ownEmber = hue.g < 0.5 ? float3(0.60, 0.018, 0.022)
          : (hue.b > 0.5 ? float3(0.0, 0.70, 0.57) : float3(0.36, 0.43, 0.008));
        body = mix(body, ownEmber, ember * (1.0 - lit) * u.uMotion.w);
        float bounce = exp(-sq((fromBottom - 0.34) / 0.14)) * nearHero * (1.0 - lit)
          * (0.09 + 0.09 * diagonal) * u.uHero.a;
        body += u.uHero.rgb * bounce * (1.0 + u.uMotion.x * 0.8);
        float barBounce = exp(-sq((xy.y - (u.uBar.y - u.uBar.w)) / (37.0 * u.metrics.z)))
          * u.uMotion.z * 0.016;
        body += u.uLime.xyz * barBounce;
        body *= 1.0 + clamp(fine * 3.0, -1.0, 1.0) * (0.035 + pour * 0.01);
        body += fine * 0.004;
        body += base * pour * u.uMotion.x * 0.35;
        body = mix(body, float3(1.0, 0.98, 0.77), u.uMotion.x * lit
          * (1.0 - smoothstep(0.0, 0.20, fromBottom)) * 0.28);

        float faceD = frontD > aa ? min(frontD, backD) : frontD;
        // Compensate for foreshortening so the bevel remains 1.5–2 screen points.
        float edgeMetric = length(float2(edgeNormal.x / max(0.18, abs(rd.z)), edgeNormal.y));
        float rimWidth = 1.65 * u.metrics.z * edgeMetric;
        float innerRim = exp(-sq((faceD + rimWidth * 1.4) / (0.45 * u.metrics.z * edgeMetric + frostBlur)));
        float polished = 1.0 - smoothstep(rimWidth * 0.35, rimWidth + aa, -faceD);
        float key = clamp(0.45 - 0.35 * edgeNormal.x - 0.25 * edgeNormal.y, 0.0, 1.0);
        key *= 0.82 + 0.18 * sin(fromBottom * 3.14159);
        // Fraction of the way through the wall, from the two plane hits: it stays 0–1 round a corner,
        // where the chord's length does not, and that drew a mitre line across the wall.
        float wallAcross = mix(clamp(frontD / max(frontD - backD, 0.001), 0.0, 1.0), 1.0,
          smoothstep(-3.0 * u.metrics.z, 0.0, backD));
        float wallLight = 0.49 + 0.23 * key + 0.06 * fresnel;
        wallLight *= 0.72 + 0.28 * cos(wallAcross * 6.28318);
        float3 wall = float3(wallLight * 0.99, wallLight, wallLight * 0.97);
        wall = mix(wall, base * 0.65, pour * 0.14);
        float3 rimColour = float3(0.24, 0.25, 0.235) + float3(0.43) * key;
        // The rim is laid over the body before the wall is blended in: blending the wall with the dark body
        // first left a one-pixel dark line where face meets wall.
        float3 colour = mix(wall, mix(body, rimColour, polished * 0.92), face);
        colour += float3(0.040) * innerRim * face;
        float travelling = exp(-sq((fromBottom - (0.15 + u.uMotion.x * 0.70)) / 0.08));
        colour += float3(0.7, 0.8, 0.56) * polished * travelling * u.uMotion.x * lit;
        colour += max(fine - 0.24, 0.0) * u.uMotion.x * lit * 0.12;

        if (face > 0.2) {
          float numberField = numeral(p, card.x, fill);
          float glyphAA = clamp(u.metrics.y / u.metrics.z * 0.90 / max(0.3, abs(rd.z)), 0.12, 0.49);
          float ink = smoothstep(0.5 - glyphAA, 0.5 + glyphAA, numberField);
          float outline = (smoothstep(0.10, 0.35, numberField) - smoothstep(0.48, 0.72, numberField));
          colour = mix(colour, base * 0.085, ink * smoothstep(0.015, 0.24, fill) * face * 0.96);
          colour += float3(0.075) * outline * (1.0 - smoothstep(0.02, 0.3, fill)) * face;
          float2 markUV = (p.xy - float2(-u.uSize.x + 6.0 * u.metrics.z, -u.uSize.y + 9.0 * u.metrics.z))
            / (float2(11.0, 16.5) * u.metrics.z);
          float mark = glyph(10.0, markUV);
          colour += float3(0.035) * smoothstep(0.20, 0.60, mark) * face;
        }
        float alpha = mix(0.98, 0.66 + 0.31 * pour, face);
        alpha = max(alpha, polished * 0.98);
        accumulated += transmit * colour * (coverage * alpha);
        float3 tint = mix(float3(0.91), base * 0.45 + float3(0.38), 0.38 + pour * 0.30);
        transmit *= mix(float3(1.0), tint * (1.0 - alpha), coverage);
        // Front-to-back transmission carries both blur and the bevel's bent ray into every rear slab.
        bend += nw.xy * (polished * 2.8 + (1.0 - face) * 1.2) * coverage * u.metrics.z;
        frostBlur += coverage * face * (1.0 - pour * 0.6) * 1.15 * u.metrics.z;
    }
    half4 evaluate(float2 xy) {
      float3 background = haze(xy);
      // The header costs no blade intersections, no texture reads, and no ray setup.
      if (xy.y < u.metrics.w) return half4(half3(background), 1.0);

      float barScale = 1.0 - 0.03 * u.uMotion.y;
      float2 barP = (xy - u.uBar.xy) / barScale;
      float barD = roundedFace(barP, u.uBar.zw, u.metrics.x);
      float barCover = 1.0 - smoothstep(-u.metrics.y, u.metrics.y, barD);
      if (barCover > 0.999) {
        float energy = clamp(u.uMotion.z, 0.0, 1.0);
        float topGlow = exp(-sq((barP.y + u.uBar.w - 0.8) / 1.0));
        float rim = 1.0 - smoothstep(0.0, 1.3, -barD);
        float3 barColour = mix(float3(0.075, 0.082, 0.073), u.uLime.xyz, energy);
        barColour *= 1.0 - 0.08 * u.uMotion.y;
        barColour += float3(0.12) * topGlow + mix(u.uLime.xyz * 0.28, float3(0.09), energy) * rim;
        barColour += u.uHero.rgb * u.uHero.a * topGlow * (0.025 + 0.045 * u.uMotion.x);
        barColour += grit(xy) * 0.035;
        return half4(half3(clamp(barColour, 0.0, 1.0)), 1.0);
      }

      float3 accumulated = float3(0.0);
      float3 transmit = float3(1.0);
      float2 bend = float2(0.0);
      float frostBlur = 0.0;
      castBlade(xy, u.uCentre[0].xyz, u.uAxisX[0].xyz, u.uAxisY[0].xyz, u.uNormal[0].xyz, u.uBounds[0], u.uCard[0], u.uColour[0].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[1].xyz, u.uAxisX[1].xyz, u.uAxisY[1].xyz, u.uNormal[1].xyz, u.uBounds[1], u.uCard[1], u.uColour[1].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[2].xyz, u.uAxisX[2].xyz, u.uAxisY[2].xyz, u.uNormal[2].xyz, u.uBounds[2], u.uCard[2], u.uColour[2].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[3].xyz, u.uAxisX[3].xyz, u.uAxisY[3].xyz, u.uNormal[3].xyz, u.uBounds[3], u.uCard[3], u.uColour[3].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[4].xyz, u.uAxisX[4].xyz, u.uAxisY[4].xyz, u.uNormal[4].xyz, u.uBounds[4], u.uCard[4], u.uColour[4].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[5].xyz, u.uAxisX[5].xyz, u.uAxisY[5].xyz, u.uNormal[5].xyz, u.uBounds[5], u.uCard[5], u.uColour[5].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[6].xyz, u.uAxisX[6].xyz, u.uAxisY[6].xyz, u.uNormal[6].xyz, u.uBounds[6], u.uCard[6], u.uColour[6].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[7].xyz, u.uAxisX[7].xyz, u.uAxisY[7].xyz, u.uNormal[7].xyz, u.uBounds[7], u.uCard[7], u.uColour[7].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[8].xyz, u.uAxisX[8].xyz, u.uAxisY[8].xyz, u.uNormal[8].xyz, u.uBounds[8], u.uCard[8], u.uColour[8].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[9].xyz, u.uAxisX[9].xyz, u.uAxisY[9].xyz, u.uNormal[9].xyz, u.uBounds[9], u.uCard[9], u.uColour[9].xyz, accumulated, transmit, bend, frostBlur);
      castBlade(xy, u.uCentre[10].xyz, u.uAxisX[10].xyz, u.uAxisY[10].xyz, u.uNormal[10].xyz, u.uBounds[10], u.uCard[10], u.uColour[10].xyz, accumulated, transmit, bend, frostBlur);
      // The foreground falls to black behind the bar, hiding rising feet in the bottom safe area.
      float floorFade = 1.0 - smoothstep(u.uBar.y - u.uBar.w + 6.0 * u.metrics.z,
        u.uBar.y + u.uBar.w + 5.0 * u.metrics.z, xy.y);
      float3 result = (accumulated + transmit * background) * floorFade;
      if (barCover > 0.0) {
        float3 barColour = mix(float3(0.075, 0.082, 0.073), u.uLime.xyz, clamp(u.uMotion.z, 0.0, 1.0));
        result = mix(result, barColour * (1.0 - 0.08 * u.uMotion.y), barCover);
      }
      return half4(half3(clamp(result, 0.0, 1.0)), 1.0);
    }
};

fragment half4 glassBladeDrawFragment(GlassBladeDrawVertex inVertex [[stage_in]],
    constant GlassBladeDrawUniforms &uniforms [[buffer(0)]],
    texture2d<float> atlas [[texture(0)]]) {
    GlassBladeDrawShader shader{uniforms, atlas};
    return shader.evaluate(inVertex.position.xy / uniforms.uResolution.z);
}
