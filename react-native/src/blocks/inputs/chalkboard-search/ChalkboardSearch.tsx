import {
  BlurMask,
  Canvas,
  Group,
  Image,
  Path,
  Skia,
  matchFont,
  rect,
} from '@shopify/react-native-skia';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Keyboard, PixelRatio, Platform, StyleSheet, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CHALK_SKSL, type ChalkLine, bakeBoard, chalkLine, makeGeo } from './chalkboardSearchArt';
import {
  AUTO_SUGGESTIONS,
  type Board,
  DUST_LIFE,
  DUST_PER_GLYPH,
  LOOP,
  QUERY,
  Q_STEP,
  SUG_LEN,
  autoBoard,
  easeInOutSine,
  glyphAt,
  hash,
  seg,
  suggest,
} from './chalkboardSearchMotion';
import { CafeInfo, ChalkPath, EraserArt } from './chalkboardSearchParts';

/*
 Chalkboard Search — a café app's menu search on a chalkboard. The query is chalked onto the
 board as you type: each glyph's outline (from the font, nudged and tilted a little) filled with
 a chalk shader, revealed by the pen moving through it, dust falling from under the stroke. The
 menu lines that match are chalked in below and rewritten when the query changes. A swipe across
 the board is a felt eraser: it wipes the chalk where it goes and leaves a haze that clears.

 One Canvas: the baked board image, then the live chalk in one layer the eraser cuts out of
 (dstOut), the dust, the haze and the eraser. A hidden TextInput over the search line takes the
 keyboard. The loop script is in chalkboardSearchMotion.ts.
*/

type Props = {
  /** Writes "lemon tart", chalks three menu lines, wipes the board, every LOOP seconds. */
  autoPlay?: boolean;
};

const CHALK_FAMILY = Platform.select({ ios: 'Chalkboard SE', default: 'casual' });
const IDLE = 2;
const RETURN_LEN = 0.65;
const ERASER_W = 60;
const PH_TEXT = 'search the menu…';


type Lines = { query: ChalkLine; sugs: ChalkLine[] };

/** Adds a chalk bullet in front of a menu line. */
function withBullet(line: ChalkLine, x: number, y: number): ChalkLine {
  return { ...line, path: Skia.PathBuilder.MakeFromPath(line.path).addCircle(x, y, 2.6).build() };
}

/** A polyline through [x0, y0, x1, y1, …]. */
function strokeOf(pts: number[]) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (pts.length < 2) return b.build();
  b.moveTo(pts[0], pts[1]);
  for (let k = 2; k + 1 < pts.length; k += 2) b.lineTo(pts[k], pts[k + 1]);
  if (pts.length === 2) b.lineTo(pts[0] + 0.1, pts[1]);
  return b.build();
}

/** Pen and per-glyph data the UI thread reads. */
type Pen = { lefts: number[]; rights: number[]; spaces: boolean[]; ends: number[] };

