package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

/**
 * The RN block's two SkSL effects (`gpm-shaders.ts`) as AGSL (API 33+), unchanged apart from the generated colour constants.
 *
 * PETALS: the page, five translucent discs whose circles all pass through the flower's centre O (uO, uS, uBlur widens every edge
 * analytically, so the frosted and softened copies cost no blur pass; uTwist turns the flower). GLASS: the whole screen, the page
 * (child `page`) with one volume of glass on it: a bead, a neck, a panel and the selection lozenge as one smooth-unioned distance
 * field with refraction, dispersion, frost (child `frost`, the page at 10 pt), the softened copy (child `soft`, 3.5 pt), tint, rim
 * light and the white-hot pour. Coordinates are dp (the canvas is scaled by the density); `uP3` stays 0.
 */
internal const val GlassPourMenuPetalsSource = """
uniform float2 uO;
uniform float uS;
uniform float uBlur;
uniform float uTwist;

float sat(float x) {
  return clamp(x, 0.0, 1.0);
}

// One disc: adds its soft coverage, and the darkness of its crisp outline and of its fold line.
// Its circle passes through O; the branch on the n side is the crisp petal edge all the way out,
// the other branch is a thin fold line near O that turns into a soft, wider edge further out.
float3 petal(float2 q, float r, float2 c, float softO, float softW, float blur) {
  float R = length(c);
  float2 d = c / R;
  float2 n = float2(d.y, -d.x);
  float s = R - length(q - c);
  float side = smoothstep(-24.0, 24.0, dot(q, n));
  float far = smoothstep(140.0, 330.0, r);
  float w = mix(mix(0.6, softW, far), 0.6, side);
  float o = mix(softO * far, 0.0, side);
  float we = sqrt(w * w + blur * blur);
  float cover = smoothstep(-we, we, s + o);
  float lw = 0.75;
  float lwe = sqrt(lw * lw + blur * blur);
  float ln = exp(-(s * s) / (lwe * lwe)) * (lw / lwe);
  float edge = ln * side;
  float fw = mix(0.85, 0.45, smoothstep(40.0, 190.0, r));
  float fwe = sqrt(fw * fw + blur * blur);
  float fl = exp(-(s * s) / (fwe * fwe)) * (fw / fwe);
  float fold = fl * (1.0 - side) * (1.0 - smoothstep(110.0, 200.0, r)) * smoothstep(22.0, 44.0, r);
  return float3(cover, edge, fold);
}

half3 ramp5(float r, half3 a, half3 b, half3 c, half3 d, half3 e, float ra, float rb, float rc, float rd, float re) {
  half3 col = mix(a, b, smoothstep(ra, rb, r));
  col = mix(col, c, smoothstep(rb, rc, r));
  col = mix(col, d, smoothstep(rc, rd, r));
  return mix(col, e, smoothstep(rd, re, r));
}

half3 flower(float2 q, float blur) {
  float r = length(q);
  float3 a = petal(q, r, float2(-316.70, -268.50), 2.0, 30.0, blur);
  float3 b = petal(q, r, float2(98.30, -360.40), 44.0, 17.0, blur);
  float3 c = petal(q, r, float2(352.20, 6.40), 20.0, 25.0, blur);
  float3 d = petal(q, r, float2(119.20, 402.20), 17.0, 19.0, blur);
  float3 e = petal(q, r, float2(-262.10, 210.20), 46.0, 19.0, blur);
  float cover = a.x + b.x + c.x + d.x + e.x;
  float edge = max(max(max(a.y, b.y), max(c.y, d.y)), e.y);
  float fold = max(max(max(a.z, b.z), max(c.z, d.z)), e.z);
  half3 one = ramp5(r, half3(0.9373, 0.4667, 0.7294), half3(0.9608, 0.6549, 0.8196), half3(0.9569, 0.5686, 0.6627), half3(0.9608, 0.6706, 0.6549), half3(0.9647, 0.7451, 0.7373), 44.0, 100.0, 180.0, 260.0, 380.0);
  half3 two = ramp5(r, half3(0.9373, 0.4667, 0.7294), half3(0.9647, 0.6431, 0.7922), half3(0.9569, 0.4039, 0.5373), half3(0.9490, 0.4824, 0.5059), half3(0.9608, 0.6706, 0.6549), 44.0, 95.0, 160.0, 215.0, 300.0);
  half3 page = mix(half3(0.9569, 0.8824, 0.8706), half3(0.9647, 0.8549, 0.8392), smoothstep(300.0, 360.0, r) * 0.0);
  half3 col = mix(page, one, sat(cover - 1.0));
  col = mix(col, two, sat(cover - 2.0));
  col = mix(col, half3(0.5569, 0.2275, 0.1882), edge * 0.92);
  col = mix(col, half3(0.7608, 0.1882, 0.4157), fold * 0.95);
  return col;
}

half4 main(float2 p) {
  float2 q = (p - uO) / uS;
  float cs = cos(uTwist), sn = sin(uTwist);
  q = float2(cs*q.x + sn*q.y, -sn*q.x + cs*q.y) / 1.253;
  half3 col = flower(q, uBlur / 1.253);
  // ±0.5/255 ordered dither: no banding in the long pink gradients
  float g = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
  return half4(col + g / 255.0, 1.0);
}
"""

