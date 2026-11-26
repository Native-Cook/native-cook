package com.ngocdevv.nativecook.blocks.progress.paperpleatprogress

internal const val PAPER_PLEAT_PROGRESS_GLSL = """#version 300 es
precision highp float;
uniform vec2 uRes; uniform float uDensity; out vec4 O;

uniform sampler2D uPhoto; uniform sampler2D uPlate; uniform sampler2D uMask; uniform sampler2D uPhoto4; uniform sampler2D uPlate5;
uniform sampler2D uPlate6; uniform sampler2D uMask5;
uniform float uP; uniform float uSw; uniform float uT; uniform float uBoost; uniform float uFade; uniform float uLift;
uniform vec2 uOrigin;
const float K=393./1024.; const vec2 CEN=vec2(749.,903.); const float TH0=1.75; const float WEDGE=.08; const float TAU=6.2831853;
vec3 lin(vec3 c){return pow(c,vec3(2.2));}
vec3 photoAt(vec2 uv){return vec3(texture(uPhoto,clamp(uv,0.,1.)).rgb);}
vec3 plateAt(vec2 uv){return vec3(texture(uPlate,clamp(uv,0.,1.)).rgb);}
float maskAt(vec2 uv){return float(texture(uMask,clamp(uv,0.,1.)).r);}
vec3 photo4At(vec2 uv){return vec3(texture(uPhoto4,clamp(uv,0.,1.)).rgb);}
vec3 plate5At(vec2 uv){return vec3(texture(uPlate5,clamp(uv,0.,1.)).rgb);}
vec3 plate6At(vec2 uv){return vec3(texture(uPlate6,clamp(uv,0.,1.)).rgb);}
float mask5At(vec2 uv){return float(texture(uMask5,clamp(uv,0.,1.)).r);}
float luma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}
void main(){vec2 p=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y)/uDensity;vec2 px=(p-uOrigin)/K;vec2 uv=px/vec2(1024.,1536.);
 vec3 plate=lin(plateAt(uv));
 // where the sculpture was painted out, soften the plate to a smooth tone so no ghost edge shows
 float gw=smoothstep(.02,.35,mask5At(uv));
 vec3 soft=lin(mix(plate5At(uv),plate6At(uv),.5));
 plate=mix(plate,soft,gw);
 vec2 d=px-CEN;float r=length(d);float th=atan(d.y,d.x);
 float u=fract((TH0-th)/TAU);
 vec3 col=plate;
 if(u<uP+WEDGE){
  float wz=(u-uP)/WEDGE;float us=u<uP?u:uP+wz*min(.16,1.-uP);   // the next pleats bunched into a closed stack at the front
  float ts=TH0-us*TAU;vec2 src=CEN+r*vec2(cos(ts),sin(ts));vec2 suv=src/vec2(1024.,1536.);
  float m=maskAt(suv);
  vec3 s=lin(photoAt(suv));
  // pleat faces: the photo's fine light minus its blurred light
  float hp=luma(s)-luma(lin(photo4At(suv)));
  float coral=smoothstep(.10,.30,s.r-s.b)*step(.15,s.r);
  vec3 CO=lin(vec3(1.,.42,.28));
  if(u>=uP){s*=.92-.22*wz;}
  // the money moment: freshly opened pleats catch the light on their coral edges
  float g=(uP-u)/.05;float k=u<uP?exp(-g*g)*uBoost:0.;
  // the finishing raking light: soft tail, crisp bright leading edge
  float xs=mix(150.,1100.,uT);float k2=uSw*(smoothstep(xs-150.,xs-20.,src.x)*(1.-smoothstep(xs-2.,xs+4.,src.x)));
  float e=(src.x-xs+6.)/5.;float edge=uSw*exp(-e*e);
  float kk=max(k,k2);
  s+=CO*coral*kk*1.1+vec3(1.,.96,.9)*max(hp,0.)*kk*3.;s+=vec3(1.,.97,.92)*edge*.22;
  s*=uLift;
  col=mix(plate,s,m*uFade);}
 // fade the picture's top and bottom into the page colour
 float f=smoothstep(0.,.12,uv.y)*(1.-smoothstep(.88,1.,uv.y));col=mix(lin(vec3(.898,.851,.812)),col,f);
 O=vec4(vec3(pow(clamp(col,0.,1.),vec3(1./2.2))),1.);}

"""
