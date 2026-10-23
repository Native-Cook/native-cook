/** A video editor whose preview you hold and drag to resize: compact, standard, full screen. */
import { useEffect } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Chrome } from './Chrome';
import { CoachMark, gotItCentre } from './CoachMark';
import { previewFrame } from './layout';
import { FRAMES, SIZES } from './motion';
import { Preview } from './Preview';
import { COLOR, LAYOUT } from './theme';
import { Timeline } from './Timeline';
import { useResize } from './use-resize';

const NAMES = ['compact', 'standard', 'full screen'];

export type HoldDragResizeProps = { autoPlay?: boolean };

export function HoldDragResize({ autoPlay = false }: HoldDragResizeProps) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const previewCentreY = FRAMES[0].top + SIZES[0] / 2;
  const {
    gesture, touchStart, touchEnd, stepSize, gotItPress, togglePlay, startCoachLoop,
    coachOn, clip, second, playing, sizeIndex, near, values,
  } = useResize({ autoPlay, reduceMotion, width, previewCentreY, gotIt: gotItCentre() });
  const { shown, held, clock, coach: coachFade, coachP, ghostX, ghostY, ghostO, ghostPress, press, lag } = values;

  useEffect(() => { startCoachLoop(); }, [startCoachLoop]);

  // The touch target follows the preview's frame.
  const target = useAnimatedStyle(() => {
    const p = previewFrame(shown.get(), width);
    return { top: p.top, left: p.left, width: p.width, height: p.height };
  });
  const ghost = useAnimatedStyle(() => ({
    opacity: ghostO.get(),
    transform: [
      { translateX: ghostX.get() - LAYOUT.finger / 2 },
      { translateY: ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.18 * ghostPress.get() },
    ],
  }));

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <Timeline shown={shown} lag={lag} clock={clock} width={width} topInset={insets.top} playing={playing} second={second} onTogglePlay={togglePlay} />
        <Preview shown={shown} held={held} press={press} clock={clock} width={width} topInset={insets.top} clip={clip} near={near} reduceMotion={reduceMotion} />
        <GestureDetector gesture={gesture}>
          <Animated.View
            style={[styles.target, target]}
            collapsable={false}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`Video preview, ${NAMES[sizeIndex]}`}
            accessibilityHint="Hold and drag up or down to resize. Swipe up or down with one finger to change size."
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(e) => stepSize(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
          />
        </GestureDetector>
        {/* Above the touch target, so the controls that float over the video at full screen can be pressed. */}
        <Chrome
          shown={shown}
          topInset={insets.top}
          bottomInset={insets.bottom}
          full={sizeIndex === SIZES.length - 1}
          onCollapse={() => stepSize(-SIZES.length)}
          onExitFull={() => stepSize(-1)}
        />
        <CoachMark coach={coachFade} coachP={coachP} on={coachOn} onGotIt={gotItPress} />
        {autoPlay && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.background },
  target: { position: 'absolute' },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
