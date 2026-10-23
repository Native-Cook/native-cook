#include <metal_stdlib>
using namespace metal;

// A cylinder page curl, drawn per pixel (curl-shader.ts). The grabbed point `grab` of the sheet lies at `tip` on
// screen. The fold is a cylinder whose axis runs perpendicular to grab→tip; paper past the axis wraps around it and,
// past half a turn, lies flat on top, back side up. For every screen pixel the shader walks back to the paper point
// that lands there (top layer first): the flat back, then the back and front of the roll, then the page itself,
// and where no paper of this sheet is left, the page beneath with the roll's cast shadow.
// The roll's radius grows along the fold (r0 + r1·t), so the sheet curls like a cone, as iOS's does.
// p0…p3 are the four pages (textures of their views); frontIdx is the turning sheet, underIdx the one below.

struct PageCurlJournalCurlUniforms {
    float sizeX, sizeY;
    float grabX, grabY;
    float tipX, tipY;
    float r0, r1, rmin;
    float frontIdx, underIdx;
    float shA, shW, eA, eW;
    float amb1, amb2, g2, bg, ba;
    float grain, edgeInk, px, fade;
    float clearUnder; // 1: a riffle sheet over another canvas, the page beneath is transparent
    float spec;
};

struct PageCurlJournalFoilUniforms {
    float dirX, dirY;
    float center, width, amount;
    float rectX, rectY, rectWidth, rectHeight;
    float sizeX, sizeY;
    float px;
};

struct PageCurlJournalVertexOut {
    float4 position [[position]];
};

constant float PCJ_PI = 3.14159265;

// One triangle that covers the screen.
vertex PageCurlJournalVertexOut pageCurlJournalCurlVertex(uint vid [[vertex_id]]) {
    float2 corners[3] = { float2(-1.0, -1.0), float2(3.0, -1.0), float2(-1.0, 3.0) };
    PageCurlJournalVertexOut out;
    out.position = float4(corners[vid], 0.0, 1.0);
    return out;
}

constexpr sampler pcjPage(coord::normalized, address::clamp_to_zero, filter::linear);

struct PageCurlJournalPages {
    texture2d<float> p0;
    texture2d<float> p1;
    texture2d<float> p2;
    texture2d<float> p3;
    float2 size;

    float4 at(float idx, float2 q) const {
        float2 uv = q / size;
        if (idx < 0.5) { return p0.sample(pcjPage, uv, level(0)); }
        if (idx < 1.5) { return p1.sample(pcjPage, uv, level(0)); }
        if (idx < 2.5) { return p2.sample(pcjPage, uv, level(0)); }
        return p3.sample(pcjPage, uv, level(0));
    }
};

// Signed distance into the sheet (negative outside it).
static float pcjEdgeDist(float2 q, float2 size) {
    return min(min(q.x, size.x - q.x), min(q.y, size.y - q.y));
}

static float pcjHash21(float2 q) {
    return fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453);
}

// The back of the sheet: off-white paper with the print showing through, mirrored, and a fine grain.
static float3 pcjBackAt(float2 q, constant PageCurlJournalCurlUniforms &u, PageCurlJournalPages pages) {
    float4 f = pages.at(u.frontIdx, clamp(q, float2(0.0), pages.size));
    float lum = (f.r + f.g + f.b) / 3.0;
    float v = u.bg * (1.0 - u.ba * (1.0 - lum));
    v *= 1.0 + u.grain * (pcjHash21(floor(q * u.px)) - 0.5);
    return float3(v);
}

// A soft paper sheen where the roll faces the key light (top-left), around 0.78 of half a turn.
static float pcjSheen(float th, float spec) {
    float k = (th - 0.78 * PCJ_PI) / 0.12;
    return spec * exp(-k * k);
}

