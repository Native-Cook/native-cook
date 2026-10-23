import {
  Atlas,
  Canvas,
  Circle,
  FilterMode,
  Group,
  LinearGradient,
  matchFont,
  MipmapMode,
  Paint,
  RadialGradient,
  Rect,
  Shadow,
  Skia,
  useRSXformBuffer,
  useTexture,
  vec,
  type SkRect,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The words the particles spell, in order. Short words (up to six letters) read best. */
  words?: string[];
  /** Two colours per word, left to right across it; the particles shift to them as it forms. */
  palettes?: [string, string][];
  /** Cycles the words on a loop, with one pass of a finger through the third word. */
  autoPlay?: boolean;
  /** Called with a word's index when it starts to form after a tap. */
  onWord?: (index: number) => void;
};

const WORDS = ['SPARK', 'DRIFT', 'BLOOM', 'ORBIT', 'GLOW'];
const PALETTES: [string, string][] = [
  ['#ffb36b', '#ff5f7e'],
  ['#6be4ff', '#4f7dff'],
  ['#ff8ad8', '#a77bff'],
  ['#c9b8ff', '#6fa8ff'],
  ['#9dffcb', '#e8ff7a'],
];

// The night behind the words: deep navy, lifted in the middle and darker at the corners.
const NIGHT = '#060a1c';
const NIGHT_GLOW = '#152255';
const NIGHT_EDGE = '#020309';
const LABEL = 'rgba(205, 214, 255, 0.55)';

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';

// Particles: how many, the grid step (pt) they are sampled from inside each word's glyphs, and the
// sprite each one is drawn with (a soft dot SPRITE px wide, drawn at SPRITE_SCALE × its size).
const COUNT = 2600;
const SAMPLE_STEP = 2.1;
const SPRITE = 16;
const SPRITE_SCALE = 0.2;
// A word is set to fit WORD_MARGIN pt in from each side (so a scattered word still clears the
// edges), at most WORD_MAX pt tall, its middle at WORD_AT of the screen height.
const WORD_MARGIN = 44;
const WORD_MAX = 128;
const WORD_AT = 0.46;

// A morph: each particle travels for TRAVEL s, starting up to SPREAD s late (left to right, with a
// little noise), along an arc that bows SWIRL pt off the straight line in a direction set by a
// slowly turning field. Idle particles shimmer by SHIMMER pt.
const TRAVEL = 1.1;
const SPREAD = 0.5;
const SWIRL = 70;
const SHIMMER = 0.7;
const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);
// Colours shift to the next word's over COLOR_TIME s; a halo of GLOW_BLUR pt in the word's colour.
const COLOR_TIME = 1.2;
const GLOW_BLUR = 5;
// The word indicator: the words themselves in small tracked capitals, LABEL_GAP pt apart, the one
// on screen lit and underlined by a hairline (MARK_HEIGHT pt) that glides to the next word as it
// forms, its width following the words' widths. The mark rides one critically damped spring (about
// 0.9 s, close to the particles' own flight). The row sits ROW_ABOVE pt above the bottom safe area,
// the hint HINT_ABOVE pt above it.
const LABEL_GAP = 22;
const MARK_HEIGHT = 1.5;
const MARK_BELOW = 8;
const ROW_ABOVE = 44;
const HINT_ABOVE = 26;
const DIM = 0.36;
// Tracking of the labels (pt); iOS adds it after the last letter too, so the mark leaves it out.
const TRACKING = 2.4;
const MARK_STIFFNESS = 60;
const MARK_DAMPING = 2 * Math.sqrt(MARK_STIFFNESS);
// Edges kept clear around the labels at the top and sides, in pt.
const EDGE = 28;

// A finger pushes particles within REACH pt away, harder the closer they are (PUSH pt/s² at the
// finger); each one is held to its place by a spring (STIFFNESS, DAMPING) that brings it back with a
// small overshoot once the finger moves on.
const REACH = 92;
const PUSH = 5200;
const STIFFNESS = 46;
const DAMPING = 7.5;
// The finger's strength fades in and out over this long (s), so a touch never jolts the field.
const FINGER_EASE = 0.12;

