#include <metal_stdlib>
using namespace metal;

constant float PrismSphereGeneratingPI = 3.141592653589793;
constant float PrismSphereGeneratingOUTER = 0.995;
constant float PrismSphereGeneratingINNER = 0.83;

// Analytic intersections and frost shading translated directly from prism-shader.ts.
struct PrismSphereGeneratingMaterial {
    float2 uSize;
    float2 uCentre;
    float uRadius;
    float uScale;
    float uClock;
    float uEnergy;
    float uCharge;
    float uFlash;
    float uRing;
    float3x3 uRot;
    float uFan;
    float uSweep;
    float uSweepPower;
    float2 uLight;
    float sq(float v) {
        return v * v;
    }
    float noisePixel(float2 p) {
        return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5;
    }
    float3 backdrop(float2 p) {
        float2 uv = p / uSize;
        float a = fmod(uClock, 5.0) * (2.0 * PrismSphereGeneratingPI / 5.0);
        float floorLight = exp(-sq((uv.x - 0.5) / 0.46) - sq((uv.y - 0.75) / 0.23));
        float pillar = exp(-sq((uv.x - 0.5) / 0.16) - sq((uv.y - 0.74) / 0.24));
        float streaks = 0.93 + 0.04 * sin(uv.x * 65.0 + sin(a)) + 0.03 * cos(uv.x * 98.0 - cos(a));
        float gate = smoothstep(0.24, 0.49, uv.y) * (1.0 - smoothstep(0.85, 1.0, uv.y));
        return (float3(0.112, 0.030, 0.252) * floorLight
            + float3(0.017, 0.005, 0.038) * pillar * streaks) * gate;
    }
    half4 backdropAt(float2 xy) {
        float2 q = (xy - uCentre) / (uRadius * uScale);
        float rr = length(q);
        float3 c = backdrop(xy);
        float halo = exp(-sq((rr - 1.01) / (0.25 - 0.06 * uCharge + 0.15 * uFlash)));
        c += float3(0.085, 0.023, 0.18) * halo * smoothstep(0.94, 1.06, rr)
            * (0.20 + 0.12 * uEnergy + 0.8 * uFlash);
        float pool = exp(-sq(q.x / 1.15) - sq((q.y - 1.16) / 0.32));
        c += float3(0.015, 0.004, 0.033) * pool * (0.65 + uEnergy);
        if (uRing > 0.0 && uRing < 1.0) {
            float delta = (rr - (1.03 + 1.37 * uRing)) * uRadius * uScale;
            float fade = 0.65 * sq(1.0 - uRing);
            c += fade * (float3(0.60, 0.46, 0.88) * exp(-sq(delta / 0.45))
                + float3(0.06, 0.018, 0.14) * exp(-sq(delta / 2.3)));
        }
        c += noisePixel(xy) / 255.0 * smoothstep(0.0, 0.01, c.b);
        return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
    }
    float3 coreSpace(float3 v) {
        return float3(dot(uRot[0], v), dot(uRot[1], v), dot(uRot[2], v));
    }
    float2 sphereRoots(float3 ro, float3 rd, float rad) {
        float b = dot(ro, rd);
        float disc = b * b - dot(ro, ro) + rad * rad;
        if (disc < 0.0) {
            return float2(100.0);
        }
        float rt = sqrt(disc);
        return float2(-b - rt, -b + rt);
    }
    bool inShell(float3 p) {
        return abs(atan2(p.x, -p.z)) <= uFan * 0.5 + 0.00001;
    }
    void orderHits(thread float2 &a, thread float2 &b) {
        if (a.x > b.x) {
            float2 temp = a;
            a = b;
            b = temp;
        }
    }
    float3 smoke(float2 q) {
        float a = fmod(uClock, 5.0) * (2.0 * PrismSphereGeneratingPI / 5.0);
        q += 0.10 * float2(sin(q.y * 3.0 + a), cos(q.x * 3.0 - a));
        float reflected = exp(-sq((length(float2((q.x - 0.03) / 0.65, (q.y - 0.01) / 0.58)) - 1.0) / 0.23));
        float cloud = exp(-sq((q.x + 0.12 + 0.08 * sin(a)) / 0.64) - sq((q.y - 0.32) / 0.31));
        float shade = exp(-sq((q.x + 0.18) / 0.48) - sq((q.y + 0.13) / 0.32));
        float blueY = 0.18 - 0.45 * cos(2.0 * a) - 0.1 * sin(a);
        float blueX = -0.25 - 0.4 * cos(2.0 * a) + 0.2 * sin(a);
        float blue = exp(-sq((q.x - blueX) / 0.35) - sq((q.y - blueY) / 0.28));
        float grazingGlow = 1.0 - abs(uRot[2].z);
        float bluePatch = exp(-sq((q.x + 0.65 - 0.08 * sin(a)) / 0.23)
            - sq((q.y - 0.10 + 0.43 * cos(a)) / 0.20));
        float cloudDetail = sin(q.x * 4.0 + 0.8 * sin(q.y * 3.0 + a))
            * cos(q.y * 4.8 - 0.6 * cos(a));
        // The deepest shadow of the hollow sits just inside the mouth, wherever the mouth has turned.
        float2 deep = q - 0.25 * uRot[2].xy - float2(-0.08, -0.10);
        float hollow = exp(-dot(deep, deep) / 0.16);
        float3 lightInside = float3(0.008) + float3(0.038, 0.023, 0.13) * bluePatch
            + float3(0.024, 0.019, 0.033) * cloudDetail
            + float3(0.035, 0.026, 0.045) * grazingGlow + float3(0.25, 0.155, 0.39) + float3(0.19, 0.13, 0.29) * cloud
            - float3(0.15, 0.105, 0.18) * shade + float3(0.025, 0.005, 0.28) * blue + float3(0.06, 0.045, 0.09) * reflected;
        return lightInside * mix(float3(1.0), float3(0.36, 0.32, 0.56), 0.85 * hollow);
    }
    float3 shadeSheet(float3 p, float material, float depth) {
        float radialSize = length(p);
        float3 radial = p / radialSize;
        float az = atan2(p.x, -p.z);
        float polar = acos(clamp(-radial.y, -1.0, 1.0)) / PrismSphereGeneratingPI;
        float3 tangent = float3(-p.z, 0.0, p.x) / max(length(p.xz), 0.00001);
        float ribAngle = 60.0 * az + 0.45 * sin(3.0 * az) + 0.22 * sin(5.0 * polar);
        float periodPx = 2.0 * PrismSphereGeneratingPI * length(p.xz) * uRadius * uScale / 60.0;
        periodPx *= sqrt(max(0.0, 1.0 - sq((uRot * tangent).z)));
        // Towards a hinge pole the pleats narrow to nothing: let them melt into the frost well before that.
        float resolved = smoothstep(1.6, 6.0, periodPx) * (1.0 - 0.48 * depth);
        // Rounded asymmetric leaf profile: broad shoulders, a soft trough, no painted white stripe.
        float relief = (sin(ribAngle) + 0.16 * sin(2.0 * ribAngle)) / 1.08;
        float crest = 0.5 + 0.5 * cos(ribAngle) * resolved;
        float fanLight = smoothstep(PrismSphereGeneratingPI, 1.7 * PrismSphereGeneratingPI, uFan);
        float strength = material < 1.5 ? 0.92 * (0.80 + 0.20 * fanLight) : 0.35 * (0.52 + 0.48 * fanLight);
        strength *= 0.8 + 0.2 * sin(az * 2.0 + polar * 5.0);
        float3 n = normalize(radial + tangent * relief * strength * resolved);
        if (material > 1.5 && material < 2.5) {
            n = -n;
        }
        if (material > 2.5) {
            float endAz = (material < 3.5 ? -0.5 : 0.5) * uFan;
            n = float3(cos(endAz), 0.0, sin(endAz)) * (material < 3.5 ? -1.0 : 1.0);
        }
        float3 nw = uRot * n;
        float3 pw = uRot * p;
        float3 key = normalize(float3(-0.18 + uLight.x * 0.45, -0.90 + uLight.y * 0.4, 0.65));
        float wrapped = clamp((dot(nw, key) + 0.65) / 1.65, 0.0, 1.0);
        float sheen = pow(max(0.0, dot(nw, normalize(key + float3(0.0, 0.0, 1.0)))), 5.0);
        float3 c = float3(0.34, 0.20, 0.57) + float3(0.30, 0.28, 0.34) * wrapped;
        c *= mix(float3(0.88, 0.83, 0.96), float3(1.0), crest);
        if (material < 1.5) {
            // The outside is relief under a key light: the lit flank of a pleat goes to near white,
            // the far flank and the fold to violet, and the contrast eases off away from the light.
            float lit = clamp((dot(nw, key) + 0.22) / 1.22, 0.0, 1.0);
            float3 relit = float3(0.25, 0.15, 0.56) + float3(0.66, 0.64, 0.50) * lit * lit;
            relit *= mix(float3(0.56, 0.47, 0.90), float3(1.0), smoothstep(0.0, 0.7, crest));
            c = mix(c, relit, 0.85 * resolved * (1.0 - 0.6 * depth));
            // The side of the ball turned from the key falls into a soft violet shadow, as in the reference.
            float turned = smoothstep(-0.70, 0.30, dot(uRot * radial, key));
            c *= mix(float3(0.50, 0.42, 0.72), float3(1.0), turned);
        }
        c += float3(0.12, 0.11, 0.14) * sheen;
        float lipBand = (1.0 - smoothstep(0.30, 0.50, uFan * 0.5 - abs(az))) * (1.0 - 0.95 * fanLight);
        if (material < 1.5) {
            c = mix(c, float3(0.744, 0.60, 0.93) + float3(0.07, 0.07, 0.08) * wrapped, lipBand);
        }
        if (material > 1.5 && material < 2.5) {
            c = mix(smoke(pw.xy), c, 0.12 + 0.60 * fanLight);
            // Inside the bowl the wall that turns its back on the key is in deep, smoky shadow.
            float hollowLit = smoothstep(-0.35, 0.75, dot(uRot * (-radial), key));
            c *= mix(float3(0.30, 0.26, 0.50), float3(1.0), hollowLit);
        }
        if (material > 2.5) {
            float edge = smoothstep(PrismSphereGeneratingINNER, PrismSphereGeneratingOUTER, radialSize);
            c = float3(0.52, 0.40, 0.72) + float3(0.20, 0.20, 0.21) * wrapped
                + float3(0.10, 0.10, 0.12) * edge;
        }
        float blue = exp(-sq((pw.x + 0.73) / 0.35) - sq((pw.y - 0.45) / 0.40));
        float pink = exp(-sq((pw.x - 0.88) / 0.22) - sq(pw.y / 0.75));
        c += float3(0.015, 0.008, 0.16) * blue + float3(0.075, 0.018, 0.065) * pink;
        c = mix(c, smoke(pw.xy), smoothstep(0.0, 0.5, -pw.z) * (0.92 - 0.78 * fanLight));
        if (material > 1.5 && material < 2.5) {
            c += float3(0.08, 0.06, 0.10) * (crest - 0.5) * resolved
                * exp(-sq((pw.y + 0.48) / 0.35));
        }
        // Behind the front sheet the same relief is still visible, softened by the frost.
        c = mix(c, c * float3(0.88, 0.83, 0.96), depth * 0.45);
        float band = exp(-sq((polar - uSweep) / 0.16));
        float gathered = exp(-sq(polar / 0.20));
        float innerLight = uSweepPower * band * 0.48 + uCharge * gathered * 0.40 + uFlash * 0.86;
        c = mix(c, float3(0.94, 0.91, 1.0), min(0.92, innerLight) * (0.7 + 0.3 * crest));
        return c * 0.95 * (1.0 + (uEnergy - 0.4) * 0.10);
    }
    float3 shellAt(float2 q) {
        float3 ro = coreSpace(float3(q, 2.0));
        float3 rd = coreSpace(float3(0.0, 0.0, -1.0));
        float2 outer = sphereRoots(ro, rd, PrismSphereGeneratingOUTER);
        float2 inner = sphereRoots(ro, rd, PrismSphereGeneratingINNER);
        float2 hits[6];
        hits[0] = float2(outer.x, 1.0);
        hits[1] = float2(outer.y, 1.0);
        hits[2] = float2(inner.x, 2.0);
        hits[3] = float2(inner.y, 2.0);
        for (int i = 0;
            i < 4;
            ++i) {
            if (hits[i].x > 10.0 || !inShell(ro + rd * hits[i].x)) {
                hits[i].x = 100.0;
            }
        }
        for (int i = 0;
            i < 2;
            ++i) {
            float az = (i == 0 ? -0.5 : 0.5) * uFan;
            float3 along = float3(sin(az), 0.0, -cos(az));
            float3 planeN = float3(cos(az), 0.0, sin(az));
            float denom = dot(rd, planeN);
            float t = 100.0;
            if (abs(denom) > 0.00001) {
                float candidate = -dot(ro, planeN) / denom;
                float3 p = ro + rd * candidate;
                float r2 = dot(p, p);
                if (candidate > 0.0 && r2 >= PrismSphereGeneratingINNER * PrismSphereGeneratingINNER && r2 <= PrismSphereGeneratingOUTER * PrismSphereGeneratingOUTER && dot(p, along) >= 0.0) {
                    t = candidate;
                }
            }
            hits[i + 4] = float2(t, 3.0 + float(i));
        }
        // Six-input sorting network: twelve comparisons, independent of ray/pose.
        orderHits(hits[1], hits[2]);
        orderHits(hits[4], hits[5]);
        orderHits(hits[0], hits[2]);
        orderHits(hits[3], hits[5]);
        orderHits(hits[0], hits[1]);
        orderHits(hits[3], hits[4]);
        orderHits(hits[2], hits[5]);
        orderHits(hits[0], hits[3]);
        orderHits(hits[1], hits[4]);
        orderHits(hits[2], hits[4]);
        orderHits(hits[1], hits[3]);
        orderHits(hits[2], hits[3]);
        float3 accum = float3(0.0);
        float transmission = 1.0;
        for (int i = 0;
            i < 4;
            ++i) {
            if (hits[i].x < 10.0) {
                float3 p = ro + rd * hits[i].x;
                float depth = float(i) / 3.0;
                float3 c = shadeSheet(p, hits[i].y, depth);
                float wallPath = 0.0;
                if (hits[i + 1].x < 10.0) {
                    float3 mid = ro + rd * ((hits[i].x + hits[i + 1].x) * 0.5);
                    if (dot(mid, mid) > PrismSphereGeneratingINNER * PrismSphereGeneratingINNER && inShell(mid)) {
                        wallPath = hits[i + 1].x - hits[i].x;
                    }
                }
                c = mix(c, float3(0.90, 0.85, 0.98), (1.0 - exp(-wallPath * 1.6)) * 0.25);
                float opacity = hits[i].y > 2.5 ? 0.73 : 0.66;
                if (hits[i].y > 2.5) {
                    opacity *= smoothstep(-0.15, 0.12, (uRot * p).z);
                }
                accum += transmission * opacity * c;
                transmission *= 1.0 - opacity;
            }
        }
        return accum + transmission * smoke(q);
    }
    half4 sphereAt(float2 xy) {
        float2 q = (xy - uCentre) / (uRadius * uScale);
        float rr = length(q);
        float aa = 1.5 / (uRadius * uScale);
        float alpha = 1.0 - smoothstep(1.0 - aa * 0.5, 1.0 + aa * 0.5, rr);
        if (alpha <= 0.0) {
            return half4(0.0);
        }
        float3 c = shellAt(q) * 0.98;
        float internalShade = exp(-sq((q.x - 0.62 + 0.1 * q.y) / 0.17) - sq((q.y - 0.12) / 0.56));
        float internalCloud = exp(-sq((q.x + 0.14) / 0.50) - sq((q.y - 0.44) / 0.24));
        c += float3(0.04, 0.033, 0.055) * internalCloud;
        c -= float3(0.12, 0.09, 0.12) * internalShade;
        float fanLight = smoothstep(PrismSphereGeneratingPI, 1.7 * PrismSphereGeneratingPI, uFan);
        c *= mix(float3(1.0), float3(0.92, 0.90, 0.95), fanLight);
        float forwardPole = smoothstep(0.40, 0.95, abs(uRot[1].z));
        float2 poleCloud = q - uRot[1].xy - float2(-0.17, -0.05);
        c -= float3(0.12, 0.09, 0.15) * exp(-sq(poleCloud.x / 0.35) - sq(poleCloud.y / 0.30))
            * fanLight * forwardPole;
        // One broad soft top light, with no window, dark border, arc or separate enclosure.
        float2 w = q - uLight * 0.10;
        float topLight = exp(-sq((w.x + 0.10) / 0.73) - sq((w.y + 0.80) / 0.39));
        float3 skin = coreSpace(float3(q, sqrt(max(0.001, 1.0 - dot(q, q)))));
        float skinAz = atan2(skin.x, -skin.z);
        float capRelief = sin(60.0 * skinAz + 0.45 * sin(3.0 * skinAz));
        float3 capTangent = float3(-skin.z, 0.0, skin.x) / max(length(skin.xz), 0.00001);
        float capPeriod = 2.0 * PrismSphereGeneratingPI * length(skin.xz) * uRadius * uScale / 60.0
            * sqrt(max(0.0, 1.0 - sq((uRot * capTangent).z)));
        float poleFade = smoothstep(1.2, 3.3, capPeriod);
        float capTexture = inShell(skin) ? 0.075 : 0.030;
        float turn = fmod(uClock, 5.0) * (2.0 * PrismSphereGeneratingPI / 5.0);
        capTexture *= 1.0 - 0.92 * exp(-2.0 * (1.0 - cos(turn - 0.25)));
        c = mix(c, float3(0.95, 0.91, 1.0) - capRelief * poleFade * capTexture, min(1.0, topLight * 1.07));
        float edgeLight = smoothstep(0.88, 1.0, rr);
        // The cool edge of the reference: electric blue low on the left, pink on the right, lilac above.
        float3 edgeColour = mix(float3(0.18, 0.12, 1.00), float3(0.50, 0.20, 0.74), smoothstep(-0.4, 0.8, q.x + 0.25 * q.y));
        edgeColour = mix(edgeColour, float3(0.50, 0.38, 0.85), smoothstep(0.2, 0.95, -q.y));
        c += edgeColour * (edgeLight * 0.36 + sq(edgeLight) * 0.24);
        c += float3(0.04, 0.025, 0.10) * uCharge * exp(-sq(rr / 0.65));
        float grey = dot(c, float3(0.2126, 0.7152, 0.0722));
        // A wide-gamut phone shows these colours richer than the proof renderer does;
        // Hold them back.
            c = mix(float3(grey), c, 0.98);
        c += noisePixel(xy) / 255.0;
        // C1 shoulder: internal bursts approach white without clipping the relief into white stripes.
        c = min(c, float3(0.92)) + 0.08 * (1.0 - exp(-max(c - 0.92, float3(0.0)) / 0.08));
        return half4(half3(clamp(c, 0.0, 1.0) * alpha), half(alpha));
    }
};

