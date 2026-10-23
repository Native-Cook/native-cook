import { BlurStyle, Canvas, ClipOp, Group, makeImageFromView, Path, Picture, Skia, type SkImage, type SkPicture } from '@shopify/react-native-skia';
import * as Clipboard from 'expo-clipboard';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, Keyboard, type LayoutChangeEvent, StyleSheet, Text, TextInput, useWindowDimensions, View, type TextStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, Ellipse, LinearGradient, Line, RadialGradient, Rect, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { makeMapPicture, makePinPaints, makeRoute } from './map';
import {
  chipPillAt,
  cityLineAt,
  clamp01,
  contactAt,
  COUNT_IN,
  COUNT_OUT,
  countAt,
  DETECT,
  dotAt,
  DROP,
  dropAt,
  FAN,
  fanAt,
  FILL,
  fillAt,
  FILLED_AT,
  FLY,
  flyAt,
  FOLD,
  foldAt,
  FOUND_AT,
  foundAt,
  highlightAt,
  HOVER,
  hoverAt,
  impactAt,
  labelAt,
  LABEL,
  lerp,
  LOOP,
  mapFaceAt,
  noteLineAt,
  NOTE_IN,
  panelAt,
  panelMatrix,
  panelsAt,
  passAt,
  PASTE_AT,
  placeholderAt,
  POP,
  popAt,
  quietAt,
  PRESS,
  pressAt,
  PRESSES,
  REDUCED_FADE,
  RESET_AT,
  REST_AGAIN,
  RESUME_AFTER,
  routeAt,
  scanAt,
  slotAt,
  START_AT,
  tipLineAt,
  wordAt,
} from './motion';
import {
  ASCENT,
  box,
  chip,
  colors,
  DESCENT,
  DIGIT_ADV,
  FONT,
  found,
  hairline,
  ICONS,
  input,
  motionColors as M,
  note,
  NOTE,
  NOTE_TEXT,
  pin,
  radius,
  REF_INSET,
  REF_PT,
  REF_W,
  SF_ASCENT,
  SF_DESCENT,
  SPOTS,
  tipUnderline,
  type as T,
  type Word,
} from './theme';

export { LOOP };

type Props = {
  /**
   * Plays paste → find places → map → reset every LOOP seconds (the note is painted, the text area is never focused);
   * a touch takes over and autoPlay resumes after RESUME_AFTER, unless the text area holds the user's own text.
   */
  autoPlay?: boolean;
};

const FONT_FILES = {
  [FONT.text]: require('./assets/Anybody-Text.ttf'),
  [FONT.extra]: require('./assets/Anybody-ExtraBold.ttf'),
};

const ICON_SRC = {
  doc: require('./assets/icon-doc.png'),
  chat: require('./assets/icon-chat.png'),
  notes: require('./assets/icon-notes.png'),
} as const;

/** The paper snapshots are taken this long after the fonts load (layout settled); the fold waits for them. */
const FACE_SNAPSHOT_MS = 600;
/** Clock modes: frozen, playing to a stop, looping. */
const IDLE = 0;
const PLAY = 1;
const AUTO = 2;

/** Lines of the note (0 = the city line) and when the scan line passes each one. */
const lineMid = (i: number) => note.firstBase + i * note.pitch - 0.3 * note.size * 3;
const PASSES = NOTE.map((_, i) => passAt((lineMid(i) - note.scanTop) / (note.scanBottom - note.scanTop)));
const CITY_PASS = PASSES[0];
/** Places = every line but the city line. */
const PLACE_PASSES = PASSES.slice(1);
const PLACES = PLACE_PASSES.length;
const LAST_IMPACT = impactAt(PLACES - 1);

/** The counter rolls to the note's length; one slot per digit, the left ones grow in. */
const COUNT_DIGITS = String(NOTE_TEXT.length).split('').map(Number);
const COUNT_CYCLES = COUNT_DIGITS.map((_, k) => k); // extra turns grow to the right: the leftmost digit none, the next one, the next two
const COUNT_SPAN = COUNT_IN.dur;

type Box = { x: number; y: number; w: number; h: number };
/** A chip's own layout: size, the 📍's centre x and the label's left x (pt, chip-local). */
type ChipSize = { w: number; h: number; ex: number; lx: number };

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** "1,234": the counter's digits while the user types. */
const grouped = (n: number) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');

/** Centres (ref px) of the three controls for screen readers; r = half the target. */
const A11Y_BUTTONS = [
  { label: 'Close', x: 1098, y: 224.5, r: 66 },
  { label: 'Paste', x: 603, y: 2031, r: 66 },
  { label: 'Start import', x: 603, y: 2431.5, r: 90 },
] as const;

/** Screen geometry helpers shared by the live text area and its frozen copies on the folding panels. */
type Geo = {
  u: number;
  k: number;
  width: number;
  height: number;
  vb: string;
  px: (v: number) => number;
  y: (v: number) => number;
  lineBox: (size: number, base: number, asc?: number, desc?: number) => { fs: number; lh: number; top: number; baseIn: number };
  line: (font: string | undefined, size: number, ls: number, color: string, base: number, left?: number, asc?: number, desc?: number) => TextStyle;
  words: (spec: { font: string; size: number; color: string }, base: number, ws: readonly Word[]) => ReactNode[];
};

