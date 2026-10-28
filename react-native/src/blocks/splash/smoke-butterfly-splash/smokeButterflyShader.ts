// SkSL port of SmokeButterflySplash.metal (same functions and numbers).
export const SMOKE_BUTTERFLY_SHADER = `uniform float uP3;
uniform float4 uFrame;
uniform float4 uA;
uniform float4 uB;
uniform float4 uTouch;
uniform float4 uDrag;

float SBS_hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float SBS_noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = SBS_hash(i);
    float b = SBS_hash(i + float2(1.0, 0.0));
    float c = SBS_hash(i + float2(0.0, 1.0));
    float d = SBS_hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float SBS_fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * SBS_noise(p);
        p = float2(1.6 * p.x - 1.2 * p.y, 1.2 * p.x + 1.6 * p.y) + float2(3.1, 1.7);
        a *= 0.5;
    }
    return v / 0.9375;
}

float SBS_ellipse(float2 p, float2 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 0.0001);
}

float SBS_segment(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}

// Local frame of a wing: x along its axis from the root, y across.
float2 SBS_local(float2 p, float2 root, float angle) {
    float2 d = float2(cos(angle), sin(angle));
    float2 r = p - root;
    return float2(dot(r, d), dot(r, float2(-d.y, d.x)));
}

float SBS_smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float SBS_triangle(float2 p, float2 p0, float2 p1, float2 p2) {
    float2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
    float2 v0 = p - p0, v1 = p - p1, v2 = p - p2;
    float2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    float2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    float2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    float2 d = min(min(float2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                       float2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                       float2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

// Forewing: a rounded triangle from the root to the apex and back along a bulging outer margin.
float SBS_fore(float2 w) {
    float tri = SBS_triangle(w, float2(0.05, -0.10), float2(0.92, -0.60), float2(0.66, 0.04)) - 0.07;
    float bulge = SBS_ellipse(w - float2(0.70, -0.27), float2(0.26, 0.31));
    return SBS_smin(tri, bulge, 0.10);
}

// Hindwing: a round lobe with a scalloped outer margin.
float SBS_hind(float2 w) {
    float2 l = SBS_local(w, float2(0.04, 0.02), 0.80) - float2(0.34, 0.0);
    float d = SBS_ellipse(l, float2(0.37, 0.31));
    float a = atan(l.y, l.x);
    return d - 0.014 * cos(a * 11.0) * smoothstep(-0.2, 0.2, l.x);
}

// Mirrored, flapping wing coordinates: the wings narrow and their tips lift on a beat.
float2 SBS_wingSpace(float2 q, float flap) {
    float k = 1.0 - 0.62 * flap;
    float x = abs(q.x) / k;
    return float2(x, q.y + 0.10 * flap * x);
}

float SBS_wings(float2 q, float flap) {
    float2 w = SBS_wingSpace(q, flap);
    float k = 1.0 - 0.62 * flap;
    return SBS_smin(SBS_fore(w), SBS_hind(w), 0.05) * k;
}

float SBS_body(float2 q) {
    float t = clamp((q.y + 0.22) / 0.56, 0.0, 1.0);
    float body = SBS_segment(q, float2(0.0, -0.20), float2(0.0, 0.34)) - mix(0.040, 0.016, t);
    body = min(body, length(q - float2(0.0, -0.25)) - 0.042);
    float2 a = float2(abs(q.x), q.y);
    float antenna = SBS_segment(a, float2(0.012, -0.28), float2(0.13, -0.52)) - 0.005;
    antenna = min(antenna, length(a - float2(0.13, -0.52)) - 0.011);
    return min(body, antenna);
}

float SBS_rays(float2 w, float2 root, float count, float start) {
    float2 r = w - root;
    float rr = length(r);
    float a = atan(r.y, r.x);
    float cell = fract(a * count / 6.2831853 + 0.5) - 0.5;
    float dist = abs(cell) * 6.2831853 / count * rr;
    return (1.0 - smoothstep(0.0025, 0.0075, dist)) * smoothstep(start, start + 0.10, rr);
}

float3 SBS_scene(float2 pos, float4 frame, float4 a, float4 b, float4 touch, float4 drag) {
    float2 size = frame.xy;
    float sc = min(size.x / 393.0, size.y / 852.0);
    float2 p = (pos - (size - float2(393.0, 852.0) * sc) * 0.5) / sc;
    float theta = frame.z;
    float light = frame.w;
    float rise = a.x, column = a.y, halo = a.z, form = a.w;
    float spread = b.x, flap = b.y, veins = b.z, pull = b.w;
    float2 finger = touch.xy;
    float stir = touch.z;
    float ember = touch.w;
    float2 vel = drag.xy;
    float2 pulled = drag.zw;

    // Stage: near-black navy with a soft blue light from the top and a vignette.
    float3 col = mix(float3(0.008, 0.016, 0.039), float3(0.024, 0.071, 0.149), clamp(1.0 - p.y / 760.0, 0.0, 1.0) * 0.8);
    float2 lp = (p - float2(196.5, -20.0)) / float2(125.0, 175.0);
    col += float3(0.18, 0.42, 1.0) * exp(-dot(lp, lp)) * light * 0.85;
    float2 cp = (p - float2(196.5, 140.0)) / float2(170.0, 320.0);
    col += float3(0.04, 0.11, 0.32) * exp(-dot(cp, cp)) * light * 0.55;
    float2 vp = (p - float2(196.5, 426.0)) / float2(330.0, 560.0);
    col *= 1.0 - 0.35 * smoothstep(0.6, 1.3, length(vp));

    // The finger stirs: the sample space turns around it and is pushed along its velocity.
    float2 fd = p - finger;
    float g = exp(-dot(fd, fd) / (110.0 * 110.0)) * stir;
    float ang = 2.2 * g;
    float2 ps = finger + float2(cos(ang) * fd.x - sin(ang) * fd.y, sin(ang) * fd.x + cos(ang) * fd.y);
    ps -= vel * 70.0 * g;
    float2 q = (ps - float2(196.5, 360.0)) / 150.0;

    // Looping smoke: every noise offset travels on a circle once (or twice) per loop.
    float2 o1 = 0.9 * float2(cos(theta), sin(theta));
    float2 o2 = 0.7 * float2(cos(2.0 * theta + 1.3), sin(2.0 * theta + 1.3));
    float2 qn = q * 1.7 + float2(0.0, 0.5 * spread);
    float2 w = float2(SBS_fbm(qn + o1), SBS_fbm(qn + o2 + float2(5.2, 1.3)));
    float warp = 1.0 + 0.9 * spread + 0.9 * flap + 1.2 * stir;
    float dn = SBS_fbm(q * 2.3 + (w - 0.5) * 2.2 * warp + o1 * 0.6 + float2(1.7, 9.2));
    float ridge = 1.0 - abs(2.0 * SBS_noise(q * 5.5 + w * 3.0 - o2) - 1.0);

    // The rising wisp: a narrow column whose tip curls in the warp.
    float2 qw = q + (w - 0.5) * 0.7;
    float tip = mix(1.15, -0.05, rise);
    float xc = 0.09 * sin(qw.y * 2.8 - theta) * smoothstep(tip + 1.2, tip, qw.y);
    float wdt = 0.05 + 0.08 * rise + 0.24 * smoothstep(tip + 0.9, tip, qw.y);
    float across = (qw.x - xc) / wdt;
    float along = smoothstep(tip - 0.30, tip + 0.10, qw.y) * (1.0 - smoothstep(1.6, 2.4, qw.y));
    float colW = exp(-across * across) * along * max(column, 0.55 * ember);

    // The halo: smoke in the butterfly's shape that billows out and up as it dissolves.
    float2 qh = (q + (w - 0.5) * (0.60 + 0.70 * spread)) / (1.0 + 0.45 * spread) + float2(0.0, 0.22 * spread);
    float hs = min(SBS_wings(qh, flap * 0.6), SBS_body(qh) + 0.05);
    float haloW = smoothstep(0.20 + 0.10 * smoothstep(-0.1, -0.6, qh.y) + 0.32 * spread, -0.06, hs) * halo * (1.0 - 0.30 * spread);
    float region = max(colW, haloW) + 0.5 * g * max(halo, 0.2);

    // The butterfly condenses where the noise passes a falling threshold, and pulls toward the finger.
    float gw = exp(-dot(fd, fd) / (170.0 * 170.0));
    float gn = exp(-dot(fd, fd) / (120.0 * 120.0));
    float2 qs = q + (w - 0.5) * 0.9 * (1.0 - form) - pulled / 150.0 * pull * gw * 0.35;
    float formEff = form * (1.0 - pull * (0.30 + 0.70 * gn));
    float dd = 0.6 * dn + 0.4 * SBS_noise(q * 9.0 + o1 * 2.0);
    float thr = mix(1.02, -0.15, formEff);
    float vis = smoothstep(thr - 0.06, thr + 0.03, dd);
    float fz = (dd - thr) / 0.022;
    float front = exp(-fz * fz);

    float ws = SBS_wings(qs, flap);
    float bs = SBS_body(qs);
    float aa = 0.0067;
    float wingMask = 1.0 - smoothstep(-aa, aa, ws);
    float bodyMask = 1.0 - smoothstep(-aa, aa, bs);

    float dens = region * smoothstep(0.22, 0.88, dn + 0.18 * region);
    dens += region * pow(ridge, 8.0) * 0.08;
    dens *= 1.0 - 0.75 * max(wingMask, bodyMask) * vis;
    float3 smoke = mix(float3(0.04, 0.30, 0.62), float3(0.24, 0.80, 1.0), smoothstep(0.15, 0.65, dens)) * dens;
    smoke += float3(0.80, 0.98, 1.0) * smoothstep(0.60, 1.0, dens) * 0.35;
    col = col * (1.0 - 0.25 * min(dens, 1.0)) + smoke;

    // Glow around the wings.
    col += float3(0.10, 0.45, 1.0) * exp(-max(ws, 0.0) * 7.0) * 0.30 * formEff;

    if (wingMask > 0.001 || bodyMask > 0.001) {
        float2 wl = SBS_wingSpace(qs, flap);
        bool fore = SBS_fore(wl) < SBS_hind(wl);
        float inner = clamp(-ws / 0.22, 0.0, 1.0);
        float2 root = fore ? float2(0.04, -0.06) : float2(0.05, 0.02);
        float2 rv = wl - root;
        float rr = length(rv);
        float ra = atan(rv.y, rv.x);
        float streak = SBS_noise(float2(ra * 26.0, rr * 4.0));
        float frost = SBS_noise(wl * 14.0 + float2(ra * 3.0, 0.0));
        float tone = mix(1.0, 0.60, smoothstep(-0.1, 0.60, wl.y));
        float3 wing = mix(float3(0.08, 0.52, 0.95), float3(0.62, 0.97, 1.0), 0.30 + 0.60 * inner * (0.55 + 0.45 * frost));
        wing *= tone * (0.80 + 0.40 * streak);
        float rays = fore ? SBS_rays(wl, root, 30.0, 0.10) : SBS_rays(wl, root, 26.0, 0.08);
        float arc = fore ? (1.0 - smoothstep(0.0025, 0.0075, abs(rr - (0.40 + 0.04 * sin(ra * 5.0))))) : 0.0;
        float vein = max(rays, arc) * (1.0 - smoothstep(-0.05, -0.01, ws)) * (1.0 - 0.5 * smoothstep(0.2, 0.9, rr));
        float band = smoothstep(fore ? -0.05 : -0.11, -0.015, ws) * (fore ? smoothstep(0.35, 0.75, wl.x) : smoothstep(0.0, 0.25, wl.y));
        float stip = smoothstep(0.55, 0.80, SBS_noise(p * 0.5));
        wing = mix(wing, mix(float3(0.04, 0.20, 0.48), float3(0.45, 0.88, 1.0), stip * 0.55), band * 0.75);
        wing += float3(0.80, 0.99, 1.0) * vein * veins * 0.45;
        wing += float3(0.75, 0.97, 1.0) * exp(-(ws * ws) / 0.000064) * 0.35;
        float3 bodyCol = mix(float3(0.03, 0.08, 0.18), float3(0.45, 0.88, 1.0), smoothstep(-0.02, 0.0, bs) * 0.8);
        float3 ink = mix(wing, bodyCol, bodyMask);
        float alpha = max(wingMask * 0.93, bodyMask) * vis;
        col = mix(col, ink, alpha);
    }
    // The condensing / dissolving front glows.
    float shape = 1.0 - smoothstep(-0.02, 0.05, min(ws, bs));
    col += float3(0.55, 0.95, 1.0) * front * shape * 0.9;

    return clamp(col, 0.0, 1.0);
}

// Skia on iOS treats the shader's output as Display P3: convert from sRGB so the cyan is not oversaturated.
float3 SBS_p3(float3 s) {
    float3 l = mix(s / 12.92, pow((s + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), s));
    l = float3(dot(l, float3(0.822592, 0.177534, 0.0)), dot(l, float3(0.033199, 0.966783, 0.0)), dot(l, float3(0.017085, 0.072396, 0.910302)));
    return mix(l * 12.92, 1.055 * pow(max(l, float3(0.0)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), l));
}

half4 main(float2 pos) {
    float3 c = SBS_scene(pos, uFrame, uA, uB, uTouch, uDrag);
    return half4(half3(mix(c, SBS_p3(c), uP3)), 1.0);
}
`;
