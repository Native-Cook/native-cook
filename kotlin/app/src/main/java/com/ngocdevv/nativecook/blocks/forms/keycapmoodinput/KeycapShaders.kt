package com.ngocdevv.nativecook.blocks.forms.keycapmoodinput

/** Packed PNG channels and lighting maths are translated from keycap-shader.ts. */
internal const val KEYCAP_CAP_AGSL = """
uniform shader data;
uniform shader picture;
uniform float2 centre;
uniform float scale, tileSize, pxPerPt, travel;
uniform float2 press;
uniform float3 albedoFrom, albedoTo;
uniform float progress;
uniform float2 origin;
uniform float radial;
uniform float3 upper, lower;
uniform float rimGain, innerGain;
uniform float3 led;
uniform float opacity, dim, gloss;
uniform float4 well;
uniform float softGradient;
uniform float2 flood;
uniform float mini, glassMode;
uniform float3 face;
uniform float faceRows[10];
uniform float3 ink, deboss;

float channel(float2 q, float tile) {
  if (q.x < 0.0 || q.y < 0.0 || q.x >= tileSize || q.y >= tileSize) return 0.0;
  return data.eval(float2(clamp(q.x, 0.5, tileSize - 0.5) + tile * tileSize,
                          clamp(q.y, 0.5, tileSize - 0.5))).r;
}
float3 decodeRGB(float3 c) {
  return float3(c.r <= 0.04045 ? c.r/12.92 : pow((c.r+0.055)/1.055,2.4),
                c.g <= 0.04045 ? c.g/12.92 : pow((c.g+0.055)/1.055,2.4),
                c.b <= 0.04045 ? c.b/12.92 : pow((c.b+0.055)/1.055,2.4));
}
float3 encodeRGB(float3 c) {
  c = max(c, float3(0.0));
  return float3(c.r <= 0.0031308 ? c.r*12.92 : 1.055*pow(c.r,1.0/2.4)-0.055,
                c.g <= 0.0031308 ? c.g*12.92 : 1.055*pow(c.g,1.0/2.4)-0.055,
                c.b <= 0.0031308 ? c.b*12.92 : 1.055*pow(c.b,1.0/2.4)-0.055);
}
float faceInk(float2 q) {
  float2 cell = floor((q - face.xy) / face.z + float2(6.0,5.0));
  if (cell.x < 0.0 || cell.x >= 12.0 || cell.y < 0.0 || cell.y >= 10.0) return 0.0;
  float rowBits = 0.0;
  for (int row=0; row<10; row++) { if (cell.y == float(row)) rowBits = faceRows[row]; }
  return mod(floor(rowBits / pow(2.0,cell.x)),2.0);
}
half4 main(float2 xy) {
  float2 local = (xy - centre) / scale;
  float2 restQ = local * pxPerPt + tileSize / 2.0;
  float2 q = restQ - press * travel * pxPerPt;
  float softTile = glassMode > 0.5 ? 3.0 : 6.0;
  float restA = channel(restQ, glassMode > 0.5 ? 2.0 : 5.0);
  float movedA = channel(q, glassMode > 0.5 ? 2.0 : 5.0);
  if (restA + movedA <= 0.0) return half4(0.0);
  float blend = progress;
  float leading = 0.0;
  if (radial > 0.5 && progress > 0.0 && progress < 1.0) {
    float reach = length(abs(origin) + float2(tileSize / pxPerPt / 2.0));
    float radius = progress * (reach + flood.x);
    float delta = length(local - press * travel - origin) - radius;
    blend = 1.0 - smoothstep(-flood.x/2.0, flood.x/2.0, delta);
    leading = (1.0 - smoothstep(0.0, flood.x, abs(delta))) * flood.y * (1.0-progress);
  }
  float3 tint = mix(albedoFrom, albedoTo, blend);
  float3 rgb;
  if (glassMode > 0.5) {
    half4 photo = picture.eval(clamp(q, float2(0.5), float2(tileSize-0.5)));
    rgb = decodeRGB(photo.rgb / max(float(photo.a),0.00001)) + channel(q,0.0)*upper + channel(q,1.0)*lower;
  } else {
    rgb = tint * (channel(q,0.0) + channel(q,2.0)*upper + channel(q,3.0)*tint*rimGain + channel(q,4.0)*tint*innerGain)
          + channel(q,1.0)*gloss + leading;
  }
  float clipped = travel > 0.0 ? restA : 1.0;
  float capA = movedA * clipped;
  float gap = travel > 0.0 ? restA * (1.0 - movedA) : 0.0;
  float2 towards = normalize(press) * well.w * pxPerPt;
  float edge = clamp((channel(restQ - towards, softTile) - channel(restQ, softTile)) * softGradient,0.0,1.0);
  rgb *= 1.0 - edge * well.z * max(0.0,travel);
  if (mini > 0.5) {
    float underInk = faceInk(local - press * travel - deboss.xy);
    rgb = mix(rgb, float3(1.0), underInk * deboss.z);
    rgb = mix(rgb, ink, faceInk(local - press * travel));
  }
  float3 interior = float3(well.x) + led * well.y * max(0.0,travel);
  float alpha = (capA + gap) * opacity;
  float3 premul = (rgb * capA + interior * gap) * opacity * dim;
  return half4(encodeRGB(premul / max(alpha, 0.00001)) * alpha, alpha);
}
"""

