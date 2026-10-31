// SkSL: the switch drawn as one field — track, plasma, knob, eclipse corona, glow and reflection.
// uBox: track x, y, width, height (pt). uKnob: knob centre x (pt), fill (0..1), dark (0..1), phase (rad).
export const NEON_ECLIPSE_SHADER = `uniform float uP3;
uniform float4 uBox;
uniform float4 uKnob;

// Neon stops round a circle: magenta, orange, yellow, violet.
float3 NET_palette(float h) {
    float x = fract(h) * 4.0;
    float3 a = float3(1.0, 0.12, 0.62);
    float3 b = float3(1.0, 0.5, 0.08);
    float3 c = float3(1.0, 0.9, 0.18);
    float3 d = float3(0.52, 0.22, 1.0);
    if (x < 1.0) return mix(a, b, smoothstep(0.0, 1.0, x));
    if (x < 2.0) return mix(b, c, smoothstep(1.0, 2.0, x));
    if (x < 3.0) return mix(c, d, smoothstep(2.0, 3.0, x));
    return mix(d, a, smoothstep(3.0, 4.0, x));
}

float NET_box(float2 p, float2 c, float2 half_, float r) {
    float2 q = abs(p - c) - (half_ - r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Premultiplied colour of the switch at p.
float4 NET_scene(float2 p) {
    float2 size = uBox.zw;
    float2 c = uBox.xy + size * 0.5;
    float r = size.y * 0.5;
    float knobX = uKnob.x;
    float fill = uKnob.y;
    float dark = uKnob.z;
    float ph = uKnob.w;
    float dBox = NET_box(p, c, size * 0.5, r);
    float2 kc = float2(knobX, c.y);
    float kr = r - 8.0;
    float dK = length(p - kc) - kr;

    // Flowing neon plasma, periodic in the phase so the loop is seamless.
    float2 q = p - uBox.xy;
    float h = 0.35 * sin(q.x * 0.022 + ph) + 0.25 * sin(q.y * 0.05 - 2.0 * ph + q.x * 0.012)
            + 0.2 * sin(length(p - kc) * 0.045 - 3.0 * ph);
    float3 plasma = NET_palette(h + ph / 6.2831853);
    // Liquid streaks: bright bands with dark glass between them.
    float band = smoothstep(0.15, 0.85, 0.5 + 0.5 * sin(q.x * 0.045 - 2.0 * ph + 1.6 * sin(q.y * 0.07 + ph)));
    float3 liquid = mix(float3(0.06, 0.03, 0.08), plasma * 1.15, 0.25 + 0.75 * band);

    // The plasma floods leftwards from the knob as fill grows.
    float front = knobX - fill * (size.x + 60.0);
    float m = smoothstep(front - 26.0, front + 4.0, p.x) * smoothstep(0.0, 0.15, fill);

    float inside = 1.0 - smoothstep(-0.75, 0.75, dBox);
    float3 col = mix(float3(0.1), liquid, m) * inside;
    float a = inside;
    // Off rim.
    float rim = (1.0 - smoothstep(0.0, 1.6, abs(dBox + 0.8))) * (1.0 - 0.6 * fill);
    col += float3(0.36) * rim;
    a = max(a, rim * 0.8);
    // Outer glow of the lit track.
    float glow = exp(-max(dBox, 0.0) / 13.0) * fill * (1.0 - inside) * 0.6;
    col += plasma * glow;
    a = max(a, glow);

    // Knob: a white pearl that turns into a black moon.
    float kin = 1.0 - smoothstep(-0.75, 0.75, dK);
    float2 kn = (p - kc) / kr;
    float shade = 0.9 + 0.1 * clamp(-kn.y * 0.8 - kn.x * 0.4, -1.0, 1.0) - 0.12 * dot(kn, kn);
    float3 knob = mix(float3(shade), float3(0.015), dark);
    col = mix(col, knob, kin);
    a = mix(a, 1.0, kin);
    // Eclipse corona: a thin hot ring and a wide halo, their hue turning round the disc.
    float ang = atan(p.y - kc.y, p.x - kc.x);
    float3 cc = NET_palette(ang / 6.2831853 + ph / 6.2831853 * 2.0) * 1.25;
    float ring = exp(-abs(dK) / 2.2) * dark;
    float halo = exp(-max(dK, 0.0) / 9.0) * 0.5 * dark * (1.0 - kin);
    col += cc * (ring + halo);
    a = max(a, min(1.0, ring + halo));
    return float4(col, a);
}

float3 NET_p3(float3 s) {
    float3 l = mix(s / 12.92, pow((s + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), s));
    l = float3(dot(l, float3(0.822592, 0.177534, 0.0)), dot(l, float3(0.033199, 0.966783, 0.0)), dot(l, float3(0.017085, 0.072396, 0.910302)));
    return mix(l * 12.92, 1.055 * pow(max(l, float3(0.0)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), l));
}

half4 main(float2 pos) {
    float floorY = uBox.y + uBox.w + 26.0;
    float4 s;
    if (pos.y < floorY) {
        s = NET_scene(pos);
    } else {
        // Reflection on the glossy floor, fading with distance.
        float d = pos.y - floorY;
        s = NET_scene(float2(pos.x, 2.0 * floorY - pos.y)) * 0.22 * exp(-d / 40.0);
    }
    float3 c = clamp(s.rgb, 0.0, 1.0);
    c = mix(c, NET_p3(c), uP3);
    float a = clamp(max(s.a, max(c.r, max(c.g, c.b))), 0.0, 1.0);
    return half4(half3(c), half(a));
}
`;
