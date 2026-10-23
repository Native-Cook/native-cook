import { Blur, Group, Paint, Rect, Shader, Skia, rect, rrect } from "@shopify/react-native-skia";
import { useAnimatedReaction, useDerivedValue, useSharedValue } from "react-native-reanimated";
import { M } from "./ember-motion";
import type { EmberMotion } from "./ember-values";
import { E } from "./ember-theme";

// One tube holding light. The two bloom passes draw the same moving fill
// as the sharp pass, then blur its premultiplied color, not a separate oval.
// Colors live in TUBE; there is no second palette of unused theme tokens.
// Only this little rectangle is filtered; the card never enters a layer.
const BAR = E.bar;
const PAD = 48;
const ORIGIN = [{ translateX: E.bleed + BAR.x }, { translateY: E.bleed + BAR.y }];
// This parent clip contains both sibling blur layers at a full fill.
const CARD = rrect(rect(E.bleed, E.bleed, E.size, E.size), E.radius, E.radius);
const BOUNDS = { x: -PAD, y: -PAD, width: BAR.w + PAD * 2, height: BAR.h + PAD * 2 };

const TUBE = Skia.RuntimeEffect.Make(`
uniform float width;
uniform float heat;
uniform float drift;
uniform float bend;
uniform float press;
uniform float fuse;
uniform float ring;
uniform float bloomOnly;
uniform float aimX;

float roundedBox(float2 p, float2 size, float radius) {
  float2 q = abs(p - size * 0.5) - size * 0.5 + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// The tube's share of the ember ramp (OKLCH hue): the fill runs 55 -> 80
// along its length and toward 90 when hot; its glass sits at 52 -> 56.
const float3 FILL_TAIL = float3(0.975, 0.51, 0.06);
const float3 FILL_HEAD = float3(0.985, 0.77, 0.38);
const float3 FILL_HOT = float3(1.0, 0.88, 0.55);
const float3 STRIPE = float3(1.0, 0.89, 0.61);
const float3 GLASS_TOP = float3(0.92, 0.45, 0.005);
const float3 GLASS_FLOOR = float3(1.0, 0.53, 0.005);
const float3 RIM = float3(1.0, 0.70, 0.28);

half4 main(float2 xy) {
  float2 size = float2(${BAR.w}, ${BAR.h});
  // Compress around the tube's centre. Ignition breathes out, then rings
  // back into the measured resting outline, with no path allocation.
  float2 scale = float2(1.0 + 0.008 * press - 0.004 * ring,
                       1.0 - 0.055 * press + 0.065 * ring);
  float2 p = (xy - size * 0.5) / scale + size * 0.5;
  float r = mix(${BAR.r}.0, size.y * 0.5, fuse);
  float trackD = roundedBox(p, size, r);
  float trackA = 1.0 - smoothstep(-0.55, 0.55, trackD);
  float v = p.y / size.y;
  // The middle travels further than the shoulders, like liquid with mass.
  // Fade the bend at the far wall so a full fill and its vessel meet.
  float shoulder = max(0.0, 1.0 - pow((v - 0.5) * 2.0, 2.0));
  float nearHead = smoothstep(width - 44.0, width, p.x);
  float2 liquid = p - float2(bend * shoulder * nearHead, 0.0);
  float fillD = roundedBox(liquid, float2(width, size.y), min(r, width * 0.5));
  float fillA = (1.0 - smoothstep(-0.55, 0.55, fillD)) * trackA;
  float u = clamp(p.x / max(width, 1.0), 0.0, 1.0);
  float hot = smoothstep(0.45, 1.0, u);
  float3 color = mix(FILL_TAIL, FILL_HEAD, u);
  color = mix(color, FILL_HOT, heat * 0.20 + fuse * 0.35);

  // Narrow, softly refracted bands. The cylinder bends their projection a
  // little at its shoulders; 36 % contrast at the cool end softens to
  // 19 % at the hot end, where the reference washes the bands out.
  float stripeX = liquid.x + p.y * 0.38 + sin(v * 3.141593) * 1.4 - drift;
  float phase = fract(stripeX / ${E.stripe.period}.0);
  float band = smoothstep(0.02, 0.10, phase) * (1.0 - smoothstep(0.47, 0.55, phase));
  float depth = 0.72 + 0.28 * sin(clamp(v, 0.0, 1.0) * 3.141593);
  color = mix(color, STRIPE, band * depth * (0.36 - 0.17 * hot) * (1.0 - fuse * 0.8));
  // Keep the original tube lighting in height-relative coordinates.
  float crown = exp(-pow((v - 5.0 / 58.0) / (7.0 / 58.0), 2.0));
  float shade = smoothstep(34.0 / 58.0, 1.0, v);
  float topLight = exp(-pow((p.x - width * 0.65 - aimX * 4.0) / 44.0, 2.0));
  color += float3(0.0, 0.025, 0.045) * crown * (0.6 + 0.4 * topLight);
  color *= 1.0 - 0.045 * shade;

  // The hot two thirds cast light above and below the tube; the cool
  // first third keeps its stripe contrast. Blur this premultiplied source.
  float bloomA = fillA * mix(0.25, 1.0, smoothstep(0.22, 0.65, u));
  if (bloomOnly > 0.5) return half4(half3(color * bloomA), bloomA);

  // The empty chamber takes the same amber, with a soft caustic ahead of
  // the liquid and a little more light caught by the lower wall.
  float ahead = max(p.x - width, 0.0);
  float caustic = exp(-ahead / 33.0) * exp(-pow((v - 43.0 / 58.0) / (22.0 / 58.0), 2.0));
  float floorLight = pow(clamp(v, 0.0, 1.0), 1.25);
  float alpha = (0.13 + 0.46 * floorLight + 0.15 * caustic) * trackA;
  float3 glass = mix(GLASS_TOP, GLASS_FLOOR, floorLight);
  float edge = exp(-pow((trackD + 0.8) / 0.75, 2.0)) * trackA;
  float3 rgb = glass * alpha;
  float rimA = edge * (0.045 + 0.065 * floorLight);
  rgb = RIM * rimA + rgb * (1.0 - rimA);
  alpha = rimA + alpha * (1.0 - rimA);
  rgb = color * fillA + rgb * (1.0 - fillA);
  alpha = fillA + alpha * (1.0 - fillA);
  return half4(half3(rgb), alpha);
}
`);

