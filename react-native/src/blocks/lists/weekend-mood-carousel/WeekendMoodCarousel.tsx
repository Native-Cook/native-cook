import { Canvas, Picture } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolateColor,
  ReduceMotion,
  type FrameInfo,
  type SharedValue,
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
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CHARACTERS, CHIP, CHIPS } from './moods';
import { drawPanel, drawRuler, makeSilhouettes } from './MoodScene';
import {
  FILL_IN_MS,
  FILL_OUT_MS,
  REDUCED_FADE_MS,
  RESUME_AFTER_MS,
  scrollEasing,
  SNAP_SPRING,
  START,
  USER_SCROLL_MS,
} from './motion';
import { sampleScript } from './script';
import { CHARACTER, CHIPS_ROW, COLORS, FONTS, PANEL, RULER, SCREEN_W, TITLE, TOP_BUTTONS } from './theme';
import { useResume } from './use-resume';

/** The pin's chip for each character (index = character). */
const CHIP_OF_CHAR = [CHIP.sleepy, CHIP.foodie, CHIP.recovery, CHIP.binge, CHIP.shopping, CHIP.scroll, CHIP.social];
const PIN_H = 874;

type Props = {
  autoPlay?: boolean;
  /** Dev only: hold the autoplay at this time (ms) for frame-by-frame comparison with the pin. */
  freezeAt?: number;
  onBack?: () => void;
  onSave?: (selection: WeekendMoodSelection) => void;
};

export type WeekendMoodSelection = { index: number; label: string; characterIndex: number };

