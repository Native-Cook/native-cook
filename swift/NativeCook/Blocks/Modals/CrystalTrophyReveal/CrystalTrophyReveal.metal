#include <metal_stdlib>
using namespace metal;

// The reference SkSL optical model, evaluated in canvas points. Textures retain raw RGB values.
struct CrystalTrophyRevealUniforms {
    float4 uSize;
    float4 uOrigin;
    float4 uUnit;
    float4 uTime;
    float4 uLamp;
    float4 uBeam;
    float4 uUnder;
    float4 uSeam;
    float4 uRise;
    float4 uFlash;
    float4 uFlecks[12];
    float4 uOpen;
    float4 uHem;
    float4 uShiver;
    float4 uPixelRatio;
    float4 uCamera;
    float4 uTurn;
    float4 uPlanes[31];
    float4 uBase[18];
    float4 uRim[32];
    float4 uFire[8];
    float4 uDrop;
    float4 uEtch;
    float4 uTouch;
    float4 uFinger;
    float4 uCameraScale;
    float4 uCameraY;
    float4 uShare;
    float4 uClose;
    float4 uControlAlpha;
    float4 uSharePress;
    float4 uClosePress;
    float4 uSheen;
    float4 uSharePoint;
    float4 uClosePoint;
    float4 uReduced;
};
struct CrystalTrophyRevealScene {
    constant CrystalTrophyRevealUniforms &u;
    texture2d<float> engravingTexture;
    texture2d<float> marbleTexture;
    float4 sampleEngraving(float2 p) {
        constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
        return engravingTexture.sample(s, p / float2(2048, 512));
    }
    float4 sampleMarble(float2 p) {
        constexpr sampler s(coord::normalized, address::repeat, filter::linear);
        return marbleTexture.sample(s, p / 768.0);
    }

    float hash21(float2 p) {
        float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
        q += dot(q, q.yzx + 33.33);
        return fract((q.x + q.y) * q.z);
    }
    float noise2(float2 p) {
        float2 a = floor(p);
        float2 b = fract(p);
        b = b * b * (3.0 - 2.0 * b);
        return mix(mix(hash21(a), hash21(a + float2(1, 0)), b.x),
                   mix(hash21(a + float2(0, 1)), hash21(a + float2(1, 1)), b.x), b.y);
    }
    float sq(float v) {
        return v * v;
    }
    float bell(float2 p) {
        return exp(-dot(p, p));
    }
    float2 poolCentre() {
        float b = clamp(u.uBeam.x, 0.0, 1.0);
        return mix(float2(u.uSize.x * 0.24, u.uSize.y * 0.17), u.uOrigin.xy, b) +
               float2(-0.08 * u.uSize.x * sin(b * 3.14159265), 0);
    }
    float2 lampPoint() {
        return float2(u.uOrigin.x + 0.32 * u.uUnit.x, -0.12 * u.uSize.y);
    }
    float shaft(float2 p) {
        float2 aim = poolCentre();
        float2 lampPos = lampPoint();
        float travel = (p.y - lampPos.y) / max(1.0, aim.y - lampPos.y);
        float axis = mix(lampPos.x, aim.x, travel);
        float radius = (9.0 + 105.0 * max(0.0, travel)) * u.uUnit.x / 100.0;
        float across = (p.x - axis) / max(1.0, radius);
        return exp(-across * across * 2.8) * (1.0 - smoothstep(0.9, 1.2, travel));
    }
    // A rounded height field, differentiated analytically: broad diffuse flanks and soft pile sheen.
    // One nap noise lookup; the second, incommensurate pleat never becomes a row of specular lines.
    float3 velvet(float2 p, float front) {
        float k = u.uUnit.x / 100.0;
        float yy = p.y / k;
        float flare = 1.0 + 0.065 * smoothstep(280.0, 650.0, yy);
        float sway = 7.0 * sin(yy * 0.0042) + 2.0 * sin(yy * 0.0091);
        float xx = (p.x / k + sway) / flare;
        float freq = mix(0.104, 0.165, front);
        float phase = xx * freq + 0.65 * sin(xx * 0.012) + 0.24 * sin(xx * 0.024 + 0.8);
        float phaseDu = (freq + 0.0078 * cos(xx * 0.012) + 0.00576 * cos(xx * 0.024 + 0.8)) / flare;
        float heightField = cos(phase) - 0.13 * cos(2.0 * phase) + 0.20 * cos(2.3 * phase + 0.7);
        float slope = mix(14.0, 11.0, front) * phaseDu *
                      (-sin(phase) + 0.26 * sin(2.0 * phase) - 0.46 * sin(2.3 * phase + 0.7));
        float3 normal = float3(-slope, 0.12, 1.0) / sqrt(slope * slope + 1.0144);
        float diffuse = pow(max(0.0, 0.5 + 0.5 * dot(normal, float3(0.38, 0.57, 0.73))), 1.4);
        float cavity = smoothstep(-1.20, 0.48, heightField);
        float sheen = pow(max(0.0, 1.0 - normal.z), 2.5);
        float3 body = mix(float3(0.043, 0.067, 0.314), float3(0.106, 0.184, 0.784), cavity);
        float3 col = body * (0.32 + 0.68 * diffuse);
        col += float3(0.14, 0.20, 0.27) * diffuse * cavity;
        col += float3(0.68, 0.75, 1.0) * sheen * (0.20 + 0.40 * cavity);
        float nap = noise2(float2(xx * 1.8, yy * 0.65));
        return col * (0.982 + nap * 0.036);
    }

