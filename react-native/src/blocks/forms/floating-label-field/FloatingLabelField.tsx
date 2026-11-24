import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  FIELD_COLORS,
  type FieldValues,
  FloatingField,
  blurField,
  changeField,
  failField,
  focusField,
  useFieldValues,
} from './FloatingField';
import {
  type ButtonPose,
  type DotPose,
  FLOAT_MS,
  FOCUS_MS,
  type FieldPose,
  GLEAM_MS,
  IDLE,
  LOOP,
  READY_MS,
  RETRACT_MS,
  RETURN_LEN,
  T_FILLED,
  VALUES,
  autoButton,
  autoDot,
  autoField,
  easeInOutCubic,
  fieldMessage,
  fieldOk,
  frameAt,
  passwordLevel,
} from './floatingLabelMotion';
import { LarchArt, PAGE } from './LarchArt';

/*
 Floating Label Field — the sign-up of Larch, a trail journal: name, email, password, a button.

 The loop types the form without focusing anything (no keyboard): the name, then an address
 with its dot missing, which fails when Return is pressed (one shake, red outline, a helper
 line), is fixed and passes; then a password whose strength bar fills segment by segment. With
 all three valid the button comes alive under a gleam and is pressed. A real tap focuses the
 real field and the same animations run from what is typed.

 The fields are `FloatingField`s; this file is the screen around them and the hand-over between
 the loop (a pure function of time, floatingLabelMotion.ts) and a person.
*/

type Props = {
  /** Fills in and submits the form every LOOP seconds until the screen is touched. */
  autoPlay?: boolean;
};

const FIELDS = [
  { label: 'Full name', keyboard: 'default' as const, content: 'name' as const, capitalize: 'words' as const },
  { label: 'Email', keyboard: 'email-address' as const, content: 'emailAddress' as const, capitalize: 'none' as const },
  // Not 'newPassword': iOS would lay its strong-password highlight over the field, whose characters are drawn as dots here.
  { label: 'Password', keyboard: 'default' as const, content: 'none' as const, capitalize: 'none' as const },
];
const DEFAULT_HELPERS = [fieldMessage(0, ''), fieldMessage(1, 'mara@larchco'), fieldMessage(2, 'x')];
const DOT_SIZE = 44;
/** Zero-width space: see `pulse` below. */
const ZW = '\u200B';
/** Seconds after mount before the loop starts, so the inputs exist before anything is typed. */
const START_DELAY = 0.35;
const REST_DOT: DotPose = { target: 0, alpha: 0, scale: 1 };
const INK = FIELD_COLORS.ink;
const MUTED = FIELD_COLORS.muted;

type Geo = ReturnType<typeof makeGeo>;

function makeGeo(W: number, H: number, top: number, bottom: number) {
  const compact = H < 740;
  const margin = Math.max(24, (W - 420) / 2);
  const artH = top + (compact ? 92 : 209);
  const titleTop = artH + (compact ? 8 : 18);
  const subTop = titleTop + (compact ? 34 : 42);
  const fieldH = compact ? 52 : 62;
  const slot = compact ? 24 : 30;
  const pitch = fieldH + slot;
  const fieldsTop = subTop + (compact ? 36 : 47);
  const buttonTop = fieldsTop + pitch * 3 + (compact ? 2 : 8);
  const buttonH = compact ? 50 : 56;
  return {
    W,
    H,
    compact,
    margin,
    width: W - margin * 2,
    artH,
    markY: top + (compact ? 8 : 14),
    titleTop,
    titleSize: compact ? 26 : 30,
    subTop,
    fieldH,
    pitch,
    slot,
    fieldsTop,
    buttonTop,
    buttonH,
    legalTop: buttonTop + buttonH + 12,
    footerTop: H - bottom - (compact ? 36 : 46),
  };
}

