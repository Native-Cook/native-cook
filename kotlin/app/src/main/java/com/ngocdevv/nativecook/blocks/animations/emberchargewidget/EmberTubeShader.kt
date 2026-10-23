package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

// Line-for-line RN shader; only density conversion and template substitution added.
internal val EmberTubeShader = """
uniform float density;

uniform float width;
uniform float heat;
uniform float drift;
uniform float bend;
uniform float press;
uniform float fuse;
uniform float ring;
uniform float bloomOnly;
uniform float aimX;

float roundedBox(float2 p, float2 size, float radius) {
  float2 q = abs(p - size * 0.5) - size * 0.5 + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// The tube's share of the ember ramp (OKLCH hue): the fill runs 55 -> 80
// along its length and toward 90 when hot; its glass sits at 52 -> 56.
const float3 FILL_TAIL = float3(0.975, 0.51, 0.06);
const float3 FILL_HEAD = float3(0.985, 0.77, 0.38);
const float3 FILL_HOT = float3(1.0, 0.88, 0.55);
const float3 STRIPE = float3(1.0, 0.89, 0.61);
const float3 GLASS_TOP = float3(0.92, 0.45, 0.005);
const float3 GLASS_FLOOR = float3(1.0, 0.53, 0.005);
const float3 RIM = float3(1.0, 0.70, 0.28);

half4 main(float2 fragCoord) {
  // Compose pixels to the reference point/dp coordinates.
  float2 xy = fragCoord / density;
  float2 size = float2(${EmberTheme.E.bar.w}, ${EmberTheme.E.bar.h});
  // Compress around the tube's centre. Ignition breathes out, then rings
  // back into the measured resting outline, with no path allocation.
  float2 scale = float2(1.0 + 0.008 * press - 0.004 * ring,
                       1.0 - 0.055 * press + 0.065 * ring);
  float2 p = (xy - size * 0.5) / scale + size * 0.5;
  float r = mix(${EmberTheme.E.bar.r}, size.y * 0.5, fuse);
  float trackD = roundedBox(p, size, r);
  float trackA = 1.0 - smoothstep(-0.55, 0.55, trackD);
  float v = p.y / size.y;
  // The middle travels further than the shoulders, like liquid with mass.
  // Fade the bend at the far wall so a full fill and its vessel meet.
  float shoulder = max(0.0, 1.0 - pow((v - 0.5) * 2.0, 2.0));
  float nearHead = smoothstep(width - 44.0, width, p.x);
  float2 liquid = p - float2(bend * shoulder * nearHead, 0.0);
  float fillD = roundedBox(liquid, float2(width, size.y), min(r, width * 0.5));
  float fillA = (1.0 - smoothstep(-0.55, 0.55, fillD)) * trackA;
  float u = clamp(p.x / max(width, 1.0), 0.0, 1.0);
  float hot = smoothstep(0.45, 1.0, u);
  float3 color = mix(FILL_TAIL, FILL_HEAD, u);
  color = mix(color, FILL_HOT, heat * 0.20 + fuse * 0.35);

  // Narrow, softly refracted bands. The cylinder bends their projection a
  // little at its shoulders; 36 % contrast at the cool end softens to
  // 19 % at the hot end, where the reference washes the bands out.
  float stripeX = liquid.x + p.y * 0.38 + sin(v * 3.141593) * 1.4 - drift;
  float phase = fract(stripeX / ${EmberTheme.E.stripe.period});
  float band = smoothstep(0.02, 0.10, phase) * (1.0 - smoothstep(0.47, 0.55, phase));
  float depth = 0.72 + 0.28 * sin(clamp(v, 0.0, 1.0) * 3.141593);
  color = mix(color, STRIPE, band * depth * (0.36 - 0.17 * hot) * (1.0 - fuse * 0.8));
  // Keep the original tube lighting in height-relative coordinates.
  float crown = exp(-pow((v - 5.0 / 58.0) / (7.0 / 58.0), 2.0));
  float shade = smoothstep(34.0 / 58.0, 1.0, v);
  float topLight = exp(-pow((p.x - width * 0.65 - aimX * 4.0) / 44.0, 2.0));
  color += float3(0.0, 0.025, 0.045) * crown * (0.6 + 0.4 * topLight);
  color *= 1.0 - 0.045 * shade;

  // The hot two thirds cast light above and below the tube; the cool
  // first third keeps its stripe contrast. Blur this premultiplied source.
  float bloomA = fillA * mix(0.25, 1.0, smoothstep(0.22, 0.65, u));
  if (bloomOnly > 0.5) return half4(half3(color * bloomA), bloomA);

  // The empty chamber takes the same amber, with a soft caustic ahead of
  // the liquid and a little more light caught by the lower wall.
  float ahead = max(p.x - width, 0.0);
  float caustic = exp(-ahead / 33.0) * exp(-pow((v - 43.0 / 58.0) / (22.0 / 58.0), 2.0));
  float floorLight = pow(clamp(v, 0.0, 1.0), 1.25);
  float alpha = (0.13 + 0.46 * floorLight + 0.15 * caustic) * trackA;
  float3 glass = mix(GLASS_TOP, GLASS_FLOOR, floorLight);
  float edge = exp(-pow((trackD + 0.8) / 0.75, 2.0)) * trackA;
  float3 rgb = glass * alpha;
  float rimA = edge * (0.045 + 0.065 * floorLight);
  rgb = RIM * rimA + rgb * (1.0 - rimA);
  alpha = rimA + alpha * (1.0 - rimA);
  rgb = color * fillA + rgb * (1.0 - fillA);
  alpha = fillA + alpha * (1.0 - fillA);
  return half4(half3(rgb), alpha);
}

"""
