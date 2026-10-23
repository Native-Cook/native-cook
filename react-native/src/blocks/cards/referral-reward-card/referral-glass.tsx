import { useEffect, useMemo } from "react";
import { AccessibilityInfo, PixelRatio, Platform, StyleSheet } from "react-native";
import { Canvas, Fill, Shader, Skia, interpolateColors } from "@shopify/react-native-skia";
import {
  type DerivedValue,
  type SharedValue,
  Easing,
  cancelAnimation,
  useDerivedValue,
  useSharedValue,
  withRepeat,
  withTiming,
} from "react-native-reanimated";
import { FRIENDS } from "./referral-friends";
import { LIGHT } from "./referral-light";
import { referralMotion as M, referralSize as S, wrapIndex } from "./referral-palette";

// The card is a slab of clear glass lying on a field of colored light. Both
// are one shader: the light is three soft lamps in the colors of the friend
// under the lens, and the glass is what the light looks like through it.
// Near its edge the slab bends the light inwards, most at the very rim, and
// splits it a little by color; the rim itself catches a highlight on the
// upper left and a fainter one opposite. The button is a second, thicker
// piece of the same glass. Outside the card the light spills onto the page
// and fades, which is what says the card is transparent and not painted.

/** How far the light reaches past the card on every side. */
export const GLASS_BLEED = 96;

