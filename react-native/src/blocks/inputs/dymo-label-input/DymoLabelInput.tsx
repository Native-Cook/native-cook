import { Canvas, Picture, type SkImage, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, makeGeo } from './dymoArt';
import { bakeBody, bakeJar, bakeWall, bakeWheel } from './dymoBake';
import { LOOP, TAPES, WHEEL, WORD, easeInOut, loopPose, loopText, seg, slotOf } from './dymoMotion';
import { CHIP, CHIP_GAP, INK, styles } from './dymoStyles';
import { FRESH, type UserTape, angleFor, slotAt, userPose } from './dymoUser';

/*
 Dymo Label Input — "Shelfwise · Label the jar". A handheld embossing label maker sits on the counter
 under a pantry shelf. Typing turns its character wheel to each letter (a spin with a detent click),
 the trigger is squeezed and the letter is pressed into glossy red tape as raised, stress-whitened
 plastic; the tape steps along. Return (or Cut) snaps the cutter and the strip flies onto the jar
 of oats, where it sticks with a squash and wraps round the glass.

 Touch: type in the field; or turn the wheel with a finger (detents, a tick each) and tap the
 trigger to press the letter under the head; tape colour chips re-colour the tape (0.25 s). After a
 cut the labelled jar slides away and a fresh one comes in. With autoPlay, 2 s after the last touch
 (field closed, no strip in flight) the scene cross-fades to the loop's first frame (0.8 s).
 Reduced motion: the rest frame, still; typed letters appear pressed at once, the strip lands
 without flying.

 One Canvas. Wall, shelf, jar, body and wheel are baked once at 2×; per frame the picture places
 them and draws the tape and its letters.
 */

const AUTO = 0;
const USER = 1;
const RETURN = 2;
const STILL = 3;
const RETURN_FOR = 0.8;
const MAX = 12;
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const EASE_SHEET = Easing.bezier(0.32, 0.72, 0, 1);
const EASE_SPIN = Easing.bezier(0.65, 0, 0.35, 1);


