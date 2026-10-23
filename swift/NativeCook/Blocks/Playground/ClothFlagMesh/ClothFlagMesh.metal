#include <metal_stdlib>
using namespace metal;

// One point of the banner's mesh, as the engine writes it: where it is on screen (pt), the flat
// banner point it shows (pt), its shade from its normal and its satin sheen.
struct ClothFlagMeshVertex {
    float2 position;
    float2 flat;
    float4 shade;
    float4 sheen;
};

struct ClothFlagMeshVaried {
    float4 position [[position]];
    float2 flat;
    float4 shade;
    float4 sheen;
};

vertex ClothFlagMeshVaried clothFlagMeshVertex(uint id [[vertex_id]],
                                               device const ClothFlagMeshVertex *points [[buffer(0)]],
                                               constant float2 &viewSize [[buffer(1)]]) {
    ClothFlagMeshVertex p = points[id];
    ClothFlagMeshVaried out;
    float2 ndc = p.position / viewSize * 2.0 - 1.0;
    out.position = float4(ndc.x, -ndc.y, 0.0, 1.0);
    out.flat = p.flat;
    out.shade = p.shade;
    out.sheen = p.sheen;
    return out;
}

static float clothFlagMeshFill(float d) {
    return 1.0 - smoothstep(-0.6, 0.6, d);
}

static float clothFlagMeshHash(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

// GLSL's mod: the result takes the sign of y.
static float clothFlagMeshMod(float x, float y) {
    return x - y * floor(x / y);
}

// The banner's cloth: a plain weave of fine threads under an invented device — a gold sun of
// sixteen rays in a ring over three waves, on an oxblood field with a gold border and an ivory
// hoist. Evaluated in the flat banner's own coordinates (pt), which the mesh maps onto the folds;
// every thread's crossing is shaded so the weave catches the light.
static float3 clothFlagMeshWeave(float2 p, float2 size) {
    const float3 FIELD = float3(0.47, 0.09, 0.13);
    const float3 FIELD_DEEP = float3(0.36, 0.06, 0.10);
    const float3 GOLD = float3(0.93, 0.74, 0.38);
    const float3 IVORY = float3(0.94, 0.90, 0.81);

    float hoist = 50.0;
    float3 col = mix(FIELD, FIELD_DEEP, smoothstep(0.0, size.y, p.y) * 0.6);

    // Gold border inside the field.
    float inset = 9.0;
    float2 q = p - float2(hoist, 0.0);
    float2 box = float2(size.x - hoist, size.y);
    float edge = min(min(q.y, box.y - q.y), box.x - q.x);
    float border = clothFlagMeshFill(abs(edge - inset) - 1.6);
    col = mix(col, GOLD, border);

    // The device: a sun in a ring over three waves.
    float2 c = float2(hoist + (size.x - hoist) * 0.5, size.y * 0.42);
    float2 d = p - c;
    float r = length(d);
    float a = atan2(d.y, d.x);
    float disc = clothFlagMeshFill(r - 19.0);
    float ring = clothFlagMeshFill(abs(r - 27.0) - 1.4);
    float sector = 6.2831853 / 16.0;
    float k = floor(a / sector + 0.5);
    float local = a - k * sector;
    float longRay = clothFlagMeshMod(k, 2.0) == 0.0 ? 1.0 : 0.0;
    float tip = 34.0 + mix(9.0, 17.0, longRay);
    float t = clamp((r - 33.0) / (tip - 33.0), 0.0, 1.0);
    float halfWidth = mix(3.2, 0.3, t);
    float rays = clothFlagMeshFill(abs(local) * r - halfWidth) * clothFlagMeshFill(33.0 - r) * clothFlagMeshFill(r - tip);
    float waves = 0.0;
    for (int i = 0; i < 3; i++) {
        float y0 = c.y + 58.0 + float(i) * 11.0;
        float wy = y0 + 3.2 * sin((p.x - c.x) * 0.16 + float(i) * 1.1);
        float span = clothFlagMeshFill(abs(p.x - c.x) - (60.0 - float(i) * 12.0));
        waves = max(waves, clothFlagMeshFill(abs(p.y - wy) - 1.7) * span);
    }
    float emblem = max(max(disc, ring), max(rays, waves));
    col = mix(col, GOLD, emblem);

    // The hoist: ivory with two oxblood stripes, and a gold seam where it meets the field.
    float sleeve = clothFlagMeshFill(p.x - hoist);
    float3 hoistCol = IVORY;
    float stripes = max(clothFlagMeshFill(abs(p.x - 14.0) - 2.0), clothFlagMeshFill(abs(p.x - 36.0) - 2.0));
    hoistCol = mix(hoistCol, FIELD, stripes);
    col = mix(col, hoistCol, sleeve);
    col = mix(col, GOLD, clothFlagMeshFill(abs(p.x - hoist) - 1.2));

    // Plain weave: threads 2 pt apart, warp and weft over and under in turn.
    float pitch = 2.0;
    float2 g = p / pitch;
    float2 cell = floor(g);
    float2 f = fract(g) - 0.5;
    bool warpUp = clothFlagMeshMod(cell.x + cell.y, 2.0) < 1.0;
    float across = warpUp ? f.x : f.y;
    float along = warpUp ? f.y : f.x;
    float rounded = 1.0 - 4.0 * across * across;
    float bend = 1.0 - 1.6 * along * along;
    float slub = warpUp ? clothFlagMeshHash(cell.x) : clothFlagMeshHash(cell.y + 31.0);
    float shade = 0.72 + 0.3 * rounded * bend + 0.06 * (slub - 0.5);
    // The warp is a touch darker than the weft, like shot silk.
    shade *= warpUp ? 0.95 : 1.03;
    return col * shade;
}

// The cloth: the weave multiplied by the shade each point takes from its normal.
fragment float4 clothFlagMeshCloth(ClothFlagMeshVaried in [[stage_in]], constant float2 &flagSize [[buffer(0)]]) {
    return float4(clothFlagMeshWeave(in.flat, flagSize) * in.shade.rgb, 1.0);
}

// The satin sheen, screened over the cloth by the pipeline's blending.
fragment float4 clothFlagMeshSheen(ClothFlagMeshVaried in [[stage_in]]) {
    return float4(in.sheen.rgb, 0.0);
}
