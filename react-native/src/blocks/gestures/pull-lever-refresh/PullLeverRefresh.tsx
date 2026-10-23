/**
 * A lever espresso machine, machined and lit like a product photo. Pump the lever: every downstroke draws a charge
 * and the gauge steps up 3 bar. At 9 bar the brew zone lights; let go and the spring pushes the lever back up
 * slowly while the shot pours: two streams, the cup filling under its crema, the needle falling, the timer counting
 * to 27 s. Then the pilot lamp glows warm and the steam rises. Pull again for the next shot: the cup is swapped.
 *
 * (The folder keeps its first name, pull-lever-refresh: the lever, the pressure and the result map 1:1.)
 */
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GaugeWindow, PullLever, TimerWindow } from './PullLever';
import { DRUM, GAUGE_BOX, TIMER_BOX, TIMER_LABEL, machineLayout, needleAngle, scaleAngle } from './lever-look';
import { RollingCaption } from './RollingCaption';
import { RollingNumber } from './RollingNumber';
import { BREW, PUMP } from './motion';
import { COLOR, GEO, LAYOUT, TYPE } from './theme';
import { STAGE, usePullLever, type Readout } from './use-pull-lever';

export type PullLeverRefreshProps = { autoPlay?: boolean };

const NUMERALS = [0, 3, 6, 9, 12] as const;
const NUMERAL_BOX = 20;
const UNIT_BOX = 30;
// The timer's digits roll through the whole window, one drum per digit.
const DIGIT_FONT = { fontSize: TYPE.digit.fontSize, fontWeight: TYPE.digit.fontWeight, color: COLOR.digit, lineHeight: DRUM.height };
const DONE_LINE = `${BREW.bar} bar · ${BREW.seconds} s · ${BREW.grams} g`;

/** The copy for each stage of the shot; `key` keeps the running seconds from rolling every tick. */
function caption({ stage, bar, seconds }: Readout): { title: string; sub: string; key?: string } {
  if (stage === STAGE.done) return { title: 'Espresso ready', sub: DONE_LINE };
  if (stage === STAGE.brewing) return { title: 'Brewing', sub: `${seconds} s`, key: 'brewing' };
  if (stage === STAGE.full) return { title: 'Building pressure', sub: 'Release to brew' };
  if (stage === STAGE.pumping) return { title: 'Building pressure', sub: `${bar} bar` };
  return { title: 'Pull a shot', sub: `Pump the lever to ${BREW.bar} bar` };
}

function hint(stage: number): string {
  if (stage === STAGE.done) return 'Double tap to pull another shot';
  if (stage === STAGE.brewing) return 'The shot is pouring';
  return `Pull the lever down ${PUMP.strokes} times to ${BREW.bar} bar and let go to brew, or double tap`;
}

