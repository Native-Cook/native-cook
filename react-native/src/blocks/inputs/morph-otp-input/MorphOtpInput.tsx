import { BlurMask, Canvas, Circle, Group, Line, matchFont, Path, RoundedRect, Skia, Text as SkiaText, type SkFont, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { type ComponentProps, useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  Easing,
  interpolateColor,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type Props = {
  /** The code that unlocks it. */
  code?: string;
  /** Where the code was sent, shown under the title. */
  destination?: string;
  /** Types a wrong code, shakes, types the right one, merges into a check, then starts over. */
  autoPlay?: boolean;
  /** Called once the right code is in. */
  onVerified?: () => void;
};

type Phase = 'idle' | 'error' | 'success';

const LENGTH = 6;
const DIGITS = /[^0-9]/g;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const BACKGROUND = '#0c0d10';
const ACCENT = '#7cf2c5';
const ERROR = '#ff5a5f';
const CELL_FILL = '#16171c';
const CELL_FILLED = '#1d1e24';
const CELL_ERROR = '#35161a';
const INK = '#0c0d10';

// ── Cells (pt) ───────────────────────────────────────────────────────────────────────────────────
const CELL_MAX_W = 48;
const CELL_H = 64;
const CELL_GAP = 9;
const CELL_RADIUS = 15;
// Hairline borders are drawn half a stroke inside the cell so they land on whole device pixels.
const BORDER = 1;
// The focused cell lights its border mint, and a mint copy of that outline collapses into this
// caret, which glows softly and breathes. A cell that loses focus lets its caret fade and shrink
// in CARET_LEAVE ms; it never grows back into an outline.
const CARET_W = 2.5;
const CARET_H = 28;
const CARET_BREATH = 1.1; // s per breath
const CARET_GLOW = 6; // blur radius of the caret's glow, pt
// Critically damped so the caret settles without a wobble; the outline only lights to half
// strength on its way, so each keystroke reads as a quiet hand-off rather than a flash.
const FOCUS_SPRING = { duration: 420, dampingRatio: 1 };
const FOCUS_IN = 180; // ms for the mint outline to light up
const CARET_LEAVE = 200; // ms
const ACTIVE_BORDER = 'rgba(124,242,197,0.34)';

// ── Digits ───────────────────────────────────────────────────────────────────────────────────────
const DIGIT_SIZE = 30;
// A digit settles into its cell: it rises ROLL_FROM pt while it fades in out of the faintest blur,
// on a soft ease-out with no overshoot, so typing feels light. Cleared, it sinks ROLL_OUT pt and
// fades. Every digit stays clipped to its own cell.
const ROLL_FROM = 10;
const ROLL_OUT = 10;
const ROLL_BLUR = 1.2;
const ROLL_IN_MS = 340;
const ROLL_OUT_MS = 220;
const CHECK_DELAY = 280; // ms after the sixth digit before the code is checked

// ── Wrong code: a damped shake, red, then the digits drop out ────────────────────────────────────
const SHAKE_AMPLITUDE = 16; // pt
const SHAKE_FREQUENCY = 6.5; // Hz
const SHAKE_DECAY = 0.16; // s, time constant of the envelope
const SHAKE_LENGTH = 0.75; // s
const ERROR_HOLD = 750; // ms red before the code clears
const CLEAR_STAGGER = 30; // ms between digits dropping out, last first

// ── Right code: merge into a pill, then a circle, then the check and its ring ─────────────────────
const MERGE_TIME = 460; // ms, cells slide together into one pill
const CIRCLE_TIME = 380; // ms, the pill closes into a circle
const MERGED_SPACING = 0.62; // × the cell width between the merged cells' centres
const CHECK_AT = MERGE_TIME + CIRCLE_TIME - 40; // ms
const CHECK_TIME = 380;
// The burst: a crisp ring grows from the circle to RING_REACH × its radius, a second, fainter one
// follows RING_LAG of the way behind, and SPARKS short dashes shoot out. Everything stays inside
// BURST_MAX pt of the centre so it never reaches the status line below.
const BURST_TIME = 620;
const RING_REACH = 1.8;
const RING_LAG = 0.14;
const SPARKS = 12;
const BURST_MAX = 64;
const UNMERGE_TIME = 700;

// autoPlay (seconds): a wrong code, the shake, the right code, the check, back to empty.
const LOOP = 10;
const WRONG_AT = 0.7;
const RIGHT_AT = 4.0;
const TYPE_STEP = 0.26; // s between typed digits
const RESET_AT = 8.6;
const USER_HOLD = 4000; // ms a touch pauses the script (and longer while the keyboard is up)
const RESEND_SECONDS = 30;
const STATUS_FADE = 220; // ms for a status line or the title to cross-fade

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT_FN = Easing.bezierFn(0.23, 1, 0.32, 1);

type SymbolName = ComponentProps<typeof SymbolView>['name'];
const symbol = (ios: string, android: string) => ({ ios, android, web: android }) as SymbolName;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/**
 * One cell's box at merge `m`: 0 apart, 1 slid together into a pill, 2 closed into a circle.
 * Returns [centre x from the row's centre, width, corner radius].
 */
function cellBox(index: number, m: number, cellW: number): [number, number, number] {
  'worklet';
  const p1 = clamp01(m);
  const p2 = clamp01(m - 1);
  const spacing = mix(mix(cellW + CELL_GAP, cellW * MERGED_SPACING, p1), 0, p2);
  // Merged cells are stadiums wide enough to overlap their neighbours, so the union is one pill.
  const width = mix(mix(cellW, cellW * MERGED_SPACING + CELL_H, p1), CELL_H, p2);
  const radius = mix(CELL_RADIUS, CELL_H / 2, p1);
  return [(index - (LENGTH - 1) / 2) * spacing, width, radius];
}

/** 1 until `from`, then down to 0 at 1 on a smoothstep: a fade that holds, then goes. */
function fadeAfter(t: number, from: number) {
  'worklet';
  const k = clamp01((t - from) / (1 - from));
  return 1 - k * k * (3 - 2 * k);
}

function haptic(kind: 'tap' | 'error' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tap') Haptics.selectionAsync().catch(() => {});
  else
    Haptics.notificationAsync(kind === 'error' ? Haptics.NotificationFeedbackType.Error : Haptics.NotificationFeedbackType.Success).catch(
      () => {},
    );
}

/**
 * A six-digit code input drawn as soft rounded cells. The focused cell's outline collapses into a
 * glowing, breathing caret; typed digits rise softly into their cells. A wrong code
 * shakes the row on a damped spring and flushes it red before the digits drop out; the right one
 * slides the cells together into a single pill that closes into a circle, draws a check and
 * throws a ring of sparks. A hidden text field takes real keyboard and one-time-code input.
 */
export function MorphOtpInput({ code: codeProp = '481930', destination = 'nora@larkpost.com', autoPlay = false, onVerified }: Props) {
  // Only six digits can ever match, whatever is passed in.
  const code = useMemo(() => (codeProp.replace(DIGITS, '') + '000000').slice(0, LENGTH), [codeProp]);
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [width, setWidth] = useState(0);
  const [value, setValue] = useState('');
  const [shown, setShown] = useState<string[]>(() => Array.from({ length: LENGTH }, () => ''));
  const [phase, setPhase] = useState<Phase>('idle');
  const [resendFrom, setResendFrom] = useState(() => Date.now());
  const input = useRef<TextInput>(null);
  // The latest callback, so a new function from the parent never rebuilds the controller.
  const verified = useRef(onVerified);
  useEffect(() => {
    verified.current = onVerified;
  });

  const cellW = Math.max(24, Math.min(CELL_MAX_W, (width - 56 - CELL_GAP * (LENGTH - 1)) / LENGTH));
  const rowW = cellW * LENGTH + CELL_GAP * (LENGTH - 1);
  const font = matchFont({ fontFamily: FAMILY, fontSize: DIGIT_SIZE, fontWeight: '600' });

  const now = useSharedValue(0);
  const shakeAt = useSharedValue(-1);
  const shake = useSharedValue(0);
  const errorAmt = useSharedValue(0);
  const merge = useSharedValue(0);
  const check = useSharedValue(0);
  const burst = useSharedValue(0);
  // How much each status line shows. Each fades on its own, so going from idle straight to success
  // (or back) never passes through the error line.
  const idleShown = useSharedValue(1);
  const errorShown = useSharedValue(0);
  const successShown = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const t = now.get() + dt;
    now.set(t);
    // A shake asked for from JS starts on this frame.
    if (shakeAt.get() === -2) shakeAt.set(t);
    const at = shakeAt.get();
    if (at >= 0) {
      const k = t - at;
      if (k > SHAKE_LENGTH) {
        shakeAt.set(-1);
        shake.set(0);
      } else shake.set(SHAKE_AMPLITUDE * Math.exp(-k / SHAKE_DECAY) * Math.sin(2 * Math.PI * SHAKE_FREQUENCY * k));
    }
  });

  // The controller: typing, checking, the error and success sequences and the script. Its own
  // bookkeeping lives in this closure; React state only mirrors what has to render.
  const api = useMemo(() => {
    // byScript: the digits in the row were typed by the demo, not by the user.
    const live = { value: '', phase: 'idle' as Phase, playing: false, byScript: false };
    const timers: ReturnType<typeof setTimeout>[] = [];
    const script: ReturnType<typeof setTimeout>[] = [];
    const later = (ms: number, fn: () => void) => {
      const id = setTimeout(() => {
        const at = timers.indexOf(id);
        if (at >= 0) timers.splice(at, 1);
        fn();
      }, ms);
      timers.push(id);
    };
    const setPhaseTo = (p: Phase) => {
      live.phase = p;
      setPhase(p);
      idleShown.set(withTiming(p === 'idle' ? 1 : 0, { duration: STATUS_FADE }));
      errorShown.set(withTiming(p === 'error' ? 1 : 0, { duration: STATUS_FADE }));
      successShown.set(withTiming(p === 'success' ? 1 : 0, { duration: STATUS_FADE }));
    };
    const empty = () => Array.from({ length: LENGTH }, () => '');

    const fail = (user: boolean) => {
      setPhaseTo('error');
      errorAmt.set(withTiming(1, { duration: 120 }));
      if (!reduced) shakeAt.set(-2);
      if (user) haptic('error');
      later(ERROR_HOLD, () => {
        // Digits drop out last to first while the red drains away.
        live.value = '';
        setValue('');
        errorAmt.set(withTiming(0, { duration: 320 }));
        for (let i = LENGTH - 1; i >= 0; i -= 1) {
          later((LENGTH - 1 - i) * CLEAR_STAGGER, () =>
            setShown((s) => {
              const next = s.slice();
              next[i] = '';
              return next;
            }),
          );
        }
        later(LENGTH * CLEAR_STAGGER + 120, () => setPhaseTo('idle'));
      });
    };

    const succeed = (user: boolean) => {
      setPhaseTo('success');
      input.current?.blur();
      if (reduced) {
        merge.set(withTiming(2, { duration: 320 }));
        check.set(withDelay(240, withTiming(1, { duration: 260 })));
      } else {
        merge.set(
          withSequence(
            withTiming(1, { duration: MERGE_TIME, easing: EASE_IN_OUT }),
            withTiming(2, { duration: CIRCLE_TIME, easing: EASE_IN_OUT }),
          ),
        );
        check.set(withDelay(CHECK_AT, withTiming(1, { duration: CHECK_TIME, easing: EASE_OUT })));
        burst.set(0);
        burst.set(withDelay(CHECK_AT, withTiming(1, { duration: BURST_TIME, easing: Easing.linear })));
      }
      // The success haptic lands with the check, as the ring leaves.
      later(reduced ? 240 : CHECK_AT, () => {
        if (user) haptic('success');
        verified.current?.();
      });
    };

    const verify = (user: boolean) => {
      if (live.value === code) succeed(user);
      else fail(user);
    };

    /** Takes the field's text (typed, pasted or filled from a message) as the new code. */
    const enter = (text: string, user: boolean) => {
      if (live.phase !== 'idle') return;
      const next = text.replace(DIGITS, '').slice(0, LENGTH);
      if (next === live.value) return;
      live.value = next;
      live.byScript = !user;
      setValue(next);
      setShown(Array.from({ length: LENGTH }, (_, i) => next[i] ?? ''));
      if (next.length === LENGTH) later(CHECK_DELAY, () => verify(user));
    };

    /** Back to an empty row: the circle opens back out into six cells. */
    const reset = () => {
      timers.splice(0).forEach(clearTimeout);
      live.value = '';
      setValue('');
      setShown(empty());
      errorAmt.set(withTiming(0, { duration: 200 }));
      check.set(withTiming(0, { duration: 160 }));
      burst.set(0);
      merge.set(withTiming(0, { duration: reduced ? 300 : UNMERGE_TIME, easing: EASE_IN_OUT }));
      setResendFrom(Date.now());
      setPhaseTo('idle');
    };

    const stopScript = () => {
      script.splice(0).forEach(clearTimeout);
    };
    const typeCode = (digits: string, at: number) => {
      for (let i = 0; i < digits.length; i += 1) {
        script.push(setTimeout(() => enter(live.value + digits[i], false), (at + i * TYPE_STEP) * 1000));
      }
    };
    const runScript = () => {
      stopScript();
      // A wrong code that differs from the right one in two places.
      const wrong = code.slice(0, 3) + code[4] + code[3] + code.slice(5);
      typeCode(wrong === code ? code.split('').reverse().join('') : wrong, WRONG_AT);
      typeCode(code, RIGHT_AT);
      script.push(setTimeout(reset, RESET_AT * 1000));
      script.push(setTimeout(runScript, LOOP * 1000));
    };
    /** A touch pauses the script; it returns whether it cleared a code the demo was half way through. */
    const touched = () => {
      if (!live.playing) return false;
      stopScript();
      // The demo's half-typed code is not the user's: clear it so their own digits start fresh.
      let cleared = false;
      if (live.byScript && live.phase === 'idle' && live.value !== '') {
        timers.splice(0).forEach(clearTimeout);
        live.value = '';
        live.byScript = false;
        setValue('');
        setShown(empty());
        cleared = true;
      }
      // The demo waits while the keyboard is up, so someone reading a code off another screen keeps
      // the digits they have typed so far.
      const resume = () => {
        if (input.current?.isFocused() && Keyboard.isVisible()) {
          script.push(setTimeout(resume, USER_HOLD));
          return;
        }
        reset();
        script.push(setTimeout(runScript, UNMERGE_TIME + 300));
      };
      script.push(setTimeout(resume, USER_HOLD));
      return cleared;
    };

    return {
      start: () => {
        live.playing = true;
        runScript();
      },
      stop: () => {
        live.playing = false;
        stopScript();
      },
      dispose: () => {
        live.playing = false;
        stopScript();
        timers.splice(0).forEach(clearTimeout);
      },
      type: (text: string) => {
        const before = live.value;
        // If the touch cleared the demo's digits, only what the user just typed counts.
        enter(touched() ? text.slice(before.length) : text, true);
      },
      tapRow: () => {
        touched();
        if (live.phase === 'success') {
          reset();
          return;
        }
        haptic('tap');
        input.current?.focus();
      },
      resend: () => {
        touched();
        haptic('tap');
        setResendFrom(Date.now());
      },
    };
  }, [code, reduced, idleShown, errorShown, successShown, errorAmt, shakeAt, merge, check, burst]);

  // The script follows autoPlay; a check or a shake already under way finishes when it stops.
  useEffect(() => {
    if (!autoPlay) return;
    api.start();
    return api.stop;
  }, [api, autoPlay]);
  useEffect(() => api.dispose, [api]);

  const rowTop = insets.top + 262;
  // The status line sits clear of the burst (BURST_MAX from the row's centre).
  const cy = rowTop + CELL_H / 2;
  const cx = width / 2;

  const rowShift = useDerivedValue(() => [{ translateX: cx + shake.get() }, { translateY: cy }]);
  const idleStatus = useAnimatedStyle(() => ({ opacity: idleShown.get() }));
  const errorStatus = useAnimatedStyle(() => ({ opacity: errorShown.get() }));
  const successStatus = useAnimatedStyle(() => ({ opacity: successShown.get() }));
  const titleIdle = useAnimatedStyle(() => ({ opacity: 1 - successShown.get() }));

  return (
    <View style={styles.screen} onLayout={(e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width)}>
      <View style={[styles.head, { top: insets.top + 48 }]}>
        <View style={styles.badge} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <SymbolView name={symbol('envelope.open.fill', 'drafts')} size={24} tintColor={ACCENT} />
        </View>
        <View style={styles.titles}>
          <Animated.Text style={[styles.title, titleIdle]}>Check your inbox</Animated.Text>
          <Animated.Text style={[styles.title, styles.overlay, successStatus]}>{"You're in"}</Animated.Text>
        </View>
        <Text style={styles.subtitle}>
          Enter the 6-digit code we sent to{'\n'}
          <Text style={styles.destination}>{destination}</Text>
        </Text>
      </View>

      {width > 0 && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Group transform={rowShift}>
            {shown.map((digit, i) => (
              <CellBox
                key={i}
                index={i}
                digit={digit}
                active={phase === 'idle' && i === Math.min(value.length, LENGTH - 1) && value.length < LENGTH}
                cellW={cellW}
                now={now}
                merge={merge}
                errorAmt={errorAmt}
                reduced={reduced}
              />
            ))}
            {shown.map((digit, i) => (
              <CellDigit key={i} index={i} digit={digit} cellW={cellW} font={font} merge={merge} errorAmt={errorAmt} reduced={reduced} />
            ))}
            <Success merge={merge} check={check} burst={burst} reduced={reduced} />
          </Group>
        </Canvas>
      )}

      <Pressable
        style={[styles.hit, { top: rowTop - 12, left: (width - rowW) / 2 - 8, width: rowW + 16, height: CELL_H + 24 }]}
        onPress={api.tapRow}
        accessibilityRole="button"
        accessibilityLabel={phase === 'success' ? 'Code verified' : `Verification code, ${value.length} of ${LENGTH} digits entered`}
        accessibilityHint="Opens the keyboard"
      />
      <TextInput
        ref={input}
        value={value}
        onChangeText={api.type}
        keyboardType="number-pad"
        textContentType="oneTimeCode"
        autoComplete="sms-otp"
        maxLength={LENGTH}
        caretHidden
        style={styles.hidden}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      />

      <View style={[styles.status, { top: cy + BURST_MAX + 10 }]}>
        <Animated.View style={[styles.statusLine, idleStatus]} pointerEvents={phase === 'idle' ? 'auto' : 'none'}>
          <Resend from={resendFrom} onResend={api.resend} />
        </Animated.View>
        <Animated.Text style={[styles.statusLine, styles.statusText, { color: ERROR }, errorStatus]}>
          {"That code didn't match. Try again."}
        </Animated.Text>
        <Animated.Text style={[styles.statusLine, styles.statusText, { color: ACCENT }, successStatus]}>
          Verified. Welcome back, Nora.
        </Animated.Text>
      </View>

      <Text style={[styles.foot, { bottom: insets.bottom + 24 }]}>Codes expire after 10 minutes</Text>
    </View>
  );
}

