#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Stamp Diary Calendar: one effect draws every stamp, ported from shaders.ts (SkSL). The perforated
// paper, the page curl, the gummed back, the shadow the curl throws, a sheen while it flutters and
// the postmark ink. The effect is a SwiftUI colorEffect on a view that only bounds the stamp: it
// ignores the incoming colour, maps the screen point back into the stamp's own centred points
// (the inverse of the stamp's translate / shear / tilt / scale matrix, passed as three rows) and
// samples the art and the postmark as textures.
//
//   view   x, y: origin of the view in screen points minus the content offset; z: points per
//          reference point; w: unused
//   hr0..2 rows of the 3x3 matrix that maps (reference x, y, 1) to the stamp's centred points
//   box    w, h, aa (antialias width in stamp points), dim (colour multiplier, the collage dims)
//   crop   x, y, w, h: the part of the art the stamp shows (cover fit), in image pixels
//   holes  across, down, radius (share of the hole pitch), shadow strength under the curl
//   curl   nx, ny, a, r: the paper rolls around the axis dot(p, n) = a with radius r (r = 0: flat)
//   sheen  position along the diagonal, amount, ink wet 0..1, ink on 0/1
//   ink    centre x, y (stamp points), postmark pixels per stamp point, rotation (radians)
//   inkfx  spread 0..1, threshold of the soft field (lower = bleeds wider), postmark ring x, y (px)
//   bounds half width and half height (stamp points) outside which nothing is drawn
//
// The output is sRGB (SwiftUI's working space), so the P3 conversions of the Skia version are gone.

struct SDCUniforms {
    float4 box;
    float4 crop;
    float4 holes;
    float4 curl;
    float4 sheen;
    float4 ink;
    float4 inkfx;
};

constant float kPi = 3.14159265;

static float sdcHash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

static float sdcNoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = sdcHash(i);
    float b = sdcHash(i + float2(1.0, 0.0));
    float c = sdcHash(i + float2(0.0, 1.0));
    float d = sdcHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Paper coverage of a point of the flat stamp: the rectangle minus half-round holes on its edges.
static float sdcPaper(float2 q, thread const SDCUniforms &u) {
    float w = u.box.x;
    float h = u.box.y;
    float aa = u.box.z;
    float2 hw = float2(w, h) * 0.5;
    float2 e = hw - abs(q);
    float edge = min(e.x, e.y);
    if (edge < -aa) return 0.0;
    float px = w / u.holes.x;
    float py = h / u.holes.y;
    float rad = u.holes.z * min(px, py);
    float2 t = q + hw;
    float hx = (floor(t.x / px) + 0.5) * px;
    float hy = (floor(t.y / py) + 0.5) * py;
    float d1 = length(float2(t.x - hx, t.y));
    float d2 = length(float2(t.x - hx, t.y - h));
    float d3 = length(float2(t.x, t.y - hy));
    float d4 = length(float2(t.x - w, t.y - hy));
    float dn = min(min(d1, d2), min(d3, d4));
    return smoothstep(-aa, aa, edge) * smoothstep(rad - aa, rad + aa, dn);
}

static float4 sdcArtAt(float2 q, thread const SDCUniforms &u, texture2d<half> art) {
    constexpr sampler smp(filter::linear, address::clamp_to_edge);
    float2 uv = clamp(q / u.box.xy + 0.5, 0.0, 1.0);
    float2 px = u.crop.xy + uv * u.crop.zw;
    return float4(art.sample(smp, px / float2(art.get_width(), art.get_height())));
}

// Postmark ink density at a point of the flat stamp (0 = clean paper).
static float sdcInkAt(float2 q, thread const SDCUniforms &u, texture2d<half> mark) {
    if (u.sheen.w < 0.5) return 0.0;
    constexpr sampler smp(filter::linear, address::clamp_to_zero);
    float2 d = q - u.ink.xy;
    float cs = cos(u.ink.w);
    float sn = sin(u.ink.w);
    float2 pm = float2(cs * d.x - sn * d.y, sn * d.x + cs * d.y) * u.ink.z + u.inkfx.zw;
    float field = float(mark.sample(smp, pm / float2(mark.get_width(), mark.get_height())).a);
    float grain = sdcNoise(q * 1.7) * 0.6 + sdcNoise(q * 5.3) * 0.4;
    float th = u.inkfx.y + (grain - 0.5) * 0.22;
    float dens = smoothstep(th - 0.07, th + 0.07, field);
    // The ink reaches out from the ring with a ragged front.
    float reach = u.inkfx.x * 1.15 * u.box.x;
    float rr = length(d) + (sdcNoise(q * 0.35) - 0.5) * 0.22 * u.box.x;
    dens *= 1.0 - smoothstep(reach - 0.07 * u.box.x, reach, rr);
    // A rubber stamp leaves voids where the paper's tooth did not take ink.
    dens *= 0.7 + 0.3 * smoothstep(0.22, 0.62, sdcNoise(q * 9.0));
    return dens;
}

