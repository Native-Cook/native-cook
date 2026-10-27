import { Modal, Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { STEPS } from './flow';
import { Mark } from './glyphs';
import { hex, white } from './theme';

export type Detail = 'overview' | 'trial' | 'terms' | 'privacy' | 'finish';

const TITLES: Record<Detail, string> = {
  overview: 'Simulation overview',
  trial: 'Demo trial selected',
  terms: 'Showcase terms',
  privacy: 'Showcase privacy',
  finish: "You're ready to explore",
};

const BODIES: Partial<Record<Detail, string>> = {
  privacy: 'This showcase makes no network requests and does not collect wallet, account or payment details. Settings live only for this demo session.',
  terms: 'This is an interactive design reconstruction. Balances, simulated returns and subscription prices are fictional local fixtures; no financial service or subscription is provided.',
};

const LOCAL = 'This is a local showcase. No wallet is connected, no trades are placed, and no trial or purchase is activated.';

/** Local sheets for trial, terms, privacy, overview and Skip on Pro. Nothing leaves the device. */
export function DetailSheet({ detail, risk, allocation, close, replay }: { detail: Detail | null; risk: string; allocation: number; close: () => void; replay: () => void }) {
  return (
    <Modal visible={detail !== null} animationType="slide" presentationStyle="pageSheet" onRequestClose={close}>
      <View style={styles.sheet}>
        <Pressable onPress={close} style={styles.done} accessibilityRole="button"><Text style={styles.link}>Done</Text></Pressable>
        <View style={{ alignItems: 'center', gap: 25, padding: 30 }}>
          <Mark size={55} />
          <Text style={styles.title}>{detail ? TITLES[detail] : ''}</Text>
          {detail === 'overview' && (
            <Text style={[styles.body, { color: '#fff', lineHeight: 30, fontVariant: ['tabular-nums'] }]}>
              {`Simulated return: +$1,284.50\nCapital allocation: ${Math.round(allocation)}%\nRisk per trade: ${risk}`}
            </Text>
          )}
          <Text style={styles.body}>{(detail && BODIES[detail]) ?? LOCAL}</Text>
          <Pressable onPress={close} style={styles.primary} accessibilityRole="button"><Text style={styles.primaryText}>Keep exploring</Text></Pressable>
          <Pressable onPress={replay} accessibilityRole="button"><Text style={styles.link}>Replay onboarding</Text></Pressable>
        </View>
      </View>
    </Modal>
  );
}

/**
 * Deliberately separate from the reconstructed product composition. Hold Back (or the
 * five-step progress) for 0.8 s to open. No automatic replay on launch.
 */
export function QAHarness({ visible, step, reduced, setReduced, show, replay, reset, close }: {
  visible: boolean;
  step: number;
  reduced: boolean;
  setReduced: (on: boolean) => void;
  show: (step: number) => void;
  replay: () => void;
  reset: () => void;
  close: () => void;
}) {
  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={close}>
      <ScrollView style={styles.sheet} contentContainerStyle={{ padding: 20, gap: 8 }}>
        <Text style={[styles.title, { alignSelf: 'center', fontSize: 17 }]}>Showcase QA</Text>
        <Text style={styles.section}>INSPECT A SCENE</Text>
        {STEPS.map((name, index) => (
          <Pressable key={name} onPress={() => show(index)} style={styles.row} accessibilityRole="button">
            <Text style={styles.rowText}>{name}</Text>
            {index === step && <Text style={styles.rowText}>✓</Text>}
          </Pressable>
        ))}
        <Text style={styles.section}>MOTION</Text>
        <View style={styles.row}>
          <Text style={styles.rowText}>Force reduced motion</Text>
          <Switch value={reduced} onValueChange={setReduced} />
        </View>
        <Text style={styles.foot}>The system Reduce Motion setting is always respected. This override allows repeatable QA without changing device settings.</Text>
        <Pressable onPress={replay} style={styles.row} accessibilityRole="button"><Text style={styles.link}>Play walkthrough (about 20 seconds)</Text></Pressable>
        <Pressable onPress={reset} style={styles.row} accessibilityRole="button"><Text style={styles.link}>Reset to wallet</Text></Pressable>
        <Text style={styles.section}>LOCAL PROTOTYPE</Text>
        <Text style={styles.foot}>All money, bot settings and subscription values are fixtures. Trial, terms and privacy actions open local demo sheets. No network, trading or purchase is performed.</Text>
      </ScrollView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  sheet: { flex: 1, backgroundColor: hex('#1c1c1e') },
  done: { alignSelf: 'flex-end', padding: 16 },
  title: { fontSize: 22, fontWeight: '600', color: '#fff', textAlign: 'center' },
  body: { fontSize: 16, color: white(0.6), textAlign: 'center' },
  link: { fontSize: 17, color: hex('#0a84ff') },
  primary: { backgroundColor: hex('#0a84ff'), borderRadius: 20, paddingHorizontal: 20, paddingVertical: 10 },
  primaryText: { fontSize: 16, fontWeight: '600', color: '#fff' },
  section: { fontSize: 13, color: white(0.5), marginTop: 18, marginLeft: 4 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: hex('#2c2c2e'), borderRadius: 10, paddingHorizontal: 16, minHeight: 44 },
  rowText: { fontSize: 17, color: '#fff' },
  foot: { fontSize: 13, color: white(0.5), marginHorizontal: 4 },
});
