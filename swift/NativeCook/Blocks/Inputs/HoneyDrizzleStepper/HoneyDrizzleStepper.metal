#include <metal_stdlib>
using namespace metal;
struct HoneyDrizzleStepperUniforms { float4 clock; float4 layout; };
struct HoneyDrizzleStepperVertexOut { float4 position [[position]]; };
vertex HoneyDrizzleStepperVertexOut HoneyDrizzleStepperVertex(uint i [[vertex_id]]) {
    float2 p = float2((i << 1) & 2, i & 2);
    return {float4(p*2.-1., 0., 1.)};
}
float HoneyDrizzleStepperHash(float2 p) { p=fract(p*float2(123.34,456.21)); p+=dot(p,p+45.32); return fract(p.x*p.y); }
float HoneyDrizzleStepperNoise(float2 p) {
    float2 i=floor(p),f=fract(p); f=f*f*(3.-2.*f);
    return mix(mix(HoneyDrizzleStepperHash(i),HoneyDrizzleStepperHash(i+float2(1,0)),f.x),mix(HoneyDrizzleStepperHash(i+float2(0,1)),HoneyDrizzleStepperHash(i+float2(1,1)),f.x),f.y);
}
float HoneyDrizzleStepperMask(float3 c) { return smoothstep(.70,.80,c.r)*smoothstep(.40,.50,c.g/max(c.r,.01))*(1.-smoothstep(.35,.5,c.b)); }
float3 HoneyDrizzleStepperTex(texture2d<half> image, float2 p) {
    constexpr sampler s(coord::normalized,address::clamp_to_edge,filter::linear,mip_filter::none);
    return float3(image.sample(s,p/float2(1024,1536)).rgb);
}
fragment half4 HoneyDrizzleStepperFragment(HoneyDrizzleStepperVertexOut in [[stage_in]], constant HoneyDrizzleStepperUniforms &u [[buffer(0)]], texture2d<half> image [[texture(0)]]) {
    float2 xy=in.position.xy/u.layout.x;
    float2 px=(xy-float2(-12,10*u.layout.y))/(520./1024.);
    // Reduce Motion is the original photograph, with neither growth nor flow.
    if(u.clock.z>.5) return half4(half3(HoneyDrizzleStepperTex(image,px)),1);
    float A=u.clock.x-2.; float2 q=px;
    q.y+=A*23.*(1.-smoothstep(560.,1040.,px.y));
    float band=smoothstep(590.,660.,q.y)*(1.-smoothstep(990.,1045.,q.y));
    q.x=500.+(q.x-500.)/(1.+.12*A*band);
    float ph0=fract(u.clock.y/1.5),ph1=fract(u.clock.y/1.5+.5),w0=1.-abs(2.*ph0-1.);
    float sm=band*HoneyDrizzleStepperMask(HoneyDrizzleStepperTex(image,q));
    float2 n0=float2(HoneyDrizzleStepperNoise(q*float2(.05,.012)+ph0*3.)-.5,0.)*6.;
    float2 n1=float2(HoneyDrizzleStepperNoise(q*float2(.05,.012)+ph1*3.+5.)-.5,0.)*6.;
    float3 a=HoneyDrizzleStepperTex(image,q-float2(0.,ph0*46.)*sm+n0*sm),b=HoneyDrizzleStepperTex(image,q-float2(0.,ph1*46.)*sm+n1*sm);
    float3 col=mix(b,a,w0);
    float2 m=float2(1.+.04*A,1.+.12*A),mc=float2(500.,1230.),mq=mc+(px-mc)/m;
    if(px.y>mc.y) mq.y=px.y;
    float region=1.-smoothstep(.9,1.,length((px-mc)/float2(330.,260.)));
    float hb=HoneyDrizzleStepperMask(HoneyDrizzleStepperTex(image,mq));
    float mm=hb*region*smoothstep(1000.,1080.,mq.y);
    float3 ma=HoneyDrizzleStepperTex(image,mq-float2(ph0*22.,0.)*mm),mb=HoneyDrizzleStepperTex(image,mq-float2(ph1*22.,0.)*mm);
    float hm=hb*region*(1.-smoothstep(1195.,1225.,mq.y));
    col=mix(col,mix(mb,ma,w0),hm*step(1040.,px.y+(m.y-1.)*220.));
    return half4(half3(col),1);
}
