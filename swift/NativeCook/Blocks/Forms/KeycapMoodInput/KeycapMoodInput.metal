#include <metal_stdlib>
using namespace metal;

struct KeycapMoodInputVertexOut {
    float4 position [[position]];
    float2 uv;
};

vertex KeycapMoodInputVertexOut keycapMoodInputVertex(uint id [[vertex_id]]) {
    float2 corner = float2(id == 1 ? 3 : -1, id == 2 ? 3 : -1);
    return {float4(corner, 0, 1), float2((corner.x + 1) * 0.5, (1 - corner.y) * 0.5)};
}

struct KeycapMoodInputCap {
    float3 rgb;
    float alpha;
};

static float keycapMoodInputUnit(float value) { return clamp(value, 0.0, 1.0); }
static float3 keycapMoodInputMood(int mood) {
    switch (mood) {
        case 1: return float3(1, 0.078187, 0.366253);
        case 2: return float3(1, 0.445201, 0.013702);
        case 3: return float3(0.028426, 0.791298, 0.644480);
        case 4: return float3(0.270498, 0.147027, 1);
        case 5: return float3(1, 0.068478, 0.027321);
        default: return float3(0.467784, 1, 0.043735);
    }
}

static float keycapMoodInputChannel(texture2d<float> data, sampler sampleLinear, float2 q, float tileSize, int tile) {
    if (q.x < 0 || q.y < 0 || q.x >= tileSize || q.y >= tileSize) { return 0; }
    q = clamp(q, float2(0.5), float2(tileSize - 0.5));
    return data.sample(sampleLinear, q + float2(tile * tileSize, 0)).r;
}

static float keycapMoodInputBit(constant float *u, int key, int choiceSide, float2 position, float pitch) {
    int columns = key == 3 ? 12 : 24;
    float2 cell = floor(position / pitch + float2(columns / 2.0, 5));
    int x = int(cell.x);
    int y = int(cell.y);
    if (x < 0 || x >= columns || y < 0 || y >= 10) { return 0; }
    int bits = int(u[128 + key * 20 + choiceSide * 10 + y]);
    return float((bits >> x) & 1);
}

static float keycapMoodInputLegend(constant float *u, int key, float2 local, float scale, int miniIndex) {
    bool mini = miniIndex >= 0;
    float pitch = key == 3 ? 16.0 / 3.0 : 13.0 / 3.0;
    if (mini) { pitch = 2.0; }
    float sizeRatio = mini ? 52.0 / 134.0 : 1.0;
    float2 face = float2(-7.386787, -9.403995) * sizeRatio;
    float2 q = local - face;
    if (mini) {
        int columns = 12;
        float2 cell = floor(q / pitch + float2(columns / 2.0, 5));
        int x = int(cell.x); int y = int(cell.y);
        if (x < 0 || x >= columns || y < 0 || y >= 10) { return 0; }
        int order[6] = {5, 2, 0, 3, 4, 1};
        int mood = order[miniIndex];
        int bits = int(u[208 + mood * 10 + y]);
        return float((bits >> x) & 1);
    }
    float before = keycapMoodInputBit(u, key, 0, q, pitch);
    float after = keycapMoodInputBit(u, key, 1, q, pitch);
    int base = 32 + key * 16;
    float age = u[base + 5];
    if (age < 0) { return after; }
    if (u[base + 6] > 1.5) { return mix(before, after, keycapMoodInputUnit(age / 140)); }
    float2 cell = floor(q / pitch + float2(key == 3 ? 6.0 : 12.0, 5.0));
    float2 dot = cell - float2(key == 3 ? 5.5 : 11.5, 4.5);
    float delay = key == 3 ? length(dot * pitch - float2(u[22], u[23])) * 0.9
        : (dot.y + 3.5) * 14.0;
    float t = keycapMoodInputUnit((age - delay) / 110.0);
    float height = before != after ? abs(cos(3.14159265 * t)) : 1.0;
    if (height < 0.12) { return 0; }
    if (abs(fract(q.y / pitch + 5.0) - 0.5) > height * 0.5) { return 0; }
    return before == after ? before : (t < 0.5 ? before : after);
}

