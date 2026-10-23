import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { interpolateColor, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { SHEET_COPY, SHEET_FONT, SHEET_LABELS, SHEET_TONE, type SheetLayout } from './sheet-theme';
import { buttonLabelState } from './sheet-timeline';
import type { SheetMotion } from './use-sheet-motion';

/** Solid, rounded view strokes avoid another canvas readiness dependency. */
function Glyph({ kind }: { kind: number }) {
  if (kind === 2) return <View style={styles.infoRing}><View style={styles.infoDot} /><View style={styles.infoStem} /></View>;
  if (kind === 1) return <View style={styles.glyph}>
    <View style={[styles.stroke, { width: 16 }]} />
    <View style={[styles.stroke, { width: 9, left: 1, top: 4, transform: [{ rotate: '-45deg' }] }]} />
    <View style={[styles.stroke, { width: 9, left: 1, top: 10, transform: [{ rotate: '45deg' }] }]} />
  </View>;
  return <View style={styles.glyph}>
    <View style={[styles.stroke, { width: 13, left: 1.5, transform: [{ rotate: '45deg' }] }]} />
    <View style={[styles.stroke, { width: 13, left: 1.5, transform: [{ rotate: '-45deg' }] }]} />
  </View>;
}
export function SheetPuck({ kind, motion }: { kind: number; motion: SheetMotion }) {
  const { frame, puckDown, puckUp, activatePuck, ambientGesture } = motion;
  const gesture = useMemo(() => Gesture.Tap().maxDuration(600000).maxDistance(16).simultaneousWithExternalGesture(ambientGesture)
    .onBegin(() => { puckDown(kind); })
    .onEnd((_e, success) => { if (success) puckUp(true, kind); })
    .onFinalize((_e, success) => { if (!success) puckUp(false, kind); }), [ambientGesture, kind, puckDown, puckUp]);
  const scale = useAnimatedStyle(() => {
    const f = frame.get(); return { transform: [{ scale: 1 - 0.08 * (f.puck === kind ? f.puckPress : 0) }] };
  });
  const size = kind === 0 ? 32 : 36;
  return <GestureDetector gesture={gesture}>
    <View accessible accessibilityRole="button" accessibilityLabel={kind === 0 ? 'Close tips' : kind === 1 ? 'Back' : 'Show tips'}
      onAccessibilityTap={() => activatePuck(kind)} accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'activate') activatePuck(kind); }}
      style={styles.puckHit}>
      <Animated.View style={[{ width: size, height: size }, scale]}>
        <LinearGradient colors={kind === 0 ? ['#23242C', '#17181D'] : ['#25262E', '#1A1B21']}
          style={[styles.puck, { width: size, height: size, borderRadius: size / 2 }]}>
          <View style={[styles.puckHighlight, { width: size - 12 }]} />
          <Glyph kind={kind} />
        </LinearGradient>
      </Animated.View>
    </View>
  </GestureDetector>;
}
function ClayCoin({ size }: { size: number }) {
  return <LinearGradient colors={['#FFF17A', '#FFE23A', '#CBA614']}
    style={{ width: size, height: size, borderRadius: size / 2, overflow: 'hidden' }}>
    <View style={[styles.coinLight, { left: size / 4, right: size / 4, top: size / 12, height: size / 12 }]} />
  </LinearGradient>;
}
export function SheetPage({ layout, motion }: { layout: SheetLayout; motion: SheetMotion }) {
  const { frame, semantics } = motion;
  const dim = useAnimatedStyle(() => ({ opacity: 1 - frame.get().open * 0.3 }));
  return <Animated.View accessibilityElementsHidden={semantics.open} importantForAccessibility={semantics.open ? 'no-hide-descendants' : 'auto'}
    style={[StyleSheet.absoluteFill, dim]}>
    <View style={{ position: 'absolute', left: 12, top: layout.headerY - 22 }}><SheetPuck kind={1} motion={motion} /></View>
    <View style={{ position: 'absolute', right: 12, top: layout.headerY - 22 }}><SheetPuck kind={2} motion={motion} /></View>
    <View accessible accessibilityLabel="noku" style={[styles.wordmark, { top: layout.headerY - 12, width: layout.width }]} pointerEvents="none">
      <ClayCoin size={12} />
      <Text allowFontScaling={false} style={styles.wordmarkText}>noku</Text>
    </View>
    <Image source={require('./assets/avatar.png')} accessible={false} style={{ position: 'absolute',
      left: (layout.width - layout.avatarSize) / 2, top: layout.avatarY - layout.avatarSize / 2,
      width: layout.avatarSize, height: layout.avatarSize, borderRadius: layout.compact ? 12 : 14 }} />
    {!layout.compact && <View style={[styles.name, { top: layout.nameY - 10, width: layout.width }]}>
      <Text allowFontScaling={false} style={styles.nameText}>Mina P.</Text>
      <Svg width={12} height={12} viewBox="0 0 12 12" accessible={false}>
        <Path d="M6 0 11 2V6C11 9 8 11 6 12 4 11 1 9 1 6V2Z" fill="#3D63F5" />
        <Path d="m3.7 5.8 1.6 1.6 3-3" stroke="#FFFFFF" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </Svg>
    </View>}
    <View pointerEvents="none" style={{ position: 'absolute', top: layout.titleY - layout.titleLine / 2, width: layout.width }}>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.pageTitle, { fontSize: layout.titleSize, lineHeight: layout.titleLine }]}>To Mina Park</Text>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.pageTitle, { fontSize: layout.titleSize, lineHeight: layout.titleLine }]}>$24.00</Text>
      {!layout.compact && <Text allowFontScaling={false} numberOfLines={1}
        style={[styles.pageTitle, { fontSize: layout.titleSize, lineHeight: layout.titleLine, color: '#6E707A' }]}>Send it now?</Text>}
    </View>
    {layout.balance.visible && <View accessible accessibilityLabel="From noku balance, $182.40 available" pointerEvents="none"
      style={[styles.balance, { left: layout.balance.x, top: layout.balance.y, width: layout.balance.width }]}>
      <View style={styles.balanceHighlight} />
      <ClayCoin size={28} />
      <View style={styles.balanceCopy}>
        <Text allowFontScaling={false} numberOfLines={1} style={styles.balanceTitle}>noku balance</Text>
        <Text allowFontScaling={false} numberOfLines={1} style={styles.balanceSubtitle}>$182.40 available</Text>
      </View>
      <Svg width={16} height={16} viewBox="0 0 16 16" accessible={false}>
        <Path d="m3 6 5 5 5-5" fill="none" stroke="#6E707A" strokeWidth={2.25} strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    </View>}
  </Animated.View>;
}
export function SheetCopy({ index, layout, motion, reduced }: { index: number; layout: SheetLayout; motion: SheetMotion; reduced: boolean }) {
  const { frame, semantics } = motion; const { width } = layout;
  const style = useAnimatedStyle(() => {
    const f = frame.get(); const distance = index - f.position;
    // Without travel the two blocks share one place: fade one out before the next comes in.
    return { opacity: f.contents * (1 - Math.min(1, Math.abs(distance) * (reduced ? 2.2 : 1.4))),
      transform: [{ translateX: reduced ? 0 : distance * width }] };
  });
  const item = SHEET_COPY[index]; const lines = index % 2 === 0 ? 1 : 2;
  return <Animated.View pointerEvents="none" accessibilityElementsHidden={semantics.step !== index}
    importantForAccessibility={semantics.step !== index ? 'no-hide-descendants' : 'auto'}
    style={[styles.copy, { bottom: layout.height - layout.copyBottom, width: layout.width }, style]}>
    <Text accessibilityRole="header" numberOfLines={1} allowFontScaling={false} style={styles.copyTitle}>{item.title}</Text>
    <Text numberOfLines={lines} allowFontScaling={false} style={styles.subtitle}>{item.subtitle}</Text>
  </Animated.View>;
}
function Dot({ index, motion }: { index: number; motion: SheetMotion }) {
  const { frame } = motion;
  const style = useAnimatedStyle(() => {
    const weight = Math.max(0, 1 - Math.abs(frame.get().position - index));
    return { width: 6 + 12 * weight, backgroundColor: interpolateColor(weight, [0, 1], ['#5A5D66', '#F4F5F8']) };
  });
  return <Animated.View style={[styles.dot, style]} />;
}
export function SheetDots({ layout, motion }: { layout: SheetLayout; motion: SheetMotion }) {
  const { frame, semantics } = motion;
  const fade = useAnimatedStyle(() => ({ opacity: frame.get().contents }));
  return <Animated.View accessible accessibilityLabel={`Step ${semantics.step + 1} of 4`}
    style={[styles.dots, { bottom: layout.height - layout.dotsTop - 6, width: layout.width }, fade]}>
    <Dot index={0} motion={motion} /><Dot index={1} motion={motion} /><Dot index={2} motion={motion} /><Dot index={3} motion={motion} />
  </Animated.View>;
}
function ButtonLabel({ index, motion, reduced }: { index: number; motion: SheetMotion; reduced: boolean }) {
  const { frame } = motion;
  const style = useAnimatedStyle(() => {
    const label = buttonLabelState(frame.get(), index);
    return { opacity: label.opacity, transform: [{ translateY: reduced ? 0 : label.y }] };
  });
  return <Animated.Text allowFontScaling={false} numberOfLines={1} accessible={false}
    style={[styles.buttonLabel, style]}>{SHEET_LABELS[index]}</Animated.Text>;
}
export function SheetButton({ layout, motion, reduced }: { layout: SheetLayout; motion: SheetMotion; reduced: boolean }) {
  const { frame, buttonGesture, activateButton, semantics } = motion;
  const scale = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * frame.get().buttonPress }] }));
  const shade = useAnimatedStyle(() => ({ opacity: Math.max(0, frame.get().buttonPress) * 0.09 }));
  return <GestureDetector gesture={buttonGesture}>
    <Animated.View accessible accessibilityRole="button" accessibilityLabel={SHEET_LABELS[semantics.label]}
      onAccessibilityTap={activateButton} accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'activate') activateButton(); }}
      style={[styles.buttonShadow, { position: 'absolute', left: layout.button.x, top: layout.button.y,
        width: layout.button.width, height: layout.button.height }, scale]}>
      <LinearGradient colors={['#FFEC4F', '#FFDD1F']} style={styles.buttonFill}>
        <View style={styles.buttonTop} /><View style={styles.buttonBottom} />
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: '#4D3D00' }, shade]} />
        <ButtonLabel index={0} motion={motion} reduced={reduced} />
        <ButtonLabel index={1} motion={motion} reduced={reduced} />
        <ButtonLabel index={2} motion={motion} reduced={reduced} />
      </LinearGradient>
    </Animated.View>
  </GestureDetector>;
}
export function SheetTouchMarker({ layout, motion }: { layout: SheetLayout; motion: SheetMotion }) {
  const { frame } = motion; const { width, stageTop, stageHeight, button } = layout;
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    const x = f.markerKind === 2 ? width / 2 + f.markerX - 201 : width / 2;
    const y = f.markerKind === 0 ? button.y + 28 : f.markerKind === 2
      ? stageTop + stageHeight - (744 - f.markerY) : stageTop + stageHeight / 2;
    return { opacity: f.markerOpacity, transform: [{ translateX: x - 18 }, { translateY: y - 18 }, { scale: 1 - 0.14 * f.markerPress }] };
  });
  return <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.marker, style]} />;
}
const styles = StyleSheet.create({
  puckHit: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  puck: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  puckHighlight: { position: 'absolute', height: 1, top: 1, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.10)' },
  glyph: { width: 16, height: 16 }, stroke: { position: 'absolute', top: 7, left: 0, height: 2.25, borderRadius: 2, backgroundColor: '#C9CBD3' },
  infoRing: { width: 17, height: 17, borderRadius: 9, borderWidth: 2.25, borderColor: '#C9CBD3', alignItems: 'center' },
  infoDot: { position: 'absolute', top: 2, width: 2.25, height: 2.25, borderRadius: 2, backgroundColor: '#C9CBD3' },
  infoStem: { position: 'absolute', top: 6, width: 2.25, height: 5, borderRadius: 2, backgroundColor: '#C9CBD3' },
  wordmark: { position: 'absolute', height: 24, flexDirection: 'row', gap: 6, alignItems: 'center', justifyContent: 'center' },
  coinLight: { position: 'absolute', left: 3, right: 3, top: 1, height: 1, backgroundColor: '#FFF5B8', borderRadius: 1 },
  wordmarkText: { fontFamily: SHEET_FONT.bold, fontSize: 17, lineHeight: 24, letterSpacing: -0.2, color: SHEET_TONE.text },
  name: { position: 'absolute', flexDirection: 'row', gap: 4, alignItems: 'center', justifyContent: 'center', height: 20 },
  nameText: { fontFamily: SHEET_FONT.regular, fontSize: 13, lineHeight: 20, color: '#8E909A' },
  pageTitle: { fontFamily: SHEET_FONT.bold, textAlign: 'center', color: SHEET_TONE.text },
  balance: { position: 'absolute', height: 48, borderRadius: 16, backgroundColor: '#16171C', overflow: 'hidden',
    flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, gap: 12 },
  balanceHighlight: { position: 'absolute', left: 16, right: 16, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.05)' },
  balanceCopy: { flex: 1, gap: 0 },
  balanceTitle: { fontFamily: SHEET_FONT.bold, fontSize: 15, lineHeight: 20, color: '#F4F5F8' },
  balanceSubtitle: { fontFamily: SHEET_FONT.regular, fontSize: 13, lineHeight: 20, color: '#8E909A' },
  copy: { position: 'absolute', paddingHorizontal: 16, alignItems: 'center', gap: 8 },
  copyTitle: { fontFamily: SHEET_FONT.bold, fontSize: 20, lineHeight: 24, textAlign: 'center', color: SHEET_TONE.text },
  subtitle: { fontFamily: SHEET_FONT.regular, fontSize: 14, lineHeight: 20, textAlign: 'center', color: SHEET_TONE.muted },
  dots: { position: 'absolute', flexDirection: 'row', height: 6, gap: 6, alignItems: 'center', justifyContent: 'center' },
  dot: { height: 6, borderRadius: 3 },
  buttonShadow: { borderRadius: 28, shadowColor: '#FFE23A', shadowOpacity: 0.14, shadowRadius: 20, shadowOffset: { width: 0, height: 6 } },
  buttonFill: { flex: 1, borderRadius: 28, overflow: 'hidden', justifyContent: 'center' },
  buttonTop: { position: 'absolute', left: 20, right: 20, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.55)' },
  buttonBottom: { position: 'absolute', left: 20, right: 20, bottom: 0, height: 1, backgroundColor: 'rgba(128,96,0,0.26)' },
  buttonLabel: { position: 'absolute', left: 0, right: 0, top: 16, fontFamily: SHEET_FONT.bold, fontSize: 17, lineHeight: 24, textAlign: 'center', color: SHEET_TONE.ink, includeFontPadding: false },
  marker: { position: 'absolute', left: 0, top: 0, width: 36, height: 36, borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.22)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.35)' },
});
