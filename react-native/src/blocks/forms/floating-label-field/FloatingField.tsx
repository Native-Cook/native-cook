import { Canvas, Circle, Group, Line, Path, Skia, interpolateColors } from '@shopify/react-native-skia';
import { type Ref, useMemo, useState } from 'react';
import { StyleSheet, TextInput, type TextInputProps, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

import { scheduleOnUI } from 'react-native-worklets';

import {
  CHECK_MS,
  DOT_MS,
  DRAW_MS,
  ERROR_CLEAR_MS,
  ERROR_MS,
  FLOAT_MS,
  FOCUS_MS,
  type FieldPose,
  RETRACT_MS,
  SHAKE_MS,
  STRENGTH_MS,
  clamp01,
  easeOutBack,
  shakeX,
} from './floatingLabelMotion';

/*
 FloatingField — an outlined text field whose placeholder becomes its label.

 On focus the placeholder rides a slight arc up to the top border and shrinks to 78 %, a notch
 opens in the border for it, and an accent outline is drawn around the field from one side of
 the notch to the other. A valid value draws a check at the right; a failed one shakes the
 field once, turns the outline and label to the error colour and slides a helper line down. A
 secure field shows its characters as dots that pop in, and a four-segment strength bar.

 The field is a real TextInput. Everything that moves reads one `pose`:

   const field = useFieldValues();
   const pose = useFieldPose(field);
   <FloatingField label="Email" pose={pose} width={320}
     onFocus={() => focusField(field)}
     onBlur={() => blurField(field, { empty: value === '', ok: isEmail(value) })}
     helper="That address needs a domain" />

 (`failField(field)` runs the shake and the error.) A demo can instead feed `pose` from a clock
 and type through `demoText`, which fills the field without focusing it.
*/

export const FIELD_COLORS = {
  fill: '#FBF9F4',
  hairline: '#D8D1C2',
  ink: '#1E2A24',
  muted: '#7C8279',
  accent: '#1F5C45',
  accentTint: '#DFEBE2',
  error: '#B4442C',
  errorTint: '#F6E1DA',
  track: '#E4DECF',
  strength: ['#B4442C', '#C98A1B', '#6E8B3D', '#2F7D55'],
};

const RADIUS = 16;
const PAD_X = 18;
const LABEL_SIZE = 17;
const LABEL_LINE = 22;
const FLOAT_SCALE = 0.78;
/** The floated label sits 6 pt further in than the placeholder, and swings 7 pt out on the way. */
const FLOAT_DX = 6;
const ARC = 7;
const NOTCH_PAD = 6;
const INSET = 1;
const BADGE_R = 11;
const DOT = 8;
export const DOT_PITCH = 13;
const MAX_DOTS = 24;
const CARET_H = 22;
const STRENGTH_WORDS = ['Weak', 'Fair', 'Good', 'Strong'];

const EASE_OUT = Easing.out(Easing.cubic);
const EASE_IN_OUT = Easing.inOut(Easing.cubic);
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

// ---- Driving a field from real input ------------------------------------------------------------

export type FieldValues = {
  focus: SharedValue<number>;
  float: SharedValue<number>;
  lineStart: SharedValue<number>;
  lineEnd: SharedValue<number>;
  valid: SharedValue<number>;
  error: SharedValue<number>;
  shake: SharedValue<number>;
  count: SharedValue<number>;
  caret: SharedValue<number>;
  strength: SharedValue<number>;
  text: SharedValue<number>;
};

export function useFieldValues(): FieldValues {
  const focus = useSharedValue(0);
  const float = useSharedValue(0);
  const lineStart = useSharedValue(0);
  const lineEnd = useSharedValue(0);
  const valid = useSharedValue(0);
  const error = useSharedValue(0);
  const shake = useSharedValue(0);
  const count = useSharedValue(0);
  const caret = useSharedValue(0);
  const strength = useSharedValue(0);
  const text = useSharedValue(1);
  return useMemo(
    () => ({ focus, float, lineStart, lineEnd, valid, error, shake, count, caret, strength, text }),
    [focus, float, lineStart, lineEnd, valid, error, shake, count, caret, strength, text],
  );
}

/** The pose of a field driven by `useFieldValues`. */
export function useFieldPose(u: FieldValues): SharedValue<FieldPose> {
  const { focus, float, lineStart, lineEnd, valid, error, shake, count, caret, strength, text } = u;
  return useDerivedValue<FieldPose>(() => ({
    focus: focus.get(),
    float: float.get(),
    lineStart: lineStart.get(),
    lineEnd: lineEnd.get(),
    valid: valid.get(),
    error: error.get(),
    shake: shake.get(),
    frame: -1,
    count: count.get(),
    caret: caret.get(),
    strength: strength.get(),
    text: text.get(),
  }));
}

function ms(duration: number, still: boolean) {
  'worklet';
  return still ? 0 : duration;
}

/** Draws the accent outline if it is not drawn already. */
function drawOutline(u: FieldValues, still: boolean) {
  'worklet';
  const start = u.lineStart.get();
  const end = u.lineEnd.get();
  if (end - start > 0.98) return;
  if (start >= end - 0.001) {
    u.lineStart.set(0);
    u.lineEnd.set(0);
  } else {
    u.lineStart.set(withTiming(0, { duration: ms(300, still), easing: EASE_OUT }));
  }
  u.lineEnd.set(withTiming(1, { duration: ms(DRAW_MS, still), easing: EASE_IN_OUT }));
}

function focusOnUI(u: FieldValues, secure: boolean, still: boolean) {
  'worklet';
  u.focus.set(withTiming(1, { duration: ms(FOCUS_MS, still), easing: EASE_OUT }));
  u.float.set(withTiming(1, { duration: ms(FLOAT_MS, still), easing: EASE_OUT }));
  drawOutline(u, still);
  if (secure) {
    cancelAnimation(u.caret);
    u.caret.set(1);
    if (!still) {
      u.caret.set(withRepeat(withSequence(withDelay(500, withTiming(0, { duration: 140 })), withDelay(360, withTiming(1, { duration: 140 }))), -1));
    }
  }
}

function failOnUI(u: FieldValues, still: boolean) {
  'worklet';
  u.valid.set(withTiming(0, { duration: ms(150, still) }));
  u.error.set(withTiming(1, { duration: ms(ERROR_MS, still), easing: EASE_OUT }));
  if (!still) {
    u.shake.set(0);
    u.shake.set(withTiming(1, { duration: SHAKE_MS, easing: Easing.linear }));
  }
  drawOutline(u, still);
}

function blurOnUI(u: FieldValues, empty: boolean, ok: boolean, still: boolean) {
  'worklet';
  u.focus.set(withTiming(0, { duration: ms(FOCUS_MS, still) }));
  cancelAnimation(u.caret);
  u.caret.set(withTiming(0, { duration: ms(100, still) }));
  if (empty) {
    u.float.set(withTiming(0, { duration: ms(FLOAT_MS, still), easing: EASE_IN_OUT }));
    u.valid.set(withTiming(0, { duration: ms(150, still) }));
    if (u.error.get() < 0.5) u.lineStart.set(withTiming(1, { duration: ms(RETRACT_MS, still), easing: EASE_IN_OUT }));
    return;
  }
  if (ok) {
    u.valid.set(withTiming(1, { duration: ms(CHECK_MS, still), easing: EASE_OUT }));
    u.lineStart.set(withTiming(1, { duration: ms(RETRACT_MS, still), easing: EASE_IN_OUT }));
    return;
  }
  failOnUI(u, still);
}

function changeOnUI(u: FieldValues, ok: boolean, length: number, level: number, still: boolean) {
  'worklet';
  if (u.error.get() > 0) u.error.set(withTiming(0, { duration: ms(ERROR_CLEAR_MS, still) }));
  u.valid.set(withTiming(ok ? 1 : 0, { duration: ms(ok ? CHECK_MS : 150, still), easing: EASE_OUT }));
  if (length >= 0) u.count.set(withTiming(length, { duration: ms(DOT_MS, still), easing: EASE_OUT }));
  if (level >= 0) u.strength.set(withTiming(level, { duration: ms(STRENGTH_MS, still), easing: EASE_OUT }));
}

// The four calls below are made from the field's JS events. Each runs as one worklet on the UI
// thread, so what it reads (is the outline drawn? is an error showing?) is never a stale copy.

/** The field took the focus. `secure` starts the drawn caret of a password field. */
export function focusField(u: FieldValues, options: { secure?: boolean; still?: boolean } = {}) {
  scheduleOnUI(focusOnUI, u, options.secure ?? false, options.still ?? false);
}

/** Shake once, show the error colour and the helper line, keep the outline drawn. */
export function failField(u: FieldValues, options: { still?: boolean } = {}) {
  scheduleOnUI(failOnUI, u, options.still ?? false);
}

/** The field lost the focus: an empty one gets its placeholder back, a wrong one fails. */
export function blurField(u: FieldValues, state: { empty: boolean; ok: boolean; still?: boolean }) {
  scheduleOnUI(blurOnUI, u, state.empty, state.ok, state.still ?? false);
}

/** The value changed: the error clears, the check follows `ok`, a secure field counts its dots. */
export function changeField(u: FieldValues, state: { ok: boolean; length?: number; level?: number; still?: boolean }) {
  scheduleOnUI(changeOnUI, u, state.ok, state.length ?? -1, state.level ?? -1, state.still ?? false);
}

// ---- The field ------------------------------------------------------------------------------------

export type FloatingFieldProps = Omit<TextInputProps, 'style' | 'placeholder' | 'value'> & {
  label: string;
  pose: SharedValue<FieldPose>;
  width: number;
  height?: number;
  /** The line that slides down under the field when `pose.error` rises. */
  helper?: string;
  /** A password: dots instead of characters, and the strength bar under the field. */
  secure?: boolean;
  /** For demos: the value to show without focusing, or null to leave the field to the person. */
  demoText?: SharedValue<string | null>;
  /** For demos: where the drawn caret stands, pt from the start of the text; negative hides it. */
  caretX?: SharedValue<number>;
  inputRef?: Ref<TextInput>;
};

function outlinePath(w: number, h: number, notchLeft: number, notchRight: number) {
  const r = RADIUS - INSET;
  const k = r * 0.5523;
  const x0 = INSET;
  const y0 = INSET;
  const x1 = w - INSET;
  const y1 = h - INSET;
  return Skia.PathBuilder.Make()
    .moveTo(notchRight, y0)
    .lineTo(x1 - r, y0)
    .cubicTo(x1 - r + k, y0, x1, y0 + r - k, x1, y0 + r)
    .lineTo(x1, y1 - r)
    .cubicTo(x1, y1 - r + k, x1 - r + k, y1, x1 - r, y1)
    .lineTo(x0 + r, y1)
    .cubicTo(x0 + r - k, y1, x0, y1 - r + k, x0, y1 - r)
    .lineTo(x0, y0 + r)
    .cubicTo(x0, y0 + r - k, x0 + r - k, y0, x0 + r, y0)
    .lineTo(notchLeft, y0)
    .build();
}

function checkPath(cx: number, cy: number) {
  return Skia.PathBuilder.Make()
    .moveTo(cx - 4.6, cy + 0.4)
    .lineTo(cx - 1.4, cy + 3.5)
    .lineTo(cx + 4.8, cy - 3.4)
    .build();
}

export function FloatingField({ label, pose, width, height = 58, helper, secure = false, demoText, caretX, inputRef, ...input }: FloatingFieldProps) {
  const [labelWidth, setLabelWidth] = useState(0);
  const notchLeft = PAD_X;
  const notchRight = Math.min(width - 56, PAD_X + FLOAT_DX + labelWidth * FLOAT_SCALE + NOTCH_PAD);
  const badgeX = width - 27;
  const badgeY = height / 2;

  const outline = useMemo(() => outlinePath(width, height, notchLeft, notchRight), [width, height, notchLeft, notchRight]);
  const check = useMemo(() => checkPath(badgeX, badgeY), [badgeX, badgeY]);
  const badgeOrigin = useMemo(() => ({ x: badgeX, y: badgeY }), [badgeX, badgeY]);

  // ---- Border, notch, outline, badges (one small canvas) ------------------------------------------
  /** The two halves of the border under the label, pulling apart from its middle. */
  const notch = useDerivedValue(() => {
    const open = clamp01((pose.get().float - 0.2) / 0.45);
    const middle = (notchLeft + notchRight) / 2;
    const half = ((notchRight - notchLeft) / 2) * open;
    const path = Skia.PathBuilder.Make();
    if (open < 0.999) {
      path.moveTo(notchLeft, INSET);
      path.lineTo(middle - half, INSET);
      path.moveTo(middle + half, INSET);
      path.lineTo(notchRight, INSET);
    }
    return path.build();
  });
  const lineStart = useDerivedValue(() => {
    const p = pose.get();
    return Math.min(p.lineStart, p.lineEnd);
  });
  const lineEnd = useDerivedValue(() => pose.get().lineEnd);
  const lineOpacity = useDerivedValue(() => {
    const p = pose.get();
    return p.lineEnd - p.lineStart > 0.002 ? 1 : 0;
  });
  /** The outline closes the notch too while the label is still a placeholder (an empty field's error). */
  const stubOpacity = useDerivedValue(() => {
    const p = pose.get();
    const drawn = clamp01(p.lineEnd - p.lineStart);
    return drawn * drawn * drawn * drawn;
  });
  const lineColor = useDerivedValue(() => interpolateColors(pose.get().error, [0, 1], [FIELD_COLORS.accent, FIELD_COLORS.error]));

  const okOpacity = useDerivedValue(() => {
    const p = pose.get();
    return clamp01(p.valid * 2.5) * (1 - p.error);
  });
  const okTransform = useDerivedValue(() => [{ scale: 0.6 + 0.4 * easeOutBack(clamp01(pose.get().valid / 0.6)) }]);
  const okEnd = useDerivedValue(() => clamp01((pose.get().valid - 0.25) / 0.75));
  const badOpacity = useDerivedValue(() => clamp01(pose.get().error * 2));
  const badTransform = useDerivedValue(() => [{ scale: 0.6 + 0.4 * easeOutBack(clamp01(pose.get().error / 0.7)) }]);

  // ---- Views ----------------------------------------------------------------------------------------
  const shakeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: shakeX(pose.get().shake) }] }));
  const labelStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const f = p.float;
    const resting = interpolateColor(p.focus, [0, 1], [FIELD_COLORS.muted, FIELD_COLORS.accent]);
    return {
      color: interpolateColor(p.error, [0, 1], [resting, FIELD_COLORS.error]),
      transform: [
        { translateX: FLOAT_DX * f + ARC * Math.sin(Math.PI * f) },
        { translateY: (-height / 2) * f },
        { scale: 1 - (1 - FLOAT_SCALE) * f },
      ],
    };
  });
  const valueStyle = useAnimatedStyle(() => ({ opacity: pose.get().text }));
  const caretStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const x = secure ? p.count * DOT_PITCH + 1 : caretX ? caretX.get() : -1;
    return { opacity: x < 0 ? 0 : p.caret * p.text, transform: [{ translateX: Math.max(0, x) }] };
  });
  const helperStyle = useAnimatedStyle(() => {
    const e = pose.get().error;
    return { opacity: e, transform: [{ translateY: -7 * (1 - e) }] };
  });
  const strengthStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.float * (1 - p.error) };
  });
  const textProps = useAnimatedProps(() => {
    const v = demoText ? demoText.get() : null;
    return v === null ? ({} as TextInputProps) : ({ text: v } as TextInputProps);
  });

  return (
    <Animated.View style={[{ width, height }, shakeStyle]}>
      <View style={[StyleSheet.absoluteFill, styles.card]} />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Path path={outline} style="stroke" strokeWidth={1} color={FIELD_COLORS.hairline} />
        <Path path={notch} style="stroke" strokeWidth={1} color={FIELD_COLORS.hairline} />
        <Path path={outline} style="stroke" strokeWidth={2} strokeCap="round" color={lineColor} start={lineStart} end={lineEnd} opacity={lineOpacity} />
        <Path path={notch} style="stroke" strokeWidth={2} color={lineColor} opacity={stubOpacity} />
        <Group transform={okTransform} origin={badgeOrigin} opacity={okOpacity}>
          <Circle cx={badgeX} cy={badgeY} r={BADGE_R} color={FIELD_COLORS.accentTint} />
          <Path path={check} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={FIELD_COLORS.accent} start={0} end={okEnd} />
        </Group>
        <Group transform={badTransform} origin={badgeOrigin} opacity={badOpacity}>
          <Circle cx={badgeX} cy={badgeY} r={BADGE_R} color={FIELD_COLORS.errorTint} />
          <Line p1={{ x: badgeX, y: badgeY - 5 }} p2={{ x: badgeX, y: badgeY + 1.2 }} style="stroke" strokeWidth={2} strokeCap="round" color={FIELD_COLORS.error} />
          <Circle cx={badgeX} cy={badgeY + 4.8} r={1.15} color={FIELD_COLORS.error} />
        </Group>
      </Canvas>

      <AnimatedTextInput
        ref={inputRef}
        {...input}
        animatedProps={textProps}
        secureTextEntry={secure}
        caretHidden={secure}
        maxLength={secure ? MAX_DOTS : input.maxLength}
        selectionColor={FIELD_COLORS.accent}
        accessibilityLabel={label}
        style={[styles.input, { height }, secure && styles.inputSecure, valueStyle]}
      />

      {secure ? (
        <Animated.View style={[styles.dots, { top: (height - DOT) / 2 }, valueStyle]} pointerEvents="none">
          {DOT_SLOTS.map((i) => (
            <PasswordDot key={i} index={i} pose={pose} />
          ))}
        </Animated.View>
      ) : null}

      <Animated.View style={[styles.caret, { top: (height - CARET_H) / 2 }, caretStyle]} pointerEvents="none" />

      <Animated.Text
        style={[styles.label, { top: (height - LABEL_LINE) / 2 }, labelStyle]}
        onLayout={(e) => setLabelWidth(e.nativeEvent.layout.width)}
        numberOfLines={1}
        pointerEvents="none"
        accessible={false}
      >
        {label}
      </Animated.Text>

      {helper ? (
        <Animated.Text style={[styles.helper, { top: height + 6, width: width - PAD_X * 2 }, helperStyle]} numberOfLines={1} pointerEvents="none">
          {helper}
        </Animated.Text>
      ) : null}

      {secure ? (
        <Animated.View style={[styles.strength, { top: height + 11, width: width - PAD_X * 2 }, strengthStyle]} pointerEvents="none">
          <View style={styles.segments}>
            {STRENGTH_SLOTS.map((i) => (
              <StrengthSegment key={i} index={i} pose={pose} />
            ))}
          </View>
          <View style={styles.words}>
            {STRENGTH_SLOTS.map((i) => (
              <StrengthWord key={i} index={i} pose={pose} />
            ))}
          </View>
        </Animated.View>
      ) : null}
    </Animated.View>
  );
}

