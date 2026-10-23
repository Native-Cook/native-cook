package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

// The slab bends the lamp field inward at its rim and separates the color channels.
// The button is a thicker piece of the same glass, with its own contact shadow.
internal val referralAgsl =
    referralLightAgsl +
        """
// The button: its centre and half its size, then its radius, presence and press.
uniform float4 pill;
uniform float3 pillLook;
uniform float opaque;

// The field seen through glass that moves the view by shift along -n, the
// three colors bent by slightly different amounts. On the flat face the
// difference is too small to resolve, so one sample does.
float3 through(float2 p, float2 n, float shift) {
  if (shift < 0.125) return field(p - n * shift);
  return float3(
    field(p - n * shift * 1.2).r,
    field(p - n * shift).g,
    field(p - n * shift * 0.8).b);
}

// What thick glass does to the light that crosses it: slow ripples, three
// sine waves each bent by another, with thin bright veins where they cross
// zero, as on the floor of a pool. About -0.07 to 0.1; it turns once a loop.
float ripples(float2 p, float a) {
  float2 q = p / 30.0;
  float w = sin(1.3 * q.x + 1.6 * sin(0.9 * q.y + a))
    + sin(1.1 * q.y - 1.4 * sin(0.7 * q.x - a) + 2.0)
    + sin(0.8 * (q.x + q.y) + 1.2 * sin(0.6 * (q.x - q.y) + a) + 4.0);
  w /= 3.0;
  float veins = rampStep(0.6, 1.0, 1.0 - abs(w));
  return 0.07 * w + 0.08 * veins * veins;
}

// A circular bezel meets the flat face with a zero slope.
float bezel(float depth, float width) {
  float k = 1.0 - clamp(depth / width, 0.0, 1.0);
  return 1.0 - sqrt(max(1.0 - k * k, 0.0));
}

// Coverage integrates a one-device-pixel transition at the silhouette.
float coverage(float d) {
  return clamp(0.5 - d * scale, 0.0, 1.0);
}

// Integrating both edges keeps a subpixel glint from flickering at a corner.
float edge(float d, float width) {
  return (coverage(d) - coverage(d + width)) / max(coverage(d), 0.0001);
}

// The rim catches the upper-left light and a weaker reflection opposite it.
float catches(float2 n, float2 from) {
  float f = dot(n, from);
  return 0.10 + 0.64 * pow(max(f, 0.0), 2.0) + 0.26 * pow(max(-f, 0.0), 3.0);
}

float4 main(float2 xy) {
  float2 p = xy - origin;
  float2 mid = card * 0.5;
  float2 rel = p - mid;
  float d = box(rel, mid, radius);
  float cover = coverage(d);
  float show = clamp(pillLook.y, 0.0, 1.0);
  float press = clamp(pillLook.z, 0.0, 1.0);
  float small = max(min(pill.z, pill.w), 0.0);
  float pr = clamp(pillLook.x, 0.0, small);
  float2 prel = p - pill.xy;

  // Reduce Transparency has no lamps, grain or transition through glass.
  if (opaque > 0.5) {
    if (cover <= 0.0) return float4(0.0);
    float3 solid = float3(12.0, 12.0, 13.0) / 255.0;
    solid = mix(solid, float3(1.0), 0.12 * edge(d, 1.0));
    if (show > 0.0 && small > 0.0) {
      float pd = box(prel, pill.zw, pr);
      if (coverage(pd) > 0.0) {
        float3 button = mix(float3(28.0, 28.0, 30.0), float3(38.0, 38.0, 40.0), press) / 255.0;
        button = mix(button, float3(1.0), 0.10 * edge(pd, 1.0 / scale));
        solid = mix(solid, button, show * coverage(pd));
      }
    }
    return float4(solid * cover, cover);
  }

  // The page never evaluates the slab, and the opaque face never evaluates spill.
  float3 color = float3(0.0);
  if (cover < 1.0) color = spill(p, d) * (1.0 - cover);
  if (cover > 0.0) {
    float a = TAU * phase;
    float2 from = highlightFrom;
    float pd = show > 0.0 && small > 0.0 ? box(prel, pill.zw, pr) : 1.0 / scale;
    float pc = coverage(pd);
    float3 glass = float3(0.0);
    // A fully present button hides the slab, including its shadow.
    if (show * pc < 1.0) {
      float depth = max(-d, 0.0);
      float2 n = normal(rel, mid, radius);
      float bend = 46.0 * bezel(depth, 26.0);
      // No veil over it: where there is no light the glass is black, which
      // is what makes it read as clear and not frosted. The ripples are
      // seen through the same bend, so the rim visibly gathers them.
      glass = through(p, n, bend) * 0.94 * (1.0 + ripples(p - n * bend, a));
      // A narrow dark reflection separates the glint from the bent image.
      float band = (depth - 1.8) / 0.95;
      glass *= 1.0 - 0.22 * exp(-band * band);
      float shine = catches(n, from) * (1.0 + 0.25 * flare);
      float corner = 2.0 * abs(n.x * n.y);
      float line = (0.44 + 0.42 * max(dot(n, from), 0.0)) * (1.0 - 0.16 * corner);
      glass += shine * (edge(d, line) + 0.065 * exp(-depth / 3.8));
      // The broad reflection is at most 1.2%, leaving unlit glass dark.
      float2 reflection = (p - card * float2(0.18, 0.10)) / (card * float2(0.65, 0.38));
      glass += 0.012 * exp(-dot(reflection, reflection));

      if (show > 0.0 && small > 0.0) {
        float lift = 1.0 - press;
        float shadowReach = 3.0 + 6.0 * lift;
        float shadowOffset = 0.6 + 2.6 * lift;
        // A press brings the contact shadow back under the button.
        if (pd < shadowReach + shadowOffset) {
          float shadow = box(prel - float2(0.0, shadowOffset), pill.zw, pr);
          glass *= 1.0 - (0.18 + 0.10 * lift) * show * (1.0 - rampStep(-1.0, shadowReach, shadow));
        }
      }
    }
    if (show > 0.0 && pc > 0.0) {
      float pdepth = max(-pd, 0.0);
      // Finish the bevel before normals meet, even in a small rounded corner.
      float bw = max(min(11.0, min(pr * 0.8, small * 0.65)), 0.01);
      float2 pn = normal(prel, pill.zw, pr);
      float shift = 18.0 * (bw / 11.0) * (1.0 - 0.35 * press) * bezel(pdepth, bw);
      float3 knob = through(p, pn, shift) * (0.92 - 0.04 * press) * (1.0 + ripples(p - pn * shift, a)) + 0.085 - 0.02 * press;
      float pband = (pdepth - 1.5) / 0.8;
      // The dark band disappears as the dot forms, before it can become a ring.
      knob *= 1.0 - 0.22 * rampStep(6.0, 14.0, small) * exp(-pband * pband);
      // Fade directional reflections before the dot's medial axis as well.
      float fade = 1.0 - rampStep(bw * 0.55, bw, pdepth);
      float glint = catches(pn, from) * (1.0 - 0.22 * press);
      float pline = (0.42 + 0.38 * max(dot(pn, from), 0.0)) * (1.0 - 0.12 * 2.0 * abs(pn.x * pn.y));
      knob += glint * (0.82 * edge(pd, pline) + 0.09 * exp(-pdepth / 2.8) * fade);
      glass = mix(glass, knob, show * pc);
    }
    color += glass * cover;
  }

  // One stationary noise sample per device pixel. Part of it is added, which
  // hides banding in the dark; part multiplies, a fine tooth in the glass
  // that shows only where light comes through it.
  float peak = max(color.r, max(color.g, color.b));
  float tooth = hash(floor(xy * scale)) - 0.5;
  color = color * (1.0 + 0.07 * tooth * cover) + tooth * (3.0 / 255.0) * rampStep(0.0, 0.03, peak);
  color = clamp(color, 0.0, 1.0);
  return float4(color, max(cover, max(color.r, max(color.g, color.b))));
}

"""
