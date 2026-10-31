// SkSL: the inside of the status pill — four looks cross-faded by uMode (thinking, searching,
// planning, writing). uBox: x, y, width, height (pt); uPhase: 2 pi t / LOOP (all motion is
// periodic in it).
export const AGENT_STATUS_SHADER = `uniform float uP3;
uniform float4 uBox;
uniform float4 uMode;
uniform float uPhase;

float3 ASP_thinking(float2 q, float ph) {
    // An aurora: a glowing band that waves across a deep violet.
    float wave = 0.55 + 0.22 * sin(q.x * 5.0 + ph * 2.0) + 0.08 * sin(q.x * 11.0 - ph * 3.0);
    float band = exp(-pow((q.y - wave) / 0.22, 2.0));
    float3 hue = mix(float3(1.0, 0.55, 0.12), float3(0.45, 1.0, 0.45), smoothstep(0.1, 0.55, q.x));
    hue = mix(hue, float3(0.72, 0.25, 1.0), smoothstep(0.55, 0.95, q.x));
    float3 base = mix(float3(0.06, 0.03, 0.16), float3(0.16, 0.08, 0.3), q.y);
    return base + hue * band * 1.1 + hue * smoothstep(0.75, 1.0, q.y) * 0.5;
}

float3 ASP_searching(float2 q, float ph) {
    // Smoky grey panels with a light sweeping through them.
    float cols = floor(q.x * 6.0);
    float shade = 0.12 + 0.08 * sin(cols * 2.7 + ph * 2.0) + 0.06 * sin(q.y * 6.0 + cols + ph * 3.0);
    float sweep = exp(-pow((fract(q.x - ph / 6.2831853) - 0.5) / 0.12, 2.0));
    float smoke = 0.08 * sin(q.x * 9.0 + q.y * 5.0 - ph * 4.0);
    return float3(shade + smoke + 0.35 * sweep) * float3(0.95, 0.95, 1.0);
}

float3 ASP_planning(float2 q, float ph) {
    // Cyan-blue on the left, red on the right, the edge between them swirling.
    float edge = 0.5 + 0.12 * sin(q.y * 4.0 + ph * 2.0) + 0.06 * sin(q.y * 9.0 - ph * 3.0);
    float k = smoothstep(edge - 0.08, edge + 0.08, q.x);
    float3 left = mix(float3(0.1, 0.35, 1.0), float3(0.15, 0.85, 1.0), smoothstep(0.0, edge, q.x));
    float3 right = mix(float3(1.0, 0.25, 0.3), float3(0.85, 0.05, 0.15), smoothstep(edge, 1.0, q.x));
    return mix(left, right, k);
}

float3 ASP_writing(float2 q, float ph) {
    // Teal to lime stripes drifting diagonally, like lines being written.
    float s = 0.5 + 0.5 * sin((q.x * 2.0 + q.y * 0.8 + 0.08 * sin(q.y * 6.0 + ph * 2.0)) * 6.2831853 - ph * 3.0);
    float3 a = float3(0.0, 0.55, 0.52);
    float3 b = float3(0.55, 0.95, 0.4);
    return mix(a, b, 0.35 * smoothstep(0.1, 0.9, s) + 0.55 * q.x);
}

float3 ASP_p3(float3 s) {
    float3 l = mix(s / 12.92, pow((s + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), s));
    l = float3(dot(l, float3(0.822592, 0.177534, 0.0)), dot(l, float3(0.033199, 0.966783, 0.0)), dot(l, float3(0.017085, 0.072396, 0.910302)));
    return mix(l * 12.92, 1.055 * pow(max(l, float3(0.0)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), l));
}

half4 main(float2 pos) {
    float2 q = (pos - uBox.xy) / uBox.zw;
    float ph = uPhase;
    float3 c = ASP_thinking(q, ph) * uMode.x + ASP_searching(q, ph) * uMode.y + ASP_planning(q, ph) * uMode.z + ASP_writing(q, ph) * uMode.w;
    // Glass: a soft highlight across the top and a darker lower edge.
    c += float3(0.22) * smoothstep(0.42, 0.0, q.y) * smoothstep(0.0, 0.08, q.y);
    c *= 1.0 - 0.18 * smoothstep(0.7, 1.0, q.y);
    c = clamp(c, 0.0, 1.0);
    return half4(half3(mix(c, ASP_p3(c), uP3)), 1.0);
}
`;