    float4 stageBack(float2 p) {
        float k = u.uUnit.x / 100.0;
        float stageScale = u.uUnit.x / 108.0;
        float2 uv = p / u.uSize.xy;
        if (uv.y > 0.91) {
            return float4(float3(0.00784, 0.01176, 0.04314) + (hash21(p * 2.37) - 0.5) / 255.0, 1);
        }
        float floorY = u.uOrigin.y - 0.10 * u.uUnit.x +
                       9.0 * k * sq((p.x - u.uOrigin.x) / max(1.0, u.uOrigin.x)) +
                       k * (2.0 * sin(p.x / k * 0.104) + 1.0 * sin(p.x / k * 0.047));
        float isFloor = smoothstep(floorY - 15.0 * k, floorY + 21.0 * k, p.y);
        float2 aim = poolCentre();
        float b = clamp(u.uBeam.x, 0.0, 1.0);
        float2 wallAim = aim - float2(0, 1.50 * u.uUnit.x * b);
        float wallPool = bell((p - wallAim) / float2((100.0 + 65.0 * b) * k, (120.0 + 110.0 * b) * k));
        float floorPool =
            bell((p - aim - float2(0, 17.0 * k)) / float2(200.0 * stageScale, 50.0 * stageScale));
        // Reuse the cloth sample for the floor reflection, stretching its lower folds into soft smears.
        float2 clothP = float2(p.x, mix(p.y, floorY - (p.y - floorY) * 0.35, isFloor));
        float3 cloth = velvet(clothP, 0.0);
        float room = 0.08 + 0.20 * u.uLamp.x + 0.02 * (1.0 - clamp(u.uLamp.x, 0.0, 1.0));
        float flashBloom = 1.0 + 0.35 * max(0.0, u.uFlash.x);
        float3 wall = cloth * (room + u.uLamp.x * 0.52 * wallPool * flashBloom);
        float reflectionFade = exp(-max(0.0, p.y - floorY) / (26.0 * k));
        float3 floorCol = float3(0.009, 0.012, 0.039) + u.uLamp.x * floorPool * float3(0.23, 0.25, 0.43) +
                          cloth * u.uLamp.x * reflectionFade * 0.18;
        float floorCore = bell((p - u.uOrigin.xy - float2(0, 15.0 * k)) / float2(100.0 * k, 24.0 * k));
        floorCol += float3(0.23, 0.27, 0.31) * floorCore * u.uLamp.x * b;
        // A broad, vertically streaked reflection of the lit marble and crystal. Reuse cloth's
        // low-frequency flank response: no extra material or noise evaluation on the floor.
        float below = (p.y - u.uOrigin.y) / stageScale;
        float reflected = exp(-sq((p.x - u.uOrigin.x) / (102.0 * stageScale))) *
                          smoothstep(12.0, 30.0, below) * exp(-max(0.0, below - 30.0) / 34.0) *
                          (1.0 - smoothstep(80.0, 108.0, below));
        float streaks = 0.72 + 0.28 * clamp(cloth.b, 0.0, 1.0);
        floorCol += float3(0.20, 0.25, 0.31) * reflected * streaks *
                    (u.uLamp.x * clamp(u.uRise.x, 0.0, 1.1) + max(0.0, u.uUnder.x));
        floorCol *= 0.99 + 0.02 * hash21(floor(p * 1.7));
        float contact = exp(-sq((p.y - floorY) / (6.5 * k)));
        float3 col = mix(wall, floorCol, isFloor) * (1.0 - contact * 0.12);
        float haze = shaft(p);
        float smoke =
            0.84 + 0.16 * noise2(p / float2(74.0 * k, 180.0 * k) +
                                 0.35 * float2(sin(u.uTime.x * 0.41887902), cos(u.uTime.x * 0.41887902)));
        // Both atmospheric terms follow the moving aim and respond on the lamp's causal frame.
        col += float3(0.60, 0.58, 0.95) * wallPool * u.uLamp.x * 0.40 * flashBloom * (1.0 - isFloor * 0.85);
        col += float3(0.63, 0.69, 1.0) * haze * smoke * u.uLamp.x * 0.22;
        float seedGlow = bell((p - u.uOrigin.xy + float2(0, 20.0 * k)) / float2(78.0 * k, 90.0 * k));
        col += float3(0.10, 0.19, 0.44) * seedGlow * u.uSeam.x * 0.14;
        // The slot is a temporary light source, spreading upwards into a soft 300-point fan.
        if (u.uUnder.x > 0.0) {
            float upward = max(0.0, (u.uOrigin.y - p.y) / stageScale);
            float fanWidth = 22.0 + 88.0 * clamp(upward / 300.0, 0.0, 1.0);
            float fanAcross = (p.x - u.uOrigin.x) / (fanWidth * stageScale);
            float rays = 0.88 + 0.12 * sq(cos(fanAcross * 9.0 + upward * 0.004));
            float fan = exp(-fanAcross * fanAcross * 1.6) * rays * (1.0 - smoothstep(220.0, 310.0, upward)) *
                        (1.0 - isFloor);
            float slotPool = bell((p - u.uOrigin.xy - float2(0, 16.0 * stageScale)) /
                                  float2(122.0 * stageScale, 32.0 * stageScale));
            col += float3(0.72, 0.91, 1.0) * u.uUnder.x *
                   (0.30 * fan * (0.65 + 0.35 * exp(-upward / 100.0)) + 0.34 * slotPool);
        }
        float3 flecks = float3(0);
        for (int i = 0; i < 12; i++) {
            float4 f = u.uFlecks[i];
            if (f.w > 0.002 && u.uLamp.x * u.uRise.x > 0.001) {
                float fi = float(i);
                float2 delta = p - f.xy;
                float ca = cos(f.z);
                float sa = sin(f.z);
                float2 local = float2(ca * delta.x + sa * delta.y, -sa * delta.x + ca * delta.y);
                float wide = (7.0 + fmod(fi * 7.0, 11.0)) * stageScale;
                float thin = (1.5 + fmod(fi * 1.7, 2.1)) * stageScale;
                float along = local.x / wide;
                if (abs(along) < 1.0 && abs(local.y) < thin * 3.0) {
                    // Wall patches bend with the broad folds; the middle is nearly achromatic.
                    float warp =
                        i < 8 ? (0.32 * along * along + 0.16 * along * sin(f.x / k * 0.104)) : 0.12 * along;
                    float across = local.y / thin - warp;
                    float envelope = sq(max(0.0, 1.0 - along * along));
                    float softness = 1.15 + fmod(fi, 3.0) * 0.25;
                    float core = exp(-across * across * softness);
                    float redEdge = exp(-sq(across - 0.95) * 5.5);
                    float blueEdge = exp(-sq(across + 0.95) * 5.5);
                    float3 patch = float3(0.91, 0.96, 1.0) * core + float3(0.28, 0.065, 0.025) * redEdge +
                                   float3(0.12, 0.06, 0.34) * blueEdge;
                    flecks += patch * envelope * f.w * 0.74;
                }
            }
        }
        col += flecks * u.uLamp.x * clamp(u.uRise.x, 0.0, 1.1) * (1.0 + u.uFlash.x) *
               mix(0.62 + cloth.b, 0.82, isFloor);
        float vignette = 0.36 + 0.64 * exp(-sq((uv.x - 0.5) * 2.2));
        vignette *= 0.67 + 0.33 * smoothstep(0.0, 0.28, uv.y);
        col *= vignette;
        // Reserve the text zone even during slot light, lamp overshoot and tap flash.
        float copyCalm = smoothstep(54.0, 76.0, below);
        float peak = max(0.0001, max(col.r, max(col.g, col.b)));
        col *= mix(1.0, min(1.0, 0.12 / peak), copyCalm);
        col = mix(col, float3(0.00784, 0.01176, 0.04314), smoothstep(0.76, 0.91, uv.y));
        col += (hash21(p * 2.37) - 0.5) / 255.0;
        return float4(max(col, float3(0)), 1);
    }