export function ChalkboardSearch({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top), [W, H, insets.top]);
  const chalk = useMemo(() => Skia.RuntimeEffect.Make(CHALK_SKSL), []);

  const fonts = useMemo(
    () => ({
      cafe: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 16, fontWeight: '400' }),
      title: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 32, fontWeight: '700' }),
      small: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 14, fontWeight: '400' }),
      query: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 30, fontWeight: '400' }),
      sug: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 21, fontWeight: '400' }),
      ph: matchFont({ fontFamily: CHALK_FAMILY, fontSize: 22, fontWeight: '400' }),
    }),
    [],
  );
  const board = useMemo(
    () => (chalk ? bakeBoard(g, fonts, chalk, Math.min(3, PixelRatio.get())) : null),
    [g, fonts, chalk],
  );

  const makeLines = useCallback(
    (q: string, sugs: string[]): Lines => ({
      query: chalkLine(q, fonts.query, g.qx, g.qy, 21),
      sugs: sugs.map((s, j) => withBullet(chalkLine(s, fonts.sug, g.sx + 16, g.sy[j], 30 + j * 7), g.sx + 4, g.sy[j] - 7)),
    }),
    [fonts, g],
  );
  const autoLines = useMemo(() => makeLines(QUERY, AUTO_SUGGESTIONS), [makeLines]);
  const placeholder = useMemo(() => chalkLine(PH_TEXT, fonts.ph, g.qx, g.qy - 2, 41), [fonts, g]);

  // ---- JS state: the user's query (keystroke rate only) ---------------------------------------
  const [userMode, setUserMode] = useState(false);
  const [query, setQuery] = useState('');
  const userSugs = useMemo(() => suggest(query), [query]);
  const userLines = useMemo(() => makeLines(query, userSugs), [makeLines, query, userSugs]);
  const lines = userMode ? userLines : autoLines;

  // ---- UI-thread state ------------------------------------------------------------------------
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const focused = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const pen = useSharedValue<Pen>({ lefts: [], rights: [], spaces: [], ends: [] });
  const births = useSharedValue<number[]>([]);
  const sugAt = useSharedValue<number[]>([-9, -9, -9]);
  const uPen = useSharedValue(g.qx);
  const uPh = useSharedValue(1);
  const uHaze = useSharedValue(0);
  const uEraserOn = useSharedValue(0);
  const uEraser = useSharedValue<number[]>([0, 0, 0]);
  /** The finger's eraser stroke and its haze, as [x0, y0, x1, y1, …]. */
  const uErase = useSharedValue<number[]>([]);
  const uHazePath = useSharedValue<number[]>([]);
  const fade = useSharedValue(1);
  /** The menu lines on the board, so entering the user's mode does not chalk them again. */
  const sugKeySeen = useSharedValue('');

  useEffect(() => {
    pen.set({
      lefts: lines.query.lefts,
      rights: lines.query.rights,
      spaces: (userMode ? query : QUERY).split('').map((c) => c === ' '),
      ends: lines.sugs.map((s) => s.end),
    });
  }, [lines, pen, query, userMode]);

  // The eraser's path in the loop: four passes over the query and the three menu lines.
  const trail = useMemo(() => {
    const b = Skia.PathBuilder.Make();
    const l = g.slateX + 22;
    const r = g.slateX + g.slateW - 22;
    const ys = [g.qy - 12, g.sy[0] - 8, g.sy[1] - 8, g.sy[2] - 8];
    b.moveTo(l, ys[0]);
    for (let k = 0; k < ys.length; k += 1) {
      const from = k % 2 === 0 ? l : r;
      const to = k % 2 === 0 ? r : l;
      if (k > 0) b.cubicTo(from + (k % 2 === 0 ? -30 : 30), ys[k - 1] + 6, from + (k % 2 === 0 ? -30 : 30), ys[k] - 6, from, ys[k]);
      b.lineTo(to, ys[k] + (hash(k) - 0.5) * 6);
    }
    const p = b.build();
    const it = Skia.ContourMeasureIter(p, false, 1);
    const c = it.next();
    const samples: number[] = [];
    if (c) {
      const len = c.length();
      for (let i = 0; i <= 120; i += 1) {
        const [pos, tan] = c.getPosTan((len * i) / 120);
        samples.push(pos.x, pos.y, Math.atan2(tan.y, tan.x));
      }
    }
    return { path: p, samples };
  }, [g]);

  const backToAuto = useCallback(() => {
    Keyboard.dismiss();
    setUserMode(false);
    setQuery('');
  }, []);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const n = now.get() + dt;
    now.set(n);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
    } else if (m === 1) {
      if (focused.get() === 0 && uEraserOn.get() === 0 && n - lastTouch.get() > IDLE) {
        mode.set(2);
        returnAt.set(n);
        fade.set(withTiming(0, { duration: 600 }));
      }
    } else if (n - returnAt.get() > RETURN_LEN) {
      uHaze.set(0);
      uErase.set([]);
      uHazePath.set([]);
      births.set([]);
      clock.set(0);
      mode.set(0);
      fade.set(1);
      scheduleOnRN(backToAuto);
    }
  });

  const state = useDerivedValue<Board>(() => {
    const m = mode.get();
    const p = pen.get();
    if (m === 0) {
      if (auto) return autoBoard(clock.get(), p.lefts, p.rights);
      return { pen: g.qx, sug: [0, 0, 0], trail: 0, eraserOn: 0, haze: 0, placeholder: 1 };
    }
    const n = now.get();
    const at = sugAt.get();
    return {
      pen: uPen.get(),
      sug: [0, 1, 2].map((j) => (reduced ? 1 : easeInOutSine(seg(n, at[j], at[j] + SUG_LEN)))),
      trail: 1,
      eraserOn: uEraserOn.get(),
      haze: uHaze.get(),
      placeholder: uPh.get(),
    };
  });
  const tNow = useDerivedValue(() => (mode.get() === 0 ? clock.get() : now.get()));
  const layerOpacity = useDerivedValue(() => (mode.get() === 0 ? 1 : fade.get()));

  const queryClip = useDerivedValue(() => rect(g.qx - 30, g.qy - 46, Math.max(0, state.get().pen - g.qx + 30), 70));
  const sugClip0 = useDerivedValue(() => rect(g.sx - 10, g.sy[0] - 30, ((pen.get().ends[0] ?? 0) - g.sx + 20) * state.get().sug[0], 44));
  const sugClip1 = useDerivedValue(() => rect(g.sx - 10, g.sy[1] - 30, ((pen.get().ends[1] ?? 0) - g.sx + 20) * state.get().sug[1], 44));
  const sugClip2 = useDerivedValue(() => rect(g.sx - 10, g.sy[2] - 30, ((pen.get().ends[2] ?? 0) - g.sx + 20) * state.get().sug[2], 44));
  const sugClips = [sugClip0, sugClip1, sugClip2];
  const phOpacity = useDerivedValue(() => state.get().placeholder * 0.34);

  const erasePath = useDerivedValue(() => (mode.get() === 0 ? trail.path : strokeOf(uErase.get())));
  const hazePath = useDerivedValue(() => (mode.get() === 0 ? trail.path : strokeOf(uHazePath.get())));
  const eraseEnd = useDerivedValue(() => state.get().trail);
  const hazeOpacity = useDerivedValue(() => state.get().haze);

  const eraserPose = useDerivedValue(() => {
    if (mode.get() !== 0) return uEraser.get();
    const s = trail.samples;
    const k = Math.min(120, Math.round(state.get().trail * 120));
    return [s[k * 3] ?? 0, s[k * 3 + 1] ?? 0, 0.12 * Math.sin(s[k * 3 + 2] ?? 0)];
  });
  const eraserTransform = useDerivedValue(() => {
    const e = eraserPose.get();
    return [{ translateX: e[0] }, { translateY: e[1] }, { rotate: e[2] }];
  });
  const eraserOpacity = useDerivedValue(() => state.get().eraserOn);

  // Chalk dust: DUST_PER_GLYPH grains from under each glyph while it is written.
  const dust = useDerivedValue(() => {
    const p = pen.get();
    const t = tNow.get();
    const b = births.get();
    const m = mode.get();
    const path = Skia.PathBuilder.Make();
    if (reduced) return path.build();
    for (let i = 0; i < p.lefts.length; i += 1) {
      if (p.spaces[i]) continue;
      const birth = m === 0 ? glyphAt(i) : (b[i] ?? -9);
      for (let k = 0; k < DUST_PER_GLYPH; k += 1) {
        const h1 = hash(i * 17 + k * 3.7);
        const h2 = hash(i * 29 + k * 5.3 + 1);
        const tau = t - (birth + (Q_STEP * k) / DUST_PER_GLYPH);
        if (tau < 0 || tau > DUST_LIFE) continue;
        const x = p.lefts[i] + (p.rights[i] - p.lefts[i]) * h1 + (h2 - 0.5) * 22 * tau;
        const y = g.qy + 3 + 160 * (0.5 + h2) * tau * tau;
        const r = (0.6 + 0.9 * h2) * (1 - tau / DUST_LIFE);
        path.addCircle(x, y, r);
      }
    }
    return path.build();
  });

  // ---- Touch -----------------------------------------------------------------------------
  const enterUser = useCallback((fromSwipe: boolean) => {
    lastTouch.set(now.get());
    if (mode.get() === 1) return;
    let q = '';
    let shown = false;
    if (mode.get() === 0 && auto) {
      const t = clock.get();
      if (t < 7.2) {
        let n = 0;
        for (let i = 0; i < QUERY.length; i += 1) if (glyphAt(i) + Q_STEP <= t) n = i + 1;
        q = QUERY.slice(0, n).trimEnd();
        shown = t > 4.5;
      }
    }
    const line = chalkLine(q, fonts.query, g.qx, g.qy, 21);
    births.set(q.split('').map(() => -9));
    uPen.set(q.length ? line.end + 4 : g.qx);
    sugAt.set(shown ? [-9, -9, -9] : [now.get(), now.get() + 0.15, now.get() + 0.3]);
    uPh.set(q.length ? 0 : 1);
    if (!fromSwipe) uErase.set([]);
    sugKeySeen.set(suggest(q).join('|'));
    fade.set(1);
    setQuery(q);
    setUserMode(true);
    mode.set(1);
  }, [auto, births, clock, fade, fonts, g, lastTouch, mode, now, sugAt, sugKeySeen, uErase, uPen, uPh]);

  // When the user's lines change, move the pen and chalk the new menu lines in.
  const sugKey = userSugs.join('|');
  useEffect(() => {
    if (!userMode) return;
    const end = userLines.query.lefts.length ? userLines.query.end + 4 : g.qx;
    uPen.set(reduced ? end : withTiming(end, { duration: 180 }));
  }, [g.qx, reduced, uPen, userLines, userMode]);
  useEffect(() => {
    if (!userMode || sugKeySeen.get() === sugKey) return;
    sugKeySeen.set(sugKey);
    const n = now.get();
    sugAt.set([n + 0.05, n + 0.2, n + 0.35]);
  }, [now, sugAt, sugKey, sugKeySeen, userMode]);

  const onChange = (v: string) => {
    const old = query;
    const n = now.get();
    const prev = births.get();
    const next: number[] = [];
    let fresh = 0;
    for (let i = 0; i < v.length; i += 1) {
      if (i < old.length && old[i] === v[i] && prev[i] !== undefined) next.push(prev[i]);
      else {
        next.push(n + fresh * 0.06);
        fresh += 1;
      }
    }
    births.set(next);
    uErase.set([]);
    uPh.set(withTiming(v.length ? 0 : 1, { duration: 200 }));
    lastTouch.set(n);
    setQuery(v);
  };

  const clearQuery = useCallback(() => {
    setQuery('');
    births.set([]);
    uPh.set(withTiming(1, { duration: 500 }));
  }, [births, uPh]);

  const erase = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .onStart((e) => {
      scheduleOnRN(enterUser, true);
      uErase.set([e.x, e.y]);
      uHazePath.set([e.x, e.y]);
      uEraser.set([e.x, e.y, 0]);
      uEraserOn.set(withTiming(1, { duration: 120 }));
      uHaze.set(1);
    })
    .onUpdate((e) => {
      uErase.set([...uErase.get(), e.x, e.y]);
      uHazePath.set([...uHazePath.get(), e.x, e.y]);
      uEraser.set([e.x, e.y, Math.max(-0.25, Math.min(0.25, e.velocityX / 4000))]);
      lastTouch.set(now.get());
    })
    .onEnd(() => {
      scheduleOnRN(clearQuery);
    })
    .onFinalize(() => {
      uEraserOn.set(withTiming(0, { duration: 200 }));
      uHaze.set(withTiming(0, { duration: reduced ? 300 : 1800 }));
      lastTouch.set(now.get());
    });

  return (
    <View style={[styles.root, { backgroundColor: '#ece3d3' }]}>
      <GestureDetector gesture={erase}>
        <View style={{ position: 'absolute', left: 0, top: 0, width: W, height: g.boardY + g.boardH + 24 }}>
          <Canvas style={StyleSheet.absoluteFill}>
            {board ? <Image image={board} x={0} y={0} width={W} height={g.boardY + g.boardH + 24} fit="fill" /> : null}
            <Path path={hazePath} end={eraseEnd} style="stroke" strokeWidth={ERASER_W + 6} strokeCap="round" strokeJoin="round" color="rgba(222,228,220,0.16)" opacity={hazeOpacity}>
              <BlurMask blur={10} style="normal" />
            </Path>
            {chalk ? (
              <Group opacity={phOpacity}>
                <ChalkPath path={placeholder.path} chalk={chalk} seed={41} />
              </Group>
            ) : null}
            {chalk ? (
              <Group layer opacity={layerOpacity}>
                <Group clip={queryClip}>
                  <ChalkPath path={lines.query.path} chalk={chalk} seed={21} />
                </Group>
                {lines.sugs.map((s, j) => (
                  <Group key={j} clip={sugClips[j]}>
                    <ChalkPath path={s.path} chalk={chalk} seed={30 + j * 7} />
                  </Group>
                ))}
                <Path path={erasePath} end={eraseEnd} style="stroke" strokeWidth={ERASER_W} strokeCap="round" strokeJoin="round" color="#000000" blendMode="dstOut" />
              </Group>
            ) : null}
            <Path path={dust} color="rgba(236,238,230,0.62)" />
            <Group transform={eraserTransform} opacity={eraserOpacity}>
              <EraserArt />
            </Group>
          </Canvas>
          <TextInput
            value={query}
            onChangeText={onChange}
            onFocus={() => {
              enterUser(false);
              focused.set(1);
            }}
            onBlur={() => {
              focused.set(0);
              lastTouch.set(now.get());
            }}
            caretHidden
            autoCorrect={false}
            autoCapitalize="none"
            returnKeyType="search"
            maxLength={22}
            onSubmitEditing={() => Keyboard.dismiss()}
            style={[styles.input, { left: g.slateX + 16, top: g.qy - 40, width: g.slateW - 32 }]}
            accessibilityLabel="Search the menu"
          />
        </View>
      </GestureDetector>

      <CafeInfo top={g.boardY + g.boardH + 28} H={H} insetBottom={insets.bottom} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  input: { position: 'absolute', height: 52, color: 'transparent', fontSize: 22, backgroundColor: 'transparent' },
});
