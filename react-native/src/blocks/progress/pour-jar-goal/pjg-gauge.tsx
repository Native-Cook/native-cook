import { useCallback, useMemo, useState } from 'react';
import { Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  AlphaType,
  BlurMask,
  Canvas,
  Circle,
  ColorType,
  Fill,
  FilterMode,
  Group,
  ImageShader,
  matchFont,
  Path,
  RoundedRect,
  Shader,
  Shadow,
  Skia,
  Text,
  useFont,
  type SkFont,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import { useAudioPlayer, type AudioPlayer } from 'expo-audio';
import { StatusBar } from 'expo-status-bar';
import { Gesture, GestureDetector, GestureHandlerRootView, ScrollView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  useAnimatedReaction,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { clamp, LOOP_FIELDS, segment } from './pjg-motion';
import { packRows } from './pjg-data';
import { bubblesAt } from './pjg-bubbles';
import { LIQUID_SKSL } from './pjg-shaders';
import { comp, COPY, makeLayout, ref, REF, sys } from './pjg-theme';
import { amountSlots, digitAt, money, percentage } from './pjg-text';
import { useLiquid } from './pjg-use-liquid';
import { buildVessel, volumeToY as volumeY, VESSELS, type VesselTable } from './pjg-vessel';
import type { LiquidGaugeProProps } from './pjg-types';

export { LOOP } from './pjg-motion';
export { VESSELS } from './pjg-vessel';
export type { LiquidGaugeProps, LiquidGaugeProProps } from './pjg-types';

const fonts = {
  'PJG-Instrument': require('./assets/fonts/InstrumentSerif-Regular.ttf'),
  'PJG-Inter': require('./assets/fonts/Inter-Regular.ttf'),
  'PJG-InterMedium': require('./assets/fonts/Inter-Medium.ttf'),
  'PJG-InterSemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  'PJG-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
};
const effect = Skia.RuntimeEffect.Make(LIQUID_SKSL);
function rgb(hex: string) {
  const n = parseInt(hex.replace('#', ''), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
function textWidth(font: SkFont, text: string) {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
}
function resolveUIFont(family: string | undefined, bundled: SkFont | null, size: number, weight: '400' | '500' | '600') {
  return family ? matchFont({ fontFamily: family, fontSize: size, fontWeight: weight }) : bundled;
}
// Each voice owns its pending seek, so a cancelled input cannot start audio later.
class AudioVoice {
  private generation = 0;
  constructor(private player: AudioPlayer) {}
  flow(level: number, gain: number) {
    try {
      this.player.volume = Math.min(0.7, gain);
      this.player.shouldCorrectPitch = false;
      this.player.setPlaybackRate(0.8 + 0.6 * clamp(level));
    } catch {
      /* A still-loading optional source remains silent. */
    }
  }
  close(tail = false) {
    if (!tail) this.generation++;
    try {
      this.player.loop = false;
      if (!tail) this.player.pause();
    } catch {
      /* Already released. */
    }
  }
  play(level: number, gain: number, loop = false) {
    const generation = ++this.generation;
    try {
      this.flow(level, gain);
      this.player.loop = loop;
      void this.player
        .seekTo(0)
        .then(() => {
          if (generation === this.generation) this.player.play();
        })
        .catch(() => undefined);
    } catch {
      /* Optional audio never blocks the gauge. */
    }
  }
}

type Ink = string | SharedValue<string>;
function Digit({
  value,
  place,
  x,
  baseline,
  font,
  ink,
  reduced,
}: {
  value: SharedValue<number>;
  place: number;
  x: number;
  baseline: number;
  font: SkFont;
  ink: Ink;
  reduced: boolean;
}) {
  const size = font.getSize(),
    travel = size * 0.28;
  const d = useDerivedValue(() => (reduced ? Math.floor(Math.max(0, value.get()) / place) % 10 : digitAt(value.get(), place)));
  // Each unit rests for most of its span and changes in its last 30 %: one legible glyph
  // most of the time, and in between a short lift + blur + cross-fade (no hard-clipped halves).
  const k = useDerivedValue(() => {
    const f = d.get() - Math.floor(d.get());
    const u = Math.min(Math.max((f - 0.7) / 0.3, 0), 1);
    return u * u * (3 - 2 * u);
  });
  const first = useDerivedValue(() => String(Math.floor(d.get()) % 10));
  const next = useDerivedValue(() => String((Math.floor(d.get()) + 1) % 10));
  const y0 = useDerivedValue(() => baseline - k.get() * travel);
  const y1 = useDerivedValue(() => baseline + (1 - k.get()) * travel);
  // Out fades over the first 60 % and in over the last 60 %: never two full glyphs at once.
  const a0 = useDerivedValue(() => {
    const u = Math.min(k.get() / 0.6, 1);
    return 1 - u * u * (3 - 2 * u);
  });
  const a1 = useDerivedValue(() => {
    const u = Math.max((k.get() - 0.4) / 0.6, 0);
    return u * u * (3 - 2 * u);
  });
  const b0 = useDerivedValue(() => Math.max(0.001, k.get() * size * 0.045));
  const b1 = useDerivedValue(() => Math.max(0.001, (1 - k.get()) * size * 0.045));
  if (reduced) return <Text text={first} x={x} y={baseline} font={font} color={ink} />;
  return (
    <>
      <Group opacity={a0}>
        <Text text={first} x={x} y={y0} font={font} color={ink}>
          <BlurMask blur={b0} style="normal" />
        </Text>
      </Group>
      <Group opacity={a1}>
        <Text text={next} x={x} y={y1} font={font} color={ink}>
          <BlurMask blur={b1} style="normal" />
        </Text>
      </Group>
    </>
  );
}
function RollingAmount({
  value,
  max,
  cx,
  baseline,
  font,
  ink,
  reduced,
}: {
  value: SharedValue<number>;
  max: number;
  cx: number;
  baseline: number;
  font: SkFont;
  ink: Ink;
  reduced: boolean;
}) {
  const data = useMemo(() => {
    const widths = font.getGlyphWidths(font.getGlyphIDs('0123456789'));
    return amountSlots(
      max,
      Math.max(...widths) - font.getSize() * 0.025,
      textWidth(font, '$') - font.getSize() * 0.025,
      textWidth(font, ','),
    );
  }, [font, max]);
  const compactText = useDerivedValue(() => money(value.get()));
  const characterWidths = useMemo(() => font.getGlyphWidths(font.getGlyphIDs('$,0123456789')), [font]);
  const compactX = useDerivedValue(() => {
    const text = compactText.get();
    let width = 0;
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i);
      width += characterWidths[code === 36 ? 0 : code === 44 ? 1 : code - 48 + 2];
    }
    return cx - width / 2;
  });
  const threshold = 10 ** (String(Math.floor(Math.max(1, max))).length - 1);
  const rollOpacity = useDerivedValue(() => (threshold === 1 ? 1 : segment(value.get(), threshold - 1, threshold)));
  const compactOpacity = useDerivedValue(() => 1 - rollOpacity.get());
  return (
    <Group origin={{ x: cx, y: baseline }} transform={[{ scaleX: 0.92 }]}>
      <Group opacity={compactOpacity}>
        <Text text={compactText} x={compactX} y={baseline} font={font} color={ink} />
      </Group>
      <Group opacity={rollOpacity}>
        {data.slots.map((slot, i) =>
          slot.place ? (
            <Digit
              key={i}
              value={value}
              place={slot.place}
              x={cx - data.width / 2 + slot.x}
              baseline={baseline}
              font={font}
              ink={ink}
              reduced={reduced}
            />
          ) : (
            <Text key={i} text={slot.char} x={cx - data.width / 2 + slot.x} y={baseline} font={font} color={ink} />
          ),
        )}
      </Group>
    </Group>
  );
}

// Arbitrary consumer formatters stay on JS, isolated from the screen and capped at 10 Hz.
function CustomAmount({
  value,
  initial,
  format,
  cx,
  baseline,
  font,
  ink,
}: {
  value: SharedValue<number>;
  initial: number;
  format: (v: number) => string;
  cx: number;
  baseline: number;
  font: SkFont;
  ink: Ink;
}) {
  const [text, setText] = useState(() => format(initial));
  const last = useSharedValue(-1000);
  const tail = useSharedValue(initial);
  const update = useCallback((v: number) => setText(format(v)), [format]);
  useAnimatedReaction(
    () => Math.round(value.get()),
    (v, prev) => {
      if (v !== prev)
        tail.set(
          withDelay(
            110,
            withTiming(v, { duration: 0 }, (done) => {
              if (done) scheduleOnRN(update, v);
            }),
          ),
        );
      const now = performance.now();
      if (v !== prev && now - last.get() >= 100) {
        last.set(now);
        scheduleOnRN(update, v);
      }
    },
  );
  return <Text text={text} x={cx - textWidth(font, text) / 2} y={baseline} font={font} color={ink} />;
}

/** Self-contained, offline, controlled or uncontrolled savings gauge. */
export function LiquidGauge(props: LiquidGaugeProProps) {
  const { max, onBack, onOptions } = props;
  const { width: W, height: windowH, fontScale } = useWindowDimensions();
  const H = Math.max(windowH, fontScale > 2 ? (REF.h + (fontScale - 2) * 360) * Math.min(W / REF.w, 1) : windowH);
  const insets = useSafeAreaInsets(),
    reduced = useReducedMotion();
  const L = useMemo(() => makeLayout(W, H, insets.top, insets.bottom, fontScale), [W, H, insets.top, insets.bottom, fontScale]);
  const { S, jar } = L;
  const [loaded] = useFonts(fonts);
  const numberSize = Math.min(ref.type.display.size * S * Math.min(fontScale, 1.08), 76);
  const bundledNumberFont = useFont(fonts['PJG-Instrument'], numberSize);
  const bundledBodyFont = useFont(fonts['PJG-Inter'], ref.type.body.size * S * fontScale);
  const numberFont = useMemo(
    () => (props.tokens?.numeralFont ? matchFont({ fontFamily: props.tokens.numeralFont, fontSize: numberSize }) : bundledNumberFont),
    [bundledNumberFont, numberSize, props.tokens],
  );
  const bodyFont = useMemo(
    () =>
      props.tokens?.uiFont ? matchFont({ fontFamily: props.tokens.uiFont, fontSize: ref.type.body.size * S * fontScale }) : bundledBodyFont,
    [S, bundledBodyFont, fontScale, props.tokens],
  );
  const bundledLabelFont = useFont(fonts['PJG-InterMedium'], ref.type.label.size * S * fontScale);
  const labelFont = resolveUIFont(props.tokens?.uiFont, bundledLabelFont, ref.type.label.size * S * fontScale, '500');
  const bundledTitleFont = useFont(
    fonts['PJG-InterSemiBold'],
    ref.type.title.size * S * (L.largestType ? fontScale : Math.min(fontScale, 1.4)),
  );
  const titleFont = resolveUIFont(
    props.tokens?.uiFont,
    bundledTitleFont,
    ref.type.title.size * S * (L.largestType ? fontScale : Math.min(fontScale, 1.4)),
    '600',
  );
  const bundledButtonFont = useFont(fonts['PJG-InterSemiBold'], 16 * S * (L.largestType ? fontScale : Math.min(fontScale, 1.5)));
  const buttonFont = resolveUIFont(
    props.tokens?.uiFont,
    bundledButtonFont,
    16 * S * (L.largestType ? fontScale : Math.min(fontScale, 1.5)),
    '600',
  );
  const bundledStatFont = useFont(fonts['PJG-InterSemiBold'], ref.type.stat.size * S * fontScale);
  const statFont = resolveUIFont(props.tokens?.uiFont, bundledStatFont, ref.type.stat.size * S * fontScale, '600');
  const monoFont = useFont(fonts['PJG-Mono'], Math.max(10 * S, 10) * Math.min(fontScale, 1.35));
  const toastSize = 14 * S * (L.largeType ? fontScale : 1);
  const bundledToastFont = useFont(fonts['PJG-InterMedium'], toastSize);
  const toastFont = resolveUIFont(props.tokens?.uiFont, bundledToastFont, toastSize, '500');
  const vessel = props.vessel ?? VESSELS.jar;
  const table = useMemo(() => {
    const path = 'path' in vessel ? Skia.Path.MakeFromSVGString(vessel.path) : null;
    return buildVessel(vessel, path ? (x, y) => path.contains(x, y) : undefined);
  }, [vessel]);
  const heroW = Math.min(jar.w, vessel.width * S),
    heroX = W / 2 - heroW / 2;
  const sx = heroW / table.width,
    sy = jar.h / table.height;
  const pourAudio = useAudioPlayer(typeof props.sound === 'object' ? props.sound.pour : require('./assets/pour.wav'));
  const landAudio = useAudioPlayer(typeof props.sound === 'object' ? props.sound.land : require('./assets/land.wav'));
  const pourVoice = useMemo(() => new AudioVoice(pourAudio), [pourAudio]);
  const landVoice = useMemo(() => new AudioVoice(landAudio), [landAudio]);
  const sound = useCallback(
    (cue: 'pour' | 'land' | 'flow' | 'tail' | 'stop', level: number, gain = 0.6) => {
      if (props.sound === false) {
        pourVoice.close();
        landVoice.close();
        return;
      }
      if (cue === 'stop' || cue === 'tail') {
        pourVoice.close(cue === 'tail');
        return;
      }
      if (cue === 'flow') {
        pourVoice.flow(level, gain);
        return;
      }
      (cue === 'pour' ? pourVoice : landVoice).play(level, gain, cue === 'pour');
    },
    [landVoice, pourVoice, props.sound],
  );
  const {
    amount,
    displayAmount,
    statAmount,
    percentAmount,
    heights,
    time,
    toast,
    delta,
    grip,
    fingerY,
    stream,
    impact,
    bubbleTime,
    dropY,
    dropX,
    dropRadius,
    mode,
    touching,
    drag,
    pour,
    undo,
    adjust,
    hasUndo,
    accessibleValue,
  } = useLiquid(props, table, sx, sy, reduced, sound);
  const atlas = useMemo(() => {
    const p = packRows(LOOP_FIELDS, -64, 64);
    return Skia.Image.MakeImage(
      { width: p.width, height: p.height, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      Skia.Data.fromBytes(p.bytes),
      p.width * 4,
    );
  }, []);
  const wallImage = useMemo(() => {
    const p = packRows([table.left, table.right], 0, table.width);
    return Skia.Image.MakeImage(
      { width: p.width, height: p.height, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      Skia.Data.fromBytes(p.bytes),
      p.width * 4,
    );
  }, [table]);
  const fieldImage = useDerivedValue(() => {
    if (mode.get() !== 1 || reduced) return atlas;
    const p = packRows([heights.get()], -64, 64);
    return Skia.Image.MakeImage(
      { width: p.width, height: p.height, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      Skia.Data.fromBytes(p.bytes),
      p.width * 4,
    );
  });
  const min = props.min ?? 0,
    range = Math.max(1, max - min);
  const pageColor = useMemo(() => rgb(props.tokens?.page ?? sys.surface.page), [props.tokens?.page]);
  const surfaceColor = useMemo(() => rgb(props.colors?.[0] ?? ref.color.surface), [props.colors]);
  const depthColor = useMemo(() => rgb(props.colors?.[1] ?? ref.color.depth), [props.colors]);
  const ambient = props.ambient === false ? 0 : 1,
    bubbleCount = clamp(props.bubbles ?? 12, 0, 12);
  const p3 = Platform.OS === 'ios' ? 1 : 0;
  const uniforms = useDerivedValue(() => ({
    resolution: [W, H],
    jar: [heroX, jar.y, heroW, jar.h],
    vesselSize: [table.width, table.height],
    radii: [table.topRadius, table.bottomRadius],
    custom: table.custom,
    fieldRow: reduced || mode.get() === 1 ? 0 : Math.min(960, Math.round(time.get() * 120)),
    // Imported below as a worklet; never a closure on a layout object.
    meanY: volumeY((amount.get() - min) / range, table),
    level: clamp((amount.get() - min) / range),
    bubbleTime: bubbleTime.get(),
    bubbleCount: reduced ? 0 : bubbleCount,
    bubbleGeometry: bubblesAt(bubbleTime.get(), volumeY((amount.get() - min) / range, table), table, heights.get()),
    grip: grip.get(),
    finger: [dropX.get(), fingerY.get()],
    stream: stream.get(),
    dropY: dropY.get(),
    dropRadius: dropRadius.get(),
    impact: impact.get(),
    ambient,
    reduced: reduced ? 1 : 0,
    p3,
    pageColor,
    surfaceColor,
    depthColor,
  }));
  const percentText = useDerivedValue(() => `${percentage(percentAmount.get(), min, max)}%`);
  const subtitle = useDerivedValue(() => `of ${money(max)} · ${percentage(percentAmount.get(), min, max)}%`);
  const remaining = useDerivedValue(() => money(max - statAmount.get()));
  const weeks = useDerivedValue(() => `${Math.ceil(Math.max(0, Math.round(max - statAmount.get())) / 40)} weeks`);
  const goalName = (props.title ?? COPY.title).split(',')[0];
  const toastHeight = L.largeType ? toastSize * 3.5 + 20 * S : 44 * S;
  const toastMoney = useDerivedValue(() => money(delta.get()));
  const toastText = useDerivedValue(() => `${money(delta.get())} poured into ${goalName}`);
  const toastTransform = useDerivedValue(() => [{ translateY: reduced ? 0 : -38 * S * (1 - toast.get()) }]);
  // The header title morphs into the toast pill (never over the jar or its scale).
  const toastGlyphs = useMemo(() => (toastFont ? toastFont.getGlyphWidths(toastFont.getGlyphIDs('$,0123456789')) : []), [toastFont]);
  const toastSuffixW = useMemo(() => (toastFont ? textWidth(toastFont, ` poured into ${goalName}`) : 0), [toastFont, goalName]);
  const titleW = useMemo(() => (titleFont ? textWidth(titleFont, props.title ?? COPY.title) : 0), [titleFont, props.title]);
  const headCY = L.headerY + 22 * S;
  const large = L.largeType;
  const pillMaxW = L.x(339) - L.x(63) - 8 * S;
  const pillW = useDerivedValue(() => {
    const text = toastMoney.get();
    let w = toastSuffixW;
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i);
      w += toastGlyphs[code === 36 ? 0 : code === 44 ? 1 : code - 48 + 2] ?? 0;
    }
    return Math.min(pillMaxW, w + 52 * S);
  });
  const pillE = useDerivedValue(() => clamp(toast.get()));
  const pillRect = useDerivedValue(() => {
    const e = pillE.get();
    const w = titleW + 28 * S + (pillW.get() - titleW - 28 * S) * e;
    const h = (30 + 10 * e) * S;
    return Skia.RRectXY(Skia.XYWHRect(W / 2 - w / 2, headCY - h / 2, w, h), h / 2, h / 2);
  });
  const pillShadow = useDerivedValue(() => `rgba(21,32,44,${(0.16 * pillE.get()).toFixed(3)})`);
  const titleFade = useDerivedValue(() => (large ? 1 : 1 - segment(pillE.get(), 0, 0.35)));
  const titleShift = useDerivedValue(() => (large || reduced ? [] : [{ translateY: -5 * S * segment(pillE.get(), 0, 0.35) }]));
  const pillContent = useDerivedValue(() => segment(pillE.get(), 0.6, 1));
  const pillFill = useDerivedValue(() => segment(pillE.get(), 0, 0.35));
  const pillContentShift = useDerivedValue(() => [{ translateY: reduced ? 0 : 5 * S * (1 - pillContent.get()) }]);
  const pillLeft = useDerivedValue(() => W / 2 - pillW.get() / 2);
  const checkCX = useDerivedValue(() => pillLeft.get() + 20 * S);
  const checkPath = useDerivedValue(() => `M${pillLeft.get() + 16 * S} ${headCY}l${3 * S} ${3 * S}l${5 * S} ${-5 * S}`);
  const checkR = useDerivedValue(() => (reduced ? 11 : 6 + 5 * pillContent.get()) * S);
  const pillTextX = useDerivedValue(() => pillLeft.get() + 38 * S);
  const pressScale = useDerivedValue(() => (reduced ? [{ scale: 1 }] : [{ scale: 1 - 0.03 * clamp(touching.get() - 1, 0, 1) }]));
  const warmInk = useDerivedValue(() => {
    const p = segment((amount.get() - min) / range, 0.9, 1);
    return `rgb(${Math.round(16 + 26 * p)},${Math.round(29 - 8 * p)},${Math.round(41 - 23 * p)})`;
  });
  const [autoEnabled, setAutoEnabled] = useState(false);
  const onAutoChange = props.onAutoChange;
  const toggleAuto = useCallback(() => {
    const next = !autoEnabled;
    setAutoEnabled(next);
    onAutoChange?.(next);
  }, [autoEnabled, onAutoChange]);
  const autoGesture = useMemo(
    () =>
      Gesture.Tap().onEnd((_e, ok) => {
        if (ok) scheduleOnRN(toggleAuto);
      }),
    [toggleAuto],
  );
  const back = useMemo(
    () =>
      Gesture.Tap().onEnd((_e, ok) => {
        if (ok && onBack) scheduleOnRN(onBack);
      }),
    [onBack],
  );
  const options = useMemo(
    () =>
      Gesture.Tap().onEnd((_e, ok) => {
        if (ok && onOptions) scheduleOnRN(onOptions);
      }),
    [onOptions],
  );
  const undoGesture = useMemo(
    () =>
      Gesture.Tap().onEnd((_e, ok) => {
        if (ok) scheduleOnRN(undo);
      }),
    [undo],
  );
  const ink = props.tokens?.ink ?? sys.ink.primary,
    mute = props.tokens?.mute ?? sys.ink.secondary;
  const visible = loaded && numberFont && bodyFont && labelFont && titleFont && statFont && buttonFont && monoFont && toastFont;
  const cardW = L.largeType ? comp.buttons.w * S : comp.cards.w * S;
  const cardH = L.largeType ? (L.cardH - 8 * S) / 2 : L.cardH;
  const card2X = L.largeType ? L.x(comp.cards.x) : L.x(comp.cards.x + comp.cards.w + comp.cards.gap);
  const card2Y = L.largeType ? L.cardsY + cardH + 8 * S : L.cardsY;
  const title = props.title ?? COPY.title;
  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="dark" />
      <ScrollView
        bounces={false}
        scrollEnabled={L.largestType}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ width: W, height: H }}
      >
        <View style={{ width: W, height: H }}>
          <Canvas style={StyleSheet.absoluteFill} accessible={false}>
            {effect && atlas && wallImage ? (
              <Fill>
                <Shader source={effect} uniforms={uniforms}>
                  <ImageShader image={fieldImage} tx="clamp" ty="clamp" fit="none" sampling={{ filter: FilterMode.Nearest }} />
                  <ImageShader image={wallImage} tx="clamp" ty="clamp" fit="none" sampling={{ filter: FilterMode.Nearest }} />
                </Shader>
              </Fill>
            ) : (
              <Fill color={sys.surface.page} />
            )}
            {visible && (
              <>
                <Circle cx={L.x(41)} cy={L.headerY + 22 * S} r={19 * S} color={sys.surface.raised}>
                  <Shadow dx={0} dy={2 * S} blur={4 * S} color="#101D290B" />
                </Circle>
                <Circle cx={L.x(361)} cy={L.headerY + 22 * S} r={19 * S} color={sys.surface.raised} />
                <Path
                  path={`M${L.x(43)} ${L.headerY + 17 * S}l${-5 * S} ${5 * S}l${5 * S} ${5 * S}`}
                  style="stroke"
                  strokeWidth={1.4 * S}
                  strokeCap="round"
                  strokeJoin="round"
                  color={ink}
                />
                {[355, 361, 367].map((x) => (
                  <Circle key={x} cx={L.x(x)} cy={L.headerY + 22 * S} r={0.95 * S} color={ink} />
                ))}
                <Group opacity={titleFade} transform={titleShift}>
                  {(L.largestType ? title.split(' ') : [title]).map((line, index) => (
                    <Text
                      key={index}
                      text={line}
                      x={W / 2 - textWidth(titleFont, line) / 2}
                      y={L.headerY + (L.largestType ? titleFont.getSize() * (0.75 + index * 1.1) : 28 * S)}
                      font={titleFont}
                      color={ink}
                    />
                  ))}
                </Group>
                {(props.ticks ?? [1, 0.75, 0.5, 0.25]).map((tick) => (
                  <ScaleTick
                    key={tick}
                    amount={amount}
                    fraction={tick}
                    min={min}
                    max={max}
                    table={table}
                    x={heroX + heroW + 16 * S}
                    y0={jar.y}
                    sy={sy}
                    font={monoFont}
                    ink={ink}
                    mute={mute}
                  />
                ))}
                <Group>
                  {props.format ? (
                    <CustomAmount
                      value={displayAmount}
                      initial={props.value ?? props.defaultValue ?? 2240}
                      format={props.format}
                      cx={W / 2}
                      baseline={L.amountBaseline}
                      font={numberFont}
                      ink={props.tokens?.ink ?? warmInk}
                    />
                  ) : (
                    <RollingAmount
                      value={displayAmount}
                      max={max}
                      cx={W / 2}
                      baseline={L.amountBaseline}
                      font={numberFont}
                      ink={props.tokens?.ink ?? warmInk}
                      reduced={reduced}
                    />
                  )}
                </Group>
                {L.largestType ? (
                  <>
                    <Text
                      text={`of ${money(max)}`}
                      x={W / 2 - textWidth(bodyFont, `of ${money(max)}`) / 2}
                      y={L.subtitleY + bodyFont.getSize()}
                      font={bodyFont}
                      color={mute}
                    />
                    <Text
                      text={percentText}
                      x={W / 2 - textWidth(bodyFont, '75%') / 2}
                      y={L.subtitleY + bodyFont.getSize() * 2.2}
                      font={bodyFont}
                      color={mute}
                    />
                  </>
                ) : (
                  <Text
                    text={subtitle}
                    x={W / 2 - textWidth(bodyFont, `of ${money(max)} · 75%`) / 2}
                    y={L.subtitleY + 13 * S}
                    font={bodyFont}
                    color={mute}
                  />
                )}
                {[
                  { x: L.x(comp.cards.x), y: L.cardsY, label: COPY.remaining, value: remaining },
                  { x: card2X, y: card2Y, label: COPY.weekly, value: weeks },
                ].map((card, i) => (
                  <Group key={i}>
                    <RoundedRect x={card.x} y={card.y} width={cardW} height={cardH} r={ref.radius.card * S} color={sys.surface.raised}>
                      <Shadow dx={0} dy={1 * S} blur={1 * S} color="#101D290A" />
                      <Shadow dx={0} dy={5 * S} blur={12 * S} color="#101D2908" />
                    </RoundedRect>
                    <RoundedRect
                      x={card.x}
                      y={card.y}
                      width={cardW}
                      height={cardH}
                      r={ref.radius.card * S}
                      color={sys.stroke.hairline}
                      style="stroke"
                      strokeWidth={0.5 * S}
                    />
                    <Text
                      text={card.label}
                      x={card.x + 16 * S}
                      y={card.y + (L.largeType ? 12 + 12 * fontScale * 0.75 : 24) * S}
                      font={labelFont}
                      color={mute}
                    />
                    <Text
                      text={card.value}
                      x={card.x + 16 * S}
                      y={card.y + (L.largeType ? cardH / S - 16 : 56) * S}
                      font={statFont}
                      color={ink}
                    />
                  </Group>
                ))}
                <Group origin={{ x: L.x(139), y: L.buttonY + L.buttonH / 2 }} transform={pressScale}>
                  <RoundedRect
                    x={L.x(comp.buttons.x)}
                    y={L.buttonY}
                    width={comp.buttons.primaryW * S}
                    height={L.buttonH}
                    r={ref.radius.pill * S}
                    color={ink}
                  />
                  <Text
                    text={COPY.pour}
                    x={L.x(comp.buttons.x) + (comp.buttons.primaryW * S) / 2 - textWidth(buttonFont, COPY.pour) / 2}
                    y={L.buttonY + L.buttonH / 2 + buttonFont.getSize() * 0.35}
                    font={buttonFont}
                    color={sys.ink.onAccent}
                  />
                </Group>
                <RoundedRect
                  x={L.x(266)}
                  y={L.buttonY}
                  width={114 * S}
                  height={L.buttonH}
                  r={ref.radius.pill * S}
                  color={sys.surface.raised}
                />
                <RoundedRect
                  x={L.x(266)}
                  y={L.buttonY}
                  width={114 * S}
                  height={L.buttonH}
                  r={ref.radius.pill * S}
                  color={sys.stroke.hairline}
                  style="stroke"
                  strokeWidth={0.5 * S}
                />
                <Text
                  text={autoEnabled ? (L.largestType ? 'On' : COPY.autoOn) : COPY.auto}
                  x={L.x(323) - textWidth(buttonFont, autoEnabled ? (L.largestType ? 'On' : COPY.autoOn) : COPY.auto) / 2}
                  y={L.buttonY + L.buttonH / 2 + buttonFont.getSize() * 0.35}
                  font={buttonFont}
                  color={ink}
                />
                {!props.children && !L.largeType && (
                  <Group>
                    <Group opacity={pillFill}>
                      <RoundedRect rect={pillRect} color={ref.color.toast}>
                        <Shadow dx={0} dy={6 * S} blur={10 * S} color={pillShadow} />
                      </RoundedRect>
                    </Group>
                    <Group opacity={pillContent} transform={pillContentShift}>
                      <Circle cx={checkCX} cy={headCY} r={checkR} color={ref.color.depth} />
                      <Path
                        path={checkPath}
                        color={sys.ink.onAccent}
                        style="stroke"
                        strokeWidth={1.5 * S}
                        strokeCap="round"
                        strokeJoin="round"
                      />
                      <Text text={toastText} x={pillTextX} y={headCY + toastSize * 0.36} font={toastFont} color={sys.ink.onAccent} />
                    </Group>
                  </Group>
                )}
                {!props.children && L.largeType && (
                  <Group opacity={toast} transform={toastTransform}>
                    <RoundedRect x={heroX} y={jar.y + 17 * S} width={heroW} height={toastHeight} r={24 * S} color={ref.color.toast}>
                      <Shadow dx={0} dy={9 * S} blur={11 * S} color="#15202C30" />
                    </RoundedRect>
                    <Circle
                      cx={heroX + 22 * S}
                      cy={jar.y + (L.largeType ? 25 * S + toastSize * 0.5 : 39 * S)}
                      r={11.5 * S}
                      color={ref.color.depth}
                    />
                    <Path
                      path={`M${heroX + 18 * S} ${jar.y + (L.largeType ? 25 * S + toastSize * 0.5 : 39 * S)}l${3 * S} ${3 * S}l${5 * S} ${-5 * S}`}
                      color={sys.ink.onAccent}
                      style="stroke"
                      strokeWidth={1.5 * S}
                      strokeCap="round"
                      strokeJoin="round"
                    />
                    {L.largeType ? (
                      <>
                        <Text
                          text={toastMoney}
                          x={heroX + 42 * S}
                          y={jar.y + 25 * S + toastSize}
                          font={toastFont}
                          color={sys.ink.onAccent}
                        />
                        <Text
                          text="poured"
                          x={W / 2 - textWidth(toastFont, 'poured') / 2}
                          y={jar.y + 25 * S + toastSize * 2.12}
                          font={toastFont}
                          color={sys.ink.onAccent}
                        />
                        <Text
                          text={`into ${goalName}`}
                          x={W / 2 - textWidth(toastFont, `into ${goalName}`) / 2}
                          y={jar.y + 25 * S + toastSize * 3.24}
                          font={toastFont}
                          color={sys.ink.onAccent}
                        />
                      </>
                    ) : (
                      <Text text={toastText} x={heroX + 42 * S} y={jar.y + 44 * S} font={toastFont} color={sys.ink.onAccent} />
                    )}
                  </Group>
                )}
              </>
            )}
          </Canvas>
          <GestureDetector gesture={back}>
            <View
              style={[styles.target, { left: L.x(19), top: L.headerY, width: 44, height: 44 }]}
              accessible
              accessibilityRole="button"
              accessibilityLabel={COPY.back}
              onAccessibilityTap={onBack}
            />
          </GestureDetector>
          <GestureDetector gesture={options}>
            <View
              style={[styles.target, { left: L.x(339), top: L.headerY, width: 44, height: 44 }]}
              accessible
              accessibilityRole="button"
              accessibilityLabel={COPY.more}
              onAccessibilityTap={onOptions}
            />
          </GestureDetector>
          <GestureDetector gesture={drag}>
            <View
              style={[styles.target, { left: heroX, top: jar.y, width: heroW, height: jar.h }]}
              accessible
              accessibilityRole={props.editable === false || props.level ? 'progressbar' : 'adjustable'}
              accessibilityLabel={`${title}. ${money(accessibleValue)} of ${money(max)}. ${percentage(accessibleValue, min, max)} percent.`}
              accessibilityHint={props.editable === false || props.level ? 'Saved progress' : COPY.adjustable}
              accessibilityValue={{ min, max: max, now: accessibleValue }}
              accessibilityActions={
                props.editable === false || props.level ? undefined : [{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]
              }
              onAccessibilityAction={(e) =>
                adjust(
                  e.nativeEvent.actionName === 'increment' ? 1 : e.nativeEvent.actionName === 'decrement' ? -1 : 0,
                  e.nativeEvent.actionName === 'activate',
                )
              }
            />
          </GestureDetector>
          <View
            pointerEvents="none"
            style={[styles.target, { left: L.x(comp.cards.x), top: L.cardsY, width: comp.buttons.w * S, height: L.cardH }]}
            accessible
            accessibilityRole="text"
            accessibilityLabel={`${COPY.remaining}, ${money(max - accessibleValue)}. ${COPY.weekly}, ${Math.ceil(Math.max(0, Math.round(max - accessibleValue)) / 40)} weeks.`}
          />
          <GestureDetector gesture={pour}>
            <View
              style={[styles.target, { left: L.x(22), top: L.buttonY, width: 234 * S, height: Math.max(48, L.buttonH) }]}
              accessible
              accessibilityRole="button"
              accessibilityLabel="Pour in"
              accessibilityHint={`Tap to add ${money((props.step ?? 10) * 10)}, or hold to keep pouring`}
              accessibilityState={{ disabled: props.editable === false || !!props.level }}
              onAccessibilityTap={() => adjust(10, true)}
            />
          </GestureDetector>
          <GestureDetector gesture={autoGesture}>
            <View
              style={[styles.target, { left: L.x(266), top: L.buttonY, width: 114 * S, height: Math.max(48, L.buttonH) }]}
              accessible
              accessibilityRole="switch"
              accessibilityLabel="Automatic weekly saving"
              accessibilityState={{ checked: autoEnabled }}
              onAccessibilityTap={toggleAuto}
            />
          </GestureDetector>
          {hasUndo && !props.children && (
            <GestureDetector gesture={undoGesture}>
              <View
                style={[
                  styles.target,
                  L.largeType
                    ? { left: heroX, top: jar.y + 17 * S, width: heroW, height: Math.max(48, toastHeight) }
                    : { left: L.x(63), top: headCY - 24, width: L.x(339) - L.x(63), height: 48 },
                ]}
                accessible
                accessibilityRole="button"
                accessibilityLabel="Undo last pour"
                onAccessibilityTap={undo}
              />
            </GestureDetector>
          )}
          {props.children && (
            <View pointerEvents="box-none" style={[styles.target, { left: heroX, top: jar.y, width: heroW }]}>
              {props.children}
            </View>
          )}
        </View>
      </ScrollView>
    </GestureHandlerRootView>
  );
}

function ScaleTick({
  amount,
  fraction,
  min,
  max,
  table,
  x,
  y0,
  sy,
  font,
  ink,
  mute,
}: {
  amount: SharedValue<number>;
  fraction: number;
  min: number;
  max: number;
  table: VesselTable;
  x: number;
  y0: number;
  sy: number;
  font: SkFont;
  ink: string;
  mute: string;
}) {
  const color = useDerivedValue(() => (Math.abs((amount.get() - min) / Math.max(1, max - min) - fraction) < 0.01 ? ink : mute));
  return (
    <Text
      text={`${Math.round(fraction * 100)}%`}
      x={x}
      y={y0 + Math.max(fraction === 1 ? 12 : 0, volumeY(fraction, table)) * sy + font.getSize() / 3}
      font={font}
      color={color}
    />
  );
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: sys.surface.page }, target: { position: 'absolute' } });
