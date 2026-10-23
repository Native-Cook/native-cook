import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, Keyboard, Platform, TextInput, View, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable } from 'react-native-gesture-handler';
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';

import { BeamCanvas } from './BeamCanvas';
import { BeamPanel } from './BeamPanel';
import { BeamRows } from './BeamRows';
import { F, FIELDS, G } from './beam-theme';
import { ctaLabel, emptyForm, formAction, type FormAction, type FormModel } from './beam-form';
import { beginDrag, drag, endDrag, goTo, type SimEvent, type BeamState } from './beam-sim';
import { touchDemo, type DemoEvent, type DemoState } from './beam-demo';
import { useBeamMotion } from './use-beam-motion';


function Rail({ state, transport, k, onTouch, dismiss, paid }: {
  state: SharedValue<BeamState>; transport: SharedValue<DemoState>; k: number;
  onTouch: (down: boolean) => void; dismiss: () => void; paid: boolean;
}) {
  const railGesture = useMemo(() => {
    const pan = Gesture.Pan().minDistance(5).onBegin(() => {
      const s = state.get(); const d = transport.get(); touchDemo(d, s, true); state.set(s); transport.set(d);
      scheduleOnRN(onTouch, true);
    }).onStart((e) => {
      const s = state.get();
      const point = (e.y - 28) / 68;
      if (point >= -0.42 && point <= s.furthest + 0.42) beginDrag(s);
      state.set(s); scheduleOnRN(dismiss);
    }).onUpdate((e) => {
      const s = state.get(); drag(s, e.translationY / (68 * k), e.velocityY / (68 * k)); state.set(s);
    }).onEnd((e) => {
      const s = state.get(); endDrag(s, e.velocityY / (68 * k)); state.set(s);
    }).onFinalize((_e, success) => {
      const s = state.get(); const d = transport.get();
      if (!success && s.dragging) endDrag(s, 0);
      touchDemo(d, s, false); state.set(s); transport.set(d); scheduleOnRN(onTouch, false);
    });
    const tap = Gesture.Tap().onEnd((e, success) => {
      if (!success) return;
      const s = state.get(); const target = Math.round((e.y - 28) / 68);
      if (target >= 0 && target <= s.furthest && target !== s.step) { goTo(s, target); state.set(s); }
      scheduleOnRN(dismiss);
    });
    return Gesture.Exclusive(pan, tap);
  }, [state, transport, k, onTouch, dismiss]);
  return <GestureDetector gesture={railGesture}><View accessible={false} pointerEvents={paid ? 'none' : 'auto'} style={{ position: 'absolute', left: 26, top: 100, width: 60, height: 328 }} /></GestureDetector>;
}

