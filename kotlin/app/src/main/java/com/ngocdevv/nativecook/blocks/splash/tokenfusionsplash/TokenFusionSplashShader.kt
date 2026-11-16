package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

/**
 * The RN block's SkSL (`tfs-shaders.ts`) as AGSL (API 33+): one live signed-distance field. The fused violet core and its coins are
 * smooth-minned into one body with necks that thin and snap; the page's rings, vignette and grain; a dye that is stirred into the gel
 * once a coin is gone; white heat at the neck from the geometry two frames ago; SDF glyphs from a mount-time atlas, clipped to their own
 * body; and, when the body floods the screen, a slow marbling of the same gel. Uniform contract (dp, not pixels): core xy / r /
 * roundness, bodies xy / r / reach (up to 6), lagBodies = geometry two frames ago, dyes 0..1, meta = stretch / axis / flood / grain,
 * logo = opacity / size / negative-space cut / ring scale. The SkSL is unchanged: output stays sRGB (`outputP3` 0).
 */
internal const val TokenFusionSplashShaderSource = """
uniform float2 viewport;
uniform float3 mapping;
uniform float4 core;
uniform float4 bodies[6];
uniform float4 lagBodies[6];
uniform float3 colors[6];
uniform float dyes[6];
uniform float leans[6];
uniform float4 meta;
uniform float4 logo;
uniform float3 baseColor;
uniform float3 shadeColor;
uniform float3 hotColor;
uniform float3 pageColor;
uniform float3 ringColor;
uniform float3 inkColor;
uniform float count;
uniform float outputP3;
uniform shader glyphs;
float sat(float v) { return clamp(v, 0.0, 1.0); }
float smin(float a, float b, float k) {
  float h = sat(0.5 + 0.5 * (b - a) / max(k, 0.01));
  return mix(b, a, h) - k * h * (1.0 - h);
}
float2 rotate(float2 p, float a) { float c = cos(a), s = sin(a); return float2(c*p.x-s*p.y,s*p.x+c*p.y); }
float circle(float2 p, float3 b) { return length(p - b.xy) - b.z; }
float coreDistance(float2 p, float4 c, float stretch, float axis) {
  float2 q = rotate(p - c.xy, -axis);
  q = float2(q.x / max(0.7, stretch), q.y * max(0.7, stretch));
  return length(q) - c.z;
}
float pairField(float2 p, float4 c, float4 b, float d, float stretch, float axis) {
  if (b.z < 0.05) return d;
  float reach = b.w;
  if (reach < 0.001) return smin(d,circle(p,b.xyz),8.0);
  float2 v = b.xy - c.xy;
  float len = max(length(v), 0.001);
  float2 dir = v / len;
  float2 start = c.xy + dir * c.z;
  float2 end = b.xy - dir * b.z;
  float2 segment = end - start;
  float u = sat(dot(p - start, segment) / max(dot(segment, segment), 0.001));
  // The radius ratio puts the waist 38% of the free gap from the small body.
  float waist = 8.0;
  float taper = u < 0.62 ? mix(min(c.z*0.30,24.0),waist,smoothstep(0.0,0.62,u)) : mix(waist,14.0,smoothstep(0.62,1.0,u));
  float neck = length(p - start - segment*u) - taper * reach;
  float blend = mix(8.0, 34.0, reach) * (0.62 + 0.38 * min(c.z / max(b.z, 1.0), 3.0) / 3.0);
  if (reach > 0.001) d = smin(d, neck + (1.0-reach)*70.0, blend);
  return smin(d, circle(p, b.xyz), blend);
}
float field(float2 p, float4 c, float stretch, float axis) {
  float d = coreDistance(p,c,stretch,axis);
  for (int i=0;i<6;i++) {
    if (float(i) < count) d = pairField(p,c,bodies[i],d,stretch,axis);
  }
  return d;
}
float lagPair(float2 p,float4 c,float4 b,float stretch,float axis) {
  return pairField(p,c,b,coreDistance(p,c,stretch,axis),stretch,axis);
}
float neckCurvature(float2 p,float4 c,float4 b,float stretch,float axis) {
  float2 e=float2(3.0,0.0);
  return abs(lagPair(p+e,c,b,stretch,axis)+lagPair(p-e,c,b,stretch,axis)+lagPair(p+e.yx,c,b,stretch,axis)+lagPair(p-e.yx,c,b,stretch,axis)-4.0*lagPair(p,c,b,stretch,axis))/9.0;
}
float glyphDistance(float2 uv, float slot) {
  if (abs(uv.x) > 0.5 || abs(uv.y) > 0.5) return 16.0;
  return (glyphs.eval(float2((uv.x+0.5)*128.0 + slot*128.0,(uv.y+0.5)*128.0)).r-0.5)*32.0;
}
float3 toLinear(float3 c) { return mix(c / 12.92, pow((c+0.055)/1.055,float3(2.4)), step(float3(0.04045),c)); }
float3 toEncoded(float3 c) { c=max(c,float3(0.0)); return mix(c*12.92,1.055*pow(c,float3(1.0/2.4))-0.055,step(float3(0.0031308),c)); }
float3 p3(float3 c) {
  float3 v = toLinear(c);
  return toEncoded(float3(dot(v,float3(0.8225929,0.177534,0.0)), dot(v,float3(0.0331995,0.9667835,0.0)), dot(v,float3(0.0170853,0.0723957,0.9103015))));
}
float hash12(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash12(i), hash12(i + float2(1.0, 0.0)), f.x), mix(hash12(i + float2(0.0, 1.0)), hash12(i + float2(1.0, 1.0)), f.x), f.y);
}
float fbm(float2 p) {
  return 0.5 * vnoise(p) + 0.25 * vnoise(p * 2.03 + 7.1) + 0.125 * vnoise(p * 4.1 + 3.3) + 0.0625 * vnoise(p * 8.3 + 1.9);
}
half4 main(float2 xy) {
  float2 p = (xy-mapping.yz)/max(mapping.x,0.001);
  float d = field(p,core,meta.x,meta.y);
  float edge = 1.0-smoothstep(-0.5,0.5,d);
  float grain = fract(sin(dot(floor(xy),float2(12.9898,78.233)))*43758.5453)-0.5;
  float3 page = pageColor;
  float distanceToCentre = length(p-float2(201.0,400.0));
  float ringD = 10000.0;
  ringD = min(ringD,abs(distanceToCentre-162.0*logo.w));
  ringD = min(ringD,abs(distanceToCentre-240.0*logo.w));
  ringD = min(ringD,abs(distanceToCentre-320.0*logo.w));
  ringD = min(ringD,abs(distanceToCentre-406.0*logo.w));
  page = mix(page,ringColor,(1.0-smoothstep(0.12,0.85,ringD))*0.55);
  page *= 1.0 - 0.055 * sat(length((xy/viewport-0.5)*1.2));
  // One luminous source: the fused violet body. Standalone tokens are flat and unlit.
  float dc = coreDistance(p,core,meta.x,meta.y);
  float halo = exp(-max(d,0.0)/14.0)*0.16*exp(-max(dc,0.0)/90.0);
  float coreHalo = exp(-max(dc,0.0)/40.0)*0.10;
  page += baseColor * (halo+coreHalo) * (1.0-edge);
  if (d > 100.0) {
    page = clamp(page + grain*0.003,0.0,1.0);
    return half4(mix(page,p3(page),outputP3),1.0);
  }
  float2 e = float2(0.8,0.0);
  float xp = field(p+e,core,meta.x,meta.y), xm = field(p-e,core,meta.x,meta.y);
  float yp = field(p+e.yx,core,meta.x,meta.y), ym = field(p-e.yx,core,meta.x,meta.y);
  float2 gradient = float2(xp-xm,yp-ym)/1.6;
  float2 outward = gradient / max(length(gradient),0.001);
  float depth = sat(-d/40.0);
  float2 local = (p-core.xy)/max(core.z,1.0);
  float radial = sat(length(local));
  float3 normal = normalize(float3(outward * mix(0.85,0.15,depth),sqrt(max(0.01,1.0-radial*radial))));
  float diffuse = dot(normal,normalize(float3(-0.55,-0.65,0.68)));
  float shade = sat(0.42 + 0.50*local.x + 0.55*local.y - 0.22*diffuse);
  float3 material = mix(baseColor,shadeColor,shade*mix(0.50,0.92,core.w));
  material += hotColor * max(diffuse,0.0)*0.075*core.w;
  float rim = exp(-abs(d)/1.8) * max(0.0,dot(outward,normalize(float2(-0.65,-0.76))))*0.10;
  material += hotColor * rim;
  for (int i=0;i<6;i++) {
    if (float(i) >= count) continue;
    float4 b = bodies[i];
    if (b.z < 0.05 && dyes[i] < 0.001) continue;
    float db = circle(p,b.xyz);
    float coinCoverage = (1.0-smoothstep(-1.0,0.0,db)) * (1.0-b.w*0.72);
    material = mix(material,colors[i],coinCoverage);
    float2 axis = b.xy-core.xy;
    float len = max(length(axis),0.01);
    float2 dir = axis/len;
    float2 entry = core.xy+dir*min(core.z,len);
    // The dye is stirred in: once the coin is gone it curls round the core as it ages, its front sinking from the rim toward
    // the middle while it spreads and fades. Before that it is a glow at the point of entry.
    float age01 = 1.0 - dyes[i];
    float gone = 1.0 - sat(b.z / 12.0);
    float rc = min(core.z, 74.0);
    float2 pd = p - core.xy;
    float tw = age01 * 3.4 * gone * (1.0 - sat(length(pd) / (rc * 1.5)));
    float2 pr = rotate(pd, tw);
    float2 anchor = dir * rc * mix(1.0, 0.32, smoothstep(0.0, 0.85, age01) * gone);
    float2 dv = pr - anchor;
    float stir = 0.65 + 0.7 * fbm(pr * 0.06 + float(i) * 3.7);
    float dyeDistance = mix(length(p-entry), length(dv), gone);
    float dye = exp(-dyeDistance/((14.0+52.0*age01)*stir))*dyes[i]*b.w*mix(0.32, 0.85, gone)*(1.0 - sat(meta.z * 2.2));
    // once inside, the token's colour is a luminous tint of the gel (full-value hue), not a dark stain
    float3 tint = colors[i] / max(max(colors[i].r, colors[i].g), max(colors[i].b, 0.05));
    material = mix(material,mix(colors[i],mix(tint,hotColor,0.25),gone),dye);
    // Curvature at the delayed neck geometry controls the white heat, not a flash.
    float4 lb = lagBodies[i];
    float2 lv = lb.xy-core.xy;
    float ll = max(length(lv),0.01);
    float2 ld = lv/ll;
    float gap = max(ll-core.z-lb.z,0.0);
    float2 waist = core.xy+ld*(core.z+gap*0.62);
    float heat=0.0;
    if (lb.w > 0.001 && dot(p-waist,p-waist) < 3600.0) {
      float curvature=neckCurvature(p,core,lb,meta.x,meta.y);
      heat=exp(-dot(p-waist,p-waist)/(30.0*30.0))*lb.w*sat(lb.z/12.0)*sat(0.90+curvature*0.2)*0.98;
    }
    material = mix(material,hotColor,heat);
    // SDF glyphs remain clipped to their own body, including during capture.
    float2 guv = rotate(p-b.xy,-leans[i]);
    float angle = atan(axis.y,axis.x);
    guv = rotate(rotate(guv,-angle)*float2(1.0+b.w*0.15,1.0),angle);
    float glyph = glyphDistance(guv/max(b.z*1.5,0.1),float(i+1));
    float glyphAA = max(0.4,128.0/max(b.z*1.5,1.0))*0.5;
    float glyphAlpha = (1.0-smoothstep(-glyphAA,glyphAA,glyph))*sat((b.z-10.0)/8.0)*(1.0-smoothstep(-1.0,0.0,db));
    material = mix(material,inkColor,glyphAlpha);
  }
  // When the body floods the screen it must not turn into a flat fill: a deep, slow marbling of the same gel (domain-warped noise
  // in the body's own coordinates, so it grows with the flood), an upper-left pool of light, the page's rings echoed as hairlines
  // and a vignette. Everything scales with the flood, so the small body keeps the clean matte look.
  float fl = smoothstep(0.02, 0.6, meta.z);
  if (fl > 0.001) {
    float2 qn = (p - core.xy) / max(core.z, 90.0);
    float2 w1 = float2(fbm(qn * 1.6 + 3.1), fbm(qn * 1.6 + 9.7));
    float n1 = fbm(qn * 2.3 + 1.9 * w1);
    float n2 = fbm(qn * 4.1 + 2.7 * w1 + 5.0);
    material = mix(material, shadeColor, fl * (1.0 - smoothstep(0.30, 0.64, n1)) * 0.13);
    material = mix(material, mix(baseColor, hotColor, 0.55), fl * smoothstep(0.40, 0.76, n1) * 0.12);
    material += hotColor * fl * (n2 - 0.5) * 0.05;
    float pool = exp(-length(p - float2(60.0, 120.0)) / 340.0);
    material = mix(material, hotColor, fl * pool * 0.20);
    float rd = length(p - float2(201.0, 400.0));
    float rings = 1.0;
    rings = min(rings, abs(rd - 162.0 * logo.w) / 1.2);
    rings = min(rings, abs(rd - 240.0 * logo.w) / 1.2);
    rings = min(rings, abs(rd - 320.0 * logo.w) / 1.2);
    rings = min(rings, abs(rd - 406.0 * logo.w) / 1.2);
    material = mix(material, hotColor, fl * (1.0 - smoothstep(0.35, 1.0, rings)) * 0.07);
    material *= 1.0 - 0.10 * fl * sat(length((xy / viewport - 0.5) * 1.25));
  }
  material += grain * meta.w;
  float3 color = mix(page,material,edge);
  float2 markUV = (p-core.xy)/max(logo.y*1.333333,1.0);
  float markD = glyphDistance(markUV,0.0);
  float markAA = 64.0/max(logo.y*1.333333,1.0);
  float markAlpha = (1.0-smoothstep(-markAA,markAA,markD))*logo.x*edge;
  color = mix(color,mix(inkColor,pageColor,logo.z),markAlpha);
  color += grain*0.003;
  color = clamp(color,0.0,1.0);
  return half4(mix(color,p3(color),outputP3),1.0);
}
"""
