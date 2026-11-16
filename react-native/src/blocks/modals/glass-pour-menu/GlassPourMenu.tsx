// A single progress clock drives a three-field liquid union, its optics, the flower and the menu content.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, findNodeHandle, PixelRatio, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { StatusBar } from 'expo-status-bar';
import {
  cancelAnimation,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Canvas,
  Fill,
  Group,
  ImageShader,
  Line,
  Path,
  Shader,
  Skia,
  Text,
  matchFont,
  useFont,
  vec,
  type SkFont,
  type SkPath,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeBackdrop, type BackdropPair, type GlassBackdrop } from './gpm-backdrop';
import { haptic } from './gpm-haptics';
import { ICONS } from './gpm-icons';
import {
  clamp01,
  easeInOut,
  GESTURE,
  glassAt,
  impulseAt,
  lifeAt,
  LOOP,
  mix,
  P_PINCH,
  smooth,
  SPRING,
  timeline,
  T,
  type Glass,
  type PourLife,
} from './gpm-motion';
import { FROST_BLUR, GLASS_SKSL, PETALS_SKSL, SOFT_BLUR } from './gpm-shaders';
import { useGlassSound, type GlassSoundCue } from './gpm-sound';
import { comp as defaults, COPY, makeLayout, menuGeometry, ref, type MenuGeometry } from './gpm-theme';

