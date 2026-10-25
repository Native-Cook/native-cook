import {
  BlurMask,
  Canvas,
  Fill,
  Group,
  Path,
  RoundedRect,
  Shader,
  Skia,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useMemo } from 'react';
import { Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  CARVE,
  CODE,
  GRAIN_LIFE,
  LOOP,
  RAKE_LEN,
  RAKE_OVER,
  SAMPLES,
  SLOTS,
  SLOT_GAP,
  SLOT_HALF_H,
  type Sand,
  type User,
  autoSand,
  cloneUser,
  clamp01,
  easeInOutCubic,
  easeOutCubic,
  emptySand,
  emptyUser,
  hash,
  makeDigitArt,
  pressAt,
  seg,
  shakeAt,
} from './sandPinCodeMotion';
import { KEY_H, KEY_W, Pebble, RakeArt, SlotGroove } from './sandPinCodeParts';
import { SAND_SKSL } from './sandPinCodeShader';

/*
 Sand PIN Code — a wellness app's lock screen on a zen sand tray. Each digit typed on the
 river-stone keypad is carved into its slot by a bamboo stylus (path trim, 0.26 s): the groove
 is a floor stroke, a shadowed upper wall, a lit lower wall and a faint berm. A wrong code
 shakes the tray, then a rake sweeps across and the digits crumble into grains that slide and
 sink, leaving fresh rake lines; the right code sends rings out through the sand and the tray
 brightens. A horizontal swipe over the tray rakes it by hand.

 One Canvas (the sand shader, grooves, grains, rake and stylus); the keypad and copy are views.
 Per frame only uniforms, trims, transforms and two grain paths change.
*/

type Props = {
  /** Types a wrong code, rakes it away, types the right one and unlocks, every LOOP seconds. */
  autoPlay?: boolean;
};

/** The canvas reaches this far past each side of the screen so a shake never shows its edge. */
const PAD = 24;
const IDLE = 2;
const RETURN_LEN = 0.75;
const KEY_GAP_X = 22;
const KEY_GAP_Y = 14;
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'del'];
const P3 = Platform.OS === 'ios' ? 1 : 0;

