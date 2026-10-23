import { Skia } from '@shopify/react-native-skia';

// A cylinder page curl, drawn per pixel. The grabbed point `grab` of the sheet lies at `tip` on screen. The fold
// is a cylinder whose axis runs perpendicular to grab→tip; paper past the axis wraps around it and, past half a
// turn, lies flat on top, back side up. For every screen pixel the shader walks back to the paper point that
// lands there (top layer first): the flat back, then the back and front of the roll, then the page itself,
// and where no paper of this sheet is left, the page beneath with the roll's cast shadow.
// The roll's radius grows along the fold (r0 + r1·t), so the sheet curls like a cone, as iOS's does.
// p0…p3 are the four pages (snapshots of their views); frontIdx is the turning sheet, underIdx the one below.
const SOURCE = `
uniform shader p0;
uniform shader p1;
uniform shader p2;
uniform shader p3;
uniform float2 size;
uniform float2 grab;
uniform float2 tip;
uniform float r0;
uniform float r1;
uniform float rmin;
uniform float frontIdx;
uniform float underIdx;
uniform float shA;
uniform float shW;
uniform float eA;
uniform float eW;
uniform float amb1;
uniform float amb2;
uniform float g2;
uniform float bg;
uniform float ba;
uniform float grain;
uniform float edgeInk;
uniform float px;
uniform float fade;
uniform float clearUnder; // 1: a riffle sheet over another canvas, the page beneath is transparent
uniform float spec;

const float PI = 3.14159265;

half4 pageAt(float idx, float2 q) {
  if (idx < 0.5) { return p0.eval(q); }
  if (idx < 1.5) { return p1.eval(q); }
  if (idx < 2.5) { return p2.eval(q); }
  return p3.eval(q);
}

// Signed distance into the sheet (negative outside it).
float edgeDist(float2 q) {
  return min(min(q.x, size.x - q.x), min(q.y, size.y - q.y));
}

float hash21(float2 q) {
  return fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453);
}

// The back of the sheet: off-white paper with the print showing through, mirrored, and a fine grain.
float3 backAt(float2 q) {
  half4 f = pageAt(frontIdx, clamp(q, float2(0.0), size));
  float lum = (float(f.r) + float(f.g) + float(f.b)) / 3.0;
  float v = bg * (1.0 - ba * (1.0 - lum));
  v *= 1.0 + grain * (hash21(floor(q * px)) - 0.5);
  return float3(v);
}

// A soft paper sheen where the roll faces the key light (top-left), around 0.78 of half a turn.
float sheen(float th) {
  float k = (th - 0.78 * PI) / 0.12;
  return spec * exp(-k * k);
}

half4 main(float2 p) {
  half4 underC = pageAt(underIdx, p);
  if (fade >= 0.0) {
    return half4(mix(float3(pageAt(frontIdx, p).rgb), float3(underC.rgb), fade), 1.0);
  }
  float2 v = grab - tip;
  float D = length(v);
  if (D < 0.25) { return pageAt(frontIdx, p); }
  float2 d = v / D;
  float2 n = float2(-d.y, d.x);
  float Rc = max(rmin, r0);
  // The axis sits where the grabbed point, wrapped half a turn and laid flat, lands on the tip.
  float2 a = grab - (D + PI * Rc) * 0.5 * d;
  float x = dot(p - a, d);
  float t = dot(p - a, n);
  float R = clamp(r0 + r1 * t, rmin, 400.0);

  // The page beneath, premultiplied: opaque, or (riffle) transparent with only the shadow as alpha.
  float4 under = clearUnder > 0.5 ? float4(0.0) : float4(float3(underC.rgb), 1.0);

  if (x > R) {
    // Past the roll: the page beneath, darkened by the roll's soft cast shadow.
    float s = shA * exp(-(x - R) / shW);
    return half4(clearUnder > 0.5 ? float4(0.0, 0.0, 0.0, s) : float4(under.rgb * (1.0 - s), 1.0));
  }

  if (x <= 0.0) {
    // The flat part of the sheet, possibly covered by its own flat back lying over it.
    float2 qb = p + (PI * R - 2.0 * x) * d;
    float db = edgeDist(qb);
    float3 f = float3(pageAt(frontIdx, p).rgb);
    if (db < 0.0) { f *= 1.0 - eA * exp(db / eW); }
    float3 b = backAt(qb);
    // The cut edge of the sheet: a hairline of thickness.
    b *= 1.0 - edgeInk * (1.0 - smoothstep(0.0, 0.9, db));
    float cov = clamp(db * px + 0.5, 0.0, 1.0);
    return half4(mix(f, b, cov), 1.0);
  }

  // Inside the roll: the rising front (lower), then the back coming over (upper).
  float th1 = asin(clamp(x / R, 0.0, 1.0));
  float th2 = PI - th1;
  float2 q1 = p + (R * th1 - x) * d;
  float2 q2 = p + (R * th2 - x) * d;
  float4 shadowed = clearUnder > 0.5 ? float4(0.0, 0.0, 0.0, shA) : float4(under.rgb * (1.0 - shA), 1.0);
  float4 col = shadowed;
  float c1 = clamp(edgeDist(q1) * px + 0.5, 0.0, 1.0);
  float3 f1 = float3(pageAt(frontIdx, q1).rgb) * (amb1 + (1.0 - amb1) * cos(th1));
  col = mix(col, float4(f1, 1.0), c1);
  float c2 = clamp(edgeDist(q2) * px + 0.5, 0.0, 1.0);
  float3 b2 = backAt(q2) * (amb2 + (1.0 - amb2) * pow(abs(cos(th2)), g2));
  b2 = min(float3(1.0), b2 + sheen(th2));
  col = mix(col, float4(b2, 1.0), c2);
  float cr = clamp((R - x) * px + 0.5, 0.0, 1.0);
  return half4(mix(shadowed, col, cr));
}
`;

export const curlEffect = Skia.RuntimeEffect.Make(SOURCE)!;