export function WeekendMoodCarousel({ autoPlay = false, freezeAt, onBack, onSave }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen;
  // Times New Roman ships with iOS; elsewhere its metric twin, Liberation Serif, is bundled.
  const [fontsLoaded] = useFonts({
    [FONTS.titleBundled]: require('./assets/fonts/LiberationSerif-Bold.ttf'),
    [FONTS.chip]: require('./assets/fonts/SourceSans3-Medium.ttf'),
  });

  // The pin is laid out for 402 x 874; the panel absorbs any extra height, the rest keeps its distance to the bottom.
  const extra = height - PIN_H;
  const panelH = PANEL.height + extra;
  const fromBottom = (y: number) => y + extra;

  const shapes = useMemo(() => makeSilhouettes(), []);

  // Scene state. Autoplay writes it every frame from the script; touches animate it.
  const chipX = useSharedValue(0);
  const charPos = useSharedValue<number>(START.char);
  const fill = useSharedValue(1);
  const ink = useSharedValue(1);
  const selChip = useSharedValue<number>(START.chip);
  const [selectedIndex, setSelectedIndex] = useState<number>(START.chip);
  const selChar = useSharedValue<number>(START.char);
  const prevChar = useSharedValue(-1);
  const age = useSharedValue(1000);
  const phase = useSharedValue(0);
  const veil = useSharedValue(0);
  const centers = useSharedValue<number[]>([]);
  const ready = useSharedValue(false);
  const mode = useSharedValue(1); // 0 autoplay, 1 touch
  const loopStart = useSharedValue(-1);
  const dragStart = useSharedValue(0);
  const autoplayEligible = useSharedValue(scripted);
  const returning = useSharedValue(false);

  const applyScript = useCallback((time: number, reduceSample: boolean) => {
    'worklet';
    const frame = sampleScript(time, centers.get(), reduceSample, REDUCED_FADE_MS);
    chipX.set(frame.chipX);
    charPos.set(frame.charPos);
    fill.set(frame.fill);
    ink.set(frame.ink);
    if (frame.selChar !== selChar.get()) prevChar.set(selChar.get());
    selChip.set(frame.selChip);
    selChar.set(frame.selChar);
    age.set(frame.age);
    veil.set(frame.veil);
    phase.set(time);
  }, [age, centers, charPos, chipX, fill, ink, phase, prevChar, selChar, selChip, veil]);
  const frameStep = useCallback((info: FrameInfo) => {
    'worklet';
    const now = info.timestamp;
    const dt = info.timeSincePreviousFrame ?? 16;
    if (!ready.get()) return;
    if (mode.get() === 0) {
      if (!autoplayEligible.get()) {
        mode.set(1);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      applyScript(now - loopStart.get(), false);
    } else {
      age.set(age.get() + dt);
      phase.set(phase.get() + dt);
    }
  }, [age, applyScript, autoplayEligible, loopStart, mode, phase, ready]);
  const frameDriver = useFrameCallback(frameStep, false);
  useEffect(() => {
    const wasAuto = mode.get() === 0;
    autoplayEligible.set(scripted);
    frameDriver.setActive(false);
    mode.set(1);
    loopStart.set(-1);
    const shouldSnapManual = returning.get() || (reduced && !wasAuto);
    if (shouldSnapManual) {
      returning.set(false);
      for (const value of [chipX, charPos, fill, ink, veil]) cancelAnimation(value);
      const selectedChip = selChip.get();
      chipX.set(centers.get()[selectedChip] ?? chipX.get());
      charPos.set(selChar.get());
      fill.set(1);
      ink.set(1);
      veil.set(0);
      age.set(9999);
    }
    if (frozen) {
      scheduleOnUI(applyScript, freezeAt, reduced);
      return;
    }
    if (reduced) {
      if (wasAuto) scheduleOnUI(applyScript, 0, true);
      return;
    }
    if (scripted) {
      mode.set(0);
    } else if (wasAuto) {
      scheduleOnUI(applyScript, 0, false);
    }
    frameDriver.setActive(true);
    return () => frameDriver.setActive(false);
  }, [age, applyScript, autoplayEligible, centers, charPos, chipX, fill, frameDriver, freezeAt, frozen, ink, loopStart, mode, reduced, returning, scripted, selChar, selChip, veil]);

  // Chip centres, once every chip has been measured.
  const widths = useRef<number[]>(new Array(CHIPS.length).fill(0));
  const onChipLayout = useCallback(
    (i: number, e: LayoutChangeEvent) => {
      widths.current[i] = e.nativeEvent.layout.width;
      if (widths.current.every((w) => w > 0)) {
        let x = 0;
        const c = widths.current.map((w) => {
          const mid = x + w / 2;
          x += w + CHIPS_ROW.gap;
          return mid;
        });
        centers.set(c);
        if (!ready.get()) {
          chipX.set(c[START.chip] - START.dx);
          ready.set(true);
        }
        if (frozen) scheduleOnUI(applyScript, freezeAt, reduced);
      }
    },
    [applyScript, centers, chipX, freezeAt, frozen, ready, reduced],
  );

  const panelPicture = useDerivedValue(
    () =>
      drawPanel(
        {
          width,
          height: panelH,
          charPos: charPos.get(),
          selChar: selChar.get(),
          prevChar: prevChar.get(),
          fill: fill.get(),
          age: age.get(),
          phase: phase.get(),
          reduced,
        },
        shapes,
      ),
    [shapes, width, panelH, reduced],
  );
  const rulerPicture = useDerivedValue(
    () => drawRuler(chipX.get(), centers.get()[START.chip] ?? 0, charPos.get(), width),
    [width],
  );

  // ---- touch -------------------------------------------------------------------------------------------------
  const returnToRest = useCallback(() => {
    if (!autoplayEligible.get()) return;
    returning.set(true);
    const c = centers.get();
    {
      fill.set(withTiming(0, { duration: FILL_OUT_MS }));
      ink.set(0);
    }
    chipX.set(
      withTiming(c[START.chip] ?? 0, {
        duration: USER_SCROLL_MS,
        easing: scrollEasing,
      }),
    );
    charPos.set(
      withTiming(START.char, { duration: USER_SCROLL_MS, easing: scrollEasing }, (fin) => {
        if (!fin || !returning.get() || !autoplayEligible.get()) return;
        returning.set(false);
        prevChar.set(selChar.get());
        selChip.set(START.chip);
        selChar.set(START.char);
        loopStart.set(-1);
        mode.set(0);
      }),
    );
  }, [autoplayEligible, centers, charPos, chipX, fill, ink, loopStart, mode, prevChar, returning, selChar, selChip]);

  const { hold: touched, release: released } = useResume(scripted, RESUME_AFTER_MS, returnToRest);
  const haptic = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, []);
  useAnimatedReaction(() => selChip.get(), (value, previous) => {
    if (value !== previous) scheduleOnRN(setSelectedIndex, value);
  });

  /** Select a chip: scroll it to the centre, move the carousel to its character, then light it up. */
  const select = (chip: number, opts: { fromDrag?: boolean; velocity?: number } = {}) => {
    'worklet';
    const target = centers.get()[chip] ?? 0;
    const char = CHIPS[chip].char;
    if (selChar.get() !== char) prevChar.set(selChar.get());
    selChip.set(chip);
    selChar.set(char);
    if (reduced) {
      veil.set(
        withTiming(1, { duration: REDUCED_FADE_MS / 2, reduceMotion: ReduceMotion.Never }, () => {
          chipX.set(target);
          charPos.set(char);
          fill.set(1);
          ink.set(1);
          age.set(9999);
          veil.set(
            withTiming(0, {
              duration: REDUCED_FADE_MS / 2,
              reduceMotion: ReduceMotion.Never,
            }),
          );
        }),
      );
      scheduleOnRN(haptic);
      return;
    }
    if (opts.fromDrag) chipX.set(withSpring(target, { ...SNAP_SPRING, velocity: -(opts.velocity ?? 0) }));
    else chipX.set(withTiming(target, { duration: USER_SCROLL_MS, easing: scrollEasing }));
    charPos.set(withTiming(char, { duration: USER_SCROLL_MS, easing: scrollEasing }));
    const lightAt = opts.fromDrag ? 380 : USER_SCROLL_MS * 0.9;
    age.set(-lightAt);
    fill.set(withDelay(lightAt, withTiming(1, { duration: FILL_IN_MS })));
    ink.set(withDelay(lightAt, withTiming(1, { duration: FILL_IN_MS })));
    scheduleOnRN(haptic);
  };

  const grab = () => {
    'worklet';
    returning.set(false);
    mode.set(1);
    cancelAnimation(chipX);
    cancelAnimation(charPos);
    cancelAnimation(fill);
    veil.set(0);
    scheduleOnRN(touched);
  };

  const nearestChip = (x: number) => {
    'worklet';
    const c = centers.get();
    let best = 0;
    for (let i = 1; i < c.length; i++) if (Math.abs(c[i] - x) < Math.abs(c[best] - x)) best = i;
    return best;
  };

  const chipPan = Gesture.Pan()
    .maxPointers(1)
    .activeOffsetX([-8, 8])
    .onBegin(() => grab())
    .onStart(() => {
      dragStart.set(chipX.get());
      {
        fill.set(withTiming(0, { duration: FILL_OUT_MS }));
        ink.set(0);
      }
    })
    .onUpdate((e) => {
      const c = centers.get();
      const min = c[0] ?? 0;
      const max = c[c.length - 1] ?? 0;
      let x = dragStart.get() - e.translationX;
      if (x < min) x = min - (min - x) * 0.35;
      if (x > max) x = max + (x - max) * 0.35;
      chipX.set(x);
    })
    .onEnd((e) => {
      select(nearestChip(chipX.get() - e.velocityX * 0.18), {
        fromDrag: true,
        velocity: e.velocityX,
      });
    })
    .onFinalize(() => scheduleOnRN(released));

  const chipTap = Gesture.Tap()
    .maxDistance(10)
    .onBegin(() => grab())
    .onEnd((e) => {
      const x = chipX.get() + (e.absoluteX - CHIPS_ROW.centerX - (width - SCREEN_W) / 2);
      const chip = nearestChip(x);
      if (Math.abs((centers.get()[chip] ?? 0) - x) < 80 && chip !== selChip.get()) {
        {
          fill.set(withTiming(0, { duration: FILL_OUT_MS }));
          ink.set(0);
        }
        select(chip);
      }
    })
    .onFinalize(() => scheduleOnRN(released));

  const panelPan = Gesture.Pan()
    .maxPointers(1)
    .activeOffsetX([-8, 8])
    .onBegin(() => grab())
    .onStart(() => {
      dragStart.set(charPos.get());
      {
        fill.set(withTiming(0, { duration: FILL_OUT_MS }));
        ink.set(0);
      }
    })
    .onUpdate((e) => {
      const max = CHARACTERS.length - 1;
      let p = dragStart.get() - e.translationX / CHARACTER.spacing;
      if (p < 0) p *= 0.35;
      if (p > max) p = max + (p - max) * 0.35;
      charPos.set(p);
    })
    .onEnd((e) => {
      const max = CHARACTERS.length - 1;
      const projected = charPos.get() - (e.velocityX * 0.2) / CHARACTER.spacing;
      const char = Math.max(0, Math.min(max, Math.round(projected)));
      const chip = CHIP_OF_CHAR[char];
      if (reduced) {
        select(chip);
        return;
      }
      if (selChar.get() !== char) prevChar.set(selChar.get());
      selChip.set(chip);
      selChar.set(char);
      charPos.set(
        withSpring(char, {
          ...SNAP_SPRING,
          velocity: -e.velocityX / CHARACTER.spacing,
        }),
      );
      chipX.set(
        withTiming(centers.get()[chip] ?? 0, {
          duration: USER_SCROLL_MS,
          easing: scrollEasing,
        }),
      );
      age.set(-420);
      fill.set(withDelay(420, withTiming(1, { duration: FILL_IN_MS })));
      ink.set(withDelay(420, withTiming(1, { duration: FILL_IN_MS })));
      scheduleOnRN(haptic);
    })
    .onFinalize(() => scheduleOnRN(released));

  const activateChip = (chip: number) => {
    touched();
    scheduleOnUI(select, chip);
    released();
  };

  const stripStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: CHIPS_ROW.centerX + (width - SCREEN_W) / 2 - chipX.get() }],
    opacity: 1 - veil.get(),
  }));
  const veilStyle = useAnimatedStyle(() => ({ opacity: 1 - veil.get() }));

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={panelPan}>
        <View style={[styles.panel, { height: panelH }]}>
          <Animated.View style={[StyleSheet.absoluteFill, veilStyle]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={panelPicture} />
            </Canvas>
          </Animated.View>
          <Pressable style={styles.back} accessibilityRole="button" accessibilityLabel="Back"
            accessibilityState={{ disabled: !onBack }} disabled={!onBack} onPress={onBack}>
            <Svg width={TOP_BUTTONS.arrow} height={TOP_BUTTONS.arrow} viewBox="0 0 18 18">
              <Path
                d={TOP_BUTTONS.arrowPath}
                stroke={COLORS.buttonInk}
                strokeWidth={TOP_BUTTONS.arrowStroke}
                fill="none"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </Svg>
          </Pressable>
          <Pressable style={styles.save} accessibilityRole="button" accessibilityLabel="Save mood"
            accessibilityState={{ disabled: !onSave }} disabled={!onSave}
            onPress={() => onSave?.({ index: selectedIndex, label: CHIPS[selectedIndex].label, characterIndex: CHIPS[selectedIndex].char })}>
            <Text style={styles.saveText}>Save</Text>
          </Pressable>
        </View>
      </GestureDetector>

      <Text style={[styles.title, { top: fromBottom(TITLE.top) }]} accessibilityRole="header">
        {'Select your\nweekend mood'}
      </Text>

      <Canvas style={[styles.ruler, { top: fromBottom(RULER.top), width }]} pointerEvents="none">
        <Picture picture={rulerPicture} />
      </Canvas>

      <GestureDetector gesture={Gesture.Exclusive(chipPan, chipTap)}>
        <View style={[styles.chipsArea, { top: fromBottom(CHIPS_ROW.top) - 8 }]}>
          <Animated.View style={[styles.strip, stripStyle]}>
            {CHIPS.map((chip, i) => (
              <Chip
                key={chip.label}
                index={i}
                label={chip.label}
                selChip={selChip}
                fill={fill}
                ink={ink}
                onLayout={onChipLayout}
                onActivate={() => activateChip(i)}
              />
            ))}
          </Animated.View>
        </View>
      </GestureDetector>
    </View>
  );
}

