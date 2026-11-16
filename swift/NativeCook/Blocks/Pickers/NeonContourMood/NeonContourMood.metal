#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Direct translation of ncm-shaders.ts at the approved RN HEAD.
// RN emits encoded Display P3 on iOS. SwiftUI colorEffect consumes sRGB;
// retain the source conversion and adapt its half-precision result at the host boundary.
namespace NeonContourMoodShader {
float3 linearize(float3 c) { return mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(float3(0.04045),c)); }
float3 encode(float3 c) { c=max(c,float3(0.0)); return mix(c*12.92,1.055*pow(c,float3(1.0/2.4))-0.055,step(float3(0.0031308),c)); }
float3 display(float3 c, float mode) {
  if(mode < 0.5) return c;
  float3 l=linearize(c);
  float3 p=float3(dot(l,float3(0.8225929,0.177534,0.0)),dot(l,float3(0.0331995,0.9667835,0.0)),dot(l,float3(0.0170853,0.0723957,0.9103015)));
  return mix(c,encode(p),mode);
}

inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float3 c = float3(color.rgb) / float(color.a);
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * float(color.a)), color.a);
}

struct Field {
    float2 center;
    float scale;
    float4 shapeA[7];
    float4 shapeB[7];
    float3 tints[7];
    float3 lowerTints[7];
    float3 coreTint;
    float3 ground;
    float3 coreCol;
    float2 lean;
    float wake;
    float breath;
    float radiate;
    float warm;
    float pulse;
    float cutoff;
    float fade;
    float spacing;
    float rings;
    float p3;

float distanceAt(float2 p, float4 a, float4 b) {
  float2 q0=abs(p);
  float2 pp=float2(q0.x+b.y*exp(-q0.y*q0.y/(b.z*b.z)), q0.y+a.w*exp(-q0.x*q0.x/(b.x*b.x)));
  float2 q=pp-a.xy+float2(a.z);
  return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-a.z;
}
half4 render(float2 xy) {
  float2 p=(xy-center)/max(scale,0.01);
  if (xy.y > cutoff) return half4(half3(display(ground,p3)),1.0h);
  float2 p0=p-lean;
  float d0=distanceAt(p0,shapeA[0],shapeB[0])-breath;
  float core=1.0-smoothstep(-8.0,2.0,d0);
  float3 col=mix(ground,coreCol,core);
  float3 energy=float3(0.0);
  for(int k=0;k<7;k++) {
    float fk=float(k); float off=0.0; float strength=0.0; float vr=0.0;
    if(k==0) { off=0.0; strength=0.12; vr=1.0; }
    if(k==1) { off=8.0; strength=0.06; vr=1.0; }
    if(k==2) { off=12.864303; strength=1.0; vr=1.0; }
    if(k==3) { off=32.0; strength=0.05; vr=0.45; }
    if(k==4) { off=49.385498; strength=0.75; vr=0.3; }
    if(k==5) { off=86.0; strength=0.02; vr=0.1; }
    if(k==6) { off=110.118761; strength=0.55; vr=0.07; }
    float4 A=shapeA[k]; float4 B=shapeB[k];
    float2 pk=p-lean*(1.0-fk/7.0);
    float ny=max(0.0,A.w)/49.46817; float nx=max(0.0,B.y)/49.46817;
    float hw=60.0+off*0.08; float hw2=hw*hw;
    float hollow=ny*off*0.5*exp(-(pk.x*pk.x)/hw2)*smoothstep(20.0,95.0,abs(pk.y))
      +nx*off*0.5*exp(-(pk.y*pk.y)/hw2)*smoothstep(20.0,95.0,abs(pk.x));
    float vs=1.0+ny*off*vr/(107.983216+off);
    float hs=1.0+nx*off*vr/(132.134746+off);
    float dk=distanceAt(float2(pk.x/hs,pk.y/vs),A,B)-breath;
    float travel=radiate*fk/6.0;
    float d=dk+hollow-off*(spacing/26.0)*(1.0+0.10*wake)-travel;
    float zone=smoothstep(-9.0,6.0,d0);
    float line=exp(-d*d/0.52)*strength;
    float halo=exp(-abs(d)/9.0)*strength;
    float softness=k==2 ? 145.0 : (k==6 ? 1000.0 : 420.0);
    float band=exp(-d*d/softness);
    float lower=smoothstep(-10.0,150.0,p.y);
    float3 light=mix(tints[k],lowerTints[k],lower*0.85);
    float weight=k==2 ? 3.058259 : (k==4 ? 1.197484 : (k==6 ? 0.223609 : 0.714657));
    float enabled=1.0-step(rings,fk);
    float3 lineTint=mix(light,float3(1.0),k==2 ? 0.12 : (k==4 ? 0.06 : 0.035));
    float pq=(pulse-0.026*fk-0.06)/0.045;
    float boost=1.0+(pulse<0.0 ? 0.0 : 1.4*exp(-(pq*pq)));
    energy+=(light*(halo*0.32+band*weight*(1.0+lower*0.3))+lineTint*line*(0.8+lower*0.3)/0.3)*enabled*zone*(1.0+0.20*wake)*boost;
  }
  // A warm, broad inner shoulder is present in the reference, rather than seven uniformly bright hairlines.
  float3 inner=coreTint*exp(-d0*d0/40.0)*0.9;
  energy+=inner;
  float notchPhase=(abs(p0.y)-118.436709)/41.771969;
  float notchWarm=exp(-(p0.x*p0.x)/4514.829637320324)*exp(-(notchPhase*notchPhase))*(1.0-smoothstep(180.0,220.0,abs(p0.y)));
  energy+=coreTint*notchWarm*2.0*(max(0.0,shapeA[0].w)/49.46817)*warm*smoothstep(-9.0,6.0,d0);
  float quiet=1.0-smoothstep(cutoff-fade,cutoff,xy.y);
  float3 lin=linearize(col)+energy*quiet*0.3;
  float3 shoulder=lin/(1.0+lin*0.38);
  col=encode(shoulder);
  float grain=fract(sin(dot(floor(xy*3.0),float2(12.9898,78.233)))*43758.5453)-0.5;
  col+=grain/510.0;
  return half4(half3(display(clamp(col,0.0,1.0),p3)),1.0h);
}
};
struct Thumb {
float2 center; float radius; float3 hue; float3 ground; float press; float p3;
half4 render(float2 xy) {
 float2 p=(xy-center)/radius; float r=length(p);
 float3 col=ground;
 col+=hue*(exp(-dot(p,p)/0.045)*0.65+exp(-r*r/0.5)*0.11);
 float rim=(r-0.975)/0.018;
 float edge=exp(-(rim*rim));
 col+=mix(hue,float3(0.65),0.4)*edge*(0.25+0.10*p.x+0.15*p.y+press*0.3);
 return half4(half3(display(clamp(col,0.0,1.0),p3)),half(1.0-smoothstep(0.98,1.02,r)));
}
};
}

