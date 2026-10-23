import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, Group, Path, Rect, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BADGE_BOX, BADGE_HEIGHT, Badge } from './Badge';
import { Confetti } from './Confetti';
import { easeOut, seg, spring, useLoopClock } from './motion';

type Props = {
  level?: number;
  /** The figures counted up under the heading. */
  points?: number;
  xp?: number;
  title?: string;
  /** The line under the heading. */
  subtitle?: string;
  pointsLabel?: string;
  xpLabel?: string;
  continueLabel?: string;
  shareLabel?: string;
  /** The coin chip next to the share link. */
  shareReward?: string;
  /** Plays by itself every LOOP seconds and ends where it began. */
  autoPlay?: boolean;
  /** Called when Continue is pressed by a touch. */
  onContinue?: () => void;
  onShare?: () => void;
};

// The timeline, in seconds from the start of the loop.
export const LOOP = 9;
// A touch on the resting screen starts here.
const TAP_AT = 0.5;
const BURST_AT = 0.6;
const DOT_OUT_IN = 0.2;
const GLOW_AT = 1.0;
const GLOW_FOR = 0.6;
const TITLE_AT = 1.5;
const TITLE_IN = 0.4;
const STATS_AT = 1.9;
const STATS_COUNT = 0.6;
const STATS_STAGGER = 0.1;
const CTA_AT = 2.5;
const LINK_AT = 3.1;
const LINK_IN = 0.4;
const SHINE_AT = 3.3;
const SHINE_FOR = 0.6;
const SECOND_BURST_AT = 4.0;
const SECOND_BURST_COUNT = 18;
const PRESS_AT = 6.0;
const PRESS_DOWN = 0.15;
const PRESS_UP = 0.25;
const OUT_AT = 7.0;
const OUT_IN = 1.0;
const DOT_BACK_AT = 8.2;
const DOT_BACK_IN = 0.4;
const REDUCED_FADE = 0.3;
// The badge bursts out on a bouncy spring: damping ratio 0.55 is 2 * 0.55 * sqrt(stiffness).
const BADGE_STIFFNESS = 220;
const BADGE_DAMPING = 16.3;
const BADGE_FROM = 0.2;
const CTA_STIFFNESS = 200;
const CTA_DAMPING = 22;
const SLIDE = 16;
const CTA_SLIDE = 40;
const OUT_SCALE = 0.96;
const PRESS_SCALE = 0.96;

const CREAM = '#fff0b8';
const EDGE = '#ffe08a';
const INK = '#1a1a2e';
const MUTE = '#8a7a50';
const ORANGE = '#ff6a1a';

const GLOW = 420;
const DOT = 12;
const COIN = 34;
const CTA_HEIGHT = 56;
const SHARE_HEIGHT = 56;
const MARGIN = 24;
// The sunburst behind the badge: rays, its size, and how fast it turns (radians a second).
const RAYS = 14;
const RAY_SIZE = 460;
const RAY_TURN = 0.12;

/** Wedges from the centre of the sunburst, alternating with gaps. */
const RAY_PATH = (() => {
  const b = Skia.PathBuilder.Make();
  const c = RAY_SIZE / 2;
  const half = Math.PI / RAYS / 1.6;
  for (let i = 0; i < RAYS; i++) {
    const a = (i / RAYS) * Math.PI * 2;
    b.moveTo(c, c);
    b.lineTo(c + Math.cos(a - half) * c, c + Math.sin(a - half) * c);
    b.lineTo(c + Math.cos(a + half) * c, c + Math.sin(a + half) * c);
    b.close();
  }
  return b.build();
})();

function grouped(value: number) {
  const n = Math.round(value);
  if (n < 1000) return String(n);
  const rest = n % 1000;
  return `${Math.floor(n / 1000)},${rest < 10 ? '00' : rest < 100 ? '0' : ''}${rest}`;
}