function errorBuzz() {
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

/** Eases one field back to the empty placeholder state. */
function restField(u: FieldValues, d: number) {
  'worklet';
  u.focus.set(withTiming(0, { duration: d * 0.4 }));
  u.caret.set(0);
  u.text.set(withTiming(0, { duration: d * 0.45 }));
  u.valid.set(withTiming(0, { duration: d * 0.45 }));
  u.error.set(withTiming(0, { duration: d * 0.45 }));
  u.strength.set(withTiming(0, { duration: d * 0.6 }));
  u.lineStart.set(withTiming(1, { duration: d * 0.8 }));
  u.float.set(withDelay(d * 0.35, withTiming(0, { duration: d * 0.65, easing: Easing.inOut(Easing.cubic) })));
}

/** A field's pose: the loop's while it plays, the person's once they touch the form. */
function useScreenPose(index: number, u: FieldValues, mode: SharedValue<number>, loopTime: SharedValue<number>, still: boolean) {
  const { focus, float, lineStart, lineEnd, valid, error, shake, count, caret, strength, text } = u;
  return useDerivedValue<FieldPose>(() => {
    if (mode.get() === 0) {
      const p = autoField(index, loopTime.get());
      if (still) p.caret = 0;
      return p;
    }
    return {
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
    };
  });
}

export function FloatingLabelField({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  /** Reduced motion with autoPlay: the filled form, nothing moving. */
  const still = autoPlay && reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);

  // ---- UI-thread state ------------------------------------------------------------------------------
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  /** 0 = the loop (or rest), 1 = a person drives, 2 = easing back to the empty form. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const focused = useSharedValue(-1);
  const lift = useSharedValue(0);
  const u0 = useFieldValues();
  const u1 = useFieldValues();
  const u2 = useFieldValues();
  const uReady = useSharedValue(0);
  const uGleam = useSharedValue(0);
  const uPress = useSharedValue(0);
  const uDone = useSharedValue(0);
  /** Caret positions of the loop's typing, per field and script frame; empty until measured. */
  const caretTable = useSharedValue<number[][]>([[], []]);

  // ---- JS state: changes on keystrokes and focus only -------------------------------------------------
  const values = useRef(['', '', '']);
  const focusIndex = useRef(-1);
  const keyboard = useRef(0);
  const level = useRef(0);
  const ready = useRef(false);
  const inputs = useRef<(TextInput | null)[]>([null, null, null]);
  const [formKey, setFormKey] = useState(0);
  const [helpers, setHelpers] = useState(DEFAULT_HELPERS);
  const [welcome, setWelcome] = useState('Mara');

  const loopTime = useDerivedValue(() => (still ? T_FILLED : clock.get()));
  const pose0 = useScreenPose(0, u0, mode, loopTime, still);
  const pose1 = useScreenPose(1, u1, mode, loopTime, still);
  const pose2 = useScreenPose(2, u2, mode, loopTime, still);
  const button = useDerivedValue<ButtonPose>(() => {
    if (mode.get() === 0) return autoButton(loopTime.get());
    return { ready: uReady.get(), gleam: uGleam.get(), press: uPress.get(), done: uDone.get() };
  });
  const dot = useDerivedValue<DotPose>(() => (mode.get() === 0 && auto ? autoDot(clock.get()) : REST_DOT));

  // A React commit can put a field back to its (empty) default value, and an animated prop is only
  // sent when it changes: a name typed before such a commit stayed blank for the rest of that loop.
  // So twice a second the loop's values carry an invisible character for a moment, which sends them again.
  const pulse = useDerivedValue(() => {
    const beat = now.get() * 2;
    return beat - Math.floor(beat) < 0.08 ? ZW : '';
  });
  const demo0 = useDerivedValue(() => (mode.get() === 0 ? (VALUES[0][pose0.get().frame] ?? '') + pulse.get() : null));
  const demo1 = useDerivedValue(() => (mode.get() === 0 ? (VALUES[1][pose1.get().frame] ?? '') + pulse.get() : null));
  const demo2 = useDerivedValue(() => (mode.get() === 0 ? (VALUES[2][pose2.get().frame] ?? '') + pulse.get() : null));
  const caret0 = useDerivedValue(() => {
    const table = caretTable.get()[0];
    const f = pose0.get().frame;
    return f >= 0 && f < table.length ? table[f] : -1;
  });
  const caret1 = useDerivedValue(() => {
    const table = caretTable.get()[1];
    const f = pose1.get().frame;
    return f >= 0 && f < table.length ? table[f] : -1;
  });

  const resetForm = useCallback(() => {
    values.current = ['', '', ''];
    level.current = 0;
    ready.current = false;
    focusIndex.current = -1;
    setHelpers(DEFAULT_HELPERS);
    setWelcome('Mara');
    setFormKey((k) => k + 1);
  }, []);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto && now.get() > START_DELAY) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (m === 1) {
      if (focused.get() < 0 && now.get() - lastTouch.get() > IDLE) {
        mode.set(2);
        returnAt.set(now.get());
        const d = reduced ? 0 : RETURN_LEN * 1000 * 0.9;
        restField(u0, d);
        restField(u1, d);
        restField(u2, d);
        uReady.set(withTiming(0, { duration: d * 0.6 }));
        uDone.set(withTiming(0, { duration: d * 0.4 }));
        uPress.set(0);
      }
      return;
    }
    if (now.get() - returnAt.get() > RETURN_LEN) {
      u0.count.set(0);
      u1.count.set(0);
      u2.count.set(0);
      clock.set(0);
      mode.set(0);
      scheduleOnRN(resetForm);
    }
  });

  const touch = useCallback(() => {
    lastTouch.set(now.get());
  }, [lastTouch, now]);

  /** Enables the button when all three values pass, with a gleam the moment it happens. */
  const syncReady = useCallback(() => {
    const ok = fieldOk(0, values.current[0]) && fieldOk(1, values.current[1]) && fieldOk(2, values.current[2]);
    if (ok === ready.current) return;
    ready.current = ok;
    uReady.set(withTiming(ok ? 1 : 0, { duration: reduced ? 0 : READY_MS, easing: Easing.inOut(Easing.cubic) }));
    if (ok && !reduced) {
      uGleam.set(0);
      uGleam.set(withDelay(250, withTiming(1, { duration: GLEAM_MS, easing: Easing.linear })));
    }
  }, [reduced, uGleam, uReady]);

  const fail = useCallback(
    (i: number) => {
      const message = fieldMessage(i, values.current[i]);
      setHelpers((h) => (h[i] === message ? h : h.map((x, k) => (k === i ? message : x))));
      failField([u0, u1, u2][i], { still: reduced });
      errorBuzz();
    },
    [reduced, u0, u1, u2],
  );

  const settle = useCallback(
    (i: number) => {
      const v = values.current[i];
      const ok = fieldOk(i, v);
      if (v.length > 0 && !ok) {
        const message = fieldMessage(i, v);
        setHelpers((h) => (h[i] === message ? h : h.map((x, k) => (k === i ? message : x))));
        errorBuzz();
      }
      blurField([u0, u1, u2][i], { empty: v.length === 0, ok, still: reduced });
    },
    [reduced, u0, u1, u2],
  );

  /** On the UI thread: the loop's frame at `t` becomes the person's state, so taking over never jumps. */
  const freeze = useCallback(
    (t: number, keep: number, ok: boolean) => {
      'worklet';
      const us = [u0, u1, u2];
      for (let j = 0; j < 3; j += 1) {
        const p = autoField(j, t);
        const u = us[j];
        u.focus.set(p.focus);
        u.float.set(p.float);
        u.lineStart.set(p.lineStart);
        u.lineEnd.set(p.lineEnd);
        u.valid.set(p.valid);
        u.error.set(p.error);
        u.shake.set(0);
        u.count.set(p.count);
        u.caret.set(0);
        u.strength.set(p.strength);
        u.text.set(1);
        // A field the loop was typing in gives up the focus quietly.
        if (p.focus > 0.5 && j !== keep) {
          u.focus.set(withTiming(0, { duration: FOCUS_MS }));
          if (p.error < 0.5) u.lineStart.set(withTiming(1, { duration: RETRACT_MS }));
          if (p.frame === 0) u.float.set(withTiming(0, { duration: FLOAT_MS }));
        }
      }
      const b = autoButton(t);
      uReady.set(b.ready);
      uReady.set(withTiming(ok ? 1 : 0, { duration: READY_MS }));
      uGleam.set(b.gleam);
      uPress.set(0);
      uDone.set(b.done);
      uDone.set(withTiming(0, { duration: 200 }));
      mode.set(1);
      lastTouch.set(now.get());
    },
    [lastTouch, mode, now, u0, u1, u2, uDone, uGleam, uPress, uReady],
  );

  /** On the UI thread: caught on the way back to rest, the form comes back as the person left it. */
  const resume = useCallback(
    (has0: boolean, has1: boolean, has2: boolean, ok0: boolean, ok1: boolean, ok2: boolean, length: number, strength: number, ok: boolean) => {
      'worklet';
      const us = [u0, u1, u2];
      const has = [has0, has1, has2];
      const oks = [ok0, ok1, ok2];
      for (let j = 0; j < 3; j += 1) {
        us[j].text.set(withTiming(1, { duration: 150 }));
        us[j].float.set(withTiming(has[j] ? 1 : 0, { duration: FLOAT_MS }));
        us[j].valid.set(withTiming(oks[j] ? 1 : 0, { duration: 200 }));
      }
      u2.count.set(length);
      u2.strength.set(withTiming(strength, { duration: 200 }));
      uReady.set(withTiming(ok ? 1 : 0, { duration: READY_MS }));
      mode.set(1);
      lastTouch.set(now.get());
    },
    [lastTouch, mode, now, u0, u1, u2, uReady],
  );

  const enterUser = useCallback(
    (keep: number) => {
      const m = mode.get();
      if (m === 1) return;
      const v = values.current;
      if (m === 0) {
        const t = still ? T_FILLED : auto ? clock.get() : 0;
        for (let j = 0; j < 3; j += 1) v[j] = VALUES[j][frameAt(j, t)] ?? '';
        level.current = passwordLevel(v[2]);
        ready.current = fieldOk(0, v[0]) && fieldOk(1, v[1]) && fieldOk(2, v[2]);
        scheduleOnUI(freeze, t, keep, ready.current);
        return;
      }
      const oks = [fieldOk(0, v[0]), fieldOk(1, v[1]), fieldOk(2, v[2])];
      ready.current = oks[0] && oks[1] && oks[2];
      scheduleOnUI(resume, v[0] !== '', v[1] !== '', v[2] !== '', oks[0], oks[1], oks[2], v[2].length, level.current, ready.current);
    },
    [auto, clock, freeze, mode, resume, still],
  );

  const raise = useCallback(
    (i: number) => {
      const need = i < 0 || keyboard.current === 0 ? 0 : g.fieldsTop + i * g.pitch + g.fieldH + g.slot + 16 - (g.H - keyboard.current);
      lift.set(withTiming(Math.max(0, need), { duration: reduced ? 0 : 280, easing: Easing.out(Easing.cubic) }));
    },
    [g, lift, reduced],
  );

  useEffect(() => {
    const show = Keyboard.addListener('keyboardWillShow', (e) => {
      keyboard.current = e.endCoordinates.height;
      raise(focusIndex.current);
    });
    const hide = Keyboard.addListener('keyboardWillHide', () => {
      keyboard.current = 0;
      raise(-1);
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [raise]);

  const onFocus = (i: number) => {
    enterUser(i);
    focusIndex.current = i;
    focused.set(i);
    touch();
    focusField([u0, u1, u2][i], { secure: i === 2, still: reduced });
    raise(i);
  };
  const onBlur = (i: number) => {
    if (focusIndex.current === i) {
      focusIndex.current = -1;
      focused.set(-1);
    }
    touch();
    settle(i);
  };
  const onChange = (i: number, raw: string) => {
    const v = raw.split(ZW).join('');
    values.current[i] = v;
    touch();
    if (i === 2) {
      const next = passwordLevel(v);
      if (next > level.current) tick();
      level.current = next;
      changeField(u2, { ok: next >= 3, length: v.length, level: next, still: reduced });
    } else {
      changeField(i === 0 ? u0 : u1, { ok: fieldOk(i, v), still: reduced });
    }
    syncReady();
  };
  const onSubmit = (i: number) => {
    touch();
    if (!fieldOk(i, values.current[i])) {
      fail(i);
      return;
    }
    if (i < 2) inputs.current[i + 1]?.focus();
    else inputs.current[i]?.blur();
  };
  const onCreate = () => {
    enterUser(-1);
    touch();
    const wrong = [0, 1, 2].filter((i) => !fieldOk(i, values.current[i]));
    if (wrong.length > 0) {
      for (const i of wrong) fail(i);
      return;
    }
    Keyboard.dismiss();
    setWelcome(values.current[0].trim().split(/\s+/)[0] ?? 'Mara');
    uDone.set(withTiming(1, { duration: reduced ? 0 : 350, easing: Easing.out(Easing.cubic) }));
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  const liftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -lift.get() }] }));
  const poses = [pose0, pose1, pose2];
  const demos = [demo0, demo1, demo2];
  const carets = [caret0, caret1, undefined];
  const targets = useMemo(
    () => [
      [g.margin + g.width * 0.64, g.fieldsTop + g.fieldH / 2],
      [g.margin + g.width * 0.64, g.fieldsTop + g.pitch + g.fieldH / 2],
      [g.margin + g.width * 0.64, g.fieldsTop + g.pitch * 2 + g.fieldH / 2],
      [g.W / 2 + 34, g.buttonTop + g.buttonH / 2 + 2],
    ],
    [g],
  );

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <Pressable style={StyleSheet.absoluteFill} onPress={Keyboard.dismiss} accessible={false}>
        <Animated.View style={[StyleSheet.absoluteFill, liftStyle]}>
          <LarchArt width={W} height={g.artH} markX={g.margin} markY={g.markY} />
          <Text style={[styles.brand, { left: g.margin + 34, top: g.markY + 2 }]}>Larch</Text>

          <Text style={[styles.title, { left: g.margin, top: g.titleTop, width: g.width, fontSize: g.titleSize, lineHeight: g.titleSize + 6 }]}>Create your account</Text>
          <Text style={[styles.sub, { left: g.margin, top: g.subTop, width: g.width }]} numberOfLines={1}>
            Trail logs, maps and gear lists in one place.
          </Text>

          {FIELDS.map((f, i) => (
            <View key={`${f.label}-${formKey}`} style={[styles.field, { left: g.margin, top: g.fieldsTop + i * g.pitch }]}>
              <FloatingField
                label={f.label}
                pose={poses[i]}
                width={g.width}
                height={g.fieldH}
                helper={helpers[i]}
                secure={i === 2}
                demoText={demos[i]}
                caretX={carets[i]}
                inputRef={(r: TextInput | null) => {
                  inputs.current[i] = r;
                }}
                defaultValue=""
                onChangeText={(v) => onChange(i, v)}
                onFocus={() => onFocus(i)}
                onBlur={() => onBlur(i)}
                onSubmitEditing={() => onSubmit(i)}
                submitBehavior="submit"
                keyboardType={f.keyboard}
                textContentType={f.content}
                autoCapitalize={f.capitalize}
                autoCorrect={false}
                spellCheck={false}
                maxLength={i === 1 ? 40 : 28}
                returnKeyType={i === 2 ? 'done' : 'next'}
              />
            </View>
          ))}

          <View style={[styles.field, { left: g.margin, top: g.buttonTop }]}>
            <CreateButton
              pose={button}
              width={g.width}
              height={g.buttonH}
              welcome={welcome}
              onPress={onCreate}
              onPressIn={() => uPress.set(withTiming(1, { duration: 100 }))}
              onPressOut={() => uPress.set(withTiming(0, { duration: 180 }))}
            />
          </View>

          <Text style={[styles.legal, { left: g.margin, top: g.legalTop, width: g.width }]}>
            By continuing you agree to the <Text style={styles.legalLink}>Terms</Text> and <Text style={styles.legalLink}>Privacy Policy</Text>.
          </Text>
          <Text style={[styles.footer, { top: g.footerTop }]}>
            Already have an account? <Text style={styles.footerLink}>Sign in</Text>
          </Text>
        </Animated.View>
      </Pressable>

      <CaretMeasure table={caretTable} />
      <GhostFinger pose={dot} targets={targets} />
    </View>
  );
}