/** "Resend code in 0:24", counting down once a second, then a tappable "Resend code". */
function Resend({ from, onResend }: { from: number; onResend: () => void }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, []);
  const left = Math.max(0, RESEND_SECONDS - Math.floor(Math.max(0, now - from) / 1000));
  if (left > 0) {
    return (
      <Text style={styles.statusText}>
        Resend code in <Text style={styles.count}>{`0:${String(left).padStart(2, '0')}`}</Text>
      </Text>
    );
  }
  return (
    <Pressable onPress={onResend} hitSlop={12} accessibilityRole="button">
      <Text style={[styles.statusText, { color: ACCENT }]}>Resend code</Text>
    </Pressable>
  );
}

type CellProps = {
  index: number;
  /** The digit in this cell, or '' when it is empty. */
  digit: string;
  active: boolean;
  cellW: number;
  font: SkFont;
  now: SharedValue<number>;
  merge: SharedValue<number>;
  errorAmt: SharedValue<number>;
  reduced: boolean;
};

/** One cell's box and the outline that collapses into the caret when it is focused. */
function CellBox({ index, digit, active, cellW, now, merge, errorAmt, reduced }: Omit<CellProps, 'font'>) {
  const filled = digit !== '';
  // focus: the outline's collapse into the caret (0 outline … 1 caret); lit: the mint outline
  // fading up when the cell takes focus; leave: the caret fading out when the cell lets it go.
  const focus = useSharedValue(0);
  const lit = useSharedValue(0);
  const leave = useSharedValue(1);
  const fill = useSharedValue(filled ? 1 : 0);

  useEffect(() => {
    if (active) {
      // Always start from the full outline, so focus arrives the same way every time.
      focus.set(0);
      leave.set(0);
      lit.set(withTiming(1, { duration: reduced ? 160 : FOCUS_IN }));
      focus.set(reduced ? 1 : withSpring(1, FOCUS_SPRING));
    } else {
      lit.set(withTiming(0, { duration: CARET_LEAVE }));
      leave.set(withTiming(1, { duration: CARET_LEAVE, easing: EASE_OUT }));
    }
  }, [active, focus, lit, leave, reduced]);
  useEffect(() => {
    fill.set(withTiming(filled ? 1 : 0, { duration: 180 }));
  }, [filled, fill]);

  const box = useDerivedValue(() => cellBox(index, reduced ? 0 : merge.get(), cellW));
  // Under reduced motion the cells stay put and fade; the circle fades in on its own.
  const visible = useDerivedValue(() => (reduced ? 1 - clamp01(merge.get()) : 1));
  const x = useDerivedValue(() => box.get()[0] - box.get()[1] / 2);
  const w = useDerivedValue(() => box.get()[1]);
  const r = useDerivedValue(() => box.get()[2]);
  // The border's rect, half a stroke inside the cell.
  const bx = useDerivedValue(() => x.get() + BORDER / 2);
  const bw = useDerivedValue(() => Math.max(0, w.get() - BORDER));
  const br = useDerivedValue(() => Math.max(0, r.get() - BORDER / 2));
  const fillColor = useDerivedValue(() => {
    const m = merge.get();
    const base = interpolateColor(fill.get(), [0, 1], [CELL_FILL, CELL_FILLED]);
    const hurt = interpolateColor(errorAmt.get(), [0, 1], [base, CELL_ERROR]);
    return reduced ? hurt : interpolateColor(clamp01(m - 1), [0, 1], [hurt, ACCENT]);
  });
  const borderColor = useDerivedValue(() => {
    const rest = fill.get() > 0.5 ? 'rgba(255,255,255,0.20)' : 'rgba(255,255,255,0.09)';
    const litColor = interpolateColor(lit.get(), [0, 1], [rest, ACTIVE_BORDER]);
    return interpolateColor(errorAmt.get(), [0, 1], [litColor, ERROR]);
  });
  const borderOpacity = useDerivedValue(() => 1 - clamp01(merge.get() * 3));

  // The mint outline collapsing into the caret: the box narrows to a sliver and shortens while its
  // stroke thickens a little; once it lets go it shrinks and fades where it stands.
  const caretW = useDerivedValue(() => Math.max(0.01, mix(cellW - BORDER, 0.01, focus.get())));
  const caretH = useDerivedValue(() => mix(CELL_H - BORDER, CARET_H, focus.get()) * mix(1, 0.55, leave.get()));
  const caretX = useDerivedValue(() => box.get()[0] - caretW.get() / 2);
  const caretY = useDerivedValue(() => -caretH.get() / 2);
  const caretR = useDerivedValue(() => mix(CELL_RADIUS - BORDER / 2, CARET_W / 2, focus.get()));
  const caretStroke = useDerivedValue(() => mix(1, CARET_W, focus.get()));
  const breath = useDerivedValue(() => 0.55 + 0.45 * Math.cos((2 * Math.PI * now.get()) / CARET_BREATH));
  // Breathing only starts once the caret has formed, and nothing shows while the row is merged.
  const caretOpacity = useDerivedValue(() => {
    const f = clamp01(focus.get());
    const settled = clamp01((f - 0.75) / 0.25);
    // The collapsing outline starts faint and only reaches full strength as it becomes the caret.
    return lit.get() * mix(0.3, 1, f) * (1 - leave.get()) * mix(1, breath.get(), settled) * (1 - clamp01(merge.get() * 2));
  });
  // The glow belongs to the formed caret only: a soft bar behind it, never a blurred outline.
  const glowOpacity = useDerivedValue(() => {
    const f = clamp01(focus.get());
    return caretOpacity.get() * f * f * f * 0.7;
  });
  const glowX = useDerivedValue(() => box.get()[0] - CARET_W / 2);

  return (
    <Group opacity={visible}>
      <RoundedRect x={x} y={-CELL_H / 2} width={w} height={CELL_H} r={r} color={fillColor} />
      <RoundedRect
        x={bx}
        y={-CELL_H / 2 + BORDER / 2}
        width={bw}
        height={CELL_H - BORDER}
        r={br}
        color={borderColor}
        style="stroke"
        strokeWidth={BORDER}
        opacity={borderOpacity}
      />
      <RoundedRect x={glowX} y={caretY} width={CARET_W} height={caretH} r={CARET_W / 2} color={ACCENT} opacity={glowOpacity}>
        <BlurMask blur={CARET_GLOW} style="normal" />
      </RoundedRect>
      <RoundedRect
        x={caretX}
        y={caretY}
        width={caretW}
        height={caretH}
        r={caretR}
        style="stroke"
        strokeWidth={caretStroke}
        color={ACCENT}
        opacity={caretOpacity}
      />
    </Group>
  );
}

