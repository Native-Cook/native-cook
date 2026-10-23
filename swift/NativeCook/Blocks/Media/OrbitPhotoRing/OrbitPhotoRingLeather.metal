#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float2 orbitPhotoRingHash(float2 p) {
    return fract(sin(float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)))) * 43758.5453);
}

// Pebbled leather: bright pebbles on a darker ground, in the face's own coordinates so the grain
// turns and foreshortens with the body. `inv0…inv2` are the rows of the matrix that maps a screen
// point back onto the face.
[[ stitchable ]] half4 orbitPhotoRingLeather(float2 position, float3 inv0, float3 inv1, float3 inv2, float3 tone, float grain) {
    float3 q = float3(position, 1.0);
    float w = dot(inv2, q);
    float2 xy = float2(dot(inv0, q), dot(inv1, q)) / w;
    float2 g = xy * grain;
    float2 i = floor(g);
    float2 f = fract(g);
    float d = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float2 o = float2(float(x), float(y));
            float2 r = o + orbitPhotoRingHash(i + o) - f;
            d = min(d, dot(r, r));
        }
    }
    float pebble = 1.0 - smoothstep(0.04, 0.6, d);
    return half4(half3(tone * (0.78 + 0.34 * pebble)), 1.0);
}
