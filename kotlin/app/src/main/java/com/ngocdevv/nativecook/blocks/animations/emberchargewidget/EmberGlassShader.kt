package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

// Line-for-line RN shader; only density conversion and template substitution added.
internal val EmberGlassShader = """
uniform float density;

uniform float2 size;
uniform float bleed;
uniform float radius;
uniform float time;
uniform float rise;
uniform float heat;
uniform float slosh;
uniform float ripple;
uniform float2 origin;
uniform float flash;
// Light lean in radians; frozen under Reduce Motion. Geometry never sees it.
uniform float lightLean;
// Linear 0…1 over the first 500 ms of ignition; 0 disables the sweep.
uniform float sweep;
uniform float2 aim;
uniform float touchLight;
uniform float touchRadius;
// J. Shares the card's entrance; Reduce Motion holds it at 1.
uniform float materialise;
// 2 high, 1 medium, 0 low; all three use the same compiled shader.
uniform float quality;

const float KEY_ANGLE = -2.356194;
// The ember ramp. One hue family whose hue climbs with lightness, as a
// heating body's does: coal 53 deg, hot coal 60, lip 62, amber 70, white-hot 92
// (OKLCH). The glass's own edge light is the one cool colour.
const float3 COAL = float3(0.76, 0.378, 0.018);
const float3 COAL_HOT = float3(0.928, 0.522, 0.02);
const float3 LIP = float3(0.946, 0.555, 0.079);
const float3 AMBER = float3(1.0, 0.68, 0.26);
const float3 WHITE_HOT = float3(1.0, 0.95, 0.80);
const float3 GLASS_COOL = float3(0.82, 0.88, 1.0);
const float UNDER_COPY = 0.94;

float countLight(float2 p) {
  float2 delta = (p - float2(${EmberTheme.countFootprint.x}, ${EmberTheme.countFootprint.y}))
               / float2(${EmberTheme.countFootprint.rx}, ${EmberTheme.countFootprint.ry});
  float radius2 = dot(delta, delta);
  return 1.0 - exp(-radius2 * radius2);
}


float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

// A continuous corner: the box's corners follow |x|^3 + |y|^3 = r^3 rather
// than a circle, so the curve eases out of the straight edge the way a
// system squircle does. r is how far along the edge the corner starts.
float sdSquircle(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  float2 m = max(q, 0.0);
  return pow(m.x * m.x * m.x + m.y * m.y * m.y, 1.0 / 3.0) + min(max(q.x, q.y), 0.0) - r;
}

// One inner shadow: 1 where the card is not covered by its own outline moved
// by off and grown by -spread, blurred across the edge.
float innerShadow(float2 p, float2 c, float2 off, float blur, float spread) {
  float d = sdSquircle(p - c - off, c, radius) + spread;
  return smoothstep(-0.5 * blur, 0.5 * blur, d);
}

// A narrow reflection across a contour. Width is its 1/e half-width in pt.
float contour(float d, float inset, float width) {
  return exp(-pow((d + inset) / width, 2.0));
}

float smoother(float x) {
  x = clamp(x, 0.0, 1.0);
  return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
}

float bevelLight(float d, float2 n, float3 h) {
  float bend = 1.0 - smoother(max(-d, 0.0) / (7.0 / 0.55));
  float3 normal = normalize(float3(n * (1.1 * bend * materialise), 1.0));
  return (pow(max(dot(normal, h), 0.0), 32.0)
        + 0.6 * pow(max(dot(normal, float3(-h.xy, h.z)), 0.0), 32.0)) * bend * materialise;
}

half4 main(float2 fragCoord) {
  // Compose pixels to the reference point/dp coordinates.
  float2 xy = fragCoord / density;
  float2 p = xy - bleed;
  float2 c = size * 0.5;
  float d = sdSquircle(p - c, c, radius);
  if (d >= 42.0) return half4(0.0);
  float inside = 1.0 - smoothstep(-0.75, 0.75, d);
  float dither = (hash(xy) - 0.5) / 255.0;

  // A. A 7 pt rise spread over 7 / 0.55 pt. Integrating this slope
  // gives the bevel's height; both slope and curvature meet the face at zero.
  float2 n = float2(0.0);
  float3 bevelNormal = float3(0.0, 0.0, 1.0);
  float bend = 0.0;
  if (quality > 0.5 && d > -20.0 && d < 0.75) {
    float2 g = float2(
      sdSquircle(p - c + float2(0.5, 0.0), c, radius) - sdSquircle(p - c - float2(0.5, 0.0), c, radius),
      sdSquircle(p - c + float2(0.0, 0.5), c, radius) - sdSquircle(p - c - float2(0.0, 0.5), c, radius));
    n = g / max(length(g), 0.001);
    bend = 1.0 - smoother(max(-d, 0.0) / (7.0 / 0.55));
    bevelNormal = normalize(float3(n * (1.1 * bend * materialise), 1.0));
  }

  // The liquid part: a slow swell and a sideways lean.
  float swell = 3.0 * sin(p.x * 0.021 + time * 0.9)
              + 2.0 * sin(p.x * 0.043 - time * 1.3 + 1.3)
              + 1.1 * sin(p.x * 0.087 + time * 2.1 + 0.4);
  // B. The wall bends the ember's sampling point, never the outline.
  // Thickness falls to zero at the face, so the four shadows join cleanly.
  float2 lens = bevelNormal.xy * (6.0 * bend * materialise);
  float2 through = p + lens;
  float2 q = through + float2(0.0, swell * (1.0 + 1.4 * heat));
  float lean = slosh * 260.0 + aim.x * 10.0;

  float s1 = innerShadow(q, c, float2(lean, -124.0 * rise), 93.0, -46.0);
  float s2 = innerShadow(q, c, float2(lean * 0.7, -62.0 * rise), 46.0, -12.0);
  // A fifth, not in the recipe: the same light hugging the whole wall, so it
  // climbs the sides the way the reference's does. Stronger toward the floor.
  float wall = smoothstep(-66.0, 4.0, d + dot(lens, n)) * mix(0.5, 1.0, smoothstep(0.1, 0.85, through.y / size.y));

  float lit = clamp(s1 + 0.6 * s2, 0.0, 1.0);
  float3 amber = AMBER;
  float3 white = WHITE_HOT;
  float3 deep = mix(COAL, COAL_HOT, heat);

  // 0. Outer bloom: a tight 4 pt spill plus a faint 11 pt skirt. Both
  // vanish at 42 pt, leaving 18 pt of clear canvas before the bleed ends.
  float halo = (0.20 * exp(-max(d, 0.0) / 4.0)
              + 0.055 * exp(-max(d, 0.0) / 11.0)) * lit;
  halo *= (1.0 + 1.8 * heat + 2.0 * flash) * (1.0 - smoothstep(24.0, 42.0, d));
  float outside = (1.0 - inside) * clamp(halo, 0.0, 1.0);

  if (d >= 0.75) return half4(half3(mix(deep, amber, 0.5) * outside), outside);
  float s3 = innerShadow(q, c, float2(lean * 0.4, -31.0 * rise), 31.0, -9.0);
  float s4 = innerShadow(through, c, float2(0.0, 9.0), 9.0, -3.0);
  float extraLight = countLight(p);
  // Heat's amber lift is extra light too. Keep the saturated coal beneath
  // it, rather than darkening the finished colour after everything is lit.
  // Under the count the coal is a shade deeper, so 12 pt copy clears 4.5:1.
  float3 coal = mix(COAL * UNDER_COPY, deep, extraLight);

  // 1. Body: a shade above the page so the slab reads before the light does.
  float v = p.y / size.y;
  float3 col = mix(float3(0.014, 0.012, 0.010), float3(0.009, 0.008, 0.006), smoothstep(0.0, 0.7, v));
  // 2. Ember light: the four original inner shadows and the wall wash.
  col = mix(col, coal, s1);
  col = mix(col, coal, 0.44 * wall * (1.0 - s1));
  // Heat lifts the light and warms it toward amber; it does not whiten it, or the
  // count would have nothing to stand on. White is the flash's.
  col += amber * 0.5 * s2 * extraLight;
  col += white * (0.3 - 0.08 * heat) * s3 * extraLight;
  col = mix(col, LIP, (0.25 + 0.2 * heat) * s4);
  // Plus lighter overflows red long before green, and clipping that turns
  // ember into lemon. Scale back to the brightest channel instead, and let
  // only a sliver of the overflow go to white.
  float peak = max(col.r, max(col.g, col.b));
  if (peak > 1.0) col = col / peak + 0.15 * (peak - 1.0);

  // Ignition: a ring of light crossing the glass, and the flash.
  float r = length(p - origin);
  float R = 340.0 * (1.0 - pow(1.0 - ripple, 3.0));
  float ring = exp(-pow((r - R) / 22.0, 2.0)) * (1.0 - ripple) * step(0.0001, ripple);
  col += amber * 0.55 * ring * extraLight;
  // A flash belongs to the coals and the wall, not the dark face above.
  float flashLight = max(lit * lit, wall * 0.65);
  col = mix(col, white, 0.52 * flash * flashLight * extraLight);

  // The border survives on every tier; only the lit lip needs a normal.
  float line = contour(d, 0.65, 0.5);
  float3 glassLight = mix(GLASS_COOL, white, lit);
  col = mix(col, glassLight, line * (0.045 + 0.10 * lit * (1.0 + heat)));
  if (quality > 0.5) {
    // C. Aim moves the light's direction, never its strength. Medium keeps
    // the resting top-left key; the opposite light is quieter.
    float angle = KEY_ANGLE + lightLean;
    float2 key = float2(cos(angle), sin(angle));
    if (quality > 1.5) {
      float2 toward = key + aim * 0.6;
      key = normalize(toward);
    }
    if (d > -20.0) {
      float2 fill = -key;
      float keyLight = pow(max(dot(n, key), 0.0), 2.0);
      float fillLight = pow(max(dot(n, fill), 0.0), 2.0);
      float lighting = keyLight + 0.6 * fillLight;
      // D. The light rolls across a curved lip. No second contour: the
      // half-vector catches a different slope as the light moves around it.
      float3 keyHalf = normalize(normalize(float3(key, 0.65)) + float3(0.0, 0.0, 1.0));
      float bevel = bevelLight(d, n, keyHalf);
      col = mix(col, glassLight, bevel * (0.14 + 0.08 * flash));
      col = mix(col, glassLight, line * lighting * (0.34 + 0.16 * flash));

      if (quality > 1.5) {
        // One clockwise lap in 500 ms; a periodic angle crosses the seam.
        float lap = 1.0 - pow(1.0 - sweep, 4.0);
        float theta = atan(p.y - c.y, p.x - c.x);
        float sweepAngle = KEY_ANGLE + 6.283185 * lap;
        float arc = atan(sin(theta - sweepAngle), cos(theta - sweepAngle));
        float sweepLight = exp(-pow(arc / 0.22, 2.0)) * (1.0 - sweep)
                         * step(0.0001, sweep);
        col += white * (line + 0.18 * bevel) * sweepLight * 0.9;
      }

      // E. Position along the rim, rather than the normal: even a straight
      // edge has one glint that travels, not a whole edge that flashes on.
      float2 radial = normalize(p - c);
      float keyArc = atan(radial.y * key.x - radial.x * key.y, dot(radial, key));
      float fillArc = atan(radial.y * fill.x - radial.x * fill.y, dot(radial, fill));
      float glints = exp(-pow(keyArc / 0.040, 2.0))
                   + 0.6 * exp(-pow(fillArc / 0.050, 2.0));
      col += glassLight * glints * (0.38 * contour(d, 0.7, 0.55)
           + 0.045 * contour(d, 0.7, 1.8));

      // F. Three nearby readings of the same lip, not three new outlines.
      // Only a small share splits; warm sits outside, cool sits inside.
      if (quality > 1.5) {
        float outer = contour(d - 0.45, 0.65, 0.5) * lighting
                    + 0.4 * bevelLight(d - 0.45, n, keyHalf);
        float middle = line * lighting + 0.4 * bevel;
        float inner = contour(d + 0.45, 0.65, 0.5) * lighting
                    + 0.4 * bevelLight(d + 0.45, n, keyHalf);
        col += float3(outer, middle, inner) * 0.045;
      }
    }
    // G. Fold the four corners onto one soft pool. Each takes light from
    // the lamp across the slab; the ember decides how warm that light is.
    if (quality > 1.5) {
      float2 corner = abs(p - c) - (c - 38.0);
      float2 across = normalize(sign(p - c) + float2(0.0001));
      float gathered = pow(max(dot(across, -key), 0.0), 2.0)
                     + 0.6 * pow(max(dot(across, key), 0.0), 2.0);
      float pool = exp(-dot(corner, corner) / (26.0 * 26.0));
      col += glassLight * pool * gathered * 0.05 * smoothstep(1.0, 12.0, -d) * extraLight;
    }
  }

  // H. The upper face reflects one diagonal light box. Its edge is soft,
  // not a painted stripe, and the reflection stops before the lower copy.
  if (quality > 0.5) {
    float shift = quality > 1.5 ? dot(aim, float2(4.0, -3.0)) : 0.0;
    float plane = dot(p, float2(0.481919, 0.876216)) - 160.0 - shift;
    float gloss = 1.0 - smoothstep(-11.0, 11.0, plane);
    col += GLASS_COOL * gloss * 0.028 * smoothstep(0.0, 12.0, -d) * extraLight;
    // The frost belongs to the glass, so its grain never swims.
    col += (hash(floor(xy * 2.0)) - 0.5) * 0.014;
  }
  // I. Light starts under the finger and reaches the nearest wall. The
  // state supplies pressure and aim; Reduce Motion fixes the centre/radius.
  if (quality > 0.5 && touchLight > 0.0) {
    float2 finger = c + clamp(aim, -1.0, 1.0) * c;
    float2 delta = p - finger;
    float touch = exp(-dot(delta, delta) / (touchRadius * touchRadius)) * touchLight;
    col += glassLight * touch * (0.045 * extraLight + 0.16 * line + 0.035 * bend);
  }
  col = clamp(col + dither, 0.0, 1.0);

  float3 rgb = col * inside + mix(deep, amber, 0.5) * outside;
  return half4(half3(rgb), inside + outside);
}

"""
