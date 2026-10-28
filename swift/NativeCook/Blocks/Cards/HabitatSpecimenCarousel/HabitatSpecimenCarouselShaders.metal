#include <metal_stdlib>
using namespace metal;

// The carousel's five effects, translated line by line from the SkSL of HabitatShaders.ts:
// the habitat environment, the creature (rig, lighting, lids, live flame), the hearts of petting
// and the signature moves. Every function is in a namespace so this block shares the module with
// all the others. Coordinates are points, as in the reference; `xy` is the fragment's centre.

namespace HabitatSC {

// One seam formula is shared by the environment, the creature rim light and the particle world:
// the incoming habitat enters from the right edge, its edge bowed by a slow current.
inline float seamLine(float y, float f, float phase) {
    float bow = sin(3.14159265 * f);
    return 1.08 - 1.16 * f + bow * (.040 * sin(y * 5.5 + phase) + .018 * sin(y * 14.0 - phase * 2.0 + 1.3));
}
inline float mod(float x, float y) { return x - y * floor(x / y); }
inline float gauss(float x) { return exp(-x * x); }
inline float bell(float2 p) { return exp(-dot(p, p)); }
inline float grain(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }
inline float hash(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }
inline float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = i.x + i.y * 57.0;
    return mix(mix(hash(n), hash(n + 1.0), f.x), mix(hash(n + 57.0), hash(n + 58.0), f.x), f.y);
}
inline float fbm(float2 p) {
    float v = 0.0;
    float w = .5;
    for (int i = 0; i < 4; i++) { v += w * noise(p); p = p * 2.03 + 17.0; w *= .5; }
    return v;
}
inline float dot2(float2 v) { return dot(v, v); }

}  // namespace HabitatSC

// A rectangle of the view, in points, drawn as one quad.
struct HabitatSCRegion {
    float4 rect;     // x, y, width, height
    float4 view;     // viewport width, height (points), pixel scale, unused
};
struct HabitatSCRegionOut {
    float4 position [[position]];
};

vertex HabitatSCRegionOut habitatSCRegionVertex(uint vid [[vertex_id]], constant HabitatSCRegion& region [[buffer(0)]]) {
    const float2 corners[6] = { float2(0, 0), float2(1, 0), float2(0, 1), float2(0, 1), float2(1, 0), float2(1, 1) };
    float2 c = corners[vid];
    float2 p = region.rect.xy + c * region.rect.zw;
    HabitatSCRegionOut out;
    out.position = float4(p.x / region.view.x * 2.0 - 1.0, 1.0 - p.y / region.view.y * 2.0, 0.0, 1.0);
    return out;
}

// MARK: - Habitat environment

struct HabitatSCEnvUniforms {
    float4 creature;   // creature size, drift (points), top margin of the creature in the stage, unused
    float4 screenStage; // resolution.xy, stage.xy (hero top, hero height)
    float4 clock;      // position, time, reduced, influence
    float4 flare;      // flare, flareElement, lift.x (hop height, points), lift.y (landing squash)
};

namespace HabitatSC {

inline float3 fireHabitat(float2 uv, float2 scene, float phase, float current, float boost) {
    float halo = bell((uv - float2(.50, .34)) / float2(.61, .38));
    float3 color = mix(float3(.075, .041, .033), float3(.31, .135, .060), halo * (1.0 + boost * .55));
    // The hot column narrows as it rises and curls with a single upward current.
    float rise = clamp(1.0 - scene.y, 0.0, 1.0);
    float bend = .025 * sin(scene.y * 11.0 + phase * 2.0) + .015 * sin(scene.y * 23.0 + phase) + current * rise * .13;
    float column = (scene.x - .50 - bend) / (.12 + .13 * scene.y + boost * .05);
    float plume = exp(-column * column);
    float convection = .5 + .5 * sin(scene.y * 22.0 + phase * 2.0 + sin(scene.x * 13.0 + phase));
    float extent = smoothstep(.02, .20, scene.y) * (1.0 - smoothstep(.87, 1.05, scene.y));
    color += float3(.092, .033, .006) * plume * extent * (.35 + .65 * convection) * (1.0 - rise * .40) * (1.0 + boost * 2.2);
    return color;
}
inline float3 waterHabitat(float2 uv, float2 scene, float phase, float current, float boost) {
    float halo = bell((uv - float2(.50, .35)) / float2(.65, .38));
    float3 color = mix(float3(.018, .067, .105), float3(.051, .235, .29), halo * (1.0 + boost * .35));
    // Interference drifts like refracted surface light, confined to the floor plane.
    float2 p = (scene - float2(current * .10 * scene.y, 0.0)) * float2(8.0, 6.0);
    float a = sin(p.x * 1.7 + sin(p.y * 1.4 + phase) + phase);
    float b = sin(p.y * 1.8 + sin(p.x * 1.1 - phase * 2.0) - phase);
    float c = sin((p.x - p.y) * 1.2 + phase * 2.0);
    float caustic = pow(clamp(1.0 - abs(a + b + c) * .62, 0.0, 1.0), 8.0);
    float ground = smoothstep(.55, .78, scene.y) * (1.0 - smoothstep(.96, 1.18, scene.y));
    color += float3(.13, .30, .32) * caustic * ground * .26 * (1.0 + boost * 2.5);
    float radius = length((scene - float2(.5 - current * .10, .88)) * float2(1.0 - abs(current) * .18, 3.8));
    float ripple = pow(max(.5 + .5 * sin(radius * 52.0 - phase * 2.0 - boost * 9.0), 0.0), 22.0);
    color += float3(.07, .18, .19) * ripple * ground * exp(-radius * 2.4) * .24 * (1.0 + boost * 3.0);
    return color;
}
inline float3 grassHabitat(float2 uv, float2 scene, float phase, float current, float boost) {
    float halo = bell((uv - float2(.50, .34)) / float2(.64, .39));
    float3 color = mix(float3(.028, .080, .053), float3(.13, .255, .115), halo * (1.0 + boost * .45));
    // Broad, soft canopy dapples: no opaque leaves, spots or straight light shafts.
    float wind = .035 * sin(phase) + .016 * sin(phase * 2.0 + scene.y * 4.0) + current * .10 * (.2 + scene.y);
    float2 canopy = float2(scene.x - wind, scene.y);
    float patches = .5 + .5 * sin(canopy.x * 9.0 + sin(canopy.y * 6.0 + phase) * .65);
    patches *= .5 + .5 * cos(canopy.y * 8.0 - canopy.x * 4.0 + .22 * sin(phase));
    float dapple = pow(max(patches, 0.0), 2.6);
    float canopyArea = (1.0 - smoothstep(.72, 1.06, scene.y)) * smoothstep(-.35, .10, scene.y);
    color += float3(.16, .18, .065) * dapple * canopyArea * .46 * (1.0 + boost * 1.6);
    return color;
}
inline float3 habitat(constant HabitatSCEnvUniforms& u, float index, float2 uv, float2 scene, float phase, float current) {
    float boost = u.flare.x * (1.0 - step(.5, abs(index - u.flare.y)));
    if (index < .5) return fireHabitat(uv, scene, phase, current, boost);
    if (index < 1.5) return waterHabitat(uv, scene, phase, current, boost);
    return grassHabitat(uv, scene, phase, current, boost);
}
// Contact shadow of a specimen standing at cx with scale s (exchangePose in the motion file).
inline float shadowAt(constant HabitatSCEnvUniforms& u, float2 xy, float cx, float s, float spread) {
    float cy = u.screenStage.z + u.creature.z + u.creature.x * (.5 + .472 * s);
    float rx = u.creature.x * s * spread;
    float2 q = (xy - float2(cx, cy)) / float2(rx, rx * .12 + .001);
    float2 wide = q * float2(.62, .42);
    return exp(-dot(q, q) * 2.6) * .62 + exp(-dot(wide, wide) * 2.0) * .22;
}
inline float spreadOf(float index) { return index < .5 ? .30 : (index < 1.5 ? .36 : .31); }

}  // namespace HabitatSC

