import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkPath,
  type SkPicture,
  StrokeJoin,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  doubleTap,
  fingerDown,
  fingerMove,
  fingerUp,
  leaving,
  type LettersSim,
  makeLettersSim,
  placeWord,
  stepLetters,
  WORDS,
} from './SpringLettersSim';

type Props = {
  /** Drags and throws letters, flicks the floor and changes the word, on a loop. Off by default. */
  autoPlay?: boolean;
  hint?: string;
};

// A pastel stage: a lilac wall, a peach floor, candy letters with a chunky extruded side.
const WALL_TOP = '#efe8fb';
const WALL_LOW = '#e4daf6';
const FLOOR_NEAR = '#f9dccf';
const FLOOR_FAR = '#fdeee6';
const HINT = '#9a88ab';
const FACES = ['#ff6b5b', '#ffa13a', '#ffcf3d', '#3fc98d', '#4b8cff', '#a26cff', '#ff7fb3', '#2fc4d6'];
// The extruded side darkens towards this plum.
const SIDE_SHADE = [59, 29, 72];
const SHADOW = '#5b3a6b';

// Type: a heavy geometric sans from the system, sized so the widest word fits.
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed';
const HINT_FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const MAX_SIZE = 150;
const SIDE_MARGIN = 22;
// Gap between letters at rest (share of the type size).
const GAP = 0.05;
// The extruded side: layers, and each layer's step down and to the right (pt).
const DEPTH = 8;
const STEP_X = 0.75;
const STEP_Y = 1.05;
// The floor line, as a share of the screen's height, and the wall's foot just behind it.
const FLOOR_AT = 0.6;
const HORIZON_BEHIND = 0.36;

type Letters = {
  size: number;
  gap: number;
  // Per word: each letter's outline, the middle of its outline, and its box.
  paths: SkPath[][];
  centres: number[][];
  boxes: number[][];
};

/** Outlines of every letter of every word, from the font, and the boxes the physics needs. */
function buildLetters(width: number): Letters {
  const probe = matchFont({ fontFamily: FAMILY, fontSize: 100, fontWeight: '800' });
  let widest = 0;
  for (const word of WORDS) {
    let total = GAP * 100 * (word.length - 1);
    for (const letter of word) total += Skia.Path.MakeFromText(letter, 0, 0, probe)?.computeTightBounds().width ?? 60;
    widest = Math.max(widest, total);
  }
  // Never a zero, negative or infinite size, even on a very narrow window.
  const size = widest > 0 ? Math.max(8, Math.min(MAX_SIZE, (100 * (width - SIDE_MARGIN * 2)) / widest)) : MAX_SIZE;
  const font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '800' });
  const paths: SkPath[][] = [];
  const centres: number[][] = [];
  const boxes: number[][] = [];
  for (const word of WORDS) {
    const p: SkPath[] = [];
    const c: number[] = [];
    const b: number[] = [];
    for (const letter of word) {
      const path = Skia.Path.MakeFromText(letter, 0, 0, font) ?? Skia.Path.Make();
      const r = path.computeTightBounds();
      p.push(path);
      c.push(r.x + r.width / 2, r.y + r.height / 2);
      b.push(r.width, r.height);
    }
    paths.push(p);
    centres.push(c);
    boxes.push(b);
  }
  return { size, gap: size * GAP, paths, centres, boxes };
}

