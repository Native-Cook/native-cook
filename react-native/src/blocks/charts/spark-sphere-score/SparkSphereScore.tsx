import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  PixelRatio,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type AccessibilityActionEvent,
  type TextStyle,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useFonts } from 'expo-font';
import { BlurMask, Canvas, Fill, Group, Path, Rect, RoundedRect, Shader, Skia, Text as SkText, useFont } from '@shopify/react-native-skia';
import type { SkFont } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { isWorkletFunction, scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { BANDS, COPY, comp, hexRgb, makeLayout, ref, topForBaseline, type SssBand, type SssThemeTokens } from './sss-theme';
import {
  bandOf,
  clamp,
  coldOf,
  colourAt,
  DEFAULT_PALETTE,
  GESTURE,
  LOOP,
  mix,
  paletteTable,
  PULSE,
  rubberPt,
  scoreOf,
  smoothstep,
  SCORE,
  SPRINGS,
  V,
} from './sss-motion';
import { PH, useSphereEngine } from './sss-engine';
import { haptic } from './sss-haptics';
import { DISC_SKSL, GROUND_SKSL, RULER_SKSL } from './sss-shaders';

export { LOOP };

const TICK_RGB = hexRgb(ref.color.tick);
const MARK_RGB = hexRgb(ref.color.tickMark);
const DISC_FX = Skia.RuntimeEffect.Make(DISC_SKSL);
const RULER_FX = Skia.RuntimeEffect.Make(RULER_SKSL);
const GROUND_FX = Skia.RuntimeEffect.Make(GROUND_SKSL);

type SssDevHook = {
  pin: (t: number) => number;
  at: (t: number) => number;
  play: () => number;
  progress: (v: number) => number;
  state: () => object;
  LOOP: number;
};
declare global {
  var __sss: SssDevHook | undefined;
}

export type SparkSphereScoreProps = {
  /** Controlled reading, in x (the sphere's rest pace). */
  value?: number;
  /** Uncontrolled starting reading. Default 0.8. */
  defaultValue?: number;
  /** Default −1, 3 and 0.05. */
  min?: number;
  max?: number;
  step?: number;
  /** Observe or drive the what-if pace on the UI thread. */
  scrub?: SharedValue<number>;
  /** true (default): an explorer, the pace returns to the reading on release; false: an input, it stays. */
  snapBack?: boolean;
  onScrubStart?: () => void;
  /** JS thread, at most 10 Hz. */
  onScrub?: (v: number) => void;
  /** On release when `snapBack` is false. */
  onChange?: (v: number) => void;
  /** The score is `base + perUnit × (pace − 1)`: `base` is what you would read at a pace of 1.0x (your calendar age). */
  score?: { base: number; perUnit: number; decimals?: number; label?: string };
  /** Words after the difference to `base`. */
  delta?: { better: string; worse: string };
  bands?: readonly { from: number; title: string; body: string }[];
  /** Worklet: pace → label. Default one decimal and an x. */
  format?: (v: number) => string;
  /** Beats per minute of the membrane's pulse, rounded to whole beats per 8 s loop; 60 (default) is eight. */
  pulseBpm?: number;
  /** false: the sphere and the ruler ignore touch (the readout stays). */
  touchable?: boolean;
  /** Pace → colour stops, '#RRGGBB'. */
  palette?: readonly { at: number; color: string }[];
  tokens?: SssThemeTokens;
  /** The three chrome controls render as buttons only when you give them an action. */
  onBack?: () => void;
  onInfo?: () => void;
  onPreviousWeek?: () => void;
  autoPlay?: boolean;
};

const DEFAULT_FORMAT = (v: number) => {
  'worklet';
  return `${v.toFixed(1)}x`;
};

// ---- static art --------------------------------------------------------------------------------------------------------------
function buildBlob(cx: number, cy: number, d: number, h: number): ReturnType<typeof Skia.Path.Make> {
  const pb = Skia.PathBuilder.Make();
  const n = 28;
  for (let i = 0; i <= n; i++) {
    const a = (i / n) * Math.PI * 2;
    const r = 1 + 0.16 * Math.cos(2 * a + 0.6) + 0.1 * Math.cos(3 * a + 1.4) + 0.05 * Math.cos(5 * a);
    const x = cx + (d / 2) * r * Math.cos(a);
    const y = cy + (h / 2) * r * Math.sin(a);
    if (i === 0) pb.moveTo(x, y);
    else pb.lineTo(x, y);
  }
  pb.close();
  return pb.build();
}
function polyline(pts: [number, number][]) {
  const pb = Skia.PathBuilder.Make();
  pts.forEach((p, i) => (i === 0 ? pb.moveTo(p[0], p[1]) : pb.lineTo(p[0], p[1])));
  return pb.build();
}

// ---- the sphere alone ----------------------------------------------------------------------------------------------------------
export type SparkSphereProps = {
  /** −0.6 crystal … 0.12 rest … 1 gas. Written by you on the UI thread. */
  temperature: SharedValue<number>;
  /** Angular velocity added to the sparks, rad/s. */
  stir?: SharedValue<number>;
  /** Side of the square the sphere sits in; the disc is 0.69 of it. */
  size: number;
  palette?: readonly { at: number; color: string }[];
  /** Drawn in the hollow. */
  children?: ReactNode;
  autoPlay?: boolean;
  touchable?: boolean;
};

/** The living disc without the screen around it: temperature and stir in, children in the hollow (AI "thinking" states, other scores). */
export function SparkSphere({ temperature, stir, size, palette, children, autoPlay = false, touchable = true }: SparkSphereProps) {
  const reduce = useReducedMotionPreference();
  const scale = size / (2 * 163.6 * 1.45);
  const cx = size / 2;
  const cy = size / 2;
  const v = useSharedValue<number>(V.rest);
  const reading = useSharedValue<number>(V.rest);
  const pal = useMemo(() => (palette ? paletteTable(palette) : DEFAULT_PALETTE), [palette]);
  const eng = useSphereEngine({
    v,
    reading,
    autoPlay: autoPlay && !reduce,
    reduceMotion: reduce,
    geo: { cx, cy, scale, px: PixelRatio.get(), p3: Platform.OS === 'ios' ? 1 : 0 },
    palette: pal,
  });
  useAnimatedReaction(
    () => temperature.get(),
    (t) => {
      if (eng.phase.get() === PH.AUTO) return;
      v.set(0.5 + 2.5 * t);
      reading.set(0.5 + 2.5 * t);
    },
  );
  useAnimatedReaction(
    () => (stir ? stir.get() : 0),
    (s) => {
      eng.stirIn.set(s);
    },
  );
  const R = 163.6 * scale;
  const probe = useDiscGestures(eng, { cx, cy, R, scale }, touchable, reading, v);
  return (
    <View style={{ width: size, height: size }} accessible accessibilityRole="image" accessibilityLabel="Spark sphere">
      <Canvas style={StyleSheet.absoluteFill} accessible={false}>
        {DISC_FX ? (
          <Rect x={cx - 1.5 * R} y={cy - 1.5 * R} width={3 * R} height={3 * R}>
            <Shader source={DISC_FX} uniforms={eng.discU} />
          </Rect>
        ) : null}
      </Canvas>
      <GestureDetector gesture={probe}>
        <View style={{ position: 'absolute', left: cx - R, top: cy - R, width: 2 * R, height: 2 * R }} />
      </GestureDetector>
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center' }]}>
        {children}
      </View>
    </View>
  );
}

/** A finger on the disc is a warm probe: it never changes the value, it agitates the sparks, dents the membrane and spins the shell. */
function useDiscGestures(
  eng: ReturnType<typeof useSphereEngine>,
  g: { cx: number; cy: number; R: number; scale: number },
  touchable: boolean,
  reading: SharedValue<number>,
  v: SharedValue<number>,
) {
  const { R, scale } = g;
  const downAt = useSharedValue(0);
  const phaseIn = eng.phase;
  return useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .enabled(touchable)
        .onBegin((e) => {
          const t = eng.time.get();
          downAt.set(t);
          eng.touchedAt.set(t);
          if (phaseIn.get() === PH.AUTO) {
            // hand the script's values to the live state, then let the cell cool back to its reading
            phaseIn.set(PH.RETURN);
            eng.pulseT0.set(t - eng.pulseAge.get());
            v.set(withSpring(reading.get(), SPRINGS.cool));
          }
          const lx = e.x - R;
          const ly = e.y - R;
          eng.probeX.set(lx / scale);
          eng.probeY.set(ly / scale);
          eng.probeHeat.set(withSpring(1, SPRINGS.glide));
          scheduleOnRN(haptic.probe);
        })
        .onUpdate((e) => {
          const lx = e.x - R;
          const ly = e.y - R;
          eng.probeX.set(lx / scale);
          eng.probeY.set(ly / scale);
          const r = Math.sqrt(lx * lx + ly * ly);
          const rim = R - r;
          const near = rim < GESTURE.dimpleReach * scale && r > R * 0.5;
          eng.dimple.set(withSpring(near ? GESTURE.dimple / 163.6 : 0, SPRINGS.dimple));
          if (near) eng.dimpleAngle.set(Math.atan2(ly, lx));
          // a circular swipe spins the shell: tangential velocity → angular velocity
          const vt = r > 8 ? (lx * e.velocityY - ly * e.velocityX) / r : 0;
          eng.stirIn.set(clamp(GESTURE.stirPerPt * vt, -GESTURE.stirMax, GESTURE.stirMax));
        })
        .onFinalize((e) => {
          const t = eng.time.get();
          eng.touchedAt.set(t);
          eng.probeHeat.set(withTiming(0, { duration: 600 }));
          eng.dimple.set(withSpring(0, SPRINGS.dimple));
          const still = Math.abs(e.translationX) < 8 && Math.abs(e.translationY) < 8 && t - downAt.get() < 0.35;
          if (still && coldOf(eng.vRim.get()) > 0.5) {
            eng.tapT0.set(t);
            scheduleOnRN(haptic.tap);
          }
        }),
    [touchable, R, scale, eng, downAt, phaseIn, reading, v],
  );
}

