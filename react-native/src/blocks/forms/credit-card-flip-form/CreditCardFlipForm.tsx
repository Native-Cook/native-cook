import { BlurMask, Canvas, Group, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { type Ref, useEffect, useEffectEvent, useRef, useState } from 'react';
import {
  Keyboard,
  type KeyboardTypeOptions,
  type LayoutChangeEvent,
  Pressable,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedKeyboard,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CARD_RADIUS, CardArt, CHIP, STRIP } from './CardArt';
import { CardReel } from './CardReel';
import { type Network, NetworkMark, PALETTES, type Palette, networkOf } from './NetworkMark';

type Field = 'number' | 'name' | 'expiry' | 'cvv';
type Rect = { x: number; y: number; w: number; h: number };

type Props = {
  /** Types a whole card, turns it over for the CVV, pays and starts over. */
  autoPlay?: boolean;
  /** The total on the pay button. */
  amount?: string;
  /** The bank printed on the card. */
  issuer?: string;
  onPay?: (card: { number: string; name: string; expiry: string; cvv: string }) => void;
};

const BACKGROUND = '#0d0e12';
const TEXT = '#f4f5f8';
const MUTED = '#8a8e9c';
const FIELD_BG = '#15161c';
const FIELD_BORDER = '#25272f';
const PLACEHOLDER = '#4b4e5a';
const SUCCESS = '#3ccf7e';
const DANGER = '#ff6369';
const PAY_OFF = '#1d1e25';
const PAY_OFF_TEXT = '#5d6170';

// Layout, in pt.
const SIDE = 24;
// With the keyboard up the page rises; before the big header would reach the top inset (the Dynamic
// Island), it gives way to a compact bar pinned under the inset, and the page slides under the bar.
const BAR_H = 44; // the compact bar's height under the top inset
const HEADER_ROOM = 14; // pt of rise over which the big header hands over to the bar
const CARD_MAX = 380;
const CARD_RATIO = 1.586;
const CARD_TOP = 84;
const FORM_GAP = 30;
// Fields carry their label inside, above the value.
const FIELD_H = 60;
const FIELD_GAP = 14;
const FIELD_RADIUS = 14;
const LABEL_TOP = 10;
const VALUE_TOP = 26;
const VALUE_H = 26;
const SAVE_GAP = 18;
const PAY_HEIGHT = 56;
// On the card: the side margin, the number row (its top as a share of the card's height) and the
// gap between groups of four, and the bottom row's top from the bottom.
const CARD_PAD = 22;
const NUMBER_AT = 0.52;
const GROUP_GAP = 14;
const NUMBER_SIZE = 21;
const NUMBER_LINE = 28;
const BOTTOM_ROW = 56;
const SMALL_SIZE = 14;
const SMALL_LINE = 19;
// The focus outline on the card stands OUTLINE_PAD_X / OUTLINE_PAD_Y pt clear of what it frames.
const OUTLINE_PAD_X = 8;
const OUTLINE_PAD_Y = 7;
// The CVV box at the right end of the signature strip.
const CVV_BOX = { width: 62, height: 28 };

// Motion. The new network's colours spread over the card in MORPH_MS; the light crosses it in SHINE_MS.
const MORPH_MS = 900;
const SHINE_MS = 1100;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
// The focus outline (on the card) and the focus ring (on the form) glide to their field together on
// one spring; the comet of light runs once round the outline every ORBIT_MS.
const FOCUS_SPRING = { duration: 520, dampingRatio: 0.86 };
const ORBIT_MS = 2600;
// The card turns over on a softer spring.
const FLIP_SPRING = { duration: 820, dampingRatio: 0.74 };
const PERSPECTIVE = 1100;
// While it turns the card rises LIFT pt and grows by LIFT_SCALE.
const LIFT = 14;
const LIFT_SCALE = 0.05;
// Dragging on the card tilts it towards the finger, up to TILT_MAX_Y° about its upright axis and
// TILT_MAX_X° about its horizontal one (TILT_PER_PT° per pt, eased into the limit), and it springs
// flat on release with the finger's speed. A new network gives it a little swing of NUDGE°.
const TILT_PER_PT = 0.09;
const TILT_MAX_Y = 12;
const TILT_MAX_X = 9;
const TILT_SPRING = { duration: 700, dampingRatio: 0.55 };
const NUDGE = 7;
// Paying: the button folds into a circle (PAY_FOLD_MS), spins for PAY_MS, then a check draws in
// CHECK_MS; the paid state holds PAID_HOLD ms before the form empties.
const PAY_FOLD_MS = 380;
const PAY_MS = 1500;
const CHECK_MS = 380;
const PAID_HOLD = 1800;
// A field left by blur is let go after BLUR_GRACE ms, so moving between fields never drops the outline.
const BLUR_GRACE = 60;

// autoPlay (seconds): the card it types and when each step happens. LOOP is the whole period.
const LOOP = 14.2;
const SCRIPT = { number: '5412753488102697', name: 'JONAH VERRIER', expiry: '0829', cvv: '417' };
const RHYTHM = [1, 0.8, 1.25, 0.9, 1.1, 0.75, 1.2, 1];
// After a touch, autoPlay starts over this long after the form is left alone.
const RESUME_AFTER = 4;

type Act =
  | { kind: 'focus'; field: Field | null }
  | { kind: 'type'; field: Field; text: string }
  | { kind: 'press' }
  | { kind: 'pay' };

function typing(steps: { at: number; act: Act }[], field: Field, text: string, from: number, key: number) {
  let at = from;
  for (let i = 1; i <= text.length; i += 1) {
    steps.push({ at, act: { kind: 'type', field, text: text.slice(0, i) } });
    at += key * RHYTHM[i % RHYTHM.length];
  }
  return at;
}

function scriptSteps() {
  const steps: { at: number; act: Act }[] = [{ at: 0.6, act: { kind: 'focus', field: 'number' } }];
  typing(steps, 'number', SCRIPT.number, 0.95, 0.15);
  steps.push({ at: 3.55, act: { kind: 'focus', field: 'name' } });
  typing(steps, 'name', SCRIPT.name, 3.8, 0.085);
  steps.push({ at: 5.15, act: { kind: 'focus', field: 'expiry' } });
  typing(steps, 'expiry', SCRIPT.expiry, 5.4, 0.2);
  steps.push({ at: 6.4, act: { kind: 'focus', field: 'cvv' } });
  typing(steps, 'cvv', SCRIPT.cvv, 6.95, 0.24);
  steps.push({ at: 7.9, act: { kind: 'focus', field: null } }, { at: 8.22, act: { kind: 'press' } }, { at: 8.4, act: { kind: 'pay' } });
  return steps;
}

const digitsOnly = (text: string) => text.replace(/\D/g, '');
const groupNumber = (digits: string) => digits.replace(/(\d{4})(?=\d)/g, '$1 ');
const showExpiry = (digits: string) => (digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits);

/** Month first: a first digit above 1 is a single-digit month, and the month never passes 12. */
function cleanExpiry(text: string) {
  let digits = digitsOnly(text).slice(0, 4);
  if (digits.length >= 1 && Number(digits[0]) > 1) digits = `0${digits}`.slice(0, 4);
  if (digits.length >= 2 && (Number(digits.slice(0, 2)) > 12 || digits.slice(0, 2) === '00')) digits = digits.slice(0, 1);
  return digits;
}

/** A full MMYY whose month has already ended. */
function isExpired(digits: string) {
  if (digits.length !== 4) return false;
  const month = Number(digits.slice(0, 2));
  const year = 2000 + Number(digits.slice(2));
  const now = new Date();
  return year < now.getFullYear() || (year === now.getFullYear() && month < now.getMonth() + 1);
}

function titleCase(name: string) {
  return name.toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function haptic(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'light') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

/** Eases `v` into ±`limit`: linear near zero, never past the limit. */
function soft(v: number, limit: number) {
  'worklet';
  return limit * Math.tanh(v / limit);
}

function letterIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ translateY: 9 }, { scale: 0.86 }] },
    animations: {
      opacity: withTiming(1, { duration: 200 }),
      transform: [{ translateY: withTiming(0, { duration: 300, easing: EASE_OUT }) }, { scale: withTiming(1, { duration: 300, easing: EASE_OUT }) }],
    },
  };
}