export function PastePlacesImport({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [fontsReady] = useFonts(FONT_FILES);
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  /** The loop is a demo: it stops for a screen reader as for Reduce Motion (the controls still work). */
  const loops = autoPlay && !screenReader;
  /** The text area's own text (typed, or pasted from the clipboard); the scripted note never goes in here. */
  const [draft, setDraft] = useState('');

  const u = width / REF_W; // ref px → pt
  const k = width / REF_PT; // pt at 402 → pt
  const dy = insets.top - REF_INSET * u; // pin y → screen y
  const y = (v: number) => v * u + dy;
  const px = (v: number) => v * u;

  /** Top and line height of a one-line box whose baseline sits at `base` ref px. */
  const lineBox = (size: number, base: number, asc = ASCENT, desc = DESCENT) => {
    const fs = size * k;
    const lh = Math.round(fs * 1.4);
    return { fs, lh, top: y(base) - ((lh - (asc + desc) * fs) / 2 + asc * fs), baseIn: (lh - (asc + desc) * fs) / 2 + asc * fs };
  };

  /** Style of a one-line Text whose baseline sits at `base` ref px. `left` is the text origin; omit it to centre the line. */
  const line = (font: string | undefined, size: number, ls: number, color: string, base: number, left?: number, asc = ASCENT, desc = DESCENT): TextStyle => {
    const { fs, lh, top } = lineBox(size, base, asc, desc);
    return {
      position: 'absolute',
      top,
      height: lh,
      ...(left === undefined ? { left: 0, right: 0, textAlign: 'center' as const } : { left: px(left) }),
      fontFamily: font,
      fontSize: fs,
      lineHeight: lh,
      letterSpacing: ls * k,
      color,
      includeFontPadding: false,
    };
  };

  /** A line of separately placed words sharing one baseline. */
  const words = (spec: { font: string; size: number; color: string }, base: number, ws: readonly Word[]) =>
    ws.map((w) => (
      <Text key={`${w.t}-${w.x}`} style={line(spec.font, spec.size, w.ls, spec.color, base, w.x)}>
        {w.t}
      </Text>
    ));

  const vb = `0 ${-dy / u} ${REF_W} ${height / u}`;
  const G: Geo = { u, k, width, height, vb, px, y, lineBox, line, words };

  // ───────────── clock ─────────────
  /** `clock` runs every frame; `time` is what the styles read, published only when the frame can differ. */
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const mode = useSharedValue(autoPlay && !initialReduced ? AUTO : IDLE);
  const stopAt = useSharedValue(FILLED_AT);
  const speed = useSharedValue(1);
  const thenReset = useSharedValue(false);
  const byHand = useSharedValue(false);
  const last = useSharedValue(-1);
  const touchedAt = useSharedValue(-1e9);
  const holding = useSharedValue(false);
  const foreground = useSharedValue(AppState.currentState !== 'background');
  const tap = useSharedValue({ n: 0, x: 0, y: 0 });
  const seen = useSharedValue(0);
  const pastePress = useSharedValue(0);
  const startPress = useSharedValue(0);
  const closePress = useSharedValue(0);
  const fade = useSharedValue(1);
  /** Frozen clocks for the paper panels' copies of the text area: the note as it folds, and the empty area. */
  const frozenNote = useSharedValue<number>(FOLD.close[0]);
  const frozenRest = useSharedValue(0);

  // Measured once: where each place sits in the note and where its chip sits in the rows (pt, screen).
  const wordBoxes = useSharedValue<Box[]>([]);
  const words$ = useRef<(Box | undefined)[]>([]).current;

  const hasDraft = useSharedValue(false);
  const focused = useSharedValue(false);
  const draftFill = useSharedValue(0);
  /** Bumped when paste finds nothing on the clipboard: the frame callback then pastes the sample note. */
  const pasteCmd = useSharedValue(0);
  const pasteSeen = useSharedValue(0);
  /** The paper faces exist (or could not be made): until then the fold waits for them. */
  const facesReady = useSharedValue(false);

  useEffect(() => {
    let live = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((on) => live && setReduced(on))
      .catch(() => {});
    AccessibilityInfo.isScreenReaderEnabled()
      .then((on) => live && setScreenReader(on))
      .catch(() => {});
    const a = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    const b = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      live = false;
      a.remove();
      b.remove();
    };
  }, []);

  useEffect(() => {
    if (reduced || (!loops && mode.get() === AUTO)) {
      clock.set(0);
      time.set(0);
      mode.set(IDLE);
      holding.set(false);
    } else if (loops && !hasDraft.get() && !focused.get()) {
      mode.set(AUTO);
    }
  }, [reduced, loops, clock, time, mode, holding, hasDraft, focused]);

  useEffect(() => {
    hasDraft.set(draft.length > 0);
    const to = draft.length > 0 ? 1 : 0;
    draftFill.set(reduced ? to : withTiming(to, { duration: (FILL.in[1] - FILL.in[0]) * 1000, easing: Easing.bezier(0.77, 0, 0.175, 1) }));
  }, [draft, reduced, hasDraft, draftFill]);

  /**
   * The text area takes input only while the screen is at rest (no scripted note on it). Animated props, not React
   * state: re-rendering this screen twice a loop cost a dropped frame each time.
   */
  const inputProps = useAnimatedProps(() => {
    const t = time.get();
    const live = t < PRESSES.paste[0] || t >= REST_AGAIN;
    return { editable: live, pointerEvents: live ? ('auto' as const) : ('none' as const) };
  });
  const inputStyle = useAnimatedStyle(() => {
    const t = time.get();
    return { opacity: t < PRESSES.paste[0] || t >= REST_AGAIN ? 1 : 0 };
  });

  useEffect(() => {
    const s = AppState.addEventListener('change', (state) => {
      foreground.set(state === 'active');
      holding.set(false);
      pastePress.set(0);
      startPress.set(0);
      closePress.set(0);
      last.set(-1);
    });
    return () => s.remove();
  }, [foreground, holding, pastePress, startPress, closePress, last]);

  const lightHaptic = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const successHaptic = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };
  const dismissKeyboard = () => Keyboard.dismiss();
  /** Start import with the user's own text: the demo only knows the sample note's places, so it imports that. */
  const clearDraft = () => {
    Keyboard.dismiss();
    setDraft('');
  };
  /** Paste: the clipboard's text goes into the text area; an empty clipboard pastes the sample note (animated). */
  const pasteFromClipboard = useCallback(() => {
    Clipboard.hasStringAsync()
      .then((has) => (has ? Clipboard.getStringAsync() : ''))
      .then((text) => {
        if (text.trim()) {
          setDraft((d) => (d + text).slice(0, input.max));
          if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        } else {
          pasteCmd.set((n) => n + 1);
        }
      })
      .catch(() => pasteCmd.set((n) => n + 1));
  }, [pasteCmd]);

  /** Which control a point (pt) is on: 1 paste, 2 start import, 3 ×, 4 the text area, 0 none. */
  const hitAt = (x: number, yy: number) => {
    'worklet';
    const rx = x / u;
    const ry = (yy - dy) / u;
    if (rx >= 470 && rx <= 740 && ry >= 1960 && ry <= 2100) return 1;
    const b = box.button;
    if (rx >= b.x && rx <= b.x + b.w && ry >= b.y && ry <= b.y + b.h) return 2;
    if (Math.abs(rx - 1098) <= 75 && Math.abs(ry - 225) <= 75) return 3;
    const c = box.card;
    if (rx >= c.x && rx <= c.x + c.w && ry >= c.y && ry <= c.y + c.h) return 4;
    return 0;
  };

  /** At rest: nothing of the sample flow on screen (the text area is the user's). */
  const atRest = (t: number) => {
    'worklet';
    return t < PRESSES.paste[0] || t >= REST_AGAIN;
  };

  /** Which control may be pressed at loop time t (the same list decides press feedback and commits). */
  const enabled = (hit: number, t: number) => {
    'worklet';
    if (hit === 1) return t < PRESSES.paste[0] || t >= REST_AGAIN;
    if (hit === 2) return (t >= FILLED_AT - 0.3 && t < START_AT) || (hasDraft.get() && atRest(t));
    if (hit === 3) return t >= PASTE_AT && t < REST_AGAIN;
    return false;
  };

  useFrameCallback((info) => {
    if (!foreground.get()) return;
    const now = info.timestamp;
    const before = last.get();
    last.set(now);
    const dt = before < 0 || holding.get() ? 0 : (now - before) / 1000;
    let t = clock.get();

    const tp = tap.get();
    if (tp.n !== seen.get()) {
      seen.set(tp.n);
      touchedAt.set(now);
      const hit = hitAt(tp.x, tp.y);
      const ok = enabled(hit, t);
      if (focused.get() && hit !== 4 && hit !== 1) scheduleOnRN(dismissKeyboard);
      const own = hit === 2 && ok && hasDraft.get() && atRest(t);
      if (own) scheduleOnRN(clearDraft);
      if (ok && hit === 1) {
        // The clipboard decides: its text goes into the text area, or (empty) pasteCmd plays the sample.
        if (mode.get() === AUTO) mode.set(IDLE);
        scheduleOnRN(pasteFromClipboard);
      } else if (reduced) {
        if (ok) {
          const to = hit === 2 ? FOUND_AT : 0;
          fade.set(
            withTiming(0, { duration: REDUCED_FADE, reduceMotion: ReduceMotion.Never }, (finished) => {
              if (!finished) return;
              clock.set(to);
              time.set(to);
              fade.set(withTiming(1, { duration: REDUCED_FADE, reduceMotion: ReduceMotion.Never }));
            }),
          );
        }
      } else if (ok && hit === 2) {
        t = own ? START_AT : Math.max(t, START_AT);
        stopAt.set(FOUND_AT);
        speed.set(1);
        thenReset.set(false);
        byHand.set(true);
        mode.set(PLAY);
      } else if (ok && hit === 3) {
        scheduleOnRN(lightHaptic);
        byHand.set(false);
        if (t < START_AT) {
          // Only the note is there: play the paste backwards into the icons.
          stopAt.set(PASTE_AT);
          speed.set(-1.6);
          thenReset.set(false);
        } else if (t < FOUND_AT) {
          stopAt.set(FOUND_AT);
          speed.set(2.5);
          thenReset.set(true);
        } else {
          t = Math.max(t, RESET_AT);
          stopAt.set(LOOP);
          speed.set(1);
          thenReset.set(false);
        }
        mode.set(PLAY);
      } else if (mode.get() === AUTO) {
        // Stop the loop, but never mid-move: finish into the nearest state.
        thenReset.set(false);
        byHand.set(false);
        speed.set(1);
        if (t > PRESSES.paste[0] && t < FILLED_AT) {
          stopAt.set(FILLED_AT);
          mode.set(PLAY);
        } else if (t > PRESSES.start[0] && t < FOUND_AT) {
          stopAt.set(FOUND_AT);
          mode.set(PLAY);
        } else if (t > PRESSES.close[0]) {
          stopAt.set(LOOP);
          mode.set(PLAY);
        } else {
          mode.set(IDLE);
        }
      }
    }

    // Paste with an empty clipboard: the sample note is pasted, as in the loop.
    if (pasteCmd.get() !== pasteSeen.get()) {
      pasteSeen.set(pasteCmd.get());
      touchedAt.set(now);
      if (reduced) {
        fade.set(
          withTiming(0, { duration: REDUCED_FADE, reduceMotion: ReduceMotion.Never }, (finished) => {
            if (!finished) return;
            clock.set(FILLED_AT);
            time.set(FILLED_AT);
            fade.set(withTiming(1, { duration: REDUCED_FADE, reduceMotion: ReduceMotion.Never }));
          }),
        );
      } else {
        t = PASTE_AT;
        stopAt.set(FILLED_AT);
        speed.set(1);
        thenReset.set(false);
        byHand.set(false);
        mode.set(PLAY);
        scheduleOnRN(lightHaptic);
      }
    }

    const m = mode.get();
    const before0 = t;
    if (!reduced && m === AUTO) {
      t = (t + dt) % LOOP;
    } else if (!reduced && m === PLAY) {
      const prev = t;
      const sp = speed.get();
      t += dt * sp;
      if (byHand.get() && prev < LAST_IMPACT && t >= LAST_IMPACT) scheduleOnRN(successHaptic);
      const stop = stopAt.get();
      if ((sp > 0 && t >= stop) || (sp < 0 && t <= stop)) {
        t = stop;
        if (thenReset.get()) {
          thenReset.set(false);
          t = RESET_AT;
          stopAt.set(LOOP);
          speed.set(1);
        } else {
          if (t >= LOOP || (sp < 0 && t <= PASTE_AT)) t = 0;
          mode.set(IDLE);
        }
      }
    } else if (!reduced && !holding.get() && m === IDLE && loops && !hasDraft.get() && !focused.get() && now - touchedAt.get() >= RESUME_AFTER) {
      mode.set(AUTO);
    }
    // The fold folds the snapshots of the text area: it waits at its first frame until they exist.
    if (!facesReady.get() && before0 < FOLD.close[0] && t >= FOLD.close[0]) t = before0;
    // Publish only when something can move: inside one quiet stretch the frame is identical.
    if (reduced) return;
    clock.set(t);
    const was = time.get();
    if (t !== was && (quietAt(t) < 0 || quietAt(t) !== quietAt(was))) time.set(t);
  });

  const press = (sv: SharedValue<number>, to: number) => {
    'worklet';
    sv.set(withTiming(to, { duration: to ? PRESS.inMs : PRESS.outMs, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  };

  const gesture = Gesture.Tap()
    .maxDuration(800)
    .onBegin((e) => {
      holding.set(true);
      if (mode.get() === AUTO) mode.set(IDLE);
      touchedAt.set(last.get());
      const hit = hitAt(e.x, e.y);
      if (reduced || !enabled(hit, clock.get())) return;
      press(hit === 1 ? pastePress : hit === 2 ? startPress : closePress, 1);
    })
    .onEnd((e, success) => {
      if (!success) return;
      tap.set({ n: tap.get().n + 1, x: e.x, y: e.y });
    })
    .onFinalize(() => {
      holding.set(false);
      touchedAt.set(last.get());
      press(pastePress, 0);
      press(startPress, 0);
      press(closePress, 0);
    });

  /** Scripted presses count only while the loop plays by itself. */
  const pressed = (sv: SharedValue<number>, at: readonly [number, number]) => {
    'worklet';
    return Math.max(sv.get(), mode.get() === AUTO ? pressAt(time.get(), at) : 0);
  };

  // ───────────── geometry measured once ─────────────
  const publish = () => {
    if (Array.from({ length: PLACES + 1 }, (_, i) => words$[i]).every((v) => v !== undefined)) wordBoxes.set([...words$] as Box[]);
  };
  const onWord = (i: number, lineTop: number) => (e: LayoutChangeEvent) => {
    const l = e.nativeEvent.layout;
    words$[i] = { x: px(note.left) + l.x, y: lineTop + l.y, w: l.width, h: l.height };
    publish();
  };

  // ───────────── static layout values ─────────────
  const stops = colors.pageStops;
  const gb = colors.glowBand;
  const gc = colors.glowCorner;
  const H = height / u - dy / u; // bottom of the page in ref px
  const c = box.card;
  const b = box.button;
  const cardX = px(c.x);
  const cardY = y(c.y);
  const cardW = px(c.w);
  const cardH = px(c.h);
  const spots = SPOTS.map(([sx, sy]) => ({ x: cardX + px(sx), y: cardY + px(sy) }));

  const counter = lineBox(T.counter.size, T.counter.base);
  const cw = T.counter.words[0];
  const counterLs = cw.ls * k;
  const colW = DIGIT_ADV.text * counter.fs + counterLs; // a roller column
  const zeroW = (1139 / 2000) * counter.fs + counterLs; // the "0" of the resting counter
  const startLine = lineBox(T.start.size, T.start.base);
  const inputLine = lineBox(note.size, note.firstBase);
  const labelColW = DIGIT_ADV.extra * startLine.fs + T.start.ls * k;

  // ───────────── Skia: the map (recorded once) and the pins' paints ─────────────
  const mapPic = useMemo(() => makeMapPicture(u, FOLD.panels), [u]);
  const route = useMemo(() => makeRoute(u), [u]);
  const paints = useMemo(() => makePinPaints(k, pin), [k]);

  /**
   * The text area as paper: two snapshots of it (the note as it folds, the empty area for the reset), taken once
   * from frozen copies that sit under the live one, so the folding panels show exactly the pixels of the screen.
   */
  const noteSrc = useRef<View>(null);
  const restSrc = useRef<View>(null);
  const faces = useSharedValue<{ note: SkImage | null; rest: SkImage | null }>({ note: null, rest: null });
  useEffect(() => {
    if (!fontsReady) return;
    let live = true;
    const id = setTimeout(() => {
      Promise.all([makeImageFromView(noteSrc), makeImageFromView(restSrc)])
        .then(([note, rest]) => {
          if (live) faces.set({ note, rest });
        })
        .catch(() => {})
        // Made or not, the fold may run now: without a snapshot it folds plain paper (never nothing).
        .finally(() => live && facesReady.set(true));
    }, FACE_SNAPSHOT_MS);
    return () => {
      live = false;
      clearTimeout(id);
    };
  }, [fontsReady, width, height, faces, facesReady]);

  const MG = FOLD.margin * k;
  const FW = cardW + 2 * MG;
  const FH = cardH + 2 * MG;
  const hp = cardH / FOLD.panels;
  const P = FOLD.perspective * k;
  const cardR = px(radius.card);
  const cardRRect = useMemo(() => Skia.RRectXY(Skia.XYWHRect(0, 0, cardW, cardH), cardR, cardR), [cardW, cardH, cardR]);
  const foldPaints = useMemo(() => {
    const shade = Skia.Paint();
    shade.setAntiAlias(true);
    shade.setColor(Skia.Color(M.shade));
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    shadow.setColor(Skia.Color(M.shade));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, FOLD.shadow.blur * k, true));
    const image = Skia.Paint();
    image.setAntiAlias(true);
    const blank = Skia.Paint();
    blank.setAntiAlias(true);
    blank.setColor(Skia.Color(colors.white));
    return { shade, shadow, image, blank };
  }, [k]);

  /**
   * The folding paper (four panels, each drawn through its own projective matrix, lit by its angle, over the
   * paper's shadow on the page), then the pins' contact shadows on the map; the route and the dots are drawn above
   * it (below). Recorded only when the clock publishes.
   */
  const paper = useDerivedValue<SkPicture>(() => {
    const t = time.get();
    const rec = Skia.PictureRecorder();
    const cv = rec.beginRecording(Skia.XYWHRect(0, 0, FW, FH));
    if (panelsAt(t)) {
      const deg = foldAt(t);
      const f = faces.get();
      const onMap = mapFaceAt(t);
      const img = t < 6 ? f.note : f.rest;
      const drawFace = () => {
        'worklet';
        if (onMap) cv.drawPicture(mapPic);
        else if (img) cv.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(0, 0, cardW, cardH), foldPaints.image);
        else cv.drawRRect(cardRRect, foldPaints.blank);
      };
      if (deg === 0) {
        cv.save();
        cv.translate(MG, MG);
        drawFace();
        cv.restore();
      } else {
        const th = (Math.abs(deg) * Math.PI) / 180;
        // The paper's shadow on the page, under the folded stack.
        const stack = FOLD.panels * hp * Math.cos(th);
        foldPaints.shadow.setAlphaf(FOLD.shadow.opacity * Math.sin(th));
        cv.drawRRect(Skia.RRectXY(Skia.XYWHRect(MG + 6 * k, MG + FOLD.shadow.y * k, cardW - 12 * k, Math.max(stack, 4 * k)), 12 * k, 12 * k), foldPaints.shadow);
        for (let i = 0; i < FOLD.panels; i++) {
          const p = panelAt(i, deg, hp, P);
          const cy = MG + p.ty + (i + 0.5) * hp;
          cv.save();
          cv.concat(Skia.Matrix(panelMatrix(i, deg, MG + cardW / 2, cy, cardW, P)));
          cv.translate(0, -(i + 0.5) * hp);
          cv.clipRect(Skia.XYWHRect(0, i * hp, cardW, hp), ClipOp.Intersect, true);
          drawFace();
          if (p.shade > 0.002) {
            foldPaints.shade.setAlphaf(p.shade);
            cv.drawRRect(cardRRect, foldPaints.shade);
          }
          cv.restore();
        }
      }
      if (onMap && Math.abs(deg) < 3) {
        cv.save();
        cv.translate(MG, MG);
        for (let i = 0; i < SPOTS.length; i++) {
          const a = contactAt(t, i);
          if (a <= 0) continue;
          const h = dropAt(t, i).h;
          const sx = SPOTS[i][0] * u;
          const sy = SPOTS[i][1] * u;
          const rx = pin.shadowRx * k * (1 + 0.5 * h);
          const ry = pin.shadowRy * k * (1 + 0.5 * h);
          paints.shadow.setAlphaf(a);
          cv.drawOval(Skia.XYWHRect(sx - rx, sy - ry + pin.dotR * 0.4 * k, 2 * rx, 2 * ry), paints.shadow);
        }
        cv.restore();
      }
    }
    return rec.finishRecordingAsPicture();
  });

  /** On the map: the route (a trimmed Path, no per-frame path) between the shadows above and the dots. */
  const onFlatMap = (t: number) => {
    'worklet';
    return panelsAt(t) && mapFaceAt(t) && Math.abs(foldAt(t)) < 3;
  };
  const routeEnd = useDerivedValue(() => routeAt(time.get()));
  const routeOpacity = useDerivedValue(() => (onFlatMap(time.get()) && routeAt(time.get()) > 0.001 ? 1 : 0));
  const dots = useDerivedValue<SkPicture>(() => {
    const t = time.get();
    const rec = Skia.PictureRecorder();
    const cv = rec.beginRecording(Skia.XYWHRect(0, 0, FW, FH));
    if (onFlatMap(t)) {
      cv.save();
      cv.translate(MG, MG);
      for (let i = 0; i < SPOTS.length; i++) {
        const d = dotAt(t, i);
        if (d <= 0) continue;
        const sx = SPOTS[i][0] * u;
        const sy = SPOTS[i][1] * u;
        const g = 0.6 + 0.4 * d;
        paints.ring.setAlphaf(d);
        paints.dot.setAlphaf(d);
        cv.drawCircle(sx, sy, pin.dotR * k * g, paints.ring);
        cv.drawCircle(sx, sy, pin.coreR * k * g, paints.dot);
      }
      cv.restore();
    }
    return rec.finishRecordingAsPicture();
  });

  // ───────────── animated styles ─────────────
  const rootStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));

  const notesStyle = useIconStyle(time, 'notes', k);
  const chatStyle = useIconStyle(time, 'chat', k);
  const docStyle = useIconStyle(time, 'doc', k);

  const tipLineStyle = useAnimatedStyle(() => {
    const p = tipLineAt(time.get(), CITY_PASS);
    return { opacity: p > 0 ? 1 : 0, transform: [{ scaleX: Math.max(p, 0.001) }] };
  });

  /**
   * While the paper panels are up, the live text area is covered by a patch of the page (same gradient) rather
   * than hidden: hiding that full-screen layer (opacity 0 or moved away) made iOS stop drawing the headline,
   * hint and icons above it.
   */
  const patchStyle = useAnimatedStyle(() => ({ opacity: panelsAt(time.get()) ? 1 : 0 }));

  const pasteStyle = useAnimatedStyle(() => ({ transform: [{ scale: lerp(1, PRESS.scale, pressed(pastePress, PRESSES.paste)) }] }));
  const startStyle = useAnimatedStyle(() => ({ transform: [{ scale: lerp(1, PRESS.scale, pressed(startPress, PRESSES.start)) }] }));
  const closeStyle = useAnimatedStyle(() => {
    const p = pressed(closePress, PRESSES.close);
    return { opacity: lerp(1, PRESS.closeDim, p), transform: [{ scale: lerp(1, PRESS.closeScale, p) }] };
  });

  const buttonW = px(b.w);
  const fillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: (Math.max(fillAt(time.get()), draftFill.get()) - 1) * buttonW }] }));
  const startLabelStyle = useAnimatedStyle(() => {
    const l = labelAt(time.get())[0];
    return { opacity: 1 - l, transform: [{ translateY: -LABEL.shift * k * l }] };
  });
  const foundLabelStyle = useAnimatedStyle(() => {
    const l = labelAt(time.get())[1];
    return { opacity: l, transform: [{ translateY: LABEL.shift * k * (1 - l) }] };
  });
  const foundDigitStyle = useAnimatedStyle(() => {
    const n = Math.max(1, foundAt(time.get(), PLACES));
    return { transform: [{ translateY: -(n - 1) * startLine.lh }] };
  });
  const pluralStyle = useAnimatedStyle(() => ({ opacity: clamp01(foundAt(time.get(), PLACES) - 1) }));

  // Counter: the resting word, and the roller that replaces it while it rolls.
  const countRolling = (t: number) => {
    'worklet';
    return countAt(t) > 0;
  };
  const countSpan = (t: number) => {
    'worklet';
    return t >= COUNT_OUT.start ? COUNT_OUT.dur : COUNT_SPAN;
  };
  const countStagger = (t: number) => {
    'worklet';
    return t >= COUNT_OUT.start ? COUNT_OUT.stagger : COUNT_IN.stagger;
  };
  const countShift = (t: number) => {
    'worklet';
    const m = countAt(t);
    let s = 0;
    for (let i = 0; i < COUNT_DIGITS.length - 1; i++) s += clamp01(slotAt(m, i, COUNT_DIGITS.length, countSpan(t), countStagger(t)) * 3) * colW;
    return s;
  };
  const restCountStyle = useAnimatedStyle(() => ({ opacity: countRolling(time.get()) ? 0 : 1 }));
  const rollerStyle = useAnimatedStyle(() => ({ opacity: countRolling(time.get()) ? 1 : 0 }));
  const restOfCountStyle = useAnimatedStyle(() => ({ transform: [{ translateX: countShift(time.get()) }] }));

  const page = (
    <Svg width={width} height={height} viewBox={vb} preserveAspectRatio="none" style={StyleSheet.absoluteFill}>
      <Defs>
        <LinearGradient id="page" x1="0" y1="0" x2="0" y2={H} gradientUnits="userSpaceOnUse">
          {stops.map(([at, col]) => (
            <Stop key={at} offset={Math.min(1, at / H)} stopColor={col} />
          ))}
        </LinearGradient>
        <RadialGradient id="band" cx={gb.cx} cy={gb.cy} rx={gb.rx} ry={gb.ry} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={gb.color} stopOpacity={gb.opacity} />
          <Stop offset="1" stopColor={gb.color} stopOpacity={0} />
        </RadialGradient>
        <RadialGradient id="corner" cx={gc.cx} cy={gc.cy} rx={gc.rx} ry={gc.ry} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={gc.color} stopOpacity={gc.opacity} />
          <Stop offset="1" stopColor={gc.color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Rect x={0} y={-dy / u} width={REF_W} height={height / u} fill="url(#page)" />
      <Ellipse cx={gb.cx} cy={gb.cy} rx={gb.rx} ry={gb.ry} fill="url(#band)" />
      <Ellipse cx={gc.cx} cy={gc.cy} rx={gc.rx} ry={gc.ry} fill="url(#corner)" />
    </Svg>
  );

  const icon = (key: 'notes' | 'chat' | 'doc', style: object) => {
    const ic = ICONS[key];
    return (
      <Animated.View key={key} style={[styles.abs, { left: px(ic.x), top: y(ic.y), width: px(ic.w), height: px(ic.h) }, style]}>
        <Image source={ICON_SRC[key]} contentFit="fill" style={StyleSheet.absoluteFill} />
      </Animated.View>
    );
  };

  /** A full-screen layer that scales about (cx, cy) ref px, so its children keep their screen positions. */
  const origin = (cx: number, cy: number) => ({ transformOrigin: [px(cx), y(cy), 0] as [number, number, number] });

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[styles.screen, rootStyle]} collapsable={false}>
        {page}

        {/* Snapshot sources for the paper: frozen copies of the text area, hidden under the live one. */}
        {(
          [
            [noteSrc, frozenNote],
            [restSrc, frozenRest],
          ] as const
        ).map(([ref, frozen], i) => (
          <View key={i} ref={ref} collapsable={false} style={[styles.abs, { left: cardX, top: cardY, width: cardW, height: cardH, overflow: 'hidden' }]} pointerEvents="none">
            <View style={[styles.abs, { left: -cardX, top: -cardY, width, height }]}>
              <CardFace G={G} time={frozen} fontsReady={fontsReady} />
            </View>
          </View>
        ))}

        {/* The live text area (the pin's), with the note, the scan and paste. */}
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <CardFace G={G} time={time} live fontsReady={fontsReady} pasteStyle={pasteStyle} onWord={onWord} hasDraft={hasDraft} />
        </View>
        <Animated.View style={[styles.abs, { left: cardX - 1, top: cardY - 1, width: cardW + 2, height: cardH + 2, overflow: 'hidden' }, patchStyle]} pointerEvents="none">
          <View style={[styles.abs, { left: -cardX + 1, top: -cardY + 1, width, height }]}>{page}</View>
        </Animated.View>

        {/* The paper: folds shut, opens as the map; route and pins' dots on it. */}
        <View style={[styles.abs, { left: cardX - MG, top: cardY - MG, width: FW, height: FH }]} pointerEvents="none">
          <Canvas style={{ width: FW, height: FH }}>
            <Picture picture={paper} />
            <Group transform={[{ translateX: MG }, { translateY: MG }]} opacity={routeOpacity}>
              <Path path={route} paint={paints.routeCasing} start={0} end={routeEnd} />
              <Path path={route} paint={paints.route} start={0} end={routeEnd} />
            </Group>
            <Picture picture={dots} />
          </Canvas>
        </View>

        {/* The text area itself: the user's own text, typed or pasted; at rest only (the sample flow paints its note). */}
        {fontsReady && (
          <AnimatedTextInput
            animatedProps={inputProps}
            value={draft}
            onChangeText={setDraft}
            onFocus={() => {
              focused.set(true);
              if (mode.get() === AUTO) mode.set(IDLE);
            }}
            onBlur={() => {
              focused.set(false);
              touchedAt.set(last.get());
            }}
            multiline
            maxLength={input.max}
            autoCorrect={false}
            selectionColor={M.detect}
            accessibilityLabel={input.label}
            accessibilityHint="Type a note, or use paste"
            style={[
              styles.abs,
              styles.input,
              {
                left: px(input.left),
                top: inputLine.top,
                width: px(input.right - input.left),
                height: y(input.bottom) - inputLine.top,
                fontFamily: note.font,
                fontSize: inputLine.fs,
                lineHeight: inputLine.lh,
                color: M.noteInk,
              },
              inputStyle,
            ]}
          />
        )}

        {/* The three app icons (back to front, each its own layer so they can fan apart), headline, hint and tip. Drawn after the paper panels: drawn before them, iOS left them out while the panels were up. */}
        {icon('doc', docStyle)}
        {icon('chat', chatStyle)}
        {icon('notes', notesStyle)}

        {fontsReady && (
          <>
            {T.h1.lines.map((l) => words(T.h1, l.base, l.words))}
            <Text style={[styles.abs, { left: px(T.pin.left), top: y(T.pin.top), fontSize: T.pin.size * k }]}>{T.pin.text}</Text>
            {T.hint.lines.map((l) => words(T.hint, l.base, l.words))}
            {words(T.tip, T.tip.base, T.tip.words)}
            <View style={[styles.abs, { left: px(T.tipDash.x), top: y(T.tipDash.y), width: px(T.tipDash.w), height: px(T.tipDash.h), backgroundColor: T.tip.color }]} />
            <Animated.View
              style={[
                styles.abs,
                { left: px(tipUnderline.x), top: y(tipUnderline.y), width: px(tipUnderline.w), height: px(tipUnderline.h), borderRadius: px(tipUnderline.h), backgroundColor: M.detect, transformOrigin: 'left' },
                tipLineStyle,
              ]}
            />
          </>
        )}

        {fontsReady && draft.length > 0 && (
          <Text style={line(T.counter.font, T.counter.size, cw.ls, T.counter.color, T.counter.base, cw.x)} accessibilityLabel={`${draft.length} of ${grouped(input.max)} characters`}>
            {`${grouped(draft.length)}/${grouped(input.max)} characters`}
          </Text>
        )}
        {fontsReady && draft.length === 0 && (
          <>
            {/* counter */}
            <Animated.View style={[StyleSheet.absoluteFill, restCountStyle]} pointerEvents="none">
              {words(T.counter, T.counter.base, [cw])}
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, rollerStyle]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
              {COUNT_DIGITS.map((d, slot) => (
                <CountSlot key={slot} time={time} slot={slot} target={d} cycles={COUNT_CYCLES[slot]} colW={colW} x0={px(cw.x)} top={counter.top} lh={counter.lh} fs={counter.fs} ls={counterLs} />
              ))}
              <Animated.Text style={[line(T.counter.font, T.counter.size, cw.ls, T.counter.color, T.counter.base, cw.x + zeroW / u), restOfCountStyle]}>{cw.t.slice(1)}</Animated.Text>
            </Animated.View>
            {words(T.counter, T.counter.base, T.counter.words.slice(1)).map((el, i) => (
              <Animated.View key={i} style={[StyleSheet.absoluteFill, restOfCountStyle]} pointerEvents="none">
                {el}
              </Animated.View>
            ))}
          </>
        )}

        {/* Start button: translucent grey; fills left to right with solid ink once there is text. */}
        <Animated.View style={[StyleSheet.absoluteFill, origin(b.x + b.w / 2, b.y + b.h / 2), startStyle]} pointerEvents="none">
          <View style={[styles.abs, { left: px(b.x), top: y(b.y), width: px(b.w), height: px(b.h), borderRadius: px(radius.button), backgroundColor: colors.buttonFill, overflow: 'hidden' }]}>
            <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: M.buttonSolid }, fillStyle]} />
          </View>
          {fontsReady && (
            <>
              <Animated.Text style={[line(T.start.font, T.start.size, T.start.ls, T.start.color, T.start.base), startLabelStyle]}>{T.start.text}</Animated.Text>
              <Animated.View style={[styles.abs, styles.row, { left: 0, right: 0, top: startLine.top, height: startLine.lh, justifyContent: 'center' }, foundLabelStyle]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
                <Text style={[styles.startText, { fontFamily: T.start.font, fontSize: startLine.fs, lineHeight: startLine.lh, letterSpacing: T.start.ls * k }]}>{found.pre}</Text>
                <View style={{ width: labelColW, height: startLine.lh, overflow: 'hidden' }}>
                  <Animated.View style={foundDigitStyle}>
                    {Array.from({ length: PLACES }, (_, i) => (
                      <Text key={i} style={[styles.startText, { fontFamily: T.start.font, fontSize: startLine.fs, lineHeight: startLine.lh, height: startLine.lh, letterSpacing: T.start.ls * k, textAlign: 'center' }]}>
                        {i + 1}
                      </Text>
                    ))}
                  </Animated.View>
                </View>
                <Text style={[styles.startText, { fontFamily: T.start.font, fontSize: startLine.fs, lineHeight: startLine.lh, letterSpacing: T.start.ls * k }]}>{found.post}</Text>
                <Animated.Text style={[styles.startText, { fontFamily: T.start.font, fontSize: startLine.fs, lineHeight: startLine.lh, letterSpacing: T.start.ls * k }, pluralStyle]}>{found.plural}</Animated.Text>
              </Animated.View>
            </>
          )}
        </Animated.View>

        {/* Screen-reader buttons: activating one sends the same tap a finger would. */}
        {A11Y_BUTTONS.map((a) => (
          <View
            key={a.label}
            accessible
            accessibilityRole="button"
            accessibilityLabel={a.label}
            onAccessibilityTap={() => tap.set({ n: tap.get().n + 1, x: px(a.x), y: y(a.y) })}
            style={[styles.abs, { left: px(a.x - a.r), top: y(a.y - a.r), width: px(2 * a.r), height: px(2 * a.r) }]}
            pointerEvents="none"
          />
        ))}

        {/* × */}
        <Animated.View style={[StyleSheet.absoluteFill, origin(1098, 224.5), closeStyle]} pointerEvents="none">
          <Svg width={width} height={height} viewBox={vb} preserveAspectRatio="none" style={StyleSheet.absoluteFill}>
            <Line x1={box.close.x1} y1={box.close.y1} x2={box.close.x2} y2={box.close.y2} stroke={colors.ink} strokeWidth={box.close.stroke} strokeLinecap="round" />
            <Line x1={box.close.x2} y1={box.close.y1} x2={box.close.x1} y2={box.close.y2} stroke={colors.ink} strokeWidth={box.close.stroke} strokeLinecap="round" />
          </Svg>
        </Animated.View>

        {/* Chips: lift off their words, hover while the paper folds, fly to the map and drop as pins. */}
        {fontsReady && (
          <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            {NOTE.slice(1).map((l, i) => (
              <Chip key={l.place} index={i} text={l.place} time={time} pass={PLACE_PASSES[i]} wordBoxes={wordBoxes} spot={spots[i]} k={k} px={px} />
            ))}
          </View>
        )}
      </Animated.View>
    </GestureDetector>
  );
}