// autoPlay (seconds): each word holds, then flows into the next every WORD_EVERY s, starting at
// FIRST_MORPH. While the third word holds, a ghost finger passes through it.
const LOOP = 16;
const WORD_EVERY = 3.2;
const FIRST_MORPH = 1.2;
const GHOST_FROM = 6.1;
const GHOST_TIME = 1.1;
// After a touch the loop waits this long, then carries on from the word on screen.
const USER_HOLD = 4;

type Field = {
  // Where each particle travels from and to, as flat x,y arrays, and when its trip began.
  fromX: number[];
  fromY: number[];
  word: number;
  start: number;
  // The finger's push: offset and speed per particle.
  ox: number[];
  oy: number[];
  vx: number[];
  vy: number[];
  // Where each particle is drawn this frame.
  px: number[];
  py: number[];
};

/** A small deterministic random generator, so every run samples the same particles. */
function random(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/**
 * Points filling `word`'s glyphs, COUNT of them, centred at (cx, cy). They are sorted left to right,
 * so particle i lands at about the same place in every word's reading order and the words flow
 * sideways into each other rather than crossing chaotically.
 */
function sampleWord(word: string, size: number, cx: number, cy: number, seed: number) {
  const font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '900' });
  const path = word ? Skia.Path.MakeFromText(word, 0, 0, font) : null;
  const xs: number[] = [];
  const ys: number[] = [];
  if (!path) {
    for (let i = 0; i < COUNT; i += 1) {
      xs.push(cx);
      ys.push(cy);
    }
    return { xs, ys };
  }
  const box = path.computeTightBounds();
  const dx = cx - (box.x + box.width / 2);
  const dy = cy - (box.y + box.height / 2);
  const rand = random(seed);
  const inside: [number, number][] = [];
  for (let y = box.y; y <= box.y + box.height; y += SAMPLE_STEP) {
    for (let x = box.x; x <= box.x + box.width; x += SAMPLE_STEP) {
      const jx = x + (rand() - 0.5) * SAMPLE_STEP * 0.8;
      const jy = y + (rand() - 0.5) * SAMPLE_STEP * 0.8;
      if (path.contains(jx, jy)) inside.push([jx + dx, jy + dy]);
    }
  }
  // Shuffle, then take COUNT (repeating with a nudge if a word is small).
  for (let i = inside.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rand() * (i + 1));
    const t = inside[i];
    inside[i] = inside[j];
    inside[j] = t;
  }
  // A word with nothing to sample (an empty string, a missing glyph) collapses to its middle.
  if (inside.length === 0) inside.push([cx, cy]);
  const picked: [number, number][] = [];
  for (let i = 0; i < COUNT; i += 1) {
    const [x, y] = inside[i % inside.length];
    const again = i >= inside.length;
    picked.push(again ? [x + (rand() - 0.5) * 1.6, y + (rand() - 0.5) * 1.6] : [x, y]);
  }
  picked.sort((a, b) => a[0] + a[1] * 0.18 - (b[0] + b[1] * 0.18));
  for (const [x, y] of picked) {
    xs.push(x);
    ys.push(y);
  }
  return { xs, ys };
}

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/** One step of a unit-mass spring towards `to`: returns [position, speed]. */
function springStep(x: number, v: number, to: number, stiffness: number, damping: number, dt: number) {
  'worklet';
  const speed = v + (stiffness * (to - x) - damping * v) * dt;
  return [x + speed * dt, speed];
}

