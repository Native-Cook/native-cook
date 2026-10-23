/** Numeric uniforms are emitted in this order by bladeUniforms; uAtlas is the sole child shader. */
export const BLADE_UNIFORMS = [
  'uResolution', 'uEye', 'uSize', 'uCentre', 'uAxisX', 'uAxisY', 'uNormal', 'uBounds',
  'uCard', 'uColour', 'uHero', 'uBar', 'uBarRadius', 'uLime', 'uMotion', 'uPixel', 'uScale', 'uFanTop',
] as const;

/** Eleven statically addressed front-to-back ray casts (avoids dynamic array indexing on mobile), with analytic plane hits and a swept rounded-rectangle silhouette. */
export const BLADE_SKSL = `
uniform shader uAtlas;
uniform float2 uResolution;
uniform float3 uEye;
uniform float4 uSize;
uniform float3 uCentre[11];
uniform float3 uAxisX[11];
uniform float3 uAxisY[11];
uniform float3 uNormal[11];
uniform float4 uBounds[11];
uniform float4 uCard[11];
uniform float3 uColour[11];
uniform float4 uHero;
uniform float4 uBar;
uniform float uBarRadius;
uniform float3 uLime;
uniform float4 uMotion;
uniform float uPixel;
uniform float uScale;
uniform float uFanTop;

float sq(float v) { return v * v; }
float roundedFace(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float grit(float2 p) {
  return uAtlas.eval(float2(1408.0, 0.0) + mod(p * 2.6, float2(128.0, 192.0))).r - 0.5;
}
float glyph(float tile, float2 uv) {
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
  return uAtlas.eval(float2(tile * 128.0, 0.0) + clamp(uv * float2(128.0, 192.0),
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
  float cellH = uSize.x * 0.60;
  float cellW = cellH * (128.0 / 192.0);
  float2 uv = (p.xy - float2(-uSize.x + uSize.x * 0.10, uSize.y - cellH - uSize.x * 0.10))
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
  float2 at = (xy - float2(uResolution.x * 0.5, uBar.y - 145.0 * uScale))
    / float2(220.0 * uScale, 270.0 * uScale);
  return uHero.rgb * uHero.a * exp(-dot(at, at) * 1.5)
    * (0.050 + 0.045 * uMotion.x) * smoothstep(220.0 * uScale, 430.0 * uScale, xy.y);
}
void castBlade(float2 xy, float3 centre, float3 axisX, float3 axisY, float3 normal,
  float4 boundsArg, float4 card, float3 cardColour,
  inout float3 accumulated, inout float3 transmit, inout float2 bend, inout float frostBlur) {
    if (max(transmit.r, max(transmit.g, transmit.b)) < 0.025) return;
    float4 bounds = boundsArg;
    float2 sampleP = xy + bend;
    if (sampleP.x < bounds.x || sampleP.y < bounds.y || sampleP.x > bounds.z || sampleP.y > bounds.w
      || card.z < 0.001) return;
    float3 ray = normalize(float3((sampleP - uEye.xy) / uEye.z, -1.0));
    float3 delta = uEye - centre;
    float3 ro = float3(dot(delta, axisX), dot(delta, axisY), dot(delta, normal));
    float3 rd = float3(dot(ray, axisX), dot(ray, axisY), dot(ray, normal));
    // Two parallel plane hits and the closest point along their connecting chord.
    // The rounded silhouette is a swept SDF; no per-pixel sphere-marching or numerical normals.
    float zDenom = rd.z < 0.0 ? -max(abs(rd.z), 0.04) : max(rd.z, 0.04);
    float3 frontP = ro + rd * ((uSize.z - ro.z) / zDenom);
    float3 backP = ro + rd * ((-uSize.z - ro.z) / zDenom);
    float2 chord = backP.xy - frontP.xy;
    float along = clamp(-dot(frontP.xy, chord) / max(dot(chord, chord), 0.0001), 0.0, 1.0);
    float2 middleP = frontP.xy + chord * along;
    float frontD = roundedFace(frontP.xy, uSize.xy, uSize.w);
    float backD = roundedFace(backP.xy, uSize.xy, uSize.w);
    float silhouette = min(frontD, backD);
    if (silhouette > 0.0) {
      silhouette = min(silhouette, roundedFace(middleP, uSize.xy, uSize.w));
      // A chord that only clips a rounded corner is nearest the corner's own centre, not the blade's:
      // without this sample the wall shows a hairline gap at each corner.
      float2 cornerC = float2(uSize.x - uSize.w, (frontP.y + backP.y < 0.0 ? -1.0 : 1.0) * (uSize.y - uSize.w));
      float invChord = 1.0 / max(dot(chord, chord), 0.0001);
      float alongR = clamp(dot(cornerC - frontP.xy, chord) * invChord, 0.0, 1.0);
      float alongL = clamp(dot(float2(-cornerC.x, cornerC.y) - frontP.xy, chord) * invChord, 0.0, 1.0);
      silhouette = min(silhouette, min(roundedFace(frontP.xy + chord * alongR, uSize.xy, uSize.w),
        roundedFace(frontP.xy + chord * alongL, uSize.xy, uSize.w)));
    }
    float aa = (uPixel * 0.65 + frostBlur) / max(0.22, abs(rd.z));
    float coverage = (1.0 - smoothstep(-aa, aa, silhouette)) * card.z;
    // A local air halo uses the existing face distance: no extra ray or texture tap.
    if (card.y > 0.01) {
      float haloWidth = (20.0 + 12.0 * uMotion.x) * uScale;
      float haloEdge = max(silhouette, 0.0) * max(0.22, abs(rd.z));
      float haloY = (uSize.y - frontP.y) / (2.0 * uSize.y);
      float halo = exp(-sq(haloEdge / haloWidth))
        * (1.0 - smoothstep(0.15, 0.68, haloY)) * smoothstep(-0.10, 0.04, haloY)
        * (1.0 - coverage) * card.y * card.z * (0.10 + 0.09 * uMotion.x);
      accumulated += transmit * cardColour * halo;
    }
    if (coverage < 0.001) return;
    float face = 1.0 - smoothstep(-0.8 * uScale - aa, aa, frontD);
    float3 p = frontP;
    // The face's antialiased edge reaches aa past its outline: switch to the wall's hit only beyond it,
    // or that last sliver of face is shaded from the far plane and shows as a dark hairline.
    if (frontD > aa) p.xy = frontD < backD ? frontP.xy : backP.xy;
    float2 corner = max(abs(p.xy) - uSize.xy + uSize.w, float2(0.0));
    float2 edgeNormal = length(corner) > 0.001 ? normalize(corner) * sign(p.xy)
      : (abs(p.x) / uSize.x > abs(p.y) / uSize.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y)));
    float bevel = 1.0 - smoothstep(0.0, 1.1 * uScale, -frontD);
    float3 n = normalize(float3(edgeNormal * (1.0 - face + bevel * 0.45), face));
    float3 nw = axisX * n.x + axisY * n.y + normal * n.z;
    float fromBottom = clamp((uSize.y - p.y) / (uSize.y * 2.0), 0.0, 1.0);
    float fill = card.y;
    float lit = min(fill, 1.0);
    float nearHero = exp(-sq(card.w / 1.65));
    float3 hue = cardColour;
    float fine = grit(p.xy);
    float cloud = sin(p.x / (34.0 * uScale) + sin(p.y / (65.0 * uScale)))
      * sin(p.y / (46.0 * uScale)) * 0.026;
    float height = card.x * 0.01 * lit;
    float pour = (1.0 - smoothstep(height - 0.30, height + 0.40, fromBottom + cloud)) * lit;
    float ember = (0.34 + 0.30 * nearHero) * (1.0 - smoothstep(0.10, 0.62, fromBottom));
    float fresnel = pow(clamp(1.0 - abs(dot(nw, -ray)), 0.0, 1.0), 4.0);
    float diagonal = exp(-sq((p.x / uSize.x * 0.35 + p.y / uSize.y + 0.04) / 0.57));
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
    body = mix(body, ownEmber, ember * (1.0 - lit) * uMotion.w);
    float bounce = exp(-sq((fromBottom - 0.34) / 0.14)) * nearHero * (1.0 - lit)
      * (0.09 + 0.09 * diagonal) * uHero.a;
    body += uHero.rgb * bounce * (1.0 + uMotion.x * 0.8);
    float barBounce = exp(-sq((xy.y - (uBar.y - uBar.w)) / (37.0 * uScale)))
      * uMotion.z * 0.016;
    body += uLime * barBounce;
    body *= 1.0 + clamp(fine * 3.0, -1.0, 1.0) * (0.035 + pour * 0.01);
    body += fine * 0.004;
    body += base * pour * uMotion.x * 0.35;
    body = mix(body, float3(1.0, 0.98, 0.77), uMotion.x * lit
      * (1.0 - smoothstep(0.0, 0.20, fromBottom)) * 0.28);

    float faceD = frontD > aa ? min(frontD, backD) : frontD;
    // Compensate for foreshortening so the bevel remains 1.5–2 screen points.
    float edgeMetric = length(float2(edgeNormal.x / max(0.18, abs(rd.z)), edgeNormal.y));
    float rimWidth = 1.65 * uScale * edgeMetric;
    float innerRim = exp(-sq((faceD + rimWidth * 1.4) / (0.45 * uScale * edgeMetric + frostBlur)));
    float polished = 1.0 - smoothstep(rimWidth * 0.35, rimWidth + aa, -faceD);
    float key = clamp(0.45 - 0.35 * edgeNormal.x - 0.25 * edgeNormal.y, 0.0, 1.0);
    key *= 0.82 + 0.18 * sin(fromBottom * 3.14159);
    // Fraction of the way through the wall, from the two plane hits: it stays 0–1 round a corner,
    // where the chord's length does not, and that drew a mitre line across the wall.
    float wallAcross = mix(clamp(frontD / max(frontD - backD, 0.001), 0.0, 1.0), 1.0,
      smoothstep(-3.0 * uScale, 0.0, backD));
    float wallLight = 0.49 + 0.23 * key + 0.06 * fresnel;
    wallLight *= 0.72 + 0.28 * cos(wallAcross * 6.28318);
    float3 wall = float3(wallLight * 0.99, wallLight, wallLight * 0.97);
    wall = mix(wall, base * 0.65, pour * 0.14);
    float3 rimColour = float3(0.24, 0.25, 0.235) + float3(0.43) * key;
    // The rim is laid over the body before the wall is blended in: blending the wall with the dark body
    // first left a one-pixel dark line where face meets wall.
    float3 colour = mix(wall, mix(body, rimColour, polished * 0.92), face);
    colour += float3(0.040) * innerRim * face;
    float travelling = exp(-sq((fromBottom - (0.15 + uMotion.x * 0.70)) / 0.08));
    colour += float3(0.7, 0.8, 0.56) * polished * travelling * uMotion.x * lit;
    colour += max(fine - 0.24, 0.0) * uMotion.x * lit * 0.12;

    if (face > 0.2) {
      float numberField = numeral(p, card.x, fill);
      float glyphAA = clamp(uPixel / uScale * 0.90 / max(0.3, abs(rd.z)), 0.12, 0.49);
      float ink = smoothstep(0.5 - glyphAA, 0.5 + glyphAA, numberField);
      float outline = (smoothstep(0.10, 0.35, numberField) - smoothstep(0.48, 0.72, numberField));
      colour = mix(colour, base * 0.085, ink * smoothstep(0.015, 0.24, fill) * face * 0.96);
      colour += float3(0.075) * outline * (1.0 - smoothstep(0.02, 0.3, fill)) * face;
      float2 markUV = (p.xy - float2(-uSize.x + 6.0 * uScale, -uSize.y + 9.0 * uScale))
        / (float2(11.0, 16.5) * uScale);
      float mark = glyph(10.0, markUV);
      colour += float3(0.035) * smoothstep(0.20, 0.60, mark) * face;
    }
    float alpha = mix(0.98, 0.66 + 0.31 * pour, face);
    alpha = max(alpha, polished * 0.98);
    accumulated += transmit * colour * (coverage * alpha);
    float3 tint = mix(float3(0.91), base * 0.45 + float3(0.38), 0.38 + pour * 0.30);
    transmit *= mix(float3(1.0), tint * (1.0 - alpha), coverage);
    // Front-to-back transmission carries both blur and the bevel's bent ray into every rear slab.
    bend += nw.xy * (polished * 2.8 + (1.0 - face) * 1.2) * coverage * uScale;
    frostBlur += coverage * face * (1.0 - pour * 0.6) * 1.15 * uScale;
}
half4 main(float2 xy) {
  float3 background = haze(xy);
  // The header costs no blade intersections, no texture reads, and no ray setup.
  if (xy.y < uFanTop) return half4(half3(background), 1.0);

  float barScale = 1.0 - 0.03 * uMotion.y;
  float2 barP = (xy - uBar.xy) / barScale;
  float barD = roundedFace(barP, uBar.zw, uBarRadius);
  float barCover = 1.0 - smoothstep(-uPixel, uPixel, barD);
  if (barCover > 0.999) {
    float energy = clamp(uMotion.z, 0.0, 1.0);
    float topGlow = exp(-sq((barP.y + uBar.w - 0.8) / 1.0));
    float rim = 1.0 - smoothstep(0.0, 1.3, -barD);
    float3 barColour = mix(float3(0.075, 0.082, 0.073), uLime, energy);
    barColour *= 1.0 - 0.08 * uMotion.y;
    barColour += float3(0.12) * topGlow + mix(uLime * 0.28, float3(0.09), energy) * rim;
    barColour += uHero.rgb * uHero.a * topGlow * (0.025 + 0.045 * uMotion.x);
    barColour += grit(xy) * 0.035;
    return half4(half3(clamp(barColour, 0.0, 1.0)), 1.0);
  }

  float3 accumulated = float3(0.0);
  float3 transmit = float3(1.0);
  float2 bend = float2(0.0);
  float frostBlur = 0.0;
  castBlade(xy, uCentre[0], uAxisX[0], uAxisY[0], uNormal[0], uBounds[0], uCard[0], uColour[0], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[1], uAxisX[1], uAxisY[1], uNormal[1], uBounds[1], uCard[1], uColour[1], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[2], uAxisX[2], uAxisY[2], uNormal[2], uBounds[2], uCard[2], uColour[2], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[3], uAxisX[3], uAxisY[3], uNormal[3], uBounds[3], uCard[3], uColour[3], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[4], uAxisX[4], uAxisY[4], uNormal[4], uBounds[4], uCard[4], uColour[4], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[5], uAxisX[5], uAxisY[5], uNormal[5], uBounds[5], uCard[5], uColour[5], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[6], uAxisX[6], uAxisY[6], uNormal[6], uBounds[6], uCard[6], uColour[6], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[7], uAxisX[7], uAxisY[7], uNormal[7], uBounds[7], uCard[7], uColour[7], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[8], uAxisX[8], uAxisY[8], uNormal[8], uBounds[8], uCard[8], uColour[8], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[9], uAxisX[9], uAxisY[9], uNormal[9], uBounds[9], uCard[9], uColour[9], accumulated, transmit, bend, frostBlur);
  castBlade(xy, uCentre[10], uAxisX[10], uAxisY[10], uNormal[10], uBounds[10], uCard[10], uColour[10], accumulated, transmit, bend, frostBlur);
  // The foreground falls to black behind the bar, hiding rising feet in the bottom safe area.
  float floorFade = 1.0 - smoothstep(uBar.y - uBar.w + 6.0 * uScale,
    uBar.y + uBar.w + 5.0 * uScale, xy.y);
  float3 result = (accumulated + transmit * background) * floorFade;
  if (barCover > 0.0) {
    float3 barColour = mix(float3(0.075, 0.082, 0.073), uLime, clamp(uMotion.z, 0.0, 1.0));
    result = mix(result, barColour * (1.0 - 0.08 * uMotion.y), barCover);
  }
  return half4(half3(clamp(result, 0.0, 1.0)), 1.0);
}
`;
