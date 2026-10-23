/// The Metal source of the scene, compiled at run time. One vertex function carries every
/// draw; each material is a fragment function ported from the runtime shader of the same
/// name in the React Native block, evaluated at the mesh's interpolated texture coordinate
/// exactly as a Skia vertex mesh evaluates its shader.
enum BoosterPackShaders {
  static let source = """
    #include <metal_stdlib>
    using namespace metal;

    // Per draw. `m0`–`m2` are the rows of the homography from local points to scene points;
    // `view` holds the scene's size in points, the commit punch and the draw's opacity;
    // `target` is the rectangle of the scene the render target covers; `a`–`d` are the
    // material's own parameters.
    struct BoosterPackU {
      float4 m0, m1, m2;
      float4 view;
      float4 target;
      float4 a, b, c, d;
      float4 colors[8];
      float4 stops[2];
    };

    struct BoosterPackVIn {
      float2 position [[attribute(0)]];
      float2 tex [[attribute(1)]];
      float4 extra [[attribute(2)]];
    };

    struct BoosterPackV {
      float4 position [[position]];
      float2 local;
      float2 tex;
      float4 extra;
    };

    vertex BoosterPackV boosterPackVertex(BoosterPackVIn in [[stage_in]],
                                          constant BoosterPackU &u [[buffer(1)]]) {
      float3 p = float3(in.position, 1.0);
      float x = dot(u.m0.xyz, p);
      float y = dot(u.m1.xyz, p);
      float w = dot(u.m2.xyz, p);
      // The punch scales the scene about the middle of the view.
      float2 middle = u.view.xy * 0.5 * w;
      float2 s = middle + (float2(x, y) - middle) * u.view.z;
      BoosterPackV out;
      float2 n = (s - u.target.xy * w) / u.target.zw * 2.0;
      out.position = float4(n.x - w, w - n.y, 0.0, w);
      out.local = in.position;
      out.tex = in.tex;
      out.extra = in.extra;
      return out;
    }

    static float boosterPackSq(float x) { return x * x; }

    static float boosterPackErf(float x) {
      float s = x < 0.0 ? -1.0 : 1.0;
      float a = abs(x);
      float t = 1.0 / (1.0 + 0.3275911 * a);
      float y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
        + 0.254829592) * t * exp(-a * a);
      return s * y;
    }

    // Coverage at signed distance `d` inside an edge: a Gaussian-blurred half-plane, or one
    // pixel of antialiasing when there is no blur.
    static float boosterPackEdge(float d, float sigma, float pixel) {
      if (sigma < 0.05) return clamp(d / max(pixel, 0.0001) + 0.5, 0.0, 1.0);
      return 0.5 + 0.5 * boosterPackErf(d * 0.70710678 / sigma);
    }

    // Coverage of a blurred band of half-width `h` at distance `d` from its middle.
    static float boosterPackBand(float d, float h, float sigma, float pixel) {
      if (sigma < 0.05) return clamp((h - abs(d)) / max(pixel, 0.0001) + 0.5, 0.0, 1.0);
      float k = 0.70710678 / sigma;
      return 0.5 * (boosterPackErf((h - d) * k) + boosterPackErf((h + d) * k));
    }

    static float boosterPackRoundedBox(float2 p, float2 halfSize, float r) {
      float2 q = abs(p) - halfSize + r;
      return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    static float boosterPackShadowX(float x, float y, float sigma, float corner, float2 halfSize) {
      float delta = min(halfSize.y - corner - abs(y), 0.0);
      float curved = halfSize.x - corner + sqrt(max(0.0, corner * corner - delta * delta));
      float k = 0.70710678 / sigma;
      return 0.5 * (boosterPackErf((x + curved) * k) - boosterPackErf((x - curved) * k));
    }

    // A Gaussian-blurred rounded rectangle: exact across, sampled down.
    static float boosterPackShadow(float2 p, float2 halfSize, float corner, float sigma) {
      float low = p.y - halfSize.y;
      float high = p.y + halfSize.y;
      float start = clamp(-3.0 * sigma, low, high);
      float end = clamp(3.0 * sigma, low, high);
      float advance = (end - start) / 8.0;
      float y = start + advance * 0.5;
      float value = 0.0;
      for (int i = 0; i < 8; i++) {
        float g = exp(-(y * y) / (2.0 * sigma * sigma)) / (2.50662827 * sigma);
        value += boosterPackShadowX(p.x, p.y - y, sigma, corner, halfSize) * g * advance;
        y += advance;
      }
      return value;
    }

    static float4 boosterPackStops(constant BoosterPackU &u, int count, float t) {
      float4 color = u.colors[0];
      float previous = u.stops[0][0];
      for (int i = 1; i < count; i++) {
        float stop = u.stops[i / 4][i % 4];
        float f = clamp((t - previous) / max(stop - previous, 0.00001), 0.0, 1.0);
        color = mix(color, u.colors[i], f);
        previous = stop;
      }
      return color;
    }

    // Shapes and paints that need no mesh of their own.
    //   a: shape kind (0 geometry, 1 rounded rectangle, 2 ribbon), then per kind
    //      rounded rectangle: corner radius, blur sigma, stroke width (0 fills)
    //      ribbon: half-width, blur sigma, round caps
    //   b: the rounded rectangle
    //   c: paint kind (0 colour, 1 linear, 2 radial, 3 image, 4 two colours through a mask), stops
    //   d: gradient points, or centre and radius, or the image's rectangle
    fragment float4 boosterPackPaintFragment(BoosterPackV in [[stage_in]],
                                             constant BoosterPackU &u [[buffer(1)]],
                                             texture2d<float> image [[texture(0)]],
                                             sampler smp [[sampler(0)]]) {
      float coverage = 1.0;
      int shape = int(u.a.x);
      if (shape == 1) {
        float2 halfSize = u.b.zw * 0.5;
        float2 p = in.local - (u.b.xy + halfSize);
        float sigma = u.a.z;
        float d = boosterPackRoundedBox(p, halfSize, u.a.y);
        if (u.a.w > 0.0) {
          coverage = boosterPackBand(d, u.a.w * 0.5, sigma, fwidth(d));
        } else if (sigma < 0.05) {
          coverage = clamp(0.5 - d / max(fwidth(d), 0.0001), 0.0, 1.0);
        } else {
          coverage = boosterPackShadow(p, halfSize, u.a.y, sigma);
        }
      } else if (shape == 2) {
        float h = u.a.y;
        float sigma = u.a.z;
        float across = in.extra.x;
        float pixel = fwidth(across);
        if (u.a.w > 0.5) {
          float beyond = max(max(-in.extra.y, -in.extra.z), 0.0);
          coverage = boosterPackBand(length(float2(across, beyond)), h, sigma, pixel);
        } else {
          coverage = boosterPackBand(across, h, sigma, pixel)
            * boosterPackEdge(in.extra.y, sigma, pixel) * boosterPackEdge(in.extra.z, sigma, pixel);
        }
      }

      float4 color = u.colors[0];
      int paint = int(u.c.x);
      int count = int(u.c.y);
      if (paint == 1) {
        float2 axis = u.d.zw - u.d.xy;
        color = boosterPackStops(u, count, dot(in.local - u.d.xy, axis) / max(dot(axis, axis), 0.00001));
      } else if (paint == 2) {
        color = boosterPackStops(u, count, length(in.local - u.d.xy) / max(u.d.z, 0.00001));
      } else if (paint == 3) {
        float4 sampled = image.sample(smp, (in.tex - u.d.xy) / u.d.zw);
        return sampled * (coverage * u.view.w);
      } else if (paint == 4) {
        float mask = image.sample(smp, (in.tex - u.d.xy) / u.d.zw).r;
        color = mix(u.colors[0], u.colors[1], mask);
      }
      float alpha = color.a * coverage * u.view.w;
      return float4(color.rgb * alpha, alpha);
    }

    // FOLD: the attached strip while it is being torn.
    //   a: the pack rectangle   b: compression, bent span, swipe direction, sheen strength
    //   arrays 0–63: texture height, compression, first-row stretch, upper-edge slope
    //   arrays 64–127: distance from the crest at quarter, half and three-quarter rows
    fragment float4 boosterPackFoldFragment(BoosterPackV in [[stage_in]],
                                            constant BoosterPackU &u [[buffer(1)]],
                                            constant float4 *arrays [[buffer(2)]],
                                            texture2d<float> artwork [[texture(0)]],
                                            sampler smp [[sampler(0)]]) {
      float2 xy = in.tex;
      float4 packRect = u.a;
      float4 fold = u.b;
      float4 sampled = artwork.sample(smp, (xy - packRect.xy) / packRect.zw);
      float3 print = sampled.rgb / max(sampled.a, 0.001);
      float uu = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
      float column = acos(clamp(1.0 - 2.0 * uu, -1.0, 1.0)) / 3.14159265359 * 63.0;
      int index = clamp(int(floor(column)), 0, 62);
      float part = clamp(column - float(index), 0.0, 1.0);
      float4 local = mix(arrays[index], arrays[index + 1], part);
      float v = clamp((xy.y - packRect.y) / max(local.x, 0.001), 0.0, 1.0);
      float d = fold.z > 0.0 ? uu : 1.0 - uu;
      float bent = smoothstep(0.015, 0.17, fold.x);

      // The 2D projection loses the sheet's depth. Continue the bend's lighting
      // softly into its shoulder, as seen at f42–48, without moving the artwork.
      float shoulder = max(0.48, fold.y + 0.12);
      float across = 1.0 - smoothstep(0.0, shoulder, d);
      float upper = 1.0 - smoothstep(0.12, 0.92, v);
      float shade = bent * across * upper;
      float compression = max(local.y, fold.x * across * 0.48);
      float facing = clamp(1.0 - 1.45 * compression * pow(max(1.0 - v, 0.0), 0.45), 0.0, 1.0);

      // A photographed softbox is illumination, not printed ink. Remove
      // it only where the fold turns away; marks and the unbent panel retain color.
      float purpleInk = smoothstep(0.08, 0.28, print.b - print.g);
      float deglare = max(print.g - 0.035, 0.0) * purpleInk * shade * 0.95;
      float3 unlit = max(print - deglare * float3(1.05, 0.95, 0.35), print * float3(0.32, 0.08, 0.40));
      float diffuse = 1.0 - shade * (0.50 + 0.16 * (1.0 - facing));
      float3 lit = unlit * diffuse;

      // A broad satin reflection settles toward the foot of the bend as its
      // compression increases. It follows material UVs, so holding or reversing
      // a gesture holds or reverses the lighting too; there is no light timer.
      float sheenV = 0.60 + fold.x * 0.14 - d * 0.38;
      float sheenWidth = 0.20 - 0.065 * fold.x;
      float band = exp(-boosterPackSq((v - sheenV) / sheenWidth));
      float crestCenter = 0.13 + 0.12 * smoothstep(0.20, 0.65, fold.x);
      float crest = exp(-boosterPackSq((d - crestCenter) / 0.12));
      float settling = 1.0 - smoothstep(0.38, 0.73, fold.x) * 0.28;
      float exposure = mix(0.13, 0.52, smoothstep(0.25, 0.68, fold.x));
      float reflection = band * crest * bent * settling * exposure * fold.w;
      lit = mix(lit, float3(0.95, 0.84, 0.80), reflection);

      // A restrained grazing reflection traces the rounded crest. It is inside
      // the material and never produces an emissive outline around the corner.
      float edge = (1.0 - smoothstep(0.0, 0.022, v)) * across * bent;
      lit += float3(0.10, 0.055, 0.17) * edge * fold.w;

      // At f57–66 the turned laminate starts exposing its purple reverse. This
      // is a narrow surface inside the curved silhouette, with a dark contact
      // crease separating it from the print, rather than a glowing stroke.
      float turned = smoothstep(0.56, 0.72, fold.x);
      float tipTaper = smoothstep(0.0, 0.065, d);
      float shoulderTaper = 1.0 - smoothstep(fold.y * 0.42, fold.y * 0.88 + 0.001, d);
      float reverseExposure = turned * tipTaper * shoulderTaper;
      float reverseWidth = packRect.z * 0.007 * reverseExposure;
      // Convert material UV distance into distance normal to the projected edge.
      // Compensation keeps the reverse thin even where the folded mesh compresses.
      float3 section = mix(arrays[64 + index].xyz, arrays[65 + index].xyz, part);
      float fromCrest = v < 0.125 ? v * max(local.z, 0.0)
        : v < 0.25 ? mix(0.125 * max(local.z, 0.0), section.x, (v - 0.125) * 8.0)
        : v < 0.5 ? mix(section.x, section.y, (v - 0.25) * 4.0)
        : v < 0.75 ? mix(section.y, section.z, (v - 0.5) * 4.0)
        : mix(section.z, 1.0 - local.y, (v - 0.75) * 4.0);
      float edgeDistance = max(fromCrest, 0.0) * local.x / sqrt(1.0 + local.w * local.w);
      float acrossReverse = edgeDistance / max(reverseWidth, packRect.z * 0.00001);
      float reverseMask = (1.0 - smoothstep(0.78, 1.12, acrossReverse)) * reverseExposure;
      float outerRidge = 1.0 - smoothstep(0.05, 0.45, acrossReverse);
      float3 reverseColor = mix(float3(0.77, 0.40, 1.0), float3(0.97, 0.61, 1.0), outerRidge);
      lit = mix(lit, reverseColor, reverseMask);
      float contactCrease = smoothstep(0.85, 1.12, acrossReverse)
        * (1.0 - smoothstep(1.20, 1.75, acrossReverse)) * reverseExposure;
      lit *= 1.0 - contactCrease * 0.28;
      return float4(clamp(lit, 0.0, 1.0) * sampled.a, sampled.a) * u.view.w;
    }

    // FOIL: the strip after it has come away.
    //   a: the pack rectangle   b: roll, twist, deglare, reflection strength   c.x: pixel scale
    //   arrays 0–27: signed facing, bending angle, texture height, projected height
    fragment float4 boosterPackFoilFragment(BoosterPackV in [[stage_in]],
                                            constant BoosterPackU &u [[buffer(1)]],
                                            constant float4 *arrays [[buffer(2)]],
                                            texture2d<float> artwork [[texture(0)]],
                                            sampler smp [[sampler(0)]]) {
      float2 xy = in.tex;
      float4 packRect = u.a;
      float4 material = u.b;
      float pixelScale = u.c.x;
      float uu = clamp((xy.x - packRect.x) / packRect.z, 0.0, 1.0);
      float column = uu * 27.0;
      int index = clamp(int(floor(column)), 0, 26);
      float4 local = mix(arrays[index], arrays[index + 1], clamp(column - float(index), 0.0, 1.0));
      float v = clamp((xy.y - packRect.y) / max(local.z, 0.001), 0.0, 1.0);
      float4 sampled = artwork.sample(smp, (xy - packRect.xy) / packRect.zw);
      float3 print = sampled.rgb / max(sampled.a, 0.001);

      // The artwork can carry a fixed light on its left side. Remove that
      // illumination from the purple substrate, keeping the printed marks,
      // before applying the changing reflection.
      float purpleInk = smoothstep(0.08, 0.28, print.b - print.g);
      float leftSubstrate = 1.0 - smoothstep(0.32, 0.4, uu);
      // The unlit violet ink has little green. Its excess green estimates the
      // additive illumination more reliably than painting a dark UV stripe.
      float deglare = max(print.g - 0.025, 0.0) * purpleInk * leftSubstrate * material.z;
      // Blue clips early in a photographed hotspot. Removing as much
      // blue as green would turn the recovered violet ink into a maroon patch.
      float3 unlit = max(print - deglare * float3(1.05, 0.95, 0.35), print * float3(0.32, 0.08, 0.4));

      // A softbox reflection is evaluated in foil coordinates, including both the
      // span-wise twist and the cross-strip bend. UVs then carry it through every
      // triangle's rotation, compression and inversion with the printed texture.
      float theta = material.x + (uu - 0.5) * material.y + (v - 0.56) * 0.44 + local.y * 0.6;
      float3 normal = float3(sin(theta), 0.0, cos(theta));
      float facingLight = max(normal.z, 0.0);
      float broadReflection = pow(facingLight, 16.0);
      float sharpReflection = pow(facingLight, 88.0);
      float lowerReflection = 0.58 + 0.42 * smoothstep(0.1, 0.95, v);
      float reflection = (broadReflection * 0.42 + sharpReflection * 0.76)
        * lowerReflection * pow(abs(local.x), 0.65) * material.w;

      // The signed Jacobian of each mesh column decides which side is visible.
      // Adjacent parts of the strip can show different sides during the twist.
      float front = smoothstep(-0.075, 0.075, local.x);
      float diffuse = 0.69 + 0.22 * abs(local.x) + 0.09 * facingLight;
      float3 frontColor = unlit * diffuse;
      float reverseDetail = dot(unlit, float3(0.2126, 0.7152, 0.0722));
      float3 reverseColor = float3(0.32, 0.012, 0.72) * (0.72 + 0.28 * abs(local.x))
        + reverseDetail * float3(0.04, 0.005, 0.07);
      // The thin purple laminate keeps its printed marks visible as it twists;
      // replacing the whole reverse with purple erased that detail.
      float reversePrint = (1.0 - purpleInk) * 0.88 * (1.0 - smoothstep(1.90, 2.20, material.x));
      reverseColor = mix(reverseColor, unlit * float3(0.72, 0.62, 0.90), reversePrint);
      float3 base = mix(reverseColor, frontColor, front);

      // The broad reflected light is pink/violet; its center tends toward silver,
      // with a restrained cyan interference fringe as the foil becomes edge-on.
      float interference = 0.5 + 0.5 * sin(theta * 5.0 + v * 2.1);
      float3 reflectionColor = mix(float3(1.0, 0.52, 0.93), float3(0.68, 0.9, 1.0), interference * 0.3);
      reflectionColor = mix(reflectionColor, float3(1.0, 0.97, 1.0), sharpReflection);
      float backsideReflection = pow(max(-normal.z, 0.0), 28.0) * 0.055;
      reflection = mix(backsideReflection * material.w, reflection, front);
      float3 lit = base + (1.0 - base) * reflectionColor * min(reflection, 0.94);

      float grazing = pow(1.0 - abs(local.x), 3.0);
      lit += grazing * float3(0.025, 0.014, 0.065) * material.w;

      // Thin colored edge, also in UV space. Its width compensates for projected
      // compression, so a turning strip does not keep an oversized glowing rim.
      float rimWidth = clamp(1.0 * pixelScale / max(local.w, pixelScale), 0.007, 0.1);
      float rim = smoothstep(1.0 - rimWidth, 1.0, v);
      float3 rimColor = mix(float3(0.3, 0.43, 0.83), float3(0.49, 1.0, 1.0), front);
      lit = mix(lit, rimColor, rim * (0.35 + 0.45 * front));
      return float4(clamp(lit, 0.0, 1.0) * sampled.a, sampled.a) * u.view.w;
    }

    // BODY: the pack below the cut.   a: the texture's rectangle   b.x: deglare strength
    fragment float4 boosterPackBodyFragment(BoosterPackV in [[stage_in]],
                                            constant BoosterPackU &u [[buffer(1)]],
                                            texture2d<float> artwork [[texture(0)]],
                                            sampler smp [[sampler(0)]]) {
      float2 xy = in.tex;
      float4 textureRect = u.a;
      float2 uv = (xy - textureRect.xy) / max(textureRect.zw, float2(0.001));
      float4 sampled = artwork.sample(smp, uv);
      float3 print = sampled.rgb / max(sampled.a, 0.001);

      // Artwork photographed intact can hold a softbox crescent below its
      // upper-left seal, which the opening loses as the front turns away.
      // Subtract only its additive energy in the same texture region.
      float flank = smoothstep(0.006, 0.026, uv.x) * (1.0 - smoothstep(0.105, 0.235, uv.x));
      float upperPanel = smoothstep(0.158, 0.175, uv.y) * (1.0 - smoothstep(0.222, 0.278, uv.y));
      // Green above the dark violet ink floor estimates that light, preserving
      // printed detail and the rest of the artwork outside the crescent.
      float deglare = max(print.g - 0.10, 0.0) * flank * upperPanel * clamp(u.b.x, 0.0, 1.0);
      float3 color = max(print - deglare * float3(0.89, 1.0, 0.52), float3(0.0));
      return float4(color * sampled.a, sampled.a) * u.view.w;
    }

    static float boosterPackLobe(float x, float width) {
      return exp(-x * x / (width * width));
    }

    // MOUTH: the silver inner wall, and with `rim` the bonded cut edge.
    //   a: center, secondCenter, strength, opening   b: travel, rim, liningCenter, liningLight
    //   c: sideLight, liningTilt   d: liningTint   arrays 0–8: lining surface angle and depth
    fragment float4 boosterPackMouthFragment(BoosterPackV in [[stage_in]],
                                             constant BoosterPackU &u [[buffer(1)]],
                                             constant float4 *arrays [[buffer(2)]]) {
      float center = u.a.x;
      float secondCenter = u.a.y;
      float strength = u.a.z;
      float opening = u.a.w;
      float travel = u.b.x;
      float rim = u.b.y;
      float liningCenter = u.b.z;
      float liningLight = u.b.w;
      float sideLight = u.c.x;
      float liningTilt = u.c.y;
      float3 liningTint = u.d.xyz;
      float uu = clamp(in.tex.x, 0.0, 1.0);
      float v = clamp(in.tex.y, 0.0, 1.0);
      // Surface pitch changes the direction reflected toward the viewer. The
      // small bend belongs to the laminate, so its reflections deform with its UVs.
      float bend = 0.016 * sin(uu * 7.5 + travel * 2.8) * (1.0 - v);
      float pitch = (v - 0.5) * (0.085 + opening * 1.45) + travel * 0.04 + bend;
      float q = uu + pitch;
      float broad = boosterPackLobe(q - center, 0.24);
      if (rim < 0.5) {
        // Sample broad reflected lights through the curved sheet's surface angle.
        // The shallow creases stay in material coordinates; they bend the light,
        // rather than translating a painted stripe or scrolling a noise texture.
        float sheetBend = 0.014 * sin(uu * 10.0 + 0.8) + 0.006 * sin(uu * 24.0 - v * 1.8);
        float column = uu * 8.0;
        int index = clamp(int(floor(column)), 0, 7);
        float2 localSurface = mix(arrays[index].xy, arrays[index + 1].xy, clamp(column - float(index), 0.0, 1.0));
        float reflected = uu + (v - 0.5) * (0.055 + opening * 0.3)
          + sheetBend * (0.35 + 0.65 * v) + liningTilt * (v - 0.2)
          + localSurface.x * 0.13 + localSurface.y * 0.45;
        float d = reflected - liningCenter;
        float softLight = boosterPackLobe(d, d < 0.0 ? 0.205 : 0.19);
        // A cool environment reflected in aluminum, including the dark left
        // flank and violet surroundings at the turned end. No flat gray fill.
        float3 color = mix(float3(0.145, 0.155, 0.205), float3(0.255, 0.230, 0.375),
          smoothstep(0.10, 0.95, reflected));
        color *= 0.91 + 0.09 * v;
        // The first shallow opening still shows the printed back of the wrapper.
        // Opening the wall rotates its silver face into view; the outer purple
        // laminate remains visible only along the lower folded return.
        float printedFacing = (1.0 - smoothstep(0.025, 0.039, opening)) * smoothstep(0.36, 0.74, uu);
        color = mix(color, float3(0.30, 0.025, 0.65), printedFacing);
        // Thin-film color has a broad falloff and shares the same reflected
        // direction as the white light. Interpolate reflected energy instead
        // of adding saturated RGB lobes, which clipped blue into a flat ribbon.
        float filmAngle = d + (v - 0.5) * 0.026;
        float filmShift = clamp((1.0 - liningTint.g) / 0.40, 0.0, 1.0);
        float3 blueWing = mix(float3(0.26, 0.72, 0.94), float3(0.38, 0.43, 1.0), filmShift);
        float3 lightColor = mix(blueWing, liningTint, smoothstep(-0.16, -0.012, filmAngle));
        lightColor = mix(lightColor, float3(0.70, 0.76, 0.83), smoothstep(0.005, 0.16, filmAngle));
        color = mix(color, lightColor, softLight * liningLight);
        // Different shallow facets catch the same environment at different
        // angles: quiet silver streaks, not equally bright rainbow stripes.
        float streak = boosterPackLobe(reflected - liningCenter - 0.055, 0.012) * 0.11
          + boosterPackLobe(reflected - liningCenter - 0.235, 0.021) * 0.12;
        color += streak * float3(0.72, 0.82, 0.94) * liningLight;
        // A second light catches the turned foil at a different incidence angle.
        // It shares the scene pose, while keeping its own direction and falloff.
        float side = boosterPackLobe(reflected - secondCenter, 0.036) * sideLight;
        color += side * float3(0.53, 0.64, 0.47);

        // The outer printed laminate turns back under the silver inside wall.
        // The return is wider at the exit, where the corner exposes its purple
        // outer face; it is not a purple pigment gradient across the interior.
        float sideTurn = smoothstep(0.925, 0.987, uu);
        color *= 1.0 - 0.30 * sideTurn;
        float returnStart = 0.77 - 0.36 * smoothstep(0.52, 0.96, uu);
        float printedReturn = smoothstep(returnStart, 0.87, v);
        float3 laminate = float3(0.28, 0.025, 0.57) * (0.82 + 0.18 * v) + softLight * float3(0.025, 0.016, 0.045);
        color = mix(color, laminate, printedReturn);

        // The folded end catches a subdued metallic glint only on its silver
        // face. A full-height white stripe would look like a seam on a rigid cap.
        float creaseCenter = 0.980 + (0.5 - v) * 0.003;
        float crease = boosterPackLobe(uu - creaseCenter, 0.0018) * (0.25 + 0.40 * strength) * (1.0 - printedReturn);
        color = mix(color, float3(0.66, 0.62, 0.82), crease * 0.20);
        color *= 1.0 - 0.28 * smoothstep(0.989, 1.0, uu);
        return float4(clamp(color, 0.0, 1.0), 1.0) * u.view.w;
      }
      float cyan = boosterPackLobe(q - center + 0.06, 0.11);
      float pink = boosterPackLobe(q - center - 0.025, 0.10);
      float white = boosterPackLobe(q - center - 0.092, 0.046);
      float second = boosterPackLobe(q - secondCenter, 0.065);
      float3 spectral = float3(0.14, 0.74, 0.88) * cyan + float3(0.76, 0.08, 0.64) * pink
        + float3(0.64, 0.66, 0.61) * white + float3(0.28, 0.22, 0.38) * second;
      // A thin bonded cut edge: a white glint, cyan transmission and a soft violet
      // underside. They share one surface rather than four overlapping shapes.
      float3 color = mix(float3(0.84, 0.96, 0.99), float3(0.99, 1.0, 1.0), broad);
      color = mix(color, float3(0.06, 0.80, 1.0), smoothstep(0.35, 0.67, v));
      color = mix(color, float3(0.24, 0.10, 0.64), smoothstep(0.63, 0.90, v));
      color += spectral * strength * (0.28 + 0.55 * broad);
      color *= 1.0 - 0.16 * smoothstep(0.82, 1.0, uu);
      float alpha = 1.0 - smoothstep(0.75, 1.0, v);
      return float4(clamp(color, 0.0, 1.0) * alpha, alpha) * u.view.w;
    }

    // Rays around the rising card. Their rotation follows the opening clock,
    // so a paused session also pauses the light.   a: center, radius, phase   b.x: strength
    fragment float4 boosterPackRiseLightFragment(BoosterPackV in [[stage_in]],
                                                 constant BoosterPackU &u [[buffer(1)]]) {
      float2 d = in.local - u.a.xy;
      float dist = length(d) / u.a.z;
      float phase = u.a.w;
      float angle = atan2(d.y, d.x);
      float broad = pow(0.5 + 0.5 * sin(angle * 7.0 + phase), 3.0);
      float fine = pow(0.5 + 0.5 * sin(angle * 13.0 - phase * 1.7 + 1.9), 4.0);
      float falloff = pow(max(0.0, 1.0 - dist), 1.35);
      float core = pow(max(0.0, 1.0 - dist * 1.15), 2.2);
      float light = clamp(u.b.x * (core * 0.95 + (broad * 0.62 + fine * 0.38) * falloff * 1.05), 0.0, 1.0);
      return float4(float3(1.0, 0.98, 0.88) * light, light) * u.view.w;
    }

    // CARD: a face of a revealed card, clipped to its rounded rectangle. Light lives in card
    // UV space; its placement is driven by the card normal, so a stationary finger holds the
    // foil. Only the measured reveal sweep uses time.
    //   a: size, corner radius   b: tilt, holo, shine   c: sweep, flash
    fragment float4 boosterPackCardFragment(BoosterPackV in [[stage_in]],
                                            constant BoosterPackU &u [[buffer(1)]],
                                            texture2d<float> artwork [[texture(0)]],
                                            sampler smp [[sampler(0)]]) {
      float2 p = in.local;
      float2 size = u.a.xy;
      float2 tilt = u.b.xy;
      float holo = u.b.z;
      float shine = u.b.w;
      float sweep = u.c.x;
      float flash = u.c.y;
      float2 uv = p / size + 0.5;
      float4 base = artwork.sample(smp, uv);
      float slope = uv.x * 0.72 + uv.y * 0.58;
      float phase = tilt.x * 0.82 + tilt.y * 0.6;
      float3 rainbow = 0.5 + 0.5 * cos(6.28318 * (slope * 1.65 - phase + float3(0.0, 0.33, 0.67)));
      float band = boosterPackSq(max(0.0, 1.0 - abs(slope - 0.63 - phase * 0.8) * 2.6));
      float border = 1.0 - smoothstep(0.012, 0.04, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
      float movement = min(1.0, abs(tilt.x) * 1.7 + abs(tilt.y));
      float foil = holo * (0.045 + movement * 0.26 + border * 0.18) * (0.28 + band * 0.72);
      float grain = fract(sin(dot(floor(uv * size * 0.8), float2(12.9898, 78.233))) * 43758.5453);
      foil *= 0.88 + grain * 0.24;
      float3 color = mix(base.rgb, min(float3(1.0), base.rgb * 0.85 + rainbow * 0.72), foil);
      float glint = pow(max(0.0, 1.0 - abs(slope - (sweep * 1.9 - 0.25)) * 5.0), 3.0);
      float reflection = pow(max(0.0, 1.0 - abs(slope - 0.25 - phase) * 4.0), 6.0);
      float white = shine * glint * flash * 0.8 + reflection * movement * (0.055 + holo * 0.16);
      color = 1.0 - (1.0 - color) * (1.0 - white);
      color *= 1.0 - min(0.1, abs(tilt.x) * 0.065);
      float edge = boosterPackRoundedBox(p, size * 0.5, u.a.z);
      float cover = clamp(0.5 - edge / max(fwidth(edge), 0.0001), 0.0, 1.0);
      return float4(min(color, float3(base.a)), base.a) * (cover * u.view.w);
    }
    """
}