function letterOut() {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
    animations: { opacity: withTiming(0, { duration: 180 }), transform: [{ translateY: withTiming(-6, { duration: 180 }) }] },
  };
}

function markIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ scale: 0.7 }, { translateY: 6 }] },
    animations: {
      opacity: withTiming(1, { duration: 260 }),
      transform: [{ scale: withSpring(1, { duration: 520, dampingRatio: 0.62 }) }, { translateY: withTiming(0, { duration: 300, easing: EASE_OUT }) }],
    },
  };
}

function markOut() {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ scale: 1 }, { translateY: 0 }] },
    animations: { opacity: withTiming(0, { duration: 160 }), transform: [{ scale: withTiming(1.15, { duration: 200 }) }, { translateY: withTiming(0, { duration: 1 }) }] },
  };
}

/** A field's check or warning pops in from 60% on a lively spring. */
function badgeIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ scale: 0.6 }] },
    animations: { opacity: withTiming(1, { duration: 160 }), transform: [{ scale: withSpring(1, { duration: 380, dampingRatio: 0.6 }) }] },
  };
}

function badgeOut() {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ scale: 1 }] },
    animations: { opacity: withTiming(0, { duration: 140 }), transform: [{ scale: withTiming(0.8, { duration: 140 }) }] },
  };
}

/**
 * A payment form under a live preview of the card. Digits roll onto the card like slot reels as
 * the number is typed, the card's colours spread out from its mark once the first digit tells the
 * network, the name and expiry fill in under a frame of light that glides to whichever part is
 * being edited (a ring on the form glides with it), and the CVV turns the card over. The card
 * tilts under a finger and its light slides across the brushed surface. Pay folds the button into
 * a spinner, then a check.
 */
