import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Ellipse, G, Path, Polygon, Rect } from 'react-native-svg';

import { COVER, FONTS } from './theme';

// Book covers, designed in code at 200 x 300 pt and scaled by their parent. Titles, authors and art are invented
// stand-ins for the pin's real books, in the same graphic language (flat shapes, poster type).

export type CoverKind = 'loud' | 'seeing' | 'objects' | 'pages' | 'sky' | 'arches' | 'grid' | 'lines' | 'shapes';

export const COVER_TITLES: Record<CoverKind, string> = {
  loud: 'Loud Type',
  seeing: 'The Life of Colour',
  objects: 'The Shape of Everyday Objects',
  pages: 'Pages & Margins',
  sky: 'Blue Hours',
  arches: 'Soft Geometry',
  grid: 'Grid Works',
  lines: 'Flow Lines',
  shapes: 'Small Rooms',
};

const SKY = require('./assets/cover-sky.png');

export function Cover({ kind }: { kind: CoverKind }) {
  return <View style={styles.cover}>{art(kind)}</View>;
}

function art(kind: CoverKind) {
  switch (kind) {
    case 'loud':
      // the pin's "Extra Bold" cover: pink block over a yellow diagonal on red, huge outlined letters
      return (
        <>
          <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
            <Rect width={200} height={300} fill="#EE3A0A" />
            <Polygon points="0,170 170,98 170,135 108,300 0,300" fill="#F6C80E" />
            <Polygon points="0,0 170,0 170,98 0,170" fill="#F2A3BC" />
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <Rect key={i} x={118} y={112 + i * 9} width={i % 3 === 2 ? 34 : 46} height={4} fill="#2A1A14" />
            ))}
            {[0, 1, 2, 3, 4].map((i) => (
              <Rect key={`b${i}`} x={12} y={232 + i * 8} width={i % 2 ? 46 : 58} height={3} fill="#2A1A14" />
            ))}
          </Svg>
          <Text style={[styles.outlined, { top: 14, left: 12 }]}>{'EXTRA\nLOUD'}</Text>
        </>
      );
    case 'seeing':
      return (
        <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
          <Rect width={200} height={300} fill="#E9B200" />
          {[98, 196].map((cy, i) => (
            <G key={cy}>
              <Path d={`M30 ${cy} Q100 ${cy - 52} 170 ${cy} Q100 ${cy + 52} 30 ${cy} Z`} fill="#FFFDF6" />
              <Circle cx={100} cy={cy} r={24} fill={i === 0 ? '#2D55D6' : '#E2321E'} />
              <Circle cx={100} cy={cy} r={11} fill="#16161A" />
            </G>
          ))}
        </Svg>
      );
    case 'objects':
      // yellow, a thin spaced title, a red pot silhouette standing at the lower left
      return (
        <>
          <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
            <Rect width={200} height={300} fill="#E2C81C" />
            <Path
              d="M28 300 L30 262 Q24 236 46 214 L52 184 Q49 172 58 166 L60 152 L66 152 L68 166 Q77 172 74 184 L80 214 Q92 226 96 244 L104 228 Q118 204 132 214 Q140 230 120 252 Q108 266 104 276 L106 300 Z"
              fill="#C2401C"
            />
            <Path d="M104 236 Q126 206 136 220 Q142 240 112 262" stroke="#2A1A10" strokeWidth={2.4} fill="none" />
            <Rect x={132} y={196} width={40} height={3} fill="#8D7A3A" />
            <Rect x={140} y={204} width={32} height={3} fill="#8D7A3A" />
          </Svg>
          <Text style={[styles.thinTitle, { top: 40 }]}>{'SHAPE\nof EVERYDAY\nOBJECTS'}</Text>
          <Text style={[styles.thinSmall, { top: 40, left: 14 }]}>The</Text>
        </>
      );
    case 'pages':
      return (
        <>
          <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
            <Rect width={200} height={300} fill="#EC3A1E" />
            {[0, 1, 2].map((i) => (
              <Rect key={i} x={40} y={196 + i * 9} width={120} height={3} fill="#F7B8A8" />
            ))}
            <Rect x={58} y={252} width={86} height={7} fill="#FFFFFF" />
          </Svg>
          <Text style={[styles.slabTitle, { top: 34 }]}>{'Layout\nof\nBooks'}</Text>
          <Text style={[styles.thinSmall, { top: 24, left: 92, color: '#FFFFFF' }]}>The</Text>
        </>
      );
    case 'sky':
      return (
        <>
          <Image source={SKY} style={StyleSheet.absoluteFill} contentFit="cover" />
          <Text style={[styles.skyTitle, { top: 18, left: 30 }]}>THE</Text>
          <Text style={[styles.skyTitle, { top: 50, left: 70 }]}>SKY</Text>
          <Text style={[styles.skyTitle, { top: 82, left: 112 }]}>DAYS</Text>
        </>
      );
    case 'arches':
      return (
        <>
          <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
            <Rect width={200} height={300} fill="#3A6FE0" />
            <Rect x={0} y={0} width={96} height={86} fill="#2FA05C" />
            <Path d="M0 300 L0 150 Q0 98 48 98 Q96 98 96 150 L96 300 Z" fill="#14603C" />
            <Circle cx={148} cy={52} r={48} fill="#F1EEE6" />
            <Path d="M100 120 A48 48 0 0 0 196 120 Z" fill="#A9C6F2" />
            <Path d="M100 170 A48 48 0 0 0 196 170 Z" fill="#A9C6F2" />
            <Path d="M100 220 A48 48 0 0 0 196 220 Z" fill="#A9C6F2" />
          </Svg>
          <View style={styles.labelBox}>
            <Text style={styles.labelText}>{'MARA\nOKONJO'}</Text>
          </View>
        </>
      );
    case 'grid':
      return (
        <>
          <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
            <Rect width={200} height={300} fill="#F2C21A" />
            <Path d="M120 40 Q160 30 176 60 M150 120 L196 96" stroke="#1A1410" strokeWidth={3} fill="none" />
          </Svg>
          <Text style={[styles.anton, styles.slanted, { color: '#E2231A' }]}>{'CRASHING'}</Text>
        </>
      );
    case 'lines':
      return (
        <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
          <Rect width={200} height={300} fill="#5EC3F2" />
          {[40, 90, 140, 190, 240].map((y, i) => (
            <Path
              key={y}
              d={`M-10 ${y} C 40 ${y - 40}, 80 ${y + 50}, 120 ${y} S 190 ${y - 30}, 220 ${y + 10}`}
              stroke={i % 2 ? '#1F3FA8' : '#0E2B78'}
              strokeWidth={10}
              fill="none"
              strokeLinecap="round"
            />
          ))}
          <Rect x={18} y={226} width={70} height={46} fill="#FFFFFF" />
        </Svg>
      );
    default:
      // shapes
      return (
        <Svg width={COVER.w} height={COVER.h} style={StyleSheet.absoluteFill}>
          <Rect width={200} height={300} fill="#F6A6C8" />
          <Rect x={0} y={170} width={200} height={130} fill="#FFD43A" />
          <Rect x={24} y={30} width={86} height={110} fill="#F04A3A" />
          <Rect x={110} y={196} width={64} height={70} fill="#FFFFFF" />
          {[0, 1, 2, 3, 4].map((i) => (
            <Rect key={i} x={120} y={206 + i * 11} width={44} height={4} fill="#2B2B2B" />
          ))}
          <Ellipse cx={150} cy={86} rx={26} ry={26} fill="#FFFFFF" opacity={0.8} />
        </Svg>
      );
  }
}

