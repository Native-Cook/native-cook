#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The source shader uses sRGB directly. Its turn, waveR and waveA are always zero.
[[ stitchable ]] half4 firstLootRewardRays(float2 xy, half4 unused, float2 center, float alpha, float2 tilt) {
    float2 d = xy - (center - tilt * 10.0);
    float r = length(d);
    float a = atan2(d.y,d.x)/6.2831853+0.5;
    float f = fract(a*14.0);
    float edge = 0.6/max(r*6.2831853/14.0,1.0);
    float lit = smoothstep(0.42-edge,0.42+edge,f)*(1.0-smoothstep(0.98-edge,0.98+edge,f));
    float fall = (1.0-smoothstep(150.0,470.0,r))*smoothstep(40.0,120.0,r);
    float3 col = mix(float3(0.035),float3(0.06,0.048,0.082),lit*fall*alpha);
    return half4(half3(col),1.0);
}
