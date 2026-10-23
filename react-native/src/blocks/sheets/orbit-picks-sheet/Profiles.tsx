import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';

import { INDIGO } from './Badge';

export type Profile = {
  name: string;
  rating: string;
  details: string;
  likes: string;
  match: number;
  skin: string;
  hair: string;
  shirt: string;
  /** Round glasses on the face. */
  glasses?: boolean;
  /** A portrait; without one, a flat drawn face stands in. */
  photo?: string;
  /** A few more interests, shown as chips. */
  tags?: string[];
};

const MUTE = '#6b6b7b';
const INK = '#14141f';
const GREEN = '#22c55e';
const WELL = '#f4f4f6';
const AVATAR = 108;
const RING = AVATAR + 12;

/** A flat face: shoulders, a head, hair and, for some, round glasses, on a pale disc. */
function Avatar({ profile }: { profile: Profile }) {
  return (
    <Svg width={AVATAR} height={AVATAR} viewBox="0 0 96 96">
      <Circle cx={48} cy={48} r={48} fill="#eeeef6" />
      <Path d="M14 90C16 70 30 64 48 64C66 64 80 70 82 90A48 48 0 0 1 14 90Z" fill={profile.shirt} />
      <Path d="M42 56H54V68C54 70 42 70 42 68Z" fill={profile.skin} />
      <Circle cx={48} cy={42} r={19} fill={profile.skin} />
      <Path d="M28 40C26 24 38 16 50 17C62 18 70 26 67 40C63 33 58 30 52 31C44 33 36 31 28 40Z" fill={profile.hair} />
      <Circle cx={41} cy={44} r={2} fill={INK} />
      <Circle cx={55} cy={44} r={2} fill={INK} />
      {profile.glasses ? (
        <>
          <Circle cx={41} cy={44} r={6} stroke={INK} strokeWidth={1.5} fill="none" />
          <Circle cx={55} cy={44} r={6} stroke={INK} strokeWidth={1.5} fill="none" />
        </>
      ) : null}
      <Path d="M43 53C46 56 50 56 53 53" stroke={INK} strokeWidth={1.5} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

/** One profile card: the person, what they like, and the Chat button. */
export function ProfileCard({ profile, chatLabel, onChat }: { profile: Profile; chatLabel: string; onChat?: () => void }) {
  return (
    <View style={styles.card}>
      <View style={styles.corners}>
        <View style={styles.corner}>
          <Ionicons name="ellipsis-horizontal" size={16} color={MUTE} />
        </View>
        <View style={styles.corner}>
          <Ionicons name="close" size={17} color={MUTE} />
        </View>
      </View>
      <LinearGradient colors={['#8f7bef', INDIGO, '#f2547d']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.ring}>
        <View style={styles.ringGap}>
          {profile.photo ? <Image source={profile.photo} style={styles.photo} contentFit="cover" transition={0} /> : <Avatar profile={profile} />}
        </View>
      </LinearGradient>
      <View style={styles.nameRow}>
        <Text style={styles.name}>{profile.name}</Text>
        <View style={styles.rating}>
          <Ionicons name="star" size={9} color="#ffffff" />
          <Text style={styles.ratingText}>{profile.rating}</Text>
        </View>
      </View>
      <View style={styles.detailsRow}>
        <Text style={styles.details}>{profile.details}</Text>
        <Ionicons name="location-outline" size={13} color={MUTE} />
        <Text style={styles.details}>3 km away</Text>
      </View>
      <View style={styles.likes}>
        <View style={styles.likesIcon}>
          <Ionicons name="american-football" size={16} color={INDIGO} />
        </View>
        <View style={styles.likesText}>
          <Text style={styles.likesTop}>
            Likes <Text style={styles.likesBold}>{profile.likes}</Text>
          </Text>
          <Text style={styles.likesBottom}>
            You&apos;re <Text style={[styles.likesBold, { color: GREEN }]}>{profile.match}%</Text> compatible
          </Text>
        </View>
      </View>
      {profile.tags ? (
        <View style={styles.tags}>
          {profile.tags.map((tag) => (
            <View key={tag} style={styles.tag}>
              <Text style={styles.tagText}>{tag}</Text>
            </View>
          ))}
        </View>
      ) : null}
      <View style={styles.chatShadow}>
        <Text style={styles.chat} accessibilityRole="button" onPress={onChat}>
          {chatLabel}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    marginHorizontal: 20,
    alignItems: 'center',
    paddingTop: 24,
    borderRadius: 28,
    backgroundColor: '#ffffff',
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: '#ececf2',
    shadowColor: '#2a1d7a',
    shadowOpacity: 0.1,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 12 },
  },
  corners: { position: 'absolute', left: 12, right: 12, top: 12, flexDirection: 'row', justifyContent: 'space-between' },
  corner: { width: 32, height: 32, borderRadius: 16, backgroundColor: WELL, alignItems: 'center', justifyContent: 'center' },
  ring: { width: RING, height: RING, borderRadius: RING / 2, alignItems: 'center', justifyContent: 'center' },
  ringGap: { width: AVATAR + 6, height: AVATAR + 6, borderRadius: (AVATAR + 6) / 2, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  photo: { width: AVATAR, height: AVATAR, borderRadius: AVATAR / 2 },
  nameRow: { marginTop: 14, flexDirection: 'row', alignItems: 'center', gap: 8 },
  name: { fontSize: 26, lineHeight: 32, fontWeight: '800', letterSpacing: -0.6, color: INK },
  rating: { height: 20, paddingHorizontal: 6, borderRadius: 6, backgroundColor: GREEN, flexDirection: 'row', alignItems: 'center', gap: 3 },
  ratingText: { fontSize: 12, lineHeight: 14, fontWeight: '800', color: '#ffffff' },
  detailsRow: { marginTop: 2, flexDirection: 'row', alignItems: 'center', gap: 4 },
  details: { fontSize: 15, lineHeight: 20, fontWeight: '500', color: MUTE, marginRight: 4 },
  likes: { marginTop: 18, alignSelf: 'stretch', marginHorizontal: 20, height: 64, borderRadius: 18, backgroundColor: WELL, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, gap: 12 },
  likesIcon: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#ece8fb', alignItems: 'center', justifyContent: 'center' },
  likesText: { gap: 2 },
  likesTop: { fontSize: 15, lineHeight: 19, color: MUTE },
  likesBottom: { fontSize: 13, lineHeight: 17, color: MUTE },
  likesBold: { fontWeight: '800', color: INK },
  tags: { marginTop: 12, flexDirection: 'row', gap: 8 },
  tag: { height: 30, paddingHorizontal: 12, borderRadius: 15, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: '#e4e4ec', justifyContent: 'center' },
  tagText: { fontSize: 13, lineHeight: 16, fontWeight: '600', color: '#45455a' },
  chatShadow: { marginTop: 18, alignSelf: 'stretch', marginHorizontal: 20, borderRadius: 27, shadowColor: INDIGO, shadowOpacity: 0.35, shadowRadius: 12, shadowOffset: { width: 0, height: 6 } },
  chat: { height: 54, borderRadius: 27, overflow: 'hidden', backgroundColor: INDIGO, textAlign: 'center', lineHeight: 54, fontSize: 17, fontWeight: '800', color: '#ffffff' },
});
