import { Ionicons } from '@expo/vector-icons';
import { Canvas, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import type { ReactNode } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { easeInOut, easeOut, seg, spring, useLoopClock } from './motion';
import { SCENE_HEIGHT, SCENE_WIDTH, Scene } from './Scene';

type Props = {
  wordmark?: string;
  skipLabel?: string;
  title?: string;
  subtitle?: string;
  buttonLabel?: string;
  /** The notification the banner previews. */
  notificationTitle?: string;
  notificationBody?: string;
  /** The caption of the grown card. */
  cardKicker?: string;
  cardTitle?: string;
  cardMeta?: string;
  /** Grows the banner into the card by itself every LOOP seconds and shrinks it back. */
  autoPlay?: boolean;
  /** Called when the button is pressed by a touch. */
  onEnable?: () => void;
};

// The timeline, in seconds from the start of the loop.
export const LOOP = 9;
const TAP_AT = 0.7;
const OUTLINE_AT = 0.8;
const OUTLINE_IN = 0.5;
const BANNER_AT = 1.3;
const BANNER_IN = 0.6;
const WARM_AT = 1.9;
const WARM_IN = 0.8;
const GROW_AT = 2.7;
const LINES_OUT_AT = 2.9;
const LINES_OUT_IN = 0.25;
const CAPTION_AT = 3.35;
const CAPTION_IN = 0.45;
const BREATHE_AT = 3.9;
const BREATHE_FOR = 2.2;
const COLLAPSE_AT = 6.1;
const COLLAPSE_IN = 0.8;
const BANNER_OUT_AT = 6.9;
const BANNER_OUT_IN = 0.5;
const OUTLINE_OUT_AT = 7.0;
const OUTLINE_OUT_IN = 0.6;
const REDUCED_FADE = 0.3;
// The card grows on a spring with a damping ratio of 0.9: 2 * 0.9 * sqrt(stiffness).
const GROW_STIFFNESS = 110;
const GROW_DAMPING = 18.9;
const BREATHE = 0.02;
// The photo shows through the warming banner this much before the card grows.
const PEEK = 0.4;

const BLACK = '#000000';
const INK = '#ffffff';
const MUTE = 'rgba(255,255,255,0.55)';
const LINE = 'rgba(255,255,255,0.32)';
const BANNER = '#1c1c1e';
const WARM = '#6b4224';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

const OUTLINE_WIDTH = 272;
const OUTLINE_HEIGHT = 424;
const OUTLINE_RADIUS = 46;
const OUTLINE_STROKE = 1.5;
// Room around the outline for the side buttons.
const PAD = 4;
const O = PAD + OUTLINE_STROKE / 2;
const OUTLINE_PATH = `M${O} ${OUTLINE_HEIGHT}V${OUTLINE_RADIUS + O}A${OUTLINE_RADIUS} ${OUTLINE_RADIUS} 0 0 1 ${OUTLINE_RADIUS + O} ${O}H${OUTLINE_WIDTH - OUTLINE_RADIUS + O}A${OUTLINE_RADIUS} ${OUTLINE_RADIUS} 0 0 1 ${OUTLINE_WIDTH + O} ${OUTLINE_RADIUS + O}V${OUTLINE_HEIGHT}`;
// The phone's buttons: action and volume on the left, power on the right.
const BUTTONS_PATH = `M${O - 3} 92V112M${O - 3} 132V168M${O - 3} 180V216M${OUTLINE_WIDTH + O + 3} 150V214`;
const NOTCH_WIDTH = 64;
const NOTCH_HEIGHT = 9;
// The banner starts this far below the top of the outline, and the card ends at its bottom edge.
const INSET = (OUTLINE_WIDTH - SCENE_WIDTH) / 2;
const BANNER_TOP = 44;
const BANNER_WIDTH = SCENE_WIDTH;
const BANNER_HEIGHT = 72;
const BANNER_RADIUS = 18;
const CARD_RADIUS = OUTLINE_RADIUS - INSET;
const CARD_HEIGHT = SCENE_HEIGHT;
const ICON = 40;
const BUTTON_HEIGHT = 56;

/**
 * An onboarding screen whose notification preview is drawn inside a phone outline. The outline
 * draws on, a notification slides in and warms, and then it grows into a full photo card that
 * the outline clips. The clock drives all of it, so a touch and autoPlay run the same motion.
 */
export function BannerGrowCard({
  wordmark = 'amber',
  skipLabel = 'Skip',
  title = 'Build your practice',
  subtitle = 'Get daily motivation and gentle reminders to sit, breathe and slow down.',
  buttonLabel = 'Enable notifications',
  notificationTitle = 'Time for your morning sit',
  notificationBody = 'Ten quiet minutes to begin.',
  cardKicker = "TODAY'S PRACTICE",
  cardTitle = 'Morning sit',
  cardMeta = '10 min · Breathwork',
  autoPlay = false,
  onEnable,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const { clock, play } = useLoopClock(LOOP, TAP_AT, autoPlay);

  // From the bottom up: the button, the copy, then the outline whose open foot sits above the copy.
  const buttonBottom = insets.bottom + 12;
  const copyBottom = buttonBottom + BUTTON_HEIGHT + 36;
  const copyHeight = 112;
  const outlineTop = Math.max(insets.top + 64, height - copyBottom - copyHeight - 28 - OUTLINE_HEIGHT);
  const outlineLeft = (width - OUTLINE_WIDTH) / 2 - PAD;
  const cardLeft = (width - BANNER_WIDTH) / 2;
  const cardTop = outlineTop + PAD + BANNER_TOP;

  const press = () => {
    if (!play()) return;
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onEnable?.();
  };

  /** 0 = banner, 1 = full card. */
  const grown = (t: number) => {
    'worklet';
    if (reduced) return t >= GROW_AT && t < COLLAPSE_AT ? 1 : 0;
    if (t < COLLAPSE_AT) return Math.max(0, spring(t - GROW_AT, GROW_STIFFNESS, GROW_DAMPING));
    return 1 - easeInOut(seg(t, COLLAPSE_AT, COLLAPSE_AT + COLLAPSE_IN));
  };

  /** 1 while the banner is on screen, with its slide-in and fade. */
  const bannerIn = (t: number) => {
    'worklet';
    return seg(t, BANNER_AT, BANNER_AT + (reduced ? REDUCED_FADE : BANNER_IN)) * (1 - seg(t, BANNER_OUT_AT, BANNER_OUT_AT + BANNER_OUT_IN));
  };

  const outlineEnd = useDerivedValue(() => {
    const t = clock.get();
    if (reduced) return 1;
    return easeInOut(seg(t, OUTLINE_AT, OUTLINE_AT + OUTLINE_IN)) * (1 - easeInOut(seg(t, OUTLINE_OUT_AT, OUTLINE_OUT_AT + OUTLINE_OUT_IN)));
  });
  const buttonsOpacity = useDerivedValue(() => seg(outlineEnd.get(), 0.75, 1));
  const outlineStyle = useAnimatedStyle(() => {
    const t = clock.get();
    if (!reduced) return { opacity: 1 };
    return { opacity: seg(t, OUTLINE_AT, OUTLINE_AT + REDUCED_FADE) * (1 - seg(t, OUTLINE_OUT_AT, OUTLINE_OUT_AT + REDUCED_FADE)) };
  });
  const notchStyle = useAnimatedStyle(() => ({ opacity: seg(outlineEnd.get(), 0.4, 0.7) }));

  const cardStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const g = grown(t);
    const slide = reduced ? 0 : (1 - easeOut(seg(t, BANNER_AT, BANNER_AT + BANNER_IN))) * -14;
    const warm = seg(t, WARM_AT, WARM_AT + WARM_IN) * (1 - seg(t, COLLAPSE_AT, COLLAPSE_AT + COLLAPSE_IN));
    const topRadius = BANNER_RADIUS + (CARD_RADIUS - BANNER_RADIUS) * Math.min(1, g);
    const bottomRadius = BANNER_RADIUS * (1 - seg(g, 0.6, 1));
    return {
      opacity: bannerIn(t),
      height: BANNER_HEIGHT + (CARD_HEIGHT - BANNER_HEIGHT) * g,
      borderTopLeftRadius: topRadius,
      borderTopRightRadius: topRadius,
      borderBottomLeftRadius: bottomRadius,
      borderBottomRightRadius: bottomRadius,
      borderColor: `rgba(255,255,255,${0.14 * (1 - seg(g, 0, 0.3))})`,
      backgroundColor: interpolateColor(Math.max(warm, Math.min(g, 1)), [0, 1], [BANNER, WARM], 'RGB', { gamma: 1 }),
      transform: [{ translateY: slide }],
    };
  });
  // Warm light spilling round the card once it is a photo.
  const glowStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const g = Math.min(1, grown(t));
    return { opacity: g * bannerIn(t), height: BANNER_HEIGHT + (CARD_HEIGHT - BANNER_HEIGHT) * g };
  });
  const sceneStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const g = grown(t);
    const warm = seg(t, WARM_AT, WARM_AT + WARM_IN) * (1 - seg(t, COLLAPSE_AT, COLLAPSE_AT + COLLAPSE_IN));
    const breathe = reduced ? 0 : Math.sin(Math.PI * seg(t, BREATHE_AT, BREATHE_AT + BREATHE_FOR)) * BREATHE;
    return { opacity: Math.max(PEEK * warm, Math.min(g, 1)), transform: [{ scale: 1.04 + breathe - 0.04 * Math.min(g, 1) }] };
  });
  // The notification leaves as the card grows and comes back as it shrinks.
  const contentStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const out =
      t < COLLAPSE_AT
        ? seg(t, LINES_OUT_AT, LINES_OUT_AT + LINES_OUT_IN)
        : 1 - seg(t, COLLAPSE_AT + COLLAPSE_IN * 0.5, COLLAPSE_AT + COLLAPSE_IN);
    return { opacity: 1 - out };
  });
  const iconStyle = useAnimatedStyle(() => {
    const t = clock.get();
    return { transform: [{ scale: reduced ? 1 : 0.6 + 0.4 * spring(t - BANNER_AT - 0.1, 300, 18) }] };
  });
  const captionStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const p = easeOut(seg(t, CAPTION_AT, CAPTION_AT + CAPTION_IN)) * (1 - seg(t, COLLAPSE_AT, COLLAPSE_AT + 0.25));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 10 }] };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.top, { top: insets.top + 14 }]}>
        <Text style={styles.wordmark}>{wordmark}</Text>
        <View style={styles.skip}>
          <Text style={styles.skipLabel}>{skipLabel}</Text>
          <Ionicons name="arrow-forward" size={15} color="rgba(255,255,255,0.72)" />
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.glow, { top: cardTop, left: cardLeft }, glowStyle]} />

      <Animated.View style={[styles.outline, { top: outlineTop, left: outlineLeft }, outlineStyle]}>
        <Canvas style={styles.canvas}>
          <Path path={OUTLINE_PATH} style="stroke" strokeWidth={OUTLINE_STROKE} strokeCap="round" color={LINE} start={0} end={outlineEnd} />
          <Path path={BUTTONS_PATH} style="stroke" strokeWidth={2.5} strokeCap="round" color={LINE} opacity={buttonsOpacity} />
        </Canvas>
        <Animated.View style={[styles.notch, notchStyle]} />
      </Animated.View>

      <Animated.View style={[styles.card, { top: cardTop, left: cardLeft }, cardStyle]}>
        <Animated.View style={[styles.scene, sceneStyle]}>
          <Scene />
        </Animated.View>
        <Animated.View style={[styles.content, contentStyle]}>
          <Animated.View style={iconStyle}>
            <LinearGradient colors={['#f6b25c', '#c8692a']} style={styles.icon}>
              <View style={styles.iconSun} />
              <View style={styles.iconHorizon} />
            </LinearGradient>
          </Animated.View>
          <View style={styles.lines}>
            <Line index={0} clock={clock} reduced={reduced}>
              <View style={styles.metaRow}>
                <Text style={styles.app}>AMBER</Text>
                <Text style={styles.when}>now</Text>
              </View>
            </Line>
            <Line index={1} clock={clock} reduced={reduced}>
              <Text style={styles.notificationTitle} numberOfLines={1}>
                {notificationTitle}
              </Text>
            </Line>
            <Line index={2} clock={clock} reduced={reduced}>
              <Text style={styles.notificationBody} numberOfLines={1}>
                {notificationBody}
              </Text>
            </Line>
          </View>
        </Animated.View>
        <Animated.View style={[styles.caption, captionStyle]}>
          <Text style={styles.kicker}>{cardKicker}</Text>
          <View style={styles.captionRow}>
            <View>
              <Text style={styles.cardTitle}>{cardTitle}</Text>
              <Text style={styles.cardMeta}>{cardMeta}</Text>
            </View>
            <View style={styles.play}>
              <Ionicons name="play" size={20} color="#3a1f0e" style={styles.playGlyph} />
            </View>
          </View>
        </Animated.View>
      </Animated.View>

      <View style={[styles.copy, { bottom: copyBottom, height: copyHeight }]}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>

      <Pressable
        onPress={press}
        accessibilityRole="button"
        style={({ pressed }) => [styles.button, { bottom: buttonBottom, opacity: pressed ? 0.6 : 1, transform: [{ scale: pressed ? 0.98 : 1 }] }]}
      >
        <Ionicons name="notifications-outline" size={19} color={INK} />
        <Text style={styles.buttonLabel}>{buttonLabel}</Text>
      </Pressable>
    </View>
  );
}

