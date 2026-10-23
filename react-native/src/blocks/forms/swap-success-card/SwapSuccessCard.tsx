import { Ionicons } from '@expo/vector-icons';
import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export type Token = {
  symbol: string;
  /** What the wallet holds of it. */
  balance: number;
  /** Its round mark: the disc and the dot in it. */
  disc: string;
  dot: string;
};

export type Exchange = { from: Token; to: Token; amount: number; received: number };

type Props = {
  /** Types an amount, switches the two tokens, swaps, and starts over every LOOP seconds. */
  autoPlay?: boolean;
  /** The two tokens; the first one starts in "From". */
  tokens?: [Token, Token];
  /** How many of the second token one of the first buys. */
  rate?: number;
  /** What autoPlay types into "From". */
  demoAmount?: string;
  title?: string;
  /** The three ways to trade; the first is selected. */
  modes?: string[];
  fromLabel?: string;
  toLabel?: string;
  balanceLabel?: string;
  maxLabel?: string;
  swapLabel?: string;
  successTitle?: string;
  successLine?: string;
  /** Called when Swap is released, as the form empties. */
  onSwap?: (exchange: Exchange) => void;
  /** Called when the success card has left and the empty form is back. */
  onDone?: () => void;
  onPickToken?: (token: Token) => void;
};

/** autoPlay: one exchange every LOOP seconds. The caret's blink divides it. */
export const LOOP = 9.6;
// The amount is tapped at TAP_AT and typed from TYPE_AT, a key every TYPE_EVERY; then the switch
// button and Swap are pressed, and the success card leaves at LEAVE_AT.
const TAP_AT = 0.8;
const TYPE_AT = 1.1;
const TYPE_EVERY = 0.15;
const SWITCH_AT = 2.75;
const PRESS_AT = 3.9;
const PRESS_FOR = 0.15;
const LEAVE_AT = 7.3;
// After a touch autoPlay waits this long, and for the card to be idle, before it starts over.
const USER_HOLD = 3;
const BLINK = LOOP / 8;

// The clock of an exchange, in seconds since Swap was released.
const CONTENT_OUT = 0.28;
// The frame contracts round the emptied panel.
const CONTRACT_AT = 0.24;
const CONTRACT_FOR = 0.34;
// A dot appears and blooms into the ringed check (a spring: angular frequency in rad/s, damping ratio).
const DOT_AT = 0.5;
const DOT_IN = 0.08;
const BLOOM_AT = 0.62;
const BLOOM_SPRING = { omega: 15, zeta: 0.55 };
const DOT_SCALE = 0.17;
const CHECK_AT = 0.74;
const CHECK_FOR = 0.3;
// The title rises into place while it sharpens, then the two small lines come up one after the other.
const TITLE_AT = 0.7;
const TITLE_FOR = 0.48;
const TITLE_RISE = 22;
const TITLE_BLUR = 12;
const LINES_AT = 1.08;
const LINE_GAP = 0.1;
const LINE_FOR = 0.3;
const LINE_RISE = 4;
const SWAP_END = 1.6;

// The clock of a turn: the success card shrinks a little and fades away, the screen stays empty
// for a moment, and the form comes back out of a blur, a little small at first.
const OUT_FOR = 0.4;
const EMPTY_FOR = 0.2;
const IN_FOR = 0.7;
const TURN_HALF = OUT_FOR + EMPTY_FOR / 2;
const TURN_END = OUT_FOR + EMPTY_FOR + IN_FOR;
const OUT_SCALE = 0.96;
const IN_SCALE = 0.94;
const IN_BLUR = 16;
// The switch: the two tokens trade places on a spring while the button turns half a turn.
const FLIP_SPRING = { damping: 17, stiffness: 190, mass: 1 };
// Half way, the row in front is this much larger, the one behind this much smaller and dimmer.
const CROSS = { grow: 0.05, shrink: 0.1, dim: 0.7 };

const TOKENS: [Token, Token] = [
  { symbol: 'VLA', balance: 1456, disc: '#FFFFFF', dot: '#1A1A1A' },
  { symbol: 'ORO', balance: 12678, disc: '#F7931A', dot: '#A35B00' },
];
const RATE = 11908 / 456;
const MODES = ['Swap', 'Limit', 'Bridge'];

// The screen: a teal light in the middle, darker towards the corners.
const SKY = ['#12917C', '#0E6E66', '#0A4A47'];
// The card's frame, top to bottom: lit green from above, black below; a dark pool in its middle
// leaves the light on the top band and the side edges.
const FRAME = ['#16A870', '#0F8A5E', '#0B4A36', '#0A1210', '#0B0B0B'];
const FRAME_AT = [0, 0.16, 0.44, 0.66, 1];
const POOL = { x: 0.52, y: 0.5, rx: 0.48, ry: 0.42, colors: ['rgba(9,11,10,0.9)', 'rgba(9,11,10,0.78)', 'rgba(9,11,10,0)'], at: [0, 0.6, 1] };
const PANEL = '#0D0D0D';
const WELL = '#151615';
const HAIRLINE = 'rgba(255,255,255,0.08)';
const INK = '#FFFFFF';
const MUTE = '#8E9794';
const FAINT = '#5F6B67';
const MINT = '#1EE39A';
const MINT_DEEP = '#0FB67A';
const MINT_LIGHT = '#7CFBDD';
const BUTTON_INK = '#06281C';

