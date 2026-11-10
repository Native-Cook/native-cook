// Filament Bulb Dimmer — the bulb's light on the app (Skia, drawn over the card, the two readings and
// the Energy Usage tab).
//
// Each panel is a rounded rectangle flat on the page with a soft 6 pt bevel round its edge. The light
// sits in front of the page at the filament (uL: x, y, depth): the flat face takes it by Lambert and
// inverse square, the bevel facing the bulb takes more and a crisp specular line, so the edges nearest
// the bulb glint and the glints slide round as it swings. The output is light only (premultiplied,
// nearly no coverage): it adds to whatever the panel shows underneath, text included, the way light does.
export const FBD_LIGHT_SKSL = `
uniform float3 uL;        // filament x, y (pt), depth in front of the page (pt)
uniform float3 uCol;      // the light's colour × output, display-referred
uniform float4 uCard;
uniform float4 uChipL;
uniform float4 uChipR;
uniform float4 uTab;
uniform float4 uRad;      // corner radii: card, chips, tab, -

float sdRR(float2 p, float4 r, float rad) {
  float2 c = r.xy + r.zw*0.5;
  float2 q = abs(p - c) - r.zw*0.5 + rad;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}

float3 lit(float2 p, float4 r, float rad, float albedo) {
  float sd = sdRR(p, r, rad);
  if (sd > 0.75) return float3(0.0);
  float e = 0.6;
  float2 g = normalize(float2(sdRR(p + float2(e, 0.0), r, rad) - sdRR(p - float2(e, 0.0), r, rad),
                              sdRR(p + float2(0.0, e), r, rad) - sdRR(p - float2(0.0, e), r, rad)) + 1e-5);
  float cov = clamp(0.5 - sd, 0.0, 1.0);
  float t = clamp(-sd/6.0, 0.0, 1.0);
  float3 n = normalize(float3(g*(1.0 - t)*1.6, 0.25 + t));
  float3 Lv = float3(uL.xy - p, uL.z);
  float d2 = dot(Lv, Lv);
  float3 l = Lv*inversesqrt(d2);
  float att = uL.z*uL.z/d2;
  float diff = max(dot(n, l), 0.0)*att;
  float spec = pow(max(dot(reflect(-l, n), float3(0.0, 0.0, 1.0)), 0.0), 18.0)*sqrt(att)*(1.0 - t);
  // the faces take a little, the bevels facing the bulb most
  return uCol*(albedo*diff*mix(0.9, 0.1, t) + 0.5*spec)*cov;
}

half4 main(float2 p) {
  float3 c = lit(p, uCard, uRad.x, 0.32) + lit(p, uChipL, uRad.y, 0.4) + lit(p, uChipR, uRad.y, 0.4) + lit(p, uTab, uRad.z, 0.25);
  // a pixel of zero alpha is dropped when the canvas is composited: give the light a little coverage
  float a = clamp(max(c.r, max(c.g, c.b))*0.3, 0.0, 1.0);
  return half4(half3(c), half(a));
}
`;
