package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

// Shader equations and uniform names preserved from the React Native source.
internal const val DUST_AGSL = """
uniform float2 uSize;
uniform float2 uOrigin;
uniform float uUnit;
uniform float uTime;
uniform float uLamp;
uniform float uBeam;
float hash21(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float noise2(float2 p) {
  float2 a = floor(p);
  float2 b = fract(p);
  b = b * b * (3.0 - 2.0 * b);
  return mix(mix(hash21(a), hash21(a + float2(1,0)), b.x),
             mix(hash21(a + float2(0,1)), hash21(a + float2(1,1)), b.x), b.y);
}
float sq(float v) { return v*v; }
float bell(float2 p) { return exp(-dot(p,p)); }
float2 poolCentre() {
  float b = clamp(uBeam, 0.0, 1.0);
  return mix(float2(uSize.x * 0.24, uSize.y * 0.17), uOrigin, b)
    + float2(-0.08 * uSize.x * sin(b * 3.14159265), 0);
}
float2 lampPoint() { return float2(uOrigin.x + 0.32 * uUnit, -0.12 * uSize.y); }
float shaft(float2 p) {
  float2 aim = poolCentre();
  float2 lampPos = lampPoint();
  float travel = (p.y - lampPos.y) / max(1.0, aim.y - lampPos.y);
  float axis = mix(lampPos.x, aim.x, travel);
  float radius = (9.0 + 105.0 * max(0.0, travel)) * uUnit / 100.0;
  float across = (p.x - axis) / max(1.0, radius);
  return exp(-across * across * 2.8) * (1.0 - smoothstep(0.9, 1.2, travel));
}
// A rounded height field, differentiated analytically: broad diffuse flanks and soft pile sheen.
// One nap noise lookup; the second, incommensurate pleat never becomes a row of specular lines.
float3 velvet(float2 p, float front) {
  float k = uUnit / 100.0;
  float yy = p.y / k;
  float flare = 1.0 + 0.065 * smoothstep(280.0,650.0,yy);
  float sway = 7.0*sin(yy*0.0042) + 2.0*sin(yy*0.0091);
  float xx = (p.x/k + sway) / flare;
  float freq = mix(0.104,0.165,front);
  float phase = xx*freq + 0.65*sin(xx*0.012) + 0.24*sin(xx*0.024+0.8);
  float phaseDu = (freq + 0.0078*cos(xx*0.012) + 0.00576*cos(xx*0.024+0.8))/flare;
  float heightField = cos(phase) - 0.13*cos(2.0*phase) + 0.20*cos(2.3*phase+0.7);
  float slope = mix(14.0,11.0,front)*phaseDu
    * (-sin(phase)+0.26*sin(2.0*phase)-0.46*sin(2.3*phase+0.7));
  float3 normal = float3(-slope,0.12,1.0)/sqrt(slope*slope+1.0144);
  float diffuse = pow(max(0.0,0.5+0.5*dot(normal,float3(0.38,0.57,0.73))),1.4);
  float cavity = smoothstep(-1.20,0.48,heightField);
  float sheen = pow(max(0.0,1.0-normal.z),2.5);
  float3 body = mix(float3(0.043,0.067,0.314),float3(0.106,0.184,0.784),cavity);
  float3 col = body*(0.32+0.68*diffuse);
  col += float3(0.14,0.20,0.27)*diffuse*cavity;
  col += float3(0.68,0.75,1.0)*sheen*(0.20+0.40*cavity);
  float nap = noise2(float2(xx*1.8,yy*0.65));
  return col*(0.982+nap*0.036);
}

half4 main(float2 p) {
  if (uLamp <= 0.001) return half4(0);
  float2 aim = poolCentre();
  float2 lampPos = lampPoint();
  float progress = (p.y-lampPos.y)/max(1.0,aim.y-lampPos.y);
  float radius = (9.0+105.0*max(0.0,progress))*uUnit/100.0;
  float axis = mix(lampPos.x,aim.x,progress);
  float2 field = float2((p.x-axis)/max(1.0,radius)*0.5+0.5,progress);
  if (field.x < 0.0 || field.x > 1.0 || field.y < 0.0 || field.y > 1.0) return half4(0);
  float2 cell = floor(field*float2(8,7));
  float seed = hash21(cell+31.0);
  float seedB = hash21(cell+94.0);
  float phase = uTime * 0.41887902 + seed*6.2831853;
  float2 offset = float2(0.5+0.24*sin(phase),0.5+0.29*cos(phase+seedB*6.2831853));
  float2 centreUV = (cell+offset)/float2(8,7);
  float yPoint = mix(lampPos.y,aim.y,centreUV.y);
  float particleRadius = (9.0+105.0*centreUV.y)*uUnit/100.0;
  float xPoint = mix(lampPos.x,aim.x,centreUV.y)+(centreUV.x-0.5)*2.0*particleRadius;
  float moteSize = (0.6+1.6*seed*seed)*uUnit/100.0;
  float glow = bell((p-float2(xPoint,yPoint))/max(0.4,moteSize));
  float twinkle = 0.25+0.75*sq(0.5+0.5*sin(uTime*0.83775804+seedB*19.0));
  float a = min(0.62,glow*twinkle*uLamp*shaft(p)*0.59);
  return half4(float3(0.76,0.85,1.0)*a,a);
}
"""