/** Presents the checkout form, physical light rail, and optional self-playing demonstration. */
export function BeamCheckoutStepper({ autoPlay = false }: { autoPlay?: boolean }) {
  const [size, setSize] = useState({ width: 402, height: 874 });
  const [canvasReady, setCanvasReady] = useState(false);
  const [fontsLoaded] = useFonts({
    'BeamInter-Regular': require('./assets/Inter-Regular.ttf'),
    'BeamInter-Bold': require('./assets/Inter-Bold.ttf'),
  });
  const [model, setModel] = useState<FormModel>(emptyForm);
  const [errorMessage, setErrorMessage] = useState('');
  const [labels, setLabels] = useState({ old: 'Continue', current: 'Continue' });
  const modelRef = useRef(emptyForm());
  const labelRef = useRef('Continue');
  const inputs = useRef<(TextInput | null)[]>([]);
  const realFocus = useRef(-1);
  const followKeyboard = useRef(false);
  const pendingFocus = useRef(-1);
  const userPaused = useRef(false);
  const handlers = useRef<{ action: (a: FormAction, silent?: boolean) => void; demo: (e: DemoEvent) => void; event: (e: SimEvent) => void }>({ action: () => {}, demo: () => {}, event: () => {} });
  const eventCallback = useCallback((event: SimEvent) => handlers.current.event(event), []);
  const demoCallback = useCallback((event: DemoEvent) => handlers.current.demo(event), []);
  const { motion, state, transport, send, touch, focused, still, labelMix } = useBeamMotion(autoPlay, eventCallback, demoCallback);
  const { frame } = motion;
  const keyboardLift = useSharedValue(0);
  const keyboardTop = useSharedValue(0);
  const k = Math.min(size.width / 402, size.height / 874);
  const ox = (size.width - 402 * k) / 2;
  const oy = (size.height - 874 * k) / 2;

  const action = useCallback((a: FormAction, silent = false) => {
    const before = modelRef.current;
    const result = formAction(before, a);
    modelRef.current = result.model; setModel(result.model);
    if (result.model.error) {
      setErrorMessage(result.model.error.message);
      if (!silent && a.type === 'continue') AccessibilityInfo.announceForAccessibility(result.model.error.message);
    }
    const label = ctaLabel(result.model);
    if (label !== labelRef.current) {
      setLabels({ old: labelRef.current, current: label }); labelRef.current = label; send('label', 0, 0, silent);
    }
    for (const c of result.commands) send(c.type, c.a ?? 0, c.b ?? 0, silent);
  }, [send]);
  const onTouch = useCallback((down: boolean) => {
    if (down) userPaused.current = true;
    touch(down);
  }, [touch]);
  const dismiss = useCallback(() => {
    Keyboard.dismiss(); realFocus.current = -1; focused(false); action({ type: 'focus', field: -1 });
  }, [focused, action]);
  const onContinue = useCallback(() => {
    const current = modelRef.current;
    followKeyboard.current = realFocus.current >= 0 && current.step < 2;
    action({ type: 'continue' });
    const error = modelRef.current.error;
    // With the keyboard up, the caret follows the focus light to the field that needs fixing.
    if (error && realFocus.current >= 0) inputs.current[error.field]?.focus();
    if (!error && !followKeyboard.current) dismiss();
  }, [action, dismiss]);
  const onPress = useCallback((index: number, down: boolean) => {
    onTouch(down); send('press', index, down ? 1 : 0);
  }, [onTouch, send]);
  const onRowPress = useCallback((index: number, down: boolean) => {
    onPress(F.ROW_PRESS + index, down); send('press', F.NODE_PRESS + index, down ? 1 : 0);
  }, [send, onPress]);
  const onGo = useCallback((target: number) => { dismiss(); action({ type: 'go', target }); }, [action, dismiss]);
  const onFocus = useCallback((field: number) => {
    realFocus.current = field; userPaused.current = true; focused(true); action({ type: 'focus', field });
  }, [focused, action]);
  const onBlur = useCallback(() => {
    realFocus.current = -1; focused(false); action({ type: 'focus', field: -1 });
  }, [focused, action]);
  useEffect(() => {
    handlers.current = {
      action,
      demo: (event) => {
        if (userPaused.current) return;
        if (event.action.type === 'press') send('press', event.action.index, event.action.down ? 1 : 0, true);
        else action(event.action, true);
      },
      event: (event) => {
        if (event.type === 'settled') {
          action({ type: 'settled', target: event.value }, event.silent);
          if (!event.silent && followKeyboard.current) {
            const first = FIELDS.findIndex((field) => field.step === event.value);
            if (first >= 0) pendingFocus.current = first; else dismiss();
            followKeyboard.current = false;
          }
        } else if (event.type === 'cleared') action({ type: 'cleared' }, event.silent);
        else if (event.type === 'restarted') {
          action({ type: 'restarted' }, event.silent); userPaused.current = false;
        } else if (event.type === 'newOrder' || event.type === 'paid') action({ type: event.type }, event.silent);
      },
    };
  }, [action, send, dismiss]);
  useEffect(() => {
    if (pendingFocus.current >= 0) {
      inputs.current[pendingFocus.current]?.focus(); pendingFocus.current = -1;
    }
  }, [model.step]);
  const ready = useCallback(() => setCanvasReady(true), []);
  useEffect(() => { if (canvasReady && fontsLoaded) send('ready'); }, [canvasReady, fontsLoaded, send]);
  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', (event) => {
      keyboardTop.set(event.endCoordinates.screenY);
      keyboardLift.set(withTiming(1, { duration: event.duration || 250, easing: Easing.bezier(0.32, 0.72, 0, 1), reduceMotion: ReduceMotion.Never }));
    });
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', (event) => {
      keyboardLift.set(withTiming(0, { duration: event.duration || 250, easing: Easing.bezier(0.32, 0.72, 0, 1), reduceMotion: ReduceMotion.Never }));
    });
    return () => { show.remove(); hide.remove(); };
  }, [keyboardLift, keyboardTop]);
  // The panel's bottom edge moves with its height, so the lift follows the live height.
  const lifted = useAnimatedStyle(() => {
    const bottom = oy + (G.panelTop + frame.get()[F.PANEL_H]) * k + 12;
    return { transform: [{ translateY: -keyboardLift.get() * Math.max(0, bottom - keyboardTop.get()) }] };
  });
  // Rows lifted under the status bar and the Dynamic Island fade into black instead of colliding with them.
  const topFade = useAnimatedStyle(() => {
    const bottom = oy + (G.panelTop + frame.get()[F.PANEL_H]) * k + 12;
    return { opacity: keyboardLift.get() * Math.min(1, Math.max(0, bottom - keyboardTop.get()) / 40) };
  });
  const present = useAnimatedStyle(() => ({ opacity: fontsLoaded && canvasReady ? frame.get()[F.PRESENT] : 0 }));
  const registerInput = useCallback((field: number, node: TextInput | null) => { inputs.current[field] = node; }, []);
  const nextInput = useCallback((field: number) => { inputs.current[field]?.focus(); }, []);
  const layout = useCallback((event: LayoutChangeEvent) => setSize(event.nativeEvent.layout), []);
  return <GestureHandlerRootView style={{ flex: 1, backgroundColor: '#000000' }}>
    <View onLayout={layout} style={{ flex: 1, overflow: 'hidden', backgroundColor: '#000000' }}>
      <Animated.View style={[{ position: 'absolute', inset: 0 }, lifted]}>
        <View accessible={false} importantForAccessibility="no-hide-descendants" pointerEvents="none" style={{ position: 'absolute', inset: 0 }}>
          <BeamCanvas width={size.width} height={size.height} k={k} ox={ox} oy={oy} motion={motion} still={still} onReady={ready} />
        </View>
        {/* Text measured before its font is registered keeps the fallback font's width: mount it afterwards. */}
        {fontsLoaded && <Animated.View pointerEvents={canvasReady ? 'box-none' : 'none'} style={[{ position: 'absolute', left: ox, top: oy, width: 402, height: 874, transformOrigin: [0, 0, 0], transform: [{ scale: k }] }, present]}>
          <Pressable accessible={false} onPressIn={() => onTouch(true)} onPressOut={() => onTouch(false)} onPress={dismiss} style={{ position: 'absolute', inset: 0 }} />
          <BeamRows motion={motion} model={model} onGo={onGo} onPress={onRowPress} />
          <Rail state={state} transport={transport} k={k} onTouch={onTouch} dismiss={dismiss} paid={model.paid} />
          <BeamPanel motion={motion} model={model} errorMessage={errorMessage} state={state} k={k} registerInput={registerInput} nextInput={nextInput} action={action} onFocus={onFocus} onBlur={onBlur} onContinue={onContinue} onPress={onPress} onTouch={onTouch} labels={labels} labelMix={labelMix} />
        </Animated.View>}
      </Animated.View>
      <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: 0, right: 0, top: 0, height: 136 }, topFade]}>
        <LinearGradient colors={['#000000', '#000000', 'rgba(0,0,0,0)']} locations={[0, 0.5, 1]} style={{ flex: 1 }} />
      </Animated.View>
    </View>
  </GestureHandlerRootView>;
}
