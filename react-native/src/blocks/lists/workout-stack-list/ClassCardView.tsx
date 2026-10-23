import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Rect, Stop } from 'react-native-svg';

import { type ClassCard, SESSION_TIMES } from './data';
import { COLORS, DETAIL, FUTURA, FUTURA_MEDIUM } from './theme';

const W = 402 - DETAIL.list.side * 2;
const CARD_ALPHA = 'E6';

/** One class card, drawn at full size; the parent positions and scales it. */
export function ClassCardView({ card, height }: { card: ClassCard; height: number }) {
  return (
    <View style={[styles.card, { width: W, height }]}>
      {/* the pin's cards are slightly see-through: the card behind shows faintly */}
      <LinearGradient colors={[card.colors[0] + CARD_ALPHA, card.colors[1] + CARD_ALPHA]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      {card.kind === 'live' && card.key === 'live8' ? <LiveArt height={height} /> : null}
      {card.photo ? <Image source={card.photo} style={[styles.photo, { height: height + 10, width: height + 10 }]} contentFit="contain" contentPosition="bottom right" /> : null}
      {card.kind === 'live' ? (
        <>
          <View style={styles.liveRow}>
            <View style={styles.liveTag}>
              <Text style={styles.liveText}>LIVE</Text>
            </View>
            <Text style={styles.liveHint}>Starting now, tap to join!</Text>
          </View>
          <Text style={[styles.title, styles.small, styles.liveTitle]}>{card.title}</Text>
          {card.coach ? <Coach coach={card.coach} /> : null}
        </>
      ) : card.kind === 'quiz' ? (
        <>
          <View style={styles.tags}>
            <View style={[styles.tag, styles.tagFilled]}>
              <Text style={styles.tagText}>{card.time}</Text>
            </View>
            <View style={styles.tag}>
              <Text style={styles.tagText}>{card.length}</Text>
            </View>
          </View>
          <Text style={[styles.title, styles.small]}>{card.title}</Text>
          {card.coach ? <Coach coach={card.coach} /> : null}
        </>
      ) : (
        <>
          <Text style={[styles.title, styles.big]}>{card.title}</Text>
          <View style={styles.select}>
            <Text style={[styles.selectText, { color: card.colors[0] }]}>Select Session</Text>
          </View>
          <View style={styles.timeRow}>
            <Svg width={22} height={22} viewBox="0 0 22 22">
              <Circle cx={11} cy={11} r={9} stroke={COLORS.white} strokeWidth={1.8} fill="none" />
              <Path d="M11 6.5V11l3 2" stroke={COLORS.white} strokeWidth={1.8} fill="none" strokeLinecap="round" />
            </Svg>
            <View>
              <Text style={styles.timeLabel}>Time</Text>
              <Text style={styles.timeValue}>{card.length}</Text>
            </View>
          </View>
          <View style={styles.chips}>
            {SESSION_TIMES.map((t) => (
              <View key={t} style={styles.chip}>
                <Text style={styles.chipText}>{t}</Text>
              </View>
            ))}
          </View>
        </>
      )}
    </View>
  );
}

/** The live class's backdrop on the pin: a lilac-to-sky gradient, a fold of magenta mat at the top right and a leaf. */
function LiveArt({ height }: { height: number }) {
  return (
    <Svg width={W} height={height} style={StyleSheet.absoluteFill} viewBox={`0 0 ${W} 180`} preserveAspectRatio="none">
      <Defs>
        <SvgGradient id="liveSky" x1="0" y1="0" x2="1" y2="0.3">
          <Stop offset="0" stopColor="#B86CD8" />
          <Stop offset="0.45" stopColor="#8F94FE" />
          <Stop offset="1" stopColor="#6CD2FC" />
        </SvgGradient>
        <SvgGradient id="liveMat" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#F22589" />
          <Stop offset="1" stopColor="#8C0242" />
        </SvgGradient>
      </Defs>
      <Rect width={W} height={180} fill="url(#liveSky)" />
      <Path d={`M150 0 L${W - 50} 0 L${W - 70} 60 L${W - 105} 110 L190 80 Z`} fill="url(#liveMat)" />
      <Path d={`M${W - 92} 180 Q${W - 70} 110 ${W} 120 L${W} 180 Z`} fill="#0A5A2A" />
      <Path d={`M${W - 60} 180 Q${W - 30} 140 ${W} 150`} stroke="#1E7A3E" strokeWidth={3} fill="none" />
    </Svg>
  );
}

function Coach({ coach }: { coach: { name: string; photo: number } }) {
  return (
    <View style={styles.coach}>
      <Image source={coach.photo} style={styles.avatar} />
      <Text style={styles.coachName}>{coach.name}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: DETAIL.list.radius, borderCurve: 'continuous', overflow: 'hidden' },
  photo: { position: 'absolute', right: -6, bottom: 0 },
  liveRow: {
    position: 'absolute',
    top: 12,
    left: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingRight: 10,
    borderRadius: 6,
    backgroundColor: 'rgba(255,255,255,0.28)',
  },
  liveTag: { backgroundColor: COLORS.white, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  liveText: { fontFamily: FUTURA, fontSize: 12, color: COLORS.tag, letterSpacing: 0.4 },
  liveHint: { fontFamily: FUTURA_MEDIUM, fontSize: 12, color: COLORS.white },
  tags: { position: 'absolute', top: 16, left: 16, flexDirection: 'row', gap: 6 },
  tag: { borderRadius: 6, borderWidth: 1, borderColor: 'rgba(255,255,255,0.6)', paddingHorizontal: 8, height: 28, justifyContent: 'center' },
  tagFilled: { backgroundColor: COLORS.tag, borderColor: COLORS.tag },
  tagText: { fontFamily: FUTURA_MEDIUM, fontSize: 13, color: COLORS.white, fontVariant: ['tabular-nums'] },
  title: { position: 'absolute', left: 16, fontFamily: FUTURA, color: COLORS.white },
  small: { top: 52, fontSize: 22, lineHeight: 28, letterSpacing: 0.4 },
  liveTitle: { top: 56, fontSize: 24, lineHeight: 29 },
  big: { top: 26, fontSize: 30, lineHeight: 38, letterSpacing: 0.5 },
  coach: { position: 'absolute', left: 16, bottom: 18, flexDirection: 'row', alignItems: 'center', gap: 10 },
  avatar: { width: 34, height: 34, borderRadius: 17, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)' },
  coachName: { fontFamily: FUTURA_MEDIUM, fontSize: 14, color: COLORS.white },
  select: {
    position: 'absolute',
    top: 128,
    left: 18,
    height: 36,
    paddingHorizontal: 24,
    borderRadius: 18,
    backgroundColor: COLORS.white,
    justifyContent: 'center',
  },
  selectText: { fontFamily: FUTURA, fontSize: 13 },
  timeRow: { position: 'absolute', top: 180, left: 18, flexDirection: 'row', alignItems: 'center', gap: 10 },
  timeLabel: { fontFamily: FUTURA_MEDIUM, fontSize: 12, color: COLORS.white, lineHeight: 15 },
  timeValue: { fontFamily: FUTURA, fontSize: 12, color: COLORS.white, lineHeight: 15 },
  chips: { position: 'absolute', left: 14, right: 14, bottom: 14, flexDirection: 'row', gap: 6 },
  chip: { flex: 1, height: 36, borderRadius: 6, backgroundColor: 'rgba(255,255,255,0.22)', alignItems: 'center', justifyContent: 'center' },
  chipText: { fontFamily: FUTURA_MEDIUM, fontSize: 13, color: COLORS.white, fontVariant: ['tabular-nums'] },
});