// The card, in points, laid out for a 402 point wide screen.
const CARD = { width: 342, height: 340, radius: 10, margin: 30 };
const HALO = 10;
// Room round the card for the halo and for the blur it comes out of.
const SPILL = 44;
const PANEL_BOX = { x: 12, y: 60, height: 217, radius: 8 };
// The "To" half is a lighter well set into the panel.
const WELL_BOX = { inset: 6, y: 105, height: 106, radius: 8 };
const BUTTON = { x: 17, y: 287, height: 37, radius: 5, squeeze: 4 };
// The contracted card: its height, and the panel in it.
const DONE = { height: 232, panelY: 20, panelHeight: 188 };
const DONE_TOP = (CARD.height - DONE.height) / 2;
// Inside the panel: the amounts, the chips, and the lines under them, for the two slots.
const SLOT = 101;
const ROW_TOP = 28;
const ROW_HEIGHT = 70;
const TEXT_LEFT = 18;
const SWITCH = 28;
const SWITCH_Y = 97;
// The success card's content, from the card's top edge.
const RING = { y: DONE_TOP + DONE.panelY + 48, radius: 29, stroke: 5, disc: 23.5 };
const RING_CANVAS = 96;
const SUCCESS_TITLE_Y = DONE_TOP + DONE.panelY + 92;
const CHECK = 'M-9 0.5L-3 6.5L9.5 -6';
// With the keyboard up, the card's bottom edge stays this far above it.
const KEYBOARD_GAP = 14;
const IOS = Platform.OS === 'ios';
// Android blurs the title itself (Android 12+); iOS lays a blur view over the panel.
const CAN_FILTER = !IOS && Number(Platform.Version) >= 31;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** What is left of a spring's way `t` seconds after it was let go at rest: 1, through 0, settling at 0. */
function springLeft(t: number, omega: number, zeta: number) {
  'worklet';
  if (t <= 0) return 1;
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** "11908.5" → "11,908.5": thousands separators on the whole part, the rest as typed. */
function grouped(draft: string) {
  const [whole, fraction] = draft.split('.');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction === undefined ? '' : `.${fraction}`);
}