/** The stage: wall, the soft shadow where wall meets floor, and the floor. Painted once. */
function paintStage(width: number, height: number, floor: number, letterHeight: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const horizon = floor - letterHeight * HORIZON_BEHIND;
  const wall = Skia.Paint();
  wall.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, horizon), [Skia.Color(WALL_TOP), Skia.Color(WALL_LOW)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, horizon), wall);
  const ground = Skia.Paint();
  ground.setShader(Skia.Shader.MakeLinearGradient(vec(0, horizon), vec(0, height), [Skia.Color(FLOOR_FAR), Skia.Color(FLOOR_NEAR)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, horizon, width, height - horizon), ground);
  // A soft fold of shadow where the wall meets the floor, and a line of light on the floor's edge.
  const fold = Skia.Paint();
  fold.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, horizon - 26), vec(0, horizon), [Skia.Color('rgba(91, 58, 107, 0)'), Skia.Color('rgba(91, 58, 107, 0.12)')], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, horizon - 26, width, 26), fold);
  const edge = Skia.Paint();
  edge.setColor(Skia.Color('rgba(255, 255, 255, 0.7)'));
  canvas.drawRect(Skia.XYWHRect(0, horizon, width, 1), edge);
  // A pool of light on the floor under the word, squashed into an ellipse: the gradient is read in
  // the squashed space, so its centre is the circle's.
  const pool = Skia.Paint();
  pool.setShader(
    Skia.Shader.MakeRadialGradient(vec(width / 2, (floor + 30) / 0.35), width * 0.7, [Skia.Color('rgba(255, 255, 255, 0.45)'), Skia.Color('rgba(255, 255, 255, 0)')], null, TileMode.Clamp),
  );
  canvas.save();
  canvas.scale(1, 0.35);
  canvas.drawCircle(width / 2, (floor + 30) / 0.35, width * 0.7, pool);
  canvas.restore();
  return recorder.finishRecordingAsPicture();
}

function hexRgb(hex: string) {
  const v = parseInt(hex.slice(1), 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

function mix(a: number[], b: number[], t: number) {
  'worklet';
  return Skia.Color(`rgb(${Math.round(a[0] + (b[0] - a[0]) * t)}, ${Math.round(a[1] + (b[1] - a[1]) * t)}, ${Math.round(a[2] + (b[2] - a[2]) * t)})`);
}

/** The soft shadow a letter casts on the floor: tighter and darker the closer it is. */
function drawShadow(canvas: SkCanvas, x: number, halfW: number, lift: number, floor: number, scale: number, alpha: number) {
  'worklet';
  const far = Math.min(1, Math.max(0, lift) / 320);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(SHADOW));
  p.setAlphaf(0.26 * (1 - far * 0.8) * alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5 + far * 14, true));
  const w = halfW * scale * (1.05 - far * 0.35);
  canvas.drawOval(Skia.XYWHRect(x + 5 - w, floor - 4, w * 2, 12 + far * 6), p);
  if (lift < 14) {
    // Contact: a tight dark line where the letter meets the floor.
    const contact = Skia.Paint();
    contact.setAntiAlias(true);
    contact.setColor(Skia.Color(SHADOW));
    contact.setAlphaf(0.22 * (1 - lift / 14) * alpha);
    contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
    canvas.drawOval(Skia.XYWHRect(x - halfW * scale * 0.9, floor - 2, halfW * scale * 1.8 + 6, 5), contact);
  }
}

/**
 * One candy letter: an extruded side in deepening shades, then the face, lit from the top left
 * with a gloss along its upper edges and a shade along its lower ones.
 */
function drawLetter(canvas: SkCanvas, path: SkPath, cx: number, cy: number, x: number, y: number, angle: number, scale: number, alpha: number, face: number[], halfH: number) {
  'worklet';
  const degrees = (angle * 180) / Math.PI;
  const side = Skia.Paint();
  side.setAntiAlias(true);
  side.setAlphaf(alpha);
  const deep = SIDE_SHADE;
  for (let k = DEPTH; k >= 1; k -= 1) {
    side.setColor(mix(face, deep, 0.38 + (0.22 * k) / DEPTH));
    side.setAlphaf(alpha);
    canvas.save();
    canvas.translate(x + k * STEP_X, y + k * STEP_Y);
    canvas.rotate(degrees, 0, 0);
    canvas.scale(scale, scale);
    canvas.translate(-cx, -cy);
    canvas.drawPath(path, side);
    canvas.restore();
  }
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(degrees, 0, 0);
  canvas.scale(scale, scale);
  canvas.translate(-cx, -cy);
  const top = cy - halfH;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, top), vec(0, top + halfH * 2), [mix(face, [255, 255, 255], 0.28), mix(face, [255, 255, 255], 0), mix(face, deep, 0.08)], [0, 0.55, 1], TileMode.Clamp),
  );
  paint.setAlphaf(alpha);
  canvas.drawPath(path, paint);
  canvas.clipPath(path, ClipOp.Intersect, true);
  const shade = Skia.Paint();
  shade.setAntiAlias(true);
  shade.setStyle(PaintStyle.Stroke);
  shade.setStrokeJoin(StrokeJoin.Round);
  shade.setStrokeWidth(7);
  shade.setColor(mix(face, deep, 0.55));
  shade.setAlphaf(0.35 * alpha);
  shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  canvas.save();
  canvas.translate(3, 3.5);
  canvas.drawPath(path, shade);
  canvas.restore();
  const gloss = Skia.Paint();
  gloss.setAntiAlias(true);
  gloss.setStyle(PaintStyle.Stroke);
  gloss.setStrokeJoin(StrokeJoin.Round);
  gloss.setStrokeWidth(3.5);
  gloss.setColor(Skia.Color('#ffffff'));
  gloss.setAlphaf(0.55 * alpha);
  gloss.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.6, true));
  canvas.save();
  canvas.translate(-2, -2.4);
  canvas.drawPath(path, gloss);
  canvas.restore();
  canvas.restore();
}