type DigitProps = {
  index: number;
  digit: string;
  cellW: number;
  font: SkFont;
  merge: SharedValue<number>;
  errorAmt: SharedValue<number>;
  reduced: boolean;
};

/**
 * A cell's digit, drawn after every box so merged boxes never cover it. It is clipped to its own
 * cell: typed, it rolls up from under the cell's bottom edge out of a slight blur; cleared, it
 * drops back out of the cell.
 */
function CellDigit({ index, digit, cellW, font, merge, errorAmt, reduced }: DigitProps) {
  const filled = digit !== '';
  // The last digit shown stays drawn while it drops out of an emptied cell.
  const [glyph, setGlyph] = useState(digit || ' ');
  if (filled && digit !== glyph) setGlyph(digit);
  // 0 below and blurred, 1 in place, 2 dropped out.
  const roll = useSharedValue(filled ? 1 : 2);
  useEffect(() => {
    if (filled) {
      roll.set(0);
      roll.set(withTiming(1, { duration: reduced ? 200 : ROLL_IN_MS, easing: EASE_OUT }));
    } else roll.set(withTiming(2, { duration: ROLL_OUT_MS, easing: EASE_OUT }));
  }, [filled, roll, reduced]);

  const box = useDerivedValue(() => cellBox(index, reduced ? 0 : merge.get(), cellW));
  const visible = useDerivedValue(() => (reduced ? 1 - clamp01(merge.get()) : 1));
  // The cell's own shape, a hair inside its border.
  const clip = useDerivedValue(() => {
    const [cx, w, r] = box.get();
    return { rect: { x: cx - w / 2 + 1, y: -CELL_H / 2 + 1, width: Math.max(0, w - 2), height: CELL_H - 2 }, rx: Math.max(0, r - 1), ry: Math.max(0, r - 1) };
  });
  const glyphW = font.measureText(glyph).width;
  const digitTransform = useDerivedValue(() => {
    const k = roll.get();
    const y = reduced ? 0 : k < 1 ? (1 - k) * ROLL_FROM : (k - 1) * ROLL_OUT;
    const s = reduced ? 1 : mix(1, 0.6, clamp01(merge.get() - 1));
    return [{ translateX: box.get()[0] }, { translateY: y }, { scale: s }];
  });
  const digitOpacity = useDerivedValue(() => {
    const k = roll.get();
    const shownAmt = k < 1 ? clamp01(k * 1.4) : clamp01((2 - k) * 1.5);
    return shownAmt * (1 - clamp01((merge.get() - 1) * 3)) * visible.get();
  });
  const digitBlur = useDerivedValue(() => (reduced ? 0.01 : Math.max(0.01, Math.min(ROLL_BLUR, Math.abs(1 - roll.get()) * ROLL_BLUR * 1.5))));
  const digitColor = useDerivedValue(() => interpolateColor(errorAmt.get(), [0, 1], ['#f4f5f7', ERROR]));

  return (
    <Group clip={clip}>
      <Group transform={digitTransform} opacity={digitOpacity}>
        <SkiaText x={-glyphW / 2} y={DIGIT_SIZE * 0.36} text={glyph} font={font} color={digitColor}>
          <BlurMask blur={digitBlur} style="normal" respectCTM={false} />
        </SkiaText>
      </Group>
    </Group>
  );
}

