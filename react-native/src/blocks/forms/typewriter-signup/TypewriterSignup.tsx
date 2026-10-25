import { matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Keyboard, Platform, Pressable, StyleSheet, TextInput, View, useWindowDimensions, type TextInputProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  COLORS,
  LINE_H,
  PAPER_W,
  TEXT_X,
  TOP_PAD,
  makeBackground,
  makeBodyBack,
  makeCarriage,
  makeDeck,
  makeGeo,
  makePaper,
} from './typewriterSignupArt';
import {
  LOOP,
  SCRIPT,
  SHEET_AWAY,
  type Pose,
  autoPose,
  barFor,
  bellCurve,
  filledPose,
  restPose,
  strikeCurve,
} from './typewriterSignupMotion';
import { MAX_COLS, TypewriterCanvas } from './TypewriterCanvas';

/*
 Typewriter Sign Up — a writing app's sign-up where the form is typed onto a sheet in a desk
 typewriter. Each keystroke swings a typebar up out of the basket to the platen (70 ms up,
 110 ms back), the carriage steps one glyph left on a stiff spring, and the glyph lands with a
 little ink spread and its own baseline jitter and ink density. Moving to the next field is a
 carriage return with the margin bell; "Pull the sheet" (or dragging the sheet up) pulls it out
 of the carriage, and a blank one feeds back in.

 One Canvas draws the desk, header, sheet, carriage, basket and bell; the three fields and the
 CTA are native views over it. Per frame only transforms, glyph positions and two small paths
 change. The script is in typewriterSignupMotion.ts.
*/

type Props = {
  /** Types the three fields, pulls the sheet and feeds a blank one, every LOOP seconds. */
  autoPlay?: boolean;
};

const FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
/** Seconds after the last touch before the screen eases back and the loop restarts. */
const IDLE = 2;
const RETURN_LEN = 0.85;
const FIELDS = [
  { label: 'Name', placeholder: 'Your pen name', keyboard: 'default' as const, content: 'name' as const },
  { label: 'Email', placeholder: 'you@inkwell.co', keyboard: 'email-address' as const, content: 'emailAddress' as const },
  { label: 'Password', placeholder: 'At least 8 characters', keyboard: 'default' as const, content: 'newPassword' as const },
];
const EASE = Easing.bezier(0.77, 0, 0.175, 1);
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

function rubber(x: number, limit: number) {
  'worklet';
  if (x <= limit) return x;
  const over = x - limit;
  return limit + over / (1 + over / 120);
}

function selectTick() {
  Haptics.selectionAsync().catch(() => {});
}

