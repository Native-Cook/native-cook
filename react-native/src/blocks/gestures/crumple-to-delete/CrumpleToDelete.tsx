import {
  BlurMask,
  Canvas,
  Group,
  Image,
  ImageShader,
  Oval,
  Rect,
  RoundedRect,
  Skia,
  Text as SkText,
  Vertices,
  drawAsImage,
  useFont,
  useImage,
  vec,
  type SkFont,
  type SkImage,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useMotionPreference } from '@/shared/useMotionPreference';

import {
  AFTER_RELEASE,
  AFTER_UNDO,
  DRAG_FULL,
  GX,
  GY,
  LOOP,
  S,
  crumple,
  easeInOut,
  easeOut,
  script,
  seg,
  smooth,
  springStep,
  springTo,
  tossPos,
  type Geo,
} from './ctd-motion';
import { type Cue, useCtdAudio } from './use-ctd-audio';

/*
 Crumple to Delete. A note on a sunny oak desk, another note under it, a mesh wastebasket at the bottom.
 Pull the note down and it crumples under your finger: the sheet is a 13 x 16 mesh textured with the
 note itself, each vertex gathered toward a lumpy ball with seeded wrinkles, its facets lit or shaded
 as the creases form. Let go past a third of the way (or flick) and it crushes into a ball, flies in an
 arc into the bin, spinning, drops in and the bin rocks; the note under it is revealed and a toast
 offers Undo, which throws the ball back out and opens it flat with a little spring.
 Art: the desk, the paper and the bin are image renders; the note is drawn once into an image with its
 handwriting; the mesh, its light, the shadows and every move are Skia; all controls are real views.
 autoPlay: LOOP = 9 s: rest 0-1, the note is pulled down 1-2.55, crushed and thrown 2.55-3.45, in the
 bin 3.45-3.8, toast, Undo 6.0, back and opened flat 6.0-7.4, rest from 8.
*/

const PAPER = { w: 880, h: 1120 };
const BIN = { w: 600, h: 597, cx: 315.4, cy: 157.7, rx: 231.6, ry: 136 };
const C = { ink: '#2A2621', inkSoft: '#7A6F63', red: '#B7472E', head: '#2C2722', mute: '#6F665C' };
const TEX = 2; // the note's image is drawn at 2x for crisp handwriting

const REST = 0;
const DRAG = 1;
const BACK = 2; // let go too early: springs back flat
const GONE = 3; // crushed, thrown, in the bin
const UNDO = 4;

type St = {
  auto: number;
  mode: number;
  t: number;
  now: number;
  touchAt: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  c: number;
  vc: number;
  spin: number;
  sc: number;
  sink: number;
  wobble: number;
  toast: number;
  lift: number;
  relAt: number;
  rx: number;
  ry: number;
  rc: number;
  rspin: number;
  undoAt: number;
  sx: number;
  sy: number;
  lastStep: number;
  gone: number;
};

/** The two notes, drawn once into images. */
function noteElement(paper: SkImage, w: number, h: number, hand: SkFont, handS: SkFont, title: string, lines: string[], line0: number, gap: number) {
  return (
    <Group transform={[{ scale: TEX }]}>
      <Image image={paper} x={0} y={0} width={w} height={h} fit="cover" />
      <SkText x={w * 0.2} y={line0 - 6} text={title} font={hand} color={C.ink} />
      {lines.map((l, i) => (
        <SkText key={i} x={w * 0.2} y={line0 + gap * (i + 1) - 5} text={l} font={handS} color={i === 1 ? C.red : C.ink} />
      ))}
    </Group>
  );
}

type Props = { autoPlay?: boolean };

