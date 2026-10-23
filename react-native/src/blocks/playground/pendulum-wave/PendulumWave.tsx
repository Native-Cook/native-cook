import { Canvas, matchFont, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawScene, makeView, project } from './PendulumWaveDrawing';
import {
  blendCamera,
  type Camera,
  combGrab,
  combMove,
  combRelease,
  createPendulums,
  CYCLE,
  EVENT_GRAB,
  EVENT_RELEASE,
  LOOP,
  MAX_FRAME,
  MORPH,
  pendulumScript,
  type PendulumSim,
  pendulumStep,
  VIEW_NAMES,
  VIEW_THREE_QUARTER,
  viewCamera,
} from './PendulumWaveSim';

type Props = {
  /** Pulls the bobs aside with the comb, lets go, tours the views through one cycle, catches them. */
  autoPlay?: boolean;
};

// The camera's focal length, as a share of the smaller of the width and half the height, and
// where the scene's middle sits on the screen (a share of the height).
const FOCAL = 1.75;
const CENTRE_AT = 0.5;
// The beam fades for the view from above between these pitches (degrees).
const TOP_FROM = 40;
const TOP_TO = 72;
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 4;
const SIDE = 24;
const MONO = process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace';

const INK = '#EDE3D2';
const INK_SOFT = 'rgba(237,227,210,0.5)';
const INK_FAINT = 'rgba(237,227,210,0.28)';

function smooth01(p: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, p));
  return t * t * (3 - 2 * t);
}