fragment float4 habitatSCEnvironment(HabitatSCRegionOut in [[stage_in]],
                                      constant HabitatSCRegion& region [[buffer(0)]],
                                      constant HabitatSCEnvUniforms& u [[buffer(1)]]) {
    using namespace HabitatSC;
    float2 xy = in.position.xy / region.view.z;
    float2 resolution = u.screenStage.xy;
    float2 stage = u.screenStage.zw;
    float position = u.clock.x;
    float time = u.clock.y;
    float reduced = u.clock.z;
    float influence = u.clock.w;
    float2 lift = u.flare.zw;
    float2 uv = xy / resolution;
    float2 scene = float2(uv.x, (xy.y - stage.x) / stage.y);
    float p = mod(position + 300.0, 3.0);
    float a = floor(p);
    float f = p - a;
    float b = mod(a + 1.0, 3.0);
    float phase = 6.28318530718 * fract(time) * (1.0 - reduced);
    float current = clamp(influence, -1.0, 1.0) * (1.0 - reduced);
    float k = sin(3.14159265 * f) * (1.0 - reduced);
    float seam = reduced > .5 ? (f < .5 ? 2.0 : -1.0) : seamLine(uv.y, f, phase);
    // A meadow grows into frame as a scalloped hedge rather than a clean wipe.
    if (b > 1.5) seam -= .011 * abs(sin(uv.y * 46.0 + phase)) * k;
    float edge = (uv.x - seam) * resolution.x;
    float side = smoothstep(-1.0, 1.0, edge);
    float3 color = float3(0.0);
    if (side < .999) {
        float2 bend = float2(0.0);
        // Entering water lenses the old habitat just ahead of its crest.
        if (b > .5 && b < 1.5) bend = float2(-.014 * gauss(max(-edge, 0.0) / 28.0) * k, 0.0);
        color += habitat(u, a, uv + bend, scene + bend, phase, current) * (1.0 - side);
    }
    if (side > .001) color += habitat(u, b, uv, scene, phase, current) * side;
    if (k > .001) {
        float behind = max(-edge, 0.0);
        float ahead = max(edge, 0.0);
        if (b < .5) {
            // Fire: the old world chars just behind a flickering ember rim.
            color *= 1.0 - .55 * k * exp(-behind / 20.0) * (1.0 - side);
            float flicker = .75 + .25 * sin(uv.y * 61.0 + phase * 6.0) * sin(uv.y * 23.0 - phase * 4.0);
            color += float3(1.0, .42, .11) * gauss(edge / 4.5) * flicker * .95 * k;
            color += float3(.55, .18, .04) * exp(-ahead / 34.0) * side * .30 * k;
            float row = floor(xy.y / 5.0);
            float spark = step(.93, grain(row + floor(phase * 3.0) * 13.0));
            color += float3(1.0, .62, .25) * spark * gauss((edge + 7.0 + grain(row) * 16.0) / 2.5) * k;
        } else if (b < 1.5) {
            // Water: a foam crest with a moving glint, and refracted light just ahead.
            float foam = gauss(edge / 2.2) * (.55 + .45 * sin(uv.y * 92.0 + phase * 5.0));
            color += float3(.70, .93, .98) * foam * .85 * k;
            color += float3(.10, .32, .36) * exp(-ahead / 30.0) * side * .45 * k;
        } else {
            // Grass: a sunlit hedge edge with soft shade on the side it covers.
            color *= 1.0 - .30 * k * exp(-behind / 26.0) * (1.0 - side);
            color += float3(.55, .80, .30) * gauss(edge / 2.6) * .32 * k + float3(.20, .32, .10) * exp(-ahead / 14.0) * side * .25 * k;
        }
    }
    // Each shadow belongs to its world: it is wiped by the same front as the specimen standing in it.
    float motion = 1.0 - reduced;
    float uu = 1.0 - f;
    // The shadow stays on the floor: it shrinks and pales while its specimen is in the air, and
    // spreads as it lands.
    float air = clamp(lift.x / u.creature.x, 0.0, 1.0);
    float shade = shadowAt(u, xy, resolution.x * .5 - u.creature.y * .5 * f * f * motion, 1.0 - .05 * f * motion, spreadOf(a) * (1.0 + .12 * lift.y)) * (1.0 - side);
    if (side > .001) shade += shadowAt(u, xy, resolution.x * .5 + u.creature.y * uu * uu * motion, 1.0 + .05 * uu * uu * motion, spreadOf(b) * (1.0 - air * 1.1)) * side * (1.0 - air * 1.6);
    color *= 1.0 - min(shade, .85);
    float reading = smoothstep(.49, .76, uv.y);
    color = mix(color, color * .23 + float3(.005, .011, .010), reading * .92);
    color *= 1.0 - .16 * (uv.x - .5) * (uv.x - .5) * 4.0;
    return float4(color, 1.0);
}

// MARK: - Creature

