#include <metal_stdlib>
using namespace metal;

float potionVialUploadHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float potionVialUploadNoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
  float a = potionVialUploadHash(i), b = potionVialUploadHash(i + float2(1.0, 0.0)), c = potionVialUploadHash(i + float2(0.0, 1.0)), d = potionVialUploadHash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float potionVialUploadFbm(float2 p) { return 0.55 * potionVialUploadNoise(p) + 0.3 * potionVialUploadNoise(p * 2.1 + 3.7) + 0.15 * potionVialUploadNoise(p * 4.3 - 1.3); }half4 potionVialUploadLiquid(float2 xy, float x0, float y0, float x1, float y1, float front, float bulge, float t, float loop, float flow, float glow, float gone, float brew) {
  float hh = (y1 - y0);
  float yn = (xy.y - y0) / hh;          // 0 top … 1 bottom
  float yc = yn * 2.0 - 1.0;
  float edge = front + bulge * (1.0 - yc * yc);
  if (xy.x > edge) return half4(0.0);
  float ang = 6.2831853 * t / loop;
  float2 q = float2(xy.x / 22.0 + cos(ang) * 0.8, xy.y / 12.0 + sin(ang) * 0.8);
  float n = potionVialUploadFbm(q + float2(-flow * 0.05, 0.0));
  // body: bright yellow-green, a lighter top band, a deeper bottom band
  float3 top = float3(0.74, 0.98, 0.48);
  float3 mid = float3(0.5, 0.93, 0.24);
  float3 deep = float3(0.33, 0.76, 0.16);
  float3 bot = float3(0.2, 0.55, 0.1);
  float3 col = mix(top, mid, smoothstep(0.08, 0.3, yn));
  col = mix(col, deep, smoothstep(0.62, 0.86, yn));
  col = mix(col, bot, smoothstep(0.86, 0.98, yn));
  // drifting blotches of darker potion
  col = mix(col, deep * 0.72, smoothstep(0.44, 0.62, n) * 0.85);
  // the meniscus: a dark crescent along the edge
  float inner = front - 5.0 + (bulge - 1.5) * (1.0 - yc * yc);
  float cres = smoothstep(inner - 0.5, inner + 0.5, xy.x);
  col = mix(col, float3(0.173, 0.486, 0.122), cres * 0.9);
  // the glass's spiral, seen through the potion: slanted, bowed white lines
  float u = xy.x - yc * 12.0 - 4.5 * yc * yc - flow;
  float sp = abs(fract(u / 34.0) - 0.5) * 34.0;
  float line = 1.0 - smoothstep(1.3, 2.1, sp);
  float shade = 1.0 - smoothstep(1.2, 2.4, abs(sp - 3.0));
  col = mix(col, deep * 0.85, shade * 0.35 * (1.0 - cres));
  col = mix(col, float3(0.96, 1.0, 0.92), line * 1.0 * (1.0 - cres) * smoothstep(0.03, 0.12, yn) * (1.0 - smoothstep(0.9, 0.98, yn)));
  // glare along the top
  float glare = (1.0 - smoothstep(0.0, 1.3, abs(xy.y - (y0 + 3.2)))) * step(0.3, fract(xy.x / 61.0 + 0.13));
  col = mix(col, float3(1.0), glare * 0.75 * (1.0 - cres));
  // a young brew is murkier and darker; it ripens to neon as the upload completes
  col = mix(col * float3(0.6, 0.78, 0.62), col, smoothstep(0.02, 0.85, brew));
  col *= 1.0 + glow * 0.05;
  float a = 1.0;
  // dissolve into mist: a noise threshold sweeping through, with a bright burning edge
  if (gone > 0.0) {
    float d = potionVialUploadFbm(xy / 7.0 + 4.0) * 0.45 + 0.55 * (1.0 - (xy.x - x0) / (x1 - x0));
    float th = gone * 1.15 - 0.05;
    if (d < th) return half4(0.0);
    float rim = 1.0 - smoothstep(0.0, 0.07, d - th);
    col = mix(col, float3(0.92, 1.0, 0.78), rim);
  }
  return half4(half3(col * a), half(a));
}
half4 potionVialUploadSmoke(float2 xy, float cy, float mx, float my, float t, float loop, float level, float mist, float glow, float flowDir) {
  float ang = 6.2831853 * t / loop;
  float2 c = float2(cos(ang), sin(ang));
  // smoke: twisted ribbons hugging the vial
  float dy = (xy.y - cy) / 70.0;
  float band = exp(-dy * dy * 2.2);
  float2 q = float2(xy.x / 46.0, xy.y / 26.0);
  float n = potionVialUploadFbm(q + c * 0.9 + float2(potionVialUploadFbm(q * 0.7 - c * 0.6) * 1.6, 0.0));
  float rib = smoothstep(0.42, 0.62, n) * (1.0 - smoothstep(0.62, 0.8, n));
  float s = rib * band * (0.26 + 0.14 * glow) * level;
  float3 col = mix(float3(0.13, 0.32, 0.11), float3(0.18, 0.46, 0.13), glow);
  // mist: a cone out of the mouth, up and to the right, streaked and drifting outwards
  float2 d = xy - float2(mx, my);
  float2 dir = normalize(float2(1.0, -0.32));
  float along = dot(d, dir);
  float across = dot(d, float2(-dir.y, dir.x));
  float m = 0.0;
  if (along > 0.0 && mist > 0.0) {
    float width = (9.0 + along * 0.55) * (flowDir < 0.0 ? 0.6 : 1.0);
    float cone = 1.0 - smoothstep(0.0, width, abs(across));
    cone = cone * cone;
    float fade = 1.0 - smoothstep(45.0, 140.0, along);
    float ray = potionVialUploadFbm(float2(atan2(across, along + 6.0) * 9.0, along / 26.0 - t * 2.4 * flowDir));
    float streak = potionVialUploadFbm(float2(along / 9.0 - t * 2.7 * flowDir, across / 5.0));
    m = cone * fade * (0.25 + 1.1 * ray * streak) * mist * (1.0 - smoothstep(0.0, 60.0, along) * 0.35);
  }
  float3 mc = float3(0.49, 0.94, 0.35);
  float a = clamp(s + m * 0.85, 0.0, 1.0);
  float3 outc = (col * s + mc * m * 0.85) / max(a, 0.001);
  return half4(half3(outc * a), half(a));
}