    float4 stageCurtain(float2 p) {
        float k = u.uUnit.x / 100.0;
        float side = p.x < u.uSize.x * 0.5 ? -1.0 : 1.0;
        float fromSide = min(p.x, u.uSize.x - p.x);
        float yRatio = clamp(p.y / max(1.0, u.uSize.y - 26.0 * k), 0.0, 1.0);
        float bend = yRatio * yRatio * (3.0 - 2.0 * yRatio);
        float railWidth = u.uSize.x * (0.5 - 0.35 * u.uOpen.x);
        float hemWidth = u.uSize.x * (0.5 - 0.385 * u.uHem.x);
        float edge = mix(railWidth, hemWidth, bend);
        float rippleY = u.uShiver.x * (u.uSize.y + 180.0 * k) - 90.0 * k;
        float band = exp(-sq((p.y - rippleY) / (60.0 * k))) * sin(u.uShiver.x * 3.14159265);
        float ripple = sin((p.y - rippleY) / (22.0 * k)) * band * 4.0 * k;
        edge += ripple * (1.0 - clamp(u.uOpen.x, 0.0, 1.0));
        float gap = fromSide - edge;
        if (gap > 32.0 * k)
            return float4(0);
        float gather = max(0.20, edge / (u.uSize.x * 0.5));
        // Gather reduces the visible fold count: about 5 per closed half, 2–3 per open wing.
        float foldCompression = 0.42 + 0.58 * gather;
        float clothX = fromSide / foldCompression;
        // Separate phase offsets avoid a mirrored fabric pattern across the opening.
        float2 clothP = float2(clothX + side * 57.0 * k - ripple, p.y);
        float hemY = u.uSize.y - 26.0 * k +
                     (3.8 * sin(clothX / k * 0.165) + 1.7 * sin(clothX / k * 0.071)) * k * sqrt(gather) -
                     2.0 * k * band;
        float coverage = (1.0 - smoothstep(-0.65 * k, 0.65 * k, gap)) *
                         (1.0 - smoothstep(-0.65 * k, 0.65 * k, p.y - hemY));
        float3 cloth = velvet(clothP, 1.0);
        float footlight = 0.14 + 0.31 * pow(yRatio, 1.35);
        float gathered = clamp(u.uOpen.x, 0.0, 1.0);
        float edgeRoll = exp(-sq((gap + 8.0 * k) / (10.0 * k)));
        float innerShadow = 1.0 - 0.30 * exp(-sq((gap + 22.0 * k) / (10.0 * k)));
        float3 col = cloth * footlight * innerShadow * (1.0 - 0.28 * gathered);
        col += float3(0.025, 0.038, 0.09) * edgeRoll * (0.25 + u.uLamp.x * 0.65);
        float seamNear = exp(-sq((p.x - u.uSize.x * 0.5) / (26.0 * k)));
        float closed = 1.0 - smoothstep(0.0, 0.09, max(u.uOpen.x, u.uHem.x));
        col += float3(0.16, 0.25, 0.52) * seamNear * u.uSeam.x * closed * (0.4 + cloth.b);
        // Backlight wraps the rounded leading fold over ten points; no luminous outline.
        float rim = exp(-sq((gap + 4.0 * k) / (10.0 * k)));
        col += float3(0.10, 0.15, 0.32) * rim * u.uLamp.x * clamp(u.uOpen.x * 3.0, 0.0, 1.0) *
               (0.35 + 0.65 * shaft(p));
        float blade = exp(-sq((p.x - u.uSize.x * 0.5) / (0.48 + 0.58 * u.uSeam.x)));
        col += float3(0.66, 0.81, 1.0) * blade * u.uSeam.x * closed;
        col *= 0.68 + 0.32 * exp(-sq((p.x / u.uSize.x - 0.5) * 1.5));
        col += (hash21(p * 2.37) - 0.5) / 255.0;
        float underHem = exp(-sq((p.y - hemY - 1.5 * k) / (3.0 * k))) * u.uSeam.x *
                         (1.0 - clamp(u.uOpen.x, 0.0, 1.0)) * 0.26;
        float forward = max(0.0, p.y - hemY);
        float rays = exp(-sq((p.x - u.uSize.x * 0.5) / (3.0 * k + forward * 0.8))) *
                     smoothstep(0.0, 4.0 * k, forward) * (1.0 - smoothstep(10.0 * k, 32.0 * k, forward)) *
                     u.uSeam.x * u.uSeam.x * closed * 0.22;
        underHem += rays;
        float shadow =
            exp(-sq((p.y - hemY - 4.0 * k) / (7.0 * k))) * 0.60 * (1.0 - smoothstep(0.0, 5.0 * k, gap));
        float spill = exp(-max(0.0, gap) / (6.0 * k)) * u.uLamp.x * 0.028 *
                      (1.0 - smoothstep(hemY, hemY + 9.0 * k, p.y));
        float a = coverage + (1.0 - coverage) * max(shadow, max(underHem, spill));
        float3 rgb =
            max(col, float3(0)) * coverage +
            (1.0 - coverage) * (float3(0.25, 0.39, 0.75) * underHem + float3(0.21, 0.31, 0.65) * spill);
        return float4(min(rgb, float3(a)), a);
    }

    float4 stageDust(float2 p) {
        if (u.uLamp.x <= 0.001)
            return float4(0);
        float2 aim = poolCentre();
        float2 lampPos = lampPoint();
        float progress = (p.y - lampPos.y) / max(1.0, aim.y - lampPos.y);
        float radius = (9.0 + 105.0 * max(0.0, progress)) * u.uUnit.x / 100.0;
        float axis = mix(lampPos.x, aim.x, progress);
        float2 field = float2((p.x - axis) / max(1.0, radius) * 0.5 + 0.5, progress);
        if (field.x < 0.0 || field.x > 1.0 || field.y < 0.0 || field.y > 1.0)
            return float4(0);
        float2 cell = floor(field * float2(8, 7));
        float seed = hash21(cell + 31.0);
        float seedB = hash21(cell + 94.0);
        float phase = u.uTime.x * 0.41887902 + seed * 6.2831853;
        float2 offset = float2(0.5 + 0.24 * sin(phase), 0.5 + 0.29 * cos(phase + seedB * 6.2831853));
        float2 centreUV = (cell + offset) / float2(8, 7);
        float yPoint = mix(lampPos.y, aim.y, centreUV.y);
        float particleRadius = (9.0 + 105.0 * centreUV.y) * u.uUnit.x / 100.0;
        float xPoint = mix(lampPos.x, aim.x, centreUV.y) + (centreUV.x - 0.5) * 2.0 * particleRadius;
        float moteSize = (0.6 + 1.6 * seed * seed) * u.uUnit.x / 100.0;
        float glow = bell((p - float2(xPoint, yPoint)) / max(0.4, moteSize));
        float twinkle = 0.25 + 0.75 * sq(0.5 + 0.5 * sin(u.uTime.x * 0.83775804 + seedB * 19.0));
        float a = min(0.62, glow * twinkle * u.uLamp.x * shaft(p) * 0.59);
        return float4(float3(0.76, 0.85, 1.0) * a, a);
    }