/** True when the loop clock went from `before` to `now` across `at`, wrapping at LOOP. */
function passed(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** The ghost finger's path across the third word: left to right, a little uphill, eased. */
function ghostAt(t: number, width: number, height: number) {
  'worklet';
  const p = EASE_IN_OUT(clamp01(t));
  return { x: width * (0.1 + 0.8 * p), y: height * (WORD_AT + 0.05 - 0.08 * p + 0.03 * Math.sin(p * Math.PI * 2)) };
}

/**
 * Thousands of small glowing particles that spell a word on a deep navy night. Every few seconds they
 * flow into the next word: each particle sets off a moment after its neighbour to the left, swings
 * along a curved path through a slowly turning field, and settles into the new letters while the
 * colours shift to the new word's. A finger pushes the particles aside like sand and they spring
 * back when it moves on; a tap forms the next word.
 */
export function ParticleWordMorph({ words: wordsIn = WORDS, palettes: palettesIn = PALETTES, autoPlay = false, onWord }: Props) {
  const words = wordsIn.length > 0 ? wordsIn : WORDS;
  const palettes = palettesIn.length > 0 ? palettesIn : PALETTES;
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  // Every word's particle targets, sampled once per screen size on the JS thread.
  const targets = useMemo(() => {
    if (width <= 0) return null;
    const probe = matchFont({ fontFamily: FAMILY, fontSize: 100, fontWeight: '900' });
    let widest = 1;
    for (const word of words) widest = Math.max(widest, probe.measureText(word).width);
    const fontSize = Math.max(12, Math.min(WORD_MAX, (100 * Math.max(40, width - WORD_MARGIN * 2)) / widest));
    return words.map((word, k) => sampleWord(word, fontSize, width / 2, height * WORD_AT, 17 + k * 101));
  }, [words, width, height]);

  // Per-particle constants: its start delay, size, shimmer phase and swirl direction.
  const traits = useMemo(() => {
    const rand = random(7);
    const delay: number[] = [];
    const scale: number[] = [];
    const phase: number[] = [];
    const turn: number[] = [];
    for (let i = 0; i < COUNT; i += 1) {
      delay.push(clamp01(i / COUNT + (rand() - 0.5) * 0.18) * SPREAD);
      scale.push(SPRITE_SCALE * (0.55 + rand() * 0.75));
      phase.push(rand() * Math.PI * 2);
      turn.push(rand() < 0.5 ? -1 : 1);
    }
    return { delay, scale, phase, turn };
  }, []);

  const field = useSharedValue<Field | null>(null);
  const elapsed = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The finger (or the ghost): where, and how strongly it pushes (0…1, eased).
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerOn = useSharedValue(0);
  const fingerTarget = useSharedValue(0);
  // Colours: the palette being left, the one arriving, and when the change began.
  const colorFrom = useSharedValue(0);
  const colorTo = useSharedValue(0);
  const colorStart = useSharedValue(-10);
  const shown = useSharedValue(0);
  // The indicator's mark, in words (0 … count − 1), and its speed; each label's place in the row.
  const mark = useSharedValue(0);
  const markSpeed = useSharedValue(0);
  const labelX = useSharedValue<number[]>(words.map(() => 0));
  const labelW = useSharedValue<number[]>(words.map(() => 0));
  const rowLeft = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  // A new screen size resamples the words and puts every particle straight onto the current word.
  useEffect(() => {
    if (!targets) return;
    const word = 0;
    const xs = targets[word].xs.slice();
    const ys = targets[word].ys.slice();
    field.set({
      fromX: xs,
      fromY: ys,
      word,
      start: -10,
      ox: new Array(COUNT).fill(0),
      oy: new Array(COUNT).fill(0),
      vx: new Array(COUNT).fill(0),
      vy: new Array(COUNT).fill(0),
      px: xs.slice(),
      py: ys.slice(),
    });
    shown.set(word);
    colorFrom.set(word);
    colorTo.set(word);
    mark.set(word);
    markSpeed.set(0);
  }, [targets, field, shown, colorFrom, colorTo, mark, markSpeed]);

  const tapped = (index: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onWord?.(index);
  };

  /** Starts every particle towards word `next` from wherever it is now. */
  const morphTo = (next: number) => {
    'worklet';
    if (!targets || next < 0 || next >= targets.length) return;
    const now = elapsed.get();
    field.modify((f) => {
      'worklet';
      if (!f) return f;
      for (let i = 0; i < COUNT; i += 1) {
        // The trip starts from the particle's place on its path, without the finger's push.
        f.fromX[i] = f.px[i] - f.ox[i];
        f.fromY[i] = f.py[i] - f.oy[i];
      }
      f.word = next;
      f.start = now;
      return f;
    }, true);
    colorFrom.set(colorTo.get());
    colorTo.set(next);
    colorStart.set(now);
    shown.set(next);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0 || !targets) return;
    const now = elapsed.get() + dt;
    elapsed.set(now);
    const count = targets.length;

    let ghost = false;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // The loop picks up from the word on screen, so it never jumps.
      if (hold.get() <= 0) clock.set(shown.get() * WORD_EVERY);
    } else if (looping.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let k = 0; k < count; k += 1) {
        if (passed(before, t, FIRST_MORPH + k * WORD_EVERY)) morphTo((k + 1) % count);
      }
      if (t >= GHOST_FROM && t <= GHOST_FROM + GHOST_TIME) {
        const at = ghostAt((t - GHOST_FROM) / GHOST_TIME, width, height);
        fingerX.set(at.x);
        fingerY.set(at.y);
        ghost = true;
      }
    }
    // The indicator's mark follows the word on screen.
    const goal = shown.get();
    if (reducedMotion) {
      mark.set(goal);
      markSpeed.set(0);
    } else {
      const a = springStep(mark.get(), markSpeed.get(), goal, MARK_STIFFNESS, MARK_DAMPING, dt);
      mark.set(Number.isFinite(a[0]) ? a[0] : goal);
      markSpeed.set(Number.isFinite(a[1]) ? a[1] : 0);
    }

    const want = ghost ? 1 : fingerTarget.get();
    const on = fingerOn.get() + (want - fingerOn.get()) * clamp01(dt / FINGER_EASE);
    fingerOn.set(reducedMotion ? 0 : on);

    const fx = fingerX.get();
    const fy = fingerY.get();
    const push = fingerOn.get();
    field.modify((f) => {
      'worklet';
      if (!f) return f;
      const to = targets[f.word] ?? targets[0];
      const since = now - f.start;
      for (let i = 0; i < COUNT; i += 1) {
        const tx = to.xs[i];
        const ty = to.ys[i];
        let bx = tx;
        let by = ty;
        const p = clamp01((since - traits.delay[i]) / TRAVEL);
        if (p < 1) {
          const sx = f.fromX[i];
          const sy = f.fromY[i];
          if (reducedMotion) {
            bx = p < 0.5 ? sx : tx;
            by = p < 0.5 ? sy : ty;
          } else {
            const e = EASE_IN_OUT(p);
            bx = sx + (tx - sx) * e;
            by = sy + (ty - sy) * e;
            // Bow off the straight line along a direction from a slowly turning field.
            const angle = Math.sin(bx * 0.009 + now * 0.5) * 2.2 + Math.cos(by * 0.011 - now * 0.4) * 2.2 + traits.phase[i] * 0.35;
            const bow = Math.sin(Math.PI * e) * SWIRL * traits.turn[i] * (0.6 + 0.4 * Math.sin(traits.phase[i] * 3));
            bx += Math.cos(angle) * bow;
            by += Math.sin(angle) * bow;
          }
        }
        if (!reducedMotion) {
          bx += Math.sin(now * 1.7 + traits.phase[i]) * SHIMMER;
          by += Math.cos(now * 1.3 + traits.phase[i] * 1.3) * SHIMMER;
        }

        // The finger's push, and the spring that returns each particle.
        let ax = -STIFFNESS * f.ox[i] - DAMPING * f.vx[i];
        let ay = -STIFFNESS * f.oy[i] - DAMPING * f.vy[i];
        if (push > 0.01) {
          const ddx = bx + f.ox[i] - fx;
          const ddy = by + f.oy[i] - fy;
          const d = Math.sqrt(ddx * ddx + ddy * ddy) + 0.001;
          if (d < REACH) {
            const k = (1 - d / REACH) * (1 - d / REACH) * PUSH * push;
            ax += (ddx / d) * k;
            ay += (ddy / d) * k;
          }
        }
        f.vx[i] += ax * dt;
        f.vy[i] += ay * dt;
        f.ox[i] += f.vx[i] * dt;
        f.oy[i] += f.vy[i] * dt;
        f.px[i] = bx + f.ox[i];
        f.py[i] = by + f.oy[i];
      }
      return f;
    }, true);
  });

  const texture = useTexture(
    <Circle cx={SPRITE / 2} cy={SPRITE / 2} r={SPRITE / 2}>
      <RadialGradient
        c={vec(SPRITE / 2, SPRITE / 2)}
        r={SPRITE / 2}
        colors={['#ffffff', 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0)']}
        positions={[0, 0.45, 1]}
      />
    </Circle>,
    { width: SPRITE, height: SPRITE },
  );
  const sprites = useMemo<SkRect[]>(() => {
    const list: SkRect[] = [];
    for (let i = 0; i < COUNT; i += 1) list.push(Skia.XYWHRect(0, 0, SPRITE, SPRITE));
    return list;
  }, []);
  const transforms = useRSXformBuffer(COUNT, (xform, i) => {
    'worklet';
    const f = field.get();
    if (!f) {
      xform.set(0, 0, -100, -100);
      return;
    }
    const s = traits.scale[i];
    xform.set(s, 0, f.px[i] - (SPRITE * s) / 2, f.py[i] - (SPRITE * s) / 2);
  });

  // The palette across the word, shifting as the next word forms.
  const shift = useDerivedValue(() => clamp01((elapsed.get() - colorStart.get()) / COLOR_TIME));
  const colors = useDerivedValue(() => {
    const a = palettes[colorFrom.get() % palettes.length];
    const b = palettes[colorTo.get() % palettes.length];
    const t = shift.get();
    return [interpolateColor(t, [0, 1], [a[0], b[0]]), interpolateColor(t, [0, 1], [a[1], b[1]])];
  });
  const glow = useDerivedValue(() => {
    const a = palettes[colorFrom.get() % palettes.length];
    const b = palettes[colorTo.get() % palettes.length];
    return interpolateColor(shift.get(), [0, 1], [a[1], b[1]]);
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      hold.set(USER_HOLD);
      fingerX.set(event.x);
      fingerY.set(event.y);
      fingerTarget.set(1);
    })
    .onUpdate((event) => {
      hold.set(USER_HOLD);
      fingerX.set(event.x);
      fingerY.set(event.y);
    })
    .onFinalize(() => {
      fingerTarget.set(0);
    });

  // The indicator's row on screen (the labels' vertical middle), for taps on its words.
  const rowY = height - insets.bottom - ROW_ABOVE;

  const tap = Gesture.Tap()
    .maxDuration(300)
    .maxDistance(12)
    .onEnd((event, success) => {
      if (!success || !targets) return;
      hold.set(USER_HOLD);
      let next = (shown.get() + 1) % targets.length;
      // A tap on the indicator picks that word.
      if (Math.abs(event.y - rowY) < 22) {
        const xs = labelX.get();
        const ws = labelW.get();
        for (let k = 0; k < targets.length; k += 1) {
          const left = rowLeft.get() + (xs[k] ?? 0);
          if ((ws[k] ?? 0) > 0 && event.x > left - LABEL_GAP / 2 && event.x < left + ws[k] + LABEL_GAP / 2) {
            if (k === shown.get()) return;
            next = k;
            break;
          }
        }
      }
      morphTo(next);
      scheduleOnRN(tapped, next);
    });

  return (
    <GestureDetector gesture={Gesture.Simultaneous(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel={`Particles spelling ${words.join(', ')}. Touch to scatter them; tap for the next word.`}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={width} height={height}>
              <RadialGradient
                c={vec(width / 2, height * WORD_AT)}
                r={Math.max(width, height) * 0.78}
                colors={[NIGHT_GLOW, NIGHT, NIGHT_EDGE]}
                positions={[0, 0.48, 1]}
              />
            </Rect>
            <Group
              layer={
                <Paint>
                  <Shadow dx={0} dy={0} blur={GLOW_BLUR} color={glow} />
                </Paint>
              }
            >
              <Atlas
                image={texture}
                sprites={sprites}
                transforms={transforms}
                sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
              />
              <Rect x={0} y={0} width={width} height={height} blendMode="srcIn">
                <LinearGradient start={vec(WORD_MARGIN, 0)} end={vec(width - WORD_MARGIN, height * 0.2)} colors={colors} />
              </Rect>
            </Group>
          </Canvas>
        )}
        <View pointerEvents="none" style={[styles.top, { top: insets.top + 22 }]}>
          <Text style={styles.label}>WORDS IN DUST</Text>
          <Text style={styles.label}>{COUNT.toLocaleString('en-US')} PARTICLES</Text>
        </View>
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + ROW_ABOVE + HINT_ABOVE }]}>
          Touch to scatter · Tap for the next word
        </Text>
        <WordIndicator words={words} mark={mark} labelX={labelX} labelW={labelW} rowLeft={rowLeft} top={rowY} />
      </View>
    </GestureDetector>
  );
}