const DOT_SLOTS = Array.from({ length: MAX_DOTS }, (_, i) => i);
const STRENGTH_SLOTS = [0, 1, 2, 3];

/** One character of a secure field: it pops in as the count passes it. */
function PasswordDot({ index, pose }: { index: number; pose: SharedValue<FieldPose> }) {
  const style = useAnimatedStyle(() => {
    const c = clamp01(pose.get().count - index);
    return { opacity: clamp01(c * 2), transform: [{ scale: c >= 1 ? 1 : 0.4 + 0.6 * easeOutBack(c) }] };
  });
  return <Animated.View style={[styles.dot, { left: index * DOT_PITCH }, style]} />;
}

function strengthColor(strength: number) {
  'worklet';
  return interpolateColor(strength, [1, 2, 3, 4], FIELD_COLORS.strength);
}

/** One of the four segments: it fills from the left as the strength passes it. */
function StrengthSegment({ index, pose }: { index: number; pose: SharedValue<FieldPose> }) {
  const style = useAnimatedStyle(() => {
    const s = pose.get().strength;
    return { backgroundColor: strengthColor(s), transform: [{ scaleX: clamp01(s - index) }] };
  });
  return (
    <View style={styles.segment}>
      <Animated.View style={[styles.segmentFill, style]} />
    </View>
  );
}