/** The open book's left page: a print-like abstract pattern in the cover's palette. */
export function SpreadArt() {
  return (
    <Svg width={COVER.w} height={COVER.h} viewBox="0 0 100 146" preserveAspectRatio="none">
      <Rect width={100} height={146} fill="#FBF6EA" />
      <Rect x={6} y={6} width={42} height={42} fill="#F7C531" />
      <Circle cx={74} cy={27} r={20} fill="#2D55D6" />
      <Path d="M8 96 A40 40 0 0 1 88 96 Z" fill="#E2321E" />
      <Path
        d="M50 104 L54 118 L68 114 L58 124 L68 134 L54 130 L50 144 L46 130 L32 134 L42 124 L32 114 L46 118 Z"
        fill="#16161A"
      />
      <Circle cx={18} cy={126} r={9} fill="#2FA67A" />
      <Rect x={74} y={110} width={20} height={30} fill="#F29FC0" />
      <Path d="M58 52 L94 52 L76 82 Z" fill="#16161A" />
      <Circle cx={26} cy={68} r={10} fill="none" stroke="#16161A" strokeWidth={4} />
    </Svg>
  );
}

/** A text page: a heading and grey lines. */
export function SpreadText() {
  return (
    <Svg width={COVER.w} height={COVER.h} viewBox="0 0 100 146" preserveAspectRatio="none">
      <Rect width={100} height={146} fill="#FFFFFF" />
      <Rect x={10} y={14} width={40} height={4} fill="#2B2B2B" />
      {Array.from({ length: 9 }, (_, i) => (
        <Rect key={i} x={10} y={28 + i * 7} width={i % 4 === 3 ? 52 : 78} height={2.4} fill="#9A9AA2" />
      ))}
    </Svg>
  );
}