    float squared(float v) {
        return v * v;
    }
    float3 safeUnit(float3 v) {
        return v * rsqrt(max(dot(v, v), 0.00000001));
    }
    float3 toObject(float3 v) {
        float ce = cos(u.uCamera.y), se = sin(u.uCamera.y);
        float3 q = float3(v.x, v.y * ce + v.z * se, -v.y * se + v.z * ce);
        return float3(q.x * u.uTurn.x - q.z * u.uTurn.y, q.y, q.x * u.uTurn.y + q.z * u.uTurn.x);
    }
    float3 toView(float3 v) {
        float3 q = float3(v.x * u.uTurn.x + v.z * u.uTurn.y, v.y, -v.x * u.uTurn.y + v.z * u.uTurn.x);
        float ce = cos(u.uCamera.y), se = sin(u.uCamera.y);
        return float3(q.x, q.y * ce - q.z * se, q.y * se + q.z * ce);
    }
    float2 projected(float3 p) {
        float3 q = toView(p);
        return u.uOrigin.xy + float2(q.x, -q.y) * u.uUnit.x * u.uCamera.x / max(0.2, u.uCamera.x - q.z);
    }
    float tile(float2 uv, float index) {
        return sampleEngraving(
                   float2((clamp(uv.x, 0.002, 0.998) + index) * 512.0, clamp(uv.y, 0.002, 0.998) * 512.0))
            .r;
    }
    float fresnel(float cosine, float eta) {
        float f = (eta - 1.0) / (eta + 1.0);
        f *= f;
        float a = 1.0 - clamp(cosine, 0.0, 1.0);
        float a2 = a * a;
        return f + (1.0 - f) * a2 * a2 * a;
    }
    float3 transmission(float3 rayDir, float3 normal, float ratio) {
        float c = -dot(normal, rayDir);
        float k = 1.0 - ratio * ratio * (1.0 - c * c);
        if (k <= 0.0)
            return float3(0);
        return ratio * rayDir + (ratio * c - sqrt(max(k, 0.0))) * normal;
    }
    // Interval clipping also returns the two limiting planes, which define the silhouette's analytic
    // gradient.
    float crystalHit(float3 ro, float3 rd, thread float &nearT, thread float &farT, thread float3 &nearN,
                     thread float3 &farN, thread float &face) {
        nearT = -10000.0;
        farT = 10000.0;
        nearN = float3(0, 0, 1);
        farN = float3(0, 0, -1);
        face = 0.0;
        float valid = 1.0;
        for (int i = 0; i < 31; i++) {
            float4 pl = u.uPlanes[i];
            if (i == 30)
                pl.w = -u.uDrop.x;
            float den = dot(pl.xyz, rd), numer = pl.w - dot(pl.xyz, ro);
            if (abs(den) < 0.000001) {
                if (numer < 0.0)
                    valid = 0.0;
            } else {
                float t = numer / den;
                if (den < 0.0 && t > nearT) {
                    nearT = t;
                    nearN = pl.xyz;
                    face = float(i);
                }
                if (den > 0.0 && t < farT) {
                    farT = t;
                    farN = pl.xyz;
                }
            }
        }
        return valid;
    }
    float baseHit(float3 ro, float3 rd, thread float &nearT, thread float &farT, thread float3 &nearN,
                  thread float3 &farN) {
        nearT = -10000.0;
        farT = 10000.0;
        nearN = float3(0, 1, 0);
        farN = float3(0, -1, 0);
        float valid = 1.0;
        for (int i = 0; i < 18; i++) {
            float4 pl = u.uBase[i];
            float den = dot(pl.xyz, rd), numer = pl.w - dot(pl.xyz, ro);
            if (abs(den) < 0.000001) {
                if (numer < 0.0)
                    valid = 0.0;
            } else {
                float t = numer / den;
                if (den < 0.0 && t > nearT) {
                    nearT = t;
                    nearN = pl.xyz;
                }
                if (den > 0.0 && t < farT) {
                    farT = t;
                    farN = pl.xyz;
                }
            }
        }
        return valid;
    }
    float coverage(float nt, float ft, float3 nn, float3 fn, float3 rd) {
        float dn = dot(nn, rd), df = dot(fn, rd);
        float3 dx = toObject(float3(1, 0, 0)) / u.uUnit.x;
        float3 dy = toObject(float3(0, 1, 0)) / u.uUnit.x;
        float2 grad = float2(0);
        if (abs(dn) > 0.000001 && abs(df) > 0.000001)
            grad = float2(-ft * dot(fn, dx) / df + nt * dot(nn, dx) / dn,
                          -ft * dot(fn, dy) / df + nt * dot(nn, dy) / dn);
        float width = clamp(length(grad) / u.uPixelRatio.x, 0.0000001, 0.003 / u.uPixelRatio.x);
        return clamp(0.5 + (ft - nt) / width, 0.0, 1.0);
    }
    // Reflections see the house. Only outgoing transmission samples the real stage.
    float3 env(float3 dir, float3 pos) {
        float3 v = safeUnit(toView(dir));
        float3 p = toView(pos - float3(0, u.uDrop.x, 0));
        float alignment = dot(v, safeUnit(float3(0.16, 0.9, 0.4)));
        float disc = smoothstep(0.993, 0.995, alignment) * 9.0;
        float halo = pow(max(alignment, 0.0), 24.0) * 0.8;
        float3 c = float3(0.002, 0.004, 0.014) + float3(0.045, 0.065, 0.13) * u.uLamp.x;
        // A large lilac softbox, 50 degrees wide, with a diagonal falloff across the slab.
        // Position in the house stays fixed as the trophy turns beneath it.
        float panel = pow(max(dot(v, safeUnit(float3(-0.40, 0.56, 1.0))), 0.0), 7.0);
        float sweep = 0.12 + 0.88 * smoothstep(0.6, 3.1, p.y - p.x * 1.2);
        c += float3(15.0, 14.4, 15.6) * panel * sweep * u.uLamp.x * (1.0 + u.uFlash.x * 0.12);
        float strip = exp(-squared((v.x - 0.66) * 19.0)) * smoothstep(-0.7, 0.0, v.y);
        c += float3(2.5, 3.5, 4.8) * strip * u.uLamp.x;
        for (int j = 0; j < 5; j++) {
            float a = float(j);
            float3 pin = safeUnit(float3(-0.78 + a * 0.37, 0.14 + 0.19 * sin(a * 2.3), 1.0));
            c += float3(2.8, 3.4, 4.2) * pow(max(dot(v, pin), 0.0), 1400.0) * u.uLamp.x;
        }
        // A soft low reflector gives internally reflected lower facets a broad band, never a rule.
        float low = exp(-squared((v.y + v.x * 0.62 + 0.30) * 4.0));
        c += float3(0.55, 0.72, 1.1) * low * u.uLamp.x;
        c += float3(0.55, 0.65, 0.82) * max(-v.y, 0.0) * max(-v.y, 0.0) * u.uLamp.x;
        float flag = exp(-squared((v.x * 0.6 + v.y * 0.35 + p.x * 0.70 - p.y * 0.15 - 0.12) * 12.0));
        c = mix(c, float3(0.055, 0.102, 0.36) * u.uLamp.x, flag * 0.88);
        return c + float3(0.86, 0.94, 1.0) * (disc + halo) * u.uLamp.x;
    }
    float3 outside(float3 dir, float3 pos) {
        float3 v = safeUnit(toView(dir));
        if (v.z < -0.15) {
            float2 offset = 2.4 * float2(v.x, -v.y) / max(-v.z, 0.3);
            offset *= min(1.0, 1.6 / max(length(offset), 0.0001));
            float2 canvasPoint = projected(pos - float3(0, u.uDrop.x, 0)) + u.uUnit.x * offset;
            float3 transmitted = stageBack(canvasPoint).rgb * float3(0.87, 0.885, 0.90);
            // House flags interrupt oblique transmission; the bands move with the exiting ray.
            float3 wallRay = toView(pos - float3(0, u.uDrop.x, 0));
            float band =
                exp(-squared((v.x * 0.6 + v.y * 0.35 + wallRay.x * 0.70 - wallRay.y * 0.15 - 0.12) * 12.0));
            float oblique = smoothstep(0.06, 0.18, abs(v.x) + abs(v.y) * 0.7);
            return mix(transmitted, float3(0.055, 0.102, 0.36) * u.uLamp.x, band * oblique * 0.94);
        }
        return env(dir, pos);
    }
    float3 spectrum(float3 rd, float3 n, float3 p) {
        float3 rr = transmission(rd, n, 1.508), gg = transmission(rd, n, 1.52),
               bb = transmission(rd, n, 1.534);
        float3 c = float3(0);
        if (dot(rr, rr) > 0.1)
            c.r = outside(rr, p).r;
        if (dot(gg, gg) > 0.1)
            c.g = outside(gg, p).g;
        if (dot(bb, bb) > 0.1)
            c.b = outside(bb, p).b;
        return c;
    }
    float frost(float3 p, float face, thread float3 &tint) {
        tint = float3(0);
        if (p.y < 1.45 && abs(face - 3.0) > 0.1)
            return 0.0;
        if (p.y >= 1.45 && (face < 3.5 || face > 6.5))
            return 0.0;
        if ((p.y < 1.45 && u.uEtch.x < 0.88) || (p.y >= 1.45 && u.uEtch.x <= 0.30))
            return 0.0;
        float2 uv = float2((p.x - 0.02) / 1.152 + 0.5, 0.5 - (p.y - 2.125) / 0.8775);
        float which = 0.0;
        float present = 1.0;
        if (p.y < 1.45) {
            uv = float2(p.x / 0.43 + 0.5, 0.5 - (p.y - 0.98) / 0.43);
            which = 2.0;
            present = smoothstep(0.88, 0.995, u.uEtch.x - length(uv - 0.5) * 0.05);
        }
        float inTile = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
        if (inTile < 0.5)
            return 0.0;
        float sdf = tile(uv, which);
        float aa = (which < 1.0 ? 8.0 : 18.0) / (u.uUnit.x * u.uPixelRatio.x);
        float mask = smoothstep(0.5 - aa, 0.5 + aa, sdf) * inTile;
        float order = tile(uv, 1.0);
        float progress = clamp((u.uEtch.x - 0.30) / 0.62, 0.0, 1.0);
        float laser = 0.0;
        if (which < 1.0) {
            present = smoothstep(order - 0.018, order + 0.018, progress) * step(0.301, u.uEtch.x);
            if (u.uEtch.x < 0.925)
                laser = exp(-squared((order - progress) * 95.0)) * exp(-squared((sdf - 0.53) * 7.0)) * inTile;
        }
        if (sdf < 0.30 && laser < 0.0001)
            return 0.0;
        float2 eps = float2(1.0 / 512.0, 0);
        float gx = tile(uv + eps, which) - tile(uv - eps, which);
        float gy = tile(uv + eps.yx, which) - tile(uv - eps.yx, which);
        // 32 source pixels encode the signed distance; convert a 1.7pt wall to SDF units.
        float wallWidth = (which < 1.0 ? 512.0 / 1.152 : 512.0 / 0.43) * 1.7 / (32.0 * u.uUnit.x);
        float slope = clamp(-(gx + gy) * 25.0, -1.0, 1.0);
        float wall = exp(-squared((sdf - 0.54) / max(wallWidth, 0.01)));
        float grain = tile(uv * 3.0 - floor(uv * 3.0), 3.0);
        float3 body = float3(0.965, 0.977, 1.0) * (0.99 - 0.08 * uv.y + 0.018 * grain);
        float3 wallTint =
            mix(float3(0.431, 0.525, 0.784), float3(1.5, 1.6, 1.75), smoothstep(-0.2, 0.55, slope));
        tint = mix(body, wallTint, wall) * (0.025 + u.uLamp.x + u.uUnder.x * 0.65);
        float contact =
            exp(-squared((sdf - 0.43) / 0.052)) * smoothstep(0.05, -0.30, slope) * (1.0 - mask) * present;
        tint = mix(tint, float3(0.045, 0.10, 0.27) * (0.02 + u.uLamp.x), contact);
        tint += float3(0.7, 1.1, 1.7) * laser * 3.0;
        return clamp(max(max(mask * present * 0.86, contact * 0.70), laser * 0.8), 0.0, 1.0);
    }
    float3 glass(float3 ro, float3 rd, float enterT, float3 normal, float face) {
        float3 hit = ro + rd * max(enterT, 0.0);
        float3 incident = safeUnit(rd);
        float f = fresnel(-dot(incident, normal), 1.52);
        float3 result = env(reflect(incident, normal), hit) * f;
        float3 rayDir = transmission(incident, normal, 1.0 / 1.52);
        float3 throughput = float3(1.0 - f);
        // Engraving viewed from the rear is intercepted on entry and is naturally mirrored by the camera.
        if ((face > 3.5 && face < 7.0) || abs(face - 3.0) < 0.1) {
            float3 ink;
            float m = frost(hit, face, ink);
            result += throughput * ink * (face < 3.5 ? float3(1.0) : float3(0.64, 0.74, 0.90)) * m;
            throughput *= 1.0 - m;
        }
        float3 point = hit + rayDir * 0.0003;
        for (int bounce = 0; bounce < 4; bounce++) {
            float exitT = 10000.0;
            float3 exitN = float3(0, 0, -1);
            float exitFace = 0.0;
            for (int i = 0; i < 31; i++) {
                float4 pl = u.uPlanes[i];
                if (i == 30)
                    pl.w = -u.uDrop.x;
                float den = dot(pl.xyz, rayDir);
                if (den > 0.000001) {
                    float t = (pl.w - dot(pl.xyz, point)) / den;
                    if (t < exitT && t >= -0.0001) {
                        exitT = max(t, 0.0);
                        exitN = pl.xyz;
                        exitFace = float(i);
                    }
                }
            }
            if (exitT > 1000.0)
                break;
            point += rayDir * exitT;
            throughput *= exp(-float3(0.020, 0.009, 0.003) * exitT);
            if ((exitFace > 3.5 && exitFace < 7.0) || abs(exitFace - 3.0) < 0.1) {
                float3 ink;
                float m = frost(point, exitFace, ink);
                result += throughput * ink * m;
                throughput *= 1.0 - m;
            }
            if (exitFace > 30.0 - 0.5) {
                result += throughput * float3(0.65, 1.05, 1.8) * u.uUnder.x * 2.4;
                throughput *= 0.12;
            }
            float cosI = dot(rayDir, exitN);
            float3 outgoing = transmission(rayDir, -exitN, 1.52);
            float split = dot(outgoing, outgoing) > 0.1 ? fresnel(cosI, 1.52) : 1.0;
            if (split < 1.0)
                result += throughput * spectrum(rayDir, -exitN, point) * (1.0 - split);
            throughput *= split;
            if (max(throughput.r, max(throughput.g, throughput.b)) < 0.012)
                break;
            rayDir = reflect(rayDir, exitN);
            point += rayDir * 0.0003;
        }
        result += throughput * float3(0.013, 0.028, 0.09) * (0.05 + u.uLamp.x + u.uUnder.x);
        // Distance to a neighbouring plane in canvas points: constant-width lit seams at every yaw.
        float edge = 100.0;
        float edgeFace = -1.0;
        float den = dot(normal, rd);
        float3 dx = toObject(float3(1, 0, 0)) / u.uUnit.x;
        float3 dy = toObject(float3(0, 1, 0)) / u.uUnit.x;
        float safeDen = abs(den) < 0.00001 ? -0.00001 : den;
        float3 hx = enterT * (dx - rd * dot(normal, dx) / safeDen);
        float3 hy = enterT * (dy - rd * dot(normal, dy) / safeDen);
        for (int i = 0; i < 30; i++) {
            float4 pl = u.uPlanes[i];
            float grad = length(float2(dot(pl.xyz, hx), dot(pl.xyz, hy)));
            if (abs(float(i) - face) > 0.1 && grad > 0.00001) {
                float candidate = abs(pl.w - dot(pl.xyz, hit)) / grad;
                if (candidate < edge) {
                    edge = candidate;
                    edgeFace = float(i);
                }
            }
        }
        float lampFacing = smoothstep(-0.30, 0.8, dot(toView(normal), safeUnit(float3(-0.5, 0.8, 0.4))));
        float edgeWidth = mix(0.15, 0.45, lampFacing);
        float edgeLight = exp(-edge * edge / edgeWidth) + 0.12 * exp(-edge * edge / 5.0);
        float etched = 1.0;
        if (u.uEtch.x < 0.38) {
            float phase = atan2(hit.x, hit.y - 1.34) / 6.2831853;
            if (phase < 0.0)
                phase += 1.0;
            etched = 0.55 + 0.45 * smoothstep(phase - 0.02, phase + 0.02, u.uEtch.x / 0.38);
        }
        float edgeFacing = 0.16 + 0.82 * lampFacing;
        edgeFacing *= 0.35 + 0.65 * smoothstep(-0.6, 0.65, hit.y * 0.4 - hit.x);
        if (abs(face - 3.0) < 0.1 || abs(edgeFace - 3.0) < 0.1)
            edgeFacing = max(edgeFacing, 0.85);
        if ((face < 0.5 && abs(edgeFace - 3.0) < 0.1) || (abs(face - 3.0) < 0.1 && edgeFace < 0.5))
            edgeFacing *= 0.16 + 0.84 * exp(-(hit.x + 0.50) * (hit.x + 0.50) * 5.0);
        result += float3(0.68, 0.91, 1.12) * edgeLight * (0.015 + u.uLamp.x * 1.20 + u.uUnder.x * 1.25) *
                  etched * edgeFacing;
        float foot = max(hit.y - u.uDrop.x, 0.0);
        float body = 0.030 + 0.050 * exp(-edge / 10.0) + 0.016 * smoothstep(1.7, 2.9, hit.y);
        result += float3(0.65, 0.80, 1.0) * body * u.uLamp.x;
        // Light injected at the cut face spreads through the full slab during the rise.
        float pipe = u.uUnder.x * (0.35 + 1.4 * exp(-foot * 1.5));
        float fire = exp(-foot * 6.0) * (0.52 * u.uLamp.x + 1.25 * u.uUnder.x);
        result += float3(0.55, 0.91, 1.25) * pipe + float3(0.40, 0.85, 1.25) * fire;
        result +=
            float3(0.11, 0.025, 0.015) * exp(-squared((foot - 0.055) * 35.0)) * (u.uLamp.x + u.uUnder.x);
        float2 delta = hit.xy - float2(u.uFinger.x, u.uFinger.y + u.uDrop.x);
        float radius = length(delta);
        result += float3(0.4, 0.75, 1.1) * u.uTouch.x *
                  (exp(-radius * radius * 17.0) * 0.45 + exp(-radius * radius * 1200.0) * 2.5);
        float ring = exp(-squared((radius - (1.0 - u.uFlash.x) * 2.8) * 42.0)) * sqrt(max(u.uFlash.x, 0.0));
        result += float3(0.85, 1.1, 1.6) * (ring * 1.8 + edgeLight * u.uFlash.x * 3.0);
        return result;
    }
    float3 caustic(float3 p) {
        float3 c = float3(0);
        float3 light = toObject(safeUnit(float3(0.16, 0.9, 0.4)));
        for (int j = 0; j < 4; j++) {
            float a = float(j);
            float3 normal = safeUnit(float3((a - 1.5) * 0.31, 0.23, 0.8));
            float3 bend = transmission(-light, normal, 0.66);
            float2 centre = float2((a - 1.5) * 0.2, 0.0) + bend.xz / max(abs(bend.y), 0.18) * 0.55;
            float2 q = p.xz - centre;
            float streak = exp(-q.x * q.x * 5.0 - q.y * q.y * 180.0);
            c += float3(0.22, 0.4, 0.65) * streak;
            c += float3(0.15, 0.04, 0.02) * exp(-q.x * q.x * 5.0 - (q.y - 0.022) * (q.y - 0.022) * 350.0);
        }
        return c * u.uLamp.x * 0.34;
    }
    float3 stone(float3 p, float3 n, float3 rd) {
        // Triplanar mapping blends across the chamfer; top and sides share world coordinates.
        float3 weights = abs(n);
        weights *= weights;
        weights /= max(weights.x + weights.y + weights.z, 0.001);
        float3 tex = sampleMarble((p.xz * 0.46 + 0.5) * 768.0).rgb * weights.y;
        tex += sampleMarble((float2(p.x, p.y + p.z) * 0.46 + 0.5) * 768.0).rgb * weights.z;
        tex += sampleMarble((float2(p.z, p.y + p.x) * 0.46 + 0.5) * 768.0).rgb * weights.x;
        float3 light = toObject(safeUnit(float3(0.16, 0.9, 0.4)));
        float diffuse = max(dot(n, light), 0.0);
        float3 c =
            tex * (float3(0.013, 0.022, 0.045) + u.uLamp.x * (float3(0.45, 0.49, 0.58) + diffuse * 0.48));
        float3 h = safeUnit(light - safeUnit(rd));
        c += float3(0.5, 0.65, 0.85) * pow(max(dot(n, h), 0.0), 65.0) * u.uLamp.x * 0.35;
        float chamfer = step(0.1, n.y) * step(n.y, 0.9);
        c += float3(0.24, 0.29, 0.36) * chamfer * u.uLamp.x;
        if (n.y > 0.5) {
            float slot = exp(-p.z * p.z * 220.0) * smoothstep(0.51, 0.39, abs(p.x));
            c *= 1.0 - slot * 0.65;
            c *= 1.0 - 0.3 * exp(-p.x * p.x * 3.0 - (p.z + 0.15) * (p.z + 0.15) * 12.0);
            float footHalo = exp(-p.x * p.x * 5.0 - p.z * p.z * 32.0);
            c += caustic(p) + float3(0.16, 0.32, 0.48) * footHalo * (u.uLamp.x * 0.55 + u.uUnder.x);
            c += float3(0.25, 0.65, 1.25) * u.uUnder.x * slot;
            c += float3(0.14, 0.20, 0.28) * exp(-p.x * p.x * 7.0 - (p.z - 0.26) * (p.z - 0.26) * 35.0) *
                 u.uLamp.x;
        }
        if (n.z > 0.95 && abs(p.z - 0.94) < 0.02) {
            float2 uv = float2(p.x / 0.20 + 0.5, 0.5 - (p.y + 0.17) / 0.20);
            float markAA = 32.0 / (u.uUnit.x * u.uPixelRatio.x);
            float m = smoothstep(0.5 - markAA, 0.5 + markAA, tile(uv, 2.0)) * step(0.0, uv.x) *
                      step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
            float3 steel = float3(0.12, 0.20, 0.30) * (0.12 + u.uLamp.x) +
                           float3(0.25, 0.38, 0.50) * exp(-squared((uv.y - 0.35) * 10.0)) * u.uLamp.x +
                           float3(0.4, 0.55, 0.72) * u.uLamp.x * pow(max(dot(n, h), 0.0), 12.0);
            c = mix(c, steel, m);
        }
        // Preserve the white marble's veins through the lamp's 25% overshoot.
        if (u.uLamp.x > 1.0) {
            float3 baseline = c / u.uLamp.x;
            float3 headroom = max(float3(0.01), float3(0.995) - baseline);
            c = baseline + headroom * (1.0 - exp(-(c - baseline) / headroom));
        }
        return c;
    }
    float star(float2 d, float size) {
        float2 a = abs(d) / max(size, 0.001);
        float core = exp(-dot(a, a) * 8.0);
        float rays = exp(-min(a.x, a.y) * 28.0) * exp(-max(a.x, a.y) * 2.7);
        return core + rays * 0.6;
    }
    float flashStar(float2 d, float size) {
        float2 a = abs(d) / max(size, 0.001);
        return exp(-dot(a, a) * 200.0) + exp(-min(a.x, a.y) * 70.0 - max(a.x, a.y) * 2.7) * 0.75;
    }
    float3 glints(float2 xy) {
        float3 c = float3(0);
        for (int j = 0; j < 8; j++) {
            float4 pin = u.uFire[j];
            if (pin.z * (u.uLamp.x + u.uUnder.x * 0.7) > 0.0001) {
                float2 d = xy - pin.xy;
                if (dot(d, d) < pin.w * pin.w * 9.0) {
                    float spread = j == 0 ? 0.7 : 0.95;
                    float3 sparkle = float3(star(d + float2(spread, 0), pin.w), star(d, pin.w),
                                            star(d - float2(spread, 0), pin.w));
                    if (j == 0 && u.uFlash.x > 0.0) {
                        float3 burst = float3(flashStar(d + float2(spread, 0), pin.w), flashStar(d, pin.w),
                                              flashStar(d - float2(spread, 0), pin.w));
                        sparkle = mix(sparkle, burst, u.uFlash.x);
                    }
                    c += sparkle * float3(0.86, 0.94, 1.0) * pin.z * (u.uLamp.x + u.uUnder.x * 0.7) *
                         (1.0 + 0.05 * sin(u.uTime.x * 0.83775804));
                }
            }
        }
        if (u.uEtch.x > 0.0 && u.uEtch.x < 0.38) {
            float phase = u.uEtch.x / 0.38 * 7.0;
            float2 a = float2(0.05, 2.90), b = float2(0.76, 2.46);
            if (phase >= 1.0) {
                a = b;
                b = float2(0.86, 1.36);
            }
            if (phase >= 2.0) {
                a = b;
                b = float2(0.40, 0.0);
            }
            if (phase >= 3.0) {
                a = b;
                b = float2(-0.40, 0.0);
            }
            if (phase >= 4.0) {
                a = b;
                b = float2(-0.86, 1.34);
            }
            if (phase >= 5.0) {
                a = b;
                b = float2(-0.74, 2.52);
            }
            if (phase >= 6.0) {
                a = b;
                b = float2(0.05, 2.90);
            }
            float2 pt = mix(a, b, fract(phase));
            float z = min(0.39 - 0.075 * pt.y, 0.015 + 0.26 * pt.y - 0.22 * abs(pt.x));
            if (pt.y >= u.uDrop.x - 0.005)
                c += float3(0.6, 1.0, 1.5) *
                     star(xy - projected(float3(pt.x, pt.y - u.uDrop.x, z)), 10.0 * u.uUnit.x / 100.0) * 2.0;
        }
        if (u.uFlash.x > 0.0001) {
            float2 fd = xy - projected(float3(u.uFinger.x, u.uFinger.y, 0.28));
            if (dot(fd, fd) < u.uUnit.x * u.uUnit.x * 0.5) {
                float size = 45.0 * u.uUnit.x / 108.0;
                c += float3(flashStar(fd + float2(0.8, 0), size), flashStar(fd, size),
                            flashStar(fd - float2(0.8, 0), size)) *
                     float3(0.9, 1.0, 1.2) * u.uFlash.x * 2.5;
            }
        }
        return c;
    }
    float4 trophy(float2 xy) {
        float2 q = (xy - u.uOrigin.xy) / u.uUnit.x;
        float3 ro = toObject(float3(0, 0, u.uCamera.x));
        float3 rd = toObject(float3(q.x, -q.y, -u.uCamera.x));
        float3 rgb = float3(0);
        float alpha = 0.0;
        // The floor is transparent except for a tight contact shadow and refracted spill.
        if (q.y > -0.36 && rd.y < -0.0001) {
            float ft = (-0.305 - ro.y) / rd.y;
            float3 floorP = ro + rd * ft;
            float rad = length(floorP.xz / float2(1.15, 1.08));
            float shadow = 0.55 * exp(-pow(rad / 0.95, 4.0));
            float3 spill = float3(0);
            if (rad < 1.9)
                spill = caustic(floorP) * exp(-pow(rad / 1.2, 4.0)) * 0.4;
            float ca = max(spill.r, max(spill.g, spill.b));
            alpha = clamp(shadow + ca, 0.0, 0.8);
            rgb = spill;
        }
        float nt, ft;
        float3 nn, fn;
        if (q.y > -0.36 && q.y < 0.60 && abs(q.x) < 1.15) {
            float valid = baseHit(ro, rd, nt, ft, nn, fn);
            float cov = coverage(nt, ft, nn, fn, rd) * valid;
            if (cov > 0.0 && ft > 0.0) {
                float3 p = ro + rd * nt;
                float3 c = stone(p, nn, rd);
                rgb = mix(rgb, c, cov);
                alpha = mix(alpha, 1.0, cov);
            }
        }
        // Conservative all-yaw screen bounds avoid the 31-plane crystal test over the floor and margins.
        float crystalTop = max(0.0, 2.94 - u.uDrop.x);
        if (abs(q.x) < 1.30 && q.y < 0.15 && q.y > -crystalTop * 1.13 - 0.13 && crystalTop > 0.0) {
            float face;
            float3 cro = ro + float3(0, u.uDrop.x, 0);
            float cv = crystalHit(cro, rd, nt, ft, nn, fn, face);
            float cc = coverage(nt, ft, nn, fn, rd) * cv;
            // Bloom uses the exact projected silhouette, including the moving cut at the marble.
            // This remains well-defined at grazing rays, edge-on views and rise overshoot.
            if (cc < 1.0) {
                float outsidePx = 10000.0;
                for (int i = 0; i < 32; i++) {
                    float4 segment = u.uRim[i];
                    float2 v = segment.zw - segment.xy;
                    float t = clamp(dot(xy - segment.xy, v) / max(dot(v, v), 0.0001), 0.0, 1.0);
                    outsidePx = min(outsidePx, length(xy - segment.xy - t * v));
                }
                float air = (0.10 * exp(-outsidePx * outsidePx / 100.0) * smoothstep(0.8, 1.8, -q.y) +
                             0.22 * exp(-outsidePx * outsidePx / 5.0)) *
                            (u.uLamp.x + u.uUnder.x) * (1.0 - cc);
                air *= smoothstep(0.12, -0.02, q.y);
                rgb += float3(0.45, 0.73, 1.0) * air;
                alpha = max(alpha, air);
            }
            if (cc > 0.0 && ft > 0.0) {
                float3 col = glass(cro, rd, nt, nn, face);
                // Gentle highlight shoulder retains detail above nominal lamp intensity.
                col = col / (1.0 + col * 0.12);
                rgb = mix(rgb, col, cc);
                alpha = mix(alpha, 1.0, cc);
            }
        }
        float3 flare = glints(xy);
        float fa = clamp(max(flare.r, max(flare.g, flare.b)), 0.0, 1.0);
        rgb += flare;
        alpha = max(alpha, fa);
        return float4(min(rgb, float3(alpha)), alpha);
    }
};