struct PotionVialUploadUniforms { float4 geo; float4 line; float4 fields; float4 fx; };
struct PotionVialUploadVertex { float4 position [[position]]; float2 uv; };
vertex PotionVialUploadVertex potionVialUploadVertex(uint id [[vertex_id]]) {
  float2 p = float2((id << 1) & 2, id & 2);
  return {float4(p * float2(2,-2)+float2(-1,1),0,1),p};
}
float potionVialUploadB2(float2 a) { a=floor(a); return fract(a.x/2+a.y*a.y*0.75); }
float potionVialUploadB4(float2 a) { return potionVialUploadB2(0.5*a)*0.25+potionVialUploadB2(a); }
// Skia image children enter the runtime effect in its Display P3 working space.
// Keep the original sRGB bitmap bytes, then perform that conversion before dithering.
float3 potionVialUploadToP3(float3 c) {
  float3 l = select(pow((c + 0.055) / 1.055, float3(2.4)), c / 12.92, c <= 0.04045);
  float3 p = float3(dot(l,float3(0.8224620,0.1775380,0)),
                    dot(l,float3(0.0331942,0.9668058,0)),
                    dot(l,float3(0.0170826,0.0723974,0.9105199)));
  return select(1.055*pow(max(p,0.0),float3(1.0/2.4))-0.055, p*12.92, p <= 0.0031308);
}
float4 potionVialUploadImageColor(float4 c) {
  return c.a > 0 ? float4(potionVialUploadToP3(c.rgb/c.a)*c.a,c.a) : c;
}
float4 potionVialUploadOver(float4 a,float4 b) { return a+b*(1-a.a); }
fragment half4 potionVialUploadFragment(PotionVialUploadVertex v [[stage_in]],
  constant PotionVialUploadUniforms &u [[buffer(0)]],
  texture2d<float> back [[texture(0)]], texture2d<float> front [[texture(1)]],
  texture2d<float> solid [[texture(2)]], texture2d<float> mask [[texture(3)]]) {
  constexpr sampler nearest(coord::pixel,address::clamp_to_edge,filter::nearest);
  float px=4/u.line.y;
  float2 cell=floor(v.uv*u.geo.xy/px), xy=(cell+0.5)*px;
  float2 local=(xy-float2(u.geo.w,u.line.x))/u.geo.z;
  float4 soft=back.sample(nearest,cell+0.5);
  if(local.x>=-20 && local.x<422 && local.y>=-150 && local.y<150) {
    float4 smoke=float4(potionVialUploadSmoke(local,0,339,0,u.line.z,12,1,u.fields.w,u.fields.z,1));
    soft=potionVialUploadOver(smoke,soft);
  }
  soft=potionVialUploadOver(front.sample(nearest,cell+0.5),soft);
  soft=potionVialUploadImageColor(floor(soft*255+0.5)/255);
  float th=potionVialUploadB4(cell);
  float3 page=float3(10,8,13)/255;
  float3 col=page+floor((soft.rgb-page)*9+th)/9;
  float2 liquidLocal=local-float2(u.fx.y,-abs(u.fx.y)*0.4);
  float2 mxy=(xy-float2(u.fx.y,-abs(u.fx.y)*0.4)*u.geo.z)/px;
  float4 liquid=float4(0);
  if(u.fx.x>0 && liquidLocal.x>=22.6 && liquidLocal.x<315 && liquidLocal.y>=-20.4 && liquidLocal.y<20.4) {
    liquid=float4(potionVialUploadLiquid(liquidLocal,24.6,-20.4,313,20.4,u.fields.x,u.fields.y,u.line.z,12,u.line.w*120,u.fields.z,0,u.line.w));
    liquid*=mask.sample(nearest,mxy).a;
  }
  float4 o=potionVialUploadOver(solid.sample(nearest,cell+0.5),liquid);
  o=potionVialUploadImageColor(floor(o*255+0.5)/255);
  if(o.a>0.004) {
    float qa=o.a>0.985 ? 1:floor(o.a*4+th)/4;
    if(qa>0)col=mix(col,o.rgb/o.a,qa);
  }
  return half4(half3(col),1);
}