const CHECK_PATH = Skia.PathBuilder.Make().moveTo(-11, 1).lineTo(-3.5, 8.5).lineTo(12, -8).build();

type SuccessProps = {
  merge: SharedValue<number>;
  check: SharedValue<number>;
  burst: SharedValue<number>;
  reduced: boolean;
};

/** The check drawn into the merged circle, the rings it throws and a scatter of spark dashes. */
function Success({ merge, check, burst, reduced }: SuccessProps) {
  const radius = CELL_H / 2;
  // Under reduced motion the cells only fade, so the circle fades in here instead.
  const circleOpacity = useDerivedValue(() => (reduced ? clamp01(merge.get() - 1) : 0));
  const pop = useDerivedValue(() => (reduced ? [] : [{ scale: 1 + Math.sin(Math.PI * check.get()) * 0.07 }]));
  // The leading ring: crisp all the way (never under 1.25 pt), at full strength until 55% of the
  // burst, then gone by its end.
  const ringR = useDerivedValue(() => mix(radius + 1, radius * RING_REACH, EASE_OUT_FN(burst.get())));
  const ringW = useDerivedValue(() => mix(3, 1.25, burst.get()));
  const ringO = useDerivedValue(() => {
    const b = burst.get();
    return b > 0 && b < 1 ? fadeAfter(b, 0.55) : 0;
  });
  // The trailing ring starts RING_LAG later and stops short of the first.
  const trail = useDerivedValue(() => clamp01((burst.get() - RING_LAG) / (1 - RING_LAG)));
  const trailR = useDerivedValue(() => mix(radius + 1, radius * (RING_REACH - 0.32), EASE_OUT_FN(trail.get())));
  const trailO = useDerivedValue(() => {
    const b = burst.get();
    return b > RING_LAG && b < 1 ? 0.45 * fadeAfter(trail.get(), 0.4) : 0;
  });
  return (
    <Group>
      <Group transform={pop}>
        <Circle cx={0} cy={0} r={radius} color={ACCENT} opacity={circleOpacity} />
        <Path path={CHECK_PATH} style="stroke" strokeWidth={4.6} strokeCap="round" strokeJoin="round" color={INK} start={0} end={check} />
      </Group>
      <Circle cx={0} cy={0} r={trailR} style="stroke" strokeWidth={1.25} color={ACCENT} opacity={trailO} />
      <Circle cx={0} cy={0} r={ringR} style="stroke" strokeWidth={ringW} color={ACCENT} opacity={ringO} />
      {Array.from({ length: SPARKS }, (_, i) => (
        <Spark key={i} index={i} burst={burst} radius={radius} />
      ))}
    </Group>
  );
}

