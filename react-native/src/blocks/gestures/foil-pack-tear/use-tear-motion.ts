/** One UI-thread engine publishes only pose values and discrete RN notifications. */
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { REST_POSE, stageFit, type TearValues } from './tear-contract';
import { actEngine, initialEngine, stepEngine, type TearEngine } from './tear-demo';
import { playTearHaptic } from './tear-haptics';
import type { TearInput, TearPhase } from './tear-sim';

type Options = { width: number; height: number; ready: boolean; autoPlay: boolean; reduceMotion: boolean };

export function useTearMotion({ width, height, ready, autoPlay, reduceMotion }: Options) {
  const engine = useSharedValue(initialEngine(reduceMotion));
  const [phase, setPhase] = useState<TearPhase>('sealed');
  const reportPhase = useCallback((next: TearPhase) => setPhase(next), []);
  const lightX = useSharedValue(REST_POSE.lightX);
  const lightY = useSharedValue(REST_POSE.lightY);
  const swayX = useSharedValue(REST_POSE.swayX);
  const swayY = useSharedValue(REST_POSE.swayY);
  const calm = useSharedValue(REST_POSE.calm);
  const packX = useSharedValue(REST_POSE.packX);
  const packY = useSharedValue(REST_POSE.packY);
  const packRot = useSharedValue(REST_POSE.packRot);
  const packScale = useSharedValue(REST_POSE.packScale);
  const packOpacity = useSharedValue(REST_POSE.packOpacity);
  const tearFront = useSharedValue(REST_POSE.tearFront);
  const tearLine = useSharedValue(REST_POSE.tearLine);
  const stripFree = useSharedValue(REST_POSE.stripFree);
  const stripX = useSharedValue(REST_POSE.stripX);
  const stripY = useSharedValue(REST_POSE.stripY);
  const stripRot = useSharedValue(REST_POSE.stripRot);
  const stripOpacity = useSharedValue(REST_POSE.stripOpacity);
  const leak = useSharedValue(REST_POSE.leak);
  const burst = useSharedValue(REST_POSE.burst);
  const mouth = useSharedValue(REST_POSE.mouth);
  const cardX = useSharedValue(REST_POSE.cardX);
  const cardY = useSharedValue(REST_POSE.cardY);
  const cardScale = useSharedValue(REST_POSE.cardScale);
  const cardFlip = useSharedValue(REST_POSE.cardFlip);
  const cardTiltX = useSharedValue(REST_POSE.cardTiltX);
  const cardTiltY = useSharedValue(REST_POSE.cardTiltY);
  const cardOpacity = useSharedValue(0);
  const sheen = useSharedValue(REST_POSE.sheen);
  const frameHeat = useSharedValue(REST_POSE.frameHeat);
  const frameRing = useSharedValue(REST_POSE.frameRing);
  const tabGlow = useSharedValue(REST_POSE.tabGlow);
  const sparkleAge = useSharedValue(REST_POSE.sparkleAge);
  const dust = useSharedValue(REST_POSE.dust);
  const values = useMemo<TearValues>(() => ({
    lightX, lightY, swayX, swayY, calm, packX, packY, packRot, packScale, packOpacity, tearFront, tearLine, stripFree, stripX, stripY, stripRot, stripOpacity, leak, burst, mouth, cardX, cardY, cardScale, cardFlip, cardTiltX, cardTiltY, cardOpacity, sheen, frameHeat, frameRing, tabGlow, sparkleAge, dust
  }), [lightX, lightY, swayX, swayY, calm, packX, packY, packRot, packScale, packOpacity, tearFront, tearLine, stripFree, stripX, stripY, stripRot, stripOpacity, leak, burst, mouth, cardX, cardY, cardScale, cardFlip, cardTiltX, cardTiltY, cardOpacity, sheen, frameHeat, frameRing, tabGlow, sparkleAge, dust]);

  const publish = useCallback((next: TearEngine) => {
    'worklet';
    const before = engine.get().sim.phase;
    engine.set(next);
    const p = next.sim.pose;
    lightX.set(p.lightX);
    lightY.set(p.lightY);
    swayX.set(p.swayX);
    swayY.set(p.swayY);
    calm.set(p.calm);
    packX.set(p.packX);
    packY.set(p.packY);
    packRot.set(p.packRot);
    packScale.set(p.packScale);
    packOpacity.set(p.packOpacity);
    tearFront.set(p.tearFront);
    tearLine.set(p.tearLine);
    stripFree.set(p.stripFree);
    stripX.set(p.stripX);
    stripY.set(p.stripY);
    stripRot.set(p.stripRot);
    stripOpacity.set(p.stripOpacity);
    leak.set(p.leak);
    burst.set(p.burst);
    mouth.set(p.mouth);
    cardX.set(p.cardX);
    cardY.set(p.cardY);
    cardScale.set(p.cardScale);
    cardFlip.set(p.cardFlip);
    cardTiltX.set(p.cardTiltX);
    cardTiltY.set(p.cardTiltY);
    cardOpacity.set(p.cardOpacity);
    sheen.set(p.sheen);
    frameHeat.set(p.frameHeat);
    frameRing.set(p.frameRing);
    tabGlow.set(p.tabGlow);
    sparkleAge.set(p.sparkleAge);
    dust.set(p.dust);
    if (next.sim.phase !== before) scheduleOnRN(reportPhase, next.sim.phase);
    for (let i = 0; i < next.sim.events.length; i++) scheduleOnRN(playTearHaptic, next.sim.events[i]);
  }, [engine, reportPhase, lightX, lightY, swayX, swayY, calm, packX, packY, packRot, packScale, packOpacity, tearFront, tearLine, stripFree, stripX, stripY, stripRot, stripOpacity, leak, burst, mouth, cardX, cardY, cardScale, cardFlip, cardTiltX, cardTiltY, cardOpacity, sheen, frameHeat, frameRing, tabGlow, sparkleAge, dust]);

  const act = useCallback((input: TearInput) => {
    'worklet';
    if (!ready) return;
    publish(actEngine(engine.get(), input));
  }, [engine, publish, ready]);

  useFrameCallback((frame) => {
    'worklet';
    if (!ready || frame.timeSincePreviousFrame === null) return;
    publish(stepEngine(engine.get(), frame.timeSincePreviousFrame / 1000, autoPlay));
  });

  const fit = stageFit(Math.max(1, width), Math.max(1, height));
  const k = fit.k;
  const originX = fit.x;
  const originY = fit.y;
  // Only shared values, scalars and standalone worklets are captured, never the gesture object.
  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .enabled(ready)
    .onBegin((event) => {
      act({ type: 'down', x: (event.x - originX) / k, y: (event.y - originY) / k });
    })
    .onUpdate((event) => {
      act({ type: 'move', x: (event.x - originX) / k, y: (event.y - originY) / k,
        vx: event.velocityX / k, vy: event.velocityY / k });
    })
    .onFinalize((event, success) => {
      act({ type: 'up', vx: event.velocityX / k, vy: event.velocityY / k, cancelled: !success });
    }), [act, k, originX, originY, ready]);

  const activate = useCallback(() => {
    scheduleOnUI(act, { type: 'activate' });
  }, [act]);

  return { values, gesture, phase, activate };
}
