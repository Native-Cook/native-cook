// Convex-plane ray tracing: adaptive internal reflections, RGB exit dispersion and a luminous core.
import { LIGHTS } from './gem-contract';
import { GEM_OUTLINE_PLANES, GEM_PLANES } from './gem-cut';

export const GEM_SKSL = `
uniform float2 uCentre;
uniform float uRadius;
uniform float uPixelRatio;
uniform float uCamera;
uniform float3 uRow0;
uniform float3 uRow1;
uniform float3 uRow2;
uniform float uStretch;
uniform float uCharge;
uniform float uFlare;
uniform float4 uLights[${LIGHTS}];
uniform float uTime;
uniform float uOpacity;
uniform float uBounces;
uniform float uOutsideLights;
uniform float uInsideLights;
uniform float4 uPlanes[${GEM_PLANES.length / 4}];
uniform float3 uOutline[${GEM_OUTLINE_PLANES}];

float square(float v) { return v*v; }
float3 unitVec(float3 v) { return v / max(length(v), 0.00001); }
float3 toView(float3 v) { return float3(dot(uRow0,v),dot(uRow1,v),dot(uRow2,v)); }
float3 toLocal(float3 v) { return uRow0*v.x + uRow1*v.y + uRow2*v.z; }
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
  if (uOutsideLights>0.0) for (int i=0;i<${LIGHTS};i++) {
    float4 lamp = uLights[i];
    if (lamp.w>0.0) {
      float3 delta = lamp.xyz-origin;
      rgb += tint(i)*lamp.w*3.0*pow(max(dot(rayDir,unitVec(delta)),0.0),180.0)
        /(1.0+0.12*dot(delta,delta));
    }
  }
  return rgb;
}
// Slab interval plus its front normal. Parallel rays are treated as actual misses outside a slab.
float2 interval(float3 origin, float3 rayDir, out float3 frontNormal, out float3 roundedNormal) {
  float nearT=-10000.0;
  float farT=10000.0;
  frontNormal=float3(0.0,0.0,1.0);
  float secondT=-10000.0;
  float3 secondNormal=frontNormal;
  float3 backNormal=-frontNormal;
  for (int i=0;i<${GEM_PLANES.length / 4};i++) {
    float4 plane=uPlanes[i];
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
float nextFacet(float3 origin, float3 rayDir, out float3 normal) {
  float rayT=10000.0;
  normal=float3(0.0,0.0,-1.0);
  for (int i=0;i<${GEM_PLANES.length / 4};i++) {
    float4 plane=uPlanes[i];
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
  float halo=exp(-max(metric-0.72,0.0)*(4.8-1.6*uCharge))*(0.12+0.20*uCharge+0.38*uFlare)
    *(1.0+0.07*sin(uTime*1.5707963268));
  float pearl=clamp(0.48-q.x/extent*1.4+q.y/extent*0.85,0.0,1.0);
  float3 shell=mix(float3(1.55,0.40,0.98),float3(2.5,2.42,2.5),pearl);
  shell=mix(shell,float3(2.7),0.36*uCharge+0.58*uFlare);
  return shell*core+float3(1.5,0.20,0.8)*halo;
}
float3 insideLight(float3 origin, float3 rayDir, float span, float starScale, float starGain) {
  float extent=mix(0.46,0.52,uCharge)*(1.0+0.40*uFlare)*starScale;
  float3 shifted=origin-float3(0.0,0.0,-0.035);
  float3 weights=rayDir*rayDir;
  weights=weights*weights*weights;
  weights/=max(weights.x+weights.y+weights.z,0.00001);
  float3 glow=float3(0.0);
  // Nearly edge-on laminae contribute less than half a thousandth; skip their astroid powers.
  if (weights.z>0.0005) glow+=starSlice(shifted,rayDir,span,extent)*weights.z;
  if (weights.x>0.0005) glow+=starSlice(shifted.yzx,rayDir.yzx,span,extent)*weights.x;
  if (weights.y>0.0005) glow+=starSlice(shifted.zxy,rayDir.zxy,span,extent)*weights.y;
  float intensity=1.05+uCharge*0.55+uFlare*2.4;
  float3 rgb=glow*intensity*starGain;
  // Soft light held in the body grows instead of the hard astroid's white area.
  float nearCore=clamp(-dot(shifted,rayDir),0.0,span);
  float3 coreOffset=shifted+rayDir*nearCore;
  float scatter=exp(-dot(coreOffset,coreOffset)*5.5)*(1.0-exp(-span*2.0));
  rgb+=float3(0.10,0.055,0.28)*scatter*(0.12+uCharge*0.22+uFlare*0.30)*(1.0+0.07*sin(uTime*1.5707963268));
  if (uInsideLights>0.0) for (int i=0;i<${LIGHTS};i++) {
    float4 lamp=uLights[i];
    if (lamp.w<0.0) {
      float3 delta=toLocal(lamp.xyz)-origin;
      float rayT=clamp(dot(delta,rayDir),0.0,span);
      float3 off=delta-rayDir*rayT;
      rgb+=tint(i)*(-lamp.w)*(exp(-dot(off,off)*100.0)*1.6+exp(-dot(off,off)*650.0)*3.0);
    }
  }
  return rgb;
}
float3 dispersedExit(float3 rayDir, float3 outward, float3 point, out float3 retained) {
  float3 indices=float3(1.62,1.72,1.80)+float3(-0.09,0.0,0.14)*min(uFlare+0.40*uCharge,1.0);
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
  float drift=0.78+0.22*cos(viewPoint.y*6.0+viewPoint.x*3.0+uTime*0.5235987756);

  rgb+=iridescence*bevelAmount*0.18;
  // A smaller key reflection leaves dark planes beside the glossy upper-left highlight.
  float key=pow(max(dot(viewNormal,unitVec(float3(-0.55,0.65,0.85))),0.0),18.0);
  rgb+=float3(1.45,1.50,1.80)*key*exp(-square(viewPoint.x+0.45)*28.0-square(viewPoint.y-0.65)*24.0);
  float side=smoothstep(0.02,0.50,viewNormal.x);
  float band=1.0-0.25*exp(-square(viewPoint.y+0.015)*65.0);
  // Deliberate blue depth under the star, violet on side planes; charge lifts colour, not white fog.
  rgb+=mix(float3(0.055,0.055,0.50),float3(0.68,0.44,0.94),side)
    *band*(0.90+0.10*viewPoint.y)*(1.0+0.26*uCharge);
  if (uOutsideLights>0.0) for (int i=0;i<${LIGHTS};i++) {
    float4 lamp=uLights[i];
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
    float bounceWeight=clamp(uBounces-float(bounce),0.0,1.0);
    if (bounceWeight<=0.0) break;
    float3 exitNormal;
    float span=nextFacet(point,internalRay,exitNormal);
    float3 absorption=exp(-float3(1.30,0.66,0.075)*span);
    float starScale=mix(0.60+0.24*uFlare,1.0,tableView)*(bounce==0 ? 1.0 : 0.82);
    float starGain=mix(0.30+0.35*uFlare,1.0,tableView)*(bounce==0 ? 1.0 : 0.30);
    emission+=throughput*mix(float3(1.0),sqrt(absorption),0.25)*insideLight(point,internalRay,span,starScale,starGain)*pow(0.38,float(bounce))*bounceWeight;
    float3 transmitted=throughput*absorption;
    point+=internalRay*span;
    float3 retained;
    rgb+=transmitted*dispersedExit(internalRay,exitNormal,point,retained)
      *pow(0.25,float(bounce))*(0.34+0.30*uCharge+1.10*uFlare+0.10*max(uBounces-3.0,0.0))*bounceWeight;
    throughput=mix(throughput,transmitted*retained,bounceWeight);
    internalRay=reflect(internalRay,exitNormal);
    point+=internalRay*0.001;
  }
  rgb+=throughput*float3(0.025,0.025,0.20);
  // A low blue volumetric floor keeps TIR paths rich, without replacing the traced reflections.
  rgb+=float3(0.015+0.04*uCharge,0.018,0.09)*(1.0+uCharge*0.25);
  rgb*=float3(1.0+0.55*uCharge,1.0+0.30*uCharge,1.0+0.18*uCharge)
    *(1.0+0.025*sin(uTime*0.5235987756));
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
  float3 filmColour=iridescence*drift*(0.95+0.20*uCharge)+float3(0.70,0.76,0.86)*filmLine*0.42;
  rgb=mix(rgb,min(filmColour,float3(0.98)),film*0.90);
  // Emission is exposed after the body's shoulder so neither charge nor flare is flattened.
  return 1.0-(1.0-rgb)*exp(-emission);
}
float4 flareRays(float2 screen) {
  float radial=length(screen);
  float reach=mix(1.0,2.2,uFlare);
  float envelope=pow(max(1.0-radial/reach,0.0),1.4)*smoothstep(0.22,0.50,radial);
  float width=0.006+0.009*uFlare;
  float mainRay=exp(-square(screen.x/width))+exp(-square(screen.y/width));
  float diagonal=exp(-square((screen.x-screen.y)/(width*0.8)))
    +exp(-square((screen.x+screen.y)/(width*0.8)));
  float streak=exp(-abs(screen.y)/0.035)*exp(-abs(screen.x)*0.8);
  float alpha=clamp(uFlare*envelope*(mainRay*0.9+diagonal*0.20+streak*0.20),0.0,0.9);
  float3 colour=0.74+0.26*cos(float3(0.0,2.0,4.0)+radial*8.0);
  return float4(colour*alpha,alpha);
}
float4 burstBloom(float2 screen) {
  float radial=length(screen);
  // Compact white-pink bloom, violet shoulders, zero well before the 2.25-unit Rect edge.
  float alpha=uFlare*0.90*exp(-radial*radial*1.28)*(1.0-smoothstep(1.25,1.88,radial));
  float3 colour=mix(float3(1.0,0.91,0.98),float3(0.36,0.12,0.95),smoothstep(0.25,1.45,radial));
  return float4(colour*alpha,alpha);
}
half4 main(float2 xy) {
  if (uOpacity<=0.0) return half4(0.0);
  float2 screen=(xy-uCentre)/max(uRadius,0.01);
  screen.y=-screen.y;
  float4 light=float4(0.0);
  if (uFlare>0.0) {
    float4 rays=flareRays(screen);
    float4 bloom=burstBloom(screen);
    light=rays+bloom*(1.0-rays.a);
  }
  float2 modelScreen=screen*float2(sqrt(uStretch),1.0/uStretch);
  // Leave room for a silhouette-following halo; the bloom and rays need no slab work.
  float bound=uCharge>0.0 ? 1.5 : 1.12;
  if (dot(modelScreen,modelScreen)>bound*bound) return half4(light*uOpacity);
  float2 screenScale=float2(sqrt(uStretch),1.0/uStretch);
  float signedEdge=10.0;
  for (int i=0;i<${GEM_OUTLINE_PLANES};i++) {
    float3 edge=uOutline[i];
    float inward=(edge.z-dot(edge.xy,modelScreen))/max(length(edge.xy*screenScale),0.00001);
    signedEdge=min(signedEdge,inward);
  }
  float pixel=1.0/max(uRadius*uPixelRatio,1.0);
  float coverage=clamp(0.5+signedEdge/pixel,0.0,1.0);
  float outsideEdge=max(-signedEdge,0.0);
  float haloAlpha=0.36*uCharge*exp(-outsideEdge*10.0)
    *(1.0-smoothstep(1.20,1.50,length(modelScreen)));
  float3 haloColour=float3(0.30,0.20,0.95);
  float3 rgb=float3(0.0);
  if (coverage>0.0) {
    float3 origin=toLocal(float3(0.0,0.0,uCamera));
    float3 rayDir=toLocal(unitVec(float3(modelScreen,-uCamera)));
    float3 frontNormal, roundedNormal;
    float2 hit=interval(origin,rayDir,frontNormal,roundedNormal);
    rgb=traceGem(origin,rayDir,hit.x,frontNormal,roundedNormal,signedEdge);
  }
  float baseAlpha=coverage+haloAlpha*(1.0-coverage);
  float3 baseRGB=rgb*coverage+haloColour*haloAlpha*(1.0-coverage);
  float alpha=light.a+baseAlpha*(1.0-light.a);
  return half4(min(light.rgb+baseRGB*(1.0-light.a),float3(alpha))*uOpacity,alpha*uOpacity);
}
`;
