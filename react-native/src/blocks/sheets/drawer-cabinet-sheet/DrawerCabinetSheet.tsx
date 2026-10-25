import { Atlas, Canvas, FilterMode, Group, Image, LinearGradient, MipmapMode, Rect, Skia, vec, type SkRect, type SkRSXform } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { INK, PANEL, SHADOW_PAD, bakeBoard, bakeDesk, bakeItems, bakeSheet, makeItems, makeLayout } from './drawerArt';
import {
  CLEAR_AT, FLY_FOR, LIFT_FOR, LOOP, PICK_AT, RATE, autoOpen, buildTable, clamp01, easeOutCubic, pickPose, stepItem, tableAt,
} from './drawerMotion';

/*
 Drawer Cabinet Sheet — "Drawers · Office supplies". The bottom sheet is a wooden drawer: an oak
 front with a brass pull that slides out from under the cabinet's top board, and behind it the
 box of supplies — pens in a felt tray, paper clips, binder clips, an eraser, a pencil, tape and
 sticky notes — every one on its own spring, so they rattle and slide as the drawer starts and
 stops. Drag it to half or full (detents, rubber band past full); let go near closed and the
 soft-close damper eases it home. Tap a supply to lift it out onto the desk; tap it there to put it
 back.

 autoPlay (LOOP = 8 s, drawerMotion.ts): rest 0–1.2 · pulled to half 1.2–2.2 · to full 2.8–3.6 ·
 the blue pen picked 4.0–5.0 · soft close 5.6–7.0 · the desk clears 7.0–7.5 · rest. A touch takes
 over at once; 2 s after the last, the drawer soft-closes, what is on the desk flies back (0.9 s)
 and the loop restarts. Reduced motion: no loop, no rattle; the drawer moves without overshoot
 and picked things appear on the desk.

 Drawing: one Canvas — the desk image, the drawer image (moved by a transform, clipped at the
 board), the supplies in the drawer and on the desk as two pairs of <Atlas> draws (shadows,
 faces), the board image. Sprites and transforms are written on the UI thread each frame.
*/

