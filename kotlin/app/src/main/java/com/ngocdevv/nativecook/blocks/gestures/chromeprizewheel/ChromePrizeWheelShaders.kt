package com.ngocdevv.nativecook.blocks.gestures.chromeprizewheel

/** AGSL translation of the React Native SkSL. Uniform names and surface maths are unchanged. */
internal const val ChromePrizeWheelAgsl = """
uniform shader uEnv;
uniform shader uEnvSoft;
uniform shader uPrizes;
uniform float2 uCentre;
uniform float uRadius;
uniform float uPixelRatio;
uniform float uTheta;
uniform float uSpan;
uniform float2 uTilt;
uniform float uEnvTurn;
uniform float uSwayA;
uniform float uSwayB;
uniform float uWinner;
uniform float uLift;
uniform float uGlow;
uniform float uWinnerPrize;
uniform float uWinnerPrizeScale;
uniform float uDim;
uniform float uComet;
uniform float uHubPress;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;
const float SECTOR = PI / 3.0;
const float BAND_INNER = 0.955;
const float RING_INNER = 0.855;
const float FACE_OUTER = 0.825;
const float WEDGE_INNER = 0.3;
const float CORNER = 0.045;
const float CHANNEL = 0.036;
const float HUB = 0.215;
const float DISC = 0.168;
const float HUB_FACE = DISC - 0.010;
const float HUB_CAP = 0.060;
const float HUB_GROOVE = 0.075;
const float KNURL_COUNT = 108.0;
const float PEG_RADIUS = 0.905;
const float PEG_SIZE = 0.026;
const float PEG_PHASE = 0.17453292519943295;
const float PEG_SPACING = 0.3490658503988659;
const float TILE = 0.46;
const float PRIZE_R = 0.575;

float sq(float value) { return value * value; }
float2 turn(float2 point, float angle) {
  float c = cos(angle), s = sin(angle);
  return float2(c * point.x - s * point.y, s * point.x + c * point.y);
}
float wrap(float angle) { return mod(angle + PI, TAU) - PI; }
float aaWidth() { return 0.8 / (uRadius * uPixelRatio); }
float cover(float field) { return 1.0 - smoothstep(-aaWidth(), aaWidth(), field); }
float3 focus(float3 rgb, float strength) {
  float grey = dot(rgb, float3(0.2126, 0.7152, 0.0722));
  return mix(rgb, float3(grey), 0.19 * uDim * min(strength / 0.62, 1.0)) * (1.0 - strength * uDim);
}
float3 surfaceNormal(float2 slope, float z) {
  float3 normal = normalize(float3(slope.x, -slope.y, z));
  float cx = cos(uTilt.x), sx = sin(uTilt.x);
  float cy = cos(uTilt.y), sy = sin(uTilt.y);
  normal = float3(cx * normal.x + sx * normal.z, normal.y, -sx * normal.x + cx * normal.z);
  return float3(normal.x, cy * normal.y - sy * normal.z, sy * normal.y + cy * normal.z);
}
float2 envUV(float3 normal) {
  float3 reflected = reflect(float3(0.0, 0.0, -1.0), normal);
  reflected.xy = turn(reflected.xy, uEnvTurn);
  return float2(0.5 + atan(reflected.x, reflected.z) / TAU,
    0.5 - asin(clamp(reflected.y, -1.0, 1.0)) / PI);
}
float3 sharpEnv(float3 normal) { return uEnv.eval(envUV(normal) * float2(1024.0, 512.0)).rgb; }
float3 softEnv(float3 normal) { return uEnvSoft.eval(envUV(normal) * float2(256.0, 128.0)).rgb; }
float3 chrome(float3 normal, float blue) {
  float3 f0 = mix(float3(0.92, 0.93, 0.97), float3(0.10, 0.14, 0.55), blue);
  float fresnel = pow(max(1.0 - normal.z, 0.0), 5.0);
  return sharpEnv(normal) * mix(f0, float3(1.0), fresnel) * 1.10;
}
float wedgeField(float2 point, float angle) {
  float2 q = turn(point, -angle);
  float radial = length(q);
  float3 edges = float3(radial - FACE_OUTER, WEDGE_INNER - radial,
    abs(q.y) * 0.866025404 - q.x * 0.5 + CHANNEL * 0.5) + CORNER;
  return length(max(edges, 0.0)) + min(max(max(edges.x, edges.y), edges.z), 0.0) - CORNER;
}
float2 wedgeGradient(float2 point, float angle) {
  const float epsilon = 0.0008;
  return float2(
    wedgeField(point + float2(epsilon, 0.0), angle) - wedgeField(point - float2(epsilon, 0.0), angle),
    wedgeField(point + float2(0.0, epsilon), angle) - wedgeField(point - float2(0.0, epsilon), angle)
  ) / (2.0 * epsilon);
}
float3 enamel(float2 point, float angle, float field, float winning) {
  float radial = length(point);
  float bevel = 1.0 - smoothstep(0.0, 0.027, -field);
  float2 gradient = wedgeGradient(point, angle);
  float3 normal = surfaceNormal(gradient * bevel * 1.9 + point * 0.10, 1.0);
  float illumination = max(dot(normal, normalize(float3(-0.48, 0.62, 0.72))), 0.0);
  float radialMix = smoothstep(WEDGE_INNER, FACE_OUTER, radial);
  float3 blue = mix(float3(0.090, 0.188, 0.69), float3(0.18, 0.482, 1.0), radialMix);
  float edgeOcclusion = 1.0 - 0.40 * exp(-max(-field, 0.0) * 65.0);
  float3 rgb = blue * (0.46 + 0.52 * illumination) * edgeOcclusion;
  float fresnel = 0.055 + 0.36 * pow(max(1.0 - normal.z, 0.0), 3.0);
  rgb += softEnv(normal) * fresnel;
  float sheen = exp(-sq((point.y + 0.46 + 0.33 * point.x) / 0.19));
  rgb += float3(0.20, 0.40, 0.74) * sheen * 0.25;
  rgb += float3(0.23, 0.43, 0.76) * bevel * sq(illumination) * (0.30 + 0.45 * winning * uLift);
  float edgeLight = dot(gradient, normalize(float2(-0.48, -0.62)));
  float inset = max(-field, 0.0);
  float glintWidth = max(0.0022, aaWidth() * 1.15);
  float edgeGlint = exp(-sq((inset - 0.010) / glintWidth)) * max(edgeLight, 0.0);
  rgb += float3(0.43, 0.65, 0.92) * edgeGlint * (0.34 + 0.18 * winning * uLift);
  rgb *= 1.0 - 0.24 * max(-edgeLight, 0.0) * exp(-sq((inset - 0.015) / 0.022));
  rgb = focus(rgb, 0.62 * (1.0 - winning));
  float2 core = point - turn(float2(0.57, 0.0), angle);
  float heart = exp(-dot(core, core) * 13.0);
  rgb += winning * uGlow * (float3(0.04, 0.29, 0.48) + float3(0.48, 0.75, 0.80) * heart);
  return rgb;
}
float swayFor(float index) {
  if (index < 0.5) return uSwayA * 1.00 + uSwayB * 0.25;
  if (index < 1.5) return uSwayA * 0.70 + uSwayB * -0.60;
  if (index < 2.5) return uSwayA * 0.85 + uSwayB * 0.50;
  if (index < 3.5) return uSwayA * 1.05 + uSwayB * -0.20;
  if (index < 4.5) return uSwayA * 0.60 + uSwayB * 0.75;
  return uSwayA * 0.90 + uSwayB * -0.45;
}
float4 prize(float2 point, float angle, float index, float winning) {
  float liftedScale = 1.0 + 0.07 * uLift * winning;
  float2 centre = turn(float2(PRIZE_R * liftedScale, 0.0), angle);
  float tileSide = TILE * liftedScale;
  float2 pivot = centre - float2(0.0, tileSide * 0.5);
  float2 q = turn(point - pivot, -swayFor(index)) - float2(0.0, tileSide * 0.5);
  float restock = mix(1.0, max(uWinnerPrizeScale, 0.001), winning);
  float2 uv = q / (tileSide * restock) + 0.5;
  if (min(uv.x, uv.y) < 0.0 || max(uv.x, uv.y) > 1.0) return float4(0.0);
  float2 tileOrigin = float2(mod(index, 3.0), floor(index / 3.0)) * 384.0;
  float4 rgba = uPrizes.eval(tileOrigin + clamp(uv * 384.0, 0.5, 383.5));
  rgba.rgb = focus(rgba.rgb, 0.62 * (1.0 - winning));
  return rgba * mix(1.0, uWinnerPrize, winning);
}
float3 ringBase(float2 point, float radial) {
  float2 radialNormal = point / max(radial, 0.00001);
  float3 rgb;
  if (radial > BAND_INNER) {
    return float3(0.0);
  } else if (radial > RING_INNER) {
    float section = clamp((radial - 0.905) / 0.050, -0.995, 0.995);
    float3 normal = surfaceNormal(radialNormal * section, sqrt(max(1.0 - section * section, 0.001)));
    rgb = chrome(normal, 0.0);
    float2 tangentNormal = float2(-radialNormal.y, radialNormal.x);
    rgb = rgb * 0.90 + 0.05 * (softEnv(surfaceNormal(radialNormal * section + tangentNormal * 0.13, normal.z)) +
      softEnv(surfaceNormal(radialNormal * section - tangentNormal * 0.13, normal.z)));
    rgb *= 1.0 - 0.88 * exp(-sq((radial - 0.865) / 0.0024));
    rgb += float3(0.30, 0.33, 0.43) * (exp(-sq((radial - 0.951) / 0.0018)) +
      0.65 * exp(-sq((radial - 0.859) / 0.0018)));
    float angle = atan(point.y, point.x);
    float cometOne = wrap(angle - (PI + uComet * PI));
    float cometTwo = -wrap(angle - (PI - uComet * PI));
    float spark = exp(-sq(cometOne / 0.050)) + exp(-sq(cometTwo / 0.050));
    float tail = exp(-sq((cometOne + 0.095) / 0.11)) + exp(-sq((cometTwo + 0.095) / 0.11));
    rgb += float3(0.90, 0.85, 1.0) * (spark * 1.9 + tail * 0.48) *
      exp(-sq((radial - 0.914) / 0.022)) * max(sin(PI * uComet), 0.0);
  } else {
    float wall = smoothstep(FACE_OUTER, RING_INNER, radial);
    rgb = float3(0.024, 0.033, 0.10) + float3(0.07, 0.085, 0.23) * wall;
    rgb += softEnv(surfaceNormal(-radialNormal * 1.8, 1.0)) * 0.12 * wall;
    rgb *= 1.0 - 0.42 * exp(-(radial - FACE_OUTER) / 0.009);
  }
  return focus(rgb, 0.45);
}
float3 pegsAndBand(float2 point, float radial, float rotation, float3 base) {
  float angle = atan(point.y, point.x);
  if (radial > BAND_INNER) {
    float notchAngle = (angle - rotation) * KNURL_COUNT;
    float fade = (1.0 - smoothstep(0.35, 1.1, KNURL_COUNT * aaWidth() / radial)) *
      (1.0 - smoothstep(0.012, 0.065, abs(uSpan)));
    float2 radialNormal = point / max(radial, 0.00001);
    float2 tangentNormal = float2(-radialNormal.y, radialNormal.x);
    float bevel = smoothstep(BAND_INNER, 1.0, radial);
    float knurl = sin(notchAngle + (radial - BAND_INNER) * 38.0);
    float3 normal = surfaceNormal(radialNormal * (0.24 + bevel * 1.2) + tangentNormal * 0.006 * knurl * fade, 1.0);
    base = chrome(normal, 1.0) + float3(0.14, 0.17, 0.33) * exp(-sq((radial - 0.996) / 0.002));
    float cutLight = 0.55 + 0.45 * max(dot(radialNormal, normalize(float2(-0.48, -0.62))), 0.0);
    base += float3(0.012, 0.020, 0.040) * knurl * fade * cutLight *
      smoothstep(BAND_INNER, BAND_INNER + 0.006, radial) * (1.0 - smoothstep(0.991, 1.0, radial));
    base = focus(base, 0.45);
  }
  float pegAngle = floor((angle - rotation - PEG_PHASE) / PEG_SPACING + 0.5) * PEG_SPACING + rotation + PEG_PHASE;
  float2 home = turn(float2(PEG_RADIUS, 0.0), pegAngle);
  float2 delta = point - home;
  float castDistance = length(delta - float2(0.012, 0.018));
  base *= 1.0 - 0.52 * exp(-sq(castDistance / (PEG_SIZE * 1.14)));
  float pegDistance = length(delta);
  base *= 1.0 - 0.52 * exp(-sq(pegDistance / (PEG_SIZE * 1.16)));
  if (pegDistance < PEG_SIZE + aaWidth()) {
    float2 ball = delta / PEG_SIZE;
    float3 normal = surfaceNormal(ball, sqrt(max(1.0 - dot(ball, ball), 0.0001)));
    float3 metal = focus(chrome(normal, 0.0), 0.45);
    base = mix(base, metal, cover(pegDistance - PEG_SIZE));
  }
  return base;
}
float3 faceAt(float2 point, float rotation) {
  float radial = length(point);
  float angle = atan(point.y, point.x);
  float index = mod(floor((angle - rotation + SECTOR * 0.5) / SECTOR), 6.0);
  float centreAngle = rotation + index * SECTOR;
  float winning = abs(index - uWinner) < 0.1 ? 1.0 : 0.0;
  float winnerAngle = rotation + uWinner * SECTOR;
  float liftedScale = 1.0 + 0.07 * uLift;
  float3 rgb = focus(float3(0.027, 0.043, 0.18), 0.62);
  float field = wedgeField(point, centreAngle);
  if (field < aaWidth() && (winning < 0.5 || uLift < 0.001)) {
    rgb = mix(rgb, enamel(point, centreAngle, field, winning), cover(field));
  }
  if (uWinner >= 0.0 && uLift > 0.001) {
    float shadowField = wedgeField((point - float2(0.018, 0.031) * uLift) / liftedScale, winnerAngle);
    float shadow = 1.0 - smoothstep(-0.005, 0.022 + uLift * 0.023, shadowField);
    rgb *= 1.0 - shadow * 0.67 * min(uLift, 1.0);
    float raisedDistance = wedgeField(point / liftedScale, winnerAngle) * liftedScale;
    float spill = exp(-max(raisedDistance, 0.0) / (0.012 + 0.02 * uGlow));
    rgb += float3(0.025, 0.17, 0.28) * uGlow * spill;
    float sideDistance = wedgeField((point - float2(0.002, 0.011) * uLift) / liftedScale, winnerAngle) * liftedScale;
    rgb = mix(rgb, float3(0.027, 0.11, 0.34) + uGlow * float3(0.03, 0.21, 0.35), cover(sideDistance));
    if (raisedDistance < aaWidth()) {
      rgb = mix(rgb, enamel(point / liftedScale, winnerAngle, raisedDistance / liftedScale, 1.0), cover(raisedDistance));
    }
  }
  rgb *= 1.0 - 0.55 * exp(-max(FACE_OUTER - radial, 0.0) * 70.0) * (1.0 - winning * uLift);
  for (int i = 0; i < 6; ++i) {
    float prizeIndex = float(i);
    float isWinner = abs(prizeIndex - uWinner) < 0.1 ? 1.0 : 0.0;
    if (isWinner < 0.5) {
      float4 decal = prize(point, rotation + prizeIndex * SECTOR, prizeIndex, 0.0);
      rgb = decal.rgb + rgb * (1.0 - decal.a);
    }
  }
  if (uWinner >= 0.0) {
    float4 decal = prize(point, winnerAngle, uWinner, 1.0);
    rgb = decal.rgb + rgb * (1.0 - decal.a);
  }
  float underHub = length(point - float2(0.008, 0.018));
  rgb *= 1.0 - 0.70 * (1.0 - smoothstep(HUB - 0.012, HUB + 0.040, underHub));
  return rgb;
}
float3 hubAt(float2 point, float radial) {
  float2 radialNormal = point / max(radial, 0.00001);
  if (radial > HUB_FACE) {
    float section = clamp((radial - (HUB_FACE + HUB) * 0.5) / ((HUB - HUB_FACE) * 0.5), -0.995, 0.995);
    float3 rgb = chrome(surfaceNormal(radialNormal * section, sqrt(max(1.0 - section * section, 0.001))), 0.0);
    rgb *= 1.0 - 0.72 * exp(-sq((radial - HUB_FACE - 0.005) / max(0.0018, aaWidth())));
    rgb += float3(0.22, 0.24, 0.30) * exp(-sq((radial - HUB + 0.003) / max(0.0015, aaWidth())));
    return focus(rgb, 0.62);
  }
  float3 normal = surfaceNormal(point * (0.35 + uHubPress * 0.3), 1.0);
  float3 room = mix(softEnv(normal), sharpEnv(normal), 0.12);
  float angle = atan(point.y, point.x) + uTilt.x * 3.0 + uTilt.y * 2.0 - uEnvTurn;
  float bow = pow(max(0.5 + 0.5 * cos(2.0 * (angle + 0.78)), 0.0), 1.25 + uHubPress * 1.65);
  float3 rgb = room * float3(0.30, 0.31, 0.34) +
    mix(float3(0.065, 0.075, 0.105), float3(0.69, 0.72, 0.80), bow);
  float brushFade = smoothstep(0.02, 0.12, radial) * (1.0 - smoothstep(0.7, 4.0, aaWidth() * 180.0 / max(radial, 0.01)));
  rgb += (sin(angle * 180.0) + 0.5 * sin(angle * 293.0)) * 0.010 * brushFade;
  rgb += sin(radial * 3400.0) * 0.008 * (1.0 - smoothstep(0.7, 2.2, aaWidth() * 3400.0));
  float grooveWidth = max(0.0014, aaWidth());
  rgb *= 1.0 - 0.72 * exp(-sq((radial - HUB_GROOVE) / grooveWidth));
  rgb += float3(0.13, 0.15, 0.20) * exp(-sq((radial - HUB_GROOVE - 0.0028) / grooveWidth));
  rgb *= 1.0 - 0.48 * exp(-sq((radial - HUB_CAP) / 0.004));
  if (radial < HUB_CAP + aaWidth()) {
    float2 capXY = point / HUB_CAP;
    float capCurve = 0.88 + 0.045 * uHubPress;
    float3 capNormal = surfaceNormal(capXY * capCurve, sqrt(max(1.0 - dot(capXY, capXY) * sq(capCurve), 0.001)));
    rgb = mix(rgb, chrome(capNormal, 0.0), cover(radial - HUB_CAP));
  }
  rgb *= (1.0 - 0.14 * uHubPress) * (1.0 - (0.32 + 0.38 * uHubPress) *
    exp(-max(HUB_FACE - radial, 0.0) / (0.003 + uHubPress * 0.010)));
  return focus(rgb, 0.62);
}
float3 movingAt(float2 point, float radial, float rotation, float3 base) {
  if (radial > RING_INNER) base = pegsAndBand(point, radial, rotation, base);
  if (radial > FACE_OUTER) {
    if (uLift < 0.001 || uWinner < 0.0 || radial > FACE_OUTER * (1.0 + 0.07 * uLift) + aaWidth()) return base;
    float raised = wedgeField(point / (1.0 + 0.07 * uLift), rotation + uWinner * SECTOR) * (1.0 + 0.07 * uLift);
    if (raised > aaWidth()) return base;
    return mix(base, faceAt(point, rotation), cover(raised));
  }
  return faceAt(point, rotation);
}
half4 main(float2 xy) {
  float2 point = (xy - uCentre) / uRadius;
  point /= float2(cos(uTilt.x), cos(uTilt.y));
  float radial = length(point);
  float alpha = cover(radial - 1.0);
  if (alpha <= 0.0) return half4(0.0);
  if (radial < HUB - aaWidth()) return half4(hubAt(point, radial), 1.0);
  float3 base = radial > FACE_OUTER ? ringBase(point, radial) : float3(0.0);
  float3 rgb;
  if (abs(uSpan) * uRadius * uPixelRatio < 0.333333) {
    rgb = movingAt(point, radial, uTheta, base);
  } else {
    rgb = float3(0.0);
    float jitter = fract(52.9829189 * fract(dot(floor(xy * uPixelRatio), float2(0.06711056, 0.00583715))));
    for (int i = 0; i < 8; ++i) {
      float tapAngle = uTheta - uSpan * (float(i) + jitter) / 8.0;
      rgb += movingAt(point, radial, tapAngle, base) / 8.0;
    }
  }
  if (radial < HUB + aaWidth()) rgb = mix(rgb, hubAt(point, radial), cover(radial - HUB));
  return half4(max(rgb, 0.0) * alpha, alpha);
}
"""