struct CrystalTrophyRevealControl {
    float2 uOrigin;
    float2 uSize;
    float uRadius;
    float uYaw;
    float uLamp;
    float uFlash;
    float uPress;
    float uSheen;
    float uGem;
    float2 uPressPoint;

    float roundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    float octagon(float2 p, float r) {
        float2 q = abs(p);
        return max(max(q.x, q.y), (q.x + q.y) * 0.70710678) - r;
    }
    float engraving(float2 p) {
        float2 a = float2((p.x + p.y) * 0.70710678, (p.x - p.y) * 0.70710678);
        return min(length(float2(max(abs(a.x) - 5.5, 0.0), a.y)),
                   length(float2(a.x, max(abs(a.y) - 5.5, 0.0)))) -
               1.5;
    }
    float star(float2 p, float strength) {
        float core = exp(-dot(p, p) / 0.28);
        float arms = max(exp(-abs(p.x) * 5.5 - abs(p.y) * 0.8), exp(-abs(p.y) * 5.5 - abs(p.x) * 0.8));
        return (core + arms * 0.65) * strength;
    }
    float4 draw(float2 xy) {
        float2 p = xy - uOrigin;
        float2 q = p - uSize * 0.5;
        float d = uGem > 0.5 ? octagon(q, 17.2) : roundedBox(q, uSize * 0.5 - 0.8, uRadius);
        float cover = 1.0 - smoothstep(-0.35, 0.35, d);
        float v = clamp(p.y / max(uSize.y, 1.0), 0.0, 1.0);
        float h = p.x / max(uSize.x, 1.0);
        // Premultiplied pigments retain the floor/cloth behind the crystal instead of painting it black.
        float upperMix = smoothstep(0.0, 0.45, v);
        float lowerMix = smoothstep(0.45, 1.0, v);
        float alpha = mix(mix(0.22, 0.16, upperMix), 0.60, lowerMix);
        float3 pigment =
            mix(mix(float3(150, 170, 255) / 255.0 * 0.22, float3(46, 60, 140) / 255.0 * 0.16, upperMix),
                float3(6, 8, 26) / 255.0 * 0.60, lowerMix);
        float glowX = (h - 0.39 - 0.065 * sin(uYaw)) / 0.32;
        float glowY = (v - 0.40) / 0.44;
        pigment += exp(-glowX * glowX - glowY * glowY) * float3(0.018, 0.025, 0.070);
        float band = exp(-max(p.y - 2.0, 0.0) / 4.8) * 0.10;
        pigment += band * float3(0.72, 0.82, 1.0);
        float rim = 1.0 - smoothstep(0.6, 1.65, abs(d + 0.85));
        float upper = 1.0 - smoothstep(0.03, 0.70, v);
        float hot = exp(-((h - 0.52) * (h - 0.52)) / 0.065);
        float bevel = rim * upper * (0.18 + 0.62 * hot);
        // Twelve-point end facets with an inner cut face; a second line makes thickness legible.
        float edgeX = min(p.x, uSize.x - p.x);
        float cut = (1.0 - smoothstep(11.5, 12.5, edgeX)) * smoothstep(1.0, 4.5, -d);
        pigment += cut * float3(0.044, 0.058, 0.10) * (0.35 + 0.65 * (1.0 - v));
        pigment += exp(-abs(edgeX - 12.0) * 2.6) * float3(0.05, 0.073, 0.12) * smoothstep(3.0, 10.0, p.y) *
                   (1.0 - v);
        float lip = exp(-abs(d + 0.9) * 3.0) * smoothstep(0.80, 0.98, v);
        pigment *= 1.0 - lip * 0.65;
        float cyan = exp(-abs(d + 2.3) * 3.0) * smoothstep(0.82, 0.98, v) * 0.22;
        pigment += cyan * float3(0.25, 0.64, 0.83);
        if (uGem > 0.5) {
            float ax = abs(q.x), ay = abs(q.y);
            float diagonal = (ax + ay) * 0.70710678;
            float2 normal = diagonal > max(ax, ay) ? sign(q) * 0.70710678
                            : ax > ay              ? float2(sign(q.x), 0.0)
                                                   : float2(0.0, sign(q.y));
            float lit = max(0.0, dot(normal, float2(-0.6, -0.8)));
            float facets = smoothstep(-3.5, -2.7, d);
            pigment = mix(pigment + float3(0.019, 0.027, 0.060),
                          float3(0.024, 0.038, 0.070) + lit * float3(0.18, 0.23, 0.32), facets);
            alpha = 0.68;
            bevel = rim * (0.045 + lit * 0.66);
            pigment += exp(-abs(d + 3.2) * 3.5) * (0.03 + lit * 0.13) * float3(0.55, 0.76, 1.0);
        }
        pigment = pigment * (1.0 - bevel) + bevel * float3(0.88, 0.94, 1.0);
        alpha = alpha + (1.0 - alpha) * bevel;
        float2 pressDelta = (p - uPressPoint * uSize) / (uGem > 0.5 ? float2(14.0) : float2(55.0, 25.0));
        float spot = exp(-dot(pressDelta, pressDelta)) * max(0.0, uPress);
        pigment += spot * float3(0.08, 0.11, 0.19);
        float sheenX = h + v * 0.17 - (uSheen * 1.5 - 0.25);
        float sheen = uSheen < 0.0 ? 0.0 : exp(-sheenX * sheenX / 0.004);
        pigment += sheen * float3(0.065, 0.09, 0.14);
        if (uGem > 0.5) {
            float frost = 1.0 - smoothstep(-0.22, 0.22, engraving(q));
            float wall = (1.0 - smoothstep(-0.2, 0.2, engraving(q - float2(0.6, 0.7)))) * (1.0 - frost);
            float inset = (1.0 - smoothstep(-0.2, 0.5, engraving(q + float2(0.45, 0.5)))) * (1.0 - frost);
            pigment *= 1.0 - inset * 0.80;
            pigment = mix(pigment, float3(0.73, 0.86, 1.0), wall * 0.85);
            // PALETTE.frost, with a subdued centre so the luminous cut wall remains visible.
            pigment = mix(pigment, float3(244, 248, 255) / 255.0 * (0.82 + v * 0.08), frost);
            alpha = max(alpha, max(frost, wall));
        }
        // Analytic floor shadow and caustic extend outside the bar's silhouette; no blur layer allocation.
        float floorY = p.y - uSize.y - 6.0;
        float floorX = q.x / max(uSize.x * 0.46, 1.0);
        float shadow = uGem > 0.5 ? 0.0 : exp(-floorX * floorX * 1.5 - floorY * floorY / 17.0) * 0.40;
        float causticX = (h - 0.46) / 0.29;
        float causticY = (p.y - uSize.y - 3.5) / 2.3;
        float caustic =
            uGem > 0.5 ? 0.0 : exp(-causticX * causticX - causticY * causticY) * 0.07 * (0.6 + uLamp * 0.4);
        float3 rgb = float3(0.32, 0.52, 1.0) * caustic;
        float outAlpha = shadow + caustic * (1.0 - shadow);
        rgb = pigment * cover + rgb * (1.0 - alpha * cover);
        outAlpha = alpha * cover + outAlpha * (1.0 - alpha * cover);
        float2 glintPoint = uGem > 0.5 ? float2(7.0, 3.1) : float2(uSize.x * (0.60 + 0.25 * sin(uYaw)), 1.0);
        float glint =
            star(p - glintPoint, uGem > 0.5 ? 0.15 + uFlash * 0.65 : 0.18 + uLamp * 0.24 + uFlash * 0.58);
        rgb += glint * float3(0.79, 0.90, 1.0);
        outAlpha = max(outAlpha, min(glint, 1.0));
        // Additive highlights still obey premultiplied-alpha requirements at the silhouette.
        outAlpha = max(outAlpha, min(1.0, max(rgb.r, max(rgb.g, rgb.b))));
        return float4(float3(min(rgb, float3(outAlpha))), float(outAlpha));
    }
};

