package expo.modules.subjectsegmentation

/**
 * GLES 3.0 port of the embedded Metal shaders used by the Apple renderer.
 * Keep the random constants and timing thresholds in sync with ParticleRenderer.swift.
 */
internal object ParticleShaders {
  const val BACKGROUND_VERTEX = """#version 300 es
precision highp float;
precision highp int;

out vec2 textureCoordinate;

void main() {
  const vec2 vertices[3] = vec2[3](
    vec2(-1.0, -1.0),
    vec2(-1.0, 3.0),
    vec2(3.0, -1.0)
  );
  vec2 vertex = vertices[gl_VertexID];
  gl_Position = vec4(vertex, 0.0, 1.0);
  textureCoordinate = vec2(
    (vertex.x + 1.0) * 0.5,
    (1.0 - vertex.y) * 0.5
  );
}
"""

  const val BACKGROUND_FRAGMENT = """#version 300 es
precision highp float;
precision highp int;

in vec2 textureCoordinate;

uniform sampler2D frameTexture;
uniform vec2 logicalSize;
uniform vec4 backgroundColor;
uniform float timeSeconds;
uniform float progress;
uniform uint reduceMotion;

out vec4 fragmentColor;

vec2 coverUV(vec2 screen, vec2 imageSize, vec2 viewport) {
  float scale = max(viewport.x / imageSize.x, viewport.y / imageSize.y);
  vec2 offset = (viewport - imageSize * scale) * 0.5;
  return (screen - offset) / (imageSize * scale);
}

void main() {
  vec3 matte = clamp(backgroundColor.rgb, 0.0, 1.0);
  if (reduceMotion != 0u || timeSeconds < 0.400) {
    vec2 imageSize = vec2(textureSize(frameTexture, 0));
    vec2 uv = coverUV(textureCoordinate * logicalSize, imageSize, logicalSize);
    vec3 original = texture(frameTexture, uv).rgb;
    float reveal = reduceMotion != 0u ? smoothstep(0.08, 0.92, progress) : 0.0;
    fragmentColor = vec4(mix(original, matte, reveal), 1.0);
    return;
  }
  fragmentColor = vec4(matte, 1.0);
}
"""