type IndicatorProps = {
  words: string[];
  mark: SharedValue<number>;
  labelX: SharedValue<number[]>;
  labelW: SharedValue<number[]>;
  /** Where the row of labels starts on screen. */
  rowLeft: SharedValue<number>;
  /** The labels' vertical middle on screen. */
  top: number;
};

/**
 * Which word is on screen: the words in a row of small capitals, the current one lit and
 * underlined. The hairline glides from word to word as the particles fly, stretching or shrinking
 * to each word's width, and the labels brighten and dim with it.
 */
function WordIndicator({ words, mark, labelX, labelW, rowLeft, top }: IndicatorProps) {
  const underline = useAnimatedStyle(() => {
    const xs = labelX.get();
    const ws = labelW.get();
    const last = xs.length - 1;
    const m = Math.min(last, Math.max(0, mark.get()));
    const a = Math.floor(m);
    const b = Math.min(last, a + 1);
    const t = m - a;
    const wa = ws[a] ?? 0;
    const wb = ws[b] ?? 0;
    const xa = xs[a] ?? 0;
    const xb = xs[b] ?? 0;
    const w = wa + (wb - wa) * t - TRACKING;
    return {
      opacity: wa > 0 && wb > 0 ? 1 : 0,
      width: Number.isFinite(w) ? Math.max(0, w) : 0,
      transform: [{ translateX: Number.isFinite(xa) ? xa + (xb - xa) * t : 0 }],
    };
  });
  return (
    <View pointerEvents="none" style={[styles.row, { top: top - 8 }]}>
      <View style={styles.rowInner} onLayout={(event: LayoutChangeEvent) => rowLeft.set(event.nativeEvent.layout.x)}>
        {words.map((word, k) => (
          <WordLabel key={`${word}-${k}`} word={word} index={k} mark={mark} labelX={labelX} labelW={labelW} />
        ))}
        <Animated.View style={[styles.mark, underline]} />
      </View>
    </View>
  );
}

