package com.ngocdevv.nativecook.blocks.pickers.windchimetones

internal object WindChimeTonesShader {
    val source = """
uniform float4 uSpeaker;
uniform float2 uState;
uniform float uTime;
uniform float4 uRings0;
uniform float4 uRings1;
uniform float4 uRings2;
uniform float4 uRings3;
const float PI=3.14159265;
const float SPEED=420.0;
float windChimeTonesSq(float v) { return v*v; }
float3 windChimeTonesLin(float3 c) { return pow(c,float3(2.2)); }
float windChimeTonesSdSeg(float2 p,float2 a,float2 b) {
    float2 pa=p-a, ba=b-a;
    return length(pa-ba*clamp(dot(pa,ba)/dot(ba,ba),0.0,1.0));
}
float2 windChimeTonesRing(int i) {
    float4 v=i<2 ? uRings0 : i<4 ? uRings1 : i<6 ? uRings2 : uRings3;
    return i-2*(i/2)==0 ? v.xy : v.zw;
}
float3 windChimeTonesEncode(float3 c) {
    return pow(clamp(c/(float3(1.0)+max(c-float3(0.85),float3(0.0))*0.6),float3(0),float3(1)),float3(1.0/2.2));
}
half4 main(float2 position) {
  float2 s = position;
  float2 c = uSpeaker.xy;
  float R = uSpeaker.z;
  float power = uState.x;
  float2 p = s - c;
  float r = length(p);
  // the room: grey, a pool of light behind the speaker that breathes with the chimes
  float3 col = windChimeTonesLin(float3(0.165));
  col += windChimeTonesLin(float3(0.3, 0.29, 0.28)) * exp(-windChimeTonesSq(r / (R * 1.9))) * (0.55 + 0.35 * uState.y) * (0.5 + 0.5 * power);
  // the rings
  for (int i = 0; i < 8; i++) {
    float2 g = windChimeTonesRing(i);
    if (g.x < 0.0) { continue; }
    float front = R + g.x * SPEED;
    float fade = g.y * exp(-g.x * 1.1) * power;
    float dd = r - front;
    col += windChimeTonesLin(float3(0.98, 0.95, 0.9)) * (exp(-windChimeTonesSq(dd / 1.6)) * 0.35 + exp(-windChimeTonesSq((dd + 14.0) / 12.0)) * 0.05) * fade;
  }
  // the stand: a slim brass yoke round the lower half, a post and a round foot, and their shadow
  float yoke = abs(r - R * 1.1) - 1.6;
  float lower = smoothstep(-0.15, 0.1, p.y / R);
  float post = windChimeTonesSdSeg(s, c + float2(0.0, R * 1.1), c + float2(0.0, R * 1.78)) - 2.2;
  float foot = length((s - c - float2(0.0, R * 1.8)) * float2(1.0, 3.6)) - R * 0.3;
  float3 brass = windChimeTonesLin(float3(0.78, 0.6, 0.38));
  float floorSh = exp(-windChimeTonesSq(length((s - c - float2(0.0, R * 1.86)) * float2(0.8, 3.0)) / (R * 0.55)));
  col *= 1.0 - 0.45 * floorSh;
  float stand = 1e9;
  stand = min(stand, (lower > 0.5 ? yoke : 1e9));
  stand = min(stand, post);
  if (foot < 0.0) {
    col = brass * (0.35 + 0.4 * smoothstep(R * 0.3, -R * 0.3, s.x - c.x)) * (0.7 + 0.3 * smoothstep(0.0, -6.0, foot));
  }
  if (stand < 0.0) {
    float lit = 0.55 + 0.45 * smoothstep(1.6, -1.6, stand);
    col = brass * lit * (0.6 + 0.6 * smoothstep(R, -R, s.x - c.x + p.y * 0.4));
  }
  // the speaker's shadow on the wall behind
  col *= 1.0 - 0.35 * exp(-windChimeTonesSq(max(length(p - float2(10.0, 14.0)) - R, 0.0) / 14.0)) * step(R, r);
  // the speaker
  if (r < R + 1.5) {
    float2 q = p / R;
    float rr = length(q);
    // the cone pushes the fabric out into a dome, and a ripple runs out across it
    float push = uSpeaker.w * power;
    float dome = 0.12 + 0.45 * push;
    float h = sqrt(max(1.0 - rr * rr, 0.0));
    float3 n = normalize(float3(q * dome / max(h, 0.15), 1.0));
    n = normalize(n + float3(q / max(rr, 0.01) * sin(rr * 18.0 - uTime * 30.0) * 0.06 * abs(push), 0.0));
    float3 L = normalize(float3(-0.55, -0.6, 0.6));
    float diff = max(dot(n, L), 0.0);
    // the weave: two sets of fine threads, over and under
    float2 wv = s * 0.95;
    float tx = 0.5 + 0.5 * sin(wv.x * PI) * sign(sin(wv.y * PI * 0.5));
    float ty = 0.5 + 0.5 * sin(wv.y * PI) * sign(sin(wv.x * PI * 0.5 + PI * 0.5));
    float weave = 0.72 + 0.28 * max(tx, ty);
    float3 fab = windChimeTonesLin(float3(0.095, 0.095, 0.1)) * weave * (0.55 + 0.9 * diff) * (0.55 + 0.45 * power);
    fab += float3(1.0) * 0.035 * pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 4.0) * weave * (0.4 + 0.6 * power);
    // the rim: a thin brushed ring, lit at its top left
    float rim = exp(-windChimeTonesSq((rr - 0.985) / 0.012));
    float rimLit = 0.25 + 0.75 * smoothstep(0.4, -0.8, q.x + q.y);
    fab = mix(fab, windChimeTonesLin(float3(0.55)) * rimLit, rim * 0.9);
    fab *= 1.0 - 0.35 * smoothstep(0.9, 1.0, rr) * (1.0 - rim);
    float a = 1.0 - smoothstep(1.0, 1.0 + 1.2 / R, rr);
    col = mix(col, fab, a);
  }
  return float4(windChimeTonesEncode(col), 1.0);
}
""".trimIndent()
}