// Every member is a float4 so the Swift mirror (HabitatSpecimenCarouselCreatureUniforms) agrees.
struct HabitatSCCreatureUniforms {
    float4 boxPd;       // box.x (art square, points), box.y (feet line), pd, zoom
    float4 invX;        // canvas point -> art box, x row; w: front
    float4 invY;        // y row; w: phase
    float4 j0; float4 j1; float4 j2; float4 j3;  // pivot.xy, centre.xy
    float4 k01;         // radius0, angle0, radius1, angle1
    float4 k23;
    float4 chest;       // centre, radius, breath
    float4 hips;        // hips y, shift, hang, blend
    float4 eyeA;
    float4 eyeB;
    float4 lidOrigin;   // lid, squint, origin.xy
    float4 screenShift; // screen.xy, headShift, role
    float4 misc;        // element, strength, shade, hidden
    float4 tint;
    float4 light;
    float4 glowAt;
    float4 glowColor;
    float4 face;
    float4 flame;
    float4 flameArea;
    float4 flameState;
};
struct HabitatSCCreatureQuad {
    float4 quad;        // canvas origin on screen (points), canvas size (points, before zoom)
    float4 view;        // viewport w, h, pixel scale, zoom
};

vertex HabitatSCRegionOut habitatSCCreatureVertex(uint vid [[vertex_id]], constant HabitatSCCreatureQuad& q [[buffer(0)]]) {
    const float2 corners[6] = { float2(0, 0), float2(1, 0), float2(0, 1), float2(0, 1), float2(1, 0), float2(1, 1) };
    float2 p = q.quad.xy + corners[vid] * q.quad.zw * q.view.w;
    HabitatSCRegionOut out;
    out.position = float4(p.x / q.view.x * 2.0 - 1.0, 1.0 - p.y / q.view.y * 2.0, 0.0, 1.0);
    return out;
}

namespace HabitatSC {

constant float LOOP_S = 13.50;

struct CreatureContext {
    constant HabitatSCCreatureUniforms& u;
    texture2d<float> image;
    texture2d<float> normals;
    sampler imageSampler;
    sampler normalSampler;
    float box;
    float4 eval(float2 a) const {
        return image.sample(imageSampler, a / box);
    }
};

// Noise rising through the flame that loops with the ambient clock: two samples a loop apart,
// blended across it, so the flicker never jumps when the loop starts again.
inline float rising(constant HabitatSCCreatureUniforms& u, float2 p, float speed) {
    float s = u.flameState.y;
    return mix(fbm(p - float2(0.0, s * speed)), fbm(p - float2(0.0, (s - LOOP_S) * speed)), s / LOOP_S);
}
// The drawn flame's own pixels: flame colours inside its ellipse, down to the tail tip it burns from.
inline float flameMask(constant HabitatSCCreatureUniforms& u, float2 p, float4 c) {
    float2 e = (p - u.flameArea.xy) / u.flameArea.zw;
    float inside = (1.0 - smoothstep(.88, .93, dot(e, e))) * (1.0 - smoothstep(u.flameArea.y + u.flameArea.w * .82, u.flameArea.y + u.flameArea.w * .90, p.y));
    return inside * smoothstep(.02, .14, (c.r - c.b) / max(c.a, .001));
}
// The live flame at an art-box point. At rest it is the drawn flame itself, licked sideways by
// rising noise, swaying at the tip and stretching on two waves. In the move a plume with the drawn
// flame's teardrop, cel edge and colours takes over from it as it starts to swell, then grows.
inline float4 liveFlame(thread const CreatureContext& cx, float2 p) {
    constant HabitatSCCreatureUniforms& u = cx.u;
    float grow = u.flameState.x;
    float live = u.flameState.w;
    float hr = u.flame.z;
    float h = hr * (1.0 + 1.25 * grow) * (1.0 + u.flameState.z);
    float2 q = p - u.flame.xy;
    float v = -q.y / h;
    if (v < -.1 || v > 1.25 || abs(q.x) > h * .6) return float4(0.0);
    float lt = u.flameState.y / LOOP_S * 6.2831853;
    float sway = (sin(lt * 9.0 + v * 3.0) * .05 + sin(lt * 17.0 - v * 6.0) * .025) * v * v * live;
    float uu = q.x / h - sway;
    float n = .5;
    float m = .5;
    if (live > .5) { n = rising(u, float2(uu * 7.5, v * 4.2), 5.5); m = rising(u, float2(uu * 15.0 + 3.1, v * 8.0), 9.0); }
    float2 src = u.flame.xy + float2((uu + (n - .5) * .09 * v) * hr, q.y * hr / h);
    float4 art = cx.eval(src);
    float4 drawn = art * flameMask(u, src, art);
    if (grow <= .001) return drawn;
    float x = clamp(v + .05, 0.0, 1.0);
    float profile = .82 * pow(x, .9) * (1.0 - x);
    float w = uu + (n - .5) * .10 * (.2 + v);
    float f = (1.0 - abs(w) / max(profile, .0001)) * 1.15 + (n - .5) * (.35 + 1.3 * v) + (m - .5) * (.2 + .5 * v) - smoothstep(.75, 1.1, v) * .35;
    // The top splits into licking tongues that run upwards, as the drawn flame's tip does.
    f -= smoothstep(.42, .95, v) * (.55 + .45 * sin(w * 34.0 + n * 6.0 - lt * 17.0)) * .85;
    float4 plume = float4(0.0);
    if (f > 0.0) {
        // Shaded like the drawn flame: a crisp outline over nested flames that blend softly, red
        // outside, orange, a yellow heart low down (colours sampled from the art), each with its own noise.
        float mid = (1.0 - abs(w + (m - .5) * .05) / max(profile * .66, .0001)) * 1.1 + (m - .5) * (.4 + 1.1 * v) - smoothstep(.38, .78, v) * 1.0;
        float heart = (1.0 - abs(w) / max(profile * .42, .0001)) * 1.05 + (n - .5) * .55 - smoothstep(.16, .46, v) * 1.1;
        float3 hot = mix(float3(1.0, .27, .17), float3(1.0, .40, .26), smoothstep(.55, 1.0, v));
        hot = mix(hot, float3(1.0, .56, .20), smoothstep(-.05, .32, mid));
        hot = mix(hot, float3(1.0, .96, .30), smoothstep(-.05, .36, heart));
        float body = smoothstep(0.0, .05, f);
        plume = float4(hot * body, body);
    }
    // The plume burns through the drawn flame (along the same rising noise) while it is still the
    // drawn flame's size: each pixel is one or the other, so the two never ghost.
    float take = smoothstep(n - .08, n + .08, smoothstep(0.0, .15, grow) * 1.3 - .15);
    return mix(drawn, plume, take);
}
inline float weight(float2 a, float4 j, float radius) {
    if (radius <= 0.0) return 0.0;
    return 1.0 - smoothstep(radius * .45, radius * 1.1, length(a - j.zw));
}
// A bend whose angle fades with the weight, so the region curls instead of tearing off.
inline float2 bend(float2 a, float4 j, float radius, float angle) {
    float w = weight(a, j, radius);
    if (w <= 0.0) return float2(0.0);
    float t = angle * w;
    float c = cos(t);
    float s = sin(t);
    float2 r = a - j.xy;
    return float2(c * r.x - s * r.y, s * r.x + c * r.y) + j.xy - a;
}
inline float2 field(constant HabitatSCCreatureUniforms& u, float2 a) {
    float2 d = bend(a, u.j0, u.k01.x, u.k01.y) + bend(a, u.j1, u.k01.z, u.k01.w) + bend(a, u.j2, u.k23.x, u.k23.y) + bend(a, u.j3, u.k23.z, u.k23.w);
    d.x += u.screenShift.z * weight(a, u.j0, u.k01.x);
    // The head's turn: the face's middle slides, its outline stays.
    d.x += u.face.w * (1.0 - smoothstep(u.face.z * .15, u.face.z * .95, length(a - u.face.xy)));
    float wb = 1.0 - smoothstep(u.chest.z * .3, u.chest.z * 1.25, length(a - u.chest.xy));
    d += float2((a.x - u.chest.x) * .5, a.y - u.boxPd.y) * u.chest.w * wb;
    d.x += u.hips.y * (1.0 - smoothstep(u.hips.x - u.hips.w, u.boxPd.y, a.y));
    d.y += max(a.y - u.hips.x, 0.0) * u.hips.z;
    return d;
}
// Lids are skin taken from just above the eye; the ellipse is 2 units larger than the eye white.
inline float4 lidded(thread const CreatureContext& cx, float4 c, float2 a, float4 eye, float closed, float squint) {
    constant HabitatSCCreatureUniforms& u = cx.u;
    if (eye.z <= 0.0 || (closed <= .001 && squint <= .001)) return c;
    float2 e = (a - eye.xy) / (eye.zw + 2.0);
    float rr = dot(e, e);
    if (rr >= 1.0) return c;
    float half_ = sqrt(max(1.0 - e.x * e.x, 0.0));
    float v = (e.y / half_ + 1.0) * .5;
    float upper = max(closed * .86, squint * .30);
    float lower = max(closed * .14, squint * .42);
    float lash = min(abs(v - upper), abs(v - (1.0 - lower))) * (eye.w + 2.0) * 2.0 * half_;
    float covered = step(v, upper) + step(1.0 - lower, v);
    if (covered < .5) {
        if (lash < 1.6 && (upper > .02 || lower > .02)) return float4(c.rgb * (.55 + .45 * lash / 1.6), c.a);
        return c;
    }
    // Skin from just above the eye; the art draws a dark lid line there on some eyes, so of three
    // samples going up the lightest opaque one is the skin. Where the head's outline runs just above
    // the eye there is no skin above it, so the whole lid takes the skin beside the eye, towards
    // the middle of the face: decided once per eye, so a lid never shows a seam.
    float top = eye.y - (eye.w + 2.0) * half_;
    float4 skin = cx.eval(float2(a.x, top - 3.0));
    float4 s2 = cx.eval(float2(a.x, top - 8.0));
    float4 s3 = cx.eval(float2(a.x, top - 14.0));
    if (s2.a >= .95 && dot(s2.rgb, float3(1.0)) > dot(skin.rgb, float3(1.0))) skin = s2;
    if (s3.a >= .95 && dot(s3.rgb, float3(1.0)) > dot(skin.rgb, float3(1.0))) skin = s3;
    float2 inward = u.face.xy - eye.xy;
    float4 side = cx.eval(eye.xy + inward / max(length(inward), .001) * (max(eye.z, eye.w) + 6.0));
    float trust = step(.95, cx.eval(float2(eye.x, eye.y - eye.w - 5.0)).a);
    float3 tone = mix(side.rgb / max(side.a, .001), skin.rgb / max(skin.a, .001), trust);
    float4 lidc = float4(tone * c.a * .92, c.a);
    if (lash < 1.6) lidc.rgb *= .55 + .45 * lash / 1.6;
    return lidc;
}

}  // namespace HabitatSC

