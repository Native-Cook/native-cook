#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Input and output are premultiplied sRGB, matching the Skia image child shader.
[[ stitchable ]] half4 glassPiggySavingsSheen(float2 xy, half4 c, float pos, float strength, float w) {
    float band=exp(-pow((xy.x+xy.y*0.55-pos)/(w*0.07),2.0));
    return half4(c.rgb+half3(band*strength)*c.a,c.a);
}

// Skia's default gradient interpolates unpremultiplied sRGB stops. SwiftUI's
// radial gradient interpolates premultiplied stops, making the outer glow too bright.
[[ stitchable ]] half4 glassPiggySavingsGlow(float2 xy, half4 input, float2 center, float pulse) {
    float r=clamp(distance(xy,center)/330.0,0.0,1.0);
    float4 a=float4(32.0/255,215.0/255,128.0/255,0.78);
    float4 b=float4(14.0/255,130.0/255,78.0/255,0.42);
    float4 c=float4(5.0/255,40.0/255,24.0/255,0.12);
    float4 d=float4(3.0/255,17.0/255,11.0/255,0.0);
    float4 g=r<0.38 ? mix(a,b,r/0.38) : r<0.72 ? mix(b,c,(r-0.38)/0.34) : mix(c,d,(r-0.72)/0.28);
    return half4(half3(mix(d.rgb,g.rgb,g.a*(0.8+0.2*pulse))),1.0h);
}

[[ stitchable ]] half4 glassPiggySavingsPig(float2 xy, half4 input,
    texture2d<half> pig, float2 origin, float2 foot, float2 scale, float angle,
    float pos, float strength) {
    float2 pivot=foot-float2(0,120);
    float2 v=xy-pivot;
    float cs=cos(angle), sn=sin(angle);
    float2 local=float2(cs*v.x+sn*v.y,-sn*v.x+cs*v.y);
    float2 p=(local-float2(0,120))/scale+foot;
    float2 uv=(p-origin)/float2(340,800.0*340.0/900.0);
    constexpr sampler linearSampler(coord::normalized,address::clamp_to_zero,filter::linear);
    half4 c=pig.sample(linearSampler,uv);
    float band=exp(-pow((p.x+p.y*0.55-pos)/(340.0*0.07),2.0));
    return half4(c.rgb+half3(band*strength)*c.a,c.a);
}
