// The light the card lies on, as SkSL. It is half of one shader: this half is
// the lamps under the glass and what they spill onto the page; the other
// half, in referral-glass.tsx, is the slab that light is seen through.
//
// The slab calls three things from here and nothing else: `field(p)`, the
// light at a point of the card; `spill(p, d)`, the page at a point `d`
// outside the card's edge; and the helpers `box`, `normal` and `hash`.
// Points are in the card's coordinates, in points, origin at its top left.

export const LIGHT = `uniform float2 origin;
uniform float2 card;
uniform float radius;
uniform float2 lens;
uniform float3 key;
uniform float3 second;
uniform float3 deep;
uniform float phase;
uniform float light;
uniform float swell;
uniform float flare;
uniform float bleed;
uniform float scale;

const float TAU = 6.2831853;

float box(float2 p, float2 size, float r) {
  float2 q = abs(p) - size + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The outward normal of that box. It only turns inside the corners, so it
// has no seam within a corner radius of the edge, where it is used.
float2 normal(float2 p, float2 size, float r) {
  float2 q = abs(p) - size + r;
  float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 && q.y > 0.0) return s * normalize(q);
  return q.x > q.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

float lamp(float2 p, float2 c, float2 reach) {
  float2 d = (p - c) / reach;
  return exp(-dot(d, d));
}

float3 vivid(float3 c, float by) {
  float l = dot(c, float3(0.299, 0.587, 0.114));
  return clamp(l + (c - l) * by, 0.0, 1.0);
}

// The light under the glass at a point of the card. The lamps circle slowly,
// each on its own small ellipse, and screen over one another.
float3 field(float2 p) {
  float a = TAU * phase;
  float w = card.x;
  float grow = 0.72 + 0.28 * light;
  float2 c1 = lens + float2(16.0 * cos(a), 9.0 * sin(a));
  float2 c2 = float2(0.08 * w, card.y + 4.0) + float2(22.0 * sin(a + 1.0), 14.0 * cos(a + 1.0));
  float2 c3 = float2(0.95 * w, -6.0) + float2(18.0 * cos(a + 2.4), 13.0 * sin(2.0 * a + 0.6));
  float l1 = (0.70 + 0.13 * swell + 0.10 * flare) * lamp(p, c1, float2(0.40, 0.24) * w * grow * (1.0 + 0.14 * swell));
  float l2 = (0.86 + 0.14 * flare) * lamp(p, c2, float2(0.33, 0.30) * w * grow);
  float l3 = (0.80 + 0.14 * flare) * lamp(p, c3, float2(0.31, 0.28) * w * grow);
  float3 c = 1.0 - (1.0 - vivid(key, 1.6) * l1) * (1.0 - second * l2) * (1.0 - deep * l3);
  return c * light;
}

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

// The page round the card, at a point d outside its edge: the same light,
// dimmer, fading with distance and gone before the canvas ends. The card
// throws a soft shadow down onto it.
float3 spill(float2 p, float d) {
  float2 mid = card * 0.5;
  float away = max(d, 0.0);
  float3 page = field(p) * 0.58 * exp(-away * away / 5400.0) * smoothstep(bleed, bleed * 0.55, away);
  float under = box(p - mid - float2(0.0, 12.0), mid - 4.0, radius);
  return page * (1.0 - 0.34 * smoothstep(30.0, -8.0, under));
}
`;
