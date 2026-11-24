import {
  Blur,
  Canvas,
  Group,
  ImageShader,
  LinearGradient,
  Paint,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Text as SkiaText,
  matchFont,
  rect,
  useImage,
  vec,
  type SkFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import type { SFSymbol } from 'sf-symbols-typescript';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PixelRatio, Platform, StyleSheet, Text, useWindowDimensions, View, type AccessibilityActionEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  DRAG_A,
  DRAG_B,
  EXPOSURE,
  FIELDS,
  FIELD_COUNT,
  LIFT,
  LOOP,
  MAJOR_EVERY,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  PRESS_LEAD,
  PROJECT,
  RESET_S,
  RESET_STAGGER,
  REST,
  SNAP_SPRING,
  SPEED_TAU,
  SQUASH_MAX,
  STRETCH_MAX,
  STRETCH_SPEED,
  TICK,
  VIGNETTE,
  WARMTH,
  clamp,
  clamp01,
  digitBlur,
  filmActive,
  filmDotAlpha,
  filmFinger,
  filmPos,
  filmStretch,
  filmTapAlpha,
  filmTapPress,
  formatValue,
  odoHigh,
  odoLow,
  rollEase,
  rubber,
  scrubGain,
  seg,
  smooth,
  type FieldSpec,
} from './scrubMotion';
import { EXPOSURE_STOPS_PER_STEP, PHOTO_SKSL, VIGNETTE_PER_STEP, WARMTH_PER_STEP } from './scrubShader';

export { LOOP };

// Photo: a valley rendered in Blender for this block (see the block's notes).
const PHOTO = require('./assets/valley.jpg');
const EFFECT = Skia.RuntimeEffect.Make(PHOTO_SKSL);
const DPR = PixelRatio.get();

// ---------------------------------------------------------------- look and layout (pt)

const PAGE = '#111312';
const PANEL = '#1C1F1D';
const INK = '#F0EDE5';
const MUTE = '#8D938E';
const ACCENT = '#F2B441';
const TICK_MINOR = 'rgba(240,237,229,0.30)';
const TICK_MAJOR = 'rgba(240,237,229,0.62)';

const SIDE = 16; // screen margin
const NAV_H = 44;
const CAPTION_H = 20;
const FIELD_GAP = 10;
const FIELDS_GAP = 16; // caption to the first field
const HINT_GAP = 12;
const HINT_H = 18;
const FIELD_RADIUS = 20;
const PHOTO_RADIUS = 22;
const NUMBER_RIGHT = 18; // number's right edge from the field's
const RULER_BOTTOM = 10; // ruler baseline from the field's bottom edge
const RULER_FADE = 0.2; // share of the width the ruler fades out over, each side
const DOT = 44; // the ghost finger
const DRAG_FROM = 0.44; // where across a field the ghost finger lands
const DRAG_UP = 20; // its centre above the field's bottom edge: on the ruler, under the number
const TAP_AT = { x: 0.56, y: 0.56 }; // where on the photo it double taps

const IDLE_S = 2; // after the last touch, before the values ease back to rest
const RETURN_S = 0.7; // the ease back, then the film restarts at t = 0
const EASE_IN_OUT = Easing.inOut(Easing.cubic);
const FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
const PLUS = '+';
const MINUS = '−';
const COLUMNS = 3; // digits a field can show

/** Haptics live on the JS thread; worklets schedule these. */
function detentTick() {
  void Haptics.selectionAsync();
}
function resetTick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

/** Writes only a changed value, so a canvas that did not move is not drawn again. */
function put(sv: SharedValue<number>, v: number) {
  'worklet';
  if (sv.get() !== v) sv.set(v);
}

/** What one field is drawn from. */
type FieldValues = {
  pos: SharedValue<number>; // the value in steps, continuous
  raw: SharedValue<number>; // the same before rubber-banding, while a finger scrubs
  active: SharedValue<number>; // 0..1 pressed: ruler shown, number lifted
  stretch: SharedValue<number>; // -1..1, from the finger's speed
  speed: SharedValue<number>; // steps per second, smoothed
  prev: SharedValue<number>; // pos on the frame before
};