static KeycapMoodInputCap keycapMoodInputCap(float2 xy, constant float *u,
    texture2d<float> opaque, texture2d<float> glassLight, texture2d<float> glass,
    texture2d<float> miniData, sampler sampleLinear, int key, int miniIndex) {
    bool mini = miniIndex >= 0;
    float unit = u[2];
    float sizeRatio = mini ? 52.0 / 134.0 : 1.0;
    float scale = unit;
    float2 centre;
    float travel;
    float opacity = 1;
    if (mini) {
        int base = 96 + miniIndex * 4;
        float out = u[base];
        float angle = (155.0 - 26.0 * miniIndex) * 3.14159265 / 180.0;
        float2 home = float2(u[0] * 0.5, u[3] + 3 * u[5] + u[4] * 0.5);
        float2 destination = home + u[5] * float2(cos(angle), sin(angle));
        float amount = u[6] > 0.5 ? 1 : out;
        centre = mix(home, destination, amount);
        scale = unit * (u[6] > 0.5 ? 1 : 0.6 + 0.4 * keycapMoodInputUnit(out));
        opacity = u[6] > 0.5 ? keycapMoodInputUnit(out) : keycapMoodInputUnit(out / 0.4);
        travel = u[base + 1];
    } else {
        centre = float2(u[0] * 0.5, u[3] + key * u[5] + u[4] * 0.5);
        travel = u[32 + key * 16];
    }
    float tile = mini ? 192 : 480;
    float2 press = float2(3.454838, 4.397497) * sizeRatio;
    float2 local = (xy - centre) / scale;
    float2 restQ = local * 3 + tile * 0.5;
    float2 q = restQ - press * travel * 3;
    bool isGlass = key == 1 && !mini;
    texture2d<float> data = mini ? miniData : (isGlass ? glassLight : opaque);
    float restA = keycapMoodInputChannel(data, sampleLinear, restQ, tile, isGlass ? 2 : 5);
    float movedA = keycapMoodInputChannel(data, sampleLinear, q, tile, isGlass ? 2 : 5);
    if (restA + movedA <= 0) { return {float3(0), 0}; }

    float3 tint = float3(0.82);
    float3 upper = float3(0);
    float3 lower = float3(0);
    float rimGain = 0;
    float innerGain = 0;
    float3 led = float3(0.05);
    float3 scene = float3(u[8], u[9], u[10]);
    if (key == 0) {
        upper = float3(0.0109601, 0.0251869, 1) * 0.9 + scene * u[33] * 1.6;
    } else if (key == 1) {
        upper = 0.25 + scene * u[49] * 1.4;
        lower = float3(0.0109601, 0.0251869, 1) * 0.25 + scene * u[49] * 1.4;
        led = float3(0.0109601, 0.0251869, 1);
    } else if (key == 2) {
        upper = scene * (1 + u[65] * 1.6);
    } else {
        int order[6] = {5, 2, 0, 3, 4, 1};
        float3 from = mini ? keycapMoodInputMood(order[miniIndex]) : float3(u[15], u[16], u[17]);
        float3 to = mini ? from : float3(u[18], u[19], u[20]);
        float blend = mini ? 1 : u[21];
        float leading = 0;
        if (!mini && u[24] > 0.5 && blend > 0 && blend < 1) {
            float reach = length(abs(float2(u[22], u[23])) + float2(tile / 3 / 2));
            float radius = blend * (reach + 6);
            float delta = length(local - press * travel - float2(u[22], u[23])) - radius;
            blend = 1 - smoothstep(-3.0, 3.0, delta);
            leading = (1 - smoothstep(0.0, 6.0, abs(delta))) * 0.5 * (1 - u[21]);
        }
        tint = mix(from, to, blend);
        upper = leading;
        rimGain = mini ? 0.34 : 1 + u[13];
        innerGain = mini ? 0.45 : 0.45 * u[11] - 0.12 * u[13];
        led = mini ? from : mix(from, to, u[21]);
    }

    float3 rgb;
    if (isGlass) {
        float3 photo = glass.sample(sampleLinear, clamp(q, float2(0.5), float2(tile - 0.5))).rgb;
        rgb = photo + keycapMoodInputChannel(data, sampleLinear, q, tile, 0) * upper
            + keycapMoodInputChannel(data, sampleLinear, q, tile, 1) * lower;
    } else {
        rgb = tint * (keycapMoodInputChannel(data, sampleLinear, q, tile, 0)
            + keycapMoodInputChannel(data, sampleLinear, q, tile, 2) * upper
            + keycapMoodInputChannel(data, sampleLinear, q, tile, 3) * tint * rimGain
            + keycapMoodInputChannel(data, sampleLinear, q, tile, 4) * tint * innerGain)
            + keycapMoodInputChannel(data, sampleLinear, q, tile, 1) * 0.9;
    }
    float edge = clamp((keycapMoodInputChannel(data, sampleLinear, restQ - normalize(press) * 3 * 3, tile, isGlass ? 3 : 6)
        - keycapMoodInputChannel(data, sampleLinear, restQ, tile, isGlass ? 3 : 6)) * 8, 0.0, 1.0);
    rgb *= 1 - edge * 0.42 * max(0.0, travel);
    float capA = movedA * (travel > 0 ? restA : 1);
    float gap = travel > 0 ? restA * (1 - movedA) : 0;
    float3 interior = 0.0015 + led * 0.075 * max(0.0, travel);
    rgb = rgb * capA + interior * gap;
    if (mini) {
        float dots = keycapMoodInputLegend(u, 3, local - press * travel, scale, miniIndex);
        rgb = mix(rgb, float3(0.0033465), dots * capA);
    } else {
        if (key == 1) {
            float2 shifted = local - press * travel - float2(1.5, 2.5);
            float shadow = keycapMoodInputLegend(u, key, shifted, scale, -1);
            shadow += keycapMoodInputLegend(u, key, shifted + float2(2, 0), scale, -1);
            shadow += keycapMoodInputLegend(u, key, shifted - float2(2, 0), scale, -1);
            shadow += keycapMoodInputLegend(u, key, shifted + float2(0, 2), scale, -1);
            shadow += keycapMoodInputLegend(u, key, shifted - float2(0, 2), scale, -1);
            rgb = mix(rgb, float3(0.00182, 0.00518, 0.0423), shadow * 0.09 * capA);
        } else {
            float deboss = keycapMoodInputLegend(u, key, local - press * travel - float2(-0.5, 0.67), scale, -1);
            rgb = mix(rgb, float3(1), deboss * 0.32 * capA);
        }
        float dots = keycapMoodInputLegend(u, key, local - press * travel, scale, -1);
        float3 ink = key == 1 ? float3(1) : key == 3
            ? float3(0.0033465, 0.0033465, 0.004777) : float3(0.055, 0.055, 0.06);
        if (key < 3) {
            float rim = u[32 + key * 16 + 1];
            ink = mix(ink, scene * 1.15, keycapMoodInputUnit(rim));
        }
        rgb = mix(rgb, ink, dots * capA);
    }
    float dim = key == 3 ? 1 : u[14];
    return {rgb * opacity * dim, (capA + gap) * opacity};
}

