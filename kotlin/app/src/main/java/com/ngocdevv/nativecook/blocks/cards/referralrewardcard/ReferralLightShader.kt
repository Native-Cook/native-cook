package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

// Three soft lamps illuminate the glass and spill onto the page outside the card.
// Coordinates are in dp, with the origin at the card's top left. Lamp geometry is
// calculated once per frame by ReferralLight instead of once per fragment.
internal val referralLightAgsl =
    """
// Some falloffs deliberately reverse their edges; the explicit ramp also supports those.
float rampStep(float a, float b, float x) {
  float t = clamp((x - a) / (b - a), 0.0, 1.0);
  return t * t * (3.0 - 2.0 * t);
}
uniform float2 origin;
uniform float2 card;
uniform float radius;
uniform float phase;
uniform float light;
uniform float flare;
uniform float bleed;
uniform float scale;

const float TAU = 6.2831853;

// Signed distance to the rounded card or button, negative inside its face.
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

// The light under the glass at a point of the card. The lamps circle slowly,
// each on its own small ellipse, and screen over one another.
uniform float2 lampCenter1;
uniform float2 lampCenter2;
uniform float2 lampCenter3;
uniform float2 lampInverse1;
uniform float2 lampInverse2;
uniform float2 lampInverse3;
uniform float3 lampColor1;
uniform float3 lampColor2;
uniform float3 lampColor3;
uniform float2 highlightFrom;
float3 field(float2 p) {
  float2 p1 = (p - lampCenter1) * lampInverse1;
  float2 p2 = (p - lampCenter2) * lampInverse2;
  float2 p3 = (p - lampCenter3) * lampInverse3;
  return (1.0 - (1.0 - lampColor1 * exp(-dot(p1, p1)))
              * (1.0 - lampColor2 * exp(-dot(p2, p2)))
              * (1.0 - lampColor3 * exp(-dot(p3, p3)))) * light;
}

// Stationary per-pixel grain breaks up the dark gradients without sparkling.
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
  float3 page = field(p) * 0.58 * exp(-away * away / 5400.0) * rampStep(bleed, bleed * 0.55, away);
  float under = box(p - mid - float2(0.0, 12.0), mid - 4.0, radius);
  return page * (1.0 - 0.34 * rampStep(30.0, -8.0, under));
}

"""