function Chip({
  index,
  label,
  selChip,
  fill,
  ink,
  onLayout,
  onActivate,
}: {
  index: number;
  label: string;
  selChip: SharedValue<number>;
  fill: SharedValue<number>;
  ink: SharedValue<number>;
  onLayout: (i: number, e: LayoutChangeEvent) => void;
  onActivate: () => void;
}) {
  const colors = CHARACTERS[CHIPS[index].char].chip;
  const fillStyle = useAnimatedStyle(() => ({
    opacity: selChip.get() === index ? fill.get() : 0,
  }));
  const ringStyle = useAnimatedStyle(() => ({
    opacity: selChip.get() === index ? 1 - fill.get() : 1,
  }));
  const textStyle = useAnimatedStyle(() => ({
    color: interpolateColor(selChip.get() === index ? ink.get() : 0, [0, 1], [COLORS.chipText, COLORS.chipTextSelected]),
  }));
  const [selected, setSelected] = useState(false);
  useAnimatedReaction(
    () => selChip.get() === index && fill.get() > 0.5,
    (on, was) => {
      if (on !== was) scheduleOnRN(setSelected, on);
    },
  );
  return (
    <View
      style={styles.chip}
      onLayout={(e) => onLayout(index, e)}
      accessible
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected }}
      accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') onActivate(); }}
    >
      <Animated.View style={[StyleSheet.absoluteFill, styles.chipRing, ringStyle]} />
      <Animated.View style={[StyleSheet.absoluteFill, styles.chipFill, fillStyle]}>
        <LinearGradient colors={colors} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Animated.Text style={[styles.chipText, textStyle]} numberOfLines={1}>
        {label}
      </Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.ink, overflow: 'hidden' },
  panel: {
    width: '100%',
    borderBottomLeftRadius: PANEL.radius,
    borderBottomRightRadius: PANEL.radius,
    overflow: 'hidden',
  },
  back: {
    position: 'absolute',
    top: TOP_BUTTONS.top,
    left: TOP_BUTTONS.backLeft,
    width: TOP_BUTTONS.size,
    height: TOP_BUTTONS.size,
    borderRadius: TOP_BUTTONS.size / 2,
    borderWidth: TOP_BUTTONS.ring,
    borderColor: COLORS.buttonRing,
    alignItems: 'center',
    justifyContent: 'center',
  },
  save: {
    position: 'absolute',
    top: TOP_BUTTONS.top,
    right: TOP_BUTTONS.saveRight,
    width: TOP_BUTTONS.saveWidth,
    height: TOP_BUTTONS.size,
    borderRadius: TOP_BUTTONS.size / 2,
    borderWidth: TOP_BUTTONS.ring,
    borderColor: COLORS.buttonRing,
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveText: {
    fontFamily: FONTS.ui,
    fontWeight: TOP_BUTTONS.saveWeight,
    fontSize: TOP_BUTTONS.saveFont,
    color: COLORS.buttonInk,
  },
  title: {
    position: 'absolute',
    left: TITLE.shiftX * 2,
    right: 0,
    textAlign: 'center',
    color: COLORS.white,
    fontFamily: FONTS.title,
    fontSize: TITLE.fontSize,
    lineHeight: TITLE.lineHeight,
    letterSpacing: TITLE.letterSpacing,
  },
  ruler: { position: 'absolute', left: 0, height: RULER.height },
  chipsArea: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: CHIPS_ROW.height + 16,
    justifyContent: 'center',
  },
  strip: { flexDirection: 'row', alignSelf: 'flex-start', gap: CHIPS_ROW.gap },
  chip: {
    height: CHIPS_ROW.height,
    minWidth: CHIPS_ROW.minWidth,
    paddingHorizontal: CHIPS_ROW.padding,
    borderRadius: CHIPS_ROW.height / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipRing: {
    borderRadius: CHIPS_ROW.height / 2,
    borderWidth: CHIPS_ROW.border,
    borderColor: COLORS.chipRing,
  },
  chipFill: { borderRadius: CHIPS_ROW.height / 2, overflow: 'hidden' },
  chipText: {
    fontFamily: FONTS.chip,
    fontWeight: CHIPS_ROW.weight,
    fontSize: CHIPS_ROW.fontSize,
    letterSpacing: CHIPS_ROW.letterSpacing,
  },
});
