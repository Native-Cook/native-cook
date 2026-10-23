import { Image } from 'expo-image';
import { type ReactNode } from 'react';
import { type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import Animated, { type AnimatedStyle } from 'react-native-reanimated';

import { SNEAKER_RATIO, Sneaker, type SneakerColors } from './Sneaker';

type Props = {
  /** Which of the four sample pages this is. */
  index: number;
  width: number;
  height: number;
  /** Moves the page's picture against its text while the page comes in. */
  drift: StyleProp<AnimatedStyle<StyleProp<ViewStyle>>>;
};

const INK = '#111111';
const PAPER = '#FFFFFF';
const PLATE = '#E9E9E6';
const MARGIN = 20;
// How far a picture trails its page; a photo is this much wider than the page for it.
export const PARALLAX = 60;

const OLIVE: SneakerColors = { upper: '#5E6B4A', shade: '#4A553A', accent: '#1E1E1C', midsole: '#F4F2EA', outsole: '#1B1B1B' };
const NIGHT: SneakerColors = { upper: '#2C2C30', shade: '#1D1D20', accent: '#8C8C92', midsole: '#F4F2EA', outsole: '#1B1B1B' };
const RUST: SneakerColors = { upper: '#6B3A2E', shade: '#4F2A21', accent: '#E9DCC8', midsole: '#F4F2EA', outsole: '#C9A26B' };
const ROSE: SneakerColors = { upper: '#F2BCCB', shade: '#E79DB3', accent: '#FFFFFF', midsole: '#F8F4EE', outsole: '#C9A26B' };
const SLATE: SneakerColors = { upper: '#5B6770', shade: '#46515A', accent: '#E8E4DA', midsole: '#F4F2EA', outsole: '#26282B' };

const ARCHIVE_PHOTO = 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=920&h=880&q=80';
const TRAIL_PHOTO = 'https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=920&h=800&q=80';
const ARCHIVE_TEXT =
  'ARCHIVE REVIVAL pairs washed denim, hooded fleece and track pants from the nineties with new cuts and trims: old favourites and fresh pieces in one wardrobe that talks across the decades.';
const TRAIL_TEXT =
  'The Ridge Runner has landed. A lugged sole that grips, a ripstop upper and a pull tab at the heel, in two colours: moss or slate. Which one are you taking up the hill?';

const SHELF: { colors: SneakerColors; year: string }[] = [
  { colors: NIGHT, year: '2019' },
  { colors: RUST, year: '2020' },
  { colors: ROSE, year: '2020' },
  { colors: SLATE, year: '2021' },
];
const SHELF_SHOE = 96;
const SHELF_STEP = 136;
// The first shoe of the shelf is cut by the left edge.
const SHELF_START = -38;
const NEW_IN: { colors: SneakerColors; name: string }[] = [
  { colors: SLATE, name: 'RIDGE RUNNER' },
  { colors: ROSE, name: 'COURT LOW' },
  { colors: RUST, name: 'GUM SOLE 74' },
  { colors: OLIVE, name: 'FIELD TRAINER' },
];

function Photo({ uri, width, height, drift, children }: { uri: string; width: number; height: number; drift: Props['drift']; children?: ReactNode }) {
  return (
    <View style={[styles.window, { height }]}>
      <Animated.View style={[styles.photo, { width: width + PARALLAX, height }, drift]}>
        <Image source={{ uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
      </Animated.View>
      {children}
    </View>
  );
}

function NewIn({ width }: { width: number }) {
  const cell = (width - MARGIN * 3) / 2;
  return (
    <>
      <View style={[styles.row, { marginTop: 24 }]}>
        <Text style={styles.caps}>THIS WEEK</Text>
        <Text style={styles.small}>8 Styles</Text>
      </View>
      <View style={styles.grid}>
        {NEW_IN.map((shoe) => (
          <View key={shoe.name} style={[styles.cell, { width: cell }]}>
            <Sneaker width={cell - 16} colors={shoe.colors} />
            <Text style={[styles.caps, styles.cellName]} numberOfLines={1}>
              {shoe.name}
            </Text>
          </View>
        ))}
      </View>
    </>
  );
}

function Browse({ width, height, drift }: Omit<Props, 'index'>) {
  const shoe = Math.min(300, width - 60);
  return (
    <>
      <Animated.View style={[styles.hero, { left: (width - shoe) / 2, top: height * 0.28 - (shoe * SNEAKER_RATIO) / 2 }, drift]}>
        <Sneaker width={shoe} colors={OLIVE} />
      </Animated.View>
      <View style={[styles.row, styles.placed, { top: height * 0.61 }]}>
        <Text style={styles.caps}>JUST DROPPED</Text>
        <Text style={styles.small}>Shop All</Text>
      </View>
      {SHELF.map((item, slot) => (
        <View key={slot} style={[styles.shelf, { left: SHELF_START + slot * SHELF_STEP, top: height * 0.73 }]}>
          <Sneaker width={SHELF_SHOE} colors={item.colors} />
          <Text style={styles.year}>{item.year}</Text>
        </View>
      ))}
    </>
  );
}

function Archive({ width, height, drift }: Omit<Props, 'index'>) {
  return (
    <>
      <Photo uri={ARCHIVE_PHOTO} width={width} height={height * 0.6875} drift={drift}>
        <Text style={[styles.caps, styles.shopNow]}>SHOP NOW</Text>
      </Photo>
      <Text style={styles.story} lineBreakStrategyIOS="standard">
        {ARCHIVE_TEXT}
      </Text>
    </>
  );
}

function Trail({ width, height, drift }: Omit<Props, 'index'>) {
  const shoe = Math.min(300, width - 60);
  return (
    <>
      <Photo uri={TRAIL_PHOTO} width={width} height={height * 0.625} drift={drift} />
      <Text style={styles.story} lineBreakStrategyIOS="standard">
        {TRAIL_TEXT}
      </Text>
      {/* The top of the next shoe, cut by the bar. */}
      <View style={[styles.hero, { left: (width - shoe) / 2, top: height - 46 }]}>
        <Sneaker width={shoe} colors={SLATE} />
      </View>
    </>
  );
}

/** The sample pages under the tabs: a shop's front, two stories and what is new. Replace them with the app's own. */
export function ShopPage({ index, width, height, drift }: Props) {
  return (
    <View style={styles.page}>
      {index === 0 && <NewIn width={width} />}
      {index === 1 && <Browse width={width} height={height} drift={drift} />}
      {index === 2 && <Archive width={width} height={height} drift={drift} />}
      {index === 3 && <Trail width={width} height={height} drift={drift} />}
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: PAPER,
  },
  window: {
    overflow: 'hidden',
    backgroundColor: PLATE,
  },
  photo: {
    position: 'absolute',
    left: -PARALLAX,
    top: 0,
  },
  hero: {
    position: 'absolute',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: MARGIN,
  },
  placed: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  caps: {
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 1,
    includeFontPadding: false,
  },
  small: {
    color: INK,
    fontSize: 10,
    lineHeight: 13,
    includeFontPadding: false,
  },
  shelf: {
    position: 'absolute',
    width: SHELF_SHOE,
    alignItems: 'center',
  },
  year: {
    marginTop: 14,
    color: INK,
    fontSize: 8,
    lineHeight: 10,
    includeFontPadding: false,
  },
  shopNow: {
    position: 'absolute',
    left: MARGIN,
    bottom: 14,
    color: '#FFFFFF',
    textDecorationLine: 'underline',
  },
  story: {
    marginTop: 12,
    marginHorizontal: MARGIN,
    color: INK,
    fontSize: 15,
    lineHeight: 18,
    fontWeight: '600',
    letterSpacing: -0.3,
    includeFontPadding: false,
  },
  grid: {
    marginTop: 20,
    paddingHorizontal: MARGIN,
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: MARGIN,
    rowGap: 28,
  },
  cell: {
    alignItems: 'center',
  },
  cellName: {
    marginTop: 10,
  },
});
