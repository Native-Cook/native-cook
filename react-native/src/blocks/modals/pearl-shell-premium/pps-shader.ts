// Pearl Shell Premium — the sheet's hero, in one Skia shader drawn over the top of the screen.
//
// Where the reference shows the friend's round photo against a violet tunnel of neon light, this
// draws the same disc: violet depth with bright arcs sweeping across it, and in its middle a pearl of
// glass — it bends the arcs behind it, carries a nacre sheen that turns with uSpin and a hot catch
// light. Around the disc the reference's field of four-point sparkles (violet, blue, pink) twinkles,
// each a whole number of times per loop; uBurst throws them outwards and brightens them.
// The shader's alpha is 0 outside the disc and the sparkles, so it sits on the black page.
export const PPS_SKSL = `
uniform float2 uRes;
uniform float3 uOrb;      // centre x, y and radius (pt)
uniform float uT;         // seconds into the loop
uniform float uLoop;
uniform float uSpin;      // the pearl's sheen, rad
uniform float uLift;      // 0 … 1, the pearl rising and swelling
uniform float uBurst;     // 0 … 1, the sparkles thrown out
uniform float uGlow;      // 0 … 1, extra light in the disc

const float TAU = 6.2831853;
float sq(float v) { return v*v; }
float h1(float n) { return fract(sin(n*91.3458)*47453.5453); }

// the tunnel of light inside the disc, in disc units (radius 1)
float3 tunnel(float2 q) {
  float3 col = mix(float3(0.24, 0.12, 0.55), float3(0.5, 0.25, 0.85), smoothstep(-1.0, 1.0, q.x - q.y*0.6));
  // arcs: circles centred down and to the left, sweeping slowly across
  float2 c = float2(-0.9, 1.3);
  float r = length(q - c);
  float sweep = uT/uLoop*TAU;
  for (int i = 0; i < 6; i++) {
    float rr = 1.15 + 0.32*float(i) + 0.06*sin(sweep + float(i));
    float w = 0.012 + 0.01*float(i == 2 ? 1 : 0);
    float band = exp(-sq((r - rr)/w));
    float3 bc = i == 2 ? float3(0.85, 0.85, 1.0) : mix(float3(0.65, 0.4, 1.0), float3(0.95, 0.55, 1.0), h1(float(i)));
    col += bc*band*(i == 2 ? 1.1 : 0.55);
    col += bc*exp(-sq((r - rr)/(w*8.0)))*0.12;
  }
  return col*(1.0 + 0.4*uGlow);
}

half4 main(float2 xy) {
  float3 col = float3(0.0);
  float a = 0.0;
  // the sparkles
  for (int i = 0; i < 56; i++) {
    float fi = float(i);
    float2 home = float2(h1(fi + 1.0)*uRes.x, 66.0 + h1(fi + 2.0)*170.0);
    float2 away = home - uOrb.xy;
    // keep them off the disc and below the title's lane
    if (length(away) < uOrb.z + 8.0) continue;
    float2 p = home + normalize(away)*uBurst*(18.0 + 30.0*h1(fi + 9.0));
    float size = 3.0 + 9.0*sq(h1(fi + 3.0));
    float laps = 1.0 + floor(h1(fi + 4.0)*3.0);
    float tw = 0.5 + 0.5*sin(TAU*(uT/uLoop*laps + h1(fi + 5.0)));
    float lum = (0.25 + 0.75*tw)*(1.0 + 1.5*uBurst);
    float2 d = abs(xy - p)/size;
    // a four-point star: thin cross with a soft core
    float star = max(exp(-d.x*9.0)*exp(-d.y*2.2), exp(-d.y*9.0)*exp(-d.x*2.2));
    star = max(star, exp(-dot(d, d)*14.0));
    float3 sc = h1(fi + 6.0) < 0.45 ? float3(0.62, 0.45, 1.0) : (h1(fi + 6.0) < 0.75 ? float3(0.5, 0.6, 1.0) : float3(0.92, 0.55, 0.95));
    float s = star*lum;
    col += sc*s; a = max(a, min(1.0, s));
  }
  // the disc
  float2 q = (xy - uOrb.xy)/uOrb.z;
  float dd = length(q);
  float edge = 1.0 - smoothstep(1.0 - 1.2/uOrb.z, 1.0, dd);
  if (edge > 0.0) {
    float3 dc = tunnel(q);
    // the pearl: a glass sphere in the middle
    float pr = 0.5*(1.0 + 0.12*uLift);
    float2 pc = float2(0.0, -0.06*uLift);
    float2 pq = (q - pc)/pr;
    float pd = length(pq);
    if (pd < 1.0) {
      float nz = sqrt(1.0 - pd*pd);
      float3 n = float3(pq, nz);
      // the tunnel behind, bent through the glass and turned upside down near the rim
      float3 seen = tunnel(pc*1.0 - pq*pr*(0.35 + 0.65*nz));
      // nacre: a thin-film sheen that turns
      float ang = atan(pq.y, pq.x) + uSpin;
      float film = 0.5 + 0.5*sin(ang*2.0 + pd*6.0 + uSpin*0.5);
      float3 nacre = mix(float3(0.95, 0.88, 1.0), float3(0.78, 0.92, 1.0), film);
      float fres = pow(1.0 - nz, 2.2);
      float3 pcol = mix(seen*0.8 + nacre*0.25, nacre, 0.25 + 0.6*fres);
      // light from the upper right
      float3 L = normalize(float3(0.45, -0.6, 0.66));
      pcol *= 0.75 + 0.35*max(dot(n, L), 0.0);
      float spec = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 60.0);
      pcol += float3(1.0)*spec*1.2;
      pcol += float3(1.0)*0.5*exp(-sq(length(pq - float2(-0.42, -0.45))/0.12));
      pcol += float3(0.9, 0.8, 1.0)*0.35*uGlow*nz;
      float pa = 1.0 - smoothstep(0.97, 1.0, pd);
      dc = mix(dc, pcol, pa);
    }
    // the pearl's soft shadow under it
    dc *= 1.0 - 0.25*exp(-sq(length(q - pc - float2(0.08, 0.55*pr))/0.22))*(pd < 1.0 ? 0.0 : 1.0);
    col = mix(col, dc, edge); a = max(a, edge);
  }
  // premultiplied: the sparkles add light, the disc covers
  return half4(half3(min(col, float3(1.0))), half(clamp(a, 0.0, 1.0)));
}
`;