function feel(kind: number) {
  if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else if (kind === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

export function SandPinCode({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const effect = useMemo(() => Skia.RuntimeEffect.Make(SAND_SKSL), []);
  const art = useMemo(() => makeDigitArt(), []);
  const empty = useMemo(() => Skia.Path.Make(), []);

  // Layout (canvas coordinates: screen x + PAD).
  const CW = W + PAD * 2;
  const cx = CW / 2;
  const keypadH = KEY_H * 4 + KEY_GAP_Y * 3;
  const keypadTop = H - insets.bottom - 20 - keypadH;
  const headerBottom = insets.top + 128;
  const slotY = Math.round((headerBottom + keypadTop) / 2 + 4);
  const x0 = cx - ((SLOTS - 1) * SLOT_GAP) / 2;
  const rakeFrom = x0 - RAKE_OVER;
  const rakeTo = x0 + (SLOTS - 1) * SLOT_GAP + RAKE_OVER;
  const stone = useMemo(() => [CW - PAD - 54, insets.top + 64, 24], [CW, insets.top]);

  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const user = useSharedValue<User>(emptyUser());

  /** Leaves autoPlay for the user's own tray (it starts empty: the loop's rest). */
  function takeOver() {
    'worklet';
    if (mode.get() !== 1) {
      user.set(emptyUser());
      mode.set(1);
    }
    lastTouch.set(now.get());
  }

  function markPasses(u: User, n: number) {
    'worklet';
    const s = u.sand;
    for (let i = 0; i < SLOTS; i += 1) {
      if (s.digits[i] >= 0 && s.rakeOn > 0 && s.rakeX >= x0 + i * SLOT_GAP) {
        s.grainDigits[i] = s.digits[i];
        s.grainAt[i] = n;
        s.digits[i] = -1;
        s.carveAt[i] = -1;
        s.linesOn = 1;
      }
    }
    if (s.digits.every((d) => d < 0)) u.entered = [];
  }

  function pressKey(index: number) {
    'worklet';
    takeOver();
    const u = cloneUser(user.get());
    const n = now.get();
    const s = u.sand;
    if (u.unlockAt >= 0 || u.rakeStart >= 0 || u.checkAt >= 0 || u.fingerRake === 1) return;
    u.keyAt = n;
    s.key = index;
    if (index === 11) {
      const k = u.entered.length - 1;
      if (k >= 0) {
        u.entered.pop();
        s.digits[k] = -1;
        s.carveAt[k] = -1;
      }
    } else if (index !== 9 && u.entered.length < SLOTS) {
      const d = index === 10 ? 0 : index + 1;
      const k = u.entered.length;
      u.entered.push(d);
      s.digits[k] = d;
      s.carveAt[k] = n;
      s.passAt[k] = -1;
      if (u.entered.length === SLOTS) u.checkAt = n + CARVE + 0.12;
    }
    user.set(u);
    scheduleOnRN(feel, 0);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const n = now.get() + dt;
    now.set(n);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    const u = cloneUser(user.get());
    const s = u.sand;
    if (u.checkAt >= 0 && n >= u.checkAt) {
      u.checkAt = -1;
      const ok = u.entered.every((d, i) => d === CODE[i]);
      if (ok) {
        u.unlockAt = n;
        scheduleOnRN(feel, 2);
      } else {
        u.shakeAt = n;
        u.rakeStart = n + 0.42;
        scheduleOnRN(feel, 1);
      }
    }
    if (u.rakeStart >= 0 && n >= u.rakeStart) {
      const r = seg(n, u.rakeStart, u.rakeStart + RAKE_LEN);
      s.rakeX = rakeFrom + (rakeTo - rakeFrom) * easeInOutCubic(r);
      s.rakeFrom = rakeFrom;
      s.rakeOn = r < 1 ? clamp01((n - u.rakeStart) / 0.12) : 1 - seg(n, u.rakeStart + RAKE_LEN, u.rakeStart + RAKE_LEN + 0.25);
      if (n > u.rakeStart + RAKE_LEN + 0.25) {
        u.rakeStart = -1;
        s.rakeOn = 0;
      }
    } else if (u.fingerRake === 0 && s.rakeOn > 0) {
      s.rakeOn = Math.max(0, s.rakeOn - dt * 4);
    }
    markPasses(u, n);
    const busy = u.rakeStart >= 0 || u.fingerRake === 1 || u.checkAt >= 0 || (u.unlockAt >= 0 && n - u.unlockAt < 1.4);
    if (busy) lastTouch.set(n);
    if (m === 1 && n - lastTouch.get() > IDLE) {
      mode.set(2);
      u.returnAt = n;
    }
    if (m === 2 && n - u.returnAt > RETURN_LEN) {
      user.set(emptyUser());
      clock.set(0);
      mode.set(0);
      return;
    }
    user.set(u);
  });

  const sand = useDerivedValue<Sand>(() => {
    const m = mode.get();
    if (m === 0) return auto ? autoSand(clock.get(), x0) : emptySand();
    const u = user.get();
    const n = now.get();
    const s = { ...u.sand };
    s.press = s.key >= 0 ? pressAt(n - u.keyAt) : 0;
    s.shake = reduced ? 0 : shakeAt(n - u.shakeAt);
    const back = u.returnAt >= 0 ? 1 - easeInOutCubic(seg(n, u.returnAt, u.returnAt + RETURN_LEN - 0.05)) : 1;
    if (u.unlockAt >= 0) {
      s.ripple = reduced ? -1 : n - u.unlockAt;
      s.bright = easeOutCubic(seg(n, u.unlockAt, u.unlockAt + 0.4)) * back;
      s.unlock = easeOutCubic(seg(n, u.unlockAt + 0.1, u.unlockAt + 0.5)) * back;
    }
    s.fade = back;
    return s;
  });
  const tNow = useDerivedValue(() => (mode.get() === 0 ? clock.get() : now.get()));

  const uniforms = useDerivedValue(() => {
    const s = sand.get();
    return {
      res: [CW, H],
      p3: P3,
      slots: [x0, slotY, SLOT_GAP, SLOT_HALF_H],
      stone,
      ripple: [cx, slotY, s.ripple],
      bright: s.bright,
    };
  });

  // Grains: 50 per crumbling digit, pushed ahead by the rake, sliding down, sinking (radius → 0).
  const grains = useDerivedValue(() => {
    const s = sand.get();
    const t = tNow.get();
    const light = Skia.PathBuilder.Make();
    const dark = Skia.PathBuilder.Make();
    for (let i = 0; i < SLOTS; i += 1) {
      const d = s.grainDigits[i];
      const at = s.grainAt[i];
      if (d < 0 || at < 0) continue;
      const tau = t - at;
      if (tau < 0 || tau > GRAIN_LIFE) continue;
      const pts = art.samples[d];
      const sx = x0 + i * SLOT_GAP;
      for (let k = 0; k < SAMPLES; k += 1) {
        const h1 = hash(i * 131 + k * 7.3);
        const h2 = hash(i * 71 + k * 3.1 + 9);
        const life = GRAIN_LIFE * (0.55 + 0.45 * h2);
        if (tau > life) continue;
        const push = (14 + 46 * h1) * (1 - Math.exp(-tau / 0.16));
        const fall = (6 + 26 * h2) * easeOutCubic(Math.min(1, tau / 0.55));
        const x = sx + pts[k * 2] + push + (h2 - 0.5) * 6 * tau;
        const y = slotY + pts[k * 2 + 1] + fall;
        const r = (0.9 + 1.1 * h1) * (1 - Math.pow(tau / life, 3));
        (k % 3 === 0 ? dark : light).addCircle(x, y, r);
      }
    }
    return [light.build(), dark.build()];
  });
  const grainsLight = useDerivedValue(() => grains.get()[0]);
  const grainsDark = useDerivedValue(() => grains.get()[1]);

  // Fresh rake lines behind the rake, across the slot row.
  const rakeLines = useDerivedValue(() => {
    const s = sand.get();
    const p = Skia.PathBuilder.Make();
    if (s.linesOn <= 0) return p.build();
    const end = Math.min(s.rakeX, rakeTo);
    if (end <= rakeFrom) return p.build();
    for (let k = 0; k < 7; k += 1) {
      const y = slotY - 42 + k * 14;
      p.moveTo(rakeFrom, y);
      p.lineTo(end, y);
    }
    return p.build();
  });
  const linesOpacity = useDerivedValue(() => sand.get().linesOn * sand.get().fade);
  const rakeTransform = useDerivedValue(() => [{ translateX: sand.get().rakeX }]);
  const rakeOpacity = useDerivedValue(() => sand.get().rakeOn);

  // The stylus rides the stroke of whichever digit is being carved.
  const stylus = useDerivedValue(() => {
    const s = sand.get();
    const t = tNow.get();
    for (let i = 0; i < SLOTS; i += 1) {
      const d = s.digits[i];
      if (d < 0 || s.carveAt[i] < 0) continue;
      const tau = t - s.carveAt[i];
      if (tau < -0.12 || tau > CARVE + 0.22) continue;
      const pr = easeInOutCubic(clamp01(tau / CARVE));
      const k = Math.min(SAMPLES - 1, Math.round(pr * (SAMPLES - 1)));
      const pts = art.samples[d];
      const o = tau < 0 ? clamp01((tau + 0.12) / 0.12) : 1 - clamp01((tau - CARVE) / 0.22);
      return [x0 + i * SLOT_GAP + pts[k * 2], slotY + pts[k * 2 + 1], reduced ? 0 : o];
    }
    return [0, 0, 0];
  });
  const stylusTransform = useDerivedValue(() => [{ translateX: stylus.get()[0] }, { translateY: stylus.get()[1] }]);
  const stylusOpacity = useDerivedValue(() => stylus.get()[2]);
  const stylusArt = useMemo(() => {
    return Skia.PathBuilder.Make().moveTo(0, 0).lineTo(34, -66).build();
  }, []);

  // ---- Gestures ----------------------------------------------------------------------------
  const rakePan = Gesture.Pan()
    .activeOffsetX([-14, 14])
    .failOffsetY([-24, 24])
    .onStart((e) => {
      takeOver();
      const u = cloneUser(user.get());
      u.fingerRake = 1;
      u.sand.rakeFrom = e.x + PAD;
      u.sand.rakeX = e.x + PAD;
      u.sand.rakeOn = 1;
      user.set(u);
    })
    .onUpdate((e) => {
      const u = cloneUser(user.get());
      u.sand.rakeX = e.x + PAD;
      u.sand.rakeFrom = Math.min(u.sand.rakeFrom, e.x + PAD);
      user.set(u);
      lastTouch.set(now.get());
    })
    .onFinalize(() => {
      const u = cloneUser(user.get());
      if (u.fingerRake === 1) {
        // Letting go rakes the rest of the tray too: every digit left crumbles.
        u.fingerRake = 0;
        const n = now.get();
        for (let i = 0; i < SLOTS; i += 1) {
          if (u.sand.digits[i] >= 0) {
            u.sand.grainDigits[i] = u.sand.digits[i];
            u.sand.grainAt[i] = n;
            u.sand.digits[i] = -1;
            u.sand.carveAt[i] = -1;
          }
        }
        u.entered = [];
        user.set(u);
      }
      lastTouch.set(now.get());
    });

  const shakeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: sand.get().shake }] }));
  const lockA = useAnimatedStyle(() => ({ opacity: 1 - sand.get().unlock }));
  const lockB = useAnimatedStyle(() => ({ opacity: sand.get().unlock, transform: [{ scale: 0.9 + 0.1 * sand.get().unlock }] }));
  const copyA = useAnimatedStyle(() => ({ opacity: 1 - sand.get().unlock, transform: [{ translateY: -8 * sand.get().unlock }] }));
  const copyB = useAnimatedStyle(() => ({ opacity: sand.get().unlock, transform: [{ translateY: 8 * (1 - sand.get().unlock) }] }));

  const onKey = (index: number) => scheduleOnUI(pressKey, index);

  return (
    <View style={[styles.root, { backgroundColor: '#cdb38c' }]}>
      <Animated.View style={[StyleSheet.absoluteFill, shakeStyle]}>
        <Canvas style={{ position: 'absolute', left: -PAD, top: 0, width: CW, height: H }}>
          <Fill>{effect ? <Shader source={effect} uniforms={uniforms} /> : null}</Fill>
          <Group opacity={linesOpacity}>
            <Path path={rakeLines} style="stroke" strokeWidth={4} strokeCap="round" color="rgba(255,246,226,0.32)" transform={[{ translateX: 0.9 }, { translateY: 1.1 }]} />
            <Path path={rakeLines} style="stroke" strokeWidth={3.4} strokeCap="round" color="rgba(132,104,68,0.55)" />
            <Path path={rakeLines} style="stroke" strokeWidth={1.6} strokeCap="round" color="rgba(92,70,42,0.55)" transform={[{ translateX: -0.6 }, { translateY: -0.8 }]} />
          </Group>
          {Array.from({ length: SLOTS }, (_, i) => (
            <SlotGroove key={i} index={i} sand={sand} tNow={tNow} paths={art.paths} empty={empty} x={x0 + i * SLOT_GAP} y={slotY} />
          ))}
          <Path path={grainsLight} color="#e9d9bb" />
          <Path path={grainsDark} color="#a88c66" />
          <Group transform={stylusTransform} opacity={stylusOpacity}>
            <Path path={stylusArt} style="stroke" strokeWidth={5} strokeCap="round" color="rgba(70,48,20,0.22)" transform={[{ translateX: 9 }, { translateY: 6 }]}>
              <BlurMask blur={3} style="normal" />
            </Path>
            <Path path={stylusArt} style="stroke" strokeWidth={5} strokeCap="round" color="#b38a52" />
            <Path path={stylusArt} style="stroke" strokeWidth={1.4} strokeCap="round" color="rgba(255,236,196,0.55)" transform={[{ translateX: -1.2 }, { translateY: -0.4 }]} />
            <RoundedRect x={13} y={-28} width={7} height={3} r={1} color="#7a5a2c" transform={[{ rotate: -1.1 }]} origin={{ x: 16, y: -26 }} />
          </Group>
          <Group transform={rakeTransform} opacity={rakeOpacity}>
            <RakeArt y={slotY} />
          </Group>
        </Canvas>
        <GestureDetector gesture={rakePan}>
          <View
            style={{ position: 'absolute', left: 0, top: 0, width: W, height: keypadTop - 8 }}
            accessible
            accessibilityLabel="Sand tray with six passcode slots. Swipe across it to rake it smooth."
          />
        </GestureDetector>

        <View style={[styles.keypad, { top: keypadTop, left: (W - (KEY_W * 3 + KEY_GAP_X * 2)) / 2 }]}>
          {KEYS.map((k, i) => (
            <Pebble key={i} index={i} label={k} sand={sand} onPress={onKey} />
          ))}
        </View>
      </Animated.View>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 20 }]}>
        <View style={styles.kickerRow}>
          <View style={styles.lockBox}>
            <Animated.View style={[StyleSheet.absoluteFill, styles.center, lockA]}>
              <SymbolView name={{ ios: 'lock.fill', android: 'lock', web: 'lock' }} size={13} tintColor="#5b4528" />
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, styles.center, lockB]}>
              <SymbolView name={{ ios: 'lock.open.fill', android: 'lock_open', web: 'lock_open' }} size={13} tintColor="#5b4528" />
            </Animated.View>
          </View>
          <Text style={styles.kicker}>DUNE</Text>
        </View>
        <View>
          <Animated.View style={copyA}>
            <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
              Enter your passcode
            </Text>
            <Text style={styles.sub}>Draw six digits in the sand.</Text>
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, copyB]}>
            <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
              Welcome back, Ilsa
            </Text>
            <Text style={styles.sub}>Your evening session is ready.</Text>
          </Animated.View>
        </View>
      </View>
      <Text pointerEvents="none" style={[styles.hint, { top: slotY + SLOT_HALF_H + 22 }]}>
        Swipe across the tray to rake it smooth
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  center: { alignItems: 'center', justifyContent: 'center' },
  header: { position: 'absolute', left: 24, right: 84, gap: 12 },
  kickerRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  lockBox: { width: 26, height: 26, borderRadius: 13, backgroundColor: 'rgba(255,248,232,0.45)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.6), 0 1px 2px rgba(80,55,20,0.25)' },
  kicker: { color: '#5b4528', fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: '#3d2d18', fontSize: 27, fontWeight: '700', letterSpacing: -0.4 },
  sub: { color: 'rgba(61,45,24,0.68)', fontSize: 15, fontWeight: '500', marginTop: 4 },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(61,45,24,0.55)', fontSize: 12, fontWeight: '500', letterSpacing: 0.2 },
  keypad: { position: 'absolute', width: KEY_W * 3 + KEY_GAP_X * 2, flexDirection: 'row', flexWrap: 'wrap', columnGap: KEY_GAP_X, rowGap: KEY_GAP_Y },
});