// The slab's half of the shader; the light's half is in referral-light.ts.
const SLAB = `// The button: its centre and half its size, then its radius, presence and press.
uniform float4 pill;
uniform float3 pillLook;
uniform float opaque;

// The field seen through glass that moves the view by shift along -n, the
// three colors bent by slightly different amounts. On the flat face the
// difference is too small to resolve, so one sample does.
float3 through(float2 p, float2 n, float shift) {
  if (shift < 0.125) return field(p - n * shift);
  return float3(
    field(p - n * shift * 1.2).r,
    field(p - n * shift).g,
    field(p - n * shift * 0.8).b);
}

// What thick glass does to the light that crosses it: slow ripples, three
// sine waves each bent by another, with thin bright veins where they cross
// zero, as on the floor of a pool. About -0.07 to 0.1; it turns once a loop.
float ripples(float2 p, float a) {
  float2 q = p / 30.0;
  float w = sin(1.3 * q.x + 1.6 * sin(0.9 * q.y + a))
    + sin(1.1 * q.y - 1.4 * sin(0.7 * q.x - a) + 2.0)
    + sin(0.8 * (q.x + q.y) + 1.2 * sin(0.6 * (q.x - q.y) + a) + 4.0);
  w /= 3.0;
  float veins = smoothstep(0.6, 1.0, 1.0 - abs(w));
  return 0.07 * w + 0.08 * veins * veins;
}

// A circular bezel meets the flat face with a zero slope.
float bezel(float depth, float width) {
  float k = 1.0 - clamp(depth / width, 0.0, 1.0);
  return 1.0 - sqrt(max(1.0 - k * k, 0.0));
}

float coverage(float d) {
  return clamp(0.5 - d * scale, 0.0, 1.0);
}

// Integrating both edges keeps a subpixel glint from flickering at a corner.
float edge(float d, float width) {
  return (coverage(d) - coverage(d + width)) / max(coverage(d), 0.0001);
}

float catches(float2 n, float2 from) {
  float f = dot(n, from);
  return 0.10 + 0.64 * pow(max(f, 0.0), 2.0) + 0.26 * pow(max(-f, 0.0), 3.0);
}

vec4 main(vec2 xy) {
  float2 p = xy - origin;
  float2 mid = card * 0.5;
  float2 rel = p - mid;
  float d = box(rel, mid, radius);
  float cover = coverage(d);
  float show = clamp(pillLook.y, 0.0, 1.0);
  float press = clamp(pillLook.z, 0.0, 1.0);
  float small = max(min(pill.z, pill.w), 0.0);
  float pr = clamp(pillLook.x, 0.0, small);
  float2 prel = p - pill.xy;

  // Reduce Transparency has no lamps, grain or transition through glass.
  if (opaque > 0.5) {
    if (cover <= 0.0) return vec4(0.0);
    float3 solid = float3(12.0, 12.0, 13.0) / 255.0;
    solid = mix(solid, float3(1.0), 0.12 * edge(d, 1.0));
    if (show > 0.0 && small > 0.0) {
      float pd = box(prel, pill.zw, pr);
      if (coverage(pd) > 0.0) {
        float3 button = mix(float3(28.0, 28.0, 30.0), float3(38.0, 38.0, 40.0), press) / 255.0;
        button = mix(button, float3(1.0), 0.10 * edge(pd, 1.0 / scale));
        solid = mix(solid, button, show * coverage(pd));
      }
    }
    return vec4(solid * cover, cover);
  }

  // The page never evaluates the slab, and the opaque face never evaluates spill.
  float3 color = float3(0.0);
  if (cover < 1.0) color = spill(p, d) * (1.0 - cover);
  if (cover > 0.0) {
    float a = TAU * phase;
    float turn = 0.16 * sin(a);
    float2 from = float2(-0.56 * cos(turn) + 0.83 * sin(turn), -0.56 * sin(turn) - 0.83 * cos(turn));
    float pd = show > 0.0 && small > 0.0 ? box(prel, pill.zw, pr) : 1.0 / scale;
    float pc = coverage(pd);
    float3 glass = float3(0.0);
    // A fully present button hides the slab, including its shadow.
    if (show * pc < 1.0) {
      float depth = max(-d, 0.0);
      float2 n = normal(rel, mid, radius);
      float bend = 46.0 * bezel(depth, 26.0);
      // No veil over it: where there is no light the glass is black, which
      // is what makes it read as clear and not frosted. The ripples are
      // seen through the same bend, so the rim visibly gathers them.
      glass = through(p, n, bend) * 0.94 * (1.0 + ripples(p - n * bend, a));
      // A narrow dark reflection separates the glint from the bent image.
      float band = (depth - 1.8) / 0.95;
      glass *= 1.0 - 0.22 * exp(-band * band);
      float shine = catches(n, from) * (1.0 + 0.25 * flare);
      float corner = 2.0 * abs(n.x * n.y);
      float line = (0.44 + 0.42 * max(dot(n, from), 0.0)) * (1.0 - 0.16 * corner);
      glass += shine * (edge(d, line) + 0.065 * exp(-depth / 3.8));
      // The broad reflection is at most 1.2%, leaving unlit glass dark.
      float2 reflection = (p - card * float2(0.18, 0.10)) / (card * float2(0.65, 0.38));
      glass += 0.012 * exp(-dot(reflection, reflection));

      if (show > 0.0 && small > 0.0) {
        float lift = 1.0 - press;
        float shadowReach = 3.0 + 6.0 * lift;
        float shadowOffset = 0.6 + 2.6 * lift;
        // A press brings the contact shadow back under the button.
        if (pd < shadowReach + shadowOffset) {
          float shadow = box(prel - float2(0.0, shadowOffset), pill.zw, pr);
          glass *= 1.0 - (0.18 + 0.10 * lift) * show * (1.0 - smoothstep(-1.0, shadowReach, shadow));
        }
      }
    }
    if (show > 0.0 && pc > 0.0) {
      float pdepth = max(-pd, 0.0);
      // Finish the bevel before normals meet, even in a small rounded corner.
      float bw = max(min(11.0, min(pr * 0.8, small * 0.65)), 0.01);
      float2 pn = normal(prel, pill.zw, pr);
      float shift = 18.0 * (bw / 11.0) * (1.0 - 0.35 * press) * bezel(pdepth, bw);
      float3 knob = through(p, pn, shift) * (0.92 - 0.04 * press) * (1.0 + ripples(p - pn * shift, a)) + 0.085 - 0.02 * press;
      float pband = (pdepth - 1.5) / 0.8;
      // The dark band disappears as the dot forms, before it can become a ring.
      knob *= 1.0 - 0.22 * smoothstep(6.0, 14.0, small) * exp(-pband * pband);
      // Fade directional reflections before the dot's medial axis as well.
      float fade = 1.0 - smoothstep(bw * 0.55, bw, pdepth);
      float glint = catches(pn, from) * (1.0 - 0.22 * press);
      float pline = (0.42 + 0.38 * max(dot(pn, from), 0.0)) * (1.0 - 0.12 * 2.0 * abs(pn.x * pn.y));
      knob += glint * (0.82 * edge(pd, pline) + 0.09 * exp(-pdepth / 2.8) * fade);
      glass = mix(glass, knob, show * pc);
    }
    color += glass * cover;
  }

  // One stationary noise sample per device pixel. Part of it is added, which
  // hides banding in the dark; part multiplies, a fine tooth in the glass
  // that shows only where light comes through it.
  float peak = max(color.r, max(color.g, color.b));
  float tooth = hash(floor(xy * scale)) - 0.5;
  color = color * (1.0 + 0.07 * tooth * cover) + tooth * (3.0 / 255.0) * smoothstep(0.0, 0.03, peak);
  color = clamp(color, 0.0, 1.0);
  return vec4(color, max(cover, max(color.r, max(color.g, color.b))));
}
`;

