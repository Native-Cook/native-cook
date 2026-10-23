import { Skia } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import {
  SUBJECT_DARK_SCAN_BLUR_RATIO,
  SUBJECT_IMPACT_DURATION_SECONDS,
  SUBJECT_IMPACT_PADDING_MULTIPLIER,
  SUBJECT_IMPACT_START_SECONDS,
} from '../constants';

// Share the exact deformation across the photo, its alpha, and both silhouettes.
// Sampling only the photo would leave a rigid edge around a moving interior.
const SUBJECT_IMPACT_SHADER = `
uniform float timeSeconds;
uniform float reduceMotion;
uniform float4 subjectBounds;
uniform float impactAmplitude;

float subjectImpactPhase() {
  return (timeSeconds - ${SUBJECT_IMPACT_START_SECONDS.toFixed(3)}) /
    ${SUBJECT_IMPACT_DURATION_SECONDS.toFixed(3)};
}

float2 subjectSamplePosition(float2 xy) {
  float phase = subjectImpactPhase();
  if (reduceMotion > 0.5 || phase <= 0.0 || phase >= 1.0) {
    return xy;
  }

  float2 size = max(subjectBounds.zw, float2(1.0));
  float2 impactOrigin = subjectBounds.xy + size * float2(0.5, 0.32);
  float2 delta = xy - impactOrigin;
  float distance = length(delta);
  float maxRadius = length(size * float2(0.5, 0.68));
  float bandWidth = max(min(size.x, size.y) * 0.65, maxRadius * 0.25);
  float radius = mix(-bandWidth, maxRadius + bandWidth, phase);
  float wavePhase = (distance - radius) / bandWidth;
  if (abs(wavePhase) >= 1.0) {
    return xy;
  }

  // A single broad crest followed by a trough: push out, recoil, then rest.
  // The compact envelope prevents a train of ripples or a repeating wobble.
  float wave = sin(wavePhase * 3.14159265) *
    (1.0 - smoothstep(0.65, 1.0, abs(wavePhase)));
  float envelope = smoothstep(0.0, 0.12, phase) *
    (1.0 - smoothstep(0.80, 1.0, phase)) * (1.0 - 0.25 * phase);
  float centerFalloff = smoothstep(0.0, bandWidth * 0.45, distance);
  float2 outside = max(max(subjectBounds.xy - xy,
    xy - (subjectBounds.xy + size)), float2(0.0));
  float edgeFalloff = 1.0 - smoothstep(impactAmplitude,
    max(impactAmplitude * ${SUBJECT_IMPACT_PADDING_MULTIPLIER.toFixed(1)}, 1.0),
    length(outside));
  float2 direction = delta / max(distance, 1.0);
  return xy - direction * impactAmplitude * wave * envelope * centerFalloff * edgeFalloff;
}
`;

/** Transparent foreground cutout, including the scan bands and impact wave. */
export const SUBJECT_CUTOUT_SHADER = `
uniform shader frame;
uniform shader subjectMask;

uniform float2 resolution;

${SUBJECT_IMPACT_SHADER}

float maskAt(float2 position) {
  return clamp(subjectMask.eval(position).r, 0.0, 1.0);
}

half4 main(float2 xy) {
  float2 samplePosition = subjectSamplePosition(xy);
  half4 original = frame.eval(samplePosition);
  float subject = maskAt(samplePosition);
  float safeHeight = max(subjectBounds.w, 1.0);
  float localY = (samplePosition.y - subjectBounds.y) / safeHeight;
  half3 subjectColor = original.rgb;
  float glowAlpha = 0.0;

  if (reduceMotion < 0.5) {
    float brightPhase = subjectImpactPhase();
    if (brightPhase >= 0.0 && brightPhase <= 1.0) {
      float brightEnvelope =
        smoothstep(0.0, 0.125, brightPhase) *
        (1.0 - smoothstep(0.875, 1.0, brightPhase));
      float brightCenter = mix(1.10, -0.10, brightPhase);
      float brightBand =
        (1.0 - smoothstep(0.105, 0.245, abs(localY - brightCenter))) *
        brightEnvelope;
      half3 warm = half3(1.0, 0.90, 0.72);
      subjectColor = min(
        subjectColor + (half3(1.0) - subjectColor) * warm * half(0.52 * brightBand),
        half3(1.0)
      );

      float edgeRadius = 1.7 * clamp(resolution.y / 844.0, 0.82, 1.28);
      float around = maskAt(samplePosition + float2(edgeRadius, 0.0));
      around = max(around, maskAt(samplePosition - float2(edgeRadius, 0.0)));
      around = max(around, maskAt(samplePosition + float2(0.0, edgeRadius)));
      around = max(around, maskAt(samplePosition - float2(0.0, edgeRadius)));
      glowAlpha = max(around - subject, 0.0) * brightBand * 0.52;

      // Metal still shows the full capture during this first scan. Replace the
      // photo patch too, so recoil cannot expose a second, rigid subject below.
      // The displacement tapers to zero before this patch's padded draw edge.
      half3 photoColor = mix(original.rgb, subjectColor, half(subject));
      return half4(mix(photoColor, warm, half(glowAlpha)), 1.0);
    }

    if (timeSeconds >= 1.580 && timeSeconds <= 1.930) {
      float darkPhase = clamp((timeSeconds - 1.580) / 0.350, 0.0, 1.0);
      float darkEnvelope =
        smoothstep(1.580, 1.660, timeSeconds) *
        (1.0 - smoothstep(1.850, 1.930, timeSeconds));
      float darkCenter = mix(-0.12, 1.12, darkPhase);
      // Blur only the moving shadow's intensity with a Gaussian falloff.
      // The photo and silhouette retain their original detail and sharpness.
      float darkDistance = (localY - darkCenter) / ${SUBJECT_DARK_SCAN_BLUR_RATIO.toFixed(3)};
      float darkBand = exp(-0.5 * darkDistance * darkDistance) * darkEnvelope;
      subjectColor *= half(1.0 - 0.23 * darkBand);
    }
  }

  float alpha = clamp(subject + glowAlpha, 0.0, 1.0);
  half3 premultiplied =
    subjectColor * half(subject) +
    half3(1.0, 0.90, 0.72) * half(glowAlpha);
  return half4(premultiplied, half(alpha));
}
`;

/** Convert the grayscale Vision mask into a colourable transparent silhouette. */
export const MASK_SILHOUETTE_SHADER = `
uniform shader subjectMask;
uniform half4 silhouetteColor;

${SUBJECT_IMPACT_SHADER}

half4 main(float2 xy) {
  float coverage = clamp(subjectMask.eval(subjectSamplePosition(xy)).r, 0.0, 1.0);
  float alpha = coverage * float(silhouetteColor.a);
  return half4(silhouetteColor.rgb * half(alpha), half(alpha));
}
`;

function makeRuntimeEffect(source: string) {
  if (Platform.OS === 'web') {
    return null;
  }

  return Skia.RuntimeEffect.Make(source);
}

// RuntimeEffect creation can fail on an unsupported backend. The scene checks
// every pass and falls back to a still capture instead of rendering half a stack.
export const subjectCutoutRuntimeEffect = makeRuntimeEffect(SUBJECT_CUTOUT_SHADER);
export const maskSilhouetteRuntimeEffect = makeRuntimeEffect(
  MASK_SILHOUETTE_SHADER
);
