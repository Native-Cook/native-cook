#include <metal_stdlib>
using namespace metal;
struct SerumDropperRoutineUniforms { float4 view, tip, ages, pool; };
struct SerumDropperRoutineOil { float airborne, pool; };
struct SerumDropperRoutineVertex { float4 pos [[position]]; float2 uv; };
constant float serumDropperRoutineGrow = 0.55;
constant float serumDropperRoutineNeck = 0.16;
constant float serumDropperRoutineG = 1500.0;
float serumDropperRoutineSq(float v) { return v*v; }
float3 serumDropperRoutineLin(float3 c) { return pow(c,float3(2.2)); }
float serumDropperRoutineEase(float x) { float c=clamp(x,0.0,1.0); return c*c*(3.0-2.0*c); }
float serumDropperRoutineSmin(float a,float b,float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }
float serumDropperRoutineSeg(float2 p,float2 a,float2 b) { float2 pa=p-a,ba=b-a; float h=clamp(dot(pa,ba)/dot(ba,ba),0.0,1.0); return length(pa-ba*h); }
float serumDropperRoutineEll(float2 p,float2 r) { return (length(p/r)-1.0)*min(r.x,r.y); }
float serumDropperRoutineLand(int i,constant SerumDropperRoutineUniforms &F) { float offsets[3]={0.0,7.0,-6.0}; return F.pool.x+offsets[i]*F.ages.w; }
SerumDropperRoutineOil serumDropperRoutineOilAt(float2 s,constant SerumDropperRoutineUniforms &F) {
    SerumDropperRoutineOil o={1e9,1e9};
    float sc=F.ages.w; float2 tip=F.tip.xy; float floorY=F.tip.z;
    for(int i=0;i<3;i++) {
        float a=F.ages[i]; if(a<0.0) continue;
        float r=4.8*sc;
        if(a<serumDropperRoutineGrow+serumDropperRoutineNeck) {
            float g=serumDropperRoutineEase(a/serumDropperRoutineGrow);
            float u=clamp((a-serumDropperRoutineGrow)/serumDropperRoutineNeck,0.0,1.0);
            float rr=r*(0.35+0.65*g);
            float2 c=tip+float2(0.0,rr*0.95+3.5*sc*u);
            float drop=serumDropperRoutineEll(s-c,float2(rr,rr*(1.0+0.18*g+0.25*u)));
            float neck=serumDropperRoutineSeg(s,tip,c)-(1.3*sc)*(1.0-u*0.92);
            o.airborne=min(o.airborne,serumDropperRoutineSmin(drop,neck,2.2*sc*(1.0-u*0.7)));
            continue;
        }
        float tau=a-serumDropperRoutineGrow-serumDropperRoutineNeck;
        float y0=tip.y+r+3.5*sc;
        float drop=y0+0.5*serumDropperRoutineG*sc*tau*tau;
        float tLand=sqrt(max(2.0*(floorY-r*0.5-y0)/(serumDropperRoutineG*sc),0.0));
        float x=mix(tip.x,serumDropperRoutineLand(i,F),clamp(tau/max(tLand,0.01),0.0,1.0));
        if(tau<tLand) {
            float v=serumDropperRoutineG*sc*tau;
            float stretch=1.0+min(v/(900.0*sc),0.7);
            o.airborne=min(o.airborne,serumDropperRoutineEll(s-float2(x,drop),float2(r/sqrt(stretch),r*stretch)));
            continue;
        }
        float tp=tau-tLand;
        float2 c=float2(serumDropperRoutineLand(i,F),floorY);
        float R=(6.0+12.0*(1.0-exp(-4.5*tp)))*sc*(1.0+0.25*F.tip.w);
        float squash=exp(-tp*14.0);
        float pool=serumDropperRoutineEll(s-c,float2(R,R*0.3));
        if(squash>0.04) {
            float bead=serumDropperRoutineEll(s-c-float2(0.0,-r*0.6*squash),float2(r*(1.0+squash*0.6),max(r*0.7*squash,0.8*sc)));
            pool=serumDropperRoutineSmin(pool,bead,3.0*sc);
        }
        if(tp<0.35) {
            for(int k=0;k<6;k++) {
                float ang=float(k)/6.0*6.2831853+float(i);
                float2 out=float2(cos(ang),sin(ang)*0.3)*(6.0+46.0*tp)*sc;
                float up=(60.0*tp-190.0*tp*tp)*sc;
                float sp=length(s-c-out+float2(0.0,up))-1.2*sc*(1.0-tp/0.35);
                o.airborne=min(o.airborne,sp);
            }
        }
        o.pool=serumDropperRoutineSmin(o.pool,pool,6.0*sc);
    }
    return o;
}
vertex SerumDropperRoutineVertex serumDropperRoutineFullVs(uint vi [[vertex_id]]) {
    float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
    return {float4(uv*float2(2.0,-2.0)+float2(-1.0,1.0),0.0,1.0),uv};
}
float3 serumDropperRoutineEncode(float3 c) { return pow(clamp(c,float3(0.0),float3(1.0)),float3(1.0/2.2)); }
fragment float4 serumDropperRoutineOilFs(SerumDropperRoutineVertex v [[stage_in]],constant SerumDropperRoutineUniforms &F [[buffer(0)]]) {
    float2 s=v.pos.xy/F.view.z; float sc=F.ages.w;
    if(abs(s.x-F.pool.x)>70.0*sc||s.y<F.tip.y-4.0||s.y>F.tip.z+20.0*sc) return float4(0.0);
    SerumDropperRoutineOil o=serumDropperRoutineOilAt(s,F);
    float3 col=float3(0.0); float a=0.0; float soak=F.tip.w;
    if(o.pool<1.0) {
        float inside=1.0-smoothstep(-0.6,0.6,o.pool);
        float3 film=serumDropperRoutineLin(float3(0.92,0.56,0.15));
        float edge=exp(-serumDropperRoutineSq((o.pool+1.2)/1.0));
        float3 c=film*0.85+float3(1.0,0.95,0.85)*0.7*edge;
        float gleam=exp(-serumDropperRoutineSq((s.y-F.tip.z+1.5*sc)/(1.2*sc)))*exp(-serumDropperRoutineSq((s.x-F.pool.x+6.0*sc)/(9.0*sc)));
        c+=float3(1.0)*0.6*gleam;
        float pa=inside*(0.5+0.35*edge+0.3*gleam)*(1.0-soak);
        col=c*pa; a=pa;
    }
    if(o.airborne<1.0) {
        float inside=1.0-smoothstep(-0.6,0.6,o.airborne);
        float depth=clamp(-o.airborne/(3.5*sc),0.0,1.0);
        float3 c=serumDropperRoutineLin(float3(0.98,0.72,0.3))*(0.55+0.45*depth)+serumDropperRoutineLin(float3(0.55,0.28,0.05))*(1.0-depth)*0.6;
        float e=0.5;
        float gx=serumDropperRoutineOilAt(s+float2(e,0.0),F).airborne-o.airborne;
        float gy=serumDropperRoutineOilAt(s+float2(0.0,e),F).airborne-o.airborne;
        float3 n=normalize(float3(gx/e,gy/e,0.35+1.6*depth));
        float3 L=normalize(float3(-0.5,-0.65,0.6));
        c+=float3(1.0)*pow(max(dot(reflect(-L,n),float3(0.0,0.0,1.0)),0.0),18.0)*1.2;
        c+=serumDropperRoutineLin(float3(1.0,0.85,0.5))*0.35*smoothstep(0.2,0.9,-n.y)*depth;
        float da=inside*(0.7+0.25*(1.0-depth));
        col=c*da+col*(1.0-da); a=da+a*(1.0-da);
    }
    if(a<=0.001) return float4(0.0);
    return float4(serumDropperRoutineEncode(col/a)*a,a);
}