static float3 keycapMoodInputOver(float3 below, KeycapMoodInputCap above) {
    return above.rgb + below * (1 - above.alpha);
}

fragment float4 keycapMoodInputFragment(KeycapMoodInputVertexOut input [[stage_in]],
    constant float *u [[buffer(0)]], texture2d<float> opaque [[texture(0)]],
    texture2d<float> glassLight [[texture(1)]], texture2d<float> glass [[texture(2)]],
    texture2d<float> miniData [[texture(3)]], texture2d<float> well [[texture(4)]]) {
    constexpr sampler sampleLinear(coord::pixel, address::clamp_to_edge, filter::linear);
    float2 xy = input.uv * float2(u[0], u[1]);
    if (xy.y < 110 * u[2]) { return float4(0, 0, 0, 1); }
    float2 hero = float2(u[0] * 0.5, u[3] + 3 * u[5] + u[4] * 0.5);
    float d = length(xy - hero) / u[2];
    float f = d / 300;
    float glow = f < 0.45 ? mix(0.16 * u[11], 0.05, f / 0.45)
        : mix(0.05, 0.0, keycapMoodInputUnit((f - 0.45) / 0.55));
    float3 scene = float3(u[8], u[9], u[10]);
    float3 rgb = scene * max(0.0, glow) * 0.12;
    if (u[12] > 0) {
        float halo = 1 - smoothstep(0.0, 30.0, abs(d - u[12] * 520));
        rgb += scene * halo * 0.22 * pow(max(0.0, 1 - u[12]), 1.5);
    }
    for (int i = 0; i < 4; i++) {
        float2 centre = float2(u[0] * 0.5, u[3] + i * u[5] + u[4] * 0.5);
        float2 q = (xy - centre) / u[2] * 3 + 360;
        if (all(q >= 0) && all(q < 720)) {
            float border = min(min(q.x, q.y), min(720 - q.x, 720 - q.y));
            float feather = smoothstep(0.0, 42.0, border);
            float3 color = i == 3 ? mix(float3(u[15], u[16], u[17]), float3(u[18], u[19], u[20]), u[21])
                : i == 1 ? float3(0.0109601, 0.0251869, 1) * 0.45 : 0.05 + scene * u[32 + i * 16 + 1];
            float lip = i == 3 ? 0.14 + 0.3 * u[13] + 0.25 * (u[11] - 1) : i == 1 ? 0.08 : 0.01;
            float bloom = i == 3 ? 0.09 * u[11] : i == 1 ? 0.04 : 0.00025;
            rgb += feather * color * (well.sample(sampleLinear, q).r * lip
                + well.sample(sampleLinear, q + float2(720, 0)).r * bloom);
        }
    }
    int order[6] = {5, 2, 0, 3, 4, 1};
    for (int i = 0; i < 6; i++) {
        float out = u[96 + i * 4];
        float angle = (155.0 - 26.0 * i) * 3.14159265 / 180.0;
        float2 destination = hero + u[5] * float2(cos(angle), sin(angle));
        float2 centre = mix(hero, destination, u[6] > 0.5 ? 1.0 : out);
        float scale = u[2] * (u[6] > 0.5 ? 1.0 : 0.6 + 0.4 * keycapMoodInputUnit(out));
        float opacity = u[6] > 0.5 ? keycapMoodInputUnit(out) : keycapMoodInputUnit(out / 0.4);
        float md = length(xy - centre) / max(0.001, scale * 42);
        float mg = exp(-md * md * 4) * opacity * (int(u[25]) == order[i] ? 0.065 : 0.025);
        rgb += keycapMoodInputMood(order[i]) * mg;
        if (opacity > 0 && all(abs(xy - centre) < 34 * scale)) {
            rgb = keycapMoodInputOver(rgb, keycapMoodInputCap(xy, u, opaque, glassLight, glass, miniData, sampleLinear, 3, i));
        }
    }
    for (int i = 0; i < 4; i++) {
        float2 centre = float2(u[0] * 0.5, u[3] + i * u[5] + u[4] * 0.5);
        if (all(abs(xy - centre) < 84 * u[2])) {
            rgb = keycapMoodInputOver(rgb, keycapMoodInputCap(xy, u, opaque, glassLight, glass, miniData, sampleLinear, i, -1));
        }
    }
    rgb *= smoothstep(110 * u[2], 128 * u[2], xy.y) * u[7];
    return float4(max(rgb, 0.0), 1);
}