function useFieldValues(rest: number): FieldValues {
  const pos = useSharedValue(rest);
  const raw = useSharedValue(rest);
  const active = useSharedValue(0);
  const stretch = useSharedValue(0);
  const speed = useSharedValue(0);
  const prev = useSharedValue(rest);
  return useMemo(() => ({ pos, raw, active, stretch, speed, prev }), [active, pos, prev, raw, speed, stretch]);
}

/** The digit face and what the odometer needs to know about it. */
type DigitType = {
  font: SkFont;
  widths: Record<string, number>;
  colW: number; // one digit column
  cellH: number; // distance a digit rolls
  baseline: number; // baseline below a cell's centre
};

function makeDigitType(size: number): DigitType {
  const font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '600' });
  const chars = [...DIGITS, PLUS, MINUS, '.'];
  // getGlyphWidths, not measureText: the latter rounds each glyph to whole points
  const measured = font.getGlyphWidths(font.getGlyphIDs(chars.join('')));
  const widths: Record<string, number> = {};
  let colW = 0;
  chars.forEach((c, i) => {
    widths[c] = measured[i] ?? size * 0.6;
    if (i < 10) colW = Math.max(colW, widths[c]);
  });
  const box = font.measureText('0');
  const baseline = box.height > 0 ? -(box.y + box.height / 2) : size * 0.35;
  return { font, widths, colW: Math.ceil(colW) + 1, cellH: Math.round(size * 1.2), baseline };
}

type Props = { autoPlay?: boolean };

/**
 * Number fields you scrub like in a design tool: press one and drag sideways. A ruler slides under
 * the number, the digits roll like an odometer (and smear when the drag is fast, which also makes
 * the steps coarser), the field stretches after the finger and a spring snaps to the step on
 * release. Three of them edit a photo live through one Skia shader.
 */