fragment float4 habitatSCCreature(HabitatSCRegionOut in [[stage_in]],
                                   constant HabitatSCCreatureQuad& quad [[buffer(0)]],
                                   constant HabitatSCCreatureUniforms& u [[buffer(1)]],
                                   texture2d<float> image [[texture(0)]],
                                   texture2d<float> normals [[texture(1)]]) {
    using namespace HabitatSC;
    if (u.misc.w > .5) return float4(0.0);
    constexpr sampler imageSampler(coord::normalized, address::clamp_to_zero, filter::linear, mip_filter::none);
    constexpr sampler normalSampler(coord::normalized, address::clamp_to_edge, filter::linear, mip_filter::none);
    float2 box = u.boxPd.xy;
    float pd = u.boxPd.z;
    float zoom = u.boxPd.w;
    float front = u.invX.w;
    float phase = u.invY.w;
    float2 origin = u.lidOrigin.zw;
    float2 screen = u.screenShift.xy;
    float role = u.screenShift.w;
    float element = u.misc.x;
    float strength = u.misc.y;
    float shade = u.misc.z;
    CreatureContext cx = { u, image, normals, imageSampler, normalSampler, box.x };

    float2 sp = in.position.xy / quad.view.z;
    float2 p = (sp - origin) / zoom;
    float y = sp.y / screen.y;
    float k = sin(3.14159265 * front);
    float seam = seamLine(y, front, phase);
    if (element > 1.5) seam -= .011 * abs(sin(y * 46.0 + phase)) * k;
    float edge = sp.x - seam * screen.x;
    float side = smoothstep(-1.0, 1.0, edge);
    float keep = role > .5 ? side : 1.0 - side;
    if (keep < .001) return float4(0.0);
    float2 q = float2(dot(u.invX.xyz, float3(p, 1.0)), dot(u.invY.xyz, float3(p, 1.0)));
    float2 a = q;
    for (int i = 0; i < 3; i++) a = q - field(u, a);
    if (a.x < -2.0 || a.y < -2.0 || a.x > box.x + 2.0 || a.y > box.x + 2.0) return float4(0.0);
    float behind = max(-edge, 0.0);
    float2 at = a;
    // Entering water lenses the outgoing specimen just ahead of its crest, as it lenses the habitat.
    if (role < .5 && element > .5 && element < 1.5) at.x -= .014 * screen.x * gauss(behind / 28.0) * k / zoom;
    float4 c = cx.eval(at);
    // The drawn flame is replaced by the live one, drawn over the lit body.
    float4 fire = float4(0.0);
    if (u.flame.w > .5) {
        fire = liveFlame(cx, at);
        if (c.a >= .01) c *= 1.0 - flameMask(u, at, c);
    }
    if (c.a < .01 && fire.a < .002) return float4(0.0);
    float4 body = float4(0.0);
    if (c.a >= .01) {
        c = lidded(cx, c, at, u.eyeA, u.lidOrigin.x, u.lidOrigin.y);
        c = lidded(cx, c, at, u.eyeB, u.lidOrigin.x, u.lidOrigin.y);
        // Volume: the baked normal turns the flat art towards or away from the key light.
        float3 n = normalize(normals.sample(normalSampler, at / box.x).rgb * 2.0 - 1.0);
        float3 L = normalize(float3(u.light.xy, .85));
        float lambert = dot(n, L) - L.z;
        float3 h = normalize(L + float3(0.0, 0.0, 1.0));
        float sheen = pow(max(dot(n, h), 0.0), 28.0);
        float3 rgb = c.rgb * (1.0 + u.light.z * .30 * lambert) + float3(1.0, .97, .9) * sheen * u.light.z * .07 * c.a;
        // The habitat's floor bounces its colour onto surfaces that face down.
        rgb += u.tint.xyz * max(n.y, 0.0) * .10 * u.light.z * c.a;
        float2 left = float2(-.62, -.78) * pd;
        float2 right = float2(.62, -.78) * pd;
        // A soft back light: the rim fades smoothly into the form instead of reading as an outline.
        float l = cx.eval(at + left * 1.5).a * .30 + cx.eval(at + left * 3.0).a * .30 + cx.eval(at + left * 5.0).a * .25 + cx.eval(at + left * 7.5).a * .15;
        float r = cx.eval(at + right * 1.5).a * .30 + cx.eval(at + right * 3.0).a * .30 + cx.eval(at + right * 5.0).a * .25 + cx.eval(at + right * 7.5).a * .15;
        float rim = clamp(c.a - l, 0.0, 1.0) + clamp(c.a - r, 0.0, 1.0) * .6;
        rim = rim * rim * (3.0 - 2.0 * rim);
        rgb = rgb * (1.0 - shade) + u.tint.xyz * rim * strength * c.a;
        // The signature move lights the body that makes it.
        if (u.glowAt.w > .001) {
            float2 toward = u.glowAt.xy - at;
            float3 dir = normalize(float3(toward, u.glowAt.z * .35));
            float reach = exp(-dot(toward, toward) / (u.glowAt.z * u.glowAt.z));
            rgb += u.glowColor.xyz * (.35 + .65 * max(dot(n, dir), 0.0)) * reach * u.glowAt.w * .55 * c.a;
        }
        if (k > .001) {
            if (element < .5) {
                if (role < .5) rgb *= 1.0 - .55 * k * exp(-behind / 20.0);
                float flicker = .75 + .25 * sin(y * 61.0 + phase * 6.0) * sin(y * 23.0 - phase * 4.0);
                rgb += float3(1.0, .42, .11) * gauss(edge / 4.5) * flicker * .95 * k * c.a;
            } else if (element < 1.5) {
                rgb += float3(.70, .93, .98) * gauss(edge / 2.2) * (.55 + .45 * sin(y * 92.0 + phase * 5.0)) * .85 * k * c.a;
            } else {
                if (role < .5) rgb *= 1.0 - .30 * k * exp(-behind / 26.0);
                rgb += float3(.55, .80, .30) * gauss(edge / 2.6) * .32 * k * c.a;
            }
        }
        body = float4(min(rgb, float3(c.a)), c.a);
    }
    return (fire + body * (1.0 - fire.a)) * keep;
}