/** An app icon fans out when the note is pasted (rotation about its own centre). */
function useIconStyle(time: SharedValue<number>, key: 'notes' | 'chat' | 'doc', k: number) {
  return useAnimatedStyle(() => {
    const f = fanAt(time.get());
    const a = FAN[key];
    const s = 'scale' in a ? lerp(1, a.scale, f) : 1;
    return { transform: [{ translateX: a.x * k * f }, { translateY: a.y * k * f }, { rotate: `${a.rot * f}deg` }, { scale: s }] };
  });
}

/**
 * The text area's content in screen coordinates: paper, placeholder, the note and paste. `live` adds the scan
 * line and the measured words; a frozen copy (time that never changes) runs its styles once and never again.
 */
function CardFace({
  G,
  time,
  live = false,
  fontsReady,
  pasteStyle,
  onWord,
  hasDraft,
}: {
  G: Geo;
  time: SharedValue<number>;
  live?: boolean;
  fontsReady: boolean;
  pasteStyle?: object;
  onWord?: (i: number, lineTop: number) => (e: LayoutChangeEvent) => void;
  /** The live area: the painted placeholder gives way to the user's own text. */
  hasDraft?: SharedValue<boolean>;
}) {
  const { px, y, u, vb, width, height, line } = G;
  const c = box.card;
  const cl = box.clip;
  const placeholderStyle = useAnimatedStyle(() => ({ opacity: hasDraft?.get() ? 0 : placeholderAt(time.get()) }));
  const scanStyle = useAnimatedStyle(() => {
    const s = scanAt(time.get());
    return { opacity: s.o, transform: [{ translateY: lerp(note.scanTop, note.scanBottom, s.p) * u }] };
  });
  const noteLines = NOTE.map((_, i) => G.lineBox(note.size, note.firstBase + i * note.pitch));

  return (
    <>
      <View style={[styles.abs, styles.card, { left: px(c.x), top: y(c.y), width: px(c.w), height: px(c.h), borderRadius: px(radius.card), borderWidth: px(hairline) }]} />
      {live && (
        <View style={[styles.abs, { left: px(c.x), top: y(c.y), width: px(c.w), height: px(c.h), borderRadius: px(radius.card), overflow: 'hidden' }]}>
          <Animated.View style={[styles.abs, { left: 0, right: 0, top: -px(c.y) - px(note.scanTrail), height: px(note.scanTrail + note.scanStroke) }, scanStyle]}>
            <Svg width="100%" height="100%">
              <Defs>
                <LinearGradient id="trail" x1="0" y1="0" x2="0" y2="1">
                  <Stop offset="0" stopColor={M.detect} stopOpacity={0} />
                  <Stop offset="1" stopColor={M.detect} stopOpacity={0.12} />
                </LinearGradient>
              </Defs>
              <Rect x="0" y="0" width="100%" height="100%" fill="url(#trail)" />
            </Svg>
            <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: px(note.scanStroke), backgroundColor: M.detect, opacity: 0.7 }} />
          </Animated.View>
        </View>
      )}
      {fontsReady && (
        <>
          <Animated.View style={[StyleSheet.absoluteFill, placeholderStyle]}>{G.words(T.placeholder, T.placeholder.base, T.placeholder.words)}</Animated.View>
          {NOTE.map((l, i) => (
            <NoteLine
              key={l.place}
              index={i}
              time={time}
              box={noteLines[i]}
              left={px(note.left)}
              text={l}
              pass={PASSES[i]}
              markerOver={px(note.marker.over)}
              markerRadius={px(note.marker.radius)}
              underline={{ gap: px(note.underline.gap), stroke: px(note.underline.stroke) }}
              onWord={live && onWord ? onWord(i, noteLines[i].top) : undefined}
            />
          ))}
          <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [px(603), y(2031), 0] }, pasteStyle]}>
            <Text style={[line(undefined, T.paste.size, T.paste.ls, T.paste.color, T.paste.base, T.paste.left, SF_ASCENT, SF_DESCENT), { fontWeight: T.paste.weight }]}>{T.paste.text}</Text>
            <Svg width={width} height={height} viewBox={vb} preserveAspectRatio="none" style={StyleSheet.absoluteFill}>
              <Rect x={cl.x + 1.9} y={cl.y + 5.9} width={cl.w - 3.8} height={cl.h - 5.9 - 1.9} rx={5} fill="none" stroke={colors.glyph} strokeWidth={3.8} />
              <Rect x={cl.x + 8.9} y={cl.y + 1.9} width={cl.w - 17.8} height={8.7} rx={3} fill={colors.white} stroke={colors.glyph} strokeWidth={3.8} />
            </Svg>
          </Animated.View>
        </>
      )}
    </>
  );
}