export function ScrubNumberField({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 480);
  const w = contentW - SIDE * 2;
  const fieldH = compact ? 66 : 76;
  const navTop = insets.top + 4;
  const photoTop = navTop + NAV_H + (compact ? 8 : 12);
  // the photo takes the height the fields leave: close to square on a tall phone
  const fieldsBlock = FIELD_COUNT * fieldH + (FIELD_COUNT - 1) * FIELD_GAP;
  const below = 12 + CAPTION_H + FIELDS_GAP + fieldsBlock + HINT_GAP + HINT_H + Math.max(insets.bottom, 12) + 8;
  const photoH = Math.round(clamp(height - photoTop - below, w * 0.5, w * 1.25));
  const captionTop = photoTop + photoH + 12;
  const fieldsTop = captionTop + CAPTION_H + FIELDS_GAP;
  const hintTop = fieldsTop + fieldsBlock + HINT_GAP;

  const image = useImage(PHOTO);
  const digitSize = compact ? 28 : 30;
  const type = useMemo(() => makeDigitType(digitSize), [digitSize]);

  const f0 = useFieldValues(REST[EXPOSURE]);
  const f1 = useFieldValues(REST[WARMTH]);
  const f2 = useFieldValues(REST[VIGNETTE]);
  const fields = useMemo(() => [f0, f1, f2], [f0, f1, f2]);

  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const tau = useSharedValue(0); // loop time, seconds
  const clock = useSharedValue(0); // the frame clock, seconds
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const holding = useSharedValue(0); // fingers down
  const ready = useSharedValue(false); // photo decoded: the film may start

  useEffect(() => {
    if (image) ready.set(true);
  }, [image, ready]);

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    const dt = clamp(now - clock.get(), 1 / 240, 0.05);
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        for (let i = 0; i < FIELD_COUNT; i++) {
          const f = fields[i];
          f.pos.set(Math.round(f.pos.get()));
          f.active.set(0);
          f.stretch.set(0);
        }
      } else {
        let t = 0;
        if (ready.get()) {
          if (loopStart.get() < 0) loopStart.set(now);
          t = (now - loopStart.get()) % LOOP;
        }
        put(tau, t);
        for (let i = 0; i < FIELD_COUNT; i++) {
          const f = fields[i];
          put(f.pos, filmPos(i, t));
          put(f.active, filmActive(i, t));
          put(f.stretch, filmStretch(i, t));
        }
      }
    } else if (film && holding.get() <= 0) {
      if (m === MODE_MANUAL && now - lastTouch.get() > IDLE_S) {
        for (let i = 0; i < FIELD_COUNT; i++) {
          fields[i].pos.set(withTiming(REST[i], { duration: RETURN_S * 1000, easing: EASE_IN_OUT }));
        }
        returnAt.set(now);
        mode.set(MODE_RETURN);
      } else if (m === MODE_RETURN && now - returnAt.get() > RETURN_S + 0.15) {
        loopStart.set(now);
        put(tau, 0);
        mode.set(MODE_AUTO);
      }
    }
    // how fast each value is moving, for the digits' smear
    const k = 1 - Math.exp(-dt / SPEED_TAU);
    for (let i = 0; i < FIELD_COUNT; i++) {
      const f = fields[i];
      const p = f.pos.get();
      const inst = Math.abs(p - f.prev.get()) / dt;
      f.prev.set(p);
      const s = f.speed.get() + (inst - f.speed.get()) * k;
      put(f.speed, s < 0.05 ? 0 : s);
    }
  });

  /** A finger anywhere: the film stops and every value settles on the step it is nearest. */
  const take = useCallback(() => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === MODE_MANUAL) return;
    mode.set(MODE_MANUAL);
    for (let i = 0; i < FIELD_COUNT; i++) {
      const f = fields[i];
      f.pos.set(withSpring(Math.round(f.pos.get()), SNAP_SPRING));
      f.active.set(withTiming(0, { duration: 200 }));
      f.stretch.set(withTiming(0, { duration: 200 }));
    }
  }, [clock, fields, lastTouch, mode]);

  /** A double tap: one field (or all, index < 0) rolls back to where the screen opened. */
  const reset = useCallback(
    (index: number) => {
      'worklet';
      take();
      let n = 0;
      for (let i = 0; i < FIELD_COUNT; i++) {
        if (index >= 0 && i !== index) continue;
        const to = withTiming(REST[i], { duration: reduced ? 180 : RESET_S * 1000, easing: EASE_IN_OUT });
        fields[i].pos.set(n > 0 && !reduced ? withDelay(n * RESET_STAGGER * 1000, to) : to);
        n++;
      }
      scheduleOnRN(resetTick);
    },
    [fields, reduced, take],
  );

  const photoTap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDistance(16)
    .onBegin(() => {
      take();
    })
    .onEnd((_e, success) => {
      if (success) reset(-1);
    });

  const uniforms = useDerivedValue(() => ({
    size: [w, photoH],
    exposure: f0.pos.get() * EXPOSURE_STOPS_PER_STEP,
    warmth: f1.pos.get() * WARMTH_PER_STEP,
    vignette: Math.max(0, f2.pos.get() * VIGNETTE_PER_STEP),
    dpr: DPR,
  }));

  const left = (width - contentW) / 2 + SIDE;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />

      <View style={[styles.nav, { top: navTop, left, width: w }]}>
        <Text style={styles.navCancel}>Cancel</Text>
        <Text style={styles.navTitle}>Adjust</Text>
        <View style={styles.navDone}>
          <Text style={styles.navDoneText}>Done</Text>
        </View>
      </View>

      <GestureDetector gesture={photoTap}>
        <View
          style={[styles.photo, { top: photoTop, left, width: w, height: photoH }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel="A valley between snowy mountains, seen over spruce trees"
          accessibilityHint="Double tap twice to reset the adjustments"
        >
          {EFFECT && image ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <RoundedRect x={0} y={0} width={w} height={photoH} r={PHOTO_RADIUS}>
                <Shader source={EFFECT} uniforms={uniforms}>
                  <ImageShader image={image} fit="cover" rect={rect(0, 0, w, photoH)} />
                </Shader>
              </RoundedRect>
            </Canvas>
          ) : null}
          <View pointerEvents="none" style={styles.photoEdge} />
        </View>
      </GestureDetector>

      <View style={[styles.caption, { top: captionTop, left, width: w }]}>
        <Text style={styles.captionName}>Valley overlook</Text>
        <Text style={styles.captionMeta}>35 mm · f/8 · 1/640 s · ISO 100</Text>
      </View>

      {FIELDS.map((spec, i) => (
        <Field
          key={spec.label}
          index={i}
          spec={spec}
          values={fields[i]}
          type={type}
          left={left}
          top={fieldsTop + i * (fieldH + FIELD_GAP)}
          width={w}
          height={fieldH}
          reduced={reduced}
          clock={clock}
          lastTouch={lastTouch}
          holding={holding}
          onTake={take}
          onReset={reset}
        />
      ))}

      <Text style={[styles.hint, { top: hintTop, left, width: w }]}>Drag a value sideways to change it. Double-tap to reset.</Text>

      <GhostTouch
        mode={mode}
        tau={tau}
        dragX={left + w * DRAG_FROM}
        fieldY={fieldsTop + fieldH - DRAG_UP}
        fieldPitch={fieldH + FIELD_GAP}
        tapX={left + w * TAP_AT.x}
        tapY={photoTop + photoH * TAP_AT.y}
      />
    </View>
  );
}