/** What is typed, kept to digits with one point, seven digits before it and two after. */
function cleaned(text: string) {
  const [whole, ...rest] = text
    .replace(/,/g, '.')
    .replace(/[^\d.]/g, '')
    .split('.');
  const digits = whole.replace(/^0+(?=\d)/, '').slice(0, 7);
  return rest.length === 0 ? digits : `${digits || '0'}.${rest.join('').slice(0, 2)}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Clocks = {
  /** Seconds since Swap was released; negative while the form is in use. */
  swap: SharedValue<number>;
  /** Seconds into a turn (the success card leaves, the form returns); 0 otherwise. */
  turn: SharedValue<number>;
};

/** The exchange clock as the card should show it: once a turn is half over, the card is a form again. */
function shownSwap(swap: number, turn: number) {
  'worklet';
  return turn >= TURN_HALF ? -1 : swap;
}

type ChromeProps = Clocks & { width: number; press: SharedValue<number>; still: boolean };

/** The card without its words: halo, lit frame, black panel, the "To" well and the Swap bar. */
function Chrome({ width, swap, turn, press, still }: ChromeProps) {
  const panelWidth = width - PANEL_BOX.x * 2;
  const contracted = useDerivedValue(() => smooth(shownSwap(swap.get(), turn.get()), CONTRACT_AT, CONTRACT_AT + CONTRACT_FOR));
  const emptied = useDerivedValue(() => 1 - smooth(shownSwap(swap.get(), turn.get()), 0, CONTENT_OUT));
  const top = useDerivedValue(() => SPILL + DONE_TOP * contracted.get());
  const height = useDerivedValue(() => CARD.height + (DONE.height - CARD.height) * contracted.get());
  const haloTop = useDerivedValue(() => top.get() - HALO);
  const haloHeight = useDerivedValue(() => height.get() + HALO * 2);
  const lightFrom = useDerivedValue(() => vec(0, top.get()));
  const lightTo = useDerivedValue(() => vec(0, top.get() + height.get()));
  // The dark pool: an ellipse in the frame's proportions, a little right of its middle.
  const pool = useDerivedValue(() => [
    { translateX: SPILL + width * POOL.x },
    { translateY: top.get() + height.get() * POOL.y },
    { scaleX: width * POOL.rx },
    { scaleY: height.get() * POOL.ry },
  ]);
  const panelTop = useDerivedValue(() => top.get() + PANEL_BOX.y + (DONE.panelY - PANEL_BOX.y) * contracted.get());
  const panelHeight = useDerivedValue(() => PANEL_BOX.height + (DONE.panelHeight - PANEL_BOX.height) * contracted.get());
  // The bar narrows by a few points at each end and darkens while it is pressed.
  const barX = useDerivedValue(() => SPILL + BUTTON.x + BUTTON.squeeze * press.get());
  const barWidth = useDerivedValue(() => width - BUTTON.x * 2 - BUTTON.squeeze * 2 * press.get());
  const barShade = useDerivedValue(() => 0.3 * press.get());
  // The form comes back out of a blur: every shape is drawn through the same soft mask.
  const blur = useDerivedValue(() => {
    const t = turn.get();
    return still || t < TURN_HALF ? 0 : IN_BLUR * (1 - smooth(t, OUT_FOR + EMPTY_FOR, TURN_END - 0.15));
  });
  const soft = <BlurMask blur={blur} style="normal" />;
  const barY = SPILL + BUTTON.y;
  const well = { x: SPILL + PANEL_BOX.x + WELL_BOX.inset, y: SPILL + PANEL_BOX.y + WELL_BOX.y, width: panelWidth - WELL_BOX.inset * 2 };

  return (
    <Canvas pointerEvents="none" style={[styles.chrome, { width: width + SPILL * 2, height: CARD.height + SPILL * 2 }]}>
      <RoundedRect x={SPILL - HALO} y={haloTop} width={width + HALO * 2} height={haloHeight} r={CARD.radius + 6} color="rgba(255,255,255,0.045)">
        {soft}
      </RoundedRect>
      <RoundedRect
        x={SPILL - HALO + 0.5}
        y={haloTop}
        width={width + HALO * 2 - 1}
        height={haloHeight}
        r={CARD.radius + 6}
        style="stroke"
        strokeWidth={1}
        color="rgba(255,255,255,0.07)"
      >
        {soft}
      </RoundedRect>

      <RoundedRect x={SPILL} y={top} width={width} height={height} r={CARD.radius}>
        <LinearGradient start={lightFrom} end={lightTo} colors={FRAME} positions={FRAME_AT} />
        {soft}
      </RoundedRect>
      <Group transform={pool}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} r={1} colors={POOL.colors} positions={POOL.at} />
        </Circle>
      </Group>
      <RoundedRect
        x={SPILL + 0.5}
        y={top}
        width={width - 1}
        height={height}
        r={CARD.radius}
        style="stroke"
        strokeWidth={1}
        color="rgba(255,255,255,0.1)"
      >
        {soft}
      </RoundedRect>

      <RoundedRect x={SPILL + PANEL_BOX.x} y={panelTop} width={panelWidth} height={panelHeight} r={PANEL_BOX.radius} color={PANEL}>
        {soft}
      </RoundedRect>
      <RoundedRect
        x={SPILL + PANEL_BOX.x + 0.5}
        y={panelTop}
        width={panelWidth - 1}
        height={panelHeight}
        r={PANEL_BOX.radius}
        style="stroke"
        strokeWidth={1}
        color={HAIRLINE}
      >
        {soft}
      </RoundedRect>

      <Group opacity={emptied}>
        <RoundedRect x={well.x} y={well.y} width={well.width} height={WELL_BOX.height} r={WELL_BOX.radius} color={WELL}>
          {soft}
        </RoundedRect>
        <RoundedRect
          x={well.x + 0.5}
          y={well.y + 0.5}
          width={well.width - 1}
          height={WELL_BOX.height - 1}
          r={WELL_BOX.radius}
          style="stroke"
          strokeWidth={1}
          color="rgba(255,255,255,0.05)"
        >
          {soft}
        </RoundedRect>
        <RoundedRect x={barX} y={barY} width={barWidth} height={BUTTON.height} r={BUTTON.radius}>
          <LinearGradient start={vec(0, barY)} end={vec(0, barY + BUTTON.height)} colors={[MINT_DEEP, MINT, MINT]} positions={[0, 0.45, 1]} />
          {soft}
        </RoundedRect>
        {/* A lighter glow along the bar's lower edge. */}
        <Group
          clip={{
            rect: { x: SPILL + BUTTON.x + BUTTON.squeeze, y: barY, width: width - (BUTTON.x + BUTTON.squeeze) * 2, height: BUTTON.height },
            rx: BUTTON.radius,
            ry: BUTTON.radius,
          }}
        >
          <Group
            transform={[
              { translateX: SPILL + width / 2 },
              { translateY: barY + BUTTON.height * 1.05 },
              { scaleX: width * 0.36 },
              { scaleY: BUTTON.height * 0.95 },
            ]}
          >
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={[MINT_LIGHT, 'rgba(124,251,221,0.5)', 'rgba(124,251,221,0)']} positions={[0, 0.45, 1]} />
            </Circle>
          </Group>
        </Group>
        <RoundedRect x={barX} y={barY} width={barWidth} height={BUTTON.height} r={BUTTON.radius} color="#000000" opacity={barShade} />
      </Group>
    </Canvas>
  );
}

/** The ringed check of the success card: a dot that blooms into a white ring round a mint disc. */
function Ring({ swap, turn, still }: Clocks & { still: boolean }) {
  const centre = vec(RING_CANVAS / 2, RING_CANVAS / 2);
  const opacity = useDerivedValue(() => smooth(shownSwap(swap.get(), turn.get()), DOT_AT, DOT_AT + DOT_IN));
  const transform = useDerivedValue(() => {
    const s = shownSwap(swap.get(), turn.get());
    const left = still ? 1 - smooth(s, BLOOM_AT, BLOOM_AT + 0.2) : springLeft(s - BLOOM_AT, BLOOM_SPRING.omega, BLOOM_SPRING.zeta);
    return [{ scale: 1 - (1 - DOT_SCALE) * left }];
  });
  const check = useDerivedValue(() => smooth(shownSwap(swap.get(), turn.get()), CHECK_AT, CHECK_AT + CHECK_FOR));
  return (
    <Canvas pointerEvents="none" style={styles.ring}>
      <Group opacity={opacity} transform={transform} origin={centre}>
        <Circle c={centre} r={RING.radius} color={INK} />
        <Circle c={centre} r={RING.disc}>
          <LinearGradient start={vec(0, RING_CANVAS / 2 - RING.disc)} end={vec(0, RING_CANVAS / 2 + RING.disc)} colors={[MINT_DEEP, MINT]} />
        </Circle>
        <Group transform={[{ translateX: RING_CANVAS / 2 }, { translateY: RING_CANVAS / 2 }]}>
          <Path path={CHECK} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={INK} end={check} />
        </Group>
      </Group>
    </Canvas>
  );
}

type RowProps = {
  token: Token;
  /** Where the token's row is: 0 in "From", 1 in "To", in between while the two trade places. */
  slot: SharedValue<number>;
  /** Whether this row passes in front when the two trade places. */
  front: boolean;
  /** Whether it is the "From" row now: the one that is typed in. */
  editable: boolean;
  amount: string;
  caret: boolean;
  time: SharedValue<number>;
  balanceLabel: string;
  inputRef: React.RefObject<TextInput | null>;
  onChangeText: (text: string) => void;
  onFocus: () => void;
  onBlur: () => void;
  onPick: () => void;
};

/** One token's row: its amount, its chip and its balance. It moves between the two slots. */
function Row({ token, slot, front, editable, amount, caret, time, balanceLabel, inputRef, onChangeText, onFocus, onBlur, onPick }: RowProps) {
  // As the two rows cross, one passes in front, a little larger, and the other behind, smaller and dim.
  const style = useAnimatedStyle(() => {
    const at = slot.get();
    const mid = Math.sin(Math.PI * clamp01(at));
    return {
      opacity: front ? 1 : 1 - CROSS.dim * mid,
      transform: [{ translateY: SLOT * at }, { scale: 1 + (front ? CROSS.grow : -CROSS.shrink) * mid }],
    };
  });
  const caretStyle = useAnimatedStyle(() => ({ opacity: time.get() % BLINK < BLINK * 0.55 ? 1 : 0 }));
  return (
    <Animated.View style={[styles.row, style]}>
      <Pressable style={styles.amountSlot} onPress={() => inputRef.current?.focus()} disabled={!editable} accessible={false}>
        <View style={styles.amountLine} pointerEvents="none">
          <Text style={styles.amount} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
            {grouped(amount)}
          </Text>
          {caret && <Animated.View style={[styles.caret, caretStyle]} />}
        </View>
        {/* The real field takes the typing; what it holds is drawn by the line above. */}
        {editable && (
          <TextInput
            ref={inputRef}
            value={amount}
            onChangeText={onChangeText}
            onFocus={onFocus}
            onBlur={onBlur}
            style={styles.input}
            keyboardType="decimal-pad"
            keyboardAppearance="dark"
            caretHidden
            selectionColor="transparent"
            maxLength={11}
            accessibilityLabel={`${token.symbol} amount`}
          />
        )}
      </Pressable>
      <Pressable onPress={onPick} style={styles.chip} accessibilityRole="button" accessibilityLabel={`${token.symbol}, choose a token`}>
        {({ pressed }) => (
          <>
            <View style={[styles.mark, { backgroundColor: token.disc, opacity: pressed ? 0.6 : 1 }]}>
              <View style={[styles.markDot, { backgroundColor: token.dot }]} />
            </View>
            <Text style={[styles.symbol, pressed && styles.pressed]} numberOfLines={1}>
              {token.symbol}
            </Text>
            <Ionicons name="chevron-down" size={11} color={MUTE} />
          </>
        )}
      </Pressable>
      <Text style={styles.balance} numberOfLines={1}>
        {balanceLabel} : {grouped(token.balance.toFixed(2))}
      </Text>
    </Animated.View>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * An exchange card on a teal screen: an amount typed into "From", "To" following at the rate, a
 * round button that makes the two tokens trade places, and Swap. Swap empties the form; the card
 * contracts, a dot blooms into a ringed check and the title sharpens out of a blur. Nothing is
 * exchanged here: `onSwap` is where the real request goes.
 */
export function SwapSuccessCard({
  autoPlay = false,
  tokens = TOKENS,
  rate = RATE,
  demoAmount = '456.00',
  title = 'Exchange',
  modes = MODES,
  fromLabel = 'From',
  toLabel = 'To',
  balanceLabel = 'Balance',
  maxLabel = 'Max',
  swapLabel = 'Swap',
  successTitle = 'Successful Exchange',
  successLine = 'You have successfully exchanged',
  onSwap,
  onDone,
  onPickToken,
}: Props) {
  const reducedMotion = useReducedMotion();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // The "From" amount as typed, digits with at most one point.
  const [draft, setDraft] = useState('');
  // The tokens have traded places: the second one is in "From".
  const [flipped, setFlipped] = useState(false);
  const [mode, setMode] = useState(0);
  const [focused, setFocused] = useState(false);
  // autoPlay is typing: its caret is drawn in "From".
  const [autoCaret, setAutoCaret] = useState(false);
  // 'form' while filling in, 'done' from Swap, 'turning' while the success card leaves and the form returns.
  const [phase, setPhase] = useState<'form' | 'done' | 'turning'>('form');
  // What the success card says.
  const [receipt, setReceipt] = useState('');
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const swap = useSharedValue(-1);
  const turn = useSharedValue(0);
  // 0: the first token is in "From"; 1: the second.
  const flip = useSharedValue(0);
  // Swap held down, 0 to 1.
  const press = useSharedValue(0);
  // The loop's clock, 0 to LOOP again and again: the caret's blink.
  const time = useSharedValue(0);
  // How far the keyboard pushes the card up.
  const lift = useSharedValue(0);
  const firstInput = useRef<TextInput>(null);
  const secondInput = useRef<TextInput>(null);
  // Whether the exchange in progress was started by hand, for the haptic at the check.
  const swappedByHand = useRef(false);

  const width = Math.min(CARD.width, size.width - CARD.margin * 2);
  const panelWidth = width - PANEL_BOX.x * 2;
  const cardTop = (size.height - CARD.height) / 2;
  const from = tokens[flipped ? 1 : 0];
  const to = tokens[flipped ? 0 : 1];
  const price = flipped ? 1 / rate : rate;
  const value = Number(draft) || 0;
  const received = value > 0 ? (value * price).toFixed(2) : '';
  // What each token's row shows: the first token's, then the second's.
  const amounts = flipped ? [received, draft] : [draft, received];

  useEffect(() => {
    time.set(0);
    time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false));
    return () => cancelAnimation(time);
  }, [time]);

  // The card rises until its bottom edge clears the keyboard.
  const cardBottom = useRef(0);
  useEffect(() => {
    cardBottom.current = cardTop + CARD.height + HALO;
  });
  useEffect(() => {
    const show = Keyboard.addListener(IOS ? 'keyboardWillShow' : 'keyboardDidShow', (event) => {
      const room = event.endCoordinates.screenY - KEYBOARD_GAP - cardBottom.current;
      lift.set(withTiming(Math.min(0, room), { duration: event.duration || 250, easing: Easing.out(Easing.cubic) }));
    });
    const hide = Keyboard.addListener(IOS ? 'keyboardWillHide' : 'keyboardDidHide', (event) => {
      lift.set(withTiming(0, { duration: event.duration || 250, easing: Easing.out(Easing.cubic) }));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [lift]);

  useEffect(
    () => () => {
      cancelAnimation(swap);
      cancelAnimation(turn);
    },
    [swap, turn],
  );

  /** The two tokens trade places; the amounts go with them. */
  const switchTokens = (byHand: boolean) => {
    if (phase !== 'form') return;
    if (byHand) tick();
    // The field that was typed in goes away with its row: the keyboard goes down with it.
    Keyboard.dismiss();
    setFocused(false);
    setDraft(received);
    setFlipped(!flipped);
    flip.set(withSpring(flipped ? 0 : 1, FLIP_SPRING));
  };

  const hold = (down: boolean) => press.set(withTiming(down ? 1 : 0, { duration: down ? 80 : 160 }));

  const exchange = (byHand: boolean) => {
    if (phase !== 'form') return;
    if (value <= 0) {
      // Nothing to swap yet: point at the amount.
      if (byHand) (flipped ? secondInput : firstInput).current?.focus();
      return;
    }
    if (byHand) tick();
    swappedByHand.current = byHand;
    Keyboard.dismiss();
    setAutoCaret(false);
    setReceipt(`${grouped(value.toFixed(2))} ${from.symbol} for ${grouped(received)} ${to.symbol}`);
    setPhase('done');
    onSwap?.({ from, to, amount: value, received: Number(received) });
    swap.set(0);
    swap.set(withTiming(SWAP_END, { duration: SWAP_END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  const home = () => {
    setPhase('form');
    onDone?.();
  };

  /** Half way through a turn the card is out of sight: it becomes an empty form again and comes back. */
  const emptied = () => {
    setDraft('');
    setFlipped(false);
    flip.set(0);
    turn.set(
      withTiming(TURN_END, { duration: (TURN_END - TURN_HALF) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        swap.set(-1);
        turn.set(0);
        scheduleOnRN(home);
      }),
    );
  };

  /** The success card leaves and the empty form comes back. */
  const leave = () => {
    if (phase !== 'done') return;
    setPhase('turning');
    turn.set(
      withTiming(TURN_HALF, { duration: TURN_HALF * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(emptied);
      }),
    );
  };

  const celebrate = () => {
    if (swappedByHand.current && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  useAnimatedReaction(
    () => swap.get() >= CHECK_AT + CHECK_FOR,
    (now, old) => {
      if (now && old === false) scheduleOnRN(celebrate);
    },
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ switchTokens, exchange, leave, phase, draft, flipped });
  useEffect(() => {
    latest.current = { switchTokens, exchange, leave, phase, draft, flipped };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      at(TAP_AT, () => setAutoCaret(true));
      demoAmount.split('').forEach((_, index) => at(TYPE_AT + index * TYPE_EVERY, () => setDraft(demoAmount.slice(0, index + 1))));
      at(SWITCH_AT, () => {
        setAutoCaret(false);
        latest.current.switchTokens(false);
      });
      at(PRESS_AT, () => press.set(withTiming(1, { duration: 80 })));
      at(PRESS_AT + PRESS_FOR, () => {
        press.set(withTiming(0, { duration: 160 }));
        latest.current.exchange(false);
      });
      at(LEAVE_AT, () => latest.current.leave());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch the loop starts over from an empty form with the keyboard down.
    const rest = () => {
      const now = latest.current;
      if (now.phase === 'done') now.leave();
      if (now.phase !== 'form') return after(100, rest);
      Keyboard.dismiss();
      if (now.flipped) {
        setDraft('');
        return after(100, () => {
          latest.current.switchTokens(false);
          after(700, rest);
        });
      }
      setDraft('');
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, demoAmount, press]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoCaret(false);
    setTouches((n) => n + 1);
  };

  const firstSlot = useDerivedValue(() => flip.get());
  const secondSlot = useDerivedValue(() => 1 - flip.get());

  // The whole card: pushed up by the keyboard, shrinking away and coming back in a turn.
  const cardStyle = useAnimatedStyle(() => {
    const t = turn.get();
    const gone = smooth(t, 0, OUT_FOR);
    const back = smooth(t, OUT_FOR + EMPTY_FOR, TURN_END);
    const scale = t < TURN_HALF ? 1 - (1 - OUT_SCALE) * gone : IN_SCALE + (1 - IN_SCALE) * back;
    return {
      opacity: t <= 0 ? 1 : t < TURN_HALF ? 1 - gone : smooth(t, OUT_FOR + EMPTY_FOR, OUT_FOR + EMPTY_FOR + IN_FOR * 0.6),
      transform: [{ translateY: lift.get() }, { scale: reducedMotion ? 1 : scale }],
    };
  });
  // Everything written on the form: dims and goes on Swap, returns late in a turn.
  const contentStyle = useAnimatedStyle(() => {
    const t = turn.get();
    const s = shownSwap(swap.get(), t);
    return { opacity: s >= 0 ? 1 - smooth(s, 0, CONTENT_OUT) : t > 0 ? smooth(t, OUT_FOR + EMPTY_FOR + IN_FOR * 0.35, TURN_END) : 1 };
  });
  const switchStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${180 * flip.get()}deg` }] }));
  const labelStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.02 * press.get() }] }));
  const titleStyle = useAnimatedStyle(() => {
    const s = shownSwap(swap.get(), turn.get());
    const u = smooth(s, TITLE_AT, TITLE_AT + TITLE_FOR);
    const style = { opacity: u, transform: [{ translateY: reducedMotion ? 0 : TITLE_RISE * (1 - u) }] };
    if (!CAN_FILTER) return style;
    return { ...style, filter: [{ blur: TITLE_BLUR * (1 - u) }] as unknown as string };
  });
  const blurProps = useAnimatedProps(() => {
    const s = shownSwap(swap.get(), turn.get());
    return { intensity: s < TITLE_AT - 0.1 ? 0 : 38 * (1 - smooth(s, TITLE_AT + 0.05, TITLE_AT + TITLE_FOR)) };
  });
  const lineOneStyle = useAnimatedStyle(() => {
    const u = smooth(shownSwap(swap.get(), turn.get()), LINES_AT, LINES_AT + LINE_FOR);
    return { opacity: u, transform: [{ translateY: LINE_RISE * (1 - u) }] };
  });
  const lineTwoStyle = useAnimatedStyle(() => {
    const u = smooth(shownSwap(swap.get(), turn.get()), LINES_AT + LINE_GAP, LINES_AT + LINE_GAP + LINE_FOR);
    return { opacity: u, transform: [{ translateY: LINE_RISE * (1 - u) }] };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const inForm = phase === 'form';
  const rows = [
    { token: tokens[0], slot: firstSlot, inputRef: firstInput, editable: !flipped },
    { token: tokens[1], slot: secondSlot, inputRef: secondInput, editable: flipped },
  ];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas pointerEvents="none" style={styles.fill}>
        <Rect x={0} y={0} width={size.width} height={size.height}>
          <RadialGradient c={vec(size.width / 2, size.height * 0.52)} r={size.height * 0.62} colors={SKY} positions={[0, 0.5, 1]} />
        </Rect>
      </Canvas>
      <Pressable style={styles.fill} onPress={() => Keyboard.dismiss()} accessible={false} />

      <Animated.View style={[styles.card, { top: cardTop, width, left: (size.width - width) / 2 }, cardStyle]}>
        <Chrome width={width} swap={swap} turn={turn} press={press} still={reducedMotion} />

        {/* The success card's words. A touch on it brings the form back. */}
        <Pressable
          style={[styles.done, { top: DONE_TOP }]}
          disabled={phase !== 'done'}
          accessibilityElementsHidden={phase !== 'done'}
          accessibilityRole="button"
          accessibilityLabel={`${successTitle}. ${successLine} ${receipt}`}
          onPress={() => {
            touched();
            leave();
          }}
        >
          <Animated.Text style={[styles.successTitle, titleStyle]} numberOfLines={1} adjustsFontSizeToFit>
            {successTitle}
          </Animated.Text>
          <Animated.Text style={[styles.successLine, lineOneStyle]} numberOfLines={1}>
            {successLine}
          </Animated.Text>
          <Animated.Text style={[styles.successLine, styles.receipt, lineTwoStyle]} numberOfLines={1}>
            {receipt}
          </Animated.Text>
        </Pressable>
        {phase === 'done' && IOS && (
          <AnimatedBlurView
            pointerEvents="none"
            tint="dark"
            animatedProps={blurProps}
            style={[styles.titleBlur, { top: DONE_TOP + DONE.panelY, left: PANEL_BOX.x, width: panelWidth }]}
          />
        )}
        <View style={[styles.ringSlot, { top: RING.y - RING_CANVAS / 2 }]} pointerEvents="none">
          <Ring swap={swap} turn={turn} still={reducedMotion} />
        </View>

        <Animated.View style={[styles.fill, contentStyle]} pointerEvents={inForm ? 'box-none' : 'none'} accessibilityElementsHidden={!inForm}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          <View style={styles.modes}>
            {modes.map((name, index) => (
              <Pressable
                key={name}
                hitSlop={6}
                accessibilityRole="button"
                accessibilityState={{ selected: index === mode }}
                onPress={() => {
                  touched();
                  tick();
                  setMode(index);
                }}
              >
                <Text style={[styles.mode, index === mode && styles.modeOn]}>{name}</Text>
              </Pressable>
            ))}
          </View>

          <View style={[styles.panel, { width: panelWidth }]} pointerEvents="box-none">
            <Text style={[styles.slotLabel, { top: 8 }]}>{fromLabel}</Text>
            <Text style={[styles.slotLabel, { top: WELL_BOX.y + 7 }]}>{toLabel}</Text>
            {rows.map((row, index) => (
              <Row
                key={row.token.symbol}
                token={row.token}
                slot={row.slot}
                front={index === 1}
                editable={row.editable && inForm}
                amount={amounts[index]}
                caret={row.editable && inForm && (focused || autoCaret)}
                time={time}
                balanceLabel={balanceLabel}
                inputRef={row.inputRef}
                onChangeText={(text) => {
                  touched();
                  setDraft(cleaned(text));
                }}
                onFocus={() => {
                  touched();
                  setFocused(true);
                }}
                onBlur={() => setFocused(false)}
                onPick={() => {
                  touched();
                  tick();
                  onPickToken?.(row.token);
                }}
              />
            ))}
            <Pressable
              style={styles.max}
              hitSlop={10}
              accessibilityRole="button"
              onPress={() => {
                touched();
                tick();
                setDraft(String(from.balance));
              }}
            >
              {({ pressed }) => <Text style={[styles.maxLabel, pressed && styles.pressed]}>{maxLabel}</Text>}
            </Pressable>
            <Text style={styles.rate} numberOfLines={1}>
              1 {from.symbol} = {grouped(price >= 1 ? price.toFixed(2) : price.toFixed(4))} {to.symbol}
            </Text>
            <Pressable
              style={[styles.switchSlot, { left: panelWidth / 2 - SWITCH / 2 - 5 }]}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel="Switch the two tokens"
              onPress={() => {
                touched();
                switchTokens(true);
              }}
            >
              {({ pressed }) => (
                <Animated.View
                  style={[styles.switch, { transform: [{ scale: pressed ? 0.9 : 1 }], transitionProperty: 'transform', transitionDuration: 120 }]}
                >
                  <Animated.View style={switchStyle}>
                    <Ionicons name="swap-vertical" size={17} color={BUTTON_INK} />
                  </Animated.View>
                </Animated.View>
              )}
            </Pressable>
          </View>

          <Pressable
            style={[styles.swap, { width: width - BUTTON.x * 2 }]}
            accessibilityRole="button"
            onPressIn={() => hold(true)}
            onPressOut={() => hold(false)}
            onPress={() => {
              touched();
              exchange(true);
            }}
          >
            <Animated.Text style={[styles.swapLabel, labelStyle]} numberOfLines={1}>
              {swapLabel}
            </Animated.Text>
          </Pressable>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY[2],
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  card: {
    position: 'absolute',
    height: CARD.height,
  },
  chrome: {
    position: 'absolute',
    left: -SPILL,
    top: -SPILL,
  },
  title: {
    position: 'absolute',
    left: 13,
    top: 16,
    color: INK,
    fontSize: 14.5,
    lineHeight: 19,
    fontWeight: '500',
  },
  modes: {
    position: 'absolute',
    right: 12,
    top: 13,
    height: 27,
    paddingHorizontal: 12,
    gap: 20,
    borderRadius: 5,
    backgroundColor: PANEL,
    flexDirection: 'row',
    alignItems: 'center',
  },
  mode: {
    color: FAINT,
    fontSize: 12,
    lineHeight: 16,
  },
  modeOn: {
    color: INK,
  },
  panel: {
    position: 'absolute',
    left: PANEL_BOX.x,
    top: PANEL_BOX.y,
    height: PANEL_BOX.height,
  },
  slotLabel: {
    position: 'absolute',
    left: TEXT_LEFT,
    color: INK,
    fontSize: 12.5,
    lineHeight: 17,
  },
  row: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: ROW_TOP,
    height: ROW_HEIGHT,
  },
  amountSlot: {
    position: 'absolute',
    left: TEXT_LEFT,
    right: 112,
    top: 0,
    height: 40,
    justifyContent: 'center',
  },
  amountLine: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  amount: {
    flexShrink: 1,
    color: INK,
    fontSize: 27,
    lineHeight: 34,
    fontWeight: '300',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
  },
  caret: {
    width: 1.5,
    height: 28,
    marginLeft: 1.5,
    borderRadius: 1,
    backgroundColor: MINT,
  },
  input: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    padding: 0,
    color: 'transparent',
    fontSize: 27,
  },
  chip: {
    position: 'absolute',
    right: TEXT_LEFT,
    top: 7,
    height: 28,
    paddingLeft: 8,
    paddingRight: 9,
    gap: 7,
    borderRadius: 9,
    borderCurve: 'continuous',
    backgroundColor: '#171817',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
    flexDirection: 'row',
    alignItems: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 6, color: 'rgba(0,0,0,0.5)' }],
  },
  mark: {
    width: 15,
    height: 15,
    borderRadius: 7.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  markDot: {
    width: 5.5,
    height: 5.5,
    borderRadius: 2.75,
  },
  symbol: {
    color: INK,
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '500',
  },
  pressed: {
    opacity: 0.6,
  },
  balance: {
    position: 'absolute',
    left: TEXT_LEFT,
    top: 45,
    color: MUTE,
    fontSize: 11,
    lineHeight: 15,
    fontVariant: ['tabular-nums'],
  },
  max: {
    position: 'absolute',
    right: TEXT_LEFT + 6,
    top: ROW_TOP + 44,
  },
  maxLabel: {
    color: INK,
    fontSize: 10,
    lineHeight: 14,
  },
  rate: {
    position: 'absolute',
    right: TEXT_LEFT,
    top: ROW_TOP + SLOT + 45,
    color: MUTE,
    fontSize: 11,
    lineHeight: 15,
    fontVariant: ['tabular-nums'],
  },
  switchSlot: {
    position: 'absolute',
    top: SWITCH_Y - SWITCH / 2 - 5,
    width: SWITCH + 10,
    height: SWITCH + 10,
    borderRadius: (SWITCH + 10) / 2,
    backgroundColor: PANEL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  switch: {
    width: SWITCH,
    height: SWITCH,
    borderRadius: SWITCH / 2,
    backgroundColor: MINT,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 12, color: 'rgba(30,227,154,0.55)' }],
  },
  swap: {
    position: 'absolute',
    left: BUTTON.x,
    top: BUTTON.y,
    height: BUTTON.height,
    alignItems: 'center',
    justifyContent: 'center',
  },
  swapLabel: {
    color: BUTTON_INK,
    fontSize: 14.5,
    lineHeight: 19,
    fontWeight: '500',
  },
  done: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: DONE.height,
    alignItems: 'center',
  },
  successTitle: {
    marginTop: SUCCESS_TITLE_Y - DONE_TOP,
    paddingHorizontal: 24,
    color: INK,
    fontSize: 19.5,
    lineHeight: 26,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  successLine: {
    marginTop: 8,
    color: MUTE,
    fontSize: 10.5,
    lineHeight: 14,
  },
  receipt: {
    marginTop: 1,
    color: INK,
    fontVariant: ['tabular-nums'],
  },
  titleBlur: {
    position: 'absolute',
    height: DONE.panelHeight,
    borderRadius: PANEL_BOX.radius,
    overflow: 'hidden',
  },
  ringSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  ring: {
    width: RING_CANVAS,
    height: RING_CANVAS,
  },
});
