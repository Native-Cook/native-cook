package com.ngocdevv.nativecook.blocks.progress.potionvialupload

internal object PotionVialUploadShaders {
val source = """

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
  vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
  float a = h21(i), b = h21(i + vec2(1.0, 0.0)), c = h21(i + vec2(0.0, 1.0)), d = h21(i + vec2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) { return 0.55 * vnoise(p) + 0.3 * vnoise(p * 2.1 + 3.7) + 0.15 * vnoise(p * 4.3 - 1.3); }half4 liquid(vec2 xy,float x0,float y0,float x1,float y1,float front,float bulge,float t,float loop,float flow,float glow,float gone,float brew) {
  float hh = (y1 - y0);
  float yn = (xy.y - y0) / hh;          // 0 top … 1 bottom
  float yc = yn * 2.0 - 1.0;
  float edge = front + bulge * (1.0 - yc * yc);
  if (xy.x > edge) return half4(0.0);
  float ang = 6.2831853 * t / loop;
  vec2 q = vec2(xy.x / 22.0 + cos(ang) * 0.8, xy.y / 12.0 + sin(ang) * 0.8);
  float n = fbm(q + vec2(-flow * 0.05, 0.0));
  // body: bright yellow-green, a lighter top band, a deeper bottom band
  vec3 top = vec3(0.74, 0.98, 0.48);
  vec3 mid = vec3(0.5, 0.93, 0.24);
  vec3 deep = vec3(0.33, 0.76, 0.16);
  vec3 bot = vec3(0.2, 0.55, 0.1);
  vec3 col = mix(top, mid, smoothstep(0.08, 0.3, yn));
  col = mix(col, deep, smoothstep(0.62, 0.86, yn));
  col = mix(col, bot, smoothstep(0.86, 0.98, yn));
  // drifting blotches of darker potion
  col = mix(col, deep * 0.72, smoothstep(0.44, 0.62, n) * 0.85);
  // the meniscus: a dark crescent along the edge
  float inner = front - 5.0 + (bulge - 1.5) * (1.0 - yc * yc);
  float cres = smoothstep(inner - 0.5, inner + 0.5, xy.x);
  col = mix(col, vec3(0.173, 0.486, 0.122), cres * 0.9);
  // the glass's spiral, seen through the potion: slanted, bowed white lines
  float u = xy.x - yc * 12.0 - 4.5 * yc * yc - flow;
  float sp = abs(fract(u / 34.0) - 0.5) * 34.0;
  float line = 1.0 - smoothstep(1.3, 2.1, sp);
  float shade = 1.0 - smoothstep(1.2, 2.4, abs(sp - 3.0));
  col = mix(col, deep * 0.85, shade * 0.35 * (1.0 - cres));
  col = mix(col, vec3(0.96, 1.0, 0.92), line * 1.0 * (1.0 - cres) * smoothstep(0.03, 0.12, yn) * (1.0 - smoothstep(0.9, 0.98, yn)));
  // glare along the top
  float glare = (1.0 - smoothstep(0.0, 1.3, abs(xy.y - (y0 + 3.2)))) * step(0.3, fract(xy.x / 61.0 + 0.13));
  col = mix(col, vec3(1.0), glare * 0.75 * (1.0 - cres));
  // a young brew is murkier and darker; it ripens to neon as the upload completes
  col = mix(col * vec3(0.6, 0.78, 0.62), col, smoothstep(0.02, 0.85, brew));
  col *= 1.0 + glow * 0.05;
  float a = 1.0;
  // dissolve into mist: a noise threshold sweeping through, with a bright burning edge
  if (gone > 0.0) {
    float d = fbm(xy / 7.0 + 4.0) * 0.45 + 0.55 * (1.0 - (xy.x - x0) / (x1 - x0));
    float th = gone * 1.15 - 0.05;
    if (d < th) return half4(0.0);
    float rim = 1.0 - smoothstep(0.0, 0.07, d - th);
    col = mix(col, vec3(0.92, 1.0, 0.78), rim);
  }
  return half4(half3(col * a), a);
}
half4 smoke(vec2 xy,float cy,float mx,float my,float t,float loop,float level,float mist,float glow,float flowDir) {
  float ang = 6.2831853 * t / loop;
  vec2 c = vec2(cos(ang), sin(ang));
  // smoke: twisted ribbons hugging the vial
  float dy = (xy.y - cy) / 70.0;
  float band = exp(-dy * dy * 2.2);
  vec2 q = vec2(xy.x / 46.0, xy.y / 26.0);
  float n = fbm(q + c * 0.9 + vec2(fbm(q * 0.7 - c * 0.6) * 1.6, 0.0));
  float rib = smoothstep(0.42, 0.62, n) * (1.0 - smoothstep(0.62, 0.8, n));
  float s = rib * band * (0.26 + 0.14 * glow) * level;
  vec3 col = mix(vec3(0.13, 0.32, 0.11), vec3(0.18, 0.46, 0.13), glow);
  // mist: a cone out of the mouth, up and to the right, streaked and drifting outwards
  vec2 d = xy - vec2(mx, my);
  vec2 dir = normalize(vec2(1.0, -0.32));
  float along = dot(d, dir);
  float across = dot(d, vec2(-dir.y, dir.x));
  float m = 0.0;
  if (along > 0.0 && mist > 0.0) {
    float width = (9.0 + along * 0.55) * (flowDir < 0.0 ? 0.6 : 1.0);
    float cone = 1.0 - smoothstep(0.0, width, abs(across));
    cone = cone * cone;
    float fade = 1.0 - smoothstep(45.0, 140.0, along);
    float ray = fbm(vec2(atan(across, along + 6.0) * 9.0, along / 26.0 - t * 2.4 * flowDir));
    float streak = fbm(vec2(along / 9.0 - t * 2.7 * flowDir, across / 5.0));
    m = cone * fade * (0.25 + 1.1 * ray * streak) * mist * (1.0 - smoothstep(0.0, 60.0, along) * 0.35);
  }
  vec3 mc = vec3(0.49, 0.94, 0.35);
  float a = clamp(s + m * 0.85, 0.0, 1.0);
  vec3 outc = (col * s + mc * m * 0.85) / max(a, 0.001);
  return half4(half3(outc * a), a);
}

uniform shader back; uniform shader frontArt; uniform shader solid; uniform shader maskArt;
uniform float4 geo; uniform float4 line; uniform float4 fields; uniform float4 fx;
float b2(float2 a) { a=floor(a); return fract(a.x/2.0+a.y*a.y*0.75); }
float b4(float2 a) { return b2(0.5*a)*0.25+b2(a); }
// Match the iOS reference's Display P3 runtime-effect working space.
// Android's window is sRGB, so decode image children before the post pass and encode once at output.
float linearChannel(float c) { return c <= 0.04045 ? c/12.92 : pow((c+0.055)/1.055,2.4); }
float encodedChannel(float c) { return c <= 0.0031308 ? c*12.92 : 1.055*pow(max(c,0.0),1.0/2.4)-0.055; }
float3 linearColor(float3 c) { return float3(linearChannel(c.r),linearChannel(c.g),linearChannel(c.b)); }
float3 encodedColor(float3 c) { return float3(encodedChannel(c.r),encodedChannel(c.g),encodedChannel(c.b)); }
float3 toP3(float3 c) {
  float3 l=linearColor(c);
  return encodedColor(float3(dot(l,float3(0.8224620,0.1775380,0.0)),dot(l,float3(0.0331942,0.9668058,0.0)),dot(l,float3(0.0170826,0.0723974,0.9105199))));
}
float3 toSrgb(float3 c) {
  float3 l=linearColor(c);
  return encodedColor(float3(dot(l,float3(1.2249402,-0.2249402,0.0)),dot(l,float3(-0.0420569,1.0420569,0.0)),dot(l,float3(-0.0196376,-0.0786360,1.0982736))));
}
half4 imageColor(half4 c) { return c.a > 0.0 ? half4(toP3(c.rgb/c.a)*c.a,c.a) : c; }
half4 over(half4 a,half4 b) { return a+b*(1.0-a.a); }
half4 main(float2 pos) {
  float px=fx.z;
  float2 cell=floor(pos/px), xy=(cell+0.5)*px;
  float2 local=(xy-float2(geo.w,line.x))/geo.z;
  half4 soft=back.eval(cell+0.5);
  if(local.x>=-20.0 && local.x<422.0 && local.y>=-150.0 && local.y<150.0)soft=over(smoke(local,0.0,339.0,0.0,line.z,12.0,1.0,fields.w,fields.z,1.0),soft);
  soft=over(frontArt.eval(cell+0.5),soft);
  soft=imageColor(floor(soft*255.0+0.5)/255.0);
  float th=b4(cell); float3 page=float3(10.0,8.0,13.0)/255.0;
  float3 col=page+floor((soft.rgb-page)*9.0+th)/9.0;
  float2 ll=local-float2(fx.y,-abs(fx.y)*0.4);
  float2 mxy=(xy-float2(fx.y,-abs(fx.y)*0.4)*geo.z)/px;
  half4 potion=half4(0.0);
  if(fx.x>0.0 && ll.x>=22.6 && ll.x<315.0 && ll.y>=-20.4 && ll.y<20.4)potion=liquid(ll,24.6,-20.4,313.0,20.4,fields.x,fields.y,line.z,12.0,line.w*120.0,fields.z,0.0,line.w)*maskArt.eval(mxy).a;
  half4 o=over(solid.eval(cell+0.5),potion);
  o=imageColor(floor(o*255.0+0.5)/255.0);
  if(o.a>0.004) { float qa=o.a>0.985 ? 1.0:floor(o.a*4.0+th)/4.0; if(qa>0.0)col=mix(col,o.rgb/o.a,qa); }
  return half4(toSrgb(col),1.0);
}

"""
}
