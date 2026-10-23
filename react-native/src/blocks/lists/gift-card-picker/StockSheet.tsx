import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, interpolate, useAnimatedStyle } from 'react-native-reanimated';

import { BackdropField } from './Backdrop';
import { BrandMark } from './Icons';
import { PRESS } from './motion';
import { GIFT_AMOUNT, STOCKS, type Stock } from './stocks';
import { BUTTON, COLORS, SKELETON, STOCK, TOUCH } from './theme';

// The dark step: a grey skeleton first, then the stock carousel whose backdrop follows the card.
type Props = {
  width: number;
  height: number; // sheet height
  trackFade: SharedValue<number>; // Reduce Motion: the cards fade instead of travelling
  ky: number;
  reveal: SharedValue<number>; // 0 skeleton, 1 content, 2 logos in
  scroll: SharedValue<number>; // carousel position in cards
  active: SharedValue<number>;
  bg: SharedValue<number>[]; // backdrop opacities, stacked by index
  desc: SharedValue<number>[];
  press: SharedValue<number>;
  commit: SharedValue<number>;
  pan: ReturnType<typeof Gesture.Race> | ReturnType<typeof Gesture.Pan>;
  onCard: (i: number) => void;
  onChoose: () => void;
  onPressIn: () => void;
  onPressOut: () => void;
};

// Dust specks drifting over the backdrop in the pin (fixed field, 1–2 pt).
const DUST = Array.from({ length: 46 }, (_, i) => {
  const a = Math.sin(i * 12.9898) * 43758.5453;
  const b = Math.sin(i * 78.233) * 12345.6789;
  const fa = a - Math.floor(a);
  const fb = b - Math.floor(b);
  return { x: fa, y: 0.18 + fb * 0.66, s: 1 + ((i * 7) % 3) * 0.5, o: 0.25 + ((i * 13) % 5) * 0.1 };
});

export function StockSheet(p: Props) {
  const { width, height, ky, trackFade, reveal, scroll, active, bg, desc, press, commit, pan, onCard, onChoose, onPressIn, onPressOut } = p;
  const y = (screenY: number) => (screenY - 62) * ky; // pin screen y → sheet y (the pin's sheet starts at 62)
  const yb = (fromBottom: number) => height - fromBottom; // bottom-anchored

  const skeleton = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.max(0, reveal.get())) }));
  const content = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, reveal.get())) * (1 - Math.min(1, commit.get() * (2540 / 160))),
  }));
  const track = useAnimatedStyle(() => ({ opacity: trackFade.get() }));
  const cardX = (width - STOCK.cardW) / 2;
  const cardTop = y(STOCK.cardTop);

  return (
    <View style={[styles.sheet, { width, height }]}>
      {/* skeleton */}
      <Animated.View style={[StyleSheet.absoluteFill, skeleton]} pointerEvents="none">
        {SKELETON.title.map((t) => (
          <View key={t.y} style={[styles.bone, { top: y(t.y), left: (width - t.w) / 2, width: t.w, height: SKELETON.titleH, borderRadius: 7 }]} />
        ))}
        <View style={[styles.bone, { top: cardTop, left: cardX, width: STOCK.cardW, height: STOCK.cardH, borderRadius: STOCK.cardRadius }]} />
        <View
          style={[
            styles.bone,
            {
              top: cardTop,
              left: cardX,
              width: STOCK.cardW,
              height: STOCK.cardH,
              borderRadius: STOCK.cardRadius,
              // the same pose a real card has one step to the right
              transform: [{ translateX: STOCK.spacing }, { skewX: `${STOCK.sideSkew}deg` }, { scale: STOCK.sideScale }],
            },
          ]}
        />
        <View style={[styles.bone, { top: y(SKELETON.dots.y), left: (width - SKELETON.dots.w) / 2, width: SKELETON.dots.w, height: SKELETON.dots.h, borderRadius: 4 }]} />
        {SKELETON.desc.map((d) => (
          <View key={d.y} style={[styles.bone, { top: yb(874 - d.y), left: (width - d.w) / 2, width: d.w, height: SKELETON.descH, borderRadius: 3 }]} />
        ))}
        <View style={[styles.bone, { top: height - BUTTON.bottom - BUTTON.height, left: BUTTON.side, right: BUTTON.side, height: BUTTON.height, borderRadius: BUTTON.radius }]} />
      </Animated.View>

      {/* content */}
      <Animated.View style={[StyleSheet.absoluteFill, content]}>
        {STOCKS.map((s, i) => (
          <Backdrop key={s.id} stock={s} index={i} width={width} height={height} opacity={bg[i]} />
        ))}
        {DUST.map((d, i) => (
          <View key={i} pointerEvents="none" style={[styles.dust, { left: d.x * width, top: d.y * height, width: d.s, height: d.s, borderRadius: d.s, opacity: d.o }]} />
        ))}

        <Animated.Text style={[styles.title, { top: y(STOCK.titleY[0]) - 17 }]} accessibilityRole="header">
          {`Choose your\n${GIFT_AMOUNT} gift stock`}
        </Animated.Text>

        <GestureDetector gesture={pan}>
          <Animated.View style={[styles.track, { top: cardTop - 20, height: STOCK.cardH + 40 }, track]}>
            {STOCKS.map((s, i) => (
              <Card key={s.id} stock={s} index={i} left={cardX} scroll={scroll} reveal={reveal} onPress={() => onCard(i)} />
            ))}
          </Animated.View>
        </GestureDetector>

        <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
          <View style={[styles.dots, { top: y(STOCK.dotsY) - STOCK.dot / 2 }]} accessibilityElementsHidden pointerEvents="none">
            {STOCKS.map((s, i) => (
              <Dot key={s.id} index={i} active={active} />
            ))}
          </View>
          {STOCKS.map((s, i) => (
            <Description key={s.id} stock={s} opacity={desc[i]} top={yb(STOCK.descBottom) - STOCK.descLine / 2} />
          ))}
        </View>

        <ChooseButton top={height - BUTTON.bottom - BUTTON.height} active={active} press={press} onPress={onChoose} onPressIn={onPressIn} onPressOut={onPressOut} />
      </Animated.View>
    </View>
  );
}