/** One line of the note: streams in, and its place (or city) is marked when the scan line passes. */
function NoteLine({
  index,
  time,
  box: lb,
  left,
  text,
  pass,
  markerOver,
  markerRadius,
  underline,
  onWord,
}: {
  index: number;
  time: SharedValue<number>;
  box: { fs: number; lh: number; top: number; baseIn: number };
  left: number;
  text: (typeof NOTE)[number];
  pass: number;
  markerOver: number;
  markerRadius: number;
  underline: { gap: number; stroke: number };
  onWord?: (e: LayoutChangeEvent) => void;
}) {
  const lineStyle = useAnimatedStyle(() => {
    const p = noteLineAt(time.get(), index);
    return { opacity: p, transform: [{ translateY: (1 - p) * NOTE_IN.rise }] };
  });
  const markStyle = useAnimatedStyle(() => {
    const t = time.get();
    const p = text.city ? cityLineAt(t, pass) : highlightAt(t, pass);
    return { opacity: p > 0 ? 1 : 0, transform: [{ scaleX: Math.max(p, 0.001) }] };
  });
  const wordStyle = useAnimatedStyle(() => ({ opacity: text.city ? 1 : wordAt(time.get(), pass) }));

  const font: TextStyle = { fontFamily: note.font, fontSize: lb.fs, lineHeight: lb.lh, color: M.noteInk, includeFontPadding: false };
  const mark = text.city
    ? { left: 0, right: 0, top: lb.baseIn + underline.gap, height: underline.stroke, borderRadius: underline.stroke, backgroundColor: M.detect }
    : {
        left: -markerOver,
        right: -markerOver,
        top: lb.lh * note.marker.top,
        height: lb.lh * (note.marker.bottom - note.marker.top),
        borderRadius: markerRadius,
        backgroundColor: M.marker,
      };

  return (
    <Animated.View style={[styles.abs, styles.row, { left, top: lb.top, height: lb.lh }, lineStyle]} pointerEvents="none">
      {text.pre ? <Text style={font}>{text.pre}</Text> : null}
      <View onLayout={onWord}>
        <Animated.View style={[styles.abs, mark, { transformOrigin: 'left' }, markStyle]} />
        <Animated.Text style={[font, wordStyle]}>{text.place}</Animated.Text>
      </View>
      {text.post ? <Text style={font}>{text.post}</Text> : null}
    </Animated.View>
  );
}