[[ stitchable ]] half4 prismSphereGeneratingBackdrop(float2 xy,
    float2 uSize, float2 uCentre, float uRadius, float uScale,
    float uClock, float uEnergy, float uCharge, float uFlash, float uRing,
    float3 column0, float3 column1, float3 column2,
    float uFan, float uSweep, float uSweepPower, float2 uLight
) {
    PrismSphereGeneratingMaterial material;
    material.uSize = uSize;
    material.uCentre = uCentre;
    material.uRadius = uRadius;
    material.uScale = uScale;
    material.uClock = uClock;
    material.uEnergy = uEnergy;
    material.uCharge = uCharge;
    material.uFlash = uFlash;
    material.uRing = uRing;
    material.uRot = float3x3(column0, column1, column2);
    material.uFan = uFan;
    material.uSweep = uSweep;
    material.uSweepPower = uSweepPower;
    material.uLight = uLight;
    return material.backdropAt(xy);
}

[[ stitchable ]] half4 prismSphereGeneratingSphere(float2 xy,
    float2 uSize, float2 uCentre, float uRadius, float uScale,
    float uClock, float uEnergy, float uCharge, float uFlash, float uRing,
    float3 column0, float3 column1, float3 column2,
    float uFan, float uSweep, float uSweepPower, float2 uLight
) {
    PrismSphereGeneratingMaterial material;
    material.uSize = uSize;
    material.uCentre = uCentre;
    material.uRadius = uRadius;
    material.uScale = uScale;
    material.uClock = uClock;
    material.uEnergy = uEnergy;
    material.uCharge = uCharge;
    material.uFlash = uFlash;
    material.uRing = uRing;
    material.uRot = float3x3(column0, column1, column2);
    material.uFan = uFan;
    material.uSweep = uSweep;
    material.uSweepPower = uSweepPower;
    material.uLight = uLight;
    return material.sphereAt(xy);
}
