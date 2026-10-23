import { Feather } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, type ReactNode, useEffect, useRef, useState } from 'react';
import { Keyboard, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type IconName = ComponentProps<typeof Feather>['name'];

export type Chip = { label: string; icon: IconName };

export type Tab = {
  label: string;
  icon: IconName;
  /** The chips of the tab's panel, row by row. A tab without rows opens the sign-in form. */
  rows?: Chip[][];
};

type Props = {
  tabs?: Tab[];
  emailPlaceholder?: string;
  passwordPlaceholder?: string;
  signInLabel?: string;
  forgotLabel?: string;
  background?: string;
  barColor?: string;
  /** The icon and the label of the tab whose panel is open. */
  activeColor?: string;
  inactiveColor?: string;
  /** Opens the panels one after the other by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the tab whose panel opened, or null when the bar closed. */
  onOpen?: (tab: number | null) => void;
  /** Called with a tab and the place of the chip chosen in it, counted along its rows. */
  onChip?: (tab: number, chip: number) => void;
  onSignIn?: (email: string, password: string) => void;
  onForgot?: () => void;
};

const TABS: Tab[] = [
  {
    label: 'Work',
    icon: 'briefcase',
    rows: [
      [{ label: 'All', icon: 'grid' }, { label: 'Design', icon: 'pen-tool' }, { label: 'Dev', icon: 'code' }],
      [{ label: 'Motion', icon: 'zap' }, { label: 'Research', icon: 'search' }, { label: 'Branding', icon: 'award' }],
      [{ label: '3D', icon: 'box' }],
    ],
  },
  {
    label: 'About',
    icon: 'user',
    rows: [
      [{ label: 'Story', icon: 'book' }, { label: 'Skills', icon: 'star' }, { label: 'Contact', icon: 'mail' }],
      [{ label: 'Resume', icon: 'file-text' }, { label: 'Awards', icon: 'award' }, { label: 'Timeline', icon: 'calendar' }],
    ],
  },
  {
    label: 'Journal',
    icon: 'book-open',
    rows: [
      [{ label: 'All', icon: 'grid' }, { label: 'Design', icon: 'pen-tool' }, { label: 'Code', icon: 'code' }, { label: 'Life', icon: 'feather' }],
      [{ label: 'Travel', icon: 'send' }, { label: 'Process', icon: 'settings' }, { label: 'Insights', icon: 'sun' }],
      [{ label: 'Tools', icon: 'tool' }],
    ],
  },
  { label: 'Login', icon: 'log-in' },
];

/** The period of the autoPlay loop, in seconds, and the tab it presses at each moment of it. */
export const LOOP = 10;
// The last press is on the tab that is open, which closes the bar.
const STEPS = [
  { at: 0.8, tab: 0 },
  { at: 2.6, tab: 1 },
  { at: 4.2, tab: 2 },
  { at: 5.8, tab: 3 },
  { at: 7.4, tab: 0 },
  { at: 8.8, tab: 0 },
];
// After a touch the loop waits this long, closes the bar and starts over.
const USER_HOLD = 3;

const BACKGROUND = '#FFFFFF';
const BAR = '#0D0D0D';
const ACTIVE = '#FFFFFF';
const INACTIVE = '#8E8E93';
const CHIP = '#1A1A1C';
const CHIP_SELECTED = '#2E2E30';
const CHIP_INK = '#A1A1A6';
const CHIP_INK_SELECTED = '#FFFFFF';
const FIELD = '#1A1A1C';
const PLACEHOLDER = '#6E6E73';
const HAIRLINE = 'rgba(255,255,255,0.08)';
// The page's placeholders: shapes, the card under the title, the outline of a group.
const SHAPE = '#E4E4E8';
const CARD = '#F6F6F8';
const OUTLINE = '#EEEEF1';

const MARGIN = 20;
const TITLE_TOP = 14;
const BAR_WIDTH = 345;
const BAR_RADIUS = 20;
// The bar's bottom edge above the bottom of the screen. A bottom inset taller than a gesture bar's
// is a row of buttons, which the bar stays clear of.
const BAR_BOTTOM = 24;
const GESTURE_INSET = 34;
const BUTTONS_CLEARANCE = 8;
const TAB_ROW = 66;
const TAB_ICON = 22;
const PAD = 12;
const GAP = 8;
const CHIP_HEIGHT = 30;
const CHIP_ICON = 13;
const FIELD_HEIGHT = 44;
const FIELD_ICON = 16;
const LINK_HEIGHT = 16;
const FORM_HEIGHT = PAD * 2 + FIELD_HEIGHT * 3 + GAP * 3 + LINK_HEIGHT;
// The page behind an open panel: blurred (the strength of the iOS blur view, the radius of the
// Android filter) and washed with white.
const IOS = Platform.OS === 'ios';
const CAN_FILTER = !IOS && Number(Platform.Version) >= 31;
const BLUR_INTENSITY = 24;
const BLUR_RADIUS = 12;
const WASH = 0.4;

// The bar's height follows a spring that overshoots by about a twentieth.
const SPRING = { mass: 1, stiffness: 250, damping: 23, reduceMotion: ReduceMotion.Never };
// Seconds. The page blurs; a tab and a chip cross-fade to their other look.
const VEIL = 0.3;
const TAB_FADE = 0.2;
const CHIP_FADE = 0.2;
// A panel's pieces come in one after the other in reading order, each fading in while it rises,
// and leave together, faster.
const REVEAL_DELAY = 0.08;
const STAGGER = 0.025;
const REVEAL_IN = 0.3;
const REVEAL_OUT = 0.15;
const RISE = 6;
// Without motion everything cross-fades in this time and the height jumps.
const STILL_FADE = 0.2;

// A cubic ease-out.
const EASE = Easing.bezier(0.33, 1, 0.68, 1);

function ease(seconds: number) {
  return { duration: seconds * 1000, easing: EASE, reduceMotion: ReduceMotion.Never };
}

function panelHeight(tab: Tab) {
  if (!tab.rows) return FORM_HEIGHT;
  return PAD * 2 + tab.rows.length * CHIP_HEIGHT + (tab.rows.length - 1) * GAP;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One piece of a panel: it fades in and rises when its panel opens, `index` steps after the first. */
function Reveal({ shown, index, still, style, children }: { shown: boolean; index: number; still: boolean; style?: ViewStyle; children: ReactNode }) {
  const progress = useSharedValue(0);
  useEffect(() => {
    if (!shown) progress.set(withTiming(0, ease(still ? STILL_FADE : REVEAL_OUT)));
    else if (still) progress.set(withTiming(1, ease(STILL_FADE)));
    else progress.set(withDelay((REVEAL_DELAY + index * STAGGER) * 1000, withTiming(1, ease(REVEAL_IN)), ReduceMotion.Never));
  }, [shown, index, still, progress]);
  const animated = useAnimatedStyle(() => ({
    opacity: progress.get(),
    transform: [{ translateY: still ? 0 : RISE * (1 - progress.get()) }],
  }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

function ChipButton({ chip, selected, onPress }: { chip: Chip; selected: boolean; onPress: () => void }) {
  const fade = { transitionDuration: CHIP_FADE * 1000 };
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
      <Animated.View style={[styles.chip, { backgroundColor: selected ? CHIP_SELECTED : CHIP, transitionProperty: 'backgroundColor' }, fade]}>
        <View>
          <Feather name={chip.icon} size={CHIP_ICON} color={CHIP_INK} />
          <Animated.View style={[styles.over, { opacity: selected ? 1 : 0, transitionProperty: 'opacity' }, fade]}>
            <Feather name={chip.icon} size={CHIP_ICON} color={CHIP_INK_SELECTED} />
          </Animated.View>
        </View>
        <Animated.Text style={[styles.chipLabel, { color: selected ? CHIP_INK_SELECTED : CHIP_INK, transitionProperty: 'color' }, fade]} numberOfLines={1}>
          {chip.label}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

/** A tab: its grey face and, over it while its panel is open, the white one with a heavier label. */
function TabButton({ tab, active, activeColor, inactiveColor, onPress }: { tab: Tab; active: boolean; activeColor: string; inactiveColor: string; onPress: () => void }) {
  const fade = { transitionProperty: 'opacity' as const, transitionDuration: TAB_FADE * 1000 };
  return (
    <Pressable style={styles.tab} onPress={onPress} accessibilityRole="tab" accessibilityLabel={tab.label} accessibilityState={{ selected: active, expanded: active }}>
      <Animated.View style={[styles.tabFace, { opacity: active ? 0 : 1 }, fade]}>
        <Feather name={tab.icon} size={TAB_ICON} color={inactiveColor} />
        <Text style={[styles.tabLabel, { color: inactiveColor }]} numberOfLines={1}>
          {tab.label}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.tabFace, styles.tabOver, { opacity: active ? 1 : 0 }, fade]}>
        <Feather name={tab.icon} size={TAB_ICON} color={activeColor} />
        <Text style={[styles.tabLabel, styles.tabLabelActive, { color: activeColor }]} numberOfLines={1}>
          {tab.label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

/** The page behind the bar: placeholders only. */
function Page() {
  const row = (width: number, key: number, last: boolean) => (
    <View key={key} style={[styles.row, !last && styles.rowLine]}>
      <View style={styles.rowDot} />
      <View style={[styles.line, { width, height: 8 }]} />
      <View style={styles.rowEnd} />
    </View>
  );
  return (
    <>
      <View style={styles.title} />
      <View style={styles.card}>
        <View style={styles.cardDot} />
        <View style={styles.cardLines}>
          <View style={[styles.line, { width: 96, height: 8 }]} />
          <View style={[styles.line, { width: 60, height: 6 }]} />
        </View>
        <View style={styles.cardSquare} />
      </View>
      <View style={styles.group}>{[72, 72, 100].map((width, i) => row(width, i, i === 2))}</View>
      <View style={styles.group}>{[72, 100, 100, 84].map((width, i) => row(width, i, i === 3))}</View>
    </>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A page with a black bar of four tabs docked at the bottom. Pressing a tab makes the bar grow
 * upward on a spring to hold that tab's panel (rows of filter chips, or a sign-in form) whose
 * pieces come in one after the other, while the page behind blurs and pales. Another tab swaps
 * the panel and the height; the open tab, or the page, closes the bar.
 */
export function SubitemTabBar({
  tabs = TABS,
  emailPlaceholder = 'Email',
  passwordPlaceholder = 'Password',
  signInLabel = 'Sign in',
  forgotLabel = 'Forgot password?',
  background = BACKGROUND,
  barColor = BAR,
  activeColor = ACTIVE,
  inactiveColor = INACTIVE,
  autoPlay = false,
  onOpen,
  onChip,
  onSignIn,
  onForgot,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  // The tab whose panel is open; null while the bar is closed.
  const [open, setOpen] = useState<number | null>(null);
  // The chip chosen in each tab, by the tab's place; the first chip until one is pressed.
  const [chosen, setChosen] = useState<Record<number, number>>({});
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [concealed, setConcealed] = useState(true);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const height = useSharedValue(TAB_ROW);
  // How far the page is blurred and washed out, 0 to 1.
  const veil = useSharedValue(0);

  const show = (next: number | null) => {
    if (next === open) return;
    Keyboard.dismiss();
    setOpen(next);
    const target = TAB_ROW + (next === null ? 0 : panelHeight(tabs[next]));
    height.set(reducedMotion ? target : withSpring(target, SPRING));
    veil.set(withTiming(next === null ? 0 : 1, ease(reducedMotion ? STILL_FADE : VEIL)));
    onOpen?.(next);
  };

  /** A press on a tab opens its panel, or closes the bar when that panel is the open one. */
  const press = (index: number, byHand: boolean) => {
    // The loop that plays by itself is silent.
    if (byHand) tick();
    show(index === open ? null : index);
  };

  const rest = () => {
    show(null);
    setChosen({});
    setEmail('');
    setPassword('');
    setConcealed(true);
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ press, rest });
  useEffect(() => {
    latest.current = { press, rest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // Every step is timed from the loop's origin, so a late timer does not push the next one back.
    const cycle = (origin: number) => {
      STEPS.forEach((step) => after(origin + step.at * 1000 - Date.now(), () => latest.current.press(step.tab, false)));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        latest.current.rest();
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const barStyle = useAnimatedStyle(() => ({ height: height.get() }));
  const washStyle = useAnimatedStyle(() => ({ opacity: WASH * veil.get() }));
  const blurProps = useAnimatedProps(() => ({ intensity: reducedMotion ? 0 : BLUR_INTENSITY * veil.get() }));
  // On Android the blur is the page's own filter; iOS has no blur filter, hence the blur view.
  const pageStyle = useAnimatedStyle(() => {
    if (!CAN_FILTER || reducedMotion) return {};
    return { filter: [{ blur: BLUR_RADIUS * veil.get() }] as unknown as string };
  });

  const bottom = insets.bottom > GESTURE_INSET ? insets.bottom + BUTTONS_CLEARANCE : BAR_BOTTOM;
  const fade = { transitionProperty: 'opacity' as const, transitionDuration: TAB_FADE * 1000 };

  return (
    <View style={[styles.screen, { backgroundColor: background }]}>
      <StatusBar style="dark" />

      <Animated.View
        style={[styles.fill, styles.page, { paddingTop: insets.top + TITLE_TOP }, pageStyle]}
        accessibilityElementsHidden={open !== null}
        importantForAccessibility={open === null ? 'auto' : 'no-hide-descendants'}
      >
        <Page />
      </Animated.View>
      {IOS && <AnimatedBlurView pointerEvents="none" style={styles.fill} tint="light" animatedProps={blurProps} />}
      <Animated.View pointerEvents={open === null ? 'none' : 'auto'} style={[styles.fill, styles.wash, washStyle]}>
        <Pressable
          style={styles.fill}
          accessibilityLabel="Close"
          onPress={() => {
            touched();
            show(null);
          }}
        />
      </Animated.View>

      <KeyboardAvoidingView behavior="padding" pointerEvents="box-none" style={styles.fill}>
        <View pointerEvents="box-none" style={[styles.barSlot, { paddingBottom: bottom }]}>
          <Animated.View style={[styles.bar, { backgroundColor: barColor }, barStyle]}>
            {tabs.map((tab, t) => {
              const shown = open === t;
              const shell = { height: panelHeight(tab) };
              if (!tab.rows) {
                return (
                  <View key={t} pointerEvents={shown ? 'auto' : 'none'} accessibilityElementsHidden={!shown} style={[styles.panel, shell]}>
                    <Reveal shown={shown} index={0} still={reducedMotion} style={styles.field}>
                      <Feather name="mail" size={FIELD_ICON} color={PLACEHOLDER} />
                      <TextInput
                        style={styles.input}
                        value={email}
                        onChangeText={(text) => {
                          touched();
                          setEmail(text);
                        }}
                        onFocus={touched}
                        placeholder={emailPlaceholder}
                        placeholderTextColor={PLACEHOLDER}
                        selectionColor={activeColor}
                        keyboardType="email-address"
                        autoCapitalize="none"
                        autoCorrect={false}
                      />
                    </Reveal>
                    <Reveal shown={shown} index={1} still={reducedMotion} style={styles.field}>
                      <Feather name="lock" size={FIELD_ICON} color={PLACEHOLDER} />
                      <TextInput
                        style={styles.input}
                        value={password}
                        onChangeText={(text) => {
                          touched();
                          setPassword(text);
                        }}
                        onFocus={touched}
                        placeholder={passwordPlaceholder}
                        placeholderTextColor={PLACEHOLDER}
                        selectionColor={activeColor}
                        secureTextEntry={concealed}
                        autoCapitalize="none"
                        autoCorrect={false}
                      />
                      <Pressable
                        hitSlop={10}
                        accessibilityRole="button"
                        accessibilityLabel={concealed ? 'Show password' : 'Hide password'}
                        onPress={() => {
                          touched();
                          setConcealed((now) => !now);
                        }}
                      >
                        <Feather name={concealed ? 'eye' : 'eye-off'} size={FIELD_ICON} color={PLACEHOLDER} />
                      </Pressable>
                    </Reveal>
                    <Reveal shown={shown} index={2} still={reducedMotion}>
                      <Pressable
                        accessibilityRole="button"
                        onPress={() => {
                          touched();
                          tick();
                          onSignIn?.(email, password);
                        }}
                      >
                        {({ pressed }) => (
                          <View style={[styles.signIn, { backgroundColor: activeColor, opacity: pressed ? 0.8 : 1 }]}>
                            <Text style={[styles.signInLabel, { color: barColor }]} numberOfLines={1}>
                              {signInLabel}
                            </Text>
                          </View>
                        )}
                      </Pressable>
                    </Reveal>
                    <Reveal shown={shown} index={3} still={reducedMotion}>
                      <Pressable
                        hitSlop={6}
                        accessibilityRole="link"
                        onPress={() => {
                          touched();
                          onForgot?.();
                        }}
                      >
                        <Text style={styles.forgot} numberOfLines={1}>
                          {forgotLabel}
                        </Text>
                      </Pressable>
                    </Reveal>
                  </View>
                );
              }
              const starts = tab.rows.map((_, r) => tab.rows!.slice(0, r).reduce((sum, row) => sum + row.length, 0));
              return (
                <View key={t} pointerEvents={shown ? 'auto' : 'none'} accessibilityElementsHidden={!shown} style={[styles.panel, shell]}>
                  {tab.rows.map((row, r) => (
                    <View key={r} style={styles.chips}>
                      {row.map((chip, c) => {
                        const place = starts[r] + c;
                        return (
                          <Reveal key={c} shown={shown} index={place} still={reducedMotion}>
                            <ChipButton
                              chip={chip}
                              selected={(chosen[t] ?? 0) === place}
                              onPress={() => {
                                touched();
                                tick();
                                setChosen((now) => ({ ...now, [t]: place }));
                                onChip?.(t, place);
                              }}
                            />
                          </Reveal>
                        );
                      })}
                    </View>
                  ))}
                </View>
              );
            })}

            <Animated.View style={[styles.hairline, { opacity: open === null ? 0 : 1 }, fade]} />

            <View style={styles.tabs} accessibilityRole="tablist">
              {tabs.map((tab, t) => (
                <TabButton
                  key={t}
                  tab={tab}
                  active={open === t}
                  activeColor={activeColor}
                  inactiveColor={inactiveColor}
                  onPress={() => {
                    touched();
                    press(t, true);
                  }}
                />
              ))}
            </View>
          </Animated.View>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  over: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  page: {
    paddingHorizontal: MARGIN,
    gap: 16,
  },
  wash: {
    backgroundColor: '#FFFFFF',
  },
  title: {
    width: 68,
    height: 20,
    borderRadius: 8,
    backgroundColor: SHAPE,
    marginBottom: 4,
  },
  line: {
    borderRadius: 4,
    backgroundColor: SHAPE,
  },
  card: {
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    gap: 12,
  },
  cardDot: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: SHAPE,
  },
  cardLines: {
    flex: 1,
    gap: 8,
  },
  cardSquare: {
    width: 16,
    height: 16,
    borderRadius: 5,
    backgroundColor: SHAPE,
  },
  group: {
    borderRadius: 16,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: OUTLINE,
    paddingHorizontal: 14,
  },
  row: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  rowLine: {
    borderBottomWidth: 1,
    borderBottomColor: OUTLINE,
  },
  rowDot: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: SHAPE,
  },
  rowEnd: {
    width: 24,
    height: 6,
    borderRadius: 3,
    backgroundColor: SHAPE,
    marginLeft: 'auto',
  },
  barSlot: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  bar: {
    width: BAR_WIDTH,
    borderRadius: BAR_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(0,0,0,0.25)' }],
  },
  // A panel keeps its place above the tabs: the bar's top edge uncovers it.
  panel: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: TAB_ROW,
    padding: PAD,
    gap: GAP,
  },
  chips: {
    flexDirection: 'row',
    gap: GAP,
  },
  chip: {
    height: CHIP_HEIGHT,
    borderRadius: CHIP_HEIGHT / 2,
    paddingHorizontal: PAD,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chipLabel: {
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
    includeFontPadding: false,
  },
  field: {
    height: FIELD_HEIGHT,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: FIELD,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    gap: 10,
  },
  input: {
    flex: 1,
    height: FIELD_HEIGHT,
    padding: 0,
    color: '#FFFFFF',
    fontSize: 14,
    includeFontPadding: false,
  },
  signIn: {
    height: FIELD_HEIGHT,
    borderRadius: 12,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  signInLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  forgot: {
    height: LINK_HEIGHT,
    color: PLACEHOLDER,
    fontSize: 12,
    lineHeight: LINK_HEIGHT,
    textAlign: 'center',
    includeFontPadding: false,
  },
  hairline: {
    position: 'absolute',
    left: PAD,
    right: PAD,
    bottom: TAB_ROW,
    height: 1,
    backgroundColor: HAIRLINE,
  },
  tabs: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: TAB_ROW,
    flexDirection: 'row',
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabFace: {
    alignItems: 'center',
    gap: 4,
  },
  // The white face lies exactly over the grey one, in the middle of its tab.
  tabOver: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  tabLabel: {
    fontSize: 11,
    lineHeight: 14,
    includeFontPadding: false,
  },
  tabLabelActive: {
    fontWeight: '600',
  },
});
