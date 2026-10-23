import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { type GestureType, GestureDetector } from 'react-native-gesture-handler';
import Animated, { interpolate, type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { Cover, type CoverKind, COVER_TITLES } from './covers';
import { COLORS, COVER, FONTS, HOME } from './theme';

const AVATAR = require('./assets/avatar.png');

/** Spines waiting at the back of the deck. */
const SPINES = ['#F2C21A', '#B9C2FE', '#9FB0F0'];
const ROW: CoverKind[] = ['objects', 'pages'];
const CHARTS: CoverKind[] = ['sky', 'arches', 'grid'];
const ROW_SCALE = HOME.row.coverH / COVER.h;
const STEP = HOME.row.coverW + HOME.row.gap;

type Props = {
  deck: SharedValue<number>;
  rowGesture: GestureType;
  onOpenBook: () => void;
};

export function Home({ deck, rowGesture, onOpenBook }: Props) {
  // the front cover slides right and reveals the next one waiting in the deck
  const loudStyle = useAnimatedStyle(() => ({ transform: [{ translateX: STEP * deck.get() }] }));
  const seeingStyle = useAnimatedStyle(() => {
    const d = deck.get();
    return {
      transform: [
        { translateX: interpolate(d, [0, 1], [-HOME.deck.peek, 0]) },
        { scale: interpolate(d, [0, 1], [(HOME.row.coverH - HOME.deck.inset * 2) / HOME.row.coverH, 1]) },
      ],
    };
  });
  const restStyle = useAnimatedStyle(() => ({ transform: [{ translateX: STEP * deck.get() }] }));

  return (
    <View style={styles.page}>
      <Text style={styles.title} accessibilityRole="header">
        For you
      </Text>
      <Image source={AVATAR} style={styles.avatar} accessibilityLabel="Your profile" />
      <Svg style={styles.searchIcon} width={HOME.searchIcon} height={HOME.searchIcon} viewBox="0 0 21 21">
        <Circle cx={8.8} cy={8.8} r={7.6} stroke={COLORS.ink} strokeWidth={1.9} fill="none" />
        <Path d="M14.4 14.4 20 20" stroke={COLORS.ink} strokeWidth={1.9} strokeLinecap="round" />
      </Svg>
      <Text style={styles.placeholder}>Book or author</Text>

      <View style={styles.nextCard} />
      <View style={styles.card}>
        <Text style={styles.heading}>Design</Text>
        <View style={styles.grabber} />
        <Text style={[styles.label, { top: HOME.label.newTop - HOME.card.top }]}>NEW & TRENDING</Text>
        <Text style={[styles.label, styles.seeAll, { top: HOME.label.newTop - HOME.card.top }]}>SEE ALL</Text>

        <GestureDetector gesture={rowGesture}>
          <View style={styles.row}>
            {SPINES.map((c, i) => (
              <View
                key={c}
                style={[
                  styles.spine,
                  {
                    left: HOME.row.deckLeft - HOME.deck.peek * (i + 2),
                    top: HOME.deck.inset * (i + 2),
                    height: HOME.row.coverH - HOME.deck.inset * 2 * (i + 2),
                    backgroundColor: c,
                    zIndex: -i,
                  },
                ]}
              />
            ))}
            <Animated.View style={[styles.rowCover, { left: HOME.row.deckLeft }, seeingStyle]}>
              <Pressable onPress={onOpenBook} accessibilityRole="button" accessibilityLabel={COVER_TITLES.seeing}>
                <ScaledCover kind="seeing" />
              </Pressable>
            </Animated.View>
            <Animated.View style={[styles.rowCover, { left: HOME.row.deckLeft }, loudStyle]}>
              <ScaledCover kind="loud" />
            </Animated.View>
            {ROW.map((k, i) => (
              <Animated.View key={k} style={[styles.rowCover, { left: HOME.row.deckLeft + STEP * (i + 1) }, restStyle]}>
                <ScaledCover kind={k} />
              </Animated.View>
            ))}
          </View>
        </GestureDetector>

        <Text style={[styles.label, { top: HOME.label.chartsTop - HOME.card.top }]}>TOP CHARTS</Text>
        {CHARTS.map((k, i) => (
          <View
            key={k}
            style={[styles.rowCover, { top: HOME.charts.top - HOME.card.top, left: HOME.charts.left + STEP * i }]}
            accessibilityLabel={COVER_TITLES[k]}
          >
            <ScaledCover kind={k} />
          </View>
        ))}
      </View>
    </View>
  );
}

function ScaledCover({ kind }: { kind: CoverKind }) {
  return (
    <View style={styles.scaledBox}>
      <View style={styles.scaledInner}>
        <Cover kind={kind} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { ...StyleSheet.absoluteFill, backgroundColor: COLORS.page },
  title: {
    position: 'absolute',
    top: HOME.titleTop,
    left: HOME.titleLeft,
    fontFamily: FONTS.medium,
    fontSize: HOME.titleSize,
    letterSpacing: 0,
    color: COLORS.ink,
  },
  avatar: {
    position: 'absolute',
    top: HOME.avatarTop,
    right: HOME.avatarRight,
    width: HOME.avatar,
    height: HOME.avatar,
    borderRadius: HOME.avatar / 2,
  },
  searchIcon: { position: 'absolute', top: HOME.searchTop, left: HOME.searchLeft },
  placeholder: {
    position: 'absolute',
    top: HOME.placeholderTop,
    left: HOME.placeholderLeft,
    fontFamily: FONTS.regular,
    fontSize: HOME.searchText,
    color: COLORS.placeholder,
  },
  nextCard: {
    position: 'absolute',
    top: HOME.nextCard.top,
    left: 0,
    right: 0,
    height: 200,
    borderRadius: HOME.card.radius,
    backgroundColor: COLORS.nextCard,
  },
  card: {
    position: 'absolute',
    top: HOME.card.top,
    left: 0,
    right: 0,
    height: HOME.card.height,
    borderRadius: HOME.card.radius,
    borderCurve: 'continuous',
    backgroundColor: COLORS.design,
    overflow: 'hidden',
  },
  heading: {
    position: 'absolute',
    top: HOME.heading.top - HOME.card.top,
    left: HOME.heading.left,
    fontFamily: FONTS.medium,
    fontSize: HOME.heading.size,
    letterSpacing: 0,
    color: COLORS.white,
  },
  grabber: {
    position: 'absolute',
    top: HOME.grabber.top - HOME.card.top,
    right: HOME.grabber.right,
    width: HOME.grabber.width,
    height: 2,
    borderRadius: 1,
    backgroundColor: COLORS.grabber,
  },
  label: {
    position: 'absolute',
    left: HOME.label.left,
    fontFamily: FONTS.heavy,
    fontSize: HOME.label.size,
    letterSpacing: HOME.label.tracking,
    color: COLORS.white,
  },
  seeAll: { left: undefined, right: HOME.label.right },
  row: { position: 'absolute', top: HOME.row.top - HOME.card.top, left: 0, right: 0, height: HOME.row.coverH },
  spine: { position: 'absolute', width: HOME.row.coverW },
  rowCover: { position: 'absolute', top: 0, width: HOME.row.coverW, height: HOME.row.coverH },
  scaledBox: { width: HOME.row.coverW, height: HOME.row.coverH, overflow: 'hidden' },
  scaledInner: {
    width: COVER.w,
    height: COVER.h,
    transformOrigin: 'top left',
    transform: [{ scale: ROW_SCALE }],
  },
});
