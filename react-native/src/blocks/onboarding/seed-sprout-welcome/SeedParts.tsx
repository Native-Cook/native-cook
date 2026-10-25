import { BlurMask, Group, LinearGradient, Path, Rect, Shadow, type SkPath, vec } from '@shopify/react-native-skia';
import { StyleSheet, Text } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { LEAVES, LOOP, PETALS, type Scene, clamp01, easeOut, hash, leafOpen, stemPoint } from './seedMotion';

/** The onboarding's pieces: rolling copy and button label, a leaf, a petal and the watering can. */

export const PAGES = [
  { title: 'Plant something small', body: 'Greenroom keeps every seed, pot and sill in one calm place.' },
  { title: 'Water on its own rhythm', body: "Gentle nudges tuned to each plant's thirst and your light." },
  { title: 'Watch it bloom', body: 'Log new leaves and buds, and see your garden come alive.' },
];
export const INK = '#23331F';
export const MUTE = 'rgba(35,51,31,0.62)';
export const GREEN = '#3E6B35';

export function PageCopy({ scene, i }: { scene: SharedValue<Scene>; i: number }) {
  const style = useAnimatedStyle(() => {
    const d = i - scene.get().page;
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.6), transform: [{ translateY: d * 28 }] };
  });
  return (
    <Animated.View style={[styles.page, style]}>
      <Text style={styles.title}>{PAGES[i].title}</Text>
      <Text style={styles.body}>{PAGES[i].body}</Text>
    </Animated.View>
  );
}

/** Leaf k: grows from the stem once the stem has passed it, unfurls with a springy overshoot. */
export function Leaf({ k, scene, stemUp, sway, leaf, veins }: { k: number; scene: SharedValue<Scene>; stemUp: SharedValue<number>; sway: SharedValue<number>; leaf: SkPath; veins: SkPath }) {
  const transform = useDerivedValue(() => {
    const L = LEAVES[k];
    const o = leafOpen(stemUp.get(), k);
    const p = stemPoint(L.u, sway.get() * L.u);
    const droop = 0.06 * Math.sin((2 * Math.PI * 2 * scene.get().t) / LOOP + k);
    const angle = p.angle + L.side * (0.25 + 0.95 * o.wide) + droop;
    const len = (L.len / 50) * Math.max(0.001, o.len);
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: angle }, { scaleX: len }, { scaleY: Math.max(0.001, len * (0.3 + 0.7 * o.wide)) }];
  });
  return (
    <Group transform={transform}>
      <Path path={leaf}>
        <LinearGradient start={vec(0, -12)} end={vec(50, 12)} colors={['#86BE68', '#55923F', '#336F2B']} />
        <Shadow dx={0} dy={2} blur={3} color="rgba(30,60,20,0.3)" />
      </Path>
      <Path path={veins} style="stroke" strokeWidth={1} strokeCap="round" color="rgba(220,245,190,0.45)" />
    </Group>
  );
}

/** Petal i: closed up into the bud, opening round the centre; at the end it lets go and falls. */
export function Petal({ i, scene, bloom, petal }: { i: number; scene: SharedValue<Scene>; bloom: SharedValue<number>; petal: SkPath }) {
  const transform = useDerivedValue(() => {
    const b = easeOut(bloom.get());
    const closed = -Math.PI / 2 + (i - 3) * 0.13;
    const open = -Math.PI / 2 + (i * 2 * Math.PI) / PETALS;
    const angle = closed + (open - closed) * b + Math.PI / 2;
    const scale = Math.max(0.001, (0.45 + 0.55 * b) * clamp01(bloom.get() * 5));
    const tau = scene.get().fall * 1.4 - i * 0.12;
    if (tau <= 0) return [{ rotate: angle }, { scale: scale * 1.25 }];
    const dx = 16 * Math.sin(tau * 5 + i) + tau * (i - 3) * 14;
    const dy = 0.5 * 240 * tau * tau;
    return [{ translateX: dx }, { translateY: dy }, { rotate: angle + tau * (hash(i) - 0.5) * 6 }, { scale: scale * 1.25 }];
  });
  const opacity = useDerivedValue(() => {
    const tau = scene.get().fall * 1.4 - i * 0.12;
    return tau <= 0 ? 1 : 1 - clamp01(tau / 1.05);
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={petal}>
        <LinearGradient start={vec(0, 0)} end={vec(0, -36)} colors={['#D9573F', '#EE7A62', '#F8B39E']} />
        <Shadow dx={0} dy={1.5} blur={2.5} color="rgba(120,30,20,0.3)" />
      </Path>
    </Group>
  );
}

export function ButtonLabel({ scene, last, label }: { scene: SharedValue<Scene>; last: boolean; label: string }) {
  const style = useAnimatedStyle(() => {
    const r = clamp01(scene.get().page - 1);
    const d = (last ? 1 : 0) - r;
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.8), transform: [{ translateY: d * 20 }] };
  });
  return <Animated.Text style={[styles.buttonText, style]}>{label}</Animated.Text>;
}

/** The watering can in its own frame (body centre at 0, 0): handle, spout, brass rose, enamel body. */
export function WateringCan({ body, spout, rose, handle }: { body: SkPath; spout: SkPath; rose: SkPath; handle: SkPath }) {
  return (
    <Group>
    <Path path={handle} style="stroke" strokeWidth={7} strokeCap="round" color="#2F5E57" />
    <Path path={spout}>
      <LinearGradient start={vec(-80, -20)} end={vec(-60, 20)} colors={['#5E9D93', '#3E7870', '#2B5A54']} />
    </Path>
    <Path path={rose}>
      <LinearGradient start={vec(-100, -30)} end={vec(-80, -10)} colors={['#D9B26A', '#B88B40', '#8E6626']} />
    </Path>
    <Path path={body}>
      <LinearGradient start={vec(-50, 0)} end={vec(50, 0)} colors={['#3A746C', '#67A79C', '#4A8A80', '#2E5F59']} positions={[0, 0.3, 0.6, 1]} />
      <Shadow dx={0} dy={10} blur={18} color="rgba(30,50,40,0.28)" />
      <Shadow dx={0} dy={1.5} blur={2.5} color="rgba(20,40,30,0.35)" />
    </Path>
    <Rect x={-46} y={-30} width={92} height={7} color="rgba(255,255,255,0.18)" />
    <Rect x={-26} y={-20} width={6} height={48} color="rgba(255,255,255,0.22)">
      <BlurMask blur={2} style="normal" />
    </Rect>
    </Group>
  );
}

const styles = StyleSheet.create({
  page: { position: 'absolute', left: 0, right: 0, top: 0, gap: 6 },
  title: { color: INK, fontSize: 28, fontWeight: '800', letterSpacing: -0.5 },
  body: { color: MUTE, fontSize: 16, fontWeight: '500', lineHeight: 22 },
  buttonText: { position: 'absolute', color: '#F1F5EA', fontSize: 17, fontWeight: '700' },
});
