#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

[[ stitchable ]] half4 tumbleCardRoutineStreak(float2 position, SwiftUI::Layer layer, float sigma, float2 axis) {
    if(sigma<0.3) return layer.sample(position);
    half4 result=half4(0); float weight=0;
    for(int i=-24;i<=24;i++) {
        float x=float(i)/6.0,w=exp(-0.5*x*x);
        result+=layer.sample(position+axis*x*sigma)*half(w); weight+=w;
    }
    return result/half(weight);
}
