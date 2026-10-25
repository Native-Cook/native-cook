import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawField, drawFinger, drawPlate, drawTileShadow, recordBackdrop, recordDock, recordFaces, TOOL_COLORS } from './magnetSnapDockArt';
import {
  LOOP,
  REST_TOOL,
  TOOLS,
  clickAt,
  loopLayout,
  loopSelection,
  magnetLayout,
  makeGeo,
  plateX,
} from './magnetSnapDockMotion';
import { MagnetSnapDockPanel, TOOL_NAMES } from './MagnetSnapDockPanel';

/*
 Magnet Snap Dock — "Studio", a drawing app whose tool dock is magnetic. Bring a finger near it
 and the six enamel tool tiles lean toward it, the nearest growing to 1.35× while its neighbours
 make room; dashed field lines flow from the tiles to the fingertip. Let go over a tool and it
 clicks down onto the dock's plate (a spring with a little overshoot), the plate slides under it
 and the panel above swaps to that tool's settings. Move away and the tiles spring home.

 Two Skia canvases: a static one under the panel (backdrop and artboard, recorded once) and a live
 one over it (dock, plate, tiles, field lines, fingertip). The six tile faces are pictures recorded
 once; per frame: positions, shadows and up to 18 dashed curves. autoPlay: LOOP = 8 s, analytic
 (magnetSnapDockMotion.ts). React renders only when the selected tool changes (accessibility).

 Modes: 0 loop · 1 finger down · 2 released (tiles spring home) · 3 back to Pen and the loop
 (2 s after the last touch, 0.9 s).
 */

type Props = {
  /** Plays the 8 s demo: approach, sweep the dock, pick Brush, then tap Pen again. */
  autoPlay?: boolean;
};

const BACKGROUND = '#16181c';
const INK = '#f1ede6';
const INK_SOFT = '#9a9ca3';
const RETURN_AFTER = 2;
const RETURN_FOR = 0.9;
const SPRING_W = 16;
const SPRING_Z = 0.72;
const TYPE_FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });

