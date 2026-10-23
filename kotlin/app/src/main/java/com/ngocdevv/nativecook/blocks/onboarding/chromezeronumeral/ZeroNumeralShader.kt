package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

// Original SkSL equations, unchanged for Android RuntimeShader.
internal const val ZERO_NUMERAL_SHADER = """
uniform shader uEnv;
uniform float2 uCentre;
uniform float uUnit;
uniform float uPixelRatio;
uniform float uCamera;
uniform float4 uTurn; // cos(yaw), sin(yaw), cos(pitch), sin(pitch)
uniform float3 uDock;
uniform float uScale;
uniform float uBuild;
uniform float uDrain;
uniform float uFuse;
uniform float uRipple;
uniform float2 uRippleOrigin;
uniform float uSweep;
uniform float uPercent;
uniform float uFlash;

const float CELL = 0.125;
const float BEVEL = 0.058;
const float PI = 3.14159265359;

float sq(float v) { return v * v; }
float3 toObject(float3 v) {
  float3 q = float3(v.x, v.y*uTurn.z + v.z*uTurn.w, -v.y*uTurn.w + v.z*uTurn.z);
  return float3(q.x*uTurn.x - q.z*uTurn.y, q.y, q.x*uTurn.y + q.z*uTurn.x);
}
float3 toWorld(float3 v) {
  float3 q = float3(v.x*uTurn.x + v.z*uTurn.y, v.y, -v.x*uTurn.y + v.z*uTurn.x);
  return float3(q.x, q.y*uTurn.z - q.z*uTurn.w, q.y*uTurn.w + q.z*uTurn.z);
}
float roundedRect(float2 p, float2 ext, float radius) {
  float2 q = abs(p) - ext + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float profile(float2 p) {
  float outer = roundedRect(p, float2(0.64, 1.0), 0.56);
  float counter = roundedRect(p, float2(0.23, 0.60), 0.23);
  return max(outer, -counter);
}
// Analytic profile gradient keeps pillow curvature outside the four field taps of the normal.
float2 profileGradient(float2 p) {
  float outer = roundedRect(p,float2(0.64,1.0),0.56);
  float counter = roundedRect(p,float2(0.23,0.60),0.23);
  float2 q = outer > -counter ? max(abs(p)-float2(0.08,0.44),0.0)
    : max(abs(p)-float2(0.0,0.37),0.0);
  return q/max(length(q),0.00001)*sign(p)*(outer > -counter ? 1.0 : -1.0);
}
float extrude(float profileD, float z, float depth, float bevel) {
  float2 q = float2(profileD + bevel, abs(z) - depth + bevel);
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - bevel;
}
float solidZero(float3 p) { return extrude(profile(p.xy), p.z, 0.26, BEVEL); }
float hashColumn(float2 col) {
  float3 h = fract(col.xyx*0.1031);
  h += dot(h,h.yzx+33.33);
  return fract((h.x+h.y)*h.z);
}
float breath() { return 1.0 + 0.10 * (1.0-uFuse); }
float columnDrop(float2 col) {
  return uDrain>0.0 ? max(0.0, uDrain - 0.22 * hashColumn(col + 17.0)) * 9.0 : 0.0;
}
// A row starts at y=-1; arrival times increase with row, so the mould fills from its foot.
float arrival(float row, float2 col) { return 0.10 + 0.08*hashColumn(col) + row*0.040; }
// Polynomial smooth union has round liquid necks, never cell-shaped facets.
float liquidUnion(float a, float b, float softness) {
  float h = max(softness-abs(a-b),0.0)/softness;
  return min(a,b)-h*h*softness*0.25;
}
float beadShape(float3 delta, float stretch) {
  // A tapered round capsule: no box distance, including during the falling phase.
  float taper = 1.0-0.16*smoothstep(-stretch,stretch+0.0001,delta.y)
    *smoothstep(0.0,0.03,stretch);
  delta.y = sign(delta.y)*max(abs(delta.y)-stretch,0.0);
  return length(delta)-0.47*CELL*taper;
}
float2 beadColumn(float3 q) {
  // Four layers at z = ±.0625, ±.1875; the centre plane lies between layers.
  return clamp(floor(float2(q.x,q.z)/CELL+float2(0.5,0.0)),
    float2(-5.0,-2.0),float2(5.0,1.0));
}
float oneBead(float3 p, float2 col, float row, float moving) {
  float3 home = float3(col.x*CELL, -1.0+row*CELL, (col.y+0.5)*CELL);
  if (profile(home.xy) > CELL*0.25 || row < 0.0 || row > 16.0) { return 0.24; }
  float until = max(arrival(row, col)-uBuild, 0.0);
  // A capsule contracts continuously into its sphere over the final .15 model units.
  float age = max(uBuild-arrival(row,col),0.0);
  float settle = (1.0-moving)*0.010*sin(age*115.0)*exp(-age*60.0);
  float rise = moving*until*36.0+settle;
  float streak = 0.0705*smoothstep(0.0, 0.15, rise);
  float3 delta = p-home-float3(0.0, rise, 0.0);
  return beadShape(delta, streak);
}
// Exact occupancy test for a bead centre, without square roots in the fusion kernel.
float beadMask(float2 home) {
  float2 outerQ = max(abs(home)-float2(0.08,0.44),0.0);
  float2 innerQ = max(abs(home)-float2(0.0,0.37),0.0);
  return dot(outerQ,outerQ)<=sq(0.56+CELL*0.25)
    && dot(innerQ,innerQ)>=sq(0.23-CELL*0.25) ? 1.0 : 0.0;
}
float beads(float3 p) {
  if (uBuild <= 0.0 || uDrain >= 1.0) { return 10.0; }
  float expansion = breath();
  float3 q = p/expansion;
  float2 col = beadColumn(q);
  // The half-second exit keeps only the two camera-side depth layers.
  if (uDrain>0.0) { col.y=clamp(col.y,uTurn.x>=0.0 ? 0.0 : -2.0,uTurn.x>=0.0 ? 1.0 : -1.0); }
  q.y += columnDrop(col);
  if (uBuild >= 1.0) {
    float3 home = float3(col.x*CELL,clamp(floor(q.y/CELL+0.5),-8.0,8.0)*CELL,(col.y+0.5)*CELL);
    float d;
    // The core grows inside constant-radius balls; easing reveals liquid necks during hold.
    // Sinking the centres lets the round bumps submerge before the exact solid endpoint.
    float3 beadDelta = q-home;
    beadDelta.z += sign(home.z)*0.105*uFuse;
    d = profile(home.xy)>CELL*0.25 ? 0.24 : beadShape(beadDelta,0.0);
    if (uFuse<=0.0 || uDrain>0.0) { return d*expansion; }
    float core = extrude(profile(q.xy),q.z,0.26,mix(0.045,BEVEL,smoothstep(0.65,1.0,uFuse)))+0.30*(1.0-sqrt(uFuse));
    // Sum neighbouring Gaussian sphere fields analytically on each lattice axis.
    // The repeated field and its gradient agree at cell boundaries: no tiled creases.
    float softness = mix(1200.0,420.0,smoothstep(0.0,0.5,uFuse));
    float3 dx = q.x-home.x+float3(CELL,0.0,-CELL);
    float3 dy = q.y-home.y+float3(CELL,0.0,-CELL);
    float3 gx = exp(-dx*dx*softness);
    float3 gy = exp(-dy*dy*softness);
    // Only the nine actual neighbouring centres contribute. This also makes the
    // field continuous where a valid centre meets an empty counter/perimeter cell.
    float gxy = gx.x*(gy.x*beadMask(home.xy+float2(-CELL,-CELL))
      +gy.y*beadMask(home.xy+float2(-CELL,0.0))+gy.z*beadMask(home.xy+float2(-CELL,CELL)))
      +gx.y*(gy.x*beadMask(home.xy+float2(0.0,-CELL))
      +gy.y*beadMask(home.xy)+gy.z*beadMask(home.xy+float2(0.0,CELL)))
      +gx.z*(gy.x*beadMask(home.xy+float2(CELL,-CELL))
      +gy.y*beadMask(home.xy+float2(CELL,0.0))+gy.z*beadMask(home.xy+float2(CELL,CELL)));
    float outerZ = 0.1875-0.105*uFuse;
    float innerZ = 0.0625-0.045*uFuse;
    float gz = exp(-sq(q.z-outerZ)*softness)+exp(-sq(q.z+outerZ)*softness)
      +exp(-sq(q.z-innerZ)*softness)+exp(-sq(q.z+innerZ)*softness);
    float sphereD = sqrt(max(-log(max(gxy*gz,0.00000001))/softness,0.0))-0.47*CELL;
    float fluidBeads = mix(d,sphereD,smoothstep(0.0,0.15,uFuse));
    float merged = liquidUnion(fluidBeads,core,0.07);
    float mould = extrude(profile(q.xy),q.z,0.26,mix(0.045,BEVEL,smoothstep(0.65,1.0,uFuse)))-(1.0-uFuse)*0.10;
    float edgeSoftness = max(0.0001,0.035*sin(PI*uFuse));
    return -liquidUnion(-merged,-mould,edgeSoftness)*expansion;
  }
  float latest = min(16.0, floor((uBuild-arrival(0.0,col))/0.040));
  float row = clamp(floor((q.y+1.0)/CELL+0.5), 0.0, max(0.0,latest));
  float landed = latest >= 0.0 ? oneBead(q,col,row,0.0) : 10.0;
  float falling = 10.0;
  if (uBuild < 1.0 && latest < 16.0) {
    // Invert the stretched lattice analytically: no loop over hundreds of individual beads.
    float spacing = CELL+36.0*0.040;
    float movingRow = floor((q.y+1.0-36.0*(arrival(0.0,col)-uBuild))/spacing+0.5);
    movingRow = clamp(movingRow, max(0.0,latest+1.0),16.0);
    falling = oneBead(q,col,movingRow,1.0);
  }
  float d = min(landed,falling);
  return d*expansion;
}
float3 percentCentre() {
  // It starts behind the right stroke, where the bowl cannot show it.
  return mix(float3(0.44,0.0,-0.45),uDock,uPercent)
    + float3(0.15*sin(PI*uPercent),0.10*sin(PI*uPercent),0.0);
}
float3 percentLocal(float3 p) {
  float ang = -1.5*PI*(1.0-uPercent);
  float3 q = (p-percentCentre())/mix(0.55,1.0,uPercent);
  return float3(q.x*cos(ang)-q.z*sin(ang),q.y,q.x*sin(ang)+q.z*cos(ang));
}
float percentField(float3 p) {
  if (uPercent <= 0.0) { return 10.0; }
  float3 q = percentLocal(p);
  // Ring centres and radius keep the complete glyph at the contract's .62-unit height.
  float a = abs(length(q.xy-float2(-0.13,0.16))-0.1075)-0.0425;
  float b = abs(length(q.xy-float2(0.13,-0.16))-0.1075)-0.0425;
  float2 bar = float2(q.x*0.819152-q.y*0.573576,q.x*0.573576+q.y*0.819152);
  float c = roundedRect(bar,float2(0.0425,0.31),0.012);
  return extrude(min(min(a,b),c),q.z,0.07,0.014)*mix(0.55,1.0,uPercent);
}
float scene(float3 p, bool poured) {
  return min(poured ? beads(p) : solidZero(p),percentField(p));
}
// A slab test in model space rejects the empty canvas before touching the distance field.
float2 boxInterval(float3 ro, float3 rd, float3 lo, float3 hi) {
  float3 safeDir = float3(rd.x>=0.0 ? max(rd.x,0.000001) : min(rd.x,-0.000001),
    rd.y>=0.0 ? max(rd.y,0.000001) : min(rd.y,-0.000001),
    rd.z>=0.0 ? max(rd.z,0.000001) : min(rd.z,-0.000001));
  float3 a = (lo-ro)/safeDir;
  float3 b = (hi-ro)/safeDir;
  float3 nearT = min(a,b);
  float3 farT = max(a,b);
  return float2(max(nearT.x,max(nearT.y,nearT.z)),min(farT.x,min(farT.y,farT.z)));
}
float2 sphereInterval(float3 ro, float3 rd, float3 at, float radius) {
  float3 q = ro-at;
  float b = dot(q,rd);
  float det = b*b-dot(q,q)+radius*radius;
  if (det < 0.0) { return float2(1.0,-1.0); }
  return float2(-b-sqrt(det),-b+sqrt(det));
}
float3 surfaceNormal(float3 p, bool poured) {
  const float e = 0.0007;
  const float2 k = float2(1.0,-1.0);
  return normalize(k.xyy*scene(p+k.xyy*e,poured)+k.yyx*scene(p+k.yyx*e,poured)
    +k.yxy*scene(p+k.yxy*e,poured)+k.xxx*scene(p+k.xxx*e,poured));
}
float3 environment(float3 rayDir, float solidStudio) {
  float2 uv = float2(fract(atan(rayDir.x,rayDir.z)/(2.0*PI)+0.5),acos(clamp(rayDir.y,-1.0,1.0))/PI);
  // Both studio tiles contain wrap gutters and prefiltered colour. Endpoints take one tap.
  float2 at = float2(1.5+uv.x*1021.0,clamp(uv.y*512.0,0.5,511.5));
  if (solidStudio>=1.0) { return float3(uEnv.eval(at).rgb); }
  if (solidStudio<=0.0) { return float3(uEnv.eval(at+float2(1024.0,0.0)).rgb); }
  return mix(float3(uEnv.eval(at+float2(1024.0,0.0)).rgb),
    float3(uEnv.eval(at).rgb),solidStudio);
}
float lamp(float3 reflected, float3 axis, float exponent) {
  return pow(max(dot(reflected,normalize(axis)),0.0),exponent);
}
// A dim opposite softbox and horizon card, evaluated only on the turning solid face.
// Keeping this fill separate from the atlas leaves REST and the bearing studio untouched.
float3 turnStudio(float3 rayDir) {
  float longitude = atan(rayDir.x,rayDir.z)/(2.0*PI)+0.5;
  float offset = fract(longitude-0.895+0.5)-0.5;
  float height = rayDir.y+0.35*offset;
  float softbox = exp(-sq(offset/0.36))*exp(-sq((height-0.10)/0.16));
  float horizon = exp(-sq((rayDir.y+0.08)/0.15));
  return float3(0.40,0.41,0.43)*softbox+float3(0.10,0.11,0.13)*horizon;
}

half4 main(float2 xy) {
  if (uScale <= 0.001) { return half4(0.0); }
  bool poured = uFuse < 1.0 || uBuild < 1.0 || uDrain > 0.0;
  float2 screen = (xy-uCentre)/uUnit;
  float3 ray = normalize(float3(screen.x,-screen.y,-uCamera));
  float3 ro = toObject(float3(0.0,0.0,uCamera))/uScale;
  float3 rd = toObject(ray);
  float expandBy = poured ? breath() : 1.0;
  float top = uBuild < 1.0 ? 7.5 : 1.10;
  float bottom = -1.10;
  if (uDrain > 0.0) {
    // Every column has fallen at least 9*(drain - 0.22) and at most 9*drain: march only that band.
    top = 1.10 - 9.0*max(0.0,uDrain-0.22);
    bottom = -1.10 - 9.0*uDrain;
  }
  // Match the drain box to its two remaining depth layers, not the old four-layer slab.
  float backZ = uDrain>0.0 ? (uTurn.x>=0.0 ? 0.003 : -0.247) : -0.34;
  float frontZ = uDrain>0.0 ? (uTurn.x>=0.0 ? 0.247 : -0.003) : 0.34;
  float2 zeroRange = boxInterval(ro,rd,float3(-0.74,bottom,backZ)*expandBy,float3(0.74,top,frontZ)*expandBy);
  if (uBuild <= 0.0 || uDrain >= 1.0) { zeroRange = float2(1.0,-1.0); }
  float2 pctRange = uPercent > 0.0 ? sphereInterval(ro,rd,percentCentre(),0.46*mix(0.55,1.0,uPercent)) : float2(1.0,-1.0);
  bool zeroPossible = zeroRange.y >= max(zeroRange.x,0.0);
  bool pctPossible = pctRange.y >= max(pctRange.x,0.0);
  if (!zeroPossible && !pctPossible) { return half4(0.0); }
  float nearT = zeroPossible ? zeroRange.x : pctRange.x;
  float farT = zeroPossible ? zeroRange.y : pctRange.y;
  if (pctPossible) { nearT=min(nearT,pctRange.x); farT=max(farT,pctRange.y); }
  float t = max(0.0,nearT);
  float closest = 10.0;
  float bestT = t;
  float pixel = 1.0/(uUnit*uScale*uPixelRatio);
  float threshold = pixel*(uDrain>0.0 ? 0.65 : 0.16);
  bool hit = false;
  if (poured) {
    for (int i=0;i<72;i++) {
      // Keep the original budget for rays that can hit the docked percent.
      if (uDrain>0.0 && !pctPossible && i>=28) { break; }
      float3 p = ro+rd*t;
      float d = scene(p,true);
      if (d<closest) { closest=d; bestT=t; }
      if (d<threshold) { hit=true; break; }
      // Column state changes at cell boundaries. Do not jump across a neighbouring column.
      float2 grid = p.xz/expandBy/CELL-float2(0.0,0.5);
      float2 cellEdge = (0.5-fract(grid+0.5))*CELL*expandBy;
      float2 boundary = (cellEdge+sign(rd.xz)*0.5*CELL*expandBy)/
        (rd.xz+float2(0.0000001));
      float crossing = max(0.0008,min(boundary.x,boundary.y)+0.0005);
      t += min(max(d*0.84,threshold*0.6),crossing);
      if (t>farT) { break; }
    }
  } else {
    for (int i=0;i<48;i++) {
      float d = scene(ro+rd*t,false);
      if (d<closest) { closest=d; bestT=t; }
      if (d<threshold) { hit=true; break; }
      t += max(d*0.9,threshold*0.6);
      if (t>farT) { break; }
    }
  }
  float alpha = hit ? 1.0 : 1.0-smoothstep(threshold,pixel*1.1,closest);
  if (alpha<=0.0) { return half4(0.0); }
  float3 p = ro+rd*bestT;
  bool isPercent = percentField(p) < (poured ? beads(p) : solidZero(p));
  float3 n;
  if (uDrain>0.0 && !isPercent) {
    // Exact sphere normal avoids four scene evaluations for every falling bearing.
    float3 q=p/expandBy;
    float2 col=beadColumn(q);
    col.y=clamp(col.y,uTurn.x>=0.0 ? 0.0 : -2.0,uTurn.x>=0.0 ? 1.0 : -1.0);
    q.y+=columnDrop(col);
    float3 home=float3(col.x*CELL,clamp(floor(q.y/CELL+0.5),-8.0,8.0)*CELL,(col.y+0.5)*CELL);
    n=normalize(q-home+float3(0.0,0.0,sign(home.z)*0.105*uFuse));
  } else { n=surfaceNormal(p,poured); }
  float facing = smoothstep(0.82,0.995,abs(n.z));
  float wave = 0.0;
  if (!isPercent && (!poured || uFuse>0.65)) {
    // Very shallow pillow curvature. Outer and inner profile gradients both bend the reflection.
    float edgeD = profile(p.xy);
    float2 grad = profileGradient(p.xy);
    float rimBend = 0.13*sq(1.0-smoothstep(0.0,0.15,-edgeD));
    n = normalize(n+float3(grad*rimBend+float2(p.x*0.035,p.y*0.045),0.0)*facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0));
  }
  if (uDrain<=0.0 && !isPercent && uRipple>0.0 && uRipple<1.0) {
    float2 radial = p.xy-uRippleOrigin;
    float radius = length(radial);
    float ringD = radius-uRipple*2.5;
    // A compact packet contains at most two broad crests, .35 model units apart.
    float packet = 1.0-smoothstep(0.26,0.35,abs(ringD));
    float envelope = exp(-sq(ringD/0.22))*packet*sin(PI*uRipple)/(1.0+radius*0.85);
    wave = envelope*cos(ringD*(2.0*PI/0.35));
    n = normalize(n+float3(radial/max(radius,0.001),0.0)*wave*0.10);
  }
  if (isPercent) {
    float3 pct = percentLocal(p);
    float2 ringA = pct.xy-float2(-0.13,0.16);
    float2 ringB = pct.xy-float2(0.13,-0.16);
    float da = abs(length(ringA)-0.1075);
    float db = abs(length(ringB)-0.1075);
    float barX = pct.x*0.819152-pct.y*0.573576;
    float2 curve = float2(0.819152,-0.573576)*barX*9.0;
    if (min(da,db)<abs(barX)) {
      float2 ringVec = da<db ? ringA : ringB;
      float ringR = length(ringVec);
      curve = ringVec/max(ringR,0.001)*(ringR-0.1075)*9.0;
    }
    float ang = -1.5*PI*(1.0-uPercent);
    float3 bend = float3(curve.x*cos(ang),curve.y,-curve.x*sin(ang));
    n = normalize(n+bend*facing);
  }
  float3 nw = toWorld(n);
  float3 reflection = reflect(ray,nw);
  float nv = clamp(dot(nw,-ray),0.0,1.0);
  float silverBevel = smoothstep(0.03,0.40,abs(n.z));
  float3 f0 = mix(float3(0.07,0.15,0.95),float3(0.95,0.96,0.98),silverBevel);
  if (poured) {
    float3 bearingF0 = mix(float3(0.76,0.83,1.0),float3(0.95,0.96,0.98),smoothstep(-0.85,-0.25,n.y));
    f0 = mix(bearingF0,f0,smoothstep(0.65,1.0,uFuse));
  }
  if (isPercent) { f0=float3(0.24,0.27,0.38); }
  float3 fresnel = f0+(float3(1.0)-f0)*pow(1.0-nv,5.0);
  // The face's studio follows 87.5% of its reflected yaw; the remaining
  // motion slides the flag across the face without losing it during idle or early drag.
  float3 lookup = reflection;
  float faceWeight = isPercent ? 0.0 : facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0);
  float turnDelta = atan(uTurn.y,uTurn.x)+0.33;
  float follow = -1.75*sin(turnDelta)*faceWeight;
  lookup = float3(reflection.x*cos(follow)+reflection.z*sin(follow),reflection.y,
    -reflection.x*sin(follow)+reflection.z*cos(follow));
  float3 env = environment(lookup,isPercent || !poured ? 1.0 : smoothstep(0.65,1.0,uFuse));
  // Idle stays in the original flagged key; beyond .45 radians the dim turn studio is complete.
  float turnFill = smoothstep(0.04,0.10,1.0-cos(turnDelta))*faceWeight;
  if (turnFill>0.0) { env += turnStudio(lookup)*turnFill; }
  float ao = 1.0;
  if (!isPercent && !poured) {
    float counterWall = (1.0-facing)*(1.0-smoothstep(0.26,0.40,abs(p.x)));
    ao -= counterWall*0.24;
  }
  if (poured && !isPercent) { ao=0.88+0.12*smoothstep(0.0,0.65,nv); }
  float3 colour = env*fresnel*ao*1.32;
  // Anodised cobalt has a body colour even when the wall reflects the black studio flag.
  // The mask excludes planar silver, and preserves every accepted loose/liquid state.
  float solidFinish = poured ? smoothstep(0.65,1.0,uFuse) : 1.0;
  float wallMask = isPercent ? 0.0 : (1.0-silverBevel)*solidFinish;
  if (wallMask>0.0) {
    float wallSheen = 0.5+0.5*dot(nw,normalize(float3(-0.3,0.65,0.7)));
    colour += mix(float3(0.055,0.105,0.65),float3(0.095,0.17,1.10),wallSheen)*wallMask;
    float wallStrip = exp(-sq((reflection.y+0.04-reflection.x*0.12)/0.045));
    colour += float3(0.30,0.42,0.95)*wallStrip*wallMask;
  }
  float hot = uDrain>0.0 ? 0.0 : lamp(reflection,float3(-0.68,0.55,0.48),180.0)*2.6
    + lamp(reflection,float3(0.74,0.28,-0.46),260.0)*3.0
    + lamp(reflection,float3(-0.2,-0.65,0.72),320.0)*1.8;
  colour += fresnel*hot;
  // Polished chamfers pick up a broad white overhead card, with moving hot lamps.
  float bevelFinish = poured && !isPercent ? smoothstep(0.65,1.0,uFuse) : 1.0;
  float chamfer = smoothstep(0.08,0.30,abs(n.z))*
    (1.0-smoothstep(mix(0.88,0.96,bevelFinish),mix(0.99,0.999,bevelFinish),abs(n.z)));
  float rimLight = pow(1.0-nv,4.0);
  float edgeLight = isPercent ? 0.42 : (poured ? mix(0.10,0.95,smoothstep(0.65,1.0,uFuse)) : 0.95);
  float3 oldEdge = float3(0.78,0.86,1.0)*(chamfer*edgeLight+rimLight*(isPercent ? 0.15 : 0.10));
  if (isPercent || solidFinish>0.0) {
    float cardLight = 0.22+0.30*lamp(reflection,float3(-0.3,0.7,0.5),3.0);
    float3 polishedEdge = mix(float3(0.97,0.98,1.0),float3(0.80,0.87,1.0),rimLight*0.25)
      *(chamfer*cardLight+rimLight*0.045);
    colour += mix(oldEdge,polishedEdge,isPercent ? 1.0 : solidFinish*(1.0-facing));
  } else { colour += oldEdge; }

  float band = exp(-sq((p.x-uSweep*1.15+0.1*p.y)/0.075))
    * (1.0-smoothstep(0.80,1.0,abs(uSweep)));
  // On the loose balls the band catches each crown in turn: a glitter running across the numeral.
  float crown = max(n.z,0.0);
  crown = crown*crown; crown = crown*crown; crown = crown*crown;
  float catchLight = (poured && uFuse<0.65) ? 1.5*crown : facing*0.60;
  colour += float3(0.85,0.91,1.0)*band*catchLight;
  colour += float3(0.40,0.52,1.0)*wave*0.18;
  if (poured && uBuild<1.0 && !isPercent) {
    float3 homeP = p/breath();
    float2 col = beadColumn(homeP);
    float row = floor((homeP.y+1.0)/CELL+0.5);
    float age = uBuild-arrival(row,col);
    float landedFlash = age>=0.0 ? exp(-age*90.0) : 0.0;
    colour += float3(0.42,0.46,0.55)*landedFlash;
  }
  // One bounded highlight lift, including bloom: never brighten the dark studio panel.
  float specularLift = smoothstep(0.65,1.10,max(colour.r,max(colour.g,colour.b)));
  float flashMask = clamp(max(chamfer,max(rimLight,specularLift)),0.0,1.0);
  colour *= 1.0+0.30*clamp(uFlash,0.0,1.0)*flashMask;
  // Soft shoulder preserves deep blacks; source pictures are already in display colour space.
  colour = colour/(1.0+colour*0.18);
  // Roll off only the solid silver face. Retain the accepted ball, wall and bevel exposure.
  float faceShoulder = isPercent ? 0.0 : facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0);
  float3 excess = max(colour-0.85,0.0);
  float3 rolled = min(colour,0.85)+excess/(1.0+excess/0.15);
  colour = mix(colour,rolled,faceShoulder);
  // Away from the key, silver stays below 60% display brightness; the bevel keeps its glints.
  colour = mix(colour,min(colour,float3(0.60)),turnFill);
  float grain = fract(sin(dot(xy,float2(12.9898,78.233)))*43758.5453)-0.5;
  colour = clamp(colour+grain/510.0,0.0,1.0);
  return half4(half3(colour*alpha),half(alpha));
}
"""