[[stitchable]] half4 neonContourMoodField(float2 position, half4 color, device const float *u, int count) {
    if (count < 121) return half4(0.0h);
    NeonContourMoodShader::Field s;
    s.center=float2(u[0],u[1]); s.scale=u[2]; s.cutoff=u[3]; s.fade=u[4];
    s.wake=u[5]; s.breath=u[6]; s.radiate=u[7]; s.warm=u[8]; s.pulse=u[9];
    s.spacing=u[10]; s.rings=u[11]; s.lean=float2(u[12],u[13]); s.p3=1.0;
    for(int k=0;k<7;k++) {
        int a=14+k*4, b=42+k*4, c=70+k*3, d=91+k*3;
        s.shapeA[k]=float4(u[a],u[a+1],u[a+2],u[a+3]);
        s.shapeB[k]=float4(u[b],u[b+1],u[b+2],u[b+3]);
        s.tints[k]=float3(u[c],u[c+1],u[c+2]);
        s.lowerTints[k]=float3(u[d],u[d+1],u[d+2]);
    }
    s.coreTint=float3(u[112],u[113],u[114]);
    s.ground=float3(u[115],u[116],u[117]); s.coreCol=float3(u[118],u[119],u[120]);
    return NeonContourMoodShader::displaySRGB(s.render(position));
}
[[stitchable]] half4 neonContourMoodThumb(float2 position, half4 color, device const float *u, int count) {
    if (count < 10) return half4(0.0h);
    NeonContourMoodShader::Thumb s;
    s.center=float2(u[0],u[1]); s.radius=u[2]; s.press=u[3];
    s.hue=float3(u[4],u[5],u[6]); s.ground=float3(u[7],u[8],u[9]); s.p3=1.0;
    return NeonContourMoodShader::displaySRGB(s.render(position));
}
