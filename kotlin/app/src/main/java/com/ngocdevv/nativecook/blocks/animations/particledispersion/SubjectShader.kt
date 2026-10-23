package com.ngocdevv.nativecook.blocks.animations.particledispersion

/** Matches the native Metal foreground pass. */
internal object SubjectShader {
  const val FRAGMENT = """#version 300 es
precision highp float;
precision highp int;
in vec2 textureCoordinate;
out vec4 fragmentColor;
uniform sampler2D frame;
uniform sampler2D mask;
uniform vec2 logicalSize;
uniform vec4 subjectBounds;
uniform float timeSeconds;
uniform int reduceMotion;
struct ParticleUniforms {
  vec2 logicalSize;
  vec4 subjectBounds;
  float timeSeconds;
  int reduceMotion;
};
vec2 coverUV(vec2 screen, vec2 imageSize, vec2 viewport) {
  float scale = max(viewport.x / imageSize.x, viewport.y / imageSize.y);
  return (screen - (viewport - imageSize * scale) * 0.5) / (imageSize * scale);
}


  float subjectClock(float globalTime) {
    return globalTime <= 1.35 ? globalTime : 1.35 + max(globalTime - 3.017, 0.0) * 0.8;
  }
  float ramp(float value) { return smoothstep(0.0, 1.0, value); }
  float cubic(float value) { return 1.0 - pow(1.0 - clamp(value, 0.0, 1.0), 3.0); }
  vec4 overColor(vec4 top, vec4 bottom) { return top + bottom * (1.0 - top.a); }

  vec2 impactPosition(vec2 xy, float time, vec4 b, float amplitude) {
    float phase = (time - 0.100) / 0.280;
    if (phase <= 0.0 || phase >= 1.0) { return xy; }
    vec2 size = max(b.zw, vec2(1.0));
    vec2 delta = xy - (b.xy + size * vec2(0.5, 0.32));
    float distance = length(delta);
    float maxRadius = length(size * vec2(0.5, 0.68));
    float band = max(min(size.x, size.y) * 0.65, maxRadius * 0.25);
    float wavePhase = (distance - mix(-band, maxRadius + band, phase)) / band;
    if (abs(wavePhase) >= 1.0) { return xy; }
    float wave = sin(wavePhase * 3.14159265) * (1.0 - smoothstep(0.65, 1.0, abs(wavePhase)));
    float envelope = ramp(phase / 0.12) * (1.0 - ramp((phase - 0.80) / 0.20)) * (1.0 - 0.25 * phase);
    vec2 outside = max(max(b.xy - xy, xy - b.xy - size), vec2(0.0));
    float edge = 1.0 - smoothstep(amplitude, max(amplitude * 4.0, 1.0), length(outside));
    return xy - delta / max(distance, 1.0) * amplitude * wave * envelope *
      smoothstep(0.0, band * 0.45, distance) * edge;
  }

  float maskValue(sampler2D mask, vec2 xy, vec2 viewport) {
    vec2 uv = coverUV(xy, vec2(textureSize(mask, 0)), viewport);
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) { return 0.0; }
    return clamp(texture(mask, uv).r, 0.0, 1.0);
  }

  vec4 renderSubject(ParticleUniforms u) {
    vec2 xy = textureCoordinate * u.logicalSize;
    vec4 b = vec4(u.subjectBounds.xy * u.logicalSize, u.subjectBounds.zw * u.logicalSize);
    vec2 center = b.xy + b.zw * 0.5;
    float time = u.reduceMotion != 0 ? 0.0 : subjectClock(u.timeSeconds);
    float scale = u.reduceMotion != 0 ? 1.0 : 1.0 + 0.05 * cubic((time - 1.480) / 0.270);
    vec2 source = center + (xy - center) / scale;
    float amplitude = min(min(b.z, b.w), min(u.logicalSize.x, u.logicalSize.y)) * 0.04;
    source = impactPosition(source, time, b, amplitude);
    float subject = maskValue(mask, source, u.logicalSize);
    vec2 uv = coverUV(source, vec2(textureSize(frame, 0)), u.logicalSize);
    vec3 original = texture(frame, uv).rgb;
    vec3 color = original;
    float localY = (source.y - b.y) / max(b.w, 1.0);
    float phase = (time - 0.100) / 0.280;
    float glow = 0.0;
    if (u.reduceMotion == 0 && phase >= 0.0 && phase <= 1.0) {
      float band = (1.0 - smoothstep(0.105, 0.245, abs(localY - mix(1.10, -0.10, phase)))) *
        ramp(phase / 0.125) * (1.0 - ramp((phase - 0.875) / 0.125));
      color = min(color + (1.0 - color) * vec3(1.0, 0.90, 0.72) * (0.52 * band), 1.0);
      float radius = 1.7 * clamp(u.logicalSize.y / 844.0, 0.82, 1.28);
      float around = max(max(maskValue(mask, source + vec2(radius, 0), u.logicalSize),
        maskValue(mask, source - vec2(radius, 0), u.logicalSize)),
        max(maskValue(mask, source + vec2(0, radius), u.logicalSize),
        maskValue(mask, source - vec2(0, radius), u.logicalSize)));
      glow = max(around - subject, 0.0) * band * 0.52;
      return vec4(vec3(mix(mix(original, color, subject), vec3(1.0, 0.90, 0.72), glow)), 1.0);
    }
    if (u.reduceMotion == 0 && time >= 1.580 && time <= 1.930) {
      float darkPhase = clamp((time - 1.580) / 0.350, 0.0, 1.0);
      float d = (localY - mix(-0.12, 1.12, darkPhase)) / 0.18;
      float envelope = ramp((time - 1.580) / 0.080) * (1.0 - ramp((time - 1.850) / 0.080));
      color *= 1.0 - 0.23 * exp(-0.5 * d * d) * envelope;
    }
    vec4 behind = vec4(0.0);
    if (u.reduceMotion == 0) {
      float shadowOpacity = 0.34 * ramp((time - 1.460) / 0.300);
      vec2 shadowXY = center + (xy - u.logicalSize * vec2(0.018, 0.012) - center) / (scale * 1.008);
      float shadow = maskValue(mask, shadowXY, u.logicalSize) * shadowOpacity;
      behind = vec4(vec3(0.141, 0.098, 0.078) * shadow, shadow);
      float rise = cubic((time - 1.480) / 0.190);
      float settle = ramp((time - 1.670) / 0.280);
      vec2 underScale = vec2(1.0 + 0.115 * rise - 0.057 * settle,
        1.0 + 0.052 * cubic((time - 1.480) / 0.270));
      vec2 underXY = center + (xy + u.logicalSize * vec2(0.004, 0.0015) - center) / underScale;
      float underOpacity = ramp((time - 1.475) / 0.150) * (0.78 - 0.48 * ramp((time - 1.700) / 0.260));
      float under = maskValue(mask, underXY, u.logicalSize) * underOpacity;
      behind = overColor(vec4(vec3(1.0, 0.945, 0.871) * under, under), behind);
    }
    return vec4(overColor(vec4(color * subject, subject), behind));
  }

void main() {
  ParticleUniforms u;
  u.logicalSize = logicalSize;
  u.subjectBounds = subjectBounds;
  u.timeSeconds = timeSeconds;
  u.reduceMotion = reduceMotion;
  fragmentColor = renderSubject(u);
}
"""
}