function feel(kind: 'tick' | 'snap') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MagnetSnapDock({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [selected, setSelected] = useState(REST_TOOL);
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const font = useMemo(() => matchFont({ fontFamily: TYPE_FAMILY, fontSize: 19, fontWeight: '700' }), []);
  const art = useMemo(() => (g ? { backdrop: recordBackdrop(g), dock: recordDock(g), faces: recordFaces(font) } : null), [g, font]);

  const now = useSharedValue(-1);
  const loopStart = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 2);
  /** Finger: [x, y, strength, target strength]. */
  const finger = useSharedValue<number[]>([0, 0, 0, 0]);
  /** Tiles under touch: positions and velocities [x×6, y×6, scale×6, vx×6, vy×6, vs×6]. */
  const tiles = useSharedValue<number[]>([]);
  /** What is drawn: [x×6, y×6, scale×6, pull×6, finger x, finger y, ring, dash phase]. */
  const scene = useSharedValue<number[]>([]);
  /** [from, to, snap time, clock now] on the clock the scene uses. */
  const sel = useSharedValue<number[]>([REST_TOOL, REST_TOOL, -10, 0]);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const nearest = useSharedValue(-1);

  useFrameCallback((info) => {
    if (!g) return;
    const t0 = info.timestamp / 1000;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (now.get() < 0) loopStart.set(t0);
    now.set(t0);
    const x = [0, 0, 0, 0, 0, 0];
    const y = [0, 0, 0, 0, 0, 0];
    const sc = [0, 0, 0, 0, 0, 0];
    const f = [0, 0, 0, 0, 0, 0];
    const m = mode.get();
    if (m === 0) {
      const t = (t0 - loopStart.get()) % LOOP;
      const fp = loopLayout(g, t, x, y, sc, f);
      const s = loopSelection(t);
      sel.set([s[0], s[1], s[2], t]);
      scene.set([...x, ...y, ...sc, ...f, fp[0], fp[1], fp[3], -((t * 36) % 12)]);
      return;
    }
    // Touch: the finger's presence eases in and out; the tiles ride springs to the magnet layout.
    const fg = finger.get().slice();
    fg[2] += (fg[3] - fg[2]) * (1 - Math.exp(-dt / 0.08));
    finger.set(fg);
    const tx = [0, 0, 0, 0, 0, 0];
    const ty = [0, 0, 0, 0, 0, 0];
    const ts = [0, 0, 0, 0, 0, 0];
    magnetLayout(g, fg[0], fg[1], reduced ? 0 : fg[2], tx, ty, ts, f);
    const st = tiles.get().slice();
    if (st.length !== 36) {
      for (let i = 0; i < TOOLS; i++) {
        st[i] = g.xs[i];
        st[6 + i] = g.cy;
        st[12 + i] = 1;
      }
      for (let i = 18; i < 36; i++) st[i] = 0;
    }
    const h = dt / 2;
    for (let k = 0; k < 2; k++) {
      for (let i = 0; i < TOOLS; i++) {
        const targets = [tx[i], ty[i], ts[i]];
        for (let a = 0; a < 3; a++) {
          const pi = a * 6 + i;
          const vi = 18 + a * 6 + i;
          st[vi] += (-SPRING_W * SPRING_W * (st[pi] - targets[a]) - 2 * SPRING_Z * SPRING_W * st[vi]) * h;
          st[pi] += st[vi] * h;
        }
      }
    }
    tiles.set(st);
    for (let i = 0; i < TOOLS; i++) {
      x[i] = st[i];
      y[i] = st[6 + i];
      sc[i] = st[12 + i];
    }
    const s = sel.get();
    if (m === 2 && auto && t0 - lastTouch.get() > RETURN_AFTER) {
      mode.set(3);
      returnAt.set(t0);
      if (s[1] !== REST_TOOL) sel.set([s[1], REST_TOOL, t0, t0]);
    } else if (m === 3 && t0 - returnAt.get() > RETURN_FOR) {
      mode.set(0);
      loopStart.set(t0);
      tiles.set([]);
    }
    const cur = sel.get();
    sel.set([cur[0], cur[1], cur[2], t0]);
    scene.set([...x, ...y, ...sc, ...f, fg[0], fg[1], fg[2], -((t0 * 36) % 12)]);
  });

  useAnimatedReaction(
    () => sel.get()[1],
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setSelected, v);
    },
  );

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    const sn = scene.get();
    if (!g || !art || sn.length !== 28) return rec.finishRecordingAsPicture();
    canvas.drawPicture(art.dock);
    const s = sel.get();
    const clock = s[3];
    drawPlate(canvas, g, reduced ? g.xs[s[1]] : plateX(g, s, clock), TOOL_COLORS[s[1]]);
    const xs = sn.slice(0, 6);
    const ys = sn.slice(6, 12);
    const scs = sn.slice(12, 18);
    const fs = sn.slice(18, 24);
    const click = reduced ? [0, 1] : clickAt(clock - s[2]);
    for (let i = 0; i < TOOLS; i++) {
      if (i === s[1]) {
        ys[i] += click[0];
        scs[i] *= click[1];
      }
      drawTileShadow(canvas, xs[i], ys[i], scs[i], Math.max(0, g.cy - ys[i]) + (scs[i] - 1) * 24);
    }
    drawField(canvas, xs, ys, scs, fs, sn[24], sn[25], sn[27]);
    for (let i = 0; i < TOOLS; i++) {
      canvas.save();
      canvas.translate(xs[i], ys[i]);
      canvas.scale(scs[i], scs[i]);
      canvas.drawPicture(art.faces[i]);
      canvas.restore();
    }
    drawFinger(canvas, sn[24], sn[25], sn[26]);
    return rec.finishRecordingAsPicture();
  });

  const gesture = useMemo(() => {
    if (!g) return Gesture.Pan();
    function nearestTile(x: number) {
      'worklet';
      let best = 0;
      for (let i = 1; i < TOOLS; i++) if (Math.abs(g!.xs[i] - x) < Math.abs(g!.xs[best] - x)) best = i;
      return best;
    }
    function takeOver() {
      'worklet';
      if (mode.get() !== 0) return;
      const sn = scene.get();
      const st: number[] = [];
      for (let i = 0; i < 18; i++) st.push(sn[i] ?? 0);
      for (let i = 18; i < 36; i++) st.push(0);
      tiles.set(st);
      const t = (now.get() - loopStart.get()) % LOOP;
      const s = loopSelection(t);
      sel.set([s[0], s[1], now.get() - (t - s[2]), now.get()]);
      finger.set([sn[24] ?? 0, sn[25] ?? 0, sn[26] ?? 0, 0]);
    }
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        takeOver();
        mode.set(1);
        const fg = finger.get();
        // Touches arrive relative to the touch area, which starts at the artboard.
        finger.set([e.x, e.y + g.boardTop, fg[2], 1]);
        nearest.set(nearestTile(e.x));
        lastTouch.set(now.get());
      })
      .onUpdate((e) => {
        const fg = finger.get();
        const y = e.y + g.boardTop;
        finger.set([e.x, y, fg[2], 1]);
        const n = nearestTile(e.x);
        if (n !== nearest.get() && y > g.cy - 170) {
          nearest.set(n);
          scheduleOnRN(feel, 'tick');
        }
        lastTouch.set(now.get());
      })
      .onFinalize((e) => {
        if (mode.get() !== 1) return;
        const st = tiles.get();
        const n = nearestTile(e.x);
        const cx = st.length === 36 ? st[n] : g.xs[n];
        const cy = st.length === 36 ? st[6 + n] : g.cy;
        const reach = 30 * (st.length === 36 ? st[12 + n] : 1);
        const s = sel.get();
        if (Math.abs(e.x - cx) < reach && Math.abs(e.y + g.boardTop - cy) < reach + 8 && n !== s[1]) {
          sel.set([s[1], n, now.get(), now.get()]);
          scheduleOnRN(feel, 'snap');
        }
        const fg = finger.get();
        finger.set([fg[0], fg[1], fg[2], 0]);
        mode.set(2);
        lastTouch.set(now.get());
      });
    // Shared values are stable; the gesture depends on the geometry only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const backdrop = art?.backdrop;
  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art && backdrop ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={backdrop} />
          </Canvas>

          <View style={[styles.header, { top: insets.top + 12 }]}>
            <View style={styles.headerText} pointerEvents="none">
              <Text style={styles.kicker}>STUDIO</Text>
              <Text style={styles.title}>Harbor study</Text>
              <Text style={styles.subtitle}>Autosaved 2 min ago · 6 layers</Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel="Share" style={({ pressed }) => [styles.share, pressed && styles.pressed]}>
              <SymbolView name="square.and.arrow.up" size={18} weight="semibold" tintColor={INK} fallback={<View style={styles.shareFallback} />} />
            </Pressable>
          </View>

          <MagnetSnapDockPanel sel={sel} top={g.panelTop} height={g.panelH} />

          <GestureDetector gesture={gesture}>
            <View
              style={[styles.touch, { top: g.boardTop }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={`Tool dock, ${TOOL_NAMES[selected]} selected. Drag near the dock and let go on a tool.`}
            >
              <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
                <Picture picture={picture} transform={[{ translateY: -g.boardTop }]} />
              </Canvas>
            </View>
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', alignItems: 'flex-start' },
  headerText: { flex: 1, gap: 3 },
  kicker: { color: '#c4532f', fontSize: 11, fontWeight: '800', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: INK_SOFT, fontSize: 14, fontWeight: '500' },
  share: {
    width: 40,
    height: 40,
    borderRadius: 20,
    marginTop: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.08)',
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.08)',
  },
  shareFallback: { width: 14, height: 14, borderRadius: 3, borderWidth: 2, borderColor: INK },
  pressed: { transform: [{ scale: 0.97 }], opacity: 0.85 },
  touch: { position: 'absolute', left: 0, right: 0, bottom: 0 },
});