function Backdrop({ stock, index, width, height, opacity }: { stock: Stock; index: number; width: number; height: number; opacity: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: stock.backdropTint }, style]} pointerEvents="none">
      <BackdropField index={index} width={width} height={height} />
    </Animated.View>
  );
}

type CardProps = { stock: Stock; index: number; left: number; scroll: SharedValue<number>; reveal: SharedValue<number>; onPress: () => void };

function Card({ stock, index, left, scroll, reveal, onPress }: CardProps) {
  const style = useAnimatedStyle(() => {
    const d = index - scroll.get();
    const side = Math.max(-1, Math.min(1, d));
    const abs = Math.abs(side);
    return {
      opacity: Math.abs(d) > 2.2 ? 0 : 1,
      zIndex: Math.round(10 - Math.abs(d) * 2),
      transform: [
        { translateX: d * STOCK.spacing },
        { skewX: `${side * STOCK.sideSkew}deg` },
        { scale: 1 - (1 - STOCK.sideScale) * abs },
      ],
    };
  });
  const logo = useAnimatedStyle(() => {
    const k = Math.min(1, Math.max(0, reveal.get() - 1));
    return { opacity: k, transform: [{ scale: interpolate(k, [0, 1], [0.9, 1]) }] };
  });
  // the art comes up from dim while the skeleton dissolves, as in the pin
  const art = useAnimatedStyle(() => ({ opacity: interpolate(reveal.get(), [0, 1, 2], [0.4, 0.75, 1]) }));
  return (
    <Animated.View style={[styles.card, { left, top: 20 }, style]}>
      <Pressable pressRetentionOffset={TOUCH.retention} style={StyleSheet.absoluteFill} onPress={onPress} accessibilityRole="button" accessibilityLabel={stockLabel(stock)}>
        <CardFace stock={stock} logoStyle={logo} artStyle={art} />

      </Pressable>
    </Animated.View>
  );
}

export function stockLabel(stock: Stock) {
  return `${stock.name}, ${stock.change > 0 ? 'up' : 'down'} ${Math.abs(stock.change)}% past year`;
}

// The front of a stock card.
export function CardFace({ stock, logoStyle, artStyle }: { stock: Stock; logoStyle?: object; artStyle?: object }) {
  const up = stock.change > 0;
  return (
    <>
      <Animated.View style={[StyleSheet.absoluteFill, artStyle]}>
        <Image source={stock.card} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
      </Animated.View>
      <View style={styles.cardRim} />
      <Animated.View style={[styles.logo, { backgroundColor: stock.markBg }, logoStyle]}>
        <BrandMark kind={stock.mark} size={STOCK.logo} />
      </Animated.View>
      <Text style={styles.name} numberOfLines={1}>
        {stock.name}
      </Text>
      <Text style={[styles.pct, { color: up ? COLORS.up : COLORS.down }]}>{`${up ? '\u2191' : '\u2193'} ${Math.abs(stock.change)}% past year`}</Text>
    </>
  );
}

