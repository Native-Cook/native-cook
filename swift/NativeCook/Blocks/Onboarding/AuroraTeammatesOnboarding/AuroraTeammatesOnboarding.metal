#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

float3 auroraTeammatesOnboardingPal(float3 w, float3 a, float3 b, float3 c) {
  return a * w.x + b * w.y + c * w.z;
}

float auroraTeammatesOnboardingPal1(float3 w, float a, float b, float c) {
  return a * w.x + b * w.y + c * w.z;
}

float auroraTeammatesOnboardingBands(float x, float ph) {
  float s = 0.0;
  s += 0.46 * sin(x * 5.0 + 0.6 + 0.40 * sin(ph));
  s += 0.30 * sin(x * 9.0 - 1.2 + 0.55 * cos(ph * 2.0));
  s += 0.18 * sin(x * 16.0 + 2.4 - 0.70 * sin(ph));
  s += 0.10 * sin(x * 27.0 - 0.3 + 0.90 * cos(ph * 3.0));
  return s;
}

float auroraTeammatesOnboardingHash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float4 auroraTeammatesOnboardingField(float2 pos, float2 uSize, float2 uCore, float uPhase, float3 uW, float uFlare, float2 uTilt) {
  float W = uSize.x;
  float H = uSize.y;
  float2 d = pos - uCore;
  float2 uv = pos / uSize;
  float up = max(-d.y, 0.0) / H;

  // deep navy, darker in the top corners
  float3 col = mix(float3(0.047, 0.055, 0.265), float3(0.052, 0.060, 0.300), smoothstep(0.0, 0.6, uv.y));
  float corner = smoothstep(0.3, 1.0, abs(uv.x - 0.5) * 2.0) * (1.0 - smoothstep(0.05, 0.5, uv.y));
  col *= 1.0 - 0.45 * corner;

  float3 leftC = auroraTeammatesOnboardingPal(uW, float3(0.26, 0.29, 0.58), float3(0.25, 0.26, 0.56), float3(0.23, 0.29, 0.60));
  float3 rightC = auroraTeammatesOnboardingPal(uW, float3(0.24, 0.50, 0.38), float3(0.46, 0.20, 0.56), float3(0.12, 0.50, 0.56));
  float3 curtainC = auroraTeammatesOnboardingPal(uW, float3(0.12, 0.38, 0.15), float3(0.46, 0.09, 0.40), float3(0.06, 0.40, 0.46));
  float3 coreC = auroraTeammatesOnboardingPal(uW, float3(0.86, 0.99, 0.94), float3(0.98, 0.95, 1.00), float3(0.88, 0.96, 1.00));

  // soft curtains fanning up out of the notch
  float fanX = (d.x - uTilt.x * (0.4 + 3.0 * up)) / (W * (0.42 + 1.4 * up));
  float b = clamp(0.5 + 0.5 * auroraTeammatesOnboardingBands(fanX * 2.2, uPhase), 0.0, 1.0);
  b = b * sqrt(b);
  float rise = exp(-up / (0.13 + 0.03 * uFlare));
  float wide = exp(-fanX * fanX * 1.6);
  float side = clamp(d.x / (W * 0.55), -1.0, 1.0) * 0.5 + 0.5;
  float3 veil = mix(mix(leftC, rightC, side), coreC * 0.85, 0.45 * exp(-up / 0.07));
  col += veil * (0.30 + 1.05 * b) * rise * wide * (1.0 + 0.35 * uFlare);

  // the page's curtain on the right
  float kx = auroraTeammatesOnboardingPal1(uW, 0.95, 0.92, 0.90) * W;
  float ky = auroraTeammatesOnboardingPal1(uW, 0.17, 0.32, 0.36) * H;
  float sx = auroraTeammatesOnboardingPal1(uW, 0.16, 0.15, 0.20) * W;
  float sy = auroraTeammatesOnboardingPal1(uW, 0.15, 0.13, 0.12) * H;
  float2 bg = pos - uTilt;
  float cx = (bg.x - kx) / sx;
  float cy = (bg.y - ky) / sy;
  float folds = 0.75 + 0.25 * sin(bg.x * 0.06 + 0.9 * sin(uPhase * 2.0));
  col += curtainC * exp(-(cx * cx + cy * cy)) * folds * 1.15;

  // a cool glow low on the left
  float lx = (bg.x - W * 0.06) / (W * 0.30);
  float ly = (bg.y - uCore.y + H * 0.12) / (H * 0.11);
  col += leftC * exp(-(lx * lx + ly * ly)) * 0.38;

  // the light: a wide bright band along the edge and a hot centre
  float ex = d.x / (W * (0.35 + 0.05 * uFlare));
  float ey = d.y / (H * (0.072 + 0.014 * uFlare));
  float core = exp(-(ex * ex + ey * ey));
  float hot = exp(-length(float2(d.x * 0.6, d.y)) / (H * (0.026 + 0.02 * uFlare)));
  col += coreC * (core * (1.1 + 0.3 * uFlare) + hot * (0.8 + 0.9 * uFlare));

  col += (auroraTeammatesOnboardingHash(pos) - 0.5) / 255.0;
  return float4(clamp(col, 0.0, 1.0), 1.0);
}

