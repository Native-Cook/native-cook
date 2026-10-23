import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState, type ReactNode } from 'react';
import {
  type LayoutChangeEvent,
  Platform,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
  useWindowDimensions,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  cancelAnimation,
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

import { drawBox, drawCanister, drawSpinner } from './art';
import { ICONS, Icon } from './icons';
import {
  CAMERA_LIFT,
  LOOP,
  NO_EVENTS,
  RESET_HALF,
  ROW_LIFT,
  SMALL,
  STAGE_BOX,
  STAGE_CAMERA,
  STAGE_FILM,
  STAGE_PHOTO,
  USER_HOLD,
  clamp01,
  countdown,
  loopDrag,
  loopEvents,
  sceneAt,
  settledAt,
  smooth,
  type Events,
  type Scene,
} from './timeline';

export type FilmDetail = { label: string; value: string };

export type Film = {
  name: string;
  description: string;
  /** Seconds left before the filter expires, counted down from the moment it is shown. */
  expiresIn: number;
  details: FilmDetail[];
  /** The colour laid over a photo taken with it. */
  tint: string;
  tintOpacity: number;
};

type Props = {
  title?: string;
  date?: string;
  slideLabel?: string;
  expiresLabel?: string;
  loadLabel?: string;
  retakeLabel?: string;
  film?: Film;
  /** What the camera sees. */
  photo?: string;
  /** The ring of the slider, the knob and the shutter. */
  accent?: string;
  /** Plays the whole flow by itself every LOOP seconds, from the closed box back to it. */
  autoPlay?: boolean;
  onUnbox?: () => void;
  onLoad?: (film: Film) => void;
  onCapture?: () => void;
  onRetake?: () => void;
  onSave?: () => void;
  onShare?: () => void;
};

const FILM: Film = {
  name: 'Alien',
  description: 'Otherworldly filter with vibrant, alien-like colors.',
  expiresIn: 17 * 3600 + 21 * 60 + 58,
  details: [
    { label: 'ISO', value: 'Medium' },
    { label: 'Contrast', value: 'High' },
    { label: 'Saturation', value: 'Vibrant' },
  ],
  tint: '#0f5560',
  tintOpacity: 0.4,
};

const PHOTO = 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=600&h=600&q=80';

const BLACK = '#000000';
const TITLE = '#e6e6e6';
const MUTE = '#8a8a8a';
const DESCRIPTION = '#b0b0b0';
const ROW_LABEL = '#6e6e6e';
const ROW_VALUE = '#e8d44d';
const RULE = '#222222';
const BUTTON = '#1c1c1c';
const ROUND = '#141414';
const ACCENT = '#ffd60a';
const KNOB_TOP = '#ffe259';
const KNOB_BOTTOM = '#ffc800';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

// Layout, in points. The box and the filter's name sit on the middle of the screen; the slider,
// the spinner, Load film and the camera's controls share one row above the bottom edge.
const HEADER_TOP = 54;
const CANISTER_ABOVE = 150;
/** The small canister at the top of the camera: its centre below the safe area. */
const SMALL_TOP = 40;
const TEXT_X = 40;
const DESCRIPTION_TOP = 22;
const ROWS_TOP = 70;
const ROW_HEIGHT = 26;
const ACTION = 88;
const PILL_WIDTH = 216;
const PILL_HEIGHT = 52;
const PILL_STROKE = 3;
const KNOB = 44;
const KNOB_INSET = 4;
const TRAVEL = PILL_WIDTH - KNOB_INSET * 2 - KNOB;
/** Released past this share of the track, the knob opens the box; before it, it springs back. */
const UNBOX_THRESHOLD = 0.85;
const LOAD_WIDTH = 104;
const LOAD_HEIGHT = 38;
const ROUND_SIZE = 44;
const SIDE = 112;
const SHUTTER = 66;
const SHUTTER_RING = 3;
const SHUTTER_GAP = 3;
const RETAKE_WIDTH = 88;
const PHOTO_MAX = 300;
const PHOTO_MARGIN = 56;
const PHOTO_RADIUS = 6;
const BRACKET = 18;
const BRACKET_INSET = 10;
const ICON = 22;
const PRESSED = 0.92;
/** The knob springs back like SwiftUI's spring(response: 0.35, dampingFraction: 0.75). */
const SPRING_BACK = { mass: 1, stiffness: 322.3, damping: 26.93 };
const SNAP = 120;

function tap(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

/** One of the card's lines, coming in after the ones above it. */
function Reveal({ scene, index, style, children }: { scene: SharedValue<Scene>; index: number; style: StyleProp<ViewStyle>; children: ReactNode }) {
  const animated = useAnimatedStyle(() => {
    const s = scene.get();
    const shown = s.rows[index];
    return { opacity: shown * (1 - s.cardOut), transform: [{ translateY: ROW_LIFT * (1 - shown) }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[style, animated]}>
      {children}
    </Animated.View>
  );
}

function RoundButton({ label, onPress, children }: { label: string; onPress: () => void; children: ReactNode }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} hitSlop={6}>
      {({ pressed }) => (
        <Animated.View style={[styles.round, { transform: [{ scale: pressed ? PRESSED : 1 }], transitionProperty: 'transform', transitionDuration: 100 }]}>
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A daily filter, unboxed: a slider opens a film box drawn in perspective, the view dives into it,
 * and the filter's canister comes out turning, with its details. Load film flies the canister up
 * into a camera; the shutter takes a photo with the filter's colour on it.
 */
export function FilmBoxUnboxing({
  title = 'Unbox your filter for today',
  date = '5/7/26',
  slideLabel = 'Unbox filter',
  expiresLabel = 'Expires in',
  loadLabel = 'Load film',
  retakeLabel = 'Retake',
  film = FILM,
  photo = PHOTO,
  accent = ACCENT,
  autoPlay = false,
  onUnbox,
  onLoad,
  onCapture,
  onRetake,
  onSave,
  onShare,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [stage, setStage] = useState(STAGE_BOX);
  const [shown, setShown] = useState(0);
  const [timerOn, setTimerOn] = useState(false);
  const [flashOn, setFlashOn] = useState(false);

  const midY = height / 2;
  const actionY = height - Math.max(insets.bottom, 16) - ACTION;
  const smallY = insets.top + SMALL_TOP;
  const photoSize = Math.min(width - PHOTO_MARGIN * 2, PHOTO_MAX, actionY - SHUTTER / 2 - smallY - 72);
  const photoY = (smallY + 24 + actionY - SHUTTER / 2) / 2;

  // Seconds since the first frame: the clock every motion reads.
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  const events = useSharedValue<Events>(NO_EVENTS);
  // Where the knob is along its track, 0 to 1.
  const drag = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  // When the autoPlay loop started; -1 while touches have it.
  const origin = useSharedValue(autoPlay ? 0 : -1);
  const touch = useSharedValue({ down: false, panning: false, last: 0 });

  useEffect(() => {
    origin.set(autoPlay ? clock.get() : -1);
  }, [autoPlay, origin, clock]);

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    clock.set(now);
    const ev = events.get();
    const t = touch.get();
    let start = origin.get();
    // A while after the last touch, the screen goes back to rest and the loop starts again.
    if (autoPlay && start < 0 && !t.down && !t.panning && now >= Math.max(t.last, settledAt(ev)) + USER_HOLD) {
      const atRest = (ev.unbox < 0 || (ev.reset >= 0 && now >= ev.reset + RESET_HALF * 2)) && drag.get() === 0;
      if (atRest) start = now;
      else {
        events.set({ unbox: ev.unbox, load: ev.load, shot: ev.shot, retake: ev.retake, reset: now });
        start = now + RESET_HALF * 2;
      }
      origin.set(start);
    }
    if (start >= 0 && now >= start) {
      const n = Math.floor((now - start) / LOOP);
      const base = start + n * LOOP;
      const local = now - base;
      const want = loopEvents(local, base);
      const had = events.get();
      if (want.unbox !== had.unbox || want.load !== had.load || want.shot !== had.shot || want.retake !== had.retake || want.reset !== had.reset) {
        events.set(want);
      }
      drag.set(loopDrag(local, still));
    } else {
      const had = events.get();
      // The knob is back at the start by the darkest moment of the fade.
      if (had.reset >= 0 && now >= had.reset + RESET_HALF && drag.get() !== 0 && !t.panning) drag.set(0);
    }
  });

  const scene = useDerivedValue(() => sceneAt(clock.get(), events.get(), still));

  useAnimatedReaction(
    () => scene.get().stage,
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setStage, next);
    },
  );
  useAnimatedReaction(
    () => Math.floor(scene.get().shown),
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setShown, next);
    },
  );

  const picture = useDerivedValue(() => {
    const s = scene.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    if (s.boxAlpha > 0.001) {
      const layer = Skia.Paint();
      layer.setAlphaf(s.boxAlpha);
      canvas.saveLayer(layer);
      drawBox(canvas, paint, width / 2, midY + s.lift, width / 2, midY, {
        yaw: s.yaw,
        pitch: s.tilt,
        roll: s.roll,
        flap: s.flap,
        glow: s.glow,
        sweep: s.sweep,
        dive: s.dive,
      });
      canvas.restore();
    }
    if (s.spinner > 0.001) drawSpinner(canvas, paint, width / 2, actionY, s.spinnerTime, s.spinner);
    if (s.canister > 0.001) {
      const from = midY - CANISTER_ABOVE;
      const layer = Skia.Paint();
      layer.setAlphaf(Math.min(1, s.canister));
      canvas.saveLayer(layer);
      drawCanister(canvas, paint, width / 2, from + (smallY - from) * s.fly, s.canisterScale * (1 + (SMALL - 1) * s.fly), s.spin);
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  /** A touch takes the screen from the autoPlay loop; a knob it left midway springs back. */
  const takeOver = () => {
    'worklet';
    const t = touch.get();
    touch.set({ down: true, panning: t.panning, last: clock.get() });
    if (origin.get() >= 0) {
      origin.set(-1);
      if (events.get().unbox < 0 && !t.panning) drag.set(withSpring(0, SPRING_BACK));
    }
  };
  const letGo = () => {
    'worklet';
    touch.set({ down: false, panning: false, last: clock.get() });
  };

  const unboxed = () => {
    tap(Haptics.ImpactFeedbackStyle.Medium);
    onUnbox?.();
  };

  const pan = Gesture.Pan()
    .enabled(stage === STAGE_BOX)
    .onBegin(() => {
      touch.set({ down: true, panning: true, last: clock.get() });
      takeOver();
      cancelAnimation(drag);
      dragFrom.set(drag.get());
    })
    .onUpdate((e) => {
      drag.set(clamp01(dragFrom.get() + e.translationX / TRAVEL));
    })
    .onEnd(() => {
      if (drag.get() >= UNBOX_THRESHOLD && events.get().unbox < 0) {
        drag.set(withTiming(1, { duration: SNAP }));
        events.set({ unbox: clock.get(), load: -1, shot: -1, retake: -1, reset: -1 });
        scheduleOnRN(unboxed);
      }
    })
    .onFinalize(() => {
      if (events.get().unbox < 0) drag.set(withSpring(0, SPRING_BACK));
      letGo();
    });

  /** The same as dragging the knob all the way, for VoiceOver and TalkBack. */
  const unboxNow = () => {
    if (stage !== STAGE_BOX) return;
    scheduleOnUI(() => {
      'worklet';
      drag.set(withTiming(1, { duration: SNAP }));
      events.set({ unbox: clock.get(), load: -1, shot: -1, retake: -1, reset: -1 });
    });
    unboxed();
  };

  const load = () => {
    if (stage !== STAGE_FILM) return;
    tap(Haptics.ImpactFeedbackStyle.Light);
    const ev = events.get();
    events.set({ unbox: ev.unbox, load: clock.get(), shot: -1, retake: -1, reset: -1 });
    onLoad?.(film);
  };
  const shoot = () => {
    if (stage !== STAGE_CAMERA) return;
    tap(Haptics.ImpactFeedbackStyle.Medium);
    const ev = events.get();
    events.set({ unbox: ev.unbox, load: ev.load, shot: clock.get(), retake: ev.retake, reset: -1 });
    onCapture?.();
  };
  const retake = () => {
    if (stage !== STAGE_PHOTO) return;
    tap(Haptics.ImpactFeedbackStyle.Light);
    const ev = events.get();
    events.set({ unbox: ev.unbox, load: ev.load, shot: ev.shot, retake: clock.get(), reset: -1 });
    onRetake?.();
  };

  const headerStyle = useAnimatedStyle(() => ({ opacity: scene.get().header }));
  const pillStyle = useAnimatedStyle(() => ({ opacity: scene.get().pill }));
  const knobStyle = useAnimatedStyle(() => ({ transform: [{ translateX: drag.get() * TRAVEL }] }));
  // The label gives way as the knob passes over it.
  const slideLabelStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(drag.get(), 0.05, 0.45) }));
  const nameStyle = useAnimatedStyle(() => ({ opacity: scene.get().name * (1 - scene.get().cardOut) }));
  const loadStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return {
      opacity: s.rows[5] * (1 - s.cardOut),
      transform: [{ translateY: ROW_LIFT * (1 - s.rows[5]) }, { scale: s.loadScale }],
    };
  });
  const loadLabelStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.62 * scene.get().grey }));
  const viewfinderStyle = useAnimatedStyle(() => ({ opacity: scene.get().camera }));
  const photoStyle = useAnimatedStyle(() => ({ opacity: scene.get().photo, transform: [{ scale: scene.get().photoScale }] }));
  const cameraRowStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return { opacity: s.camera * s.cameraRow, transform: [{ translateY: CAMERA_LIFT * (1 - s.camera) + 12 * (1 - s.cameraRow) }] };
  });
  const shutterStyle = useAnimatedStyle(() => ({ transform: [{ scale: scene.get().shutter }] }));
  const photoRowStyle = useAnimatedStyle(() => ({
    opacity: scene.get().photoRow,
    transform: [{ translateY: 12 * (1 - scene.get().photoRow) }],
  }));
  const flashStyle = useAnimatedStyle(() => ({ opacity: scene.get().flash }));
  const overlayStyle = useAnimatedStyle(() => ({ opacity: scene.get().overlay }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const rows = [{ label: expiresLabel, value: countdown(film.expiresIn - shown) }, ...film.details];
  const actionTop = (size: number) => actionY - size / 2;

  return (
    <View
      style={styles.screen}
      onLayout={onLayout}
      onTouchStart={() => scheduleOnUI(takeOver)}
      onTouchEnd={() => scheduleOnUI(letGo)}
      onTouchCancel={() => scheduleOnUI(letGo)}
    >
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>

      <Animated.View pointerEvents="none" style={[styles.header, { top: insets.top + HEADER_TOP }, headerStyle]}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.date}>{date}</Text>
      </Animated.View>

      <Animated.View
        style={[styles.pill, { top: actionTop(PILL_HEIGHT), left: (width - PILL_WIDTH) / 2, borderColor: accent }, pillStyle]}
        pointerEvents={stage === STAGE_BOX ? 'auto' : 'none'}
        accessible
        accessibilityRole="button"
        accessibilityLabel={slideLabel}
        accessibilityElementsHidden={stage !== STAGE_BOX}
        importantForAccessibility={stage === STAGE_BOX ? 'yes' : 'no-hide-descendants'}
        onAccessibilityTap={unboxNow}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={unboxNow}
      >
        <GestureDetector gesture={pan}>
          <View style={styles.track}>
            <Animated.Text style={[styles.slideLabel, slideLabelStyle]} numberOfLines={1}>
              {slideLabel}
            </Animated.Text>
            <Animated.View style={[styles.knob, knobStyle]}>
              <LinearGradient colors={[KNOB_TOP, KNOB_BOTTOM]} style={styles.knobFill}>
                <Icon drawing={ICONS.arrow} size={ICON} color={BLACK} stroke={2} />
              </LinearGradient>
            </Animated.View>
          </View>
        </GestureDetector>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.name, { top: midY - 11 }, nameStyle]}>
        <Text style={styles.nameText} numberOfLines={1}>
          {film.name}
        </Text>
      </Animated.View>

      <Reveal scene={scene} index={0} style={[styles.textBlock, { top: midY + DESCRIPTION_TOP }]}>
        <Text style={styles.description} lineBreakStrategyIOS="standard">
          {film.description}
        </Text>
      </Reveal>
      {rows.map((row, i) => (
        <Reveal
          key={row.label}
          scene={scene}
          index={Math.min(i + 1, 4)}
          style={[styles.textBlock, styles.row, i > 0 && styles.rule, { top: midY + ROWS_TOP + i * ROW_HEIGHT }]}
        >
          <Text style={styles.rowLabel} numberOfLines={1}>
            {row.label}
          </Text>
          <Text style={styles.rowValue} numberOfLines={1}>
            {row.value}
          </Text>
        </Reveal>
      ))}

      <Animated.View
        style={[styles.loadSlot, { top: actionTop(LOAD_HEIGHT), left: (width - LOAD_WIDTH) / 2 }, loadStyle]}
        pointerEvents={stage === STAGE_FILM ? 'auto' : 'none'}
        accessibilityElementsHidden={stage !== STAGE_FILM}
        importantForAccessibility={stage === STAGE_FILM ? 'auto' : 'no-hide-descendants'}
      >
        <Pressable accessibilityRole="button" onPress={load}>
          {({ pressed }) => (
            <Animated.View style={[styles.load, { opacity: pressed ? 0.7 : 1, transitionProperty: 'opacity', transitionDuration: 100 }]}>
              <Animated.Text style={[styles.buttonLabel, loadLabelStyle]} numberOfLines={1}>
                {loadLabel}
              </Animated.Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>

      <View
        pointerEvents="none"
        style={[styles.photoFrame, { top: photoY - photoSize / 2, left: (width - photoSize) / 2, width: photoSize, height: photoSize }]}
      >
        <Animated.View style={[StyleSheet.absoluteFill, viewfinderStyle]}>
          <View style={styles.photoClip}>
            <Image source={{ uri: photo }} style={StyleSheet.absoluteFill} contentFit="cover" />
          </View>
          <View style={[styles.bracket, { left: BRACKET_INSET, top: BRACKET_INSET, borderLeftWidth: 2, borderTopWidth: 2 }]} />
          <View style={[styles.bracket, { right: BRACKET_INSET, top: BRACKET_INSET, borderRightWidth: 2, borderTopWidth: 2 }]} />
          <View style={[styles.bracket, { left: BRACKET_INSET, bottom: BRACKET_INSET, borderLeftWidth: 2, borderBottomWidth: 2 }]} />
          <View style={[styles.bracket, { right: BRACKET_INSET, bottom: BRACKET_INSET, borderRightWidth: 2, borderBottomWidth: 2 }]} />
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, photoStyle]}>
          <View style={styles.photoClip}>
            <Image source={{ uri: photo }} style={StyleSheet.absoluteFill} contentFit="cover" />
            <View style={[StyleSheet.absoluteFill, { backgroundColor: film.tint, opacity: film.tintOpacity }]} />
          </View>
        </Animated.View>
      </View>

      <Animated.View
        style={[styles.controls, { top: actionTop(SHUTTER) }, cameraRowStyle]}
        pointerEvents={stage === STAGE_CAMERA ? 'box-none' : 'none'}
        accessibilityElementsHidden={stage !== STAGE_CAMERA}
        importantForAccessibility={stage === STAGE_CAMERA ? 'auto' : 'no-hide-descendants'}
      >
        <View style={[styles.side, { left: width / 2 - SIDE - ROUND_SIZE / 2 }]}>
          <RoundButton
            label="Timer"
            onPress={() => {
              tap(Haptics.ImpactFeedbackStyle.Light);
              setTimerOn((on) => !on);
            }}
          >
            <Icon drawing={ICONS.timer} size={ICON} color={timerOn ? accent : '#ffffff'} stroke={1.6} />
          </RoundButton>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Take photo" onPress={shoot}>
          {({ pressed }) => (
            <Animated.View style={[styles.shutter, { borderColor: accent }, shutterStyle]}>
              <LinearGradient
                colors={[KNOB_TOP, KNOB_BOTTOM]}
                style={[styles.shutterFill, { opacity: pressed ? 0.7 : 1 }]}
              />
            </Animated.View>
          )}
        </Pressable>
        <View style={[styles.side, { left: width / 2 + SIDE - ROUND_SIZE / 2 }]}>
          <RoundButton
            label={flashOn ? 'Flash on' : 'Flash off'}
            onPress={() => {
              tap(Haptics.ImpactFeedbackStyle.Light);
              setFlashOn((on) => !on);
            }}
          >
            <Icon
              drawing={flashOn ? ICONS.flashOn : ICONS.flashOff}
              size={ICON}
              color={flashOn ? accent : '#ffffff'}
              fill={flashOn ? accent : 'none'}
              stroke={1.6}
            />
          </RoundButton>
        </View>
      </Animated.View>

      <Animated.View
        style={[styles.controls, { top: actionTop(SHUTTER) }, photoRowStyle]}
        pointerEvents={stage === STAGE_PHOTO ? 'box-none' : 'none'}
        accessibilityElementsHidden={stage !== STAGE_PHOTO}
        importantForAccessibility={stage === STAGE_PHOTO ? 'auto' : 'no-hide-descendants'}
      >
        <View style={[styles.side, { left: width / 2 - SIDE - ROUND_SIZE / 2 }]}>
          <RoundButton
            label="Save"
            onPress={() => {
              tap(Haptics.ImpactFeedbackStyle.Light);
              onSave?.();
            }}
          >
            <Icon drawing={ICONS.download} size={ICON} color="#ffffff" stroke={1.6} />
          </RoundButton>
        </View>
        <Pressable accessibilityRole="button" onPress={retake}>
          {({ pressed }) => (
            <Animated.View style={[styles.retake, { transform: [{ scale: pressed ? PRESSED : 1 }], transitionProperty: 'transform', transitionDuration: 100 }]}>
              <Text style={styles.buttonLabel} numberOfLines={1}>
                {retakeLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
        <View style={[styles.side, { left: width / 2 + SIDE - ROUND_SIZE / 2 }]}>
          <RoundButton
            label="Share"
            onPress={() => {
              tap(Haptics.ImpactFeedbackStyle.Light);
              onShare?.();
            }}
          >
            <Icon drawing={ICONS.share} size={ICON} color="#ffffff" stroke={1.6} />
          </RoundButton>
        </View>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.flash, flashStyle]} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.overlay, overlayStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BLACK,
  },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    alignItems: 'center',
  },
  title: {
    color: TITLE,
    fontFamily: MONO,
    fontSize: 12,
    lineHeight: 16,
    includeFontPadding: false,
  },
  date: {
    marginTop: 4,
    color: MUTE,
    fontFamily: MONO,
    fontSize: 11,
    lineHeight: 14,
    includeFontPadding: false,
  },
  pill: {
    position: 'absolute',
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    borderWidth: PILL_STROKE,
  },
  track: {
    flex: 1,
    justifyContent: 'center',
  },
  slideLabel: {
    position: 'absolute',
    left: KNOB_INSET + KNOB - PILL_STROKE,
    right: KNOB_INSET - PILL_STROKE,
    textAlign: 'center',
    color: '#ffffff',
    fontFamily: MONO,
    fontSize: 12,
    lineHeight: 16,
    includeFontPadding: false,
  },
  knob: {
    position: 'absolute',
    left: KNOB_INSET - PILL_STROKE,
    width: KNOB,
    height: KNOB,
  },
  knobFill: {
    flex: 1,
    borderRadius: KNOB / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: {
    position: 'absolute',
    left: 24,
    right: 24,
    alignItems: 'center',
  },
  nameText: {
    color: '#ffffff',
    fontFamily: MONO,
    fontSize: 17,
    lineHeight: 22,
    includeFontPadding: false,
  },
  textBlock: {
    position: 'absolute',
    left: TEXT_X,
    right: TEXT_X,
  },
  description: {
    color: DESCRIPTION,
    fontFamily: MONO,
    fontSize: 11,
    lineHeight: 16,
    includeFontPadding: false,
  },
  row: {
    height: ROW_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  rule: {
    borderTopWidth: 1,
    borderTopColor: RULE,
  },
  rowLabel: {
    color: ROW_LABEL,
    fontFamily: MONO,
    fontSize: 10,
    lineHeight: 14,
    includeFontPadding: false,
  },
  rowValue: {
    color: ROW_VALUE,
    fontFamily: MONO,
    fontSize: 10,
    lineHeight: 14,
    includeFontPadding: false,
    fontVariant: ['tabular-nums'],
  },
  loadSlot: {
    position: 'absolute',
    width: LOAD_WIDTH,
    height: LOAD_HEIGHT,
  },
  load: {
    width: LOAD_WIDTH,
    height: LOAD_HEIGHT,
    borderRadius: LOAD_HEIGHT / 2,
    backgroundColor: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontFamily: MONO,
    fontSize: 12,
    lineHeight: 16,
    includeFontPadding: false,
  },
  photoFrame: {
    position: 'absolute',
  },
  photoClip: {
    flex: 1,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
    backgroundColor: ROUND,
  },
  bracket: {
    position: 'absolute',
    width: BRACKET,
    height: BRACKET,
    borderColor: 'rgba(255,255,255,0.9)',
  },
  controls: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: SHUTTER,
    alignItems: 'center',
    justifyContent: 'center',
  },
  side: {
    position: 'absolute',
    top: (SHUTTER - ROUND_SIZE) / 2,
  },
  round: {
    width: ROUND_SIZE,
    height: ROUND_SIZE,
    borderRadius: ROUND_SIZE / 2,
    backgroundColor: ROUND,
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutter: {
    width: SHUTTER,
    height: SHUTTER,
    borderRadius: SHUTTER / 2,
    borderWidth: SHUTTER_RING,
    padding: SHUTTER_GAP,
    backgroundColor: BLACK,
  },
  shutterFill: {
    flex: 1,
    borderRadius: SHUTTER / 2,
  },
  retake: {
    width: RETAKE_WIDTH,
    height: ROUND_SIZE,
    borderRadius: ROUND_SIZE / 2,
    backgroundColor: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flash: {
    backgroundColor: '#ffffff',
  },
  overlay: {
    backgroundColor: BLACK,
  },
});