internal const val GlassPourMenuGlassSource = """
uniform shader page;
uniform shader frost;
uniform shader soft;
uniform float2 uO;
uniform float uS;
uniform float3 uBead;
uniform float4 uBox;
uniform float uBoxR;
uniform float uK;
uniform float uThick;
uniform float uHot;
uniform float uFrost;
uniform float uOpen;
uniform float uPress;
uniform float4 uLens;
uniform float uSpec;
uniform float uRim;
uniform float uP3;
uniform float3 uNeck;
uniform float3 uSatellite;
uniform float uDwell;
uniform float uDir;
uniform float4 uOptics;
uniform float3 uTint;

float sat(float x) {
  return clamp(x, 0.0, 1.0);
}

float sdBox(float2 q, float2 b, float r) {
  float2 d = abs(q) - b + float2(r, r);
  return length(max(d, float2(0.0, 0.0))) + min(max(d.x, d.y), 0.0) - r;
}

// Polynomial smooth minimum: k is the width of the neck's fillet; k = 0 is a plain union (pinched off).
float smin(float a, float b, float k) {
  if (k < 0.01) {
    return min(a, b);
  }
  float h = max(k - abs(a - b), 0.0) / k;
  return min(a, b) - h * h * k * 0.25;
}

// The glass: bead ∪ panel (smooth), ∪ the lens bar while it shows.
float body(float2 q, float3 bead, float4 box, float boxR, float k) {
  float a = length(q - bead.xy) - bead.z;
  float b = sdBox(q - box.xy, box.zw, boxR);
  float g = smin(a, b, k);
  if (uNeck.z > 0.001) {
    float yy = clamp(q.y, min(uNeck.x, uNeck.y), max(uNeck.x, uNeck.y));
    float neck = length(q - float2(0.0, yy)) - uNeck.z;
    g = smin(smin(a, neck, 16.0), b, 16.0);
  }
  if (uSatellite.z > 0.001) g = smin(g, length(q - uSatellite.xy) - uSatellite.z, 12.0);
  return g;
}

// The selection lozenge sits inside the panel (7 pt from its sides), a full pill, 34 pt tall at rest.
float bar(float2 q, float4 box, float4 lens) {
  float hw = box.z - mix(16.0, 7.0, lens.z);
  float hh = lens.y * mix(0.6, 0.94, lens.z);
  return sdBox(q - float2(box.x, lens.x), float2(max(hw, 1.0), hh), hh);
}

float glass(float2 q, float3 bead, float4 box, float boxR, float k, float4 lens) {
  float g = body(q, bead, box, boxR, k);
  if (lens.z > 0.001) {
    g = smin(g, bar(q, box, lens), 3.0);
  }
  return g;
}

half3 toP3(half3 c) {
  half3 lin = pow(c, half3(2.2, 2.2, 2.2));
  half3 p = half3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  return pow(max(p, half3(0.0, 0.0, 0.0)), half3(0.4545, 0.4545, 0.4545));
}

half4 finish(half3 c, float p3) {
  half3 o = clamp(c, half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0));
  if (p3 > 0.5) {
    o = toP3(o);
  }
  return half4(o, 1.0);
}

half4 main(float2 p) {
  float2 q = (p - uO) / uS;
  q.y *= uDir;
  half3 bg = page.eval(p).rgb;
  float d = glass(q, uBead, uBox, uBoxR, uK, uLens);
  if (d > 70.0) {
    return finish(bg, uP3);
  }
  float e = 0.6;
  float dx = glass(q + float2(e, 0.0), uBead, uBox, uBoxR, uK, uLens) - glass(q - float2(e, 0.0), uBead, uBox, uBoxR, uK, uLens);
  float dy = glass(q + float2(0.0, e), uBead, uBox, uBoxR, uK, uLens) - glass(q - float2(0.0, e), uBead, uBox, uBoxR, uK, uLens);
  float2 n = float2(dx, dy) / max(length(float2(dx, dy)), 0.0001);
  float aa = 0.75 / uS;
  // A thin part (the neck, a fillet) is a cylinder lens: its slope, and so its bend, is 0 on its centre line. The
  // distance field's gradient shrinks there over a 3.5 pt probe; without this the bend flips sign across the axis
  // and prints a seam down the middle of the neck.
  float axis = 1.0;
  if (d < 0.0) {
    float e2 = 3.5;
    float ax = glass(q + float2(e2, 0.0), uBead, uBox, uBoxR, uK, uLens) - glass(q - float2(e2, 0.0), uBead, uBox, uBoxR, uK, uLens);
    float ay = glass(q + float2(0.0, e2), uBead, uBox, uBoxR, uK, uLens) - glass(q - float2(0.0, e2), uBead, uBox, uBoxR, uK, uLens);
    axis = smoothstep(0.12, 0.8, length(float2(ax, ay)) / (2.0 * e2));
  }

  // ---- outside: the page with the glass's shade, its magenta halo and the hot bloom ----
  // Projected page light: ref.color.projectedCaustic (#FFE3F6).
  float2 cq = (q - float2(uBead.x, uBead.y + (uBead.z + 12.0) * uDir)) / float2(23.0, 10.0);
  float caustic = exp(-pow(max(length(cq) - 0.45, 0.0), 2.0) * 1.8) * 0.5 * uThick * uThick;
  half3 outside = mix(bg, half3(1.0, 0.8902, 0.9647), caustic);
  if (d > -aa) {
    float ds = glass(q - float2(0.0, 8.0 * uDir), uBead, uBox, uBoxR, uK, uLens);
    float amb = exp(-max(ds, 0.0) / 12.0) * 0.12;
    float contact = exp(-max(d, 0.0) / 2.5) * mix(0.1, 0.3, uThick) * smoothstep(-0.2, 0.6, n.y * uDir);
    outside = mix(outside, outside * half3(0.4784, 0.1647, 0.3608), amb + contact);
    float halo = (0.62 * exp(-max(d, 0.0) / 8.0) + 0.22 * exp(-max(d, 0.0) / 38.0)) * uThick * uThick;
    outside = mix(outside, half3(0.7608, 0.3608, 0.6392), halo);
    float neckLight = exp(-abs(q.x) / 12.0) * (1.0 - smoothstep(0.0, 20.0, max(uNeck.x - q.y, q.y - uNeck.y)));
    float bloom = exp(-max(d, 0.0) / 8.0) * 0.38 * uHot * max(neckLight, exp(-abs(d) / 2.0));
    outside = mix(outside, half3(1.0000, 0.9529, 0.9804), bloom);
  }

  // ---- inside: refracted, dispersed, frosted, tinted, lit ----
  float depth = max(-d, 0.0);
  float x = sat(depth / uRim);
  float bend = (1.0 - x) * (1.0 - x);
  float refr = uRim * mix(0.45, 0.75, uThick) * (1.0 + 0.4 * uHot);
  // One continuous height field; no nearest-part centre, hence no optical seam at the junction.
  float mag = 1.0 + 0.45 * max(uThick, uHot);
  float dome = max(uRim - depth, 0.0) * (1.0 - 1.0 / mag) * smoothstep(0.0, 0.6, x) * (1.0 - uFrost);
  float2 off = n * (bend * refr + dome) * uOptics.x * axis;
  // dispersion only where the glass is steep (the outer rim): a split wider than a 0.75 pt petal line reads as a
  // rainbow wire, not as glass
  float2 dn = n * (1.1 * bend * bend * bend + 0.35 * uHot * bend * bend) * uOptics.y * axis;
  off.y *= uDir;
  dn.y *= uDir;
  half3 sharp = half3(page.eval(p - (off + dn) * uS).r, page.eval(p - off * uS).g, page.eval(p - (off - dn) * uS).b);
  // Where the gel bends hard (its rim, the neck, the fillets) it is a thick, curved body: the petal lines seen through it
  // come out of the softened copy, so they bend as shading instead of folding into crisp seams and rainbow wires.
  float haze = sat(1.6 * bend + 1.3 * (1.0 - axis));
  half3 softer = half3(soft.eval(p - (off + dn) * uS).r, soft.eval(p - off * uS).g, soft.eval(p - (off - dn) * uS).b);
  sharp = mix(sharp, softer, half(haze));
  half3 fro = frost.eval(p - off * uS).rgb;
  half3 inner = mix(sharp, fro, uFrost * uOptics.z);

  // the clear lens bar over the row under the finger: no frost, 1.08 × magnified, its own rim
  float dl = 100.0;
  if (uLens.z > 0.001) {
    dl = bar(q, uBox, uLens);
  }
  float lensIn = smoothstep(aa, -aa, dl) * uLens.z;
  if (lensIn > 0.001) {
    float edl = 0.6;
    float lx = bar(q + float2(edl, 0.0), uBox, uLens) - bar(q - float2(edl, 0.0), uBox, uLens);
    float ly = bar(q + float2(0.0, edl), uBox, uLens) - bar(q - float2(0.0, edl), uBox, uLens);
    float2 nl = float2(lx, ly) / max(length(float2(lx, ly)), 0.0001);
    float xl = sat(-dl / 9.0);
    float bl = (1.0 - xl) * (1.0 - xl);
    float2 lc = float2(uBox.x, uLens.x);
    float2 lp = lc + (q - lc) / mix(1.08, 1.16, uDwell) - nl * bl * 7.0;
    lp.y *= uDir;
    nl.y *= uDir;
    float ld = 0.9 * bl * bl;
    // clearer than the frosted panel, never sharp: the page through 3.5 pt of softening, so the petals' 0.75 pt
    // outlines read as refracted shading, not as dark wires through the highlight
    half3 clear = half3(soft.eval(uO + (lp - nl * ld) * uS).r, soft.eval(uO + lp * uS).g, soft.eval(uO + (lp + nl * ld) * uS).b);
    clear = mix(clear, half3(1.0000, 0.6510, 0.8235), 0.22) + half3(0.045, 0.04, 0.045);
    // a thin bright rim, brightest along the top (the key light), and a soft caustic gathered along the bottom
    float lrim = exp(-max(-dl, 0.0) / 0.9) * (0.28 + 0.34 * sat(-nl.y));
    float lcaus = exp(-max(-dl, 0.0) / 5.0) * sat(nl.y) * 0.32;
    clear = mix(clear, half3(1.0000, 0.7216, 0.9647), lcaus);
    clear = mix(clear, half3(1.0000, 1.0000, 1.0000), lrim + 0.35 * uLens.w);
    inner = mix(inner, clear, lensIn);
  }

  // tint: magenta drop → pink panel (lighter toward its bottom), thinner when hot
  float yIn = sat((q.y - (uBox.y - uBox.w)) / max(2.0 * uBox.w, 1.0));
  half3 panelC = mix(half3(1.0000, 0.6510, 0.8235), half3(1.0000, 0.7529, 0.9059), smoothstep(0.35, 1.0, yIn));
  half3 tint = mix(uTint, panelC, uOpen);
  float tintAmt = mix(mix(0.48, 0.46, uOpen), 0.12, uHot) * (1.0 - lensIn);
  half3 col = mix(inner, tint, tintAmt);
  float vy = sat((q.y - (uBead.y - uBead.z)) / max(2.0 * uBead.z, 1.0));
  col = mix(col, col * half3(0.93, 0.9, 0.95), (1.0 - vy) * 0.5 * uThick * (1.0 - uHot));

  // light from above: darker upper inner rim, a bright caustic along the lower rim, a 1 pt Fresnel rim
  float up = sat(-n.y * uDir);
  float down = sat(n.y * uDir);
  col = mix(col, half3(0.7255, 0.3412, 0.6157), bend * up * mix(0.38, 0.12, uOpen) * (1.0 - uHot));
  float caus = exp(-depth / mix(9.0, 5.0, uOpen)) * pow(down, 1.2) * mix(0.95, 0.7, uOpen);
  col = mix(col, half3(1.0000, 0.7216, 0.9647), caus * (1.0 - lensIn));
  float ang = atan(n.y, n.x) + uSpec;
  // the thick drop's rim is lit from below (its top edge stays dark); the thin open sheet catches light all round
  float fres = exp(-depth / 1.1) * mix(0.1 + 0.42 * down, 0.36 + 0.22 * sat(-sin(ang)) + 0.3 * down, uOpen);
  col = mix(col, half3(1.0000, 1.0000, 1.0000), fres);

  // white-hot: thin, fast glass gathers light; it stays clear enough to see the magnified petals
  float sweep = exp(-pow((q.y - mix(uNeck.x, uNeck.y, fract(uSpec))) / 7.0, 2.0)) * exp(-q.x*q.x / 36.0);
  col = mix(col, half3(1.0000, 0.9529, 0.9804), uHot * uOptics.w * (0.16 * bend + 0.32 * sweep));
  col = col * (1.0 - 0.08 * uPress * (1.0 - uHot));

  float cover = smoothstep(aa, -aa, d);
  return finish(mix(outside, col, cover), uP3);
}
"""

internal const val GLASS_POUR_MENU_FROST_BLUR = 10.0
internal const val GLASS_POUR_MENU_SOFT_BLUR = 3.5