export function PullLeverRefresh({ autoPlay = false }: PullLeverRefreshProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const { copyTop, scale, px, py } = machineLayout(width, height, insets);
  const { gesture, pullShot, readout, values } = usePullLever({ autoPlay, reduceMotion, pivot: { x: px, y: py, scale } });
  const { title, sub, key } = caption(readout);
  const needle = useDerivedValue(() => needleAngle(values.bar.get()));
  const seconds = useDerivedValue(() => Math.floor(values.shot.get() + 0.0001));

  const at = (x: number, y: number) => ({ left: px + scale * x, top: py + scale * y });
  const scaled = { transform: [{ scale }], transformOrigin: 'top left' } as const;

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={gesture}>
        <View
          style={styles.root}
          collapsable={false}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Espresso lever"
          accessibilityHint={hint(readout.stage)}
          accessibilityValue={{ text: `${title}. ${sub}` }}
          accessibilityState={{ busy: readout.stage === STAGE.brewing }}
          onAccessibilityTap={pullShot}
          accessibilityActions={[{ name: 'activate', label: 'Pull a shot' }]}
          onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'activate') pullShot(); }}
        >
          <PullLever values={values} width={width} height={height} px={px} py={py} scale={scale} reduceMotion={reduceMotion} />

          <View style={[styles.copy, { top: copyTop }]} pointerEvents="none">
            <Text style={styles.eyebrow} maxFontSizeMultiplier={TYPE.maxScale}>ESPRESSO</Text>
            <View style={styles.titleSlot}>
              <RollingCaption text={title} reduceMotion={reduceMotion} style={styles.title} align="center" />
            </View>
            <View style={styles.subSlot}>
              <RollingCaption text={sub} rollKey={key} reduceMotion={reduceMotion} style={styles.sub} align="center" roll="small" />
            </View>
          </View>

          {/* The gauge's printed numerals, at its scale, under its needle and glass. */}
          <View pointerEvents="none" style={[styles.box, at(GAUGE_BOX.x, GAUGE_BOX.y), { width: GAUGE_BOX.size, height: GAUGE_BOX.size }, scaled]}>
            {NUMERALS.map((n) => {
              const a = scaleAngle(n);
              return (
                <Text
                  key={n}
                  style={[styles.numeral, {
                    left: GAUGE_BOX.size / 2 + Math.cos(a) * GEO.gauge.numerals - NUMERAL_BOX / 2,
                    top: GAUGE_BOX.size / 2 + Math.sin(a) * GEO.gauge.numerals - TYPE.numeral.lineHeight / 2,
                  }]}
                  maxFontSizeMultiplier={1}
                >
                  {n}
                </Text>
              );
            })}
            {/* The unit sits halfway between the hub and the 6, clear of the 0 and the 12 at the foot of the scale. */}
            <Text style={[styles.unit, { left: GAUGE_BOX.size / 2 - UNIT_BOX / 2 + TYPE.unit.letterSpacing / 2, top: GAUGE_BOX.size / 2 - GEO.gauge.unit - TYPE.unit.lineHeight / 2 }]} maxFontSizeMultiplier={1}>BAR</Text>
          </View>
          <View pointerEvents="none" style={[styles.box, at(GAUGE_BOX.x, GAUGE_BOX.y), { width: GAUGE_BOX.size * scale, height: GAUGE_BOX.size * scale }]}>
            <GaugeWindow size={GAUGE_BOX.size} scale={scale} needle={needle} />
          </View>

          {/* The shot timer: an etched label on the lamp's row, two reels behind glass. */}
          <View pointerEvents="none" style={[styles.box, at(TIMER_BOX.x, TIMER_BOX.y), { width: TIMER_BOX.width, height: TIMER_BOX.height }, scaled]}>
            <Text style={styles.label} maxFontSizeMultiplier={1}>SHOT</Text>
            <View style={styles.window}>
              <View style={styles.reels}>
                <RollingNumber value={seconds} decimals={0} wholeDigits={2} padded cell={DRUM.width} gap={DRUM.gap} font={DIGIT_FONT} still={reduceMotion} />
              </View>
              <TimerWindow width={TIMER_BOX.width} height={TIMER_BOX.height} />
            </View>
          </View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.panel },
  copy: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, alignItems: 'center' },
  eyebrow: { ...TYPE.eyebrow, color: COLOR.eyebrow, marginBottom: LAYOUT.copyGap },
  titleSlot: { alignSelf: 'stretch', height: TYPE.title.lineHeight, marginBottom: LAYOUT.subGap },
  title: { ...TYPE.title, color: COLOR.ink },
  subSlot: { alignSelf: 'stretch', height: TYPE.sub.lineHeight },
  sub: { ...TYPE.sub, color: COLOR.mute },
  box: { position: 'absolute' },
  numeral: { ...TYPE.numeral, position: 'absolute', width: NUMERAL_BOX, textAlign: 'center', color: COLOR.dialInk },
  unit: { ...TYPE.unit, position: 'absolute', width: UNIT_BOX, textAlign: 'center', color: COLOR.dialMute },
  label: { ...TYPE.label, position: 'absolute', left: TIMER_LABEL.x, top: TIMER_LABEL.y, color: COLOR.etch },
  window: { ...StyleSheet.absoluteFill, borderRadius: GEO.timer.radius, overflow: 'hidden' },
  reels: { ...StyleSheet.absoluteFill, flexDirection: 'row', justifyContent: 'center' },
});