function StrengthWord({ index, pose }: { index: number; pose: SharedValue<FieldPose> }) {
  const style = useAnimatedStyle(() => {
    const s = pose.get().strength;
    const d = s - (index + 1);
    return { color: strengthColor(s), opacity: clamp01(1 - Math.abs(d) * 2), transform: [{ translateY: -5 * d }] };
  });
  return <Animated.Text style={[styles.word, style]}>{STRENGTH_WORDS[index]}</Animated.Text>;
}

const styles = StyleSheet.create({
  card: {
    borderRadius: RADIUS,
    backgroundColor: FIELD_COLORS.fill,
    boxShadow: '0 1px 2px rgba(31,45,36,0.07), 0 8px 22px rgba(31,45,36,0.06), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  input: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    paddingLeft: PAD_X,
    paddingRight: 48,
    paddingVertical: 0,
    fontSize: LABEL_SIZE,
    fontWeight: '400',
    color: FIELD_COLORS.ink,
  },
  inputSecure: { color: 'transparent' },
  label: {
    position: 'absolute',
    left: PAD_X,
    fontSize: LABEL_SIZE,
    lineHeight: LABEL_LINE,
    fontWeight: '500',
    transformOrigin: 'left center',
  },
  caret: { position: 'absolute', left: PAD_X, width: 2, height: CARET_H, borderRadius: 1, backgroundColor: FIELD_COLORS.accent },
  dots: { position: 'absolute', left: PAD_X, height: DOT, width: MAX_DOTS * DOT_PITCH },
  dot: { position: 'absolute', top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: FIELD_COLORS.ink },
  helper: { position: 'absolute', left: PAD_X, fontSize: 12.5, lineHeight: 16, fontWeight: '500', color: FIELD_COLORS.error },
  strength: { position: 'absolute', left: PAD_X, height: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  segments: { flex: 1, flexDirection: 'row', gap: 5 },
  segment: { flex: 1, height: 4, borderRadius: 2, backgroundColor: FIELD_COLORS.track, overflow: 'hidden' },
  segmentFill: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: 2, transformOrigin: 'left center' },
  words: { width: 46, height: 16 },
  word: { position: 'absolute', right: 0, top: 0, fontSize: 12.5, lineHeight: 16, fontWeight: '600', textAlign: 'right' },
});
