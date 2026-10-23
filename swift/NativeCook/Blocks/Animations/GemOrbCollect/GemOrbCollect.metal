#include <metal_stdlib>
using namespace metal;

struct GemOrbCollectVertexOutput {
    float4 position [[position]];
};

struct GemOrbCollectUniforms {
    float2 size;
    float displayScale;
    float time;
    float4 gem;
    float4 attitude;
    float4 effects;
    float4 capsule;
    float4 button;
    float4 mini;
    float4 state;
    float4 ping;
    float4 row0;
    float4 row1;
    float4 row2;
    float4 miniRow0;
    float4 miniRow1;
    float4 miniRow2;
    float4 optics;
    float4 backdrop;
    float4 pillBar;
    float4 pillCapsule;
    float4 coloursTop;
    float4 coloursMid;
    float4 coloursBloom;
    float4 coloursDust;
};

vertex GemOrbCollectVertexOutput gemOrbCollectVertex(uint vertexID [[vertex_id]]) {
    const float2 positions[4] = {
        float2(-1.0, -1.0), float2(1.0, -1.0),
        float2(-1.0, 1.0), float2(1.0, 1.0)
    };
    GemOrbCollectVertexOutput output;
    output.position = float4(positions[vertexID], 0.0, 1.0);
    return output;
}