[[ stitchable ]] half4 auroraTeammatesOnboardingAurora(float2 pos, half4 color, float2 uSize, float2 uCore, float uPhase, float3 uW, float uFlare, float2 uTilt) {
    return half4(auroraTeammatesOnboardingField(pos,uSize,uCore,uPhase,uW,uFlare,uTilt));
}

// Glass parameter mapping and optics follow Tencent/tgfx fe7ca97d2f14 (BSD-3-Clause).
// That revision's GlassStyle.h is byte-identical to reference/tgfx-glass/GlassStyle.h.
// The backdrop is evaluated analytically instead of capturing/processing an image each frame.
float2 auroraTeammatesOnboardingProject(float2 p,float4 pose,float4 turn) {
    float cs=cos(turn.x),sn=sin(turn.x),cx=cos(turn.y),sx=sin(turn.y),cy=cos(turn.z),sy=sin(turn.z);
    float2 r=float2(cs*p.x-sn*p.y,sn*p.x+cs*p.y)*pose.z;
    float3 rp=float3(cy*r.x,cx*r.y+sx*sy*r.x,sx*r.y-cx*sy*r.x);
    return pose.xy+rp.xy/(1.0-rp.z/640.0);
}
float auroraTeammatesOnboardingMatrixScale(float4 pose,float4 turn) {
    float cs=cos(turn.x),sn=sin(turn.x),cx=cos(turn.y),sx=sin(turn.y),cy=cos(turn.z),sy=sin(turn.z),s=pose.z;
    float g=-s*(sx*sn-cx*sy*cs)/640.0,h=-s*(sx*cs+cx*sy*sn)/640.0;
    float a=s*cy*cs+pose.x*g,b=s*(cx*sn+sx*sy*cs)+pose.y*g;
    float c=-s*cy*sn+pose.x*h,d=s*(cx*cs-sx*sy*sn)+pose.y*h;
    float aa=a*a+b*b,bb=a*c+b*d,cc=c*c+d*d;
    return sqrt(max(0.0001,(aa+cc+sqrt((aa-cc)*(aa-cc)+4*bb*bb))*0.5));
}
float auroraTeammatesOnboardingRoundedDistance(float2 p,float2 halfSize,float radius) {
    float2 q=abs(p)-(halfSize-radius);
    return length(max(q,float2(0)))+min(max(q.x,q.y),0.0)-radius;
}
float auroraTeammatesOnboardingTentHeight(float2 p,float2 halfSize,float radius,float tentRadius) {
    float d=-auroraTeammatesOnboardingRoundedDistance(p,halfSize,radius);
    float q=clamp(d/tentRadius,-1.0,1.0);
    return q>=0 ? 1.0-0.5*(1.0-q)*(1.0-q):0.5*(1.0+q)*(1.0+q);
}
float2 auroraTeammatesOnboardingMirror(float2 p,float2 lo,float2 hi) {
    float2 width=max(hi-lo,float2(1));
    float2 q=(p-lo)/width;
    float2 cycle=q-2.0*floor(q/2.0);
    return lo+(1.0-abs(cycle-1.0))*width;
}
constant float2 auroraTeammatesOnboardingFrostSamples[128]={
    float2(-1.935269011,-1.935269011),
    float2(-1.825860008,0.009346136),
    float2(-1.734748228,-0.627689811),
    float2(-1.656110377,0.650618008),
    float2(-1.586554733,-1.054656994),
    float2(-1.523924450,0.313477336),
    float2(-1.466758939,-0.293902909),
    float2(-1.414021119,1.087834517),
    float2(-1.364946836,-1.364946836),
    float2(-1.318956013,0.159556429),
    float2(-1.275597391,-0.454429633),
    float2(-1.234512652,0.848821480),
    float2(-1.195412271,-0.822320181),
    float2(-1.158058775,0.475254522),
    float2(-1.122254819,-0.140652385),
    float2(-1.087834517,1.414021119),
    float2(-1.054656994,-1.586554733),
    float2(-1.022601531,0.084213434),
    float2(-0.991563824,-0.539034000),
    float2(-0.961453088,0.746050567),
    float2(-0.932189771,-0.932189771),
    float2(-0.903703733,0.393078094),
    float2(-0.875932781,-0.216640746),
    float2(-0.848821480,1.234512652),
    float2(-0.822320181,-1.195412271),
    float2(-0.796384211,0.235817523),
    float2(-0.770973210,-0.372963162),
    float2(-0.746050567,0.961453088),
    float2(-0.721582945,-0.721582945),
    float2(-0.697539888,0.560777340),
    float2(-0.673893469,-0.065468739),
    float2(-0.650618008,1.656110377),
    float2(-0.627689811,-1.734748228),
    float2(-0.605086958,0.046747021),
    float2(-0.582789108,-0.582789108),
    float2(-0.560777340,0.697539888),
    float2(-0.539034000,-0.991563824),
    float2(-0.517542580,0.352998027),
    float2(-0.496287603,-0.255081423),
    float2(-0.475254522,1.158058775),
    float2(-0.454429633,-1.275597391),
    float2(-0.433799993,0.197543317),
    float2(-0.413353354,-0.413353354),
    float2(-0.393078094,0.903703733),
    float2(-0.372963162,-0.770973210),
    float2(-0.352998027,0.517542580),
    float2(-0.333172628,-0.102987768),
    float2(-0.313477336,1.523924450),
    float2(-0.293902909,-1.466758939),
    float2(-0.274440460,0.121798476),
    float2(-0.255081423,-0.496287603),
    float2(-0.235817523,0.796384211),
    float2(-0.216640746,-0.875932781),
    float2(-0.197543317,0.433799993),
    float2(-0.178517670,-0.178517670),
    float2(-0.159556429,1.318956013),
    float2(-0.140652385,-1.122254819),
    float2(-0.121798476,0.274440460),
    float2(-0.102987768,-0.333172628),
    float2(-0.084213434,1.022601531),
    float2(-0.065468739,-0.673893469),
    float2(-0.046747021,0.605086958),
    float2(-0.028041675,-0.028041675),
    float2(-0.009346136,1.825860008),
    float2(0.009346136,-1.825860008),
    float2(0.028041675,0.028041675),
    float2(0.046747021,-0.605086958),
    float2(0.065468739,0.673893469),
    float2(0.084213434,-1.022601531),
    float2(0.102987768,0.333172628),
    float2(0.121798476,-0.274440460),
    float2(0.140652385,1.122254819),
    float2(0.159556429,-1.318956013),
    float2(0.178517670,0.178517670),
    float2(0.197543317,-0.433799993),
    float2(0.216640746,0.875932781),
    float2(0.235817523,-0.796384211),
    float2(0.255081423,0.496287603),
    float2(0.274440460,-0.121798476),
    float2(0.293902909,1.466758939),
    float2(0.313477336,-1.523924450),
    float2(0.333172628,0.102987768),
    float2(0.352998027,-0.517542580),
    float2(0.372963162,0.770973210),
    float2(0.393078094,-0.903703733),
    float2(0.413353354,0.413353354),
    float2(0.433799993,-0.197543317),
    float2(0.454429633,1.275597391),
    float2(0.475254522,-1.158058775),
    float2(0.496287603,0.255081423),
    float2(0.517542580,-0.352998027),
    float2(0.539034000,0.991563824),
    float2(0.560777340,-0.697539888),
    float2(0.582789108,0.582789108),
    float2(0.605086958,-0.046747021),
    float2(0.627689811,1.734748228),
    float2(0.650618008,-1.656110377),
    float2(0.673893469,0.065468739),
    float2(0.697539888,-0.560777340),
    float2(0.721582945,0.721582945),
    float2(0.746050567,-0.961453088),
    float2(0.770973210,0.372963162),
    float2(0.796384211,-0.235817523),
    float2(0.822320181,1.195412271),
    float2(0.848821480,-1.234512652),
    float2(0.875932781,0.216640746),
    float2(0.903703733,-0.393078094),
    float2(0.932189771,0.932189771),
    float2(0.961453088,-0.746050567),
    float2(0.991563824,0.539034000),
    float2(1.022601531,-0.084213434),
    float2(1.054656994,1.586554733),
    float2(1.087834517,-1.414021119),
    float2(1.122254819,0.140652385),
    float2(1.158058775,-0.475254522),
    float2(1.195412271,0.822320181),
    float2(1.234512652,-0.848821480),
    float2(1.275597391,0.454429633),
    float2(1.318956013,-0.159556429),
    float2(1.364946836,1.364946836),
    float2(1.414021119,-1.087834517),
    float2(1.466758939,0.293902909),
    float2(1.523924450,-0.313477336),
    float2(1.586554733,1.054656994),
    float2(1.656110377,-0.650618008),
    float2(1.734748228,0.627689811),
    float2(1.825860008,-0.009346136),
    float2(1.935269011,1.935269011)
};