export function TypewriterSignup({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);

  const fonts = useMemo(() => {
    const typed = matchFont({ fontFamily: MONO, fontSize: 15, fontWeight: '500' });
    return {
      typed,
      label: matchFont({ fontFamily: MONO, fontSize: 8.5, fontWeight: '600' }),
      header: matchFont({ fontFamily: MONO, fontSize: 8, fontWeight: '500' }),
      plate: matchFont({ fontFamily: MONO, fontSize: 9, fontWeight: '700' }),
      title: matchFont({ fontFamily: FAMILY, fontSize: g.compact ? 25 : 28, fontWeight: '700' }),
      small: matchFont({ fontFamily: FAMILY, fontSize: 15, fontWeight: '400' }),
      kicker: matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '700' }),
    };
  }, [g.compact]);
  const adv = useMemo(() => fonts.typed.getGlyphWidths(fonts.typed.getGlyphIDs('M'))[0] ?? 9, [fonts]);
  const art = useMemo(
    () => ({
      background: makeBackground(g, fonts.title, fonts.small, fonts.kicker),
      paper: makePaper(fonts.label, fonts.header),
      carriage: makeCarriage(g),
      body: makeBodyBack(g),
      deck: makeDeck(g, fonts.plate),
    }),
    [g, fonts],
  );
  const scriptIds = useMemo(() => SCRIPT.map((s) => fonts.typed.getGlyphIDs(s)), [fonts]);

  // ---- UI-thread state -------------------------------------------------------------------
  const now = useSharedValue(0);
  /** Follows `now` only while a strike or the bell is still moving, so a still screen does not redraw. */
  const anim = useSharedValue(0);
  const clock = useSharedValue(0);
  /** 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const focused = useSharedValue(-1);
  const dragging = useSharedValue(0);
  const uIds = useSharedValue<number[][]>([[], [], []]);
  const uN = useSharedValue<number[]>([0, 0, 0]);
  const uLine = useSharedValue(0);
  const uCol = useSharedValue(0);
  const uStrikeAt = useSharedValue(-9);
  const uBar = useSharedValue(4);
  const uBellAt = useSharedValue(-9);
  const uPull = useSharedValue(0);
  const uFlex = useSharedValue(0);
  const uDone = useSharedValue(0);
  const uInk = useSharedValue(1);
  const uPress = useSharedValue(0);

  // ---- JS state: changes only on keystrokes, focus and the pull -----------------------------
  // What the fields hold (the inputs are uncontrolled); a new `formKey` remounts them empty.
  const fieldValues = useSharedValue(['', '', '']);
  const [formKey, setFormKey] = useState(0);
  const inputs = useRef<(TextInput | null)[]>([null, null, null]);

  const resetForm = useCallback(() => {
    fieldValues.set(['', '', '']);
    setFormKey((k) => k + 1);
  }, [fieldValues]);

  /** Freezes the current loop frame into the user state, so taking over never jumps. */
  const enterUser = useCallback(() => {
    if (mode.get() === 1) return;
    const p = mode.get() === 0 ? (auto ? autoPose(clock.get()) : restPose()) : restPose();
    const vals = [SCRIPT[0].slice(0, p.n0), SCRIPT[1].slice(0, p.n1), 'x'.repeat(p.n2)];
    if (p.pull >= SHEET_AWAY - 1) {
      vals[0] = '';
      vals[1] = '';
      vals[2] = '';
    }
    uIds.set([scriptIds[0].slice(0, vals[0].length), scriptIds[1].slice(0, vals[1].length), scriptIds[2].slice(0, vals[2].length)]);
    uN.set([vals[0].length, vals[1].length, vals[2].length]);
    uLine.set(p.line);
    uCol.set(p.col);
    uPull.set(p.pull);
    uFlex.set(0);
    uDone.set(p.done);
    uInk.set(1);
    uBellAt.set(-9);
    uStrikeAt.set(-9);
    fieldValues.set(vals);
    mode.set(1);
    lastTouch.set(now.get());
    if (p.pull >= SHEET_AWAY - 1 || p.pull > 1) uPull.set(withTiming(0, { duration: reduced ? 0 : 700, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [auto, clock, fieldValues, lastTouch, mode, now, reduced, scriptIds, uBellAt, uCol, uDone, uFlex, uIds, uInk, uLine, uN, uPull, uStrikeAt]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (now.get() - Math.max(uStrikeAt.get(), uBellAt.get()) < 1.4) anim.set(now.get());
    if (m === 1) {
      if (focused.get() < 0 && dragging.get() === 0 && now.get() - lastTouch.get() > IDLE) {
        mode.set(2);
        returnAt.set(now.get());
        const d = reduced ? 0 : 800;
        uInk.set(withTiming(0, { duration: reduced ? 0 : 350 }));
        uCol.set(withTiming(0, { duration: d, easing: EASE }));
        uLine.set(withTiming(0, { duration: d, easing: EASE }));
        uPull.set(withTiming(0, { duration: d, easing: EASE }));
        uDone.set(withTiming(0, { duration: 400 }));
      }
      return;
    }
    if (now.get() - returnAt.get() > RETURN_LEN) {
      uN.set([0, 0, 0]);
      uInk.set(1);
      clock.set(0);
      mode.set(0);
      scheduleOnRN(resetForm);
    }
  });

  const pose = useDerivedValue<Pose>(() => {
    const m = mode.get();
    if (m === 0) return auto ? autoPose(clock.get()) : reduced && autoPlay ? filledPose() : restPose();
    const p = restPose();
    const n = uN.get();
    p.n0 = n[0];
    p.n1 = n[1];
    p.n2 = n[2];
    p.line = uLine.get();
    p.col = uCol.get();
    const tau = anim.get() - uStrikeAt.get();
    p.strike = reduced ? 0 : strikeCurve(tau);
    p.inkAge = tau;
    p.bar = uBar.get();
    const b = bellCurve(anim.get() - uBellAt.get());
    p.bell = reduced ? 0 : b[0];
    p.bellGlow = b[1];
    p.pull = uPull.get();
    p.flex = uFlex.get();
    p.done = uDone.get();
    p.press = uPress.get();
    return p;
  });

  // ---- Drawing ---------------------------------------------------------------------------
  const paperLeft = useDerivedValue(() => g.cx - TEXT_X - pose.get().col * adv - adv / 2);
  const paperTop = useDerivedValue(() => g.sy - TOP_PAD - pose.get().line * LINE_H - pose.get().pull);

  // ---- Fields --------------------------------------------------------------------------
  const textProps0 = useAnimatedProps(() => (mode.get() === 0 ? ({ text: SCRIPT[0].slice(0, pose.get().n0) } as TextInputProps) : ({} as TextInputProps)));
  const textProps1 = useAnimatedProps(() => (mode.get() === 0 ? ({ text: SCRIPT[1].slice(0, pose.get().n1) } as TextInputProps) : ({} as TextInputProps)));
  const textProps2 = useAnimatedProps(() => (mode.get() === 0 ? ({ text: 'x'.repeat(pose.get().n2) } as TextInputProps) : ({} as TextInputProps)));
  const textProps = [textProps0, textProps1, textProps2];
  const fieldsStyle = useAnimatedStyle(() => ({ opacity: mode.get() === 0 ? pose.get().fields : 1 }));
  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * pose.get().press }] }));
  const ctaIdle = useAnimatedStyle(() => ({ opacity: 1 - pose.get().done, transform: [{ translateY: -6 * pose.get().done }] }));
  const ctaDone = useAnimatedStyle(() => ({ opacity: pose.get().done, transform: [{ translateY: 6 * (1 - pose.get().done) }] }));

  const touch = useCallback(() => {
    lastTouch.set(now.get());
  }, [lastTouch, now]);

  const onFocus = (l: number) => {
    enterUser();
    const prev = Math.round(uLine.get());
    focused.set(l);
    touch();
    const len = Math.min(fieldValues.get()[l].length, MAX_COLS);
    const d = reduced ? 0 : 450;
    uLine.set(withTiming(l, { duration: d, easing: EASE }));
    uCol.set(withTiming(len, { duration: d, easing: EASE }));
    if (l !== prev) uBellAt.set(now.get());
  };
  const onBlur = () => {
    focused.set(-1);
    touch();
  };
  const onChange = (l: number, v: string) => {
    const old = fieldValues.get()[l];
    const next = fieldValues.get().slice();
    next[l] = v;
    fieldValues.set(next);
    const shown = l === 2 ? '•'.repeat(v.length) : v;
    const lines = uIds.get().slice();
    lines[l] = fonts.typed.getGlyphIDs(shown);
    uIds.set(lines);
    const n = uN.get().slice();
    n[l] = v.length;
    uN.set(n);
    touch();
    const col = Math.min(v.length, MAX_COLS);
    if (v.length > old.length && v.length <= MAX_COLS) {
      uStrikeAt.set(now.get());
      uBar.set(barFor(l, v.length - 1));
      selectTick();
      if (v.length === MAX_COLS - 3) uBellAt.set(now.get());
    }
    uCol.set(reduced ? col : withSpring(col, { damping: 14, stiffness: 700, mass: 0.5 }));
  };

  const feedBlank = useCallback(() => {
    resetForm();
    uIds.set([[], [], []]);
    uN.set([0, 0, 0]);
    uLine.set(0);
    uCol.set(0);
    uPull.set(withTiming(0, { duration: reduced ? 0 : 900, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
    lastTouch.set(now.get());
  }, [lastTouch, now, reduced, resetForm, uCol, uIds, uLine, uN, uPull]);

  const pulled = useCallback(() => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    uDone.set(withTiming(1, { duration: 300 }));
    setTimeout(feedBlank, 900);
  }, [feedBlank, uDone]);

  const pullOut = useCallback(
    (velocity: number) => {
      'worklet';
      const d = reduced ? 0 : Math.max(260, Math.min(520, 520 - velocity * 0.12));
      uFlex.set(withTiming(0, { duration: d }));
      uPull.set(
        withTiming(SHEET_AWAY, { duration: d, easing: Easing.bezier(0.33, 0, 0.2, 1) }, (finished) => {
          if (finished) scheduleOnRN(pulled);
        }),
      );
    },
    [pulled, reduced, uFlex, uPull],
  );

  const onCta = () => {
    enterUser();
    Keyboard.dismiss();
    touch();
    pullOut(0);
  };

  const startY = useSharedValue(0);
  const pan = Gesture.Pan()
    .onBegin((e) => {
      // Touches are in screen points; the machine is drawn scaled by g.scale.
      const x = e.x / g.scale;
      const y = e.y / g.scale;
      const left = paperLeft.get();
      const top = paperTop.get();
      const inside = x > left && x < left + PAPER_W && y > top && y < g.platenY;
      dragging.set(inside ? 1 : 0);
      if (inside) scheduleOnRN(enterUser);
      startY.set(e.y);
    })
    .onUpdate((e) => {
      if (dragging.get() === 0) return;
      lastTouch.set(now.get());
      const up = rubber(Math.max(0, -e.translationY / g.scale), 70);
      const down = Math.min(0, -e.translationY / g.scale);
      uPull.set(up + down / (1 + -down / 12));
      uFlex.set(Math.max(-1, Math.min(1, -e.velocityY / 1600)));
    })
    .onEnd((e) => {
      if (dragging.get() === 0) return;
      if (-e.translationY > 90 || e.velocityY < -700) pullOut(e.velocityY);
      else {
        uPull.set(withSpring(0, { velocity: -e.velocityY / g.scale, damping: 16, stiffness: 260 }));
        uFlex.set(withSpring(0, { damping: 12, stiffness: 200 }));
      }
    })
    .onFinalize(() => {
      dragging.set(0);
      lastTouch.set(now.get());
    });

  const fieldH = g.compact ? 52 : 56;

  return (
    <View style={[styles.root, { backgroundColor: COLORS.desk[1] }]}>
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel="A sheet in a typewriter. Your entries are typed onto it. Drag the sheet up to pull it out."
        >
          <TypewriterCanvas g={g} W={W} art={art} fonts={fonts} adv={adv} pose={pose} mode={mode} uIds={uIds} uInk={uInk} scriptIds={scriptIds} paperLeft={paperLeft} paperTop={paperTop} />
        </View>
      </GestureDetector>

      <View style={[styles.form, { top: g.formTop, paddingBottom: insets.bottom + 16 }]}>
        <Animated.View style={[styles.fields, fieldsStyle]}>
          {FIELDS.map((f, l) => (
            <View key={`${f.label}-${formKey}`} style={[styles.field, { height: fieldH }]}>
              <Animated.Text style={styles.label}>{f.label.toUpperCase()}</Animated.Text>
              <AnimatedTextInput
                ref={(r: TextInput | null) => {
                  inputs.current[l] = r;
                }}
                animatedProps={textProps[l]}
                defaultValue=""
                onChangeText={(v) => onChange(l, v)}
                onFocus={() => onFocus(l)}
                onBlur={onBlur}
                placeholder={f.placeholder}
                placeholderTextColor="rgba(241,232,216,0.28)"
                keyboardType={f.keyboard}
                textContentType={f.content}
                secureTextEntry={l === 2}
                autoCapitalize={l === 0 ? 'words' : 'none'}
                autoCorrect={false}
                maxLength={l === 1 ? 40 : 24}
                returnKeyType={l === 2 ? 'done' : 'next'}
                onSubmitEditing={() => (l < 2 ? inputs.current[l + 1]?.focus() : inputs.current[l]?.blur())}
                selectionColor={COLORS.brass}
                style={styles.input}
                accessibilityLabel={f.label}
              />
            </View>
          ))}
        </Animated.View>
        <Pressable
          onPress={onCta}
          onPressIn={() => uPress.set(withTiming(1, { duration: 120 }))}
          onPressOut={() => uPress.set(withTiming(0, { duration: 160 }))}
          accessibilityRole="button"
          accessibilityLabel="Pull the sheet"
        >
          <Animated.View style={[styles.cta, { height: g.compact ? 48 : 52 }, ctaStyle]}>
            <LinearGradient colors={['#f7efdf', '#ead9bb', '#d9c39b']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
            <Animated.View style={[styles.ctaRow, ctaIdle]}>
              <SymbolView name={{ ios: 'arrow.up.doc', android: 'upload_file', web: 'upload_file' }} size={17} tintColor={COLORS.ink} />
              <Animated.Text style={styles.ctaText}>Pull the sheet</Animated.Text>
            </Animated.View>
            <Animated.View style={[styles.ctaRow, StyleSheet.absoluteFill, ctaDone]}>
              <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={16} tintColor={COLORS.ink} />
              <Animated.Text style={styles.ctaText}>Draft started</Animated.Text>
            </Animated.View>
          </Animated.View>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  form: { position: 'absolute', left: 24, right: 24, gap: 16 },
  fields: { gap: 8 },
  field: {
    justifyContent: 'flex-end',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(241,232,216,0.22)',
    paddingBottom: 6,
    gap: 2,
  },
  label: { color: 'rgba(207,174,109,0.9)', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  input: { color: COLORS.cream, fontSize: 17, fontWeight: '500', paddingVertical: 4, paddingHorizontal: 0 },
  cta: {
    borderRadius: 999,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 10px 28px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.7)',
  },
  ctaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  ctaText: { color: COLORS.ink, fontSize: 17, fontWeight: '600' },
});