type Props = {
  /** Opens the drawer to half and full, picks a pen onto the desk, soft-closes. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
const ATLAS_W = 300;
const SOFT = Easing.bezier(0.12, 0.8, 0.2, 1);
/** The auto loop's pick: the blue fineliner. */
const PEN = 0;

type Sim = { place: number[]; start: number[]; from: number[][]; slot: number[]; lag: number[][]; prevOpen: number; prevV: number; acc: number; chipAt: number; idle: boolean };

export function DrawerCabinetSheet({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const [chipName, setChipName] = useState('Blue fineliner');

  const art = useMemo(() => {
    const L = makeLayout(size.width, size.height, insets.top, insets.bottom);
    const items = makeItems(L.iw);
    const px = Math.min(3, PixelRatio.get());
    const baked = bakeItems(items, px, ATLAS_W);
    return {
      L,
      items,
      px,
      ...baked,
      desk: bakeDesk(L, px),
      sheet: bakeSheet(L, px),
      board: bakeBoard(L, px),
      table: buildTable(items.map((it) => it.spring), L.half, L.full),
    };
  }, [size.width, size.height, insets.top, insets.bottom]);
  const { L, items, px, cells, table } = art;
  const n = items.length;

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const open = useSharedValue(0);
  const dragging = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const sim = useSharedValue<Sim>({ place: [], start: [], from: [], slot: [], lag: [], prevOpen: 0, prevV: 0, acc: 0, chipAt: -10, idle: false });
  const shown = useSharedValue({ open: 0, top: 1, chip: 0 });
  const inSprites = useSharedValue<SkRect[]>([]);
  const inXf = useSharedValue<SkRSXform[]>([]);
  const inShXf = useSharedValue<SkRSXform[]>([]);
  const outSprites = useSharedValue<SkRect[]>([]);
  const outXf = useSharedValue<SkRSXform[]>([]);
  const outShXf = useSharedValue<SkRSXform[]>([]);

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  function resetSim() {
    'worklet';
    const s = sim.get();
    s.place = []; s.start = []; s.from = []; s.slot = []; s.lag = [];
    for (let i = 0; i < n; i++) {
      s.place.push(0); s.start.push(0); s.from.push([0, 0, 0]); s.slot.push(0); s.lag.push([0, 0]);
    }
    s.prevOpen = open.get(); s.prevV = 0; s.acc = 0; s.chipAt = -10; s.idle = false;
  }

  /** Item i's place in the drawer on screen, for an opening and a lag. */
  function homeOf(i: number, o: number, lag: number, out: number[]) {
    'worklet';
    const it = items[i];
    out[0] = L.ix + it.x;
    out[1] = L.boardY - o + it.y + lag;
    out[2] = it.rot + (lag / it.spring.slack) * it.spring.turn;
  }

  /** Where item i lands on the desk: long things stacked on the right, small ones in a row. */
  function deskOf(i: number, slot: number, out: number[]) {
    'worklet';
    const it = items[i];
    const long = it.w > 100;
    if (long) {
      out[0] = L.W * 0.6; out[1] = L.top + 172 + slot * 30; out[2] = -0.12 + slot * 0.05;
    } else {
      out[0] = 70 + (slot % 4) * 72; out[1] = L.top + 242 + Math.floor(slot / 4) * 54; out[2] = 0.2 - slot * 0.1;
    }
  }

  function nextSlot(s: Sim, i: number) {
    'worklet';
    const long = items[i].w > 100;
    let k = 0;
    for (let j = 0; j < n; j++) if (j !== i && s.place[j] !== 0 && s.place[j] !== 3 && items[j].w > 100 === long) k++;
    return k;
  }

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    sim.get().idle = false;
    if (mode.get() === MODE_USER) return;
    if (mode.get() === MODE_AUTO) {
      const t = clock.get();
      resetSim();
      const s = sim.get();
      open.set(autoOpen(t, L.half, L.full));
      s.prevOpen = open.get();
      for (let i = 0; i < n; i++) {
        s.lag[i][0] = tableAt(table[i], t);
        s.lag[i][1] = (tableAt(table[i], t) - tableAt(table[i], t - 1 / RATE)) * RATE;
      }
      if (t >= PICK_AT && t < CLEAR_AT) {
        const f = [0, 0, 0];
        homeOf(PEN, L.full, 0, f);
        s.from[PEN] = f;
        s.place[PEN] = t < PICK_AT + LIFT_FOR + FLY_FOR ? 1 : 2;
        s.start[PEN] = now.get() - (t - PICK_AT);
        s.slot[PEN] = 0;
      }
    }
    mode.set(MODE_USER);
  }

  function pick(i: number) {
    'worklet';
    const s = sim.get();
    const f = [0, 0, 0];
    homeOf(i, open.get(), s.lag[i][0], f);
    s.from[i] = f;
    s.slot[i] = nextSlot(s, i);
    s.place[i] = 1;
    s.start[i] = now.get() - (reduced ? 10 : 0);
    scheduleOnRN(setChipName, items[i].name);
    scheduleOnRN(feel, 0);
  }

  function putBack(i: number, pose: number[]) {
    'worklet';
    const s = sim.get();
    s.from[i] = [pose[0], pose[1], pose[2]];
    s.place[i] = 3;
    s.start[i] = now.get() - (reduced ? 10 : 0);
  }

  useFrameCallback((frame) => {
    const s = sim.get();
    if (s.place.length !== n) resetSim();
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const m = mode.get();
    let o = 0;
    let topAlpha = 1;
    let chip = 0;
    const lags: number[] = [];
    const out: number[][] = [];
    const tmp = [0, 0, 0, 0, 1];
    const home = [0, 0, 0];
    let moving = true;

    if (m === MODE_AUTO) {
      moving = auto;
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      o = autoOpen(t, L.half, L.full);
      for (let i = 0; i < n; i++) lags.push(reduced ? 0 : tableAt(table[i], t));
      if (t >= PICK_AT && t < CLEAR_AT + 0.5) {
        homeOf(PEN, L.full, 0, home);
        const d = [0, 0, 0];
        deskOf(PEN, 0, d);
        pickPose(home[0], home[1], home[2], d[0], d[1], d[2], t - PICK_AT, tmp);
        out.push([PEN, tmp[0], tmp[1], tmp[2], tmp[3], tmp[4]]);
        topAlpha = 1 - easeOutCubic((t - CLEAR_AT) / 0.5);
      }
      chip = clamp01((t - 4.95) / 0.25) * (1 - clamp01((t - 6.8) / 0.25));
    } else {
      if (m === MODE_USER && auto && !dragging.get() && at - lastTouch.get() > IDLE) {
        // Back to rest: soft-close, everything on the desk flies home.
        open.set(withTiming(0, { duration: RETURN * 1000 - 50, easing: SOFT }));
        for (let i = 0; i < n; i++) {
          if (s.place[i] === 1 || s.place[i] === 2) {
            const d = [0, 0, 0];
            deskOf(i, s.slot[i], d);
            putBack(i, d);
          }
        }
        returnAt.set(at);
        mode.set(MODE_RETURN);
      }
      o = open.get();
      // The drawer's acceleration, smoothed, shakes the springs (four substeps a frame).
      const v = (o - s.prevOpen) / Math.max(dt, 0.001);
      const a = (v - s.prevV) / Math.max(dt, 0.001);
      s.acc += (a - s.acc) * Math.min(1, dt * 30);
      if (s.prevOpen > 0.5 && o <= 0.5 && v < 0 && m === MODE_USER) scheduleOnRN(feel, 1);
      s.prevOpen = o;
      s.prevV = v;
      for (let i = 0; i < n; i++) {
        if (!reduced) for (let k = 0; k < 4; k++) stepItem(items[i].spring, s.lag[i], s.acc, dt / 4);
        lags.push(s.lag[i][0]);
      }
      for (let i = 0; i < n; i++) {
        const p = s.place[i];
        if (p === 0) continue;
        const u = at - s.start[i];
        const d = [0, 0, 0];
        deskOf(i, s.slot[i], d);
        if (p === 1 || p === 2) {
          pickPose(s.from[i][0], s.from[i][1], s.from[i][2], d[0], d[1], d[2], u, tmp);
          if (p === 1 && u >= LIFT_FOR + FLY_FOR) {
            s.place[i] = 2;
            s.chipAt = at;
          }
        } else {
          homeOf(i, o, s.lag[i][0], home);
          pickPose(s.from[i][0], s.from[i][1], s.from[i][2], home[0], home[1], home[2], u, tmp);
          if (u >= LIFT_FOR + FLY_FOR) {
            s.place[i] = 0;
            s.lag[i][0] = 0;
            s.lag[i][1] = 0;
            continue;
          }
        }
        out.push([i, tmp[0], tmp[1], tmp[2], tmp[3], tmp[4]]);
      }
      chip = clamp01((at - s.chipAt) / 0.25) * (1 - clamp01((at - s.chipAt - 2.4) / 0.25));
      // Still when the drawer, the springs and every flight have settled.
      moving = mode.get() === MODE_RETURN || dragging.get() === 1 || Math.abs(v) > 0.05 || Math.abs(s.acc) > 0.5 || (at - s.chipAt > 0 && at - s.chipAt < 2.8);
      for (let i = 0; i < n; i++) {
        if (Math.abs(s.lag[i][0]) > 0.02 || Math.abs(s.lag[i][1]) > 0.05 || s.place[i] === 1 || s.place[i] === 3) moving = true;
        if (s.place[i] === 2 && at - s.start[i] < LIFT_FOR + FLY_FOR + 0.6) moving = true;
      }
      if (mode.get() === MODE_RETURN && at - returnAt.get() >= RETURN) {
        mode.set(MODE_AUTO);
        clock.set(0);
        open.set(0);
        resetSim();
        scheduleOnRN(setChipName, 'Blue fineliner');
      }
    }

    if (!moving && s.idle) return;
    s.idle = !moving;

    // In the drawer: every item not out on the desk.
    const isOut: boolean[] = [];
    for (let i = 0; i < n; i++) isOut.push(false);
    for (let k = 0; k < out.length; k++) isOut[out[k][0]] = true;
    const sp: SkRect[] = [];
    const xf: SkRSXform[] = [];
    const sh: SkRSXform[] = [];
    for (let i = 0; i < n; i++) {
      if (isOut[i]) continue;
      homeOf(i, o, lags[i], home);
      const c = cells[i];
      sp.push(Skia.XYWHRect(c.x * px, c.y * px, c.w * px, c.h * px));
      xf.push(Skia.RSXformFromRadians(1 / px, home[2], home[0], home[1], (c.w * px) / 2, (c.h * px) / 2));
      sh.push(Skia.RSXformFromRadians(1 / px, home[2], home[0] + 1.5, home[1] + 2.5, (c.w * px) / 2, (c.h * px) / 2));
    }
    const osp: SkRect[] = [];
    const oxf: SkRSXform[] = [];
    const osh: SkRSXform[] = [];
    for (let k = 0; k < out.length; k++) {
      const [i, x, y, r, lift, sc] = out[k];
      const c = cells[i];
      osp.push(Skia.XYWHRect(c.x * px, c.y * px, c.w * px, c.h * px));
      oxf.push(Skia.RSXformFromRadians(sc / px, r, x, y, (c.w * px) / 2, (c.h * px) / 2));
      osh.push(Skia.RSXformFromRadians((sc * (1 + 0.06 * lift)) / px, r, x + 3 + 12 * lift, y + 4 + 22 * lift, (c.w * px) / 2, (c.h * px) / 2));
    }
    inSprites.set(sp);
    inXf.set(xf);
    inShXf.set(sh);
    outSprites.set(osp);
    outXf.set(oxf);
    outShXf.set(osh);
    shown.set({ open: o, top: topAlpha, chip });
  });

  // ——— Gestures ———

  /** The topmost item under a point: on the desk first, then in the open part of the drawer. */
  function hitItem(x: number, y: number) {
    'worklet';
    const s = sim.get();
    const pose = [0, 0, 0];
    for (let i = n - 1; i >= 0; i--) {
      if (s.place[i] !== 2) continue;
      deskOf(i, s.slot[i], pose);
      const it = items[i];
      const dx = x - pose[0];
      const dy = y - pose[1];
      const lx = dx * Math.cos(pose[2]) + dy * Math.sin(pose[2]);
      const ly = -dx * Math.sin(pose[2]) + dy * Math.cos(pose[2]);
      if (Math.abs(lx) < it.w / 2 + 10 && Math.abs(ly) < it.h / 2 + 12) return { i, desk: true, pose };
    }
    if (y > L.boardY || y < L.boardY - open.get()) return null;
    for (let i = n - 1; i >= 0; i--) {
      if (s.place[i] !== 0) continue;
      homeOf(i, open.get(), s.lag[i][0], pose);
      const it = items[i];
      const dx = x - pose[0];
      const dy = y - pose[1];
      const lx = dx * Math.cos(pose[2]) + dy * Math.sin(pose[2]);
      const ly = -dx * Math.sin(pose[2]) + dy * Math.cos(pose[2]);
      if (Math.abs(lx) < it.w / 2 + 8 && Math.abs(ly) < it.h / 2 + 10) return { i, desk: false, pose };
    }
    return null;
  }

  function settleTo(target: number, velocity: number) {
    'worklet';
    if (target <= 0) open.set(withTiming(0, { duration: reduced ? 250 : 850, easing: SOFT }));
    else open.set(reduced ? withTiming(target, { duration: 250 }) : withSpring(target, { duration: 500, dampingRatio: 0.72, velocity }));
  }

  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((e, ok) => {
      if (!ok) return;
      touched();
      const h = hitItem(e.x, e.y);
      if (h) {
        if (h.desk) putBack(h.i, h.pose);
        else pick(h.i);
        return;
      }
      const top = L.boardY - open.get() - PANEL;
      if (e.y >= top && e.y <= top + PANEL) {
        settleTo(open.get() < 10 ? L.half : 0, 0);
        scheduleOnRN(feel, 0);
      }
    });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart((e) => {
      const y0 = e.y - e.translationY;
      const top = L.boardY - open.get() - PANEL;
      if (y0 < top - 12 || y0 > L.boardY + 40) return;
      touched();
      dragging.set(1);
      open.set(open.get());
      dragFrom.set(open.get());
    })
    .onUpdate((e) => {
      if (!dragging.get()) return;
      lastTouch.set(now.get());
      let o = dragFrom.get() - e.translationY;
      if (o > L.full) {
        const over = o - L.full;
        o = L.full + (over * 0.55) / (1 + over / 120);
      } else if (o < 0) o *= 0.2;
      open.set(o);
    })
    .onFinalize((e) => {
      if (!dragging.get()) return;
      dragging.set(0);
      lastTouch.set(now.get());
      const o = open.get();
      const aim = o - e.velocityY * 0.18;
      const stops = [0, L.half, L.full];
      let best = 0;
      for (const p of stops) if (Math.abs(aim - p) < Math.abs(aim - best)) best = p;
      settleTo(best, -e.velocityY);
      if (best > 0) scheduleOnRN(feel, 0);
    });

  const gesture = Gesture.Exclusive(pan, tap);

  const sheetT = useDerivedValue(() => [{ translateX: L.sheetX }, { translateY: L.boardY - PANEL - SHADOW_PAD - shown.get().open }]);
  const darkY = useDerivedValue(() => L.boardY - Math.max(0, Math.min(46, shown.get().open)));
  const topAlpha = useDerivedValue(() => shown.get().top);
  const chipStyle = useAnimatedStyle(() => {
    const c = shown.get().chip;
    return { opacity: c, transform: [{ translateY: 8 * (1 - c) }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };
  const sampling = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
  const sheetH = SHADOW_PAD + PANEL + 384;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Supplies drawer. Drag the brass pull to open it; tap a supply to put it on the desk.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Image image={art.desk} x={0} y={0} width={L.W} height={L.H} fit="fill" />
            <Group clip={{ x: 0, y: 0, width: L.W, height: L.boardY }}>
              <Group transform={sheetT}>
                <Image image={art.sheet} x={0} y={0} width={L.sheetW} height={sheetH} fit="fill" />
              </Group>
              <Atlas image={art.shadows} sprites={inSprites} transforms={inShXf} sampling={sampling} />
              <Atlas image={art.faces} sprites={inSprites} transforms={inXf} sampling={sampling} />
              {/* Inside the cabinet it is darker: the last few centimetres before the board. */}
              <Rect x={L.sheetX} y={darkY} width={L.sheetW} height={46}>
                <LinearGradient start={vec(0, L.boardY - 46)} end={vec(0, L.boardY)} colors={['rgba(30,16,4,0)', 'rgba(30,16,4,0.55)']} />
              </Rect>
            </Group>
            <Image image={art.board} x={0} y={L.boardY - 20} width={L.W} height={L.H - L.boardY + 20} fit="fill" />
            <Group opacity={topAlpha}>
              <Group opacity={0.6}>
                <Atlas image={art.shadows} sprites={outSprites} transforms={outShXf} sampling={sampling} />
              </Group>
              <Atlas image={art.faces} sprites={outSprites} transforms={outXf} sampling={sampling} />
            </Group>
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>DRAWERS</Text>
        <Text style={styles.title}>Office supplies</Text>
        <Text style={styles.subtitle}>Top drawer · {n} items · pull to open</Text>
      </View>
      <Animated.View pointerEvents="none" style={[styles.chip, { top: L.matY + 14 }, chipStyle]}>
        <Text style={styles.chipText} numberOfLines={1}>{`${chipName} · on your desk`}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: INK.paper },
  header: { position: 'absolute', left: 22, right: 22, gap: 4 },
  kicker: { color: '#9A6A3A', fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  title: { color: INK.ink, fontSize: 32, fontWeight: '800', letterSpacing: -0.5 },
  subtitle: { color: INK.soft, fontSize: 15, fontWeight: '500' },
  chip: {
    position: 'absolute',
    right: 30,
    maxWidth: 240,
    paddingHorizontal: 12,
    height: 30,
    borderRadius: 15,
    justifyContent: 'center',
    backgroundColor: 'rgba(246,240,228,0.94)',
    boxShadow: '0 1px 2px rgba(20,30,20,0.25), 0 6px 14px rgba(20,30,20,0.18)',
  },
  chipText: { color: INK.ink, fontSize: 13, fontWeight: '600' },
});