/** A short dash shooting straight out of the circle, shortening as it goes. */
function Spark({ index, burst, radius }: { index: number; burst: SharedValue<number>; radius: number }) {
  const angle = (index / SPARKS) * Math.PI * 2 + Math.PI / SPARKS;
  const long = index % 2 === 0;
  const reach = long ? BURST_MAX - 3 : BURST_MAX - 12;
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  const p1 = useDerivedValue(() => {
    const e = EASE_OUT_FN(burst.get());
    const d = mix(radius + 6, reach, e);
    const half = mix(long ? 6 : 4.5, 1, e);
    return vec(cos * (d - half), sin * (d - half));
  });
  const p2 = useDerivedValue(() => {
    const e = EASE_OUT_FN(burst.get());
    const d = mix(radius + 6, reach, e);
    const half = mix(long ? 6 : 4.5, 1, e);
    return vec(cos * (d + half), sin * (d + half));
  });
  const o = useDerivedValue(() => {
    const b = burst.get();
    return b > 0 && b < 1 ? fadeAfter(b, 0.45) : 0;
  });
  return <Line p1={p1} p2={p2} color={ACCENT} style="stroke" strokeWidth={long ? 2.4 : 2} strokeCap="round" opacity={o} />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
    overflow: 'hidden',
  },
  head: {
    position: 'absolute',
    left: 28,
    right: 28,
    alignItems: 'center',
    gap: 14,
  },
  badge: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(124,242,197,0.1)',
    marginBottom: 6,
  },
  titles: {
    alignSelf: 'stretch',
    alignItems: 'center',
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.6,
    color: '#f4f5f7',
    textAlign: 'center',
  },
  overlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  subtitle: {
    fontFamily: FAMILY,
    fontSize: 16,
    lineHeight: 23,
    color: 'rgba(244,245,247,0.55)',
    textAlign: 'center',
  },
  destination: {
    color: '#f4f5f7',
    fontWeight: '600',
  },
  hit: {
    position: 'absolute',
  },
  hidden: {
    position: 'absolute',
    width: 1,
    height: 1,
    opacity: 0,
    left: -10,
    top: -10,
  },
  status: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 24,
  },
  statusLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    alignItems: 'center',
  },
  statusText: {
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '500',
    textAlign: 'center',
    color: 'rgba(244,245,247,0.55)',
  },
  count: {
    color: '#f4f5f7',
    fontVariant: ['tabular-nums'],
  },
  foot: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 13,
    color: 'rgba(244,245,247,0.32)',
  },
});
