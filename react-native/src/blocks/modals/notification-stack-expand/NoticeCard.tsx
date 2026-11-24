// One notification's face: avatar, sender, time and two lines. Static; the stack moves it from outside.
import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';

import type { Notice } from './data';
import { CARD_H, RADIUS } from './motion';

export const BG = ['#F4F5F7', '#EDEFF2', '#E6E9EE'] as const;
export const CARD = '#FCFCFD';
export const INK = '#1B1F24';
export const MUTE = '#69707A';
export const ACCENT = '#2D6F64';
export const DANGER = '#E2484D';

const AVATAR = 38;

function initials(name: string) {
  const parts = name.split(' ');
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase();
}

function AppGlyph({ app }: { app: 'calendar' | 'files' }) {
  if (app === 'calendar') {
    return (
      <Svg width={20} height={20} viewBox="0 0 20 20">
        <Rect x={2.5} y={3.5} width={15} height={14} rx={3.2} fill="none" stroke="#FFF" strokeWidth={1.7} />
        <Path d="M2.8 8 H17.2 M6.5 1.8 V5 M13.5 1.8 V5" stroke="#FFF" strokeWidth={1.7} strokeLinecap="round" />
        <Rect x={6} y={10.6} width={3} height={3} rx={0.8} fill="#FFF" />
      </Svg>
    );
  }
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      <Path
        d="M5 2.5 H11.5 L15.5 6.5 V16 A1.6 1.6 0 0 1 13.9 17.6 H5 A1.6 1.6 0 0 1 3.4 16 V4.1 A1.6 1.6 0 0 1 5 2.5 Z M11.2 2.8 V6.8 H15.2"
        fill="none"
        stroke="#FFF"
        strokeWidth={1.6}
        strokeLinejoin="round"
      />
    </Svg>
  );
}

function Badge({ kind }: { kind: 'channel' | 'file' }) {
  return (
    <View style={[styles.badge, kind === 'file' && styles.badgeFile]}>
      {kind === 'channel' ? (
        <Svg width={9} height={9} viewBox="0 0 9 9">
          <Path d="M3.2 0.8 L2.4 8.2 M6.6 0.8 L5.8 8.2 M0.9 3 H8.2 M0.6 6 H7.9" stroke="#FFF" strokeWidth={1.25} strokeLinecap="round" />
        </Svg>
      ) : (
        <Svg width={9} height={9} viewBox="0 0 9 9">
          <Path d="M2 0.9 H5.4 L7.4 2.9 V8.1 H2 Z" fill="#FFF" />
        </Svg>
      )}
    </View>
  );
}

export function Avatar({ notice }: { notice: Notice }) {
  return (
    <View style={styles.avatarWrap}>
      <LinearGradient
        colors={notice.tint}
        locations={[0, 0.55, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.avatar, notice.app && styles.squircle]}
      >
        {notice.app ? <AppGlyph app={notice.app} /> : <Text style={styles.initials}>{initials(notice.from)}</Text>}
        <View style={[styles.sheen, notice.app && styles.squircle]} pointerEvents="none" />
      </LinearGradient>
      {notice.badge ? <Badge kind={notice.badge} /> : null}
    </View>
  );
}

/** The card's content only. The caller gives it a background, a shadow and a transform. */
export const NoticeFace = memo(function NoticeFace({ notice }: { notice: Notice }) {
  return (
    <View style={styles.face}>
      <Avatar notice={notice} />
      <View style={styles.text}>
        <View style={styles.topLine}>
          <Text style={styles.from} numberOfLines={1}>{notice.from}</Text>
          <Text style={styles.time}>{notice.time}</Text>
        </View>
        <Text style={styles.body} numberOfLines={2}>{notice.body}</Text>
      </View>
    </View>
  );
});

/** A plain, still notification card for the rest of the feed. */
export const NoticeCard = memo(function NoticeCard({ notice }: { notice: Notice }) {
  return (
    <View style={styles.card} accessible accessibilityLabel={`${notice.from}, ${notice.time}. ${notice.body}`}>
      <NoticeFace notice={notice} />
    </View>
  );
});

export const cardSurface = {
  height: CARD_H,
  borderRadius: RADIUS,
  borderCurve: 'continuous' as const,
  backgroundColor: CARD,
  boxShadow: '0 1px 2px rgba(30,38,48,0.08), 0 10px 24px rgba(30,38,48,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
};

const styles = StyleSheet.create({
  card: cardSurface,
  face: { flex: 1, flexDirection: 'row', alignItems: 'flex-start', paddingHorizontal: 14, paddingTop: 13 },
  avatarWrap: { width: AVATAR, height: AVATAR, marginTop: 2 },
  avatar: { width: AVATAR, height: AVATAR, borderRadius: AVATAR / 2, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  squircle: { borderRadius: 10, borderCurve: 'continuous' },
  sheen: {
    ...StyleSheet.absoluteFill,
    borderRadius: AVATAR / 2,
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.35), inset 0 -1px 0 rgba(0,0,0,0.08)',
  },
  initials: { color: '#FFFFFF', fontSize: 14, fontWeight: '600', letterSpacing: 0.2 },
  badge: {
    position: 'absolute',
    right: -3,
    bottom: -3,
    width: 17,
    height: 17,
    borderRadius: 8.5,
    backgroundColor: ACCENT,
    borderWidth: 2,
    borderColor: CARD,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeFile: { backgroundColor: '#5C8FD6' },
  text: { flex: 1, marginLeft: 12, minWidth: 0 },
  topLine: { flexDirection: 'row', alignItems: 'baseline', height: 20 },
  from: { flexShrink: 1, fontSize: 15, lineHeight: 20, fontWeight: '600', color: INK },
  time: { marginLeft: 'auto', paddingLeft: 8, fontSize: 13, color: MUTE, fontVariant: ['tabular-nums'] },
  body: { marginTop: 3, fontSize: 14, lineHeight: 18, color: '#3C434C' },
});
