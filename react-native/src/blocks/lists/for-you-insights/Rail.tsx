import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { ScrollView } from 'react-native-gesture-handler';
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Stop, Text as SvgText } from 'react-native-svg';

import { colors, layout, radius, type } from './theme';

/** The context rail: four glass tiles in a horizontal scroll (the fourth runs off the edge, as in the pin). */
export function Rail({ k, y }: { k: number; y: (ref: number) => number }) {
  const top = y(layout.railTop);
  return (
    <View style={{ position: 'absolute', left: 0, right: 0, top, height: layout.railHeight * k }}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: layout.railInset * k, gap: layout.railGap * k }}
        accessibilityLabel="Today's context"
      >
        {layout.tiles.map((tile) => (
          <View
            key={tile.kind}
            accessible
            accessibilityLabel={'title' in tile ? `${tile.title}: ${tile.value}` : 'Heart health'}
            style={[
              styles.tile,
              {
                width: tile.width * k,
                height: layout.railHeight * k,
                borderRadius: radius.tile * k,
                backgroundColor: tile.kind === 'heart' ? colors.tileHeart : colors.tile,
                borderWidth: tile.kind === 'heart' ? 0 : StyleSheet.hairlineWidth * 2,
              },
            ]}
          >
            {tile.kind === 'heart' ? (
              <HeartTile k={k} />
            ) : (
              <>
                <View style={{ position: 'absolute', left: layout.railRingPad * k, top: ((layout.railHeight - layout.railRing) / 2) * k }}>
                  {tile.kind === 'sleep' && <SleepRing size={layout.railRing * k} />}
                  {tile.kind === 'ready' && <ReadyGauge size={layout.railRing * k} />}
                  {tile.kind === 'fitness' && <FitnessRings size={layout.railRing * k} />}
                </View>
                <View style={{ position: 'absolute', left: (layout.railRingPad + layout.railRing + layout.railTextGap) * k, top: 0 }}>
                  <Text style={[styles.railTitle, { top: (layout.railTitleTop - layout.railTop) * k, fontSize: type.railTitle.size * k, lineHeight: type.railTitle.line * k, letterSpacing: type.railTitle.tracking * k }]} numberOfLines={1}>
                    {tile.title}
                  </Text>
                  <Text style={[styles.railValue, { top: (layout.railValueTop - layout.railTop) * k, fontSize: type.railValue.size * k, lineHeight: type.railValue.line * k, letterSpacing: type.railValue.tracking * k }]} numberOfLines={1}>
                    {tile.value}
                  </Text>
                </View>
              </>
            )}
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

function HeartTile({ k }: { k: number }) {
  return (
    <View style={StyleSheet.absoluteFill}>
      <View style={{ position: 'absolute', left: 0, right: 0, top: (layout.heartTop - layout.railTop) * k, alignItems: 'center' }}>
        <Ionicons name="heart" size={layout.heartSize * k} color={colors.heart} />
      </View>
      {layout.heartLines.map((line) => (
        <View
          key={line.top}
          style={{
            position: 'absolute',
            left: (layout.tiles[0].width - line.width) / 2 * k,
            top: (line.top - layout.railTop) * k,
            width: line.width * k,
            height: 2 * k,
            borderRadius: k,
            backgroundColor: colors.heartLine,
          }}
        />
      ))}
    </View>
  );
}

const RING_VIEW = 40;

function SleepRing({ size }: { size: number }) {
  const r = 16.5;
  const c = 2 * Math.PI * r;
  const arc = (from: number, to: number, color: string) => (
    <Circle cx="20" cy="20" r={r} fill="none" stroke={color} strokeWidth="6.5" strokeDasharray={`${c * (to - from)} ${c}`} strokeDashoffset={-c * from} rotation="-90" origin="20,20" />
  );
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${RING_VIEW} ${RING_VIEW}`}>
      {arc(0.015, 0.2, colors.sleepOrange)}
      {arc(0.215, 0.72, colors.sleepBlue)}
      {arc(0.735, 1, colors.sleepTeal)}
      <SvgText x="20" y="24.2" textAnchor="middle" fontSize={type.railNumber.size * (RING_VIEW / layout.railRing)} fontWeight="700" fill={colors.ink}>84</SvgText>
    </Svg>
  );
}

function ReadyGauge({ size }: { size: number }) {
  // A 270° track open at the bottom; the reading fills 60 % of it and ends on a dot.
  const r = 16;
  const polar = (deg: number) => {
    const a = ((deg - 90) * Math.PI) / 180;
    return `${(20 + r * Math.cos(a)).toFixed(2)} ${(20 + r * Math.sin(a)).toFixed(2)}`;
  };
  const start = -115;
  const end = 115;
  const value = start + (end - start) * 0.6;
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${RING_VIEW} ${RING_VIEW}`}>
      <Defs>
        <SvgGradient id="ready" x1="0" y1="1" x2="1" y2="0">
          <Stop offset="0" stopColor={colors.readyLight} />
          <Stop offset="1" stopColor={colors.readyStrong} />
        </SvgGradient>
      </Defs>
      <Path d={`M ${polar(start)} A ${r} ${r} 0 1 1 ${polar(end)}`} fill="none" stroke={colors.readyTrack} strokeWidth="4" strokeLinecap="round" />
      <Path d={`M ${polar(start)} A ${r} ${r} 0 0 1 ${polar(value)}`} fill="none" stroke="url(#ready)" strokeWidth="4" strokeLinecap="round" />
      <Circle cx={polar(value).split(' ')[0]} cy={polar(value).split(' ')[1]} r="3.4" fill={colors.readyStrong} />
      <SvgText x="20" y="28" textAnchor="middle" fontSize={(type.railNumber.size + 0.5) * (RING_VIEW / layout.railRing)} fontWeight="700" fill={colors.readyStrong}>7</SvgText>
    </Svg>
  );
}

function FitnessRings({ size }: { size: number }) {
  const ring = (r: number, share: number, color: string) => {
    const c = 2 * Math.PI * r;
    return (
      <>
        <Circle cx="20" cy="20" r={r} fill="none" stroke={color} strokeOpacity={0.18} strokeWidth="4.2" />
        <Circle cx="20" cy="20" r={r} fill="none" stroke={color} strokeWidth="4.2" strokeLinecap="round" strokeDasharray={`${c * share} ${c}`} rotation="-90" origin="20,20" />
      </>
    );
  };
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${RING_VIEW} ${RING_VIEW}`}>
      {ring(16.5, 0.86, colors.fitMove)}
      {ring(11.5, 0.72, colors.fitExercise)}
      {ring(6.5, 0.58, colors.fitStand)}
    </Svg>
  );
}

const styles = StyleSheet.create({
  tile: { borderColor: colors.tileBorder, shadowColor: colors.shadow, shadowOpacity: 0.06, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  railTitle: { position: 'absolute', left: 0, color: colors.railTitle, fontWeight: type.railTitle.weight },
  railValue: { position: 'absolute', left: 0, color: colors.ink, fontWeight: type.railValue.weight },
});