  const val PARTICLE_VERTEX = """#version 300 es
precision highp float;
precision highp int;

uniform sampler2D frameTexture;
uniform sampler2D maskTexture;
uniform vec2 logicalSize;
uniform vec2 drawableSize;
uniform vec4 subjectBounds;
uniform float timeSeconds;
uniform float dustStartSeconds;
uniform float dustDurationSeconds;
uniform uint seed;
uniform uint columns;
uniform uint particleCount;
uniform float cellSize;
uniform float edgeParticleStrength;

flat out vec4 particleColor;
flat out float coreRadius;
flat out float edgeWidth;

vec2 coverUV(vec2 screen, vec2 imageSize, vec2 viewport) {
  float scale = max(viewport.x / imageSize.x, viewport.y / imageSize.y);
  vec2 offset = (viewport - imageSize * scale) * 0.5;
  return (screen - offset) / (imageSize * scale);
}

uint particleHash(uint value) {
  value ^= value >> 16u;
  value *= 0x7feb352du;
  value ^= value >> 15u;
  value *= 0x846ca68bu;
  value ^= value >> 16u;
  return value;
}

float particleRandom(uint value) {
  return float(particleHash(value) >> 8u) * (1.0 / 16777216.0);
}

void main() {
  particleColor = vec4(0.0);
  coreRadius = 0.0;
  edgeWidth = 0.5;
  gl_Position = vec4(3.0, 3.0, 0.0, 1.0);
  gl_PointSize = 1.0;

  uint id = uint(gl_VertexID);
  if (id >= particleCount) {
    return;
  }

  uint randomSeed = particleHash(id ^ seed);
  vec2 jitter = vec2(
    particleRandom(randomSeed + 11u),
    particleRandom(randomSeed + 23u)
  );
  vec2 cell = vec2(float(id % columns), float(id / columns));
  vec2 source = (cell + 0.12 + jitter * 0.76) * cellSize;
  if (any(greaterThanEqual(source, logicalSize))) {
    return;
  }

  vec2 frameSize = vec2(textureSize(frameTexture, 0));
  vec2 maskSize = vec2(textureSize(maskTexture, 0));
  vec2 frameUV = coverUV(source, frameSize, logicalSize);
  vec2 maskUV = coverUV(source, maskSize, logicalSize);
  float sourceSubject = clamp(textureLod(maskTexture, maskUV, 0.0).r, 0.0, 1.0);
  float membership = 1.0 - smoothstep(0.08, 0.72, sourceSubject);
  if (edgeParticleStrength > 0.0) {
    vec2 probe = vec2(3.0);
    float around = textureLod(
      maskTexture,
      coverUV(source + vec2(probe.x, 0.0), maskSize, logicalSize),
      0.0
    ).r;
    around = min(around, textureLod(
      maskTexture,
      coverUV(source - vec2(probe.x, 0.0), maskSize, logicalSize),
      0.0
    ).r);
    around = min(around, textureLod(
      maskTexture,
      coverUV(source + vec2(0.0, probe.y), maskSize, logicalSize),
      0.0
    ).r);
    around = min(around, textureLod(
      maskTexture,
      coverUV(source - vec2(0.0, probe.y), maskSize, logicalSize),
      0.0
    ).r);
    float innerEdge = smoothstep(0.08, 0.72, sourceSubject) *
      (1.0 - smoothstep(0.08, 0.72, around));
    membership = max(membership, innerEdge * edgeParticleStrength);
  }
  if (membership <= 0.001) {
    return;
  }

  float age = clamp(
    (timeSeconds - dustStartSeconds) / dustDurationSeconds,
    0.0,
    1.0
  );
  float birth = 0.06 * particleRandom(randomSeed + 37u);
  bool longLived = particleRandom(randomSeed + 43u) < 0.36;
  float deathRandom = particleRandom(randomSeed + 41u);
  float death = longLived
    ? mix(0.86, 1.0, deathRandom)
    : mix(0.32, 0.64, deathRandom);
  if (age < birth || age >= death) {
    return;
  }

  float motionAge = max(age - birth, 0.0);
  float particleAge = motionAge / (death - birth);
  float fade = smoothstep(0.50, 1.0, particleAge);
  float opacity = smoothstep(birth, birth + 0.02, age) *
    (1.0 - fade) * membership * 0.94;
  if (opacity <= 0.001) {
    return;
  }

  float viewportScale = clamp(logicalSize.x / 390.0, 0.82, 1.30);
  float angle = particleRandom(randomSeed + 53u) * 6.28318530718;
  float speed = mix(15.0, 48.0, particleRandom(randomSeed + 67u)) * viewportScale;
  vec2 velocity = vec2(cos(angle), sin(angle)) * speed;
  vec2 subjectUV = subjectBounds.xy + subjectBounds.zw * 0.5;
  vec2 center = subjectUV * logicalSize;
  vec2 radial = source - center;
  radial /= max(length(radial), 1.0);
  velocity += radial * (8.0 * viewportScale * particleRandom(randomSeed + 71u));
  float scatter = 1.0 - exp(-3.6 * motionAge);
  float lift = mix(45.0, 130.0, particleRandom(randomSeed + 83u)) *
    viewportScale * pow(motionAge, 1.6);
  vec2 phase = vec2(
    particleRandom(randomSeed + 97u),
    particleRandom(randomSeed + 101u)
  ) * 6.28318530718;
  vec2 frequency = vec2(4.0, 4.5) + vec2(
    particleRandom(randomSeed + 107u),
    particleRandom(randomSeed + 109u)
  ) * 5.0;
  vec2 amplitude = (2.0 + vec2(
    particleRandom(randomSeed + 127u),
    particleRandom(randomSeed + 131u)
  ) * 5.0) * viewportScale;
  vec2 curl = (sin(phase + frequency * motionAge) - sin(phase)) * amplitude;
  vec2 destination = source + velocity * scatter + curl + vec2(0.0, -lift);

  float sizeRandom = particleRandom(randomSeed + 149u);
  float diameter = sizeRandom < 0.94
    ? mix(0.8, 1.6, sizeRandom / 0.94)
    : mix(1.6, 2.0, (sizeRandom - 0.94) / 0.06);
  float initialFootprint = mix(1.25, 1.0, smoothstep(0.03, 0.25, age));
  diameter *= viewportScale * initialFootprint * mix(1.0, 0.88, fade);
  float pixelRatio = min(
    drawableSize.x / logicalSize.x,
    drawableSize.y / logicalSize.y
  );
  float diameterPixels = max(0.5, diameter * pixelRatio);
  gl_PointSize = diameterPixels + 1.0;
  coreRadius = 0.5 * diameterPixels / gl_PointSize;
  edgeWidth = 0.5 / gl_PointSize;
  gl_Position = vec4(
    destination.x / logicalSize.x * 2.0 - 1.0,
    1.0 - destination.y / logicalSize.y * 2.0,
    0.0,
    1.0
  );
  vec4 sampled = textureLod(frameTexture, frameUV, 0.0);
  particleColor = vec4(sampled.rgb, opacity * sampled.a);
}
"""

  const val PARTICLE_FRAGMENT = """#version 300 es
precision highp float;
precision highp int;

flat in vec4 particleColor;
flat in float coreRadius;
flat in float edgeWidth;

out vec4 fragmentColor;

void main() {
  float pointDistance = length(gl_PointCoord - 0.5);
  float shape = 1.0 - smoothstep(
    coreRadius - edgeWidth,
    coreRadius + edgeWidth,
    pointDistance
  );
  float alpha = particleColor.a * shape;
  fragmentColor = vec4(particleColor.rgb * alpha, alpha);
}
"""
}