type LabelProps = {
  word: string;
  index: number;
  mark: SharedValue<number>;
  labelX: SharedValue<number[]>;
  labelW: SharedValue<number[]>;
};

function WordLabel({ word, index, mark, labelX, labelW }: LabelProps) {
  const lit = useAnimatedStyle(() => ({ opacity: DIM + (1 - DIM) * clamp01(1 - Math.abs(mark.get() - index)) }));
  return (
    <Animated.Text
      style={[styles.word, lit]}
      onLayout={(event: LayoutChangeEvent) => {
        const { x, width } = event.nativeEvent.layout;
        labelX.modify((list) => {
          'worklet';
          const next = list.slice();
          next[index] = x;
          return next;
        });
        labelW.modify((list) => {
          'worklet';
          const next = list.slice();
          next[index] = width;
          return next;
        });
      }}
    >
      {word}
    </Animated.Text>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: NIGHT,
    overflow: 'hidden',
  },
  top: {
    position: 'absolute',
    left: EDGE,
    right: EDGE,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  label: {
    color: LABEL,
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 2.2,
    fontVariant: ['tabular-nums'],
  },
  row: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  rowInner: {
    flexDirection: 'row',
    gap: LABEL_GAP,
    paddingBottom: MARK_BELOW + MARK_HEIGHT,
  },
  word: {
    color: '#eef1ff',
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: TRACKING,
    lineHeight: 16,
  },
  mark: {
    position: 'absolute',
    left: 0,
    bottom: 0,
    height: MARK_HEIGHT,
    borderRadius: MARK_HEIGHT / 2,
    backgroundColor: '#eef1ff',
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: LABEL,
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
  },
});