// ---- The button --------------------------------------------------------------------------------------

type ButtonProps = {
  pose: SharedValue<ButtonPose>;
  width: number;
  height: number;
  welcome: string;
  onPress: () => void;
  onPressIn: () => void;
  onPressOut: () => void;
};

const GLEAM_W = 90;

/** Disabled and flat until `ready`; then it takes its colour and shadow, and a gleam crosses it. */
function CreateButton({ pose, width, height, welcome, onPress, onPressIn, onPressOut }: ButtonProps) {
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * pose.get().press }] }));
  const liveStyle = useAnimatedStyle(() => ({ opacity: pose.get().ready }));
  const gleamStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return {
      opacity: Math.sin(Math.PI * p.gleam) * p.ready,
      transform: [{ translateX: -GLEAM_W + (width + GLEAM_W) * easeInOutCubic(p.gleam) }, { skewX: '-20deg' }],
    };
  });
  const labelStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return {
      color: interpolateColor(p.ready, [0, 1], ['#A7A698', '#F6F3EA']),
      opacity: 1 - p.done,
      transform: [{ translateY: -8 * p.done }],
    };
  });
  const doneStyle = useAnimatedStyle(() => {
    const d = pose.get().done;
    return { opacity: d, transform: [{ translateY: 8 * (1 - d) }] };
  });

  return (
    <Pressable onPress={onPress} onPressIn={onPressIn} onPressOut={onPressOut} accessibilityRole="button" accessibilityLabel="Create account">
      <Animated.View style={[{ width, height }, pressStyle]}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.buttonShadow, liveStyle]} />
        <View style={[StyleSheet.absoluteFill, styles.buttonFace]}>
          <Animated.View style={[StyleSheet.absoluteFill, liveStyle]}>
            <LinearGradient colors={['#2C7257', '#1F5C45', '#184B38']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
            <View style={styles.buttonEdge} />
          </Animated.View>
          <Animated.View style={[styles.gleam, { height: height + 20 }, gleamStyle]}>
            <LinearGradient
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.4)', 'rgba(255,255,255,0)']}
              start={{ x: 0, y: 0.5 }}
              end={{ x: 1, y: 0.5 }}
              style={StyleSheet.absoluteFill}
            />
          </Animated.View>
          <Animated.Text style={[styles.buttonText, labelStyle]}>Create account</Animated.Text>
          <Animated.View style={[StyleSheet.absoluteFill, styles.doneRow, doneStyle]}>
            <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={15} tintColor="#F6F3EA" />
            <Text style={styles.doneText} numberOfLines={1}>
              Welcome, {welcome}
            </Text>
          </Animated.View>
        </View>
      </Animated.View>
    </Pressable>
  );
}