// A panel also frosts earlier panels and their ink where they overlap.
[[ stitchable ]] half4 auroraTeammatesOnboardingBackdrop(float2 pos, SwiftUI::Layer layer, float2 uSize, float4 box, float4 pose, float4 turn, float4 optics, float opacity) {
    float cs=cos(turn.x),sn=sin(turn.x),cx=cos(turn.y),sx=sin(turn.y),cy=cos(turn.z),sy=sin(turn.z),s=pose.z;
    float aa=s*cy*cs,bb=s*(cx*sn+sx*sy*cs),cc=-s*cy*sn,dd=s*(cx*cs-sx*sy*sn);
    float gg=-s*(sx*sn-cx*sy*cs)/640.0,hh=-s*(sx*cs+cx*sy*sn)/640.0;
    float2 q=pos-pose.xy;
    float m00=aa-q.x*gg,m01=cc-q.x*hh,m10=bb-q.y*gg,m11=dd-q.y*hh;
    float det=m00*m11-m01*m10;
    if(abs(det)<0.000001) return layer.sample(pos);
    float2 p=float2(q.x*m11-m01*q.y,m00*q.y-q.x*m10)/det;
    float sd=auroraTeammatesOnboardingRoundedDistance(p,box.zw,optics.x);
    float a=(1.0-smoothstep(-0.35,0.35,sd))*(opacity>0.00001 ? 1.0:0.0);
    if(a<=0) return layer.sample(pos);
    float2 screen=pos;
    // tgfx captures the transformed panel's axis-aligned bounds, with an axis-aligned mask,
    // and clips the result through the original transformed rounded rectangle.
    float2 c0=auroraTeammatesOnboardingProject(-box.zw,pose,turn);
    float2 c1=auroraTeammatesOnboardingProject(float2(box.z,-box.w),pose,turn);
    float2 c2=auroraTeammatesOnboardingProject(box.zw,pose,turn);
    float2 c3=auroraTeammatesOnboardingProject(float2(-box.z,box.w),pose,turn);
    float pixelScale=max(pose.w,1.0);
    float2 shapeLo=floor(min(min(c0,c1),min(c2,c3))*pixelScale)/pixelScale;
    float2 shapeHi=ceil(max(max(c0,c1),max(c2,c3))*pixelScale)/pixelScale;
    float2 lo=shapeLo;
    float2 hi=shapeHi;
    lo=max(lo,float2(0));hi=min(hi,uSize);
    float2 halfSize=(hi-lo)*0.5,shapeHalf=(shapeHi-shapeLo)*0.5,local=screen-(shapeLo+shapeHi)*0.5;
    float scale=auroraTeammatesOnboardingMatrixScale(pose,turn),radius=min(optics.x*scale,min(halfSize.x,halfSize.y));
    float udfPixelToPoint=max(1.0,max(hi.x-lo.x,hi.y-lo.y)*pixelScale/512.0)/pixelScale;
    float step=1.3*udfPixelToPoint,tentRadius=6.0/pixelScale;
    float height=auroraTeammatesOnboardingTentHeight(local,shapeHalf,radius,tentRadius);
    float hRight=auroraTeammatesOnboardingTentHeight(local+float2(step,0),shapeHalf,radius,tentRadius);
    float hUp=auroraTeammatesOnboardingTentHeight(local-float2(0,step),shapeHalf,radius,tentRadius);
    float2 gradient=float2(hRight-height,height-hUp)/step;
    float gradientLength=length(gradient);
    float2 inward=gradientLength>0.000001 ? gradient/gradientLength:float2(0);
    float refractionDistance=min(halfSize.x,halfSize.y)*0.18*0.1;
    float edgeProximity=(1.0-height*height)*(1.0-height*height)*1.2;
    float2 displacement=clamp(inward*refractionDistance*edgeProximity,float2(-0.999*refractionDistance),float2(0.999*refractionDistance));
    float dispersion=optics.y/100.0*0.2;
    float sigma=22.0/100.0*50.0*scale;
    // tgfx uses a Gaussian truncated at two sigmas. Stratified inverse-CDF taps
    // integrate that same kernel without preserving a sharp central ghost of prior ink.
    float3 col=float3(0);
    for(int j=0;j<128;j++) {
        float2 at=screen+auroraTeammatesOnboardingFrostSamples[j]*sigma;
        if(dispersion>=0.01) {
            float2 r=auroraTeammatesOnboardingMirror(at+displacement*(1.0+dispersion),lo,hi);
            float2 g=auroraTeammatesOnboardingMirror(at+displacement,lo,hi);
            float2 b=auroraTeammatesOnboardingMirror(at+displacement*(1.0-dispersion),lo,hi);
            col+=float3(layer.sample(r).r,layer.sample(g).g,layer.sample(b).b)/128.0;
        } else {
            float2 g=auroraTeammatesOnboardingMirror(at+displacement,lo,hi);
            col+=float3(layer.sample(g).rgb)/128.0;
        }
    }
    float edgeHeight=auroraTeammatesOnboardingTentHeight(local,shapeHalf,radius,10.0/pixelScale);
    float edgeWeight=1.0-smoothstep(0.5,0.75,edgeHeight);
    float angle=optics.z*0.0174532925199;
    float ndotl=dot(-inward,float2(sin(angle),-cos(angle)));
    col+=(smoothstep(0.35,1.0,ndotl)+smoothstep(0.35,1.0,-ndotl)*0.6)*edgeWeight*optics.w/100.0;
    return mix(layer.sample(pos),half4(half3(clamp(col,0.0,1.0)),1),half(a));
}

