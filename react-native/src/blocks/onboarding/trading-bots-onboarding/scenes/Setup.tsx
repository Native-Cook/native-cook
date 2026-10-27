import MaskedView from '@react-native-masked-view/masked-view';
import { LinearGradient } from 'expo-linear-gradient';
import { Image, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { Character, TOGGLE_TILE } from '../characters';
import { BellCurve, SliderGlyph } from '../glyphs';
import { Caption, Centered, Frame, PopIn, PressScale, Reveal, SFSymbol, headingText, useEntrance } from '../primitives';
import { EASE_OUT, hex, white } from '../theme';

/** Blurred dashboard words behind the phone: text, centre, rotation, pill. */
const LABELS: [string, number, number, number, boolean][] = [
  ['Risk Settings', 204, 70, 0, false],
  ['Overview', 330, 86, 5, true],
  ['Configuration', 80, 104, -6, false],
  ['Win Rate', 205, 143, 5, false],
  ['Risk Settings', 328, 151, 5, true],
  ['Risk Settings', 78, 167, -5, false],
  ['Protected', 204, 196, 3, false],
  ['Risk Settings', 335, 205, 4, false],
  ['Overview', 64, 225, -4, false],
];

type Actions = { configure: () => void; overview: () => void };

/**
 * Step 3: a miniature phone (316.7 pt wide at x 41.7, y 196) rises over a field of blurred
 * dashboard labels, above "Ai Bots, / Ready To Go".
 */
export function SetupScene({ reduced, configure, overview }: { reduced: boolean } & Actions) {
  const labels = useEntrance(reduced, { delay: 0.15, duration: 0.9, easing: EASE_OUT });
  const labelsStyle = useAnimatedStyle(() => ({ opacity: labels.get() }));
  const phone = useEntrance(reduced, { spring: [0.8, 0.92] });
  const phoneStyle = useAnimatedStyle(() => ({ opacity: phone.get(), transform: [{ translateY: reduced ? 0 : (1 - phone.get()) * 70 }] }));
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View style={[StyleSheet.absoluteFill, labelsStyle]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        {LABELS.map(([text, x, y, angle, pill], index) => (
          <Centered key={index} x={x} y={y} box={300}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: pill ? 14 : 0, paddingVertical: pill ? 9 : 0, borderRadius: 40, backgroundColor: pill ? white(0.025) : 'transparent', transform: [{ rotate: `${angle}deg` }] }}>
              {pill && <SFSymbol name="circle.lefthalf.filled" size={15} color={white(0.19)} />}
              <Text style={{ fontSize: 15, fontWeight: '500', color: white(0.19), textShadowColor: white(0.19), textShadowRadius: 2 }}>{text}</Text>
            </View>
          </Centered>
        ))}
      </Animated.View>

      <Frame x={200} y={196 + 190} width={316.7} height={380}>
        <Animated.View style={phoneStyle}>
          <MiniPhone configure={configure} overview={overview} />
        </Animated.View>
      </Frame>

      <Centered x={200} y={603.5}>
        <View style={{ height: 39, flexDirection: 'row', alignItems: 'center', gap: 12, transform: [{ translateX: -2.5 }] }}>
          <PopIn reduced={reduced} delay={0.05} from={0.5} damping={0.62} style={{ transform: [{ translateY: 1 }] }}>
            <Image source={TOGGLE_TILE} style={{ width: 45, height: 45, shadowColor: '#fff', shadowOpacity: 0.12, shadowRadius: 6 }} accessibilityIgnoresInvertColors />
          </PopIn>
          <Reveal reduced={reduced} delay={0.15} y={6}>
            <Text style={headingText}>Ai Bots,</Text>
          </Reveal>
        </View>
        <Reveal reduced={reduced} delay={0.32} y={10} style={{ marginTop: -3 }}>
          <Text style={headingText}>Ready To Go</Text>
        </Reveal>
      </Centered>

      <Centered x={200} y={681}>
        <Reveal reduced={reduced} delay={0.4} y={6}>
          <Caption text={'Fine-tune your bots, build strategies, and manage trades\neffortlessly'} />
        </Reveal>
      </Centered>
    </View>
  );
}

