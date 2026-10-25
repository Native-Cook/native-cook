import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import Svg, { ClipPath, Defs, G, Image as SvgImage, Circle, LinearGradient as SvgGradient, Path, Polygon, Rect, Stop, Text as SvgText } from 'react-native-svg';

import { CARD } from './postcardMotion';

// The postcard's two faces as static views: the writing side (note, address, stamp, a dog-eared
// corner showing the photo) and the photo side. Only their container moves.

export const PHOTO = require('./assets/lisbon.jpg');
const SCRIPT = Platform.select({ ios: 'Georgia', default: 'serif' });
const PAPER = '#F4ECDA';
const INK = '#26305A';

/** A colourful invented stamp: a yellow tram climbing past tiles at sunset, with perforations. */
const Stamp = memo(function Stamp() {
  const w = 70;
  const h = 84;
  const holes: [number, number][] = [];
  for (let x = 3.5; x < w; x += 7) holes.push([x, 0], [x, h]);
  for (let y = 3.5; y < h; y += 7) holes.push([0, y], [w, y]);
  return (
    <Svg width={w} height={h}>
      <Defs>
        <SvgGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#F6CE7A" />
          <Stop offset="0.55" stopColor="#EE8B50" />
          <Stop offset="1" stopColor="#C4523F" />
        </SvgGradient>
        <ClipPath id="art">
          <Rect x={6} y={6} width={w - 12} height={h - 12} />
        </ClipPath>
      </Defs>
      <Rect x={0} y={0} width={w} height={h} fill="#FBF7EE" />
      {holes.map(([x, y], i) => (
        <Circle key={i} cx={x} cy={y} r={2.5} fill={PAPER} />
      ))}
      <G clipPath="url(#art)">
        <Rect x={6} y={6} width={w - 12} height={h - 12} fill="url(#sky)" />
        <Circle cx={47} cy={30} r={9} fill="#FFE3A0" opacity={0.9} />
        {/* Rooftops on the hill. */}
        <Path d="M6 52 L16 44 L24 49 L33 41 L43 48 L52 43 L64 50 L64 62 L6 62 Z" fill="#B4463A" />
        <Path d="M6 56 L18 50 L30 55 L42 49 L64 56 L64 64 L6 64 Z" fill="#8E3530" />
        {/* The tram on its rails. */}
        <Path d="M40 37 L33 47" stroke="#3A2A2A" strokeWidth={0.8} />
        <Rect x={17} y={47} width={32} height={15} rx={3} fill="#F2C230" />
        <Rect x={17} y={56} width={32} height={2.2} fill="#E3A51E" />
        {[20, 27, 34, 41].map((x) => (
          <Rect key={x} x={x} y={49.5} width={5} height={5} rx={1} fill="#FFF4D6" />
        ))}
        <Rect x={6} y={64} width={w - 12} height={14} fill="#2C5AA6" />
        {[9, 21, 33, 45, 57].map((x) => (
          <G key={x}>
            <Rect x={x} y={66} width={9} height={9} fill="#F4F1E8" />
            <Circle cx={x + 4.5} cy={70.5} r={3} fill="#2C5AA6" />
            <Circle cx={x + 4.5} cy={70.5} r={1.2} fill="#F4F1E8" />
          </G>
        ))}
        <SvgText x={9} y={15} fontSize={6.2} fontWeight="700" fill="#FFF6E2" letterSpacing={0.6}>
          POSTA LUSA
        </SvgText>
        <SvgText x={w - 9} y={42} fontSize={8} fontWeight="800" fill="#FFF6E2" textAnchor="end">
          0,85 €
        </SvgText>
      </G>
    </Svg>
  );
});

/** The bottom-right corner, folded back: a triangle of the photo side with a crease shadow. */
const DogEar = memo(function DogEar() {
  const s = 34;
  return (
    <Svg width={s} height={s} style={styles.ear}>
      <Defs>
        <ClipPath id="ear">
          <Polygon points={`${s},0 ${s},${s} 0,${s}`} />
        </ClipPath>
        <SvgGradient id="crease" x1="0" y1="1" x2="1" y2="0">
          <Stop offset="0.42" stopColor="#000000" stopOpacity="0" />
          <Stop offset="0.5" stopColor="#000000" stopOpacity="0.35" />
          <Stop offset="0.56" stopColor="#000000" stopOpacity="0" />
        </SvgGradient>
      </Defs>
      <Polygon points={`${s},0 ${s},${s} 0,${s}`} fill="#1E3150" />
      <SvgImage href={PHOTO} x={-60} y={-30} width={110} height={74} preserveAspectRatio="xMidYMid slice" clipPath="url(#ear)" />
      <Polygon points={`${s},0 0,${s} 4,${s} ${s},4`} fill="url(#crease)" />
      <Path d={`M${s} 0 L0 ${s}`} stroke="rgba(255,255,255,0.7)" strokeWidth={0.8} />
    </Svg>
  );
});

