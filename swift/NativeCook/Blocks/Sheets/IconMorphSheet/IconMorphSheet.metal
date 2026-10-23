#include <metal_stdlib>
using namespace metal;
constant float IconMorphSheetPI = 3.14159265359;

// Direct SkSL translation; geometry, materials, marching and premultiplication are preserved.
struct IconMorphSheetShader {

    float2 uCentre;
    float uUnit;
    float uPixelRatio;
    float uCamera;
    float uRadius;
    float4 uScene;
    float4 uShape;
    float uTurn;
    float uScale;
    float uPress;
    float uWet;
    float4 uFront;
    float4 uZero;
    float4 uCoin0;
    float4 uCoin1;
    float4 uCoin2;
    float4 uCoinTurn0;
    float4 uCoinTurn1;
    float4 uCoinTurn2;
    float4 uSafe;
    float4 uEnvelope;
    float4 uNote;
    float4 uBook;
    float4 uBookTurn;
    struct Clay {
        float d;
        float3 rgb;
        float gloss;
        float emit;
        float pigment;
    };
    Clay clay(float d, float3 rgb, float gloss) {
        Clay c;
        c.d = d;
        c.rgb = rgb;
        c.gloss = gloss;
        c.emit = 0.0;
        c.pigment = 0.0;
        return c;
    }
    float sq(float v) {
        return v * v;
    }
    float3 safeUnit(float3 v) {
        return v / max(length(v), 0.000001);
    }
    float3 rx(float3 p, float a) {
        float c = cos(a), s = sin(a);
        return float3(p.x, c * p.y + s * p.z, -s * p.y + c * p.z);
    }
    float3 ry(float3 p, float a) {
        float c = cos(a), s = sin(a);
        return float3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    }
    float3 rz(float3 p, float a) {
        float c = cos(a), s = sin(a);
        return float3(c * p.x + s * p.y, -s * p.x + c * p.y, p.z);
    }
    float rrect(float2 p, float2 ext, float r) {
        float2 q = abs(p) - ext + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    float slab(float prof, float z, float dep, float r) {
        float2 q = float2(prof + r, abs(z) - dep + r);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }
    float box(float3 p, float3 ext, float r) {
        float3 q = abs(p) - ext + r;
        return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
    }
    bool partPossible(float3 q, float radius, float nearest) {
        float bound = radius + max(nearest, 0.0) + uShape.x;
        return dot(q, q) < bound * bound;
    }
    float lineD(float2 p, float2 a, float2 b) {
        float2 v = p - a, w = b - a;
        return length(v - w * clamp(dot(v, w) / max(dot(w, w), 0.000001), 0.0, 1.0));
    }
    float triangle(float2 p, float2 a, float2 b, float2 c) {
        float2 e0 = b - a, e1 = c - b, e2 = a - c;
        float2 v0 = p - a, v1 = p - b, v2 = p - c;
        float2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
        float2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
        float2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
        float sg = sign(e0.x * e2.y - e0.y * e2.x);
        float2 d = min(min(float2(dot(pq0, pq0), sg * (v0.x * e0.y - v0.y * e0.x)),
                           float2(dot(pq1, pq1), sg * (v1.x * e1.y - v1.y * e1.x))),
                       float2(dot(pq2, pq2), sg * (v2.x * e2.y - v2.y * e2.x)));
        return -sqrt(max(d.x, 0.0)) * sign(d.y);
    }
    Clay joinClay(Clay a, Clay b, float k) {
        float h = clamp(0.5 + 0.5 * (b.d - a.d) / k, 0.0, 1.0);
        Clay c;
        c.d = mix(b.d, a.d, h) - k * h * (1.0 - h);
        c.rgb = mix(b.rgb, a.rgb, h);
        c.gloss = mix(b.gloss, a.gloss, h);
        c.emit = mix(b.emit, a.emit, h);
        c.pigment = mix(b.pigment, a.pigment, h);
        return c;
    }
    // Un-swung model coordinates: lower-left becomes new clay first.
    float frontWeight(float3 p) {
        if (uScene.z <= 0.0)
            return 0.0;
        if (uScene.z >= 1.0)
            return 1.0;
        float projected = dot(p.xy, float2(0.70710678118)) * uShape.w;
        float front = mix(uFront.x, uFront.y, uScene.z);
        return 1.0 - smoothstep(front - uFront.z, front + uFront.z, projected);
    }
    float molten(float3 p) {
        float w = frontWeight(p);
        return 4.0 * w * (1.0 - w) * uFront.w;
    }
    float softAt(float3 p) {
        return 0.018 + 0.22 * molten(p);
    }
    float retention(float id, float3 at, float radius) {
        // Begin at the satellite's leading edge and finish while the band still covers
        // its anchor. Pull along the surface first; the final small shape is inside it.
        float3 leading = at - float3(float2(0.70710678118) * uShape.w * radius, 0.0);
        return id == uScene.x ? 1.0 - smoothstep(0.0, 0.65, frontWeight(leading)) : 1.0;
    }
    float inkFor(float id, float3 p) {
        if (uScene.w <= 0.0)
            return 1.0;
        float w = frontWeight(p);
        return id == uScene.x ? 1.0 - w : w;
    }
    float stroke(float d, float r) {
        float aa = 0.65 / (uUnit * uPixelRatio * uScale);
        return r <= 0.0 ? 0.0 : 1.0 - smoothstep(max(0.0, r - aa), r + aa, d);
    }
    float currency(float2 p, float thickness) {
        p.x -= 0.20 * p.y;
        float d = min(lineD(p, float2(-0.24, -0.28), float2(-0.24, 0.28)),
                      min(lineD(p, float2(-0.24, 0.28), float2(0.24, -0.28)),
                          lineD(p, float2(0.24, -0.28), float2(0.24, 0.28))));
        d = min(d, lineD(p, float2(-0.40, 0.0), float2(0.40, 0.0)));
        return stroke(d, 0.063 * thickness);
    }
    float checkInk(float2 p, float drawn, float size) {
        float2 elbow = float2(-0.02, -0.09);
        float2 left = mix(elbow, float2(-0.16, 0.04), drawn);
        float2 right = mix(elbow, float2(0.20, 0.17), drawn);
        return drawn <= 0.0 ? 0.0 : stroke(min(lineD(p, elbow, left), lineD(p, elbow, right)), size);
    }
    float3 coinLocal(float3 p, float4 at, float4 turn) {
        float3 q = p - at.xyz;
        q = float3(q.x, q.y * turn.z + q.z * turn.w, -q.y * turn.w + q.z * turn.z);
        return float3(q.x * turn.x - q.z * turn.y, q.y, q.x * turn.y + q.z * turn.x);
    }
    Clay goldCoin(float3 p, float4 at, float4 turn, bool ink) {
        float bound = length(p - at.xyz) - at.w - 0.025;
        if (uFront.w <= 0.0 && bound > 0.18)
            return clay(bound, float3(1.0, 0.70, 0.05), 0.85);
        float3 q = coinLocal(p, at, turn);
        float dep = mix(at.w * 0.80, at.w * 0.105, max(uZero.z, smoothstep(0.0, 0.82, uZero.x)));
        float pillow = 0.028 * (1.0 - clamp(dot(q.xy, q.xy) / max(at.w * at.w, 0.001), 0.0, 1.0));
        float disc = slab(length(q.xy) - at.w, q.z, dep + pillow, min(0.035, dep * 0.55));
        float rim =
            length(float2(length(q.xy) - at.w * 0.89, abs(q.z) - dep - 0.012)) - min(0.024, at.w * 0.065);
        Clay c = clay(min(disc, rim), float3(1.0, 0.81, 0.045), 0.88);
        c.pigment = 1.0;
        if (ink) {
            float face = smoothstep(dep * 0.40, dep * 0.90, q.z);
            float back = smoothstep(dep * 0.40, dep * 0.90, -q.z);
            c.rgb = mix(float3(0.96, 0.46, 0.035), float3(0.96, 0.64, 0.0), back);
            c.rgb = mix(c.rgb, float3(1.0, 0.80, 0.045), face);
            float mark = currency(q.xy / max(at.w, 0.02) * 0.775, inkFor(0.0, p)) * face;
            c.rgb = mix(c.rgb, float3(0.94, 0.32, 0.020), mark);
            c.gloss = mix(c.gloss, 0.32, mark);
            c.rgb = mix(float3(0.427, 0.435, 0.475), c.rgb, max(uZero.z, smoothstep(0.10, 0.54, uZero.x)));
        }
        return c;
    }
    Clay travellingCoin(float3 p, float4 at, float4 turn, float3 home, bool ink) {
        float keep = retention(0.0, at.xyz, at.w);
        at.xyz = mix(home, at.xyz, keep);
        // Shrink only after the disc has reached and intersects its anchor.
        at.w *= mix(0.18, 1.0, smoothstep(0.0, 0.35, keep));
        return goldCoin(p, at, turn, ink);
    }
    Clay zeroScene(float3 p, bool ink) {
        float3 q = ry(p, -0.18);
        q.y /= uZero.y;
        // Fill only behind the travelling front; the other half retains the counter.
        float closure = uScene.x == 0.0 ? frontWeight(p) : uScene.y == 0.0 ? 1.0 - frontWeight(p) : 0.0;
        float counter = rrect(q.xy, float2(0.135, 0.51), 0.135) + 0.38 * closure;
        float prof = max(rrect(q.xy, float2(0.51, 0.85), 0.43), -counter);
        float pillow = 0.025 * (1.0 - clamp(sq(q.y / 0.90), 0.0, 1.0));
        Clay c = clay(slab(prof, q.z, 0.18 + pillow, 0.065), float3(0.427, 0.435, 0.475), 0.22);
        if (uZero.x > 0.0) {
            float k = max(softAt(p), 0.15 * (1.0 - uZero.x));
            c = joinClay(c, travellingCoin(p, uCoin0, uCoinTurn0, float3(0.0, 0.52, -0.12), ink), k);
            c = joinClay(c, travellingCoin(p, uCoin1, uCoinTurn1, float3(-0.37, -0.08, -0.12), ink), k);
            c = joinClay(c, travellingCoin(p, uCoin2, uCoinTurn2, float3(0.37, -0.08, -0.12), ink), k);
        }
        return c;
    }
    float shieldProfile(float2 q) {
        // Five-sided shield: crowned top, shoulders, bowed sides and pointed foot.
        float top = q.y - 0.285 + 0.16 * abs(q.x);
        float side = abs(q.x) - 0.285 + 0.10 * max(0.12 - q.y, 0.0);
        float foot = (0.72 * abs(q.x) - q.y - 0.325) * 0.81;
        return max(max(top, side), foot) - 0.025;
    }
    Clay safeScene(float3 p, bool ink) {
        float k = uSafe.x;
        float3 q = ry(p, -IconMorphSheetPI * k - 0.14 * k);
        float disc = slab(length(q.xy) - 0.88, q.z, 0.11 - 0.02 * k, 0.044);
        Clay c = clay(disc, mix(float3(0.90, 0.91, 0.95), float3(0.065, 0.067, 0.088), k), 0.48);
        float3 f = rx(float3(-q.x, q.y, -q.z), -0.16 * k);
        if (ink) {
            float mark = currency(q.xy * 0.72, inkFor(1.0, p) * (1.0 - smoothstep(0.05, 0.85, k)));
            c.rgb = mix(c.rgb, float3(0.40, 0.43, 0.53), mark * smoothstep(0.06, 0.095, abs(q.z)));
            float wash = exp(-sq((f.x - 0.48 * uSafe.y) / 0.46) - sq((f.y - 0.10) / 0.67)) * k;
            c.rgb += float3(0.64, 0.022, 0.055) * wash;
            c.emit = wash * 0.48;
        }
        if (k > 0.0) {
            float grow = k;
            float3 v = f - float3(0.0, 0.015, 0.32 * grow);
            float r = mix(0.065, 0.35, grow);
            if (length(v) - 0.80 < c.d + softAt(p)) {
                // True circular XZ cross-section, straight walls and hemispherical crown.
                float3 domeP = v;
                domeP.y -= clamp(domeP.y, -0.28 * grow, 0.12 * grow);
                float dd = max(length(domeP) - r, -v.y - 0.37 * grow);
                Clay lamp = clay(dd, float3(0.87, 0.045, 0.09), 0.97);
                if (ink) {
                    float edge = clamp(abs(v.x) / r, 0.0, 1.0);
                    float facing = sqrt(max(0.0, 1.0 - edge * edge));
                    float front = smoothstep(-0.04, 0.20, v.z);
                    float lampX = 0.16 * uSafe.y - 0.04;
                    float halo = exp(-sq((v.x - lampX) / 0.21) - sq((v.y - 0.11) / 0.32)) * front;
                    float core = exp(-sq((v.x - lampX) / 0.078) - sq((v.y - 0.13) / 0.15)) * front;
                    float streak = exp(-sq((v.x + 0.19) / 0.038) - sq((v.y - 0.13) / 0.25)) * front;
                    lamp.rgb = mix(float3(0.48, 0.012, 0.045), float3(0.94, 0.085, 0.13), facing);
                    lamp.rgb = mix(lamp.rgb, float3(1.0, 0.26, 0.24), halo * 0.65);
                    lamp.rgb = mix(lamp.rgb, float3(1.0, 0.81, 0.75), core);
                    lamp.rgb = mix(lamp.rgb, float3(1.0, 0.99, 0.95), smoothstep(0.40, 0.92, core));
                    lamp.rgb += float3(0.34, 0.25, 0.25) * streak;
                    lamp.emit = (0.20 * halo + 0.85 * core + 0.18 * streak) * grow;
                }
                c = joinClay(c, lamp, 0.004 + softAt(p) * grow);
                float3 baseP = f - float3(0.0, -0.39 * grow, 0.31 * grow);
                float bd =
                    slab(length(baseP.xz) - (0.07 + 0.39 * grow), baseP.y, 0.055 + 0.012 * grow, 0.022);
                Clay base = clay(bd, float3(0.085, 0.09, 0.115), 0.6);
                if (ink)
                    base.rgb += float3(0.055, 0.06, 0.075) * smoothstep(-0.02, 0.058, baseP.y);
                c = joinClay(c, base, max(0.008, (0.0045 + 0.22 * molten(p)) * grow));
                float lip = slab(length(baseP.xz) - (0.07 + 0.405 * grow), baseP.y - 0.042, 0.014, 0.011);
                c = joinClay(c, clay(lip, float3(0.19, 0.20, 0.24), 0.66), 0.006 + 0.19 * molten(p));
            }
            float3 shieldWorld = rx(float3(0.65, 0.66, 0.20), 0.16 * k);
            shieldWorld = ry(float3(-shieldWorld.x, shieldWorld.y, -shieldWorld.z), IconMorphSheetPI * k + 0.14 * k);
            float bud = uSafe.z * retention(1.0, shieldWorld, 0.35);
            if (bud > 0.0) {
                float3 at = mix(float3(0.42, 0.44, -0.065), float3(0.65, 0.66, 0.20), bud);
                float size = mix(0.18, 1.0, smoothstep(0.0, 0.65, bud));
                float3 v = (f - at) / size;
                if (length(v) < 0.77 + c.d / size) {
                    float sh = shieldProfile(v.xy);
                    Clay badge = clay(slab(sh, v.z, 0.095, 0.037) * size, float3(0.24, 0.39, 0.96), 0.72);
                    badge.pigment = 1.0;
                    if (ink) {
                        badge.rgb = mix(badge.rgb, float3(0.424, 0.545, 1.0), smoothstep(-0.075, -0.031, sh));
                        float ck = checkInk(v.xy * 1.20, 1.0, 0.042 * inkFor(1.0, p)) *
                                   smoothstep(0.035, 0.075, v.z);
                        badge.rgb = mix(badge.rgb, float3(0.97, 0.99, 1.0), ck);
                    }
                    c = joinClay(c, badge, max(softAt(p), 0.11 * (1.0 - min(bud, 1.0))));
                }
            }
        }
        return c;
    }
    float heartD(float2 p) {
        p.x = abs(p.x);
        float top = length(p - float2(0.075, 0.055)) - 0.095;
        float bottom = triangle(p, float2(0.0, -0.16), float2(-0.15, 0.06), float2(0.15, 0.06)) - 0.015;
        return min(top, bottom);
    }
    Clay emoji(float3 q, float3 at, float rad, float kind, float grow, bool ink) {
        float r = rad * mix(0.16, 1.0, smoothstep(0.0, 0.72, grow));
        float3 v = q - at;
        Clay c = clay(length(v) - r, float3(1.0, 0.74, 0.045), 0.8);
        c.pigment = 1.0;
        if (ink) {
            float3 face = v / max(r, 0.01);
            float2 uv = face.xy;
            float paint = inkFor(2.0, q);
            c.rgb = mix(float3(1.0, 0.57, 0.025), float3(1.0, 0.81, 0.065), smoothstep(-0.9, 0.55, face.y));
            if (face.z > 0.30 && paint > 0.0) {
                float eyes;
                if (kind > 0.5) {
                    float2 le = (uv - float2(-0.34, 0.25)) * 0.55;
                    float2 re = (uv - float2(0.34, 0.25)) * 0.55;
                    eyes = 1.0 - smoothstep(-0.01, 0.014, min(heartD(le), heartD(re)) + 0.19 * (1.0 - paint));
                } else {
                    float left = length(uv - float2(-0.30, 0.20));
                    float right = lineD(uv, float2(0.18, 0.18), float2(0.36, 0.25));
                    eyes = stroke(min(left, right) * r, 0.026 * paint);
                }
                c.rgb = mix(c.rgb, kind > 0.5 ? float3(0.93, 0.055, 0.045) : float3(0.27, 0.11, 0.045), eyes);
                float mouth = max(length((uv - float2(0.0, -0.18)) / float2(1.0, 0.90)) - 0.43, uv.y + 0.14);
                float lips = 1.0 - smoothstep(-0.018, 0.018, mouth + 0.30 * (1.0 - paint));
                float3 red = mix(float3(0.67, 0.035, 0.045), float3(0.98, 0.18, 0.20),
                                 (1.0 - smoothstep(-0.55, -0.30, uv.y)));
                c.rgb = mix(c.rgb, red, lips);
                float teeth = (1.0 - smoothstep(0.025, 0.065, abs(uv.y + 0.20))) * lips;
                c.rgb = mix(c.rgb, float3(1.0, 0.93, 0.79), teeth * 0.85);
            }
        }
        return c;
    }
    float3 envelopeLocal(float3 p) {
        float3 q = ry(p, uEnvelope.w - 0.10);
        q = rx(q, -0.16);
        q = rz(q, -0.10);
        q.y -= uNote.y;
        return q;
    }
    float3 envelopeWorld(float3 q) {
        q.y += uNote.y;
        return ry(rx(rz(q, 0.10), 0.16), 0.10 - uEnvelope.w);
    }
    Clay envelopeScene(float3 p, bool ink) {
        float3 q = envelopeLocal(p);
        float body = rrect(q.xy - float2(0.0, -0.10), float2(0.91, 0.57), 0.13);
        // The rear lining sits inside the front pillow's rim, including the lower-right
        // corner; coincident stacked outlines otherwise make a second dark edge.
        float lining = rrect(q.xy - float2(0.0, -0.10), float2(0.855, 0.515), 0.13);
        Clay c = clay(slab(lining, q.z + 0.035, 0.125, 0.07), float3(0.08, 0.43, 0.88), 0.60);
        float op = uEnvelope.x;
        float3 flapP = rx(q - float3(0.0, 0.42, 0.02), -op * 2.82);
        if (partPossible(flapP - float3(0.0, -0.26, 0.14), 0.96, c.d)) {
            float flap = triangle(flapP.xy, float2(-0.80, 0.0), float2(0.80, 0.0), float2(0.0, -0.66)) - 0.06;
            c = joinClay(c, clay(slab(flap, flapP.z - 0.14, 0.065, 0.038), float3(0.10, 0.55, 0.98), 0.55),
                         softAt(p));
        }
        float3 noteP = q - float3(0.0, -0.08 + 0.37 * uNote.x, -0.03 + 0.10 * uNote.x);
        if (uNote.x > 0.0 && partPossible(noteP, 1.0, c.d)) {
            float nd =
                slab(rrect(noteP.xy, float2(0.76, 0.44 + 0.12 * uNote.x), 0.14), noteP.z, 0.045, 0.028);
            Clay note = clay(nd, float3(0.88, 0.91, 0.96), 0.28);
            if (ink && noteP.z > 0.0) {
                float mark = currency((noteP.xy - float2(0.0, 0.15)) * 1.35, inkFor(2.0, p));
                note.rgb = mix(note.rgb, float3(0.055, 0.065, 0.09), mark);
            }
            c = joinClay(c, note, max(0.009, softAt(p) * 0.35));
        }
        if (partPossible(q - float3(0.0, -0.10, 0.25), 1.17, c.d)) {
            float vtop = (q.y + 0.19 - 0.79 * abs(q.x)) * 0.78;
            float front = max(body, vtop);
            float pillow = 0.04 * (1.0 - clamp(abs(q.x), 0.0, 1.0));
            Clay fold = clay(slab(front, q.z - 0.20 - pillow, 0.12, 0.06), float3(0.065, 0.45, 0.94), 0.64);
            if (ink)
                fold.rgb =
                    mix(float3(0.075, 0.34, 0.84), float3(0.10, 0.56, 1.0), smoothstep(-0.70, 0.5, q.y));
            c = joinClay(c, fold, max(0.012, softAt(p) * 0.6));
            float centreFold = max(body, (q.y - 0.04 + 0.77 * abs(q.x)) * 0.78);
            Clay lower = clay(slab(centreFold, q.z - 0.30, 0.065, 0.04), float3(0.08, 0.45, 0.94), 0.63);
            if (ink)
                lower.rgb =
                    mix(float3(0.075, 0.35, 0.87), float3(0.08, 0.50, 0.97), smoothstep(-0.67, 0.05, q.y));
            c = joinClay(c, lower, max(0.008, softAt(p) * 0.6));
        }
        float h = uEnvelope.y *
                  retention(2.0, envelopeWorld(float3(1.04, 0.78 + 0.025 * sin(uNote.w * 3.0), 0.20)), 0.265);
        float g = uEnvelope.z *
                  retention(2.0, envelopeWorld(float3(-0.96, -0.54 + 0.023 * sin(uNote.w * 3.0 + 2.0), 0.36)),
                            0.34);
        if (h > 0.0) {
            float3 at = mix(float3(0.40, 0.02, -0.06), float3(1.04, 0.78, 0.20), h);
            at.y += 0.025 * sin(uNote.w * 3.0) * min(h, 1.0);
            if (length(q - at) < 0.60 + c.d)
                c = joinClay(c, emoji(q, at, 0.265, 1.0, h, ink), max(softAt(p), 0.08 * (1.0 - min(h, 1.0))));
        }
        if (g > 0.0) {
            float3 at = mix(float3(-0.50, -0.20, 0.02), float3(-0.96, -0.54, 0.36), g);
            at.y += 0.023 * sin(uNote.w * 3.0 + 2.0) * min(g, 1.0);
            if (length(q - at) < 0.65 + c.d)
                c = joinClay(c, emoji(q, at, 0.34, 0.0, g, ink), max(softAt(p), 0.10 * (1.0 - min(g, 1.0))));
        }
        // Specks are ejected and fall back into the envelope; their radius stays constant in flight.
        float flight = smoothstep(0.38, 0.65, uNote.w) * (1.0 - smoothstep(0.76, 1.10, uNote.w));
        flight *= uScene.x == 2.0 ? uShape.y : 1.0;
        if (flight > 0.0) {
            for (int i = 0; i < 4; i++) {
                float fi = float(i);
                float3 home = float3((fi - 1.5) * 0.22, 0.18, 0.0);
                float3 travel = float3((fi - 1.5) * 0.25, 0.50 + 0.10 * fi, 0.13) * flight;
                float3 at = home + travel * retention(2.0, envelopeWorld(home + travel), 0.024);
                float dd = length(q - at) - 0.024;
                c = joinClay(c, clay(dd, float3(1.0, 0.46, 0.04), 0.7), max(0.045, softAt(p)));
            }
        }
        return c;
    }
    float3 bookLocal(float3 p) {
        return rx(ry(rz(p, uBookTurn.y), uBookTurn.z), uBookTurn.x);
    }
    float pageCurl(float2 p, float layer) {
        float corner = smoothstep(0.12, 0.88, p.x) * (1.0 - smoothstep(-0.73, -0.1, p.y));
        return corner * (0.17 + (uBook.w + 0.35 * uBook.x) * (0.18 + layer * 0.16));
    }
    Clay bookScene(float3 p, bool ink) {
        float3 q = bookLocal(p);
        float prof = rrect(q.xy, float2(0.93, 0.75), 0.18);
        float curl = pageCurl(q.xy, 0.0);
        float fanCorner = smoothstep(0.12, 0.88, q.x) * (1.0 - smoothstep(-0.73, -0.1, q.y));
        Clay c = clay(slab(prof, q.z + 0.18 - curl + 0.18 * uBook.w * fanCorner, 0.049, 0.027),
                      float3(0.96, 0.57, 0.035), 0.55);
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float3 v = q - float3(0.012 * fi * (uBook.w + 0.4 * uBook.x),
                                  -0.01 * fi - 0.025 * uBook.x * (4.0 - fi), -0.105 + fi * 0.052);
            if (!partPossible(v, 1.31 + 0.24 * uBook.w, c.d))
                continue;
            float lag = -uBook.w * (3.0 - fi) * 0.012 * (1.0 - smoothstep(-0.70, -0.10, v.y));
            // Fan toward the back cover: page layers cannot pierce the coloured front cover.
            v.z -= pageCurl(v.xy, (fi - 3.0) * 0.25) + lag;
            float pd = slab(rrect(v.xy, float2(0.895 - fi * 0.004, 0.725), 0.17), v.z, 0.018, 0.014);
            Clay page = clay(pd, float3(0.88 + fi * 0.018, 0.90 + fi * 0.015, 0.95 + fi * 0.010), 0.24);
            c = joinClay(c, page, max(0.006, softAt(p) * 0.22));
        }
        float pillow = 0.025 * max(0.0, 1.0 - sq(q.x / 0.95)) * max(0.0, 1.0 - sq(q.y / 0.8));
        float3 coverP = q;
        coverP.z -= 0.15 + curl + pillow;
        if (partPossible(coverP, 1.24, c.d)) {
            float cd = slab(prof, coverP.z, 0.055, 0.038);
            Clay cover = clay(cd, float3(1.0, 0.85, 0.055), 0.62);
            if (ink) {
                float green = smoothstep(-0.24, 0.24, uBook.x * 2.55 - 1.25 + q.y * 0.8 + q.x * 0.20);
                float3 yellow =
                    mix(float3(0.98, 0.76, 0.03), float3(1.0, 0.89, 0.10), smoothstep(-0.75, 0.65, q.y));
                cover.rgb = mix(yellow, float3(0.12, 0.80, 0.39), green);
                float band = inkFor(3.0, p) > 0.0
                                 ? smoothstep(0.75 - 0.27 * inkFor(3.0, p), 0.77 - 0.27 * inkFor(3.0, p), q.y)
                                 : 0.0;
                float d1 = lineD(q.xy, float2(-0.67, 0.29), float2(-0.35, 0.29));
                float d2 = lineD(q.xy, float2(-0.67, 0.12), float2(-0.24, 0.12));
                float lines = stroke(min(d1, d2), 0.025 * inkFor(3.0, p));
                cover.rgb = mix(cover.rgb, float3(0.97, 0.98, 1.0), max(band, lines));
            }
            c = joinClay(c, cover, max(0.008, softAt(p) * 0.25));
        }
        float3 badgeWorld = rz(ry(rx(float3(0.86, 0.68, 0.37), -uBookTurn.x), -uBookTurn.z), -uBookTurn.y);
        float b = uBook.y * retention(3.0, badgeWorld, 0.22);
        if (b > 0.0) {
            float3 at = mix(float3(0.63, 0.54, 0.06), float3(0.86, 0.68, 0.37), b);
            float r = 0.22 * mix(0.16, 1.0, smoothstep(0.0, 0.70, b));
            float3 v = q - at;
            Clay badge = clay(length(v) - r, float3(0.08, 0.78, 0.36), 0.64);
            badge.pigment = 1.0;
            float3 face = rz(ry(rx(v, -uBookTurn.x), -uBookTurn.z), -uBookTurn.y);
            if (ink && face.z > 0.02) {
                float ck = checkInk(face.xy * 1.35, uBook.z, 0.035 * inkFor(3.0, p));
                badge.rgb = mix(badge.rgb, float3(0.97, 1.0, 0.96), ck);
            }
            c = joinClay(c, badge, max(softAt(p), 0.085 * (1.0 - min(b, 1.0))));
        }
        return c;
    }
    Clay oneScene(float3 p, float id, bool ink) {
        if (id < 0.5)
            return zeroScene(p, ink);
        if (id < 1.5)
            return safeScene(p, ink);
        if (id < 2.5)
            return envelopeScene(p, ink);
        return bookScene(p, ink);
    }
    // OKLab interpolation in linear-light RGB: saturated clay retains lightness through the turn.
    float3 toLab(float3 c) {
        c = pow(max(c, float3(0.0)), float3(2.2));
        float3 lms =
            float3(dot(c, float3(0.412221, 0.536333, 0.051446)), dot(c, float3(0.211903, 0.680700, 0.107397)),
                   dot(c, float3(0.088302, 0.281719, 0.629979)));
        lms = pow(max(lms, float3(0.0)), float3(0.333333));
        return float3(dot(lms, float3(0.210454, 0.793618, -0.004072)),
                      dot(lms, float3(1.977998, -2.428592, 0.450594)),
                      dot(lms, float3(0.025904, 0.782772, -0.808676)));
    }
    float3 fromLab(float3 c) {
        float3 lms = float3(c.x + 0.396338 * c.y + 0.215804 * c.z, c.x - 0.105561 * c.y - 0.063854 * c.z,
                            c.x - 0.089484 * c.y - 1.291486 * c.z);
        lms = lms * lms * lms;
        float3 rgb = float3(dot(lms, float3(4.076742, -3.307712, 0.230970)),
                            dot(lms, float3(-1.268438, 2.609757, -0.341319)),
                            dot(lms, float3(-0.004196, -0.703419, 1.707615)));
        return pow(max(rgb, float3(0.0)), float3(1.0 / 2.2));
    }
    float3 clayColour(float3 a, float3 b, float w) {
        float3 la = toLab(a), lb = toLab(b);
        float ca = length(la.yz), cb = length(lb.yz);
        float ha = atan2(la.z, la.y), hb = atan2(lb.z, lb.y);
        // Polar OKLab retains chroma when complementary blue/gold clay mixes.
        // Neutral endpoints borrow the coloured endpoint's hue instead of tinting it.
        if (ca < 0.018)
            ha = hb;
        if (uScene.x == 1.0 && uScene.y == 2.0 && ca < 0.08)
            ha = 0.48;
        if (cb < 0.018)
            hb = ha;
        float arc = hb - ha;
        if (arc > IconMorphSheetPI)
            arc -= 2.0 * IconMorphSheetPI;
        if (arc < -IconMorphSheetPI)
            arc += 2.0 * IconMorphSheetPI;
        // The orange back cover follows the same cyan/green route as the yellow front.
        if (uScene.x == 2.0 && uScene.y == 3.0 && ca > 0.03 && cb > 0.03 && arc > 0.0)
            arc -= 2.0 * IconMorphSheetPI;
        if (uScene.x == 3.0 && uScene.y == 2.0 && ca > 0.03 && cb > 0.03 && arc < 0.0)
            arc += 2.0 * IconMorphSheetPI;
        float hue = ha + arc * w;
        float band = 4.0 * w * (1.0 - w) * uFront.w;
        float chroma = mix(ca, cb, w) + 0.035 * band * smoothstep(0.02, 0.09, max(ca, cb));
        float light = mix(la.x, lb.x, w) + band * (0.055 + 0.18 * abs(la.x - lb.x));
        if (max(ca, cb) > 0.08) {
            chroma = max(chroma, band * max(0.17, max(ca, cb)));
            light = max(light, band * 0.72);
        }
        return fromLab(float3(light, chroma * cos(hue), chroma * sin(hue)));
    }
    float3 seamAccent() {
        if (uScene.y < 1.5)
            return float3(0.62, 0.78, 1.0);
        if (uScene.y < 2.5)
            return float3(0.23, 0.44, 1.0);
        return float3(0.45, 1.0, 0.20);
    }
    Clay field(float3 p, bool ink) {
        if (uScene.z <= 0.0)
            return oneScene(p, uScene.x, ink);
        if (uScene.z >= 1.0)
            return oneScene(p, uScene.y, ink);
        float w = frontWeight(p);
        if (w <= 0.0)
            return oneScene(p, uScene.x, ink);
        if (w >= 1.0)
            return oneScene(p, uScene.y, ink);
        Clay a = oneScene(p, uScene.x, ink), b = oneScene(p, uScene.y, ink), c;
        float band = 4.0 * w * (1.0 - w) * uFront.w;
        c.d = mix(a.d, b.d, w) - 0.042 * band;
        // Satellite pigment stays its own colour until the geometry has sunk inside.
        c.pigment = a.pigment;
        c.rgb = ink ? mix(clayColour(a.rgb, b.rgb, w), a.rgb, c.pigment) : float3(0.0);
        float heat = band * (1.0 - c.pigment);
        c.gloss = mix(a.gloss, b.gloss, w) + 0.24 * heat;
        c.emit = mix(a.emit, b.emit, w) + 0.18 * heat;
        if (ink)
            c.rgb += seamAccent() * 0.13 * heat * heat;
        return c;
    }
    float3 objectPoint(float3 p) {
        p /= uScale;
        p.xy /= rsqrt(1.0 - 0.12 * uPress);
        p.z /= 1.0 - 0.12 * uPress;
        return rx(ry(p, uTurn), uShape.z - 0.12 * uPress);
    }
    float sceneD(float3 p) {
        return field(objectPoint(p), false).d * uScale * (1.0 - 0.12 * uPress);
    }
    float3 surfaceNormal(float3 p) {
        const float e = 0.001;
        const float2 k = float2(1.0, -1.0);
        return safeUnit(k.xyy * sceneD(p + k.xyy * e) + k.yyx * sceneD(p + k.yyx * e) +
                        k.yxy * sceneD(p + k.yxy * e) + k.xxx * sceneD(p + k.xxx * e));
    }
    float3 accent(float id) {
        if (id < 0.5)
            return float3(1.0, 0.64, 0.055);
        if (id < 1.5)
            return mix(float3(0.55, 0.61, 0.84), float3(1.0, 0.035, 0.065), uSafe.x * uSafe.w);
        if (id < 2.5)
            return float3(0.05, 0.40, 1.0);
        return mix(float3(1.0, 0.79, 0.04), float3(0.06, 0.87, 0.40), uBook.x);
    }
    float footprint(float id) {
        if (id < 0.5)
            return 0.68 + 0.17 * uZero.x;
        if (id < 1.5)
            return 0.88;
        if (id < 2.5)
            return 1.05;
        return 1.02;
    }
    float pixelHash(float2 p) {
        float3 v = fract(float3(p.x, p.y, p.x) * 0.1031);
        v += dot(v, v.yzx + 33.33);
        return fract((v.x + v.y) * v.z);
    }
    half4 finishPixel(float3 rgb, float alpha, float2 xy) {
        if (alpha <= 0.0)
            return half4(0.0);
        float2 pixel = floor(xy * uPixelRatio);
        float noise = (pixelHash(pixel) - pixelHash(pixel + float2(71.7, 193.3))) / 255.0;
        // Correlated RGBA dither preserves premultiplication. Unlike noise on straight
        // RGB, its ±1 LSB amplitude survives the 8-bit low-alpha intermediate surface.
        float a = clamp(alpha + noise, 0.0, 1.0);
        float3 straight = rgb / max(alpha, 0.000001);
        float3 premul = rgb + straight * (a - alpha);
        // Dark shadows need colour-channel noise as well as alpha noise. Keep it
        // zero-mean, premultiplied and bounded in fully transparent/opaque regions.
        float rgbNoise =
            (pixelHash(pixel + float2(19.1, 47.2)) - pixelHash(pixel + float2(127.4, 11.3))) / 255.0;
        premul += rgbNoise * min(1.0, alpha * 255.0);
        return half4(half3(clamp(premul, float3(0.0), float3(a))), half(a));
    }
    half4 main(float2 xy) {
        float2 screen = (xy - uCentre) / uUnit;
        float3 ray = safeUnit(float3(screen.x, -screen.y, -uCamera));
        float3 ro = float3(0.0, 0.0, uCamera);
        float extent = mix(footprint(uScene.x), footprint(uScene.y), uScene.z) * uScale;
        float dip = 1.0 - uScale;
        float sh = exp(-sq(screen.x / max(0.2, extent * 0.77)) -
                       sq((screen.y - 1.05) / max(0.10, 0.14 - 0.15 * dip)));
        float shadow = sh * (0.29 + 0.38 * dip);
        float siren = (uScene.x == 1.0   ? 1.0 - uScene.z
                       : uScene.y == 1.0 ? uScene.z
                                         : 0.0) *
                      uSafe.x * uSafe.w;
        float pool = exp(-sq(screen.x / 1.16) - sq((screen.y - 0.55) / 0.67)) * (0.065 + 0.04 * uWet);
        pool += siren * (0.04 + 0.025 * sq(uSafe.y)) *
                exp(-sq((screen.x - 0.18 * uSafe.y) / 0.93) - sq(screen.y / 0.91));
        float3 poolRGB = mix(accent(uScene.x), accent(uScene.y), uScene.z);
        float underA = shadow + pool * (1.0 - shadow);
        float3 underRGB = poolRGB * pool * (1.0 - shadow);
        // The CPU expands the bound for the furthest tumbling coin; pressing widens it too.
        float radius = uRadius * uScale * rsqrt(1.0 - 0.12 * uPress);
        float rb = dot(ro, ray);
        float det = rb * rb - dot(ro, ro) + radius * radius;
        if (det < 0.0)
            return finishPixel(underRGB, underA, xy);
        float nearT = -rb - sqrt(max(det, 0.0)), farT = -rb + sqrt(max(det, 0.0));
        float marchT = max(nearT, 0.0), closest = 10.0, bestT = marchT;
        float pixel = 1.0 / (uUnit * uPixelRatio);
        float threshold = pixel * 0.18;
        bool hit = false;
        for (int i = 0; i < 48; i++) {
            float d = sceneD(ro + ray * marchT);
            if (d < closest) {
                closest = d;
                bestT = marchT;
            }
            if (d < threshold) {
                hit = true;
                break;
            }
            marchT += max(d * mix(0.78, 0.52, uFront.w), threshold * 0.55);
            if (marchT > farT)
                break;
        }
        float coverage = hit ? 1.0 : 1.0 - smoothstep(threshold, pixel * 1.1, closest);
        if (coverage <= 0.0)
            return finishPixel(underRGB, underA, xy);
        float3 p = ro + ray * bestT;
        float3 n = surfaceNormal(p);
        Clay surface = field(objectPoint(p), true);
        float ao = 0.0;
        for (int i = 0; i < 3; i++) {
            float h = 0.05 + float(i) * 0.095;
            ao += max(0.0, h - sceneD(p + n * h)) / (1.0 + float(i)) * 1.65;
        }
        ao = clamp(1.0 - ao, 0.58, 1.0);
        float3 key = safeUnit(float3(-0.60, 0.78, 1.1));
        float3 fill = safeUnit(float3(0.82, 0.18, 0.45));
        float nl = clamp((dot(n, key) + 0.42) / 1.42, 0.0, 1.0);
        float nf = max(dot(n, fill), 0.0);
        float nv = clamp(dot(n, -ray), 0.0, 1.0);
        float3 shadowTint = mix(surface.rgb * float3(0.72, 0.62, 0.68), surface.rgb, 0.35);
        float3 colour = mix(shadowTint, surface.rgb, nl) * (.68 + 0.36 * nl) * ao;
        colour += surface.rgb * float3(0.10, 0.13, 0.20) * nf;
        float3 hv = safeUnit(key - ray);
        float hn = max(dot(n, hv), 0.0);
        float wet = molten(objectPoint(p)) * (1.0 - surface.pigment);
        float broad = pow(hn, mix(24.0, 12.0, wet));
        float tight = pow(hn, 120.0);
        float spec = (0.12 + 0.21 * surface.gloss + 0.18 * wet) * broad + 0.26 * surface.gloss * tight;
        colour += float3(1.0, 0.95, 0.85) * spec * ao;
        float rim = pow(max(1.0 - nv, 0.0), 3.0) * max(dot(n, safeUnit(float3(0.8, -0.35, 0.30))), 0.0);
        colour += float3(0.44, 0.60, 0.95) * rim * (0.19 + 0.12 * surface.gloss);
        colour += surface.rgb * surface.emit * 0.50;
        colour += seamAccent() * 0.13 * wet * wet;
        // The hot front lights the remaining old clay as it leans away from the key.
        // Lift perceptual lightness, preserving chroma instead of adding grey light.
        if (uFront.w > 0.0 && uScene.w < 0.5) {
            float3 lit = toLab(max(colour, float3(0.0)));
            float reflectedHeat = 0.20 * sqrt(max(uWet, 0.0)) * (1.0 - smoothstep(0.35, 0.5, uScene.w));
            lit.x += reflectedHeat * (1.0 - frontWeight(objectPoint(p))) * (1.0 - surface.pigment);
            colour = fromLab(lit);
        }
        // Smooth highlight shoulder preserves the saturated diffuse body.
        float3 excess = max(colour - 0.86, 0.0);
        colour = min(colour, float3(0.86)) + excess / (1.0 + excess / 0.14);
        colour = clamp(colour, 0.0, 1.0);
        return finishPixel(colour * coverage + underRGB * (1.0 - coverage),
                           coverage + underA * (1.0 - coverage), xy);
    }
};

