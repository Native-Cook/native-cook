/** An onboarding screen: pull a glass dome up into a lens that shows the next screen, and it bursts into a cloud of people. */
import { useImage } from '@shopify/react-native-skia';
import { SymbolView } from 'expo-symbols';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import { Backdrop, buttonRects, GlassLayer } from './GlassScene';
import { EASE_OUT, FADE, UPGRADE } from './motion';
import { COLOR, COPY, LAYOUT, SPACE, TYPE } from './theme';
import { useGlassDisc } from './use-glass-disc';

export type GlassDiscOnboardingProps = { autoPlay?: boolean };

const atlasSource = require('./assets/bubbles.png');

// SF Pro's ascender at these sizes, so a text block can be placed by its first baseline as measured on the source.
const ASCENT = 0.94;

/**
 * A pill that answers the finger: a tinted glass lens slides to the touch point inside it (Liquid Glass), the pill
 * presses to 0.97, and a bright top edge reads as its rim.
 */
function GlassButton({ dark, label, icon }: { dark?: boolean; label: string; icon: 'envelope.fill' | 'phone.fill' }) {
  const lensX = useSharedValue(0);
  const lensOn = useSharedValue(0);
  const lens = useAnimatedStyle(() => ({ opacity: lensOn.get(), transform: [{ translateX: lensX.get() - LENS_W / 2 }] }));
  const press = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - UPGRADE.pressScale) * lensOn.get() }] }));
  const ink = dark ? COLOR.buttonDarkInk : COLOR.buttonLightInk;
  return <APressable accessibilityRole="button" accessibilityLabel={label}
    onPressIn={(e) => { lensX.set(e.nativeEvent.locationX); lensOn.set(withTiming(1, { duration: UPGRADE.pressIn, easing: LENS_EASE })); }}
    onPressOut={() => { lensOn.set(withTiming(0, { duration: UPGRADE.pressOut, easing: LENS_EASE })); }}
    style={[styles.button, dark ? styles.dark : styles.light, press]}>
    <View style={[StyleSheet.absoluteFill, styles.clip]} pointerEvents="none">
      <Animated.View style={[styles.lens, lens]}>
        <LinearGradient colors={dark ? LENS_DARK : LENS_LIGHT} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
      </Animated.View>
      {!dark && <View style={styles.rim} />}
    </View>
    <SymbolView name={icon} size={LAYOUT.buttonIcon} tintColor={ink} weight="medium" style={styles.icon} />
    <Text style={[styles.label, dark ? styles.labelDark : { color: ink }]}>{label}</Text>
  </APressable>;
}

const APressable = Animated.createAnimatedComponent(Pressable);
const LENS_W = 120;
const LENS_EASE = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const LENS_LIGHT = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0)'] as const;
const LENS_DARK = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.16)', 'rgba(255,255,255,0)'] as const;

