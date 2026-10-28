import { StatusBar } from 'expo-status-bar';
import { useRef, useState } from 'react';
import {
  Alert,
  Button,
  InputAccessoryView,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
  type TextProps,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { TrophySavingsMilestoneBackdrop } from './TrophySavingsMilestoneArtwork';
import { TrophySavingsMilestoneHero, type TrophySavingsMilestoneHeroHandle } from './TrophySavingsMilestoneHero';
import { TrophySavingsMilestoneMetricIcon } from './TrophySavingsMilestoneMetricIcon';
import { TrophySavingsMilestoneTargetGlyph } from './TrophySavingsMilestoneTargetGlyph';

export { LOOP } from './TrophySavingsMilestoneMotion';

export type TrophySavingsMilestoneProps = {
  autoPlay?: boolean;
  previewTime?: number;
  previewReducedMotion?: boolean;
};

const WIDTH = 393;
const HEIGHT = 852;
const AMOUNT_ACCESSORY = 'trophy-savings-milestone-amount';
const AMOUNT_FORMAT = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

function FixedText(props: TextProps) {
  return <Text {...props} allowFontScaling={false} />;
}

function Metric({ title, value, kind, accent, fill, left, top, width }: {
  title: string;
  value: string;
  kind: 0 | 1 | 2;
  accent: string;
  fill: string;
  left: number;
  top: number;
  width: number;
}) {
  return (
    <View accessible accessibilityLabel={`${title}, ${value}`} style={[styles.metric, { left, top, width, backgroundColor: fill }]}>
      <TrophySavingsMilestoneMetricIcon kind={kind} color={accent} />
      <View style={styles.metricCopy}>
        <FixedText style={styles.metricTitle}>{title}</FixedText>
        <FixedText style={styles.metricValue}>{value}</FixedText>
      </View>
    </View>
  );
}

export function TrophySavingsMilestone({ autoPlay = false, previewTime, previewReducedMotion = false }: TrophySavingsMilestoneProps) {
  const windowSize = useWindowDimensions();
  const [measuredViewport, setMeasuredViewport] = useState<{ width: number; height: number }>();
  const viewport = measuredViewport ?? windowSize;
  const scale = Math.min(viewport.width / WIDTH, viewport.height / HEIGHT);
  const [composerPresented, setComposerPresented] = useState(false);
  const [goalName, setGoalName] = useState('');
  const [goalAmount, setGoalAmount] = useState('');
  const [goalReady, setGoalReady] = useState(false);
  const amountField = useRef<TextInput>(null);
  const hero = useRef<TrophySavingsMilestoneHeroHandle>(null);
  const trimmedName = goalName.trim();
  const parsedAmount = Number(goalAmount.replaceAll(',', ''));
  const validAmount = Number.isFinite(parsedAmount) && parsedAmount > 0;
  const validGoal = trimmedName.length > 0 && validAmount;

  function openComposer() {
    hero.current?.pauseForOverlay();
    setGoalName('');
    setGoalAmount('');
    setGoalReady(false);
    setComposerPresented(true);
  }

  function closeComposer() {
    Keyboard.dismiss();
    setComposerPresented(false);
    hero.current?.resumeFromOverlay();
  }

  function createGoal() {
    if (!validGoal) return;
    Keyboard.dismiss();
    setGoalReady(true);
  }

  return (
    <View style={styles.root} onLayout={({ nativeEvent: { layout } }) => {
      setMeasuredViewport(current => current?.width === layout.width && current.height === layout.height
        ? current : { width: layout.width, height: layout.height });
    }}>
      <StatusBar style="dark" />
      <View
        style={[styles.stage, {
          left: (viewport.width - WIDTH) / 2,
          top: (viewport.height - HEIGHT) / 2,
          transform: [{ scale }],
        }]}
        accessibilityElementsHidden={composerPresented}
        importantForAccessibility={composerPresented ? 'no-hide-descendants' : 'auto'}
      >
        <TrophySavingsMilestoneBackdrop />
        <TrophySavingsMilestoneHero ref={hero} autoPlay={autoPlay} previewTime={previewTime} previewReducedMotion={previewReducedMotion} scale={scale} />
        <View accessible accessibilityRole="header" accessibilityLabel="You Did It! 🎯" style={styles.headline}>
          <FixedText accessible={false} importantForAccessibility="no" numberOfLines={1} style={styles.headlineText}>You Did It!</FixedText>
          <View style={styles.headlineGlyph}><TrophySavingsMilestoneTargetGlyph /></View>
        </View>
        <View style={styles.message}>
          <FixedText style={styles.messageText}>{"You've successfully reached your savings\ngoal for School Fees. Every little step added\nup to something great!"}</FixedText>
        </View>
        <FixedText accessibilityRole="header" style={styles.detailsTitle}>Milestone Details</FixedText>
        <Metric title="Target Reached" value="₦70,000" kind={0} accent="#1BA14D" fill="#F5FEF9" left={20} top={537} width={353} />
        <Metric title="Duration" value="3 months" kind={1} accent="#327FE9" fill="#F6F9FE" left={20} top={617} width={173} />
        <Metric title="Transfers" value="5" kind={2} accent="#DB0DD1" fill="#FFF5FE" left={200} top={617} width={173} />
        <Pressable accessibilityRole="button" onPress={openComposer} style={({ pressed }) => [styles.button, styles.primaryButton, pressed && styles.pressed]}>
          <FixedText style={styles.primaryButtonText}>Set New Goal</FixedText>
        </Pressable>
        <Pressable accessibilityRole="button" onPress={() => {
          hero.current?.pauseForOverlay();
          const resume = () => hero.current?.resumeFromOverlay();
          Alert.alert(
            'Savings Summary',
            'School Fees\nTarget reached: ₦70,000\nDuration: 3 months\nTransfers: 5',
            [{ text: 'Done', style: 'cancel', onPress: resume }],
            { cancelable: true, onDismiss: resume },
          );
        }} style={({ pressed }) => [styles.button, styles.secondaryButton, pressed && styles.pressed]}>
          <FixedText style={styles.secondaryButtonText}>View Summary</FixedText>
        </Pressable>
      </View>
      <Modal visible={composerPresented} presentationStyle="pageSheet" animationType="none" onRequestClose={closeComposer}>
        <SafeAreaView style={styles.composer} edges={['top', 'bottom', 'left', 'right']}>
          <KeyboardAvoidingView style={styles.composer} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
            <View style={styles.navigationBar}>
              <View style={styles.navigationAction}>
                {!goalReady && <Button title="Cancel" onPress={closeComposer} />}
              </View>
              <Text accessibilityRole="header" style={styles.navigationTitle}>{goalReady ? 'Goal Ready' : 'Set New Goal'}</Text>
              <View style={styles.navigationAction}>
                <Button title={goalReady ? 'Done' : 'Create Goal'} disabled={!goalReady && !validGoal} onPress={goalReady ? closeComposer : createGoal} />
              </View>
            </View>
            <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.form}>
              <Text style={styles.sectionTitle}>{goalReady ? 'GOAL READY' : 'NEW SAVINGS GOAL'}</Text>
              {goalReady ? <View style={styles.formGroup}>
                <Text style={styles.formRow}>{trimmedName}</Text>
                <View style={styles.separator} />
                <Text style={styles.formRow}>Target: ₦{AMOUNT_FORMAT.format(parsedAmount)}</Text>
                <View style={styles.separator} />
                <Text style={[styles.formRow, styles.secondaryCopy]}>Your new goal is ready on this device.</Text>
              </View> : <>
                <View style={styles.formGroup}>
                  <TextInput
                    accessibilityLabel="Goal name"
                    placeholder="Goal name"
                    placeholderTextColor="#8E8E93"
                    value={goalName}
                    onChangeText={setGoalName}
                    returnKeyType="next"
                    onSubmitEditing={() => amountField.current?.focus()}
                    submitBehavior="submit"
                    style={styles.input}
                  />
                  <View style={styles.separator} />
                  <TextInput
                    ref={amountField}
                    accessibilityLabel="Target amount"
                    placeholder="Target amount"
                    placeholderTextColor="#8E8E93"
                    value={goalAmount}
                    onChangeText={setGoalAmount}
                    keyboardType="decimal-pad"
                    inputMode="decimal"
                    inputAccessoryViewID={Platform.OS === 'ios' ? AMOUNT_ACCESSORY : undefined}
                    style={styles.input}
                  />
                </View>
                <Text style={styles.formFootnote}>This creates a local goal. Your completed School Fees milestone stays unchanged.</Text>
              </>}
            </ScrollView>
          </KeyboardAvoidingView>
          {Platform.OS === 'ios' && <InputAccessoryView nativeID={AMOUNT_ACCESSORY}>
            <View style={styles.keyboardAccessory}><Button title="Done" onPress={Keyboard.dismiss} /></View>
          </InputAccessoryView>}
        </SafeAreaView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#EFEFEF', overflow: 'hidden' },
  stage: { position: 'absolute', width: WIDTH, height: HEIGHT },
  headline: { position: 'absolute', left: 21.5, top: 363, width: 350, height: 38, alignItems: 'center', justifyContent: 'center' },
  headlineText: { position: 'absolute', left: 82.5, top: -0.5, width: 150, color: '#222222', fontSize: 31, lineHeight: 38, fontWeight: '700', letterSpacing: 0.15, includeFontPadding: false },
  headlineGlyph: { position: 'absolute', left: 234, top: 6, width: 32, height: 26 },
  message: { position: 'absolute', left: 26.5, top: 412.5, width: 340, height: 60, justifyContent: 'center' },
  messageText: { color: '#444444', fontSize: 14.5, lineHeight: 19.3, textAlign: 'center', includeFontPadding: false },
  detailsTitle: { position: 'absolute', left: 20, top: 503, width: 353, height: 20, color: '#222222', fontSize: 15, lineHeight: 20, fontWeight: '500', includeFontPadding: false },
  metric: { position: 'absolute', height: 73, borderRadius: 24, flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 16 },
  metricCopy: { gap: 3 },
  metricTitle: { color: '#868686', fontSize: 13, lineHeight: 15.5, includeFontPadding: false },
  metricValue: { color: '#222222', fontSize: 17, lineHeight: 20.5, fontWeight: '600', includeFontPadding: false },
  button: { position: 'absolute', top: 738, height: 49, borderRadius: 24.5, alignItems: 'center', justifyContent: 'center' },
  primaryButton: { left: 29, width: 187, backgroundColor: '#222222' },
  secondaryButton: { left: 232, width: 134, backgroundColor: '#F7F7F7' },
  primaryButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '500', includeFontPadding: false },
  secondaryButtonText: { color: '#222222', fontSize: 14, fontWeight: '500', includeFontPadding: false },
  pressed: { opacity: 0.88 },
  composer: { flex: 1, backgroundColor: '#F2F2F7' },
  navigationBar: { minHeight: 56, flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#D1D1D6', backgroundColor: '#F9F9F9' },
  navigationAction: { width: 112, alignItems: 'center' },
  navigationTitle: { flex: 1, textAlign: 'center', color: '#000000', fontSize: 17, fontWeight: '600' },
  form: { paddingHorizontal: 20, paddingTop: 28, paddingBottom: 24 },
  sectionTitle: { marginLeft: 16, marginBottom: 8, color: '#6D6D72', fontSize: 13 },
  formGroup: { borderRadius: 10, backgroundColor: '#FFFFFF', overflow: 'hidden' },
  formRow: { paddingVertical: 12, paddingHorizontal: 16, color: '#000000', fontSize: 17 },
  input: { minHeight: 46, paddingVertical: 12, paddingHorizontal: 16, color: '#000000', fontSize: 17 },
  separator: { marginLeft: 16, height: StyleSheet.hairlineWidth, backgroundColor: '#C6C6C8' },
  secondaryCopy: { color: '#6D6D72' },
  formFootnote: { marginTop: 10, marginHorizontal: 16, color: '#6D6D72', fontSize: 13, lineHeight: 18 },
  keyboardAccessory: { alignItems: 'flex-end', backgroundColor: '#F2F2F7', paddingHorizontal: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#C6C6C8' },
});
