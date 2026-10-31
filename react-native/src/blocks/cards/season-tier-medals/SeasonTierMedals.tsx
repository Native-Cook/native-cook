import { BlurStyle, Canvas, Path, Picture, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { METALS, medalFx } from './stm-medal';
import {
  clamp01,
  CURRENT,
  LOOP,
  makeRail,
  mix,
  type Rail,
  rubber,
  SCRIPT,
  scriptTarget,
  seg,
  setSpin,
  smooth,
  SPIN,
  spinStart,
  spinYaw,
  stepRail,
  TIERS,
} from './stm-motion';
import { type Cue, useStmAudio } from './use-stm-audio';

/*
 Season Tier Medals — the loyalty level screen of an invented bank's rewards programme.

 Look (the pin, a still): on a near-black page "Your level in the summer season", SILVER in heavy
 condensed capitals, three medals on a rail (bronze cut off on the left, a big silver one in a white
 halo in the middle, gold cut off on the right), a frosted card with the points still to earn and a
 bar, page dashes, then "Claim your rewards" and a reward card. The pin's medals carry the bank's
 own mark; ours carry rank chevrons, one to three.

 Motion (ours): the medals are ray-cast in an SkSL shader, so they turn as real discs. Swipe the
 rail: the medals follow the finger, turning to face the middle; let go and they spring to the
 nearest one (a flick carries further), and the medal that arrives spins once about its vertical
 axis and lands with a small overshoot and a flash. The tier name, the card's copy and the bar
 follow the rail continuously, so a half-way drag shows a half-way crossfade. Tap a side medal to go
 to it, tap the middle one to spin it.

 autoPlay: LOOP = 8 s. Rest on Silver; 1.4 s glide to Gold, which spins in; 4.4 s back to Silver,
 which spins in; 6.0-7.1 s a light sweeps the medal. Frame 8 s equals frame 0.
 Reduced motion: no autoplay, no spins; the rail moves on a short spring.
*/

let debugState = '';
const CHEVRON = (s: number) => {
  // the small tier mark next to the points, a chevron in a ring
  const p = Skia.PathBuilder.Make();
  p.moveTo(s * 0.28, s * 0.62);
  p.lineTo(s * 0.5, s * 0.4);
  p.lineTo(s * 0.72, s * 0.62);
  return p.build();
};
const TEE = (() => {
  const p = Skia.PathBuilder.Make();
  p.moveTo(9, 6);
  p.lineTo(13, 4);
  p.cubicTo(14, 6.5, 18, 6.5, 19, 4);
  p.lineTo(23, 6);
  p.lineTo(26, 11);
  p.lineTo(22.5, 13);
  p.lineTo(21.5, 11.5);
  p.lineTo(21.5, 24);
  p.lineTo(10.5, 24);
  p.lineTo(10.5, 11.5);
  p.lineTo(9.5, 13);
  p.lineTo(6, 11);
  p.close();
  return p.build();
})();

const DATA = [
  {
    name: 'BRONZE',
    kicker: 'Your level in the spring season',
    title: 'Bronze reached in April',
    nums: '500 / 500',
    foot: '1% back as points on every purchase',
    fill: 1,
  },
  {
    name: 'SILVER',
    kicker: 'Your level in the summer season',
    title: 'Earn 1,500 more points',
    nums: '500 / 2,000',
    foot: 'Get it by August 31 to reach Gold',
    fill: 0.25,
  },
  {
    name: 'GOLD',
    kicker: 'Your next level',
    title: 'Gold starts at 2,000 points',
    nums: '500 / 2,000',
    foot: '10% back on travel and dining',
    fill: 0.25,
  },
];
const BAR = ['#c98d68', '#e9eaee', '#f2c56c'];
const HALO = [
  [1, 0.72, 0.55],
  [1, 1, 1],
  [1, 0.86, 0.55],
];

type Props = { autoPlay?: boolean };

/** Copy of the tiers not in the middle stays in the tree for the crossfade but is hidden from VoiceOver. */
const hidden = (h: boolean) =>
  h ? ({ accessibilityElementsHidden: true, importantForAccessibility: 'no-hide-descendants' } as const) : {};

export function SeasonTierMedals({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [fontsOk] = useFonts({
    'STM-Display': require('./assets/fonts/BarlowCondensed-ExtraBold.ttf'),
    'STM-Regular': require('./assets/fonts/Inter-Regular.ttf'),
    'STM-Medium': require('./assets/fonts/Inter-Medium.ttf'),
    'STM-SemiBold': require('./assets/fonts/Inter-SemiBold.ttf'),
  });

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2;
  const K = insets.top + 62;
  const MY = K + 186;
  const RC = Math.min(84, w * 0.21);
  const SP = Math.min(172, w * 0.43);

  const play = useStmAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const rail = useSharedValue<Rail>(makeRail());
  const pos = useSharedValue(CURRENT);
  const tick = useSharedValue(0);
  const drag = useSharedValue({ startP: CURRENT, startX: 0, at: 0, detent: CURRENT, byHand: 0 });
  const [cur, setCur] = useState(CURRENT);
  useAnimatedReaction(
    () => Math.round(Math.max(0, Math.min(TIERS - 1, pos.get()))),
    (n, prev) => {
      if (n !== prev) scheduleOnRN(setCur, n);
    },
  );
  const debugT = useSharedValue(-1);
  const debugRail = useSharedValue<{ t: number; r: Rail; clock: number }>({ t: -1, r: makeRail(), clock: 0 });

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __seasonMedals?: unknown };
    g.__seasonMedals = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__seasonMedals;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  /** Autoplay for one frame: the script sets the target, the arriving medal spins. */
  const scriptStep = (r: Rail, t: number, dt: number, now: number) => {
    'worklet';
    const tg = scriptTarget(t);
    if (tg !== r.target) {
      r.target = tg;
      r.stiff = 70;
      if (!reduced) setSpin(r, tg, now);
    }
    stepRail(r, dt);
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const dbg = debugT.get();
    let r = rail.get();
    let t = time.get();
    if (dbg >= 0) {
      // replay the script from 0 so a pinned frame shows the spins as they play
      const cache = debugRail.get();
      if (cache.t !== dbg) {
        const sim = makeRail();
        let st = 0;
        const step = 1 / 120;
        while (st < dbg) {
          const d = Math.min(step, dbg - st);
          scriptStep(sim, st, d, st + d);
          st += d;
        }
        debugRail.set({ t: dbg, r: sim, clock: dbg });
      }
      r = debugRail.get().r;
      t = dbg;
    } else {
      const touched = r.mode === 1 || now - last.get() < 2;
      if (auto && !touched) {
        t += dt;
        if (t >= LOOP) t -= LOOP;
        scriptStep(r, t, dt, now);
      } else {
        stepRail(r, dt);
      }
      // the landing cue of a spin started by a finger
      const d = drag.get();
      if (d.byHand === 1) {
        for (let i = 0; i < TIERS; i++) {
          const since = now - spinStart(r, i);
          if (since >= SPIN * 0.78 && since < SPIN && r.landed !== i) {
            r.landed = i;
            scheduleOnRN(cue, 'land');
            scheduleOnRN(buzz, 1);
          }
        }
      }
    }
    time.set(t);
    pos.set(r.p);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} p=${r.p.toFixed(3)} target=${r.target}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && size !== null);
    return () => frameCb.setActive(false);
  }, [active, size, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const dbg = debugT.get();
    const r = dbg >= 0 ? debugRail.get().r : rail.get();
    const now = dbg >= 0 ? dbg : clock.get();
    const t = time.get();
    const p = r.p;

    // the halo behind the middle medal, tinted by the tier in the middle
    const i0 = Math.max(0, Math.min(TIERS - 2, Math.floor(p)));
    const fk = clamp01(p - i0);
    const hr = mix(HALO[i0][0], HALO[i0 + 1][0], fk);
    const hg = mix(HALO[i0][1], HALO[i0 + 1][1], fk);
    const hb = mix(HALO[i0][2], HALO[i0 + 1][2], fk);
    let flash = 0;
    for (let i = 0; i < TIERS; i++) {
      const since = now - spinStart(r, i);
      if (since > SPIN * 0.75 && since < SPIN + 1.2) flash = Math.max(flash, Math.exp(-(since - SPIN * 0.75) * 3.2));
    }
    // the whole page takes the tier's colour, faintly, like an achievements carousel's backdrop
    const PAGE = [
      [0.42, 0.2, 0.1],
      [0.26, 0.28, 0.36],
      [0.46, 0.33, 0.08],
    ];
    const pr = mix(PAGE[i0][0], PAGE[i0 + 1][0], fk);
    const pgc = mix(PAGE[i0][1], PAGE[i0 + 1][1], fk);
    const pb = mix(PAGE[i0][2], PAGE[i0 + 1][2], fk);
    const page = Skia.Paint();
    page.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx, y: MY - 40 },
        h * 0.75,
        [Skia.Color(`rgba(${Math.round(pr * 255)},${Math.round(pgc * 255)},${Math.round(pb * 255)},0.85)`), Skia.Color('rgba(18,18,20,0)')],
        null,
        0,
      ),
    );
    c.drawRect(Skia.XYWHRect(0, 0, w, h), page);
    const halo = Skia.Paint();
    halo.setColor(Skia.Color(`rgba(${Math.round(hr * 255)},${Math.round(hg * 255)},${Math.round(hb * 255)},1)`));
    halo.setAlphaf(0.2 + 0.12 * flash);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, RC * 0.75, true));
    c.drawOval(Skia.XYWHRect(cx - RC * 1.55, MY - RC * 1.05, RC * 3.1, RC * 2.1), halo);
    const core = Skia.Paint();
    core.setColor(Skia.Color('#ffffff'));
    core.setAlphaf(0.1 + 0.1 * flash);
    core.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, RC * 0.35, true));
    c.drawOval(Skia.XYWHRect(cx - RC * 1.05, MY - RC * 0.95, RC * 2.1, RC * 1.9), core);

    if (medalFx) {
      // far medals first
      const order = [0, 1, 2].sort((a, b) => Math.abs(b - p) - Math.abs(a - p));
      const sw = seg(t, SCRIPT.sweep[0], SCRIPT.sweep[1]);
      const sweep = sw > 0 && sw < 1 ? mix(-2.2, 2.2, smooth(sw)) : 9;
      for (let n = 0; n < order.length; n++) {
        const i = order[n];
        const o = i - p;
        const ao = Math.abs(o);
        if (ao > 2.3) continue;
        const sideK = Math.min(ao, 1);
        const far = Math.max(0, ao - 1);
        const R = RC * mix(1, 0.64, sideK) * mix(1, 0.8, Math.min(far, 1));
        const x = cx + Math.sign(o) * (SP * sideK + far * 110);
        const y = MY + sideK * 4;
        const since = now - spinStart(r, i);
        const sway = 0.05 * Math.sin((2 * Math.PI * 2 * t) / LOOP + i * 1.7);
        const yaw = -Math.max(-1.5, Math.min(1.5, o)) * 0.62 + spinYaw(since) + sway;
        const land = since > SPIN * 0.75 && since < SPIN + 1.2 ? Math.exp(-(since - SPIN * 0.75) * 3.2) : 0;
        const m = METALS[i];
        const paint = Skia.Paint();
        paint.setShader(
          medalFx.makeShader([
            x,
            y,
            R,
            yaw,
            0.1,
            0,
            i + 1,
            m.metal[0],
            m.metal[1],
            m.metal[2],
            m.deep[0],
            m.deep[1],
            m.deep[2],
            m.glassy,
            ao < 0.5 ? sweep : 9,
            -0.42 * sideK + 0.5 * land,
          ]),
        );
        c.drawRect(Skia.XYWHRect(x - R * 1.25, y - R * 1.25, R * 2.5, R * 2.5), paint);
      }
    }
    return rec.finishRecordingAsPicture();
  }, [w, h, cx, MY, RC, SP]);

  // ---------------------------------------------------------------- the copy follows the rail
  const layer = (i: number, dx: number) => {
    'worklet';
    // the outgoing copy is gone before the incoming one shows (no two lines on top of each other)
    const k = clamp01(1 - Math.abs(pos.get() - i) * 2.1);
    return { opacity: Math.pow(k, 1.3), transform: [{ translateX: (i - pos.get()) * dx }] };
  };
  const name0 = useAnimatedStyle(() => ({ ...layer(0, 70), transform: [{ translateX: (0 - pos.get()) * 70 }, { scale: mix(0.92, 1, clamp01(1 - Math.abs(pos.get()) * 2.1)) }] }));
  const name1 = useAnimatedStyle(() => ({ ...layer(1, 70), transform: [{ translateX: (1 - pos.get()) * 70 }, { scale: mix(0.92, 1, clamp01(1 - Math.abs(pos.get() - 1) * 2.1)) }] }));
  const name2 = useAnimatedStyle(() => ({ ...layer(2, 70), transform: [{ translateX: (2 - pos.get()) * 70 }, { scale: mix(0.92, 1, clamp01(1 - Math.abs(pos.get() - 2) * 2.1)) }] }));
  const copy0 = useAnimatedStyle(() => {
    pos.get();
    return layer(0, 28);
  });
  const copy1 = useAnimatedStyle(() => {
    pos.get();
    return layer(1, 28);
  });
  const copy2 = useAnimatedStyle(() => {
    pos.get();
    return layer(2, 28);
  });
  const names = [name0, name1, name2];
  const copies = [copy0, copy1, copy2];
  const fillStyle = useAnimatedStyle(() => {
    const p = Math.max(0, Math.min(TIERS - 1, pos.get()));
    const i = Math.min(TIERS - 2, Math.floor(p));
    const k = p - i;
    return { width: `${mix(DATA[i].fill, DATA[i + 1].fill, k) * 100}%` };
  });
  const fillTint = (i: number) => {
    'worklet';
    return { opacity: clamp01(1 - Math.abs(pos.get() - i)) };
  };
  const tint0 = useAnimatedStyle(() => {
    pos.get();
    return fillTint(0);
  });
  const tint1 = useAnimatedStyle(() => {
    pos.get();
    return fillTint(1);
  });
  const tint2 = useAnimatedStyle(() => {
    pos.get();
    return fillTint(2);
  });
  const tints = [tint0, tint1, tint2];
  const dash = (i: number) => {
    'worklet';
    const k = clamp01(1 - Math.abs(pos.get() - i));
    return { width: mix(22, 30, k), opacity: mix(0.28, 1, k) };
  };
  const dash0 = useAnimatedStyle(() => {
    pos.get();
    return dash(0);
  });
  const dash1 = useAnimatedStyle(() => {
    pos.get();
    return dash(1);
  });
  const dash2 = useAnimatedStyle(() => {
    pos.get();
    return dash(2);
  });
  const dashes = [dash0, dash1, dash2];

  // ---------------------------------------------------------------- touch
  const go = (r: Rail, target: number, now: number, spin: boolean) => {
    'worklet';
    const tg = Math.max(0, Math.min(TIERS - 1, target));
    const changed = tg !== r.target;
    r.target = tg;
    r.stiff = reduced ? 260 : 150;
    if ((changed || spin) && !reduced) setSpin(r, tg, now);
    return changed;
  };
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const r = rail.get();
      const now = clock.get();
      last.set(now);
      drag.set({ startP: r.p, startX: e.absoluteX, at: now, detent: Math.round(r.p), byHand: 1 });
      r.mode = 1;
      r.finger = r.p;
    })
    .onUpdate((e) => {
      const r = rail.get();
      const d = drag.get();
      last.set(clock.get());
      r.finger = rubber(d.startP - e.translationX / SP);
      const dt = Math.round(Math.max(0, Math.min(TIERS - 1, r.finger)));
      if (dt !== d.detent) {
        drag.set({ ...d, detent: dt });
        scheduleOnRN(cue, 'tick');
        scheduleOnRN(buzz, 0);
      }
    })
    .onFinalize((e) => {
      const r = rail.get();
      const d = drag.get();
      const now = clock.get();
      last.set(now);
      r.mode = 0;
      const moved = Math.abs(e.translationX) > 8;
      if (!moved && now - d.at < 0.35) {
        // a tap: a side medal is chosen, the middle one spins
        const o = (e.x - cx) / SP;
        if (Math.abs(e.y - MY) < RC * 1.2) {
          const pick = Math.round(r.p + Math.max(-1, Math.min(1, Math.round(o))));
          const changed = go(r, pick, now, true);
          scheduleOnRN(cue, changed ? 'slide' : 'tick');
          scheduleOnRN(buzz, changed ? 0 : 2);
        }
        return;
      }
      r.v = -e.velocityX / SP;
      const proj = r.p + r.v * 0.16;
      const changed = go(r, Math.round(proj), now, false);
      if (changed) scheduleOnRN(cue, 'slide');
    });

  // runs on the UI thread: the state it changes lives there (a JS-side .get() is a copy)
  const stepUI = (dir: number) => {
    'worklet';
    const r = rail.get();
    const now = clock.get();
    last.set(now);
    if (go(r, r.target + dir, now, false)) scheduleOnRN(buzz, 0);
  };
  const step = (dir: number) => scheduleOnUI(stepUI, dir);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };
  const mark = useMemo(() => CHEVRON(16), []);
  // the copy is laid out once the fonts are in, so no line is measured with a fallback face
  if (!fontsOk) return <View style={styles.screen} onLayout={onLayout} />;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
      ) : null}

      <View style={[styles.nav, { top: insets.top + 2 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back" hitSlop={8} style={styles.navBtn}>
          <Text style={styles.navGlyph} allowFontScaling={false}>
            ‹
          </Text>
        </Pressable>
        <Text style={styles.navTitle} allowFontScaling={false}>
          Season perks
        </Text>
        <Pressable accessibilityRole="button" accessibilityLabel="How levels work" hitSlop={8} style={styles.help}>
          <Text style={styles.helpGlyph} allowFontScaling={false}>
            ?
          </Text>
        </Pressable>
      </View>

      <View style={[styles.stackKicker, { top: K }]} pointerEvents="none">
        {DATA.map((d, i) => (
          <Animated.Text key={d.name} style={[styles.kicker, copies[i]]} allowFontScaling={false} {...hidden(i !== cur)}>
            {d.kicker}
          </Animated.Text>
        ))}
      </View>
      <View style={[styles.stackName, { top: K + 22 }]} pointerEvents="none">
        {DATA.map((d, i) => (
          <Animated.Text key={d.name} style={[styles.name, names[i]]} allowFontScaling={false} {...hidden(i !== cur)}>
            {d.name}
          </Animated.Text>
        ))}
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.rail, { top: MY - RC * 1.3, height: RC * 2.6 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Season level"
          accessibilityValue={{ text: `${DATA[cur].name.toLowerCase()}, level ${cur + 1} of ${TIERS}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
        />
      </GestureDetector>

      <View style={[styles.card, { top: K + 292 }]}>
        <View style={styles.cardTitle}>
          {DATA.map((d, i) => (
            <Animated.Text key={d.name} style={[styles.title, copies[i]]} allowFontScaling={false} {...hidden(i !== cur)}>
              {d.title}
            </Animated.Text>
          ))}
        </View>
        <View style={styles.row}>
          <View style={styles.track}>
            <Animated.View style={[styles.fill, fillStyle]}>
              {BAR.map((b, i) => (
                <Animated.View key={b} style={[StyleSheet.absoluteFill, { backgroundColor: b }, tints[i]]} />
              ))}
            </Animated.View>
          </View>
          <View style={styles.nums}>
            {DATA.map((d, i) => (
              <Animated.Text key={d.name} style={[styles.numText, copies[i]]} allowFontScaling={false} {...hidden(i !== cur)}>
                {d.nums}
              </Animated.Text>
            ))}
          </View>
          <View style={styles.mark}>
            <Canvas style={styles.markCanvas}>
              <Path path={mark} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color="#141416" />
            </Canvas>
          </View>
        </View>
        <View style={styles.footBox}>
          {DATA.map((d, i) => (
            <Animated.Text key={d.name} style={[styles.foot, copies[i]]} allowFontScaling={false} {...hidden(i !== cur)}>
              {d.foot}
            </Animated.Text>
          ))}
        </View>
      </View>

      <View style={[styles.dashes, { top: K + 446 }]} pointerEvents="none">
        {DATA.map((d, i) => (
          <Animated.View key={d.name} style={[styles.dash, dashes[i]]} />
        ))}
      </View>

      <Text style={[styles.claimTitle, { top: K + 470 }]} allowFontScaling={false}>
        Claim your rewards
      </Text>
      <View style={[styles.reward, { top: K + 516 }]}>
        <View style={styles.rewardRow}>
          <View style={styles.rewardIcon}>
            <Canvas style={styles.teeCanvas}>
              <Path path={TEE} color="#ffffff" />
            </Canvas>
          </View>
          <View style={styles.rewardText}>
            <Text style={styles.rewardTitle} allowFontScaling={false}>
              7% back on clothing and shoes
            </Text>
            <Text style={styles.rewardSub} allowFontScaling={false}>
              Paid as bonus points with a Season card
            </Text>
          </View>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Claim 7% back on clothing and shoes"
          style={({ pressed }) => [styles.claim, pressed && styles.claimPressed]}
          onPress={() => buzz(1)}
        >
          <Text style={styles.claimText} allowFontScaling={false}>
            Claim
          </Text>
        </Pressable>
      </View>
      <View style={[styles.reward, { top: K + 516 + 148 }]}>
        <View style={styles.rewardRow}>
          <View style={[styles.rewardIcon, styles.rewardIconTwo]}>
            <Text style={styles.rewardGlyph} allowFontScaling={false}>
              ×2
            </Text>
          </View>
          <View style={styles.rewardText}>
            <Text style={styles.rewardTitle} allowFontScaling={false}>
              Double points at cafés
            </Text>
            <Text style={styles.rewardSub} allowFontScaling={false}>
              Every weekend until the season ends
            </Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#121214' },
  nav: { position: 'absolute', left: 8, right: 8, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  navGlyph: { color: '#ffffff', fontSize: 34, lineHeight: 38, fontFamily: 'STM-Regular', marginTop: -4 },
  navTitle: { color: '#ffffff', fontSize: 17, fontFamily: 'STM-SemiBold' },
  help: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  helpGlyph: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 2,
    borderColor: '#ffffff',
    color: '#ffffff',
    textAlign: 'center',
    lineHeight: 22,
    fontSize: 15,
    fontFamily: 'STM-SemiBold',
    overflow: 'hidden',
  },
  stackKicker: { position: 'absolute', left: 0, right: 0, height: 20 },
  kicker: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#e8e8ea', fontSize: 15, fontFamily: 'STM-Medium' },
  stackName: { position: 'absolute', left: 0, right: 0, height: 56 },
  name: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#f4f4f6', fontSize: 50, lineHeight: 56, fontFamily: 'STM-Display', letterSpacing: 0.5 },
  rail: { position: 'absolute', left: 0, right: 0 },
  card: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 140,
    borderRadius: 32,
    backgroundColor: 'rgba(66,68,74,0.62)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.14)',
    paddingHorizontal: 24,
    paddingTop: 22,
  },
  cardTitle: { height: 26 },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#ffffff', fontSize: 20, lineHeight: 26, fontFamily: 'STM-Medium' },
  row: { marginTop: 14, height: 24, flexDirection: 'row', alignItems: 'center', gap: 10 },
  track: { flex: 1, height: 10, borderRadius: 5, backgroundColor: 'rgba(255,255,255,0.16)', overflow: 'hidden' },
  fill: { height: 10, borderRadius: 5, overflow: 'hidden' },
  nums: { width: 100, height: 24 },
  numText: { position: 'absolute', right: 0, color: '#ffffff', fontSize: 16, lineHeight: 24, fontFamily: 'STM-Regular', fontVariant: ['tabular-nums'] },
  mark: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  markCanvas: { width: 16, height: 16 },
  footBox: { marginTop: 12, height: 20 },
  foot: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.72)', fontSize: 14, lineHeight: 20, fontFamily: 'STM-Regular' },
  dashes: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  dash: { height: 4, borderRadius: 2, backgroundColor: '#ffffff' },
  claimTitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#ffffff', fontSize: 26, lineHeight: 32, fontFamily: 'STM-SemiBold' },
  reward: { position: 'absolute', left: 16, right: 16, borderRadius: 26, backgroundColor: '#29292d', padding: 16, gap: 14 },
  rewardRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  rewardIcon: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#c070d8', alignItems: 'center', justifyContent: 'center' },
  rewardIconTwo: { backgroundColor: '#e0a33c' },
  rewardGlyph: { color: '#ffffff', fontSize: 16, fontFamily: 'STM-SemiBold' },
  teeCanvas: { width: 32, height: 28 },
  rewardText: { flex: 1, gap: 2 },
  rewardTitle: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontFamily: 'STM-Medium' },
  rewardSub: { color: 'rgba(255,255,255,0.6)', fontSize: 14, lineHeight: 19, fontFamily: 'STM-Regular' },
  claim: { height: 48, borderRadius: 16, backgroundColor: '#0d0d0f', alignItems: 'center', justifyContent: 'center' },
  claimPressed: { transform: [{ scale: 0.97 }], opacity: 0.9 },
  claimText: { color: '#ffffff', fontSize: 17, fontFamily: 'STM-SemiBold' },
});

export { LOOP };