export function PendulumWave({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [viewName, setViewName] = useState(VIEW_NAMES[VIEW_THREE_QUARTER]);
  // Bold, as the SwiftUI (Menlo-Bold) and Compose (monospace bold) readouts are.
  const font = useMemo(() => matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '700' }), []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const focal = Math.min(width, height * 0.5) * FOCAL;
  const centreY = height * CENTRE_AT;
  const readoutY = insets.top + 70;

  const sim = useSharedValue<PendulumSim | null>(null);
  const tick = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The camera: where the last morph started, the view it goes to, how far along (1 = there),
  // and the clock that sways the 3/4 view.
  const camFrom = useSharedValue<Camera>(viewCamera(VIEW_THREE_QUARTER, 0));
  const viewTo = useSharedValue(VIEW_THREE_QUARTER);
  const morph = useSharedValue(1);
  const orbit = useSharedValue(0);
  // Screen points per world unit along the swing, for turning a drag into the comb's travel.
  const axis = useSharedValue([1, 0]);
  const dragFrom = useSharedValue(0);
  const dragging = useSharedValue(false);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    orbit.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping, orbit]);

  const feel = (events: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (events & EVENT_RELEASE) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (events & EVENT_GRAB) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const viewChanged = (index: number, byUser: boolean) => {
    setViewName(VIEW_NAMES[index]);
    if (byUser && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  /** The camera right now. */
  const camera = () => {
    'worklet';
    const target = viewCamera(viewTo.get(), orbit.get());
    return morph.get() < 1 ? blendCamera(camFrom.get(), target, morph.get()) : target;
  };

  const setView = (index: number, byUser: boolean) => {
    'worklet';
    if (index === viewTo.get()) return;
    camFrom.set(camera());
    viewTo.set(index);
    morph.set(reducedMotion ? 1 : 0);
    scheduleOnRN(viewChanged, index, byUser);
  };

  useFrameCallback((frame) => {
    // The script, the pendulums and the camera share one clock, capped so a slow frame cannot
    // skip ahead.
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_FRAME);
    let s = sim.get();
    if (!s) {
      s = createPendulums();
      sim.set(s);
    }
    // A comb in the user's hand keeps the script away.
    if (dragging.get()) hold.set(USER_HOLD);
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        clock.set(0);
        orbit.set(0);
      }
    } else if (looping.get()) {
      const before = clock.get();
      const now = (before + dt) % LOOP;
      clock.set(now);
      setView(pendulumScript(s, before, now), false);
    }
    if (!reducedMotion) orbit.set((orbit.get() + dt) % LOOP);
    if (morph.get() < 1) morph.set(Math.min(1, morph.get() + dt / MORPH));
    pendulumStep(s, dt);
    if (s.events !== 0) {
      if (s.eventsByUser) scheduleOnRN(feel, s.events);
      s.events = 0;
      s.eventsByUser = false;
    }
    if (width > 0) {
      const v = makeView(camera(), width, height, focal, centreY);
      const a = project(v, 0, 6, 0);
      const b = project(v, 1, 6, 0);
      axis.set([b[0] - a[0], b[1] - a[1]]);
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  // Drag sideways: the comb takes the bobs and follows the finger along the swing.
  // One finger works the comb: a second would move the pan's centroid and jerk the bobs.
  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .maxPointers(1)
    .onStart(() => {
      touched();
      const s = sim.get();
      if (!s) return;
      // Pick the comb up where the bobs are on average, so a moving row is caught, not yanked.
      let mean = 0;
      for (let k = 0; k < s.x.length; k += 1) mean += s.x[k] / s.x.length;
      dragFrom.set(mean);
      dragging.set(true);
      combGrab(s, mean, true);
    })
    .onUpdate((event) => {
      const s = sim.get();
      if (!s) return;
      hold.set(USER_HOLD);
      const a = axis.get();
      const per = a[0] * a[0] + a[1] * a[1];
      const along = per > 0 ? (event.translationX * a[0] + event.translationY * a[1]) / per : 0;
      combMove(s, dragFrom.get() + along);
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s && dragging.get()) combRelease(s, true, true);
      dragging.set(false);
    });

  // Tap: the next view.
  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((_e, success) => {
      if (!success) return;
      touched();
      setView((viewTo.get() + 1) % VIEW_NAMES.length, true);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    const s = sim.get();
    if (s && width > 0) {
      const cam = camera();
      const v = makeView(cam, width, height, focal, centreY);
      drawScene(canvas, v, s, width, height, smooth01((cam.pitch - TOP_FROM) / (TOP_TO - TOP_FROM)));
      // The cycle readout: a hairline that fills over the cycle, and the seconds since release.
      const running = s.sinceRelease >= 0;
      const t = running ? s.sinceRelease % CYCLE : 0;
      const track = Skia.Paint();
      track.setColor(Skia.Color('rgba(237,227,210,0.14)'));
      canvas.drawRect(Skia.XYWHRect(SIDE, readoutY, width - SIDE * 2, 1), track);
      const fill = Skia.Paint();
      fill.setColor(Skia.Color('rgba(255,214,150,0.85)'));
      canvas.drawRect(Skia.XYWHRect(SIDE, readoutY - 0.5, ((width - SIDE * 2) * t) / CYCLE, 2), fill);
      const label = running ? `${t.toFixed(1)} s / ${CYCLE} s` : `— / ${CYCLE} s`;
      const ink = Skia.Paint();
      ink.setColor(Skia.Color(INK_SOFT));
      canvas.drawText(label, width - SIDE - font.getTextWidth(label), readoutY - 10, ink, font);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel={`Pendulum wave, ${viewName} view. Drag sideways to pull the pendulums aside and release them; tap to change the view.`}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <Text style={styles.kicker}>PENDULUM WAVE</Text>
        <Text style={styles.title}>Fifteen bobs, one {CYCLE} s cycle</Text>
      </View>
      <View pointerEvents="none" style={[styles.footer, { bottom: insets.bottom + 22 }]}>
        <View style={styles.views}>
          {VIEW_NAMES.map((name) => (
            <Text key={name} style={[styles.view, { color: name === viewName ? INK : INK_FAINT }]}>
              {name}
            </Text>
          ))}
        </View>
        <Text style={styles.hint}>Drag sideways to release · Tap to change view</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#0D0E10' },
  header: { position: 'absolute', left: SIDE, right: SIDE, gap: 5 },
  kicker: { color: INK, fontSize: 11, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  footer: { position: 'absolute', left: SIDE, right: SIDE, alignItems: 'center', gap: 10 },
  views: { flexDirection: 'row', gap: 22 },
  view: { fontSize: 12, fontWeight: '700', letterSpacing: 1.6, textTransform: 'uppercase' },
  hint: { color: INK_SOFT, fontSize: 12, fontWeight: '500', letterSpacing: 0.3 },
});