// ---- The loop's caret and finger -----------------------------------------------------------------------

/**
 Measures where the caret stands after every value the loop types, with the same Text the field
 draws, so the drawn caret sits exactly at the end of the native text. A trailing bar keeps a
 trailing space from being trimmed; its own width is taken off again.
*/
function CaretMeasure({ table }: { table: SharedValue<number[][]> }) {
  const got = useRef<{ bar: number; rows: number[][] }>({ bar: -1, rows: [VALUES[0].map(() => -1), VALUES[1].map(() => -1)] });
  const report = () => {
    const { bar, rows } = got.current;
    if (bar < 0 || rows[0].includes(-1) || rows[1].includes(-1)) return;
    table.set(rows.map((row) => row.map((w) => Math.max(0, w - bar) + 1)));
  };
  return (
    <View style={styles.measure} pointerEvents="none" accessible={false} importantForAccessibility="no-hide-descendants">
      <Text
        style={styles.measureText}
        numberOfLines={1}
        onLayout={(e) => {
          got.current.bar = e.nativeEvent.layout.width;
          report();
        }}
      >
        |
      </Text>
      {[0, 1].map((field) =>
        VALUES[field].map((value, k) => (
          <Text
            key={`${field}-${k}`}
            style={styles.measureText}
            numberOfLines={1}
            onLayout={(e) => {
              got.current.rows[field][k] = e.nativeEvent.layout.width;
              report();
            }}
          >
            {value}|
          </Text>
        )),
      )}
    </View>
  );
}

