import { Canvas, Image as SkiaImage, Picture, Rect, Skia, type SkImage, createPicture } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { SymbolView, type SFSymbol } from 'expo-symbols';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DRY_MS, STRIDE, addSample, paintInk, penAt, retime } from './InkPainter';
import { signatureScript } from './SignatureScript';
import { WAX_SEAL_SIZE, WaxSeal } from './WaxSeal';

type Props = {
  /** Signs the name, replays it, seals the page, opens it again and wipes it, over and over. */
  autoPlay?: boolean;
  /** Printed under the signature line. */
  signer?: string;
  role?: string;
  date?: string;
  /** The letters pressed into the wax. */
  monogram?: string;
  /** Called with the ink (x, y, ms, width, stroke per sample) when the page is sealed by hand. */
  onDone?: (ink: number[]) => void;
};

const photo = (id: string, width: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
// Cream paper with a fine speckle for the pad; white laid paper for the pages behind it.
const PAD_PAPER = photo('photo-1629968417850-3505f5180761', 900);
const PAGE_PAPER = photo('photo-1601662528567-526cd06f6582', 600);

const SERIF = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';
const BACKGROUND = '#15120e';
const CREAM = '#efe6d5';
const MUTED = '#8c8170';
const PRINT = '#463d31';
const RULE = '#9c8f78';

// Layout, in pt.
const SIDE = 18;
const PAD_TOP = 96;
const PAD_RADIUS = 22;
const BUTTON_HEIGHT = 54;
const BUTTONS_GAP = 22;
// The signature line sits this share of the pad's height down, the signature's baseline LINE_LIFT above it.
const LINE_AT = 0.7;
const LINE_LIFT = 5;
// The signature runs from SIGN_LEFT (clear of the × mark) to SIGN_RIGHT pt from the pad's right edge.
const SIGN_LEFT = 50;
const SIGN_RIGHT = 26;

// The pad's own clock runs on frames; a stroke made after a pause longer than REPLAY_GAP ms is
// replayed REPLAY_GAP after the one before. The replay starts REPLAY_LEAD ms after the press, over
// the ghost of the ink it redraws, which fades in over GHOST_IN and out over GHOST_OUT ms.
const REPLAY_GAP = 450;
const REPLAY_LEAD = 260;
const GHOST_IN = 220;
const GHOST_OUT = 380;
// The pen fades in and out with a time constant of PEN_FADE ms.
const PEN_FADE = 70;
const MAX_SAMPLES = 9000;

// Clear: the wiper crosses the pad in SWEEP_MS on an ease-in-out curve.
const SWEEP_MS = 1300;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
// Done: the page shrinks to CARD_SCALE onto two pages behind it in SEAL_MS, then the wax lands:
// it drops from STAMP_FROM× in STAMP_MS, accelerating, squashes by STAMP_SQUASH and settles.
const CARD_SCALE = 0.56;
const SEAL_MS = 720;
const STAMP_MS = 230;
const STAMP_FROM = 1.75;
const STAMP_SQUASH = 0.06;
const STAMP_EASE = Easing.bezier(0.55, 0, 1, 0.45);
const UNSEAL_MS = 560;
// The two pages behind the signed one, as offsets (pt, before scaling) and turns (°).
const BACK_PAGES = [
  { x: -26, y: -18, turn: -5.5 },
  { x: 24, y: -8, turn: 4 },
];

// autoPlay loop (seconds): sign, replay, seal, open, wipe. A button "presses" PRESS before it acts.
const LOOP = 17.4;
const SIGN_AT = 0.7;
const REPLAY_AT = 5.9;
const DONE_AT = 11.1;
const UNSEAL_AT = 14.3;
const CLEAR_AT = 15.5;
const PRESS = 0.14;
// After a touch, autoPlay waits this long (s) with no finger down before it picks up again.
const USER_HOLD = 4;

/** Whether `at` was passed going from `before` to `t` on a clock that wraps every LOOP. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return t >= before ? before < at && t >= at : at > before || at <= t;
}

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function flash(press: SharedValue<number>) {
  'worklet';
  press.set(withSequence(withTiming(1, { duration: 90 }), withTiming(0, { duration: 240 })));
}

/**
 * A signature pad on cream paper. The finger writes in fountain-pen ink whose width follows its
 * speed, drawn through as smooth curves, glossy while wet and drying to blue-black. Clear sweeps a
 * felt wiper across it, Replay redraws the signature at the speed it was written, and Done shrinks
 * the page onto the rest of the lease and presses a wax seal on it.
 */
export function InkSignaturePad({
  autoPlay = false,
  signer = 'Mara Ellison',
  role = 'Tenant',
  date = '13 Oct 2026',
  monogram = 'ME',
  onDone,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenW, height: screenH } = useWindowDimensions();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  // The paper photo, loaded by hand so a failed download is caught (useImage leaves the promise
  // rejection unhandled) and the plain cream fill under it simply stays.
  const [paper, setPaper] = useState<SkImage | null>(null);
  useEffect(() => {
    let alive = true;
    Skia.Data.fromURI(PAD_PAPER)
      .then((data) => {
        const image = Skia.Image.MakeImageFromEncoded(data);
        if (alive && image) setPaper(image);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const padW = screenW - SIDE * 2;
  const padTop = insets.top + PAD_TOP;
  const buttonsTop = screenH - insets.bottom - 14 - BUTTON_HEIGHT;
  const padH = Math.max(300, buttonsTop - BUTTONS_GAP - padTop);
  const lineY = Math.round(padH * LINE_AT);
  // Where the sealed card sits, and the seal on it.
  const cardCentreY = insets.top + (screenH - insets.top - insets.bottom) * 0.45;
  const shiftY = cardCentreY - (padTop + padH / 2);
  const cardRight = screenW / 2 + (padW * CARD_SCALE) / 2;
  const cardBottom = cardCentreY + (padH * CARD_SCALE) / 2;
  // The seal sits over the card's lower right corner, clear of the signature.
  const sealX = cardRight - WAX_SEAL_SIZE / 2 - 22;
  const sealY = cardBottom - WAX_SEAL_SIZE / 2 - 24;

  // The script as ink samples (x, y, ms from its start, -, stroke).
  const script = useMemo(() => {
    const s = signatureScript(SIGN_LEFT, padW - SIGN_RIGHT, lineY - LINE_LIFT);
    const samples: number[] = [];
    s.x.forEach((x, i) => samples.push(x, s.y[i], s.t[i] * 1000, 0, s.stroke[i]));
    return { samples, strokes: s.stroke[s.stroke.length - 1] + 1 };
  }, [padW, lineY]);

  const [hasInk, setHasInk] = useState(false);
  const [sealed, setSealed] = useState(false);

  const ink = useSharedValue<number[]>([]);
  const replay = useSharedValue<number[]>([]);
  const replayCursor = useSharedValue(0);
  const ghost = useSharedValue(0);
  const strokeCount = useSharedValue(0);
  const drawing = useSharedValue(false);
  const clock = useSharedValue(0);
  // The clock as the picture sees it: it only moves while something is still changing.
  const wetClock = useSharedValue(0);
  const sweep = useSharedValue(0);
  const inkAlpha = useSharedValue(1);
  const seal = useSharedValue(0);
  const stamp = useSharedValue(0);
  const fade = useSharedValue(1);
  const penX = useSharedValue(0);
  const penY = useSharedValue(0);
  const penLift = useSharedValue(0);
  const penAlpha = useSharedValue(0);
  const penOn = useSharedValue(false);
  // autoPlay: where the script is (sample index, or −1), when it started, and its clock.
  const scriptCursor = useSharedValue(-1);
  const scriptFrom = useSharedValue(0);
  const scriptStroke = useSharedValue(0);
  const looping = useSharedValue(auto);
  const loopAt = useSharedValue(0);
  const hold = useSharedValue(0);
  const pressClear = useSharedValue(0);
  const pressReplay = useSharedValue(0);
  const pressDone = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    loopAt.set(0);
    hold.set(0);
  }, [auto, looping, loopAt, hold]);

  useAnimatedReaction(
    () => ink.get().length > 0,
    (now, before) => {
      if (now !== before) scheduleOnRN(setHasInk, now);
    },
  );

  const thud = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };
  const tap = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Ends a replay now: the redrawn ink becomes the ink. */
  const finishReplay = () => {
    'worklet';
    const r = replay.get();
    if (r.length === 0) return;
    ink.set(r);
    replay.set([]);
    ghost.set(withTiming(0, { duration: GHOST_OUT }));
    penOn.set(false);
  };

  const startReplay = () => {
    'worklet';
    const current = ink.get();
    if (current.length === 0 || sweep.get() > 0 || seal.get() > 0) return;
    finishReplay();
    replay.set(retime(ink.get(), clock.get() + REPLAY_LEAD, REPLAY_GAP));
    replayCursor.set(0);
    ghost.set(withTiming(1, { duration: GHOST_IN }));
    penOn.set(!reducedMotion);
  };

  const wipe = () => {
    'worklet';
    ink.set([]);
    replay.set([]);
    ghost.set(0);
    sweep.set(0);
    inkAlpha.set(1);
    penOn.set(false);
  };

  const startSweep = (user: boolean) => {
    'worklet';
    if (ink.get().length === 0 || sweep.get() > 0 || seal.get() > 0) return;
    finishReplay();
    scriptCursor.set(-1);
    penOn.set(false);
    if (user) scheduleOnRN(tap);
    if (reducedMotion) {
      inkAlpha.set(withTiming(0, { duration: 260 }, (done) => done && wipe()));
      return;
    }
    sweep.set(withTiming(1, { duration: SWEEP_MS, easing: EASE_IN_OUT }, (done) => done && wipe()));
  };

  const sealUp = (user: boolean) => {
    'worklet';
    if (ink.get().length === 0 || seal.get() > 0 || sweep.get() > 0) return;
    finishReplay();
    scriptCursor.set(-1);
    penOn.set(false);
    scheduleOnRN(setSealed, true);
    if (user && onDone) scheduleOnRN(onDone, ink.get().slice());
    if (reducedMotion) {
      fade.set(withSequence(withTiming(0, { duration: 160 }), withTiming(1, { duration: 260 })));
      seal.set(withDelay(160, withTiming(1, { duration: 0 })));
      stamp.set(withDelay(420, withTiming(1, { duration: 260 }, (done) => done && user && scheduleOnRN(thud))));
      return;
    }
    seal.set(withTiming(1, { duration: SEAL_MS, easing: EASE_IN_OUT }));
    stamp.set(
      withDelay(
        SEAL_MS - 60,
        withSequence(
          withTiming(1, { duration: STAMP_MS, easing: STAMP_EASE }, (done) => done && user && scheduleOnRN(thud)),
          withTiming(1 + STAMP_SQUASH, { duration: 80 }),
          withTiming(1, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) }),
        ),
      ),
    );
  };

  const sealDown = () => {
    'worklet';
    if (seal.get() < 1) return;
    scheduleOnRN(setSealed, false);
    stamp.set(withTiming(0, { duration: 180 }));
    if (reducedMotion) {
      fade.set(withSequence(withTiming(0, { duration: 160 }), withTiming(1, { duration: 260 })));
      seal.set(withDelay(160, withTiming(0, { duration: 0 })));
      return;
    }
    seal.set(withDelay(120, withTiming(0, { duration: UNSEAL_MS, easing: EASE_IN_OUT })));
  };

  /** A touch: autoPlay stops writing and waits until the pad has been left alone for a while. */
  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    if (scriptCursor.get() >= 0) {
      scriptCursor.set(-1);
      penOn.set(false);
    }
  };

  /** Where the loop picks up after a touch: the page is opened or wiped first if need be. */
  const resumePoint = () => {
    'worklet';
    if (seal.get() > 0.5) return UNSEAL_AT - 0.4;
    if (ink.get().length > 0) return CLEAR_AT - 0.5;
    return 0;
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64);
    const now = clock.get() + dt;
    clock.set(now);

    if (looping.get()) {
      if (hold.get() > 0) {
        if (!drawing.get()) {
          hold.set(hold.get() - dt / 1000);
          if (hold.get() <= 0) loopAt.set(resumePoint());
        }
      } else {
        const before = loopAt.get();
        const t = (before + dt / 1000) % LOOP;
        loopAt.set(t);
        if (crossed(before, t, SIGN_AT) && seal.get() === 0 && sweep.get() === 0) {
          scriptFrom.set(now - (t - SIGN_AT) * 1000);
          scriptCursor.set(0);
          scriptStroke.set(strokeCount.get() + 1);
          strokeCount.set(strokeCount.get() + script.strokes);
          penOn.set(true);
        }
        if (crossed(before, t, REPLAY_AT - PRESS) && ink.get().length > 0) flash(pressReplay);
        if (crossed(before, t, REPLAY_AT)) startReplay();
        if (crossed(before, t, DONE_AT - PRESS) && ink.get().length > 0) flash(pressDone);
        if (crossed(before, t, DONE_AT)) sealUp(false);
        if (crossed(before, t, UNSEAL_AT)) sealDown();
        if (crossed(before, t, CLEAR_AT - PRESS) && ink.get().length > 0) flash(pressClear);
        if (crossed(before, t, CLEAR_AT)) startSweep(false);
      }
    }

    // autoPlay's hand: every script sample whose time has come goes down as ink.
    const cursor = scriptCursor.get();
    if (cursor >= 0) {
      const samples = script.samples;
      const count = samples.length / STRIDE;
      const rel = now - scriptFrom.get();
      let k = cursor;
      if (samples[k * STRIDE + 2] <= rel && ink.get().length < MAX_SAMPLES * STRIDE) {
        const next = ink.get().slice();
        while (k < count && samples[k * STRIDE + 2] <= rel) {
          const at = k * STRIDE;
          const starts = k === 0 || samples[at + 4] !== samples[at - 1];
          addSample(next, samples[at], samples[at + 1], scriptFrom.get() + samples[at + 2], scriptStroke.get() + samples[at + 4], starts, -1);
          k += 1;
        }
        ink.set(next);
      }
      const pen = penAt(samples, k, rel);
      penX.set(pen.x);
      penY.set(pen.y);
      penLift.set(k === 0 ? 1 : pen.lift);
      if (k >= count) {
        scriptCursor.set(-1);
        penOn.set(false);
      } else {
        scriptCursor.set(k);
      }
    }

    // The replay: the pen follows the redrawn ink, and when it has caught up the replay ends.
    const r = replay.get();
    if (r.length > 0) {
      let k = replayCursor.get();
      const count = r.length / STRIDE;
      while (k < count && r[k * STRIDE + 2] <= now) k += 1;
      replayCursor.set(k);
      const pen = penAt(r, k, now);
      penX.set(pen.x);
      penY.set(pen.y);
      penLift.set(k === 0 ? 1 : pen.lift);
      if (k >= count && now > r[r.length - 3] + 140) finishReplay();
    }

    const target = penOn.get() && !reducedMotion ? 1 : 0;
    const alpha = penAlpha.get();
    if (alpha !== target) {
      const next = alpha + (target - alpha) * (1 - Math.exp(-dt / PEN_FADE));
      penAlpha.set(Math.abs(next - target) < 0.01 ? target : next);
    }

    // The picture is drawn again only while ink dries, a replay runs or the pen moves.
    const current = ink.get();
    const lastInk = current.length > 0 ? current[current.length - 3] : -Infinity;
    if (now - lastInk < DRY_MS + 60 || r.length > 0 || penAlpha.get() > 0) wetClock.set(now);
  });

  const picture = useDerivedValue(() =>
    createPicture(
      (canvas) =>
        paintInk(canvas, {
          ink: ink.get(),
          replay: replay.get(),
          now: wetClock.get(),
          ghost: ghost.get(),
          sweep: sweep.get(),
          alpha: inkAlpha.get(),
          width: padW,
          height: padH,
          pen: { x: penX.get(), y: penY.get(), lift: penLift.get(), alpha: penAlpha.get() },
        }),
      { width: padW, height: padH },
    ),
  );

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((event) => {
      if (seal.get() > 0 || sweep.get() > 0 || inkAlpha.get() < 1) return;
      touched();
      finishReplay();
      const stroke = strokeCount.get() + 1;
      strokeCount.set(stroke);
      if (ink.get().length >= MAX_SAMPLES * STRIDE) return;
      const next = ink.get().slice();
      addSample(next, event.x, event.y, clock.get(), stroke, true, -1);
      ink.set(next);
      drawing.set(true);
    })
    .onUpdate((event) => {
      if (!drawing.get() || ink.get().length >= MAX_SAMPLES * STRIDE) return;
      const next = ink.get().slice();
      if (addSample(next, event.x, event.y, clock.get(), strokeCount.get(), false, Math.hypot(event.velocityX, event.velocityY))) {
        ink.set(next);
      }
    })
    .onFinalize(() => {
      drawing.set(false);
    });

  const run = (action: () => void) => {
    scheduleOnUI(() => {
      'worklet';
      touched();
      action();
    });
  };
  const onClear = () => run(() => {
    'worklet';
    startSweep(true);
  });
  const onReplay = () => run(() => {
    'worklet';
    startReplay();
  });
  const onDoneTap = () => run(() => {
    'worklet';
    sealUp(true);
  });
  const onUnseal = () => run(() => {
    'worklet';
    sealDown();
  });

  // The page: it shrinks onto the pages behind it and rises to the middle of the screen.
  const padStyle = useAnimatedStyle(() => {
    const e = seal.get();
    return {
      opacity: fade.get(),
      transform: [{ translateY: shiftY * e }, { scale: 1 - (1 - CARD_SCALE) * e }],
    };
  });
  const chromeStyle = useAnimatedStyle(() => ({ opacity: clamp01(1 - seal.get() * 2.2) }));
  const sealedStyle = useAnimatedStyle(() => ({ opacity: clamp01((seal.get() - 0.55) / 0.45) }));
  const stampStyle = useAnimatedStyle(() => {
    const s = stamp.get();
    const scale = reducedMotion ? 1 : s <= 1 ? STAMP_FROM - (STAMP_FROM - 1) * s : 1 - (s - 1) * 1.6;
    return {
      opacity: clamp01(s * 1.6),
      transform: [{ scale }, { rotate: `${-16 + 10 * Math.min(1, s)}deg` }],
    };
  });

  return (
    <View style={styles.screen}>
      <Animated.View style={[styles.header, { top: insets.top + 12 }, chromeStyle]}>
        <Text style={styles.eyebrow}>STUDIO LEASE · PAGE 4 OF 4</Text>
        <Text style={styles.title}>Sign to accept</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }, sealedStyle]}>
        <Text style={styles.eyebrow}>STUDIO LEASE · COMPLETE</Text>
        <Text style={styles.title}>Lease sealed</Text>
      </Animated.View>

      {BACK_PAGES.map((page, index) => (
        <BackPage
          key={index}
          seal={seal}
          fade={fade}
          shiftY={shiftY}
          page={page}
          style={{ left: SIDE, top: padTop, width: padW, height: padH }}
        />
      ))}

      <Animated.View style={[styles.pad, { left: SIDE, top: padTop, width: padW, height: padH }, padStyle]}>
        <GestureDetector gesture={pan}>
          <View
            style={styles.fill}
            accessible
            accessibilityLabel={`Signature pad for ${signer}`}
            accessibilityHint="Draw your signature above the line"
          >
            <Canvas style={styles.fill}>
              <Rect x={0} y={0} width={padW} height={padH} color="#efe7d6" />
              {paper && <SkiaImage image={paper} x={0} y={0} width={padW} height={padH} fit="cover" />}
              <Picture picture={picture} />
            </Canvas>
            <View pointerEvents="none" style={styles.print}>
              <Text style={styles.printEyebrow}>ACCEPTANCE</Text>
              <Text style={styles.printBody}>
                I have read the lease for Atelier 3B, 14 Rue Vasse, and take the studio from 1 November 2026 for twelve months.
              </Text>
            </View>
            <Animated.Text
              pointerEvents="none"
              style={[
                styles.hint,
                { top: lineY - 64, opacity: hasInk ? 0 : 1, transitionProperty: 'opacity', transitionDuration: 220 },
              ]}
            >
              Sign above the line
            </Animated.Text>
            <View pointerEvents="none" style={[styles.signLine, { top: lineY }]}>
              <Text style={styles.cross}>×</Text>
              <View style={styles.rule} />
              <View style={styles.captions}>
                <Text style={styles.caption}>{`${signer} · ${role}`.toUpperCase()}</Text>
                <Text style={styles.caption}>{date.toUpperCase()}</Text>
              </View>
            </View>
          </View>
        </GestureDetector>
      </Animated.View>

      {/* The wax seal and the line under the sealed card. */}
      <Animated.View pointerEvents="none" style={[styles.seal, { left: sealX, top: sealY }, stampStyle]}>
        <WaxSeal monogram={monogram} />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.sealedNote, { top: cardBottom + 40 }, sealedStyle]}>
        <Text style={styles.sealedLine}>{`Signed by ${signer} · ${date}`}</Text>
        <Text style={styles.sealedHint}>Tap the page to sign again</Text>
      </Animated.View>
      {sealed && (
        <Pressable
          style={[styles.cardHit, { top: cardCentreY - (padH * CARD_SCALE) / 2, height: padH * CARD_SCALE, width: padW * CARD_SCALE, left: (screenW - padW * CARD_SCALE) / 2 }]}
          accessibilityRole="button"
          accessibilityLabel="Open the page to sign again"
          onPress={onUnseal}
        />
      )}

      <Animated.View style={[styles.buttons, { top: buttonsTop }, chromeStyle]} pointerEvents={sealed ? 'none' : 'auto'}>
        <PadButton label="Clear" icon="eraser" disabled={!hasInk} press={pressClear} onPress={onClear} />
        <PadButton label="Replay" icon="arrow.counterclockwise" disabled={!hasInk} press={pressReplay} onPress={onReplay} />
        <PadButton label="Done" icon="checkmark" primary disabled={!hasInk} press={pressDone} onPress={onDoneTap} />
      </Animated.View>
    </View>
  );
}

