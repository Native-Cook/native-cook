import { StyleSheet } from "react-native";
import {
  Canvas,
  Circle,
  Group,
  Path,
  RadialGradient,
  Rect,
  Shader,
  vec,
} from "@shopify/react-native-skia";
import { Easing, ReduceMotion, type SharedValue, useAnimatedReaction, useDerivedValue, useSharedValue, withTiming } from "react-native-reanimated";
import { M, easeOut, seg } from "./ember-motion";
import { EmberBar } from "./ember-bar";
import { EmberHeart, EmberHeartOcclusion } from "./ember-heart";
import { EMBER } from "./ember-shader";
import type { EmberMotion } from "./ember-values";
import { E, countLight, emberGlyph, emberTone as T, type Glyph } from "./ember-theme";

// Everything the widget draws rather than lays out. Three canvases, so the
// copy can sit between them: the glass and its liquid light (one shader),
// the glyphs and the bar on the glass, and the ignition over the copy.

const B = E.bleed;
const BAR_CY = B + E.bar.y + E.bar.h / 2;
const SPARKS = Array.from({ length: 18 }, (_, i) => i);
const MEDIUM_SPARKS = SPARKS.slice(0, 10);
const EMBERS = Array.from({ length: 8 }, (_, i) => i);

function Pictogram({ glyph, x, y, size, color }: { glyph: Glyph; x: number; y: number; size: number; color: string }) {
  return (
    <Group transform={[{ translateX: x }, { translateY: y }, { scale: size / 24 }]}>
      {glyph.fill.map((d) => (
        <Path key={d} path={d} color={color} />
      ))}
      {glyph.stroke.map((d) => (
        <Path key={d} path={d} style="stroke" strokeWidth={glyph.strokeWidth} strokeCap="round" strokeJoin="round" color={color} />
      ))}
    </Group>
  );
}

// The flash: up to full in 60.5 ms, back down over 423.5 ms.
function flashAt(ignition: number) {
  "worklet";
  if (ignition <= 0) return 0;
  return ignition < 0.055 ? ignition / 0.055 : 1 - seg(ignition, 0.055, 0.44);
}

/** The glass card, its light, its border and its halo: one shader over the whole canvas. */
export function EmberGlass({ glow, slosh, ignition, time, progress, press, aimX, aimY, enter, quality, reduced }: EmberMotion) {
  const touching = useSharedValue(false);
  const touchLight = useSharedValue(0);
  const touchSpread = useSharedValue(0);
  // Pressure is already smoothed. Its first falling sample starts the
  // fade, rather than waiting for it to reach zero on the state side.
  useAnimatedReaction(
    () => press.get(),
    (now, before) => {
      if (before === null || now === before) return;
      const down = now > before;
      if (down === touching.get()) return;
      touching.set(down);
      const timing = { easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never };
      if (down) {
        if (touchLight.get() === 0) touchSpread.set(0);
        touchLight.set(1);
        touchSpread.set(reduced ? 0 : withTiming(1, { ...timing, duration: 240 }));
      } else {
        touchLight.set(withTiming(0, { ...timing, duration: 320 }));
      }
    },
  );
  const uniforms = useDerivedValue(() => {
    const heat = glow.get();
    return {
      size: [E.size, E.size],
      bleed: B,
      radius: E.corner,
      time: reduced ? 0 : time.get(),
      aim: reduced ? [0, 0] : [aimX.get(), aimY.get()],
      touchLight: touchLight.get(),
      touchRadius: reduced ? 60 : 60 + 90 * touchSpread.get(),
      // J. The bevel and lensing grow with the card's own fade.
      materialise: reduced ? 1 : enter.get(),
      quality: quality === "high" ? 2 : quality === "medium" ? 1 : 0,
      rise: M.rise.rest + (M.rise.hot - M.rise.rest) * heat,
      heat,
      slosh: reduced ? 0 : slosh.get(),
      // The wave crosses the card in the first 800 ms of ignition.
      ripple: reduced ? 0 : seg(ignition.get(), 0, 0.73),
      origin: [E.bar.x + Math.max(E.bar.h / 2, progress.get() * E.bar.w), E.bar.y + E.bar.h / 2],
      flash: reduced ? 0 : flashAt(ignition.get()),
      // Slosh rotates the lights up to 0.35 rad; pressure adds 0.08 rad.
      // Both freeze at their resting angle under Reduce Motion.
      lightLean: reduced || quality !== "high" ? 0 : Math.max(-0.35, Math.min(0.35, slosh.get() * 2.4)) + press.get() * 0.08,
      sweep: reduced || quality !== "high" ? 0 : seg(ignition.get() * M.ignite, 0, 0.5),
    };
  });
  return (
    <Canvas style={styles.canvas}>
      <Rect x={0} y={0} width={E.size + B * 2} height={E.size + B * 2}>
        {EMBER && <Shader source={EMBER} uniforms={uniforms} />}
      </Rect>
    </Canvas>
  );
}

