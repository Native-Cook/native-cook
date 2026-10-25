#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Slide Projector Gallery — the living-room wall, the projected slide and the projector's beam,
 in one full-screen pass (screen coordinates, pt). Ported from the React Native Skia shader
 (projectorShader.ts) with the same maths.

 The layer holds the slide in the gate at three focus levels (sharp, soft, softer), stacked
 vertically at the top of the full-screen view, each tile.x × tile.y pt; `tileAt` samples it
 (the Skia version sampled a 5 × 3 atlas; here only the current slide's row is in the layer).

   size   screen size            proj  (centre x, top, width, height)
   key    (top width ×, bottom width ×)       lens (x, y)
   slide  (unused, bright, drop pt, focus)    look (flicker, image scale, 0, 0)
   tile   tile size in pt
 */

static float SlideProjectorGalleryHash(float2 q) {
    return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453);
}

static float SlideProjectorGalleryNoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(SlideProjectorGalleryHash(i), SlideProjectorGalleryHash(i + float2(1.0, 0.0)), f.x),
               mix(SlideProjectorGalleryHash(i + float2(0.0, 1.0)), SlideProjectorGalleryHash(i + float2(1.0, 1.0)), f.x), f.y);
}

static float3 SlideProjectorGalleryTile(SwiftUI::Layer layer, float2 uv, float level, float2 tile) {
    float2 m = 0.75 / tile;
    uv = clamp(uv, m, 1.0 - m);
    return float3(layer.sample(float2(uv.x * tile.x, (level + uv.y) * tile.y)).rgb);
}

[[ stitchable ]] half4 SlideProjectorGalleryWall(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float4 proj,
    float2 key,
    float2 lens,
    float4 slide,
    float4 look,
    float2 tile
) {
    // Wall.
    float mott = SlideProjectorGalleryNoise(p * 0.012) * 0.6 + SlideProjectorGalleryNoise(p * 0.045) * 0.4;
    float grain = SlideProjectorGalleryHash(floor(p * 1.5)) - 0.5;
    float stripe = 0.5 + 0.5 * sin(p.x * 0.33);
    float tex = 0.86 + 0.14 * mott + 0.035 * grain;
    float3 wall = float3(0.165, 0.128, 0.108) * tex * (0.97 + 0.03 * stripe);
    float2 vc = (p - float2(size.x * 0.5, size.y * 0.38)) / size;
    wall *= 1.0 - 0.9 * dot(vc, vc);

    float3 lamp = float3(1.0, 0.9, 0.74);
    float lampOn = slide.y * look.x;

    // The keystoned slide.
    float v = (p.y - proj.y) / proj.w;
    float hw = proj.z * 0.5 * mix(key.x, key.y, clamp(v, 0.0, 1.0));
    float u = (p.x - proj.x) / (2.0 * hw) + 0.5;
    float soft = 0.8 + slide.w * 5.0;
    float ex = min(u, 1.0 - u) * 2.0 * hw;
    float ey = min(v, 1.0 - v) * proj.w;
    float inside = smoothstep(-soft, soft, min(ex, ey));
    float3 col = wall;
    if (inside > 0.0) {
        float2 c = float2(u, v) - 0.5;
        float2 uv = c / look.y + 0.5;
        uv.y -= slide.z / proj.w;
        float3 img;
        float f = clamp(slide.w, 0.0, 1.0);
        if (f < 0.5) img = mix(SlideProjectorGalleryTile(layer, uv, 0.0, tile), SlideProjectorGalleryTile(layer, uv, 1.0, tile), f * 2.0);
        else img = mix(SlideProjectorGalleryTile(layer, uv, 1.0, tile), SlideProjectorGalleryTile(layer, uv, 2.0, tile), f * 2.0 - 1.0);
        // Above the dropping slide the gate is empty: open light.
        float gate = step(uv.y, 0.0);
        img = mix(img, float3(0.92), gate);
        float vig = 1.0 - 0.42 * dot(c * float2(1.6, 1.9), c * float2(1.6, 1.9));
        float hot = 1.0 + 0.1 * exp(-dot(c, c) * 9.0);
        float3 light = img * lamp * vig * hot * lampOn;
        col = wall + light * (0.8 + 0.2 * tex) * inside;
    }
    // Light spilling around the frame.
    float2 q = float2(u - 0.5, v - 0.5);
    float d = length(max(abs(q) * float2(2.0 * hw, proj.w) - float2(hw, proj.w * 0.5), 0.0));
    col += lamp * exp(-d / 26.0) * 0.07 * lampOn * (1.0 - inside);

    // The beam, seen from behind the projector: from the lens to the slide's lower corners.
    float bottom = proj.y + proj.w;
    float span = lens.y - bottom;
    float s = clamp((lens.y - p.y) / span, 0.0, 1.0);
    if (p.y < lens.y && p.y > bottom - proj.w * 0.5) {
        float bw = mix(11.0, proj.z * 0.5 * key.y, s);
        if (p.y < bottom) bw = proj.z * 0.5 * mix(key.x, key.y, clamp((p.y - proj.y) / proj.w, 0.0, 1.0));
        float dx = abs(p.x - lens.x) / bw;
        float edge = 1.0 - smoothstep(0.72, 1.0, dx);
        float shafts = 0.72 + 0.28 * SlideProjectorGalleryNoise(float2((p.x - lens.x) / max(bw, 1.0) * 9.0, s * 1.5));
        float om = 1.0 - s;
        float dens = 0.2 * om * sqrt(om) + 0.03;
        dens *= 1.0 - 0.8 * smoothstep(0.0, proj.w * 0.5, bottom - p.y);
        dens *= p.y < bottom ? 0.35 : 1.0;
        col += lamp * dens * edge * shafts * lampOn;
    }
    // The lens itself glows.
    float2 dl = p - lens;
    col += lamp * exp(-dot(dl, dl) / 260.0) * 0.55 * lampOn;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
