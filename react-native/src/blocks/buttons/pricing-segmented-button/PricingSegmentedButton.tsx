import { BlurMask, Canvas, Path, RoundedRect, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

export type PricingValue = 'free' | 'monthly' | 'annual';

type Props = {
  /** Controlled selection. Leave it out to let the control keep its own. */
  value?: PricingValue;
  defaultValue?: PricingValue;
  onValueChange?: (value: PricingValue) => void;
  freeLabel?: string;
  premiumLabel?: string;
  monthlyLabel?: string;
  annualLabel?: string;
  /** Walks Free → Monthly → Annual → Monthly → Free by itself, pausing on each. */
  autoPlay?: boolean;
};

const WIDTH = 336;
const HEIGHT = 48;
// Gap between the track and the black pill, and between the black pill and the white one.
const INSET = 3;
const INNER_INSET = 2;
const PILL_W = WIDTH / 2 - INSET * 2;
const PILL_H = HEIGHT - INSET * 2;
// How far the black pill travels between Free and Premium.
const SHIFT = WIDTH / 2;
const KNOB_W = (PILL_W - INNER_INSET * 2) / 2;
const KNOB_H = PILL_H - INNER_INSET * 2;
const KNOB_X = SHIFT + INSET + INNER_INSET;
// The canvas overflows the control by this much on every side to fit the drop shadow.
const BLEED = 48;
const PLAN_MS = 480;
const BILLING_MS = 380;
const AUTO_PAUSE_MS = 1500;

const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}
// A pill's two ends follow the same progress on different curves, so whichever end faces the
// direction of travel moves first and the pill stretches on the way, in both directions.
const STRETCH = 1.4;
function lead(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, STRETCH);
}
function lag(x: number) {
  'worklet';
  return Math.pow(x, STRETCH);
}

function capsule(left: number, right: number, height: number, scale: number) {
  'worklet';
  const w = (right - left) * scale;
  const h = height * scale;
  const x = BLEED + (left + right) / 2 - w / 2;
  const path = Skia.Path.Make();
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(x, BLEED + (HEIGHT - h) / 2, w, h), h / 2, h / 2));
  return path;
}

function Track({ plan, billing }: { plan: SharedValue<number>; billing: SharedValue<number> }) {
  const pill = useDerivedValue(() =>
    capsule(INSET + SHIFT * lag(plan.get()), INSET + PILL_W + SHIFT * lead(plan.get()), PILL_H, 1),
  );
  // The white pill grows into place as the black one arrives.
  const open = useDerivedValue(() => seg(plan.get(), 0.4, 1));
  const knob = useDerivedValue(() =>
    capsule(
      KNOB_X + KNOB_W * lag(billing.get()),
      KNOB_X + KNOB_W + KNOB_W * lead(billing.get()),
      KNOB_H,
      0.6 + 0.4 * open.get(),
    ),
  );
  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={BLEED + 16} y={BLEED + 18} width={WIDTH - 32} height={HEIGHT - 8} r={(HEIGHT - 8) / 2} color="rgba(0,0,0,0.17)">
        <BlurMask blur={13} style="normal" />
      </RoundedRect>
      <RoundedRect x={BLEED - 0.5} y={BLEED - 0.5} width={WIDTH + 1} height={HEIGHT + 1} r={HEIGHT / 2 + 0.5} color="#e6e6e6" />
      <RoundedRect x={BLEED} y={BLEED} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#fafafa" />
      <Path path={pill} color="#050505" />
      <Path path={knob} color="#ffffff" opacity={open} />
    </Canvas>
  );
}

/**
 * Pricing switch. "Free | Premium" where choosing Premium slides the black pill across and opens it
 * into a nested "Monthly | Annual" switch with its own white pill; choosing Free closes it again.
 */