// ---- the odometer ----------------------------------------------------------------------------------------------------------------
type SlotProps = {
  font: SkFont | null;
  s: SharedValue<number>;
  k: number;
  x: SharedValue<number>;
  slotW: number;
  baseline: number;
  cell: number;
  colour: string;
  widths: Record<string, number>;
  reduce: boolean;
  vis?: SharedValue<number>;
};
/** One digit of the score. The position runs continuously: tenths scroll with the value, the others glide when the digit below is about to roll over. */
function OdometerSlot({ font, s, k, x, slotW, baseline, cell, colour, widths, reduce, vis }: SlotProps) {
  const pos = useDerivedValue(() => {
    const sc = s.get();
    const q = k === 0 ? sc * 10 : k === 1 ? sc : sc / 10;
    if (reduce) return Math.round(q);
    const fl = Math.floor(q);
    return k === 0 ? q : fl + smoothstep(1 - Math.pow(0.1, k), 1, q - fl);
  });
  // One legible digit at a time: a unit rests, then changes in its last 30 % (the higher
  // places already glide only near a carry). The old digit lifts, blurs and fades in the
  // first 60 % of the change, the new one rises, sharpens and fades in over the last 60 %.
  const frac = useDerivedValue(() => {
    const f = pos.get() - Math.floor(pos.get());
    return k === 0 ? smoothstep(0.7, 1, f) : f;
  });
  const travel = cell * 0.45;
  const dCur = useDerivedValue(() => String(((Math.floor(pos.get() + 1e-6) % 10) + 10) % 10));
  const dNext = useDerivedValue(() => String((((Math.floor(pos.get() + 1e-6) + 1) % 10) + 10) % 10));
  const xCur = useDerivedValue(() => x.get() + (slotW - (widths[dCur.get()] ?? slotW)) / 2);
  const xNext = useDerivedValue(() => x.get() + (slotW - (widths[dNext.get()] ?? slotW)) / 2);
  const yCur = useDerivedValue(() => baseline - frac.get() * travel);
  const yNext = useDerivedValue(() => baseline + (1 - frac.get()) * travel);
  const aCur = useDerivedValue(() => (1 - smoothstep(0, 0.6, frac.get())) * (vis ? vis.get() : 1));
  const aNext = useDerivedValue(() => smoothstep(0.4, 1, frac.get()) * (vis ? vis.get() : 1));
  const bCur = useDerivedValue(() => Math.max(0.001, frac.get() * cell * 0.07));
  const bNext = useDerivedValue(() => Math.max(0.001, (1 - frac.get()) * cell * 0.07));
  if (!font) return null;
  if (reduce) return <SkText x={xCur} y={baseline} text={dCur} font={font} color={colour} opacity={aCur} />;
  return (
    <>
      <SkText x={xCur} y={yCur} text={dCur} font={font} color={colour} opacity={aCur}>
        <BlurMask blur={bCur} style="normal" />
      </SkText>
      <SkText x={xNext} y={yNext} text={dNext} font={font} color={colour} opacity={aNext}>
        <BlurMask blur={bNext} style="normal" />
      </SkText>
    </>
  );
}