export function CrumpleToDelete({ autoPlay = false }: Props) {
  const reduced = useMotionPreference();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [gone, setGone] = useState(false);
  const [fontsOk] = useFonts({
    'CTD-Archivo-ExtraBold': require('./assets/fonts/Archivo-ExtraBold.ttf'),
    'CTD-Archivo-SemiBold': require('./assets/fonts/Archivo-SemiBold.ttf'),
    'CTD-Archivo-Medium': require('./assets/fonts/Archivo-Medium.ttf'),
    'CTD-Mono': require('./assets/fonts/JetBrainsMono-Medium.ttf'),
  });
  const desk = useImage(require('./assets/desk.jpg'));
  const paper = useImage(require('./assets/paper.webp'));
  const bin = useImage(require('./assets/bin.webp'));
  const hand = useFont(require('./assets/fonts/GochiHand-Regular.ttf'), 30);
  const handS = useFont(require('./assets/fonts/GochiHand-Regular.ttf'), 20);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  // ------------------------------------------------------------------ geometry
  const W = size?.width ?? 402;
  const H = size?.height ?? 874;
  const top = insets.top;
  const NW = Math.min(W - 56, 352);
  const NH = Math.round(NW * 1.2);
  const HX = W / 2;
  const HY = top + 128 + NH / 2;
  const BW = 156;
  const bs = BW / BIN.w;
  const BH = BIN.h * bs;
  const BX = W / 2 - BW / 2;
  const BY = H - insets.bottom - 20 - BH;
  const BCX = BX + BIN.cx * bs;
  const BCY = BY + BIN.cy * bs;
  const BRX = BIN.rx * bs;
  const BRY = BIN.ry * bs;
  const geo: Geo = useMemo(() => ({ hx: HX, hy: HY, bx: BCX, by: BCY - 6 }), [HX, HY, BCX, BCY]);
  // the paper's ruled lines, scaled onto the note
  const ps = NW / PAPER.w;
  const LINE0 = 176 * ps;
  const GAP = 51.2 * ps;

  // the notes' images (the top one is the mesh's texture)
  const [noteA, setNoteA] = useState<SkImage | null>(null);
  const [noteB, setNoteB] = useState<SkImage | null>(null);
  useEffect(() => {
    if (!paper || !hand || !handS) return;
    let alive = true;
    const size2 = { width: NW * TEX, height: NH * TEX };
    drawAsImage(noteElement(paper, NW, NH, hand, handS, 'Saturday market', ['sourdough + 2 lemons', 'call Mai about the van!', 'return the library books', 'fix the bike light', 'flowers for Ana'], LINE0, GAP), size2).then((img) => {
      if (alive) setNoteA(img);
    });
    drawAsImage(noteElement(paper, NW, NH, hand, handS, 'Ideas for the trip', ['night train to the coast', 'pack the film camera', 'ask about the cabin'], LINE0, GAP), size2).then((img) => {
      if (alive) setNoteB(img);
    });
    return () => {
      alive = false;
    };
  }, [paper, hand, handS, NW, NH, LINE0, GAP]);

  // the mesh's fixed parts: texture coordinates and triangles
  const tex = useMemo(() => {
    const out = [];
    for (let j = 0; j < GY; j++) for (let i = 0; i < GX; i++) out.push(vec((i / (GX - 1)) * NW * TEX, (j / (GY - 1)) * NH * TEX));
    return out;
  }, [NW, NH]);
  const idx = useMemo(() => {
    const out: number[] = [];
    for (let j = 0; j < GY - 1; j++) {
      for (let i = 0; i < GX - 1; i++) {
        const a = j * GX + i;
        // alternate the diagonal so the creases do not all lean one way
        if ((i + j) % 2 === 0) out.push(a, a + 1, a + GX, a + 1, a + GX + 1, a + GX);
        else out.push(a, a + 1, a + GX + 1, a, a + GX + 1, a + GX);
      }
    }
    return out;
  }, []);

  // ------------------------------------------------------------------ state
  const st = useSharedValue<St>({
    auto: 0, mode: REST, t: 0, now: 0, touchAt: -10, x: HX, y: HY, vx: 0, vy: 0, c: 0, vc: 0, spin: 0, sc: 1,
    sink: 0, wobble: 0, toast: 0, lift: 0, relAt: -10, rx: 0, ry: 0, rc: 0, rspin: 0, undoAt: -10, sx: 0, sy: 0, lastStep: 0, gone: 0,
  });
  const tick = useSharedValue(0);
  const debugT = useSharedValue(-1);

  useEffect(() => {
    st.modify((s) => {
      'worklet';
      if (s.mode === REST && s.auto === 0) {
        s.x = HX;
        s.y = HY;
      }
      return s;
    });
  }, [HX, HY, st]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __crumple?: unknown };
    g.__crumple = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), manual: () => debugT.set(-3) };
    return () => {
      delete g.__crumple;
    };
  }, [debugT]);

  const play = useCtdAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // ------------------------------------------------------------------ the clock
  const goneSv = useSharedValue(false);
  /** Tells React when the note is gone or back (for touch targets and VoiceOver), only on change. */
  function scheduleOnRNIfChanged(s: St) {
    'worklet';
    const g = s.gone === 1;
    if (g !== goneSv.get()) {
      goneSv.set(g);
      scheduleOnRN(setGone, g);
    }
  }

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const s = st.get();
    s.now += dt;
    const now = s.now;
    const dbg = debugT.get();

    if (dbg === -3 && s.auto === 1) {
      s.auto = 0;
      s.mode = REST;
      s.x = geo.hx;
      s.y = geo.hy;
      s.c = 0;
      s.spin = 0;
      s.sc = 1;
      s.sink = 0;
      s.toast = 0;
      s.lift = 0;
    }
    if (auto && dbg !== -3 && s.auto === 0 && s.mode === REST && now - s.touchAt > 2 && s.c < 0.001) {
      s.auto = 1;
      s.t = 0;
    }
    if (dbg >= 0 && s.auto === 0) s.auto = 1;

    if (s.auto === 1) {
      let t = s.t;
      if (dbg >= 0) t = dbg;
      else if (dbg !== -2) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
      }
      s.t = t;
      const p = script(t, geo);
      s.vx = (p.x - s.x) / dt;
      s.vy = (p.y - s.y) / dt;
      s.x = p.x;
      s.y = p.y;
      s.c = p.c;
      s.spin = p.spin;
      s.sc = p.sc;
      s.sink = p.sink;
      s.wobble = p.wobble;
      s.toast = p.toast;
      s.lift = p.lift;
      s.gone = t >= S.release + AFTER_RELEASE.sink[0] && t < S.undo + 0.3 ? 1 : 0;
    } else {
      if (s.mode === DRAG) {
        // the note follows the finger; pulling it down crumples it
        const tx = s.sx;
        const ty = s.sy;
        const n = Math.max(1, Math.ceil(dt / (1 / 240)));
        for (let i = 0; i < n; i++) {
          const a = springTo(s.x, s.vx, tx, 0.12, 0.95, dt / n);
          s.x = a[0];
          s.vx = a[1];
          const b = springTo(s.y, s.vy, ty, 0.12, 0.95, dt / n);
          s.y = b[0];
          s.vy = b[1];
        }
        const target = Math.min(0.92, Math.max(0, (s.y - geo.hy) / DRAG_FULL));
        s.c += (target - s.c) * (1 - Math.exp(-dt * 14));
        s.spin = (s.x - geo.hx) * 0.002;
        s.lift += (1 - s.lift) * (1 - Math.exp(-dt * 16));
        const step = Math.floor(s.c * 8);
        if (step !== s.lastStep) {
          s.lastStep = step;
          scheduleOnRN(buzz, 0);
          scheduleOnRN(cue, 'rustle');
        }
      } else if (s.mode === BACK) {
        const n = Math.max(1, Math.ceil(dt / (1 / 240)));
        for (let i = 0; i < n; i++) {
          const a = springTo(s.x, s.vx, geo.hx, 0.5, 0.75, dt / n);
          s.x = a[0];
          s.vx = a[1];
          const b = springTo(s.y, s.vy, geo.hy, 0.5, 0.75, dt / n);
          s.y = b[0];
          s.vy = b[1];
          const c = springTo(s.c, s.vc, 0, 0.6, 0.6, dt / n);
          s.c = Math.max(0, c[0]);
          s.vc = c[1];
        }
        s.spin *= 1 - Math.min(1, dt * 8);
        s.lift *= 1 - Math.min(1, dt * 10);
        if (Math.abs(s.x - geo.hx) + Math.abs(s.y - geo.hy) < 0.4 && s.c < 0.002) {
          s.mode = REST;
          s.c = 0;
          s.x = geo.hx;
          s.y = geo.hy;
        }
      } else if (s.mode === GONE) {
        const tr = now - s.relAt;
        const c = s.rc + (1 - s.rc) * springStep(tr, AFTER_RELEASE.crush.duration, AFTER_RELEASE.crush.ratio);
        const q = easeInOut(seg(tr, AFTER_RELEASE.toss[0], AFTER_RELEASE.toss[1]));
        const p = tossPos(q, s.rx, s.ry, geo.bx, geo.by);
        s.x = p[0];
        s.y = p[1];
        s.c = reduced ? 1 : c;
        s.spin = s.rspin + 7 * q;
        s.sc = 1 - 0.18 * q;
        s.sink = easeInOut(seg(tr, AFTER_RELEASE.sink[0], AFTER_RELEASE.sink[1]));
        const land = tr - AFTER_RELEASE.toss[1];
        s.wobble = land > 0 ? Math.exp(-land * 5) * Math.sin(land * 26) : 0;
        s.toast = smooth(AFTER_RELEASE.toast, AFTER_RELEASE.toast + 0.3, tr);
        s.lift = 1 - smooth(0, 0.3, tr);
        s.gone = tr >= AFTER_RELEASE.sink[0] ? 1 : 0;
        if (tr - dt < AFTER_RELEASE.toss[0] && tr >= AFTER_RELEASE.toss[0]) scheduleOnRN(cue, 'toss');
        if (tr - dt < AFTER_RELEASE.toss[1] && tr >= AFTER_RELEASE.toss[1]) {
          scheduleOnRN(buzz, 1);
          scheduleOnRN(cue, 'bin');
        }
        // for the demo: with no Undo the note comes back by itself after a while
        if (tr > 7) {
          s.mode = UNDO;
          s.undoAt = now;
        }
      } else if (s.mode === UNDO) {
        const tu = now - s.undoAt;
        const q = easeInOut(seg(tu, AFTER_UNDO.fly[0], AFTER_UNDO.fly[1]));
        const p = tossPos(q, geo.bx, geo.by, geo.hx, geo.hy);
        const open = springStep(tu - AFTER_UNDO.open.start, AFTER_UNDO.open.duration, AFTER_UNDO.open.ratio);
        s.x = p[0];
        s.y = p[1];
        s.sink = 1 - easeOut(seg(tu, AFTER_UNDO.rise[0], AFTER_UNDO.rise[1]));
        s.c = reduced ? (tu > 0.5 ? 0 : 1) : Math.max(0, 1 - open);
        s.spin = (s.rspin + 7) * (1 - q) * (1 - smooth(0, 1, open));
        s.sc = 0.82 + 0.18 * q;
        s.toast = 1 - smooth(0, 0.25, tu);
        s.wobble = 0;
        s.gone = tu < 0.3 ? 1 : 0;
        if (tu - dt < AFTER_UNDO.open.start && tu >= AFTER_UNDO.open.start) scheduleOnRN(cue, 'open');
        if (tu > AFTER_UNDO.open.start + 1.2) {
          s.mode = REST;
          s.c = 0;
          s.spin = 0;
          s.sc = 1;
          s.x = geo.hx;
          s.y = geo.hy;
        }
      }
    }
    if (dbg >= 0) debugT.set(-2);
    tick.set(tick.get() + 1);
    scheduleOnRNIfChanged(s);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  // ------------------------------------------------------------------ actions
  const interrupt = (s: St) => {
    'worklet';
    s.touchAt = s.now;
    if (s.auto === 0) return;
    s.auto = 0;
    const t = s.t;
    if (t >= S.undo && t < S.rest) {
      s.mode = UNDO;
      s.undoAt = s.now - (t - S.undo);
      s.rspin = 0.12;
    } else if (t >= S.release && t < S.rest) {
      s.mode = GONE;
      s.relAt = s.now - (t - S.release);
      s.rx = geo.hx + 18;
      s.ry = geo.hy + 210;
      s.rc = 210 / DRAG_FULL;
      s.rspin = 0.12;
    } else if (t >= S.grab[0] && t < S.rest) {
      s.mode = BACK;
    } else s.mode = REST;
  };
  const undo = () => {
    st.modify((s) => {
      'worklet';
      interrupt(s);
      if (s.mode === GONE) {
        s.mode = UNDO;
        s.undoAt = s.now;
      }
      return s;
    });
    cue('pop');
  };

  const pan = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      const s = st.get();
      interrupt(s);
    })
    .onStart(() => {
      const s = st.get();
      if (s.mode === REST || s.mode === BACK) {
        s.mode = DRAG;
        s.sx = s.x;
        s.sy = s.y;
        s.lastStep = 0;
      }
    })
    .onUpdate((e) => {
      const s = st.get();
      s.touchAt = s.now;
      if (s.mode !== DRAG) return;
      s.sx = geo.hx + e.translationX;
      s.sy = geo.hy + Math.max(-40, e.translationY);
    })
    .onEnd((e) => {
      const s = st.get();
      s.touchAt = s.now;
      if (s.mode !== DRAG) return;
      if (s.c > 0.33 || e.velocityY > 900) {
        s.mode = GONE;
        s.relAt = s.now;
        s.rx = s.x;
        s.ry = s.y;
        s.rc = s.c;
        s.rspin = s.spin;
      } else s.mode = BACK;
    })
    .onFinalize(() => {
      const s = st.get();
      if (s.mode === DRAG) s.mode = BACK;
    });

  // ------------------------------------------------------------------ derived drawing
  const mesh = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const sink = s.sink;
    // dropping into the bin: a little smaller and lower each moment
    const y = s.y + 26 * sink;
    const sc = s.sc * (1 - 0.25 * sink);
    const m = crumple(Math.max(0, Math.min(1, s.c)), s.spin, sc * (1 + 0.025 * s.lift), s.x, y, NW, NH);
    const pts = [];
    const cols = [];
    for (let i = 0; i < m.light.length; i++) {
      pts.push(vec(m.pos[i * 2], m.pos[i * 2 + 1]));
      // the creases: a shade over the paper where a facet turns away from the light
      const b = Math.max(0, Math.min(1, m.light[i]));
      // warm shade: paper in shadow goes toward umber, not grey
      cols.push(`rgb(${Math.round(255 * Math.pow(b, 0.85))},${Math.round(255 * b)},${Math.round(255 * Math.pow(b, 1.25))})`);
    }
    return { pts, cols };
  });
  const meshPts = useDerivedValue(() => mesh.get().pts);
  const meshCols = useDerivedValue(() => mesh.get().cols);
  // into the bin: the ball is seen only inside the opening once it drops
  const inBin = useDerivedValue(() => {
    tick.get();
    return st.get().sink > 0.02 ? 1 : 0;
  });
  const outBin = useDerivedValue(() => 1 - inBin.get());
  // the shadow: a soft sheet's shadow that gathers into a ball's
  const shadow = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    const c = Math.max(0, Math.min(1, s.c));
    const e = c * c * (3 - 2 * c);
    const w = (NW + (88 - NW) * e) * s.sc;
    const h = (NH + (70 - NH) * e) * s.sc;
    const air = s.lift * 10 + (s.mode === GONE ? 0 : 0);
    const y = s.y + 10 + air + (1 - e) * 4;
    return Skia.RRectXY(Skia.XYWHRect(s.x - w / 2 + 4, y - h / 2, w, h), 6 + 40 * e, 6 + 40 * e);
  });
  const shadowA = useDerivedValue(() => {
    tick.get();
    const s = st.get();
    return (0.32 - 0.12 * s.lift) * (1 - s.sink) * (s.toast > 0.01 && s.sink > 0.5 ? 0 : 1);
  });
  const shadowBlur = useDerivedValue(() => 10 + 12 * st.get().lift);
  const binT = useDerivedValue(() => {
    tick.get();
    const w = st.get().wobble;
    return [{ translateX: BCX }, { translateY: BY + BH }, { rotate: w * 0.06 }, { translateX: -BCX }, { translateY: -(BY + BH) }];
  });
  const noteBA = useDerivedValue(() => {
    tick.get();
    // the note underneath lifts a touch when it is the top one
    return 1;
  });

  // ------------------------------------------------------------------ RN layers
  const toastStyle = useAnimatedStyle(() => {
    tick.get();
    const k = st.get().toast;
    return { opacity: k, transform: [{ translateY: 16 * (1 - k) }] };
  });
  const hitStyle = useAnimatedStyle(() => {
    tick.get();
    const s = st.get();
    return { transform: [{ translateX: s.x - NW / 2 }, { translateY: s.y - NH / 2 }] };
  });

  const ready = fontsOk && size !== null;

  return (
    <View style={styles.screen} onLayout={(e) => setSize({ width: e.nativeEvent.layout.width, height: e.nativeEvent.layout.height })}>
      {size && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {desk && <Image image={desk} x={0} y={0} width={W} height={H} fit="cover" />}
          <Rect x={0} y={0} width={W} height={H} color="rgba(40,30,20,0.06)" />

          {/* the note underneath */}
          <Group transform={[{ translateX: HX }, { translateY: HY + 6 }, { rotate: 0.035 }]} opacity={noteBA}>
            <RoundedRect x={-NW / 2 + 4} y={-NH / 2 + 8} width={NW - 8} height={NH} r={6} color="rgba(60,40,20,0.28)">
              <BlurMask blur={10} style="normal" />
            </RoundedRect>
            {noteB && <Image image={noteB} x={-NW / 2} y={-NH / 2} width={NW} height={NH} fit="fill" />}
          </Group>

          {/* the bin, the ball inside its opening, the bin's front rim over the ball */}
          <Group transform={binT}>
            <Oval x={BX + 8} y={BY + BH - 18} width={BW - 16} height={26} color="rgba(50,35,20,0.35)">
              <BlurMask blur={8} style="normal" />
            </Oval>
            {bin && <Image image={bin} x={BX} y={BY} width={BW} height={BH} fit="fill" />}
            <Group opacity={inBin} clip={Skia.Path.MakeFromSVGString(ovalPath(BCX, BCY + 2, BRX * 0.94, BRY * 0.9)) ?? undefined}>
              {noteA && (
                <>
                  <Vertices vertices={meshPts} textures={tex} indices={idx} mode="triangles">
                    <ImageShader image={noteA} />
                  </Vertices>
                  <Group blendMode="multiply">
                  <Vertices vertices={meshPts} colors={meshCols} indices={idx} mode="triangles" />
                </Group>
                </>
              )}
              <Rect x={BX} y={BY} width={BW} height={BH} color="rgba(10,10,10,0.25)" />
            </Group>
          </Group>

          {/* the note on top: its shadow, then the paper mesh */}
          <RoundedRect rect={shadow} color="rgba(60,40,20,1)" opacity={shadowA}>
            <BlurMask blur={shadowBlur} style="normal" />
          </RoundedRect>
          <Group opacity={outBin}>
            {noteA && (
              <>
                <Vertices vertices={meshPts} textures={tex} indices={idx} mode="triangles">
                  <ImageShader image={noteA} />
                </Vertices>
                <Group blendMode="multiply">
                  <Vertices vertices={meshPts} colors={meshCols} indices={idx} mode="triangles" />
                </Group>
              </>
            )}
          </Group>
        </Canvas>
      )}

      {ready && (
        <>
          <View style={[styles.head, { top: top + 12 }]} pointerEvents="none">
            <Text style={styles.eyebrow} maxFontSizeMultiplier={1.3}>NOTES · TODAY</Text>
            <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.2}>Notes</Text>
            <Text style={styles.sub} maxFontSizeMultiplier={1.3}>Pull a note into the bin to delete it</Text>
          </View>

          <GestureDetector gesture={pan}>
            <Animated.View
              style={[styles.hit, { width: NW, height: NH }, hitStyle]}
              pointerEvents={gone ? 'none' : 'auto'}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Note: Saturday market"
              accessibilityHint="Drag down into the bin to delete"
              accessibilityActions={[{ name: 'delete', label: 'Delete note' }]}
              onAccessibilityAction={() => st.modify((s) => {
                'worklet';
                interrupt(s);
                s.mode = GONE;
                s.relAt = s.now;
                s.rx = s.x;
                s.ry = s.y;
                s.rc = s.c;
                s.rspin = 0;
                return s;
              })}
            />
          </GestureDetector>

          <Animated.View style={[styles.toast, { top: top + 96, left: 20, right: 20 }, toastStyle]} pointerEvents={gone ? 'box-none' : 'none'}>
            <Text style={styles.toastText}>Note deleted</Text>
            <Pressable onPress={undo} hitSlop={10} style={({ pressed }) => [styles.undo, pressed && styles.undoPressed]} accessibilityRole="button" accessibilityLabel="Undo">
              <Text style={styles.undoText}>Undo</Text>
            </Pressable>
          </Animated.View>
        </>
      )}
    </View>
  );
}