const GLASS = Skia.RuntimeEffect.Make(`${LIGHT}
${SLAB}`);
if (!GLASS) throw new Error("Referral card: the glass shader did not compile.");

// One more stop than friends, so the colors wrap from the last to the first.
const loop = (colors: string[]) => [...colors, colors[0]];
const KEY = loop(FRIENDS.map((f) => f.lamp ?? f.color));
const SECOND = loop(FRIENDS.map((f) => f.aura[0]));
const DEEP = loop(FRIENDS.map((f) => f.aura[1]));
const STOPS = KEY.map((_, i) => i);

/** The button's body as the glass draws it, in the card's coordinates. */
export type GlassPill = { x: number; y: number; halfWidth: number; halfHeight: number; radius: number; show: number; press: number };

export function ReferralGlass({
  width,
  height,
  lensY,
  scroll,
  light,
  glow,
  flash,
  pill,
  drift,
}: {
  width: number;
  height: number;
  // The lens centre, from the card's top.
  lensY: number;
  // Which friend is under the lens; the light takes that friend's colors.
  scroll: SharedValue<number>;
  // 0 while the card arrives dark, 1 once its light is on.
  light: DerivedValue<number>;
  glow: SharedValue<number>;
  flash: SharedValue<number>;
  pill: DerivedValue<GlassPill>;
  // Whether the lamps circle. They stand still under Reduce Motion.
  drift: boolean;
}) {
  const phase = useSharedValue(0);
  // Clear until the setting is known: the card arrives dark, so a first
  // frame of glass and a first frame of the plain surface look alike.
  const opaque = useSharedValue(0);
  useEffect(() => {
    if (Platform.OS !== "ios") return;
    let active = true;
    let changed = false;
    const subscription = AccessibilityInfo.addEventListener("reduceTransparencyChanged", (enabled) => {
      changed = true;
      opaque.set(enabled ? 1 : 0);
    });
    void AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => {
      // A newer event must win over the query that was already in flight.
      if (active && !changed) opaque.set(enabled ? 1 : 0);
    }).catch(() => {
      // Without an answer the card stays glass.
    });
    return () => {
      active = false;
      subscription.remove();
    };
  }, [opaque]);
  useEffect(() => {
    if (!drift) return;
    phase.set(0);
    phase.set(withRepeat(withTiming(1, { duration: M.drift, easing: Easing.linear }), -1, false));
    return () => cancelAnimation(phase);
  }, [drift, phase]);

  const colors = useDerivedValue(() => {
    const at = wrapIndex(scroll.get(), FRIENDS.length);
    return [interpolateColors(at, STOPS, KEY), interpolateColors(at, STOPS, SECOND), interpolateColors(at, STOPS, DEEP)];
  });
  const still = useMemo(
    () => ({
      origin: [GLASS_BLEED, GLASS_BLEED],
      card: [width, height],
      radius: S.cardRadius,
      lens: [width / 2, lensY],
      bleed: GLASS_BLEED,
      scale: PixelRatio.get(),
    }),
    [width, height, lensY],
  );
  const uniforms = useDerivedValue(() => {
    const [key, second, deep] = colors.get();
    const b = pill.get();
    return {
      ...still,
      opaque: opaque.get(),
      pill: [b.x, b.y, b.halfWidth, b.halfHeight],
      pillLook: [b.radius, b.show, b.press],
      key: [key[0], key[1], key[2]],
      second: [second[0], second[1], second[2]],
      deep: [deep[0], deep[1], deep[2]],
      phase: phase.get(),
      light: light.get(),
      swell: glow.get(),
      flare: flash.get(),
    };
  });

  return (
    <Canvas
      pointerEvents="none"
      style={[styles.canvas, { width: width + GLASS_BLEED * 2, height: height + GLASS_BLEED * 2 }]}
    >
      <Fill>
        <Shader source={GLASS!} uniforms={uniforms} />
      </Fill>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: { position: "absolute", left: -GLASS_BLEED, top: -GLASS_BLEED },
});