function Stat({ to, at, caption, icon, clock, reduced }: { to: number; at: number; caption: string; icon: 'coin' | 'xp'; clock: SharedValue<number>; reduced: boolean }) {
  // The figure is state, set from the clock only while it changes: a text input fed by animated
  // props lost its text when the loop wrapped.
  const [shown, setShown] = useState(0);
  useAnimatedReaction(
    () => {
      const t = clock.get();
      return Math.round(to * (reduced ? seg(t, at, at + REDUCED_FADE) : easeOut(seg(t, at, at + STATS_COUNT))));
    },
    (value, old) => {
      if (value !== old) scheduleOnRN(setShown, value);
    },
  );
  const style = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), at, at + 0.35));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 10 }] };
  });
  return (
    <Animated.View style={[styles.stat, style]}>
      {icon === 'coin' ? (
        <LinearGradient colors={['#ffe066', '#ffb000']} style={[styles.statIcon, styles.coinRing]}>
          <Ionicons name="star" size={16} color="#ffffff" />
        </LinearGradient>
      ) : (
        <LinearGradient colors={['#5aa2ff', '#2a62ff']} style={[styles.statIcon, styles.xpRing]}>
          <Ionicons name="flash" size={17} color="#ffffff" />
        </LinearGradient>
      )}
      <Text style={styles.statValue}>+{grouped(shown)}</Text>
      <Text style={styles.statCaption}>{caption}</Text>
    </Animated.View>
  );
}

/**
 * A level-up screen: a gold badge bursts out of a dot with confetti, the heading and two counted
 * figures follow, and Continue slides up. After a pause the whole screen fades back to the dot.
 * One clock drives all of it, so a touch and the autoPlay loop run the same motion.
 */