/** The bar owns its spring; all inputs come from the widget's UI clock. */
export function EmberBar({ charge, glow, progress, drift, time, ignition, slosh, press, aimX, quality, reduced }: EmberMotion) {
  const meniscus = useSharedValue(0);
  const velocity = useSharedValue(0);
  const lastProgress = useSharedValue(-1);
  // Only the owner's active frame callback advances time. Under Reduce
  // Motion that callback is off; this reaction cannot keep a clock alive.
  useAnimatedReaction(
    () => time.get(),
    (now, before) => {
      if (reduced || before === null || now <= before) return;
      const p = progress.get();
      const previous = lastProgress.get();
      lastProgress.set(p);
      if (previous < 0) return;
      const dt = Math.min(0.05, now - before);
      const speed = ((p - previous) * BAR.w) / Math.max(dt, 1 / 240);
      const target = Math.max(-8, Math.min(10, speed * M.bar.drag));
      // Substeps keep the spring stable after a hitch as well as at 120 Hz.
      const steps = Math.ceil(dt * 120);
      const h = dt / steps;
      let x = meniscus.get();
      let v = velocity.get();
      for (let n = 0; n < steps; n++) {
        v += (M.bar.stiffness * (target - x) - M.bar.damping * v) * h;
        x += v * h;
      }
      meniscus.set(Math.abs(x) < 0.001 && Math.abs(v) < 0.01 ? 0 : x);
      velocity.set(v);
    },
  );

  const uniforms = useDerivedValue(() => {
    const t = ignition.get() * M.ignite;
    const lit = !reduced && t > 0;
    const fuse = lit ? Math.min(1, t / M.bar.fuseIn) * Math.exp(-Math.max(0, t - M.bar.fuseIn) / M.bar.fuseOut) : 0;
    const ring = lit ? Math.exp(-M.bar.ringDecay * t) * Math.sin(M.bar.ringFrequency * t) : 0;
    const p = Math.max(0, Math.min(1, progress.get()));
    return {
      width: Math.max(BAR.r * 2, p * BAR.w),
      heat: reduced ? 0 : glow.get(),
      drift: reduced ? 0 : drift.get(),
      bend: reduced ? 0 : (meniscus.get() + slosh.get() * 3) * Math.min(1, (1 - p) * 12),
      press: reduced ? 0 : press.get() * (1 - fuse) + charge.get() * 0.12 * (1 - fuse),
      fuse,
      ring,
      bloomOnly: 0,
      aimX: reduced ? 0 : aimX.get(),
    };
  });
  const bloom = useDerivedValue(() => ({ ...uniforms.get(), bloomOnly: 1 }));
  // 65 % broad / 22 % tight bloom keeps the hot end visible on both sides
  // of the fill, without washing the stripes out in the sharp pass.
  const halo = useDerivedValue(() => 0.65 + (reduced ? 0 : glow.get() * 0.12 + uniforms.get().fuse * 0.25));
  const core = useDerivedValue(() => 0.22 + (reduced ? 0 : uniforms.get().fuse * 0.18));

  if (!TUBE) return null;
  return (
    <Group clip={CARD}>
      <Group transform={ORIGIN} clip={BOUNDS}>
        {quality !== "low" && (
          <Group layer={<Paint opacity={halo}><Blur blur={BAR.bloom} /></Paint>}>
            <Rect {...BOUNDS}><Shader source={TUBE} uniforms={bloom} /></Rect>
          </Group>
        )}
        {quality === "high" && (
          <Group layer={<Paint opacity={core}><Blur blur={BAR.core} /></Paint>}>
            <Rect {...BOUNDS}><Shader source={TUBE} uniforms={bloom} /></Rect>
          </Group>
        )}
        <Rect {...BOUNDS}><Shader source={TUBE} uniforms={uniforms} /></Rect>
      </Group>
    </Group>
  );
}