export function PricingSegmentedButton({
  value,
  defaultValue = 'free',
  onValueChange,
  freeLabel = 'Free',
  premiumLabel = 'Premium',
  monthlyLabel = 'Monthly',
  annualLabel = 'Annual',
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [own, setOwn] = useState<PricingValue>(defaultValue);
  const selected = value ?? own;
  const premium = selected !== 'free';
  // Premium reopens on whichever billing period was chosen last.
  const [lastBilling, setLastBilling] = useState<'monthly' | 'annual'>(defaultValue === 'annual' ? 'annual' : 'monthly');
  const billingChoice = selected === 'free' ? lastBilling : selected;
  const [pressed, setPressed] = useState(false);
  // Which way autoPlay is walking through the options.
  const forward = useRef(true);

  const plan = useSharedValue(premium ? 1 : 0);
  const billing = useSharedValue(billingChoice === 'annual' ? 1 : 0);

  // Under reduced motion Reanimated lands these on their targets at once, which is what we want.
  useEffect(() => {
    plan.set(withTiming(premium ? 1 : 0, { duration: PLAN_MS, easing: EASE_IN_OUT }));
  }, [premium, plan]);
  useEffect(() => {
    billing.set(withTiming(billingChoice === 'annual' ? 1 : 0, { duration: BILLING_MS, easing: EASE_IN_OUT }));
  }, [billingChoice, billing]);

  const select = (next: PricingValue) => {
    if (next === selected) return;
    if (next !== 'free') setLastBilling(next);
    setOwn(next);
    onValueChange?.(next);
  };

  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed) return;
    const timer = setTimeout(() => {
      if (selected === 'free') forward.current = true;
      if (selected === 'annual') forward.current = false;
      const next: PricingValue = selected === 'monthly' ? (forward.current ? 'annual' : 'free') : 'monthly';
      if (next !== 'free') setLastBilling(next);
      setOwn(next);
      onValueChange?.(next);
    }, AUTO_PAUSE_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, pressed, selected, onValueChange]);

  const freeStyle = useAnimatedStyle(() => ({
    color: interpolateColor(plan.get(), [0, 1], ['#ffffff', '#b3b3b3']),
  }));
  // "Premium" lifts away while the two billing labels grow out of its small print.
  const premiumStyle = useAnimatedStyle(() => {
    const gone = seg(plan.get(), 0, 0.5);
    return { opacity: 1 - gone, transform: [{ translateY: -6 * gone }] };
  });
  const monthlyStyle = useAnimatedStyle(() => {
    const open = seg(plan.get(), 0.4, 1);
    return {
      opacity: open,
      color: interpolateColor(billing.get(), [0, 1], ['#000000', '#8a8a8a']),
      transform: [{ translateY: 9 * (1 - open) }, { scale: 0.6 + 0.4 * open }],
    };
  });
  const annualStyle = useAnimatedStyle(() => {
    const open = seg(plan.get(), 0.4, 1);
    return {
      opacity: open,
      color: interpolateColor(billing.get(), [0, 1], ['#8a8a8a', '#000000']),
      transform: [{ translateY: 9 * (1 - open) }, { scale: 0.6 + 0.4 * open }],
    };
  });

  // The three hit areas stay mounted whatever is selected: swapping a Pressable out from under a
  // finger loses its press-out. While Free is selected the two right-hand quarters both mean
  // "Premium", and only the first is announced.
  const segment = (key: string, label: string, isSelected: boolean, onPress: () => void, flex: number, hidden = false) => (
    <Pressable
      key={key}
      style={{ flex }}
      accessible={!hidden}
      importantForAccessibility={hidden ? 'no' : 'auto'}
      accessibilityRole="radio"
      accessibilityLabel={label}
      accessibilityState={{ selected: isSelected }}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
    />
  );

  return (
    <Animated.View
      accessibilityRole="radiogroup"
      style={[
        styles.control,
        {
          transform: [{ scale: pressed ? 0.97 : 1 }],
          transitionProperty: 'transform',
          transitionDuration: 130,
          transitionTimingFunction: CSS_EASE_OUT,
        },
      ]}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Track plan={plan} billing={billing} />
      </View>
      <View pointerEvents="none" style={[styles.slot, { left: 0, width: SHIFT }]}>
        <Animated.Text style={[styles.label, freeStyle]} numberOfLines={1}>
          {freeLabel}
        </Animated.Text>
      </View>
      <Animated.View pointerEvents="none" style={[styles.slot, { left: SHIFT, width: SHIFT }, premiumStyle]}>
        <Text style={[styles.label, styles.premium]} numberOfLines={1}>
          {premiumLabel}
        </Text>
        <Text style={styles.small} numberOfLines={1}>
          {monthlyLabel} · {annualLabel}
        </Text>
      </Animated.View>
      <View pointerEvents="none" style={[styles.slot, { left: KNOB_X, width: KNOB_W }]}>
        <Animated.Text style={[styles.label, monthlyStyle]} numberOfLines={1}>
          {monthlyLabel}
        </Animated.Text>
      </View>
      <View pointerEvents="none" style={[styles.slot, { left: KNOB_X + KNOB_W, width: KNOB_W }]}>
        <Animated.Text style={[styles.label, annualStyle]} numberOfLines={1}>
          {annualLabel}
        </Animated.Text>
      </View>
      <View style={styles.segments}>
        {segment('free', freeLabel, !premium, () => select('free'), 2)}
        {segment('monthly', premium ? monthlyLabel : premiumLabel, selected === 'monthly', () => select(premium ? 'monthly' : lastBilling), 1)}
        {segment('annual', annualLabel, selected === 'annual', () => select(premium ? 'annual' : lastBilling), 1, !premium)}
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  control: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  slot: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segments: {
    ...StyleSheet.absoluteFill,
    flexDirection: 'row',
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
  premium: {
    color: '#b3b3b3',
  },
  small: {
    marginTop: -1,
    fontSize: 8.5,
    fontWeight: '500',
    color: '#cdcdcd',
  },
});
