import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type AnimatedStyle } from 'react-native-reanimated';
import type { StyleProp, ViewStyle } from 'react-native';

import { Badge, INDIGO } from './Badge';

const PAGE = '#ffffff';
const INK = '#14141f';
const MUTE = '#6b6b7b';
const WELL = '#f6f6f8';
const MARGIN = 20;
const ME = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=120&h=120&q=80';

type Interest = { label: string; icon: keyof typeof Ionicons.glyphMap; fill: string; ink: string };

const INTERESTS: Interest[][] = [
  [
    { label: 'Gaming', icon: 'game-controller', fill: '#e1ecff', ink: '#2f6fe0' },
    { label: 'Skincare', icon: 'sparkles', fill: '#fff4c4', ink: '#a67c00' },
    { label: 'Hiking', icon: 'trail-sign', fill: '#ffeede', ink: '#d0661c' },
    { label: 'Pizza', icon: 'pizza', fill: '#ffe1ec', ink: '#d6366b' },
  ],
  [
    { label: 'Pop', icon: 'musical-notes', fill: '#ffe1ec', ink: '#d6366b' },
    { label: 'Dogs', icon: 'paw', fill: '#dcf7e4', ink: '#1a9a4a' },
    { label: 'Country', icon: 'headset', fill: '#ffe0e0', ink: '#d63636' },
    { label: 'Space', icon: 'planet', fill: '#dff6fa', ink: '#1695a8' },
  ],
];

