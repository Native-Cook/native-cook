import { Canvas, Picture, type SkImage, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { ICINGS, drawCake, drawNozzle, makeGeo } from './icingArt';
import { bakeCake, bakeTable } from './icingBake';
import { type Lettering, MAX_LETTERS, layoutMessage, pipeSchedule } from './icingFont';
import { LOOP, REST_TEXT, SECOND_TEXT, TEXT_SCRIPT, chipPosition, easeInOut, priceAt, scriptIndex, seg } from './icingMotion';
import { drawLoopFrame, drawWriting, parkPoint } from './icingScene';
import { CHIP, CHIP_GAP, INK, styles } from './icingStyles';
import { IcingName, Price, type UserWriting, priceFor, userFracs, userPen, userShift, zeros } from './icingParts';

/*
 Icing Name Form — "Crumb & Co · Write on the cake". An 8-inch cake seen from above on a gold drum,
 a ring of piped rosettes round its edge. Whatever is typed in the message field is piped onto it in
 buttercream: the nozzle follows each letter's strokes and lays a glossy tube with a pressure bulge
 where it starts, lifting between strokes. The colour chips re-pipe the message in another icing,
 which flows through the letters. The price rolls with every letter.

 Touch: typing pipes each new letter (0.22 s + 5 ms a pt of icing); the message re-centres with a
 0.35 s glide; a chip flows the new colour through the letters in 0.7 s. Drag on the cake to turn the
 turntable (it follows the finger round its centre and coasts on release). With autoPlay, 2 s after
 the last touch (and with the field closed) the cake turns home and cross-fades back to the loop's
 first frame in 0.8 s. Reduced motion: "Happy birthday" stays piped; typed letters appear whole.

 One Canvas: counter and cake baked once at 2×; per frame the cake image, the lettering paths, the
 knife and the piping bag are recorded into one picture.
 */

const AUTO = 0;
const USER = 1;
const RETURN = 2;
const STILL = 3;
const RETURN_FOR = 0.8;
const GLIDE = 0.35;
const FLOW_FOR = 0.7;
const PRICE_ROLL = 0.45;
const EASE_SHEET = Easing.bezier(0.32, 0.72, 0, 1);


export function IcingNameForm({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hero, setHero] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [text, setText] = useState(REST_TEXT);
  const [icing, setIcing] = useState(0);
  const input = useRef<TextInput>(null);

  const geo = useMemo(() => (size.width > 0 && hero.width > 0 ? makeGeo(size.width, size.height, hero) : null), [size, hero]);
  const restL = useMemo(() => (geo ? layoutMessage(REST_TEXT, geo.boxW, geo.boxH) : null), [geo]);
  const secondL = useMemo(() => (geo ? layoutMessage(SECOND_TEXT, geo.boxW, geo.boxH) : null), [geo]);
  const sched = useMemo(() => (secondL ? pipeSchedule(secondL) : { t0: [], t1: [] }), [secondL]);
  const park = useMemo(() => (geo ? parkPoint(geo) : [0, 0]), [geo]);
  const table = useSharedValue<SkImage | null>(null);
  const cake = useSharedValue<SkImage | null>(null);
  useEffect(() => {
    if (!geo) return undefined;
    const t = bakeTable(geo, 2);
    const c = bakeCake(geo, 2);
    table.set(t);
    cake.set(c);
    return () => {
      table.set(null);
      cake.set(null);
      t?.dispose();
      c?.dispose();
    };
  }, [geo, table, cake]);

  const now = useSharedValue(0);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(auto ? AUTO : STILL);
  const modeAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const focused = useSharedValue(0);
  const touching = useSharedValue(0);
  const turn = useSharedValue(0);
  const spin = useSharedValue(0);
  const turnFrom = useSharedValue(0);
  const grab = useSharedValue(0);
  const pen = useSharedValue<number[]>([0, 0, 1]);
  const chipUser = useSharedValue(0);
  const priceUser = useSharedValue<number[]>([4450, 4450, 0]);
  const user = useSharedValue<UserWriting | null>(null);
  const fieldBottom = useSharedValue(0);

  const resetToRest = () => {
    setText(REST_TEXT);
    setIcing(0);
  };

  useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 16) / 1000);
    const t = now.get() + dt;
    now.set(t);
    const m = mode.get();
    if (m === AUTO) loopT.set((loopT.get() + dt) % LOOP);
    if (m === USER && auto && focused.get() === 0 && touching.get() === 0 && t - lastTouch.get() > 2) {
      mode.set(RETURN);
      modeAt.set(t);
      // The turntable goes home by the shortest way.
      const a = turn.get();
      turnFrom.set(a - Math.round(a / (2 * Math.PI)) * 2 * Math.PI);
      scheduleOnRN(resetToRest);
    }
    if (m === RETURN) {
      const k = easeInOut(seg(t, modeAt.get(), modeAt.get() + RETURN_FOR));
      turn.set(turnFrom.get() * (1 - k));
      spin.set(0);
      if (k >= 1) {
        mode.set(AUTO);
        loopT.set(0);
        turn.set(0);
      }
    }
    if (m === USER && touching.get() === 0) {
      // The turntable coasts after a flick.
      turn.set(turn.get() + spin.get() * dt);
      spin.set(spin.get() * Math.exp(-dt * 2.4));
    }
    const u = user.get();
    if ((m === USER || m === RETURN) && u && geo) {
      const fr = userFracs(u, t, reduced);
      const target = userPen(u, fr, userShift(u, t, 0), userShift(u, t, 1), park);
      const p = pen.get();
      const k = 1 - Math.exp(-dt * (target[2] > 0.5 ? 30 : 8));
      pen.set([p[0] + (target[0] - p[0]) * k, p[1] + (target[1] - p[1]) * k, p[2] + ((target[2] > 0.5 ? 0 : 1) - p[2]) * (1 - Math.exp(-dt * 10))]);
    }
  });

  useAnimatedReaction(
    () => (mode.get() === AUTO ? scriptIndex(loopT.get()) : -1),
    (i, prev) => {
      if (i >= 0 && i !== prev) scheduleOnRN(setText, TEXT_SCRIPT[i][1]);
    },
  );
  useAnimatedReaction(
    () => (mode.get() === AUTO ? Math.round(chipPosition(loopT.get())) : -1),
    (i, prev) => {
      if (i >= 0 && i !== prev) scheduleOnRN(setIcing, i);
    },
  );

  const chipPos = useDerivedValue(() => {
    const m = mode.get();
    if (m === AUTO) return chipPosition(loopT.get());
    if (m === RETURN) return chipUser.get() * (1 - easeInOut(seg(now.get(), modeAt.get(), modeAt.get() + RETURN_FOR)));
    if (m === STILL) return 0;
    return chipUser.get();
  });

  const price = useDerivedValue(() => {
    const m = mode.get();
    if (m === AUTO) return priceAt(loopT.get());
    if (m === STILL) return [4450, 4450, 1];
    const p = priceUser.get();
    if (m === RETURN) return [p[1], 4450, easeInOut(seg(now.get(), modeAt.get(), modeAt.get() + PRICE_ROLL))];
    return [p[0], p[1], easeInOut(seg(now.get(), p[2], p[2] + PRICE_ROLL))];
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const tb = table.get();
    const ck = cake.get();
    if (!geo || !tb || !ck || !restL || !secondL) return recorder.finishRecordingAsPicture();
    canvas.drawImageRect(tb, Skia.XYWHRect(0, 0, tb.width(), tb.height()), Skia.XYWHRect(0, 0, geo.w, geo.h), Skia.Paint());
    const m = mode.get();
    const u = user.get();
    if (m === AUTO || m === STILL || !u) {
      drawLoopFrame(canvas, geo, ck, m === AUTO ? loopT.get() : 0, restL, secondL, sched.t0, sched.t1, 1, true);
      return recorder.finishRecordingAsPicture();
    }
    const tNow = now.get();
    const k = m === RETURN ? easeInOut(seg(tNow, modeAt.get(), modeAt.get() + RETURN_FOR)) : 0;
    const a = turn.get();
    drawCake(canvas, geo, ck, geo.cx, geo.cy, a, 1);
    const flow = reduced ? 1 : easeInOut(seg(tNow, u.colorAt, u.colorAt + FLOW_FOR));
    drawWriting(canvas, u.L, geo.cx, geo.cy, a, userFracs(u, tNow, reduced), userShift(u, tNow, 0), userShift(u, tNow, 1), u.colorFrom, u.colorTo, flow, 1 - k, -1e5);
    if (k > 0) drawWriting(canvas, restL, geo.cx, geo.cy, a, null, null, null, 0, 0, 0, k, -1e5);
    const p = pen.get();
    const c = Math.cos(a);
    const s = Math.sin(a);
    // The nozzle works in the cake's frame while it pipes; at rest it hangs where it always does.
    const busy = 1 - p[2];
    const lx = p[0] * c - p[1] * s;
    const ly = p[0] * s + p[1] * c;
    const x = lx * busy + park[0] * (1 - busy);
    const y = ly * busy + park[1] * (1 - busy);
    drawNozzle(canvas, geo.cx + x + (park[0] - x) * k, geo.cy + y + (park[1] - y) * k, Math.max(p[2], k), ICINGS[u.colorTo].rgb);
    return recorder.finishRecordingAsPicture();
  });

  // What the cake shows right now, ready for a hand-over on the UI thread.
  const shown = useSharedValue<{ L: Lettering | null; colour: number; cents: number }>({ L: null, colour: 0, cents: 4450 });
  useEffect(() => {
    if (!geo) return;
    shown.set({ L: layoutMessage(text, geo.boxW, geo.boxH), colour: icing, cents: priceFor(text, icing) });
  }, [geo, text, icing, shown]);

  /** Hands the screen to the user with whatever message the cake shows. */
  function takeOver() {
    'worklet';
    const t = now.get();
    lastTouch.set(t);
    if (mode.get() === USER && user.get()) return;
    const sh = shown.get();
    const L = sh.L;
    if (!L) return;
    const typedAt: number[] = [];
    for (let i = 0; i < L.firstStroke.length; i += 1) typedAt.push(-100);
    user.set({
      L,
      typedAt,
      fromX: zeros(L.firstStroke.length),
      fromY: zeros(L.firstStroke.length),
      movedAt: -100,
      colorFrom: sh.colour,
      colorTo: sh.colour,
      colorAt: -100,
    });
    chipUser.set(sh.colour);
    priceUser.set([sh.cents, sh.cents, -100]);
    pen.set([park[0], park[1], 1]);
    if (mode.get() !== RETURN) turn.set(0);
    mode.set(USER);
  }

  const onType = (value: string) => {
    if (!geo) return;
    const next = Array.from(value).slice(0, MAX_LETTERS).join('');
    const prevText = text;
    setText(next);
    const t = now.get();
    const prev = user.get();
    const L = layoutMessage(next, geo.boxW, geo.boxH);
    const typedAt: number[] = [];
    const fromX: number[] = [];
    const fromY: number[] = [];
    const prevChars = Array.from(prevText);
    const chars = Array.from(next);
    const k = prev ? 1 - easeInOut(seg(t, prev.movedAt, prev.movedAt + GLIDE)) : 0;
    for (let i = 0; i < chars.length; i += 1) {
      const same = !!prev && i < prev.L.originX.length && prevChars[i] === chars[i] && Math.abs(prev.L.size - L.size) < 0.5;
      typedAt.push(same && prev ? prev.typedAt[i] : t);
      fromX.push(same && prev ? prev.L.originX[i] + prev.fromX[i] * k - L.originX[i] : 0);
      fromY.push(same && prev ? prev.L.originY[i] + prev.fromY[i] * k - L.originY[i] : 0);
    }
    const colour = prev ? prev.colorTo : icing;
    const u: UserWriting = {
      L,
      typedAt,
      fromX,
      fromY,
      movedAt: t,
      colorFrom: prev ? prev.colorFrom : colour,
      colorTo: colour,
      colorAt: prev ? prev.colorAt : -100,
    };
    user.set(u);
    const before = priceFor(prevText, colour);
    priceUser.set([before, priceFor(next, colour), t]);
    lastTouch.set(t);
  };

  const pickIcing = (i: number) => {
    if (mode.get() !== USER) scheduleOnUI(takeOver);
    setIcing(i);
    Haptics.selectionAsync().catch(() => {});
    const message = text;
    const from = priceFor(message, icing);
    const to = priceFor(message, i);
    scheduleOnUI(() => {
      'worklet';
      const t = now.get();
      const u = user.get();
      if (!u) return;
      const flowDone = t - u.colorAt >= FLOW_FOR;
      const next: UserWriting = { ...u, colorFrom: flowDone ? u.colorTo : u.colorFrom, colorTo: i, colorAt: t };
      user.set(next);
      chipUser.set(withSpring(i, { duration: 400, dampingRatio: 0.75 }));
      priceUser.set([from, to, t]);
      lastTouch.set(t);
    });
  };

  const onFocus = () => {
    focused.set(1);
    if (mode.get() !== USER) scheduleOnUI(takeOver);
  };
  const onBlur = () => {
    focused.set(0);
    lastTouch.set(now.get());
  };

  const turntable = Gesture.Pan()
    .minDistance(2)
    .onBegin((e) => {
      if (!geo) return;
      touching.set(1);
      grab.set(Math.atan2(hero.y + e.y - geo.cy, hero.x + e.x - geo.cx));
      spin.set(0);
    })
    .onStart(() => {
      if (mode.get() !== USER) takeOver();
    })
    .onChange((e) => {
      if (!geo) return;
      const ang = Math.atan2(hero.y + e.y - geo.cy, hero.x + e.x - geo.cx);
      let d = ang - grab.get();
      if (d > Math.PI) d -= 2 * Math.PI;
      if (d < -Math.PI) d += 2 * Math.PI;
      grab.set(ang);
      if (mode.get() === USER) turn.set(turn.get() + d);
      lastTouch.set(now.get());
    })
    .onEnd((e) => {
      if (!geo) return;
      // Angular speed from the finger's velocity across the radius.
      const rx = hero.x + e.x - geo.cx;
      const ry = hero.y + e.y - geo.cy;
      const r2 = Math.max(400, rx * rx + ry * ry);
      spin.set(Math.max(-9, Math.min(9, (rx * e.velocityY - ry * e.velocityX) / r2)));
    })
    .onFinalize(() => {
      touching.set(0);
      lastTouch.set(now.get());
    });

  const keyboard = useSharedValue(0);
  useEffect(() => {
    const ios = process.env.EXPO_OS === 'ios';
    const show = Keyboard.addListener(ios ? 'keyboardWillShow' : 'keyboardDidShow', (e) => {
      keyboard.set(withTiming(e.endCoordinates.height, { duration: e.duration || 250, easing: EASE_SHEET }));
    });
    const hide = Keyboard.addListener(ios ? 'keyboardWillHide' : 'keyboardDidHide', (e) => {
      keyboard.set(withTiming(0, { duration: e?.duration || 250, easing: EASE_SHEET }));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [keyboard]);
  const lift = useAnimatedStyle(() => {
    const room = size.height - fieldBottom.get() - 12;
    return { transform: [{ translateY: -Math.max(0, keyboard.get() - room) }] };
  });
  const ring = useAnimatedStyle(() => ({ transform: [{ translateX: chipPos.get() * (CHIP + CHIP_GAP) }] }));

  const onRoot = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };
  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (width !== hero.width || height !== hero.height || y !== hero.y) setHero({ x, y, width, height });
  };

  return (
    <View style={styles.root} onLayout={onRoot}>
      <Animated.View style={[StyleSheet.absoluteFill, lift]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
        <View style={[styles.column, { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 12 }]}>
          <View style={styles.nav}>
            <View style={styles.navButton}>
              <SymbolView name="chevron.left" size={17} weight="semibold" tintColor={INK} fallback={<Text style={styles.navGlyph}>‹</Text>} />
            </View>
            <Text style={styles.navTitle}>Crumb &amp; Co</Text>
            <Text style={styles.step}>Step 2 of 3</Text>
          </View>
          <View style={styles.titles}>
            <Text style={styles.title}>Write on the cake</Text>
            <Text style={styles.subtitle}>Piped by hand in Swiss meringue buttercream</Text>
          </View>

          <GestureDetector gesture={turntable}>
            <View style={styles.hero} onLayout={onHero} accessible accessibilityLabel={`Cake preview with ${text ? `"${text}"` : 'no message'} piped on top. Drag to turn the cake.`} />
          </GestureDetector>

          <View
            style={styles.field}
            onLayout={(e) => {
              fieldBottom.set(e.nativeEvent.layout.y + e.nativeEvent.layout.height);
            }}
          >
            <Text style={styles.fieldLabel}>Message</Text>
            <TextInput
              ref={input}
              value={text}
              onChangeText={onType}
              onFocus={onFocus}
              onBlur={onBlur}
              maxLength={MAX_LETTERS}
              autoCorrect={false}
              returnKeyType="done"
              placeholder="Write something sweet"
              placeholderTextColor="#BFA998"
              selectionColor={ICINGS[icing].hex}
              style={styles.input}
            />
            <Text style={styles.letters}>
              {Array.from(text).length}/{MAX_LETTERS}
            </Text>
          </View>

          <View style={styles.icingRow}>
            <View style={styles.icingLabel}>
              <Text style={styles.small}>Icing</Text>
              <IcingName position={chipPos} />
            </View>
            <View style={styles.chips}>
              <Animated.View pointerEvents="none" style={[styles.ring, ring]} />
              {ICINGS.map((ic, i) => (
                <Pressable
                  key={ic.name}
                  onPress={() => pickIcing(i)}
                  hitSlop={6}
                  accessibilityRole="button"
                  accessibilityLabel={`${ic.name} icing`}
                  accessibilityState={{ selected: icing === i }}
                  style={({ pressed }) => [styles.chip, { backgroundColor: ic.hex, transform: [{ scale: pressed ? 0.94 : 1 }] }]}
                >
                  <View style={styles.chipShine} />
                </Pressable>
              ))}
            </View>
          </View>

          <View style={styles.footer}>
            <View style={styles.total}>
              <Text style={styles.small}>Total</Text>
              <Text style={styles.cakeLine}>Vanilla bean · 8 in · serves 10</Text>
            </View>
            <Price price={price} />
          </View>
          <Pressable style={({ pressed }) => [styles.cta, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
            <Text style={styles.ctaText}>Add to order</Text>
          </Pressable>
        </View>
      </Animated.View>
    </View>
  );
}