internal const val KEYCAP_SCENE_AGSL = """
uniform shader wellData;
uniform float u;
uniform float2 centres[4];
uniform float3 colors[4];
uniform float2 gains[4];
uniform float4 minis[6];
uniform float3 miniColors[6];
uniform float fanOrder[6];
uniform float3 scene;
uniform float burst, ring, selected, wellTile, pxPerPt, pageExposure, wellFeather;
uniform float4 page, ringLook;
uniform float2 top;
uniform float3 miniGlow;
float3 encodeRGB(float3 c) {
  c = max(c,float3(0.0));
  return float3(c.r <= 0.0031308 ? c.r*12.92 : 1.055*pow(c.r,1.0/2.4)-0.055,
                c.g <= 0.0031308 ? c.g*12.92 : 1.055*pow(c.g,1.0/2.4)-0.055,
                c.b <= 0.0031308 ? c.b*12.92 : 1.055*pow(c.b,1.0/2.4)-0.055);
}
half4 main(float2 xy) {
  if (xy.y < top.x) return half4(0.0,0.0,0.0,1.0);
  float d = length(xy-centres[3])/u;
  float f = d/page.x;
  float glow = f < page.w ? mix(page.y*burst,page.z,f/page.w) : mix(page.z,0.0,clamp((f-page.w)/(1.0-page.w),0.0,1.0));
  float3 rgb = scene * max(0.0,glow) * pageExposure;
  if (ring > 0.0) {
    float halo = 1.0 - smoothstep(0.0,ringLook.y/2.0,abs(d-ring*ringLook.x));
    rgb += scene * halo * ringLook.z * pow(max(0.0,1.0-ring),ringLook.w);
  }
  for (int i=0; i<4; i++) {
    float2 q = (xy-centres[i])/u*pxPerPt + wellTile/2.0;
    if (q.x >= 0.0 && q.y >= 0.0 && q.x < wellTile && q.y < wellTile) {
      float border = min(min(q.x,q.y),min(wellTile-q.x,wellTile-q.y));
      float feather = smoothstep(0.0,wellFeather*pxPerPt,border);
      q = clamp(q,float2(0.5),float2(wellTile-0.5));
      rgb += feather * colors[i] * (wellData.eval(q).r*gains[i].x + wellData.eval(q+float2(wellTile,0.0)).r*gains[i].y);
    }
  }
  for (int i=0; i<6; i++) {
    float md = length(xy-minis[i].xy)/max(0.001,minis[i].w*miniGlow.x);
    float mg = exp(-md*md*4.0) * minis[i].z * (selected == fanOrder[i] ? miniGlow.z : miniGlow.y);
    rgb += miniColors[i] * mg;
  }
  return half4(encodeRGB(rgb * smoothstep(top.x,top.x+top.y,xy.y)),1.0);
}
"""