// MARK: - Affection (hearts that rise from a petted creature)

struct HabitatSCAffectionUniforms {
    float4 a;      // resolution.xy, source.xy
    float4 b;      // clock, until, unit, reduced
    float4 color;  // rgb, unused
};

namespace HabitatSC {
inline float sdHeart(float2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return length(p - float2(.25, .75)) - .35355339;
    float2 m = p - .5 * max(p.x + p.y, 0.0);
    return sqrt(min(dot(p - float2(0.0, 1.0), p - float2(0.0, 1.0)), dot(m, m))) * sign(p.x - p.y);
}
inline float heartGrain(float n) { return fract(sin(n * 91.7 + 17.3) * 43758.5453); }
}

fragment float4 habitatSCAffection(HabitatSCRegionOut in [[stage_in]],
                                    constant HabitatSCRegion& region [[buffer(0)]],
                                    constant HabitatSCAffectionUniforms& u [[buffer(1)]]) {
    using namespace HabitatSC;
    float2 xy = (in.position.xy / region.view.z) - region.rect.xy;
    float2 source = u.a.zw;
    float clock = u.b.x;
    float until = u.b.y;
    float unit = u.b.z;
    float reduced = u.b.w;
    float3 color = u.color.xyz;
    float3 rgb = float3(0.0);
    float alpha = 0.0;
    for (int i = 0; i < 14; i++) {
        float slot = float(i);
        float born = floor((clock - slot * .18) / 2.52) * 2.52 + slot * .18;
        float age = clock - born;
        if (born < 0.0 || born > until || age < 0.0 || age > 1.3) continue;
        float t = age / 1.3;
        float rise = (1.0 - (1.0 - t) * (1.0 - t)) * (1.0 - reduced);
        float sx = (heartGrain(slot) - .5) * 80.0 * unit + sin(age * 5.0 + slot) * 8.0 * unit * (1.0 - reduced);
        float2 c = source + float2(sx, -rise * 95.0 * unit - 10.0 * unit);
        float pop = .6 + .4 * smoothstep(0.0, .16, age);
        float size = (9.0 + heartGrain(slot + 3.0) * 6.0) * unit * pop;
        float2 q = (xy - c) / size;
        q.y = -q.y + .55;
        float d = sdHeart(q) * size;
        float fade = smoothstep(0.0, .12, t) * (1.0 - smoothstep(.62, 1.0, t));
        float fill = (1.0 - smoothstep(-.6, .6, d)) * fade;
        float glow = exp(-max(d, 0.0) / (3.0 * unit)) * .35 * fade;
        float core = (1.0 - smoothstep(-.6, .6, length(q - float2(-.22, .72)) * size - size * .12)) * fade * .55;
        rgb += color * fill + float3(1.0) * core * fill + color * glow;
        alpha = max(alpha, max(fill, glow));
    }
    return float4(min(rgb, float3(alpha)), alpha);
}

