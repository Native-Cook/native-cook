import { Atlas, Canvas, FilterMode, Image, MipmapMode, Skia, type SkRect, type SkRSXform } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { interpolateColor, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DOOR, SHADOW_LEVELS, SHADOW_PX, bakeDoor, bakeFaces, bakeShadows, glyphPaths, magnetFont, magnetScale, makeLayout, maxLettersFit, slotXs } from './magneticLettersArt';
import { KIND_SNAP, LOOP, autoCount, autoReady } from './magneticLettersMotion';
import {
  type Ctx, type Sim, autoPose, drop, grab, hit, makeSim, nameText, removeAt, sendHome, simPose, takeOver, typeLetter, typeMagnet,
} from './magneticLettersSim';

/*
 Magnetic Letters Name — a family app's "Who's joining?" sign-up whose name field is the fridge
 door. A loose heap of glossy plastic magnet letters lies on the brushed steel; typing (or tapping
 a magnet) flies a letter up to the name line, where the door's pull snaps it on with a little
 bounce; backspace pops the last one off and back onto the heap. Magnets can be dragged anywhere,
 onto the line (in between letters) or off it.

 autoPlay (LOOP = 9 s): rest 0–1.3 · "NOAH" typed 1.3–3.4 (a flight of 0.38 s every 0.42 s) ·
 "Continue" lights 3.6 · hold 3.6–5.2 · backspace ×4 5.2–7.0 (0.45 s apart) · rest 7.0–9.0.
 A touch takes over at once from where the loop is; 2 s after the last one every magnet flies home
 (≤ 0.9 s) and the loop starts again from 0. Reduced motion: no loop; letters jump to their place.

 Drawing: the door is one baked image; the magnets are two <Atlas> draws (glyph-shaped shadows,
 then faces) whose sprites and transforms are written on the UI thread every frame something moves.
*/

type Props = {
  /** Spells "NOAH" on the door, lights "Continue", backspaces it away, and starts over. */
  autoPlay?: boolean;
};