// ---------------------------------------------------------------- one field

type FieldProps = {
  index: number;
  spec: FieldSpec;
  values: FieldValues;
  type: DigitType;
  left: number;
  top: number;
  width: number;
  height: number;
  reduced: boolean;
  clock: SharedValue<number>;
  lastTouch: SharedValue<number>;
  holding: SharedValue<number>;
  onTake: () => void;
  onReset: (index: number) => void;
};

function Field({ index, spec, values, type, left, top, width, height, reduced, clock, lastTouch, holding, onTake, onReset }: FieldProps) {
  const { pos, raw, active, stretch, speed } = values;
  const { min, max, unitsPerStep } = spec;
  const dragging = useSharedValue(false);
  const lastTick = useSharedValue(0);
  const [shown, setShown] = useState(spec.rest); // the settled step, for screen readers

  const pan = Gesture.Pan()
    .activeOffsetX([-4, 4])
    .maxPointers(1)
    .onBegin(() => {
      onTake();
      holding.set(holding.get() + 1);
      active.set(withTiming(1, { duration: 160 }));
    })
    .onStart(() => {
      // catch the value wherever its spring is
      raw.set(pos.get());
      dragging.set(true);
    })
    .onChange((e) => {
      const next = raw.get() + (e.changeX / TICK) * scrubGain(Math.abs(e.velocityX));
      // the raw value cannot run away past the limits: keep it within reach of the rubber band
      raw.set(clamp(next, min - 60, max + 60));
      pos.set(rubber(raw.get(), min, max));
      if (!reduced) {
        const target = clamp(e.velocityX / STRETCH_SPEED, -1, 1);
        stretch.set(stretch.get() + (target - stretch.get()) * 0.3);
      }
      lastTouch.set(clock.get());
    })
    .onEnd((e) => {
      const v = (e.velocityX / TICK) * scrubGain(Math.abs(e.velocityX)); // steps per second
      const p = pos.get();
      const inside = p >= min && p <= max;
      const goal = clamp(Math.round(p + (inside ? v * PROJECT : 0)), min, max);
      if (reduced) pos.set(withTiming(goal, { duration: 160 }));
      else pos.set(withSpring(goal, { ...SNAP_SPRING, velocity: inside ? v : 0 }));
      scheduleOnRN(setShown, goal);
    })
    .onFinalize(() => {
      holding.set(Math.max(0, holding.get() - 1));
      dragging.set(false);
      active.set(withTiming(0, { duration: 320 }));
      stretch.set(withSpring(0, SNAP_SPRING));
      lastTouch.set(clock.get());
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDistance(16)
    .onEnd((_e, success) => {
      if (!success) return;
      onReset(index);
      scheduleOnRN(setShown, spec.rest);
    });

  const gesture = Gesture.Simultaneous(pan, doubleTap);

  // a detent for every step that passes under a finger, at most about 14 a second
  useAnimatedReaction(
    () => Math.round(pos.get()),
    (cur, prev) => {
      if (prev == null || cur === prev || !dragging.get()) return;
      const now = clock.get();
      if (now - lastTick.get() < 0.07) return;
      lastTick.set(now);
      scheduleOnRN(detentTick);
    },
  );

  // the field grows towards the finger's direction; its trailing edge stays where it is
  const cardStyle = useAnimatedStyle(() => {
    const st = stretch.get();
    const mag = Math.abs(st);
    const sx = 1 + STRETCH_MAX * mag;
    return {
      transform: [{ translateX: ((st < 0 ? -1 : 1) * (sx - 1) * width) / 2 }, { scaleX: sx }, { scaleY: 1 - SQUASH_MAX * mag }],
    };
  });
  const glowStyle = useAnimatedStyle(() => ({ opacity: active.get() }));

  // ---- the canvas: ruler and digits
  const cy = height / 2;
  const rulerY = height - RULER_BOTTOM;
  const centre = width / 2;
  const { colW, widths, font, baseline } = type;
  const minDigits = spec.decimals + 1;
  const xr = width - NUMBER_RIGHT;
  const dotW = spec.decimals > 0 ? widths['.'] : 0;
  const colLeft = [xr - colW, xr - colW * 2, xr - colW * 3 - dotW];
  const signLeft = colLeft[COLUMNS - 1] - Math.max(widths[PLUS], widths[MINUS]) - 1.5;

  const ruler = useMemo(() => {
    const minor = Skia.PathBuilder.Make();
    const major = Skia.PathBuilder.Make();
    for (let s = min; s <= max; s++) {
      const x = s * TICK;
      if (s === 0) continue;
      const long = s % MAJOR_EVERY === 0;
      (long ? major : minor).moveTo(x, 0).lineTo(x, long ? -9 : -5);
    }
    return { minor: minor.build(), major: major.build() };
  }, [max, min]);

  const absUnits = useDerivedValue(() => Math.abs(pos.get()) * unitsPerStep);
  const sigma = useDerivedValue(() => (reduced ? 0 : digitBlur(speed.get())));
  const rulerShift = useDerivedValue(() => [{ translateX: centre - pos.get() * TICK }]);
  const numberLift = useDerivedValue(() => [{ translateY: -LIFT * active.get() }]);
  const plusOpacity = useDerivedValue(() => clamp01(pos.get() / 0.5));
  const minusOpacity = useDerivedValue(() => clamp01(-pos.get() / 0.5));
  // the sign sits against the first digit that shows
  const signShift = useDerivedValue(() => {
    let hidden = 0;
    const x = absUnits.get();
    for (let k = minDigits; k < COLUMNS; k++) hidden += (1 - clamp01(odoHigh(x, k, unitsPerStep))) * colW;
    return [{ translateX: hidden }];
  });

  const onAccessibilityAction = (e: AccessibilityActionEvent) => {
    const by = e.nativeEvent.actionName === 'increment' ? 1 : e.nativeEvent.actionName === 'decrement' ? -1 : 0;
    if (by === 0) return;
    const goal = Math.min(max, Math.max(min, shown + by));
    setShown(goal);
    pos.set(withTiming(goal, { duration: 160 }));
  };

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[styles.field, { left, top, width, height }, cardStyle]}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={spec.label}
        accessibilityValue={{ text: `${formatValue(spec, shown)} ${spec.accessibilityUnit}`.trim() }}
        accessibilityHint="Drag sideways to change. Double tap twice to reset."
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={onAccessibilityAction}
      >
        <Animated.View pointerEvents="none" style={[styles.fieldGlow, glowStyle]} />
        <View style={styles.labelRow} pointerEvents="none">
          <SymbolView name={spec.symbol as SFSymbol} size={17} weight="medium" tintColor={MUTE} type="monochrome" style={styles.labelIcon} />
          <Text style={styles.label} maxFontSizeMultiplier={1.2}>
            {spec.label}
          </Text>
        </View>

        {/* The ruler has a canvas of its own, faded by its view: every step a tick, the value under a
            fixed index in the middle. Its edges are faded inside one layer (a gradient with dstIn). */}
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, glowStyle]}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Group layer>
              <Group transform={rulerShift}>
                <Group transform={[{ translateY: rulerY }]}>
                  <Path path={ruler.minor} style="stroke" strokeWidth={1} color={TICK_MINOR} />
                  <Path path={ruler.major} style="stroke" strokeWidth={1.5} strokeCap="round" color={TICK_MAJOR} />
                  {min < 0 ? <RoundedRect x={-1} y={-12} width={2} height={12} r={1} color={INK} /> : null}
                </Group>
              </Group>
              <Rect x={0} y={rulerY - 16} width={width} height={18} blendMode="dstIn">
                <LinearGradient
                  start={vec(0, 0)}
                  end={vec(width, 0)}
                  colors={['transparent', 'white', 'white', 'transparent']}
                  positions={[0, RULER_FADE, 1 - RULER_FADE, 1]}
                />
              </Rect>
            </Group>
            <RoundedRect x={centre - 1} y={rulerY - 15} width={2} height={15} r={1} color={ACCENT} />
          </Canvas>
        </Animated.View>

        {/* The number, always shown: one strip of digits per column, right-aligned at a fixed inset. */}
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Group transform={numberLift}>
            {spec.signed ? (
              <Group transform={signShift}>
                <SkiaText text={PLUS} font={font} x={signLeft} y={cy + baseline} color={INK} opacity={plusOpacity} />
                <SkiaText text={MINUS} font={font} x={signLeft} y={cy + baseline} color={INK} opacity={minusOpacity} />
              </Group>
            ) : null}
            {spec.decimals > 0 ? <SkiaText text="." font={font} x={xr - colW * 2 - dotW} y={cy + baseline} color={INK} /> : null}
            {colLeft.map((x, k) => (
              <DigitColumn
                key={k}
                k={k}
                unitsPerStep={unitsPerStep}
                always={k < minDigits}
                absUnits={absUnits}
                sigma={sigma}
                type={type}
                left={x}
                cy={cy}
              />
            ))}
          </Group>
        </Canvas>
      </Animated.View>
    </GestureDetector>
  );
}

