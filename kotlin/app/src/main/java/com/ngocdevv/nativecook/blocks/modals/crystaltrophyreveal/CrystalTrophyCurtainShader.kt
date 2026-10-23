package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

// Shader equations and uniform names preserved from the React Native source.
internal const val CURTAIN_AGSL = """
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

uniform float uOpen;
uniform float uHem;
uniform float uShiver;
uniform float uSeam;
half4 main(float2 p) {
  float k = uUnit/100.0;
  float side = p.x < uSize.x*0.5 ? -1.0 : 1.0;
  float fromSide = min(p.x,uSize.x-p.x);
  float yRatio = clamp(p.y / max(1.0,uSize.y-26.0*k),0.0,1.0);
  float bend = yRatio*yRatio*(3.0-2.0*yRatio);
  float railWidth = uSize.x*(0.5-0.35*uOpen);
  float hemWidth = uSize.x*(0.5-0.385*uHem);
  float edge = mix(railWidth,hemWidth,bend);
  float rippleY = uShiver*(uSize.y+180.0*k)-90.0*k;
  float band = exp(-sq((p.y-rippleY)/(60.0*k))) * sin(uShiver*3.14159265);
  float ripple = sin((p.y-rippleY)/(22.0*k)) * band * 4.0*k;
  edge += ripple * (1.0-clamp(uOpen,0.0,1.0));
  float gap = fromSide-edge;
  if (gap > 32.0*k) return half4(0);
  float gather = max(0.20,edge/(uSize.x*0.5));
  // Gather reduces the visible fold count: about 5 per closed half, 2–3 per open wing.
  float foldCompression = 0.42+0.58*gather;
  float clothX = fromSide / foldCompression;
  // Separate phase offsets avoid a mirrored fabric pattern across the opening.
  float2 clothP = float2(clothX+side*57.0*k-ripple,p.y);
  float hemY = uSize.y-26.0*k + (3.8*sin(clothX/k*0.165)+1.7*sin(clothX/k*0.071))*k*sqrt(gather)
    - 2.0*k*band;
  float coverage = (1.0-smoothstep(-0.65*k,0.65*k,gap))
    * (1.0-smoothstep(-0.65*k,0.65*k,p.y-hemY));
  float3 cloth = velvet(clothP,1.0);
  float footlight = 0.14+0.31*pow(yRatio,1.35);
  float gathered = clamp(uOpen,0.0,1.0);
  float edgeRoll = exp(-sq((gap+8.0*k)/(10.0*k)));
  float innerShadow = 1.0-0.30*exp(-sq((gap+22.0*k)/(10.0*k)));
  float3 col = cloth*footlight*innerShadow*(1.0-0.28*gathered);
  col += float3(0.025,0.038,0.09)*edgeRoll*(0.25+uLamp*0.65);
  float seamNear = exp(-sq((p.x-uSize.x*0.5)/(26.0*k)));
  float closed = 1.0-smoothstep(0.0,0.09,max(uOpen,uHem));
  col += float3(0.16,0.25,0.52)*seamNear*uSeam*closed*(0.4+cloth.b);
  // Backlight wraps the rounded leading fold over ten points; no luminous outline.
  float rim = exp(-sq((gap+4.0*k)/(10.0*k)));
  col += float3(0.10,0.15,0.32)*rim*uLamp*clamp(uOpen*3.0,0.0,1.0)
    * (0.35+0.65*shaft(p));
  float blade = exp(-sq((p.x-uSize.x*0.5)/(0.48+0.58*uSeam)));
  col += float3(0.66,0.81,1.0)*blade*uSeam*closed;
  col *= 0.68+0.32*exp(-sq((p.x/uSize.x-0.5)*1.5));
  col += (hash21(p*2.37)-0.5)/255.0;
  float underHem = exp(-sq((p.y-hemY-1.5*k)/(3.0*k)))
    * uSeam*(1.0-clamp(uOpen,0.0,1.0))*0.26;
  float forward = max(0.0,p.y-hemY);
  float rays = exp(-sq((p.x-uSize.x*0.5)/(3.0*k+forward*0.8)))
    * smoothstep(0.0,4.0*k,forward) * (1.0-smoothstep(10.0*k,32.0*k,forward))
    * uSeam*uSeam*closed*0.22;
  underHem += rays;
  float shadow = exp(-sq((p.y-hemY-4.0*k)/(7.0*k)))*0.60
    * (1.0-smoothstep(0.0,5.0*k,gap));
  float spill = exp(-max(0.0,gap)/(6.0*k))*uLamp*0.028
    * (1.0-smoothstep(hemY,hemY+9.0*k,p.y));
  float a = coverage+(1.0-coverage)*max(shadow,max(underHem,spill));
  float3 rgb = max(col,float3(0))*coverage + (1.0-coverage)
    * (float3(0.25,0.39,0.75)*underHem + float3(0.21,0.31,0.65)*spill);
  return half4(min(rgb,float3(a)),a);
}
"""
