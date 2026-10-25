// The whole candle scene in one full-screen pass: a dark plaster wall and a walnut table lit by
// the flame, a pillar candle on a brass dish, its melted pool and wick, wax drips, the flame
// itself, its glow, and smoke when it goes out.
//
// The flame is a teardrop (an uneven capsule, y up from the wick) bent sideways by `bend` along
// the square of its height and stirred by looping noise (sampled on circles `turb`, so it repeats
// with the loop): a blue base, a white-yellow core, amber body, orange tip. The wax is a cylinder
// (normal from the horizontal position) lit from the front and from the flame above, with a
// subsurface glow under the pool. Each drip is a capsule with a bulb at its end, shaded as a
// small cylinder. Smoke is a curling column of fbm that rises at ~70 pt/s from the wick.
// Colours are sRGB, converted to Display P3 at the end.

export const CANDLE_SKSL = `
uniform float2 size;
uniform float2 base;
uniform float2 candle;
uniform float4 flame;
uniform float4 turb;
uniform float2 smoke;
uniform float4 drips[2];

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.1 + float2(7.3, 3.1);
    a *= 0.5;
  }
  return v;
}

float smin(float a, float b, float k) {
  float h = max(k - abs(a - b), 0.0) / k;
  return min(a, b) - h * h * k * 0.25;
}

float sdUnevenCapsule(float2 p, float r1, float r2, float h) {
  p.x = abs(p.x);
  float b = (r1 - r2) / h;
  float a = sqrt(1.0 - b * b);
  float k = dot(p, float2(-b, a));
  if (k < 0.0) return length(p) - r1;
  if (k > a * h) return length(p - float2(0.0, h)) - r2;
  return dot(p, float2(a, b)) - r1;
}

float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}

half4 main(float2 p) {
  float cx = base.x;
  float baseY = base.y;
  float cw = candle.x;
  float hw = cw * 0.5;
  float topY = baseY - candle.y;
  float ery = cw * 0.13;
  float lit = flame.x;
  float bend = flame.y;
  float flick = flame.z;
  // The flame's light: where it is, how bright (it breathes with the flicker).
  float2 lightAt = float2(cx + bend * 10.0, topY - 30.0);
  float glowAmt = lit * (1.0 + 0.08 * flick);

  // Wall above the table's back edge, table below it.
  float edge = baseY - 34.0;
  float3 col;
  float dl = length((p - lightAt) * float2(1.0, 0.9));
  if (p.y < edge) {
    col = mix(float3(0.075, 0.052, 0.04), float3(0.045, 0.032, 0.026), smoothstep(0.0, size.y * 0.6, abs(p.y - lightAt.y) + abs(p.x - cx) * 0.6));
    col += float3(1.0, 0.62, 0.3) * exp(-dl * dl / (2.0 * 170.0 * 170.0)) * 0.24 * glowAmt;
    col *= 1.0 + (fbm(p * 0.06) - 0.5) * 0.12;
  } else {
    float depth = (p.y - edge) / max(size.y - edge, 1.0);
    col = mix(float3(0.13, 0.08, 0.05), float3(0.05, 0.032, 0.022), smoothstep(0.0, 1.0, depth));
    // Walnut grain running across, and the flame's light pooling on the table.
    col *= 0.88 + 0.24 * noise(float2(p.x * 0.012, p.y * 0.35));
    float2 pool = (p - float2(lightAt.x, baseY + 6.0)) / float2(230.0, 70.0);
    col += float3(1.0, 0.6, 0.3) * exp(-dot(pool, pool)) * 0.2 * glowAmt;
    // The back edge catches a thin highlight.
    col += float3(1.0, 0.7, 0.45) * exp(-(p.y - edge) * (p.y - edge) / 2.0) * 0.12 * (0.4 + glowAmt);
    // Contact shadow around the dish.
    float2 cs = (p - float2(cx, baseY + 4.0)) / float2(hw + 46.0, 18.0);
    col *= 1.0 - 0.6 * exp(-dot(cs, cs) * 1.4);
  }
  col *= 1.0 + (hash(floor(p)) - 0.5) * 0.06;

  // Brass dish: an ellipse under the candle with a lit rim.
  float2 dq = (p - float2(cx, baseY)) / float2(hw + 30.0, 13.0);
  float dd = length(dq);
  if (dd < 1.0) {
    float3 brass = mix(float3(0.42, 0.29, 0.13), float3(0.78, 0.58, 0.3), smoothstep(0.2, -0.8, dq.y + dq.x * 0.3));
    brass *= 0.55 + 0.6 * glowAmt;
    brass += float3(1.0, 0.85, 0.6) * smoothstep(0.8, 0.97, dd) * smoothstep(0.3, -0.6, dq.y) * 0.35;
    col = mix(col, brass, smoothstep(1.0, 0.96, dd));
  }

  // Wax body: a cylinder from the top ellipse down to the dish.
  float u = (p.x - cx) / hw;
  if (abs(u) < 1.0) {
    float ey = ery * sqrt(1.0 - u * u);
    if (p.y > topY - ey && p.y < baseY + ey * 0.4) {
      float3 wax = float3(0.93, 0.87, 0.77);
      float nz = sqrt(1.0 - u * u);
      float shade = 0.42 + 0.5 * nz - 0.12 * u;
      float below = p.y - topY;
      float sss = exp(-below / 40.0) * glowAmt;
      float3 body = wax * shade * (0.55 + 0.45 * glowAmt);
      body += float3(1.0, 0.62, 0.28) * sss * 0.55 * nz;
      body += float3(1.0, 0.95, 0.88) * exp(-(u + 0.45) * (u + 0.45) / 0.012) * 0.1;
      body *= 1.0 + (fbm(float2(p.x * 0.08, p.y * 0.02)) - 0.5) * 0.08;
      // Darker toward the dish where the light falls off.
      body *= 1.0 - 0.35 * smoothstep(0.0, 1.0, below / max(candle.y, 1.0));
      float a = smoothstep(1.0, 0.985, abs(u)) * smoothstep(baseY + ey * 0.4, baseY + ey * 0.4 - 1.0, p.y);
      // The top: anything above the front of the top ellipse belongs to the pool, drawn next.
      if (p.y > topY + ey - 1.5) col = mix(col, body, a);
      // The pool: the top ellipse, a lighter rim and molten wax inside reflecting the flame.
      float2 tq = (p - float2(cx, topY)) / float2(hw, ery);
      float td = length(tq);
      if (td < 1.0) {
        float3 rim = float3(0.97, 0.92, 0.84) * (0.6 + 0.4 * glowAmt);
        float3 molten = mix(float3(0.95, 0.78, 0.5), float3(1.0, 0.86, 0.6), smoothstep(0.6, 0.0, td)) * (0.45 + 0.6 * glowAmt);
        molten += float3(1.0, 0.95, 0.8) * exp(-dot(tq - float2(bend * 0.08, -0.25), tq - float2(bend * 0.08, -0.25)) / 0.02) * 0.6 * glowAmt;
        float3 top = mix(molten, rim, smoothstep(0.72, 0.86, td));
        col = mix(col, top, smoothstep(1.0, 0.97, td));
      }
    }
  }

  // Drips: capsules running down the front from the rim, with a bulb at the end.
  for (int i = 0; i < 2; i++) {
    float4 d = drips[i];
    if (d.w > 0.01 && d.z > 0.5) {
      float x0 = cx + d.x;
      float y0 = topY + ery * 0.6;
      float y1 = topY + ery * 0.4 + d.z;
      if (y1 > y0) {
        // Wide where it leaves the rim, tapering, ending in a rounder bead.
        float k01 = clamp((p.y - y0) / max(y1 - y0, 1.0), 0.0, 1.0);
        float w = mix(5.5, 3.4, k01) + 4.0 * exp(-max(p.y - y0, 0.0) / 3.5);
        float dy = clamp(p.y, y0, y1);
        float stem = length(float2(p.x - x0, p.y - dy)) - w;
        float bead = length(p - float2(x0, y1 + 1.0)) - 4.6;
        float sd = smin(stem, bead, 5.0);
        // Its shadow on the wax, a little to the right.
        float shadow = smoothstep(4.0, -1.0, smin(length(float2(p.x - x0 - 2.5, p.y - clamp(p.y, y0, y1 + 2.0))) - w, bead - 0.5, 5.0));
        col *= 1.0 - 0.14 * shadow * d.w;
        if (sd < 1.0) {
          float nx = clamp((p.x - x0) / max(w, 1.0), -1.0, 1.0);
          float uu = (p.x - cx) / hw;
          float3 c2 = float3(0.95, 0.89, 0.79) * (0.52 + 0.45 * sqrt(max(1.0 - uu * uu, 0.0))) * (0.55 + 0.45 * glowAmt);
          c2 *= 0.9 + 0.18 * (1.0 - nx * nx) - 0.1 * nx;
          c2 += float3(1.0, 0.97, 0.9) * exp(-(nx + 0.45) * (nx + 0.45) / 0.03) * 0.16;
          col = mix(col, c2, smoothstep(1.0, -0.5, sd) * d.w);
        }
      }
    }
  }

  // Wick: a short dark curl leaning a little, its tip glowing while lit.
  float2 wq = p - float2(cx + bend * 1.5, topY - 4.0);
  if (abs(wq.x) < 3.0 && wq.y > -10.0 && wq.y < 1.0) {
    float wm = smoothstep(1.4, 0.6, abs(wq.x + wq.y * 0.12));
    col = mix(col, float3(0.08, 0.05, 0.04), wm);
    col += float3(1.0, 0.45, 0.15) * wm * smoothstep(-4.0, -9.0, wq.y) * flame.w * 0.9;
  }

  // Flame (y up from the wick tip).
  if (lit > 0.01) {
    float fy = (topY - 12.0) - p.y;
    float Hf = 60.0 * lit * (1.0 + 0.07 * flick);
    float Wf = 11.5 * sqrt(lit);
    float hn = clamp(fy / max(Hf, 1.0), 0.0, 1.2);
    float n1 = noise(float2(fy * 0.07 + turb.x * 1.5, turb.y * 1.5 + p.x * 0.02));
    float fx = p.x - cx - bend * 26.0 * hn * hn - (n1 - 0.5) * 5.0 * hn;
    float d = sdUnevenCapsule(float2(fx, fy + Wf * 0.2), Wf, 0.8, max(Hf - Wf, 1.0));
    float outer = smoothstep(3.0, -3.5, d);
    // Body: bright yellow low down, amber higher, orange at the tip; a white-hot core in the
    // middle; a dim blue cup at the base, where the flame is also thinner.
    float3 fc = mix(float3(1.0, 0.86, 0.5), float3(1.0, 0.62, 0.24), smoothstep(0.3, 0.75, hn));
    fc = mix(fc, float3(1.0, 0.44, 0.14), smoothstep(0.7, 1.0, hn));
    float core = smoothstep(-1.0, -Wf * 0.55, d) * smoothstep(0.08, 0.3, hn) * smoothstep(0.85, 0.4, hn);
    fc = mix(fc, float3(1.0, 0.97, 0.88), core);
    float cup = 1.0 - smoothstep(0.0, Wf * 1.5, fy + Wf * 0.4);
    fc = mix(fc, float3(0.3, 0.42, 1.0), cup * 0.75);
    float a = outer * smoothstep(1.15, 0.85, hn) * clamp(lit * 1.4, 0.0, 1.0) * (1.0 - cup * 0.45);
    col = mix(col, fc, a * 0.95);
    // Glow around the flame.
    float2 gq = (p - float2(cx + bend * 12.0, topY - 34.0)) / float2(1.0, 1.25);
    col += float3(1.0, 0.66, 0.3) * exp(-length(gq) / 36.0) * 0.32 * glowAmt;
  }

  // Smoke: a curling column above the wick, rising from the moment the flame went out.
  if (smoke.y > 0.01) {
    float sy = (topY - 8.0) - p.y;
    float reach = smoke.x * 75.0;
    if (sy > 0.0 && sy < reach + 30.0) {
      float sway = sin(sy * 0.045 - smoke.x * 2.2) * (4.0 + sy * 0.12) + (fbm(float2(sy * 0.02, smoke.x * 0.6)) - 0.5) * sy * 0.25;
      float w = 2.5 + sy * 0.09;
      float sx = p.x - cx - sway;
      float dens = exp(-sx * sx / (2.0 * w * w)) * smoothstep(reach + 30.0, reach - 20.0, sy);
      dens *= 0.55 + 0.6 * fbm(float2(p.x * 0.05, sy * 0.03 - smoke.x * 0.8));
      dens *= smoothstep(0.0, 6.0, sy) * smoothstep(320.0, 80.0, sy);
      col = mix(col, float3(0.62, 0.6, 0.58), clamp(dens * smoke.y * 0.55, 0.0, 1.0));
    }
  }

  // Vignette.
  float2 vq = p / size - float2(0.5, 0.42);
  col *= 1.0 - 0.95 * dot(vq * float2(0.95, 0.75), vq * float2(0.95, 0.75));

  col = toP3(clamp(col, 0.0, 1.0));
  col += (hash(p + 31.0) - 0.5) / 255.0;
  return half4(half3(col), 1.0);
}
`;