fragment float4 pageCurlJournalCurlFragment(
    PageCurlJournalVertexOut in [[stage_in]],
    constant PageCurlJournalCurlUniforms &u [[buffer(0)]],
    texture2d<float> p0 [[texture(0)]],
    texture2d<float> p1 [[texture(1)]],
    texture2d<float> p2 [[texture(2)]],
    texture2d<float> p3 [[texture(3)]]
) {
    float2 size = float2(u.sizeX, u.sizeY);
    float2 grab = float2(u.grabX, u.grabY);
    float2 tip = float2(u.tipX, u.tipY);
    PageCurlJournalPages pages = { p0, p1, p2, p3, size };
    // Pixels to points: the canvas works in points.
    float2 p = in.position.xy / u.px;

    float4 underC = pages.at(u.underIdx, p);
    if (u.fade >= 0.0) {
        return float4(mix(pages.at(u.frontIdx, p).rgb, underC.rgb, u.fade), 1.0);
    }
    float2 v = grab - tip;
    float D = length(v);
    if (D < 0.25) { return pages.at(u.frontIdx, p); }
    float2 d = v / D;
    float2 n = float2(-d.y, d.x);
    float Rc = max(u.rmin, u.r0);
    // The axis sits where the grabbed point, wrapped half a turn and laid flat, lands on the tip.
    float2 a = grab - (D + PCJ_PI * Rc) * 0.5 * d;
    float x = dot(p - a, d);
    float t = dot(p - a, n);
    float R = clamp(u.r0 + u.r1 * t, u.rmin, 400.0);

    // The page beneath, premultiplied: opaque, or (riffle) transparent with only the shadow as alpha.
    bool clearUnder = u.clearUnder > 0.5;
    float4 under = clearUnder ? float4(0.0) : float4(underC.rgb, 1.0);

    if (x > R) {
        // Past the roll: the page beneath, darkened by the roll's soft cast shadow.
        float s = u.shA * exp(-(x - R) / u.shW);
        return clearUnder ? float4(0.0, 0.0, 0.0, s) : float4(under.rgb * (1.0 - s), 1.0);
    }

    if (x <= 0.0) {
        // The flat part of the sheet, possibly covered by its own flat back lying over it.
        float2 qb = p + (PCJ_PI * R - 2.0 * x) * d;
        float db = pcjEdgeDist(qb, size);
        float3 f = pages.at(u.frontIdx, p).rgb;
        if (db < 0.0) { f *= 1.0 - u.eA * exp(db / u.eW); }
        float3 b = pcjBackAt(qb, u, pages);
        // The cut edge of the sheet: a hairline of thickness.
        b *= 1.0 - u.edgeInk * (1.0 - smoothstep(0.0, 0.9, db));
        float cov = clamp(db * u.px + 0.5, 0.0, 1.0);
        return float4(mix(f, b, cov), 1.0);
    }

    // Inside the roll: the rising front (lower), then the back coming over (upper).
    float th1 = asin(clamp(x / R, 0.0, 1.0));
    float th2 = PCJ_PI - th1;
    float2 q1 = p + (R * th1 - x) * d;
    float2 q2 = p + (R * th2 - x) * d;
    float4 shadowed = clearUnder ? float4(0.0, 0.0, 0.0, u.shA) : float4(under.rgb * (1.0 - u.shA), 1.0);
    float4 col = shadowed;
    float c1 = clamp(pcjEdgeDist(q1, size) * u.px + 0.5, 0.0, 1.0);
    float3 f1 = pages.at(u.frontIdx, q1).rgb * (u.amb1 + (1.0 - u.amb1) * cos(th1));
    col = mix(col, float4(f1, 1.0), c1);
    float c2 = clamp(pcjEdgeDist(q2, size) * u.px + 0.5, 0.0, 1.0);
    float3 b2 = pcjBackAt(q2, u, pages) * (u.amb2 + (1.0 - u.amb2) * pow(abs(cos(th2)), u.g2));
    b2 = min(float3(1.0), b2 + pcjSheen(th2, u.spec));
    col = mix(col, float4(b2, 1.0), c2);
    float cr = clamp((R - x) * u.px + 0.5, 0.0, 1.0);
    return mix(shadowed, col, cr);
}

// Foil on the cover (foil-shader.ts): a silver light band that passes over the cover's own ink. The mask is the cover
// page (1 − luminance), so the light sits exactly on the glyphs and the sparkle, never beside them.
// The quad covers the foil canvas; p is local to it, as in the Skia canvas.
vertex PageCurlJournalVertexOut pageCurlJournalFoilVertex(uint vid [[vertex_id]], constant PageCurlJournalFoilUniforms &u [[buffer(0)]]) {
    float2 corners[4] = { float2(0.0, 0.0), float2(1.0, 0.0), float2(0.0, 1.0), float2(1.0, 1.0) };
    float2 size = float2(u.sizeX, u.sizeY);
    float2 point = float2(u.rectX, u.rectY) + corners[vid] * float2(u.rectWidth, u.rectHeight);
    float2 ndc = float2(point.x / size.x * 2.0 - 1.0, 1.0 - point.y / size.y * 2.0);
    PageCurlJournalVertexOut out;
    out.position = float4(ndc, 0.0, 1.0);
    return out;
}

fragment float4 pageCurlJournalFoilFragment(
    PageCurlJournalVertexOut in [[stage_in]],
    constant PageCurlJournalFoilUniforms &u [[buffer(0)]],
    texture2d<float> cover [[texture(0)]]
) {
    float2 size = float2(u.sizeX, u.sizeY);
    float2 screen = in.position.xy / u.px;
    float2 p = screen - float2(u.rectX, u.rectY);
    float4 c = cover.sample(pcjPage, screen / size, level(0));
    float lum = (c.r + c.g + c.b) / 3.0;
    float mask = clamp((1.0 - lum) * 1.4, 0.0, 1.0);
    float k = (dot(p, float2(u.dirX, u.dirY)) - u.center) / u.width;
    float band = exp(-k * k);
    // A thin warm-to-cool split across the band reads as foil rather than a white smear.
    float3 tint = mix(float3(1.0, 0.93, 0.82), float3(0.84, 0.92, 1.0), clamp(k * 0.5 + 0.5, 0.0, 1.0));
    float a = u.amount * mask * band;
    return float4(tint * a, a);
}