// ---------------------------------------------------------------- one digit of the odometer

type ColumnProps = {
  k: number; // 0 = the last digit
  unitsPerStep: number;
  always: boolean; // shown even when it is a leading zero
  absUnits: SharedValue<number>;
  sigma: SharedValue<number>;
  type: DigitType;
  left: number;
  cy: number;
};

/**
 * A strip of digits behind a window one digit tall. The strip's position is the odometer's: the last
 * digit turns with the value, the others only while the digits below them wrap. The two fastest
 * columns are drawn through a vertical blur whose size follows the value's speed.
 */
function DigitColumn({ k, unitsPerStep, always, absUnits, sigma, type, left, cy }: ColumnProps) {
  const { font, widths, colW, cellH, baseline } = type;
  const glyphs = useMemo(() => {
    const cycle = k === 0 ? DIGITS.filter((_d, i) => i % unitsPerStep === 0) : DIGITS;
    return [...cycle, cycle[0]]; // the first glyph again, for the wrap
  }, [k, unitsPerStep]);
  const n = glyphs.length - 1;

  const p = useDerivedValue(() => (k === 0 ? odoLow(absUnits.get(), unitsPerStep) : odoHigh(absUnits.get(), k, unitsPerStep)));
  const strip = useDerivedValue(() => {
    // a digit rests on its line and turns in the middle half of a step
    const v = rollEase(p.get());
    return [{ translateY: -(v - Math.floor(v / n) * n) * cellH }];
  });
  const blur = useDerivedValue(() => {
    const s = sigma.get();
    if (k === 0) return { x: 0, y: s };
    const v = p.get();
    const f = v - Math.floor(v);
    return { x: 0, y: s * 4 * f * (1 - f) }; // only while this column is turning
  });
  // a leading column fades in as it turns from nothing to 1 (set on the glyphs: no group opacity
  // around the blur layer)
  const opacity = useDerivedValue(() => (always ? 1 : clamp01(p.get())));

  const texts = glyphs.map((g, i) => (
    <SkiaText key={i} text={g} font={font} x={left + (colW - widths[g]) / 2} y={cy + i * cellH + baseline} color={INK} opacity={opacity} />
  ));
  const slot = rect(left - 1, cy - cellH / 2 - 1, colW + 2, cellH + 2);

  return (
    <Group clip={slot}>
      {k < 2 ? (
        <Group
          layer={
            <Paint>
              <Blur blur={blur} />
            </Paint>
          }
        >
          <Group transform={strip}>{texts}</Group>
        </Group>
      ) : (
        <Group transform={strip}>{texts}</Group>
      )}
    </Group>
  );
}