/**
 * A place chip: starts as its word (label over the word, same size), hovers off the paper while it folds,
 * flies on an arc to its place on the map and drops onto it as a pin (gravity, bounces, squash). All transforms
 * are about the chip's centre, computed so the right point of the chip lands on the right point of the screen.
 */
function Chip({ index, text, time, pass, wordBoxes, spot, k, px }: { index: number; text: string; time: SharedValue<number>; pass: number; wordBoxes: SharedValue<Box[]>; spot: { x: number; y: number }; k: number; px: (v: number) => number }) {
  const s0 = note.size / chip.size;
  const MS = chip.mapScale;
  const size = useSharedValue<ChipSize | null>(null);
  const parts = useRef<{ w?: number; h?: number; ex?: number; lx?: number }>({}).current;
  const done = () => {
    if (parts.w !== undefined && parts.h !== undefined && parts.ex !== undefined && parts.lx !== undefined) size.set({ w: parts.w, h: parts.h, ex: parts.ex, lx: parts.lx });
  };

  const flyStyle = useAnimatedStyle(() => {
    const t = time.get();
    const ws = wordBoxes.get();
    const m = size.get();
    const lift = pass + DETECT.lift;
    const pop = popAt(t, index);
    if (ws.length === 0 || m === null || t < lift || pop >= 1) return { opacity: 0, transform: [{ translateX: -999 }, { translateY: 0 }, { rotate: '0deg' }, { scaleX: 1 }, { scaleY: 1 }] };
    const w = ws[index + 1];
    const cx = m.w / 2;
    const cy = m.h / 2;
    // Start: label's left / middle on the word's, at the word's size, hovering.
    const hov = hoverAt(t, pass);
    const sA = s0 * (1 + HOVER.grow * hov);
    const ax = w.x - sA * (m.lx - cx);
    const ay = w.y + w.h / 2 - HOVER.rise * k * hov;
    // End: the 📍's foot right above the dot, at map scale.
    const qy = spot.y - (pin.dotR + pin.gap) * k;
    const bx = spot.x - MS * (m.ex - cx);
    const by = qy - MS * (m.h - cy);
    const f = flyAt(t, index);
    const fc = clamp01(f);
    const arc = Math.sin(Math.PI * fc);
    const d = dropAt(t, index);
    const s = lerp(sA, MS, fc) * (1 + FLY.grow * arc) * (1 - POP.shrink * pop);
    const sx = s * (1 + DROP.squash.x * d.imp);
    const sy = s * (1 - DROP.squash.y * d.imp);
    const X = lerp(ax, bx, f);
    // The drop lowers the chip by its height above the map; the squash keeps its foot on the map.
    const Y = lerp(ay, by - DROP.height * k * d.h, f) - FLY.arc * k * arc + (s - sy) * cy - POP.rise * k * pop;
    return {
      opacity: 1 - pop,
      transform: [{ translateX: X - cx }, { translateY: Y - cy }, { rotate: `${(index % 2 ? 1 : -1) * FLY.tilt * arc}deg` }, { scaleX: sx }, { scaleY: sy }],
    };
  });
  const pillStyle = useAnimatedStyle(() => {
    const t = time.get();
    const fc = clamp01(flyAt(t, index));
    const air = Math.max(0.45 * hoverAt(t, pass) * (1 - fc), Math.sin(Math.PI * fc), 0.6 * dropAt(t, index).h * fc);
    return {
      opacity: chipPillAt(t, pass),
      shadowOpacity: 0.05 + 0.15 * air,
      shadowRadius: (2 + 9 * air) * k,
      shadowOffset: { width: 0, height: (1 + 9 * air) * k },
    };
  });
  const pinStyle = useAnimatedStyle(() => ({ opacity: chipPillAt(time.get(), pass) }));

  return (
    <Animated.View
      onLayout={(e) => {
        parts.w = e.nativeEvent.layout.width;
        parts.h = e.nativeEvent.layout.height;
        done();
      }}
      style={[styles.abs, styles.row, { left: 0, top: 0, height: px(chip.height), paddingLeft: px(chip.padLeft), paddingRight: px(chip.padRight), alignItems: 'center' }, flyStyle]}
    >
      <Animated.View style={[StyleSheet.absoluteFill, styles.chipPill, { borderRadius: px(chip.radius), borderWidth: px(hairline) }, pillStyle]} />
      <Animated.Text
        onLayout={(e) => {
          parts.ex = e.nativeEvent.layout.x + e.nativeEvent.layout.width / 2;
          done();
        }}
        style={[{ fontSize: chip.pinSize * k, marginRight: px(chip.pinGap) }, pinStyle]}
      >
        {T.pin.text}
      </Animated.Text>
      <Text
        onLayout={(e) => {
          parts.lx = e.nativeEvent.layout.x;
          done();
        }}
        style={{ fontFamily: chip.font, fontSize: chip.size * k, lineHeight: Math.round(chip.size * k * 1.4), color: colors.ink, includeFontPadding: false }}
      >
        {text}
      </Text>
    </Animated.View>
  );
}

