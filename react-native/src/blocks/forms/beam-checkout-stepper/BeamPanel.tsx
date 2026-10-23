import { useCallback, useMemo } from 'react';
import { Text, TextInput, View, type TextInputProps } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { C, CONTENT_BOTTOM, F, FIELDS, FONT, G, ORDER_ID, STEPS, contentIn, contentOut, contentShift, ctaLeft, ctaWidth, type BeamMotion } from './beam-theme';
import { reviewLines, type FormAction, type FormModel } from './beam-form';
import { dragDelivery, endDelivery, type BeamState } from './beam-sim';

type Props = {
  motion: BeamMotion; model: FormModel; errorMessage: string; state: SharedValue<BeamState>; k: number;
  registerInput: (field: number, node: TextInput | null) => void; nextInput: (field: number) => void; action: (action: FormAction) => void;
  onFocus: (field: number) => void; onBlur: () => void; onContinue: () => void;
  onPress: (index: number, down: boolean) => void; onTouch: (down: boolean) => void;
  labels: { old: string; current: string }; labelMix: SharedValue<number>;
};
const autofill: TextInputProps['autoComplete'][] = ['email', 'street-address', 'off', 'postal-code', 'cc-number', 'cc-exp', 'cc-csc'];
function Field({ field, props }: { field: number; props: Props }) {
  const { motion: { frame }, model, registerInput, nextInput, action, onFocus, onBlur, onContinue, onTouch } = props;
  const r = FIELDS[field];
  const label = useAnimatedStyle(() => {
    const amount = frame.get()[F.FIELD_FLOAT + field];
    return { transform: [{ translateY: (G.labelFloatY - G.labelRestY) * amount }, { scale: 1 + (G.labelFloatScale - 1) * amount }] };
  });
  const last = field === 0 || field === 3 || field === 6;
  const error = model.error?.field === field ? `, error: ${model.error.message}` : '';
  return (
    <View style={{ position: 'absolute', left: r.x, top: r.y, width: r.w, height: 60 }}>
      <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', left: 16, top: 18, width: r.w - 32, height: 20, transformOrigin: [0, 0, 0] }, label]}>
        <Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.regular, fontSize: 16, lineHeight: 20, color: C.textMute }}>{r.label}</Text>
      </Animated.View>
      <TextInput ref={(node) => { registerInput(field, node); }} value={model.values[field]} editable={model.step === r.step && !model.paid}
        accessibilityLabel={`${r.label}${error}`} allowFontScaling={false} numberOfLines={1}
        inputMode={field === 0 ? 'email' : field >= 3 ? 'numeric' : 'text'} autoComplete={autofill[field]} textContentType={field === 2 ? 'addressCity' : undefined}
        autoCapitalize={field === 0 || field >= 3 ? 'none' : 'words'} autoCorrect={false}
        selectionColor={C.beam} keyboardAppearance="dark" returnKeyType={last ? 'done' : 'next'}
        submitBehavior="submit"
        onTouchStart={() => onTouch(true)} onTouchEnd={() => onTouch(false)} onTouchCancel={() => onTouch(false)}
        onChangeText={(value) => action({ type: 'edit', field, value })}
        onFocus={() => onFocus(field)} onBlur={onBlur}
        onSubmitEditing={() => { if (last) onContinue(); else nextInput(field + 1); }}
        style={{ position: 'absolute', left: 0, top: -2, width: r.w, height: 60, padding: 0, paddingHorizontal: 16, paddingTop: 27, paddingBottom: 9, fontFamily: FONT.regular, fontSize: 16, lineHeight: 24, color: C.text, backgroundColor: 'transparent', fontVariant: field >= 3 ? ['tabular-nums'] : undefined }} />
    </View>
  );
}
function DeliveryLabel({ index, selected, frame }: { index: number; selected: boolean; frame: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const p = Math.max(0, Math.min(1, (f[F.SEG_LEAD] + f[F.SEG_TRAIL]) / 2));
    const a = index === 0 ? 1 - p : p; return { opacity: selected ? a : 1 - a };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }, style]}><Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.bold, fontSize: 14, color: selected ? C.text : C.textMute }}>{index === 0 ? 'Standard · Free' : 'Express · $12'}</Text></Animated.View>;
}
function Delivery({ props }: { props: Props }) {
  const { state, k, onTouch, action, onPress, model, motion: { frame } } = props;
  const dragging = useSharedValue(false);
  const committed = useCallback((express: boolean) => action({ type: 'delivery', express }), [action]);
  const gesture = useMemo(() => Gesture.Pan().activeOffsetX([-5, 5]).onStart(() => {
    const s = state.get(); s.deliveryGrab = s.frame[F.SEG_LEAD]; state.set(s); dragging.set(true);
    scheduleOnRN(onTouch, true);
  }).onUpdate((e) => {
    const s = state.get(); dragDelivery(s, s.deliveryGrab + e.translationX / (161 * k)); state.set(s);
  }).onEnd((e) => {
    const s = state.get(); const express = endDelivery(s, e.velocityX / (161 * k)); state.set(s); scheduleOnRN(committed, express);
  }).onFinalize((_e, success) => {
    if (dragging.get()) {
      if (!success) { const s = state.get(); const express = endDelivery(s, 0); state.set(s); scheduleOnRN(committed, express); }
      dragging.set(false); scheduleOnRN(onTouch, false);
    }
  }), [state, k, onTouch, committed, dragging]);
  return <GestureDetector gesture={gesture}><View accessibilityRole="radiogroup" accessibilityLabel="Delivery speed" style={{ position: 'absolute', left: 16, top: 178, width: 322, height: 60 }}>
    {[0, 1].map((index) => <Pressable key={index} accessibilityRole="radio" accessibilityLabel={index ? 'Express, $12' : 'Standard, free'} accessibilityState={{ checked: model.express === (index === 1) }} onPressIn={() => onPress(F.SEG_PRESS, true)} onPressOut={() => onPress(F.SEG_PRESS, false)} onPress={() => action({ type: 'delivery', express: index === 1 })} style={{ position: 'absolute', left: index * 161, width: 161, height: 60 }}>
      <DeliveryLabel index={index} selected={false} frame={frame} /><DeliveryLabel index={index} selected frame={frame} />
    </Pressable>)}
  </View></GestureDetector>;
}
function PanelStep({ index, props }: { index: number; props: Props }) {
  const { model, motion: { frame }, errorMessage } = props;
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const incoming = f[F.PANEL_TO] === index;
    return { top: G.panelTop, height: f[F.PANEL_H], opacity: incoming ? contentIn(f[F.PANEL_MIX]) : f[F.PANEL_FROM] === index ? contentOut(f[F.PANEL_MIX]) : 0, transform: [{ translateY: incoming ? contentShift(f[F.PANEL_MIX]) : 0 }] };
  });
  const errorStyle = useAnimatedStyle(() => ({ opacity: frame.get()[F.ERR] }));
  const interactive = model.step === index;
  return <Animated.View pointerEvents={interactive ? 'box-none' : 'none'} accessibilityElementsHidden={!interactive} importantForAccessibility={interactive ? 'auto' : 'no-hide-descendants'} style={[{ position: 'absolute', left: 24, width: 354 }, style]}>
    <Text allowFontScaling={false} numberOfLines={1} style={{ position: 'absolute', left: 16, top: 16, fontSize: 17, lineHeight: 24, fontFamily: FONT.bold, color: C.text }}>{STEPS[index].title}</Text>
    <Text allowFontScaling={false} numberOfLines={1} style={{ position: 'absolute', right: 16, top: 19, fontSize: 13, lineHeight: 20, fontFamily: FONT.regular, color: C.textMute, fontVariant: ['tabular-nums'] }}>{index === 4 ? ORDER_ID : `${index + 1} of 5`}</Text>
    {FIELDS.map((field, f) => field.step === index ? <Field key={f} field={f} props={props} /> : null)}
    {index === 1 && <Delivery props={props} />}
    {index === 3 && reviewLines(model).map(([label, value], i) => <View key={label} pointerEvents="none" style={{ position: 'absolute', left: 16, top: 52 + 32 * i, width: 322, height: 32, justifyContent: 'center' }}>
      <Text allowFontScaling={false} numberOfLines={1} style={{ fontSize: 14, fontFamily: FONT.regular, color: C.textMute }}>{label}</Text>
      <Text allowFontScaling={false} numberOfLines={1} ellipsizeMode="tail" style={{ position: 'absolute', right: 0, maxWidth: 220, fontSize: 14, fontFamily: FONT.regular, color: C.text, textAlign: 'right' }}>{value}</Text>
    </View>)}
    {index === 4 && <Text allowFontScaling={false} numberOfLines={1} style={{ position: 'absolute', left: 16, top: 48, right: 16, fontSize: 14, lineHeight: 20, fontFamily: FONT.regular, color: C.textMute }}>{`Receipt sent to ${model.values[0]}`}</Text>}
    {index < 3 && <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', left: 16, right: 16, top: CONTENT_BOTTOM[index] + 8 }, errorStyle]}><Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.regular, fontSize: 13, lineHeight: 16, color: C.errorText }}>{errorMessage}</Text></Animated.View>}
  </Animated.View>;
}
function CTAText({ props, old, dark }: { props: Props; old: boolean; dark: boolean }) {
  const { labelMix, motion: { frame }, labels } = props;
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const m = labelMix.get() * 0.26;
    const label = old ? 1 - Math.min(1, m / 0.1) : Math.max(0, Math.min(1, (m - 0.1) / 0.16));
    return { opacity: label * (dark ? f[F.CTA_DONE] : 1 - f[F.CTA_DONE]) * (1 - Math.max(0, Math.min(1, f[F.CTA_LENS] / 0.3))) };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }, style]}><Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.bold, fontSize: 17, color: dark ? C.ink : C.text }}>{old ? labels.old : labels.current}</Text></Animated.View>;
}
/** Keeps every panel layer mounted while the simulation drives height, copy, focus, and controls. */
export function BeamPanel(props: Props) {
  const { motion: { frame }, model, onContinue, onPress, action } = props;
  const cta = useAnimatedStyle(() => {
    const f = frame.get(); return { top: G.panelTop + f[F.PANEL_H] - 74, left: 24 + ctaLeft(f[F.CTA_SPLIT], f[F.CTA_LENS]), width: ctaWidth(f[F.CTA_SPLIT], f[F.CTA_LENS]), transform: [{ scale: 1 - 0.03 * f[F.CTA_PRESS] }] };
  });
  const back = useAnimatedStyle(() => {
    const f = frame.get(); return { top: G.panelTop + f[F.PANEL_H] - 74, opacity: f[F.CTA_SPLIT], transform: [{ scale: 1 - 0.03 * f[F.BACK_PRESS] }] };
  });
  return <View pointerEvents="box-none" style={{ position: 'absolute', inset: 0 }}>
    {STEPS.map((_, index) => <PanelStep key={index} index={index} props={props} />)}
    <Animated.View pointerEvents={model.step > 0 && model.step < 4 && !model.paid ? 'auto' : 'none'} style={[{ position: 'absolute', left: 38, width: 60, height: 60 }, back]}>
      <Pressable accessible={model.step > 0 && model.step < 4 && !model.paid} accessibilityElementsHidden={model.step === 0 || model.paid} accessibilityRole="button" accessibilityLabel="Back" onPressIn={() => onPress(F.BACK_PRESS, true)} onPressOut={() => onPress(F.BACK_PRESS, false)} onPress={() => action({ type: 'go', target: model.step - 1 })} style={{ flex: 1 }} />
    </Animated.View>
    <Animated.View style={[{ position: 'absolute', height: 60 }, cta]}>
      <CTAText props={props} old dark={false} /><CTAText props={props} old={false} dark={false} /><CTAText props={props} old dark /><CTAText props={props} old={false} dark />
      <Pressable accessibilityRole="button" accessibilityLabel={props.labels.current} onPressIn={() => onPress(F.CTA_PRESS, true)} onPressOut={() => onPress(F.CTA_PRESS, false)} onPress={onContinue} style={{ position: 'absolute', inset: 0 }} />
    </Animated.View>
  </View>;
}