struct GemOrbCollectBackdropShader {
    constant GemOrbCollectUniforms* u;

const float TAU = 6.28318530718;
float squareB(float v) { return v*v; }
float hashB(float2 p) {
  float3 q = fract(float3(p.x,p.y,p.x)*0.1031);
  q += dot(q,q.yzx+33.33);
  return fract((q.x+q.y)*q.z);
}
float3 spectrumB(float v) {
  float3 cool = mix(float3(0.14,0.94,1.0),float3(0.24,0.35,1.0),smoothstep(-0.85,-0.15,v));
  float3 warm = mix(float3(0.72,0.48,1.0),float3(1.0,0.27,0.65),smoothstep(0.15,0.85,v));
  return mix(cool,warm,smoothstep(-0.15,0.15,v));
}
float3 dustB(float2 p) {
  // Forty cells, each with one mote. The 36% inset keeps every glow inside its cell,
  // including drift/parallax, so there are no neighbour loops or clipped seams.
  float2 pageSize = max(u->size,float2(1.0));
  float2 cellSize = pageSize/float2(5.0,8.0);
  // Independently stagger each column; empty cell margins hide the coordinate seam.
  p.y = fmod(p.y+hashB(float2(floor(p.x/cellSize.x),52.0))*cellSize.y,pageSize.y);
  float2 cellId = floor(p/cellSize);
  if (cellId.x<0.0 || cellId.x>=5.0 || cellId.y<0.0 || cellId.y>=8.0) return float3(0.0);
  float seed = hashB(cellId+7.31);
  float seedY = hashB(cellId+29.7);
  float depth = fmod(cellId.x+cellId.y*5.0,3.0);
  float scaleB = min(pageSize.x/402.0,pageSize.y/874.0);
  float parallax = depth<0.5 ? 4.0 : (depth<1.5 ? 8.0 : 14.0);
  float2 home = (cellId+0.36+0.28*float2(seed,seedY))*cellSize;
  home += scaleB*(float2(sin(u->backdrop.y+seed*TAU),cos(u->backdrop.y*2.0+seedY*TAU))*2.4
    - float2(sin(u->gem.w),sin(u->attitude.x))*parallax);
  float2 delta = (p-home)/max(scaleB,0.01);
  float d2 = dot(delta,delta);
  // Exact finite support also makes the cell lookup safe under the shock's warp.
  if (d2>81.0) return float3(0.0);
  // Five of the thirteen near cells carry a larger defocused mote.
  float nearSoft = depth>1.5 && fmod(cellId.x+cellId.y*5.0,9.0)<3.0 ? 1.0 : 0.0;
  float core = 0.50+depth*0.36+nearSoft*1.15;
  float twinkle = 0.62+0.38*squareB(sin(u->backdrop.y*(1.0+depth)+seed*TAU));
  float glow = exp(-d2/(core*core))*(0.44-0.23*nearSoft)
    + exp(-d2/(core*core*11.0))*(0.075-0.025*nearSoft);
  float3 tint = mix(u->coloursDust.xyz,float3(0.30,0.45,1.0),seedY*0.40);
  return tint*glow*twinkle*(0.44+depth*0.24)*(1.0-smoothstep(36.0,81.0,d2));
}
float4 sample(float2 xy) {
  float r = max(u->backdrop.x,1.0);
  float k = r/104.0;
  float y = xy.y/max(u->size.y,1.0);
  float3 sky = mix(u->coloursTop.xyz,u->coloursMid.xyz,smoothstep(0.0,0.45,y));
  sky *= 1.0-smoothstep(0.45,0.62,y);
  sky *= 1.0-0.30*u->effects.z;
  float2 delta = xy-u->gem.xy;
  float radial = length(delta);
  float2 radialDir = delta/max(radial,0.001);
  float ringRadius = mix(0.9*r,1.1*length(u->size*0.5),1.0-squareB(1.0-u->effects.w));
  float ringOffset = radial-ringRadius;
  float ringLife = smoothstep(0.0,0.035,u->effects.w)*(1.0-smoothstep(0.45,0.90,u->effects.w));
  float warp = 0.0;
  if (ringLife>0.0) warp = ringLife*9.0*k*exp(-squareB(ringOffset/(12.0*k)));
  float2 warped = xy-radialDir*warp;
  float bloomDist = length(warped-u->gem.xy)/r;
  float bloomWidth = 1.10*(1.0-0.24*u->effects.z);
  float3 bloomTint = mix(u->coloursBloom.xyz,float3(0.35,0.12,0.86),u->effects.x*0.55);
  // At one radius the envelope is 18% before collection and 45% when charged.
  float bloom = (0.25+0.20*u->effects.x)*(0.55*exp(-squareB(bloomDist/bloomWidth)*0.65)+0.45*exp(-bloomDist*1.5))*(1.0-0.50*u->effects.z);
  sky += bloomTint*bloom*(1.0-smoothstep(0.48,0.64,y));
  float2 poolPoint = (warped-u->gem.xy-float2(0.0,1.75*r))/float2(1.2*r,0.25*r);
  float pool = exp(-dot(poolPoint,poolPoint)*2.0)*0.14*u->effects.x*(1.0-0.65*u->effects.z);
  sky += bloomTint*pool;
  float2 dustPoint = u->gem.xy+(warped-u->gem.xy)/max(1.0-0.14*u->effects.z,0.01);
  sky += dustB(dustPoint)*(1.0-0.28*u->effects.z);
  // A 14-point soft wake follows two separated, two-point spectral crests.
  if (ringLife>0.0 && abs(ringOffset)<65.0*k) {
    float ringWhite = exp(-squareB((ringOffset+1.0*k)/max(1.2*k,0.1)));
    float ringRed = exp(-squareB((ringOffset-2.5*k)/max(1.5*k,0.1)));
    float ringBlue = exp(-squareB((ringOffset+2.5*k)/max(1.5*k,0.1)));
    sky += ringLife*(1.0-0.70*u->effects.w)*(float3(0.68,0.75,1.0)*ringWhite*0.19
      + float3(1.0,0.24,0.52)*ringRed*0.19+float3(0.13,0.83,1.0)*ringBlue*0.23
      +float3(0.39,0.29,0.88)*exp(-squareB((ringOffset+6.0*k)/(8.4*k)))*0.15);
  }
  // End caustics above copy: contract does not expose titleTop; the design-size
  // title is 2.27 radii below the gem. Use the stricter of that and the 3.2r mask.
  float copyMask = 1.0-smoothstep(u->gem.xy.y+1.82*r,u->gem.xy.y+2.15*r,xy.y);
  float farMask = 1.0-smoothstep(u->gem.xy.y+3.2*r,u->gem.xy.y+3.4*r,xy.y);
  if (u->effects.x>0.001 && radial>1.30*r && radial<2.35*r && copyMask>0.0) {
    float3 caustic = float3(0.0);
    for (int i=0;i<6;i++) {
      float n = float(i);
      float a = n*2.399963+u->gem.w*(0.32+0.065*n)+u->attitude.x*sin(n*1.9)*0.65;
      float2 dir = float2(cos(a),sin(a));
      float orbit = (1.60+0.46*(0.5+0.5*sin(n*3.1+u->attitude.x*1.4+u->gem.w*0.4)))*r;
      float2 q = xy-u->gem.xy-dir*orbit;
      float along = dot(q,dir)/(k*(13.0+2.2*n));
      float across = dot(q,float2(-dir.y,dir.x))/(k*(5.0+0.8*n));
      float lens = along*along+across*across;
      float shapeB = exp(-lens*1.8)*(1.0-smoothstep(0.65,1.45,lens));
      float breathe = 0.72+0.28*sin(u->backdrop.y+n*1.8+u->gem.w*(0.6+n*0.09)+u->attitude.x*2.0);
      caustic += spectrumB(along*1.5)*shapeB*breathe;
    }
    sky += caustic*0.16*u->effects.x*copyMask*farMask;
  }
  if (u->state.x>0.001 && radial>0.94*r) {
    float3 beamLight = float3(0.0);
    float lowerMask = mix(1.0,0.12,smoothstep(u->gem.xy.y+2.15*r,u->gem.xy.y+2.70*r,xy.y));
    // Seven main shafts plus three faint wide shafts between them; ten fixed iterations.
    for (int j=0;j<10;j++) {
      float n = float(j);
      float wide = j>=7 ? 1.0 : 0.0;
      float a = u->gem.w+(j<7 ? n*TAU/7.0+0.10*sin(n*3.0) : (n-6.5)*TAU/3.0);
      float2 dir = float2(cos(a),sin(a));
      float along = dot(delta,dir);
      float across = dot(delta,float2(-dir.y,dir.x));
      // Compute the distance to the page edge along this shaft, guarding near-axis directions.
      float2 edge = mix(u->gem.xy,u->size-u->gem.xy,step(float2(0.0),dir))/max(abs(dir),float2(0.001));
      float edgeAlong = min(edge.x,edge.y);
      float spread = clamp((along-r)/max(edgeAlong-r,1.0),0.0,1.0);
      float widthB = mix(3.0*k,k*(16.0+fmod(n*7.1,20.0)),spread)*(1.0+2.2*wide);
      float side = across/max(widthB,0.1);
      if (along>0.95*r && abs(side)<2.5) {
        float beamProfile = exp(-side*side*1.3)*(1.0-smoothstep(1.8,2.5,abs(side)));
        float beamCore = exp(-side*side*4.5);
        float reach = length(u->size)*(0.40+0.065*fmod(n*1.9,4.0));
        float envelope = smoothstep(0.95*r,1.06*r,along)*exp(-max(along-r,0.0)/max(reach*0.48,1.0));
        float3 beamTint = mix(float3(0.16,0.90,1.0),float3(1.0,0.26,0.70),smoothstep(-0.8,0.8,side));
        beamLight += (beamTint*beamProfile*0.27+float3(0.95,0.89,1.0)*beamCore*0.28)
          *envelope*(0.66+0.34*squareB(cos(n*1.8)))*(1.0-0.94*wide);
      }
    }
    sky += beamLight*u->state.x*lowerMask;
  }
  sky += u->effects.y*(float3(0.40,0.26,0.66)*exp(-squareB(radial/(1.4*r)))*0.33
    +float3(0.07,0.045,0.12)*(1.0-smoothstep(0.30,0.72,y)));
  // Screen-locked triangular dither in physical pixels; no animated grain or lifted black floor.
  float2 pixel = floor(xy*u->displayScale);
  float grain = (hashB(pixel+0.17)-hashB(pixel+71.3))/255.0;
  float lit = smoothstep(0.0,0.025,max(sky.r,max(sky.g,sky.b)));
  return float4(clamp(sky+grain*lit,0.0,1.0),1.0);
}

};
struct GemOrbCollectGemShader {
    constant GemOrbCollectUniforms* u;
    constant float4* planes;
    constant float4* outline;
    constant float4* lights;
    float2 centre;
    float radius;
    float3 row0;
    float3 row1;
    float3 row2;
    float stretch;
    float flare;
    float outsideLights;
    float insideLights;
    float bounces;


float square(float v) { return v*v; }
float3 unitVec(float3 v) { return v / max(length(v), 0.00001); }
float3 toView(float3 v) { return float3(dot(row0,v),dot(row1,v),dot(row2,v)); }
float3 toLocal(float3 v) { return row0*v.x + row1*v.y + row2*v.z; }
float safeDenom(float v) { return v<0.0 ? min(v,-0.00001) : max(v,0.00001); }
float fresnel(float cosine, float ior) {
  float f = square((ior-1.0)/(ior+1.0));
  return f+(1.0-f)*pow(clamp(1.0-cosine,0.0,1.0),5.0);
}
float3 tint(int slot) {
  if (slot==0 || slot==3) return float3(0.48,0.91,1.0);
  if (slot==1 || slot==4) return float3(0.69,0.48,1.0);
  return float3(1.0,0.40,0.78);
}
// Directional rectangular softboxes have narrow shoulders; refraction separates their RGB edges.
float panel(float3 rayDir, float3 axis, float width, float height, float shoulder) {
  float3 forward = unitVec(axis);
  float3 right = unitVec(cross(float3(0.0,1.0,0.0),forward));
  float3 up = cross(forward,right);
  float facing = dot(rayDir,forward);
  float2 uv = float2(dot(rayDir,right),dot(rayDir,up))/max(facing,0.001);
  float falloff=clamp(0.76-uv.x*0.24+uv.y*0.18,0.25,1.0);
  return smoothstep(0.0,0.05,facing) * (1.0-smoothstep(width,width+shoulder,abs(uv.x)))
    * (1.0-smoothstep(height,height+shoulder,abs(uv.y)))*falloff;
}
float3 environment(float3 rayDir, float3 origin) {
  float3 rgb = float3(0.018,0.023,0.13)+float3(0.025,0.035,0.18)*(0.5+0.5*rayDir.y);
  // Finite studio panels give broad facets parallax and a smooth lighting gradient.
  float3 panelRay=unitVec(rayDir+origin*0.24);
  rgb += float3(1.25,1.35,1.9)*panel(panelRay,float3(-0.8,0.85,0.7),0.60,0.86,0.36);
  rgb += float3(2.1,0.62,2.2)*panel(panelRay,float3(0.95,0.16,0.25),0.44,0.84,0.48);
  rgb += float3(0.22,0.16,0.45)*panel(panelRay,float3(0.75,-0.25,0.55),0.50,0.60,0.30);
  float rimMask=smoothstep(0.48,0.96,length(origin.xy))*(0.68-0.28*clamp(origin.x,-1.0,1.0));
  rgb += float3(0.08,0.85,1.05)*rimMask
    *panel(panelRay,float3(-0.65,-0.45,-0.45),0.08,1.1,0.16);
  rgb += float3(0.10,0.18,1.05)*pow(max(dot(rayDir,unitVec(float3(0.1,0.1,-1.0))),0.0),3.0);
  // The atlas supplies pin glints; a broad internal panel keeps the optical return calm.
  rgb += float3(0.26,0.20,0.66)*square(max(rayDir.y,0.0));
  if (outsideLights>0.0) for (int i=0;i<6;i++) {
    float4 lamp = lights[i];
    if (lamp.w>0.0) {
      float3 delta = lamp.xyz-origin;
      rgb += tint(i)*lamp.w*3.0*pow(max(dot(rayDir,unitVec(delta)),0.0),180.0)
        /(1.0+0.12*dot(delta,delta));
    }
  }
  return rgb;
}
// Slab interval plus its front normal. Parallel rays are treated as actual misses outside a slab.
float2 interval(float3 origin, float3 rayDir, thread float3& frontNormal, thread float3& roundedNormal) {
  float nearT=-10000.0;
  float farT=10000.0;
  frontNormal=float3(0.0,0.0,1.0);
  float secondT=-10000.0;
  float3 secondNormal=frontNormal;
  float3 backNormal=-frontNormal;
  for (int i=0;i<30;i++) {
    float4 plane=planes[i];
    float denom=dot(plane.xyz,rayDir);
    float residual=plane.w-dot(plane.xyz,origin);
    float rayT=residual/safeDenom(denom);
    if (abs(denom)<0.00001) {
      if (residual<0.0) farT=-10000.0;
    } else if (denom<0.0) {
      if (rayT>nearT) { secondT=nearT; secondNormal=frontNormal; nearT=rayT; frontNormal=plane.xyz; }
      else if (rayT>secondT) { secondT=rayT; secondNormal=plane.xyz; }
    } else if (rayT<farT) { farT=rayT; backNormal=plane.xyz; }
  }
  // The runner-up slab supplies the adjacent facet without a second plane loop.
  float frontGap=max((nearT-secondT)*abs(dot(secondNormal,rayDir)),0.0);
  float backGap=max((farT-nearT)*abs(dot(backNormal,rayDir)),0.0);
  roundedNormal=unitVec(frontNormal+secondNormal*(1.0-smoothstep(0.0,0.022,frontGap))
    +backNormal*(1.0-smoothstep(0.0,0.022,backGap)));
  return float2(nearT,farT);
}
float nextFacet(float3 origin, float3 rayDir, thread float3& normal) {
  float rayT=10000.0;
  normal=float3(0.0,0.0,-1.0);
  for (int i=0;i<30;i++) {
    float4 plane=planes[i];
    float denom=dot(plane.xyz,rayDir);
    if (denom>0.00001) {
      float candidate=(plane.w-dot(plane.xyz,origin))/denom;
      if (candidate<rayT) { rayT=candidate; normal=plane.xyz; }
    }
  }
  return max(rayT,0.0001);
}
// Three intersecting astroid laminae form a volumetric jack: a four-point profile from all axes.
float3 starSlice(float3 origin, float3 rayDir, float span, float extent) {
  float rayT=-origin.z/safeDenom(rayDir.z);
  if (rayT<0.0 || rayT>span || abs(rayDir.z)<0.001) return float3(0.0);
  float2 q=(origin+rayDir*rayT).xy;
  float metric=pow(abs(q.x)/extent,0.70)+pow(abs(q.y)/extent,0.70);
  float core=1.0-smoothstep(0.78,1.12,metric);
  float halo=exp(-max(metric-0.72,0.0)*(4.8-1.6*u->effects.x))*(0.12+0.20*u->effects.x+0.38*flare)
    *(1.0+0.07*sin(u->time*1.5707963268));
  float pearl=clamp(0.48-q.x/extent*1.4+q.y/extent*0.85,0.0,1.0);
  float3 shell=mix(float3(1.55,0.40,0.98),float3(2.5,2.42,2.5),pearl);
  shell=mix(shell,float3(2.7),0.36*u->effects.x+0.58*flare);
  return shell*core+float3(1.5,0.20,0.8)*halo;
}
float3 insideLight(float3 origin, float3 rayDir, float span, float starScale, float starGain) {
  float extent=mix(0.46,0.52,u->effects.x)*(1.0+0.40*flare)*starScale;
  float3 shifted=origin-float3(0.0,0.0,-0.035);
  float3 weights=rayDir*rayDir;
  weights=weights*weights*weights;
  weights/=max(weights.x+weights.y+weights.z,0.00001);
  float3 glow=float3(0.0);
  // Nearly edge-on laminae contribute less than half a thousandth; skip their astroid powers.
  if (weights.z>0.0005) glow+=starSlice(shifted,rayDir,span,extent)*weights.z;
  if (weights.x>0.0005) glow+=starSlice(shifted.yzx,rayDir.yzx,span,extent)*weights.x;
  if (weights.y>0.0005) glow+=starSlice(shifted.zxy,rayDir.zxy,span,extent)*weights.y;
  float intensity=1.05+u->effects.x*0.55+flare*2.4;
  float3 rgb=glow*intensity*starGain;
  // Soft light held in the body grows instead of the hard astroid's white area.
  float nearCore=clamp(-dot(shifted,rayDir),0.0,span);
  float3 coreOffset=shifted+rayDir*nearCore;
  float scatter=exp(-dot(coreOffset,coreOffset)*5.5)*(1.0-exp(-span*2.0));
  rgb+=float3(0.10,0.055,0.28)*scatter*(0.12+u->effects.x*0.22+flare*0.30)*(1.0+0.07*sin(u->time*1.5707963268));
  if (insideLights>0.0) for (int i=0;i<6;i++) {
    float4 lamp=lights[i];
    if (lamp.w<0.0) {
      float3 delta=toLocal(lamp.xyz)-origin;
      float rayT=clamp(dot(delta,rayDir),0.0,span);
      float3 off=delta-rayDir*rayT;
      rgb+=tint(i)*(-lamp.w)*(exp(-dot(off,off)*100.0)*1.6+exp(-dot(off,off)*650.0)*3.0);
    }
  }
  return rgb;
}
float3 dispersedExit(float3 rayDir, float3 outward, float3 point, thread float3& retained) {
  float3 indices=float3(1.62,1.72,1.80)+float3(-0.09,0.0,0.14)*min(flare+0.40*u->effects.x,1.0);
  float cosine=clamp(dot(rayDir,outward),0.0,1.0);
  float3 redRay=refract(rayDir,-outward,indices.x);
  float3 greenRay=refract(rayDir,-outward,indices.y);
  float3 blueRay=refract(rayDir,-outward,indices.z);
  retained=float3(fresnel(cosine,indices.x),fresnel(cosine,indices.y),fresnel(cosine,indices.z));
  float3 rgb=float3(0.0);
  float3 viewPoint=toView(point);
  if (dot(redRay,redRay)>0.01) rgb.r=environment(toView(redRay),viewPoint).r; else retained.r=1.0;
  if (dot(greenRay,greenRay)>0.01) rgb.g=environment(toView(greenRay),viewPoint).g; else retained.g=1.0;
  if (dot(blueRay,blueRay)>0.01) rgb.b=environment(toView(blueRay),viewPoint).b; else retained.b=1.0;
  return rgb*(1.0-retained);
}
float3 traceGem(float3 origin, float3 rayDir, float rayT, float3 frontNormal, float3 normal, float edgeDistance) {
  float3 point=origin+rayDir*rayT;
  float3 viewPoint=toView(point);
  float facing=clamp(-dot(rayDir,normal),0.0,1.0);
  float frontF=fresnel(facing,1.72);
  float3 rgb=environment(toView(reflect(rayDir,normal)),toView(point))*(frontF+0.04);
  float3 viewNormal=toView(normal);
  float bevelAmount=smoothstep(0.0005,0.06,1.0-dot(normal,frontNormal));
  // Wide thin film follows the true outline, not a circular overlay. A bright line sits inside it.
  float edgeDepth=max(edgeDistance,0.0);
  float film=(0.22+0.78*smoothstep(0.0,0.018,edgeDepth))
    *(1.0-smoothstep(0.035,0.090,edgeDepth));
  float filmLine=exp(-square((edgeDepth-0.033)/0.009));
  float3 iridescence=mix(float3(0.055,0.92,0.65),float3(0.88,0.40,0.98),smoothstep(-0.45,0.65,viewPoint.x));
  float drift=0.78+0.22*cos(viewPoint.y*6.0+viewPoint.x*3.0+u->time*0.5235987756);

  rgb+=iridescence*bevelAmount*0.18;
  // A smaller key reflection leaves dark planes beside the glossy upper-left highlight.
  float key=pow(max(dot(viewNormal,unitVec(float3(-0.55,0.65,0.85))),0.0),18.0);
  rgb+=float3(1.45,1.50,1.80)*key*exp(-square(viewPoint.x+0.45)*28.0-square(viewPoint.y-0.65)*24.0);
  float side=smoothstep(0.02,0.50,viewNormal.x);
  float band=1.0-0.25*exp(-square(viewPoint.y+0.015)*65.0);
  // Deliberate blue depth under the star, violet on side planes; charge lifts colour, not white fog.
  rgb+=mix(float3(0.055,0.055,0.50),float3(0.68,0.44,0.94),side)
    *band*(0.90+0.10*viewPoint.y)*(1.0+0.26*u->effects.x);
  if (outsideLights>0.0) for (int i=0;i<6;i++) {
    float4 lamp=lights[i];
    if (lamp.w>0.0) {
      float3 lightDelta=lamp.xyz-toView(point);
      float3 lightDir=unitVec(lightDelta);
      // Finite orb radius keeps contact luminous even when its centre lies on the facet plane.
      float facingLight=max(dot(viewNormal,lightDir),dot(viewNormal,unitVec(lamp.xyz)));
      float wide=pow(max(facingLight,0.0),3.0)/(1.0+0.8*dot(lightDelta,lightDelta));
      float sharp=pow(max(dot(reflect(toView(rayDir),viewNormal),lightDir),0.0),90.0);
      float edgeFlash=bevelAmount*pow(max(facingLight,0.0),1.5)
        /(1.0+2.0*dot(lightDelta,lightDelta));
      rgb+=tint(i)*lamp.w*(wide*1.25+sharp*1.8+edgeFlash*1.3);
    }
  }
  float3 emission=float3(0.0);
  float3 throughput=float3(1.0-frontF);
  float3 internalRay=unitVec(refract(rayDir,frontNormal,1.0/1.72));
  point+=internalRay*0.001;
  // Crown/pavilion images stay smaller and dimmer than the table's direct star.
  float tableView=smoothstep(0.93,0.995,frontNormal.z);
  for (int bounce=0;bounce<5;bounce++) {
    float bounceWeight=clamp(bounces-float(bounce),0.0,1.0);
    if (bounceWeight<=0.0) break;
    float3 exitNormal;
    float span=nextFacet(point,internalRay,exitNormal);
    float3 absorption=exp(-float3(1.30,0.66,0.075)*span);
    float starScale=mix(0.60+0.24*flare,1.0,tableView)*(bounce==0 ? 1.0 : 0.82);
    float starGain=mix(0.30+0.35*flare,1.0,tableView)*(bounce==0 ? 1.0 : 0.30);
    emission+=throughput*mix(float3(1.0),sqrt(absorption),0.25)*insideLight(point,internalRay,span,starScale,starGain)*pow(0.38,float(bounce))*bounceWeight;
    float3 transmitted=throughput*absorption;
    point+=internalRay*span;
    float3 retained;
    rgb+=transmitted*dispersedExit(internalRay,exitNormal,point,retained)
      *pow(0.25,float(bounce))*(0.34+0.30*u->effects.x+1.10*flare+0.10*max(bounces-3.0,0.0))*bounceWeight;
    throughput=mix(throughput,transmitted*retained,bounceWeight);
    internalRay=reflect(internalRay,exitNormal);
    point+=internalRay*0.001;
  }
  rgb+=throughput*float3(0.025,0.025,0.20);
  // A low blue volumetric floor keeps TIR paths rich, without replacing the traced reflections.
  rgb+=float3(0.015+0.04*u->effects.x,0.018,0.09)*(1.0+u->effects.x*0.25);
  rgb*=float3(1.0+0.55*u->effects.x,1.0+0.30*u->effects.x,1.0+0.18*u->effects.x)
    *(1.0+0.025*sin(u->time*0.5235987756));
  // Luminance compression preserves tint; the shared peak shoulder keeps every channel in gamut.
  float luminance=dot(rgb,float3(0.2126,0.7152,0.0722));
  // Only very bright radiance approaches white; contact highlights keep at least 15% tint.
  float peakBefore=max(rgb.r,max(rgb.g,rgb.b));
  rgb=mix(rgb,float3(peakBefore),0.85*smoothstep(0.8,2.8,luminance));
  rgb/=1.0+luminance*0.22;
  float peak=max(rgb.r,max(rgb.g,rgb.b));
  float shoulder=0.72+0.27*(1.0-exp(-max(peak-0.72,0.0)/0.27));
  rgb*=peak>0.72 ? shoulder/max(peak,0.00001) : 1.0;
  // At grazing incidence the film replaces transmission; adding it to blue would erase its green.
  float3 filmColour=iridescence*drift*(0.95+0.20*u->effects.x)+float3(0.70,0.76,0.86)*filmLine*0.42;
  rgb=mix(rgb,min(filmColour,float3(0.98)),film*0.90);
  // Emission is exposed after the body's shoulder so neither charge nor flare is flattened.
  return 1.0-(1.0-rgb)*exp(-emission);
}
float4 flareRays(float2 screen) {
  float radial=length(screen);
  float reach=mix(1.0,2.2,flare);
  float envelope=pow(max(1.0-radial/reach,0.0),1.4)*smoothstep(0.22,0.50,radial);
  float width=0.006+0.009*flare;
  float mainRay=exp(-square(screen.x/width))+exp(-square(screen.y/width));
  float diagonal=exp(-square((screen.x-screen.y)/(width*0.8)))
    +exp(-square((screen.x+screen.y)/(width*0.8)));
  float streak=exp(-abs(screen.y)/0.035)*exp(-abs(screen.x)*0.8);
  float alpha=clamp(flare*envelope*(mainRay*0.9+diagonal*0.20+streak*0.20),0.0,0.9);
  float3 colour=0.74+0.26*cos(float3(0.0,2.0,4.0)+radial*8.0);
  return float4(colour*alpha,alpha);
}
float4 burstBloom(float2 screen) {
  float radial=length(screen);
  // Compact white-pink bloom, violet shoulders, zero well before the 2.25-unit Rect edge.
  float alpha=flare*0.90*exp(-radial*radial*1.28)*(1.0-smoothstep(1.25,1.88,radial));
  float3 colour=mix(float3(1.0,0.91,0.98),float3(0.36,0.12,0.95),smoothstep(0.25,1.45,radial));
  return float4(colour*alpha,alpha);
}
float4 sample(float2 xy) {
  if (u->optics.y<=0.0) return float4(0.0);
  float2 screen=(xy-centre)/max(radius,0.01);
  screen.y=-screen.y;
  float4 light=float4(0.0);
  if (flare>0.0) {
    float4 rays=flareRays(screen);
    float4 bloom=burstBloom(screen);
    light=rays+bloom*(1.0-rays.a);
  }
  float2 modelScreen=screen*float2(sqrt(stretch),1.0/stretch);
  // Leave room for a silhouette-following halo; the bloom and rays need no slab work.
  float bound=u->effects.x>0.0 ? 1.5 : 1.12;
  if (dot(modelScreen,modelScreen)>bound*bound) return float4(light*u->optics.y);
  float2 screenScale=float2(sqrt(stretch),1.0/stretch);
  float signedEdge=10.0;
  for (int i=0;i<32;i++) {
    float3 edge=outline[i].xyz;
    float inward=(edge.z-dot(edge.xy,modelScreen))/max(length(edge.xy*screenScale),0.00001);
    signedEdge=min(signedEdge,inward);
  }
  float pixel=1.0/max(radius*u->displayScale,1.0);
  float coverage=clamp(0.5+signedEdge/pixel,0.0,1.0);
  float outsideEdge=max(-signedEdge,0.0);
  float haloAlpha=0.36*u->effects.x*exp(-outsideEdge*10.0)
    *(1.0-smoothstep(1.20,1.50,length(modelScreen)));
  float3 haloColour=float3(0.30,0.20,0.95);
  float3 rgb=float3(0.0);
  if (coverage>0.0) {
    float3 origin=toLocal(float3(0.0,0.0,7.0));
    float3 rayDir=toLocal(unitVec(float3(modelScreen,-7.0)));
    float3 frontNormal, roundedNormal;
    float2 hit=interval(origin,rayDir,frontNormal,roundedNormal);
    rgb=traceGem(origin,rayDir,hit.x,frontNormal,roundedNormal,signedEdge);
  }
  float baseAlpha=coverage+haloAlpha*(1.0-coverage);
  float3 baseRGB=rgb*coverage+haloColour*haloAlpha*(1.0-coverage);
  float alpha=light.a+baseAlpha*(1.0-light.a);
  return float4(min(light.rgb+baseRGB*(1.0-light.a),float3(alpha))*u->optics.y,alpha*u->optics.y);
}

};
struct GemOrbCollectPillShader {
    constant GemOrbCollectUniforms* u;
    float2 centre;
    float2 dimensions;
    float tone;
    float press;
    float glow;
    float sheen;
    float controlOpacity;
    float scale;


const float PI = 3.14159265359;
float sqr(float v) { return v * v; }
float stadium(float2 q, float straight, float rad) {
  return length(float2(max(abs(q.x) - straight, 0.0), q.y)) - rad;
}
float3 studio(float3 n, float2 q, float depth, float facetU) {
  // Perspective varies substantially within a facet, despite its constant plane normal.
  float3 viewRay = float3(-q.x/max(dimensions.x,1.0)*1.65,-q.y/max(dimensions.y,1.0)*1.1,1.0);
  viewRay /= max(length(viewRay),0.001);
  float3 reflected = reflect(-viewRay,n);
  float upper = max(-n.y,0.0);
  float left = 1.0-smoothstep(-dimensions.x*0.48,dimensions.x*0.35,q.x);
  // A bounded softbox image in each plane. Its spectrum lives only at the card edge.
  float cardCoord = depth + reflected.x*0.26 + reflected.y*0.18 + facetU*0.30;
  float panel = smoothstep(0.10,0.31,cardCoord)*(1.0-smoothstep(0.57,0.84,cardCoord));
  float key = panel*(0.18+0.65*upper)*(0.28+0.72*left);
  float fireEdge = cardCoord-0.62;
  float3 fire = float3(exp(-sqr((fireEdge-0.028)/0.038)),
    exp(-sqr(fireEdge/0.038)),exp(-sqr((fireEdge+0.028)/0.038)));
  float pink = pow(max(dot(reflected,float3(0.83,-0.18,0.53)),0.0),8.0)
    *exp(-sqr((depth-0.60)/0.23)-sqr((facetU+0.10)/0.42));
  float cyan = pow(max(dot(reflected,float3(-0.72,0.55,0.42)),0.0),9.0)
    *exp(-sqr((depth-0.46)/0.21)-sqr((facetU-0.12)/0.38));
  // Small coloured cards occupy only part of their facing end facets.
  cyan += pow(clamp(dot(n.xy,float2(-0.81,0.59))/0.67,0.0,1.0),8.0)
    *exp(-sqr((depth-0.44)/0.17)-sqr((facetU-0.06)/0.26))*0.78;
  pink += pow(clamp(dot(n.xy,float2(1.0,-0.08))/0.67,0.0,1.0),8.0)
    *exp(-sqr((depth-0.57)/0.19)-sqr((facetU+0.10)/0.28))*0.72;
  float3 body = mix(float3(0.086,0.071,0.478),float3(0.26,0.18,0.69),upper);
  body *= 0.78+0.22*smoothstep(0.0,1.0,depth);
  return body + float3(0.74,0.77,1.0)*key + fire*key*0.22
    + float3(0.78,0.28,0.77)*pink*0.8 + float3(0.12,0.78,0.55)*cyan*0.85;
}
float4 sample(float2 xy) {
  float sc = max(scale,0.01);
  float2 q = (xy-centre)/sc;
  float rad = max(dimensions.y*0.5,1.0);
  float straight = max(dimensions.x*0.5-rad,0.0);
  float d = stadium(q,straight,rad);
  float aa = max(1.0 / u->displayScale/sc,0.16);
  float coverage = 1.0-smoothstep(-aa*0.5,aa*0.5,d);
  float capsule = clamp(tone,0.0,1.0);
  float pressed = clamp(press,0.0,1.0);
  float lit = clamp(glow,0.0,1.0);
  float bevel = mix(9.0,7.0,capsule)*min(dimensions.y/44.0,1.0);
  float2 tableQ = q-float2(1.6,1.8)*pressed;
  float td = stadium(tableQ,straight,rad-bevel);
  float tableMask = 1.0-smoothstep(-aa*0.5,aa*0.5,td);
  float2 radial = float2(max(abs(q.x)-straight,0.0),q.y);
  float a = atan2(radial.y,max(radial.x,0.0001));
  float sector = clamp((a+PI*0.5)/PI,0.0,0.99999)*5.0;
  float facetAngle = (floor(sector)+0.5)*PI/5.0-PI*0.5;
  float sectorEdge = (fract(sector)-0.5)*PI/5.0;
  float seamDistance = (abs(sectorEdge)-PI/10.0)*max(length(radial),1.0);
  float rounding = 1.0-smoothstep(0.0,aa*0.8+0.18,-seamDistance);
  facetAngle += sign(sectorEdge)*PI/5.0*rounding*0.5;
  float2 facet = float2(cos(facetAngle)*sign(q.x),sin(facetAngle));
  float endMask = smoothstep(-aa,aa,abs(q.x)-straight);
  facet = mix(float2(0.0,sign(q.y)),facet,endMask);
  float depth = clamp(-d/max(bevel,1.0),0.0,1.0);
  // One crown plane per facet: no secondary girdle ring.
  float3 normal = float3(facet*0.67,0.7424);
  normal.xy -= float2(0.16,0.22)*pressed;
  normal /= max(length(normal),0.001);
  float facetU = mix(q.x/max(dimensions.x,1.0),fract(sector)-0.5,endMask);
  float3 crown = studio(normal,q,depth,facetU);
  crown *= 0.97+0.03*cos(u->time*2.0*PI/12.0);
  // A narrow lower-left rim reflection slides under pressure, without flooding a facet.
  float rimSpot = exp(-sqr((q.x+dimensions.x*(0.28-0.07*pressed))/(dimensions.x*0.18)));
  float rim = exp(-sqr((depth-0.18-0.06*pressed)/0.09))*smoothstep(0.0,rad*0.5,q.y)*rimSpot;
  crown += float3(0.12,0.62,0.40)*rim*(0.58+0.12*pressed);
  // Refraction through the table: saturated vertical depth, broad low-contrast internal cards.
  float tableY = clamp((tableQ.y+rad-bevel)/max(2.0*(rad-bevel),1.0),0.0,1.0);
  float diagonal = q.x+1.45*q.y;
  float bands = exp(-sqr((diagonal+dimensions.x*0.25)/(dimensions.x*0.13)))
    + 0.65*exp(-sqr((diagonal-dimensions.x*0.28)/(dimensions.x*0.10)));
  float3 tableColour = mix(float3(0.3333,0.2745,0.9098),float3(0.1686,0.1216,0.6510),tableY);
  tableColour *= 1.0+bands*0.055;
  float tableKey = exp(-sqr((q.x+dimensions.x*0.23)/(dimensions.x*0.18))-sqr((tableY-0.16)/0.28));
  tableColour += float3(0.065,0.058,0.080)*tableKey;
  float innerShadow = exp(-sqr((td+0.85)/0.85))*smoothstep(0.0,rad*0.4,tableQ.y);
  tableColour *= (1.0-0.22*innerShadow)*(1.0-0.12*pressed);
  float centreLight = exp(-sqr(q.x/(dimensions.x*0.31))-sqr(q.y/(rad*0.95)));
  tableColour = mix(tableColour,float3(0.49,0.40,0.78),lit*centreLight*0.82);
  // True low-opacity indigo glass; the page remains visible through the capsule.
  float3 smokedTable = float3(0.3765,0.3608,0.8627)*(1.04-0.16*tableY+0.045*bands);
  smokedTable *= 1.0-0.12*pressed;
  smokedTable = mix(smokedTable,float3(0.57,0.45,0.87),lit*centreLight*0.6);
  tableColour = mix(tableColour,smokedTable,capsule);
  crown = mix(crown,float3(0.27,0.25,0.67)+crown*0.30,capsule);
  float3 colour = mix(crown,tableColour,tableMask);
  // The outline is a single 0.75 pt light. The table edge only has a selective reflection.
  float upperLeft = clamp(0.50-facet.y*0.39-facet.x*0.24,0.05,1.0);
  float outerLine = exp(-sqr((d+0.38)/(0.38+aa*0.15)));
  float tableLine = exp(-sqr(td/(0.20+aa*0.20)));
  float topKey = max(-facet.y,0.0)*(1.0-smoothstep(-dimensions.x*0.35,dimensions.x*0.42,q.x));
  colour += float3(0.67,0.69,0.93)*tableLine*topKey*0.21*(1.0-capsule*0.85);
  float3 edgeColour = mix(float3(0.55,0.23,0.72),float3(0.86,0.88,1.0),upperLeft);
  colour = mix(colour,edgeColour,outerLine*mix(0.12+0.76*upperLeft,0.92,capsule));
  float sheenOn = smoothstep(-0.10,0.0,sheen)*(1.0-smoothstep(1.0,1.10,sheen));
  float sweep = q.x+q.y*0.55-(sheen-0.5)*dimensions.x;
  float sheenBand = exp(-sqr(sweep/6.0))*sheenOn;
  float3 sheenFire = float3(exp(-sqr((sweep-1.0)/0.7)),exp(-sqr(sweep/0.7)),exp(-sqr((sweep+1.0)/0.7)))*sheenOn;
  colour += (float3(0.55,0.60,0.72)*sheenBand+sheenFire*0.35)
    *mix(0.62,0.035,tableMask)*(1.0-capsule*0.6);
  // This ceiling preserves saturated blue while protecting the white label in every state.
  colour = mix(colour,min(colour,float3(0.50,0.43,0.94)),tableMask*(1.0-capsule));
  float glassAlpha = 0.16+lit*centreLight*0.15;
  glassAlpha = mix(glassAlpha,0.055+0.50*upperLeft,outerLine);
  float solidAlpha = mix(0.98,glassAlpha,capsule);
  float haloWidth = mix(16.0,23.0,lit)*(1.0-pressed*0.38);
  float haloD = stadium(q-float2(0.0,4.0-pressed*2.0),straight,rad);
  float halo = exp(-sqr(max(haloD,0.0)/max(haloWidth,1.0)))
    *(1.0-smoothstep(haloWidth,haloWidth*1.55,max(haloD,0.0)));
  float haloAlpha = halo*mix(0.25,0.016,capsule)*(1.0+lit*0.3);
  float bodyAlpha = coverage*solidAlpha;
  float alpha = bodyAlpha+haloAlpha*(1.0-bodyAlpha);
  float3 premul = clamp(colour,0.0,1.0)*bodyAlpha
    +float3(0.22,0.085,0.62)*haloAlpha*(1.0-bodyAlpha);
  float opacity = clamp(controlOpacity,0.0,1.0);
  return float4(float3(premul*opacity),(alpha*opacity));
}

};

