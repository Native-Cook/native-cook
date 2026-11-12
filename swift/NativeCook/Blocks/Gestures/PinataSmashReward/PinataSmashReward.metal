#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

namespace PinataSmashRewardShader {
inline float cross2(float2 a, float2 b) { return a.x * b.y - a.y * b.x; }
inline float2 position(device const float *u, int i) { return float2(u[48 + i * 5], u[49 + i * 5]); }
inline bool triangle(float2 p, device const float *u, int ia, int ib, int ic, thread float3 &b) {
    float2 a = position(u, ia), ab = position(u, ib) - a, ac = position(u, ic) - a;
    float det = cross2(ab, ac);
    if (abs(det) < 1e-8) return false;
    b.y = cross2(p - a, ac) / det;
    b.z = cross2(ab, p - a) / det;
    b.x = 1 - b.y - b.z;
    return min(b.x, min(b.y, b.z)) >= -1e-5;
}
}

// UVs and lighting use the same affine interpolation as Skia's indexed triangles.
[[ stitchable ]] half4 pinataSmashRewardMesh(float2 p, SwiftUI::Layer layer, device const float *u, int count) {
    using namespace PinataSmashRewardShader;
    for (int j = 11; j >= 0; --j) {
        float4 bounds = float4(u[j*4], u[j*4+1], u[j*4+2], u[j*4+3]);
        if (p.x < bounds.x || p.y < bounds.y || p.x > bounds.z || p.y > bounds.w) continue;
        for (int i = 11; i >= 0; --i) {
            int a = j * 13 + i;
            for (int tri = 1; tri >= 0; --tri) {
                int ia = a, ib = tri == 0 ? a + 1 : a + 14, ic = tri == 0 ? a + 14 : a + 13;
                float3 b;
                if (!triangle(p, u, ia, ib, ic, b)) continue;
                float2 uv = 0; float shade = 0;
                int ids[3] = {ia, ib, ic};
                for (int q=0; q<3; ++q) {
                    int o = 48 + ids[q]*5;
                    shade += u[o+2] * b[q]; uv += float2(u[o+3], u[o+4]) * b[q];
                }
                half4 c = layer.sample(uv);
                return half4(c.rgb * half(shade), c.a);
            }
        }
    }
    return half4(0);
}