type BackPageProps = {
  seal: SharedValue<number>;
  fade: SharedValue<number>;
  shiftY: number;
  page: { x: number; y: number; turn: number };
  style: { left: number; top: number; width: number; height: number };
};

/** One of the lease's earlier pages, waiting under the signed one. */
function BackPage({ seal, fade, shiftY, page, style }: BackPageProps) {
  const animated = useAnimatedStyle(() => {
    const e = seal.get();
    return {
      opacity: clamp01(e * 3) * fade.get(),
      transform: [
        { translateY: shiftY * e },
        { scale: 1 - (1 - CARD_SCALE) * e },
        { translateX: page.x * e },
        { translateY: page.y * e },
        { rotate: `${page.turn * e}deg` },
      ],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.backPage, style, animated]}>
      <Image source={PAGE_PAPER} style={styles.fill} contentFit="cover" />
      <View style={styles.backShade} />
    </Animated.View>
  );
}

type PadButtonProps = {
  label: string;
  icon: SFSymbol;
  primary?: boolean;
  disabled: boolean;
  press: SharedValue<number>;
  onPress: () => void;
};

/** A pill button that sinks while held; autoPlay "presses" it through `press`. */
function PadButton({ label, icon, primary = false, disabled, press, onPress }: PadButtonProps) {
  const [held, setHeld] = useState(false);
  const style = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.05 * Math.max(press.get(), held ? 1 : 0) }],
    opacity: disabled ? 0.38 : 1,
  }));
  const tint = primary ? '#1b1610' : CREAM;
  return (
    <Pressable
      style={styles.buttonSlot}
      disabled={disabled}
      onPressIn={() => setHeld(true)}
      onPressOut={() => setHeld(false)}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
    >
      <Animated.View style={[styles.button, primary ? styles.buttonPrimary : styles.buttonGhost, style]}>
        <SymbolView name={icon} size={15} tintColor={tint} weight="semibold" />
        <Text style={[styles.buttonLabel, { color: tint }]}>{label}</Text>
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
  fill: {
    ...StyleSheet.absoluteFill,
  },
  header: {
    position: 'absolute',
    left: SIDE + 4,
    right: SIDE,
    gap: 4,
  },
  eyebrow: {
    color: MUTED,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.8,
  },
  title: {
    color: CREAM,
    fontFamily: SERIF,
    fontWeight: '600',
    fontSize: 33,
    letterSpacing: -0.3,
  },
  pad: {
    position: 'absolute',
    borderRadius: PAD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#efe7d6',
  },
  backPage: {
    position: 'absolute',
    borderRadius: PAD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#f2eee6',
  },
  backShade: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(40, 30, 18, 0.12)',
  },
  print: {
    position: 'absolute',
    left: 26,
    right: 30,
    top: 26,
    gap: 8,
  },
  printEyebrow: {
    color: '#8f826b',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 2,
  },
  printBody: {
    color: PRINT,
    fontFamily: SERIF,
    fontWeight: '600',
    fontSize: 16,
    lineHeight: 22,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: '#a39679',
    fontFamily: SERIF,
    fontWeight: '600',
    fontStyle: 'italic',
    fontSize: 16,
  },
  signLine: {
    position: 'absolute',
    left: 26,
    right: 26,
  },
  cross: {
    position: 'absolute',
    left: 0,
    top: -30,
    color: RULE,
    fontFamily: SERIF,
    fontWeight: '600',
    fontSize: 24,
  },
  rule: {
    height: StyleSheet.hairlineWidth * 2,
    backgroundColor: RULE,
  },
  captions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 9,
  },
  caption: {
    color: '#857862',
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.6,
  },
  seal: {
    position: 'absolute',
  },
  sealedNote: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    gap: 6,
  },
  sealedLine: {
    color: CREAM,
    fontFamily: SERIF,
    fontWeight: '600',
    fontStyle: 'italic',
    fontSize: 17,
  },
  sealedHint: {
    color: MUTED,
    fontSize: 12,
    letterSpacing: 0.3,
  },
  cardHit: {
    position: 'absolute',
  },
  buttons: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: BUTTON_HEIGHT,
    flexDirection: 'row',
    gap: 10,
  },
  buttonSlot: {
    flex: 1,
  },
  button: {
    flex: 1,
    borderRadius: BUTTON_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  buttonGhost: {
    backgroundColor: 'rgba(239, 230, 213, 0.07)',
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: 'rgba(239, 230, 213, 0.16)',
  },
  buttonPrimary: {
    backgroundColor: CREAM,
  },
  buttonLabel: {
    fontSize: 15,
    fontWeight: '600',
  },
});