// MARK: - Signature moves

// `secs` runs from the move's start past its end while its particles land; t is the move's own
// 0..1 progress. Attached effects follow the posed body; particles were born at the resting body
// and belong to its world, so the habitat front wipes them like it wipes the specimen.
struct HabitatSCSignatureUniforms {
    float4 a;      // resolution.xy, secs, element
    float4 b;      // layer, floorY, unit, fadeTop
    float4 anchor; // anchorA.xy, anchorB.xy
    float4 aim;    // aimA.xy, aimB.xy
    float4 emit;   // emitA.xy, emitB.xy
    float4 c;      // screen.xy, front, phase
    float4 d;      // role, incoming, particles, unused
    float4 leaf[12];
    float4 leafState[12];
};

namespace HabitatSC {

constant float MOVE = 1.800;
constant float GRAVITY = 1500.0;
constant float FX_S = 3.90;

inline float window(float x, float a, float b, float c, float d) { return smoothstep(a, b, x) * (1.0 - smoothstep(c, d, x)); }

// The live flame's height (the creature shader's liveFlame): the drawn flame's at rest, a plume at the peak.
inline float flameHeight(float unit, float t) { return unit * 74.0 * (1.0 + 1.25 * window(t, .10, .28, .60, .96)); }

// The flame itself is drawn by the creature shader, in the art's own space; this layer adds what leaves it.
inline float4 fireMove(constant HabitatSCSignatureUniforms& u, float2 xy, float t) {
    float unit = u.b.z;
    float secs = u.a.z;
    float2 emitA = u.emit.xy;
    float3 light = float3(0.0);
    float alpha = 0.0;
    // Embers leave the plume's tip where it was when they left, rise on its buoyancy and burn out.
    for (int i = 0; i < 26; i++) {
        if (u.d.z < .5) break;
        float id = float(i);
        float te = .12 + .52 * hash(id + 1.0);
        float age = secs - te * MOVE;
        if (age <= 0.0 || age > .75) continue;
        float2 tip = emitA + float2(0.0, -flameHeight(unit, te) * 1.02);
        float2 velocity = float2((hash(id + 3.0) - .62) * 95.0, -(110.0 + 150.0 * hash(id + 5.0))) * unit;
        float2 pos = tip + velocity * age + float2(sin(age * 9.0 + id) * 7.0, -age * age * 60.0) * unit;
        float radius = (1.1 + 1.5 * hash(id + 7.0)) * unit;
        float d2 = dot2(xy - pos);
        if (d2 > radius * radius * 36.0) continue;
        float life = 1.0 - age / .75;
        float glow = exp(-d2 / (radius * radius)) * life + exp(-d2 / (radius * radius * 9.0)) * .25 * life;
        light += float3(1.0, .58 + .3 * life, .18) * glow;
        alpha += glow * .8;
    }
    return float4(light, clamp(alpha, 0.0, 1.0));
}

inline float4 waterMove(constant HabitatSCSignatureUniforms& u, float2 xy, float t) {
    float unit = u.b.z;
    float floorY = u.b.y;
    float secs = u.a.z;
    float3 light = float3(0.0);
    float alpha = 0.0;
    float speed = 640.0 * unit;
    float g = GRAVITY * unit;
    for (int c = 0; c < 2; c++) {
        float2 origin = c == 0 ? u.anchor.xy : u.anchor.zw;
        float2 born = c == 0 ? u.emit.xy : u.emit.zw;
        float2 aim = normalize(c == 0 ? u.aim.xy : u.aim.zw);
        // Spray at the muzzle while it fires: a short cone along the jet, not a disc.
        float muzzle = window(t, .13, .20, .58, .74);
        float2 mq = xy - origin;
        float along = dot(mq, aim);
        float across = dot(mq, float2(-aim.y, aim.x));
        float spread = (5.0 + .45 * max(along, 0.0)) * unit;
        float puff = gauss(across / spread) * gauss((along - 12.0 * unit) / (16.0 * unit)) * smoothstep(-2.0 * unit, 4.0 * unit, along) * muzzle * .55;
        light += float3(.80, .96, 1.0) * puff; alpha += puff * .6;
        // A continuous stream: each segment joins two parcels of water fired 36 ms apart, each on its
        // own ballistic path, so the jet bends under the same gravity as the drops it sheds.
        float wobble = sin(t * 40.0 + float(c) * 2.0) * .015;
        float2 dir = float2(aim.x * cos(wobble) - aim.y * sin(wobble), aim.x * sin(wobble) + aim.y * cos(wobble));
        // Union of segments (min distance), so joints never double up into beads.
        float2 previous = origin;
        bool hasPrevious = false;
        float nearest = 1e4; float nearestAge = 0.0;
        for (int k = 0; k < 16; k++) {
            float age = float(k) * .036;
            float emitted = t - age / MOVE;
            if (emitted < .15 || emitted > .60) { hasPrevious = false; continue; }
            float2 pos = origin + dir * speed * age + float2(0.0, .5 * g * age * age);
            if (hasPrevious) {
                // Age is interpolated along the segment, so width and ripples run continuously.
                float2 pa = xy - previous; float2 ba = pos - previous;
                float h = clamp(dot(pa, ba) / max(dot(ba, ba), .0001), 0.0, 1.0);
                float gap = length(pa - ba * h);
                if (gap < nearest) { nearest = gap; nearestAge = age - .036 + h * .036; }
            }
            previous = pos; hasPrevious = true;
        }
        if (nearest < 80.0 * unit) {
            // The jet thickens and frays as it travels; its edge ripples with the flow.
            float radius = (10.0 + nearestAge * 34.0) * unit * (1.0 + .05 * sin(nearestAge * 55.0 - t * 48.0) + .03 * sin(nearestAge * 140.0 - t * 90.0));
            float breakup = 1.0 - smoothstep(.30, .56, nearestAge);
            float body = (1.0 - smoothstep(radius * .55, radius, nearest)) * breakup;
            float coreLine = (1.0 - smoothstep(0.0, radius * .22, nearest)) * breakup;
            float rim = gauss((nearest - radius * .72) / (radius * .18 + .3)) * breakup;
            // Shaded like a cylinder of water: deep at the edges, a bright specular streak above the axis.
            float across2 = clamp(nearest / radius, 0.0, 1.0);
            float3 waterColor = mix(float3(.70, .92, 1.0), float3(.22, .52, .74), across2 * across2);
            light += waterColor * body * .85 + float3(1.0) * coreLine * .45 + float3(.85, .97, 1.0) * rim * .18;
            alpha += body * .82 + coreLine * .12;
        }
        // Spray: drops torn off the jet's surface close to the muzzle keep a little of its speed, fall
        // under gravity, land on the floor at their own depth and splash there (a ring on the floor).
        for (int i = 0; i < 24; i++) {
            if (u.d.z < .5) break;
            float id = float(i) + float(c) * 40.0;
            float te = (.15 + .40 * hash(id)) * MOVE;
            float shed = .012 + .09 * hash(id + 1.0);
            float age = secs - te - shed;
            if (age <= 0.0 || age > 2.2) continue;
            float spread2 = (hash(id + 2.0) - .5) * 1.2;
            float keep = .06 + .34 * hash(id + 3.0);
            float2 at = born + dir * speed * shed + float2(0.0, .5 * g * shed * shed);
            float2 jet = dir * speed + float2(0.0, g * shed);
            float2 v = float2(jet.x * cos(spread2) - jet.y * sin(spread2), jet.x * sin(spread2) + jet.y * cos(spread2)) * keep;
            float ground = floorY + (3.0 + 12.0 * hash(id + 4.0)) * unit - at.y;
            float land = (-v.y + sqrt(max(v.y * v.y + 2.0 * g * ground, 0.0))) / g;
            float radius = (1.8 + 2.4 * hash(id + 6.0)) * unit;
            if (age < land) {
                float2 pos = at + v * age + float2(0.0, .5 * g * age * age);
                float d2 = dot2(xy - pos);
                if (d2 > radius * radius * 9.0) continue;
                float drop = (1.0 - smoothstep(radius * .5, radius, sqrt(d2))) * smoothstep(0.0, .04, age);
                float sheen = exp(-dot2(xy - pos + float2(radius * .35)) / (radius * radius * .25)) * drop;
                light += float3(.6, .88, .98) * drop * .8 + float3(1.0) * sheen * .6;
                alpha += drop * .75 + sheen * .3;
            } else {
                float s = (age - land) / .34;
                if (s >= 1.0) continue;
                float2 p = at + v * land + float2(0.0, .5 * g * land * land);
                float reach = radius * (1.6 + 4.0 * s);
                float2 rq = (xy - p) / float2(reach, reach * .3);
                float r = length(rq);
                if (r > 2.0) continue;
                float ring = gauss((r - 1.0) / .25) * (1.0 - s) * (1.0 - s);
                float flash = gauss(r / .5) * (1.0 - smoothstep(0.0, .3, s));
                light += float3(.75, .95, 1.0) * (ring * .75 + flash * .5);
                alpha += ring * .6 + flash * .4;
            }
        }
    }
    return float4(light, clamp(alpha, 0.0, 1.0));
}

// The leaves come from the bulb's physics: position and attitude, then opacity, size, how settled
// on the floor, and which layer it falls in.
inline float4 leavesMove(constant HabitatSCSignatureUniforms& u, float2 xy, float wanted) {
    float unit = u.b.z;
    float3 light = float3(0.0);
    float alpha = 0.0;
    for (int i = 0; i < 12; i++) {
        float4 l = u.leaf[i];
        float4 s = u.leafState[i];
        if (s.x <= .002 || abs(s.w - wanted) > .5) continue;
        float2 d = xy - l.xy;
        float reach = unit * 22.0 * s.y;
        if (abs(d.x) > reach || abs(d.y) > reach) continue;
        // A contact shadow grows under a leaf as it settles on the floor.
        float2 sq = (d - float2(0.0, 2.0 * unit)) / (unit * s.y * float2(16.0, 3.0));
        float shadow = exp(-dot(sq, sq) * 1.6) * .38 * s.z * s.x;
        light *= 1.0 - shadow; alpha = alpha * (1.0 - shadow) + shadow;
        float cs = cos(l.z); float sn = sin(l.z);
        float2 local = float2(d.x * cs + d.y * sn, -d.x * sn + d.y * cs) / (unit * s.y);
        // Rolling on its long axis foreshortens the leaf and turns its paler underside to the view.
        float face = cos(l.w);
        float width = max(abs(face), .12);
        float along = local.x / 17.0;
        float halfWidth = 6.6 * width * max(0.0, 1.0 - along * along) * (1.0 + .16 * along);
        float body = (1.0 - smoothstep(halfWidth - .7, halfWidth + .3, abs(local.y))) * step(abs(along), 1.0);
        if (body <= 0.0) continue;
        float vein = (1.0 - smoothstep(.1, .5, abs(local.y) / max(width, .35))) * step(abs(along), .9);
        float3 top = mix(float3(.22, .50, .16), float3(.52, .80, .30), clamp(.5 + local.y / (halfWidth + .01) * .45, 0.0, 1.0));
        float3 under = mix(float3(.50, .68, .30), float3(.70, .84, .44), clamp(.5 - local.y / (halfWidth + .01) * .45, 0.0, 1.0));
        float3 green = mix(under, top, step(0.0, face)) * (.62 + .38 * abs(face)) + float3(.10, .14, .05) * vein;
        // Leaves behind the creature sit deeper in the habitat's shade.
        if (wanted < .5) green *= .74;
        float a = body * s.x;
        light = light * (1.0 - a) + green * a;
        alpha = alpha * (1.0 - a) + a;
    }
    return float4(light, alpha);
}

inline float4 grassMove(constant HabitatSCSignatureUniforms& u, float2 xy, float t) {
    float unit = u.b.z;
    float secs = u.a.z;
    float2 anchorA = u.anchor.xy;
    float2 emitA = u.emit.xy;
    float3 light = float3(0.0);
    float alpha = 0.0;
    // The bulb's own glow is light on the body (the creature shader's glowAt), never a haze over it.
    // Solar Beam: the bulb gathers sunlight, then fires one golden column straight up.
    float beam = window(t, .30, .36, .56, .74);
    if (beam > 0.0) {
        float2 q = xy - anchorA;
        float up = -q.y;
        float flick = 1.0 + .10 * sin(t * 160.0) + .06 * sin(t * 97.0 + q.y * .08);
        float w = unit * (11.0 + 8.0 * beam) * flick * (1.0 + max(up, 0.0) / (320.0 * unit));
        float side = gauss(q.x / w);
        float coreW = gauss(q.x / (w * .32));
        float along = smoothstep(-6.0 * unit, 4.0 * unit, up);
        float swirl = .5 + .5 * sin(up * .08 / unit - t * 60.0 + q.x * .3 / unit);
        float halo = gauss(q.x / (w * 2.6));
        light += (float3(1.0, .90, .50) * side * .75 + float3(1.0, 1.0, .92) * coreW * .9 + float3(.75, 1.0, .45) * side * swirl * .25 + float3(.9, 1.0, .5) * halo * .22) * along * beam;
        alpha += (side * .65 + coreW * .3 + halo * .18) * along * beam;
    }
    // The charge before it fires: light pulled into the bulb tip.
    float charge = window(t, .12, .30, .30, .38);
    if (charge > 0.0) {
        for (int i = 0; i < 10; i++) {
            float id = float(i);
            float a = id * .628 + t * 9.0;
            float r = (70.0 * unit) * (1.0 - smoothstep(.12, .34, t)) + 6.0 * unit;
            float2 p = anchorA + float2(cos(a), sin(a) * .6) * r;
            float d2 = dot2(xy - p);
            float s = exp(-d2 / (unit * unit * 6.0)) * charge;
            light += float3(1.0, .95, .6) * s; alpha += s * .8;
        }
    }
    // Pollen: light motes thrown out with the beam, slowed by the air, drifting on the breeze.
    for (int i = 0; i < 40; i++) {
        if (u.d.z < .5) break;
        float id = float(i);
        float te = .36 + .30 * hash(id + 11.0);
        float age = secs - te * MOVE;
        if (age <= 0.0 || age > 1.45) continue;
        float angle = -1.5708 + (hash(id + 12.0) - .5) * 2.5;
        float speed = (120.0 + 220.0 * hash(id + 13.0)) * unit;
        float travel = speed * (1.0 - exp(-age * 2.6)) / 2.6;
        float2 pos = emitA + float2(cos(angle), sin(angle)) * travel + float2(age * 26.0 + sin(age * 5.0 + id) * 5.0, age * age * 10.0) * unit;
        float radius = (1.3 + 1.8 * hash(id + 14.0)) * unit;
        float d2 = dot2(xy - pos);
        if (d2 > radius * radius * 30.0) continue;
        float life = smoothstep(0.0, .08, age) * (1.0 - smoothstep(1.05, 1.45, age));
        float twinkle = .7 + .3 * sin(age * 18.0 + id * 2.3);
        float mote = (exp(-d2 / (radius * radius)) + exp(-d2 / (radius * radius * 7.0)) * .25) * life * twinkle;
        light += float3(1.0, .95, .62) * mote; alpha += mote * .8;
    }
    float4 sum = float4(light, clamp(alpha, 0.0, 1.0));
    float4 front = leavesMove(u, xy, 1.0);
    return front + sum * (1.0 - front.a);
}

}  // namespace HabitatSC

