#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float cityEventHeroSquare(float x) { return x * x; }
static float cityEventHeroDescSmooth(float high, float low, float value) {
    return 1.0 - smoothstep(low, high, value);
}
static float cityEventHeroHash21(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}
static float cityEventHeroValueNoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    return mix(
        mix(cityEventHeroHash21(i), cityEventHeroHash21(i + float2(1.0, 0.0)), u.x),
        mix(cityEventHeroHash21(i + float2(0.0, 1.0)), cityEventHeroHash21(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}
// Atlas tiles: origin x/y and original decoded width/height. Keeping every source at
// its native dimensions preserves the canonical sampler scale, including the half-size maps.
constant float2 cityEventHeroAtlasSize = float2(1701.0, 4162.0);
constant float4 cityEventHeroFillTile = float4(850.0, 1850.0, 425.0, 925.0);
constant float4 cityEventHeroTowerTile = float4(0.0, 0.0, 850.0, 1850.0);
constant float4 cityEventHeroNearTile = float4(0.0, 1850.0, 850.0, 1850.0);
constant float4 cityEventHeroBackDepthTile = float4(1275.0, 1850.0, 425.0, 925.0);
constant float4 cityEventHeroDepthTile = float4(850.0, 2775.0, 425.0, 925.0);
constant float4 cityEventHeroLightsTile = float4(850.0, 0.0, 850.0, 1850.0);
constant float4 cityEventHeroKeyTile = float4(1275.0, 2775.0, 425.0, 925.0);
constant float4 cityEventHeroBloomTile = float4(0.0, 3700.0, 212.0, 462.0);
constant float4 cityEventHeroBloomKeyTile = float4(212.0, 3700.0, 212.0, 462.0);
constant float4 cityEventHeroFacadeTile = float4(1700.0, 0.0, 1.0, 1850.0);
constant float4 cityEventHeroGlyphsTile = float4(424.0, 3700.0, 110.0, 7.0);
constant float4 cityEventHeroViewerTile = float4(534.0, 3700.0, 180.0, 180.0);

static float4 cityEventHeroSampleAtlasNormalized(
    texture2d<half> atlas,
    sampler imageSampler,
    float2 normalizedPoint,
    float4 tile,
    bool decal = false
) {
    if (decal && (any(normalizedPoint < 0.0) || any(normalizedPoint > 1.0))) return float4(0.0);
    float2 atlasPoint = tile.xy + clamp(normalizedPoint * tile.zw, float2(0.5), tile.zw - 0.5);
    return float4(atlas.sample(imageSampler, atlasPoint / cityEventHeroAtlasSize));
}
static float4 cityEventHeroSampleAtlas(
    texture2d<half> atlas,
    sampler imageSampler,
    float2 photoPixel,
    float4 tile,
    bool decal = false
) {
    return cityEventHeroSampleAtlasNormalized(atlas, imageSampler, photoPixel / float2(850.0, 1850.0), tile, decal);
}
static float cityEventHeroGate(float key, float front) {
    return smoothstep(key - 0.035, key, front) * step(0.01, key);
}
static float2 cityEventHeroLayerUV(float2 p, float parallax, float3 cam) {
    float zoom = cam.z * 0.1 + abs(cam.x) / 400.0 + abs(cam.y) / 600.0;
    float2 focus = float2(428.0, 650.0);
    return focus + (p - focus - cam.xy * parallax) / (1.0 + zoom * parallax);
}

static float cityEventHeroWord0(float k) {
    if (k < 0.5) return 18.0; if (k < 1.5) return 12.0; return 12.0;
}
static float cityEventHeroWord1(float k) {
    if (k < 0.5) return 21.0; if (k < 1.5) return 16.0; return 20.0;
}
static float cityEventHeroWord2(float k) {
    if (k < 0.5) return 19.0; if (k < 1.5) return 13.0; if (k < 2.5) return 12.0;
    if (k < 3.5) return 17.0; return 12.0;
}
static float cityEventHeroDateTail(float k) {
    if (k < 0.5) return 0.0; if (k < 1.5) return 0.0; if (k < 2.5) return 14.0;
    if (k < 3.5) return 11.0; return 15.0;
}
static float cityEventHeroPageGlyph(float page, float k, float day) {
    if (page < 0.5) return cityEventHeroWord0(k);
    if (page < 1.5) return cityEventHeroWord1(k);
    if (page < 2.5) return cityEventHeroWord2(k);
    float tens = floor(day / 10.0);
    if (k < 0.5) return 1.0 + tens;
    if (k < 1.5) return 1.0 + day - 10.0 * tens;
    return cityEventHeroDateTail(k);
}

static float3 cityEventHeroLEDContent(
    float column,
    float row,
    float4 ledA,
    float4 ledB,
    float4 ledC,
    texture2d<half> atlas,
    sampler imageSampler
) {
    float band = (column + 0.5) / 29.0;
    if (row > ledA.y) return float3(0.0);
    float wave = 0.5 + 0.5 * sin(6.2832 * (row - ledC.z) / 120.0 + 2.2 * band);
    float3 color = mix(float3(0.02, 0.62, 0.72), float3(0.22, 0.14, 0.62), wave)
        * 0.3 * (0.8 + 0.4 * cityEventHeroHash21(float2(column, row)));
    color += float3(0.82, 0.96, 1.0) * exp(-cityEventHeroSquare((ledA.y - row) / 6.0)) * step(ledA.y, 9999.0);
    if (ledA.z > -0.5) {
        float page = ledA.z;
        float date = step(2.5, page);
        float count = date > 0.5 ? 5.0 : (page < 0.5 ? 3.0 : (page < 1.5 ? 3.0 : 5.0));
        float height = count * 40.0 - 5.0 + date * 8.0;
        float yy = height - 1.0 - (row - ledA.w);
        if (date > 0.5 && yy >= 80.0) yy -= 8.0;
        float letter = floor(yy / 40.0);
        float inner = yy - letter * 40.0;
        float glyphColumn = floor((column - 7.0) / 3.0);
        if (yy >= 0.0 && letter < count && inner < 35.0 && glyphColumn >= 0.0 && glyphColumn < 5.0) {
            float glyph = cityEventHeroPageGlyph(page, letter, ledB.y);
            float2 glyphUV = float2(glyph * 5.0 + glyphColumn + 0.5, floor(inner / 5.0) + 0.5) / float2(110.0, 7.0);
            float bit = cityEventHeroSampleAtlasNormalized(atlas, imageSampler, glyphUV, cityEventHeroGlyphsTile).a;
            float age = (ledB.x - letter) * 0.04;
            float lit = step(0.0, age);
            float3 textColor = mix(float3(1.0, 0.98, 0.94), float3(1.0, 0.84, 0.52), smoothstep(0.0, 0.18, age));
            color = mix(color, textColor * (1.0 + 0.8 * exp(-max(age, 0.0) / 0.08)), bit * lit);
        }
    }
    if (ledC.x > 0.5 && ledB.w > 0.3) {
        float y = 1320.0 - (row + 0.5) * 3.0;
        float halfWidth = cityEventHeroSampleAtlas(atlas, imageSampler, float2(427.5, y), cityEventHeroFacadeTile).a * 64.0;
        float x = ((column + 0.5) / 29.0 - 0.5) * M_PI_F * halfWidth;
        float2 d = float2(x, y - ledB.z) / ledB.w;
        float radius = length(d);
        if (radius < 1.0) {
            float3 face = cityEventHeroSampleAtlasNormalized(atlas, imageSampler, d * 0.5 + 0.5, cityEventHeroViewerTile).rgb;
            color = mix(face * 1.2, float3(1.0, 0.78, 0.36) * 1.2, smoothstep(0.8, 0.88, radius));
        }
    }
    return color;
}

[[ stitchable ]] half4 cityEventHeroScene(
    float2 xy,
    SwiftUI::Layer photo,
    texture2d<half> atlas,
    float mode,
    float night,
    float photoScale,
    float3 cam,
    float4 sun,
    float3 f0,
    float3 f1,
    float3 f2,
    float3 c0,
    float3 c1,
    float3 c2,
    float3 smoke,
    float3 flare,
    float4 ledA,
    float4 ledB,
    float4 ledC,
    float4 ledD,
    float drift
) {
    constexpr sampler imageSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    float2 p = xy / photoScale;
    float moving = step(0.001, abs(cam.x) + abs(cam.y) + cam.z);
    float2 uvBack = p;
    float2 uvTower = p;
    float2 uvNear = p;
    float3 color = float3(0.0);
    float towerAlpha = 0.0;
    float nearAlpha = 0.0;
    if (moving > 0.5) {
        float backParallax = 0.08 + 1.664 * cityEventHeroSampleAtlas(atlas, imageSampler, p, cityEventHeroBackDepthTile).a;
        uvBack = cityEventHeroLayerUV(p, backParallax, cam);
        uvTower = cityEventHeroLayerUV(p, 0.5, cam);
        uvNear = cityEventHeroLayerUV(p, 0.78, cam);
        float hole = smoothstep(0.0, 0.35, max(
            cityEventHeroSampleAtlas(atlas, imageSampler, uvBack, cityEventHeroTowerTile, true).a,
            cityEventHeroSampleAtlas(atlas, imageSampler, uvBack, cityEventHeroNearTile, true).a
        ));
        float2 backPoint = clamp(uvBack, float2(0.0), float2(850.0, 1850.0));
        color = mix(
            float3(photo.sample(backPoint * photoScale).rgb),
            cityEventHeroSampleAtlas(atlas, imageSampler, uvBack, cityEventHeroFillTile).rgb,
            hole
        );
        towerAlpha = cityEventHeroSampleAtlas(atlas, imageSampler, uvTower, cityEventHeroTowerTile, true).a;
        nearAlpha = cityEventHeroSampleAtlas(atlas, imageSampler, uvNear, cityEventHeroNearTile, true).a;
        if (towerAlpha > 0.0) color = mix(color, float3(photo.sample(uvTower * photoScale).rgb), towerAlpha);
        if (nearAlpha > 0.0) color = mix(color, float3(photo.sample(uvNear * photoScale).rgb), nearAlpha);
    } else {
        color = float3(photo.sample(xy).rgb);
        towerAlpha = cityEventHeroSampleAtlas(atlas, imageSampler, p, cityEventHeroTowerTile, true).a;
        nearAlpha = cityEventHeroSampleAtlas(atlas, imageSampler, p, cityEventHeroNearTile, true).a;
    }
    float layerAlpha = max(towerAlpha, nearAlpha);
    if (mode > 0.5 && layerAlpha < 0.002) return half4(0.0);
    float2 uv = nearAlpha > 0.5 ? uvNear : (towerAlpha > 0.5 ? uvTower : uvBack);
    float luminance = dot(color, float3(0.299, 0.587, 0.114));
    float key = cityEventHeroSampleAtlas(atlas, imageSampler, uv, cityEventHeroKeyTile).a;
    float object = max(smoothstep(0.0, 0.04, key), max(towerAlpha, nearAlpha));

    if (sun.z > 0.0) {
        float3 lightDirection = float3(sin(sun.x) * cos(sun.y), sin(sun.y), cos(sun.x) * cos(sun.y));
        float3 sunlight = mix(float3(1.0, 0.52, 0.24), float3(1.0, 0.93, 0.8), smoothstep(0.05, 0.6, sun.y));
        float3 ray = normalize(float3((uv.x - 425.0) / 1000.0, -(uv.y - 930.0) / 1000.0, 1.0));
        float ca = dot(ray, lightDirection);
        float glow = 0.9 * exp((ca - 1.0) / 0.05) + 0.35 * exp((ca - 1.0) / 0.45);
        float low = 1.0 - smoothstep(0.0, 0.45, sun.y);
        float sunlit = 1.0;
        if (uvBack.y > 932.0 && uvBack.y < 1320.0 && nearAlpha < 0.5 && towerAlpha < 0.5) {
            constexpr float zBase = 2.5641025641;
            constexpr float xBase = 0.0076923077;
            constexpr float towerHeight = 2.6333333333;
            constexpr float towerWidth = 0.1179487179;
            float z = 1000.0 / (uvBack.y - 930.0);
            float2 relative = float2((uvBack.x - 425.0) * z / 1000.0 - xBase, z - zBase);
            float2 direction = float2(-sin(sun.x), -cos(sun.x));
            float lengthOfShadow = towerHeight / tan(max(sun.y, 0.03));
            float along = dot(relative, direction);
            float across = abs(relative.x * direction.y - relative.y * direction.x);
            float width = towerWidth * clamp(1.0 - along / lengthOfShadow, 0.0, 1.0);
            float inside = smoothstep(0.0, 0.3, along)
                * cityEventHeroDescSmooth(width + 0.03 + 0.05 * along, width * 0.3, across)
                * cityEventHeroDescSmooth(lengthOfShadow, lengthOfShadow * 0.7, along);
            sunlit = 1.0 - inside;
        }
        float3 warmed = color * mix(float3(1.0), sunlight * 1.2, 0.55) * (1.0 + 0.22 * (0.5 + 0.5 * object));
        float3 shaded = color * (1.0 - 0.62 * 0.5) * float3(0.86, 0.92, 1.0);
        color = mix(color, mix(shaded, warmed, sunlit), sun.z);
        color += sunlight * sun.z * glow * (0.2 + smoothstep(0.3, 0.75, luminance)) * 0.8 * (1.0 - 0.7 * object);
        color += float3(1.0, 0.52, 0.24) * sun.z * low * 0.55
            * exp(-cityEventHeroSquare((uv.y - 890.0) / 170.0)) * (0.35 + luminance) * (1.0 - 0.6 * object);
        color += float3(1.0, 0.95, 0.85) * sun.z * smoothstep(0.99988, 0.99996, ca) * (1.0 - object);
        if (towerAlpha > 0.0) {
            float halfWidth = max(cityEventHeroSampleAtlas(atlas, imageSampler, float2(427.5, uvTower.y), cityEventHeroFacadeTile).a * 64.0, 0.5);
            float side = clamp((uvTower.x - 427.5) / halfWidth, -0.999, 0.999);
            float theta = asin(side);
            float3 normal = float3(sin(theta), 0.0, -cos(theta));
            float3 halfway = normalize(lightDirection + float3(0.0, 0.0, -1.0));
            float specular = pow(max(dot(normal, halfway), 0.0), 60.0);
            float diffuse = max(dot(normal, lightDirection), 0.0);
            color += sunlight * sun.z * towerAlpha * (specular * 1.5 + diffuse * 0.22);
        }
        float gx = cityEventHeroSampleAtlas(atlas, imageSampler, uv + float2(3.0, 0.0), cityEventHeroDepthTile).a
            - cityEventHeroSampleAtlas(atlas, imageSampler, uv - float2(3.0, 0.0), cityEventHeroDepthTile).a;
        float lightSide = lightDirection.x < 0.0 ? 1.0 : -1.0;
        color += sunlight * sun.z * clamp(gx * lightSide * 18.0, 0.0, 1.0)
            * abs(lightDirection.x) * 0.4 * (1.0 - towerAlpha);
    }

    float grade = smoothstep(0.0, 0.7, night);
    float3 output = color;
    if (night > 0.0) {
        float front = -0.05 + 1.1 * smoothstep(0.15, 1.0, night);
        float up = cityEventHeroDescSmooth(940.0, 250.0, uv.y);
        float3 sky = mix(float3(0.1, 0.11, 0.22), float3(0.025, 0.045, 0.11), up)
            * clamp(0.15 + 2.4 * (luminance - 0.22), 0.08, 1.6);
        sky += float3(0.34, 0.17, 0.09) * exp(-cityEventHeroSquare((uv.y - 930.0) / 150.0))
            * clamp(0.4 + 1.6 * (luminance - 0.25), 0.2, 1.2) * smoothstep(0.3, 1.0, night);
        float3 nightColor = mix(sky, color * float3(0.13, 0.16, 0.25), object);
        float3 afterglow = float3(0.22, 0.08, 0.12) * exp(-cityEventHeroSquare((uv.y - 920.0) / 160.0))
            * sin(M_PI_F * grade) * (1.0 - object);
        output = mix(color, nightColor, grade) + afterglow;
        float lights = cityEventHeroSampleAtlas(atlas, imageSampler, uv, cityEventHeroLightsTile, true).a;
        float gate = cityEventHeroGate(key, front);
        float edge = exp(-cityEventHeroSquare((front - key) / 0.03));
        float warm = step(cityEventHeroHash21(floor(uv / float2(5.0, 6.0))), 0.8);
        float3 windowColor = mix(float3(0.8, 0.88, 1.0), float3(1.0, 0.74, 0.42), warm);
        float3 windows = windowColor * lights * gate * (1.0 + 1.4 * edge) * 1.5;
        float3 bloom = float3(1.0, 0.62, 0.32) * cityEventHeroSampleAtlas(atlas, imageSampler, uv, cityEventHeroBloomTile, true).a
            * cityEventHeroGate(cityEventHeroSampleAtlas(atlas, imageSampler, uv, cityEventHeroBloomKeyTile, true).a, front) * 0.55;
        output = 1.0 - (1.0 - output) * (1.0 - clamp(windows + bloom, 0.0, 1.0));
    }

    if (ledA.x > 0.0) {
        float halfWidth = cityEventHeroSampleAtlas(atlas, imageSampler, float2(427.5, uvTower.y), cityEventHeroFacadeTile).a * 64.0;
        if (towerAlpha > 0.0 && halfWidth > 0.5) {
            float side = clamp((uvTower.x - 427.5) / halfWidth, -0.999, 0.999);
            float theta = asin(side);
            float yReference = uvTower.y + (930.0 - uvTower.y) * 0.035 * (1.0 - side * side);
            float floorLine = (1320.0 - yReference) / 3.0;
            float columnUnit = (theta / M_PI_F + 0.5) * 29.0;
            float column = floor(columnUnit);
            float row = floor(floorLine);
            float cellWidth = halfWidth * (M_PI_F / 29.0) * max(cos(theta), 0.05);
            float2 middle = abs(float2(fract(columnUnit) - 0.5, fract(floorLine) - 0.5)) * float2(cellWidth, 3.0);
            float pixel = 0.6 / max(1.0 + cam.z * 0.1, 1.0);
            float dotX = cityEventHeroDescSmooth(cellWidth * 0.4 + pixel, cellWidth * 0.4 - pixel, middle.x);
            float dotY = cityEventHeroDescSmooth(1.14 + pixel, 1.14 - pixel, middle.y);
            float slab = 1.0 - 0.35 * step(0.5, fract(row * 0.5));
            float dotValue = mix(dotX * dotY, 0.55, cityEventHeroDescSmooth(2.2, 1.0, cellWidth)) * slab;
            float3 emitted = cityEventHeroLEDContent(column, row, ledA, ledB, ledC, atlas, imageSampler);
            emitted *= step(ledD.x, cityEventHeroHash21(float2(column * 1.7, row)) * 0.999);
            float3 lit = emitted * (dotValue * 1.35 + 0.3) + float3(1.0, 0.98, 0.94) * ledC.y * 0.6 * dotValue;
            output = mix(output, output * 0.28, ledA.x * towerAlpha) + lit * ledA.x * towerAlpha;
        }
        float edgeDistance = max(abs(uvTower.x - 427.5) - max(halfWidth, 0.0), 0.0)
            + max(293.0 - uvTower.y, 0.0) * 2.0;
        float3 spillColor = mix(float3(0.5, 0.72, 0.85), float3(1.0, 0.78, 0.45), step(-0.5, ledA.z) * 0.7);
        float halo = (0.35 * exp(-edgeDistance / 8.0) + 0.65 * exp(-edgeDistance / 55.0)) * 0.55;
        float wash = exp(-edgeDistance / 170.0) * mix(0.35 + 0.9 * luminance, 0.5, object) * 0.35;
        output += spillColor * ledC.w * (halo + wash) * (1.0 - towerAlpha);
    }

    if (f0.z + f1.z + f2.z > 0.0) {
        float depth = cityEventHeroSampleAtlas(atlas, imageSampler, uv, cityEventHeroDepthTile).a * 5.2;
        float depthDistance = (depth - 1.3) * 70.0 / photoScale;
        float skyLight = clamp(2.2 * (luminance - 0.25), 0.0, 1.0) * 1.1 * (1.0 - 0.6 * object) + 0.05;
        float surface = mix(skyLight, 0.35 + 0.5 * luminance + 0.9 * towerAlpha, object);
        float radius2 = cityEventHeroSquare(260.0);
        output += c0 * f0.z * surface * exp(-(cityEventHeroSquare(p.x - f0.x) + cityEventHeroSquare(p.y - f0.y) + depthDistance * depthDistance) / radius2);
        output += c1 * f1.z * surface * exp(-(cityEventHeroSquare(p.x - f1.x) + cityEventHeroSquare(p.y - f1.y) + depthDistance * depthDistance) / radius2);
        output += c2 * f2.z * surface * exp(-(cityEventHeroSquare(p.x - f2.x) + cityEventHeroSquare(p.y - f2.y) + depthDistance * depthDistance) / radius2);
    }
    float flareDistance = cityEventHeroSquare(p.x - flare.x) + cityEventHeroSquare(p.y - flare.y);
    output += float3(1.0, 0.9, 0.72) * flare.z * (
        exp(-flareDistance / cityEventHeroSquare(9.0)) + 0.35 * exp(-flareDistance / cityEventHeroSquare(60.0))
    );
    if (smoke.x > 0.0 || smoke.y > 0.0 || smoke.z > 0.0) {
        for (int i = 0; i < 3; i++) {
            float time = i == 0 ? smoke.x : (i == 1 ? smoke.y : smoke.z);
            if (time > 0.0) {
                float2 center = i == 0 ? f0.xy : (i == 1 ? f1.xy : f2.xy);
                center.y -= 18.0 * time / photoScale;
                float radius = 70.0 * (0.45 + 0.55 * (1.0 - exp(-time / 0.6))) / photoScale;
                float distance2 = (cityEventHeroSquare(p.x - center.x) + cityEventHeroSquare(p.y - center.y)) / (radius * radius);
                if (distance2 < 2.5) {
                    float2 q = (p - center) * photoScale / 26.0 + float2(drift * 0.15 + float(i) * 5.3, time * 0.4);
                    float noise = 0.65 * cityEventHeroValueNoise(q) + 0.35 * cityEventHeroValueNoise(q * 2.3 + 7.3);
                    float density = smoothstep(0.35, 0.75, noise) * exp(-distance2)
                        * smoothstep(0.0, 0.25, time) * exp(-time / 1.1) * ledD.y;
                    float3 smokeLight = float3(0.3, 0.31, 0.37)
                        + (c0 * f0.z + c1 * f1.z + c2 * f2.z) * 0.35 + float3(0.34, 0.17, 0.09) * 0.3;
                    output = mix(output, smokeLight, density * 0.42 * (1.0 - towerAlpha * 0.5));
                }
            }
        }
    }

    float3 result = clamp(output, 0.0, 1.0);
    if (mode > 0.5) return half4(half3(result * layerAlpha), half(layerAlpha));
    return half4(half3(result), 1.0h);
}

static float cityEventHeroMistHash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
static float cityEventHeroMistNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(cityEventHeroMistHash(i), cityEventHeroMistHash(i + float2(1.0, 0.0)), u.x),
        mix(cityEventHeroMistHash(i + float2(0.0, 1.0)), cityEventHeroMistHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}
static float cityEventHeroMistFBM(float2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int index = 0; index < 4; index++) {
        value += amplitude * cityEventHeroMistNoise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

[[ stitchable ]] half4 cityEventHeroMist(
    float2 xy,
    half4 source,
    float unit,
    float drift,
    float amount
) {
    float2 reference = float2(xy.x / unit, xy.y / unit + 181.0);
    float2 p = float2(reference.x + drift, reference.y) / float2(560.0, 230.0);
    float noise = cityEventHeroMistFBM(p + float2(0.0, drift * 0.0004));
    float mist = smoothstep(0.46, 0.78, noise);
    float sky = cityEventHeroDescSmooth(1180.0, 820.0, reference.y) * smoothstep(200.0, 460.0, reference.y);
    float alpha = amount * mist * sky;
    return half4(half3(0.8, 0.9, 0.93) * half(alpha), half(alpha));
}