// Front of the paper: the art, shaded, with the ink pressed into it.
static float3 sdcFront(float2 q, float shade, thread const SDCUniforms &u, texture2d<half> art, texture2d<half> mark) {
    float4 c = sdcArtAt(q, u, art);
    float3 rgb = c.a > 0.001 ? c.rgb / c.a : float3(0.0);
    float dens = sdcInkAt(q, u, mark);
    if (dens > 0.0) {
        float3 dry = float3(0.1300, 0.1400, 0.2000);
        float3 wet = float3(0.1600, 0.2000, 0.3800);
        float3 tint = mix(dry, wet, u.sheen.z);
        rgb = mix(rgb, rgb * tint, dens * 0.93);
        rgb += u.sheen.z * dens * 0.08 * sdcNoise(q * 2.3);
    }
    return rgb * shade;
}

// Back of the paper: warm gummed stock, a little of the picture showing through.
static float3 sdcBack(float2 q, float shade, float gloss, thread const SDCUniforms &u, texture2d<half> art) {
    float3 stock = float3(0.9300, 0.9050, 0.8500);
    float fibre = sdcNoise(q * float2(0.9, 2.6)) * 0.05 + sdcNoise(q * 7.0) * 0.03;
    float4 c = sdcArtAt(q, u, art);
    float3 rgb = c.a > 0.001 ? c.rgb / c.a : stock;
    float lum = dot(rgb, float3(0.3, 0.59, 0.11));
    float3 col = stock * (0.95 + fibre) * (0.93 + 0.07 * lum);
    return col * shade + gloss;
}

// Coverage of the lifted paper (rolled or folded back) at a screen point.
static float sdcLifted(float2 p, thread const SDCUniforms &u) {
    float r = u.curl.w;
    float x = dot(p, u.curl.xy) - u.curl.z;
    if (x > r) return 0.0;
    if (x >= 0.0) {
        float th = asin(clamp(x / r, 0.0, 1.0));
        float a1 = sdcPaper(p + u.curl.xy * (r * (kPi - th) - x), u);
        float a2 = sdcPaper(p + u.curl.xy * (r * th - x), u);
        return max(a1, a2);
    }
    return sdcPaper(p + u.curl.xy * (kPi * r - 2.0 * x), u);
}

