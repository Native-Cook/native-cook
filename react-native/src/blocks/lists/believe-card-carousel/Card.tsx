import { BlurMask, Canvas, Circle, LinearGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { LinearGradient as Gradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle as SvgCircle, Path } from 'react-native-svg';

import type { CardData } from './cards';
import { BrandMark } from './BrandMark';
import { PixelBowl } from './PixelBowl';
import { card, colors, fonts, row } from './theme';

const IW = card.width - 2 * card.inset;
const IH = card.imageHeight;

export function Picture({ data, glowShift = 0 }: { data: CardData; glowShift?: number }) {
  const [top, mid, bottom, glow] = data.image;
  return (
    <Canvas style={styles.picture}>
      <RoundedRect x={0} y={0} width={IW} height={IH} r={card.imageRadius}>
        <LinearGradient start={vec(0, 0)} end={vec(0, IH)} colors={[top, mid, bottom]} positions={[0, 0.55, 1]} />
      </RoundedRect>
      {/* the soft light behind the icon */}
      <Circle cx={IW / 2 + glowShift} cy={IH / 2} r={card.glowR} color={glow}>
        <BlurMask blur={card.glowBlur} style="normal" />
      </Circle>
    </Canvas>
  );
}

export function Icon({ data }: { data: CardData }) {
  if (data.art === 'mark') {
    return (
      <View style={[styles.icon, styles.iconShadow]}>
        <Gradient colors={['#00EA76', '#01D454']} style={[StyleSheet.absoluteFill, styles.iconClip]} />
        <View style={styles.markGlow}>
          <BrandMark size={62} color="#FFFFFF" opacity={0.86} />
        </View>
      </View>
    );
  }
  if (data.art === 'figo') {
    return (
      <View style={[styles.icon, styles.iconShadow]}>
        <View style={[StyleSheet.absoluteFill, styles.iconClip]}>
          <PixelBowl size={card.icon} />
        </View>
        <Text style={styles.figoWord}>a r o</Text>
      </View>
    );
  }
  return (
    <View style={[styles.icon, styles.iconClip, { backgroundColor: '#B2F6FD' }]}>
      <Svg width={100} height={100} viewBox="0 0 86 86">
        <SvgCircle cx={28} cy={30} r={15} stroke="#000" strokeWidth={13} fill="none" />
        <SvgCircle cx={56} cy={58} r={15} stroke="#000" strokeWidth={13} fill="none" />
        <Path d="M66 12 L18 76" stroke="#000" strokeWidth={13} strokeLinecap="round" />
      </Svg>
    </View>
  );
}

// `textShift` moves the text block sideways: the pin's side cards show it offset from their art
// (the cards are drawn in slight perspective there), measured per card.
export function Card({
  data,
  textShift = 0,
  iconShift = 0,
  textDy = 0,
  inPerspective = false,
}: {
  data: CardData;
  textShift?: number;
  iconShift?: number;
  textDy?: number;
  inPerspective?: boolean;
}) {
  const pic = row.picture;
  const pictureTilt = inPerspective
    ? [
        { translateX: pic.dx },
        { translateY: pic.dy },
        { perspective: pic.perspective },
        { rotate: `${pic.rotate}deg` },
        { rotateX: `${pic.rotateX}deg` },
        { rotateY: `${pic.rotateY}deg` },
        { skewX: `${pic.skewX}deg` },
        { scale: pic.scale },
      ]
    : [];
  return (
    <View style={styles.card}>
      <View style={[styles.pictureBox, { transform: pictureTilt }]}>
        <Picture data={data} glowShift={iconShift} />
        <View style={[styles.iconBox, { transform: [{ translateX: iconShift }] }]}>
          <Icon data={data} />
        </View>
      </View>
      <Text style={[styles.believe, { transform: [{ translateX: textShift }, { translateY: textDy }] }]}>
        I believe in
      </Text>
      <Text style={[styles.name, { transform: [{ translateX: textShift }, { translateY: textDy }] }]} numberOfLines={1}>
        {data.name}
      </Text>
      <View
        style={[styles.meta, { top: card.dateTop, transform: [{ translateX: textShift }, { translateY: textDy }] }]}
      >
        <Text style={styles.arrow}>→</Text>
        <Text style={styles.metaText}>{data.date}</Text>
      </View>
      <View style={[styles.meta, { top: card.byTop, transform: [{ translateX: textShift }, { translateY: textDy }] }]}>
        {data.art === 'mark' ? (
          <View style={styles.byMark}>
            <BrandMark size={9} color="#FFFFFF" />
          </View>
        ) : data.avatar ? (
          <Image source={data.avatar} style={styles.avatar} />
        ) : (
          <View style={[styles.avatar, { backgroundColor: '#E6C9B5' }]} />
        )}
        <Text style={styles.metaText}>{data.by}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: card.width,
    height: card.height,
    borderRadius: card.radius,
    borderCurve: 'continuous',
    backgroundColor: colors.card,
    shadowColor: '#000',
    shadowOpacity: 0.07,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
  },
  pictureBox: { position: 'absolute', left: card.inset, top: card.inset, width: IW, height: IH },
  picture: { width: IW, height: IH },
  iconBox: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  icon: { width: card.icon, height: card.icon, alignItems: 'center', justifyContent: 'center' },
  iconClip: { borderRadius: card.iconRadius, borderCurve: 'continuous', overflow: 'hidden' },
  iconShadow: {
    borderRadius: card.iconRadius,
    shadowColor: '#0B5E33',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
  },
  markGlow: { shadowColor: '#0A7A3C', shadowOpacity: 0.35, shadowRadius: 3, shadowOffset: { width: 0, height: 2 } },
  figoWord: {
    position: 'absolute',
    bottom: 12,
    color: '#FFFFFF',
    fontSize: 21,
    fontFamily: fonts.extrabold,
    letterSpacing: 1,
  },
  believe: {
    position: 'absolute',
    left: card.pad,
    top: card.believeTop,
    fontSize: 15,
    fontFamily: fonts.semibold,
    color: colors.mute,
  },
  name: {
    position: 'absolute',
    left: card.pad,
    right: 12,
    top: card.nameTop,
    fontSize: 26,
    fontFamily: fonts.extrabold,
    color: colors.ink,
    letterSpacing: -0.5,
  },
  meta: { position: 'absolute', left: card.pad, flexDirection: 'row', alignItems: 'center', gap: 8 },
  arrow: { width: 16, textAlign: 'center', fontSize: 12, fontFamily: fonts.semibold, color: colors.ink },
  metaText: { fontSize: 14, fontFamily: fonts.medium, color: colors.meta, fontVariant: ['tabular-nums'] },
  byMark: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#00D45F',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatar: { width: 16, height: 16, borderRadius: 8 },
});