export const WritingSide = memo(function WritingSide() {
  return (
    <View style={styles.face}>
      <LinearGradient colors={['#F8F2E4', PAPER, '#EADFC6']} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      <Text style={styles.note}>
        Day four. Rode the yellow tram all the way up to the castle, then got lost in Alfama on purpose. Custard tarts still warm — I have had three.
      </Text>
      <Text style={styles.sign}>— J.</Text>
      <View style={styles.divider} />
      <View style={styles.stamp}>
        <Stamp />
      </View>
      {['Ines Marlowe', '14 Wren Lane', 'Bristol BS8 4QT', 'United Kingdom'].map((line, i) => (
        <View key={line} style={[styles.addressLine, { top: 120 + i * 25 }]}>
          <Text style={styles.address}>{line}</Text>
        </View>
      ))}
      <Text style={styles.printed}>POST CARD · CARTE POSTALE</Text>
      <DogEar />
    </View>
  );
});

export const PhotoSide = memo(function PhotoSide() {
  return (
    <View style={[styles.face, styles.photoFace]}>
      <Image source={PHOTO} style={styles.photo} contentFit="cover" />
      <LinearGradient colors={['rgba(20,24,40,0)', 'rgba(20,24,40,0.55)']} start={{ x: 0, y: 0.45 }} end={{ x: 0, y: 1 }} style={styles.photoShade} />
      <Text style={styles.greeting}>Lisboa</Text>
      <Text style={styles.greetingSub}>ALFAMA ROOFTOPS · TEJO</Text>
    </View>
  );
});

const styles = StyleSheet.create({
  face: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD.w,
    height: CARD.h,
    borderRadius: 6,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: PAPER,
  },
  note: {
    position: 'absolute',
    left: 16,
    top: 16,
    width: 160,
    color: INK,
    fontFamily: SCRIPT,
    fontStyle: 'italic',
    fontSize: 13,
    lineHeight: 20,
  },
  sign: { position: 'absolute', left: 112, top: 186, color: INK, fontFamily: SCRIPT, fontStyle: 'italic', fontSize: 15 },
  divider: { position: 'absolute', left: 188, top: 22, bottom: 22, width: 1, backgroundColor: 'rgba(90,70,50,0.28)' },
  stamp: { position: 'absolute', left: 262, top: 14, boxShadow: '0 1px 1.5px rgba(40,25,10,0.35)' },
  addressLine: { position: 'absolute', left: 200, right: 14, height: 22, borderBottomWidth: 1, borderColor: 'rgba(90,70,50,0.25)', justifyContent: 'flex-end' },
  address: { color: INK, fontFamily: SCRIPT, fontStyle: 'italic', fontSize: 14, lineHeight: 20, paddingLeft: 4 },
  printed: { position: 'absolute', left: 200, bottom: 10, color: 'rgba(90,70,50,0.5)', fontSize: 7, fontWeight: '700', letterSpacing: 1.4 },
  ear: { position: 'absolute', right: 0, bottom: 0 },
  photoFace: { backgroundColor: '#FBF8F1', padding: 8, transform: [{ rotateY: '180deg' }] },
  photo: { flex: 1, borderRadius: 2 },
  photoShade: { position: 'absolute', left: 8, right: 8, bottom: 8, height: 110, borderRadius: 2 },
  greeting: {
    position: 'absolute',
    left: 22,
    bottom: 34,
    color: '#FFF7E8',
    fontFamily: SCRIPT,
    fontStyle: 'italic',
    fontWeight: '700',
    fontSize: 34,
    textShadowColor: 'rgba(0,0,0,0.45)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  greetingSub: { position: 'absolute', left: 24, bottom: 20, color: 'rgba(255,247,232,0.85)', fontSize: 9, fontWeight: '700', letterSpacing: 1.6 },
});