export function LevelUpTakeover({
  level = 3,
  points = 596,
  xp = 1750,
  title = 'LEVEL UP!',
  subtitle = 'You reached level 3. Keep the streak going!',
  pointsLabel = 'Coins earned',
  xpLabel = 'XP earned',
  continueLabel = 'Continue',
  shareLabel = 'Share the News',
  shareReward = '+10',
  autoPlay = false,
  onContinue,
  onShare,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const { clock, play } = useLoopClock(LOOP, TAP_AT, autoPlay);

  const badgeY = Math.max(insets.top + 150, height * 0.32);
  const titleTop = badgeY + BADGE_HEIGHT / 2 + 26;
  const statsTop = titleTop + 100;
  const linkBottom = insets.bottom + 12;
  const ctaBottom = linkBottom + SHARE_HEIGHT + 12;

  const dismiss = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    // A press on Continue goes straight to the end of the screen.
    play(PRESS_AT, true);
    onContinue?.();
  };

  const sweep = useDerivedValue(() => seg(clock.get(), SHINE_AT, SHINE_AT + SHINE_FOR));
  // The sparkles blink on after the burst and twice more while the screen holds.
  const twinkle = useDerivedValue(() => {
    const t = clock.get();
    if (t < BURST_AT + 0.3) return 0;
    return 0.35 + 0.65 * Math.abs(Math.sin((t - BURST_AT - 0.3) * 1.6));
  });
  // The sunburst turns slowly while it shows and holds still otherwise, so it stops repainting.
  const rayTurn = useDerivedValue(() => {
    const t = Math.min(Math.max(clock.get(), BURST_AT), OUT_AT + OUT_IN);
    return [{ rotate: reduced ? 0 : (t - BURST_AT) * RAY_TURN }];
  });

  const dotStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const gone = seg(t, BURST_AT, BURST_AT + DOT_OUT_IN);
    const back = seg(t, DOT_BACK_AT, DOT_BACK_AT + DOT_BACK_IN);
    return { opacity: t < DOT_BACK_AT ? 1 - gone : back, transform: [{ scale: 1 + 0.15 * Math.sin(t * 4) * (1 - gone) }] };
  });
  // Everything but the dot fades and shrinks a little at the end.
  const stageStyle = useAnimatedStyle(() => {
    const out = seg(clock.get(), OUT_AT, OUT_AT + OUT_IN);
    return { opacity: 1 - out, transform: [{ scale: reduced ? 1 : 1 - (1 - OUT_SCALE) * out }] };
  });
  const badgeStyle = useAnimatedStyle(() => {
    const t = clock.get();
    if (reduced) return { opacity: seg(t, BURST_AT, BURST_AT + REDUCED_FADE) };
    const pop = t < BURST_AT ? 0 : BADGE_FROM + (1 - BADGE_FROM) * spring(t - BURST_AT, BADGE_STIFFNESS, BADGE_DAMPING);
    return { opacity: t < BURST_AT ? 0 : 1, transform: [{ scale: pop }] };
  });
  const glowStyle = useAnimatedStyle(() => {
    const p = seg(clock.get(), GLOW_AT, GLOW_AT + GLOW_FOR);
    // A resting haze, and a pulse that spreads out of it once.
    return { opacity: reduced ? 0.4 : 0.4 + 0.6 * Math.sin(Math.PI * p) * (1 - p) * 2, transform: [{ scale: reduced ? 1 : 1 + 0.3 * easeOut(p) }] };
  });
  const raysStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const p = reduced ? seg(t, BURST_AT, BURST_AT + REDUCED_FADE) : easeOut(seg(t, BURST_AT, BURST_AT + 0.6));
    return { opacity: p, transform: [{ scale: reduced ? 1 : 0.6 + 0.4 * p }] };
  });
  const subtitleStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), TITLE_AT + 0.08, TITLE_AT + 0.08 + TITLE_IN));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * SLIDE }] };
  });
  const cardStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), STATS_AT - 0.1, STATS_AT + 0.3));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * SLIDE }] };
  });
  const titleStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), TITLE_AT, TITLE_AT + TITLE_IN));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * SLIDE }] };
  });
  const ctaStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const into = reduced ? seg(t, CTA_AT, CTA_AT + REDUCED_FADE) : Math.min(1, spring(t - CTA_AT, CTA_STIFFNESS, CTA_DAMPING));
    const press = seg(t, PRESS_AT, PRESS_AT + PRESS_DOWN) - seg(t, PRESS_AT + PRESS_DOWN, PRESS_AT + PRESS_DOWN + PRESS_UP);
    return {
      opacity: Math.min(1, into * 3),
      transform: [{ translateY: reduced ? 0 : (1 - into) * CTA_SLIDE }, { scale: 1 - (1 - PRESS_SCALE) * press }],
    };
  });
  const linkStyle = useAnimatedStyle(() => {
    const p = easeOut(seg(clock.get(), LINK_AT, LINK_AT + LINK_IN));
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 12 }] };
  });

  return (
    <Pressable style={styles.screen} onPress={() => play()} accessibilityLabel={title}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill}>
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={vec(width / 2, badgeY)} r={Math.max(width, height) * 0.75} colors={[CREAM, EDGE]} />
        </Rect>
      </Canvas>

      <Animated.View style={[styles.dot, { left: width / 2 - DOT / 2, top: badgeY - DOT / 2 }, dotStyle]} pointerEvents="none">
        <View style={styles.dotCore} />
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, stageStyle]} pointerEvents="box-none">
        <Animated.View style={[styles.glow, { left: width / 2 - GLOW / 2, top: badgeY - GLOW / 2 }, glowStyle]} pointerEvents="none">
          <Canvas style={styles.glowCanvas}>
            <Circle cx={GLOW / 2} cy={GLOW / 2} r={GLOW / 2}>
              <RadialGradient c={vec(GLOW / 2, GLOW / 2)} r={GLOW / 2} colors={['rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']} />
            </Circle>
          </Canvas>
        </Animated.View>

        <Animated.View style={[styles.rays, { left: width / 2 - RAY_SIZE / 2, top: badgeY - RAY_SIZE / 2 }, raysStyle]} pointerEvents="none">
          <Canvas style={styles.rayCanvas}>
            <Group transform={rayTurn} origin={vec(RAY_SIZE / 2, RAY_SIZE / 2)}>
              <Path path={RAY_PATH}>
                <RadialGradient c={vec(RAY_SIZE / 2, RAY_SIZE / 2)} r={RAY_SIZE / 2} colors={['rgba(255,255,255,0.85)', 'rgba(255,255,255,0.35)', 'rgba(255,255,255,0)']} positions={[0, 0.45, 1]} />
              </Path>
            </Group>
          </Canvas>
        </Animated.View>

        <Animated.View style={[styles.badge, { top: badgeY - BADGE_BOX / 2 }, badgeStyle]} pointerEvents="none">
          <Badge level={level} sweep={sweep} twinkle={twinkle} />
        </Animated.View>

        <Animated.Text style={[styles.title, { top: titleTop }, titleStyle]}>{title}</Animated.Text>
        <Animated.Text style={[styles.subtitle, { top: titleTop + 50 }, subtitleStyle]}>{subtitle}</Animated.Text>

        <Animated.View style={[styles.stats, { top: statsTop }, cardStyle]} pointerEvents="none">
          <Stat to={points} at={STATS_AT} caption={pointsLabel} icon="coin" clock={clock} reduced={reduced} />
          <View style={styles.divider} />
          <Stat to={xp} at={STATS_AT + STATS_STAGGER} caption={xpLabel} icon="xp" clock={clock} reduced={reduced} />
        </Animated.View>

        <Animated.View style={[styles.ctaSlot, { bottom: ctaBottom }, ctaStyle]}>
          <Pressable style={styles.ctaPress} onPress={dismiss} accessibilityRole="button">
            <LinearGradient colors={['#ff8a3d', ORANGE, '#f45400']} locations={[0, 0.55, 1]} style={styles.cta}>
              <Text style={styles.ctaLabel}>{continueLabel}</Text>
            </LinearGradient>
          </Pressable>
        </Animated.View>

        <Animated.View style={[styles.link, { bottom: linkBottom }, linkStyle]}>
          <Pressable style={styles.linkRow} onPress={onShare} accessibilityRole="button">
            <Ionicons name="share-outline" size={20} color={INK} />
            <Text style={styles.linkLabel}>{shareLabel}</Text>
            <View style={styles.chip}>
              <LinearGradient colors={['#ffe066', '#ffb000']} style={styles.chipCoin}>
                <Ionicons name="star" size={9} color="#ffffff" />
              </LinearGradient>
              <Text style={styles.chipLabel}>{shareReward}</Text>
            </View>
          </Pressable>
        </Animated.View>
      </Animated.View>

      {!reduced && <Confetti clock={clock} at={BURST_AT} x={width / 2} y={badgeY} />}
      {!reduced && <Confetti clock={clock} at={SECOND_BURST_AT} x={width / 2} y={badgeY} count={SECOND_BURST_COUNT} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: EDGE, overflow: 'hidden' },
  dot: { position: 'absolute', width: DOT, height: DOT, alignItems: 'center', justifyContent: 'center' },
  dotCore: { width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: '#ffd84a', boxShadow: '0 0 14px 4px rgba(255, 184, 0, 0.7)' },
  glow: { position: 'absolute', width: GLOW, height: GLOW },
  glowCanvas: { width: GLOW, height: GLOW },
  rays: { position: 'absolute', width: RAY_SIZE, height: RAY_SIZE },
  rayCanvas: { width: RAY_SIZE, height: RAY_SIZE },
  badge: { position: 'absolute', alignSelf: 'center' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: INK, fontSize: 36, lineHeight: 42, fontWeight: '900', letterSpacing: 0.5 },
  subtitle: { position: 'absolute', left: 40, right: 40, textAlign: 'center', color: '#6f6140', fontSize: 16, lineHeight: 22, fontWeight: '500' },
  stats: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: 128,
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.62)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0 12px 30px rgba(196, 140, 20, 0.16), 0 1px 3px rgba(150, 100, 0, 0.1)',
  },
  stat: { flex: 1, alignItems: 'center' },
  divider: { width: 1, height: 72, backgroundColor: 'rgba(138,122,80,0.2)' },
  statIcon: { width: COIN, height: COIN, borderRadius: COIN / 2, alignItems: 'center', justifyContent: 'center', borderWidth: 2, marginBottom: 8 },
  coinRing: { borderColor: '#ffd23a', boxShadow: '0 3px 8px rgba(230, 150, 0, 0.35)' },
  xpRing: { borderColor: '#7ab4ff', boxShadow: '0 3px 8px rgba(40, 100, 255, 0.3)' },
  statValue: { color: INK, fontSize: 26, lineHeight: 30, fontWeight: '800', letterSpacing: -0.4, fontVariant: ['tabular-nums'] },
  statCaption: { marginTop: 2, color: MUTE, fontSize: 13, lineHeight: 16, fontWeight: '600' },
  ctaSlot: { position: 'absolute', left: MARGIN, right: MARGIN, height: CTA_HEIGHT, borderRadius: CTA_HEIGHT / 2, boxShadow: '0 12px 24px rgba(255, 106, 26, 0.38), 0 2px 4px rgba(200, 70, 0, 0.25)' },
  ctaPress: { flex: 1 },
  cta: { flex: 1, borderRadius: CTA_HEIGHT / 2, alignItems: 'center', justifyContent: 'center' },
  ctaLabel: { color: '#ffffff', fontSize: 18, lineHeight: 22, fontWeight: '800', letterSpacing: -0.2 },
  link: { position: 'absolute', left: MARGIN, right: MARGIN, height: SHARE_HEIGHT },
  linkRow: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 18,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.55)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.9)',
  },
  linkLabel: { flex: 1, color: INK, fontSize: 16, lineHeight: 20, fontWeight: '700' },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 5, height: 30, paddingLeft: 5, paddingRight: 10, borderRadius: 15, backgroundColor: '#fff7d6', borderWidth: 1, borderColor: '#ffe28a' },
  chipCoin: { width: 20, height: 20, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  chipLabel: { color: INK, fontSize: 14, lineHeight: 18, fontWeight: '800' },
});