fragment float4 habitatSCSignature(HabitatSCRegionOut in [[stage_in]],
                                    constant HabitatSCRegion& region [[buffer(0)]],
                                    constant HabitatSCSignatureUniforms& u [[buffer(1)]]) {
    using namespace HabitatSC;
    float2 xy = (in.position.xy / region.view.z) - region.rect.xy;
    float secs = u.a.z;
    float element = u.a.w;
    float layer = u.b.x;
    float unit = u.b.z;
    float fadeTop = u.b.w;
    float2 screen = u.c.xy;
    float front = u.c.z;
    float phase = u.c.w;
    float role = u.d.x;
    float incoming = u.d.y;
    if (secs <= 0.0 || secs >= FX_S || role < -.5) return float4(0.0);
    if (layer < .5 && element < 1.5) return float4(0.0);
    // Only on the performer's side of a habitat front (the same seam that wipes the specimen).
    float y = xy.y / screen.y;
    float k = sin(3.14159265 * front);
    float seam = seamLine(y, front, phase);
    if (incoming > 1.5) seam -= .011 * abs(sin(y * 46.0 + phase)) * k;
    float side = smoothstep(-1.0, 1.0, xy.x - seam * screen.x);
    float keep = role > .5 ? side : 1.0 - side;
    if (keep < .001) return float4(0.0);
    float t = secs / MOVE;
    float4 c = layer < .5 ? leavesMove(u, xy, 0.0) : element < .5 ? fireMove(u, xy, t) : element < 1.5 ? waterMove(u, xy, t) : grassMove(u, xy, t);
    return c * keep * smoothstep(fadeTop * .35, fadeTop + 40.0 * unit, xy.y) * (1.0 - smoothstep(FX_S - 0.5, FX_S, secs));
}