function Screen({ width, height, autoPlay }: { width: number; height: number; autoPlay: boolean }) {
  const reduceMotion = useReducedMotion();
  const atlas = useImage(atlasSource);
  const { values, stickers, finger, gesture, stage, enter, startOver } = useGlassDisc({
    width, height, ready: atlas !== null, autoPlay, reduceMotion,
  });
  const R0 = width * LAYOUT.domeRadius;
  // The intro lifts 6 pt as it fades out under the rising glass, and settles back the same way on a rewind.
  const intro = useAnimatedStyle(() => ({
    opacity: values.introAlpha.get(),
    transform: [{ translateY: reduceMotion ? 0 : -(1 - values.introAlpha.get()) * UPGRADE.rise }],
  }));
  // The hint brightens as the glint passes over the dome, so the light points at the gesture.
  const hint = useAnimatedStyle(() => ({
    opacity: values.hintAlpha.get() * (UPGRADE.hintBase + (1 - UPGRADE.hintBase) * values.glint.get()),
    transform: [{ translateY: values.discY.get() - (height * LAYOUT.domeTop + R0) }],
  }));
  // The second screen's lines rise into place as they fade in (ease-out, 60 ms apart); Reduce Motion only fades.
  const rise = (v: number) => {
    'worklet';
    return reduceMotion ? 0 : (1 - (1 - Math.pow(1 - v, 3))) * UPGRADE.rise;
  };
  const head = useAnimatedStyle(() => ({ opacity: values.headIn.get(), transform: [{ translateY: rise(values.headIn.get()) }] }));
  const rest = useAnimatedStyle(() => ({ opacity: values.restIn.get(), transform: [{ translateY: rise(values.restIn.get()) }] }));
  const restLate = useAnimatedStyle(() => {
    const v = Math.max(0, Math.min(1, values.restIn.get() * (1 + UPGRADE.lineStagger / FADE.restMs) - UPGRADE.lineStagger / FADE.restMs));
    return { opacity: v, transform: [{ translateY: rise(v) }] };
  });
  const community = stage === 'community';
  const b = buttonRects(width, height);
  const introTop = height * LAYOUT.introBaseline - (TYPE.intro.lineHeight - TYPE.intro.fontSize) / 2 - TYPE.intro.fontSize * ASCENT;
  const headTop = height * LAYOUT.headlineBaseline - (TYPE.headlineLine - TYPE.headline) / 2 - TYPE.headline * ASCENT;

  return <View style={styles.root}>
    <Backdrop width={width} height={height} values={values} />
    <GestureDetector gesture={gesture}>
      <View style={StyleSheet.absoluteFill}
        accessible accessibilityRole={community ? 'header' : 'button'}
        accessibilityLabel={community ? `${COPY.headline} ${COPY.sub.join(' ')}` : `${COPY.intro.join(' ')} ${COPY.hint}`}
        accessibilityHint={community ? undefined : 'Pull the glass dome up to enter'}
        accessibilityActions={community ? [{ name: 'activate', label: COPY.back }] : [{ name: 'activate', label: COPY.enter }]}
        onAccessibilityAction={(e) => {
          if (e.nativeEvent.actionName !== 'activate') return;
          if (community) startOver();
          else enter();
        }}>
        <Animated.View style={[styles.block, { top: introTop }, intro]} pointerEvents="none">
          {COPY.intro.map((l) => <Text key={l} style={TYPE.intro}>{l}</Text>)}
        </Animated.View>
        <View style={[styles.block, { top: headTop }]} pointerEvents="none">
          <Animated.View style={head}><Text style={styles.headline}>{COPY.headline}</Text></Animated.View>
          <Animated.View style={rest}><Text style={[styles.headline, styles.sub]}>{COPY.sub[0]}</Text></Animated.View>
          <Animated.View style={restLate}><Text style={[styles.headline, styles.sub]}>{COPY.sub[1]}</Text></Animated.View>
        </View>
      </View>
    </GestureDetector>
    <GlassLayer width={width} height={height} values={values} stickers={stickers} finger={finger} atlas={atlas} />
    <Animated.Text style={[TYPE.hint, styles.hint, { top: height * LAYOUT.hintY }, hint]} pointerEvents="none">{COPY.hint}</Animated.Text>
    <Animated.View style={[styles.buttons, { top: b.lightY }, rest]} pointerEvents={community ? 'box-none' : 'none'}>
      <GlassButton label={COPY.email} icon="envelope.fill" />
      <GlassButton label={COPY.phone} icon="phone.fill" dark />
    </Animated.View>
  </View>;
}

export function GlassDiscOnboarding({ autoPlay = false }: GlassDiscOnboardingProps) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((p) => (p.width === width && p.height === height ? p : { width, height }));
  }, []);
  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style="dark" />
    <View style={styles.root} onLayout={onLayout}>
      {size.width > 0 && <Screen key={`${size.width}x${size.height}`} width={size.width} height={size.height} autoPlay={autoPlay} />}
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bgTL },
  block: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  headline: {
    fontSize: TYPE.headline, lineHeight: TYPE.headlineLine, letterSpacing: TYPE.headlineTracking,
    fontWeight: '400', color: COLOR.ink, textAlign: 'center',
  },
  sub: { color: COLOR.inkSoft },
  hint: { position: 'absolute', left: 0, right: 0 },
  buttons: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, gap: LAYOUT.buttonGap },
  button: {
    height: LAYOUT.buttonHeight, borderRadius: LAYOUT.buttonHeight / 2, alignItems: 'center', justifyContent: 'center',
  },
  light: {
    backgroundColor: COLOR.buttonLight, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.buttonLightEdge,
    shadowColor: COLOR.buttonLightShadow, shadowOpacity: 0.16, shadowRadius: 6, shadowOffset: { width: 0, height: 2 },
  },
  dark: { backgroundColor: COLOR.buttonDark, height: LAYOUT.buttonHeightDark, borderRadius: LAYOUT.buttonHeightDark / 2 },
  clip: { borderRadius: LAYOUT.buttonHeightDark / 2, overflow: 'hidden' },
  lens: { position: 'absolute', top: 0, bottom: 0, width: LENS_W },
  // Inset to where the pill's top edge turns flat (radius 26): at 14 pt the hairline stood ~3 pt off the curve.
  rim: { position: 'absolute', left: SPACE.xl, right: SPACE.xl, top: 0, height: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(255,255,255,0.9)' },
  icon: { position: 'absolute', left: LAYOUT.buttonIconInset, width: LAYOUT.buttonIcon, height: LAYOUT.buttonIcon },
  label: { fontSize: TYPE.button.fontSize, letterSpacing: TYPE.button.letterSpacing, fontWeight: '400' },
  labelDark: { color: COLOR.buttonDarkInk, fontWeight: '600' },
});
