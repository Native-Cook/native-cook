#include <metal_stdlib>
using namespace metal;
struct TurbineBoostButtonUniforms { float4 clock; float4 layout; };
vertex float4 TurbineBoostButtonVertex(uint id [[vertex_id]]) {
    float2 p=float2((id<<1)&2,id&2); return float4(p*2.-1.,0,1);
}
static float3 TurbineBoostButtonLin(float3 c) { return pow(c,float3(2.2)); }
static float3 TurbineBoostButtonImage(float2 p, texture2d<float> fan) {
    constexpr sampler sample(coord::normalized,address::clamp_to_edge,filter::linear);
    float2 uv=(p-float2(0,252.1))/393.,e=min(uv,1.-uv);
    float m=smoothstep(0.,.04,min(e.x,e.y));
    return mix(TurbineBoostButtonLin(float3(0,.008,.067)),TurbineBoostButtonLin(fan.sample(sample,clamp(uv,0.,1.)).rgb),m);
}
fragment float4 TurbineBoostButtonFragment(float4 xy [[position]], constant TurbineBoostButtonUniforms &u [[buffer(0)]], texture2d<float> fan [[texture(0)]]) {
    float2 p=xy.xy/u.layout.z-float2(u.layout.x,u.layout.y);
    float2 C=float2(194.2,448.6),d=p-C; float r=length(d),inR=1.-smoothstep(164.,171.,r);
    float3 col=TurbineBoostButtonImage(p,fan);
    if(inR>0.) {
        float3 acc=0.;
        // Plain 40-sample arc average. The nacelle stays fixed; highlights are painted below.
        for(int i=0;i<40;i++) {
            float f=(float(i)+.5)/40.-.5,a=-(u.clock.x+f*u.clock.y),c=cos(a),s=sin(a);
            acc+=TurbineBoostButtonImage(C+float2(c*d.x-s*d.y,s*d.x+c*d.y),fan);
        }
        acc/=40.; float k=max(0.,cos(atan2(d.y,d.x)+2.3));
        float a1=(r-113.6)/2.2,a2=(r-155.)/1.6,a3=(r-113.6)/9.;
        float rings=.9*exp(-a1*a1)+.6*exp(-a2*a2)+.18*exp(-a3*a3);
        col=mix(col,acc+TurbineBoostButtonLin(float3(.85,.92,1))*rings*(.45+.55*k*k)*smoothstep(.05,.6,u.clock.y),inR);
    }
    float b=u.clock.z; float3 blue=TurbineBoostButtonLin(float3(.05,.28,1));
    float g=(r-208.)/260.,h=(r-196.)/14.;
    float3 field=(TurbineBoostButtonLin(float3(.02,.06,.22))+blue*.55*exp(-g*g))*(1.-.35*exp(-h*h));
    col=mix(col,field,b*smoothstep(192.,198.,r));
    float q=r/115.;col+=blue*b*1.3*exp(-q*q)*inR*(1.-dot(col,float3(.2126,.7152,.0722))*1.5);
    q=r/38.;col+=TurbineBoostButtonLin(float3(.6,.8,1))*b*.9*exp(-q*q);
    q=(r-182.)/9.;col+=blue*b*.9*exp(-q*q);
    return float4(pow(clamp(col,0.,1.),float3(1./2.2)),1);
}