/** One line of the notification, sliding in a beat after the one above it. */
function Line({ index, clock, reduced, children }: { index: number; clock: SharedValue<number>; reduced: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const at = BANNER_AT + 0.22 + index * 0.06;
    const p = reduced ? 1 : easeOut(seg(t, at, at + 0.35));
    return { opacity: p, transform: [{ translateX: (1 - p) * -8 }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BLACK },
  top: { position: 'absolute', left: 0, right: 0, height: 24, justifyContent: 'center' },
  wordmark: { color: INK, fontSize: 16, lineHeight: 20, textAlign: 'center', fontFamily: MONO, letterSpacing: 8, marginLeft: 8 },
  skip: { position: 'absolute', right: 20, flexDirection: 'row', alignItems: 'center', gap: 4 },
  skipLabel: { color: 'rgba(255,255,255,0.72)', fontSize: 15, lineHeight: 20, fontWeight: '500' },
  outline: { position: 'absolute', width: OUTLINE_WIDTH + PAD * 2, height: OUTLINE_HEIGHT },
  canvas: { width: OUTLINE_WIDTH + PAD * 2, height: OUTLINE_HEIGHT },
  notch: { position: 'absolute', top: PAD + 14, alignSelf: 'center', width: NOTCH_WIDTH, height: NOTCH_HEIGHT, borderRadius: NOTCH_HEIGHT / 2, borderWidth: 1.5, borderColor: LINE },
  glow: {
    position: 'absolute',
    width: BANNER_WIDTH,
    borderRadius: CARD_RADIUS,
    backgroundColor: '#8a4c22',
    boxShadow: '0 16px 70px 2px rgba(226, 132, 62, 0.34), 0 0 28px rgba(240, 150, 70, 0.22)',
  },
  card: { position: 'absolute', width: BANNER_WIDTH, borderWidth: 1, overflow: 'hidden' },
  scene: { position: 'absolute', left: 0, top: 0, width: SCENE_WIDTH, height: SCENE_HEIGHT },
  content: { position: 'absolute', left: 0, top: 0, width: BANNER_WIDTH, height: BANNER_HEIGHT, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, gap: 11 },
  icon: { width: ICON, height: ICON, borderRadius: 10, alignItems: 'center', justifyContent: 'flex-end', overflow: 'hidden' },
  iconSun: { position: 'absolute', top: 11, width: 16, height: 16, borderRadius: 8, backgroundColor: '#fff4e2' },
  iconHorizon: { width: ICON, height: 13, backgroundColor: 'rgba(80,32,8,0.55)' },
  lines: { flex: 1, gap: 1 },
  metaRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  app: { color: MUTE, fontSize: 11, lineHeight: 14, fontWeight: '600', letterSpacing: 0.8 },
  when: { color: MUTE, fontSize: 11, lineHeight: 14 },
  notificationTitle: { color: INK, fontSize: 14, lineHeight: 18, fontWeight: '600', letterSpacing: -0.2 },
  notificationBody: { color: 'rgba(255,255,255,0.72)', fontSize: 13, lineHeight: 17 },
  caption: { position: 'absolute', left: 20, right: 18, top: SCENE_HEIGHT - 116 },
  kicker: { color: 'rgba(255,240,225,0.78)', fontSize: 11, lineHeight: 14, fontWeight: '700', letterSpacing: 1.4 },
  captionRow: { marginTop: 6, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  cardTitle: { color: INK, fontSize: 28, lineHeight: 32, fontWeight: '600', letterSpacing: -0.6 },
  cardMeta: { marginTop: 4, color: 'rgba(255,240,225,0.78)', fontSize: 14, lineHeight: 18, fontWeight: '500' },
  play: { width: 48, height: 48, borderRadius: 24, backgroundColor: '#fff6ec', alignItems: 'center', justifyContent: 'center', boxShadow: '0 6px 16px rgba(30, 10, 0, 0.35)' },
  playGlyph: { marginLeft: 3 },
  copy: { position: 'absolute', left: 32, right: 32, alignItems: 'center', justifyContent: 'flex-start', gap: 12 },
  title: { color: INK, fontSize: 32, lineHeight: 38, fontWeight: '500', letterSpacing: -0.8, textAlign: 'center' },
  subtitle: { color: MUTE, fontSize: 16, lineHeight: 22, textAlign: 'center' },
  button: {
    position: 'absolute',
    left: 20,
    right: 20,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.85)',
    backgroundColor: 'rgba(255,255,255,0.04)',
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '500', letterSpacing: -0.2 },
});
