import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';

export type Task = {
  name: string;
  caption: string;
  time: string;
  color: string;
  /** Running (green dot) or later today (white ring). Running by default. */
  status?: 'running' | 'later';
  /** How far through today's session it is, 0 to 1: the dashes under the time fill up to it. */
  progress?: number;
};

export const CARD_HEIGHT = 112;
const RUNNING = '#3ddc6c';
const DASHES = 14;

/** The task as a card: what the list shows, and what the flying card turns into. */
export function TaskCard({ task, width }: { task: Task; width: number }) {
  const running = (task.status ?? 'running') === 'running';
  const filled = Math.round((task.progress ?? 0.35) * DASHES);
  return (
    <View style={[styles.card, { width, backgroundColor: task.color }]}>
      {/* A light from the top left, as on a printed card. */}
      <LinearGradient
        pointerEvents="none"
        colors={['rgba(255,255,255,0.22)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.06)']}
        locations={[0, 0.55, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.6, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <Text style={styles.name} numberOfLines={1}>
        {task.name}
      </Text>
      <Text style={styles.caption} numberOfLines={2} lineBreakStrategyIOS="standard">
        {task.caption}
      </Text>
      <View style={styles.footer}>
        <View>
          <Text style={styles.time}>{task.time}</Text>
          <View style={styles.dashes}>
            {Array.from({ length: DASHES }, (_, i) => (
              <View key={i} style={[styles.dash, i >= filled && styles.dashEmpty]} />
            ))}
          </View>
        </View>
        <View style={styles.status}>
          <View style={[styles.dot, !running && styles.ring]} />
          <Text style={styles.running}>{running ? 'RUNNING' : 'LATER'}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { height: CARD_HEIGHT, borderRadius: 20, paddingHorizontal: 18, paddingTop: 16, paddingBottom: 14, overflow: 'hidden' },
  name: { fontSize: 19, lineHeight: 24, fontWeight: '800', letterSpacing: -0.4, color: '#ffffff' },
  caption: { marginTop: 3, fontSize: 12.5, lineHeight: 16, fontWeight: '500', color: 'rgba(255,255,255,0.86)' },
  footer: { flex: 1, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  time: { fontSize: 12, lineHeight: 15, fontWeight: '800', color: '#ffffff', letterSpacing: 0.2 },
  dashes: { flexDirection: 'row', gap: 3, marginTop: 4 },
  dash: { width: 6, height: 2, borderRadius: 1, backgroundColor: '#ffffff' },
  dashEmpty: { backgroundColor: 'rgba(255,255,255,0.38)' },
  status: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 22, paddingHorizontal: 9, borderRadius: 11, backgroundColor: 'rgba(0,0,0,0.12)' },
  dot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: RUNNING },
  ring: { backgroundColor: 'transparent', borderWidth: 1.5, borderColor: '#ffffff' },
  running: { fontSize: 10, lineHeight: 12, fontWeight: '800', color: '#ffffff', letterSpacing: 0.6 },
});
