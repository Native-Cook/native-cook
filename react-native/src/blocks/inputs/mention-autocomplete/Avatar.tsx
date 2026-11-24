import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

type Props = {
  initials: string;
  colors: [string, string, string];
  size: number;
  /** Shows a presence dot with a ring in the page colour. */
  online?: boolean;
  ring?: string;
};

/** A drawn avatar: initials on a three-stop gradient disc, with a top highlight and an optional presence dot. */
export const Avatar = memo(function Avatar({ initials, colors, size, online, ring = '#FBFAF7' }: Props) {
  const dot = Math.round(size * 0.3);
  return (
    <View style={{ width: size, height: size }}>
      <View style={[styles.disc, { width: size, height: size, borderRadius: size / 2 }]}>
        <LinearGradient colors={colors} locations={[0, 0.55, 1]} start={{ x: 0.15, y: 0 }} end={{ x: 0.85, y: 1 }} style={StyleSheet.absoluteFill} />
        <View style={[styles.shine, { borderRadius: size / 2 }]} />
        <Text style={[styles.initials, { fontSize: size * 0.38 }]} allowFontScaling={false}>
          {initials}
        </Text>
      </View>
      {online ? <View style={[styles.dot, { width: dot, height: dot, borderRadius: dot / 2, borderColor: ring }]} /> : null}
    </View>
  );
});

const styles = StyleSheet.create({
  disc: { overflow: 'hidden', alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 2px rgba(30,26,20,0.18)' },
  shine: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderTopWidth: 1, borderColor: 'rgba(255,255,255,0.35)' },
  initials: { color: '#FFFDF8', fontWeight: '600', letterSpacing: 0.2 },
  dot: { position: 'absolute', right: -1, bottom: -1, backgroundColor: '#3DAA63', borderWidth: 2 },
});