/** A soft touch dot that taps each field and the button while the loop plays. */
function GhostFinger({ pose, targets }: { pose: SharedValue<DotPose>; targets: number[][] }) {
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const at = targets[p.target] ?? targets[0];
    return {
      opacity: p.alpha,
      transform: [{ translateX: at[0] - DOT_SIZE / 2 }, { translateY: at[1] - DOT_SIZE / 2 + (1 - p.alpha) * 8 }, { scale: p.scale }],
    };
  });
  return <Animated.View style={[styles.finger, style]} pointerEvents="none" />;
}

export type { Geo };

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  brand: { position: 'absolute', fontSize: 17, lineHeight: 22, fontWeight: '700', color: INK, letterSpacing: -0.2 },
  title: { position: 'absolute', fontWeight: '700', color: INK, letterSpacing: -0.6 },
  sub: { position: 'absolute', fontSize: 15, lineHeight: 20, color: MUTED },
  field: { position: 'absolute' },
  legal: { position: 'absolute', fontSize: 12.5, lineHeight: 17, color: MUTED, textAlign: 'center' },
  legalLink: { color: INK, fontWeight: '500' },
  footer: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 15, lineHeight: 20, color: MUTED },
  footerLink: { color: FIELD_COLORS.accent, fontWeight: '600' },
  buttonShadow: {
    borderRadius: 16,
    backgroundColor: '#1F5C45',
    boxShadow: '0 1px 2px rgba(20,60,44,0.35), 0 10px 22px rgba(20,60,44,0.26)',
  },
  buttonFace: { borderRadius: 16, overflow: 'hidden', backgroundColor: '#E4DECF', alignItems: 'center', justifyContent: 'center' },
  buttonEdge: { position: 'absolute', left: 0, right: 0, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.22)' },
  gleam: { position: 'absolute', left: 0, top: -10, width: GLEAM_W },
  buttonText: { fontSize: 17, fontWeight: '600', letterSpacing: -0.2 },
  doneRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  doneText: { fontSize: 17, fontWeight: '600', color: '#F6F3EA', letterSpacing: -0.2 },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0 },
  measureText: { position: 'absolute', left: 0, top: 0, fontSize: 17, fontWeight: '400' },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: 'rgba(30,42,36,0.16)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.7)',
    boxShadow: '0 4px 12px rgba(30,42,36,0.14)',
  },
});