static float3 gemOrbCollectAtlasPiece(float2 xy, float2 centre, float size, float alpha,
    float tint, float stretch, float angle, int piece, texture2d<float> atlas) {
    if (alpha <= 0.0 || size <= 0.0) return float3(0.0);
    float scale = size / 32.0;
    float2 tile;
    float2 dimensions;
    float2 local;
    float pieceAlpha = alpha;
    if (piece == 0) {
        int focus = stretch > 1.15 ? 3 : 0;
        tile = float2(focus == 3 ? 384.0 : 0.0, 128.0 * tint);
        dimensions = float2(focus == 3 ? 256.0 : 128.0, 128.0);
        local = (xy - centre) / scale + float2(focus == 3 ? 210.0 : 64.0, 64.0);
    } else {
        if (stretch <= 1.15) return float3(0.0);
        float tail = scale * clamp(0.55 + 0.17 * stretch, 0.7, 1.3) * (piece == 1 ? 1.0 : 0.86);
        float2 direction = float2(cos(angle), sin(angle));
        float2 side = float2(-direction.y, direction.x);
        float2 head = centre - direction * (piece == 1 ? 0.0 : 105.0 * tail);
        float2 delta = xy - head;
        local = float2(dot(delta, direction), dot(delta, side)) / tail + float2(210.0, 64.0);
        tile = float2(384.0, 128.0 * tint);
        dimensions = float2(256.0, 128.0);
        pieceAlpha *= piece == 1 ? 0.85 * min(1.0, (stretch - 1.0) / 1.2)
            : 0.6 * clamp((stretch - 2.2) / 2.0, 0.0, 1.0);
    }
    if (any(local < float2(0.0)) || any(local >= dimensions)) return float3(0.0);
    constexpr sampler atlasSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    return atlas.sample(atlasSampler, (tile + local) / float2(768.0, 512.0)).rgb * pieceAlpha;
}

