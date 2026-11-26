package com.ngocdevv.nativecook.blocks.buttons.turbineboostbutton

// Raw sRGB texture bytes, one explicit gamma transform, no P3 matrix. Full-resolution, 40 samples.
internal const val TURBINE_BOOST_BUTTON_AGSL = """

uniform shader uFan;
uniform float uA; uniform float uArc; uniform float uB; uniform float2 uOff;
const float2 C=float2(194.2,448.6); const float2 SQ=float2(0.,252.1); const float SIDE=393.; const float RR=168.; const float RN=192.;
const float3 BG0=float3(0.,.008,.067);
const int N=40;
float3 lin(float3 c){return pow(c,float3(2.2));}
float3 img(float2 p){float2 uv=(p-SQ)/SIDE;float2 e=min(uv,1.-uv);float m=smoothstep(0.,.04,min(e.x,e.y));
 return mix(lin(BG0),lin(uFan.eval(clamp(uv,0.,1.)*1024.).rgb),m);}
float2 rot(float2 v,float a){float c=cos(a),s=sin(a);return float2(c*v.x-s*v.y,s*v.x+c*v.y);}
float luma(float3 c){return dot(c,float3(.2126,.7152,.0722));}
float3 shade(float2 p){float2 d=p-C;float r=length(d);
 float inR=1.-smoothstep(RR-4.,RR+3.,r);
 float3 col=img(p);
 if(inR>0.){
  // rotor: turned by uA, averaged along the blur arc
  float3 acc=float3(0.);
  for(int i=0;i<N;i++){float f=(float(i)+.5)/float(N)-.5;acc+=img(C+rot(d,-(uA+f*uArc)));}
  acc/=float(N);
  float bl=smoothstep(.05,.6,uArc);
  // specular rings where the blades shine, brighter toward the key light (upper left)
  float ang=atan(d.y,d.x);float k=max(0.,cos(ang+2.3));float key=.45+.55*k*k;
  float a1=(r-113.6)/2.2,a2=(r-155.)/1.6,a3=(r-113.6)/9.;
  float rings=exp(-a1*a1)*.9+exp(-a2*a2)*.6+exp(-a3*a3)*.18;
  col=mix(col,acc+lin(float3(.85,.92,1.))*rings*key*bl,inR);
 }
 float b=uB;float3 blue=lin(float3(.05,.28,1.));
 // outside the nacelle the field turns electric blue, with only a soft contact shadow
 float outside=smoothstep(RN,RN+6.,r);
 float g=(r-RR-40.)/260.;float h=(r-RN-4.)/14.;
 float3 field=lin(float3(.02,.06,.22))+blue*.55*exp(-g*g);
 field*=1.-.35*exp(-h*h);
 col=mix(col,field,b*outside);
 float q=r/115.;col+=blue*b*1.3*exp(-q*q)*inR*(1.-luma(col)*1.5);
 float q2=r/38.;col+=lin(float3(.6,.8,1.))*b*.9*exp(-q2*q2);
 float q3=(r-RR-14.)/9.;col+=blue*b*.9*exp(-q3*q3);
 return col;}
// Screen-fixed output noise matches RN; color transfer and all 40 arc samples stay unchanged.
half3 dither(float3 c,float2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),float2(.06711056,.00583715))));return half3(c+(n-.5)/255.);}
half4 main(float2 xy){float3 c=clamp(shade(xy-uOff),0.,1.);return half4(dither(pow(c,float3(1./2.2)),xy),1.);}
"""

// API 26–32: the identical effect through GLES 3.
internal const val TURBINE_BOOST_BUTTON_GLSL = """#version 300 es
precision highp float;

uniform sampler2D uFan; uniform vec2 uRes; uniform float uDensity; out vec4 O;
uniform float uA; uniform float uArc; uniform float uB; uniform vec2 uOff;
const vec2 C=vec2(194.2,448.6); const vec2 SQ=vec2(0.,252.1); const float SIDE=393.; const float RR=168.; const float RN=192.;
const vec3 BG0=vec3(0.,.008,.067);
const int N=40;
vec3 lin(vec3 c){return pow(c,vec3(2.2));}
vec3 img(vec2 p){vec2 uv=(p-SQ)/SIDE;vec2 e=min(uv,1.-uv);float m=smoothstep(0.,.04,min(e.x,e.y));
 return mix(lin(BG0),lin(texture(uFan,clamp(uv,0.,1.)).rgb),m);}
vec2 rot(vec2 v,float a){float c=cos(a),s=sin(a);return vec2(c*v.x-s*v.y,s*v.x+c*v.y);}
float luma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}
vec3 shade(vec2 p){vec2 d=p-C;float r=length(d);
 float inR=1.-smoothstep(RR-4.,RR+3.,r);
 vec3 col=img(p);
 if(inR>0.){
  // rotor: turned by uA, averaged along the blur arc
  vec3 acc=vec3(0.);
  for(int i=0;i<N;i++){float f=(float(i)+.5)/float(N)-.5;acc+=img(C+rot(d,-(uA+f*uArc)));}
  acc/=float(N);
  float bl=smoothstep(.05,.6,uArc);
  // specular rings where the blades shine, brighter toward the key light (upper left)
  float ang=atan(d.y,d.x);float k=max(0.,cos(ang+2.3));float key=.45+.55*k*k;
  float a1=(r-113.6)/2.2,a2=(r-155.)/1.6,a3=(r-113.6)/9.;
  float rings=exp(-a1*a1)*.9+exp(-a2*a2)*.6+exp(-a3*a3)*.18;
  col=mix(col,acc+lin(vec3(.85,.92,1.))*rings*key*bl,inR);
 }
 float b=uB;vec3 blue=lin(vec3(.05,.28,1.));
 // outside the nacelle the field turns electric blue, with only a soft contact shadow
 float outside=smoothstep(RN,RN+6.,r);
 float g=(r-RR-40.)/260.;float h=(r-RN-4.)/14.;
 vec3 field=lin(vec3(.02,.06,.22))+blue*.55*exp(-g*g);
 field*=1.-.35*exp(-h*h);
 col=mix(col,field,b*outside);
 float q=r/115.;col+=blue*b*1.3*exp(-q*q)*inR*(1.-luma(col)*1.5);
 float q2=r/38.;col+=lin(vec3(.6,.8,1.))*b*.9*exp(-q2*q2);
 float q3=(r-RR-14.)/9.;col+=blue*b*.9*exp(-q3*q3);
 return col;}
// Match the confirmed AGSL/RN output adjustment on the same screen-fixed 3x grid.
vec3 dither(vec3 c,vec2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),vec2(.06711056,.00583715))));return c+(n-.5)/255.;}
void main(){vec2 xy=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y)/uDensity; vec3 c=clamp(shade(xy-uOff),0.,1.);O=vec4(dither(pow(c,vec3(1./2.2)),xy),1.);}
"""
