/**
 * The card: midnight anodised aluminium drawn in Skia (scenes.tsx), with its printed face on top (logotype,
 * contactless mark, the balance odometer, the last four digits). The parent moves it; the material reads the move for its light.
 */
import { Canvas } from '@shopify/react-native-skia';
import { PixelRatio, StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, {
  LayoutAnimationConfig, ReduceMotion, useDerivedValue, useReducedMotion, withDelay, withTiming, type EntryExitAnimationFunction,
  type SharedValue,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { PULL, ROLL, SEND, easeOut } from './motion';
import { CardMaterial, cardShadow, cardUniforms } from './scenes';
import { CARD_LAST4, COLORS, GLYPH, LAYOUT, TYPE, formatMoney } from './theme';

/**
 * One digit of the balance. When it changes, the old figure rises out and fades, then the new one rises in after it,
 * like a counter ticking over: no figure rolls through the ones between, and none is cut by a clip. One direction
 * for both a payment and its undo, so a figure leaving can never disagree with the one arriving. Under Reduce Motion
 * the figures crossfade in place on the same timings.
 */
function Digit({ value, index, style, line, rollMs, reduced }: {
  value: number; index: number; style: TextStyle; line: number; rollMs: number; reduced: boolean;
}) {
  const delay = index * ROLL.stagger;
  const travel = reduced ? 0 : ROLL.travel * line;
  const never = ReduceMotion.Never;
  // The old figure is gone (fast, eased out) before the new one is half in, so the two never read as one glyph.
  const exit: EntryExitAnimationFunction = () => {
    'worklet';
    const t = { duration: rollMs * 0.4, easing: easeOut, reduceMotion: never };
    return {
      initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
      animations: { opacity: withDelay(delay, withTiming(0, t), never), transform: [{ translateY: withDelay(delay, withTiming(-travel, t), never) }] },
    };
  };
  const enter: EntryExitAnimationFunction = () => {
    'worklet';
    const t = { duration: rollMs * 0.7, easing: easeOut, reduceMotion: never };
    const d = delay + rollMs * 0.25;
    return {
      initialValues: { opacity: 0, transform: [{ translateY: travel }] },
      animations: { opacity: withDelay(d, withTiming(1, t), never), transform: [{ translateY: withDelay(d, withTiming(0, t), never) }] },
    };
  };
  return (
    <View style={{ height: line }}>
      {/* Sizes the column to one tabular digit, exactly as the static number sets it. */}
      <Text style={[style, styles.sizer]}>0</Text>
      <Animated.Text key={value} style={[style, styles.glyph]} entering={enter} exiting={exit}>
        {value}
      </Animated.Text>
    </View>
  );
}

/** A number whose changed digits tick over: each digit is its own column, the other characters are set in place. */
export function Odometer({ text, style, rollMs = SEND.rollMs }: { text: string; style: TextStyle; rollMs?: number }) {
  const reduced = useReducedMotion();
  const line = style.lineHeight ?? 36;
  let digit = 0;
  // Keyed by place from the right, so a column keeps its identity if the number gains or loses a digit.
  return (
    // The figures on screen when the card appears are simply there; only later changes move.
    <LayoutAnimationConfig skipEntering>
      <View style={styles.odometer}>
        {text.split('').map((ch, i) => (/\d/.test(ch)
          ? <Digit key={`d${text.length - i}`} value={Number(ch)} index={digit++} style={style} line={line} rollMs={rollMs} reduced={reduced} />
          : <Text key={`c${text.length - i}`} style={[style, { height: line }]}>{ch}</Text>))}
      </View>
    </LayoutAnimationConfig>
  );
}

type Props = {
  width: number;
  height: number;
  cents: number;
  /** The card's pull offset (pt) and press (0..1), for its light and shadow. */
  shift: SharedValue<number>;
  press: SharedValue<number>;
};

/**
 * The card's own logotype: "cove" drawn as a monoline in a 13 pt x-height, geometric like the etched contours,
 * so the brand is a mark and not a word set in the system font.
 */
const LOGO = [
  'M11.24 2.76 A6 6 0 1 0 11.24 11.24',
  'M28 7 A6 6 0 1 0 16 7 A6 6 0 1 0 28 7',
  'M32.6 1.2 L38.4 12.8 L44.2 1.2',
  'M48 7 L60 7 A6 6 0 1 0 57.9 11.6',
].join(' ');

export function WalletCard({ width, height, cents, shift, press }: Props) {
  const pad = LAYOUT.shadowPad;
  const aa = 1 / PixelRatio.get();
  const uniforms = useDerivedValue(() => cardUniforms(width, height, shift.get(), press.get(), aa));
  const lift = useDerivedValue(() => Math.min(1, Math.max(0, shift.get() / PULL.tether)));
  const contactY = useDerivedValue(() => cardShadow(lift.get()).contactY);
  const contactBlur = useDerivedValue(() => cardShadow(lift.get()).contactBlur);
  const contactOpacity = useDerivedValue(() => cardShadow(lift.get()).contactOpacity);
  const ambientY = useDerivedValue(() => cardShadow(lift.get()).ambientY);
  const ambientBlur = useDerivedValue(() => cardShadow(lift.get()).ambientBlur);
  const ambientOpacity = useDerivedValue(() => cardShadow(lift.get()).ambientOpacity);
  // The bottom line: the balance's baseline sits 26 pt above the card's edge, the last four digits share it.
  const amountTop = height - 26 - 29;
  return (
    <View style={{ width, height }}>
      <Canvas style={[styles.canvas, { left: -pad, top: -pad, width: width + pad * 2, height: height + pad * 2 }]}
        pointerEvents="none">
        <CardMaterial width={width} height={height} pad={pad} pixelRatio={PixelRatio.get()} uniforms={uniforms}
          contactY={contactY} contactBlur={contactBlur} contactOpacity={contactOpacity}
          ambientY={ambientY} ambientBlur={ambientBlur} ambientOpacity={ambientOpacity} />
      </Canvas>
      <View style={styles.logo}>
        <Svg width={66} height={17} viewBox="-1.5 -1.5 66 17">
          <Path d={LOGO} stroke={COLORS.onCard} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </Svg>
      </View>
      <View style={styles.contactless}>
        <Svg width={24} height={24} viewBox="0 0 24 24">
          <Path d={GLYPH.contactless} stroke={COLORS.onCard} strokeOpacity={0.72} strokeWidth={1.8} strokeLinecap="round" fill="none" />
        </Svg>
      </View>
      <Text style={[styles.label, TYPE.cardLabel, { top: amountTop - 18 }]}>Balance</Text>
      <View style={[styles.balance, { top: amountTop }]}>
        <Odometer text={formatMoney(cents)} style={styles.balanceText} />
      </View>
      <Text style={[styles.pan, TYPE.pan, { top: amountTop + 29 - 15 }]}>{`•••• ${CARD_LAST4}`}</Text>
    </View>
  );
}

const P = LAYOUT.cardPad;
const styles = StyleSheet.create({
  canvas: { position: 'absolute' },
  logo: { position: 'absolute', left: P - 1, top: P },
  contactless: { position: 'absolute', right: P - 4, top: P - 4, width: 24, height: 24 },
  label: { position: 'absolute', left: P, color: COLORS.onCardMute },
  balance: { position: 'absolute', left: P },
  balanceText: { ...TYPE.balance, color: COLORS.onCard, fontVariant: ['tabular-nums'] },
  pan: { position: 'absolute', right: P, color: COLORS.onCardMute, fontVariant: ['tabular-nums'] },
  odometer: { flexDirection: 'row' },
  sizer: { color: 'transparent' },
  glyph: { position: 'absolute', left: 0, right: 0, top: 0, textAlign: 'center' },
});