const MODE_REST = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
/** Seconds after the last touch before the loop takes the door back, and how long that takes. */
const IDLE = 2;
const RETURN = 0.9;
const FACE_COLS = 7;
export function MagneticLettersName({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const [count, setCount] = useState(0);
  const [text, setText] = useState('');
  const input = useRef<TextInput>(null);

  const art = useMemo(() => {
    const W = size.width;
    const H = size.height;
    const s = magnetScale(W, H, insets.top, insets.bottom);
    const paths = glyphPaths(magnetFont(52 * s));
    const L = makeLayout(W, H, insets.top, insets.bottom, paths);
    const px = Math.min(3, PixelRatio.get());
    const xs = slotXs(L, L.script);
    const scriptOf = L.magnets.map((_, i) => L.script.indexOf(i));
    const autoSlots = L.script.map((_, k) => ({ x: xs[k], y: L.letterY, rot: 0, lift: 0, scale: 1 }));
    return {
      L,
      px,
      maxFit: maxLettersFit(L),
      scriptOf,
      autoSlots,
      door: bakeDoor(L, insets.top, px),
      faces: bakeFaces(L, paths, px, FACE_COLS),
      shadows: bakeShadows(L, paths),
    };
  }, [size.width, size.height, insets.top, insets.bottom]);
  const { L, px, maxFit, scriptOf, autoSlots } = art;

  const sim = useSharedValue<Sim | null>(null);
  const mode = useSharedValue(MODE_REST);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnEnd = useSharedValue(0);
  const ready = useSharedValue(0);
  const shown = useSharedValue(0);
  const faceSprites = useSharedValue<SkRect[]>([]);
  const faceXforms = useSharedValue<SkRSXform[]>([]);
  const shadowSprites = useSharedValue<SkRect[]>([]);
  const shadowXforms = useSharedValue<SkRSXform[]>([]);

  useEffect(() => {
    sim.set(makeSim(L));
    mode.set(MODE_REST);
    clock.set(0);
  }, [L, sim, mode, clock]);

  const snapFeel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  // The keyboard is asked for through state, so no gesture callback holds the input's ref.
  const [focusAsk, setFocusAsk] = useState(0);
  useEffect(() => {
    if (focusAsk > 0) input.current?.focus();
    else if (focusAsk < 0) input.current?.blur();
  }, [focusAsk]);

  const ctx: Ctx = { L, scriptOf, autoSlots, maxFit, reduced };

  // ——— UI-thread handlers (declared before their callers; the logic is in magneticLettersSim.ts) ———

  function syncText(s: Sim) {
    'worklet';
    scheduleOnRN(setText, nameText(ctx, s));
  }

  /** Takes the door from the loop: every magnet keeps its pose and its flight's progress. */
  function touched() {
    'worklet';
    const s = sim.get();
    if (!s) return;
    lastTouch.set(now.get());
    if (mode.get() === MODE_USER) return;
    if (mode.get() === MODE_REST) {
      takeOver(ctx, s, clock.get(), now.get());
      s.ready = ready.get();
      syncText(s);
    }
    mode.set(MODE_USER);
  }

  /** The keyboard's text, applied as backspaces and typed letters; the line is the truth. */
  function applyText(next: string) {
    'worklet';
    touched();
    const s = sim.get();
    if (!s) return;
    const at = now.get();
    const cur = nameText(ctx, s);
    let p = 0;
    while (p < cur.length && p < next.length && cur[p] === next[p]) p++;
    while (s.name.length > p) removeAt(ctx, s, s.name.length - 1, true, at);
    for (let j = p; j < next.length; j++) typeLetter(ctx, s, next[j], at);
    syncText(s);
  }

  function backspace() {
    'worklet';
    touched();
    const s = sim.get();
    if (!s || s.name.length === 0) return;
    removeAt(ctx, s, s.name.length - 1, true, now.get());
    syncText(s);
  }

  function onTap(x: number, y: number) {
    'worklet';
    touched();
    const s = sim.get();
    if (!s) return;
    const i = hit(ctx, s, x, y);
    if (i >= 0) {
      const k = s.name.indexOf(i);
      if (k >= 0) removeAt(ctx, s, k, true, now.get());
      else typeMagnet(ctx, s, i, now.get());
      syncText(s);
    } else if (y > L.lineY - L.capH - 44 && y < L.lineY + 24) scheduleOnRN(setFocusAsk, now.get() + 1);
    else scheduleOnRN(setFocusAsk, -now.get() - 1);
  }

  function onGrab(x: number, y: number) {
    'worklet';
    touched();
    const s = sim.get();
    if (!s) return;
    const i = hit(ctx, s, x, y);
    if (i < 0) return;
    const wasOnLine = s.name.indexOf(i) >= 0;
    grab(ctx, s, i, x, y, now.get());
    if (wasOnLine) syncText(s);
  }

  function onDrag(x: number, y: number, vx: number) {
    'worklet';
    const s = sim.get();
    if (!s || s.held < 0) return;
    lastTouch.set(now.get());
    s.heldX = x; s.heldY = y; s.vx = vx;
  }

  function onDrop() {
    'worklet';
    const s = sim.get();
    if (!s || s.held < 0) return;
    lastTouch.set(now.get());
    if (drop(ctx, s, now.get())) syncText(s);
  }

  /** 2 s after the last touch: the line empties and every magnet flies home, staggered. */
  function beginReturn(s: Sim) {
    'worklet';
    sendHome(ctx, s, now.get());
    syncText(s);
    returnEnd.set(now.get() + RETURN);
    mode.set(MODE_RETURN);
  }

  useFrameCallback((frame) => {
    const s = sim.get();
    if (!s || s.poses.length !== L.magnets.length) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const before = now.get();
    const at = before + dt;
    now.set(at);
    const m = mode.get();
    let moving = false;
    let rdy = 0;
    let cnt = 0;

    if (m === MODE_REST) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      for (let i = 0; i < L.magnets.length; i++) autoPose(ctx, i, t, s.poses[i]);
      moving = auto;
      rdy = autoReady(t);
      cnt = autoCount(t);
    } else {
      if (m === MODE_USER && auto && s.held < 0 && at - lastTouch.get() > IDLE) beginReturn(s);
      if (mode.get() === MODE_RETURN && at >= returnEnd.get()) {
        mode.set(MODE_REST);
        clock.set(0);
      }
      let haptic = false;
      for (let i = 0; i < L.magnets.length; i++) {
        if (simPose(ctx, s, i, at, s.poses[i])) moving = true;
        if (s.kind[i] === KIND_SNAP) {
          const end = s.start[i] + s.dur[i];
          if (before < end && at >= end) haptic = true;
        }
      }
      if (haptic && mode.get() === MODE_USER) scheduleOnRN(snapFeel);
      const target = mode.get() === MODE_USER && s.name.length >= 2 ? 1 : 0;
      s.ready += (target - s.ready) * Math.min(1, dt * 14);
      if (Math.abs(target - s.ready) > 0.002) moving = true;
      rdy = s.ready;
      for (let k = 0; k < s.name.length; k++) {
        const i = s.name[k];
        if (s.kind[i] !== KIND_SNAP || at >= s.start[i] + s.dur[i]) cnt++;
      }
    }
    ready.set(rdy);
    if (shown.get() !== cnt) shown.set(cnt);

    // Nothing moves and the last still frame is on screen: leave the canvas alone.
    if (!moving && s.idleDrawn) return;
    s.idleDrawn = !moving;

    // Draw order: flat magnets by their place in the heap, lifted ones above them by height.
    const n = L.magnets.length;
    const idx: number[] = [];
    const key: number[] = [];
    for (let r = 0; r < n; r++) {
      const i = L.order[r];
      const p = s.poses[i];
      idx.push(i);
      key.push((p.lift > 0.01 ? 10 + p.lift : 0) + r / n);
    }
    for (let a = 1; a < n; a++) {
      const vi = idx[a];
      const vk = key[a];
      let b = a - 1;
      while (b >= 0 && key[b] > vk) { idx[b + 1] = idx[b]; key[b + 1] = key[b]; b--; }
      idx[b + 1] = vi; key[b + 1] = vk;
    }
    const cell = L.cell * px;
    const sc = L.shadowCell * SHADOW_PX;
    const fs: SkRect[] = [];
    const fx: SkRSXform[] = [];
    const ss: SkRect[] = [];
    const sx: SkRSXform[] = [];
    for (let r = 0; r < n; r++) {
      const i = idx[r];
      const p = s.poses[i];
      fs.push(Skia.XYWHRect((i % FACE_COLS) * cell, Math.floor(i / FACE_COLS) * cell, cell, cell));
      fx.push(Skia.RSXformFromRadians(p.scale / px, p.rot, p.x, p.y, cell / 2, cell / 2));
      const lv = Math.min(SHADOW_LEVELS - 1, Math.round(p.lift * (SHADOW_LEVELS - 1)));
      ss.push(Skia.XYWHRect(lv * sc, i * sc, sc, sc));
      sx.push(Skia.RSXformFromRadians((p.scale * (1 + 0.05 * p.lift)) / SHADOW_PX, p.rot, p.x + 1.2 + 7 * p.lift, p.y + 2 + 13 * p.lift, sc / 2, sc / 2));
    }
    faceSprites.set(fs);
    faceXforms.set(fx);
    shadowSprites.set(ss);
    shadowXforms.set(sx);
  });

  useAnimatedReaction(
    () => shown.get(),
    (c, prev) => {
      if (c !== prev) scheduleOnRN(setCount, c);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart((e) => {
      onGrab(e.x - e.translationX, e.y - e.translationY);
      onDrag(e.x, e.y, e.velocityX);
    })
    .onUpdate((e) => {
      onDrag(e.x, e.y, e.velocityX);
    })
    .onFinalize(() => {
      onDrop();
    });
  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((e, ok) => {
      if (ok) onTap(e.x, e.y);
    });
  const gesture = Gesture.Race(pan, tap);

  const onText = (next: string) => {
    const clean = next.toUpperCase().replace(/[^A-Z]/g, '').slice(0, maxFit);
    setText(clean);
    scheduleOnUI(applyText, clean);
  };
  const onBackspace = () => scheduleOnUI(backspace);
  const onContinue = () => {
    if (count >= 2 && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  const buttonStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(ready.get(), [0, 1], ['#C9CCCA', DOOR.blue]),
    boxShadow: `0 ${2 + 8 * ready.get()}px ${6 + 18 * ready.get()}px rgba(24,52,110,${0.08 + 0.3 * ready.get()})`,
    transform: [{ scale: 1 + 0.03 * Math.sin(Math.PI * Math.min(1, ready.get())) }],
  }));
  const labelStyle = useAnimatedStyle(() => ({
    color: interpolateColor(ready.get(), [0, 1], ['#8A8F8D', '#F4F6FB']),
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  const sampling = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
  const pillBottom = insets.bottom + 20;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Fridge door with magnet letters. Tap a magnet to add it to your name, drag magnets, or type.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Image image={art.door} x={0} y={0} width={L.W} height={L.H} fit="fill" />
            <Atlas image={art.shadows} sprites={shadowSprites} transforms={shadowXforms} sampling={sampling} />
            <Atlas image={art.faces} sprites={faceSprites} transforms={faceXforms} sampling={sampling} />
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <Text style={styles.kicker}>FRIDGE · MARLOW HOUSEHOLD</Text>
        <Text style={styles.title}>{"Who's joining?"}</Text>
        <Text style={styles.subtitle}>Spell your name on the door. Tap a magnet, drag one, or just type.</Text>
      </View>

      <View pointerEvents="none" style={[styles.lineLabels, { top: L.lineY - L.capH - 40, left: L.lineX0, width: L.lineX1 - L.lineX0 }]}>
        <Text style={styles.label}>YOUR NAME</Text>
        <Text style={styles.counter}>{`${count} / ${maxFit}`}</Text>
      </View>

      <Pressable
        onPress={onBackspace}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel="Delete last letter"
        style={({ pressed }) => [styles.backspace, { left: L.lineX1 + 10, top: L.letterY - 20, transform: [{ scale: pressed ? 0.94 : 1 }] }]}
      >
        <SymbolView name={{ ios: 'delete.left', android: 'backspace', web: 'backspace' }} size={18} weight="semibold" tintColor={DOOR.inkSoft} />
      </Pressable>

      <TextInput
        ref={input}
        value={text}
        onChangeText={onText}
        autoCapitalize="characters"
        autoCorrect={false}
        spellCheck={false}
        autoComplete="off"
        caretHidden
        returnKeyType="done"
        accessibilityLabel="Your name"
        style={styles.hiddenInput}
      />

      <View style={[styles.footer, { bottom: pillBottom }]}>
        <Pressable onPress={onContinue} accessibilityRole="button" accessibilityState={{ disabled: count < 2 }}>
          <Animated.View style={[styles.button, buttonStyle]}>
            <Animated.Text style={[styles.buttonLabel, labelStyle]}>Continue</Animated.Text>
          </Animated.View>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: DOOR.mid },
  header: { position: 'absolute', left: 28, right: 56, gap: 6 },
  kicker: { color: DOOR.inkFaint, fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  title: { color: DOOR.ink, fontSize: 34, fontWeight: '800', letterSpacing: -0.6 },
  subtitle: { color: DOOR.inkSoft, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  lineLabels: { position: 'absolute', flexDirection: 'row', justifyContent: 'space-between' },
  label: { color: DOOR.inkFaint, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  counter: { color: DOOR.inkFaint, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  backspace: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#EEEFEC',
    boxShadow: '0 1px 2px rgba(40,44,48,0.22), 0 6px 14px rgba(40,44,48,0.12), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  hiddenInput: { position: 'absolute', left: -100, top: 0, width: 2, height: 2, opacity: 0 },
  footer: { position: 'absolute', left: 24, right: 24 },
  button: { height: 56, borderRadius: 28, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { fontSize: 17, fontWeight: '700', letterSpacing: 0.2 },
});