export { LOOP };
export type GlassPourMenuItem = { key: string; label: string; icon?: SkPath | string; destructive?: boolean; disabled?: boolean };
export type GlassPourMenuProps = {
  items: GlassPourMenuItem[];
  onSelect: (key: string) => void;
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  onComplete?: (open: boolean) => void;
  progress?: SharedValue<number>;
  backdrop?: GlassBackdrop;
  onBackdropError?: (error: unknown) => void;
  anchor?: { x: number; y: number };
  placement?: 'below' | 'above' | 'auto';
  size?: { drop?: number; width?: number; rowHeight?: number };
  tint?: string;
  glass?: { refraction?: number; dispersion?: number; frost?: number; hot?: number };
  font?: { family: string; size?: number };
  dragToSelect?: boolean;
  haptics?: boolean;
  sound?: boolean;
  autoPlay?: boolean;
  accessibilityLabel?: string;
};
const petalsFx = Skia.RuntimeEffect.Make(PETALS_SKSL);
const glassFx = Skia.RuntimeEffect.Make(GLASS_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const EMPTY_TOUCH = { active: false, mode: 0, x: 0, y: 0, p: 0, moved: false, row: -1, latched: false, off: false };

function tintRGB(tint: string) {
  const color = Skia.Color(tint);
  return [color[0], color[1], color[2]];
}

/** Shared by the live material and its two reduced-motion endpoints. */
function uniforms(
  g: Glass,
  ox: number,
  oy: number,
  dir: number,
  optics: number[],
  tint: number[],
  lens: number[],
  spec: number,
  satellite: number[],
) {
  'worklet';
  return {
    uO: [ox, oy],
    uS: 1,
    uBead: [g.beadX, g.beadY, g.beadR],
    uBox: [g.boxX, g.boxY, g.boxW, g.boxH],
    uBoxR: g.boxR,
    uK: g.k,
    uThick: g.thick,
    uHot: g.hot,
    uFrost: g.frost,
    uOpen: g.open,
    uPress: 0,
    uLens: lens,
    uSpec: spec,
    uRim: g.rim,
    uP3: P3,
    uNeck: [g.neckA, g.neckB, g.neckR],
    uSatellite: satellite,
    uDwell: (lens[1] - 18) / 2,
    uDir: dir,
    uOptics: optics,
    uTint: tint,
  };
}

export function GlassPourMenu({
  items,
  onSelect,
  open: controlledOpen,
  defaultOpen = false,
  onOpenChange,
  onComplete,
  progress,
  backdrop,
  onBackdropError,
  anchor,
  placement = 'auto',
  size,
  tint = ref.color.dropTint,
  glass,
  font: fontProp,
  dragToSelect = true,
  haptics = true,
  sound = false,
  autoPlay = false,
  accessibilityLabel = COPY.button,
}: GlassPourMenuProps) {
  if (items.length < 2 || items.length > 7) throw new Error('GlassPourMenu requires 2–7 items');
  const { width, height } = useWindowDimensions();
  const [bounds, setBounds] = useState<{ width: number; height: number } | null>(null);
  const W = bounds?.width ?? width,
    H = bounds?.height ?? height;
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotion();
  const labelSize = (fontProp?.size ?? ref.type.row.size) * PixelRatio.getFontScale();
  const bundledFont = useFont(require('./assets/fonts/Inter-Medium.ttf'), labelSize);
  const font = useMemo(
    () => (fontProp ? matchFont({ fontFamily: fontProp.family, fontSize: labelSize }) : bundledFont),
    [fontProp, labelSize, bundledFont],
  );
  const comp = useMemo(
    () => menuGeometry(items.length, { ...size, width: Math.min(size?.width ?? 250, W - 32) }, labelSize),
    [items.length, size, W, labelSize],
  );
  const L = makeLayout(W, H, insets.top, insets.bottom);
  const direction =
    placement === 'above' ||
    (placement === 'auto' && anchor && H - anchor.y - insets.bottom < comp.panel.bottom && anchor.y - insets.top > comp.panel.bottom)
      ? -1
      : 1;
  const low = direction === 1 ? -comp.bead.y + comp.bead.r : comp.panel.bottom;
  const high = direction === 1 ? comp.panel.bottom : -comp.bead.y + comp.bead.r;
  const ox = Math.max(comp.panel.half + 8, Math.min(W - comp.panel.half - 8, anchor?.x ?? L.ox));
  const oy = Math.max(insets.top + low + 8, Math.min(H - insets.bottom - high - 8, anchor?.y ?? L.oy));
  const optics = useMemo(
    () => [glass?.refraction ?? 1, glass?.dispersion ?? 1, glass?.frost ?? 1, glass?.hot ?? 1].map((x) => Math.max(0, Math.min(x, 2))),
    [glass],
  );
  const tintColor = useMemo(() => tintRGB(tint), [tint]);
  const playSound = useGlassSound(sound);
  const cue = useCallback(
    (kind: keyof typeof haptic, audio?: GlassSoundCue) => {
      if (haptics) haptic[kind]();
      if (sound && audio) playSound(audio);
    },
    [haptics, sound, playSound],
  );

  const initialOpen = controlledOpen ?? defaultOpen;
  const ownP = useSharedValue(initialOpen ? 1 : 0);
  const p = progress ?? ownP;
  const press = useSharedValue(0),
    lensY = useSharedValue(comp.rows[0]),
    lensA = useSharedValue(0),
    dwell = useSharedValue(0),
    flash = useSharedValue(0);
  const fade = useSharedValue(initialOpen ? 1 : 0);
  const clock = useSharedValue(0),
    loopT = useSharedValue(0),
    playing = useSharedValue(autoPlay && !reduce && controlledOpen === undefined);
  const pinned = useSharedValue(-1),
    held = useSharedValue(-1),
    touchedAt = useSharedValue(-1e9),
    settling = useSharedValue(false);
  const touch = useSharedValue(EMPTY_TOUCH),
    prevP = useSharedValue(initialOpen ? 1 : 0),
    pinchAt = useSharedValue(-100),
    satelliteAt = useSharedValue(-100),
    satelliteProgress = useSharedValue(0),
    dipAt = useSharedValue(-100),
    dipStrength = useSharedValue(0);
  const twist = useSharedValue(0),
    twistKickAt = useSharedValue(-100),
    activeRowAt = useSharedValue(0),
    settled = useSharedValue(initialOpen ? 1 : 0);
  const [menuOpen, setMenuOpen] = useState(initialOpen);
  const [pairState, setPair] = useState<{ source: GlassBackdrop; width: number; height: number; value: BackdropPair } | null>(null);
  const pair = pairState?.source === backdrop && pairState?.width === W && pairState?.height === H ? pairState.value : null;
  const snapshotTicket = useRef(0);
  const captureNeeded = useRef(true);
  const capture = useCallback(async () => {
    if (!backdrop || !captureNeeded.current) return;
    captureNeeded.current = false;
    const ticket = ++snapshotTicket.current;
    try {
      const next = await bakeBackdrop(backdrop, W, H);
      if (ticket === snapshotTicket.current) setPair({ source: backdrop, width: W, height: H, value: next });
    } catch (error) {
      captureNeeded.current = true;
      onBackdropError?.(error);
    }
  }, [backdrop, W, H, onBackdropError]);
  useEffect(() => {
    captureNeeded.current = true;
    if (backdrop && (!('current' in backdrop) || defaultOpen)) void Promise.resolve().then(capture);
    return () => {
      // Invalidate asynchronous captures when this source or size leaves the screen.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      snapshotTicket.current++;
    };
  }, [capture, backdrop, defaultOpen]);
  const notify = useCallback(
    (next: boolean) => {
      if (!next && backdrop && 'current' in backdrop) captureNeeded.current = true;
      onOpenChange?.(next);
    },
    [onOpenChange, backdrop],
  );
  const complete = useCallback((next: boolean) => onComplete?.(next), [onComplete]);

  useEffect(() => {
    if (controlledOpen === undefined) return;
    playing.set(false);
    if (reduce) fade.set(withTiming(controlledOpen ? 1 : 0, { duration: 180 }));
    else p.set(withSpring(controlledOpen ? 1 : 0, controlledOpen ? SPRING.open : SPRING.close));
    if (controlledOpen) void Promise.resolve().then(capture);
  }, [controlledOpen, reduce, fade, p, playing, capture]);
  useEffect(() => {
    playing.set(autoPlay && !reduce && controlledOpen === undefined);
  }, [autoPlay, reduce, controlledOpen, playing]);

  useEffect(() => {
    if (!__DEV__) return;
    const hook = {
      pin: (t: number) => {
        held.set(-1);
        pinned.set(t);
      },
      play: () => {
        pinned.set(-1);
        held.set(-1);
        loopT.set(0);
        playing.set(!reduce);
      },
      progress: (x: number) => {
        pinned.set(-1);
        playing.set(false);
        held.set(x);
      },
      state: () => ({ p: p.get(), t: loopT.get(), lensY: lensY.get(), lensA: lensA.get(), playing: playing.get() }),
    };
    (globalThis as Record<string, unknown>).__gpm = hook;
    return () => {
      if ((globalThis as Record<string, unknown>).__gpm === hook) delete (globalThis as Record<string, unknown>).__gpm;
    };
  }, [held, lensA, lensY, loopT, p, pinned, playing, reduce]);

  // Announce only after all labels are revealed; callbacks never run for each progress frame.
  const announced = useSharedValue(menuOpen);
  function scheduleOnRNIfChanged(next: boolean) {
    'worklet';
    if (next !== announced.get()) {
      announced.set(next);
      scheduleOnRN(setMenuOpen, next);
    }
  }

  useAnimatedReaction(
    () => (reduce ? fade.get() : p.get()),
    (value) => {
      const next = announced.get() ? value > 0.9 : value >= 0.999;
      scheduleOnRNIfChanged(next);
      const end = Math.abs(value) < 0.001 ? 0 : Math.abs(value - 1) < 0.001 ? 1 : -1;
      if (end >= 0 && end !== settled.get()) {
        settled.set(end);
        if (!playing.get()) scheduleOnRN(complete, end === 1);
      } else if (value > 0.02 && value < 0.98) settled.set(-1);
    },
  );
  const frame = useFrameCallback((f) => {
    'worklet';
    const dt = Math.min(f.timeSincePreviousFrame ?? 16.7, 50) / 1000;
    const now = clock.get() + dt;
    clock.set(now);
    if (pinned.get() >= 0 || playing.get()) {
      const rawT = pinned.get() >= 0 ? pinned.get() : loopT.get() + dt;
      const t = ((rawT % LOOP) + LOOP) % LOOP;
      loopT.set(t);
      const sample = timeline(t);
      p.set(sample.p);
      press.set(sample.press);
      lensY.set(sample.lensY);
      lensA.set(sample.lensA);
      flash.set(sample.flash);
      dwell.set(0);
    } else if (held.get() >= 0) p.set(held.get());
    const pv = p.get(),
      before = prevP.get();
    if ((before < P_PINCH && pv >= P_PINCH) || (before >= P_PINCH && pv < P_PINCH)) {
      pinchAt.set(now);
      twistKickAt.set(now);
      if (!playing.get() && pinned.get() < 0 && held.get() < 0 && (touch.get().active || now - touchedAt.get() < 1.2))
        scheduleOnRN(cue, pv >= P_PINCH ? 'pinch' : 'merge', pv >= P_PINCH ? 'plip' : 'merge');
    }
    prevP.set(pv);
    const satelliteAge = now - satelliteAt.get();
    if (satelliteAge >= 0.22 && satelliteAge - dt < 0.22 && !playing.get() && pinned.get() < 0) scheduleOnRN(cue, 'grab');
    if (!reduce) twist.set(mix(twist.get(), ((8 * Math.PI) / 180) * easeInOut(clamp01(pv)), 1 - Math.exp(-dt / 0.07)));
    if (touch.get().row >= 0 && now - activeRowAt.get() >= 0.25 && dwell.get() === 0) dwell.set(withSpring(1, SPRING.lens));
    if (
      autoPlay &&
      controlledOpen === undefined &&
      !reduce &&
      !playing.get() &&
      pinned.get() < 0 &&
      held.get() < 0 &&
      !touch.get().active &&
      now - touchedAt.get() > GESTURE.resumeAfter
    ) {
      if (!settling.get()) {
        settling.set(true);
        p.set(withSpring(0, SPRING.glide));
        lensA.set(withTiming(0, { duration: 160 }));
      } else if (Math.abs(pv) < 0.001) {
        settling.set(false);
        loopT.set(0);
        playing.set(true);
      }
    }
  }, false);
  useEffect(() => {
    frame.setActive(!reduce);
    return () => frame.setActive(false);
  }, [frame, reduce]);

  const look = useDerivedValue(() => {
    const t = pinned.get() >= 0 || playing.get() ? loopT.get() : clock.get();
    const life: PourLife = reduce ? {} : lifeAt(t, p.get());
    if ((pinned.get() >= 0 || playing.get()) && t > 6.55) life.sag = (life.sag ?? 0) * (1 - smooth(6.55, 6.9, t));
    // Loop one-shots are analytic too: pinning order never changes the picture.
    const event =
      playing.get() || pinned.get() >= 0 ? (t > 6.55 ? -1 : t - (t >= T.close ? T.merge : T.pinch)) : clock.get() - pinchAt.get();
    return glassAt(p.get(), press.get(), comp, {
      ...life,
      recoil: 5 * impulseAt(event, SPRING.recoil),
      dimple: -3 * impulseAt(event, SPRING.dimple),
      dip: playing.get() || pinned.get() >= 0 ? 0 : 9 * dipStrength.get() * Math.max(0, impulseAt(clock.get() - dipAt.get(), SPRING.open)),
    });
  });
  const glassU = useDerivedValue(() => {
    const g = look.get();
    const t = pinned.get() >= 0 || playing.get() ? loopT.get() : clock.get();
    const satTime = clock.get() - satelliteAt.get();
    const satellite =
      pinned.get() < 0 && !playing.get() && satTime >= 0 && satTime < 0.22
        ? [0, mix(g.neckB, g.boxY, clamp01(satelliteProgress.get())), 5 * (1 - smooth(0.12, 0.22, satTime))]
        : [0, 0, 0];
    return uniforms(
      g,
      ox,
      oy,
      direction,
      optics,
      tintColor,
      [lensY.get(), 18 + 2 * dwell.get(), lensA.get(), flash.get()],
      t * 1.5,
      satellite,
    );
  });
  const closedU = useMemo(
    () => uniforms(glassAt(0, 0, comp), ox, oy, direction, optics, tintColor, [comp.rows[0], 18, 0, 0], 0, [0, 0, 0]),
    [comp, ox, oy, direction, optics, tintColor],
  );
  const openedU = useDerivedValue(() =>
    uniforms(glassAt(1, 0, comp), ox, oy, direction, optics, tintColor, [lensY.get(), 18, lensA.get(), flash.get()], 0, [0, 0, 0]),
  );
  const pageU = useDerivedValue(() => {
    const time = loopT.get();
    const auto = playing.get() || pinned.get() >= 0;
    const kick = auto
      ? time > 6.55
        ? 0
        : impulseAt(time - (time >= T.close ? T.merge : T.pinch), SPRING.twist)
      : impulseAt(clock.get() - twistKickAt.get(), SPRING.twist);
    const turn = auto ? ((8 * Math.PI) / 180) * easeInOut(clamp01(timeline(Math.max(0, time - 0.07)).p)) : twist.get();
    return { uO: [ox, oy], uS: 1, uBlur: 0, uTwist: reduce ? 0 : turn + ((1.2 * Math.PI) / 180) * kick };
  });
  const frostU = useDerivedValue(() => ({ ...pageU.get(), uBlur: FROST_BLUR }));
  const softU = useDerivedValue(() => ({ ...pageU.get(), uBlur: SOFT_BLUR }));

  const takeOver = () => {
    'worklet';
    playing.set(false);
    pinned.set(-1);
    held.set(-1);
    settling.set(false);
    cancelAnimation(p);
    cancelAnimation(fade);
    cancelAnimation(lensY);
    cancelAnimation(lensA);
    cancelAnimation(flash);
    flash.set(0);
    touchedAt.set(clock.get());
  };
  const request = (next: boolean, velocity = 0) => {
    'worklet';
    scheduleOnRN(notify, next);
    if (next) scheduleOnRN(capture);
    if (controlledOpen !== undefined) {
      if (!reduce) p.set(withSpring(controlledOpen ? 1 : 0, SPRING.glide));
      return;
    }
    if (reduce) {
      if (fade.get() > 0.5 !== next) scheduleOnRN(cue, next ? 'pinch' : 'merge', next ? 'plip' : 'merge');
      fade.set(withTiming(next ? 1 : 0, { duration: 180 }));
    } else
      p.set(
        withSpring(
          next ? 1 : 0,
          next ? { ...SPRING.open, damping: mix(22, 14, clamp01(velocity / 6)), velocity } : { ...SPRING.close, velocity },
        ),
      );
    if (!next) lensA.set(withTiming(0, { duration: 160 }));
  };
  const selectRow = (i: number) => {
    'worklet';
    if (!items[i] || items[i].disabled || (p.get() < 0.985 && !reduce)) return;
    scheduleOnRN(onSelect, items[i].key);
    scheduleOnRN(cue, items[i].destructive ? 'pickDanger' : 'pick');
    flash.set(withSequence(withTiming(1, { duration: 120 }), withTiming(0, { duration: 230 })));
    request(false);
    if (controlledOpen === undefined) {
      if (reduce) fade.set(withDelay(300, withTiming(0, { duration: 180 })));
      else p.set(withDelay(300, withSpring(0, SPRING.close)));
      lensA.set(withDelay(300, withTiming(0, { duration: 160 })));
    }
  };
  const rowAt = (y: number) => {
    'worklet';
    const i = Math.round((y - comp.rows[0]) / comp.rowPitch);
    return i >= 0 && i < items.length && !items[i].disabled ? i : -1;
  };
  const focusRow = (i: number, onset = false) => {
    'worklet';
    if (i >= 0) {
      lensY.set(reduce ? comp.rows[i] : withSpring(comp.rows[i], SPRING.lens));
      lensA.set(withTiming(1, { duration: 140 }));
      scheduleOnRN(cue, 'row', onset ? 'tap' : undefined);
    } else lensA.set(withTiming(0, { duration: 140 }));
    dwell.set(0);
    activeRowAt.set(clock.get());
  };
  // RNGH stores these callbacks; refs in capture are read only after a touch, never during render.
  /* eslint-disable react-hooks/refs */
  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          takeOver();
          const qx = e.x - ox,
            qy = (e.y - oy) * direction;
          const pv = reduce ? (fade.get() > 0.5 ? 1 : 0) : p.get();
          const g = glassAt(pv, 0, comp);
          let mode = 4;
          if (Math.hypot(qx, qy - g.beadY) < Math.max(g.beadR + 10, 22)) mode = pv > 0.5 ? 3 : 1;
          else if (Math.abs(qx) < g.boxW && Math.abs(qy - g.boxY) < g.boxH) mode = pv > 0.985 ? 2 : 1;
          const row = mode === 2 ? rowAt(qy) : -1;
          touch.set({ ...EMPTY_TOUCH, active: true, mode, x: e.x, y: e.y, p: p.get(), row });
          if (mode === 1 || mode === 3) {
            press.set(withSpring(1, SPRING.snap));
            scheduleOnRN(cue, 'grab', 'tap');
            if (mode === 1) scheduleOnRN(capture);
          }
          if (row >= 0) focusRow(row, true);
        })
        .onUpdate((e) => {
          const t = touch.get();
          if (!t.active) return;
          touchedAt.set(clock.get());
          const dy = (e.y - t.y) * direction;
          const qy = (e.y - oy) * direction;
          const moved = t.moved || Math.hypot(e.x - t.x, e.y - t.y) > GESTURE.tapSlop;
          const off = Math.abs(e.x - ox) > comp.panel.half + 40 || qy < comp.panel.top || qy > comp.panel.bottom;
          let latched = t.latched,
            row = t.row;
          if (!reduce && (t.mode === 1 || t.mode === 3)) {
            const raw = t.p + (t.mode === 3 ? -dy : dy) / GESTURE.ptPerP;
            const rubber = raw < 0 ? raw / (1 - raw * 6) : raw > 1 ? 1 + (raw - 1) / (1 + (raw - 1) * 6) : raw;
            p.set(rubber);
            latched = t.mode === 1 && raw >= 1 && dragToSelect;
          }
          if (t.mode === 2 || latched) {
            const next = !off ? rowAt(qy) : -1;
            if (next !== row) {
              row = next;
              focusRow(row);
            }
          }
          touch.set({ ...t, moved, row, latched, off });
        })
        .onEnd((e) => {
          const t = touch.get();
          if (t.mode === 1 || t.mode === 3) {
            if (!t.moved) request(t.mode === 1);
            else if (t.latched && t.row >= 0) selectRow(t.row);
            else {
              const velocity = (e.velocityY * direction * (t.mode === 3 ? -1 : 1)) / GESTURE.ptPerP;
              request(p.get() + velocity * GESTURE.projection > 0.5, velocity);
              dipAt.set(clock.get());
              dipStrength.set(clamp01(velocity / 6));
              if (!reduce && Math.abs(e.velocityY) > 900 && p.get() > 0.6) {
                satelliteAt.set(clock.get());
                satelliteProgress.set(0);
                satelliteProgress.set(withSpring(1, SPRING.satellite));
              }
            }
          } else if (t.mode === 2 && t.row >= 0) selectRow(t.row);
          else if (t.mode === 4 || t.off) request(false);
        })
        .onFinalize((_e, success) => {
          touch.set(EMPTY_TOUCH);
          touchedAt.set(clock.get());
          press.set(withSpring(0, SPRING.snap));
          dwell.set(withSpring(0, SPRING.lens));
          if (!success) request(p.get() > 0.5);
        }), // Worklet closures are recreated only when their actual dependencies change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items, comp, ox, oy, direction, reduce, controlledOpen, dragToSelect, cue, capture, notify, onSelect],
  );

  /* eslint-enable react-hooks/refs */
  const firstRow = useRef<View>(null),
    trigger = useRef<View>(null);
  const focused = useRef(false);
  useEffect(() => {
    if (menuOpen === focused.current || playing.get()) return;
    focused.current = menuOpen;
    const node = findNodeHandle((menuOpen ? firstRow : trigger).current);
    if (node) AccessibilityInfo.setAccessibilityFocus(node);
  }, [menuOpen, playing]);
  const activate = (action: number) => {
    takeOver();
    if (action < 0) request(action === -1);
    else selectRow(action);
  };
  // the glass's three children: the page, its frosted copy (the open panel) and its softened copy (the selection)
  const children = (kind: 'sharp' | 'frost' | 'soft') =>
    pair ? (
      <ImageShader image={pair[kind]} tx="clamp" ty="clamp" fit="fill" rect={{ x: 0, y: 0, width: W, height: H }} />
    ) : (
      <Shader source={petalsFx!} uniforms={kind === 'sharp' ? pageU : kind === 'frost' ? frostU : softU} />
    );
  const openedAlpha = useDerivedValue(() => fade.get());
  const closedAlpha = useDerivedValue(() => 1 - fade.get());
  const display = useDerivedValue(() => (reduce ? glassAt(fade.get() > 0.5 ? 1 : 0, 0, comp) : look.get()));
  return (
    <View
      style={styles.screen}
      onAccessibilityEscape={() => activate(-2)}
      accessibilityViewIsModal={menuOpen}
      onLayout={(e) => {
        const b = e.nativeEvent.layout;
        if (!bounds || b.width !== bounds.width || b.height !== bounds.height) setBounds({ width: b.width, height: b.height });
      }}
    >
      {/* the page is pale pink: dark status-bar ink (white measured 1.6 : 1 on it) */}
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible={false}>
          {petalsFx && glassFx ? (
            <Canvas style={StyleSheet.absoluteFill}>
              {reduce ? (
                <>
                  <Fill>
                    <Shader source={glassFx} uniforms={closedU}>
                      {children('sharp')}
                      {children('frost')}
                      {children('soft')}
                    </Shader>
                  </Fill>
                  <Group opacity={closedAlpha}>
                    <GlassGlyph look={display} ox={ox} oy={oy} dir={direction} endpoint={0} />
                  </Group>
                  <Group opacity={openedAlpha}>
                    <Fill>
                      <Shader source={glassFx} uniforms={openedU}>
                        {children('sharp')}
                        {children('frost')}
                        {children('soft')}
                      </Shader>
                    </Fill>
                    <GlassGlyph look={display} ox={ox} oy={oy} dir={direction} endpoint={1} endpointY={comp.bead.y} />
                  </Group>
                </>
              ) : (
                <>
                  <Fill>
                    <Shader source={glassFx} uniforms={glassU}>
                      {children('sharp')}
                      {children('frost')}
                      {children('soft')}
                    </Shader>
                  </Fill>
                  <GlassGlyph look={look} ox={ox} oy={oy} dir={direction} />
                </>
              )}
              {font
                ? items.map((item, i) => (
                    <MenuRow
                      key={item.key}
                      item={item}
                      index={i}
                      comp={comp}
                      font={font}
                      look={look}
                      fade={fade}
                      reduce={reduce}
                      ox={ox}
                      oy={oy}
                      dir={direction}
                    />
                  ))
                : null}
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        {!menuOpen ? (
          <View
            ref={trigger}
            accessible
            accessibilityRole="button"
            accessibilityLabel={accessibilityLabel}
            accessibilityState={{ expanded: false }}
            onAccessibilityTap={() => activate(-1)}
            accessibilityActions={[{ name: 'activate' }]}
            onAccessibilityAction={() => activate(-1)}
            style={hitBox(ox, oy, comp.button.r * 2, comp.button.r * 2)}
          />
        ) : (
          <>
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={COPY.close}
              onAccessibilityTap={() => activate(-2)}
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityAction={() => activate(-2)}
              style={hitBox(ox, oy + direction * comp.bead.y, comp.bead.r * 2, comp.bead.r * 2)}
            />
            {items.map((item, i) => (
              <View
                key={item.key}
                ref={i === items.findIndex((x) => !x.disabled) ? firstRow : undefined}
                accessible
                accessibilityRole="menuitem"
                accessibilityLabel={item.label}
                accessibilityState={{ disabled: !!item.disabled }}
                onAccessibilityTap={() => activate(i)}
                accessibilityActions={[{ name: 'activate' }]}
                onAccessibilityAction={() => activate(i)}
                style={hitBox(ox, oy + direction * comp.rows[i], comp.panel.half * 2, comp.rowPitch)}
              />
            ))}
          </>
        )}
      </View>
    </View>
  );
}

function GlassGlyph({
  look,
  ox,
  oy,
  dir,
  endpoint,
  endpointY,
}: {
  look: SharedValue<Glass>;
  ox: number;
  oy: number;
  dir: number;
  endpoint?: number;
  endpointY?: number;
}) {
  return (
    <>
      {[-1, 0, 1].map((line) => (
        <GlyphStroke key={line} line={line} look={look} ox={ox} oy={oy} dir={dir} endpoint={endpoint} endpointY={endpointY} />
      ))}
    </>
  );
}
function GlyphStroke({
  line,
  look,
  ox,
  oy,
  dir,
  endpoint,
  endpointY,
}: {
  line: number;
  look: SharedValue<Glass>;
  ox: number;
  oy: number;
  dir: number;
  endpoint?: number;
  endpointY?: number;
}) {
  // ≡ → ✕ in two overlapping moves: the outer bars close onto the middle one (which shrinks to its centre and is
  // gone by then), then they turn ±45° about the same centre, so the strokes always cross at the glyph's centre and
  // no mid-way chevron or half-faded bar shows.
  const xf = useDerivedValue(() => {
    const g = look.get(),
      k = endpoint ?? g.glyphK;
    const y = endpoint === undefined ? g.beadY : endpoint === 1 ? (endpointY ?? defaults.bead.y) : 0;
    const close = smooth(0, 0.55, k);
    const turn = smooth(0.3, 1, k) + Math.max(0, k - 1);
    return [
      { translateX: ox },
      { translateY: oy + dir * y + line * defaults.menuGlyph.gap * (1 - close) },
      { rotate: (line * turn * Math.PI) / 4 },
      { scaleX: line === 0 ? Math.max(0.001, 1 - close) : mix(1, 0.74, turn) },
    ];
  });
  const alpha = useDerivedValue(() => (line === 0 ? 1 - smooth(0, 0.4, endpoint ?? look.get().glyphK) : 1));
  return (
    <Group transform={xf} opacity={alpha}>
      <Line
        p1={vec(-defaults.menuGlyph.w / 2, 0)}
        p2={vec(defaults.menuGlyph.w / 2, 0)}
        color={ref.color.glyph}
        strokeWidth={defaults.menuGlyph.stroke}
        strokeCap="round"
      />
    </Group>
  );
}

function MenuRow({
  item,
  index,
  comp,
  font,
  look,
  fade,
  reduce,
  ox,
  oy,
  dir,
}: {
  item: GlassPourMenuItem;
  index: number;
  comp: MenuGeometry;
  font: SkFont;
  look: SharedValue<Glass>;
  fade: SharedValue<number>;
  reduce: boolean;
  ox: number;
  oy: number;
  dir: number;
}) {
  const alpha = useDerivedValue(() => (reduce ? fade.get() : (look.get().rows[index] ?? 0)) * (item.disabled ? 0.35 : 1));
  const transform = useDerivedValue(() => [{ translateY: reduce ? 0 : -4 * dir * (1 - (look.get().rows[index] ?? 0)) }]);
  const path = useMemo(
    () => (typeof item.icon === 'string' ? Skia.Path.MakeFromSVGString(ICONS[item.icon as keyof typeof ICONS] ?? item.icon) : item.icon),
    [item.icon],
  );
  const y = oy + dir * comp.rows[index];
  const color = item.destructive ? ref.color.danger : ref.color.ink;
  const label = useMemo(() => {
    const max = comp.panel.half * 2 - comp.labelX - comp.iconX - comp.icon - 12;
    let value = item.label;
    const measure = (text: string) => font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
    if (measure(value) <= max) return value;
    while (value.length && measure(value + '…') > max) value = value.slice(0, -1);
    return value + '…';
  }, [item.label, font, comp]);
  return (
    <Group opacity={alpha} transform={transform}>
      <Text
        x={ox - comp.panel.half + comp.labelX}
        y={y + (font.getSize() * ref.type.capRatio) / 2}
        text={label}
        font={font}
        color={color}
      />
      {path ? (
        <Group
          transform={[
            { translateX: ox + comp.panel.half - comp.iconX - comp.icon / 2 },
            { translateY: y - comp.icon / 2 },
            { scale: comp.icon / 24 },
          ]}
        >
          <Path
            path={path}
            style="stroke"
            strokeWidth={(comp.stroke * 24) / comp.icon}
            strokeCap="round"
            strokeJoin="round"
            color={color}
          />
        </Group>
      ) : null}
      {item.destructive && index > 0 ? (
        <Line
          p1={vec(ox - comp.panel.half + comp.rule.inset, y - (dir * comp.rowPitch) / 2)}
          p2={vec(ox + comp.panel.half - comp.rule.inset, y - (dir * comp.rowPitch) / 2)}
          color={ref.color.rule}
          strokeWidth={0.5}
          opacity={0.35}
        />
      ) : null}
    </Group>
  );
}
function hitBox(cx: number, cy: number, w: number, h: number) {
  const width = Math.max(w, 44),
    height = Math.max(h, 44);
  return { position: 'absolute' as const, left: cx - width / 2, top: cy - height / 2, width, height };
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: ref.color.cream } });