static float3 gemOrbCollectSprites(float2 xy, constant float4 *orbs, texture2d<float> atlas, bool front) {
    float3 result = float3(0.0);
    for (int index = 0; index < 72; index++) {
        float4 position = orbs[index * 2];
        float4 appearance = orbs[index * 2 + 1];
        if ((position.z >= 0.0) != front || appearance.x <= 0.001) continue;
        float alpha = appearance.x * (position.z < 0.0 ? 0.66 : 1.0);
        int focus = position.z > 1.6 ? 2 : (position.z < 0.0 || abs(position.z) >= 0.6 ? 1 : 0);
        float scale = position.w / 32.0;
        float2 local = (xy - position.xy) / scale + float2(64.0);
        if (all(local >= float2(0.0)) && all(local < float2(128.0))) {
            constexpr sampler atlasSampler(coord::normalized, address::clamp_to_edge, filter::linear);
            float2 tile = float2(128.0 * min(focus, 2), 128.0 * appearance.y);
            result += atlas.sample(atlasSampler, (tile + local) / float2(768.0, 512.0)).rgb * alpha;
        }
        if (appearance.z > 1.15) {
            result += gemOrbCollectAtlasPiece(xy, position.xy, position.w, alpha,
                appearance.y, appearance.z, appearance.w, 1, atlas);
            result += gemOrbCollectAtlasPiece(xy, position.xy, position.w, alpha,
                appearance.y, appearance.z, appearance.w, 2, atlas);
        }
    }
    return result;
}