function Dot({ index, active }: { index: number; active: SharedValue<number> }) {
  // the pin switches the dot in one frame, with the label
  const style = useAnimatedStyle(() => ({ opacity: active.get() === index ? 1 : 0.38 }));
  return <Animated.View style={[styles.dot, style]} />;
}

function Description({ stock, opacity, top }: { stock: Stock; opacity: SharedValue<number>; top: number }) {
  const style = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return (
    <Animated.View style={[styles.desc, { top }, style]} pointerEvents="none">
      <Text style={styles.descText} numberOfLines={1}>
        {stock.lines[0]}
      </Text>
      <View style={styles.descRow}>
        <Text style={styles.descText} numberOfLines={1}>
          {stock.lines[1]}
          {stock.tail.split('').map((c, i) => (
            <Text key={i} style={{ color: `rgba(255,255,255,${0.5 - i * 0.3})` }}>
              {c}
            </Text>
          ))}
        </Text>
        <Text style={[styles.descText, styles.more]}>more</Text>
      </View>
    </Animated.View>
  );
}

function ChooseButton({ top, active, press, onPress, onPressIn, onPressOut }: { top: number; active: SharedValue<number>; press: SharedValue<number>; onPress: () => void; onPressIn: () => void; onPressOut: () => void }) {
  const scale = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - (1 - PRESS.scale) * press.get() }],
  }));
  return (
    <Animated.View style={[styles.choose, { top }, scale]}>
      <Pressable pressRetentionOffset={TOUCH.retention} style={styles.hit} onPress={onPress} onPressIn={onPressIn} onPressOut={onPressOut} accessibilityRole="button" accessibilityLabel="Choose this stock">
        {STOCKS.map((s, i) => (
          <ChooseLabel key={s.id} name={s.name} index={i} active={active} />
        ))}
      </Pressable>
    </Animated.View>
  );
}

function ChooseLabel({ name, index, active }: { name: string; index: number; active: SharedValue<number> }) {
  // the pin switches the label in one frame, at the moment the next card is picked
  const style = useAnimatedStyle(() => ({ opacity: active.get() === index ? 1 : 0 }));
  return <Animated.Text style={[styles.chooseLabel, style]}>{`Choose ${name}`}</Animated.Text>;
}

const styles = StyleSheet.create({
  sheet: { backgroundColor: COLORS.skeletonBg, overflow: 'hidden' },
  bone: { position: 'absolute', backgroundColor: COLORS.skeleton, borderCurve: 'continuous' },
  dust: { position: 'absolute', backgroundColor: '#ffffff' },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: STOCK.titleSize,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.4,
    color: COLORS.white,
  },
  track: { position: 'absolute', left: 0, right: 0 },
  card: {
    position: 'absolute',
    width: STOCK.cardW,
    height: STOCK.cardH,
    borderRadius: STOCK.cardRadius,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#101018',
  },
  cardRim: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: STOCK.cardRadius,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 255, 255, 0.22)',
  },
  logo: {
    position: 'absolute',
    left: (STOCK.cardW - STOCK.logo) / 2,
    top: STOCK.logoY - STOCK.logo / 2,
    width: STOCK.logo,
    height: STOCK.logo,
    borderRadius: STOCK.logo / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: {
    position: 'absolute',
    left: 6,
    right: 6,
    top: STOCK.nameY - 17,
    textAlign: 'center',
    fontSize: STOCK.nameSize,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.3,
    color: COLORS.white,
  },
  pct: { position: 'absolute', left: 0, right: 0, top: STOCK.pctY - 10, textAlign: 'center', fontSize: STOCK.pctSize, lineHeight: 20 },
  dots: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: STOCK.dotGap - STOCK.dot },
  dot: { width: STOCK.dot, height: STOCK.dot, borderRadius: STOCK.dot / 2, backgroundColor: COLORS.white },
  desc: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  descRow: { flexDirection: 'row', alignItems: 'center' },
  descText: { fontSize: STOCK.descSize, lineHeight: STOCK.descLine, color: 'rgba(255, 255, 255, 0.86)' },
  more: { marginLeft: 6, textDecorationLine: 'underline', color: COLORS.white },
  choose: {
    position: 'absolute',
    left: BUTTON.side,
    right: BUTTON.side,
    height: BUTTON.height,
    borderRadius: BUTTON.radius,
    borderCurve: 'continuous',
    backgroundColor: COLORS.choose,
  },
  hit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  chooseLabel: { position: 'absolute', fontSize: BUTTON.font, color: COLORS.chooseInk },
});