export function DymoLabelInput({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hero, setHero] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [text, setText] = useState('');
  const [tape, setTape] = useState(0);
  const input = useRef<TextInput>(null);

  const geo = useMemo(() => (size.width > 0 && hero.width > 0 ? makeGeo(size.width, size.height, hero) : null), [size, hero]);
  const font = useMemo(() => matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '700' }), []);
  const wheelFont = useMemo(() => matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '700' }), []);
  const widths = useMemo(() => {
    const out: Record<string, number> = {};
    for (const ch of WHEEL) out[ch] = font.getGlyphWidths(font.getGlyphIDs(ch))[0] ?? 9;
    return out;
  }, [font]);
  const art = useSharedValue<{ wall: SkImage; jar: SkImage; body: SkImage; wheel: SkImage } | null>(null);
  useEffect(() => {
    if (!geo) return undefined;
    const wall = bakeWall(geo, 2);
    const jar = bakeJar(geo, 2);
    const body = bakeBody(geo, 2);
    const wheel = bakeWheel(geo, 2, wheelFont);
    if (wall && jar && body && wheel) art.set({ wall, jar, body, wheel });
    return () => {
      art.set(null);
      wall?.dispose();
      jar?.dispose();
      body?.dispose();
      wheel?.dispose();
    };
  }, [geo, wheelFont, art]);

  const now = useSharedValue(0);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(auto ? AUTO : STILL);
  const modeAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const focused = useSharedValue(0);
  const touching = useSharedValue(0);
  const wheel = useSharedValue(0);
  const lastSlot = useSharedValue(0);
  const user = useSharedValue<UserTape>(FRESH);
  const fieldBottom = useSharedValue(0);

  const resetToRest = () => {
    setText('');
    setTape(0);
  };

  useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 16) / 1000);
    const t = now.get() + dt;
    now.set(t);
    const m = mode.get();
    if (m === AUTO) loopT.set((loopT.get() + dt) % LOOP);
    const u = user.get();
    const busy = u.cutAt > 0 && t < u.cutAt + 3.6;
    const lastE = u.e.length > 0 ? u.e[u.e.length - 1] : -100;
    if (m === USER && auto && focused.get() === 0 && touching.get() === 0 && !busy && t - Math.max(lastTouch.get(), lastE) > 2) {
      mode.set(RETURN);
      modeAt.set(t);
      wheel.set(withTiming(angleFor(0, wheel.get()), { duration: 700, easing: EASE_SPIN }));
      scheduleOnRN(resetToRest);
    }
    if (m === RETURN && t - modeAt.get() >= RETURN_FOR) {
      mode.set(AUTO);
      loopT.set(0);
      user.set(FRESH);
    }
  });

  useAnimatedReaction(
    () => (mode.get() === AUTO ? loopText(loopT.get()) : -1),
    (n, prev) => {
      if (n >= 0 && n !== prev) scheduleOnRN(setText, WORD.slice(0, n));
    },
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    const a = art.get();
    if (!geo || !a) return recorder.finishRecordingAsPicture();
    const m = mode.get();
    if (m === AUTO || m === STILL) {
      drawScene(canvas, geo, a, loopPose(m === AUTO ? loopT.get() : 0), font, widths, 1);
    } else {
      const tNow = now.get();
      drawScene(canvas, geo, a, userPose(user.get(), tNow, wheel.get(), reduced), font, widths, 1);
      if (m === RETURN) {
        const k = easeInOut(seg(tNow, modeAt.get(), modeAt.get() + RETURN_FOR));
        const layer = Skia.Paint();
        layer.setAlphaf(k);
        canvas.saveLayer(layer);
        drawScene(canvas, geo, a, loopPose(0), font, widths, 1);
        canvas.restore();
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  /** Hands the machine to the user as the loop left it. */
  function takeOver() {
    'worklet';
    const t = now.get();
    lastTouch.set(t);
    const m = mode.get();
    if (m === USER) return;
    if (m === AUTO) {
      const lt = loopT.get();
      const p = loopPose(lt);
      const e: number[] = [];
      for (let i = 0; i < p.tape.length; i += 1) e.push(t - 1);
      const cut = lt >= 4.7 && lt < 8.4;
      user.set({ ...FRESH, text: p.tape, e, strip: cut ? WORD : '', cutAt: cut ? t - (lt - 4.75) : -100 });
      wheel.set(p.wheel);
      lastSlot.set(slotAt(p.wheel));
      scheduleOnRN(setText, p.tape);
    } else if (m === STILL || m === RETURN) {
      user.set(FRESH);
      wheel.set(m === RETURN ? wheel.get() : 0);
    }
    mode.set(USER);
  }

  const strike = (delayMs: number) => {
    if (process.env.EXPO_OS === 'web') return;
    setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}), Math.max(0, delayMs));
  };
  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  /** Presses `added` letters after the ones on the tape, spinning the wheel to each unless `spun`. */
  const press = (current: string, added: string, spun: boolean) => {
    const t = now.get();
    const u = user.get();
    const e = u.e.slice(0, current.length);
    let last = e.length > 0 ? e[e.length - 1] : t - 1;
    let angle = wheel.get();
    for (let i = 0; i < added.length; i += 1) {
      const slot = slotOf(added[i]);
      const target = angleFor(slot, angle);
      const needsSpin = !spun && Math.abs(target - angle) > 0.5;
      const at = Math.max(t + (needsSpin ? 0.36 : 0.04), last + 0.42);
      if (needsSpin && !reduced) {
        wheel.set(withDelay(Math.max(0, (at - 0.36 - t) * 1000), withTiming(target, { duration: 320, easing: EASE_SPIN })));
      } else if (needsSpin) wheel.set(target);
      angle = target;
      e.push(at);
      last = at;
      strike((at - t) * 1000);
    }
    user.set({ ...u, text: current + added, e });
    lastTouch.set(t);
  };

  const onType = (value: string) => {
    const clean = Array.from(value.toUpperCase())
      .filter((c) => WHEEL.includes(c))
      .join('')
      .slice(0, MAX);
    if (mode.get() !== USER) scheduleOnUI(takeOver);
    const prev = user.get().text;
    setText(clean);
    if (clean.startsWith(prev)) press(prev, clean.slice(prev.length), false);
    else {
      // Edited in the middle or deleted: the tape shows the new text, already pressed.
      const t = now.get();
      user.set({ ...user.get(), text: clean, e: Array.from(clean).map(() => t - 1) });
      lastTouch.set(t);
    }
  };

  const cut = () => {
    const u = user.get();
    if (u.text.length === 0) return;
    const t = now.get();
    user.set({ ...u, text: '', e: [], strip: u.text, stripColour: u.colour, cutAt: t });
    setText('');
    lastTouch.set(t);
    if (process.env.EXPO_OS !== 'web') {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
      setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}), reduced ? 0 : 1200);
    }
  };

  const onCut = () => {
    if (mode.get() !== USER) {
      scheduleOnUI(takeOver);
      setTimeout(cut, 0);
    } else cut();
  };

  const pickTape = (i: number) => {
    if (mode.get() !== USER) scheduleOnUI(takeOver);
    setTape(i);
    tick();
    scheduleOnUI(() => {
      'worklet';
      const t = now.get();
      const u = user.get();
      const shown = seg(t, u.colourAt, u.colourAt + 0.25) >= 1 ? u.colour : u.colourFrom;
      user.set({ ...u, colourFrom: shown, colour: i, colourAt: t });
      lastTouch.set(t);
    });
  };

  const pressTrigger = () => {
    const ch = WHEEL[slotAt(wheel.get())];
    if (!ch || ch === ' ') return;
    const prev = user.get().text;
    if (prev.length >= MAX) return;
    setText(prev + ch);
    press(prev, ch, true);
  };

  // Turn the wheel with a finger; tap the trigger (anywhere on the machine's handle) to press.
  const turnWheel = Gesture.Pan()
    .minDistance(3)
    .onBegin(() => {
      touching.set(1);
    })
    .onStart(() => {
      takeOver();
    })
    .onChange((e) => {
      if (!geo) return;
      const x = hero.x + e.x - geo.wheelX;
      const y = hero.y + e.y - geo.wheelY;
      const r2 = Math.max(200, x * x + y * y);
      // Tangential finger motion turns the wheel about its centre.
      const d = ((x * e.changeY - y * e.changeX) / r2) * (180 / Math.PI);
      wheel.set(wheel.get() + d);
      const slot = slotAt(wheel.get());
      if (slot !== lastSlot.get()) {
        lastSlot.set(slot);
        scheduleOnRN(tick);
      }
      lastTouch.set(now.get());
    })
    .onEnd((e) => {
      if (!geo) return;
      const x = hero.x + e.x - geo.wheelX;
      const y = hero.y + e.y - geo.wheelY;
      const r2 = Math.max(200, x * x + y * y);
      const v = ((x * e.velocityY - y * e.velocityX) / r2) * (180 / Math.PI);
      const target = angleFor(slotAt(wheel.get() + v * 0.12), wheel.get());
      wheel.set(withSpring(target, { velocity: v, duration: 400, dampingRatio: 0.8 }));
    })
    .onFinalize(() => {
      touching.set(0);
      lastTouch.set(now.get());
    });
  const tapTrigger = Gesture.Tap()
    .maxDistance(8)
    .onEnd((e) => {
      if (!geo) return;
      const x = hero.x + e.x;
      const y = hero.y + e.y;
      // The handle and trigger: right of the wheel, below the tape.
      if (x > geo.wheelX + geo.wheelR * 0.4 && y > geo.wheelY - geo.wheelR * 0.5 && y < geo.wheelY + geo.wheelR * 1.1) {
        takeOver();
        scheduleOnRN(pressTrigger);
      }
    });
  const machine = Gesture.Exclusive(turnWheel, tapTrigger);

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

  const ringPos = useDerivedValue(() => (mode.get() === AUTO || mode.get() === STILL ? 0 : user.get().colour));
  const ringX = useSharedValue(0);
  useAnimatedReaction(
    () => ringPos.get(),
    (v, prev) => {
      if (v !== prev) ringX.set(reduced ? v : withSpring(v, { duration: 400, dampingRatio: 0.75 }));
    },
  );
  const ring = useAnimatedStyle(() => ({ transform: [{ translateX: ringX.get() * (CHIP + CHIP_GAP) }] }));

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
            <Text style={styles.navTitle}>Shelfwise</Text>
            <Text style={styles.where}>Pantry · Shelf 2</Text>
          </View>
          <View style={styles.titles}>
            <Text style={styles.title}>Label the jar</Text>
            <Text style={styles.subtitle}>Pressed into 9 mm glossy tape</Text>
          </View>

          <GestureDetector gesture={machine}>
            <View
              style={styles.hero}
              onLayout={onHero}
              accessible
              accessibilityLabel={`Label maker. Tape reads ${text || 'nothing yet'}. Turn the wheel and tap the handle to press a letter.`}
            />
          </GestureDetector>

          <View
            style={styles.fieldRow}
            onLayout={(e) => {
              fieldBottom.set(e.nativeEvent.layout.y + e.nativeEvent.layout.height);
            }}
          >
            <View style={styles.field}>
              <Text style={styles.fieldLabel}>Label text</Text>
              <TextInput
                ref={input}
                value={text}
                onChangeText={onType}
                onFocus={() => {
                  focused.set(1);
                  if (mode.get() !== USER) scheduleOnUI(takeOver);
                }}
                onBlur={() => {
                  focused.set(0);
                  lastTouch.set(now.get());
                }}
                onSubmitEditing={onCut}
                submitBehavior="submit"
                autoCapitalize="characters"
                autoCorrect={false}
                maxLength={MAX}
                returnKeyType="done"
                placeholder="TYPE A LABEL"
                placeholderTextColor="#BDAF9E"
                selectionColor={TAPES[tape].hex}
                style={styles.input}
              />
            </View>
            <Pressable
              onPress={onCut}
              accessibilityRole="button"
              accessibilityLabel="Cut the label"
              style={({ pressed }) => [styles.cut, { transform: [{ scale: pressed ? 0.95 : 1 }] }]}
            >
              <SymbolView name="scissors" size={20} weight="medium" tintColor="#F7F1E8" fallback={<Text style={styles.cutText}>Cut</Text>} />
            </Pressable>
          </View>

          <View style={styles.tapeRow}>
            <Text style={styles.small}>Tape</Text>
            <View style={styles.chips}>
              <Animated.View pointerEvents="none" style={[styles.ring, ring]} />
              {TAPES.map((tp, i) => (
                <Pressable
                  key={tp.name}
                  onPress={() => pickTape(i)}
                  hitSlop={6}
                  accessibilityRole="button"
                  accessibilityLabel={`${tp.name} tape`}
                  accessibilityState={{ selected: tape === i }}
                  style={({ pressed }) => [styles.chip, { backgroundColor: tp.hex, transform: [{ scale: pressed ? 0.94 : 1 }] }]}
                >
                  <View style={styles.chipShine} />
                </Pressable>
              ))}
            </View>
          </View>

          <Pressable style={({ pressed }) => [styles.cta, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button">
            <Text style={styles.ctaText}>Save to pantry</Text>
          </Pressable>
        </View>
      </Animated.View>
    </View>
  );
}