static float3 gemOrbCollectGlints(float2 xy, constant float4 *glints, texture2d<float> atlas) {
    float3 result = float3(0.0);
    constexpr sampler atlasSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    for (int index = 0; index < 8; index++) {
        float4 glint = glints[index];
        if (glint.z < 0.005 || glint.w <= 0.0) continue;
        float2 local = (xy - glint.xy) / glint.w + float2(64.0);
        if (any(local < float2(0.0)) || any(local >= float2(128.0))) continue;
        result += atlas.sample(atlasSampler, (float2(0.0, 384.0) + local) / float2(768.0, 512.0)).rgb * glint.z;
    }
    return result;
}

static float3 gemOrbCollectComposite(float3 under, float4 over) {
    return over.rgb + under * (1.0 - over.a);
}

fragment half4 gemOrbCollectFragment(
    GemOrbCollectVertexOutput input [[stage_in]],
    constant GemOrbCollectUniforms &uniforms [[buffer(0)]],
    constant float4 *orbs [[buffer(1)]],
    constant float4 *planes [[buffer(2)]],
    constant float4 *outline [[buffer(3)]],
    constant float4 *lights [[buffer(4)]],
    constant float4 *glints [[buffer(5)]],
    constant float4 *miniOutline [[buffer(6)]],
    texture2d<float> atlas [[texture(0)]]) {
    float2 xy = input.position.xy / max(uniforms.displayScale, 1.0);
    GemOrbCollectBackdropShader backdrop{&uniforms};
    float3 colour = backdrop.sample(xy).rgb;
    colour += gemOrbCollectSprites(xy, orbs, atlas, false);
    GemOrbCollectGemShader gem{&uniforms, planes, outline, lights, uniforms.gem.xy, uniforms.gem.z,
        uniforms.row0.xyz, uniforms.row1.xyz, uniforms.row2.xyz, uniforms.optics.x,
        uniforms.effects.y, uniforms.optics.w, uniforms.backdrop.z, uniforms.optics.z};
    float4 stone = gem.sample(xy);
    colour = gemOrbCollectComposite(colour, stone);
    colour += gemOrbCollectGlints(xy, glints, atlas);
    colour += gemOrbCollectSprites(xy, orbs, atlas, true);
    GemOrbCollectPillShader capsule{&uniforms, uniforms.capsule.xy + uniforms.capsule.zw * 0.5,
        uniforms.capsule.zw, 1.0, uniforms.pillCapsule.x, uniforms.pillCapsule.y,
        uniforms.pillCapsule.z, uniforms.pillCapsule.w, uniforms.mini.w};
    colour = gemOrbCollectComposite(colour, capsule.sample(xy));
    GemOrbCollectGemShader miniGem{&uniforms, planes, miniOutline, lights, uniforms.mini.xy, uniforms.mini.z,
        uniforms.miniRow0.xyz, uniforms.miniRow1.xyz, uniforms.miniRow2.xyz,
        1.0, uniforms.backdrop.w, 0.0, 0.0, 2.0};
    colour = gemOrbCollectComposite(colour, miniGem.sample(xy));
    GemOrbCollectPillShader button{&uniforms, uniforms.button.xy + uniforms.button.zw * 0.5,
        uniforms.button.zw, 0.0, uniforms.pillBar.x, uniforms.pillBar.y,
        uniforms.pillBar.z, uniforms.pillBar.w, uniforms.state.z};
    colour = gemOrbCollectComposite(colour, button.sample(xy));
    return half4(half3(clamp(colour, 0.0, 1.0)), 1.0h);
}