const CHARS = '0123456789.,-+x%:abcdefghijklmnopqrstuvwxyz ';
function glyphWidths(font: SkFont | null): Record<string, number> {
  const out: Record<string, number> = {};
  if (!font) return out;
  const ids = font.getGlyphIDs(CHARS);
  const ws = font.getGlyphWidths(ids);
  for (let i = 0; i < CHARS.length; i++) out[CHARS[i]] = ws[i] ?? 0;
  return out;
}
function textWidth(font: SkFont | null, text: string): number {
  if (!font) return 0;
  const ids = font.getGlyphIDs(text);
  return font.getGlyphWidths(ids).reduce((a, b) => a + b, 0);
}

// ---- the screen ----------------------------------------------------------------------------------------------------------------------
export function SparkSphereScore({
  value,
  defaultValue,
  min = V.min,
  max = V.max,
  step = V.step,
  scrub,
  snapBack = true,
  onScrubStart,
  onScrub,
  onChange,
  score,
  delta,
  bands = BANDS,
  format,
  pulseBpm = 60,
  touchable = true,
  palette,
  tokens,
  onBack,
  onInfo,
  onPreviousWeek,
  autoPlay = false,
}: SparkSphereScoreProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const fontScale = PixelRatio.getFontScale();
  const L = useMemo(
    () => makeLayout(width, height, insets.top, insets.bottom, fontScale),
    [width, height, insets.top, insets.bottom, fontScale],
  );
  const S = L.S;
  const p3 = Platform.OS === 'ios' ? 1 : 0;
  const px = PixelRatio.get();
  const [fontsReady] = useFonts({
    SssFigtreeMedium: require('./assets/fonts/Figtree-Medium.ttf'),
    SssFigtreeSemi: require('./assets/fonts/Figtree-SemiBold.ttf'),
    SssFigtreeBold: require('./assets/fonts/Figtree-Bold.ttf'),
    SssBarlowSemi: require('./assets/fonts/BarlowSemiCondensed-SemiBold.ttf'),
    SssBarlowBold: require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'),
  });
  const cfg = useMemo(
    () => ({ base: score?.base ?? SCORE.chrono, perUnit: score?.perUnit ?? SCORE.perUnit }),
    [score?.base, score?.perUnit],
  );
  const scoreLabel = score?.label ?? COPY.scoreLabel;
  const words = delta ?? { better: COPY.younger, worse: COPY.older };
  const pal = useMemo(() => (palette ? paletteTable(palette) : DEFAULT_PALETTE), [palette]);
  const fmt = useMemo(() => (format && isWorkletFunction(format) ? format : DEFAULT_FORMAT), [format]);
  const C = useMemo(
    () => ({
      ground0: tokens?.ground ?? ref.color.ground0,
      ground1: tokens?.ground ?? ref.color.ground1,
      ink: tokens?.ink ?? ref.color.ink,
      mute: tokens?.mute ?? ref.color.mute,
      card: tokens?.card ?? ref.color.card,
    }),
    [tokens?.ground, tokens?.ink, tokens?.mute, tokens?.card],
  );

  // ---- state ----
  const [own, setReading] = useState(defaultValue ?? V.rest);
  const reading = value ?? own;
  const readingSV = useSharedValue<number>(reading);
  const vOwn = useSharedValue<number>(reading);
  const v = scrub ?? vOwn;
  const eng = useSphereEngine({
    v,
    reading: readingSV,
    autoPlay: autoPlay && !reduce,
    reduceMotion: reduce,
    geo: { cx: L.disc.cx, cy: L.disc.cy, scale: L.disc.scale, px, p3 },
    palette: pal,
    beats: (pulseBpm / 60) * LOOP,
    initialV: reading,
  });
  const bandFrom = useMemo(
    () => (bands.length ? bands : BANDS).slice(0, 6).map((b) => (Number.isFinite(b.from) ? b.from : -1000)),
    [bands],
  );
  const initialBand = bandOf(reading, bandFrom);
  const lastDetent = useSharedValue(0);
  const edgeHit = useSharedValue(0);
  const fingerOffset = useSharedValue(0);
  const scrubTick = useSharedValue(0);
  const bandA = [
    useSharedValue(initialBand === 0 ? 1 : 0),
    useSharedValue(initialBand === 1 ? 1 : 0),
    useSharedValue(initialBand === 2 ? 1 : 0),
    useSharedValue(initialBand === 3 ? 1 : 0),
    useSharedValue(initialBand === 4 ? 1 : 0),
    useSharedValue(initialBand === 5 ? 1 : 0),
  ];
  const [a11yReading, setA11yReading] = useState(reading);

  useEffect(() => {
    if (value === undefined) return;
    readingSV.set(value);
    if (eng.phase.get() !== PH.AUTO && eng.phase.get() !== PH.DRAG) v.set(withSpring(value, SPRINGS.cool));
  }, [value, readingSV, v, eng.phase]);

  // the discovery nudge: 1.4 s after mount the cursor slides three ticks right and back (only when nothing plays)
  useEffect(() => {
    if (autoPlay || reduce || !touchable) return;
    v.set(
      withDelay(
        GESTURE.hint.at * 1000,
        withSequence(withTiming(reading + GESTURE.hint.ticks * 0.05, { duration: 420 }), withTiming(reading, { duration: 620 })),
      ),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- dev hook ----
  useEffect(() => {
    if (!__DEV__) return;
    globalThis.__sss = {
      pin: (t: number) => {
        eng.pinV.set(-99);
        eng.pinT.set(t);
        return t;
      },
      at: (t: number) => {
        eng.pinV.set(-99);
        eng.pinT.set(t);
        return t;
      },
      play: () => {
        eng.pinT.set(-1);
        eng.pinV.set(-99);
        eng.cyc.set(0);
        eng.phase.set(autoPlay ? PH.AUTO : PH.REST);
        return 1;
      },
      progress: (x: number) => {
        eng.pinT.set(-1);
        eng.phase.set(PH.REST);
        eng.swirl.set(0);
        eng.agit.set(0);
        eng.pinV.set(x);
        return x;
      },
      state: () => ({ phase: eng.phase.get(), v: v.get(), cyc: eng.cyc.get() }),
      LOOP,
    };
    return () => {
      delete globalThis.__sss;
    };
  }, [eng, v, autoPlay]);

  // ---- geometry helpers (numbers only; worklets capture these) ----
  const x0 = L.ruler.x0;
  const pitch = L.ruler.pitch;
  const xMax = x0 + ((V.max - V.min) / V.step) * pitch;
  const cursorBaseY = L.cursorBase;
  const cardX = L.card.x;
  const cardW = L.card.w;
  const notchHalf = L.card.notchHalf;
  const notchH = L.card.notchH;
  const notchOffset = comp.card.notchOffset * S;
  const discCx = L.disc.cx;
  const cardTop = L.card.top;
  const rulerCy = L.ruler.cy;
  const tickH = L.ruler.tickH;
  const tickW = L.ruler.tickW;
  const cursorW = L.ruler.cursorW;
  const cursorH = L.ruler.cursorH;
  // "Slow" ends near x 66 and "Fast" starts near x 336 (pin units); 4–16 pt of clear air.
  const slowFrom = L.x(70);
  const slowTo = L.x(82);
  const fastFrom = L.x(320);
  const fastTo = L.x(332);
  const labelMinX = L.x(16);
  const labelMaxX = L.x(386);

  const cursorX = useDerivedValue(() => x0 + ((v.get() - V.min) / V.step) * pitch);
  const cardNotchX = useDerivedValue(() =>
    clamp(x0 + ((eng.vCard.get() - V.min) / V.step) * pitch + notchOffset, cardX + 30 * S, cardX + cardW - 30 * S),
  );

  // ---- fonts for the canvas ----
  const fScore = useFont(require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'), ref.type.score.size * L.ts * L.sc);
  const fDeltaNum = useFont(require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'), ref.type.deltaNum.size * L.ts * L.sc);
  const fDeltaWord = useFont(require('./assets/fonts/BarlowSemiCondensed-SemiBold.ttf'), ref.type.deltaText.size * L.ts * L.sc);
  const fCursor = useFont(require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'), ref.type.cursor.size * S);
  const fCursorX = useFont(require('./assets/fonts/BarlowSemiCondensed-Bold.ttf'), ref.type.cursor.size * S * 0.78);
  const wScore = useMemo(() => glyphWidths(fScore), [fScore]);
  const wDelta = useMemo(() => glyphWidths(fDeltaNum), [fDeltaNum]);
  const wCursor = useMemo(() => glyphWidths(fCursor), [fCursor]);
  const wCursorUnit = useMemo(() => glyphWidths(fCursorX), [fCursorX]);
  const wYounger = useMemo(() => textWidth(fDeltaWord, words.better), [fDeltaWord, words.better]);
  const wOlder = useMemo(() => textWidth(fDeltaWord, words.worse), [fDeltaWord, words.worse]);
  const dotW = wScore['.'] ?? 10;
  const slotW = wScore['0'] ?? 22;
  const scoreSize = ref.type.score.size * L.ts * L.sc;

  // ---- derived channels ----
  const scoreSV = useDerivedValue(() => scoreOf(eng.vScore.get(), cfg));
  const tensVis = useDerivedValue(() => smoothstep(9.0, 9.95, scoreSV.get()));
  const scoreBase = L.centreBase(comp.centre.scoreBase);
  const deltaBaseY = L.centreBase(comp.centre.deltaBase);
  const gLeft = useDerivedValue(() => discCx - (tensVis.get() * slotW + slotW + dotW + slotW) / 2);
  const xTens = gLeft;
  const xOnes = useDerivedValue(() => gLeft.get() + tensVis.get() * slotW);
  const xDot = useDerivedValue(() => xOnes.get() + slotW);
  const xTenths = useDerivedValue(() => xDot.get() + dotW);
  const cell = scoreSize * 0.62;
  const deltaV = useDerivedValue(() => scoreOf(eng.vCard.get(), cfg) - cfg.base);
  const deltaStr = useDerivedValue(() => Math.abs(deltaV.get()).toFixed(1));
  const older = useDerivedValue(() => deltaV.get() > 0.049);
  const deltaWord = useDerivedValue(() => (older.get() ? words.worse : words.better));
  const deltaNumW = useDerivedValue(() => {
    const s = deltaStr.get();
    let w = 0;
    for (let i = 0; i < s.length; i++) w += wDelta[s[i]] ?? 0;
    return w;
  });
  const gap = 4 * L.ts * L.sc;
  const deltaTotal = useDerivedValue(() => deltaNumW.get() + gap + (older.get() ? wOlder : wYounger));
  const xDeltaNum = useDerivedValue(() => discCx - deltaTotal.get() / 2);
  const xDeltaWord = useDerivedValue(() => xDeltaNum.get() + deltaNumW.get() + gap);
  const deltaColour = useDerivedValue(() => {
    const c = [0, 0, 0];
    colourAt(eng.vCard.get(), c, pal);
    return `rgba(${Math.round(c[0] * 255)},${Math.round(c[1] * 255)},${Math.round(c[2] * 255)},1)`;
  });
  const cursorLabel = useDerivedValue(() => fmt(clamp(v.get(), min, max)));
  // the number in full size, a trailing unit (the pin's "x") smaller
  const cursorNum = useDerivedValue(() => cursorLabel.get().replace(/[A-Za-z%]+$/, ''));
  const cursorUnit = useDerivedValue(() => cursorLabel.get().slice(cursorNum.get().length));
  const cursorNumW = useDerivedValue(() => {
    const s = cursorNum.get();
    let w = 0;
    for (let i = 0; i < s.length; i++) w += wCursor[s[i]] ?? 0;
    return w;
  });
  const cursorUnitW = useDerivedValue(() => {
    const s = cursorUnit.get();
    let w = 0;
    for (let i = 0; i < s.length; i++) w += wCursorUnit[s[i]] ?? 0;
    return w;
  });
  // The label follows the cursor but stays inside the 16 pt page margins.
  const labelHalf = useDerivedValue(() => ((cursorNumW.get() + cursorUnitW.get()) / 2) * (1 + 0.08 * eng.grab.get()));
  const labelCX = useDerivedValue(() => clamp(cursorX.get(), labelMinX + labelHalf.get(), labelMaxX - labelHalf.get()));
  const cursorLabelX = useDerivedValue(() => labelCX.get() - (cursorNumW.get() + cursorUnitW.get()) / 2);
  const cursorUnitX = useDerivedValue(() => cursorLabelX.get() + cursorNumW.get());
  const cursorLabelXf = useDerivedValue(() => {
    const gr = eng.grab.get();
    return [
      { translateX: labelCX.get() },
      { translateY: cursorBaseY },
      { scale: 1 + 0.08 * gr },
      { translateY: -cursorBaseY },
      { translateX: -labelCX.get() },
    ];
  });
  const cardPath = useDerivedValue(() => {
    const nx = cardNotchX.get();
    const top = cardTop;
    const pb = Skia.PathBuilder.Make();
    pb.moveTo(nx - notchHalf, top + 1);
    pb.lineTo(nx - 1.8, top - notchH + 2.2);
    pb.quadTo(nx, top - notchH - 0.4, nx + 1.8, top - notchH + 2.2);
    pb.lineTo(nx + notchHalf, top + 1);
    pb.close();
    return pb.build();
  });

  const rulerU = useDerivedValue(() => {
    const age = eng.pulseAge.get();
    const rr = age < 0.5 ? age * PULSE.ripple.speed : -1;
    const rip = age < 0.5 ? PULSE.ripple.amount * Math.exp(-age / 0.2) : 0;
    const c = [0, 0, 0];
    colourAt(eng.vFilm.get(), c, pal);
    const tint = smoothstep(0.08, 0.45, Math.abs(eng.vFilm.get() - V.rest));
    const neutral = [0.93, 0.95, 0.96];
    return {
      uBox: [x0, pitch, rulerCy, tickH],
      uTick: [tickW, cursorW, cursorH, cursorX.get()],
      uMore: [1, eng.grab.get(), rr, rip],
      uCol: [mix(neutral[0], c[0], tint), mix(neutral[1], c[1], tint), mix(neutral[2], c[2], tint), 1],
      uInk: [TICK_RGB[0], TICK_RGB[1], TICK_RGB[2], 0],
      uMark: [MARK_RGB[0], MARK_RGB[1], MARK_RGB[2], p3],
    };
  });

  // ---- the card's copy: a time-based cross-fade at the band boundaries (no layout shift: the card is as tall as its longest copy) ----
  useAnimatedReaction(
    () => bandOf(eng.vCard.get(), bandFrom),
    (b, prev) => {
      if (b === prev && prev !== null) return;
      for (let i = 0; i < bandA.length; i++) {
        const target = i === b ? 1 : 0;
        const inst = prev === null || eng.pinT.get() >= 0 || eng.pinV.get() > -50 || reduce;
        const a = bandA[i];
        a.set(inst ? target : withTiming(target, { duration: reduce ? 200 : 160 }));
      }
    },
  );
  // the label for assistive tech settles with the sphere
  const updateA11y = useCallback((x: number) => setA11yReading(x), []);
  useAnimatedReaction(
    () => (eng.phase.get() === PH.REST ? Math.round(v.get() * 20) / 20 : -99),
    (cur, prev) => {
      if (cur > -50 && cur !== prev) scheduleOnRN(updateA11y, cur);
    },
  );

  // ---- the ruler's finger ----
  const toPace = (xf: number) => {
    'worklet';
    let xc = xf;
    if (xf < x0) xc = x0 - rubberPt(x0 - xf);
    else if (xf > xMax) xc = xMax + rubberPt(xf - xMax);
    return V.min + ((xc - x0) / pitch) * V.step;
  };
  const onSettle = useCallback(() => haptic.settle(), []);
  // pass stable callbacks (useCallback) for onChange / onScrub / onScrubStart: a new function rebuilds the gesture
  const commit = useCallback(
    (x: number) => {
      setReading(x);
      onChange?.(x);
    },
    [onChange],
  );
  const scrubCb = useCallback((x: number) => onScrub?.(x), [onScrub]);
  const startCb = useCallback(() => onScrubStart?.(), [onScrubStart]);
  const rulerPan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .enabled(touchable)
        .onBegin((e) => {
          cancelAnimation(v);
          cancelAnimation(fingerOffset);
          const t = eng.time.get();
          eng.phase.set(PH.DRAG);
          eng.touchedAt.set(t);
          eng.pinT.set(-1);
          eng.pinV.set(-99);
          const vf = toPace(e.x);
          // the cursor glides to the finger instead of jumping to it
          fingerOffset.set(v.get() - vf);
          fingerOffset.set(withSpring(0, SPRINGS.snap));
          eng.grab.set(withSpring(1, SPRINGS.cursor));
          lastDetent.set(Math.floor(v.get() / GESTURE.keyStep + 1e-6));
          edgeHit.set(0);
          scrubTick.set(t);
          scheduleOnRN(haptic.onset);
          scheduleOnRN(startCb);
        })
        .onUpdate((e) => {
          const vf = toPace(e.x);
          const nv = vf + fingerOffset.get();
          const pv = v.get();
          v.set(nv);
          const t = eng.time.get();
          const d = Math.floor(nv / GESTURE.keyStep + 1e-6);
          const crossed = (pv - 1) * (nv - 1) < 0 || (pv - 0) * (nv - 0) < 0;
          if (crossed) scheduleOnRN(haptic.cross);
          else if (d !== lastDetent.get() && t - scrubTick.get() > 0.06) {
            scrubTick.set(t);
            scheduleOnRN(haptic.detent);
          }
          lastDetent.set(d);
          const out = nv < V.min || nv > V.max;
          if (out && edgeHit.get() === 0) {
            edgeHit.set(1);
            scheduleOnRN(haptic.limit);
          } else if (!out) edgeHit.set(0);
          if (t - eng.touchedAt.get() > 0.1) {
            eng.touchedAt.set(t);
            scheduleOnRN(scrubCb, clamp(nv, min, max));
          }
        })
        .onFinalize((e) => {
          const t = eng.time.get();
          eng.touchedAt.set(t);
          eng.grab.set(withSpring(0, SPRINGS.cursor));
          const vel = (e.velocityX ?? 0) * GESTURE.xPerPt;
          eng.phase.set(PH.RETURN);
          const nv = v.get();
          if (snapBack) {
            const tgt = readingSV.get();
            v.set(
              reduce
                ? withTiming(tgt, { duration: 200 }, (done) => {
                    if (done) scheduleOnRN(onSettle);
                  })
                : withSpring(tgt, { ...SPRINGS.cool, velocity: vel }, (done) => {
                    if (done) scheduleOnRN(onSettle);
                  }),
            );
          } else {
            const proj = clamp(Math.round((nv + vel * GESTURE.releaseProject) / step) * step, min, max);
            readingSV.set(proj);
            v.set(withSpring(proj, { ...SPRINGS.glide, velocity: vel }));
            scheduleOnRN(commit, proj);
          }
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [touchable, snapBack, reduce, x0, pitch, xMax, step, min, max, commit, onSettle, scrubCb, startCb],
  );
  const probeGesture = useDiscGestures(eng, { cx: L.disc.cx, cy: L.disc.cy, R: L.disc.r, scale: L.disc.scale }, touchable, readingSV, v);

  const onA11yAction = (e: AccessibilityActionEvent) => {
    const dir = e.nativeEvent.actionName === 'increment' ? 1 : e.nativeEvent.actionName === 'decrement' ? -1 : 0;
    if (!dir) return;
    const nv = clamp(Math.round((a11yReading + dir * GESTURE.keyStep) / step) * step, min, max);
    setA11yReading(nv);
    eng.phase.set(PH.RETURN);
    eng.touchedAt.set(eng.time.get());
    if (snapBack) {
      v.set(withSequence(withTiming(nv, { duration: 200 }), withDelay(1800, withSpring(readingSV.get(), SPRINGS.cool))));
    } else {
      readingSV.set(nv);
      setReading(nv);
      v.set(withSpring(nv, SPRINGS.glide));
      onChange?.(nv);
    }
  };

  // ---- ground and static art ----
  const groundU = useMemo(() => {
    const g0 = hexRgb(C.ground0);
    const g1 = hexRgb(C.ground1);
    return { uGr: [L.disc.cy + 54 * L.sc, L.disc.cy + 192 * L.sc, 0, p3], uG0: g0, uG1: g1 };
  }, [L.disc.cy, L.sc, C.ground0, C.ground1, p3]);
  const art = useMemo(() => {
    const sx = (x: number) => L.x(x);
    const backX = sx(comp.back.x);
    const backY = L.yA(comp.back.cy);
    const bw = comp.back.w * S;
    const bh = comp.back.h * L.sc * 0.5;
    const chevL = polyline([
      [sx(comp.date.chevL) + comp.date.chevW * S, L.yA(comp.date.chevCy) - (comp.date.chevH * L.sc) / 2],
      [sx(comp.date.chevL), L.yA(comp.date.chevCy)],
      [sx(comp.date.chevL) + comp.date.chevW * S, L.yA(comp.date.chevCy) + (comp.date.chevH * L.sc) / 2],
    ]);
    const chevR = polyline([
      [sx(comp.date.chevR), L.yA(comp.date.chevCy) - (comp.date.chevH * L.sc) / 2],
      [sx(comp.date.chevR) + comp.date.chevW * S, L.yA(comp.date.chevCy)],
      [sx(comp.date.chevR), L.yA(comp.date.chevCy) + (comp.date.chevH * L.sc) / 2],
    ]);
    const back = polyline([
      [backX + bw, backY - bh],
      [backX, backY],
      [backX + bw, backY + bh],
    ]);
    const infoCx = sx(comp.info.cx);
    const infoCy = L.yA(comp.info.cy);
    const iStem = polyline([
      [infoCx, infoCy - 1.4 * S],
      [infoCx, infoCy + 6 * S],
    ]);
    const iDot = polyline([
      [infoCx, infoCy - 6 * S],
      [infoCx, infoCy - 5.9 * S],
    ]);
    const blob = buildBlob(sx(comp.row.blobCx), L.rowCy, comp.row.blobW * S, comp.row.blobH * L.sc);
    return { chevL, chevR, back, infoCx, infoCy, iStem, iDot, blob };
  }, [L, S]);

  // A legend fades out before the moving value label reaches it (measured on the label's own edges).
  const slowAlpha = useDerivedValue(() => smoothstep(slowFrom, slowTo, labelCX.get() - labelHalf.get()));
  const fastAlpha = useDerivedValue(() => 1 - smoothstep(fastFrom, fastTo, labelCX.get() + labelHalf.get()));
  const slowStyle = useAnimatedStyle(() => ({ opacity: slowAlpha.get() }));
  const fastStyle = useAnimatedStyle(() => ({ opacity: fastAlpha.get() }));
  const bandStyles = [
    useAnimatedStyle(() => ({ opacity: bandA[0].get() })),
    useAnimatedStyle(() => ({ opacity: bandA[1].get() })),
    useAnimatedStyle(() => ({ opacity: bandA[2].get() })),
    useAnimatedStyle(() => ({ opacity: bandA[3].get() })),
    useAnimatedStyle(() => ({ opacity: bandA[4].get() })),
    useAnimatedStyle(() => ({ opacity: bandA[5].get() })),
  ];

  const tk = (key: keyof typeof ref.type, base: number, extra?: TextStyle, k = S): TextStyle => {
    const t = ref.type[key];
    const size = Math.max(t.size * k, Math.min(t.size, 11)); // never under 11 pt (the pin's 10.5 pt tick labels stay)
    const lh = Math.max(t.lineHeight * k, t.lineHeight * (size / t.size));
    return {
      position: 'absolute',
      top: topForBaseline(base, t.family, size, lh),
      fontFamily: t.family,
      fontSize: size,
      lineHeight: lh,
      letterSpacing: t.tracking * size,
      includeFontPadding: false,
      ...extra,
    };
  };
  const centred = { left: 0, right: 0, textAlign: 'center' } as const;
  const scoreText = `${scoreLabel}, ${scoreOf(a11yReading, cfg).toFixed(1)}`;
  const deltaNow = scoreOf(a11yReading, cfg) - cfg.base;
  const a11yDisc = `${scoreLabel} ${scoreOf(a11yReading, cfg).toFixed(1)}, ${Math.abs(deltaNow).toFixed(1)} ${deltaNow > 0.049 ? words.worse : words.better}`;
  const gate = fontsReady ? 1 : 0;
  const rulerTop = L.rowCy - 14 * L.sc;
  const rulerZoneH = L.tickLabelBase + 8 * L.sc - rulerTop;
  const titleBandCopy = (bands.length ? bands : BANDS).slice(0, 6);

  const bodySize = ref.type.cardBody.size * S;

  return (
    <View style={[styles.screen, { backgroundColor: C.ground0 }]}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} accessible={false} pointerEvents="none">
        {GROUND_FX ? (
          <Fill>
            <Shader source={GROUND_FX} uniforms={groundU} />
          </Fill>
        ) : null}
        {DISC_FX ? (
          <Rect x={L.disc.cx - 1.5 * L.disc.r} y={L.disc.cy - 1.5 * L.disc.r} width={3 * L.disc.r} height={3 * L.disc.r}>
            <Shader source={DISC_FX} uniforms={eng.discU} />
          </Rect>
        ) : null}
        {/* the card, with its notch under the cursor */}
        <RoundedRect x={cardX} y={L.card.top} width={cardW} height={L.card.h} r={L.card.r} color={C.card} />
        <Path path={cardPath} color={C.card} />
        {RULER_FX ? (
          <Rect x={0} y={L.ruler.cy - 26 * L.sc} width={width} height={52 * L.sc}>
            <Shader source={RULER_FX} uniforms={rulerU} />
          </Rect>
        ) : null}
        {/* icons */}
        <Path path={art.back} style="stroke" strokeWidth={comp.back.stroke * S} strokeCap="round" strokeJoin="round" color={C.ink} />
        <Group>
          <Path path={art.chevL} style="stroke" strokeWidth={3 * S} strokeCap="round" strokeJoin="round" color={C.ink} />
          <Path path={art.chevR} style="stroke" strokeWidth={3 * S} strokeCap="round" strokeJoin="round" color={ref.color.dim} />
        </Group>
        <Group>
          <RoundedRect
            x={art.infoCx - (comp.info.d * S) / 2}
            y={art.infoCy - (comp.info.d * S) / 2}
            width={comp.info.d * S}
            height={comp.info.d * S}
            r={(comp.info.d * S) / 2}
            color={ref.color.muteIcon}
            style="stroke"
            strokeWidth={comp.info.stroke * S}
          />
          <Path path={art.iStem} style="stroke" strokeWidth={2.4 * S} strokeCap="round" color={ref.color.muteIcon} />
          <Path path={art.iDot} style="stroke" strokeWidth={2.6 * S} strokeCap="round" color={ref.color.muteIcon} />
        </Group>
        <Group opacity={slowAlpha}>
          <RoundedRect
            x={L.x(comp.row.dotX) - (comp.row.dotD * S) / 2}
            y={L.rowCy - (comp.row.dotD * S) / 2}
            width={comp.row.dotD * S}
            height={comp.row.dotD * S}
            r={(comp.row.dotD * S) / 2}
            color={ref.color.slowDot}
          />
        </Group>
        <Group opacity={fastAlpha}>
          <Path path={art.blob} color={ref.color.fastBlob} />
        </Group>
        {/* the number in the hollow: odometer digits, the point, and the difference to the calendar */}
        {fScore ? (
          <Group
            clip={Skia.XYWHRect(
              L.disc.cx - 4 * slotW,
              scoreBase - scoreSize * 0.711 - scoreSize * 0.24,
              8 * slotW,
              scoreSize * 0.711 + scoreSize * 0.42,
            )}
          >
            <OdometerSlot
              font={fScore}
              s={scoreSV}
              k={2}
              x={xTens}
              slotW={slotW}
              baseline={scoreBase}
              cell={cell}
              colour={C.ink}
              widths={wScore}
              reduce={reduce}
              vis={tensVis}
            />
            <OdometerSlot
              font={fScore}
              s={scoreSV}
              k={1}
              x={xOnes}
              slotW={slotW}
              baseline={scoreBase}
              cell={cell}
              colour={C.ink}
              widths={wScore}
              reduce={reduce}
            />
            <SkText x={xDot} y={scoreBase} text="." font={fScore} color={C.ink} />
            <OdometerSlot
              font={fScore}
              s={scoreSV}
              k={0}
              x={xTenths}
              slotW={slotW}
              baseline={scoreBase}
              cell={cell}
              colour={C.ink}
              widths={wScore}
              reduce={reduce}
            />
          </Group>
        ) : null}
        {fDeltaNum && fDeltaWord ? (
          <>
            <SkText x={xDeltaNum} y={deltaBaseY} text={deltaStr} font={fDeltaNum} color={deltaColour} />
            <SkText x={xDeltaWord} y={deltaBaseY} text={deltaWord} font={fDeltaWord} color={deltaColour} />
          </>
        ) : null}
        {fCursor ? (
          <Group transform={cursorLabelXf}>
            <SkText x={cursorLabelX} y={cursorBaseY} text={cursorNum} font={fCursor} color={ref.color.cursor} />
            {fCursorX ? <SkText x={cursorUnitX} y={cursorBaseY} text={cursorUnit} font={fCursorX} color={ref.color.cursor} /> : null}
          </Group>
        ) : null}
      </Canvas>

      {/* chrome controls: buttons only when they have an action (44 pt targets around the drawn icons) */}
      {onBack ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={COPY.back}
          onPress={onBack}
          style={{ position: 'absolute', left: L.x(comp.back.x) - 14, top: L.yA(comp.back.cy) - 22, width: 44, height: 44 }}
        />
      ) : null}
      {onInfo ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={COPY.info}
          onPress={onInfo}
          style={{ position: 'absolute', left: L.x(comp.info.cx) - 22, top: L.yA(comp.info.cy) - 22, width: 44, height: 44 }}
        />
      ) : null}
      {onPreviousWeek ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={COPY.prevWeek}
          onPress={onPreviousWeek}
          style={{ position: 'absolute', left: L.x(comp.date.chevL) - 18, top: L.yA(comp.date.chevCy) - 22, width: 44, height: 44 }}
        />
      ) : null}

      {/* the touch targets */}
      <GestureDetector gesture={probeGesture}>
        <View
          style={{ position: 'absolute', left: L.disc.cx - L.disc.r, top: L.disc.cy - L.disc.r, width: 2 * L.disc.r, height: 2 * L.disc.r }}
        />
      </GestureDetector>
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="image"
        accessibilityLabel={a11yDisc}
        style={{ position: 'absolute', left: L.disc.cx - L.disc.r, top: L.disc.cy - L.disc.r, width: 2 * L.disc.r, height: 2 * L.disc.r }}
      />
      <GestureDetector gesture={rulerPan}>
        <View
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={COPY.rulerLabel}
          accessibilityHint={COPY.rulerHint}
          accessibilityValue={{ text: `${fmt(a11yReading)}. ${scoreText}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={onA11yAction}
          style={{ position: 'absolute', left: 0, right: 0, top: rulerTop, height: rulerZoneH }}
        />
      </GestureDetector>

      {/* static type (assistive tech reads these; the numbers that move are on the canvas) */}
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { opacity: gate }]}>
        <Text
          maxFontSizeMultiplier={1.2}
          style={tk('title', L.yA(comp.titleBase), { ...centred, color: C.ink })}
          accessibilityRole="header"
        >
          {COPY.title}
        </Text>
        <Text maxFontSizeMultiplier={1.2} style={tk('subtitle', L.yA(comp.subtitleBase), { ...centred, color: C.mute })}>
          {COPY.subtitle}
        </Text>
        <Text maxFontSizeMultiplier={1.2} style={tk('date', L.yA(comp.date.base), { ...centred, color: C.ink })}>
          {COPY.date}
        </Text>
        <Text
          maxFontSizeMultiplier={1.2}
          style={tk('scoreLabel', L.centreBase(comp.centre.labelBase), { ...centred, color: ref.color.inkSoft }, L.ts * L.sc)}
        >
          {scoreLabel}
        </Text>
        <Text maxFontSizeMultiplier={1.2} style={tk('pace', L.paceBase, { left: L.x(comp.pace.x), color: C.ink })}>
          {COPY.pace}
        </Text>
        <Animated.View style={slowStyle}>
          <Text maxFontSizeMultiplier={1.2} style={tk('end', L.rowBase, { left: L.x(comp.row.slowX), color: ref.color.label })}>
            {COPY.slow}
          </Text>
        </Animated.View>
        <Animated.View style={fastStyle}>
          <Text
            maxFontSizeMultiplier={1.2}
            style={tk('end', L.rowBase, { right: width - L.x(comp.row.fastRight), textAlign: 'right', color: ref.color.label })}
          >
            {COPY.fast}
          </Text>
        </Animated.View>
        {COPY.ends.map((label, i) => (
          <Text
            key={label}
            maxFontSizeMultiplier={1.2}
            style={tk('tickLabel', L.tickLabelBase, {
              left: x0 + i * 40 * pitch - 30,
              width: 60,
              textAlign: 'center',
              color: ref.color.label,
            })}
          >
            {label}
          </Text>
        ))}
        {titleBandCopy.map((b: SssBand | { from: number; title: string; body: string }, i) => (
          <Animated.View key={`${i}-${b.title}`} pointerEvents="none" style={[StyleSheet.absoluteFill, bandStyles[Math.min(i, 5)]]}>
            <Text
              maxFontSizeMultiplier={1.2}
              style={tk('cardTitle', L.card.titleBase, { left: cardX + L.card.padX - 0.4, color: ref.color.cardTitle })}
              accessibilityRole="header"
            >
              {b.title}
            </Text>
            <Text
              maxFontSizeMultiplier={1.2}
              numberOfLines={L.lines}
              style={{
                position: 'absolute',
                left: cardX + L.card.padX,
                width: cardW - 2 * L.card.padX,
                top: topForBaseline(L.card.bodyBase, ref.type.cardBody.family, bodySize, ref.type.cardBody.lineHeight * L.k),
                fontFamily: ref.type.cardBody.family,
                fontSize: bodySize,
                lineHeight: ref.type.cardBody.lineHeight * L.k,
                letterSpacing: ref.type.cardBody.tracking * bodySize,
                color: ref.color.cardBody,
                includeFontPadding: false,
              }}
            >
              {b.body}
            </Text>
          </Animated.View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden' },
});