// MARK: - Normal bake

inline float habitatSCAlpha(texture2d<float, access::read> t, int w, int h, int2 q) {
    if (q.x < 0 || q.y < 0 || q.x >= w || q.y >= h) return 0.0;
    return t.read(uint2(q)).a;
}

// NORMAL_BAKE: the slope of two blurs of the art's alpha, as an RGBA normal map (depth 7).
kernel void habitatSCNormalBake(texture2d<float, access::read> wide [[texture(0)]],
                                texture2d<float, access::read> near [[texture(1)]],
                                texture2d<float, access::read> art [[texture(2)]],
                                texture2d<float, access::write> result [[texture(3)]],
                                uint2 gid [[thread_position_in_grid]]) {
    int w = int(art.get_width());
    int h = int(art.get_height());
    if (int(gid.x) >= w || int(gid.y) >= h) return;
    int2 p = int2(gid);
    if (art.read(gid).a < .01) { result.write(float4(.5, .5, 1.0, 1.0), gid); return; }
    float hx = (habitatSCAlpha(wide, w, h, p + int2(2, 0)) - habitatSCAlpha(wide, w, h, p - int2(2, 0))) * .65 + (habitatSCAlpha(near, w, h, p + int2(2, 0)) - habitatSCAlpha(near, w, h, p - int2(2, 0))) * .35;
    float hy = (habitatSCAlpha(wide, w, h, p + int2(0, 2)) - habitatSCAlpha(wide, w, h, p - int2(0, 2))) * .65 + (habitatSCAlpha(near, w, h, p + int2(0, 2)) - habitatSCAlpha(near, w, h, p - int2(0, 2))) * .35;
    float3 n = normalize(float3(-hx * 7.0, -hy * 7.0, 1.0));
    result.write(float4(n * .5 + .5, 1.0), gid);
}