/** One digit slot of the counter: a strip of digits that rolls; slots left of the ones grow in. */
function CountSlot({
  time,
  slot,
  target,
  cycles,
  colW,
  x0,
  top,
  lh,
  fs,
  ls,
}: {
  time: SharedValue<number>;
  slot: number;
  target: number;
  cycles: number;
  colW: number;
  x0: number;
  top: number;
  lh: number;
  fs: number;
  ls: number;
}) {
  const n = COUNT_DIGITS.length;
  const steps = target + 10 * cycles;
  const style = useAnimatedStyle(() => {
    const t = time.get();
    const out = t >= COUNT_OUT.start;
    const span = out ? COUNT_OUT.dur : COUNT_IN.dur;
    const stagger = out ? COUNT_OUT.stagger : COUNT_IN.stagger;
    const m = countAt(t);
    const p = slotAt(m, slot, n, span, stagger);
    // Left of this slot: the slots that grew in so far.
    let left = 0;
    for (let i = 0; i < slot; i++) left += clamp01(slotAt(m, i, n, span, stagger) * 3) * colW;
    const grow = slot === n - 1 ? 1 : clamp01(p * 3);
    return { opacity: grow, transform: [{ translateX: left }] };
  });
  const strip = useAnimatedStyle(() => {
    const t = time.get();
    const out = t >= COUNT_OUT.start;
    const p = slotAt(countAt(t), slot, n, out ? COUNT_OUT.dur : COUNT_IN.dur, out ? COUNT_OUT.stagger : COUNT_IN.stagger);
    return { transform: [{ translateY: -p * steps * lh }] };
  });
  return (
    <Animated.View style={[styles.abs, { left: x0 - (colW - ((1139 / 2000) * fs + ls)) / 2, top, width: colW, height: lh, overflow: 'hidden' }, style]}>
      <Animated.View style={strip}>
        {Array.from({ length: steps + 1 }, (_, i) => (
          <Text key={i} style={{ height: lh, fontFamily: T.counter.font, fontSize: fs, lineHeight: lh, letterSpacing: ls, color: T.counter.color, textAlign: 'center', includeFontPadding: false }}>
            {i % 10}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EDEDED' },
  abs: { position: 'absolute' },
  row: { flexDirection: 'row' },
  card: { backgroundColor: colors.white, borderColor: colors.cardBorder },
  chipPill: { backgroundColor: M.chipFill, borderColor: M.chipBorder, shadowColor: M.chipShadow },
  startText: { color: colors.white, includeFontPadding: false },
  input: { padding: 0, paddingTop: 0, paddingBottom: 0, paddingLeft: 0, paddingRight: 0, margin: 0, textAlignVertical: 'top', includeFontPadding: false, backgroundColor: 'transparent' },
});