function ovalPath(cx: number, cy: number, rx: number, ry: number) {
  return `M ${cx - rx} ${cy} A ${rx} ${ry} 0 1 0 ${cx + rx} ${cy} A ${rx} ${ry} 0 1 0 ${cx - rx} ${cy} Z`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#D9C8B0', overflow: 'hidden' },
  head: { position: 'absolute', left: 24, right: 24 },
  eyebrow: { fontFamily: 'CTD-Mono', fontSize: 11, letterSpacing: 1.6, color: C.mute },
  title: { fontFamily: 'CTD-Archivo-ExtraBold', fontSize: 36, lineHeight: 40, color: C.head, letterSpacing: -0.7, marginTop: 8 },
  sub: { fontFamily: 'CTD-Archivo-Medium', fontSize: 15, lineHeight: 20, color: C.mute, marginTop: 2 },
  hit: { position: 'absolute', left: 0, top: 0 },
  toast: { position: 'absolute', height: 52, borderRadius: 26, backgroundColor: 'rgba(34,30,26,0.92)', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 20, paddingRight: 8 },
  toastText: { fontFamily: 'CTD-Archivo-SemiBold', fontSize: 15, color: '#F6EFE6' },
  undo: { height: 36, paddingHorizontal: 16, borderRadius: 18, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  undoPressed: { transform: [{ scale: 0.96 }], backgroundColor: 'rgba(255,255,255,0.22)' },
  undoText: { fontFamily: 'CTD-Archivo-SemiBold', fontSize: 15, color: '#FFD7A3' },
});

export { LOOP };