static half4 sdcShade(float2 p, thread const SDCUniforms &u, texture2d<half> art, texture2d<half> mark) {
    float dim = u.box.w;
    float r = u.curl.w;
    float3 lightDir = normalize(float3(-0.45, -0.75, 1.1));
    // Flat stamp: one layer.
    if (r <= 0.0) {
        float a = sdcPaper(p, u);
        if (a <= 0.0) return half4(0.0h);
        float3 rgb = sdcFront(p, 1.0, u, art, mark);
        float band = dot(p / u.box.xy, float2(0.7071, 0.7071)) - u.sheen.x + 0.5;
        rgb += u.sheen.y * exp(-band * band / 0.025);
        return half4(half3(rgb * dim * a), half(a));
    }
    float2 n = u.curl.xy;
    float x = dot(p, n) - u.curl.z;
    float lx = dot(lightDir.xy, n);
    float lz = lightDir.z;
    float4 top = float4(0.0);
    float4 under = float4(0.0);
    bool underFlat = false;
    if (x > r) {
        // Beyond the roll: the paper that was here has been lifted away.
    } else if (x >= 0.0) {
        float s = clamp(x / r, 0.0, 1.0);
        float t2 = kPi - asin(s);
        float2 q2 = p + n * (r * t2 - x);
        float a2 = sdcPaper(q2, u);
        if (a2 > 0.0) {
            float dif = sin(t2) * lx - cos(t2) * lz;
            float3 hv = normalize(lightDir + float3(0.0, 0.0, 1.0));
            float spec = pow(max(0.0, sin(t2) * dot(hv.xy, n) - cos(t2) * hv.z), 28.0) * 0.35;
            top = float4(sdcBack(q2, 0.5 + 0.5 * max(dif, 0.0) / lz, spec, u, art), a2);
        }
        float t1 = asin(s);
        float2 q1 = p + n * (r * t1 - x);
        float a1 = sdcPaper(q1, u);
        if (a1 > 0.0) {
            float dif = -sin(t1) * lx + cos(t1) * lz;
            under = float4(sdcFront(q1, 0.42 + 0.58 * max(dif, 0.0) / lz, u, art, mark), a1);
        }
    } else {
        float2 q3 = p + n * (kPi * r - 2.0 * x);
        float a3 = sdcPaper(q3, u);
        if (a3 > 0.0) top = float4(sdcBack(q3, 0.97, 0.0, u, art), a3);
        float a0 = sdcPaper(p, u);
        if (a0 > 0.0) {
            under = float4(sdcFront(p, 1.0, u, art, mark), a0);
            underFlat = true;
        }
    }
    // The lifted paper throws a soft shadow down and to the right, onto the stamp and the page.
    float sh = 0.0;
    if (u.holes.w > 0.0) {
        float2 o = normalize(float2(0.45, 0.75)) * max(r, 2.0) * 0.9;
        sh = (sdcLifted(p - o * 0.35, u) + sdcLifted(p - o * 0.75, u) + sdcLifted(p - o * 1.2, u) + sdcLifted(p - o * 1.7, u)) * 0.25;
        if (underFlat) sh = max(sh, (1.0 - smoothstep(0.0, 0.7 * r, -x)) * 0.6);
        sh *= u.holes.w;
    }
    float3 rgb = under.rgb * (1.0 - sh) * under.a;
    float alpha = under.a;
    // Shadow on the page where nothing covers it.
    float pageShadow = sh * 0.75 * (1.0 - under.a);
    alpha += pageShadow;
    // The rolled or folded paper on top.
    rgb = top.rgb * top.a + rgb * (1.0 - top.a);
    alpha = top.a + alpha * (1.0 - top.a);
    return half4(half3(rgb * dim), half(alpha));
}

// Screen point of the view -> point of the stamp, through the inverse of its matrix.
static float2 sdcLocal(float2 position, float4 view, float3 h0, float3 h1, float3 h2) {
    float2 ref = (position + view.xy) / view.z;
    float3 r = float3(ref, 1.0);
    float3 q = float3(dot(h0, r), dot(h1, r), dot(h2, r));
    return q.xy / q.z;
}

[[ stitchable ]] half4 stampDiaryCalendarStamp(
    float2 position, half4 currentColor,
    float4 view, float3 h0, float3 h1, float3 h2,
    float4 box, float4 crop, float4 holes, float4 curl, float4 sheen, float4 ink, float4 inkfx,
    float2 bounds,
    texture2d<half> art, texture2d<half> mark
) {
    SDCUniforms u;
    u.box = box; u.crop = crop; u.holes = holes; u.curl = curl; u.sheen = sheen; u.ink = ink; u.inkfx = inkfx;
    float2 p = sdcLocal(position, view, h0, h1, h2);
    if (abs(p.x) > bounds.x || abs(p.y) > bounds.y) return half4(0.0h);
    return sdcShade(p, u, art, mark);
}

// Soft black rectangle (a Gaussian-blurred box, sigma = box.z) under a moving stamp.
// box: w, h, sigma, opacity.
[[ stitchable ]] half4 stampDiaryCalendarShadow(
    float2 position, half4 currentColor,
    float4 view, float3 h0, float3 h1, float3 h2, float4 box
) {
    float2 p = sdcLocal(position, view, h0, h1, h2);
    float sigma = max(box.z, 0.01);
    float2 hw = box.xy * 0.5;
    float2 z0 = (p + hw) / sigma;
    float2 z1 = (p - hw) / sigma;
    float2 cdf0 = 1.0 / (1.0 + exp(-1.702 * z0));
    float2 cdf1 = 1.0 / (1.0 + exp(-1.702 * z1));
    float2 cover = cdf0 - cdf1;
    float a = cover.x * cover.y * box.w;
    return half4(0.0h, 0.0h, 0.0h, half(a));
}
