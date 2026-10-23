import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { Easing, ReduceMotion, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { CARD, CURVES, heightAt, PEAK_NODE, PERIODS } from './peak-contract';
import { stepDemo } from './peak-demo';
import { adjust, contact, createState, fingerDown, fingerMove, fingerUp, press, request, toFrame } from './peak-sim';
import type { Action } from './peak-sim';

const PRESENT_EASE = Easing.bezier(0.23, 1, 0.32, 1);
const NODE_LABELS = ['point 1', 'valley', 'peak', 'point 4'];

// Ignore unavailable haptic hardware without creating an unhandled promise rejection.
function ignoreHapticFailure(): void {}

// Deliver discrete physical feedback on RN; the simulation never invokes platform APIs.
function deliverHaptic(kind: number): void {
  const result = kind === 1 ? Haptics.selectionAsync() : Haptics.impactAsync(
    kind === 0 ? Haptics.ImpactFeedbackStyle.Light : kind === 2 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Heavy,
  );
  void result.catch(ignoreHapticFailure);
}

// Bridge the pure simulation to the frame clock, transparent gestures and discrete accessibility state.
export function usePeakMotion(autoPlay: boolean, reduced: boolean, ready: boolean, k: number) {
  const [initial] = useState(() => createState(autoPlay, reduced));
  const state = useSharedValue(initial);
  const frame = useSharedValue({ ...toFrame(initial), presented: 0 });
  const presented = useSharedValue(0);
  const started = useSharedValue(false);
  const reported = useSharedValue([-1, 0, 0, 0, 0, 0]);
  const [a11y, setA11y] = useState({ period: 0, text: '300 percent, peak, Monthly', value: 300, busy: !autoPlay && !reduced });

  // Synchronize spoken values only at a state transition or completed interaction.
  const report = useCallback((period: number, node: number, value: number, busy: boolean, announce: boolean) => {
    const rounded = Math.round(value);
    const text = `${rounded} percent, ${node < 0 ? 'between points' : NODE_LABELS[node]}, ${PERIODS[period].label}`;
    setA11y({ period, text, value: rounded, busy });
    if (announce) AccessibilityInfo.announceForAccessibility(text);
  }, []);

  // Start scene time only once the explicitly enabled presentation fade has completed.
  const finishPresentation = useCallback((finished?: boolean) => {
    'worklet';
    if (finished) started.set(true);
  }, [started]);

  useEffect(() => {
    if (ready) presented.set(withTiming(1, { duration: 300, easing: PRESENT_EASE, reduceMotion: ReduceMotion.Never }, finishPresentation));
  }, [ready, presented, finishPresentation]);

  useFrameCallback((tick) => {
    'worklet';
    const s = state.get();
    if (started.get()) stepDemo(s, Math.min(50, tick.timeSincePreviousFrame ?? 0) / 1000);
    const snapshot = toFrame(s);
    snapshot.presented *= presented.get();
    frame.set(snapshot);
    const previous = reported.get();
    if (s.light !== previous[1]) scheduleOnRN(deliverHaptic, 0);
    if (s.selection !== previous[2]) scheduleOnRN(deliverHaptic, 1);
    if (s.medium !== previous[3]) scheduleOnRN(deliverHaptic, 2);
    if (s.heavy !== previous[4]) scheduleOnRN(deliverHaptic, 3);
    if (s.revision !== previous[0]) {
      const u = s.settling ? s.nodes[s.node] : s.orbU;
      const peak = Math.max(0.001, heightAt(s.f.ys, s.nodes[PEAK_NODE]));
      const value = s.mode === 'morph'
        ? PERIODS[s.f.period].value * Math.min(1, heightAt(CURVES[s.f.period], PERIODS[s.f.period].nodes[s.node]) / heightAt(CURVES[s.f.period], PERIODS[s.f.period].nodes[PEAK_NODE]))
        : s.mode === 'rest' ? PERIODS[s.f.period].value * Math.min(1, heightAt(s.f.ys, u) / peak) : s.f.value;
      const node = Math.abs(u - s.nodes[s.node]) < 0.002 ? s.node : -1;
      const busy = s.mode === 'refresh' || s.mode === 'opening' || (s.mode === 'fade' && s.fadePeriod === s.from.period);
      scheduleOnRN(report, s.f.period, node, value, busy, s.announcements !== previous[5]);
    }
    if (s.revision !== previous[0] || s.light !== previous[1] || s.selection !== previous[2] || s.medium !== previous[3] || s.heavy !== previous[4]) {
      reported.set([s.revision, s.light, s.selection, s.medium, s.heavy, s.announcements]);
    }
    state.set(s);
  });

  // Register touches outside the slab too, without coupling the worklet to a gesture object.
  const markContact = useCallback((down: boolean) => {
    'worklet';
    const s = state.get();
    contact(s, down);
    state.set(s);
  }, [state]);

  // Compress a control through the same action used by the demo script.
  const controlPress = useCallback((action: Action, down: boolean) => {
    'worklet';
    const s = state.get();
    press(s, action, down, true);
    state.set(s);
  }, [state]);

  // Commit only successful taps; cancelled touches still release their press answer.
  const activate = useCallback((action: Action) => {
    'worklet';
    const s = state.get();
    press(s, action, false, true);
    request(s, action, true);
    state.set(s);
  }, [state]);

  // Translate local gesture positions into the chart's design space on the UI thread.
  const beginDrag = useCallback((x: number, y: number) => {
    'worklet';
    const s = state.get();
    fingerDown(s, CARD.x + x / k, CARD.y + y / k, true);
    state.set(s);
  }, [state, k]);

  // Keep pointer position and velocity in the same coordinate system as the simulation.
  const moveDrag = useCallback((x: number, y: number, velocityX: number) => {
    'worklet';
    const s = state.get();
    fingerMove(s, CARD.x + x / k, CARD.y + y / k, velocityX / k);
    state.set(s);
  }, [state, k]);

  // Hand the release velocity to the ordinary node spring, including cancelled pans.
  const endDrag = useCallback((velocityX: number) => {
    'worklet';
    const s = state.get();
    fingerUp(s, velocityX / k, true);
    state.set(s);
  }, [state, k]);

  const cardGesture = useMemo(() => Gesture.Pan()
    .enabled(ready)
    .minDistance(0)
    .onBegin((e) => { beginDrag(e.x, e.y); })
    .onUpdate((e) => { moveDrag(e.x, e.y, e.velocityX); })
    .onFinalize((e) => { endDrag(e.velocityX); }), [ready, beginDrag, moveDrag, endDrag]);

  const pillGesture = useMemo(() => Gesture.Tap()
    .enabled(ready)
    .onBegin(() => { controlPress('period', true); })
    .onEnd((_e, success) => { if (success) activate('period'); })
    .onFinalize(() => { controlPress('period', false); }), [ready, controlPress, activate]);

  const refreshGesture = useMemo(() => Gesture.Tap()
    .enabled(ready)
    .onBegin(() => { controlPress('refresh', true); })
    .onEnd((_e, success) => { if (success) activate('refresh'); })
    .onFinalize(() => { controlPress('refresh', false); }), [ready, controlPress, activate]);

  const pageGesture = useMemo(() => Gesture.Tap()
    .enabled(ready)
    .maxDuration(3600000)
    .maxDistance(100000)
    .simultaneousWithExternalGesture(cardGesture, pillGesture, refreshGesture)
    .onBegin(() => { markContact(true); })
    .onFinalize(() => { markContact(false); }), [ready, cardGesture, pillGesture, refreshGesture, markContact]);

  // Apply an assistive-technology adjustment on UI, sharing the finger release spring.
  const adjustOnUI = useCallback((direction: number) => {
    'worklet';
    const s = state.get();
    adjust(s, direction);
    state.set(s);
  }, [state]);

  // Expose accessibility actions without reading a shared value on the RN thread.
  const adjustAccessible = useCallback((direction: number) => {
    scheduleOnUI(adjustOnUI, direction);
  }, [adjustOnUI]);

  // Route a screen-reader button activation through the ordinary queued action path.
  const activateAccessible = useCallback((action: Action) => {
    scheduleOnUI(activate, action);
  }, [activate]);

  return { frame, a11y, cardGesture, pillGesture, refreshGesture, pageGesture, adjustAccessible, activateAccessible };
}