/**
 * A big bold word standing on a pastel floor, each letter a rigid body on springs: one pulls it
 * back over its slot, one stands it upright, and links to its neighbours chain the word together.
 * Drag a letter and it follows with a lag, swinging, the ones beside it tugged along; let go and
 * it flies, lands, bounces and springs home. Flick up from the floor and every letter jumps.
 * Double tap for the next word: the letters shrink and turn away, and new ones drop in.
 */
export function SpringLetters({ autoPlay = false, hint = 'Drag a letter · flick the floor · double tap' }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const letters = useMemo(() => (size ? buildLetters(size.width) : null), [size]);
  const floor = size ? Math.round(size.height * FLOOR_AT) : 0;
  const stage = useMemo(
    () => (size && letters ? paintStage(size.width, size.height, floor, letters.size * 0.72) : null),
    [size, letters, floor],
  );
  const faces = useMemo(() => FACES.map(hexRgb), []);

  const sim = useSharedValue<LettersSim>(makeLettersSim(auto, reducedMotion));
  const answered = useSharedValue({ grabs: 0, flicks: 0, swaps: 0 });

  useEffect(() => {
    if (!size || !letters) return;
    const top = insets.top;
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      s.still = reducedMotion;
      s.clock = 0;
      s.floor = floor;
      s.left = 0;
      s.right = size.width;
      s.ceiling = top;
      placeWord(s, 0, letters.boxes, letters.gap, false);
      return s;
    });
  }, [auto, reducedMotion, sim, size, letters, floor, insets.top]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const knock = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  const boxes = letters?.boxes ?? [];
  const gap = letters?.gap ?? 0;
  useFrameCallback((frame) => {
    if (!letters) return;
    const dt = (frame.timeSincePreviousFrame ?? 16) / 1000;
    sim.modify((s) => {
      'worklet';
      stepLetters(s, dt, boxes, gap);
      return s;
    });
    const s = sim.get();
    const seen = answered.get();
    if (s.userGrabs > seen.grabs) scheduleOnRN(tick);
    if (s.userFlicks > seen.flicks) scheduleOnRN(knock, Haptics.ImpactFeedbackStyle.Medium);
    if (s.userSwaps > seen.swaps) scheduleOnRN(knock, Haptics.ImpactFeedbackStyle.Light);
    if (s.userGrabs !== seen.grabs || s.userFlicks !== seen.flicks || s.userSwaps !== seen.swaps) {
      answered.set({ grabs: s.userGrabs, flicks: s.userFlicks, swaps: s.userSwaps });
    }
  });

  // One finger: a second one would move the pan's centroid and make the held letter jump.
  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      sim.modify((s) => {
        'worklet';
        fingerDown(s, e.x, e.y);
        return s;
      });
    })
    .onUpdate((e) => {
      sim.modify((s) => {
        'worklet';
        fingerMove(s, e.x, e.y, e.velocityX, e.velocityY);
        return s;
      });
    })
    .onFinalize((e) => {
      sim.modify((s) => {
        'worklet';
        fingerUp(s, e.velocityX, e.velocityY);
        return s;
      });
    });

  const swap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDelay(280)
    .onEnd((e, success) => {
      if (!success) return;
      sim.modify((s) => {
        'worklet';
        doubleTap(s, e.x, e.y, boxes, gap);
        return s;
      });
    });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (!letters) return recorder.finishRecordingAsPicture();
    const s = sim.get();

    // Shadows first, so no letter is darkened by another's.
    for (let i = 0; i < s.count; i += 1) {
      if (s.wait[i] > 0) continue;
      drawShadow(canvas, s.x[i], s.halfW[i], s.floor - (s.y[i] + s.halfH[i]), s.floor, 1, s.still ? Math.min(1, s.arrive / 0.3) : 1);
    }
    if (s.leaveAge >= 0) {
      for (let i = 0; i < s.leftCount; i += 1) {
        const e = leaving(s, i);
        drawShadow(canvas, s.lx[i], letters.boxes[s.leftWord][i * 2] / 2, s.floor - s.ly[i] - letters.boxes[s.leftWord][i * 2 + 1] / 2, s.floor, 1 - 0.5 * e, 1 - e);
      }
    }

    // The word leaving: each letter shrinks, turns and fades, one after another.
    if (s.leaveAge >= 0) {
      for (let i = 0; i < s.leftCount; i += 1) {
        const e = leaving(s, i);
        if (e >= 1) continue;
        const ease = e * e;
        const turn = reducedMotion ? 0 : (i % 2 === 0 ? -1 : 1) * 0.9 * ease;
        const shrink = reducedMotion ? 1 : 1 - 0.55 * ease;
        const rise = reducedMotion ? 0 : 46 * ease;
        drawLetter(
          canvas,
          letters.paths[s.leftWord][i],
          letters.centres[s.leftWord][i * 2],
          letters.centres[s.leftWord][i * 2 + 1],
          s.lx[i],
          s.ly[i] - rise,
          s.la[i] + turn,
          shrink,
          1 - e,
          faces[i % faces.length],
          letters.boxes[s.leftWord][i * 2 + 1] / 2,
        );
      }
    }

    // The word, the held letter drawn last so it is on top.
    const shown = s.still ? Math.min(1, s.arrive / 0.3) : 1;
    for (let pass = 0; pass < 2; pass += 1) {
      for (let i = 0; i < s.count; i += 1) {
        if ((i === s.held) !== (pass === 1)) continue;
        if (s.wait[i] > 0) continue;
        drawLetter(
          canvas,
          letters.paths[s.word][i],
          letters.centres[s.word][i * 2],
          letters.centres[s.word][i * 2 + 1],
          s.x[i],
          s.y[i],
          s.a[i],
          1,
          shown,
          faces[i % faces.length],
          s.halfH[i],
        );
      }
    }

    // The fingertip, real or scripted, and the ring of a double tap.
    if (s.down) {
      const touch = Skia.Paint();
      touch.setAntiAlias(true);
      touch.setColor(Skia.Color('#ffffff'));
      touch.setAlphaf(0.38);
      canvas.drawCircle(s.fx, s.fy, 22, touch);
      touch.setStyle(PaintStyle.Stroke);
      touch.setStrokeWidth(1.5);
      touch.setAlphaf(0.8);
      canvas.drawCircle(s.fx, s.fy, 22, touch);
    }
    if (s.tapAge >= 0) {
      const t = s.tapAge / 0.6;
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(2);
      ring.setColor(Skia.Color('#ffffff'));
      ring.setAlphaf(0.85 * (1 - t));
      canvas.drawCircle(s.tapX, s.tapY, 18 + 50 * (1 - (1 - t) * (1 - t)), ring);
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Simultaneous(pan, swap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel={`A word of letters on springs. Drag a letter, flick the floor, or double tap for the next word.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {stage && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={stage} />
            <Picture picture={picture} />
          </Canvas>
        )}
        <Text style={[styles.hint, { bottom: insets.bottom + 28 }]}>{hint}</Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: WALL_TOP,
  },
  hint: {
    position: 'absolute',
    left: 24,
    right: 24,
    textAlign: 'center',
    color: HINT,
    fontFamily: HINT_FAMILY,
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
});
