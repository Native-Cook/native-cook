/** The source's onboarding: the whole screen dimmed, an outlined hand on the preview that shows the gesture, the line, Got it. */
import { StyleSheet, Text, View } from 'react-native';
import { PressScale } from './press-scale';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { DEMO } from './motion';
import { COACH, COLOR, TYPE } from './theme';

type Props = { coach: SharedValue<number>; coachP: SharedValue<number>; on: boolean; onGotIt: () => void };

// An outlined pointing hand, index finger up, drawn in a 44 × 70 box like the source's glyph.
const HAND = 'M14 40V9a5 5 0 0 1 10 0v22a4.5 4.5 0 0 1 9 0v3a4.5 4.5 0 0 1 9 0v4a4 4 0 0 1 0.5 0v14c0 10-7 17-17 17h-4c-8 0-12-4-16-10L3 47c-2-3 0-7 4-6 3 0 5 2 7 5';

/** Where the demo's finger taps Got it. */
export function gotItCentre() {
  return { x: COACH.button.x + COACH.button.width / 2, y: COACH.button.y + COACH.button.height / 2 };
}

export function CoachMark({ coach, coachP, on, onGotIt }: Props) {
  const fade = useAnimatedStyle(() => ({ opacity: coach.get() }));
  const hand = useAnimatedStyle(() => ({ transform: [{ translateY: DEMO.coachHand.travel * coachP.get() }] }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, fade]} pointerEvents={on ? 'auto' : 'none'} accessibilityViewIsModal={on}>
      <Animated.View style={[styles.hand, hand]}>
        <View style={styles.touch} />
        <Svg width={COACH.hand.width} height={COACH.hand.height} viewBox="0 0 44 70">
          <Path d={HAND} stroke={COLOR.ink} strokeWidth={3.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </Animated.View>
      <Text style={styles.text}>{'Hold and drag to change\npreview size.'}</Text>
      <PressScale style={styles.button} onPress={onGotIt} accessibilityRole="button" accessibilityLabel="Got it">
        <Text style={styles.buttonText}>Got it</Text>
      </PressScale>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  scrim: { backgroundColor: COLOR.scrim },
  hand: { position: 'absolute', left: COACH.hand.x, top: COACH.hand.y, width: COACH.hand.width, height: COACH.hand.height },
  // The source's touch mark sits on the fingertip.
  touch: {
    position: 'absolute', left: COACH.touch.x - COACH.touch.size / 2, top: COACH.touch.y - COACH.touch.size / 2,
    width: COACH.touch.size, height: COACH.touch.size, borderRadius: COACH.touch.size / 2, backgroundColor: COLOR.touch,
  },
  text: { ...TYPE.coach, color: COLOR.ink, textAlign: 'center', position: 'absolute', left: 0, right: 0, top: COACH.textTop },
  button: {
    position: 'absolute', left: COACH.button.x, top: COACH.button.y, width: COACH.button.width, height: COACH.button.height,
    borderRadius: COACH.button.height / 2, backgroundColor: COLOR.ink, alignItems: 'center', justifyContent: 'center',
  },
  buttonText: { ...TYPE.coachButton, color: COLOR.background },
});
