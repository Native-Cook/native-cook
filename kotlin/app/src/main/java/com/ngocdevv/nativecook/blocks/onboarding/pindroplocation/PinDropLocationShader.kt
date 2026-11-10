package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

/**
 * AGSL translation of the RN pdl-wgsl.ts map and pin pass. It deliberately keeps the map as one
 * draw so API 33+ devices get the same globe bend, lighting, rings, places, and glass pin without
 * allocating a bitmap every frame. Spark particles are drawn by PinDropLocationArt on the CPU.
 */
internal object PinDropLocationShader {
    val SOURCE = """
uniform float2 uView;
uniform float uTime;
uniform float4 uDisc;   // centre x/y, radius, bend
uniform float4 uSpin;   // longitude, latitude, zoom, places
uniform float4 uPin;    // fall, hover, seconds since land, lights

const float PI = 3.14159265;
const float TAU = 6.2831853;
const float2 TIP = float2(0.0, -0.49);

float sq(float v) { return v * v; }
float hash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float3 lin(float3 c) { return pow(max(c, float3(0.0)), float3(2.2)); }

float noise2(float2 x) {
    float2 i = floor(x);
    float2 f = fract(x);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + float2(1.0, 0.0)), u.x),
        mix(hash2(i + float2(0.0, 1.0)), hash2(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    return 0.55 * noise2(p) + 0.28 * noise2(p * 2.1 + 5.3) + 0.17 * noise2(p * 4.3 + 1.7);
}

float3 mapAt(float2 uv, float2 pinUv) {
    float home = exp(-dot(uv, uv) * 1.1);
    float field = (fbm(uv * 1.25 + float2(3.7, 1.1)) - 0.47) * (1.0 - 0.85 * home)
        + clamp((uv.x - uv.y + 0.03) * 4.0, -0.9, 0.9) * home;
    float land = smoothstep(-0.02, 0.02, field);
    float3 c = mix(lin(float3(0.16, 0.13, 0.27)), lin(float3(0.29, 0.27, 0.57)), land);
    c = mix(c, lin(float3(0.37, 0.35, 0.68)), land * smoothstep(-0.2, 0.9, uv.x - uv.y) * 0.6);

    float2 g1 = float2(dot(uv, float2(0.87, 0.5)), dot(uv, float2(-0.5, 0.87))) * 9.0;
    float2 gl = abs(fract(g1) - 0.5);
    float blockOn = step(0.35, hash2(floor(g1)));
    float streets = (1.0 - smoothstep(0.015, 0.04, min(gl.x, gl.y))) * land * blockOn * 0.1 * (1.0 - home * 0.95);
    c += lin(float3(0.6, 0.55, 0.9)) * streets;

    float homeRoad = abs(uv.x - uv.y - 0.36 - 0.07 * sin((uv.x + uv.y) * 2.4)) * 0.7071;
    float coastRoad = abs(field + 0.02) * 0.6;
    float road = mix(coastRoad, homeRoad, smoothstep(0.08, 0.28, home));
    c = mix(c, lin(float3(0.79, 0.76, 0.95)), (1.0 - smoothstep(0.012, 0.026, road)) * 0.95);

    float2 cell = floor(uv * 26.0);
    float h = hash2(cell);
    if (h > 0.8 && land > 0.5) {
        float2 pp = (cell + 0.5 + (float2(hash2(cell + 3.1), hash2(cell + 7.7)) - 0.5) * 0.6) / 26.0;
        float dd = length(uv - pp) * 26.0;
        float on = 0.0;
        if (uPin.z >= 0.0) on = uPin.w * smoothstep(0.0, 0.15, uPin.z * 0.8 - length(pp - pinUv));
        float tw = 0.75 + 0.25 * sin(uTime * TAU * (0.2 + 0.2 * h) + h * 40.0);
        c += lin(float3(1.0, 0.72, 0.85)) * exp(-dd * dd * 18.0) * on * tw * 1.3;
    }
    return c;
}

float sdTear(float2 q) {
    float cy = 0.62;
    float r = 0.36;
    float sa = r / cy;
    float ca = sqrt(1.0 - sa * sa);
    float len = sqrt(cy * cy - r * r);
    float along = dot(q, float2(sa, ca));
    if (along < 0.0) return length(q);
    if (along < len) return dot(q, float2(ca, -sa));
    return length(q - float2(0.0, cy)) - r;
}

float sdPin(float3 p0) {
    float3 p = float3(p0.x, p0.y, p0.z * 1.9);
    float body = sdTear(float2(length(p.xz), p.y)) / 1.9;
    float hole = length(p0.xy - float2(0.0, 0.62)) - 0.135;
    return max(body, -hole);
}

float3 pinNormal(float3 p) {
    float e = 0.002;
    float3 k1 = float3(1.0, -1.0, -1.0);
    float3 k2 = float3(-1.0, -1.0, 1.0);
    float3 k3 = float3(-1.0, 1.0, -1.0);
    float3 k4 = float3(1.0, 1.0, 1.0);
    return normalize(k1 * sdPin(p + k1 * e) + k2 * sdPin(p + k2 * e) + k3 * sdPin(p + k3 * e) + k4 * sdPin(p + k4 * e));
}

float3 encode(float3 c) {
    float3 mapped = c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6);
    return pow(clamp(mapped, float3(0.0), float3(1.0)), float3(1.0 / 2.2));
}

half4 main(float2 s) {
    if (s.x < 0.0 || s.y < 0.0 || s.x > uView.x || s.y > uView.y) return half4(0.0);
    float R = uDisc.z;
    float bend = uDisc.w;
    float2 q = (s - uDisc.xy) / (R * (1.0 + 0.08 * bend));
    float radius = length(q);

    float3 col = float3(0.0);
    float alpha = 0.0;
    if (radius >= 1.0) {
        float atmosphere = exp(-sq((radius - 1.0) / 0.12)) * 0.55 * bend;
        col = lin(float3(0.45, 0.36, 0.95)) * atmosphere;
        alpha = atmosphere;
    }
    if (radius < 1.0) {
        float z = sqrt(max(1.0 - radius * radius, 0.0));
        float3 ys = float3(q.x, -q.y, z);
        float cl = cos(uSpin.y);
        float sl = sin(uSpin.y);
        float3 projected = float3(ys.x, cl * ys.y + sl * ys.z, -sl * ys.y + cl * ys.z);
        float2 sphereUv = float2(atan(projected.x, projected.z) + uSpin.x, asin(clamp(projected.y, -1.0, 1.0)));
        float2 flatUv = float2(q.x, -q.y) + uSpin.xy;
        float2 tipUv = TIP + uSpin.xy;
        float zoomK = 1.0 / (1.0 + 0.7 * uSpin.z);
        float2 uv = mix(flatUv, sphereUv, bend);
        uv = tipUv + (uv - tipUv) * zoomK;
        col = mapAt(uv, tipUv);
        float3 normal = normalize(mix(float3(0.0, 0.0, 1.0), ys, bend));
        float3 light = normalize(float3(-0.5, 0.6, 0.65));
        col *= mix(1.0, 0.35 + 0.85 * max(dot(normal, light), 0.0), bend);
        col += lin(float3(0.55, 0.45, 1.0)) * pow(1.0 - z, 3.0) * 0.9 * bend;

        if (uPin.z >= 0.0 && uPin.z < 1.6) {
            float2 tip = uDisc.xy + float2(TIP.x, -TIP.y) * R;
            float dr = length((s - tip) * float2(1.0, 1.6));
            for (int k = 0; k < 2; k++) {
                float age = uPin.z - float(k) * 0.22;
                if (age > 0.0) {
                    float front = age * 120.0;
                    col += lin(float3(1.0, 0.55, 0.75)) * exp(-sq((dr - front) / (2.0 + 6.0 * age))) * (1.0 - age / 1.6) * 0.8;
                }
            }
        }

        if (uSpin.w > 0.0) {
            float2 p0 = float2(-0.58, 0.30);
            float2 p1 = float2(0.60, -0.25);
            float2 p2 = float2(0.35, 0.72);
            float2 ps0 = uDisc.xy + float2(p0.x, p0.y) * R;
            float2 ps1 = uDisc.xy + float2(p1.x, p1.y) * R;
            float2 ps2 = uDisc.xy + float2(p2.x, p2.y) * R;
            float up0 = clamp(uSpin.w * 1.6, 0.0, 1.0);
            float up1 = clamp(uSpin.w * 1.6 - 0.25, 0.0, 1.0);
            float up2 = clamp(uSpin.w * 1.6 - 0.5, 0.0, 1.0);
            float d0 = length(s - ps0 - float2(0.0, -10.0 * up0));
            float d1 = length(s - ps1 - float2(0.0, -10.0 * up1));
            float d2 = length(s - ps2 - float2(0.0, -10.0 * up2));
            col = mix(col, lin(float3(0.98, 0.95, 1.0)), (1.0 - smoothstep(4.5, 5.5, d0)) * up0);
            col = mix(col, lin(float3(0.98, 0.95, 1.0)), (1.0 - smoothstep(4.5, 5.5, d1)) * up1);
            col = mix(col, lin(float3(0.98, 0.95, 1.0)), (1.0 - smoothstep(4.5, 5.5, d2)) * up2);
            col += lin(float3(0.75, 0.6, 1.0)) * exp(-d0 * d0 / 120.0) * up0 * 0.8;
            col += lin(float3(0.75, 0.6, 1.0)) * exp(-d1 * d1 / 120.0) * up1 * 0.8;
            col += lin(float3(0.75, 0.6, 1.0)) * exp(-d2 * d2 / 120.0) * up2 * 0.8;
            col *= 1.0 - (1.0 - smoothstep(3.0, 6.0, length((s - ps0) * float2(1.0, 2.5)))) * up0 * 0.3;
            col *= 1.0 - (1.0 - smoothstep(3.0, 6.0, length((s - ps1) * float2(1.0, 2.5)))) * up1 * 0.3;
            col *= 1.0 - (1.0 - smoothstep(3.0, 6.0, length((s - ps2) * float2(1.0, 2.5)))) * up2 * 0.3;
        }
        alpha = 1.0 - smoothstep(0.985, 1.0, radius);
    }

    float2 tipRest = uDisc.xy + float2(TIP.x, -TIP.y) * R;
    float drop = (1.0 - uPin.x) * (tipRest.y + 120.0) + uPin.y * 16.0 * uPin.x;
    float2 pinScreen = float2(tipRest.x, tipRest.y - drop);
    float H = R * 1.23;
    float height = max(tipRest.y - pinScreen.y, 0.0);
    float2 shadowUv = (s - tipRest - float2(height * 0.15, 0.0)) / float2(18.0 + height * 0.12, 6.0 + height * 0.04);
    float shadow = 0.5 * exp(-dot(shadowUv, shadowUv) * 1.5) / (1.0 + height / 90.0);
    col *= 1.0 - shadow * alpha;

    float squash = 0.0;
    if (uPin.z >= 0.0) squash = 0.13 * exp(-uPin.z * 9.0) * cos(uPin.z * 26.0) * smoothstep(0.0, 0.03, uPin.z) * (1.0 - step(0.5, uPin.z));
    float2 local = float2((s.x - pinScreen.x) / (H * (1.0 + squash)), (pinScreen.y - s.y) / (H * (1.0 - squash)));
    if (abs(local.x) < 0.45 && local.y > -0.05 && local.y < 1.05) {
        float3 rayOrigin = float3(local.x, local.y, 3.0);
        float3 ray = float3(0.0, 0.0, -1.0);
        float distance = 2.0;
        bool hit = false;
        for (int i = 0; i < 52; i++) {
            float d = sdPin(rayOrigin + ray * distance);
            if (d < 0.0008) { hit = true; break; }
            distance += d * 0.8;
            if (distance > 4.0) break;
        }
        if (hit) {
            float3 point = rayOrigin + ray * distance;
            float3 normal = pinNormal(point);
            float3 light = normalize(float3(-0.5, 0.6, 0.65));
            float3 base = mix(lin(float3(1.0, 0.24, 0.37)), lin(float3(1.0, 0.42, 0.6)), smoothstep(0.1, 0.95, point.y));
            float wrap = clamp((dot(normal, light) + 0.3) / 1.3, 0.0, 1.0);
            float3 pinColor = base * (0.45 + 0.6 * wrap);
            pinColor += lin(float3(1.0, 0.7, 0.8)) * pow(1.0 - max(normal.z, 0.0), 2.5) * 0.5;
            pinColor += float3(1.0) * pow(max(dot(reflect(-light, normal), float3(0.0, 0.0, 1.0)), 0.0), 50.0) * 0.6;
            col = mix(col, pinColor, 1.0);
            alpha = 1.0;
        }
    }
    // RuntimeShader, like the WebGPU surface, returns premultiplied encoded colour.
    float3 straight = radius < 1.0 ? col : col / max(alpha, 0.000001);
    return half4(half3(encode(straight) * alpha), alpha);
}
""".trimIndent()
}