[[stitchable]] half4 iconMorphSheet(float2 position, float2 uCentre, float uUnit, float uPixelRatio,
                                    float uCamera, float uRadius, float4 uScene, float4 uShape, float uTurn,
                                    float uScale, float uPress, float uWet, float4 uFront, float4 uZero,
                                    float4 uCoin0, float4 uCoin1, float4 uCoin2, float4 uCoinTurn0,
                                    float4 uCoinTurn1, float4 uCoinTurn2, float4 uSafe, float4 uEnvelope,
                                    float4 uNote, float4 uBook, float4 uBookTurn) {
    IconMorphSheetShader icon;
    icon.uCentre = uCentre;
    icon.uUnit = uUnit;
    icon.uPixelRatio = uPixelRatio;
    icon.uCamera = uCamera;
    icon.uRadius = uRadius;
    icon.uScene = uScene;
    icon.uShape = uShape;
    icon.uTurn = uTurn;
    icon.uScale = uScale;
    icon.uPress = uPress;
    icon.uWet = uWet;
    icon.uFront = uFront;
    icon.uZero = uZero;
    icon.uCoin0 = uCoin0;
    icon.uCoin1 = uCoin1;
    icon.uCoin2 = uCoin2;
    icon.uCoinTurn0 = uCoinTurn0;
    icon.uCoinTurn1 = uCoinTurn1;
    icon.uCoinTurn2 = uCoinTurn2;
    icon.uSafe = uSafe;
    icon.uEnvelope = uEnvelope;
    icon.uNote = uNote;
    icon.uBook = uBook;
    icon.uBookTurn = uBookTurn;
    // Skia's recorded surface uses Display P3 numeric primaries. SwiftUI's shader
    // destination is extended sRGB; preserve that authored appearance at the boundary.
    // Keep negative extended components until SwiftUI converts into the display gamut.
    half4 pixel = icon.main(position);
    float alpha = float(pixel.a);
    float3 encodedP3 = float3(pixel.rgb) / max(alpha, 0.000001);
    float3 linearP3 = select(encodedP3 / 12.92, pow((encodedP3 + 0.055) / 1.055, float3(2.4)),
                            encodedP3 > 0.04045);
    float3 linearSRGB = float3(
        dot(linearP3, float3(1.22494018, -0.22494018, 0.0)),
        dot(linearP3, float3(-0.04205695, 1.04205695, 0.0)),
        dot(linearP3, float3(-0.01963755, -0.07863605, 1.09827360)));
    float3 magnitude = abs(linearSRGB);
    float3 encodedSRGB = sign(linearSRGB) * select(magnitude * 12.92,
        1.055 * pow(magnitude, float3(1.0 / 2.4)) - 0.055, magnitude > 0.0031308);
    return half4(half3(encodedSRGB * alpha), pixel.a);
}