/** A phone outline at 316.7 × 380 holding a bot dashboard; it fades out downward. */
function MiniPhone({ configure, overview }: Actions) {
  return (
    <MaskedView
      style={{ width: 316.7, height: 380 }}
      maskElement={<LinearGradient colors={['#fff', '#fff', 'transparent']} locations={[0, 0.74, 0.95]} style={StyleSheet.absoluteFill} />}>
      <View style={{ width: 316.7, height: 380, borderRadius: 54, borderCurve: 'continuous', backgroundColor: '#000' }}>
        <LinearGradient colors={[hex('#4a4a4a'), hex('#2c2c2c')]} style={[StyleSheet.absoluteFill, { borderRadius: 54 }]} />
        <View style={{ position: 'absolute', left: 2.4, top: 2.4, right: 2.4, bottom: 2.4, borderRadius: 51.6, borderCurve: 'continuous', backgroundColor: '#000' }} />
        <Centered x={56.6} y={33} box={100}>
          <Text style={{ fontSize: 13, fontWeight: '600', color: '#fff' }}>9:41</Text>
        </Centered>
        <Frame x={157} y={33.3} width={90.7} height={20} style={{ borderRadius: 10, backgroundColor: '#000', borderWidth: 1.6, borderColor: hex('#3a3a3a') }} />
        <Centered x={256} y={33} box={100}>
          <View style={{ flexDirection: 'row', gap: 5, alignItems: 'center' }}>
            <SFSymbol name="cellularbars" size={12} weight="semibold" color="#fff" />
            <SFSymbol name="wifi" size={12} weight="semibold" color="#fff" />
            <SFSymbol name="battery.100percent" size={12} weight="semibold" color="#fff" />
          </View>
        </Centered>
        <View style={{ position: 'absolute', left: 22.3, top: 59, gap: 10.5 }}>
          <View style={{ padding: 4, gap: 6, borderRadius: 28, borderCurve: 'continuous', backgroundColor: hex('#101010'), transform: [{ translateX: -4 }] }}>
            <PressScale onPress={configure} accessibilityLabel="Configure AlphaMind">
              <View style={{ width: 259.3, height: 75, paddingHorizontal: 11, flexDirection: 'row', alignItems: 'center', gap: 8.3, borderRadius: 24, borderCurve: 'continuous', backgroundColor: hex('#090909'), borderWidth: 1.6, borderColor: hex('#2a2a2a') }}>
                <View style={{ width: 49.7, height: 50.7, borderRadius: 14, borderCurve: 'continuous', backgroundColor: hex('#1e1e1e'), alignItems: 'center', justifyContent: 'center' }}>
                  <Character kind="avatar" size={35} />
                </View>
                <View style={{ gap: 6, flex: 1 }}>
                  <Text style={{ fontSize: 11.8, fontWeight: '500', color: '#fff' }}>AlphaMind</Text>
                  <Text style={{ fontSize: 8.9, color: hex('#404040') }}>Scalping · BTC/USDT</Text>
                </View>
                <SFSymbol name="arrow.right" size={15} color="#fff" style={{ marginRight: 6 }} />
              </View>
            </PressScale>
            <DashboardControls configure={configure} overview={overview} width={259.3} fontSize={8.8} />
          </View>
          <DashboardRow title="Live Execution Monitor" icon="waveform.path.ecg.rectangle.fill" onPress={overview} />
          <DashboardRow title="Performance Overview" icon="chart.bar.doc.horizontal" onPress={overview} />
          <DashboardRow title="View Trading Logs" icon="doc.text" onPress={overview} />
        </View>
      </View>
    </MaskedView>
  );
}

function DashboardRow({ title, icon, onPress }: { title: string; icon: 'waveform.path.ecg.rectangle.fill' | 'chart.bar.doc.horizontal' | 'doc.text'; onPress: () => void }) {
  return (
    <PressScale onPress={onPress} accessibilityLabel={title} style={{ transform: [{ translateX: -4 }] }}>
      <View style={{ width: 261, height: 56.7, paddingLeft: 14, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 20, borderCurve: 'continuous', backgroundColor: hex('#0e0e0e'), borderWidth: 1, borderColor: white(0.03) }}>
        <View style={{ width: 15, alignItems: 'center' }}>
          <SFSymbol name={icon} size={11} color={hex('#8a8a8a')} />
        </View>
        <Text style={{ fontSize: 10.3, fontWeight: '600', letterSpacing: -0.15, color: white(0.82) }}>{title}</Text>
      </View>
    </PressScale>
  );
}

/** Risk Overview and Configure Bot, shared by the setup phone and the results stack. */
export function DashboardControls({ configure, overview, width = 278, height = 46, fontSize = 10 }: Actions & { width?: number; height?: number; fontSize?: number }) {
  const control = (title: string, onPress: () => void, icon: React.ReactNode) => (
    <PressScale onPress={onPress} accessibilityLabel={title} style={{ flex: 1 }}>
      <View style={{ height, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 17, borderCurve: 'continuous', backgroundColor: hex('#050505'), borderWidth: 1, borderColor: white(0.05) }}>
        {icon}
        <Text style={{ fontSize, fontWeight: '500', letterSpacing: -0.2, color: hex('#e2e2e2') }}>{title}</Text>
      </View>
    </PressScale>
  );
  return (
    <View style={{ width, flexDirection: 'row', gap: 7 }}>
      {control('Risk Overview', overview, <BellCurve width={fontSize * 1.45} height={fontSize * 1.45} color="#fff" lineWidth={1.2} />)}
      {control('Configure Bot', configure, <SliderGlyph width={fontSize * 1.6} height={fontSize * 0.75} />)}
    </View>
  );
}