struct AuroraTeammatesOnboardingDustParams {
    float4 view,core,pal,notch,sink;
    float4 leaving[6];
    float4 arriving[6];
};
struct AuroraTeammatesOnboardingMote { float4 place,tint; };
float auroraTeammatesOnboardingDustHash(float x,float y) { return fract(sin(x*127.1+y*311.7+1.7)*43758.5453); }
float3 auroraTeammatesOnboardingTint(float side,float3 w) {
    float3 right=float3(.62,1,.78)*w.x+float3(.86,.66,1)*w.y+float3(.62,.9,1)*w.z;
    return mix(float3(.64,.72,1),right,clamp(side,0.0,1.0));
}
float2 auroraTeammatesOnboardingBezier(float2 a,float2 c,float2 b,float t) { float s=1-t;return a*s*s+c*2*s*t+b*t*t; }
kernel void auroraTeammatesOnboardingDustCs(constant AuroraTeammatesOnboardingDustParams& P [[buffer(0)]],device AuroraTeammatesOnboardingMote* motes [[buffer(1)]],uint i [[thread_position_in_grid]]) {
    if(i>=1536) return;
    float fi=float(i),px=P.view.z,flare=P.core.z,sinkOn=P.sink.z;
    float2 core=P.core.xy,home=mix(core,P.sink.xy,sinkOn),pos=core;
    float3 w=P.pal.xyz,colour=float3(1);
    float masked=1,size=0,alpha=0;
    if(i<768) {
        float k=2+floor(auroraTeammatesOnboardingDustHash(fi,3.17)*3),life=9/k;
        float s=P.view.w/life+auroraTeammatesOnboardingDustHash(fi,1.31),c=floor(s),ph=fract(s),cycle=c-k*floor(c/k);
        float r1=auroraTeammatesOnboardingDustHash(fi,cycle+.5),r2=auroraTeammatesOnboardingDustHash(fi+17,cycle),r3=auroraTeammatesOnboardingDustHash(fi+41,cycle),r4=auroraTeammatesOnboardingDustHash(fi+73,cycle);
        float x0=r1*2-1,theta=x0*1.1+(r2-.5)*.4,age=ph*life,speed=(18+46*r3)*px*(1+.8*flare);
        float2 dir=float2(sin(theta),-cos(theta)),start=core+float2(x0*P.notch.x*.85,6*px);
        pos=start+dir*speed*age+float2(sin(age*1.7+fi)*5*px,0)-P.notch.zw*(.35+.9*r4);
        alpha=smoothstep(0.0,.14,ph)*(1-smoothstep(.35,.9,ph))*(.06+.34*r4*r4)*(1+1.6*flare);
        size=(.45+1.1*r4*r4*r4)*px;colour=mix(float3(.94,.98,1),auroraTeammatesOnboardingTint(x0*.5+.5,w),.6);
    } else if(i<1152) {
        float j=fi-768;uint obj=uint(floor(j/64));float4 src=P.leaving[obj];
        float e=auroraTeammatesOnboardingDustHash(j,5.3),t=clamp((src.w-e*.45)/.55,0.0,1.0);
        if(src.z>0 && t>0 && t<1) {
            float a1=auroraTeammatesOnboardingDustHash(j,7.1)*6.2831853,rr=sqrt(auroraTeammatesOnboardingDustHash(j,9.7));
            float2 start=src.xy+float2(cos(a1),sin(a1)*.6)*src.z*.9*rr,mid=(start+home)*.5;
            float side=(auroraTeammatesOnboardingDustHash(j,11.3)-.5)*.9*length(home-start);
            pos=auroraTeammatesOnboardingBezier(start,mid+float2(side,-40*px*(1-sinkOn)),home,t*t);masked=1-sinkOn;
            alpha=pow(sin(3.14159*t),.7)*(.35+.55*auroraTeammatesOnboardingDustHash(j,13.1))*(1+.7*sinkOn);
            size=(.6+1.5*auroraTeammatesOnboardingDustHash(j,15.7))*px*(1+.6*sinkOn);
            colour=mix(float3(1),auroraTeammatesOnboardingTint(auroraTeammatesOnboardingDustHash(j,2.2),w),.4);
        }
    } else {
        float j=fi-1152;uint obj=uint(floor(j/64));float4 dst=P.arriving[obj];
        float e=auroraTeammatesOnboardingDustHash(j,21.9),t=clamp(dst.w*1.35-e*.35,0.0,1.0);
        if(dst.z>0 && t>0 && t<1) {
            float a1=auroraTeammatesOnboardingDustHash(j,23.3)*6.2831853,rr=sqrt(auroraTeammatesOnboardingDustHash(j,25.1));
            float2 goal=dst.xy+float2(cos(a1),sin(a1)*.6)*dst.z*.85*rr,mid=(home+goal)*.5;
            float side=(auroraTeammatesOnboardingDustHash(j,27.7)-.5)*.8*length(goal-home),ease=1-pow(1-t,3.0);
            pos=auroraTeammatesOnboardingBezier(home,mid+float2(side,-60*px),goal,ease);masked=1-sinkOn;
            alpha=pow(sin(3.14159*t),.8)*(.4+.6*auroraTeammatesOnboardingDustHash(j,29.3));size=(.6+1.7*auroraTeammatesOnboardingDustHash(j,31.9))*px;
            colour=mix(float3(1),auroraTeammatesOnboardingTint(auroraTeammatesOnboardingDustHash(j,4.4),w),.35);
        }
    }
    float xn=abs(pos.x-core.x)/max(P.notch.x,1.0),edge=P.pal.w;
    if(xn<1) edge+=P.notch.y*(1-smoothstep(.2,1.0,xn));
    alpha*=1-masked*smoothstep(-6*px,0.0,pos.y-edge);
    motes[i].place=float4(pos,size,alpha);motes[i].tint=float4(colour,0);
}
struct AuroraTeammatesOnboardingDustOut { float4 position [[position]];float2 uv;float3 colour;float alpha; };
vertex AuroraTeammatesOnboardingDustOut auroraTeammatesOnboardingDustVs(uint vi [[vertex_id]],uint ii [[instance_id]],constant AuroraTeammatesOnboardingDustParams& P [[buffer(0)]],device const AuroraTeammatesOnboardingMote* motes [[buffer(1)]]) {
    constexpr float2 corners[6]={float2(-1,-1),float2(1,-1),float2(-1,1),float2(-1,1),float2(1,-1),float2(1,1)};
    AuroraTeammatesOnboardingMote m=motes[ii];float2 corner=corners[vi];float reach=max(m.place.z,0.0)*3.2;
    float2 at=m.place.xy+corner*reach;
    return {float4(at.x/P.view.x*2-1,1-at.y/P.view.y*2,0,1),corner,m.tint.xyz,m.place.w};
}
fragment float4 auroraTeammatesOnboardingDustFs(AuroraTeammatesOnboardingDustOut in [[stage_in]]) {
    float r2=dot(in.uv,in.uv),i=(exp(-r2*16)+.3*exp(-r2*3))*in.alpha;
    return float4(in.colour*i,i*.55);
}