// ---------------------------------------------------------------- the ghost finger

type GhostProps = {
  mode: SharedValue<number>;
  tau: SharedValue<number>;
  dragX: number;
  fieldY: number;
  fieldPitch: number;
  tapX: number;
  tapY: number;
};

/** In the film a soft dot shows the gesture: a press and a sideways drag on each field, a double tap on the photo. */
function GhostTouch({ mode, tau, dragX, fieldY, fieldPitch, tapX, tapY }: GhostProps) {
  const style = useAnimatedStyle(() => {
    if (mode.get() !== MODE_AUTO) return { opacity: 0, transform: [{ translateX: tapX - DOT / 2 }, { translateY: tapY - DOT / 2 }, { scale: 1 }] };
    const t = tau.get();
    let alpha = filmTapAlpha(t);
    let x = tapX;
    let y = tapY;
    let press = filmTapPress(t);
    for (let i = 0; i < FIELD_COUNT; i++) {
      const a = filmDotAlpha(i, t);
      if (a > alpha) {
        alpha = a;
        x = dragX + filmFinger(i, t);
        y = fieldY + i * fieldPitch;
        press = smooth(seg(t, DRAG_A[i] - PRESS_LEAD, DRAG_A[i] - 0.02)) * (1 - smooth(seg(t, DRAG_B[i], DRAG_B[i] + 0.12)));
      }
    }
    return {
      opacity: alpha,
      transform: [{ translateX: x - DOT / 2 }, { translateY: y - DOT / 2 }, { scale: 1 - 0.16 * press }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.dot, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  nav: { position: 'absolute', height: NAV_H, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navCancel: { color: MUTE, fontSize: 17, width: 72 },
  navTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  navDone: { width: 72, alignItems: 'flex-end' },
  navDoneText: {
    color: PAGE,
    fontSize: 15,
    fontWeight: '600',
    backgroundColor: ACCENT,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    overflow: 'hidden',
  },
  photo: {
    position: 'absolute',
    borderRadius: PHOTO_RADIUS,
    backgroundColor: PANEL,
    boxShadow: '0 2px 4px rgba(0,0,0,0.35), 0 18px 40px rgba(0,0,0,0.35)',
  },
  photoEdge: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    borderRadius: PHOTO_RADIUS,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  caption: { position: 'absolute', height: CAPTION_H, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 4 },
  captionName: { color: INK, fontSize: 14, fontWeight: '600' },
  captionMeta: { color: MUTE, fontSize: 13, fontVariant: ['tabular-nums'] },
  field: {
    position: 'absolute',
    borderRadius: FIELD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: PANEL,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(255,255,255,0.10)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.40), 0 10px 24px rgba(0,0,0,0.22)',
  },
  fieldGlow: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    borderRadius: FIELD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(240,237,229,0.045)',
    borderWidth: 1,
    borderColor: 'rgba(242,180,65,0.55)',
  },
  labelRow: { position: 'absolute', left: 16, top: 0, bottom: 0, flexDirection: 'row', alignItems: 'center', gap: 9 },
  labelIcon: { width: 20, height: 20 },
  label: { color: INK, fontSize: 16, fontWeight: '500', letterSpacing: -0.2 },
  hint: { position: 'absolute', height: HINT_H, color: MUTE, fontSize: 13, textAlign: 'center' },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: 'rgba(240,237,229,0.22)',
    borderWidth: 1.5,
    borderColor: 'rgba(240,237,229,0.55)',
  },
});
