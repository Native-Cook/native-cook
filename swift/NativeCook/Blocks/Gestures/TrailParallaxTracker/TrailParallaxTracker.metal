#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The visible viewport is rasterised in non-linear sRGB before this native nearest-fill remap.
[[ stitchable ]] half4 trailParallaxTrackerRelight(float2 p, half4 color, device const float *source, int sourceCount,
    device const float *destination, int destinationCount, float count) {
    if (color.a < 0.003h) return half4(0.0h);
    float3 rgb=float3(color.rgb)/float(color.a),delta=float3(0.0);float best=1e9;
    for (int i=0;i<6;i++) {
        if (float(i)<count) {
            float3 src=float3(source[i*3],source[i*3+1],source[i*3+2]);float3 e=rgb-src;float d=dot(e,e);
            if (d<best) {best=d;delta=float3(destination[i*3],destination[i*3+1],destination[i*3+2])-src;}
        }
    }
    return half4(half3(clamp(rgb+delta,0.0,1.0))*color.a,color.a);
}

[[ stitchable ]] half4 trailParallaxTrackerHorizontalBlur(float2 p, SwiftUI::Layer layer, float radius) {
    if (radius < 0.1) return layer.sample(p);
    return layer.sample(p)*0.4026h + (layer.sample(p+float2(radius,0.0))+layer.sample(p-float2(radius,0.0)))*0.2442h
        +(layer.sample(p+float2(2.0*radius,0.0))+layer.sample(p-float2(2.0*radius,0.0)))*0.0545h;
}
[[ stitchable ]] half4 trailParallaxTrackerGrain(float2 p, half4 color) {
    float2 px=floor(p*3.0);
    float g=fract(sin(dot(px,float2(12.9898,78.233)))*43758.5453)*2.0-1.0;
    half a=half(0.03*abs(g));return g>0.0 ? half4(a,a,a,a):half4(0.0h,0.0h,0.0h,a);
}
