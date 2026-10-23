package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

// Shader equations and uniform names preserved from the React Native source.
internal const val BACK_AGSL = """
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

uniform float uUnder;
uniform float uSeam;
uniform float uRise;
uniform float uFlash;
uniform float4 uFlecks[12];
half4 main(float2 p) {
  float k = uUnit / 100.0;
  float stageScale = uUnit / 108.0;
  float2 uv = p / uSize;
  if (uv.y > 0.91) {
    return half4(float3(0.00784,0.01176,0.04314)+(hash21(p*2.37)-0.5)/255.0,1);
  }
  float floorY = uOrigin.y - 0.10*uUnit
    + 9.0*k*sq((p.x-uOrigin.x)/max(1.0,uOrigin.x))
    + k*(2.0*sin(p.x/k*0.104)+1.0*sin(p.x/k*0.047));
  float isFloor = smoothstep(floorY-15.0*k,floorY+21.0*k,p.y);
  float2 aim = poolCentre();
  float b = clamp(uBeam,0.0,1.0);
  float2 wallAim = aim-float2(0,1.50*uUnit*b);
  float wallPool = bell((p-wallAim)/float2((100.0+65.0*b)*k,(120.0+110.0*b)*k));
  float floorPool = bell((p-aim-float2(0,17.0*k))/float2(200.0*stageScale,50.0*stageScale));
  // Reuse the cloth sample for the floor reflection, stretching its lower folds into soft smears.
  float2 clothP = float2(p.x,mix(p.y,floorY-(p.y-floorY)*0.35,isFloor));
  float3 cloth = velvet(clothP,0.0);
  float room = 0.08 + 0.20*uLamp + 0.02*(1.0-clamp(uLamp,0.0,1.0));
  float flashBloom = 1.0 + 0.35*max(0.0,uFlash);
  float3 wall = cloth*(room+uLamp*0.52*wallPool*flashBloom);
  float reflectionFade = exp(-max(0.0,p.y-floorY)/(26.0*k));
  float3 floorCol = float3(0.009,0.012,0.039)
    + uLamp*floorPool*float3(0.23,0.25,0.43)
    + cloth*uLamp*reflectionFade*0.18;
  float floorCore = bell((p-uOrigin-float2(0,15.0*k))/float2(100.0*k,24.0*k));
  floorCol += float3(0.23,0.27,0.31)*floorCore*uLamp*b;
  // A broad, vertically streaked reflection of the lit marble and crystal. Reuse cloth's
  // low-frequency flank response: no extra material or noise evaluation on the floor.
  float below = (p.y-uOrigin.y)/stageScale;
  float reflected = exp(-sq((p.x-uOrigin.x)/(102.0*stageScale)))
    * smoothstep(12.0,30.0,below) * exp(-max(0.0,below-30.0)/34.0)
    * (1.0-smoothstep(80.0,108.0,below));
  float streaks = 0.72+0.28*clamp(cloth.b,0.0,1.0);
  floorCol += float3(0.20,0.25,0.31)*reflected*streaks
    * (uLamp*clamp(uRise,0.0,1.1)+max(0.0,uUnder));
  floorCol *= 0.99+0.02*hash21(floor(p*1.7));
  float contact = exp(-sq((p.y-floorY)/(6.5*k)));
  float3 col = mix(wall,floorCol,isFloor)*(1.0-contact*0.12);
  float haze = shaft(p);
  float smoke = 0.84+0.16*noise2(p/float2(74.0*k,180.0*k)
    + 0.35*float2(sin(uTime*0.41887902),cos(uTime*0.41887902)));
  // Both atmospheric terms follow the moving aim and respond on the lamp's causal frame.
  col += float3(0.60,0.58,0.95)*wallPool*uLamp*0.40*flashBloom*(1.0-isFloor*0.85);
  col += float3(0.63,0.69,1.0)*haze*smoke*uLamp*0.22;
  float seedGlow = bell((p - uOrigin + float2(0,20.0*k)) / float2(78.0*k,90.0*k));
  col += float3(0.10,0.19,0.44) * seedGlow * uSeam*0.14;
  // The slot is a temporary light source, spreading upwards into a soft 300-point fan.
  if (uUnder > 0.0) {
    float upward = max(0.0,(uOrigin.y-p.y)/stageScale);
    float fanWidth = 22.0+88.0*clamp(upward/300.0,0.0,1.0);
    float fanAcross = (p.x-uOrigin.x)/(fanWidth*stageScale);
    float rays = 0.88+0.12*sq(cos(fanAcross*9.0+upward*0.004));
    float fan = exp(-fanAcross*fanAcross*1.6)*rays
      * (1.0-smoothstep(220.0,310.0,upward))*(1.0-isFloor);
    float slotPool = bell((p-uOrigin-float2(0,16.0*stageScale))
      /float2(122.0*stageScale,32.0*stageScale));
    col += float3(0.72,0.91,1.0)*uUnder
      * (0.30*fan*(0.65+0.35*exp(-upward/100.0))+0.34*slotPool);
  }
  float3 flecks = float3(0);
  for (int i = 0; i < 12; i++) {
    float4 f = uFlecks[i];
    if (f.w > 0.002 && uLamp * uRise > 0.001) {
      float fi = float(i);
      float2 delta = p-f.xy;
      float ca = cos(f.z); float sa = sin(f.z);
      float2 local = float2(ca*delta.x+sa*delta.y,-sa*delta.x+ca*delta.y);
      float wide = (7.0 + mod(fi*7.0,11.0))*stageScale;
      float thin = (1.5 + mod(fi*1.7,2.1))*stageScale;
      float along = local.x / wide;
      if (abs(along) < 1.0 && abs(local.y) < thin*3.0) {
        // Wall patches bend with the broad folds; the middle is nearly achromatic.
        float warp = i < 8 ? (0.32*along*along+0.16*along*sin(f.x/k*0.104)) : 0.12*along;
        float across = local.y / thin-warp;
        float envelope = sq(max(0.0,1.0-along*along));
        float softness = 1.15+mod(fi,3.0)*0.25;
        float core = exp(-across*across*softness);
        float redEdge = exp(-sq(across-0.95)*5.5);
        float blueEdge = exp(-sq(across+0.95)*5.5);
        float3 patch = float3(0.91,0.96,1.0)*core
          + float3(0.28,0.065,0.025)*redEdge + float3(0.12,0.06,0.34)*blueEdge;
        flecks += patch * envelope * f.w * 0.74;
      }
    }
  }
  col += flecks * uLamp * clamp(uRise,0.0,1.1) * (1.0+uFlash)
    * mix(0.62+cloth.b,0.82,isFloor);
  float vignette = 0.36 + 0.64 * exp(-sq((uv.x-0.5)*2.2));
  vignette *= 0.67 + 0.33 * smoothstep(0.0,0.28,uv.y);
  col *= vignette;
  // Reserve the text zone even during slot light, lamp overshoot and tap flash.
  float copyCalm = smoothstep(54.0,76.0,below);
  float peak = max(0.0001,max(col.r,max(col.g,col.b)));
  col *= mix(1.0,min(1.0,0.12/peak),copyCalm);
  col = mix(col,float3(0.00784,0.01176,0.04314),smoothstep(0.76,0.91,uv.y));
  col += (hash21(p*2.37)-0.5)/255.0;
  return half4(max(col,float3(0)),1);
}
"""
