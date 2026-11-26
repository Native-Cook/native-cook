#include <metal_stdlib>
using namespace metal;
struct PaperPleatProgressUniforms { float uP,uSw,uT,uBoost,uFade,uLift; float2 uOrigin; float density; };
constexpr sampler PaperPleatProgressSampler(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::none);
vertex float4 PaperPleatProgressVertex(uint id [[vertex_id]]) {
    return float4(id==1?3.:-1.,id==2?3.:-1.,0.,1.);
}
constant float K=393./1024.; constant float2 CEN=float2(749.,903.); constant float TH0=1.75; constant float WEDGE=.08; constant float TAU=6.2831853;
float3 PaperPleatProgressLin(float3 c){return pow(c,float3(2.2));}
float3 PaperPleatProgressPhotoAt(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).rgb;}
float3 PaperPleatProgressPlateAt(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).rgb;}
float PaperPleatProgressMaskAt(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).r;}
float3 PaperPleatProgressPhoto4At(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).rgb;}
float3 PaperPleatProgressPlate5At(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).rgb;}
float3 PaperPleatProgressPlate6At(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).rgb;}
float PaperPleatProgressMask5At(float2 uv, texture2d<float> tex){return tex.sample(PaperPleatProgressSampler,clamp(uv,0.f,1.f)).r;}
float PaperPleatProgressLuma(float3 c){return dot(c,float3(.2126,.7152,.0722));}
float4 PaperPleatProgressShade(float2 p, constant PaperPleatProgressUniforms &v, texture2d<float> uPhoto, texture2d<float> uPlate, texture2d<float> uMask, texture2d<float> uPhoto4, texture2d<float> uPlate5, texture2d<float> uPlate6, texture2d<float> uMask5){float2 px=(p-v.uOrigin)/K;float2 uv=px/float2(1024.,1536.);
 float3 plate=PaperPleatProgressLin(PaperPleatProgressPlateAt(uv,uPlate));
 // where the sculpture was painted out, soften the plate to a smooth tone so no ghost edge shows
 float gw=smoothstep(.02,.35,PaperPleatProgressMask5At(uv,uMask5));
 float3 soft=PaperPleatProgressLin(mix(PaperPleatProgressPlate5At(uv,uPlate5),PaperPleatProgressPlate6At(uv,uPlate6),.5));
 plate=mix(plate,soft,gw);
 float2 d=px-CEN;float r=length(d);float th=atan2(d.y,d.x);
 float u=fract((TH0-th)/TAU);
 float3 col=plate;
 if(u<v.uP+WEDGE){
  float wz=(u-v.uP)/WEDGE;float us=u<v.uP?u:v.uP+wz*min(.16,1.-v.uP);   // the next pleats bunched into a closed stack at the front
  float ts=TH0-us*TAU;float2 src=CEN+r*float2(cos(ts),sin(ts));float2 suv=src/float2(1024.,1536.);
  float m=PaperPleatProgressMaskAt(suv,uMask);
  float3 s=PaperPleatProgressLin(PaperPleatProgressPhotoAt(suv,uPhoto));
  // pleat faces: the photo's fine light minus its blurred light
  float hp=PaperPleatProgressLuma(s)-PaperPleatProgressLuma(PaperPleatProgressLin(PaperPleatProgressPhoto4At(suv,uPhoto4)));
  float coral=smoothstep(.10,.30,s.r-s.b)*step(.15,s.r);
  float3 CO=PaperPleatProgressLin(float3(1.,.42,.28));
  if(u>=v.uP){s*=.92-.22*wz;}
  // the money moment: freshly opened pleats catch the light on their coral edges
  float g=(v.uP-u)/.05;float k=u<v.uP?exp(-g*g)*v.uBoost:0.;
  // the finishing raking light: soft tail, crisp bright leading edge
  float xs=mix(150.,1100.,v.uT);float k2=v.uSw*(smoothstep(xs-150.,xs-20.,src.x)*(1.-smoothstep(xs-2.,xs+4.,src.x)));
  float e=(src.x-xs+6.)/5.;float edge=v.uSw*exp(-e*e);
  float kk=max(k,k2);
  s+=CO*coral*kk*1.1+float3(1.,.96,.9)*max(hp,0.)*kk*3.;s+=float3(1.,.97,.92)*edge*.22;
  s*=v.uLift;
  col=mix(plate,s,m*v.uFade);}
 // fade the picture's top and bottom into the page colour
 float f=smoothstep(0.,.12,uv.y)*(1.-smoothstep(.88,1.,uv.y));col=mix(PaperPleatProgressLin(float3(.898,.851,.812)),col,f);
 return float4(float3(pow(clamp(col,0.,1.),float3(1./2.2))),1.);}

fragment float4 PaperPleatProgressFragment(float4 position [[position]],constant PaperPleatProgressUniforms &v [[buffer(0)]],
texture2d<float> uPhoto [[texture(0)]],
texture2d<float> uPlate [[texture(1)]],
texture2d<float> uMask [[texture(2)]],
texture2d<float> uPhoto4 [[texture(3)]],
texture2d<float> uPlate5 [[texture(4)]],
texture2d<float> uPlate6 [[texture(5)]],
texture2d<float> uMask5 [[texture(6)]]) {
    return PaperPleatProgressShade(position.xy/v.density,v,uPhoto,uPlate,uMask,uPhoto4,uPlate5,uPlate6,uMask5);
}