/** What sits on the glass under the copy: the glyphs, the bar and the heart. */
export function EmberLight(motion: EmberMotion) {
  const discR = E.disc.d / 2;
  const discX = B + E.disc.x + discR;
  const discY = B + E.disc.y + discR;

  return (
    <Canvas style={styles.canvas}>
      {/* Header: the walking figure on a glass disc. */}
      <Circle cx={discX} cy={discY} r={discR}>
        <RadialGradient c={vec(discX, discY - 2)} r={discR + 2} colors={[T.discTop, T.discBottom]} />
      </Circle>
      {/* The v2 silhouette spans 22.3 grid points: 29 on the grid gives 27 pt
          of ink. Half a point left and up balances the forward arm and feet. */}
      <Pictogram glyph={emberGlyph.walk} x={discX - 15} y={discY - 15} size={29} color={T.ink} />

      <Pictogram glyph={emberGlyph.route} x={B + E.metrics.x[0]} y={B + E.metrics.glyphTop} size={E.metrics.glyph} color={T.sub} />
      <Pictogram glyph={emberGlyph.clock} x={B + E.metrics.x[1]} y={B + E.metrics.glyphTop} size={E.metrics.glyph} color={T.sub} />
      <Pictogram glyph={emberGlyph.bolt} x={B + E.metrics.x[2]} y={B + E.metrics.glyphTop} size={E.metrics.glyph} color={T.sub} />

      <EmberBar {...motion} />

      <EmberHeart {...motion} />
    </Canvas>
  );
}

// An ember thrown from the bar's head at ignition: a fixed direction and
// speed per index, then gravity.
function Spark({ index, headX, ignition }: { index: number; headX: SharedValue<number>; ignition: SharedValue<number> }) {
  const angle = (-(20 + ((index * 53) % 140)) * Math.PI) / 180;
  const speed = 120 + (((index * 37) % 100) / 100) * 240;
  const life = 0.7 + (((index * 29) % 100) / 100) * 0.4;
  const age = useDerivedValue(() => ignition.get() * M.ignite);
  const cx = useDerivedValue(() => headX.get() + Math.cos(angle) * speed * age.get());
  const cy = useDerivedValue(() => BAR_CY + Math.sin(angle) * speed * age.get() + 260 * age.get() * age.get());
  const r = useDerivedValue(() => 3.5 * Math.max(0, 1 - age.get() / life));
  const live = useDerivedValue(() => ignition.get() > 0 ? countLight(cx.get() - B, cy.get() - B) : 0);
  const cooled = useDerivedValue(() => seg(age.get(), 0, life));
  const hot = useDerivedValue(() => 1 - cooled.get());
  return (
    <Group opacity={live}>
      <Circle cx={cx} cy={cy} r={r} color={T.spark} opacity={hot} />
      <Circle cx={cx} cy={cy} r={r} color={T.sparkCool} opacity={cooled} />
    </Group>
  );
}

// A faint ember rising off the bloom while the card is charged.
function Ember({ index, charge, time }: { index: number; charge: SharedValue<number>; time: SharedValue<number> }) {
  const baseX = E.embers.x + index * E.embers.pitch;
  const life = useDerivedValue(() => (time.get() * 0.55 + index * 0.137) % 1);
  const cx = useDerivedValue(() => B + baseX + Math.sin(time.get() * 1.7 + index * 2.1) * 7);
  const cy = useDerivedValue(() => B + E.count.top + E.count.h + E.embers.below - life.get() * 190);
  const opacity = useDerivedValue(() => seg(charge.get(), 0.2, 0.7) * Math.sin(Math.PI * life.get()) * 0.8 * countLight(cx.get() - B, cy.get() - B));
  return <Circle cx={cx} cy={cy} r={1.6} color={T.wave} opacity={opacity} />;
}

/** What sits above the copy: rising embers, the wave's leading edge and the sparks. */
export function EmberEffects({ charge, ignition, time, progress, phase, quality, reduced }: EmberMotion) {
  const headX = useDerivedValue(() => B + E.bar.x + Math.max(E.bar.h / 2, progress.get() * E.bar.w) - 2);
  // The wave itself runs through the liquid (see the shader); this is only
  // its bright leading edge in the air, 0 → 260 pt over 640 ms.
  const wave = useDerivedValue(() => seg(ignition.get(), 0, 0.58));
  const waveR = useDerivedValue(() => 260 * easeOut(wave.get()));
  const waveWidth = useDerivedValue(() => 3 - 2.5 * wave.get());
  const waveOpacity = useDerivedValue(() => (ignition.get() > 0 && wave.get() < 1 ? 0.55 * (1 - wave.get()) : 0));
  if (quality === "low" || reduced) return null;
  return (
    <Canvas style={styles.canvas} pointerEvents="none">
      <EmberHeartOcclusion phase={phase} reduced={reduced}>
        {EMBERS.map((index) => (
          <Ember key={index} index={index} charge={charge} time={time} />
        ))}
        <Circle cx={headX} cy={BAR_CY} r={waveR} style="stroke" strokeWidth={waveWidth} color={T.wave} opacity={waveOpacity} />
        {(quality === "medium" ? MEDIUM_SPARKS : SPARKS).map((index) => (
          <Spark key={index} index={index} headX={headX} ignition={ignition} />
        ))}
      </EmberHeartOcclusion>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: { width: E.size + B * 2, height: E.size + B * 2 },
});