/** The screen behind the sheet: a heading, the banner that opens the picks, interests and an empty chat list. */
export function PageBehind({
  top,
  bottom,
  title,
  banner,
  bannerCaption,
  bannerStyle,
  onBanner,
}: {
  top: number;
  bottom: number;
  title: string;
  banner: string;
  bannerCaption: string;
  bannerStyle: StyleProp<AnimatedStyle<ViewStyle>>;
  onBanner: () => void;
}) {
  return (
    <View style={styles.page}>
      <View style={[styles.header, { top: top + 6 }]}>
        <Image source={ME} style={styles.avatar} contentFit="cover" transition={0} />
        <Text style={styles.title}>{title}</Text>
        <View style={styles.round}>
          <Ionicons name="shield-checkmark-outline" size={20} color={INK} />
        </View>
        <View style={styles.round}>
          <Ionicons name="options-outline" size={20} color={INK} />
        </View>
      </View>

      <Animated.View style={[styles.bannerSlot, { top: top + 62 }, bannerStyle]}>
        <Pressable style={styles.bannerPress} accessibilityRole="button" onPress={onBanner}>
          <LinearGradient colors={['#f1edff', '#e4ddfd']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.banner}>
            <View style={styles.bannerBadge}>
              <Badge size={48} />
            </View>
            <View style={styles.bannerText}>
              <Text style={styles.bannerTitle}>{banner}</Text>
              <Text style={styles.bannerCaption} numberOfLines={2} lineBreakStrategyIOS="standard">
                {bannerCaption}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={INDIGO} />
          </LinearGradient>
          <View style={styles.count}>
            <Text style={styles.countText}>5</Text>
          </View>
        </Pressable>
      </Animated.View>
      <View style={[styles.pageDots, { top: top + 162 }]}>
        <View style={[styles.pageDot, styles.pageDotOn]} />
        <View style={styles.pageDot} />
        <View style={styles.pageDot} />
      </View>

      <View style={[styles.sectionRow, { top: top + 190 }]}>
        <Text style={styles.section}>Explore Interests</Text>
        <Text style={styles.sectionLink}>See All</Text>
      </View>
      {INTERESTS.map((row, index) => (
        <View key={index} style={[styles.interests, { top: top + 228 + index * 44 }]}>
          {row.map((interest) => (
            <View key={interest.label} style={[styles.pill, { backgroundColor: interest.fill }]}>
              <Ionicons name={interest.icon} size={15} color={interest.ink} />
              <Text style={[styles.pillText, { color: interest.ink }]}>{interest.label}</Text>
            </View>
          ))}
        </View>
      ))}

      <View style={[styles.sectionRow, { top: top + 334 }]}>
        <Text style={styles.section}>Chats</Text>
        <Text style={styles.sectionMute}>0 Requests</Text>
      </View>
      <View style={[styles.empty, { top: top + 374 }]}>
        <View style={styles.bubbles}>
          <View style={[styles.bubble, styles.bubbleBack]}>
            <Ionicons name="chatbubble" size={22} color="#c9bff7" />
          </View>
          <View style={[styles.bubble, styles.bubbleFront]}>
            <Ionicons name="chatbubbles" size={24} color={INDIGO} />
          </View>
        </View>
        <Text style={styles.emptyTitle}>No chats yet!</Text>
        <Text style={styles.emptyCaption}>When you chat with new people on {title}, they&apos;ll show up here</Text>
        <View style={styles.find}>
          <Ionicons name="sparkles" size={16} color="#ffffff" />
          <Text style={styles.findText}>Find New People</Text>
        </View>
      </View>

      <View style={[styles.tabs, { paddingBottom: bottom + 2 }]}>
        <View style={styles.tab}>
          <Ionicons name="happy-outline" size={26} color={MUTE} />
        </View>
        <View style={styles.tab}>
          <Ionicons name="compass" size={27} color={INDIGO} />
          <View style={styles.tabDot} />
        </View>
        <View style={styles.tab}>
          <Ionicons name="chatbubbles-outline" size={25} color={MUTE} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { ...StyleSheet.absoluteFill, backgroundColor: PAGE, overflow: 'hidden' },
  header: { position: 'absolute', left: MARGIN, right: MARGIN, height: 40, flexDirection: 'row', alignItems: 'center', gap: 8 },
  avatar: { width: 36, height: 36, borderRadius: 18, marginRight: 4 },
  title: { flex: 1, fontSize: 30, lineHeight: 36, fontWeight: '800', letterSpacing: -0.8, color: INK },
  round: { width: 40, height: 40, borderRadius: 20, backgroundColor: WELL, alignItems: 'center', justifyContent: 'center' },
  bannerSlot: { position: 'absolute', left: MARGIN, right: MARGIN, height: 88 },
  bannerPress: { flex: 1 },
  banner: { flex: 1, borderRadius: 22, paddingLeft: 14, paddingRight: 14, flexDirection: 'row', alignItems: 'center', gap: 14, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: '#ddd5fb' },
  bannerBadge: { width: 58, height: 58, borderRadius: 29, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center', shadowColor: INDIGO, shadowOpacity: 0.2, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
  bannerText: { flex: 1, gap: 3 },
  bannerTitle: { fontSize: 17, lineHeight: 22, fontWeight: '800', letterSpacing: -0.3, color: INK },
  bannerCaption: { fontSize: 13, lineHeight: 17, color: MUTE },
  count: { position: 'absolute', top: -6, right: -4, minWidth: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: '#ffffff', backgroundColor: '#f2547d', alignItems: 'center', justifyContent: 'center' },
  countText: { fontSize: 11, lineHeight: 13, fontWeight: '800', color: '#ffffff' },
  pageDots: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 5 },
  pageDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#dcdce4' },
  pageDotOn: { width: 16, backgroundColor: INDIGO },
  sectionRow: { position: 'absolute', left: MARGIN, right: MARGIN, flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  section: { fontSize: 20, lineHeight: 26, fontWeight: '800', letterSpacing: -0.4, color: INK },
  sectionLink: { fontSize: 14, lineHeight: 18, fontWeight: '700', color: INDIGO },
  sectionMute: { fontSize: 14, lineHeight: 18, fontWeight: '600', color: '#a9a9b6' },
  interests: { position: 'absolute', left: MARGIN, right: -40, flexDirection: 'row', gap: 8 },
  pill: { height: 36, paddingHorizontal: 13, borderRadius: 18, flexDirection: 'row', alignItems: 'center', gap: 6 },
  pillText: { fontSize: 14, lineHeight: 18, fontWeight: '700' },
  empty: { position: 'absolute', left: MARGIN, right: MARGIN, borderRadius: 24, backgroundColor: WELL, paddingHorizontal: 20, paddingTop: 30, paddingBottom: 24, alignItems: 'center', gap: 8 },
  bubbles: { width: 76, height: 56, marginBottom: 6 },
  bubble: { position: 'absolute', width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  bubbleBack: { left: 0, top: 0, backgroundColor: '#ece8fb' },
  bubbleFront: { right: 0, bottom: 0, backgroundColor: '#ffffff', shadowColor: INDIGO, shadowOpacity: 0.18, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
  emptyTitle: { fontSize: 18, lineHeight: 23, fontWeight: '800', letterSpacing: -0.3, color: INK },
  emptyCaption: { fontSize: 14, lineHeight: 19, color: MUTE, textAlign: 'center', paddingHorizontal: 8 },
  find: { marginTop: 14, alignSelf: 'stretch', height: 52, borderRadius: 26, backgroundColor: INDIGO, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', shadowColor: INDIGO, shadowOpacity: 0.3, shadowRadius: 10, shadowOffset: { width: 0, height: 5 } },
  findText: { fontSize: 16, lineHeight: 20, fontWeight: '800', color: '#ffffff' },
  tabs: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingTop: 10, flexDirection: 'row', justifyContent: 'space-around', backgroundColor: PAGE, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#e6e6ec' },
  tab: { width: 64, height: 40, alignItems: 'center', justifyContent: 'center' },
  tabDot: { position: 'absolute', bottom: -2, width: 5, height: 5, borderRadius: 2.5, backgroundColor: INDIGO },
});