/** The page under the art when the reader turns back: a black-and-white plate. */
export function SpreadPlate() {
  return (
    <Svg width={COVER.w} height={COVER.h} viewBox="0 0 100 146" preserveAspectRatio="none">
      <Rect width={100} height={146} fill="#D9D9D9" />
      <Rect x={8} y={10} width={84} height={112} fill="#8E8E8E" />
      <Circle cx={50} cy={58} r={22} fill="#C9C9C9" />
      <Path d="M18 122 Q50 76 82 122 Z" fill="#6E6E6E" />
      <Rect x={8} y={130} width={50} height={3} fill="#555555" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  cover: { width: COVER.w, height: COVER.h, overflow: 'hidden' },
  anton: { position: 'absolute', fontFamily: FONTS.poster, includeFontPadding: false },
  vertical: {
    fontSize: 70,
    lineHeight: 74,
    width: 300,
    top: 113,
    left: -64,
    transform: [{ rotate: '-90deg' }],
    textAlign: 'center',
  },
  outlined: {
    position: 'absolute',
    fontFamily: FONTS.poster,
    fontSize: 52,
    lineHeight: 50,
    color: '#FBD7E2',
    textShadowColor: '#E8456A',
    textShadowRadius: 1.5,
  },
  thinTitle: { position: 'absolute', left: 30, right: 6, fontSize: 18, lineHeight: 26, letterSpacing: 2.5, color: '#6A5A2A', textAlign: 'center' },
  thinSmall: { position: 'absolute', fontFamily: FONTS.serif, fontStyle: 'italic', fontSize: 12, color: '#6A5A2A' },
  slabTitle: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FONTS.serif,
    fontWeight: '700',
    fontSize: 40,
    lineHeight: 46,
    color: '#FFFFFF',
  },
  skyTitle: { position: 'absolute', fontFamily: FONTS.serif, fontStyle: 'italic', fontSize: 26, color: '#FFFFFF', letterSpacing: 2 },
  slanted: {
    fontSize: 96,
    lineHeight: 100,
    width: 360,
    top: 100,
    left: -70,
    transform: [{ rotate: '-72deg' }],
    textAlign: 'center',
  },
  serifTitle: { position: 'absolute', fontFamily: FONTS.serif, fontSize: 24, lineHeight: 27, letterSpacing: 0.4 },
  serifBold: { position: 'absolute', fontFamily: FONTS.serif, fontWeight: '700', color: '#FFFFFF' },
  serifItalic: {
    position: 'absolute',
    fontFamily: FONTS.serif,
    fontStyle: 'italic',
    fontSize: 44,
    lineHeight: 44,
    color: '#FFFFFF',
  },
  small: { position: 'absolute', fontSize: 10, lineHeight: 13, fontWeight: '600', letterSpacing: 0.2 },
  labelBox: { position: 'absolute', left: 0, top: 250, width: 120, height: 50, padding: 8, backgroundColor: '#FFFDF7' },
  labelText: { fontFamily: FONTS.serif, fontSize: 17, lineHeight: 18, color: '#23232A', letterSpacing: 1 },
  labelSub: { marginTop: 4, fontSize: 9, color: '#55555C' },
});