export function CreditCardFlipForm({ autoPlay = false, amount = '€248.00', issuer = 'Halcyon', onPay }: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenW, height: screenH } = useWindowDimensions();
  const reducedMotion = useReducedMotion();

  const cardW = Math.max(200, Math.min(screenW - SIDE * 2, CARD_MAX));
  const cardH = Math.round(cardW / CARD_RATIO);
  const cardTop = insets.top + CARD_TOP;
  const formTop = cardTop + cardH + FORM_GAP;
  const formW = screenW - SIDE * 2;
  const half = (formW - 12) / 2;
  const rowStep = FIELD_H + FIELD_GAP;
  // Each field's box on the form, from the form's top left.
  const fieldRects: Record<Field, Rect> = {
    number: { x: 0, y: 0, w: formW, h: FIELD_H },
    name: { x: 0, y: rowStep, w: formW, h: FIELD_H },
    expiry: { x: 0, y: rowStep * 2, w: half, h: FIELD_H },
    cvv: { x: half + 12, y: rowStep * 2, w: half, h: FIELD_H },
  };
  const formH = rowStep * 2 + FIELD_H;
  const payTop = screenH - insets.bottom - 16 - PAY_HEIGHT;

  // Where each front field sits on the card: the number row is fixed; the name and the expiry are
  // measured, so the outline hugs them exactly (and grows with the name as it is typed).
  const numberTop = Math.round(cardH * NUMBER_AT);
  const bottomTop = cardH - BOTTOM_ROW;
  const slotW = (cardW - CARD_PAD * 2 - GROUP_GAP * 3) / 16;
  const [nameBox, setNameBox] = useState({ w: 96, h: 34 });
  const [expiryBox, setExpiryBox] = useState({ w: 52, h: 34 });
  const outlines: Record<Exclude<Field, 'cvv'>, Rect> = {
    number: { x: CARD_PAD - OUTLINE_PAD_X, y: numberTop - 6, w: cardW - CARD_PAD * 2 + OUTLINE_PAD_X * 2, h: NUMBER_LINE + 12 },
    name: { x: CARD_PAD - OUTLINE_PAD_X, y: bottomTop - OUTLINE_PAD_Y, w: nameBox.w + OUTLINE_PAD_X * 2, h: nameBox.h + OUTLINE_PAD_Y * 2 },
    expiry: {
      x: cardW - CARD_PAD - expiryBox.w - OUTLINE_PAD_X,
      y: bottomTop - OUTLINE_PAD_Y,
      w: expiryBox.w + OUTLINE_PAD_X * 2,
      h: expiryBox.h + OUTLINE_PAD_Y * 2,
    },
  };
  const cvvTop = STRIP.y + (STRIP.height - CVV_BOX.height) / 2;
  const cvvLeft = cardW - STRIP.x - 4 - CVV_BOX.width;
  const markOrigin = { x: cardW - CARD_PAD - 40, y: 30 };

  const [number, setNumber] = useState('');
  const [name, setName] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvv, setCvv] = useState('');
  const [save, setSave] = useState(true);
  const [field, setField] = useState<Field | null>(null);
  const [stage, setStage] = useState<'idle' | 'paying' | 'paid'>('idle');
  const [playing, setPlaying] = useState(autoPlay);
  const [pressed, setPressed] = useState(false);
  const network = networkOf(number);
  const expired = isExpired(expiry);
  const done: Record<Field, boolean> = {
    number: number.length === 16,
    name: name.trim().length > 1,
    expiry: expiry.length === 4 && !expired,
    cvv: cvv.length === 3,
  };
  const valid = done.number && done.name && done.expiry && done.cvv;

  const numberRef = useRef<TextInput>(null);
  const nameRef = useRef<TextInput>(null);
  const expiryRef = useRef<TextInput>(null);
  const cvvRef = useRef<TextInput>(null);

  const from = useSharedValue<Palette>(PALETTES.none);
  const to = useSharedValue<Palette>(PALETTES.none);
  const mix = useSharedValue(1);
  const shine = useSharedValue(0);
  const flip = useSharedValue(0);
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  const orbit = useSharedValue(0);
  // The card's outline (front) and the form's ring move together; the back has its own outline.
  const outlineX = useSharedValue(outlines.number.x);
  const outlineY = useSharedValue(outlines.number.y);
  const outlineW = useSharedValue(outlines.number.w);
  const outlineH = useSharedValue(outlines.number.h);
  const outlineOn = useSharedValue(0);
  const cvvOn = useSharedValue(0);
  const ringX = useSharedValue(0);
  const ringY = useSharedValue(0);
  const ringW = useSharedValue(formW);
  const ringH = useSharedValue(FIELD_H);
  const ringOn = useSharedValue(0);
  const focusBottom = useSharedValue(0);
  const ready = useSharedValue(0);
  const fold = useSharedValue(0);
  const check = useSharedValue(0);
  const spin = useSharedValue(0);
  const keyboard = useAnimatedKeyboard();

  // The comet of light runs round the outline for as long as the block is on screen.
  useEffect(() => {
    if (reducedMotion) {
      orbit.set(0.62);
      return;
    }
    orbit.set(0);
    orbit.set(withRepeat(withTiming(1, { duration: ORBIT_MS, easing: Easing.linear }), -1, false));
    return () => cancelAnimation(orbit);
  }, [orbit, reducedMotion]);

  // A new network: its colours spread from the mark over the old ones while a light crosses the
  // card and the card swings a little, so the light runs over its surface.
  const seenNetwork = useRef<Network>(network);
  useEffect(() => {
    if (seenNetwork.current === network) return;
    seenNetwork.current = network;
    const next = PALETTES[network];
    scheduleOnUI(() => {
      'worklet';
      from.set(to.get());
      to.set(next);
      mix.set(0);
      mix.set(withTiming(1, { duration: reducedMotion ? 300 : MORPH_MS, easing: EASE_IN_OUT }));
      shine.set(0);
      if (!reducedMotion) {
        shine.set(withTiming(1, { duration: SHINE_MS, easing: EASE_IN_OUT }));
        if (next !== PALETTES.none) tiltY.set(withSequence(withTiming(NUDGE, { duration: 260, easing: EASE_OUT }), withSpring(0, TILT_SPRING)));
      }
    });
  }, [network, from, to, mix, shine, tiltY, reducedMotion]);

  // The card turns over for the CVV, and back for anything else.
  useEffect(() => {
    flip.set(reducedMotion ? withTiming(field === 'cvv' ? 1 : 0, { duration: 260 }) : withSpring(field === 'cvv' ? 1 : 0, FLIP_SPRING));
    cvvOn.set(withTiming(field === 'cvv' ? 1 : 0, { duration: 220 }));
  }, [field, flip, cvvOn, reducedMotion]);

  // The outline glides to the part of the card being edited on the front (fading when none is),
  // and the ring to the field being edited on the form. A newly shown outline appears in place.
  const target = field && field !== 'cvv' ? outlines[field] : null;
  const ringTarget = field ? fieldRects[field] : null;
  useEffect(() => {
    const go = (sv: SharedValue<number>, v: number, jump: boolean) => sv.set(reducedMotion || jump ? v : withSpring(v, FOCUS_SPRING));
    if (target) {
      const jump = outlineOn.get() < 0.05;
      go(outlineX, target.x, jump);
      go(outlineY, target.y, jump);
      go(outlineW, target.w, jump);
      go(outlineH, target.h, jump);
    }
    outlineOn.set(withTiming(target ? 1 : 0, { duration: 200 }));
  }, [target?.x, target?.y, target?.w, target?.h, outlineX, outlineY, outlineW, outlineH, outlineOn, reducedMotion]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const go = (sv: SharedValue<number>, v: number, jump: boolean) => sv.set(reducedMotion || jump ? v : withSpring(v, FOCUS_SPRING));
    if (ringTarget) {
      const jump = ringOn.get() < 0.05;
      go(ringX, ringTarget.x, jump);
      go(ringY, ringTarget.y, jump);
      go(ringW, ringTarget.w, jump);
      go(ringH, ringTarget.h, jump);
    }
    ringOn.set(withTiming(ringTarget ? 1 : 0, { duration: 200 }));
    focusBottom.set(ringTarget ? formTop + ringTarget.y + ringTarget.h : 0);
  }, [ringTarget?.x, ringTarget?.y, ringTarget?.w, ringTarget?.h, formTop, ringX, ringY, ringW, ringH, ringOn, focusBottom, reducedMotion]); // eslint-disable-line react-hooks/exhaustive-deps

  // The pay button lights up once the card is complete.
  useEffect(() => {
    ready.set(withTiming(valid ? 1 : 0, { duration: 240 }));
  }, [valid, ready]);

  // Paying: fold, spin, check, hold, then empty the form.
  const finish = useEffectEvent((byUser: boolean) => {
    if (byUser) haptic('success');
    shine.set(0);
    if (!reducedMotion) shine.set(withTiming(1, { duration: SHINE_MS, easing: EASE_IN_OUT }));
  });
  const reset = useEffectEvent(() => {
    // A field focused while the payment ran gives its keyboard back with the emptied form.
    Keyboard.dismiss();
    setNumber('');
    setName('');
    setExpiry('');
    setCvv('');
    setField(null);
    setStage('idle');
  });
  const byUserPay = useRef(false);
  useEffect(() => {
    if (stage === 'idle') {
      fold.set(withTiming(0, { duration: PAY_FOLD_MS, easing: EASE_IN_OUT }));
      check.set(withTiming(0, { duration: 160 }));
      cancelAnimation(spin);
      spin.set(0);
      return;
    }
    if (stage === 'paying') {
      fold.set(withTiming(1, { duration: PAY_FOLD_MS, easing: EASE_IN_OUT }));
      spin.set(0);
      spin.set(withRepeat(withTiming(1, { duration: 820, easing: Easing.linear }), -1, false));
      const timer = setTimeout(() => setStage('paid'), PAY_FOLD_MS + PAY_MS);
      return () => clearTimeout(timer);
    }
    check.set(withTiming(1, { duration: CHECK_MS, easing: EASE_OUT }));
    finish(byUserPay.current);
    const timer = setTimeout(() => reset(), PAID_HOLD + CHECK_MS);
    return () => clearTimeout(timer);
  }, [stage, fold, check, spin]);
  // Nothing keeps running once the block is gone.
  useEffect(
    () => () => {
      cancelAnimation(spin);
      cancelAnimation(orbit);
    },
    [spin, orbit],
  );

  const pay = (byUser: boolean) => {
    if (!valid || stage !== 'idle') return;
    byUserPay.current = byUser;
    if (byUser) {
      Keyboard.dismiss();
      onPay?.({ number, name, expiry, cvv });
    }
    setField(null);
    setStage('paying');
  };

  // What each field's text becomes when typed, by hand or by the script. Typed by hand, a number
  // or an expiry that just became complete moves the keyboard on to the next field.
  const edit = (target: Field, text: string, byUser: boolean) => {
    if (stage !== 'idle') return;
    if (target === 'number') {
      const digits = digitsOnly(text).slice(0, 16);
      if (byUser && networkOf(digits) !== networkOf(number) && digits.length > 0) haptic('select');
      setNumber(digits);
      if (byUser && digits.length === 16 && number.length < 16) nameRef.current?.focus();
    } else if (target === 'name') {
      setName(text.toUpperCase().replace(/[^A-Z' -]/g, '').slice(0, 20));
    } else if (target === 'expiry') {
      const clean = cleanExpiry(text);
      setExpiry(clean);
      if (byUser && clean.length === 4 && expiry.length < 4 && !isExpired(clean)) cvvRef.current?.focus();
    } else {
      setCvv(digitsOnly(text).slice(0, 3));
    }
  };

  // autoPlay: every step is timed from the start of its loop, so the period never drifts.
  const perform = useEffectEvent((act: Act) => {
    if (act.kind === 'focus') setField(act.field);
    else if (act.kind === 'type') edit(act.field, act.text, false);
    else if (act.kind === 'press') setPressed(true);
    else {
      setPressed(false);
      pay(false);
    }
  });
  useEffect(() => {
    if (!playing) return;
    const steps = scriptSteps();
    let origin = Date.now();
    let index = 0;
    let timer: ReturnType<typeof setTimeout>;
    const wait = () => {
      const at = index < steps.length ? steps[index].at : LOOP;
      timer = setTimeout(
        () => {
          if (index < steps.length) {
            perform(steps[index].act);
            index += 1;
          } else {
            origin += LOOP * 1000;
            index = 0;
          }
          wait();
        },
        Math.max(0, origin + at * 1000 - Date.now()),
      );
    };
    wait();
    return () => {
      clearTimeout(timer);
      setPressed(false);
    };
  }, [playing]);

  // A touch takes over; once the form is left alone again the loop starts over from an empty form.
  // The script's focus is only make-believe (no keyboard), so it is dropped with the script.
  const takeOver = () => {
    if (!playing) return;
    setPlaying(false);
    setField(null);
  };
  const resume = useEffectEvent(() => {
    reset();
    setPlaying(true);
  });
  useEffect(() => {
    if (!autoPlay || playing || field !== null || stage !== 'idle') return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, playing, field, stage, number, name, expiry, cvv, save]);

  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (blurTimer.current) clearTimeout(blurTimer.current);
    },
    [],
  );
  const onFocus = (target: Field) => {
    takeOver();
    if (blurTimer.current) clearTimeout(blurTimer.current);
    if (target === 'cvv' && field !== 'cvv') haptic('light');
    setField(target);
  };
  const onBlur = (target: Field) => {
    if (blurTimer.current) clearTimeout(blurTimer.current);
    blurTimer.current = setTimeout(() => setField((current) => (current === target ? null : current)), BLUR_GRACE);
  };

  // A tap on the card edits what was tapped: the back is the CVV, the front its number, name or
  // expiry. The gesture only records where it landed; the field is focused from an effect.
  const [cardTap, setCardTap] = useState<{ x: number; y: number; n: number } | null>(null);
  const focusTapped = useEffectEvent((x: number, y: number) => {
    if (stage !== 'idle') return;
    let next: Field = 'number';
    if (field === 'cvv') next = 'number';
    else if (y > bottomTop - 12) next = x > cardW * 0.62 ? 'expiry' : 'name';
    const input = { number: numberRef, name: nameRef, expiry: expiryRef, cvv: cvvRef }[next];
    input.current?.focus();
  });
  useEffect(() => {
    if (cardTap) focusTapped(cardTap.x, cardTap.y);
  }, [cardTap]);
  const onCardTap = (x: number, y: number) => {
    takeOver();
    setCardTap((prev) => ({ x, y, n: (prev?.n ?? 0) + 1 }));
  };
  const onCardTouch = () => takeOver();

  // The card tilts towards a dragging finger and springs flat when it lets go.
  const tilt = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      scheduleOnRN(onCardTouch);
    })
    .onUpdate((e) => {
      if (reducedMotion) return;
      tiltY.set(soft(e.translationX * TILT_PER_PT, TILT_MAX_Y));
      tiltX.set(soft(-e.translationY * TILT_PER_PT, TILT_MAX_X));
    })
    .onFinalize((e) => {
      tiltY.set(withSpring(0, { ...TILT_SPRING, velocity: e.velocityX * TILT_PER_PT }));
      tiltX.set(withSpring(0, { ...TILT_SPRING, velocity: -e.velocityY * TILT_PER_PT }));
    });
  const tap = Gesture.Tap().onEnd((e, success) => {
    if (success) scheduleOnRN(onCardTap, e.x, e.y);
  });
  const cardGesture = Gesture.Race(tilt, tap);

  const liftStyle = useAnimatedStyle(() => {
    const up = reducedMotion ? 0 : Math.sin(Math.min(1, Math.max(0, flip.get())) * Math.PI);
    return {
      transform: [
        { perspective: PERSPECTIVE },
        { translateY: -LIFT * up },
        { scale: 1 + LIFT_SCALE * up },
        { rotateX: `${tiltX.get()}deg` },
        { rotateY: `${tiltY.get()}deg` },
      ],
    };
  });
  const frontStyle = useAnimatedStyle(() => {
    const f = flip.get();
    return {
      opacity: reducedMotion ? 1 - Math.min(1, Math.max(0, f)) : f < 0.5 ? 1 : 0,
      transform: [{ perspective: PERSPECTIVE }, { rotateY: `${reducedMotion ? 0 : f * 180}deg` }],
    };
  });
  const backStyle = useAnimatedStyle(() => {
    const f = flip.get();
    return {
      opacity: reducedMotion ? Math.min(1, Math.max(0, f)) : f >= 0.5 ? 1 : 0,
      transform: [{ perspective: PERSPECTIVE }, { rotateY: `${reducedMotion ? 0 : f * 180 - 180}deg` }],
    };
  });
  // Whatever is focused stays above the keyboard: the whole page rises as far as it has to.
  const rise = useDerivedValue(() => {
    const kb = keyboard.height.get();
    const bottom = focusBottom.get();
    return kb > 0 && bottom > 0 ? Math.max(0, bottom + 16 - (screenH - kb)) : 0;
  });
  const pageStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -rise.get() }] }));
  // The big header fades out before it rises into the top inset, and the compact bar takes its place.
  const collapse = useDerivedValue(() => {
    const k = Math.min(1, Math.max(0, rise.get() / HEADER_ROOM));
    return k * k * (3 - 2 * k);
  });
  const headerStyle = useAnimatedStyle(() => ({ opacity: 1 - collapse.get() }));
  const barStyle = useAnimatedStyle(() => ({ opacity: collapse.get() }));
  const barRowStyle = useAnimatedStyle(() => ({ transform: [{ translateY: reducedMotion ? 0 : 6 * (1 - collapse.get()) }] }));
  // The shadow on the "table" under the card: a dark contact shadow and a wider one tinted with the
  // card's own light colour, both sliding a little against the tilt.
  const glowColor = useDerivedValue(() => interpolateColor(mix.get(), [0, 1], [from.get()[2], to.get()[2]]));
  const shadowShift = useDerivedValue(() => [{ translateX: -tiltY.get() * 1.2 }, { translateY: tiltX.get() * 0.8 }]);
  const ringColor = useDerivedValue(() => interpolateColor(mix.get(), [0, 1], [from.get()[2], to.get()[2]]));

  const numberDigits = number.split('');
  const expiryDigits = expiry.split('');
  const cvvDigits = cvv.split('');
  const frontOutline = { x: outlineX, y: outlineY, w: outlineW, h: outlineH, on: outlineOn };
  const cvvX = useStatic(cvvLeft - 5);
  const cvvY = useStatic(cvvTop - 5);
  const cvvW = useStatic(CVV_BOX.width + 10);
  const cvvH = useStatic(CVV_BOX.height + 10);
  const backOutline = { x: cvvX, y: cvvY, w: cvvW, h: cvvH, on: cvvOn, tone: 'dark' as const };

  const measureName = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setNameBox((b) => (Math.abs(b.w - width) < 0.5 && Math.abs(b.h - height) < 0.5 ? b : { w: width, h: height }));
  };
  const measureExpiry = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setExpiryBox((b) => (Math.abs(b.w - width) < 0.5 && Math.abs(b.h - height) < 0.5 ? b : { w: width, h: height }));
  };

  return (
    <View style={styles.screen}>
      <Pressable style={StyleSheet.absoluteFill} onPress={() => Keyboard.dismiss()} accessible={false} />
      <Animated.View style={[StyleSheet.absoluteFill, pageStyle]} pointerEvents="box-none">
        <Animated.View style={[styles.header, { top: insets.top + 14 }, headerStyle]} pointerEvents="none">
          <View style={styles.headerText}>
            <Text style={styles.eyebrow}>CHECKOUT · STEP 2 OF 2</Text>
            <Text style={styles.title}>Card details</Text>
          </View>
          <View style={styles.total}>
            <Text style={styles.totalLabel}>Total</Text>
            <Text style={styles.totalValue}>{amount}</Text>
          </View>
        </Animated.View>

        <View pointerEvents="none" style={[styles.shadow, { top: cardTop - 20, left: (screenW - cardW) / 2 - 40 }]}>
          <Canvas style={{ width: cardW + 80, height: cardH + 90 }}>
            <Group transform={shadowShift}>
              <RoundedRect x={64} y={44} width={cardW - 48} height={cardH - 10} r={CARD_RADIUS} color={glowColor} opacity={0.3}>
                <BlurMask blur={28} style="normal" />
              </RoundedRect>
              <RoundedRect x={52} y={34} width={cardW - 24} height={cardH - 8} r={CARD_RADIUS} color="rgba(0,0,0,0.7)">
                <BlurMask blur={12} style="normal" />
              </RoundedRect>
            </Group>
          </Canvas>
        </View>

        <GestureDetector gesture={cardGesture}>
          <View
            style={[styles.card, { top: cardTop, left: (screenW - cardW) / 2, width: cardW, height: cardH }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={`Card preview${network !== 'none' ? `, ${network}` : ''}`}
          >
            <Animated.View style={[StyleSheet.absoluteFill, liftStyle]}>
              <View collapsable={false} style={StyleSheet.absoluteFill}>
                <Animated.View style={[StyleSheet.absoluteFill, styles.face, frontStyle]}>
                  <CardArt
                    width={cardW}
                    height={cardH}
                    face="front"
                    from={from}
                    to={to}
                    mix={mix}
                    shine={shine}
                    tiltX={tiltX}
                    tiltY={tiltY}
                    origin={markOrigin}
                    outline={frontOutline}
                    orbit={orbit}
                  />
                  <Text style={styles.issuer}>{issuer.toUpperCase()}</Text>
                  <View style={styles.mark}>
                    <Animated.View key={network} entering={markIn} exiting={markOut}>
                      <NetworkMark network={network} />
                    </Animated.View>
                  </View>
                  <View style={[styles.contactless, { left: CHIP.x + CHIP.width + 12, top: CHIP.y + 6 }]}>
                    <SymbolView name="wave.3.right" size={17} tintColor="rgba(255,255,255,0.75)" weight="medium" />
                  </View>
                  <View style={[styles.numberRow, { top: numberTop, left: CARD_PAD, gap: GROUP_GAP }]}>
                    {[0, 1, 2, 3].map((group) => (
                      <View key={group} style={styles.group}>
                        {[0, 1, 2, 3].map((k) => {
                          const i = group * 4 + k;
                          return (
                            <CardReel
                              key={k}
                              char={numberDigits[i] ?? ''}
                              placeholder="•"
                              width={slotW}
                              lineHeight={NUMBER_LINE}
                              textStyle={styles.numberGlyph}
                              delay={(15 - i) * 22}
                              still={reducedMotion}
                            />
                          );
                        })}
                      </View>
                    ))}
                  </View>
                  <View style={[styles.bottomRow, { top: bottomTop, left: CARD_PAD, right: CARD_PAD }]}>
                    <View style={styles.holder}>
                      <View style={styles.hug} onLayout={measureName}>
                        <Text style={styles.cardLabel}>CARD HOLDER</Text>
                        <View style={styles.nameLine}>
                          {name.length === 0 ? (
                            <Animated.Text entering={letterIn} exiting={letterOut} style={[styles.nameGlyph, styles.placeholder]}>
                              YOUR NAME
                            </Animated.Text>
                          ) : (
                            name.split('').map((c, i) => (
                              <Animated.Text key={i} entering={reducedMotion ? undefined : letterIn} exiting={letterOut} style={styles.nameGlyph}>
                                {c === ' ' ? ' ' : c}
                              </Animated.Text>
                            ))
                          )}
                        </View>
                      </View>
                    </View>
                    <View style={styles.expires} onLayout={measureExpiry}>
                      <Text style={styles.cardLabel}>EXPIRES</Text>
                      <View style={styles.expiryLine}>
                        {[0, 1].map((i) => (
                          <CardReel
                            key={i}
                            char={expiryDigits[i] ?? ''}
                            placeholder="M"
                            width={10}
                            lineHeight={SMALL_LINE}
                            textStyle={styles.smallGlyph}
                            delay={(3 - i) * 30}
                            still={reducedMotion}
                          />
                        ))}
                        <Text style={[styles.smallGlyph, styles.slash]}>/</Text>
                        {[2, 3].map((i) => (
                          <CardReel
                            key={i}
                            char={expiryDigits[i] ?? ''}
                            placeholder="Y"
                            width={10}
                            lineHeight={SMALL_LINE}
                            textStyle={styles.smallGlyph}
                            delay={(3 - i) * 30}
                            still={reducedMotion}
                          />
                        ))}
                      </View>
                    </View>
                  </View>
                </Animated.View>
              </View>
              <View collapsable={false} style={StyleSheet.absoluteFill}>
                <Animated.View style={[StyleSheet.absoluteFill, styles.face, backStyle]}>
                  <CardArt
                    width={cardW}
                    height={cardH}
                    face="back"
                    from={from}
                    to={to}
                    mix={mix}
                    shine={shine}
                    tiltX={tiltX}
                    tiltY={tiltY}
                    origin={markOrigin}
                    outline={backOutline}
                    orbit={orbit}
                  />
                  <Text
                    numberOfLines={1}
                    style={[styles.signature, { top: STRIP.y + 4, left: STRIP.x + 12, width: cardW - STRIP.x * 2 - CVV_BOX.width - 30 }]}
                  >
                    {name ? titleCase(name) : ''}
                  </Text>
                  <Text style={[styles.cvvLabel, { top: STRIP.y - 16, right: STRIP.x + 4 }]}>CVV</Text>
                  <View style={[styles.cvvBox, { top: cvvTop, left: cvvLeft }]}>
                    {[0, 1, 2].map((i) => (
                      <CardReel
                        key={i}
                        char={cvvDigits[i] ?? ''}
                        placeholder="•"
                        width={13}
                        lineHeight={22}
                        textStyle={styles.cvvGlyph}
                        delay={(2 - i) * 40}
                        still={reducedMotion}
                      />
                    ))}
                  </View>
                  <Text style={[styles.finePrint, { left: CARD_PAD, right: CARD_PAD, bottom: 18 }]}>
                    {`Authorised signature. This card stays the property of ${issuer} and is to be returned on request.`}
                  </Text>
                </Animated.View>
              </View>
            </Animated.View>
          </View>
        </GestureDetector>

        <View style={[styles.form, { top: formTop, height: formH }]}>
          <View style={[styles.slot, { left: fieldRects.number.x, top: fieldRects.number.y, width: fieldRects.number.w }]}>
            <FormField
              label="Card number"
              value={groupNumber(number)}
              placeholder="0000 0000 0000 0000"
              keyboardType="number-pad"
              maxLength={19}
              focused={field === 'number'}
              caret={playing && field === 'number'}
              done={done.number}
              inputRef={numberRef}
              onFocus={() => onFocus('number')}
              onBlur={() => onBlur('number')}
              onChangeText={(text) => edit('number', text, true)}
            />
          </View>
          <View style={[styles.slot, { left: fieldRects.name.x, top: fieldRects.name.y, width: fieldRects.name.w }]}>
            <FormField
              label="Cardholder name"
              value={name}
              placeholder="As printed on the card"
              autoCapitalize="characters"
              returnKeyType="next"
              onSubmitEditing={() => expiryRef.current?.focus()}
              focused={field === 'name'}
              caret={playing && field === 'name'}
              done={done.name}
              inputRef={nameRef}
              onFocus={() => onFocus('name')}
              onBlur={() => onBlur('name')}
              onChangeText={(text) => edit('name', text, true)}
            />
          </View>
          <View style={[styles.slot, { left: fieldRects.expiry.x, top: fieldRects.expiry.y, width: fieldRects.expiry.w }]}>
            <FormField
              label={expired ? 'Card expired' : 'Expiry'}
              value={showExpiry(expiry)}
              placeholder="MM/YY"
              keyboardType="number-pad"
              maxLength={5}
              error={expired}
              focused={field === 'expiry'}
              caret={playing && field === 'expiry'}
              done={done.expiry}
              inputRef={expiryRef}
              onFocus={() => onFocus('expiry')}
              onBlur={() => onBlur('expiry')}
              onChangeText={(text) => edit('expiry', text, true)}
            />
          </View>
          <View style={[styles.slot, { left: fieldRects.cvv.x, top: fieldRects.cvv.y, width: fieldRects.cvv.w }]}>
            <FormField
              label="CVV"
              value={cvv}
              placeholder="3 digits"
              keyboardType="number-pad"
              maxLength={3}
              focused={field === 'cvv'}
              caret={playing && field === 'cvv'}
              done={done.cvv}
              inputRef={cvvRef}
              onFocus={() => onFocus('cvv')}
              onBlur={() => onBlur('cvv')}
              onChangeText={(text) => edit('cvv', text, true)}
            />
          </View>
          <View pointerEvents="none" style={[styles.ring, { width: formW + 4, height: formH + 4 }]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <FocusRing x={ringX} y={ringY} w={ringW} h={ringH} on={ringOn} color={ringColor} />
            </Canvas>
          </View>
        </View>

        <View style={[styles.saveRow, { top: formTop + formH + SAVE_GAP }]}>
          <View style={styles.saveText}>
            <Text style={styles.saveTitle}>Save card for next time</Text>
            <Text style={styles.saveNote}>{`Kept by ${issuer}, never on this phone`}</Text>
          </View>
          <Switch
            value={save}
            onValueChange={(next) => {
              takeOver();
              setSave(next);
            }}
            trackColor={{ true: SUCCESS, false: '#2a2c35' }}
            accessibilityLabel="Save card for next time"
          />
        </View>
      </Animated.View>

      {/* The compact bar: the page slides under it while the keyboard is up. */}
      <Animated.View pointerEvents="none" style={[styles.bar, { height: insets.top + BAR_H }, barStyle]}>
        <Animated.View style={[styles.barRow, { top: insets.top }, barRowStyle]}>
          <Text style={styles.barTitle}>Card details</Text>
          <View style={styles.barTotal}>
            <Text style={styles.barTotalLabel}>Total</Text>
            <Text style={styles.barTotalValue}>{amount}</Text>
          </View>
        </Animated.View>
      </Animated.View>

      <View pointerEvents="none" style={[styles.secure, { top: payTop - 34 }]}>
        <SymbolView name="lock.fill" size={11} tintColor={MUTED} />
        <Text style={styles.secureText}>Encrypted end to end · never stored on this device</Text>
      </View>
      <PayButton
        top={payTop}
        width={formW}
        label={`Pay ${amount}`}
        enabled={valid && stage === 'idle'}
        pressed={pressed}
        stage={stage}
        ready={ready}
        fold={fold}
        check={check}
        spin={spin}
        onPress={() => {
          takeOver();
          pay(true);
        }}
      />
    </View>
  );
}

/** A shared value that never changes (for the back's fixed CVV outline). */
function useStatic(value: number) {
  const sv = useSharedValue(value);
  useEffect(() => {
    sv.set(value);
  }, [sv, value]);
  return sv;
}

type RingProps = {
  x: SharedValue<number>;
  y: SharedValue<number>;
  w: SharedValue<number>;
  h: SharedValue<number>;
  on: SharedValue<number>;
  color: SharedValue<string>;
};

/** The form's focus ring: one crisp ring in the card's light colour that glides between fields. */
function FocusRing({ x, y, w, h, on, color }: RingProps) {
  const rx = useDerivedValue(() => x.get() + 2 + 0.75);
  const ry = useDerivedValue(() => y.get() + 2 + 0.75);
  const rw = useDerivedValue(() => Math.max(0, w.get() - 1.5));
  const rh = useDerivedValue(() => Math.max(0, h.get() - 1.5));
  return <RoundedRect x={rx} y={ry} width={rw} height={rh} r={FIELD_RADIUS - 0.75} style="stroke" strokeWidth={1.5} color={color} opacity={on} />;
}

type FormFieldProps = {
  label: string;
  value: string;
  placeholder: string;
  focused: boolean;
  /** autoPlay's caret: the field is "focused" without a keyboard. */
  caret: boolean;
  /** Complete and valid: a check shows at the field's end. */
  done: boolean;
  error?: boolean;
  inputRef: Ref<TextInput>;
  keyboardType?: KeyboardTypeOptions;
  autoCapitalize?: 'characters' | 'none';
  maxLength?: number;
  returnKeyType?: 'next' | 'done';
  onSubmitEditing?: () => void;
  onFocus: () => void;
  onBlur: () => void;
  onChangeText: (text: string) => void;
};

/** A field with its label inside, above the value, and a check (or a warning) at its end. */
function FormField({
  label,
  value,
  placeholder,
  focused,
  caret,
  done,
  error = false,
  inputRef,
  keyboardType = 'default',
  autoCapitalize = 'none',
  maxLength,
  returnKeyType = 'done',
  onSubmitEditing,
  onFocus,
  onBlur,
  onChangeText,
}: FormFieldProps) {
  return (
    <Animated.View
      style={[
        styles.fieldBox,
        { borderColor: error ? DANGER : FIELD_BORDER, transitionProperty: 'borderColor', transitionDuration: 200 },
      ]}
    >
      <Animated.Text style={[styles.fieldLabel, { color: error ? DANGER : focused ? TEXT : MUTED, transitionProperty: 'color', transitionDuration: 200 }]}>
        {label}
      </Animated.Text>
      <TextInput
        ref={inputRef}
        style={styles.input}
        value={value}
        placeholder={placeholder}
        placeholderTextColor={PLACEHOLDER}
        keyboardType={keyboardType}
        keyboardAppearance="dark"
        autoCapitalize={autoCapitalize}
        autoCorrect={false}
        maxLength={maxLength}
        returnKeyType={returnKeyType}
        onSubmitEditing={onSubmitEditing}
        submitBehavior={onSubmitEditing ? 'submit' : 'blurAndSubmit'}
        selectionColor={TEXT}
        onFocus={onFocus}
        onBlur={onBlur}
        onChangeText={onChangeText}
        accessibilityLabel={label}
      />
      {caret && (
        <View pointerEvents="none" style={styles.mirror}>
          <Text style={[styles.mirrorText, styles.ghost]}>{value.replace(/ /g, ' ')}</Text>
          <Animated.View
            style={[
              styles.caret,
              {
                animationName: { '0%': { opacity: 1 }, '45%': { opacity: 1 }, '55%': { opacity: 0 }, '100%': { opacity: 0 } },
                animationDuration: '1s',
                animationIterationCount: 'infinite',
              },
            ]}
          />
        </View>
      )}
      <View pointerEvents="none" style={styles.badge}>
        {error ? (
          <Animated.View key="error" entering={badgeIn} exiting={badgeOut}>
            <SymbolView name="exclamationmark.circle.fill" size={18} tintColor={DANGER} />
          </Animated.View>
        ) : done ? (
          <Animated.View key="done" entering={badgeIn} exiting={badgeOut}>
            <SymbolView name="checkmark.circle.fill" size={18} tintColor={SUCCESS} />
          </Animated.View>
        ) : null}
      </View>
    </Animated.View>
  );
}

const AnimatedPath = Animated.createAnimatedComponent(Path);
const CHECK_LENGTH = 26;

type PayButtonProps = {
  top: number;
  width: number;
  label: string;
  enabled: boolean;
  pressed: boolean;
  stage: 'idle' | 'paying' | 'paid';
  ready: SharedValue<number>;
  fold: SharedValue<number>;
  check: SharedValue<number>;
  spin: SharedValue<number>;
  onPress: () => void;
};

/** The pay button: dark until the card is complete, then white; it folds into a spinning ring, then fills green behind a check. */
function PayButton({ top, width, label, enabled, pressed, stage, ready, fold, check, spin, onPress }: PayButtonProps) {
  const [held, setHeld] = useState(false);
  const down = pressed || held;
  const shell = useAnimatedStyle(() => {
    const f = fold.get();
    const w = width + (PAY_HEIGHT - width) * f;
    const base = interpolateColor(ready.get(), [0, 1], [PAY_OFF, TEXT]);
    return {
      width: w,
      left: (width - w) / 2,
      backgroundColor: interpolateColor(check.get(), [0, 1], [base, SUCCESS]),
    };
  });
  const labelStyle = useAnimatedStyle(() => ({
    opacity: Math.max(0, 1 - fold.get() * 3),
    color: interpolateColor(ready.get(), [0, 1], [PAY_OFF_TEXT, BACKGROUND]),
  }));
  // The ring only shows while paying: on the way back to the pill it is already gone.
  const spinning = stage === 'paying';
  const spinner = useAnimatedStyle(() => ({
    opacity: spinning ? Math.max(0, Math.min(1, (fold.get() - 0.6) * 4)) * (1 - check.get()) : withTiming(0, { duration: 120 }),
    transform: [{ rotate: `${spin.get() * 360}deg` }],
  }));
  const checkProps = useAnimatedProps(() => ({ strokeDashoffset: CHECK_LENGTH * (1 - check.get()) }));
  return (
    <Pressable
      style={[styles.pay, { top, left: SIDE, width }]}
      disabled={!enabled}
      onPressIn={() => setHeld(true)}
      onPressOut={() => setHeld(false)}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={stage === 'paid' ? 'Paid' : stage === 'paying' ? 'Paying' : label}
      accessibilityState={{ disabled: !enabled, busy: stage === 'paying' }}
    >
      <Animated.View
        style={[
          styles.payShell,
          shell,
          {
            transform: [{ scale: down && enabled ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 140,
          },
        ]}
      >
        <Animated.Text numberOfLines={1} style={[styles.payLabel, labelStyle]}>
          {label}
        </Animated.Text>
        <Animated.View style={[styles.payIcon, spinner]}>
          <Svg width={26} height={26} viewBox="0 0 26 26">
            <Circle cx={13} cy={13} r={10} stroke="rgba(13,14,18,0.15)" strokeWidth={2.6} fill="none" />
            <Path d="M13 3 A10 10 0 0 1 23 13" stroke={BACKGROUND} strokeWidth={2.6} strokeLinecap="round" fill="none" />
          </Svg>
        </Animated.View>
        <View style={styles.payIcon}>
          <Svg width={26} height={26} viewBox="0 0 26 26">
            <AnimatedPath
              d="M6.5 13.5 L11 18 L19.5 8.5"
              stroke="#ffffff"
              strokeWidth={3}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
              strokeDasharray={CHECK_LENGTH}
              animatedProps={checkProps}
            />
          </Svg>
        </View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  headerText: {
    gap: 4,
  },
  eyebrow: {
    color: MUTED,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.6,
  },
  title: {
    color: TEXT,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  total: {
    alignItems: 'flex-end',
    gap: 2,
    paddingBottom: 3,
  },
  totalLabel: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
  },
  totalValue: {
    color: TEXT,
    fontSize: 17,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  shadow: {
    position: 'absolute',
  },
  bar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    backgroundColor: BACKGROUND,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(255,255,255,0.08)',
  },
  barRow: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: BAR_H,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  barTitle: {
    color: TEXT,
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  barTotal: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 6,
  },
  barTotalLabel: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '500',
  },
  barTotalValue: {
    color: TEXT,
    fontSize: 15,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  card: {
    position: 'absolute',
  },
  face: {
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backfaceVisibility: 'hidden',
  },
  issuer: {
    position: 'absolute',
    left: CARD_PAD,
    top: 24,
    color: 'rgba(255, 255, 255, 0.92)',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 3.2,
  },
  mark: {
    position: 'absolute',
    right: CARD_PAD,
    top: 18,
    height: 26,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  contactless: {
    position: 'absolute',
  },
  numberRow: {
    position: 'absolute',
    flexDirection: 'row',
  },
  group: {
    flexDirection: 'row',
  },
  numberGlyph: {
    color: '#ffffff',
    fontSize: NUMBER_SIZE,
    fontWeight: '600',
    textShadowColor: 'rgba(0, 0, 0, 0.35)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 1.5,
  },
  bottomRow: {
    position: 'absolute',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  holder: {
    flex: 1,
    alignItems: 'flex-start',
  },
  hug: {
    gap: 4,
  },
  cardLabel: {
    color: 'rgba(255, 255, 255, 0.6)',
    fontSize: 8.5,
    fontWeight: '700',
    letterSpacing: 1.4,
  },
  nameLine: {
    flexDirection: 'row',
    height: SMALL_LINE,
    overflow: 'hidden',
  },
  nameGlyph: {
    color: '#ffffff',
    fontSize: SMALL_SIZE,
    lineHeight: SMALL_LINE,
    fontWeight: '600',
    letterSpacing: 1,
  },
  placeholder: {
    color: 'rgba(255, 255, 255, 0.4)',
  },
  expires: {
    gap: 4,
    alignItems: 'flex-end',
  },
  expiryLine: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  smallGlyph: {
    color: '#ffffff',
    fontSize: SMALL_SIZE,
    fontWeight: '600',
  },
  slash: {
    lineHeight: SMALL_LINE,
    marginHorizontal: 2,
    color: 'rgba(255, 255, 255, 0.7)',
  },
  signature: {
    position: 'absolute',
    color: '#2a2f45',
    fontFamily: process.env.EXPO_OS === 'ios' ? 'Snell Roundhand' : 'cursive',
    fontSize: 19,
    fontWeight: '600',
    lineHeight: 28,
  },
  cvvLabel: {
    position: 'absolute',
    color: 'rgba(255, 255, 255, 0.75)',
    fontSize: 9,
    fontWeight: '700',
    letterSpacing: 1.4,
  },
  cvvBox: {
    position: 'absolute',
    width: CVV_BOX.width,
    height: CVV_BOX.height,
    borderRadius: 6,
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cvvGlyph: {
    color: '#15161c',
    fontSize: 15,
    fontWeight: '700',
    fontStyle: 'italic',
  },
  finePrint: {
    position: 'absolute',
    color: 'rgba(255, 255, 255, 0.55)',
    fontSize: 8,
    fontWeight: '500',
    lineHeight: 11,
  },
  form: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
  },
  slot: {
    position: 'absolute',
    height: FIELD_H,
  },
  ring: {
    position: 'absolute',
    left: -2,
    top: -2,
  },
  fieldBox: {
    height: FIELD_H,
    borderRadius: FIELD_RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    backgroundColor: FIELD_BG,
  },
  fieldLabel: {
    position: 'absolute',
    left: 16,
    top: LABEL_TOP,
    fontSize: 12,
    fontWeight: '500',
  },
  input: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: VALUE_TOP - 4,
    height: VALUE_H + 8,
    paddingLeft: 16,
    paddingRight: 44,
    paddingVertical: 0,
    color: TEXT,
    fontSize: 17,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  mirror: {
    position: 'absolute',
    left: 16,
    top: VALUE_TOP - 4,
    height: VALUE_H + 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  mirrorText: {
    fontSize: 17,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  ghost: {
    opacity: 0,
  },
  caret: {
    width: 2,
    height: 21,
    borderRadius: 1,
    marginLeft: 1,
    backgroundColor: TEXT,
  },
  badge: {
    position: 'absolute',
    right: 14,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  saveRow: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  },
  saveText: {
    flex: 1,
    gap: 2,
  },
  saveTitle: {
    color: TEXT,
    fontSize: 15,
    fontWeight: '600',
  },
  saveNote: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
  },
  secure: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  secureText: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
  },
  pay: {
    position: 'absolute',
    height: PAY_HEIGHT,
  },
  payShell: {
    position: 'absolute',
    top: 0,
    height: PAY_HEIGHT,
    borderRadius: PAY_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  payLabel: {
    fontSize: 17,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  payIcon: {
    position: 'absolute',
    width: 26,
    height: 26,
  },
});