struct CrystalTrophyRevealVertex {
    float4 position [[position]];
    float2 uv;
};
vertex CrystalTrophyRevealVertex crystalTrophyRevealVertex(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    return {float4(p * float2(2, -2) + float2(-1, 1), 0, 1), p};
}
fragment float4 crystalTrophyRevealFragment(CrystalTrophyRevealVertex in [[stage_in]],
                                            constant CrystalTrophyRevealUniforms &u [[buffer(0)]],
                                            texture2d<float> engraving [[texture(0)]],
                                            texture2d<float> marble [[texture(1)]]) {
    CrystalTrophyRevealScene scene{u, engraving, marble};

    float2 p = in.uv * u.uSize.xy;
    float2 centre = u.uOrigin.xy - float2(0, 1.45 * u.uUnit.x);
    float2 q = centre + (p - centre - float2(0, u.uCameraY.x)) / u.uCameraScale.x;
    float4 col = scene.stageBack(q);
    float4 trophy = scene.trophy(q);
    col = trophy + col * (1 - trophy.a);
    float4 dust = scene.stageDust(q);
    col = dust + col * (1 - dust.a);
    float4 curtain = scene.stageCurtain(q);
    col = curtain + col * (1 - curtain.a);
    if (u.uControlAlpha.x > 0.0001) {
        float2 shareCentre = u.uShare.xy + u.uShare.zw * 0.5;
        float2 sp = shareCentre +
                    (p - shareCentre - float2(0, u.uReduced.x > 0.5 ? 0 : 16 * (1 - u.uControlAlpha.x))) /
                        (1 - 0.03 * u.uSharePress.x);
        CrystalTrophyRevealControl share{u.uShare.xy,
                                         u.uShare.zw,
                                         16,
                                         atan2(u.uTurn.y, u.uTurn.x),
                                         u.uLamp.x,
                                         u.uFlash.x,
                                         u.uSharePress.x,
                                         u.uSheen.x,
                                         0,
                                         u.uSharePoint.xy};
        float4 bar = share.draw(sp) * float(u.uControlAlpha.x);
        col = bar + col * (1 - bar.a);
        float scale = (u.uReduced.x > 0.5 ? 1 : 0.9 + 0.1 * u.uControlAlpha.x) * (1 - 0.03 * u.uClosePress.x);
        float2 cp = u.uClose.xy + (p - u.uClose.xy) / scale;
        CrystalTrophyRevealControl close{u.uClose.xy - float2(18),
                                         float2(36),
                                         18,
                                         atan2(u.uTurn.y, u.uTurn.x),
                                         u.uLamp.x,
                                         u.uFlash.x,
                                         u.uClosePress.x,
                                         -1,
                                         1,
                                         u.uClosePoint.xy};
        float4 gem = close.draw(cp) * float(u.uControlAlpha.x);
        col = gem + col * (1 - gem.a);
    }
    return float4(col.rgb, 1);
}
