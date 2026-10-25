/*
 Slot Reel List — the three reels in one pass over the window.

 Each reel is a cylinder of radius `radius` seen side-on: a pixel `yc` points from the window's
 centre looks at the angle θ = asin(yc / radius), and the printed strip is read at row
 `offset + θ / rowAngle`, so rows near the window's edges are squeezed exactly as on a drum.
 Light falls off with cos θ (the drum turning away), a soft specular band rides above the payline,
 and a spinning reel is blurred along its travel by averaging eight jittered reads spread over the distance
 it covers in ~1/30 s. On the payline row the win glow warms the print and a slanted band of light
 sweeps across all three reels.

 The strip arrives through `strip` (an image shader at `px` pixels per point; its colours are
 already in the screen's space). Colours written here are sRGB and converted to Display P3 at the
 end, because Skia treats a runtime effect's output as P3 on iOS.
*/
export const SLOT_REEL_SKSL = `
uniform shader strip;
uniform float2 origin;
uniform float2 win;
uniform float colW;
uniform float gap;
uniform float rowH;
uniform float radius;
uniform float rows;
uniform float px;
uniform float3 off;
uniform float3 blur;
uniform float sweep;
uniform float glow;

float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}

float3 read(float c, float lx, float r) {
  float v = r + 0.5;
  v = v - rows * floor(v / rows);
  return strip.eval(float2((c * colW + lx) * px, v * rowH * px)).rgb;
}

half4 main(float2 p) {
  float2 q = p - origin;
  float pitch = colW + gap;
  float c = floor(q.x / pitch);
  float lx = q.x - c * pitch;
  float yc = q.y - win.y * 0.5;
  float s = clamp(yc / radius, -0.999, 0.999);
  float th = asin(s);
  float ct = cos(th);

  if (c < 0.0 || c > 2.0 || lx > colW) {
    // The dark steel between the drums, lit a little across its middle.
    float3 steel = mix(float3(0.07, 0.04, 0.035), float3(0.2, 0.13, 0.11), pow(ct, 3.0));
    return half4(half3(toP3(steel)), 1.0);
  }

  float o = c < 0.5 ? off.x : (c < 1.5 ? off.y : off.z);
  float b = c < 0.5 ? blur.x : (c < 1.5 ? blur.y : blur.z);
  float r = o + th * radius / rowH;
  // Eight reads spread over the blur, each pixel's set shifted by a hash so the copies melt into
  // a smooth streak instead of stepping.
  float3 col = float3(0.0);
  float j = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
  for (int i = 0; i < 8; i++) {
    col += read(c, lx, r + b * ((float(i) + j) / 8.0 - 0.5));
  }
  col *= 0.125;

  // The drum's light: falling off toward its top and bottom, a soft band of gloss just above the
  // payline, and the sides of each drum a shade darker (it is round across, too).
  float across = (lx / colW) * 2.0 - 1.0;
  float shade = (0.16 + 0.84 * pow(ct, 1.6)) * (1.0 - 0.18 * across * across);
  float spec = exp(-pow((th + 0.42) / 0.2, 2.0)) * 0.16;
  col = col * shade + toP3(float3(1.0, 0.96, 0.9)) * spec * ct;

  // The win: the payline row warms up and a slanted band of light sweeps across.
  float rowA = rowH / radius;
  float line = 1.0 - smoothstep(rowA * 0.42, rowA * 0.56, abs(th));
  col = mix(col, col * 1.06 + toP3(float3(0.32, 0.2, 0.04)) * 0.32, glow * line);
  if (sweep > -0.5) {
    float bx = mix(-80.0, win.x + 80.0, sweep);
    float band = exp(-pow((q.x - bx + yc * 0.5) / 26.0, 2.0));
    col += toP3(float3(1.0, 0.93, 0.78)) * band * line * 0.55;
  }
  return half4(half3(col), 1.0);
}
`;
