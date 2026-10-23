import { d, tgpu } from 'typegpu';
import { postLayout } from './schemas';

const resources = postLayout.$;
// TypeGPU 0.12.7 warns on literal `in.uv` in WGSL shells; a typed alias preserves its generated input.
const varying = tgpu['~unstable'].rawCodeSnippet('in', d.struct({ uv: d.vec2f }));
const tent = tgpu.fn([d.vec2f, d.vec2f], d.vec4f)(`(uv, stepSize) {
  var colour = vec4f(0.0);
  for (var y = -1; y <= 1; y++) {
    for (var x = -1; x <= 1; x++) {
      let weight = f32((2 - abs(x)) * (2 - abs(y))) / 16.0;
      colour += textureSampleLevel(resources.lower, resources.linear, uv + vec2f(f32(x), f32(y)) * stepSize, 0.0) * weight;
    }
  }
  return colour;
}`).$uses({ resources });

export function downsample(threshold: boolean) {
  return tgpu.fragmentFn({ in: { uv: d.vec2f }, out: d.vec4f })(`{
    let texel = 0.5 / vec2f(textureDimensions(resources.source));
    var colour = vec3f(0.0);
    for (var y = -1; y <= 1; y += 2) {
      for (var x = -1; x <= 1; x += 2) {
        let sampleColour = textureSampleLevel(resources.source, resources.linear, varying.uv + vec2f(f32(x), f32(y)) * texel, 0.0).rgb;
        ${threshold ? `let peak = max(sampleColour.r, max(sampleColour.g, sampleColour.b));
        let knee = pow(clamp(peak - 0.55, 0.0, 0.9), 2.0) / 1.8;
        let gain = max(peak - 1.0, knee) / max(peak, 0.0001);
        // Only soft flake emission enters here; it cannot reach the sharp resolve.
        let leafGlow = textureSampleLevel(resources.lower, resources.linear, varying.uv + vec2f(f32(x), f32(y)) * texel, 0.0).rgb;
        colour += (sampleColour * gain + leafGlow) * 0.25;` : 'colour += sampleColour * 0.25;'}
      }
    }
    return vec4f(colour, 0.0);
  }`).$uses({ resources, varying }).$name(threshold ? 'bloomExtract' : 'bloomDownsample');
}

// Blur the smallest level densely: wide sparse tent taps made separate ghost images.
// Two cheap separable passes give hot metal a broad, continuous halo of real radiance.
export function blur(horizontal: boolean, radius: number) {
  return tgpu.fragmentFn({ in: { uv: d.vec2f }, out: d.vec4f })(`{
    let texel = 1.0 / vec2f(textureDimensions(resources.source));
    var colour = vec3f(0.0);
    var weightSum = 0.0;
    for (var i = -${radius}; i <= ${radius}; i++) {
      let weight = exp(-f32(i * i) / ${2 * (radius * 0.375) ** 2});
      let offset = ${horizontal ? 'vec2f(f32(i), 0.0)' : 'vec2f(0.0, f32(i))'} * texel;
      colour += textureSampleLevel(resources.source, resources.linear, varying.uv + offset, 0.0).rgb * weight;
      weightSum += weight;
    }
    return vec4f(colour / weightSum, 0.0);
  }`).$uses({ resources, varying }).$name(horizontal ? 'bloomBlurX' : 'bloomBlurY');
}

export const upsample = tgpu.fragmentFn({ in: { uv: d.vec2f }, out: d.vec4f })(`{
  let high = textureSampleLevel(resources.source, resources.linear, varying.uv, 0.0);
  return high * 0.35 + tent(varying.uv, 2.5 / vec2f(textureDimensions(resources.lower))) * 0.65;
}`).$uses({ resources, tent, varying }).$name('bloomUpsample');

export function composite(bloom: boolean, srgbTarget: boolean) {
  return tgpu.fragmentFn({ in: { uv: d.vec2f }, out: d.vec4f })(`{
    let scene = textureSampleLevel(resources.source, resources.linear, varying.uv, 0.0);
    let halo = ${bloom ? 'tent(varying.uv, 1.0 / vec2f(textureDimensions(resources.lower))).rgb * 0.15' : 'vec3f(0.0)'};
    // Tone-map light with zero geometric coverage separately. Its brightest display
    // channel defines coverage, so the straight halo is luminous rather than grey.
    // Dividing faint radiance by an arbitrary alpha made a dark cloud on light sheets.
    let haloDisplay = clamp(pow(halo / (vec3f(1.0) + halo * 0.22), vec3f(0.4545)), vec3f(0.0), vec3f(1.0));
    let haloAlpha = max(haloDisplay.r, max(haloDisplay.g, haloDisplay.b));
    let alpha = scene.a + haloAlpha * (1.0 - scene.a);
    if (alpha < 0.000001) { return vec4f(0.0); }
    let radiance = scene.rgb / max(scene.a, 0.000001); // Bloom never lifts opaque iron or washes out its embossed lettering.
    let display = clamp(pow(max(radiance / (vec3f(1.0) + radiance * 0.22), vec3f(0.0)), vec3f(0.4545)), vec3f(0.0), vec3f(1.0));
    var colour = display * scene.a + haloDisplay * (1.0 - scene.a);
    ${srgbTarget ? `// An sRGB attachment encodes on store; undo it here to avoid double encoding.
    colour = select(colour / 12.92, pow((colour + 0.055) / 1.055, vec3f(2.4)), colour > vec3f(0.04045));` : ''}
    return vec4f(colour, alpha);
  }`).$uses({ resources, tent, varying }).$name('toneAndComposite');
}
