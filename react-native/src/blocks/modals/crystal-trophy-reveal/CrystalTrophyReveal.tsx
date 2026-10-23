// One canvas, one simulation, one touch router. All three material layers must be ready before presentation.
import { Canvas, Fill, Group } from '@shopify/react-native-skia';
import { useMemo, useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { StageBack, StageFront } from './StageLayers';
import { COPY, layout, projectTrophy, type TrophyFrame } from './trophy-contract';
import { useTrophyFonts } from './trophy-fonts';
import { TrophyControls, TrophyControlTargets } from './TrophyControls';
import { Trophy } from './TrophyLayer';
import { TrophyCopy, TrophyUi } from './TrophyUi';
import { trophyHitBox, useTrophyMotion } from './use-trophy-motion';

export type CrystalTrophyRevealProps = { autoPlay?: boolean; onClose?: () => void; onShare?: () => void };
/** Keep the contract's composition, reserving 16 points below the tilted date on short phones. */
export function screenFrame(width: number, height: number, top: number, bottom: number): TrophyFrame {
  const frame = layout(width, height, top, bottom);
  const apex = projectTrophy(0, 2.9, 0, 0, frame).y;
  const dateBottom = frame.dateY + frame.dateSize * 0.5 + Math.sin(-frame.titleTilt) * frame.dateSize * 4.8;
  const lift = Math.max(0, dateBottom + 16 - apex);
  return { ...frame, titleY: frame.titleY - lift, dateY: frame.dateY - lift };
}
export function CrystalTrophyReveal({ autoPlay = false, onClose, onShare }: CrystalTrophyRevealProps): React.JSX.Element {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const frame = useMemo(() => screenFrame(width, height, insets.top, insets.bottom), [width, height, insets.top, insets.bottom]);
  const reduced = useReducedMotion();
  const fonts = useTrophyFonts(frame);
  const [backReady, setBackReady] = useState(false);
  const [trophyReady, setTrophyReady] = useState(false);
  const [frontReady, setFrontReady] = useState(false);
  const [laidOut, setLaidOut] = useState(false);
  const ready = backReady && trophyReady && frontReady && fonts !== null && laidOut;
  const motion = useTrophyMotion({ frame, ready, autoPlay, reduced, onClose, onShare });
  const { state, visible, canvasSize, values, phase, gesture, accessibility } = motion;
  const cameraTransform = useDerivedValue(() => {
    const s = state.get();
    return [{ translateY: s.cameraY }, { scale: s.cameraScale }];
  });
  const veil = useDerivedValue(() => state.get().veil);
  const coverStyle = useAnimatedStyle(() => ({ opacity: 1 - visible.get() }));
  const bodyStyle = useAnimatedStyle(() => ({ opacity: visible.get() * (1 - state.get().veil) }));
  const hit = trophyHitBox(frame);
  const interactive = ready && phase === 'open';
  const curtain = ready && phase === 'closed';
  return <GestureHandlerRootView style={styles.root}>
    <GestureDetector gesture={gesture}>
      <View collapsable={false} style={styles.root} onLayout={() => setLaidOut(true)} accessibilityViewIsModal>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" onSize={canvasSize} accessible={false}>
          <Group origin={{ x: frame.originX, y: frame.originY - 1.45 * frame.unit }} transform={cameraTransform}>
            <StageBack frame={frame} values={values} still={reduced} onReady={setBackReady} />
            <Trophy frame={frame} values={values} still={reduced} onReady={setTrophyReady} />
            <StageFront frame={frame} values={values} still={reduced} onReady={setFrontReady} />
          </Group>
          {fonts && <TrophyUi frame={frame} fonts={fonts} state={state} />}
          {fonts && <TrophyControls frame={frame} fonts={fonts} state={state} />}
          <Fill color="#000000" opacity={veil} />
        </Canvas>
        <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFill, bodyStyle]}>
          <TrophyCopy frame={frame} state={state} accessible={interactive} />
          <View accessible={interactive} accessibilityRole="adjustable" accessibilityLabel={COPY.trophyLabel}
            accessibilityElementsHidden={!interactive} importantForAccessibility={interactive ? 'auto' : 'no-hide-descendants'}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]}
            onAccessibilityAction={(event) => {
              const action = event.nativeEvent.actionName;
              accessibility('trophy', action === 'increment' ? Math.PI / 12 : action === 'decrement' ? -Math.PI / 12 : undefined);
            }} onAccessibilityTap={() => accessibility('trophy')}
            style={{ position: 'absolute', left: hit.x, top: hit.y, width: hit.width, height: hit.height }} />
          {curtain && <View accessible accessibilityRole="button" accessibilityLabel={COPY.curtainLabel}
            onAccessibilityTap={() => accessibility('curtain')} accessibilityActions={[{ name: 'activate' }]}
            onAccessibilityAction={() => accessibility('curtain')} style={StyleSheet.absoluteFill} />}
          <TrophyControlTargets frame={frame} enabled={interactive} activate={accessibility} />
        </Animated.View>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.black, coverStyle]} />
      </View>
    </GestureDetector>
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#000000' }, black: { backgroundColor: '#000000' } });
